package com.oneofx.fusion.tradingbot.desktop;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.oneofx.fusion.client.model.OrderSide;
import com.oneofx.fusion.client.model.OrderType;
import com.oneofx.fusion.tradingbot.Database.DatabaseSchema;
import com.oneofx.fusion.tradingbot.desktop.ManualOrderService.PreparedOrder;
import com.oneofx.fusion.tradingbot.desktop.ManualOrderService.Request;

public class ManualOrderServiceTest {
    private static final String[] PROPERTIES={
            "ONEOFX_TRADING_DB","ONEOFX_SETTINGS_DB","ONEOFX_WPD_DB"};
    private final String[] previous=new String[PROPERTIES.length];
    private File database;
    private PaperTradingRepository paper;
    private OperationsRepository operations;
    private ManualOrderService service;
    private BotProfile bot;

    @Before public void setUp()throws Exception{
        database=File.createTempFile("oneofx-manual-service-",".db");
        for(int i=0;i<PROPERTIES.length;i++){
            previous[i]=System.getProperty(PROPERTIES[i]);
            System.setProperty(PROPERTIES[i],database.getAbsolutePath());
        }
        DatabaseSchema.initialize();
        execute("INSERT INTO tradingRules(currency,tickSize,stepSize,minQty,minOrderAmount,maxOrderAmount) "
                +"VALUES('BTCEUR','0.01','0.001','0.001','5','1000000')");
        paper=new PaperTradingRepository();operations=new OperationsRepository();
        paper.ensureAccount(1,1000);
        service=new ManualOrderService();
        bot=new BotProfile(1,"Paper",true,false,BotProfile.Mode.PAPER,"GRID",
                1000,1000,20,20,0.25,0.1);
    }

    @After public void tearDown(){
        for(int i=0;i<PROPERTIES.length;i++){
            if(previous[i]==null)System.clearProperty(PROPERTIES[i]);
            else System.setProperty(PROPERTIES[i],previous[i]);
        }
        if(database!=null)database.delete();
    }

    @Test public void paperLimitBuyCanBePreviewedReplacedAndCancelled()throws Exception{
        PreparedOrder first=service.prepare(bot,new Request("BTC-EUR",OrderSide.BUY,
                OrderType.LIMIT,0.1019,99.999,100,null));
        assertEquals("100.00",first.priceText());
        assertEquals("0.101",first.quantityText());
        service.submit(bot,first,null);
        OperationsRepository.OpenOrderRow old=operations.loadOpenOrders(1).get(0);
        assertEquals(OperationsRepository.OrderOrigin.PAPER,old.origin());

        PreparedOrder replacement=service.prepareReplacement(bot,old,new Request("BTCEUR",
                OrderSide.BUY,OrderType.LIMIT,0.2,90,100,null));
        service.replace(bot,old,replacement,null);
        assertEquals(1,operations.loadOpenOrders(1).size());
        OperationsRepository.OpenOrderRow changed=operations.loadOpenOrders(1).get(0);
        assertEquals(90.0,changed.price(),0.000001);
        assertEquals(0.2,changed.quantity(),0.000001);
        assertTrue(service.cancel(bot,changed,null).clean());
        assertEquals(0,operations.loadOpenOrders(1).size());
        assertEquals(1000.0,paper.loadBalances(1).stream()
                .filter(value->"EUR".equals(value.asset())).findFirst().orElseThrow().available(),
                0.000001);
    }

    private void execute(String sql)throws Exception{
        try(Connection con=DriverManager.getConnection("jdbc:sqlite:"+database.getAbsolutePath());
            Statement statement=con.createStatement()){statement.executeUpdate(sql);}
    }
}
