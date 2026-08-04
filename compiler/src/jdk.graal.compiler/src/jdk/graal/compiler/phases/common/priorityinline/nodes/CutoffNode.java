/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.phases.common.priorityinline.nodes;

import java.util.EnumSet;
import java.util.Map;

import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.graph.NodeSourcePosition;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.Invoke;
import jdk.graal.compiler.phases.common.priorityinline.InliningMath;
import jdk.graal.compiler.phases.common.priorityinline.data.BenefitKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

@NodeInfo(nameTemplate = "Cutoff [P = {p#priority}]")
public class CutoffNode extends CallTreeNode {
    public static final NodeClass<CutoffNode> TYPE = NodeClass.create(CutoffNode.class);

    private final ResolvedJavaMethod target;
    private final ResolvedJavaType dispatchedType;
    private final ResolvedJavaType originalDispatchedType;
    private boolean monomorphic;
    private EnumSet<BenefitKind> benefits;

    public CutoffNode(NodeSourcePosition compilationRootPosition, Invoke invoke, double frequency, ResolvedJavaMethod target, ResolvedJavaType dispatchedType, ResolvedJavaType originalDispatchedType,
                    boolean monomorphic,
                    EnumSet<BenefitKind> benefits) {
        this(TYPE, compilationRootPosition, invoke, frequency, target, dispatchedType, originalDispatchedType, monomorphic, benefits);
    }

    @SuppressWarnings("this-escape")
    protected CutoffNode(NodeClass<? extends CutoffNode> c, NodeSourcePosition compilationRootPosition, Invoke invoke, double frequency, ResolvedJavaMethod target, ResolvedJavaType dispatchedType,
                    ResolvedJavaType originalDispatchedType,
                    boolean monomorphic, EnumSet<BenefitKind> benefits) {
        super(c, compilationRootPosition, invoke, frequency);
        this.target = target;
        this.dispatchedType = dispatchedType;
        this.originalDispatchedType = originalDispatchedType;
        this.monomorphic = monomorphic;
        this.benefits = benefits;

        this.setActiveCutoffCount(1);
    }

    @Override
    public Map<Object, Object> getDebugProperties(Map<Object, Object> map) {
        Map<Object, Object> debugProperties = super.getDebugProperties(map);
        debugProperties.put("codeSize", target.getCodeSize());
        debugProperties.put("threshold", InliningMath.defaultExpansionThreshold(callTree().getPolicy(), this));
        return debugProperties;
    }

    public ResolvedJavaType getDispatchedType() {
        return dispatchedType;
    }

    public boolean isMonomorphic() {
        return monomorphic;
    }

    public EnumSet<BenefitKind> getBenefits() {
        return benefits;
    }

    @Override
    public ResolvedJavaMethod targetMethod() {
        return target;
    }

    public ResolvedJavaType getOriginalDispatchedType() {
        return originalDispatchedType;
    }

    public int getCostEstimate() {
        return InliningMath.getLocalCostEstimate(target);
    }

    @Override
    public boolean isForceInlined() {
        return targetMethod().shouldBeInlined() || callTree().matchesForceInlineFilter(targetMethod()) || callTree().matchDirectedInline(this) != null;
    }

    @Override
    public final void initializeCounts() {
        setCutoffCount(1);
        // Do not set active cutoff count, since it will be reset when the next round starts.
        setDeadendCount(0);
    }
}
