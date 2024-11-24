package com.oracle.svm.hosted.analysis.ai.fixpoint;

import com.oracle.svm.hosted.analysis.ai.domain.AbstractDomain;

/**
 * Represents a fixpoint iterator that can be used to perform a fixpoint iteration
 *
 * @param <Domain> the type of the abstract domain
 */
public interface FixpointIterator<Domain extends AbstractDomain<Domain>> {

    /**
     * Iterates until a fixpoint is reached.
     * This method performs a fixpoint iteration algorithm and returns the
     * resulting environment (mapping of StructuredGraph Nodes to a common AbstractDomain)
     * after the analysis is complete.
     *
     * @return the environment after the fixpoint iteration
     */
    Environment<Domain> iterateUntilFixpoint();
}