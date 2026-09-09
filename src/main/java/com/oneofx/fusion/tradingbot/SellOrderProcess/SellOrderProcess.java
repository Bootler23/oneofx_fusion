package com.oneofx.fusion.tradingbot.SellOrderProcess;

import static com.oneofx.fusion.client.model.NewOrder.marketSell;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import com.oneofx.fusion.client.FusionApiClient;
import com.oneofx.fusion.client.model.NewOrderResponse;
import com.oneofx.fusion.client.FusionApiException;
import com.oneofx.fusion.tradingbot.BuyOrderProcess.Ticker;
import com.oneofx.fusion.tradingbot.HelperFunctions.Time;
import com.oneofx.fusion.tradingbot.HelperFunctions.empty;
import com.oneofx.fusion.tradingbot.HelperFunctions.round;
import com.oneofx.fusion.tradingbot.HelperFunctions.sleep;
import com.oneofx.fusion.tradingbot.SQL_Database.CurrencyDAO;
import com.oneofx.fusion.tradingbot.SQL_Database.PositionDAO;
import com.oneofx.fusion.tradingbot.SQL_Database.SellOrderPersistence;
import com.oneofx.fusion.tradingbot.SQL_Database.SellOrderPersistence.Reservation;
import com.oneofx.fusion.tradingbot.HelperFunctions.TradingRulesFormatter;
import com.oneofx.fusion.tradingbot.domain.Position;

public class SellOrderProcess {

    private static final PositionDAO positionDAO = new PositionDAO();
    private static final CurrencyDAO currencyDAO = new CurrencyDAO();
    private static final SellOrderPersistence sellOrderPersistence = new SellOrderPersistence();

    public static void setSellOrder(String currency, FusionApiClient client,
            List<String> GetRecordFromDataBase_POS, List<Double> LivePrice) {

        double currentPrice = LivePrice.get(0);

        for (String dataRecord : GetRecordFromDataBase_POS) {
            String[] parts = dataRecord.split(", ");

            String BuyOrderId = parts[0];
            String Quantity_String = parts[3];
            String BuyPrice_String = parts[5];

            double BuyPrice_Double = Double.valueOf(BuyPrice_String);

            if (BuyPrice_Double <= 0) {
                continue;
            }

            // TSL_activate ist nur die Aktivierungsschwelle. Nach der Aktivierung
            // folgt der Stop dem Peak mit dem festen Abstand TSL_decline.
            if (currencyDAO.getTSL(currency)) {
                double tslActivatePct = currencyDAO.getTSLActivate(currency);
                double tslDeclinePct  = currencyDAO.getTSLDecline(currency);

                double storedPeakPrice = positionDAO.getPeakPrice(BuyOrderId);
                boolean wasActive = positionDAO.isTrailingStopActive(BuyOrderId);
                TrailingStopDecision decision = evaluateTrailingStop(
                        BuyPrice_Double,
                        currentPrice,
                        storedPeakPrice,
                        wasActive,
                        tslActivatePct,
                        tslDeclinePct);

                if (Double.compare(storedPeakPrice, decision.peakPrice()) != 0) {
                    positionDAO.updatePeakPrice(BuyOrderId, decision.peakPrice());
                }

                // Profit immer aktualisieren (positiv oder negativ)
                double profitPct = round.three((currentPrice - BuyPrice_Double) / BuyPrice_Double * 100.0);
                positionDAO.update(new Position.Builder(null, BuyOrderId)
                        .profit(profitPct)
                        .build());

                if (!wasActive && decision.active()) {
                    positionDAO.update(new Position.Builder(null, BuyOrderId)
                            .tsl("active")
                            .build());
                    System.out.println("TSL AKTIVIERT: " + currency
                            + " | Buy: " + BuyPrice_Double
                            + " | Peak: " + round.four(decision.peakPrice())
                            + " | Aktivierung: " + round.three(tslActivatePct) + "%"
                            + " | Abstand: " + round.three(tslDeclinePct) + "%");
                }

                if (decision.sell()) {
                    empty.Line();
                    System.out.println("TRAILING STOP-LOSS: " + currency
                            + " | Buy: " + BuyPrice_Double
                            + " | Peak: " + round.four(decision.peakPrice())
                            + " | HighProfit%: " + round.three(decision.highestProfitPct())
                            + " | Abstand: " + round.three(tslDeclinePct) + "%"
                            + " | Trigger: " + round.four(decision.triggerPrice())
                            + " | Aktuell: " + currentPrice);
                    executeSell(currency, client, BuyOrderId, Quantity_String);
                    continue;
                }
            }
        }
    }

    static TrailingStopDecision evaluateTrailingStop(double buyPrice, double currentPrice,
            double storedPeakPrice, boolean active, double activationPct, double declinePct) {
        double peakPrice = storedPeakPrice > 0
                ? Math.max(storedPeakPrice, currentPrice)
                : Math.max(buyPrice, currentPrice);
        double highestProfitPct = (peakPrice - buyPrice) / buyPrice * 100.0;
        boolean activeAfterEvaluation = active
                || (activationPct > 0 && highestProfitPct >= activationPct);

        boolean validDecline = declinePct > 0 && declinePct < 100;
        double triggerPrice = activeAfterEvaluation && validDecline
                ? peakPrice * (1.0 - declinePct / 100.0)
                : Double.NaN;
        boolean sell = activeAfterEvaluation && validDecline && currentPrice <= triggerPrice;

        return new TrailingStopDecision(
                peakPrice, highestProfitPct, activeAfterEvaluation, triggerPrice, sell);
    }

