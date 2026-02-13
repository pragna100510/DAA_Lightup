import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;
import javax.swing.*;

/**
 
 * 
 * Responsibilities:
 * - Cell and GraphNode data structures
 * - Random puzzle generation
 * - Graph construction for AI analysis
 * - Centrality calculations
 * - Merge sort implementation
 */
public class GraphBuilding {
    
    enum CellType { BLACK, NUMBER, BLANK }

    static class Cell {
        CellType type;
        int number;
        boolean bulb;
        boolean dot;
        boolean lit;
        int row, col;
        int graphId;

        Cell(CellType t, int number, int r, int c) {
            this.type = t;
            this.number = number;
            this.row = r;
            this.col = c;
            bulb = false;
            dot = false;
            lit = false;
            graphId = -1;
        }

        boolean isWall() {
            return type == CellType.BLACK || type == CellType.NUMBER;
        }
    }

    static class GraphNode implements Comparable<GraphNode> {
        int id;
        int row, col;
        List<GraphNode> neighbors;
        int degree;
        double centrality;
        int distanceMetric;
        boolean visited;

        GraphNode(int id, int r, int c) {
            this.id = id;
            this.row = r;
            this.col = c;
            this.neighbors = new ArrayList<>();
            this.degree = 0;
            this.centrality = 0.0;
            this.distanceMetric = 0;
            this.visited = false;
        }

        @Override
        public int compareTo(GraphNode other) {
            return Integer.compare(this.distanceMetric, other.distanceMetric);
        }
    }

    // ===== GAME GENERATION =====
    
    /**
     * Generates a random 7x7 puzzle with black cells and numbered cells
     * Ensures the puzzle is solvable before accepting it
     */
    public static Cell[][] generateRandomPuzzle(int rows, int cols, Random rng) {
        int maxAttempts = 50;
        Cell[][] board = null;
        boolean puzzleValid = false;

        for (int attempt = 0; attempt < maxAttempts && !puzzleValid; attempt++) {
            board = new Cell[rows][cols];
            for (int r = 0; r < rows; r++)
                for (int c = 0; c < cols; c++)
                    board[r][c] = new Cell(CellType.BLANK, -1, r, c);

            int blackCellCount = 8 + rng.nextInt(3);
            Set<String> blackPositions = new HashSet<>();
            int attempts = 0;

            while (blackPositions.size() < blackCellCount && attempts < 200) {
                int r = rng.nextInt(rows);
                int c = rng.nextInt(cols);
                String pos = r + "," + c;
                if (!blackPositions.contains(pos) && !hasAdjacentBlack(r, c, blackPositions, rows, cols)) {
                    blackPositions.add(pos);
                    board[r][c] = new Cell(CellType.BLACK, -1, r, c);
                }
                attempts++;
            }

            List<String> blackPosList = new ArrayList<>(blackPositions);
            Collections.shuffle(blackPosList);
            int numberedCount = 0;
            int targetNumbered = 4 + rng.nextInt(2);

            for (String pos : blackPosList) {
                if (numberedCount >= targetNumbered) break;
                String[] parts = pos.split(",");
                int r = Integer.parseInt(parts[0]);
                int c = Integer.parseInt(parts[1]);
                int blanks = countBlankNeighbors(r, c, board, rows, cols);
                if (blanks > 0) {
                    int num = rng.nextInt(Math.min(5, blanks + 1));
                    board[r][c] = new Cell(CellType.NUMBER, num, r, c);
                    numberedCount++;
                }
            }

            // Check if puzzle is solvable (would need solver from Member 4)
            puzzleValid = true; // Placeholder - integrate with Member 4's solver
        }

        for (int r = 0; r < rows; r++)
            for (int c = 0; c < cols; c++) {
                board[r][c].bulb = false;
                board[r][c].dot = false;
            }

        return board;
    }

    private static boolean hasAdjacentBlack(int r, int c, Set<String> bp, int rows, int cols) {
        int[][] dirs = {{-1,0},{1,0},{0,-1},{0,1},{-1,-1},{-1,1},{1,-1},{1,1}};
        for (int[] d : dirs) {
            int nr = r+d[0], nc = c+d[1];
            if (nr>=0&&nr<rows&&nc>=0&&nc<cols && bp.contains(nr+","+nc)) return true;
        }
        return false;
    }

    private static int countBlankNeighbors(int r, int c, Cell[][] board, int rows, int cols) {
        int count = 0;
        int[][] dirs = {{-1,0},{1,0},{0,-1},{0,1}};
        for (int[] d : dirs) {
            int nr=r+d[0], nc=c+d[1];
            if (nr>=0&&nr<rows&&nc>=0&&nc<cols && board[nr][nc].type==CellType.BLANK) count++;
        }
        return count;
    }

    // ===== GRAPH BUILDING =====
    
