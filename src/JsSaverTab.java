import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * JS Saver configuration + activity-log tab.
 * Extends JPanel so it can be passed directly to
 *   api.userInterface().registerSuiteTab("JS Saver", tab).
 */
public class JsSaverTab extends JPanel {

    // ── palette ───────────────────────────────────────────────────────────────
    private static final Color BG_DARK  = new Color(30,  32,  36);
    private static final Color BG_CARD  = new Color(40,  42,  48);
    private static final Color BG_INPUT = new Color(50,  52,  60);
    private static final Color BG_LOG   = new Color(20,  22,  26);
    private static final Color FG_GREEN = new Color(80, 210, 120);
    private static final Color FG_MUTED = new Color(160, 162, 170);
    private static final Color ACCENT   = new Color(88, 140, 255);
    private static final Color BTN_GREY = new Color(65,  68,  78);

    private static final DateTimeFormatter T = DateTimeFormatter.ofPattern("HH:mm:ss");

    // ── component refs used by public API ─────────────────────────────────────
    private final JTextField    dirField;
    private final JToggleButton enableBtn;
    private final JTextArea     logArea;
    private final JLabel        statsLabel;
    private final AtomicInteger savedCount = new AtomicInteger(0);

    // VS Code integration
    private final JToggleButton vsCodeBtn;
    private final JTextField    vsCodePortField;

