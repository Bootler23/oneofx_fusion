package com.binance.api.tradingbot.constants;

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

    public static final String TABLE_POS = "POS";
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
    
    // ========= Rate-Limit-Konfiguration =================
    
    /**
     * Intervall für Rate-Limit-Statusausgabe in Sekunden.
     * Alle 5 Sekunden wird eine Übersicht über die API-Auslastung ausgegeben.
     */
    public static final int RATE_LIMIT_REPORT_INTERVAL_SECONDS = 5;
    
    /**
     * Warnschwelle für Rate-Limit-Auslastung (0.0 - 1.0).
     * Bei Überschreitung wird eine Warnung auf System.err ausgegeben.
     * Standard: 0.80 = 80% von 6000 Weight = 4800 Weight
     */
    public static final double RATE_LIMIT_WARNING_THRESHOLD = 0.80;
    
    /**
     * Aktiviert/Deaktiviert das Rate-Limit-Tracking.
     * Bei true werden alle API-Calls überwacht und alle 5 Sekunden ein Report ausgegeben.
     */
    public static final boolean RATE_LIMIT_TRACKING_ENABLED = true;

    // Verhindern von Instanziierung (Utility-Klasse)
    private TradingConstants() {
        throw new AssertionError("Cannot instantiate TradingConstants - this is a utility class");
    }
}
