package robot;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Holds independent grids and the selected grid for one CLI session. */
public final class GridRegistry {
    private final Map<String, Grid> grids = new LinkedHashMap<>();
    private long nextId = 1;
    private String currentId = "";

    /** Creates a grid without selecting it; failed initialization consumes no ID. */
    public String initialize(int rows, int columns) {
        Grid grid = new Grid("G" + nextId);
        grid.initialize(rows, columns);
        nextId++;
        grids.put(grid.id(), grid);
        return grid.id();
    }

    /** Selects an existing grid, preserving the previous selection on failure. */
    public void checkout(String id) {
        if (!grids.containsKey(id)) throw new IllegalArgumentException("Grid not found: " + id);
        currentId = id;
    }

    /** Returns an immutable list of initialized grids in creation order. */
    public List<Grid> listGrids() {
        return List.copyOf(grids.values());
    }

    /** Resolves the selected grid for each operation. */
    public Grid currentGrid() {
        Grid grid = grids.get(currentId);
        if (grid == null) throw new IllegalArgumentException("No grid checked out.");
        return grid;
    }

    /** Returns the selected ID, or an empty string before checkout. */
    public String currentGridId() {
        return currentId;
    }
}
