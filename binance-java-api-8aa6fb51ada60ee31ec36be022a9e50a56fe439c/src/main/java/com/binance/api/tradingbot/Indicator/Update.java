package com.binance.api.tradingbot.Indicator;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;

import com.binance.api.tradingbot.Database.dbUrl;
import com.binance.api.tradingbot.SQL_Database.SETSQL;

public class Update {

    // public static void SellPriceAfterOneDay(final String POS, List<String> getDataRecords) {

    //     for (String DataRecord : getDataRecords) {
    //         String[] parts = DataRecord.split(", ");
    //         String OrderId = parts[0];
    //         String Original_Buy_Price = parts[5];
    //         String DiffDataBase = parts[6];

    //         String BuyDate = parts[7];
    //         String BuyTime = parts[8];

    //         String BuyDate_Time = BuyDate + " " + BuyTime;
    //         LocalDateTime buyDateTime;

    //         DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss");

    //         try {
    //             if ("null".equals(BuyDate)) {
    //                 buyDateTime = LocalDateTime.of(LocalDate.now(),
    //                         LocalTime.parse(BuyTime, DateTimeFormatter.ofPattern("HH:mm:ss")));
    //             } else {
    //                 buyDateTime = LocalDateTime.parse(BuyDate_Time, dateTimeFormatter);
    //             }
    //         } catch (Exception e) {
    //             System.err.println("Fehler beim Parsen des Datums: " + BuyDate_Time);
    //             continue;
    //         }

    //         int DiffDB = 0;
    //         try {
    //             DiffDB = Integer.parseInt(DiffDataBase.trim());
    //         } catch (NumberFormatException e) {
    //             System.err.println("Fehler beim Parsen von DiffDataBase: " + DiffDataBase);
    //             continue;
    //         }

    //         double Orig_Buy_Price = Double.parseDouble(Original_Buy_Price);
    //         LocalDateTime currentDateTime = LocalDateTime.now();
    //         long DiffCalc = ChronoUnit.DAYS.between(buyDateTime, currentDateTime);

    //         if (DiffCalc > DiffDB) {
    //             Double NewCalcBuyPrice = round.five((Orig_Buy_Price / 100) * (100 + (DiffCalc * 0.1))); // TODO 27.02.2025
    //             System.out.println("Neuer BuyPrice " + NewCalcBuyPrice);

    //             try (Connection con_update = DriverManager.getConnection(POS);
    //                     Statement update = con_update.createStatement()) {

    //                 String SQL_update = "UPDATE POS SET BuyPrice = " + NewCalcBuyPrice +
    //                         ", OrderPrice = " + NewCalcBuyPrice +
    //                         ", Differenz = " + DiffCalc +
    //                         " WHERE BuyOrderId = " + OrderId + ";";

    //                 update.executeUpdate(SQL_update);
    //                 System.out.println("Update BuyPrice in POS: " + NewCalcBuyPrice);

    //             } catch (SQLException err) {
    //                 System.out.println("Fehler beim Update des BuyPrice in POS: " + err.getMessage());
    //             }
    //         }
    //     }
    // }

    public static void NewCounterPosition() {
        int count = SETSQL.getCount();
        count++;
        SETSQL.updateCount(count);
    }
}