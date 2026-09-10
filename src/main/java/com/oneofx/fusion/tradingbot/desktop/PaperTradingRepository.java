package com.oneofx.fusion.tradingbot.desktop;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import com.oneofx.fusion.tradingbot.Database.dbUrl;

/** Transaktionales lokales Konto und Orderbuch fuer den Paper-Modus. */
public final class PaperTradingRepository {

    public void ensureAccount(long botId, double initialBudget) throws SQLException {
        try (Connection con = open()) {
            try (PreparedStatement check = con.prepareStatement(
                    "SELECT 1 FROM paperAccounts WHERE bot_id = ? LIMIT 1")) {
                check.setLong(1, botId);
                try (ResultSet rs = check.executeQuery()) {
                    if (rs.next()) return;
                }
            }
            try (PreparedStatement insert = con.prepareStatement(
                    "INSERT INTO paperAccounts (bot_id, asset, available, reserved) VALUES (?, 'EUR', ?, 0)")) {
                insert.setLong(1, botId);
                insert.setDouble(2, initialBudget);
                insert.executeUpdate();
            }
        }
    }

    public void reset(long botId, double initialBudget) throws SQLException {
        try (Connection con = open()) {
            con.setAutoCommit(false);
            try {
                execute(con, "DELETE FROM paperOrders WHERE bot_id = ?", botId);
                execute(con, "DELETE FROM paperPositions WHERE bot_id = ?", botId);
                execute(con, "DELETE FROM paperAccounts WHERE bot_id = ?", botId);
                try (PreparedStatement ps = con.prepareStatement(
                        "INSERT INTO paperAccounts (bot_id, asset, available, reserved) VALUES (?, 'EUR', ?, 0)")) {
                    ps.setLong(1, botId);
                    ps.setDouble(2, initialBudget);
                    ps.executeUpdate();
                }
                con.commit();
            } catch (SQLException | RuntimeException ex) {
                rollback(con, ex);
                throw ex;
            }
        }
    }

    public boolean placeLimitBuy(long botId, String currency, double price,
            double quantity, double feePercent) throws SQLException {
        return placeBuy(botId, currency, price, quantity, feePercent, "LIMIT");
    }

    public boolean placeBuy(long botId, String currency, double price,
            double quantity, double feePercent, String orderType) throws SQLException {
        requireEurPair(currency);
        double notional = price * quantity;
        double fee = notional * feePercent / 100.0;
        if (!positive(price) || !positive(quantity) || !Double.isFinite(fee)) return false;
        try (Connection con = open()) {
            con.setAutoCommit(false);
            try {
                if (existsAtPrice(con, botId, currency, price)) {
                    con.rollback();
                    return false;
                }
                double available = balance(con, botId, "EUR", "available");
                if (available + 0.00000001 < notional + fee) {
                    con.rollback();
                    return false;
                }
                changeBalance(con, botId, "EUR", -(notional + fee), notional + fee);
                try (PreparedStatement ps = con.prepareStatement(
                        "INSERT INTO paperOrders (order_id, bot_id, currency, side, type, status, "
                                + "limit_price, quantity, amount, fee) VALUES (?, ?, ?, 'BUY', ?, "
                                + "'OPEN', ?, ?, ?, ?)")) {
                    ps.setString(1, id("paper-buy"));
                    ps.setLong(2, botId);
                    ps.setString(3, currency);
                    ps.setString(4, orderType);
                    ps.setDouble(5, price);
                    ps.setDouble(6, quantity);
                    ps.setDouble(7, notional);
                    ps.setDouble(8, fee);
                    ps.executeUpdate();
                }
                con.commit();
                return true;
            } catch (SQLException | RuntimeException ex) {
                rollback(con, ex);
                throw ex;
            }
        }
    }

