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

import static org.graalvm.igvutil.args.OptionValue.INDENT;

import java.io.PrintWriter;

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
                printHelpAndExit(e.getCommand(), 1);
            }
        } catch (HelpRequestedException e) {
            printHelpAndExit(e.getCommand(), 0);
        }
    }

    public void printHelpAndExit(Command c, int exitCode) {
        try (PrintWriter writer = new PrintWriter(System.out)) {
            printHelp(c, writer);
        }
        System.exit(exitCode);
    }

    private void printUsage(Command c, PrintWriter writer) {
        if (c != this) {
            // Printing usage for subcommand
            writer.append(getName());
            writer.append(' ');
        }
        c.printUsage(writer);
    }

    public void printHelp(Command c, PrintWriter writer) {
        writer.println();
        writer.println("USAGE:");
        writer.append(INDENT);
        printUsage(c, writer);
        writer.println();
        writer.println();
        c.printHelp(writer);
    }
}
