package com.oracle.graal.truffle.debug;

import static com.oracle.graal.truffle.TruffleCompilerOptions.*;

import java.util.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.truffle.*;
import com.oracle.truffle.api.nodes.*;

public class TraceCompilationPolymorphismListener extends AbstractDebugCompilationListener {

    private TraceCompilationPolymorphismListener() {
    }

    public static void install(GraalTruffleRuntime runtime) {
        if (TraceTruffleCompilationPolymorphism.getValue()) {
            runtime.addCompilationListener(new TraceCompilationPolymorphismListener());
        }
    }

    @Override
    public void notifyCompilationSuccess(OptimizedCallTarget target, StructuredGraph graph, CompilationResult result) {
        super.notifyCompilationSuccess(target, graph, result);
        target.nodeStream(true).filter(node -> node != null && (node.getCost() == NodeCost.MEGAMORPHIC || node.getCost() == NodeCost.POLYMORPHIC))//
        .forEach(node -> {
            NodeCost cost = node.getCost();
            Map<String, Object> props = new LinkedHashMap<>();
            props.put("simpleName", node.getClass().getSimpleName());
            props.put("subtree", "\n" + NodeUtil.printCompactTreeToString(node));
            String msg = cost == NodeCost.MEGAMORPHIC ? "megamorphic" : "polymorphic";
            log(0, msg, node.toString(), props);
        });
    }

}
