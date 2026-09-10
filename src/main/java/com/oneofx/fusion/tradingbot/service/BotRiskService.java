package com.oneofx.fusion.tradingbot.service;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import com.oneofx.fusion.tradingbot.Database.dbUrl;
import com.oneofx.fusion.tradingbot.bot.BotRuntime;

/** Letzte harte Schranke unmittelbar vor einer echten Kaufübermittlung. */
public final class BotRiskService {

    public Decision evaluateNextBuy(double amount) {
        if (!Double.isFinite(amount) || amount <= 0) return Decision.denied("Ungültiger Kaufbetrag");
        String sql = "SELECT enabled, archived, mode, budget, max_exposure, "
                + "max_open_positions, max_open_orders, "
                + "(SELECT COALESCE(SUM(COALESCE(BuyAmount, 0)), 0) FROM positions "
                + " WHERE bot_id = b.id AND Status IN (0,1,5,7,8)) + "
                + "(SELECT COALESCE(SUM(CAST(a.quantity AS REAL) * CAST(a.limit_price AS REAL)), 0) "
                + " FROM buy_attempts a LEFT JOIN positions p ON p.BuyOrderId = a.exchange_order_id "
                + " WHERE a.bot_id = b.id AND a.state IN ('SUBMITTING','RECONCILIATION_REQUIRED') "
                + " AND p.BuyOrderId IS NULL) AS exposure, "
                + "(SELECT COUNT(*) FROM positions WHERE bot_id = b.id "
                + " AND Status IN (1,5,7,8)) AS position_count, "
                + "((SELECT COUNT(*) FROM positions WHERE bot_id = b.id AND Status = 0) + "
                + " (SELECT COUNT(*) FROM historyPosition WHERE bot_id = b.id AND Status = 0 "
                + "  AND SellOrderId IS NOT NULL) + "
                + " (SELECT COUNT(*) FROM buy_attempts a LEFT JOIN positions p "
                + "  ON p.BuyOrderId = a.exchange_order_id WHERE a.bot_id = b.id "
                + "  AND a.state IN ('SUBMITTING','RECONCILIATION_REQUIRED') "
                + "  AND p.BuyOrderId IS NULL) + "
                + " (SELECT COUNT(*) FROM sell_attempts WHERE bot_id = b.id "
                + "  AND state IN ('SUBMITTING','RECONCILIATION_REQUIRED'))) AS order_count "
                + "FROM bots b WHERE id = ?";
        try (Connection con = open()) {
            if (!tableExists(con, "bots")) return Decision.permitted();
            try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, BotRuntime.activeBotId());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Decision.denied("Aktiver Bot wurde nicht gefunden");
                if (rs.getInt("archived") != 0) return Decision.denied("Bot ist archiviert");
                if (rs.getInt("enabled") == 0) return Decision.denied("Bot ist deaktiviert");
                if (!"LIVE".equals(rs.getString("mode"))) {
                    return Decision.denied("Paper-Bot darf keine Live-Order senden");
                }
                if (rs.getInt("position_count") >= rs.getInt("max_open_positions")) {
                    return Decision.denied("Maximale Anzahl offener Positionen erreicht");
                }
                if (rs.getInt("order_count") >= rs.getInt("max_open_orders")) {
                    return Decision.denied("Maximale Anzahl offener Orders erreicht");
                }
                double limit = Math.min(rs.getDouble("budget"), rs.getDouble("max_exposure"));
                double exposure = rs.getDouble("exposure");
                if (exposure + amount > limit + 0.000001) {
                    return Decision.denied("Bot-Kapitalgrenze erreicht: gebunden=" + exposure
                            + ", nächster Kauf=" + amount + ", Limit=" + limit);
                }
                return Decision.permitted();
            }
            }
        } catch (SQLException ex) {
            if (ex.getMessage() != null && ex.getMessage().contains("no such table: bots")) {
                return Decision.permitted();
            }
            return Decision.denied("Bot-Risiko konnte nicht sicher geprüft werden: " + ex.getMessage());
        }
    }

    private static Connection open() throws SQLException {
        Connection con = DriverManager.getConnection(dbUrl.getoneOfX());
        try (Statement statement = con.createStatement()) {
            statement.execute("PRAGMA busy_timeout = 5000");
        }
        return con;
    }

    private static boolean tableExists(Connection con, String table) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ?")) {
            ps.setString(1, table);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    public record Decision(boolean allowed, String reason) {
        public static Decision permitted() { return new Decision(true, null); }
        public static Decision denied(String reason) { return new Decision(false, reason); }
    }
}
