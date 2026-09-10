package com.oneofx.fusion.tradingbot.desktop;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import com.oneofx.fusion.tradingbot.Database.dbUrl;
import com.oneofx.fusion.tradingbot.bot.BotRuntime;

/** Persistiert Baseconfig, Config Pools und deren Paarzuordnung. */
public final class BaseConfigRepository {
    private static final String COLUMNS = "buy_order_type, sell_order_type, max_buy_order_minutes, "
            + "max_sell_order_minutes, cooldown_minutes, take_profit_percent, "
            + "trailing_stop_buy_enabled, trailing_stop_buy_activation, trailing_stop_buy_rebound, "
            + "only_sell_with_profit, close_after_minutes, dca_enabled, dca_max_orders, "
            + "dca_trigger_percent, dca_size_multiplier";

    public BotBaseConfig loadBase(long botId) throws SQLException {
        ensureBase(botId);
        try (Connection con = open(); PreparedStatement ps = con.prepareStatement(
                "SELECT " + COLUMNS + " FROM botBaseConfig WHERE bot_id=?")) {
            ps.setLong(1, botId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new SQLException("Baseconfig nicht gefunden.");
                return read(botId, rs);
            }
        }
    }

    public BotBaseConfig loadEffective(String currency) throws SQLException {
        return loadEffective(BotRuntime.activeBotId(), currency);
    }

