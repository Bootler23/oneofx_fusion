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

            // ---- Dynamischer Trailing Stop Loss --------------------------------
            // Aktivierung: highestProfitPct >= TSLactivate (z.B. 1.2 %)
            // Stop wird proportional gezogen:
            //   trailingFactor   = (TSLactivate - TSLdecline) / TSLactivate   (z.B. 0.6667)
            //   trailingStopPct  = highestProfitPct * trailingFactor
            //   tslTriggerPrice  = buyPrice * (1 + trailingStopPct/100)
            // → Je höher der Peak, desto größer der Abstand zum Stop.
            if (currencyDAO.getTSL(currency)) {
                double tslActivatePct = currencyDAO.getTSLActivate(currency);
                double tslDeclinePct  = currencyDAO.getTSLDecline(currency);

                double peakPrice = positionDAO.getPeakPrice(BuyOrderId);
                if (peakPrice <= 0) {
                    peakPrice = Math.max(BuyPrice_Double, currentPrice);
                    positionDAO.updatePeakPrice(BuyOrderId, peakPrice);
                } else if (currentPrice > peakPrice) {
                    peakPrice = currentPrice;
                    positionDAO.updatePeakPrice(BuyOrderId, peakPrice);
                }

                double highestProfitPct = (peakPrice - BuyPrice_Double) / BuyPrice_Double * 100.0;

                // Profit immer aktualisieren (positiv oder negativ)
                double profitPct = round.three((currentPrice - BuyPrice_Double) / BuyPrice_Double * 100.0);
                positionDAO.update(new Position.Builder(null, BuyOrderId)
                        .profit(profitPct)
                        .build());

                if (tslActivatePct > 0 && highestProfitPct >= tslActivatePct) {
                    // TSL aktiv → Status in DB setzen
                    positionDAO.update(new Position.Builder(null, BuyOrderId)
                            .tsl("active")
                            .build());

                    double trailingFactor  = (tslActivatePct - tslDeclinePct) / tslActivatePct;
                    double trailingStopPct = highestProfitPct * trailingFactor;
                    double tslTriggerPrice = BuyPrice_Double * (1.0 + trailingStopPct / 100.0);

                    if (currentPrice <= tslTriggerPrice) {
                        empty.Line();
                        System.out.println("🟡 TRAILING STOP-LOSS: " + currency
                                + " | Buy: " + BuyPrice_Double
                                + " | Peak: " + round.four(peakPrice)
                                + " | HighProfit%: " + round.three(highestProfitPct)
                                + " | StopProfit%: " + round.three(trailingStopPct)
                                + " | Trigger: " + round.four(tslTriggerPrice)
                                + " | Aktuell: " + currentPrice);
                        executeSell(currency, client, BuyOrderId, Quantity_String);
                        continue;
                    }
                }
            }
        }
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
