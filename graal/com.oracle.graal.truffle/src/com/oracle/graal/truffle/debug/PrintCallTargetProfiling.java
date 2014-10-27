package com.oracle.graal.truffle.debug;

import static com.oracle.graal.truffle.TruffleCompilerOptions.*;

import java.util.*;
import java.util.function.*;
import java.util.stream.*;

import com.oracle.graal.truffle.*;
import com.oracle.truffle.api.*;

public class PrintCallTargetProfiling extends AbstractDebugCompilationListener {

    public static void install(GraalTruffleRuntime runtime) {
        if (TruffleCallTargetProfiling.getValue()) {
            runtime.addCompilationListener(new PrintCallTargetProfiling());
        }
    }

    @Override
    public void notifyShutdown(TruffleRuntime runtime) {
        Map<OptimizedCallTarget, List<OptimizedCallTarget>> groupedTargets = Truffle.getRuntime().getCallTargets().stream().map(target -> (OptimizedCallTarget) target).collect(
                        Collectors.groupingBy(target -> {
                            if (target.getSourceCallTarget() != null) {
                                return target.getSourceCallTarget();
                            }
                            return target;
                        }));

        List<OptimizedCallTarget> uniqueSortedTargets = groupedTargets.keySet().stream().sorted(
                        (target1, target2) -> sumCalls(groupedTargets.get(target2), p -> p.getTotalCallCount()) - sumCalls(groupedTargets.get(target1), p -> p.getTotalCallCount())).collect(
                        Collectors.toList());

        int totalDirectCallCount = 0;
        int totalInlinedCallCount = 0;
        int totalIndirectCallCount = 0;
        int totalTotalCallCount = 0;
        int totalInterpretedCallCount = 0;
        int totalInvalidationCount = 0;

        OUT.println();
        OUT.printf(" %-50s  | %-15s || %-15s | %-15s || %-15s | %-15s | %-15s || %3s \n", "Call Target", "Total Calls", "Interp. Calls", "Opt. Calls", "Direct Calls", "Inlined Calls",
                        "Indirect Calls", "Invalidations");
        for (OptimizedCallTarget uniqueCallTarget : uniqueSortedTargets) {
            List<OptimizedCallTarget> allCallTargets = groupedTargets.get(uniqueCallTarget);
            int directCallCount = sumCalls(allCallTargets, p -> p.getDirectCallCount());
            int indirectCallCount = sumCalls(allCallTargets, p -> p.getIndirectCallCount());
            int inlinedCallCount = sumCalls(allCallTargets, p -> p.getInlinedCallCount());
            int interpreterCallCount = sumCalls(allCallTargets, p -> p.getInterpreterCallCount());
            int totalCallCount = sumCalls(allCallTargets, p -> p.getTotalCallCount());
            int invalidationCount = allCallTargets.stream().collect(Collectors.summingInt(target -> target.getCompilationProfile().getInvalidationCount()));

            totalDirectCallCount += directCallCount;
            totalInlinedCallCount += inlinedCallCount;
            totalIndirectCallCount += indirectCallCount;
            totalInvalidationCount += invalidationCount;
            totalInterpretedCallCount += interpreterCallCount;
            totalTotalCallCount += totalCallCount;

            if (totalCallCount > 0) {
                OUT.printf("  %-50s | %15d || %15d | %15d || %15d | %15d | %15d || %3d\n", uniqueCallTarget, totalCallCount, interpreterCallCount, totalCallCount - interpreterCallCount,
                                directCallCount, inlinedCallCount, indirectCallCount, invalidationCount);
            }

        }

        OUT.printf(" %-50s  | %15d || %15d | %15d || %15d | %15d | %15d || %3d\n", "Total", totalTotalCallCount, totalInterpretedCallCount, totalTotalCallCount - totalInterpretedCallCount,
                        totalDirectCallCount, totalInlinedCallCount, totalIndirectCallCount, totalInvalidationCount);

    }

    private static int sumCalls(List<OptimizedCallTarget> targets, Function<TraceCompilationProfile, Integer> function) {
        return targets.stream().collect(Collectors.summingInt(target -> function.apply((TraceCompilationProfile) target.getCompilationProfile())));
    }
}
