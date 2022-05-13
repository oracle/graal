package org.graalvm.bisect.test;

import org.graalvm.bisect.core.optimization.Optimization;
import org.graalvm.bisect.core.optimization.OptimizationImpl;
import org.graalvm.bisect.core.optimization.OptimizationPhaseImpl;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;

public class OptimizationPhaseTest {
    @Test
    public void recursiveOptimizationList() {
        OptimizationPhaseImpl rootPhase = new OptimizationPhaseImpl("RootPhase");
        OptimizationPhaseImpl phaseA = new OptimizationPhaseImpl("A");
        OptimizationPhaseImpl phaseB = new OptimizationPhaseImpl("B");
        OptimizationPhaseImpl phaseC = new OptimizationPhaseImpl("C");
        OptimizationPhaseImpl phaseD = new OptimizationPhaseImpl("D");
        Optimization optimization1 = new OptimizationImpl("foo", "bar", 1, null);
        Optimization optimization2 = new OptimizationImpl("foo", "bar", 2, null);
        Optimization optimization3 = new OptimizationImpl("foo", "bar", 3, null);
        Optimization optimization4 = new OptimizationImpl("foo", "bar", 4, null);
        Optimization optimization5 = new OptimizationImpl("foo", "bar", 5, null);

        rootPhase.addChild(optimization1);
        rootPhase.addChild(phaseA);
        phaseA.addChild(optimization2);
        rootPhase.addChild(optimization3);
        rootPhase.addChild(phaseB);
        phaseB.addChild(phaseC);
        phaseC.addChild(optimization4);
        phaseC.addChild(optimization5);
        phaseB.addChild(phaseD);

        List<Optimization> expected = List.of(optimization1, optimization2, optimization3, optimization4, optimization5);
        assertEquals(expected, rootPhase.getOptimizationsRecursive());
    }
}
