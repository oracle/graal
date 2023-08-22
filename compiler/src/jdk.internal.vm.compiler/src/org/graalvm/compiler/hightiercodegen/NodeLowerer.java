/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.hightiercodegen;

import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.iterators.NodeIterable;
import org.graalvm.compiler.hightiercodegen.variables.ResolvedVar;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.DeadEndNode;
import org.graalvm.compiler.nodes.EndNode;
import org.graalvm.compiler.nodes.FixedNode;
import org.graalvm.compiler.nodes.GuardPhiNode;
import org.graalvm.compiler.nodes.Invoke;
import org.graalvm.compiler.nodes.InvokeNode;
import org.graalvm.compiler.nodes.InvokeWithExceptionNode;
import org.graalvm.compiler.nodes.LogicNegationNode;
import org.graalvm.compiler.nodes.ParameterNode;
import org.graalvm.compiler.nodes.PhiNode;
import org.graalvm.compiler.nodes.PiNode;
import org.graalvm.compiler.nodes.ReturnNode;
import org.graalvm.compiler.nodes.ShortCircuitOrNode;
import org.graalvm.compiler.nodes.UnwindNode;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.ValueProxyNode;
import org.graalvm.compiler.nodes.calc.AbsNode;
import org.graalvm.compiler.nodes.calc.BinaryArithmeticNode;
import org.graalvm.compiler.nodes.calc.CompareNode;
import org.graalvm.compiler.nodes.calc.ConditionalNode;
import org.graalvm.compiler.nodes.calc.FloatConvertNode;
import org.graalvm.compiler.nodes.calc.IntegerDivRemNode;
import org.graalvm.compiler.nodes.calc.IntegerTestNode;
import org.graalvm.compiler.nodes.calc.IsNullNode;
import org.graalvm.compiler.nodes.calc.NarrowNode;
import org.graalvm.compiler.nodes.calc.NegateNode;
import org.graalvm.compiler.nodes.calc.NotNode;
import org.graalvm.compiler.nodes.calc.ObjectEqualsNode;
import org.graalvm.compiler.nodes.calc.ReinterpretNode;
import org.graalvm.compiler.nodes.calc.RoundNode;
import org.graalvm.compiler.nodes.calc.ShiftNode;
import org.graalvm.compiler.nodes.calc.SignExtendNode;
import org.graalvm.compiler.nodes.calc.SignumNode;
import org.graalvm.compiler.nodes.calc.SqrtNode;
import org.graalvm.compiler.nodes.calc.ZeroExtendNode;
import org.graalvm.compiler.nodes.debug.BlackholeNode;
import org.graalvm.compiler.nodes.extended.BoxNode;
import org.graalvm.compiler.nodes.extended.BytecodeExceptionNode;
import org.graalvm.compiler.nodes.extended.ForeignCallNode;
import org.graalvm.compiler.nodes.extended.GetClassNode;
import org.graalvm.compiler.nodes.extended.JavaReadNode;
import org.graalvm.compiler.nodes.extended.JavaWriteNode;
import org.graalvm.compiler.nodes.extended.LoadArrayComponentHubNode;
import org.graalvm.compiler.nodes.extended.LoadHubNode;
import org.graalvm.compiler.nodes.extended.ObjectIsArrayNode;
import org.graalvm.compiler.nodes.extended.RawLoadNode;
import org.graalvm.compiler.nodes.extended.RawStoreNode;
import org.graalvm.compiler.nodes.extended.StateSplitProxyNode;
import org.graalvm.compiler.nodes.extended.UnboxNode;
import org.graalvm.compiler.nodes.extended.UnsafeMemoryLoadNode;
import org.graalvm.compiler.nodes.extended.UnsafeMemoryStoreNode;
import org.graalvm.compiler.nodes.java.AbstractUnsafeCompareAndSwapNode;
import org.graalvm.compiler.nodes.java.ArrayLengthNode;
import org.graalvm.compiler.nodes.java.AtomicReadAndAddNode;
import org.graalvm.compiler.nodes.java.AtomicReadAndWriteNode;
import org.graalvm.compiler.nodes.java.ClassIsAssignableFromNode;
import org.graalvm.compiler.nodes.java.DynamicNewArrayNode;
import org.graalvm.compiler.nodes.java.DynamicNewInstanceNode;
import org.graalvm.compiler.nodes.java.ExceptionObjectNode;
import org.graalvm.compiler.nodes.java.InstanceOfDynamicNode;
import org.graalvm.compiler.nodes.java.InstanceOfNode;
import org.graalvm.compiler.nodes.java.LoadFieldNode;
import org.graalvm.compiler.nodes.java.LoadIndexedNode;
import org.graalvm.compiler.nodes.java.NewArrayNode;
import org.graalvm.compiler.nodes.java.NewInstanceNode;
import org.graalvm.compiler.nodes.java.NewMultiArrayNode;
import org.graalvm.compiler.nodes.java.ReachabilityFenceNode;
import org.graalvm.compiler.nodes.java.StoreFieldNode;
import org.graalvm.compiler.nodes.java.StoreIndexedNode;
import org.graalvm.compiler.nodes.memory.ReadNode;
import org.graalvm.compiler.nodes.virtual.AllocatedObjectNode;
import org.graalvm.compiler.nodes.virtual.CommitAllocationNode;
import org.graalvm.compiler.nodes.virtual.VirtualObjectNode;
import org.graalvm.compiler.replacements.nodes.ArrayEqualsNode;
import org.graalvm.compiler.replacements.nodes.BasicArrayCopyNode;
import org.graalvm.compiler.replacements.nodes.BasicObjectCloneNode;
import org.graalvm.compiler.replacements.nodes.BinaryMathIntrinsicNode;
import org.graalvm.compiler.replacements.nodes.IdentityHashCodeNode;
import org.graalvm.compiler.replacements.nodes.UnaryMathIntrinsicNode;
import org.graalvm.compiler.word.WordCastNode;

