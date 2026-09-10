package com.oneofx.fusion.tradingbot.backtest;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.time.Instant;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.oneofx.fusion.tradingbot.Database.DatabaseSchema;
import com.oneofx.fusion.tradingbot.strategy.StrategyDefinition;
import com.oneofx.fusion.tradingbot.strategy.StrategyRepository;
import com.oneofx.fusion.tradingbot.grid.GridMode;

public class BacktestRepositoryTest {
    private static final String[] PROPERTIES={"ONEOFX_TRADING_DB","ONEOFX_SETTINGS_DB","ONEOFX_WPD_DB"};
    private final String[] previous=new String[PROPERTIES.length];private File database;

    @Before public void setUp()throws Exception{database=File.createTempFile("oneofx-backtest-",".db");for(int i=0;i<PROPERTIES.length;i++){previous[i]=System.getProperty(PROPERTIES[i]);System.setProperty(PROPERTIES[i],database.getAbsolutePath());}DatabaseSchema.initialize();}
    @After public void tearDown(){for(int i=0;i<PROPERTIES.length;i++){if(previous[i]==null)System.clearProperty(PROPERTIES[i]);else System.setProperty(PROPERTIES[i],previous[i]);}if(database!=null)database.delete();}

    @Test public void persistsCompletedRunTradesAndMetrics()throws Exception{
        StrategyDefinition strategy=new StrategyRepository().create(1,"Backtest");BacktestRepository repository=new BacktestRepository();
        BacktestRequest request=new BacktestRequest(1,strategy.id(),"BTCEUR","1h",Instant.ofEpochMilli(1000),Instant.ofEpochMilli(10000),1000,100,0.25,0.1,2,3,0.01,0.001,5,100000,new BacktestExecutionConfig(GridMode.GEOMETRIC,1,1000,1000,10,4,"LIMIT",true,3,2,1.5,false,0.05,50,1));
        long id=repository.createRun(request,strategy.name());BacktestTrade trade=new BacktestTrade(1,2000,3000,100,105,1,0.5,4.5,"TAKE_PROFIT","Signal");
        BacktestOrderEvent event=new BacktestOrderEvent(1,"BT-1",1,1500,"PARTIAL_FILL",99,99,0.5,50,"LIMIT_TOUCHED");
        repository.complete(new BacktestResult(id,1000,1004.5,4.5,0.45,1.2,1,100,Double.POSITIVE_INFINITY,List.of(trade),List.of(event),List.of(new BacktestEquityPoint(3000,1004.5,0))));

        BacktestRepository.BacktestRunSummary loaded=repository.loadRecent(1,10).get(0);
        assertEquals("COMPLETED",loaded.status());assertEquals(4.5,loaded.netProfit(),0.000001);
        assertEquals("TAKE_PROFIT",repository.loadTrades(id).get(0).exitReason());
        assertEquals(1004.5,repository.loadEquity(id).get(0).equity(),0.000001);
        assertEquals("PARTIAL_FILL",repository.loadOrderEvents(id).get(0).event());
    }
}
