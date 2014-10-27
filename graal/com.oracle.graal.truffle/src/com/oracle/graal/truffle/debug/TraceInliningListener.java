package com.oracle.graal.truffle.debug;

import static com.oracle.graal.truffle.TruffleCompilerOptions.*;

import java.util.*;

import com.oracle.graal.nodes.*;
import com.oracle.graal.truffle.*;

public class TraceInliningListener extends AbstractDebugCompilationListener {

    private TraceInliningListener() {
    }

    public static void install(GraalTruffleRuntime runtime) {
        if (TraceTruffleInlining.getValue()) {
            runtime.addCompilationListener(new TraceInliningListener());
        }
    }

    @Override
    public void notifyCompilationTruffleTierFinished(OptimizedCallTarget target, StructuredGraph graph) {
        TruffleInlining inlining = target.getInlining();
        if (inlining == null) {
            return;
        }

        log(0, "inline start", target.toString(), target.getDebugProperties());
        logInliningDecisionRecursive(inlining, 1);
        log(0, "inline done", target.toString(), target.getDebugProperties());
    }

    private static void logInliningDecisionRecursive(TruffleInlining result, int depth) {
        for (TruffleInliningDecision decision : result) {
            TruffleInliningProfile profile = decision.getProfile();
            boolean inlined = decision.isInline();
            String msg = inlined ? "inline success" : "inline failed";
            logInlinedImpl(msg, decision.getProfile().getCallNode(), profile, depth);
            if (inlined) {
                logInliningDecisionRecursive(decision, depth + 1);
            }
        }
    }

    private static void logInlinedImpl(String status, OptimizedDirectCallNode callNode, TruffleInliningProfile profile, int depth) {
        Map<String, Object> properties = new LinkedHashMap<>();
        if (profile != null) {
            properties.putAll(profile.getDebugProperties());
        }
        log((depth * 2), status, callNode.getCurrentCallTarget().toString(), properties);
    }

}