/**
 * This class is responsible for generating code for individual {@link Node}s in the graph.
 */
public abstract class NodeLowerer {

    protected final CodeGenTool codeGenTool;

    public NodeLowerer(CodeGenTool codeGenTool) {
        this.codeGenTool = codeGenTool;
    }

    /**
     * Generates a top-level statement for the given node (including inputs of the node).
     *
     * For nodes that declare a variable, this generates a variable declaration + assignment.
     */
    public void lowerStatement(Node node) {
        if (codeGenTool.declared(node)) {
            ResolvedVar resolvedVar = codeGenTool.getAllocatedVariable((ValueNode) node);
            /*
             * Here we assume a value node is first visited in the scheduled order of basic blocks.
             * All nodes that depend on it come after in the schedule.
             */
            lowerVarDeclPrefix(resolvedVar);
        }

        dispatch(node);

        codeGenTool.genResolvedVarDeclPostfix(nodeDebugInfo(node));
    }

    /**
     * Generates the code for the declaration and assignment of a variable, but not the value it's
     * assigned to. Also sets the variable's definition as lowered if it wasn't already.
     */
    protected abstract void lowerVarDeclPrefix(ResolvedVar resolvedVar);

    /**
     * Generates code that represents the value of the given node.
     * <p>
     * This is either a variable read, if the node is allocated as a variable, or the
     * materialization of the node using {@link #dispatch(Node)}.
     * <p>
     * This assumes that allocated variables have been defined beforehand at some point using
     * {@link #lowerStatement(Node)}.
     */
    public final void lowerValue(ValueNode node) {
        assert isActiveValueNode(node) : "Attempted to lower " + node + " which is not an active value node";
        ResolvedVar resolvedVar = codeGenTool.getAllocatedVariable(node);
        if (resolvedVar != null) {
            assert resolvedVar.isDefinitionLowered() : "Variable definition for node " + node + " was not lowered before use";
            lower(resolvedVar);
        } else {
            dispatch(node);
        }
    }

    protected void lower(ResolvedVar resolvedVar) {
        codeGenTool.genResolvedVarAccess(resolvedVar.getName());
    }

