package com.oneofx.fusion.tradingbot.backtest;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

import com.oneofx.fusion.tradingbot.Database.dbUrl;

public final class BacktestRepository {
    public long createRun(BacktestRequest request,String strategyName)throws SQLException{
        String sql="INSERT INTO backtestRuns(bot_id,strategy_id,strategy_name,currency,base_timeframe,start_time,end_time,initial_capital,order_amount,fee_percent,slippage_percent,stop_loss_percent,take_profit_percent,execution_config) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
        try(Connection c=open();PreparedStatement p=c.prepareStatement(sql,Statement.RETURN_GENERATED_KEYS)){
            p.setLong(1,request.botId());p.setLong(2,request.strategyId());p.setString(3,strategyName);
            p.setString(4,request.currency());p.setString(5,request.baseTimeframe());
            p.setLong(6,request.start().toEpochMilli());p.setLong(7,request.end().toEpochMilli());
            p.setDouble(8,request.initialCapital());p.setDouble(9,request.orderAmount());
            p.setDouble(10,request.feePercent());p.setDouble(11,request.slippagePercent());
            p.setDouble(12,request.stopLossPercent());p.setDouble(13,request.takeProfitPercent());p.setString(14,request.execution().toString());p.executeUpdate();
            try(ResultSet r=p.getGeneratedKeys()){if(!r.next())throw new SQLException("Backtest-ID fehlt.");return r.getLong(1);}
        }
    }

    public void complete(BacktestResult result)throws SQLException{
        try(Connection c=open()){c.setAutoCommit(false);try{
            try(PreparedStatement p=c.prepareStatement("INSERT INTO backtestTrades(run_id,number,entry_time,exit_time,entry_price,exit_price,quantity,fees,pnl,exit_reason,explanation) VALUES(?,?,?,?,?,?,?,?,?,?,?)")){
                for(BacktestTrade trade:result.trades()){p.setLong(1,result.runId());p.setInt(2,trade.number());p.setLong(3,trade.entryTime());p.setLong(4,trade.exitTime());p.setDouble(5,trade.entryPrice());p.setDouble(6,trade.exitPrice());p.setDouble(7,trade.quantity());p.setDouble(8,trade.fees());p.setDouble(9,trade.pnl());p.setString(10,trade.exitReason());p.setString(11,trade.explanation());p.addBatch();}p.executeBatch();
            }
            try(PreparedStatement p=c.prepareStatement("INSERT INTO backtestEquity(run_id,timestamp,equity,drawdown_percent) VALUES(?,?,?,?)")){
                for(BacktestEquityPoint point:result.equity()){p.setLong(1,result.runId());p.setLong(2,point.timestamp());p.setDouble(3,point.equity());p.setDouble(4,point.drawdownPercent());p.addBatch();}p.executeBatch();
            }
            try(PreparedStatement p=c.prepareStatement("INSERT INTO backtestOrderEvents(run_id,number,order_reference,grid_level,timestamp,event,limit_price,fill_price,fill_quantity,remaining_amount,reason) VALUES(?,?,?,?,?,?,?,?,?,?,?)")){
                for(BacktestOrderEvent event:result.orderEvents()){p.setLong(1,result.runId());p.setInt(2,event.number());p.setString(3,event.orderReference());p.setInt(4,event.gridLevel());p.setLong(5,event.timestamp());p.setString(6,event.event());p.setDouble(7,event.limitPrice());p.setDouble(8,event.fillPrice());p.setDouble(9,event.fillQuantity());p.setDouble(10,event.remainingAmount());p.setString(11,event.reason());p.addBatch();}p.executeBatch();
            }
            try(PreparedStatement p=c.prepareStatement("UPDATE backtestRuns SET status='COMPLETED',final_capital=?,net_profit=?,return_percent=?,max_drawdown_percent=?,trade_count=?,win_rate_percent=?,profit_factor=?,completed_at=CURRENT_TIMESTAMP WHERE id=?")){
                p.setDouble(1,result.finalCapital());p.setDouble(2,result.netProfit());p.setDouble(3,result.returnPercent());p.setDouble(4,result.maxDrawdownPercent());p.setInt(5,result.tradeCount());p.setDouble(6,result.winRatePercent());if(Double.isFinite(result.profitFactor()))p.setDouble(7,result.profitFactor());else p.setNull(7,Types.REAL);p.setLong(8,result.runId());if(p.executeUpdate()!=1)throw new SQLException("Backtest-Lauf nicht gefunden.");
            }
            c.commit();
        }catch(SQLException|RuntimeException ex){c.rollback();throw ex;}}
    }

