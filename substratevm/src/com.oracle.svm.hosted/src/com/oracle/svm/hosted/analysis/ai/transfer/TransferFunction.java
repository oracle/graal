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
     * 7. Load -> LoadFieldNode
     * 8. Store -> StoreFieldNode
     * 9. If -> IfNode
     * 10. LoopBegin -> LoopBeginNode
     * 11. LoopEnd -> LoopEndNode
     * 12. Merge -> MergeNode
     * 13. Call -> CallNode
     * 14. Invoke -> InvokeNode
     *
     * @param node        node to analyze
     * @param environment environment to use of a FixpointIterator
     * @return Domain element representing the result of the analysis of the give node
     */
    Domain analyzeNode(Node node, Environment<Domain> environment);
}