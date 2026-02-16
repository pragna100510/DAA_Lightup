/**
 * Common Cell class for the Light Up game
 * Used by all components to ensure compatibility
 */
public class CommonCell {
    public enum CellType { BLACK, NUMBER, BLANK }
    
    public CellType type;
    public int number;
    public boolean bulb;
    public boolean dot;
    public boolean lit;
    public int row, col;
    public int graphId;
    
    public CommonCell(CellType t, int num, int r, int c) {
        this.type = t;
        this.number = num;
        this.row = r;
        this.col = c;
        this.bulb = false;
        this.dot = false;
        this.lit = false;
        this.graphId = -1;
    }
    
    // Copy constructor
    public CommonCell(CommonCell other) {
        this.type = other.type;
        this.number = other.number;
        this.row = other.row;
        this.col = other.col;
        this.bulb = other.bulb;
        this.dot = other.dot;
        this.lit = other.lit;
        this.graphId = other.graphId;
    }
    
    public boolean isWall() { 
        return type == CellType.BLACK || type == CellType.NUMBER; 
    }
}
