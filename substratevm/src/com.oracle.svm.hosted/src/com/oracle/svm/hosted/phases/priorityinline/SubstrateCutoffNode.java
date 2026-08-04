/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.phases.priorityinline;

import java.util.EnumSet;
import java.util.Map;

import com.oracle.svm.hosted.meta.HostedMethod;

import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.graph.NodeSourcePosition;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.Invoke;
import jdk.graal.compiler.phases.common.priorityinline.data.BenefitKind;
import jdk.graal.compiler.phases.common.priorityinline.nodes.CutoffNode;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

@NodeInfo(nameTemplate = "SVMCutoff({p#targetMethod/s}) [P = {p#priority}]")
public class SubstrateCutoffNode extends CutoffNode {
    public static final NodeClass<SubstrateCutoffNode> TYPE = NodeClass.create(SubstrateCutoffNode.class);

    private final int costEstimate;

    public SubstrateCutoffNode(NodeSourcePosition compilationRootPosition, Invoke invoke, double frequency, ResolvedJavaMethod targetMethod, ResolvedJavaType dispatchedType,
                    ResolvedJavaType originalDispatchedType, boolean monomorphic, EnumSet<BenefitKind> benefits) {
        super(TYPE, compilationRootPosition, invoke, frequency, targetMethod, dispatchedType, originalDispatchedType, monomorphic, benefits);

        /*
         * As we are doing AOT compilation, we don't have to estimate. We can use the graph's actual
         * node count, because it's already parsed anyway.
         */
        assert targetMethod instanceof HostedMethod;
        costEstimate = ((HostedMethod) targetMethod).compilationInfo.getCompilationGraph().getNodeCount();
    }

    @Override
    public int getSubtreeTotalCutoffCodeSize() {
        /*
         * Used for calculating ParentNode's size penalty in
         * Expander.DefaultPolicy.updateParentNodePriority.
         */
        return costEstimate;
    }

    @Override
    public int getCostEstimate() {
        return costEstimate;
    }

    @Override
    public Map<Object, Object> getDebugProperties(Map<Object, Object> map) {
        Map<Object, Object> debugProperties = super.getDebugProperties(map);
        debugProperties.put("Hotness", SamplingCallTreeState.getSamplingCallTreeState(callTree()).hotness(this));
        return debugProperties;
    }
}
