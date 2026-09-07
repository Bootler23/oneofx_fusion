package com.oneofx.fusion.tradingbot.SQL_Database;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import com.oneofx.fusion.tradingbot.Database.dbUrl;
import com.oneofx.fusion.tradingbot.constants.TradingConstants;

/**
 * Persists the state transition around an external Fusion sell order.
 *
 * <p>The HTTP request itself cannot participate in a SQLite transaction. A
 * position is therefore reserved and committed before the request is sent.
 * After Fusion accepts the order, HIST and positions are changed together on
 * one connection. An ambiguous outcome deliberately remains reserved until it
 * is reconciled.</p>
 */
public final class SellOrderPersistence {

    static final String ATTEMPT_SUBMITTING = "SUBMITTING";
    static final String ATTEMPT_SUBMITTED = "SUBMITTED";
    static final String ATTEMPT_REJECTED = "REJECTED";
    static final String ATTEMPT_RECONCILIATION_REQUIRED = "RECONCILIATION_REQUIRED";
    static final String ATTEMPT_COMPLETED = "COMPLETED";

    private final String jdbcUrl;

    public SellOrderPersistence() {
        this(dbUrl.getoneOfX());
    }

    public SellOrderPersistence(String jdbcUrl) {
        this.jdbcUrl = Objects.requireNonNull(jdbcUrl, "jdbcUrl");
    }

    /**
     * Atomically claims a sellable position. An empty result means another
     * worker already claimed it or the position is no longer sellable.
     */
    public Optional<Reservation> reserve(String buyOrderId, String currencyPair, String quantity)
            throws SQLException {
        requireText(buyOrderId, "buyOrderId");
        requireText(currencyPair, "currencyPair");
        requireText(quantity, "quantity");

        try (Connection con = openConnection()) {
            con.setAutoCommit(false);
            try {
                ensureSchema(con);

                Integer previousStatus = findSellableStatus(con, buyOrderId);
                if (previousStatus == null) {
                    con.rollback();
                    return Optional.empty();
                }
                if (!historyIsReadyForSell(con, buyOrderId)) {
                    con.rollback();
                    return Optional.empty();
                }

                int positionRows;
                try (PreparedStatement ps = con.prepareStatement(
                        "UPDATE positions SET Status = ? "
                                + "WHERE BuyOrderId = ? AND Status = ?")) {
                    ps.setInt(1, TradingConstants.POSITION_STATUS_SELL_SUBMITTING);
                    ps.setString(2, buyOrderId);
                    ps.setInt(3, previousStatus);
                    positionRows = ps.executeUpdate();
                }
                if (positionRows == 0) {
                    con.rollback();
                    return Optional.empty();
                }
                requireExactlyOne(positionRows, "positions reservation", buyOrderId);

                String attemptId = UUID.randomUUID().toString();
                try (PreparedStatement ps = con.prepareStatement(
                        "INSERT INTO sell_attempts "
                                + "(attempt_id, buy_order_id, currency_pair, quantity, "
                                + "previous_position_status, state) "
                                + "VALUES (?, ?, ?, ?, ?, ?)")) {
                    ps.setString(1, attemptId);
                    ps.setString(2, buyOrderId);
                    ps.setString(3, currencyPair);
                    ps.setString(4, quantity);
                    ps.setInt(5, previousStatus);
                    ps.setString(6, ATTEMPT_SUBMITTING);
                    requireExactlyOne(ps.executeUpdate(), "sell attempt insert", buyOrderId);
                }

                con.commit();
                return Optional.of(new Reservation(attemptId, buyOrderId, previousStatus));
            } catch (SQLException | RuntimeException e) {
                rollback(con, e);
                throw e;
            }
        }
    }

