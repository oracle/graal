package com.oracle.graal.truffle.debug;

import static com.oracle.graal.truffle.TruffleCompilerOptions.*;

import java.util.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.truffle.*;
import com.oracle.graal.truffle.TruffleInlining.*;
import com.oracle.truffle.api.nodes.*;

public class TraceCompilationCallTreeListener extends AbstractDebugCompilationListener {

    private TraceCompilationCallTreeListener() {
    }

    public static void install(GraalTruffleRuntime runtime) {
        if (TraceTruffleCompilationCallTree.getValue()) {
            runtime.addCompilationListener(new TraceCompilationCallTreeListener());
        }
    }

    @Override
    public void notifyCompilationSuccess(OptimizedCallTarget target, StructuredGraph graph, CompilationResult result) {
        log(0, "opt call tree", target.toString(), target.getDebugProperties());
        logTruffleCallTree(target);
    }

    private static void logTruffleCallTree(OptimizedCallTarget compilable) {
        CallTreeNodeVisitor visitor = new CallTreeNodeVisitor() {

            public boolean visit(List<TruffleInlining> decisionStack, Node node) {
                if (node instanceof OptimizedDirectCallNode) {
                    OptimizedDirectCallNode callNode = ((OptimizedDirectCallNode) node);
                    int depth = decisionStack == null ? 0 : decisionStack.size() - 1;
                    TruffleInliningDecision inlining = CallTreeNodeVisitor.getCurrentInliningDecision(decisionStack);
                    String dispatched = "<dispatched>";
                    if (inlining != null && inlining.isInline()) {
                        dispatched = "";
                    }
                    Map<String, Object> properties = new LinkedHashMap<>();
                    addASTSizeProperty(callNode.getCurrentCallTarget(), properties);
                    properties.putAll(callNode.getCurrentCallTarget().getDebugProperties());
                    properties.put("Stamp", callNode.getCurrentCallTarget().getArgumentStamp());
                    log((depth * 2), "opt call tree", callNode.getCurrentCallTarget().toString() + dispatched, properties);
                } else if (node instanceof OptimizedIndirectCallNode) {
                    int depth = decisionStack == null ? 0 : decisionStack.size() - 1;
                    log((depth * 2), "opt call tree", "<indirect>", new LinkedHashMap<String, Object>());
                }
                return true;
            }

        };
        compilable.accept(visitor, true);
    }

}
