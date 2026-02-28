import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;
import javax.swing.*;
import javax.swing.Timer;

/**
 * LightUp (Akari) puzzle - Random 7x7 with Smart AI, Undo/Redo, and Team Win popup
 * 
 * Features:
 * - Only these buttons: New Game, Restart Game, Undo Move, Redo Move, Solve Game
 * - Smart AI: uses D&C and DP algorithms
 * - Undo/Redo: complete board-state snapshots
 * - "Team win" popup: when both user and AI contributed
 */
public class LightUpMain extends GameUI {
    
    private Map<Integer, GraphBuilding.GraphNode> cellGraph;
    private List<GraphBuilding.GraphNode> blankNodes;
    private Map<Integer, Integer> centralityOrder;
    
    private int[][] dpLightGain;
    private int[][] dpNumberScore;
    private int[][] dpFutureBlock;
    
    private CommonCell[][] initialBoard;
    private Random rng = new Random();
    
    private boolean[][] perfectSolution;
    private boolean solutionReady = false;

    public LightUpMain() {
        super();
        blankNodes = new ArrayList<>();
        centralityOrder = new HashMap<>();
        
        // Initialize game
        generateRandomPuzzle();
        saveInitialBoard();
        buildGraph();
        buildUI();
        
        setTitle("Light Up - Team Project");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(900, 700));
        pack();
        setLocationRelativeTo(null);
        setVisible(true);
    }

    // ===== GAME INITIALIZATION =====
    
    private void generateRandomPuzzle() {
        System.out.println("Generating new puzzle...");
        board = GraphBuilding.generateRandomPuzzle(rows, cols, rng);
    
        if (board == null) {
            System.out.println("Generation failed, using fallback");
            board = new CommonCell[rows][cols];
            for (int r = 0; r < rows; r++) {
                for (int c = 0; c < cols; c++) {
                    board[r][c] = new CommonCell(CommonCell.CellType.BLANK, -1, r, c);
                }
            }
        }
    
        // Verify the generated puzzle is solvable
        boolean solvable = Solver.isSolvable(board, rows, cols);
        System.out.println("Generated puzzle solvable: " + solvable);
    
        // Reset game state
        userTurn = true;
        userContributed = false;
        aiContributed = false;
        lastComputerR = -1;
        lastComputerC = -1;
        solutionReady = false;
        perfectSolution = null;
    
        undoStack.clear();
        redoStack.clear();
        pushUndoState();
    }
    
    private void saveInitialBoard() {
        initialBoard = new CommonCell[rows][cols];
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                initialBoard[r][c] = new CommonCell(board[r][c]);
            }
        }
    }

    private void restoreInitialBoard() {
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                board[r][c] = new CommonCell(initialBoard[r][c]);
            }
        }
        
        recomputeLighting();
        
        userTurn = true;
        userContributed = false;
        aiContributed = false;
        lastComputerR = -1;
        lastComputerC = -1;
        solutionReady = false;
        perfectSolution = null;
        
        undoStack.clear();
        redoStack.clear();
        pushUndoState();
    }

    // ===== GRAPH BUILDING =====
    
    private void buildGraph() {
        cellGraph = GraphBuilding.buildGraph(board, rows, cols, blankNodes);
        GraphBuilding.calculateCentrality(blankNodes);
        GraphBuilding.mergeSortNodesByCentrality(blankNodes, centralityOrder);
    }

    // ===== DP TABLE BUILDING =====
    
    private void rebuildDPTables() {
        recomputeLighting(board, rows, cols);
        dpLightGain = DynamicProg.buildDPLightGain(board, rows, cols);
        dpNumberScore = DynamicProg.buildDPNumberScore(board, rows, cols);
        dpFutureBlock = DynamicProg.buildDPFutureBlock(board, rows, cols);
    }

    // ===== USER INPUT HANDLING =====
    
    @Override
    protected void handleMouseClick(MouseEvent e) {
        if (!userTurn) return;
        
        int cellSize = Math.min(gridPanel.getWidth()/cols, gridPanel.getHeight()/rows);
        int offsetX = (gridPanel.getWidth() - cellSize*cols)/2;
        int offsetY = (gridPanel.getHeight() - cellSize*rows)/2;
        
        int x = e.getX() - offsetX;
        int y = e.getY() - offsetY;
        
        if (x < 0 || y < 0) return;
        
        int c = x / cellSize;
        int r = y / cellSize;
        
        if (r < 0 || r >= rows || c < 0 || c >= cols) return;
        if (board[r][c].type != CommonCell.CellType.BLANK) return;
        
        boolean madeMove = false;
        
        if (SwingUtilities.isLeftMouseButton(e)) {
            // Toggle bulb
            if (board[r][c].bulb) {
                saveState();
                board[r][c].bulb = false;
                madeMove = true;
                statusLabel.setText("Bulb removed at (" + r + ", " + c + ")");
            } else if (canPlaceBulb(board, rows, cols, r, c)) {
                saveState();
                board[r][c].bulb = true;
                board[r][c].dot = false;
                userContributed = true;
                madeMove = true;
                statusLabel.setText("Bulb placed at (" + r + ", " + c + ")");
            }
        } else if (SwingUtilities.isRightMouseButton(e)) {
            // Toggle dot
            saveState();
            board[r][c].dot = !board[r][c].dot;
            if (board[r][c].dot) {
                board[r][c].bulb = false;
                statusLabel.setText("Dot placed at (" + r + ", " + c + ")");
            } else {
                statusLabel.setText("Dot removed at (" + r + ", " + c + ")");
            }
            madeMove = true;
        }
        
        if (madeMove) {
            rebuildDPTables();
            recomputeLighting();
            gridPanel.repaint();
            
            if (tryFinishAndPopup()) return;
            
            // Switch to AI turn
            userTurn = false;
            Timer timer = new Timer(500, evt -> {
                computerMakeOneMove();
                userTurn = true;
                gridPanel.repaint();
            });
            timer.setRepeats(false);
            timer.start();
        }
    }

    // ===== AI MOVE =====
    
    private void computerMakeOneMove() {
        // Save snapshot before move
        BoardState beforeMove = snapshotState();
        
        // Create DP scorer
        AIMove.DPScorer dpScorer = (r, c) -> {
            GraphBuilding.GraphNode graphNode = null;
            if (cellGraph != null && r >= 0 && r < rows && c >= 0 && c < cols) {
                int graphId = board[r][c].graphId;
                if (graphId >= 0) {
                    graphNode = cellGraph.get(graphId);
                }
            }
            
            DynamicProg.GraphNode dpNode = null;
            if (graphNode != null) {
                dpNode = new DynamicProg.GraphNode(graphNode.id, graphNode.row, graphNode.col);
            }
            
            return DynamicProg.calculateDPScore(
                board, rows, cols, r, c,
                dpLightGain, dpNumberScore, dpFutureBlock,
                dpNode, centralityOrder
            );
        };
        
        // Get best move using D&C
        int[] move = AIMove.makeAIMove(board, rows, cols, dpScorer);
        
        if (move == null || move[0] == -1) {
            statusLabel.setText("AI has no valid moves");
            return;
        }
        
        int r = move[0];
        int c = move[1];
        int type = move[2];
        
        saveState();
        lastComputerR = r;
        lastComputerC = c;
        
        if (type == 0) {
            // Place bulb
            board[r][c].bulb = true;
            board[r][c].dot = false;
            aiContributed = true;
            statusLabel.setText("AI placed bulb at (" + r + ", " + c + ")");
        } else if (type == 1) {
            // Remove bulb
            board[r][c].bulb = false;
            statusLabel.setText("AI removed bulb at (" + r + ", " + c + ")");
        } else if (type == 2) {
            // Place dot
            board[r][c].dot = true;
            statusLabel.setText("AI placed dot at (" + r + ", " + c + ")");
        }
        
        rebuildDPTables();
        recomputeLighting();
        gridPanel.repaint();
        tryFinishAndPopup();
    }

    // ===== SOLVER =====
    
    private boolean solveDivideAndConquer() {

    // Deep copy board
    CommonCell[][] copy =
            GraphBuilding.copyBoard(board, rows, cols);

    // Use REAL solver (not isSolvable)
    boolean solved =
            Solver.solveBoard(copy, rows, cols);

    if (!solved) {
        return false;
    }

    // Copy solved configuration back
    for (int r = 0; r < rows; r++) {
        for (int c = 0; c < cols; c++) {
            board[r][c].bulb = copy[r][c].bulb;
            board[r][c].dot = false;
        }
    }

    recomputeLighting();
    gridPanel.repaint();

    return true;
}

    private List<List<int[]>> divideIntoRegions() {
        boolean[][] visited = new boolean[rows][cols];
        List<List<int[]>> regions = new ArrayList<>();

        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                if (!visited[r][c] && board[r][c].type == CommonCell.CellType.BLANK) {
                    List<int[]> region = new ArrayList<>();
                    dfsRegion(r, c, visited, region);
                    regions.add(region);
                }
            }
        }

        return regions;
    }

    private void dfsRegion(int r, int c, boolean[][] visited, List<int[]> region) {
        if (r < 0 || r >= rows || c < 0 || c >= cols) return;
        if (visited[r][c]) return;
        if (board[r][c].type != CommonCell.CellType.BLANK) return;

        visited[r][c] = true;
        region.add(new int[]{r, c});

        dfsRegion(r + 1, c, visited, region);
        dfsRegion(r - 1, c, visited, region);
        dfsRegion(r, c + 1, visited, region);
        dfsRegion(r, c - 1, visited, region);
    }

    private void mergeSortRegions(List<List<int[]>> regions) {
        if (regions.size() <= 1) return;

        int mid = regions.size() / 2;
        List<List<int[]>> left = new ArrayList<>(regions.subList(0, mid));
        List<List<int[]>> right = new ArrayList<>(regions.subList(mid, regions.size()));

        mergeSortRegions(left);
        mergeSortRegions(right);
        mergeRegions(regions, left, right);
    }

    private void mergeRegions(List<List<int[]>> result,
                              List<List<int[]>> left,
                              List<List<int[]>> right) {
        int i = 0, j = 0, k = 0;
        while (i < left.size() && j < right.size()) {
            if (left.get(i).size() <= right.get(j).size()) {
                result.set(k++, left.get(i++));
            } else {
                result.set(k++, right.get(j++));
            }
        }
        while (i < left.size()) result.set(k++, left.get(i++));
        while (j < right.size()) result.set(k++, right.get(j++));
    }

    private boolean solveRegionDP(List<int[]> region) {
        Map<String, Boolean> memo = new HashMap<>();
        return backtrackRegion(region, 0, memo);
    }

    private boolean backtrackRegion(List<int[]> region,
                                    int index,
                                    Map<String, Boolean> memo) {
        if (index == region.size()) {
            recomputeLighting();
            return isRegionValid(region);
        }

        String key = buildStateKey(region, index);
        if (memo.containsKey(key)) return memo.get(key);

        int r = region.get(index)[0];
        int c = region.get(index)[1];

        // Option 1: no bulb
        board[r][c].bulb = false;
        if (backtrackRegion(region, index + 1, memo)) {
            memo.put(key, true);
            return true;
        }

        // Option 2: place bulb if allowed
        if (canPlaceBulb(board, rows, cols, r, c)) {
            board[r][c].bulb = true;
            if (backtrackRegion(region, index + 1, memo)) {
                memo.put(key, true);
                return true;
            }
            board[r][c].bulb = false;
        }

        memo.put(key, false);
        return false;
    }

    private String buildStateKey(List<int[]> region, int index) {
        StringBuilder sb = new StringBuilder();
        sb.append(index).append("|");

        for (int[] cell : region) {
            sb.append(board[cell[0]][cell[1]].bulb ? "1" : "0");
        }

        return sb.toString();
    }

    private boolean isRegionValid(List<int[]> region) {
        for (int[] p : region) {
            int r = p[0], c = p[1];

            if (!board[r][c].lit) return false;
            if (board[r][c].bulb && AIMove.isViolatingBulb(board, rows, cols, r, c))
                return false;
        }
        return true;
    }

    private boolean applyAllSimpleDeductions() {
        boolean changed = false;
        changed |= completeNumberCells();
        changed |= forceSingleLightSources();
        changed |= forbidConflictingPlacements();
        return changed;
    }

    private boolean completeNumberCells() {
        boolean changed = false;
        int[][] dirs = {{0,1},{1,0},{0,-1},{-1,0}};

        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                if (board[r][c].type != CommonCell.CellType.NUMBER) continue;

                int need = board[r][c].number;
                List<int[]> candidates = new ArrayList<>();
                int already = 0;

                for (int[] d : dirs) {
                    int nr = r + d[0], nc = c + d[1];
                    if (nr < 0 || nr >= rows || nc < 0 || nc >= cols) continue;
                    if (board[nr][nc].type != CommonCell.CellType.BLANK) continue;

                    if (board[nr][nc].bulb) already++;
                    else if (!board[nr][nc].dot) candidates.add(new int[]{nr, nc});
                }

                // Case 1: exactly the remaining spots needed → fill them all
                if (candidates.size() == need - already && need - already > 0) {
                    for (int[] pos : candidates) {
                        int pr = pos[0], pc = pos[1];
                        if (!board[pr][pc].bulb) {
                            board[pr][pc].bulb = true;
                            changed = true;
                        }
                    }
                }

                // Case 2: already satisfied → forbid remaining
                if (already == need) {
                    for (int[] pos : candidates) {
                        int pr = pos[0], pc = pos[1];
                        if (!board[pr][pc].dot) {
                            board[pr][pc].dot = true;
                            changed = true;
                        }
                    }
                }
            }
        }
        if (changed) recomputeLighting();
        return changed;
    }

    private boolean forceSingleLightSources() {
        boolean changed = false;

        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                if (board[r][c].type != CommonCell.CellType.BLANK || board[r][c].lit || board[r][c].dot) continue;

                List<int[]> possibleBulbPositions = findPossibleBulbPositionsThatLightThisCell(r, c);

                if (possibleBulbPositions.size() == 1) {
                    int[] pos = possibleBulbPositions.get(0);
                    int br = pos[0], bc = pos[1];
                    if (canPlaceBulb(board, rows, cols, br, bc) && !board[br][bc].bulb) {
                        board[br][bc].bulb = true;
                        changed = true;
                    }
                }
            }
        }
        if (changed) recomputeLighting();
        return changed;
    }

    private List<int[]> findPossibleBulbPositionsThatLightThisCell(int tr, int tc) {
        List<int[]> possibles = new ArrayList<>();
        int[][] dirs = {{0,1},{1,0},{0,-1},{-1,0}};

        for (int[] d : dirs) {
            int r = tr + d[0], c = tc + d[1];
            while (r >= 0 && r < rows && c >= 0 && c < cols && !board[r][c].isWall()) {
                if (board[r][c].type == CommonCell.CellType.BLANK && canPlaceBulb(board, rows, cols, r, c) && !board[r][c].dot) {
                    possibles.add(new int[]{r, c});
                }
                r += d[0]; c += d[1];
            }
        }
        return possibles;
    }

    private boolean forbidConflictingPlacements() {
        boolean changed = false;
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                if (board[r][c].type != CommonCell.CellType.BLANK || board[r][c].dot || board[r][c].bulb) continue;
                if (!canPlaceBulb(board, rows, cols, r, c)) {
                    board[r][c].dot = true;
                    changed = true;
                }
            }
        }
        return changed;
    }

    private void storePerfectSolution() {
        perfectSolution = new boolean[rows][cols];
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                perfectSolution[r][c] = board[r][c].bulb;
            }
        }
        solutionReady = true;
    }

    private boolean checkStillSolvable() {
        boolean[][] savedBulbs = new boolean[rows][cols];
        boolean[][] savedDots = new boolean[rows][cols];

        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                savedBulbs[r][c] = board[r][c].bulb;
                savedDots[r][c] = board[r][c].dot;
            }
        }

        boolean solvable = solveDivideAndConquer();

        // Restore board
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                board[r][c].bulb = savedBulbs[r][c];
                board[r][c].dot = savedDots[r][c];
            }
        }

        recomputeLighting();
        return solvable;
    }

    // ===== BUTTON HANDLERS =====
    
    @Override
    protected JPanel createControlPanel() {
        JPanel panel = super.createControlPanel();
        
        Component[] components = panel.getComponents();
        for (Component comp : components) {
            if (comp instanceof JButton) {
                JButton btn = (JButton) comp;
                String text = btn.getText();
                
                if (text.equals("New Game")) {
                    btn.addActionListener(e -> {
                        generateRandomPuzzle();
                        saveInitialBoard();
                        buildGraph();
                        rebuildDPTables();
                        statusLabel.setText("New game generated");
                        gridPanel.repaint();
                    });
                } else if (text.equals("Restart")) {
                    btn.addActionListener(e -> {
                        restoreInitialBoard();
                        buildGraph();
                        rebuildDPTables();
                        statusLabel.setText("Game restarted");
                        gridPanel.repaint();
                    });
                } else if (text.equals("Solve")) {
                    btn.addActionListener(e -> {
                        pushUndoState();
                        redoStack.clear();
                        
                        boolean solved = solveDivideAndConquer();
                        
                        if (solved) {
                            rebuildDPTables();
                            recomputeLighting();
                            statusLabel.setText("Solved successfully!");
                            tryFinishAndPopup();
                        } else {
                            statusLabel.setText("No solution found");
                        }
                        
                        gridPanel.repaint();
                    });
                }
            }
        }
        
        return panel;
    }

    // ===== MAIN =====
    
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception e) {
                e.printStackTrace();
            }
            new LightUpMain();
        });
    }
}
