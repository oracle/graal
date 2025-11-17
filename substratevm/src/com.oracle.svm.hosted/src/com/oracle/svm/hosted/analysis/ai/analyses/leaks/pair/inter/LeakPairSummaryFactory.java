package com.oracle.svm.hosted.analysis.ai.analyses.leaks.pair.inter;

import com.oracle.svm.hosted.analysis.ai.domain.util.BooleanOrDomain;
import com.oracle.svm.hosted.analysis.ai.domain.util.CountDomain;
import com.oracle.svm.hosted.analysis.ai.domain.composite.PairDomain;
import com.oracle.svm.hosted.analysis.ai.summary.Summary;
import com.oracle.svm.hosted.analysis.ai.summary.SummaryFactory;
import jdk.graal.compiler.nodes.Invoke;

import java.util.List;

public class LeakPairSummaryFactory implements SummaryFactory<PairDomain<CountDomain, BooleanOrDomain>> {

    @Override
    public Summary<PairDomain<CountDomain, BooleanOrDomain>> createSummary(Invoke invoke,
                                                                           PairDomain<CountDomain, BooleanOrDomain> callerPreCondition,
                                                                           List<PairDomain<CountDomain, BooleanOrDomain>> domainArguments) {
        CountDomain summaryPreCount = new CountDomain(0, callerPreCondition.first().getMaxValue());
        return new LeakPairSummary(new PairDomain<>(summaryPreCount, BooleanOrDomain.FALSE), invoke);
    }
}
