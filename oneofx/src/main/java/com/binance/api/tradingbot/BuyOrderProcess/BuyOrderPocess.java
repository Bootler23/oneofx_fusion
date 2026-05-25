package com.binance.api.tradingbot.BuyOrderProcess;

import static com.binance.api.client.domain.account.NewOrder.limitBuy;

import java.math.BigDecimal;
import java.util.List;

import com.binance.api.client.BinanceApiRestClient;
import com.binance.api.client.domain.TimeInForce;
import com.binance.api.client.domain.account.NewOrderResponse;
import com.binance.api.client.domain.market.CandlestickInterval;
import com.binance.api.client.exception.BinanceApiException;
import com.binance.api.tradingbot.HelperFunctions.Asset;
import com.binance.api.tradingbot.HelperFunctions.TradingRulesFormatter;
import com.binance.api.tradingbot.HelperFunctions.empty;
import com.binance.api.tradingbot.HelperFunctions.round;
import com.binance.api.tradingbot.HelperFunctions.sleep;

import com.binance.api.tradingbot.SQL_Database.CurrencyDAO;
import com.binance.api.tradingbot.SQL_Database.PositionDAO;
import com.binance.api.tradingbot.Settings.set;
import com.binance.api.tradingbot.constants.TradingConstants;
import com.binance.api.tradingbot.domain.Position;

public class BuyOrderPocess {

    private static final PositionDAO positionDAO = new PositionDAO();
    private static final CurrencyDAO currencyDAO = new CurrencyDAO();
    private static final int MAX_GRID_STEPS = 10000;

    public static void setBuyOrder(String currency, BinanceApiRestClient client, List<Double> LivePrice) {

        double BuyAmount;
        double Ath = currencyDAO.getAllTimeHigh(currency);      
        double BuyPrice = 0.0;      
        double TickerPrice = LivePrice.get(0);
        int grid = set.getGridforCurrency(currency);        

        if (Ath <= 0.0) {
            System.err.println("⚠️ ATH für " + currency + " ist 0 - Buy-Order wird übersprungen.");
            return;
        }

        if (positionDAO.countPendingOrders(currency) >= 2) {
            return;
        }      

        int stepsBelow = 0;
        int count = 0;
        boolean buyOrderCalc = true;

        while (buyOrderCalc) {

            Ath = Ath - ((Ath / 100.0) / grid);
            BuyPrice = TradingRulesFormatter.formatPrice(currency, Ath);
            double orderPrice = BuyPrice;

            if (TickerPrice >= BuyPrice) {
                count++;
                stepsBelow++;
                if (count >= MAX_GRID_STEPS) {
                    buyOrderCalc = false;
                }
            }
            boolean preisPasst = TickerPrice >= orderPrice;

            // Nur die erste Grid-Stufe unter dem LivePrice ordern
            if (stepsBelow > 1) {
                break;
            }

            if (preisPasst && buyOrderCalc && !positionDAO.positionExistsAtPrice(currency, orderPrice)) {

                empty.Line();
                System.out.println("Setze mal eine Order bei: " + BuyPrice);

                BuyAmount = BuyAmountFunktion.getBuyAmount(currency, client, LivePrice, preisPasst);      
                
                if (BuyAmount < 0) {
                    BuyAmount = currencyDAO.getMinBuyAmount(currency);
                }
                
                String buyprice = TradingRulesFormatter.formatOrderPrice(currency, BuyPrice);
                String Quantity = TradingRulesFormatter.calculateAndFormatQuantity(currency,
                        BigDecimal.valueOf(BuyAmount), BigDecimal.valueOf(LivePrice.get(0)));

                if (!TradingRulesFormatter.isOrderValid(
                        currency, new BigDecimal(buyprice), new BigDecimal(Quantity))) {
                    System.out.println("⚠️ Order ist ungültig gemäß Binance Trading-Regeln für " + currency);
                    System.out.println("   Preis: " + buyprice + ", Quantity: " + Quantity);
                    return;
                }

                try {
                    NewOrderResponse newOrderResponse = client
                            .newOrder(limitBuy(currency, TimeInForce.GTC, Quantity, buyprice));

                    positionDAO.insert(new Position.Builder(currency, String.valueOf(newOrderResponse.getOrderId()))
                            .orderPrice(new BigDecimal(newOrderResponse.getPrice()).doubleValue())
                            .status(0)
                            .statusCode(TradingConstants.STATUS_NEW)
                            .build());

                    System.out.println("NEW_POSITION in DataBase: " + BuyPrice + " EUR");
                    System.out.println("BUY AMOUNT bei..........: " + round.three(BuyAmount) + " EUR");
                    System.out.println("Quantity bei............: " + Quantity + " " + currency);
                    System.out.println("");
                    // return;

                } catch (BinanceApiException ex) {
                    System.err.println("Fehler beim Kauf: Nicht genügend Geld verfügbar - " + ex.getMessage());
                    sleep.for_60_seconds();
                } catch (Exception ex) {
                    System.err.println("Unerwarteter Fehler: " + ex.getMessage());
                    ex.printStackTrace();
                }
            }
        }
    }
}
