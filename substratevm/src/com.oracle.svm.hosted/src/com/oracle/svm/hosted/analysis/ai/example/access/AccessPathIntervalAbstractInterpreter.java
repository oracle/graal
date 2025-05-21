package com.oracle.svm.hosted.analysis.ai.example.access;

import com.oracle.graal.pointsto.util.AnalysisError;
import com.oracle.svm.hosted.analysis.ai.analyzer.AnalysisOutcome;
import com.oracle.svm.hosted.analysis.ai.analyzer.call.InvokeCallBack;
import com.oracle.svm.hosted.analysis.ai.domain.access.AccessPath;
import com.oracle.svm.hosted.analysis.ai.domain.access.AccessPathBase;
import com.oracle.svm.hosted.analysis.ai.domain.access.AccessPathConstants;
import com.oracle.svm.hosted.analysis.ai.domain.access.AccessPathMap;
import com.oracle.svm.hosted.analysis.ai.domain.access.PlaceHolderAccessPathBase;
import com.oracle.svm.hosted.analysis.ai.domain.numerical.IntInterval;
import com.oracle.svm.hosted.analysis.ai.fixpoint.state.AbstractState;
import com.oracle.svm.hosted.analysis.ai.interpreter.AbstractInterpreter;
import com.oracle.svm.hosted.analysis.ai.log.AbstractInterpretationLogger;
import com.oracle.svm.hosted.analysis.ai.log.LoggerVerbosity;
import com.oracle.svm.hosted.analysis.ai.summary.Summary;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.IfNode;
import jdk.graal.compiler.nodes.Invoke;
import jdk.graal.compiler.nodes.ParameterNode;
import jdk.graal.compiler.nodes.PhiNode;
import jdk.graal.compiler.nodes.PiNode;
import jdk.graal.compiler.nodes.ReturnNode;
import jdk.graal.compiler.nodes.calc.AddNode;
import jdk.graal.compiler.nodes.calc.BinaryArithmeticNode;
import jdk.graal.compiler.nodes.calc.FloatDivNode;
import jdk.graal.compiler.nodes.calc.IntegerLessThanNode;
import jdk.graal.compiler.nodes.calc.MulNode;
import jdk.graal.compiler.nodes.calc.SubNode;
import jdk.graal.compiler.nodes.java.AccessFieldNode;
import jdk.graal.compiler.nodes.java.LoadFieldNode;
import jdk.graal.compiler.nodes.java.StoreFieldNode;
import jdk.graal.compiler.nodes.virtual.AllocatedObjectNode;
import jdk.vm.ci.meta.ResolvedJavaField;

import java.util.List;

public class AccessPathIntervalAbstractInterpreter implements AbstractInterpreter<AccessPathMap<IntInterval>> {

    @Override
    public void execEdge(Node source,
                         Node target,
                         AbstractState<AccessPathMap<IntInterval>> abstractState) {

        AbstractInterpretationLogger logger = AbstractInterpretationLogger.getInstance();
        logger.log("Analyzing edge: " + source + " -> " + target, LoggerVerbosity.DEBUG);

        if (!(source instanceof IfNode ifNode)) {
            abstractState.getPreCondition(target).joinWith(abstractState.getPostCondition(source));
            return;
        }

        AccessPathMap<IntInterval> ifPost = abstractState.getPostCondition(ifNode);
        /* Following a branch that won't get taken in real execution, because the condition is false and we are the true successor */
        if (ifPost.isBot() && target.equals(ifNode.trueSuccessor())) {
            abstractState.getState(target).markRestrictedFromExecution();
            return;
        }

        /* Vice versa, just that we are the false successor and the condition is true */
        if (!ifPost.isBot() && target.equals(ifNode.falseSuccessor())) {
            abstractState.getState(target).markRestrictedFromExecution();
            return;
        }

        /* In the future support other relational conditions, for now this gets the job done */
        if (!(ifNode.condition() instanceof IntegerLessThanNode integerLessThanNode)) {
            return;
        }

        /*
          We are restricting ourselves to conditions in the form ( field/param/some_identifier_path < value ),
           so we need to get the post-condition value of the field and join it with the post-condition value of the
           condition. This is done to get the value of the field in the pre-condition of the target node.
         */
        IntInterval condInterval = ifPost.get(new AccessPath(ifNode));
        AccessPath relevantFieldPath = abstractState.getPostCondition(integerLessThanNode.getX()).getOnlyAccessPath();
        IntInterval relevantFieldInterval = abstractState.getPostCondition(integerLessThanNode.getX()).get(relevantFieldPath);

        /* Join with the post-condition value of the access path */
        AccessPathMap<IntInterval> tmpMap = new AccessPathMap<>(new IntInterval());
        tmpMap.put(relevantFieldPath, relevantFieldInterval);
        abstractState.getPreCondition(target).joinWith(tmpMap);

        /* Then use interval intersection to refine result */
        if (target.equals(ifNode.trueSuccessor())) {
            abstractState.getPreCondition(target).get(relevantFieldPath).meetWith(condInterval);
        } else {
            condInterval = IntInterval.getHigherInterval(condInterval);
            abstractState.getPreCondition(target).get(relevantFieldPath).meetWith(condInterval);
        }

    }

