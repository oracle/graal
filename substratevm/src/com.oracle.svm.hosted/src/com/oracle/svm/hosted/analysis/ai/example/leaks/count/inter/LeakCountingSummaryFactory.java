package com.oracle.svm.hosted.analysis.ai.example.leaks.count.inter;

import com.oracle.svm.hosted.analysis.ai.domain.CountDomain;
import com.oracle.svm.hosted.analysis.ai.summary.Summary;
import com.oracle.svm.hosted.analysis.ai.summary.SummaryFactory;
import jdk.graal.compiler.nodes.Invoke;

import java.util.List;

public class LeakCountingSummaryFactory implements SummaryFactory<CountDomain> {

    @Override
    public Summary<CountDomain> createSummary(Invoke invoke, CountDomain callerPreCondition, List<CountDomain> domainArguments) {
        return new LeakCountingSummary(new CountDomain(0, callerPreCondition.getMaxValue()), invoke);
    }
}
