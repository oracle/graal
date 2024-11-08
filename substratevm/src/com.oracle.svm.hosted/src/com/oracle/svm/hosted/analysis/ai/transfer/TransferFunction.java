package com.oracle.svm.hosted.analysis.ai.transfer;

import com.oracle.svm.hosted.analysis.ai.fixpoint.Environment;
import com.oracle.svm.hosted.analysis.ai.domain.AbstractDomain;
import jdk.graal.compiler.graph.Node;

/**
 * Interface for transfer functions used in fixpoint analysis.
 */

public interface TransferFunction<
        Domain extends AbstractDomain<Domain>> {
    /**
     * Basic types of nodes:
     * 1. Constant -> ConstantNode
     * 2. Binary arithmetic operations -> BinaryArithmeticNode
     * 3. Binary relational operations -> CompareNode
     * 4. Binary logical operations -> SLShortCircuitNode
     * 5. Unary arithmetic operations -> UnaryArithmeticNode
     * 6. Unary logical operations -> UnaryLogicalNode
     * 7. Load
     * 8. Store
     * 9. Phi -> PhiNode
     * 10. If -> IfNode
     * 11. LoopBegin -> LoopBeginNode
     * 12. LoopEnd -> LoopEndNode
     * 13. Merge -> MergeNode
     * 14. Call -> CallNode
     *
     * @param node        node to analyze
     * @param environment environment to use of a FixpointIterator
     * @return Domain element representing the result of the analysis of the give node
     */
    Domain analyzeNode(Node node, Environment<Domain> environment);
}