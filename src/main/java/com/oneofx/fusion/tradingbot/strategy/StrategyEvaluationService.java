package com.oneofx.fusion.tradingbot.strategy;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import com.oneofx.fusion.client.FusionApiClient;
import com.oneofx.fusion.client.model.Candlestick;
import com.oneofx.fusion.client.model.CandlestickInterval;
import com.oneofx.fusion.tradingbot.strategy.StrategyNode.Action;
import com.oneofx.fusion.tradingbot.strategy.StrategyNode.Indicator;

/** Wertet gespeicherte Strategiebäume aus und liefert eine lesbare Begründung. */
public final class StrategyEvaluationService {
    private static final Duration CACHE_TTL=Duration.ofSeconds(30);
    private final StrategyRepository repository=new StrategyRepository();
    private final Map<String,Cached> cache=new ConcurrentHashMap<>();

    public Optional<StrategyEvaluation> evaluateAssigned(long botId,String currency,
            FusionApiClient client)throws Exception{
        return evaluateAssigned(botId,currency,null,client);
    }

    public Optional<StrategyEvaluation> evaluateAssigned(long botId,String currency,String marketRegime,
            FusionApiClient client)throws Exception{
        Optional<StrategyDefinition> assigned=repository.resolve(botId,currency,marketRegime);
        if(assigned.isEmpty())return Optional.empty();
        StrategyDefinition strategy=assigned.get();String key=strategy.id()+":"+currency;
        Cached old=cache.get(key);if(old!=null&&Duration.between(old.at(),Instant.now()).compareTo(CACHE_TTL)<0)return Optional.of(old.value());
        StrategyEvaluation value=evaluate(strategy,currency,client);cache.put(key,new Cached(value,Instant.now()));
        repository.log(botId,value,currency);return Optional.of(value);
    }

    public StrategyEvaluation evaluate(StrategyDefinition strategy,String currency,
            FusionApiClient client)throws Exception{
        List<StrategyNode> nodes=repository.loadNodes(strategy.id());
        Map<String,List<Candlestick>> candles=new HashMap<>();
        Map<String,Integer> limits=new HashMap<>();
        for(StrategyNode n:nodes)if(n.type()==StrategyNode.NodeType.CONDITION)
            limits.merge(n.timeframe(),Math.min(1440,Math.max(100,Math.max(n.secondaryPeriod()+12,n.period()*2+5))),Math::max);
        for(Map.Entry<String,Integer> request:limits.entrySet()){
            List<Candlestick> bars=new ArrayList<>(client.getCandlestickBars(currency,interval(request.getKey()),request.getValue(),null,null));
            bars.sort(Comparator.comparing(Candlestick::getOpenTime));candles.put(request.getKey(),bars);
        }
        return evaluate(strategy,nodes,candles);
    }

    public StrategyEvaluation evaluate(StrategyDefinition strategy,List<StrategyNode> nodes,
            Map<String,List<Candlestick>> candles){
        Map<Long,List<StrategyNode>> children=new HashMap<>();List<StrategyNode> roots=new ArrayList<>();
        for(StrategyNode n:nodes){if(n.parentId()==null)roots.add(n);else children.computeIfAbsent(n.parentId(),x->new ArrayList<>()).add(n);}
        roots.sort(Comparator.comparingInt(StrategyNode::position));
        children.values().forEach(list->list.sort(Comparator.comparingInt(StrategyNode::position)));
        EnumMap<Action,Integer> matches=new EnumMap<>(Action.class);List<String> explanations=new ArrayList<>();
        for(StrategyNode root:roots){boolean match=evaluateNode(root,root.action(),children,candles,explanations,0);if(match)matches.merge(root.action(),1,Integer::sum);}
        int confirmRoots=(int)roots.stream().filter(n->n.action()==Action.CONFIRM).count();
        int confirmations=confirmRoots==0?strategy.minimumConfirmations():matches.getOrDefault(Action.CONFIRM,0);
        StrategyEvaluation result=new StrategyEvaluation(strategy.id(),strategy.name(),
                matches.getOrDefault(Action.BUY,0)>0,matches.getOrDefault(Action.SELL,0)>0,
                matches.getOrDefault(Action.BLOCK,0)>0,confirmations,strategy.minimumConfirmations(),List.copyOf(explanations));
        return result;
    }

