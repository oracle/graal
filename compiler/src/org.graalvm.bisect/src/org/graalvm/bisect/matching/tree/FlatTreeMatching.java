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
package org.graalvm.bisect.matching.tree;

import org.graalvm.bisect.util.Writer;
import org.graalvm.bisect.core.ExperimentId;
import org.graalvm.bisect.core.optimization.Optimization;
import org.graalvm.bisect.matching.optimization.OptimizationMatching;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Describes a matching of flattened optimization trees, which is a wrapper of {@link OptimizationMatching}.
 */
public class FlatTreeMatching implements TreeMatching {
    final OptimizationMatching optimizationMatching;

    public FlatTreeMatching(OptimizationMatching optimizationMatching) {
        this.optimizationMatching = optimizationMatching;
    }

    /**
     * Writes the list optimizations in both experiments and the lists of optimizations that are only in one experiment.
     * @param writer the destination writer
     */
    @Override
    public void writeSummary(Writer writer) {
        summarizeOptimizationsForExperiment(writer, ExperimentId.ONE, optimizationMatching);
        summarizeOptimizationsForExperiment(writer, ExperimentId.TWO, optimizationMatching);
        List<Optimization> optimizations = optimizationMatching.getMatchedOptimizations();
        if (optimizations.isEmpty()) {
            return;
        }
        writer.writeln("Optimizations in both experiments");
        writer.increaseIndent();
        Optimization.writeOptimizations(writer, optimizations.stream());
        writer.decreaseIndent();
    }

    /**
     * Writes the list of optimizations only in one of the experiments.
     * @param writer the destination writer
     * @param experimentId write optimizations that are only in this experiment
     * @param optimizationMatching the optimization matching
     */
    private static void summarizeOptimizationsForExperiment(
            Writer writer,
            ExperimentId experimentId,
            OptimizationMatching optimizationMatching) {
        List<OptimizationMatching.ExtraOptimization> optimizations = optimizationMatching
                .getExtraOptimizations()
                .stream()
                .filter(match -> match.getExperimentId() == experimentId)
                .collect(Collectors.toList());
        if (optimizations.isEmpty()) {
            return;
        }
        writer.writeln("Optimizations only in experiment " + experimentId);
        writer.increaseIndent();
        Optimization.writeOptimizations(
                writer,
                optimizations.stream().map(OptimizationMatching.ExtraOptimization::getOptimization)
        );
        writer.decreaseIndent();
    }
}
