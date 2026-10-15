package robot.commands;

import java.util.List;
import java.util.stream.Collectors;
import robot.Grid;
import robot.GridRegistry;

public final class ListGridsCommand implements Command {
    private final GridRegistry grids;

    /** Connects this command to the session's grids. */
    public ListGridsCommand(GridRegistry grids) {
        this.grids = grids;
    }

    /** Returns the command's accepted syntax. */
    @Override
    public String usage() {
        return "grid list";
    }

    /** Lists each initialized grid's own ID and dimensions in creation order. */
    @Override
    public CommandResult executeValidated(String[] args) {
        Arguments.count(args, 0, usage());
        List<Grid> initialized = grids.listGrids();
        if (initialized.isEmpty()) return CommandResult.success("No grids initialized.");
        return CommandResult.success(initialized.stream()
                .map(grid -> grid.id() + ": " + grid.rows() + " rows, " + grid.columns() + " columns.")
                .collect(Collectors.joining("\n")));
    }
}
