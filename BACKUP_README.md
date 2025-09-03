# 🔄 Automatisches Git-Backup System

Dieses System führt automatisch stündliche Backups Ihrer Trading-Bot-Daten durch.

## 📋 Was wird gesichert?

### Datenbanken
- `SETTING.db` - Bot-Einstellungen
- `ATH_LTCEUR.db` - All-Time-High Daten
- `POS_LTCEUR.db` - Aktuelle Positionen
- `POS_LTCEUR_HIST.db` - Positions-Historie
- `WPD.db` - Gewinn/Verlust Daten
- `ExpoTag.db` - Exposition Daten

### Quellcode
- Alle Java-Dateien
- Konfigurationsdateien
- Diese README

## ⚙️ Konfiguration

### Pfade (in GitBackup.java anpassen wenn nötig):
```java
private static final String WORKSPACE_PATH = "c:\\Users\\Fujitsu\\Desktop\\trading_bot";
private static final String REMOTE_DB_PATH = "C:\\TradingBot\\SQLiteStudio\\Datenbanken\\LTC_EUR";
```

### Automatische Backups
- **Wann**: Jede volle Stunde (wenn `HourlySchedulerExample.executeHourly()` aufgerufen wird)
- **Was passiert**:
  1. Kopiert alle Datenbanken nach `database_backup/`
  2. Führt `git add .` aus
  3. Erstellt Commit mit Zeitstempel
  4. Pushed zu GitHub

### Manuelle Backups
```java
// Manuelles Backup mit eigener Nachricht
GitBackup.performManualBackup("Wichtiges Update vor großem Trade");
```

## 🚀 Setup

1. **Git Repository initialisieren** (falls noch nicht geschehen):
```powershell
cd "c:\Users\Fujitsu\Desktop\trading_bot"
git init
git remote add origin YOUR_GITHUB_REPO_URL
```

2. **Erste Push**:
```powershell
git add .
git commit -m "Initial Trading Bot Setup mit Backup-System"
git push -u origin master
```

3. **Bot starten** - Backups laufen automatisch!

## 📊 Monitoring

Console-Output zeigt Backup-Status:
- ✅ `Backup erfolgreich um 2025-09-03_14-00`
- ❌ `Fehler beim Backup: ...`
- 📁 `Kopiert: SETTING.db`

## 🔧 Troubleshooting

### Git nicht gefunden
```java
if (!GitBackup.isGitAvailable()) {
    System.err.println("Git ist nicht installiert oder nicht im PATH!");
}
```

### Datenbank-Pfad falsch
Prüfen Sie die Pfade in `GitBackup.java` und `dbUrl.java`

### Push-Fehler
- Überprüfen Sie Git-Credentials
- Überprüfen Sie Internet-Verbindung
- Überprüfen Sie Repository-Berechtigung

## 📈 Vorteile

- **Automatisch**: Keine manuelle Intervention nötig
- **Versioniert**: Komplette Historie aller Änderungen
- **Remote**: Daten sicher in der Cloud
- **Transparent**: Vollständige Logs in der Console
- **Flexibel**: Manuelle Backups jederzeit möglich
