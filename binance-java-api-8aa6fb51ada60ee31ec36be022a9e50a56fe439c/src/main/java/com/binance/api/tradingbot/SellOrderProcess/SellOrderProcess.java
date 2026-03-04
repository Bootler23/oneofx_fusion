package com.binance.api.tradingbot.SellOrderProcess;

import static com.binance.api.client.domain.account.NewOrder.marketSell;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import com.binance.api.client.BinanceApiRestClient;
import com.binance.api.client.domain.account.NewOrderResponse;
import com.binance.api.client.exception.BinanceApiException;
import com.binance.api.tradingbot.BuyOrderProcess.Ticker;
import com.binance.api.tradingbot.Database.dbUrl;
import com.binance.api.tradingbot.HelperFunctions.Time;
import com.binance.api.tradingbot.HelperFunctions.empty;
import com.binance.api.tradingbot.HelperFunctions.round;
import com.binance.api.tradingbot.HelperFunctions.sleep;
import com.binance.api.tradingbot.RiskRewardRatio.CurrencyRRR;
import com.binance.api.tradingbot.SQL_Database.PerformanceSQL;
import com.binance.api.tradingbot.SQL_Database.CurrencySQL;
import com.binance.api.tradingbot.SQL_Database.POSSQL;
import com.binance.api.tradingbot.SQL_Database.SETSQL;
import com.binance.api.tradingbot.HelperFunctions.Slippage;
import com.binance.api.tradingbot.HelperFunctions.TradingRulesFormatter;
import java.math.BigDecimal;

public class SellOrderProcess {

    public static void setSellOrder(String currency, BinanceApiRestClient client,
            List<String> GetRecordFromDataBase_POS, List<Double> LivePrice, double PnL) {

        double currentPrice = LivePrice.get(0);
        double percent = SETSQL.getPercentToSell();

        if (percent <= 0) {
            percent = 0.5;
        }

        String today = Time.getCurrentDate();
        double dynamicStopLoss = round.two(PerformanceSQL.calculateCurrentDynamicStopLoss(currency, today));
        // double currentRRR = PerformanceSQL.getCurrentWeightedRRR(currency, today);
        // System.out.println("📊 Aktuelles RRR: " + currentRRR + " | Dynamic Stop-Loss:
        // " + dynamicStopLoss + "%");

        for (String dataRecord : GetRecordFromDataBase_POS) {
            String[] parts = dataRecord.split(", ");

            String BuyOrderId = parts[0];
            String OrigPrice_String = parts[2];
            String Quantity_String = parts[3];
            String BuyPrice_String = parts[5];

            double BuyPrice_Double = Double.valueOf(BuyPrice_String);

            if (BuyPrice_Double <= 0) {
                continue;
            }

            double takeProfitTarget = (BuyPrice_Double / 100) * (100 + percent);
            // double stopLossTarget = BuyPrice_Double * (1 + (dynamicStopLoss / 100));
            double stopLossTarget = BuyPrice_Double * (1 + (-2.3 / 100));

            boolean hitStopLoss = currentPrice <= stopLossTarget;
            boolean hitTakeProfit = currentPrice >= takeProfitTarget;

            if (hitStopLoss) {
                // empty.Line();
                // System.out.println("🔴 STOP-LOSS: " + currency + " bei " + dynamicStopLoss +
                // "%");
                // System.out.println("Kaufpreis: " + BuyPrice_Double + " -> Aktuell: " +
                // currentPrice);
                // executeSell(currency, client, BuyOrderId, Quantity_String, LivePrice,
                // BuyPrice_Double, true);

                // erst verkaufen wenn es dazu positive gibt

                // CurrencyRRR currencyRRR = CurrencySQL.getCurrencyRRR(currency);
                // if (currencyRRR != null) {
                // System.out.println(currencyRRR.toWeightedBreakdownString());
                // System.out.println("RRR: " + round.two(currencyRRR.calculateRRR()));
                // }
                return; // Todo

            } else if (hitTakeProfit) {

                if (!Slippage.isProfitableAfterSlippage(currency, client, Double.parseDouble(Quantity_String),
                        BuyPrice_Double, 0.5)) {
                    System.out.println("⏳ Warte auf besseres Orderbuch...");
                    return;
                }

                Ticker.get_CurrencyPair_Price(currency, client, LivePrice);
                double polledPrice = LivePrice.get(0);
                if (polledPrice != currentPrice) {
                    return;
                }

                empty.Line();
                // System.out.println("✅ TAKE-PROFIT: " + currency + " bei +" + percent + "%");
                // System.out.println("Kaufpreis: " + BuyPrice_Double + " -> Aktuell: " +
                // currentPrice);

                executeSell(currency, client, BuyOrderId, Quantity_String, LivePrice, BuyPrice_Double, false);

                CurrencyRRR currencyRRR = CurrencySQL.getCurrencyRRR(currency);
                if (currencyRRR != null) {
                    System.out.println(currencyRRR.toWeightedBreakdownString());
                    System.out.println("RRR: " + round.two(currencyRRR.calculateRRR()));
                }

                // double currentRRR = PerformanceSQL.getCurrentWeightedRRR(currency, today);
                // System.out.println("📊 Aktuelles RRR: " + currentRRR + " | Dynamic
                // Stop-Loss:// " + dynamicStopLoss + "%");
                return; // Todo
            }
        }
    }

