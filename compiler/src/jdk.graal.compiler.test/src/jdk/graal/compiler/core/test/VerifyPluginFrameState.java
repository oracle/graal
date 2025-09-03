/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Set;

import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.java.FrameStateBuilder;
import jdk.graal.compiler.nodes.Invoke;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.StateSplit;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugin;
import jdk.graal.compiler.nodes.java.MethodCallTargetNode;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.nodes.spi.Proxy;
import jdk.graal.compiler.phases.VerifyPhase;
import jdk.graal.compiler.replacements.InvocationPluginHelper;
import jdk.graal.compiler.util.EconomicHashSet;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Checks that the {@link InvocationPlugin}s that call {@link GraphBuilderContext#add} on
 * {@link StateSplit} nodes don't also call {@link GraphBuilderContext#addPush} on it. Various
 * {@code emitReturn} methods in {@link InvocationPluginHelper} that also call addPush are also
 * checked. This is checked to ensure that the resulting frame state has the correct value on the
 * top stack as checked by {@code verifyStackEffect} in {@link FrameStateBuilder}.
 */
public class VerifyPluginFrameState extends VerifyPhase<CoreProviders> {

    Set<Node> transitiveUsages(Node node, Set<Node> usages) {
        for (Node usage : node.usages()) {
            if (usages.contains(usage)) {
                continue;
            }
            if (usage instanceof Proxy) {
                transitiveUsages(usage, usages);
            } else {
                usages.add(usage);
            }
        }
        return usages;
    }

    @Override
    protected void verify(StructuredGraph graph, CoreProviders context) {
        MetaAccessProvider metaAccess = context.getMetaAccess();
        ResolvedJavaMethod method = graph.method();

        if (!method.getName().equals("apply")) {
            return;
        }
        ResolvedJavaType invocationPluginType = metaAccess.lookupJavaType(InvocationPlugin.class);
        if (!invocationPluginType.isAssignableFrom(method.getDeclaringClass())) {
            return;
        }

        ResolvedJavaType graphBuilderContextType = metaAccess.lookupJavaType(GraphBuilderContext.class);
        ResolvedJavaType invocationPluginHelperType = metaAccess.lookupJavaType(InvocationPluginHelper.class);
        ResolvedJavaType stateSplitType = metaAccess.lookupJavaType(StateSplit.class);

        for (MethodCallTargetNode callTargetNode : graph.getNodes().filter(MethodCallTargetNode.class)) {
            // Look for calls to GraphBuilderContext.add
            ResolvedJavaMethod target = callTargetNode.targetMethod();
            if (!target.getName().equals("add") || !graphBuilderContextType.isAssignableFrom(target.getDeclaringClass())) {
                continue;
            }

            // See if the added node is a StateSplit
            ValueNode added = callTargetNode.arguments().get(1);
            ResolvedJavaType type = added.stamp(NodeView.DEFAULT).javaType(metaAccess);
            if (type == null) {
                continue;
            }
            if (!stateSplitType.isAssignableFrom(type)) {
                continue;
            }

            Invoke invoke = callTargetNode.invoke();
            // Examine all usages, including usages through proxies
            for (Node usage : transitiveUsages(invoke.asNode(), new EconomicHashSet<>())) {
                if (usage == callTargetNode) {
                    continue;
                }
                if (usage instanceof MethodCallTargetNode) {
                    MethodCallTargetNode call = (MethodCallTargetNode) usage;
                    ResolvedJavaMethod invokedMethod = call.targetMethod();
                    if (graphBuilderContextType.isAssignableFrom(invokedMethod.getDeclaringClass())) {
                        if (invokedMethod.getName().equals("add")) {
                            throw new VerificationError(invoke, "%s is added multiple times %s %s", added, invoke, call.invoke());
                        } else if (invokedMethod.getName().equals("addPush")) {
                            throw new VerificationError(invoke, "%s is both added %s and pushed %s", added, invoke, call.invoke());
                        }
                    } else if (invocationPluginHelperType.isAssignableFrom(invokedMethod.getDeclaringClass())) {
                        if (invokedMethod.getName().startsWith("emitReturn") || invokedMethod.getName().equals("emitFinalReturn")) {
                            throw new VerificationError(invoke, "%s is both added %s and pushed %s", added, invoke, call.invoke());
                        }
                    }
                }
            }
        }
    }
}
