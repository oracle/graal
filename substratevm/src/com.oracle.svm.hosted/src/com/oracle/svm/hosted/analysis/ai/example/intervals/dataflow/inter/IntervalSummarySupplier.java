package com.oracle.svm.hosted.analysis.ai.example.intervals.dataflow.inter;

import com.oracle.svm.hosted.analysis.ai.domain.IntInterval;
import com.oracle.svm.hosted.analysis.ai.fixpoint.state.AbstractState;
import com.oracle.svm.hosted.analysis.ai.summary.Summary;
import com.oracle.svm.hosted.analysis.ai.summary.SummarySupplier;
import jdk.graal.compiler.nodes.Invoke;

import java.util.List;

public class IntervalSummarySupplier implements SummarySupplier<IntInterval> {

    @Override
    public Summary<IntInterval> get(Invoke invoke, AbstractState<IntInterval> abstractState, List<IntInterval> actualArguments) {
        return new IntervalSummary(new IntInterval(), new IntInterval(), actualArguments);
    }

    @Override
    public Summary<IntInterval> createCompleteSummary(Summary<IntInterval> preConditionSummary, AbstractState<IntInterval> abstractState) {
        IntInterval postCondition = abstractState.getPostCondition();
        return new IntervalSummary(preConditionSummary.preCondition(), postCondition, preConditionSummary.getActualArguments());
    }
}
