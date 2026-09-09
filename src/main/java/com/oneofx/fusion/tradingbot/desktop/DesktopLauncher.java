package com.oneofx.fusion.tradingbot.desktop;

import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

import com.oneofx.fusion.tradingbot.Database.DatabaseSchema;
import com.oneofx.fusion.tradingbot.Database.PortablePaths;

/** Programmeinstieg der Desktop-Oberfläche. */
public final class DesktopLauncher {

    private DesktopLauncher() {
    }

    public static void main(String[] args) {
        try {
            PortablePaths.initialize();
            DatabaseSchema.initialize();
            OneOfXTheme.install();
            JFrame.setDefaultLookAndFeelDecorated(true);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(null,
                    "OneOfX konnte nicht initialisiert werden:\n" + ex.getMessage(),
                    "Startfehler", JOptionPane.ERROR_MESSAGE);
            return;
        }

        SwingUtilities.invokeLater(() -> new OneOfXFrame().setVisible(true));
    }
}
