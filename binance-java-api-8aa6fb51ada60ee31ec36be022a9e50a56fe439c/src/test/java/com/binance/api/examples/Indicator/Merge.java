package com.binance.api.examples.Indicator;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import com.binance.api.examples.SQL_Database.POSSQL;
import com.binance.api.examples.HelperFunctions.round;

public class Merge {

    public static void splitValue(final String POS, final String HIST, List<String> SplitRecords, List<Double> LivePrice) {
        boolean splitComplete = false;

        for (String dataRecord : SplitRecords) {
            if (splitComplete) {
                break;
            }

            String[] parts = dataRecord.split(", ");
            String BuyOrderId_Split = parts[0];
            String profitSplitValue = parts[1];
            double profitSplitValue_Double = Double.valueOf(profitSplitValue);

            List<String> BuyAmountRecord = new ArrayList<String>();
            
            // Hole einen Datensatz mit dem Status 1 und dem größten OrderPrice
            POSSQL.getDataRecordsPOS_FirstEntryWithHighestOrderPrice(POS, BuyAmountRecord);

            if (!BuyAmountRecord.isEmpty() && (profitSplitValue_Double > 0.01)) { // Prüft, ob ein Datensatz vorhanden ist
                String record = BuyAmountRecord.get(0); // Ersten Eintrag holen

                String[] recordParts = record.split(", ");
                String BuyOrderId_POS = recordParts[0];
                String Quantity = recordParts[2];
                String BuyAmount = recordParts[3];
                String OrigBuyPrice = recordParts[5];

                double new_Buymount, new_BuyPrice;

                System.out.println("ProfitSplitValue: " + profitSplitValue);
                System.out.println("Old Position: " + BuyOrderId_POS + " - " + Quantity + " - " + BuyAmount + " - "
                        + OrigBuyPrice);

                
                new_Buymount = round.five(Double.valueOf(BuyAmount) - Double.valueOf(profitSplitValue));
                if (new_Buymount < 6) {
                    return;
                }               
              
                new_BuyPrice = round.five(new_Buymount / Double.valueOf(Quantity));
              

                System.out.println("New Position: " + BuyOrderId_POS + " - " + Quantity + " - " + new_Buymount + " - "
                        + new_BuyPrice);

                updateOrderPOS(POS, "POS", new_Buymount, new_BuyPrice, BuyOrderId_POS);
                updateOrderHIST(HIST, "HIST", new_Buymount, new_BuyPrice, BuyOrderId_POS);
                updateHIST_Status(HIST, "HIST", BuyOrderId_Split);

                splitComplete = true;
            } else {
                updateHIST_Status(HIST, "HIST", BuyOrderId_Split);
            }
        }
    }

    private static void updateOrderPOS(final String Url_dataBase, final String tableName, double BuyAmount, double Price, String BuyOrderId) {

        try (Connection con_update = DriverManager.getConnection(Url_dataBase);
                Statement update = con_update.createStatement()) {

            String SQL = "UPDATE " + tableName + " SET "
                    + "BuyPrice = " + Price + ", "
                    + "OrigPrice = " + Price + ", "
                    + "OrderPrice = " + Price + ", "
                    + "BuyAmount = " + BuyAmount + " "                 
                    + "WHERE BuyOrderId = '" + BuyOrderId + "';";

            update.executeUpdate(SQL);

        } catch (SQLException err) {
            System.out.println("Fehler beim Aktualisieren der Daten: " +
                    err.getMessage());
        }
    }

    private static void updateOrderHIST(final String Url_dataBase, final String tableName, double BuyAmount,
            double Price, String BuyOrderId) {
        try (Connection con_update = DriverManager.getConnection(Url_dataBase);
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

    private static void updateHIST_Status(final String Url_dataBase, final String tableName, String BuyOrderId) {
        try (Connection con_update = DriverManager.getConnection(Url_dataBase);
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
