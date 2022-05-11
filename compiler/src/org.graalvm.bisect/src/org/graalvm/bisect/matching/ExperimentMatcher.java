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

import org.graalvm.bisect.core.ExecutedMethod;
import org.graalvm.bisect.core.Experiment;
import org.graalvm.bisect.core.ExperimentId;
import org.graalvm.bisect.core.optimization.Optimization;
import org.graalvm.bisect.matching.method.GreedyMethodMatcher;
import org.graalvm.bisect.matching.method.MethodMatching;
import org.graalvm.bisect.matching.optimization.SetBasedOptimizationMatcher;
import org.graalvm.bisect.matching.optimization.OptimizationMatching;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Compares two experiments and creates a summary.
 */
public class ExperimentMatcher {
    private final String[] indent;
    private static final int indents = 5;

    public ExperimentMatcher() {
        indent = new String[indents];
        indent[0] = "";
        for (int i = 1; i < indents; i++) {
            indent[i] = indent[i - 1] + "    ";
        }
    }

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
            appendMethodSummary(sb, matchedMethod.getCompilationMethodName(), experiment1, experiment2);
            for (MethodMatching.MatchedExecutedMethod matchedExecutedMethod : matchedMethod.getMatchedExecutedMethods()) {
                appendMatchedMethodSummary(sb, experiment1, experiment2, matchedExecutedMethod);
                OptimizationMatching optimizationMatching = optimizationMatcher.match(
                        matchedExecutedMethod.getMethod1().getOptimizationsRecursive(),
                        matchedExecutedMethod.getMethod2().getOptimizationsRecursive()
                );
                appendOptimizationSummary(sb, optimizationMatching);
            }
            for (MethodMatching.ExtraExecutedMethod extraExecutedMethod : matchedMethod.getExtraExecutedMethods()) {
                sb.append(indent[1]).append("Compilation ")
                        .append(extraExecutedMethod.getExecutedMethod().getCompilationId())
                        .append(" only in experiment ").append(extraExecutedMethod.getExperimentId()).append('\n');
                sb.append("Optimizations in experiment ").append(extraExecutedMethod.getExperimentId()).append('\n');
                appendOptimizations(sb, extraExecutedMethod.getExecutedMethod().getOptimizationsRecursive().stream());
            }
        }

        for (MethodMatching.ExtraMethod extraMethod : matching.getExtraMethods()) {
            sb.append("Method ")
                    .append(extraMethod.getCompilationMethodName())
                    .append(" is only in experiment ").append(extraMethod.getExperimentId()).append('\n');
        }

        return sb.toString();
    }

    private void appendMatchedMethodSummary(StringBuilder sb,
                                            Experiment experiment1,
                                            Experiment experiment2,
                                            MethodMatching.MatchedExecutedMethod matchedExecutedMethod) {
        sb.append(indent[1]).append("Compilation ")
                .append(matchedExecutedMethod.getMethod1().getCompilationId())
                .append(" (")
                .append(createSummaryOfMethodExecution(experiment1, matchedExecutedMethod.getMethod1()))
                .append(") in experiment ").append(ExperimentId.ONE).append('\n')
                .append(indent[1]).append("vs compilation ")
                .append(matchedExecutedMethod.getMethod2().getCompilationId())
                .append(" (")
                .append(createSummaryOfMethodExecution(experiment2, matchedExecutedMethod.getMethod2()))
                .append(") in experiment ").append(ExperimentId.TWO)
                .append('\n');
    }

    private void appendOptimizationSummary(StringBuilder sb, OptimizationMatching optimizationMatching) {
        appendOptimizationSummaryForExperiment(sb, ExperimentId.ONE, optimizationMatching);
        appendOptimizationSummaryForExperiment(sb, ExperimentId.TWO, optimizationMatching);
        List<Optimization> optimizations = optimizationMatching.getMatchedOptimizations();
        if (optimizations.isEmpty()) {
            return;
        }
        sb.append(indent[2]).append("Optimizations in both experiments\n");
        appendOptimizations(sb, optimizations.stream());
    }

    private void appendOptimizationSummaryForExperiment(
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
        sb.append(indent[2]).append("Optimizations only in experiment ").append(experimentId).append('\n');
        appendOptimizations(sb, optimizations.stream().map(OptimizationMatching.ExtraOptimization::getOptimization));
    }

    private void appendOptimizations(StringBuilder sb, Stream<Optimization> optimizations) {
        optimizations
                .sorted(Comparator.comparing(Optimization::getBCI))
                .iterator()
                .forEachRemaining(optimization -> {
                    sb.append(indent[3])
                            .append(optimization.getOptimizationName())
                            .append(' ')
                            .append(optimization.getEventName())
                            .append(" at bci ").append(optimization.getBCI()).append('\n');
                    if (optimization.getProperties() == null) {
                        return;
                    }
                    for (Map.Entry<String, Object> entry : optimization.getProperties().entrySet()) {
                        sb.append(indent[4])
                                .append(entry.getKey())
                                .append(": ")
                                .append(entry.getValue())
                                .append('\n');
                    }
                });
    }

    private String createSummaryOfMethodExecution(Experiment experiment, ExecutedMethod executedMethod) {
        String graalPercent = String.format("%.2f", (double) executedMethod.getPeriod() / experiment.getGraalPeriod() * 100);
        String totalPercent = String.format("%.2f", (double) executedMethod.getPeriod() / experiment.getTotalPeriod() * 100);
        return graalPercent + "% of graal execution, " + totalPercent + "% of total";
    }

    private void appendMethodSummary(StringBuilder sb, String compilationMethodName, Experiment experiment1, Experiment experiment2) {
        sb.append("Method ")
                .append(compilationMethodName).append('\n');
        appendMethodSummaryForExperiment(sb, compilationMethodName, experiment1);
        appendMethodSummaryForExperiment(sb, compilationMethodName, experiment2);
    }

    private void appendMethodSummaryForExperiment(StringBuilder sb, String compilationMethodName, Experiment experiment) {
        sb.append(indent[1]).append("In experiment ").append(experiment.getExperimentId()).append('\n');
        List<ExecutedMethod> executedMethods = experiment.getMethodsByName(compilationMethodName)
                .stream()
                .sorted(Comparator.comparingLong(executedMethod -> -executedMethod.getPeriod()))
                .collect(Collectors.toList());
        long hotMethodCount = executedMethods.stream()
                .filter(ExecutedMethod::isHot)
                .count();
        sb.append(indent[2]).append(executedMethods.size()).append(" compilations (")
                .append(hotMethodCount).append(" of which are hot)\n")
                .append(indent[2]).append("Compilations\n");
        for (ExecutedMethod executedMethod : executedMethods) {
            sb.append(indent[3])
                    .append(executedMethod.getCompilationId())
                    .append(" (")
                    .append(createSummaryOfMethodExecution(experiment, executedMethod))
                    .append(')');
            if (executedMethod.isHot()) {
                sb.append(" *hot*\n");
            } else {
                sb.append('\n');
            }
        }
    }
}
