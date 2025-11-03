package com.oracle.svm.hosted.analysis.ai.checker;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.svm.hosted.analysis.ai.checker.facts.ConditionFact;
import com.oracle.svm.hosted.analysis.ai.checker.facts.Fact;
import com.oracle.svm.hosted.analysis.ai.domain.memory.AccessPath;
import com.oracle.svm.hosted.analysis.ai.domain.memory.AbstractMemory;
import com.oracle.svm.hosted.analysis.ai.domain.numerical.IntInterval;
import com.oracle.svm.hosted.analysis.ai.fixpoint.state.AbstractState;
import com.oracle.svm.hosted.analysis.ai.log.AbstractInterpretationLogger;
import com.oracle.svm.hosted.analysis.ai.log.LoggerVerbosity;

import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.calc.IntegerLessThanNode;
import jdk.graal.compiler.nodes.calc.IntegerEqualsNode;
import jdk.graal.compiler.nodes.ConstantNode;

import java.util.ArrayList;
import java.util.List;

/**
 * Checker that inspects condition nodes (e.g., IntegerLessThanNode, IntegerEqualsNode)
 * and emits facts if they are always true or always false under the current abstract state.
 */
public class ConditionTruthChecker implements Checker<AbstractMemory> {

    @Override
    public String getDescription() {
        return "Condition truthiness checker";
    }

    @Override
    public List<CheckerResult> check(AnalysisMethod method, AbstractState<AbstractMemory> abstractState) {
        List<CheckerResult> res = new ArrayList<>();
        AbstractInterpretationLogger logger = AbstractInterpretationLogger.getInstance();

        for (Node node : abstractState.getStateMap().keySet()) {
            try {
                if (node instanceof IntegerLessThanNode itn) {
                    AbstractMemory post = abstractState.getPostCondition(node);
                    if (post == null) continue;
                    IntInterval ix = getInterval(itn.getX(), post);
                    IntInterval iy = getInterval(itn.getY(), post);
                    if (ix == null || iy == null) continue;
                    // x < y always true if upper(ix) < lower(iy)
                    if (!ix.isUpperInfinite() && !iy.isLowerInfinite() && ix.getUpper() < iy.getLower()) {
                        res.add(new CheckerResult(CheckerStatus.WARNING, node + " is always true"));
                    } else if (!ix.isLowerInfinite() && !iy.isUpperInfinite() && ix.getLower() >= iy.getUpper()) {
                        res.add(new CheckerResult(CheckerStatus.WARNING, node + " is always false"));
                    }
                } else if (node instanceof IntegerEqualsNode ien) {
                    AbstractMemory post = abstractState.getPostCondition(node);
                    if (post == null) continue;
                    IntInterval ix = getInterval(ien.getX(), post);
                    IntInterval iy = getInterval(ien.getY(), post);
                    if (ix == null || iy == null) continue;
                    // equality always true if both singletons equal
                    boolean ixSingleton = !ix.isTop() && !ix.isBot() && !ix.isLowerInfinite() && !ix.isUpperInfinite() && ix.getLower() == ix.getUpper();
                    boolean iySingleton = !iy.isTop() && !iy.isBot() && !iy.isLowerInfinite() && !iy.isUpperInfinite() && iy.getLower() == iy.getUpper();
                    if (ixSingleton && iySingleton && ix.getLower() == iy.getLower()) {
                        res.add(new CheckerResult(CheckerStatus.WARNING, node + " is always true"));
                    } else if ((!ix.isUpperInfinite() && !iy.isLowerInfinite() && ix.getUpper() < iy.getLower()) || (!iy.isUpperInfinite() && !ix.isLowerInfinite() && iy.getUpper() < ix.getLower())) {
                        res.add(new CheckerResult(CheckerStatus.WARNING, node + " is always false"));
                    }
                }
            } catch (Exception e) {
                logger.log("ConditionTruthChecker error for node " + node + ": " + e.getMessage(), LoggerVerbosity.CHECKER_ERR);
            }
        }

        return res;
    }

    @Override
    public List<Fact> produceFacts(AnalysisMethod method, AbstractState<AbstractMemory> abstractState) {
        List<Fact> facts = new ArrayList<>();
        for (Node node : abstractState.getStateMap().keySet()) {
            try {
                if (node instanceof IntegerLessThanNode itn) {
                    AbstractMemory post = abstractState.getPostCondition(node);
                    if (post == null) continue;
                    IntInterval ix = getInterval(itn.getX(), post);
                    IntInterval iy = getInterval(itn.getY(), post);
                    if (ix == null || iy == null) continue;
                    if (!ix.isUpperInfinite() && !iy.isLowerInfinite() && ix.getUpper() < iy.getLower()) {
                        facts.add(new ConditionFact(node, "lt", "always-true"));
                    } else if (!ix.isLowerInfinite() && !iy.isUpperInfinite() && ix.getLower() >= iy.getUpper()) {
                        facts.add(new ConditionFact(node, "lt", "always-false"));
                    }
                } else if (node instanceof IntegerEqualsNode ien) {
                    AbstractMemory post = abstractState.getPostCondition(node);
                    if (post == null) continue;
                    IntInterval ix = getInterval(ien.getX(), post);
                    IntInterval iy = getInterval(ien.getY(), post);
                    if (ix == null || iy == null) continue;
                    boolean ixSingleton = !ix.isTop() && !ix.isBot() && !ix.isLowerInfinite() && !ix.isUpperInfinite() && ix.getLower() == ix.getUpper();
                    boolean iySingleton = !iy.isTop() && !iy.isBot() && !iy.isLowerInfinite() && !iy.isUpperInfinite() && iy.getLower() == iy.getUpper();
                    if (ixSingleton && iySingleton && ix.getLower() == iy.getLower()) {
                        facts.add(new ConditionFact(node, "eq", "always-true"));
                    } else if ((!ix.isUpperInfinite() && !iy.isLowerInfinite() && ix.getUpper() < iy.getLower()) || (!iy.isUpperInfinite() && !ix.isLowerInfinite() && iy.getUpper() < ix.getLower())) {
                        facts.add(new ConditionFact(node, "eq", "always-false"));
                    }
                }
            } catch (Exception ex) {
                AbstractInterpretationLogger.getInstance().log("ConditionTruthChecker produceFacts error: " + ex.getMessage(), LoggerVerbosity.CHECKER_ERR);
            }
        }
        return facts;
    }

    private IntInterval getInterval(Node n, AbstractMemory mem) {
        if (n == null) return null;
        // if it's constant node
        if (n instanceof ConstantNode cn && cn.asJavaConstant() != null && cn.asJavaConstant().getJavaKind().isNumericInteger()) {
            long v = cn.asJavaConstant().asLong();
            return new IntInterval(v, v);
        }
        String nid = "n" + Integer.toHexString(System.identityHashCode(n));
        return mem.readStore(AccessPath.forLocal(nid));
    }

    @Override
    public boolean isCompatibleWith(AbstractState<?> abstractState) {
        return abstractState.getInitialDomain() instanceof AbstractMemory;
    }
}
