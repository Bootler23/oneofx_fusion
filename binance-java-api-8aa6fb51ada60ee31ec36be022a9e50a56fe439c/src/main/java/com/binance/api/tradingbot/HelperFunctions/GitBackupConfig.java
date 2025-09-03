package com.binance.api.tradingbot.HelperFunctions;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Erweiterte Git-Backup-Konfiguration
 * Ermöglicht verschiedene Backup-Strategien je nach Bedarf
 */
public class GitBackupConfig {
    
    // Backup-Strategien
    public enum BackupStrategy {
        SAME_BRANCH,        // Auf aktuellem Branch bleiben
        DEDICATED_BRANCH,   // Immer auf speziellen Backup-Branch
        DAILY_BRANCHES,     // Täglich neue Branches (auto-backup-2025-09-03)
        HOURLY_BRANCHES     // Stündlich neue Branches (auto-backup-2025-09-03-14)
    }
    
    private static BackupStrategy currentStrategy = BackupStrategy.DEDICATED_BRANCH;
    private static String customBackupBranch = "auto-backup";
    
    // Konfiguration ändern
    public static void setBackupStrategy(BackupStrategy strategy) {
        currentStrategy = strategy;
        System.out.println("🔧 Backup-Strategie geändert zu: " + strategy);
    }
    
    public static void setCustomBackupBranch(String branchName) {
        customBackupBranch = branchName;
        System.out.println("🔧 Custom Backup-Branch gesetzt: " + branchName);
    }
    
    // Ermittelt den zu verwendenden Branch basierend auf der Strategie
    public static String getTargetBranch() {
        LocalDateTime now = LocalDateTime.now();
        
        switch (currentStrategy) {
            case SAME_BRANCH:
                return null; // Bleibt auf aktuellem Branch
                
            case DEDICATED_BRANCH:
                return customBackupBranch;
                
            case DAILY_BRANCHES:
                return "auto-backup-" + now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
                
            case HOURLY_BRANCHES:
                return "auto-backup-" + now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HH"));
                
            default:
                return customBackupBranch;
        }
    }
    
    // Info über aktuelle Konfiguration
    public static void printCurrentConfig() {
        System.out.println("📋 === Git-Backup Konfiguration ===");
        System.out.println("Strategie: " + currentStrategy);
        System.out.println("Custom Branch: " + customBackupBranch);
        System.out.println("Aktueller Ziel-Branch: " + getTargetBranch());
        System.out.println("=====================================");
    }
}
