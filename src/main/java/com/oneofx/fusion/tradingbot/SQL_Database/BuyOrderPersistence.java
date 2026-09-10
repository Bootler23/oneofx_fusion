package com.oneofx.fusion.tradingbot.SQL_Database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Objects;
import java.util.UUID;

import com.oneofx.fusion.tradingbot.Database.dbUrl;
import com.oneofx.fusion.tradingbot.domain.Position;

/**
 * Durable journal for the non-transactional boundary between Fusion and SQLite.
 * A SUBMITTING row is committed before the HTTP request. It therefore survives
 * a crash even when the exchange response could not yet be stored locally.
 */
public final class BuyOrderPersistence {

    static final String ATTEMPT_SUBMITTING = "SUBMITTING";
    static final String ATTEMPT_SUBMITTED = "SUBMITTED";
    static final String ATTEMPT_REJECTED = "REJECTED";
    static final String ATTEMPT_RECONCILIATION_REQUIRED = "RECONCILIATION_REQUIRED";
    static final String ATTEMPT_CANCELED = "CANCELED";

    private final String jdbcUrl;
    private final PositionDAO positionDAO;

    public BuyOrderPersistence() {
        this(dbUrl.getoneOfX());
    }

    public BuyOrderPersistence(String jdbcUrl) {
        this.jdbcUrl = Objects.requireNonNull(jdbcUrl, "jdbcUrl");
        this.positionDAO = new PositionDAO();
    }

    public Attempt start(String currencyPair, String quantity, String stopPrice, String limitPrice)
            throws SQLException {
        requireText(currencyPair, "currencyPair");
        requireText(quantity, "quantity");
        requireText(stopPrice, "stopPrice");
        requireText(limitPrice, "limitPrice");

        String attemptId = UUID.randomUUID().toString();
        try (Connection con = openConnection()) {
            ensureSchema(con);
            try (PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO buy_attempts (attempt_id, currency_pair, quantity, stop_price, "
                            + "limit_price, state) VALUES (?, ?, ?, ?, ?, ?)")) {
                ps.setString(1, attemptId);
                ps.setString(2, currencyPair);
                ps.setString(3, quantity);
                ps.setString(4, stopPrice);
                ps.setString(5, limitPrice);
                ps.setString(6, ATTEMPT_SUBMITTING);
                requireExactlyOne(ps.executeUpdate(), "buy attempt insert", attemptId);
            }
        }
        return new Attempt(attemptId);
    }

    /** Atomically stores the accepted exchange order and closes its journal entry. */
    public void recordSubmitted(Attempt attempt, String exchangeOrderId, Position position)
            throws SQLException {
        Objects.requireNonNull(attempt, "attempt");
        requireText(exchangeOrderId, "exchangeOrderId");
        Objects.requireNonNull(position, "position");

        try (Connection con = openConnection()) {
            con.setAutoCommit(false);
            try {
                ensureSchema(con);
                positionDAO.insert(con, position);
                int rows = updateAttempt(con, attempt.attemptId(), exchangeOrderId,
                        ATTEMPT_SUBMITTED, null, ATTEMPT_SUBMITTING);
                requireExactlyOne(rows, "buy attempt submission", attempt.attemptId());
                con.commit();
            } catch (SQLException | RuntimeException ex) {
                rollback(con, ex);
                throw ex;
            }
        }
    }

    public void recordRejected(Attempt attempt, String reason) throws SQLException {
        changeState(attempt, null, ATTEMPT_REJECTED, reason);
    }

    public void requireReconciliation(Attempt attempt, String exchangeOrderId, String reason)
            throws SQLException {
        changeState(attempt, exchangeOrderId, ATTEMPT_RECONCILIATION_REQUIRED, reason);
    }

    /** Removes an unfilled local buy only after Fusion confirmed a terminal cancel. */
    public void recordCancelled(String exchangeOrderId) throws SQLException {
        requireText(exchangeOrderId, "exchangeOrderId");
        try (Connection con = openConnection()) {
            con.setAutoCommit(false);
            try {
                ensureSchema(con);
                try (PreparedStatement ps = con.prepareStatement(
                        "DELETE FROM positions WHERE BuyOrderId=? AND Status=0 "
                                + "AND bot_id=(SELECT selected_bot_id FROM runtimeState WHERE id=1)")) {
                    ps.setString(1, exchangeOrderId);
                    requireExactlyOne(ps.executeUpdate(), "cancelled buy removal", exchangeOrderId);
                }
                try (PreparedStatement ps = con.prepareStatement(
                        "UPDATE buy_attempts SET state=?,error_message=NULL,updated_at=CURRENT_TIMESTAMP "
                                + "WHERE exchange_order_id=? AND state=? "
                                + "AND bot_id=(SELECT selected_bot_id FROM runtimeState WHERE id=1)")) {
                    ps.setString(1, ATTEMPT_CANCELED); ps.setString(2, exchangeOrderId);
                    ps.setString(3, ATTEMPT_SUBMITTED);
                    if (ps.executeUpdate() > 1) throw new SQLException(
                            "More than one buy attempt matched " + exchangeOrderId);
                }
                con.commit();
            } catch (SQLException | RuntimeException ex) {
                rollback(con, ex);
                throw ex;
            }
        }
    }

