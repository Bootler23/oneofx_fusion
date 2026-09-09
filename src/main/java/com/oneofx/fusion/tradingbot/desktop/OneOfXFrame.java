package com.oneofx.fusion.tradingbot.desktop;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.LayoutManager;
import java.awt.RenderingHints;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFormattedTextField;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.ScrollPaneConstants;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.SwingWorker;
import javax.swing.Timer;

import com.oneofx.fusion.client.FusionApiClient;
import com.oneofx.fusion.client.model.TradingPair;
import com.oneofx.fusion.tradingbot.Database.PortablePaths;
import com.oneofx.fusion.tradingbot.Database.dbUrl;
import com.oneofx.fusion.tradingbot.Settings.FusionClientProvider;
import com.oneofx.fusion.tradingbot.desktop.TradingEngineController.State;
import com.oneofx.fusion.tradingbot.grid.GridMode;

/** Lokale OneOfX-Bedienoberfläche im dunklen Trading-Design. */
public final class OneOfXFrame extends JFrame {

    private static final String PAGE_DASHBOARD = "dashboard";
    private static final String PAGE_SETTINGS = "settings";
    private static final String PAGE_ACCESS = "access";

    private final CurrencySettingsRepository repository = new CurrencySettingsRepository();
    private final TradingPairValidator pairValidator = new TradingPairValidator();
    private final TradingEngineController engine = new TradingEngineController();

    private final JLabel pageTitle = new JLabel("Dashboard");
    private final JLabel engineStatus = new JLabel("Gestoppt");
    private final JLabel engineStatusDot = new JLabel("●");
    private final RoundedPanel engineStatusChip =
            new RoundedPanel(new FlowLayout(FlowLayout.CENTER, 8, 0), 18);
    private final JLabel databasePath = new JLabel();
    private final JLabel currencyCount = metricValue();
    private final JLabel enabledCount = metricValue();
    private final JLabel pendingCount = metricValue();
    private final JLabel positionCount = metricValue();
    private final JLabel capitalAmount = metricValue();
    private final JLabel message = new JLabel("Bereit");

    private final JButton startButton = new JButton("Trading starten");
    private final JButton stopButton = new JButton("Stoppen");

    private final JComboBox<String> currencyBox = new JComboBox<>();
    private final JCheckBox buyEnabled = new JCheckBox("Neue Käufe erlauben");
    private final JSpinner buyAmount = decimalSpinner(10.0, 0.01, 1_000_000.0, 1.0);
    private final JSpinner maxBuyAmount =
            decimalSpinner(500.0, 0.01, 100_000_000.0, 10.0);
    private final JComboBox<GridMode> gridMode = new JComboBox<>(GridMode.values());
    private final JSpinner gridSpacing =
            decimalSpinner(1.0 / 23.0, 0.00000001, 1_000_000.0, 0.1);
    private final JLabel gridSpacingUnit = new JLabel("%");
    private final JSpinner stopLoss = decimalSpinner(2.0, 0.0, 99.99, 0.1);
    private final JCheckBox trailingStop = new JCheckBox("Trailing Stop aktiv");
    private final JSpinner trailingActivation =
            decimalSpinner(2.5, 0.0, 99.99, 0.1);
    private final JSpinner trailingDecline =
            decimalSpinner(0.8, 0.0, 99.99, 0.1);

    private final JPasswordField apiKey = new JPasswordField(28);
    private final JTextField baseUrl =
            new JTextField(FusionApiClient.DEFAULT_BASE_URL, 28);

    private final CardLayout pageLayout = new CardLayout();
    private final JPanel pages = new JPanel(pageLayout);
    private final Map<String, NavButton> navigation = new LinkedHashMap<>();
    private final Timer dashboardTimer;
    private final Timer autoSaveTimer;
    private boolean loadingSettings;
    private boolean autoSaveDirty;
    private String loadedCurrency;

