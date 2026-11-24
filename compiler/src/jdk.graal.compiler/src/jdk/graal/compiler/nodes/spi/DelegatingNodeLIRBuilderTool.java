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
package jdk.graal.compiler.nodes.spi;

import java.util.List;

import jdk.graal.compiler.core.common.cfg.BlockMap;
import jdk.graal.compiler.core.common.spi.ForeignCallDescriptor;
import jdk.graal.compiler.core.common.spi.ForeignCallLinkage;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.lir.LIRFrameState;
import jdk.graal.compiler.lir.gen.LIRGeneratorTool;
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
import jdk.graal.compiler.nodes.cfg.HIRBlock;
import jdk.graal.compiler.nodes.extended.ForeignCall;
import jdk.graal.compiler.nodes.extended.SwitchNode;
import jdk.vm.ci.meta.Value;

/**
 * Delegates all operations to another {@link NodeLIRBuilderTool}. Subclasses can override individual
 * operations to customize its behavior.
 */
public class DelegatingNodeLIRBuilderTool implements NodeLIRBuilderTool {
    private final NodeLIRBuilderTool delegate;

    public DelegatingNodeLIRBuilderTool(NodeLIRBuilderTool delegate) {
        this.delegate = delegate;
    }

    @Override
    public Value operand(Node node) {
        return delegate.operand(node);
    }

    @Override
    public boolean hasOperand(Node node) {
        return delegate.hasOperand(node);
    }

    @Override
    public Value setResult(ValueNode node, Value operand) {
        return delegate.setResult(node, operand);
    }

    @Override
    public ValueNode valueForOperand(Value value) {
        return delegate.valueForOperand(value);
    }

    @Override
    public LIRFrameState state(DeoptimizingNode deopt) {
        return delegate.state(deopt);
    }

    @Override
    public void emitIf(IfNode node) {
        delegate.emitIf(node);
    }

    @Override
    public void emitConditional(ConditionalNode node) {
        delegate.emitConditional(node);
    }

    @Override
    public void emitSwitch(SwitchNode node) {
        delegate.emitSwitch(node);
    }

    @Override
    public void emitInvoke(Invoke node) {
        delegate.emitInvoke(node);
    }

    @Override
    public void emitForeignCall(ForeignCall node) {
        delegate.emitForeignCall(node);
    }

    @Override
    public void visitMerge(AbstractMergeNode node) {
        delegate.visitMerge(node);
    }

    @Override
    public void visitEndNode(AbstractEndNode node) {
        delegate.visitEndNode(node);
    }

    @Override
    public void visitLoopEnd(LoopEndNode node) {
        delegate.visitLoopEnd(node);
    }

    @Override
    public void visitSafepointNode(SafepointNode node) {
        delegate.visitSafepointNode(node);
    }

    @Override
    public void visitBreakpointNode(BreakpointNode node) {
        delegate.visitBreakpointNode(node);
    }

    @Override
    public void visitFullInfopointNode(FullInfopointNode node) {
        delegate.visitFullInfopointNode(node);
    }

    @Override
    public LIRGeneratorTool getLIRGeneratorTool() {
        return delegate.getLIRGeneratorTool();
    }

    @Override
    public boolean narrowsUseCastValue() {
        return delegate.narrowsUseCastValue();
    }

    @Override
    public void emitOverflowCheckBranch(AbstractBeginNode overflowSuccessor, AbstractBeginNode next, Stamp compareStamp, double probability) {
        delegate.emitOverflowCheckBranch(overflowSuccessor, next, compareStamp, probability);
    }

    @Override
    public void doBlock(HIRBlock block, StructuredGraph graph, BlockMap<List<Node>> blockMap) {
        delegate.doBlock(block, graph, blockMap);
    }

    @Override
    public void emitReadExceptionObject(ValueNode node) {
        delegate.emitReadExceptionObject(node);
    }

    @Override
    public ForeignCallLinkage lookupGraalStub(ValueNode node, ForeignCallDescriptor descriptor) {
        return delegate.lookupGraalStub(node, descriptor);
    }

    @Override
    public void emitStartRecordingThreadedSwitch() {
        delegate.emitStartRecordingThreadedSwitch();
    }
}
