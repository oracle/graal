package com.oracle.svm.hosted.analysis.ai.analyzer.example.leaks.pair;

import com.oracle.svm.hosted.analysis.ai.domain.BooleanOrDomain;
import com.oracle.svm.hosted.analysis.ai.domain.CountingDomain;
import com.oracle.svm.hosted.analysis.ai.domain.PairDomain;
import com.oracle.svm.hosted.analysis.ai.fixpoint.state.AbstractStateMap;
import com.oracle.svm.hosted.analysis.ai.interpreter.NodeInterpreter;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.Invoke;
import jdk.graal.compiler.nodes.ReturnNode;

public class LeaksPairDomainNodeInterpreter implements NodeInterpreter<PairDomain<CountingDomain, BooleanOrDomain>> {

    @Override
    public void execEdge(Node source, Node destination, AbstractStateMap<PairDomain<CountingDomain, BooleanOrDomain>> abstractStateMap) {
        abstractStateMap.getPreCondition(destination).joinWith(abstractStateMap.getPostCondition(source));
    }

    @Override
    public void execNode(Node node, AbstractStateMap<PairDomain<CountingDomain, BooleanOrDomain>> abstractStateMap) {
        PairDomain<CountingDomain, BooleanOrDomain> preCondition = abstractStateMap.getPreCondition(node);
        CountingDomain preCount = preCondition.getFirst();
        BooleanOrDomain preReturns = preCondition.getSecond();

        if (node instanceof Invoke invoke) {
            if (opensResource(invoke)) {
                abstractStateMap.setPostCondition(node, new PairDomain<>(preCount.getIncremented(), preReturns));
            }
            if (closesResource(invoke)) {
                abstractStateMap.setPostCondition(node, new PairDomain<>(preCount.getDecremented(), preReturns));
            }
        } else if (node instanceof ReturnNode && preCount.getValue() > 0) {
            abstractStateMap.setPostCondition(node, new PairDomain<>(preCount, new BooleanOrDomain(true)));
        } else {
            abstractStateMap.getPostCondition(node).joinWith(abstractStateMap.getPreCondition(node));
        }
    }

    private boolean opensResource(Invoke invoke) {
        return invoke.getTargetMethod().getName().contains("<init>");
    }

    private boolean closesResource(Invoke invoke) {
        return invoke.getTargetMethod().getName().contains("<close>");
    }
}