    public OneOfXFrame() {
        super("OneOfX Trading Bot");
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        setMinimumSize(new Dimension(1040, 700));
        setSize(1240, 780);
        setLocationRelativeTo(null);

        getRootPane().putClientProperty("JRootPane.titleBarBackground", OneOfXTheme.SIDEBAR);
        getRootPane().putClientProperty("JRootPane.titleBarForeground", OneOfXTheme.TEXT);

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(OneOfXTheme.BACKGROUND);
        setContentPane(root);

        root.add(createSidebar(), BorderLayout.WEST);
        root.add(createWorkspace(), BorderLayout.CENTER);

        autoSaveTimer = new Timer(700, event -> savePendingSettings());
        autoSaveTimer.setRepeats(false);

        startButton.addActionListener(event -> startTrading());
        stopButton.addActionListener(event -> engine.stop(this::showEngineState));
        currencyBox.addActionListener(event -> switchSelectedCurrency());
        installAutoSaveListeners();

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent event) {
                closeApplication();
            }
        });

        reloadCurrencies(null);
        refreshDashboard();
        showEngineState(State.STOPPED);
        showPage(PAGE_DASHBOARD, "Dashboard");

        dashboardTimer = new Timer(2_000, event -> refreshDashboard());
        dashboardTimer.start();
    }

    private JPanel createSidebar() {
        JPanel sidebar = new JPanel();
        sidebar.setBackground(OneOfXTheme.SIDEBAR);
        sidebar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 0, 1, OneOfXTheme.BORDER),
                OneOfXTheme.padding(0, 8, 0, 8)));
        sidebar.setLayout(new BoxLayout(sidebar, BoxLayout.Y_AXIS));
        sidebar.setPreferredSize(new Dimension(224, 0));

        JPanel brand = new JPanel();
        brand.setOpaque(false);
        brand.setLayout(new BoxLayout(brand, BoxLayout.Y_AXIS));
        brand.setBorder(OneOfXTheme.padding(27, 18, 27, 12));
        brand.setAlignmentX(Component.CENTER_ALIGNMENT);
        brand.setMaximumSize(new Dimension(Integer.MAX_VALUE, 112));

        JPanel brandLine = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        brandLine.setOpaque(false);
        brandLine.setAlignmentX(Component.LEFT_ALIGNMENT);
        brandLine.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        JLabel name = new JLabel("oneofx");
        name.setForeground(OneOfXTheme.PRIMARY);
        name.setFont(OneOfXTheme.font(Font.BOLD, 25));
        JLabel edition = new JLabel(".fusion");
        edition.setForeground(OneOfXTheme.TEXT);
        edition.setFont(OneOfXTheme.font(Font.BOLD, 25));
        brandLine.add(name);
        brandLine.add(edition);

        JLabel subtitle = new JLabel("AUTOMATED TRADING");
        subtitle.setForeground(OneOfXTheme.TEXT_MUTED);
        subtitle.setFont(OneOfXTheme.font(Font.BOLD, 10));
        subtitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        brand.add(brandLine);
        brand.add(Box.createVerticalStrut(5));
        brand.add(subtitle);
        sidebar.add(brand);

        JLabel navLabel = new JLabel("NAVIGATION");
        navLabel.setForeground(OneOfXTheme.TEXT_MUTED);
        navLabel.setFont(OneOfXTheme.font(Font.BOLD, 10));
        navLabel.setBorder(OneOfXTheme.padding(8, 19, 10, 0));
        navLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        navLabel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
        sidebar.add(navLabel);

        sidebar.add(createNavButton(PAGE_DASHBOARD, "Dashboard", IconType.DASHBOARD));
        sidebar.add(Box.createVerticalStrut(6));
        sidebar.add(createNavButton(PAGE_SETTINGS, "Einstellungen", IconType.SETTINGS));
        sidebar.add(Box.createVerticalStrut(6));
        sidebar.add(createNavButton(PAGE_ACCESS, "API-Zugang", IconType.KEY));
        sidebar.add(Box.createVerticalGlue());

        RoundedPanel localCard = new RoundedPanel(new BorderLayout(10, 0), 14);
        localCard.setFill(OneOfXTheme.SURFACE);
        localCard.setBorder(OneOfXTheme.padding(12, 14, 12, 14));
        localCard.setMaximumSize(new Dimension(184, 64));
        localCard.setPreferredSize(new Dimension(184, 64));
        localCard.setAlignmentX(Component.CENTER_ALIGNMENT);
        JLabel localDot = new JLabel("●");
        localDot.setForeground(OneOfXTheme.SUCCESS);
        localDot.setFont(OneOfXTheme.font(Font.BOLD, 15));
        JPanel localText = new JPanel();
        localText.setOpaque(false);
        localText.setLayout(new BoxLayout(localText, BoxLayout.Y_AXIS));
        JLabel mode = new JLabel("Lokaler Modus");
        mode.setForeground(OneOfXTheme.TEXT);
        mode.setFont(OneOfXTheme.font(Font.BOLD, 12));
        JLabel storage = new JLabel("Portable SQLite");
        storage.setForeground(OneOfXTheme.TEXT_MUTED);
        storage.setFont(OneOfXTheme.font(Font.PLAIN, 10));
        localText.add(mode);
        localText.add(storage);
        localCard.add(localDot, BorderLayout.WEST);
        localCard.add(localText, BorderLayout.CENTER);
        sidebar.add(localCard);
        sidebar.add(Box.createVerticalStrut(22));
        return sidebar;
    }

    private JPanel createWorkspace() {
        JPanel workspace = new JPanel(new BorderLayout());
        workspace.setBackground(OneOfXTheme.BACKGROUND);

        pages.setOpaque(false);
        pages.add(createDashboard(), PAGE_DASHBOARD);
        pages.add(createSettings(), PAGE_SETTINGS);
        pages.add(createAccessPanel(), PAGE_ACCESS);

        workspace.add(createTopBar(), BorderLayout.NORTH);
        workspace.add(pages, BorderLayout.CENTER);
        workspace.add(createStatusBar(), BorderLayout.SOUTH);
        return workspace;
    }

    private JPanel createTopBar() {
        JPanel panel = new JPanel(new BorderLayout(18, 0));
        panel.setBackground(OneOfXTheme.BACKGROUND);
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, OneOfXTheme.BORDER),
                OneOfXTheme.padding(15, 28, 15, 28)));
        panel.setPreferredSize(new Dimension(0, 76));

        pageTitle.setFont(OneOfXTheme.font(Font.BOLD, 20));
        pageTitle.setForeground(OneOfXTheme.TEXT);
        panel.add(pageTitle, BorderLayout.WEST);

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        controls.setOpaque(false);

        engineStatusChip.setFill(OneOfXTheme.ERROR_SOFT);
        engineStatusChip.setBorder(OneOfXTheme.padding(10, 13, 10, 13));
        engineStatusDot.setForeground(OneOfXTheme.ERROR);
        engineStatusDot.setFont(OneOfXTheme.font(Font.BOLD, 12));
        engineStatus.setFont(OneOfXTheme.font(Font.BOLD, 12));
        engineStatus.setForeground(OneOfXTheme.ERROR);
        engineStatusChip.add(engineStatusDot);
        engineStatusChip.add(engineStatus);
        controls.add(engineStatusChip);

        styleSecondaryButton(stopButton);
        stopButton.setForeground(OneOfXTheme.ERROR);
        stopButton.setToolTipText("Trading-Engine kontrolliert stoppen");
        controls.add(stopButton);

        stylePrimaryButton(startButton);
        startButton.setIcon(new LineIcon(IconType.PLAY, 15));
        startButton.setToolTipText("Live-Trading nach Sicherheitsabfrage starten");
        controls.add(startButton);

        panel.add(controls, BorderLayout.EAST);
        return panel;
    }

    private JScrollPane createDashboard() {
        JPanel content = pageContent();
        content.add(pageHeading(
                "Guten Überblick.",
                "Alle wichtigen Kennzahlen deiner Trading-Engine auf einen Blick."));
        content.add(Box.createVerticalStrut(22));

        JPanel metrics = new JPanel(new GridLayout(1, 5, 12, 0));
        metrics.setOpaque(false);
        metrics.setAlignmentX(Component.LEFT_ALIGNMENT);
        metrics.setMaximumSize(new Dimension(Integer.MAX_VALUE, 142));
        metrics.add(metricCard("HANDELSPAARE", currencyCount, IconType.PAIRS,
                OneOfXTheme.PRIMARY, "Konfiguriert"));
        metrics.add(metricCard("KAUF AKTIV", enabledCount, IconType.PLAY,
                OneOfXTheme.SUCCESS, "Freigegeben"));
        metrics.add(metricCard("KAUFORDERS", pendingCount, IconType.CLOCK,
                OneOfXTheme.PRIMARY_HOVER, "Noch offen"));
        metrics.add(metricCard("POSITIONEN", positionCount, IconType.CHART,
                OneOfXTheme.INFO, "Überwacht"));
        metrics.add(metricCard("KAPITAL", capitalAmount, IconType.WALLET,
                OneOfXTheme.SUCCESS, "Gebunden"));
        content.add(metrics);
        content.add(Box.createVerticalStrut(20));

        JPanel detailRow = new JPanel(new GridLayout(1, 2, 16, 0));
        detailRow.setOpaque(false);
        detailRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        detailRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 260));
        detailRow.add(createEngineCard());
        detailRow.add(createSafetyCard());
        content.add(detailRow);
        content.add(Box.createVerticalGlue());
        return scroll(content);
    }

    private RoundedPanel createEngineCard() {
        RoundedPanel card = cardPanel(new BorderLayout(0, 16));
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        JPanel title = titleBlock("Trading Engine", "Live-Steuerung und Datenstatus");
        header.add(title, BorderLayout.WEST);
        JLabel live = pill("SYSTEMBEREIT", OneOfXTheme.SUCCESS, OneOfXTheme.SUCCESS_SOFT);
        header.add(live, BorderLayout.EAST);
        card.add(header, BorderLayout.NORTH);

        JPanel rows = new JPanel();
        rows.setOpaque(false);
        rows.setLayout(new BoxLayout(rows, BoxLayout.Y_AXIS));
        rows.add(infoRow("Ausführung", "Bitpanda Fusion API"));
        rows.add(divider());
        rows.add(infoRow("Marktdaten", "REST-Polling"));
        rows.add(divider());
        rows.add(infoRow("Speicher", "Lokale SQLite-Datenbank"));
        rows.add(divider());
        rows.add(infoRow("Aktualisierung", "Alle 2 Sekunden"));
        card.add(rows, BorderLayout.CENTER);
        return card;
    }

    private RoundedPanel createSafetyCard() {
        RoundedPanel card = cardPanel(new BorderLayout(0, 16));
        JPanel heading = new JPanel(new BorderLayout(12, 0));
        heading.setOpaque(false);
        RoundedPanel icon = iconTile(IconType.SHIELD, OneOfXTheme.PRIMARY,
                OneOfXTheme.PRIMARY_SOFT);
        heading.add(icon, BorderLayout.WEST);
        heading.add(titleBlock("Live-Trading mit Kontrolle",
                "Vor jedem Start erfolgt eine Sicherheitsabfrage."), BorderLayout.CENTER);
        card.add(heading, BorderLayout.NORTH);

        JPanel text = new JPanel();
        text.setOpaque(false);
        text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
        text.add(bullet("API-Key, Handelspaare und Risikowerte prüfen"));
        text.add(Box.createVerticalStrut(10));
        text.add(bullet("MACD- und Stop-Loss-Regeln können Verkäufe auslösen"));
        text.add(Box.createVerticalStrut(10));
        text.add(bullet("Zugangsschlüssel wird nicht in SQLite gespeichert"));
        card.add(text, BorderLayout.CENTER);
        return card;
    }

    private JScrollPane createSettings() {
        JPanel content = pageContent();
        content.add(pageHeading(
                "Strategie konfigurieren",
                "Kapital, Grid und Risikoschutz für jedes Handelspaar."));
        content.add(Box.createVerticalStrut(22));

        RoundedPanel selection = cardPanel(new BorderLayout(16, 0));
        selection.setMaximumSize(new Dimension(Integer.MAX_VALUE, 96));
        JPanel selectText = titleBlock("Handelspaar",
                "Geprüfte Paare verwalten · Änderungen speichern automatisch");
        selection.add(selectText, BorderLayout.WEST);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        actions.setOpaque(false);
        currencyBox.setPreferredSize(new Dimension(190, 40));
        OneOfXTheme.round(currencyBox);
        actions.add(currencyBox);

        JButton addButton = new JButton("Paar hinzufügen");
        styleSecondaryButton(addButton);
        addButton.setForeground(OneOfXTheme.PRIMARY);
        addButton.addActionListener(event -> addCurrency(addButton));
        actions.add(addButton);

        JButton removeButton = new JButton("Entfernen");
        styleSecondaryButton(removeButton);
        removeButton.setForeground(OneOfXTheme.ERROR);
        removeButton.addActionListener(event -> removeCurrency());
        actions.add(removeButton);
        selection.add(actions, BorderLayout.EAST);
        content.add(selection);
        content.add(Box.createVerticalStrut(16));

        JPanel forms = new JPanel(new GridLayout(1, 2, 16, 0));
        forms.setOpaque(false);
        forms.setAlignmentX(Component.LEFT_ALIGNMENT);
        forms.setMaximumSize(new Dimension(Integer.MAX_VALUE, 390));
        forms.add(strategyCard());
        forms.add(riskCard());
        content.add(forms);
        content.add(Box.createVerticalStrut(16));

        JPanel saveRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        saveRow.setOpaque(false);
        saveRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        saveRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 46));
        JLabel autoSaveHint = new JLabel("Automatische Speicherung aktiv");
        autoSaveHint.setIcon(new LineIcon(IconType.CHECK, 13, OneOfXTheme.SUCCESS));
        autoSaveHint.setForeground(OneOfXTheme.TEXT_MUTED);
        autoSaveHint.setFont(OneOfXTheme.font(Font.PLAIN, 11));
        saveRow.add(autoSaveHint);
        content.add(saveRow);
        content.add(Box.createVerticalGlue());
        return scroll(content);
    }

    private RoundedPanel strategyCard() {
        RoundedPanel card = cardPanel(new BorderLayout(0, 18));
        card.add(titleBlock("Kaufstrategie", "Ordergröße und Grid-Steuerung"),
                BorderLayout.NORTH);
        JPanel form = modernForm();
        addFormRow(form, 0, "Kaufstatus", buyEnabled, null);
        addFormRow(form, 1, "Betrag je Order", buyAmount, "EUR");
        addFormRow(form, 2, "Kapitalgrenze", maxBuyAmount, "EUR");
        addFormRow(form, 3, "Grid-Art", gridMode, null);
        addFormRowWithSuffix(form, 4, "Grid-Abstand", gridSpacing, gridSpacingUnit);
        card.add(form, BorderLayout.CENTER);
        return card;
    }

    private RoundedPanel riskCard() {
        RoundedPanel card = cardPanel(new BorderLayout(0, 18));
        card.add(titleBlock("Risikomanagement", "Stop-Loss und Trailing-Schutz"),
                BorderLayout.NORTH);
        JPanel form = modernForm();
        addFormRow(form, 0, "Harter Stop-Loss", stopLoss, "%");
        addFormRow(form, 1, "Trailing Stop", trailingStop, null);
        addFormRow(form, 2, "Aktivierung", trailingActivation, "%");
        addFormRow(form, 3, "Abstand vom Hoch", trailingDecline, "%");
        card.add(form, BorderLayout.CENTER);
        return card;
    }

    private JScrollPane createAccessPanel() {
        JPanel content = pageContent();
        content.add(pageHeading(
                "Sicher verbinden",
                "Bitpanda Fusion-Zugang für diese Programmsitzung hinterlegen."));
        content.add(Box.createVerticalStrut(22));

        RoundedPanel card = cardPanel(new BorderLayout(28, 0));
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 300));

        JPanel formColumn = new JPanel();
        formColumn.setOpaque(false);
        formColumn.setLayout(new BoxLayout(formColumn, BoxLayout.Y_AXIS));
        formColumn.add(fieldLabel("BITPANDA FUSION API-KEY"));
        formColumn.add(Box.createVerticalStrut(8));
        apiKey.putClientProperty("JTextField.placeholderText", "API-Key eingeben");
        apiKey.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
        apiKey.setAlignmentX(Component.LEFT_ALIGNMENT);
        OneOfXTheme.round(apiKey);
        formColumn.add(apiKey);
        formColumn.add(Box.createVerticalStrut(20));
        formColumn.add(fieldLabel("API-BASIS-URL"));
        formColumn.add(Box.createVerticalStrut(8));
        baseUrl.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
        baseUrl.setAlignmentX(Component.LEFT_ALIGNMENT);
        OneOfXTheme.round(baseUrl);
        formColumn.add(baseUrl);
        card.add(formColumn, BorderLayout.CENTER);

        RoundedPanel privacy = new RoundedPanel(new BorderLayout(12, 12), 14);
        privacy.setFill(OneOfXTheme.PRIMARY_SOFT);
        privacy.setBorder(OneOfXTheme.padding(18, 18, 18, 18));
        privacy.setPreferredSize(new Dimension(300, 0));
        JPanel privacyIcon = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        privacyIcon.setOpaque(false);
        privacyIcon.add(iconTile(IconType.SHIELD, OneOfXTheme.PRIMARY,
                OneOfXTheme.SURFACE));
        privacy.add(privacyIcon, BorderLayout.NORTH);
        JPanel privacyText = titleBlock("Bleibt auf diesem Gerät",
                "<html>Nur für diese Sitzung.<br>Nicht in SQLite gespeichert.</html>");
        privacy.add(privacyText, BorderLayout.CENTER);
        card.add(privacy, BorderLayout.EAST);
        content.add(card);
        content.add(Box.createVerticalStrut(16));

        RoundedPanel hint = new RoundedPanel(new BorderLayout(14, 0), 14);
        hint.setFill(OneOfXTheme.SURFACE);
        hint.setBorder(OneOfXTheme.padding(16, 18, 16, 18));
        hint.setAlignmentX(Component.LEFT_ALIGNMENT);
        hint.setMaximumSize(new Dimension(Integer.MAX_VALUE, 76));
        JLabel hintIcon = new JLabel("i", SwingConstants.CENTER);
        hintIcon.setOpaque(true);
        hintIcon.setBackground(OneOfXTheme.SURFACE_HOVER);
        hintIcon.setForeground(OneOfXTheme.PRIMARY);
        hintIcon.setFont(OneOfXTheme.font(Font.BOLD, 15));
        hintIcon.setPreferredSize(new Dimension(34, 34));
        hint.add(hintIcon, BorderLayout.WEST);
        JLabel hintText = new JLabel(
                "Nutze zunächst reine Leserechte, bevor du dem Schlüssel Trade-Rechte gibst.");
        hintText.setForeground(OneOfXTheme.TEXT_SECONDARY);
        hint.add(hintText, BorderLayout.CENTER);
        content.add(hint);
        content.add(Box.createVerticalGlue());
        return scroll(content);
    }

    private JPanel createStatusBar() {
        JPanel panel = new JPanel(new BorderLayout(12, 0));
        panel.setBackground(OneOfXTheme.SIDEBAR);
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, OneOfXTheme.BORDER),
                OneOfXTheme.padding(8, 18, 8, 18)));

        message.setForeground(OneOfXTheme.TEXT_MUTED);
        message.setFont(OneOfXTheme.font(Font.PLAIN, 11));
        databasePath.setText(dbUrl.getoneOfX().replace("jdbc:sqlite:", "SQLite · "));
        databasePath.setForeground(OneOfXTheme.TEXT_MUTED);
        databasePath.setFont(OneOfXTheme.font(Font.PLAIN, 11));
        panel.add(message, BorderLayout.WEST);
        panel.add(databasePath, BorderLayout.EAST);
        return panel;
    }

    private NavButton createNavButton(String page, String label, IconType iconType) {
        NavButton button = new NavButton(label, new LineIcon(iconType, 18));
        button.setAlignmentX(Component.CENTER_ALIGNMENT);
        button.addActionListener(event -> showPage(page, label));
        navigation.put(page, button);
        return button;
    }

    private void showPage(String page, String title) {
        pageLayout.show(pages, page);
        pageTitle.setText(title);
        navigation.forEach((key, button) -> button.setActive(key.equals(page)));
    }

    private void reloadCurrencies(String selectAfterReload) {
        loadingSettings = true;
        try {
            List<CurrencySettings> settings = repository.loadAll();
            currencyBox.removeAllItems();
            for (CurrencySettings entry : settings) {
                currencyBox.addItem(entry.currency());
            }
            if (selectAfterReload != null) {
                currencyBox.setSelectedItem(selectAfterReload);
            }
            boolean available = currencyBox.getItemCount() > 0;
            setSettingsEnabled(available);
            if (available && currencyBox.getSelectedIndex() < 0) {
                currencyBox.setSelectedIndex(0);
            }
            loadedCurrency = null;
        } catch (SQLException | RuntimeException ex) {
            showError("Handelspaare konnten nicht geladen werden", ex);
        } finally {
            loadingSettings = false;
        }
        if (currencyBox.getItemCount() > 0) {
            loadSelectedCurrency();
        } else {
            loadedCurrency = null;
            autoSaveDirty = false;
        }
    }

    private void switchSelectedCurrency() {
        if (loadingSettings) return;
        String selected = selectedCurrency();
        if (java.util.Objects.equals(selected, loadedCurrency)) return;
        if (!flushAutoSave()) {
            loadingSettings = true;
            currencyBox.setSelectedItem(loadedCurrency);
            loadingSettings = false;
            return;
        }
        loadSelectedCurrency();
    }

    private void loadSelectedCurrency() {
        String currency = selectedCurrency();
        if (currency == null) {
            return;
        }
        try {
            CurrencySettings settings = repository.load(currency);
            loadingSettings = true;
            buyEnabled.setSelected(settings.buyEnabled());
            buyAmount.setValue(settings.buyAmount());
            maxBuyAmount.setValue(settings.maxBuyAmount());
            gridMode.setSelectedItem(settings.gridMode());
            gridSpacing.setValue(settings.gridSpacing());
            stopLoss.setValue(settings.stopLoss());
            trailingStop.setSelected(settings.trailingStopEnabled());
            trailingActivation.setValue(settings.trailingStopActivation());
            trailingDecline.setValue(settings.trailingStopDecline());
            updateTrailingFields();
            updateGridUnit();
            loadedCurrency = currency;
            autoSaveDirty = false;
            setMessage("Einstellungen für " + currency + " geladen.", false);
        } catch (SQLException | RuntimeException ex) {
            showError("Einstellungen konnten nicht geladen werden", ex);
        } finally {
            loadingSettings = false;
        }
    }

    private boolean savePendingSettings() {
        if (!autoSaveDirty || loadedCurrency == null) return true;
        try {
            repository.save(readForm(loadedCurrency));
            autoSaveDirty = false;
            setMessage("Einstellungen für " + loadedCurrency
                    + " automatisch gespeichert.", false);
            refreshDashboard();
            return true;
        } catch (SQLException | IllegalArgumentException ex) {
            setMessage("Automatisches Speichern fehlgeschlagen: " + ex.getMessage(), true);
            return false;
        }
    }

    private boolean flushAutoSave() {
        autoSaveTimer.stop();
        return savePendingSettings();
    }

    private void addCurrency(JButton addButton) {
        if (!flushAutoSave()) return;
        String input = JOptionPane.showInputDialog(this,
                "Handelspaar eingeben, zum Beispiel BTCEUR:",
                "Handelspaar hinzufügen", JOptionPane.PLAIN_MESSAGE);
        if (input == null || input.isBlank()) {
            return;
        }
        try {
            configureSessionFromFields();
            if (!FusionClientProvider.isConfigured()) {
                showPage(PAGE_ACCESS, "API-Zugang");
                throw new IllegalStateException(
                        "Für die Paarprüfung zuerst einen Fusion API-Key eingeben.");
            }
            CurrencySettings settings = CurrencySettings.defaults(input);
            addButton.setEnabled(false);
            setMessage(settings.currency() + " wird bei Bitpanda Fusion geprüft …", false);
            new SwingWorker<TradingPair, Void>() {
                @Override
                protected TradingPair doInBackground() {
                    return pairValidator.validate(input, FusionClientProvider.getClient());
                }

                @Override
                protected void done() {
                    addButton.setEnabled(true);
                    try {
                        TradingPair pair = get();
                        repository.addVerified(settings, pair);
                        reloadCurrencies(settings.currency());
                        setMessage(settings.currency()
                                + " wurde geprüft und deaktiviert hinzugefügt.", false);
                        refreshDashboard();
                    } catch (Exception ex) {
                        Throwable cause = ex.getCause() == null ? ex : ex.getCause();
                        showError("Handelspaar konnte nicht hinzugefügt werden",
                                cause instanceof Exception exception
                                        ? exception : new RuntimeException(cause));
                    }
                }
            }.execute();
        } catch (IllegalArgumentException | IllegalStateException ex) {
            showError("Handelspaar konnte nicht hinzugefügt werden", ex);
        }
    }

    private void removeCurrency() {
        String currency = selectedCurrency();
        if (currency == null || !flushAutoSave()) return;
        int answer = JOptionPane.showConfirmDialog(this,
                currency + " aus der Konfiguration entfernen?\n"
                        + "Vorhandene Positionsdaten bleiben sicher erhalten.",
                "Handelspaar entfernen", JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);
        if (answer != JOptionPane.YES_OPTION) return;
        try {
            CurrencySettingsRepository.RemovalResult result = repository.remove(currency);
            reloadCurrencies(null);
            refreshDashboard();
            if (result.archived()) {
                setMessage(currency + " wurde deaktiviert und wegen vorhandener "
                        + "Order-/Positionsdaten archiviert.", false);
            } else {
                setMessage(currency + " wurde gelöscht.", false);
            }
        } catch (SQLException | RuntimeException ex) {
            showError("Handelspaar konnte nicht entfernt werden", ex);
        }
    }

    private CurrencySettings readForm(String currency) {
        commitSpinner(buyAmount);
        commitSpinner(maxBuyAmount);
        commitSpinner(gridSpacing);
        commitSpinner(stopLoss);
        commitSpinner(trailingActivation);
        commitSpinner(trailingDecline);
        return new CurrencySettings(
                currency,
                buyEnabled.isSelected(),
                number(buyAmount),
                number(maxBuyAmount),
                (GridMode) gridMode.getSelectedItem(),
                number(gridSpacing),
                number(stopLoss),
                trailingStop.isSelected(),
                number(trailingActivation),
                number(trailingDecline));
    }

    private void startTrading() {
        try {
            if (!flushAutoSave()) return;
            configureSessionFromFields();
            if (!FusionClientProvider.isConfigured()) {
                throw new IllegalStateException(
                        "Bitte zuerst einen API-Key unter „API-Zugang“ eingeben.");
            }
            if (repository.loadDashboardStats().enabledCurrencies() == 0) {
                throw new IllegalStateException(
                        "Bitte zuerst mindestens ein Handelspaar aktivieren.");
            }
            int answer = JOptionPane.showConfirmDialog(this,
                    "Die echte Trading-Engine wird gestartet und kann Orders ausführen.\n"
                            + "Sind API-Key, Positionen und Risikowerte geprüft?",
                    "Live-Trading starten", JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE);
            if (answer != JOptionPane.YES_OPTION) {
                return;
            }
            engine.start(this::showEngineState);
        } catch (SQLException | RuntimeException ex) {
            showError("Trading konnte nicht gestartet werden", ex);
        }
    }

    private void showEngineState(State state) {
        switch (state) {
            case STARTING -> setEngineStatus("Startet …", OneOfXTheme.PRIMARY,
                    OneOfXTheme.PRIMARY_SOFT);
            case RUNNING -> setEngineStatus("Trading aktiv", OneOfXTheme.SUCCESS,
                    OneOfXTheme.SUCCESS_SOFT);
            case STOPPING -> setEngineStatus("Stoppt …", OneOfXTheme.ERROR,
                    OneOfXTheme.ERROR_SOFT);
            case FAILED -> setEngineStatus("Fehler", OneOfXTheme.ERROR,
                    OneOfXTheme.ERROR_SOFT);
            case STOPPED -> setEngineStatus("Gestoppt", OneOfXTheme.ERROR,
                    OneOfXTheme.ERROR_SOFT);
        }
        startButton.setEnabled(state == State.STOPPED && engine.canStart());
        stopButton.setEnabled(state == State.RUNNING || state == State.STARTING);
    }

    private void setEngineStatus(String text, Color color, Color background) {
        engineStatus.setText(text);
        engineStatus.setForeground(color);
        engineStatusDot.setForeground(color);
        engineStatusChip.setFill(background);
        engineStatusChip.repaint();
    }

    private void refreshDashboard() {
        try {
            DashboardStats stats = repository.loadDashboardStats();
            currencyCount.setText(Integer.toString(stats.currencies()));
            enabledCount.setText(Integer.toString(stats.enabledCurrencies()));
            pendingCount.setText(Integer.toString(stats.pendingBuyOrders()));
            positionCount.setText(Integer.toString(stats.openPositions()));
            capitalAmount.setText(String.format("%.2f €", stats.committedCapital()));
        } catch (SQLException ex) {
            setMessage("Dashboard konnte nicht aktualisiert werden: " + ex.getMessage(), true);
        }
    }

    private void updateTrailingFields() {
        trailingActivation.setEnabled(trailingStop.isSelected());
        trailingDecline.setEnabled(trailingStop.isSelected());
    }

    private void updateGridUnit() {
        GridMode mode = (GridMode) gridMode.getSelectedItem();
        gridSpacingUnit.setText(mode == GridMode.ARITHMETIC ? "Preis" : "%");
        SpinnerNumberModel model = (SpinnerNumberModel) gridSpacing.getModel();
        double maximum = mode == GridMode.GEOMETRIC ? 99.999999 : 1_000_000.0;
        if (number(gridSpacing) > maximum) gridSpacing.setValue(1.0);
        model.setMaximum(maximum);
    }

    private void installAutoSaveListeners() {
        buyEnabled.addActionListener(event -> scheduleAutoSave());
        trailingStop.addActionListener(event -> {
            updateTrailingFields();
            scheduleAutoSave();
        });
        gridMode.addActionListener(event -> {
            updateGridUnit();
            scheduleAutoSave();
        });
        for (JSpinner spinner : List.of(buyAmount, maxBuyAmount, gridSpacing,
                stopLoss, trailingActivation, trailingDecline)) {
            spinner.addChangeListener(event -> scheduleAutoSave());
        }
    }

    private void scheduleAutoSave() {
        if (loadingSettings || loadedCurrency == null) return;
        autoSaveDirty = true;
        setMessage("Änderungen an " + loadedCurrency + " werden gespeichert …", false);
        autoSaveTimer.restart();
    }

    private void configureSessionFromFields() {
        char[] enteredKey = apiKey.getPassword();
        if (enteredKey.length == 0) return;
        try {
            FusionClientProvider.configureSession(new String(enteredKey), baseUrl.getText());
        } finally {
            java.util.Arrays.fill(enteredKey, '\0');
            apiKey.setText("");
        }
    }

    private void setSettingsEnabled(boolean enabled) {
        buyEnabled.setEnabled(enabled);
        buyAmount.setEnabled(enabled);
        maxBuyAmount.setEnabled(enabled);
        gridMode.setEnabled(enabled);
        gridSpacing.setEnabled(enabled);
        stopLoss.setEnabled(enabled);
        trailingStop.setEnabled(enabled);
        trailingActivation.setEnabled(enabled && trailingStop.isSelected());
        trailingDecline.setEnabled(enabled && trailingStop.isSelected());
    }

    private void closeApplication() {
        if (!flushAutoSave()) {
            JOptionPane.showMessageDialog(this,
                    "Die aktuellen Einstellungen sind ungültig und konnten nicht "
                            + "gespeichert werden. Bitte korrigiere die markierten Werte.",
                    "Speichern nicht möglich", JOptionPane.ERROR_MESSAGE);
            return;
        }
        if (engine.getState() == State.RUNNING || engine.getState() == State.STARTING) {
            int answer = JOptionPane.showConfirmDialog(this,
                    "Trading läuft noch. Soll OneOfX kontrolliert gestoppt werden?",
                    "OneOfX beenden", JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE);
            if (answer != JOptionPane.YES_OPTION) {
                return;
            }
            engine.stop(this::showEngineState);
        }
        autoSaveTimer.stop();
        dashboardTimer.stop();
        dispose();
    }

    private String selectedCurrency() {
        Object selected = currencyBox.getSelectedItem();
        return selected == null ? null : selected.toString();
    }

    private void showError(String title, Exception ex) {
        setMessage(title + ": " + ex.getMessage(), true);
        JOptionPane.showMessageDialog(this, ex.getMessage(), title,
                JOptionPane.ERROR_MESSAGE);
    }

    private void setMessage(String text, boolean error) {
        message.setText(text);
        message.setForeground(error ? OneOfXTheme.ERROR : OneOfXTheme.TEXT_MUTED);
    }

    private static JPanel pageContent() {
        JPanel content = new JPanel();
        content.setBackground(OneOfXTheme.BACKGROUND);
        content.setBorder(OneOfXTheme.padding(28, 30, 28, 30));
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        return content;
    }

    private static JScrollPane scroll(JPanel content) {
        JScrollPane scroll = new JScrollPane(content,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(null);
        scroll.getViewport().setBackground(OneOfXTheme.BACKGROUND);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        return scroll;
    }

    private static JPanel pageHeading(String titleText, String subtitleText) {
        JPanel panel = titleBlock(titleText, subtitleText);
        JLabel title = (JLabel) panel.getComponent(0);
        title.setFont(OneOfXTheme.font(Font.BOLD, 27));
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 62));
        return panel;
    }

    private static JPanel titleBlock(String titleText, String subtitleText) {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

        JLabel title = new JLabel(titleText);
        title.setForeground(OneOfXTheme.TEXT);
        title.setFont(OneOfXTheme.font(Font.BOLD, 16));
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(title);
        panel.add(Box.createVerticalStrut(4));

        JLabel subtitle = new JLabel(subtitleText);
        subtitle.setForeground(OneOfXTheme.TEXT_MUTED);
        subtitle.setFont(OneOfXTheme.font(Font.PLAIN, 12));
        subtitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(subtitle);
        return panel;
    }

    private static JLabel metricValue() {
        JLabel value = new JLabel("–");
        value.setForeground(OneOfXTheme.TEXT);
        value.setFont(OneOfXTheme.font(Font.BOLD, 22));
        return value;
    }

    private static RoundedPanel metricCard(String labelText, JLabel value,
            IconType type, Color accent, String detail) {
        RoundedPanel card = cardPanel(new BorderLayout(0, 12));
        card.setBorder(OneOfXTheme.padding(16, 16, 16, 16));

        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        JLabel label = new JLabel(labelText);
        label.setForeground(OneOfXTheme.TEXT_MUTED);
        label.setFont(OneOfXTheme.font(Font.BOLD, 9));
        top.add(label, BorderLayout.WEST);
        top.add(iconTile(type, accent, colorWithAlpha(accent, 34)), BorderLayout.EAST);
        card.add(top, BorderLayout.NORTH);

        JPanel bottom = new JPanel();
        bottom.setOpaque(false);
        bottom.setLayout(new BoxLayout(bottom, BoxLayout.Y_AXIS));
        value.setAlignmentX(Component.LEFT_ALIGNMENT);
        bottom.add(value);
        bottom.add(Box.createVerticalStrut(4));
        JLabel secondary = new JLabel(detail);
        secondary.setForeground(OneOfXTheme.TEXT_MUTED);
        secondary.setFont(OneOfXTheme.font(Font.PLAIN, 10));
        secondary.setAlignmentX(Component.LEFT_ALIGNMENT);
        bottom.add(secondary);
        card.add(bottom, BorderLayout.CENTER);
        return card;
    }

    private static RoundedPanel cardPanel(LayoutManager layout) {
        RoundedPanel card = new RoundedPanel(layout, 18);
        card.setFill(OneOfXTheme.SURFACE_RAISED);
        card.setOutline(OneOfXTheme.BORDER);
        card.setBorder(OneOfXTheme.padding(20, 20, 20, 20));
        card.setAlignmentX(Component.LEFT_ALIGNMENT);
        return card;
    }

    private static RoundedPanel iconTile(IconType type, Color foreground,
            Color background) {
        RoundedPanel tile = new RoundedPanel(new GridBagLayout(), 12);
        tile.setFill(background);
        tile.setPreferredSize(new Dimension(38, 38));
        tile.setMinimumSize(new Dimension(38, 38));
        tile.add(new JLabel(new LineIcon(type, 17, foreground)));
        return tile;
    }

    private static JLabel pill(String text, Color foreground, Color background) {
        JLabel label = new JLabel(text);
        label.setOpaque(true);
        label.setForeground(foreground);
        label.setBackground(background);
        label.setFont(OneOfXTheme.font(Font.BOLD, 9));
        label.setBorder(OneOfXTheme.padding(7, 10, 7, 10));
        return label;
    }

    private static JPanel infoRow(String labelText, String valueText) {
        JPanel row = new JPanel(new BorderLayout(12, 0));
        row.setOpaque(false);
        row.setBorder(OneOfXTheme.padding(8, 0, 8, 0));
        JLabel label = new JLabel(labelText);
        label.setForeground(OneOfXTheme.TEXT_MUTED);
        label.setFont(OneOfXTheme.font(Font.PLAIN, 12));
        JLabel value = new JLabel(valueText);
        value.setForeground(OneOfXTheme.TEXT);
        value.setFont(OneOfXTheme.font(Font.BOLD, 12));
        row.add(label, BorderLayout.WEST);
        row.add(value, BorderLayout.EAST);
        return row;
    }

    private static JComponent divider() {
        JPanel divider = new JPanel();
        divider.setBackground(OneOfXTheme.BORDER);
        divider.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        divider.setPreferredSize(new Dimension(0, 1));
        return divider;
    }

    private static JPanel bullet(String text) {
        JPanel row = new JPanel(new BorderLayout(10, 0));
        row.setOpaque(false);
        JLabel check = new JLabel(new LineIcon(IconType.CHECK, 12,
                OneOfXTheme.SUCCESS));
        JLabel label = new JLabel(text);
        label.setForeground(OneOfXTheme.TEXT_SECONDARY);
        label.setFont(OneOfXTheme.font(Font.PLAIN, 12));
        row.add(check, BorderLayout.WEST);
        row.add(label, BorderLayout.CENTER);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 22));
        return row;
    }

    private static JPanel modernForm() {
        JPanel form = new JPanel(new GridBagLayout());
        form.setOpaque(false);
        return form;
    }

    private static void addFormRow(JPanel form, int row, String labelText,
            JComponent component, String suffixText) {
        addFormRowWithSuffix(form, row, labelText, component,
                suffixText == null ? null : new JLabel(suffixText));
    }

    private static void addFormRowWithSuffix(JPanel form, int row, String labelText,
            JComponent component, JLabel suffix) {
        component.setPreferredSize(new Dimension(
                Math.max(155, component.getPreferredSize().width), 40));
        OneOfXTheme.round(component);

        JLabel label = new JLabel(labelText);
        label.setForeground(OneOfXTheme.TEXT_SECONDARY);
        label.setFont(OneOfXTheme.font(Font.PLAIN, 12));

        GridBagConstraints left = new GridBagConstraints();
        left.gridx = 0;
        left.gridy = row;
        left.weightx = 1.0;
        left.anchor = GridBagConstraints.LINE_START;
        left.fill = GridBagConstraints.HORIZONTAL;
        left.insets = new Insets(6, 0, 6, 12);
        form.add(label, left);

        JPanel field = new JPanel(new BorderLayout(7, 0));
        field.setOpaque(false);
        field.add(component, BorderLayout.CENTER);
        if (suffix != null) {
            suffix.setForeground(OneOfXTheme.TEXT_MUTED);
            suffix.setFont(OneOfXTheme.font(Font.PLAIN, 11));
            field.add(suffix, BorderLayout.EAST);
        }
        GridBagConstraints right = new GridBagConstraints();
        right.gridx = 1;
        right.gridy = row;
        right.weightx = 0.5;
        right.anchor = GridBagConstraints.LINE_END;
        right.fill = GridBagConstraints.HORIZONTAL;
        right.insets = new Insets(6, 12, 6, 0);
        form.add(field, right);
    }

    private static JLabel fieldLabel(String text) {
        JLabel label = new JLabel(text);
        label.setForeground(OneOfXTheme.TEXT_MUTED);
        label.setFont(OneOfXTheme.font(Font.BOLD, 10));
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        return label;
    }

    private static void stylePrimaryButton(JButton button) {
        button.setBackground(OneOfXTheme.PRIMARY);
        button.setForeground(OneOfXTheme.BACKGROUND);
        button.setFont(OneOfXTheme.font(Font.BOLD, 12));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.putClientProperty("JButton.buttonType", "roundRect");
        button.putClientProperty("JButton.selectedBackground", OneOfXTheme.PRIMARY_HOVER);
    }

    private static void styleSecondaryButton(JButton button) {
        button.setBackground(OneOfXTheme.SURFACE_HOVER);
        button.setForeground(OneOfXTheme.TEXT_SECONDARY);
        button.setFont(OneOfXTheme.font(Font.BOLD, 12));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.putClientProperty("JButton.buttonType", "roundRect");
    }

    private static Color colorWithAlpha(Color color, int alpha) {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha);
    }

    private static JSpinner decimalSpinner(double value, double minimum,
            double maximum, double step) {
        JSpinner spinner = new JSpinner(
                new SpinnerNumberModel(value, minimum, maximum, step));
        spinner.setEditor(new JSpinner.NumberEditor(spinner, "0.########"));
        spinner.setPreferredSize(new Dimension(155, 40));
        return spinner;
    }

    private static double number(JSpinner spinner) {
        return ((Number) spinner.getValue()).doubleValue();
    }

    private static void commitSpinner(JSpinner spinner) {
        try {
            spinner.commitEdit();
        } catch (java.text.ParseException ex) {
            JFormattedTextField field =
                    ((JSpinner.DefaultEditor) spinner.getEditor()).getTextField();
            field.setValue(spinner.getValue());
            throw new IllegalArgumentException("Ungültige Zahl: " + field.getText(), ex);
        }
    }

    private static final class RoundedPanel extends JPanel {
        private final int arc;
        private Color fill = OneOfXTheme.SURFACE_RAISED;
        private Color outline;

        private RoundedPanel(LayoutManager layout, int arc) {
            super(layout);
            this.arc = arc;
            setOpaque(false);
        }

        private void setFill(Color fill) {
            this.fill = fill;
        }

        private void setOutline(Color outline) {
            this.outline = outline;
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g2 = (Graphics2D) graphics.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(fill);
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), arc, arc);
            if (outline != null) {
                g2.setColor(outline);
                g2.setStroke(new BasicStroke(1f));
                g2.drawRoundRect(0, 0, Math.max(0, getWidth() - 1),
                        Math.max(0, getHeight() - 1), arc, arc);
            }
            g2.dispose();
            super.paintComponent(graphics);
        }
    }

    private static final class NavButton extends JButton {
        private boolean active;

        private NavButton(String text, Icon icon) {
            super(text, icon);
            setHorizontalAlignment(SwingConstants.LEFT);
            setIconTextGap(14);
            setBorder(OneOfXTheme.padding(0, 20, 0, 14));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 46));
            setPreferredSize(new Dimension(206, 46));
            setForeground(OneOfXTheme.TEXT_MUTED);
            setBackground(OneOfXTheme.SIDEBAR);
            setFont(OneOfXTheme.font(Font.BOLD, 13));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setFocusPainted(false);
            setContentAreaFilled(false);
        }

        private void setActive(boolean active) {
            this.active = active;
            setForeground(active ? OneOfXTheme.PRIMARY : OneOfXTheme.TEXT_MUTED);
            repaint();
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g2 = (Graphics2D) graphics.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            if (active || getModel().isRollover()) {
                g2.setColor(active ? OneOfXTheme.PRIMARY_SOFT : OneOfXTheme.SURFACE);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
            }
            if (active) {
                g2.setColor(OneOfXTheme.PRIMARY);
                g2.fillRoundRect(0, 10, 3, getHeight() - 20, 3, 3);
            }
            g2.dispose();
            super.paintComponent(graphics);
        }
    }

    private enum IconType {
        DASHBOARD, SETTINGS, KEY, PLAY, PAIRS, CLOCK, CHART, WALLET, SHIELD, SAVE,
        CHECK
    }

    private static final class LineIcon implements Icon {
        private final IconType type;
        private final int size;
        private final Color fixedColor;

        private LineIcon(IconType type, int size) {
            this(type, size, null);
        }

        private LineIcon(IconType type, int size, Color fixedColor) {
            this.type = type;
            this.size = size;
            this.fixedColor = fixedColor;
        }

        @Override
        public int getIconWidth() {
            return size;
        }

        @Override
        public int getIconHeight() {
            return size;
        }

        @Override
        public void paintIcon(Component component, Graphics graphics, int x, int y) {
            Graphics2D g2 = (Graphics2D) graphics.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(fixedColor != null ? fixedColor : component.getForeground());
            g2.setStroke(new BasicStroke(Math.max(1.4f, size / 11f),
                    BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

            int s = size;
            switch (type) {
                case DASHBOARD -> {
                    int cell = (s - 5) / 2;
                    g2.drawRoundRect(x + 1, y + 1, cell, cell, 3, 3);
                    g2.drawRoundRect(x + s - cell - 1, y + 1, cell, cell, 3, 3);
                    g2.drawRoundRect(x + 1, y + s - cell - 1, cell, cell, 3, 3);
                    g2.drawRoundRect(x + s - cell - 1, y + s - cell - 1,
                            cell, cell, 3, 3);
                }
                case SETTINGS -> {
                    g2.drawLine(x + 2, y + 4, x + s - 2, y + 4);
                    g2.drawLine(x + 2, y + s / 2, x + s - 2, y + s / 2);
                    g2.drawLine(x + 2, y + s - 4, x + s - 2, y + s - 4);
                    g2.fillOval(x + 4, y + 1, 6, 6);
                    g2.fillOval(x + s - 10, y + s / 2 - 3, 6, 6);
                    g2.fillOval(x + 6, y + s - 7, 6, 6);
                }
                case KEY -> {
                    g2.drawOval(x + 1, y + 2, s / 2, s / 2);
                    g2.drawLine(x + s / 2, y + s / 2 + 1, x + s - 2, y + s - 2);
                    g2.drawLine(x + s - 6, y + s - 6, x + s - 3, y + s - 9);
                }
                case PLAY -> {
                    g2.drawRoundRect(x + 1, y + 1, s - 2, s - 2, 5, 5);
                    int[] xs = {x + s / 2 - 2, x + s / 2 - 2, x + s - 4};
                    int[] ys = {y + 4, y + s - 4, y + s / 2};
                    g2.fillPolygon(xs, ys, 3);
                }
                case PAIRS -> {
                    g2.drawOval(x + 1, y + 4, s - 7, s - 7);
                    g2.drawOval(x + 6, y + 1, s - 7, s - 7);
                }
                case CLOCK -> {
                    g2.drawOval(x + 1, y + 1, s - 2, s - 2);
                    g2.drawLine(x + s / 2, y + 4, x + s / 2, y + s / 2);
                    g2.drawLine(x + s / 2, y + s / 2, x + s - 4, y + s / 2);
                }
                case CHART -> {
                    g2.drawLine(x + 2, y + s - 3, x + s - 2, y + s - 3);
                    g2.drawLine(x + 3, y + s - 4, x + 3, y + 2);
                    g2.drawLine(x + 5, y + s - 7, x + 9, y + s / 2);
                    g2.drawLine(x + 9, y + s / 2, x + 12, y + s / 2 + 2);
                    g2.drawLine(x + 12, y + s / 2 + 2, x + s - 3, y + 3);
                }
                case WALLET -> {
                    g2.drawRoundRect(x + 1, y + 3, s - 2, s - 6, 4, 4);
                    g2.drawLine(x + 2, y + 7, x + s - 2, y + 7);
                    g2.fillOval(x + s - 6, y + s / 2, 2, 2);
                }
                case SHIELD -> {
                    int[] xs = {x + s / 2, x + s - 2, x + s - 3, x + s / 2,
                            x + 3, x + 2};
                    int[] ys = {y + 1, y + 4, y + s - 6, y + s - 2,
                            y + s - 6, y + 4};
                    g2.drawPolygon(xs, ys, xs.length);
                    g2.drawLine(x + 5, y + s / 2, x + s / 2 - 1, y + s - 6);
                    g2.drawLine(x + s / 2 - 1, y + s - 6, x + s - 4, y + 5);
                }
                case SAVE -> {
                    g2.drawRoundRect(x + 2, y + 1, s - 4, s - 2, 3, 3);
                    g2.drawRect(x + 5, y + 2, s - 10, s / 3);
                    g2.drawRect(x + 5, y + s / 2 + 1, s - 10, s / 3);
                }
                case CHECK -> {
                    g2.drawLine(x + 1, y + s / 2, x + s / 3, y + s - 2);
                    g2.drawLine(x + s / 3, y + s - 2, x + s - 1, y + 1);
                }
            }
            g2.dispose();
        }
    }
}
