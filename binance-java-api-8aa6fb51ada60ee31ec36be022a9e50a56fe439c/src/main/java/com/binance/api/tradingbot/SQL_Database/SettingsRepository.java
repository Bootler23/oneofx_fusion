package com.binance.api.tradingbot.SQL_Database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.binance.api.tradingbot.Database.dbUrl;
import com.binance.api.tradingbot.HelperFunctions.round;

public class SettingsRepository {
    
    private static final Logger logger = LoggerFactory.getLogger(SettingsRepository.class);   
      
    public static double getDouble(String columnName) {
        String sql = "SELECT " + columnName + " FROM SETTING";
        
        try (Connection con = DriverManager.getConnection(dbUrl.getSET());
             Statement query = con.createStatement();
             ResultSet rs = query.executeQuery(sql)) {
            
            if (rs.next()) {
                double value = rs.getDouble(columnName);
                return rs.wasNull() ? 0.0 : value;
            }
            return 0.0;
            
        } catch (SQLException err) {
            logger.error("Fehler beim Abrufen von {}: {}", columnName, err.getMessage());
            return 0.0;
        }
    }    
  
    // public static boolean setDouble(String columnName, double value, int roundDigits) {
    //     String sql = "UPDATE SETTING SET " + columnName + " = ?";
        
    //     try (Connection con = DriverManager.getConnection(dbUrl.getSET());
    //          PreparedStatement ps = con.prepareStatement(sql)) {
            
    //         double roundedValue = roundDigits > 0 ? roundValue(value, roundDigits) : value;
    //         ps.setDouble(1, roundedValue);
            
    //         int rowsAffected = ps.executeUpdate();
    //         if (rowsAffected > 0) {
    //             logger.debug("{} erfolgreich auf {} gesetzt", columnName, roundedValue);
    //             return true;
    //         }
    //         return false;
            
    //     } catch (SQLException err) {
    //         logger.error("Fehler beim Setzen von {} auf {}: {}", columnName, value, err.getMessage());
    //         return false;
    //     }
    // }    

    public static void setDouble(String columnName, double value, int roundDigits) {
        String sql = "UPDATE SETTING SET " + columnName + " = ?";
        
        try (Connection con = DriverManager.getConnection(dbUrl.getSET());
             PreparedStatement ps = con.prepareStatement(sql)) {
            
            // Wert runden falls roundDigits angegeben
            double roundedValue;
            if (roundDigits > 0) {
                roundedValue = roundValue(value, roundDigits);
            } else {
                roundedValue = value;
            }
            
            // Gerundeten Wert in DB speichern
            ps.setDouble(1, roundedValue);
            ps.executeUpdate();
            
        } catch (SQLException err) {
            logger.error("Fehler beim Setzen von {} auf {}: {}", columnName, value, err.getMessage());
        }
    }
   
    public static int getInt(String columnName, int defaultValue) {
        String sql = "SELECT " + columnName + " FROM SETTING";
        
        try (Connection con = DriverManager.getConnection(dbUrl.getSET());
             Statement query = con.createStatement();
             ResultSet rs = query.executeQuery(sql)) {
            
            if (rs.next()) {
                int value = rs.getInt(columnName);
                return rs.wasNull() ? defaultValue : value;
            }
            return defaultValue;
            
        } catch (SQLException err) {
            logger.error("Fehler beim Abrufen von {}: {}", columnName, err.getMessage());
            return defaultValue;
        }
    }    
   
    public static boolean setInt(String columnName, int value) {
        String sql = "UPDATE SETTING SET " + columnName + " = ?";
        
        try (Connection con = DriverManager.getConnection(dbUrl.getSET());
             PreparedStatement ps = con.prepareStatement(sql)) {
            
            ps.setInt(1, value);
            int rowsAffected = ps.executeUpdate();
            
            if (rowsAffected > 0) {
                logger.debug("{} erfolgreich auf {} gesetzt", columnName, value);
                return true;
            }
            return false;
            
        } catch (SQLException err) {
            logger.error("Fehler beim Setzen von {} auf {}: {}", columnName, value, err.getMessage());
            return false;
        }
    }    
   
