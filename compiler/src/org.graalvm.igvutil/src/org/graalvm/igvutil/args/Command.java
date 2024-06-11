/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.igvutil.args;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

import org.graalvm.collections.EconomicMap;

/**
 * Encapsulates a group of option and positional arguments.
 */
public class Command {
    public static final String SEPARATOR = "--";
    /**
     * The value of a valued option argument may be specified as the successive program argument
     * (e.g. --arg value) or with an equal sign (e.g. --arg=value).
     */
    public static final char EQUAL_SIGN = '=';

    public static final String HELP = "--help";

    /**
     * The map of argument names to option arguments. Option argument names start with the "--"
     * prefix.
     */
    private final EconomicMap<String, OptionValue<?>> options = EconomicMap.create();

    /**
     * The list of positional arguments. The argument names do not start with a prefix and are
     * always required.
     */
    private final List<OptionValue<?>> positional = new ArrayList<>();

    private CommandGroup<? extends Command> commandGroup = null;

    private final String name;
    private final String description;

    public Command(String name, String description) {
        this.name = name;
        this.description = description;
    }

    /**
     * Adds an argument to the list of program arguments.
     *
     * @param argument the program argument to be added
     */
    public <T> OptionValue<T> addPositional(OptionValue<T> argument) {
        positional.add(argument);
        return argument;
    }

    /**
     * Adds an argument to the list of program arguments.
     *
     * @param argument the program argument to be added
     */
    public <T> OptionValue<T> addOption(String optionName, OptionValue<T> argument) {
        options.put(optionName, argument);
        return argument;
    }

    public <C extends Command> CommandGroup<C> addCommandGroup(CommandGroup<C> commandGroup) {
        if (this.commandGroup != null) {
            throw new RuntimeException("Only one subcommand per command is supported");
        }
        this.commandGroup = commandGroup;
        return commandGroup;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    private boolean parseEqualsValue(String arg, int equalSignIndex) throws InvalidArgumentException {
        String optionName = arg.substring(0, equalSignIndex);
        String valueString = arg.substring(equalSignIndex + 1);
        OptionValue<?> value = options.get(optionName);
        if (value == null) {
            return false;
        }
        int index = value.parse(new String[]{valueString}, 0);
        return index > 0;
    }

    public int parse(String[] args, int offset) throws InvalidArgumentException, MissingArgumentException, HelpRequestedException {
        int nextPositionalArg = 0;
        int index = offset;
        while (index < args.length) {
            if (args[index].contentEquals(SEPARATOR)) {
                index++;
                break;
            }
            if (args[index].contentEquals(HELP)) {
                throw new HelpRequestedException(this);
            }
            if (commandGroup != null && commandGroup.getSelectedCommand() == null) {
                index = commandGroup.parse(args, index);
                continue;
            }
            // Split up argument of the form option=value
            int equalSignIndex = args[index].indexOf(EQUAL_SIGN);
            if (equalSignIndex > -1 && parseEqualsValue(args[index], equalSignIndex)) {
                index++;
                continue;
            }
            OptionValue<?> value = options.get(args[index]);
            if (value == null) {
                if (nextPositionalArg >= positional.size()) {
                    break;
                }
                value = positional.get(nextPositionalArg++);
            }
            index = value.parse(args, index);
        }
        if (commandGroup != null && commandGroup.getSelectedCommand() == null) {
            throw new MissingArgumentException("SUBCOMMAND");
        }
        var cursor = options.getEntries();
        while (cursor.advance()) {
            String name = cursor.getKey();
            OptionValue<?> value = cursor.getValue();
            if (!value.isSet() && value.isRequired()) {
                throw new MissingArgumentException(name);
            }
        }
        if (nextPositionalArg < positional.size()) {
            throw new MissingArgumentException(positional.get(nextPositionalArg).getName());
        }
        return index;
    }


    static final String INDENT = "  ";

    static final String HELP_ITEM_FMT = "  %s%n    %s%n";

    public void printUsage(PrintWriter writer) {
        writer.append(getName());
        if (commandGroup != null) {
            writer.append(' ');
            commandGroup.printUsage(writer);
            if (!options.isEmpty() || !positional.isEmpty()) {
                writer.append(" --");
            }
        }
        for (OptionValue<?> option : positional) {
            writer.append(' ');
            writer.append(option.getUsage());
        }
        if (!options.isEmpty()) {
            writer.append(" [OPTIONS]");
        }
    }

    public void printHelp(PrintWriter writer) {
        if (commandGroup != null) {
            commandGroup.printHelp(writer);
            return;
        }

        boolean separate = false;
        if (!positional.isEmpty()) {
            writer.println("ARGS:");
            for (OptionValue<?> arg : positional) {
                if (separate) {
                    writer.println();
                }
                writer.format(HELP_ITEM_FMT, arg.getUsage(), arg.getDescription());
                separate = true;
            }
        }
        if (!options.isEmpty()) {
            if (separate) {
                writer.println();
                separate = false;
            }
            writer.println("OPTIONS:");
            var cursor = options.getEntries();
            while (cursor.advance()) {
                if (separate) {
                    writer.println();
                }
                String name = cursor.getKey();
                OptionValue<?> value = cursor.getValue();
                writer.format(HELP_ITEM_FMT, String.format("%s %s", name, value.getUsage()), value.getDescription());
                separate = true;
            }
        }
    }
}
