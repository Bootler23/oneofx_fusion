package com.oneofx.fusion.tradingbot.constants;

public final class TradingConstants {

    // ========== Status-Codes (kompatibel mit bestehender Datenbank) ==========

    public static final String STATUS_NEW = "NEW";
    public static final String STATUS_FILLED = "FILLED";
    public static final String STATUS_FILLED_CHECKED = "FILLED CHECKED";
    public static final String STATUS_PARTIALLY_FILLED = "PARTIALLY_FILLED";
    public static final String STATUS_CANCELLED = "CANCELLED";

    // ========== Update-Intervalle ==========

    public static final int UPDATE_CYCLE_COUNT = 11;
    public static final long POLL_INTERVAL_MS = 1000;
    public static final long ERROR_RETRY_MS = 60000;

    // ========== Trading-Parameter ==========

    public static final String BASE_CURRENCY = "EUR";

    // ========= Datenbanktabellen ==========

    public static final String TABLE_POS = "positions";
    public static final String TABLE_HIST = "HIST";
    public static final String TABLE_ATH = "ATH";
    public static final String TABLE_SET = "SET";
    public static final String TABLE_WPD = "WPD";

    // ========= Spalten ====================

    public static final String[] HIST_COLUMNS_SELL_TRADES = {"SellOrderId", "Quantity", "BuyPrice", "Währung" };
    public static final String[] POS_COLUMNS_BUY_TRADES = {"BuyOrderId", "Währung"};

    // ========= min BuyAmount ==============

    public static final double MIN_BUY_AMOUNT = 5.5;
    
    // ========= ROI & Split =================
    
    public static final double MIN_SPLIT_VALUE = 0.01; // Minimaler Split-Wert in EUR
    
    // FusionApiClient schützt zentral vor den veröffentlichten API-Limits.
    public static final int TRADING_LOOP_DELAY_MS = 300;

    // ========= Currency-Watcher Konfiguration =================

    /**
     * Polling-Intervall in Sekunden, in dem die currency-Tabelle auf neue
     * Waehrungen (buystatus=true) geprueft wird.
     */
    public static final long CURRENCY_WATCH_INTERVAL_SECONDS = 60;

    // Verhindern von Instanziierung (Utility-Klasse)
    private TradingConstants() {
        throw new AssertionError("Cannot instantiate TradingConstants - this is a utility class");
    }
}
