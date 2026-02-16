import java.util.*;

/**
 * Responsibilities:
 * - Cell and GraphNode data structures
 * - Random puzzle generation
 * - Graph construction for AI analysis
 * - Centrality calculations
 * - Merge sort implementation
 */
public class GraphBuilding {

    public static class GraphNode implements Comparable<GraphNode> {
        public int id;
        public int row, col;
        public List<GraphNode> neighbors;
        public int degree;
        public double centrality;
        public int distanceMetric;
        public boolean visited;

        public GraphNode(int id, int r, int c) {
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
    public static CommonCell[][] generateRandomPuzzle(int rows, int cols, Random rng) {

    int maxAttempts = 200;

    for (int attempt = 0; attempt < maxAttempts; attempt++) {

        CommonCell[][] board = new CommonCell[rows][cols];

        // 1️⃣ Initialize blanks
        for (int r = 0; r < rows; r++)
            for (int c = 0; c < cols; c++)
                board[r][c] = new CommonCell(CommonCell.CellType.BLANK, -1, r, c);

        // 2️⃣ Add black cells
        int blackCount = 5 + rng.nextInt(4); // 5–8 blacks
        Set<String> used = new HashSet<>();

        while (used.size() < blackCount) {
            int r = rng.nextInt(rows);
            int c = rng.nextInt(cols);
            String key = r + "," + c;

            if (!used.contains(key)) {
                used.add(key);
                board[r][c] = new CommonCell(CommonCell.CellType.BLACK, -1, r, c);
            }
        }

        // 3️⃣ Convert some blacks to numbers
        int[][] dirs = {{1,0},{-1,0},{0,1},{0,-1}};
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {

                if (board[r][c].type == CommonCell.CellType.BLACK &&
                    rng.nextDouble() < 0.6) {

                    int blankNeighbors = 0;

                    for (int[] d : dirs) {
                        int nr = r + d[0], nc = c + d[1];
                        if (nr>=0 && nr<rows && nc>=0 && nc<cols &&
                            board[nr][nc].type == CommonCell.CellType.BLANK)
                            blankNeighbors++;
                    }

                    int number = rng.nextInt(blankNeighbors + 1);
                    board[r][c] =
                        new CommonCell(CommonCell.CellType.NUMBER, number, r, c);
                }
            }
        }

        // 4️⃣ Reset bulbs/dots
        for (int r = 0; r < rows; r++)
            for (int c = 0; c < cols; c++) {
                board[r][c].bulb = false;
                board[r][c].dot = false;
                board[r][c].lit = false;
            }

        // 5️⃣ Check solvability
        if (Solver.isSolvable(board, rows, cols)) {
            return board;   // ✅ FOUND SOLVABLE
        }
    }

    // If somehow all attempts fail, fallback
    return createSimpleSolvablePuzzle(rows, cols);
}

    
    /**
     * Creates a simple guaranteed solvable puzzle as fallback
     */
    private static CommonCell[][] createSimpleSolvablePuzzle(int rows, int cols) {
        CommonCell[][] board = new CommonCell[rows][cols];
        
        // Initialize all blanks
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                board[r][c] = new CommonCell(CommonCell.CellType.BLANK, -1, r, c);
            }
        }
        
        // Add a few black cells in a pattern
        int[][] blackPositions = {{1,1}, {1,5}, {3,3}, {5,1}, {5,5}};
        for (int[] pos : blackPositions) {
            int r = pos[0];
            int c = pos[1];
            if (r < rows && c < cols) {
                board[r][c] = new CommonCell(CommonCell.CellType.BLACK, -1, r, c);
            }
        }
        
        // Add numbered cells with reasonable numbers
        int[][] numberPositions = {{1,3}, {3,5}, {5,3}};
        int[] numbers = {2, 1, 2};
        
        for (int i = 0; i < numberPositions.length; i++) {
            int r = numberPositions[i][0];
            int c = numberPositions[i][1];
            if (r < rows && c < cols) {
                board[r][c] = new CommonCell(CommonCell.CellType.NUMBER, numbers[i], r, c);
            }
        }
        
        return board;
    }

    private static boolean hasAdjacentBlack(int r, int c, Set<String> bp, int rows, int cols) {
        int[][] dirs = {{-1,0},{1,0},{0,-1},{0,1}};
        for (int[] d : dirs) {
            int nr = r+d[0], nc = c+d[1];
            if (nr>=0&&nr<rows&&nc>=0&&nc<cols && bp.contains(nr+","+nc)) return true;
        }
        return false;
    }

    private static int countBlankNeighbors(int r, int c, CommonCell[][] board, int rows, int cols) {
        int count = 0;
        int[][] dirs = {{-1,0},{1,0},{0,-1},{0,1}};
        for (int[] d : dirs) {
            int nr=r+d[0], nc=c+d[1];
            if (nr>=0&&nr<rows&&nc>=0&&nc<cols && board[nr][nc].type==CommonCell.CellType.BLANK) count++;
        }
        return count;
    }

    // ===== GRAPH BUILDING =====
    
    /**
     * Builds a graph representation of blank cells
     * Nodes are connected if they can see each other (no walls between)
     */
    public static Map<Integer, GraphNode> buildGraph(CommonCell[][] board, int rows, int cols, 
                                                      List<GraphNode> blankNodes) {
        Map<Integer, GraphNode> cellGraph = new HashMap<>();
        blankNodes.clear();
        int nodeId = 0;
        
        for (int r = 0; r < rows; r++)
            for (int c = 0; c < cols; c++)
                if (board[r][c].type == CommonCell.CellType.BLANK) {
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
                    if (board[nr][nc].type == CommonCell.CellType.BLANK) {
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
                        paths.put(nb, paths.get(nb) + paths.get(cur));
                    }
                }
            }
            
            for (GraphNode node : blankNodes) {
                if (node != source) {
                    node.centrality += paths.get(node);
                }
            }
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
        
        mergeSortHelper(nodes, 0, nodes.size() - 1);
        
        centralityOrder.clear();
        for (int i = 0; i < nodes.size(); i++)
            centralityOrder.put(nodes.get(i).id, i);
    }
    
    private static void mergeSortHelper(List<GraphNode> nodes, int left, int right) {
        if (left < right) {
            int mid = left + (right - left) / 2;
            mergeSortHelper(nodes, left, mid);
            mergeSortHelper(nodes, mid + 1, right);
            merge(nodes, left, mid, right);
        }
    }
    
    private static void merge(List<GraphNode> nodes, int left, int mid, int right) {
        List<GraphNode> temp = new ArrayList<>();
        int i = left, j = mid + 1;
        
        while (i <= mid && j <= right) {
            if (nodes.get(i).centrality >= nodes.get(j).centrality) {
                temp.add(nodes.get(i));
                i++;
            } else {
                temp.add(nodes.get(j));
                j++;
            }
        }
        
        while (i <= mid) {
            temp.add(nodes.get(i));
            i++;
        }
        
        while (j <= right) {
            temp.add(nodes.get(j));
            j++;
        }
        
        for (int k = 0; k < temp.size(); k++) {
            nodes.set(left + k, temp.get(k));
        }
    }

    /**
     * Creates a deep copy of the board for backup purposes
     */
    public static CommonCell[][] copyBoard(CommonCell[][] board, int rows, int cols) {
        CommonCell[][] copy = new CommonCell[rows][cols];
        for (int r = 0; r < rows; r++)
            for (int c = 0; c < cols; c++) {
                copy[r][c] = new CommonCell(board[r][c]);
            }
        return copy;
    }
}
