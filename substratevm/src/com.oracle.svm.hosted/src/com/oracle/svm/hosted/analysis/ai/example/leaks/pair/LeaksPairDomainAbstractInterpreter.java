package com.oracle.svm.hosted.analysis.ai.example.leaks.pair;

import com.oracle.graal.pointsto.util.AnalysisError;
import com.oracle.svm.hosted.analysis.ai.analyzer.AnalysisOutcome;
import com.oracle.svm.hosted.analysis.ai.analyzer.call.InvokeCallBack;
import com.oracle.svm.hosted.analysis.ai.domain.BooleanOrDomain;
import com.oracle.svm.hosted.analysis.ai.domain.CountDomain;
import com.oracle.svm.hosted.analysis.ai.domain.composite.PairDomain;
import com.oracle.svm.hosted.analysis.ai.example.leaks.InvokeUtil;
import com.oracle.svm.hosted.analysis.ai.fixpoint.state.AbstractState;
import com.oracle.svm.hosted.analysis.ai.interpreter.AbstractInterpreter;
import com.oracle.svm.hosted.analysis.ai.summary.Summary;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.Invoke;
import jdk.graal.compiler.nodes.ReturnNode;

public class LeaksPairDomainAbstractInterpreter implements AbstractInterpreter<PairDomain<CountDomain, BooleanOrDomain>> {

    @Override
    public void execEdge(Node source,
                         Node target,
                         AbstractState<PairDomain<CountDomain, BooleanOrDomain>> abstractState) {
        abstractState.getPreCondition(target).joinWith(abstractState.getPostCondition(source));
    }

    @Override
    public void execNode(Node node,
                         AbstractState<PairDomain<CountDomain, BooleanOrDomain>> abstractState,
                         InvokeCallBack<PairDomain<CountDomain, BooleanOrDomain>> invokeCallBack) {
        PairDomain<CountDomain, BooleanOrDomain> preCondition = abstractState.getPreCondition(node);
        PairDomain<CountDomain, BooleanOrDomain> computedPost = preCondition.copyOf();
        int preCount = preCondition.getFirst().getValue();

        switch (node) {
            case Invoke invoke -> {
                if (InvokeUtil.opensResource(invoke)) {
                    computedPost.getFirst().increment();
                } else if (InvokeUtil.closesResource(invoke)) {
                    computedPost.getFirst().decrement();
                } else {
                    AnalysisOutcome<PairDomain<CountDomain, BooleanOrDomain>> outcome = invokeCallBack.handleInvoke(invoke, node, abstractState);
                    if (outcome.isError()) {
                        throw AnalysisError.interruptAnalysis(outcome.toString());
                    }

                    Summary<PairDomain<CountDomain, BooleanOrDomain>> summary = outcome.summary();
                    computedPost = summary.applySummary(preCondition);
                }
            }

            case ReturnNode returnNode -> {
                if (preCount == 0) {
                    computedPost.getSecond().meetWith(BooleanOrDomain.FALSE);
                } else {
                    computedPost.getSecond().joinWith(BooleanOrDomain.TRUE);
                }
            }

            default -> {
            }
        }

        abstractState.getState(node).setPostCondition(computedPost);
    }
}
