package com.oracle.svm.hosted.analysis.ai.analysis.invokehandle;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.svm.hosted.analysis.ai.domain.AbstractDomain;
import com.oracle.svm.hosted.analysis.ai.fixpoint.state.AbstractState;
import com.oracle.svm.hosted.analysis.ai.summary.ContextKey;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.Invoke;

import java.util.List;
import java.util.Optional;

/**
 * Immutable input bundle provided to an interprocedural invoke handler.
 * Carries the caller abstract state at the call site, the invoke node, actual
 * argument nodes, and the current context key/signature if available.
 */
public record InvokeInput<Domain extends AbstractDomain<Domain>>(
        AnalysisMethod callerMethod,
        AbstractState<Domain> callerState,
        Invoke invoke,
        List<Node> actualArgsNodes,
        List<Domain> actualArgDomains,
        Optional<ContextKey> contextKey,
        Optional<String> contextSignature) {

    /** This method should be used if interpreters want to create their own custom signatures of methods **/
    public static <D extends AbstractDomain<D>> InvokeInput<D> of(
            AnalysisMethod callerMethod,
            AbstractState<D> callerState,
            Invoke invoke,
            List<Node> actualArgsNodes,
            List<D> actualArgDomains,
            ContextKey contextKey,
            String contextSignature) {
        return new InvokeInput<>(callerMethod, callerState, invoke, actualArgsNodes, actualArgDomains,
                Optional.ofNullable(contextKey), Optional.ofNullable(contextSignature));
    }

    /** In this case, if the contextKey is empty, the framework will use the default context signature builder **/
    public static <D extends AbstractDomain<D>> InvokeInput<D> of(
            AnalysisMethod callerMethod,
            AbstractState<D> callerState,
            Invoke invoke,
            List<Node> actualArgsNodes,
            List<D> actualArgDomains) {
        return new InvokeInput<>(callerMethod, callerState, invoke, actualArgsNodes, actualArgDomains, Optional.empty(), Optional.empty());
    }
}
