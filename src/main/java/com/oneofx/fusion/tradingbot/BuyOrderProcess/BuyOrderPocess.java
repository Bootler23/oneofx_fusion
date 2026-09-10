package com.oneofx.fusion.tradingbot.BuyOrderProcess;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import com.oneofx.fusion.client.FusionApiClient;
import com.oneofx.fusion.client.FusionApiException;
import com.oneofx.fusion.client.model.NewOrder;
import com.oneofx.fusion.client.model.NewOrderResponse;
import com.oneofx.fusion.client.model.OrderSide;
import com.oneofx.fusion.client.model.OrderType;
import com.oneofx.fusion.client.model.TimeInForce;
import com.oneofx.fusion.tradingbot.HelperFunctions.TradingRulesFormatter;
import com.oneofx.fusion.tradingbot.HelperFunctions.empty;
import com.oneofx.fusion.tradingbot.HelperFunctions.round;
import com.oneofx.fusion.tradingbot.HelperFunctions.sleep;
import com.oneofx.fusion.tradingbot.SQL_Database.BuyOrderPersistence;
import com.oneofx.fusion.tradingbot.SQL_Database.BuyOrderPersistence.Attempt;
import com.oneofx.fusion.tradingbot.SQL_Database.CurrencyDAO;
import com.oneofx.fusion.tradingbot.SQL_Database.PositionDAO;
import com.oneofx.fusion.tradingbot.Settings.set;
import com.oneofx.fusion.tradingbot.constants.TradingConstants;
import com.oneofx.fusion.tradingbot.domain.Position;
import com.oneofx.fusion.tradingbot.grid.GridSettings;
import com.oneofx.fusion.tradingbot.service.BotRiskService;
import com.oneofx.fusion.tradingbot.service.GridOrderPlanner;
import com.oneofx.fusion.tradingbot.desktop.BaseConfigRepository;
import com.oneofx.fusion.tradingbot.desktop.BotBaseConfig;

public class BuyOrderPocess {

    private static final PositionDAO positionDAO = new PositionDAO();
    private static final CurrencyDAO currencyDAO = new CurrencyDAO();
    private static final BotRiskService botRiskService = new BotRiskService();
    private static final BaseConfigRepository baseConfigRepository = new BaseConfigRepository();
    private static final int MAX_PENDING_BUY_ORDERS = 2;

    public static void setBuyOrder(String currency, FusionApiClient client, List<Double> LivePrice) {

        double ath = currencyDAO.getAllTimeHigh(currency);
        double tickerPrice = LivePrice.get(0);
        GridSettings gridSettings = set.getGridSettings(currency);
        BuyOrderPersistence buyOrderPersistence = new BuyOrderPersistence();
        BotBaseConfig executionConfig;
        try { executionConfig = baseConfigRepository.loadEffective(currency); }
        catch (SQLException ex) {
            executionConfig = BotBaseConfig.defaults(0);
            System.err.println("Baseconfig fuer " + currency
                    + " fehlt; sichere Standardwerte werden verwendet: " + ex.getMessage());
        }

        if (ath <= 0.0) {
            System.err.println("ATH fuer " + currency + " ist 0 - Buy-Order wird uebersprungen.");
            return;
        }
        if (tickerPrice <= 0.0) {
            System.err.println("Ungueltige Grid- oder Preisdaten fuer " + currency
                    + ": grid=" + gridSettings + ", ticker=" + tickerPrice);
            return;
        }

        int pendingOrders = positionDAO.countPendingOrders(currency);
        if (pendingOrders >= MAX_PENDING_BUY_ORDERS) {
            return;
        }

        // Das Grid bleibt am ATH verankert. Gesucht werden ausschliesslich freie
        // Grid-Stufen unter dem aktuellen Kurs. Pro Durchlauf werden so viele
        // LIMIT-Buys ergaenzt, bis insgesamt zwei Pending-Orders vorhanden sind.
        int maximumNewOrders = "MARKET".equals(executionConfig.buyOrderType())
                ? 1 : MAX_PENDING_BUY_ORDERS - pendingOrders;
        List<Double> candidates = GridOrderPlanner.candidates(Math.max(ath, tickerPrice),
                tickerPrice, gridSettings,
                price -> TradingRulesFormatter.formatPrice(currency, price),
                price -> positionDAO.positionExistsAtPrice(currency, price),
                maximumNewOrders,
                CheckOrderStatus.MAX_GRID_LEVELS_BELOW_MARKET);
        for (double gridPrice : candidates) {
            if (!placeLimitBuy(currency, client, tickerPrice, gridPrice,
                    buyOrderPersistence, executionConfig)) {
                // Bei einem unklaren oder abgelehnten Ausgang keine weiteren
                // Orders senden. So vermeiden wir Doppelorders.
                return;
            }
        }
    }

