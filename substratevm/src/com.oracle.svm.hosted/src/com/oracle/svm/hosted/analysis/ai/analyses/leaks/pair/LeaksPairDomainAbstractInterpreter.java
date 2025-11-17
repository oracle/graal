package com.oracle.svm.hosted.analysis.ai.analyses.leaks.pair;

import com.oracle.graal.pointsto.util.AnalysisError;
import com.oracle.svm.hosted.analysis.ai.analyzer.AnalysisOutcome;
import com.oracle.svm.hosted.analysis.ai.analyzer.call.InvokeCallBack;
import com.oracle.svm.hosted.analysis.ai.domain.util.BooleanOrDomain;
import com.oracle.svm.hosted.analysis.ai.domain.util.CountDomain;
import com.oracle.svm.hosted.analysis.ai.domain.composite.PairDomain;
import com.oracle.svm.hosted.analysis.ai.analyses.leaks.InvokeUtil;
import com.oracle.svm.hosted.analysis.ai.fixpoint.context.IteratorContext;
import com.oracle.svm.hosted.analysis.ai.fixpoint.state.AbstractState;
import com.oracle.svm.hosted.analysis.ai.interpreter.AbstractInterpreter;
import com.oracle.svm.hosted.analysis.ai.summary.Summary;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.Invoke;
import jdk.graal.compiler.nodes.ReturnNode;

public class LeaksPairDomainAbstractInterpreter implements AbstractInterpreter<PairDomain<CountDomain, BooleanOrDomain>> {

    @Override
    public void execEdge(Node source, Node target, AbstractState<PairDomain<CountDomain, BooleanOrDomain>> abstractState, IteratorContext iteratorContext) {
        abstractState.getPreCondition(target).joinWith(abstractState.getPostCondition(source));
    }

    @Override
    public void execNode(Node node, AbstractState<PairDomain<CountDomain, BooleanOrDomain>> abstractState, InvokeCallBack<PairDomain<CountDomain, BooleanOrDomain>> invokeCallBack, IteratorContext iteratorContext) {
        PairDomain<CountDomain, BooleanOrDomain> preCondition = abstractState.getPreCondition(node);
        PairDomain<CountDomain, BooleanOrDomain> computedPost = preCondition.copyOf();
        int preCount = preCondition.first().getValue();

        switch (node) {
            case Invoke invoke -> {
                if (InvokeUtil.opensResource(invoke)) {
                    computedPost.first().increment();
                } else if (InvokeUtil.closesResource(invoke)) {
                    computedPost.first().decrement();
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
                    computedPost.second().meetWith(BooleanOrDomain.FALSE);
                } else {
                    computedPost.second().joinWith(BooleanOrDomain.TRUE);
                }
            }

            default -> {
            }
        }

        abstractState.getState(node).setPostCondition(computedPost);
    }
}
