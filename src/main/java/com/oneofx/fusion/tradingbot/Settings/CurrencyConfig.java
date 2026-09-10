package com.oneofx.fusion.tradingbot.Settings;

import com.oneofx.fusion.tradingbot.Database.SQLiteConnectionFactory;
import com.oneofx.fusion.tradingbot.Database.dbUrl;
import com.oneofx.fusion.tradingbot.bot.BotRuntime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Set;

public class CurrencyConfig {

    private static final Logger logger = LoggerFactory.getLogger(CurrencyConfig.class);

    /**
     * Laedt alle Waehrungen aus der currency-Tabelle bei denen buystatus = true.
     *
     * @return Array der aktiven Waehrungspaare (z.B. {"LTCEUR", "BNBEUR"})
     */
    public static String[] getBuyCurrencies() {
        List<String> currencies = new ArrayList<>();

        String sql = "SELECT currency FROM botPairSettings WHERE bot_id = "
                + BotRuntime.activeBotId() + " AND "
                + "(buystatus = 'true' OR buystatus = '1') "
                + "AND COALESCE(archived, 0) = 0";

        try (Connection con = SQLiteConnectionFactory.open(dbUrl.getoneOfX());
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
        String sql = "SELECT COUNT(*) FROM botPairSettings WHERE bot_id = "
                + BotRuntime.activeBotId() + " AND "
                + "(buystatus = 'true' OR buystatus = '1') "
                + "AND COALESCE(archived, 0) = 0";

        try (Connection con = SQLiteConnectionFactory.open(dbUrl.getoneOfX());
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

        String sql = "SELECT currency FROM botPairSettings WHERE bot_id = "
                + BotRuntime.activeBotId() + " AND COALESCE(archived, 0) = 0";

        try (Connection con = SQLiteConnectionFactory.open(dbUrl.getoneOfX());
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

    /**
     * Lädt alle Paare, die weiterhin überwacht werden müssen. Ein deaktivierter
     * Kaufstatus darf Orderabgleich, Stop-Loss und Verkäufe vorhandener
     * Bot-Positionen nicht beenden.
     */
    public static String[] getMonitoredCurrencies() {
        Set<String> currencies = new LinkedHashSet<>();
        String sql = "SELECT currency FROM botPairSettings "
                + "WHERE bot_id = " + BotRuntime.activeBotId() + " "
                + "AND (buyStatus = 'true' OR buyStatus = '1') "
                + "AND COALESCE(archived, 0) = 0 "
                + "UNION SELECT currency FROM positions "
                + "WHERE bot_id = " + BotRuntime.activeBotId() + " "
                + "AND Status IN (0, 1, 2, 5, 7, 8) "
                + "ORDER BY currency";

        try (Connection con = SQLiteConnectionFactory.open(dbUrl.getoneOfX());
             Statement stmt = con.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                String currency = rs.getString("currency");
                if (currency != null && !currency.isBlank()) {
                    currencies.add(currency.trim());
                }
            }
        } catch (Exception e) {
            logger.error("Fehler beim Laden der zu ueberwachenden Waehrungen: {}", e.getMessage());
            for (String currency : getBuyCurrencies()) {
                currencies.add(currency);
            }
        }
        return currencies.toArray(new String[0]);
    }

    public static int getBuyCurrenciesCount() {
        return getBuyCurrencies().length;
    }
}
