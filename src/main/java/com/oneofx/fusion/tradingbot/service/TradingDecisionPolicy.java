package com.oneofx.fusion.tradingbot.service;

/**
 * Reine, ausfuehrungsunabhaengige Ausstiegsregeln. Live- und Paper-Engine
 * verwenden damit exakt dieselben Stop-Loss- und Trailing-Stop-Entscheidungen.
 */
public final class TradingDecisionPolicy {

    private TradingDecisionPolicy() { }

    public static ExitDecision evaluateExit(double entryPrice, double currentPrice,
            double storedPeakPrice, boolean trailingActive, double stopLossPercent,
            boolean trailingEnabled, double activationPercent, double declinePercent) {
        return evaluateExit(entryPrice, currentPrice, storedPeakPrice, trailingActive,
                stopLossPercent, trailingEnabled, activationPercent, declinePercent, 0);
    }

    public static ExitDecision evaluateExit(double entryPrice, double currentPrice,
            double storedPeakPrice, boolean trailingActive, double stopLossPercent,
            boolean trailingEnabled, double activationPercent, double declinePercent,
            double takeProfitPercent) {
        double peak = storedPeakPrice > 0
                ? Math.max(storedPeakPrice, currentPrice)
                : Math.max(entryPrice, currentPrice);
        double highestProfit = entryPrice > 0
                ? (peak - entryPrice) / entryPrice * 100.0 : Double.NaN;

        if (isHardStop(entryPrice, currentPrice, stopLossPercent)) {
            return new ExitDecision(ExitReason.HARD_STOP_LOSS, peak,
                    highestProfit, trailingActive, entryPrice * (1.0 - stopLossPercent / 100.0));
        }
        if (entryPrice > 0 && takeProfitPercent > 0 && takeProfitPercent < 100
                && currentPrice >= entryPrice * (1.0 + takeProfitPercent / 100.0)) {
            return new ExitDecision(ExitReason.TAKE_PROFIT, peak, highestProfit,
                    trailingActive, entryPrice * (1.0 + takeProfitPercent / 100.0));
        }

        boolean active = trailingEnabled && (trailingActive
                || (activationPercent > 0 && highestProfit >= activationPercent));
        boolean validDecline = declinePercent > 0 && declinePercent < 100;
        double trigger = active && validDecline
                ? peak * (1.0 - declinePercent / 100.0) : Double.NaN;
        ExitReason reason = active && validDecline && currentPrice <= trigger
                ? ExitReason.TRAILING_STOP : ExitReason.NONE;
        return new ExitDecision(reason, peak, highestProfit, active, trigger);
    }

    public static boolean isHardStop(double entryPrice, double currentPrice,
            double stopLossPercent) {
        return entryPrice > 0.0 && currentPrice > 0.0
                && stopLossPercent > 0.0 && stopLossPercent < 100.0
                && currentPrice <= entryPrice * (1.0 - stopLossPercent / 100.0);
    }

    public enum ExitReason {
        NONE,
        TAKE_PROFIT,
        HARD_STOP_LOSS,
        TRAILING_STOP
    }

    public record ExitDecision(ExitReason reason, double peakPrice,
            double highestProfitPercent, boolean trailingActive, double triggerPrice) {
        public boolean shouldExit() { return reason != ExitReason.NONE; }
    }
}
