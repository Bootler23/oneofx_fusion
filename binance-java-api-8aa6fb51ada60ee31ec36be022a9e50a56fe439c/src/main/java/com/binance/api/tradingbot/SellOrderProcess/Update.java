package com.binance.api.tradingbot.SellOrderProcess;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import com.binance.api.client.BinanceApiRestClient;
import com.binance.api.client.domain.account.Trade;
import com.binance.api.client.exception.BinanceApiException;
import com.binance.api.tradingbot.BuyOrderProcess.Ticker;
import com.binance.api.tradingbot.Database.dbUrl;
import com.binance.api.tradingbot.HelperFunctions.CalcSplit;
import com.binance.api.tradingbot.HelperFunctions.round;

public class Update {

    public static void getSellTradeInformation(BinanceApiRestClient client, List<String> dataRecords) {

        for (String dataRecord : dataRecords) {

            String[] parts = dataRecord.split(", ");
            String SellOrderId = parts[0];
            String Quantity = parts[1];
            String BuyPrice = parts[2];
            String Währung = parts[3];

            Long OrderId = Long.valueOf(SellOrderId);
            double SetFeePercentFromBinance = 0.08; // 0.1 = 0.15

            List<Trade> tradeList = find_TradesWithOrderId(client, Währung, OrderId);

            if (!tradeList.isEmpty()) {

                double Qty = 0;
                double BuyAmount = 0;
                double Fee = 0;
                double SplitValue = 0;

                for (Trade trade : tradeList) {
                    double tradeQuantity = Double.parseDouble(trade.getQty());
                    double tradeAmount = Double.parseDouble(trade.getQuoteQty());
                    double tradefee = Double.valueOf(trade.getCommission());

                    // hier muss man sich entscheiden welche Menge man nimmt
                    Qty = Qty + tradeQuantity;
                    BuyAmount = BuyAmount + tradeAmount;
                    Fee = Fee + tradefee;
                }

                double SellPriceFromExchange = round.five(BuyAmount / Qty);
                double GewinnAfterTax = getGewinnAfterTaxAndFee(Quantity, BuyPrice, SetFeePercentFromBinance,
                        SellPriceFromExchange,
                        getRevenuePerTrade(Quantity, BuyPrice, SellPriceFromExchange));

                if (GewinnAfterTax > 0) {
                    SplitValue = (CalcSplit.calcSplitValue(GewinnAfterTax));
                    if (SplitValue == 0) {
                        continue;
                    } else {
                        GewinnAfterTax = (GewinnAfterTax - SplitValue);
                    }
                }

                double LossAfterTax;
                if (GewinnAfterTax < 0) {
                    LossAfterTax = GewinnAfterTax;
                    GewinnAfterTax = 0;
                } else {
                    LossAfterTax = 0;
                }

                try {
                    Connection conUpdateHIST = DriverManager.getConnection(dbUrl.getHIST());
                    Statement updateHIST = conUpdateHIST.createStatement();

                    String SQL = "UPDATE HIST SET SellPrice = " + SellPriceFromExchange +
                            ", Tax = " + getTaxe(Quantity, BuyPrice, SellPriceFromExchange) +
                            ", Fee = " + getFee(Quantity, SetFeePercentFromBinance, SellPriceFromExchange) + 
                            ", Gewinn = " + getRevenuePerTrade(Quantity, BuyPrice, SellPriceFromExchange) +
                            ", GewinnAfterTax = " + round.five(GewinnAfterTax) +
                            ", LossAfterTax = " + round.five(LossAfterTax) +
                            ", Profit = " + getProfitinPercent(BuyPrice, SellPriceFromExchange) +
                            ", SellFee = " + round.five(Fee * (Ticker.getAssetPrice("BNBEUR", client))) +
                            ", Split = " + round.five(SplitValue) +
                            ", Status = " + 1 +
                            " WHERE SellOrderId = " + OrderId + ";";

                    updateHIST.executeUpdate(SQL);
                    updateHIST.close();
                    conUpdateHIST.close();

                    System.out.println("Update in Hist!");

                } catch (Exception e) {
                    System.err.println("Error updating HIST: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }
    }

    public static void getBuyTradeInformation(BinanceApiRestClient client, List<String> dataRecords) {

        for (String dataRecord : dataRecords) {

            String[] parts = dataRecord.split(", ");
            String BuyOrderId = parts[0];
            String Währung = parts[1];

            Long OrderId = Long.valueOf(BuyOrderId);

            List<Trade> tradeList = find_TradesWithOrderId(client, Währung, OrderId);

            if (!tradeList.isEmpty()) {

                double Quantity = 0;
                double BuyAmount = 0;
                double Fee = 0;
                double BuyPriceFromExchange = 0;

                for (Trade trade : tradeList) {
                    double tradeQuantity = Double.parseDouble(trade.getQty());
                    double tradeAmount = Double.parseDouble(trade.getQuoteQty());
                    double tradefee = Double.valueOf(trade.getCommission());

                    Quantity = Quantity + tradeQuantity;
                    BuyAmount = BuyAmount + tradeAmount;
                    Fee = Fee + tradefee;
                }

                Fee = round.five(Fee * Ticker.getAssetPrice("BNBEUR", client));
                BuyPriceFromExchange = round.five(BuyAmount / Quantity);

                try (Connection con_update = DriverManager.getConnection(dbUrl.getPOS());
                        Statement update = con_update.createStatement()) {

                    String SQL_update = "UPDATE POS SET "
                            + "BuyPrice = " + BuyPriceFromExchange + ", "
                            + "OrigPrice = " + BuyPriceFromExchange + ", "
                            + "Qty = " + Quantity + ", "
                            + "BuyAmount = " + round.five(BuyAmount) + ", "
                            + "Status = 1 "
                            + "WHERE BuyOrderId = " + BuyOrderId;

                    update.executeUpdate(SQL_update);
                    System.out.println("Update BuyDataInformation");

                } catch (SQLException err) {
                    System.out.println("Fehler beim Aktualisieren der Daten: " + err.getMessage());
                }

                try (Connection con_insert_HIST = DriverManager.getConnection(dbUrl.getHIST());
                        Statement insert_HIST = con_insert_HIST.createStatement()) {

                    String SQL = "UPDATE HIST SET BuyPrice = " + BuyPriceFromExchange +
                            ", OrigPrice = " + BuyPriceFromExchange +
                            ", Quantity = " + Quantity +
                            ", BuyAmount = " + round.five(BuyAmount) +
                            ", BuyFee = " + Fee +
                            " WHERE BuyOrderId = " + OrderId + ";";

                    insert_HIST.executeUpdate(SQL);
                    System.out.println("Update in Hist!");

                } catch (SQLException err) {
                    System.out.println("Fehler beim Einfügen in die HIST-Tabelle: " + err.getMessage());
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
