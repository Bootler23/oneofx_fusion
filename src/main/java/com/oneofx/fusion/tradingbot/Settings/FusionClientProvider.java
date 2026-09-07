package com.oneofx.fusion.tradingbot.Settings;

import com.oneofx.fusion.client.FusionApiClient;

/** Provides the shared Bitpanda Fusion API client. */
public final class FusionClientProvider {
    public static final String API_KEY_ENV = "BITPANDA_FUSION_API_KEY";
    public static final String API_KEY_ENV_ALIAS = "FUSION_API_KEY";
    public static final String BASE_URL_ENV = "BITPANDA_FUSION_BASE_URL";

    private static volatile FusionApiClient client;

    private FusionClientProvider() {}

    public static FusionApiClient getClient() {
        FusionApiClient current = client;
        if (current == null) {
            synchronized (FusionClientProvider.class) {
                current = client;
                if (current == null) {
                    String apiKey = readApiKey();
                    String baseUrl = System.getenv().getOrDefault(BASE_URL_ENV, FusionApiClient.DEFAULT_BASE_URL);
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
        String baseUrl = System.getenv().getOrDefault(BASE_URL_ENV, FusionApiClient.DEFAULT_BASE_URL);
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("Set the " + API_KEY_ENV + " environment variable to your Fusion API key");
        }
        return new FusionApiClient(apiKey, baseUrl);
    }

    private static String readApiKey() {
        String value = System.getenv(API_KEY_ENV);
        if (value == null || value.isBlank()) value = System.getenv(API_KEY_ENV_ALIAS);
        return value;
    }
}
