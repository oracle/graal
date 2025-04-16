package com.oracle.svm.hosted.analysis.ai.example.backward;

import com.oracle.svm.hosted.analysis.ai.analyzer.call.InvokeCallBack;
import com.oracle.svm.hosted.analysis.ai.domain.EmptyDomain;
import com.oracle.svm.hosted.analysis.ai.fixpoint.state.AbstractStateMap;
import com.oracle.svm.hosted.analysis.ai.interpreter.NodeInterpreter;
import jdk.graal.compiler.graph.Node;

public class EmptyDomainInterpreter implements NodeInterpreter<EmptyDomain> {

    @Override
    public EmptyDomain execEdge(Node source, Node target, AbstractStateMap<EmptyDomain> abstractStateMap) {
        return abstractStateMap.getPreCondition(source);
    }
    
    @Override
    public EmptyDomain execNode(Node node, AbstractStateMap<EmptyDomain> abstractStateMap, InvokeCallBack<EmptyDomain> invokeCallBack) {
        return abstractStateMap.getPreCondition(node);
    }
}
