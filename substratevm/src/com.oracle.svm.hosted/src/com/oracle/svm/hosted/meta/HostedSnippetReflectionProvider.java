/*
 * Copyright (c) 2015, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.meta;

import com.oracle.graal.pointsto.heap.AbstractImageHeapSnippetReflectionProvider;
import com.oracle.graal.pointsto.heap.ImageHeapScanner;
import com.oracle.svm.core.deopt.DeoptimizationSupport;
import com.oracle.svm.core.graal.nodes.TLABObjectHeaderConstant;
import com.oracle.svm.core.heap.Heap;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.meta.SubstrateObjectConstant;
import com.oracle.svm.shared.util.VMError;
import com.oracle.svm.hosted.code.SubstrateCompilationDirectives;

import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.NonFoldingConstantNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.ZeroExtendNode;
import jdk.graal.compiler.word.WordTypes;
import jdk.vm.ci.meta.JavaConstant;

public class HostedSnippetReflectionProvider extends AbstractImageHeapSnippetReflectionProvider {
    public HostedSnippetReflectionProvider(ImageHeapScanner heapScanner, WordTypes wordTypes) {
        super(heapScanner, wordTypes);
    }

    @Override
    public ValueNode forTLABObjectHeader(Object hub, StructuredGraph graph) {
        VMError.guarantee(hub instanceof DynamicHub, "must be a DynamicHub: %s", hub);
        int constantHeaderSize = Heap.getHeap().getObjectHeader().constantHeaderSize();
        if (constantHeaderSize < 0) {
            return null;
        }
        if (DeoptimizationSupport.enabled() && SubstrateCompilationDirectives.isDeoptTarget(graph.method())) {
            /*
             * This implies the existence of alternative entry points of the method. Since the
             * object header constant is a floating node, if it moves to before a method entry, we
             * lose its value if the method starts executing at that entry because it is not in the
             * frame state.
             */
            return null;
        }

        JavaConstant constant = new TLABObjectHeaderConstant(forObject(hub), constantHeaderSize);
        ValueNode res = NonFoldingConstantNode.create(constant, graph);
        if (constantHeaderSize == Integer.BYTES) {
            res = graph.addOrUniqueWithInputs(ZeroExtendNode.create(res, Long.SIZE, NodeView.DEFAULT));
        }
        return res;
    }

    @Override
    protected <T> T asObjectFromUnwrapped(Class<T> type, JavaConstant constant) {
        if (type == Class.class && constant.getJavaKind().isObject()) {
            /* Only unwrap the DynamicHub if a Class object is required explicitly. */
            if (getHeapScanner().getHostedValuesProvider().asObject(Object.class, constant) instanceof DynamicHub hub) {
                return type.cast(hub.getHostedJavaClass());
            }
        }
        VMError.guarantee(!(constant instanceof SubstrateObjectConstant));
        return getHeapScanner().getHostedValuesProvider().asObject(type, constant);
    }
}
