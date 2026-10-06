package io.github.harshjoeyit.cli.command;

import org.springframework.shell.command.annotation.Command;

@Command(group = "General Commands")
public class AppCommands {

    @Command(command = "ping", description = "Check if the interactive CLI is active")
    public String ping() {
        return "pong! ExpenseSimplified CLI is active.";
    }
}
