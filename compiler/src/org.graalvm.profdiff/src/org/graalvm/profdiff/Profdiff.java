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

import java.io.IOException;

import org.graalvm.profdiff.core.Experiment;
import org.graalvm.profdiff.core.ExperimentId;
import org.graalvm.profdiff.core.HotCompilationUnitPolicy;
import org.graalvm.profdiff.core.VerbosityLevel;
import org.graalvm.profdiff.core.optimization.OptimizationTreeNode;
import org.graalvm.profdiff.matching.method.GreedyMethodMatcher;
import org.graalvm.profdiff.matching.method.MatchedCompilationUnit;
import org.graalvm.profdiff.matching.method.MatchedMethod;
import org.graalvm.profdiff.matching.method.MethodMatching;
import org.graalvm.profdiff.matching.tree.OptimizationTreeEditPolicy;
import org.graalvm.profdiff.matching.tree.SelkowTreeMatcher;
import org.graalvm.profdiff.matching.tree.TreeMatcher;
import org.graalvm.profdiff.matching.tree.TreeMatching;
import org.graalvm.profdiff.parser.args.ArgumentParser;
import org.graalvm.profdiff.parser.args.DoubleArgument;
import org.graalvm.profdiff.parser.args.EnumArgument;
import org.graalvm.profdiff.parser.args.IntegerArgument;
import org.graalvm.profdiff.parser.args.InvalidArgumentException;
import org.graalvm.profdiff.parser.args.MissingArgumentException;
import org.graalvm.profdiff.parser.args.StringArgument;
import org.graalvm.profdiff.parser.args.UnknownArgumentException;
import org.graalvm.profdiff.parser.experiment.ExperimentFiles;
import org.graalvm.profdiff.parser.experiment.ExperimentFilesImpl;
import org.graalvm.profdiff.parser.experiment.ExperimentParser;
import org.graalvm.profdiff.parser.experiment.ExperimentParserError;
import org.graalvm.profdiff.util.StdoutWriter;
import org.graalvm.profdiff.util.Writer;

public class Profdiff {
    public static void main(String[] args) {
        ArgumentParser argumentParser = new ArgumentParser(
                        "mx profdiff",
                        "Compares the optimization log of hot compilation units of two experiments.");
        IntegerArgument hotMinArgument = argumentParser.addIntegerArgument(
                        "--hot-min-limit", 1,
                        "the minimum number of compilation units to mark as hot");
        IntegerArgument hotMaxArgument = argumentParser.addIntegerArgument(
                        "--hot-max-limit", 10,
                        "the maximum number of compilation units to mark as hot");
        DoubleArgument percentileArgument = argumentParser.addDoubleArgument(
                        "--hot-percentile", 0.9,
                        "the percentile of the execution period that is spent executing hot compilation units");
        EnumArgument<VerbosityLevel> verbosityLevelArgument = argumentParser.addEnumArgument(
                        "--verbosity", VerbosityLevel.DEFAULT,
                        "the verbosity level of the diff");
        StringArgument proftoolArgument1 = argumentParser.addStringArgument(
                        "proftool_output_1", "proftool output of the first experiment in JSON.");
        StringArgument optimizationLogArgument1 = argumentParser.addStringArgument(
                        "optimization_log_1", "directory with optimization logs for each compilation unit in the first experiment.");
        StringArgument proftoolArgument2 = argumentParser.addStringArgument(
                        "proftool_output_2", "proftool output of the second experiment in JSON.");
        StringArgument optimizationLogArgument2 = argumentParser.addStringArgument(
                        "optimization_log_2", "directory with optimization logs for each compilation unit in the second experiment.");
        try {
            argumentParser.parse(args);
        } catch (InvalidArgumentException | MissingArgumentException | UnknownArgumentException e) {
            System.err.println(e.getMessage());
            System.err.println(argumentParser.createUsage());
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

        VerbosityLevel verbosityLevel = verbosityLevelArgument.getValue();
        Writer writer = new StdoutWriter(verbosityLevel);

        HotCompilationUnitPolicy hotCompilationUnitPolicy = new HotCompilationUnitPolicy();
        hotCompilationUnitPolicy.setHotMinLimit(hotMinArgument.getValue());
        hotCompilationUnitPolicy.setHotMaxLimit(hotMaxArgument.getValue());
        hotCompilationUnitPolicy.setHotPercentile(percentileArgument.getValue());

        ExperimentFiles experimentFiles1 = new ExperimentFilesImpl(
                        ExperimentId.ONE, proftoolArgument1.getValue(), optimizationLogArgument1.getValue());
        ExperimentParser parser1 = new ExperimentParser(experimentFiles1);
        Experiment experiment1 = parseOrExit(parser1);
        hotCompilationUnitPolicy.markHotCompilationUnits(experiment1);
        experiment1.writeExperimentSummary(writer);
        writer.writeln();

        ExperimentFiles experimentFiles2 = new ExperimentFilesImpl(
                        ExperimentId.TWO, proftoolArgument2.getValue(), optimizationLogArgument2.getValue());
        ExperimentParser parser2 = new ExperimentParser(experimentFiles2);
        Experiment experiment2 = parseOrExit(parser2);
        hotCompilationUnitPolicy.markHotCompilationUnits(experiment2);
        experiment2.writeExperimentSummary(writer);

        GreedyMethodMatcher matcher = new GreedyMethodMatcher();
        MethodMatching matching = matcher.match(experiment1, experiment2);
        TreeMatcher<OptimizationTreeNode> optimizationTreeMatcher = new SelkowTreeMatcher<>(new OptimizationTreeEditPolicy());

        for (MatchedMethod matchedMethod : matching.getMatchedMethods()) {
            writer.writeln();
            matchedMethod.writeHeaderAndCompilationUnits(writer, experiment1, experiment2);
            writer.increaseIndent();
            if (verbosityLevel.shouldPrintOptimizationTree()) {
                matchedMethod.getFirstHotCompilationUnits().forEach(compilationUnit -> compilationUnit.write(writer));
                matchedMethod.getSecondHotCompilationUnits().forEach(compilationUnit -> compilationUnit.write(writer));
            }
            if (verbosityLevel.shouldDiffCompilations()) {
                for (MatchedCompilationUnit matchedCompilationUnit : matchedMethod.getMatchedCompilationUnits()) {
                    matchedCompilationUnit.writeHeader(writer);
                    TreeMatching optimizationTreeMatching = optimizationTreeMatcher.match(
                                    matchedCompilationUnit.getFirstCompilationUnit().getRootPhase(),
                                    matchedCompilationUnit.getSecondCompilationUnit().getRootPhase());
                    optimizationTreeMatching.write(writer);
                }
                matchedMethod.getUnmatchedCompilationUnits().forEach(compilationUnit -> compilationUnit.write(writer));
            }
            writer.decreaseIndent();
        }
        matching.getUnmatchedMethods().forEach(unmatchedMethod -> {
            writer.writeln();
            unmatchedMethod.write(writer, experiment1, experiment2);
        });
    }

    private static Experiment parseOrExit(ExperimentParser parser) {
        try {
            return parser.parse();
        } catch (IOException e) {
            System.err.println("Could not read the files of the experiment " + parser.getExperimentFiles().getExperimentId() + ": " + e.getMessage());
            System.exit(1);
        } catch (ExperimentParserError e) {
            System.err.println(e.getMessage());
            System.exit(1);
        }
        return null;
    }
}
