package com.binance.api.tradingbot.BuyOrderProcess;

import static com.binance.api.client.domain.account.NewOrder.limitBuy;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

import com.binance.api.client.BinanceApiRestClient;
import com.binance.api.client.domain.TimeInForce;
import com.binance.api.client.domain.account.NewOrderResponse;
import com.binance.api.client.exception.BinanceApiException;
import com.binance.api.tradingbot.Database.dbUrl;
import com.binance.api.tradingbot.HelperFunctions.TradingRulesFormatter;
import com.binance.api.tradingbot.HelperFunctions.empty;
import com.binance.api.tradingbot.HelperFunctions.round;
import com.binance.api.tradingbot.HelperFunctions.sleep;
import com.binance.api.tradingbot.SQL_Database.ATHSQL;
import com.binance.api.tradingbot.SQL_Database.CurrencySQL;
import com.binance.api.tradingbot.SQL_Database.POSSQL;
import com.binance.api.tradingbot.Settings.set;
import com.binance.api.tradingbot.constants.TradingConstants;
import com.binance.api.tradingbot.service.VolumeService;

public class BuyOrderPocess {

    public static void setBuyOrder(String currency, BinanceApiRestClient client, List<Double> LivePrice) {

        double BuyAmaunt;
        double Ath = ATHSQL.getAllTimeHigh(currency);
        double unten = POSSQL.getLastDownSidePrice(currency, LivePrice);
        double BuyPrice;
        int Count = 0;
        boolean BuyOrderCalc = true;
        double TickerPrice = LivePrice.get(0);
        int grid = set.getGridforCurrency(currency);

        while (BuyOrderCalc) {

            Ath = Ath - ((Ath / 100) / grid);
            BuyPrice = TradingRulesFormatter.formatPrice(currency, Ath);

            if (TickerPrice >= BuyPrice) {

                Count++;
                if (Count >= 3) {
                    BuyOrderCalc = false;
                }
            }

            if ((TickerPrice >= BuyPrice) && (BuyOrderCalc) && !POSSQL.positionExistsAtPrice(currency, BuyPrice)) {

                // VolumeService service = VolumeService.getInstance();
                // boolean hasVolume = service.hasMinimumVolume(currency, new BigDecimal("300000"));
                // if (!hasVolume) {
                //     System.out.println("Das Handelsvolumen für " + currency + " ist zu gering");
                //     return;
                // }

                empty.Line();
                System.out.println("Setze mal eine Order bei: " + BuyPrice);

                BuyAmaunt = BuyAmountFunktion.getsimplebuyamount();
                if (BuyAmaunt <= 0) {
                    System.out.println("Nicht genügend Geld verfügbar für eine Kauforder. Kaufprozess wird abgebrochen.");
                    return;
                }

                // BuyAmount = abhängig vom STOCH RSI 4h
                double[] stoch = CurrencySQL.getStochRSI(currency);
                double k4h = stoch[0], d4h = stoch[1];

                if (d4h > k4h) {
                    BuyAmaunt = (BuyAmaunt * ((100 - (k4h + d4h)) / 100));
                    System.out.println("Buyamount " + BuyAmaunt + " angepasst aufgrund StochRSI 4h: K=" + k4h + " D=" + d4h);
                    if (BuyAmaunt < 10.0) {
                        BuyAmaunt = 10.0;
                    }
                }

                String buyprice = TradingRulesFormatter.formatOrderPrice(currency, BuyPrice);
                String Quantity = TradingRulesFormatter.calculateAndFormatQuantity(currency, BigDecimal.valueOf(BuyAmaunt), BigDecimal.valueOf(LivePrice.get(0)));

                if (!TradingRulesFormatter.isOrderValid(
                        currency, new BigDecimal(buyprice), new BigDecimal(Quantity))) {
                    System.out.println("⚠️ Order ist ungültig gemäß Binance Trading-Regeln für " + currency);
                    System.out.println("   Preis: " + buyprice + ", Quantity: " + Quantity);
                    return;
                }

                try {
                    NewOrderResponse newOrderResponse = client.newOrder(limitBuy(currency, TimeInForce.GTC, Quantity, buyprice));

                    try (Connection con = DriverManager.getConnection(dbUrl.getPOS())) {
                        String SQL = "INSERT INTO POS (BuyOrderId, OrderPrice, Status, Währung, statusCode) VALUES (?, ?, ?, ?, ?)";

                        try (PreparedStatement pstmt = con.prepareStatement(SQL)) {
                            pstmt.setLong(1, newOrderResponse.getOrderId());
                            pstmt.setBigDecimal(2, new BigDecimal(newOrderResponse.getPrice()));
                            pstmt.setInt(3, 0);
                            pstmt.setString(4, currency);
                            pstmt.setString(5, TradingConstants.STATUS_NEW);

                            pstmt.executeUpdate();
                        }
                    }

                    System.out.println("NEW_POSITION in DataBase: " + BuyPrice + " EUR");
                    System.out.println("BUY AMOUNT bei..........: " + round.three(BuyAmaunt) + " EUR");
                    System.out.println("Quantity bei............: " + Quantity + " " + currency);
                    System.out.println("");
                    return;

                } catch (BinanceApiException ex) {
                    System.err.println("Fehler beim Kauf: Nicht genügend Geld verfügbar - " + ex.getMessage());
                    sleep.for_60_seconds();
                } catch (SQLException ex) {
                    System.err.println("Datenbankfehler: " + ex.getMessage());
                } catch (Exception ex) {
                    System.err.println("Unerwarteter Fehler: " + ex.getMessage());
                    ex.printStackTrace();
                }
            }
        }
    }
}
