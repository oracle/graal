package com.oracle.svm.hosted.analysis.ai.interpreter;

import com.oracle.svm.hosted.analysis.ai.analyzer.call.InvokeCallBack;
import com.oracle.svm.hosted.analysis.ai.domain.AbstractDomain;
import com.oracle.svm.hosted.analysis.ai.fixpoint.context.IteratorContext;
import com.oracle.svm.hosted.analysis.ai.fixpoint.state.AbstractState;
import jdk.graal.compiler.graph.Node;

/**
 * This interface provides an API for interpreting semantical operations
 * on nodes and edges within the GraalIR.
 *
 * @param <Domain> type of the derived {@link AbstractDomain} used in the analysis
 */
public interface AbstractInterpreter<Domain extends AbstractDomain<Domain>> {

    /**
     * Interpret the effect of executing an edge between two nodes {@link AbstractState}.
     * For efficiency, this method should modify the pre-condition of {@param target} directly.
     *
     * @param source          the node from which the edge originates
     * @param target          the node to which the edge goes
     * @param abstractState   of the analyzed method
     * @param iteratorContext optional context information from the fixpoint iterator (can be null)
     */
    void execEdge(Node source, Node target, AbstractState<Domain> abstractState, IteratorContext iteratorContext);

    /**
     * Interpret the effect of executing a Graal IR node within given {@link AbstractState}.
     * For efficiency, this method should modify the post-condition of {@param target} directly.
     *
     * @param node            to interpret
     * @param abstractState   of the analyzed method
     * @param invokeCallBack  callback that can be used to analyze the summary of invokes
     * @param iteratorContext optional context information from the fixpoint iterator (can be null)
     */
    void execNode(Node node, AbstractState<Domain> abstractState, InvokeCallBack<Domain> invokeCallBack, IteratorContext iteratorContext);

    // TODO: remove this once we have everything ready for iteratorContext

    /**
     * Interpret the effect of executing an edge between two nodes {@link AbstractState}.
     * Default implementation that calls the context-aware version with null context.
     * For backward compatibility with implementations that don't need context.
     *
     * @param source        the node from which the edge originates
     * @param target        the node to which the edge goes
     * @param abstractState of the analyzed method
     */
    default void execEdge(Node source, Node target, AbstractState<Domain> abstractState) {
        execEdge(source, target, abstractState, null);
    }

    /**
     * Interpret the effect of executing a Graal IR node within given {@link AbstractState}.
     * Default implementation that calls the context-aware version with null context.
     * For backward compatibility with implementations that don't need context.
     *
     * @param node           to interpret
     * @param abstractState  of the analyzed method
     * @param invokeCallBack callback that can be used to analyze the summary of invokes
     */
    default void execNode(Node node, AbstractState<Domain> abstractState, InvokeCallBack<Domain> invokeCallBack) {
        execNode(node, abstractState, invokeCallBack, null);
    }
}
