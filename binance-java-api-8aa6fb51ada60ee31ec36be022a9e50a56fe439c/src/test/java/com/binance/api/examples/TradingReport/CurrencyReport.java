// package com.binance.api.examples.TradingReport;

// import com.binance.api.client.BinanceApiClientFactory;
// import com.binance.api.client.BinanceApiRestClient;
// import java.util.List;
// import java.lang.Math;
// //import com.binance.api.client.domain.account.Account;
// import com.binance.api.examples.HelperFunctions.Asset;
// import com.binance.api.examples.HelperFunctions.round;

// import java.util.ArrayList;
// import java.sql.Connection;
// import java.sql.DriverManager;
// import java.sql.ResultSet;
// import java.sql.SQLException;
// import java.sql.Statement;

// public class CurrencyReport {
//     public static void main(String[] args) {

//         boolean weitermachen = true;
//         while (weitermachen) {

//             try {
//                 double CoinTracking = 70.00; // CoinTracking Gebühren pro Monat
//                 double Energiekosten = 20.00; // Energiekosten pro Monat

//                 // -> Set New Key 09/2024
//                 String key = "GunwtQg5ADNRyo2TPVvOZRoGKyZlNoDCg9uDlaPO7seL3W2J195bwuHZnAEZjoaA";
//                 String secret = "mZN0M5mvnitVYJzbhEDlYxahENyHXeT4ZH9R1fAzxNItqUQzslKfKR7RuUQiiNYA";

//                 BinanceApiClientFactory factory = BinanceApiClientFactory.newInstance(key, secret);
//                 BinanceApiRestClient client = factory.newRestClient();

//                 // final String ATH =
//                 // "jdbc:sqlite:C:/TradingBot/SQLiteStudio/Datenbanken/LTC_EUR/ATH_LTCEUR.db";
//                 final String HIST = "jdbc:sqlite:C:/TradingBot/SQLiteStudio/Datenbanken/LTC_EUR/POS_LTCEUR_HIST.db";

//                 List<Long> BuyOrderIdList = new ArrayList<Long>();
//                 List<String> GetRecordFromDataBase_POS = new ArrayList<String>();
//                 List<String> GetRecordFromDataBase_HIST = new ArrayList<String>();
//                 List<Double> GetSumQuantity = new ArrayList<Double>();
//                 List<Double> GetSumOriginalBuyPrice = new ArrayList<Double>();
//                 List<Double> GetSumBuyAmount = new ArrayList<Double>();

//                 // -----------------------------------------------------------------------------------------------------------------------------------------------------

//                 boolean weiter = true;
//                 while (weiter) {

//                     // -------------------------------------------------------------------------------------------------------------------------------

//                     // --- CalC the rest of the Money ???
//                     // Calculate_Fee_Tax_Win(CoinTracking, Energiekosten, client, HIST, EURO);

//                     // System.out.println(GetEUR_Account_Balance(EURO, client.getAccount()));

//                     // --- Durchschnitt Profit berechnen
//                     AverageProfitCalcOfHistDataBase(HIST, 2024);

//                     // UpdateGewinnAfterTax(HIST, Record);

//                     getDataRecords(HIST, GetRecordFromDataBase_HIST);
//                     System.out.println("SellOrder in HIST: " + GetRecordFromDataBase_HIST.size());

//                     // GetSumQuantity(POS, GetSumQuantity);
//                     // GetSumOriginalBuyPrice(POS, GetSumOriginalBuyPrice);
//                     // GetSumBuyAmount(POS, GetSumBuyAmount);

//                 }
//             } catch (IndexOutOfBoundsException e) {
//                 String FehlerMessage = "Fehler: Index out of bounds! Der Fehler liegt in einem leerem Array irgendwo in dem Code!";
//                 System.err.println(FehlerMessage);
//                 continue;
//             }
//         }
//     }

//     private static double GetTaxe(final String HIST) {
//         double Taxe24 = 0.0;
//         try {
//             Connection con = DriverManager.getConnection(HIST);
//             Statement query = con.createStatement();
//             String SQL = "SELECT SUM(Tax) AS TotalAmount FROM HIST WHERE Status = 1";
//             ResultSet rs = query.executeQuery(SQL);

//             if (rs.next()) {
//                 double SumTotalTax = rs.getDouble("TotalAmount");
//                 SumTotalTax = round.two(SumTotalTax); // assuming RoundTWO is defined somewhere
//                 Taxe24 = SumTotalTax;
//             }
//             con.close();
//             query.close();
//         } catch (SQLException | NullPointerException | NumberFormatException e) {
//             System.out.println("Es ist ein Fehler aufgetreten: " + e.getMessage());
//         } catch (Exception e) {
//             System.out.println("Es ist ein unbekannter Fehler aufgetreten: " + e.getMessage());
//         }
//         return Taxe24;
//     }

