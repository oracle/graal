package com.oracle.svm.hosted.analysis.ai.example.leaks.count;

import com.oracle.graal.pointsto.util.AnalysisError;
import com.oracle.svm.hosted.analysis.ai.analyzer.AnalysisOutcome;
import com.oracle.svm.hosted.analysis.ai.analyzer.call.InvokeCallBack;
import com.oracle.svm.hosted.analysis.ai.domain.CountDomain;
import com.oracle.svm.hosted.analysis.ai.example.leaks.InvokeUtil;
import com.oracle.svm.hosted.analysis.ai.fixpoint.state.AbstractState;
import com.oracle.svm.hosted.analysis.ai.fixpoint.state.NodeState;
import com.oracle.svm.hosted.analysis.ai.interpreter.AbstractInterpreter;
import com.oracle.svm.hosted.analysis.ai.summary.Summary;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.Invoke;

public class LeaksCountingDomainAbstractInterpreter implements AbstractInterpreter<CountDomain> {

    @Override
    public CountDomain execEdge(Node source,
                                Node target,
                                AbstractState<CountDomain> abstractState) {
        abstractState.getPreCondition(target).joinWith(abstractState.getPostCondition(source));
        return abstractState.getPreCondition(target);
    }

    @Override
    public CountDomain execNode(Node node, AbstractState<CountDomain> abstractState, InvokeCallBack<CountDomain> invokeCallBack) {
        NodeState<CountDomain> state = abstractState.getState(node);
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
                    AnalysisOutcome<CountDomain> result = invokeCallBack.handleInvoke(invoke, node, abstractState);
                    if (result.isError()) {
                        throw AnalysisError.interruptAnalysis(result.toString());
                    }
                    Summary<CountDomain> summary = result.summary();
                    computedPost = summary.applySummary(preCondition);
                }
            }

            default -> {
                /* Leave the post-condition as-is */
            }
        }

        state.setPostCondition(computedPost);
        return computedPost;
    }
}
