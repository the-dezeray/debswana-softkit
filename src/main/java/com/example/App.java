package com.example;

import com.formdev.flatlaf.FlatLightLaf;
import javax.swing.*;

public class App {
    public static void main(String[] args) {
        FlatLightLaf.setup();
        // Global FlatLaf tweaks for rounded, clean look
        UIManager.put("Button.arc", 8);
        UIManager.put("Component.arc", 8);
        UIManager.put("TextComponent.arc", 8);
        UIManager.put("ScrollBar.showButtons", false);
        UIManager.put("ScrollBar.width", 8);
        UIManager.put("TabbedPane.selectedBackground", MainWindow.PRIMARY);

        SwingUtilities.invokeLater(() -> new MainWindow().setVisible(true));
    }
}
