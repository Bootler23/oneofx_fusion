package com.oneofx.fusion.tradingbot.strategy;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.oneofx.fusion.tradingbot.Database.dbUrl;
import com.oneofx.fusion.tradingbot.desktop.CurrencySettings;
import com.oneofx.fusion.tradingbot.strategy.StrategyNode.Action;
import com.oneofx.fusion.tradingbot.strategy.StrategyNode.Comparator;
import com.oneofx.fusion.tradingbot.strategy.StrategyNode.Indicator;
import com.oneofx.fusion.tradingbot.strategy.StrategyNode.Logic;
import com.oneofx.fusion.tradingbot.strategy.StrategyNode.NodeType;

public final class StrategyRepository {
    public List<StrategyDefinition> loadAll(long botId) throws SQLException {
        List<StrategyDefinition> result=new ArrayList<>();
        try(Connection c=open();PreparedStatement p=c.prepareStatement("SELECT id,bot_id,name,minimum_confirmations,enabled FROM strategies WHERE bot_id=? AND archived=0 ORDER BY name")){
            p.setLong(1,botId);try(ResultSet r=p.executeQuery()){while(r.next())result.add(readDefinition(r));}}
        return result;
    }

    public StrategyDefinition create(long botId,String name)throws SQLException{
        StrategyDefinition value=new StrategyDefinition(0,botId,name,1,true);
        try(Connection c=open();PreparedStatement p=c.prepareStatement("INSERT INTO strategies(bot_id,name) VALUES(?,?)",Statement.RETURN_GENERATED_KEYS)){
            p.setLong(1,botId);p.setString(2,value.name());p.executeUpdate();try(ResultSet r=p.getGeneratedKeys()){if(!r.next())throw new SQLException("Strategie-ID fehlt.");return new StrategyDefinition(r.getLong(1),botId,value.name(),1,true);}}
    }

    public StrategyDefinition duplicate(long strategyId,String name)throws SQLException{
        try(Connection c=open()){c.setAutoCommit(false);try{
            StrategyDefinition source=loadDefinition(c,strategyId);StrategyDefinition target;
            try(PreparedStatement p=c.prepareStatement("INSERT INTO strategies(bot_id,name,minimum_confirmations,enabled) VALUES(?,?,?,?)",Statement.RETURN_GENERATED_KEYS)){
                p.setLong(1,source.botId());p.setString(2,new StrategyDefinition(0,source.botId(),name,source.minimumConfirmations(),source.enabled()).name());p.setInt(3,source.minimumConfirmations());p.setInt(4,source.enabled()?1:0);p.executeUpdate();try(ResultSet r=p.getGeneratedKeys()){r.next();target=new StrategyDefinition(r.getLong(1),source.botId(),name,source.minimumConfirmations(),source.enabled());}}
            copyChildren(c,strategyId,target.id(),null,null);c.commit();return target;
        }catch(SQLException|RuntimeException ex){c.rollback();throw ex;}}
    }

    private void copyChildren(Connection c,long sourceStrategy,long targetStrategy,Long sourceParent,Long targetParent)throws SQLException{
        String sql="SELECT * FROM strategyNodes WHERE strategy_id=? AND "+(sourceParent==null?"parent_id IS NULL":"parent_id=?")+" ORDER BY position,id";
        try(PreparedStatement p=c.prepareStatement(sql)){p.setLong(1,sourceStrategy);if(sourceParent!=null)p.setLong(2,sourceParent);try(ResultSet r=p.executeQuery()){
            List<StrategyNode> nodes=new ArrayList<>();while(r.next())nodes.add(readNode(r));
            for(StrategyNode n:nodes){long newId=insertNode(c,targetStrategy,targetParent,n);if(n.type()==NodeType.GROUP)copyChildren(c,sourceStrategy,targetStrategy,n.id(),newId);}}}
    }

    public void saveDefinition(StrategyDefinition d)throws SQLException{
        try(Connection c=open();PreparedStatement p=c.prepareStatement("UPDATE strategies SET name=?,minimum_confirmations=?,enabled=?,updated_at=CURRENT_TIMESTAMP WHERE id=? AND bot_id=? AND archived=0")){
            p.setString(1,d.name());p.setInt(2,d.minimumConfirmations());p.setInt(3,d.enabled()?1:0);p.setLong(4,d.id());p.setLong(5,d.botId());if(p.executeUpdate()!=1)throw new SQLException("Strategie nicht gefunden.");}
    }

