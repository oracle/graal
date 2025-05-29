/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.util.args;

import java.io.PrintWriter;

import org.graalvm.collections.EconomicMap;

import jdk.graal.compiler.debug.GraalError;

/**
 * Encapsulates a set of sub-{@link Command}s of which the user can select one explicitly by name.
 *
 * @param <C> Common type of all subcommands in the group. Useful when subcommands share common
 *            functionality by inheriting from the same subclass of {@link Command}.
 */
public class CommandGroup<C extends Command> extends OptionValue<C> {
    private final EconomicMap<String, C> subCommands = EconomicMap.create();

    public CommandGroup(String name, String help) {
        super(name, help);
    }

    /**
     * Parses and updates the selected subcommand based on {@code args[offset]}.
     *
     * @see Command#parse(String[], int)
     */
    public int parse(String[] args, int offset) throws InvalidArgumentException, HelpRequestedException, CommandParsingException {
        String arg = args[offset];
        value = subCommands.get(arg);
        if (value == null) {
            throw new InvalidArgumentException(getName(), String.format("no subcommand named '%s'", arg));
        }
        return value.parse(args, offset + 1);
    }

    @Override
    public final boolean parseValue(String arg) throws InvalidArgumentException {
        throw GraalError.unimplementedOverride();
    }

    /**
     * Adds a command to the set of subcommands.
     */
    public C addCommand(C command) {
        subCommands.put(command.getName(), command);
        return command;
    }

    /**
     * @return The subcommand that was specified by the program arguments, or null if none was
     *         selected (yet).
     */
    public C getSelectedCommand() {
        return getValue();
    }

    @Override
    public void printUsage(PrintWriter writer, boolean detailed) {
        if (value != null) {
            value.printUsage(writer);
            return;
        }
        super.printUsage(writer, detailed);
    }

    @Override
    public void printHelp(PrintWriter writer, int indentLevel) {
        boolean separate = false;
        for (C command : subCommands.getValues()) {
            if (separate) {
                writer.println();
            }
            printIndented(writer, command.getName(), indentLevel);
            printIndented(writer, command.getDescription(), indentLevel + 1);
            separate = true;
        }
        writer.println();
        printIndented(writer, "Pass the --help flag after " + getUsage(false) + " for more help on the selected subcommand.", indentLevel);
    }
}
