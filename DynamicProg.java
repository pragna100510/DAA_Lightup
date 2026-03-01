import java.util.*;

/**
 * Responsibilities:
 * - Build DP tables for AI evaluation
 * - dpLightGain: Calculate lighting coverage
 * - dpNumberScore: Evaluate numbered cell satisfaction
 * - dpFutureBlock: Predict blocking effects
 * - Combined DP scoring function
 */
public class DynamicProg {

    // ===== MEMOIZATION CACHES =====
    // Key: compact board-state string (bulb/dot/lit per cell)
    // Value: the previously computed DP table for that state
    private static final Map<String, int[][]> memoLightGain  = new HashMap<>();
    private static final Map<String, int[][]> memoNumberScore = new HashMap<>();
    private static final Map<String, int[][]> memoFutureBlock = new HashMap<>();

    /** Encodes the mutable board state (bulb/dot/lit) into a compact string key. */
    private static String boardStateKey(CommonCell[][] board, int rows, int cols) {
        char[] buf = new char[rows * cols];
        for (int r = 0; r < rows; r++)
            for (int c = 0; c < cols; c++) {
                int bits = (board[r][c].bulb ? 4 : 0)
                         | (board[r][c].dot  ? 2 : 0)
                         | (board[r][c].lit  ? 1 : 0);
                buf[r * cols + c] = (char)('0' + bits);
            }
        return new String(buf);
    }

    /** Clears all DP caches — call before starting a fresh solve. */
    public static void clearMemo() {
        memoLightGain.clear();
        memoNumberScore.clear();
        memoFutureBlock.clear();
    }

    public static class GraphNode {
        public int id;
        public int row, col;
        public GraphNode(int id, int r, int c) { 
            this.id = id; 
            this.row = r; 
            this.col = c; 
        }
    }

    // ===== DP TABLE 1: LIGHT GAIN =====
    
    /**
     * Builds dpLightGain[r][c]: number of currently-unlit cells that a bulb
     * placed at (r,c) would illuminate.
     */
    public static int[][] buildDPLightGain(CommonCell[][] board, int rows, int cols) {
        String key = boardStateKey(board, rows, cols);
        if (memoLightGain.containsKey(key)) return memoLightGain.get(key);

        int[][] dpLightGain = new int[rows][cols];

        // Horizontal contribution
        for (int r = 0; r < rows; r++) {
            int c = 0;
            while (c < cols) {
                if (board[r][c].isWall()) {
                    c++;
                    continue;
                }
                
                // Find extent of this blank run
                int start = c;
                while (c < cols && !board[r][c].isWall()) c++;
                int end = c; // exclusive
                
                // Count unlit cells in [start, end)
                int unlitInRun = 0;
                for (int cc = start; cc < end; cc++)
                    if (!board[r][cc].lit) unlitInRun++;
                
                // Every candidate in the run gets the horizontal unlit count
                for (int cc = start; cc < end; cc++)
                    dpLightGain[r][cc] += unlitInRun;
            }
        }

        // Vertical contribution
        for (int c = 0; c < cols; c++) {
            int r = 0;
            while (r < rows) {
                if (board[r][c].isWall()) {
                    r++;
                    continue;
                }
                
                int start = r;
                while (r < rows && !board[r][c].isWall()) r++;
                int end = r;
                
                int unlitInRun = 0;
                for (int rr = start; rr < end; rr++)
                    if (!board[rr][c].lit) unlitInRun++;
                
                for (int rr = start; rr < end; rr++)
                    dpLightGain[rr][c] += unlitInRun;
            }
        }

        // The cell itself was counted twice (once in row run, once in col run).
        // Correct: subtract 1 if the cell is unlit (counted in both directions).
        for (int r = 0; r < rows; r++)
            for (int c = 0; c < cols; c++)
                if (board[r][c].type == CommonCell.CellType.BLANK && !board[r][c].lit)
                    dpLightGain[r][c]--;   // remove the double-count
                    
        memoLightGain.put(key, dpLightGain);
        return dpLightGain;
    }

    // ===== DP TABLE 2: NUMBER SCORE =====
    
