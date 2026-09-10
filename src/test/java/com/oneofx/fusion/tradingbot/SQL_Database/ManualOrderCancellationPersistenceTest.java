package com.oneofx.fusion.tradingbot.SQL_Database;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.oneofx.fusion.tradingbot.Database.DatabaseSchema;
import com.oneofx.fusion.tradingbot.SQL_Database.SellOrderPersistence.Reservation;

public class ManualOrderCancellationPersistenceTest {
    private static final String[] PROPERTIES={
            "ONEOFX_TRADING_DB","ONEOFX_SETTINGS_DB","ONEOFX_WPD_DB"};
    private final String[] previous=new String[PROPERTIES.length];
    private File database;

    @Before public void setUp()throws Exception{
        database=File.createTempFile("oneofx-manual-cancel-",".db");
        for(int i=0;i<PROPERTIES.length;i++){
            previous[i]=System.getProperty(PROPERTIES[i]);
            System.setProperty(PROPERTIES[i],database.getAbsolutePath());
        }
        DatabaseSchema.initialize();
    }

    @After public void tearDown(){
        for(int i=0;i<PROPERTIES.length;i++){
            if(previous[i]==null)System.clearProperty(PROPERTIES[i]);
            else System.setProperty(PROPERTIES[i],previous[i]);
        }
        if(database!=null)database.delete();
    }

    @Test public void confirmedBuyCancellationRemovesOnlyPendingPosition()throws Exception{
        execute("INSERT INTO positions(currency,BuyOrderId,Status,bot_id) "
                +"VALUES('BTCEUR','buy-cancel',0,1)");
        execute("INSERT INTO buy_attempts(attempt_id,currency_pair,quantity,stop_price,"
                +"limit_price,exchange_order_id,state,bot_id) VALUES"
                +"('attempt-buy','BTCEUR','0.1','100','100','buy-cancel','SUBMITTED',1)");

        new BuyOrderPersistence().recordCancelled("buy-cancel");

        assertEquals(0,integer("SELECT COUNT(*) FROM positions WHERE BuyOrderId='buy-cancel'"));
        assertEquals("CANCELED",text("SELECT state FROM buy_attempts WHERE attempt_id='attempt-buy'"));
    }

    @Test public void confirmedSellCancellationRestoresOriginalPositionAtomically()throws Exception{
        execute("INSERT INTO positions(currency,BuyOrderId,quantity,Status,bot_id) "
                +"VALUES('ETHEUR','buy-position',2,1,1)");
        execute("INSERT INTO historyPosition(currency,BuyOrderId,Quantity,bot_id) "
                +"VALUES('ETHEUR','buy-position',2,1)");
        SellOrderPersistence persistence=new SellOrderPersistence();
        Reservation reservation=persistence.reserve("buy-position","ETHEUR","2").orElseThrow();
        persistence.recordSubmitted(reservation,"sell-cancel","2026-09-10","14:20:00");

        persistence.recordCancelled("sell-cancel");

        assertEquals(1,integer("SELECT Status FROM positions WHERE BuyOrderId='buy-position'"));
        assertNull(nullableText("SELECT SellOrderId FROM historyPosition WHERE BuyOrderId='buy-position'"));
        assertEquals("CANCELED",text("SELECT state FROM sell_attempts WHERE attempt_id='"
                +reservation.attemptId()+"'"));
    }

    private void execute(String sql)throws Exception{
        try(Connection con=DriverManager.getConnection("jdbc:sqlite:"+database.getAbsolutePath());
            Statement statement=con.createStatement()){statement.executeUpdate(sql);}
    }
    private int integer(String sql)throws Exception{
        try(Connection con=DriverManager.getConnection("jdbc:sqlite:"+database.getAbsolutePath());
            Statement statement=con.createStatement();ResultSet rs=statement.executeQuery(sql)){
            rs.next();return rs.getInt(1);
        }
    }
    private String text(String sql)throws Exception{return nullableText(sql);}
    private String nullableText(String sql)throws Exception{
        try(Connection con=DriverManager.getConnection("jdbc:sqlite:"+database.getAbsolutePath());
            Statement statement=con.createStatement();ResultSet rs=statement.executeQuery(sql)){
            rs.next();return rs.getString(1);
        }
    }
}
