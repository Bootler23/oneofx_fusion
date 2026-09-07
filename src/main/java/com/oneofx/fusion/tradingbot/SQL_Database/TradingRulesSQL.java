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

        String sql = "INSERT OR REPLACE INTO tradingRules (currency, tickSize, stepSize, minQty) VALUES (?, ?, ?, ?)";
        
        try (Connection con = DriverManager.getConnection(dbUrl.getoneOfX());
             PreparedStatement ps = con.prepareStatement(sql)) {
            
            ps.setString(1, rules.getSymbol());
            ps.setString(2, rules.getTickSize() != null ? rules.getTickSize().toPlainString() : "0.01");
            ps.setString(3, rules.getStepSize() != null ? rules.getStepSize().toPlainString() : "0.001");
            ps.setString(4, rules.getMinQty() != null ? rules.getMinQty().toPlainString() : "0.001");
            
            ps.executeUpdate();
            
        } catch (SQLException err) {
            System.err.println("Fehler beim Speichern von TradingRules für " + rules.getSymbol() + ": " + err.getMessage());
        }
    }

    public static TradingRules getTradingRules(String symbol) {
        if (symbol == null || symbol.trim().isEmpty()) {
            return null;
        }

        String sql = "SELECT currency, tickSize, stepSize, minQty FROM tradingRules WHERE currency = ?";
        
        try (Connection con = DriverManager.getConnection(dbUrl.getoneOfX());
             PreparedStatement ps = con.prepareStatement(sql)) {
            
            ps.setString(1, symbol);
            ResultSet rs = ps.executeQuery();
            
            if (rs.next()) {
                TradingRules rules = new TradingRules(rs.getString("currency"));
                rules.setTickSize(toBigDecimal(rs.getString("tickSize")));
                rules.setStepSize(toBigDecimal(rs.getString("stepSize")));
                rules.setMinQty(toBigDecimal(rs.getString("minQty")));
                return rules;
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
}
