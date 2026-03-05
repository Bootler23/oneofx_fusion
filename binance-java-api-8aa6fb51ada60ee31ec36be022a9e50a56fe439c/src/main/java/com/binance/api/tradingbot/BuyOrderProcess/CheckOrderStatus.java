package com.binance.api.tradingbot.BuyOrderProcess;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.binance.api.client.BinanceApiRestClient;
import com.binance.api.client.domain.OrderStatus;
import com.binance.api.client.domain.account.Account;
import com.binance.api.client.domain.account.AssetBalance;
import com.binance.api.client.domain.account.Order;
import com.binance.api.client.domain.account.request.CancelOrderRequest;
import com.binance.api.client.domain.account.request.OrderRequest;
import com.binance.api.client.domain.account.request.OrderStatusRequest;
import com.binance.api.client.exception.BinanceApiException;
import com.binance.api.tradingbot.Database.dbUrl;
import com.binance.api.tradingbot.HelperFunctions.Time;
import com.binance.api.tradingbot.HelperFunctions.TradingRulesFormatter;
import com.binance.api.tradingbot.HelperFunctions.round;
import com.binance.api.tradingbot.Indicator.Updates;
import com.binance.api.tradingbot.SQL_Database.ATHSQL;
import com.binance.api.tradingbot.SQL_Database.HISTSQL;
import com.binance.api.tradingbot.SQL_Database.POSSQL;
import com.binance.api.tradingbot.Settings.set;

public class CheckOrderStatus {

    /**
     * Prüft den Status aller offenen Buy-Orders für eine Währung.
     *
     * Verwendet getOpenOrders() (Weight: 6) für ALLE Fälle:
     * - 0 Orders: Kein API-Call (Early Return)
     * - 1-2 Orders: getOpenOrders() → Weight 6, spart Roundtrips bei 2 Orders
     *
     * Orders die nicht mehr offen sind (FILLED/CANCELED/EXPIRED)
     * werden einzeln nachgefragt (selten, nur wenn gerade gefüllt).
     *
     * @param currency       Währungspaar (z.B. "LTCEUR")
     * @param client         Binance API Client
     * @param BuyOrderIdList Liste der Buy-Order-IDs mit Status=0 aus DB
     * @param LivePrice      Aktueller Preis
     */
    public static void OrderStatus(String currency, BinanceApiRestClient client, List<Long> BuyOrderIdList, List<Double> LivePrice) {

        if (BuyOrderIdList.isEmpty()) {
            return;
        }

        checkOrdersViaBatch(currency, client, BuyOrderIdList, LivePrice);
    }

