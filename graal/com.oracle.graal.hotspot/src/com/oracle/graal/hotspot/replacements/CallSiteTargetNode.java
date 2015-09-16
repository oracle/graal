/*
 * Copyright (c) 2013, 2014, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.hotspot.replacements;

import jdk.internal.jvmci.hotspot.HotSpotObjectConstant;
import jdk.internal.jvmci.meta.Assumptions;
import jdk.internal.jvmci.meta.JavaConstant;
import jdk.internal.jvmci.meta.JavaType;
import jdk.internal.jvmci.meta.MetaAccessProvider;
import jdk.internal.jvmci.meta.ResolvedJavaMethod;

import com.oracle.graal.graph.Node;
import com.oracle.graal.graph.NodeClass;
import com.oracle.graal.graph.spi.Canonicalizable;
import com.oracle.graal.graph.spi.CanonicalizerTool;
import com.oracle.graal.nodeinfo.NodeInfo;
import com.oracle.graal.nodes.CallTargetNode.InvokeKind;
import com.oracle.graal.nodes.ConstantNode;
import com.oracle.graal.nodes.InvokeNode;
import com.oracle.graal.nodes.ValueNode;
import com.oracle.graal.nodes.spi.Lowerable;
import com.oracle.graal.nodes.spi.LoweringTool;
import com.oracle.graal.replacements.nodes.MacroStateSplitNode;

@NodeInfo
public final class CallSiteTargetNode extends MacroStateSplitNode implements Canonicalizable, Lowerable {

    public static final NodeClass<CallSiteTargetNode> TYPE = NodeClass.create(CallSiteTargetNode.class);

    public CallSiteTargetNode(InvokeKind invokeKind, ResolvedJavaMethod targetMethod, int bci, JavaType returnType, ValueNode receiver) {
        super(TYPE, invokeKind, targetMethod, bci, returnType, receiver);
    }

    private ValueNode getCallSite() {
        return arguments.get(0);
    }

    public static ConstantNode tryFold(ValueNode callSite, MetaAccessProvider metaAccess, Assumptions assumptions) {
        if (callSite != null && callSite.isConstant() && !callSite.isNullConstant()) {
            HotSpotObjectConstant c = (HotSpotObjectConstant) callSite.asConstant();
            JavaConstant target = c.getCallSiteTarget(assumptions);
            if (target != null) {
                return ConstantNode.forConstant(target, metaAccess);
            }
        }
        return null;
    }

    @Override
    public Node canonical(CanonicalizerTool tool) {
        ConstantNode target = tryFold(getCallSite(), tool.getMetaAccess(), graph().getAssumptions());
        if (target != null) {
            return target;
        }

        return this;
    }

    @Override
    public void lower(LoweringTool tool) {
        ConstantNode target = tryFold(getCallSite(), tool.getMetaAccess(), graph().getAssumptions());

        if (target != null) {
            graph().replaceFixedWithFloating(this, target);
        } else {
            InvokeNode invoke = createInvoke();
            graph().replaceFixedWithFixed(this, invoke);
            invoke.lower(tool);
        }
    }
}