    /** Executes a manual market buy immediately against the supplied ticker price. */
    public boolean executeMarketBuy(long botId, String currency, double marketPrice,
            double quantity, double slippagePercent, double feePercent) throws SQLException {
        requireEurPair(currency);
        double fillPrice = marketPrice * (1.0 + slippagePercent / 100.0);
        double notional = fillPrice * quantity;
        double fee = notional * feePercent / 100.0;
        if (!positive(fillPrice) || !positive(quantity) || !Double.isFinite(fee)) return false;
        try (Connection con = open()) {
            con.setAutoCommit(false);
            try {
                if (balance(con, botId, "EUR", "available") + 0.00000001 < notional + fee) {
                    con.rollback();
                    return false;
                }
                String orderId = id("paper-buy");
                changeBalance(con, botId, "EUR", -(notional + fee), 0);
                changeBalance(con, botId, baseAsset(currency), quantity, 0);
                try (PreparedStatement ps = con.prepareStatement(
                        "INSERT INTO paperOrders (order_id, bot_id, currency, side, type, status, "
                                + "quantity, amount, filled_price, fee, reason) "
                                + "VALUES (?, ?, ?, 'BUY', 'MARKET', 'FILLED', ?, ?, ?, ?, 'MANUAL')")) {
                    ps.setString(1, orderId); ps.setLong(2, botId); ps.setString(3, currency);
                    ps.setDouble(4, quantity); ps.setDouble(5, notional);
                    ps.setDouble(6, fillPrice); ps.setDouble(7, fee); ps.executeUpdate();
                }
                try (PreparedStatement ps = con.prepareStatement(
                        "INSERT INTO paperPositions (position_id, bot_id, currency, entry_price, "
                                + "quantity, buy_amount, peak_price, fees) VALUES (?, ?, ?, ?, ?, ?, ?, ?)")) {
                    ps.setString(1, orderId); ps.setLong(2, botId); ps.setString(3, currency);
                    ps.setDouble(4, fillPrice); ps.setDouble(5, quantity);
                    ps.setDouble(6, notional); ps.setDouble(7, fillPrice);
                    ps.setDouble(8, fee); ps.executeUpdate();
                }
                con.commit();
                return true;
            } catch (SQLException | RuntimeException ex) {
                rollback(con, ex);
                throw ex;
            }
        }
    }

    /** Reserves a complete paper position for a manual limit sell. */
    public boolean placeLimitSell(long botId, PaperPosition position, double limitPrice)
            throws SQLException {
        if (position == null || !positive(limitPrice)) return false;
        requireEurPair(position.currency());
        try (Connection con = open()) {
            con.setAutoCommit(false);
            try {
                try (PreparedStatement update = con.prepareStatement(
                        "UPDATE paperPositions SET status='SELL_PENDING', updated_at=CURRENT_TIMESTAMP "
                                + "WHERE position_id=? AND bot_id=? AND status='OPEN'")) {
                    update.setString(1, position.positionId()); update.setLong(2, botId);
                    if (update.executeUpdate() != 1) { con.rollback(); return false; }
                }
                if (balance(con, botId, baseAsset(position.currency()), "available")
                        + 0.00000001 < position.quantity()) {
                    con.rollback();
                    return false;
                }
                changeBalance(con, botId, baseAsset(position.currency()),
                        -position.quantity(), position.quantity());
                try (PreparedStatement ps = con.prepareStatement(
                        "INSERT INTO paperOrders (order_id, bot_id, currency, side, type, status, "
                                + "limit_price, quantity, amount, fee, reason, position_id) "
                                + "VALUES (?, ?, ?, 'SELL', 'LIMIT', 'OPEN', ?, ?, ?, 0, 'MANUAL', ?)")) {
                    ps.setString(1, id("paper-sell")); ps.setLong(2, botId);
                    ps.setString(3, position.currency()); ps.setDouble(4, limitPrice);
                    ps.setDouble(5, position.quantity());
                    ps.setDouble(6, limitPrice * position.quantity());
                    ps.setString(7, position.positionId()); ps.executeUpdate();
                }
                con.commit();
                return true;
            } catch (SQLException | RuntimeException ex) {
                rollback(con, ex);
                throw ex;
            }
        }
    }

