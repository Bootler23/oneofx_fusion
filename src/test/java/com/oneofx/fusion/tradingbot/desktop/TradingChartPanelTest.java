package com.oneofx.fusion.tradingbot.desktop;

import static org.junit.Assert.assertTrue;

import java.awt.Graphics2D;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import com.oneofx.fusion.tradingbot.desktop.TradingTerminalSnapshot.LineType;
import com.oneofx.fusion.tradingbot.desktop.TradingTerminalSnapshot.PriceLine;
import com.oneofx.fusion.tradingbot.desktop.TradingTerminalSnapshot.TradeMarker;
import com.oneofx.fusion.tradingbot.desktop.TradingTerminalSnapshot.TradingCandle;

public class TradingChartPanelTest {
    @Test public void paintsAndAcceptsZoomPanAndResetGestures(){
        List<TradingCandle> candles=new ArrayList<>();
        for(int i=0;i<80;i++)candles.add(new TradingCandle(1_700_000_000_000L+i*60_000,
                100+i*0.1,102+i*0.1,99+i*0.1,101+i*0.1,1000+i));
        TradingTerminalSnapshot snapshot=new TradingTerminalSnapshot("BTCEUR","1m",candles,
                List.of(new PriceLine(LineType.GRID,"Grid 1",103,0.1),
                        new PriceLine(LineType.BUY_ORDER,"Kauf",104,0.2),
                        new PriceLine(LineType.POSITION,"Position",105,0.3)),
                List.of(new TradeMarker(candles.get(40).timestamp(),104,"BUY","Trade")),
                candles.get(79).close(),System.currentTimeMillis());
        TradingChartPanel panel=new TradingChartPanel();panel.setSize(1000,620);
        panel.setSnapshot(snapshot);
        panel.dispatchEvent(new MouseWheelEvent(panel,MouseEvent.MOUSE_WHEEL,
                System.currentTimeMillis(),0,500,300,0,false,
                MouseWheelEvent.WHEEL_UNIT_SCROLL,1,-1));
        panel.dispatchEvent(new MouseEvent(panel,MouseEvent.MOUSE_PRESSED,
                System.currentTimeMillis(),0,500,300,1,false));
        panel.dispatchEvent(new MouseEvent(panel,MouseEvent.MOUSE_DRAGGED,
                System.currentTimeMillis(),0,560,300,0,false));
        panel.dispatchEvent(new MouseEvent(panel,MouseEvent.MOUSE_CLICKED,
                System.currentTimeMillis(),0,500,300,2,false));
        BufferedImage image=new BufferedImage(1000,620,BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics=image.createGraphics();panel.paint(graphics);graphics.dispose();
        int background=image.getRGB(0,0);boolean different=false;
        for(int y=20;y<600&&!different;y+=20)for(int x=20;x<980;x+=20)
            if(image.getRGB(x,y)!=background){different=true;break;}
        assertTrue(different);
    }
}
