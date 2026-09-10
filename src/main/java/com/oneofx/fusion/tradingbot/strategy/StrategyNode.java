package com.oneofx.fusion.tradingbot.strategy;

public record StrategyNode(long id, long strategyId, Long parentId, int position,
        NodeType type, Action action, Logic logic, Indicator leftIndicator,
        Comparator comparator, Indicator rightIndicator, double compareValue,
        String timeframe, int period, int secondaryPeriod) {

    public enum NodeType { GROUP, CONDITION }
    public enum Action { BUY, SELL, BLOCK, CONFIRM }
    public enum Logic { AND, OR }
    public enum Comparator { GREATER_THAN, GREATER_OR_EQUAL, LESS_THAN, LESS_OR_EQUAL, CROSS_ABOVE, CROSS_BELOW }
    public enum Indicator {
        VALUE, PRICE, SMA, EMA, RSI, MACD, MACD_SIGNAL, STOCH_RSI,
        BOLLINGER_UPPER, BOLLINGER_LOWER, ATR, CCI, VOLUME,
        BULLISH_ENGULFING, BEARISH_ENGULFING, HAMMER, SHOOTING_STAR
    }

    public StrategyNode {
        if (type == null || action == null) throw new IllegalArgumentException("Knotentyp und Aktion fehlen.");
        logic = logic == null ? Logic.AND : logic;
        if (type == NodeType.CONDITION) {
            if (leftIndicator == null || leftIndicator == Indicator.VALUE || comparator == null)
                throw new IllegalArgumentException("Eine Bedingung benötigt Indikator und Vergleich.");
            rightIndicator = rightIndicator == null ? Indicator.VALUE : rightIndicator;
            timeframe = timeframe == null || timeframe.isBlank() ? "1d" : timeframe;
            if (period < 1 || secondaryPeriod < 1)
                throw new IllegalArgumentException("Indikatorperioden müssen größer als 0 sein.");
        }
    }
}
