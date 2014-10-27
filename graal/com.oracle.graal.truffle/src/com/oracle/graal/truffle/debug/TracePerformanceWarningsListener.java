package com.oracle.graal.truffle.debug;

import static com.oracle.graal.truffle.TruffleCompilerOptions.*;

import java.util.*;

import com.oracle.graal.nodes.*;
import com.oracle.graal.truffle.*;

public class TracePerformanceWarningsListener extends AbstractDebugCompilationListener {

    private TracePerformanceWarningsListener() {
    }

    public static void install(GraalTruffleRuntime runtime) {
        if (isEnabled()) {
            runtime.addCompilationListener(new TracePerformanceWarningsListener());
        }
    }

    @Override
    public void notifyCompilationTruffleTierFinished(OptimizedCallTarget target, StructuredGraph graph) {
    }

    public static boolean isEnabled() {
        return TraceTrufflePerformanceWarnings.getValue();
    }

    public static void logPerformanceWarning(String details, Map<String, Object> properties) {
        log(0, "perf warn", details, properties);
    }

}