    /**
     * Holt alle offenen Orders per Batch (Weight: 6) und gleicht mit der DB-Liste ab.
     * Orders die nicht mehr offen sind werden einzeln nachgefragt.
     */
    private static void checkOrdersViaBatch(String currency, BinanceApiRestClient client, List<Long> BuyOrderIdList, List<Double> LivePrice) {
        try {
            // EIN API-Call für alle offenen Orders dieser Währung (Weight: 6)
            List<Order> openOrders = client.getOpenOrders(new OrderRequest(currency));

            // Set für schnellen Lookup: welche unserer DB-Orders sind noch offen bei Binance?
            Set<Long> openOrderIds = new HashSet<>();
            for (Order order : openOrders) {
                openOrderIds.add(order.getOrderId());
            }

            // 1) Offene Orders verarbeiten (NEW, PARTIALLY_FILLED) – ohne Extra-API-Call
            for (Order order : openOrders) {
                Long orderId = order.getOrderId();

                if (!BuyOrderIdList.contains(orderId)) {
                    continue; // Nicht unsere Buy-Order (z.B. Sell-Order oder andere)
                }

                Double orderPrice = Double.parseDouble(order.getPrice());

                if (order.getStatus() == OrderStatus.NEW) {
                    BUY_NEW(currency, client, LivePrice, orderId, orderPrice);
                } else if (order.getStatus() == OrderStatus.PARTIALLY_FILLED) {
                    PARTIALLY_FILLED(currency, client, LivePrice, orderId, orderPrice, order);
                }
            }

            // 2) Orders die in DB (Status=0) stehen aber NICHT mehr offen sind
            //    → FILLED, CANCELED oder EXPIRED. Einzeln nachfragen (selten).
            for (Long buyOrderId : BuyOrderIdList) {
                if (openOrderIds.contains(buyOrderId)) {
                    continue; // Bereits oben als offene Order verarbeitet
                }

                // Diese Order ist nicht mehr offen → Status einzeln abfragen
                try {
                    Order order = client.getOrderStatus(new OrderStatusRequest(currency, buyOrderId));

                    if (order.getStatus() == OrderStatus.FILLED) {
                        BUY_FILLED(currency, client, buyOrderId, order, LivePrice);
                    } else if (order.getStatus() == OrderStatus.CANCELED) {
                        CancelOrderFromOutside(order);
                    } else if (order.getStatus() == OrderStatus.EXPIRED_IN_MATCH) {
                        DeleteOrderWithOrderId(buyOrderId);
                    }

                } catch (BinanceApiException e) {
                    handleBinanceException(e, buyOrderId);
                } catch (Exception e) {
                    handleGeneralException(e, buyOrderId);
                }
            }

        } catch (BinanceApiException e) {
            System.err.println("Fehler beim Abrufen der offenen Orders für " + currency + ": " + e.getMessage());

            if (e.getMessage() != null &&
                    (e.getMessage().contains("timeout") || e.getMessage().contains("SocketTimeoutException"))) {
                System.out.println("Netzwerk-Timeout bei getOpenOrders. Warte 5 Sekunden...");
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            }

        } catch (Exception e) {
            System.err.println("Unerwarteter Fehler in OrderStatus für " + currency + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Behandelt BinanceApiException beim Order-Status-Check.
     * Jackson-Deserialisierungsfehler (z.B. EXPIRED_IN_MATCH) → Order aus DB löschen.
     * Timeout → 5 Sekunden warten.
     */
    private static void handleBinanceException(BinanceApiException e, Long buyOrderId) {
        if (e.getMessage() != null && (e.getMessage().contains("InvalidFormatException") ||
                e.getMessage().contains("EXPIRED_IN_MATCH") ||
                e.getMessage().contains("not one of declared Enum instance names"))) {
            System.out.println("Jackson Deserialisierung Fehler: " + e.getMessage());
            System.out.println("Überspringe Order " + buyOrderId
                    + " - wahrscheinlich EXPIRED_IN_MATCH, entferne aus DB");
            DeleteOrderWithOrderId(buyOrderId);
            return;
        }

        System.out.println("Fehler beim Abrufen des Order-Status für " + buyOrderId + ": " + e.getMessage());

        if (e.getMessage() != null &&
                (e.getMessage().contains("timeout") || e.getMessage().contains("SocketTimeoutException"))) {
            System.out.println("Netzwerk-Timeout erkannt. Warte 5 Sekunden...");
            try {
                Thread.sleep(5000);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Behandelt allgemeine Exceptions beim Order-Status-Check.
     */
    private static void handleGeneralException(Exception e, Long buyOrderId) {
        if (e.getMessage() != null && (e.getMessage().contains("InvalidFormatException") ||
                e.getMessage().contains("EXPIRED_IN_MATCH") ||
                e.getMessage().contains("not one of declared Enum instance names"))) {
            System.out.println("Jackson Deserialisierung Fehler: " + e.getMessage());
            DeleteOrderWithOrderId(buyOrderId);
            return;
        }

        System.out.println("Unerwarteter Fehler beim Prüfen der Order " + buyOrderId + ": " + e.getMessage());
        e.printStackTrace();
    }

    private static void DeleteOrderWithOrderId(Long buyOrderId) {
        // System.out.println("Gecancelte Order gefunden: " + buyOrderId);
        try (Connection con = DriverManager.getConnection(dbUrl.getPOS());
                PreparedStatement pstmt = con.prepareStatement(
                        "DELETE FROM POS WHERE BuyOrderId = ?")) {

            pstmt.setLong(1, buyOrderId);
            pstmt.executeUpdate();
            // System.out.println("Order aus Datenbank entfernt: " + buyOrderId);

        } catch (SQLException err) {
            System.err.println("Datenbankfehler beim Löschen: " + err.getMessage());
        }
    }

    private static void CancelOrderFromOutside(Order order) {
        double OrderPrice = round.two(Double.valueOf(order.getPrice()));
        System.out.println("CANCEL FROM OUTSIDE: " + OrderPrice);

        try (Connection con = DriverManager.getConnection(dbUrl.getPOS());
                Statement delete = con.createStatement()) {
            delete.execute("DELETE FROM POS WHERE BuyOrderId = " + order.getOrderId());
        } catch (SQLException err) {
            System.out.println(err.getMessage());
        }
    }

    private static void BUY_FILLED(String currency, BinanceApiRestClient client, Long BuyOrderId, Order order, List<Double> LivePrice) {

        double OrderPrice = TradingRulesFormatter.formatPrice(currency, Double.valueOf(order.getPrice()));
        String BuyDate = Time.getCurrentDate();
        String BuyTime = Time.getCurrentTime_HHmmss();

        Updates.NewCounterPosition();
        Updates.addminBuyAmount();
        // Updates.ratioBalanceToBA();
        Updates.calcPercentToAddForNextBuy();
        Updates.setExpectationCounter();

        try (Connection con_update = DriverManager.getConnection(dbUrl.getPOS());
                Statement update = con_update.createStatement()) {

            String SQL_update = "UPDATE POS SET "
                    + "OrderPrice = " + OrderPrice + ", "
                    + "Status = 5, "
                    + "statusCode = 'FILLED', "
                    + "BuyTime = '" + Time.getCurrentTime_HHmmss() + "', "
                    + "BuyDate = '" + Time.getCurrentDate() + "' "
                    + "WHERE BuyOrderId = " + BuyOrderId;

            update.executeUpdate(SQL_update);
            // System.out.println("BUY FILLED - Vollzogen: " + currency + " " + OrderPrice);

        } catch (SQLException err) {
            System.out.println("Fehler beim Aktualisieren der Daten: " + err.getMessage());
        }
       
        String asset = currency.replace("EUR", ""); // z.B. "LTCEUR" -> "LTC", "BNBEUR" -> "BNB"
        BalanceInfo balances = getBalances(asset, client);
        double taxe = HISTSQL.getTaxe();

        try (Connection con_insert_HIST = DriverManager.getConnection(dbUrl.getoneOfX());
                PreparedStatement insert_HIST = con_insert_HIST.prepareStatement(
                        "INSERT INTO HIST (Währung, BuyOrderId, OrigPrice, BuyDate, BuyTime, Balance_atBuy, Asset_atBuy, BalanceToAsset_atBuy, POS_count, X) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {

            insert_HIST.setString(1, currency);
            insert_HIST.setLong(2, BuyOrderId);
            insert_HIST.setDouble(3, OrderPrice);
            insert_HIST.setString(4, BuyDate);
            insert_HIST.setString(5, BuyTime);

            double eurBalance = round.eight((balances.eurBalance - taxe));
            insert_HIST.setDouble(6, eurBalance);

            double assetQuantityPrice = round.five(balances.assetQuantity * LivePrice.get(0));
            insert_HIST.setDouble(7, assetQuantityPrice);    

            double balanceToAssetRatio = 0.0;
            if (balances.assetQuantity > 0) {
                balanceToAssetRatio = round.three((eurBalance -  HISTSQL.getTaxe()) / assetQuantityPrice);
            }
            insert_HIST.setDouble(8, balanceToAssetRatio);

            int countPosition = POSSQL.getCountPOS(currency);
            insert_HIST.setInt(9, countPosition);  
            insert_HIST.setDouble(10, round.five(countPosition/balanceToAssetRatio)); // TODO -> Balance/BA

            insert_HIST.executeUpdate();
            System.out.println("Save Hist & Pos " + currency + " " + OrderPrice);

        } catch (SQLException err) {
            System.out.println("Fehler beim Einfügen in die HIST-Tabelle: " + err.getMessage());
        }
    }

    private static double getBuyPrice(Order order) {
        double BuyPrice = round.five(Double.valueOf(order.getPrice()));
        return BuyPrice;
    }

    private static void BUY_NEW(String currency, BinanceApiRestClient client, List<Double> LivePrice,
            Long BuyOrderId, Double orderPrice) {

        try {
            double athPrice = ATHSQL.getAllTimeHigh(currency);
            double CancelPrice = athPrice;
            double LiveKurs = LivePrice.get(0);
            int i = 0;
            int grid = set.getGridforCurrency(currency);
            double stepSize = athPrice / 100.0 / grid;  // präziser Step ohne Runden

            boolean calc = true;
            int maxIterations = 10000;
            int iteration = 0;
            while (calc && iteration++ < maxIterations) {
                CancelPrice = CancelPrice - stepSize;  // kein round.two hier

                if (LiveKurs > CancelPrice) {
                    i++;
                    if (i > 5) {
                        calc = false;
                        break;
                    }
                    if (i == 3) {
                        if (CancelPrice > orderPrice) {
                            client.cancelOrder(new CancelOrderRequest(currency, BuyOrderId));
                            System.out.println("Order gecancelt: " + currency + " orderPrice " + orderPrice);

                            DeleteOrderWithOrderId(BuyOrderId);
                            calc = false;
                            break;
                        }
                    }
                }
            }

        } catch (Exception e) {
            System.err.println("Fehler in BUY_NEW für Order " + BuyOrderId + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void PARTIALLY_FILLED(String currency, BinanceApiRestClient client, List<Double> LivePrice,
            Long BuyOrderId, Double orderPrice,
            Order order) {

        try {
            double athPrice = ATHSQL.getAllTimeHigh(currency);
            double CancelPrice = athPrice;
            double LiveKurs = LivePrice.get(0);
            int i = 0;
            int grid = set.getGridforCurrency(currency);
            double stepSize = athPrice / 100.0 / grid;  // präziser Step ohne Runden

            boolean calc = true;
            int maxIterations = 10000;
            int iteration = 0;
            while (calc && iteration++ < maxIterations) {
                CancelPrice = CancelPrice - stepSize;  // kein round.two hier

                if (LiveKurs > CancelPrice) {
                    i++;
                    if (i > 5) {
                        calc = false;
                        break;
                    }
                    if (i == 3) {
                        if (CancelPrice > orderPrice) {
                            System.out.println("Order teilweise gefüllt: " + orderPrice + " - " + BuyOrderId);

                            int Status;
                            double BuyPrice = TradingRulesFormatter.formatPrice(currency, Double.valueOf(order.getPrice()));
                            String BuyDate = Time.getCurrentDate();
                            String BuyTime = Time.getCurrentTime_HHmmss();
                            double Quantity = TradingRulesFormatter.formatQuantity(currency, Double.valueOf(order.getExecutedQty()));
                            double partially_BuyAmount = round.five(BuyPrice * Quantity);

                            if (partially_BuyAmount >= 6.0) {
                                Status = 5;
                            } else {
                                Status = 3;
                            }

                            try (Connection con_update = DriverManager.getConnection(dbUrl.getPOS());
                                    PreparedStatement pstmt = con_update.prepareStatement(
                                            "UPDATE POS SET BuyPrice = ?, Qty = ?, Währung = ?, BuyAmount = ?, " +
                                                    "Status = ?, BuyTime = ?, BuyDate = ? WHERE BuyOrderId = ?")) {

                                pstmt.setDouble(1, BuyPrice);
                                pstmt.setDouble(2, Quantity);
                                pstmt.setString(3, currency);
                                pstmt.setDouble(4, partially_BuyAmount);
                                pstmt.setInt(5, Status);
                                pstmt.setString(6, BuyTime);
                                pstmt.setString(7, BuyDate);
                                pstmt.setLong(8, BuyOrderId);

                                pstmt.executeUpdate();

                            } catch (SQLException err) {
                                System.err.println("Fehler beim Aktualisieren der Daten: " + err.getMessage());
                                err.printStackTrace();
                            }

                            try (Connection con_insert_HIST = DriverManager.getConnection(dbUrl.getoneOfX());
                                    PreparedStatement pstmt = con_insert_HIST.prepareStatement(
                                            "INSERT INTO HIST (Währung, BuyOrderId, BuyPrice, Quantity, BuyAmount, BuyDate, BuyTime) VALUES (?, ?, ?, ?, ?, ?, ?)")) {

                                pstmt.setString(1, currency);
                                pstmt.setLong(2, BuyOrderId);
                                pstmt.setDouble(3, BuyPrice);
                                pstmt.setDouble(4, Quantity);
                                pstmt.setDouble(5, partially_BuyAmount);
                                pstmt.setString(6, BuyDate);
                                pstmt.setString(7, BuyTime);

                                pstmt.executeUpdate();

                            } catch (SQLException err) {
                                System.err.println("Fehler beim Einfügen in die HIST-Tabelle: " + err.getMessage());
                                err.printStackTrace();
                            }

                            // Cancel der verbleibenden offenen Order
                            client.cancelOrder(new CancelOrderRequest(currency, BuyOrderId));
                            System.out.println("Verbleibende Order gecancelt: " + BuyOrderId);

                            calc = false;
                            break;
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Fehler in Test_PARTIALLY_FILLED für Order " + BuyOrderId + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static class BalanceInfo {
        public final double assetQuantity; // z.B. LTC
        public final double eurBalance; // EUR

        public BalanceInfo(double assetQuantity, double eurBalance) {
            this.assetQuantity = assetQuantity;
            this.eurBalance = eurBalance;
        }
    }

    public static BalanceInfo getBalances(String asset, BinanceApiRestClient client) {
        try {
            // Nur EIN API-Call für beide Werte (Weight: 20)
            Account account = client.getAccount();

            // Asset-Balance (z.B. LTC)
            AssetBalance assetBalance = account.getAssetBalance(asset);
            double assetFree = Double.parseDouble(assetBalance.getFree());
            double assetLocked = Double.parseDouble(assetBalance.getLocked());
            double assetTotal = assetFree + assetLocked;

            // EUR-Balance
            AssetBalance eurBalance = account.getAssetBalance("EUR");
            double eurFree = Double.parseDouble(eurBalance.getFree());
            double eurLocked = Double.parseDouble(eurBalance.getLocked());
            double eurTotal = eurFree + eurLocked;

            return new BalanceInfo(assetTotal, eurTotal);

        } catch (BinanceApiException e) {
            System.err.println("Fehler beim Abrufen der Balances: " + e.getMessage());
            return new BalanceInfo(0.0, 0.0);
        } catch (Exception e) {
            System.err.println("Unbekannter Fehler: " + e.getMessage());
            return new BalanceInfo(0.0, 0.0);
        }
    }
}
