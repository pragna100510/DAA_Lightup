import java.awt.*;
import java.util.*;
import java.util.List;
import javax.swing.*;


public class LightUpMain {
    
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeel());
            } catch (Exception e) {
                e.printStackTrace();
            }
            
            // Create and initialize the game
            LightUpGame game = new LightUpGame();
            game.setVisible(true);
        });
    }
    
    /**
     * Main game class that integrates all member contributions
     */
    static class LightUpGame extends GameUI {
        
        private Map<Integer, GraphBuilding.GraphNode> cellGraph;
        private List<GraphBuilding.GraphNode> blankNodes;
        private Map<Integer, Integer> centralityOrder;
        
        private int[][] dpLightGain;
        private int[][] dpNumberScore;
        private int[][] dpFutureBlock;
        
        private Random rng;
        
        public LightUpGame() {
            super();
            rng = new Random();
            blankNodes = new ArrayList<>();
            centralityOrder = new HashMap<>();
            
            // Initialize game
            initializeNewGame();
            
            // Build UI 
            buildUI();
            
            setTitle("Light Up - Team Project");
            setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            pack();
            setLocationRelativeTo(null);
        }
        
        /**
         * Initialize a new game using  puzzle generator
         */
        private void initializeNewGame() {
          
            board = GraphBuilding.generateRandomPuzzle(rows, cols, rng);
            cellGraph = GraphBuilding.buildGraph(board, rows, cols, blankNodes);
            
            // Calculate centrality 
            GraphBuilding.calculateCentrality(blankNodes);
            GraphBuilding.mergeSortNodesByCentrality(blankNodes, centralityOrder);
            
            // Build DP tables 
            rebuildDPTables();
            
            // Reset game state
            userTurn = true;
            userContributed = false;
            aiContributed = false;
            lastComputerR = -1;
            lastComputerC = -1;
        }
        
        /**
         * Rebuild all DP tables using  algorithms
         */
        private void rebuildDPTables() {
            recomputeLighting(board, rows, cols);
            dpLightGain = DynamicProg.buildDPLightGain(board, rows, cols);
            dpNumberScore = DynamicProg.buildDPNumberScore(board, rows, cols);
            dpFutureBlock = DynamicProg.buildDPFutureBlock(board, rows, cols);
        }
        
        /**
         * Override mouse click handler to integrate AI move after user move
         */
        @Override
        protected void handleMouseClick(java.awt.event.MouseEvent e) {
            if (!userTurn) return;
            
            int cellSize = Math.min(gridPanel.getWidth()/cols, gridPanel.getHeight()/rows);
            int offsetX = (gridPanel.getWidth() - cellSize*cols)/2;
            int offsetY = (gridPanel.getHeight() - cellSize*rows)/2;
            
            int c = (e.getX() - offsetX) / cellSize;
            int r = (e.getY() - offsetY) / cellSize;
            
            if (r < 0 || r >= rows || c < 0 || c >= cols) return;
            if (board[r][c].type != GraphBuilding.Cell.CellType.BLANK) return;
            
            boolean madeMove = false;
            
            if (javax.swing.SwingUtilities.isLeftMouseButton(e)) {
                // Toggle bulb
                if (board[r][c].bulb) {
                    saveState();
                    board[r][c].bulb = false;
                    madeMove = true;
                } else if (canPlaceBulb(board, rows, cols, r, c)) {
                    saveState();
                    board[r][c].bulb = true;
                    board[r][c].dot = false;
                    userContributed = true;
                    madeMove = true;
                }
            } else if (javax.swing.SwingUtilities.isRightMouseButton(e)) {
                // Toggle dot
                saveState();
                if (board[r][c].bulb) {
                    board[r][c].bulb = false;
                }
                board[r][c].dot = !board[r][c].dot;
                madeMove = true;
            }
            
            if (madeMove) {
                rebuildDPTables();
                gridPanel.repaint();
                updateStatusLabel();
                
                if (tryFinishAndPopup()) return;
                
                // Switch to AI turn
                userTurn = false;
                Timer timer = new Timer(800, evt -> {
                    performAIMove();
                    userTurn = true;
                });
                timer.setRepeats(false);
                timer.start();
            }
        }
        
        private void performAIMove() {
            // Create DP scorer using scoring function
            AIMove.DPScorer dpScorer = (r, c) -> {
                return DynamicProg.calculateDPScore(
                    board, rows, cols, r, c,
                    dpLightGain, dpNumberScore, dpFutureBlock,
                    null, centralityOrder
                );
            };
            
            // Use Divide & Conquer AI to find best move
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
            gridPanel.repaint();
            tryFinishAndPopup();
        }
        
        /**
         * Override to add new game functionality
         */
        @Override
        protected JPanel createControlPanel() {
            JPanel panel = super.createControlPanel();
            
            // Add functionality to existing buttons
            Component[] components = panel.getComponents();
            for (Component comp : components) {
                if (comp instanceof JButton) {
                    JButton btn = (JButton) comp;
                    String text = btn.getText();
                    
                    if (text.equals("New Game")) {
                        btn.addActionListener(e -> {
                            initializeNewGame();
                            gridPanel.repaint();
                            statusLabel.setText("New game started!");
                        });
                    } else if (text.equals("Restart")) {
                        btn.addActionListener(e -> {
                            // Clear all bulbs and dots
                            for (int r = 0; r < rows; r++) {
                                for (int c = 0; c < cols; c++) {
                                    board[r][c].bulb = false;
                                    board[r][c].dot = false;
                                }
                            }
                            userContributed = false;
                            aiContributed = false;
                            lastComputerR = -1;
                            lastComputerC = -1;
                            rebuildDPTables();
                            gridPanel.repaint();
                            statusLabel.setText("Game restarted");
                        });
                    } else if (text.equals("Solve")) {
                        btn.addActionListener(e -> {
                            if (GameUI.solveGreedy(board, rows, cols)) {
                                rebuildDPTables();
                                gridPanel.repaint();
                                statusLabel.setText("Puzzle solved by greedy algorithm!");
                                tryFinishAndPopup();
                            } else {
                                statusLabel.setText("Could not solve puzzle automatically");
                            }
                        });
                    }
                }
            }
            
            return panel;
        }
    }
}