    private static void executeSell(String currency, BinanceApiRestClient client, String BuyOrderId,
            String Quantity_String, List<Double> LivePrice, double buyprice, boolean isStopLoss) {

        try {
            // Quantity nochmals durch Trading-Rules-Formatter laufen lassen zur Sicherheit
            String validatedQuantity = TradingRulesFormatter.formatOrderQuantity(currency,
                    new BigDecimal(Quantity_String));

            // Validierung der Quantity
            if (!TradingRulesFormatter.isQuantityValid(currency, new BigDecimal(validatedQuantity))) {
                System.out.println("⚠️ Quantity ungültig für " + currency + ": " + validatedQuantity);
                return;
            }

            NewOrderResponse orderResponse = getNewSellOrderResponse(currency, client, validatedQuantity);

            System.out
                    .println("DEBUG: Verkauf erfolgreich abgeschlossen für BuyOrderId: " + BuyOrderId + " " + currency);
            // System.out.println("Verkauf erfolgreich: " + BuyOrderId + " (" + (isStopLoss
            // ? "STOP-LOSS" : "TAKE-PROFIT") + ")");

            update_HIST_AfterMarketSell(BuyOrderId, Time.getCurrentTime_HHmmss(), Time.getCurrentDate(), orderResponse);
            update_POS_AfterMarketSell(BuyOrderId);
            delete_POS_AfterMarketSell(BuyOrderId);

            updatePerformanceAfterSell(orderResponse, currency, BuyOrderId, isStopLoss, buyprice, LivePrice.get(0));

        } catch (BinanceApiException ex) {
            System.err.println("Fehler beim Verkauf: Keine Menge vorhanden! " + ex.getMessage());
            sleep.for_10_seconds();
        }
    }

    private static void updatePerformanceAfterSell(NewOrderResponse orderResponse, String currency, String BuyOrderId,
            boolean isStopLoss, double buyprice, double livePrice) {
        try (Connection con = DriverManager.getConnection(dbUrl.getoneOfX())) {

            double currentProfit = Update.getProfitinPercent(buyprice, livePrice);
            String today = Time.getCurrentDate();
            double lastBuffer = getLastTotalBuffer(currency);
            double newTotalBuffer = lastBuffer + currentProfit;

            String insertSql = "INSERT INTO performance (currency, SellOrderId, Profit, TotalBuffer, " +
                    "SellDate, SellTime, count_Position, SellAmount) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

            try (PreparedStatement ps = con.prepareStatement(insertSql)) {
                ps.setString(1, currency);
                ps.setLong(2, orderResponse.getOrderId());
                ps.setDouble(3, currentProfit);
                ps.setDouble(4, round.two(newTotalBuffer));
                ps.setString(5, Time.getCurrentDate());
                ps.setString(6, Time.getCurrentTime_HHmmss());
                ps.setInt(7, POSSQL.getCountPOS(currency));
                ps.setDouble(8, round.five(Double.parseDouble(orderResponse.getExecutedQty())));
                ps.executeUpdate();

                // System.out.println("📊 Performance-Eintrag erstellt für SellOrderId: " + orderResponse.getOrderId());
            }

        } catch (SQLException err) {
            System.err.println("Fehler beim Performance-Insert: " + err.getMessage());
            err.printStackTrace();
        }
    }

