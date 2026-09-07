package com.oneofx.fusion.tradingbot.BuyOrderProcess;

import java.math.BigDecimal;
import java.util.List;

import com.oneofx.fusion.client.FusionApiClient;
import com.oneofx.fusion.client.model.OrderSide;
import com.oneofx.fusion.client.model.OrderType;
import com.oneofx.fusion.client.model.TimeInForce;
import com.oneofx.fusion.client.model.NewOrder;
import com.oneofx.fusion.client.model.NewOrderResponse;
import com.oneofx.fusion.client.FusionApiException;
import com.oneofx.fusion.tradingbot.HelperFunctions.TradingRulesFormatter;
import com.oneofx.fusion.tradingbot.HelperFunctions.empty;
import com.oneofx.fusion.tradingbot.HelperFunctions.round;
import com.oneofx.fusion.tradingbot.HelperFunctions.sleep;

import com.oneofx.fusion.tradingbot.SQL_Database.CurrencyDAO;
import com.oneofx.fusion.tradingbot.SQL_Database.PositionDAO;
import com.oneofx.fusion.tradingbot.Settings.set;
import com.oneofx.fusion.tradingbot.constants.TradingConstants;
import com.oneofx.fusion.tradingbot.domain.Position;

public class BuyOrderPocess {

    private static final PositionDAO positionDAO = new PositionDAO();
    private static final CurrencyDAO currencyDAO = new CurrencyDAO();
    private static final int MAX_GRID_STEPS = 10000;

    public static void setBuyOrder(String currency, FusionApiClient client, List<Double> LivePrice) {

        double Ath = currencyDAO.getAllTimeHigh(currency);
        double TickerPrice = LivePrice.get(0);
        int grid = set.getGridforCurrency(currency);

        if (Ath <= 0.0) {
            System.err.println("⚠️ ATH für " + currency + " ist 0 - Buy-Order wird übersprungen.");
            return;
        }

        if (positionDAO.countPendingOrders(currency) >= 2) {
            return;
        }

        if (TickerPrice >= Ath) {
            // Kurs ist auf/über ATH -> keine Grid-Stufe oberhalb verfügbar
            return;
        }

        // Suche die letzten zwei Grid-Stufen ÜBER dem TickerPrice
        double stopLevel = 0.0;    // erste Stufe über Ticker -> stopPrice (Trigger)
        double limitLevel = 0.0;   // zweite Stufe darüber    -> limitPrice (Maximalpreis)
        double prevLevel = 0.0;
        double prevPrevLevel = 0.0;
        double currentLevel = Ath;
        boolean foundStopLevel = false;

        for (int count = 0; count < MAX_GRID_STEPS; count++) {
            currentLevel = currentLevel - ((currentLevel / 100.0) / grid);
            double gridPrice = TradingRulesFormatter.formatPrice(currency, currentLevel);

            if (gridPrice <= TickerPrice) {
                // Grid ist unter den Ticker gefallen -> die letzten zwei Stufen
                // davor sind unsere Trigger/Limit-Kandidaten.
                if (prevLevel > 0.0 && prevPrevLevel > 0.0) {
                    stopLevel = prevLevel;
                    limitLevel = prevPrevLevel;
                    foundStopLevel = true;
                }
                break;
            }

            prevPrevLevel = prevLevel;
            prevLevel = gridPrice;
        }

        if (!foundStopLevel) {
            // Keine zwei vollständigen Stufen über dem Ticker gefunden -> Order skippen
            return;
        }

        if (positionDAO.positionExistsAtPrice(currency, stopLevel)) {
            return;
        }

        double BuyAmount = BuyAmountFunktion.getsimplebuyamount(currency);
        if (BuyAmount <= 0) {
            BuyAmount = currencyDAO.getMinBuyAmount(currency);
        }

        String stopPriceStr = TradingRulesFormatter.formatOrderPrice(currency, stopLevel);
        String limitPriceStr = TradingRulesFormatter.formatOrderPrice(currency, limitLevel);
        // Quantity über limitPrice berechnen (worst-case Preis, sichert Notional ab)
        String Quantity = TradingRulesFormatter.calculateAndFormatQuantity(currency,
                BigDecimal.valueOf(BuyAmount), new BigDecimal(limitPriceStr));

        if (!TradingRulesFormatter.isOrderValid(
                currency, new BigDecimal(limitPriceStr), new BigDecimal(Quantity))) {
            System.out.println("⚠️ Stop-Limit Order ist ungültig gemäß Fusion-Trading-Regeln für " + currency);
            System.out.println("   stopPrice: " + stopPriceStr
                    + ", limitPrice: " + limitPriceStr
                    + ", Quantity: " + Quantity);
            return;
        }

        empty.Line();
        System.out.println("Setze STOP_LIMIT BUY für " + currency);
        System.out.println("  TickerPrice: " + TickerPrice);
        System.out.println("  stopPrice  : " + stopPriceStr + " (Trigger)");
        System.out.println("  limitPrice : " + limitPriceStr + " (Max-Preis)");

        try {
            NewOrder stopLimitBuy = new NewOrder(
                    currency,
                    OrderSide.BUY,
                    OrderType.STOP_LIMIT,
                    TimeInForce.GTC,
                    Quantity,
                    limitPriceStr)
                    .triggerPrice(stopPriceStr);

            NewOrderResponse newOrderResponse = client.newOrder(stopLimitBuy);

            positionDAO.insert(new Position.Builder(currency, String.valueOf(newOrderResponse.getOrderId()))
                    .orderPrice(new BigDecimal(stopPriceStr).doubleValue())
                    .status(0)
                    .statusCode(TradingConstants.STATUS_NEW)
                    .build());

            System.out.println("NEW_POSITION in DataBase: stop=" + stopPriceStr
                    + " EUR / limit=" + limitPriceStr + " EUR");
            System.out.println("BUY AMOUNT bei..........: " + round.three(BuyAmount) + " EUR");
            System.out.println("Quantity bei............: " + Quantity + " " + currency);
            System.out.println("");

        } catch (FusionApiException ex) {
            System.err.println("Fehler beim Kauf (Stop-Limit): " + ex.getMessage());
            sleep.for_60_seconds();
        } catch (Exception ex) {
            System.err.println("Unerwarteter Fehler: " + ex.getMessage());
            ex.printStackTrace();
        }
    }
}
