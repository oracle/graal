/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.svm.hosted.webimage.pointsto;

import com.oracle.graal.pointsto.PointsToAnalysis;
import com.oracle.graal.pointsto.flow.MethodFlowsGraph;
import com.oracle.graal.pointsto.flow.ProxyTypeFlow;
import com.oracle.graal.pointsto.flow.TypeFlow;
import com.oracle.graal.pointsto.flow.builder.TypeFlowBuilder;
import com.oracle.graal.pointsto.flow.builder.TypeFlowGraphBuilder;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.meta.PointsToAnalysisMethod;
import com.oracle.svm.hosted.analysis.flow.SVMMethodTypeFlowBuilder;
import com.oracle.svm.hosted.webimage.codegen.node.InterceptJSInvokeNode;
import com.oracle.svm.hosted.webimage.js.JSBody;
import com.oracle.svm.hosted.webimage.pointsto.flow.InterceptJSInvokeTypeFlow;

import jdk.graal.compiler.core.common.type.ObjectStamp;
import jdk.graal.compiler.nodes.FixedNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.vm.ci.code.BytecodePosition;

public class WebImageMethodTypeFlowBuilder extends SVMMethodTypeFlowBuilder {
    public WebImageMethodTypeFlowBuilder(PointsToAnalysis bb, PointsToAnalysisMethod method, MethodFlowsGraph flowsGraph, MethodFlowsGraph.GraphKind graphKind) {
        super(bb, method, flowsGraph, graphKind);
    }

    @Override
    protected boolean delegateNodeProcessing(FixedNode n, TypeFlowsOfNodes state) {
        super.delegateNodeProcessing(n, state);
        return processWebImageNodes(bb, method, typeFlowGraphBuilder, n, state, this.flowsGraph);
    }

    public static boolean processWebImageNodes(PointsToAnalysis bb, PointsToAnalysisMethod method, TypeFlowGraphBuilder typeFlowGraphBuilder, FixedNode n, TypeFlowsOfNodes state,
                    MethodFlowsGraph flowsGraph) {
        if (n instanceof InterceptJSInvokeNode) {
            InterceptJSInvokeNode node = (InterceptJSInvokeNode) n;

            TypeFlowBuilder<?>[] argumentBuilders = new TypeFlowBuilder<?>[node.arguments().size()];
            for (int i = 0; i < argumentBuilders.length; i++) {
                final ValueNode argument = node.arguments().get(i);
                if (argument.stamp(NodeView.DEFAULT) instanceof ObjectStamp) {
                    TypeFlowBuilder<?> argumentBuilder = state.lookup(argument);
                    argumentBuilders[i] = argumentBuilder;
                }
            }

            TypeFlowBuilder<InterceptJSInvokeTypeFlow> interceptBuilder = TypeFlowBuilder.create(bb, method, state.getPredicate(), node, InterceptJSInvokeTypeFlow.class, () -> {
                BytecodePosition pos = node.getNodeSourcePosition();
                if (pos == null) {
                    pos = new BytecodePosition(null, node.graph().method(), node.bci());
                }
                TypeFlow<?>[] arguments = new TypeFlow<?>[argumentBuilders.length];
                for (int i = 0; i < arguments.length; i++) {
                    if (argumentBuilders[i] != null) {
                        arguments[i] = argumentBuilders[i].get();
                    }
                }
                InterceptJSInvokeTypeFlow intercept = new InterceptJSInvokeTypeFlow(pos, (AnalysisMethod) node.targetMethod(), arguments);
                assert flowsGraph != null;
                flowsGraph.addMiscEntryFlow(intercept);
                return intercept;
            });

            for (int i = 0; i < argumentBuilders.length; i++) {
                if (argumentBuilders[i] != null) {
                    interceptBuilder.addUseDependency(argumentBuilders[i]);
                    interceptBuilder.addObserverDependency(argumentBuilders[i]);
                }
            }

            typeFlowGraphBuilder.registerSinkBuilder(interceptBuilder);
            return true;
        } else if (n instanceof JSBody jsBody && n.stamp(NodeView.DEFAULT) instanceof ObjectStamp stamp) {
            ValueNode node = jsBody.asNode();
            AnalysisType type = (AnalysisType) (stamp.type() == null ? bb.getObjectType() : stamp.type());
            ProxyTypeFlow proxy = new ProxyTypeFlow(node.getNodeSourcePosition(), type.getTypeFlow(bb, true));
            flowsGraph.addNodeFlow(n, proxy);
            return true;
        }

        return false;
    }
}
