package com.oneofx.fusion.tradingbot.desktop;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Supplier;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.table.DefaultTableModel;

import com.oneofx.fusion.tradingbot.desktop.PortfolioRepository.AllocationRow;
import com.oneofx.fusion.tradingbot.desktop.PortfolioRepository.PerformanceRow;
import com.oneofx.fusion.tradingbot.desktop.PortfolioRepository.PortfolioSummary;
import com.oneofx.fusion.tradingbot.desktop.PortfolioRepository.TradeHistoryRow;
import com.oneofx.fusion.tradingbot.strategy.StrategyDefinition;
import com.oneofx.fusion.tradingbot.strategy.StrategyRepository;

/** Portfolio management workspace embedded in the native desktop application. */
final class PortfolioPanel extends JPanel {
    private final Supplier<BotProfile> botSupplier;
    private final Consumer<String> messages;
    private final PortfolioRepository repository=new PortfolioRepository();
    private final StrategyRepository strategies=new StrategyRepository();
    private final DefaultTableModel allocationsModel=model("Quelle","Paar","Position","Menge","Gruppe","Strategie","Bezeichnung");
    private final DefaultTableModel historyModel=model("Modus","Paar","Position","Eröffnet","Geschlossen","Kauf","Verkauf","Menge","Gebühren","Realisiert","Unrealisiert","Status","Strategie");
    private final DefaultTableModel performanceModel=model("Gruppe","Trades","Ergebnis","Gebühren","Trefferquote");
    private final JTable allocationsTable=table(allocationsModel);
    private final JTable historyTable=table(historyModel);
    private final JTable performanceTable=table(performanceModel);
    private final JLabel realized=value();private final JLabel unrealized=value();
    private final JLabel fees=value();private final JLabel capital=value();
    private final JTextField search=new JTextField(22);
    private final JComboBox<Object> strategyBox=new JComboBox<>();
    private final JComboBox<String> grouping=new JComboBox<>(new String[]{"Handelspaar","Strategie","Bot","Modus"});
    private final JComboBox<String> period=new JComboBox<>(new String[]{"30 Tage","7 Tage","Gesamt"});
    private List<AllocationRow> allocations=List.of();private List<TradeHistoryRow> history=List.of();

    PortfolioPanel(Supplier<BotProfile> botSupplier,Consumer<String> messages){
        this.botSupplier=botSupplier;this.messages=messages;setLayout(new BorderLayout(0,14));
        setOpaque(false);setBorder(OneOfXTheme.padding(4,0,20,0));
        JPanel metrics=new JPanel(new GridLayout(1,4,12,0));metrics.setOpaque(false);
        metrics.add(metric("REALISIERT",realized,OneOfXTheme.SUCCESS));
        metrics.add(metric("UNREALISIERT",unrealized,OneOfXTheme.INFO));
        metrics.add(metric("GEBÜHREN",fees,OneOfXTheme.PRIMARY));
        metrics.add(metric("KAPITALBINDUNG",capital,OneOfXTheme.TEXT));add(metrics,BorderLayout.NORTH);
        JTabbedPane tabs=new JTabbedPane();tabs.addTab("Positionsteile",allocationsTab());
        tabs.addTab("Handelsverlauf",historyTab());tabs.addTab("Performance",performanceTab());
        add(tabs,BorderLayout.CENTER);
    }

    void refresh(){BotProfile bot=botSupplier.get();if(bot==null)return;try{
        allocations=repository.loadAllocations(bot.id());replace(allocationsModel,allocations.stream().map(row->new Object[]{
                row.sourceKind(),row.currency(),shortId(row.sourcePositionId()),number(row.quantity()),
                text(row.groupName()),text(row.strategyName()),text(row.label())}).toList());
        history=repository.loadTradeHistory(bot.id(),search.getText(),5000);replace(historyModel,history.stream().map(row->new Object[]{
                row.origin(),row.currency(),shortId(row.positionId()),row.openedAt(),row.closedAt(),number(row.entryPrice()),
                number(row.exitPrice()),number(row.quantity()),money(row.fees()),money(row.realizedPnl()),
                money(row.unrealizedPnl()),row.status(),row.strategy()}).toList());
        PortfolioSummary summary=repository.loadSummary(bot.id());realized.setText(money(summary.realizedPnl()));
        unrealized.setText(money(summary.unrealizedPnl()));fees.setText(money(summary.fees()));
        capital.setText(money(summary.committedCapital()));reloadStrategies(bot.id());refreshPerformance();
    }catch(Exception ex){error("Portfolio konnte nicht geladen werden",ex);}}

