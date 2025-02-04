package com.oracle.svm.hosted.analysis.ai.summary;

import com.oracle.svm.hosted.analysis.ai.domain.AbstractDomain;
import com.oracle.svm.hosted.analysis.ai.fixpoint.state.AbstractStateMap;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.Invoke;

/**
 * Represents a description of behavior of a method.
 * It is used to avoid reanalyzing the method's body at every call site.
 * The main idea of the Summary is to store the pre-condition and post-condition of the method
 * as well as inputs and outputs of the method.
 *
 * @param <Domain> type of the derived {@link AbstractDomain} used in abstract interpretation
 */
public interface Summary<Domain extends AbstractDomain<Domain>> {

    /**
     * Get the pre-condition of the summary.
     * The pre-condition should be deduced from the input parameters of the method,
     * and from the abstract context at the call site.
     * It is mainly used to check, if the summary can be reused for a given call site.
     *
     * @return the pre-condition of the summary
     */
    Domain getPreCondition();

    /**
     * Get the post-condition of the summary.
     * The post-condition should be deduced from the return value of the method.
     */
    Domain getPostCondition();

    /**
     * Check if this summary covers the other summary.
     * Covering in this case means that the precondition of this summary is more general than the precondition of the other summary.
     * We will be using this method to check if we can reuse the summary from {@link SummaryCache},
     * meaning that one summary does not have a calculated post-condition yet, therefore implementations
     * should not take abstract post-conditions into account.
     * Implementations can choose to implement strict equivalence for better precision of the analysis
     * (at the cost of slower analysis, since we will be analysing methods way more frequently),
     * or for better performance, they can choose to implement relational comparison (< or >)
     *
     * @param other the other {@link Summary} to compare with
     * @return true if this summary subsumes other summary
     */
    boolean subsumes(Summary<Domain> other);

    /**
     * Convert the summary to an abstract domain {@code Domain} and apply it back to the caller state map.
     * @param invoke of the invokeNode
     * @param invokeNode the invoke node that we are applying the summary to
     * @param callerStateMap the state map of the caller
     */
    void applySummary(Invoke invoke, Node invokeNode, AbstractStateMap<Domain> callerStateMap);

    /**
     * Replace the formal parameters of the method with the actual arguments that the method was called with
     * @param calleeMap the {@link AbstractStateMap} of the callee
     */
    void replaceFormalArgsWithActual(AbstractStateMap<Domain> calleeMap);

}