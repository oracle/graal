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
package org.graalvm.bisect.matching;

import org.graalvm.bisect.core.Experiment;
import org.graalvm.bisect.core.ExperimentId;
import org.graalvm.bisect.core.optimization.Optimization;
import org.graalvm.bisect.matching.method.GreedyMethodMatcher;
import org.graalvm.bisect.matching.method.MethodMatching;
import org.graalvm.bisect.matching.optimization.SetBasedOptimizationMatcher;
import org.graalvm.bisect.matching.optimization.OptimizationMatching;

import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Compares two experiments and creates a summary.
 */
public class ExperimentMatcher {
    private static final String indent = "    ";
    private static final String indent2 = makeIndent(2);
    private static final String indent3 = makeIndent(3);

    /**
     * Matches two experiments and creates a summary. Uses a {@link GreedyMethodMatcher} to match hot methods of two
     * experiments and then compares their lists of optimizations with a {@link SetBasedOptimizationMatcher}. The
     * summary lists Java methods with hot executions, its matched and extra compilations in both experiments and the
     * lists of common/extra optimizations for each matched executed method.
     * @param experiment1 the first experiment
     * @param experiment2 the second experiment
     * @return a summary of the matched hot method executions and optimization matchings
     */
    public String matchAndSummarize(Experiment experiment1, Experiment experiment2) {
        StringBuilder sb = new StringBuilder();
        GreedyMethodMatcher matcher = new GreedyMethodMatcher();
        MethodMatching matching = matcher.match(experiment1, experiment2);
        SetBasedOptimizationMatcher optimizationMatcher = new SetBasedOptimizationMatcher();

        for (MethodMatching.MatchedMethod matchedMethod : matching.getMatchedMethods()) {
            sb.append("Method ")
                    .append(matchedMethod.getCompilationMethodName()).append('\n');
            for (MethodMatching.MatchedExecutedMethod matchedExecutedMethod : matchedMethod.getMatchedExecutedMethods()) {
                sb.append(indent).append("Compilation ")
                        .append(matchedExecutedMethod.getMethod1().getCompilationId())
                        .append(" in experiment ").append(ExperimentId.ONE)
                        .append(" vs compilation ")
                        .append(matchedExecutedMethod.getMethod2().getCompilationId())
                        .append(" in experiment ").append(ExperimentId.TWO)
                        .append('\n');
                OptimizationMatching optimizationMatching = optimizationMatcher.match(
                        matchedExecutedMethod.getMethod1().getOptimizations(),
                        matchedExecutedMethod.getMethod2().getOptimizations()
                );
                appendOptimizationSummary(sb, optimizationMatching);
            }
            for (MethodMatching.ExtraExecutedMethod extraExecutedMethod : matchedMethod.getExtraExecutedMethods()) {
                sb.append(indent).append("Compilation ")
                        .append(extraExecutedMethod.getExecutedMethod().getCompilationId())
                        .append(" only in experiment ").append(extraExecutedMethod.getExperimentId()).append('\n');
                sb.append("Optimizations in experiment ").append(extraExecutedMethod.getExperimentId()).append('\n');
                appendOptimizations(sb, extraExecutedMethod.getExecutedMethod().getOptimizations().iterator());
            }
        }

        for (MethodMatching.ExtraMethod extraMethod : matching.getExtraMethods()) {
            sb.append("Method ")
                    .append(extraMethod.getCompilationMethodName())
                    .append(" is only in experiment ").append(extraMethod.getExperimentId()).append('\n');
        }

        return sb.toString();
    }

    private static void appendOptimizationSummary(StringBuilder sb, OptimizationMatching optimizationMatching) {
        appendOptimizationSummaryForExperiment(sb, ExperimentId.ONE, optimizationMatching);
        appendOptimizationSummaryForExperiment(sb, ExperimentId.TWO, optimizationMatching);
        List<Optimization> optimizations = optimizationMatching.getMatchedOptimizations();
        if (optimizations.isEmpty()) {
            return;
        }
        sb.append(indent2).append("Optimizations in both experiments");
        appendOptimizations(sb, optimizations.iterator());
    }

    private static void appendOptimizationSummaryForExperiment(
            StringBuilder sb,
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
        sb.append(indent2).append("Optimizations only in experiment ").append(experimentId).append('\n');
        appendOptimizations(sb, optimizations.stream().map(OptimizationMatching.ExtraOptimization::getOptimization).iterator());
    }

    private static void appendOptimizations(StringBuilder sb, Iterator<Optimization> optimizations) {
        optimizations.forEachRemaining(optimization ->
            sb.append(indent3)
                    .append(optimization.getOptimizationKind())
                    .append(" at bci ").append(optimization.getBCI()).append('\n')
        );
    }

    private static String makeIndent(int indentLevel) {
        assert indentLevel >= 0;
        return indent.repeat(indentLevel);
    }
}
