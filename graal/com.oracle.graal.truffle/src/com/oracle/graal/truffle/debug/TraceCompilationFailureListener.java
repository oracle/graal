package com.oracle.graal.truffle.debug;

import java.util.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.truffle.*;

public class TraceCompilationFailureListener extends AbstractDebugCompilationListener {

    private TraceCompilationFailureListener() {
    }

    public static void install(GraalTruffleRuntime runtime) {
        runtime.addCompilationListener(new TraceCompilationFailureListener());
    }

    @Override
    public void notifyCompilationFailed(OptimizedCallTarget target, StructuredGraph graph, Throwable t) {
        if (isPermanentBailout(t)) {
            Map<String, Object> properties = new LinkedHashMap<>();
            properties.put("Reason", t.toString());
            log(0, "opt fail", target.toString(), properties);
        }
    }

    public static final boolean isPermanentBailout(Throwable t) {
        return !(t instanceof BailoutException) || ((BailoutException) t).isPermanent();
    }

}
