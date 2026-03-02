package com.binance.api.tradingbot.Settings;

import com.binance.api.tradingbot.Database.dbUrl;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class set {

    private static final int DEFAULT_GRID = 7;

    public static int getGridforCurrency(String currency) {
        String sql = "SELECT grid FROM currency WHERE currency = ?";

        try (Connection con = DriverManager.getConnection(dbUrl.getoneOfX());
                PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setString(1, currency);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    int grid = rs.getInt("grid");
                    if (grid > 0) {
                        return grid;
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Fehler beim Laden des Grid-Werts für " + currency + ": " + e.getMessage());
        }

        System.out.println("Kein Grid-Wert für " + currency + " gefunden, verwende Default: " + DEFAULT_GRID);
        return DEFAULT_GRID;
    }

    public static int Currency(String[] currencies, int state) {
        state++;
        if (state >= currencies.length) {
            state = 0;
        }
        return state;
    }
}
