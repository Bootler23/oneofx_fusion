package com.oneofx.fusion.tradingbot.desktop;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Optional;

import com.oneofx.fusion.client.FusionApiClient;
import com.oneofx.fusion.tradingbot.HelperFunctions.TradingRulesFormatter;
import com.oneofx.fusion.tradingbot.SQL_Database.CurrencyDAO;
import com.oneofx.fusion.tradingbot.SQL_Database.StrategyStateDAO;
import com.oneofx.fusion.tradingbot.Settings.FusionClientProvider;
import com.oneofx.fusion.tradingbot.Stream.PricePoller;
import com.oneofx.fusion.tradingbot.bot.BotRuntime;
import com.oneofx.fusion.tradingbot.desktop.PaperTradingRepository.PaperPosition;
import com.oneofx.fusion.tradingbot.desktop.PaperTradingRepository.PaperRisk;
import com.oneofx.fusion.tradingbot.service.GridOrderPlanner;
import com.oneofx.fusion.tradingbot.service.MarketRegimeService;
import com.oneofx.fusion.tradingbot.service.MarketRegimeService.Regime;
import com.oneofx.fusion.tradingbot.service.TradingDecisionPolicy;
import com.oneofx.fusion.tradingbot.strategy.StrategyEvaluation;
import com.oneofx.fusion.tradingbot.strategy.StrategyEvaluationService;

/** Lokale Simulation mit echten Fusion-Marktdaten, aber ohne Orderuebermittlung. */
final class PaperTradingEngine implements TradingExecution {
    private static final int MAX_PENDING_PER_PAIR = 2;
    private static final Duration HARD_STOP_COOLDOWN = Duration.ofHours(24);

    private final BotProfile bot;
    private final BotRepository bots = new BotRepository();
    private final CurrencySettingsRepository settings = new CurrencySettingsRepository();
    private final PaperTradingRepository paper = new PaperTradingRepository();
    private final OperationsRepository activity = new OperationsRepository();
    private final BaseConfigRepository baseConfigs = new BaseConfigRepository();
    private final CurrencyDAO currencyDao = new CurrencyDAO();
    private final StrategyStateDAO strategyState = new StrategyStateDAO();
    private final StrategyEvaluationService strategyEvaluation = new StrategyEvaluationService();
    private volatile boolean running = true;
    private volatile PricePoller poller;

    PaperTradingEngine(BotProfile bot) { this.bot = bot; }

