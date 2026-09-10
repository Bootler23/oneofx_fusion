package com.oneofx.fusion.tradingbot.Settings;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import com.oneofx.fusion.tradingbot.Database.SQLiteConnectionFactory;
import com.oneofx.fusion.tradingbot.Database.dbUrl;
import com.oneofx.fusion.tradingbot.bot.BotRuntime;
import com.oneofx.fusion.tradingbot.grid.GridMode;
import com.oneofx.fusion.tradingbot.grid.GridSettings;

public class set {
    private static final int DEFAULT_GRID = 7;

    public static int getGridforCurrency(String currency) {
        String sql = "SELECT CASE WHEN gridMode = 'GEOMETRIC' AND gridSpacing > 0 "
                + "THEN CAST(ROUND(1.0 / gridSpacing) AS INTEGER) ELSE 1 END AS grid "
                + "FROM botPairSettings WHERE bot_id = ? AND currency = ?";
        try (Connection con = SQLiteConnectionFactory.open(dbUrl.getoneOfX());
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, BotRuntime.activeBotId());
            ps.setString(2, currency);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next() && rs.getInt("grid") > 0) return rs.getInt("grid");
            }
        } catch (Exception ignored) {
            // Eigenständig erzeugte Legacy-Datenbanken besitzen die Bot-Tabelle noch nicht.
        }
        return legacyGrid(currency);
    }

    public static GridSettings getGridSettings(String currency) {
        String sql = "SELECT gridMode, gridSpacing FROM botPairSettings "
                + "WHERE bot_id = ? AND currency = ?";
        try (Connection con = SQLiteConnectionFactory.open(dbUrl.getoneOfX());
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, BotRuntime.activeBotId());
            ps.setString(2, currency);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    double spacing = rs.getDouble("gridSpacing");
                    if (spacing > 0 && Double.isFinite(spacing)) {
                        return new GridSettings(
                                GridMode.fromDatabase(rs.getString("gridMode")), spacing);
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return legacyGridSettings(currency);
    }

    private static int legacyGrid(String currency) {
        try (Connection con = SQLiteConnectionFactory.open(dbUrl.getoneOfX());
             PreparedStatement ps = con.prepareStatement(
                     "SELECT grid FROM currency WHERE currency = ?")) {
            ps.setString(1, currency);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next() && rs.getInt("grid") > 0) return rs.getInt("grid");
            }
        } catch (Exception ignored) {
        }
        return DEFAULT_GRID;
    }

    private static GridSettings legacyGridSettings(String currency) {
        try (Connection con = SQLiteConnectionFactory.open(dbUrl.getoneOfX());
             PreparedStatement ps = con.prepareStatement(
                     "SELECT gridMode, gridSpacing, grid FROM currency WHERE currency = ?")) {
            ps.setString(1, currency);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    double spacing = rs.getDouble("gridSpacing");
                    if (spacing > 0 && Double.isFinite(spacing)) {
                        return new GridSettings(
                                GridMode.fromDatabase(rs.getString("gridMode")), spacing);
                    }
                    return GridSettings.legacy(rs.getInt("grid"));
                }
            }
        } catch (Exception ignored) {
        }
        return GridSettings.legacy(DEFAULT_GRID);
    }

    /** Round-Robin über das Währungspaar-Array. */
    public static int Currency(String[] currencies, int state) {
        if (currencies == null || currencies.length == 0) return -1;
        state++;
        return state >= currencies.length ? 0 : state;
    }
}
