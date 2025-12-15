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
package com.oracle.svm.interpreter.ristretto;

import static jdk.graal.compiler.nodeinfo.NodeCycles.CYCLES_0;
import static jdk.graal.compiler.nodeinfo.NodeSize.SIZE_0;

import com.oracle.svm.graal.meta.SubstrateMethod;

import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.FixedWithNextNode;
import jdk.graal.compiler.nodes.GraphState;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.spi.Canonicalizable;
import jdk.graal.compiler.nodes.spi.CanonicalizerTool;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * 
 * Node that evaluates to true when Native Image compiles the current graph at run-time, and false
 * otherwise.
 * <p>
 * This node intentionally avoids folding during graph building or AOT image build-time (where graph
 * builder plugins could fold it prematurely). It only folds once
 * {@link jdk.graal.compiler.nodes.GraphState.StageFlag#HIGH_TIER_LOWERING} has been passed,
 * ensuring the fold occurs during compilation.
 * <p>
 * Usage notes:
 * <ul>
 * <li>Intended primarily for tests and diagnostics that need to assert runtime compilation in
 * SVM.</li>
 * <li>The result is stable after folding; before that point, the node remains as-is.</li>
 * </ul>
 * 
 * @see RistrettoDirectives#inRuntimeCompiledCode()
 */
@NodeInfo(cycles = CYCLES_0, size = SIZE_0)
public final class IsInRuntimeCompiledCodeNode extends FixedWithNextNode implements Canonicalizable {

    public static final NodeClass<IsInRuntimeCompiledCodeNode> TYPE = NodeClass.create(IsInRuntimeCompiledCodeNode.class);

    public IsInRuntimeCompiledCodeNode() {
        super(TYPE, StampFactory.forInteger(32));
    }

    @Override
    public Node canonical(CanonicalizerTool tool) {
        // wait a bit before we fold this node to be absolutely sure it can only fold during
        // compilation
        if (tool.allUsagesAvailable() && this.graph() != null && this.graph().isAfterStage(GraphState.StageFlag.HIGH_TIER_LOWERING)) {
            StructuredGraph graph = this.graph();
            GraalError.guarantee(graph != null, "Must not have a null graph at this point");
            ResolvedJavaMethod method = graph.method();
            GraalError.guarantee(method != null, "Must not have a null graph at this point");
            boolean isRuntimeCompiledCode = (method instanceof SubstrateMethod);
            return ConstantNode.forBoolean(isRuntimeCompiledCode);
        }
        return this;
    }
}
