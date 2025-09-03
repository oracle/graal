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

import static jdk.graal.compiler.util.args.OptionValue.INDENT;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

import org.graalvm.collections.Pair;

/**
 * Main entry-point of command-line parsing. The method {@link #parseAndValidate(String[], boolean)}
 * will parse the full array of command-line arguments and handle any errors that might have been
 * thrown during parsing.
 */
public class Program extends Command {
    public Program(String name, String description) {
        super(name, description);
    }

    public final void parseAndValidate(String[] args, boolean errorIsFatal) {
        try {
            int parsed = parse(args, 0);
            if (parsed < args.length) {
                throw new CommandParsingException(new UnknownArgumentException(args[parsed]), this);
            }
        } catch (CommandParsingException e) {
            System.err.println(e.getMessage());
            if (errorIsFatal) {
                printHelpAndExit(1);
            }
        } catch (HelpRequestedException e) {
            printHelpAndExit(0);
        }
    }

    public void printHelpAndExit(int exitCode) {
        try (PrintWriter writer = new PrintWriter(System.out)) {
            printHelp(writer);
        }
        System.exit(exitCode);
    }

    public final void printHelp(PrintWriter writer) {
        List<OptionValue<?>> positional = new ArrayList<>();
        List<Pair<String, OptionValue<?>>> named = new ArrayList<>();
        collectOptions(positional, named);

        writer.println();
        writer.println("USAGE:");
        writer.append(INDENT);
        printUsage(writer);
        writer.println();

        if (!positional.isEmpty()) {
            writer.println();
            writer.println("ARGS:");
        }
        boolean separate = false;
        for (OptionValue<?> arg : positional) {
            if (separate) {
                writer.println();
            }
            OptionValue.printIndented(writer, arg.getUsage(false), 1);
            arg.printHelp(writer, 2);
            separate = true;
        }

        if (!named.isEmpty()) {
            writer.println();
            writer.println("OPTIONS:");
        }
        separate = false;
        for (Pair<String, OptionValue<?>> pair : named) {
            if (separate) {
                writer.println();
            }
            String name = pair.getLeft();
            OptionValue<?> option = pair.getRight();
            OptionValue.printIndented(writer, String.format("%s %s", name, option.getUsage(false)), 1);
            option.printHelp(writer, 2);
            separate = true;
        }
    }
}
