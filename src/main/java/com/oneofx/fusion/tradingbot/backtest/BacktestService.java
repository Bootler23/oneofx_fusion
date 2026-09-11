package com.oneofx.fusion.tradingbot.backtest;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.oneofx.fusion.client.FusionApiClient;
import com.oneofx.fusion.client.model.Candlestick;
import com.oneofx.fusion.client.model.CandlestickInterval;
import com.oneofx.fusion.tradingbot.strategy.StrategyDefinition;
import com.oneofx.fusion.tradingbot.strategy.StrategyEvaluation;
import com.oneofx.fusion.tradingbot.strategy.StrategyEvaluationService;
import com.oneofx.fusion.tradingbot.strategy.StrategyNode;
import com.oneofx.fusion.tradingbot.strategy.StrategyRepository;
import com.oneofx.fusion.tradingbot.strategy.EntrySpacingPolicy;
import com.oneofx.fusion.tradingbot.grid.GridCalculator;

/** Ereignisbasierter Long-Backtest. Signale abgeschlossener Kerzen werden am nächsten Open ausgeführt. */
public final class BacktestService {
    private final StrategyRepository strategies=new StrategyRepository();
    private final StrategyEvaluationService evaluator=new StrategyEvaluationService();
    private final BacktestRepository repository=new BacktestRepository();

    public BacktestResult run(BacktestRequest request,FusionApiClient client)throws Exception{
        StrategyDefinition strategy=strategies.loadAll(request.botId()).stream()
                .filter(value->value.id()==request.strategyId()).findFirst()
                .orElseThrow(()->new IllegalArgumentException("Strategie gehört nicht zum ausgewählten Bot."));
        long runId=repository.createRun(request,strategy.name());
        try{
            List<StrategyNode> nodes=strategies.loadNodes(strategy.id());
            boolean hasBuyCondition=nodes.stream().anyMatch(node->
                    node.type()==StrategyNode.NodeType.CONDITION
                            &&node.action()==StrategyNode.Action.BUY);
            if(!hasBuyCondition)throw new IllegalArgumentException(
                    "Die Strategie enth\u00e4lt keine Kaufregel. F\u00fcge im Strategie-Designer mindestens eine Kaufregel hinzu.");
            if(nodes.isEmpty())throw new IllegalArgumentException("Die Strategie enthält keine Regeln.");
            Map<String,List<Candlestick>> candles=loadCandles(request,nodes,client);
            BacktestResult result=simulate(request,strategy,nodes,candles).withRunId(runId);
            repository.complete(result);return result;
        }catch(Exception ex){try{repository.fail(runId,ex.getMessage());}catch(Exception failure){ex.addSuppressed(failure);}throw ex;}
    }

