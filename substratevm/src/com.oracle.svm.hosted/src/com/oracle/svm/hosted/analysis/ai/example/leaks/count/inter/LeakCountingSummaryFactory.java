package com.oracle.svm.hosted.analysis.ai.example.leaks.count.inter;

import com.oracle.svm.hosted.analysis.ai.domain.CountDomain;
import com.oracle.svm.hosted.analysis.ai.summary.Summary;
import com.oracle.svm.hosted.analysis.ai.summary.SummaryFactory;
import jdk.graal.compiler.nodes.Invoke;

import java.util.List;

public class LeakCountingSummaryFactory implements SummaryFactory<CountDomain> {

    @Override
    public Summary<CountDomain> createSummary(Invoke invoke, CountDomain callSitePreCondition, List<CountDomain> arguments) {
        /* The arguments are unused since this simple analysis doesn't work with them at all */
        return new LeakCountingSummary(callSitePreCondition, invoke);
    }
}
