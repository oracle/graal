/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.oracle.max.graal.compiler.phases;

import java.lang.reflect.*;

import com.oracle.max.cri.ci.*;
import com.oracle.max.cri.ri.*;
import com.oracle.max.graal.graph.*;
import com.oracle.max.graal.graph.Node.ConstantNodeParameter;
import com.oracle.max.graal.graph.Node.NodeIntrinsic;
import com.oracle.max.graal.nodes.*;
import com.oracle.max.graal.nodes.extended.*;
import com.oracle.max.graal.nodes.java.*;
import com.oracle.max.graal.nodes.util.*;

public class SnippetIntrinsificationPhase extends Phase {

    private final RiRuntime runtime;
    private final BoxingMethodPool pool;

    public SnippetIntrinsificationPhase(RiRuntime runtime, BoxingMethodPool pool) {
        this.runtime = runtime;
        this.pool = pool;
    }

    @Override
    protected void run(StructuredGraph graph) {
        for (Invoke i : graph.getInvokes()) {
            tryIntrinsify(i);
        }
    }

    private void tryIntrinsify(Invoke invoke) {
        RiResolvedMethod target = invoke.callTarget().targetMethod();
        NodeIntrinsic intrinsic = target.getAnnotation(Node.NodeIntrinsic.class);
        if (intrinsic != null) {
            Class< ? >[] parameterTypes = CiUtil.signatureToTypes(target.signature(), target.holder());

            // Prepare the arguments for the reflective constructor call on the node class.
            Object[] nodeConstructorArguments = prepareArguments(invoke, parameterTypes, target);

            // Create the new node instance.
            Class< ? > c = getNodeClass(target, intrinsic);
            Node newInstance = createNodeInstance(c, parameterTypes, nodeConstructorArguments);

            // Replace the invoke with the new node.
            invoke.node().graph().add(newInstance);
            invoke.intrinsify(newInstance);

            // Clean up checkcast instructions inserted by javac if the return type is generic.
            cleanUpReturnCheckCast(newInstance);
        }
    }

    private Object[] prepareArguments(Invoke invoke, Class< ? >[] parameterTypes, RiResolvedMethod target) {
        NodeInputList<ValueNode> arguments = invoke.callTarget().arguments();
        Object[] nodeConstructorArguments = new Object[arguments.size()];
        for (int i = 0; i < nodeConstructorArguments.length; ++i) {
            int parameterIndex = i;
            if (!invoke.callTarget().isStatic()) {
                parameterIndex--;
            }
            ValueNode argument = tryBoxingElimination(parameterIndex, target, arguments.get(i));
            ConstantNodeParameter param = CiUtil.getParameterAnnotation(ConstantNodeParameter.class, parameterIndex, target);
            if (param != null) {
                assert argument instanceof ConstantNode : "parameter " + parameterIndex + " must be compile time constant for " + invoke.callTarget().targetMethod();
                ConstantNode constantNode = (ConstantNode) argument;
                Object o = constantNode.asConstant().boxedValue();
                if (o instanceof Class< ? >) {
                    nodeConstructorArguments[i] = runtime.getType((Class< ? >) o);
                    parameterTypes[i] = RiResolvedType.class;
                } else {
                    nodeConstructorArguments[i] = o;
                }
            } else {
                nodeConstructorArguments[i] = argument;
                parameterTypes[i] = ValueNode.class;
            }
        }
        return nodeConstructorArguments;
    }

    private static Class< ? > getNodeClass(RiResolvedMethod target, NodeIntrinsic intrinsic) {
        Class< ? > result = intrinsic.value();
        if (result == NodeIntrinsic.class) {
            result = target.holder().toJava();
        }
        assert Node.class.isAssignableFrom(result);
        return result;
    }

    private ValueNode tryBoxingElimination(int parameterIndex, RiResolvedMethod target, ValueNode node) {
        if (parameterIndex >= 0) {
            Type type = target.getGenericParameterTypes()[parameterIndex];
            if (type instanceof TypeVariable) {
                TypeVariable typeVariable = (TypeVariable) type;
                if (typeVariable.getBounds().length == 1) {
                    Type boundType = typeVariable.getBounds()[0];
                    if (boundType instanceof Class && ((Class) boundType).getSuperclass() == null) {
                        // Unbound generic => try boxing elimination
                        if (node.usages().size() == 2) {
                            if (node instanceof Invoke) {
                                Invoke invokeNode = (Invoke) node;
                                MethodCallTargetNode callTarget = invokeNode.callTarget();
                                if (pool.isBoxingMethod(callTarget.targetMethod())) {
                                    if (invokeNode instanceof InvokeWithExceptionNode) {
                                        // Destroy exception edge & clear stateAfter.
                                        InvokeWithExceptionNode invokeWithExceptionNode = (InvokeWithExceptionNode) invokeNode;
                                        invokeWithExceptionNode.killExceptionEdge();
                                    }
                                    assert invokeNode.stateAfter().usages().size() == 1;
                                    invokeNode.stateAfter().delete();
                                    invokeNode.node().replaceAndDelete(invokeNode.next());
                                    ValueNode result = callTarget.arguments().get(0);
                                    GraphUtil.propagateKill(callTarget);
                                    return result;
                                }
                            }
                        }
                    }
                }
            }
        }
        return node;
    }

    private static Node createNodeInstance(Class< ? > nodeClass, Class< ? >[] parameterTypes, Object[] nodeConstructorArguments) {

        Constructor< ? > constructor;
        try {
            constructor = nodeClass.getDeclaredConstructor(parameterTypes);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        try {
            return (ValueNode) constructor.newInstance(nodeConstructorArguments);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void cleanUpReturnCheckCast(Node newInstance) {
        if (newInstance instanceof ValueNode && ((ValueNode) newInstance).kind() != CiKind.Object) {
            StructuredGraph graph = (StructuredGraph) newInstance.graph();
            for (Node usage : newInstance.usages().snapshot()) {
                if (usage instanceof CheckCastNode) {
                    CheckCastNode checkCastNode = (CheckCastNode) usage;
                    for (Node checkCastUsage : checkCastNode.usages().snapshot()) {
                        if (checkCastUsage instanceof ValueAnchorNode) {
                            ValueAnchorNode valueAnchorNode = (ValueAnchorNode) checkCastUsage;
                            graph.removeFixed(valueAnchorNode);
                        } else if (checkCastUsage instanceof MethodCallTargetNode) {
                            MethodCallTargetNode checkCastCallTarget = (MethodCallTargetNode) checkCastUsage;
                            assert pool.isUnboxingMethod(checkCastCallTarget.targetMethod());
                            Invoke invokeNode = checkCastCallTarget.invoke();
                            invokeNode.node().replaceAtUsages(newInstance);
                            if (invokeNode instanceof InvokeWithExceptionNode) {
                                // Destroy exception edge & clear stateAfter.
                                InvokeWithExceptionNode invokeWithExceptionNode = (InvokeWithExceptionNode) invokeNode;

                                invokeWithExceptionNode.killExceptionEdge();
                                graph.removeSplit(invokeWithExceptionNode, InvokeWithExceptionNode.NORMAL_EDGE);
                            } else {
                                graph.removeFixed((InvokeNode) invokeNode);
                            }
                            checkCastCallTarget.safeDelete();
                        } else if (checkCastUsage instanceof FrameState) {
                            checkCastUsage.replaceFirstInput(checkCastNode, null);
                        } else {
                            assert false : "unexpected checkcast usage: " + checkCastUsage;
                        }
                    }
                    checkCastNode.safeDelete();
                }
            }
        }
    }
}
