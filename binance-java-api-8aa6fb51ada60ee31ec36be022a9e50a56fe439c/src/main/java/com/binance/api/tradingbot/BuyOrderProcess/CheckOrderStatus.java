package com.binance.api.tradingbot.BuyOrderProcess;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import com.binance.api.client.BinanceApiRestClient;
import com.binance.api.client.domain.OrderSide;
import com.binance.api.client.domain.OrderStatus;
import com.binance.api.client.domain.account.Order;
import com.binance.api.client.domain.account.request.CancelOrderRequest;
import com.binance.api.client.domain.account.request.OrderStatusRequest;
import com.binance.api.client.exception.BinanceApiException;
import com.binance.api.tradingbot.Database.dbUrl;
import com.binance.api.tradingbot.HelperFunctions.Time;
import com.binance.api.tradingbot.HelperFunctions.round;
import com.binance.api.tradingbot.Indicator.Update;
import com.binance.api.tradingbot.SQL_Database.ATHSQL;
import com.binance.api.tradingbot.Settings.set;

public class CheckOrderStatus {

    public static void OrderStatus(String currency, BinanceApiRestClient client, List<Long> BuyOrderIdList,
            List<Double> LivePrice) {

        for (Long buyOrderId : BuyOrderIdList) {

            // Fehler es wir bei binance gelöscht aber nicht in meiner Datenbank ?????
            try {
                Order order = client.getOrderStatus(new OrderStatusRequest(currency, buyOrderId));
                Double orderPrice = round.two(Double.parseDouble(order.getPrice()));

                if (((order.getSide().compareTo(OrderSide.BUY) == 0)
                        && (order.getStatus().compareTo(OrderStatus.NEW) == 0))) {

                    BUY_NEW(currency, client, LivePrice, buyOrderId, orderPrice);
                }

                if (((order.getSide().compareTo(OrderSide.BUY) == 0)
                        && (order.getStatus().compareTo(OrderStatus.PARTIALLY_FILLED) == 0))) {

                    Test_PARTIALLY_FILLED(currency, client, LivePrice, buyOrderId, orderPrice, order);
                }

                if (((order.getSide().compareTo(OrderSide.BUY) == 0)
                        && (order.getStatus().compareTo(OrderStatus.FILLED) == 0))) {

                    BUY_FILLED(currency, client, buyOrderId, order);
                }

                if (((order.getSide().compareTo(OrderSide.BUY) == 0)
                        && (order.getStatus().compareTo(OrderStatus.EXPIRED_IN_MATCH) == 0))) {

                    System.out.println("Gecancelte Order gefunden: " + buyOrderId);
                    DeleteOrderWithOrderId(buyOrderId);
                }

                CancelOrderFromOutside(order);

            } catch (BinanceApiException e) {
                // Prüfe ob es sich um einen Jackson Deserialisierung Fehler handelt
                if (e.getMessage().contains("InvalidFormatException") ||
                        e.getMessage().contains("EXPIRED_IN_MATCH") ||
                        e.getMessage().contains("not one of declared Enum instance names")) {
                    System.out.println("Jackson Deserialisierung Fehler (unbekannter OrderStatus): " + e.getMessage());
                    System.out.println("Überspringe Order " + buyOrderId
                            + " - wahrscheinlich neuer OrderStatus von Binance: EXPIRED_IN_MATCH");
                    continue;
                }

                System.out.println("Fehler beim Abrufen des Binance-API-Service: " + e.getMessage());

                if (e.getMessage().contains("timeout") || e.getMessage().contains("SocketTimeoutException")) {
                    System.out.println("Netzwerk-Timeout erkannt. Warte 5 Sekunden und versuche es später erneut...");
                    try {
                        Thread.sleep(5000); // 5 Sekunden warten
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    }
                }

                continue;
            } catch (Exception e) {
                // Prüfe auch hier auf Jackson-Fehler falls sie als andere Exception kommen
                if (e.getMessage().contains("InvalidFormatException") ||
                        e.getMessage().contains("EXPIRED_IN_MATCH") ||
                        e.getMessage().contains("not one of declared Enum instance names")) {
                    System.out.println("Jackson Deserialisierung Fehler (unbekannter OrderStatus): " + e.getMessage());
                    System.out.println(
                            "Überspringe Order " + buyOrderId + " - wahrscheinlich neuer OrderStatus von Binance");
                    continue;
                }

                System.out.println("Unerwarteter Fehler beim Prüfen der Order " + buyOrderId + ": " + e.getMessage());
                e.printStackTrace();
                // Bei unerwarteten Fehlern auch weitermachen
                continue;
            }
        }
    }

    private static void DeleteOrderWithOrderId(Long buyOrderId) {
        try (Connection con = DriverManager.getConnection(dbUrl.getPOS());
                PreparedStatement pstmt = con.prepareStatement(
                        "DELETE FROM POS WHERE BuyOrderId = ?")) {

            pstmt.setLong(1, buyOrderId);
            pstmt.executeUpdate();
            System.out.println("Order aus Datenbank entfernt: " + buyOrderId);

        } catch (SQLException err) {
            System.err.println("Datenbankfehler beim Löschen: " + err.getMessage());
        }
    }

