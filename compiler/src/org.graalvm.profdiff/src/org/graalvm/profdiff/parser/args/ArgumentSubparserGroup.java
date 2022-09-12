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

import org.graalvm.collections.EconomicMap;

/**
 * Represents a program argument that parses a single string as a command name, which invokes
 * {@link ArgumentSubparser a subparser}. The subparser is then responsible for parsing the rest of
 * the arguments.
 */
public class ArgumentSubparserGroup extends Argument {

    /**
     * Individual subparsers mapped by commands that invoke them.
     */
    private final EconomicMap<String, ArgumentSubparser> subparsers;

    /**
     * The subparser that was invoked during parsing.
     */
    private ArgumentSubparser invokedSubparser;

    /**
     * Constructs an argument group.
     *
     * @param name the name of the argument group
     * @param help the help message for the argument group
     */
    ArgumentSubparserGroup(String name, String help) {
        super(name, true, help);
        subparsers = EconomicMap.create();
    }

    /**
     * Adds a subparser to this subparser group and returns it.
     *
     * @param command the command that invokes the added subparser
     * @param help the help message for the added subparser
     * @return the added command
     */
    public ArgumentSubparser addSubparser(String command, String help) {
        ArgumentSubparser subparser = new ArgumentSubparser(command, help);
        subparsers.put(command, subparser);
        return subparser;
    }

    /**
     * Gets the subparser that was invoked during parsing.
     */
    public ArgumentSubparser getInvokedSubparser() {
        return invokedSubparser;
    }

    /**
     * Creates a usage string listing the commands of this group and their help strings.
     *
     * @return the usage string listing the commands
     */
    public String createUsage() {
        StringBuilder sb = new StringBuilder("Commands:\n");
        for (ArgumentSubparser subparser : subparsers.getValues()) {
            sb.append(String.format("  %-20s ", subparser.getCommand())).append(subparser.getHelp()).append('\n');
        }
        return sb.toString();
    }

    @Override
    int parse(String[] args, int offset) throws InvalidArgumentException, UnknownArgumentException, MissingArgumentException {
        String command = args[offset];
        invokedSubparser = subparsers.get(args[offset]);
        if (invokedSubparser == null) {
            throw new InvalidArgumentException(getName(), "invalid command name: " + command);
        }
        invokedSubparser.parse(Arrays.copyOfRange(args, offset + 1, args.length));
        return args.length;
    }
}
