package com.oracle.svm.hosted.analysis.ai.example.access;

import com.oracle.graal.pointsto.util.AnalysisError;
import com.oracle.svm.hosted.analysis.ai.analyzer.AnalysisOutcome;
import com.oracle.svm.hosted.analysis.ai.analyzer.call.CallCallback;
import com.oracle.svm.hosted.analysis.ai.domain.EnvironmentDomain;
import com.oracle.svm.hosted.analysis.ai.domain.IntInterval;
import com.oracle.svm.hosted.analysis.ai.domain.access.AccessPath;
import com.oracle.svm.hosted.analysis.ai.domain.access.AccessPathBase;
import com.oracle.svm.hosted.analysis.ai.domain.access.ClassAccessPathBase;
import com.oracle.svm.hosted.analysis.ai.domain.access.ObjectAccessPathBase;
import com.oracle.svm.hosted.analysis.ai.fixpoint.state.AbstractStateMap;
import com.oracle.svm.hosted.analysis.ai.interpreter.NodeInterpreter;
import com.oracle.svm.hosted.analysis.ai.summary.Summary;
import com.oracle.svm.hosted.analysis.ai.util.AbstractInterpretationLogger;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeSourcePosition;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.Invoke;
import jdk.graal.compiler.nodes.ParameterNode;
import jdk.graal.compiler.nodes.PiNode;
import jdk.graal.compiler.nodes.ReturnNode;
import jdk.graal.compiler.nodes.calc.AddNode;
import jdk.graal.compiler.nodes.calc.BinaryArithmeticNode;
import jdk.graal.compiler.nodes.calc.FloatDivNode;
import jdk.graal.compiler.nodes.calc.MulNode;
import jdk.graal.compiler.nodes.calc.SubNode;
import jdk.graal.compiler.nodes.java.AccessFieldNode;
import jdk.graal.compiler.nodes.java.LoadFieldNode;
import jdk.graal.compiler.nodes.java.LoadIndexedNode;
import jdk.graal.compiler.nodes.java.StoreFieldNode;
import jdk.graal.compiler.nodes.java.StoreIndexedNode;
import jdk.graal.compiler.nodes.virtual.AllocatedObjectNode;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaType;

import java.util.List;

public class AccessPathIntervalNodeInterpreter implements NodeInterpreter<EnvironmentDomain<IntInterval>> {

    @Override
    public EnvironmentDomain<IntInterval> execEdge(Node source,
                                                   Node destination,
                                                   AbstractStateMap<EnvironmentDomain<IntInterval>> abstractStateMap) {

        abstractStateMap.getPreCondition(destination).joinWith(abstractStateMap.getPostCondition(source));
        return abstractStateMap.getPostCondition(destination);
    }

