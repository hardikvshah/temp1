package robot.commands;

import java.util.List;
import java.util.stream.Collectors;
import robot.Grid;
import robot.GridRegistry;
import robot.Robot;

public final class ListRobotsCommand implements Command {
    private final GridRegistry grids;

    /** Connects this command to the session's current grid. */
    public ListRobotsCommand(GridRegistry grids) {
        this.grids = grids;
    }

    /** Returns the command's accepted syntax. */
    @Override
    public String usage() {
        return "robot list";
    }

    /** Lists each robot's current position, or reports an empty grid. */
    @Override
    public CommandResult executeValidated(String[] args) {
        Grid grid = grids.currentGrid();
        Arguments.count(args, 0, usage());
        List<Robot> robots = grid.listRobots();
        if (robots.isEmpty()) return CommandResult.success("No robots in grid.");
        return CommandResult.success(robots.stream()
                .map(robot -> robot.id() + " at (" + robot.x() + ", " + robot.y() + ")")
                .collect(Collectors.joining("\n")));
    }
}
