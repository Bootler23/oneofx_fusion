package com.binance.api.examples.Indicator;
// package com.binance.api.examples;

// import com.binance.api.client.BinanceApiClientFactory;
// import com.binance.api.client.BinanceApiRestClient;
// import java.util.List;

// import com.binance.api.client.domain.account.Order;
// import com.binance.api.client.domain.market.TickerPrice;

// import java.lang.Math;
// import com.binance.api.client.domain.account.Account;
// import com.binance.api.client.domain.TimeInForce;
// import static com.binance.api.client.domain.account.NewOrder.limitBuy;
// import static com.binance.api.client.domain.account.NewOrder.marketSell;
// import com.binance.api.client.domain.OrderSide;
// import com.binance.api.client.domain.OrderStatus;
// import com.binance.api.client.domain.account.NewOrderResponse;
// import com.binance.api.client.exception.BinanceApiException;
// //import com.fasterxml.jackson.annotation.JacksonInject.Value;
// import com.binance.api.client.domain.account.request.CancelOrderRequest;
// import com.binance.api.client.domain.account.request.OrderStatusRequest;

// import java.util.ArrayList;
// import java.util.Collections;
// import java.sql.Connection;
// import java.sql.DriverManager;
// import java.sql.ResultSet;
// import java.sql.SQLException;
// import java.sql.Statement;
// import java.text.DecimalFormat;
// import java.math.RoundingMode;
// import java.text.DecimalFormatSymbols;
// import java.time.LocalDateTime;
// import java.time.format.DateTimeFormatter;
// import java.time.LocalDate;
// import java.time.temporal.ChronoUnit;

// public class Save {
//     public static void main(String[] args) {

//         boolean weitermachen = true;
//         while (weitermachen) {

//             try {
//                 String LTCEUR = "LTCEUR";
//                 String BNBEUR = "BNBEUR";
//                 String EURO = "EUR";
//                 String BNB = "BNB";

//                 int Grid = 7;
//                 int count = 0;

//                 boolean FirstRound = true;

//                 double BuyAmaunt;
//                 double p = 1.0; // +% mit wieviel die Position verkauft wird

//                 // String Start = "01.01.2024";
//                 double SetFeePercentFromBinance = 0.08; // 0.1 = 0.1%

//                 // ----------------------------------------------------------------------------------------------------------------------

//                 ATHCodeUnit ATHService = new ATHCodeUnit();

//                 // ----------------------------------------------------------------------------------------------------------------------

//                 // -> Set New Key 09/2024
//                 String key = "GunwtQg5ADNRyo2TPVvOZRoGKyZlNoDCg9uDlaPO7seL3W2J195bwuHZnAEZjoaA";
//                 String secret = "mZN0M5mvnitVYJzbhEDlYxahENyHXeT4ZH9R1fAzxNItqUQzslKfKR7RuUQiiNYA";

//                 BinanceApiClientFactory factory = BinanceApiClientFactory.newInstance(key, secret);
//                 BinanceApiRestClient client = factory.newRestClient();

//                 final String ATH = "jdbc:sqlite:C:/TradingBot/SQLiteStudio/Datenbanken/LTC_EUR/ATH_LTCEUR.db";
//                 final String POS = "jdbc:sqlite:C:/TradingBot/SQLiteStudio/Datenbanken/LTC_EUR/POS_LTCEUR.db";
//                 final String HIST = "jdbc:sqlite:C:/TradingBot/SQLiteStudio/Datenbanken/LTC_EUR/POS_LTCEUR_HIST.db";

//                 List<Long> BuyOrderIdList = new ArrayList<Long>();
//                 List<Double> Ath_List = new ArrayList<Double>();
//                 List<String> GetRecordFromDataBase_POS = new ArrayList<String>();
//                 List<String> GetRecordFromDataBase_HIST = new ArrayList<String>();
//                 List<Double> LivePrice = new ArrayList<Double>();
//                 List<Double> LivePrice007 = new ArrayList<Double>();
//                 List<Double> BNBEURLiveTicker = new ArrayList<Double>();
//                 List<Double> GetSumQuantity = new ArrayList<Double>();
//                 List<Double> GetSumOriginalBuyPrice = new ArrayList<Double>();
//                 List<Double> GetSumBuyAmount = new ArrayList<Double>();
//                 List<Double> TempPrice = new ArrayList<Double>();

//                 // -----------------------------------------------------------------------------------------------------------------------------------------------------

//                 boolean weiter = true;
//                 while (weiter) {

//                     Sleep_0_5_second();

//                     LTCEUR_LiveTickerPrice(LTCEUR, client, LivePrice);
//                     GetLivePrice_007(LTCEUR, client, LivePrice007);

//                     // -------------------------------------------------------------------------------------------------------------------------------

//                     // Account account = client.getAccount();
//                     // Verwende AccountMgtCodeunit.getBalance mit dem Account-Objekt
//                     // System.out.println(AccountMgtCodeunit.getBalance(EURO, client.getAccount(),
//                     // 2));

//                     // -------------------------------------------------------------------------------------------------------------------------------

//                     if (count == 1200 || FirstRound) {

//                         // -------------------------------------------------------------------------------------------------------------------------------

//                         // Beispielhafte URL zur Datenbank
//                         // String dbURL = ATH;
//                         // // Beispielhafte BuyOrderId
//                         // int buyOrderId = 123;
//                         // // Erstelle ein Objekt von DatabaseHelper und rufe die deleteFromPOS-Methode
//                         // auf
//                         // SQL_MgtCodeunit dbHelper = new SQL_MgtCodeunit();
//                         // dbHelper.deleteFromPOS(dbURL, buyOrderId);

//                         // -------------------------------------------------------------------------------------------------------------------------------

//                         CompareBalanceInSQLWithBinanceBalance(ATH, EURO, client);

