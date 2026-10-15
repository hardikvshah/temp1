package robot.commands;

import robot.Grid;
import robot.GridRegistry;
import robot.Robot;

public final class StatusCommand implements Command {
    private final GridRegistry grids;

    /** Connects this command to the session's current grid. */
    public StatusCommand(GridRegistry grids) {
        this.grids = grids;
    }

    /** Returns the command's accepted syntax. */
    @Override
    public String usage() {
        return "robot status <robot-id>";
    }

    /** Validates arguments and performs the grid operation. */
    @Override
    public CommandResult executeValidated(String[] args) {
        Grid grid = grids.currentGrid();
        Arguments.count(args, 1, usage());
        Robot robot = grid.status(args[0]);
        return CommandResult.success(robot.id() + " at (" + robot.x() + ", " + robot.y() + ")");
    }
}