    public static boolean getBoolean(String columnName, boolean defaultValue) {
        String sql = "SELECT " + columnName + " FROM SETTING";
        
        try (Connection con = DriverManager.getConnection(dbUrl.getSET());
             Statement query = con.createStatement();
             ResultSet rs = query.executeQuery(sql)) {
            
            if (rs.next()) {
                String value = rs.getString(columnName);
                if (value == null) {
                    return defaultValue;
                }
                return "true".equalsIgnoreCase(value) || "1".equals(value);
            }
            return defaultValue;
            
        } catch (SQLException err) {
            logger.error("Fehler beim Abrufen von {}: {}", columnName, err.getMessage());
            return defaultValue;
        }
    }    
  
    public static boolean setBoolean(String columnName, boolean value) {
        String sql = "UPDATE SETTING SET " + columnName + " = ?";
        
        try (Connection con = DriverManager.getConnection(dbUrl.getSET());
             PreparedStatement ps = con.prepareStatement(sql)) {
            
            ps.setString(1, value ? "1" : "0");
            int rowsAffected = ps.executeUpdate();
            
            if (rowsAffected > 0) {
                logger.debug("{} erfolgreich auf {} gesetzt", columnName, value);
                return true;
            }
            return false;
            
        } catch (SQLException err) {
            logger.error("Fehler beim Setzen von {} auf {}: {}", columnName, value, err.getMessage());
            return false;
        }
    }    
   
    public static String getString(String columnName, String defaultValue) {
        String sql = "SELECT " + columnName + " FROM SETTING";
        
        try (Connection con = DriverManager.getConnection(dbUrl.getSET());
             Statement query = con.createStatement();
             ResultSet rs = query.executeQuery(sql)) {
            
            if (rs.next()) {
                String value = rs.getString(columnName);
                return value != null ? value : defaultValue;
            }
            return defaultValue;
            
        } catch (SQLException err) {
            logger.error("Fehler beim Abrufen von {}: {}", columnName, err.getMessage());
            return defaultValue;
        }
    }    
   
    public static boolean setString(String columnName, String value) {
        String sql = "UPDATE SETTING SET " + columnName + " = ?";
        
        try (Connection con = DriverManager.getConnection(dbUrl.getSET());
             PreparedStatement ps = con.prepareStatement(sql)) {
            
            ps.setString(1, value);
            int rowsAffected = ps.executeUpdate();
            
            if (rowsAffected > 0) {
                logger.debug("{} erfolgreich auf '{}' gesetzt", columnName, value);
                return true;
            }
            return false;
            
        } catch (SQLException err) {
            logger.error("Fehler beim Setzen von {} auf '{}': {}", columnName, value, err.getMessage());
            return false;
        }
    }    
   
    private static double roundValue(double value, int digits) {
        switch (digits) {
            case 2:
                return round.two(value);
            case 3:
                return round.three(value);
            case 5:
                return round.five(value);
            case 6:
                return round.six(value);
            case 8:
                return round.eight(value);
            default:
                logger.warn("Nicht unterstützte Rundungsanzahl: {}. Keine Rundung durchgeführt.", digits);
                return value;
        }
    }    
  
    public static boolean columnExists(String columnName) {
        String sql = "PRAGMA table_info(SETTING)";
        
        try (Connection con = DriverManager.getConnection(dbUrl.getSET());
             Statement query = con.createStatement();
             ResultSet rs = query.executeQuery(sql)) {
            
            while (rs.next()) {
                String name = rs.getString("name");
                if (columnName.equals(name)) {
                    return true;
                }
            }
            return false;
            
        } catch (SQLException err) {
            logger.error("Fehler beim Prüfen der Spalte {}: {}", columnName, err.getMessage());
            return false;
        }
    }
}
