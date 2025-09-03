package com.oracle.svm.hosted.analysis.ai.example.backward;

import com.oracle.svm.hosted.analysis.ai.analyzer.call.InvokeCallBack;
import com.oracle.svm.hosted.analysis.ai.domain.EmptyDomain;
import com.oracle.svm.hosted.analysis.ai.fixpoint.state.AbstractState;
import com.oracle.svm.hosted.analysis.ai.interpreter.AbstractInterpreter;
import jdk.graal.compiler.graph.Node;

public class EmptyDomainInterpreter implements AbstractInterpreter<EmptyDomain> {

    @Override
    public void execEdge(Node source, Node target, AbstractState<EmptyDomain> abstractState) {
    }

    @Override
    public void execNode(Node node, AbstractState<EmptyDomain> abstractState, InvokeCallBack<EmptyDomain> invokeCallBack) {
    }
}
