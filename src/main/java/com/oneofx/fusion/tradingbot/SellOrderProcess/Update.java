package com.oneofx.fusion.tradingbot.SellOrderProcess;

import java.math.BigDecimal;
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
import com.oneofx.fusion.tradingbot.SQL_Database.SellOrderPersistence;
import com.oneofx.fusion.tradingbot.SQL_Database.SellOrderPersistence.CompletedSell;
import com.oneofx.fusion.tradingbot.constants.TradingConstants;
import com.oneofx.fusion.tradingbot.HelperFunctions.TradingRulesFormatter;
import com.oneofx.fusion.tradingbot.domain.HistoryPosition;
import com.oneofx.fusion.tradingbot.domain.Position;

public class Update {

    private static final HistDAO histDAO = new HistDAO();
    private static final PositionDAO positionDAO = new PositionDAO();
    private static final SellOrderPersistence sellOrderPersistence = new SellOrderPersistence();

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

                if (failedStatus && new BigDecimal(order.getExecutedQty()).compareTo(BigDecimal.ZERO) == 0) {
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
                double tax = getTaxe(buyamount, sellamount);
                double GewinnAfterTax = getGewinnAfterTaxAndFeeSimple(buyamount, sellamount, buyfee, sellfee);

                if (GewinnAfterTax == 0) {
                    // REVIEW [HOCH]: Eine exakt kostenneutrale, aber abgeschlossene Order
                    // bleibt hier fuer immer Status 0. Sie wird in jedem Zyklus erneut
                    // geprueft und die zugehoerige Position wird nie geloescht.
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
                CompletedSell completedSell = new CompletedSell(
                        sellorderID,
                        currency,
                        TradingRulesFormatter.formatPrice(currency, sellamount),
                        sellprice,
                        tax,
                        getFee(buyfee, sellfee),
                        getRevenuePerTrade(buyamount, sellamount),
                        round.five(GewinnAfterTax),
                        round.five(LossAfterTax),
                        profitPercent,
                        sellfee,
                        round.five(SplitValue),
                        round.five(Qty),
                        Time.getCurrentDate(),
                        Time.getCurrentTime_HHmmss());
                try {
                    sellOrderPersistence.recordCompleted(completedSell);
                } catch (SQLException ex) {
                    System.err.println("KRITISCH: Sell-Order " + sellorderID
                            + " konnte nicht atomar abgeschlossen werden: " + ex.getMessage());
                }
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
        double taxableResult = sellamount - buyamount;
        return round.eight((taxableResult / 100.0) * TradingConstants.getProfitTaxRatePercent());
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