    private JPanel allocationsTab(){JPanel panel=new JPanel(new BorderLayout(0,10));panel.setOpaque(false);
        JPanel actions=bar();JButton split=new JButton("Teilen");JButton merge=new JButton("Zusammenführen");
        JButton reserve=new JButton("Für Strategie reservieren");JButton release=new JButton("Freigeben");
        actions.add(split);actions.add(merge);actions.add(strategyBox);actions.add(reserve);actions.add(release);
        split.addActionListener(e->split());merge.addActionListener(e->merge());reserve.addActionListener(e->reserve(false));
        release.addActionListener(e->reserve(true));panel.add(actions,BorderLayout.NORTH);
        allocationsTable.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        panel.add(scroll(allocationsTable),BorderLayout.CENTER);return panel;}

    private JPanel historyTab(){JPanel panel=new JPanel(new BorderLayout(0,10));panel.setOpaque(false);
        JPanel actions=bar();actions.add(new JLabel("Suche"));actions.add(search);JButton apply=new JButton("Filtern");
        JButton export=new JButton("CSV exportieren");actions.add(apply);actions.add(export);
        apply.addActionListener(e->refresh());search.addActionListener(e->refresh());export.addActionListener(e->export());
        panel.add(actions,BorderLayout.NORTH);panel.add(scroll(historyTable),BorderLayout.CENTER);return panel;}

    private JPanel performanceTab(){JPanel panel=new JPanel(new BorderLayout(0,10));panel.setOpaque(false);
        JPanel actions=bar();actions.add(new JLabel("Gruppierung"));actions.add(grouping);
        actions.add(new JLabel("Zeitraum"));actions.add(period);JButton refresh=new JButton("Auswerten");
        refresh.addActionListener(e->refreshPerformance());actions.add(refresh);panel.add(actions,BorderLayout.NORTH);
        panel.add(scroll(performanceTable),BorderLayout.CENTER);return panel;}

    private void split(){AllocationRow row=single();if(row==null)return;JSpinner amount=new JSpinner(
            new SpinnerNumberModel(row.quantity()/2.0,0.00000001,row.quantity()-0.00000001,row.quantity()/10.0));
        if(JOptionPane.showConfirmDialog(this,amount,"Menge des ersten Teils",JOptionPane.OK_CANCEL_OPTION)
                !=JOptionPane.OK_OPTION)return;try{repository.split(bot().id(),row.allocationId(),
                        ((Number)amount.getValue()).doubleValue());done("Position wurde geteilt.");}
        catch(Exception ex){error("Position konnte nicht geteilt werden",ex);}}
    private void merge(){List<String> ids=selectedIds();if(ids.size()<2){message("Mindestens zwei Positionsteile auswählen.");return;}
        String name=JOptionPane.showInputDialog(this,"Name der Positionsgruppe:","Positionsgruppe");if(name==null)return;
        try{repository.merge(bot().id(),ids,name);done("Positionsteile wurden gruppiert.");}
        catch(Exception ex){error("Zusammenführen fehlgeschlagen",ex);}}
    private void reserve(boolean release){List<String> ids=selectedIds();if(ids.isEmpty()){message("Positionsteile auswählen.");return;}
        StrategyDefinition strategy=strategyBox.getSelectedItem() instanceof StrategyDefinition value?value:null;
        if(!release&&strategy==null){message("Strategie auswählen.");return;}try{
            repository.reserve(bot().id(),ids,release?null:strategy.id());done(release?"Reservierung aufgehoben.":"Strategie reserviert.");
        }catch(Exception ex){error("Reservierung fehlgeschlagen",ex);}}
    private void export(){if(history.isEmpty()){message("Keine gefilterten Handelsdaten vorhanden.");return;}
        JFileChooser chooser=new JFileChooser();chooser.setSelectedFile(new File("oneofx-handelsverlauf.csv"));
        if(chooser.showSaveDialog(this)!=JFileChooser.APPROVE_OPTION)return;try{
            repository.exportCsv(chooser.getSelectedFile().toPath(),history);message("CSV exportiert: "+chooser.getSelectedFile());
        }catch(Exception ex){error("CSV-Export fehlgeschlagen",ex);}}
    private void refreshPerformance(){BotProfile bot=botSupplier.get();if(bot==null)return;try{
        String dimension=switch(grouping.getSelectedIndex()){case 1->"STRATEGY";case 2->"BOT";case 3->"MODE";default->"PAIR";};
        int days=switch(period.getSelectedIndex()){case 0->30;case 1->7;default->0;};
        List<PerformanceRow> rows=repository.loadPerformance(bot.id(),dimension,days);
        replace(performanceModel,rows.stream().map(row->new Object[]{row.group(),row.trades(),money(row.realizedPnl()),
                money(row.fees()),String.format(Locale.GERMANY,"%.2f %%",row.winRatePercent())}).toList());
    }catch(Exception ex){error("Performance konnte nicht geladen werden",ex);}}

