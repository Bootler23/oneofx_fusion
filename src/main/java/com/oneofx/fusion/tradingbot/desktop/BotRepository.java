package com.oneofx.fusion.tradingbot.desktop;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import com.oneofx.fusion.tradingbot.Database.dbUrl;
import com.oneofx.fusion.tradingbot.bot.BotRuntime;
import com.oneofx.fusion.tradingbot.desktop.BotProfile.Mode;

/** Verwaltet lokale Bots und die persistente Auswahl der Einzel-Engine. */
public final class BotRepository {

    public List<BotProfile> loadAll() throws SQLException {
        String sql = "SELECT id, name, enabled, archived, mode, strategy, budget, "
                + "max_exposure, max_open_positions, max_open_orders, paper_fee_percent, "
                + "paper_slippage_percent FROM bots "
                + "WHERE archived = 0 ORDER BY id";
        List<BotProfile> bots = new ArrayList<>();
        try (Connection con = open(); Statement statement = con.createStatement();
             ResultSet rs = statement.executeQuery(sql)) {
            while (rs.next()) bots.add(read(rs));
        }
        return bots;
    }

    public BotProfile load(long id) throws SQLException {
        String sql = "SELECT id, name, enabled, archived, mode, strategy, budget, "
                + "max_exposure, max_open_positions, max_open_orders, paper_fee_percent, "
                + "paper_slippage_percent FROM bots WHERE id = ?";
        try (Connection con = open(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return read(rs);
            }
        }
        throw new SQLException("Bot nicht gefunden: " + id);
    }

    public BotProfile loadSelected() throws SQLException {
        String sql = "SELECT selected_bot_id FROM runtimeState WHERE id = 1";
        long id = BotRuntime.DEFAULT_BOT_ID;
        try (Connection con = open(); Statement statement = con.createStatement();
             ResultSet rs = statement.executeQuery(sql)) {
            if (rs.next()) id = rs.getLong(1);
        }
        BotProfile bot = load(id);
        BotRuntime.select(bot.id());
        return bot;
    }

