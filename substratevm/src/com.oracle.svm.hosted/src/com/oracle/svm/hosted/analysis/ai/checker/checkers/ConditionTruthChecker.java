package com.oracle.svm.hosted.analysis.ai.checker.checkers;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.svm.hosted.analysis.ai.checker.core.Checker;
import com.oracle.svm.hosted.analysis.ai.checker.core.CheckerResult;
import com.oracle.svm.hosted.analysis.ai.checker.core.CheckerStatus;
import com.oracle.svm.hosted.analysis.ai.checker.core.facts.Fact;
import com.oracle.svm.hosted.analysis.ai.checker.core.facts.ConditionTruthFact;
import com.oracle.svm.hosted.analysis.ai.domain.memory.AbstractMemory;
import com.oracle.svm.hosted.analysis.ai.domain.memory.AccessPath;
import com.oracle.svm.hosted.analysis.ai.domain.numerical.IntInterval;
import com.oracle.svm.hosted.analysis.ai.fixpoint.state.AbstractState;

import jdk.graal.compiler.nodes.IfNode;
import jdk.graal.compiler.graph.Node;

import java.util.ArrayList;
import java.util.List;

public final class ConditionTruthChecker implements Checker<AbstractMemory> {

    @Override
    public String getDescription() { return "Condition truthness checker"; }

    @Override
    public List<CheckerResult> check(AnalysisMethod method, AbstractState<AbstractMemory> abstractState) {
        List<CheckerResult> out = new ArrayList<>();
        for (Node n : abstractState.getStateMap().keySet()) {
            if (n instanceof IfNode ifn) {
                Node cond = ifn.condition();
                AbstractMemory mem = abstractState.getPostCondition(n);
                if (mem == null) mem = abstractState.getPreCondition(n);
                if (mem == null) continue;
                IntInterval iv = mem.readStore(AccessPath.forLocal("n" + Integer.toHexString(System.identityHashCode(cond))));
                if (iv != null && !iv.isTop() && !iv.isBot() && !iv.isLowerInfinite() && !iv.isUpperInfinite()) {
                    if (iv.getLower() == 1 && iv.getUpper() == 1) {
                        out.add(new CheckerResult(CheckerStatus.OK, "If is always true: " + ifn));
                    } else if (iv.getLower() == 0 && iv.getUpper() == 0) {
                        out.add(new CheckerResult(CheckerStatus.OK, "If is always false: " + ifn));
                    }
                }
            }
        }
        return out;
    }

    @Override
    public List<Fact> produceFacts(AnalysisMethod method, AbstractState<AbstractMemory> abstractState) {
        List<Fact> facts = new ArrayList<>();
        for (Node n : abstractState.getStateMap().keySet()) {
            if (n instanceof IfNode ifn) {
                Node cond = ifn.condition();
                AbstractMemory mem = abstractState.getPostCondition(n);
                if (mem == null) mem = abstractState.getPreCondition(n);
                if (mem == null) continue;
                IntInterval iv = mem.readStore(AccessPath.forLocal("n" + Integer.toHexString(System.identityHashCode(cond))));
                if (iv != null && !iv.isTop() && !iv.isBot() && !iv.isLowerInfinite() && !iv.isUpperInfinite()) {
                    if (iv.getLower() == 1 && iv.getUpper() == 1) {
                        facts.add(new ConditionTruthFact(ifn, cond, ConditionTruthFact.Truth.ALWAYS_TRUE));
                    } else if (iv.getLower() == 0 && iv.getUpper() == 0) {
                        facts.add(new ConditionTruthFact(ifn, cond, ConditionTruthFact.Truth.ALWAYS_FALSE));
                    }
                }
            }
        }
        return facts;
    }

    @Override
    public boolean isCompatibleWith(AbstractState<?> st) { return st.getInitialDomain() instanceof AbstractMemory; }
}

