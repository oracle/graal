/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.phases.common.inlining.info;

import com.oracle.graal.api.code.Assumptions;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.common.calc.Condition;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.calc.CompareNode;
import com.oracle.graal.nodes.extended.LoadHubNode;
import com.oracle.graal.phases.common.inlining.InliningUtil;
import com.oracle.graal.phases.common.inlining.info.elem.Inlineable;
import com.oracle.graal.phases.util.Providers;
import com.oracle.graal.nodes.java.MethodCallTargetNode.InvokeKind;

/**
 * Represents an inlining opportunity for which profiling information suggests a monomorphic
 * receiver, but for which the receiver type cannot be proven. A type check guard will be generated
 * if this inlining is performed.
 */
public class TypeGuardInlineInfo extends AbstractInlineInfo {

    private final ResolvedJavaMethod concrete;
    private final ResolvedJavaType type;
    private Inlineable inlineableElement;

    public TypeGuardInlineInfo(Invoke invoke, ResolvedJavaMethod concrete, ResolvedJavaType type) {
        super(invoke);
        this.concrete = concrete;
        this.type = type;
        assert type.isArray() || !type.isAbstract() : type;
    }

    @Override
    public int numberOfMethods() {
        return 1;
    }

    @Override
    public ResolvedJavaMethod methodAt(int index) {
        assert index == 0;
        return concrete;
    }

    @Override
    public Inlineable inlineableElementAt(int index) {
        assert index == 0;
        return inlineableElement;
    }

    @Override
    public double probabilityAt(int index) {
        assert index == 0;
        return 1.0;
    }

    @Override
    public double relevanceAt(int index) {
        assert index == 0;
        return 1.0;
    }

    @Override
    public void setInlinableElement(int index, Inlineable inlineableElement) {
        assert index == 0;
        this.inlineableElement = inlineableElement;
    }

    @Override
    public void inline(Providers providers, Assumptions assumptions) {
        createGuard(graph(), providers.getMetaAccess());
        inline(invoke, concrete, inlineableElement, assumptions, false);
    }

    @Override
    public void tryToDevirtualizeInvoke(MetaAccessProvider metaAccess, Assumptions assumptions) {
        createGuard(graph(), metaAccess);
        InliningUtil.replaceInvokeCallTarget(invoke, graph(), InvokeKind.Special, concrete);
    }

    private void createGuard(StructuredGraph graph, MetaAccessProvider metaAccess) {
        ValueNode nonNullReceiver = InliningUtil.nonNullReceiver(invoke);
        ConstantNode typeHub = ConstantNode.forConstant(type.getEncoding(ResolvedJavaType.Representation.ObjectHub), metaAccess, graph);
        LoadHubNode receiverHub = graph.unique(new LoadHubNode(nonNullReceiver, typeHub.getKind()));

        CompareNode typeCheck = CompareNode.createCompareNode(graph, Condition.EQ, receiverHub, typeHub);
        FixedGuardNode guard = graph.add(new FixedGuardNode(typeCheck, DeoptimizationReason.TypeCheckedInliningViolated, DeoptimizationAction.InvalidateReprofile));
        assert invoke.predecessor() != null;

        ValueNode anchoredReceiver = InliningUtil.createAnchoredReceiver(graph, guard, type, nonNullReceiver, true);
        invoke.callTarget().replaceFirstInput(nonNullReceiver, anchoredReceiver);

        graph.addBeforeFixed(invoke.asNode(), guard);
    }

    @Override
    public String toString() {
        return "type-checked with type " + type.getName() + " and method " + MetaUtil.format("%H.%n(%p):%r", concrete);
    }

    public boolean shouldInline() {
        return concrete.shouldBeInlined();
    }
}
