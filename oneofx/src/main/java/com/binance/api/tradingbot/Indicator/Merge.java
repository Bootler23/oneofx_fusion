package com.binance.api.tradingbot.Indicator;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import com.binance.api.tradingbot.Database.dbUrl;
import com.binance.api.tradingbot.HelperFunctions.round;
import com.binance.api.tradingbot.SQL_Database.POSSQL;

public class Merge {

    public static void splitValue(String currency, List<String> SplitRecords) {
        boolean splitComplete = false;

        for (String dataRecord : SplitRecords) {
            if (splitComplete) {
                break;
            }

            String[] parts = dataRecord.split(", ");
            String BuyOrderId_Split = parts[0];
            String profitSplitValue = parts[1];
            String currency_Split = parts[2];
            double profitSplitValue_Double = Double.valueOf(profitSplitValue);

            List<String> BuyAmountRecord = new ArrayList<String>();
            POSSQL.getDataRecordsPOS_WithMaxInMinus(currency, BuyAmountRecord);

            if (!BuyAmountRecord.isEmpty() && (profitSplitValue_Double > 0.01) && currency_Split.equals(currency)) { 
                                                                                  
                String record = BuyAmountRecord.get(0); 
                String[] recordParts = record.split(", ");              
                String BuyOrderId_POS = recordParts[0];               
                String Quantity = recordParts[3];
                String BuyAmount = recordParts[4];
                String BuyPrice = recordParts[5];

                double new_Buymount, new_BuyPrice;

                System.out.println("ProfitSplitValue: " + profitSplitValue);
                System.out.println("Old Position: " + BuyOrderId_POS + " - " + Quantity + " - " + BuyAmount + " - " + BuyPrice);

                new_Buymount = round.five(Double.valueOf(BuyAmount) - Double.valueOf(profitSplitValue));
                new_BuyPrice = round.five(new_Buymount / Double.valueOf(Quantity));
              
                if (new_Buymount < 6) {
                    return;
                }             

                System.out.println("New Position: " + BuyOrderId_POS + " - " + Quantity + " - " + new_Buymount + " - " + new_BuyPrice);

                updateOrderPOS("POS", new_Buymount, new_BuyPrice, BuyOrderId_POS);
                updateOrderHIST("HIST", new_Buymount, new_BuyPrice, BuyOrderId_POS);
                updateHIST_Status("HIST", BuyOrderId_Split);

                splitComplete = true;
            } else {
                updateHIST_Status("HIST", BuyOrderId_Split);
            }
        }
    }   

    public static void updateOrderPOS(final String tableName, double BuyAmount, double Price, String BuyOrderId) {

        try (Connection con_update = DriverManager.getConnection(dbUrl.getoneOfX());
                Statement update = con_update.createStatement()) {

            String SQL = "UPDATE " + tableName + " SET "
                    + "BuyPrice = " + Price + ", "
                    + "BuyAmount = " + BuyAmount + ", "
                    + "Status = 7 "
                    + "WHERE BuyOrderId = '" + BuyOrderId + "';";

            update.executeUpdate(SQL);

        } catch (SQLException err) {
            System.out.println("Fehler beim Aktualisieren der Daten: " +
                    err.getMessage());
        }
    }

    public static void updateOrderHIST(final String tableName, double BuyAmount,
            double Price, String BuyOrderId) {
        try (Connection con_update = DriverManager.getConnection(dbUrl.getoneOfX());
                Statement update = con_update.createStatement()) {

            String SQL = "UPDATE " + tableName + " SET "
                    + "BuyPrice = " + Price + ", "
                    + "BuyAmount = " + BuyAmount + " "
                    + "WHERE BuyOrderId = '" + BuyOrderId + "';";

            update.executeUpdate(SQL);

        } catch (SQLException err) {
            System.out.println("Fehler beim Aktualisieren der Daten: " +
                    err.getMessage());
        }
    }

    private static void updateHIST_Status(final String tableName, String BuyOrderId) {
        try (Connection con_update = DriverManager.getConnection(dbUrl.getoneOfX());
                Statement update = con_update.createStatement()) {

            String SQL = "UPDATE " + tableName + " SET "
                    + "Status = 2 "
                    + "WHERE BuyOrderId = '" + BuyOrderId + "';";

            update.executeUpdate(SQL);

        } catch (SQLException err) {
            System.out.println("Fehler beim Aktualisieren der Daten: " + err.getMessage());
        }
    }
}
