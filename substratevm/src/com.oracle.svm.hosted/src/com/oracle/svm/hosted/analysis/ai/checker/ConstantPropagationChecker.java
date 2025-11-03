package com.oracle.svm.hosted.analysis.ai.checker;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.svm.hosted.analysis.ai.checker.facts.ConstantFact;
import com.oracle.svm.hosted.analysis.ai.checker.facts.Fact;
import com.oracle.svm.hosted.analysis.ai.domain.memory.AccessPath;
import com.oracle.svm.hosted.analysis.ai.domain.memory.AbstractMemory;
import com.oracle.svm.hosted.analysis.ai.domain.numerical.IntInterval;
import com.oracle.svm.hosted.analysis.ai.fixpoint.state.AbstractState;
import com.oracle.svm.hosted.analysis.ai.log.AbstractInterpretationLogger;
import com.oracle.svm.hosted.analysis.ai.log.LoggerVerbosity;

import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.java.StoreFieldNode;
import jdk.vm.ci.meta.ResolvedJavaField;

import java.util.ArrayList;
import java.util.List;

public class ConstantPropagationChecker implements Checker<AbstractMemory> {
    private static final String NODE_PREFIX = "n";

    private static String nodeId(Node n) {
        return NODE_PREFIX + Integer.toHexString(System.identityHashCode(n));
    }

    public ConstantPropagationChecker() {
    }

    @Override
    public String getDescription() {
        return "Constant propagation checker";
    }

    @Override
    public List<CheckerResult> check(AnalysisMethod method, AbstractState<AbstractMemory> abstractState) {
        List<CheckerResult> results = new ArrayList<>();
        AbstractInterpretationLogger logger = AbstractInterpretationLogger.getInstance();

        for (Node node : abstractState.getStateMap().keySet()) {
            AbstractMemory post = abstractState.getPostCondition(node);
            if (post == null) continue;

            String nid = nodeId(node);
            try {
                var p = post.lookupTempByName(nid);
                if (p != null) {
                    IntInterval v = post.readStore(p);
                    if (v != null && !v.isTop() && !v.isBot() && !v.isLowerInfinite() && !v.isUpperInfinite() && v.getLower() == v.getUpper()) {
                        long c = v.getLower();
                        String detail = "Node constant: " + node + " = " + c;
                        results.add(new CheckerResult(CheckerStatus.WARNING, detail));
                    }
                }
            } catch (Exception e) {
                logger.log("Error while checking node " + node + ": " + e.getMessage(), LoggerVerbosity.CHECKER_ERR);
            }
        }

        return results;
    }

    @Override
    public List<Fact> produceFacts(AnalysisMethod method, AbstractState<AbstractMemory> abstractState) {
        List<Fact> facts = new ArrayList<>();
        for (Node node : abstractState.getStateMap().keySet()) {
            AbstractMemory post = abstractState.getPostCondition(node);
            if (post == null) continue;

            // 0) constant nodes
            if (node instanceof ConstantNode cn) {
                if (cn.asJavaConstant() != null && cn.asJavaConstant().getJavaKind().isNumericInteger()) {
                    long v = cn.asJavaConstant().asLong();
                    facts.add(new ConstantFact(node, v));
                }
            }

            // 1) temps bound to this node
            String nid = nodeId(node);
            var p = post.lookupTempByName(nid);
            if (p != null) {
                var iv = post.readStore(p);
                if (iv != null && !iv.isTop() && !iv.isBot() && !iv.isLowerInfinite() && !iv.isUpperInfinite() && iv.getLower() == iv.getUpper()) {
                    long c = iv.getLower();
                    facts.add(new ConstantFact(node, c));
                }
            }

            // 2) if this is a StoreFieldNode check the field value in the post-condition
            if (node instanceof StoreFieldNode sfn) {
                ResolvedJavaField field = sfn.field();
                if (field != null && field.getType().getJavaKind().isNumericInteger()) {
                    if (field.isStatic()) {
                        String className = field.getDeclaringClass().getName();
                        AccessPath key = AccessPath.forStaticClass(className).appendField(field.getName());
                        IntInterval v = post.readStore(key);
                        if (v != null && !v.isTop() && !v.isBot() && !v.isLowerInfinite() && !v.isUpperInfinite() && v.getLower() == v.getUpper()) {
                            long c = v.getLower();
                            facts.add(new ConstantFact(node, c));
                        }
                    } else {
                        // instance field: try to resolve base from evaluated temps
                        Node objNode = sfn.object();
                        AccessPath base = null;
                        if (objNode != null) base = post.lookupTempByName(nodeId(objNode));
                        if (base != null) {
                            AccessPath key = base.appendField(field.getName());
                            IntInterval v = post.readStore(key);
                            if (v != null && !v.isTop() && !v.isBot() && !v.isLowerInfinite() && !v.isUpperInfinite() && v.getLower() == v.getUpper()) {
                                long c = v.getLower();
                                facts.add(new ConstantFact(node, c));
                            }
                        }
                    }
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
