import java.util.*;

/**
 * - Recursive board partitioning (Divide & Conquer)
 * - Region-based candidate evaluation
 * - AI move decision making
 * - Forced move detection
 * - Strategic dot placement
 */
public class AIMove {
    
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

    // ===== DIVIDE & CONQUER =====
    
    /**
     * DIVIDE & CONQUER — wall-aware entry point.
     *
     * DIVIDE  : BFS flood-fill inside the given bounding Region splits the board
     *           into connected blank pockets separated by walls.  Walls are the
     *           natural sub-problem boundaries for Light Up — a bulb in one pocket
     *           can never illuminate cells in another pocket.
     *
     * CONQUER : Each wall-bounded pocket is evaluated independently via
     *           dcBestInWallRegion(), which scores every placeable blank cell
     *           using the DP scorer.
     *
     * COMBINE : The best candidate across all pockets is returned.  When two
     *           pockets tie on score, the one with more unlit cells wins
     *           (it is more urgent to light).
     *
     * @param board    The game board
     * @param region   Bounding rectangle to search within (pass full board for AI)
     * @param dpScorer DP scoring function
     * @return int[]{bestScore, bestR, bestC} or null if no placeable cell found
     */
    public static int[] dcBestInRegion(CommonCell[][] board, int rows, int cols,
                                        Region region,
                                        DPScorer dpScorer) {
        // DIVIDE — BFS flood-fill to find wall-separated pockets inside the bounding region
        List<List<int[]>> pockets = dcFindWallRegions(board, rows, cols, region);

        int[] best = null;

        // CONQUER — evaluate each pocket independently
        for (List<int[]> pocket : pockets) {
            int[] candidate = dcBestInWallRegion(board, pocket, dpScorer);
            if (candidate == null) continue;

            // COMBINE — keep the higher-scoring candidate; break ties by urgency
            if (best == null) {
                best = candidate;
            } else if (candidate[0] > best[0]) {
                best = candidate;
            } else if (candidate[0] == best[0]) {
                // Tie-break: prefer the pocket with more unlit cells (more urgent)
                if (countUnlitInPocket(board, pocket) > countUnlitInPocket(board, pocketOf(best, pockets)))
                    best = candidate;
            }
        }

        return best;
    }

    /**
     * BFS flood-fill inside a bounding Region.
     * Returns a list of pockets, where each pocket is a list of [r,c] blank cells
     * connected to each other without crossing a wall.
     */
    private static List<List<int[]>> dcFindWallRegions(CommonCell[][] board, int rows, int cols,
                                                         Region bounds) {
        boolean[][] visited = new boolean[rows][cols];
        List<List<int[]>> pockets = new ArrayList<>();
        int[][] dirs = {{1,0},{-1,0},{0,1},{0,-1}};

        for (int r = bounds.r0; r <= bounds.r1; r++) {
            for (int c = bounds.c0; c <= bounds.c1; c++) {
                if (visited[r][c] || board[r][c].isWall()) continue;

                // BFS from (r,c)
                List<int[]> pocket = new ArrayList<>();
                Queue<int[]> queue = new LinkedList<>();
                queue.add(new int[]{r, c});
                visited[r][c] = true;

                while (!queue.isEmpty()) {
                    int[] cur = queue.poll();
                    pocket.add(cur);
                    for (int[] d : dirs) {
                        int nr = cur[0] + d[0], nc = cur[1] + d[1];
                        if (nr < bounds.r0 || nr > bounds.r1 || nc < bounds.c0 || nc > bounds.c1) continue;
                        if (visited[nr][nc] || board[nr][nc].isWall()) continue;
                        visited[nr][nc] = true;
                        queue.add(new int[]{nr, nc});
                    }
                }
                pockets.add(pocket);
            }
        }
        return pockets;
    }

    /**
     * CONQUER step: scores every blank, placeable cell in one wall-bounded pocket
     * using the DP scorer and returns the best {score, r, c}.
     */
    private static int[] dcBestInWallRegion(CommonCell[][] board,
                                             List<int[]> pocket,
                                             DPScorer dpScorer) {
        int bestScore = Integer.MIN_VALUE;
        int bestR = -1, bestC = -1;

        for (int[] cell : pocket) {
            int r = cell[0], c = cell[1];
            if (board[r][c].type != CommonCell.CellType.BLANK) continue;

            int score = dpScorer.score(r, c);
            if (score == Integer.MIN_VALUE) continue; // canPlace returned false

            if (score > bestScore) {
                bestScore = score;
                bestR = r;
                bestC = c;
            }
        }

        return (bestR == -1) ? null : new int[]{bestScore, bestR, bestC};
    }

    /**
     * Counts unlit blank cells in a wall-bounded pocket (for tie-breaking).
     */
    private static int countUnlitInPocket(CommonCell[][] board, List<int[]> pocket) {
        if (pocket == null) return 0;
        int count = 0;
        for (int[] cell : pocket)
            if (board[cell[0]][cell[1]].type == CommonCell.CellType.BLANK && !board[cell[0]][cell[1]].lit)
                count++;
        return count;
    }

    /**
     * Finds which pocket in the list contains the cell at {score,r,c}.
     * Used for tie-breaking in the COMBINE step.
     */
    private static List<int[]> pocketOf(int[] best, List<List<int[]>> pockets) {
        for (List<int[]> pocket : pockets)
            for (int[] cell : pocket)
                if (cell[0] == best[1] && cell[1] == best[2])
                    return pocket;
        return Collections.emptyList();
    }

    // ===== AI MOVE LOGIC =====
    
    /**
     * Main AI decision function
     * Returns {r, c, type} where type: 0=bulb, 1=remove, 2=dot, -1=no move
     */
    public static int[] makeAIMove(CommonCell[][] board, int rows, int cols,
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
    private static int[] findForcedNumberedCellPlacement(CommonCell[][] board, int rows, int cols) {
        int[][] d = {{1,0},{-1,0},{0,1},{0,-1}};
        
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                if (board[r][c].type != CommonCell.CellType.NUMBER) continue;
                
                int need = board[r][c].number;
                List<int[]> empties = new ArrayList<>();
                int placed = 0;
                
                for (int[] dir : d) {
                    int nr = r + dir[0], nc = c + dir[1];
                    if (nr < 0 || nr >= rows || nc < 0 || nc >= cols) continue;
                    
                    if (board[nr][nc].bulb) {
                        placed++;
                    } else if (board[nr][nc].type == CommonCell.CellType.BLANK && 
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
    private static int[] findStrategicDotPlacement(CommonCell[][] board, int rows, int cols) {
        int[][] dirs = {{1,0},{-1,0},{0,1},{0,-1}};
        
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                if (board[r][c].type != CommonCell.CellType.BLANK || 
                    board[r][c].bulb || 
                    board[r][c].dot) continue;
                
                // Count how many placeable spots this cell can "see"
                int visibleSpots = 0;
                for (int[] dir : dirs) {
                    int nr = r + dir[0], nc = c + dir[1];
                    while (nr >= 0 && nr < rows && nc >= 0 && nc < cols) {
                        if (board[nr][nc].isWall()) break;
                        
                        if (board[nr][nc].type == CommonCell.CellType.BLANK && 
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
    public static boolean isViolatingBulb(CommonCell[][] board, int rows, int cols, int r, int c) {
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
            
            if (board[nr][nc].type == CommonCell.CellType.NUMBER) {
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