package com.oneofx.fusion.tradingbot.backtest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.Test;

import com.oneofx.fusion.client.model.Candlestick;
import com.oneofx.fusion.tradingbot.strategy.StrategyDefinition;
import com.oneofx.fusion.tradingbot.strategy.StrategyNode;
import com.oneofx.fusion.tradingbot.strategy.StrategyNode.Action;
import com.oneofx.fusion.tradingbot.strategy.StrategyNode.Comparator;
import com.oneofx.fusion.tradingbot.strategy.StrategyNode.Indicator;
import com.oneofx.fusion.tradingbot.strategy.StrategyNode.Logic;
import com.oneofx.fusion.tradingbot.strategy.StrategyNode.NodeType;
import com.oneofx.fusion.tradingbot.grid.GridMode;

public class BacktestServiceTest {
    private static final long START=1_700_000_000_000L;
    private final BacktestService service=new BacktestService();

    @Test public void executesClosedCandleSignalAtNextOpenAndAccountsForFees() {
        BacktestResult result=service.simulate(request(),strategy(),nodes(),Map.of("1h",bars(false)));

        assertEquals(1,result.tradeCount());
        assertEquals(START+3*3_600_000L,result.trades().get(0).entryTime());
        assertEquals("TAKE_PROFIT",result.trades().get(0).exitReason());
        assertEquals(2.95,result.netProfit(),0.000001);
        assertEquals(2.05,result.trades().get(0).fees(),0.000001);
        assertTrue(result.maxDrawdownPercent()>=0);
    }

    @Test public void choosesStopLossWhenStopAndTargetOccurInSameCandle() {
        BacktestResult result=service.simulate(request(),strategy(),nodes(),Map.of("1h",bars(true)));

        assertEquals(1,result.tradeCount());
        assertEquals("STOP_LOSS",result.trades().get(0).exitReason());
        assertTrue(result.netProfit()<0);
    }

    @Test public void fillsTwoGridLevelsAcrossSeveralCandles() {
        BacktestRequest request=request(new BacktestExecutionConfig(
                GridMode.GEOMETRIC,10,1000,1000,2,2,"LIMIT",false,0,2,1,
                false,0.2,50,100),0,0,7);

        BacktestResult result=service.simulate(request,strategy(),alwaysBuyNodes(),
                Map.of("1h",gridBars()));

        assertEquals(2,result.tradeCount());
        assertEquals(2,result.orderEvents().stream().filter(e->"PLACED".equals(e.event())).count());
        assertTrue(result.orderEvents().stream().anyMatch(e->"PARTIAL_FILL".equals(e.event())));
        assertEquals(2,result.orderEvents().stream().filter(e->"FILLED".equals(e.event())).count());
        assertTrue(result.orderEvents().stream().filter(e->e.timestamp()==START+3*3_600_000L)
                .noneMatch(e->e.fillQuantity()>0));
        assertTrue(result.orderEvents().stream().filter(e->e.fillQuantity()>0)
                .allMatch(e->e.fillPrice()<=e.limitPrice()));
    }

    @Test public void placesConfiguredDcaOrderAfterPriceDrop() {
        BacktestRequest request=request(new BacktestExecutionConfig(
                GridMode.GEOMETRIC,10,1000,1000,2,1,"LIMIT",true,1,2,2,
                false,0,100,100),0,0,7);

        BacktestResult result=service.simulate(request,strategy(),alwaysBuyNodes(),
                Map.of("1h",dcaBars()));

        assertEquals(2,result.tradeCount());
        BacktestOrderEvent dca=result.orderEvents().stream()
                .filter(e->"DCA_LIMIT".equals(e.reason())).findFirst().orElseThrow();
        assertEquals("PLACED",dca.event());
        assertEquals(200,dca.remainingAmount(),0.000001);
        assertTrue(dca.gridLevel()>1);
    }

    private static BacktestRequest request(){return new BacktestRequest(1,1,"BTCEUR","1h",
            Instant.ofEpochMilli(START),Instant.ofEpochMilli(START+6*3_600_000L),1000,100,
            1,0,2,5,0.01,0.001,5,100000,new BacktestExecutionConfig(
                    GridMode.GEOMETRIC,1,1000,1000,10,4,"MARKET",false,0,2,1,
                    false,0,100,100));}
    private static BacktestRequest request(BacktestExecutionConfig execution,double stopLoss,
            double takeProfit,int hours){return new BacktestRequest(1,1,"BTCEUR","1h",
                    Instant.ofEpochMilli(START),Instant.ofEpochMilli(START+hours*3_600_000L),1000,100,
                    1,0,stopLoss,takeProfit,0.01,0.001,5,100000,execution);}
    private static StrategyDefinition strategy(){return new StrategyDefinition(1,1,"Test",1,true);}
    private static List<StrategyNode> nodes(){return List.of(
            new StrategyNode(1,1,null,0,NodeType.GROUP,Action.BUY,Logic.AND,null,null,null,0,"1h",14,26),
            new StrategyNode(2,1,1L,0,NodeType.CONDITION,Action.BUY,Logic.AND,Indicator.PRICE,
                    Comparator.GREATER_THAN,Indicator.VALUE,101,"1h",14,26));}
    private static List<StrategyNode> alwaysBuyNodes(){return List.of(
            new StrategyNode(1,1,null,0,NodeType.GROUP,Action.BUY,Logic.AND,null,null,null,0,"1h",14,26),
            new StrategyNode(2,1,1L,0,NodeType.CONDITION,Action.BUY,Logic.AND,Indicator.PRICE,
                    Comparator.GREATER_THAN,Indicator.VALUE,0,"1h",14,26));}
    private static List<Candlestick> bars(boolean both){
        double[] closes={100,100,102,100,100,100};List<Candlestick> result=new ArrayList<>();
        for(int i=0;i<closes.length;i++){Candlestick bar=new Candlestick();bar.setTimestamp((START+i*3_600_000L)/1000);bar.setOpen("100");bar.setHigh(i==3?"106":"103");bar.setLow(i==3&&both?"97":"99");bar.setClose(Double.toString(closes[i]));bar.setVolume("1000");result.add(bar);}return result;
    }
    private static List<Candlestick> gridBars(){
        List<Candlestick> bars=customBars(
                new double[]{100,100,100,85,85,85,85},
                new double[]{99,99,99,80,80,80,80});
        bars.get(3).setVolume("0");
        return bars;
    }
    private static List<Candlestick> dcaBars(){return customBars(
            new double[]{100,100,100,90,85,82,82},
            new double[]{99,99,99,89,84,80,80});}
    private static List<Candlestick> customBars(double[] closes,double[] lows){
        List<Candlestick> result=new ArrayList<>();
        for(int i=0;i<closes.length;i++){
            Candlestick bar=new Candlestick();bar.setTimestamp((START+i*3_600_000L)/1000);
            bar.setOpen(i==0?"100":Double.toString(closes[i-1]));bar.setHigh("100");
            bar.setLow(Double.toString(lows[i]));bar.setClose(Double.toString(closes[i]));
            bar.setVolume("1000");result.add(bar);
        }
        return result;
    }
}