    public BacktestResult simulate(BacktestRequest request,StrategyDefinition strategy,
            List<StrategyNode> nodes,Map<String,List<Candlestick>> source){
        List<Candlestick> base=new ArrayList<>(source.getOrDefault(request.baseTimeframe(),List.of()));
        base.sort(Comparator.comparing(Candlestick::getOpenTime));
        long baseDuration=intervalMillis(request.baseTimeframe());
        long completedBefore=Math.min(request.end().toEpochMilli(),Instant.now().toEpochMilli());
        base.removeIf(bar->bar.getOpenTime()<request.start().toEpochMilli()
                || bar.getOpenTime()+baseDuration>completedBefore);
        if(base.size()<2)throw new IllegalArgumentException("Für diesen Zeitraum liegen weniger als zwei Basis-Kerzen vor.");

        BacktestExecutionConfig config=request.execution();
        double cash=request.initialCapital(),peakEquity=cash,maxDrawdown=0,anchor=initialAnchor(source.get(request.baseTimeframe()),base.get(0).getOpenTime());
        boolean sellAtNextOpen=false;int nextOrder=1,dcaOrders=0;
        List<LimitOrder> orders=new ArrayList<>();List<Position> positions=new ArrayList<>();
        List<BacktestTrade> trades=new ArrayList<>();List<BacktestOrderEvent> orderEvents=new ArrayList<>();List<BacktestEquityPoint> equity=new ArrayList<>();
        for(Candlestick bar:base){
            long time=bar.getOpenTime();double open=price(bar.getOpen(),"Open"),high=price(bar.getHigh(),"High"),low=price(bar.getLow(),"Low"),close=price(bar.getClose(),"Close");
            anchor=Math.max(anchor,high);
            if(sellAtNextOpen){
                cancelOrders(orders,time,"SELL_SIGNAL",orderEvents);
                double fill=executionPrice(open,false,request);
                for(Position position:new ArrayList<>(positions))if(!config.onlySellWithProfit()||fill>position.entryPrice){cash=closePosition(position,time,fill,"STRATEGY_SELL","Verkaufssignal am nächsten Open ausgeführt.",cash,request,trades);positions.remove(position);}
                sellAtNextOpen=false;
            }

            for(LimitOrder order:new ArrayList<>(orders)){
                if(!order.market&&low>order.limitPrice)continue;
                double raw=order.market?open:Math.min(open,order.limitPrice),fill=executionPrice(raw,true,request);
                if(!order.market)fill=Math.min(fill,order.limitPrice);
                double maximum=order.originalAmount*config.partialFillPercent()/100;
                double volume=nonNegative(bar.getVolume());
                maximum=Math.min(maximum,volume*config.volumeParticipationPercent()/100*fill);
                maximum=Math.min(maximum,Math.min(order.remainingAmount,cash/(1+request.feePercent()/100)));
                double quantity=quantizeDown(maximum/fill,request.sizeIncrement()),notional=quantity*fill;
                if(quantity<=0||notional<=0)continue;
                double fee=notional*request.feePercent()/100;cash-=notional+fee;order.remainingAmount=Math.max(0,order.remainingAmount-notional);
                Position position=positions.stream().filter(value->value.orderReference.equals(order.reference)).findFirst().orElse(null);
                if(position==null){position=new Position(order.reference,time,fill,quantity,fee,notional,fill,order.explanation);positions.add(position);}else position.addFill(fill,quantity,fee,notional);
                boolean complete=order.remainingAmount<Math.max(request.sizeIncrement()*fill,0.00000001);
                orderEvents.add(new BacktestOrderEvent(orderEvents.size()+1,order.reference,order.gridLevel,time,complete?"FILLED":"PARTIAL_FILL",order.limitPrice,fill,quantity,order.remainingAmount,order.market?"MARKET":"LIMIT_TOUCHED"));
                if(complete)orders.remove(order);
            }

            for(Position position:new ArrayList<>(positions)){
                position.peak=Math.max(position.peak,high);
                double stop=request.stopLossPercent()>0?position.entryPrice*(1-request.stopLossPercent()/100):0;
                double target=request.takeProfitPercent()>0?position.entryPrice*(1+request.takeProfitPercent()/100):Double.POSITIVE_INFINITY;
                if(stop>0&&low<=stop){cancelOrderFor(position,orders,time,"STOP_LOSS",orderEvents);double raw=Math.min(open,stop),fill=executionPrice(raw,false,request);cash=closePosition(position,time,fill,"STOP_LOSS","Konservativ innerhalb der Kerze zuerst ausgeführt.",cash,request,trades);positions.remove(position);}
                else if(high>=target){cancelOrderFor(position,orders,time,"TAKE_PROFIT",orderEvents);double fill=executionPrice(target,false,request);cash=closePosition(position,time,fill,"TAKE_PROFIT","Take-Profit innerhalb der Kerze erreicht.",cash,request,trades);positions.remove(position);}
            }

            Map<String,List<Candlestick>> windows=windowsAt(source,time+baseDuration);
            StrategyEvaluation signal=evaluator.evaluate(strategy,nodes,windows);
            String explanation=String.join(" | ",signal.explanations());
            if(signal.sell()){cancelOrders(orders,time+baseDuration,"SELL_SIGNAL",orderEvents);sellAtNextOpen=!positions.isEmpty();}
            else if(!signal.buyAllowed()){cancelOrders(orders,time+baseDuration,signal.blocked()?"BLOCK_SIGNAL":"BUY_SIGNAL_FALSE",orderEvents);}
            else{
                boolean dca=!positions.isEmpty();boolean dcaReady=dca&&config.dcaEnabled()&&dcaOrders<config.dcaMaxOrders()
                        && close<=positions.stream().mapToDouble(value->value.entryPrice).min().orElse(close)*(1-config.dcaTriggerPercent()/100);
                if(!dca||dcaReady){int targetOrders=dca?1:config.maxOpenOrders()-orders.size();double amount=request.orderAmount()*(dca?Math.pow(config.dcaSizeMultiplier(),dcaOrders+1):1);int placed=placeOrders(request,config,strategy,time+baseDuration,close,anchor,amount,targetOrders,dca,nextOrder,orders,positions,orderEvents,explanation,cash);nextOrder+=placed;if(dca&&placed>0)dcaOrders++;}
            }
            if(positions.isEmpty()&&orders.isEmpty())dcaOrders=0;

            double currentEquity=cash+positions.stream().mapToDouble(position->position.quantity*close).sum();
            peakEquity=Math.max(peakEquity,currentEquity);double drawdown=peakEquity<=0?0:(peakEquity-currentEquity)/peakEquity*100;
            maxDrawdown=Math.max(maxDrawdown,drawdown);equity.add(new BacktestEquityPoint(time,currentEquity,drawdown));
        }

        Candlestick last=base.get(base.size()-1);long finalTime=last.getOpenTime()+baseDuration;
        cancelOrders(orders,finalTime,"END_OF_TEST",orderEvents);
        double finalFill=executionPrice(price(last.getClose(),"Close"),false,request);
        for(Position position:new ArrayList<>(positions)){cash=closePosition(position,finalTime,finalFill,"END_OF_TEST","Offene Position am Testende geschlossen.",cash,request,trades);positions.remove(position);}
        peakEquity=Math.max(peakEquity,cash);double finalDrawdown=peakEquity<=0?0:(peakEquity-cash)/peakEquity*100;maxDrawdown=Math.max(maxDrawdown,finalDrawdown);
        if(!equity.isEmpty())equity.set(equity.size()-1,new BacktestEquityPoint(equity.get(equity.size()-1).timestamp(),cash,finalDrawdown));
        double net=cash-request.initialCapital(),grossProfit=0,grossLoss=0;int wins=0;
        for(BacktestTrade trade:trades){if(trade.pnl()>0){wins++;grossProfit+=trade.pnl();}else grossLoss+=-trade.pnl();}
        double profitFactor=grossLoss>0?grossProfit/grossLoss:grossProfit>0?Double.POSITIVE_INFINITY:0;
        return new BacktestResult(0,request.initialCapital(),cash,net,net/request.initialCapital()*100,
                maxDrawdown,trades.size(),trades.isEmpty()?0:wins*100.0/trades.size(),profitFactor,
                List.copyOf(trades),List.copyOf(orderEvents),List.copyOf(equity));
    }

