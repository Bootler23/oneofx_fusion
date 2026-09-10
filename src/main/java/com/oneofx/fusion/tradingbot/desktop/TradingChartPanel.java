package com.oneofx.fusion.tradingbot.desktop;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

import javax.swing.JPanel;

import com.oneofx.fusion.tradingbot.desktop.TradingTerminalSnapshot.LineType;
import com.oneofx.fusion.tradingbot.desktop.TradingTerminalSnapshot.PriceLine;
import com.oneofx.fusion.tradingbot.desktop.TradingTerminalSnapshot.TradeMarker;
import com.oneofx.fusion.tradingbot.desktop.TradingTerminalSnapshot.TradingCandle;

/** Nativer Kerzenchart mit Zoom, Verschieben, Fadenkreuz und Trading-Overlays. */
public final class TradingChartPanel extends JPanel {
    private static final DateTimeFormatter TIME=DateTimeFormatter.ofPattern("dd.MM. HH:mm")
            .withZone(ZoneId.systemDefault());
    private TradingTerminalSnapshot snapshot;
    private int visibleCount=120,offsetFromEnd,crosshairX=-1,crosshairY=-1;
    private int dragStartX,dragStartOffset;

    public TradingChartPanel(){
        setOpaque(true);setBackground(OneOfXTheme.SURFACE);setFocusable(true);
        setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
        ChartMouse mouse=new ChartMouse();addMouseListener(mouse);addMouseMotionListener(mouse);
        addMouseWheelListener(mouse);
    }

    public void setSnapshot(TradingTerminalSnapshot value){
        snapshot=value;offsetFromEnd=0;
        visibleCount=value==null?120:Math.min(120,value.candles().size());
        repaint();
    }

    public void resetView(){
        offsetFromEnd=0;visibleCount=snapshot==null?120:Math.min(120,snapshot.candles().size());
        repaint();
    }

    @Override protected void paintComponent(Graphics graphics){
        super.paintComponent(graphics);Graphics2D g=(Graphics2D)graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
        if(snapshot==null||snapshot.candles().size()<2){
            g.setColor(OneOfXTheme.TEXT_MUTED);g.setFont(OneOfXTheme.font(Font.PLAIN,14));
            g.drawString("Marktdaten laden, um den Trading-Chart anzuzeigen.",28,48);g.dispose();return;
        }
        int left=14,right=94,top=34,bottom=62,volumeHeight=58;
        int plotWidth=Math.max(1,getWidth()-left-right);
        int priceBottom=Math.max(top+1,getHeight()-bottom-volumeHeight);
        int priceHeight=priceBottom-top;
        List<TradingCandle> all=snapshot.candles();int end=Math.max(1,all.size()-offsetFromEnd);
        int start=Math.max(0,end-Math.min(visibleCount,end));
        List<TradingCandle> candles=all.subList(start,end);
        double min=candles.stream().mapToDouble(TradingCandle::low).min().orElse(0);
        double max=candles.stream().mapToDouble(TradingCandle::high).max().orElse(1);
        for(PriceLine line:snapshot.priceLines())if(line.type()!=LineType.GRID&&line.price()>0){
            min=Math.min(min,line.price());max=Math.max(max,line.price());
        }
        double padding=Math.max((max-min)*0.08,max*0.002);min=Math.max(0,min-padding);max+=padding;
        drawGrid(g,left,top,plotWidth,priceHeight,min,max);
        double slot=plotWidth/(double)Math.max(1,candles.size());
        double maxVolume=candles.stream().mapToDouble(TradingCandle::volume).max().orElse(1);
        for(int i=0;i<candles.size();i++)drawCandle(g,candles.get(i),i,left,top,priceBottom,
                volumeHeight,slot,min,max,maxVolume);
        drawPriceLines(g,left,top,plotWidth,priceHeight,min,max);
        drawMarkers(g,candles,start,left,top,priceHeight,slot,min,max);
        drawTimeAxis(g,candles,left,priceBottom+volumeHeight+18,plotWidth,slot);
        drawLegend(g,left,20);
        drawCrosshair(g,candles,left,top,plotWidth,priceBottom,volumeHeight,slot,min,max);
        g.dispose();
    }

    private static void drawGrid(Graphics2D g,int left,int top,int width,int height,
            double min,double max){
        g.setFont(OneOfXTheme.font(Font.PLAIN,10));
        for(int i=0;i<=5;i++){
            int y=top+i*height/5;g.setColor(OneOfXTheme.BORDER);g.drawLine(left,y,left+width,y);
            double price=max-(max-min)*i/5;g.setColor(OneOfXTheme.TEXT_MUTED);
            g.drawString(format(price),left+width+8,y+4);
        }
    }

