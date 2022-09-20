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
package org.graalvm.profdiff.command;

import java.util.List;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.MapCursor;
import org.graalvm.profdiff.core.CompilationUnit;
import org.graalvm.profdiff.core.Experiment;
import org.graalvm.profdiff.core.ExperimentId;
import org.graalvm.profdiff.core.HotCompilationUnitPolicy;
import org.graalvm.profdiff.core.VerbosityLevel;
import org.graalvm.profdiff.parser.args.ArgumentParser;
import org.graalvm.profdiff.parser.args.StringArgument;
import org.graalvm.profdiff.parser.experiment.ExperimentParser;
import org.graalvm.profdiff.util.Writer;

/**
 * Prints out one experiment which may optionally include proftool data.
 */
public class ReportCommand implements Command {
    private final ArgumentParser argumentParser;

    private final StringArgument optimizationLogArgument;

    private final StringArgument proftoolArgument;

    private HotCompilationUnitPolicy hotCompilationUnitPolicy;

    public ReportCommand() {
        argumentParser = new ArgumentParser();
        optimizationLogArgument = argumentParser.addStringArgument(
                        "optimization_log", "directory with optimization logs for each compilation unit");
        proftoolArgument = argumentParser.addStringArgument(
                        "proftool_output", null, "proftool output of the experiment in JSON");
    }

    @Override
    public String getName() {
        return "report";
    }

    @Override
    public String getHelp() {
        return "dump the optimization log of an experiment";
    }

    @Override
    public ArgumentParser getArgumentParser() {
        return argumentParser;
    }

    @Override
    public void setHotCompilationUnitPolicy(HotCompilationUnitPolicy hotCompilationUnitPolicy) {
        this.hotCompilationUnitPolicy = hotCompilationUnitPolicy;
    }

    @Override
    public void invoke(Writer writer) {
        VerbosityLevel verbosity = writer.getVerbosityLevel();
        Experiment experiment = ExperimentParser.parseOrExit(ExperimentId.ONE, proftoolArgument.getValue(), optimizationLogArgument.getValue());
        hotCompilationUnitPolicy.markHotCompilationUnits(experiment);
        experiment.preprocessCompilationUnits(verbosity);
        experiment.writeExperimentSummary(writer);
        writer.writeln();

        ExplanationWriter explanationWriter = new ExplanationWriter(writer);
        explanationWriter.explain();

        EconomicMap<String, List<CompilationUnit>> methods = experiment.getCompilationUnitsByName();
        if (proftoolArgument.getValue() != null) {
            methods = experiment.groupHotCompilationUnitsByMethod();
        }
        MapCursor<String, List<CompilationUnit>> methodCursor = methods.getEntries();
        while (methodCursor.advance()) {
            writer.writeln();
            writer.writeln("Method " + methodCursor.getKey());
            writer.increaseIndent();
            experiment.writeCompilationUnits(writer, methodCursor.getKey());
            if ((verbosity.shouldPrintOptimizationTree() || verbosity.shouldDiffCompilations()) && !verbosity.shouldShowOnlyDiff()) {
                for (CompilationUnit compilationUnit : methodCursor.getValue()) {
                    compilationUnit.write(writer);
                }
            }
            writer.decreaseIndent();
        }
    }
}
