package org.graalvm.bisect.test;

import static org.junit.Assert.assertEquals;

import java.util.List;

import org.graalvm.bisect.NaiveOptimizationMatcher;
import org.graalvm.bisect.NaiveOptimizationMatching;
import org.graalvm.bisect.core.optimization.Optimization;
import org.graalvm.bisect.core.optimization.PartialLoopUnrollingOptimization;
import org.junit.Test;


public class NaiveOptimizationMatcherTest {
    @Test
    public void testBCI() {
        List<Optimization> optimizations1 = List.of(
                new PartialLoopUnrollingOptimization(2),
                new PartialLoopUnrollingOptimization(3)
        );
        List<Optimization> optimizations2 = List.of(
                new PartialLoopUnrollingOptimization(2),
                new PartialLoopUnrollingOptimization(5)
        );
        NaiveOptimizationMatcher matcher = new NaiveOptimizationMatcher();
        NaiveOptimizationMatching matching = matcher.match(optimizations1, optimizations2);
        assertEquals(matching.getExtraOptimizations().size(), 2);
    }
}
