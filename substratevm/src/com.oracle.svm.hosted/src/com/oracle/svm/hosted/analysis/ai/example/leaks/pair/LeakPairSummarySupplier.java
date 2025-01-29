package com.oracle.svm.hosted.analysis.ai.example.leaks.pair;

import com.oracle.svm.hosted.analysis.ai.domain.BooleanOrDomain;
import com.oracle.svm.hosted.analysis.ai.domain.CountingDomain;
import com.oracle.svm.hosted.analysis.ai.domain.PairDomain;
import com.oracle.svm.hosted.analysis.ai.fixpoint.state.AbstractState;
import com.oracle.svm.hosted.analysis.ai.summary.Summary;
import com.oracle.svm.hosted.analysis.ai.summary.SummarySupplier;
import jdk.graal.compiler.nodes.Invoke;

public class LeakPairSummarySupplier implements SummarySupplier<PairDomain<CountingDomain, BooleanOrDomain>> {

    @Override
    public Summary<PairDomain<CountingDomain, BooleanOrDomain>> get(Invoke invoke, AbstractState<PairDomain<CountingDomain, BooleanOrDomain>> abstractState) {
        return new LeakPairSummary(0, false);
    }

    @Override
    public Summary<PairDomain<CountingDomain, BooleanOrDomain>> createCompleteSummary(
            Summary<PairDomain<CountingDomain, BooleanOrDomain>> preconditionSummary,
            AbstractState<PairDomain<CountingDomain, BooleanOrDomain>> abstractState) {
        PairDomain<CountingDomain, BooleanOrDomain> postCondition = abstractState.getPostCondition();
        return new LeakPairSummary(postCondition.getFirst().getValue(), postCondition.getSecond().getValue());
    }
}
