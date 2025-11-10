package com.binance.api.tradingbot.SellOrderProcess;

import static com.binance.api.client.domain.account.NewOrder.marketSell;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import com.binance.api.client.BinanceApiRestClient;
import com.binance.api.client.domain.account.NewOrderResponse;
import com.binance.api.client.exception.BinanceApiException;
import com.binance.api.tradingbot.Database.dbUrl;
import com.binance.api.tradingbot.HelperFunctions.CalcPercenToSell;
import com.binance.api.tradingbot.HelperFunctions.Time;
import com.binance.api.tradingbot.HelperFunctions.empty;
import com.binance.api.tradingbot.HelperFunctions.round;
import com.binance.api.tradingbot.HelperFunctions.sleep;
import com.binance.api.tradingbot.SQL_Database.POSSQL;

public class SellOrderProcess {

    public static void setSellOrder(String currency, BinanceApiRestClient client,
            List<String> GetRecordFromDataBase_POS, List<Double> LivePrice) {

        double percent = CalcPercenToSell.PercentToSell(currency);
       
        if (percent <= 0) {
            percent = 1.0;
        }

        percent = 1.31;

        for (String dataRecord : GetRecordFromDataBase_POS) {
            String[] parts = dataRecord.split(", ");

            String BuyOrderId = parts[0];
            String Quantity_String = parts[2];
            String BuyPrice_String = parts[4];
            double BuyPrice_Double = round.two(Double.valueOf(BuyPrice_String));
            double sellTarget = (BuyPrice_Double / 100) * (100 + percent);

            if ((LivePrice.get(0) >= sellTarget)) {

                empty.Line();
                System.out.println("es soll " + currency + " verkauft werden");
                System.out.println("DEBUG: Verkaufsbedingung erfüllt! Starte Verkauf...");

                try {
                    NewOrderResponse newOrderResponse = getNewSellOrderResponse(currency, client, Quantity_String);

                    update_POS_AfterMarketSell(BuyOrderId);
                    update_HIST_AfterMarketSell(BuyOrderId, Time.getCurrentTime_HHmmss(),
                            Time.getCurrentDate(), newOrderResponse);
                    delete_POS_AfterMarketSell(BuyOrderId);

                    System.out.println("DEBUG: Verkauf erfolgreich abgeschlossen für BuyOrderId: " + BuyOrderId);

                } catch (BinanceApiException ex) {
                    String FehlerMessage = "Fehler beim Verkauf: Keine Menge für den Verkauf Vorhanden!";
                    System.out.println(FehlerMessage);
                    System.err.println("DEBUG: BinanceApiException: " + ex.getMessage());

                    sleep.for_10_seconds();
                    break;
                }
            }
        }
    }

    private static void delete_POS_AfterMarketSell(String BuyOrderId) {
        try (Connection con = DriverManager.getConnection(dbUrl.getPOS());
                PreparedStatement pstmt = con.prepareStatement(
                        "DELETE FROM POS WHERE BuyOrderId = ?")) {

            pstmt.setString(1, BuyOrderId);
            pstmt.executeUpdate();

        } catch (SQLException err) {
            System.err.println("Fehler beim Löschen: " + err.getMessage());
            err.printStackTrace();
        }
    }

    private static void update_HIST_AfterMarketSell(String BuyOrderId, String SellTime,
            String SellDate, NewOrderResponse newOrderResponse) {

        try (Connection con = DriverManager.getConnection(dbUrl.getHIST());
                PreparedStatement pstmt = con.prepareStatement(
                        "UPDATE HIST SET Status = ?, SellOrderId = ?, SellTime = ?, SellDate = ? WHERE BuyOrderId = ?")) {

            pstmt.setInt(1, 0);
            pstmt.setLong(2, newOrderResponse.getOrderId());
            pstmt.setString(3, SellTime);
            pstmt.setString(4, SellDate);
            pstmt.setString(5, BuyOrderId);

            pstmt.executeUpdate();

        } catch (SQLException err) {
            System.err.println("Fehler beim Aktualisieren: " + err.getMessage());
            err.printStackTrace();
        }
    }

    private static void update_POS_AfterMarketSell(String BuyOrderId) {
        try (Connection con = DriverManager.getConnection(dbUrl.getPOS());
                Statement stmt = con.createStatement()) {

            String UpdateSQL = "UPDATE POS SET Status = 2 WHERE BuyOrderId = '" + BuyOrderId + "';";
            stmt.executeUpdate(UpdateSQL);

        } catch (SQLException err) {
            System.out.println("Fehler beim Aktualisieren: " + err.getMessage());
        }
    }

    public static NewOrderResponse getNewSellOrderResponse(String CurrencyPair, BinanceApiRestClient client,
            String Quantity_String) {
        NewOrderResponse newOrderResponse = client.newOrder(marketSell(CurrencyPair, Quantity_String));
        return newOrderResponse;
    }

    public static void handleSellProcess(String currency, BinanceApiRestClient client) {

        List<String> BuyAmountRecord = new ArrayList<String>();

        // nicht die größte Position sondern die, die am weitesten im Minus ist
        POSSQL.getPositionWithMaxInMinus(BuyAmountRecord, currency, client);

        String record = BuyAmountRecord.get(0);
        String[] recordParts = record.split(", ");

        String BuyOrderId = recordParts[0];
        String Quantity_String = recordParts[1];
        String CurrencyPair = recordParts[2];

        try {
            NewOrderResponse newOrderResponse = getNewSellOrderResponse(CurrencyPair, client, Quantity_String);

            update_POS_AfterMarketSell(BuyOrderId);
            update_HIST_AfterMarketSell(BuyOrderId, Time.getCurrentTime_HHmmss(),
                    Time.getCurrentDate(), newOrderResponse);
            delete_POS_AfterMarketSell(BuyOrderId);

        } catch (BinanceApiException dex) {
            System.err.println("Fehler beim Verkauf: Keine Menge für den Verkauf verfügbar!");
            sleep.for_10_seconds();
        } catch (Exception e) {
            System.err.println("Fehler beim Verkaufsprozess: " + e.getMessage());
        }
    }
}
