package com.oracle.svm.hosted.analysis.ai.example.pentagon;

import com.oracle.svm.hosted.analysis.ai.analyzer.AnalysisOutcome;
import com.oracle.svm.hosted.analysis.ai.analyzer.call.InvokeCallBack;
import com.oracle.svm.hosted.analysis.ai.domain.access.AccessPath;
import com.oracle.svm.hosted.analysis.ai.domain.numerical.IntInterval;
import com.oracle.svm.hosted.analysis.ai.domain.numerical.PentagonDomain;
import com.oracle.svm.hosted.analysis.ai.fixpoint.state.AbstractState;
import com.oracle.svm.hosted.analysis.ai.interpreter.AbstractInterpreter;
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
public class PentagonAbstractInterpreter implements AbstractInterpreter<PentagonDomain<AccessPath>> {

    private final AbstractInterpretationLogger logger = AbstractInterpretationLogger.getInstance();

    @Override
    public void execEdge(Node source,
                         Node target,
                         AbstractState<PentagonDomain<AccessPath>> abstractState) {

        AbstractInterpretationLogger logger = AbstractInterpretationLogger.getInstance();
        logger.log("Analyzing edge: " + source + " -> " + target, LoggerVerbosity.DEBUG);

        /* If not an if-node, simply propagate post-condition to target's pre-condition */
        if (!(source instanceof IfNode ifNode)) {
            abstractState.getPreCondition(target).joinWith(abstractState.getPostCondition(source));
            return;
        }

        PentagonDomain<AccessPath> ifPost = abstractState.getPostCondition(ifNode);

        if (ifPost.isBot() && target.equals(ifNode.trueSuccessor())) {
            abstractState.getState(target).markRestrictedFromExecution();
            return;
        }

        if (!ifPost.isBot() && target.equals(ifNode.falseSuccessor())) {
            abstractState.getState(target).markRestrictedFromExecution();
            return;
        }

        /* Only handle integer less than conditions for now */
        if (!(ifNode.condition() instanceof IntegerLessThanNode lessThanNode)) {
            return;
        }

        /* Get the variables involved in the comparison */
        AccessPath leftVar = new AccessPath(lessThanNode.getX());
        AccessPath rightVar = new AccessPath(lessThanNode.getY());

        PentagonDomain<AccessPath> targetPre = abstractState.getPreCondition(target);
        targetPre.joinWith(abstractState.getPostCondition(source));

        if (target.equals(ifNode.trueSuccessor())) {
            /* On true branch, we know that leftVar < rightVar
               Add the less-than relationship to the pentagon domain */
            targetPre.addLessThanRelation(leftVar, rightVar);

            IntInterval leftInterval = targetPre.getInterval(leftVar);
            IntInterval rightInterval = targetPre.getInterval(rightVar);

            /* Refine right interval to be > left's minimum */
            if (rightInterval.getLowerBound() <= leftInterval.getUpperBound()) {
                IntInterval refinedRight = new IntInterval(
                        Math.max(rightInterval.getLowerBound(), leftInterval.getLowerBound() + 1),
                        rightInterval.getUpperBound());
                targetPre.setInterval(rightVar, refinedRight);
            }

            /* Refine left interval to be < right's maximum */
            if (leftInterval.getUpperBound() >= rightInterval.getLowerBound()) {
                IntInterval refinedLeft = new IntInterval(
                        leftInterval.getLowerBound(),
                        Math.min(leftInterval.getUpperBound(), rightInterval.getUpperBound() - 1));
                targetPre.setInterval(leftVar, refinedLeft);
            }
        } else {
            /* Inside false branch, we know that leftVar >= rightVar */
            IntInterval leftInterval = targetPre.getInterval(leftVar);
            IntInterval rightInterval = targetPre.getInterval(rightVar);

            /* Refine left interval to be >= right's minimum */
            if (leftInterval.getLowerBound() < rightInterval.getLowerBound()) {
                IntInterval refinedLeft = new IntInterval(
                        rightInterval.getLowerBound(),
                        leftInterval.getUpperBound());
                targetPre.setInterval(leftVar, refinedLeft);
            }

            /*  Refine right interval to be <= left's maximum */
            if (rightInterval.getUpperBound() > leftInterval.getUpperBound()) {
                IntInterval refinedRight = new IntInterval(
                        rightInterval.getLowerBound(),
                        leftInterval.getUpperBound());
                targetPre.setInterval(rightVar, refinedRight);
            }
        }
    }

