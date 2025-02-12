package com.oracle.svm.hosted.analysis.ai.interpreter.call;

import com.oracle.svm.hosted.analysis.ai.analyzer.payload.AnalysisPayload;
import com.oracle.svm.hosted.analysis.ai.domain.AbstractDomain;
import com.oracle.svm.hosted.analysis.ai.fixpoint.state.AbstractStateMap;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.Invoke;

/**
 * Represents a context insensitive call interpreter.
 * It ignores methods in the sense that every method is a black box,
 * and therefore produces the `top` value of the used abstract domain.
 *
 * @param <Domain> type of the derived {@link AbstractDomain}
 */
public final class IntraProceduralCallInterpreter<Domain extends AbstractDomain<Domain>>
        implements CallInterpreter<Domain> {

    @Override
    public void execInvoke(Invoke invoke, Node invokeNode, AbstractStateMap<Domain> abstractStateMap, AnalysisPayload<Domain> payload) {
        payload.getLogger().logToFile("IntraProceduralCallInterpreter::execInvoke invoke: " + invoke);

        /* We set the post condition to top since we don't know the side effect of this call  */
        abstractStateMap.setPostCondition(invokeNode, AbstractDomain.createTop(payload.getInitialDomain()));
    }
}