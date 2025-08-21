// package com.binance.api.examples.xSaveReset;

// import com.binance.api.client.BinanceApiClientFactory;
// import com.binance.api.client.BinanceApiRestClient;
// import java.util.List;
// import com.binance.api.client.domain.account.Order;
// import com.binance.api.client.domain.market.TickerPrice;
// import java.lang.Math;
// import com.binance.api.client.domain.TimeInForce;
// import static com.binance.api.client.domain.account.NewOrder.limitBuy;
// import static com.binance.api.client.domain.account.NewOrder.marketSell;
// import com.binance.api.client.domain.OrderSide;
// import com.binance.api.client.domain.OrderStatus;
// import com.binance.api.client.domain.account.NewOrderResponse;
// import com.binance.api.client.exception.BinanceApiException;
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

// public class LTC_Light {
//     public static void main(String[] args) {

//         boolean weitermachen = true;
//         while (weitermachen) {

//             try {
//                 String LTCEUR = "LTCEUR";              

//                 int Grid = 7;                
//                 double BuyAmaunt;
//                 double p = 1.2;             

//                 // -> Set New Key 12.04.2024                
//                 String key = "xjAvjpW8o73VMo3AeVyXXnowbt2fdiF9ZKgBOCfmSittC9YZwhUuMzQ8FM05fiGN";
//                 String secret = "mP7TCYXaGM6kIJtpTQMTBc6NeB44mJ6XkodoxWodXgyUrhQnqH2enxGERqvmDBpc";

//                 BinanceApiClientFactory factory = BinanceApiClientFactory.newInstance(key, secret);
//                 BinanceApiRestClient client = factory.newRestClient();

//                 final String ATH = "jdbc:sqlite:C:/TradingBot/SQLiteStudio/Datenbanken/LTC_EUR/ATH_LTCEUR.db";
//                 final String POS = "jdbc:sqlite:C:/TradingBot/SQLiteStudio/Datenbanken/LTC_EUR/POS_LTCEUR.db";   
              
//                 List<Double> Price_min = new ArrayList<Double>();
//                 List<Long> BuyOrderIdList = new ArrayList<Long>();
//                 List<Double> ath_List = new ArrayList<Double>();
//                 List<String> GetRecordFromDataBase_POS = new ArrayList<String>();
//                 List<Double> LivePrice = new ArrayList<Double>();
//                 List<Double> BuyAmountValueDouble = new ArrayList<Double>();
//                 List<Integer> DataBaseCount = new ArrayList<Integer>();
//                 List<Double> ValuePart = new ArrayList<Double>();

//                 // -----------------------------------------------------------------------------------------------------------------------------------------------------

//                 boolean weiter = true;
//                 while (weiter) {

//                     Sleep_0_5_second();

//                     LTCEUR_LiveTickerPrice(LTCEUR, client, LivePrice);

//                     CheckOfaNewATHValue(ATH, ath_List, LivePrice);

//                     Get_MinMax_BuyPrice_Value_From_POS_DataBase(POS, Price_min, LivePrice);

//                     double Ath = ath_List.get(0);
//                     double unten = Price_min.get(0);
//                     double BuyPrice;

//                     int Count = 0;
//                     boolean BuyOrderCalc = true;

//                     while (BuyOrderCalc) {

//                         Ath = Ath - ((Ath / 100) / Grid);
//                         BuyPrice = RoundTWO(Ath);

//                         if (LivePrice.get(0) >= BuyPrice) {
//                             Count++;
//                             if (Count >= 3) {
//                                 BuyOrderCalc = false;
//                             }
//                         }

//                         if ((LivePrice.get(0) >= BuyPrice) && (unten > BuyPrice) && (BuyOrderCalc)) {

//                             System.out.println("Setze mal eine Order bei: " + BuyPrice);

//                             GetBuyAmountFromDataBase(ATH, BuyAmountValueDouble);
//                             GetDataBaseQuantity(POS, DataBaseCount);
//                             GetValueOfCentColumn(ATH, ValuePart);