//                         // --- CalC the rest of the Money ???
//                         // Calculate_Fee_Tax_Win(CoinTracking, Energiekosten, client, HIST, EURO);

//                         // System.out.println(GetEUR_Account_Balance(EURO, client.getAccount()));

//                         // --- Durchschnitt Profit berechnen
//                         // AverageProfitCalcOfHistDataBase(HIST);

//                         // GetRecordFrom_POS_DataBase(POS, GetRecordFromDataBase_POS);

//                         // --- Update New Sell Price After One Day
//                         UpdateSellPriceAfterOneDay(POS, GetRecordFromDataBase_POS);

//                         // --- Check BNB Balance
//                         CheckBNB_Balance(BNBEUR, BNB, client, BNBEURLiveTicker);

//                         // UpdateGewinnAfterTax(HIST, Record);

//                         // GetRecordFrom_Hist_DataBase(HIST, GetRecordFromDataBase_HIST);
//                         // System.out.println("SellOrder in HIST: " +
//                         // GetRecordFromDataBase_HIST.size());

//                         GetSumQuantity(POS, GetSumQuantity);
//                         GetSumOriginalBuyPrice(POS, GetSumOriginalBuyPrice);
//                         GetSumBuyAmount(POS, GetSumBuyAmount);

//                         count = 0;
//                         FirstRound = false;

//                     }
//                     count++;

//                     ATHService.CheckForNewAllTimeHigh(ATH, Ath_List, LivePrice);

//                     double Ath = ATHService.GetAllTimeHigh(ATH);
//                     double unten = GetLastPriceFromPOS(POS, LivePrice);
//                     double BuyPrice;

//                     int Count = 0;
//                     boolean BuyOrderCalc = true;

//                     while (BuyOrderCalc) {

//                         Ath = Ath - ((Ath / 100) / Grid);
//                         BuyPrice = round.two(Ath);

//                         if (LivePrice.get(0) >= BuyPrice) {
//                             Count++;
//                             if (Count >= 3) {
//                                 BuyOrderCalc = false;
//                             }
//                         }

//                         if ((LivePrice.get(0) >= BuyPrice) && (unten > BuyPrice) && (BuyOrderCalc)) {
//                             System.out.println("Setze mal eine Order bei: " + BuyPrice);

//                             // ---------------------------------------------------------------------------------------------

//                             int Hist = GetDataBaseHistCount(HIST) - 2500;
//                             double HistPrice = 5.5;
//                             int x = 0;

//                             while (x < Hist) {
//                                 x = x + 1;
//                                 HistPrice = HistPrice + RoundFIVE(HistPrice * (1.7 / 1000));
//                                 TempPrice.add(RoundTWO(HistPrice));
//                             }
//                             // System.out.println(TempPrice.get(TempPrice.size() - 1));
//                             double ExpoPrice = TempPrice.get(TempPrice.size() - 1);

//                             // ------------------------------------------------------------------------------------------------

//                             // GetBuyAmountFromDataBase(ATH);
//                             // GetDataBaseCount(POS);
//                             // GetValue(ATH);

//                             // ----------------------------------------------------------
//                             // try {
//                             // System.out.println("AbsolutBuyAmount Try Catch: " +
//                             // AbsolutBuyAmountPyramid(EURO, Grid,
//                             // LastPossiblePrice, client, ATH, POS, LivePrice, BuyPrice));
//                             // } catch (Exception e) {
//                             // System.err.println(
//                             // "Fehler beim Berechnen des AbsolutBuyAmount -> 6.0€ : " + e.getMessage());
//                             // Sleep_60_second();
//                             // }

//                             // System.out.println("AbsolutBuyAmount funtion outside: " +
//                             // AbsolutBuyAmountPyramid(EURO, Grid, LastPossiblePrice, client, ATH, POS,
//                             // LivePrice, BuyPrice));

//                             BuyAmaunt = ExpoPrice; // FBA;
//                             if (BuyAmaunt > 15.0) {
//                                 BuyAmaunt = 15.0;
//                                 System.out.println(
//                                         "Price > 15 !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!! €");
//                             }

//                             // if (BuyAmaunt > GetEURBalanceSQL(ATH)) {
//                             // CompareBalanceInSQLWithBinanceBalance(ATH, EURO, client.getAccount()); //
//                             // TODO es gibt einen Unterschied zwischen getAccount und getAssetFree - es
//                             // fehlen 5 Euro wo sind die?
//                             // if (GetEURBalanceSQL(ATH) < 5.0) {
//                             // // System.out.println("Kein Geld mehr vorhanden!");
//                             // // Sleep_60_second();
//                             // // BuyOrderCalc = false;
//                             // } else {
//                             // BuyAmaunt = 5.5;
//                             // }
//                             // }

//                             double Qty = round.three(BuyAmaunt / LivePrice.get(0));
//                             String Quantity = Double.toString(Qty);
//                             String buyprice = String.valueOf(BuyPrice);
//                             System.out.println("Quantity: " + Quantity + " LTC");

//                             try {
//                                 NewOrderResponse newOrderResponse = client
//                                         .newOrder(limitBuy(LTCEUR, TimeInForce.GTC, Quantity, buyprice));

//                                 Connection con = DriverManager.getConnection(POS);
//                                 Statement insert = con.createStatement();
//                                 String SQL = "INSERT INTO POS (BuyOrderId, OrderPrice, Status) VALUES ("
//                                         + newOrderResponse.getOrderId() + "," + newOrderResponse.getPrice() + "," + 0
//                                         + ")";

//                                 insert.execute(SQL);
//                                 con.close();
//                                 insert.close();
//                                 System.out.println("NEW_POSITION in DataBase: " + buyprice + " EUR");
//                                 System.out.println("BUY AMOUNT bei..........: " + BuyAmaunt + " EUR");
//                                 System.out.println("Quantity bei............: " + Quantity + " LTC");
//                                 break;

