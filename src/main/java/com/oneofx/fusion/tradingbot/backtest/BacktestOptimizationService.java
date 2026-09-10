package com.oneofx.fusion.tradingbot.backtest;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.oneofx.fusion.client.FusionApiClient;
import com.oneofx.fusion.client.model.Candlestick;
import com.oneofx.fusion.tradingbot.strategy.StrategyDefinition;
import com.oneofx.fusion.tradingbot.strategy.StrategyNode;
import com.oneofx.fusion.tradingbot.strategy.StrategyRepository;

/** Vergleicht Varianten ohne Out-of-Sample-Daten zur Auswahl zu verwenden. */
public final class BacktestOptimizationService {
    private final BacktestService engine=new BacktestService();
    private final StrategyRepository strategies=new StrategyRepository();

    public BacktestOptimizationResult run(BacktestOptimizationRequest request,
            FusionApiClient client)throws Exception{
        Map<Long,StrategyDefinition> definitions=new HashMap<>();
        for(StrategyDefinition definition:strategies.loadAll(request.baseRequest().botId()))
            definitions.put(definition.id(),definition);
        List<StrategyCandidate> candidates=new ArrayList<>();
        List<StrategyNode> allNodes=new ArrayList<>();
        for(long strategyId:request.strategyIds()){
            StrategyDefinition definition=definitions.get(strategyId);
            if(definition==null)throw new IllegalArgumentException(
                    "Eine ausgewählte Strategie gehört nicht zum aktuellen Bot.");
            List<StrategyNode> nodes=strategies.loadNodes(strategyId);
            if(nodes.isEmpty())throw new IllegalArgumentException(
                    "Strategie "+definition.name()+" enthält keine Regeln.");
            candidates.add(new StrategyCandidate(definition,nodes));allNodes.addAll(nodes);
        }
        Map<String,List<Candlestick>> source=engine.loadCandles(
                request.baseRequest(),allNodes,client);
        return optimize(request,candidates,source);
    }

    public BacktestOptimizationResult optimize(BacktestOptimizationRequest request,
            List<StrategyCandidate> candidates,Map<String,List<Candlestick>> source){
        Map<Long,StrategyCandidate> byId=new HashMap<>();
        for(StrategyCandidate candidate:candidates)byId.put(candidate.definition().id(),candidate);
        for(long id:request.strategyIds())if(!byId.containsKey(id))
            throw new IllegalArgumentException("Strategiedaten für ID "+id+" fehlen.");

        BacktestRequest base=request.baseRequest();long interval=intervalMillis(base.baseTimeframe());
        long totalBars=(base.end().toEpochMilli()-base.start().toEpochMilli())/interval;
        long trainBars=(long)Math.floor(totalBars*request.inSamplePercent()/100);
        long outBars=totalBars-trainBars;
        if(trainBars<2||outBars<2)throw new IllegalArgumentException(
                "In-Sample und Out-of-Sample benötigen jeweils mindestens zwei Kerzen.");
        if(outBars<request.walkForwardFolds()*2L)throw new IllegalArgumentException(
                "Der Out-of-Sample-Zeitraum ist für die gewählte Anzahl Walk-forward-Fenster zu kurz.");
        Instant split=base.start().plusMillis(trainBars*interval);

        List<BacktestVariant> variants=createVariants(request,byId);
        List<Unranked> calculated=new ArrayList<>();
        for(BacktestVariant variant:variants){
            StrategyCandidate candidate=byId.get(variant.strategyId());
            BacktestResult inSample=simulate(base,variant,base.start(),split,candidate,source);
            BacktestResult outOfSample=simulate(base,variant,split,base.end(),candidate,source);
            calculated.add(new Unranked(variant,inSample,outOfSample,score(inSample)));
        }
        calculated.sort(Comparator.comparingDouble(Unranked::score).reversed()
                .thenComparing(value->value.variant().label()));
        List<BacktestOptimizationRow> rows=new ArrayList<>();
        for(int i=0;i<calculated.size();i++){
            Unranked row=calculated.get(i);
            rows.add(new BacktestOptimizationRow(i+1,row.variant(),row.inSample(),
                    row.outOfSample(),row.score()));
        }

        List<BacktestWalkForwardFold> folds=new ArrayList<>();
        for(int fold=0;fold<request.walkForwardFolds();fold++){
            long testOffset=trainBars+outBars*fold/request.walkForwardFolds();
            long testEndOffset=trainBars+outBars*(fold+1)/request.walkForwardFolds();
            Instant testStart=base.start().plusMillis(testOffset*interval);
            Instant testEnd=base.start().plusMillis(testEndOffset*interval);
            Unranked best=null;
            for(BacktestVariant variant:variants){
                StrategyCandidate candidate=byId.get(variant.strategyId());
                BacktestResult training=fold==0
                        ?calculated.stream().filter(row->row.variant().equals(variant))
                                .findFirst().orElseThrow().inSample()
                        :simulate(base,variant,base.start(),testStart,candidate,source);
                Unranked current=new Unranked(variant,training,null,score(training));
                if(best==null||current.score()>best.score()
                        ||(current.score()==best.score()
                                &&current.variant().label().compareTo(best.variant().label())<0))best=current;
            }
            StrategyCandidate selected=byId.get(best.variant().strategyId());
            BacktestResult test=simulate(base,best.variant(),testStart,testEnd,selected,source);
            folds.add(new BacktestWalkForwardFold(fold+1,base.start(),testStart,
                    testStart,testEnd,best.variant(),best.inSample(),test));
        }
        return new BacktestOptimizationResult(base.start(),split,split,base.end(),
                List.copyOf(rows),List.copyOf(folds));
    }

