package com.oneofx.fusion.tradingbot.desktop;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Map;

import com.oneofx.fusion.tradingbot.Database.dbUrl;

/** Local position allocations, portfolio metrics and unified trade history. */
public final class PortfolioRepository {
    public void synchronizeAllocations(long botId) throws SQLException {
        try(Connection con=open()){
            con.setAutoCommit(false);
            try{
                List<SourcePosition> sources=loadSources(con,botId);
                Set<String> active=new HashSet<>();
                for(SourcePosition source:sources){
                    String key=source.kind()+"\0"+source.id();active.add(key);
                    try(PreparedStatement count=con.prepareStatement(
                            "SELECT COUNT(*) FROM positionAllocations WHERE bot_id=? "
                                    +"AND source_kind=? AND source_position_id=?")){
                        count.setLong(1,botId);count.setString(2,source.kind());count.setString(3,source.id());
                        try(ResultSet rs=count.executeQuery()){if(rs.next()&&rs.getInt(1)==0){
                            try(PreparedStatement insert=con.prepareStatement(
                                    "INSERT INTO positionAllocations(allocation_id,bot_id,source_kind,"
                                            +"source_position_id,currency,quantity,label) VALUES(?,?,?,?,?,?,?)")){
                                insert.setString(1,id("allocation"));insert.setLong(2,botId);
                                insert.setString(3,source.kind());insert.setString(4,source.id());
                                insert.setString(5,source.currency());insert.setDouble(6,source.quantity());
                                insert.setString(7,"Gesamtposition");insert.executeUpdate();
                            }
                        }}
                    }
                }
                try(PreparedStatement ps=con.prepareStatement(
                        "SELECT allocation_id,source_kind,source_position_id FROM positionAllocations WHERE bot_id=?")){
                    ps.setLong(1,botId);try(ResultSet rs=ps.executeQuery()){while(rs.next()){
                        if(!active.contains(rs.getString(2)+"\0"+rs.getString(3)))
                            try(PreparedStatement delete=con.prepareStatement(
                                    "DELETE FROM positionAllocations WHERE allocation_id=?")){
                                delete.setString(1,rs.getString(1));delete.executeUpdate();
                            }
                    }}
                }
                con.commit();
            }catch(SQLException|RuntimeException ex){rollback(con,ex);throw ex;}
        }
    }

    public List<AllocationRow> loadAllocations(long botId)throws SQLException{
        synchronizeAllocations(botId);
        String sql="SELECT a.allocation_id,a.source_kind,a.source_position_id,a.currency,a.quantity,"
                +"a.group_id,g.name group_name,a.reserved_strategy_id,s.name strategy_name,a.label "
                +"FROM positionAllocations a LEFT JOIN positionGroups g ON g.group_id=a.group_id "
                +"LEFT JOIN strategies s ON s.id=a.reserved_strategy_id WHERE a.bot_id=? "
                +"ORDER BY a.currency,COALESCE(g.name,''),a.created_at";
        List<AllocationRow> rows=new ArrayList<>();
        try(Connection con=open();PreparedStatement ps=con.prepareStatement(sql)){
            ps.setLong(1,botId);
            try(ResultSet rs=ps.executeQuery()){
                while(rs.next()){
                    rows.add(new AllocationRow(rs.getString(1),rs.getString(2),rs.getString(3),
                            rs.getString(4),rs.getDouble(5),rs.getString(6),rs.getString(7),
                            nullableLong(rs,8),rs.getString(9),rs.getString(10)));
                }
            }
        }return rows;
    }

    public void split(long botId,String allocationId,double firstQuantity)throws SQLException{
        if(!positive(firstQuantity))throw new IllegalArgumentException("Teilmenge muss größer als 0 sein.");
        try(Connection con=open()){con.setAutoCommit(false);try{
            AllocationData current=findAllocation(con,botId,allocationId);
            if(firstQuantity>=current.quantity()-0.0000000001)
                throw new IllegalArgumentException("Teilmenge muss kleiner als die vorhandene Menge sein.");
            double second=current.quantity()-firstQuantity;
            try(PreparedStatement update=con.prepareStatement(
                    "UPDATE positionAllocations SET quantity=?,label='Teil 1',updated_at=CURRENT_TIMESTAMP "
                            +"WHERE allocation_id=? AND bot_id=?")){
                update.setDouble(1,firstQuantity);update.setString(2,allocationId);update.setLong(3,botId);
                requireOne(update.executeUpdate(),"Positionsteilung");
            }
            try(PreparedStatement insert=con.prepareStatement(
                    "INSERT INTO positionAllocations(allocation_id,bot_id,source_kind,source_position_id,"
                            +"currency,quantity,group_id,reserved_strategy_id,label) VALUES(?,?,?,?,?,?,?,?,?)")){
                insert.setString(1,id("allocation"));insert.setLong(2,botId);insert.setString(3,current.kind());
                insert.setString(4,current.sourceId());insert.setString(5,current.currency());
                insert.setDouble(6,second);insert.setString(7,current.groupId());
                if(current.strategyId()==null)insert.setNull(8,java.sql.Types.INTEGER);else insert.setLong(8,current.strategyId());
                insert.setString(9,"Teil 2");insert.executeUpdate();
            }con.commit();
        }catch(SQLException|RuntimeException ex){rollback(con,ex);throw ex;}}
    }

