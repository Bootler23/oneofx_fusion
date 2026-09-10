package com.oneofx.fusion.tradingbot.backtest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.Test;

import com.oneofx.fusion.client.model.Candlestick;
import com.oneofx.fusion.tradingbot.backtest.BacktestOptimizationService.StrategyCandidate;
import com.oneofx.fusion.tradingbot.grid.GridMode;
import com.oneofx.fusion.tradingbot.strategy.StrategyDefinition;
import com.oneofx.fusion.tradingbot.strategy.StrategyNode;
import com.oneofx.fusion.tradingbot.strategy.StrategyNode.Action;
import com.oneofx.fusion.tradingbot.strategy.StrategyNode.Comparator;
import com.oneofx.fusion.tradingbot.strategy.StrategyNode.Indicator;
import com.oneofx.fusion.tradingbot.strategy.StrategyNode.Logic;
import com.oneofx.fusion.tradingbot.strategy.StrategyNode.NodeType;

public class BacktestOptimizationServiceTest {
    private static final long START=1_700_000_000_000L;

    @Test public void ranksOnlyFromTrainingAndWalksForwardThroughUntouchedWindows(){
        BacktestRequest base=new BacktestRequest(1,1,"BTCEUR","1h",
                Instant.ofEpochMilli(START),Instant.ofEpochMilli(START+20*3_600_000L),
                1000,100,0,0,0,0,0.01,0.001,5,100000,
                new BacktestExecutionConfig(GridMode.GEOMETRIC,1,1000,1000,2,1,
                        "MARKET",false,0,2,1,false,0,100,100));
        BacktestOptimizationRequest request=new BacktestOptimizationRequest(base,
                List.of(1L,2L),List.of(1.0),List.of(0.0),List.of(0.0),60,2);
        StrategyCandidate active=candidate(1,"Aktiv",0);
        StrategyCandidate inactive=candidate(2,"Inaktiv",10_000);

        BacktestOptimizationResult result=new BacktestOptimizationService().optimize(request,
                List.of(active,inactive),Map.of("1h",bars()));

        assertEquals(START+12*3_600_000L,result.inSampleEnd().toEpochMilli());
        assertEquals(2,result.variants().size());
        assertEquals("Aktiv",result.variants().get(0).variant().strategyName());
        assertTrue(result.variants().get(0).inSample().tradeCount()>0);
        assertEquals(0,result.variants().get(1).inSample().tradeCount());
        assertEquals(2,result.folds().size());
        assertTrue(result.folds().stream()
                .allMatch(fold->"Aktiv".equals(fold.selected().strategyName())));
        assertEquals(result.folds().get(0).testEnd(),result.folds().get(1).testStart());
        assertEquals(result.folds().get(1).trainingEnd(),result.folds().get(1).testStart());
        assertTrue(result.folds().stream().allMatch(fold->fold.testResult().tradeCount()>0));
    }

    @Test(expected=IllegalArgumentException.class)
    public void limitsTheNumberOfComparedVariants(){
        BacktestRequest base=new BacktestRequest(1,1,"BTCEUR","1h",
                Instant.ofEpochMilli(START),Instant.ofEpochMilli(START+20*3_600_000L),
                1000,100,0,0,0,0,0.01,0.001,5,100000,
                new BacktestExecutionConfig(GridMode.GEOMETRIC,1,1000,1000,2,1,
                        "MARKET",false,0,2,1,false,0,100,100));
        new BacktestOptimizationRequest(base,List.of(1L),
                List.of(1.0,2.0,3.0,4.0),List.of(1.0,2.0,3.0,4.0),
                List.of(1.0,2.0,3.0,4.0),70,3);
    }

    private static StrategyCandidate candidate(long id,String name,double threshold){
        StrategyDefinition definition=new StrategyDefinition(id,1,name,1,true);
        List<StrategyNode> nodes=List.of(
                new StrategyNode(id*10, id,null,0,NodeType.GROUP,Action.BUY,Logic.AND,
                        null,null,null,0,"1h",14,26),
                new StrategyNode(id*10+1,id,id*10,0,NodeType.CONDITION,Action.BUY,
                        Logic.AND,Indicator.PRICE,Comparator.GREATER_THAN,Indicator.VALUE,
                        threshold,"1h",14,26));
        return new StrategyCandidate(definition,nodes);
    }

    private static List<Candlestick> bars(){
        List<Candlestick> result=new ArrayList<>();
        for(int i=0;i<20;i++){
            double price=100+i;
            Candlestick bar=new Candlestick();bar.setTimestamp((START+i*3_600_000L)/1000);
            bar.setOpen(Double.toString(price));bar.setHigh(Double.toString(price+1));
            bar.setLow(Double.toString(price-1));bar.setClose(Double.toString(price+0.5));
            bar.setVolume("1000");result.add(bar);
        }
        return result;
    }
}
