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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Builds and parses arguments.
 */
public abstract class ArgumentParser {
    /**
     * The map of argument names to option arguments. Option argument names start with the "--"
     * prefix. They may be required or optional.
     */
    protected final Map<String, Argument> optionArguments = new LinkedHashMap<>();

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
        for (Argument argument : optionArguments.values()) {
            if (!argument.isSet() && argument.isRequired()) {
                throw new MissingArgumentException(argument.getName());
            }
        }
        if (nextPositionalArg < positionalArguments.size()) {
            throw new MissingArgumentException(positionalArguments.get(nextPositionalArg).getName());
        }
    }

    /**
     * Gets the subparser group argument if this parser contains a subparser group.
     */
    protected Optional<ArgumentSubparserGroup> getSubparserGroup() {
        if (positionalArguments.isEmpty()) {
            return Optional.empty();
        }
        int last = positionalArguments.size() - 1;
        if (positionalArguments.get(last) instanceof ArgumentSubparserGroup) {
            return Optional.of((ArgumentSubparserGroup) positionalArguments.get(last));
        }
        return Optional.empty();
    }

    /**
     * Adds an argument to the list of program arguments.
     *
     * @param argument the program argument to be added
     */
    protected void addArgument(Argument argument) {
        if (argument.isOptionArgument()) {
            optionArguments.put(argument.getName(), argument);
        } else {
            if (getSubparserGroup().isPresent()) {
                throw new RuntimeException("Only one subparser group per argument parser is possible, " +
                                "and it must be the last positional argument.");
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
     * Adds an argument that expects a command name that will parse the rest of the arguments. Only
     * one subparser group per parser is possible, and it must be the last positional argument.
     *
     * @param name the name of the command group
     * @param help the help message for the command group
     * @return the command group
     */
    public ArgumentSubparserGroup addSubparserGroup(String name, String help) {
        ArgumentSubparserGroup subparserGroup = new ArgumentSubparserGroup(name, help);
        addArgument(subparserGroup);
        return subparserGroup;
    }
}
