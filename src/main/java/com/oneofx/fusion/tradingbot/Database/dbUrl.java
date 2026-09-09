package com.oneofx.fusion.tradingbot.Database;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class dbUrl {

    private static final String DB_DIR_ENV = "ONEOFX_DB_DIR";

    public static String getSET() {
        return sqliteUrl("ONEOFX_SETTINGS_DB", "oneofx_fusion.db");
    }

    public static String getoneOfX() {
        return sqliteUrl("ONEOFX_TRADING_DB", "oneofx_fusion.db");
    }

    public static String getWPD() {
        return sqliteUrl("ONEOFX_WPD_DB", "oneofx_fusion.db");
    }

    private static String sqliteUrl(String fileEnv, String fileName) {
        // System-Property zuerst auswerten, damit Tests eine isolierte Datenbank
        // verwenden koennen, ohne Prozess-Umgebungsvariablen zu veraendern.
        String explicitFile = getSetting(fileEnv);
        Path path;
        if (explicitFile != null && !explicitFile.isBlank()) {
            path = Path.of(explicitFile.trim());
        } else {
            String configuredDir = getSetting(DB_DIR_ENV);
            Path directory = configuredDir == null || configuredDir.isBlank()
                    ? Path.of(System.getProperty("user.dir"), "data")
                    : Path.of(configuredDir.trim());
            path = directory.resolve(fileName);
        }
        Path normalized = path.toAbsolutePath().normalize();
        try {
            if (normalized.getParent() != null) Files.createDirectories(normalized.getParent());
        } catch (IOException e) {
            throw new IllegalStateException("Datenbankverzeichnis kann nicht erstellt werden: "
                    + normalized.getParent(), e);
        }
        return "jdbc:sqlite:" + normalized;
    }

    private static String getSetting(String name) {
        String property = System.getProperty(name);
        return property != null ? property : System.getenv(name);
    }
}
