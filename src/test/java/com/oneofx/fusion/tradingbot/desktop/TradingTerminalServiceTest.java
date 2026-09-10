package com.oneofx.fusion.tradingbot.desktop;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Test;

import com.oneofx.fusion.client.model.Candlestick;
import com.oneofx.fusion.tradingbot.desktop.OperationsRepository.OpenOrderRow;
import com.oneofx.fusion.tradingbot.desktop.OperationsRepository.PositionRow;
import com.oneofx.fusion.tradingbot.desktop.OperationsRepository.TradeMarkerRow;
import com.oneofx.fusion.tradingbot.grid.GridMode;
import com.oneofx.fusion.tradingbot.grid.GridPreviewService;

public class TradingTerminalServiceTest {
    @Test public void combinesCandlesGridOrdersPositionsAndMarkers(){
        CurrencySettings settings=new CurrencySettings("BTCEUR",true,100,300,
                GridMode.GEOMETRIC,1,2,true,2.5,0.8);
        CurrencySettingsRepository.GridPreviewContext context=
                new CurrencySettingsRepository.GridPreviewContext(105,
                        new GridPreviewService.Rules(java.math.BigDecimal.valueOf(0.01),
                                java.math.BigDecimal.valueOf(0.001),5,100000));
        TradingTerminalSnapshot snapshot=TradingTerminalService.assemble("BTC-EUR","1h",
                settings,context,List.of(candle(1,100,106,99,104),
                        candle(2,104,108,103,107),invalidCandle()),
                List.of(new OpenOrderRow("Kauf","BTCEUR","buy-order",101,0.2,0,"Offen"),
                        new OpenOrderRow("Verkauf","ETHEUR","other",2000,1,0,"Offen")),
                List.of(new PositionRow("BTCEUR","position",102,102,0.3,30,0,0,1,
                        "Offen","")),
                List.of(new TradeMarkerRow(1_000,100,"BUY","trade")),
                new GridPreviewService());



        assertEquals("BTCEUR",snapshot.currency());assertEquals(2,snapshot.candles().size());
        assertEquals(107,snapshot.lastPrice(),0.000001);assertEquals(1,snapshot.markers().size());
        assertEquals(5,snapshot.priceLines().size());
        assertTrue(snapshot.priceLines().stream().anyMatch(line->line.type()
                ==TradingTerminalSnapshot.LineType.BUY_ORDER));
        assertTrue(snapshot.priceLines().stream().anyMatch(line->line.type()
                ==TradingTerminalSnapshot.LineType.POSITION));
    }

    private static Candlestick candle(long timestamp,double open,double high,double low,double close){
        Candlestick candle=new Candlestick();candle.setTimestamp(timestamp);
        candle.setOpen(Double.toString(open));candle.setHigh(Double.toString(high));
        candle.setLow(Double.toString(low));candle.setClose(Double.toString(close));
        candle.setVolume("1000");return candle;
    }
    private static Candlestick invalidCandle(){
        Candlestick candle=candle(3,100,90,110,100);return candle;
    }
}
