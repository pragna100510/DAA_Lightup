import java.awt.*;
import java.awt.geom.*;
import javax.swing.*;
import javax.swing.border.EmptyBorder;

/**
 * ComplexityAnalysis - Displays time and space complexity charts
 * for the three AI algorithms: Greedy, Divide & Conquer + DP, and Backtracking.
 *
 * Shown in a dedicated dialog with tabbed charts:
 *  - Time Complexity (Big-O per board size N = rows × cols)
 *  - Space Complexity
 *  - Side-by-side comparison table
 */
public class ComplexityAnalysis extends JDialog {

    // ── Colour palette ────────────────────────────────────────────────────────
    private static final Color BG          = new Color(245, 247, 250);
    private static final Color PANEL_BG    = new Color(255, 255, 255);
    private static final Color GREEDY_CLR  = new Color(52, 168, 83);   // green
    private static final Color DCDP_CLR    = new Color(66, 133, 244);  // blue
    private static final Color BT_CLR      = new Color(234, 67, 53);   // red
    private static final Color AXIS_CLR    = new Color(60, 70, 90);
    private static final Color GRID_CLR    = new Color(225, 230, 240);
    private static final Color TEXT_CLR    = new Color(40, 50, 70);

    // ── Complexity data ───────────────────────────────────────────────────────
    /*
     * N = total blank cells on the board  (≈ rows × cols for an open board)
     * W = number of wall / numbered cells
     *
     * GREEDY
     *   Time  : O(N²)  — scans every blank cell each iteration, up to N iterations
     *   Space : O(N)   — stores score for each cell; DP tables are O(rows×cols)
     *
     * DIVIDE & CONQUER + DP
     *   Time  : O(N²)  — BFS O(N) + DP table build O(N) + pocket evaluation O(N) per move
     *                    Memoised DP tables avoid recomputation → amortised O(N²) per game
     *   Space : O(N)   — three DP tables + pocket lists ≈ O(rows×cols)
     *
     * BACKTRACKING
     *   Time  : O(B^N) worst-case (B ≈ branching factor ≈ 2 per blank cell)
     *                  In practice, forced-deduction pre-processing + pruning
     *                  reduce average case to roughly O(N²) ~ O(N³) for 7×7
     *   Space : O(N)   — recursion stack depth ≤ N; cache stores one solution grid
     */

    // Board-size axis: N = 4..100 (blank cells)
    private static final int[] X_VALS  = new int[]{4,9,16,25,36,49,64,81,100};
    private static final String[] X_LBLS = {"2²","3²","4²","5²","6²","7²","8²","9²","10²"};

    // Normalised growth values (scaled for chart clarity)
    // Greedy : N²
    // DC+DP  : N²  (slightly lower constant in practice — shown separately)
    // BT     : 2^(N/5) capped at chart height   (exponential, but heavily pruned)

    public ComplexityAnalysis(JFrame parent) {
        super(parent, "Algorithm Complexity Analysis", true);
        setBackground(BG);
        getContentPane().setBackground(BG);
        buildUI();
        pack();
        setMinimumSize(new Dimension(820, 600));
        setLocationRelativeTo(parent);
    }

    // ── UI construction ───────────────────────────────────────────────────────

    private void buildUI() {
        setLayout(new BorderLayout(0, 0));

        // Header
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(new Color(30, 40, 60));
        header.setBorder(new EmptyBorder(16, 24, 16, 24));
        JLabel title = new JLabel("Algorithm Complexity Analysis");
        title.setFont(new Font("Segoe UI", Font.BOLD, 20));
        title.setForeground(Color.WHITE);
        JLabel sub = new JLabel("Greedy  ·  Divide & Conquer + DP  ·  Backtracking");
        sub.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        sub.setForeground(new Color(180, 200, 220));
        JPanel titles = new JPanel(new GridLayout(2, 1));
        titles.setOpaque(false);
        titles.add(title);
        titles.add(sub);
        header.add(titles, BorderLayout.WEST);
        add(header, BorderLayout.NORTH);

        // Tabbed pane
        JTabbedPane tabs = new JTabbedPane();
        tabs.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        tabs.setBackground(BG);
        tabs.addTab("Time Complexity",  buildTimePanel());
        tabs.addTab("Space Complexity", buildSpacePanel());
        tabs.addTab("Comparison Table", buildTablePanel());
        tabs.addTab("Explanation",       buildExplanationPanel());
        add(tabs, BorderLayout.CENTER);

        // Footer
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        footer.setBackground(BG);
        footer.setBorder(new EmptyBorder(8, 16, 8, 16));
        JButton closeBtn = new JButton("Close");
        closeBtn.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        closeBtn.addActionListener(e -> dispose());
        footer.add(closeBtn);
        add(footer, BorderLayout.SOUTH);
    }

