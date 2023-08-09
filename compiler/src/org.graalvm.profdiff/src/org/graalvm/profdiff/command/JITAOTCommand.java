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

import org.graalvm.profdiff.core.CompilationUnit;
import org.graalvm.profdiff.core.Experiment;
import org.graalvm.profdiff.core.ExperimentId;
import org.graalvm.profdiff.core.pair.ExperimentPair;
import org.graalvm.profdiff.args.ArgumentParser;
import org.graalvm.profdiff.args.StringArgument;
import org.graalvm.profdiff.parser.ExperimentParser;
import org.graalvm.profdiff.parser.ExperimentParserError;
import org.graalvm.profdiff.core.Writer;

/**
 * Compares a JIT-compiled experiment with an AOT experiment. It is possible to provide a profile of
 * the AOT experiment. If no AOT profile is provided, all methods that are hot or inlined in a hot
 * JIT method are marked hot in the AOT experiment.
 */
public class JITAOTCommand implements Command {
    private final ArgumentParser argumentParser;

    private final StringArgument jitOptimizationLogArgument;

    private final StringArgument jitProftoolArgument;

    private final StringArgument aotOptimizationLogArgument;

    private final StringArgument aotProftoolArgument;

    public JITAOTCommand() {
        argumentParser = new ArgumentParser();
        jitOptimizationLogArgument = argumentParser.addStringArgument(
                        "jit_optimization_log", "directory with optimization logs for each compilation unit in the JIT experiment");
        jitProftoolArgument = argumentParser.addStringArgument(
                        "jit_proftool_output", "proftool output of the JIT experiment in JSON");
        aotOptimizationLogArgument = argumentParser.addStringArgument(
                        "aot_optimization_log", "directory with optimization logs of the AOT compilation");
        aotProftoolArgument = argumentParser.addStringArgument(
                        "aot_proftool_output", null, "proftool output of the AOT experiment in JSON");
    }

    @Override
    public String getName() {
        return "jit-vs-aot";
    }

    @Override
    public String getDescription() {
        return "compare a JIT experiment with an AOT compilation";
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
        Experiment jit = ExperimentParser.parseOrPanic(ExperimentId.ONE, Experiment.CompilationKind.JIT, jitProftoolArgument.getValue(), jitOptimizationLogArgument.getValue(), writer);
        writer.getOptionValues().getHotCompilationUnitPolicy().markHotCompilationUnits(jit);
        jit.writeExperimentSummary(writer);

        writer.writeln();
        Experiment aot = ExperimentParser.parseOrPanic(ExperimentId.TWO, Experiment.CompilationKind.AOT, aotProftoolArgument.getValue(), aotOptimizationLogArgument.getValue(), writer);
        if (aotProftoolArgument.getValue() == null) {
            for (CompilationUnit jitUnit : jit.getCompilationUnits()) {
                if (!jitUnit.isHot()) {
                    continue;
                }
                jitUnit.loadTrees().getInliningTree().getRoot().forEach(node -> {
                    if (node.isPositive() && node.getName() != null) {
                        aot.getMethodOrCreate(node.getName()).getCompilationUnits().forEach(aotUnit -> aotUnit.setHot(true));
                    }
                });
            }
        } else {
            writer.getOptionValues().getHotCompilationUnitPolicy().markHotCompilationUnits(aot);
        }
        aot.writeExperimentSummary(writer);

        writer.writeln();
        ExperimentMatcher matcher = new ExperimentMatcher(writer);
        matcher.match(new ExperimentPair(jit, aot));
    }
}
