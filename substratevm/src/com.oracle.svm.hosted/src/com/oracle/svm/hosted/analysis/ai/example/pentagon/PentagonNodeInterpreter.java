package com.oracle.svm.hosted.analysis.ai.example.pentagon;

import com.oracle.svm.hosted.analysis.ai.analyzer.AnalysisOutcome;
import com.oracle.svm.hosted.analysis.ai.analyzer.call.InvokeCallBack;
import com.oracle.svm.hosted.analysis.ai.domain.access.AccessPath;
import com.oracle.svm.hosted.analysis.ai.domain.numerical.IntInterval;
import com.oracle.svm.hosted.analysis.ai.domain.numerical.PentagonDomain;
import com.oracle.svm.hosted.analysis.ai.fixpoint.state.AbstractStateMap;
import com.oracle.svm.hosted.analysis.ai.interpreter.NodeInterpreter;
import com.oracle.svm.hosted.analysis.ai.log.AbstractInterpretationLogger;
import com.oracle.svm.hosted.analysis.ai.log.LoggerVerbosity;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.IfNode;
import jdk.graal.compiler.nodes.Invoke;
import jdk.graal.compiler.nodes.PhiNode;
import jdk.graal.compiler.nodes.ReturnNode;
import jdk.graal.compiler.nodes.calc.AddNode;
import jdk.graal.compiler.nodes.calc.BinaryArithmeticNode;
import jdk.graal.compiler.nodes.calc.IntegerLessThanNode;
import jdk.graal.compiler.nodes.calc.MulNode;
import jdk.graal.compiler.nodes.calc.SubNode;
import jdk.graal.compiler.nodes.java.LoadFieldNode;
import jdk.graal.compiler.nodes.java.StoreFieldNode;

/**
 * Node interpreter for Pentagon domain analysis.
 * Tracks both interval bounds and variable relationships.
 * Variables will be represented by the {@link AccessPath} objects.
 * <p>
 * NOTE: This interpreter does not handle inter-procedural relationships,
 * it handles only intra-procedural methods.
 */
public class PentagonNodeInterpreter implements NodeInterpreter<PentagonDomain<AccessPath>> {

    private final AbstractInterpretationLogger logger = AbstractInterpretationLogger.getInstance();

    @Override
    public PentagonDomain<AccessPath> execEdge(Node source,
                                               Node target,
                                               AbstractStateMap<PentagonDomain<AccessPath>> abstractStateMap) {

        AbstractInterpretationLogger logger = AbstractInterpretationLogger.getInstance();
        logger.log("Analyzing edge: " + source + " -> " + target, LoggerVerbosity.DEBUG);

        // If not an if-node, simply propagate post-condition to target's pre-condition
        if (!(source instanceof IfNode ifNode)) {
            abstractStateMap.getPreCondition(target).joinWith(abstractStateMap.getPostCondition(source));
            return abstractStateMap.getPreCondition(target);
        }

        PentagonDomain<AccessPath> ifPost = abstractStateMap.getPostCondition(ifNode);

        // Check for unreachable branches
        if (ifPost.isBot() && target.equals(ifNode.trueSuccessor())) {
            abstractStateMap.getState(target).markRestrictedFromExecution();
            return abstractStateMap.getPreCondition(target);
        }

        if (!ifPost.isBot() && target.equals(ifNode.falseSuccessor())) {
            abstractStateMap.getState(target).markRestrictedFromExecution();
            return abstractStateMap.getPreCondition(target);
        }

        // Only handle integer less than conditions for now
        if (!(ifNode.condition() instanceof IntegerLessThanNode lessThanNode)) {
            return abstractStateMap.getPreCondition(target);
        }

        // Get the variables involved in the comparison
        AccessPath leftVar = new AccessPath(lessThanNode.getX());
        AccessPath rightVar = new AccessPath(lessThanNode.getY());

        // Get the current intervals
        PentagonDomain<AccessPath> targetPre = abstractStateMap.getPreCondition(target);
        targetPre.joinWith(abstractStateMap.getPostCondition(source));

        // Refine based on which branch we're taking
        if (target.equals(ifNode.trueSuccessor())) {
            // On true branch, we know that leftVar < rightVar
            // Add the less-than relationship to the pentagon domain
            targetPre.addLessThanRelation(leftVar, rightVar);

            // Also refine the intervals
            IntInterval leftInterval = targetPre.getInterval(leftVar);
            IntInterval rightInterval = targetPre.getInterval(rightVar);

            // Refine right interval to be > left's minimum
            if (rightInterval.getLowerBound() <= leftInterval.getUpperBound()) {
                IntInterval refinedRight = new IntInterval(
                        Math.max(rightInterval.getLowerBound(), leftInterval.getLowerBound() + 1),
                        rightInterval.getUpperBound());
                targetPre.setInterval(rightVar, refinedRight);
            }

            // Refine left interval to be < right's maximum
            if (leftInterval.getUpperBound() >= rightInterval.getLowerBound()) {
                IntInterval refinedLeft = new IntInterval(
                        leftInterval.getLowerBound(),
                        Math.min(leftInterval.getUpperBound(), rightInterval.getUpperBound() - 1));
                targetPre.setInterval(leftVar, refinedLeft);
            }
        } else {
            // On false branch, we know that leftVar >= rightVar
            // Update intervals accordingly
            IntInterval leftInterval = targetPre.getInterval(leftVar);
            IntInterval rightInterval = targetPre.getInterval(rightVar);

            // Refine left interval to be >= right's minimum
            if (leftInterval.getLowerBound() < rightInterval.getLowerBound()) {
                IntInterval refinedLeft = new IntInterval(
                        rightInterval.getLowerBound(),
                        leftInterval.getUpperBound());
                targetPre.setInterval(leftVar, refinedLeft);
            }

            // Refine right interval to be <= left's maximum
            if (rightInterval.getUpperBound() > leftInterval.getUpperBound()) {
                IntInterval refinedRight = new IntInterval(
                        rightInterval.getLowerBound(),
                        leftInterval.getUpperBound());
                targetPre.setInterval(rightVar, refinedRight);
            }
        }

        return targetPre;
    }

