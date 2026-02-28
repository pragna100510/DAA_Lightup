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
            
            if (tryFinishAndPopup(algoLabel())) return;

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

        int[] move;

        switch (aiAlgoMode) {
            case GREEDY:
                move = GreedyAIMove.makeMove(board, rows, cols);
                break;
            case BACKTRACKING:
                move = BacktrackingAIMove.makeMove(board, rows, cols);
                break;
            case DC_DP:
            default:
                move = makeDCDPMove();
                break;
        }

        if (move == null || move[0] == -1) {
            statusLabel.setText("AI has no valid moves");
            return;
        }

        int r    = move[0];
        int c    = move[1];
        int type = move[2];

        saveState();
        lastComputerR = r;
        lastComputerC = c;

        if (type == 0) {
            board[r][c].bulb = true;
            board[r][c].dot  = false;
            aiContributed = true;
            statusLabel.setText("AI (" + algoLabel() + ") placed bulb at (" + r + ", " + c + ")");
        } else if (type == 1) {
            board[r][c].bulb = false;
            statusLabel.setText("AI (" + algoLabel() + ") removed bulb at (" + r + ", " + c + ")");
        } else if (type == 2) {
            board[r][c].dot = true;
            statusLabel.setText("AI (" + algoLabel() + ") placed dot at (" + r + ", " + c + ")");
        }

        rebuildDPTables();
        recomputeLighting();
        gridPanel.repaint();
        tryFinishAndPopup(algoLabel());
    }

    /** Returns the display label for the currently active AI algorithm. */
    private String algoLabel() {
        switch (aiAlgoMode) {
            case GREEDY:       return "Greedy";
            case BACKTRACKING: return "Backtracking";
            default:           return "DC+DP";
        }
    }

    /** Original DC+DP move logic, extracted into its own method. */
    private int[] makeDCDPMove() {
        AIMove.DPScorer dpScorer = (r, c) -> {
            GraphBuilding.GraphNode graphNode = null;
            if (cellGraph != null && r >= 0 && r < rows && c >= 0 && c < cols) {
                int graphId = board[r][c].graphId;
                if (graphId >= 0) graphNode = cellGraph.get(graphId);
            }
            DynamicProg.GraphNode dpNode = null;
            if (graphNode != null)
                dpNode = new DynamicProg.GraphNode(graphNode.id, graphNode.row, graphNode.col);

            return DynamicProg.calculateDPScore(
                board, rows, cols, r, c,
                dpLightGain, dpNumberScore, dpFutureBlock,
                dpNode, centralityOrder
            );
        };
        return AIMove.makeAIMove(board, rows, cols, dpScorer);
    }

    // ===== SOLVER =====

    /**
     * Delegates to Solver.solveDivideAndConquer() which owns the full D&C + DP
     * implementation. LightUp only needs to trigger the repaint on success.
     */
    private boolean solveDivideAndConquer() {
        boolean solved = Solver.solveDivideAndConquer(board, rows, cols);
        if (solved) {
            recomputeLighting();
            gridPanel.repaint();
        }
        return solved;
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

                        boolean solved;
                        String algoName;
                        switch (solverAlgoMode) {
                            case GREEDY:
                                solved   = Solver.solveGreedy(board, rows, cols);
                                algoName = "Greedy";
                                break;
                            case BACKTRACKING:
                                solved   = Solver.solveBoard(board, rows, cols);
                                algoName = "Backtracking";
                                break;
                            case DC_DP:
                            default:
                                solved   = solveDivideAndConquer();
                                algoName = "Divide & Conquer + DP";
                                break;
                        }

                        if (solved) {
                            rebuildDPTables();
                            recomputeLighting();
                            statusLabel.setText("Solved with " + algoName + "!");
                            tryFinishAndPopup("SOLVER:" + algoName);
                        } else {
                            statusLabel.setText("No solution found with " + algoName);
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