//                             } catch (BinanceApiException ex) {
//                                 String FehlerMessage = "Fehler beim Kauf Vorhanden kein Geld zur Verfügung";
//                                 System.out.println(FehlerMessage);
//                                 System.out.println(
//                                         "Aktuelles Datum und Uhrzeit: " + TimeMgtCodeunit.getDateTimeWith_HHmmss());

//                                 Sleep_60_second();
//                                 BuyOrderCalc = false;

//                             } catch (SQLException err) {
//                                 System.out.println(err.getMessage());
//                             } catch (IndexOutOfBoundsException e) {
//                                 System.out.println(e);
//                             }
//                         }
//                     }
//                     // #endregion

//                     // ----------------------------------------------------------------------------------------------------------------------------------------------------------------------
//                     // #region to Cancel - FILLED

//                     GetBuyOrderWhereStatusZero(POS, BuyOrderIdList);

//                     for (Long BuyOrderId : BuyOrderIdList) {

//                         try {
//                             Order order = client.getOrderStatus(new OrderStatusRequest(LTCEUR, BuyOrderId));
//                             Double orderPrice = round.two(Double.parseDouble(order.getPrice()));

//                             if (((order.getSide().compareTo(OrderSide.BUY) == 0)
//                                     && (order.getStatus().compareTo(OrderStatus.NEW) == 0))) {

//                                 double CancelPrice = Ath_List.get(0);
//                                 double LiveKurs = LivePrice.get(0);
//                                 int i = 0;

//                                 boolean calc = true;
//                                 while (calc) {
//                                     CancelPrice = CancelPrice - ((CancelPrice / 100) / Grid);
//                                     CancelPrice = Math.round(100.0 * CancelPrice) / 100.0;

//                                     if (LiveKurs > CancelPrice) {
//                                         i++;
//                                         if (i > 5) {
//                                             calc = false;
//                                         }
//                                         if (i == 3) {
//                                             if (CancelPrice > orderPrice) {
//                                                 client.cancelOrder(new CancelOrderRequest(LTCEUR, BuyOrderId));
//                                                 System.out.println("CANCELD ORDER: " + orderPrice);

//                                                 // System.out.println(GridTest);

//                                                 try {
//                                                     Connection con = DriverManager.getConnection(POS);
//                                                     Statement delete = con.createStatement();
//                                                     String SQL = "DELETE FROM POS WHERE BuyOrderId = " + BuyOrderId
//                                                             + ";";

//                                                     delete.execute(SQL);
//                                                     con.close();
//                                                     delete.close();

//                                                 } catch (SQLException err) {
//                                                     System.out.println(err.getMessage());
//                                                 }
//                                                 calc = false;
//                                             }
//                                         }
//                                     }
//                                 }
//                             }
//                             // -----------------------------------------------------------------------------------------------------------------------------------------------------------------------
//                             // // #region --- BUY FILLED --- to Status 1

//                             if (((order.getSide().compareTo(OrderSide.BUY) == 0)
//                                     && (order.getStatus().compareTo(OrderStatus.FILLED) == 0))) {

//                                 BuyPrice = round.two(Double.valueOf(order.getPrice()));
//                                 String BuyDateTime = TimeMgtCodeunit.getDateTimeWith_HHmm();
//                                 double Quantity = round.three(Double.valueOf(order.getOrigQty()));
//                                 double BuyAmount = round.five(BuyPrice * Quantity);

//                                 try {
//                                     Connection con_update = DriverManager.getConnection(POS);
//                                     Statement update = con_update.createStatement();
//                                     String SQL_update = "UPDATE POS SET BuyPrice = " + BuyPrice + ", OrigPrice = "
//                                             + BuyPrice + ", Qty = " + Quantity + ", Währung = '" + LTCEUR
//                                             + "', BuyAmount = " + BuyAmount + ", Differenz = " + 0 + ", Status = " + 1
//                                             + ", BuyTime = '" + BuyDateTime + "' WHERE BuyOrderId = " + BuyOrderId
//                                             + ";";

//                                     update.executeUpdate(SQL_update);
//                                     con_update.close();
//                                     update.close();
//                                     System.out.println("");
//                                     System.out.println("BUY FILLED - Vollzogen: " + BuyPrice);

//                                 } catch (SQLException err) {
//                                     System.out.println(err.getMessage());
//                                 }

//                                 try {
//                                     Connection con_insert_HIST = DriverManager.getConnection(HIST);
//                                     Statement insert_HIST = con_insert_HIST.createStatement();
//                                     String SQL = "INSERT INTO HIST (Währung, BuyOrderId, BuyPrice, Quantity, BuyAmount, BuyTime) VALUES ('"
//                                             + LTCEUR + "', " + BuyOrderId + ", " + BuyPrice + ", " + Quantity
//                                             + ", " + BuyAmount + ", '" + BuyDateTime + "')";

//                                     insert_HIST.execute(SQL);
//                                     con_insert_HIST.close();
//                                     insert_HIST.close();
//                                     System.out.println("Part in Hist Saved");

//                                 } catch (SQLException err) {
//                                     System.out.println(err.getMessage());
//                                 }
//                             }
//                             // --------------------------------------------------------------------------------------------------------------------------------------------------------------------
//                             // #region Cancel der Order von Außerhalb

//                             CancelOrderFromOutside(POS, order);

//                         } catch (BinanceApiException e) {
//                             System.out.println("Fehler beim Abrufen des Binance-API-Service: " +
//                                     e.getMessage());
//                         }
//                     }

//                     GetRecordFrom_POS_DataBase(POS, GetRecordFromDataBase_POS);

//                     for (String dataRecord : GetRecordFromDataBase_POS) {
//                         String[] parts = dataRecord.split(", ");

//                         String BuyOrderId = parts[0];
//                         String Quantity_String = parts[2];
//                         String Buy_Amount_String = parts[3];
//                         String BuyPrice_String = parts[4];
//                         String OriginalBuyPrice_String = parts[5];
//                         String DiffDataBase = parts[6];

