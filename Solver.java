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
}
