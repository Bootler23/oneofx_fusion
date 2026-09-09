package com.oneofx.fusion.tradingbot.SQL_Database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;

import com.oneofx.fusion.tradingbot.Database.dbUrl;

/** Speichert Schutz- und Sperrzustände dauerhaft über einen Bot-Neustart hinweg. */
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
        String sql = "INSERT INTO strategyState "
                + "(currency, buyBlockedUntil, reason, updatedAt) VALUES (?, ?, ?, ?) "
                + "ON CONFLICT(currency) DO UPDATE SET "
                + "buyBlockedUntil = MAX(strategyState.buyBlockedUntil, excluded.buyBlockedUntil), "
                + "reason = excluded.reason, updatedAt = excluded.updatedAt";
        try (Connection con = DriverManager.getConnection(dbUrl.getoneOfX());
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, currency);
            ps.setLong(2, until);
            ps.setString(3, reason);
            ps.setLong(4, now);
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw new IllegalStateException(
                    "Kaufsperre fuer " + currency + " konnte nicht gespeichert werden", ex);
        }
    }

    public boolean isBuyBlocked(String currency) {
        return getBuyBlockedUntil(currency) > System.currentTimeMillis();
    }

    public long getBuyBlockedUntil(String currency) {
        String sql = "SELECT buyBlockedUntil FROM strategyState WHERE currency = ?";
        try (Connection con = DriverManager.getConnection(dbUrl.getoneOfX());
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, currency);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0L;
            }
        } catch (SQLException ex) {
            // Ein fehlender oder unlesbarer Schutzstatus darf Käufe nicht freigeben.
            System.err.println("KRITISCH: Kaufsperre fuer " + currency
                    + " konnte nicht gelesen werden: " + ex.getMessage());
            return Long.MAX_VALUE;
        }
    }
}
