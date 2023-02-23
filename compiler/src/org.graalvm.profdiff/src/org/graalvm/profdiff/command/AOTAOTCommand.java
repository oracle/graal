/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.profdiff.args.ArgumentParser;
import org.graalvm.profdiff.args.StringArgument;
import org.graalvm.profdiff.core.CompilationUnit;
import org.graalvm.profdiff.core.Experiment;
import org.graalvm.profdiff.core.ExperimentId;
import org.graalvm.profdiff.core.Writer;
import org.graalvm.profdiff.core.pair.ExperimentPair;
import org.graalvm.profdiff.parser.ExperimentParser;
import org.graalvm.profdiff.parser.ExperimentParserError;

/**
 * Compares 2 AOT experiments. The command also takes a profiled JIT experiment as an argument. The
 * JIT experiment is used to identify hot methods. All methods that are hot in JIT or inlined in a
 * hot JIT method are marked as hot in both AOT experiments.
 */
public class AOTAOTCommand implements Command {
    private final ArgumentParser argumentParser;
    private final StringArgument jitOptimizationLogArgument;
    private final StringArgument proftoolArgument;
    private final StringArgument aotOptimizationLogArgument1;
    private final StringArgument aotOptimizationLogArgument2;

    public AOTAOTCommand() {
        argumentParser = new ArgumentParser();
        jitOptimizationLogArgument = argumentParser.addStringArgument(
                        "jit_optimization_log", "directory with optimization logs for each compilation unit in the JIT experiment");
        proftoolArgument = argumentParser.addStringArgument(
                        "proftool_output", "proftool output of the JIT experiment in JSON");
        aotOptimizationLogArgument1 = argumentParser.addStringArgument(
                        "aot_optimization_log_1", "directory with optimization logs of the first AOT compilation");
        aotOptimizationLogArgument2 = argumentParser.addStringArgument(
                        "aot_optimization_log_2", "directory with optimization logs of the second AOT compilation");
    }

    @Override
    public String getName() {
        return "aot-vs-aot";
    }

    @Override
    public String getDescription() {
        return "compare two AOT experiments using an execution profile from JIT";
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
        Experiment jit = ExperimentParser.parseOrExit(ExperimentId.AUXILIARY, Experiment.CompilationKind.JIT, proftoolArgument.getValue(), jitOptimizationLogArgument.getValue(), writer);
        writer.getOptionValues().getHotCompilationUnitPolicy().markHotCompilationUnits(jit);
        jit.writeExperimentSummary(writer);

        writer.writeln();
        Experiment aot1 = ExperimentParser.parseOrExit(ExperimentId.ONE, Experiment.CompilationKind.AOT, null, aotOptimizationLogArgument1.getValue(), writer);
        aot1.writeExperimentSummary(writer);

        writer.writeln();
        Experiment aot2 = ExperimentParser.parseOrExit(ExperimentId.TWO, Experiment.CompilationKind.AOT, null, aotOptimizationLogArgument2.getValue(), writer);
        aot2.writeExperimentSummary(writer);

        for (CompilationUnit jitUnit : jit.getCompilationUnits()) {
            if (!jitUnit.isHot()) {
                continue;
            }
            jitUnit.loadTrees().getInliningTree().getRoot().forEach(node -> {
                if (node.isPositive() && node.getName() != null) {
                    aot1.getMethodOrCreate(node.getName()).getCompilationUnits().forEach(aotUnit -> aotUnit.setHot(true));
                    aot2.getMethodOrCreate(node.getName()).getCompilationUnits().forEach(aotUnit -> aotUnit.setHot(true));
                }
            });
        }

        ExperimentMatcher matcher = new ExperimentMatcher(writer);
        matcher.match(new ExperimentPair(aot1, aot2));
    }
}
