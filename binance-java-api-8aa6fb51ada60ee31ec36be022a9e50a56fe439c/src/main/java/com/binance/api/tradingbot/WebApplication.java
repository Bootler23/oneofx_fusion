package com.binance.api.tradingbot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Spring Boot Application für Trading Bot Web-Interface.
 * 
 * Diese Klasse startet den embedded Tomcat-Server und stellt
 * die Weboberfläche für den Trading Bot bereit.
 * 
 * Zugriff auf: http://localhost:8080
 */
@SpringBootApplication
public class WebApplication {

    public static void main(String[] args) {
        SpringApplication.run(WebApplication.class, args);
    }
}