    private static boolean placeLimitBuy(String currency, FusionApiClient client,
            double tickerPrice, double limitLevel, BuyOrderPersistence buyOrderPersistence,
            BotBaseConfig executionConfig) {

        double buyAmount = BuyAmountFunktion.getsimplebuyamount(currency);
        if (buyAmount <= 0) {
            buyAmount = currencyDAO.getMinBuyAmount(currency);
        }
        int openPositions = positionDAO.countOpenPositions(currency);
        if (openPositions > 0) {
            if (!executionConfig.dcaEnabled() || openPositions > executionConfig.dcaMaxOrders()) {
                return false;
            }
            double lowestEntry = positionDAO.getLowestOpenEntryPrice(currency);
            if (lowestEntry <= 0 || tickerPrice > lowestEntry
                    * (1.0 - executionConfig.dcaTriggerPercent() / 100.0)) {
                return false;
            }
            buyAmount *= Math.pow(executionConfig.dcaSizeMultiplier(), openPositions);
        }

        double maxBuyAmount = currencyDAO.getMaxBuyAmount(currency);
        double committedBuyAmount = positionDAO.getCommittedBuyAmount(currency);
        if (!Double.isFinite(committedBuyAmount)) {
            System.err.println("Buy-Budget fuer " + currency
                    + " konnte nicht sicher bestimmt werden; Kauf bleibt gesperrt.");
            return false;
        }
        if (maxBuyAmount <= 0.0
                || committedBuyAmount + buyAmount > maxBuyAmount + 0.000001) {
            System.out.println("Buy-Budget erreicht fuer " + currency
                    + ": gebunden=" + round.two(committedBuyAmount)
                    + " EUR, naechster Kauf=" + round.two(buyAmount)
                    + " EUR, Limit=" + round.two(maxBuyAmount) + " EUR");
            return false;
        }

        BotRiskService.Decision botRisk = botRiskService.evaluateNextBuy(buyAmount);
        if (!botRisk.allowed()) {
            System.out.println("Kauf durch Bot-Risikolimit gesperrt: " + botRisk.reason());
            return false;
        }

        double sizingPrice = "MARKET".equals(executionConfig.buyOrderType())
                ? tickerPrice : limitLevel;
        String limitPriceStr = TradingRulesFormatter.formatOrderPrice(currency, sizingPrice);
        String quantity = TradingRulesFormatter.calculateAndFormatQuantity(currency,
                BigDecimal.valueOf(buyAmount), new BigDecimal(limitPriceStr));

        if (!TradingRulesFormatter.isOrderValid(
                currency, new BigDecimal(limitPriceStr), new BigDecimal(quantity))) {
            System.out.println("LIMIT BUY ist ungueltig gemaess Fusion-Trading-Regeln fuer " + currency);
            System.out.println("   limitPrice: " + limitPriceStr + ", Quantity: " + quantity);
            return false;
        }

        empty.Line();
        System.out.println("Setze LIMIT BUY fuer " + currency);
        System.out.println("  TickerPrice: " + tickerPrice);
        System.out.println("  limitPrice : " + limitPriceStr);

        OrderType configuredType = OrderType.valueOf(executionConfig.buyOrderType());
        NewOrder limitBuy;
        if (configuredType == OrderType.MARKET) {
            limitBuy = NewOrder.marketBuy(currency, quantity);
        } else {
            TimeInForce timeInForce = executionConfig.maxBuyOrderMinutes() > 0
                    ? TimeInForce.GTD : TimeInForce.GTC;
            limitBuy = new NewOrder(currency, OrderSide.BUY, configuredType,
                    timeInForce, quantity, limitPriceStr);
            if (configuredType == OrderType.STOP_LIMIT) limitBuy.triggerPrice(limitPriceStr);
            if (timeInForce == TimeInForce.GTD) {
                limitBuy.endTime(Instant.now().plus(executionConfig.maxBuyOrderMinutes(),
                        ChronoUnit.MINUTES).toString());
            }
        }

        Attempt attempt;
        try {
            // Das vorhandene Journal besitzt aus Kompatibilitaetsgruenden ein
            // Stop- und ein Limit-Feld. Fuer LIMIT-Orders enthalten beide das Limit.
            attempt = buyOrderPersistence.start(currency, quantity, limitPriceStr, limitPriceStr);
        } catch (SQLException ex) {
            System.err.println("KRITISCH: Buy-Versuch konnte vor der Uebermittlung nicht gespeichert werden: "
                    + ex.getMessage());
            return false;
        }

        String exchangeOrderId = null;
        try {
            NewOrderResponse newOrderResponse = client.newOrder(limitBuy);
            exchangeOrderId = newOrderResponse == null ? null : newOrderResponse.getOrderId();
            if (exchangeOrderId == null || exchangeOrderId.isBlank()) {
                requireReconciliation(buyOrderPersistence, attempt, null,
                        "Fusion hat die Buy-Anfrage beantwortet, aber keine Order-ID geliefert");
                System.err.println("KRITISCH: Ausgang des Buy-Versuchs " + attempt.attemptId()
                        + " ist unklar; er muss abgeglichen werden.");
                return false;
            }

            Position position = new Position.Builder(currency, exchangeOrderId)
                    .orderPrice(new BigDecimal(limitPriceStr).doubleValue())
                    .quantity(new BigDecimal(quantity).doubleValue())
                    .buyAmount(buyAmount)
                    .status(0)
                    .statusCode(TradingConstants.STATUS_NEW)
                    .build();
            buyOrderPersistence.recordSubmitted(attempt, exchangeOrderId, position);

            System.out.println("NEW_ORDER in DataBase: limit=" + limitPriceStr + " EUR");
            System.out.println("BUY AMOUNT bei..........: " + round.three(buyAmount) + " EUR");
            System.out.println("Quantity bei............: " + quantity + " " + currency);
            System.out.println("");
            return true;
        } catch (FusionApiException ex) {
            handleSubmissionFailure(buyOrderPersistence, attempt, ex);
            System.err.println("Fehler beim Kauf (Limit): " + ex.getMessage());
            sleep.for_60_seconds();
        } catch (SQLException ex) {
            requireReconciliation(buyOrderPersistence, attempt, exchangeOrderId,
                    "Lokale Verbuchung der angenommenen Buy-Order fehlgeschlagen: " + ex.getMessage());
            System.err.println("KRITISCH: Angenommene Fusion-Buy-Order " + exchangeOrderId
                    + " konnte lokal nicht verbucht werden: " + ex.getMessage());
        } catch (Exception ex) {
            requireReconciliation(buyOrderPersistence, attempt, exchangeOrderId,
                    "Unerwarteter Fehler waehrend der Buy-Uebermittlung: " + ex.getMessage());
            System.err.println("Unerwarteter Fehler: " + ex.getMessage());
            ex.printStackTrace();
        }
        return false;
    }

    private static void handleSubmissionFailure(BuyOrderPersistence buyOrderPersistence,
            Attempt attempt, FusionApiException ex) {
        int statusCode = ex.getStatusCode();
        boolean definiteRejection = statusCode >= 400 && statusCode < 500 && statusCode != 408;
        try {
            if (definiteRejection) {
                buyOrderPersistence.recordRejected(attempt, ex.getMessage());
            } else {
                buyOrderPersistence.requireReconciliation(attempt, null, ex.getMessage());
            }
        } catch (SQLException persistenceError) {
            System.err.println("KRITISCH: Fehlerstatus des Buy-Versuchs " + attempt.attemptId()
                    + " konnte nicht gespeichert werden: " + persistenceError.getMessage());
        }
    }

    private static void requireReconciliation(BuyOrderPersistence buyOrderPersistence,
            Attempt attempt, String exchangeOrderId, String reason) {
        try {
            buyOrderPersistence.requireReconciliation(attempt, exchangeOrderId, reason);
        } catch (SQLException persistenceError) {
            System.err.println("KRITISCH: Buy-Versuch " + attempt.attemptId()
                    + " konnte nicht als ungeklärt markiert werden: " + persistenceError.getMessage());
        }
    }
}