    private static int placeOrders(BacktestRequest request,BacktestExecutionConfig config,
            StrategyDefinition strategy,
            long time,double close,double anchor,double desiredAmount,int target,boolean dca,
            int nextOrder,List<LimitOrder> orders,List<Position> positions,
            List<BacktestOrderEvent> events,String explanation,double cash){
        int placed=0;Set<Double> occupied=new java.util.HashSet<>();
        orders.forEach(order->occupied.add(order.limitPrice));
        positions.forEach(position->occupied.add(position.entryPrice));
        for(int level=1;placed<target&&level<=10_000&&orders.size()<config.maxOpenOrders()
                &&usedSlots(orders,positions)<config.maxOpenPositions();level++){
            double reserved=orders.stream().mapToDouble(order->order.remainingAmount).sum();
            double positionExposure=positions.stream()
                    .mapToDouble(position->position.costNotional).sum();
            double committed=reserved+positionExposure;
            double availableCash=Math.max(0,cash-reserved*(1+request.feePercent()/100))
                    /(1+request.feePercent()/100);
            double available=Math.min(availableCash,Math.min(
                    config.maxPairExposure()-committed,config.maxBotExposure()-committed));
            double amount=Math.min(desiredAmount,available);
            if(request.maxOrderAmount()>0)amount=Math.min(amount,request.maxOrderAmount());
            if(amount<request.minOrderAmount())break;
            boolean market="MARKET".equals(config.buyOrderType());
            double limit=market?quantize(close,request.tickSize(),RoundingMode.HALF_UP)
                    :quantize(GridCalculator.level(anchor,level,
                            new com.oneofx.fusion.tradingbot.grid.GridSettings(
                                    config.gridMode(),config.gridSpacing())),
                             request.tickSize(),RoundingMode.FLOOR);
            if(limit<=0||occupied.contains(limit))continue;
            double spacingReference=occupied.stream().mapToDouble(Double::doubleValue).min().orElse(0);
            if(spacingReference>0&&!EntrySpacingPolicy.allows(strategy.entrySpacingMode(),
                    strategy.entrySpacing(),spacingReference,limit))continue;
            String reference="BT-"+(nextOrder+placed);
            LimitOrder order=new LimitOrder(reference,market?0:level,limit,amount,amount,
                    market,dca,explanation);
            orders.add(order);occupied.add(limit);
            events.add(new BacktestOrderEvent(events.size()+1,reference,order.gridLevel,time,
                    "PLACED",limit,0,0,amount,
                    (dca?"DCA_":"GRID_")+config.buyOrderType()));
            placed++;
            if(market)break;
        }
        return placed;
    }