    private BacktestResult simulate(BacktestRequest base,BacktestVariant variant,Instant start,
            Instant end,StrategyCandidate candidate,Map<String,List<Candlestick>> source){
        BacktestExecutionConfig old=base.execution();
        BacktestExecutionConfig execution=new BacktestExecutionConfig(old.gridMode(),
                variant.gridSpacing(),old.maxPairExposure(),old.maxBotExposure(),
                old.maxOpenPositions(),old.maxOpenOrders(),old.buyOrderType(),old.dcaEnabled(),
                old.dcaMaxOrders(),old.dcaTriggerPercent(),old.dcaSizeMultiplier(),
                old.onlySellWithProfit(),old.spreadPercent(),old.partialFillPercent(),
                old.volumeParticipationPercent());
        BacktestRequest configured=new BacktestRequest(base.botId(),variant.strategyId(),
                base.currency(),base.baseTimeframe(),start,end,base.initialCapital(),
                base.orderAmount(),base.feePercent(),base.slippagePercent(),
                variant.stopLossPercent(),variant.takeProfitPercent(),base.tickSize(),
                base.sizeIncrement(),base.minOrderAmount(),base.maxOrderAmount(),execution);
        return engine.simulate(configured,candidate.definition(),candidate.nodes(),source);
    }

    private static List<BacktestVariant> createVariants(BacktestOptimizationRequest request,
            Map<Long,StrategyCandidate> candidates){
        List<BacktestVariant> result=new ArrayList<>();
        for(long strategyId:request.strategyIds())for(double grid:request.gridSpacings())
            for(double stop:request.stopLosses())for(double takeProfit:request.takeProfits())
                result.add(new BacktestVariant(strategyId,
                        candidates.get(strategyId).definition().name(),grid,stop,takeProfit));
        return result;
    }

    /** Transparenter Robustheitswert: In-Sample-Rendite minus maximaler Drawdown. */
    private static double score(BacktestResult result){
        return result.tradeCount()==0?-1_000_000_000:
                result.returnPercent()-result.maxDrawdownPercent();
    }

    private static long intervalMillis(String value){return switch(value){
        case"1m"->60_000L;case"5m"->300_000L;case"10m"->600_000L;
        case"15m"->900_000L;case"30m"->1_800_000L;case"1h"->3_600_000L;
        case"4h"->14_400_000L;case"1d"->86_400_000L;
        default->throw new IllegalArgumentException("Nicht unterstützter Timeframe: "+value);};}

    public record StrategyCandidate(StrategyDefinition definition,List<StrategyNode> nodes){
        public StrategyCandidate{nodes=List.copyOf(nodes);}
    }
    private record Unranked(BacktestVariant variant,BacktestResult inSample,
            BacktestResult outOfSample,double score) { }
}
