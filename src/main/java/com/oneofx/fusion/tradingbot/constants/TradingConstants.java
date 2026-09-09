package com.oneofx.fusion.tradingbot.constants;

public final class TradingConstants {

    // ========== Status-Codes (kompatibel mit bestehender Datenbank) ==========

    public static final String STATUS_NEW = "NEW";
    public static final String STATUS_FILLED = "FILLED";
    public static final String STATUS_FILLED_CHECKED = "FILLED CHECKED";
    public static final String STATUS_PARTIALLY_FILLED = "PARTIALLY_FILLED";
    public static final String STATUS_CANCELLED = "CANCELLED";

    /** Position is reserved locally while a sell request is sent to Fusion. */
    public static final int POSITION_STATUS_SELL_SUBMITTING = 8;

    /** Fusion accepted the sell order; its terminal execution is still pending. */
    public static final int POSITION_STATUS_SELL_PENDING = 2;

    // ========== Update-Intervalle ==========

    public static final int UPDATE_CYCLE_COUNT = 11;
    public static final long POLL_INTERVAL_MS = 1000;
    public static final long ERROR_RETRY_MS = 60000;

    // ========== Trading-Parameter ==========

    public static final String BASE_CURRENCY = "EUR";

    // ========= Datenbanktabellen ==========

    public static final String TABLE_POS = "positions";
    public static final String TABLE_HIST = "historyPosition";
    public static final String TABLE_ATH = "ATH";
    public static final String TABLE_SET = "SET";
    public static final String TABLE_WPD = "WPD";

    // ========= Spalten ====================

    public static final String[] HIST_COLUMNS_SELL_TRADES = {"SellOrderId", "Quantity", "BuyPrice", "currency" };
    public static final String[] POS_COLUMNS_BUY_TRADES = {"BuyOrderId", "currency"};

    // ========= min BuyAmount ==============

    public static final double MIN_BUY_AMOUNT = 5.5;

    public static final double DEFAULT_PROFIT_TAX_RATE_PERCENT = 42.0;
    public static final String PROFIT_TAX_RATE_PROPERTY = "oneofx.profitTaxRatePercent";
    public static final String PROFIT_TAX_RATE_ENV = "ONEOFX_PROFIT_TAX_RATE_PERCENT";

    public static double getProfitTaxRatePercent() {
        String configured = System.getProperty(PROFIT_TAX_RATE_PROPERTY);
        if (configured == null || configured.isBlank()) {
            configured = System.getenv(PROFIT_TAX_RATE_ENV);
        }
        if (configured == null || configured.isBlank()) {
            return DEFAULT_PROFIT_TAX_RATE_PERCENT;
        }
        try {
            double rate = Double.parseDouble(configured.trim());
            if (!Double.isFinite(rate) || rate < 0.0 || rate > 100.0) {
                throw new IllegalArgumentException("Steuersatz muss zwischen 0 und 100 liegen");
            }
            return rate;
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Ungueltiger Steuersatz: " + configured, ex);
        }
    }
    
    // ========= ROI & Split =================
    
    public static final double MIN_SPLIT_VALUE = 0.01; // Minimaler Split-Wert in EUR
    
    // FusionApiClient schützt zentral vor den veröffentlichten API-Limits.
    public static final int TRADING_LOOP_DELAY_MS = 300;

    // Verhindern von Instanziierung (Utility-Klasse)
    private TradingConstants() {
        throw new AssertionError("Cannot instantiate TradingConstants - this is a utility class");
    }
}
