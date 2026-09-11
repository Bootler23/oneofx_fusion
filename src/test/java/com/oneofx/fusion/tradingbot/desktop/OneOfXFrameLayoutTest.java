package com.oneofx.fusion.tradingbot.desktop;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.awt.GridBagLayout;
import java.awt.Component;

import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableModel;

import org.junit.Test;

public class OneOfXFrameLayoutTest {
    @Test public void formularfelderBleibenMitUndOhneEinheitGleichLang()
            throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JPanel form = new JPanel(new GridBagLayout());
            JSpinner euro = new JSpinner(new SpinnerNumberModel(5_000, 0, 10_000, 100));
            JSpinner withoutSuffix = new JSpinner(new SpinnerNumberModel(20, 0, 100, 1));
            JSpinner percent = new JSpinner(new SpinnerNumberModel(0.25, 0.0, 10.0, 0.01));

            OneOfXFrame.addFormRow(form, 0, "Gesamtbudget", euro, "EUR");
            OneOfXFrame.addFormRow(form, 1, "Offene Positionen", withoutSuffix, null);
            OneOfXFrame.addFormRow(form, 2, "Paper-Gebühr", percent, "%");
            form.setSize(800, 180);
            form.doLayout();

            assertEquals(euro.getParent().getX(), withoutSuffix.getParent().getX());
            assertEquals(euro.getParent().getX(), percent.getParent().getX());
            assertEquals(euro.getParent().getWidth(), withoutSuffix.getParent().getWidth());
            assertEquals(euro.getParent().getWidth(), percent.getParent().getWidth());
        });
    }

    @Test public void getrennteFormularspaltenNutzenDasselbeFeldraster()
            throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JPanel first = new JPanel(new GridBagLayout());
            JPanel second = new JPanel(new GridBagLayout());
            JTextField text = new JTextField(24);
            JComboBox<String> selection = new JComboBox<>(new String[] {"MARKET", "LIMIT"});

            OneOfXFrame.addFormRow(first, 0, "Kurz", text, null);
            OneOfXFrame.addFormRow(second, 0, "Verkaufsorder-Laufzeit", selection, "Min");
            first.setSize(600, 70);
            second.setSize(600, 70);
            first.doLayout();
            second.doLayout();

            assertEquals(text.getParent().getX(), selection.getParent().getX());
            assertEquals(text.getParent().getWidth(), selection.getParent().getWidth());
        });
    }

    @Test public void auswahlfelderZeigenDenFormatiertenWertImFeldUndPopup()
            throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JComboBox<String> selection = new JComboBox<>(new String[] {"RSI", "MACD"});
            OneOfXFrame.useFriendlyNames(selection, value -> "Indikator: " + value);
            JList<String> list = new JList<>(new String[] {"RSI", "MACD"});

            Component closed = selection.getRenderer().getListCellRendererComponent(
                    list, "RSI", -1, false, false);
            assertEquals("Indikator: RSI", ((JLabel) closed).getText());

            Component popup = selection.getRenderer().getListCellRendererComponent(
                    list, "MACD", 1, true, true);

            assertEquals("Indikator: MACD", ((JLabel) popup).getText());
        });
    }

    @Test public void abhaengigeFormularzeileWirdVollstaendigAusgeblendet()
            throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JPanel form = new JPanel(new GridBagLayout());
            JSpinner dependent = new JSpinner(new SpinnerNumberModel(1, 0, 100, 1));
            OneOfXFrame.addFormRow(form, 0, "Abhängiger Wert", dependent, "%");

            OneOfXFrame.setFormRowVisible(dependent, false);
            for (Component child : form.getComponents()) assertFalse(child.isVisible());

            OneOfXFrame.setFormRowVisible(dependent, true);
            for (Component child : form.getComponents()) assertTrue(child.isVisible());
        });
    }

    @Test public void tabellendetailZeigtDenAusgewaehltenDatensatz()
            throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JTable table = new JTable(new DefaultTableModel(
                    new Object[][] {{"BTC-EUR", "Offen"}}, new Object[] {"Paar", "Status"}));
            assertEquals("Ausgewählt: Paar: BTC-EUR · Status: Offen",
                    OneOfXFrame.tableSelectionDetail(table, 0));
        });
    }
}