    /**
     * Materialize the operation represented by the node without caring about inlining and reuse.
     *
     * The logic for handling inlining and reuse is located in the method
     * {@link #lowerValue(ValueNode)}.
     *
     * Lowering of the inputs of the node should call {@link #lowerValue(ValueNode)} instead of this
     * method.
     */
    protected void dispatch(Node node) {
        if (node instanceof CommitAllocationNode) {
            lower((CommitAllocationNode) node);
        } else if (node instanceof VirtualObjectNode) {
            lower((VirtualObjectNode) node);
        } else if (node instanceof AllocatedObjectNode) {
            lower((AllocatedObjectNode) node);
        } else if (node instanceof NegateNode) {
            lower((NegateNode) node);
        } else if (node instanceof UnwindNode) {
            lower((UnwindNode) node);
        } else if (node instanceof GuardPhiNode) {
            lowerValue(((GuardPhiNode) node).merge());
        } else if (node instanceof InvokeNode) {
            lower((InvokeNode) node);
        } else if (node instanceof InvokeWithExceptionNode) {
            lower((InvokeWithExceptionNode) node);
        } else if (node instanceof IsNullNode) {
            lower((IsNullNode) node);
        } else if (node instanceof ExceptionObjectNode) {
            lower((ExceptionObjectNode) node);
        } else if (node instanceof ValueProxyNode) {
            lowerValue(((ValueProxyNode) node).value());
        } else if (node instanceof ConstantNode) {
            lower((ConstantNode) node);
        } else if (node instanceof LoadIndexedNode) {
            lower((LoadIndexedNode) node);
        } else if (node instanceof NewArrayNode) {
            lower((NewArrayNode) node);
        } else if (node instanceof StoreIndexedNode) {
            lower((StoreIndexedNode) node);
        } else if (node instanceof ParameterNode) {
            lower((ParameterNode) node);
        } else if (node instanceof ConditionalNode) {
            lower((ConditionalNode) node);
        } else if (node instanceof BoxNode) {
            lower((BoxNode) node);
        } else if (node instanceof FloatConvertNode) {
            lower((FloatConvertNode) node);
        } else if (node instanceof ArrayLengthNode) {
            lower((ArrayLengthNode) node);
        } else if (node instanceof BasicArrayCopyNode) {
            lower((BasicArrayCopyNode) node);
        } else if (node instanceof IntegerTestNode) {
            lower((IntegerTestNode) node);
        } else if (node instanceof ReturnNode) {
            lower((ReturnNode) node);
        } else if (node instanceof ObjectEqualsNode) {
            // should be before the case for CompareNode
            lower((ObjectEqualsNode) node);
        } else if (node instanceof CompareNode) {
            lower((CompareNode) node);
        } else if (node instanceof DeadEndNode) {
            lower((DeadEndNode) node);
        } else if (node instanceof ShiftNode) {
            lower((ShiftNode<?>) node);
        } else if (node instanceof SignExtendNode) {
            lower((SignExtendNode) node);
        } else if (node instanceof ZeroExtendNode) {
            lower((ZeroExtendNode) node);
        } else if (node instanceof NarrowNode) {
            lower((NarrowNode) node);
        } else if (node instanceof NotNode) {
            lower((NotNode) node);
        } else if (node instanceof SignumNode) {
            lower((SignumNode) node);
        } else if (node instanceof NewInstanceNode) {
            lower((NewInstanceNode) node);
        } else if (node instanceof LoadFieldNode) {
            lower((LoadFieldNode) node);
        } else if (node instanceof UnboxNode) {
            lower((UnboxNode) node);
        } else if (node instanceof ReinterpretNode) {
            lower((ReinterpretNode) node);
        } else if (node instanceof StoreFieldNode) {
            lower((StoreFieldNode) node);
        } else if (node instanceof ShortCircuitOrNode) {
            lower((ShortCircuitOrNode) node);
        } else if (node instanceof LogicNegationNode) {
            lower((LogicNegationNode) node);
        } else if (node instanceof WordCastNode) {
            lower((WordCastNode) node);
        } else if (node instanceof InstanceOfNode) {
            lower((InstanceOfNode) node);
        } else if (node instanceof InstanceOfDynamicNode) {
            lower((InstanceOfDynamicNode) node);
        } else if (node instanceof ArrayEqualsNode) {
            lower((ArrayEqualsNode) node);
        } else if (node instanceof NewMultiArrayNode) {
            lower((NewMultiArrayNode) node);
        } else if (node instanceof BasicObjectCloneNode) {
            lower((BasicObjectCloneNode) node);
        } else if (node instanceof LoadHubNode) {
            lower((LoadHubNode) node);
        } else if (node instanceof LoadArrayComponentHubNode) {
            lower((LoadArrayComponentHubNode) node);
        } else if (node instanceof EndNode) {
            lower((EndNode) node);
        } else if (node instanceof StateSplitProxyNode) {
            lower((StateSplitProxyNode) node);
        } else if (node instanceof JavaReadNode) {
            lower((JavaReadNode) node);
        } else if (node instanceof JavaWriteNode) {
            lower((JavaWriteNode) node);
        } else if (node instanceof ReadNode) {
            lower((ReadNode) node);
        } else if (node instanceof RawLoadNode) {
            lower((RawLoadNode) node);
        } else if (node instanceof RawStoreNode) {
            lower((RawStoreNode) node);
        } else if (node instanceof AbstractUnsafeCompareAndSwapNode) {
            lower((AbstractUnsafeCompareAndSwapNode) node);
        } else if (node instanceof AtomicReadAndWriteNode) {
            lower((AtomicReadAndWriteNode) node);
        } else if (node instanceof AtomicReadAndAddNode) {
            lower((AtomicReadAndAddNode) node);
        } else if (node instanceof DynamicNewArrayNode) {
            lower((DynamicNewArrayNode) node);
        } else if (node instanceof ObjectIsArrayNode) {
            lower((ObjectIsArrayNode) node);
        } else if (node instanceof ForeignCallNode) {
            lower((ForeignCallNode) node);
        } else if (node instanceof IntegerDivRemNode) {
            lower((IntegerDivRemNode) node);
        } else if (node instanceof BinaryArithmeticNode) {
            lower((BinaryArithmeticNode<?>) node);
        } else if (node instanceof GetClassNode) {
            lower((GetClassNode) node);
        } else if (node instanceof PiNode) {
            lowerValue(((PiNode) node).getOriginalNode());
        } else if (node instanceof UnaryMathIntrinsicNode) {
            lower((UnaryMathIntrinsicNode) node);
        } else if (node instanceof BinaryMathIntrinsicNode) {
            lower((BinaryMathIntrinsicNode) node);
        } else if (node instanceof AbsNode) {
            lower((AbsNode) node);
        } else if (node instanceof SqrtNode) {
            lower((SqrtNode) node);
        } else if (node instanceof RoundNode) {
            lower((RoundNode) node);
        } else if (node instanceof BytecodeExceptionNode) {
            lower((BytecodeExceptionNode) node);
        } else if (node instanceof UnsafeMemoryStoreNode) {
            lower((UnsafeMemoryStoreNode) node);
        } else if (node instanceof UnsafeMemoryLoadNode) {
            lower((UnsafeMemoryLoadNode) node);
        } else if (node instanceof BlackholeNode) {
            lower((BlackholeNode) node);
        } else if (node instanceof ReachabilityFenceNode) {
            lower((ReachabilityFenceNode) node);
        } else if (node instanceof IdentityHashCodeNode) {
            lower((IdentityHashCodeNode) node);
        } else if (node instanceof ClassIsAssignableFromNode) {
            lower((ClassIsAssignableFromNode) node);
        } else if (node instanceof DynamicNewInstanceNode n) {
            lower(n);
        } else {
            if (!isIgnored(node)) {
                handleUnknownNodeType(node);
            }
        }
    }

