package com.oneofx.fusion.tradingbot.BuyOrderProcess;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.oneofx.fusion.client.FusionApiClient;
import com.oneofx.fusion.client.model.OrderStatus;
import com.oneofx.fusion.client.model.FusionSymbol;
import com.oneofx.fusion.client.model.Account;
import com.oneofx.fusion.client.model.AssetBalance;
import com.oneofx.fusion.client.model.Order;
import com.oneofx.fusion.client.FusionApiException;
import com.oneofx.fusion.tradingbot.HelperFunctions.Time;
import com.oneofx.fusion.tradingbot.HelperFunctions.TradingRulesFormatter;
import com.oneofx.fusion.tradingbot.HelperFunctions.round;
import com.oneofx.fusion.tradingbot.Indicator.Updates;
import com.oneofx.fusion.tradingbot.SQL_Database.CurrencyDAO;
import com.oneofx.fusion.tradingbot.SQL_Database.HistDAO;
import com.oneofx.fusion.tradingbot.SQL_Database.PositionDAO;
import com.oneofx.fusion.tradingbot.Settings.set;
import com.oneofx.fusion.tradingbot.domain.HistoryPosition;
import com.oneofx.fusion.tradingbot.domain.Position;

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
     * @param client         Bitpanda Fusion API Client
     * @param BuyOrderIdList Liste der Buy-Order-IDs mit Status=0 aus DB
     * @param LivePrice      Aktueller Preis
     */
    public static void OrderStatus(String currency, FusionApiClient client, List<String> BuyOrderIdList, List<Double> LivePrice) {

        if (BuyOrderIdList.isEmpty()) {
            return;
        }

        checkOrdersViaBatch(currency, client, BuyOrderIdList, LivePrice);
    }

    /**
     * Holt alle offenen Orders per Batch (Weight: 6) und gleicht mit der DB-Liste ab.
     * Orders die nicht mehr offen sind werden einzeln nachgefragt.
     */
    private static void checkOrdersViaBatch(String currency, FusionApiClient client, List<String> BuyOrderIdList, List<Double> LivePrice) {
        try {
            // EIN API-Call für alle offenen Orders dieser Währung (Weight: 6)
            List<Order> openOrders = client.getOpenOrders(currency);

            // Set für schnellen Lookup: welche unserer DB-Orders sind bei Fusion noch offen?
            Set<String> openOrderIds = new HashSet<>();
            for (Order order : openOrders) {
                openOrderIds.add(order.getOrderId());
            }

            // 1) Offene Orders verarbeiten (NEW, PARTIALLY_FILLED) – ohne Extra-API-Call
            for (Order order : openOrders) {
                String orderId = order.getOrderId();

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
            for (String buyOrderId : BuyOrderIdList) {
                if (openOrderIds.contains(buyOrderId)) {
                    continue; // Bereits oben als offene Order verarbeitet
                }

                // Diese Order ist nicht mehr offen → Status einzeln abfragen
                try {
                    Order order = client.getOrderStatus(buyOrderId);

                    if (order.getStatus() == OrderStatus.FILLED) {
                        BUY_FILLED(currency, client, buyOrderId, order, LivePrice);
                    } else if (order.getStatus() == OrderStatus.CANCELED) {
                        CancelOrderFromOutside(order);
                    } else if (order.getStatus() == OrderStatus.FILLED_AND_CANCELED
                            && Double.parseDouble(order.getExecutedQty()) > 0) {
                        BUY_FILLED(currency, client, buyOrderId, order, LivePrice);
                    } else if (order.getStatus() == OrderStatus.REJECTED
                            || order.getStatus() == OrderStatus.DONE_FOR_DAY
                            || order.getStatus() == OrderStatus.FILLED_AND_CANCELED) {
                        DeleteOrderWithOrderId(buyOrderId);
                    } else if (order.getStatus() == OrderStatus.UNKNOWN) {
                        System.err.println("Unbekannter Fusion-Orderstatus für " + buyOrderId
                                + "; DB-Eintrag bleibt zur sicheren späteren Prüfung erhalten.");
                    }

                } catch (FusionApiException e) {
                    handleFusionException(e, buyOrderId);
                } catch (Exception e) {
                    handleGeneralException(e, buyOrderId);
                }
            }

        } catch (FusionApiException e) {
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
     * Behandelt FusionApiException beim Order-Status-Check.
     * Timeout → 5 Sekunden warten.
     */
    private static void handleFusionException(FusionApiException e, String buyOrderId) {
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
    private static void handleGeneralException(Exception e, String buyOrderId) {
        System.out.println("Unerwarteter Fehler beim Prüfen der Order " + buyOrderId + ": " + e.getMessage());
        e.printStackTrace();
    }

    private static void DeleteOrderWithOrderId(String buyOrderId) {
        positionDAO.delete(buyOrderId);
    }

    private static void CancelOrderFromOutside(Order order) {
        double OrderPrice = round.two(Double.valueOf(order.getPrice()));
        System.out.println("CANCEL FROM OUTSIDE: " + OrderPrice);
        positionDAO.delete(String.valueOf(order.getOrderId()));
    }

    private static void BUY_FILLED(String currency, FusionApiClient client, String BuyOrderId, Order order, List<Double> LivePrice) {

        // OrderPrice = stopPrice = der Preis, zu dem wir die Order am Markt platziert haben (Trigger).
        // BuyPrice   = tatsächlicher Ausführungspreis = cummulativeQuoteQty / executedQty.
        double OrderPrice = TradingRulesFormatter.formatPrice(currency, Double.valueOf(order.getStopPrice()));
        double ActualBuyPrice = computeActualFillPrice(currency, order, OrderPrice);
        double Quantity = TradingRulesFormatter.formatQuantity(currency, Double.valueOf(order.getExecutedQty()));
        double BuyAmount = round.five(ActualBuyPrice * Quantity);

        String BuyDate = Time.getCurrentDate();
        String BuyTime = Time.getCurrentTime_HHmmss();

        Updates.NewCounterPosition();
        Updates.setExpectationCounter();

        positionDAO.update(new Position.Builder(currency, BuyOrderId)
                .orderPrice(OrderPrice)
                .buyPrice(ActualBuyPrice)
                .quantity(Quantity)
                .buyAmount(BuyAmount)
                .status(5)
                .statusCode("FILLED")
                .buyTime(BuyTime)
                .buyDate(BuyDate)
                .build());

        String asset = FusionSymbol.baseAsset(currency);
        BalanceInfo balances = getBalances(asset, client);
        double taxe = histDAO.getTaxe();

        double eurBalance = round.eight((balances.eurBalance - taxe));
        double assetQuantityPrice = round.five(balances.assetQuantity * LivePrice.get(0));

        double balanceToAssetRatio = 0.0;
        if (balances.assetQuantity > 0) {
            balanceToAssetRatio = round.three((eurBalance - histDAO.getTaxe()) / assetQuantityPrice);
        }

        int countPosition = positionDAO.getCountPOS(currency);

        histDAO.insert(new HistoryPosition.Builder(currency, BuyOrderId)
                .origPrice(OrderPrice)
                .buyPrice(ActualBuyPrice)
                .quantity(Quantity)
                .buyAmount(BuyAmount)
                .buyDate(BuyDate)
                .buyTime(BuyTime)
                .balanceAtBuy(eurBalance)
                .assetAtBuy(assetQuantityPrice)
                .balanceToAssetAtBuy(balanceToAssetRatio)
                .posCount(countPosition)
                .x(round.five(countPosition / balanceToAssetRatio))
                .build());

        System.out.println("Save Hist & Pos " + currency + " orderPrice=" + OrderPrice
                + " buyPrice=" + ActualBuyPrice);
    }

    /**
     * Berechnet den tatsächlichen Ausführungspreis aus cummulativeQuoteQty / executedQty.
     * Fallback: limitPrice der Order, falls executedQty == 0 (sollte bei FILLED nicht vorkommen).
     */
    private static double computeActualFillPrice(String currency, Order order, double fallbackPrice) {
        try {
            double cumQuote = Double.parseDouble(order.getCummulativeQuoteQty());
            double execQty  = Double.parseDouble(order.getExecutedQty());
            if (execQty > 0 && cumQuote > 0) {
                return TradingRulesFormatter.formatPrice(currency, cumQuote / execQty);
            }
        } catch (Exception ignored) { }
        return fallbackPrice;
    }

    private static void BUY_NEW(String currency, FusionApiClient client, List<Double> LivePrice,
            String BuyOrderId, Double orderPrice) {

        try {
            double athPrice = currencyDAO.getAllTimeHigh(currency);
            int grid = set.getGridforCurrency(currency);
            double livePrice = LivePrice.get(0);

            int stepsBetween = gridStepsBetweenOrderAndLive(currency, athPrice, grid, orderPrice, livePrice);

            if (stepsBetween > 3) {
                client.cancelOrder(BuyOrderId);
                System.out.println("Order gecancelt (Markt zu weit unter Order): " + currency
                        + " orderPrice=" + orderPrice + " livePrice=" + livePrice
                        + " stepsBetween=" + stepsBetween);
                positionDAO.delete(BuyOrderId);
            }
        } catch (Exception e) {
            System.err.println("Fehler in BUY_NEW für Order " + BuyOrderId + ": " + e.getMessage());
        }
    }

    private static void PARTIALLY_FILLED(String currency, FusionApiClient client, List<Double> LivePrice,
            String BuyOrderId, Double orderPrice,
            Order order) {

        try {
            double athPrice = currencyDAO.getAllTimeHigh(currency);
            int grid = set.getGridforCurrency(currency);
            double livePrice = LivePrice.get(0);

            int stepsBetween = gridStepsBetweenOrderAndLive(currency, athPrice, grid, orderPrice, livePrice);
            boolean shouldCancel = stepsBetween > 3;

            if (shouldCancel) {
                            System.out.println("Order teilweise gefüllt: " + orderPrice + " - " + BuyOrderId);

                            int Status;
                            // OrderPrice = stopPrice (Platzierungspreis), BuyPrice = tatsächlicher Ausführungspreis
                            double OrderPrice = TradingRulesFormatter.formatPrice(currency, Double.valueOf(order.getStopPrice()));
                            double BuyPrice = computeActualFillPrice(currency, order, OrderPrice);
                            String BuyDate = Time.getCurrentDate();
                            String BuyTime = Time.getCurrentTime_HHmmss();
                            double Quantity = TradingRulesFormatter.formatQuantity(currency, Double.valueOf(order.getExecutedQty()));
                            double partially_BuyAmount = round.five(BuyPrice * Quantity);

                            if (partially_BuyAmount >= 6.0) {
                                Status = 5;
                            } else {
                                Status = 3;
                            }

                            positionDAO.update(new Position.Builder(currency, BuyOrderId)
                                    .orderPrice(OrderPrice)
                                    .buyPrice(BuyPrice)
                                    .quantity(Quantity)
                                    .buyAmount(partially_BuyAmount)
                                    .status(Status)
                                    .buyTime(BuyTime)
                                    .buyDate(BuyDate)
                                    .build());

                            histDAO.insert(new HistoryPosition.Builder(currency, BuyOrderId)
                                    .origPrice(OrderPrice)
                                    .buyPrice(BuyPrice)
                                    .quantity(Quantity)
                                    .buyAmount(partially_BuyAmount)
                                    .buyDate(BuyDate)
                                    .buyTime(BuyTime)
                                    .build());

                            // Cancel der verbleibenden offenen Order
                            client.cancelOrder(BuyOrderId);
                            System.out.println("Verbleibende Order gecancelt: " + BuyOrderId
                                    + " stepsBetween=" + stepsBetween);
            }
        } catch (Exception e) {
            System.err.println("Fehler in PARTIALLY_FILLED für Order " + BuyOrderId + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Zählt die Grid-Stufen strikt zwischen orderPrice (über dem Markt) und livePrice.
     * Strategie-Kontext: STOP_LOSS_LIMIT BUY liegt ÜBER dem Markt. Fällt der Markt,
     * wächst der Abstand - veraltete Orders sollten gecancelt werden.
     *
     * @return Anzahl Stufen zwischen Order und Markt, 0 wenn Order schon unter/auf Markt,
     *         -1 wenn Order nicht auf dem Grid liegt.
     */
    private static int gridStepsBetweenOrderAndLive(String currency, double athPrice, int grid,
            double orderPrice, double livePrice) {

        if (orderPrice <= 0 || livePrice <= 0 || athPrice <= 0 || grid <= 0) {
            return -1;
        }
        if (orderPrice <= livePrice) {
            return 0;
        }

        double price = athPrice;
        boolean passedOrder = false;
        int stepsBetween = 0;

        for (int i = 0; i < 10000; i++) {
            price = price - (price / 100.0 / grid);
            double formatted = TradingRulesFormatter.formatPrice(currency, price);

            if (!passedOrder) {
                if (Math.abs(formatted - orderPrice) / orderPrice < 0.001) {
                    passedOrder = true;
                    continue;
                }
                if (formatted < orderPrice * 0.999) {
                    return -1;
                }
            } else {
                if (formatted > livePrice) {
                    stepsBetween++;
                } else {
                    return stepsBetween;
                }
            }

            if (formatted < livePrice * 0.5) {
                return passedOrder ? stepsBetween : -1;
            }
        }
        return -1;
    }

    public static class BalanceInfo {
        public final double assetQuantity; // z.B. LTC
        public final double eurBalance; // EUR

        public BalanceInfo(double assetQuantity, double eurBalance) {
            this.assetQuantity = assetQuantity;
            this.eurBalance = eurBalance;
        }
    }

    public static BalanceInfo getBalances(String asset, FusionApiClient client) {
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

        } catch (FusionApiException e) {
            System.err.println("Fehler beim Abrufen der Balances: " + e.getMessage());
            return new BalanceInfo(0.0, 0.0);
        } catch (Exception e) {
            System.err.println("Unbekannter Fehler: " + e.getMessage());
            return new BalanceInfo(0.0, 0.0);
        }
    }
}
