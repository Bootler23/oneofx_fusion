package com.oneofx.fusion.tradingbot.BuyOrderProcess;

import java.util.List;

import com.binance.api.client.BinanceApiRestClient;
import com.binance.api.client.domain.market.TickerPrice;
import com.binance.api.client.exception.BinanceApiException;
import com.oneofx.fusion.tradingbot.HelperFunctions.TradingRulesFormatter;
import com.oneofx.fusion.tradingbot.HelperFunctions.Time;
import com.oneofx.fusion.tradingbot.HelperFunctions.round;
import com.oneofx.fusion.tradingbot.HelperFunctions.sleep;

public class Ticker {

    public static double getAssetPrice(String currencyPair, BinanceApiRestClient client) {
        int maxRetries = 10;
        int retryCount = 0;

        while (retryCount < maxRetries) {
            try {
                TickerPrice tickerPrice = client.getPrice(currencyPair);
                return round.two(Double.parseDouble(tickerPrice.getPrice()));
            } catch (BinanceApiException e) {
                if (e.getCause() instanceof java.net.SocketTimeoutException && retryCount < maxRetries - 1) {
                    System.out.println("Timeout aufgetreten in getAssetPrice, versuche es erneut (" + (retryCount + 1)
                            + "/" + maxRetries + ")");
                    retryCount++;
                    sleep.for_10_seconds();
                } else {
                    System.out.println("Fehler beim Abrufen des Binance-API-Service getAssetPrice: " + e.getMessage());
                    return 0.0;
                }
            } catch (Exception e) {
                System.out.println("Unerwarteter Fehler in getAssetPrice: " + e.getMessage());
                return 0.0;
            }
        }
        System.out.println("Maximale Anzahl von Wiederholungsversuchen in getAssetPrice erreicht.");
        return 0.0;
    }

    public static void get_CurrencyPair_Price(String currency, BinanceApiRestClient client,
            List<Double> LiveTicker) {
        int maxRetries = 10;
        int retryCount = 0;
        LiveTicker.clear();

        while (retryCount < maxRetries) {
            try {
                TickerPrice tickerPrice = client.getPrice(currency);
                LiveTicker.add(TradingRulesFormatter.formatPrice(currency, Double.parseDouble(tickerPrice.getPrice())));
                System.out.print(".");
                return;
            } catch (BinanceApiException e) {
                if (e.getCause() instanceof java.net.SocketTimeoutException && retryCount < maxRetries - 1) {
                    System.out.println(
                            "Timeout aufgetreten, versuche es erneut (" + (retryCount + 1) + "/" + maxRetries + ")");
                    retryCount++;
                    sleep.for_10_seconds();
                } else {
                    System.out
                            .println("Fehler beim Abrufen Live_TickerPrice des Binance-API-Service: " + e.getMessage());
                    System.out.println(Time.getCurrent_DateTimeWith_HHmmss());
                    return;
                }
            }
        }
        System.out.println("Maximale Anzahl von Wiederholungsversuchen erreicht.");
    }

    public static double getAssetPrice_WithRetry(String currencyPair, BinanceApiRestClient client) {
        int maxRetries = 10;
        int retryCount = 0;

        while (retryCount < maxRetries) {
            try {
                TickerPrice TickerPrice = client.getPrice(currencyPair);
                return round.two(Double.parseDouble(TickerPrice.getPrice()));
            } catch (BinanceApiException e) {
                if (e.getCause() instanceof java.net.SocketTimeoutException && retryCount < maxRetries - 1) {
                    System.out.println(
                            "Timeout aufgetreten, versuche es erneut (" + (retryCount + 1) + "/" + maxRetries + ")");
                    retryCount++;
                    sleep.for_10_seconds();
                } else {
                    System.out.println("Fehler beim Abrufen des Binance-API-Service getAssetPrice: " + e.getMessage());
                    return 0.0;
                }
            }
        }
        System.out.println("Maximale Anzahl von Wiederholungsversuchen erreicht.");
        return 0.0;
    }
}
