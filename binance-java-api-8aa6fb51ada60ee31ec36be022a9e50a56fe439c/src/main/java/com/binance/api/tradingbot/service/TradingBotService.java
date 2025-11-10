package com.binance.api.tradingbot.service;

import com.binance.api.tradingbot.TradingMain.LTC_EUR_Live;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Service;

/**
 * Service der den Trading Bot automatisch startet, wenn Spring Boot läuft.
 * 
 * Der Bot läuft in einem separaten Thread, damit er die Web-Anwendung nicht blockiert.
 */
@Service
public class TradingBotService implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(TradingBotService.class);
    private Thread botThread;
    private volatile boolean isRunning = false;

    @Override
    public void run(String... args) throws Exception {
        logger.info("Trading Bot Service wird gestartet...");
        startBot();
    }

    /**
     * Startet den Trading Bot in einem separaten Thread.
     */
    public void startBot() {
        if (isRunning) {
            logger.warn("Trading Bot läuft bereits!");
            return;
        }

        botThread = new Thread(() -> {
            try {
                isRunning = true;
                logger.info("Trading Bot gestartet!");
                LTC_EUR_Live.main(new String[]{});
            } catch (Exception e) {
                logger.error("Fehler im Trading Bot", e);
                isRunning = false;
            }
        });

        botThread.setName("TradingBot-Thread");
        botThread.setDaemon(false); // Bot läuft weiter, auch wenn Spring beendet wird
        botThread.start();
    }

    /**
     * Stoppt den Trading Bot (falls implementiert).
     */
    public void stopBot() {
        if (botThread != null && botThread.isAlive()) {
            logger.info("Stoppe Trading Bot...");
            botThread.interrupt();
            isRunning = false;
        }
    }

    /**
     * Gibt den Status des Bots zurück.
     */
    public boolean isRunning() {
        return isRunning && botThread != null && botThread.isAlive();
    }
}
