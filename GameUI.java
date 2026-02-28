import java.awt.*;
import java.awt.event.*;
import java.util.*;
import javax.swing.*;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;

/**
 * Base UI class for the Light Up game
 */
public class GameUI extends JFrame {

    // ===== ALGORITHM SELECTION =====

    public enum AlgoMode { GREEDY, DC_DP, BACKTRACKING }

    protected AlgoMode aiAlgoMode     = AlgoMode.DC_DP;
    protected AlgoMode solverAlgoMode = AlgoMode.DC_DP;

    // Accessible by subclass (LightUp) to read selected mode
    protected JComboBox<String> aiAlgoCombo;
    protected JComboBox<String> solverAlgoCombo;

    // ===== STATE MANAGEMENT =====
    
    public static class BoardState {
        public boolean[][] bulbs;
        public boolean[][] dots;
        public boolean userTurn;
        public boolean userContributed;
        public boolean aiContributed;
        public int lastComputerR;
        public int lastComputerC;

        public BoardState(int rows, int cols) {
            bulbs = new boolean[rows][cols];
            dots = new boolean[rows][cols];
        }
    }

    protected CommonCell[][] board;
    protected int rows = 7, cols = 7;
    protected JPanel gridPanel;
    protected JLabel statusLabel;
    protected boolean userTurn = true;
    protected int lastComputerR = -1, lastComputerC = -1;
    protected boolean userContributed = false;
    protected boolean aiContributed = false;

    protected Deque<BoardState> undoStack = new ArrayDeque<>();
    protected Deque<BoardState> redoStack = new ArrayDeque<>();

    // ===== COLOR SCHEME =====
    protected final Color BG_COLOR = new Color(245, 247, 250);
    protected final Color GRID_BG = new Color(255, 255, 255);
    protected final Color WALL_COLOR = new Color(30, 40, 60);
    protected final Color NUMBER_COLOR = new Color(240, 245, 255);
    protected final Color LIT_COLOR = new Color(255, 253, 200);
    protected final Color BULB_COLOR = new Color(255, 225, 50);
    protected final Color BULB_GLOW = new Color(255, 255, 200, 80);
    protected final Color DOT_COLOR = new Color(180, 180, 180);
    protected final Color CONFLICT_COLOR = new Color(255, 100, 100, 120); // More visible red
    protected final Color WRONG_CELL_COLOR = new Color(255, 200, 200, 150); // Light red for wrong cells
    protected final Color AI_HIGHLIGHT = new Color(80, 200, 120, 100);
    protected final Color GRID_LINE = new Color(210, 215, 225);
    protected final Color PANEL_BG = new Color(250, 252, 255);

    // ===== UI BUILDING =====
    