    public String merge(long botId,List<String> allocationIds,String name)throws SQLException{
        if(allocationIds==null||allocationIds.size()<2)
            throw new IllegalArgumentException("Mindestens zwei Positionsteile auswählen.");
        String groupId=id("group");String currency=null;
        try(Connection con=open()){con.setAutoCommit(false);try{
            for(String allocationId:allocationIds){AllocationData row=findAllocation(con,botId,allocationId);
                if(currency==null)currency=row.currency();
                else if(!currency.equals(row.currency()))throw new IllegalArgumentException(
                        "Nur Positionen desselben Handelspaars können zusammengeführt werden.");
            }
            try(PreparedStatement insert=con.prepareStatement(
                    "INSERT INTO positionGroups(group_id,bot_id,currency,name) VALUES(?,?,?,?)")){
                insert.setString(1,groupId);insert.setLong(2,botId);insert.setString(3,currency);
                insert.setString(4,clean(name,"Positionsgruppe"));insert.executeUpdate();
            }
            try(PreparedStatement update=con.prepareStatement(
                    "UPDATE positionAllocations SET group_id=?,updated_at=CURRENT_TIMESTAMP "
                            +"WHERE allocation_id=? AND bot_id=?")){
                for(String allocationId:allocationIds){update.setString(1,groupId);
                    update.setString(2,allocationId);update.setLong(3,botId);update.addBatch();}
                update.executeBatch();
            }con.commit();return groupId;
        }catch(SQLException|RuntimeException ex){rollback(con,ex);throw ex;}}
    }

    public void reserve(long botId,List<String> allocationIds,Long strategyId)throws SQLException{
        if(allocationIds==null||allocationIds.isEmpty())throw new IllegalArgumentException("Keine Position ausgewählt.");
        try(Connection con=open()){con.setAutoCommit(false);try(PreparedStatement ps=con.prepareStatement(
                "UPDATE positionAllocations SET reserved_strategy_id=?,updated_at=CURRENT_TIMESTAMP "
                        +"WHERE allocation_id=? AND bot_id=?")){
            for(String id:allocationIds){if(strategyId==null)ps.setNull(1,java.sql.Types.INTEGER);else ps.setLong(1,strategyId);
                ps.setString(2,id);ps.setLong(3,botId);requireOne(ps.executeUpdate(),"Strategiereservierung");}
            con.commit();
        }catch(SQLException|RuntimeException ex){rollback(con,ex);throw ex;}}
    }

    public List<TradeHistoryRow> loadTradeHistory(long botId,String search,int limit)throws SQLException{
        String query=search==null?"":search.trim().toLowerCase(Locale.ROOT);
        List<TradeHistoryRow> rows=new ArrayList<>();
        String live="SELECT 'LIVE',h.currency,h.BuyOrderId,h.BuyDate||' '||h.BuyTime,"
                +"h.SellDate||' '||h.SellTime,COALESCE(NULLIF(h.BuyPrice,0),h.OrigPrice,0),"
                +"COALESCE(h.SellPrice,0),COALESCE(h.Quantity,0),COALESCE(h.BuyFee,0)+COALESCE(h.SellFee,0),"
                +"COALESCE(h.GewinnAfterTax,0)+COALESCE(h.LossAfterTax,0),"
                +"COALESCE(p.unrealisierterPNL,0),CASE WHEN h.Status=1 THEN 'CLOSED' ELSE 'OPEN' END,"
                +"COALESCE(s.name,'–') FROM historyPosition h LEFT JOIN positions p "
                +"ON p.BuyOrderId=h.BuyOrderId AND p.bot_id=h.bot_id LEFT JOIN strategies s "
                +"ON s.id=p.strategy_id WHERE h.bot_id=?";
        String paper="SELECT 'PAPER',p.currency,p.position_id,p.opened_at,p.closed_at,p.entry_price,"
                +"COALESCE(p.exit_price,0),p.quantity,p.fees,COALESCE(p.realized_pnl,0),"
                +"COALESCE(p.unrealized_pnl,0),p.status,COALESCE(s.name,'–') FROM paperPositions p "
                +"LEFT JOIN strategies s ON s.id=p.strategy_id WHERE p.bot_id=?";
        try(Connection con=open()){
            readHistory(con,live,botId,rows);readHistory(con,paper,botId,rows);
        }
        rows=rows.stream().filter(row->query.isEmpty()||row.searchable().contains(query))
                .sorted((a,b)->b.openedAt().compareTo(a.openedAt())).limit(Math.max(1,Math.min(limit,10000))).toList();
        return rows;
    }

