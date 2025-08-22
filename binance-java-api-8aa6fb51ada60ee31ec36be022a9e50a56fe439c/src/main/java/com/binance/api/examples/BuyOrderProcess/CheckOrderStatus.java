package com.binance.api.examples.BuyOrderProcess;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import com.binance.api.client.BinanceApiRestClient;
import com.binance.api.client.domain.OrderSide;
import com.binance.api.client.domain.OrderStatus;
import com.binance.api.client.domain.account.Order;
import com.binance.api.client.domain.account.request.CancelOrderRequest;
import com.binance.api.client.domain.account.request.OrderStatusRequest;
import com.binance.api.client.exception.BinanceApiException;
import com.binance.api.examples.HelperFunctions.Time;
import com.binance.api.examples.HelperFunctions.round;
import com.binance.api.examples.Indicator.Update;
import com.binance.api.examples.SQL_Database.ATHSQL;

public class CheckOrderStatus {

    public static void OrderStatus(String currency, int Grid, BinanceApiRestClient client, final String POS,
            final String HIST, List<Long> BuyOrderIdList, final String ATH,
            final String SET, List<Double> LivePrice) {

        for (Long buyOrderId : BuyOrderIdList) {

            try {
                Order order = client.getOrderStatus(new OrderStatusRequest(currency, buyOrderId));
                Double orderPrice = round.two(Double.parseDouble(order.getPrice()));

                if (((order.getSide().compareTo(OrderSide.BUY) == 0)
                        && (order.getStatus().compareTo(OrderStatus.NEW) == 0))) {

                    BUY_NEW(currency, Grid, client, POS, ATH, LivePrice, buyOrderId, orderPrice);
                }

                if (((order.getSide().compareTo(OrderSide.BUY) == 0)
                        && (order.getStatus().compareTo(OrderStatus.PARTIALLY_FILLED) == 0))) {

                    Test_PARTIALLY_FILLED(currency, Grid, client, POS, ATH, HIST, LivePrice, buyOrderId, orderPrice,
                            order);
                }

                if (((order.getSide().compareTo(OrderSide.BUY) == 0)
                        && (order.getStatus().compareTo(OrderStatus.FILLED) == 0))) {

                    BUY_FILLED(currency, ATH, POS, HIST, SET, client, buyOrderId, order);
                }

                CancelOrderFromOutside(POS, order);

            } catch (BinanceApiException e) {
                System.out.println("Fehler beim Abrufen des Binance-API-Service: " + e.getMessage());

                if (e.getMessage().contains("timeout") || e.getMessage().contains("SocketTimeoutException")) {
                    System.out.println("Netzwerk-Timeout erkannt. Warte 5 Sekunden und versuche es später erneut...");
                    try {
                        Thread.sleep(5000); // 5 Sekunden warten
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    }
                }

                continue;
            } catch (Exception e) {
                System.out.println("Unerwarteter Fehler beim Prüfen der Order " + buyOrderId + ": " + e.getMessage());
                e.printStackTrace();
                // Bei unerwarteten Fehlern auch weitermachen
                continue;
            }
        }
    }

    private static void CancelOrderFromOutside(final String POS, Order order) {
        if (((order.getSide().compareTo(OrderSide.BUY) == 0)
                && (order.getStatus().compareTo(OrderStatus.CANCELED) == 0))) {

            double OrderPrice = round.two(Double.valueOf(order.getPrice()));
            System.out.println("CANCEL FROM OUTSIDE: " + OrderPrice);

            try (Connection con = DriverManager.getConnection(POS);
                    Statement delete = con.createStatement()) {
                delete.execute("DELETE FROM POS WHERE BuyOrderId = " + order.getOrderId());
            } catch (SQLException err) {
                System.out.println(err.getMessage());
            }
        }
    }

