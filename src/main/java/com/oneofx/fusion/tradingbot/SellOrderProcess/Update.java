package com.oneofx.fusion.tradingbot.SellOrderProcess;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import com.oneofx.fusion.client.FusionApiClient;
import com.oneofx.fusion.client.model.Order;
import com.oneofx.fusion.client.model.OrderStatus;
import com.oneofx.fusion.client.model.Trade;
import com.oneofx.fusion.client.model.FusionSymbol;
import com.oneofx.fusion.client.FusionApiException;
import com.oneofx.fusion.tradingbot.BuyOrderProcess.Ticker;
import com.oneofx.fusion.tradingbot.HelperFunctions.CalcSplit;
import com.oneofx.fusion.tradingbot.HelperFunctions.Time;
import com.oneofx.fusion.tradingbot.HelperFunctions.round;
import com.oneofx.fusion.tradingbot.HelperFunctions.sleep;
import com.oneofx.fusion.tradingbot.SQL_Database.HistDAO;
import com.oneofx.fusion.tradingbot.SQL_Database.PositionDAO;
import com.oneofx.fusion.tradingbot.Database.dbUrl;
import com.oneofx.fusion.tradingbot.constants.TradingConstants;
import com.oneofx.fusion.tradingbot.HelperFunctions.TradingRulesFormatter;
import com.oneofx.fusion.tradingbot.domain.HistoryPosition;
import com.oneofx.fusion.tradingbot.domain.Position;

public class Update {

    private static final HistDAO histDAO = new HistDAO();
    private static final PositionDAO positionDAO = new PositionDAO();

    public static void getSellTradeInformation(FusionApiClient client, List<String> dataRecords) {

        for (String dataRecord : dataRecords) {

            String[] parts = dataRecord.split(", ");
            String sellorderID = parts[0];
            String currency = parts[3];

            String OrderId = sellorderID;

            Order order = find_OrderWithOrderId(client, currency, OrderId);
            if (order != null) {
                boolean failedStatus = order.getStatus() == OrderStatus.CANCELED
                        || order.getStatus() == OrderStatus.REJECTED
                        || order.getStatus() == OrderStatus.DONE_FOR_DAY
                        || order.getStatus() == OrderStatus.FILLED_AND_CANCELED;

                if (failedStatus
                        && new BigDecimal(order.getExecutedQty()).compareTo(BigDecimal.ZERO) == 0) {
                    restorePositionAfterFailedSell(sellorderID);
                    continue;
                }
                if (order.getStatus() != OrderStatus.FILLED
                        && order.getStatus() != OrderStatus.FILLED_AND_CANCELED) {
                    continue;
                }

                List<Trade> tradeList = find_TradesWithOrderId(client, currency, OrderId);

                sleep.for_1_second();

                double trade_quantity = 0;
                double trade_fee = 0;

                for (Trade trade : tradeList) {
                    double Qty = Double.parseDouble(trade.getQty());
                    double Fee = feeInQuoteCurrency(trade, currency);

                    trade_quantity = trade_quantity + Qty;
                    trade_fee = trade_fee + Fee;
                }

                if (Double.compare(TradingRulesFormatter.formatQuantity(currency, trade_quantity),
                        TradingRulesFormatter.formatQuantity(currency, Double.parseDouble(order.getExecutedQty()))) != 0) {
                    System.out.println("Die Mengen stimmen nicht überein!");
                    continue;
                }

                histDAO.setSellFee(sellorderID, trade_fee);
                double Qty = Double.valueOf(order.getExecutedQty());

                double buyamount = histDAO.getBuyAmount(sellorderID);
                double sellamount = Double.valueOf(order.getCummulativeQuoteQty());

                double buyfee = histDAO.getBuyFee(sellorderID);
                // Fusion liefert die tatsächliche Gebühr direkt je Trade.
                double sellfee = round.eight(trade_fee);

                double SplitValue = 0;
                double LossAfterTax = 0;

                double buyprice = histDAO.getBuyPrice(sellorderID);
                double sellprice = TradingRulesFormatter.formatPrice(currency, sellamount / Qty);
                double GewinnAfterTax = getGewinnAfterTaxAndFeeSimple(buyamount, sellamount, buyfee, sellfee);

                if (GewinnAfterTax == 0) {
                    continue;
                }

                // if (SETSQL.getStatus("currency", "ROI", currency)) {
                //     if (GewinnAfterTax > 0) {
                //         SplitValue = CalcSplit.calcROI(currency, GewinnAfterTax);

                //         if (SplitValue == 0) {
                //             continue;
                //         }

                //         if (SplitValue > TradingConstants.MIN_SPLIT_VALUE) {
                //             GewinnAfterTax = (GewinnAfterTax - SplitValue);
                //         } else {
                //             SplitValue = 0;
                //         }
                //     }
                // }

                if (GewinnAfterTax < 0) {
                    LossAfterTax = GewinnAfterTax;
                    GewinnAfterTax = 0;
                } else {
                    LossAfterTax = 0;
                }

                double profitPercent = getProfitinPercent(buyprice, sellprice);
                histDAO.updateBySellOrderId(new HistoryPosition.Builder(null, null)
                        .sellOrderId(sellorderID)
                        .sellAmount(TradingRulesFormatter.formatPrice(currency, sellamount))
                        .sellPrice(sellprice)
                        .tax(getTaxe(buyamount, sellamount))
                        .fee(getFee(buyfee, sellfee))
                        .gewinn(getRevenuePerTrade(buyamount, sellamount))
                        .gewinnAfterTax(round.five(GewinnAfterTax))
                        .lossAfterTax(round.five(LossAfterTax))
                        .profit(profitPercent)
                        .sellFee(sellfee)
                        .split(round.five(SplitValue))
                        .status(1)
                        .statusCode(TradingConstants.STATUS_FILLED_CHECKED)
                        .build());
                insertPerformance(currency, sellorderID, profitPercent, Qty);
                String buyOrderId = histDAO.getBuyOrderId(sellorderID);
                if (buyOrderId != null) positionDAO.delete(buyOrderId);
            }
        }
    }

