package com.oneofx.fusion.tradingbot.strategy;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.Test;

import com.oneofx.fusion.client.model.Candlestick;
import com.oneofx.fusion.tradingbot.strategy.StrategyNode.Action;
import com.oneofx.fusion.tradingbot.strategy.StrategyNode.Comparator;
import com.oneofx.fusion.tradingbot.strategy.StrategyNode.Indicator;
import com.oneofx.fusion.tradingbot.strategy.StrategyNode.Logic;
import com.oneofx.fusion.tradingbot.strategy.StrategyNode.NodeType;

public class StrategyEvaluationServiceTest {
    private final StrategyEvaluationService service=new StrategyEvaluationService();

    @Test public void evaluatesNestedBuyAndMinimumConfirmations() {
        StrategyDefinition strategy=new StrategyDefinition(1,1,"Test",2,true);
        List<StrategyNode> nodes=List.of(
                group(1,null,Action.BUY,Logic.AND),
                condition(2,1,Action.BUY,Indicator.PRICE,Comparator.GREATER_THAN,
                        Indicator.SMA,0,3),
                group(3,null,Action.CONFIRM,Logic.AND),
                condition(4,3,Action.CONFIRM,Indicator.RSI,Comparator.GREATER_THAN,
                        Indicator.VALUE,50,3),
                group(5,null,Action.CONFIRM,Logic.AND),
                condition(6,5,Action.CONFIRM,Indicator.VOLUME,Comparator.GREATER_THAN,
                        Indicator.VALUE,100,3));

        StrategyEvaluation result=service.evaluate(strategy,nodes,Map.of("1h",risingBars()));

        assertTrue(result.buy());
        assertTrue(result.buyAllowed());
        assertEquals(2,result.confirmations());
        assertTrue(result.explanations().stream().anyMatch(line->line.contains("RSI")));
    }

    @Test public void blockSignalOverridesMatchingBuy() {
        StrategyDefinition strategy=new StrategyDefinition(1,1,"Block",1,true);
        List<StrategyNode> nodes=List.of(
                group(1,null,Action.BUY,Logic.AND),
                condition(2,1,Action.BUY,Indicator.PRICE,Comparator.GREATER_THAN,
                        Indicator.VALUE,1,3),
                group(3,null,Action.BLOCK,Logic.OR),
                condition(4,3,Action.BLOCK,Indicator.RSI,Comparator.GREATER_THAN,
                        Indicator.VALUE,50,3));

        StrategyEvaluation result=service.evaluate(strategy,nodes,Map.of("1h",risingBars()));

        assertTrue(result.buy());
        assertTrue(result.blocked());
        assertFalse(result.buyAllowed());
    }

    @Test public void evaluatesCciAndCandlestickPattern() {
        StrategyDefinition strategy=new StrategyDefinition(1,1,"Candles",1,true);
        List<StrategyNode> nodes=List.of(
                group(1,null,Action.BUY,Logic.AND),
                condition(2,1,Action.BUY,Indicator.BULLISH_ENGULFING,
                        Comparator.GREATER_OR_EQUAL,Indicator.VALUE,1,14),
                condition(3,1,Action.BUY,Indicator.CCI,Comparator.GREATER_THAN,
                        Indicator.VALUE,-1000,14));
        List<Candlestick> bars=risingBars();
        Candlestick previous=bars.get(bars.size()-2);
        previous.setOpen("150");previous.setHigh("151");previous.setLow("144");previous.setClose("145");
        Candlestick current=bars.get(bars.size()-1);
        current.setOpen("144");current.setHigh("153");current.setLow("143");current.setClose("152");

        StrategyEvaluation result=service.evaluate(strategy,nodes,Map.of("1h",bars));

        assertTrue(result.buyAllowed());
    }

    private static StrategyNode group(long id,Long parent,Action action,Logic logic) {
        return new StrategyNode(id,1,parent,0,NodeType.GROUP,action,logic,null,null,null,
                0,"1h",14,26);
    }

    private static StrategyNode condition(long id,long parent,Action action,Indicator left,
            Comparator comparator,Indicator right,double value,int period) {
        return new StrategyNode(id,1,parent,0,NodeType.CONDITION,action,Logic.AND,left,
                comparator,right,value,"1h",period,26);
    }

    private static List<Candlestick> risingBars() {
        List<Candlestick> bars=new ArrayList<>();
        for(int i=0;i<40;i++) {
            double price=100+i;
            Candlestick bar=new Candlestick();
            bar.setTimestamp(1_700_000_000L+i*3600L);
            bar.setOpen(Double.toString(price-0.5));
            bar.setHigh(Double.toString(price+1));
            bar.setLow(Double.toString(price-1));
            bar.setClose(Double.toString(price));
            bar.setVolume("1000");
            bars.add(bar);
        }
        return bars;
    }
}
