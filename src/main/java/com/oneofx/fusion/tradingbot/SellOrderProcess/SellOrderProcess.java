package com.oneofx.fusion.tradingbot.SellOrderProcess;

import static com.oneofx.fusion.client.model.NewOrder.marketSell;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import com.oneofx.fusion.client.FusionApiClient;
import com.oneofx.fusion.client.model.NewOrderResponse;
import com.oneofx.fusion.client.model.NewOrder;
import com.oneofx.fusion.client.model.OrderSide;
import com.oneofx.fusion.client.model.OrderType;
import com.oneofx.fusion.client.model.TimeInForce;
import com.oneofx.fusion.client.FusionApiException;
import com.oneofx.fusion.tradingbot.BuyOrderProcess.Ticker;
import com.oneofx.fusion.tradingbot.HelperFunctions.Time;
import com.oneofx.fusion.tradingbot.HelperFunctions.empty;
import com.oneofx.fusion.tradingbot.HelperFunctions.round;
import com.oneofx.fusion.tradingbot.HelperFunctions.sleep;
import com.oneofx.fusion.tradingbot.SQL_Database.CurrencyDAO;
import com.oneofx.fusion.tradingbot.SQL_Database.PositionDAO;
import com.oneofx.fusion.tradingbot.SQL_Database.SellOrderPersistence;
import com.oneofx.fusion.tradingbot.SQL_Database.StrategyStateDAO;
import com.oneofx.fusion.tradingbot.SQL_Database.SellOrderPersistence.Reservation;
import com.oneofx.fusion.tradingbot.HelperFunctions.TradingRulesFormatter;
import com.oneofx.fusion.tradingbot.domain.Position;
import com.oneofx.fusion.tradingbot.service.TradingDecisionPolicy;
import com.oneofx.fusion.tradingbot.desktop.BaseConfigRepository;
import com.oneofx.fusion.tradingbot.desktop.BotBaseConfig;

public class SellOrderProcess {

    private static final PositionDAO positionDAO = new PositionDAO();
    private static final CurrencyDAO currencyDAO = new CurrencyDAO();
    private static final SellOrderPersistence sellOrderPersistence = new SellOrderPersistence();
    private static final StrategyStateDAO strategyStateDAO = new StrategyStateDAO();
    private static final BaseConfigRepository baseConfigRepository = new BaseConfigRepository();
    private static final Duration HARD_STOP_BUY_COOLDOWN = Duration.ofHours(24);