    public BotProfile create(String name) throws SQLException {
        String normalized = normalizeName(name);
        String sql = "INSERT INTO bots (name, enabled, mode, strategy, budget, max_exposure, "
                + "max_open_positions, max_open_orders, paper_fee_percent, paper_slippage_percent) "
                + "VALUES (?, 0, 'LIVE', 'GRID', 5000, 5000, 20, 4, 0.25, 0.10)";
        try (Connection con = open();
             PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, normalized);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (!keys.next()) throw new SQLException("Bot-ID wurde nicht erzeugt.");
                return load(keys.getLong(1));
            }
        }
    }

    public void save(BotProfile bot) throws SQLException {
        String sql = "UPDATE bots SET name = ?, enabled = ?, mode = ?, strategy = ?, "
                + "budget = ?, max_exposure = ?, max_open_positions = ?, max_open_orders = ?, "
                + "paper_fee_percent = ?, paper_slippage_percent = ?, "
                + "updated_at = CURRENT_TIMESTAMP WHERE id = ? AND archived = 0";
        try (Connection con = open(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, bot.name());
            ps.setInt(2, bot.enabled() ? 1 : 0);
            ps.setString(3, bot.mode().name());
            ps.setString(4, bot.strategy());
            ps.setDouble(5, bot.budget());
            ps.setDouble(6, bot.maxExposure());
            ps.setInt(7, bot.maxOpenPositions());
            ps.setInt(8, bot.maxOpenOrders());
            ps.setDouble(9, bot.paperFeePercent());
            ps.setDouble(10, bot.paperSlippagePercent());
            ps.setLong(11, bot.id());
            if (ps.executeUpdate() != 1) throw new SQLException("Bot konnte nicht gespeichert werden.");
        }
    }

    public void select(long botId) throws SQLException {
        BotProfile bot = load(botId);
        if (bot.archived()) throw new SQLException("Ein archivierter Bot kann nicht ausgewählt werden.");
        try (Connection con = open(); PreparedStatement ps = con.prepareStatement(
                "UPDATE runtimeState SET selected_bot_id = ? WHERE id = 1")) {
            ps.setLong(1, botId);
            if (ps.executeUpdate() != 1) throw new SQLException("Bot-Auswahl konnte nicht gespeichert werden.");
        }
        BotRuntime.select(botId);
    }

    /** Bots werden absichtlich archiviert; Handelsdaten bleiben vollständig erhalten. */
    public void archive(long botId) throws SQLException {
        try (Connection con = open()) {
            con.setAutoCommit(false);
            try {
                int obligations;
                String obligationsSql = "SELECT "
                        + "(SELECT COUNT(*) FROM positions WHERE bot_id = ? "
                        + " AND Status IN (0,1,2,5,7,8)) + "
                        + "(SELECT COUNT(*) FROM historyPosition WHERE bot_id = ? "
                        + " AND Status = 0 AND SellOrderId IS NOT NULL) + "
                        + "(SELECT COUNT(*) FROM buy_attempts WHERE bot_id = ? "
                        + " AND state IN ('SUBMITTING','RECONCILIATION_REQUIRED')) + "
                        + "(SELECT COUNT(*) FROM sell_attempts WHERE bot_id = ? "
                        + " AND state IN ('SUBMITTING','RECONCILIATION_REQUIRED')) + "
                        + "(SELECT COUNT(*) FROM paperOrders WHERE bot_id = ? AND status = 'OPEN') + "
                        + "(SELECT COUNT(*) FROM paperPositions WHERE bot_id = ? AND status = 'OPEN')";
                try (PreparedStatement ps = con.prepareStatement(obligationsSql)) {
                    for (int i = 1; i <= 6; i++) ps.setLong(i, botId);
                    try (ResultSet rs = ps.executeQuery()) {
                        obligations = rs.next() ? rs.getInt(1) : 0;
                    }
                }
                if (obligations > 0) {
                    throw new SQLException("Der Bot besitzt noch offene Positionen, Orders oder "
                            + "ungeklärte Übermittlungen und kann nicht archiviert werden.");
                }
                int remaining;
                try (PreparedStatement ps = con.prepareStatement(
                        "SELECT COUNT(*) FROM bots WHERE archived = 0 AND id <> ?")) {
                    ps.setLong(1, botId);
                    try (ResultSet rs = ps.executeQuery()) {
                        remaining = rs.next() ? rs.getInt(1) : 0;
                    }
                }
                if (remaining == 0) {
                    throw new SQLException("Der letzte vorhandene Bot kann nicht archiviert werden.");
                }
                try (PreparedStatement ps = con.prepareStatement(
                        "UPDATE bots SET enabled = 0, archived = 1, updated_at = CURRENT_TIMESTAMP "
                                + "WHERE id = ? AND archived = 0")) {
                    ps.setLong(1, botId);
                    if (ps.executeUpdate() != 1) throw new SQLException("Bot nicht gefunden.");
                }
                long fallback;
                try (Statement statement = con.createStatement();
                     ResultSet rs = statement.executeQuery(
                             "SELECT id FROM bots WHERE archived = 0 ORDER BY id LIMIT 1")) {
                    if (!rs.next()) throw new SQLException("Kein Ersatz-Bot verfügbar.");
                    fallback = rs.getLong(1);
                }
                try (PreparedStatement ps = con.prepareStatement(
                        "UPDATE runtimeState SET selected_bot_id = ? WHERE selected_bot_id = ?")) {
                    ps.setLong(1, fallback);
                    ps.setLong(2, botId);
                    ps.executeUpdate();
                }
                con.commit();
                if (BotRuntime.activeBotId() == botId) BotRuntime.select(fallback);
            } catch (SQLException | RuntimeException ex) {
                try { con.rollback(); } catch (SQLException rollback) { ex.addSuppressed(rollback); }
                throw ex;
            }
        }
    }

    public RiskSnapshot loadRiskSnapshot(long botId) throws SQLException {
        BotProfile profile = load(botId);
        if (profile.mode() == Mode.PAPER) {
            PaperTradingRepository.PaperRisk paper =
                    new PaperTradingRepository().loadRisk(botId);
            return new RiskSnapshot(profile.budget(), profile.maxExposure(),
                    paper.exposure(), paper.openPositions(), paper.openOrders(),
                    profile.maxOpenPositions(), profile.maxOpenOrders());
        }
        String sql = "SELECT b.budget, b.max_exposure, b.max_open_positions, b.max_open_orders, "
                + "(SELECT COALESCE(SUM(COALESCE(p.BuyAmount, 0)), 0) FROM positions p "
                + " WHERE p.bot_id = b.id AND p.Status IN (0,1,5,7,8)) + "
                + "(SELECT COALESCE(SUM(CAST(a.quantity AS REAL) * CAST(a.limit_price AS REAL)), 0) "
                + " FROM buy_attempts a LEFT JOIN positions p ON p.BuyOrderId = a.exchange_order_id "
                + " WHERE a.bot_id = b.id AND a.state IN ('SUBMITTING','RECONCILIATION_REQUIRED') "
                + " AND p.BuyOrderId IS NULL) AS exposure, "
                + "(SELECT COUNT(*) FROM positions p WHERE p.bot_id = b.id "
                + " AND p.Status IN (1,5,7,8)) AS positions, "
                + "((SELECT COUNT(*) FROM positions p WHERE p.bot_id = b.id AND p.Status = 0) + "
                + " (SELECT COUNT(*) FROM historyPosition h WHERE h.bot_id = b.id AND h.Status = 0 "
                + "  AND h.SellOrderId IS NOT NULL) + "
                + " (SELECT COUNT(*) FROM buy_attempts a LEFT JOIN positions p "
                + "  ON p.BuyOrderId = a.exchange_order_id WHERE a.bot_id = b.id "
                + "  AND a.state IN ('SUBMITTING','RECONCILIATION_REQUIRED') "
                + "  AND p.BuyOrderId IS NULL) + "
                + " (SELECT COUNT(*) FROM sell_attempts WHERE bot_id = b.id "
                + "  AND state IN ('SUBMITTING','RECONCILIATION_REQUIRED'))) AS orders "
                + "FROM bots b WHERE b.id = ?";
        try (Connection con = open(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, botId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new SQLException("Bot nicht gefunden: " + botId);
                return new RiskSnapshot(rs.getDouble("budget"), rs.getDouble("max_exposure"),
                        rs.getDouble("exposure"), rs.getInt("positions"), rs.getInt("orders"),
                        rs.getInt("max_open_positions"), rs.getInt("max_open_orders"));
            }
        }
    }

    private static BotProfile read(ResultSet rs) throws SQLException {
        return new BotProfile(rs.getLong("id"), rs.getString("name"),
                rs.getInt("enabled") != 0, rs.getInt("archived") != 0,
                Mode.valueOf(rs.getString("mode")), rs.getString("strategy"),
                rs.getDouble("budget"), rs.getDouble("max_exposure"),
                rs.getInt("max_open_positions"), rs.getInt("max_open_orders"),
                rs.getDouble("paper_fee_percent"), rs.getDouble("paper_slippage_percent"));
    }

    private static String normalizeName(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Der Bot-Name darf nicht leer sein.");
        }
        String normalized = name.trim();
        if (normalized.length() > 80) {
            throw new IllegalArgumentException("Der Bot-Name darf höchstens 80 Zeichen lang sein.");
        }
        return normalized;
    }

    private static Connection open() throws SQLException {
        return com.oneofx.fusion.tradingbot.Database.SQLiteConnectionFactory.open(dbUrl.getoneOfX());
    }

    public record RiskSnapshot(double budget, double maxExposure, double exposure,
            int openPositions, int openOrders, int maxOpenPositions, int maxOpenOrders) {
        public double remainingCapital() {
            return Math.max(0, Math.min(budget, maxExposure) - exposure);
        }
    }
}
