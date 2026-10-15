package robot.commands;

import java.util.Map;
import java.util.stream.Collectors;

public final class HelpCommand implements Command {
    private final Map<String, Command> commands;

    /** Uses the registry so newly registered commands appear in help automatically. */
    public HelpCommand(Map<String, Command> commands) {
        this.commands = commands;
    }

    /** Returns the help command syntax. */
    @Override
    public String usage() {
        return "help";
    }

    /** Lists the syntax of every registered command. */
    @Override
    public CommandResult executeValidated(String[] args) {
        Arguments.count(args, 0, usage());
        return CommandResult.success(commands.values().stream().map(Command::usage).collect(Collectors.joining("\n")));
    }
}
