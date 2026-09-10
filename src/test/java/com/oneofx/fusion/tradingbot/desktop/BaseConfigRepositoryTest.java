package com.oneofx.fusion.tradingbot.desktop;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.oneofx.fusion.tradingbot.Database.DatabaseSchema;
import com.oneofx.fusion.tradingbot.grid.GridMode;

public class BaseConfigRepositoryTest {
    private static final String[] PROPERTIES = {
            "ONEOFX_TRADING_DB", "ONEOFX_SETTINGS_DB", "ONEOFX_WPD_DB"
    };
    private final String[] previous = new String[PROPERTIES.length];
    private File database;
    private BaseConfigRepository configs;
    private CurrencySettingsRepository currencies;

    @Before
    public void setUp() throws Exception {
        database = File.createTempFile("oneofx-baseconfig-", ".db");
        for (int i=0;i<PROPERTIES.length;i++) {
            previous[i]=System.getProperty(PROPERTIES[i]);
            System.setProperty(PROPERTIES[i],database.getAbsolutePath());
        }
        DatabaseSchema.initialize();
        configs=new BaseConfigRepository();
        currencies=new CurrencySettingsRepository();
        currencies.add(new CurrencySettings("BTCEUR",true,10,100,
                GridMode.GEOMETRIC,1,2,true,2.5,0.8));
    }

    @After public void tearDown(){
        for(int i=0;i<PROPERTIES.length;i++){
            if(previous[i]==null)System.clearProperty(PROPERTIES[i]);else System.setProperty(PROPERTIES[i],previous[i]);
        }
        if(database!=null)database.delete();
    }

    @Test
    public void poolOverridesBaseOnlyForAssignedPair() throws Exception {
        BotBaseConfig base=configs.loadBase(1);
        BotBaseConfig aggressive=new BotBaseConfig(1,"STOP_LIMIT","MARKET",15,5,30,
                7,true,2,0.5,true,1440,true,4,3,1.5);
        ConfigPool pool=configs.createPool(1,"Aggressiv",aggressive);
        configs.assignPool(1,"BTCEUR",pool.id());

        assertEquals("STOP_LIMIT",configs.loadEffective(1,"BTCEUR").buyOrderType());
        assertEquals(7,configs.loadEffective(1,"BTCEUR").takeProfitPercent(),0.0001);
        assertEquals(base,configs.loadBase(1));

        configs.archivePool(1,pool.id());
        assertNull(configs.loadAssignedPoolId(1,"BTCEUR"));
        assertEquals(base,configs.loadEffective(1,"BTCEUR"));
    }

    @Test
    public void trailingBuyPersistsDropAndReboundState() throws Exception {
        assertFalse(configs.evaluateTrailingBuy(1,"BTCEUR",100,1,0.3));
        assertFalse(configs.evaluateTrailingBuy(1,"BTCEUR",98,1,0.3));
        assertTrue(configs.evaluateTrailingBuy(1,"BTCEUR",98.5,1,0.3));
        configs.resetTrailingBuy(1,"BTCEUR",98.5);
        assertFalse(configs.evaluateTrailingBuy(1,"BTCEUR",98.6,1,0.3));
    }
}