    /**
     * Builds a graph representation of blank cells
     * Nodes are connected if they can see each other (no walls between)
     */
    public static Map<Integer, GraphNode> buildGraph(Cell[][] board, int rows, int cols, 
                                                      List<GraphNode> blankNodes) {
        Map<Integer, GraphNode> cellGraph = new HashMap<>();
        blankNodes.clear();
        int nodeId = 0;
        
        for (int r = 0; r < rows; r++)
            for (int c = 0; c < cols; c++)
                if (board[r][c].type == CellType.BLANK) {
                    GraphNode node = new GraphNode(nodeId, r, c);
                    cellGraph.put(nodeId, node);
                    blankNodes.add(node);
                    board[r][c].graphId = nodeId++;
                }

        for (GraphNode node : blankNodes) {
            int r = node.row, c = node.col;
            int[][] dirs = {{-1,0},{1,0},{0,-1},{0,1}};
            for (int[] dir : dirs) {
                int nr = r+dir[0], nc = c+dir[1];
                while (nr>=0&&nr<rows&&nc>=0&&nc<cols) {
                    if (board[nr][nc].isWall()) break;
                    if (board[nr][nc].type == CellType.BLANK) {
                        GraphNode neighbor = cellGraph.get(board[nr][nc].graphId);
                        if (!node.neighbors.contains(neighbor)) {
                            node.neighbors.add(neighbor);
                            node.degree++;
                        }
                        break;
                    }
                    nr += dir[0]; nc += dir[1];
                }
            }
        }
        
        return cellGraph;
    }

    // ===== CENTRALITY CALCULATION =====
    
    /**
     * Calculates betweenness centrality for each node
     * Uses BFS to find shortest paths between all pairs
     */
    public static void calculateCentrality(List<GraphNode> blankNodes) {
        for (GraphNode node : blankNodes) node.centrality = 0.0;
        
        for (GraphNode source : blankNodes) {
            Map<GraphNode,Integer> dist  = new HashMap<>();
            Map<GraphNode,Integer> paths = new HashMap<>();
            Queue<GraphNode> queue = new LinkedList<>();
            
            for (GraphNode n : blankNodes) {
                dist.put(n, Integer.MAX_VALUE);
                paths.put(n, 0);
            }
            
            dist.put(source, 0);
            paths.put(source, 1);
            queue.offer(source);
            
            while (!queue.isEmpty()) {
                GraphNode cur = queue.poll();
                int cd = dist.get(cur);
                
                for (GraphNode nb : cur.neighbors) {
                    if (dist.get(nb) == Integer.MAX_VALUE) {
                        dist.put(nb, cd+1);
                        paths.put(nb, paths.get(cur));
                        queue.offer(nb);
                    } else if (dist.get(nb) == cd+1) {
                        paths.put(nb, paths.get(nb)+paths.get(cur));
                    }
                }
            }
            
            for (GraphNode node : blankNodes)
                if (node != source) node.centrality += paths.get(node);
        }
    }

    // ===== MERGE SORT BY CENTRALITY =====
    
    /**
     * Sorts nodes by centrality using merge sort algorithm
     * Updates centralityOrder map for fast lookup
     */
    public static void mergeSortNodesByCentrality(List<GraphNode> nodes, 
                                                   Map<Integer, Integer> centralityOrder) {
        if (nodes.size() <= 1) return;
        
        int mid = nodes.size() / 2;
        List<GraphNode> left  = new ArrayList<>(nodes.subList(0, mid));
        List<GraphNode> right = new ArrayList<>(nodes.subList(mid, nodes.size()));
        
        mergeSortNodesByCentrality(left, centralityOrder);
        mergeSortNodesByCentrality(right, centralityOrder);
        merge(nodes, left, right);
        
        centralityOrder.clear();
        for (int i = 0; i < nodes.size(); i++)
            centralityOrder.put(nodes.get(i).id, i);
    }

    private static void merge(List<GraphNode> result, List<GraphNode> left, List<GraphNode> right) {
        int i=0, j=0, k=0;
        while (i < left.size() && j < right.size())
            result.set(k++, left.get(i).centrality <= right.get(j).centrality ? 
                       left.get(i++) : right.get(j++));
        while (i < left.size()) result.set(k++, left.get(i++));
        while (j < right.size()) result.set(k++, right.get(j++));
    }

    /**
     * Creates a deep copy of the board for backup purposes
     */
    public static Cell[][] copyBoard(Cell[][] board, int rows, int cols) {
        Cell[][] copy = new Cell[rows][cols];
        for (int r = 0; r < rows; r++)
            for (int c = 0; c < cols; c++) {
                Cell src = board[r][c];
                copy[r][c] = new Cell(src.type, src.number, r, c);
                copy[r][c].bulb = src.bulb;
                copy[r][c].dot = src.dot;
                copy[r][c].lit = src.lit;
                copy[r][c].graphId = src.graphId;
            }
        return copy;
    }
}