    private boolean evaluateNode(StrategyNode node,Action action,Map<Long,List<StrategyNode>> children,
            Map<String,List<Candlestick>> candles,List<String> explanations,int depth){
        if(node.type()==StrategyNode.NodeType.CONDITION){
            Values left=values(node.leftIndicator(),node,candles.get(node.timeframe()));
            Values right=node.rightIndicator()==Indicator.VALUE?new Values(node.compareValue(),node.compareValue())
                    :values(node.rightIndicator(),node,candles.get(node.timeframe()));
            boolean match=compare(left,right,node.comparator());
            explanations.add("  ".repeat(Math.min(depth,8))+action+": "+node.leftIndicator()+" "+node.comparator()+" "+node.rightIndicator()+" => "+match+" ("+number(left.current())+" / "+number(right.current())+")");return match;
        }
        List<StrategyNode> nested=children.getOrDefault(node.id(),List.of());boolean value=!nested.isEmpty()&&node.logic()==StrategyNode.Logic.AND;
        if(node.logic()==StrategyNode.Logic.AND){for(StrategyNode child:nested)value&=evaluateNode(child,action,children,candles,explanations,depth+1);}
        else {value=false;for(StrategyNode child:nested)value|=evaluateNode(child,action,children,candles,explanations,depth+1);}
        explanations.add("  ".repeat(Math.min(depth,8))+action+"-Gruppe "+node.logic()+" => "+value);return value;
    }

    private static boolean compare(Values l,Values r,StrategyNode.Comparator c){
        if(!Double.isFinite(l.current())||!Double.isFinite(r.current()))return false;
        return switch(c){case GREATER_THAN->l.current()>r.current();case GREATER_OR_EQUAL->l.current()>=r.current();case LESS_THAN->l.current()<r.current();case LESS_OR_EQUAL->l.current()<=r.current();case CROSS_ABOVE->l.previous()<=r.previous()&&l.current()>r.current();case CROSS_BELOW->l.previous()>=r.previous()&&l.current()<r.current();};
    }

    private static Values values(Indicator indicator,StrategyNode node,List<Candlestick> bars){
        if(bars==null||bars.size()<3)return Values.invalid();double[] open=array(bars,"open"),close=array(bars,"close"),high=array(bars,"high"),low=array(bars,"low"),volume=array(bars,"volume");
        return switch(indicator){
            case PRICE->lastTwo(close);case VOLUME->lastTwo(volume);
            case SMA->new Values(sma(close,node.period(),0),sma(close,node.period(),1));
            case EMA->new Values(ema(close,node.period(),0),ema(close,node.period(),1));
            case RSI->new Values(rsi(close,node.period(),0),rsi(close,node.period(),1));
            case MACD->new Values(macd(close,node.period(),node.secondaryPeriod(),0)[0],macd(close,node.period(),node.secondaryPeriod(),1)[0]);
            case MACD_SIGNAL->new Values(macd(close,node.period(),node.secondaryPeriod(),0)[1],macd(close,node.period(),node.secondaryPeriod(),1)[1]);
            case STOCH_RSI->new Values(stochRsi(close,node.period(),0),stochRsi(close,node.period(),1));
            case BOLLINGER_UPPER->new Values(bollinger(close,node.period(),0,true),bollinger(close,node.period(),1,true));
            case BOLLINGER_LOWER->new Values(bollinger(close,node.period(),0,false),bollinger(close,node.period(),1,false));
            case ATR->new Values(atr(high,low,close,node.period(),0),atr(high,low,close,node.period(),1));
            case CCI->new Values(cci(high,low,close,node.period(),0),cci(high,low,close,node.period(),1));
            case BULLISH_ENGULFING->pattern(open,high,low,close,Pattern.BULLISH_ENGULFING);
            case BEARISH_ENGULFING->pattern(open,high,low,close,Pattern.BEARISH_ENGULFING);
            case HAMMER->pattern(open,high,low,close,Pattern.HAMMER);
            case SHOOTING_STAR->pattern(open,high,low,close,Pattern.SHOOTING_STAR);
            case VALUE->new Values(node.compareValue(),node.compareValue());};
    }

