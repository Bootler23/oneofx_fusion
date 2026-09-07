package com.oneofx.fusion.tradingbot.BuyOrderProcess;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;

import com.oneofx.fusion.client.FusionApiClient;
import com.oneofx.fusion.client.model.OrderSide;
import com.oneofx.fusion.client.model.OrderType;
import com.oneofx.fusion.client.model.TimeInForce;
import com.oneofx.fusion.client.model.NewOrder;
import com.oneofx.fusion.client.model.NewOrderResponse;
import com.oneofx.fusion.client.FusionApiException;
import com.oneofx.fusion.tradingbot.HelperFunctions.TradingRulesFormatter;
import com.oneofx.fusion.tradingbot.HelperFunctions.empty;
import com.oneofx.fusion.tradingbot.HelperFunctions.round;
import com.oneofx.fusion.tradingbot.HelperFunctions.sleep;

import com.oneofx.fusion.tradingbot.SQL_Database.CurrencyDAO;
import com.oneofx.fusion.tradingbot.SQL_Database.BuyOrderPersistence;
import com.oneofx.fusion.tradingbot.SQL_Database.BuyOrderPersistence.Attempt;
import com.oneofx.fusion.tradingbot.SQL_Database.PositionDAO;
import com.oneofx.fusion.tradingbot.Settings.set;
import com.oneofx.fusion.tradingbot.constants.TradingConstants;
import com.oneofx.fusion.tradingbot.domain.Position;

public class BuyOrderPocess {

    private static final PositionDAO positionDAO = new PositionDAO();
    private static final CurrencyDAO currencyDAO = new CurrencyDAO();
    private static final BuyOrderPersistence buyOrderPersistence = new BuyOrderPersistence();
    private static final int MAX_GRID_STEPS = 10000;

