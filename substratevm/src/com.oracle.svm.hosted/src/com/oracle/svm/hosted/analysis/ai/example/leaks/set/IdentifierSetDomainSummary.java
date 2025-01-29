package com.oracle.svm.hosted.analysis.ai.example.leaks.set;

import com.oracle.svm.hosted.analysis.ai.domain.SetDomain;
import com.oracle.svm.hosted.analysis.ai.fixpoint.state.AbstractStateMap;
import com.oracle.svm.hosted.analysis.ai.summary.Summary;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.Invoke;

public record IdentifierSetDomainSummary(SetDomain<String> preCondition,
                                         SetDomain<String> postCondition)
        implements Summary<SetDomain<String>> {

    @Override
    public SetDomain<String> getPreCondition() {
        return preCondition;
    }

    @Override
    public SetDomain<String> getPostCondition() {
        return postCondition;
    }

    @Override
    public boolean subsumes(Summary<SetDomain<String>> other) {
        return true;
    }

    @Override
    public void applySummary(Invoke invoke, Node invokeNode, AbstractStateMap<SetDomain<String>> callerStateMap) {
        callerStateMap.setPostCondition(invokeNode, postCondition);
    }
}
