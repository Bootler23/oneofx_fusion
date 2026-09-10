package com.oneofx.fusion.tradingbot.Database;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.Test;

public class SQLiteConnectionFactoryTest {
    @Test public void aktiviertEinheitlichePragmasAuchImKompatibilitaetsUrl()
            throws Exception {
        File database = File.createTempFile("oneofx-sqlite-config-", ".db");
        String raw = "jdbc:sqlite:" + database.getAbsolutePath();
        try (Connection con = SQLiteConnectionFactory.open(raw)) {
            assertCentralPragmas(con);
        }
        try (Connection con = DriverManager.getConnection(
                SQLiteConnectionFactory.configuredUrl(raw))) {
            assertCentralPragmas(con);
        } finally {
            database.delete();
        }
    }

    @Test public void zweiterSchreiberWartetStattMitSqliteBusyAbzubrechen() throws Exception {
        File database = File.createTempFile("oneofx-sqlite-concurrency-", ".db");
        String url = "jdbc:sqlite:" + database.getAbsolutePath();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try (Connection first = SQLiteConnectionFactory.open(url)) {
            try (Statement statement = first.createStatement()) {
                statement.executeUpdate("CREATE TABLE values_table(id INTEGER PRIMARY KEY,value INTEGER)");
                statement.executeUpdate("INSERT INTO values_table VALUES(1,0)");
            }
            first.setAutoCommit(false);
            try (Statement statement = first.createStatement()) {
                statement.executeUpdate("UPDATE values_table SET value=1 WHERE id=1");
            }

            CountDownLatch attemptingWrite = new CountDownLatch(1);
            Future<Boolean> second = executor.submit(() -> {
                try (Connection con = SQLiteConnectionFactory.open(url);
                     Statement statement = con.createStatement()) {
                    attemptingWrite.countDown();
                    statement.executeUpdate("UPDATE values_table SET value=2 WHERE id=1");
                    return true;
                }
            });
            assertTrue(attemptingWrite.await(2, TimeUnit.SECONDS));
            Thread.sleep(100);
            assertFalse("Der zweite Schreiber muss auf den ersten warten", second.isDone());
            first.commit();
            first.setAutoCommit(true);
            assertTrue(second.get(3, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
            database.delete();
        }
    }

    @Test public void transaktionsHelferFuehrtCommitUndRollbackGarantiertAus()
            throws Exception {
        File database = File.createTempFile("oneofx-sqlite-transaction-", ".db");
        String url = "jdbc:sqlite:" + database.getAbsolutePath();
        try {
            try (Connection con = SQLiteConnectionFactory.open(url);
                 Statement statement = con.createStatement()) {
                statement.executeUpdate("CREATE TABLE entries(value INTEGER)");
            }
            SQLiteConnectionFactory.inTransaction(url, con -> {
                try (Statement statement = con.createStatement()) {
                    statement.executeUpdate("INSERT INTO entries VALUES(1)");
                }
                return null;
            });
            try {
                SQLiteConnectionFactory.inTransaction(url, con -> {
                    try (Statement statement = con.createStatement()) {
                        statement.executeUpdate("INSERT INTO entries VALUES(2)");
                    }
                    throw new SQLException("absichtlicher Testabbruch");
                });
            } catch (SQLException expected) {
                assertEquals("absichtlicher Testabbruch", expected.getMessage());
            }
            try (Connection con = SQLiteConnectionFactory.open(url);
                 Statement statement = con.createStatement();
                 ResultSet rs = statement.executeQuery("SELECT COUNT(*) FROM entries")) {
                assertTrue(rs.next());
                assertEquals(1, rs.getInt(1));
            }
        } finally {
            database.delete();
        }
    }

    private static String pragma(Connection con, String name) throws SQLException {
        try (Statement statement = con.createStatement();
             ResultSet rs = statement.executeQuery("PRAGMA " + name)) {
            if (!rs.next()) throw new SQLException("PRAGMA ohne Ergebnis: " + name);
            return rs.getString(1);
        }
    }

    private static void assertCentralPragmas(Connection con) throws SQLException {
        assertEquals("wal", pragma(con, "journal_mode").toLowerCase());
        assertEquals("1", pragma(con, "foreign_keys"));
        assertEquals(Integer.toString(SQLiteConnectionFactory.busyTimeoutMillis()),
                pragma(con, "busy_timeout"));
        assertEquals("1", pragma(con, "synchronous"));
    }
}