    public void fail(long runId,String message)throws SQLException{
        try(Connection c=open();PreparedStatement p=c.prepareStatement("UPDATE backtestRuns SET status='FAILED',error_message=?,completed_at=CURRENT_TIMESTAMP WHERE id=?")){p.setString(1,message==null?"Unbekannter Fehler":message);p.setLong(2,runId);p.executeUpdate();}
    }

    public List<BacktestRunSummary> loadRecent(long botId,int limit)throws SQLException{
        List<BacktestRunSummary> result=new ArrayList<>();String sql="SELECT id,strategy_name,currency,base_timeframe,start_time,end_time,status,initial_capital,final_capital,net_profit,return_percent,max_drawdown_percent,trade_count,win_rate_percent,profit_factor,error_message,created_at FROM backtestRuns WHERE bot_id=? ORDER BY id DESC LIMIT ?";
        try(Connection c=open();PreparedStatement p=c.prepareStatement(sql)){p.setLong(1,botId);p.setInt(2,Math.max(1,Math.min(limit,500)));try(ResultSet r=p.executeQuery()){while(r.next())result.add(new BacktestRunSummary(r.getLong("id"),r.getString("strategy_name"),r.getString("currency"),r.getString("base_timeframe"),r.getLong("start_time"),r.getLong("end_time"),r.getString("status"),r.getDouble("initial_capital"),nullableDouble(r,"final_capital"),nullableDouble(r,"net_profit"),nullableDouble(r,"return_percent"),nullableDouble(r,"max_drawdown_percent"),r.getInt("trade_count"),nullableDouble(r,"win_rate_percent"),nullableDouble(r,"profit_factor"),r.getString("error_message"),r.getString("created_at")));}}return result;
    }

    public List<BacktestTrade> loadTrades(long runId)throws SQLException{
        List<BacktestTrade> result=new ArrayList<>();try(Connection c=open();PreparedStatement p=c.prepareStatement("SELECT number,entry_time,exit_time,entry_price,exit_price,quantity,fees,pnl,exit_reason,explanation FROM backtestTrades WHERE run_id=? ORDER BY number")){p.setLong(1,runId);try(ResultSet r=p.executeQuery()){while(r.next())result.add(new BacktestTrade(r.getInt(1),r.getLong(2),r.getLong(3),r.getDouble(4),r.getDouble(5),r.getDouble(6),r.getDouble(7),r.getDouble(8),r.getString(9),r.getString(10)));}}return result;
    }

    public List<BacktestEquityPoint> loadEquity(long runId)throws SQLException{
        List<BacktestEquityPoint> result=new ArrayList<>();try(Connection c=open();PreparedStatement p=c.prepareStatement("SELECT timestamp,equity,drawdown_percent FROM backtestEquity WHERE run_id=? ORDER BY timestamp")){p.setLong(1,runId);try(ResultSet r=p.executeQuery()){while(r.next())result.add(new BacktestEquityPoint(r.getLong(1),r.getDouble(2),r.getDouble(3)));}}return result;
    }

    public List<BacktestOrderEvent> loadOrderEvents(long runId)throws SQLException{
        List<BacktestOrderEvent> result=new ArrayList<>();try(Connection c=open();PreparedStatement p=c.prepareStatement("SELECT number,order_reference,grid_level,timestamp,event,limit_price,fill_price,fill_quantity,remaining_amount,reason FROM backtestOrderEvents WHERE run_id=? ORDER BY number")){p.setLong(1,runId);try(ResultSet r=p.executeQuery()){while(r.next())result.add(new BacktestOrderEvent(r.getInt(1),r.getString(2),r.getInt(3),r.getLong(4),r.getString(5),r.getDouble(6),r.getDouble(7),r.getDouble(8),r.getDouble(9),r.getString(10)));}}return result;
    }

    private static Double nullableDouble(ResultSet r,String column)throws SQLException{double value=r.getDouble(column);return r.wasNull()?null:value;}
    private static Connection open()throws SQLException{return com.oneofx.fusion.tradingbot.Database.SQLiteConnectionFactory.open(dbUrl.getoneOfX());}

    public record BacktestRunSummary(long id,String strategyName,String currency,
            String timeframe,long startTime,long endTime,String status,double initialCapital,
            Double finalCapital,Double netProfit,Double returnPercent,Double maxDrawdownPercent,
            int tradeCount,Double winRatePercent,Double profitFactor,String error,String createdAt){
        @Override public String toString(){return "#"+id+" · "+strategyName+" · "+currency+" · "+status;}
    }
}
