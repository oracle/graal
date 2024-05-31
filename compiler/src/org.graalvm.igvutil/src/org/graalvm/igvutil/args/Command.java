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

import java.util.ArrayList;
import java.util.List;

import org.graalvm.collections.EconomicMap;

/**
 * Encapsulates a group of option and positional arguments.
 */
public class Command extends Argument {
    /**
     * The map of argument names to option arguments. Option argument names start with the "--"
     * prefix.
     */
    private final EconomicMap<String, Option<?>> options = EconomicMap.create();

    /**
     * The list of positional arguments. The argument names do not start with a prefix and are
     * always required.
     */
    private final List<Argument> positional = new ArrayList<>();

    public Command(String name, String description) {
        super(name, true, description);
    }

    @Override
    public int parse(String[] args, int offset) throws InvalidArgumentException, MissingArgumentException, HelpRequestedException {
        int nextPositionalArg = 0;
        int index;
        for (index = offset; index < args.length; ) {
            String arg = args[index];
            Argument argument;
            if (arg.startsWith(Argument.OPTION_PREFIX)) {
                if (arg.contentEquals(Argument.OPTION_PREFIX)) {
                    index++;
                    break;
                }
                if (arg.contentEquals(Argument.HELP)) {
                    throw new HelpRequestedException(this);
                }
                int equalSignIndex = arg.indexOf(Argument.EQUAL_SIGN);
                String optionArgumentName = equalSignIndex == -1 ? arg : arg.substring(0, equalSignIndex);
                argument = options.get(optionArgumentName);
                if (argument == null) {
                    break;
                }
            } else {
                if (nextPositionalArg >= positional.size()) {
                    break;
                }
                argument = positional.get(nextPositionalArg++);
            }
            index = argument.parse(args, index);
        }
        for (Argument argument : options.getValues()) {
            if (!argument.isSet() && argument.isRequired()) {
                throw new MissingArgumentException(argument.getName());
            }
        }
        if (nextPositionalArg < positional.size()) {
            throw new MissingArgumentException(positional.get(nextPositionalArg).getName());
        }
        return index;
    }

    /**
     * Adds an argument to the list of program arguments.
     *
     * @param argument the program argument to be added
     */
    public <A extends Argument> A addArgument(A argument) {
        if (argument instanceof Option<?> option) {
            options.put(argument.getName(), option);
        } else {
            positional.add(argument);
        }
        return argument;
    }

    @Override
    public void printUsage(HelpPrinter help) {
        help.print("%s", getName());
        if (!options.isEmpty()) {
            help.print(" [OPTIONS]");
        }
        for (Argument arg : positional) {
            help.print(" ");
            arg.printUsage(help);
        }
    }

    @Override
    public void printHelp(HelpPrinter help) {
        if (!positional.isEmpty()) {
            if (positional.getFirst() instanceof CommandGroup<?> group) {
                group.printHelp(help);
                return;
            }
            help.println("ARGS:");
            for (Argument arg : positional) {
                arg.printHelp(help);
                help.newline();
            }
        }
        if (!options.isEmpty()) {
            help.println("OPTIONS:");
            for (Argument arg : options.getValues()) {
                arg.printHelp(help);
                help.newline();
            }
        }
    }
}
