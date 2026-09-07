package com.oneofx.fusion.tradingbot.Settings;

import com.oneofx.fusion.tradingbot.Database.dbUrl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class CurrencyConfig {

    private static final Logger logger = LoggerFactory.getLogger(CurrencyConfig.class);

    /**
     * Laedt alle Waehrungen aus der currency-Tabelle bei denen buystatus = true.
     *
     * @return Array der aktiven Waehrungspaare (z.B. {"LTCEUR", "BNBEUR"})
     */
    public static String[] getBuyCurrencies() {
        List<String> currencies = new ArrayList<>();

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
            logger.error("Fehler beim Laden der Waehrungen aus der DB: {}", e.getMessage());
        }

        if (currencies.isEmpty()) {
            logger.warn("Keine aktiven Waehrungen (buystatus=true) in der Tabelle 'currency' gefunden!");
        } else {
            // logger.info("Aktive Waehrungen (buystatus=true) aus DB geladen: {}", currencies);
        }

        return currencies.toArray(new String[0]);
    }

    public static int getActiveCurrencyCount() {
        String sql = "SELECT COUNT(*) FROM currency WHERE buystatus = 'true' OR buystatus = '1'";

        try (Connection con = DriverManager.getConnection(dbUrl.getoneOfX());
             Statement stmt = con.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            if (rs.next()) {
                return rs.getInt(1);
            }

        } catch (Exception e) {
            logger.error("Fehler beim Zaehlen der aktiven Waehrungen: {}", e.getMessage());
        }

        return 0;
    }

    /**
     * Laedt alle Waehrungen aus der currency-Tabelle (unabhaengig von buystatus).
     *
     * @return Array aller Waehrungspaare
     */
    public static String[] getAllCurrencies() {
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
            logger.error("Fehler beim Laden aller Waehrungen aus der DB: {}", e.getMessage());
        }

        return currencies.toArray(new String[0]);
    }

    public static int getBuyCurrenciesCount() {
        return getBuyCurrencies().length;
    }
}
