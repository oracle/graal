package com.oracle.svm.hosted.analysis.ai.example.leaks.pair.inter;

import com.oracle.svm.hosted.analysis.ai.domain.BooleanOrDomain;
import com.oracle.svm.hosted.analysis.ai.domain.CountDomain;
import com.oracle.svm.hosted.analysis.ai.domain.PairDomain;
import com.oracle.svm.hosted.analysis.ai.summary.Summary;
import jdk.graal.compiler.nodes.Invoke;

public class LeakPairSummary implements Summary<PairDomain<CountDomain, BooleanOrDomain>> {

    private final PairDomain<CountDomain, BooleanOrDomain> preCondition;
    private PairDomain<CountDomain, BooleanOrDomain> postCondition;
    private final Invoke invoke;

    public LeakPairSummary(PairDomain<CountDomain, BooleanOrDomain> preCondition, Invoke invoke) {
        this.preCondition = preCondition;
        this.postCondition = this.preCondition.copyOf();
        this.invoke = invoke;
    }

    @Override
    public Invoke getInvoke() {
        return invoke;
    }

    @Override
    public PairDomain<CountDomain, BooleanOrDomain> getPreCondition() {
        return preCondition;
    }

    @Override
    public PairDomain<CountDomain, BooleanOrDomain> getPostCondition() {
        return postCondition;
    }

    @Override
    public boolean subsumes(Summary<PairDomain<CountDomain, BooleanOrDomain>> other) {
        if (!(other instanceof LeakPairSummary)) {
            return false;
        }

        return invoke.getTargetMethod().equals(other.getInvoke().getTargetMethod());
    }

    @Override
    public void finalizeSummary(PairDomain<CountDomain, BooleanOrDomain> postCondition) {
        this.postCondition = postCondition.copyOf();
    }

    @Override
    public PairDomain<CountDomain, BooleanOrDomain> applySummary(PairDomain<CountDomain, BooleanOrDomain> callerPreCondition) {
        int newCount = postCondition.getFirst().getValue() + callerPreCondition.getFirst().getValue();
        int maxValue = callerPreCondition.getFirst().getMaxValue();
        return new PairDomain<>(new CountDomain(newCount, maxValue), postCondition.getSecond());
    }
}