//                             Double FBA = BuyAmountValueDouble.get(0) + (ValuePart.get(0) * DataBaseCount.get(0));
//                             FBA = RoundFIVE(FBA);

//                             BuyAmaunt = FBA;
//                             if (BuyAmaunt > 10.0) {
//                                 BuyAmaunt = 10.0;
//                             }

//                             double Qty = (BuyAmaunt / LivePrice.get(0));
//                             DecimalFormat df = new DecimalFormat("#.###");
//                             DecimalFormatSymbols symbols = df.getDecimalFormatSymbols();
//                             symbols.setDecimalSeparator('.');
//                             df.setDecimalFormatSymbols(symbols);
//                             df.setRoundingMode(RoundingMode.CEILING);
//                             String Quantity = df.format(Qty);
//                             String buyprice = String.valueOf(BuyPrice);

//                             try {
//                                 NewOrderResponse newOrderResponse = client
//                                         .newOrder(limitBuy(LTCEUR, TimeInForce.GTC, Quantity, buyprice));
//                                 // System.out.println("BUY_ORDER gesetzt bei...: " + BuyPrice);

//                                 // System.out.println(GridTest);

//                                 Connection con = DriverManager.getConnection(POS);
//                                 Statement insert = con.createStatement();
//                                 String SQL = "INSERT INTO POS (BuyOrderId, OrderPrice, Status) VALUES ("
//                                         + newOrderResponse.getOrderId() + "," 
//                                         + newOrderResponse.getPrice() + "," 
//                                         + 0
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

//                                 try {
//                                     Thread.sleep(3000);
//                                     BuyOrderCalc = false;
//                                 } catch (InterruptedException e) {
//                                     e.printStackTrace();
//                                 }

//                             } catch (SQLException err) {
//                                 System.out.println(err.getMessage());
//                             } catch (IndexOutOfBoundsException e) {
//                                 System.out.println(e);
//                             }
//                         }
//                     }

//                     // ----------------------------------------------------------------------------------------------------------------------------------------------------------------------
//                     // #region to Cancel - FILLED

//                     GetBuyOrderFromDataBaseWhereStatusEqualZero(POS, BuyOrderIdList);

//                     for (Long BuyOrderId : BuyOrderIdList) {

//                         try {
//                             Order order = client.getOrderStatus(new OrderStatusRequest(LTCEUR, BuyOrderId));
//                             Double orderPrice = Double.parseDouble(order.getPrice());
//                             orderPrice = RoundTWO(orderPrice);

//                             if (((order.getSide().compareTo(OrderSide.BUY) == 0)
//                                     && (order.getStatus().compareTo(OrderStatus.NEW) == 0))) {

//                                 double CalculateOrderPriceFromATH = ath_List.get(0);
//                                 double LiveKurs = LivePrice.get(0);
//                                 int i = 0;

//                                 boolean calc = true;
//                                 while (calc) {
//                                     CalculateOrderPriceFromATH = CalculateOrderPriceFromATH
//                                             - ((CalculateOrderPriceFromATH / 100) / Grid);
//                                     CalculateOrderPriceFromATH = Math.round(100.0 * CalculateOrderPriceFromATH) / 100.0;

//                                     if (LiveKurs > CalculateOrderPriceFromATH) {
//                                         i++;
//                                         if (i > 5) {
//                                             calc = false;
//                                         }
//                                         if (i == 3) {
//                                             if (CalculateOrderPriceFromATH > orderPrice) {
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

//                                 LocalDateTime now = LocalDateTime.now();
//                                 DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
//                                 String BuyDateTime = now.format(formatter);

//                                 double Quantity = Double.valueOf(order.getOrigQty());
//                                 Quantity = RoundTHREE(Quantity);
//                                 BuyPrice = Double.valueOf(order.getPrice());
//                                 BuyPrice = RoundTWO(BuyPrice);
//                                 double BuyAmount = (BuyPrice * Quantity);
//                                 BuyAmount = RoundFIVE(BuyAmount);

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
//                             }

