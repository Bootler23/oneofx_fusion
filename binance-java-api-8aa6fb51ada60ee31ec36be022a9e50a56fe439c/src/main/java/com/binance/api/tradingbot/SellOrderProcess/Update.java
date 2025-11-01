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
import com.binance.api.tradingbot.SQL_Database.SETSQL;
import com.binance.api.tradingbot.constants.TradingConstants;
import com.binance.api.tradingbot.HelperFunctions.RoundCurrency;

public class Update {

    public static void getSellTradeInformation(BinanceApiRestClient client, List<String> dataRecords) {

        for (String dataRecord : dataRecords) {

            String[] parts = dataRecord.split(", ");
            String SellOrderId = parts[0];
            String Quantity = parts[1];
            String BuyPrice = parts[2];
            String currency = parts[3];

            Long OrderId = Long.valueOf(SellOrderId);
            double SetFeePercentFromBinance = 0.08; // 0.1 = 0.15

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

                if (round.three(trade_quantity) != round.three(Double.valueOf(order.getExecutedQty()))) {
                    System.out.println("Die Mengen stimmen nicht überein!");
                    continue;
                }

                double Qty = Double.valueOf(order.getExecutedQty());
                double BuyAmount = Double.valueOf(order.getCummulativeQuoteQty());
                double SellFee = round.eight(trade_fee * Ticker.getAssetPrice("BNBEUR", client));
                double SplitValue = 0;
                double LossAfterTax = 0;

                double SellPriceFromExchange = RoundCurrency.forQuantity((BuyAmount / Qty), currency);

                double GewinnAfterTax = getGewinnAfterTaxAndFee(Quantity, BuyPrice, SetFeePercentFromBinance,
                        SellPriceFromExchange,
                        getRevenuePerTrade(Quantity, BuyPrice, SellPriceFromExchange));

                if (GewinnAfterTax == 0) {
                    continue;
                }
              
                if (SETSQL.getROIstatus()) {  
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

                try (Connection conUpdateHIST = DriverManager.getConnection(dbUrl.getHIST());
                        PreparedStatement pstmt = conUpdateHIST.prepareStatement(
                                "UPDATE HIST SET SellPrice = ?, Tax = ?, Fee = ?, Gewinn = ?, " +
                                        "GewinnAfterTax = ?, LossAfterTax = ?, Profit = ?, SellFee = ?, " +
                                        "Split = ?, Status = ?, statusCode = ? WHERE SellOrderId = ?")) {

                    pstmt.setDouble(1, SellPriceFromExchange);
                    pstmt.setDouble(2, getTaxe(Quantity, BuyPrice, SellPriceFromExchange));
                    pstmt.setDouble(3, getBuyFeeInfoFromHist(OrderId) + SellFee);
                    pstmt.setDouble(4, getRevenuePerTrade(Quantity, BuyPrice, SellPriceFromExchange));
                    pstmt.setDouble(5, round.five(GewinnAfterTax));
                    pstmt.setDouble(6, round.five(LossAfterTax));
                    pstmt.setDouble(7, getProfitinPercent(BuyPrice, SellPriceFromExchange));
                    pstmt.setDouble(8, SellFee);
                    pstmt.setDouble(9, round.five(SplitValue));
                    pstmt.setInt(10, 1);
                    pstmt.setString(11, TradingConstants.STATUS_FILLED_CHECKED);
                    pstmt.setLong(12, OrderId);

                    pstmt.executeUpdate();
                    System.out.println("Update in Hist!");

                } catch (SQLException e) {
                    System.err.println("Error updating HIST: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }
    }

    public static double getBuyFeeInfoFromHist(long BuyOrderId) {
        double BuyFee = 0;
        try (Connection con = DriverManager.getConnection(dbUrl.getHIST());
                PreparedStatement pstmt = con
                        .prepareStatement("SELECT BuyFee FROM HIST WHERE SellOrderId = ?")) {

            pstmt.setLong(1, BuyOrderId);
            java.sql.ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                BuyFee = rs.getDouble("BuyFee");
            }

        } catch (SQLException err) {
            System.out.println("Fehler beim Abrufen der BuyFee aus HIST: " + err.getMessage());
        }
        return BuyFee;
    }

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

                if (round.three(trade_quantity) != round.three(Double.valueOf(order.getExecutedQty()))) {
                    System.out.println("Die Mengen stimmen nicht überein!");
                    continue;
                }

                double Quantity = Double.valueOf(order.getExecutedQty());
                double BuyAmount = Double.valueOf(order.getCummulativeQuoteQty());

                double BuyPriceFromExchange = Double.valueOf(order.getPrice());

                double BuyFee = round.eight(trade_fee * Ticker.getAssetPrice("BNBEUR", client));

                try (Connection con_update = DriverManager.getConnection(dbUrl.getPOS());
                        PreparedStatement pstmt = con_update.prepareStatement(
                                "UPDATE POS SET BuyPrice = ?, OrigPrice = ?, Qty = ?, BuyAmount = ?, " +
                                        "Status = ?, statusCode = ? WHERE BuyOrderId = ?")) {

                    pstmt.setDouble(1, BuyPriceFromExchange);
                    pstmt.setDouble(2, BuyPriceFromExchange);
                    pstmt.setDouble(3, RoundCurrency.forQuantity(Quantity, currency));
                    pstmt.setDouble(4, RoundCurrency.BuyAmount(BuyAmount, currency));
                    pstmt.setInt(5, 1);
                    pstmt.setString(6, TradingConstants.STATUS_FILLED_CHECKED);
                    pstmt.setLong(7, OrderId);

                    pstmt.executeUpdate();
                    System.out.println("Update BuyDataInformation");

                } catch (SQLException err) {
                    System.out.println("Fehler beim Aktualisieren der Daten: " + err.getMessage());
                }

                try (Connection con_insert_HIST = DriverManager.getConnection(dbUrl.getHIST());
                        PreparedStatement pstmt = con_insert_HIST.prepareStatement(
                                "UPDATE HIST SET BuyPrice = ?, OrigPrice = ?, Quantity = ?, BuyAmount = ?, BuyFee = ? "
                                        +
                                        "WHERE BuyOrderId = ?")) {

                    pstmt.setDouble(1, BuyPriceFromExchange);
                    pstmt.setDouble(2, BuyPriceFromExchange);
                    pstmt.setDouble(3, RoundCurrency.forQuantity(Quantity, currency));
                    pstmt.setDouble(4, RoundCurrency.BuyAmount(BuyAmount, currency));
                    pstmt.setDouble(5, BuyFee);
                    pstmt.setLong(6, OrderId);

                    pstmt.executeUpdate();
                    System.out.println("Update in Hist!");

                } catch (SQLException err) {
                    System.out.println("Fehler beim Aktualisieren der HIST-Tabelle: " + err.getMessage());
                }
            }
        }
    }

