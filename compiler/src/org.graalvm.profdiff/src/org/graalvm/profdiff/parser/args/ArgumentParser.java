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

import java.util.ArrayList;
import java.util.Formatter;
import java.util.List;
import java.util.Optional;

import org.graalvm.collections.EconomicMap;
import org.graalvm.profdiff.command.Command;

/**
 * Assembles and parses arguments.
 */
public class ArgumentParser {
    /**
     * Prefix of an optional argument in a usage string.
     */
    public static final char LEFT_BRACKET = '[';

    /**
     * Suffix of an optional argument in a usage string.
     */
    public static final char RIGHT_BRACKET = ']';

    /**
     * The map of argument names to option arguments. Option argument names start with the "--"
     * prefix. They may be required or optional.
     */
    protected final EconomicMap<String, Argument> optionArguments = EconomicMap.create();

    /**
     * The list of positional arguments. The argument names do not start with a prefix and are
     * always required.
     */
    protected final List<Argument> positionalArguments = new ArrayList<>();

    /**
     * Parses the program arguments, sets the parsed values to {@link Argument} objects and verifies
     * constraints.
     *
     * @param args the list of program arguments
     * @throws InvalidArgumentException the provided argument has an invalid value
     * @throws MissingArgumentException a required argument is missing in the program arguments
     * @throws UnknownArgumentException a value was provided for an unknown argument
     */
    public void parse(String[] args) throws InvalidArgumentException,
                    MissingArgumentException,
                    UnknownArgumentException {
        int nextPositionalArg = 0;
        for (int index = 0; index < args.length;) {
            String arg = args[index];
            Argument argument;
            if (arg.startsWith(Argument.OPTION_PREFIX)) {
                int equalSignIndex = arg.indexOf(Argument.EQUAL_SIGN);
                String optionArgumentName = equalSignIndex == -1 ? arg : arg.substring(0, equalSignIndex);
                argument = optionArguments.get(optionArgumentName);
                if (argument == null) {
                    throw new UnknownArgumentException(arg);
                }
            } else {
                if (nextPositionalArg >= positionalArguments.size()) {
                    throw new UnknownArgumentException(arg);
                }
                argument = positionalArguments.get(nextPositionalArg++);
            }
            index = argument.parse(args, index);
        }
        for (Argument argument : optionArguments.getValues()) {
            if (!argument.isSet() && argument.isRequired()) {
                throw new MissingArgumentException(argument.getName());
            }
        }
        if (nextPositionalArg < positionalArguments.size() && positionalArguments.get(nextPositionalArg).isRequired()) {
            throw new MissingArgumentException(positionalArguments.get(nextPositionalArg).getName());
        }
    }

    /**
     * Gets {@link CommandGroup the command group} argument if this parser contains a command group.
     */
    public Optional<CommandGroup> getCommandGroup() {
        if (positionalArguments.isEmpty()) {
            return Optional.empty();
        }
        Argument last = positionalArguments.get(positionalArguments.size() - 1);
        if (last instanceof CommandGroup) {
            return Optional.of((CommandGroup) last);
        }
        return Optional.empty();
    }

    /**
     * Gets options argument mapped by argument name.
     */
    public EconomicMap<String, Argument> getOptionArguments() {
        return optionArguments;
    }

    /**
     * Gets the list of positional arguments.
     */
    public List<Argument> getPositionalArguments() {
        return positionalArguments;
    }

    /**
     * Formats a usage string for the option arguments.
     */
    public String formatOptionUsage() {
        StringBuilder sb = new StringBuilder();
        boolean isFirst = true;
        for (Argument argument : optionArguments.getValues()) {
            if (!isFirst) {
                sb.append(' ');
            }
            if (!argument.isRequired()) {
                sb.append(LEFT_BRACKET);
            }
            String dummyValue = argument.getName().substring(Argument.OPTION_PREFIX.length()).toUpperCase();
            sb.append(argument.getName()).append(' ').append(dummyValue);
            if (!argument.isRequired()) {
                sb.append(RIGHT_BRACKET);
            }
            isFirst = false;
        }
        return sb.toString();
    }

    /**
     * Formats a usage string for the positional arguments.
     */
    public String formatPositionalUsage() {
        StringBuilder sb = new StringBuilder();
        boolean isFirst = true;
        for (Argument argument : positionalArguments) {
            if (!isFirst) {
                sb.append(' ');
            }
            if (!argument.isRequired()) {
                sb.append(LEFT_BRACKET);
            }
            sb.append(argument.getName().toUpperCase());
            if (!argument.isRequired()) {
                sb.append(RIGHT_BRACKET);
            }
            isFirst = false;
        }
        return sb.toString();
    }

    /**
     * Formats a usage string for the positional arguments, selecting a given command for the
     * command group of this parser.
     *
     * @param command the selected command
     * @return a usage string with a selected command
     */
    public String formatPositionalUsage(Command command) {
        StringBuilder sb = new StringBuilder();
        boolean isFirst = true;
        for (Argument argument : positionalArguments) {
            if (!isFirst) {
                sb.append(' ');
            }
            if (!argument.isRequired()) {
                sb.append(LEFT_BRACKET);
            }
            if (argument instanceof CommandGroup) {
                sb.append(command.getName());
            } else {
                sb.append(argument.getName().toUpperCase());
            }
            if (!argument.isRequired()) {
                sb.append(RIGHT_BRACKET);
            }
            isFirst = false;
        }
        return sb.toString();
    }