    /** Should the node be ignored in lowering? */
    public abstract boolean isIgnored(Node node);

    protected abstract boolean isForbiddenNode(Node node);

    protected abstract String reportForbiddenNode(Node node);

    /**
     * Called when a node is encountered that is neither handled by {@link #dispatch}, forbidden or
     * ignored.
     */
    protected void handleUnknownNodeType(Node node) {
        throw GraalError.unimplemented("Could not lower node: " + node);
    }

    protected abstract void lower(BlackholeNode node);

    protected abstract void lower(ReachabilityFenceNode node);

    protected abstract void lower(UnsafeMemoryLoadNode node);

    protected abstract void lower(UnsafeMemoryStoreNode node);

    protected abstract void lower(BytecodeExceptionNode node);

    protected abstract void lower(RoundNode node);

    protected abstract void lower(SqrtNode node);

    protected abstract void lower(AbsNode node);

    protected abstract void lower(UnaryMathIntrinsicNode node);

    protected abstract void lower(BinaryMathIntrinsicNode node);

    protected abstract void lower(GetClassNode node);

    protected abstract void lower(BinaryArithmeticNode<?> node);

    protected abstract void lower(IntegerDivRemNode node);

    protected abstract void lower(ForeignCallNode node);

    protected abstract void lower(ObjectIsArrayNode node);

    protected abstract void lower(DynamicNewArrayNode node);

    protected abstract void lower(AtomicReadAndWriteNode node);

    protected abstract void lower(AtomicReadAndAddNode node);

