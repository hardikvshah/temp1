package robot.commands;

import robot.Grid;
import robot.GridRegistry;

public final class PrintCommand implements Command {
    private final GridRegistry grids;

    /** Connects this command to the session's current grid. */
    public PrintCommand(GridRegistry grids) {
        this.grids = grids;
    }

    /** Returns the command's accepted syntax. */
    @Override
    public String usage() {
        return "grid print";
    }

    /** Validates arguments and performs the grid operation. */
    @Override
    public CommandResult executeValidated(String[] args) {
        Grid grid = grids.currentGrid();
        Arguments.count(args, 0, usage());
        return CommandResult.success(grid.print());
    }
}
