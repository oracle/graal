package com.oracle.svm.hosted.analysis.ai.example.leaks.count.inter;

import com.oracle.svm.hosted.analysis.ai.domain.CountingDomain;
import com.oracle.svm.hosted.analysis.ai.fixpoint.state.AbstractState;
import com.oracle.svm.hosted.analysis.ai.summary.Summary;
import com.oracle.svm.hosted.analysis.ai.summary.SummarySupplier;
import jdk.graal.compiler.nodes.Invoke;

public class LeakCountingSummarySupplier implements SummarySupplier<CountingDomain> {

    @Override
    public Summary<CountingDomain> get(Invoke invoke, AbstractState<CountingDomain> abstractState) {
        CountingDomain preCondition = abstractState.getPreCondition();
        return new LeakCountingSummary(preCondition, null);
    }

    @Override
    public Summary<CountingDomain> createCompleteSummary(Summary<CountingDomain> preconditionSummary, AbstractState<CountingDomain> abstractState) {
        CountingDomain postCondition = abstractState.getPostCondition();
        return new LeakCountingSummary(preconditionSummary.getPreCondition(), postCondition);
    }
}