    protected abstract void lower(AbstractUnsafeCompareAndSwapNode node);

    protected abstract void lower(RawStoreNode node);

    protected abstract void lower(RawLoadNode node);

    protected abstract void lower(ReadNode node);

    protected abstract void lower(JavaWriteNode node);

    protected abstract void lower(JavaReadNode node);

    protected abstract void lower(StateSplitProxyNode node);

    protected abstract void lower(EndNode node);

    protected abstract void lower(LoadArrayComponentHubNode node);

    protected abstract void lower(LoadHubNode node);

    protected abstract void lower(BasicObjectCloneNode node);

    protected abstract void lower(NewMultiArrayNode node);

    protected abstract void lower(ArrayEqualsNode node);

    protected abstract void lower(InstanceOfDynamicNode node);

    protected abstract void lower(InstanceOfNode node);

    protected abstract void lower(WordCastNode node);

    protected abstract void lower(ShortCircuitOrNode node);

    protected abstract void lower(LogicNegationNode node);

    protected abstract void lower(StoreFieldNode node);

    protected abstract void lower(ReinterpretNode node);

    protected abstract void lower(UnboxNode node);

    protected abstract void lower(SignumNode node);

    protected abstract void lower(LoadFieldNode node);

    protected abstract void lower(NewInstanceNode node);

    protected abstract void lower(DynamicNewInstanceNode node);

    protected abstract void lower(NotNode node);

    protected abstract void lower(NarrowNode node);

    protected abstract void lower(ZeroExtendNode node);

    protected abstract void lower(SignExtendNode node);

    protected abstract void lower(ShiftNode<?> node);

    protected abstract void lower(DeadEndNode node);

    protected abstract void lower(CompareNode node);

    protected abstract void lower(ObjectEqualsNode node);

    protected abstract void lower(ReturnNode node);

    protected abstract void lower(IntegerTestNode node);

    protected abstract void lower(BasicArrayCopyNode node);

    protected abstract void lower(ArrayLengthNode node);

    protected abstract void lower(FloatConvertNode node);

    protected abstract void lower(BoxNode node);

    protected abstract void lower(ConditionalNode node);

    protected abstract void lower(ParameterNode node);

    protected abstract void lower(StoreIndexedNode node);

    protected abstract void lower(NewArrayNode node);

    protected abstract void lower(LoadIndexedNode node);

    protected abstract void lower(ConstantNode node);

    protected abstract void lower(ExceptionObjectNode node);

    protected abstract void lower(IsNullNode node);

    protected abstract <T extends FixedNode & Invoke> void lower(T node);

    protected abstract void lower(UnwindNode node);

    protected abstract void lower(NegateNode node);

    protected abstract void lower(VirtualObjectNode node);

    protected abstract void lower(CommitAllocationNode node);

    protected abstract void lower(AllocatedObjectNode node);

    protected abstract void lower(IdentityHashCodeNode node);

    protected abstract void lower(ClassIsAssignableFromNode node);

    /**
     * Phi nodes are handled specially.
     *
     * The declarations are located at the beginning of a function before lowering.
     */
    public void lower(PhiNode node) {
        codeGenTool.genEmptyDeclaration(node);
    }

    /**
     * Return the actual usage count of a node.
     * <p>
     * This is supposed to be more precise than {@link Node#usages()}, as it ignores some spurious
     * usage. For example, a usage by a FrameState node can be ignored.
     */
    public abstract NodeIterable<Node> actualUsages(ValueNode node);

    /**
     * See {@link #actualUsages(ValueNode)}.
     */
    public int actualUsageCount(Node node) {
        if (node instanceof ValueNode) {
            return actualUsages((ValueNode) node).count();
        } else {
            return 0;
        }
    }

    /** Does the node produce a value that is used by some other nodes? */
    public abstract boolean isActiveValueNode(ValueNode node);

    /**
     * Should the scheduler issue the Node from the basic block schedule to {@link NodeLowerer}?
     *
     * This should only return true for nodes that should be materialized as a top-level statement.
     * Inlined nodes, ignored nodes, and some specially-handled nodes should not be issued.
     *
     * Note: non-top-level-statement nodes are intended to be lowered by {@link NodeLowerer} while
     * lowering the nodes that use them as inputs.
     */
    public abstract boolean isTopLevelStatement(Node node);

    public abstract String nodeDebugInfo(Node node);
}