    /**
     * Builds dpNumberScore[r][c]: how much placing a bulb at (r,c) helps
     * satisfy adjacent numbered cells.
     */
    public static int[][] buildDPNumberScore(CommonCell[][] board, int rows, int cols) {
        String key = boardStateKey(board, rows, cols);
        if (memoNumberScore.containsKey(key)) return memoNumberScore.get(key);

        int[][] dpNumberScore = new int[rows][cols];
        int[][] dirs = {{1,0},{-1,0},{0,1},{0,-1}};

        // For each numbered cell, compute deficit and write to neighbours once
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                if (board[r][c].type != CommonCell.CellType.NUMBER) continue;
                
                int target  = board[r][c].number;
                int placed  = 0;
                List<int[]> empties = new ArrayList<>();
                
                for (int[] d : dirs) {
                    int nr = r + d[0], nc = c + d[1];
                    if (nr < 0 || nr >= rows || nc < 0 || nc >= cols) continue;
                    
                    if (board[nr][nc].bulb) {
                        placed++;
                    } else if (board[nr][nc].type == CommonCell.CellType.BLANK && !board[nr][nc].dot) {
                        empties.add(new int[]{nr, nc});
                    }
                }
                
                int deficit = target - placed;
                
                if (deficit > 0) {
                    // Each empty neighbour can help; weight = deficit magnitude
                    for (int[] e : empties)
                        dpNumberScore[e[0]][e[1]] += deficit * 10;
                } else if (deficit < 0) {
                    // Already over-satisfied — penalise adding more
                    for (int[] e : empties)
                        dpNumberScore[e[0]][e[1]] += deficit * 15; // negative
                }
            }
        }
        
        memoNumberScore.put(key, dpNumberScore);
        return dpNumberScore;
    }

    // ===== DP TABLE 3: FUTURE BLOCK =====
    
    /**
     * Builds dpFutureBlock[r][c]: how many currently-placeable blank cells
     * in the same rows/columns would be blocked if we place a bulb at (r,c).
     */
    public static int[][] buildDPFutureBlock(CommonCell[][] board, int rows, int cols) {
        String key = boardStateKey(board, rows, cols);
        if (memoFutureBlock.containsKey(key)) return memoFutureBlock.get(key);

        int[][] dpFutureBlock = new int[rows][cols];

        // Row-wise
        for (int r = 0; r < rows; r++) {
            int c = 0;
            while (c < cols) {
                if (board[r][c].isWall()) {
                    c++;
                    continue;
                }
                
                int start = c;
                while (c < cols && !board[r][c].isWall()) c++;
                int end = c;
                
                int candidates = 0;
                for (int cc = start; cc < end; cc++)
                    if (board[r][cc].type == CommonCell.CellType.BLANK && 
                        canPlaceBulb(board, rows, cols, r, cc))
                        candidates++;
                
                for (int cc = start; cc < end; cc++)
                    if (board[r][cc].type == CommonCell.CellType.BLANK)
                        dpFutureBlock[r][cc] += candidates - 1; // subtract self
            }
        }

        // Column-wise
        for (int c = 0; c < cols; c++) {
            int r = 0;
            while (r < rows) {
                if (board[r][c].isWall()) {
                    r++;
                    continue;
                }
                
                int start = r;
                while (r < rows && !board[r][c].isWall()) r++;
                int end = r;
                
                int candidates = 0;
                for (int rr = start; rr < end; rr++)
                    if (board[rr][c].type == CommonCell.CellType.BLANK && 
                        canPlaceBulb(board, rows, cols, rr, c))
                        candidates++;
                
                for (int rr = start; rr < end; rr++)
                    if (board[rr][c].type == CommonCell.CellType.BLANK)
                        dpFutureBlock[rr][c] += candidates - 1;
            }
        }
        
        memoFutureBlock.put(key, dpFutureBlock);
        return dpFutureBlock;
    }

    // ===== COMBINED DP SCORE =====
    
    /**
     * Combined DP score for candidate (r,c), using pre-built tables.
     */
    public static int calculateDPScore(CommonCell[][] board, int rows, int cols,
                                       int r, int c,
                                       int[][] dpLightGain,
                                       int[][] dpNumberScore,
                                       int[][] dpFutureBlock,
                                       GraphNode graphNode,
                                       Map<Integer, Integer> centralityOrder) {
        if (!canPlaceBulb(board, rows, cols, r, c)) return Integer.MIN_VALUE;

        int score = 0;
        score += dpLightGain[r][c]  * 3;
        score += dpNumberScore[r][c];        // already weighted ×10 / ×15
        score -= dpFutureBlock[r][c] * 2;

        // Bonus: cell is currently unlit
        if (!board[r][c].lit) score += 5;

        // Graph-based centrality bonus
        if (graphNode != null && centralityOrder != null && centralityOrder.containsKey(graphNode.id)) {
            int centralityRank = centralityOrder.get(graphNode.id);
            if (centralityRank < 10) { // Top 10 most central
                score += 10;
            } else if (centralityRank < 20) {
                score += 5;
            }
        }
        
        return score;
    }

    // ===== HELPER: CAN PLACE BULB =====
    
    /**
     * Checks if a bulb can be legally placed at position (r,c)
     */
    public static boolean canPlaceBulb(CommonCell[][] board, int rows, int cols, int r, int c) {
        if (board[r][c].type != CommonCell.CellType.BLANK) return false;
        if (board[r][c].bulb || board[r][c].dot) return false;
        
        int[][] dirs = {{1,0},{-1,0},{0,1},{0,-1}};
        
        // Check for conflicting bulbs in line of sight
        for (int[] d : dirs) {
            int nr = r + d[0], nc = c + d[1];
            while (nr >= 0 && nr < rows && nc >= 0 && nc < cols) {
                if (board[nr][nc].isWall()) break;
                if (board[nr][nc].bulb) return false;
                nr += d[0];
                nc += d[1];
            }
        }
        
        // Check numbered cell constraints
        for (int[] d : dirs) {
            int nr = r + d[0], nc = c + d[1];
            if (nr < 0 || nr >= rows || nc < 0 || nc >= cols) continue;
            
            if (board[nr][nc].type == CommonCell.CellType.NUMBER) {
                int count = 0;
                for (int[] d2 : dirs) {
                    int rr = nr + d2[0], cc = nc + d2[1];
                    if (rr >= 0 && rr < rows && cc >= 0 && cc < cols && board[rr][cc].bulb)
                        count++;
                }
                
                // Check if this placement would be adjacent
                boolean adjacentToThis = false;
                for (int[] d2 : dirs) {
                    int rr = nr + d2[0], cc = nc + d2[1];
                    if (rr == r && cc == c) {
                        adjacentToThis = true;
                        break;
                    }
                }
                
                if (adjacentToThis) count++;
                if (count > board[nr][nc].number) return false;
            }
        }
        
        return true;
    }

    /**
     * Quick test to see if placing at (r,c) immediately solves the puzzle
     */
    public static boolean wouldSolvePuzzle(CommonCell[][] board, int rows, int cols, int r, int c) {
        // Simple check: if all cells are lit after placement
        boolean originalBulb = board[r][c].bulb;
        boolean originalDot = board[r][c].dot;
        
        board[r][c].bulb = true;
        board[r][c].dot = false;
        
        // Save lighting state
        boolean[][] originalLit = new boolean[rows][cols];
        for (int i = 0; i < rows; i++)
            for (int j = 0; j < cols; j++)
                originalLit[i][j] = board[i][j].lit;
        
        // Temporarily recompute lighting
        recomputeLighting(board, rows, cols);
        
        // Check if all blank cells are lit
        boolean allLit = true;
        for (int i = 0; i < rows && allLit; i++)
            for (int j = 0; j < cols; j++)
                if (board[i][j].type == CommonCell.CellType.BLANK && !board[i][j].lit)
                    allLit = false;
        
        // Restore state
        board[r][c].bulb = originalBulb;
        board[r][c].dot = originalDot;
        for (int i = 0; i < rows; i++)
            for (int j = 0; j < cols; j++)
                board[i][j].lit = originalLit[i][j];
        
        return allLit;
    }
    
    private static void recomputeLighting(CommonCell[][] board, int rows, int cols) {
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
}
