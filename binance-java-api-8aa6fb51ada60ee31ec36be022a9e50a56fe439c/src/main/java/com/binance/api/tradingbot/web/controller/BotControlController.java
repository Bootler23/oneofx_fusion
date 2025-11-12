package com.binance.api.tradingbot.web.controller;

import com.binance.api.tradingbot.service.TradingBotService;
import com.binance.api.tradingbot.service.TradingBotService.BotStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * REST-API Controller für Bot-Steuerung.
 * 
 * Ermöglicht Start/Stop/Status-Abfrage des Trading-Bots über die Web-API.
 */
@RestController
@RequestMapping("/api/bot")
public class BotControlController {
    
    private static final Logger logger = LoggerFactory.getLogger(BotControlController.class);
    
    private final TradingBotService tradingBotService;
    
    public BotControlController(TradingBotService tradingBotService) {
        this.tradingBotService = tradingBotService;
    }
    
    /**
     * Startet den Trading-Bot.
     * 
     * @return Response mit Status
     */
    @PostMapping("/start")
    public ResponseEntity<Map<String, Object>> startBot() {
        logger.info("API-Request: Bot starten");
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            boolean started = tradingBotService.startBot();
            
            if (started) {
                response.put("success", true);
                response.put("message", "Bot erfolgreich gestartet");
                response.put("status", tradingBotService.getStatus().name());
                return ResponseEntity.ok(response);
            } else {
                response.put("success", false);
                response.put("message", "Bot läuft bereits");
                response.put("status", tradingBotService.getStatus().name());
                return ResponseEntity.badRequest().body(response);
            }
            
        } catch (Exception e) {
            logger.error("Fehler beim Starten des Bots", e);
            response.put("success", false);
            response.put("message", "Fehler beim Starten: " + e.getMessage());
            response.put("status", "ERROR");
            return ResponseEntity.internalServerError().body(response);
        }
    }
    
    /**
     * Stoppt den Trading-Bot.
     * 
     * @return Response mit Status
     */
    @PostMapping("/stop")
    public ResponseEntity<Map<String, Object>> stopBot() {
        logger.info("API-Request: Bot stoppen");
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            boolean stopped = tradingBotService.stopBot();
            
            if (stopped) {
                response.put("success", true);
                response.put("message", "Bot erfolgreich gestoppt");
                response.put("status", tradingBotService.getStatus().name());
                return ResponseEntity.ok(response);
            } else {
                response.put("success", false);
                response.put("message", "Bot läuft nicht");
                response.put("status", tradingBotService.getStatus().name());
                return ResponseEntity.badRequest().body(response);
            }
            
        } catch (Exception e) {
            logger.error("Fehler beim Stoppen des Bots", e);
            response.put("success", false);
            response.put("message", "Fehler beim Stoppen: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }
    
    /**
     * Gibt den aktuellen Bot-Status zurück.
     * 
     * @return Status-Informationen
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        Map<String, Object> response = new HashMap<>();
        
        BotStatus status = tradingBotService.getStatus();
        boolean isRunning = tradingBotService.isRunning();
        
        response.put("status", status.name());
        response.put("isRunning", isRunning);
        response.put("statusText", getStatusText(status));
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Gibt einen benutzerfreundlichen Status-Text zurück.
     */
    private String getStatusText(BotStatus status) {
        switch (status) {
            case RUNNING:
                return "Bot läuft";
            case STOPPED:
                return "Bot gestoppt";
            case STARTING:
                return "Bot startet...";
            case STOPPING:
                return "Bot wird gestoppt...";
            case ERROR:
                return "Bot hat einen Fehler";
            default:
                return "Unbekannt";
        }
    }
}
