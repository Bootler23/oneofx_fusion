package com.binance.api.tradingbot.SellOrderProcess;

import java.util.List;

import com.binance.api.tradingbot.HelperFunctions.round;

public class SELL {

    public static double getProfit(double SellPrice, double BuyPrice) {
        double Profit = round.five(((SellPrice / BuyPrice) * 100) - 100);
        return Profit;
    }

    public static double getPrice(List<Double> LivePrice) {
        double SellPrice = LivePrice.get(0);
        return SellPrice;
    }

    public static double getTaxe(double Quantity, double Profit, double SellPrice) {
        double Taxe = round.five((((Quantity * SellPrice) / 100) * Profit) * (42.0 / 100.0));
        return Taxe;
    }

    public static double getFeeX2(double Quantity, double FeePercent, double SellPrice) {
        double Fee = round.six((((Quantity * SellPrice) / 100.0) * FeePercent) * 2);
        return Fee;
    }

    public static double getRevenue(double Quantity, double ProfitinPercent, double BuyPrice) {
        double RevenuePerTrade = round.five(((Quantity * BuyPrice) / 100.0) * ProfitinPercent);
        return RevenuePerTrade;
    }

    public static double getWin(double Taxe, double Fee, double RevenuePerTrade) {
        double GewinnAfterTax = round.five(RevenuePerTrade - Taxe - Fee);
        return GewinnAfterTax;
    }
}
