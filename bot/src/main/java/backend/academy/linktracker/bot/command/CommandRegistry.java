package backend.academy.linktracker.bot.command;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class CommandRegistry {

    private final Map<String, Command> commands;
    private final UnknownCommand unknownCommand;

    public CommandRegistry(List<UserCommand> commandList, UnknownCommand unknownCommand) {
        this.unknownCommand = unknownCommand;

        this.commands =
                commandList.stream().collect(Collectors.toUnmodifiableMap(UserCommand::name, command -> command));

        log.atInfo()
                .addKeyValue("registerCommands", commands.keySet())
                .log("CommandRegistry initialized with commands");
    }

    public Command getCommand(String name) {
        Command command = commands.getOrDefault(name, unknownCommand);

        log.atDebug()
                .addKeyValue("requestedCommand", name)
                .addKeyValue("resolvedCommand", command.getClass().getSimpleName())
                .log("Command request processed");

        return command;
    }

    public Boolean hasCommand(String name) {
        return commands.containsKey(name);
    }

    public Collection<Command> getCommands() {
        return commands.values();
    }
}
