package com.oracle.svm.hosted.analysis.ai.analyzer.example.leaks.pair;

import com.oracle.svm.hosted.analysis.ai.domain.BooleanOrDomain;
import com.oracle.svm.hosted.analysis.ai.domain.CountingDomain;
import com.oracle.svm.hosted.analysis.ai.domain.PairDomain;
import com.oracle.svm.hosted.analysis.ai.fixpoint.state.AbstractState;
import com.oracle.svm.hosted.analysis.ai.fixpoint.state.AbstractStateMap;
import com.oracle.svm.hosted.analysis.ai.summary.Summary;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.Invoke;

public record LeakPairSummary(int summaryCount,
                              boolean returnsResource)
        implements Summary<PairDomain<CountingDomain, BooleanOrDomain>> {

    @Override
    public PairDomain<CountingDomain, BooleanOrDomain> getPreCondition() {
        return new PairDomain<>(new CountingDomain(0), new BooleanOrDomain(false));
    }

    @Override
    public PairDomain<CountingDomain, BooleanOrDomain> getPostCondition() {
        return new PairDomain<>(new CountingDomain(summaryCount), new BooleanOrDomain(returnsResource));
    }

    @Override
    public boolean subsumes(Summary<PairDomain<CountingDomain, BooleanOrDomain>> other) {
        return true;
    }

    @Override
    public void applySummary(Invoke invoke, Node invokeNode, AbstractStateMap<PairDomain<CountingDomain, BooleanOrDomain>> callerStateMap) {
        AbstractState<PairDomain<CountingDomain, BooleanOrDomain>> preState = callerStateMap.getState(invokeNode);
        BooleanOrDomain preReturns = preState.getPreCondition().getSecond().copyOf();
        CountingDomain newCount = new CountingDomain(preState.getPreCondition().getFirst().getValue() + summaryCount);
        callerStateMap.setPostCondition(invokeNode, new PairDomain<>(newCount, preReturns));
    }
}
