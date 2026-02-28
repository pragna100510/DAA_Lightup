import java.util.*;

public class BacktrackingAIMove {

    // ── ADAPTIVE SOLUTION CACHE ──────────────────────────────────────────────
    // Persisted between turns so the AI remembers its planned solution.
    // When the user plays a move that fits the plan, the AI just continues it.
    // When the user deviates, the cache is invalidated and re-solved from scratch.

    private static boolean[][] cachedSolution = null;
    private static int cachedRows = -1, cachedCols = -1;

    /**
     * Call this from LightUp when New Game or Restart is clicked,
     * so the AI starts fresh with no stale plan.
     */
    public static void clearCache() {
        cachedSolution = null;
        cachedRows = -1;
        cachedCols = -1;
    }

    /**
     * Cache is valid if every bulb currently on the board also exists in the
     * cached solution. If the user placed a bulb outside the plan, the cache
     * is stale and must be discarded.
     */
    private static boolean isCacheValid(CommonCell[][] board, int rows, int cols) {
        if (cachedSolution == null) return false;
        if (cachedRows != rows || cachedCols != cols) return false;
        for (int r = 0; r < rows; r++)
            for (int c = 0; c < cols; c++)
                if (board[r][c].bulb && !cachedSolution[r][c])
                    return false; // user placed a bulb outside the plan
        return true;
    }

    // ── MAIN ENTRY POINT ─────────────────────────────────────────────────────

    public static int[] makeMove(CommonCell[][] board, int rows, int cols) {

        // Fix any existing violation first (always, regardless of cache)
        for (int r = 0; r < rows; r++)
            for (int c = 0; c < cols; c++)
                if (board[r][c].bulb && isViolatingBulb(board, rows, cols, r, c))
                    return new int[]{r, c, 1};

        // If cache is stale or missing, re-solve from current board state
        if (!isCacheValid(board, rows, cols)) {
            CommonCell[][] workBoard = deepCopy(board, rows, cols);
            recomputeLighting(workBoard, rows, cols);
            applyForcedDeductions(workBoard, rows, cols);

            boolean solved = btSolve(workBoard, rows, cols);

            if (!solved)
                return GreedyAIMove.makeMove(board, rows, cols);

            // Store the new solution plan
            cachedSolution = new boolean[rows][cols];
            cachedRows = rows;
            cachedCols = cols;
            for (int r = 0; r < rows; r++)
                for (int c = 0; c < cols; c++)
                    cachedSolution[r][c] = workBoard[r][c].bulb;
        }

        // Pick the next unplayed bulb from the cached plan
        for (int r = 0; r < rows; r++)
            for (int c = 0; c < cols; c++)
                if (cachedSolution[r][c] && !board[r][c].bulb)
                    return new int[]{r, c, 0};

        return new int[]{-1, -1, -1};
    }

    // ── CORE BACKTRACKING ────────────────────────────────────────────────────

