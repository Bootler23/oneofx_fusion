package com.oneofx.fusion.tradingbot.HelperFunctions;

import com.oneofx.fusion.client.FusionApiClient;
import com.oneofx.fusion.client.model.Account;
import com.oneofx.fusion.client.model.AssetBalance;
import com.oneofx.fusion.client.model.FusionSymbol;
import com.oneofx.fusion.client.FusionApiException;
import com.oneofx.fusion.tradingbot.SQL_Database.CurrencyDAO;
import com.oneofx.fusion.tradingbot.SQL_Database.PositionDAO;
import com.oneofx.fusion.tradingbot.constants.TradingConstants;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Gleicht fuer jedes aktive Waehrungspaar die Balance auf der Boerse (Binance)
 * mit der Datenbank-Balance (SUM Qty offener Positionen) ab und speichert
 * Exchange, Database und Differenz in der currency-Tabelle.
 *
 * Ersetzt die alte BalanceChecker-Klasse, die nur fuer eine Waehrung funktionierte.
 */
public class BalanceReconciliation {

    private static final Logger logger = LoggerFactory.getLogger(BalanceReconciliation.class);

    /** Schwellwert ab dem eine Differenz als Warnung geloggt wird */
    private static final double DIFF_WARN_THRESHOLD = 0.001;
    private static final PositionDAO positionDAO = new PositionDAO();
    private static final CurrencyDAO currencyDAO = new CurrencyDAO();

    /**
     * Fuehrt den Balance-Abgleich fuer alle uebergebenen Waehrungspaare durch.
     * Es wird nur ein einziger API-Call (client.getAccount()) gemacht,
     * um Rate-Limits zu schonen.
     *
     * @param currencies Alle aktiven Waehrungspaare (z.B. ["LTCEUR", "BNBEUR"])
     * @param client     Der authentifizierte Binance API Client
     */
    public static void reconcileAll(String[] currencies, FusionApiClient client) {
        if (currencies == null || currencies.length == 0) {
            return;
        }

        Account account;
        try {
            account = client.getAccount();
        } catch (FusionApiException e) {
            logger.error("Balance-Abgleich fehlgeschlagen - Binance API nicht erreichbar: {}", e.getMessage());
            return;
        }

        for (String currency : currencies) {
            try {
                reconcileCurrency(currency, account);
            } catch (Exception e) {
                logger.error("Balance-Abgleich fuer {} fehlgeschlagen: {}", currency, e.getMessage());
            }
        }
    }

    /**
     * Gleicht die Balance fuer ein einzelnes Waehrungspaar ab.
     *
     * @param currencyPair Das Waehrungspaar (z.B. "LTCEUR")
     * @param account      Das bereits geladene Binance Account-Objekt
     */
    private static void reconcileCurrency(String currencyPair, Account account) {
        String asset = extractAsset(currencyPair);

        // Exchange-Balance: free + locked
        double exchangeBalance = getExchangeBalance(asset, account);

        // Datenbank-Balance: SUM(Qty) offener Positionen (Status 0, 1, 5)
        double databaseBalance = positionDAO.getSumQuantityForCurrency(currencyPair);

        // Differenz berechnen
        double differenz = round.eight(exchangeBalance - databaseBalance);

        // In currency-Tabelle speichern
        currencyDAO.updateBalanceInfo(currencyPair, exchangeBalance, databaseBalance, differenz);

        // Bei relevanter Differenz warnen
        if (Math.abs(differenz) > DIFF_WARN_THRESHOLD) {
            // logger.warn("Balance-Differenz fuer {}: Exchange={}, DB={}, Diff={}",
            //         currencyPair,
            //         round.eight(exchangeBalance),
            //         round.eight(databaseBalance),
            //         differenz);
        }
    }

    /**
     * Holt die Gesamt-Balance (free + locked) eines Assets von Binance.
     *
     * @param asset   Das Asset (z.B. "LTC", "BNB")
     * @param account Das Binance Account-Objekt
     * @return Gesamt-Balance oder 0.0 wenn das Asset nicht gefunden wird
     */
    private static double getExchangeBalance(String asset, Account account) {
        AssetBalance balance = account.getAssetBalance(asset);
        if (balance == null) {
            logger.warn("Asset {} nicht auf Binance gefunden - Balance = 0.0", asset);
            return 0.0;
        }

        double free = Double.parseDouble(balance.getFree());
        double locked = Double.parseDouble(balance.getLocked());
        return round.eight(free + locked);
    }

    /**
     * Extrahiert den Asset-Namen aus einem Waehrungspaar.
     * z.B. "LTCEUR" -> "LTC", "BNBEUR" -> "BNB"
     *
     * @param currencyPair Das Waehrungspaar
     * @return Der Asset-Name ohne Base-Currency
     */
    private static String extractAsset(String currencyPair) {
        return FusionSymbol.baseAsset(currencyPair);
    }
}