    @Override
    public PentagonDomain<AccessPath> execNode(Node node,
                                               AbstractStateMap<PentagonDomain<AccessPath>> abstractStateMap,
                                               InvokeCallBack<PentagonDomain<AccessPath>> invokeCallBack) {

        PentagonDomain<AccessPath> preCondition = abstractStateMap.getPreCondition(node);
        PentagonDomain<AccessPath> computedPost = preCondition.copyOf();

        switch (node) {
            case ConstantNode constantNode -> {
                if (constantNode.asJavaConstant() != null &&
                        constantNode.asJavaConstant().getJavaKind().isNumericInteger()) {
                    // Constant integers get precise intervals
                    int value = constantNode.asJavaConstant().asInt();
                    computedPost.setInterval(new AccessPath(node), new IntInterval(value, value));
                }
            }

            case BinaryArithmeticNode<?> binaryNode -> {
                AccessPath resultVar = new AccessPath(binaryNode);
                AccessPath leftVar = new AccessPath(binaryNode.getX());
                AccessPath rightVar = new AccessPath(binaryNode.getY());
                PentagonDomain<AccessPath> xPentagon = execNode(binaryNode.getX(), abstractStateMap, invokeCallBack);
                PentagonDomain<AccessPath> yPentagon = execNode(binaryNode.getY(), abstractStateMap, invokeCallBack);
                IntInterval leftInterval = xPentagon.getInterval(leftVar);
                IntInterval rightInterval = yPentagon.getInterval(rightVar);
                IntInterval result;

                switch (binaryNode) {
                    case AddNode ignored -> result = leftInterval.add(rightInterval);
                    case SubNode ignored -> result = leftInterval.sub(rightInterval);
                    case MulNode ignored -> result = leftInterval.mul(rightInterval);
                    default -> result = new IntInterval();
                }
                computedPost.setInterval(resultVar, result);

                // Maintain relationships for monotonic operations
                if (binaryNode instanceof AddNode) {
                    // For add operations with constants, we can maintain inequalities
                    if (binaryNode.getY() instanceof ConstantNode constNode) {
                        assert constNode.asJavaConstant() != null;
                        if (constNode.asJavaConstant().asInt() > 0) {
                            // If x < y, then x + c < y + c for positive c
                            transferLessThanRelations(leftVar, resultVar, computedPost);
                        }
                    }
                }
            }

            case LoadFieldNode loadFieldNode -> {
                /* We suppose that all positions of fields are known in the method -> This wouldn't work with inter-procedural analyses */
                AccessPath fieldVar = AccessPath.getAccessPathFromAccessFieldNode(loadFieldNode);
                IntInterval keyInterval = preCondition.getInterval(fieldVar);
                computedPost.setInterval(new AccessPath(loadFieldNode), keyInterval);
            }

            case StoreFieldNode storeFieldNode -> {
                /* Same potential problem as in LoadFieldNode case */
                AccessPath fieldVar = AccessPath.getAccessPathFromAccessFieldNode(storeFieldNode);
                PentagonDomain<AccessPath> storeFieldEnv = execNode(storeFieldNode.value(), abstractStateMap, invokeCallBack);
                computedPost.setInterval(fieldVar, storeFieldEnv.getInterval(new AccessPath(storeFieldNode.value())));

                // Transfer inequality relations to the field
                for (AccessPath otherVar : computedPost.getVariableNames()) {
                    if (computedPost.lessThan(fieldVar, otherVar)) {
                        computedPost.addLessThanRelation(fieldVar, otherVar);
                    }
                    if (computedPost.lessThan(otherVar, fieldVar)) {
                        computedPost.addLessThanRelation(otherVar, fieldVar);
                    }
                }
            }

            case PhiNode phiNode -> {
                IntInterval phiResult = new IntInterval();

                for (Node input : phiNode.inputs()) {
                    AccessPath variableName = new AccessPath(input);
                    boolean isCyclicEdge = false;
                    for (Node inputOfInput : input.inputs()) {
                        if (inputOfInput.equals(phiNode)) {
                            isCyclicEdge = true;
                            break;
                        }
                    }

                    if (isCyclicEdge) {
                        phiResult.joinWith(abstractStateMap.getPostCondition(input).getInterval(variableName));
                    } else {
                        PentagonDomain<AccessPath> inputEnv = execNode(input, abstractStateMap, invokeCallBack);
                        phiResult.joinWith(inputEnv.getInterval(variableName));
                    }

                    computedPost.setInterval(new AccessPath(node), phiResult);
                }
            }

            case IntegerLessThanNode lessThanNode -> {

                AccessPath nodeVar = new AccessPath(node);
                PentagonDomain<AccessPath> xPentagon = execNode(lessThanNode.getX(), abstractStateMap, invokeCallBack);
                PentagonDomain<AccessPath> yPentagon = execNode(lessThanNode.getY(), abstractStateMap, invokeCallBack);
                AccessPath leftVar = new AccessPath(lessThanNode.getX());
                AccessPath rightVar = new AccessPath(lessThanNode.getY());
                IntInterval leftInterval = xPentagon.getInterval(leftVar);
                IntInterval rightInterval = yPentagon.getInterval(rightVar);

                // Check if the comparison is always true/false
                if (leftInterval.getUpperBound() < rightInterval.getLowerBound()) {
                    computedPost.setInterval(nodeVar, new IntInterval(1, 1));
                } else if (leftInterval.getLowerBound() >= rightInterval.getUpperBound()) {
                    computedPost.setInterval(nodeVar, new IntInterval(0, 0));
                } else {
                    computedPost.setInterval(nodeVar, new IntInterval(0, 1));
                }

            }

            case IfNode ifNode -> {
                if (!(ifNode.condition() instanceof IntegerLessThanNode)) {
                    return computedPost;
                }

                PentagonDomain<AccessPath> condition = execNode(ifNode.condition(), abstractStateMap, invokeCallBack);
                if (condition.isBot()) {
                    abstractStateMap.getPostCondition(ifNode).setToBot();
                    return abstractStateMap.getPostCondition(ifNode);
                }

                IntInterval condInterval = condition.getInterval(new AccessPath(ifNode.condition()));
                computedPost.setInterval(new AccessPath(ifNode), condInterval);
            }

            case ReturnNode returnNode -> {
                if (returnNode.result() != null &&
                        returnNode.result().getStackKind().isNumericInteger()) {
                    AccessPath returnVar = new AccessPath(returnNode);
                    AccessPath resultVar = new AccessPath(returnNode.result());
                    computedPost.setInterval(returnVar, computedPost.getInterval(resultVar));

                    // Transfer relationships to return value
                    transferLessThanRelations(resultVar, returnVar, computedPost);
                }
            }

            /* Vey simplified for our purposes, we deal with intra-procedural analyses of pentagon domains, so invokes
               aren't our concern.
             */
            case Invoke invoke -> {
                AnalysisOutcome<PentagonDomain<AccessPath>> outcome = invokeCallBack.handleCall(invoke, node, abstractStateMap);
                if (outcome.isError()) {
                    logger.log("Error in invoke: " + outcome.result(), LoggerVerbosity.INFO);
                    computedPost.setToTop();
                }
            }

            default -> {
                // Default case - keep existing domain
            }
        }

        abstractStateMap.setPostCondition(node, computedPost);
        logger.log("Completed node: " + node + " -> " + computedPost, LoggerVerbosity.DEBUG);
        return computedPost;
    }

    private void transferLessThanRelations(AccessPath sourceVar, AccessPath targetVar, PentagonDomain<AccessPath> domain) {
        // Transfer all less-than relations from source to target
        for (AccessPath otherVar : domain.getVariableNames()) {
            if (domain.lessThan(sourceVar, otherVar)) {
                domain.addLessThanRelation(targetVar, otherVar);
            }
            if (domain.lessThan(otherVar, sourceVar)) {
                domain.addLessThanRelation(otherVar, targetVar);
            }
        }
    }
}