    @Override
    public void execNode(Node node,
                         AbstractState<AccessPathMap<IntInterval>> abstractState,
                         InvokeCallBack<AccessPathMap<IntInterval>> invokeCallBack) {

        AbstractInterpretationLogger logger = AbstractInterpretationLogger.getInstance();
        AccessPathMap<IntInterval> preCondition = abstractState.getPreCondition(node);
        logger.log("Analyzing node: " + node, LoggerVerbosity.DEBUG);
        AccessPathMap<IntInterval> computedPostCondition = preCondition.copyOf();

        switch (node) {

            case ConstantNode constantNode -> {
                AccessPath pathNode = new AccessPath(node);
                if (constantNode.asJavaConstant() == null || !constantNode.asJavaConstant().getJavaKind().isNumericInteger()) {
                    IntInterval result = new IntInterval();
                    computedPostCondition.put(pathNode, result);
                }

                computedPostCondition.put(pathNode, new IntInterval(constantNode.asJavaConstant().asInt()));
            }

            case StoreFieldNode storeFieldNode -> {
                AccessPath key = getAccessPathFromAccessFieldNode(storeFieldNode, abstractState);
                var storeFieldEnv = execAndGet(storeFieldNode.value(), abstractState, invokeCallBack);
                computedPostCondition.put(key, storeFieldEnv.getNodeDataValue(storeFieldNode.value(), new IntInterval()));
            }

            case LoadFieldNode loadFieldNode -> {
                AccessPath key = getAccessPathFromAccessFieldNode(loadFieldNode, abstractState);
                IntInterval keyInterval = new IntInterval();

                logger.log("Key: " + key, LoggerVerbosity.INFO);
                if (preCondition.containsAccessPath(key)) {
                    keyInterval = preCondition.get(key);
                } else {
                    for (AccessPath path : abstractState.getStartNodeState().getPreCondition().getAccessPaths()) {
                        logger.log("AccessPath from init: " + path, LoggerVerbosity.INFO);
                        if (path.getElements().isEmpty() || !path.getElements().getLast().equals(key.getElements().getLast())) {
                            continue;
                        }

                        keyInterval = abstractState.getStartNodeState().getPreCondition().get(path);
                        break;
                    }
                }

                logger.log("LoadFieldNode interval: " + keyInterval, LoggerVerbosity.INFO);
                computedPostCondition.put(new AccessPath(loadFieldNode), keyInterval);
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

                IntInterval condInterval = condition.getNodeDataValue(ifNode.condition(), new IntInterval());
                computedPostCondition.put(new AccessPath(ifNode), condInterval);
            }

            case IntegerLessThanNode integerLessThanNode -> {
                Node nodeX = integerLessThanNode.getX();
                Node nodeY = integerLessThanNode.getY();
                /* Evaluate the condition, if it holds, set the postCondition to the data-flow value satisfying the condition */
                /* If we have a condition like Constant(4) < Param(5), this is true and set post to [ -inf, 4 ], because this satisfies it */
                /* If we have a condition like Constant(4) < Param(3), this is false and set post to bot, because this is unsatisfiable */
                var firstEnv = execAndGet(nodeX, abstractState, invokeCallBack);
                var secondEnv = execAndGet(nodeY, abstractState, invokeCallBack);
                AccessPath nodeAccessPath = new AccessPath(integerLessThanNode);
                IntInterval defaultInterval = new IntInterval();

                if (!(firstEnv.getNodeDataValue(nodeX, defaultInterval).isLessThan(secondEnv.getNodeDataValue(nodeY, defaultInterval)))) {
                    abstractState.getPostCondition(integerLessThanNode).setToBot();
                    return;
                }
                IntInterval secondInterval = secondEnv.get(new AccessPath(integerLessThanNode.getY()));
                computedPostCondition.put(nodeAccessPath, IntInterval.getLowerInterval(secondInterval));
            }

            case PhiNode phiNode -> {
                AccessPath path = new AccessPath(phiNode);
                IntInterval phiResult = new IntInterval();

                for (Node input : phiNode.inputs()) {
                    boolean isCyclicEdge = false;
                    for (Node inputOfInput : input.inputs()) {
                        if (inputOfInput.equals(phiNode)) {
                            isCyclicEdge = true;
                            break;
                        }
                    }

                    if (isCyclicEdge) {
                        phiResult.joinWith(abstractState.getPostCondition(input).getNodeDataValue(input, new IntInterval()));
                    } else {
                        var inputEnv = execAndGet(input, abstractState, invokeCallBack);
                        phiResult.joinWith(inputEnv.getNodeDataValue(input, new IntInterval()));
                    }

                    computedPostCondition.put(path, phiResult);
                }
            }

            case PiNode piNode -> {
                computedPostCondition = new AccessPathMap<>(new IntInterval());
                Node originalNode = piNode.getOriginalNode();
                var originalPre = abstractState.getPreCondition(originalNode);
                var originalPost = abstractState.getPostCondition(originalNode);

                for (AccessPath accessPath : originalPost.getAccessPaths()) {
                    if (originalPre.getValue().getMap().containsKey(accessPath)) {
                        continue;
                    }

                    computedPostCondition.put(new AccessPath(accessPath.getBase()), new IntInterval());
                    break;
                }
            }

            case BinaryArithmeticNode<?> binaryArithmeticNode -> {
                Node nodeX = binaryArithmeticNode.getX();
                Node nodeY = binaryArithmeticNode.getY();
                var mapX = execAndGet(nodeX, abstractState, invokeCallBack);
                var mapY = execAndGet(nodeY, abstractState, invokeCallBack);
                IntInterval firstInterval = mapX.getNodeDataValue(nodeX, new IntInterval());
                IntInterval secondInterval = mapY.getNodeDataValue(nodeY, new IntInterval());
                IntInterval result;

                switch (binaryArithmeticNode) {
                    case AddNode ignored -> {
                        result = firstInterval.add(secondInterval);
                    }
                    case SubNode ignored -> {
                        result = firstInterval.sub(secondInterval);
                    }
                    case MulNode ignored -> {
                        result = firstInterval.mul(secondInterval);
                    }
                    case FloatDivNode ignored -> {
                        result = firstInterval.div(secondInterval);
                    }
                    default -> {
                        result = new IntInterval();
                        result.setToTop();
                    }
                }
                AccessPath pathNode = new AccessPath(binaryArithmeticNode);
                computedPostCondition.put(pathNode, result);
            }

            case AllocatedObjectNode allocatedObject -> {
                computedPostCondition = new AccessPathMap<>(new IntInterval());
                computedPostCondition.put(AccessPath.fromAllocatedObject(allocatedObject), new IntInterval());
            }

            case ParameterNode parameterNode -> {
                AccessPath path = getParamAccessPath(abstractState, parameterNode.index());
                if (path == null) {
                    throw AnalysisError.interruptAnalysis("Base variable not found");
                }
                IntInterval paramInterval = getStartNodeIntervalFromPath(abstractState, path);
                computedPostCondition.put(path, paramInterval);
            }

            case ReturnNode returnNode -> {
                if (returnNode.result() == null) {
                    break;
                }

                if (returnNode.result().getStackKind().isPrimitive()) {
                    AccessPathMap<IntInterval> resultMap = execAndGet(returnNode.result(), abstractState, invokeCallBack);
                    IntInterval resultInterval = resultMap.getOnlyDataValue();
                    computedPostCondition.put(new AccessPath(new PlaceHolderAccessPathBase(AccessPathConstants.RETURN_PREFIX)), resultInterval);
                }

                if (returnNode.result().getStackKind().isObject()) {
                    if (!(returnNode.result() instanceof AllocatedObjectNode allocatedObjectNode)) {
                        break; /* object parameters will be passed by default */
                    }

                    AccessPath accessPath = AccessPath.fromAllocatedObject(allocatedObjectNode);
                    AccessPathBase base = accessPath.getBase();
                    List<AccessPath> accessPathsWithBase = computedPostCondition.getAccessPathsWithBase(base);
                    for (AccessPath path : accessPathsWithBase) {
                        IntInterval value = computedPostCondition.get(path);
                        computedPostCondition.remove(path);
                        AccessPath newPath = new AccessPath(path.getBase().addPrefix(AccessPathConstants.RETURN_PREFIX), path.getElements());
                        computedPostCondition.put(newPath, value);
                    }
                }
            }

            case Invoke invoke -> {
                /* We can use analyzeDependencyCallback to analyze calls to other methods */
                AnalysisOutcome<AccessPathMap<IntInterval>> outcome = invokeCallBack.handleInvoke(invoke, node, abstractState);
                if (outcome.isError()) {
                    logger.log("Error in handling call: " + outcome.result().toString(), LoggerVerbosity.INFO);
                    computedPostCondition.setToTop();
                } else {
                    Summary<AccessPathMap<IntInterval>> summary = outcome.summary();
                    computedPostCondition = summary.applySummary(preCondition);
                    /* Sometimes, when the method returns only an integer, we prefix it with return# in the summary, but
                       when applying it back to the caller, we remove the prefix, therefore its access path is empty,
                       and we need to assign its value to this node. */
                    AccessPath emptyAccessPath = null;
                    for (AccessPath path : computedPostCondition.getAccessPaths()) {
                        if (path.toString().isEmpty()) {
                            emptyAccessPath = path;
                            break;
                        }
                    }

                    if (emptyAccessPath != null) {
                        IntInterval value = computedPostCondition.get(emptyAccessPath);
                        AccessPath nodeAccessPath = new AccessPath(node);
                        computedPostCondition.remove(emptyAccessPath);
                        computedPostCondition.put(nodeAccessPath, value);
                    }
                }
            }

            default -> {
            }
        }

        logger.log("Finished analyzing node: " + node + " -> post: " + computedPostCondition, LoggerVerbosity.DEBUG);
        abstractState.setPostCondition(node, computedPostCondition);
    }

