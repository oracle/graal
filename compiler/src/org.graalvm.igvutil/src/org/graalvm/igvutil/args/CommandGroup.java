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

import org.graalvm.collections.EconomicMap;

/**
 * Encapsulates a set of sub-{@link Command}s of which the user can select one explicitly by name.
 *
 * @param <C> Common type of all subcommands in the group. Useful when subcommands share common
 *            functionality by inheriting from the same subclass of {@link Command}.
 */
public class CommandGroup<C extends Command> {
    private final EconomicMap<String, C> subCommands = EconomicMap.create();

    /**
     * Subcommand specified by the arguments, or null if none was selected.
     */
    private C selectedCommand = null;

    /**
     * Parses and updates the selected subcommand based on {@code args[offset]}.
     *
     * @see Command#parse(String[], int)
     */
    public int parse(String[] args, int offset) throws InvalidArgumentException, MissingArgumentException, HelpRequestedException {
        String arg = args[offset];
        selectedCommand = subCommands.get(arg);
        if (selectedCommand == null) {
            throw new InvalidArgumentException("SUBCOMMAND", String.format("no subcommand named '%s'", arg));
        }
        return selectedCommand.parse(args, offset + 1);
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
        return selectedCommand;
    }

    public void clear() {
        selectedCommand = null;
    }

    public void printUsage(PrintWriter writer) {
        if (selectedCommand == null) {
            writer.print("<SUBCOMMAND>");
        } else {
            selectedCommand.printUsage(writer);
        }
    }

    public void printHelp(PrintWriter writer) {
        if (selectedCommand != null) {
            selectedCommand.printHelp(writer);
            return;
        }
        writer.println("SUBCOMMANDS:");
        boolean separate = false;
        for (C command : subCommands.getValues()) {
            if (separate) {
                writer.println();
            }
            writer.format(Command.HELP_ITEM_FMT, command.getName(), command.getDescription());
            separate = true;
        }
    }
}
