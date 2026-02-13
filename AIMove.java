import java.util.*;

/**
 * - Recursive board partitioning (Divide & Conquer)
 * - Region-based candidate evaluation
 * - AI move decision making
 * - Forced move detection
 * - Strategic dot placement
 */
public class AIMove {
    
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

    // ===== REGION STRUCTURE =====
    
    /**
     * Represents a rectangular sub-region of the board.
     */
    static class Region {
        int r0, c0, r1, c1; // inclusive bounds
        
        Region(int r0, int c0, int r1, int c1) {
            this.r0 = r0;
            this.c0 = c0;
            this.r1 = r1;
            this.c1 = c1;
        }
        
        int area() {
            return (r1 - r0 + 1) * (c1 - c0 + 1);
        }
    }

    private static final int MIN_REGION_AREA = 4;

    // ===== DIVIDE & CONQUER =====
    
    /**
     * Recursively divides the region until a base case is reached,
     * then evaluates all candidate placements in the region and returns
     * the best (r,c) found.
     * 
     * Divide strategy:
     *   - If the region contains ≤ MIN_REGION_AREA cells, evaluate all
     *     candidates directly (base case).
     *   - Otherwise, split along the longer axis. Recursively solve
     *     both halves. Choose the better candidate by DP score, but
     *     prefer the sub-region that has more unlit cells (most urgent).
     * 
     * @param board The game board
     * @param region The region to evaluate
     * @param dpScorer Function to score each candidate position
     * @return int[]{bestScore, bestR, bestC} or null if no candidate found
     */
    public static int[] dcBestInRegion(Cell[][] board, int rows, int cols,
                                        Region region,
                                        DPScorer dpScorer) {
        if (region.area() <= MIN_REGION_AREA ||
            (region.r1 - region.r0 < 1 && region.c1 - region.c0 < 1)) {
            // BASE CASE: evaluate every blank candidate in this region
            return evaluateRegion(board, rows, cols, region, dpScorer);
        }

        // DIVIDE: split along the longer axis
        Region left, right;
        int rSpan = region.r1 - region.r0;
        int cSpan = region.c1 - region.c0;

        if (rSpan >= cSpan) {
            int mid = (region.r0 + region.r1) / 2;
            left  = new Region(region.r0, region.c0, mid, region.c1);
            right = new Region(mid + 1, region.c0, region.r1, region.c1);
        } else {
            int mid = (region.c0 + region.c1) / 2;
            left  = new Region(region.r0, region.c0, region.r1, mid);
            right = new Region(region.r0, mid + 1, region.r1, region.c1);
        }

        // CONQUER: recursively solve both halves
        int[] bestLeft  = dcBestInRegion(board, rows, cols, left, dpScorer);
        int[] bestRight = dcBestInRegion(board, rows, cols, right, dpScorer);

        // COMBINE: pick the better candidate
        if (bestLeft == null) return bestRight;
        if (bestRight == null) return bestLeft;

        // If scores are equal, prefer the region with more unlit cells (more urgent)
        if (bestLeft[0] == bestRight[0]) {
            int unlitLeft  = countUnlitInRegion(board, left);
            int unlitRight = countUnlitInRegion(board, right);
            return (unlitLeft >= unlitRight) ? bestLeft : bestRight;
        }
        
        return (bestLeft[0] >= bestRight[0]) ? bestLeft : bestRight;
    }

    /**
     * Evaluates all blank, placeable candidates in a region.
     * Returns {score, r, c}.
     */
    private static int[] evaluateRegion(Cell[][] board, int rows, int cols,
                                         Region reg,
                                         DPScorer dpScorer) {
        int bestScore = Integer.MIN_VALUE;
        int bestR = -1, bestC = -1;

        for (int r = reg.r0; r <= reg.r1; r++) {
            for (int c = reg.c0; c <= reg.c1; c++) {
                if (board[r][c].type != Cell.CellType.BLANK) continue;
                
                int score = dpScorer.score(r, c);
                if (score == Integer.MIN_VALUE) continue; // canPlace returned false
                
                if (score > bestScore) {
                    bestScore = score;
                    bestR = r;
                    bestC = c;
                }
            }
        }
        
        return (bestR == -1) ? null : new int[]{bestScore, bestR, bestC};
    }

    /**
     * Counts unlit blank cells in a region (used for D&C urgency comparison).
     */
    private static int countUnlitInRegion(Cell[][] board, Region reg) {
        int count = 0;
        for (int r = reg.r0; r <= reg.r1; r++)
            for (int c = reg.c0; c <= reg.c1; c++)
                if (board[r][c].type == Cell.CellType.BLANK && !board[r][c].lit)
                    count++;
        return count;
    }

    // ===== AI MOVE LOGIC =====
    