    private AccessPath getAccessPathFromAccessFieldNode(AccessFieldNode accessFieldNode,
                                                        AbstractState<AccessPathMap<IntInterval>> abstractState) {

        AccessPath fieldPath = AccessPath.getAccessPathFromAccessFieldNode(accessFieldNode);
        if (fieldPath != null) {
            return fieldPath;
        }

        /* We don't know the source position of the object -> it must be a parameter */
        ResolvedJavaField field = accessFieldNode.field();
        ParameterNode param = (ParameterNode) accessFieldNode.object();
        int idx = param.index();
        AccessPath base = getParamAccessPath(abstractState, idx);
        if (base == null) {
            throw AnalysisError.interruptAnalysis("Base variable not found");
        }

        return base.appendField(field.getName(), field.getModifiers());
    }

    private AccessPath getParamAccessPath(AbstractState<AccessPathMap<IntInterval>> abstractState, int idx) {
        var startState = abstractState.getStartNodeState();
        for (AccessPath path : startState.getPreCondition().getAccessPaths()) {
            if (path.getBase().toString().startsWith(AccessPathConstants.PARAM_PREFIX + idx)) {
                return path.copyOf();
            }
        }
        return null;
    }

    private IntInterval getStartNodeIntervalFromPath(AbstractState<AccessPathMap<IntInterval>> abstractState, AccessPath accessPath) {
        var startState = abstractState.getStartNodeState();
        return startState.getPreCondition().get(accessPath).copyOf();
    }

    private AccessPathMap<IntInterval> execAndGet(Node node,
                                                  AbstractState<AccessPathMap<IntInterval>> abstractState,
                                                  InvokeCallBack<AccessPathMap<IntInterval>> invokeCallBack) {
        execNode(node, abstractState, invokeCallBack);
        return abstractState.getPostCondition(node);
    }
}
