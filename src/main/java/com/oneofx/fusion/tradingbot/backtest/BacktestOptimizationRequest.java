package com.oneofx.fusion.tradingbot.backtest;

import java.util.List;

import com.oneofx.fusion.tradingbot.grid.GridSettings;

public record BacktestOptimizationRequest(BacktestRequest baseRequest,
        List<Long> strategyIds,List<Double> gridSpacings,List<Double> stopLosses,
        List<Double> takeProfits,double inSamplePercent,int walkForwardFolds) {

    public BacktestOptimizationRequest {
        if(baseRequest==null)throw new IllegalArgumentException("Backtest-Grundkonfiguration fehlt.");
        strategyIds=distinct(strategyIds,"Strategie");
        gridSpacings=distinct(gridSpacings,"Grid-Abstand");
        stopLosses=distinct(stopLosses,"Stop-Loss");
        takeProfits=distinct(takeProfits,"Take Profit");
        if(inSamplePercent<50||inSamplePercent>90||!Double.isFinite(inSamplePercent))
            throw new IllegalArgumentException("Der In-Sample-Anteil muss zwischen 50 und 90 Prozent liegen.");
        if(walkForwardFolds<1||walkForwardFolds>6)
            throw new IllegalArgumentException("Es sind 1 bis 6 Walk-forward-Fenster erlaubt.");
        long combinations=(long)strategyIds.size()*gridSpacings.size()
                *stopLosses.size()*takeProfits.size();
        if(combinations>60)throw new IllegalArgumentException(
                "Maximal 60 Varianten pro Optimierung sind erlaubt (aktuell "+combinations+").");
        validatePositive(gridSpacings,"Grid-Abstand",false,false);
        validatePositive(stopLosses,"Stop-Loss",true,true);
        validatePositive(takeProfits,"Take Profit",true,true);
        for(double spacing:gridSpacings)new GridSettings(
                baseRequest.execution().gridMode(),spacing);
    }

    private static <T> List<T> distinct(List<T> values,String label){
        if(values==null||values.isEmpty())throw new IllegalArgumentException(label+" fehlt.");
        List<T> result=values.stream().distinct().toList();
        if(result.stream().anyMatch(java.util.Objects::isNull))
            throw new IllegalArgumentException(label+" enthält einen leeren Wert.");
        return result;
    }

    private static void validatePositive(List<Double> values,String label,boolean allowZero,
            boolean belowHundred){
        for(double value:values)if(!Double.isFinite(value)||(allowZero?value<0:value<=0)
                ||(belowHundred&&value>=100))
            throw new IllegalArgumentException(label+" enthält einen ungültigen Wert: "+value);
    }
}
