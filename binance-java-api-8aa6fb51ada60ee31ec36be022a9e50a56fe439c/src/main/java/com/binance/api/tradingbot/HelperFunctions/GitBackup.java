package com.binance.api.tradingbot.HelperFunctions;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class GitBackup {
    
    private static final String WORKSPACE_PATH = "c:\\Users\\Fujitsu\\Desktop\\trading_bot";
    private static final String DATABASE_BACKUP_PATH = WORKSPACE_PATH + "\\database_backup";
    private static final String REMOTE_DB_PATH = "C:\\TradingBot\\SQLiteStudio\\Datenbanken\\LTC_EUR";
    
    // Stündliche Backup-Funktion
    public static void performHourlyBackup() {
        LocalDateTime now = LocalDateTime.now();
        String timestamp = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm"));
        
        try {
            // 0. Sicherstellen, dass wir auf dem richtigen Branch sind
            ensureCorrectBranch();
            
            // 1. Datenbanken kopieren
            copyDatabases();
            
            // 2. Git Add
            executeGitCommand("git add .");
            
            // 3. Git Commit mit Zeitstempel
            String commitMessage = String.format("Hourly backup - %s", timestamp);
            executeGitCommand("git commit -m \"" + commitMessage + "\"");
            
            // 4. Git Push auf aktuellen Branch
            executeGitCommand("git push origin HEAD");
            
            System.out.println("✅ Backup erfolgreich um " + timestamp);
            
        } catch (Exception e) {
            System.err.println("❌ Fehler beim Backup: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    // Kopiert alle SQLite-Datenbanken ins Git-Repository
    private static void copyDatabases() throws IOException {
        // Backup-Ordner erstellen falls nicht vorhanden
        Path backupDir = Paths.get(DATABASE_BACKUP_PATH);
        if (!Files.exists(backupDir)) {
            Files.createDirectories(backupDir);
        }
        
        // Liste der Datenbank-Dateien
        String[] dbFiles = {
            "SETTING.db",
            "ATH_LTCEUR.db", 
            "POS_LTCEUR.db",
            "POS_LTCEUR_HIST.db",
            "WPD.db",
            "ExpoTag.db"
        };
        
        // Jede Datenbank kopieren
        for (String dbFile : dbFiles) {
            Path source = Paths.get(REMOTE_DB_PATH, dbFile);
            Path target = Paths.get(DATABASE_BACKUP_PATH, dbFile);
            
            if (Files.exists(source)) {
                Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
                System.out.println("📁 Kopiert: " + dbFile);
            } else {
                System.out.println("⚠️  Datei nicht gefunden: " + source);
            }
        }
    }
    
    // Führt Git-Befehle aus
    private static void executeGitCommand(String command) throws IOException, InterruptedException {
        ProcessBuilder processBuilder = new ProcessBuilder();
        processBuilder.directory(new File(WORKSPACE_PATH));
        
        // Für Windows PowerShell
        processBuilder.command("powershell.exe", "/c", command);
        
        Process process = processBuilder.start();
        
        // Output lesen
        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        String line;
        while ((line = reader.readLine()) != null) {
            System.out.println("Git: " + line);
        }
        
        // Error stream lesen
        BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
        while ((line = errorReader.readLine()) != null) {
            System.err.println("Git Error: " + line);
        }
        
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            System.err.println("Git Befehl fehlgeschlagen: " + command + " (Exit Code: " + exitCode + ")");
        }
    }
    
    // Prüft ob Git verfügbar ist
    public static boolean isGitAvailable() {
        try {
            ProcessBuilder processBuilder = new ProcessBuilder("git", "--version");
            Process process = processBuilder.start();
            int exitCode = process.waitFor();
            return exitCode == 0;
        } catch (Exception e) {
            return false;
        }
    }
    
    // Manuelle Backup-Funktion mit Custom Message
    public static void performManualBackup(String customMessage) {
        LocalDateTime now = LocalDateTime.now();
        String timestamp = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
        
        try {
            ensureCorrectBranch();
            copyDatabases();
            executeGitCommand("git add .");
            
            String commitMessage = String.format("%s - %s", customMessage, timestamp);
            executeGitCommand("git commit -m \"" + commitMessage + "\"");
            executeGitCommand("git push origin HEAD");
            
            System.out.println("✅ Manuelles Backup erfolgreich: " + commitMessage);
            
        } catch (Exception e) {
            System.err.println("❌ Fehler beim manuellen Backup: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    // Stellt sicher, dass wir auf dem richtigen Branch sind
    private static void ensureCorrectBranch() throws IOException, InterruptedException {
        String targetBranch = GitBackupConfig.getTargetBranch();
        
        // Wenn null → auf aktuellem Branch bleiben
        if (targetBranch == null) {
            String currentBranch = getCurrentBranch();
            System.out.println("✅ Bleibe auf aktuellem Branch: " + currentBranch);
            return;
        }
        
        String currentBranch = getCurrentBranch();
        
        // Wenn wir nicht auf dem Ziel-Branch sind
        if (!targetBranch.equals(currentBranch)) {
            System.out.println("🔄 Aktueller Branch: " + currentBranch + " → Wechsle zu: " + targetBranch);
            
            // Prüfen ob Branch existiert
            if (branchExists(targetBranch)) {
                // Branch existiert → wechseln
                executeGitCommand("git checkout " + targetBranch);
                System.out.println("✅ Gewechselt zu existierendem Branch: " + targetBranch);
            } else {
                // Branch existiert nicht → erstellen und wechseln
                executeGitCommand("git checkout -b " + targetBranch);
                System.out.println("✅ Neuer Branch erstellt: " + targetBranch);
                
                // Ersten Push für neuen Branch
                executeGitCommand("git push --set-upstream origin " + targetBranch);
                System.out.println("✅ Branch im Remote-Repository erstellt");
            }
        } else {
            System.out.println("✅ Bereits auf korrektem Branch: " + targetBranch);
        }
    }
    
    // Ermittelt den aktuellen Branch
    private static String getCurrentBranch() throws IOException, InterruptedException {
        ProcessBuilder processBuilder = new ProcessBuilder();
        processBuilder.directory(new File(WORKSPACE_PATH));
        processBuilder.command("powershell.exe", "/c", "git branch --show-current");
        
        Process process = processBuilder.start();
        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        String currentBranch = reader.readLine();
        
        int exitCode = process.waitFor();
        if (exitCode != 0 || currentBranch == null) {
            throw new RuntimeException("Konnte aktuellen Branch nicht ermitteln");
        }
        
        return currentBranch.trim();
    }
    
    // Prüft ob ein Branch existiert
    private static boolean branchExists(String branchName) throws IOException, InterruptedException {
        ProcessBuilder processBuilder = new ProcessBuilder();
        processBuilder.directory(new File(WORKSPACE_PATH));
        processBuilder.command("powershell.exe", "/c", "git branch --list " + branchName);
        
        Process process = processBuilder.start();
        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        String result = reader.readLine();
        
        process.waitFor();
        return result != null && result.trim().contains(branchName);
    }
}
