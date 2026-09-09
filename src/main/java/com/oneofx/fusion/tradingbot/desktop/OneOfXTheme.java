package com.oneofx.fusion.tradingbot.desktop;

import java.awt.Color;
import java.awt.Font;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.UIManager;
import javax.swing.border.Border;

import com.formdev.flatlaf.FlatDarkLaf;

/** Zentrale Design-Tokens der OneOfX-Desktopoberfläche. */
final class OneOfXTheme {

    static final Color BACKGROUND = color("0B0E11");
    static final Color SIDEBAR = color("11151A");
    static final Color SURFACE = color("181A20");
    static final Color SURFACE_RAISED = color("1E2329");
    static final Color SURFACE_HOVER = color("2B3139");
    static final Color BORDER = color("2B3139");
    static final Color BORDER_STRONG = color("474D57");

    static final Color PRIMARY = color("F0B90B");
    static final Color PRIMARY_HOVER = color("FCD535");
    static final Color PRIMARY_SOFT = color("3A3214");
    static final Color SUCCESS = color("0ECB81");
    static final Color SUCCESS_SOFT = color("12382D");
    static final Color ERROR = color("F6465D");
    static final Color ERROR_SOFT = color("3D2028");
    static final Color INFO = color("5B8DEF");

    static final Color TEXT = color("EAECEF");
    static final Color TEXT_SECONDARY = color("A7B0BE");
    static final Color TEXT_MUTED = color("848E9C");

    private OneOfXTheme() {
    }

    static void install() {
        FlatDarkLaf.setup();

        UIManager.put("defaultFont", font(Font.PLAIN, 14));
        UIManager.put("Panel.background", BACKGROUND);
        UIManager.put("Label.foreground", TEXT);
        UIManager.put("Component.background", SURFACE_RAISED);
        UIManager.put("Component.foreground", TEXT);
        UIManager.put("Component.borderColor", BORDER_STRONG);
        UIManager.put("Component.focusColor", PRIMARY);
        UIManager.put("Component.focusedBorderColor", PRIMARY);
        UIManager.put("Component.arc", 12);
        UIManager.put("Component.focusWidth", 1);
        UIManager.put("Component.innerFocusWidth", 0);
        UIManager.put("Component.minimumHeight", 38);

        UIManager.put("Button.background", SURFACE_HOVER);
        UIManager.put("Button.foreground", TEXT);
        UIManager.put("Button.hoverBackground", BORDER_STRONG);
        UIManager.put("Button.pressedBackground", SURFACE);
        UIManager.put("Button.arc", 12);
        UIManager.put("Button.minimumHeight", 40);
        UIManager.put("Button.margin", new java.awt.Insets(3, 16, 3, 16));

        UIManager.put("TextField.background", SURFACE_RAISED);
        UIManager.put("TextField.foreground", TEXT);
        UIManager.put("TextField.placeholderForeground", TEXT_MUTED);
        UIManager.put("PasswordField.background", SURFACE_RAISED);
        UIManager.put("ComboBox.background", SURFACE_RAISED);
        UIManager.put("Spinner.background", SURFACE_RAISED);
        UIManager.put("CheckBox.background", SURFACE_RAISED);
        UIManager.put("CheckBox.icon.focusedBorderColor", PRIMARY);
        UIManager.put("CheckBox.icon.selectedBackground", PRIMARY);
        UIManager.put("CheckBox.icon.checkmarkColor", BACKGROUND);

        UIManager.put("ScrollPane.background", BACKGROUND);
        UIManager.put("ScrollPane.border", BorderFactory.createEmptyBorder());
        UIManager.put("ScrollBar.track", BACKGROUND);
        UIManager.put("ScrollBar.thumb", BORDER_STRONG);
        UIManager.put("ScrollBar.hoverThumbColor", TEXT_MUTED);
        UIManager.put("ScrollBar.thumbArc", 999);
        UIManager.put("ScrollBar.trackArc", 999);
        UIManager.put("ScrollBar.width", 10);

        UIManager.put("OptionPane.background", SURFACE);
        UIManager.put("OptionPane.messageForeground", TEXT);
        UIManager.put("RootPane.background", BACKGROUND);
        UIManager.put("TitlePane.background", SIDEBAR);
        UIManager.put("TitlePane.foreground", TEXT);
    }

    static Font font(int style, float size) {
        return new Font("Segoe UI", style, Math.round(size));
    }

    static Border padding(int top, int left, int bottom, int right) {
        return BorderFactory.createEmptyBorder(top, left, bottom, right);
    }

    static void round(JComponent component) {
        component.putClientProperty("JComponent.roundRect", true);
    }

    private static Color color(String hex) {
        return Color.decode("#" + hex);
    }
}