    /**
     * Main AI decision function
     * Returns {r, c, type} where type: 0=bulb, 1=remove, 2=dot, -1=no move
     */
    public static int[] makeAIMove(Cell[][] board, int rows, int cols,
                                    DPScorer dpScorer) {
        // PHASE 1: Fix a rule violation (one removal per turn)
        List<int[]> violating = new ArrayList<>();
        for (int r = 0; r < rows; r++)
            for (int c = 0; c < cols; c++)
                if (board[r][c].bulb && isViolatingBulb(board, rows, cols, r, c))
                    violating.add(new int[]{r, c});

        if (!violating.isEmpty()) {
            int[] rem = violating.get(0);
            return new int[]{rem[0], rem[1], 1}; // Type 1 = remove bulb
        }

        // PHASE 2: Forced numbered-cell placement
        int[] forced = findForcedNumberedCellPlacement(board, rows, cols);
        if (forced != null && canPlaceBulb(board, rows, cols, forced[0], forced[1])) {
            return new int[]{forced[0], forced[1], 0}; // Type 0 = place bulb
        }

        // PHASE 3: Use Divide & Conquer to find best placement
        Region fullBoard = new Region(0, 0, rows - 1, cols - 1);
        int[] best = dcBestInRegion(board, rows, cols, fullBoard, dpScorer);

        if (best != null && best[0] > Integer.MIN_VALUE) {
            return new int[]{best[1], best[2], 0}; // Type 0 = place bulb
        }

        // PHASE 4: Fallback — strategic dot placement
        int[] dot = findStrategicDotPlacement(board, rows, cols);
        if (dot != null) {
            return new int[]{dot[0], dot[1], 2}; // Type 2 = place dot
        }

        return new int[]{-1, -1, -1}; // No move found
    }

    // ===== FORCED MOVE DETECTION =====
    
    /**
     * Finds a forced placement around numbered cells
     * Returns {r, c} if found, null otherwise
     */
    private static int[] findForcedNumberedCellPlacement(Cell[][] board, int rows, int cols) {
        int[][] d = {{1,0},{-1,0},{0,1},{0,-1}};
        
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                if (board[r][c].type != Cell.CellType.NUMBER) continue;
                
                int need = board[r][c].number;
                List<int[]> empties = new ArrayList<>();
                int placed = 0;
                
                for (int[] dir : d) {
                    int nr = r + dir[0], nc = c + dir[1];
                    if (nr < 0 || nr >= rows || nc < 0 || nc >= cols) continue;
                    
                    if (board[nr][nc].bulb) {
                        placed++;
                    } else if (board[nr][nc].type == Cell.CellType.BLANK && 
                               !board[nr][nc].dot) {
                        empties.add(new int[]{nr, nc});
                    }
                }
                
                // If we need exactly one more bulb and have exactly one empty spot
                if (need - placed == 1 && empties.size() == 1) {
                    return empties.get(0);
                }
            }
        }
        
        return null;
    }

    // ===== STRATEGIC DOT PLACEMENT =====
    
    /**
     * Finds a strategic position to place a dot marker
     * Useful for eliminating possibilities and guiding search
     */
    private static int[] findStrategicDotPlacement(Cell[][] board, int rows, int cols) {
        int[][] dirs = {{1,0},{-1,0},{0,1},{0,-1}};
        
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                if (board[r][c].type != Cell.CellType.BLANK || 
                    board[r][c].bulb || 
                    board[r][c].dot) continue;
                
                // Count how many placeable spots this cell can "see"
                int visibleSpots = 0;
                for (int[] dir : dirs) {
                    int nr = r + dir[0], nc = c + dir[1];
                    while (nr >= 0 && nr < rows && nc >= 0 && nc < cols) {
                        if (board[nr][nc].isWall()) break;
                        
                        if (board[nr][nc].type == Cell.CellType.BLANK && 
                            canPlaceBulb(board, rows, cols, nr, nc)) {
                            visibleSpots++;
                            break;
                        }
                        
                        nr += dir[0];
                        nc += dir[1];
                    }
                }
                
                // If this cell can see multiple placement spots, 
                // marking it might help
                if (visibleSpots > 1) {
                    return new int[]{r, c};
                }
            }
        }
        
        return null;
    }

    // ===== VIOLATION CHECKING =====
    
    /**
     * Checks if a bulb at (r,c) violates any rules
     */
    private static boolean isViolatingBulb(Cell[][] board, int rows, int cols, int r, int c) {
        if (!board[r][c].bulb) return false;
        
        int[][] dirs = {{1,0},{-1,0},{0,1},{0,-1}};
        
        // Check for bulbs seeing each other
        for (int[] d : dirs) {
            int nr = r + d[0], nc = c + d[1];
            while (nr >= 0 && nr < rows && nc >= 0 && nc < cols) {
                if (board[nr][nc].isWall()) break;
                if (board[nr][nc].bulb) return true;
                nr += d[0];
                nc += d[1];
            }
        }
        
        // Check numbered cell over-satisfaction
        for (int[] d : dirs) {
            int nr = r + d[0], nc = c + d[1];
            if (nr < 0 || nr >= rows || nc < 0 || nc >= cols) continue;
            
            if (board[nr][nc].type == Cell.CellType.NUMBER) {
                int count = 0;
                for (int[] d2 : dirs) {
                    int rr = nr + d2[0], cc = nc + d2[1];
                    if (rr >= 0 && rr < rows && cc >= 0 && cc < cols && 
                        board[rr][cc].bulb)
                        count++;
                }
                if (count > board[nr][nc].number) return true;
            }
        }
        
        return false;
    }

    /**
     * Checks if a bulb can be legally placed at position (r,c)
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
                    if (rr >= 0 && rr < rows && cc >= 0 && cc < cols && 
                        board[rr][cc].bulb)
                        count++;
                }
                
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

    // ===== INTERFACE FOR DP SCORER =====
    
    /**
     * Interface for DP scoring function
     * Allows integration with Member 2's DP implementation
     */
    public interface DPScorer {
        int score(int r, int c);
    }
}