    private void changeState(Attempt attempt, String exchangeOrderId, String state, String reason)
            throws SQLException {
        Objects.requireNonNull(attempt, "attempt");
        try (Connection con = openConnection()) {
            ensureSchema(con);
            int rows = updateAttempt(con, attempt.attemptId(), exchangeOrderId,
                    state, reason, ATTEMPT_SUBMITTING);
            requireExactlyOne(rows, "buy attempt state change", attempt.attemptId());
        }
    }

    private Connection openConnection() throws SQLException {
        return com.oneofx.fusion.tradingbot.Database.SQLiteConnectionFactory.open(jdbcUrl);
    }

    private static int updateAttempt(Connection con, String attemptId, String exchangeOrderId,
            String state, String reason, String expectedState) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "UPDATE buy_attempts SET exchange_order_id = COALESCE(?, exchange_order_id), "
                        + "state = ?, error_message = ?, updated_at = CURRENT_TIMESTAMP "
                        + "WHERE attempt_id = ? AND state = ?")) {
            ps.setString(1, exchangeOrderId);
            ps.setString(2, state);
            ps.setString(3, truncate(reason));
            ps.setString(4, attemptId);
            ps.setString(5, expectedState);
            return ps.executeUpdate();
        }
    }

    private static void ensureSchema(Connection con) throws SQLException {
        try (Statement statement = con.createStatement()) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS buy_attempts ("
                    + "attempt_id TEXT PRIMARY KEY, "
                    + "currency_pair TEXT NOT NULL, "
                    + "quantity TEXT NOT NULL, "
                    + "stop_price TEXT NOT NULL, "
                    + "limit_price TEXT NOT NULL, "
                    + "exchange_order_id TEXT, "
                    + "state TEXT NOT NULL, "
                    + "error_message TEXT, "
                    + "created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP, "
                    + "updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP"
                    + ")");
            statement.executeUpdate("CREATE UNIQUE INDEX IF NOT EXISTS uq_buy_attempt_exchange_order "
                    + "ON buy_attempts(exchange_order_id) WHERE exchange_order_id IS NOT NULL");
            if (columnExists(con, "buy_attempts", "bot_id")) {
                statement.executeUpdate("DROP INDEX IF EXISTS uq_active_buy_attempt");
                statement.executeUpdate("CREATE UNIQUE INDEX IF NOT EXISTS uq_active_buy_attempt_bot "
                        + "ON buy_attempts(bot_id, currency_pair, stop_price) "
                        + "WHERE state IN ('SUBMITTING', 'RECONCILIATION_REQUIRED')");
            } else {
                statement.executeUpdate("CREATE UNIQUE INDEX IF NOT EXISTS uq_active_buy_attempt "
                        + "ON buy_attempts(currency_pair, stop_price) "
                        + "WHERE state IN ('SUBMITTING', 'RECONCILIATION_REQUIRED')");
            }
        }
    }

    private static boolean columnExists(Connection con, String table, String column)
            throws SQLException {
        try (Statement statement = con.createStatement();
             java.sql.ResultSet rs = statement.executeQuery("PRAGMA table_info(" + table + ")")) {
            while (rs.next()) {
                if (column.equalsIgnoreCase(rs.getString("name"))) return true;
            }
            return false;
        }
    }

    private static void requireExactlyOne(int rows, String operation, String attemptId)
            throws SQLException {
        if (rows != 1) {
            throw new SQLException(operation + " changed " + rows
                    + " rows for attempt " + attemptId + "; expected exactly one");
        }
    }

    private static void rollback(Connection con, Exception original) {
        try {
            con.rollback();
        } catch (SQLException rollbackError) {
            original.addSuppressed(rollbackError);
        }
    }

    private static String truncate(String value) {
        if (value == null || value.length() <= 1000) return value;
        return value.substring(0, 1000);
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }

    public record Attempt(String attemptId) {
        public Attempt {
            requireText(attemptId, "attemptId");
        }
    }
}
