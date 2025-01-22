package com.oracle.svm.hosted.analysis.ai.analyzer.example.leaks.count;

import com.oracle.svm.hosted.analysis.ai.domain.CountingDomain;
import com.oracle.svm.hosted.analysis.ai.fixpoint.state.AbstractStateMap;
import com.oracle.svm.hosted.analysis.ai.interpreter.NodeInterpreter;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.Invoke;

public class LeaksCountingDomainNodeInterpreter implements NodeInterpreter<CountingDomain> {

    @Override
    public void execEdge(Node source, Node destination, AbstractStateMap<CountingDomain> abstractStateMap) {
        abstractStateMap.getPreCondition(destination).joinWith(abstractStateMap.getPostCondition(source));
    }


    @Override
    public void execNode(Node node, AbstractStateMap<CountingDomain> abstractStateMap) {
        if (node instanceof Invoke invoke) {
            if (opensResource(invoke)) {
                abstractStateMap.setPostCondition(node, abstractStateMap.getPreCondition(node).getIncremented());
            }
            if (closesResource(invoke)) {
                abstractStateMap.setPostCondition(node, abstractStateMap.getPreCondition(node).getDecremented());
            }
        } else {
            abstractStateMap.setPostCondition(node, abstractStateMap.getPreCondition(node));
        }
    }


    private boolean opensResource(Invoke invoke) {
        return invoke.getTargetMethod().getName().contains("<init>");
    }

    private boolean closesResource(Invoke invoke) {
        return invoke.getTargetMethod().getName().contains("<close>");
    }
}
