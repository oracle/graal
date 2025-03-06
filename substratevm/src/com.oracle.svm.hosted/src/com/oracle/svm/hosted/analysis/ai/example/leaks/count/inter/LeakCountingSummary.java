package com.oracle.svm.hosted.analysis.ai.example.leaks.count.inter;

import com.oracle.svm.hosted.analysis.ai.domain.CountDomain;
import com.oracle.svm.hosted.analysis.ai.summary.Summary;
import jdk.graal.compiler.nodes.Invoke;

/**
 * Leak-counting summary will just be a pair of counting domains,
 * one for the pre-condition and one for the post-condition.
 */
public class LeakCountingSummary implements Summary<CountDomain> {

    private final CountDomain preCondition;
    private CountDomain postCondition;
    private final Invoke invoke;

    public LeakCountingSummary(CountDomain preCondition, Invoke invoke) {
        this.preCondition = preCondition;
        this.postCondition = new CountDomain(0); /* We haven't opened any files yet */
        this.invoke = invoke;
    }

    @Override
    public Invoke getInvoke() {
        return invoke;
    }

    @Override
    public CountDomain getPreCondition() {
        return null;
    }

    @Override
    public CountDomain getPostCondition() {
        return postCondition;
    }

    @Override
    public boolean subsumes(Summary<CountDomain> other) {
        /* This is a simple example, so if we encounter the same analysisMethod call as in cache we return true */
        if (!(other instanceof LeakCountingSummary)) {
            return false;
        }

        return invoke.getTargetMethod().equals(other.getInvoke().getTargetMethod());
    }

    @Override
    public void finalizeSummary(CountDomain postCondition) {
        this.postCondition = postCondition;
    }


    @Override
    public CountDomain applySummary(CountDomain callerPreCondition) {
        int newValue = callerPreCondition.getValue() + postCondition.getValue();
        return new CountDomain(newValue);
    }
}
