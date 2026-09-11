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
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.DefaultListModel;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JDialog;
import javax.swing.JFormattedTextField;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.ScrollPaneConstants;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;

import com.oneofx.fusion.client.FusionApiClient;
import com.oneofx.fusion.client.model.Account;
import com.oneofx.fusion.client.model.AssetBalance;
import com.oneofx.fusion.client.model.OrderSide;
import com.oneofx.fusion.client.model.OrderType;
import com.oneofx.fusion.client.model.TickerPrice;
import com.oneofx.fusion.client.model.TradingPair;
import com.oneofx.fusion.tradingbot.Database.PortablePaths;
import com.oneofx.fusion.tradingbot.Database.dbUrl;
import com.oneofx.fusion.tradingbot.Settings.FusionClientProvider;
import com.oneofx.fusion.tradingbot.desktop.TradingEngineController.State;
import com.oneofx.fusion.tradingbot.grid.GridMode;
import com.oneofx.fusion.tradingbot.grid.GridPreviewService;
import com.oneofx.fusion.tradingbot.backtest.BacktestRepository;
import com.oneofx.fusion.tradingbot.backtest.BacktestRepository.BacktestRunSummary;
import com.oneofx.fusion.tradingbot.backtest.BacktestEquityPoint;
import com.oneofx.fusion.tradingbot.backtest.BacktestExecutionConfig;
import com.oneofx.fusion.tradingbot.backtest.BacktestOptimizationRequest;
import com.oneofx.fusion.tradingbot.backtest.BacktestOptimizationResult;
import com.oneofx.fusion.tradingbot.backtest.BacktestOptimizationService;
import com.oneofx.fusion.tradingbot.backtest.BacktestOrderEvent;
import com.oneofx.fusion.tradingbot.backtest.BacktestRequest;
import com.oneofx.fusion.tradingbot.backtest.BacktestResult;
import com.oneofx.fusion.tradingbot.backtest.BacktestService;
import com.oneofx.fusion.tradingbot.strategy.StrategyDefinition;
import com.oneofx.fusion.tradingbot.strategy.EntrySpacingMode;
import com.oneofx.fusion.tradingbot.strategy.StrategyEvaluation;
import com.oneofx.fusion.tradingbot.strategy.StrategyEvaluationService;
import com.oneofx.fusion.tradingbot.strategy.StrategyNode;
import com.oneofx.fusion.tradingbot.strategy.StrategyRepository;
import com.oneofx.fusion.tradingbot.strategy.StrategyNode.Action;
import com.oneofx.fusion.tradingbot.strategy.StrategyNode.Comparator;
import com.oneofx.fusion.tradingbot.strategy.StrategyNode.Indicator;
import com.oneofx.fusion.tradingbot.strategy.StrategyNode.Logic;
import com.oneofx.fusion.tradingbot.strategy.StrategyNode.NodeType;

/** Lokale OneOfX-Bedienoberfläche im dunklen Trading-Design. */
public final class OneOfXFrame extends JFrame {

    private static final String PAGE_DASHBOARD = "dashboard";
    private static final String PAGE_BOTS = "bots";
    private static final String PAGE_TRADE = "trade";
    private static final String PAGE_SYSTEM = "system";
    private static final String PAGE_STRATEGIES = "strategies";
    private static final String PAGE_BACKTEST = "backtest";
    private static final String PAGE_TERMINAL = "terminal";
    private static final String PAGE_SETTINGS = "settings";
    private static final String PAGE_ORDERS = "orders";
    private static final String PAGE_POSITIONS = "positions";
    private static final String PAGE_BALANCES = "balances";
    private static final String PAGE_ACTIVITY = "activity";
    private static final String PAGE_ACCESS = "access";
    private static final int FORM_LABEL_WIDTH = 190;
    private static final int FORM_CONTROL_WIDTH = 155;
    private static final int FORM_SUFFIX_WIDTH = 38;

    private final CurrencySettingsRepository repository = new CurrencySettingsRepository();
    private final BotRepository botRepository = new BotRepository();
    private final PaperTradingRepository paperRepository = new PaperTradingRepository();
    private final BaseConfigRepository baseConfigRepository = new BaseConfigRepository();
    private final OrderTypeAvailability orderTypeAvailability = new OrderTypeAvailability();
    private final OperationsRepository operationsRepository = new OperationsRepository();
    private final TradingPairValidator pairValidator = new TradingPairValidator();
    private final GridPreviewService gridPreviewService = new GridPreviewService();
    private final TradingEngineController engine = new TradingEngineController();
    private final StrategyRepository strategyRepository = new StrategyRepository();
    private final StrategyEvaluationService strategyEvaluationService =
            new StrategyEvaluationService();
    private final BacktestRepository backtestRepository = new BacktestRepository();
    private final BacktestService backtestService = new BacktestService();
    private final BacktestOptimizationService backtestOptimizationService =
            new BacktestOptimizationService();
    private final TradingTerminalService tradingTerminalService=new TradingTerminalService();
    private final PortfolioPanel portfolioPanel;

    private final JLabel pageTitle = new JLabel("Übersicht");
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
    private final JLabel openOrdersCount = metricValue();
    private final JLabel unresolvedCount = metricValue();
    private final JLabel positionsViewCount = metricValue();
    private final JLabel warningsCount = metricValue();
    private final JLabel readinessStatus = new JLabel("Startbereitschaft wird geprüft …");
    private final JLabel readinessDetail = new JLabel("–");
    private final JButton readinessFixButton = new JButton("Beheben");
    private final JPanel dashboardWarningsList = new JPanel();

    private final JButton startButton = new JButton("Trading starten");
    private final JButton stopButton = new JButton("Stoppen");

    private final JComboBox<BotProfile> botBox = new JComboBox<>();
    private final JTextField botName = new JTextField(24);
    private final JCheckBox botEnabled = new JCheckBox("Bot aktiviert");
    private final JComboBox<BotProfile.Mode> botMode =
            new JComboBox<>(BotProfile.Mode.values());
    private final JTextField botStrategy = new JTextField("GRID", 18);
    private final JSpinner botBudget = decimalSpinner(5_000, 0.01, 1_000_000_000.0, 100);
    private final JSpinner botMaxExposure =
            decimalSpinner(5_000, 0.01, 1_000_000_000.0, 100);
    private final JSpinner botMaxPositions = new JSpinner(new SpinnerNumberModel(20, 1, 100_000, 1));
    private final JSpinner botMaxOrders = new JSpinner(new SpinnerNumberModel(4, 1, 100_000, 1));
    private final JSpinner botPaperFee = decimalSpinner(0.25, 0.0, 99.99, 0.01);
    private final JSpinner botPaperSlippage = decimalSpinner(0.10, 0.0, 99.99, 0.01);
    private final JLabel botExposure = metricValue();
    private final JLabel botRemaining = metricValue();
    private final JLabel botOpenLimits = metricValue();
    private final JLabel botSaveStatus = new JLabel("Alle Änderungen gespeichert");
    private final JLabel baseConfigSaveStatus = new JLabel("Alle Änderungen gespeichert");
    private final JComboBox<Object> configEditorBox = new JComboBox<>();
    private final JComboBox<String> baseBuyOrderType = new JComboBox<>(new String[] {"LIMIT", "MARKET", "STOP_LIMIT"});
    private final JComboBox<String> baseSellOrderType = new JComboBox<>(new String[] {"MARKET", "LIMIT", "STOP_MARKET"});
    private final JLabel baseOrderTypeInfo = new JLabel("Ordertypen werden aus dem Paarkatalog geladen");
    private final JSpinner baseBuyOrderMinutes = new JSpinner(new SpinnerNumberModel(0, 0, 525_600, 1));
    private final JSpinner baseSellOrderMinutes = new JSpinner(new SpinnerNumberModel(0, 0, 525_600, 1));
    private final JSpinner baseCooldownMinutes = new JSpinner(new SpinnerNumberModel(60, 0, 525_600, 5));
    private final JSpinner baseTakeProfit = decimalSpinner(3.0, 0.0, 99.99, 0.1);
    private final JCheckBox baseTrailingBuy = new JCheckBox("Trailing Stop-Buy aktiv");
    private final JSpinner baseTrailingBuyActivation = decimalSpinner(1.0, 0.0, 99.99, 0.1);
    private final JSpinner baseTrailingBuyRebound = decimalSpinner(0.3, 0.0, 99.99, 0.1);
    private final JCheckBox baseOnlyProfit = new JCheckBox("Nur mit Gewinn verkaufen");
    private final JCheckBox baseCloseAfterEnabled =
            new JCheckBox("Zeitbasierten Ausstieg verwenden");
    private final JSpinner baseCloseAfter = new JSpinner(new SpinnerNumberModel(0, 0, 5_256_000, 60));
    private final JCheckBox baseDcaEnabled = new JCheckBox("DCA aktiv");
    private final JSpinner baseDcaMaxOrders = new JSpinner(new SpinnerNumberModel(3, 0, 1000, 1));
    private final JSpinner baseDcaTrigger = decimalSpinner(2.0, 0.0, 99.99, 0.1);
    private final JSpinner baseDcaMultiplier = decimalSpinner(1.0, 1.0, 100.0, 0.1);
    private final JComboBox<Object> pairPoolBox = new JComboBox<>();

    private final DefaultListModel<StrategyDefinition> strategyLibraryModel =
            new DefaultListModel<>();
    private final JList<StrategyDefinition> strategyLibrary =
            new JList<>(strategyLibraryModel);
    private final JTextField strategyName = new JTextField(24);
    private final JSpinner strategyConfirmations =
            new JSpinner(new SpinnerNumberModel(1, 1, 100, 1));
    private final JComboBox<EntrySpacingMode> strategySpacingMode =
            new JComboBox<>(EntrySpacingMode.values());
    private final JSpinner strategySpacing = decimalSpinner(1.0, 0.01, 1_000_000_000, 0.1);
    private final JCheckBox strategyEnabled = new JCheckBox("Strategie aktiv");
    private final JLabel strategySaveStatus = new JLabel("Automatische Speicherung aktiv");
    private final JLabel strategySpacingPreview = new JLabel();
    private final JLabel strategyTestStatus = new JLabel("Noch kein Markttest durchgeführt.");
    private final JButton strategyBacktestButton = new JButton("Backtest mit dieser Strategie");
    private StrategyDefinition lastTestedStrategy;
    private String lastTestedCurrency;
    private final JComboBox<AssignmentTarget> strategyAssignment = new JComboBox<>();
    private final JLabel strategyAssignmentInfo = new JLabel("–");
    private final DefaultTableModel strategyRulesModel = readOnlyModel(
            "Bereich", "Signal", "Indikator/Gruppe", "Bedingung", "Vergleich mit",
            "Zeitraum", "Einstellung");
    private final JTable strategyRulesTable = cockpitTable(strategyRulesModel);
    private List<StrategyNode> visibleStrategyNodes = List.of();

    private final JComboBox<StrategyDefinition> backtestStrategyBox = new JComboBox<>();
    private final JComboBox<String> backtestCurrencyBox = new JComboBox<>();
    private final JComboBox<String> backtestTimeframe = new JComboBox<>(
            new String[] {"1m", "5m", "10m", "15m", "30m", "1h", "4h", "1d"});
    private final JTextField backtestStart = new JTextField(
            LocalDate.now().minusDays(90).toString(), 12);
    private final JTextField backtestEnd = new JTextField(LocalDate.now().toString(), 12);
    private final JSpinner backtestCapital = decimalSpinner(5_000, 1, 1_000_000_000, 100);
    private final JSpinner backtestSpread = decimalSpinner(0.05, 0, 10, 0.01);
    private final JSpinner backtestPartialFill = decimalSpinner(50, 0.01, 100, 5);
    private final JSpinner backtestVolumeParticipation = decimalSpinner(1, 0.01, 100, 0.1);
    private final DefaultListModel<StrategyDefinition> backtestOptimizationStrategyModel =
            new DefaultListModel<>();
    private final JList<StrategyDefinition> backtestOptimizationStrategies =
            new JList<>(backtestOptimizationStrategyModel);
    private final JTextField backtestOptimizationGrid = new JTextField(18);
    private final JTextField backtestOptimizationStop = new JTextField(18);
    private final JTextField backtestOptimizationTakeProfit = new JTextField(18);
    private final JSpinner backtestInSample = decimalSpinner(70, 50, 90, 5);
    private final JSpinner backtestWalkForwardFolds =
            new JSpinner(new SpinnerNumberModel(3, 1, 6, 1));
    private final JComboBox<BacktestRunSummary> backtestHistory = new JComboBox<>();
    private final JLabel backtestFinalCapital = metricValue();
    private final JLabel backtestProfit = metricValue();
    private final JLabel backtestReturn = metricValue();
    private final JLabel backtestDrawdown = metricValue();
    private final JLabel backtestTradeCount = metricValue();
    private final JLabel backtestWinRate = metricValue();
    private final JLabel backtestProfitFactor = metricValue();
    private final JLabel backtestRunStatus = new JLabel(
            "Noch kein Backtest gestartet. Ergebnisse erscheinen direkt nach dem Lauf.");
    private final DefaultTableModel backtestTradesModel = readOnlyModel(
            "#", "Einstieg", "Ausstieg", "Kaufpreis", "Verkaufspreis", "Menge",
            "Gebühren", "PnL", "Grund", "Signalerklärung");
    private final JTable backtestTradesTable = cockpitTable(backtestTradesModel);
    private final DefaultTableModel backtestOrdersModel = readOnlyModel(
            "#", "Order", "Grid", "Zeit", "Ereignis", "Limit", "Fillpreis",
            "Fillmenge", "Restbetrag", "Grund");
    private final JTable backtestOrdersTable = cockpitTable(backtestOrdersModel);
    private final DefaultTableModel backtestOptimizationModel = readOnlyModel(
            "Rang", "Strategie", "Grid", "SL", "TP", "IS Rendite", "IS DD",
            "IS Trades", "OOS Rendite", "OOS DD", "OOS Trades", "IS Score");
    private final JTable backtestOptimizationTable = cockpitTable(backtestOptimizationModel);
    private final DefaultTableModel backtestWalkForwardModel = readOnlyModel(
            "Fenster", "Training bis", "Testzeitraum", "Gewählte Variante",
            "OOS Rendite", "OOS DD", "OOS Trades");
    private final JTable backtestWalkForwardTable = cockpitTable(backtestWalkForwardModel);
    private final EquityCurvePanel backtestEquityCurve = new EquityCurvePanel();

    private final JComboBox<String> terminalCurrencyBox=new JComboBox<>();
    private final JComboBox<String> terminalTimeframe=new JComboBox<>(
            new String[]{"1m","5m","10m","15m","30m","1h","4h","1d"});
    private final JSpinner terminalCandleCount=
            new JSpinner(new SpinnerNumberModel(300,30,1_440,30));
    private final JButton terminalRefreshButton=new JButton("Marktdaten laden");
    private final TradingChartPanel tradingChart=new TradingChartPanel();
    private final JLabel terminalLastPrice=metricValue();
    private final JLabel terminalCandles=metricValue();
    private final JLabel terminalOverlays=metricValue();
    private final JLabel terminalUpdated=metricValue();
    private final JComboBox<OrderSide> terminalOrderSide=new JComboBox<>(OrderSide.values());
    private final JComboBox<OrderType> terminalOrderType=
            new JComboBox<>(new OrderType[]{OrderType.LIMIT,OrderType.MARKET});
    private final JSpinner terminalOrderQuantity=
            decimalSpinner(0.001,0.00000001,1_000_000_000.0,0.001);
    private final JSpinner terminalOrderPrice=
            decimalSpinner(100,0.00000001,1_000_000_000.0,1);
    private final JComboBox<PositionChoice> terminalPositionBox=new JComboBox<>();
    private final JButton terminalSubmitOrder=new JButton("Order prüfen");
    private final JButton terminalReplaceOrder=new JButton("Ausgewählte ersetzen");
    private final JButton terminalCancelOrder=new JButton("Ausgewählte stornieren");
    private final DefaultTableModel terminalOrdersModel=readOnlyModel(
            "Modus","Seite","Paar","Order-ID","Typ","Preis","Menge","Status");
    private final JTable terminalOrdersTable=cockpitTable(terminalOrdersModel);
    private final ManualOrderService manualOrderService=new ManualOrderService();
    private List<OperationsRepository.OpenOrderRow> terminalOrderRows=List.of();
    private TradingTerminalSnapshot terminalSnapshot;
    private boolean terminalOrderRefreshing;
    private boolean terminalLoading;

    private final JComboBox<String> currencyBox = new JComboBox<>();
    private final JLabel pairSupportedOrderTypes = new JLabel("Unterstützte Ordertypen: –");
    private final JCheckBox buyEnabled = new JCheckBox("Neue Käufe erlauben");
    private final JSpinner buyAmount = decimalSpinner(10.0, 0.01, 1_000_000.0, 1.0);
    private final JSpinner maxBuyAmount =
            decimalSpinner(500.0, 0.01, 100_000_000.0, 10.0);
    private final JComboBox<GridMode> gridMode = new JComboBox<>(GridMode.values());
    private final JSpinner gridSpacing =
            decimalSpinner(1.0 / 23.0, 0.00000001, 1_000_000.0, 0.1);
    private final JLabel gridSpacingUnit = new JLabel("%");
    private final JSpinner previewPrice =
            decimalSpinner(100.0, 0.00000001, 1_000_000_000.0, 1.0);
    private final JLabel previewOrders = new JLabel("–");
    private final JLabel previewCapital = new JLabel("–");
    private final JLabel previewBand = new JLabel("–");
    private final JLabel previewWarning = new JLabel("Vorschau wird berechnet …");
    private final JLabel settingsSaveStatus = new JLabel("Alle Änderungen gespeichert");
    private final JSpinner stopLoss = decimalSpinner(2.0, 0.0, 99.99, 0.1);
    private final JCheckBox trailingStop = new JCheckBox("Trailing Stop aktiv");
    private final JSpinner trailingActivation =
            decimalSpinner(2.5, 0.0, 99.99, 0.1);
    private final JSpinner trailingDecline =
            decimalSpinner(0.8, 0.0, 99.99, 0.1);

    private final JPasswordField apiKey = new JPasswordField(28);
    private final JTextField baseUrl =
            new JTextField(FusionApiClient.DEFAULT_BASE_URL, 28);

    private final DefaultTableModel gridPreviewModel = readOnlyModel(
            "#", "Preis", "Menge", "Orderwert", "Prüfung");
    private final DefaultTableModel openOrdersModel = readOnlyModel(
            "Seite", "Paar", "Order-ID", "Preis", "Menge", "Status");
    private final DefaultTableModel unresolvedModel = readOnlyModel(
            "Seite", "Paar", "Versuch-ID", "Exchange-ID", "Zustand", "Fehler", "Aktualisiert");
    private final DefaultTableModel positionsModel = readOnlyModel(
            "Paar", "Order-ID", "Kaufpreis", "Orderpreis", "Menge", "Kapital", "Gewinn", "PnL", "Status", "Eröffnet");
    private final DefaultTableModel balancesModel = readOnlyModel(
            "Asset", "Verfügbar", "Gesperrt", "Gesamt");
    private final DefaultTableModel warningModel = readOnlyModel(
            "Stufe", "Bereich", "Paar", "Hinweis");
    private final DefaultTableModel activityModel = readOnlyModel(
            "Zeit", "Stufe", "Bereich", "Paar", "Ereignis");

    private final JTable gridPreviewTable = cockpitTable(gridPreviewModel);
    private final JTable openOrdersTable = cockpitTable(openOrdersModel);
    private final JTable unresolvedTable = cockpitTable(unresolvedModel);
    private final JTable positionsTable = cockpitTable(positionsModel);
    private final JTable balancesTable = cockpitTable(balancesModel);
    private final JTable warningTable = cockpitTable(warningModel);
    private final JTable activityTable = cockpitTable(activityModel);

    private final CardLayout pageLayout = new CardLayout();
    private final JPanel pages = new JPanel(pageLayout);
    private final Map<String, NavButton> navigation = new LinkedHashMap<>();
    private final JTabbedPane botTabs = new JTabbedPane();
    private final JTabbedPane tradingTabs = new JTabbedPane();
    private final JTabbedPane systemTabs = new JTabbedPane();
    private List<ReadinessIssue> startReadinessIssues = List.of();
    private final Timer dashboardTimer;
    private final Timer autoSaveTimer;
    private final Timer botSaveTimer;
    private final Timer strategySaveTimer;
    private boolean loadingSettings;
    private boolean loadingBot;
    private boolean loadingBaseConfig;
    private boolean loadingPoolAssignment;
    private boolean autoSaveDirty;
    private boolean botSaveDirty;
    private boolean loadingStrategy;
    private boolean loadingBacktest;
    private boolean strategySaveDirty;
    private String loadedCurrency;
    private BotProfile loadedBot;
    private ConfigPool loadedConfigPool;
    private StrategyDefinition loadedStrategy;
    private CurrencySettingsRepository.GridPreviewContext gridPreviewContext;