    public static void setSellOrder(String currency, FusionApiClient client,
            List<String> GetRecordFromDataBase_POS, List<Double> LivePrice) {

        double currentPrice = LivePrice.get(0);
        BotBaseConfig baseConfig;
        try {
            baseConfig = baseConfigRepository.loadEffective(currency);
        } catch (SQLException ex) {
            baseConfig = BotBaseConfig.defaults(0);
            System.err.println("Baseconfig fuer " + currency
                    + " fehlt; sichere Standardwerte werden verwendet: " + ex.getMessage());
        }

        for (String dataRecord : GetRecordFromDataBase_POS) {
            String[] parts = dataRecord.split(", ");

            String BuyOrderId = parts[0];
            String Quantity_String = parts[3];
            String BuyPrice_String = parts[5];

            double BuyPrice_Double = Double.valueOf(BuyPrice_String);

            if (BuyPrice_Double <= 0) {
                continue;
            }

            if (baseConfig.takeProfitPercent() > 0
                    && currentPrice >= BuyPrice_Double
                            * (1.0 + baseConfig.takeProfitPercent() / 100.0)) {
                System.out.println("TAKE PROFIT: " + currency + " | Buy: " + BuyPrice_Double
                        + " | Aktuell: " + currentPrice);
                executeSell(currency, client, BuyOrderId, Quantity_String, currentPrice);
                continue;
            }

            if (parts.length > 7 && isPositionExpired(parts[6], parts[7],
                    baseConfig.closeAfterMinutes())
                    && (!baseConfig.onlySellWithProfit() || currentPrice > BuyPrice_Double)) {
                System.out.println("ZEIT-AUSSTIEG: " + currency + " | Position: " + BuyOrderId);
                executeSell(currency, client, BuyOrderId, Quantity_String, currentPrice);
                continue;
            }

            double stopLossPercent = currencyDAO.getStopLossPercent(currency);
            if (shouldTriggerHardStop(BuyPrice_Double, currentPrice, stopLossPercent)) {
                empty.Line();
                System.out.println("HARD STOP-LOSS: " + currency
                        + " | Buy: " + BuyPrice_Double
                        + " | Stop: " + round.four(BuyPrice_Double * (1.0 - stopLossPercent / 100.0))
                        + " | Aktuell: " + currentPrice);
                // Vor dem Verkauf speichern. Auch ein unklarer Orderausgang darf
                // keinen sofortigen Ersatzkauf auslösen.
                try {
                    strategyStateDAO.blockBuys(
                            currency, HARD_STOP_BUY_COOLDOWN, "HARD_STOP_LOSS");
                } catch (IllegalStateException ex) {
                    // Der Lesepfad sperrt Käufe bei einem Fehler. Der Verkauf bleibt
                    // deshalb sinnvoll, auch wenn das Ablaufdatum nicht gespeichert wurde.
                    System.err.println("KRITISCH: " + ex.getMessage());
                }
                executeSell(currency, client, BuyOrderId, Quantity_String, currentPrice);
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
                    executeSell(currency, client, BuyOrderId, Quantity_String, currentPrice);
                    continue;
                }
            }
        }
    }

    static TrailingStopDecision evaluateTrailingStop(double buyPrice, double currentPrice,
            double storedPeakPrice, boolean active, double activationPct, double declinePct) {
        TradingDecisionPolicy.ExitDecision decision = TradingDecisionPolicy.evaluateExit(
                buyPrice, currentPrice, storedPeakPrice, active, 0.0, true,
                activationPct, declinePct);
        return new TrailingStopDecision(decision.peakPrice(),
                decision.highestProfitPercent(), decision.trailingActive(),
                decision.triggerPrice(), decision.shouldExit());
    }

    record TrailingStopDecision(double peakPrice, double highestProfitPct,
            boolean active, double triggerPrice, boolean sell) {
    }

    static boolean shouldTriggerHardStop(double buyPrice, double currentPrice,
            double stopLossPercent) {
        return TradingDecisionPolicy.isHardStop(buyPrice, currentPrice, stopLossPercent);
    }

    /** Schließt bei einem bestätigten Regime-Ausstieg alle verkaufbaren Bot-Positionen. */
    public static void closePositionsForRegime(String currency, FusionApiClient client,
            List<String> positionRecords) {
        closePositionsForRegime(currency, client, positionRecords, Double.NaN);
    }

    public static void closePositionsForRegime(String currency, FusionApiClient client,
            List<String> positionRecords, double currentPrice) {
        BotBaseConfig config;
        try { config = baseConfigRepository.loadEffective(currency); }
        catch (SQLException ex) { config = BotBaseConfig.defaults(0); }
        for (String dataRecord : positionRecords) {
            String[] parts = dataRecord.split(", ");
            if (parts.length < 6) {
                System.err.println("Ungueltiger Positionsdatensatz beim MACD-Ausstieg: " + dataRecord);
                continue;
            }
            String buyOrderId = parts[0];
            String quantity = parts[3];
            double buyPrice;
            try { buyPrice = Double.parseDouble(parts[5]); }
            catch (RuntimeException ex) { buyPrice = Double.NaN; }
            if (config.onlySellWithProfit() && Double.isFinite(currentPrice)
                    && Double.isFinite(buyPrice) && currentPrice <= buyPrice) {
                System.out.println("Regime-Ausstieg fuer " + buyOrderId
                        + " durch Gewinnbedingung zurueckgestellt.");
                continue;
            }
            System.out.println("1D-MACD EXIT: Schliesse " + currency
                    + " Position " + buyOrderId);
            executeSell(currency, client, buyOrderId, quantity, currentPrice);
        }
    }

    private static void executeSell(String currency, FusionApiClient client, String BuyOrderId,
            String Quantity_String, double currentPrice) {

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
            orderResponse = client.newOrder(createSellOrder(currency, validatedQuantity,
                    currentPrice));
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
            try {
                BotBaseConfig config = baseConfigRepository.loadEffective(currency);
                if (config.cooldownMinutes() > 0) {
                    strategyStateDAO.blockBuys(currency,
                            Duration.ofMinutes(config.cooldownMinutes()), "SELL_COOLDOWN");
                }
            } catch (SQLException ex) {
                System.err.println("Cooldown konnte nicht geladen werden: " + ex.getMessage());
            }
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

    private static NewOrder createSellOrder(String currency, String quantity, double currentPrice) {
        BotBaseConfig config;
        try { config = baseConfigRepository.loadEffective(currency); }
        catch (SQLException ex) { config = BotBaseConfig.defaults(0); }
        OrderType type = OrderType.valueOf(config.sellOrderType());
        if (!TradingRulesFormatter.supportsOrderType(currency, type)) {
            throw new IllegalStateException("Fusion erlaubt " + type + " für " + currency
                    + " laut gespeichertem Paarkatalog nicht.");
        }
        if (type == OrderType.MARKET || !Double.isFinite(currentPrice) || currentPrice <= 0) {
            return marketSell(currency, quantity);
        }
        TimeInForce tif = config.maxSellOrderMinutes() > 0 ? TimeInForce.GTD : TimeInForce.GTC;
        String price = TradingRulesFormatter.formatOrderPrice(currency, currentPrice);
        NewOrder order = new NewOrder(currency, OrderSide.SELL, type, tif, quantity,
                type == OrderType.LIMIT ? price : null);
        if (type == OrderType.STOP_MARKET) order.triggerPrice(price);
        if (tif == TimeInForce.GTD) order.endTime(Instant.now()
                .plus(config.maxSellOrderMinutes(), ChronoUnit.MINUTES).toString());
        return order;
    }

    private static boolean isPositionExpired(String date, String time, int maximumMinutes) {
        if (maximumMinutes <= 0 || date == null || time == null) return false;
        try {
            LocalDateTime opened = LocalDateTime.parse(date + " " + time,
                    DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss"));
            return opened.plusMinutes(maximumMinutes).isBefore(LocalDateTime.now());
        } catch (RuntimeException ex) { return false; }
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

        executeSell(CurrencyPair, client, BuyOrderId, Quantity_String, livePrice);
    }
}
