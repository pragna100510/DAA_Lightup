import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;

/**
 * BacktrackingVisualizer — animated step-by-step view of the backtracking solver.
 *
 * Opens as a non-modal dialog. The user can:
 *  • Play / Pause the animation
 *  • Step forward / backward one move at a time
 *  • Adjust speed with a slider (Slow → Fast)
 *  • See real-time stats: steps tried, backtracks, bulbs placed
 *
 * HOW TO INTEGRATE  (see bottom of this file for exact diff)
 * ──────────────────────────────────────────────────────────
 * 1. Add this file to your project.
 * 2. In GameUI.createControlPanel(), add one button:
 *        JButton vizBtn = createMinimalButton("🔍 BT Visualizer", new Color(120, 60, 160));
 *        vizBtn.addActionListener(e -> showBacktrackingVisualizer());
 *        controlPanel.add(vizBtn);
 * 3. Add the method showBacktrackingVisualizer() to GameUI (or LightUpMain):
 *        protected void showBacktrackingVisualizer() {
 *            new BacktrackingVisualizer(this, board, rows, cols).setVisible(true);
 *        }
 */
public class BacktrackingVisualizer extends JDialog {

    // ── Colours ───────────────────────────────────────────────────────────────
    private static final Color BG           = new Color(245, 247, 250);
    private static final Color PANEL_BG     = new Color(255, 255, 255);
    private static final Color WALL_COLOR   = new Color(30,  40,  60);
    private static final Color BLANK_COLOR  = Color.WHITE;
    private static final Color LIT_COLOR    = new Color(255, 253, 200);
    private static final Color BULB_COLOR   = new Color(255, 225,  50);
    private static final Color BULB_GLOW    = new Color(255, 255, 200,  80);
    private static final Color TRY_COLOR    = new Color(100, 180, 255,  90);   // blue  – trying
    private static final Color BACK_COLOR   = new Color(255, 100, 100, 110);   // red   – backtrack
    private static final Color PLACED_COLOR = new Color( 80, 220, 120,  90);   // green – placed
    private static final Color DOT_COLOR    = new Color(180, 180, 180);
    private static final Color GRID_LINE    = new Color(210, 215, 225);
    private static final Color NUM_FG       = new Color(240, 245, 255);
    private static final Color HEADER_BG    = new Color( 30,  40,  60);

    // ── Step types ────────────────────────────────────────────────────────────
    private enum StepType { TRY, PLACE, BACKTRACK, SOLVED }

    /** Immutable snapshot of the board at one visualiser step. */
    private static class Step {
        final StepType   type;
        final boolean[][] bulbs, dots, lit;
        final int         r, c;
        final int         stepCount, backtrackCount, placedCount;
        final String      message;

        Step(StepType t, boolean[][] b, boolean[][] d, boolean[][] l,
             int r, int c, int sc, int bc, int pc, String msg) {
            this.type           = t;
            this.bulbs          = copy2D(b);
            this.dots           = copy2D(d);
            this.lit            = copy2D(l);
            this.r = r; this.c = c;
            this.stepCount      = sc;
            this.backtrackCount = bc;
            this.placedCount    = pc;
            this.message        = msg;
        }

        private static boolean[][] copy2D(boolean[][] src) {
            boolean[][] dst = new boolean[src.length][src[0].length];
            for (int i = 0; i < src.length; i++)
                dst[i] = Arrays.copyOf(src[i], src[i].length);
            return dst;
        }
    }

    // ── Fields ────────────────────────────────────────────────────────────────
    private final CommonCell[][] originalBoard;
    private final int rows, cols;

    private final List<Step> trace = new ArrayList<>();
    private int traceIdx = 0;

    // Current display state
    private boolean[][] dispBulbs, dispDots, dispLit;
    private int dispR = -1, dispC = -1;
    private StepType dispType = null;

    // Recording counters
    private int recSteps, recBacktracks, recPlaced;

    // UI elements
    private JPanel  boardPanel;
    private JLabel  stepLabel, backLabel, placedLabel, messageLabel, progressLabel;
    private JButton playBtn, stepFwdBtn, stepBckBtn;
    private JSlider speedSlider;
    private javax.swing.Timer playTimer;
    private boolean isPlaying = false;