    @Override
    public void execNode(Node node,
                         AbstractState<PentagonDomain<AccessPath>> abstractState,
                         InvokeCallBack<PentagonDomain<AccessPath>> invokeCallBack) {

        PentagonDomain<AccessPath> preCondition = abstractState.getPreCondition(node);
        PentagonDomain<AccessPath> computedPost = preCondition.copyOf();

        switch (node) {
            case ConstantNode constantNode -> {
                if (constantNode.asJavaConstant() != null &&
                        constantNode.asJavaConstant().getJavaKind().isNumericInteger()) {
                    int value = constantNode.asJavaConstant().asInt();
                    computedPost.setInterval(new AccessPath(node), new IntInterval(value, value));
                }
            }

            case BinaryArithmeticNode<?> binaryNode -> {
                AccessPath resultVar = new AccessPath(binaryNode);
                AccessPath leftVar = new AccessPath(binaryNode.getX());
                AccessPath rightVar = new AccessPath(binaryNode.getY());
                var xPentagon = execAndGet(binaryNode.getX(), abstractState, invokeCallBack);
                var yPentagon = execAndGet(binaryNode.getY(), abstractState, invokeCallBack);
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
                /* Maintain relationships for monotonic operations */
                if (binaryNode instanceof AddNode) {
                    if (binaryNode.getY() instanceof ConstantNode constNode) {
                        assert constNode.asJavaConstant() != null;
                        if (constNode.asJavaConstant().asInt() > 0) {
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
                var storeFieldEnv = execAndGet(storeFieldNode.value(), abstractState, invokeCallBack);
                computedPost.setInterval(fieldVar, storeFieldEnv.getInterval(new AccessPath(storeFieldNode.value())));

                /* Transfer inequality relations to the field */
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
                        phiResult.joinWith(abstractState.getPostCondition(input).getInterval(variableName));
                    } else {
                        var inputEnv = execAndGet(input, abstractState, invokeCallBack);
                        phiResult.joinWith(inputEnv.getInterval(variableName));
                    }

                    computedPost.setInterval(new AccessPath(node), phiResult);
                }
            }

            case IntegerLessThanNode lessThanNode -> {

                AccessPath nodeVar = new AccessPath(node);
                var xPentagon = execAndGet(lessThanNode.getX(), abstractState, invokeCallBack);
                var yPentagon = execAndGet(lessThanNode.getY(), abstractState, invokeCallBack);
                AccessPath leftVar = new AccessPath(lessThanNode.getX());
                AccessPath rightVar = new AccessPath(lessThanNode.getY());
                IntInterval leftInterval = xPentagon.getInterval(leftVar);
                IntInterval rightInterval = yPentagon.getInterval(rightVar);

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
                    return;
                }

                var condition = execAndGet(ifNode.condition(), abstractState, invokeCallBack);
                if (condition.isBot()) {
                    abstractState.getPostCondition(ifNode).setToBot();
                    return;
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
                AnalysisOutcome<PentagonDomain<AccessPath>> outcome = invokeCallBack.handleInvoke(invoke, node, abstractState);
                if (outcome.isError()) {
                    logger.log("Error in invoke: " + outcome.result(), LoggerVerbosity.INFO);
                    computedPost.setToTop();
                }
            }

            default -> {
                // Default case - keep existing domain
            }
        }

        abstractState.setPostCondition(node, computedPost);
        logger.log("Completed node: " + node + " -> " + computedPost, LoggerVerbosity.DEBUG);
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

    private PentagonDomain<AccessPath> execAndGet(Node node,
                                                  AbstractState<PentagonDomain<AccessPath>> abstractState,
                                                  InvokeCallBack<PentagonDomain<AccessPath>> invokeCallBack) {
        execNode(node, abstractState, invokeCallBack);
        return abstractState.getPostCondition(node);
    }
}