    private static void BUY_FILLED(String currencyPair, final String ATH, final String POS, final String HIST,
            final String SET, BinanceApiRestClient client, Long BuyOrderId,
            Order order) {

        double BuyPrice = getBuyPrice(order);
        String BuyDate = Time.getCurrentDate();
        String BuyTime = Time.getCurrentTime_HHmmss();
        double Quantity = round.Quantity((Double.valueOf(order.getExecutedQty())), currencyPair);
        double BuyAmount = round.five(BuyPrice * Quantity);

        Update.NewCounterPosition(SET);

        try (Connection con_update = DriverManager.getConnection(POS);
                Statement update = con_update.createStatement()) {

            String SQL_update = "UPDATE POS SET "
                    + "BuyPrice = " + BuyPrice + ", "
                    + "Qty = " + round.withPoint(Quantity) + ", "
                    + "OrderPrice = " + BuyPrice + ", "
                    + "Währung = '" + currencyPair + "', "
                    + "BuyAmount = " + BuyAmount + ", "
                    + "Status = 5, "
                    + "BuyTime = '" + BuyTime + "', "
                    + "BuyDate = '" + BuyDate + "' "
                    + "WHERE BuyOrderId = " + BuyOrderId;

            update.executeUpdate(SQL_update);
            System.out.println("BUY FILLED - Vollzogen: " + BuyPrice);

        } catch (SQLException err) {
            System.out.println("Fehler beim Aktualisieren der Daten: " + err.getMessage());
        }

        try (Connection con_insert_HIST = DriverManager.getConnection(HIST);
                Statement insert_HIST = con_insert_HIST.createStatement()) {

            String SQL = "INSERT INTO HIST (Währung, BuyOrderId, BuyPrice, Quantity, BuyAmount, BuyDate, BuyTime) VALUES ('"
                    + currencyPair + "', "
                    + BuyOrderId + ", "
                    + BuyPrice + ", "
                    + round.withPoint(Quantity) + ", "
                    + BuyAmount + ", '"
                    + BuyDate + "', '"
                    + BuyTime + "')";

            insert_HIST.execute(SQL);
            System.out.println("Part in Hist Saved");

        } catch (SQLException err) {
            System.out.println("Fehler beim Einfügen in die HIST-Tabelle: " + err.getMessage());
        }
    }

    // private static void PARTIALLY_FILLED(String currencyPair, final String POS,
    // final String HIST, Long BuyOrderId,
    // Order order, BinanceApiRestClient client) {

    // // ----------------- 10 Sekunden nach Kauf -----------------

    // Long currentTime = Long.valueOf(Time.getCurrentTimeInMilliseconds());
    // Long buyTime = order.getTime();

    // if (currentTime >= buyTime + 10000) { // nach 10 Sekunden die Position
    // schließen
    // System.out.println("10 Sekunden vergangen, Position schließen! " +
    // currentTime + " - " + buyTime);

    // int Status;
    // double BuyPrice = getBuyPrice(order);
    // String BuyDate = Time.getCurrentDate();
    // String BuyTime = Time.getCurrentTime_HHmmss();
    // double Quantity = round.Quantity((Double.valueOf(order.getExecutedQty())),
    // currencyPair);
    // double partially_BuyAmount = round.five(BuyPrice * Quantity);

    // if (partially_BuyAmount >= 6.0) {
    // Status = 1;
    // } else {
    // Status = 3;
    // }

    // try (Connection con_update = DriverManager.getConnection(POS);
    // Statement update = con_update.createStatement()) {

    // String SQL_update = "UPDATE POS SET "
    // + "BuyPrice = " + BuyPrice + ", "
    // + "Qty = " + Quantity + ", "
    // + "Währung = '" + currencyPair + "', "
    // + "BuyAmount = " + partially_BuyAmount + ", "
    // + "Status = " + Status + ", "
    // + "BuyTime = '" + BuyTime + "', "
    // + "BuyDate = '" + BuyDate + "' "
    // + "WHERE BuyOrderId = " + BuyOrderId;

    // update.executeUpdate(SQL_update);

    // System.out.println("BUY FILLED - Vollzogen: " + BuyPrice);

    // } catch (SQLException err) {
    // System.out.println("Fehler beim Aktualisieren der Daten: " +
    // err.getMessage());
    // }

    // try (Connection con_insert_HIST = DriverManager.getConnection(HIST);
    // Statement insert_HIST = con_insert_HIST.createStatement()) {

    // String SQL = "INSERT INTO HIST (Währung, BuyOrderId, BuyPrice, Quantity,
    // BuyAmount, BuyDate, BuyTime) VALUES ('"
    // + currencyPair + "', "
    // + BuyOrderId + ", "
    // + BuyPrice + ", "
    // + Quantity + ", "
    // + partially_BuyAmount + ", '"
    // + BuyDate + "', '"
    // + BuyTime + "')";

    // insert_HIST.execute(SQL);
    // client.cancelOrder(new CancelOrderRequest(currencyPair, BuyOrderId));
    // System.out.println("Part in Hist Saved");

    // } catch (SQLException err) {
    // System.out.println("Fehler beim Einfügen in die HIST-Tabelle: " +
    // err.getMessage());
    // }
    // }
    // }

    private static double getBuyPrice(Order order) {
        double BuyPrice = round.five(Double.valueOf(order.getPrice()));
        return BuyPrice;
    }

