package com.binance.api.tradingbot.SellOrderProcess;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import com.binance.api.client.BinanceApiRestClient;
import com.binance.api.client.domain.account.Order;
import com.binance.api.client.domain.account.Trade;
import com.binance.api.client.domain.account.request.OrderStatusRequest;
import com.binance.api.client.exception.BinanceApiException;
import com.binance.api.tradingbot.BuyOrderProcess.Ticker;
import com.binance.api.tradingbot.Database.dbUrl;
import com.binance.api.tradingbot.HelperFunctions.CalcSplit;
import com.binance.api.tradingbot.HelperFunctions.round;
import com.binance.api.tradingbot.SQL_Database.HISTSQL;
import com.binance.api.tradingbot.SQL_Database.SETSQL;
import com.binance.api.tradingbot.constants.TradingConstants;
import com.binance.api.tradingbot.HelperFunctions.TradingRulesFormatter;

public class Update {

    public static void getSellTradeInformation(BinanceApiRestClient client, List<String> dataRecords) {

        for (String dataRecord : dataRecords) {

            String[] parts = dataRecord.split(", ");
            String sellorderID = parts[0];
            String currency = parts[3];

            Long OrderId = Long.valueOf(sellorderID);

            Order order = find_OrderWithOrderId(client, currency, OrderId);
            if (order != null) {

                List<Trade> tradeList = find_TradesWithOrderId(client, currency, OrderId);

                double trade_quantity = 0;
                double trade_fee = 0;

                for (Trade trade : tradeList) {
                    double Qty = Double.parseDouble(trade.getQty());
                    double Fee = Double.valueOf(trade.getCommission());

                    trade_quantity = trade_quantity + Qty;
                    trade_fee = trade_fee + Fee;
                }

                if (TradingRulesFormatter.formatQuantity(currency, trade_quantity) != TradingRulesFormatter.formatQuantity(currency, Double.valueOf(order.getExecutedQty()))) {
                    System.out.println("Die Mengen stimmen nicht überein!");
                    continue;
                }

                HISTSQL.setsellfee(sellorderID, trade_fee);
                double bnb_price = SETSQL.get_BNB_price();
                if (bnb_price == 0) {
                    continue;
                }

                double Qty = Double.valueOf(order.getExecutedQty());

                double buyamount = HISTSQL.getbuyamount(sellorderID);
                double sellamount = Double.valueOf(order.getCummulativeQuoteQty());

                double buyfee = HISTSQL.getbuyfee(sellorderID);
                double sellfee = round.eight(trade_fee * bnb_price);

                if (sellfee > 10) { // TODO wenn kein BNB da ist dann wird in Euro bezahlt
                    sellfee = trade_fee;
                }

                double SplitValue = 0;
                double LossAfterTax = 0;

                double buyprice = HISTSQL.getbuyprice(sellorderID);
                double sellprice = TradingRulesFormatter.formatPrice(currency, sellamount / Qty);
                double GewinnAfterTax = getGewinnAfterTaxAndFeeSimple(buyamount, sellamount, buyfee, sellfee);

                if (GewinnAfterTax == 0) {
                    continue;
                }

                if (SETSQL.getStatus("currency", "ROI", currency)) {
                    if (GewinnAfterTax > 0) {
                        SplitValue = CalcSplit.calcROI(currency, GewinnAfterTax);

                        if (SplitValue == 0) {
                            continue;
                        }

                        if (SplitValue > TradingConstants.MIN_SPLIT_VALUE) {
                            GewinnAfterTax = (GewinnAfterTax - SplitValue);
                        } else {
                            SplitValue = 0;
                        }
                    }
                }

                if (GewinnAfterTax < 0) {
                    LossAfterTax = GewinnAfterTax;
                    GewinnAfterTax = 0;
                } else {
                    LossAfterTax = 0;
                }

                try (Connection conUpdateHIST = DriverManager.getConnection(dbUrl.getoneOfX());
                        PreparedStatement pstmt = conUpdateHIST.prepareStatement(
                                "UPDATE HIST SET SellAmount = ?, SellPrice = ?, Tax = ?, Fee = ?, Gewinn = ?, " +
                                        "GewinnAfterTax = ?, LossAfterTax = ?, Profit = ?, SellFee = ?, " +
                                        "Split = ?, Status = ?, statusCode = ? WHERE SellOrderId = ?")) {

                    pstmt.setDouble(1, TradingRulesFormatter.formatPrice(currency, sellamount));
                    pstmt.setDouble(2, sellprice);
                    pstmt.setDouble(3, getTaxe(buyamount, sellamount));
                    pstmt.setDouble(4, getFee(buyfee, sellfee));
                    pstmt.setDouble(5, getRevenuePerTrade(buyamount, sellamount));
                    pstmt.setDouble(6, round.five(GewinnAfterTax));
                    pstmt.setDouble(7, round.five(LossAfterTax));
                    pstmt.setDouble(8, getProfitinPercent(buyprice, sellprice));
                    pstmt.setDouble(9, sellfee);
                    pstmt.setDouble(10, round.five(SplitValue));
                    pstmt.setInt(11, 1);
                    pstmt.setString(12, TradingConstants.STATUS_FILLED_CHECKED);
                    pstmt.setLong(13, OrderId);

                    pstmt.executeUpdate();
                    //System.out.println("Update in Hist!");

                } catch (SQLException e) {
                    System.err.println("Error updating HIST: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }
    }

    // -------------------------- Buy Trade Information --------------------------

    public static void getBuyTradeInformation(BinanceApiRestClient client, List<String> dataRecords) {

        for (String dataRecord : dataRecords) {

            String[] parts = dataRecord.split(", ");
            String BuyOrderId = parts[0];
            String currency = parts[1];

            Long OrderId = Long.valueOf(BuyOrderId);
            Order order = find_OrderWithOrderId(client, currency, OrderId);

            if (order != null) {

                List<Trade> tradeList = find_TradesWithOrderId(client, currency, OrderId);

                double trade_quantity = 0;
                double trade_fee = 0;

                for (Trade trade : tradeList) {
                    double Qty = Double.parseDouble(trade.getQty());
                    double Fee = Double.valueOf(trade.getCommission());

                    trade_quantity = trade_quantity + Qty;
                    trade_fee = trade_fee + Fee;
                }

                if (TradingRulesFormatter.formatQuantity(currency, trade_quantity) != TradingRulesFormatter.formatQuantity(currency, Double.valueOf(order.getExecutedQty()))) {
                    System.out.println("Die Mengen stimmen nicht überein!");
                    continue;
                }

                double bnb_price = SETSQL.get_BNB_price();
                if (bnb_price == 0) {
                    continue;
                }

                double Quantity = Double.valueOf(order.getExecutedQty());
                double BuyAmount = Double.valueOf(order.getCummulativeQuoteQty());
                double BuyPriceFromExchange = Double.valueOf(order.getPrice());
                double BuyFee = round.eight(trade_fee * bnb_price);

                try (Connection con_update = DriverManager.getConnection(dbUrl.getPOS());
                        PreparedStatement pstmt = con_update.prepareStatement(
                                "UPDATE POS SET BuyPrice = ?, OrigPrice = ?, Qty = ?, BuyAmount = ?, " +
                                        "Status = ?, statusCode = ? WHERE BuyOrderId = ?")) {

                    pstmt.setDouble(1, BuyPriceFromExchange);
                    pstmt.setDouble(2, BuyPriceFromExchange);
                    pstmt.setDouble(3, TradingRulesFormatter.formatQuantity(currency, Quantity));
                    pstmt.setDouble(4, TradingRulesFormatter.formatPrice(currency, BuyAmount));
                    pstmt.setInt(5, 1);
                    pstmt.setString(6, TradingConstants.STATUS_FILLED_CHECKED);
                    pstmt.setLong(7, OrderId);

                    pstmt.executeUpdate();
                    System.out.println("Update BuyDataInformation");

                } catch (SQLException err) {
                    System.out.println("Fehler beim Aktualisieren der Daten: " + err.getMessage());
                }

                try (Connection con_insert_HIST = DriverManager.getConnection(dbUrl.getoneOfX());
                        PreparedStatement pstmt = con_insert_HIST.prepareStatement(
                                "UPDATE HIST SET BuyPrice = ?, OrigPrice = ?, Quantity = ?, BuyAmount = ?, BuyFee = ? "
                                        +
                                        "WHERE BuyOrderId = ?")) {

                    pstmt.setDouble(1, BuyPriceFromExchange);
                    pstmt.setDouble(2, BuyPriceFromExchange);
                    pstmt.setDouble(3, TradingRulesFormatter.formatQuantity(currency, Quantity));
                    pstmt.setDouble(4, TradingRulesFormatter.formatPrice(currency, BuyAmount));
                    pstmt.setDouble(5, BuyFee);
                    pstmt.setLong(6, OrderId);

                    pstmt.executeUpdate();
                    // System.out.println("Update in Hist!");

                } catch (SQLException err) {
                    System.out.println("Fehler beim Aktualisieren der HIST-Tabelle: " + err.getMessage());
                }
            }
        }
    }

    // -------------------------- Helper Functions --------------------------

    public static double getGewinnAfterTaxAndFeeSimple(double buyamount, double sellamount, double buyfee,
            double sellfee) {
        return ((sellamount - buyamount) - getTaxe(buyamount, sellamount) - (buyfee + sellfee));
    }

    public static double getRevenuePerTrade(double buyamount, double sellamount) {
        return round.five(sellamount - buyamount);
    }

    public static double getFee(double buyfee, double sellfee) {
        return (buyfee + sellfee);
    }

    public static double getTaxe(double buyamount, double sellamount) {
        return round.eight(((sellamount - buyamount) / 100) * 42);
    }

    static double getProfitinPercent(double buyprice, double sellprice) {
        return round.five(((sellprice - buyprice) / buyprice) * 100);
    }

    public static Order find_OrderWithOrderId(BinanceApiRestClient client, String currencyPair, long orderId) {
        try {
            OrderStatusRequest orderStatusRequest = new OrderStatusRequest(currencyPair, orderId);
            Order order = client.getOrderStatus(orderStatusRequest);
            return order;

        } catch (BinanceApiException e) {
            System.out.println("Fehler beim Abrufen der Order von Binance: " + e.getMessage());
        } catch (Exception e) {
            System.out.println("Ein unerwarteter Fehler ist beim Abrufen der Order aufgetreten: " + e.getMessage());
        }
        return null;
    }

    public static List<Trade> find_TradesWithOrderId(BinanceApiRestClient client, String currencyPair,
            long orderId) {
        try {
            List<Trade> trades = client.getMyTrades(currencyPair);
            String orderIdAsString = String.valueOf(orderId);
            return trades.stream()
                    .filter(t -> t.getOrderId().equals(orderIdAsString))
                    .collect(Collectors.toList());

        } catch (BinanceApiException e) {
            System.out.println("Fehler beim Abrufen von Trades von Binance: " + e.getMessage());
        } catch (Exception e) {
            System.out.println("Ein unerwarteter Fehler ist aufgetreten: " + e.getMessage());
        }

        return new ArrayList<>();
    }
}