    // ── constructor ───────────────────────────────────────────────────────────
    public JsSaverTab() {
        setLayout(new BorderLayout(0, 10));
        setBackground(BG_DARK);
        setBorder(new EmptyBorder(16, 18, 16, 18));

        // ── header ────────────────────────────────────────────────────────────
        JLabel title = new JLabel("⚡ JS Saver");
        title.setFont(new Font("Segoe UI", Font.BOLD, 22));
        title.setForeground(FG_GREEN);

        JLabel sub = new JLabel("  Intercepts Proxy traffic and saves beautified JavaScript files to disk");
        sub.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        sub.setForeground(FG_MUTED);

        JPanel header = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        header.setBackground(BG_DARK);
        header.add(title);
        header.add(sub);

        // ── config card ───────────────────────────────────────────────────────
        JPanel card = new JPanel(new GridBagLayout());
        card.setBackground(BG_CARD);
        card.setBorder(BorderFactory.createCompoundBorder(
            new LineBorder(new Color(60, 65, 75), 1, true),
            new EmptyBorder(12, 14, 12, 14)));

        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(5, 6, 5, 6);
        g.fill   = GridBagConstraints.HORIZONTAL;

        // Row 0 – directory picker
        JLabel dirLbl = makeLabel("Save Directory:", true);

        dirField = new JTextField(
            System.getProperty("user.home") + File.separator + "js_saved", 42);
        dirField.setBackground(BG_INPUT);
        dirField.setForeground(Color.WHITE);
        dirField.setCaretColor(Color.WHITE);
        dirField.setFont(new Font("Consolas", Font.PLAIN, 12));
        dirField.setBorder(BorderFactory.createCompoundBorder(
            new LineBorder(new Color(70, 75, 88), 1),
            new EmptyBorder(5, 8, 5, 8)));

        JButton browseBtn = makeBtn("Browse", ACCENT, 90);

        g.gridx = 0; g.gridy = 0; g.weightx = 0;   card.add(dirLbl,    g);
        g.gridx = 1;              g.weightx = 1.0;  card.add(dirField,  g);
        g.gridx = 2;              g.weightx = 0;    card.add(browseBtn, g);

        // Row 1 – controls bar
        enableBtn = new JToggleButton("▶  Enable");
        enableBtn.setBackground(BTN_GREY);
        enableBtn.setForeground(Color.WHITE);
        enableBtn.setFont(new Font("Segoe UI", Font.BOLD, 12));
        enableBtn.setFocusPainted(false);
        enableBtn.setBorderPainted(false);
        enableBtn.setPreferredSize(new Dimension(120, 30));
        enableBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        statsLabel = new JLabel("Files saved: 0");
        statsLabel.setForeground(FG_MUTED);
        statsLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));

        JButton clearBtn = makeBtn("Clear Log",   BTN_GREY, 100);
        JButton openBtn  = makeBtn("Open Folder", BTN_GREY, 110);

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        controls.setBackground(BG_CARD);
        controls.add(enableBtn);
        controls.add(Box.createHorizontalStrut(16));
        controls.add(statsLabel);
        controls.add(Box.createHorizontalStrut(16));
        controls.add(clearBtn);
        controls.add(openBtn);

        g.gridx = 0; g.gridy = 1; g.gridwidth = 3; g.weightx = 1.0;
        card.add(controls, g);

        // Row 2 – VS Code integration
        JLabel vsCodeLbl = makeLabel("VS Code:", true);

        vsCodeBtn = new JToggleButton("⚡  Send to VS Code");
        vsCodeBtn.setBackground(BTN_GREY);
        vsCodeBtn.setForeground(Color.WHITE);
        vsCodeBtn.setFont(new Font("Segoe UI", Font.BOLD, 12));
        vsCodeBtn.setFocusPainted(false);
        vsCodeBtn.setBorderPainted(false);
        vsCodeBtn.setPreferredSize(new Dimension(170, 30));
        vsCodeBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        vsCodeBtn.setToolTipText("Stream captured JS to the VS Code JS Grepper extension");

        JLabel portLbl = makeLabel("Port:", false);
        portLbl.setBorder(new EmptyBorder(0, 16, 0, 4));

        vsCodePortField = new JTextField("7777", 6);
        vsCodePortField.setBackground(BG_INPUT);
        vsCodePortField.setForeground(Color.WHITE);
        vsCodePortField.setCaretColor(Color.WHITE);
        vsCodePortField.setFont(new Font("Consolas", Font.PLAIN, 12));
        vsCodePortField.setBorder(BorderFactory.createCompoundBorder(
            new LineBorder(new Color(70, 75, 88), 1),
            new EmptyBorder(4, 6, 4, 6)));
        vsCodePortField.setToolTipText("Must match jsGrepper.port setting in VS Code");

        JLabel vsHintLbl = makeLabel("  ← run  bash vscode-extension/install.sh  then restart VS Code", false);
        vsHintLbl.setForeground(FG_MUTED);

        JPanel vsControls = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        vsControls.setBackground(BG_CARD);
        vsControls.add(vsCodeBtn);
        vsControls.add(portLbl);
        vsControls.add(vsCodePortField);
        vsControls.add(vsHintLbl);

        g.gridx = 0; g.gridy = 2; g.gridwidth = 3; g.weightx = 1.0;
        card.add(vsCodeLbl,   new java.awt.GridBagConstraints() {{ gridx=0; gridy=2; gridwidth=1; weightx=0; fill=GridBagConstraints.HORIZONTAL; insets=new Insets(5,6,5,6); }});
        card.add(vsControls,  new java.awt.GridBagConstraints() {{ gridx=1; gridy=2; gridwidth=2; weightx=1.0; fill=GridBagConstraints.HORIZONTAL; insets=new Insets(5,6,5,6); }});

        // VS Code toggle colours
        vsCodeBtn.addItemListener(e -> {
            boolean on = vsCodeBtn.isSelected();
            vsCodeBtn.setText(on ? "⚡  VS Code ON" : "⚡  Send to VS Code");
            vsCodeBtn.setBackground(on ? new Color(90, 60, 180) : BTN_GREY);
            log(on ? "[VSCODE] Streaming enabled → port " + getVsCodePort()
                   : "[VSCODE] Streaming disabled.");
        });

        // ── log area ──────────────────────────────────────────────────────────
        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setBackground(BG_LOG);
        logArea.setForeground(FG_GREEN);
        logArea.setFont(new Font("Consolas", Font.PLAIN, 12));
        logArea.setBorder(new EmptyBorder(8, 10, 8, 10));
        logArea.setLineWrap(false);

        JScrollPane scroll = new JScrollPane(logArea);
        scroll.setBorder(new LineBorder(new Color(55, 58, 68), 1, true));
        scroll.getViewport().setBackground(BG_LOG);

        JLabel logLbl = makeLabel("Activity Log", true);
        logLbl.setBorder(new EmptyBorder(10, 0, 4, 0));

        // ── wire up actions ───────────────────────────────────────────────────
        browseBtn.addActionListener(e -> {
            JFileChooser fc = new JFileChooser(dirField.getText());
            fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                dirField.setText(fc.getSelectedFile().getAbsolutePath());
                log("[CONFIG] Save directory → " + dirField.getText());
            }
        });

        enableBtn.addItemListener(e -> {
            boolean on = enableBtn.isSelected();
            enableBtn.setText(on ? "⏹  Disable" : "▶  Enable");
            enableBtn.setBackground(on ? new Color(50, 160, 80) : BTN_GREY);
            log(on ? "[INFO] Capturing enabled." : "[INFO] Capturing disabled.");
        });

        clearBtn.addActionListener(e -> logArea.setText(""));

        openBtn.addActionListener(e -> {
            try { Desktop.getDesktop().open(new File(dirField.getText())); }
            catch (IOException ex) { log("[ERROR] Cannot open folder: " + ex.getMessage()); }
        });

        // ── assemble layout ───────────────────────────────────────────────────
        JPanel topPanel = new JPanel(new BorderLayout(0, 8));
        topPanel.setBackground(BG_DARK);
        topPanel.add(header, BorderLayout.NORTH);
        topPanel.add(card,   BorderLayout.CENTER);
        topPanel.add(logLbl, BorderLayout.SOUTH);

        add(topPanel, BorderLayout.NORTH);
        add(scroll,   BorderLayout.CENTER);
    }

    // ── public API ────────────────────────────────────────────────────────────

    /** Whether the handler should capture JS responses. */
    public boolean isEnabled()       { return enableBtn.isSelected(); }

    /** Whether captured JS should be streamed to VS Code. */
    public boolean isVsCodeEnabled() { return vsCodeBtn.isSelected(); }

    /** Port the VS Code extension is listening on. */
    public int getVsCodePort() {
        try { return Integer.parseInt(vsCodePortField.getText().trim()); }
        catch (NumberFormatException e) { return 7777; }
    }

    /** Directory where JS files should be saved. */
    public String getSaveDir()  { return dirField.getText().trim(); }

    /** Append a timestamped message to the log area (thread-safe). */
    public void log(String msg) {
        String line = "[" + LocalTime.now().format(T) + "] " + msg + "\n";
        SwingUtilities.invokeLater(() -> {
            logArea.append(line);
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    /** Increment the saved-files counter (thread-safe). */
    public void incrementSaved() {
        int n = savedCount.incrementAndGet();
        SwingUtilities.invokeLater(() -> statsLabel.setText("Files saved: " + n));
    }

    // ── private factory helpers ───────────────────────────────────────────────

    private static JLabel makeLabel(String text, boolean bold) {
        JLabel l = new JLabel(text);
        l.setForeground(Color.LIGHT_GRAY);
        l.setFont(new Font("Segoe UI", bold ? Font.BOLD : Font.PLAIN, 13));
        return l;
    }

    private static JButton makeBtn(String text, Color bg, int width) {
        JButton b = new JButton(text);
        b.setBackground(bg);
        b.setForeground(Color.WHITE);
        b.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        b.setFocusPainted(false);
        b.setBorderPainted(false);
        b.setPreferredSize(new Dimension(width, 30));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return b;
    }
}
