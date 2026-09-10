package com.oneofx.fusion.tradingbot.service;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.oneofx.fusion.tradingbot.Database.DatabaseSchema;
import com.oneofx.fusion.tradingbot.bot.BotRuntime;
import com.oneofx.fusion.tradingbot.desktop.BotProfile;
import com.oneofx.fusion.tradingbot.desktop.BotRepository;

public class BotRiskServiceTest {
    private static final String[] PROPERTIES = {
            "ONEOFX_TRADING_DB", "ONEOFX_SETTINGS_DB", "ONEOFX_WPD_DB"
    };
    private final String[] previous = new String[PROPERTIES.length];
    private File database;
    private BotRiskService service;

    @Before
    public void setUp() throws Exception {
        database = File.createTempFile("oneofx-bot-risk-", ".db");
        for (int i = 0; i < PROPERTIES.length; i++) {
            previous[i] = System.getProperty(PROPERTIES[i]);
            System.setProperty(PROPERTIES[i], database.getAbsolutePath());
        }
        BotRuntime.select(1);
        DatabaseSchema.initialize();
        BotRepository repository = new BotRepository();
        repository.save(new BotProfile(1, "Standard-Bot", true, false,
                BotProfile.Mode.LIVE, "GRID", 20, 20, 2, 2, 0.25, 0.10));
        service = new BotRiskService();
    }

    @After
    public void tearDown() {
        BotRuntime.select(1);
        for (int i = 0; i < PROPERTIES.length; i++) {
            if (previous[i] == null) System.clearProperty(PROPERTIES[i]);
            else System.setProperty(PROPERTIES[i], previous[i]);
        }
        if (database != null) database.delete();
    }

    @Test
    public void blocksPurchaseBeyondExposureLimit() throws Exception {
        assertTrue(service.evaluateNextBuy(10).allowed());
        execute("INSERT INTO positions (currency, BuyOrderId, Status, BuyAmount) "
                + "VALUES ('BTCEUR', 'existing', 1, 15)");
        assertFalse(service.evaluateNextBuy(10).allowed());
    }

    @Test
    public void blocksDisabledAndPaperBots() throws Exception {
        BotRepository repository = new BotRepository();
        BotProfile bot = repository.load(1);
        repository.save(new BotProfile(bot.id(), bot.name(), false, false,
                bot.mode(), bot.strategy(), bot.budget(), bot.maxExposure(), 2, 2,
                bot.paperFeePercent(), bot.paperSlippagePercent()));
        assertFalse(service.evaluateNextBuy(5).allowed());
        repository.save(new BotProfile(bot.id(), bot.name(), true, false,
                BotProfile.Mode.PAPER, bot.strategy(), bot.budget(), bot.maxExposure(), 2, 2,
                bot.paperFeePercent(), bot.paperSlippagePercent()));
        assertFalse(service.evaluateNextBuy(5).allowed());
    }

    private void execute(String sql) throws Exception {
        try (Connection con = DriverManager.getConnection("jdbc:sqlite:" + database.getAbsolutePath());
             Statement statement = con.createStatement()) {
            statement.executeUpdate(sql);
        }
    }
}