    public void archive(long botId,long strategyId)throws SQLException{
        try(Connection c=open()){c.setAutoCommit(false);try{
            execute(c,"DELETE FROM botStrategyAssignments WHERE bot_id=? AND strategy_id=?",botId,strategyId);
            execute(c,"DELETE FROM marketStrategyAssignments WHERE bot_id=? AND strategy_id=?",botId,strategyId);
            execute(c,"UPDATE configPools SET strategy_id=NULL WHERE bot_id=? AND strategy_id=?",botId,strategyId);
            execute(c,"UPDATE botPairSettings SET strategy_id=NULL WHERE bot_id=? AND strategy_id=?",botId,strategyId);
            try(PreparedStatement p=c.prepareStatement("UPDATE strategies SET archived=1,enabled=0 WHERE id=? AND bot_id=?")){p.setLong(1,strategyId);p.setLong(2,botId);if(p.executeUpdate()!=1)throw new SQLException("Strategie nicht gefunden.");}c.commit();
        }catch(SQLException ex){c.rollback();throw ex;}}
    }

    public List<StrategyNode> loadNodes(long strategyId)throws SQLException{
        List<StrategyNode> result=new ArrayList<>();try(Connection c=open();PreparedStatement p=c.prepareStatement("SELECT * FROM strategyNodes WHERE strategy_id=? ORDER BY parent_id,position,id")){p.setLong(1,strategyId);try(ResultSet r=p.executeQuery()){while(r.next())result.add(readNode(r));}}return result;
    }

    public StrategyNode addGroup(long strategyId,Long parentId,Action action,Logic logic)throws SQLException{
        StrategyNode n=new StrategyNode(0,strategyId,parentId,nextPosition(strategyId,parentId),NodeType.GROUP,action,logic,null,null,null,0,"1d",14,26);return withId(n,insertNode(open(),strategyId,parentId,n));
    }

    public StrategyNode addCondition(long strategyId,Long parentId,Action action,Indicator left,
            Comparator comparator,Indicator right,double value,String timeframe,int period,int secondary)throws SQLException{
        StrategyNode n=new StrategyNode(0,strategyId,parentId,nextPosition(strategyId,parentId),NodeType.CONDITION,action,Logic.AND,left,comparator,right,value,timeframe,period,secondary);try(Connection c=open()){return withId(n,insertNode(c,strategyId,parentId,n));}
    }

    public void deleteNode(long nodeId)throws SQLException{try(Connection c=open();PreparedStatement p=c.prepareStatement("DELETE FROM strategyNodes WHERE id=?")){p.setLong(1,nodeId);p.executeUpdate();}}

    public void updateNode(StrategyNode n)throws SQLException{
        try(Connection c=open();PreparedStatement p=c.prepareStatement("UPDATE strategyNodes SET parent_id=?,position=?,node_type=?,action=?,logical_operator=?,left_indicator=?,comparator=?,right_indicator=?,compare_value=?,timeframe=?,period=?,secondary_period=? WHERE id=? AND strategy_id=?")){
            nullable(p,1,n.parentId());p.setInt(2,n.position());p.setString(3,n.type().name());p.setString(4,n.action().name());p.setString(5,n.logic().name());nullable(p,6,n.leftIndicator());nullable(p,7,n.comparator());nullable(p,8,n.rightIndicator());p.setDouble(9,n.compareValue());p.setString(10,n.timeframe());p.setInt(11,n.period());p.setInt(12,n.secondaryPeriod());p.setLong(13,n.id());p.setLong(14,n.strategyId());if(p.executeUpdate()!=1)throw new SQLException("Regel nicht gefunden.");}
    }

    public void assignBot(long botId,Long strategyId)throws SQLException{
        try(Connection c=open()){if(strategyId==null){try(PreparedStatement p=c.prepareStatement("DELETE FROM botStrategyAssignments WHERE bot_id=?")){p.setLong(1,botId);p.executeUpdate();}}else{requireOwned(c,botId,strategyId);try(PreparedStatement p=c.prepareStatement("INSERT INTO botStrategyAssignments(bot_id,strategy_id) VALUES(?,?) ON CONFLICT(bot_id) DO UPDATE SET strategy_id=excluded.strategy_id")){p.setLong(1,botId);p.setLong(2,strategyId);p.executeUpdate();}}}
    }
    public void assignPool(long botId,long poolId,Long strategyId)throws SQLException{assignColumn("configPools","id",botId,poolId,strategyId);}
    public void assignPair(long botId,String currency,Long strategyId)throws SQLException{
        try(Connection c=open()){if(strategyId!=null)requireOwned(c,botId,strategyId);try(PreparedStatement p=c.prepareStatement("UPDATE botPairSettings SET strategy_id=? WHERE bot_id=? AND currency=? AND archived=0")){nullable(p,1,strategyId);p.setLong(2,botId);p.setString(3,CurrencySettings.normalizeCurrency(currency));if(p.executeUpdate()!=1)throw new SQLException("Paar nicht gefunden.");}}
    }

