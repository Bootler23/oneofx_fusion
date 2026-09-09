package com.oneofx.fusion.tradingbot.Database;

import java.nio.file.Path;

/** Richtet die beschreibbaren Verzeichnisse relativ zum Programmordner ein. */
public final class PortablePaths {

    public static final String HOME_PROPERTY = "ONEOFX_HOME";

    private static volatile Path baseDirectory;

    private PortablePaths() {
    }

    public static synchronized Path initialize() {
        if (baseDirectory != null) {
            return baseDirectory;
        }

        String configuredHome = readSetting(HOME_PROPERTY);
        baseDirectory = configuredHome == null || configuredHome.isBlank()
                ? Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize()
                : Path.of(configuredHome.trim()).toAbsolutePath().normalize();

        // Explizit gesetzte Datenbankpfade haben weiterhin Vorrang.
        if (readSetting("ONEOFX_DB_DIR") == null) {
            System.setProperty("ONEOFX_DB_DIR", baseDirectory.resolve("data").toString());
        }
        return baseDirectory;
    }

    public static Path getBaseDirectory() {
        return initialize();
    }

    public static Path getDataDirectory() {
        return getBaseDirectory().resolve("data");
    }

    public static Path getLogDirectory() {
        return getBaseDirectory().resolve("logs");
    }

    private static String readSetting(String name) {
        String property = System.getProperty(name);
        return property != null ? property : System.getenv(name);
    }
}
