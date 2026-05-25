package com.binance.api.tradingbot.SellOrderProcess;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import com.binance.api.client.BinanceApiRestClient;
import com.binance.api.client.domain.account.Order;
import com.binance.api.client.domain.account.Trade;
import com.binance.api.client.domain.account.request.OrderStatusRequest;
import com.binance.api.client.exception.BinanceApiException;
import com.binance.api.tradingbot.BuyOrderProcess.Ticker;
import com.binance.api.tradingbot.HelperFunctions.CalcSplit;
import com.binance.api.tradingbot.HelperFunctions.round;
import com.binance.api.tradingbot.HelperFunctions.sleep;
import com.binance.api.tradingbot.SQL_Database.HistDAO;
import com.binance.api.tradingbot.SQL_Database.PositionDAO;
import com.binance.api.tradingbot.SQL_Database.SETSQL;
import com.binance.api.tradingbot.constants.TradingConstants;
import com.binance.api.tradingbot.HelperFunctions.TradingRulesFormatter;
import com.binance.api.tradingbot.domain.HistoryPosition;
import com.binance.api.tradingbot.domain.Position;

public class Update {

    private static final HistDAO histDAO = new HistDAO();
    private static final PositionDAO positionDAO = new PositionDAO();

    public static void getSellTradeInformation(BinanceApiRestClient client, List<String> dataRecords) {

        for (String dataRecord : dataRecords) {

            String[] parts = dataRecord.split(", ");
            String sellorderID = parts[0];
            String currency = parts[3];

            Long OrderId = Long.valueOf(sellorderID);

            Order order = find_OrderWithOrderId(client, currency, OrderId);
            if (order != null) {

                List<Trade> tradeList = find_TradesWithOrderId(client, currency, OrderId);

                sleep.for_1_second();

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

                histDAO.setSellFee(sellorderID, trade_fee);
                double bnb_price = SETSQL.get_BNB_price();
                if (bnb_price == 0) {
                    continue;
                }

                double Qty = Double.valueOf(order.getExecutedQty());

                double buyamount = histDAO.getBuyAmount(sellorderID);
                double sellamount = Double.valueOf(order.getCummulativeQuoteQty());

                double buyfee = histDAO.getBuyFee(sellorderID);
                double sellfee = round.eight(trade_fee * bnb_price);

                if (sellfee > 10) { // TODO wenn kein BNB da ist dann wird in Euro bezahlt
                    sellfee = trade_fee;
                }

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

                histDAO.updateBySellOrderId(new HistoryPosition.Builder(null, null)
                        .sellOrderId(sellorderID)
                        .sellAmount(TradingRulesFormatter.formatPrice(currency, sellamount))
                        .sellPrice(sellprice)
                        .tax(getTaxe(buyamount, sellamount))
                        .fee(getFee(buyfee, sellfee))
                        .gewinn(getRevenuePerTrade(buyamount, sellamount))
                        .gewinnAfterTax(round.five(GewinnAfterTax))
                        .lossAfterTax(round.five(LossAfterTax))
                        .profit(getProfitinPercent(buyprice, sellprice))
                        .sellFee(sellfee)
                        .split(round.five(SplitValue))
                        .status(1)
                        .statusCode(TradingConstants.STATUS_FILLED_CHECKED)
                        .build());
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
