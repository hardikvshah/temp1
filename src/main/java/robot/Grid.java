package robot;

import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;

public final class Grid {
    private final String id;
    private int rows;
    private int columns;
    private long nextId = 1;
    private final Map<String, Robot> robots = new LinkedHashMap<>();
    private final Set<String> obstacles = new HashSet<>();

    /** Assigns the grid's immutable identity before initialization. */
    public Grid(String id) {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("Grid ID must not be blank.");
        this.id = id;
    }

    /** Returns the grid's identity. */
    public String id() {
        return id;
    }

    /** Returns the initialized row count. */
    public int rows() {
        requireInitialized();
        return rows;
    }

    /** Returns the initialized column count. */
    public int columns() {
        requireInitialized();
        return columns;
    }

    /** Creates the grid once, using positive dimensions. */
    public void initialize(int rows, int columns) {
        if (isInitialized()) throw new IllegalArgumentException("Grid is already initialized.");
        if (rows <= 0 || columns <= 0) {
            throw new IllegalArgumentException("Grid dimensions must be positive.");
        }
        this.rows = rows;
        this.columns = columns;
    }

    /** Reports whether initialization has succeeded. */
    public boolean isInitialized() {
        return rows > 0;
    }

    /** Rejects operations that require an initialized grid. */
    public void requireInitialized() {
        if (!isInitialized()) throw new IllegalArgumentException("Grid not initialized.");
    }

    /** Adds a robot with a unique ID; shared positions are allowed. */
    public Robot addRobot(int x, int y) {
        requireValidPosition(x, y);
        if (obstacles.contains(x + "," + y)) {
            throw new IllegalArgumentException("Cannot place a robot on an obstacle.");
        }
        Robot robot = new Robot("R" + nextId++, x, y);
        robots.put(robot.id(), robot);
        return robot;
    }

    /** Adds an obstacle only to an empty, valid cell. */
    public void addObstacle(int x, int y) {
        requireValidPosition(x, y);
        String key = x + "," + y;
        if (obstacles.contains(key)) throw new IllegalArgumentException("Obstacle already exists.");
        if (robots.values().stream().anyMatch(robot -> robot.x() == x && robot.y() == y)) {
            throw new IllegalArgumentException("Cannot place an obstacle on a robot.");
        }
        obstacles.add(key);
    }

    /** Removes an obstacle if present; deleting an empty valid cell succeeds. */
    public void deleteObstacle(int x, int y) {
        requireValidPosition(x, y);
        obstacles.remove(x + "," + y);
    }

    /** Returns the robot or reports an unknown ID. */
    public Robot status(String id) {
        requireInitialized();
        Robot robot = robots.get(id);
        if (robot == null) throw new IllegalArgumentException("Robot not found: " + id);
        return robot;
    }

    /** Returns an immutable snapshot of all robots in creation order. */
    public List<Robot> listRobots() {
        requireInitialized();
        return List.copyOf(robots.values());
    }

    /** Moves through available cells, stopping before obstacles or at the boundary. */
    public Robot move(String id, String direction, int units) {
        Robot robot = status(id);
        if (units < 0) throw new IllegalArgumentException("Units must be nonnegative.");
        int dx = 0;
        int dy = 0;
        switch (direction) {
            case "up" -> dy = 1;
            case "down" -> dy = -1;
            case "left" -> dx = -1;
            case "right" -> dx = 1;
            default -> throw new IllegalArgumentException("Direction must be up, down, left, or right.");
        }
        int x = robot.x();
        int y = robot.y();
        int available = dx > 0 ? columns - x : dx < 0 ? x - 1 : dy > 0 ? rows - y : y - 1;
        // Bound the loop before stepping, even for extremely large requested distances.
        int steps = Math.min(units, available);
        for (int step = 0; step < steps; step++) {
            // Check each cell so a robot cannot jump over an obstacle.
            if (!canEnter(x + dx, y + dy)) break;
            x += dx;
            y += dy;
        }
        Robot moved = new Robot(id, x, y);
        robots.put(id, moved);
        return moved;
    }

    /** Removes an existing robot without reusing its ID. */
    public void deleteRobot(String id) {
        status(id);
        robots.remove(id);
    }

    /** Allows movement only into cells within bounds and without obstacles. */
    private boolean canEnter(int x, int y) {
        return isWithinBounds(x, y) && !obstacles.contains(x + "," + y);
    }

    /** Validates initialization and coordinates before any placement or removal. */
    private void requireValidPosition(int x, int y) {
        requireInitialized();
        if (!isWithinBounds(x, y)) throw new IllegalArgumentException("Position is outside the grid.");
    }

    /** Checks one-based coordinates with (1, 1) at the bottom-left. */
    private boolean isWithinBounds(int x, int y) {
        return x >= 1 && x <= columns && y >= 1 && y <= rows;
    }

    /** Renders borders, coordinates, robot IDs, and obstacles marked with a star. */
    public String print() {
        requireInitialized();
        Map<String, StringJoiner> cells = new LinkedHashMap<>();
        int width = Math.max(4, Integer.toString(columns).length() + 2);
        for (Robot robot : robots.values()) {
            String key = robot.x() + "," + robot.y();
            cells.computeIfAbsent(key, ignored -> new StringJoiner(",")).add(robot.id());
            width = Math.max(width, cells.get(key).length() + 2);
        }
        int labelWidth = Integer.toString(rows).length() + 3;
        String indent = " ".repeat(labelWidth);
        String border = indent + "+" + ("-".repeat(width) + "+").repeat(columns);
        StringBuilder output = new StringBuilder(border).append('\n');
        for (int y = rows; y >= 1; y--) {
            output.append(String.format("%" + labelWidth + "s", "y=" + y + " ")).append('|');
            for (int x = 1; x <= columns; x++) {
                StringJoiner occupants = cells.get(x + "," + y);
                String value = occupants == null ? "" : occupants.toString();
                if (obstacles.contains(x + "," + y)) value = "*";
                output.append(center(value, width)).append('|');
            }
            output.append('\n').append(border).append('\n');
        }
        output.append(indent).append(' ');
        for (int x = 1; x <= columns; x++) output.append(center(Integer.toString(x), width)).append(' ');
        return output.append('x').toString();
    }

    /** Pads a label to a consistent display width. */
    private static String center(String value, int width) {
        int left = (width - value.length()) / 2;
        return " ".repeat(left) + value + " ".repeat(width - value.length() - left);
    }
}
