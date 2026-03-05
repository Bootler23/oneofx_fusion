package com.binance.api.tradingbot.HelperFunctions;

import static com.binance.api.client.domain.account.NewOrder.marketBuy;

import com.binance.api.client.BinanceApiRestClient;
import com.binance.api.client.domain.account.NewOrderResponse;
import com.binance.api.client.exception.BinanceApiException;
import com.binance.api.tradingbot.BuyOrderProcess.Ticker;
import com.binance.api.tradingbot.HelperFunctions.TradingRulesFormatter;
import com.binance.api.tradingbot.SQL_Database.POSSQL;
import com.binance.api.tradingbot.SQL_Database.SETSQL;

public class Asset {

    public static double getFree_Balance(String currency, BinanceApiRestClient client) {
        try {
            double balance = round.two(Double.valueOf(client.getAccount().getAssetBalance(currency).getFree()));
            return balance;

        } catch (NullPointerException e) {
            System.out.println("Fehler: Asset " + currency + " nicht gefunden oder nicht verfügbar.");
            return 0.0;
        } catch (BinanceApiException e) {
            System.out.println("Fehler: Live - Balance des Binance-API nicht möglich.");
            return 0.0;
        }
    }

    public static double getLocked_Balance(String currency, BinanceApiRestClient client) {
        try {
            double balance = round.two(Double.valueOf(client.getAccount().getAssetBalance(currency).getLocked()));
            return balance;

        } catch (NullPointerException e) {
            System.out.println("Fehler: Asset " + currency + " nicht gefunden oder nicht verfügbar.");
            return 0.0;
        } catch (BinanceApiException e) {
            System.out.println("Fehler: Live - Balance des Binance-API nicht möglich.");
            return 0.0;
        }
    }

    public static double getFreeCalced_Balance(String currency, BinanceApiRestClient client) {
        try {
            double balance = round.two(getFree_Balance(currency, client) - getLocked_Balance(currency, client));
            return balance;

        } catch (NullPointerException e) {
            System.out.println("Fehler: Asset " + currency + " nicht gefunden oder nicht verfügbar.");
            return 0.0;
        } catch (BinanceApiException e) {
            System.out.println("Fehler: Live - Balance des Binance-API nicht möglich.");
            return 0.0;
        }
    }

    public static double getBNB_Balance(String currencyPeer, String currency, BinanceApiRestClient client) {
        try {
            double bnbFree = Double.valueOf(client.getAccount().getAssetBalance(currency).getFree());
            double bnbLocked = Double.valueOf(client.getAccount().getAssetBalance(currency).getLocked());
            double bnbTotal = bnbFree + bnbLocked;
            double bnbPrice = Double.valueOf(client.getPrice(currencyPeer).getPrice());

            // BNB die in offenen BNBEUR-Positionen gebunden sind
            double bnbInPositions = POSSQL.getSumQuantityForCurrency(currencyPeer);

            // Verfügbare BNB = Gesamt minus Positionen
            double bnbAvailable = bnbTotal - bnbInPositions;
            double bnbAvailableEur = round.five(bnbPrice * bnbAvailable);
            double bnbTotalEur = round.five(bnbPrice * bnbTotal);
            double bnbInPositionsEur = round.five(bnbPrice * bnbInPositions);

            // System.out.println();
            // System.out.println("BNB Gesamt: " + round.five(bnbTotal) + " BNB (" + bnbTotalEur + " EUR)");
            // System.out.println("BNB in Positionen: " + round.five(bnbInPositions) + " BNB (" + bnbInPositionsEur + " EUR)");
            // System.out.println("BNB verfügbar (Fee-Reserve): " + round.five(bnbAvailable) + " BNB (" + bnbAvailableEur + " EUR)");

            if (bnbAvailableEur < 10.0) {
                System.out.println("BNB Fee-Reserve unter 10 EUR -> Nachkauf wird ausgelöst!");
                buy_bnb(client);
            }

            return bnbAvailableEur;

        } catch (BinanceApiException e) {
            System.out.println("Fehler beim Abrufen der BNB-Balance: " + e.getMessage());
            return 0.0;
        } catch (Exception e) {
            System.out.println("Unbekannter Fehler bei BNB-Balance: " + e.getMessage());
            return 0.0;
        }
    }

    private static void buy_bnb(BinanceApiRestClient client) {
        try {
            String quantity = TradingRulesFormatter.calculateAndFormatQuantity(
                    "BNBEUR", 10.0, Ticker.getAssetPrice("BNBEUR", client));

            NewOrderResponse newOrderResponse = client.newOrder(marketBuy("BNBEUR", quantity));

            double totalPaid = Double.valueOf(newOrderResponse.getCummulativeQuoteQty());
            double quantityBought = Double.valueOf(newOrderResponse.getExecutedQty());
            double avgBuyPrice = round.two(totalPaid / quantityBought);

            SETSQL.set_BNB_price(avgBuyPrice);
            System.out.println("BNB Nachkauf erfolgreich: " + quantityBought + " BNB zu " + avgBuyPrice + " EUR");

        } catch (BinanceApiException e) {
            System.err.println("Fehler beim BNB-Nachkauf: " + e.getMessage());
            sleep.for_10_seconds();
        } catch (Exception e) {
            System.err.println("Fehler beim BNB-Nachkauf: " + e.getMessage());
        }
    }
}