    /**
     * Stores the accepted exchange order and changes both business tables in
     * one SQLite transaction and on one Connection.
     */
    public void recordSubmitted(Reservation reservation, String sellOrderId,
            String sellDate, String sellTime) throws SQLException {
        Objects.requireNonNull(reservation, "reservation");
        requireText(sellOrderId, "sellOrderId");
        requireText(sellDate, "sellDate");
        requireText(sellTime, "sellTime");

        try (Connection con = openConnection()) {
            con.setAutoCommit(false);
            try {
                ensureSchema(con);

                int histRows;
                try (PreparedStatement ps = con.prepareStatement(
                        "UPDATE HIST SET Status = 0, SellOrderId = ?, SellTime = ?, SellDate = ? "
                                + "WHERE BuyOrderId = ? "
                                + "AND (SellOrderId IS NULL OR SellOrderId = ?)")) {
                    ps.setString(1, sellOrderId);
                    ps.setString(2, sellTime);
                    ps.setString(3, sellDate);
                    ps.setString(4, reservation.buyOrderId());
                    ps.setString(5, sellOrderId);
                    histRows = ps.executeUpdate();
                }
                requireExactlyOne(histRows, "HIST sell submission", reservation.buyOrderId());

                int positionRows;
                try (PreparedStatement ps = con.prepareStatement(
                        "UPDATE positions SET Status = ? "
                                + "WHERE BuyOrderId = ? AND Status = ?")) {
                    ps.setInt(1, TradingConstants.POSITION_STATUS_SELL_PENDING);
                    ps.setString(2, reservation.buyOrderId());
                    ps.setInt(3, TradingConstants.POSITION_STATUS_SELL_SUBMITTING);
                    positionRows = ps.executeUpdate();
                }
                requireExactlyOne(positionRows, "positions sell submission", reservation.buyOrderId());

                int attemptRows;
                try (PreparedStatement ps = con.prepareStatement(
                        "UPDATE sell_attempts SET exchange_order_id = ?, state = ?, "
                                + "error_message = NULL, updated_at = CURRENT_TIMESTAMP "
                                + "WHERE attempt_id = ? AND state = ?")) {
                    ps.setString(1, sellOrderId);
                    ps.setString(2, ATTEMPT_SUBMITTED);
                    ps.setString(3, reservation.attemptId());
                    ps.setString(4, ATTEMPT_SUBMITTING);
                    attemptRows = ps.executeUpdate();
                }
                requireExactlyOne(attemptRows, "sell attempt submission", reservation.buyOrderId());

                con.commit();
            } catch (SQLException | RuntimeException e) {
                rollback(con, e);
                throw e;
            }
        }
    }

    /** Restores the former position state only after a definite API rejection. */
    public void recordRejected(Reservation reservation, String reason) throws SQLException {
        Objects.requireNonNull(reservation, "reservation");
        try (Connection con = openConnection()) {
            con.setAutoCommit(false);
            try {
                ensureSchema(con);

                int positionRows;
                try (PreparedStatement ps = con.prepareStatement(
                        "UPDATE positions SET Status = ? "
                                + "WHERE BuyOrderId = ? AND Status = ?")) {
                    ps.setInt(1, reservation.previousPositionStatus());
                    ps.setString(2, reservation.buyOrderId());
                    ps.setInt(3, TradingConstants.POSITION_STATUS_SELL_SUBMITTING);
                    positionRows = ps.executeUpdate();
                }
                requireExactlyOne(positionRows, "positions sell rejection", reservation.buyOrderId());

                int attemptRows = updateAttempt(con, reservation.attemptId(), null,
                        ATTEMPT_REJECTED, reason, ATTEMPT_SUBMITTING);
                requireExactlyOne(attemptRows, "sell attempt rejection", reservation.buyOrderId());
                con.commit();
            } catch (SQLException | RuntimeException e) {
                rollback(con, e);
                throw e;
            }
        }
    }

    /**
     * Keeps the position reserved and records that an operator/recovery job must
     * reconcile the attempt with Fusion before any retry is allowed.
     */
    public void requireReconciliation(Reservation reservation, String sellOrderId, String reason)
            throws SQLException {
        Objects.requireNonNull(reservation, "reservation");
        try (Connection con = openConnection()) {
            con.setAutoCommit(false);
            try {
                ensureSchema(con);
                int attemptRows = updateAttempt(con, reservation.attemptId(), sellOrderId,
                        ATTEMPT_RECONCILIATION_REQUIRED, reason, ATTEMPT_SUBMITTING);
                requireExactlyOne(attemptRows, "sell attempt reconciliation", reservation.buyOrderId());
                con.commit();
            } catch (SQLException | RuntimeException e) {
                rollback(con, e);
                throw e;
            }
        }
    }

