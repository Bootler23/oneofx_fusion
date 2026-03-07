package com.binance.api.tradingbot.Settings;

import com.binance.api.tradingbot.Database.dbUrl;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class CurrencyConfig {

    public static String[] getBuyCurrencies() {
        List<String> currencies = new ArrayList<>();

        // Nur Währungen laden bei denen buystatus = 'true' oder '1'
        String sql = "SELECT currency FROM currency WHERE buystatus = 'true' OR buystatus = '1'";

        try (Connection con = DriverManager.getConnection(dbUrl.getoneOfX());
             Statement stmt = con.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                String currency = rs.getString("currency");
                if (currency != null && !currency.trim().isEmpty()) {
                    currencies.add(currency.trim());
                }
            }

        } catch (Exception e) {
            System.err.println("Fehler beim Laden der Währungen aus der DB: " + e.getMessage());
        }

        if (currencies.isEmpty()) {
            System.err.println("Keine aktiven Währungen (buystatus=true) in der Tabelle 'currency' gefunden!");
        } else {
            System.out.println("Aktive Währungen (buystatus=true) aus DB geladen: " + currencies);
        }

        return currencies.toArray(new String[0]);
    }

    public static int getBuyCurrenciesCount() {
        return getBuyCurrencies().length;
    }
}
