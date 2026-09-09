package com.oneofx.fusion.tradingbot.SQL_Database;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.oneofx.fusion.tradingbot.Database.dbUrl;
import com.oneofx.fusion.tradingbot.domain.TradingRules;

public class TradingRulesSQL {  

    private static final Object SCHEMA_LOCK = new Object();
    private static final Set<String> INITIALIZED_DATABASES = ConcurrentHashMap.newKeySet();
    private static final Map<String, TradingRules> CACHE = new ConcurrentHashMap<>();

    public static void saveTradingRules(TradingRules rules) {
        if (rules == null || rules.getSymbol() == null) {
            return;
        }

        String sql = "INSERT OR REPLACE INTO tradingRules "
                + "(currency, tickSize, stepSize, minQty, amountIncrement, maxOrderSize, minOrderAmount, maxOrderAmount) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        
        String databaseUrl = dbUrl.getoneOfX();
        try (Connection con = DriverManager.getConnection(databaseUrl)) {
            ensureSchemaOnce(con, databaseUrl);
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
            CACHE.put(cacheKey(databaseUrl, rules.getSymbol()), rules);
            
        } catch (SQLException err) {
            System.err.println("Fehler beim Speichern von TradingRules für " + rules.getSymbol() + ": " + err.getMessage());
        }
    }

    public static TradingRules getTradingRules(String symbol) {
        if (symbol == null || symbol.trim().isEmpty()) {
            return null;
        }

        String databaseUrl = dbUrl.getoneOfX();
        String cacheKey = cacheKey(databaseUrl, symbol);
        TradingRules cached = CACHE.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        String sql = "SELECT currency, tickSize, stepSize, minQty, amountIncrement, maxOrderSize, "
                + "minOrderAmount, maxOrderAmount FROM tradingRules WHERE currency = ?";
        
        try (Connection con = DriverManager.getConnection(databaseUrl)) {
            ensureSchemaOnce(con, databaseUrl);
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
                        CACHE.put(cacheKey, rules);
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

    /** Clears in-memory values, primarily for an explicit runtime rules reload. */
    public static void clearCache() {
        CACHE.clear();
    }

    private static String cacheKey(String databaseUrl, String symbol) {
        return databaseUrl + '\0' + symbol.trim().toUpperCase(Locale.ROOT);
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

    private static void ensureSchemaOnce(Connection con, String databaseUrl) throws SQLException {
        if (INITIALIZED_DATABASES.contains(databaseUrl)) {
            return;
        }
        synchronized (SCHEMA_LOCK) {
            if (INITIALIZED_DATABASES.contains(databaseUrl)) {
                return;
            }
            ensureSchema(con);
            INITIALIZED_DATABASES.add(databaseUrl);
        }
    }

    private static void addColumnIfMissing(Connection con, String column) throws SQLException {
        try (java.sql.Statement statement = con.createStatement()) {
            statement.executeUpdate("ALTER TABLE tradingRules ADD COLUMN " + column + " TEXT");
        } catch (SQLException e) {
            if (e.getMessage() == null || !e.getMessage().toLowerCase().contains("duplicate column")) throw e;
        }
    }
}