    private static void drawCandle(Graphics2D g,TradingCandle candle,int index,int left,int top,
            int priceBottom,int volumeHeight,double slot,double min,double max,double maxVolume){
        int x=(int)Math.round(left+(index+0.5)*slot);boolean up=candle.close()>=candle.open();
        Color color=up?OneOfXTheme.SUCCESS:OneOfXTheme.ERROR;g.setColor(color);
        int high=y(candle.high(),top,priceBottom-top,min,max),low=y(candle.low(),top,priceBottom-top,min,max);
        int open=y(candle.open(),top,priceBottom-top,min,max),close=y(candle.close(),top,priceBottom-top,min,max);
        g.drawLine(x,high,x,low);int body=Math.max(2,(int)Math.min(14,slot*0.62));
        int bodyTop=Math.min(open,close),bodyHeight=Math.max(1,Math.abs(open-close));
        if(up)g.drawRect(x-body/2,bodyTop,body,bodyHeight);
        else g.fillRect(x-body/2,bodyTop,body,bodyHeight);
        int volume=maxVolume<=0?0:(int)Math.round(candle.volume()/maxVolume*(volumeHeight-8));
        g.setColor(new Color(color.getRed(),color.getGreen(),color.getBlue(),105));
        g.fillRect(x-Math.max(1,body/2),priceBottom+volumeHeight-volume,
                Math.max(2,body),volume);
    }

    private void drawPriceLines(Graphics2D g,int left,int top,int width,int height,
            double min,double max){
        for(PriceLine line:snapshot.priceLines()){
            if(line.price()<min||line.price()>max)continue;
            Color color=lineColor(line.type());int y=y(line.price(),top,height,min,max);
            g.setColor(color);g.setStroke(line.type()==LineType.GRID
                    ?new BasicStroke(1f,BasicStroke.CAP_BUTT,BasicStroke.JOIN_MITER,1,new float[]{4,5},0)
                    :new BasicStroke(1.5f));g.drawLine(left,y,left+width,y);
            if(line.type()!=LineType.GRID){
                String label=line.label()+"  "+format(line.price());int textWidth=g.getFontMetrics().stringWidth(label);
                g.setColor(new Color(24,26,32,220));g.fillRoundRect(left+width-textWidth-12,y-15,textWidth+10,17,6,6);
                g.setColor(color);g.drawString(label,left+width-textWidth-7,y-3);
            }
        }
        g.setStroke(new BasicStroke(1f));
    }

    private void drawMarkers(Graphics2D g,List<TradingCandle> candles,int globalStart,int left,
            int top,int height,double slot,double min,double max){
        long first=candles.get(0).timestamp(),last=candles.get(candles.size()-1).timestamp();
        for(TradeMarker marker:snapshot.markers()){
            if(marker.timestamp()<first||marker.timestamp()>last)continue;
            int global=nearest(snapshot.candles(),marker.timestamp());int local=global-globalStart;
            if(local<0||local>=candles.size())continue;
            int x=(int)Math.round(left+(local+0.5)*slot),py=y(marker.price(),top,height,min,max);
            boolean buy="BUY".equalsIgnoreCase(marker.side());g.setColor(buy?OneOfXTheme.SUCCESS:OneOfXTheme.ERROR);
            int direction=buy?1:-1;int[] xs={x-5,x+5,x};int[] ys={py+direction*9,py+direction*9,py};
            g.fillPolygon(xs,ys,3);
        }
    }

    private static void drawTimeAxis(Graphics2D g,List<TradingCandle> candles,int left,int y,
            int width,double slot){
        g.setColor(OneOfXTheme.TEXT_MUTED);g.setFont(OneOfXTheme.font(Font.PLAIN,10));
        int labels=Math.min(6,candles.size());
        for(int i=0;i<labels;i++){
            int index=labels==1?0:i*(candles.size()-1)/(labels-1);
            String text=TIME.format(Instant.ofEpochMilli(candles.get(index).timestamp()));
            int x=(int)Math.round(left+(index+0.5)*slot)-g.getFontMetrics().stringWidth(text)/2;
            g.drawString(text,Math.max(left,x),y);
        }
    }

