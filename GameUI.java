import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;
import javax.swing.*;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;

/**
 * Responsibilities:
 * - Build and manage user interface
 * - Handle user input (mouse clicks, button actions)
 * - Undo/Redo state management
 * - Lighting computation
 * - Puzzle validation
 * - Greedy solver algorithm
 * - Victory detection and team win popup
 */
public class GameUI extends JFrame {
    
    // Minimal Cell definition for standalone compilation
    static class Cell {
        enum CellType { BLACK, NUMBER, BLANK }
        CellType type;
        int number;
        boolean bulb;
        boolean dot;
        boolean lit;
        int row, col;
        int graphId;
        
        Cell(CellType t, int num, int r, int c) {
            type = t; number = num; row = r; col = c;
            bulb = false; dot = false; lit = false; graphId = -1;
        }
        
        boolean isWall() { return type == CellType.BLACK || type == CellType.NUMBER; }
    }

    // ===== STATE MANAGEMENT =====
    
    static class BoardState {
        boolean[][] bulbs;
        boolean[][] dots;
        boolean userTurn;
        boolean userContributed;
        boolean aiContributed;
        int lastComputerR;
        int lastComputerC;

        BoardState(int rows, int cols) {
            bulbs = new boolean[rows][cols];
            dots = new boolean[rows][cols];
        }
    }

    private Cell[][] board;
    private int rows = 7, cols = 7;
    private JPanel gridPanel;
    private JLabel statusLabel;
    private boolean userTurn = true;
    private int lastComputerR = -1, lastComputerC = -1;
    private boolean userContributed = false;
    private boolean aiContributed = false;

    private Deque<BoardState> undoStack = new ArrayDeque<>();
    private Deque<BoardState> redoStack = new ArrayDeque<>();

    // ===== COLOR SCHEME =====
    private final Color BG_COLOR        = new Color(245, 247, 250);
    private final Color GRID_BG         = new Color(255, 255, 255);
    private final Color WALL_COLOR      = new Color(30,  40,  60);
    private final Color NUMBER_COLOR    = new Color(240, 245, 255);
    private final Color LIT_COLOR       = new Color(255, 253, 200);
    private final Color BULB_COLOR      = new Color(255, 225, 50);
    private final Color BULB_GLOW       = new Color(255, 255, 200, 80);
    private final Color DOT_COLOR       = new Color(180, 180, 180);
    private final Color CONFLICT_COLOR  = new Color(255, 100, 100, 90);
    private final Color AI_HIGHLIGHT    = new Color(80,  200, 120, 100);
    private final Color GRID_LINE       = new Color(210, 215, 225);
    private final Color PANEL_BG        = new Color(250, 252, 255);

    // ===== UI BUILDING =====
    
