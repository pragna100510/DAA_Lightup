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
    
    // Import Cell class definition (would reference Member 1's Cell)
    // For standalone compilation, include minimal Cell definition
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

    // ===== DP TABLE 1: LIGHT GAIN =====
    
    /**
     * Builds dpLightGain[r][c]: number of currently-unlit cells that a bulb
     * placed at (r,c) would illuminate.
     * 
     * Uses prefix-sum DP along each row and column so the full table is filled
     * in O(rows*cols) instead of O((rows+cols)*rows*cols).
     * 
     * Algorithm:
     *   For each row, scan left-to-right tracking a "run" of blank cells
     *   separated by walls. Within a run, a bulb lights all unlit cells in
     *   the run. We store the run's unlit count and assign it to every member
     *   of the run (contribution from the horizontal direction).
     *   Repeat column-wise for vertical contribution.
     *   Finally add 1 if the cell itself is unlit.
     */
    public static int[][] buildDPLightGain(Cell[][] board, int rows, int cols) {
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
                if (board[r][c].type == Cell.CellType.BLANK && !board[r][c].lit)
                    dpLightGain[r][c]--;   // remove the double-count
                    
        return dpLightGain;
    }

    // ===== DP TABLE 2: NUMBER SCORE =====
    
    /**
     * Builds dpNumberScore[r][c]: how much placing a bulb at (r,c) helps
     * satisfy adjacent numbered cells.
     * 
     * For each adjacent NUMBER cell, if placing here moves the count closer
     * to the target, add (target - current).
     * 
     * Memoized: each NUMBER cell is evaluated once and its result is written
     * to all adjacent blank cells via a reverse-lookup table.
     */
    public static int[][] buildDPNumberScore(Cell[][] board, int rows, int cols) {
        int[][] dpNumberScore = new int[rows][cols];
        int[][] dirs = {{1,0},{-1,0},{0,1},{0,-1}};

        // For each numbered cell, compute deficit and write to neighbours once
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                if (board[r][c].type != Cell.CellType.NUMBER) continue;
                
                int target  = board[r][c].number;
                int placed  = 0;
                List<int[]> empties = new ArrayList<>();
                
                for (int[] d : dirs) {
                    int nr = r + d[0], nc = c + d[1];
                    if (nr < 0 || nr >= rows || nc < 0 || nc >= cols) continue;
                    
                    if (board[nr][nc].bulb) {
                        placed++;
                    } else if (board[nr][nc].type == Cell.CellType.BLANK && !board[nr][nc].dot) {
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
        
        return dpNumberScore;
    }

    // ===== DP TABLE 3: FUTURE BLOCK =====
    
    /**
     * Builds dpFutureBlock[r][c]: how many currently-placeable blank cells
     * in the same rows/columns would be blocked if we place a bulb at (r,c).
     * 
     * Uses a simple linear scan per run, memoised per run segment.
     */
    public static int[][] buildDPFutureBlock(Cell[][] board, int rows, int cols) {
        int[][] dpFutureBlock = new int[rows][cols];

        // Count the number of canPlace candidates in each row-run and col-run,
        // then assign that count to every candidate in the same run (they all
        // block each other).

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
                    if (board[r][cc].type == Cell.CellType.BLANK && 
                        canPlaceBulb(board, rows, cols, r, cc))
                        candidates++;
                
                for (int cc = start; cc < end; cc++)
                    if (board[r][cc].type == Cell.CellType.BLANK)
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
                    if (board[rr][c].type == Cell.CellType.BLANK && 
                        canPlaceBulb(board, rows, cols, rr, c))
                        candidates++;
                
                for (int rr = start; rr < end; rr++)
                    if (board[rr][c].type == Cell.CellType.BLANK)
                        dpFutureBlock[rr][c] += candidates - 1;
            }
        }
        
        return dpFutureBlock;
    }

    // ===== COMBINED DP SCORE =====
    
    /**
     * Combined DP score for candidate (r,c), using pre-built tables.
     * Called inside the D&C evaluation — O(1) per candidate.
     * 
     * @param dpLightGain Pre-computed light gain table
     * @param dpNumberScore Pre-computed number score table
     * @param dpFutureBlock Pre-computed future block table
     * @param graphNode The graph node at this position (can be null)
     * @param centralityOrder Map of node centrality rankings
     * @return Score for this position, or Integer.MIN_VALUE if invalid
     */
    public static int calculateDPScore(Cell[][] board, int rows, int cols,
                                       int r, int c,
                                       int[][] dpLightGain,
                                       int[][] dpNumberScore,
                                       int[][] dpFutureBlock,
                                       Object graphNode,
                                       Map<Integer, Integer> centralityOrder) {
        if (!canPlaceBulb(board, rows, cols, r, c)) return Integer.MIN_VALUE;

        int score = 0;
        score += dpLightGain[r][c]  * 3;
        score += dpNumberScore[r][c];        // already weighted ×10 / ×15
        score -= dpFutureBlock[r][c] * 2;

        // Bonus: cell is currently unlit
        if (!board[r][c].lit) score += 5;

        // Graph-based centrality bonus (if graph node provided)
        // This would integrate with Member 1's GraphNode class
        
        return score;
    }

    // ===== HELPER: CAN PLACE BULB =====
    
    /**
     * Checks if a bulb can be legally placed at position (r,c)
     * Used by dpFutureBlock calculation
     */
    private static boolean canPlaceBulb(Cell[][] board, int rows, int cols, int r, int c) {
        if (board[r][c].type != Cell.CellType.BLANK) return false;
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
            
            if (board[nr][nc].type == Cell.CellType.NUMBER) {
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
     * This is a fast-check optimization for the DP scorer
     */
    public static boolean wouldSolvePuzzle(Cell[][] board, int rows, int cols, int r, int c) {
        // This would integrate with Member 4's validation logic
        // Placeholder for now
        return false;
    }
}