    public void assignMarketRegime(long botId,String regime,Long strategyId)throws SQLException{
        if(regime==null||regime.isBlank())throw new IllegalArgumentException("Marktphase fehlt.");
        try(Connection c=open()){if(strategyId==null){try(PreparedStatement p=c.prepareStatement("DELETE FROM marketStrategyAssignments WHERE bot_id=? AND regime=?")){p.setLong(1,botId);p.setString(2,regime);p.executeUpdate();}}else{requireOwned(c,botId,strategyId);try(PreparedStatement p=c.prepareStatement("INSERT INTO marketStrategyAssignments(bot_id,regime,strategy_id) VALUES(?,?,?) ON CONFLICT(bot_id,regime) DO UPDATE SET strategy_id=excluded.strategy_id")){p.setLong(1,botId);p.setString(2,regime);p.setLong(3,strategyId);p.executeUpdate();}}}
    }

    public Long loadBotAssignment(long botId)throws SQLException{return scalarLong("SELECT strategy_id FROM botStrategyAssignments WHERE bot_id=?",botId,null);}
    public Long loadPoolAssignment(long botId,long poolId)throws SQLException{return scalarLong("SELECT strategy_id FROM configPools WHERE bot_id=? AND id=? AND archived=0",botId,poolId);}
    public Long loadPairAssignment(long botId,String currency)throws SQLException{return scalarLong("SELECT strategy_id FROM botPairSettings WHERE bot_id=? AND currency=? AND archived=0",botId,CurrencySettings.normalizeCurrency(currency));}
    public Long loadMarketAssignment(long botId,String regime)throws SQLException{return scalarLong("SELECT strategy_id FROM marketStrategyAssignments WHERE bot_id=? AND regime=?",botId,regime);}

    /** Prioritaet: Paar, Config Pool, Marktphase, Bot. */
    public Optional<StrategyDefinition> resolve(long botId,String currency)throws SQLException{
        return resolve(botId,currency,null);
    }

    public Optional<StrategyDefinition> resolve(long botId,String currency,String regime)throws SQLException{
        String sql="SELECT COALESCE(bp.strategy_id,cp.strategy_id,ma.strategy_id,ba.strategy_id) strategy_id FROM botPairSettings bp LEFT JOIN configPools cp ON cp.id=bp.config_pool_id AND cp.archived=0 LEFT JOIN marketStrategyAssignments ma ON ma.bot_id=bp.bot_id AND ma.regime=? LEFT JOIN botStrategyAssignments ba ON ba.bot_id=bp.bot_id WHERE bp.bot_id=? AND bp.currency=? AND bp.archived=0";
        try(Connection c=open();PreparedStatement p=c.prepareStatement(sql)){p.setString(1,regime);p.setLong(2,botId);p.setString(3,CurrencySettings.normalizeCurrency(currency));try(ResultSet r=p.executeQuery()){if(!r.next()||r.getObject(1)==null)return Optional.empty();StrategyDefinition d=loadDefinition(c,r.getLong(1));return d.enabled()?Optional.of(d):Optional.empty();}}
    }

    public void log(long botId,StrategyEvaluation e,String currency)throws SQLException{
        try(Connection c=open();PreparedStatement p=c.prepareStatement("INSERT INTO strategyEvaluationLog(bot_id,strategy_id,currency,buy_signal,sell_signal,blocked,confirmations,explanation) VALUES(?,?,?,?,?,?,?,?)")){
            p.setLong(1,botId);p.setLong(2,e.strategyId());p.setString(3,currency);p.setInt(4,e.buy()?1:0);p.setInt(5,e.sell()?1:0);p.setInt(6,e.blocked()?1:0);p.setInt(7,e.confirmations());p.setString(8,String.join(" | ",e.explanations()));p.executeUpdate();}
    }

