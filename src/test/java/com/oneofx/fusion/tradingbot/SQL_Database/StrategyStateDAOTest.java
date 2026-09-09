package com.oneofx.fusion.tradingbot.SQL_Database;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class StrategyStateDAOTest {

    private static final String DATABASE_PROPERTY = "ONEOFX_TRADING_DB";

    private File database;
    private String previousDatabase;
    private StrategyStateDAO dao;

    @Before
    public void setUp() throws Exception {
        previousDatabase = System.getProperty(DATABASE_PROPERTY);
        database = File.createTempFile("oneofx-strategy-state-", ".db");
        System.setProperty(DATABASE_PROPERTY, database.getAbsolutePath());
        try (Connection con = DriverManager.getConnection("jdbc:sqlite:" + database.getAbsolutePath());
             Statement statement = con.createStatement()) {
            statement.executeUpdate("CREATE TABLE strategyState ("
                    + "currency TEXT PRIMARY KEY, buyBlockedUntil INTEGER NOT NULL DEFAULT 0, "
                    + "reason TEXT, updatedAt INTEGER NOT NULL)");
        }
        dao = new StrategyStateDAO();
    }

    @After
    public void tearDown() {
        if (previousDatabase == null) {
            System.clearProperty(DATABASE_PROPERTY);
        } else {
            System.setProperty(DATABASE_PROPERTY, previousDatabase);
        }
        if (database != null) database.delete();
    }

    @Test
    public void persistsCooldownAndNeverShortensIt() {
        long now = System.currentTimeMillis();
        dao.blockBuysUntil("BTCEUR", now + 60_000, "FIRST", now);
        dao.blockBuysUntil("BTCEUR", now + 30_000, "SECOND", now + 1);

        assertEquals(now + 60_000, dao.getBuyBlockedUntil("BTCEUR"));
        assertTrue(dao.isBuyBlocked("BTCEUR"));
    }

    @Test
    public void expiredCooldownDoesNotBlockBuying() {
        long now = System.currentTimeMillis();
        dao.blockBuysUntil("BTCEUR", now - 1, "OLD", now - 2);

        assertFalse(dao.isBuyBlocked("BTCEUR"));
    }
}