    /**
     * Atomically finalizes HIST, appends the performance row and removes the
     * sold position. A failure rolls back all three business changes.
     */
    public void recordCompleted(CompletedSell sell) throws SQLException {
        Objects.requireNonNull(sell, "sell");
        try (Connection con = openConnection()) {
            con.setAutoCommit(false);
            try {
                ensureSchema(con);
                String buyOrderId = findPendingBuyOrderId(con, sell.sellOrderId());
                int positionCount = countOpenPositions(con, sell.currency());
                double lastBuffer = findLastBuffer(con, sell.currency());

                try (PreparedStatement ps = con.prepareStatement(
                        "UPDATE HIST SET SellAmount = ?, SellPrice = ?, Tax = ?, Fee = ?, "
                                + "Gewinn = ?, GewinnAfterTax = ?, LossAfterTax = ?, Profit = ?, "
                                + "SellFee = ?, Split = ?, Status = ?, statusCode = ? "
                                + "WHERE SellOrderId = ? AND Status = 0")) {
                    ps.setDouble(1, sell.sellAmount());
                    ps.setDouble(2, sell.sellPrice());
                    ps.setDouble(3, sell.tax());
                    ps.setDouble(4, sell.fee());
                    ps.setDouble(5, sell.revenue());
                    ps.setDouble(6, sell.profitAfterTax());
                    ps.setDouble(7, sell.lossAfterTax());
                    ps.setDouble(8, sell.profitPercent());
                    ps.setDouble(9, sell.sellFee());
                    ps.setDouble(10, sell.split());
                    ps.setInt(11, 1);
                    ps.setString(12, TradingConstants.STATUS_FILLED_CHECKED);
                    ps.setString(13, sell.sellOrderId());
                    requireExactlyOne(ps.executeUpdate(), "HIST sell completion", buyOrderId);
                }

                try (PreparedStatement ps = con.prepareStatement(
                        "INSERT INTO performance (currency, SellOrderId, Profit, TotalBuffer, "
                                + "SellDate, SellTime, count_Position, SellAmount) "
                                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)")) {
                    ps.setString(1, sell.currency());
                    ps.setString(2, sell.sellOrderId());
                    ps.setDouble(3, sell.profitPercent());
                    ps.setDouble(4, roundTwo(lastBuffer + sell.profitPercent()));
                    ps.setString(5, sell.sellDate());
                    ps.setString(6, sell.sellTime());
                    ps.setInt(7, positionCount);
                    ps.setDouble(8, sell.soldQuantity());
                    requireExactlyOne(ps.executeUpdate(), "performance insert", buyOrderId);
                }

                try (PreparedStatement ps = con.prepareStatement(
                        "DELETE FROM positions WHERE BuyOrderId = ?")) {
                    ps.setString(1, buyOrderId);
                    requireExactlyOne(ps.executeUpdate(), "positions delete", buyOrderId);
                }

                try (PreparedStatement ps = con.prepareStatement(
                        "UPDATE sell_attempts SET state = ?, updated_at = CURRENT_TIMESTAMP "
                                + "WHERE exchange_order_id = ? AND state = ?")) {
                    ps.setString(1, ATTEMPT_COMPLETED);
                    ps.setString(2, sell.sellOrderId());
                    ps.setString(3, ATTEMPT_SUBMITTED);
                    ps.executeUpdate();
                }

                con.commit();
            } catch (SQLException | RuntimeException e) {
                rollback(con, e);
                throw e;
            }
        }
    }

    private Connection openConnection() throws SQLException {
        Connection con = DriverManager.getConnection(jdbcUrl);
        try {
            try (Statement statement = con.createStatement()) {
                statement.execute("PRAGMA busy_timeout = 5000");
            }
            return con;
        } catch (SQLException e) {
            try {
                con.close();
            } catch (SQLException closeError) {
                e.addSuppressed(closeError);
            }
            throw e;
        }
    }