    /**
     * Formats a help string for the option arguments, listing their names and descriptions.
     */
    public String formatOptionHelp() {
        Formatter fmt = new Formatter();
        fmt.format("options:%n");
        for (Argument argument : optionArguments.getValues()) {
            fmt.format("  %-20s %s%n", argument.getName(), argument.getDescription());
        }
        return fmt.toString();
    }

    /**
     * Formats a help string for the positional arguments, listing their names and descriptions.
     */
    public String formatPositionalHelp() {
        Formatter fmt = new Formatter();
        fmt.format("positional arguments:%n");
        for (Argument argument : positionalArguments) {
            fmt.format("  %-20s %s%n", argument.getName(), argument.getDescription());
        }
        return fmt.toString();
    }

    /**
     * Adds an argument to the list of program arguments. Verifies that there is up to 1 command
     * group, which must be the last positional arguments. Also verifies that optional positional
     * arguments are not followed by required positional arguments.
     *
     * @param argument the program argument to be added
     */
    protected void addArgument(Argument argument) {
        if (argument.isOptionArgument()) {
            optionArguments.put(argument.getName(), argument);
        } else {
            if (!positionalArguments.isEmpty()) {
                Argument last = positionalArguments.get(positionalArguments.size() - 1);
                if (last instanceof CommandGroup) {
                    throw new RuntimeException("Only one subparser group per argument parser is possible, and it must be the last positional argument.");
                }
                if (!last.isRequired() && argument.isRequired()) {
                    throw new RuntimeException("Optional positional arguments cannot be followed by required positional arguments.");
                }
            }
            positionalArguments.add(argument);
        }
    }

    /**
     * Adds an optional program argument that expects an integer. The argument name must include the
     * "--" prefix.
     *
     * @param name the name of the argument
     * @param defaultValue the value of the argument when no value is set in the program arguments
     * @param help the help message in the program usage string
     * @return the created argument instance
     */
    public IntegerArgument addIntegerArgument(String name, int defaultValue, String help) {
        IntegerArgument argument = new IntegerArgument(name, defaultValue, help);
        assert argument.isOptionArgument();
        addArgument(argument);
        return argument;
    }

    /**
     * Adds an optional program argument that expects a double. The argument name must include the
     * "--" prefix.
     *
     * @param name the name of the argument
     * @param defaultValue the value of the argument when no value is set in the program arguments
     * @param help the help message in the program usage string
     * @return the created argument instance
     */
    public DoubleArgument addDoubleArgument(String name, double defaultValue, String help) {
        DoubleArgument argument = new DoubleArgument(name, defaultValue, help);
        addArgument(argument);
        return argument;
    }

    /**
     * Adds a required program argument that expects a string.
     *
     * @param name the name of the argument
     * @param help the help message in the program usage string
     * @return the created argument instance
     */
    public StringArgument addStringArgument(String name, String help) {
        StringArgument argument = new StringArgument(name, help);
        addArgument(argument);
        return argument;
    }

    /**
     * Adds an optional program argument with a default value that expects a string.
     *
     * @param name the name of the argument
     * @param defaultValue the value of the argument when no value is set in the program arguments
     * @param help the help message in the program usage string
     * @return the created argument instance
     */
    public StringArgument addStringArgument(String name, String defaultValue, String help) {
        StringArgument argument = new StringArgument(name, defaultValue, help);
        addArgument(argument);
        return argument;
    }

    /**
     * Adds a flag holding a boolean that is true iff the option is present in the program
     * arguments.
     *
     * @param name the name of the argument
     * @param help the help message in the program usage string
     * @return the created argument instance
     */
    public FlagArgument addFlagArgument(String name, String help) {
        FlagArgument argument = new FlagArgument(name, help);
        addArgument(argument);
        return argument;
    }

    /**
     * Adds an argument that can hold an enum value. The value is parsed case-insensitively as its
     * string representation.
     *
     * @param name the name of the argument
     * @param defaultValue the default value of the argument, must not be null
     * @param help the help message in the program usage string
     * @param <T> the type of the enum
     * @return the created argument instance
     */
    public <T extends Enum<T>> EnumArgument<T> addEnumArgument(String name, T defaultValue, String help) {
        EnumArgument<T> argument = new EnumArgument<>(name, defaultValue, help);
        addArgument(argument);
        return argument;
    }

    /**
     * Adds a positional argument that expects a command name. The returned {@link CommandGroup}
     * should be populated with commands. The selected command from the command group will then
     * parse the rest of the arguments. Only one command group per parser is possible, and it must
     * be the last positional argument.
     *
     * @param name the name of the command group
     * @param help the help message for the command group
     * @return the added command group
     */
    public CommandGroup addCommandGroup(String name, String help) {
        if (name.startsWith(Argument.OPTION_PREFIX)) {
            throw new RuntimeException("Command group must be a positional argument, i.e., the name must not start with " +
                            Argument.OPTION_PREFIX + ".");
        }
        CommandGroup subparserGroup = new CommandGroup(name, help);
        addArgument(subparserGroup);
        return subparserGroup;
    }
}