    public int fillTriggeredBuys(long botId, String currency, double currentPrice)
            throws SQLException {
        requireEurPair(currency);
        int filled = 0;
        try (Connection con = open()) {
            con.setAutoCommit(false);
            try (PreparedStatement ps = con.prepareStatement(
                    "SELECT order_id, limit_price, quantity, amount, fee FROM paperOrders "
                            + "WHERE bot_id = ? AND currency = ? AND side = 'BUY' "
                            + "AND status = 'OPEN' AND limit_price >= ? ORDER BY created_at")) {
                ps.setLong(1, botId);
                ps.setString(2, currency);
                ps.setDouble(3, currentPrice);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String orderId = rs.getString("order_id");
                        double price = rs.getDouble("limit_price");
                        double quantity = rs.getDouble("quantity");
                        double reserved = rs.getDouble("amount") + rs.getDouble("fee");
                        changeBalance(con, botId, "EUR", 0, -reserved);
                        changeBalance(con, botId, baseAsset(currency), quantity, 0);
                        try (PreparedStatement update = con.prepareStatement(
                                "UPDATE paperOrders SET status = 'FILLED', filled_price = ?, "
                                        + "updated_at = CURRENT_TIMESTAMP WHERE order_id = ?")) {
                            update.setDouble(1, price);
                            update.setString(2, orderId);
                            update.executeUpdate();
                        }
                        try (PreparedStatement insert = con.prepareStatement(
                                "INSERT INTO paperPositions (position_id, bot_id, currency, entry_price, "
                                        + "quantity, buy_amount, peak_price, fees) VALUES (?, ?, ?, ?, ?, ?, ?, ?)")) {
                            insert.setString(1, orderId);
                            insert.setLong(2, botId);
                            insert.setString(3, currency);
                            insert.setDouble(4, price);
                            insert.setDouble(5, quantity);
                            insert.setDouble(6, rs.getDouble("amount"));
                            insert.setDouble(7, price);
                            insert.setDouble(8, rs.getDouble("fee"));
                            insert.executeUpdate();
                        }
                        filled++;
                    }
                }
                con.commit();
            } catch (SQLException | RuntimeException ex) {
                rollback(con, ex);
                throw ex;
            }
        }
        return filled;
    }

    /** Fills manual paper limit sells once the market trades at or above the limit. */
    public int fillTriggeredSells(long botId, String currency, double currentPrice,
            double feePercent) throws SQLException {
        requireEurPair(currency);
        int filled = 0;
        try (Connection con = open()) {
            con.setAutoCommit(false);
            try (PreparedStatement ps = con.prepareStatement(
                    "SELECT order_id, position_id, limit_price, quantity FROM paperOrders "
                            + "WHERE bot_id=? AND currency=? AND side='SELL' AND status='OPEN' "
                            + "AND limit_price<=? ORDER BY created_at")) {
                ps.setLong(1, botId); ps.setString(2, currency); ps.setDouble(3, currentPrice);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String orderId = rs.getString("order_id");
                        String positionId = rs.getString("position_id");
                        double price = rs.getDouble("limit_price");
                        double quantity = rs.getDouble("quantity");
                        double proceeds = price * quantity;
                        double fee = proceeds * feePercent / 100.0;
                        double buyAmount;
                        double buyFees;
                        try (PreparedStatement position = con.prepareStatement(
                                "SELECT buy_amount,fees FROM paperPositions WHERE position_id=? "
                                        + "AND bot_id=? AND status='SELL_PENDING'")) {
                            position.setString(1, positionId); position.setLong(2, botId);
                            try (ResultSet found = position.executeQuery()) {
                                if (!found.next()) throw new SQLException(
                                        "Reservierte Paper-Position fehlt: " + positionId);
                                buyAmount = found.getDouble("buy_amount");
                                buyFees = found.getDouble("fees");
                            }
                        }
                        changeBalance(con, botId, baseAsset(currency), 0, -quantity);
                        changeBalance(con, botId, "EUR", proceeds - fee, 0);
                        try (PreparedStatement update = con.prepareStatement(
                                "UPDATE paperOrders SET status='FILLED',filled_price=?,amount=?,fee=?,"
                                        + "updated_at=CURRENT_TIMESTAMP WHERE order_id=? AND status='OPEN'")) {
                            update.setDouble(1, price); update.setDouble(2, proceeds);
                            update.setDouble(3, fee); update.setString(4, orderId);
                            if (update.executeUpdate() != 1) throw new SQLException(
                                    "Paper-Sell konnte nicht abgeschlossen werden: " + orderId);
                        }
                        try (PreparedStatement update = con.prepareStatement(
                                "UPDATE paperPositions SET status='CLOSED',exit_price=?,realized_pnl=?,"
                                        + "fees=fees+?,exit_reason='MANUAL_LIMIT',closed_at=CURRENT_TIMESTAMP,"
                                        + "updated_at=CURRENT_TIMESTAMP WHERE position_id=? AND bot_id=? "
                                        + "AND status='SELL_PENDING'")) {
                            update.setDouble(1, price);
                            update.setDouble(2, proceeds - fee - buyAmount - buyFees);
                            update.setDouble(3, fee); update.setString(4, positionId);
                            update.setLong(5, botId);
                            if (update.executeUpdate() != 1) throw new SQLException(
                                    "Paper-Position konnte nicht geschlossen werden: " + positionId);
                        }
                        filled++;
                    }
                }
                con.commit();
            } catch (SQLException | RuntimeException ex) {
                rollback(con, ex);
                throw ex;
            }
        }
        return filled;
    }

    /** Cancels one open paper order and releases its reserved cash or asset. */
    public boolean cancelOpenOrder(long botId, String orderId, String reason) throws SQLException {
        try (Connection con = open()) {
            con.setAutoCommit(false);
            try {
                String side; String currency; String positionId; double quantity;
                double amount; double fee;
                try (PreparedStatement ps = con.prepareStatement(
                        "SELECT side,currency,position_id,quantity,amount,fee FROM paperOrders "
                                + "WHERE bot_id=? AND order_id=? AND status='OPEN'")) {
                    ps.setLong(1, botId); ps.setString(2, orderId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) { con.rollback(); return false; }
                        side=rs.getString("side"); currency=rs.getString("currency");
                        positionId=rs.getString("position_id"); quantity=rs.getDouble("quantity");
                        amount=rs.getDouble("amount"); fee=rs.getDouble("fee");
                    }
                }
                if ("BUY".equals(side)) {
                    changeBalance(con, botId, "EUR", amount + fee, -(amount + fee));
                } else if ("SELL".equals(side)) {
                    changeBalance(con, botId, baseAsset(currency), quantity, -quantity);
                    try (PreparedStatement ps = con.prepareStatement(
                            "UPDATE paperPositions SET status='OPEN',updated_at=CURRENT_TIMESTAMP "
                                    + "WHERE position_id=? AND bot_id=? AND status='SELL_PENDING'")) {
                        ps.setString(1, positionId); ps.setLong(2, botId);
                        if (ps.executeUpdate()!=1) throw new SQLException(
                                "Reservierte Paper-Position konnte nicht freigegeben werden.");
                    }
                } else throw new SQLException("Unbekannte Paper-Orderseite: " + side);
                try (PreparedStatement ps = con.prepareStatement(
                        "UPDATE paperOrders SET status='CANCELLED',reason=?,updated_at=CURRENT_TIMESTAMP "
                                + "WHERE bot_id=? AND order_id=? AND status='OPEN'")) {
                    ps.setString(1, reason); ps.setLong(2, botId); ps.setString(3, orderId);
                    if (ps.executeUpdate()!=1) throw new SQLException("Paper-Order wurde parallel geändert.");
                }
                con.commit();
                return true;
            } catch (SQLException | RuntimeException ex) {
                rollback(con, ex);
                throw ex;
            }
        }
    }

    public int cancelOpenBuys(long botId, String currency, String reason) throws SQLException {
        return cancelOpenBuys(botId, currency, reason, 0);
    }

    public int expireOpenBuys(long botId, String currency, int maximumMinutes) throws SQLException {
        if (maximumMinutes <= 0) return 0;
        return cancelOpenBuys(botId, currency, "MAX_ORDER_AGE", maximumMinutes);
    }

    private int cancelOpenBuys(long botId, String currency, String reason, int olderThanMinutes)
            throws SQLException {
        int cancelled = 0;
        try (Connection con = open()) {
            con.setAutoCommit(false);
            try (PreparedStatement ps = con.prepareStatement(
                    "SELECT order_id, amount, fee FROM paperOrders WHERE bot_id = ? AND currency = ? "
                            + "AND side = 'BUY' AND status = 'OPEN'"
                            + (olderThanMinutes > 0
                                    ? " AND created_at <= datetime('now', '-' || ? || ' minutes')" : ""))) {
                ps.setLong(1, botId);
                ps.setString(2, currency);
                if (olderThanMinutes > 0) ps.setInt(3, olderThanMinutes);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        changeBalance(con, botId, "EUR", rs.getDouble("amount") + rs.getDouble("fee"),
                                -(rs.getDouble("amount") + rs.getDouble("fee")));
                        try (PreparedStatement update = con.prepareStatement(
                                "UPDATE paperOrders SET status = 'CANCELLED', reason = ?, "
                                        + "updated_at = CURRENT_TIMESTAMP WHERE order_id = ?")) {
                            update.setString(1, reason);
                            update.setString(2, rs.getString("order_id"));
                            update.executeUpdate();
                        }
                        cancelled++;
                    }
                }
                con.commit();
            } catch (SQLException | RuntimeException ex) {
                rollback(con, ex);
                throw ex;
            }
        }
        return cancelled;
    }

    public void updatePosition(String positionId, double peak, boolean trailingActive,
            double unrealizedPnl) throws SQLException {
        try (Connection con = open(); PreparedStatement ps = con.prepareStatement(
                "UPDATE paperPositions SET peak_price = ?, tsl_active = ?, unrealized_pnl = ?, "
                        + "updated_at = CURRENT_TIMESTAMP WHERE position_id = ? AND status = 'OPEN'")) {
            ps.setDouble(1, peak);
            ps.setInt(2, trailingActive ? 1 : 0);
            ps.setDouble(3, unrealizedPnl);
            ps.setString(4, positionId);
            ps.executeUpdate();
        }
    }

    public boolean closePosition(long botId, PaperPosition position, double marketPrice,
            double slippagePercent, double feePercent, String reason) throws SQLException {
        return closePosition(botId, position, marketPrice, slippagePercent, feePercent,
                reason, "MARKET");
    }

    public boolean closePosition(long botId, PaperPosition position, double marketPrice,
            double slippagePercent, double feePercent, String reason, String orderType)
            throws SQLException {
        double exitPrice = marketPrice * (1.0 - slippagePercent / 100.0);
        double proceeds = exitPrice * position.quantity();
        double fee = proceeds * feePercent / 100.0;
        if (!positive(exitPrice) || !Double.isFinite(fee)) return false;
        try (Connection con = open()) {
            con.setAutoCommit(false);
            try {
                try (PreparedStatement ps = con.prepareStatement(
                        "UPDATE paperPositions SET status = 'CLOSED', exit_price = ?, realized_pnl = ?, "
                                + "fees = fees + ?, exit_reason = ?, updated_at = CURRENT_TIMESTAMP "
                                + "WHERE position_id = ? AND bot_id = ? AND status = 'OPEN'")) {
                    ps.setDouble(1, exitPrice);
                    ps.setDouble(2, proceeds - fee - position.buyAmount() - position.fees());
                    ps.setDouble(3, fee);
                    ps.setString(4, reason);
                    ps.setString(5, position.positionId());
                    ps.setLong(6, botId);
                    if (ps.executeUpdate() != 1) {
                        con.rollback();
                        return false;
                    }
                }
                changeBalance(con, botId, baseAsset(position.currency()), -position.quantity(), 0);
                changeBalance(con, botId, "EUR", proceeds - fee, 0);
                try (PreparedStatement ps = con.prepareStatement(
                        "INSERT INTO paperOrders (order_id, bot_id, currency, side, type, status, "
                                + "quantity, amount, filled_price, fee, reason) "
                                + "VALUES (?, ?, ?, 'SELL', ?, 'FILLED', ?, ?, ?, ?, ?)")) {
                    ps.setString(1, id("paper-sell"));
                    ps.setLong(2, botId);
                    ps.setString(3, position.currency());
                    ps.setString(4, orderType);
                    ps.setDouble(5, position.quantity());
                    ps.setDouble(6, proceeds);
                    ps.setDouble(7, exitPrice);
                    ps.setDouble(8, fee);
                    ps.setString(9, reason);
                    ps.executeUpdate();
                }
                con.commit();
                return true;
            } catch (SQLException | RuntimeException ex) {
                rollback(con, ex);
                throw ex;
            }
        }
    }

    public List<PaperPosition> loadOpenPositions(long botId, String currency) throws SQLException {
        List<PaperPosition> result = new ArrayList<>();
        try (Connection con = open(); PreparedStatement ps = con.prepareStatement(
                "SELECT position_id, currency, entry_price, quantity, buy_amount, peak_price, "
                        + "tsl_active, fees, opened_at FROM paperPositions WHERE bot_id = ? AND currency = ? "
                        + "AND status = 'OPEN' ORDER BY opened_at")) {
            ps.setLong(1, botId);
            ps.setString(2, currency);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(new PaperPosition(rs.getString("position_id"),
                            rs.getString("currency"), rs.getDouble("entry_price"),
                            rs.getDouble("quantity"), rs.getDouble("buy_amount"),
                            rs.getDouble("peak_price"), rs.getInt("tsl_active") != 0,
                            rs.getDouble("fees"), rs.getString("opened_at")));
                }
            }
        }
        return result;
    }

    public List<Double> occupiedPrices(long botId, String currency) throws SQLException {
        List<Double> result = new ArrayList<>();
        String sql = "SELECT limit_price AS price FROM paperOrders WHERE bot_id = ? AND currency = ? "
                + "AND side = 'BUY' AND status = 'OPEN' UNION ALL "
                + "SELECT entry_price FROM paperPositions WHERE bot_id = ? AND currency = ? AND status = 'OPEN'";
        try (Connection con = open(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, botId); ps.setString(2, currency);
            ps.setLong(3, botId); ps.setString(4, currency);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) result.add(rs.getDouble(1));
            }
        }
        return result;
    }

    public PaperRisk loadRisk(long botId) throws SQLException {
        String sql = "SELECT "
                + "(SELECT COALESCE(SUM(buy_amount),0) FROM paperPositions WHERE bot_id=? AND status='OPEN') + "
                + "(SELECT COALESCE(SUM(amount),0) FROM paperOrders WHERE bot_id=? AND side='BUY' AND status='OPEN') exposure, "
                + "(SELECT COUNT(*) FROM paperPositions WHERE bot_id=? AND status='OPEN') positions, "
                + "(SELECT COUNT(*) FROM paperOrders WHERE bot_id=? AND status='OPEN') orders";
        try (Connection con = open(); PreparedStatement ps = con.prepareStatement(sql)) {
            for (int i = 1; i <= 4; i++) ps.setLong(i, botId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return new PaperRisk(rs.getDouble("exposure"), rs.getInt("positions"),
                        rs.getInt("orders"));
            }
        }
    }

    public double loadPairExposure(long botId, String currency) throws SQLException {
        String sql = "SELECT "
                + "(SELECT COALESCE(SUM(buy_amount),0) FROM paperPositions WHERE bot_id=? AND currency=? AND status='OPEN') + "
                + "(SELECT COALESCE(SUM(amount),0) FROM paperOrders WHERE bot_id=? AND currency=? AND side='BUY' AND status='OPEN')";
        try (Connection con = open(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, botId); ps.setString(2, currency);
            ps.setLong(3, botId); ps.setString(4, currency);
            try (ResultSet rs = ps.executeQuery()) { return rs.next() ? rs.getDouble(1) : 0; }
        }
    }

    public int countOpenBuys(long botId, String currency) throws SQLException {
        try (Connection con = open(); PreparedStatement ps = con.prepareStatement(
                "SELECT COUNT(*) FROM paperOrders WHERE bot_id=? AND currency=? "
                        + "AND side='BUY' AND status='OPEN'")) {
            ps.setLong(1, botId); ps.setString(2, currency);
            try (ResultSet rs = ps.executeQuery()) { return rs.next() ? rs.getInt(1) : 0; }
        }
    }

    public List<Balance> loadBalances(long botId) throws SQLException {
        List<Balance> result = new ArrayList<>();
        try (Connection con = open(); PreparedStatement ps = con.prepareStatement(
                "SELECT asset, available, reserved FROM paperAccounts WHERE bot_id = ? "
                        + "ORDER BY CASE WHEN asset='EUR' THEN 0 ELSE 1 END, asset")) {
            ps.setLong(1, botId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) result.add(new Balance(rs.getString("asset"),
                        rs.getDouble("available"), rs.getDouble("reserved")));
            }
        }
        return result;
    }

    private static boolean existsAtPrice(Connection con, long botId, String currency,
            double price) throws SQLException {
        String sql = "SELECT 1 FROM paperOrders WHERE bot_id=? AND currency=? AND status='OPEN' "
                + "AND ABS(limit_price-?) < 0.000000001 UNION ALL SELECT 1 FROM paperPositions "
                + "WHERE bot_id=? AND currency=? AND status='OPEN' AND ABS(entry_price-?) < 0.000000001 LIMIT 1";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, botId); ps.setString(2, currency); ps.setDouble(3, price);
            ps.setLong(4, botId); ps.setString(5, currency); ps.setDouble(6, price);
            try (ResultSet rs = ps.executeQuery()) { return rs.next(); }
        }
    }

    private static void changeBalance(Connection con, long botId, String asset,
            double availableDelta, double reservedDelta) throws SQLException {
        try (PreparedStatement insert = con.prepareStatement(
                "INSERT OR IGNORE INTO paperAccounts (bot_id, asset, available, reserved) VALUES (?, ?, 0, 0)")) {
            insert.setLong(1, botId); insert.setString(2, asset); insert.executeUpdate();
        }
        try (PreparedStatement update = con.prepareStatement(
                "UPDATE paperAccounts SET available=available+?, reserved=reserved+?, "
                        + "updated_at=CURRENT_TIMESTAMP WHERE bot_id=? AND asset=?")) {
            update.setDouble(1, availableDelta); update.setDouble(2, reservedDelta);
            update.setLong(3, botId); update.setString(4, asset); update.executeUpdate();
        }
    }

    private static double balance(Connection con, long botId, String asset, String column)
            throws SQLException {
        if (!"available".equals(column) && !"reserved".equals(column)) throw new IllegalArgumentException();
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT " + column + " FROM paperAccounts WHERE bot_id=? AND asset=?")) {
            ps.setLong(1, botId); ps.setString(2, asset);
            try (ResultSet rs = ps.executeQuery()) { return rs.next() ? rs.getDouble(1) : 0; }
        }
    }

    private static void execute(Connection con, String sql, long botId) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(sql)) { ps.setLong(1, botId); ps.executeUpdate(); }
    }

    private static String baseAsset(String currency) {
        requireEurPair(currency);
        return currency.substring(0, currency.length() - 3).toUpperCase(Locale.ROOT);
    }

    private static void requireEurPair(String currency) {
        if (currency == null || !currency.toUpperCase(Locale.ROOT).endsWith("EUR")) {
            throw new IllegalArgumentException("Paper-Trading unterstuetzt derzeit EUR-Handelspaare.");
        }
    }

    private static String id(String prefix) { return prefix + "-" + UUID.randomUUID(); }
    private static boolean positive(double value) { return Double.isFinite(value) && value > 0; }

    private static Connection open() throws SQLException {
        return com.oneofx.fusion.tradingbot.Database.SQLiteConnectionFactory.open(dbUrl.getoneOfX());
    }

    private static void rollback(Connection con, Exception original) {
        try { con.rollback(); } catch (SQLException ex) { original.addSuppressed(ex); }
    }

    public record PaperPosition(String positionId, String currency, double entryPrice,
            double quantity, double buyAmount, double peakPrice, boolean trailingActive,
            double fees, String openedAt) { }
    public record PaperRisk(double exposure, int openPositions, int openOrders) { }
    public record Balance(String asset, double available, double reserved) {
        public double total() { return available + reserved; }
    }
}
