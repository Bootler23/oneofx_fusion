package com.binance.api.tradingbot.service;

import com.binance.api.tradingbot.TradingMain.LTC_EUR_Live;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Service;

/**
 * Service der den Trading Bot verwaltet.
 * 
 * Startet automatisch beim Spring Boot-Start und ermöglicht
 * Start/Stop über die Web-Oberfläche.
 */
@Service
public class TradingBotService implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(TradingBotService.class);
    
    private Thread botThread;
    private BotStatus status = BotStatus.STOPPED;
    
    public enum BotStatus {
        STOPPED,
        STARTING,
        RUNNING,
        STOPPING,
        ERROR
    }

    @Override
    public void run(String... args) throws Exception {
        logger.info("Trading Bot Service initialisiert");
        // Bot startet automatisch beim Spring-Start
        startBot();
    }

    /**
     * Startet den Trading Bot in einem separaten Thread.
     * 
     * @return true wenn erfolgreich gestartet, false wenn bereits läuft
     */
    public synchronized boolean startBot() {
        if (isRunning()) {
            logger.warn("Trading Bot läuft bereits!");
            return false;
        }

        try {
            logger.info("Starte Trading Bot...");
            status = BotStatus.STARTING;

            botThread = new Thread(() -> {
                try {
                    status = BotStatus.RUNNING;
                    logger.info("Trading Bot erfolgreich gestartet!");
                    
                    LTC_EUR_Live.main(new String[]{});
                    
                } catch (Exception e) {
                    logger.error("Fehler im Trading Bot", e);
                    status = BotStatus.ERROR;
                } finally {
                    if (status != BotStatus.ERROR) {
                        status = BotStatus.STOPPED;
                    }
                    logger.info("Trading Bot beendet");
                }
            }, "TradingBot-Thread");

            botThread.start();
            return true;
            
        } catch (Exception e) {
            logger.error("Fehler beim Starten des Bots", e);
            status = BotStatus.ERROR;
            return false;
        }
    }

    /**
     * Stoppt den Trading Bot gracefully.
     * 
     * @return true wenn erfolgreich gestoppt, false wenn nicht läuft
     */
    public synchronized boolean stopBot() {
        if (!isRunning()) {
            logger.warn("Trading Bot läuft nicht!");
            return false;
        }

        try {
            logger.info("Stoppe Trading Bot...");
            status = BotStatus.STOPPING;
            
            // Stopp-Signal an LTC_EUR_Live senden
            LTC_EUR_Live.stop();
            
            // Warte auf sauberes Beenden (max 10 Sekunden)
            if (botThread != null && botThread.isAlive()) {
                botThread.join(10000);
                
                if (botThread.isAlive()) {
                    logger.warn("Bot reagiert nicht, forciere Interrupt...");
                    botThread.interrupt();
                }
            }
            
            status = BotStatus.STOPPED;
            logger.info("Trading Bot erfolgreich gestoppt");
            return true;
            
        } catch (InterruptedException e) {
            logger.error("Fehler beim Stoppen des Bots", e);
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * Gibt den Status des Bots zurück.
     * 
     * @return true wenn Bot aktiv läuft
     */
    public boolean isRunning() {
        return status == BotStatus.RUNNING && 
               botThread != null && 
               botThread.isAlive() && 
               LTC_EUR_Live.isRunning();
    }
    
    /**
     * Gibt den aktuellen Status zurück.
     * 
     * @return BotStatus
     */
    public BotStatus getStatus() {
        return status;
    }
}
