package com.oneofx.fusion.tradingbot.SQL_Database;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import com.oneofx.fusion.tradingbot.Database.dbUrl;
import com.oneofx.fusion.tradingbot.domain.TradingRules;

public class TradingRulesSQL {  

    public static void saveTradingRules(TradingRules rules) {
        if (rules == null || rules.getSymbol() == null) {
            return;
        }

        String sql = "INSERT OR REPLACE INTO tradingRules "
                + "(currency, tickSize, stepSize, minQty, amountIncrement, maxOrderSize, minOrderAmount, maxOrderAmount) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        
        try (Connection con = DriverManager.getConnection(dbUrl.getoneOfX())) {
            ensureSchema(con);
            try (PreparedStatement ps = con.prepareStatement(sql)) {
                ps.setString(1, rules.getSymbol());
                ps.setString(2, rules.getTickSize() != null ? rules.getTickSize().toPlainString() : "0.01");
                ps.setString(3, rules.getStepSize() != null ? rules.getStepSize().toPlainString() : "0.001");
                ps.setString(4, rules.getMinQty() != null ? rules.getMinQty().toPlainString() : "0.001");
                ps.setString(5, decimal(rules.getAmountIncrement()));
                ps.setString(6, decimal(rules.getMaxOrderSize()));
                ps.setString(7, decimal(rules.getMinOrderAmount()));
                ps.setString(8, decimal(rules.getMaxOrderAmount()));
                ps.executeUpdate();
            }
            
        } catch (SQLException err) {
            System.err.println("Fehler beim Speichern von TradingRules für " + rules.getSymbol() + ": " + err.getMessage());
        }
    }

    public static TradingRules getTradingRules(String symbol) {
        if (symbol == null || symbol.trim().isEmpty()) {
            return null;
        }

        String sql = "SELECT currency, tickSize, stepSize, minQty, amountIncrement, maxOrderSize, "
                + "minOrderAmount, maxOrderAmount FROM tradingRules WHERE currency = ?";
        
        try (Connection con = DriverManager.getConnection(dbUrl.getoneOfX())) {
            ensureSchema(con);
            try (PreparedStatement ps = con.prepareStatement(sql)) {
                ps.setString(1, symbol);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        TradingRules rules = new TradingRules(rs.getString("currency"));
                        rules.setTickSize(toBigDecimal(rs.getString("tickSize")));
                        rules.setStepSize(toBigDecimal(rs.getString("stepSize")));
                        rules.setMinQty(toBigDecimal(rs.getString("minQty")));
                        rules.setAmountIncrement(toBigDecimal(rs.getString("amountIncrement")));
                        rules.setMaxOrderSize(toBigDecimal(rs.getString("maxOrderSize")));
                        rules.setMinOrderAmount(toBigDecimal(rs.getString("minOrderAmount")));
                        rules.setMaxOrderAmount(toBigDecimal(rs.getString("maxOrderAmount")));
                        return rules;
                    }
                }
            }
            
            return null;
            
        } catch (SQLException err) {
            System.err.println("Fehler beim Abrufen von TradingRules für " + symbol + ": " + err.getMessage());
            return null;
        }
    }

    private static BigDecimal toBigDecimal(String value) {
        if (value == null || value.trim().isEmpty()) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }

    private static String decimal(BigDecimal value) {
        return value == null ? null : value.toPlainString();
    }

    private static void ensureSchema(Connection con) throws SQLException {
        try (java.sql.Statement statement = con.createStatement()) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS tradingRules ("
                    + "currency TEXT PRIMARY KEY, tickSize TEXT, stepSize TEXT, minQty TEXT, "
                    + "amountIncrement TEXT, maxOrderSize TEXT, minOrderAmount TEXT, maxOrderAmount TEXT)");
        }
        addColumnIfMissing(con, "amountIncrement");
        addColumnIfMissing(con, "maxOrderSize");
        addColumnIfMissing(con, "minOrderAmount");
        addColumnIfMissing(con, "maxOrderAmount");
    }

    private static void addColumnIfMissing(Connection con, String column) throws SQLException {
        try (java.sql.Statement statement = con.createStatement()) {
            statement.executeUpdate("ALTER TABLE tradingRules ADD COLUMN " + column + " TEXT");
        } catch (SQLException e) {
            if (e.getMessage() == null || !e.getMessage().toLowerCase().contains("duplicate column")) throw e;
        }
    }
}
