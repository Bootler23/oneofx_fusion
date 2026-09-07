package com.oneofx.fusion.tradingbot.Settings;

import com.oneofx.fusion.tradingbot.Database.dbUrl;

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

    /**
     * Round-Robin ueber das Waehrungs-Array.
     * Gibt den naechsten Index zurueck. Sicher bei Array-Groessenaenderungen.
     *
     * @param currencies Aktives Waehrungs-Array (kann sich zur Laufzeit aendern)
     * @param state      Aktueller Index
     * @return Naechster gueltiger Index, oder -1 wenn Array leer
     */
    public static int Currency(String[] currencies, int state) {
        if (currencies == null || currencies.length == 0) {
            return -1;
        }
        state++;
        if (state >= currencies.length) {
            state = 0;
        }
        return state;
    }
}
