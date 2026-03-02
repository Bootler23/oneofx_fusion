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

        String sql = "SELECT currency FROM currency";

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
            System.err.println("Keine Währungen in der Tabelle 'currency' gefunden! Bitte Datenbank prüfen.");
        } else {
            System.out.println("Währungen aus DB geladen: " + currencies);
        }

        return currencies.toArray(new String[0]);
    }

    public static int getBuyCurrenciesCount() {
        return getBuyCurrencies().length;
    }
}
