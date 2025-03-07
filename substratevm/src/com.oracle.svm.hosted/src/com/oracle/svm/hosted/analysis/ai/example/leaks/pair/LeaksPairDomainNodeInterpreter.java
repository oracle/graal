package com.oracle.svm.hosted.analysis.ai.example.leaks.pair;

import com.oracle.svm.hosted.analysis.ai.analyzer.payload.AnalysisPayload;
import com.oracle.svm.hosted.analysis.ai.domain.BooleanOrDomain;
import com.oracle.svm.hosted.analysis.ai.domain.CountDomain;
import com.oracle.svm.hosted.analysis.ai.domain.PairDomain;
import com.oracle.svm.hosted.analysis.ai.fixpoint.state.AbstractStateMap;
import com.oracle.svm.hosted.analysis.ai.interpreter.NodeInterpreter;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.Invoke;
import jdk.graal.compiler.nodes.ReturnNode;

public class LeaksPairDomainNodeInterpreter implements NodeInterpreter<PairDomain<CountDomain, BooleanOrDomain>> {

    @Override
    public PairDomain<CountDomain, BooleanOrDomain> execEdge(Node source,
                                                             Node destination,
                                                             AbstractStateMap<PairDomain<CountDomain, BooleanOrDomain>> abstractStateMap) {
        abstractStateMap.getPreCondition(destination).joinWith(abstractStateMap.getPostCondition(source));
        return abstractStateMap.getPreCondition(destination);
    }

    @Override
    public PairDomain<CountDomain, BooleanOrDomain> execNode(Node node,
                                                             AbstractStateMap<PairDomain<CountDomain, BooleanOrDomain>> abstractStateMap,
                                                             AnalysisPayload<PairDomain<CountDomain, BooleanOrDomain>> payload) {

        PairDomain<CountDomain, BooleanOrDomain> preCondition = abstractStateMap.getPreCondition(node);
        CountDomain preCount = preCondition.getFirst();
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

        return abstractStateMap.getPreCondition(node);
    }

    // TODO: this is not complete
    private boolean opensResource(Invoke invoke) {
        System.out.println("invoke.getTargetMethod().getName() = " + invoke.getTargetMethod().getName());
        System.out.println("invoke.getTargetMethod().getDeclaringClass() = " + invoke.getTargetMethod().getDeclaringClass());
        return invoke.getTargetMethod().getName().contains("<init>");
    }

    private boolean closesResource(Invoke invoke) {
        System.out.println("invoke.getTargetMethod().getName() = " + invoke.getTargetMethod().getName());
        System.out.println("invoke.getTargetMethod().getDeclaringClass() = " + invoke.getTargetMethod().getDeclaringClass());
        return invoke.getTargetMethod().getName().contains("<close>");
    }
}
