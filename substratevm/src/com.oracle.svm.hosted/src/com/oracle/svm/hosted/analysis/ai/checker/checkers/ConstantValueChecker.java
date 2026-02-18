package com.oracle.svm.hosted.analysis.ai.checker.checkers;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.svm.hosted.analysis.ai.checker.core.Checker;
import com.oracle.svm.hosted.analysis.ai.checker.facts.ConstantFact;
import com.oracle.svm.hosted.analysis.ai.checker.facts.Fact;
import com.oracle.svm.hosted.analysis.ai.domain.memory.AccessPath;
import com.oracle.svm.hosted.analysis.ai.domain.memory.AbstractMemory;
import com.oracle.svm.hosted.analysis.ai.domain.numerical.IntInterval;
import com.oracle.svm.hosted.analysis.ai.fixpoint.state.AbstractState;

import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.calc.FloatingNode;
import jdk.graal.compiler.nodes.java.NewArrayNode;
import jdk.graal.compiler.nodes.java.StoreFieldNode;
import jdk.vm.ci.meta.ResolvedJavaField;

import java.util.ArrayList;
import java.util.List;

public class ConstantValueChecker implements Checker<AbstractMemory> {
    private static final String NODE_PREFIX = "n";

    private static String nodeId(Node n) {
        return NODE_PREFIX + Integer.toHexString(System.identityHashCode(n));
    }

    public ConstantValueChecker() {
    }

    @Override
    public String getDescription() {
        return "Constant propagation checker";
    }

    @Override
    public List<Fact> produceFacts(AnalysisMethod method, AbstractState<AbstractMemory> abstractState) {
        List<Fact> facts = new ArrayList<>();
        for (Node node : abstractState.getStateMap().keySet()) {
            AbstractMemory post = abstractState.getPostCondition(node);
            if (post == null) continue;

            if (node instanceof ConstantNode cn || !(node instanceof FloatingNode)) {
                continue;
            }

            /* 1) temps bound to this node */
            String nid = nodeId(node);
            var p = post.lookupTempByName(nid);
            if (p != null) {
                var iv = post.readStore(p);
                if (iv != null && iv.isConstantValue()) {
                    long c = iv.getLower();
                    facts.add(new ConstantFact(node, c));
                }
            }
        }
        return facts;
    }

    @Override
    public boolean isCompatibleWith(AbstractState<?> abstractState) {
        return abstractState.getInitialDomain() instanceof AbstractMemory;
    }
}