    public PortfolioSummary loadSummary(long botId)throws SQLException{
        List<TradeHistoryRow> rows=loadTradeHistory(botId,"",10000);
        return new PortfolioSummary(rows.stream().mapToDouble(TradeHistoryRow::realizedPnl).sum(),
                rows.stream().mapToDouble(TradeHistoryRow::unrealizedPnl).sum(),
                rows.stream().mapToDouble(TradeHistoryRow::fees).sum(),
                rows.stream().filter(row->!"CLOSED".equals(row.status()))
                        .mapToDouble(row->row.entryPrice()*row.quantity()).sum(),
                rows.stream().filter(row->"CLOSED".equals(row.status())).count(),
                rows.stream().filter(row->!"CLOSED".equals(row.status())).count());
    }

    public List<PerformanceRow> loadPerformance(long botId,String dimension,int days)throws SQLException{
        long cutoff=days<=0?Long.MIN_VALUE:System.currentTimeMillis()-days*86_400_000L;
        Map<String,List<TradeHistoryRow>> groups=new LinkedHashMap<>();
        for(TradeHistoryRow row:loadTradeHistory(botId,"",10000)){
            if(!"CLOSED".equals(row.status())||timestamp(row.closedAt())<cutoff)continue;
            String key=switch(dimension==null?"PAIR":dimension){
                case "BOT"->"Bot #"+botId;case "STRATEGY"->row.strategy();
                case "MODE"->row.origin();default->row.currency();};
            groups.computeIfAbsent(key,ignored->new ArrayList<>()).add(row);
        }
        List<PerformanceRow> result=new ArrayList<>();
        for(Map.Entry<String,List<TradeHistoryRow>> entry:groups.entrySet()){
            List<TradeHistoryRow> values=entry.getValue();double pnl=values.stream()
                    .mapToDouble(TradeHistoryRow::realizedPnl).sum();double fees=values.stream()
                    .mapToDouble(TradeHistoryRow::fees).sum();long wins=values.stream()
                    .filter(row->row.realizedPnl()>0).count();
            result.add(new PerformanceRow(entry.getKey(),values.size(),pnl,fees,
                    values.isEmpty()?0:wins*100.0/values.size()));
        }
        return result.stream().sorted((a,b)->Double.compare(b.realizedPnl(),a.realizedPnl())).toList();
    }

    public Path exportCsv(Path target,List<TradeHistoryRow> rows)throws IOException{
        List<String> lines=new ArrayList<>();
        lines.add("Modus;Paar;Position;Eröffnet;Geschlossen;Kaufpreis;Verkaufspreis;Menge;Gebühren;Realisiert;Unrealisiert;Status;Strategie");
        for(TradeHistoryRow row:rows)lines.add(String.join(";",csv(row.origin()),csv(row.currency()),
                csv(row.positionId()),csv(row.openedAt()),csv(row.closedAt()),number(row.entryPrice()),
                number(row.exitPrice()),number(row.quantity()),number(row.fees()),number(row.realizedPnl()),
                number(row.unrealizedPnl()),csv(row.status()),csv(row.strategy())));
        Files.write(target,lines,StandardCharsets.UTF_8);return target;
    }