    public void buildUI() {
        setLayout(new BorderLayout(0, 0));
        getContentPane().setBackground(BG_COLOR);

        // Header Panel
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBackground(PANEL_BG);
        headerPanel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0,0,1,0,new Color(220,225,230)),
            new EmptyBorder(15,25,15,25)));

        JLabel titleLabel = new JLabel("LIGHT UP", SwingConstants.LEFT);
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 24));
        titleLabel.setForeground(new Color(30,40,60));
        headerPanel.add(titleLabel, BorderLayout.WEST);

        JLabel turnIndicator = new JLabel("", SwingConstants.RIGHT);
        turnIndicator.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        turnIndicator.setForeground(new Color(100,120,140));
        
        Timer timer = new Timer(500, e -> {
            if (userTurn) {
                turnIndicator.setText("YOUR TURN");
                turnIndicator.setForeground(new Color(80,150,80));
            } else {
                turnIndicator.setText("AI THINKING");
                turnIndicator.setForeground(new Color(200,100,100));
            }
        });
        timer.start();
        
        headerPanel.add(turnIndicator, BorderLayout.EAST);
        add(headerPanel, BorderLayout.NORTH);

        // Main Panel with Grid
        JPanel mainPanel = new JPanel(new BorderLayout(20,20));
        mainPanel.setBackground(BG_COLOR);
        mainPanel.setBorder(new EmptyBorder(20,20,20,20));

        JPanel gridContainer = new JPanel(new BorderLayout());
        gridContainer.setBackground(BG_COLOR);

        gridPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                paintGrid(g);
            }
        };
        gridPanel.setBackground(GRID_BG);
        gridPanel.setPreferredSize(new Dimension(cols*70, rows*70));
        
        // Mouse listener for user interaction
        gridPanel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                handleMouseClick(e);
            }
        });
        
        gridContainer.add(gridPanel, BorderLayout.CENTER);
        mainPanel.add(gridContainer, BorderLayout.CENTER);

        // Control Panel
        JPanel controlPanel = createControlPanel();
        mainPanel.add(controlPanel, BorderLayout.EAST);
        add(mainPanel, BorderLayout.CENTER);

        // Status Panel
        JPanel statusPanel = createStatusPanel();
        add(statusPanel, BorderLayout.SOUTH);
        
        // Initialize with empty board if not set
        if (board == null) {
            initializeEmptyBoard();
        }
    }

    protected void initializeEmptyBoard() {
        board = new CommonCell[rows][cols];
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                board[r][c] = new CommonCell(CommonCell.CellType.BLANK, -1, r, c);
            }
        }
    }

    protected JPanel createControlPanel() {
        JPanel controlPanel = new JPanel();
        controlPanel.setLayout(new BoxLayout(controlPanel, BoxLayout.Y_AXIS));
        controlPanel.setBackground(PANEL_BG);
        controlPanel.setBorder(BorderFactory.createCompoundBorder(
            new LineBorder(new Color(220,225,230),1),
            new EmptyBorder(20,15,20,15)));

        JButton newGameBtn = createMinimalButton("New Game", new Color(70,130,180));
        JButton restartBtn = createMinimalButton("Restart", new Color(65,105,225));
        JButton undoBtn    = createMinimalButton("Undo", new Color(72, 61,139));
        JButton redoBtn    = createMinimalButton("Redo", new Color(106,90,205));
        JButton solveBtn   = createMinimalButton("Solve", new Color(50,180, 80));

        undoBtn.addActionListener(e -> handleUndo());
        redoBtn.addActionListener(e -> handleRedo());

        controlPanel.add(newGameBtn);
        controlPanel.add(Box.createVerticalStrut(8));
        controlPanel.add(restartBtn);
        controlPanel.add(Box.createVerticalStrut(8));
        controlPanel.add(undoBtn);
        controlPanel.add(Box.createVerticalStrut(8));
        controlPanel.add(redoBtn);
        controlPanel.add(Box.createVerticalStrut(8));
        controlPanel.add(solveBtn);

        // ── Algorithm selectors ────────────────────────────────────────────
        String[] algoOptions = { "Greedy", "DC + DP", "Backtracking" };

        controlPanel.add(Box.createVerticalStrut(16));

        JSeparator sep1 = new JSeparator();
        sep1.setMaximumSize(new Dimension(160, 1));
        controlPanel.add(sep1);
        controlPanel.add(Box.createVerticalStrut(10));

        JLabel aiLabel = new JLabel("AI Algorithm:");
        aiLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));
        aiLabel.setForeground(new Color(50, 60, 80));
        aiLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        controlPanel.add(aiLabel);
        controlPanel.add(Box.createVerticalStrut(4));

        aiAlgoCombo = new JComboBox<>(algoOptions);
        aiAlgoCombo.setSelectedIndex(1); // default: DC + DP
        aiAlgoCombo.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        aiAlgoCombo.setMaximumSize(new Dimension(160, 28));
        aiAlgoCombo.setAlignmentX(Component.CENTER_ALIGNMENT);
        aiAlgoCombo.addActionListener(e -> {
            switch (aiAlgoCombo.getSelectedIndex()) {
                case 0: aiAlgoMode = AlgoMode.GREEDY;       break;
                case 1: aiAlgoMode = AlgoMode.DC_DP;        break;
                case 2: aiAlgoMode = AlgoMode.BACKTRACKING; break;
            }
        });
        controlPanel.add(aiAlgoCombo);

        controlPanel.add(Box.createVerticalStrut(12));

        JSeparator sep2 = new JSeparator();
        sep2.setMaximumSize(new Dimension(160, 1));
        controlPanel.add(sep2);
        controlPanel.add(Box.createVerticalStrut(10));

        JLabel solverLabel = new JLabel("Solver Algorithm:");
        solverLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));
        solverLabel.setForeground(new Color(50, 60, 80));
        solverLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        controlPanel.add(solverLabel);
        controlPanel.add(Box.createVerticalStrut(4));

        solverAlgoCombo = new JComboBox<>(algoOptions);
        solverAlgoCombo.setSelectedIndex(1); // default: DC + DP
        solverAlgoCombo.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        solverAlgoCombo.setMaximumSize(new Dimension(160, 28));
        solverAlgoCombo.setAlignmentX(Component.CENTER_ALIGNMENT);
        solverAlgoCombo.addActionListener(e -> {
            switch (solverAlgoCombo.getSelectedIndex()) {
                case 0: solverAlgoMode = AlgoMode.GREEDY;       break;
                case 1: solverAlgoMode = AlgoMode.DC_DP;        break;
                case 2: solverAlgoMode = AlgoMode.BACKTRACKING; break;
            }
        });
        controlPanel.add(solverAlgoCombo);

        JPanel counterPanel = new JPanel();
        counterPanel.setBackground(PANEL_BG);
        counterPanel.setBorder(new EmptyBorder(15,0,0,0));
        JLabel moveCounter = new JLabel("Moves: 0");
        moveCounter.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        moveCounter.setForeground(new Color(120,130,150));
        counterPanel.add(moveCounter);
        controlPanel.add(Box.createVerticalGlue());
        controlPanel.add(counterPanel);

        Timer timer = new Timer(100, e -> moveCounter.setText("Moves: " + (undoStack.size())));
        timer.start();

        return controlPanel;
    }

    private JPanel createStatusPanel() {
        JPanel statusPanel = new JPanel(new BorderLayout());
        statusPanel.setBackground(PANEL_BG);
        statusPanel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1,0,0,0,new Color(220,225,230)),
            new EmptyBorder(8,20,8,20)));
        
        statusLabel = new JLabel("Click cells to place bulbs. Red = wrong placement.");
        statusLabel.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        statusLabel.setForeground(new Color(80,90,110));
        statusPanel.add(statusLabel, BorderLayout.WEST);
        
        JLabel modeLabel = new JLabel("7×7 Puzzle  |  D&C + DP AI");
        modeLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        modeLabel.setForeground(new Color(140,150,170));
        statusPanel.add(modeLabel, BorderLayout.EAST);
        
        return statusPanel;
    }

    protected JButton createMinimalButton(String text, Color baseColor) {
        JButton button = new JButton(text);
        button.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        button.setForeground(new Color(50,60,80));
        button.setBackground(Color.WHITE);
        button.setBorder(BorderFactory.createCompoundBorder(
            new LineBorder(new Color(220,225,230),1),
            new EmptyBorder(10,20,10,20)));
        button.setFocusPainted(false);
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));
        button.setAlignmentX(Component.CENTER_ALIGNMENT);
        
        button.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                button.setBackground(new Color(245,248,255));
                button.setBorder(BorderFactory.createCompoundBorder(
                    new LineBorder(baseColor,1),
                    new EmptyBorder(10,20,10,20)));
            }
            @Override
            public void mouseExited(MouseEvent e) {
                button.setBackground(Color.WHITE);
                button.setBorder(BorderFactory.createCompoundBorder(
                    new LineBorder(new Color(220,225,230),1),
                    new EmptyBorder(10,20,10,20)));
            }
        });
        
        return button;
    }

    // ===== USER INPUT HANDLING =====
    
    protected void handleMouseClick(MouseEvent e) {
        // To be overridden by subclass
    }

    // ===== UNDO/REDO =====
    
    protected void saveState() {
        pushUndoState();
    }

    protected void handleUndo() {
        if (undoStack.size() > 1) {
            redoStack.push(snapshotState());
            undoStack.pop();
            applyState(undoStack.peek());
            recomputeLighting();
            statusLabel.setText("Undo move");
            gridPanel.repaint();
        } else {
            statusLabel.setText("Nothing to undo");
        }
    }

    protected void handleRedo() {
        if (!redoStack.isEmpty()) {
            undoStack.push(snapshotState());
            applyState(redoStack.pop());
            recomputeLighting();
            statusLabel.setText("Redo move");
            gridPanel.repaint();
        } else {
            statusLabel.setText("Nothing to redo");
        }
    }

    protected BoardState snapshotState() {
        BoardState st = new BoardState(rows, cols);
        for (int r = 0; r < rows; r++)
            for (int c = 0; c < cols; c++) {
                st.bulbs[r][c] = board[r][c].bulb;
                st.dots[r][c] = board[r][c].dot;
            }
        st.userTurn = userTurn;
        st.userContributed = userContributed;
        st.aiContributed = aiContributed;
        st.lastComputerR = lastComputerR;
        st.lastComputerC = lastComputerC;
        return st;
    }

    protected void applyState(BoardState st) {
        for (int r = 0; r < rows; r++)
            for (int c = 0; c < cols; c++) {
                board[r][c].bulb = st.bulbs[r][c];
                board[r][c].dot = st.dots[r][c];
            }
        userTurn = st.userTurn;
        userContributed = st.userContributed;
        aiContributed = st.aiContributed;
        lastComputerR = st.lastComputerR;
        lastComputerC = st.lastComputerC;
    }

    protected void pushUndoState() {
        undoStack.push(snapshotState());
    }

    // ===== LIGHTING COMPUTATION =====
    
    public static void recomputeLighting(CommonCell[][] board, int rows, int cols) {
        // Clear all lighting
        for (int r = 0; r < rows; r++)
            for (int c = 0; c < cols; c++)
                board[r][c].lit = false;

        // For each bulb, light up its rays
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                if (!board[r][c].bulb) continue;
                
                board[r][c].lit = true;
                
                // Light up in all 4 directions
                for (int rr = r - 1; rr >= 0; rr--) {
                    if (board[rr][c].isWall()) break;
                    board[rr][c].lit = true;
                }
                for (int rr = r + 1; rr < rows; rr++) {
                    if (board[rr][c].isWall()) break;
                    board[rr][c].lit = true;
                }
                for (int cc = c - 1; cc >= 0; cc--) {
                    if (board[r][cc].isWall()) break;
                    board[r][cc].lit = true;
                }
                for (int cc = c + 1; cc < cols; cc++) {
                    if (board[r][cc].isWall()) break;
                    board[r][cc].lit = true;
                }
            }
        }
    }

    protected void recomputeLighting() {
        recomputeLighting(board, rows, cols);
    }

    // ===== VALIDATION =====
    
    public static String validateSolution(CommonCell[][] board, int rows, int cols) {
        // Check all cells are lit
        for (int r = 0; r < rows; r++)
            for (int c = 0; c < cols; c++)
                if (board[r][c].type == CommonCell.CellType.BLANK && !board[r][c].lit)
                    return "❌ Unlit cell at ("+r+","+c+")";

        int[][] dirs = {{1,0},{-1,0},{0,1},{0,-1}};
        
        // Check no bulbs see each other
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                if (!board[r][c].bulb) continue;
                
                for (int[] d : dirs) {
                    int nr = r + d[0], nc = c + d[1];
                    while (nr >= 0 && nr < rows && nc >= 0 && nc < cols) {
                        if (board[nr][nc].isWall()) break;
                        if (board[nr][nc].bulb)
                            return "❌ Bulbs see each other at ("+r+","+c+") & ("+nr+","+nc+")";
                        nr += d[0];
                        nc += d[1];
                    }
                }
            }
        }
        
        // Check numbered cells
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                if (board[r][c].type != CommonCell.CellType.NUMBER) continue;
                
                int count = 0;
                for (int[] d : dirs) {
                    int nr = r + d[0], nc = c + d[1];
                    if (nr >= 0 && nr < rows && nc >= 0 && nc < cols && board[nr][nc].bulb)
                        count++;
                }
                
                if (count != board[r][c].number)
                    return "❌ Number mismatch at ("+r+","+c+")";
            }
        }
        
        return "Puzzle solved! ✅";
    }

    /**
     * Check if a specific cell is in violation of the rules
     */
    protected boolean isCellInViolation(int r, int c) {
        CommonCell cell = board[r][c];
        
        // Blank cells with bulbs
        if (cell.type == CommonCell.CellType.BLANK && cell.bulb) {
            // Check if this bulb sees another bulb
            int[][] dirs = {{1,0},{-1,0},{0,1},{0,-1}};
            for (int[] d : dirs) {
                int nr = r + d[0], nc = c + d[1];
                while (nr >= 0 && nr < rows && nc >= 0 && nc < cols) {
                    if (board[nr][nc].isWall()) break;
                    if (board[nr][nc].bulb && (nr != r || nc != c)) {
                        return true; // Bulb sees another bulb
                    }
                    nr += d[0];
                    nc += d[1];
                }
            }
        }
        
        // Numbered cells
        if (cell.type == CommonCell.CellType.NUMBER) {
            int[][] dirs = {{1,0},{-1,0},{0,1},{0,-1}};
            int count = 0;
            for (int[] d : dirs) {
                int nr = r + d[0], nc = c + d[1];
                if (nr >= 0 && nr < rows && nc >= 0 && nc < cols && board[nr][nc].bulb) {
                    count++;
                }
            }
            if (count != cell.number) {
                return true; // Wrong number of adjacent bulbs
            }
        }
        
        return false;
    }

    protected boolean tryFinishAndPopup() {
        return tryFinishAndPopup(null);
    }

    protected boolean tryFinishAndPopup(String solverAlgoName) {
        String result = validateSolution(board, rows, cols);
        if (result.equals("Puzzle solved! ✅")) {
            onSolvedCheckTeamWin(solverAlgoName);
            return true;
        }
        return false;
    }

    protected void onSolvedCheckTeamWin() {
        onSolvedCheckTeamWin(null);
    }

    protected void onSolvedCheckTeamWin(String algoName) {
        statusLabel.setText("Puzzle solved");
        gridPanel.repaint();

        String message;
        boolean teamWin = userContributed && aiContributed;
        boolean solverUsed = (algoName != null && algoName.startsWith("SOLVER:"));
        String displayAlgo = solverUsed ? algoName.substring(7) : algoName;

        if (solverUsed) {
            // Solved entirely by the Solve button
            message = "Puzzle solved using " + displayAlgo + "! ✅";
        } else if (teamWin && displayAlgo != null) {
            // Both user and AI contributed during normal play
            message = "Team effort! Both you and the AI (" + displayAlgo + ") contributed to solving the puzzle. ✅";
        } else if (teamWin) {
            message = "Team effort! Both you and the AI contributed to solving the puzzle. ✅";
        } else if (!userContributed && displayAlgo != null) {
            // AI solved it alone during normal play
            message = "AI (" + displayAlgo + ") solved the puzzle! ✅";
        } else if (!aiContributed) {
            // User solved it alone
            message = "Congratulations! You solved the puzzle! ✅";
        } else {
            message = "Congratulations! Puzzle solved! ✅";
        }

        JOptionPane.showMessageDialog(this, message, "Puzzle Complete",
                JOptionPane.INFORMATION_MESSAGE);
    }

    protected boolean canPlaceBulb(CommonCell[][] board, int rows, int cols, int r, int c) {
        return canPlaceBulbSimple(board, rows, cols, r, c);
    }

    protected static boolean canPlaceBulbSimple(CommonCell[][] board, int rows, int cols, 
                                               int r, int c) {
        if (board[r][c].type != CommonCell.CellType.BLANK) return false;
        if (board[r][c].bulb || board[r][c].dot) return false;
        
        int[][] dirs = {{1,0},{-1,0},{0,1},{0,-1}};
        
        for (int[] d : dirs) {
            int nr = r + d[0], nc = c + d[1];
            while (nr >= 0 && nr < rows && nc >= 0 && nc < cols) {
                if (board[nr][nc].isWall()) break;
                if (board[nr][nc].bulb) return false;
                nr += d[0];
                nc += d[1];
            }
        }
        
        return true;
    }

    // ===== GRID PAINTING =====
    
    protected void paintGrid(Graphics g) {
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, 
                            RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, 
                            RenderingHints.VALUE_RENDER_QUALITY);

        int w = gridPanel.getWidth(), h = gridPanel.getHeight();
        int cellSize = Math.min(w/cols, h/rows);
        int offsetX = (w - cellSize*cols)/2, offsetY = (h - cellSize*rows)/2;

        g2.setColor(GRID_BG);
        g2.fillRect(offsetX, offsetY, cellSize*cols, cellSize*rows);

        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                int x = offsetX + c*cellSize, y = offsetY + r*cellSize;
                CommonCell cell = board[r][c];
                
                // Check if this cell is in violation
                boolean inViolation = isCellInViolation(r, c);

                // Draw cell background
                if (cell.type == CommonCell.CellType.BLACK || 
                    cell.type == CommonCell.CellType.NUMBER) {
                    g2.setColor(WALL_COLOR);
                    g2.fillRect(x+1, y+1, cellSize-2, cellSize-2);
                    
                    if (cell.type == CommonCell.CellType.NUMBER) {
                        g2.setFont(new Font("Segoe UI", Font.BOLD, cellSize/2));
                        String s = Integer.toString(cell.number);
                        FontMetrics fm = g2.getFontMetrics();
                        
                        // If numbered cell is in violation, draw number in red
                        if (inViolation) {
                            g2.setColor(Color.RED);
                        } else {
                            g2.setColor(NUMBER_COLOR);
                        }
                        g2.drawString(s, x+(cellSize-fm.stringWidth(s))/2, 
                                     y+(cellSize+fm.getAscent())/2-4);
                    }
                } else {
                    // Draw blank cell with appropriate color
                    if (inViolation && cell.bulb) {
                        // Wrong bulb placement - show red background
                        g2.setColor(WRONG_CELL_COLOR);
                    } else if (cell.lit) {
                        g2.setColor(LIT_COLOR);
                    } else {
                        g2.setColor(GRID_BG);
                    }
                    g2.fillRect(x+1, y+1, cellSize-2, cellSize-2);
                }

                // Draw bulb
                if (cell.bulb) {
                    int bs = cellSize*3/5, bx = x+(cellSize-bs)/2, by = y+(cellSize-bs)/2;
                    
                    // If bulb is in violation, make the glow red
                    if (inViolation) {
                        g2.setColor(new Color(255, 100, 100, 80));
                    } else {
                        g2.setColor(BULB_GLOW);
                    }
                    g2.fillOval(bx-3, by-3, bs+6, bs+6);
                    
                    GradientPaint grad = new GradientPaint(bx, by, BULB_COLOR.brighter(), 
                                                           bx+bs, by+bs, BULB_COLOR);
                    g2.setPaint(grad);
                    g2.fillOval(bx, by, bs, bs);
                    
                    // If bulb is in violation, draw red outline
                    if (inViolation) {
                        g2.setColor(Color.RED);
                    } else {
                        g2.setColor(BULB_COLOR.darker());
                    }
                    g2.setStroke(new BasicStroke(1.5f));
                    g2.drawOval(bx, by, bs, bs);
                    
                    if (r == lastComputerR && c == lastComputerC) {
                        g2.setColor(AI_HIGHLIGHT);
                        g2.setStroke(new BasicStroke(2));
                        g2.drawRect(x+1, y+1, cellSize-3, cellSize-3);
                    }
                } else if (cell.dot) {
                    int ds = Math.max(5, cellSize/7), dx = x+(cellSize-ds)/2, 
                        dy = y+(cellSize-ds)/2;
                    g2.setColor(DOT_COLOR);
                    g2.fillOval(dx, dy, ds, ds);
                    g2.setColor(DOT_COLOR.darker());
                    g2.setStroke(new BasicStroke(1));
                    g2.drawOval(dx, dy, ds, ds);
                }

                // Draw conflict overlay (for cells that are causing rule violations)
                if (inViolation && !cell.bulb && cell.type != CommonCell.CellType.NUMBER) {
                    g2.setColor(CONFLICT_COLOR);
                    g2.fillRect(x+1, y+1, cellSize-2, cellSize-2);
                }

                // Draw grid lines
                g2.setColor(GRID_LINE);
                g2.setStroke(new BasicStroke(1));
                g2.drawRect(x, y, cellSize, cellSize);
            }
        }
    }

    protected void updateStatusLabel() {
        // To be overridden
    }
}