    private void drawLegend(Graphics2D g,int x,int y){
        g.setFont(OneOfXTheme.font(Font.BOLD,12));g.setColor(OneOfXTheme.TEXT);
        g.drawString(snapshot.currency()+" · "+snapshot.timeframe()+" · "+format(snapshot.lastPrice()),x,y);
        String hint="Mausrad: Zoom · Ziehen: Verschieben · Doppelklick: Zurücksetzen";
        g.setFont(OneOfXTheme.font(Font.PLAIN,10));g.setColor(OneOfXTheme.TEXT_MUTED);
        g.drawString(hint,Math.max(x+260,getWidth()-g.getFontMetrics().stringWidth(hint)-18),y);
    }

    private void drawCrosshair(Graphics2D g,List<TradingCandle> candles,int left,int top,int width,
            int priceBottom,int volumeHeight,double slot,double min,double max){
        if(crosshairX<left||crosshairX>left+width||crosshairY<top
                ||crosshairY>priceBottom+volumeHeight)return;
        int index=Math.max(0,Math.min(candles.size()-1,
                (int)((crosshairX-left)/Math.max(slot,0.0001))));
        int x=(int)Math.round(left+(index+0.5)*slot);g.setColor(OneOfXTheme.BORDER_STRONG);
        g.setStroke(new BasicStroke(1f,BasicStroke.CAP_BUTT,BasicStroke.JOIN_MITER,1,
                new float[]{3,4},0));g.drawLine(x,top,x,priceBottom+volumeHeight);
        g.drawLine(left,crosshairY,left+width,crosshairY);
        TradingCandle candle=candles.get(index);
        String text=TIME.format(Instant.ofEpochMilli(candle.timestamp()))+"  O "+format(candle.open())
                +"  H "+format(candle.high())+"  L "+format(candle.low())
                +"  C "+format(candle.close())+"  Vol "+format(candle.volume());
        g.setFont(OneOfXTheme.font(Font.PLAIN,11));int textWidth=g.getFontMetrics().stringWidth(text);
        g.setColor(new Color(30,35,41,235));g.fillRoundRect(left+6,top+7,textWidth+14,21,7,7);
        g.setColor(OneOfXTheme.TEXT);g.drawString(text,left+13,top+22);
    }

    private static int nearest(List<TradingCandle> candles,long timestamp){
        int low=0,high=candles.size()-1;
        while(low<high){int mid=(low+high)/2;
            if(candles.get(mid).timestamp()<timestamp)low=mid+1;else high=mid;}
        if(low>0&&Math.abs(candles.get(low-1).timestamp()-timestamp)
                <Math.abs(candles.get(low).timestamp()-timestamp))return low-1;
        return low;
    }

    private static int y(double price,int top,int height,double min,double max){
        return top+(int)Math.round((max-price)/Math.max(1e-12,max-min)*height);
    }
    private static Color lineColor(LineType type){return switch(type){
        case GRID->OneOfXTheme.BORDER_STRONG;case BUY_ORDER->OneOfXTheme.SUCCESS;
        case SELL_ORDER->OneOfXTheme.ERROR;case POSITION->OneOfXTheme.INFO;};}
    private static String format(double value){
        if(Math.abs(value)>=1000)return String.format(java.util.Locale.GERMANY,"%,.2f",value);
        if(Math.abs(value)>=1)return String.format(java.util.Locale.GERMANY,"%.4f",value);
        return String.format(java.util.Locale.GERMANY,"%.8f",value);
    }

    private final class ChartMouse extends MouseAdapter {
        @Override public void mouseMoved(MouseEvent event){
            crosshairX=event.getX();crosshairY=event.getY();repaint();
        }
        @Override public void mouseExited(MouseEvent event){crosshairX=-1;crosshairY=-1;repaint();}
        @Override public void mousePressed(MouseEvent event){
            dragStartX=event.getX();dragStartOffset=offsetFromEnd;
        }
        @Override public void mouseDragged(MouseEvent event){
            if(snapshot==null)return;double slot=Math.max(1,(getWidth()-108)/(double)visibleCount);
            int delta=(int)Math.round((event.getX()-dragStartX)/slot);
            offsetFromEnd=Math.max(0,Math.min(snapshot.candles().size()-2,dragStartOffset+delta));
            crosshairX=event.getX();crosshairY=event.getY();repaint();
        }
        @Override public void mouseClicked(MouseEvent event){if(event.getClickCount()==2)resetView();}
        @Override public void mouseWheelMoved(MouseWheelEvent event){
            if(snapshot==null)return;int maximum=snapshot.candles().size();
            double factor=event.getWheelRotation()>0?1.22:0.82;
            visibleCount=Math.max(Math.min(20,maximum),Math.min(maximum,
                    (int)Math.round(visibleCount*factor)));
            offsetFromEnd=Math.min(offsetFromEnd,Math.max(0,maximum-visibleCount));repaint();
        }
    }
}
