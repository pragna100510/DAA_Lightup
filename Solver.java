import java.util.*;

/**
 * Dedicated solver for Light Up puzzles
 * Used to verify puzzle solvability during generation
 */
public class Solver {
    
    /**
     * Checks if a puzzle has at least one valid solution
     */
    public static boolean isSolvable(CommonCell[][] board, int rows, int cols) {
        // Create a deep copy of the board
        CommonCell[][] testBoard = new CommonCell[rows][cols];
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                testBoard[r][c] = new CommonCell(board[r][c]);
                testBoard[r][c].bulb = false;
                testBoard[r][c].dot = false;
            }
        }
        
        // First, apply simple deductions to reduce search space
        boolean changed;
        do {
            changed = applySimpleDeductions(testBoard, rows, cols);
        } while (changed);
        
        // Then use backtracking
        return solveBacktracking(testBoard, rows, cols);
    }
    /**
     * Greedy solver: repeatedly applies constraint propagation rules until no
     * more progress, then places bulbs greedily by highest light-gain score.
     * Fast but not always complete — may fail on harder puzzles.
     */
    public static boolean solveGreedy(CommonCell[][] board, int rows, int cols) {
        // Clear existing bulbs/dots
        for (int r = 0; r < rows; r++)
            for (int c = 0; c < cols; c++) {
                board[r][c].bulb = false;
                board[r][c].dot  = false;
            }
        recomputeLighting(board, rows, cols);

        boolean progress = true;
        while (progress) {
            progress = false;

            // Step 1: constraint propagation
            if (applySimpleDeductions(board, rows, cols)) { progress = true; continue; }

            // Step 2: place bulb at highest-scoring unlit cell
            int bestScore = Integer.MIN_VALUE, bestR = -1, bestC = -1;
            for (int r = 0; r < rows; r++) {
                for (int c = 0; c < cols; c++) {
                    if (!canPlaceBulb(board, rows, cols, r, c)) continue;
                    int litGain = countUnlitLightGain(board, rows, cols, r, c);
                    int numHelp = countNumberedHelp(board, rows, cols, r, c);
                    int score   = litGain * 3 + numHelp * 10;
                    if (!board[r][c].lit) score += 5;
                    if (score > bestScore) { bestScore = score; bestR = r; bestC = c; }
                }
            }
            if (bestR != -1) {
                board[bestR][bestC].bulb = true;
                recomputeLighting(board, rows, cols);
                progress = true;
            }
        }

        return isValidSolution(board, rows, cols);
    }

    /** Counts how many currently-unlit cells a bulb at (r,c) would illuminate. */
    private static int countUnlitLightGain(CommonCell[][] board, int rows, int cols,
                                            int r, int c) {
        int gain = board[r][c].lit ? 0 : 1;
        int[][] dirs = {{-1,0},{1,0},{0,-1},{0,1}};
        for (int[] d : dirs) {
            int nr = r + d[0], nc = c + d[1];
            while (nr >= 0 && nr < rows && nc >= 0 && nc < cols) {
                if (board[nr][nc].isWall()) break;
                if (!board[nr][nc].lit) gain++;
                nr += d[0]; nc += d[1];
            }
        }
        return gain;
    }

    /** Returns how much a bulb at (r,c) helps satisfy adjacent numbered cells. */
    private static int countNumberedHelp(CommonCell[][] board, int rows, int cols,
                                          int r, int c) {
        int help = 0;
        int[][] dirs = {{-1,0},{1,0},{0,-1},{0,1}};
        for (int[] d : dirs) {
            int nr = r + d[0], nc = c + d[1];
            if (nr < 0 || nr >= rows || nc < 0 || nc >= cols) continue;
            if (board[nr][nc].type != CommonCell.CellType.NUMBER) continue;
            int target = board[nr][nc].number, current = 0;
            for (int[] d2 : dirs) {
                int rr = nr + d2[0], cc = nc + d2[1];
                if (rr >= 0 && rr < rows && cc >= 0 && cc < cols && board[rr][cc].bulb)
                    current++;
            }
            if (current < target) help += (target - current);
        }
        return help;
    }

    public static boolean solveBoard(CommonCell[][] board, int rows, int cols) {

    // Clear any existing bulbs/dots
    for (int r = 0; r < rows; r++)
        for (int c = 0; c < cols; c++) {
            board[r][c].bulb = false;
            board[r][c].dot = false;
        }

    recomputeLighting(board, rows, cols);

    boolean changed;
    do {
        changed = applySimpleDeductions(board, rows, cols);
    } while (changed);

    return solveBacktracking(board, rows, cols);
}

    /**
     * Apply simple logical deductions to force placements
     */
    private static boolean applySimpleDeductions(CommonCell[][] board, int rows, int cols) {
        boolean changed = false;
        changed |= completeNumberCells(board, rows, cols);
        changed |= forceSingleLightSources(board, rows, cols);
        changed |= forbidConflictingPlacements(board, rows, cols);
        recomputeLighting(board, rows, cols);
        return changed;
    }
    
    private static boolean completeNumberCells(CommonCell[][] board, int rows, int cols) {
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
        return changed;
    }
    
    private static boolean forceSingleLightSources(CommonCell[][] board, int rows, int cols) {
        boolean changed = false;

        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                if (board[r][c].type != CommonCell.CellType.BLANK || board[r][c].lit || board[r][c].dot) continue;

                List<int[]> possibleBulbPositions = findPossibleBulbPositionsThatLightThisCell(board, rows, cols, r, c);

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
        return changed;
    }
    
    private static List<int[]> findPossibleBulbPositionsThatLightThisCell(CommonCell[][] board, int rows, int cols, int tr, int tc) {
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
    
    private static boolean forbidConflictingPlacements(CommonCell[][] board, int rows, int cols) {
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
    
    private static boolean solveBacktracking(CommonCell[][] board, int rows, int cols) {
        // Find next empty cell
        int[] next = findNextEmpty(board, rows, cols);
        if (next == null) {
            // No more empty cells, check if solution is valid
            return isValidSolution(board, rows, cols);
        }
        
        int r = next[0];
        int c = next[1];
        
        // Try placing a bulb
        if (canPlaceBulb(board, rows, cols, r, c)) {
            board[r][c].bulb = true;
            recomputeLighting(board, rows, cols);
            
            if (solveBacktracking(board, rows, cols)) {
                return true;
            }
            
            // Backtrack
            board[r][c].bulb = false;
            recomputeLighting(board, rows, cols);
        }
        
        // Try leaving it empty (but only if it can be lit by another bulb)
        // Mark as dot to avoid placing bulb here in future
        board[r][c].dot = true;
        if (solveBacktracking(board, rows, cols)) {
            return true;
        }
        
        // Backtrack
        board[r][c].dot = false;
        
        return false;
    }
    
    private static int[] findNextEmpty(CommonCell[][] board, int rows, int cols) {
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                if (board[r][c].type == CommonCell.CellType.BLANK && 
                    !board[r][c].bulb && !board[r][c].dot) {
                    return new int[]{r, c};
                }
            }
        }
        return null;
    }
    
    private static boolean isValidSolution(CommonCell[][] board, int rows, int cols) {
        // Rule 1: All blank cells are lit
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                if (board[r][c].type == CommonCell.CellType.BLANK && !board[r][c].lit) {
                    return false;
                }
            }
        }

        int[][] dirs = {{1,0},{-1,0},{0,1},{0,-1}};
        
        // Rule 2 & 3: No two bulbs see each other
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                if (!board[r][c].bulb) continue;
                
                for (int[] d : dirs) {
                    int nr = r + d[0], nc = c + d[1];
                    while (nr >= 0 && nr < rows && nc >= 0 && nc < cols) {
                        if (board[nr][nc].isWall()) break;
                        if (board[nr][nc].bulb) return false;
                        nr += d[0];
                        nc += d[1];
                    }
                }
            }
        }
        
        // Rule 4: Numbered cells have exact count
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                if (board[r][c].type != CommonCell.CellType.NUMBER) continue;
                
                int count = 0;
                for (int[] d : dirs) {
                    int nr = r + d[0], nc = c + d[1];
                    if (nr >= 0 && nr < rows && nc >= 0 && nc < cols && board[nr][nc].bulb) {
                        count++;
                    }
                }
                
                if (count != board[r][c].number) return false;
            }
        }
        
        return true;
    }
    
    private static boolean canPlaceBulb(CommonCell[][] board, int rows, int cols, int r, int c) {
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

    // =========================================================================
    // DIVIDE & CONQUER + DP SOLVER
    // =========================================================================

    /**
     * Public entry point called from LightUp.
     *
     * DIVIDE  : BFS flood-fill splits the board into connected blank regions
     *           (walls are natural dividers between sub-problems).
     * CONQUER : Each region is solved independently. Blank cells are sorted by
     *           a composite DP score (light gain + number satisfaction - future
     *           blocking) so the most impactful cells are explored first,
     *           dramatically pruning the search tree.
     * COMBINE : Region solutions are merged and the full board is validated.
     *           If cross-region numbered-wall constraints cannot be resolved,
     *           the method falls back to full backtracking (solveBoard).
     *
     * Modifies {@code board} in-place on success.
     */
    public static boolean solveDivideAndConquer(CommonCell[][] board, int rows, int cols) {
        // Work on a deep copy so the board is untouched if solving fails
        CommonCell[][] copy = new CommonCell[rows][cols];
        for (int r = 0; r < rows; r++)
            for (int c = 0; c < cols; c++) {
                copy[r][c] = new CommonCell(board[r][c]);
                copy[r][c].bulb = false;
                copy[r][c].dot  = false;
            }

        // DIVIDE — find connected blank regions separated by walls
        List<List<int[]>> regions = dcFindRegions(copy, rows, cols);

        // CONQUER — solve each region with DP-guided backtracking
        for (List<int[]> region : regions) {
            if (!dcSolveRegion(copy, rows, cols, region)) {
                // Region failed: fall back to full backtracking on original board
                return solveBoard(board, rows, cols);
            }
        }

        // COMBINE — validate the fully merged solution
        if (!isValidSolution(copy, rows, cols)) {
            // Cross-region constraint mismatch: fall back
            return solveBoard(board, rows, cols);
        }

        // Commit solution to the live board
        for (int r = 0; r < rows; r++)
            for (int c = 0; c < cols; c++) {
                board[r][c].bulb = copy[r][c].bulb;
                board[r][c].dot  = false;
            }
        recomputeLighting(board, rows, cols);
        return true;
    }

    /**
     * BFS flood-fill: groups all non-wall cells into connected regions.
     * Walls act as dividers — each isolated pocket becomes its own sub-problem.
     */
    private static List<List<int[]>> dcFindRegions(CommonCell[][] b, int rows, int cols) {
        boolean[][] visited = new boolean[rows][cols];
        List<List<int[]>> regions = new ArrayList<>();
        int[][] dirs = {{1,0},{-1,0},{0,1},{0,-1}};

        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                if (visited[r][c] || b[r][c].isWall()) continue;

                List<int[]> region = new ArrayList<>();
                Queue<int[]> queue = new LinkedList<>();
                queue.add(new int[]{r, c});
                visited[r][c] = true;

                while (!queue.isEmpty()) {
                    int[] cur = queue.poll();
                    region.add(cur);
                    for (int[] d : dirs) {
                        int nr = cur[0]+d[0], nc = cur[1]+d[1];
                        if (nr >= 0 && nr < rows && nc >= 0 && nc < cols
                                && !visited[nr][nc] && !b[nr][nc].isWall()) {
                            visited[nr][nc] = true;
                            queue.add(new int[]{nr, nc});
                        }
                    }
                }
                regions.add(region);
            }
        }
        return regions;
    }

    /**
     * DP-guided solver for a single region.
     * Blank cells are sorted by composite DP score (descending) so the solver
     * explores the most constrained / highest-impact positions first.
     */
    private static boolean dcSolveRegion(CommonCell[][] b, int rows, int cols,
                                          List<int[]> region) {
        // Build DP tables reflecting current board state
        int[][] lgain  = DynamicProg.buildDPLightGain(b, rows, cols);
        int[][] nscore = DynamicProg.buildDPNumberScore(b, rows, cols);
        int[][] fblock = DynamicProg.buildDPFutureBlock(b, rows, cols);

        // Collect only BLANK cells (walls/numbers are constraints, not placement slots)
        List<int[]> blanks = new ArrayList<>();
        for (int[] cell : region)
            if (b[cell[0]][cell[1]].type == CommonCell.CellType.BLANK)
                blanks.add(cell);

        // Sort descending by DP score — highest-value cells tried first
        blanks.sort((a, z) -> {
            int sa = lgain[a[0]][a[1]] + nscore[a[0]][a[1]] - fblock[a[0]][a[1]];
            int sz = lgain[z[0]][z[1]] + nscore[z[0]][z[1]] - fblock[z[0]][z[1]];
            return Integer.compare(sz, sa);
        });

        return dcBacktrack(b, rows, cols, blanks, 0, new HashSet<>());
    }

    /**
     * Backtracking over a DP-sorted list of blank cells in a region.
     * At each position: try placing a bulb, or skip.
     * Prunes early when any numbered wall is already over-satisfied.
     */
    private static boolean dcBacktrack(CommonCell[][] b, int rows, int cols,
                                        List<int[]> blanks, int idx, Set<String> memo) {
        if (dcHasNumberViolation(b, rows, cols)) return false;
        if (idx == blanks.size()) return dcRegionSatisfied(b, rows, cols, blanks);

        // Build a state key: which of blanks[0..idx-1] hold bulbs, plus current index
        StringBuilder sb = new StringBuilder(idx + 4);
        for (int i = 0; i < idx; i++)
            sb.append(b[blanks.get(i)[0]][blanks.get(i)[1]].bulb ? '1' : '0');
        sb.append(':').append(idx);
        String stateKey = sb.toString();
        if (memo.contains(stateKey)) return false;
        memo.add(stateKey);

        int r = blanks.get(idx)[0];
        int c = blanks.get(idx)[1];

        // Option A: place a bulb (only if it doesn't immediately conflict)
        if (canPlaceBulb(b, rows, cols, r, c)) {
            b[r][c].bulb = true;
            recomputeLighting(b, rows, cols);
            DynamicProg.clearMemo(); // invalidate cached DP tables after board mutation
            if (dcBacktrack(b, rows, cols, blanks, idx + 1, memo)) return true;
            b[r][c].bulb = false;
            recomputeLighting(b, rows, cols);
            DynamicProg.clearMemo();
        }

        // Option B: leave this cell without a bulb
        return dcBacktrack(b, rows, cols, blanks, idx + 1, memo);
    }

    /**
     * Pruning check: returns true if any numbered wall already has MORE
     * adjacent bulbs than its required count.
     */
    private static boolean dcHasNumberViolation(CommonCell[][] b, int rows, int cols) {
        int[][] dirs = {{1,0},{-1,0},{0,1},{0,-1}};
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                if (b[r][c].type != CommonCell.CellType.NUMBER) continue;
                int count = 0;
                for (int[] d : dirs) {
                    int nr = r+d[0], nc = c+d[1];
                    if (nr >= 0 && nr < rows && nc >= 0 && nc < cols && b[nr][nc].bulb)
                        count++;
                }
                if (count > b[r][c].number) return true;
            }
        }
        return false;
    }

    /**
     * Terminal check after all cells in a region are decided:
     * - Every blank cell in the region must be lit.
     * - Every numbered wall bordering this region must have exactly the right count.
     */
    private static boolean dcRegionSatisfied(CommonCell[][] b, int rows, int cols,
                                              List<int[]> blanks) {
        int[][] dirs = {{1,0},{-1,0},{0,1},{0,-1}};

        // All blank cells in the region must be lit
        for (int[] cell : blanks)
            if (!b[cell[0]][cell[1]].lit) return false;

        // Numbered walls adjacent to this region must be exactly satisfied
        Set<String> checked = new HashSet<>();
        for (int[] cell : blanks) {
            for (int[] d : dirs) {
                int nr = cell[0]+d[0], nc = cell[1]+d[1];
                if (nr < 0 || nr >= rows || nc < 0 || nc >= cols) continue;
                if (b[nr][nc].type != CommonCell.CellType.NUMBER) continue;
                String key = nr + "," + nc;
                if (checked.contains(key)) continue;
                checked.add(key);
                int count = 0;
                for (int[] d2 : dirs) {
                    int wr = nr+d2[0], wc = nc+d2[1];
                    if (wr >= 0 && wr < rows && wc >= 0 && wc < cols && b[wr][wc].bulb)
                        count++;
                }
                if (count != b[nr][nc].number) return false;
            }
        }
        return true;
    }

}