    // -------------------------- Buy Trade Information --------------------------

    public static void getBuyTradeInformation(FusionApiClient client, List<String> dataRecords) {

        for (String dataRecord : dataRecords) {

            String[] parts = dataRecord.split(", ");
            String BuyOrderId = parts[0];
            String currency = parts[1];

            String OrderId = BuyOrderId;
            Order order = find_OrderWithOrderId(client, currency, OrderId);

            if (order != null) {
                if (order.getStatus() != OrderStatus.FILLED
                        && order.getStatus() != OrderStatus.FILLED_AND_CANCELED) {
                    continue;
                }

                List<Trade> tradeList = find_TradesWithOrderId(client, currency, OrderId);

                double trade_quantity = 0;
                double trade_fee = 0;

                for (Trade trade : tradeList) {
                    double Qty = Double.parseDouble(trade.getQty());
                    double Fee = feeInQuoteCurrency(trade, currency);

                    trade_quantity = trade_quantity + Qty;
                    trade_fee = trade_fee + Fee;
                }

                if (Double.compare(TradingRulesFormatter.formatQuantity(currency, trade_quantity),
                        TradingRulesFormatter.formatQuantity(currency, Double.parseDouble(order.getExecutedQty()))) != 0) {
                    System.out.println("Die Mengen stimmen nicht überein!");
                    continue;
                }

                double Quantity = Double.valueOf(order.getExecutedQty());
                double BuyAmount = Double.valueOf(order.getCummulativeQuoteQty());
                double BuyPriceFromExchange = Double.valueOf(order.getPrice());
                double BuyFee = round.eight(trade_fee);

                positionDAO.update(new Position.Builder(currency, BuyOrderId)
                        .buyPrice(BuyPriceFromExchange)
                        .origPrice(BuyPriceFromExchange)
                        .quantity(TradingRulesFormatter.formatQuantity(currency, Quantity))
                        .buyAmount(TradingRulesFormatter.formatPrice(currency, BuyAmount))
                        .status(1)
                        .statusCode(TradingConstants.STATUS_FILLED_CHECKED)
                        .build());
                System.out.println("Update BuyDataInformation");

                histDAO.updateByBuyOrderId(new HistoryPosition.Builder(currency, BuyOrderId)
                        .buyPrice(BuyPriceFromExchange)
                        .origPrice(BuyPriceFromExchange)
                        .quantity(TradingRulesFormatter.formatQuantity(currency, Quantity))
                        .buyAmount(TradingRulesFormatter.formatPrice(currency, BuyAmount))
                        .buyFee(BuyFee)
                        .build());
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

    private static double feeInQuoteCurrency(Trade trade, String pair) {
        double fee = Double.parseDouble(trade.getCommission());
        String feeCurrency = trade.getCommissionAsset();
        if (feeCurrency == null || feeCurrency.equalsIgnoreCase(FusionSymbol.quoteAsset(pair))) {
            return fee;
        }
        if (feeCurrency.equalsIgnoreCase(FusionSymbol.baseAsset(pair))) {
            return fee * Double.parseDouble(trade.getPrice());
        }
        System.err.println("Gebühr in unerwarteter Währung " + feeCurrency
                + " für " + pair + " kann nicht in die Quote-Währung umgerechnet werden.");
        return 0.0;
    }

    private static void restorePositionAfterFailedSell(String sellOrderId) {
        String buyOrderId = histDAO.getBuyOrderId(sellOrderId);
        if (buyOrderId != null) {
            positionDAO.update(new Position.Builder(null, buyOrderId).status(1).build());
        }
        histDAO.resetPendingSell(sellOrderId);
        System.err.println("Fusion-Sell-Order " + sellOrderId
                + " wurde nicht ausgeführt; die Position wurde wieder freigegeben.");
    }

    private static void insertPerformance(String currency, String sellOrderId,
            double profitPercent, double soldQuantity) {
        String selectSql = "SELECT TotalBuffer FROM performance WHERE currency = ? ORDER BY rowid DESC LIMIT 1";
        String insertSql = "INSERT INTO performance (currency, SellOrderId, Profit, TotalBuffer, "
                + "SellDate, SellTime, count_Position, SellAmount) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

        try (Connection con = DriverManager.getConnection(dbUrl.getoneOfX())) {
            double lastBuffer = 0.0;
            try (PreparedStatement select = con.prepareStatement(selectSql)) {
                select.setString(1, currency);
                try (ResultSet rs = select.executeQuery()) {
                    if (rs.next()) lastBuffer = rs.getDouble("TotalBuffer");
                }
            }

            try (PreparedStatement insert = con.prepareStatement(insertSql)) {
                insert.setString(1, currency);
                insert.setString(2, sellOrderId);
                insert.setDouble(3, profitPercent);
                insert.setDouble(4, round.two(lastBuffer + profitPercent));
                insert.setString(5, Time.getCurrentDate());
                insert.setString(6, Time.getCurrentTime_HHmmss());
                insert.setInt(7, positionDAO.getCountPOS(currency));
                insert.setDouble(8, round.five(soldQuantity));
                insert.executeUpdate();
            }
        } catch (SQLException e) {
            System.err.println("Fehler beim Performance-Insert: " + e.getMessage());
        }
    }

    public static Order find_OrderWithOrderId(FusionApiClient client, String currencyPair, String orderId) {
        try {
            Order order = client.getOrderStatus(orderId);
            return order;

        } catch (FusionApiException e) {
            System.out.println("Fehler beim Abrufen der Fusion-Order: " + e.getMessage());
        } catch (Exception e) {
            System.out.println("Ein unerwarteter Fehler ist beim Abrufen der Order aufgetreten: " + e.getMessage());
        }
        return null;
    }

    public static List<Trade> find_TradesWithOrderId(FusionApiClient client, String currencyPair,
            String orderId) {
        try {
            return client.getTradesForOrder(currencyPair, orderId);

        } catch (FusionApiException e) {
            System.out.println("Fehler beim Abrufen von Fusion-Trades: " + e.getMessage());
        } catch (Exception e) {
            System.out.println("Ein unerwarteter Fehler ist aufgetreten: " + e.getMessage());
        }

        return new ArrayList<>();
    }
}