    public static void setBuyOrder(String currency, FusionApiClient client, List<Double> LivePrice) {

        double Ath = currencyDAO.getAllTimeHigh(currency);
        double TickerPrice = LivePrice.get(0);
        int grid = set.getGridforCurrency(currency);

        if (Ath <= 0.0) {
            System.err.println("⚠️ ATH für " + currency + " ist 0 - Buy-Order wird übersprungen.");
            return;
        }

        if (positionDAO.countPendingOrders(currency) >= 2) {
            return;
        }

        if (TickerPrice >= Ath) {
            // Kurs ist auf/über ATH -> keine Grid-Stufe oberhalb verfügbar
            return;
        }

        // Suche die letzten zwei Grid-Stufen ÜBER dem TickerPrice
        double stopLevel = 0.0;    // erste Stufe über Ticker -> stopPrice (Trigger)
        double limitLevel = 0.0;   // zweite Stufe darüber    -> limitPrice (Maximalpreis)
        double prevLevel = 0.0;
        double prevPrevLevel = 0.0;
        double currentLevel = Ath;
        boolean foundStopLevel = false;

        for (int count = 0; count < MAX_GRID_STEPS; count++) {
            currentLevel = currentLevel - ((currentLevel / 100.0) / grid);
            double gridPrice = TradingRulesFormatter.formatPrice(currency, currentLevel);

            if (gridPrice <= TickerPrice) {
                // Grid ist unter den Ticker gefallen -> die letzten zwei Stufen
                // davor sind unsere Trigger/Limit-Kandidaten.
                if (prevLevel > 0.0 && prevPrevLevel > 0.0) {
                    stopLevel = prevLevel;
                    limitLevel = prevPrevLevel;
                    foundStopLevel = true;
                }
                break;
            }

            prevPrevLevel = prevLevel;
            prevLevel = gridPrice;
        }

        if (!foundStopLevel) {
            // Keine zwei vollständigen Stufen über dem Ticker gefunden -> Order skippen
            return;
        }

        if (positionDAO.positionExistsAtPrice(currency, stopLevel)) {
            return;
        }

        double BuyAmount = BuyAmountFunktion.getsimplebuyamount(currency);
        if (BuyAmount <= 0) {
            BuyAmount = currencyDAO.getMinBuyAmount(currency);
        }

        String stopPriceStr = TradingRulesFormatter.formatOrderPrice(currency, stopLevel);
        String limitPriceStr = TradingRulesFormatter.formatOrderPrice(currency, limitLevel);
        // Quantity über limitPrice berechnen (worst-case Preis, sichert Notional ab)
        String Quantity = TradingRulesFormatter.calculateAndFormatQuantity(currency,
                BigDecimal.valueOf(BuyAmount), new BigDecimal(limitPriceStr));

        if (!TradingRulesFormatter.isOrderValid(
                currency, new BigDecimal(limitPriceStr), new BigDecimal(Quantity))) {
            System.out.println("⚠️ Stop-Limit Order ist ungültig gemäß Fusion-Trading-Regeln für " + currency);
            System.out.println("   stopPrice: " + stopPriceStr
                    + ", limitPrice: " + limitPriceStr
                    + ", Quantity: " + Quantity);
            return;
        }

        empty.Line();
        System.out.println("Setze STOP_LIMIT BUY für " + currency);
        System.out.println("  TickerPrice: " + TickerPrice);
        System.out.println("  stopPrice  : " + stopPriceStr + " (Trigger)");
        System.out.println("  limitPrice : " + limitPriceStr + " (Max-Preis)");

        // REVIEW [API-PRUEFUNG]: Die aktuelle offizielle Fusion-CLI demonstriert
        // das Erstellen nur fuer Limit- und Market-Orders. Dieser Bot ist jedoch
        // vollstaendig auf StopLimit-Buys angewiesen. Das muss mit einem kleinen
        // Testbetrag gegen die echte API bestaetigt werden, bevor Trading aktiv ist.
        NewOrder stopLimitBuy = new NewOrder(
                currency,
                OrderSide.BUY,
                OrderType.STOP_LIMIT,
                TimeInForce.GTC,
                Quantity,
                limitPriceStr)
                .triggerPrice(stopPriceStr);

        Attempt attempt;
        try {
            attempt = buyOrderPersistence.start(currency, Quantity, stopPriceStr, limitPriceStr);
        } catch (SQLException ex) {
            System.err.println("KRITISCH: Buy-Versuch konnte vor der Uebermittlung nicht gespeichert werden: "
                    + ex.getMessage());
            return;
        }

        String exchangeOrderId = null;
        try {
            NewOrderResponse newOrderResponse = client.newOrder(stopLimitBuy);
            exchangeOrderId = newOrderResponse == null ? null : newOrderResponse.getOrderId();
            if (exchangeOrderId == null || exchangeOrderId.isBlank()) {
                requireReconciliation(attempt, null,
                        "Fusion hat die Buy-Anfrage beantwortet, aber keine Order-ID geliefert");
                System.err.println("KRITISCH: Ausgang des Buy-Versuchs " + attempt.attemptId()
                        + " ist unklar; er muss abgeglichen werden.");
                return;
            }

            Position position = new Position.Builder(currency, exchangeOrderId)
                    .orderPrice(new BigDecimal(stopPriceStr).doubleValue())
                    .status(0)
                    .statusCode(TradingConstants.STATUS_NEW)
                    .build();
            buyOrderPersistence.recordSubmitted(attempt, exchangeOrderId, position);

            System.out.println("NEW_POSITION in DataBase: stop=" + stopPriceStr
                    + " EUR / limit=" + limitPriceStr + " EUR");
            System.out.println("BUY AMOUNT bei..........: " + round.three(BuyAmount) + " EUR");
            System.out.println("Quantity bei............: " + Quantity + " " + currency);
            System.out.println("");
        } catch (FusionApiException ex) {
            handleSubmissionFailure(attempt, ex);
            System.err.println("Fehler beim Kauf (Stop-Limit): " + ex.getMessage());
            sleep.for_60_seconds();
        } catch (SQLException ex) {
            requireReconciliation(attempt, exchangeOrderId,
                    "Lokale Verbuchung der angenommenen Buy-Order fehlgeschlagen: " + ex.getMessage());
            System.err.println("KRITISCH: Angenommene Fusion-Buy-Order " + exchangeOrderId
                    + " konnte lokal nicht verbucht werden: " + ex.getMessage());
        } catch (Exception ex) {
            requireReconciliation(attempt, exchangeOrderId,
                    "Unerwarteter Fehler waehrend der Buy-Uebermittlung: " + ex.getMessage());
            System.err.println("Unerwarteter Fehler: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    private static void handleSubmissionFailure(Attempt attempt, FusionApiException ex) {
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

    private static void requireReconciliation(Attempt attempt, String exchangeOrderId, String reason) {
        try {
            buyOrderPersistence.requireReconciliation(attempt, exchangeOrderId, reason);
        } catch (SQLException persistenceError) {
            System.err.println("KRITISCH: Buy-Versuch " + attempt.attemptId()
                    + " konnte nicht als ungeklärt markiert werden: " + persistenceError.getMessage());
        }
    }
}
