package com.oneofx.fusion.tradingbot.SQL_Database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;

import com.oneofx.fusion.tradingbot.Database.dbUrl;
import com.oneofx.fusion.tradingbot.bot.BotRuntime;

/** Speichert Schutz- und Sperrzustände je Bot dauerhaft über einen Neustart hinweg. */
public final class StrategyStateDAO {

    public void blockBuys(String currency, Duration duration, String reason) {
        long now = System.currentTimeMillis();
        long until;
        try {
            until = Math.addExact(now, duration.toMillis());
        } catch (ArithmeticException ex) {
            until = Long.MAX_VALUE;
        }
        blockBuysUntil(currency, until, reason, now);
    }

    void blockBuysUntil(String currency, long until, String reason, long now) {
        try (Connection con = com.oneofx.fusion.tradingbot.Database.SQLiteConnectionFactory.open(dbUrl.getoneOfX())) {
            boolean botAware = tableExists(con, "botStrategyState");
            String sql = botAware
                    ? "INSERT INTO botStrategyState (bot_id, currency, buyBlockedUntil, reason, updatedAt) "
                            + "VALUES (?, ?, ?, ?, ?) ON CONFLICT(bot_id, currency) DO UPDATE SET "
                            + "buyBlockedUntil = MAX(botStrategyState.buyBlockedUntil, excluded.buyBlockedUntil), "
                            + "reason = excluded.reason, updatedAt = excluded.updatedAt"
                    : "INSERT INTO strategyState (currency, buyBlockedUntil, reason, updatedAt) "
                            + "VALUES (?, ?, ?, ?) ON CONFLICT(currency) DO UPDATE SET "
                            + "buyBlockedUntil = MAX(strategyState.buyBlockedUntil, excluded.buyBlockedUntil), "
                            + "reason = excluded.reason, updatedAt = excluded.updatedAt";
            try (PreparedStatement ps = con.prepareStatement(sql)) {
                int index = 1;
                if (botAware) ps.setLong(index++, BotRuntime.activeBotId());
                ps.setString(index++, currency);
                ps.setLong(index++, until);
                ps.setString(index++, reason);
                ps.setLong(index, now);
                ps.executeUpdate();
            }
        } catch (SQLException ex) {
            throw new IllegalStateException(
                    "Kaufsperre für " + currency + " konnte nicht gespeichert werden", ex);
        }
    }

    public boolean isBuyBlocked(String currency) {
        return getBuyBlockedUntil(currency) > System.currentTimeMillis();
    }

    public long getBuyBlockedUntil(String currency) {
        try (Connection con = com.oneofx.fusion.tradingbot.Database.SQLiteConnectionFactory.open(dbUrl.getoneOfX())) {
            boolean botAware = tableExists(con, "botStrategyState");
            String sql = botAware
                    ? "SELECT buyBlockedUntil FROM botStrategyState WHERE bot_id = ? AND currency = ?"
                    : "SELECT buyBlockedUntil FROM strategyState WHERE currency = ?";
            try (PreparedStatement ps = con.prepareStatement(sql)) {
                if (botAware) {
                    ps.setLong(1, BotRuntime.activeBotId());
                    ps.setString(2, currency);
                } else {
                    ps.setString(1, currency);
                }
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? rs.getLong(1) : 0L;
                }
            }
        } catch (SQLException ex) {
            System.err.println("KRITISCH: Kaufsperre für " + currency
                    + " konnte nicht gelesen werden: " + ex.getMessage());
            return Long.MAX_VALUE;
        }
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
}
