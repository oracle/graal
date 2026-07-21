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
package jdk.graal.compiler.phases.common.priorityinline.nodes.devirtualization;

import jdk.graal.compiler.core.common.calc.CanonicalCondition;
import jdk.graal.compiler.nodes.AbstractBeginNode;
import jdk.graal.compiler.nodes.CallTargetNode;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.DirectCallTargetNode;
import jdk.graal.compiler.nodes.FixedWithNextNode;
import jdk.graal.compiler.nodes.IndirectCallTargetNode;
import jdk.graal.compiler.nodes.Invoke;
import jdk.graal.compiler.nodes.LogicNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.CompareNode;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.phases.common.priorityinline.InliningProvider;
import jdk.graal.compiler.word.WordCastNode;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Devirtualization that dispatches a direct call based on an address check. This devirtualization
 * compares the address of the indirect call against the address of the expected dispatched method.
 * It would be typically used with {@link IndirectCallTargetNode}.
 */
public abstract class AddressBasedDevirtualization extends ToDirectCallDevirtualization {

    @Override
    public LogicNode createDevirtualizationCondition(CoreProviders coreProviders, InliningProvider inliningProvider, StructuredGraph graph, Invoke virtualInvoke,
                    AbstractBeginNode invocationBlock) {
        Constant concreteMethodAddressConstant = dispatchedMethod().getEncoding();
        MetaAccessProvider metaAccess = coreProviders.getMetaAccess();
        IndirectCallTargetNode callTarget = (IndirectCallTargetNode) virtualInvoke.callTarget();
        ConstantNode concreteMethodAddress = ConstantNode.forConstant(coreProviders.getStampProvider().createMethodStamp(), concreteMethodAddressConstant, metaAccess, graph);
        WordCastNode castedConstant = graph.add(WordCastNode.addressToWord(concreteMethodAddress, JavaKind.Long));
        ValueNode dispatchAddress = callTarget.computedAddress();
        FixedWithNextNode invokePred = (FixedWithNextNode) virtualInvoke.asNode().predecessor();
        invokePred.setNext(castedConstant);
        castedConstant.setNext(virtualInvoke.asFixedNode());
        return CompareNode.createCompareNode(graph, CanonicalCondition.EQ, dispatchAddress, castedConstant, null, NodeView.DEFAULT);
    }

    @Override
    protected CallTargetNode duplicateCallTargetForDirectCall(StructuredGraph graph, Invoke newInvoke, InliningProvider inliningProvider) {
        IndirectCallTargetNode indirectCallTarget = (IndirectCallTargetNode) newInvoke.callTarget();
        ResolvedJavaMethod dispatchedMethod = dispatchedMethod();
        CallTargetNode.InvokeKind invokeKind = dispatchedMethod.isStatic() ? CallTargetNode.InvokeKind.Static : CallTargetNode.InvokeKind.Special;
        DirectCallTargetNode directCallTarget = inliningProvider.createDirectCallTarget(indirectCallTarget.arguments().toArray(ValueNode.EMPTY_ARRAY), indirectCallTarget.returnStamp(),
                        indirectCallTarget.signature(), dispatchedMethod(), invokeKind);
        return graph.add(directCallTarget);
    }
}