//                         double BuyPrice_Double = round.two(Double.valueOf(BuyPrice_String));                       
//                         double BuyAmount_Double = round.two(Double.valueOf(Buy_Amount_String)); 
//                         double Original_BuyPrice = round.two(Double.valueOf(OriginalBuyPrice_String));  
//                         int DifferenzOfDays = Integer.valueOf(DiffDataBase);

//                         if (LivePrice007.get(0) >= ((BuyPrice_Double / 100) * (100 + p))) {
//                             System.out.println("es soll verkauft werden!");
                           
//                             String SellDateTime = TimeMgtCodeunit.getDateTimeWith_HHmm();

//                             try {
//                                 NewOrderResponse newOrderResponse = client
//                                         .newOrder(marketSell(LTCEUR, Quantity_String));

//                                 Connection con_update_POS = DriverManager.getConnection(POS);
//                                 Statement update_POS = con_update_POS.createStatement();
//                                 String Update_SQL = "UPDATE POS SET Status = " + 2
//                                         + " WHERE BuyOrderId = "
//                                         + BuyOrderId + ";";

//                                 update_POS.executeUpdate(Update_SQL);
//                                 con_update_POS.close();
//                                 update_POS.close();

//                                 double ProfitinPercent = GetProfit(LivePrice, Original_BuyPrice);
//                                 double SellPrice = getSellPrice(LivePrice);
//                                 double Taxe = GetTaxeOfTheSellOrder(BuyAmount_Double, ProfitinPercent);
//                                 double Fee = CalculateFeeForBuyAndSellTrade(BuyAmount_Double, SetFeePercentFromBinance);
//                                 double RevenuePerTrade = getRevenuePerTrade(BuyAmount_Double, ProfitinPercent);
//                                 double GewinnAfterTaxAndFee = getGewinnAfterTaxAndFee(Taxe, Fee, RevenuePerTrade);

//                                 Connection con_update_HIST = DriverManager.getConnection(HIST);
//                                 Statement update_HIST = con_update_HIST.createStatement();
//                                 String SQL = "UPDATE HIST SET SellPrice = " + SellPrice + ", Profit = "
//                                         + ProfitinPercent + ", Differenz = " + DifferenzOfDays + ", Gewinn = "
//                                         + RevenuePerTrade + ", Tax = " + Taxe
//                                         + ", GewinnAfterTax = " + GewinnAfterTaxAndFee + ", SellOrderId = "
//                                         + newOrderResponse.getOrderId() + ", Fee = " + Fee + ", SellTime = '" +
//                                         SellDateTime + "' WHERE BuyOrderId = "
//                                         + BuyOrderId
//                                         + ";";

//                                 update_HIST.execute(SQL);
//                                 con_update_HIST.close();
//                                 update_HIST.close();
//                                 System.out.println("Speichern in HIST!");

//                                 Connection con_deleteupdate_POS = DriverManager.getConnection(POS);
//                                 Statement delete_POS = con_deleteupdate_POS.createStatement();
//                                 String delete_SQL = "DELETE FROM POS WHERE BuyOrderId = " + BuyOrderId + ";";

//                                 delete_POS.executeUpdate(delete_SQL);
//                                 con_deleteupdate_POS.close();
//                                 delete_POS.close();
//                                 break;

//                             } catch (BinanceApiException ex) {
//                                 String FehlerMessage = "Fehler beim Verkauf: Keine Menge für den Verkauf Vorhanden!";
//                                 System.out.println(FehlerMessage);

//                                 try {
//                                     Thread.sleep(10000);
//                                     // Das geht hier nicht weil es keiner schleife gibt die zum beenden da ist.
//                                     // aus einer Forschelife soll mann mit einem break rausgehen

//                                     BuyOrderCalc = false;
//                                 } catch (InterruptedException e) {
//                                     e.printStackTrace();
//                                 }

//                             } catch (SQLException err) {
//                                 String FehlerMessage = "Fehler beim schreiben in die Datenbank!";
//                                 System.out.println(FehlerMessage);

//                                 System.out.println("Ich breche das jetzt mal ab " + err.getMessage());
//                                 System.exit(1);
//                                 break;
//                             }
//                         }
//                     }
//                 }
//             } catch (IndexOutOfBoundsException e) {
//                 String FehlerMessage = "Fehler: Index out of bounds! Der Fehler liegt in einem leerem Array irgendwo in dem Code!";
//                 System.err.println(FehlerMessage);
//                 continue;
//             }
//         }
//     }

//     private static double AbsolutBuyAmountPyramid(String EURO, int Grid, Double LastPossiblePrice,
//             BinanceApiRestClient client,
//             final String ATH, final String POS, List<Double> LivePrice, double CalcBuyPrice) {
//         double AbsolutBuyAmount = Double.MAX_VALUE;
//         double ValuePart = 0.0;
//         double BuyPrice = GetAllTimeHIGH(ATH);
//         double unten = GetLastPriceFromPOS(POS, LivePrice);
//         double Value = GetValue(ATH);
//         double SUM = GetBuyAmountFromDataBase(ATH);
//         double BA = SUM;
//         boolean CalcBayAmount = true;
//         boolean CalcValuePart = true;
//         double SQLSUM = GetEURBalanceSQL(ATH);
//         ValuePart = GetValue(ATH);

//         CompareBalanceInSQLWithBinanceBalance(ATH, EURO, client);

//         while (CalcBayAmount) {
//             while (CalcValuePart) {
//                 BuyPrice -= ((BuyPrice / 100) / Grid);

//                 if (((LivePrice.get(0) >= BuyPrice)) && (unten > CalcBuyPrice)) { // bei der 2ten Klammer noch mal
//                                                                                   // schauen
//                     // Counter++;
//                     // System.out
//                     // .println("No.:" + Counter + " - " + "BP:" + RoundTWO(BuyPrice) + " - "
//                     // + "BA:" + RoundTWO(BA + Value) + " - " + "SUM:" + RoundTWO(SUM));

