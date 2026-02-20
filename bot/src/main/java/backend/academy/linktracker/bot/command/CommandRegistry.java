package backend.academy.linktracker.bot.command;

import org.springframework.stereotype.Component;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class CommandRegistry {

    private final Map<String, Command> commands;
    private final UnknownCommand unknownCommand;

    public CommandRegistry(
        List<UserCommand> commandList,
        UnknownCommand unknownCommand
    ) {
        this.unknownCommand = unknownCommand;

        this.commands = commandList.stream()
            .collect(Collectors.toUnmodifiableMap(
                UserCommand::name,
                command -> command
            ));
    }

    public Command getCommand(String name) {
        return commands.getOrDefault(name, unknownCommand);
    }

    public Collection<Command> getCommands() {
        return commands.values();
    }
}