    public static double getGewinnAfterTaxAndFee(String Quantity, String BuyPrice, double SetFeePercentFromBinance,
            double SellPriceFromExchange, double RevenuePerTrade) {
        return SELL.getWin(getTaxe(Quantity, BuyPrice, SellPriceFromExchange),
                getFee(Quantity, SetFeePercentFromBinance, SellPriceFromExchange), RevenuePerTrade);
    }

    public static double getRevenuePerTrade(String Quantity, String BuyPrice, double SellPriceFromExchange) {
        return SELL.getRevenue((Double.valueOf(Quantity)), getProfitinPercent(BuyPrice, SellPriceFromExchange),
                Double.valueOf(BuyPrice));
    }

    // Hole dem BNB Preis und errechne die Gebühr (Fee * BNB/EUR * 2)
    public static double getFee(String Quantity, double SetFeePercentFromBinance, double SellPriceFromExchange) {
        return SELL.getFeeX2((Double.valueOf(Quantity)), SetFeePercentFromBinance, SellPriceFromExchange);
    }

    public static double getTaxe(String Quantity, String BuyPrice, double SellPriceFromExchange) {
        return SELL.getTaxe((Double.valueOf(Quantity)), getProfitinPercent(BuyPrice, SellPriceFromExchange),
                SellPriceFromExchange);
    }

    private static double getProfitinPercent(String BuyPrice, double SellPriceFromExchange) {
        return SELL.getProfit(Double.valueOf(SellPriceFromExchange),
                (Double.valueOf(BuyPrice)));
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