//                     if (AbsolutBuyAmount > (BA + Value)) {
//                         AbsolutBuyAmount = (BA + Value);
//                     }

//                     SUM += (BA + Value); // TODO
//                     Value = Value + ValuePart;

//                     if (LastPossiblePrice > BuyPrice) {
//                         CalcValuePart = false;
//                     }
//                 }
//             }

//             if ((SUM - BA) < SQLSUM) {
//                 if ((GetEURBalanceSQL(ATH)) == (get_EUR_Balance(EURO, client.getAccount()))) {

//                     ValuePart += GetValue(ATH);
//                     Value = ValuePart;
//                     CalcBayAmount = true;
//                     CalcValuePart = true;
//                     BuyPrice = GetAllTimeHIGH(ATH);
//                     SUM = GetBuyAmountFromDataBase(ATH);
//                     BA = GetBuyAmountFromDataBase(ATH);
//                     System.out.println("Value: " + ValuePart);
//                 } else {
//                     CalcBayAmount = false;
//                     ValuePart = Value;
//                     AbsolutBuyAmount = 6.1;

//                     System.out.println("ValuePart: " + ValuePart);
//                 }
//             } else {
//                 System.out.println("Budget Überschritten");
//                 // System.out.println("ValuePart: " + ValuePart);
//                 // System.out.println("TestPries: " + TestPries);
//                 // System.out.println("AbsolutBuyAmount: " + AbsolutBuyAmount);
//                 CalcBayAmount = false;
//             }
//         }
//         return AbsolutBuyAmount;
//     }

//     private static double GetTaxe(final String HIST) {
//         double Taxe24 = 0.0;
//         try {
//             Connection con = DriverManager.getConnection(HIST);
//             Statement query = con.createStatement();
//             String SQL = "SELECT SUM(Tax) AS TotalAmount FROM HIST";
//             ResultSet rs = query.executeQuery(SQL);

//             if (rs.next()) {
//                 double SumTotalTax = round.two(rs.getDouble("TotalAmount"));
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
//                 double SumBuyAmount = round.two(rs.getDouble("BuyAmount"));
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
//                 double OrigBuyPrice = round.two(rs.getDouble("OriginalBuyPrice"));
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
//                 double Quantity_Double = round.five(rs.getDouble("Quantity"));
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

//     private static int GetDataBaseCount(final String POS) {
//         int i = 0;
//         try {
//             Connection con = DriverManager.getConnection(POS);
//             Statement query = con.createStatement();
//             String SQL = "SELECT COUNT(*) AS total FROM POS";
//             ResultSet rs = query.executeQuery(SQL);

//             if (rs.next()) {
//                 i = rs.getInt("total");
//             }
//             con.close();
//             query.close();
//             rs.close();

//         } catch (SQLException err) {
//             System.out.println(err.getMessage());
//         }
//         return i;
//     }

//     private static int GetDataBaseHistCount(final String SQL) {
//         int count = 0;
//         try (Connection con = DriverManager.getConnection(SQL);
//                 Statement query = con.createStatement();
//                 ResultSet rs = query.executeQuery("SELECT COUNT(*) AS total FROM HIST")) {

//             if (rs.next()) {
//                 count = rs.getInt("total");
//             }
//         } catch (SQLException err) {
//             System.out.println(err.getMessage());
//         }
//         return count;
//     }

//     private static double GetValue(final String ATH) {
//         double ValuePart = 0.0;
//         try {
//             Connection con = DriverManager.getConnection(ATH);
//             Statement query = con.createStatement();
//             String SQL = "SELECT ValuePart FROM ATH";
//             ResultSet rs = query.executeQuery(SQL);
//             ValuePart = rs.getDouble("ValuePart");
//             con.close();
//             query.close();

//         } catch (SQLException err) {
//             System.out.println(err.getMessage());
//         }
//         return ValuePart;
//     }

//     private static double GetBuyAmountFromDataBase(final String ATH) {
//         Double BuyAmount = 0.0;
//         try {
//             Connection con = DriverManager.getConnection(ATH);
//             Statement query = con.createStatement();
//             String SQL = "SELECT BuyAmount FROM ATH";
//             ResultSet rs = query.executeQuery(SQL);
//             BuyAmount = rs.getDouble("BuyAmount");
//             con.close();
//             query.close();
//         } catch (SQLException err) {
//             System.out.println(err.getMessage());
//         }
//         return BuyAmount;
//     }  

//     private static void CheckBNB_Balance(String BNBEUR, String BNB, BinanceApiRestClient client,
//             List<Double> BNBEURLiveTicker) {
//         try {
//             Account account = client.getAccount();
//             Double BNB_Balance = get_BNB_Balance(BNB, account);

//             BNBEURLiveTicker.clear();
//             TickerPrice tickerPrice = client.getPrice(BNBEUR);
//             Double liveKurs = round.two(Double.parseDouble(tickerPrice.getPrice()));
//             BNBEURLiveTicker.add(liveKurs);

//             double BNBBalanceINEUR = (BNBEURLiveTicker.get(0) * BNB_Balance);
//             BNBBalanceINEUR = Math.round(100.0 * BNBBalanceINEUR) / 100.0;

//             System.out.println("BNB Balance in EUR: " + BNBBalanceINEUR + " EUR");

//             if ((BNBEURLiveTicker.get(0) * BNB_Balance) < 1.0) {
//                 System.out.println("BNB unter 1 Euro -> Bitte Nachkaufen!");
//             }

//         } catch (BinanceApiException ex) {
//             System.out.println("Fehler beim BNB Balance oder beim Abrufen des Binance-API-Services: "
//                     + ex.getMessage());

