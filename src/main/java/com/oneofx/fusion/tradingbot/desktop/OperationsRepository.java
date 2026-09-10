package com.oneofx.fusion.tradingbot.desktop;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.oneofx.fusion.tradingbot.Database.dbUrl;
import com.oneofx.fusion.tradingbot.bot.BotRuntime;
import com.oneofx.fusion.client.model.OrderSide;

/** Read-only cockpit queries plus the local application event log. */
public final class OperationsRepository {

    public List<OpenOrderRow> loadOpenOrders() throws SQLException {
        return loadOpenOrders(BotRuntime.activeBotId());
    }

    public List<OpenOrderRow> loadOpenOrders(long botId) throws SQLException {
        String sql = "SELECT 'Kauf' AS side, currency, BuyOrderId AS orderId, "
                + "OrderPrice AS price, quantity, Status AS status, statusCode, "
                + "'BUY' AS rawSide, 'LIVE' AS origin, 'LIMIT' AS orderType, "
                + "BuyOrderId AS referenceId "
                + "FROM positions WHERE bot_id = ? AND Status = 0 "
                + "UNION ALL "
                + "SELECT 'Verkauf', currency, SellOrderId, SellPrice, Quantity, Status, statusCode, "
                + "'SELL', 'LIVE', 'LIMIT', BuyOrderId "
                + "FROM historyPosition WHERE bot_id = ? AND Status = 0 AND SellOrderId IS NOT NULL "
                + "ORDER BY currency, side";
        List<OpenOrderRow> rows = new ArrayList<>();
        try (Connection con = open(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, botId);
            ps.setLong(2, botId);
            try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                rows.add(new OpenOrderRow(rs.getString("side"), rs.getString("currency"),
                        rs.getString("orderId"), rs.getDouble("price"),
                        rs.getDouble("quantity"), rs.getInt("status"),
                        value(rs.getString("statusCode"), "Offen"),
                        OrderOrigin.valueOf(rs.getString("origin")),
                        OrderSide.valueOf(rs.getString("rawSide")),
                        rs.getString("orderType"), rs.getString("referenceId")));
            }
            }
        }
        String paperSql = "SELECT side, currency, order_id, limit_price, quantity, status, "
                + "type, position_id "
                + "FROM paperOrders WHERE bot_id = ? AND status = 'OPEN' ORDER BY currency";
        try (Connection con = open(); PreparedStatement ps = con.prepareStatement(paperSql)) {
            ps.setLong(1, botId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) rows.add(new OpenOrderRow(
                        "BUY".equals(rs.getString("side")) ? "Paper-Kauf" : "Paper-Verkauf",
                        rs.getString("currency"), rs.getString("order_id"),
                        rs.getDouble("limit_price"), rs.getDouble("quantity"), 0,
                        "PAPER · Offen", OrderOrigin.PAPER,
                        OrderSide.valueOf(rs.getString("side")), rs.getString("type"),
                        value(rs.getString("position_id"), rs.getString("order_id"))));
            }
        }
        return rows;
    }

    public List<PositionRow> loadPositions() throws SQLException {
        return loadPositions(BotRuntime.activeBotId());
    }

    public List<PositionRow> loadPositions(long botId) throws SQLException {
        String sql = "SELECT currency, BuyOrderId, BuyPrice, OrderPrice, quantity, BuyAmount, "
                + "Profit, unrealisierterPNL, Status, statusCode, BuyDate, BuyTime "
                + "FROM positions WHERE bot_id = ? AND Status IN (1, 5, 7, 8) "
                + "ORDER BY COALESCE(BuyDate, '') DESC, COALESCE(BuyTime, '') DESC";
        List<PositionRow> rows = new ArrayList<>();
        try (Connection con = open(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, botId);
            try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                rows.add(new PositionRow(rs.getString("currency"),
                        rs.getString("BuyOrderId"), rs.getDouble("BuyPrice"),
                        rs.getDouble("OrderPrice"), rs.getDouble("quantity"),
                        rs.getDouble("BuyAmount"), rs.getDouble("Profit"),
                        rs.getDouble("unrealisierterPNL"), rs.getInt("Status"),
                        value(rs.getString("statusCode"), statusLabel(rs.getInt("Status"))),
                        joinDateTime(rs.getString("BuyDate"), rs.getString("BuyTime"))));
            }
            }
        }
        String paperSql = "SELECT currency, position_id, entry_price, quantity, buy_amount, "
                + "unrealized_pnl, opened_at FROM paperPositions WHERE bot_id = ? "
                + "AND status = 'OPEN' ORDER BY opened_at DESC";
        try (Connection con = open(); PreparedStatement ps = con.prepareStatement(paperSql)) {
            ps.setLong(1, botId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    double amount = rs.getDouble("buy_amount");
                    double pnl = rs.getDouble("unrealized_pnl");
                    rows.add(new PositionRow(rs.getString("currency"),
                            rs.getString("position_id"), rs.getDouble("entry_price"),
                            rs.getDouble("entry_price"), rs.getDouble("quantity"), amount,
                            amount == 0 ? 0 : pnl / amount * 100.0, pnl, 1,
                            "PAPER · Offen", rs.getString("opened_at")));
                }
            }
        }
        return rows;
    }

    public List<AttemptRow> loadUnresolvedAttempts() throws SQLException {
        return loadUnresolvedAttempts(BotRuntime.activeBotId());
    }

    public List<AttemptRow> loadUnresolvedAttempts(long botId) throws SQLException {
        String sql = "SELECT 'Kauf' AS side, attempt_id AS attemptId, currency_pair AS currency, "
                + "exchange_order_id AS exchangeOrderId, state, error_message AS error, updated_at "
                + "FROM buy_attempts WHERE bot_id = ? "
                + "AND state IN ('SUBMITTING', 'RECONCILIATION_REQUIRED') "
                + "UNION ALL "
                + "SELECT 'Verkauf', attempt_id, currency_pair, exchange_order_id, state, "
                + "error_message, updated_at FROM sell_attempts "
                + "WHERE bot_id = ? AND state IN ('SUBMITTING', 'RECONCILIATION_REQUIRED') "
                + "ORDER BY updated_at DESC";
        List<AttemptRow> rows = new ArrayList<>();
        try (Connection con = open(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, botId);
            ps.setLong(2, botId);
            try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                rows.add(new AttemptRow(rs.getString("side"), rs.getString("currency"),
                        rs.getString("attemptId"), rs.getString("exchangeOrderId"),
                        rs.getString("state"), rs.getString("error"),
                        rs.getString("updated_at")));
            }
            }
        }
        return rows;
    }

    public List<ActivityRow> loadActivity(int limit) throws SQLException {
        return loadActivity(BotRuntime.activeBotId(), limit);
    }

    public List<ActivityRow> loadActivity(long botId, int limit) throws SQLException {
        String sql = "SELECT created_at, severity, category, currency, message FROM ("
                + "SELECT created_at, severity, category, currency, message FROM activityLog WHERE bot_id = ? "
                + "UNION ALL SELECT created_at, "
                + "CASE WHEN state IN ('REJECTED', 'RECONCILIATION_REQUIRED') THEN 'WARN' ELSE 'INFO' END, "
                + "'KAUFORDER', currency_pair, 'Orderversuch ' || attempt_id || ': ' || state "
                + "|| CASE WHEN error_message IS NULL THEN '' ELSE ' – ' || error_message END "
                + "FROM buy_attempts WHERE bot_id = ? "
                + "UNION ALL SELECT created_at, "
                + "CASE WHEN state IN ('REJECTED', 'RECONCILIATION_REQUIRED') THEN 'WARN' ELSE 'INFO' END, "
                + "'VERKAUFSORDER', currency_pair, 'Orderversuch ' || attempt_id || ': ' || state "
                + "|| CASE WHEN error_message IS NULL THEN '' ELSE ' – ' || error_message END "
                + "FROM sell_attempts WHERE bot_id = ? "
                + "UNION ALL SELECT created_at, 'INFO', 'STRATEGIE', currency, "
                + "'Strategie #' || strategy_id || ': Kauf=' || buy_signal || ', Verkauf=' "
                + "|| sell_signal || ', Sperre=' || blocked || ', Bestätigungen=' "
                + "|| confirmations || ' – ' || explanation "
                + "FROM strategyEvaluationLog WHERE bot_id = ?) ORDER BY created_at DESC LIMIT ?";
        List<ActivityRow> rows = new ArrayList<>();
        try (Connection con = open(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, botId);
            ps.setLong(2, botId);
            ps.setLong(3, botId);
            ps.setLong(4, botId);
            ps.setInt(5, Math.max(1, Math.min(limit, 2_000)));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.add(new ActivityRow(rs.getString("created_at"),
                            rs.getString("severity"), rs.getString("category"),
                            rs.getString("currency"), rs.getString("message")));
                }
            }
        }
        return rows;
    }

    public List<WarningRow> loadWarnings() throws SQLException {
        return loadWarnings(BotRuntime.activeBotId());
    }

    public List<TradeMarkerRow> loadTradeMarkers(long botId,String currency,int limit)
            throws SQLException {
        String normalized=CurrencySettings.normalizeCurrency(currency);
        int safeLimit=Math.max(1,Math.min(limit,2_000));
        List<TradeMarkerRow> rows=new ArrayList<>();
        String live="SELECT BuyOrderId,COALESCE(NULLIF(BuyPrice,0),OrigPrice,0) buy_price,"
                +"COALESCE(SellPrice,0) sell_price,buyunixtime,BuyDate,BuyTime,SellDate,SellTime "
                +"FROM historyPosition WHERE bot_id=? AND currency=? "
                +"ORDER BY COALESCE(buyunixtime,0) DESC LIMIT ?";
        try(Connection con=open();PreparedStatement ps=con.prepareStatement(live)){
            ps.setLong(1,botId);ps.setString(2,normalized);ps.setInt(3,safeLimit);
            try(ResultSet rs=ps.executeQuery()){while(rs.next()){
                String id=value(rs.getString("BuyOrderId"),"Live-Trade");
                long buyTime=timestamp(rs.getLong("buyunixtime"),
                        join(rs.getString("BuyDate"),rs.getString("BuyTime")));
                double buy=rs.getDouble("buy_price");
                if(buyTime>0&&buy>0)rows.add(new TradeMarkerRow(buyTime,buy,"BUY",id));
                long sellTime=timestamp(0,join(rs.getString("SellDate"),rs.getString("SellTime")));
                double sell=rs.getDouble("sell_price");
                if(sellTime>0&&sell>0)rows.add(new TradeMarkerRow(sellTime,sell,"SELL",id));
            }}
        }
        String paper="SELECT position_id,entry_price,exit_price,opened_at,closed_at "
                +"FROM paperPositions WHERE bot_id=? AND currency=? "
                +"ORDER BY opened_at DESC LIMIT ?";
        try(Connection con=open();PreparedStatement ps=con.prepareStatement(paper)){
            ps.setLong(1,botId);ps.setString(2,normalized);ps.setInt(3,safeLimit);
            try(ResultSet rs=ps.executeQuery()){while(rs.next()){
                String id=value(rs.getString("position_id"),"Paper-Trade");
                long buyTime=timestamp(0,rs.getString("opened_at"));double buy=rs.getDouble("entry_price");
                if(buyTime>0&&buy>0)rows.add(new TradeMarkerRow(buyTime,buy,"BUY","Paper · "+id));
                long sellTime=timestamp(0,rs.getString("closed_at"));double sell=rs.getDouble("exit_price");
                if(sellTime>0&&sell>0)rows.add(new TradeMarkerRow(sellTime,sell,"SELL","Paper · "+id));
            }}
        }
        rows.sort(Comparator.comparingLong(TradeMarkerRow::timestamp).reversed());
        return List.copyOf(rows.subList(0,Math.min(rows.size(),safeLimit)));
    }

    public List<WarningRow> loadWarnings(long botId) throws SQLException {
        List<WarningRow> rows = new ArrayList<>();
        for (AttemptRow attempt : loadUnresolvedAttempts(botId)) {
            rows.add(new WarningRow("KRITISCH", "Orderabgleich", attempt.currency(),
                    attempt.side() + "versuch " + attempt.attemptId()
                            + " benötigt einen manuellen Abgleich"
                            + (attempt.error() == null ? "" : ": " + attempt.error())));
        }
        String sql = "SELECT b.currency, b.buyStatus, c.updateTime, r.tickSize, r.stepSize, "
                + "r.minOrderAmount FROM botPairSettings b JOIN currency c ON c.currency = b.currency "
                + "LEFT JOIN tradingRules r ON r.currency = b.currency "
                + "WHERE b.bot_id = ? AND b.archived = 0";
        try (Connection con = open(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, botId);
            try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String currency = rs.getString("currency");
                if (!positive(rs.getString("tickSize"))
                        || !positive(rs.getString("stepSize"))
                        || !positive(rs.getString("minOrderAmount"))) {
                    rows.add(new WarningRow("KRITISCH", "Handelsregeln", currency,
                            "Tick-Größe, Mengenpräzision oder Mindestorder fehlen."));
                }
                String updateTime = rs.getString("updateTime");
                if (enabled(rs.getString("buyStatus"))
                        && (updateTime == null || updateTime.isBlank())) {
                    rows.add(new WarningRow("WARNUNG", "Marktdaten", currency,
                            "Für das aktive Paar liegt noch kein Signal-Zeitstempel vor."));
                }
            }
            }
        }
        return rows;
    }

    public void recordEvent(String severity, String category, String currency, String message)
            throws SQLException {
        recordEvent(BotRuntime.activeBotId(), severity, category, currency, message);
    }

    public void recordEvent(long botId, String severity, String category, String currency,
            String message) throws SQLException {
        String sql = "INSERT INTO activityLog (bot_id, severity, category, currency, message) "
                + "VALUES (?, ?, ?, ?, ?)";
        try (Connection con = open(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, botId);
            ps.setString(2, value(severity, "INFO"));
            ps.setString(3, value(category, "SYSTEM"));
            ps.setString(4, currency);
            ps.setString(5, truncate(value(message, "Ereignis"), 2_000));
            ps.executeUpdate();
        }
    }

    private static Connection open() throws SQLException {
        return com.oneofx.fusion.tradingbot.Database.SQLiteConnectionFactory.open(dbUrl.getoneOfX());
    }

    private static boolean positive(String value) {
        try {
            return value != null && Double.parseDouble(value) > 0.0;
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    private static boolean enabled(String value) {
        return "true".equalsIgnoreCase(value) || "1".equals(value);
    }

    private static String joinDateTime(String date, String time) {
        if (date == null) return value(time, "–");
        return time == null ? date : date + " " + time;
    }

    private static String join(String date,String time){
        if(date==null||date.isBlank())return time;
        return time==null||time.isBlank()?date:date+" "+time;
    }

    private static long timestamp(long epoch,String text){
        if(epoch>0)return epoch<10_000_000_000L?epoch*1000L:epoch;
        if(text==null||text.isBlank())return 0;
        String value=text.trim();
        try{return Instant.parse(value).toEpochMilli();}catch(DateTimeParseException ignored){}
        for(DateTimeFormatter formatter:List.of(
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
                DateTimeFormatter.ISO_LOCAL_DATE_TIME)){
            try{return LocalDateTime.parse(value,formatter).toInstant(ZoneOffset.UTC).toEpochMilli();}
            catch(DateTimeParseException ignored){}
        }
        return 0;
    }

    private static String statusLabel(int status) {
        return switch (status) {
            case 1 -> "Position offen";
            case 5 -> "Teilgefüllt";
            case 7 -> "Verkauf reserviert";
            case 8 -> "Verkauf läuft";
            default -> "Status " + status;
        };
    }

    private static String value(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String truncate(String value, int length) {
        return value.length() <= length ? value : value.substring(0, length);
    }

    public enum OrderOrigin { LIVE, PAPER }

    public record OpenOrderRow(String side, String currency, String orderId, double price,
            double quantity, int status, String statusText, OrderOrigin origin,
            OrderSide orderSide, String orderType, String referenceId) {
        public OpenOrderRow(String side, String currency, String orderId, double price,
                double quantity, int status, String statusText) {
            this(side, currency, orderId, price, quantity, status, statusText,
                    side != null && side.toLowerCase().contains("paper")
                            ? OrderOrigin.PAPER : OrderOrigin.LIVE,
                    side != null && side.toLowerCase().contains("verkauf")
                            ? OrderSide.SELL : OrderSide.BUY,
                    "LIMIT", orderId);
        }
    }

    public record PositionRow(String currency, String orderId, double buyPrice,
            double orderPrice, double quantity, double buyAmount, double profit,
            double unrealizedPnl, int status, String statusText, String openedAt) { }

    public record AttemptRow(String side, String currency, String attemptId,
            String exchangeOrderId, String state, String error, String updatedAt) { }

    public record ActivityRow(String createdAt, String severity, String category,
            String currency, String message) { }

    public record WarningRow(String severity, String category, String currency,
            String message) { }

    public record TradeMarkerRow(long timestamp,double price,String side,String label) { }
}
