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
import com.oneofx.fusion.client.model.OrderType;
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
import com.oneofx.fusion.tradingbot.grid.GridCalculator;
import com.oneofx.fusion.tradingbot.grid.GridSettings;

public class CheckOrderStatus {

    private static final PositionDAO positionDAO = new PositionDAO();
    private static final HistDAO histDAO = new HistDAO();
    private static final CurrencyDAO currencyDAO = new CurrencyDAO();
    static final int MAX_GRID_LEVELS_BELOW_MARKET = 2;
    private static final int MAX_GRID_SCAN_STEPS = 10_000;

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
     * Storniert alle noch offenen Kauforders, wenn der Marktfilter keine neuen
     * Einstiege erlaubt. Eine Teilfüllung bleibt erhalten und wird durch die
     * normale Abschlussverarbeitung als Position verbucht.
     */
    public static void cancelOpenBuyOrdersForRegime(String currency, FusionApiClient client,
            List<String> buyOrderIds, List<Double> livePrice) {
        for (String buyOrderId : buyOrderIds) {
            try {
                Order cancelResult = client.cancelOrder(buyOrderId);
                System.out.println("1D-MARKTFILTER KAUFPAUSE: Cancel angefordert fuer "
                        + currency + " Order " + buyOrderId);
                handleTerminalBuyOrder(currency, client, buyOrderId, cancelResult,
                        livePrice, "1D-Marktfilter Kaufpause");
            } catch (FusionApiException e) {
                handleFusionException(e, buyOrderId);
            } catch (Exception e) {
                handleGeneralException(e, buyOrderId);
            }
        }
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

                double orderPrice = getConfiguredBuyPrice(currency, order);

                // Alte Stop-Limit-Buys liegen oberhalb des Marktes und gehoeren
                // nicht mehr zur neuen Strategie. Kontrolliert stornieren und
                // erst nach bestaetigtem Ergebnis aus der DB entfernen.
                if (order.getType() != OrderType.LIMIT) {
                    cancelAndHandleOrder(currency, client, LivePrice, orderId, orderPrice,
                            LivePrice.get(0), -1);
                    continue;
                }

                if (order.getStatus() == OrderStatus.NEW) {
                    BUY_NEW(currency, client, LivePrice, orderId, orderPrice);
                } else if (order.getStatus() == OrderStatus.PARTIALLY_FILLED) {
                    PARTIALLY_FILLED(currency, client, LivePrice, orderId, orderPrice);
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

                    handleTerminalBuyOrder(currency, client, buyOrderId, order, LivePrice,
                            "Statusabfrage");

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

    private static void BUY_FILLED(String currency, FusionApiClient client, String BuyOrderId, Order order, List<Double> LivePrice) {

        // OrderPrice = eingestelltes Limit; BuyPrice = tatsächlicher Durchschnittspreis.
        double OrderPrice = getConfiguredBuyPrice(currency, order);
        double ActualBuyPrice = computeActualFillPrice(currency, order, OrderPrice);
        double Quantity = TradingRulesFormatter.formatQuantity(currency, Double.valueOf(order.getExecutedQty()));
        double BuyAmount = round.five(ActualBuyPrice * Quantity);

        String BuyDate = Time.getCurrentDate();
        String BuyTime = Time.getCurrentTime_HHmmss();

        Updates.NewCounterPosition();

        positionDAO.update(new Position.Builder(currency, BuyOrderId)
                .orderPrice(OrderPrice)
                .buyPrice(ActualBuyPrice)
                .quantity(Quantity)
                .buyAmount(BuyAmount)
                .status(5)
                .statusCode("FILLED")
                .peakPrice(ActualBuyPrice)
                .tsl("inactive")
                .buyTime(BuyTime)
                .buyDate(BuyDate)
                .build());

        String asset = FusionSymbol.baseAsset(currency);
        BalanceInfo balances = getBalances(asset, client);
        double taxe = histDAO.getTaxe();

        double eurBalance = round.eight((balances.eurBalance - taxe));
        double assetQuantityPrice = round.five(balances.assetQuantity * LivePrice.get(0));

        double balanceToAssetRatio = 0.0;
        if (assetQuantityPrice > 0) {
            balanceToAssetRatio = round.three(eurBalance / assetQuantityPrice);
        }

        int countPosition = positionDAO.getCountPOS(currency);
        double positionsToBalanceRatio = balanceToAssetRatio != 0.0
                ? round.five(countPosition / balanceToAssetRatio)
                : 0.0;

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
                .x(positionsToBalanceRatio)
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
            GridSettings grid = set.getGridSettings(currency);
            double livePrice = LivePrice.get(0);

            int stepsBetween = gridStepsBetweenLiveAndOrder(currency, athPrice, grid, livePrice, orderPrice);

            if (stepsBetween > MAX_GRID_LEVELS_BELOW_MARKET) {
                cancelAndHandleOrder(currency, client, LivePrice, BuyOrderId, orderPrice,
                        livePrice, stepsBetween);
            }
        } catch (Exception e) {
            System.err.println("Fehler in BUY_NEW für Order " + BuyOrderId + ": " + e.getMessage());
        }
    }

    private static void PARTIALLY_FILLED(String currency, FusionApiClient client, List<Double> LivePrice,
            String BuyOrderId, Double orderPrice) {

        try {
            double athPrice = currencyDAO.getAllTimeHigh(currency);
            GridSettings grid = set.getGridSettings(currency);
            double livePrice = LivePrice.get(0);

            int stepsBetween = gridStepsBetweenLiveAndOrder(currency, athPrice, grid, livePrice, orderPrice);
            if (stepsBetween > MAX_GRID_LEVELS_BELOW_MARKET) {
                cancelAndHandleOrder(currency, client, LivePrice, BuyOrderId, orderPrice,
                        livePrice, stepsBetween);
            }
        } catch (Exception e) {
            System.err.println("Fehler in PARTIALLY_FILLED für Order " + BuyOrderId + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Storniert eine offene Buy-Order und verarbeitet nur den vom Cancel-Aufruf
     * zurückgegebenen Zustand. Bei Fehlern oder einem weiterhin offenen/unklaren
     * Zustand bleibt der DB-Eintrag für den nächsten Abgleich erhalten.
     */
    private static void cancelAndHandleOrder(String currency, FusionApiClient client,
            List<Double> LivePrice, String buyOrderId, double orderPrice,
            double livePrice, int stepsBetween) {

        Order cancelResult = client.cancelOrder(buyOrderId);

        System.out.println("Cancel angefordert: " + currency
                + " orderPrice=" + orderPrice + " livePrice=" + livePrice
                + " stepsBetween=" + stepsBetween);

        handleTerminalBuyOrder(currency, client, buyOrderId, cancelResult, LivePrice,
                "Cancel");
    }

    /**
     * Verarbeitet den abschließenden Zustand einer Buy-Order. Die ausgeführte Menge
     * hat Vorrang vor der Statusbezeichnung, da auch CANCELED eine Teilfüllung
     * enthalten kann.
     */
    private static void handleTerminalBuyOrder(String currency, FusionApiClient client,
            String buyOrderId, Order order, List<Double> LivePrice, String source) {

        if (order == null || order.getStatus() == null) {
            System.err.println(source + " ohne eindeutigen Orderstatus für " + buyOrderId
                    + "; DB-Eintrag bleibt erhalten.");
            return;
        }

        final double executedQty;
        try {
            executedQty = Double.parseDouble(order.getExecutedQty());
        } catch (RuntimeException e) {
            System.err.println(source + " mit ungültiger Ausführungsmenge für " + buyOrderId
                    + "; DB-Eintrag bleibt erhalten.");
            return;
        }

        OrderStatus status = order.getStatus();

        if (status == OrderStatus.FILLED) {
            if (executedQty > 0) {
                BUY_FILLED(currency, client, buyOrderId, order, LivePrice);
            } else {
                System.err.println(source + " meldet FILLED ohne Ausführungsmenge für "
                        + buyOrderId + "; DB-Eintrag bleibt erhalten.");
            }
            return;
        }

        if (status == OrderStatus.NEW || status == OrderStatus.PARTIALLY_FILLED
                || status == OrderStatus.UNKNOWN) {
            System.err.println(source + " noch nicht abschließend für " + buyOrderId
                    + ": " + status + "; DB-Eintrag bleibt erhalten.");
            return;
        }

        if (executedQty > 0) {
            savePartiallyFilledOrder(currency, buyOrderId, order);
            return;
        }

        DeleteOrderWithOrderId(buyOrderId);
        System.out.println("Order ohne Ausführung abgeschlossen: " + buyOrderId
                + " status=" + status);
    }

    /** Speichert die nach einem bestätigten Cancel tatsächlich ausgeführte Teilmenge. */
    private static void savePartiallyFilledOrder(String currency, String buyOrderId, Order order) {
        double orderPrice = getConfiguredBuyPrice(currency, order);
        double buyPrice = computeActualFillPrice(currency, order, orderPrice);
        String buyDate = Time.getCurrentDate();
        String buyTime = Time.getCurrentTime_HHmmss();
        double quantity = TradingRulesFormatter.formatQuantity(currency,
                Double.valueOf(order.getExecutedQty()));
        double partiallyBuyAmount = round.five(buyPrice * quantity);
        int status = partiallyBuyAmount >= 6.0 ? 5 : 3;

        positionDAO.update(new Position.Builder(currency, buyOrderId)
                .orderPrice(orderPrice)
                .buyPrice(buyPrice)
                .quantity(quantity)
                .buyAmount(partiallyBuyAmount)
                .status(status)
                .buyTime(buyTime)
                .buyDate(buyDate)
                .build());

        histDAO.insert(new HistoryPosition.Builder(currency, buyOrderId)
                .origPrice(orderPrice)
                .buyPrice(buyPrice)
                .quantity(quantity)
                .buyAmount(partiallyBuyAmount)
                .buyDate(buyDate)
                .buyTime(buyTime)
                .build());

        System.out.println("Teilweise ausgeführte Order gespeichert: " + buyOrderId
                + " quantity=" + quantity + " buyAmount=" + partiallyBuyAmount);
    }

    /**
     * Zählt die Grid-Stufen strikt zwischen dem Livekurs und einer Limit-Order darunter.
     * Steigt der Markt, wächst der Abstand und eine zu weit entfernte Order wird storniert.
     *
     * @return Anzahl dazwischenliegender Stufen oder -1 bei ungültigen Eingaben.
     */
    static int gridStepsBetweenLiveAndOrder(String currency, double athPrice, int grid,
            double livePrice, double orderPrice) {

        if (grid <= 0) return -1;
        return gridStepsBetweenLiveAndOrder(currency, athPrice,
                GridSettings.legacy(grid), livePrice, orderPrice);
    }

    static int gridStepsBetweenLiveAndOrder(String currency, double athPrice,
            GridSettings grid, double livePrice, double orderPrice) {

        if (orderPrice <= 0 || livePrice <= 0 || athPrice <= 0 || grid == null) {
            return -1;
        }
        if (orderPrice >= livePrice) {
            return 0;
        }

        double formattedOrderPrice = TradingRulesFormatter.formatPrice(currency, orderPrice);
        double startPrice = Math.max(athPrice, livePrice);

        // Direkt zur ersten Grid-Stufe in der Naehe des Livepreises springen.
        // Ein linearer Lauf vom ATH bis zum Markt kann bei alten ATHs tausende
        // Iterationen pro offener Order benoetigen.
        long level;
        double price;
        try {
            level = GridCalculator.firstLevelBelow(startPrice, livePrice, grid);
            price = GridCalculator.level(startPrice, level, grid);
        } catch (IllegalArgumentException ex) {
            return -1;
        }
        double formatted = TradingRulesFormatter.formatPrice(currency, price);

        // Rundung auf tickSize kann den logarithmischen Kandidaten um wenige
        // Stufen verschieben. Lokal korrigieren, ohne wieder am ATH zu beginnen.
        for (int adjustments = 0; formatted >= livePrice && adjustments < MAX_GRID_SCAN_STEPS; adjustments++) {
            level++;
            price = GridCalculator.level(startPrice, level, grid);
            formatted = TradingRulesFormatter.formatPrice(currency, price);
        }
        if (formatted >= livePrice) {
            return -1;
        }

        double previousFormattedLevel = Double.NaN;
        int stepsBetween = 0;

        for (int i = 0; i < MAX_GRID_SCAN_STEPS; i++) {
            if (Double.compare(formatted, previousFormattedLevel) == 0) {
                price = GridCalculator.level(startPrice, ++level, grid);
                formatted = TradingRulesFormatter.formatPrice(currency, price);
                continue;
            }
            previousFormattedLevel = formatted;

            // Die Order muss nicht exakt auf dem aktuellen Grid liegen. Das ATH
            // kann seit ihrer Erstellung gestiegen sein und das Grid verschoben haben.
            if (formatted <= formattedOrderPrice) {
                return stepsBetween;
            }
            stepsBetween++;

            // Der Aufrufer muss nur wissen, ob die Order mehr als zwei Grid-Stufen
            // entfernt ist. Danach ist die exakte Distanz irrelevant.
            if (stepsBetween > MAX_GRID_LEVELS_BELOW_MARKET) {
                return stepsBetween;
            }

            price = GridCalculator.level(startPrice, ++level, grid);
            if (price <= 0.0) return stepsBetween;
            formatted = TradingRulesFormatter.formatPrice(currency, price);
        }
        return -1;
    }

    private static double getConfiguredBuyPrice(String currency, Order order) {
        double triggerPrice = parsePositive(order.getTriggerPrice());
        if (triggerPrice > 0.0) {
            // Kompatibilitaet mit bereits vorhandenen Stop-Limit-Orders.
            return TradingRulesFormatter.formatPrice(currency, triggerPrice);
        }

        double limitPrice = parsePositive(order.getLimitPrice());
        if (limitPrice > 0.0) {
            return TradingRulesFormatter.formatPrice(currency, limitPrice);
        }

        throw new IllegalArgumentException("Buy-Order " + order.getOrderId()
                + " besitzt keinen gueltigen Limit- oder Triggerpreis");
    }

    private static double parsePositive(String value) {
        try {
            double parsed = Double.parseDouble(value);
            return parsed > 0.0 ? parsed : 0.0;
        } catch (RuntimeException ex) {
            return 0.0;
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