    /**
     * Builds the complete user interface
     */
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
        new Timer(500, e -> {
            if (userTurn) {
                turnIndicator.setText("YOUR TURN");
                turnIndicator.setForeground(new Color(80,150,80));
            } else {
                turnIndicator.setText("AI THINKING");
                turnIndicator.setForeground(new Color(200,100,100));
            }
        }).start();
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
    }

    private JPanel createControlPanel() {
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

        // Button listeners would connect to game logic
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

        JPanel counterPanel = new JPanel();
        counterPanel.setBackground(PANEL_BG);
        counterPanel.setBorder(new EmptyBorder(15,0,0,0));
        JLabel moveCounter = new JLabel("Moves: 0");
        moveCounter.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        moveCounter.setForeground(new Color(120,130,150));
        counterPanel.add(moveCounter);
        controlPanel.add(Box.createVerticalGlue());
        controlPanel.add(counterPanel);

        new Timer(100, e -> moveCounter.setText("Moves: " + (undoStack.size()-1))).start();

        return controlPanel;
    }

    private JPanel createStatusPanel() {
        JPanel statusPanel = new JPanel(new BorderLayout());
        statusPanel.setBackground(PANEL_BG);
        statusPanel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1,0,0,0,new Color(220,225,230)),
            new EmptyBorder(8,20,8,20)));
        
        statusLabel = new JLabel("Click cells to place bulbs");
        statusLabel.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        statusLabel.setForeground(new Color(80,90,110));
        statusPanel.add(statusLabel, BorderLayout.WEST);
        
        JLabel modeLabel = new JLabel("7×7 Puzzle  |  D&C + DP AI");
        modeLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        modeLabel.setForeground(new Color(140,150,170));
        statusPanel.add(modeLabel, BorderLayout.EAST);
        
        return statusPanel;
    }

    private JButton createMinimalButton(String text, Color baseColor) {
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
    
    private void handleMouseClick(MouseEvent e) {
        int w = gridPanel.getWidth(), h = gridPanel.getHeight();
        int cellSize = Math.min(w/cols, h/rows);
        int offsetX = (w - cellSize*cols)/2, offsetY = (h - cellSize*rows)/2;
        int x = e.getX()-offsetX, y = e.getY()-offsetY;
        
        if (x < 0 || y < 0) return;
        
        int col = x/cellSize, row = y/cellSize;
        if (row < 0 || row >= rows || col < 0 || col >= cols) return;
        
        if (SwingUtilities.isLeftMouseButton(e)) {
            handleUserToggleBulb(row, col);
        } else if (SwingUtilities.isRightMouseButton(e)) {
            handleUserToggleDot(row, col);
        }
    }

    private void handleUserToggleBulb(int r, int c) {
        Cell cell = board[r][c];
        if (cell.type != Cell.CellType.BLANK) return;
        
        pushUndoState();
        redoStack.clear();
        
        if (cell.bulb) {
            cell.bulb = false;
            recomputeLighting();
            userContributed = true;
            statusLabel.setText("Bulb removed at ("+r+","+c+")");
        } else {
            cell.bulb = true;
            cell.dot = false;
            recomputeLighting();
            userContributed = true;
            statusLabel.setText("Bulb placed at ("+r+","+c+")");
        }
        
        gridPanel.repaint();
        
        if (tryFinishAndPopup()) return;
        
        // AI move would be triggered here
    }

    private void handleUserToggleDot(int r, int c) {
        Cell cell = board[r][c];
        if (cell.type != Cell.CellType.BLANK) return;
        
        pushUndoState();
        redoStack.clear();
        
        cell.dot = !cell.dot;
        if (cell.dot) cell.bulb = false;
        
        recomputeLighting();
        userContributed = true;
        statusLabel.setText((cell.dot?"Marked":"Unmarked")+" dot at ("+r+","+c+")");
        gridPanel.repaint();
    }

    // ===== UNDO/REDO =====
    
    private void handleUndo() {
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

    private void handleRedo() {
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

    private BoardState snapshotState() {
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

    private void applyState(BoardState st) {
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

    private void pushUndoState() {
        undoStack.push(snapshotState());
    }

    // ===== LIGHTING COMPUTATION =====
    
    /**
     * Recomputes which cells are lit based on current bulb placements
     */
    public static void recomputeLighting(Cell[][] board, int rows, int cols) {
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

    private void recomputeLighting() {
        recomputeLighting(board, rows, cols);
    }

    // ===== VALIDATION =====
    
    /**
     * Validates the current puzzle solution
     * @return Status message describing validation result
     */
    public static String validateSolution(Cell[][] board, int rows, int cols) {
        // Check all cells are lit
        for (int r = 0; r < rows; r++)
            for (int c = 0; c < cols; c++)
                if (board[r][c].type == Cell.CellType.BLANK && !board[r][c].lit)
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
                if (board[r][c].type != Cell.CellType.NUMBER) continue;
                
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

    private boolean tryFinishAndPopup() {
        String result = validateSolution(board, rows, cols);
        if (result.equals("Puzzle solved! ✅")) {
            onSolvedCheckTeamWin();
            return true;
        }
        return false;
    }

    private void onSolvedCheckTeamWin() {
        statusLabel.setText("Puzzle solved");
        gridPanel.repaint();
        
        boolean teamWin = userContributed && aiContributed;
        JOptionPane.showMessageDialog(this,
            teamWin
              ? "Team effort! Both you and the AI (D&C + DP) contributed to solving the puzzle."
              : "Congratulations! Puzzle solved!",
            "Puzzle Complete", JOptionPane.INFORMATION_MESSAGE);
    }

    // ===== GREEDY SOLVER =====
    
    /**
     * Greedy solver algorithm for the "Solve" button
     * Also used to check puzzle solvability during generation
     */
    public static boolean solveGreedy(Cell[][] board, int rows, int cols) {
        // Clear all bulbs and dots
        for (int r = 0; r < rows; r++)
            for (int c = 0; c < cols; c++) {
                board[r][c].bulb = false;
                board[r][c].dot = false;
            }
        
        recomputeLighting(board, rows, cols);
        
        boolean progress = true;
        int[][] dirs = {{1,0},{-1,0},{0,1},{0,-1}};
        
        while (progress) {
            progress = false;
            
            // Strategy 1: Fill forced numbered cells
            for (int r = 0; r < rows; r++) {
                for (int c = 0; c < cols; c++) {
                    if (board[r][c].type != Cell.CellType.NUMBER) continue;
                    
                    int need = board[r][c].number;
                    int placed = 0;
                    List<int[]> blanks = new ArrayList<>();
                    
                    for (int[] d : dirs) {
                        int nr = r + d[0], nc = c + d[1];
                        if (nr < 0 || nr >= rows || nc < 0 || nc >= cols) continue;
                        
                        if (board[nr][nc].bulb) {
                            placed++;
                        } else if (board[nr][nc].type == Cell.CellType.BLANK && 
                                   !board[nr][nc].dot) {
                            blanks.add(new int[]{nr, nc});
                        }
                    }
                    
                    // If remaining spots exactly match remaining need, fill them
                    if (placed + blanks.size() == need) {
                        for (int[] p : blanks) {
                            board[p[0]][p[1]].bulb = true;
                            progress = true;
                        }
                    }
                }
            }
            
            recomputeLighting(board, rows, cols);
            
            // Strategy 2: Place bulbs in unlit cells that are safe
            for (int r = 0; r < rows; r++) {
                for (int c = 0; c < cols; c++) {
                    if (board[r][c].type == Cell.CellType.BLANK && 
                        !board[r][c].lit && 
                        canPlaceBulbSimple(board, rows, cols, r, c)) {
                        board[r][c].bulb = true;
                        recomputeLighting(board, rows, cols);
                        progress = true;
                    }
                }
            }
        }
        
        return validateSolution(board, rows, cols).equals("Puzzle solved! ✅");
    }

    private static boolean canPlaceBulbSimple(Cell[][] board, int rows, int cols, 
                                               int r, int c) {
        if (board[r][c].type != Cell.CellType.BLANK) return false;
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
    
    private void paintGrid(Graphics g) {
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
                Cell cell = board[r][c];

                // Draw cell background
                if (cell.type == Cell.CellType.BLACK || 
                    cell.type == Cell.CellType.NUMBER) {
                    g2.setColor(WALL_COLOR);
                    g2.fillRect(x+1, y+1, cellSize-2, cellSize-2);
                    
                    if (cell.type == Cell.CellType.NUMBER) {
                        g2.setFont(new Font("Segoe UI", Font.BOLD, cellSize/2));
                        String s = Integer.toString(cell.number);
                        FontMetrics fm = g2.getFontMetrics();
                        g2.setColor(NUMBER_COLOR);
                        g2.drawString(s, x+(cellSize-fm.stringWidth(s))/2, 
                                     y+(cellSize+fm.getAscent())/2-4);
                    }
                } else {
                    g2.setColor(cell.lit ? LIT_COLOR : GRID_BG);
                    g2.fillRect(x+1, y+1, cellSize-2, cellSize-2);
                }

                // Draw bulb
                if (cell.bulb) {
                    int bs = cellSize*3/5, bx = x+(cellSize-bs)/2, by = y+(cellSize-bs)/2;
                    g2.setColor(BULB_GLOW);
                    g2.fillOval(bx-3, by-3, bs+6, bs+6);
                    GradientPaint grad = new GradientPaint(bx, by, BULB_COLOR.brighter(), 
                                                           bx+bs, by+bs, BULB_COLOR);
                    g2.setPaint(grad);
                    g2.fillOval(bx, by, bs, bs);
                    g2.setColor(BULB_COLOR.darker());
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

                // Draw grid lines
                g2.setColor(GRID_LINE);
                g2.setStroke(new BasicStroke(1));
                g2.drawRect(x, y, cellSize, cellSize);
            }
        }
    }
}
