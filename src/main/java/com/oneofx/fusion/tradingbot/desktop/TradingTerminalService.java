package com.oneofx.fusion.tradingbot.desktop;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.oneofx.fusion.client.FusionApiClient;
import com.oneofx.fusion.client.model.Candlestick;
import com.oneofx.fusion.client.model.CandlestickInterval;
import com.oneofx.fusion.tradingbot.desktop.OperationsRepository.OpenOrderRow;
import com.oneofx.fusion.tradingbot.desktop.OperationsRepository.PositionRow;
import com.oneofx.fusion.tradingbot.desktop.OperationsRepository.TradeMarkerRow;
import com.oneofx.fusion.tradingbot.desktop.TradingTerminalSnapshot.LineType;
import com.oneofx.fusion.tradingbot.desktop.TradingTerminalSnapshot.PriceLine;
import com.oneofx.fusion.tradingbot.desktop.TradingTerminalSnapshot.TradeMarker;
import com.oneofx.fusion.tradingbot.desktop.TradingTerminalSnapshot.TradingCandle;
import com.oneofx.fusion.tradingbot.grid.GridPreviewService;

/** Lädt Marktdaten und lokale Bot-Daten für eine konsistente Terminalansicht. */
public final class TradingTerminalService {
    private final CurrencySettingsRepository settings=new CurrencySettingsRepository();
    private final OperationsRepository operations=new OperationsRepository();
    private final GridPreviewService grids=new GridPreviewService();

    public TradingTerminalSnapshot load(long botId,String currency,String timeframe,int candleCount,
            FusionApiClient client)throws Exception{
        int limit=Math.max(30,Math.min(candleCount,1_440));
        List<Candlestick> candles=client.getCandlestickBars(currency,interval(timeframe),
                limit,null,null);
        CurrencySettings pair=settings.load(botId,currency);
        CurrencySettingsRepository.GridPreviewContext context=
                settings.loadGridPreviewContext(botId,currency);
        return assemble(currency,timeframe,pair,context,candles,
                operations.loadOpenOrders(botId),operations.loadPositions(botId),
                operations.loadTradeMarkers(botId,currency,500),grids);
    }

    static TradingTerminalSnapshot assemble(String currency,String timeframe,CurrencySettings pair,
            CurrencySettingsRepository.GridPreviewContext context,List<Candlestick> source,
            List<OpenOrderRow> orders,List<PositionRow> positions,List<TradeMarkerRow> markers,
            GridPreviewService gridService){
        String normalized=CurrencySettings.normalizeCurrency(currency);
        List<TradingCandle> candles=new ArrayList<>();
        for(Candlestick candle:source){
            double open=number(candle.getOpen()),high=number(candle.getHigh()),
                    low=number(candle.getLow()),close=number(candle.getClose());
            if(open<=0||high<=0||low<=0||close<=0||high<low)continue;
            candles.add(new TradingCandle(candle.getOpenTime(),open,high,low,close,
                    Math.max(0,number(candle.getVolume()))));
        }
        candles.sort(Comparator.comparingLong(TradingCandle::timestamp));
        if(candles.size()<2)throw new IllegalArgumentException(
                "Für den Chart wurden weniger als zwei gültige Kerzen geliefert.");
        double last=candles.get(candles.size()-1).close();
        double visibleHigh=candles.stream().mapToDouble(TradingCandle::high).max().orElse(last);
        double anchor=Math.max(visibleHigh,context==null?0:context.allTimeHigh());
        List<PriceLine> lines=new ArrayList<>();
        if(context!=null){
            GridPreviewService.Preview preview=gridService.calculate(pair,anchor,context.rules());
            preview.levels().stream().limit(250).forEach(level->lines.add(new PriceLine(
                    LineType.GRID,"Grid "+level.number(),level.price().doubleValue(),
                    level.quantity().doubleValue())));
        }
        for(OpenOrderRow order:orders)if(normalized.equals(normalize(order.currency()))&&order.price()>0)
            lines.add(new PriceLine(order.side().toLowerCase().contains("verkauf")
                    ?LineType.SELL_ORDER:LineType.BUY_ORDER,
                    order.side()+" · "+shortId(order.orderId()),order.price(),order.quantity()));
        for(PositionRow position:positions)if(normalized.equals(normalize(position.currency()))
                &&position.buyPrice()>0)lines.add(new PriceLine(LineType.POSITION,
                        "Position · "+shortId(position.orderId()),position.buyPrice(),position.quantity()));
        List<TradeMarker> tradeMarkers=markers.stream()
                .filter(marker->marker.timestamp()>0&&marker.price()>0)
                .map(marker->new TradeMarker(marker.timestamp(),marker.price(),marker.side(),
                        marker.label())).toList();
        return new TradingTerminalSnapshot(normalized,timeframe,candles,lines,tradeMarkers,last,
                System.currentTimeMillis());
    }

    private static CandlestickInterval interval(String value){
        for(CandlestickInterval interval:CandlestickInterval.values())
            if(interval.getIntervalId().equals(value))return interval;
        throw new IllegalArgumentException("Nicht unterstützter Timeframe: "+value);
    }

    private static String normalize(String value){
        try{return CurrencySettings.normalizeCurrency(value);}catch(RuntimeException ex){return "";}
    }

    private static double number(String value){
        try{double parsed=Double.parseDouble(value);return Double.isFinite(parsed)?parsed:0;}
        catch(Exception ex){return 0;}
    }

    private static String shortId(String value){
        if(value==null||value.isBlank())return "ohne ID";
        return value.length()>12?value.substring(0,12)+"…":value;
    }
}