//     private static void GetSumBuyAmount(final String POS, List<Double> GetSumBuyAmount) {
//         try {
//             GetSumBuyAmount.clear();
//             Connection con = DriverManager.getConnection(POS);
//             Statement query = con.createStatement();
//             String SQL = "SELECT SUM(BuyAmount) AS BuyAmount FROM POS WHERE Status = 1";
//             ResultSet rs = query.executeQuery(SQL);

//             if (rs.next()) {
//                 double SumBuyAmount = rs.getDouble("BuyAmount");
//                 SumBuyAmount = round.two(SumBuyAmount);
//                 GetSumBuyAmount.add(SumBuyAmount);
//             }

//             con.close();
//             query.close();
//         } catch (SQLException | NullPointerException | NumberFormatException e) {
//             System.out.println("Es ist ein Fehler aufgetreten: " + e.getMessage());
//         } catch (Exception e) {
//             System.out.println("Es ist ein unbekannter Fehler aufgetreten: " + e.getMessage());
//         }
//     }

//     private static void GetSumOriginalBuyPrice(final String POS, List<Double> GetSumOriginalBuyPrice) {
//         try {
//             GetSumOriginalBuyPrice.clear();
//             Connection con = DriverManager.getConnection(POS);
//             Statement query = con.createStatement();
//             String SQL = "SELECT SUM(OrigPrice) AS OriginalBuyPrice FROM POS WHERE Status = 1";
//             ResultSet rs = query.executeQuery(SQL);

//             if (rs.next()) {
//                 double OrigBuyPrice = rs.getDouble("OriginalBuyPrice");
//                 OrigBuyPrice = round.two(OrigBuyPrice);
//                 GetSumOriginalBuyPrice.add(OrigBuyPrice);
//             }

//             con.close();
//             query.close();
//         } catch (SQLException | NullPointerException | NumberFormatException e) {
//             System.out.println("Es ist ein Fehler aufgetreten: " + e.getMessage());
//         } catch (Exception e) {
//             System.out.println("Es ist ein unbekannter Fehler aufgetreten: " + e.getMessage());
//         }
//     }

//     private static void GetSumQuantity(final String POS, List<Double> GetQuantity) {
//         try {
//             GetQuantity.clear(); // Du kannst das entfernen, wenn du sicher bist, dass die Liste vor dem
//                                  // Hinzufügen leer ist.
//             Connection con = DriverManager.getConnection(POS);
//             Statement query = con.createStatement();
//             String SQL = "SELECT SUM(Qty) AS Quantity FROM POS WHERE Status = 1";
//             ResultSet rs = query.executeQuery(SQL);

//             if (rs.next()) {
//                 double Quantity_Double = rs.getDouble("Quantity");
//                 Quantity_Double = round.five(Quantity_Double);
//                 GetQuantity.add(Quantity_Double);
//             }

//             con.close();
//             query.close();
//         } catch (SQLException | NullPointerException | NumberFormatException e) {
//             System.out.println("Es ist ein Fehler aufgetreten: " + e.getMessage());
//         } catch (Exception e) {
//             System.out.println("Es ist ein unbekannter Fehler aufgetreten: " + e.getMessage());
//         }
//     }

//     public static double AverageProfitCalcOfHistDataBase(final String HIST, int year) {
//         double AverageProfit = 0.0;
    
//         try (Connection con = DriverManager.getConnection(HIST);
//              Statement query = con.createStatement();
//              ResultSet rs = query.executeQuery(
//                 "SELECT AVG(Profit) AS AverageProfit FROM HIST WHERE Profit IS NOT NULL RIGHT(Selldate, 4) = '2024'")) {
    
//             if (rs.next()) {
//                 AverageProfit = round.three(rs.getDouble("AverageProfit")); // Berechne den Durchschnittswert
//             }    
//         } catch (SQLException err) {
//             System.out.println("SQL-Fehler: " + err.getMessage());
//         }    
//         return AverageProfit;
//     }
    

//     private static void Calculate_Fee_Tax_Win(double coinTracking, double energieCost, BinanceApiRestClient client,
//             final String HIST, String EURO) {
//         try {
//             Double EUR_Account_Balance = Asset.getFreeCalced_Balance(EURO, client);
//             System.out.println("");

//             // Steuerausgaben pro Jahr berechnen
//             Connection con = DriverManager.getConnection(HIST);
//             Statement query = con.createStatement();
//             String SQL = "SELECT SUM(Tax) AS TotalAmount, SUM(Gewinn) AS SumGewinn, SUM(Fee) AS TotalFee, SUM(GewinnAfterTax) AS TotalGewinnAfter FROM HIST";
//             ResultSet rs = query.executeQuery(SQL);

