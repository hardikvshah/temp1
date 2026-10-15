package robot.commands;

import robot.Grid;
import robot.GridRegistry;

public final class DeleteObstacleCommand implements Command {
    private final GridRegistry grids;

    /** Connects this command to the session's current grid. */
    public DeleteObstacleCommand(GridRegistry grids) {
        this.grids = grids;
    }

    /** Returns the command's accepted syntax. */
    @Override
    public String usage() {
        return "obstacle delete <x> <y>";
    }

    /** Validates coordinates and applies the obstacle operation. */
    @Override
    public CommandResult executeValidated(String[] args) {
        Grid grid = grids.currentGrid();
        Arguments.count(args, 2, usage());
        int x = Arguments.integer(args[0], "X");
        int y = Arguments.integer(args[1], "Y");
        grid.deleteObstacle(x, y);
        return CommandResult.success("Obstacle removed at (" + x + ", " + y + ").");
    }
}