    private void reloadStrategies(long botId)throws Exception{Object selected=strategyBox.getSelectedItem();Long id=selected instanceof StrategyDefinition s?s.id():null;
        strategyBox.removeAllItems();strategyBox.addItem("Strategie wählen");for(StrategyDefinition s:strategies.loadAll(botId))strategyBox.addItem(s);
        if(id!=null)for(int i=1;i<strategyBox.getItemCount();i++)if(((StrategyDefinition)strategyBox.getItemAt(i)).id()==id)strategyBox.setSelectedIndex(i);}
    private AllocationRow single(){int[] selected=allocationsTable.getSelectedRows();if(selected.length!=1){message("Genau einen Positionsteil auswählen.");return null;}
        return allocations.get(allocationsTable.convertRowIndexToModel(selected[0]));}
    private List<String> selectedIds(){List<String> result=new ArrayList<>();for(int view:allocationsTable.getSelectedRows())
        result.add(allocations.get(allocationsTable.convertRowIndexToModel(view)).allocationId());return result;}
    private BotProfile bot(){BotProfile value=botSupplier.get();if(value==null)throw new IllegalStateException("Kein Bot ausgewählt.");return value;}
    private void done(String text){message(text);refresh();}private void message(String text){messages.accept(text);}
    private void error(String title,Exception ex){messages.accept(title+": "+ex.getMessage());JOptionPane.showMessageDialog(this,ex.getMessage(),title,JOptionPane.ERROR_MESSAGE);}
    private static JPanel bar(){JPanel panel=new JPanel(new FlowLayout(FlowLayout.LEFT,8,4));panel.setOpaque(false);return panel;}
    private static JPanel metric(String title,JLabel value,Color color){JPanel panel=new JPanel(new BorderLayout(0,8));panel.setBackground(OneOfXTheme.SURFACE_RAISED);
        panel.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(OneOfXTheme.BORDER),OneOfXTheme.padding(14,16,14,16)));
        JLabel label=new JLabel(title);label.setForeground(OneOfXTheme.TEXT_MUTED);label.setFont(OneOfXTheme.font(Font.BOLD,10));
        value.setForeground(color);panel.add(label,BorderLayout.NORTH);panel.add(value,BorderLayout.CENTER);return panel;}
    private static JLabel value(){JLabel label=new JLabel("–");label.setFont(OneOfXTheme.font(Font.BOLD,20));return label;}
    private static JTable table(DefaultTableModel model){JTable table=new JTable(model);table.setAutoCreateRowSorter(true);table.setRowHeight(29);
        table.setFillsViewportHeight(true);table.setBackground(OneOfXTheme.SURFACE_RAISED);table.setForeground(OneOfXTheme.TEXT_SECONDARY);
        table.setSelectionBackground(OneOfXTheme.PRIMARY_SOFT);table.setGridColor(OneOfXTheme.BORDER);table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        for(int i=0;i<table.getColumnCount();i++)table.getColumnModel().getColumn(i).setPreferredWidth(i==2?190:125);return table;}
    private static JScrollPane scroll(JTable table){JScrollPane scroll=new JScrollPane(table);scroll.setPreferredSize(new Dimension(900,330));return scroll;}
    private static DefaultTableModel model(String... columns){return new DefaultTableModel(columns,0){@Override public boolean isCellEditable(int r,int c){return false;}};}
    private static void replace(DefaultTableModel model,List<Object[]> rows){model.setRowCount(0);for(Object[] row:rows)model.addRow(row);}
    private static String text(String value){return value==null||value.isBlank()?"–":value;}
    private static String number(double value){return Double.toString(value);}
    private static String money(double value){return String.format(Locale.GERMANY,"%.2f €",value);}
    private static String shortId(String id){return id==null?"–":id.length()>18?id.substring(0,18)+"…":id;}
}