    record TrailingStopDecision(double peakPrice, double highestProfitPct,
            boolean active, double triggerPrice, boolean sell) {
    }

    private static void executeSell(String currency, FusionApiClient client, String BuyOrderId, String Quantity_String) {

        String validatedQuantity;
        try {
            validatedQuantity = TradingRulesFormatter.formatOrderQuantity(currency, new BigDecimal(Quantity_String));

            if (!TradingRulesFormatter.isQuantityValid(currency, new BigDecimal(validatedQuantity))) {
                System.out.println("⚠️ Quantity ungültig für " + currency + ": " + validatedQuantity);
                return;
            }
        } catch (RuntimeException ex) {
            System.err.println("Ungültige Sell-Order für " + BuyOrderId + ": " + ex.getMessage());
            return;
        }

        Optional<Reservation> reserved;
        try {
            reserved = sellOrderPersistence.reserve(BuyOrderId, currency, validatedQuantity);
        } catch (SQLException ex) {
            System.err.println("KRITISCH: Position " + BuyOrderId
                    + " konnte vor dem Verkauf nicht atomar reserviert werden: " + ex.getMessage());
            return;
        }

        if (reserved.isEmpty()) {
            System.out.println("Sell übersprungen: Position " + BuyOrderId
                    + " wurde bereits reserviert oder ist nicht mehr verkaufbar.");
            return;
        }
        Reservation reservation = reserved.get();

        NewOrderResponse orderResponse;
        try {
            orderResponse = getNewSellOrderResponse(currency, client, validatedQuantity);
        } catch (FusionApiException ex) {
            handleSubmissionFailure(reservation, ex);
            System.err.println("Fehler beim Verkauf: " + ex.getMessage() + " " + currency);
            sleep.for_10_seconds();
            return;
        } catch (RuntimeException ex) {
            requireReconciliation(reservation, null,
                    "Unerwarteter Fehler während der Sell-Übermittlung: " + ex.getMessage());
            System.err.println("KRITISCH: Unerwarteter Fehler während der Sell-Übermittlung für Position "
                    + BuyOrderId + "; die Position bleibt gesperrt: " + ex.getMessage());
            return;
        }

        String sellOrderId = orderResponse == null ? null : orderResponse.getOrderId();
        if (sellOrderId == null || sellOrderId.isBlank()) {
            requireReconciliation(reservation, null,
                    "Fusion hat die Sell-Anfrage beantwortet, aber keine Order-ID geliefert");
            System.err.println("KRITISCH: Sell-Ausgang für Position " + BuyOrderId
                    + " ist unklar; die Position bleibt gesperrt.");
            return;
        }

        try {
            sellOrderPersistence.recordSubmitted(reservation, sellOrderId, Time.getCurrentDate(), Time.getCurrentTime_HHmmss());
            System.out.println("DEBUG: Verkauf eingereicht für BuyOrderId: "
                    + BuyOrderId + " " + currency + ", SellOrderId: " + sellOrderId);
        } catch (SQLException ex) {
            requireReconciliation(reservation, sellOrderId,
                    "Lokale Verbuchung der angenommenen Sell-Order fehlgeschlagen: " + ex.getMessage());
            System.err.println("KRITISCH: Fusion-Sell-Order " + sellOrderId
                    + " existiert, konnte aber lokal nicht vollständig verbucht werden. "
                    + "Die Position bleibt gesperrt: " + ex.getMessage());
        }
    }

    private static void handleSubmissionFailure(Reservation reservation, FusionApiException ex) {
        int statusCode = ex.getStatusCode();
        boolean definiteRejection = statusCode >= 400 && statusCode < 500 && statusCode != 408;
        try {
            if (definiteRejection) {
                sellOrderPersistence.recordRejected(reservation, ex.getMessage());
            } else {
                sellOrderPersistence.requireReconciliation(reservation, null, ex.getMessage());
                System.err.println("KRITISCH: Der Ausgang der Sell-Anfrage für Position "
                        + reservation.buyOrderId() + " ist unklar; die Position bleibt gesperrt.");
            }
        } catch (SQLException persistenceError) {
            System.err.println("KRITISCH: Fehlerstatus des Sell-Versuchs " + reservation.attemptId()
                    + " konnte nicht gespeichert werden: " + persistenceError.getMessage());
        }
    }

    private static void requireReconciliation(Reservation reservation, String sellOrderId, String reason) {
        try {
            sellOrderPersistence.requireReconciliation(reservation, sellOrderId, reason);
        } catch (SQLException persistenceError) {
            System.err.println("KRITISCH: Sell-Versuch " + reservation.attemptId()
                    + " konnte nicht als ungeklärt markiert werden: " + persistenceError.getMessage());
        }
    }

    public static NewOrderResponse getNewSellOrderResponse(String CurrencyPair, FusionApiClient client, String Quantity_String) {
        NewOrderResponse newOrderResponse = client.newOrder(marketSell(CurrencyPair, Quantity_String));
        return newOrderResponse;
    }

    public static void handleSellProcess(String currency, FusionApiClient client) {

        double livePrice = Ticker.getAssetPrice(currency, client);
        List<String> BuyAmountRecord = positionDAO.getPositionWithMaxInMinus(currency, livePrice);

        if (BuyAmountRecord.isEmpty()) {
            return;
        }
        String record = BuyAmountRecord.get(0);
        String[] recordParts = record.split(", ");

        String BuyOrderId = recordParts[0];
        String Quantity_String = recordParts[1];
        String CurrencyPair = recordParts[2];

        executeSell(CurrencyPair, client, BuyOrderId, Quantity_String);
    }
}