    private static void BUY_NEW(String currencyPair, int Grid, BinanceApiRestClient client, final String POS,
            final String ATH, List<Double> LivePrice, Long BuyOrderId, Double orderPrice) {

        try {
            double CancelPrice = ATHSQL.getAllTimeHigh(currencyPair, ATH);
            double LiveKurs = LivePrice.get(0);
            int i = 0;

            boolean calc = true;
            while (calc) {
                CancelPrice = CancelPrice - ((CancelPrice / 100) / Grid);
                CancelPrice = round.two(CancelPrice);

                if (LiveKurs > CancelPrice) {
                    i++;
                    if (i > 5) {
                        calc = false;
                        break;
                    }
                    if (i == 3) {
                        if (CancelPrice > orderPrice) {
                            client.cancelOrder(new CancelOrderRequest(currencyPair, BuyOrderId));
                            System.out.println("Order gecancelt: " + orderPrice);

                            try (Connection con = DriverManager.getConnection(POS);
                                    PreparedStatement pstmt = con.prepareStatement(
                                            "DELETE FROM POS WHERE BuyOrderId = ?")) {

                                pstmt.setLong(1, BuyOrderId);
                                pstmt.executeUpdate();
                                System.out.println("Order aus Datenbank entfernt: " + orderPrice);

                            } catch (SQLException err) {
                                System.err.println("Datenbankfehler beim Löschen: " + err.getMessage());
                            }
                            calc = false;
                            break;
                        }
                    }
                }
            }

        } catch (Exception e) {
            System.err.println("Fehler in BUY_NEW für Order " + BuyOrderId + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void Test_PARTIALLY_FILLED(String currencyPair, int Grid, BinanceApiRestClient client,
            final String POS,
            final String ATH, final String HIST, List<Double> LivePrice, Long BuyOrderId, Double orderPrice,
            Order order) {

        try {
            double CancelPrice = ATHSQL.getAllTimeHigh(currencyPair, ATH);
            double LiveKurs = LivePrice.get(0);
            int i = 0;

            boolean calc = true;
            while (calc) {
                CancelPrice = CancelPrice - ((CancelPrice / 100) / Grid);
                CancelPrice = round.two(CancelPrice);

                if (LiveKurs > CancelPrice) {
                    i++;
                    if (i > 5) {
                        calc = false;
                        break;
                    }
                    if (i == 3) {
                        if (CancelPrice > orderPrice) {
                            System.out.println("Order teilweise gefüllt: " + orderPrice + " - " + BuyOrderId);

                            int Status;
                            double BuyPrice = getBuyPrice(order);
                            String BuyDate = Time.getCurrentDate();
                            String BuyTime = Time.getCurrentTime_HHmmss();
                            double Quantity = round.Quantity((Double.valueOf(order.getExecutedQty())), currencyPair);
                            double partially_BuyAmount = round.five(BuyPrice * Quantity);

                            if (partially_BuyAmount >= 6.0) {
                                Status = 666;
                            } else {
                                Status = 999;
                            }

                            try (Connection con_update = DriverManager.getConnection(POS);
                                    PreparedStatement pstmt = con_update.prepareStatement(
                                            "UPDATE POS SET BuyPrice = ?, Qty = ?, Währung = ?, BuyAmount = ?, " +
                                                    "Status = ?, BuyTime = ?, BuyDate = ? WHERE BuyOrderId = ?")) {

                                pstmt.setDouble(1, BuyPrice);
                                pstmt.setDouble(2, Quantity);
                                pstmt.setString(3, currencyPair);
                                pstmt.setDouble(4, partially_BuyAmount);
                                pstmt.setInt(5, Status);
                                pstmt.setString(6, BuyTime);
                                pstmt.setString(7, BuyDate);
                                pstmt.setLong(8, BuyOrderId);

                                pstmt.executeUpdate();

                            } catch (SQLException err) {
                                System.err.println("Fehler beim Aktualisieren der Daten: " + err.getMessage());
                                err.printStackTrace();
                            }

                            try (Connection con_insert_HIST = DriverManager.getConnection(HIST);
                                    PreparedStatement pstmt = con_insert_HIST.prepareStatement(
                                            "INSERT INTO HIST (Währung, BuyOrderId, BuyPrice, Quantity, BuyAmount, BuyDate, BuyTime) VALUES (?, ?, ?, ?, ?, ?, ?)")) {

                                pstmt.setString(1, currencyPair);
                                pstmt.setLong(2, BuyOrderId);
                                pstmt.setDouble(3, BuyPrice);
                                pstmt.setDouble(4, Quantity);
                                pstmt.setDouble(5, partially_BuyAmount);
                                pstmt.setString(6, BuyDate);
                                pstmt.setString(7, BuyTime);

                                pstmt.executeUpdate();

                            } catch (SQLException err) {
                                System.err.println("Fehler beim Einfügen in die HIST-Tabelle: " + err.getMessage());
                                err.printStackTrace();
                            }
                            calc = false;
                            break;
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Fehler in Test_PARTIALLY_FILLED für Order " + BuyOrderId + ": " + e.getMessage());
            e.printStackTrace();
        }
    }
}