    private static double getLastTotalBuffer(String currency) {
        String sql = "SELECT TotalBuffer FROM performance WHERE currency = ? " +
                "ORDER BY rowid DESC LIMIT 1";

        try (Connection con = DriverManager.getConnection(dbUrl.getoneOfX());
                PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setString(1, currency);
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                return rs.getDouble("TotalBuffer");
            }
        } catch (SQLException e) {
            System.err.println("Fehler beim Abrufen des letzten TotalBuffers: " + e.getMessage());
        }

        return 0.0;
    }

    private static void delete_POS_AfterMarketSell(String BuyOrderId) {
        try (Connection con = DriverManager.getConnection(dbUrl.getPOS());
                PreparedStatement pstmt = con.prepareStatement(
                        "DELETE FROM POS WHERE BuyOrderId = ?")) {

            pstmt.setString(1, BuyOrderId);
            pstmt.executeUpdate();

        } catch (SQLException err) {
            System.err.println("Fehler beim Löschen: " + err.getMessage());
            err.printStackTrace();
        }
    }

    private static void update_HIST_AfterMarketSell(String BuyOrderId, String SellTime,
            String SellDate, NewOrderResponse newOrderResponse) {

        try (Connection con = DriverManager.getConnection(dbUrl.getoneOfX());
                PreparedStatement pstmt = con.prepareStatement(
                        "UPDATE HIST SET Status = ?, SellOrderId = ?, SellTime = ?, SellDate = ? WHERE BuyOrderId = ?")) {

            pstmt.setInt(1, 0);
            pstmt.setLong(2, newOrderResponse.getOrderId());
            pstmt.setString(3, SellTime);
            pstmt.setString(4, SellDate);
            pstmt.setString(5, BuyOrderId);

            pstmt.executeUpdate();

        } catch (SQLException err) {
            System.err.println("Fehler beim Aktualisieren: " + err.getMessage());
            err.printStackTrace();
        }
    }

    private static void update_POS_AfterMarketSell(String BuyOrderId) {
        try (Connection con = DriverManager.getConnection(dbUrl.getPOS());
                Statement stmt = con.createStatement()) {

            String UpdateSQL = "UPDATE POS SET Status = 2 WHERE BuyOrderId = '" + BuyOrderId + "';";
            stmt.executeUpdate(UpdateSQL);

        } catch (SQLException err) {
            System.out.println("Fehler beim Aktualisieren: " + err.getMessage());
        }
    }

    public static NewOrderResponse getNewSellOrderResponse(String CurrencyPair, BinanceApiRestClient client,
            String Quantity_String) {
        NewOrderResponse newOrderResponse = client.newOrder(marketSell(CurrencyPair, Quantity_String));
        return newOrderResponse;
    }

    public static void handleSellProcess(String currency, BinanceApiRestClient client) {

        List<String> BuyAmountRecord = new ArrayList<String>();

        POSSQL.getPositionWithMaxInMinus(BuyAmountRecord, currency, client);

        String record = BuyAmountRecord.get(0);
        String[] recordParts = record.split(", ");

        String BuyOrderId = recordParts[0];
        String Quantity_String = recordParts[1];
        String CurrencyPair = recordParts[2];

        try {
            // Quantity nochmals durch Trading-Rules-Formatter laufen lassen zur Sicherheit
            String validatedQuantity = TradingRulesFormatter.formatOrderQuantity(CurrencyPair,
                    new BigDecimal(Quantity_String));

            // Validierung der Quantity
            if (!TradingRulesFormatter.isQuantityValid(CurrencyPair, new BigDecimal(validatedQuantity))) {
                System.out.println("⚠️ Quantity ungültig für " + CurrencyPair + ": " + validatedQuantity);
                return;
            }

            NewOrderResponse newOrderResponse = getNewSellOrderResponse(CurrencyPair, client, validatedQuantity);

            update_POS_AfterMarketSell(BuyOrderId);
            update_HIST_AfterMarketSell(BuyOrderId, Time.getCurrentTime_HHmmss(), Time.getCurrentDate(),
                    newOrderResponse);
            delete_POS_AfterMarketSell(BuyOrderId);

        } catch (BinanceApiException dex) {
            System.err.println("Fehler beim Verkauf: Keine Menge für den Verkauf verfügbar!");
            sleep.for_10_seconds();
        } catch (Exception e) {
            System.err.println("Fehler beim Verkaufsprozess: " + e.getMessage());
        }
    }
}
