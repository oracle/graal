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
package org.graalvm.profdiff;

import org.graalvm.profdiff.args.BooleanArgument;
import org.graalvm.profdiff.args.CommandGroup;
import org.graalvm.profdiff.args.DoubleArgument;
import org.graalvm.profdiff.args.IntegerArgument;
import org.graalvm.profdiff.args.ProgramArgumentParser;
import org.graalvm.profdiff.command.AOTAOTCommand;
import org.graalvm.profdiff.command.AOTAOTWithJITProfileCommand;
import org.graalvm.profdiff.command.HelpCommand;
import org.graalvm.profdiff.command.JITAOTCommand;
import org.graalvm.profdiff.command.JITJITCommand;
import org.graalvm.profdiff.command.ReportCommand;
import org.graalvm.profdiff.core.HotCompilationUnitPolicy;
import org.graalvm.profdiff.core.OptionValues;
import org.graalvm.profdiff.core.Writer;

public class Profdiff {
    private static class ProgramArguments {
        private final ProgramArgumentParser argumentParser;

        private final IntegerArgument hotMinArgument;

        private final IntegerArgument hotMaxArgument;

        private final DoubleArgument percentileArgument;

        private final BooleanArgument optimizationContextTreeArgument;

        private final BooleanArgument diffCompilationsArgument;

        private final BooleanArgument bciLongFormArgument;

        private final BooleanArgument sortInliningTreeArgument;

        private final BooleanArgument sortUnorderedPhasesArgument;

        private final BooleanArgument removeVeryDetailedPhasesArgument;

        private final BooleanArgument pruneIdentitiesArgument;

        private final BooleanArgument createFragmentsArgument;

        private final BooleanArgument inlinerReasoningArgument;

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
            optimizationContextTreeArgument = argumentParser.addBooleanArgument(
                            "--optimization-context-tree", false, "combine optimization/inlining trees into an optimization-context tree");
            diffCompilationsArgument = argumentParser.addBooleanArgument(
                            "--diff-compilations", true, "match and diff compilations");
            bciLongFormArgument = argumentParser.addBooleanArgument(
                            "--long-bci", false, "display bci in long form");
            sortInliningTreeArgument = argumentParser.addBooleanArgument(
                            "--sort-inlining-tree", true, "sort inlining tree nodes by (bci, name)");
            sortUnorderedPhasesArgument = argumentParser.addBooleanArgument(
                            "--sort-unordered-phases", true, "sort the children of optimization phases where order is not important");
            removeVeryDetailedPhasesArgument = argumentParser.addBooleanArgument(
                            "--remove-detailed-phases", true, "remove phases which perform many optimizations");
            pruneIdentitiesArgument = argumentParser.addBooleanArgument(
                            "--prune-identities", true, "show only differences when trees are compared");
            createFragmentsArgument = argumentParser.addBooleanArgument(
                            "--create-fragments", true, "create compilation fragments from inlinees in hot compilation units");
            inlinerReasoningArgument = argumentParser.addBooleanArgument(
                            "--inliner-reasoning", false, "always print the reasoning for inlining decisions");
            commandGroup = argumentParser.addCommandGroup(
                            "command", "the action to invoke");
        }

        public void parseAndVerifyArguments(String[] args) throws Exception {
            argumentParser.parse(args);
            if (percentileArgument.getValue() > 1 || percentileArgument.getValue() < 0) {
                throw new IllegalArgumentException("The hot method percentile must be in the range [0;1].");
            }
            if (hotMinArgument.getValue() < 0 || hotMinArgument.getValue() > hotMaxArgument.getValue()) {
                throw new IllegalArgumentException(String.format("The condition 0 <= %s <= %s must be satisfied.", hotMinArgument.getName(), hotMaxArgument.getName()));
            }
        }

        public CommandGroup getCommandGroup() {
            return commandGroup;
        }

        public ProgramArgumentParser getArgumentParser() {
            return argumentParser;
        }

        private HotCompilationUnitPolicy getHotCompilationUnitPolicy() {
            HotCompilationUnitPolicy hotCompilationUnitPolicy = new HotCompilationUnitPolicy();
            hotCompilationUnitPolicy.setHotMinLimit(hotMinArgument.getValue());
            hotCompilationUnitPolicy.setHotMaxLimit(hotMaxArgument.getValue());
            hotCompilationUnitPolicy.setHotPercentile(percentileArgument.getValue());
            return hotCompilationUnitPolicy;
        }

        public OptionValues getOptionValues() {
            return new OptionValues(getHotCompilationUnitPolicy(), optimizationContextTreeArgument.getValue(),
                            diffCompilationsArgument.getValue(), bciLongFormArgument.getValue(),
                            sortInliningTreeArgument.getValue(), sortUnorderedPhasesArgument.getValue(),
                            removeVeryDetailedPhasesArgument.getValue(), pruneIdentitiesArgument.getValue(),
                            createFragmentsArgument.getValue(), inlinerReasoningArgument.getValue());
        }
    }

    public static boolean mainImpl(String[] args) {
        ProgramArguments programArguments = new ProgramArguments();
        CommandGroup commandGroup = programArguments.getCommandGroup();
        commandGroup.addCommand(new ReportCommand());
        commandGroup.addCommand(new JITJITCommand());
        commandGroup.addCommand(new JITAOTCommand());
        commandGroup.addCommand(new AOTAOTCommand());
        commandGroup.addCommand(new AOTAOTWithJITProfileCommand());
        commandGroup.addCommand(new HelpCommand(programArguments.getArgumentParser()));

        try {
            programArguments.parseAndVerifyArguments(args);
        } catch (Exception exception) {
            System.err.println(exception.getMessage());
            System.err.println(programArguments.argumentParser.formatHelp());
            return false;
        }

        Writer writer = Writer.standardOutput(programArguments.getOptionValues());
        try {
            commandGroup.getSelectedCommand().invoke(writer);
        } catch (Exception exception) {
            System.err.println(exception.getMessage());
            return false;
        }
        return true;
    }

    public static void main(String[] args) {
        if (!mainImpl(args)) {
            System.exit(1);
        }
    }
}
