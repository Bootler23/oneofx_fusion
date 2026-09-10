package com.oneofx.fusion.tradingbot.strategy;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.oneofx.fusion.tradingbot.Database.DatabaseSchema;
import com.oneofx.fusion.tradingbot.desktop.BaseConfigRepository;
import com.oneofx.fusion.tradingbot.desktop.CurrencySettings;
import com.oneofx.fusion.tradingbot.desktop.CurrencySettingsRepository;
import com.oneofx.fusion.tradingbot.desktop.ConfigPool;
import com.oneofx.fusion.tradingbot.grid.GridMode;
import com.oneofx.fusion.tradingbot.strategy.StrategyNode.Action;
import com.oneofx.fusion.tradingbot.strategy.StrategyNode.Comparator;
import com.oneofx.fusion.tradingbot.strategy.StrategyNode.Indicator;
import com.oneofx.fusion.tradingbot.strategy.StrategyNode.Logic;

public class StrategyRepositoryTest {
    private static final String[] PROPERTIES = {
            "ONEOFX_TRADING_DB", "ONEOFX_SETTINGS_DB", "ONEOFX_WPD_DB"
    };
    private final String[] previous = new String[PROPERTIES.length];
    private File database;
    private StrategyRepository strategies;
    private BaseConfigRepository configs;

    @Before public void setUp() throws Exception {
        database = File.createTempFile("oneofx-strategy-", ".db");
        for (int i=0;i<PROPERTIES.length;i++) {
            previous[i]=System.getProperty(PROPERTIES[i]);
            System.setProperty(PROPERTIES[i],database.getAbsolutePath());
        }
        DatabaseSchema.initialize();
        strategies=new StrategyRepository();
        configs=new BaseConfigRepository();
        new CurrencySettingsRepository().add(new CurrencySettings("BTCEUR",true,10,100,
                GridMode.GEOMETRIC,1,2,true,2.5,0.8));
    }

    @After public void tearDown() {
        for(int i=0;i<PROPERTIES.length;i++) {
            if(previous[i]==null)System.clearProperty(PROPERTIES[i]);
            else System.setProperty(PROPERTIES[i],previous[i]);
        }
        if(database!=null)database.delete();
    }

    @Test public void duplicatesNestedRuleTreeAndArchivesIt() throws Exception {
        StrategyDefinition source=strategies.create(1,"Momentum");
        StrategyNode root=strategies.addGroup(source.id(),null,Action.BUY,Logic.AND);
        StrategyNode nested=strategies.addGroup(source.id(),root.id(),Action.BUY,Logic.OR);
        strategies.addCondition(source.id(),nested.id(),Action.BUY,Indicator.RSI,
                Comparator.LESS_THAN,Indicator.VALUE,30,"1h",14,26);

        StrategyDefinition copy=strategies.duplicate(source.id(),"Momentum Kopie");
        assertEquals(3,strategies.loadNodes(copy.id()).size());
        assertTrue(strategies.loadNodes(copy.id()).stream().allMatch(n->n.strategyId()==copy.id()));

        strategies.archive(1,copy.id());
        assertEquals(1,strategies.loadAll(1).size());
    }

    @Test public void resolvesAssignmentsInDocumentedPriority() throws Exception {
        StrategyDefinition bot=strategies.create(1,"Bot");
        StrategyDefinition market=strategies.create(1,"Markt");
        StrategyDefinition poolStrategy=strategies.create(1,"Pool");
        StrategyDefinition pair=strategies.create(1,"Paar");
        strategies.assignBot(1,bot.id());
        strategies.assignMarketRegime(1,"BUY_ALLOWED",market.id());

        assertEquals(market.id(),strategies.resolve(1,"BTCEUR","BUY_ALLOWED").orElseThrow().id());

        ConfigPool pool=configs.createPool(1,"Momentum",configs.loadBase(1));
        configs.assignPool(1,"BTCEUR",pool.id());
        strategies.assignPool(1,pool.id(),poolStrategy.id());
        assertEquals(poolStrategy.id(),strategies.resolve(1,"BTCEUR","BUY_ALLOWED").orElseThrow().id());

        strategies.assignPair(1,"BTCEUR",pair.id());
        assertEquals(pair.id(),strategies.resolve(1,"BTCEUR","BUY_ALLOWED").orElseThrow().id());
        strategies.assignPair(1,"BTCEUR",null);
        assertEquals(poolStrategy.id(),strategies.resolve(1,"BTCEUR","BUY_ALLOWED").orElseThrow().id());
    }
}
