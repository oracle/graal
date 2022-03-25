package org.graalvm.bisect;

import org.graalvm.bisect.core.optimization.Optimization;

import java.util.List;

public interface OptimizationMatcher {
    OptimizationMatching match(List<Optimization> optimizations1, List<Optimization> optimizations2);
}