    private static void CancelOrderFromOutside(Order order) {
        if (((order.getSide().compareTo(OrderSide.BUY) == 0)
                && (order.getStatus().compareTo(OrderStatus.CANCELED) == 0))) {

            double OrderPrice = round.two(Double.valueOf(order.getPrice()));
            System.out.println("CANCEL FROM OUTSIDE: " + OrderPrice);

            try (Connection con = DriverManager.getConnection(dbUrl.getPOS());
                    Statement delete = con.createStatement()) {
                delete.execute("DELETE FROM POS WHERE BuyOrderId = " + order.getOrderId());
            } catch (SQLException err) {
                System.out.println(err.getMessage());
            }
        }
    }

    private static void BUY_FILLED(String currencyPair, BinanceApiRestClient client, Long BuyOrderId, Order order) {

        double OrderPrice = getBuyPrice(order);
        String BuyDate = Time.getCurrentDate();
        String BuyTime = Time.getCurrentTime_HHmmss();

        Update.NewCounterPosition();

        
                    // mache ein DCA auf die Nr7
                    // Merge.splitValue(currencyPair, null);
                





        try (Connection con_update = DriverManager.getConnection(dbUrl.getPOS());
                Statement update = con_update.createStatement()) {

            String SQL_update = "UPDATE POS SET "
                    + "OrderPrice = " + OrderPrice + ", "
                    + "Status = 5, "
                    + "BuyTime = '" + Time.getCurrentTime_HHmmss() + "', "
                    + "BuyDate = '" + Time.getCurrentDate() + "' "
                    + "WHERE BuyOrderId = " + BuyOrderId;

            update.executeUpdate(SQL_update);
            System.out.println("BUY FILLED - Vollzogen: " + OrderPrice);

        } catch (SQLException err) {
            System.out.println("Fehler beim Aktualisieren der Daten: " + err.getMessage());
        }

        try (Connection con_insert_HIST = DriverManager.getConnection(dbUrl.getHIST());
                PreparedStatement insert_HIST = con_insert_HIST.prepareStatement(
                        "INSERT INTO HIST (Währung, BuyOrderId, OrigPrice, BuyDate, BuyTime) VALUES (?, ?, ?, ?, ?)")) {        
           
            insert_HIST.setString(1, currencyPair);
            insert_HIST.setLong(2, BuyOrderId);
            insert_HIST.setDouble(3, OrderPrice);
            insert_HIST.setString(4, BuyDate);
            insert_HIST.setString(5, BuyTime);

            insert_HIST.executeUpdate();
            System.out.println("Part in Hist Saved");

        } catch (SQLException err) {
            System.out.println("Fehler beim Einfügen in die HIST-Tabelle: " + err.getMessage());
        }
    }

    private static double getBuyPrice(Order order) {
        double BuyPrice = round.five(Double.valueOf(order.getPrice()));
        return BuyPrice;
    }

    private static void BUY_NEW(String currencyPair, BinanceApiRestClient client, List<Double> LivePrice,
            Long BuyOrderId, Double orderPrice) {

        try {
            double CancelPrice = ATHSQL.getAllTimeHigh(currencyPair);
            double LiveKurs = LivePrice.get(0);
            int i = 0;
            int grid = set.getGridforCurrency(currencyPair);

            boolean calc = true;
            while (calc) {
                CancelPrice = CancelPrice - ((CancelPrice / 100) / grid);
                CancelPrice = round.two(CancelPrice);

                if (LiveKurs > CancelPrice) {
                    i++;
                    if (i > 5) {
                        calc = false;
                        break;
                    }
                    if (i == 3) {
                        if (CancelPrice > orderPrice) {
                            client.cancelOrder(new CancelOrderRequest(currencyPair, BuyOrderId));
                            System.out.println("Order gecancelt: " + orderPrice);

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

    private static void Test_PARTIALLY_FILLED(String currencyPair, BinanceApiRestClient client, List<Double> LivePrice,
            Long BuyOrderId, Double orderPrice,
            Order order) {

        try {
            double CancelPrice = ATHSQL.getAllTimeHigh(currencyPair);
            double LiveKurs = LivePrice.get(0);
            int i = 0;
            int grid = set.getGridforCurrency(currencyPair);

            boolean calc = true;
            while (calc) {
                CancelPrice = CancelPrice - ((CancelPrice / 100) / grid);
                CancelPrice = round.two(CancelPrice);

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
                            double BuyPrice = getBuyPrice(order);
                            String BuyDate = Time.getCurrentDate();
                            String BuyTime = Time.getCurrentTime_HHmmss();
                            double Quantity = round.Quantity((Double.valueOf(order.getExecutedQty())), currencyPair);
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
                                pstmt.setString(3, currencyPair);
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

                            try (Connection con_insert_HIST = DriverManager.getConnection(dbUrl.getHIST());
                                    PreparedStatement pstmt = con_insert_HIST.prepareStatement(
                                            "INSERT INTO HIST (Währung, BuyOrderId, BuyPrice, Quantity, BuyAmount, BuyDate, BuyTime) VALUES (?, ?, ?, ?, ?, ?, ?)")) {

                                pstmt.setString(1, currencyPair);
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
                            client.cancelOrder(new CancelOrderRequest(currencyPair, BuyOrderId));
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
}
