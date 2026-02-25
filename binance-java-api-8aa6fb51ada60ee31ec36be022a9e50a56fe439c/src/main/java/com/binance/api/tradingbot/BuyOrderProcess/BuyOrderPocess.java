package com.binance.api.tradingbot.BuyOrderProcess;

import static com.binance.api.client.domain.account.NewOrder.limitBuy;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
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
import com.binance.api.tradingbot.HelperFunctions.RoundCurrency;
import com.binance.api.tradingbot.HelperFunctions.empty;
import com.binance.api.tradingbot.HelperFunctions.round;
import com.binance.api.tradingbot.HelperFunctions.sleep;
import com.binance.api.tradingbot.SQL_Database.ATHSQL;
import com.binance.api.tradingbot.SQL_Database.POSSQL;
import com.binance.api.tradingbot.SQL_Database.SETSQL;
import com.binance.api.tradingbot.Settings.set;
import com.binance.api.tradingbot.constants.TradingConstants;

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
            BuyPrice = RoundCurrency.forTickerPrice(Ath, currency);

            if (TickerPrice >= BuyPrice) {

                Count++;
                if (Count >= 3) {
                    BuyOrderCalc = false;
                }
            }

            if ((TickerPrice >= BuyPrice) && (unten > BuyPrice) && (BuyOrderCalc)) {
            // if ((TickerPrice >= BuyPrice) && !POSSQL.positionExistsAtPrice(currency, BuyPrice) && (BuyOrderCalc)) {

                empty.Line();
                System.out.println("Setze mal eine Order bei: " + BuyPrice);                        

                BuyAmaunt = BuyAmountFunktion.getsimplebuyamount();
                if (BuyAmaunt <= 0) {
                    return;
                }             

                String Quantity = getQty(currency, LivePrice, BuyAmaunt);
                String buyprice = String.valueOf(BuyPrice);

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

    private static double checkBuyAmount(BinanceApiRestClient client, double BuyAmaunt) {

        double sqlBalance = SETSQL.getBalance_SQL();
        double ExcangeBalance = Asset.getFreeCalced_Balance(TradingConstants.BASE_CURRENCY, client);

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
