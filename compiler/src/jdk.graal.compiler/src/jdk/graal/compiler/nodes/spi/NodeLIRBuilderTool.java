/*
 * Copyright (c) 2011, 2020, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.nodes.spi;

import java.util.List;

import jdk.graal.compiler.core.common.cfg.BlockMap;
import jdk.graal.compiler.core.common.spi.ForeignCallDescriptor;
import jdk.graal.compiler.core.common.spi.ForeignCallLinkage;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.lir.LIRFrameState;
import jdk.graal.compiler.lir.gen.LIRGeneratorTool;
import jdk.graal.compiler.nodes.cfg.HIRBlock;
import jdk.graal.compiler.nodes.extended.ForeignCall;
import jdk.graal.compiler.nodes.extended.SwitchNode;
import jdk.graal.compiler.nodes.AbstractBeginNode;
import jdk.graal.compiler.nodes.AbstractEndNode;
import jdk.graal.compiler.nodes.AbstractMergeNode;
import jdk.graal.compiler.nodes.BreakpointNode;
import jdk.graal.compiler.nodes.DeoptimizingNode;
import jdk.graal.compiler.nodes.FullInfopointNode;
import jdk.graal.compiler.nodes.IfNode;
import jdk.graal.compiler.nodes.Invoke;
import jdk.graal.compiler.nodes.LoopEndNode;
import jdk.graal.compiler.nodes.SafepointNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.ConditionalNode;

public interface NodeLIRBuilderTool extends NodeValueMap {

    // TODO (je) remove and move into the Node
    LIRFrameState state(DeoptimizingNode deopt);

    void emitIf(IfNode i);

    void emitConditional(ConditionalNode i);

    void emitSwitch(SwitchNode i);

    void emitInvoke(Invoke i);

    void emitForeignCall(ForeignCall i);

    // Handling of block-end nodes still needs to be unified in the LIRGenerator.
    void visitMerge(AbstractMergeNode i);

    void visitEndNode(AbstractEndNode i);

    void visitLoopEnd(LoopEndNode i);

    // These methods define the contract a runtime specific backend must provide.

    void visitSafepointNode(SafepointNode i);

    void visitBreakpointNode(BreakpointNode i);

    void visitFullInfopointNode(FullInfopointNode i);

    LIRGeneratorTool getLIRGeneratorTool();

    void emitOverflowCheckBranch(AbstractBeginNode overflowSuccessor, AbstractBeginNode next, Stamp compareStamp, double probability);

    void doBlock(HIRBlock block, StructuredGraph graph, BlockMap<List<Node>> blockMap);

    void emitReadExceptionObject(ValueNode node);

    @SuppressWarnings("unused")
    default ForeignCallLinkage lookupGraalStub(ValueNode valueNode, ForeignCallDescriptor foreignCallDescriptor) {
        return null;
    }
}
