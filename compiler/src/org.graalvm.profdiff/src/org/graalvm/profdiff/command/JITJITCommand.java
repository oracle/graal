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
package org.graalvm.profdiff.command;

import org.graalvm.profdiff.core.Experiment;
import org.graalvm.profdiff.core.ExperimentId;
import org.graalvm.profdiff.core.pair.ExperimentPair;
import org.graalvm.profdiff.args.ArgumentParser;
import org.graalvm.profdiff.args.StringArgument;
import org.graalvm.profdiff.parser.ExperimentParser;
import org.graalvm.profdiff.parser.ExperimentParserError;
import org.graalvm.profdiff.core.Writer;

/**
 * Compares two JIT-compiled experiments with proftool data.
 */
public class JITJITCommand implements Command {
    private final ArgumentParser argumentParser;

    private final StringArgument optimizationLogArgument1;

    private final StringArgument proftoolArgument1;

    private final StringArgument optimizationLogArgument2;

    private final StringArgument proftoolArgument2;

    public JITJITCommand() {
        argumentParser = new ArgumentParser();
        optimizationLogArgument1 = argumentParser.addStringArgument(
                        "optimization_log_1", "directory with optimization logs of the first JIT experiment");
        proftoolArgument1 = argumentParser.addStringArgument(
                        "proftool_output_1", "proftool output of the first JIT experiment in JSON");
        optimizationLogArgument2 = argumentParser.addStringArgument(
                        "optimization_log_2", "directory with optimization logs of the second JIT experiment");
        proftoolArgument2 = argumentParser.addStringArgument(
                        "proftool_output_2", "proftool output of the second JIT experiment in JSON");
    }

    @Override
    public String getName() {
        return "jit-vs-jit";
    }

    @Override
    public String getDescription() {
        return "compare two JIT-compiled experiments";
    }

    @Override
    public ArgumentParser getArgumentParser() {
        return argumentParser;
    }

    @Override
    public void invoke(Writer writer) throws ExperimentParserError {
        ExplanationWriter explanationWriter = new ExplanationWriter(writer, false, true);
        explanationWriter.explain();

        writer.writeln();
        Experiment jit1 = ExperimentParser.parseOrPanic(ExperimentId.ONE, Experiment.CompilationKind.JIT, proftoolArgument1.getValue(), optimizationLogArgument1.getValue(), writer);
        writer.getOptionValues().getHotCompilationUnitPolicy().markHotCompilationUnits(jit1);
        jit1.writeExperimentSummary(writer);

        writer.writeln();
        Experiment jit2 = ExperimentParser.parseOrPanic(ExperimentId.TWO, Experiment.CompilationKind.JIT, proftoolArgument2.getValue(), optimizationLogArgument2.getValue(), writer);
        writer.getOptionValues().getHotCompilationUnitPolicy().markHotCompilationUnits(jit2);
        jit2.writeExperimentSummary(writer);

        writer.writeln();
        ExperimentMatcher matcher = new ExperimentMatcher(writer);
        matcher.match(new ExperimentPair(jit1, jit2));
    }
}