    // ── Constructor ───────────────────────────────────────────────────────────

    public BacktrackingVisualizer(JFrame parent, CommonCell[][] board,
                                  int rows, int cols) {
        super(parent, "Backtracking Visualizer", false);
        this.originalBoard = board;
        this.rows = rows;
        this.cols = cols;

        dispBulbs = new boolean[rows][cols];
        dispDots  = new boolean[rows][cols];
        dispLit   = new boolean[rows][cols];

        buildUI();
        recordTrace();    // record all steps up-front (fast, synchronous)
        showStep(0);

        pack();
        setMinimumSize(new Dimension(780, 640));
        setLocationRelativeTo(parent);
    }

    // ── UI construction ───────────────────────────────────────────────────────

    private void buildUI() {
        setLayout(new BorderLayout());
        getContentPane().setBackground(BG);

        // ── Header ──────────────────────────────────────────────────────────
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(HEADER_BG);
        header.setBorder(new EmptyBorder(14, 22, 14, 22));

        JLabel title = new JLabel("Backtracking Solver — Step-by-Step Visualizer");
        title.setFont(new Font("Segoe UI", Font.BOLD, 18));
        title.setForeground(Color.WHITE);
        header.add(title, BorderLayout.WEST);

        JLabel sub = new JLabel("Watch every try, place & backtrack");
        sub.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        sub.setForeground(new Color(160, 190, 220));
        header.add(sub, BorderLayout.EAST);
        add(header, BorderLayout.NORTH);

        // ── Centre: board + stats ────────────────────────────────────────────
        JPanel centre = new JPanel(new BorderLayout(16, 0));
        centre.setBackground(BG);
        centre.setBorder(new EmptyBorder(16, 16, 8, 16));

        boardPanel = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                drawBoard((Graphics2D) g);
            }
        };
        boardPanel.setBackground(PANEL_BG);
        boardPanel.setPreferredSize(new Dimension(cols * 68, rows * 68));
        boardPanel.setBorder(new LineBorder(new Color(200, 210, 225), 1));
        centre.add(boardPanel, BorderLayout.CENTER);
        centre.add(buildStatsPanel(), BorderLayout.EAST);
        add(centre, BorderLayout.CENTER);

        // ── Bottom controls ──────────────────────────────────────────────────
        add(buildControlBar(), BorderLayout.SOUTH);
    }

    private JPanel buildStatsPanel() {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBackground(PANEL_BG);
        p.setBorder(BorderFactory.createCompoundBorder(
            new LineBorder(new Color(210, 220, 235), 1),
            new EmptyBorder(16, 14, 16, 14)));
        p.setPreferredSize(new Dimension(200, 0));

        p.add(sectionLabel("Statistics"));
        p.add(Box.createVerticalStrut(10));
        stepLabel   = statRow(p, "Steps tried:",  "0");
        backLabel   = statRow(p, "Backtracks:",   "0");
        placedLabel = statRow(p, "Bulbs placed:", "0");

        p.add(Box.createVerticalStrut(18));
        p.add(sectionLabel("Legend"));
        p.add(Box.createVerticalStrut(8));
        legendRow(p, TRY_COLOR,    "Trying cell");
        legendRow(p, PLACED_COLOR, "Bulb placed");
        legendRow(p, BACK_COLOR,   "Backtracking");
        legendRow(p, LIT_COLOR,    "Cell is lit");

        p.add(Box.createVerticalStrut(18));
        p.add(sectionLabel("Current action"));
        p.add(Box.createVerticalStrut(6));
        messageLabel = new JLabel("<html><i>—</i></html>");
        messageLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        messageLabel.setForeground(new Color(60, 80, 120));
        messageLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.add(messageLabel);

        p.add(Box.createVerticalGlue());
        return p;
    }

    private JPanel buildControlBar() {
        JPanel bar = new JPanel(new BorderLayout(12, 0));
        bar.setBackground(BG);
        bar.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(210, 215, 225)),
            new EmptyBorder(10, 18, 10, 18)));

        progressLabel = new JLabel("Step 0 / 0");
        progressLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        progressLabel.setForeground(new Color(120, 130, 150));
        bar.add(progressLabel, BorderLayout.WEST);

        JPanel btns = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
        btns.setBackground(BG);

        JButton resetBtn = ctrlBtn("⏮ Reset");
        stepBckBtn = ctrlBtn("◀ Back");
        playBtn    = ctrlBtn("▶ Play");
        stepFwdBtn = ctrlBtn("▶ Step");
        JButton closeBtn = ctrlBtn("✖ Close");

        resetBtn.addActionListener(e   -> { stop(); showStep(0); });
        stepBckBtn.addActionListener(e -> { stop(); if (traceIdx > 0) showStep(traceIdx - 1); });
        stepFwdBtn.addActionListener(e -> { stop(); if (traceIdx < trace.size() - 1) showStep(traceIdx + 1); });
        playBtn.addActionListener(e    -> togglePlay());
        closeBtn.addActionListener(e   -> dispose());

        btns.add(resetBtn); btns.add(stepBckBtn);
        btns.add(playBtn);  btns.add(stepFwdBtn);
        btns.add(Box.createHorizontalStrut(20));
        btns.add(closeBtn);
        bar.add(btns, BorderLayout.CENTER);

        // Speed slider
        JPanel sp = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        sp.setBackground(BG);
        JLabel sl = new JLabel("Speed:");
        sl.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        sl.setForeground(new Color(100, 110, 130));
        speedSlider = new JSlider(1, 10, 5);
        speedSlider.setPreferredSize(new Dimension(110, 22));
        speedSlider.setBackground(BG);
        speedSlider.addChangeListener(e -> { if (playTimer != null) playTimer.setDelay(delay()); });
        sp.add(sl); sp.add(speedSlider);
        bar.add(sp, BorderLayout.EAST);

        return bar;
    }

    // ── Small UI helpers ──────────────────────────────────────────────────────

    private JLabel sectionLabel(String text) {
        JLabel l = new JLabel(text.toUpperCase());
        l.setFont(new Font("Segoe UI", Font.BOLD, 10));
        l.setForeground(new Color(120, 140, 170));
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        return l;
    }

    private JLabel statRow(JPanel parent, String label, String init) {
        JPanel row = new JPanel(new BorderLayout(4, 0));
        row.setBackground(PANEL_BG);
        row.setMaximumSize(new Dimension(190, 22));
        JLabel lbl = new JLabel(label);
        lbl.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        lbl.setForeground(new Color(90, 100, 120));
        JLabel val = new JLabel(init);
        val.setFont(new Font("Segoe UI", Font.BOLD, 12));
        val.setForeground(new Color(30, 50, 90));
        row.add(lbl, BorderLayout.WEST); row.add(val, BorderLayout.EAST);
        parent.add(row); parent.add(Box.createVerticalStrut(4));
        return val;
    }

    private void legendRow(JPanel parent, Color c, String text) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        row.setBackground(PANEL_BG);
        row.setMaximumSize(new Dimension(190, 20));
        row.add(new JComponent() {
            { setPreferredSize(new Dimension(14, 14)); setOpaque(false); }
            @Override protected void paintComponent(Graphics g) {
                g.setColor(c); ((Graphics2D) g).fillRoundRect(0, 0, 14, 14, 4, 4);
            }
        });
        JLabel lbl = new JLabel(text);
        lbl.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        lbl.setForeground(new Color(60, 70, 90));
        row.add(lbl); parent.add(row);
    }

    private JButton ctrlBtn(String text) {
        JButton b = new JButton(text);
        b.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        b.setForeground(new Color(40, 55, 80));
        b.setBackground(PANEL_BG);
        b.setBorder(BorderFactory.createCompoundBorder(
            new LineBorder(new Color(210, 220, 235), 1),
            new EmptyBorder(6, 14, 6, 14)));
        b.setFocusPainted(false);
        b.setCursor(new Cursor(Cursor.HAND_CURSOR));
        b.addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) { b.setBackground(new Color(235, 240, 255)); }
            @Override public void mouseExited (MouseEvent e) { b.setBackground(PANEL_BG); }
        });
        return b;
    }

    // ── Playback ──────────────────────────────────────────────────────────────

    private void togglePlay() {
        if (isPlaying) { stop(); return; }
        isPlaying = true;
        playBtn.setText("⏸ Pause");
        playTimer = new javax.swing.Timer(delay(), e -> {
            if (traceIdx < trace.size() - 1) showStep(traceIdx + 1);
            else stop();
        });
        playTimer.start();
    }

    private void stop() {
        isPlaying = false;
        playBtn.setText("▶ Play");
        if (playTimer != null) { playTimer.stop(); playTimer = null; }
    }

    /** speed 1 → 600 ms,  speed 10 → 30 ms */
    private int delay() { return 630 - speedSlider.getValue() * 60; }

    // ── Show step ─────────────────────────────────────────────────────────────

    private void showStep(int idx) {
        if (idx < 0 || idx >= trace.size()) return;
        traceIdx = idx;
        Step s = trace.get(idx);

        for (int r = 0; r < rows; r++) {
            dispBulbs[r] = Arrays.copyOf(s.bulbs[r], cols);
            dispDots[r]  = Arrays.copyOf(s.dots[r],  cols);
            dispLit[r]   = Arrays.copyOf(s.lit[r],   cols);
        }
        dispR = s.r; dispC = s.c; dispType = s.type;

        stepLabel.setText(String.valueOf(s.stepCount));
        backLabel.setText(String.valueOf(s.backtrackCount));
        placedLabel.setText(String.valueOf(s.placedCount));
        messageLabel.setText("<html>" + s.message + "</html>");
        progressLabel.setText("Step " + (idx + 1) + " / " + trace.size());
        boardPanel.repaint();
    }

    // ── Board drawing ─────────────────────────────────────────────────────────

    private void drawBoard(Graphics2D g2) {
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int w = boardPanel.getWidth(), h = boardPanel.getHeight();
        int cs = Math.min(w / cols, h / rows);
        int ox = (w - cs * cols) / 2, oy = (h - cs * rows) / 2;

        g2.setColor(PANEL_BG);
        g2.fillRect(ox, oy, cs * cols, cs * rows);

        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                int x = ox + c * cs, y = oy + r * cs;
                CommonCell cell = originalBoard[r][c];
                boolean cur = (r == dispR && c == dispC);

                // Background
                if (cell.isWall()) {
                    g2.setColor(WALL_COLOR);
                    g2.fillRect(x+1, y+1, cs-2, cs-2);
                    if (cell.type == CommonCell.CellType.NUMBER) {
                        g2.setFont(new Font("Segoe UI", Font.BOLD, cs / 2));
                        String ns = String.valueOf(cell.number);
                        FontMetrics fm = g2.getFontMetrics();
                        g2.setColor(NUM_FG);
                        g2.drawString(ns,
                            x + (cs - fm.stringWidth(ns)) / 2,
                            y + (cs + fm.getAscent()) / 2 - 4);
                    }
                } else {
                    Color fill = BLANK_COLOR;
                    if (cur && dispType != null) {
                        switch (dispType) {
                            case TRY:       fill = TRY_COLOR;    break;
                            case PLACE:     fill = PLACED_COLOR; break;
                            case BACKTRACK: fill = BACK_COLOR;   break;
                            default:        fill = dispLit[r][c] ? LIT_COLOR : BLANK_COLOR;
                        }
                    } else if (dispLit[r][c]) {
                        fill = LIT_COLOR;
                    }
                    g2.setColor(fill);
                    g2.fillRect(x+1, y+1, cs-2, cs-2);
                }

                // Bulb
                if (!cell.isWall() && dispBulbs[r][c]) {
                    int bs = cs * 3 / 5, bx = x + (cs-bs)/2, by = y + (cs-bs)/2;
                    g2.setColor(BULB_GLOW); g2.fillOval(bx-3, by-3, bs+6, bs+6);
                    g2.setPaint(new GradientPaint(bx, by, BULB_COLOR.brighter(),
                                                   bx+bs, by+bs, BULB_COLOR));
                    g2.fillOval(bx, by, bs, bs);
                    g2.setColor(BULB_COLOR.darker());
                    g2.setStroke(new BasicStroke(1.5f));
                    g2.drawOval(bx, by, bs, bs);
                }

                // Dot
                if (!cell.isWall() && dispDots[r][c] && !dispBulbs[r][c]) {
                    int ds = Math.max(5, cs/7);
                    g2.setColor(DOT_COLOR);
                    g2.fillOval(x+(cs-ds)/2, y+(cs-ds)/2, ds, ds);
                }

                // Current-cell highlight border
                if (cur && dispType != null && !cell.isWall()) {
                    Color border = switch (dispType) {
                        case TRY       -> new Color( 50, 150, 255);
                        case PLACE     -> new Color( 40, 180,  80);
                        case BACKTRACK -> new Color(220,  50,  50);
                        default        -> new Color(100, 100, 200);
                    };
                    g2.setColor(border);
                    g2.setStroke(new BasicStroke(2.5f));
                    g2.drawRect(x+1, y+1, cs-3, cs-3);
                }

                // Grid lines
                g2.setColor(GRID_LINE);
                g2.setStroke(new BasicStroke(1));
                g2.drawRect(x, y, cs, cs);
            }
        }
    }

    // ── Trace recording ───────────────────────────────────────────────────────

    /**
     * Runs a complete backtracking solve on a fresh copy of the board,
     * recording every TRY / PLACE / BACKTRACK event into {@code trace}.
     * Called once synchronously on dialog open — takes the same time as the
     * solver itself (typically < 100 ms for a 7×7 board).
     */
    private void recordTrace() {
        boolean[][] bulbs = new boolean[rows][cols];
        boolean[][] dots  = new boolean[rows][cols];
        boolean[][] lit   = new boolean[rows][cols];
        recSteps = 0; recBacktracks = 0; recPlaced = 0;

        // Pre-processing: forced deductions
        applyForcedDeductions(bulbs, dots, lit);

        // Backtracking solve
        recBT(bulbs, dots, lit);

        // Final step
        boolean solved = isComplete(bulbs, lit);
        record(StepType.SOLVED, bulbs, dots, lit, -1, -1,
            solved ? "<b style='color:green'>✅ Puzzle solved!</b>"
                   : "<b style='color:red'>❌ No solution found</b>");
    }

    private boolean recBT(boolean[][] bulbs, boolean[][] dots, boolean[][] lit) {
        // Find first unlit, undotted blank cell
        int tr = -1, tc = -1;
        outer:
        for (int r = 0; r < rows; r++)
            for (int c = 0; c < cols; c++)
                if (originalBoard[r][c].type == CommonCell.CellType.BLANK
                        && !lit[r][c] && !dots[r][c]) {
                    tr = r; tc = c; break outer;
                }
        if (tr == -1) return isComplete(bulbs, lit);

        List<int[]> candidates = getCandidates(bulbs, dots, tr, tc);

        for (int[] cand : candidates) {
            int r = cand[0], c = cand[1];
            if (!canPlace(bulbs, dots, r, c)) continue;

            recSteps++;
            record(StepType.TRY, bulbs, dots, lit, r, c,
                "Trying bulb at <b>(" + r + ", " + c + ")</b>");

            bulbs[r][c] = true; recPlaced++;
            relight(bulbs, lit);
            record(StepType.PLACE, bulbs, dots, lit, r, c,
                "Placed bulb at <b>(" + r + ", " + c + ")</b>");

            if (!numExceeded(bulbs) && !hasDeadCell(bulbs, dots, lit))
                if (recBT(bulbs, dots, lit)) return true;

            bulbs[r][c] = false; recPlaced--; recBacktracks++;
            relight(bulbs, lit);
            record(StepType.BACKTRACK, bulbs, dots, lit, r, c,
                "Backtracking from <b>(" + r + ", " + c + ")</b>");
        }
        return false;
    }

    private void record(StepType t, boolean[][] b, boolean[][] d, boolean[][] l,
                        int r, int c, String msg) {
        trace.add(new Step(t, b, d, l, r, c,
            recSteps, recBacktracks, recPlaced, msg));
    }

    // ── Forced deductions ─────────────────────────────────────────────────────

    private void applyForcedDeductions(boolean[][] bulbs, boolean[][] dots, boolean[][] lit) {
        int[][] dirs = {{1,0},{-1,0},{0,1},{0,-1}};
        boolean changed = true;
        while (changed) {
            changed = false;
            relight(bulbs, lit);
            for (int r = 0; r < rows; r++) for (int c = 0; c < cols; c++) {
                if (originalBoard[r][c].type != CommonCell.CellType.NUMBER) continue;
                int need = originalBoard[r][c].number, placed = 0;
                List<int[]> emp = new ArrayList<>();
                for (int[] d : dirs) {
                    int nr=r+d[0], nc=c+d[1];
                    if (nr<0||nr>=rows||nc<0||nc>=cols) continue;
                    if (bulbs[nr][nc]) placed++;
                    else if (originalBoard[nr][nc].type==CommonCell.CellType.BLANK
                             && !dots[nr][nc]) emp.add(new int[]{nr,nc});
                }
                if (placed < need && emp.size() == need-placed)
                    for (int[] e : emp) { if (!bulbs[e[0]][e[1]]) { bulbs[e[0]][e[1]]=true; changed=true; } }
                if (placed == need)
                    for (int[] e : emp) { if (!dots[e[0]][e[1]])  { dots[e[0]][e[1]]=true;  changed=true; } }
            }
        }
    }

    // ── Solver helpers ────────────────────────────────────────────────────────

    private List<int[]> getCandidates(boolean[][] bulbs, boolean[][] dots, int tr, int tc) {
        List<int[]> res = new ArrayList<>();
        if (canPlace(bulbs, dots, tr, tc)) res.add(new int[]{tr, tc});
        int[][] dirs = {{-1,0},{1,0},{0,-1},{0,1}};
        for (int[] d : dirs) {
            int nr=tr+d[0], nc=tc+d[1];
            while (nr>=0&&nr<rows&&nc>=0&&nc<cols) {
                if (originalBoard[nr][nc].isWall()) break;
                if (originalBoard[nr][nc].type==CommonCell.CellType.BLANK
                        && canPlace(bulbs, dots, nr, nc)) res.add(new int[]{nr,nc});
                nr+=d[0]; nc+=d[1];
            }
        }
        Set<String> seen = new HashSet<>();
        List<int[]> uniq = new ArrayList<>();
        for (int[] e : res) if (seen.add(e[0]+","+e[1])) uniq.add(e);
        return uniq;
    }

    private boolean canPlace(boolean[][] bulbs, boolean[][] dots, int r, int c) {
        if (originalBoard[r][c].type != CommonCell.CellType.BLANK) return false;
        if (bulbs[r][c] || dots[r][c]) return false;
        int[][] dirs = {{1,0},{-1,0},{0,1},{0,-1}};
        for (int[] d : dirs) {
            int nr=r+d[0], nc=c+d[1];
            while (nr>=0&&nr<rows&&nc>=0&&nc<cols) {
                if (originalBoard[nr][nc].isWall()) break;
                if (bulbs[nr][nc]) return false;
                nr+=d[0]; nc+=d[1];
            }
        }
        for (int[] d : dirs) {
            int nr=r+d[0], nc=c+d[1];
            if (nr<0||nr>=rows||nc<0||nc>=cols) continue;
            if (originalBoard[nr][nc].type != CommonCell.CellType.NUMBER) continue;
            int cnt=0;
            for (int[] d2 : dirs) {
                int rr=nr+d2[0], cc=nc+d2[1];
                if (rr>=0&&rr<rows&&cc>=0&&cc<cols&&bulbs[rr][cc]) cnt++;
            }
            boolean adj=false;
            for (int[] d2 : dirs) if (nr+d2[0]==r&&nc+d2[1]==c) { adj=true; break; }
            if (adj) cnt++;
            if (cnt > originalBoard[nr][nc].number) return false;
        }
        return true;
    }

    private void relight(boolean[][] bulbs, boolean[][] lit) {
        for (int r=0;r<rows;r++) Arrays.fill(lit[r], false);
        for (int r=0;r<rows;r++) for (int c=0;c<cols;c++) {
            if (!bulbs[r][c]) continue;
            lit[r][c]=true;
            for (int rr=r-1;rr>=0;rr--)   { if(originalBoard[rr][c].isWall()) break; lit[rr][c]=true; }
            for (int rr=r+1;rr<rows;rr++)  { if(originalBoard[rr][c].isWall()) break; lit[rr][c]=true; }
            for (int cc=c-1;cc>=0;cc--)    { if(originalBoard[r][cc].isWall()) break; lit[r][cc]=true; }
            for (int cc=c+1;cc<cols;cc++)  { if(originalBoard[r][cc].isWall()) break; lit[r][cc]=true; }
        }
    }

    private boolean numExceeded(boolean[][] bulbs) {
        int[][] dirs = {{1,0},{-1,0},{0,1},{0,-1}};
        for (int r=0;r<rows;r++) for (int c=0;c<cols;c++) {
            if (originalBoard[r][c].type != CommonCell.CellType.NUMBER) continue;
            int cnt=0;
            for (int[] d : dirs) {
                int nr=r+d[0], nc=c+d[1];
                if (nr>=0&&nr<rows&&nc>=0&&nc<cols&&bulbs[nr][nc]) cnt++;
            }
            if (cnt > originalBoard[r][c].number) return true;
        }
        return false;
    }

    private boolean hasDeadCell(boolean[][] bulbs, boolean[][] dots, boolean[][] lit) {
        for (int r=0;r<rows;r++) for (int c=0;c<cols;c++) {
            if (originalBoard[r][c].type != CommonCell.CellType.BLANK) continue;
            if (lit[r][c] || dots[r][c]) continue;
            if (!canPlace(bulbs, dots, r, c)) {
                boolean ok=false;
                int[][] dirs = {{-1,0},{1,0},{0,-1},{0,1}};
                outer:
                for (int[] d : dirs) {
                    int nr=r+d[0], nc=c+d[1];
                    while (nr>=0&&nr<rows&&nc>=0&&nc<cols) {
                        if (originalBoard[nr][nc].isWall()) break;
                        if (originalBoard[nr][nc].type==CommonCell.CellType.BLANK
                                && canPlace(bulbs, dots, nr, nc)) { ok=true; break outer; }
                        nr+=d[0]; nc+=d[1];
                    }
                }
                if (!ok) return true;
            }
        }
        return false;
    }

    private boolean isComplete(boolean[][] bulbs, boolean[][] lit) {
        int[][] dirs = {{1,0},{-1,0},{0,1},{0,-1}};
        for (int r=0;r<rows;r++) for (int c=0;c<cols;c++)
            if (originalBoard[r][c].type==CommonCell.CellType.BLANK && !lit[r][c]) return false;
        for (int r=0;r<rows;r++) for (int c=0;c<cols;c++) {
            if (!bulbs[r][c]) continue;
            for (int[] d : dirs) {
                int nr=r+d[0], nc=c+d[1];
                while (nr>=0&&nr<rows&&nc>=0&&nc<cols) {
                    if (originalBoard[nr][nc].isWall()) break;
                    if (bulbs[nr][nc]) return false;
                    nr+=d[0]; nc+=d[1];
                }
            }
        }
        for (int r=0;r<rows;r++) for (int c=0;c<cols;c++) {
            if (originalBoard[r][c].type != CommonCell.CellType.NUMBER) continue;
            int cnt=0;
            for (int[] d : dirs) {
                int nr=r+d[0], nc=c+d[1];
                if (nr>=0&&nr<rows&&nc>=0&&nc<cols&&bulbs[nr][nc]) cnt++;
            }
            if (cnt != originalBoard[r][c].number) return false;
        }
        return true;
    }
}