//                             CancelOrderFromOutside(POS, order);

//                         } catch (BinanceApiException e) {
//                             System.out.println("Fehler beim Abrufen des Binance-API-Service: " +
//                                     e.getMessage());
//                         }
//                     }

//                     // ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------

//                     GetRecordFrom_POS_DataBase_Where_Status_Equal_One(POS, GetRecordFromDataBase_POS);

//                     for (String DataRecord : GetRecordFromDataBase_POS) {
//                         String[] parts = DataRecord.split(", ");

//                         String BuyOrderId = parts[0];
//                         String Quantity_String = parts[2];                       
//                         String BuyPrice_String = parts[4];                                 

//                         double BuyPrice_Double = Double.valueOf(BuyPrice_String);
//                         BuyPrice_Double = Math.round(100.0 * BuyPrice_Double) / 100.0;

//                         if (LivePrice.get(0) >= ((BuyPrice_Double / 100) * (100 + p))) {

//                             System.out.println("es soll verkauft werden!");

//                             try {
//                                 NewOrderResponse newOrderResponse = client.newOrder(marketSell(LTCEUR, Quantity_String));

//                                 System.out.println("Sell " + newOrderResponse.getClientOrderId());

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

//     private static void GetDataBaseQuantity(final String POS, List<Integer> DataBaseCount) {
//         try {
//             DataBaseCount.clear();
//             Connection con = DriverManager.getConnection(POS);
//             Statement query = con.createStatement();
//             String SQL = "SELECT COUNT(*) AS total FROM POS";
//             ResultSet rs = query.executeQuery(SQL);

//             // Überprüfe, ob es Ergebnisse gibt und erhalte die Anzahl der Datensätze
//             if (rs.next()) {
//                 int i = rs.getInt("total");
//                 DataBaseCount.add(i); // Füge die Anzahl der Datensätze zur Liste hinzu
//             }
//             con.close();
//             query.close();
//             rs.close();
//         } catch (SQLException err) {
//             System.out.println(err.getMessage());
//         }
//     }

//     private static void GetValueOfCentColumn(final String ATH, List<Double> ValuePart) {
//         try {
//             ValuePart.clear();
//             Connection con = DriverManager.getConnection(ATH);
//             Statement query = con.createStatement();
//             String SQL = "SELECT ValuePart FROM ATH";
//             ResultSet rs = query.executeQuery(SQL);

//             double BA = rs.getDouble("ValuePart");
//             ValuePart.add(BA);

//             con.close();
//             query.close();

//         } catch (SQLException err) {
//             System.out.println(err.getMessage());
//         }
//     }

//     private static void GetBuyAmountFromDataBase(final String ATH, List<Double> BuyAmountValueDouble) {
//         try {
//             BuyAmountValueDouble.clear();
//             Connection con = DriverManager.getConnection(ATH);
//             Statement query = con.createStatement();
//             String SQL = "SELECT BuyAmount FROM ATH";
//             ResultSet rs = query.executeQuery(SQL);

//             double BA = rs.getDouble("BuyAmount");
//             BuyAmountValueDouble.add(BA);

//             con.close();
//             query.close();

//         } catch (SQLException err) {
//             System.out.println(err.getMessage());
//         }
//     }

//     private static double RoundFIVE(double BuyAmount) {
//         return Math.round(100000.0 * BuyAmount) / 100000.0;
//     }

//     private static double RoundTHREE(double Quantity) {
//         return Math.round(1000.0 * Quantity) / 1000.0;
//     }

//     private static double RoundTWO(Double BuyPrice) {
//         return Math.round(100.0 * BuyPrice) / 100.0;
//     }

//     private static void GetBuyOrderFromDataBaseWhereStatusEqualZero(final String POS, List<Long> OrderIdList) {
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
//             double OrderPrice = Double.valueOf(order.getPrice());
//             OrderPrice = Math.round(100.0 * OrderPrice) / 100.0;
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
  