    private void assignColumn(String table,String key,long botId,long targetId,Long strategyId)throws SQLException{try(Connection c=open()){if(strategyId!=null)requireOwned(c,botId,strategyId);try(PreparedStatement p=c.prepareStatement("UPDATE "+table+" SET strategy_id=? WHERE "+key+"=? AND bot_id=?")){nullable(p,1,strategyId);p.setLong(2,targetId);p.setLong(3,botId);if(p.executeUpdate()!=1)throw new SQLException("Ziel nicht gefunden.");}}}
    private static void requireOwned(Connection c,long botId,long strategyId)throws SQLException{try(PreparedStatement p=c.prepareStatement("SELECT 1 FROM strategies WHERE id=? AND bot_id=? AND archived=0")){p.setLong(1,strategyId);p.setLong(2,botId);try(ResultSet r=p.executeQuery()){if(!r.next())throw new SQLException("Strategie gehört nicht zu diesem Bot.");}}}
    private int nextPosition(long strategyId,Long parent)throws SQLException{String sql="SELECT COALESCE(MAX(position),-1)+1 FROM strategyNodes WHERE strategy_id=? AND "+(parent==null?"parent_id IS NULL":"parent_id=?");try(Connection c=open();PreparedStatement p=c.prepareStatement(sql)){p.setLong(1,strategyId);if(parent!=null)p.setLong(2,parent);try(ResultSet r=p.executeQuery()){return r.next()?r.getInt(1):0;}}}
    private static long insertNode(Connection c,long strategy,Long parent,StrategyNode n)throws SQLException{boolean close=c.getAutoCommit();try(PreparedStatement p=c.prepareStatement("INSERT INTO strategyNodes(strategy_id,parent_id,position,node_type,action,logical_operator,left_indicator,comparator,right_indicator,compare_value,timeframe,period,secondary_period) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)",Statement.RETURN_GENERATED_KEYS)){p.setLong(1,strategy);nullable(p,2,parent);p.setInt(3,n.position());p.setString(4,n.type().name());p.setString(5,n.action().name());p.setString(6,n.logic().name());nullable(p,7,n.leftIndicator());nullable(p,8,n.comparator());nullable(p,9,n.rightIndicator());p.setDouble(10,n.compareValue());p.setString(11,n.timeframe());p.setInt(12,n.period());p.setInt(13,n.secondaryPeriod());p.executeUpdate();try(ResultSet r=p.getGeneratedKeys()){if(!r.next())throw new SQLException("Regel-ID fehlt.");return r.getLong(1);}}finally{if(close)c.close();}}
    private static StrategyNode withId(StrategyNode n,long id){return new StrategyNode(id,n.strategyId(),n.parentId(),n.position(),n.type(),n.action(),n.logic(),n.leftIndicator(),n.comparator(),n.rightIndicator(),n.compareValue(),n.timeframe(),n.period(),n.secondaryPeriod());}
    private static StrategyDefinition loadDefinition(Connection c,long id)throws SQLException{try(PreparedStatement p=c.prepareStatement("SELECT id,bot_id,name,minimum_confirmations,enabled FROM strategies WHERE id=? AND archived=0")){p.setLong(1,id);try(ResultSet r=p.executeQuery()){if(!r.next())throw new SQLException("Strategie nicht gefunden.");return readDefinition(r);}}}
    private static StrategyDefinition readDefinition(ResultSet r)throws SQLException{return new StrategyDefinition(r.getLong("id"),r.getLong("bot_id"),r.getString("name"),r.getInt("minimum_confirmations"),r.getInt("enabled")!=0);}
    private static StrategyNode readNode(ResultSet r)throws SQLException{long parent=r.getLong("parent_id");return new StrategyNode(r.getLong("id"),r.getLong("strategy_id"),r.wasNull()?null:parent,r.getInt("position"),NodeType.valueOf(r.getString("node_type")),Action.valueOf(r.getString("action")),Logic.valueOf(r.getString("logical_operator")),enumValue(Indicator.class,r.getString("left_indicator")),enumValue(Comparator.class,r.getString("comparator")),enumValue(Indicator.class,r.getString("right_indicator")),r.getDouble("compare_value"),r.getString("timeframe"),r.getInt("period"),r.getInt("secondary_period"));}
    private static <E extends Enum<E>> E enumValue(Class<E> c,String v){return v==null?null:Enum.valueOf(c,v);}
    private static void nullable(PreparedStatement p,int i,Object v)throws SQLException{if(v==null)p.setNull(i,Types.NULL);else if(v instanceof Number n)p.setLong(i,n.longValue());else p.setString(i,v instanceof Enum<?> e?e.name():v.toString());}
    private static void execute(Connection c,String sql,long a,long b)throws SQLException{try(PreparedStatement p=c.prepareStatement(sql)){p.setLong(1,a);p.setLong(2,b);p.executeUpdate();}}
    private static Long scalarLong(String sql,long botId,Object value)throws SQLException{try(Connection c=open();PreparedStatement p=c.prepareStatement(sql)){p.setLong(1,botId);if(value!=null){if(value instanceof Number n)p.setLong(2,n.longValue());else p.setString(2,value.toString());}try(ResultSet r=p.executeQuery()){if(!r.next()||r.getObject(1)==null)return null;return r.getLong(1);}}}
    private static Connection open()throws SQLException{return com.oneofx.fusion.tradingbot.Database.SQLiteConnectionFactory.open(dbUrl.getoneOfX());}
}
