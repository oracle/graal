package com.oracle.svm.hosted.analysis.ai.example.leaks.set;

import com.oracle.svm.hosted.analysis.ai.analyzer.payload.AnalysisPayload;
import com.oracle.svm.hosted.analysis.ai.domain.SetDomain;
import com.oracle.svm.hosted.analysis.ai.fixpoint.state.AbstractStateMap;
import com.oracle.svm.hosted.analysis.ai.interpreter.NodeInterpreter;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.Invoke;

public class IdentifierSetDomainNodeInterpreter implements NodeInterpreter<SetDomain<String>> {

    @Override
    public SetDomain<String> execEdge(Node source,
                                      Node destination,
                                      AbstractStateMap<SetDomain<String>> abstractStateMap,
                                      AnalysisPayload<SetDomain<String>> payload) {
        abstractStateMap.getPreCondition(destination).joinWith(abstractStateMap.getPostCondition(source));
        return abstractStateMap.getPreCondition(destination);
    }

    @Override
    public SetDomain<String> execNode(Node node,
                                      AbstractStateMap<SetDomain<String>> abstractStateMap,
                                      AnalysisPayload<SetDomain<String>> payload) {

        SetDomain<String> preCond = abstractStateMap.getPreCondition(node).copyOf();
        if (node instanceof Invoke invoke) {
            if (opensResource(invoke)) {
                preCond.getValue().add(getIdentifier(node));
                abstractStateMap.setPostCondition(node, preCond);
            }
            if (closesResource(invoke)) {
                preCond.getValue().remove(getIdentifier(node));
                abstractStateMap.setPostCondition(node, preCond);
            }
        } else {
            abstractStateMap.getPostCondition(node).joinWith(abstractStateMap.getPreCondition(node));
        }

        return abstractStateMap.getPostCondition(node);
    }

    private boolean opensResource(Invoke invoke) {
        return invoke.getTargetMethod().getName().contains("<init>");
    }

    private boolean closesResource(Invoke invoke) {
        return invoke.getTargetMethod().getName().contains("<close>");
    }

    private String getIdentifier(Node node) {
        return node.getNodeSourcePosition().toString();
    }
}
