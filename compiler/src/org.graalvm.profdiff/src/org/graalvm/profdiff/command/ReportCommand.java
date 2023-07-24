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

import org.graalvm.profdiff.core.CompilationUnit;
import org.graalvm.profdiff.core.Experiment;
import org.graalvm.profdiff.core.ExperimentId;
import org.graalvm.profdiff.core.Method;
import org.graalvm.profdiff.args.ArgumentParser;
import org.graalvm.profdiff.args.StringArgument;
import org.graalvm.profdiff.parser.ExperimentParser;
import org.graalvm.profdiff.parser.ExperimentParserError;
import org.graalvm.profdiff.core.Writer;

/**
 * Prints out one experiment which may optionally include proftool data.
 */
public class ReportCommand implements Command {
    private final ArgumentParser argumentParser;

    private final StringArgument optimizationLogArgument;

    private final StringArgument proftoolArgument;

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
    public String getDescription() {
        return "dump the optimization log of an experiment";
    }

    @Override
    public ArgumentParser getArgumentParser() {
        return argumentParser;
    }

    @Override
    public void invoke(Writer writer) throws ExperimentParserError {
        boolean hasProftool = proftoolArgument.getValue() != null;
        ExplanationWriter explanationWriter = new ExplanationWriter(writer, true, hasProftool);
        explanationWriter.explain();

        writer.writeln();
        Experiment experiment = ExperimentParser.parseOrPanic(ExperimentId.ONE, null, proftoolArgument.getValue(), optimizationLogArgument.getValue(), writer);
        writer.getOptionValues().getHotCompilationUnitPolicy().markHotCompilationUnits(experiment);
        experiment.writeExperimentSummary(writer);

        for (Method method : experiment.getMethodsByDescendingPeriod()) {
            if (hasProftool && !method.isHot()) {
                continue;
            }
            writer.writeln();
            writer.writeln("Method " + method.getMethodName());
            writer.increaseIndent();
            method.writeCompilationList(writer);
            writer.increaseIndent();
            Iterable<CompilationUnit> compilationUnits = method.getCompilationUnits();
            if (proftoolArgument.getValue() != null) {
                compilationUnits = method.getHotCompilationUnits();
            }
            for (CompilationUnit compilationUnit : compilationUnits) {
                compilationUnit.write(writer);
            }
            writer.decreaseIndent(2);
        }
    }
}
