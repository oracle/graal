package com.oracle.svm.hosted.analysis.ai.fixpoint.iterator;

import com.oracle.svm.hosted.analysis.ai.checker.Checker;
import com.oracle.svm.hosted.analysis.ai.checker.CheckerSummary;
import com.oracle.svm.hosted.analysis.ai.domain.AbstractDomain;
import com.oracle.svm.hosted.analysis.ai.fixpoint.AbstractState;
import com.oracle.svm.hosted.analysis.ai.fixpoint.AbstractStateMap;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.StructuredGraph;

import java.util.List;

/**
 * Represents an API for all fixpoint iterators.
 *
 * @param <Domain> the type of the abstract domain
 */
public interface FixpointIterator<Domain extends AbstractDomain<Domain>> {

    /**
     * Iterates until a fixpoint is reached.
     * This method performs a fixpoint iteration algorithm and returns the
     * resulting {@link AbstractStateMap} (invariants of the iterated nodes in the {@link StructuredGraph})
     * after the iteration is completed.
     *
     * @param initialDomain the initial domain
     * @return the environment after the fixpoint is reached
     */
    AbstractStateMap<Domain> iterateUntilFixpoint(Domain initialDomain);

    /**
     * Returns the pre-condition of the given node.
     *
     * @param node we want to get the pre-condition of
     * @return the pre-condition of the node
     */
    Domain getPreCondition(Node node);

    /**
     * Returns the post-condition of the given node.
     *
     * @param node we want to get the post-condition of
     * @return the post-condition of the node
     */
    Domain getPostCondition(Node node);

    /**
     * Clears the internal state of the iterator,
     * so that the {@link FixpointIterator} can be reused.
     */
    void clear();

    /**
     * Returns the Structured graph of the iterator
     */
    StructuredGraph getGraph();

    /**
     * Returns the abstract state of the given node
     *
     * @param node we want to get the {@link AbstractState} of
     * @return the abstract state of the node
     */
    AbstractState<Domain> getAbstractState(Node node);

    /**
     * Runs the checks of the given checkers on the current state of the analysis.
     *
     * @param checkers the checkers to run
     * @return the summary of the checks
     */
    List<CheckerSummary> runCheckers(List<Checker> checkers);
}