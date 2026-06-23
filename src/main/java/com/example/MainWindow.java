package com.example;

import com.formdev.flatlaf.FlatClientProperties;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.*;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainWindow extends JFrame {

    // ── Palette ───────────────────────────────────────────────────────────
    static final Color APP_BG       = hex("#eaf1f8");
    static final Color SURFACE      = hex("#ffffff");
    static final Color SURFACE_ALT  = hex("#f5f8fc");
    static final Color BORDER       = hex("#c8d7e6");
    static final Color TEXT         = hex("#10233f");
    static final Color MUTED        = hex("#5f7188");
    static final Color PRIMARY      = hex("#003a70");
    static final Color PRIMARY_HOV  = hex("#002c55");
    static final Color SIDEBAR_HOV  = hex("#e1ebf5");
    static final Color SUCCESS      = hex("#1f7a4d");
    static final Color WARNING_CLR  = hex("#b96a00");
    static final Color DANGER       = hex("#c43f3f");

    static final Map<String,Color> CAT_COLORS = new LinkedHashMap<>();
    static {
        CAT_COLORS.put("Standard",      hex("#eef6ff"));
        CAT_COLORS.put("Mining",        hex("#f3f7fb"));
        CAT_COLORS.put("Oil Processing",hex("#e8f0fa"));
        CAT_COLORS.put("IM",            hex("#eaf4ff"));
        CAT_COLORS.put("Uninstallers",  hex("#f6f1f5"));
    }

    static final String[] CATEGORIES = {"All","Standard","Mining","Oil Processing","IM","Uninstallers"};
    static final String[] CAT_ICONS  = {"grid-2x2","badge-check","pickaxe","droplets","monitor-cog","trash-2"};

    // ── State ─────────────────────────────────────────────────────────────
    private final AppLogic logic = new AppLogic();
    private final ExecutorService pool = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r); t.setDaemon(true); return t;
    });

    private String selectedCategory = "All";
    private int selectedIndex = -1;
    private List<AppModel> filteredApps = new ArrayList<>();

    // ── UI components ──────────────────────────────────────────────────────
    private JPanel cardsPanel;
    private JScrollPane cardsScroll;
    private JTextField searchField;
    private JLabel wifiLabel;
    private JProgressBar progressBar;
    private JLabel statusLabel;
    private final JButton[] catButtons = new JButton[CATEGORIES.length];
    private final Map<String,ImageIcon> icons = new HashMap<>();

    // ── Constructor ───────────────────────────────────────────────────────
    public MainWindow() {
        setTitle("Debswana Software Kit");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(1050, 700);
        setMinimumSize(new Dimension(900, 580));
        setLocationRelativeTo(null);
        getContentPane().setBackground(APP_BG);

        loadIcons();
        buildUI();
        bindShortcuts();

        SwingUtilities.invokeLater(this::showConnectionDialog);
    }

    static Color hex(String h) {
        return Color.decode(h);
    }

    // ── Icon loading ──────────────────────────────────────────────────────
    private void loadIcons() {
        String[] names = {
            "search","download","package-plus","circle-check-big","refresh-cw","plus",
            "wifi","wifi-off","triangle-alert","grid-2x2","grid-2x2-active",
            "badge-check","badge-check-active","pickaxe","pickaxe-active",
            "droplets","droplets-active","monitor-cog","monitor-cog-active",
            "trash-2","trash-2-active","wrench","info","computer","folder","server"
        };
        for (String name : names) {
            ImageIcon ic = loadIcon(name + ".png", 18, 18);
            if (ic != null) icons.put(name, ic);
        }
    }

    ImageIcon icon(String name) { return icons.get(name); }

    private ImageIcon loadIcon(String file, int w, int h) {
        try (InputStream is = getClass().getResourceAsStream("/assets/" + file)) {
            if (is == null) return null;
            BufferedImage img = ImageIO.read(is);
            return new ImageIcon(img.getScaledInstance(w, h, Image.SCALE_SMOOTH));
        } catch (Exception e) { return null; }
    }

    ImageIcon loadLogo(int w) {
        try (InputStream is = getClass().getResourceAsStream("/image.png")) {
            if (is == null) return null;
            BufferedImage img = ImageIO.read(is);
            int h = (int)(w * img.getHeight() / (double)img.getWidth());
            return new ImageIcon(img.getScaledInstance(w, h, Image.SCALE_SMOOTH));
        } catch (Exception e) { return null; }
    }

    // ── Build UI ──────────────────────────────────────────────────────────
    private void buildUI() {
        JPanel root = new JPanel(new BorderLayout(10, 10));
        root.setBackground(APP_BG);
        root.setBorder(new EmptyBorder(10, 10, 10, 10));
        setContentPane(root);

        root.add(buildSidebar(), BorderLayout.WEST);
        root.add(buildMain(), BorderLayout.CENTER);
    }

    // ── Sidebar ───────────────────────────────────────────────────────────
    private JPanel buildSidebar() {
        JPanel sidebar = new RoundPanel(18, SURFACE);
        sidebar.setPreferredSize(new Dimension(185, 0));
        sidebar.setLayout(new BoxLayout(sidebar, BoxLayout.Y_AXIS));
        sidebar.setBorder(new EmptyBorder(8, 8, 8, 8));

        for (int i = 0; i < CATEGORIES.length; i++) {
            final String cat = CATEGORIES[i];
            JButton btn = sidebarBtn(cat, icon(CAT_ICONS[i]));
            btn.addActionListener(e -> selectCategory(cat));
            catButtons[i] = btn;
            sidebar.add(btn);
            sidebar.add(Box.createVerticalStrut(2));
        }

        sidebar.add(Box.createVerticalStrut(6));
        sidebar.add(hSep());
        sidebar.add(Box.createVerticalStrut(6));

        JButton quickTools = sidebarBtn(" Quick Tools", icon("wrench"));
        quickTools.addActionListener(e -> openQuickTools());
        sidebar.add(quickTools);
        sidebar.add(Box.createVerticalStrut(2));

        JButton about = sidebarBtn(" About", icon("info"));
        about.addActionListener(e -> openAbout());
        sidebar.add(about);

        sidebar.add(Box.createVerticalStrut(8));
        sidebar.add(buildHintsPanel());
        sidebar.add(Box.createVerticalGlue());

        ImageIcon logo = loadLogo(140);
        if (logo != null) {
            JLabel logoLbl = new JLabel(logo);
            logoLbl.setAlignmentX(CENTER_ALIGNMENT);
            logoLbl.setBorder(new EmptyBorder(10, 0, 4, 0));
            sidebar.add(logoLbl);
        }

        selectCategory("All");
        return sidebar;
    }

    private JButton sidebarBtn(String text, ImageIcon ic) {
        JButton btn = new JButton(text, ic);
        btn.setHorizontalAlignment(SwingConstants.LEFT);
        btn.setIconTextGap(8);
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setOpaque(true);
        btn.setBackground(SURFACE);
        btn.setForeground(TEXT);
        btn.setFont(btn.getFont().deriveFont(13f));
        btn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_BORDERLESS);
        btn.putClientProperty(FlatClientProperties.STYLE, "arc: 10");
        btn.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e) {
                if (btn.getBackground() != PRIMARY) btn.setBackground(SIDEBAR_HOV);
            }
            public void mouseExited(MouseEvent e) {
                if (btn.getBackground() != PRIMARY) btn.setBackground(SURFACE);
            }
        });
        return btn;
    }

    private void selectCategory(String cat) {
        selectedCategory = cat;
        for (int i = 0; i < CATEGORIES.length; i++) {
            boolean active = CATEGORIES[i].equals(cat);
            catButtons[i].setBackground(active ? PRIMARY : SURFACE);
            catButtons[i].setForeground(active ? Color.WHITE : TEXT);
            String iconName = CAT_ICONS[i];
            catButtons[i].setIcon(active ? icon(iconName + "-active") : icon(iconName));
        }
        selectedIndex = -1;
        renderApps();
    }

    private JSeparator hSep() {
        JSeparator sep = new JSeparator();
        sep.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        sep.setForeground(BORDER);
        return sep;
    }

    private JPanel buildHintsPanel() {
        JPanel p = new RoundPanel(8, SURFACE_ALT);
        p.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(BORDER),
            new EmptyBorder(4, 8, 4, 8)));
        p.setLayout(new GridLayout(0, 2, 4, 1));
        String[][] hints = {{"Ctrl+F","Search"},{"Ctrl+A","Add App"},
            {"↑↓/Enter","Navigate"},{"Ctrl+I","About"},
            {"Ctrl+R","Rename PC"},{"Ctrl+L","Apps"}};
        for (String[] h : hints) {
            JLabel k = new JLabel(h[0]); k.setFont(k.getFont().deriveFont(Font.BOLD, 10f));
            k.setForeground(PRIMARY);
            JLabel v = new JLabel(h[1]); v.setFont(v.getFont().deriveFont(10f));
            v.setForeground(MUTED);
            p.add(k); p.add(v);
        }
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, p.getPreferredSize().height + 8));
        return p;
    }

    // ── Header ────────────────────────────────────────────────────────────
    private JPanel buildHeader() {
        JPanel hdr = new RoundPanel(16, PRIMARY);
        hdr.setLayout(new BorderLayout());
        hdr.setBorder(new EmptyBorder(14, 16, 14, 16));
        hdr.setPreferredSize(new Dimension(0, 60));

        JLabel title = new JLabel("Debswana Software Kit");
        title.setForeground(Color.WHITE);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 20f));
        hdr.add(title, BorderLayout.WEST);

        wifiLabel = new JLabel("Checking connection...");
        wifiLabel.setForeground(Color.WHITE);
        wifiLabel.setFont(wifiLabel.getFont().deriveFont(Font.BOLD, 12f));
        hdr.add(wifiLabel, BorderLayout.EAST);
        return hdr;
    }

    // ── Main panel ────────────────────────────────────────────────────────
    private JPanel buildMain() {
        JPanel main = new RoundPanel(18, SURFACE_ALT);
        main.setLayout(new BorderLayout(0, 8));
        main.setBorder(new EmptyBorder(8, 8, 8, 8));

        main.add(buildHeader(), BorderLayout.NORTH);
        main.add(buildActionBar(), BorderLayout.CENTER);

        JPanel center = new JPanel(new BorderLayout());
        center.setOpaque(false);

        cardsPanel = new JPanel(new GridLayout(0, 2, 10, 10));
        cardsPanel.setOpaque(false);
        cardsPanel.setBorder(new EmptyBorder(4, 4, 4, 4));

        cardsScroll = new JScrollPane(cardsPanel);
        cardsScroll.setOpaque(false);
        cardsScroll.getViewport().setOpaque(false);
        cardsScroll.setBorder(null);
        cardsScroll.getVerticalScrollBar().setUnitIncrement(16);
        center.add(cardsScroll, BorderLayout.CENTER);
        center.add(buildStatusBar(), BorderLayout.SOUTH);

        // Stack action bar + cards in a vertical layout
        JPanel wrapper = new JPanel(new BorderLayout(0, 6));
        wrapper.setOpaque(false);
        wrapper.add(buildActionBar(), BorderLayout.NORTH);
        wrapper.add(center, BorderLayout.CENTER);

        // Replace center placeholder with wrapper
        main.remove(main.getComponent(1)); // remove the action bar added above
        main.add(wrapper, BorderLayout.CENTER);

        return main;
    }

    private JPanel buildActionBar() {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        bar.setOpaque(false);

        JLabel searchIco = new JLabel(icon("search"));
        bar.add(searchIco);

        searchField = new JTextField(28);
        searchField.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "Search applications...");
        searchField.putClientProperty(FlatClientProperties.STYLE, "arc: 8");
        searchField.addKeyListener(new KeyAdapter() {
            public void keyReleased(KeyEvent e) { selectedIndex = -1; renderApps(); }
        });
        bar.add(searchField);

        JButton installAll = primaryBtn("Install All Standard", icon("package-plus"));
        installAll.addActionListener(e -> showInstallAllDialog());
        bar.add(installAll);

        JButton addApp = outlineBtn("Add App", icon("plus"));
        addApp.addActionListener(e -> showAddAppDialog());
        bar.add(addApp);

        return bar;
    }

    private JPanel buildStatusBar() {
        JPanel bar = new JPanel();
        bar.setLayout(new BoxLayout(bar, BoxLayout.Y_AXIS));
        bar.setOpaque(false);
        bar.setBorder(new EmptyBorder(4, 0, 0, 0));

        progressBar = new JProgressBar(0, 100);
        progressBar.setValue(0);
        progressBar.setStringPainted(false);
        progressBar.setForeground(PRIMARY);
        progressBar.putClientProperty(FlatClientProperties.STYLE, "arc: 6");
        progressBar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 6));
        bar.add(progressBar);
        bar.add(Box.createVerticalStrut(3));

        statusLabel = new JLabel("Ready.");
        statusLabel.setForeground(MUTED);
        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.BOLD, 11f));
        bar.add(statusLabel);
        return bar;
    }

    // ── Render cards ──────────────────────────────────────────────────────
    void renderApps() {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(this::renderApps); return;
        }
        String q = searchField == null ? "" : searchField.getText().toLowerCase();
        List<AppModel> all = logic.apps;
        List<AppModel> filtered = new ArrayList<>();
        for (AppModel a : all) {
            if (!selectedCategory.equals("All") && !selectedCategory.equals(a.category)) continue;
            if (!q.isEmpty() && !a.name.toLowerCase().contains(q) && !a.category.toLowerCase().contains(q)) continue;
            filtered.add(a);
        }
        filteredApps = filtered;

        cardsPanel.removeAll();
        for (int i = 0; i < filtered.size(); i++) {
            cardsPanel.add(buildCard(filtered.get(i), i));
        }
        cardsPanel.revalidate();
        cardsPanel.repaint();
    }

    private JPanel buildCard(AppModel app, int index) {
        boolean selected = index == selectedIndex;
        Color bg  = selected ? PRIMARY : CAT_COLORS.getOrDefault(app.category, SURFACE);
        Color fg  = selected ? Color.WHITE : TEXT;
        Color sub = selected ? hex("#c8d7e6") : MUTED;

        RoundPanel card = new RoundPanel(10, bg);
        card.setLayout(new BorderLayout(8, 0));
        card.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(selected ? PRIMARY : BORDER, selected ? 2 : 1),
            new EmptyBorder(8, 12, 8, 10)));

        JPanel info = new JPanel();
        info.setLayout(new BoxLayout(info, BoxLayout.Y_AXIS));
        info.setOpaque(false);

        JLabel name = new JLabel(app.name);
        name.setFont(name.getFont().deriveFont(Font.BOLD, 13f));
        name.setForeground(fg);
        info.add(name);

        JLabel cat = new JLabel(app.category);
        cat.setFont(cat.getFont().deriveFont(11f));
        cat.setForeground(sub);
        info.add(cat);

        card.add(info, BorderLayout.CENTER);

        JButton installBtn = primaryBtn("Install", icon("download"));
        installBtn.addActionListener(e -> pool.submit(() -> runInstall(app)));
        card.add(installBtn, BorderLayout.EAST);

        // click to select
        MouseAdapter click = new MouseAdapter() {
            public void mousePressed(MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e)) { showCardMenu(e, app); return; }
                selectedIndex = index; renderApps();
            }
        };
        card.addMouseListener(click);
        name.addMouseListener(click);
        cat.addMouseListener(click);
        card.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return card;
    }

    private void showCardMenu(MouseEvent e, AppModel app) {
        JPopupMenu menu = new JPopupMenu();
        JMenuItem open = new JMenuItem("Open Path");
        open.addActionListener(x -> {
            String folder = app.path.contains("\\")
                ? app.path.substring(0, app.path.lastIndexOf('\\')) : app.path;
            try { new ProcessBuilder("explorer", folder).start(); }
            catch (Exception ex) { JOptionPane.showMessageDialog(this, "Path not accessible:\n" + folder); }
        });
        JMenuItem edit = new JMenuItem("Edit");
        edit.addActionListener(x -> showEditDialog(app));
        menu.add(open); menu.addSeparator(); menu.add(edit);
        menu.show(e.getComponent(), e.getX(), e.getY());
    }

    // ── Install ───────────────────────────────────────────────────────────
    private void runInstall(AppModel app) {
        setProgress(20);
        boolean ok = logic.installApp(app, this::updateStatus);
        setProgress(100);
        if (ok) {
            SwingUtilities.invokeLater(() ->
                JOptionPane.showMessageDialog(this, app.name + " installed successfully.",
                    "Done", JOptionPane.INFORMATION_MESSAGE, icon("circle-check-big")));
        } else {
            SwingUtilities.invokeLater(() ->
                JOptionPane.showMessageDialog(this,
                    "Could not install " + app.name + ".\nCheck your network or contact IT support.",
                    "Installation Failed", JOptionPane.ERROR_MESSAGE));
        }
    }

    private void setProgress(int pct) {
        SwingUtilities.invokeLater(() -> progressBar.setValue(pct));
    }

    void updateStatus(String msg, String color) {
        SwingUtilities.invokeLater(() -> {
            statusLabel.setText(msg);
            statusLabel.setForeground(switch (color) {
                case "orange" -> WARNING_CLR;
                case "red"    -> DANGER;
                case "green"  -> SUCCESS;
                default       -> MUTED;
            });
        });
    }

    // ── Install All dialog ────────────────────────────────────────────────
    private void showInstallAllDialog() {
        List<AppModel> standard = logic.apps.stream()
            .filter(a -> "Standard".equals(a.category)).toList();
        if (standard.isEmpty()) {
            JOptionPane.showMessageDialog(this, "No standard applications found.");
            return;
        }

        JDialog dlg = new JDialog(this, "Select Applications to Install", true);
        dlg.setSize(660, 520);
        dlg.setLocationRelativeTo(this);
        JPanel content = new JPanel(new BorderLayout(0, 8));
        content.setBorder(new EmptyBorder(14, 14, 14, 14));
        content.setBackground(SURFACE);
        dlg.setContentPane(content);

        JLabel hdr = new JLabel("Select applications to install:");
        hdr.setFont(hdr.getFont().deriveFont(Font.BOLD, 15f));
        hdr.setForeground(PRIMARY);
        content.add(hdr, BorderLayout.NORTH);

        JPanel listPanel = new JPanel();
        listPanel.setLayout(new BoxLayout(listPanel, BoxLayout.Y_AXIS));
        listPanel.setBackground(SURFACE);
        List<JCheckBox> boxes = new ArrayList<>();
        for (AppModel a : standard) {
            JCheckBox cb = new JCheckBox(a.name + " – " + a.category, true);
            cb.setBackground(SURFACE);
            cb.setForeground(TEXT);
            listPanel.add(cb);
            boxes.add(cb);
        }
        JScrollPane sp = new JScrollPane(listPanel);
        sp.setBorder(BorderFactory.createLineBorder(BORDER));
        content.add(sp, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        bottom.setBackground(SURFACE);
        JButton selAll = outlineBtn("Select All", null);
        selAll.addActionListener(e -> boxes.forEach(b -> b.setSelected(true)));
        JButton deselAll = outlineBtn("Deselect All", null);
        deselAll.addActionListener(e -> boxes.forEach(b -> b.setSelected(false)));
        JButton install = primaryBtn("Install Selected", icon("package-plus"));
        install.addActionListener(e -> {
            List<AppModel> chosen = new ArrayList<>();
            for (int i = 0; i < boxes.size(); i++) if (boxes.get(i).isSelected()) chosen.add(standard.get(i));
            if (chosen.isEmpty()) { JOptionPane.showMessageDialog(dlg, "Select at least one app."); return; }
            dlg.dispose();
            pool.submit(() -> runBulkInstall(chosen));
        });
        bottom.add(selAll); bottom.add(deselAll); bottom.add(install);
        content.add(bottom, BorderLayout.SOUTH);
        dlg.setVisible(true);
    }

    private void runBulkInstall(List<AppModel> apps) {
        int total = apps.size();
        List<String> failed = new ArrayList<>();
        for (int i = 0; i < total; i++) {
            AppModel a = apps.get(i);
            updateStatus("Installing (" + (i+1) + "/" + total + "): " + a.name + "...", "orange");
            setProgress((i + 1) * 100 / total);
            if (!logic.installApp(a, this::updateStatus)) failed.add(a.name);
        }
        SwingUtilities.invokeLater(() -> {
            if (failed.isEmpty()) {
                updateStatus("All " + total + " apps installed.", "green");
            } else {
                JOptionPane.showMessageDialog(this,
                    failed.size() + " app(s) failed:\n" + String.join("\n", failed),
                    "Some Installations Failed", JOptionPane.WARNING_MESSAGE);
            }
        });
    }

    // ── Add App dialog ─────────────────────────────────────────────────────
    private void showAddAppDialog() {
        showAppFormDialog(null);
    }

    private void showEditDialog(AppModel app) {
        showAppFormDialog(app);
    }

    private void showAppFormDialog(AppModel existing) {
        boolean editing = existing != null;
        JDialog dlg = new JDialog(this, editing ? "Edit – " + existing.name : "Add Application", true);
        dlg.setSize(480, 310);
        dlg.setLocationRelativeTo(this);
        JPanel content = new JPanel(new GridBagLayout());
        content.setBackground(SURFACE);
        content.setBorder(new EmptyBorder(14, 14, 14, 14));
        dlg.setContentPane(content);

        GridBagConstraints lc = new GridBagConstraints();
        lc.anchor = GridBagConstraints.EAST; lc.insets = new Insets(5,5,5,5);
        GridBagConstraints fc = new GridBagConstraints();
        fc.fill = GridBagConstraints.HORIZONTAL; fc.weightx = 1;
        fc.insets = new Insets(5,0,5,5); fc.gridwidth = GridBagConstraints.REMAINDER;

        JTextField nameF = new JTextField(editing ? existing.name : "");
        JTextField pathF = new JTextField(editing ? existing.path : "");
        JTextField argsF = new JTextField(editing ? existing.args : "");
        String[] cats = {"Standard","Mining","Oil Processing","IM","Uninstallers"};
        JComboBox<String> catBox = new JComboBox<>(cats);
        if (editing) catBox.setSelectedItem(existing.category);

        String[][] rows = {{"Name:",null},{"Path:",null},{"Args:",null},{"Category:",null}};
        JComponent[] fields = {nameF, pathF, argsF, catBox};

        for (int i = 0; i < rows.length; i++) {
            lc.gridy = fc.gridy = i;
            content.add(new JLabel(rows[i][0]), lc);
            if (i == 1) { // path gets Browse button
                JPanel prow = new JPanel(new BorderLayout(4,0));
                prow.setOpaque(false);
                prow.add(pathF, BorderLayout.CENTER);
                JButton browse = outlineBtn("Browse", null);
                browse.addActionListener(e -> {
                    JFileChooser fc2 = new JFileChooser();
                    if (fc2.showOpenDialog(dlg) == JFileChooser.APPROVE_OPTION)
                        pathF.setText(fc2.getSelectedFile().getAbsolutePath());
                });
                prow.add(browse, BorderLayout.EAST);
                content.add(prow, fc);
            } else {
                content.add(fields[i], fc);
            }
        }

        GridBagConstraints bc = new GridBagConstraints();
        bc.gridy = 4; bc.gridwidth = GridBagConstraints.REMAINDER;
        bc.insets = new Insets(10,0,0,0);
        JButton save = primaryBtn("Save", null);
        save.addActionListener(e -> {
            AppModel a = editing ? existing : new AppModel();
            a.name = nameF.getText().trim();
            a.path = pathF.getText().trim();
            a.args = argsF.getText().trim();
            a.category = (String) catBox.getSelectedItem();
            if (!editing) logic.addApp(a); else logic.saveApps();
            renderApps();
            dlg.dispose();
        });
        dlg.getRootPane().setDefaultButton(save);
        content.add(save, bc);
        dlg.setVisible(true);
    }

    // ── About dialog ──────────────────────────────────────────────────────
    private void openAbout() {
        JDialog dlg = new JDialog(this, "About", true);
        dlg.setSize(380, 420);
        dlg.setLocationRelativeTo(this);
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBackground(SURFACE);
        p.setBorder(new EmptyBorder(18,20,18,20));
        dlg.setContentPane(p);

        ImageIcon logo = loadLogo(90);
        if (logo != null) { JLabel l = new JLabel(logo); l.setAlignmentX(CENTER_ALIGNMENT); p.add(l); }

        JLabel title = centeredLabel("Debswana Software Kit", Font.BOLD, 16, PRIMARY);
        JLabel authors = centeredLabel("Made by Desiree Chingwaru & Odirile Mathepeo", Font.PLAIN, 11, MUTED);
        JLabel dept = centeredLabel("Debswana IT Department", Font.PLAIN, 11, MUTED);
        p.add(Box.createVerticalStrut(6)); p.add(title);
        p.add(Box.createVerticalStrut(2)); p.add(authors);
        p.add(dept);
        p.add(Box.createVerticalStrut(10));
        p.add(hSep());
        p.add(Box.createVerticalStrut(8));

        String[][] shortcuts = {{"Ctrl+F","Search"},{"Ctrl+A","Add App"},{"Ctrl+I","About"},
            {"Ctrl+R","Rename PC"},{"Ctrl+L","Installed Apps"},{"Ctrl+W","Close"},{"↑↓/Enter","Navigate"}};
        JPanel grid = new JPanel(new GridLayout(0, 2, 8, 2));
        grid.setBackground(SURFACE);
        for (String[] s : shortcuts) {
            JLabel k = new JLabel(s[0]); k.setFont(k.getFont().deriveFont(Font.BOLD,11f)); k.setForeground(PRIMARY);
            JLabel v = new JLabel(s[1]); v.setForeground(TEXT);
            grid.add(k); grid.add(v);
        }
        p.add(grid);
        p.add(Box.createVerticalStrut(14));

        JButton close = primaryBtn("Close", null);
        close.setAlignmentX(CENTER_ALIGNMENT);
        close.addActionListener(e -> dlg.dispose());
        p.add(close);
        dlg.setVisible(true);
    }

    // ── Quick Tools ───────────────────────────────────────────────────────
    private void openQuickTools() {
        JDialog dlg = new JDialog(this, "Quick Tools", true);
        dlg.setSize(280, 200);
        dlg.setLocationRelativeTo(this);
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBackground(SURFACE);
        p.setBorder(new EmptyBorder(14,14,14,14));
        dlg.setContentPane(p);

        JLabel lbl = new JLabel("Quick Tools");
        lbl.setFont(lbl.getFont().deriveFont(Font.BOLD, 14f));
        lbl.setForeground(PRIMARY);
        lbl.setAlignmentX(LEFT_ALIGNMENT);
        p.add(lbl); p.add(Box.createVerticalStrut(10));

        boolean proxyOn = logic.isProxyEnabled();
        JButton proxyBtn = proxyOn
            ? new JButton("⚙  Proxy: ON")
            : outlineBtn("⚙  Proxy: OFF", null);
        if (proxyOn) { proxyBtn.setBackground(SUCCESS); proxyBtn.setForeground(Color.WHITE); }
        proxyBtn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        proxyBtn.setAlignmentX(LEFT_ALIGNMENT);
        proxyBtn.addActionListener(e -> pool.submit(() -> {
            try { logic.setProxy(!logic.isProxyEnabled()); }
            catch (Exception ex) { SwingUtilities.invokeLater(() ->
                JOptionPane.showMessageDialog(this, "Proxy error: " + ex.getMessage())); }
            SwingUtilities.invokeLater(dlg::dispose);
        }));
        p.add(proxyBtn); p.add(Box.createVerticalStrut(4));

        JButton rename = outlineBtn(" Rename this PC  (Ctrl+R)", icon("computer"));
        rename.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        rename.setAlignmentX(LEFT_ALIGNMENT);
        rename.addActionListener(e -> { try { new ProcessBuilder("ms-settings:about").start(); } catch(Exception ignored){} });
        p.add(rename); p.add(Box.createVerticalStrut(4));

        JButton apps = outlineBtn(" Installed Apps  (Ctrl+L)", icon("wrench"));
        apps.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        apps.setAlignmentX(LEFT_ALIGNMENT);
        apps.addActionListener(e -> { try { new ProcessBuilder("appwiz.cpl").start(); } catch(Exception ignored){} });
        p.add(apps);
        dlg.setVisible(true);
    }

    // ── Connection dialog ─────────────────────────────────────────────────
    private void showConnectionDialog() {
        JDialog dlg = new JDialog(this, "Checking Connection", true);
        dlg.setSize(380, 260);
        dlg.setResizable(false);
        dlg.setLocationRelativeTo(this);
        dlg.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);

        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBackground(SURFACE);
        p.setBorder(new EmptyBorder(18, 20, 18, 20));
        dlg.setContentPane(p);

        JLabel statusLbl = centeredLabel("Checking network...", Font.BOLD, 13, WARNING_CLR);
        JLabel detailLbl = centeredLabel("", Font.PLAIN, 11, MUTED);
        detailLbl.setMaximumSize(new Dimension(340, 60));

        ImageIcon wifiIcon = loadIcon("wifi-animation.gif", 80, 80);
        JLabel gifLbl = new JLabel(wifiIcon != null ? wifiIcon : icon("wifi"));
        gifLbl.setAlignmentX(CENTER_ALIGNMENT);
        p.add(gifLbl);
        p.add(Box.createVerticalStrut(10));
        p.add(statusLbl);
        p.add(Box.createVerticalStrut(4));
        p.add(detailLbl);
        p.add(Box.createVerticalStrut(12));

        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
        btnRow.setBackground(SURFACE);
        btnRow.setAlignmentX(CENTER_ALIGNMENT);
        JButton retryBtn = primaryBtn("Retry", null);
        retryBtn.setEnabled(false);
        JButton explorerBtn = outlineBtn("Open \\\\10.50.93.5 in Explorer", icon("server"));
        explorerBtn.setEnabled(false);
        explorerBtn.addActionListener(e -> logic.openServerInExplorer());
        btnRow.add(retryBtn); btnRow.add(explorerBtn);
        p.add(btnRow);

        Runnable doCheck = () -> pool.submit(() -> {
            Map<String,Object> status = logic.checkConnection();
            SwingUtilities.invokeLater(() -> {
                boolean isDebs  = Boolean.TRUE.equals(status.get("is_debs"));
                boolean srvOk   = Boolean.TRUE.equals(status.get("server_ok"));
                boolean conn    = Boolean.TRUE.equals(status.get("connected"));
                String ssid     = String.valueOf(status.get("ssid"));

                if (isDebs && srvOk) {
                    dlg.dispose();
                    updateWifiLabel(status);
                    pool.submit(() -> { logic.loadApps(); SwingUtilities.invokeLater(this::renderApps); });
                } else {
                    String msg, detail;
                    if (!conn) { msg = "No WiFi connection detected."; detail = "Please connect to the DEBS corporate WiFi."; }
                    else if (!isDebs) { msg = "Connected to '" + ssid + "' — not DEBS WiFi."; detail = "Please switch to the Debswana corporate WiFi (debs.debswana.bw)."; }
                    else { msg = "DEBS WiFi connected but server unreachable."; detail = "Cannot reach \\\\10.50.93.5. Try opening it in Explorer first."; }
                    statusLbl.setText(msg); statusLbl.setForeground(DANGER);
                    detailLbl.setText("<html><center>" + detail + "</center></html>");
                    retryBtn.setEnabled(true); explorerBtn.setEnabled(true);
                }
            });
        });

        retryBtn.addActionListener(e -> {
            statusLbl.setText("Checking network..."); statusLbl.setForeground(WARNING_CLR);
            detailLbl.setText("");
            retryBtn.setEnabled(false); explorerBtn.setEnabled(false);
            doCheck.run();
        });

        doCheck.run();
        dlg.setVisible(true);
    }

    private void updateWifiLabel(Map<String,Object> status) {
        boolean isDebs = Boolean.TRUE.equals(status.get("is_debs"));
        boolean srvOk  = Boolean.TRUE.equals(status.get("server_ok"));
        boolean conn   = Boolean.TRUE.equals(status.get("connected"));
        String ssid    = String.valueOf(status.get("ssid"));

        if (isDebs && srvOk) {
            wifiLabel.setIcon(icon("wifi"));
            wifiLabel.setText("● DEBS Connected (" + ssid + ")");
            wifiLabel.setForeground(hex("#90EE90"));
        } else if (isDebs) {
            wifiLabel.setIcon(icon("triangle-alert"));
            wifiLabel.setText("● DEBS WiFi — server unreachable");
            wifiLabel.setForeground(hex("#FFB6C1"));
        } else if (conn) {
            wifiLabel.setIcon(icon("triangle-alert"));
            wifiLabel.setText("● " + ssid + " (Not DEBS)");
            wifiLabel.setForeground(hex("#FFB6C1"));
        } else {
            wifiLabel.setIcon(icon("wifi-off"));
            wifiLabel.setText("● Not Connected");
            wifiLabel.setForeground(hex("#FF6347"));
        }
    }

    // ── Keyboard shortcuts ────────────────────────────────────────────────
    private void bindShortcuts() {
        KeyStroke[] ks = {
            KeyStroke.getKeyStroke(KeyEvent.VK_F, InputEvent.CTRL_DOWN_MASK),
            KeyStroke.getKeyStroke(KeyEvent.VK_A, InputEvent.CTRL_DOWN_MASK),
            KeyStroke.getKeyStroke(KeyEvent.VK_I, InputEvent.CTRL_DOWN_MASK),
            KeyStroke.getKeyStroke(KeyEvent.VK_W, InputEvent.CTRL_DOWN_MASK),
            KeyStroke.getKeyStroke(KeyEvent.VK_R, InputEvent.CTRL_DOWN_MASK),
            KeyStroke.getKeyStroke(KeyEvent.VK_L, InputEvent.CTRL_DOWN_MASK),
            KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0),
            KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0),
            KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0),
        };
        ActionListener[] acts = {
            e -> searchField.requestFocusInWindow(),
            e -> showAddAppDialog(),
            e -> openAbout(),
            e -> dispose(),
            e -> { try { new ProcessBuilder("ms-settings:about").start(); } catch(Exception ignored){} },
            e -> { try { new ProcessBuilder("appwiz.cpl").start(); } catch(Exception ignored){} },
            e -> { if (!filteredApps.isEmpty()) { selectedIndex = Math.max(0, selectedIndex - 1); renderApps(); }},
            e -> { if (!filteredApps.isEmpty()) { selectedIndex = Math.min(filteredApps.size()-1, selectedIndex + 1); renderApps(); }},
            e -> { if (!(getFocusOwner() instanceof JTextField) && selectedIndex >= 0 && selectedIndex < filteredApps.size())
                       pool.submit(() -> runInstall(filteredApps.get(selectedIndex))); },
        };
        JPanel root = (JPanel) getContentPane();
        for (int i = 0; i < ks.length; i++) {
            String key = "action" + i;
            root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(ks[i], key);
            root.getActionMap().put(key, new AbstractAction() {
                final ActionListener act;
                { act = acts[acts.length > 0 ? 0 : 0]; } // placeholder, overridden below
                public void actionPerformed(ActionEvent e) { act.actionPerformed(e); }
            });
        }
        // Re-bind properly
        for (int i = 0; i < ks.length; i++) {
            final ActionListener al = acts[i];
            String key = "action" + i;
            root.getActionMap().put(key, new AbstractAction() {
                public void actionPerformed(ActionEvent e) { al.actionPerformed(e); }
            });
        }
    }

    // ── Button helpers ────────────────────────────────────────────────────
    JButton primaryBtn(String text, ImageIcon ic) {
        JButton b = new JButton(text, ic);
        b.setBackground(PRIMARY); b.setForeground(Color.WHITE);
        b.setFocusPainted(false);
        b.putClientProperty(FlatClientProperties.STYLE, "arc: 8");
        if (ic != null) b.setIconTextGap(6);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e) { b.setBackground(PRIMARY_HOV); }
            public void mouseExited(MouseEvent e)  { b.setBackground(PRIMARY); }
        });
        return b;
    }

    JButton outlineBtn(String text, ImageIcon ic) {
        JButton b = new JButton(text, ic);
        b.setBackground(SURFACE); b.setForeground(TEXT);
        b.setFocusPainted(false);
        b.putClientProperty(FlatClientProperties.STYLE, "arc: 8");
        b.putClientProperty(FlatClientProperties.BUTTON_TYPE, "roundRect");
        if (ic != null) b.setIconTextGap(6);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e) { b.setBackground(SIDEBAR_HOV); }
            public void mouseExited(MouseEvent e)  { b.setBackground(SURFACE); }
        });
        return b;
    }

    private JLabel centeredLabel(String text, int style, float size, Color color) {
        JLabel l = new JLabel(text, SwingConstants.CENTER);
        l.setFont(l.getFont().deriveFont(style, size));
        l.setForeground(color);
        l.setAlignmentX(CENTER_ALIGNMENT);
        return l;
    }

    // ── RoundPanel ────────────────────────────────────────────────────────
    static class RoundPanel extends JPanel {
        private final int arc;
        RoundPanel(int arc, Color bg) {
            this.arc = arc;
            setBackground(bg);
            setOpaque(false);
        }
        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(getBackground());
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), arc, arc);
            g2.dispose();
            super.paintComponent(g);
        }
    }
}
