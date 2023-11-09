/*
 * Copyright (c) 2013, 2022, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.phases.common.inlining.info;

import jdk.graal.compiler.phases.common.inlining.info.elem.Inlineable;
import org.graalvm.collections.EconomicSet;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.CallTargetNode.InvokeKind;
import jdk.graal.compiler.nodes.Invoke;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.phases.common.inlining.InliningUtil;
import jdk.graal.compiler.phases.util.Providers;

import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.SpeculationLog.Speculation;

/**
 * Represents an inlining opportunity for which profiling information suggests a monomorphic
 * receiver, but for which the receiver type cannot be proven. A type check guard will be generated
 * if this inlining is performed.
 */
public class TypeGuardInlineInfo extends AbstractInlineInfo {

    private final ResolvedJavaMethod concrete;
    private final ResolvedJavaType type;
    private Inlineable inlineableElement;
    private final Speculation speculation;

    public TypeGuardInlineInfo(Invoke invoke, ResolvedJavaMethod concrete, ResolvedJavaType type, Speculation speculation) {
        super(invoke);
        this.concrete = concrete;
        this.type = type;
        assert type.isArray() || type.isConcrete() : type;
        this.speculation = speculation;
    }

    @Override
    public int numberOfMethods() {
        return 1;
    }

    @Override
    public ResolvedJavaMethod methodAt(int index) {
        assert index == 0 : index;
        return concrete;
    }

    @Override
    public Inlineable inlineableElementAt(int index) {
        assert index == 0 : index;
        return inlineableElement;
    }

    @Override
    public double probabilityAt(int index) {
        assert index == 0 : index;
        return 1.0;
    }

    @Override
    public double relevanceAt(int index) {
        assert index == 0 : index;
        return 1.0;
    }

    @Override
    public void setInlinableElement(int index, Inlineable inlineableElement) {
        assert index == 0 : index;
        this.inlineableElement = inlineableElement;
    }

    @Override
    public EconomicSet<Node> inline(CoreProviders providers, String reason) {
        InliningUtil.insertTypeGuard(providers, invoke, type, speculation);
        return inline(invoke, concrete, inlineableElement, false, reason);
    }

    @Override
    public void tryToDevirtualizeInvoke(Providers providers) {
        InliningUtil.insertTypeGuard(providers, invoke, type, speculation);
        InliningUtil.replaceInvokeCallTarget(invoke, graph(), InvokeKind.Special, concrete);
    }

    @Override
    public String toString() {
        return "type-checked with type " + type.getName() + " and method " + concrete.format("%H.%n(%p):%r");
    }

    @Override
    public boolean shouldInline() {
        return concrete.shouldBeInlined();
    }
}