    public BotBaseConfig loadEffective(long botId, String currency) throws SQLException {
        String sql = "SELECT p.id, " + prefix(COLUMNS, "p") + " FROM botPairSettings b "
                + "LEFT JOIN configPools p ON p.id=b.config_pool_id AND p.archived=0 "
                + "WHERE b.bot_id=? AND b.currency=? AND b.archived=0";
        try (Connection con = open(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, botId); ps.setString(2, CurrencySettings.normalizeCurrency(currency));
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next() && rs.getObject("id") != null) return read(rs.getLong("id"), rs);
            }
        }
        return loadBase(botId);
    }

    public void saveBase(long botId, BotBaseConfig config) throws SQLException {
        ensureBase(botId);
        update("botBaseConfig", "bot_id", botId, config);
    }

    public List<ConfigPool> loadPools(long botId) throws SQLException {
        List<ConfigPool> result = new ArrayList<>();
        try (Connection con = open(); PreparedStatement ps = con.prepareStatement(
                "SELECT id, name, " + COLUMNS + " FROM configPools WHERE bot_id=? AND archived=0 ORDER BY name")) {
            ps.setLong(1, botId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) result.add(new ConfigPool(rs.getLong("id"), botId,
                        rs.getString("name"), read(rs.getLong("id"), rs)));
            }
        }
        return result;
    }

    public ConfigPool createPool(long botId, String name, BotBaseConfig source) throws SQLException {
        String normalized = normalizeName(name);
        String sql = "INSERT INTO configPools (bot_id,name," + COLUMNS + ") VALUES (?, ?, "
                + placeholders(15) + ")";
        try (Connection con = open(); PreparedStatement ps = con.prepareStatement(sql,
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, botId); ps.setString(2, normalized); bind(ps, 3, source);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (!keys.next()) throw new SQLException("Config-Pool-ID fehlt.");
                return new ConfigPool(keys.getLong(1), botId, normalized, source);
            }
        }
    }

    public void savePool(ConfigPool pool) throws SQLException {
        update("configPools", "id", pool.id(), pool.configuration());
        try (Connection con = open(); PreparedStatement ps = con.prepareStatement(
                "UPDATE configPools SET name=?, updated_at=CURRENT_TIMESTAMP WHERE id=? AND bot_id=? AND archived=0")) {
            ps.setString(1, normalizeName(pool.name())); ps.setLong(2, pool.id()); ps.setLong(3, pool.botId());
            if (ps.executeUpdate()!=1) throw new SQLException("Config Pool nicht gefunden.");
        }
    }

    public void archivePool(long botId, long poolId) throws SQLException {
        try (Connection con = open()) {
            con.setAutoCommit(false);
            try {
                try (PreparedStatement ps = con.prepareStatement(
                        "UPDATE botPairSettings SET config_pool_id=NULL WHERE bot_id=? AND config_pool_id=?")) {
                    ps.setLong(1, botId); ps.setLong(2, poolId); ps.executeUpdate();
                }
                try (PreparedStatement ps = con.prepareStatement(
                        "UPDATE configPools SET archived=1,updated_at=CURRENT_TIMESTAMP WHERE id=? AND bot_id=?")) {
                    ps.setLong(1, poolId); ps.setLong(2, botId);
                    if (ps.executeUpdate()!=1) throw new SQLException("Config Pool nicht gefunden.");
                }
                con.commit();
            } catch (SQLException ex) { con.rollback(); throw ex; }
        }
    }

    public Long loadAssignedPoolId(long botId, String currency) throws SQLException {
        try (Connection con = open(); PreparedStatement ps = con.prepareStatement(
                "SELECT config_pool_id FROM botPairSettings WHERE bot_id=? AND currency=?")) {
            ps.setLong(1, botId); ps.setString(2, CurrencySettings.normalizeCurrency(currency));
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                long value=rs.getLong(1); return rs.wasNull()?null:value;
            }
        }
    }

    public void assignPool(long botId, String currency, Long poolId) throws SQLException {
        String sql = "UPDATE botPairSettings SET config_pool_id=? WHERE bot_id=? AND currency=? AND archived=0 "
                + "AND (? IS NULL OR EXISTS(SELECT 1 FROM configPools WHERE id=? AND bot_id=? AND archived=0))";
        try (Connection con=open(); PreparedStatement ps=con.prepareStatement(sql)) {
            if(poolId==null) ps.setNull(1,java.sql.Types.INTEGER); else ps.setLong(1,poolId);
            ps.setLong(2,botId); ps.setString(3,CurrencySettings.normalizeCurrency(currency));
            if(poolId==null) ps.setNull(4,java.sql.Types.INTEGER); else ps.setLong(4,poolId);
            if(poolId==null) ps.setNull(5,java.sql.Types.INTEGER); else ps.setLong(5,poolId);
            ps.setLong(6,botId);
            if(ps.executeUpdate()!=1) throw new SQLException("Pool konnte dem Paar nicht zugeordnet werden.");
        }
    }

    /** Persistente Drop-und-Rebound-Entscheidung fuer Trailing Stop-Buy. */
    public boolean evaluateTrailingBuy(long botId, String currency, double price,
            double activationPercent, double reboundPercent) throws SQLException {
        if (!Double.isFinite(price) || price <= 0 || activationPercent <= 0 || reboundPercent <= 0) return false;
        String normalized=CurrencySettings.normalizeCurrency(currency);
        try(Connection con=open()){
            con.setAutoCommit(false);
            try {
                double high=price,low=price;boolean active=false;boolean exists=false;
                try(PreparedStatement ps=con.prepareStatement("SELECT high_price,low_price,active FROM trailingBuyState WHERE bot_id=? AND currency=?")){
                    ps.setLong(1,botId);ps.setString(2,normalized);
                    try(ResultSet rs=ps.executeQuery()){if(rs.next()){exists=true;high=rs.getDouble(1);low=rs.getDouble(2);active=rs.getInt(3)!=0;}}
                }
                if(!exists){try(PreparedStatement ps=con.prepareStatement("INSERT INTO trailingBuyState(bot_id,currency,high_price,low_price,active) VALUES(?,?,?,?,0)")){
                    ps.setLong(1,botId);ps.setString(2,normalized);ps.setDouble(3,price);ps.setDouble(4,price);ps.executeUpdate();}con.commit();return false;}
                boolean trigger=false;
                if(!active){high=Math.max(high,price);if(price<=high*(1-activationPercent/100.0)){active=true;low=price;}}
                else {low=Math.min(low,price);trigger=price>=low*(1+reboundPercent/100.0);}
                try(PreparedStatement ps=con.prepareStatement("UPDATE trailingBuyState SET high_price=?,low_price=?,active=?,updated_at=CURRENT_TIMESTAMP WHERE bot_id=? AND currency=?")){
                    ps.setDouble(1,high);ps.setDouble(2,low);ps.setInt(3,active?1:0);ps.setLong(4,botId);ps.setString(5,normalized);ps.executeUpdate();}
                con.commit();return trigger;
            }catch(SQLException ex){con.rollback();throw ex;}
        }
    }

    public void resetTrailingBuy(long botId,String currency,double price)throws SQLException{
        String sql="INSERT INTO trailingBuyState(bot_id,currency,high_price,low_price,active,updated_at) VALUES(?,?,?,?,0,CURRENT_TIMESTAMP) "
                +"ON CONFLICT(bot_id,currency) DO UPDATE SET high_price=excluded.high_price,low_price=excluded.low_price,active=0,updated_at=CURRENT_TIMESTAMP";
        try(Connection con=open();PreparedStatement ps=con.prepareStatement(sql)){
            ps.setLong(1,botId);ps.setString(2,CurrencySettings.normalizeCurrency(currency));ps.setDouble(3,price);ps.setDouble(4,price);ps.executeUpdate();}
    }

    private void ensureBase(long botId) throws SQLException {
        BotBaseConfig d=BotBaseConfig.defaults(botId);
        String sql="INSERT OR IGNORE INTO botBaseConfig (bot_id,"+COLUMNS+") VALUES (?,"+placeholders(15)+")";
        try(Connection con=open();PreparedStatement ps=con.prepareStatement(sql)){
            ps.setLong(1,botId);bind(ps,2,d);ps.executeUpdate();
        }
    }

    private static void update(String table,String key,long id,BotBaseConfig c)throws SQLException{
        String[] names=COLUMNS.split(", ");StringBuilder sql=new StringBuilder("UPDATE ").append(table).append(" SET ");
        for(int i=0;i<names.length;i++){if(i>0)sql.append(',');sql.append(names[i]).append("=?");}
        sql.append(",updated_at=CURRENT_TIMESTAMP WHERE ").append(key).append("=?");
        try(Connection con=open();PreparedStatement ps=con.prepareStatement(sql.toString())){
            int next=bind(ps,1,c);ps.setLong(next,id);if(ps.executeUpdate()!=1)throw new SQLException("Konfiguration nicht gefunden.");
        }
    }

    private static int bind(PreparedStatement ps,int i,BotBaseConfig c)throws SQLException{
        ps.setString(i++,c.buyOrderType());ps.setString(i++,c.sellOrderType());
        ps.setInt(i++,c.maxBuyOrderMinutes());ps.setInt(i++,c.maxSellOrderMinutes());ps.setInt(i++,c.cooldownMinutes());
        ps.setDouble(i++,c.takeProfitPercent());ps.setInt(i++,c.trailingStopBuyEnabled()?1:0);
        ps.setDouble(i++,c.trailingStopBuyActivationPercent());ps.setDouble(i++,c.trailingStopBuyReboundPercent());
        ps.setInt(i++,c.onlySellWithProfit()?1:0);ps.setInt(i++,c.closeAfterMinutes());ps.setInt(i++,c.dcaEnabled()?1:0);
        ps.setInt(i++,c.dcaMaxOrders());ps.setDouble(i++,c.dcaTriggerPercent());ps.setDouble(i++,c.dcaSizeMultiplier());return i;
    }

    private static BotBaseConfig read(long owner,ResultSet r)throws SQLException{return new BotBaseConfig(owner,
            r.getString("buy_order_type"),r.getString("sell_order_type"),r.getInt("max_buy_order_minutes"),
            r.getInt("max_sell_order_minutes"),r.getInt("cooldown_minutes"),r.getDouble("take_profit_percent"),
            r.getInt("trailing_stop_buy_enabled")!=0,r.getDouble("trailing_stop_buy_activation"),
            r.getDouble("trailing_stop_buy_rebound"),r.getInt("only_sell_with_profit")!=0,
            r.getInt("close_after_minutes"),r.getInt("dca_enabled")!=0,r.getInt("dca_max_orders"),
            r.getDouble("dca_trigger_percent"),r.getDouble("dca_size_multiplier"));}
    private static String prefix(String c,String p){String[] a=c.split(", ");StringBuilder b=new StringBuilder();for(int i=0;i<a.length;i++){if(i>0)b.append(", ");b.append(p).append('.').append(a[i]);}return b.toString();}
    private static String placeholders(int n){return String.join(",",java.util.Collections.nCopies(n,"?"));}
    private static String normalizeName(String n){if(n==null||n.isBlank())throw new IllegalArgumentException("Pool-Name fehlt.");String x=n.trim();if(x.length()>80)throw new IllegalArgumentException("Pool-Name ist zu lang.");return x;}
    private static Connection open()throws SQLException{Connection c=DriverManager.getConnection(dbUrl.getoneOfX());try(Statement s=c.createStatement()){s.execute("PRAGMA busy_timeout=5000");s.execute("PRAGMA foreign_keys=ON");}return c;}
}