    private static int usedSlots(List<LimitOrder> orders,List<Position> positions){Set<String> refs=new java.util.HashSet<>();orders.forEach(order->refs.add(order.reference));positions.forEach(position->refs.add(position.orderReference));return refs.size();}
    private static void cancelOrderFor(Position position,List<LimitOrder> orders,long time,String reason,List<BacktestOrderEvent> events){for(LimitOrder order:new ArrayList<>(orders))if(order.reference.equals(position.orderReference)){events.add(new BacktestOrderEvent(events.size()+1,order.reference,order.gridLevel,time,"CANCELLED",order.limitPrice,0,0,order.remainingAmount,reason));orders.remove(order);}}
    private static void cancelOrders(List<LimitOrder> orders,long time,String reason,List<BacktestOrderEvent> events){for(LimitOrder order:new ArrayList<>(orders)){events.add(new BacktestOrderEvent(events.size()+1,order.reference,order.gridLevel,time,"CANCELLED",order.limitPrice,0,0,order.remainingAmount,reason));orders.remove(order);}}

    Map<String,List<Candlestick>> loadCandles(BacktestRequest request,List<StrategyNode> nodes,FusionApiClient client){
        Set<String> timeframes=new LinkedHashSet<>();timeframes.add(request.baseTimeframe());
        nodes.stream().filter(n->n.type()==StrategyNode.NodeType.CONDITION).map(StrategyNode::timeframe).forEach(timeframes::add);
        Map<String,List<Candlestick>> result=new HashMap<>();
        for(String timeframe:timeframes){
            long estimated=(request.end().toEpochMilli()-request.start().toEpochMilli())/intervalMillis(timeframe);
            if(estimated>50_000)throw new IllegalArgumentException("Der Zeitraum benötigt mehr als 50.000 Kerzen im Timeframe "+timeframe+". Bitte Zeitraum verkürzen.");
            int lookback=nodes.stream().filter(n->n.type()==StrategyNode.NodeType.CONDITION&&timeframe.equals(n.timeframe()))
                    .mapToInt(n->Math.max(n.period()*2+5,n.secondaryPeriod()+12)).max().orElse(10);
            long from=request.start().toEpochMilli()-intervalMillis(timeframe)*Math.min(lookback,1000);
            List<Candlestick> bars=new ArrayList<>(client.getCandlestickBarsRange(
                    request.currency(),interval(timeframe),from,request.end().toEpochMilli()));
            bars.sort(Comparator.comparing(Candlestick::getOpenTime));result.put(timeframe,List.copyOf(bars));
        }
        return result;
    }