//             if (rs.next()) {
//                 double SumTotalTax = rs.getDouble("TotalAmount");
//                 SumTotalTax = round.five(SumTotalTax);

//                 double GewinnAfterTax = rs.getDouble("TotalGewinnAfter");
//                 GewinnAfterTax = Math.round(100.0 * GewinnAfterTax) / 100.0;

//                 double Fee2024 = rs.getDouble("TotalFee");
//                 Fee2024 = Math.round(100.0 * Fee2024) / 100.0;

//                 double SumGewinn = rs.getDouble("SumGewinn");
//                 SumGewinn = Math.round(100.0 * SumGewinn) / 100.0;

//                 double Tax2024 = Math.round(100.0 * SumTotalTax) / 100.0;

//                 double AllPaymentPerAnno = -(((coinTracking + energieCost) * 12) + SumTotalTax);
//                 AllPaymentPerAnno = Math.round(100.0 * AllPaymentPerAnno) / 100.0;

//                 double Summe = EUR_Account_Balance + AllPaymentPerAnno;
//                 Summe = Math.round(100.0 * Summe) / 100.0;

//                 double DiffWinAndTax = GewinnAfterTax - Tax2024;
//                 DiffWinAndTax = Math.round(100.0 * DiffWinAndTax) / 100.0;

//                 System.out.println("Free Account Balance..: " + (round.two(EUR_Account_Balance - Tax2024) + " EUR"));
//                 System.out.println("Alle Ausgaben per Anno: " + AllPaymentPerAnno + " EUR - 1129.75€");
//                 System.out.println("Guthaben nach Ausgaben: " + Summe + " EUR");
//                 System.out.println("Gewinn vor Steuer....: " + SumGewinn + " EUR");
//                 System.out.println("Steuern per Anno 2024: " + Tax2024 + " EUR");
//                 System.out.println("Gebühre per Anno 2024: " + Fee2024 + " EUR");
//                 System.out.println("Gewinne per Anno 2024: " + GewinnAfterTax + " EUR");
//                 System.out.println("Differenz Win and Tax: " + (DiffWinAndTax) + " EUR");
//             }

//             con.close();
//             query.close();

//         } catch (SQLException | NullPointerException | NumberFormatException e) {
//             System.out.println("Es ist ein Fehler aufgetreten: " + e.getMessage());
//         } catch (Exception e) {
//             System.out.println("Es ist ein unbekannter Fehler aufgetreten: " + e.getMessage());
//         }
//     }

//     public static void getDataRecords(String HIST, List<String> GetDataRecord) {
//         try {
//             GetDataRecord.clear();
//             Connection con = DriverManager.getConnection(HIST);
//             Statement query = con.createStatement();
//             String SQL = "SELECT Währung, BuyOrderId, SellOrderId, Quantity, BuyAmount, BuyPrice, SellPrice, Profit, Differenz, BuyTime, SellTime, Fee, Tax, Gewinn, GewinnAfterTax FROM HIST WHERE Status = 1";
//             ResultSet rs = query.executeQuery(SQL);

//             while (rs.next()) {
//                 String Währung = rs.getString("Währung");
//                 String BuyOrderId = rs.getString("BuyOrderId");
//                 String SellOrderId = rs.getString("SellOrderId");
//                 String Quantity = rs.getString("Quantity");
//                 String BuyAmount = rs.getString("BuyAmount");
//                 String BuyPrice = rs.getString("BuyPrice");
//                 String SellPrice = rs.getString("SellPrice");
//                 String Profit = rs.getString("Profit");
//                 String Differenz = rs.getString("Differenz");
//                 String BuyTime = rs.getString("BuyTime");
//                 String SellTime = rs.getString("SellTime");
//                 String Fee = rs.getString("Fee");
//                 String Tax = rs.getString("Tax");
//                 String Gewinn = rs.getString("Gewinn");
//                 String GewinnAfterTax = rs.getString("GewinnAfterTax");

//                 String dataRecord = Währung + ", " + BuyOrderId + ", " + SellOrderId + ", " + Quantity + ", " +
//                         BuyAmount + ", " + BuyPrice + ", " + SellPrice + ", " + Profit + ", " +
//                         Differenz + ", " + BuyTime + ", " + SellTime + ", " + Fee + ", " +
//                         Tax + ", " + Gewinn + ", " + GewinnAfterTax;
//                 GetDataRecord.add(dataRecord);
//             }
//             con.close();
//             query.close();

//         } catch (SQLException err) {
//             System.out.println(err.getMessage());
//         }
//     }
// }