    private static double[] array(List<Candlestick>b,String field){double[]v=new double[b.size()];for(int i=0;i<v.length;i++){Candlestick c=b.get(i);try{v[i]=Double.parseDouble(switch(field){case"open"->c.getOpen();case"high"->c.getHigh();case"low"->c.getLow();case"volume"->c.getVolume();default->c.getClose();});}catch(Exception e){v[i]=Double.NaN;}}return v;}
    private static Values lastTwo(double[]v){return v.length<2?Values.invalid():new Values(v[v.length-1],v[v.length-2]);}
    private static double sma(double[]v,int p,int off){int end=v.length-off;if(end<p)return Double.NaN;double s=0;for(int i=end-p;i<end;i++)s+=v[i];return s/p;}
    private static double ema(double[]v,int p,int off){int end=v.length-off;if(end<p)return Double.NaN;double e=v[0],a=2.0/(p+1);for(int i=1;i<end;i++)e+=a*(v[i]-e);return e;}
    private static double rsi(double[]v,int p,int off){int end=v.length-off;if(end<=p)return Double.NaN;double g=0,l=0;for(int i=end-p;i<end;i++){double d=v[i]-v[i-1];g+=Math.max(0,d);l+=Math.max(0,-d);}return l==0?100:100-100/(1+g/l);}
    private static double[] macd(double[]v,int fast,int slow,int off){double m=ema(v,fast,off)-ema(v,slow,off);int end=v.length-off;if(end<slow+9)return new double[]{m,Double.NaN};double[]ms=new double[end];for(int i=slow;i<end;i++){double[]slice=java.util.Arrays.copyOf(v,i+1);ms[i]=ema(slice,fast,0)-ema(slice,slow,0);}double signal=ema(java.util.Arrays.copyOfRange(ms,slow,end),9,0);return new double[]{m,signal};}
    private static double stochRsi(double[]v,int p,int off){int end=v.length-off;if(end<p*2+1)return Double.NaN;double current=rsi(v,p,off),min=Double.POSITIVE_INFINITY,max=Double.NEGATIVE_INFINITY;for(int i=0;i<p;i++){double x=rsi(v,p,off+i);min=Math.min(min,x);max=Math.max(max,x);}return max==min?50:(current-min)/(max-min)*100;}
    private static double bollinger(double[]v,int p,int off,boolean upper){double mean=sma(v,p,off);int end=v.length-off;if(!Double.isFinite(mean)||end<p)return Double.NaN;double s=0;for(int i=end-p;i<end;i++)s+=(v[i]-mean)*(v[i]-mean);double sd=Math.sqrt(s/p);return mean+(upper?2:-2)*sd;}
    private static double atr(double[]h,double[]l,double[]c,int p,int off){int end=c.length-off;if(end<=p)return Double.NaN;double s=0;for(int i=end-p;i<end;i++)s+=Math.max(h[i]-l[i],Math.max(Math.abs(h[i]-c[i-1]),Math.abs(l[i]-c[i-1])));return s/p;}
    private static double cci(double[]h,double[]l,double[]c,int p,int off){int end=c.length-off;if(end<p)return Double.NaN;double mean=0;for(int i=end-p;i<end;i++)mean+=(h[i]+l[i]+c[i])/3;mean/=p;double deviation=0;for(int i=end-p;i<end;i++)deviation+=Math.abs((h[i]+l[i]+c[i])/3-mean);deviation/=p;if(deviation==0)return 0;double current=(h[end-1]+l[end-1]+c[end-1])/3;return(current-mean)/(0.015*deviation);}
    private static Values pattern(double[]o,double[]h,double[]l,double[]c,Pattern p){return new Values(matchesPattern(o,h,l,c,o.length-1,p)?1:0,matchesPattern(o,h,l,c,o.length-2,p)?1:0);}
    private static boolean matchesPattern(double[]o,double[]h,double[]l,double[]c,int i,Pattern p){if(i<1||!Double.isFinite(o[i])||!Double.isFinite(c[i]))return false;double body=Math.max(Math.abs(c[i]-o[i]),1e-12),upper=h[i]-Math.max(o[i],c[i]),lower=Math.min(o[i],c[i])-l[i];return switch(p){case BULLISH_ENGULFING->c[i]>o[i]&&c[i-1]<o[i-1]&&o[i]<=c[i-1]&&c[i]>=o[i-1];case BEARISH_ENGULFING->c[i]<o[i]&&c[i-1]>o[i-1]&&o[i]>=c[i-1]&&c[i]<=o[i-1];case HAMMER->lower>=body*2&&upper<=body;case SHOOTING_STAR->upper>=body*2&&lower<=body;};}
    private static CandlestickInterval interval(String value){for(CandlestickInterval i:CandlestickInterval.values())if(i.getIntervalId().equalsIgnoreCase(value))return i;throw new IllegalArgumentException("Nicht unterstützter Timeframe: "+value);}
    private static String number(double v){return Double.isFinite(v)?String.format(java.util.Locale.ROOT,"%.6f",v):"n/a";}
    private record Values(double current,double previous){static Values invalid(){return new Values(Double.NaN,Double.NaN);}}
    private enum Pattern{BULLISH_ENGULFING,BEARISH_ENGULFING,HAMMER,SHOOTING_STAR}
    private record Cached(StrategyEvaluation value,Instant at){}
}
