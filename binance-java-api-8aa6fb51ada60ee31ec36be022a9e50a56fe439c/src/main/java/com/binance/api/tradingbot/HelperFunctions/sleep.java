package com.binance.api.tradingbot.HelperFunctions;

public class sleep {

    public static void for_01_second() {
        sleeping(100);
    }

    public static void for_02_second() {
        sleeping(200);
    }

    public static void for_05_second() {
        sleeping(500);
    }

    public static void for_1_second() {
        sleeping(1000);
    }

    public static void for_1_5_seconds() {
        sleeping(1500);
    }

    public static void for_60_seconds() {
        sleeping(60000);
    }

    public static void for_10_seconds() {
        sleeping(10000);
    }

    public static void for_30_seconds() {
        sleeping(30000);
    }

    public static void for_5_min() {
        sleeping(300000);
    }

    public static void valueOffMillieSeconds(int milliseconds) {
        sleeping(milliseconds);
    }

    private static void sleeping(long milliseconds) {
        try {
            Thread.sleep(milliseconds);
        } catch (InterruptedException e) {
            System.out.println("Schlafvorgang unterbrochen: " + e.getMessage());
            Thread.currentThread().interrupt();
        }
    }


    
}
