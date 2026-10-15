package robot.commands;

import robot.GridRegistry;

public final class InitGridCommand implements Command {
    private final GridRegistry grids;

    /** Connects this command to the session's grids. */
    public InitGridCommand(GridRegistry grids) {
        this.grids = grids;
    }

    /** Returns the command's accepted syntax. */
    @Override
    public String usage() {
        return "grid init <rows> <columns>";
    }

    /** Validates arguments and performs the grid operation. */
    @Override
    public CommandResult executeValidated(String[] args) {
        Arguments.count(args, 2, usage());
        String id = grids.initialize(Arguments.integer(args[0], "Rows"), Arguments.integer(args[1], "Columns"));
        return CommandResult.success("Grid " + id + " initialized: " + args[0] + " rows, " + args[1] + " columns.");
    }
}
