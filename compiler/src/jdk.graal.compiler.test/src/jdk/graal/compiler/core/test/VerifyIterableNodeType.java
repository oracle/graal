/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package jdk.graal.compiler.core.test;

import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.graph.Graph;
import jdk.graal.compiler.graph.IterableNodeType;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.iterators.NodeIterable;
import jdk.graal.compiler.nodes.CallTargetNode;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.Invoke;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.java.MethodCallTargetNode;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.phases.VerifyPhase;

import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Verifies that code which uses {@link Node} implementing {@link IterableNodeType} uses
 * {@link StructuredGraph#getNodes(NodeClass)} and not {@linkplain NodeIterable#filter(Class)} to
 * access a subset of a {@link Graph}.
 */
public class VerifyIterableNodeType extends VerifyPhase<CoreProviders> {

    @Override
    protected void verify(StructuredGraph graph, CoreProviders context) {
        ResolvedJavaMethod caller = graph.method();
        MetaAccessProvider metaAccess = context.getMetaAccess();
        final ResolvedJavaType nodeIterableType = metaAccess.lookupJavaType(NodeIterable.class);
        final ResolvedJavaType iterableNodeType = metaAccess.lookupJavaType(IterableNodeType.class);
        final ResolvedJavaType classType = metaAccess.lookupJavaType(Class.class);
        final ResolvedJavaType graphType = metaAccess.lookupJavaType(Graph.class);
        for (MethodCallTargetNode t : graph.getNodes(MethodCallTargetNode.TYPE)) {
            ValueNode receiver = t.receiver();
            if (receiver instanceof Invoke) {
                CallTargetNode receiverCallTarget = ((Invoke) receiver).callTarget();
                ResolvedJavaMethod receiverMethod = receiverCallTarget.targetMethod();
                if (receiverMethod.getDeclaringClass().equals(graphType) && receiverMethod.getName().equals("getNodes") && receiverMethod.getParameters().length == 0) {
                    ResolvedJavaMethod callee = t.targetMethod();
                    if (callee.getDeclaringClass().equals(nodeIterableType)) {
                        if (callee.getName().equals("filter")) {
                            ResolvedJavaMethod.Parameter[] params = callee.getParameters();
                            if (params.length == 1 && params[0].getType().equals(classType)) {
                                // call to filter
                                ValueNode v = t.arguments().get(1);
                                ResolvedJavaType javaType = v.stamp(NodeView.DEFAULT).javaType(metaAccess);
                                assert classType.isAssignableFrom(javaType) : "Need to have a class type parameter.";
                                if (v instanceof ConstantNode) {
                                    ConstantNode c = (ConstantNode) v;
                                    javaType = context.getConstantReflection().asJavaType(c.asConstant());
                                    if (iterableNodeType.isAssignableFrom(javaType)) {
                                        throw new VerificationError(
                                                        "Call to %s at callsite %s is prohibited. Argument node class %s implements IterableNodeType. Use graph.getNodes(IterableNodeType.TYPE) instead.",
                                                        callee.format("%H.%n(%p)"),
                                                        caller.format("%H.%n(%p)"), javaType.toJavaName());
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