//         } catch (Exception e) {
//             System.out.println("Es ist ein unbekannter Fehler aufgetreten: " + e.getMessage());
//         }
//     }

//     private static double get_BNB_Balance(String BNB, Account Account) {
//         double BNB_Balance = round.eight(Double.valueOf(Account.getAssetBalance(BNB).getFree()));
//         return BNB_Balance;
//     }

//     private static Double get_EUR_Balance(String EURO, Account Account) {
//         Double EUR_Account_Balance = round.two(Double.valueOf(Account.getAssetBalance(EURO).getFree()));
//         return EUR_Account_Balance;
//     }

//     private static void GetBuyOrderWhereStatusZero(final String POS, List<Long> OrderIdList) {
//         try {
//             OrderIdList.clear();
//             Connection con = DriverManager.getConnection(POS);
//             Statement query = con.createStatement();
//             String SQL = "SELECT BuyOrderId FROM POS WHERE Status = 0";
//             ResultSet rs = query.executeQuery(SQL);

//             while (rs.next()) {
//                 Long Topi = rs.getLong("BuyOrderId");
//                 OrderIdList.add(Topi);
//             }
//             con.close();
//             query.close();

//         } catch (SQLException err) {
//             System.out.println(err.getMessage());
//         }
//     }

//     private static void CancelOrderFromOutside(final String POS, Order order) {
//         if (((order.getSide().compareTo(OrderSide.BUY) == 0)
//                 && (order.getStatus().compareTo(OrderStatus.CANCELED) == 0))) {
//             double OrderPrice = round.two(Double.valueOf(order.getPrice()));
//             System.out.println("CANCEL FROM OUTSIDE: " + OrderPrice);

//             try {
//                 Connection con = DriverManager.getConnection(POS);
//                 Statement delete = con.createStatement();
//                 String SQL = "DELETE FROM POS WHERE BuyOrderId = " + order.getOrderId() +
//                         ";";

//                 delete.execute(SQL);
//                 con.close();
//                 delete.close();

//             } catch (SQLException err) {
//                 System.out.println(err.getMessage());
//             }
//         }
//     }

//     private static double getGewinnAfterTaxAndFee(double Taxe, double Fee, double RevenuePerTrade) {
//         double GewinnAfterTax = round.five(RevenuePerTrade - Taxe - Fee);
//         return GewinnAfterTax;
//     }

//     private static double getRevenuePerTrade(double BuyAmount_Double, double ProfitinPercent) {
//         double RevenuePerTrade = round.four((BuyAmount_Double / 100.0) * ProfitinPercent);
//         return RevenuePerTrade;
//     }

//     private static double getSellPrice(List<Double> LivePrice) {
//         double SellPrice = LivePrice.get(0);
//         return SellPrice;
//     }

//     private static double CalculateFeeForBuyAndSellTrade(double BuyAmount_Double, double FeePercent) {
//         double Fee = (((BuyAmount_Double / 100.0) * FeePercent) * 2);
//         return Fee;
//     }

//     private static void AverageProfitCalcOfHistDataBase(final String HIST) {
//         try {
//             Connection con = DriverManager.getConnection(HIST);
//             Statement query = con.createStatement();
//             String SQL = "SELECT AVG(Profit) AS AverageProfit FROM HIST WHERE Profit IS NOT NULL";
//             ResultSet rs = query.executeQuery(SQL);

//             if (rs.next()) {
//                 double AverageProfit = round.three(rs.getDouble("AverageProfit"));
//                 System.out.println("Durchschnittlicher Profit: " + AverageProfit + " %");
//             }

//             con.close();
//             query.close();

//         } catch (SQLException err) {
//             System.out.println(err.getMessage());
//         }
//     }

//     private static void Calculate_Fee_Tax_Win(double CoinTracking, double Energiekosten, BinanceApiRestClient client,
//             final String HIST, String EURO) {
//         try {
//             Double EUR_Account_Balance = get_EUR_Balance(EURO, client.getAccount()); // Double.valueOf(Account.getAssetBalance(Basis).getFree());
//             System.out.println("");

//             // Steuerausgaben pro Jahr berechnen
//             Connection con = DriverManager.getConnection(HIST);
//             Statement query = con.createStatement();
//             String SQL = "SELECT SUM(Tax) AS TotalAmount, SUM(Gewinn) AS SumGewinn, SUM(Fee) AS TotalFee, SUM(GewinnAfterTax) AS TotalGewinnAfter FROM HIST";
//             ResultSet rs = query.executeQuery(SQL);

//             if (rs.next()) {
//                 double SumTotalTax = rs.getDouble("TotalAmount");
//                 SumTotalTax = RoundFIVE(SumTotalTax);

//                 double GewinnAfterTax = rs.getDouble("TotalGewinnAfter");
//                 GewinnAfterTax = Math.round(100.0 * GewinnAfterTax) / 100.0;

//                 double Fee2024 = rs.getDouble("TotalFee");
//                 Fee2024 = Math.round(100.0 * Fee2024) / 100.0;

//                 double SumGewinn = rs.getDouble("SumGewinn");
//                 SumGewinn = Math.round(100.0 * SumGewinn) / 100.0;

//                 double Tax2024 = Math.round(100.0 * SumTotalTax) / 100.0;

//                 double AllPaymentPerAnno = -(((CoinTracking + Energiekosten) * 12) + SumTotalTax);
//                 AllPaymentPerAnno = Math.round(100.0 * AllPaymentPerAnno) / 100.0;

//                 double Summe = EUR_Account_Balance + AllPaymentPerAnno;
//                 Summe = Math.round(100.0 * Summe) / 100.0;

//                 double DiffWinAndTax = GewinnAfterTax - Tax2024;
//                 DiffWinAndTax = Math.round(100.0 * DiffWinAndTax) / 100.0;

//                 System.out.println("Free Account Balance..: " + (RoundTWO(EUR_Account_Balance - Tax2024) + " EUR"));
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

