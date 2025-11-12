package com.binance.api.tradingbot.web.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Controller für die Haupt-Dashboard-Ansichten.
 */
@Controller
public class DashboardController {

    @GetMapping("/")
    public String index() {
        return "index";
    }

    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        // TODO: Daten aus Database holen
        model.addAttribute("title", "Trading Dashboard");
        return "dashboard";
    }

    @GetMapping("/trading-dashboard")
    public String tradingDashboard(Model model) {
        model.addAttribute("title", "Trading Dashboard");
        return "trading-dashboard";
    }

    @GetMapping("/stats-dashboard")
    public String statsDashboard(Model model) {
        model.addAttribute("title", "Statistics Dashboard");
        return "stats-dashboard";
    }

    @GetMapping("/bots")
    public String bots(Model model) {
        model.addAttribute("title", "Trading Bots");
        return "bots";
    }

    @GetMapping("/trade")
    public String trade(Model model) {
        model.addAttribute("title", "Trade");
        return "trade";
    }

    @GetMapping("/bot-settings")
    public String botSettings(Model model) {
        model.addAttribute("title", "Bot Settings");
        return "bot-settings";
    }

    @GetMapping("/bot-strategies")
    public String botStrategies(Model model) {
        model.addAttribute("title", "Bot Strategies");
        return "bot-strategies";
    }

    @GetMapping("/exchange-settings")
    public String exchangeSettings(Model model) {
        model.addAttribute("title", "Exchange Settings");
        return "exchange-settings";
    }

    @GetMapping("/user-settings")
    public String userSettings(Model model) {
        model.addAttribute("title", "User Settings");
        return "user-settings";
    }

    @GetMapping("/test-currencies")
    public String testCurrencies(Model model) {
        model.addAttribute("title", "Test Currencies");
        return "test-currencies";
    }

    @GetMapping("/bot-control")
    public String botControl(Model model) {
        model.addAttribute("title", "Bot Control");
        return "bot-control";
    }

    @GetMapping("/about")
    public String about(Model model) {
        model.addAttribute("title", "About");
        return "about";
    }

    @GetMapping("/impressum")
    public String impressum(Model model) {
        model.addAttribute("title", "Impressum");
        return "impressum";
    }

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    @GetMapping("/create-account")
    public String createAccount() {
        return "create-account";
    }

    @GetMapping("/forgot-password")
    public String forgotPassword() {
        return "forgot-password";
    }
}