    @Override
    public EnvironmentDomain<IntInterval> execNode(Node node,
                                                   AbstractStateMap<EnvironmentDomain<IntInterval>> abstractStateMap,
                                                   CallCallback<EnvironmentDomain<IntInterval>> analyzeDependencyCallBack) {


        AbstractInterpretationLogger logger = AbstractInterpretationLogger.getInstance();
        EnvironmentDomain<IntInterval> preCondition = abstractStateMap.getPreCondition(node);
        EnvironmentDomain<IntInterval> computedPostCondition = preCondition.copyOf();

        switch (node) {

            case ConstantNode constantNode -> {
                if (constantNode.asJavaConstant() == null || !constantNode.asJavaConstant().getJavaKind().isNumericInteger()) {
                    IntInterval result = new IntInterval();
                    computedPostCondition.setExprValue(result);
                }

                IntInterval result = new IntInterval(constantNode.asJavaConstant().asInt());
                computedPostCondition.setExprValue(result);
            }

            case StoreFieldNode storeFieldNode -> {
                AccessPath key = getAccessPathFromAccessFieldNode(storeFieldNode, abstractStateMap);
                EnvironmentDomain<IntInterval> storeFieldEnv = execNode(storeFieldNode.value(), abstractStateMap, analyzeDependencyCallBack);
                computedPostCondition.put(key, storeFieldEnv.getExprValue());
            }

            case LoadFieldNode loadFieldNode -> {
                AccessPath key = getAccessPathFromAccessFieldNode(loadFieldNode, abstractStateMap);
                IntInterval keyInterval = preCondition.get(key);
                computedPostCondition.setExprValue(keyInterval);
            }

            case StoreIndexedNode storeIndexedNode -> {
                // TODO
            }

            case LoadIndexedNode loadIndexedNode -> {
                // TODO
            }

            case PiNode piNode -> {
                computedPostCondition = new EnvironmentDomain<>(new IntInterval());
                Node originalNode = piNode.getOriginalNode();
                EnvironmentDomain<IntInterval> originalPre = abstractStateMap.getPreCondition(originalNode);
                EnvironmentDomain<IntInterval> originalPost = abstractStateMap.getPostCondition(originalNode);

                for (AccessPath accessPath : originalPost.getValue().getMap().keySet()) {
                    if (originalPre.getValue().getMap().containsKey(accessPath)) {
                        continue;
                    }

                    computedPostCondition.put(new AccessPath(accessPath.getBase()), new IntInterval());
                    break;
                }
            }

            case BinaryArithmeticNode<?> binaryArithmeticNode -> {
                EnvironmentDomain<IntInterval> firstEnv = execNode(binaryArithmeticNode.getX(), abstractStateMap, analyzeDependencyCallBack);
                EnvironmentDomain<IntInterval> secondEnv = execNode(binaryArithmeticNode.getY(), abstractStateMap, analyzeDependencyCallBack);
                IntInterval result = new IntInterval();
                IntInterval firstInterval = firstEnv.getExprValue();
                IntInterval secondInterval = secondEnv.getExprValue();
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
                computedPostCondition.setExprValue(result);
            }

            case ParameterNode parameterNode -> {
                int idx = parameterNode.index();
                EnvironmentDomain<IntInterval> initial = abstractStateMap.getInitialDomain();

                /* This parameterNode will have a primitive type and therefore a unique prefix
                ( params have index in name and only objects can have same prefix ) */
                IntInterval paramValue = initial.getValueAtPlaceHolderPrefix("param" + idx, new IntInterval());
                computedPostCondition.setExprValue(paramValue);
            }

            case AllocatedObjectNode allocatedObject -> {
                computedPostCondition = new EnvironmentDomain<>(new IntInterval());
                computedPostCondition.put(AccessPath.fromAllocatedObject(allocatedObject), new IntInterval());
            }

            case ReturnNode returnNode -> {
                if (returnNode.result() == null) {
                    break;
                }

                if (returnNode.result().getStackKind().isPrimitive()) {
                    IntInterval intResult = execEdge(returnNode.result(), returnNode, abstractStateMap).getExprValue();
                    computedPostCondition.setExprValue(intResult);
                }

                if (returnNode.result().getStackKind().isObject()) {
                    if (!(returnNode.result() instanceof AllocatedObjectNode allocatedObjectNode)) {
                        break; /* object parameters will be passed default */
                    }

                    AccessPath accessPath = AccessPath.fromAllocatedObject(allocatedObjectNode);
                    AccessPathBase base = accessPath.getBase();
                    List<AccessPath> accessPathsWithBase = computedPostCondition.getAccessPathsWithBase(base);
                    for (AccessPath path : accessPathsWithBase) {
                        IntInterval value = computedPostCondition.get(path);
                        computedPostCondition.remove(path);
                        AccessPath newPath = new AccessPath(path.getBase().addPrefix("return#"), path.getElements());
                        computedPostCondition.put(newPath, value);
                    }
                }
            }

            case Invoke invoke -> {
                /* We can use analyzeDependencyCallback to analyze calls to other methods */
                AnalysisOutcome<EnvironmentDomain<IntInterval>> outcome = analyzeDependencyCallBack.handleCall(invoke, node, abstractStateMap);
                if (outcome.isError()) {
                    logger.logToFile("Error in handling call: " + outcome.result().toString());
                    computedPostCondition.setToTop();
                } else {
                    Summary<EnvironmentDomain<IntInterval>> summary = outcome.summary();
                    computedPostCondition = summary.applySummary(invoke, node, preCondition);
                }
            }

            default -> {
            }
        }

        abstractStateMap.setPostCondition(node, computedPostCondition);
        return computedPostCondition;
    }

    private AccessPath getAccessPathFromAccessFieldNode(AccessFieldNode accessFieldNode,
                                                        AbstractStateMap<EnvironmentDomain<IntInterval>> abstractStateMap) {
        ResolvedJavaField field = accessFieldNode.field();
        if (field.isStatic()) {
            /* For static fields, use the declaring class as the base variable */
            AccessPathBase base = new ClassAccessPathBase(field.getDeclaringClass());
            return new AccessPath(base).appendField(field.getName(), field.getModifiers());
        }

        if (accessFieldNode.object().getNodeSourcePosition() != null) {
            ResolvedJavaType declaringClass = field.getDeclaringClass();
            NodeSourcePosition nodeSourcePosition = accessFieldNode.object().getNodeSourcePosition();
            AccessPathBase base = new ObjectAccessPathBase(declaringClass, nodeSourcePosition);
            return new AccessPath(base).appendField(field.getName(), field.getModifiers());
        }

        /* We don't know the source position of the object -> it must be a parameter */
        ParameterNode param = (ParameterNode) accessFieldNode.object();
        int idx = param.index();
        EnvironmentDomain<IntInterval> env = abstractStateMap.getInitialDomain();
        AccessPath base = getAccessPathBaseFromPrefix(env, "param" + idx);
        if (base == null) {
            throw AnalysisError.interruptAnalysis("Base variable not found");
        }

        return base.appendField(field.getName(), field.getModifiers());
    }

    private AccessPath getAccessPathBaseFromPrefix(EnvironmentDomain<IntInterval> env, String prefix) {
        for (AccessPath path : env.getValue().getMap().keySet()) {
            if (path.getBase().toString().startsWith(prefix)) {
                return new AccessPath(path.getBase());
            }
        }
        return null;
    }
}