    private static Integer findSellableStatus(Connection con, String buyOrderId) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT Status FROM positions "
                        + "WHERE BuyOrderId = ? AND Status IN (1, 7)")) {
            ps.setString(1, buyOrderId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                int status = rs.getInt("Status");
                if (rs.next()) {
                    throw new SQLException("More than one position found for BuyOrderId " + buyOrderId);
                }
                return status;
            }
        }
    }

    private static String findPendingBuyOrderId(Connection con, String sellOrderId) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT BuyOrderId FROM HIST WHERE SellOrderId = ? AND Status = 0")) {
            ps.setString(1, sellOrderId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new SQLException("No pending HIST row found for SellOrderId " + sellOrderId);
                }
                String buyOrderId = rs.getString("BuyOrderId");
                if (rs.next()) {
                    throw new SQLException("More than one HIST row found for SellOrderId " + sellOrderId);
                }
                requireText(buyOrderId, "buyOrderId");
                return buyOrderId;
            }
        }
    }

    private static int countOpenPositions(Connection con, String currency) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT COUNT(*) FROM positions WHERE Status IN (0, 1) AND Währung = ?")) {
            ps.setString(1, currency);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    private static double findLastBuffer(Connection con, String currency) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT TotalBuffer FROM performance WHERE currency = ? ORDER BY rowid DESC LIMIT 1")) {
            ps.setString(1, currency);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getDouble(1) : 0.0;
            }
        }
    }

    private static double roundTwo(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    private static boolean historyIsReadyForSell(Connection con, String buyOrderId) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT SellOrderId FROM HIST WHERE BuyOrderId = ?")) {
            ps.setString(1, buyOrderId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new SQLException("No HIST row found for BuyOrderId " + buyOrderId);
                }
                String sellOrderId = rs.getString("SellOrderId");
                if (rs.next()) {
                    throw new SQLException("More than one HIST row found for BuyOrderId " + buyOrderId);
                }
                return sellOrderId == null || sellOrderId.isBlank();
            }
        }
    }

    private static int updateAttempt(Connection con, String attemptId, String sellOrderId,
            String state, String reason, String expectedState) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "UPDATE sell_attempts SET exchange_order_id = COALESCE(?, exchange_order_id), "
                        + "state = ?, error_message = ?, updated_at = CURRENT_TIMESTAMP "
                        + "WHERE attempt_id = ? AND state = ?")) {
            ps.setString(1, sellOrderId);
            ps.setString(2, state);
            ps.setString(3, truncate(reason));
            ps.setString(4, attemptId);
            ps.setString(5, expectedState);
            return ps.executeUpdate();
        }
    }

    private static void ensureSchema(Connection con) throws SQLException {
        try (Statement statement = con.createStatement()) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS sell_attempts ("
                    + "attempt_id TEXT PRIMARY KEY, "
                    + "buy_order_id TEXT NOT NULL, "
                    + "currency_pair TEXT NOT NULL, "
                    + "quantity TEXT NOT NULL, "
                    + "previous_position_status INTEGER NOT NULL, "
                    + "exchange_order_id TEXT, "
                    + "state TEXT NOT NULL, "
                    + "error_message TEXT, "
                    + "created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP, "
                    + "updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP"
                    + ")");
            statement.executeUpdate("CREATE UNIQUE INDEX IF NOT EXISTS uq_sell_attempt_exchange_order "
                    + "ON sell_attempts(exchange_order_id) WHERE exchange_order_id IS NOT NULL");
            statement.executeUpdate("CREATE UNIQUE INDEX IF NOT EXISTS uq_active_sell_attempt "
                    + "ON sell_attempts(buy_order_id) "
                    + "WHERE state IN ('SUBMITTING', 'RECONCILIATION_REQUIRED')");
        }
    }

    private static void requireExactlyOne(int rows, String operation, String buyOrderId)
            throws SQLException {
        if (rows != 1) {
            throw new SQLException(operation + " changed " + rows
                    + " rows for BuyOrderId " + buyOrderId + "; expected exactly one");
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

    public record Reservation(String attemptId, String buyOrderId, int previousPositionStatus) {
        public Reservation {
            requireText(attemptId, "attemptId");
            requireText(buyOrderId, "buyOrderId");
        }
    }

    public record CompletedSell(
            String sellOrderId,
            String currency,
            double sellAmount,
            double sellPrice,
            double tax,
            double fee,
            double revenue,
            double profitAfterTax,
            double lossAfterTax,
            double profitPercent,
            double sellFee,
            double split,
            double soldQuantity,
            String sellDate,
            String sellTime) {
        public CompletedSell {
            requireText(sellOrderId, "sellOrderId");
            requireText(currency, "currency");
            requireText(sellDate, "sellDate");
            requireText(sellTime, "sellTime");
        }
    }
}