//     private static double GetTaxeOfTheSellOrder(double Buy_Amount, double Profit) {
//         double Taxe = (((Buy_Amount / 100) * Profit) * (42.0 / 100.0));
//         Taxe = RoundFIVE(Taxe);
//         return Taxe;
//     }

//     // //#region Hier muss nochmal der Updatepreis berechnet werden -> bitte
//     // berichtigen
//     private static void UpdateSellPriceAfterOneDay(final String POS, List<String> GetRecordFromDataBase) {
//         for (String DataRecord : GetRecordFromDataBase) {
//             String[] parts = DataRecord.split(", ");
//             String OrderId = parts[0];
//             String Original_Buy_Price = parts[5];
//             String DiffDataBase = parts[6];
//             String BuyDate = parts[7];

//             DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
//             LocalDateTime buyDateTime = LocalDateTime.parse(BuyDate, dateTimeFormatter);

//             int DiffDB = Integer.valueOf(DiffDataBase);
//             Double Orig_Buy_Price = Double.valueOf(Original_Buy_Price);

//             LocalDateTime currentDateTime = LocalDateTime.now();

//             long Hours = ChronoUnit.HOURS.between(buyDateTime, currentDateTime);
//             int DiffCalc = (int) (Hours / 24);

//             if (DiffCalc > DiffDB) {
//                 Double NewCalcBuyPrice = (Orig_Buy_Price / 100) * (100 + (DiffCalc * 0.1));
//                 NewCalcBuyPrice = RoundTWO(NewCalcBuyPrice);
//                 System.out.println("Neuer BuyPrice " + NewCalcBuyPrice);

//                 try {
//                     Connection con_update = DriverManager.getConnection(POS);
//                     Statement update = con_update.createStatement();
//                     String SQL_update = "UPDATE POS SET BuyPrice = " + NewCalcBuyPrice + ", OrderPrice = "
//                             + NewCalcBuyPrice + ", Differenz = "
//                             + DiffCalc + "  WHERE BuyOrderId = " + OrderId + ";";

//                     update.executeUpdate(SQL_update);
//                     con_update.close();
//                     update.close();

//                     System.out.println("Update BuyPrice in POS: " + NewCalcBuyPrice);

//                 } catch (SQLException err) {
//                     System.out.println(err.getMessage());
//                     System.out.println("Fehler beim Update des BuyPrice in POS");
//                     break;
//                 }
//             }
//         }
//     }

//     private static void GetRecordFrom_POS_DataBase(final String POS, List<String> GetDataRecord) {

//         try {
//             GetDataRecord.clear();
//             Connection con = DriverManager.getConnection(POS);
//             Statement query = con.createStatement();
//             String SQL = "SELECT BuyOrderId, OrderPrice, Qty, BuyAmount, BuyPrice, OrigPrice, Differenz, BuyTime, SellPrice FROM POS WHERE Status = 1";
//             ResultSet rs = query.executeQuery(SQL);

//             while (rs.next()) {
//                 String BuyOrderId = rs.getString("BuyOrderId"); // 0
//                 String OrderPrice = rs.getString("OrderPrice"); // 1
//                 String Quantity = rs.getString("Qty"); // 2
//                 String BuyAmount = rs.getString("BuyAmount"); // 3
//                 String BuyPrice = rs.getString("BuyPrice"); // 4
//                 String OrigPrice = rs.getString("OrigPrice"); // 5
//                 String Differenz = rs.getString("Differenz"); // 6
//                 String BuyTime = rs.getString("BuyTime"); // 7
//                 String SellPrice = rs.getString("SellPrice"); // 8

//                 String dataRecord = BuyOrderId + ", " + OrderPrice + ", " + Quantity + ", " + BuyAmount + ", " +
//                         BuyPrice + ", " + OrigPrice + ", " + Differenz + ", " + BuyTime + ", " + SellPrice;
//                 GetDataRecord.add(dataRecord);
//             }
//             con.close();
//             query.close();

//         } catch (SQLException err) {
//             System.out.println(err.getMessage());
//         }
//     }

//     public static void GetRecordFrom_Hist_DataBase(String HIST, List<String> GetDataRecord) {
//         try {
//             GetDataRecord.clear();
//             Connection con = DriverManager.getConnection(HIST);
//             Statement query = con.createStatement();
//             String SQL = "SELECT Währung, BuyOrderId, SellOrderId, Quantity, BuyAmount, BuyPrice, SellPrice, Profit, Differenz, BuyTime, SellTime, Fee, Tax, Gewinn, GewinnAfterTax FROM HIST WHERE SellOrderId IS NOT NULL;";
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

//     private static double GetProfit(List<Double> LiveTickerPrice, double Original_BuyPrice) {
//         double Profit = (((LiveTickerPrice.get(0) / Original_BuyPrice) * 100) - 100);
//         Profit = Math.round(10000.0 * Profit) / 10000.0;
//         return Profit;
//     }

//     private static int DifferenzDaysFromStartToToday(String Start) {
//         LocalDate today = LocalDate.now();
//         String TodayDate = today.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"));
//         DateTimeFormatter formi = DateTimeFormatter.ofPattern("dd.MM.yyyy");
//         LocalDate date1 = LocalDate.parse(TodayDate, formi);
//         LocalDate date2 = LocalDate.parse(Start, formi);
//         long diffInDays = ChronoUnit.DAYS.between(date2, date1);
//         int DiffFromStartToNow = (int) diffInDays;
//         return DiffFromStartToNow;
//     }

//     private static double GetEURBalanceSQL(final String ATH) {
//         double Balance = 0.0;
//         try {
//             Connection con = DriverManager.getConnection(ATH);
//             Statement query = con.createStatement();
//             String SQL = "SELECT Balance FROM ATH";
//             ResultSet rs = query.executeQuery(SQL);

