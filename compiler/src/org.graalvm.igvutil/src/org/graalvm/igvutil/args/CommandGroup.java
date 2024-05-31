
/*
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.collections.EconomicMap;

/**
 * Encapsulates a group of option and positional arguments.
 */
public class CommandGroup<C extends Command> extends Argument {
    /**
     * The map of argument names to option arguments. Option argument names start with the "--"
     * prefix. They may be required or optional.
     */
    private final EconomicMap<String, C> subCommands = EconomicMap.create();

    /**
     * Parsed subcommand.
     */
    private C selectedCommand = null;

    public CommandGroup(String name, boolean required, String description) {
        super(name, required, description);
    }

    @Override
    public int parse(String[] args, int offset) throws InvalidArgumentException, MissingArgumentException, HelpRequestedException {
        String arg = args[offset];
        selectedCommand = subCommands.get(arg);
        if (selectedCommand == null) {
            throw new InvalidArgumentException(this, String.format("no subcommand corresponding to argument '%s'", arg));
        }
        set = true;
        return selectedCommand.parse(args, offset + 1);
    }

    public C addCommand(C command) {
        subCommands.put(command.getName(), command);
        return command;
    }

    public C getSelectedCommand() {
        return selectedCommand;
    }

    @Override
    public void printUsage(HelpPrinter help) {
        if (selectedCommand == null) {
            help.print("<%s>", getName());
        } else {
            selectedCommand.printUsage(help);
        }
    }

    @Override
    public void printHelp(HelpPrinter help) {
        if (selectedCommand != null) {
            selectedCommand.printHelp(help);
            return;
        }
        help.println("SUBCOMMANDS:");
        for (Argument arg : subCommands.getValues()) {
            help.printArg(arg).newline();
        }
        help.newline();
    }
}