    // ── Time-complexity chart ─────────────────────────────────────────────────

    private JPanel buildTimePanel() {
        JPanel wrapper = new JPanel(new BorderLayout(12, 12));
        wrapper.setBackground(BG);
        wrapper.setBorder(new EmptyBorder(16, 16, 16, 16));

        // Legend
        wrapper.add(buildLegend("Time Complexity (Big-O)"), BorderLayout.NORTH);

        // Chart
        JPanel chart = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                drawTimeChart((Graphics2D) g, getWidth(), getHeight());
            }
        };
        chart.setBackground(PANEL_BG);
        chart.setBorder(BorderFactory.createLineBorder(new Color(210, 215, 225)));
        chart.setPreferredSize(new Dimension(680, 360));
        wrapper.add(chart, BorderLayout.CENTER);

        // Notes
        JLabel note = new JLabel(
            "<html><font color='#667788'><i>X-axis: board size N (blank cells).  " +
            "Y-axis: relative operation count (log scale for BT).</i></font></html>");
        note.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        wrapper.add(note, BorderLayout.SOUTH);
        return wrapper;
    }

    private void drawTimeChart(Graphics2D g2, int w, int h) {
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setBackground(PANEL_BG);
        g2.clearRect(0, 0, w, h);

        int pad = 60, bw = w - pad - 30, bh = h - pad - 30;
        double maxY = 1.0;  // normalised

        // Grid
        g2.setColor(GRID_CLR);
        g2.setStroke(new BasicStroke(1, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
                10, new float[]{4, 4}, 0));
        int yLines = 5;
        for (int i = 0; i <= yLines; i++) {
            int y = pad + (bh * i / yLines);
            g2.drawLine(pad, y, pad + bw, y);
        }

        // Axes
        g2.setColor(AXIS_CLR);
        g2.setStroke(new BasicStroke(1.5f));
        g2.drawLine(pad, pad, pad, pad + bh);
        g2.drawLine(pad, pad + bh, pad + bw, pad + bh);

        // Axis labels
        g2.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        g2.setColor(TEXT_CLR);
        for (int i = 0; i < X_VALS.length; i++) {
            int x = pad + (i * bw / (X_VALS.length - 1));
            g2.drawString(X_LBLS[i], x - 8, pad + bh + 16);
        }
        g2.drawString("N (blank cells)", pad + bw / 2 - 40, pad + bh + 28);

        g2.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        String[] yLabels = {"0", "0.2", "0.4", "0.6", "0.8", "1.0"};
        for (int i = 0; i <= yLines; i++) {
            int y = pad + (bh * (yLines - i) / yLines);
            g2.drawString(yLabels[i], 4, y + 4);
        }

        // Y-axis title
        Graphics2D g3 = (Graphics2D) g2.create();
        g3.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        g3.rotate(-Math.PI / 2, 14, h / 2);
        g3.drawString("Relative Operations", 7, 25 + h/2);
        g3.dispose();

        // ── Plot lines ────────────────────────────────────────────────────────
        // Greedy: O(N²)  → normalised by max(N²)
        drawCurve(g2, pad, bh, bw, GREEDY_CLR, 2.5f,
            n -> (double)(n * n) / (100.0 * 100.0));

        // DC+DP:  O(N²) with smaller constant (0.6×)
        drawCurve(g2, pad, bh, bw, DCDP_CLR, 2.5f,
            n -> 0.6 * (double)(n * n) / (100.0 * 100.0));

        // Backtracking: O(2^(N/8)) — exponential, capped
        drawCurve(g2, pad, bh, bw, BT_CLR, 2.5f,
            n -> Math.min(1.0, Math.pow(2.0, n / 16.0) / Math.pow(2.0, 100.0 / 16.0)));
    }

    // ── Space-complexity chart ────────────────────────────────────────────────

    private JPanel buildSpacePanel() {
        JPanel wrapper = new JPanel(new BorderLayout(12, 12));
        wrapper.setBackground(BG);
        wrapper.setBorder(new EmptyBorder(16, 16, 16, 16));
        wrapper.add(buildSpaceLegend(), BorderLayout.NORTH);

        JPanel chart = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                drawSpaceChart((Graphics2D) g, getWidth(), getHeight());
            }
        };
        chart.setBackground(PANEL_BG);
        chart.setBorder(BorderFactory.createLineBorder(new Color(210, 215, 225)));
        chart.setPreferredSize(new Dimension(680, 360));
        wrapper.add(chart, BorderLayout.CENTER);

        JLabel note = new JLabel(
            "<html><font color='#667788'><i>All three algorithms use O(N) auxiliary space " +
            "— Backtracking's recursion stack is bounded by board depth.</i></font></html>");
        note.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        wrapper.add(note, BorderLayout.SOUTH);
        return wrapper;
    }

    private JPanel buildSpaceLegend() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 20, 4));
        p.setBackground(BG);

        JLabel title = new JLabel("Space Complexity (Big-O)");
        title.setFont(new Font("Segoe UI", Font.BOLD, 14));
        title.setForeground(TEXT_CLR);
        p.add(title);

        p.add(buildLegendItem(GREEDY_CLR, "Greedy  O(N)"));
        p.add(buildLegendItem(DCDP_CLR,   "DC + DP  O(N) — 3 DP tables"));
        p.add(buildLegendItem(BT_CLR,     "Backtracking  O(N) — recursion stack"));
        return p;
    }

    private void drawSpaceChart(Graphics2D g2, int w, int h) {
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setBackground(PANEL_BG);
        g2.clearRect(0, 0, w, h);

        int pad = 60, bw = w - pad - 30, bh = h - pad - 30;

        // Grid
        g2.setColor(GRID_CLR);
        g2.setStroke(new BasicStroke(1, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
                10, new float[]{4, 4}, 0));
        int yLines = 5;
        for (int i = 0; i <= yLines; i++) {
            int y = pad + (bh * i / yLines);
            g2.drawLine(pad, y, pad + bw, y);
        }

        // Axes
        g2.setColor(AXIS_CLR);
        g2.setStroke(new BasicStroke(1.5f));
        g2.drawLine(pad, pad, pad, pad + bh);
        g2.drawLine(pad, pad + bh, pad + bw, pad + bh);

        // Axis labels
        g2.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        g2.setColor(TEXT_CLR);
        for (int i = 0; i < X_VALS.length; i++) {
            int x = pad + (i * bw / (X_VALS.length - 1));
            g2.drawString(X_LBLS[i], x - 8, pad + bh + 16);
        }
        g2.drawString("N (blank cells)", pad + bw / 2 - 40, pad + bh + 28);

        String[] yLabels = {"0", "0.2", "0.4", "0.6", "0.8", "1.0"};
        for (int i = 0; i <= yLines; i++) {
            int y = pad + (bh * (yLines - i) / yLines);
            g2.drawString(yLabels[i], 4, y + 4);
        }

        Graphics2D g3 = (Graphics2D) g2.create();
        g3.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        g3.rotate(-Math.PI / 2, 14, h / 2);
        g3.drawString("Relative Memory", 7, 25 + h / 2);
        g3.dispose();

        // All three are O(N), with slightly different constants
        // Greedy: O(N)  — score array
        drawCurve(g2, pad, bh, bw, GREEDY_CLR, 2.5f,
            n -> (double) n / 100.0);

        // DC+DP: O(N)  — three DP tables (constant ≈ 3)
        drawCurve(g2, pad, bh, bw, DCDP_CLR, 2.5f,
            n -> 0.8 * (double) n / 100.0 + 0.05);

        // Backtracking: O(N)  — recursion stack + solution cache
        drawCurve(g2, pad, bh, bw, BT_CLR, 2.5f,
            n -> 0.65 * (double) n / 100.0 + 0.02);
    }

    // ── Comparison table ──────────────────────────────────────────────────────

    private JPanel buildTablePanel() {
        JPanel wrapper = new JPanel(new BorderLayout(12, 16));
        wrapper.setBackground(BG);
        wrapper.setBorder(new EmptyBorder(20, 20, 20, 20));

        String[] cols = {"Property", "Greedy", "Divide & Conquer + DP", "Backtracking"};
        Object[][] data = {
            {"Time Complexity",    "O(N²)",        "O(N²) amortised",       "O(2^N) worst-case"},
            {"Space Complexity",   "O(N)",          "O(N) — 3 DP tables",    "O(N) — recursion stack"},
            {"Completeness",       "Not complete",  "Not complete",          "Complete (finds solution)"},
            {"Optimality",         "Heuristic",     "DP-guided heuristic",   "Optimal (first valid)"},
            {"Practical Speed",    "Very fast",     "Fast (memoised)",       "Slower on large boards"},
            {"Solution Quality",   "Good",          "Very good",             "Exact"},
            {"Handles 7×7",        "Yes",           "Yes",                   "Yes (with pruning)"},
            {"Handles 10×10+",     "Yes",           "Yes",                   "May time out"},
            {"Uses Memoisation",   "No",            "Yes (HashMap cache)",   "Partial (solution cache)"},
            {"Forced Deductions",  "Yes",           "Yes",                   "Yes (pre-processing)"},
        };

        JTable table = new JTable(data, cols) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
            @Override public Component prepareRenderer(
                    javax.swing.table.TableCellRenderer r, int row, int col) {
                Component c = super.prepareRenderer(r, row, col);
                c.setFont(new Font("Segoe UI", Font.PLAIN, 12));
                if (row % 2 == 0) c.setBackground(new Color(248, 250, 255));
                else               c.setBackground(Color.WHITE);
                if (col == 0) {
                    c.setFont(new Font("Segoe UI", Font.BOLD, 12));
                    c.setForeground(TEXT_CLR);
                } else if (col == 1) c.setForeground(GREEDY_CLR.darker());
                  else if (col == 2) c.setForeground(DCDP_CLR.darker());
                  else               c.setForeground(BT_CLR.darker());
                return c;
            }
        };
        table.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        table.setRowHeight(26);
        table.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 12));
        table.getTableHeader().setBackground(new Color(30, 40, 60));
        table.getTableHeader().setForeground(Color.WHITE);
        table.setGridColor(new Color(220, 225, 235));
        table.setIntercellSpacing(new Dimension(8, 4));
        table.getColumnModel().getColumn(0).setPreferredWidth(160);
        table.getColumnModel().getColumn(1).setPreferredWidth(110);
        table.getColumnModel().getColumn(2).setPreferredWidth(185);
        table.getColumnModel().getColumn(3).setPreferredWidth(185);

        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(BorderFactory.createLineBorder(new Color(210, 215, 225)));
        wrapper.add(scroll, BorderLayout.CENTER);

        JLabel heading = new JLabel("Side-by-Side Algorithm Comparison");
        heading.setFont(new Font("Segoe UI", Font.BOLD, 15));
        heading.setForeground(TEXT_CLR);
        wrapper.add(heading, BorderLayout.NORTH);
        return wrapper;
    }

    // ── Explanation panel ─────────────────────────────────────────────────────

    private JPanel buildExplanationPanel() {
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setBackground(BG);
        wrapper.setBorder(new EmptyBorder(16, 20, 16, 20));

        JTextPane text = new JTextPane();
        text.setContentType("text/html");
        text.setEditable(false);
        text.setBackground(PANEL_BG);
        text.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        text.setText(
            "<html><body style='font-family:Segoe UI,Arial; font-size:12px; margin:12px;'>"

            + "<h3 style='color:#1a2a4a;'>1. Greedy Algorithm</h3>"
            + "<b>Time: O(N²)</b> &nbsp;|&nbsp; <b>Space: O(N)</b><br>"
            + "<p>For each of the up-to-N moves, the greedy scorer scans every blank cell "
            + "(O(N)) to pick the highest-scoring placement — giving O(N²) overall. "
            + "The scoring function evaluates lighting gain, numbered-cell help, and an "
            + "unlit-cell bonus, all computable in O(rows + cols) per cell. "
            + "Space is dominated by the score array: O(N).</p>"

            + "<h3 style='color:#1a2a4a;'>2. Divide & Conquer + Dynamic Programming</h3>"
            + "<b>Time: O(N²) amortised</b> &nbsp;|&nbsp; <b>Space: O(N)</b><br>"
            + "<p><b>Divide:</b> BFS flood-fill partitions the board into wall-bounded "
            + "pockets — O(N) per BFS call. "
            + "<b>Conquer:</b> Each pocket evaluates candidates using three pre-built DP tables "
            + "(dpLightGain, dpNumberScore, dpFutureBlock), each computed in O(N). "
            + "<b>Combine:</b> The best candidate across pockets is returned in O(pockets). "
            + "HashMap memoisation ensures DP tables are reused when the board state has not "
            + "changed, giving amortised O(N²) across a full game. "
            + "Space: three O(rows×cols) tables = O(N).</p>"

            + "<h3 style='color:#1a2a4a;'>3. Backtracking</h3>"
            + "<b>Time: O(2<sup>N</sup>) worst-case, O(N²)–O(N³) in practice</b> "
            + "&nbsp;|&nbsp; <b>Space: O(N)</b><br>"
            + "<p>Pure backtracking would explore all 2<sup>N</sup> subsets of blank cells. "
            + "Three optimisations dramatically prune the tree:<br>"
            + "&nbsp;&nbsp;<b>(a) Forced deductions</b> — numbered cells with only one legal "
            + "neighbour are resolved before search begins.<br>"
            + "&nbsp;&nbsp;<b>(b) Dead-cell pruning</b> — if any blank cell cannot be lit, "
            + "the branch is cut immediately.<br>"
            + "&nbsp;&nbsp;<b>(c) Adaptive solution cache</b> — the solved board is cached "
            + "between AI turns; subsequent moves just read from the cache (O(1)).<br>"
            + "On the 7×7 puzzle used here, average-case depth is ≤ 20 with branching ≈ 2, "
            + "making it fast in practice. "
            + "Space: recursion depth is at most N; one full solution grid is cached.</p>"

            + "<h3 style='color:#1a2a4a;'>Variable Definitions</h3>"
            + "<ul>"
            + "<li><b>N</b> = number of blank cells ≈ rows × cols minus walls.</li>"
            + "<li><b>B</b> = effective branching factor (≈ 2 for BT after pruning).</li>"
            + "</ul>"

            + "</body></html>"
        );

        JScrollPane scroll = new JScrollPane(text);
        scroll.setBorder(BorderFactory.createLineBorder(new Color(210, 215, 225)));
        wrapper.add(scroll, BorderLayout.CENTER);
        return wrapper;
    }

    // ── Helper: draw a single curve ───────────────────────────────────────────

    @FunctionalInterface
    interface CurveFunction { double apply(int n); }

    private void drawCurve(Graphics2D g2, int pad, int bh, int bw,
                           Color color, float stroke, CurveFunction fn) {
        g2.setColor(color);
        g2.setStroke(new BasicStroke(stroke, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

        GeneralPath path = new GeneralPath();
        boolean first = true;

        for (int i = 0; i < X_VALS.length; i++) {
            int n = X_VALS[i];
            double yVal = Math.min(1.0, Math.max(0.0, fn.apply(n)));
            int x = pad + (i * bw / (X_VALS.length - 1));
            int y = pad + bh - (int)(yVal * bh);
            if (first) { path.moveTo(x, y); first = false; }
            else          path.lineTo(x, y);
        }
        g2.draw(path);

        // Draw data points
        for (int i = 0; i < X_VALS.length; i++) {
            int n = X_VALS[i];
            double yVal = Math.min(1.0, Math.max(0.0, fn.apply(n)));
            int x = pad + (i * bw / (X_VALS.length - 1));
            int y = pad + bh - (int)(yVal * bh);
            g2.fillOval(x - 4, y - 4, 8, 8);
        }
    }

    // ── Legend ────────────────────────────────────────────────────────────────

    private JPanel buildLegend(String chartTitle) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 20, 4));
        p.setBackground(BG);

        JLabel title = new JLabel(chartTitle);
        title.setFont(new Font("Segoe UI", Font.BOLD, 14));
        title.setForeground(TEXT_CLR);
        p.add(title);

        p.add(buildLegendItem(GREEDY_CLR, "Greedy  O(N²)"));
        p.add(buildLegendItem(DCDP_CLR,   "DC + DP  O(N²) amortised"));
        p.add(buildLegendItem(BT_CLR,     "Backtracking  O(2^N) worst"));
        return p;
    }

    private JPanel buildLegendItem(Color c, String label) {
        JPanel item = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        item.setBackground(BG);
        item.add(new JComponent() {
            { setPreferredSize(new Dimension(22, 14)); setOpaque(false); }
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                                    RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(c);
                g2.setStroke(new BasicStroke(2.5f));
                g2.drawLine(0, 7, 22, 7);
                g2.fillOval(7, 3, 8, 8);
            }
        });
        JLabel lbl = new JLabel(label);
        lbl.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        lbl.setForeground(TEXT_CLR);
        item.add(lbl);
        return item;
    }
}
