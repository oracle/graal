package com.oracle.svm.hosted.analysis.ai.example.leaks.count;

import com.oracle.graal.pointsto.util.AnalysisError;
import com.oracle.svm.hosted.analysis.ai.analyzer.AnalysisOutcome;
import com.oracle.svm.hosted.analysis.ai.analyzer.call.InvokeCallBack;
import com.oracle.svm.hosted.analysis.ai.domain.CountDomain;
import com.oracle.svm.hosted.analysis.ai.example.leaks.InvokeUtil;
import com.oracle.svm.hosted.analysis.ai.fixpoint.state.AbstractState;
import com.oracle.svm.hosted.analysis.ai.fixpoint.state.AbstractStateMap;
import com.oracle.svm.hosted.analysis.ai.interpreter.NodeInterpreter;
import com.oracle.svm.hosted.analysis.ai.summary.Summary;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.Invoke;

public class LeaksCountingDomainNodeInterpreter implements NodeInterpreter<CountDomain> {

    @Override
    public CountDomain execEdge(Node source,
                                Node target,
                                AbstractStateMap<CountDomain> abstractStateMap) {
        abstractStateMap.getPreCondition(target).joinWith(abstractStateMap.getPostCondition(source));
        return abstractStateMap.getPreCondition(target);
    }

    @Override
    public CountDomain execNode(Node node, AbstractStateMap<CountDomain> abstractStateMap, InvokeCallBack<CountDomain> invokeCallBack) {
        AbstractState<CountDomain> state = abstractStateMap.getState(node);
        CountDomain preCondition = state.getPreCondition();
        CountDomain computedPost = preCondition.copyOf();

        switch (node) {
            case Invoke invoke -> {
                if (InvokeUtil.opensResource(invoke)) {
                    computedPost.increment();
                } else if (InvokeUtil.closesResource(invoke)) {
                    computedPost.decrement();
                } else {
                    /* We can use analyzeDependencyCallback to analyze calls to other methods */
                    AnalysisOutcome<CountDomain> result = invokeCallBack.handleCall(invoke, node, abstractStateMap);
                    if (result.isError()) {
                        throw AnalysisError.interruptAnalysis(result.toString());
                    }
                    Summary<CountDomain> summary = result.summary();
                    computedPost = summary.applySummary(preCondition);
                }
            }

            default -> {
                // TODO: handle default case in the framework
            }
        }

        state.setPostCondition(computedPost);
        return computedPost;
    }
}
