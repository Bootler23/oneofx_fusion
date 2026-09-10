package com.oneofx.fusion.tradingbot.Database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteConfig.JournalMode;
import org.sqlite.SQLiteConfig.SynchronousMode;
import org.sqlite.SQLiteConfig.TransactionMode;

/** Einheitliche SQLite-Verbindungen für Desktop, Engine und Migrationen. */
public final class SQLiteConnectionFactory {
    public static final int DEFAULT_BUSY_TIMEOUT_MS = 15_000;
    private static final String TIMEOUT_SETTING = "ONEOFX_SQLITE_BUSY_TIMEOUT_MS";

    private SQLiteConnectionFactory() { }

    public static Connection open(String jdbcUrl) throws SQLException {
        SQLiteConfig config = new SQLiteConfig();
        config.setBusyTimeout(busyTimeoutMillis());
        config.setJournalMode(JournalMode.WAL);
        config.setSynchronous(SynchronousMode.NORMAL);
        config.enforceForeignKeys(true);
        config.setTransactionMode(TransactionMode.IMMEDIATE);
        return DriverManager.getConnection(jdbcUrl, config.toProperties());
    }

    /** Ergänzt die zentralen Vorgaben als Sicherheitsnetz auch in Datenbank-URLs. */
    public static String configuredUrl(String jdbcUrl) {
        String separator = jdbcUrl.contains("?") ? "&" : "?";
        return jdbcUrl + separator + "busy_timeout=" + busyTimeoutMillis()
                + "&journal_mode=WAL&synchronous=NORMAL&foreign_keys=on"
                + "&transaction_mode=IMMEDIATE";
    }

    public static int busyTimeoutMillis() {
        String configured = System.getProperty(TIMEOUT_SETTING);
        if (configured == null || configured.isBlank()) configured = System.getenv(TIMEOUT_SETTING);
        if (configured == null || configured.isBlank()) return DEFAULT_BUSY_TIMEOUT_MS;
        try {
            int value = Integer.parseInt(configured.trim());
            return Math.max(1_000, Math.min(value, 120_000));
        } catch (NumberFormatException ignored) {
            return DEFAULT_BUSY_TIMEOUT_MS;
        }
    }

    @FunctionalInterface
    public interface Transaction<T> {
        T execute(Connection connection) throws SQLException;
    }

    /** Führt einen Schreibblock mit garantiertem Commit oder Rollback aus. */
    public static <T> T inTransaction(String jdbcUrl, Transaction<T> transaction)
            throws SQLException {
        try (Connection con = open(jdbcUrl)) {
            con.setAutoCommit(false);
            try {
                T result = transaction.execute(con);
                con.commit();
                return result;
            } catch (SQLException | RuntimeException ex) {
                try { con.rollback(); }
                catch (SQLException rollback) { ex.addSuppressed(rollback); }
                throw ex;
            }
        }
    }
}
