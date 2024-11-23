package com.oracle.svm.hosted.analysis.ai.transfer;

import com.oracle.svm.hosted.analysis.ai.fixpoint.Environment;
import com.oracle.svm.hosted.analysis.ai.domain.AbstractDomain;
import jdk.graal.compiler.graph.Node;

/**
 * Interface for transfer function used in fixpoint analysis.
 */
public interface TransferFunction<
        Domain extends AbstractDomain<Domain>> {

    /**
     * This method transforms an operation from original program semantics into
     * an operation used on the abstract domain and returns the Domain
     * representing the state of the program after executing the operation.
     *
     * @param node        node to analyze
     * @param environment environment to use of a FixpointIterator
     * @return Domain element representing the result of the analysis of the give node
     */
    Domain computePostCondition(Node node, Environment<Domain> environment);

}