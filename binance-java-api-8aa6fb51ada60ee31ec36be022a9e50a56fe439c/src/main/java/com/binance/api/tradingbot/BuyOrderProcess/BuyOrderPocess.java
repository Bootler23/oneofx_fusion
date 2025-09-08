package com.binance.api.tradingbot.BuyOrderProcess;

import static com.binance.api.client.domain.account.NewOrder.limitBuy;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.List;
import java.util.Locale;

import com.binance.api.client.BinanceApiRestClient;
import com.binance.api.client.domain.TimeInForce;
import com.binance.api.client.domain.account.NewOrderResponse;
import com.binance.api.client.exception.BinanceApiException;
import com.binance.api.tradingbot.Database.dbUrl;
import com.binance.api.tradingbot.HelperFunctions.Asset;
import com.binance.api.tradingbot.HelperFunctions.empty;
import com.binance.api.tradingbot.HelperFunctions.round;
import com.binance.api.tradingbot.HelperFunctions.sleep;
import com.binance.api.tradingbot.SQL_Database.ATHSQL;
import com.binance.api.tradingbot.SQL_Database.POSSQL;
import com.binance.api.tradingbot.SQL_Database.SETSQL;
import com.binance.api.tradingbot.Settings.set;

public class BuyOrderPocess {

    public static void setBuyOrder(String CurrencyPair, String EURO, BinanceApiRestClient client,
            List<Double> LivePrice) {

        double BuyAmaunt;
        double Ath = ATHSQL.getAllTimeHigh(CurrencyPair);
        double unten = POSSQL.getLastPrice(CurrencyPair, LivePrice);
        double BuyPrice;
        int Count = 0;
        boolean BuyOrderCalc = true;
        double TickerPrice = LivePrice.get(0);
        int grid = set.getGridforCurrency(CurrencyPair);

        while (BuyOrderCalc) {

            Ath = Ath - ((Ath / 100) / grid);
            BuyPrice = round.two(Ath);

            if (TickerPrice >= BuyPrice) {

                Count++;
                if (Count >= 3) {
                    BuyOrderCalc = false;
                }
            }

            if ((TickerPrice >= BuyPrice) && (unten > BuyPrice) && (BuyOrderCalc)) {

                empty.Line();
                System.out.println("Setze mal eine Order bei: " + BuyPrice);

                ATHSQL.GetHighestBuyAmount(CurrencyPair);

                sleep.for_05_second();

                BuyAmaunt = BuyAmountFunktion.getBuyAmount(CurrencyPair, EURO, client, LivePrice, true);

                BuyAmaunt = checkBuyAmount(EURO, client, BuyAmaunt);

                String Quantity = getQty(CurrencyPair, LivePrice, BuyAmaunt);
                String buyprice = String.valueOf(BuyPrice);

                try {
                    NewOrderResponse newOrderResponse = client
                            .newOrder(limitBuy(CurrencyPair, TimeInForce.GTC, Quantity, buyprice));

                    try (Connection con = DriverManager.getConnection(dbUrl.getPOS())) {
                        String SQL = "INSERT INTO POS (BuyOrderId, OrderPrice, Status, Währung) VALUES (?, ?, ?, ?)";
                        try (PreparedStatement pstmt = con.prepareStatement(SQL)) {
                            pstmt.setLong(1, newOrderResponse.getOrderId());
                            pstmt.setBigDecimal(2, new BigDecimal(newOrderResponse.getPrice()));
                            pstmt.setInt(3, 0);
                            pstmt.setString(4, CurrencyPair);
                            pstmt.executeUpdate();
                        }
                    }

                    System.out.println("NEW_POSITION in DataBase: " + BuyPrice + " EUR");
                    System.out.println("BUY AMOUNT bei..........: " + round.three(BuyAmaunt) + " EUR");
                    System.out.println("Quantity bei............: " + Quantity + " " + CurrencyPair);
                    System.out.println("");
                    break;

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

    private static double checkBuyAmount(String EURO, BinanceApiRestClient client, double BuyAmaunt) {

        double sqlBalance = SETSQL.getBalance_SQL();
        double ExcangeBalance = Asset.getFreeCalced_Balance(EURO, client);

        if (sqlBalance != ExcangeBalance) {
            System.out.println(
                    "Balance stimmt nicht überein.------------------ Preis wird zugewiesen ---------------------------------");
            BuyAmaunt = round.two(POSSQL.getAverageBuyAmount());
        }
        return BuyAmaunt;
    }

    public static String getQty(String Currency, List<Double> LivePrice, double BuyAmaunt) {
        double Qty = round.Quantity((BuyAmaunt / LivePrice.get(0)), Currency);
        return roundWithPoint(Qty);
    }

    public static String getQtySimple(String Currency, double Quantity) {
        double Qty = round.Quantity(Quantity, Currency);
        return roundWithPoint(Qty);
    }

    public static String roundWithPoint(double Qty) {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.US);
        symbols.setDecimalSeparator('.');

        // Festlegen des Formats mit genau 6 Nachkommastellen
        DecimalFormat df = new DecimalFormat("0.000000", symbols);
        df.setRoundingMode(RoundingMode.HALF_UP); // Normale Rundung

        // Formatieren des Ergebnisses als String
        return df.format(Qty);
    }
}
