/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package org.graalvm.profdiff.parser.args;

import java.util.Arrays;
import java.util.Formatter;

import org.graalvm.collections.EconomicMap;
import org.graalvm.profdiff.command.Command;

/**
 * Represents a program argument that parses a single string as the command name. Using that string,
 * it selects a matching {@link Command} according to its {@link Command#getName() command name}.
 * The {@link #getSelectedCommand() selected command}'s {@link Command#getArgumentParser() argument
 * parser} is then used to parse the rest of the arguments.
 */
public class CommandGroup extends Argument {

    /**
     * Individual commands in this group mapped by their name.
     */
    private final EconomicMap<String, Command> commands;

    /**
     * The command that was selected in the argument list.
     */
    private Command selectedCommand;

    /**
     * Constructs an argument group.
     *
     * @param name the name of the argument group
     * @param help the help message for the argument group
     */
    public CommandGroup(String name, String help) {
        super(name, true, help);
        commands = EconomicMap.create();
    }

    /**
     * Adds a command to this command group.
     *
     * @param command the command to be added
     */
    public void addCommand(Command command) {
        commands.put(command.getName(), command);
    }

    /**
     * Gets the command that was selected in the parsed argument list.
     */
    public Command getSelectedCommand() {
        return selectedCommand;
    }

    /**
     * Returns the command with the given name, or {@code null} if there is no such command.
     *
     * @param commandName the name of the command
     * @return the command with the given name or {@code null}
     */
    public Command getCommandByName(String commandName) {
        return commands.get(commandName);
    }

    /**
     * Creates a usage string listing the commands of this group and their help strings.
     *
     * @return the usage string listing the commands
     */
    public String formatCommandsHelp() {
        Formatter fmt = new Formatter();
        fmt.format("available commands:%n");
        for (Command command : commands.getValues()) {
            fmt.format("  %-20s %s%n", command.getName(), command.getDescription());
        }
        return fmt.toString();
    }

    @Override
    int parse(String[] args, int offset) throws InvalidArgumentException, UnknownArgumentException, MissingArgumentException {
        String command = args[offset];
        selectedCommand = commands.get(args[offset]);
        if (selectedCommand == null) {
            throw new InvalidArgumentException(getName(), "invalid command name: " + command);
        }
        selectedCommand.getArgumentParser().parse(Arrays.copyOfRange(args, offset + 1, args.length));
        return args.length;
    }
}
