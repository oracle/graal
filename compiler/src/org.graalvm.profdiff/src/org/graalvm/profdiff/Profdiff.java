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
package org.graalvm.profdiff;

import org.graalvm.profdiff.command.HelpCommand;
import org.graalvm.profdiff.command.JITAOTCommand;
import org.graalvm.profdiff.command.JITJITCommand;
import org.graalvm.profdiff.command.ReportCommand;
import org.graalvm.profdiff.core.HotCompilationUnitPolicy;
import org.graalvm.profdiff.core.VerbosityLevel;
import org.graalvm.profdiff.parser.args.CommandGroup;
import org.graalvm.profdiff.parser.args.DoubleArgument;
import org.graalvm.profdiff.parser.args.EnumArgument;
import org.graalvm.profdiff.parser.args.IntegerArgument;
import org.graalvm.profdiff.parser.args.InvalidArgumentException;
import org.graalvm.profdiff.parser.args.MissingArgumentException;
import org.graalvm.profdiff.parser.args.ProgramArgumentParser;
import org.graalvm.profdiff.parser.args.UnknownArgumentException;
import org.graalvm.profdiff.util.StdoutWriter;

public class Profdiff {
    private static class ProgramArguments {
        private final ProgramArgumentParser argumentParser;

        private final IntegerArgument hotMinArgument;

        private final IntegerArgument hotMaxArgument;

        private final DoubleArgument percentileArgument;

        private final EnumArgument<VerbosityLevel> verbosityLevelArgument;

        private final CommandGroup commandGroup;

        ProgramArguments() {
            argumentParser = new ProgramArgumentParser(
                            "mx profdiff",
                            "compares the optimization log of hot compilation units of two experiments");
            hotMinArgument = argumentParser.addIntegerArgument(
                            "--hot-min-limit", 1,
                            "the minimum number of compilation units to mark as hot");
            hotMaxArgument = argumentParser.addIntegerArgument(
                            "--hot-max-limit", 10,
                            "the maximum number of compilation units to mark as hot");
            percentileArgument = argumentParser.addDoubleArgument(
                            "--hot-percentile", 0.9,
                            "the percentile of the execution period that is spent executing hot compilation units");
            verbosityLevelArgument = argumentParser.addEnumArgument(
                            "--verbosity", VerbosityLevel.DEFAULT,
                            "the verbosity level of the diff");
            commandGroup = argumentParser.addCommandGroup("command", "the action to invoke");
        }

        public void parseOrExit(String[] args) {
            try {
                argumentParser.parse(args);
            } catch (InvalidArgumentException | MissingArgumentException | UnknownArgumentException e) {
                System.err.println(e.getMessage());
                System.err.println(argumentParser.formatHelp());
                System.exit(1);
            }

            if (percentileArgument.getValue() > 1 || percentileArgument.getValue() < 0) {
                System.err.println("The hot method percentile must be in the range [0;1].");
                System.exit(1);
            }

            if (hotMinArgument.getValue() < 0 || hotMinArgument.getValue() > hotMaxArgument.getValue()) {
                System.err.printf("The condition 0 <= %s <= %s must be satisfied.", hotMinArgument.getName(), hotMaxArgument.getName());
                System.exit(1);
            }
        }

        public CommandGroup getCommandGroup() {
            return commandGroup;
        }

        public VerbosityLevel getVerbosityLevel() {
            return verbosityLevelArgument.getValue();
        }

        public ProgramArgumentParser getArgumentParser() {
            return argumentParser;
        }

        public HotCompilationUnitPolicy getHotCompilationUnitPolicy() {
            HotCompilationUnitPolicy hotCompilationUnitPolicy = new HotCompilationUnitPolicy();
            hotCompilationUnitPolicy.setHotMinLimit(hotMinArgument.getValue());
            hotCompilationUnitPolicy.setHotMaxLimit(hotMaxArgument.getValue());
            hotCompilationUnitPolicy.setHotPercentile(percentileArgument.getValue());
            return hotCompilationUnitPolicy;
        }
    }

    public static void main(String[] args) {
        ProgramArguments programArguments = new ProgramArguments();
        CommandGroup commandGroup = programArguments.getCommandGroup();
        commandGroup.addCommand(new ReportCommand());
        commandGroup.addCommand(new JITJITCommand());
        commandGroup.addCommand(new JITAOTCommand());
        commandGroup.addCommand(new HelpCommand(programArguments.getArgumentParser()));

        programArguments.parseOrExit(args);

        commandGroup.getSelectedCommand().setHotCompilationUnitPolicy(programArguments.getHotCompilationUnitPolicy());
        VerbosityLevel verbosityLevel = programArguments.getVerbosityLevel();
        StdoutWriter writer = new StdoutWriter(verbosityLevel);
        try {
            commandGroup.getSelectedCommand().invoke(writer);
        } catch (Exception exception) {
            System.err.println(exception.getMessage());
            System.exit(1);
        }
    }
}