//     private static void GetRecordFrom_POS_DataBase_Where_Status_Equal_One(final String POS,
//             List<String> GetDataRecord) {

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

//     private static void CheckOfaNewATHValue(final String ATH, List<Double> ath_List, List<Double> BTC_Live) {
//         try {
//             ath_List.clear();
//             Connection con = DriverManager.getConnection(ATH);
//             Statement query = con.createStatement();
//             String SQL = "SELECT ATH FROM ATH";
//             ResultSet rs = query.executeQuery(SQL);

//             double ath = rs.getDouble("ATH");
//             ath_List.add(ath);

//             // System.out.println("ATH: " + ath);

//             con.close();
//             query.close();

//             if (BTC_Live.get(0) > ath) {
//                 ath = BTC_Live.get(0);

//                 ath_List.clear();
//                 ath_List.add(ath);

//                 System.out.println("New ATH");

//                 Connection con_ath = DriverManager.getConnection(ATH);
//                 Statement update = con_ath.createStatement();
//                 String SQL1 = "UPDATE ATH SET ath =" + ath + "";

//                 update.executeUpdate(SQL1);
//                 con_ath.close();
//                 update.close();
//             }

//         } catch (SQLException err) {
//             System.out.println(err.getMessage());
//         } catch (IndexOutOfBoundsException e) {
//             System.out.println("Fehler beim Zugriff auf die BTC-Live-Liste: Vernindung: " + e.getMessage());
//         }
//     }

//     private static void Sleep_0_5_second() {
//         try {
//             Thread.sleep(500);
//         } catch (InterruptedException e) {
//             e.printStackTrace();
//         }
//     }

//     private static void LTCEUR_LiveTickerPrice(String WährungPaar, BinanceApiRestClient client,
//             List<Double> LiveTicker) {
//         try {
//             LiveTicker.clear();
//             TickerPrice TickerPrice = client.getPrice(WährungPaar);
//             Double LiveKurs = Double.parseDouble(TickerPrice.getPrice());
//             LiveKurs = RoundTWO(LiveKurs);
            
//             LiveKurs = (LiveKurs - 0.07); //TODO
//             LiveKurs = RoundTWO(LiveKurs);
//             LiveTicker.add(LiveKurs);
//             System.out.print(".");
//             // System.out.println("LTC: " + LiveKurs + " EUR");

//         } catch (BinanceApiException e) {
//             System.out.println("Fehler beim Abrufen des Binance-API-Service: " + e.getMessage());
//         }
//     }

//     private static void Get_MinMax_BuyPrice_Value_From_POS_DataBase(final String POS, List<Double> Price_min, List<Double> LivePrice) {
//         double LowestPrice;    
//         try {
//             Price_min.clear();
            
//             Connection con = DriverManager.getConnection(POS);
//             Statement query = con.createStatement();
//             String SQL2 = "SELECT OrderPrice FROM POS WHERE Status IN (0, 1)";

//             // Speichere das Resultat in der ResultSet-Variable "rs"
//             ResultSet rs = query.executeQuery(SQL2);

//             // Iteriere über die ResultSet-Variable "rs"
//             while (rs.next()) {
//                 Double OrderPrice = rs.getDouble("OrderPrice");

//                 // Füge den Wert der Liste hinzu
//                 Price_min.add(OrderPrice);
//             }

//             // Wenn die Liste leer ist, füge den ersten Wert aus der Live-Liste hinzu
//             if (Price_min.isEmpty()) {
//                 Price_min.add(LivePrice.get(0));
//             }

//             // Berechne den Min- und Max-Wert der Liste
//             LowestPrice = Collections.min(Price_min);          

//             Price_min.clear();
//             Price_min.add(LowestPrice);          

//             con.close();
//             query.close();

//         } catch (SQLException err) {
//             System.out.println(err.getMessage());
//         } catch (IndexOutOfBoundsException e) {
//             System.out.println("Fehler beim Zugriff auf die BTC-Live-Liste: Vernindung: " + e.getMessage());
//         }
//     }
// }