    @Override
    public void run() {
        BotRuntime.select(bot.id());
        try {
            paper.ensureAccount(bot.id(), bot.budget());
            List<CurrencySettings> pairs = settings.loadAll(bot.id());
            if (pairs.isEmpty()) throw new IllegalStateException("Keine Handelspaare konfiguriert.");
            String[] symbols = pairs.stream().map(CurrencySettings::currency).toArray(String[]::new);
            poller = new PricePoller();
            poller.start(symbols);
            event("INFO", null, "Paper-Engine gestartet; es werden keine Live-Orders gesendet.");

            FusionApiClient client = FusionClientProvider.getClient();
            while (running) {
                BotProfile current = bots.load(bot.id());
                if (!current.enabled() || current.mode() != BotProfile.Mode.PAPER) break;
                pairs = settings.loadAll(bot.id());
                poller.addSymbols(pairs.stream().map(CurrencySettings::currency).toArray(String[]::new));
                for (CurrencySettings pair : pairs) {
                    if (!running) break;
                    try { processPair(current, pair, client); }
                    catch (Exception ex) { event("ERROR", pair.currency(), ex.getMessage()); }
                }
                Thread.sleep(1_000L);
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        } catch (Exception ex) {
            throw new IllegalStateException("Paper-Engine fehlgeschlagen: " + ex.getMessage(), ex);
        } finally {
            if (poller != null) poller.stop();
            event("INFO", null, "Paper-Engine gestoppt.");
        }
    }

    private void processPair(BotProfile currentBot, CurrencySettings pair,
            FusionApiClient client) throws Exception {
        String currency = pair.currency();
        BotBaseConfig executionConfig = baseConfigs.loadEffective(bot.id(), currency);
        Double current = poller.getPrice(currency);
        if (current == null || current <= 0) return;
        currencyDao.checkForNewAllTimeHigh(currency, List.of(current));

        int fills = paper.fillTriggeredBuys(bot.id(), currency, current);
        if (fills > 0) event("INFO", currency, fills + " Paper-Kauforder(s) ausgefuehrt.");
        int sellFills = paper.fillTriggeredSells(bot.id(), currency, current,
                currentBot.paperFeePercent());
        if (sellFills > 0) event("INFO", currency,
                sellFills + " manuelle Paper-Verkaufsorder(s) ausgefuehrt.");
        int expired = paper.expireOpenBuys(bot.id(), currency,
                executionConfig.maxBuyOrderMinutes());
        if (expired > 0) event("INFO", currency,
                expired + " Paper-Kauforder(s) wegen Laufzeit storniert.");

        MarketRegimeService.Snapshot market = MarketRegimeService.getInstance()
                .getSnapshot(currency, client);
        Optional<StrategyEvaluation> customStrategy = strategyEvaluation.evaluateAssigned(
                bot.id(), currency, market.regime().name(), client);
        boolean sellSignal = customStrategy.map(StrategyEvaluation::sell)
                .orElse(market.regime() == Regime.EXIT);
        for (PaperPosition position : paper.loadOpenPositions(bot.id(), currency)) {
            TradingDecisionPolicy.ExitDecision decision = TradingDecisionPolicy.evaluateExit(
                    position.entryPrice(), current, position.peakPrice(), position.trailingActive(),
                    pair.stopLoss(), pair.trailingStopEnabled(), pair.trailingStopActivation(),
                    pair.trailingStopDecline(), executionConfig.takeProfitPercent());
            double pnl = (current - position.entryPrice()) * position.quantity() - position.fees();
            paper.updatePosition(position.positionId(), decision.peakPrice(), decision.trailingActive(), pnl);
            String reason = null;
            boolean profitable = current > position.entryPrice();
            if (decision.shouldExit()) reason = decision.reason().name();
            else if (sellSignal && (!executionConfig.onlySellWithProfit() || profitable))
                reason = customStrategy.isPresent() ? "CUSTOM_STRATEGY_SELL" : "MARKET_REGIME_EXIT";
            else if (isExpired(position.openedAt(), executionConfig.closeAfterMinutes())
                    && (!executionConfig.onlySellWithProfit() || profitable)) reason = "MAX_POSITION_AGE";
            if (reason != null && paper.closePosition(bot.id(), position, current,
                    currentBot.paperSlippagePercent(), currentBot.paperFeePercent(), reason,
                    executionConfig.sellOrderType())) {
                event("INFO", currency, "Paper-Position verkauft: " + reason);
                if (decision.reason() == TradingDecisionPolicy.ExitReason.HARD_STOP_LOSS) {
                    strategyState.blockBuys(currency, HARD_STOP_COOLDOWN, "PAPER_HARD_STOP_LOSS");
                } else if (executionConfig.cooldownMinutes() > 0) {
                    strategyState.blockBuys(currency,
                            Duration.ofMinutes(executionConfig.cooldownMinutes()), "PAPER_SELL_COOLDOWN");
                }
            }
        }

        boolean strategyBuyAllowed = customStrategy.map(StrategyEvaluation::buyAllowed)
                .orElse(market.regime() == Regime.BUY_ALLOWED);
        boolean mayBuy = pair.buyEnabled() && strategyBuyAllowed
                && !strategyState.isBuyBlocked(currency);
        if (mayBuy && executionConfig.trailingStopBuyEnabled()) {
            mayBuy = baseConfigs.evaluateTrailingBuy(bot.id(), currency, current,
                    executionConfig.trailingStopBuyActivationPercent(),
                    executionConfig.trailingStopBuyReboundPercent());
        }
        if (!mayBuy) {
            int cancelled = paper.cancelOpenBuys(bot.id(), currency,
                    customStrategy.isPresent() ? "CUSTOM_STRATEGY" : "STRATEGY_" + market.regime().name());
            if (cancelled > 0) event("INFO", currency,
                    cancelled + " Paper-Kauforder(s) storniert.");
            return;
        }
        replenishGrid(currentBot, pair, executionConfig, current);
        if (executionConfig.trailingStopBuyEnabled()) {
            baseConfigs.resetTrailingBuy(bot.id(), currency, current);
        }
    }

    private void replenishGrid(BotProfile currentBot, CurrencySettings pair,
            BotBaseConfig executionConfig, double current)
            throws Exception {
        int pending = paper.countOpenBuys(bot.id(), pair.currency());
        if (pending >= MAX_PENDING_PER_PAIR) return;
        double anchor = Math.max(currencyDao.getAllTimeHigh(pair.currency()), current);
        Set<Double> occupied = new HashSet<>(paper.occupiedPrices(bot.id(), pair.currency()));
        List<PaperPosition> positions = paper.loadOpenPositions(bot.id(), pair.currency());
        double amount = pair.buyAmount();
        if (!positions.isEmpty()) {
            if (!executionConfig.dcaEnabled()
                    || positions.size() > executionConfig.dcaMaxOrders()) return;
            double lowestEntry = positions.stream().mapToDouble(PaperPosition::entryPrice).min().orElse(current);
            if (current > lowestEntry * (1.0 - executionConfig.dcaTriggerPercent() / 100.0)) return;
            amount *= Math.pow(executionConfig.dcaSizeMultiplier(), positions.size());
        }
        List<Double> candidates = "MARKET".equals(executionConfig.buyOrderType())
                ? List.of(TradingRulesFormatter.formatPrice(pair.currency(),
                        current * (1.0 + currentBot.paperSlippagePercent() / 100.0)))
                : GridOrderPlanner.candidates(anchor, current, pair.gridSettings(),
                        price -> TradingRulesFormatter.formatPrice(pair.currency(), price),
                        occupied::contains, MAX_PENDING_PER_PAIR - pending, 2);
        for (double price : candidates) {
            PaperRisk risk = paper.loadRisk(bot.id());
            double botLimit = Math.min(currentBot.budget(), currentBot.maxExposure());
            if (risk.openPositions() >= currentBot.maxOpenPositions()
                    || risk.openOrders() >= currentBot.maxOpenOrders()
                    || risk.exposure() + amount > botLimit + 0.000001
                    || paper.loadPairExposure(bot.id(), pair.currency()) + amount
                            > pair.maxBuyAmount() + 0.000001) return;
            String quantityText = TradingRulesFormatter.calculateAndFormatQuantity(
                    pair.currency(), BigDecimal.valueOf(amount), BigDecimal.valueOf(price));
            double quantity = new BigDecimal(quantityText).doubleValue();
            if (!TradingRulesFormatter.isOrderValid(pair.currency(),
                    BigDecimal.valueOf(price), BigDecimal.valueOf(quantity))) return;
            if (!paper.placeBuy(bot.id(), pair.currency(), price, quantity,
                    currentBot.paperFeePercent(), executionConfig.buyOrderType())) return;
            occupied.add(price);
            pending++;
            event("INFO", pair.currency(), "Paper-" + executionConfig.buyOrderType()
                    + "-Kauf platziert bei " + price + " EUR.");
            if ("MARKET".equals(executionConfig.buyOrderType())) {
                paper.fillTriggeredBuys(bot.id(), pair.currency(), current);
                break;
            }
        }
    }

    private static boolean isExpired(String openedAt, int minutes) {
        if (minutes <= 0 || openedAt == null || openedAt.isBlank()) return false;
        try {
            LocalDateTime opened = LocalDateTime.parse(openedAt,
                    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            return opened.plusMinutes(minutes).isBefore(LocalDateTime.now());
        } catch (RuntimeException ex) { return false; }
    }

    private void event(String severity, String currency, String message) {
        try { activity.recordEvent(bot.id(), severity, "PAPER", currency, message); }
        catch (Exception ex) { System.err.println("Paper-Protokoll: " + ex.getMessage()); }
    }

    @Override
    public void stop() {
        running = false;
        PricePoller current = poller;
        if (current != null) current.stop();
    }
}
