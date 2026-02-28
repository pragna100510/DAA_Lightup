import java.util.*;

/**
 * GreedyAIMove - Greedy algorithm for AI move selection.
 *
 * Strategy:
 *  1. Fix any rule violation immediately (remove one violating bulb).
 *  2. Place a forced bulb if a numbered cell needs exactly one more and
 *     has exactly one empty neighbour.
 *  3. Scan every placeable blank cell and score it by:
 *       score = (unlit cells it would illuminate) * 3
 *             + (how much it helps unsatisfied numbered neighbours) * 10
 *             + 5 bonus if the cell itself is currently unlit
 *     Pick the highest-scoring safe cell.
 *  4. If no good bulb placement exists, place a strategic dot.
 *
 * Returns: int[]{r, c, type}
 *   type 0 = place bulb, type 1 = remove bulb, type 2 = place dot, -1 = no move
 */
public class GreedyAIMove {

    /**
     * Main entry point. Returns {r, c, type}.
     */
    public static int[] makeMove(CommonCell[][] board, int rows, int cols) {

        // ── Phase 1: fix violations ──────────────────────────────────────────
        for (int r = 0; r < rows; r++)
            for (int c = 0; c < cols; c++)
                if (board[r][c].bulb && isViolatingBulb(board, rows, cols, r, c))
                    return new int[]{r, c, 1};   // remove this bulb

        // ── Phase 2: forced numbered-cell placement ──────────────────────────
        int[] forced = findForcedPlacement(board, rows, cols);
        if (forced != null && canPlaceBulb(board, rows, cols, forced[0], forced[1]))
            return new int[]{forced[0], forced[1], 0};

        // ── Phase 3: greedy best placement ───────────────────────────────────
        int bestScore = Integer.MIN_VALUE;
        int bestR = -1, bestC = -1;

        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                if (!canPlaceBulb(board, rows, cols, r, c)) continue;

                int litGain = countUnlitLightGain(board, rows, cols, r, c);
                int numHelp = countNumberedCellHelp(board, rows, cols, r, c);
                int score   = litGain * 3 + numHelp * 10;
                if (!board[r][c].lit) score += 5;

                if (score > bestScore) {
                    bestScore = score;
                    bestR = r;
                    bestC = c;
                }
            }
        }

        if (bestR != -1) return new int[]{bestR, bestC, 0};

        // ── Phase 4: strategic dot fallback ──────────────────────────────────
        int[] dot = findStrategicDot(board, rows, cols);
        if (dot != null) return new int[]{dot[0], dot[1], 2};

        return new int[]{-1, -1, -1};   // nothing to do
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /** Returns the first numbered-cell neighbour that needs exactly 1 more bulb
     *  in exactly 1 remaining empty spot. */
    private static int[] findForcedPlacement(CommonCell[][] board, int rows, int cols) {
        int[][] dirs = {{1,0},{-1,0},{0,1},{0,-1}};
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                if (board[r][c].type != CommonCell.CellType.NUMBER) continue;

                int need = board[r][c].number;
                int placed = 0;
                List<int[]> empties = new ArrayList<>();

                for (int[] d : dirs) {
                    int nr = r + d[0], nc = c + d[1];
                    if (nr < 0 || nr >= rows || nc < 0 || nc >= cols) continue;
                    if (board[nr][nc].bulb) placed++;
                    else if (board[nr][nc].type == CommonCell.CellType.BLANK
                             && !board[nr][nc].dot)
                        empties.add(new int[]{nr, nc});
                }

                if (need - placed == 1 && empties.size() == 1)
                    return empties.get(0);
            }
        }
        return null;
    }

    /** Counts how many currently-unlit cells a bulb at (r,c) would illuminate. */
    static int countUnlitLightGain(CommonCell[][] board, int rows, int cols,
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
    static int countNumberedCellHelp(CommonCell[][] board, int rows, int cols,
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
                if (rr >= 0 && rr < rows && cc >= 0 && cc < cols
                    && board[rr][cc].bulb) current++;
            }
            if (current < target) help += (target - current);
        }
        return help;
    }

    /** Finds a cell that is dangerous to leave empty (can see multiple directions). */
    private static int[] findStrategicDot(CommonCell[][] board, int rows, int cols) {
        int[][] dirs = {{1,0},{-1,0},{0,1},{0,-1}};
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                if (board[r][c].type != CommonCell.CellType.BLANK
                    || board[r][c].bulb || board[r][c].dot) continue;

                int spots = 0;
                for (int[] d : dirs) {
                    int nr = r + d[0], nc = c + d[1];
                    while (nr >= 0 && nr < rows && nc >= 0 && nc < cols) {
                        if (board[nr][nc].isWall()) break;
                        if (board[nr][nc].type == CommonCell.CellType.BLANK
                            && canPlaceBulb(board, rows, cols, nr, nc)) {
                            spots++;
                            break;
                        }
                        nr += d[0]; nc += d[1];
                    }
                }
                if (spots > 1) return new int[]{r, c};
            }
        }
        return null;
    }

    /** True if the bulb at (r,c) sees another bulb or over-satisfies a number. */
    public static boolean isViolatingBulb(CommonCell[][] board, int rows, int cols,
                                           int r, int c) {
        if (!board[r][c].bulb) return false;
        int[][] dirs = {{1,0},{-1,0},{0,1},{0,-1}};

        for (int[] d : dirs) {
            int nr = r + d[0], nc = c + d[1];
            while (nr >= 0 && nr < rows && nc >= 0 && nc < cols) {
                if (board[nr][nc].isWall()) break;
                if (board[nr][nc].bulb) return true;
                nr += d[0]; nc += d[1];
            }
        }
        for (int[] d : dirs) {
            int nr = r + d[0], nc = c + d[1];
            if (nr < 0 || nr >= rows || nc < 0 || nc >= cols) continue;
            if (board[nr][nc].type == CommonCell.CellType.NUMBER) {
                int count = 0;
                for (int[] d2 : dirs) {
                    int rr = nr + d2[0], cc = nc + d2[1];
                    if (rr >= 0 && rr < rows && cc >= 0 && cc < cols
                        && board[rr][cc].bulb) count++;
                }
                if (count > board[nr][nc].number) return true;
            }
        }
        return false;
    }

    /** True if a bulb may legally be placed at (r,c). */
    public static boolean canPlaceBulb(CommonCell[][] board, int rows, int cols,
                                        int r, int c) {
        if (board[r][c].type != CommonCell.CellType.BLANK) return false;
        if (board[r][c].bulb || board[r][c].dot) return false;

        int[][] dirs = {{1,0},{-1,0},{0,1},{0,-1}};
        for (int[] d : dirs) {
            int nr = r + d[0], nc = c + d[1];
            while (nr >= 0 && nr < rows && nc >= 0 && nc < cols) {
                if (board[nr][nc].isWall()) break;
                if (board[nr][nc].bulb) return false;
                nr += d[0]; nc += d[1];
            }
        }
        for (int[] d : dirs) {
            int nr = r + d[0], nc = c + d[1];
            if (nr < 0 || nr >= rows || nc < 0 || nc >= cols) continue;
            if (board[nr][nc].type == CommonCell.CellType.NUMBER) {
                int count = 0;
                for (int[] d2 : dirs) {
                    int rr = nr + d2[0], cc = nc + d2[1];
                    if (rr >= 0 && rr < rows && cc >= 0 && cc < cols
                        && board[rr][cc].bulb) count++;
                }
                boolean adj = false;
                for (int[] d2 : dirs)
                    if (nr + d2[0] == r && nc + d2[1] == c) { adj = true; break; }
                if (adj) count++;
                if (count > board[nr][nc].number) return false;
            }
        }
        return true;
    }
}