    private static Map<String,List<Candlestick>> windowsAt(Map<String,List<Candlestick>> source,long cutoff){
        Map<String,List<Candlestick>> result=new HashMap<>();for(Map.Entry<String,List<Candlestick>> entry:source.entrySet()){long duration=intervalMillis(entry.getKey());List<Candlestick> bars=entry.getValue();int low=0,high=bars.size();while(low<high){int middle=(low+high)>>>1;if(bars.get(middle).getOpenTime()+duration<=cutoff)low=middle+1;else high=middle;}int from=Math.max(0,low-1000);result.put(entry.getKey(),bars.subList(from,low));}return result;
    }

    private static double closePosition(Position position,long time,double fill,String reason,String explanation,double cash,BacktestRequest request,List<BacktestTrade> trades){double proceeds=position.quantity*fill,sellFee=proceeds*request.feePercent()/100,pnl=(fill-position.entryPrice)*position.quantity-position.entryFee-sellFee;cash+=proceeds-sellFee;trades.add(new BacktestTrade(trades.size()+1,position.entryTime,time,position.entryPrice,fill,position.quantity,position.entryFee+sellFee,pnl,reason,position.entryExplanation+" | "+explanation));return cash;}
    private static double executionPrice(double raw,boolean buy,BacktestRequest request){double friction=request.slippagePercent()+request.execution().spreadPercent()/2;double slipped=raw*(1+(buy?1:-1)*friction/100);return quantize(slipped,request.tickSize(),buy?RoundingMode.CEILING:RoundingMode.FLOOR);}
    private static double quantize(double value,double increment,RoundingMode mode){BigDecimal v=BigDecimal.valueOf(value),step=BigDecimal.valueOf(increment);return v.divide(step,0,mode).multiply(step).doubleValue();}
    private static double quantizeDown(double value,double increment){BigDecimal v=BigDecimal.valueOf(value),step=BigDecimal.valueOf(increment);return v.divide(step,0,RoundingMode.DOWN).multiply(step).doubleValue();}
    private static double initialAnchor(List<Candlestick> bars,long firstTime){double result=0;if(bars!=null)for(Candlestick bar:bars)if(bar.getOpenTime()<=firstTime)result=Math.max(result,price(bar.getHigh(),"High"));return result;}
    private static double nonNegative(String value){try{double parsed=Double.parseDouble(value);return Double.isFinite(parsed)&&parsed>=0?parsed:0;}catch(Exception ex){return 0;}}
    private static double price(String value,String label){try{double parsed=Double.parseDouble(value);if(!Double.isFinite(parsed)||parsed<=0)throw new NumberFormatException();return parsed;}catch(Exception ex){throw new IllegalArgumentException("Ungültiger Kerzenwert für "+label+": "+value);}}
    private static CandlestickInterval interval(String value){for(CandlestickInterval interval:CandlestickInterval.values())if(interval.getIntervalId().equals(value))return interval;throw new IllegalArgumentException("Nicht unterstützter Timeframe: "+value);}
    private static long intervalMillis(String value){return switch(value){case"1m"->60_000L;case"5m"->300_000L;case"10m"->600_000L;case"15m"->900_000L;case"30m"->1_800_000L;case"1h"->3_600_000L;case"4h"->14_400_000L;case"1d"->86_400_000L;default->throw new IllegalArgumentException("Nicht unterstützter Timeframe: "+value);};}
    private static final class LimitOrder{final String reference;final int gridLevel;final double limitPrice,originalAmount;final boolean market,dca;final String explanation;double remainingAmount;LimitOrder(String r,int l,double p,double original,double remaining,boolean market,boolean dca,String explanation){reference=r;gridLevel=l;limitPrice=p;originalAmount=original;remainingAmount=remaining;this.market=market;this.dca=dca;this.explanation=explanation;}}
    private static final class Position{final String orderReference;final long entryTime;final String entryExplanation;double entryPrice,quantity,entryFee,costNotional,peak;Position(String ref,long t,double p,double q,double f,double cost,double peak,String e){orderReference=ref;entryTime=t;entryPrice=p;quantity=q;entryFee=f;costNotional=cost;this.peak=peak;entryExplanation=e;}void addFill(double price,double addedQuantity,double fee,double notional){entryPrice=(entryPrice*quantity+price*addedQuantity)/(quantity+addedQuantity);quantity+=addedQuantity;entryFee+=fee;costNotional+=notional;peak=Math.max(peak,price);}}
}
