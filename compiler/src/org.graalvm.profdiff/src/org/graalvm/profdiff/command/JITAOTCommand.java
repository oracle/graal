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
import org.graalvm.profdiff.core.HotCompilationUnitPolicy;
import org.graalvm.profdiff.core.pair.ExperimentPair;
import org.graalvm.profdiff.parser.args.ArgumentParser;
import org.graalvm.profdiff.parser.args.StringArgument;
import org.graalvm.profdiff.parser.experiment.ExperimentParser;
import org.graalvm.profdiff.util.Writer;

/**
 * Compares a JIT-compiled experiment with an AOT compilation. Uses proftool data of the JIT
 * experiment to designate hot compilations units.
 */
public class JITAOTCommand implements Command {
    private final ArgumentParser argumentParser;

    private final StringArgument jitOptimizationLogArgument;

    private final StringArgument proftoolArgument;

    private final StringArgument aotOptimizationLogArgument;

    private HotCompilationUnitPolicy hotCompilationUnitPolicy;

    public JITAOTCommand() {
        argumentParser = new ArgumentParser();
        jitOptimizationLogArgument = argumentParser.addStringArgument(
                        "jit_optimization_log", "directory with optimization logs for each compilation unit in the JIT experiment");
        proftoolArgument = argumentParser.addStringArgument(
                        "proftool_output", "proftool output of the JIT experiment in JSON");
        aotOptimizationLogArgument = argumentParser.addStringArgument(
                        "aot_optimization_log", "directory with optimization logs of the AOT compilation");
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
    public void setHotCompilationUnitPolicy(HotCompilationUnitPolicy hotCompilationUnitPolicy) {
        this.hotCompilationUnitPolicy = hotCompilationUnitPolicy;
    }

    @Override
    public void invoke(Writer writer) throws Exception {
        ExplanationWriter explanationWriter = new ExplanationWriter(writer, false, true);
        explanationWriter.explain();

        writer.writeln();
        Experiment jit = ExperimentParser.parseOrExit(ExperimentId.ONE, Experiment.CompilationKind.JIT, proftoolArgument.getValue(), jitOptimizationLogArgument.getValue(), writer);
        hotCompilationUnitPolicy.markHotCompilationUnits(jit);
        jit.writeExperimentSummary(writer);

        writer.writeln();
        Experiment aot = ExperimentParser.parseOrExit(ExperimentId.TWO, Experiment.CompilationKind.AOT, null, aotOptimizationLogArgument.getValue(), writer);
        aot.writeExperimentSummary(writer);
        for (CompilationUnit jitUnit : jit.getCompilationUnits()) {
            if (!jitUnit.isHot()) {
                continue;
            }
            aot.getMethodOrCreate(jitUnit.getMethod().getMethodName()).getCompilationUnits().forEach(aotUnit -> aotUnit.setHot(true));
        }

        ExperimentMatcher matcher = new ExperimentMatcher(writer);
        matcher.match(new ExperimentPair(jit, aot));
    }
}
