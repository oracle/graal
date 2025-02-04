package com.oracle.svm.hosted.analysis.ai.example.leaks.set.inter;

import com.oracle.svm.hosted.analysis.ai.domain.SetDomain;
import com.oracle.svm.hosted.analysis.ai.fixpoint.state.AbstractState;
import com.oracle.svm.hosted.analysis.ai.summary.Summary;
import com.oracle.svm.hosted.analysis.ai.summary.SummarySupplier;
import jdk.graal.compiler.nodes.Invoke;

public class IdentifierSetDomainSummarySupplier implements SummarySupplier<SetDomain<String>> {

    @Override
    public Summary<SetDomain<String>> get(Invoke invoke, AbstractState<SetDomain<String>> abstractState) {
        SetDomain<String> preCondition = abstractState.getPreCondition().copyOf();
        return new IdentifierSetDomainSummary(preCondition, null);
    }

    @Override
    public Summary<SetDomain<String>> createCompleteSummary(Summary<SetDomain<String>> preconditionSummary,
                                                            AbstractState<SetDomain<String>> abstractState) {
        SetDomain<String> postCondition = abstractState.getPostCondition();
        return new IdentifierSetDomainSummary(preconditionSummary.getPreCondition(), postCondition);
    }
}
