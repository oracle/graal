package com.oracle.svm.hosted.analysis.ai.analyzer.example.leaks.count.inter;

import com.oracle.svm.hosted.analysis.ai.domain.CountingDomain;
import com.oracle.svm.hosted.analysis.ai.fixpoint.state.AbstractStateMap;
import com.oracle.svm.hosted.analysis.ai.summary.Summary;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.Invoke;

/**
 * Leak-counting summary will just be a pair of counting domains,
 * one for the pre-condition and one for the post-condition.
 */
public record LeakCountingSummary(CountingDomain getPreCondition,
                                  CountingDomain getPostCondition) implements Summary<CountingDomain> {

    @Override
    public boolean subsumes(Summary<CountingDomain> other) {
        /* This is a simple example, so if we encounter the same method call as in cache we return true */
        return other instanceof LeakCountingSummary;
    }

    @Override
    public void applySummary(Invoke invoke, Node invokeNode, AbstractStateMap<CountingDomain> callerStateMap) {
        callerStateMap.setPostCondition(invokeNode, getPostCondition);
    }
}