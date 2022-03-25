package org.graalvm.bisect;

import org.graalvm.bisect.core.ExperimentId;
import org.graalvm.bisect.core.optimization.Optimization;
import org.graalvm.bisect.util.SetUtil;

import java.util.List;

public class NaiveOptimizationMatcher implements OptimizationMatcher {
    @Override
    public NaiveOptimizationMatching match(List<Optimization> optimizations1, List<Optimization> optimizations2) {
        NaiveOptimizationMatching matching = new NaiveOptimizationMatching();
        analyzeExtraOptimizations(optimizations1, optimizations2, matching, ExperimentId.ONE);
        analyzeExtraOptimizations(optimizations2, optimizations1, matching, ExperimentId.TWO);
        return matching;
    }

    private static void analyzeExtraOptimizations(
            List<Optimization> optimizations1,
            List<Optimization> optimizations2,
            NaiveOptimizationMatching matching,
            ExperimentId lhsExperimentId) {
        for (Optimization optimization : SetUtil.difference(optimizations1, optimizations2)) {
            matching.addExtraOptimization(optimization, lhsExperimentId);
        }
    }
}