    private static boolean btSolve(CommonCell[][] board, int rows, int cols) {
        recomputeLighting(board, rows, cols);

        if (isSolved(board, rows, cols)) return true;

        // Find first unlit blank cell (top-left to bottom-right)
        int targetR = -1, targetC = -1;
        outer:
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                if (board[r][c].type == CommonCell.CellType.BLANK
                        && !board[r][c].lit && !board[r][c].dot) {
                    targetR = r; targetC = c;
                    break outer;
                }
            }
        }

        if (targetR == -1) return false;

        // Get all cells that could illuminate (targetR, targetC)
        List<int[]> candidates = getCandidates(board, rows, cols, targetR, targetC);

        if (candidates.isEmpty()) return false;

        for (int[] cand : candidates) {
            int r = cand[0], c = cand[1];

            if (!canPlaceBulb(board, rows, cols, r, c)) continue;

            // PLACE
            board[r][c].bulb = true;
            recomputeLighting(board, rows, cols);

            // CHECK CONSTRAINTS
            if (!numberedExceeded(board, rows, cols) && !hasDeadCell(board, rows, cols)) {
                // RECURSE
                if (btSolve(board, rows, cols)) return true;
            }

            // BACKTRACK
            board[r][c].bulb = false;
            recomputeLighting(board, rows, cols);
        }

        return false;
    }

    // ── FORCED DEDUCTIONS (pre-processing before backtracking) ───────────────

    private static void applyForcedDeductions(CommonCell[][] board, int rows, int cols) {
        boolean changed = true;
        int[][] dirs = {{1,0},{-1,0},{0,1},{0,-1}};

        while (changed) {
            changed = false;
            recomputeLighting(board, rows, cols);

            for (int r = 0; r < rows; r++) {
                for (int c = 0; c < cols; c++) {
                    if (board[r][c].type != CommonCell.CellType.NUMBER) continue;

                    int need = board[r][c].number, placed = 0;
                    List<int[]> empties = new ArrayList<>();

                    for (int[] d : dirs) {
                        int nr = r+d[0], nc = c+d[1];
                        if (nr < 0 || nr >= rows || nc < 0 || nc >= cols) continue;
                        if (board[nr][nc].bulb) placed++;
                        else if (board[nr][nc].type == CommonCell.CellType.BLANK && !board[nr][nc].dot)
                            empties.add(new int[]{nr, nc});
                    }

                    // Exactly enough empty spots → all must be bulbs
                    if (placed < need && empties.size() == need - placed)
                        for (int[] e : empties)
                            if (!board[e[0]][e[1]].bulb) { board[e[0]][e[1]].bulb = true; changed = true; }

                    // Already satisfied → block remaining
                    if (placed == need)
                        for (int[] e : empties)
                            if (!board[e[0]][e[1]].dot) { board[e[0]][e[1]].dot = true; changed = true; }
                }
            }
        }
    }

    // ── CANDIDATE GENERATION ─────────────────────────────────────────────────

    private static List<int[]> getCandidates(CommonCell[][] board, int rows, int cols,
                                              int targetR, int targetC) {
        List<int[]> result = new ArrayList<>();

        if (canPlaceBulb(board, rows, cols, targetR, targetC))
            result.add(new int[]{targetR, targetC});

        int[][] dirs = {{-1,0},{1,0},{0,-1},{0,1}};
        for (int[] d : dirs) {
            int nr = targetR+d[0], nc = targetC+d[1];
            while (nr >= 0 && nr < rows && nc >= 0 && nc < cols) {
                if (board[nr][nc].isWall()) break;
                if (board[nr][nc].type == CommonCell.CellType.BLANK
                        && canPlaceBulb(board, rows, cols, nr, nc))
                    result.add(new int[]{nr, nc});
                nr += d[0]; nc += d[1];
            }
        }

        Set<String> seen = new HashSet<>();
        List<int[]> unique = new ArrayList<>();
        for (int[] e : result)
            if (seen.add(e[0]+","+e[1])) unique.add(e);

        return unique;
    }

    // ── PRUNING ──────────────────────────────────────────────────────────────

    private static boolean hasDeadCell(CommonCell[][] board, int rows, int cols) {
        for (int r = 0; r < rows; r++)
            for (int c = 0; c < cols; c++) {
                if (board[r][c].type != CommonCell.CellType.BLANK) continue;
                if (board[r][c].lit || board[r][c].dot) continue;

                boolean canBeLit = canPlaceBulb(board, rows, cols, r, c);
                if (!canBeLit) {
                    int[][] dirs = {{-1,0},{1,0},{0,-1},{0,1}};
                    outer:
                    for (int[] d : dirs) {
                        int nr = r+d[0], nc = c+d[1];
                        while (nr >= 0 && nr < rows && nc >= 0 && nc < cols) {
                            if (board[nr][nc].isWall()) break;
                            if (board[nr][nc].type == CommonCell.CellType.BLANK
                                    && canPlaceBulb(board, rows, cols, nr, nc)) {
                                canBeLit = true; break outer;
                            }
                            nr += d[0]; nc += d[1];
                        }
                    }
                }
                if (!canBeLit) return true;
            }
        return false;
    }

    private static boolean numberedExceeded(CommonCell[][] board, int rows, int cols) {
        int[][] dirs = {{1,0},{-1,0},{0,1},{0,-1}};
        for (int r = 0; r < rows; r++)
            for (int c = 0; c < cols; c++) {
                if (board[r][c].type != CommonCell.CellType.NUMBER) continue;
                int count = 0;
                for (int[] d : dirs) {
                    int nr = r+d[0], nc = c+d[1];
                    if (nr >= 0 && nr < rows && nc >= 0 && nc < cols && board[nr][nc].bulb) count++;
                }
                if (count > board[r][c].number) return true;
            }
        return false;
    }

    // ── SOLUTION CHECK ───────────────────────────────────────────────────────

    private static boolean isSolved(CommonCell[][] board, int rows, int cols) {
        int[][] dirs = {{1,0},{-1,0},{0,1},{0,-1}};
        for (int r = 0; r < rows; r++)
            for (int c = 0; c < cols; c++) {
                if (board[r][c].type == CommonCell.CellType.BLANK && !board[r][c].lit)
                    return false;
                if (board[r][c].type == CommonCell.CellType.NUMBER) {
                    int count = 0;
                    for (int[] d : dirs) {
                        int nr = r+d[0], nc = c+d[1];
                        if (nr >= 0 && nr < rows && nc >= 0 && nc < cols && board[nr][nc].bulb) count++;
                    }
                    if (count != board[r][c].number) return false;
                }
            }
        return true;
    }

    // ── UTILITIES ────────────────────────────────────────────────────────────

    private static CommonCell[][] deepCopy(CommonCell[][] board, int rows, int cols) {
        CommonCell[][] copy = new CommonCell[rows][cols];
        for (int r = 0; r < rows; r++)
            for (int c = 0; c < cols; c++)
                copy[r][c] = new CommonCell(board[r][c]);
        return copy;
    }

    private static void recomputeLighting(CommonCell[][] board, int rows, int cols) {
        for (int r = 0; r < rows; r++)
            for (int c = 0; c < cols; c++)
                board[r][c].lit = false;

        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                if (!board[r][c].bulb) continue;
                board[r][c].lit = true;
                for (int rr = r-1; rr >= 0;  rr--) { if (board[rr][c].isWall()) break; board[rr][c].lit = true; }
                for (int rr = r+1; rr < rows; rr++) { if (board[rr][c].isWall()) break; board[rr][c].lit = true; }
                for (int cc = c-1; cc >= 0;  cc--) { if (board[r][cc].isWall()) break; board[r][cc].lit = true; }
                for (int cc = c+1; cc < cols; cc++) { if (board[r][cc].isWall()) break; board[r][cc].lit = true; }
            }
        }
    }

    public static boolean isViolatingBulb(CommonCell[][] board, int rows, int cols, int r, int c) {
        if (!board[r][c].bulb) return false;
        int[][] dirs = {{1,0},{-1,0},{0,1},{0,-1}};
        for (int[] d : dirs) {
            int nr = r+d[0], nc = c+d[1];
            while (nr >= 0 && nr < rows && nc >= 0 && nc < cols) {
                if (board[nr][nc].isWall()) break;
                if (board[nr][nc].bulb) return true;
                nr += d[0]; nc += d[1];
            }
        }
        for (int[] d : dirs) {
            int nr = r+d[0], nc = c+d[1];
            if (nr < 0 || nr >= rows || nc < 0 || nc >= cols) continue;
            if (board[nr][nc].type == CommonCell.CellType.NUMBER) {
                int count = 0;
                for (int[] d2 : dirs) {
                    int rr = nr+d2[0], cc = nc+d2[1];
                    if (rr >= 0 && rr < rows && cc >= 0 && cc < cols && board[rr][cc].bulb) count++;
                }
                if (count > board[nr][nc].number) return true;
            }
        }
        return false;
    }

    public static boolean canPlaceBulb(CommonCell[][] board, int rows, int cols, int r, int c) {
        if (board[r][c].type != CommonCell.CellType.BLANK) return false;
        if (board[r][c].bulb || board[r][c].dot) return false;
        int[][] dirs = {{1,0},{-1,0},{0,1},{0,-1}};
        for (int[] d : dirs) {
            int nr = r+d[0], nc = c+d[1];
            while (nr >= 0 && nr < rows && nc >= 0 && nc < cols) {
                if (board[nr][nc].isWall()) break;
                if (board[nr][nc].bulb) return false;
                nr += d[0]; nc += d[1];
            }
        }
        for (int[] d : dirs) {
            int nr = r+d[0], nc = c+d[1];
            if (nr < 0 || nr >= rows || nc < 0 || nc >= cols) continue;
            if (board[nr][nc].type == CommonCell.CellType.NUMBER) {
                int count = 0;
                for (int[] d2 : dirs) {
                    int rr = nr+d2[0], cc = nc+d2[1];
                    if (rr >= 0 && rr < rows && cc >= 0 && cc < cols && board[rr][cc].bulb) count++;
                }
                boolean adj = false;
                for (int[] d2 : dirs)
                    if (nr+d2[0] == r && nc+d2[1] == c) { adj = true; break; }
                if (adj) count++;
                if (count > board[nr][nc].number) return false;
            }
        }
        return true;
    }
}