//             if (rs.next()) {
//                 Balance = rs.getDouble("Balance");

//                 con.close();
//                 query.close();
//             }

//         } catch (SQLException err) {
//             System.out.println(err.getMessage());
//         } catch (IndexOutOfBoundsException e) {
//             System.out.println("Fehler beim Vergleichen des Preise: " + e.getMessage());
//         }
//         return RoundTWO(Balance);
//     }

//     private static void CompareBalanceInSQLWithBinanceBalance(final String ATH, String EURO,
//             BinanceApiRestClient client) {// TODO Nochmals Testen
//         try {
//             Connection con = DriverManager.getConnection(ATH);
//             Statement query = con.createStatement();
//             String SQL = "SELECT Balance FROM ATH";
//             ResultSet rs = query.executeQuery(SQL);

//             if (rs.next()) {
//                 double Balance = rs.getDouble("Balance");

//                 con.close();
//                 query.close();

//                 Double EurBalance = RoundFIVE(get_EUR_Balance(EURO, client.getAccount()));
//                 if (EurBalance != Balance) {
//                     Balance = get_EUR_Balance(EURO, client.getAccount());

//                     Connection con_ath = DriverManager.getConnection(ATH);
//                     Statement update = con_ath.createStatement();
//                     String SQL1 = "UPDATE ATH SET Balance =" + Balance;

//                     update.executeUpdate(SQL1);
//                     con_ath.close();
//                     update.close();
//                 }
//             }

//         } catch (SQLException err) {
//             // Fehler bei der Datenbankverbindung oder SQL-Operationen
//             System.out.println("Datenbankfehler: " + err.getMessage());
//         } catch (IndexOutOfBoundsException e) {
//             // Fehler beim Zugriff auf Listen oder Arrays
//             System.out.println("Fehler beim Vergleichen des Preises: " + e.getMessage());
//         } catch (BinanceApiException e) {
//             // Fehler bei der Verbindung zur Binance API
//             System.out.println("Binance API Fehler: " + e.getMessage());
//             // Hier kannst du festlegen, dass das Programm weiterhin läuft, z.B. durch
//             // Log-Nachrichten
//             System.out.println("Kein Internet oder Binance-Service nicht verfügbar. Das Programm läuft weiter.");
//         } catch (Exception e) {
//             // Allgemeiner Fehlerfänger für unerwartete Probleme
//             System.out.println("Ein unerwarteter Fehler ist aufgetreten: " + e.getMessage());
//         }
//     }

//     private static double GetAllTimeHIGH(final String ATH) {
//         double ATH_Value = 0.0;
//         try {
//             Connection con = DriverManager.getConnection(ATH);
//             Statement query = con.createStatement();
//             String SQL = "SELECT ATH FROM ATH";
//             ResultSet rs = query.executeQuery(SQL);

//             if (rs.next()) {
//                 ATH_Value = rs.getDouble("ATH");

//                 con.close();
//                 query.close();
//             }

//         } catch (SQLException err) {
//             System.out.println(err.getMessage());
//         } catch (IndexOutOfBoundsException e) {
//             System.out.println("Fehler beim Vergleichen des Preise: " + e.getMessage());
//         }
//         return RoundTWO(ATH_Value);
//     }

//     private static void Sleep_0_5_second() {
//         try {
//             Thread.sleep(500);
//         } catch (InterruptedException e) {
//             e.printStackTrace();
//         }
//     }

//     private static void Sleep_60_second() {
//         try {
//             Thread.sleep(60000);
//         } catch (InterruptedException e) {
//             e.printStackTrace();
//         }
//     }

//     private static void GetLivePrice_007(String WährungPaar, BinanceApiRestClient client, List<Double> LiveTicker007) {
//         try {
//             LiveTicker007.clear();
//             TickerPrice TickerPrice = client.getPrice(WährungPaar);
//             Double LiveKurs = Double.parseDouble(TickerPrice.getPrice());
//             LiveKurs = RoundTWO(LiveKurs - 0.07);
//             LiveTicker007.add(LiveKurs);
//             System.out.print(".");

//         } catch (BinanceApiException e) {
//             System.out.println("Fehler beim Abrufen des Binance-API-Service: " + e.getMessage());
//         }
//     }

//     private static void LTCEUR_LiveTickerPrice(String WährungPaar, BinanceApiRestClient client,
//             List<Double> LiveTicker) {
//         try {
//             LiveTicker.clear();
//             TickerPrice TickerPrice = client.getPrice(WährungPaar);
//             Double LiveKurs = round.two((Double.parseDouble(TickerPrice.getPrice())));
//             LiveTicker.add(LiveKurs);
//             System.out.print(".");

//         } catch (BinanceApiException e) {
//             System.out.println("Fehler beim Abrufen Live_TickerPrice des Binance-API-Service: " + e.getMessage());
//         }
//     }

//     private static double GetLastPriceFromPOS(final String POS, List<Double> LivePrice) {
//         double PriceMin = Double.MAX_VALUE;
//         boolean foundPriceInDB = false;

//         try (Connection con = DriverManager.getConnection(POS);
//                 Statement query = con.createStatement()) {
//             String SQL2 = "SELECT OrderPrice FROM POS WHERE Status IN (0, 1)";
//             ResultSet rs = query.executeQuery(SQL2);

//             while (rs.next()) {
//                 double currentPrice = rs.getDouble("OrderPrice");
//                 if (currentPrice < PriceMin) {
//                     PriceMin = currentPrice;
//                     foundPriceInDB = true;
//                 }
//             }
//         } catch (SQLException err) {
//             System.out.println("SQL-Fehler: " + err.getMessage());
//         }

//         if (!foundPriceInDB && !LivePrice.isEmpty()) { // TODO
//             PriceMin = LivePrice.get(0); // Fallback: LivePrice verwenden
//         }
//         return PriceMin;
//     }
// }
