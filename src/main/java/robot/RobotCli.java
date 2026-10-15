package robot;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.Reader;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.Locale;
import java.util.StringJoiner;
import java.util.stream.Collectors;
import robot.commands.*;

public final class RobotCli {
    private final Map<String, Command> commands = new LinkedHashMap<>();
    private final GridRegistry grids = new GridRegistry();
    private boolean running = true;

    /** Registers commands against the session's grids and current selection. */
    public RobotCli() {
        commands.put("grid init", new InitGridCommand(grids));
        commands.put("grid list", new ListGridsCommand(grids));
        commands.put("grid checkout", new CheckoutGridCommand(grids));
        commands.put("robot add", new AddRobotCommand(grids));
        commands.put("robot move", new MoveRobotCommand(grids));
        commands.put("robot delete", new DeleteRobotCommand(grids));
        commands.put("robot status", new StatusCommand(grids));
        commands.put("robot list", new ListRobotsCommand(grids));
        commands.put("grid print", new PrintCommand(grids));
        commands.put("obstacle add", new AddObstacleCommand(grids));
        commands.put("obstacle delete", new DeleteObstacleCommand(grids));
        commands.put("help", new HelpCommand(commands));
        commands.put("exit", new ExitCommand(this::stop));
    }

    /** Runs semicolon-separated commands in order, stopping at the first error or exit. */
    public CommandResult execute(String line) {
        StringJoiner results = new StringJoiner("\n");
        int statusCode = 0;
        for (String command : line.split(";")) {
            if (!running) break;
            CommandResult result = executeCommand(command);
            if (!result.message().isEmpty()) results.add(result.message());
            statusCode = result.statusCode();
            if (statusCode != 0) break;
        }
        return new CommandResult(statusCode, results.toString());
    }

    /** Dispatches one command, converting user mistakes into recoverable errors. */
    private CommandResult executeCommand(String line) {
        if (!running) return CommandResult.success("");
        String trimmed = line.trim();
        if (trimmed.isEmpty()) return CommandResult.success("");
        String[] tokens = trimmed.split("\\s+");
        if (tokens.length == 2 && isHelpFlag(tokens[1])) {
            String resourceHelp = commands.entrySet().stream()
                    .filter(entry -> entry.getKey().startsWith(tokens[0] + " "))
                    .map(entry -> entry.getValue().usage())
                    .collect(Collectors.joining("\n"));
            if (!resourceHelp.isEmpty()) return CommandResult.success(resourceHelp);
        }
        String name = tokens.length >= 2 && commands.containsKey(tokens[0] + " " + tokens[1])
                ? tokens[0] + " " + tokens[1] : tokens[0];
        Command command = commands.get(name);
        if (command == null) return unknownCommand(tokens);
        int nameLength = name.contains(" ") ? 2 : 1;
        String[] args = Arrays.copyOfRange(tokens, nameLength, tokens.length);
        // Help bypasses execution, including initialization guards and state changes.
        if (Arrays.stream(args).anyMatch(RobotCli::isHelpFlag)) {
            return CommandResult.success("Usage: " + command.usage());
        }
        return command.execute(args);
    }

    /** Suggests the closest registered names within two edits, ignoring command arguments. */
    private CommandResult unknownCommand(String[] tokens) {
        List<String> suggestions = new ArrayList<>();
        int closest = 2;
        String resource = tokens[0].toLowerCase(Locale.ROOT);
        boolean knownResource = commands.keySet().stream().anyMatch(name -> name.startsWith(resource + " "));
        for (String name : commands.keySet()) {
            boolean hasVerb = name.contains(" ");
            // Invalid flags and missing verbs should not look like misspelled commands.
            if (hasVerb && (tokens.length < 2 || tokens[1].startsWith("-"))) continue;
            if (!hasVerb && tokens.length > 1 && !isHelpFlag(tokens[1])) continue;
            if (knownResource && !name.startsWith(resource + " ")) continue;
            String input = hasVerb ? tokens[0] + " " + tokens[1] : tokens[0];
            if (Math.abs(input.length() - name.length()) > closest) continue;
            int distance = editDistance(input.toLowerCase(Locale.ROOT), name);
            if (distance > closest) continue;
            if (distance < closest) {
                closest = distance;
                suggestions.clear();
            }
            suggestions.add(name);
        }
        String message = "Error: Unknown command. Type help for commands.";
        if (!suggestions.isEmpty()) message += " Did you mean " + String.join(" or ", suggestions) + "?";
        return CommandResult.error(message);
    }

    /** Computes Levenshtein distance using insertion, deletion, and substitution costs of one. */
    private static int editDistance(String input, String candidate) {
        int[] previous = new int[candidate.length() + 1];
        for (int column = 0; column <= candidate.length(); column++) previous[column] = column;
        for (int row = 1; row <= input.length(); row++) {
            int[] current = new int[candidate.length() + 1];
            current[0] = row;
            for (int column = 1; column <= candidate.length(); column++) {
                int substitution = input.charAt(row - 1) == candidate.charAt(column - 1) ? 0 : 1;
                current[column] = Math.min(Math.min(previous[column] + 1, current[column - 1] + 1),
                        previous[column - 1] + substitution);
            }
            previous = current;
        }
        return previous[candidate.length()];
    }

    /** Recognizes the supported help flags at both resource and command levels. */
    private static boolean isHelpFlag(String argument) {
        return argument.equals("--help") || argument.equals("-h");
    }

    /** Processes commands until exit or end-of-input without closing the caller's streams. */
    public void run(Reader input, PrintWriter output) throws IOException {
        BufferedReader reader = new BufferedReader(input);
        while (running) {
            output.print(grids.currentGridId().isEmpty() ? "> " : "[" + grids.currentGridId() + "] > ");
            output.flush();
            String line = reader.readLine();
            if (line == null) break;
            CommandResult result = execute(line);
            if (!result.message().isEmpty()) output.println(result.message());
            output.flush();
        }
    }

    /** Ends the current interactive session. */
    private void stop() {
        running = false;
    }

    /** Starts a session using standard input and output. */
    public static void main(String[] args) throws IOException {
        PrintWriter output = new PrintWriter(System.out, true);
        output.println("Robot CLI. Type help for commands.");
        new RobotCli().run(new InputStreamReader(System.in), output);
    }
}