    private static List<SourcePosition> loadSources(Connection con,long botId)throws SQLException{
        List<SourcePosition> rows=new ArrayList<>();
        try(PreparedStatement ps=con.prepareStatement("SELECT BuyOrderId,currency,quantity FROM positions "
                +"WHERE bot_id=? AND Status IN(1,5,7,8)")){ps.setLong(1,botId);
            try(ResultSet rs=ps.executeQuery()){while(rs.next())rows.add(new SourcePosition(
                    "LIVE",rs.getString(1),rs.getString(2),rs.getDouble(3)));}}
        try(PreparedStatement ps=con.prepareStatement("SELECT position_id,currency,quantity FROM paperPositions "
                +"WHERE bot_id=? AND status IN('OPEN','SELL_PENDING')")){ps.setLong(1,botId);
            try(ResultSet rs=ps.executeQuery()){while(rs.next())rows.add(new SourcePosition(
                    "PAPER",rs.getString(1),rs.getString(2),rs.getDouble(3)));}}
        return rows;
    }
    private static void readHistory(Connection con,String sql,long botId,List<TradeHistoryRow> rows)throws SQLException{
        try(PreparedStatement ps=con.prepareStatement(sql)){ps.setLong(1,botId);
            try(ResultSet rs=ps.executeQuery()){while(rs.next())rows.add(new TradeHistoryRow(
                    rs.getString(1),rs.getString(2),rs.getString(3),value(rs.getString(4)),
                    value(rs.getString(5)),rs.getDouble(6),rs.getDouble(7),rs.getDouble(8),
                    rs.getDouble(9),rs.getDouble(10),rs.getDouble(11),rs.getString(12),rs.getString(13)));}}
    }
    private static AllocationData findAllocation(Connection con,long botId,String id)throws SQLException{
        try(PreparedStatement ps=con.prepareStatement("SELECT source_kind,source_position_id,currency,quantity,"
                +"group_id,reserved_strategy_id FROM positionAllocations WHERE allocation_id=? AND bot_id=?")){
            ps.setString(1,id);ps.setLong(2,botId);try(ResultSet rs=ps.executeQuery()){
                if(!rs.next())throw new SQLException("Positionsteil nicht gefunden.");
                return new AllocationData(rs.getString(1),rs.getString(2),rs.getString(3),rs.getDouble(4),
                        rs.getString(5),nullableLong(rs,6));}}
    }
    private static Long nullableLong(ResultSet rs,int column)throws SQLException{
        long value=rs.getLong(column);return rs.wasNull()?null:value;
    }
    private static Connection open()throws SQLException{return com.oneofx.fusion.tradingbot.Database.SQLiteConnectionFactory.open(dbUrl.getoneOfX());}
    private static void rollback(Connection con,Exception ex){try{con.rollback();}catch(SQLException rollback){ex.addSuppressed(rollback);}}
    private static void requireOne(int rows,String action)throws SQLException{if(rows!=1)throw new SQLException(action+" änderte "+rows+" Datensätze.");}
    private static String clean(String text,String fallback){return text==null||text.isBlank()?fallback:text.trim();}
    private static String id(String prefix){return prefix+"-"+UUID.randomUUID();}
    private static boolean positive(double value){return Double.isFinite(value)&&value>0;}
    private static String value(String value){return value==null||value.isBlank()?"–":value;}
    private static String csv(String value){String safe=value==null?"":value.replace("\"","\"\"");return "\""+safe+"\"";}
    private static String number(double value){return Double.toString(value);}
    private static long timestamp(String value){if(value==null||value.isBlank()||"–".equals(value))return 0;
        for(DateTimeFormatter formatter:List.of(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss"),
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),DateTimeFormatter.ISO_LOCAL_DATE_TIME))
            try{return LocalDateTime.parse(value.trim(),formatter).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();}
            catch(DateTimeParseException ignored){}return 0;}

    private record SourcePosition(String kind,String id,String currency,double quantity){}
    private record AllocationData(String kind,String sourceId,String currency,double quantity,String groupId,Long strategyId){}
    public record AllocationRow(String allocationId,String sourceKind,String sourcePositionId,String currency,
            double quantity,String groupId,String groupName,Long strategyId,String strategyName,String label){}
    public record TradeHistoryRow(String origin,String currency,String positionId,String openedAt,String closedAt,
            double entryPrice,double exitPrice,double quantity,double fees,double realizedPnl,double unrealizedPnl,
            String status,String strategy){private String searchable(){return (origin+" "+currency+" "+positionId+" "+status+" "+strategy).toLowerCase(Locale.ROOT);}}
    public record PortfolioSummary(double realizedPnl,double unrealizedPnl,double fees,double committedCapital,
            long closedTrades,long openPositions){}
    public record PerformanceRow(String group,int trades,double realizedPnl,double fees,double winRatePercent){}
}
