package com.binance.api.tradingbot.BuyOrderProcess;

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
import com.binance.api.tradingbot.HelperFunctions.Time;
import com.binance.api.tradingbot.HelperFunctions.TradingRulesFormatter;
import com.binance.api.tradingbot.HelperFunctions.round;
import com.binance.api.tradingbot.Indicator.Updates;
import com.binance.api.tradingbot.SQL_Database.CurrencyDAO;
import com.binance.api.tradingbot.SQL_Database.HistDAO;
import com.binance.api.tradingbot.SQL_Database.PositionDAO;
import com.binance.api.tradingbot.Settings.set;
import com.binance.api.tradingbot.domain.HistoryPosition;
import com.binance.api.tradingbot.domain.Position;

public class CheckOrderStatus {

    private static final PositionDAO positionDAO = new PositionDAO();
    private static final HistDAO histDAO = new HistDAO();
    private static final CurrencyDAO currencyDAO = new CurrencyDAO();

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
        positionDAO.delete(String.valueOf(buyOrderId));
    }

    private static void CancelOrderFromOutside(Order order) {
        double OrderPrice = round.two(Double.valueOf(order.getPrice()));
        System.out.println("CANCEL FROM OUTSIDE: " + OrderPrice);
        positionDAO.delete(String.valueOf(order.getOrderId()));
    }

    private static void BUY_FILLED(String currency, BinanceApiRestClient client, Long BuyOrderId, Order order, List<Double> LivePrice) {

        double OrderPrice = TradingRulesFormatter.formatPrice(currency, Double.valueOf(order.getPrice()));
        String BuyDate = Time.getCurrentDate();
        String BuyTime = Time.getCurrentTime_HHmmss();

        Updates.NewCounterPosition();       
        Updates.setExpectationCounter();

        positionDAO.update(new Position.Builder(currency, String.valueOf(BuyOrderId))
                .orderPrice(OrderPrice)
                .status(5)
                .statusCode("FILLED")
                .buyTime(Time.getCurrentTime_HHmmss())
                .buyDate(Time.getCurrentDate())
                .build());
       
        String asset = currency.replace("EUR", "");
        BalanceInfo balances = getBalances(asset, client);
        double taxe = histDAO.getTaxe();

        double eurBalance = round.eight((balances.eurBalance - taxe));
        double assetQuantityPrice = round.five(balances.assetQuantity * LivePrice.get(0));

        double balanceToAssetRatio = 0.0;
        if (balances.assetQuantity > 0) {
            balanceToAssetRatio = round.three((eurBalance - histDAO.getTaxe()) / assetQuantityPrice);
        }

        int countPosition = positionDAO.getCountPOS(currency);

        histDAO.insert(new HistoryPosition.Builder(currency, String.valueOf(BuyOrderId))
                .origPrice(OrderPrice)
                .buyDate(BuyDate)
                .buyTime(BuyTime)
                .balanceAtBuy(eurBalance)
                .assetAtBuy(assetQuantityPrice)
                .balanceToAssetAtBuy(balanceToAssetRatio)
                .posCount(countPosition)
                .x(round.five(countPosition / balanceToAssetRatio))
                .build());

        System.out.println("Save Hist & Pos " + currency + " " + OrderPrice);
    }

    private static double getBuyPrice(Order order) {
        double BuyPrice = round.five(Double.valueOf(order.getPrice()));
        return BuyPrice;
    }

    private static void BUY_NEW(String currency, BinanceApiRestClient client, List<Double> LivePrice,
            Long BuyOrderId, Double orderPrice) {

        try {
            double athPrice = currencyDAO.getAllTimeHigh(currency);
            int grid = set.getGridforCurrency(currency);
            double livePrice = LivePrice.get(0);

            // Proportionaler Walk vom ATH (identisch zu BuyOrderProcess)
            double price = athPrice;
            int stepsBelow = 0;
            boolean found = false;

            for (int iteration = 0; iteration < 10000; iteration++) {
                price = price - (price / 100.0 / grid);
                double formatted = TradingRulesFormatter.formatPrice(currency, price);

                if (livePrice > formatted) {
                    stepsBelow++;
                }

                if (orderPrice > 0 && Math.abs(formatted - orderPrice) / orderPrice < 0.001) {
                    found = true;
                    break;
                }

                if (formatted < orderPrice * 0.9) break;
            }

            if (found && stepsBelow > 3) {
                client.cancelOrder(new CancelOrderRequest(currency, BuyOrderId));
                System.out.println("Order gecancelt (zu weit weg): " + currency
                        + " orderPrice=" + orderPrice + " livePrice=" + livePrice
                        + " steps=" + stepsBelow);
                positionDAO.delete(String.valueOf(BuyOrderId));
            }
        } catch (Exception e) {
            System.err.println("Fehler in BUY_NEW für Order " + BuyOrderId + ": " + e.getMessage());
        }
    }

    private static void PARTIALLY_FILLED(String currency, BinanceApiRestClient client, List<Double> LivePrice,
            Long BuyOrderId, Double orderPrice,
            Order order) {

        try {
            double athPrice = currencyDAO.getAllTimeHigh(currency);
            int grid = set.getGridforCurrency(currency);
            double livePrice = LivePrice.get(0);

            // Proportionaler Walk vom ATH (identisch zu BuyOrderProcess)
            double price = athPrice;
            int stepsBelow = 0;
            boolean shouldCancel = false;

            for (int iteration = 0; iteration < 10000; iteration++) {
                price = price - (price / 100.0 / grid);
                double formatted = TradingRulesFormatter.formatPrice(currency, price);

                if (livePrice > formatted) {
                    stepsBelow++;
                }

                if (orderPrice > 0 && Math.abs(formatted - orderPrice) / orderPrice < 0.001) {
                    shouldCancel = stepsBelow > 3;
                    break;
                }

                if (formatted < orderPrice * 0.9) break;
            }

            if (shouldCancel) {
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

                            positionDAO.update(new Position.Builder(currency, String.valueOf(BuyOrderId))
                                    .buyPrice(BuyPrice)
                                    .quantity(Quantity)
                                    .buyAmount(partially_BuyAmount)
                                    .status(Status)
                                    .buyTime(BuyTime)
                                    .buyDate(BuyDate)
                                    .build());

                            histDAO.insert(new HistoryPosition.Builder(currency, String.valueOf(BuyOrderId))
                                    .buyPrice(BuyPrice)
                                    .quantity(Quantity)
                                    .buyAmount(partially_BuyAmount)
                                    .buyDate(BuyDate)
                                    .buyTime(BuyTime)
                                    .build());

                            // Cancel der verbleibenden offenen Order
                            client.cancelOrder(new CancelOrderRequest(currency, BuyOrderId));
                            System.out.println("Verbleibende Order gecancelt: " + BuyOrderId
                                    + " steps=" + stepsBelow);
            }
        } catch (Exception e) {
            System.err.println("Fehler in PARTIALLY_FILLED für Order " + BuyOrderId + ": " + e.getMessage());
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
