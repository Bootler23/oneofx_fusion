package com.oneofx.fusion.tradingbot.SQL_Database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import com.oneofx.fusion.tradingbot.Database.dbUrl;
import com.oneofx.fusion.tradingbot.HelperFunctions.Time;
import com.oneofx.fusion.tradingbot.HelperFunctions.round;
import com.oneofx.fusion.tradingbot.domain.HistoryPosition;

/**
 * DAO für die historyPosition-Tabelle. Ersetzt HISTSQL (statisch) durch Instanz-Methoden
 * mit modularem insert/update und PreparedStatements überall.
 */
public class HistDAO {

    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection(dbUrl.getoneOfX());
    }

    // ===================== INSERT (modular) =====================

    public void insert(HistoryPosition pos) {
        List<String> columns = new ArrayList<>();
        List<Object> values = new ArrayList<>();

        columns.add("currency");   values.add(pos.getCurrency());
        columns.add("BuyOrderId");  values.add(pos.getBuyOrderId());

        if (pos.getQuantity() != null)           { columns.add("Quantity");           values.add(pos.getQuantity()); }
        if (pos.getBuyAmount() != null)           { columns.add("BuyAmount");           values.add(pos.getBuyAmount()); }
        if (pos.getOrigPrice() != null)           { columns.add("OrigPrice");           values.add(pos.getOrigPrice()); }
        if (pos.getBuyPrice() != null)            { columns.add("BuyPrice");            values.add(pos.getBuyPrice()); }
        if (pos.getSellPrice() != null)           { columns.add("SellPrice");           values.add(pos.getSellPrice()); }
        if (pos.getSellOrderId() != null)         { columns.add("SellOrderId");         values.add(pos.getSellOrderId()); }
        if (pos.getSellAmount() != null)          { columns.add("SellAmount");          values.add(pos.getSellAmount()); }
        if (pos.getBuyDate() != null)             { columns.add("BuyDate");             values.add(pos.getBuyDate()); }
        if (pos.getBuyTime() != null)             { columns.add("BuyTime");             values.add(pos.getBuyTime()); }
        if (pos.getSellDate() != null)            { columns.add("SellDate");            values.add(pos.getSellDate()); }
        if (pos.getSellTime() != null)            { columns.add("SellTime");            values.add(pos.getSellTime()); }
        if (pos.getFee() != null)                 { columns.add("Fee");                 values.add(pos.getFee()); }
        if (pos.getTax() != null)                 { columns.add("Tax");                 values.add(pos.getTax()); }
        if (pos.getGewinn() != null)              { columns.add("Gewinn");              values.add(pos.getGewinn()); }
        if (pos.getGewinnAfterTax() != null)      { columns.add("GewinnAfterTax");      values.add(pos.getGewinnAfterTax()); }
        if (pos.getLossAfterTax() != null)        { columns.add("LossAfterTax");        values.add(pos.getLossAfterTax()); }
        if (pos.getProfit() != null)              { columns.add("Profit");              values.add(pos.getProfit()); }
        if (pos.getSplit() != null)               { columns.add("Split");               values.add(pos.getSplit()); }
        if (pos.getStatus() != null)              { columns.add("Status");              values.add(pos.getStatus()); }
        if (pos.getStatusCode() != null)          { columns.add("statusCode");          values.add(pos.getStatusCode()); }
        if (pos.getBuyFee() != null)              { columns.add("BuyFee");              values.add(pos.getBuyFee()); }
        if (pos.getSellFee() != null)             { columns.add("SellFee");             values.add(pos.getSellFee()); }
        if (pos.getBalanceAtBuy() != null)        { columns.add("Balance_atBuy");       values.add(pos.getBalanceAtBuy()); }
        if (pos.getAssetAtBuy() != null)          { columns.add("Asset_atBuy");         values.add(pos.getAssetAtBuy()); }
        if (pos.getBalanceToAssetAtBuy() != null) { columns.add("BalanceToAsset_atBuy"); values.add(pos.getBalanceToAssetAtBuy()); }
        if (pos.getPosCount() != null)            { columns.add("POS_count");           values.add(pos.getPosCount()); }
        if (pos.getX() != null)                   { columns.add("X");                   values.add(pos.getX()); }

        String cols = String.join(", ", columns);
        String placeholders = String.join(", ", columns.stream().map(c -> "?").toArray(String[]::new));
        String sql = "INSERT INTO historyPosition (" + cols + ") VALUES (" + placeholders + ")";

        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            for (int i = 0; i < values.size(); i++) {
                ps.setObject(i + 1, values.get(i));
            }
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Fehler beim Insert in historyPosition: " + e.getMessage());
        }
    }

    // ===================== UPDATE (modular, WHERE BuyOrderId) =====================

    public void updateByBuyOrderId(HistoryPosition pos) {
        List<String> setClauses = new ArrayList<>();
        List<Object> values = new ArrayList<>();

        if (pos.getQuantity() != null)           { setClauses.add("Quantity = ?");           values.add(pos.getQuantity()); }
        if (pos.getBuyAmount() != null)           { setClauses.add("BuyAmount = ?");           values.add(pos.getBuyAmount()); }
        if (pos.getOrigPrice() != null)           { setClauses.add("OrigPrice = ?");           values.add(pos.getOrigPrice()); }
        if (pos.getBuyPrice() != null)            { setClauses.add("BuyPrice = ?");            values.add(pos.getBuyPrice()); }
        if (pos.getSellPrice() != null)           { setClauses.add("SellPrice = ?");           values.add(pos.getSellPrice()); }
        if (pos.getSellOrderId() != null)         { setClauses.add("SellOrderId = ?");         values.add(pos.getSellOrderId()); }
        if (pos.getSellAmount() != null)          { setClauses.add("SellAmount = ?");          values.add(pos.getSellAmount()); }
        if (pos.getBuyDate() != null)             { setClauses.add("BuyDate = ?");             values.add(pos.getBuyDate()); }
        if (pos.getBuyTime() != null)             { setClauses.add("BuyTime = ?");             values.add(pos.getBuyTime()); }
        if (pos.getSellDate() != null)            { setClauses.add("SellDate = ?");            values.add(pos.getSellDate()); }
        if (pos.getSellTime() != null)            { setClauses.add("SellTime = ?");            values.add(pos.getSellTime()); }
        if (pos.getFee() != null)                 { setClauses.add("Fee = ?");                 values.add(pos.getFee()); }
        if (pos.getTax() != null)                 { setClauses.add("Tax = ?");                 values.add(pos.getTax()); }
        if (pos.getGewinn() != null)              { setClauses.add("Gewinn = ?");              values.add(pos.getGewinn()); }
        if (pos.getGewinnAfterTax() != null)      { setClauses.add("GewinnAfterTax = ?");      values.add(pos.getGewinnAfterTax()); }
        if (pos.getLossAfterTax() != null)        { setClauses.add("LossAfterTax = ?");        values.add(pos.getLossAfterTax()); }
        if (pos.getProfit() != null)              { setClauses.add("Profit = ?");              values.add(pos.getProfit()); }
        if (pos.getSplit() != null)               { setClauses.add("Split = ?");               values.add(pos.getSplit()); }
        if (pos.getStatus() != null)              { setClauses.add("Status = ?");              values.add(pos.getStatus()); }
        if (pos.getStatusCode() != null)          { setClauses.add("statusCode = ?");          values.add(pos.getStatusCode()); }
        if (pos.getBuyFee() != null)              { setClauses.add("BuyFee = ?");              values.add(pos.getBuyFee()); }
        if (pos.getSellFee() != null)             { setClauses.add("SellFee = ?");             values.add(pos.getSellFee()); }

        if (setClauses.isEmpty()) return;

        String sql = "UPDATE historyPosition SET " + String.join(", ", setClauses) + " WHERE BuyOrderId = ?";
        values.add(pos.getBuyOrderId());

        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            for (int i = 0; i < values.size(); i++) {
                ps.setObject(i + 1, values.get(i));
            }
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Fehler beim Update in historyPosition (BuyOrderId): " + e.getMessage());
        }
    }

    // ===================== UPDATE (WHERE SellOrderId) =====================

    public void updateBySellOrderId(HistoryPosition pos) {
        List<String> setClauses = new ArrayList<>();
        List<Object> values = new ArrayList<>();

        if (pos.getSellAmount() != null)          { setClauses.add("SellAmount = ?");          values.add(pos.getSellAmount()); }
        if (pos.getSellPrice() != null)           { setClauses.add("SellPrice = ?");           values.add(pos.getSellPrice()); }
        if (pos.getTax() != null)                 { setClauses.add("Tax = ?");                 values.add(pos.getTax()); }
        if (pos.getFee() != null)                 { setClauses.add("Fee = ?");                 values.add(pos.getFee()); }
        if (pos.getGewinn() != null)              { setClauses.add("Gewinn = ?");              values.add(pos.getGewinn()); }
        if (pos.getGewinnAfterTax() != null)      { setClauses.add("GewinnAfterTax = ?");      values.add(pos.getGewinnAfterTax()); }
        if (pos.getLossAfterTax() != null)        { setClauses.add("LossAfterTax = ?");        values.add(pos.getLossAfterTax()); }
        if (pos.getProfit() != null)              { setClauses.add("Profit = ?");              values.add(pos.getProfit()); }
        if (pos.getSellFee() != null)             { setClauses.add("SellFee = ?");             values.add(pos.getSellFee()); }
        if (pos.getSplit() != null)               { setClauses.add("Split = ?");               values.add(pos.getSplit()); }
        if (pos.getStatus() != null)              { setClauses.add("Status = ?");              values.add(pos.getStatus()); }
        if (pos.getStatusCode() != null)          { setClauses.add("statusCode = ?");          values.add(pos.getStatusCode()); }
        if (pos.getBuyPrice() != null)            { setClauses.add("BuyPrice = ?");            values.add(pos.getBuyPrice()); }
        if (pos.getOrigPrice() != null)           { setClauses.add("OrigPrice = ?");           values.add(pos.getOrigPrice()); }
        if (pos.getQuantity() != null)            { setClauses.add("Quantity = ?");            values.add(pos.getQuantity()); }
        if (pos.getBuyAmount() != null)           { setClauses.add("BuyAmount = ?");           values.add(pos.getBuyAmount()); }
        if (pos.getBuyFee() != null)              { setClauses.add("BuyFee = ?");              values.add(pos.getBuyFee()); }

        if (setClauses.isEmpty()) return;

        String sql = "UPDATE historyPosition SET " + String.join(", ", setClauses) + " WHERE SellOrderId = ?";
        values.add(pos.getSellOrderId());

        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            for (int i = 0; i < values.size(); i++) {
                ps.setObject(i + 1, values.get(i));
            }
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Fehler beim Update in historyPosition (SellOrderId): " + e.getMessage());
        }
    }

    // ===================== Queries (aus HISTSQL migriert) =====================

    public List<String> getSellTradeRecordsWhereStatusZero() {
        List<String> records = new ArrayList<>();
        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT SellOrderId, Quantity, currency, BuyPrice FROM historyPosition WHERE Status = 0")) {
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                records.add(rs.getString("SellOrderId") + ", " + rs.getString("Quantity") + ", "
                        + rs.getString("BuyPrice") + ", " + rs.getString("currency"));
            }
        } catch (SQLException e) {
            System.err.println("SQL-Fehler: " + e.getMessage());
        }
        return records;
    }

    public List<String> getDataRecordsWhereStatusOne(String currency) {
        List<String> records = new ArrayList<>();
        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT BuyOrderId, Split, currency FROM historyPosition WHERE Status = 1 AND Split IS NOT NULL AND currency = ?")) {
            ps.setString(1, currency);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                records.add(rs.getString("BuyOrderId") + ", " + rs.getString("Split") + ", " + rs.getString("currency"));
            }
        } catch (SQLException e) {
            System.err.println("SQL-Fehler: " + e.getMessage());
        }
        return records;
    }

    public double getTaxe() {
        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement("SELECT ROUND(SUM(Tax), 2) AS TotalTax FROM historyPosition")) {
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getDouble("TotalTax");
        } catch (SQLException e) {
            System.err.println("SQL-Fehler: " + e.getMessage());
        }
        return 0.0;
    }

    public double getBuyAmount(String sellOrderId) {
        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement("SELECT BuyAmount FROM historyPosition WHERE SellOrderId = ?")) {
            ps.setString(1, sellOrderId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getDouble("BuyAmount");
        } catch (SQLException e) {
            System.err.println("SQL-Fehler: " + e.getMessage());
        }
        return 0.0;
    }

    public double getBuyFee(String sellOrderId) {
        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement("SELECT BuyFee FROM historyPosition WHERE SellOrderId = ?")) {
            ps.setString(1, sellOrderId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getDouble("BuyFee");
        } catch (SQLException e) {
            System.err.println("SQL-Fehler: " + e.getMessage());
        }
        return 0.0;
    }

    public double getBuyPrice(String sellOrderId) {
        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement("SELECT BuyPrice FROM historyPosition WHERE SellOrderId = ?")) {
            ps.setString(1, sellOrderId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getDouble("BuyPrice");
        } catch (SQLException e) {
            System.err.println("SQL-Fehler: " + e.getMessage());
        }
        return 0.0;
    }

    public String getBuyOrderId(String sellOrderId) {
        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement("SELECT BuyOrderId FROM historyPosition WHERE SellOrderId = ?")) {
            ps.setString(1, sellOrderId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString("BuyOrderId") : null;
            }
        } catch (SQLException e) {
            System.err.println("SQL-Fehler: " + e.getMessage());
            return null;
        }
    }

    public void resetPendingSell(String sellOrderId) {
        String sql = "UPDATE historyPosition SET SellOrderId = NULL, SellDate = NULL, SellTime = NULL, Status = NULL "
                + "WHERE SellOrderId = ?";
        try (Connection con = getConnection(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, sellOrderId);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("SQL-Fehler beim Zurücksetzen der Sell-Order: " + e.getMessage());
        }
    }

    public int getCountHist() {
        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement("SELECT COUNT(*) AS TotalCount FROM historyPosition")) {
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getInt("TotalCount");
        } catch (SQLException e) {
            System.err.println("SQL-Fehler: " + e.getMessage());
        }
        return 0;
    }

    public void setSellFee(String sellOrderId, double sellFee) {
        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement("UPDATE historyPosition SET SellFee = ? WHERE SellOrderId = ?")) {
            ps.setDouble(1, sellFee);
            ps.setString(2, sellOrderId);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("SQL-Fehler: " + e.getMessage());
        }
    }

    public double getSumColumnToday(String url, String columnName, String tableName) {
        String todayString = Time.getCurrentDate();
        String sql = "SELECT SUM(" + sanitizeIdentifier(columnName) + ") AS SumColumn FROM " + sanitizeIdentifier(tableName) + " WHERE SellDate = ?";
        try (Connection con = DriverManager.getConnection(url);
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, todayString);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getDouble("SumColumn");
        } catch (SQLException e) {
            System.err.println("SQL-Fehler: " + e.getMessage());
        }
        return 0.0;
    }

    // ===================== Hilfsmethoden =====================

    private String sanitizeIdentifier(String name) {
        return name.replaceAll("[^a-zA-Z0-9_€]", "");
    }
}
