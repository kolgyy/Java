package backend.academy.linktracker.bot.command;

public interface Command {

    String name();

    String description();

    String execute(Long chadId, String[] args);
}