    public OneOfXFrame() {
        super("OneOfX Trading Bot");
        portfolioPanel=new PortfolioPanel(this::selectedBot,text->setMessage(text,false));
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
        botSaveTimer = new Timer(700, event -> savePendingBot());
        botSaveTimer.setRepeats(false);
        strategySaveTimer = new Timer(700, event -> savePendingStrategy());
        strategySaveTimer.setRepeats(false);

        startButton.addActionListener(event -> startTrading());
        stopButton.addActionListener(event -> engine.stop(this::showEngineState));
        botBox.addActionListener(event -> switchSelectedBot());
        currencyBox.addActionListener(event -> switchSelectedCurrency());
        configEditorBox.addActionListener(event -> switchConfigEditor());
        pairPoolBox.addActionListener(event -> assignSelectedPool());
        strategyLibrary.addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) switchStrategy();
        });
        strategyAssignment.addActionListener(event -> refreshStrategyAssignmentInfo());
        backtestHistory.addActionListener(event -> loadSelectedBacktest());
        backtestTimeframe.setSelectedItem("1h");
        terminalTimeframe.setSelectedItem("15m");
        terminalRefreshButton.addActionListener(event->refreshTradingTerminal(true));
        terminalCurrencyBox.addActionListener(event->{
            refreshTerminalOrderTypes();
            refreshTerminalOrderControls();
        });
        terminalOrderSide.addActionListener(event->refreshTerminalOrderControls());
        terminalOrderType.addActionListener(event->refreshTerminalOrderControls());
        terminalSubmitOrder.addActionListener(event->submitManualOrder(false));
        terminalReplaceOrder.addActionListener(event->submitManualOrder(true));
        terminalCancelOrder.addActionListener(event->cancelSelectedOrder());
        terminalOrdersTable.getSelectionModel().addListSelectionListener(event->{
            if(!terminalOrderRefreshing&&!event.getValueIsAdjusting())loadSelectedTerminalOrder();
        });
        installAutoSaveListeners();
        installBotAutoSaveListeners();
        installStrategyAutoSaveListeners();
        previewPrice.addChangeListener(event -> refreshGridPreview());
        apiKey.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent event) { refreshStartReadiness(); }
            @Override public void removeUpdate(DocumentEvent event) { refreshStartReadiness(); }
            @Override public void changedUpdate(DocumentEvent event) { refreshStartReadiness(); }
        });

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent event) {
                closeApplication();
            }
        });

        reloadBots(null);
        refreshDashboard();
        refreshOperationalViews();
        showEngineState(State.STOPPED);
        showPage(PAGE_DASHBOARD, "Übersicht");
        recordEvent("INFO", "SYSTEM", null, "OneOfX Desktop wurde gestartet.");
        refreshOperationalViews();

        dashboardTimer = new Timer(2_000, event -> {
            refreshDashboard();
            refreshOperationalViews();
        });
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

        sidebar.add(createNavButton(PAGE_DASHBOARD, "Übersicht", IconType.DASHBOARD));
        sidebar.add(Box.createVerticalStrut(6));
        sidebar.add(createNavButton(PAGE_BOTS, "Bots", IconType.PAIRS));
        sidebar.add(Box.createVerticalStrut(6));
        sidebar.add(createNavButton(PAGE_TRADE, "Handel", IconType.CHART));
        sidebar.add(Box.createVerticalStrut(6));
        sidebar.add(createNavButton(PAGE_BACKTEST, "Backtesting", IconType.CLOCK));
        sidebar.add(Box.createVerticalStrut(6));
        sidebar.add(createNavButton(PAGE_SYSTEM, "System", IconType.SETTINGS));
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
        pages.add(createBotsWorkspacePage(), PAGE_BOTS);
        pages.add(createTradingWorkspacePage(), PAGE_TRADE);
        pages.add(createBacktestPage(), PAGE_BACKTEST);
        pages.add(createSystemWorkspacePage(), PAGE_SYSTEM);

        workspace.add(createTopBar(), BorderLayout.NORTH);
        workspace.add(pages, BorderLayout.CENTER);
        workspace.add(createStatusBar(), BorderLayout.SOUTH);
        return workspace;
    }

    private JPanel createBotsWorkspacePage() {
        JPanel workspace = new JPanel(new BorderLayout());
        workspace.setOpaque(false);
        configureWorkspaceTabs(botTabs);
        botTabs.addTab("Basis", createBotsPage());
        botTabs.addTab("Paare & Risiko", createSettings());
        botTabs.addTab("Strategien", createStrategiesPage());
        botTabs.addTab("Erweitert", createAdvancedBotPage());
        workspace.add(botTabs, BorderLayout.CENTER);
        return workspace;
    }

    private JPanel createTradingWorkspacePage() {
        JPanel workspace = new JPanel(new BorderLayout());
        workspace.setOpaque(false);
        configureWorkspaceTabs(tradingTabs);
        tradingTabs.addTab("Terminal", createTradingTerminalPage());
        tradingTabs.addTab("Positionen", createPositionsPage());
        tradingTabs.addTab("Orders", createOrdersPage());
        tradingTabs.addTab("Ungeklärt", createUnresolvedPage());
        tradingTabs.addTab("Kontostände", createBalancesPage());
        workspace.add(tradingTabs, BorderLayout.CENTER);
        return workspace;
    }

    private JPanel createSystemWorkspacePage() {
        JPanel workspace = new JPanel(new BorderLayout());
        workspace.setOpaque(false);
        configureWorkspaceTabs(systemTabs);
        systemTabs.addTab("Aktivität & Warnungen", createActivityPage());
        systemTabs.addTab("API-Zugang", createAccessPanel());
        workspace.add(systemTabs, BorderLayout.CENTER);
        return workspace;
    }

    private static void configureWorkspaceTabs(JTabbedPane tabs) {
        tabs.setOpaque(false);
        tabs.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);
        tabs.setBorder(OneOfXTheme.padding(10, 18, 0, 18));
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

        JLabel botLabel = new JLabel("Bot");
        botLabel.setForeground(OneOfXTheme.TEXT_MUTED);
        botLabel.setFont(OneOfXTheme.font(Font.BOLD, 10));
        controls.add(botLabel);
        botBox.setPreferredSize(new Dimension(170, 38));
        OneOfXTheme.round(botBox);
        controls.add(botBox);

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

        content.add(createReadinessCard());
        content.add(Box.createVerticalStrut(16));

        JPanel detailRow = new JPanel(new GridLayout(1, 2, 16, 0));
        detailRow.setOpaque(false);
        detailRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        detailRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 260));
        detailRow.add(createEngineCard());
        detailRow.add(createActionCenterCard());
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

    private RoundedPanel createReadinessCard() {
        RoundedPanel card = cardPanel(new BorderLayout(18, 0));
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 112));
        JPanel copy = new JPanel();
        copy.setOpaque(false);
        copy.setLayout(new BoxLayout(copy, BoxLayout.Y_AXIS));
        readinessStatus.setFont(OneOfXTheme.font(Font.BOLD, 15));
        readinessStatus.setForeground(OneOfXTheme.TEXT);
        readinessDetail.setFont(OneOfXTheme.font(Font.PLAIN, 12));
        readinessDetail.setForeground(OneOfXTheme.TEXT_SECONDARY);
        copy.add(readinessStatus);
        copy.add(Box.createVerticalStrut(6));
        copy.add(readinessDetail);
        card.add(copy, BorderLayout.CENTER);
        stylePrimaryButton(readinessFixButton);
        readinessFixButton.addActionListener(event -> fixFirstReadinessIssue());
        card.add(readinessFixButton, BorderLayout.EAST);
        return card;
    }

    private RoundedPanel createActionCenterCard() {
        RoundedPanel card = cardPanel(new BorderLayout(0, 16));
        JPanel heading = new JPanel(new BorderLayout(12, 0));
        heading.setOpaque(false);
        RoundedPanel icon = iconTile(IconType.SHIELD, OneOfXTheme.PRIMARY,
                OneOfXTheme.PRIMARY_SOFT);
        heading.add(icon, BorderLayout.WEST);
        heading.add(titleBlock("Handlungsbedarf",
                "Probleme öffnen direkt die passende Stelle."), BorderLayout.CENTER);
        card.add(heading, BorderLayout.NORTH);

        dashboardWarningsList.setOpaque(false);
        dashboardWarningsList.setLayout(new BoxLayout(dashboardWarningsList, BoxLayout.Y_AXIS));
        card.add(dashboardWarningsList, BorderLayout.CENTER);
        return card;
    }

    private JScrollPane createBotsPage() {
        JPanel content = pageContent();
        content.add(pageHeading("Bots getrennt steuern",
                "Eigene Paarlisten, Budgets, Status und harte Risikogrenzen je Bot."));
        content.add(Box.createVerticalStrut(22));

        RoundedPanel selection = cardPanel(new BorderLayout(16, 0));
        selection.setMaximumSize(new Dimension(Integer.MAX_VALUE, 92));
        selection.add(titleBlock("Bot-Verwaltung",
                "Die Bot-Auswahl oben gilt für alle Ansichten und die Trading-Engine."),
                BorderLayout.WEST);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        actions.setOpaque(false);
        JButton create = new JButton("Neuen Bot anlegen");
        styleSecondaryButton(create);
        create.setForeground(OneOfXTheme.PRIMARY);
        create.addActionListener(event -> createBot());
        actions.add(create);
        JButton archive = new JButton("Bot archivieren");
        styleSecondaryButton(archive);
        archive.setForeground(OneOfXTheme.ERROR);
        archive.addActionListener(event -> archiveBot());
        actions.add(archive);
        selection.add(actions, BorderLayout.EAST);
        content.add(selection);
        content.add(Box.createVerticalStrut(16));

        JPanel forms = new JPanel(new GridLayout(1, 2, 16, 0));
        forms.setOpaque(false);
        forms.setAlignmentX(Component.LEFT_ALIGNMENT);
        forms.setMaximumSize(new Dimension(Integer.MAX_VALUE, 420));

        RoundedPanel identity = cardPanel(new BorderLayout(0, 18));
        identity.add(titleBlock("Grundkonfiguration",
                "Name, Status, Betriebsart und Strategie"), BorderLayout.NORTH);
        JPanel identityForm = modernForm();
        addFormRow(identityForm, 0, "Bot-Name", botName, null);
        addFormRow(identityForm, 1, "Status", botEnabled, null);
        addFormRow(identityForm, 2, "Modus", botMode, null);
        addFormRow(identityForm, 3, "Strategie", botStrategy, null);
        identity.add(identityForm, BorderLayout.CENTER);
        forms.add(identity);

        RoundedPanel limits = cardPanel(new BorderLayout(0, 18));
        limits.add(titleBlock("Bot-weite Limits",
                "Käufe werden unmittelbar vor der Übermittlung erneut geprüft"),
                BorderLayout.NORTH);
        JPanel limitForm = modernForm();
        addFormRow(limitForm, 0, "Gesamtbudget", botBudget, "EUR");
        addFormRow(limitForm, 1, "Maximales Exposure", botMaxExposure, "EUR");
        addFormRow(limitForm, 2, "Offene Positionen", botMaxPositions, null);
        addFormRow(limitForm, 3, "Offene Orders", botMaxOrders, null);
        addFormRow(limitForm, 4, "Paper-Gebühr", botPaperFee, "%");
        addFormRow(limitForm, 5, "Paper-Slippage", botPaperSlippage, "%");
        limits.add(limitForm, BorderLayout.CENTER);
        forms.add(limits);
        content.add(forms);
        content.add(Box.createVerticalStrut(16));

        JPanel summary = new JPanel(new GridLayout(1, 3, 12, 0));
        summary.setOpaque(false);
        summary.setAlignmentX(Component.LEFT_ALIGNMENT);
        summary.setMaximumSize(new Dimension(Integer.MAX_VALUE, 112));
        summary.add(previewSummary("GEBUNDENES KAPITAL", botExposure));
        summary.add(previewSummary("VERFÜGBAR", botRemaining));
        summary.add(previewSummary("POSITIONEN / ORDERS", botOpenLimits));
        content.add(summary);
        content.add(Box.createVerticalStrut(10));
        configureSaveStatus(botSaveStatus);
        botSaveStatus.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(botSaveStatus);
        content.add(Box.createVerticalGlue());
        return scroll(content);
    }

    private JScrollPane createAdvancedBotPage() {
        JPanel content = pageContent();
        content.add(pageHeading("Erweiterte Bot-Einstellungen",
                "Spezielle Orderausführung, Trailing Stop-Buy, DCA und Paper-Verwaltung."));
        content.add(Box.createVerticalStrut(22));
        content.add(createBaseConfigPanel());
        content.add(Box.createVerticalStrut(16));

        configureSaveStatus(baseConfigSaveStatus);
        baseConfigSaveStatus.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(baseConfigSaveStatus);
        content.add(Box.createVerticalStrut(12));

        RoundedPanel paper = cardPanel(new BorderLayout(16, 0));
        paper.setMaximumSize(new Dimension(Integer.MAX_VALUE, 94));
        paper.add(titleBlock("Paper-Konto",
                "Virtuelle Orders, Positionen und Guthaben kontrolliert zurücksetzen."),
                BorderLayout.WEST);
        JButton resetPaper = new JButton("Paper-Konto zurücksetzen");
        styleSecondaryButton(resetPaper);
        resetPaper.addActionListener(event -> resetPaperAccount());
        paper.add(resetPaper, BorderLayout.EAST);
        content.add(paper);
        content.add(Box.createVerticalStrut(10));
        JLabel hint = new JLabel(
                "Änderungen werden automatisch gespeichert. Paper und Live bleiben strikt getrennt.");
        hint.setForeground(OneOfXTheme.TEXT_MUTED);
        hint.setFont(OneOfXTheme.font(Font.PLAIN, 11));
        hint.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(hint);
        content.add(Box.createVerticalGlue());
        return scroll(content);
    }

    private JScrollPane createStrategiesPage() {
        JPanel content = pageContent();
        content.add(pageHeading("Strategie-Designer",
                "Indikatoren visuell verknüpfen, Signale erklären und gezielt zuweisen."));
        content.add(Box.createVerticalStrut(22));

        RoundedPanel selection = cardPanel(new BorderLayout(16, 12));
        selection.setMaximumSize(new Dimension(Integer.MAX_VALUE, 190));
        selection.add(titleBlock("Meine Strategien",
                "Strategie auswählen oder eine neue Strategie für den aktuellen Bot anlegen."),
                BorderLayout.NORTH);
        strategyLibrary.setVisibleRowCount(4);
        strategyLibrary.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        strategyLibrary.setBackground(OneOfXTheme.SURFACE_HOVER);
        strategyLibrary.setForeground(OneOfXTheme.TEXT);
        strategyLibrary.setSelectionBackground(OneOfXTheme.PRIMARY_SOFT);
        strategyLibrary.setSelectionForeground(OneOfXTheme.TEXT);
        strategyLibrary.setFixedCellHeight(34);
        strategyLibrary.setCellRenderer((list, value, index, selected, focus) -> {
            JLabel label = new JLabel((value.enabled() ? "●  " : "○  ") + value.name()
                    + (value.enabled() ? "   · aktiv" : "   · inaktiv"));
            label.setOpaque(true);
            label.setBorder(OneOfXTheme.padding(0, 10, 0, 10));
            label.setBackground(selected ? OneOfXTheme.PRIMARY_SOFT : OneOfXTheme.SURFACE_HOVER);
            label.setForeground(value.enabled() ? OneOfXTheme.TEXT : OneOfXTheme.TEXT_MUTED);
            return label;
        });
        JScrollPane libraryScroll = new JScrollPane(strategyLibrary);
        libraryScroll.setPreferredSize(new Dimension(0, 112));
        libraryScroll.setBorder(BorderFactory.createLineBorder(OneOfXTheme.BORDER));
        selection.add(libraryScroll, BorderLayout.CENTER);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actions.setOpaque(false);
        JButton create = new JButton("Neue Strategie");
        styleSecondaryButton(create);
        create.addActionListener(event -> createStrategy());
        actions.add(create);
        JButton duplicate = new JButton("Kopie erstellen");
        styleSecondaryButton(duplicate);
        duplicate.addActionListener(event -> duplicateStrategy());
        actions.add(duplicate);
        JButton archive = new JButton("Löschen");
        styleSecondaryButton(archive);
        archive.setForeground(OneOfXTheme.ERROR);
        archive.addActionListener(event -> archiveStrategy());
        actions.add(archive);
        selection.add(actions, BorderLayout.SOUTH);
        content.add(selection);
        content.add(Box.createVerticalStrut(16));

        JPanel top = new JPanel(new GridLayout(1, 2, 16, 0));
        top.setOpaque(false);
        top.setAlignmentX(Component.LEFT_ALIGNMENT);
        top.setMaximumSize(new Dimension(Integer.MAX_VALUE, 310));
        RoundedPanel definition = cardPanel(new BorderLayout(0, 12));
        definition.add(titleBlock("Strategie-Einstellungen",
                "Kaufsignale und Positionsabstand werden automatisch gespeichert"),
                BorderLayout.NORTH);
        JPanel definitionForm = modernForm();
        addFormRow(definitionForm, 0, "Name", strategyName, null);
        addFormRow(definitionForm, 1, "Benötigte Bestätigungsregeln", strategyConfirmations, null);
        addFormRow(definitionForm, 2, "Abstandstyp", strategySpacingMode, null);
        addFormRow(definitionForm, 3, "Mindestabstand", strategySpacing, null);
        addFormRow(definitionForm, 4, "Status", strategyEnabled, null);
        definition.add(definitionForm, BorderLayout.CENTER);
        strategySaveStatus.setForeground(OneOfXTheme.SUCCESS);
        strategySaveStatus.setFont(OneOfXTheme.font(Font.PLAIN, 11));
        strategySpacingPreview.setForeground(OneOfXTheme.TEXT_SECONDARY);
        strategySpacingPreview.setFont(OneOfXTheme.font(Font.PLAIN, 11));
        JPanel strategyFooter = new JPanel();
        strategyFooter.setOpaque(false);
        strategyFooter.setLayout(new BoxLayout(strategyFooter, BoxLayout.Y_AXIS));
        strategyFooter.add(strategySpacingPreview);
        strategyFooter.add(Box.createVerticalStrut(4));
        strategyFooter.add(strategySaveStatus);
        definition.add(strategyFooter, BorderLayout.SOUTH);
        top.add(definition);

        RoundedPanel assignment = cardPanel(new BorderLayout(0, 12));
        assignment.add(titleBlock("Wo soll die Strategie gelten?",
                "Ein Handelspaar hat Vorrang vor Pool, Marktphase und Bot"), BorderLayout.NORTH);
        JPanel assignmentCenter = new JPanel();
        assignmentCenter.setOpaque(false);
        assignmentCenter.setLayout(new BoxLayout(assignmentCenter, BoxLayout.Y_AXIS));
        strategyAssignment.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        strategyAssignment.setAlignmentX(Component.LEFT_ALIGNMENT);
        assignmentCenter.add(strategyAssignment);
        assignmentCenter.add(Box.createVerticalStrut(10));
        JPanel assignmentButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        assignmentButtons.setOpaque(false);
        JButton assign = new JButton("Hier verwenden");
        stylePrimaryButton(assign);
        assign.addActionListener(event -> assignStrategy(false));
        assignmentButtons.add(assign);
        JButton clear = new JButton("Nicht mehr verwenden");
        styleSecondaryButton(clear);
        clear.addActionListener(event -> assignStrategy(true));
        assignmentButtons.add(clear);
        assignmentCenter.add(assignmentButtons);
        strategyAssignmentInfo.setForeground(OneOfXTheme.TEXT_MUTED);
        strategyAssignmentInfo.setFont(OneOfXTheme.font(Font.PLAIN, 11));
        strategyAssignmentInfo.setAlignmentX(Component.LEFT_ALIGNMENT);
        assignmentCenter.add(Box.createVerticalStrut(8));
        assignmentCenter.add(strategyAssignmentInfo);
        assignment.add(assignmentCenter, BorderLayout.CENTER);
        top.add(assignment);
        content.add(top);
        content.add(Box.createVerticalStrut(16));

        RoundedPanel rules = cardPanel(new BorderLayout(0, 14));
        rules.setMaximumSize(new Dimension(Integer.MAX_VALUE, 490));
        JPanel ruleHeader = new JPanel(new BorderLayout(0, 10));
        ruleHeader.setOpaque(false);
        ruleHeader.add(titleBlock("Wann soll die Strategie reagieren?",
                "Regeln direkt hinzufügen. OneOfX ordnet sie automatisch dem richtigen Signal zu."),
                BorderLayout.NORTH);
        JPanel ruleActionRows = new JPanel();
        ruleActionRows.setOpaque(false);
        ruleActionRows.setLayout(new BoxLayout(ruleActionRows, BoxLayout.Y_AXIS));
        JPanel primaryRuleActions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        primaryRuleActions.setOpaque(false);
        JButton buyCondition = new JButton("Kaufregel +");
        stylePrimaryButton(buyCondition);
        buyCondition.addActionListener(event -> addStrategyCondition(Action.BUY));
        primaryRuleActions.add(buyCondition);
        JButton sellCondition = new JButton("Verkaufsregel +");
        styleSecondaryButton(sellCondition);
        sellCondition.addActionListener(event -> addStrategyCondition(Action.SELL));
        primaryRuleActions.add(sellCondition);
        JButton blockCondition = new JButton("Sperrregel +");
        styleSecondaryButton(blockCondition);
        blockCondition.addActionListener(event -> addStrategyCondition(Action.BLOCK));
        primaryRuleActions.add(blockCondition);
        JButton confirmCondition = new JButton("Bestätigung +");
        styleSecondaryButton(confirmCondition);
        confirmCondition.addActionListener(event -> addStrategyCondition(Action.CONFIRM));
        primaryRuleActions.add(confirmCondition);
        ruleActionRows.add(primaryRuleActions);
        ruleActionRows.add(Box.createVerticalStrut(7));
        JPanel advancedRuleActions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        advancedRuleActions.setOpaque(false);
        JButton group = new JButton("UND/ODER-Untergruppe");
        styleSecondaryButton(group);
        group.addActionListener(event -> addStrategyGroup());
        advancedRuleActions.add(group);
        JButton edit = new JButton("Bearbeiten");
        styleSecondaryButton(edit);
        edit.addActionListener(event -> editStrategyNode());
        advancedRuleActions.add(edit);
        JButton remove = new JButton("Entfernen");
        styleSecondaryButton(remove);
        remove.setForeground(OneOfXTheme.ERROR);
        remove.addActionListener(event -> deleteStrategyNode());
        advancedRuleActions.add(remove);
        JButton test = new JButton("Mit Marktdaten testen");
        stylePrimaryButton(test);
        test.addActionListener(event -> testStrategy(test));
        advancedRuleActions.add(test);
        styleSecondaryButton(strategyBacktestButton);
        strategyBacktestButton.setEnabled(false);
        strategyBacktestButton.addActionListener(event -> openBacktestForTestedStrategy());
        advancedRuleActions.add(strategyBacktestButton);
        ruleActionRows.add(advancedRuleActions);
        strategyTestStatus.setForeground(OneOfXTheme.TEXT_MUTED);
        strategyTestStatus.setFont(OneOfXTheme.font(Font.PLAIN, 11));
        strategyTestStatus.setAlignmentX(Component.LEFT_ALIGNMENT);
        ruleActionRows.add(Box.createVerticalStrut(8));
        ruleActionRows.add(strategyTestStatus);
        ruleHeader.add(ruleActionRows, BorderLayout.SOUTH);
        rules.add(ruleHeader, BorderLayout.NORTH);
        strategyRulesTable.setAutoCreateRowSorter(false);
        strategyRulesTable.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseClicked(java.awt.event.MouseEvent event) {
                if (event.getClickCount() == 2) editStrategyNode();
            }
        });
        JScrollPane ruleScroll = new JScrollPane(strategyRulesTable);
        ruleScroll.setBorder(BorderFactory.createLineBorder(OneOfXTheme.BORDER));
        rules.add(ruleScroll, BorderLayout.CENTER);
        content.add(rules);
        content.add(Box.createVerticalGlue());
        return scroll(content);
    }

    private JScrollPane createBacktestPage() {
        JPanel content=pageContent();
        content.add(pageHeading("Backtesting",
                "Strategien mit historischen Fusion-Kerzen testen – lokal und ohne Orders."));
        content.add(Box.createVerticalStrut(22));

        RoundedPanel setup=cardPanel(new BorderLayout(0,16));
        setup.setMaximumSize(new Dimension(Integer.MAX_VALUE,430));
        JPanel header=new JPanel(new BorderLayout(12,0));header.setOpaque(false);
        header.add(titleBlock("Neuer Backtest",
                "Zeitraum in UTC · Ausführung am nächsten Open · Kosten aus der Bot-Konfiguration"),BorderLayout.WEST);
        JButton run=new JButton("Backtest starten");stylePrimaryButton(run);
        run.addActionListener(event->runBacktest(run));header.add(run,BorderLayout.EAST);setup.add(header,BorderLayout.NORTH);
        JPanel columns=new JPanel(new GridLayout(1,2,20,0));columns.setOpaque(false);
        JPanel left=modernForm();
        addFormRow(left,0,"Strategie",backtestStrategyBox,null);
        addFormRow(left,1,"Handelspaar",backtestCurrencyBox,null);
        addFormRow(left,2,"Basis-Timeframe",backtestTimeframe,null);
        JPanel right=modernForm();
        addFormRow(right,0,"Startdatum",backtestStart,"JJJJ-MM-TT");
        addFormRow(right,1,"Enddatum",backtestEnd,"JJJJ-MM-TT");
        addFormRow(right,2,"Startkapital",backtestCapital,"EUR");
        addFormRow(right,3,"Spread",backtestSpread,"%");
        addFormRow(right,4,"Teilfüllung je Kerze",backtestPartialFill,"%");
        addFormRow(right,5,"Volumenbeteiligung",backtestVolumeParticipation,"%");
        columns.add(left);columns.add(right);setup.add(columns,BorderLayout.CENTER);
        backtestRunStatus.setForeground(OneOfXTheme.TEXT_SECONDARY);
        backtestRunStatus.setFont(OneOfXTheme.font(Font.BOLD,11));
        backtestRunStatus.setBorder(OneOfXTheme.padding(8,0,0,0));
        setup.add(backtestRunStatus,BorderLayout.SOUTH);
        content.add(setup);content.add(Box.createVerticalStrut(16));

        RoundedPanel optimization=cardPanel(new BorderLayout(0,16));
        optimization.setMaximumSize(new Dimension(Integer.MAX_VALUE,360));
        JPanel optimizationHeader=new JPanel(new BorderLayout(12,0));
        optimizationHeader.setOpaque(false);
        optimizationHeader.add(titleBlock("Varianten und Walk-forward",
                "Rang nur aus In-Sample-Daten · Werte mit Semikolon trennen · maximal 60 Varianten"),
                BorderLayout.WEST);
        JButton optimize=new JButton("Varianten vergleichen");stylePrimaryButton(optimize);
        optimize.addActionListener(event->runBacktestOptimization(optimize));
        optimizationHeader.add(optimize,BorderLayout.EAST);
        optimization.add(optimizationHeader,BorderLayout.NORTH);
        JPanel optimizationColumns=new JPanel(new GridLayout(1,2,20,0));
        optimizationColumns.setOpaque(false);
        JPanel strategySelection=new JPanel(new BorderLayout(0,8));strategySelection.setOpaque(false);
        JLabel strategyLabel=new JLabel("Strategien (Mehrfachauswahl)");
        strategyLabel.setForeground(OneOfXTheme.TEXT_MUTED);
        strategySelection.add(strategyLabel,BorderLayout.NORTH);
        backtestOptimizationStrategies.setSelectionMode(
                ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        JScrollPane strategyScroll=new JScrollPane(backtestOptimizationStrategies);
        strategyScroll.setPreferredSize(new Dimension(0,180));
        strategyScroll.setBorder(BorderFactory.createLineBorder(OneOfXTheme.BORDER));
        strategySelection.add(strategyScroll,BorderLayout.CENTER);
        JPanel variantFields=modernForm();
        addFormRow(variantFields,0,"Grid-Abstände",backtestOptimizationGrid,"leer = aktuell");
        addFormRow(variantFields,1,"Stop-Loss",backtestOptimizationStop,"% · leer = aktuell");
        addFormRow(variantFields,2,"Take Profit",backtestOptimizationTakeProfit,"% · leer = aktuell");
        addFormRow(variantFields,3,"In-Sample",backtestInSample,"%");
        addFormRow(variantFields,4,"Walk-forward-Fenster",backtestWalkForwardFolds,null);
        optimizationColumns.add(strategySelection);optimizationColumns.add(variantFields);
        optimization.add(optimizationColumns,BorderLayout.CENTER);
        content.add(optimization);content.add(Box.createVerticalStrut(16));

        content.add(tableCard("Variantenvergleich",
                "IS bestimmt den Rang; OOS bleibt bei der Auswahl unangetastet",
                backtestOptimizationTable,330));content.add(Box.createVerticalStrut(16));
        content.add(tableCard("Walk-forward-Auswertung",
                "Expanding Window: bis zum jeweiligen Testfenster werden nur frühere Daten verwendet",
                backtestWalkForwardTable,260));content.add(Box.createVerticalStrut(16));

        RoundedPanel history=cardPanel(new BorderLayout(16,0));
        history.setMaximumSize(new Dimension(Integer.MAX_VALUE,92));
        history.add(titleBlock("Gespeicherte Läufe","Die letzten 100 Backtests dieses Bots"),BorderLayout.WEST);
        backtestHistory.setPreferredSize(new Dimension(390,40));history.add(backtestHistory,BorderLayout.EAST);
        content.add(history);content.add(Box.createVerticalStrut(16));

        JPanel metrics=new JPanel(new GridLayout(2,4,12,12));metrics.setOpaque(false);
        metrics.setAlignmentX(Component.LEFT_ALIGNMENT);metrics.setMaximumSize(new Dimension(Integer.MAX_VALUE,218));
        metrics.add(previewSummary("ENDKAPITAL",backtestFinalCapital));
        metrics.add(previewSummary("NETTOERGEBNIS",backtestProfit));
        metrics.add(previewSummary("RENDITE",backtestReturn));
        metrics.add(previewSummary("MAX. DRAWDOWN",backtestDrawdown));
        metrics.add(previewSummary("TRADES",backtestTradeCount));
        metrics.add(previewSummary("TREFFERQUOTE",backtestWinRate));
        metrics.add(previewSummary("PROFIT FACTOR",backtestProfitFactor));
        JLabel method=new JLabel("Next-open / konservativ");
        metrics.add(previewSummary("AUSFÜHRUNGSMODELL",method));
        content.add(metrics);content.add(Box.createVerticalStrut(16));
        RoundedPanel curve=cardPanel(new BorderLayout(0,12));
        curve.setMaximumSize(new Dimension(Integer.MAX_VALUE,230));
        curve.setPreferredSize(new Dimension(0,230));
        curve.add(titleBlock("Equity-Kurve","Kontowert nach jeder simulierten Basis-Kerze"),BorderLayout.NORTH);
        curve.add(backtestEquityCurve,BorderLayout.CENTER);content.add(curve);content.add(Box.createVerticalStrut(16));
        content.add(tableCard("Order- und Teilfüllungsprotokoll",
                "Grid-Platzierungen, Teilfüllungen, Vollfüllungen und Stornierungen",
                backtestOrdersTable,330));content.add(Box.createVerticalStrut(16));
        content.add(tableCard("Simulierte Trades",
                "Gebühren und Slippage sind bereits im PnL enthalten",backtestTradesTable,390));
        content.add(Box.createVerticalGlue());return scroll(content);
    }

    private JScrollPane createTradingTerminalPage(){
        JPanel content=pageContent();
        content.add(pageHeading("Trading-Terminal",
                "Kerzen, Grid, Orders und Positionen in einer gemeinsamen lokalen Ansicht."));
        content.add(Box.createVerticalStrut(18));
        RoundedPanel controls=cardPanel(new BorderLayout(16,0));
        controls.setMaximumSize(new Dimension(Integer.MAX_VALUE,96));
        controls.add(titleBlock("Marktansicht",
                "Grün: Käufe · Rot: Verkäufe · Blau: Positionen · Grau: Grid"),BorderLayout.WEST);
        JPanel actions=new JPanel(new FlowLayout(FlowLayout.RIGHT,8,0));actions.setOpaque(false);
        actions.add(new JLabel("Paar"));terminalCurrencyBox.setPreferredSize(new Dimension(125,40));
        actions.add(terminalCurrencyBox);actions.add(new JLabel("Timeframe"));
        terminalTimeframe.setPreferredSize(new Dimension(85,40));actions.add(terminalTimeframe);
        actions.add(new JLabel("Kerzen"));terminalCandleCount.setPreferredSize(new Dimension(85,40));
        actions.add(terminalCandleCount);
        JButton reset=new JButton("Ansicht zurücksetzen");styleSecondaryButton(reset);
        reset.addActionListener(event->tradingChart.resetView());actions.add(reset);
        stylePrimaryButton(terminalRefreshButton);actions.add(terminalRefreshButton);
        controls.add(actions,BorderLayout.EAST);content.add(controls);
        content.add(Box.createVerticalStrut(14));

        JPanel metrics=new JPanel(new GridLayout(1,4,12,0));metrics.setOpaque(false);
        metrics.setAlignmentX(Component.LEFT_ALIGNMENT);
        metrics.setMaximumSize(new Dimension(Integer.MAX_VALUE,106));
        metrics.add(previewSummary("LETZTER PREIS",terminalLastPrice));
        metrics.add(previewSummary("KERZEN",terminalCandles));
        metrics.add(previewSummary("OVERLAYS",terminalOverlays));
        metrics.add(previewSummary("AKTUALISIERT",terminalUpdated));
        content.add(metrics);content.add(Box.createVerticalStrut(14));

        RoundedPanel chart=cardPanel(new BorderLayout(0,10));
        chart.setMaximumSize(new Dimension(Integer.MAX_VALUE,690));
        chart.setPreferredSize(new Dimension(0,690));
        chart.add(titleBlock("Kurschart",
                "Mausrad zum Zoomen, ziehen zum Verschieben, Fadenkreuz für OHLCV"),
                BorderLayout.NORTH);
        tradingChart.setPreferredSize(new Dimension(980,610));chart.add(tradingChart,BorderLayout.CENTER);
        content.add(chart);content.add(Box.createVerticalStrut(14));

        RoundedPanel orderEntry=cardPanel(new BorderLayout(18,12));
        orderEntry.setMaximumSize(new Dimension(Integer.MAX_VALUE,255));
        orderEntry.add(titleBlock("Manuelle Order",
                "Verkäufe schließen immer eine vollständig ausgewählte Position"),BorderLayout.NORTH);
        JPanel orderForm=new JPanel(new GridLayout(1,5,12,0));orderForm.setOpaque(false);
        orderForm.add(compactField("Seite",terminalOrderSide));
        orderForm.add(compactField("Typ",terminalOrderType));
        orderForm.add(compactField("Position",terminalPositionBox));
        orderForm.add(compactField("Menge",terminalOrderQuantity));
        orderForm.add(compactField("Limitpreis",terminalOrderPrice));
        orderEntry.add(orderForm,BorderLayout.CENTER);
        JPanel orderActions=new JPanel(new FlowLayout(FlowLayout.RIGHT,8,0));orderActions.setOpaque(false);
        stylePrimaryButton(terminalSubmitOrder);styleSecondaryButton(terminalReplaceOrder);
        styleSecondaryButton(terminalCancelOrder);
        orderActions.add(terminalCancelOrder);orderActions.add(terminalReplaceOrder);
        orderActions.add(terminalSubmitOrder);orderEntry.add(orderActions,BorderLayout.SOUTH);
        content.add(orderEntry);content.add(Box.createVerticalStrut(14));
        content.add(tableCard("Offene Orders verwalten",
                "Live-Änderungen werden kontrolliert storniert und anschließend neu angelegt",
                terminalOrdersTable,280));
        content.add(Box.createVerticalGlue());return scroll(content);
    }

    private void refreshTradingTerminal(boolean userInitiated){
        if(terminalLoading)return;
        if(!flushAutoSave()||!flushBotAutoSave())return;
        try{
            configureSessionFromFields();
            if(!FusionClientProvider.isConfigured()){
                if(userInitiated)showPage(PAGE_ACCESS,"API-Zugang");
                throw new IllegalStateException(
                        "Für den Trading-Chart zuerst einen Fusion API-Key eingeben.");
            }
            BotProfile bot=selectedBot();String currency=(String)terminalCurrencyBox.getSelectedItem();
            if(bot==null||currency==null)throw new IllegalStateException(
                    "Bot und Handelspaar müssen ausgewählt sein.");
            commitSpinner(terminalCandleCount);
            String timeframe=terminalTimeframe.getSelectedItem().toString();
            int count=((Number)terminalCandleCount.getValue()).intValue();
            terminalLoading=true;terminalRefreshButton.setEnabled(false);
            terminalRefreshButton.setText("Wird geladen …");
            setMessage("Terminal-Marktdaten werden geladen …",false);
            new SwingWorker<TradingTerminalSnapshot,Void>(){
                @Override protected TradingTerminalSnapshot doInBackground()throws Exception{
                    return tradingTerminalService.load(bot.id(),currency,timeframe,count,
                            FusionClientProvider.getClient());
                }
                @Override protected void done(){
                    terminalLoading=false;terminalRefreshButton.setEnabled(true);
                    terminalRefreshButton.setText("Marktdaten laden");
                    try{
                        TradingTerminalSnapshot snapshot=get();tradingChart.setSnapshot(snapshot);
                        terminalSnapshot=snapshot;
                        terminalOrderPrice.setValue(snapshot.lastPrice());
                        long grids=snapshot.priceLines().stream()
                                .filter(line->line.type()==TradingTerminalSnapshot.LineType.GRID).count();
                        long orders=snapshot.priceLines().stream().filter(line->line.type()
                                ==TradingTerminalSnapshot.LineType.BUY_ORDER||line.type()
                                ==TradingTerminalSnapshot.LineType.SELL_ORDER).count();
                        long positions=snapshot.priceLines().stream()
                                .filter(line->line.type()==TradingTerminalSnapshot.LineType.POSITION).count();
                        terminalLastPrice.setText(decimal(snapshot.lastPrice()));
                        terminalCandles.setText(Integer.toString(snapshot.candles().size()));
                        terminalOverlays.setText(grids+" Grid · "+orders+" Orders · "
                                +positions+" Positionen");
                        terminalUpdated.setText(dateTime(snapshot.loadedAt()));
                        setMessage("Trading-Terminal für "+snapshot.currency()+" aktualisiert.",false);
                    }catch(Exception ex){showBackgroundError("Trading-Terminal konnte nicht geladen werden",ex);}
                }
            }.execute();
        }catch(RuntimeException ex){showError("Trading-Terminal konnte nicht geladen werden",ex);}
    }

    private void refreshTerminalOrderControls(){
        OrderSide side=(OrderSide)terminalOrderSide.getSelectedItem();
        OrderType type=(OrderType)terminalOrderType.getSelectedItem();
        boolean sell=side==OrderSide.SELL;
        terminalOrderType.setEnabled(type!=null);
        terminalSubmitOrder.setEnabled(type!=null);
        terminalReplaceOrder.setEnabled(type!=null);
        terminalPositionBox.setEnabled(sell);
        terminalOrderQuantity.setEnabled(!sell);
        terminalOrderPrice.setEnabled(type==OrderType.LIMIT);
        if(!sell)terminalPositionBox.setSelectedItem(null);
    }

    private void refreshTerminalOrderViews(List<OperationsRepository.OpenOrderRow> orders,
            List<OperationsRepository.PositionRow> positions){
        String selectedOrder=selectedTerminalOrder()==null?null:selectedTerminalOrder().orderId();
        terminalOrderRefreshing=true;
        terminalOrderRows=List.copyOf(orders);
        replaceRows(terminalOrdersModel,orders.stream().map(row->new Object[]{
                row.origin(),row.orderSide(),row.currency(),row.orderId(),row.orderType(),
                row.price()>0?decimal(row.price()):"–",decimal(row.quantity()),row.statusText()
        }).toList());
        if(selectedOrder!=null)for(int i=0;i<terminalOrderRows.size();i++)
            if(selectedOrder.equals(terminalOrderRows.get(i).orderId())){
                int view=terminalOrdersTable.convertRowIndexToView(i);
                if(view>=0)terminalOrdersTable.setRowSelectionInterval(view,view);break;
            }
        terminalOrderRefreshing=false;
        String selectedPosition=terminalPositionBox.getSelectedItem() instanceof PositionChoice choice
                ?choice.id():null;
        terminalPositionBox.removeAllItems();
        BotProfile bot=selectedBot();String currency=(String)terminalCurrencyBox.getSelectedItem();
        if(bot!=null&&currency!=null)for(OperationsRepository.PositionRow row:positions){
            boolean paper=row.statusText()!=null&&row.statusText().startsWith("PAPER");
            if(row.currency().equalsIgnoreCase(currency)
                    &&paper==(bot.mode()==BotProfile.Mode.PAPER))
                terminalPositionBox.addItem(new PositionChoice(row.orderId(),row.quantity()));
        }
        if(selectedPosition!=null)for(int i=0;i<terminalPositionBox.getItemCount();i++)
            if(selectedPosition.equals(terminalPositionBox.getItemAt(i).id())){
                terminalPositionBox.setSelectedIndex(i);break;
            }
        refreshTerminalOrderControls();
    }

    private void loadSelectedTerminalOrder(){
        OperationsRepository.OpenOrderRow row=selectedTerminalOrder();if(row==null)return;
        terminalCurrencyBox.setSelectedItem(row.currency());
        terminalOrderSide.setSelectedItem(row.orderSide());
        try{
            OrderType type=OrderType.valueOf(row.orderType());
            if(comboContains(terminalOrderType,type))terminalOrderType.setSelectedItem(type);
        }catch(RuntimeException ignored){ }
        terminalOrderQuantity.setValue(row.quantity());
        if(row.price()>0)terminalOrderPrice.setValue(row.price());
        refreshTerminalOrderControls();
    }

    private OperationsRepository.OpenOrderRow selectedTerminalOrder(){
        int view=terminalOrdersTable.getSelectedRow();if(view<0)return null;
        int model=terminalOrdersTable.convertRowIndexToModel(view);
        return model>=0&&model<terminalOrderRows.size()?terminalOrderRows.get(model):null;
    }

    private ManualOrderService.Request readManualOrderRequest(
            OperationsRepository.OpenOrderRow replacement){
        String currency=(String)terminalCurrencyBox.getSelectedItem();
        OrderSide side=(OrderSide)terminalOrderSide.getSelectedItem();
        OrderType type=(OrderType)terminalOrderType.getSelectedItem();
        double market=terminalSnapshot!=null&&currency!=null
                &&terminalSnapshot.currency().equalsIgnoreCase(currency)
                ?terminalSnapshot.lastPrice():0;
        String position=terminalPositionBox.getSelectedItem() instanceof PositionChoice choice
                ?choice.id():null;
        if(replacement!=null&&side==OrderSide.SELL)position=replacement.referenceId();
        return new ManualOrderService.Request(currency,side,type,number(terminalOrderQuantity),
                number(terminalOrderPrice),market,position);
    }

    private void submitManualOrder(boolean replace){
        if(!flushAutoSave()||!flushBotAutoSave())return;
        try{
            BotProfile bot=selectedBot();if(bot==null)throw new IllegalStateException("Kein Bot ausgewählt.");
            OperationsRepository.OpenOrderRow old=replace?selectedTerminalOrder():null;
            if(replace&&old==null)throw new IllegalArgumentException("Zuerst eine offene Order auswählen.");
            ManualOrderService.PreparedOrder prepared=replace
                    ?manualOrderService.prepareReplacement(bot,old,readManualOrderRequest(old))
                    :manualOrderService.prepare(bot,readManualOrderRequest(null));
            String action=replace?"ersetzen":"aufgeben";
            String warning=bot.mode()==BotProfile.Mode.LIVE
                    ?"LIVE-ORDER: Dies löst eine echte Börsentransaktion aus.\n\n":"Paper-Order\n\n";
            int answer=JOptionPane.showConfirmDialog(this,warning
                    +prepared.request().side()+" "+prepared.request().type()+"\n"
                    +prepared.request().currency()+" · "+prepared.quantityText()+"\n"
                    +"Referenzwert: "+prepared.priceText()+" · ca. "+money(prepared.notional())+"\n\n"
                    +(replace?"Die bestehende Order wird zuerst bestätigt storniert.\n":"")
                    +"Order wirklich "+action+"?","Order bestätigen",
                    JOptionPane.YES_NO_OPTION,bot.mode()==BotProfile.Mode.LIVE
                            ?JOptionPane.WARNING_MESSAGE:JOptionPane.QUESTION_MESSAGE);
            if(answer!=JOptionPane.YES_OPTION)return;
            configureSessionFromFields();
            if(bot.mode()==BotProfile.Mode.LIVE&&!FusionClientProvider.isConfigured()){
                showPage(PAGE_ACCESS,"API-Zugang");
                throw new IllegalStateException("Für Live-Orders zuerst den Fusion API-Key eingeben.");
            }
            setManualOrderBusy(true,"Order wird übermittelt …");
            new SwingWorker<ManualOrderService.Submission,Void>(){
                @Override protected ManualOrderService.Submission doInBackground()throws Exception{
                    FusionApiClient client=bot.mode()==BotProfile.Mode.LIVE
                            ?FusionClientProvider.getClient():null;
                    return replace?manualOrderService.replace(bot,old,prepared,client)
                            :manualOrderService.submit(bot,prepared,client);
                }
                @Override protected void done(){
                    setManualOrderBusy(false,null);
                    try{ManualOrderService.Submission result=get();
                        operationsRepository.recordEvent(bot.id(),"INFO","MANUELLE ORDER",
                                prepared.request().currency(),(replace?"Order ersetzt: ":"Order angelegt: ")
                                        +result.side()+" "+result.type()+" "+value(result.orderId()));
                        setMessage("Manuelle "+result.mode()+"-Order: "+result.status(),false);
                        refreshOperationalViews();refreshDashboard();refreshTradingTerminal(false);
                    }catch(Exception ex){showBackgroundError("Manuelle Order fehlgeschlagen",ex);}
                }
            }.execute();
        }catch(Exception ex){showError("Orderprüfung fehlgeschlagen",ex);}
    }

    private void cancelSelectedOrder(){
        OperationsRepository.OpenOrderRow row=selectedTerminalOrder();
        if(row==null){showError("Storno nicht möglich",new IllegalArgumentException(
                "Zuerst eine offene Order auswählen."));return;}
        BotProfile bot=selectedBot();
        String warning=row.origin()==OperationsRepository.OrderOrigin.LIVE
                ?"LIVE-ORDER wirklich bei Fusion stornieren?":"Paper-Order wirklich stornieren?";
        if(JOptionPane.showConfirmDialog(this,warning+"\n\n"+row.currency()+" · "+row.orderId(),
                "Storno bestätigen",JOptionPane.YES_NO_OPTION,JOptionPane.WARNING_MESSAGE)
                !=JOptionPane.YES_OPTION)return;
        try{
            configureSessionFromFields();
            if(row.origin()==OperationsRepository.OrderOrigin.LIVE
                    &&!FusionClientProvider.isConfigured()){
                showPage(PAGE_ACCESS,"API-Zugang");
                throw new IllegalStateException("Für Live-Stornos zuerst den Fusion API-Key eingeben.");
            }
            setManualOrderBusy(true,"Storno wird geprüft …");
            new SwingWorker<ManualOrderService.CancelResult,Void>(){
                @Override protected ManualOrderService.CancelResult doInBackground()throws Exception{
                    return manualOrderService.cancel(bot,row,
                            row.origin()==OperationsRepository.OrderOrigin.LIVE
                                    ?FusionClientProvider.getClient():null);
                }
                @Override protected void done(){setManualOrderBusy(false,null);try{
                    ManualOrderService.CancelResult result=get();
                    operationsRepository.recordEvent(bot.id(),result.clean()?"INFO":"WARN",
                            "ORDER-STORNO",row.currency(),result.message()+" "+row.orderId());
                    setMessage(result.message(),!result.clean());refreshOperationalViews();
                    refreshDashboard();refreshTradingTerminal(false);
                }catch(Exception ex){showBackgroundError("Order konnte nicht storniert werden",ex);}}
            }.execute();
        }catch(Exception ex){setManualOrderBusy(false,null);showError("Storno nicht möglich",ex);}
    }

    private void setManualOrderBusy(boolean busy,String text){
        terminalSubmitOrder.setEnabled(!busy);terminalReplaceOrder.setEnabled(!busy);
        terminalCancelOrder.setEnabled(!busy);botBox.setEnabled(!busy);
        terminalCurrencyBox.setEnabled(!busy);terminalOrderSide.setEnabled(!busy);
        terminalOrderType.setEnabled(!busy);terminalPositionBox.setEnabled(!busy);
        terminalOrderQuantity.setEnabled(!busy);terminalOrderPrice.setEnabled(!busy);
        if(text!=null)setMessage(text,false);
        if(!busy)refreshTerminalOrderControls();
    }

    private RoundedPanel createBaseConfigPanel() {
        RoundedPanel panel = cardPanel(new BorderLayout(0, 16));
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 570));
        JPanel header = new JPanel(new BorderLayout(12, 0));
        header.setOpaque(false);
        header.add(titleBlock("Baseconfig und Config Pools",
                "Pools überschreiben diese Ausführungsregeln für zugeordnete Paare"), BorderLayout.WEST);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actions.setOpaque(false);
        configEditorBox.setPreferredSize(new Dimension(190, 38));
        actions.add(configEditorBox);
        JButton create = new JButton("Pool anlegen");
        styleSecondaryButton(create);
        create.addActionListener(event -> createConfigPool());
        actions.add(create);
        JButton remove = new JButton("Pool löschen");
        styleSecondaryButton(remove);
        remove.setForeground(OneOfXTheme.ERROR);
        remove.addActionListener(event -> archiveConfigPool());
        actions.add(remove);
        header.add(actions, BorderLayout.EAST);
        panel.add(header, BorderLayout.NORTH);

        JPanel columns = new JPanel(new GridLayout(1, 3, 14, 0));
        columns.setOpaque(false);
        JPanel orders = modernForm();
        addFormRow(orders, 0, "Kauf-Ordertyp", baseBuyOrderType, null);
        addFormRow(orders, 1, "Verkauf-Ordertyp", baseSellOrderType, null);
        addFormRow(orders, 2, "Kauforder-Laufzeit", baseBuyOrderMinutes, "Min");
        addFormRow(orders, 3, "Verkaufsorder-Laufzeit", baseSellOrderMinutes, "Min");
        addFormRow(orders, 4, "Cooldown", baseCooldownMinutes, "Min");
        addFormRow(orders, 5, "Take Profit", baseTakeProfit, "%");
        columns.add(orders);

        JPanel exits = modernForm();
        addFormRow(exits, 0, "Trailing Stop-Buy", baseTrailingBuy, null);
        addFormRow(exits, 1, "Drop-Aktivierung", baseTrailingBuyActivation, "%");
        addFormRow(exits, 2, "Rebound", baseTrailingBuyRebound, "%");
        addFormRow(exits, 3, "Gewinnbedingung", baseOnlyProfit, null);
        addFormRow(exits, 4, "Zeitlimit", baseCloseAfterEnabled, null);
        addFormRow(exits, 5, "Position schließen nach", baseCloseAfter, "Min");
        columns.add(exits);

        JPanel dca = modernForm();
        addFormRow(dca, 0, "DCA", baseDcaEnabled, null);
        addFormRow(dca, 1, "Max. Nachkäufe", baseDcaMaxOrders, null);
        addFormRow(dca, 2, "DCA-Trigger", baseDcaTrigger, "%");
        addFormRow(dca, 3, "Order-Multiplikator", baseDcaMultiplier, "×");
        columns.add(dca);
        panel.add(columns, BorderLayout.CENTER);
        baseOrderTypeInfo.setForeground(OneOfXTheme.TEXT_MUTED);
        baseOrderTypeInfo.setFont(OneOfXTheme.font(Font.PLAIN, 11));
        panel.add(baseOrderTypeInfo, BorderLayout.SOUTH);
        return panel;
    }

    private JScrollPane createOrdersPage() {
        JPanel content = pageContent();
        content.add(pageHeading("Orders im Blick",
                "Offene Kauf- und Verkaufsorders sowie ungeklärte Übermittlungen."));
        content.add(Box.createVerticalStrut(18));

        JPanel metrics = new JPanel(new GridLayout(1, 1, 14, 0));
        metrics.setOpaque(false);
        metrics.setAlignmentX(Component.LEFT_ALIGNMENT);
        metrics.setMaximumSize(new Dimension(Integer.MAX_VALUE, 118));
        metrics.add(metricCard("OFFENE ORDERS", openOrdersCount, IconType.CLOCK,
                OneOfXTheme.PRIMARY, "Lokal überwacht"));
        content.add(metrics);
        content.add(Box.createVerticalStrut(16));
        content.add(operationalTableCard("Offene Orders", "Kauf- und Verkaufsorders aus SQLite",
                openOrdersTable, 340, "Eine Order auswählen, um Details zu sehen.",
                "Im Terminal verwalten", () -> openTerminalForSelectedRow(openOrdersTable)));
        content.add(Box.createVerticalGlue());
        return scroll(content);
    }

    private JScrollPane createUnresolvedPage() {
        JPanel content = pageContent();
        content.add(pageHeading("Ungeklärte Übermittlungen",
                "Orderversuche mit Prüfbedarf werden nicht automatisch aufgelöst."));
        content.add(Box.createVerticalStrut(18));
        JPanel metrics = new JPanel(new GridLayout(1, 1));
        metrics.setOpaque(false);
        metrics.setAlignmentX(Component.LEFT_ALIGNMENT);
        metrics.setMaximumSize(new Dimension(Integer.MAX_VALUE, 118));
        metrics.add(metricCard("ABGLEICH NÖTIG", unresolvedCount, IconType.SHIELD,
                OneOfXTheme.ERROR, "Vor dem nächsten Start prüfen"));
        content.add(metrics);
        content.add(Box.createVerticalStrut(16));
        content.add(operationalTableCard("Ungeklärte Übermittlungen",
                "SUBMITTING oder RECONCILIATION_REQUIRED", unresolvedTable, 430,
                "Einen Übermittlungsversuch auswählen, um Details zu sehen.",
                "Aktivität öffnen", () -> showPage(PAGE_ACTIVITY, "Aktivität")));
        content.add(Box.createVerticalGlue());
        return scroll(content);
    }

    private JScrollPane createPositionsPage() {
        JPanel content = pageContent();
        content.add(pageHeading("Positionen",
                "Offene Bestände, Kapitalbindung und lokaler Ergebnisstatus."));
        content.add(Box.createVerticalStrut(18));
        JPanel metrics = new JPanel(new GridLayout(1, 1));
        metrics.setOpaque(false);
        metrics.setAlignmentX(Component.LEFT_ALIGNMENT);
        metrics.setMaximumSize(new Dimension(Integer.MAX_VALUE, 118));
        metrics.add(metricCard("AKTIVE POSITIONEN", positionsViewCount, IconType.CHART,
                OneOfXTheme.INFO, "Status 1, 5, 7 oder 8"));
        content.add(metrics);
        content.add(Box.createVerticalStrut(16));
        content.add(operationalTableCard("Positionsübersicht",
                "Realisierte Werte werden erst nach bestätigter Ausführung gebucht",
                positionsTable, 430, "Eine Position auswählen, um Details zu sehen.",
                "Im Terminal öffnen", () -> openTerminalForSelectedRow(positionsTable)));
        content.add(Box.createVerticalStrut(16));
        portfolioPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        portfolioPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE,620));
        portfolioPanel.setPreferredSize(new Dimension(0,620));
        content.add(portfolioPanel);
        content.add(Box.createVerticalGlue());
        return scroll(content);
    }

    private JScrollPane createBalancesPage() {
        JPanel content = pageContent();
        content.add(pageHeading("Kontostände",
                "Aktuelle verfügbare und durch Fusion gesperrte Asset-Bestände."));
        content.add(Box.createVerticalStrut(18));

        RoundedPanel controls = cardPanel(new BorderLayout(16, 0));
        controls.setMaximumSize(new Dimension(Integer.MAX_VALUE, 88));
        controls.add(titleBlock("Fusion-Konto",
                "Wird nur auf Anforderung geladen und nicht lokal gespeichert"),
                BorderLayout.WEST);
        JButton refresh = new JButton("Kontostände laden");
        stylePrimaryButton(refresh);
        refresh.addActionListener(event -> refreshBalances(refresh));
        controls.add(refresh, BorderLayout.EAST);
        content.add(controls);
        content.add(Box.createVerticalStrut(16));
        content.add(operationalTableCard("Asset-Bestände", "Nullbestände werden ausgeblendet",
                balancesTable, 430, "Ein Asset auswählen, um Details zu sehen.", null, null));
        content.add(Box.createVerticalGlue());
        return scroll(content);
    }

    private JScrollPane createActivityPage() {
        JPanel content = pageContent();
        content.add(pageHeading("Aktivität und Warnungen",
                "Lokale Ereignisse, API-Probleme und Zustände mit Prüfbedarf."));
        content.add(Box.createVerticalStrut(18));
        JPanel metrics = new JPanel(new GridLayout(1, 1));
        metrics.setOpaque(false);
        metrics.setAlignmentX(Component.LEFT_ALIGNMENT);
        metrics.setMaximumSize(new Dimension(Integer.MAX_VALUE, 118));
        metrics.add(metricCard("AKTIVE WARNUNGEN", warningsCount, IconType.SHIELD,
                OneOfXTheme.ERROR, "Kritische Zustände zuerst prüfen"));
        content.add(metrics);
        content.add(Box.createVerticalStrut(16));
        content.add(operationalTableCard("Warnzentrale",
                "Orderabgleich, Handelsregeln und fehlende Marktdaten",
                warningTable, 280, "Eine Warnung auswählen, um Details zu sehen.",
                "Hinweise öffnen", () -> showPage(PAGE_ACTIVITY, "Aktivität")));
        content.add(Box.createVerticalStrut(16));
        content.add(operationalTableCard("Aktivitätsprotokoll", "Neueste Ereignisse zuerst",
                activityTable, 350, "Ein Ereignis auswählen, um Details zu sehen.", null, null));
        content.add(Box.createVerticalGlue());
        return scroll(content);
    }

    private JScrollPane createSettings() {
        JPanel content = pageContent();
        content.add(pageHeading(
                "Strategie konfigurieren",
                "Kapital, Grid und Risikoschutz für jedes Handelspaar."));
        content.add(Box.createVerticalStrut(22));

        RoundedPanel selection = cardPanel(new BorderLayout(16, 0));
        selection.setMaximumSize(new Dimension(Integer.MAX_VALUE, 150));
        JPanel selectText = titleBlock("Handelspaar",
                "Geprüfte Paare verwalten · Änderungen speichern automatisch");
        selection.add(selectText, BorderLayout.WEST);

        JPanel actions = new JPanel();
        actions.setOpaque(false);
        actions.setLayout(new BoxLayout(actions, BoxLayout.Y_AXIS));
        JPanel selectors = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        selectors.setOpaque(false);
        currencyBox.setPreferredSize(new Dimension(190, 40));
        OneOfXTheme.round(currencyBox);
        selectors.add(currencyBox);

        pairPoolBox.setPreferredSize(new Dimension(180, 40));
        pairPoolBox.setToolTipText("Config Pool für dieses Handelspaar");
        OneOfXTheme.round(pairPoolBox);
        selectors.add(pairPoolBox);
        actions.add(selectors);
        actions.add(Box.createVerticalStrut(6));
        JPanel pairActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        pairActions.setOpaque(false);

        JButton refreshRulesButton = new JButton("Regeln aktualisieren");
        styleSecondaryButton(refreshRulesButton);
        refreshRulesButton.setToolTipText(
                "Trading-Regeln und erlaubte Ordertypen erneut von Fusion laden");
        refreshRulesButton.addActionListener(event -> refreshSelectedPairRules(refreshRulesButton));
        pairActions.add(refreshRulesButton);

        JButton addButton = new JButton("Paar hinzufügen");
        styleSecondaryButton(addButton);
        addButton.setForeground(OneOfXTheme.PRIMARY);
        addButton.addActionListener(event -> addCurrency(addButton));
        pairActions.add(addButton);

        JButton removeButton = new JButton("Entfernen");
        styleSecondaryButton(removeButton);
        removeButton.setForeground(OneOfXTheme.ERROR);
        removeButton.addActionListener(event -> removeCurrency());
        pairActions.add(removeButton);
        actions.add(pairActions);
        selection.add(actions, BorderLayout.EAST);
        pairSupportedOrderTypes.setForeground(OneOfXTheme.TEXT_MUTED);
        pairSupportedOrderTypes.setFont(OneOfXTheme.font(Font.PLAIN, 11));
        pairSupportedOrderTypes.setBorder(OneOfXTheme.padding(8, 0, 0, 0));
        selection.add(pairSupportedOrderTypes, BorderLayout.SOUTH);
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
        content.add(createGridPreviewCard());
        content.add(Box.createVerticalStrut(12));

        JPanel saveRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        saveRow.setOpaque(false);
        saveRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        saveRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 46));
        configureSaveStatus(settingsSaveStatus);
        saveRow.add(settingsSaveStatus);
        content.add(saveRow);
        content.add(Box.createVerticalGlue());
        return scroll(content);
    }

    private RoundedPanel createGridPreviewCard() {
        RoundedPanel card = cardPanel(new BorderLayout(0, 14));
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 430));

        JPanel header = new JPanel(new BorderLayout(18, 0));
        header.setOpaque(false);
        header.add(titleBlock("Grid-Vorschau",
                "Reine Planung – diese Tabelle sendet keine Orders"), BorderLayout.WEST);
        JPanel priceControls = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        priceControls.setOpaque(false);
        JLabel priceLabel = new JLabel("Startpreis");
        priceLabel.setForeground(OneOfXTheme.TEXT_MUTED);
        priceControls.add(priceLabel);
        previewPrice.setPreferredSize(new Dimension(145, 38));
        priceControls.add(previewPrice);
        JButton livePrice = new JButton("Livekurs laden");
        styleSecondaryButton(livePrice);
        livePrice.addActionListener(event -> refreshPreviewPrice(livePrice));
        priceControls.add(livePrice);
        header.add(priceControls, BorderLayout.EAST);
        card.add(header, BorderLayout.NORTH);

        JPanel summary = new JPanel(new GridLayout(1, 3, 12, 0));
        summary.setOpaque(false);
        summary.add(previewSummary("ORDERS", previewOrders));
        summary.add(previewSummary("KAPITAL", previewCapital));
        summary.add(previewSummary("PREISBAND", previewBand));

        JPanel center = new JPanel(new BorderLayout(0, 12));
        center.setOpaque(false);
        center.add(summary, BorderLayout.NORTH);
        JScrollPane tableScroll = new JScrollPane(gridPreviewTable);
        tableScroll.setBorder(BorderFactory.createLineBorder(OneOfXTheme.BORDER));
        tableScroll.setPreferredSize(new Dimension(0, 220));
        center.add(tableScroll, BorderLayout.CENTER);
        card.add(center, BorderLayout.CENTER);

        previewWarning.setForeground(OneOfXTheme.TEXT_MUTED);
        previewWarning.setFont(OneOfXTheme.font(Font.PLAIN, 11));
        card.add(previewWarning, BorderLayout.SOUTH);
        return card;
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
        String rootPage = page;
        String rootTitle = title;
        switch (page) {
            case PAGE_SETTINGS -> {
                rootPage = PAGE_BOTS;
                rootTitle = "Bots";
                botTabs.setSelectedIndex(1);
            }
            case PAGE_STRATEGIES -> {
                rootPage = PAGE_BOTS;
                rootTitle = "Bots";
                botTabs.setSelectedIndex(2);
            }
            case PAGE_TERMINAL -> {
                rootPage = PAGE_TRADE;
                rootTitle = "Handel";
                tradingTabs.setSelectedIndex(0);
            }
            case PAGE_POSITIONS -> {
                rootPage = PAGE_TRADE;
                rootTitle = "Handel";
                tradingTabs.setSelectedIndex(1);
            }
            case PAGE_ORDERS -> {
                rootPage = PAGE_TRADE;
                rootTitle = "Handel";
                tradingTabs.setSelectedIndex(2);
            }
            case PAGE_BALANCES -> {
                rootPage = PAGE_TRADE;
                rootTitle = "Handel";
                tradingTabs.setSelectedIndex(4);
            }
            case PAGE_ACTIVITY -> {
                rootPage = PAGE_SYSTEM;
                rootTitle = "System";
                systemTabs.setSelectedIndex(0);
            }
            case PAGE_ACCESS -> {
                rootPage = PAGE_SYSTEM;
                rootTitle = "System";
                systemTabs.setSelectedIndex(1);
            }
            default -> { }
        }
        pageLayout.show(pages, rootPage);
        pageTitle.setText(rootTitle);
        String selectedPage = rootPage;
        navigation.forEach((key, button) -> button.setActive(key.equals(selectedPage)));
    }

    private void reloadStrategies(Long selectedId) {
        if (loadedBot == null) return;
        loadingStrategy = true;
        try {
            strategyRepository.ensureExampleStrategy(loadedBot.id());
            long wanted = selectedId != null ? selectedId
                    : loadedStrategy == null ? -1 : loadedStrategy.id();
            strategyLibraryModel.clear();
            backtestStrategyBox.removeAllItems();
            backtestOptimizationStrategyModel.clear();
            StrategyDefinition selected = null;
            for (StrategyDefinition strategy : strategyRepository.loadAll(loadedBot.id())) {
                strategyLibraryModel.addElement(strategy);
                backtestStrategyBox.addItem(strategy);
                backtestOptimizationStrategyModel.addElement(strategy);
                if (strategy.id() == wanted) selected = strategy;
            }
            if (selected == null && !strategyLibraryModel.isEmpty()) selected = strategyLibraryModel.get(0);
            if (selected != null) {
                strategyLibrary.setSelectedValue(selected, true);backtestStrategyBox.setSelectedItem(selected);
                for(int i=0;i<backtestOptimizationStrategyModel.size();i++)
                    if(backtestOptimizationStrategyModel.get(i).id()==selected.id())
                        backtestOptimizationStrategies.setSelectedIndex(i);
            }
            loadedStrategy = selected;
            populateStrategy(selected);
            reloadAssignmentTargets();
        } catch (SQLException ex) {
            showError("Strategien konnten nicht geladen werden", ex);
        } finally {
            loadingStrategy = false;
        }
        refreshStrategyAssignmentInfo();
        reloadBacktestHistory(null);
    }

    private void populateStrategy(StrategyDefinition strategy) throws SQLException {
        boolean present = strategy != null;
        strategyName.setEnabled(present);
        strategyConfirmations.setEnabled(present);
        strategyEnabled.setEnabled(present);
        strategyAssignment.setEnabled(present);
        strategySpacingMode.setEnabled(present);
        strategySpacing.setEnabled(present);
        if (present) {
            strategyName.setText(strategy.name());
            strategyConfirmations.setValue(strategy.minimumConfirmations());
            strategyEnabled.setSelected(strategy.enabled());
            strategySpacingMode.setSelectedItem(strategy.entrySpacingMode());
            strategySpacing.setValue(strategy.entrySpacing());
        } else {
            strategyName.setText("");
            strategyConfirmations.setValue(1);
            strategyEnabled.setSelected(false);
            strategySpacingMode.setSelectedItem(EntrySpacingMode.PERCENT);
            strategySpacing.setValue(1.0);
        }
        refreshStrategySpacingPreview();
        strategySaveDirty = false;
        lastTestedStrategy = null;
        lastTestedCurrency = null;
        strategyBacktestButton.setEnabled(false);
        strategyTestStatus.setText(present
                ? "Noch kein Markttest für diese Strategie durchgeführt."
                : "Keine Strategie ausgewählt.");
        strategyTestStatus.setForeground(OneOfXTheme.TEXT_MUTED);
        strategyTestStatus.setToolTipText(null);
        strategySaveStatus.setText(present ? "Gespeichert" : "Keine Strategie ausgewählt");
        strategySaveStatus.setForeground(present ? OneOfXTheme.SUCCESS : OneOfXTheme.TEXT_MUTED);
        reloadStrategyNodes();
    }

    private void switchStrategy() {
        if (loadingStrategy) return;
        if (!flushStrategyAutoSave()) {
            loadingStrategy = true;
            strategyLibrary.setSelectedValue(loadedStrategy, true);
            loadingStrategy = false;
            return;
        }
        StrategyDefinition selected = strategyLibrary.getSelectedValue();
        loadingStrategy = true;
        try {
            loadedStrategy = selected;
            populateStrategy(selected);
        } catch (SQLException ex) {
            showError("Strategie konnte nicht geladen werden", ex);
        } finally {
            loadingStrategy = false;
        }
    }

    private void createStrategy() {
        if (loadedBot == null || !flushStrategyAutoSave()) return;
        String name = JOptionPane.showInputDialog(this, "Name der neuen Strategie:",
                "Strategie anlegen", JOptionPane.PLAIN_MESSAGE);
        if (name == null) return;
        try {
            StrategyDefinition created = strategyRepository.createWithDefaultBuyGroup(
                    loadedBot.id(), name.trim());
            reloadStrategies(created.id());
            setMessage("Strategie „" + created.name() + "“ angelegt.", false);
        } catch (SQLException | RuntimeException ex) {
            showError("Strategie konnte nicht angelegt werden", ex);
        }
    }

    private void duplicateStrategy() {
        if (loadedStrategy == null || !flushStrategyAutoSave()) return;
        String name = JOptionPane.showInputDialog(this, "Name der Kopie:",
                loadedStrategy.name() + " – Kopie");
        if (name == null) return;
        try {
            StrategyDefinition copy = strategyRepository.duplicate(loadedStrategy.id(), name.trim());
            reloadStrategies(copy.id());
            setMessage("Strategie dupliziert.", false);
        } catch (SQLException | RuntimeException ex) {
            showError("Strategie konnte nicht dupliziert werden", ex);
        }
    }

    private void archiveStrategy() {
        if (loadedStrategy == null) return;
        int answer = JOptionPane.showConfirmDialog(this,
                "Strategie „" + loadedStrategy.name() + "“ samt Regelbaum löschen?",
                "Strategie löschen", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (answer != JOptionPane.YES_OPTION) return;
        try {
            strategyRepository.archive(loadedBot.id(), loadedStrategy.id());
            loadedStrategy = null;
            reloadStrategies(null);
            setMessage("Strategie gelöscht und Zuweisungen gelöst.", false);
        } catch (SQLException ex) {
            showError("Strategie konnte nicht gelöscht werden", ex);
        }
    }

    private void reloadStrategyNodes() throws SQLException {
        if (loadedStrategy == null) {
            visibleStrategyNodes = List.of();
            strategyRulesModel.setRowCount(0);
            return;
        }
        List<StrategyNode> nodes = strategyRepository.loadNodes(loadedStrategy.id());
        if (nodes.isEmpty()) {
            strategyRepository.ensureDefaultBuyGroup(loadedStrategy.id());
            nodes = strategyRepository.loadNodes(loadedStrategy.id());
            setMessage("Leere Strategie repariert: Kaufbereich wurde angelegt.", false);
        }
        Map<Long, List<StrategyNode>> children = new HashMap<>();
        List<StrategyNode> roots = new ArrayList<>();
        for (StrategyNode node : nodes) {
            if (node.parentId() == null) roots.add(node);
            else children.computeIfAbsent(node.parentId(), key -> new ArrayList<>()).add(node);
        }
        java.util.Comparator<StrategyNode> order = java.util.Comparator
                .comparingInt(StrategyNode::position).thenComparingLong(StrategyNode::id);
        roots.sort(order);
        children.values().forEach(list -> list.sort(order));
        List<StrategyNode> flattened = new ArrayList<>();
        List<Integer> depths = new ArrayList<>();
        for (StrategyNode root : roots) flattenStrategy(root, 0, children, flattened, depths);
        visibleStrategyNodes = List.copyOf(flattened);
        strategyRulesModel.setRowCount(0);
        for (int i = 0; i < flattened.size(); i++) {
            StrategyNode node = flattened.get(i);
            String indent = "    ".repeat(Math.min(depths.get(i), 8));
            String tree = indent + (node.type() == NodeType.GROUP ? "▾ Regelgruppe" : "• Regel");
            String rule = node.type() == NodeType.GROUP ? logicLabel(node.logic())
                    : indicatorLabel(node.leftIndicator());
            String compare = node.type() == NodeType.GROUP ? "–"
                    : comparatorLabel(node.comparator());
            String target = node.type() == NodeType.GROUP ? "–"
                    : node.rightIndicator() == Indicator.VALUE ? decimal(node.compareValue())
                    : indicatorLabel(node.rightIndicator());
            strategyRulesModel.addRow(new Object[] {tree, actionLabel(node.action()), rule, compare, target,
                    node.type() == NodeType.GROUP ? "–" : node.timeframe(),
                    node.type() == NodeType.GROUP ? "–" : node.period() + " / " + node.secondaryPeriod()});
        }
    }

    private static void flattenStrategy(StrategyNode node, int depth,
            Map<Long, List<StrategyNode>> children, List<StrategyNode> target,
            List<Integer> depths) {
        target.add(node);
        depths.add(depth);
        for (StrategyNode child : children.getOrDefault(node.id(), List.of()))
            flattenStrategy(child, depth + 1, children, target, depths);
    }

    private StrategyNode selectedStrategyNode() {
        int row = strategyRulesTable.getSelectedRow();
        if (row < 0 || row >= visibleStrategyNodes.size()) return null;
        return visibleStrategyNodes.get(strategyRulesTable.convertRowIndexToModel(row));
    }

    private void selectStrategyNode(long nodeId) {
        for (int i = 0; i < visibleStrategyNodes.size(); i++) {
            if (visibleStrategyNodes.get(i).id() == nodeId) {
                strategyRulesTable.setRowSelectionInterval(i, i);
                strategyRulesTable.scrollRectToVisible(strategyRulesTable.getCellRect(i, 0, true));
                return;
            }
        }
    }

    private StrategyNode selectedParent() {
        StrategyNode selected = selectedStrategyNode();
        if (selected == null) return null;
        if (selected.type() == NodeType.GROUP) return selected;
        if (selected.parentId() == null) return null;
        return visibleStrategyNodes.stream().filter(node -> node.id() == selected.parentId())
                .findFirst().orElse(null);
    }

    private void addStrategyGroup() {
        if (loadedStrategy == null) return;
        StrategyNode parent = selectedParent();
        Action action = parent == null ? chooseAction(Action.BUY) : parent.action();
        if (action == null) return;
        Logic logic = chooseLogic(Logic.AND, "Untergruppe hinzufügen");
        if (logic == null) return;
        try {
            StrategyNode created = strategyRepository.addGroup(loadedStrategy.id(),
                    parent == null ? null : parent.id(),
                    action, logic);
            reloadStrategyNodes();
            selectStrategyNode(created.id());
            setMessage("Untergruppe gespeichert.", false);
        } catch (SQLException ex) {
            showError("Gruppe konnte nicht gespeichert werden", ex);
        }
    }

    private void addStrategyCondition(Action requestedAction) {
        if (loadedStrategy == null) return;
        StrategyNode parent = selectedParent();
        if (parent == null || parent.action() != requestedAction) {
            parent = visibleStrategyNodes.stream()
                    .filter(node -> node.parentId() == null && node.type() == NodeType.GROUP
                            && node.action() == requestedAction)
                    .findFirst().orElse(null);
        }
        StrategyNode draft = editConditionDialog(new StrategyNode(0, loadedStrategy.id(),
                parent == null ? null : parent.id(), 0, NodeType.CONDITION,
                requestedAction, Logic.AND,
                Indicator.RSI, Comparator.LESS_THAN, Indicator.VALUE, 30, "1h", 14, 26));
        if (draft == null) return;
        try {
            if (parent == null) {
                parent = strategyRepository.addGroup(loadedStrategy.id(), null,
                        requestedAction, Logic.AND);
            }
            StrategyNode created = strategyRepository.addCondition(
                    draft.strategyId(), parent.id(), draft.action(),
                    draft.leftIndicator(), draft.comparator(), draft.rightIndicator(),
                    draft.compareValue(), draft.timeframe(), draft.period(), draft.secondaryPeriod());
            reloadStrategyNodes();
            selectStrategyNode(created.id());
            setMessage(actionLabel(requestedAction) + "-Regel gespeichert.", false);
        } catch (SQLException | RuntimeException ex) {
            showError("Bedingung konnte nicht gespeichert werden", ex);
        }
    }

    private void editStrategyNode() {
        StrategyNode node = selectedStrategyNode();
        if (node == null) return;
        try {
            StrategyNode changed;
            if (node.type() == NodeType.GROUP) {
                Logic logic = chooseLogic(node.logic(), "Untergruppe bearbeiten");
                if (logic == null) return;
                Action action = node.parentId() == null ? chooseAction(node.action()) : node.action();
                if (action == null) return;
                changed = new StrategyNode(node.id(), node.strategyId(), node.parentId(), node.position(),
                        node.type(), action, logic, null, null, null, 0, node.timeframe(),
                        node.period(), node.secondaryPeriod());
            } else {
                changed = editConditionDialog(node);
                if (changed == null) return;
            }
            strategyRepository.updateNode(changed);
            reloadStrategyNodes();
        } catch (SQLException | RuntimeException ex) {
            showError("Regel konnte nicht geändert werden", ex);
        }
    }

    private StrategyNode editConditionDialog(StrategyNode node) {
        Indicator[] usableIndicators = java.util.Arrays.stream(Indicator.values())
                .filter(indicator -> indicator != Indicator.VALUE).toArray(Indicator[]::new);
        JComboBox<Indicator> left = new JComboBox<>(usableIndicators);
        JComboBox<Comparator> comparator = new JComboBox<>(Comparator.values());
        JComboBox<Indicator> right = new JComboBox<>(Indicator.values());
        JComboBox<String> timeframe = new JComboBox<>(new String[] {"1m", "5m", "10m", "15m", "30m", "1h", "4h", "1d"});
        JSpinner value = decimalSpinner(node.compareValue(), -1_000_000_000, 1_000_000_000, 0.1);
        JSpinner period = new JSpinner(new SpinnerNumberModel(node.period(), 1, 300, 1));
        JSpinner secondary = new JSpinner(new SpinnerNumberModel(node.secondaryPeriod(), 1, 300, 1));
        useFriendlyNames(left, OneOfXFrame::indicatorLabel);
        useFriendlyNames(right, OneOfXFrame::indicatorLabel);
        useFriendlyNames(comparator, OneOfXFrame::comparatorLabel);
        left.setSelectedItem(node.leftIndicator());
        comparator.setSelectedItem(node.comparator());
        right.setSelectedItem(node.rightIndicator());
        timeframe.setSelectedItem(node.timeframe());
        Runnable updateFields = () -> {
            value.setEnabled(right.getSelectedItem() == Indicator.VALUE);
            Indicator leftValue = (Indicator) left.getSelectedItem();
            Indicator rightValue = (Indicator) right.getSelectedItem();
            secondary.setEnabled(leftValue == Indicator.MACD
                    || leftValue == Indicator.MACD_SIGNAL
                    || rightValue == Indicator.MACD
                    || rightValue == Indicator.MACD_SIGNAL);
        };
        left.addActionListener(event -> updateFields.run());
        right.addActionListener(event -> updateFields.run());
        updateFields.run();
        JPanel form = modernForm();
        addFormRow(form, 0, "Indikator", left, null);
        addFormRow(form, 1, "Bedingung", comparator, null);
        addFormRow(form, 2, "Vergleichen mit", right, null);
        addFormRow(form, 3, "Fester Wert", value, null);
        addFormRow(form, 4, "Zeitraum", timeframe, null);
        addFormRow(form, 5, "Periode", period, null);
        addFormRow(form, 6, "Zweite Periode", secondary, "nur MACD");
        JPanel dialog = new JPanel(new BorderLayout(0, 12));
        dialog.setOpaque(false);
        JLabel explanation = new JLabel("<html><b>" + actionLabel(node.action())
                + "</b> – Beispiel: RSI ist kleiner als fester Wert 30.</html>");
        explanation.setForeground(OneOfXTheme.TEXT_SECONDARY);
        dialog.add(explanation, BorderLayout.NORTH);
        dialog.add(form, BorderLayout.CENTER);
        int answer = JOptionPane.showConfirmDialog(this, dialog, "Regel bearbeiten",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (answer != JOptionPane.OK_OPTION) return null;
        commitSpinner(value);commitSpinner(period);commitSpinner(secondary);
        return new StrategyNode(node.id(), node.strategyId(), node.parentId(), node.position(),
                NodeType.CONDITION, node.action(), Logic.AND, (Indicator) left.getSelectedItem(),
                (Comparator) comparator.getSelectedItem(), (Indicator) right.getSelectedItem(),
                number(value), timeframe.getSelectedItem().toString(),
                ((Number) period.getValue()).intValue(), ((Number) secondary.getValue()).intValue());
    }

    private Action chooseAction(Action initial) {
        String[] labels = java.util.Arrays.stream(Action.values())
                .map(OneOfXFrame::actionLabel).toArray(String[]::new);
        String selected = (String) JOptionPane.showInputDialog(this,
                "Was soll passieren, wenn die Regeln zutreffen?", "Signal auswählen",
                JOptionPane.PLAIN_MESSAGE, null, labels, actionLabel(initial));
        if (selected == null) return null;
        return java.util.Arrays.stream(Action.values())
                .filter(action -> actionLabel(action).equals(selected)).findFirst().orElse(initial);
    }

    private Logic chooseLogic(Logic initial, String title) {
        String and = logicLabel(Logic.AND);
        String or = logicLabel(Logic.OR);
        String selected = (String) JOptionPane.showInputDialog(this,
                "Wie sollen die Regeln dieser Gruppe verknüpft werden?", title,
                JOptionPane.PLAIN_MESSAGE, null, new String[] {and, or}, logicLabel(initial));
        if (selected == null) return null;
        return selected.equals(or) ? Logic.OR : Logic.AND;
    }

    private void deleteStrategyNode() {
        StrategyNode node = selectedStrategyNode();
        if (node == null) return;
        int answer = JOptionPane.showConfirmDialog(this,
                node.type() == NodeType.GROUP
                        ? "Gruppe einschließlich aller Unterregeln entfernen?"
                        : "Bedingung entfernen?",
                "Regel entfernen", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (answer != JOptionPane.YES_OPTION) return;
        try {
            strategyRepository.deleteNode(node.id());
            reloadStrategyNodes();
        } catch (SQLException ex) {
            showError("Regel konnte nicht entfernt werden", ex);
        }
    }

    private void reloadAssignmentTargets() throws SQLException {
        strategyAssignment.removeAllItems();
        if (loadedBot == null) return;
        strategyAssignment.addItem(new AssignmentTarget(AssignmentKind.BOT, loadedBot.id(), null,
                "Bot-Standard · " + loadedBot.name()));
        for (ConfigPool pool : baseConfigRepository.loadPools(loadedBot.id()))
            strategyAssignment.addItem(new AssignmentTarget(AssignmentKind.POOL, pool.id(), null,
                    "Config Pool · " + pool.name()));
        for (CurrencySettings pair : repository.loadAll(loadedBot.id()))
            strategyAssignment.addItem(new AssignmentTarget(AssignmentKind.PAIR, 0, pair.currency(),
                    "Handelspaar · " + pair.currency()));
        for (String regime : List.of("BUY_ALLOWED", "BUY_PAUSED", "EXIT"))
            strategyAssignment.addItem(new AssignmentTarget(AssignmentKind.MARKET, 0, regime,
                    "Marktphase · " + regime));
    }

    private void refreshStrategyTargets() {
        try {
            reloadAssignmentTargets();
            refreshStrategyAssignmentInfo();
        } catch (SQLException ex) {
            showError("Strategieziele konnten nicht aktualisiert werden", ex);
        }
    }

    private void assignStrategy(boolean clear) {
        if (loadedBot == null || loadedStrategy == null) return;
        AssignmentTarget target = (AssignmentTarget) strategyAssignment.getSelectedItem();
        if (target == null) return;
        Long strategyId = clear ? null : loadedStrategy.id();
        try {
            switch (target.kind()) {
                case BOT -> strategyRepository.assignBot(loadedBot.id(), strategyId);
                case POOL -> strategyRepository.assignPool(loadedBot.id(), target.id(), strategyId);
                case PAIR -> strategyRepository.assignPair(loadedBot.id(), target.value(), strategyId);
                case MARKET -> strategyRepository.assignMarketRegime(loadedBot.id(), target.value(), strategyId);
            }
            strategyAssignmentInfo.setText(clear ? "Zuweisung gelöst: " + target
                    : "Aktiv: " + loadedStrategy.name() + " → " + target);
            setMessage(clear ? "Strategie-Zuweisung gelöst." : "Strategie zugewiesen.", false);
        } catch (SQLException ex) {
            showError("Zuweisung konnte nicht gespeichert werden", ex);
        }
    }

    private void refreshStrategyAssignmentInfo() {
        if (loadingStrategy || loadedBot == null) return;
        AssignmentTarget target = (AssignmentTarget) strategyAssignment.getSelectedItem();
        if (target == null) return;
        try {
            Long assigned = switch (target.kind()) {
                case BOT -> strategyRepository.loadBotAssignment(loadedBot.id());
                case POOL -> strategyRepository.loadPoolAssignment(loadedBot.id(), target.id());
                case PAIR -> strategyRepository.loadPairAssignment(loadedBot.id(), target.value());
                case MARKET -> strategyRepository.loadMarketAssignment(loadedBot.id(), target.value());
            };
            if (assigned == null) strategyAssignmentInfo.setText("Keine Strategie zugewiesen");
            else {
                String name = "Strategie #" + assigned;
                for (int i=0;i<strategyLibraryModel.size();i++)
                    if (strategyLibraryModel.get(i).id()==assigned) name=strategyLibraryModel.get(i).name();
                strategyAssignmentInfo.setText("Aktiv: " + name);
            }
        } catch (SQLException ex) {
            strategyAssignmentInfo.setText("Zuweisung konnte nicht gelesen werden");
        }
    }

    private void testStrategy(JButton button) {
        if (loadedStrategy == null || !flushStrategyAutoSave()) return;
        try {
            configureSessionFromFields();
            if (!FusionClientProvider.isConfigured()) {
                showPage(PAGE_ACCESS, "API-Zugang");
                throw new IllegalStateException("Für den Strategietest zuerst einen Fusion API-Key eingeben.");
            }
            String currency = selectedCurrency();
            if (currency == null) throw new IllegalStateException("Dem Bot fehlt ein Handelspaar.");
            StrategyDefinition strategy = loadedStrategy;
            button.setEnabled(false);
            new SwingWorker<StrategyEvaluation, Void>() {
                @Override protected StrategyEvaluation doInBackground() throws Exception {
                    return strategyEvaluationService.evaluate(strategy, currency,
                            FusionClientProvider.getClient());
                }
                @Override protected void done() {
                    button.setEnabled(true);
                    try {
                        StrategyEvaluation evaluation = get();
                        lastTestedStrategy = strategy;
                        lastTestedCurrency = currency;
                        strategyBacktestButton.setEnabled(true);
                        strategyTestStatus.setText(strategyDecisionHeadline(evaluation) + " · "
                                + strategyDecisionReason(evaluation));
                        strategyTestStatus.setForeground(evaluation.buyAllowed()
                                ? OneOfXTheme.SUCCESS : OneOfXTheme.TEXT_SECONDARY);
                        strategyTestStatus.setToolTipText(String.join(" · ",
                                evaluation.explanations()));
                        setMessage("Markttest für „" + strategy.name() + "“ abgeschlossen.", false);
                    } catch (Exception ex) {
                        strategyBacktestButton.setEnabled(false);
                        strategyTestStatus.setText("Markttest fehlgeschlagen: "
                                + (ex.getCause() == null ? ex.getMessage() : ex.getCause().getMessage()));
                        strategyTestStatus.setForeground(OneOfXTheme.ERROR);
                        showBackgroundError("Strategie konnte nicht ausgewertet werden", ex);
                    }
                }
            }.execute();
        } catch (RuntimeException ex) {
            showError("Strategie konnte nicht getestet werden", ex);
        }
    }

    private void openBacktestForTestedStrategy() {
        if (lastTestedStrategy == null || lastTestedCurrency == null) return;
        if (!flushStrategyAutoSave()) return;
        backtestStrategyBox.setSelectedItem(lastTestedStrategy);
        backtestCurrencyBox.setSelectedItem(lastTestedCurrency);
        BotProfile bot = selectedBot();
        if (bot != null) backtestCapital.setValue(bot.budget());
        showPage(PAGE_BACKTEST, "Backtesting");
        setMessage("Backtest ist mit „" + lastTestedStrategy.name() + "“ und „"
                + lastTestedCurrency + "“ vorausgefüllt.", false);
    }

    private static String strategyDecisionHeadline(StrategyEvaluation evaluation){
        if(evaluation.blocked())return "KEIN KAUFSIGNAL – durch Sperrregel blockiert";
        if(evaluation.buy()&&evaluation.confirmations()<evaluation.requiredConfirmations())
            return "KEIN KAUFSIGNAL – Bestätigungen fehlen";
        return evaluation.buyAllowed()?"KAUFSIGNAL AKTIV":"KEIN KAUFSIGNAL";
    }

    private static String strategyDecisionReason(StrategyEvaluation evaluation){
        if(evaluation.blocked())return "Eine aktive Sperrregel verhindert den Kauf.";
        if(evaluation.buy()&&evaluation.confirmations()<evaluation.requiredConfirmations())
            return "Erfüllt: "+evaluation.confirmations()+" von "
                    +evaluation.requiredConfirmations()+" notwendigen Bestätigungen.";
        if(evaluation.buyAllowed())return evaluation.sell()
                ?"Die Kaufregeln sind erfüllt; gleichzeitig liegt auch ein Verkaufssignal vor."
                :"Alle notwendigen Kaufregeln und Bestätigungen sind erfüllt.";
        return evaluation.sell()
                ?"Die Kaufregeln sind nicht erfüllt. Ein Verkaufssignal ist aktiv."
                :"Mindestens eine notwendige Kaufregel ist derzeit nicht erfüllt.";
    }

    private void runBacktest(JButton button) {
        if (!flushAutoSave() || !flushBotAutoSave() || !flushStrategyAutoSave()) return;
        try {
            configureSessionFromFields();
            if (!FusionClientProvider.isConfigured()) {
                showPage(PAGE_ACCESS,"API-Zugang");
                throw new IllegalStateException("Für historische Kerzen zuerst einen Fusion API-Key eingeben.");
            }
            StrategyDefinition strategy=(StrategyDefinition)backtestStrategyBox.getSelectedItem();
            if(strategy==null)throw new IllegalStateException("Eine Strategie muss ausgewählt sein.");
            BacktestRequest request=buildBacktestRequest(strategy.id());
            String currency=request.currency();
            button.setEnabled(false);setMessage("Historische Kerzen werden geladen und simuliert …",false);
            backtestRunStatus.setForeground(OneOfXTheme.PRIMARY);
            backtestRunStatus.setText("L\u00e4uft: Historische Kerzen werden geladen und ausgewertet ...");
            new SwingWorker<BacktestResult,Void>(){
                @Override protected BacktestResult doInBackground()throws Exception{return backtestService.run(request,FusionClientProvider.getClient());}
                @Override protected void done(){button.setEnabled(true);try{BacktestResult result=get();showBacktestResult(result);reloadBacktestHistory(result.runId());recordEvent("INFO","BACKTEST",currency,"Backtest #"+result.runId()+" abgeschlossen: "+String.format(Locale.GERMANY,"%.2f %%",result.returnPercent()));setMessage("Backtest #"+result.runId()+" abgeschlossen.",false);}catch(Exception ex){reloadBacktestHistory(null);showBackgroundError("Backtest fehlgeschlagen",ex);}}
            }.execute();
        }catch(SQLException|RuntimeException ex){showError("Backtest konnte nicht gestartet werden",ex);}
    }

    private BacktestRequest buildBacktestRequest(long strategyId)throws SQLException{
        BotProfile bot=selectedBot();String currency=(String)backtestCurrencyBox.getSelectedItem();
        if(bot==null||currency==null)throw new IllegalStateException(
                "Bot und Handelspaar müssen ausgewählt sein.");
        for(JSpinner spinner:List.of(backtestCapital,backtestSpread,backtestPartialFill,
                backtestVolumeParticipation))commitSpinner(spinner);
        LocalDate startDate=LocalDate.parse(backtestStart.getText().trim());
        LocalDate endDate=LocalDate.parse(backtestEnd.getText().trim());
        Instant start=startDate.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant end=endDate.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        CurrencySettings pair=repository.load(bot.id(),currency);
        BotBaseConfig config=baseConfigRepository.loadEffective(bot.id(),currency);
        CurrencySettingsRepository.GridPreviewContext context=
                repository.loadGridPreviewContext(bot.id(),currency);
        if(context.rules()==null||!context.rules().usable())throw new IllegalStateException(
                "Für den Backtest fehlen Tick-Größe, Mengenschritt oder Mindestorder.");
        BacktestExecutionConfig execution=new BacktestExecutionConfig(pair.gridMode(),
                pair.gridSpacing(),pair.maxBuyAmount(),bot.maxExposure(),
                bot.maxOpenPositions(),bot.maxOpenOrders(),config.buyOrderType(),
                config.dcaEnabled(),config.dcaMaxOrders(),config.dcaTriggerPercent(),
                config.dcaSizeMultiplier(),config.onlySellWithProfit(),number(backtestSpread),
                number(backtestPartialFill),number(backtestVolumeParticipation));
        return new BacktestRequest(bot.id(),strategyId,currency,
                backtestTimeframe.getSelectedItem().toString(),start,end,number(backtestCapital),
                pair.buyAmount(),bot.paperFeePercent(),bot.paperSlippagePercent(),
                pair.stopLoss(),config.takeProfitPercent(),context.rules().tickSize().doubleValue(),
                context.rules().sizeIncrement().doubleValue(),context.rules().minOrderAmount(),
                context.rules().maxOrderAmount(),execution);
    }

    private void runBacktestOptimization(JButton button){
        if(!flushAutoSave()||!flushBotAutoSave()||!flushStrategyAutoSave())return;
        try{
            configureSessionFromFields();
            if(!FusionClientProvider.isConfigured()){
                showPage(PAGE_ACCESS,"API-Zugang");
                throw new IllegalStateException(
                        "Für historische Kerzen zuerst einen Fusion API-Key eingeben.");
            }
            List<StrategyDefinition> selected=backtestOptimizationStrategies.getSelectedValuesList();
            if(selected.isEmpty())throw new IllegalStateException(
                    "Mindestens eine Strategie für den Vergleich auswählen.");
            commitSpinner(backtestInSample);commitSpinner(backtestWalkForwardFolds);
            BacktestRequest base=buildBacktestRequest(selected.get(0).id());
            List<Double> grids=parseVariantValues(backtestOptimizationGrid.getText(),
                    base.execution().gridSpacing(),"Grid-Abstand");
            List<Double> stops=parseVariantValues(backtestOptimizationStop.getText(),
                    base.stopLossPercent(),"Stop-Loss");
            List<Double> targets=parseVariantValues(backtestOptimizationTakeProfit.getText(),
                    base.takeProfitPercent(),"Take Profit");
            BacktestOptimizationRequest request=new BacktestOptimizationRequest(base,
                    selected.stream().map(StrategyDefinition::id).toList(),grids,stops,targets,
                    number(backtestInSample),((Number)backtestWalkForwardFolds.getValue()).intValue());
            button.setEnabled(false);setMessage(
                    "Varianten werden im In-/Out-of-Sample- und Walk-forward-Verfahren geprüft …",false);
            new SwingWorker<BacktestOptimizationResult,Void>(){
                @Override protected BacktestOptimizationResult doInBackground()throws Exception{
                    return backtestOptimizationService.run(request,FusionClientProvider.getClient());
                }
                @Override protected void done(){
                    button.setEnabled(true);
                    try{
                        BacktestOptimizationResult result=get();showBacktestOptimization(result);
                        recordEvent("INFO","BACKTEST_OPTIMIZATION",base.currency(),
                                result.variants().size()+" Varianten und "+result.folds().size()
                                        +" Walk-forward-Fenster abgeschlossen.");
                        setMessage("Variantenvergleich abgeschlossen.",false);
                    }catch(Exception ex){showBackgroundError("Variantenvergleich fehlgeschlagen",ex);}
                }
            }.execute();
        }catch(SQLException|RuntimeException ex){
            showError("Variantenvergleich konnte nicht gestartet werden",ex);
        }
    }

    private static List<Double> parseVariantValues(String input,double fallback,String label){
        String normalized=input==null?"":input.trim();
        if(normalized.isEmpty())return List.of(fallback);
        List<Double> result=new ArrayList<>();
        for(String token:normalized.split("[;\\s]+")){
            try{result.add(Double.parseDouble(token.replace(',','.')));}
            catch(NumberFormatException ex){throw new IllegalArgumentException(
                    label+" enthält keine gültige Zahl: "+token);}
        }
        return result;
    }

    private void showBacktestOptimization(BacktestOptimizationResult result){
        replaceRows(backtestOptimizationModel,result.variants().stream().map(row->new Object[]{
                row.rank(),row.variant().strategyName(),decimal(row.variant().gridSpacing()),
                percent(row.variant().stopLossPercent()),percent(row.variant().takeProfitPercent()),
                percent(row.inSample().returnPercent()),percent(row.inSample().maxDrawdownPercent()),
                row.inSample().tradeCount(),percent(row.outOfSample().returnPercent()),
                percent(row.outOfSample().maxDrawdownPercent()),row.outOfSample().tradeCount(),
                decimal(row.score())}).toList());
        replaceRows(backtestWalkForwardModel,result.folds().stream().map(fold->new Object[]{
                fold.number(),dateTime(fold.trainingEnd().toEpochMilli()),
                dateTime(fold.testStart().toEpochMilli())+" – "+dateTime(fold.testEnd().toEpochMilli()),
                fold.selected().label(),percent(fold.testResult().returnPercent()),
                percent(fold.testResult().maxDrawdownPercent()),fold.testResult().tradeCount()
        }).toList());
    }

    private void reloadBacktestHistory(Long selectId) {
        if(loadedBot==null)return;loadingBacktest=true;
        try{
            backtestHistory.removeAllItems();BacktestRunSummary selected=null;
            for(BacktestRunSummary run:backtestRepository.loadRecent(loadedBot.id(),100)){backtestHistory.addItem(run);if(selectId!=null&&run.id()==selectId)selected=run;}
            if(selected!=null)backtestHistory.setSelectedItem(selected);else if(backtestHistory.getItemCount()>0)backtestHistory.setSelectedIndex(0);
        }catch(SQLException ex){setMessage("Backtest-Verlauf konnte nicht geladen werden: "+ex.getMessage(),true);}
        finally{loadingBacktest=false;}
        loadSelectedBacktest();
    }

    private void loadSelectedBacktest() {
        if(loadingBacktest)return;BacktestRunSummary run=(BacktestRunSummary)backtestHistory.getSelectedItem();
        if(run==null){clearBacktestResult();return;}
        try{
            List<com.oneofx.fusion.tradingbot.backtest.BacktestTrade> trades=
                    backtestRepository.loadTrades(run.id());
            List<BacktestOrderEvent> events=backtestRepository.loadOrderEvents(run.id());
            List<BacktestEquityPoint> equity=backtestRepository.loadEquity(run.id());
            showBacktestSummary(run);showBacktestTrades(trades);
            backtestEquityCurve.setPoints(equity);showBacktestOrderEvents(events);
            updateBacktestStatus(run.status(),run.error(),equity.size(),events.size(),trades.size());
            if("FAILED".equals(run.status())&&run.error()!=null)setMessage("Backtest #"+run.id()+" fehlgeschlagen: "+run.error(),true);
        }catch(SQLException ex){setMessage("Backtest-Ergebnis konnte nicht geladen werden: "+ex.getMessage(),true);}
    }

    private void showBacktestResult(BacktestResult result) {
        backtestFinalCapital.setText(money(result.finalCapital()));backtestProfit.setText(money(result.netProfit()));
        updateBacktestStatus("COMPLETED",null,result.equity().size(),
                result.orderEvents().size(),result.trades().size());
        backtestReturn.setText(percent(result.returnPercent()));backtestDrawdown.setText(percent(result.maxDrawdownPercent()));
        backtestTradeCount.setText(Integer.toString(result.tradeCount()));backtestWinRate.setText(percent(result.winRatePercent()));
        backtestProfitFactor.setText(Double.isInfinite(result.profitFactor())?"∞":decimal(result.profitFactor()));showBacktestTrades(result.trades());showBacktestOrderEvents(result.orderEvents());backtestEquityCurve.setPoints(result.equity());
    }

    private void showBacktestSummary(BacktestRunSummary run) {
        backtestFinalCapital.setText(nullableMoney(run.finalCapital()));backtestProfit.setText(nullableMoney(run.netProfit()));
        backtestReturn.setText(nullablePercent(run.returnPercent()));backtestDrawdown.setText(nullablePercent(run.maxDrawdownPercent()));
        backtestTradeCount.setText(Integer.toString(run.tradeCount()));backtestWinRate.setText(nullablePercent(run.winRatePercent()));
        backtestProfitFactor.setText(run.profitFactor()==null&&"COMPLETED".equals(run.status())?"∞":run.profitFactor()==null?"–":decimal(run.profitFactor()));
    }

    private void showBacktestTrades(List<com.oneofx.fusion.tradingbot.backtest.BacktestTrade> trades) {
        if(trades.isEmpty()){
            backtestTradesModel.setRowCount(0);
            backtestTradesModel.addRow(new Object[]{"Keine Trades","-","-","-","-","-","-","-","-",
                    "Es wurde keine Position ge\u00f6ffnet."});
            return;
        }
        replaceRows(backtestTradesModel,trades.stream().map(trade->new Object[]{trade.number(),dateTime(trade.entryTime()),dateTime(trade.exitTime()),decimal(trade.entryPrice()),decimal(trade.exitPrice()),decimal(trade.quantity()),money(trade.fees()),money(trade.pnl()),trade.exitReason(),trade.explanation()}).toList());
    }

    private void showBacktestOrderEvents(List<BacktestOrderEvent> events) {
        if(events.isEmpty()){
            backtestOrdersModel.setRowCount(0);
            backtestOrdersModel.addRow(new Object[]{"Keine Orders","-","-","-","-","-","-","-","-",
                    "Kaufbedingungen im Zeitraum nie gemeinsam erf\u00fcllt."});
            return;
        }
        replaceRows(backtestOrdersModel,events.stream().map(event->new Object[]{event.number(),event.orderReference(),event.gridLevel(),dateTime(event.timestamp()),event.event(),decimal(event.limitPrice()),event.fillPrice()>0?decimal(event.fillPrice()):"–",event.fillQuantity()>0?decimal(event.fillQuantity()):"–",money(event.remainingAmount()),event.reason()}).toList());
    }

    private void updateBacktestStatus(String status,String error,int candles,int orders,int trades){
        if("FAILED".equals(status)){
            backtestRunStatus.setForeground(OneOfXTheme.ERROR);
            backtestRunStatus.setText("Fehlgeschlagen: "+(error==null?"Unbekannter Fehler":error));
        }else if(orders==0){
            backtestRunStatus.setForeground(OneOfXTheme.PRIMARY);
            backtestRunStatus.setText("Abgeschlossen: "+candles
                    +" Kerzen ausgewertet, aber kein Kaufsignal - Regeln oder Zeitraum pr\u00fcfen.");
        }else if(trades==0){
            backtestRunStatus.setForeground(OneOfXTheme.PRIMARY);
            backtestRunStatus.setText("Abgeschlossen: "+orders
                    +" Order-Ereignisse, aber keine Order wurde ausgef\u00fchrt.");
        }else{
            backtestRunStatus.setForeground(OneOfXTheme.SUCCESS);
            backtestRunStatus.setText("Abgeschlossen: "+candles+" Kerzen, "+orders
                    +" Order-Ereignisse und "+trades+" Trades.");
        }
    }

    private void clearBacktestResult() {
        for(JLabel label:List.of(backtestFinalCapital,backtestProfit,backtestReturn,backtestDrawdown,backtestTradeCount,backtestWinRate,backtestProfitFactor))label.setText("–");
        backtestTradesModel.setRowCount(0);backtestOrdersModel.setRowCount(0);backtestEquityCurve.setPoints(List.of());
        backtestRunStatus.setForeground(OneOfXTheme.TEXT_SECONDARY);
        backtestRunStatus.setText("Noch kein Backtest ausgew\u00e4hlt.");
    }

    private static String nullableMoney(Double value){return value==null?"–":money(value);}
    private static String nullablePercent(Double value){return value==null?"–":percent(value);}
    private static String percent(double value){return String.format(Locale.GERMANY,"%.2f %%",value);}
    private static String dateTime(long epochMillis){return DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(epochMillis));}

    private void reloadBots(Long selectAfterReload) {
        loadingBot = true;
        try {
            BotProfile selected = selectAfterReload == null
                    ? botRepository.loadSelected() : botRepository.load(selectAfterReload);
            botBox.removeAllItems();
            for (BotProfile bot : botRepository.loadAll()) botBox.addItem(bot);
            for (int i = 0; i < botBox.getItemCount(); i++) {
                if (botBox.getItemAt(i).id() == selected.id()) {
                    botBox.setSelectedIndex(i);
                    break;
                }
            }
            botRepository.select(selected.id());
            loadedBot = selected;
            populateBotForm(selected);
            reloadBaseConfigs(selected.id(), null);
        } catch (SQLException | RuntimeException ex) {
            showError("Bots konnten nicht geladen werden", ex);
        } finally {
            loadingBot = false;
        }
        reloadCurrencies(null);
        reloadStrategies(null);
        refreshBotRiskSummary();
    }

    private void switchSelectedBot() {
        if (loadingBot) return;
        BotProfile selected = selectedBot();
        if (selected == null || loadedBot != null && selected.id() == loadedBot.id()) return;
        if (engine.getState() != State.STOPPED) {
            loadingBot = true;
            botBox.setSelectedItem(loadedBot);
            loadingBot = false;
            setMessage("Der Bot kann nur bei gestoppter Engine gewechselt werden.", true);
            return;
        }
        if (!flushAutoSave() || !flushBotAutoSave() || !flushStrategyAutoSave()) {
            loadingBot = true;
            botBox.setSelectedItem(loadedBot);
            loadingBot = false;
            return;
        }
        try {
            botRepository.select(selected.id());
            loadedBot = botRepository.load(selected.id());
            loadingBot = true;
            populateBotForm(loadedBot);
            reloadBaseConfigs(loadedBot.id(), null);
            loadingBot = false;
            reloadCurrencies(null);
            reloadStrategies(null);
            refreshDashboard();
            refreshOperationalViews();
            refreshBotRiskSummary();
            setMessage("Bot „" + loadedBot.name() + "“ ausgewählt.", false);
        } catch (SQLException | RuntimeException ex) {
            showError("Bot konnte nicht gewechselt werden", ex);
        } finally {
            loadingBot = false;
        }
    }

    private void populateBotForm(BotProfile bot) {
        botName.setText(bot.name());
        botEnabled.setSelected(bot.enabled());
        botMode.setSelectedItem(bot.mode());
        botStrategy.setText(bot.strategy());
        botBudget.setValue(bot.budget());
        botMaxExposure.setValue(bot.maxExposure());
        botMaxPositions.setValue(bot.maxOpenPositions());
        botMaxOrders.setValue(bot.maxOpenOrders());
        botPaperFee.setValue(bot.paperFeePercent());
        botPaperSlippage.setValue(bot.paperSlippagePercent());
        backtestCapital.setValue(bot.budget());
        botSaveDirty = false;
        setSaveStatus(botSaveStatus, "Gespeichert", OneOfXTheme.SUCCESS);
    }

    private void reloadBaseConfigs(long botId, Long selectPoolId) throws SQLException {
        loadingBaseConfig = true;
        try {
            configEditorBox.removeAllItems();
            configEditorBox.addItem("Bot-Baseconfig");
            ConfigPool selected = null;
            for (ConfigPool pool : baseConfigRepository.loadPools(botId)) {
                configEditorBox.addItem(pool);
                if (selectPoolId != null && pool.id() == selectPoolId) selected = pool;
            }
            if (selected == null) {
                configEditorBox.setSelectedIndex(0);
                loadedConfigPool = null;
                populateBaseConfig(baseConfigRepository.loadBase(botId));
            } else {
                configEditorBox.setSelectedItem(selected);
                loadedConfigPool = selected;
                populateBaseConfig(selected.configuration());
            }
        } finally {
            loadingBaseConfig = false;
        }
    }

    private void switchConfigEditor() {
        if (loadingBaseConfig || loadedBot == null) return;
        if (!flushBotAutoSave()) return;
        Object selected = configEditorBox.getSelectedItem();
        try {
            loadedConfigPool = selected instanceof ConfigPool pool
                    ? baseConfigRepository.loadPools(loadedBot.id()).stream()
                            .filter(item -> item.id() == pool.id()).findFirst().orElse(null)
                    : null;
            loadingBaseConfig = true;
            populateBaseConfig(loadedConfigPool == null
                    ? baseConfigRepository.loadBase(loadedBot.id())
                    : loadedConfigPool.configuration());
        } catch (SQLException ex) {
            showError("Konfiguration konnte nicht geladen werden", ex);
        } finally {
            loadingBaseConfig = false;
        }
    }

    private void populateBaseConfig(BotBaseConfig config) {
        boolean adjusted = refreshBaseOrderTypeChoices(config);
        baseBuyOrderMinutes.setValue(config.maxBuyOrderMinutes());
        baseSellOrderMinutes.setValue(config.maxSellOrderMinutes());
        baseCooldownMinutes.setValue(config.cooldownMinutes());
        baseTakeProfit.setValue(config.takeProfitPercent());
        baseTrailingBuy.setSelected(config.trailingStopBuyEnabled());
        baseTrailingBuyActivation.setValue(config.trailingStopBuyActivationPercent());
        baseTrailingBuyRebound.setValue(config.trailingStopBuyReboundPercent());
        baseOnlyProfit.setSelected(config.onlySellWithProfit());
        baseCloseAfterEnabled.setSelected(config.closeAfterMinutes() > 0);
        baseCloseAfter.setValue(config.closeAfterMinutes());
        baseDcaEnabled.setSelected(config.dcaEnabled());
        baseDcaMaxOrders.setValue(config.dcaMaxOrders());
        baseDcaTrigger.setValue(config.dcaTriggerPercent());
        baseDcaMultiplier.setValue(config.dcaSizeMultiplier());
        updateBaseConfigFields();
        botSaveDirty = adjusted;
        if (!adjusted) setSaveStatus(baseConfigSaveStatus, "Gespeichert", OneOfXTheme.SUCCESS);
        if (adjusted) {
            setMessage("Nicht unterstützte Orderart wurde auf eine gültige Alternative gesetzt und wird gespeichert.", true);
            botSaveTimer.restart();
        }
    }

    private BotBaseConfig readBaseConfig(long ownerId) {
        for (JSpinner spinner : List.of(baseBuyOrderMinutes, baseSellOrderMinutes,
                baseCooldownMinutes, baseTakeProfit, baseTrailingBuyActivation,
                baseTrailingBuyRebound, baseCloseAfter, baseDcaMaxOrders,
                baseDcaTrigger, baseDcaMultiplier)) commitSpinner(spinner);
        return new BotBaseConfig(ownerId, (String) baseBuyOrderType.getSelectedItem(),
                (String) baseSellOrderType.getSelectedItem(),
                ((Number) baseBuyOrderMinutes.getValue()).intValue(),
                ((Number) baseSellOrderMinutes.getValue()).intValue(),
                ((Number) baseCooldownMinutes.getValue()).intValue(), number(baseTakeProfit),
                baseTrailingBuy.isSelected(), number(baseTrailingBuyActivation),
                number(baseTrailingBuyRebound), baseOnlyProfit.isSelected(),
                baseCloseAfterEnabled.isSelected()
                        ? ((Number) baseCloseAfter.getValue()).intValue() : 0,
                baseDcaEnabled.isSelected(),
                ((Number) baseDcaMaxOrders.getValue()).intValue(), number(baseDcaTrigger),
                number(baseDcaMultiplier));
    }

    private void createConfigPool() {
        if (loadedBot == null || !flushBotAutoSave()) return;
        String name = JOptionPane.showInputDialog(this, "Name des neuen Config Pools:",
                "Config Pool anlegen", JOptionPane.PLAIN_MESSAGE);
        if (name == null || name.isBlank()) return;
        try {
            BotBaseConfig source = readBaseConfig(loadedBot.id());
            ConfigPool pool = baseConfigRepository.createPool(loadedBot.id(), name, source);
            reloadBaseConfigs(loadedBot.id(), pool.id());
            reloadPairPoolAssignment();
            refreshStrategyTargets();
            setMessage("Config Pool „" + pool.name() + "“ wurde angelegt.", false);
        } catch (SQLException | IllegalArgumentException ex) {
            showError("Config Pool konnte nicht angelegt werden", ex);
        }
    }

    private void archiveConfigPool() {
        if (loadedBot == null || loadedConfigPool == null || !flushBotAutoSave()) {
            if (loadedConfigPool == null) setMessage("Bitte zuerst einen Config Pool auswählen.", true);
            return;
        }
        int answer = JOptionPane.showConfirmDialog(this,
                "Config Pool „" + loadedConfigPool.name() + "“ löschen? Zugeordnete Paare fallen auf die Baseconfig zurück.",
                "Config Pool löschen", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (answer != JOptionPane.YES_OPTION) return;
        try {
            baseConfigRepository.archivePool(loadedBot.id(), loadedConfigPool.id());
            reloadBaseConfigs(loadedBot.id(), null);
            reloadPairPoolAssignment();
            refreshStrategyTargets();
            setMessage("Config Pool wurde gelöscht.", false);
        } catch (SQLException ex) { showError("Config Pool konnte nicht gelöscht werden", ex); }
    }

    private void createBot() {
        if (!flushAutoSave() || !flushBotAutoSave() || !flushStrategyAutoSave()) return;
        JDialog dialog = new JDialog(this, "Neuen Bot einrichten", true);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        dialog.setLayout(new BorderLayout(0, 16));
        dialog.getContentPane().setBackground(OneOfXTheme.BACKGROUND);
        dialog.setSize(720, 430);
        dialog.setLocationRelativeTo(this);

        JLabel progress = new JLabel();
        progress.setForeground(OneOfXTheme.TEXT_MUTED);
        progress.setFont(OneOfXTheme.font(Font.BOLD, 11));
        progress.setBorder(OneOfXTheme.padding(18, 22, 0, 22));
        dialog.add(progress, BorderLayout.NORTH);

        CardLayout steps = new CardLayout();
        JPanel stepCards = new JPanel(steps);
        stepCards.setBackground(OneOfXTheme.BACKGROUND);
        stepCards.setBorder(OneOfXTheme.padding(0, 22, 0, 22));

        JTextField name = new JTextField(24);
        JComboBox<BotProfile.Mode> mode = new JComboBox<>(BotProfile.Mode.values());
        mode.setSelectedItem(BotProfile.Mode.PAPER);
        JPanel identity = modernForm();
        addFormRow(identity, 0, "Bot-Name", name, null);
        addFormRow(identity, 1, "Modus", mode, null);
        stepCards.add(wizardStep("1. Zweck festlegen",
                "Paper ist für den sicheren ersten Test vorausgewählt.", identity), "identity");

        JTextField pair = new JTextField(18);
        JCheckBox pairEnabled = new JCheckBox("Neue Käufe für dieses Paar erlauben");
        pairEnabled.setSelected(true);
        JSpinner orderAmount = decimalSpinner(10.0, 0.01, 1_000_000.0, 1.0);
        JSpinner pairLimit = decimalSpinner(500.0, 0.01, 100_000_000.0, 10.0);
        JPanel market = modernForm();
        addFormRow(market, 0, "Handelspaar", pair, "optional");
        addFormRow(market, 1, "Kaufstatus", pairEnabled, null);
        addFormRow(market, 2, "Betrag je Order", orderAmount, "EUR");
        addFormRow(market, 3, "Kapitalgrenze", pairLimit, "EUR");
        stepCards.add(wizardStep("2. Markt und Einstieg",
                "Ein Paar wird vor dem Anlegen direkt gegen Bitpanda Fusion geprüft.", market),
                "market");

        JSpinner budget = decimalSpinner(5_000, 0.01, 1_000_000_000.0, 100);
        JSpinner exposure = decimalSpinner(5_000, 0.01, 1_000_000_000.0, 100);
        JSpinner positions = new JSpinner(new SpinnerNumberModel(20, 1, 100_000, 1));
        JSpinner orders = new JSpinner(new SpinnerNumberModel(4, 1, 100_000, 1));
        JPanel risk = modernForm();
        addFormRow(risk, 0, "Gesamtbudget", budget, "EUR");
        addFormRow(risk, 1, "Maximales Exposure", exposure, "EUR");
        addFormRow(risk, 2, "Offene Positionen", positions, null);
        addFormRow(risk, 3, "Offene Orders", orders, null);
        stepCards.add(wizardStep("3. Grenzen prüfen",
                "Erweiterte Orderregeln, DCA und Strategien können danach ergänzt werden.", risk),
                "risk");
        dialog.add(stepCards, BorderLayout.CENTER);

        JPanel controls = new JPanel(new BorderLayout());
        controls.setOpaque(false);
        controls.setBorder(OneOfXTheme.padding(0, 22, 18, 22));
        JButton cancel = new JButton("Abbrechen");
        styleSecondaryButton(cancel);
        cancel.addActionListener(event -> dialog.dispose());
        controls.add(cancel, BorderLayout.WEST);
        JPanel nextControls = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        nextControls.setOpaque(false);
        JButton back = new JButton("Zurück");
        styleSecondaryButton(back);
        JButton next = new JButton("Weiter");
        stylePrimaryButton(next);
        nextControls.add(back);
        nextControls.add(next);
        controls.add(nextControls, BorderLayout.EAST);
        dialog.add(controls, BorderLayout.SOUTH);

        int[] step = {0};
        Runnable update = () -> {
            String[] cards = {"identity", "market", "risk"};
            steps.show(stepCards, cards[step[0]]);
            progress.setText("SCHRITT " + (step[0] + 1) + " VON 3");
            back.setEnabled(step[0] > 0);
            next.setText(step[0] == 2 ? "Bot anlegen" : "Weiter");
        };
        back.addActionListener(event -> { if (step[0] > 0) { step[0]--; update.run(); } });
        next.addActionListener(event -> {
            if (step[0] == 0 && name.getText().isBlank()) {
                JOptionPane.showMessageDialog(dialog, "Bitte einen Bot-Namen eingeben.",
                        "Bot-Name fehlt", JOptionPane.WARNING_MESSAGE);
                return;
            }
            if (step[0] < 2) {
                step[0]++;
                update.run();
                return;
            }
            finishBotWizard(dialog, back, next, cancel, name.getText(),
                    (BotProfile.Mode) mode.getSelectedItem(), pair.getText(), pairEnabled.isSelected(),
                    orderAmount, pairLimit, budget, exposure, positions, orders);
        });
        update.run();
        dialog.setVisible(true);
    }

    private JPanel wizardStep(String title, String subtitle, JPanel form) {
        RoundedPanel card = cardPanel(new BorderLayout(0, 18));
        card.add(titleBlock(title, subtitle), BorderLayout.NORTH);
        card.add(form, BorderLayout.CENTER);
        return card;
    }

    private void finishBotWizard(JDialog dialog, JButton back, JButton finish, JButton cancel,
            String name, BotProfile.Mode mode, String pairInput, boolean pairEnabled,
            JSpinner orderAmount, JSpinner pairLimit, JSpinner budget, JSpinner exposure,
            JSpinner positions, JSpinner orders) {
        try {
            for (JSpinner spinner : List.of(orderAmount, pairLimit, budget, exposure, positions, orders)) {
                commitSpinner(spinner);
            }
            String normalizedPair = pairInput == null || pairInput.isBlank()
                    ? null : CurrencySettings.normalizeCurrency(pairInput);
            configureSessionFromFields();
            if (normalizedPair != null && !FusionClientProvider.isConfigured()) {
                throw new IllegalStateException(
                        "Für die Paarprüfung zuerst einen Fusion API-Key unter „System“ hinterlegen.");
            }
            back.setEnabled(false);
            finish.setEnabled(false);
            cancel.setEnabled(false);
            String finalPair = normalizedPair;
            new SwingWorker<BotProfile, Void>() {
                @Override protected BotProfile doInBackground() throws Exception {
                    TradingPair verified = finalPair == null ? null
                            : pairValidator.validate(finalPair, FusionClientProvider.getClient());
                    BotProfile created = botRepository.create(name.trim());
                    BotProfile configured = new BotProfile(created.id(), created.name(), false, false,
                            mode, "GRID", number(budget), number(exposure),
                            ((Number) positions.getValue()).intValue(),
                            ((Number) orders.getValue()).intValue(), 0.25, 0.10);
                    botRepository.save(configured);
                    if (verified != null) {
                        CurrencySettings defaults = CurrencySettings.defaults(finalPair);
                        repository.addVerified(configured.id(), new CurrencySettings(finalPair,
                                pairEnabled, number(orderAmount), number(pairLimit),
                                defaults.gridMode(), defaults.gridSpacing(), defaults.stopLoss(),
                                defaults.trailingStopEnabled(), defaults.trailingStopActivation(),
                                defaults.trailingStopDecline()), verified);
                    }
                    return configured;
                }

                @Override protected void done() {
                    try {
                        BotProfile created = get();
                        botRepository.select(created.id());
                        dialog.dispose();
                        reloadBots(created.id());
                        if (finalPair != null) reloadCurrencies(finalPair);
                        botTabs.setSelectedIndex(finalPair == null ? 0 : 1);
                        setMessage("Bot „" + created.name() + "“ wurde eingerichtet."
                                + (finalPair == null ? " Als Nächstes ein Handelspaar hinzufügen."
                                : " Handelspaar „" + finalPair + "“ ist geprüft."), false);
                    } catch (Exception ex) {
                        back.setEnabled(true);
                        finish.setEnabled(true);
                        cancel.setEnabled(true);
                        showBackgroundError("Bot konnte nicht eingerichtet werden", ex);
                    }
                }
            }.execute();
        } catch (RuntimeException ex) {
            showError("Bot konnte nicht eingerichtet werden", ex);
        }
    }

    private void archiveBot() {
        BotProfile bot = selectedBot();
        if (bot == null || engine.getState() != State.STOPPED
                || !flushAutoSave() || !flushBotAutoSave() || !flushStrategyAutoSave()) return;
        int answer = JOptionPane.showConfirmDialog(this,
                "Bot „" + bot.name() + "“ archivieren?\n"
                        + "Orders, Positionen, Historie und Einstellungen bleiben erhalten.",
                "Bot archivieren", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (answer != JOptionPane.YES_OPTION) return;
        try {
            botRepository.archive(bot.id());
            operationsRepository.recordEvent(bot.id(), "INFO", "BOT", null,
                    "Bot sicher archiviert.");
            reloadBots(null);
            setMessage("Bot „" + bot.name() + "“ wurde archiviert.", false);
        } catch (SQLException ex) {
            showError("Bot konnte nicht archiviert werden", ex);
        }
    }

    private void resetPaperAccount() {
        BotProfile bot = selectedBot();
        if (bot == null) return;
        if (engine.getState() != State.STOPPED) {
            setMessage("Das Paper-Konto kann nur bei gestoppter Engine zurückgesetzt werden.", true);
            return;
        }
        if (bot.mode() != BotProfile.Mode.PAPER) {
            setMessage("Das Zurücksetzen ist nur für Paper-Bots verfügbar.", true);
            return;
        }
        int answer = JOptionPane.showConfirmDialog(this,
                "Alle Paper-Orders, Paper-Positionen und virtuellen Kontostände von „"
                        + bot.name() + "“ löschen und mit " + money(bot.budget()) + " neu starten?",
                "Paper-Konto zurücksetzen", JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);
        if (answer != JOptionPane.YES_OPTION) return;
        try {
            paperRepository.reset(bot.id(), bot.budget());
            operationsRepository.recordEvent(bot.id(), "INFO", "PAPER", null,
                    "Paper-Konto auf das Bot-Budget zurückgesetzt.");
            refreshDashboard();
            refreshOperationalViews();
            setMessage("Paper-Konto wurde zurückgesetzt.", false);
        } catch (SQLException ex) {
            showError("Paper-Konto konnte nicht zurückgesetzt werden", ex);
        }
    }

    private void scheduleBotAutoSave() {
        if (loadingBot || loadingBaseConfig || loadedBot == null) return;
        botSaveDirty = true;
        setSaveStatus(botSaveStatus, "Wird gespeichert …", OneOfXTheme.PRIMARY);
        setSaveStatus(baseConfigSaveStatus, "Wird gespeichert …", OneOfXTheme.PRIMARY);
        setMessage("Bot-Einstellungen werden gespeichert …", false);
        botSaveTimer.restart();
    }

    private boolean savePendingBot() {
        if (!botSaveDirty || loadedBot == null) return true;
        try {
            commitSpinner(botBudget);
            commitSpinner(botMaxExposure);
            commitSpinner(botMaxPositions);
            commitSpinner(botMaxOrders);
            commitSpinner(botPaperFee);
            commitSpinner(botPaperSlippage);
            BotProfile updated = new BotProfile(loadedBot.id(), botName.getText(),
                    botEnabled.isSelected(), false,
                    (BotProfile.Mode) botMode.getSelectedItem(), botStrategy.getText(),
                    number(botBudget), number(botMaxExposure),
                    ((Number) botMaxPositions.getValue()).intValue(),
                    ((Number) botMaxOrders.getValue()).intValue(),
                    number(botPaperFee), number(botPaperSlippage));
            BotBaseConfig executionConfig = readBaseConfig(
                    loadedConfigPool == null ? updated.id() : loadedConfigPool.id());
            botRepository.save(updated);
            if (loadedConfigPool == null) {
                baseConfigRepository.saveBase(updated.id(), executionConfig);
            } else {
                loadedConfigPool = new ConfigPool(loadedConfigPool.id(), updated.id(),
                        loadedConfigPool.name(), executionConfig);
                baseConfigRepository.savePool(loadedConfigPool);
            }
            loadedBot = updated;
            botSaveDirty = false;
            loadingBot = true;
            int selectedIndex = botBox.getSelectedIndex();
            if (selectedIndex >= 0) {
                botBox.removeItemAt(selectedIndex);
                botBox.insertItemAt(updated, selectedIndex);
                botBox.setSelectedIndex(selectedIndex);
            }
            loadingBot = false;
            recordEvent("INFO", "BOT", null, "Bot- und Risikowerte automatisch gespeichert.");
            refreshBotRiskSummary();
            refreshStartReadiness();
            setSaveStatus(botSaveStatus, "Gespeichert", OneOfXTheme.SUCCESS);
            setSaveStatus(baseConfigSaveStatus, "Gespeichert", OneOfXTheme.SUCCESS);
            setMessage("Bot-Einstellungen automatisch gespeichert.", false);
            return true;
        } catch (SQLException | IllegalArgumentException ex) {
            setSaveStatus(botSaveStatus, "Nicht gespeichert: " + ex.getMessage(), OneOfXTheme.ERROR);
            setSaveStatus(baseConfigSaveStatus, "Nicht gespeichert: " + ex.getMessage(), OneOfXTheme.ERROR);
            setMessage("Bot-Einstellungen konnten nicht gespeichert werden: "
                    + ex.getMessage(), true);
            return false;
        }
    }

    private boolean flushBotAutoSave() {
        botSaveTimer.stop();
        return savePendingBot();
    }

    private void installStrategyAutoSaveListeners() {
        strategyEnabled.addActionListener(event -> scheduleStrategyAutoSave());
        strategyConfirmations.addChangeListener(event -> scheduleStrategyAutoSave());
        strategySpacingMode.addActionListener(event -> {
            if (strategySpacingMode.getSelectedItem() == EntrySpacingMode.PERCENT
                    && number(strategySpacing) >= 100) strategySpacing.setValue(1.0);
            refreshStrategySpacingPreview();
            scheduleStrategyAutoSave();
        });
        strategySpacing.addChangeListener(event -> {
            refreshStrategySpacingPreview();
            scheduleStrategyAutoSave();
        });
        DocumentListener listener = new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent event) { scheduleStrategyAutoSave(); }
            @Override public void removeUpdate(DocumentEvent event) { scheduleStrategyAutoSave(); }
            @Override public void changedUpdate(DocumentEvent event) { scheduleStrategyAutoSave(); }
        };
        strategyName.getDocument().addDocumentListener(listener);
    }

    private void scheduleStrategyAutoSave() {
        if (loadingStrategy || loadedStrategy == null) return;
        strategySaveDirty = true;
        strategySaveStatus.setText("Wird automatisch gespeichert …");
        strategySaveStatus.setForeground(OneOfXTheme.PRIMARY);
        setMessage("Strategie wird automatisch gespeichert …", false);
        strategySaveTimer.restart();
    }

    private void refreshStrategySpacingPreview() {
        EntrySpacingMode mode = (EntrySpacingMode) strategySpacingMode.getSelectedItem();
        String unit = mode == EntrySpacingMode.PERCENT ? " %" : " in der Kursw\u00e4hrung";
        strategySpacingPreview.setText("N\u00e4chster Kauf: mindestens "
                + decimal(number(strategySpacing)) + unit + " unter dem niedrigsten Einstieg.");
    }

    private boolean savePendingStrategy() {
        if (!strategySaveDirty || loadedStrategy == null) return true;
        try {
            commitSpinner(strategyConfirmations);
            commitSpinner(strategySpacing);
            StrategyDefinition updated = new StrategyDefinition(loadedStrategy.id(),
                    loadedStrategy.botId(), strategyName.getText(),
                    ((Number) strategyConfirmations.getValue()).intValue(),
                    strategyEnabled.isSelected(),
                    (EntrySpacingMode) strategySpacingMode.getSelectedItem(),
                    number(strategySpacing));
            strategyRepository.saveDefinition(updated);
            loadedStrategy = updated;
            strategySaveDirty = false;
            strategySaveStatus.setText("Gespeichert");
            strategySaveStatus.setForeground(OneOfXTheme.SUCCESS);
            loadingStrategy = true;
            int index = strategyLibrary.getSelectedIndex();
            if (index >= 0) {
                strategyLibraryModel.set(index, updated);
                strategyLibrary.setSelectedIndex(index);
            }
            loadingStrategy = false;
            setMessage("Strategie automatisch gespeichert.", false);
            return true;
        } catch (SQLException | IllegalArgumentException ex) {
            loadingStrategy = false;
            strategySaveStatus.setText("Nicht gespeichert: " + ex.getMessage());
            strategySaveStatus.setForeground(OneOfXTheme.ERROR);
            setMessage("Strategie konnte nicht gespeichert werden: " + ex.getMessage(), true);
            return false;
        }
    }

    private boolean flushStrategyAutoSave() {
        strategySaveTimer.stop();
        return savePendingStrategy();
    }

    private void refreshBotRiskSummary() {
        BotProfile bot = selectedBot();
        if (bot == null) return;
        try {
            BotRepository.RiskSnapshot risk = botRepository.loadRiskSnapshot(bot.id());
            botExposure.setText(money(risk.exposure()));
            botRemaining.setText(money(risk.remainingCapital()));
            botOpenLimits.setText(risk.openPositions() + " / " + risk.openOrders()
                    + " von " + risk.maxOpenPositions() + " / " + risk.maxOpenOrders());
        } catch (SQLException ex) {
            botExposure.setText("–");
            botRemaining.setText("–");
            botOpenLimits.setText("–");
        }
    }

    private void reloadCurrencies(String selectAfterReload) {
        loadingSettings = true;
        try {
            List<CurrencySettings> settings = repository.loadAll();
            currencyBox.removeAllItems();
            backtestCurrencyBox.removeAllItems();
            terminalCurrencyBox.removeAllItems();
            for (CurrencySettings entry : settings) {
                currencyBox.addItem(entry.currency());
                backtestCurrencyBox.addItem(entry.currency());
                terminalCurrencyBox.addItem(entry.currency());
            }
            if (selectAfterReload != null) {
                currencyBox.setSelectedItem(selectAfterReload);
                terminalCurrencyBox.setSelectedItem(selectAfterReload);
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
            gridPreviewContext = null;
            gridPreviewModel.setRowCount(0);
            pairSupportedOrderTypes.setText("Unterstützte Ordertypen: –");
            refreshTerminalOrderTypes();
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
            gridPreviewContext = repository.loadGridPreviewContext(currency);
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
            if (gridPreviewContext.allTimeHigh() > 0.000001) {
                previewPrice.setValue(gridPreviewContext.allTimeHigh());
            }
            loadedCurrency = currency;
            refreshPairOrderTypeDisplay(currency);
            reloadPairPoolAssignment();
            autoSaveDirty = false;
            setSaveStatus(settingsSaveStatus, "Gespeichert", OneOfXTheme.SUCCESS);
            setMessage("Einstellungen für " + currency + " geladen.", false);
        } catch (SQLException | RuntimeException ex) {
            showError("Einstellungen konnten nicht geladen werden", ex);
        } finally {
            loadingSettings = false;
        }
        refreshGridPreview();
    }

    private void refreshPairOrderTypeDisplay(String currency) {
        try {
            OrderTypeAvailability.Availability availability = orderTypeAvailability.forPair(currency);
            pairSupportedOrderTypes.setText("Unterstützte Ordertypen: " + availability.display());
            pairSupportedOrderTypes.setToolTipText(
                    "Diese Angaben stammen aus dem gespeicherten Bitpanda-Fusion-Paarkatalog.");
            pairSupportedOrderTypes.setForeground(availability.supported().isEmpty()
                    ? OneOfXTheme.ERROR : OneOfXTheme.TEXT_MUTED);
        } catch (SQLException ex) {
            pairSupportedOrderTypes.setText("Unterstützte Ordertypen konnten nicht geladen werden");
            pairSupportedOrderTypes.setForeground(OneOfXTheme.ERROR);
        }
    }

    private boolean refreshBaseOrderTypeChoices(BotBaseConfig config) {
        OrderTypeAvailability.Availability availability;
        boolean failed = false;
        try {
            availability = loadedBot == null
                    ? new OrderTypeAvailability.Availability(Set.of(OrderType.values()), List.of())
                    : orderTypeAvailability.forConfiguration(loadedBot.id(),
                            loadedConfigPool == null ? null : loadedConfigPool.id());
        } catch (SQLException ex) {
            availability = new OrderTypeAvailability.Availability(Set.of(OrderType.values()), List.of());
            failed = true;
        }
        List<String> buy = OrderTypeAvailability.buyChoices(availability);
        List<String> sell = OrderTypeAvailability.sellChoices(availability);
        boolean adjusted = (!buy.isEmpty() && !buy.contains(config.buyOrderType()))
                || (!sell.isEmpty() && !sell.contains(config.sellOrderType()));
        baseBuyOrderType.setModel(new DefaultComboBoxModel<>(buy.toArray(String[]::new)));
        baseSellOrderType.setModel(new DefaultComboBoxModel<>(sell.toArray(String[]::new)));
        baseBuyOrderType.setEnabled(!buy.isEmpty());
        baseSellOrderType.setEnabled(!sell.isEmpty());
        if (buy.contains(config.buyOrderType())) baseBuyOrderType.setSelectedItem(config.buyOrderType());
        else if (!buy.isEmpty()) baseBuyOrderType.setSelectedIndex(0);
        if (sell.contains(config.sellOrderType())) baseSellOrderType.setSelectedItem(config.sellOrderType());
        else if (!sell.isEmpty()) baseSellOrderType.setSelectedIndex(0);
        if (failed) {
            baseOrderTypeInfo.setText("Ordertyp-Kompatibilität konnte nicht geladen werden");
            baseOrderTypeInfo.setForeground(OneOfXTheme.ERROR);
        } else if (!availability.currencies().isEmpty()) {
            baseOrderTypeInfo.setText("Für " + String.join(", ", availability.currencies())
                    + " gemeinsam erlaubt: " + availability.display()
                    + (adjusted ? " · gespeicherte Auswahl wird angepasst" : ""));
            baseOrderTypeInfo.setForeground(availability.supported().isEmpty() || adjusted
                    ? OneOfXTheme.ERROR : OneOfXTheme.TEXT_MUTED);
        } else {
            baseOrderTypeInfo.setText("Noch keinem Handelspaar zugeordnet · alle Ordertypen auswählbar");
            baseOrderTypeInfo.setForeground(OneOfXTheme.TEXT_MUTED);
        }
        return !failed && adjusted;
    }

    private void refreshTerminalOrderTypes() {
        String currency = (String) terminalCurrencyBox.getSelectedItem();
        OrderType previous = (OrderType) terminalOrderType.getSelectedItem();
        List<OrderType> choices = List.of();
        if (currency != null) try {
            choices = OrderTypeAvailability.terminalChoices(orderTypeAvailability.forPair(currency));
        } catch (SQLException ex) {
            setMessage("Ordertypen für " + currency + " konnten nicht geladen werden.", true);
        }
        terminalOrderType.setModel(new DefaultComboBoxModel<>(choices.toArray(OrderType[]::new)));
        if (previous != null && choices.contains(previous)) terminalOrderType.setSelectedItem(previous);
        else if (!choices.isEmpty()) terminalOrderType.setSelectedIndex(0);
        terminalOrderType.setEnabled(!choices.isEmpty());
        terminalOrderType.setToolTipText(choices.isEmpty()
                ? "Für dieses Paar ist keine manuell unterstützte Orderart verfügbar."
                : "Für " + currency + " verfügbar: " + choices.stream()
                        .map(OrderTypeAvailability::label).collect(java.util.stream.Collectors.joining(", ")));
    }

    private static <T> boolean comboContains(JComboBox<T> combo, T value) {
        for (int index=0; index<combo.getItemCount(); index++) {
            if (java.util.Objects.equals(combo.getItemAt(index), value)) return true;
        }
        return false;
    }

    private void reloadPairPoolAssignment() throws SQLException {
        if (loadedBot == null || loadedCurrency == null) return;
        Long assignedId = baseConfigRepository.loadAssignedPoolId(loadedBot.id(), loadedCurrency);
        loadingPoolAssignment = true;
        try {
            pairPoolBox.removeAllItems();
            pairPoolBox.addItem("Keine Pool-Überschreibung");
            Object selected = pairPoolBox.getItemAt(0);
            for (ConfigPool pool : baseConfigRepository.loadPools(loadedBot.id())) {
                pairPoolBox.addItem(pool);
                if (assignedId != null && pool.id() == assignedId) selected = pool;
            }
            pairPoolBox.setSelectedItem(selected);
        } finally { loadingPoolAssignment = false; }
    }

    private void assignSelectedPool() {
        if (loadingPoolAssignment || loadedBot == null || loadedCurrency == null) return;
        Object selected = pairPoolBox.getSelectedItem();
        Long poolId = selected instanceof ConfigPool pool ? pool.id() : null;
        try {
            baseConfigRepository.assignPool(loadedBot.id(), loadedCurrency, poolId);
            reloadBaseConfigs(loadedBot.id(), loadedConfigPool == null ? null : loadedConfigPool.id());
            recordEvent("INFO", "CONFIG_POOL", loadedCurrency,
                    poolId == null ? "Pool-Zuordnung entfernt." : "Config Pool „" + selected + "“ zugeordnet.");
            setMessage(poolId == null ? "Das Paar verwendet die Bot-Baseconfig."
                    : "Config Pool „" + selected + "“ ist für " + loadedCurrency + " aktiv.", false);
        } catch (SQLException | IllegalArgumentException ex) {
            try { reloadPairPoolAssignment(); }
            catch (SQLException reload) { ex.addSuppressed(reload); }
            showError("Config Pool konnte nicht zugeordnet werden", ex);
        }
    }

    private boolean savePendingSettings() {
        if (!autoSaveDirty || loadedCurrency == null) return true;
        try {
            repository.save(readForm(loadedCurrency));
            autoSaveDirty = false;
            recordEvent("INFO", "KONFIGURATION", loadedCurrency,
                    "Strategie- und Risikowerte automatisch gespeichert.");
            setMessage("Einstellungen für " + loadedCurrency
                    + " automatisch gespeichert.", false);
            setSaveStatus(settingsSaveStatus, "Gespeichert", OneOfXTheme.SUCCESS);
            refreshDashboard();
            return true;
        } catch (SQLException | IllegalArgumentException ex) {
            setSaveStatus(settingsSaveStatus, "Nicht gespeichert: " + ex.getMessage(), OneOfXTheme.ERROR);
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
                        recordEvent("INFO", "HANDELSPAAR", settings.currency(),
                                "Handelspaar geprüft und hinzugefügt.");
                        reloadCurrencies(settings.currency());
                        if (loadedBot != null) reloadBaseConfigs(loadedBot.id(),
                                loadedConfigPool == null ? null : loadedConfigPool.id());
                        refreshStrategyTargets();
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

    private void refreshSelectedPairRules(JButton button) {
        String currency = selectedCurrency();
        if (currency == null || !flushAutoSave()) return;
        try {
            configureSessionFromFields();
            if (!FusionClientProvider.isConfigured()) {
                showPage(PAGE_ACCESS, "API-Zugang");
                throw new IllegalStateException(
                        "Für die Aktualisierung zuerst einen Fusion API-Key eingeben.");
            }
            BotProfile bot = selectedBot();
            if (bot == null) throw new IllegalStateException("Kein Bot ausgewählt.");
            CurrencySettings settings = repository.load(bot.id(), currency);
            button.setEnabled(false);
            setMessage("Trading-Regeln für " + currency + " werden aktualisiert …", false);
            new SwingWorker<TradingPair, Void>() {
                @Override protected TradingPair doInBackground() {
                    return pairValidator.validate(currency, FusionClientProvider.getClient());
                }

                @Override protected void done() {
                    button.setEnabled(true);
                    try {
                        TradingPair pair = get();
                        repository.addVerified(bot.id(), settings, pair);
                        refreshPairOrderTypeDisplay(currency);
                        refreshTerminalOrderTypes();
                        reloadBaseConfigs(bot.id(), loadedConfigPool == null
                                ? null : loadedConfigPool.id());
                        recordEvent("INFO", "HANDELSPAAR", currency,
                                "Trading-Regeln und Ordertypen aus Fusion aktualisiert.");
                        setMessage("Trading-Regeln für " + currency + " wurden aktualisiert.", false);
                    } catch (Exception ex) {
                        Throwable cause = ex.getCause() == null ? ex : ex.getCause();
                        showError("Trading-Regeln konnten nicht aktualisiert werden",
                                cause instanceof Exception exception
                                        ? exception : new RuntimeException(cause));
                    }
                }
            }.execute();
        } catch (SQLException | RuntimeException ex) {
            button.setEnabled(true);
            showError("Trading-Regeln konnten nicht aktualisiert werden", ex);
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
            recordEvent("INFO", "HANDELSPAAR", currency,
                    result.archived() ? "Handelspaar sicher archiviert."
                            : "Unbenutztes Handelspaar gelöscht.");
            reloadCurrencies(null);
            if (loadedBot != null) reloadBaseConfigs(loadedBot.id(),
                    loadedConfigPool == null ? null : loadedConfigPool.id());
            refreshStrategyTargets();
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
            if (!flushAutoSave() || !flushBotAutoSave() || !flushStrategyAutoSave()) return;
            configureSessionFromFields();
            refreshStartReadiness();
            if (!startReadinessIssues.isEmpty()) {
                ReadinessIssue first = startReadinessIssues.get(0);
                openReadinessTarget(first.target());
                throw new IllegalStateException(first.message());
            }
            BotProfile bot = selectedBot();
            orderTypeAvailability.validateBot(bot.id(), baseConfigRepository);
            if (bot.mode() == BotProfile.Mode.LIVE) {
                int answer = JOptionPane.showConfirmDialog(this,
                        "Der Bot „" + bot.name() + "“ wird live gestartet und kann Orders ausführen.\n"
                                + "Budget: " + money(bot.budget()) + " · Exposure-Limit: "
                                + money(bot.maxExposure()) + "\n"
                                + "Sind API-Key, Positionen und Risikowerte geprüft?",
                        "Live-Trading starten", JOptionPane.YES_NO_OPTION,
                        JOptionPane.WARNING_MESSAGE);
                if (answer != JOptionPane.YES_OPTION) return;
            } else {
                paperRepository.ensureAccount(bot.id(), bot.budget());
            }
            engine.start(bot, this::showEngineState);
        } catch (SQLException | RuntimeException ex) {
            showError("Trading konnte nicht gestartet werden", ex);
        }
    }

    private void showEngineState(State state) {
        boolean paper = selectedBot() != null && selectedBot().mode() == BotProfile.Mode.PAPER;
        switch (state) {
            case STARTING -> setEngineStatus("Startet …", OneOfXTheme.PRIMARY,
                    OneOfXTheme.PRIMARY_SOFT);
            case RUNNING -> setEngineStatus(paper ? "Paper aktiv" : "Live aktiv", OneOfXTheme.SUCCESS,
                    OneOfXTheme.SUCCESS_SOFT);
            case STOPPING -> setEngineStatus("Stoppt …", OneOfXTheme.ERROR,
                    OneOfXTheme.ERROR_SOFT);
            case FAILED -> setEngineStatus("Fehler", OneOfXTheme.ERROR,
                    OneOfXTheme.ERROR_SOFT);
            case STOPPED -> setEngineStatus("Gestoppt", OneOfXTheme.ERROR,
                    OneOfXTheme.ERROR_SOFT);
        }
        stopButton.setEnabled(state == State.RUNNING || state == State.STARTING);
        botBox.setEnabled(state == State.STOPPED);
        botMode.setEnabled(state == State.STOPPED);
        refreshStartReadiness();
        recordEvent(state == State.FAILED ? "ERROR" : "INFO", "ENGINE", null,
                "Trading-Engine: " + engineStatus.getText());
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
            refreshBotRiskSummary();
            refreshStartReadiness();
        } catch (SQLException ex) {
            setMessage("Dashboard konnte nicht aktualisiert werden: " + ex.getMessage(), true);
        }
    }

    private void refreshStartReadiness() {
        State state = engine.getState();
        if (state == State.RUNNING || state == State.STARTING) {
            startReadinessIssues = List.of();
            readinessStatus.setText("Bot läuft");
            readinessStatus.setForeground(OneOfXTheme.SUCCESS);
            readinessDetail.setText("Engine und Schutzlogik überwachen den ausgewählten Bot.");
            readinessFixButton.setVisible(false);
            startButton.setEnabled(false);
            return;
        }
        startReadinessIssues = evaluateStartReadiness();
        if (startReadinessIssues.isEmpty()) {
            BotProfile bot = selectedBot();
            readinessStatus.setText("Startbereit");
            readinessStatus.setForeground(OneOfXTheme.SUCCESS);
            readinessDetail.setText(bot == null ? "Alle Prüfungen sind erfüllt."
                    : bot.name() + " · " + bot.mode() + " · alle Prüfungen erfüllt");
            readinessFixButton.setVisible(false);
        } else {
            ReadinessIssue first = startReadinessIssues.get(0);
            readinessStatus.setText("Nicht startbereit · " + startReadinessIssues.size()
                    + (startReadinessIssues.size() == 1 ? " Punkt offen" : " Punkte offen"));
            readinessStatus.setForeground(OneOfXTheme.ERROR);
            readinessDetail.setText(first.message());
            readinessFixButton.setText(first.actionLabel());
            readinessFixButton.setVisible(true);
        }
        startButton.setEnabled(state == State.STOPPED && engine.canStart()
                && startReadinessIssues.isEmpty());
    }

    private List<ReadinessIssue> evaluateStartReadiness() {
        List<ReadinessIssue> issues = new ArrayList<>();
        BotProfile bot = selectedBot();
        if (bot == null) {
            issues.add(new ReadinessIssue("Es ist kein Bot ausgewählt.", "Bot auswählen",
                    ReadinessTarget.BOT_BASIS));
            return issues;
        }
        if (!hasSessionApiKey()) {
            issues.add(new ReadinessIssue("Für Marktdaten und Orders fehlt der Fusion API-Key.",
                    "API-Zugang öffnen", ReadinessTarget.API_ACCESS));
        }
        if (!bot.enabled()) {
            issues.add(new ReadinessIssue("Der ausgewählte Bot ist noch nicht aktiviert.",
                    "Bot aktivieren", ReadinessTarget.BOT_BASIS));
        }
        try {
            if (repository.loadDashboardStats(bot.id()).enabledCurrencies() == 0) {
                issues.add(new ReadinessIssue("Mindestens ein Handelspaar muss für Käufe aktiv sein.",
                        "Paare öffnen", ReadinessTarget.PAIR_SETTINGS));
            }
            if (!operationsRepository.loadUnresolvedAttempts(bot.id()).isEmpty()) {
                issues.add(new ReadinessIssue(
                        "Ungeklärte Orderübermittlungen müssen vor dem Start geprüft werden.",
                        "Ungeklärte öffnen", ReadinessTarget.UNRESOLVED));
            }
            try {
                orderTypeAvailability.validateBot(bot.id(), baseConfigRepository);
            } catch (IllegalArgumentException ex) {
                issues.add(new ReadinessIssue(ex.getMessage(), "Ordertypen prüfen",
                        ReadinessTarget.ADVANCED_CONFIG));
            }
        } catch (SQLException ex) {
            issues.add(new ReadinessIssue("Startprüfung fehlgeschlagen: " + ex.getMessage(),
                    "System öffnen", ReadinessTarget.SYSTEM));
        }
        return List.copyOf(issues);
    }

    private boolean hasSessionApiKey() {
        if (FusionClientProvider.isConfigured()) return true;
        char[] value = apiKey.getPassword();
        try {
            return value.length > 0;
        } finally {
            java.util.Arrays.fill(value, '\0');
        }
    }

    private void fixFirstReadinessIssue() {
        if (!startReadinessIssues.isEmpty()) openReadinessTarget(startReadinessIssues.get(0).target());
    }

    private void openReadinessTarget(ReadinessTarget target) {
        switch (target) {
            case API_ACCESS -> showPage(PAGE_ACCESS, "API-Zugang");
            case BOT_BASIS -> {
                showPage(PAGE_BOTS, "Bots");
                botTabs.setSelectedIndex(0);
            }
            case PAIR_SETTINGS -> showPage(PAGE_SETTINGS, "Einstellungen");
            case ADVANCED_CONFIG -> {
                showPage(PAGE_BOTS, "Bots");
                botTabs.setSelectedIndex(3);
            }
            case UNRESOLVED -> {
                showPage(PAGE_TRADE, "Handel");
                tradingTabs.setSelectedIndex(3);
            }
            case SYSTEM -> showPage(PAGE_SYSTEM, "System");
        }
    }

    private void refreshOperationalViews() {
        try {
            List<OperationsRepository.OpenOrderRow> orders =
                    operationsRepository.loadOpenOrders();
            replaceRows(openOrdersModel, orders.stream().map(row -> new Object[] {
                    row.side(), row.currency(), row.orderId(), decimal(row.price()),
                    decimal(row.quantity()), row.statusText()
            }).toList());
            openOrdersCount.setText(Integer.toString(orders.size()));

            List<OperationsRepository.AttemptRow> attempts =
                    operationsRepository.loadUnresolvedAttempts();
            replaceRows(unresolvedModel, attempts.stream().map(row -> new Object[] {
                    row.side(), row.currency(), row.attemptId(), value(row.exchangeOrderId()),
                    row.state(), value(row.error()), row.updatedAt()
            }).toList());
            unresolvedCount.setText(Integer.toString(attempts.size()));

            List<OperationsRepository.PositionRow> positions =
                    operationsRepository.loadPositions();
            replaceRows(positionsModel, positions.stream().map(row -> new Object[] {
                    row.currency(), row.orderId(), decimal(row.buyPrice()),
                    decimal(row.orderPrice()), decimal(row.quantity()),
                    money(row.buyAmount()), money(row.profit()), money(row.unrealizedPnl()),
                    row.statusText(), row.openedAt()
            }).toList());
            positionsViewCount.setText(Integer.toString(positions.size()));
            refreshTerminalOrderViews(orders,positions);
            portfolioPanel.refresh();

            List<OperationsRepository.WarningRow> warnings =
                    operationsRepository.loadWarnings();
            if (!hasSessionApiKey()) {
                warnings = new java.util.ArrayList<>(warnings);
                warnings.add(new OperationsRepository.WarningRow("HINWEIS", "API", null,
                        "Kein Fusion API-Key für diese Sitzung hinterlegt."));
            }
            replaceRows(warningModel, warnings.stream().map(row -> new Object[] {
                    row.severity(), row.category(), value(row.currency()), row.message()
            }).toList());
            warningsCount.setText(Integer.toString(warnings.size()));
            updateDashboardWarnings(warnings);
            refreshStartReadiness();

            List<OperationsRepository.ActivityRow> activity =
                    operationsRepository.loadActivity(500);
            replaceRows(activityModel, activity.stream().map(row -> new Object[] {
                    row.createdAt(), row.severity(), row.category(), value(row.currency()),
                    row.message()
            }).toList());
        } catch (SQLException ex) {
            setMessage("Cockpit konnte nicht aktualisiert werden: " + ex.getMessage(), true);
        }
    }

    private void updateDashboardWarnings(List<OperationsRepository.WarningRow> warnings) {
        dashboardWarningsList.removeAll();
        if (warnings.isEmpty()) {
            JLabel clear = new JLabel("✓ Kein aktueller Handlungsbedarf");
            clear.setForeground(OneOfXTheme.SUCCESS);
            clear.setFont(OneOfXTheme.font(Font.BOLD, 12));
            dashboardWarningsList.add(clear);
        } else {
            int visible = Math.min(3, warnings.size());
            for (int i = 0; i < visible; i++) {
                if (i > 0) dashboardWarningsList.add(Box.createVerticalStrut(8));
                dashboardWarningsList.add(createDashboardWarningRow(warnings.get(i)));
            }
            if (warnings.size() > visible) {
                dashboardWarningsList.add(Box.createVerticalStrut(8));
                JButton all = new JButton("Alle " + warnings.size() + " Hinweise öffnen");
                styleSecondaryButton(all);
                all.addActionListener(event -> showPage(PAGE_ACTIVITY, "Aktivität"));
                all.setAlignmentX(Component.LEFT_ALIGNMENT);
                dashboardWarningsList.add(all);
            }
        }
        dashboardWarningsList.revalidate();
        dashboardWarningsList.repaint();
    }

    private JPanel createDashboardWarningRow(OperationsRepository.WarningRow warning) {
        JPanel row = new JPanel(new BorderLayout(10, 0));
        row.setOpaque(false);
        String pair = warning.currency() == null || warning.currency().isBlank()
                ? "" : warning.currency() + " · ";
        JLabel text = new JLabel("<html><b>" + pair + warning.category() + "</b><br>"
                + warning.message() + "</html>");
        text.setForeground(OneOfXTheme.TEXT_SECONDARY);
        text.setFont(OneOfXTheme.font(Font.PLAIN, 11));
        row.add(text, BorderLayout.CENTER);
        JButton action = new JButton(warningActionLabel(warning));
        styleSecondaryButton(action);
        action.addActionListener(event -> openWarning(warning));
        row.add(action, BorderLayout.EAST);
        return row;
    }

    private static String warningActionLabel(OperationsRepository.WarningRow warning) {
        return switch (warning.category()) {
            case "API" -> "API-Zugang";
            case "Orderabgleich" -> "Prüfen";
            case "Handelsregeln", "Marktdaten" -> "Paar öffnen";
            default -> "Details";
        };
    }

    private void openWarning(OperationsRepository.WarningRow warning) {
        switch (warning.category()) {
            case "API" -> showPage(PAGE_ACCESS, "API-Zugang");
            case "Orderabgleich" -> {
                showPage(PAGE_TRADE, "Handel");
                tradingTabs.setSelectedIndex(3);
            }
            case "Handelsregeln", "Marktdaten" -> {
                if (warning.currency() != null) currencyBox.setSelectedItem(warning.currency());
                showPage(PAGE_SETTINGS, "Einstellungen");
            }
            default -> showPage(PAGE_ACTIVITY, "Aktivität");
        }
    }

    private void refreshGridPreview() {
        if (loadingSettings || loadedCurrency == null || gridPreviewContext == null) return;
        try {
            CurrencySettings settings = readPreviewForm(loadedCurrency);
            GridPreviewService.Preview preview = gridPreviewService.calculate(
                    settings, number(previewPrice), gridPreviewContext.rules());
            replaceRows(gridPreviewModel, preview.levels().stream().map(level -> new Object[] {
                    level.number(), level.price().stripTrailingZeros().toPlainString(),
                    level.quantity().stripTrailingZeros().toPlainString(),
                    level.notional().setScale(2, java.math.RoundingMode.HALF_UP)
                            .toPlainString(), level.status()
            }).toList());
            previewOrders.setText(preview.levels().size() + " / "
                    + preview.fundedOrderCount());
            previewCapital.setText(money(preview.requiredCapital()) + " von "
                    + money(settings.maxBuyAmount()));
            previewBand.setText(decimal(preview.bottomPrice()) + " · −"
                    + String.format(Locale.GERMANY, "%.2f %%", preview.coveragePercent()));
            if (preview.warnings().isEmpty()) {
                previewWarning.setText("✓ Tick-Größe und Mindestorder sind erfüllt.");
                previewWarning.setForeground(OneOfXTheme.SUCCESS);
            } else {
                previewWarning.setText("⚠ " + String.join("  ·  ", preview.warnings()));
                previewWarning.setForeground(OneOfXTheme.ERROR);
            }
        } catch (RuntimeException ex) {
            gridPreviewModel.setRowCount(0);
            previewWarning.setText("⚠ " + ex.getMessage());
            previewWarning.setForeground(OneOfXTheme.ERROR);
        }
    }

    private void refreshPreviewPrice(JButton button) {
        if (!flushAutoSave()) return;
        try {
            configureSessionFromFields();
            if (!FusionClientProvider.isConfigured()) {
                showPage(PAGE_ACCESS, "API-Zugang");
                throw new IllegalStateException(
                        "Für den Livekurs zuerst einen Fusion API-Key eingeben.");
            }
            String currency = loadedCurrency;
            if (currency == null) return;
            button.setEnabled(false);
            new SwingWorker<TickerPrice, Void>() {
                @Override
                protected TickerPrice doInBackground() {
                    return FusionClientProvider.getClient().getPrice(currency);
                }

                @Override
                protected void done() {
                    button.setEnabled(true);
                    try {
                        if (!currency.equals(loadedCurrency)) return;
                        double price = Double.parseDouble(get().getPrice());
                        previewPrice.setValue(price);
                        setMessage("Livekurs für die Grid-Vorschau geladen.", false);
                    } catch (Exception ex) {
                        showBackgroundError("Livekurs konnte nicht geladen werden", ex);
                    }
                }
            }.execute();
        } catch (RuntimeException ex) {
            showError("Livekurs konnte nicht geladen werden", ex);
        }
    }

    private CurrencySettings readPreviewForm(String currency) {
        return new CurrencySettings(currency, buyEnabled.isSelected(), number(buyAmount),
                number(maxBuyAmount), (GridMode) gridMode.getSelectedItem(),
                number(gridSpacing), number(stopLoss), trailingStop.isSelected(),
                number(trailingActivation), number(trailingDecline));
    }

    private void refreshBalances(JButton button) {
        try {
            BotProfile bot = selectedBot();
            if (bot != null && bot.mode() == BotProfile.Mode.PAPER) {
                paperRepository.ensureAccount(bot.id(), bot.budget());
                List<Object[]> rows = new java.util.ArrayList<>();
                for (PaperTradingRepository.Balance balance : paperRepository.loadBalances(bot.id())) {
                    rows.add(new Object[] {balance.asset(), plain(BigDecimal.valueOf(balance.available())),
                            plain(BigDecimal.valueOf(balance.reserved())),
                            plain(BigDecimal.valueOf(balance.total()))});
                }
                replaceRows(balancesModel, rows);
                setMessage(rows.size() + " virtuelle Paper-Kontostände geladen.", false);
                return;
            }
            configureSessionFromFields();
            if (!FusionClientProvider.isConfigured()) {
                showPage(PAGE_ACCESS, "API-Zugang");
                throw new IllegalStateException(
                        "Für Kontostände zuerst einen Fusion API-Key eingeben.");
            }
            button.setEnabled(false);
            setMessage("Kontostände werden von Fusion geladen …", false);
            new SwingWorker<Account, Void>() {
                @Override
                protected Account doInBackground() {
                    return FusionClientProvider.getClient().getAccount();
                }

                @Override
                protected void done() {
                    button.setEnabled(true);
                    try {
                        List<Object[]> rows = new java.util.ArrayList<>();
                        for (AssetBalance balance : get().getBalances()) {
                            BigDecimal free = new BigDecimal(balance.getFree());
                            BigDecimal locked = new BigDecimal(balance.getLocked());
                            if (free.signum() == 0 && locked.signum() == 0) continue;
                            rows.add(new Object[] {balance.getAsset(), plain(free),
                                    plain(locked), plain(free.add(locked))});
                        }
                        replaceRows(balancesModel, rows);
                        setMessage(rows.size() + " Kontostände von Fusion geladen.", false);
                        recordEvent("INFO", "KONTO", null,
                                "Kontostände erfolgreich aktualisiert.");
                    } catch (Exception ex) {
                        showBackgroundError("Kontostände konnten nicht geladen werden", ex);
                    }
                }
            }.execute();
        } catch (SQLException | RuntimeException ex) {
            showError("Kontostände konnten nicht geladen werden", ex);
        }
    }

    private void updateTrailingFields() {
        boolean trailing = trailingStop.isSelected();
        trailingActivation.setEnabled(trailing && trailingStop.isEnabled());
        trailingDecline.setEnabled(trailing && trailingStop.isEnabled());
        setFormRowVisible(trailingActivation, trailing);
        setFormRowVisible(trailingDecline, trailing);
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

    private void installBotAutoSaveListeners() {
        botEnabled.addActionListener(event -> scheduleBotAutoSave());
        botMode.addActionListener(event -> scheduleBotAutoSave());
        baseBuyOrderType.addActionListener(event -> scheduleBotAutoSave());
        baseSellOrderType.addActionListener(event -> scheduleBotAutoSave());
        baseTrailingBuy.addActionListener(event -> {
            updateBaseConfigFields();
            scheduleBotAutoSave();
        });
        baseOnlyProfit.addActionListener(event -> scheduleBotAutoSave());
        baseCloseAfterEnabled.addActionListener(event -> {
            if (baseCloseAfterEnabled.isSelected()
                    && ((Number) baseCloseAfter.getValue()).intValue() == 0) {
                baseCloseAfter.setValue(60);
            }
            updateBaseConfigFields();
            scheduleBotAutoSave();
        });
        baseDcaEnabled.addActionListener(event -> {
            updateBaseConfigFields();
            scheduleBotAutoSave();
        });
        for (JSpinner spinner : List.of(botBudget, botMaxExposure,
                botMaxPositions, botMaxOrders, botPaperFee, botPaperSlippage,
                baseBuyOrderMinutes, baseSellOrderMinutes, baseCooldownMinutes,
                baseTakeProfit, baseTrailingBuyActivation, baseTrailingBuyRebound,
                baseCloseAfter, baseDcaMaxOrders, baseDcaTrigger, baseDcaMultiplier)) {
            spinner.addChangeListener(event -> scheduleBotAutoSave());
        }
        DocumentListener listener = new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent event) { scheduleBotAutoSave(); }
            @Override public void removeUpdate(DocumentEvent event) { scheduleBotAutoSave(); }
            @Override public void changedUpdate(DocumentEvent event) { scheduleBotAutoSave(); }
        };
        botName.getDocument().addDocumentListener(listener);
        botStrategy.getDocument().addDocumentListener(listener);
    }

    private void updateBaseConfigFields() {
        boolean trailing = baseTrailingBuy.isSelected();
        baseTrailingBuyActivation.setEnabled(trailing);
        baseTrailingBuyRebound.setEnabled(trailing);
        setFormRowVisible(baseTrailingBuyActivation, trailing);
        setFormRowVisible(baseTrailingBuyRebound, trailing);

        boolean timedExit = baseCloseAfterEnabled.isSelected();
        baseCloseAfter.setEnabled(timedExit);
        setFormRowVisible(baseCloseAfter, timedExit);

        boolean dca = baseDcaEnabled.isSelected();
        baseDcaMaxOrders.setEnabled(dca);
        baseDcaTrigger.setEnabled(dca);
        baseDcaMultiplier.setEnabled(dca);
        setFormRowVisible(baseDcaMaxOrders, dca);
        setFormRowVisible(baseDcaTrigger, dca);
        setFormRowVisible(baseDcaMultiplier, dca);
    }

    private void scheduleAutoSave() {
        if (loadingSettings || loadedCurrency == null) return;
        autoSaveDirty = true;
        refreshGridPreview();
        setSaveStatus(settingsSaveStatus, "Wird gespeichert …", OneOfXTheme.PRIMARY);
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
        updateTrailingFields();
        pairPoolBox.setEnabled(enabled);
    }

    private void closeApplication() {
        if (!flushAutoSave() || !flushBotAutoSave() || !flushStrategyAutoSave()) {
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
        botSaveTimer.stop();
        strategySaveTimer.stop();
        dashboardTimer.stop();
        dispose();
    }

    private String selectedCurrency() {
        Object selected = currencyBox.getSelectedItem();
        return selected == null ? null : selected.toString();
    }

    private BotProfile selectedBot() {
        Object selected = botBox.getSelectedItem();
        return selected instanceof BotProfile bot ? bot : loadedBot;
    }

    private void showError(String title, Exception ex) {
        setMessage(title + ": " + ex.getMessage(), true);
        recordEvent("ERROR", "OBERFLÄCHE", loadedCurrency,
                title + ": " + ex.getMessage());
        JOptionPane.showMessageDialog(this, ex.getMessage(), title,
                JOptionPane.ERROR_MESSAGE);
    }

    private void showBackgroundError(String title, Exception ex) {
        Throwable cause = ex.getCause() == null ? ex : ex.getCause();
        setMessage(title + ": " + cause.getMessage(), true);
        recordEvent("ERROR", "API", loadedCurrency,
                title + ": " + cause.getMessage());
        if ("Backtest fehlgeschlagen".equals(title)) {
            backtestRunStatus.setForeground(OneOfXTheme.ERROR);
            backtestRunStatus.setText("Fehlgeschlagen: " + cause.getMessage());
            JOptionPane.showMessageDialog(this, cause.getMessage(), title,
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private void recordEvent(String severity, String category, String currency,
            String eventMessage) {
        try {
            operationsRepository.recordEvent(severity, category, currency, eventMessage);
        } catch (SQLException ignored) {
            // Die eigentliche Aktion darf nicht an der Protokollierung scheitern.
        }
    }

    private void setMessage(String text, boolean error) {
        message.setText(text);
        message.setForeground(error ? OneOfXTheme.ERROR : OneOfXTheme.TEXT_MUTED);
    }

    private static void configureSaveStatus(JLabel label) {
        label.setIcon(new LineIcon(IconType.CHECK, 13, OneOfXTheme.SUCCESS));
        label.setForeground(OneOfXTheme.TEXT_MUTED);
        label.setFont(OneOfXTheme.font(Font.PLAIN, 11));
    }

    private static void setSaveStatus(JLabel label, String text, Color color) {
        label.setText(text);
        label.setForeground(color);
        label.setIcon(new LineIcon(OneOfXTheme.ERROR.equals(color) ? IconType.SHIELD : IconType.CHECK,
                13, color));
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

    private static RoundedPanel tableCard(String title, String subtitle, JTable table,
            int height) {
        RoundedPanel card = cardPanel(new BorderLayout(0, 14));
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, height));
        card.setPreferredSize(new Dimension(0, height));
        card.add(titleBlock(title, subtitle), BorderLayout.NORTH);
        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(BorderFactory.createLineBorder(OneOfXTheme.BORDER));
        scroll.getViewport().setBackground(OneOfXTheme.SURFACE_RAISED);
        card.add(scroll, BorderLayout.CENTER);
        return card;
    }

    private RoundedPanel operationalTableCard(String title, String subtitle, JTable table,
            int height, String emptyDetail, String actionLabel, Runnable action) {
        RoundedPanel card = cardPanel(new BorderLayout(0, 12));
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, height));
        card.setPreferredSize(new Dimension(0, height));
        card.add(titleBlock(title, subtitle), BorderLayout.NORTH);

        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(BorderFactory.createLineBorder(OneOfXTheme.BORDER));
        scroll.getViewport().setBackground(OneOfXTheme.SURFACE_RAISED);
        card.add(scroll, BorderLayout.CENTER);

        JPanel detail = new JPanel(new BorderLayout(12, 0));
        detail.setOpaque(false);
        detail.setBorder(OneOfXTheme.padding(6, 0, 0, 0));
        JLabel detailText = new JLabel(emptyDetail);
        detailText.setForeground(OneOfXTheme.TEXT_MUTED);
        detailText.setFont(OneOfXTheme.font(Font.PLAIN, 11));
        detail.add(detailText, BorderLayout.CENTER);
        JButton actionButton = null;
        if (actionLabel != null && action != null) {
            actionButton = new JButton(actionLabel);
            styleSecondaryButton(actionButton);
            actionButton.setEnabled(false);
            actionButton.addActionListener(event -> action.run());
            detail.add(actionButton, BorderLayout.EAST);
        }
        JButton finalActionButton = actionButton;
        table.getSelectionModel().addListSelectionListener(event -> {
            if (event.getValueIsAdjusting()) return;
            int selected = table.getSelectedRow();
            if (selected < 0) {
                detailText.setText(emptyDetail);
                detailText.setToolTipText(null);
                if (finalActionButton != null) finalActionButton.setEnabled(false);
                return;
            }
            String text = tableSelectionDetail(table, selected);
            detailText.setText(text);
            detailText.setToolTipText(text);
            detailText.setForeground(OneOfXTheme.TEXT_SECONDARY);
            if (finalActionButton != null) finalActionButton.setEnabled(true);
        });
        card.add(detail, BorderLayout.SOUTH);
        return card;
    }

    static String tableSelectionDetail(JTable table, int selectedViewRow) {
        int row = table.convertRowIndexToModel(selectedViewRow);
        StringBuilder text = new StringBuilder("Ausgewählt: ");
        for (int column = 0; column < table.getColumnCount(); column++) {
            if (column > 0) text.append(" · ");
            Object value = table.getModel().getValueAt(row,
                    table.convertColumnIndexToModel(column));
            text.append(table.getColumnName(column)).append(": ").append(value(value == null
                    ? null : value.toString()));
        }
        return text.toString();
    }

    private void openTerminalForSelectedRow(JTable table) {
        int selected = table.getSelectedRow();
        if (selected >= 0) {
            int row = table.convertRowIndexToModel(selected);
            Object currency = table.getModel().getValueAt(row, 0);
            if (currency != null) terminalCurrencyBox.setSelectedItem(currency.toString());
        }
        showPage(PAGE_TERMINAL, "Trading-Terminal");
    }

    private static RoundedPanel previewSummary(String labelText, JLabel value) {
        RoundedPanel panel = new RoundedPanel(new BorderLayout(0, 4), 12);
        panel.setFill(OneOfXTheme.SURFACE);
        panel.setBorder(OneOfXTheme.padding(10, 12, 10, 12));
        JLabel label = new JLabel(labelText);
        label.setForeground(OneOfXTheme.TEXT_MUTED);
        label.setFont(OneOfXTheme.font(Font.BOLD, 9));
        value.setForeground(OneOfXTheme.TEXT);
        value.setFont(OneOfXTheme.font(Font.BOLD, 13));
        panel.add(label, BorderLayout.NORTH);
        panel.add(value, BorderLayout.CENTER);
        return panel;
    }

    private static DefaultTableModel readOnlyModel(String... columns) {
        return new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
    }

    private static JTable cockpitTable(DefaultTableModel model) {
        JTable table = new JTable(model);
        table.setFillsViewportHeight(true);
        table.setRowHeight(30);
        table.setShowVerticalLines(false);
        table.setGridColor(OneOfXTheme.BORDER);
        table.setBackground(OneOfXTheme.SURFACE_RAISED);
        table.setForeground(OneOfXTheme.TEXT_SECONDARY);
        table.setSelectionBackground(OneOfXTheme.PRIMARY_SOFT);
        table.setSelectionForeground(OneOfXTheme.TEXT);
        table.getTableHeader().setBackground(OneOfXTheme.SURFACE);
        table.getTableHeader().setForeground(OneOfXTheme.TEXT_MUTED);
        table.getTableHeader().setFont(OneOfXTheme.font(Font.BOLD, 10));
        table.setFont(OneOfXTheme.font(Font.PLAIN, 11));
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        DefaultTableCellRenderer renderer = new DefaultTableCellRenderer();
        renderer.setBorder(OneOfXTheme.padding(0, 8, 0, 8));
        table.setDefaultRenderer(Object.class, renderer);
        table.setAutoCreateRowSorter(true);
        for (int i = 0; i < table.getColumnCount(); i++) {
            String name = table.getColumnName(i);
            int width = switch (name) {
                case "#" -> 55;
                case "Order-ID", "Versuch-ID", "Exchange-ID" -> 210;
                case "Fehler", "Hinweis", "Ereignis", "Signalerklärung" -> 360;
                case "Aktualisiert", "Eröffnet", "Zeit" -> 150;
                case "Status", "Zustand", "Prüfung" -> 150;
                default -> 115;
            };
            table.getColumnModel().getColumn(i).setPreferredWidth(width);
        }
        return table;
    }

    private static void replaceRows(DefaultTableModel model, List<Object[]> rows) {
        model.setRowCount(0);
        for (Object[] row : rows) model.addRow(row);
    }

    private static String decimal(double value) {
        if (!Double.isFinite(value)) return "–";
        return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }

    private static String money(double value) {
        return String.format(Locale.GERMANY, "%.2f €", value);
    }

    private static String plain(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    private static String value(String value) {
        return value == null || value.isBlank() ? "–" : value;
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

    private static JPanel compactField(String labelText,JComponent component) {
        JPanel field=new JPanel(new BorderLayout(0,6));field.setOpaque(false);
        JLabel label=fieldLabel(labelText);field.add(label,BorderLayout.NORTH);
        component.setPreferredSize(new Dimension(
                Math.max(125,component.getPreferredSize().width),40));
        OneOfXTheme.round(component);field.add(component,BorderLayout.CENTER);
        return field;
    }

    static void addFormRow(JPanel form, int row, String labelText,
            JComponent component, String suffixText) {
        addFormRowWithSuffix(form, row, labelText, component,
                suffixText == null ? null : new JLabel(suffixText));
    }

    private static void addFormRowWithSuffix(JPanel form, int row, String labelText,
            JComponent component, JLabel suffix) {
        OneOfXTheme.round(component);
        JPanel controlSlot = new JPanel(new BorderLayout());
        controlSlot.setOpaque(false);
        Dimension controlSize = new Dimension(FORM_CONTROL_WIDTH, 40);
        controlSlot.setMinimumSize(controlSize);
        controlSlot.setPreferredSize(controlSize);
        controlSlot.add(component, BorderLayout.CENTER);

        JLabel label = new JLabel(labelText);
        label.setForeground(OneOfXTheme.TEXT_SECONDARY);
        label.setFont(OneOfXTheme.font(Font.PLAIN, 12));
        Dimension labelSize = new Dimension(FORM_LABEL_WIDTH, 40);
        label.setMinimumSize(labelSize);
        label.setPreferredSize(labelSize);

        GridBagConstraints left = new GridBagConstraints();
        left.gridx = 0;
        left.gridy = row;
        left.weightx = 0.0;
        left.anchor = GridBagConstraints.LINE_START;
        left.fill = GridBagConstraints.HORIZONTAL;
        left.insets = new Insets(6, 0, 6, 12);
        form.add(label, left);

        GridBagConstraints spacer = new GridBagConstraints();
        spacer.gridx = 1;
        spacer.gridy = row;
        spacer.weightx = 1.0;
        spacer.fill = GridBagConstraints.HORIZONTAL;
        form.add(Box.createHorizontalStrut(0), spacer);

        GridBagConstraints right = new GridBagConstraints();
        right.gridx = 2;
        right.gridy = row;
        right.weightx = 1.0;
        right.anchor = GridBagConstraints.LINE_END;
        right.fill = GridBagConstraints.HORIZONTAL;
        right.insets = new Insets(6, 12, 6, 0);
        form.add(controlSlot, right);

        JLabel unit = suffix == null ? new JLabel("") : suffix;
        unit.setForeground(OneOfXTheme.TEXT_MUTED);
        unit.setFont(OneOfXTheme.font(Font.PLAIN, 11));
        Dimension suffixSize = new Dimension(FORM_SUFFIX_WIDTH, 40);
        unit.setMinimumSize(suffixSize);
        unit.setPreferredSize(suffixSize);
        GridBagConstraints suffixConstraints = new GridBagConstraints();
        suffixConstraints.gridx = 3;
        suffixConstraints.gridy = row;
        suffixConstraints.weightx = 0.0;
        suffixConstraints.anchor = GridBagConstraints.LINE_START;
        suffixConstraints.insets = new Insets(6, 7, 6, 0);
        form.add(unit, suffixConstraints);
    }

    static void setFormRowVisible(JComponent component, boolean visible) {
        if (component.getParent() == null || component.getParent().getParent() == null) return;
        Component controlSlot = component.getParent();
        java.awt.Container form = controlSlot.getParent();
        if (!(form.getLayout() instanceof GridBagLayout layout)) {
            component.setVisible(visible);
            return;
        }
        int row = layout.getConstraints(controlSlot).gridy;
        for (Component child : form.getComponents()) {
            if (layout.getConstraints(child).gridy == row) child.setVisible(visible);
        }
        form.revalidate();
        form.repaint();
    }

    private static JLabel fieldLabel(String text) {
        JLabel label = new JLabel(text);
        label.setForeground(OneOfXTheme.TEXT_MUTED);
        label.setFont(OneOfXTheme.font(Font.BOLD, 10));
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        return label;
    }

    static <T> void useFriendlyNames(JComboBox<T> combo,
            java.util.function.Function<T, String> formatter) {
        javax.swing.DefaultListCellRenderer defaultRenderer = new javax.swing.DefaultListCellRenderer();
        combo.setRenderer((list, value, index, selected, focus) -> {
            JLabel label = (JLabel) defaultRenderer.getListCellRendererComponent(
                    list, value, index, selected, focus);
            label.setText(value == null ? "" : formatter.apply(value));
            label.setBorder(OneOfXTheme.padding(0, 8, 0, 8));
            return label;
        });
    }

    private static String actionLabel(Action action) {
        return switch (action) {
            case BUY -> "Kaufen";
            case SELL -> "Verkaufen";
            case BLOCK -> "Kauf sperren";
            case CONFIRM -> "Bestätigung";
        };
    }

    private static String logicLabel(Logic logic) {
        return logic == Logic.OR ? "Mindestens eine Regel (ODER)" : "Alle Regeln (UND)";
    }

    private static String comparatorLabel(Comparator comparator) {
        return switch (comparator) {
            case GREATER_THAN -> "ist größer als";
            case GREATER_OR_EQUAL -> "ist größer oder gleich";
            case LESS_THAN -> "ist kleiner als";
            case LESS_OR_EQUAL -> "ist kleiner oder gleich";
            case CROSS_ABOVE -> "kreuzt nach oben";
            case CROSS_BELOW -> "kreuzt nach unten";
        };
    }

    private static String indicatorLabel(Indicator indicator) {
        return switch (indicator) {
            case VALUE -> "Fester Wert";
            case PRICE -> "Aktueller Preis";
            case SMA -> "Gleitender Durchschnitt (SMA)";
            case EMA -> "Exponentieller Durchschnitt (EMA)";
            case RSI -> "RSI";
            case MACD -> "MACD";
            case MACD_SIGNAL -> "MACD-Signallinie";
            case STOCH_RSI -> "Stochastischer RSI";
            case BOLLINGER_UPPER -> "Oberes Bollinger-Band";
            case BOLLINGER_LOWER -> "Unteres Bollinger-Band";
            case ATR -> "Durchschnittliche Handelsspanne (ATR)";
            case CCI -> "Commodity Channel Index (CCI)";
            case VOLUME -> "Handelsvolumen";
            case BULLISH_ENGULFING -> "Bullish Engulfing";
            case BEARISH_ENGULFING -> "Bearish Engulfing";
            case HAMMER -> "Hammer-Kerze";
            case SHOOTING_STAR -> "Shooting-Star-Kerze";
        };
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

    private enum ReadinessTarget {
        API_ACCESS, BOT_BASIS, PAIR_SETTINGS, ADVANCED_CONFIG, UNRESOLVED, SYSTEM
    }

    private record ReadinessIssue(String message, String actionLabel, ReadinessTarget target) { }

    private enum AssignmentKind { BOT, POOL, PAIR, MARKET }

    private record AssignmentTarget(AssignmentKind kind, long id, String value,
            String label) {
        @Override public String toString() { return label; }
    }

    private record PositionChoice(String id,double quantity) {
        @Override public String toString() {
            String shortId=id.length()>14?id.substring(0,14)+"…":id;
            return shortId+" · "+decimal(quantity);
        }
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

    private static final class EquityCurvePanel extends JPanel {
        private List<BacktestEquityPoint> points=List.of();
        private EquityCurvePanel(){setOpaque(false);setPreferredSize(new Dimension(0,145));}
        private void setPoints(List<BacktestEquityPoint> value){points=value==null?List.of():List.copyOf(value);repaint();}
        @Override protected void paintComponent(Graphics graphics){super.paintComponent(graphics);Graphics2D g=(Graphics2D)graphics.create();g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);int left=58,right=18,top=16,bottom=25,w=Math.max(1,getWidth()-left-right),h=Math.max(1,getHeight()-top-bottom);g.setColor(OneOfXTheme.BORDER);g.drawLine(left,top,left,top+h);g.drawLine(left,top+h,left+w,top+h);if(points.size()<2){g.setColor(OneOfXTheme.TEXT_MUTED);g.drawString("Noch keine Equity-Daten",left+12,top+h/2);g.dispose();return;}double min=points.stream().mapToDouble(BacktestEquityPoint::equity).min().orElse(0),max=points.stream().mapToDouble(BacktestEquityPoint::equity).max().orElse(1);if(max-min<1e-9){max+=1;min-=1;}g.setColor(OneOfXTheme.TEXT_MUTED);g.setFont(OneOfXTheme.font(Font.PLAIN,10));g.drawString(decimal(max),4,top+5);g.drawString(decimal(min),4,top+h);g.setColor(points.get(points.size()-1).equity()>=points.get(0).equity()?OneOfXTheme.SUCCESS:OneOfXTheme.ERROR);g.setStroke(new BasicStroke(2f,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));int previousX=left,previousY=top+h-(int)Math.round((points.get(0).equity()-min)/(max-min)*h);for(int i=1;i<points.size();i++){int x=left+(int)Math.round(i*w/(double)(points.size()-1));int y=top+h-(int)Math.round((points.get(i).equity()-min)/(max-min)*h);g.drawLine(previousX,previousY,x,y);previousX=x;previousY=y;}g.dispose();}
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
