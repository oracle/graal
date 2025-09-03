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

package com.oracle.svm.hosted.webimage.wasm.phases;

import com.oracle.svm.core.graal.nodes.ReadExceptionObjectNode;
import com.oracle.svm.hosted.webimage.wasm.codegen.WebImageWasmBackend;
import com.oracle.svm.hosted.webimage.wasm.codegen.WebImageWasmProviders;

import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.nodes.FixedNode;
import jdk.graal.compiler.nodes.FixedWithNextNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.WithExceptionNode;
import jdk.graal.compiler.nodes.debug.BlackholeNode;
import jdk.graal.compiler.phases.BasePhase;
import jdk.graal.compiler.phases.tiers.LowTierContext;

/**
 * Makes sure that all values produced by {@link ValueNode}s are consumed.
 * <p>
 * Each {@link FixedNode} without usages is attached to a {@link BlackholeNode} or removed if it is
 * side-effect free.
 * <p>
 * In WASM, each produced value is pushed onto the stack and must be consumed at some point.
 */
public class ValueDropPhase extends BasePhase<LowTierContext> {
    @Override
    protected void run(StructuredGraph graph, LowTierContext context) {
        WebImageWasmProviders providers = ((WebImageWasmBackend) context.getTargetProvider()).getWasmProviders();
        for (FixedNode node : graph.getNodes().filter(FixedNode.class)) {
            if (!providers.util().hasValue(node)) {
                continue;
            }

            if (providers.util().actualUsages(node).isNotEmpty()) {
                continue;
            }

            /*
             * Unused ReadExceptionObjectNodes can be completely removed since they are side-effect
             * free.
             *
             * It may be that WasmUtil#hasValue return false, but the node has usages (only used in
             * framestates). In that case, we can't just remove it and instead treat it like any
             * other fixed node.
             */
            if (node instanceof ReadExceptionObjectNode && node.hasNoUsages()) {
                graph.removeFixed((FixedWithNextNode) node);
                continue;
            }

            // Currently there are no other non-void FixedNodes
            if (node instanceof FixedWithNextNode fixedWithNextNode) {
                graph.addAfterFixed(fixedWithNextNode, graph.add(new BlackholeNode(node)));
            } else if (node instanceof WithExceptionNode withExceptionNode) {
                graph.addAfterFixed(withExceptionNode.next(), graph.add(new BlackholeNode(node)));
                graph.addAfterFixed(withExceptionNode.exceptionEdge(), graph.add(new BlackholeNode(node)));
            } else {
                GraalError.shouldNotReachHere(node.toString()); // ExcludeFromJacocoGeneratedReport
            }
        }

        for (ValueNode node : graph.getNodes().filter(ValueNode.class)) {
            if (!node.stamp(NodeView.DEFAULT).hasValues()) {
                continue;
            }

            GraalError.guarantee(node.usages().isNotEmpty(), "Value node without usages still exist: %s", node);
        }
    }
}
