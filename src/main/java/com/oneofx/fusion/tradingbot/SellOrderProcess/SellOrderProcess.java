package com.oneofx.fusion.tradingbot.SellOrderProcess;

import static com.oneofx.fusion.client.model.NewOrder.marketSell;

import java.util.List;

import com.oneofx.fusion.client.FusionApiClient;
import com.oneofx.fusion.client.model.NewOrderResponse;
import com.oneofx.fusion.client.FusionApiException;
import com.oneofx.fusion.tradingbot.BuyOrderProcess.Ticker;
import com.oneofx.fusion.tradingbot.HelperFunctions.Time;
import com.oneofx.fusion.tradingbot.HelperFunctions.empty;
import com.oneofx.fusion.tradingbot.HelperFunctions.round;
import com.oneofx.fusion.tradingbot.HelperFunctions.sleep;
import com.oneofx.fusion.tradingbot.SQL_Database.CurrencyDAO;
import com.oneofx.fusion.tradingbot.SQL_Database.HistDAO;
import com.oneofx.fusion.tradingbot.SQL_Database.PositionDAO;
import com.oneofx.fusion.tradingbot.HelperFunctions.TradingRulesFormatter;
import com.oneofx.fusion.tradingbot.domain.HistoryPosition;
import com.oneofx.fusion.tradingbot.domain.Position;
import java.math.BigDecimal;

public class SellOrderProcess {

    private static final PositionDAO positionDAO = new PositionDAO();
    private static final HistDAO histDAO = new HistDAO();
    private static final CurrencyDAO currencyDAO = new CurrencyDAO();

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

    private static void executeSell(String currency, FusionApiClient client, String BuyOrderId,
            String Quantity_String) {

        try {
            String validatedQuantity = TradingRulesFormatter.formatOrderQuantity(currency,
                    new BigDecimal(Quantity_String));

            if (!TradingRulesFormatter.isQuantityValid(currency, new BigDecimal(validatedQuantity))) {
                System.out.println("⚠️ Quantity ungültig für " + currency + ": " + validatedQuantity);
                return;
            }

            NewOrderResponse orderResponse = getNewSellOrderResponse(currency, client, validatedQuantity);

            System.out.println("DEBUG: Verkauf erfolgreich abgeschlossen für BuyOrderId: " + BuyOrderId + " " + currency);

            update_HIST_AfterMarketSell(BuyOrderId, Time.getCurrentTime_HHmmss(), Time.getCurrentDate(), orderResponse);
            update_POS_AfterMarketSell(BuyOrderId);

        } catch (FusionApiException ex) {
            System.err.println("Fehler beim Verkauf: Keine Menge vorhanden! " + ex.getMessage() + " " + currency);
            sleep.for_10_seconds();
        }
    }

    private static void update_HIST_AfterMarketSell(String BuyOrderId, String SellTime,
            String SellDate, NewOrderResponse newOrderResponse) {
        histDAO.updateByBuyOrderId(new HistoryPosition.Builder(null, BuyOrderId)
                .status(0)
                .sellOrderId(String.valueOf(newOrderResponse.getOrderId()))
                .sellTime(SellTime)
                .sellDate(SellDate)
                .build());
    }

    private static void update_POS_AfterMarketSell(String BuyOrderId) {
        positionDAO.update(new Position.Builder(null, BuyOrderId)
                .status(2)
                .build());
    }

    public static NewOrderResponse getNewSellOrderResponse(String CurrencyPair, FusionApiClient client,
            String Quantity_String) {
        NewOrderResponse newOrderResponse = client.newOrder(marketSell(CurrencyPair, Quantity_String));
        return newOrderResponse;
    }

    public static void handleSellProcess(String currency, FusionApiClient client) {

        double livePrice = Ticker.getAssetPrice(currency, client);
        List<String> BuyAmountRecord = positionDAO.getPositionWithMaxInMinus(currency, livePrice);

        String record = BuyAmountRecord.get(0);
        String[] recordParts = record.split(", ");

        String BuyOrderId = recordParts[0];
        String Quantity_String = recordParts[1];
        String CurrencyPair = recordParts[2];

        try {
            // Quantity nochmals durch Trading-Rules-Formatter laufen lassen zur Sicherheit
            String validatedQuantity = TradingRulesFormatter.formatOrderQuantity(CurrencyPair,
                    new BigDecimal(Quantity_String));

            // Validierung der Quantity
            if (!TradingRulesFormatter.isQuantityValid(CurrencyPair, new BigDecimal(validatedQuantity))) {
                System.out.println("⚠️ Quantity ungültig für " + CurrencyPair + ": " + validatedQuantity);
                return;
            }

            NewOrderResponse newOrderResponse = getNewSellOrderResponse(CurrencyPair, client, validatedQuantity);

            update_POS_AfterMarketSell(BuyOrderId);
            update_HIST_AfterMarketSell(BuyOrderId, Time.getCurrentTime_HHmmss(), Time.getCurrentDate(),
                    newOrderResponse);

        } catch (FusionApiException dex) {
            System.err.println("Fehler beim Verkauf: Keine Menge für den Verkauf verfügbar!");
            sleep.for_10_seconds();
        } catch (Exception e) {
            System.err.println("Fehler beim Verkaufsprozess: " + e.getMessage());
        }
    }
}
