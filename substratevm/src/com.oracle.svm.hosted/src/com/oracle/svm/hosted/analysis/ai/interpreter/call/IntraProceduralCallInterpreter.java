package com.oracle.svm.hosted.analysis.ai.interpreter.call;

import com.oracle.svm.hosted.analysis.ai.analyzer.context.IntraProceduralAnalysisContext;
import com.oracle.svm.hosted.analysis.ai.domain.AbstractDomain;
import com.oracle.svm.hosted.analysis.ai.fixpoint.state.AbstractStateMap;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.Invoke;
import jdk.graal.compiler.nodes.InvokeNode;

/**
 * Represents a context insensitive call interpreter.
 * It ignores methods in the sense that every method is a black box,
 * and therefore produces the `top` value of the used abstract domain.
 *
 * @param <Domain> type of the derived {@link AbstractDomain}
 */
public final class IntraProceduralCallInterpreter<
        Domain extends AbstractDomain<Domain>>
        implements CallInterpreter<Domain> {

    private final IntraProceduralAnalysisContext<Domain> analysisContext;

    public IntraProceduralCallInterpreter(IntraProceduralAnalysisContext<Domain> analysisContext) {
        this.analysisContext = analysisContext;
    }

    @Override
    public void execInvoke(Invoke invoke, Node invokeNode, AbstractStateMap<Domain> abstractStateMap) {
        analysisContext.getLogger().logToFile("IntraProceduralCallInterpreter::execInvoke invoke: " + invoke);
        analysisContext.getLogger().logToFile("IntraProceduralCallInterpreter::execInvoke targetMethod: " + invoke.getTargetMethod());
        /* We set the post condition to top since we don't know the side effect of this call  */
        abstractStateMap.setPostCondition(invokeNode, AbstractDomain.createTop(analysisContext.getInitialDomain()));
    }
}