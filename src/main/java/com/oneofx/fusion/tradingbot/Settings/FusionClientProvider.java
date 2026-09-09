package com.oneofx.fusion.tradingbot.Settings;

import com.oneofx.fusion.client.FusionApiClient;

/** Stellt den gemeinsam verwendeten Bitpanda-Fusion-API-Client bereit. */
public final class FusionClientProvider {
    public static final String API_KEY_ENV = "BITPANDA_FUSION_API_KEY";
    public static final String API_KEY_ENV_ALIAS = "FUSION_API_KEY";
    public static final String BASE_URL_ENV = "BITPANDA_FUSION_BASE_URL";

    private static volatile FusionApiClient client;
    private static volatile String sessionApiKey;
    private static volatile String sessionBaseUrl;

    private FusionClientProvider() {}

    public static FusionApiClient getClient() {
        FusionApiClient current = client;
        if (current == null) {
            synchronized (FusionClientProvider.class) {
                current = client;
                if (current == null) {
                    String apiKey = readApiKey();
                    String baseUrl = readBaseUrl();
                    if (apiKey == null || apiKey.isBlank()) {
                        throw new IllegalStateException(
                                "Set the " + API_KEY_ENV + " environment variable to your Fusion API key");
                    }
                    current = new FusionApiClient(apiKey, baseUrl);
                    client = current;
                }
            }
        }
        return current;
    }

    public static FusionApiClient createRestClient() {
        String apiKey = readApiKey();
        String baseUrl = readBaseUrl();
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("Set the " + API_KEY_ENV + " environment variable to your Fusion API key");
        }
        return new FusionApiClient(apiKey, baseUrl);
    }

    private static String readApiKey() {
        if (sessionApiKey != null && !sessionApiKey.isBlank()) return sessionApiKey;
        String value = System.getenv(API_KEY_ENV);
        if (value == null || value.isBlank()) value = System.getenv(API_KEY_ENV_ALIAS);
        return value;
    }

    /** Übernimmt Zugangsdaten nur für die aktuelle Programmsitzung. */
    public static synchronized void configureSession(String apiKey, String baseUrl) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("Der API-Key darf nicht leer sein.");
        }
        sessionApiKey = apiKey.trim();
        sessionBaseUrl = baseUrl == null || baseUrl.isBlank()
                ? FusionApiClient.DEFAULT_BASE_URL
                : baseUrl.trim();
        client = null;
    }

    public static boolean isConfigured() {
        String apiKey = readApiKey();
        return apiKey != null && !apiKey.isBlank();
    }

    private static String readBaseUrl() {
        if (sessionBaseUrl != null && !sessionBaseUrl.isBlank()) return sessionBaseUrl;
        return System.getenv().getOrDefault(BASE_URL_ENV, FusionApiClient.DEFAULT_BASE_URL);
    }
}
