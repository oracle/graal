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

package com.oracle.svm.hosted.webimage.wasm.codegen;

import static com.oracle.svm.hosted.webimage.wasm.ast.Instruction.Const;
import static com.oracle.svm.webimage.wasm.types.WasmPrimitiveType.f32;
import static com.oracle.svm.webimage.wasm.types.WasmPrimitiveType.f64;
import static com.oracle.svm.webimage.wasm.types.WasmPrimitiveType.i32;
import static com.oracle.svm.webimage.wasm.types.WasmPrimitiveType.i64;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import com.oracle.svm.core.graal.nodes.FloatingWordCastNode;
import com.oracle.svm.core.graal.nodes.ReadExceptionObjectNode;
import com.oracle.svm.core.meta.SubstrateMethodPointerConstant;
import com.oracle.svm.core.snippets.SnippetRuntime;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.webimage.codegen.WebImageJSNodeLowerer;
import com.oracle.svm.hosted.webimage.wasm.WasmImports;
import com.oracle.svm.hosted.webimage.wasm.WebImageWasmOptions;
import com.oracle.svm.hosted.webimage.wasm.ast.ImportDescriptor;
import com.oracle.svm.hosted.webimage.wasm.ast.Instruction;
import com.oracle.svm.hosted.webimage.wasm.ast.Instruction.Binary;
import com.oracle.svm.hosted.webimage.wasm.ast.Instruction.Call;
import com.oracle.svm.hosted.webimage.wasm.ast.Instruction.Nop;
import com.oracle.svm.hosted.webimage.wasm.ast.Instruction.Select;
import com.oracle.svm.hosted.webimage.wasm.ast.Instruction.Unary;
import com.oracle.svm.hosted.webimage.wasm.ast.Instructions;
import com.oracle.svm.hosted.webimage.wasm.ast.Literal;
import com.oracle.svm.hosted.webimage.wasm.ast.id.WasmId;
import com.oracle.svm.hosted.webimage.wasm.codegen.WasmIRWalker.Requirements;
import com.oracle.svm.hosted.webimage.wasm.nodes.WasmIsNonZeroNode;
import com.oracle.svm.hosted.webimage.wasm.nodes.WasmPopcntNode;
import com.oracle.svm.hosted.webimage.wasm.snippets.WasmImportForeignCallDescriptor;
import com.oracle.svm.webimage.hightiercodegen.NodeLowerer;
import com.oracle.svm.webimage.hightiercodegen.variables.ResolvedVar;
import com.oracle.svm.webimage.wasm.WasmForeignCallDescriptor;
import com.oracle.svm.webimage.wasm.types.WasmPrimitiveType;
import com.oracle.svm.webimage.wasm.types.WasmUtil;
import com.oracle.svm.webimage.wasm.types.WasmValType;

import jdk.graal.compiler.core.common.spi.ForeignCallDescriptor;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.iterators.NodeIterable;
import jdk.graal.compiler.lir.gen.ArithmeticLIRGeneratorTool;
import jdk.graal.compiler.nodeinfo.Verbosity;
import jdk.graal.compiler.nodes.AbstractEndNode;
import jdk.graal.compiler.nodes.CompressionNode;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.ControlSplitNode;
import jdk.graal.compiler.nodes.DeadEndNode;
import jdk.graal.compiler.nodes.EndNode;
import jdk.graal.compiler.nodes.FixedWithNextNode;
import jdk.graal.compiler.nodes.Invoke;
import jdk.graal.compiler.nodes.LogicNegationNode;
import jdk.graal.compiler.nodes.LogicNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ParameterNode;
import jdk.graal.compiler.nodes.PhiNode;
import jdk.graal.compiler.nodes.ReturnNode;
import jdk.graal.compiler.nodes.ShortCircuitOrNode;
import jdk.graal.compiler.nodes.UnwindNode;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.WithExceptionNode;
import jdk.graal.compiler.nodes.calc.AbsNode;
import jdk.graal.compiler.nodes.calc.AddNode;
import jdk.graal.compiler.nodes.calc.AndNode;
import jdk.graal.compiler.nodes.calc.BinaryArithmeticNode;
import jdk.graal.compiler.nodes.calc.BinaryNode;
import jdk.graal.compiler.nodes.calc.CompareNode;
import jdk.graal.compiler.nodes.calc.ConditionalNode;
import jdk.graal.compiler.nodes.calc.CopySignNode;
import jdk.graal.compiler.nodes.calc.FloatConvertNode;
import jdk.graal.compiler.nodes.calc.FloatDivNode;
import jdk.graal.compiler.nodes.calc.FloatingIntegerDivRemNode;
import jdk.graal.compiler.nodes.calc.FloatingNode;
import jdk.graal.compiler.nodes.calc.IntegerDivRemNode;
import jdk.graal.compiler.nodes.calc.IntegerTestNode;
import jdk.graal.compiler.nodes.calc.IsNullNode;
import jdk.graal.compiler.nodes.calc.LeftShiftNode;
import jdk.graal.compiler.nodes.calc.MaxNode;
import jdk.graal.compiler.nodes.calc.MinMaxNode;
import jdk.graal.compiler.nodes.calc.MinNode;
import jdk.graal.compiler.nodes.calc.MulNode;
import jdk.graal.compiler.nodes.calc.NarrowNode;
import jdk.graal.compiler.nodes.calc.NegateNode;
import jdk.graal.compiler.nodes.calc.NotNode;
import jdk.graal.compiler.nodes.calc.ObjectEqualsNode;
import jdk.graal.compiler.nodes.calc.OrNode;
import jdk.graal.compiler.nodes.calc.ReinterpretNode;
import jdk.graal.compiler.nodes.calc.RemNode;
import jdk.graal.compiler.nodes.calc.RightShiftNode;
import jdk.graal.compiler.nodes.calc.RoundNode;
import jdk.graal.compiler.nodes.calc.ShiftNode;
import jdk.graal.compiler.nodes.calc.SignExtendNode;
import jdk.graal.compiler.nodes.calc.SignedFloatingIntegerDivNode;
import jdk.graal.compiler.nodes.calc.SignedFloatingIntegerRemNode;
import jdk.graal.compiler.nodes.calc.SignumNode;
import jdk.graal.compiler.nodes.calc.SqrtNode;
import jdk.graal.compiler.nodes.calc.SubNode;
import jdk.graal.compiler.nodes.calc.UnaryNode;
import jdk.graal.compiler.nodes.calc.UnsignedRightShiftNode;
import jdk.graal.compiler.nodes.calc.XorNode;
import jdk.graal.compiler.nodes.calc.ZeroExtendNode;
import jdk.graal.compiler.nodes.debug.BlackholeNode;
import jdk.graal.compiler.nodes.extended.BoxNode;
import jdk.graal.compiler.nodes.extended.BytecodeExceptionNode;
import jdk.graal.compiler.nodes.extended.ForeignCall;
import jdk.graal.compiler.nodes.extended.GetClassNode;
import jdk.graal.compiler.nodes.extended.JavaReadNode;
import jdk.graal.compiler.nodes.extended.JavaWriteNode;
import jdk.graal.compiler.nodes.extended.LoadArrayComponentHubNode;
import jdk.graal.compiler.nodes.extended.LoadHubNode;
import jdk.graal.compiler.nodes.extended.ObjectIsArrayNode;
import jdk.graal.compiler.nodes.extended.RawLoadNode;
import jdk.graal.compiler.nodes.extended.RawStoreNode;
import jdk.graal.compiler.nodes.extended.StateSplitProxyNode;
import jdk.graal.compiler.nodes.extended.UnboxNode;
import jdk.graal.compiler.nodes.extended.UnsafeMemoryLoadNode;
import jdk.graal.compiler.nodes.extended.UnsafeMemoryStoreNode;
import jdk.graal.compiler.nodes.java.AbstractUnsafeCompareAndSwapNode;
import jdk.graal.compiler.nodes.java.ArrayLengthNode;
import jdk.graal.compiler.nodes.java.AtomicReadAndAddNode;
import jdk.graal.compiler.nodes.java.AtomicReadAndWriteNode;
import jdk.graal.compiler.nodes.java.ClassIsAssignableFromNode;
import jdk.graal.compiler.nodes.java.DynamicNewArrayNode;
import jdk.graal.compiler.nodes.java.DynamicNewInstanceNode;
import jdk.graal.compiler.nodes.java.ExceptionObjectNode;
import jdk.graal.compiler.nodes.java.InstanceOfDynamicNode;
import jdk.graal.compiler.nodes.java.InstanceOfNode;
import jdk.graal.compiler.nodes.java.LoadFieldNode;
import jdk.graal.compiler.nodes.java.LoadIndexedNode;
import jdk.graal.compiler.nodes.java.NewArrayNode;
import jdk.graal.compiler.nodes.java.NewInstanceNode;
import jdk.graal.compiler.nodes.java.NewMultiArrayNode;
import jdk.graal.compiler.nodes.java.ReachabilityFenceNode;
import jdk.graal.compiler.nodes.java.StoreFieldNode;
import jdk.graal.compiler.nodes.java.StoreIndexedNode;
import jdk.graal.compiler.nodes.memory.ReadNode;
import jdk.graal.compiler.replacements.nodes.ArrayEqualsNode;
import jdk.graal.compiler.replacements.nodes.ArrayFillNode;
import jdk.graal.compiler.replacements.nodes.AssertionNode;
import jdk.graal.compiler.replacements.nodes.BasicArrayCopyNode;
import jdk.graal.compiler.replacements.nodes.BinaryMathIntrinsicNode;
import jdk.graal.compiler.replacements.nodes.CountLeadingZerosNode;
import jdk.graal.compiler.replacements.nodes.CountTrailingZerosNode;
import jdk.graal.compiler.replacements.nodes.ObjectClone;
import jdk.graal.compiler.replacements.nodes.UnaryMathIntrinsicNode;
import jdk.graal.compiler.word.WordCastNode;
import jdk.vm.ci.code.CodeUtil;
import jdk.vm.ci.code.site.ConstantReference;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.PrimitiveConstant;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.VMConstant;

/**
 * Logic for generating Wasm instructions for {@link Node}s.
 * <p>
 * This base class handles codegen for backend-agnostic functionality (mostly operations on
 * primitive values, e.g. arithmetic). Subclasses have to implement codegen for backend-specific
 * functionality.
 * <p>
 * Does not implement the various {@code lower(...)} methods from {@link NodeLowerer} as they cannot
 * produce return values and the WASM backend requires node lowering methods to return an
 * {@link Instruction} instance to construct the instructions that use the node as an input.
 */
public abstract class WebImageWasmNodeLowerer extends NodeLowerer {

    protected final WasmCodeGenTool masm;

    /**
     * Temporary variable holding the exception object from a catch block.
     * <p>
     * In a WASM catch block, the thrown value was already placed onto the stack. In the Graal IR,
     * that value would be accessed using {@link ReadExceptionObjectNode}, however, that node may
     * not be scheduled in the catch block (but in a block that the catch block jumps to). Because
     * of that, in a catch block, the value on the stack is written to this variable.
     */
    protected final WasmId.Local exceptionObjectVariable;

    /**
     * Variable holding the stack pointer for the current function.
     * <p>
     * This is set when the function starts and is used to restore the stack pointer after an
     * exception is caught.
     * <p>
     * Is null if the stack pointer does not need to be preserved.
     */
    protected final WasmId.Local stackPointerHolder;

    protected final WasmUtil util;

    protected WebImageWasmNodeLowerer(WasmCodeGenTool codeGenTool) {
        super(codeGenTool);
        this.masm = codeGenTool;
        this.exceptionObjectVariable = masm.idFactory.newTemporaryVariable(codeGenTool.getWasmProviders().util().getThrowableType());
        // We only need to preserve the stack pointer if we catch exceptions
        if (masm.compilationResult.getGraph().getNodes().filter(WithExceptionNode.class).isEmpty()) {
            this.stackPointerHolder = null;
        } else {
            this.stackPointerHolder = masm.idFactory.newTemporaryVariable(masm.getKnownIds().stackPointer.getVariableType());
        }

        this.util = masm.wasmProviders.util();
    }

    /**
     * List of nodes which should never be attempted to be lowered directly.
     * <p>
     * If any of these appear in a regular call to {@link #dispatch(ValueNode, Requirements)} that
     * is a bug.
     */
    protected static final Set<Class<?>> FORBIDDEN_NODE_TYPES = new HashSet<>(Arrays.asList(
                    /*
                     * Handled in IR walker
                     */
                    AbstractEndNode.class,
                    /*
                     * Are always materialized as a variable read, writing to those variables is
                     * handled separately.
                     */
                    PhiNode.class));

    protected WasmCodeGenTool masm() {
        return masm;
    }

    /**
     * This method cannot be used. Use {@link #dispatch(ValueNode, Requirements)} instead.
     */
    @Override
    protected void dispatch(Node node) {
        throw GraalError.shouldNotReachHere("This method cannot be used in the WASM backend.");
    }

    public Collection<Class<?>> getIgnoredNodeTypes() {
        return WebImageJSNodeLowerer.IGNORED_NODE_TYPES;
    }

    @Override
    public boolean isIgnored(Node node) {
        for (Class<?> c : getIgnoredNodeTypes()) {
            if (c.isInstance(node)) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected boolean isForbiddenNode(Node node) {
        for (Class<?> c : FORBIDDEN_NODE_TYPES) {
            if (c.isInstance(node)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public NodeIterable<Node> actualUsages(ValueNode node) {
        return util.actualUsages(node);
    }

    @Override
    public boolean isActiveValueNode(ValueNode node) {
        return util.hasValue(node) && actualUsageCount(node) > 0;
    }

    /**
     * In WASM, a top-level statement is any instruction that appears directly inside a block and
     * not as an input to another instruction.
     */
    @Override
    public boolean isTopLevelStatement(Node node) {
        // Ignored node should never be emitted.
        if (isIgnored(node)) {
            return false;
        }

        // Nodes declared as variables are always a top-level statement (as the assignment to that
        // variables).
        if (masm.declared(node)) {
            return true;
        }

        // Value nodes producing an actual value must never be a top level statement in WASM
        if (util.hasValue(node)) {
            return false;
        }

        return true;
    }

    @Override
    public String nodeDebugInfo(Node node) {
        boolean isValueNode = node instanceof ValueNode;
        return node + ", " + node.usages() +
                        ", inlineable = " + (isValueNode && !masm.getVariableAllocation().needsVariable((ValueNode) node, masm)) +
                        (isValueNode ? ", safe inlining = " + masm.getVariableAllocation().getSafeInliningPolicies((ValueNode) node, masm) : "") +
                        (node instanceof FixedWithNextNode fixed ? ", next = " + fixed.next() : "") +
                        (node instanceof ControlSplitNode ? ", " + node.successors() : "");
    }

    @Override
    protected String reportForbiddenNode(Node node) {
        return nodeDebugInfo(node);
    }

    /**
     * Lowers the given node as a top-level statement (see
     * {@link WebImageWasmNodeLowerer#isTopLevelStatement(Node)}).
     * <p>
     * Nodes that are declared as variables are lowered as writing their value to that variable
     * (later lowerings of that node using {@link #lowerExpression(ValueNode)} will produce a
     * variable load). All other nodes are lowered directly into the current block.
     * <p>
     * Example:
     *
     * <pre>
     * {@code
     * {
     *     int a = foo() + bar();
     *     print(a);
     *     int b = a - 3;
     *     return a * b;
     * }
     * }
     * </pre>
     *
     * This java program is lowered to the following WASM code:
     *
     * <pre>
     * {@code
     * (local.set $l0 (call $foo))
     * (local.set $l1 (call $bar))
     * (local.set $a (i32.add (local.get $l0) (local.get $l1)))
     * (call $print (local.get $a))
     * (return
     *     (i32.mul
     *         (local.get $a)
     *         (i32.sub (local.get $a) (i32.const 3))
     *     )
     * )
     * }
     * </pre>
     *
     * Which nodes are first written to a variable is determined by
     * {@link WebImageWasmVariableAllocation}. In the example, the two method calls are written to a
     * variable because they could possibly have side effects. Variable {@code a} also uses a
     * variable in WASM because it has multiple usages. Everything else is inlined without the need
     * for new variables.
     */
    @Override
    public void lowerStatement(Node n) {
        if (masm.declared(n)) {
            ValueNode valueNode = (ValueNode) n;
            Instruction value = dispatch(valueNode, Requirements.defaults());
            ResolvedVar variable = lowerVariableStore(valueNode, value);
            variable.setDefinitionLowered();
        } else {
            Instruction stmt = lowerTopLevelStatement(n, Requirements.defaults());

            if (stmt == null) {
                /*
                 * If comments are generated, still emit a Nop to show the node in the text format.
                 */
                if (WebImageWasmOptions.genComments()) {
                    masm.genInst(new Nop(), n);
                }
            } else {
                masm.genInst(stmt, n);
            }
        }
    }

    /**
     * Generates code to store the given value in the variable allocated for the given node.
     *
     * @param n Node with associated variable
     * @param value Value to store in variable
     * @return The {@link ResolvedVar} for the given node
     */
    public ResolvedVar lowerVariableStore(ValueNode n, Instruction value) {
        assert masm.declared(n) : n;
        assert util.hasValue(n) : n;
        ResolvedVar variable = masm.getAllocatedVariable(n);
        WasmValType type = util.typeForNode(n);
        WasmId.Local local = masm.idFactory.forVariable(variable, type);

        masm.genInst(local.setter(value), n);

        return variable;
    }

    /**
     * Lowers the given node to an {@link Instruction} that produces no value.
     * <p>
     * This method is for nodes that do not produce a value in the WASM lowering.
     *
     * @return The {@link Instruction} for the node or null if the node does nothing.
     */
    protected abstract Instruction lowerTopLevelStatement(Node n, Requirements reqs);

    protected abstract Instruction lowerReturn(ReturnNode returnNode);

    protected Instruction lowerReachabilityFence(ReachabilityFenceNode n) {
        List<Instruction> values = new ArrayList<>(n.getValues().count());

        for (ValueNode value : n.getValues()) {
            Instruction inst = lowerDrop(value);

            if (inst != null) {
                values.add(inst);
            }
        }

        if (values.isEmpty()) {
            return null;
        } else if (values.size() == 1) {
            return values.get(0);
        } else {
            var block = new Instruction.Block(null);
            block.instructions.addAll(values);
            return block;
        }
    }

    /**
     * Lowers the given node and immediately drops it's produced value.
     * <p>
     * May return {@code null} when no drop is necessary (e.g. the node is side effect free).
     */
    protected Instruction lowerDrop(ValueNode value) {
        if (value instanceof FloatingNode) {
            // Floating nodes do not have side effects.
            return null;
        }
        return new Instruction.Drop(lowerExpression(value, Requirements.valueIgnored()));
    }

    protected static Instruction lowerAssert(AssertionNode n) {
        /*
         * The generate method on AssertionNode throws if it does not always evaluate to true and
         * does not use the NodeLIRBuilderTool, if this ever changes, the same checks just have to
         * be duplicated here.
         */
        n.generate(null);
        return null;
    }

    protected Instruction lowerExpression(ValueNode n) {
        return lowerExpression(n, Requirements.defaults());
    }

    protected Instruction lowerExpression(ValueNode n, Requirements reqs) {
        Objects.requireNonNull(n, "Tried to lower null Node");
        Instruction inst;
        ResolvedVar variable = masm.variableMap.getVarByNode(n);
        if (variable != null) {
            assert variable.isDefinitionLowered() : "Variable not yet defined: " + variable;
            inst = masm.idFactory.forVariable(variable, util.typeForNode(n)).getter();
        } else {
            inst = dispatch(n, reqs);
        }

        if (inst.getComment() == null) {
            inst.setComment(masm.getNodeComment(n));
        }
        return inst;
    }

    /**
     * Materialize the given node into a single {@link Instruction}, with possible nested inputs.
     */
    protected abstract Instruction dispatch(ValueNode n, WasmIRWalker.Requirements reqs);

    protected Instruction lowerParam(ParameterNode param) {
        return masm.compilationResult.getFunction().getParam(param.index()).getter();
    }

    /**
     * Creates a stub instruction that produces a value of the same type that the actual node would.
     * <p>
     * TODO GR-42437 temporary, just here so that we have instructions that produce the same value
     * as unimplemented JS nodes.
     */
    protected Instruction getStub(Node n) {
        Instruction inst = null;
        if (n instanceof ValueNode valueNode) {
            inst = getValueStub(valueNode);
        }

        if (inst == null) {
            inst = new Nop();
        }

        inst.setComment("unreachable: " + n);
        return inst;
    }

    protected Instruction getValueStub(ValueNode node) {
        JavaKind wasmKind = util.kindForNode(node);

        if (wasmKind == JavaKind.Void) {
            return null;
        }

        Literal literal = Literal.forConstant((PrimitiveConstant) JavaConstant.defaultForKind(wasmKind));
        return new Const(literal);
    }

    protected Instruction lowerConstant(ConstantNode n) {
        return lowerConstant(n.getValue());
    }

    protected Instruction lowerConstant(Constant constant) {
        if (constant instanceof PrimitiveConstant) {
            return Const.forConstant((PrimitiveConstant) constant);
        } else if (JavaConstant.isNull(constant)) {
            return Const.forInt(0);
        } else if (constant instanceof SubstrateMethodPointerConstant pointerConstant) {
            return masm.getRelocation(new ConstantReference(pointerConstant));
        } else if (constant instanceof VMConstant && constant instanceof JavaConstant) {
            return masm.getConstantRelocation((JavaConstant) constant);
        } else {
            throw GraalError.shouldNotReachHereUnexpectedValue(constant);
        }
    }

    protected Instruction lowerCompression(CompressionNode compression, Requirements reqs) {
        // Compression nodes are no-ops in Web Image
        assert !compression.getEncoding().hasBase() : compression.getEncoding();
        assert !compression.getEncoding().hasShift() : compression.getEncoding();
        return lowerExpression(compression.getValue(), reqs);
    }

    protected Instruction lowerSubstrateForeignCall(SnippetRuntime.SubstrateForeignCallDescriptor descriptor, Instruction... args) {
        return lowerSubstrateForeignCall(descriptor, Instructions.asInstructions(args));
    }

    protected Instruction lowerSubstrateForeignCall(SnippetRuntime.SubstrateForeignCallDescriptor descriptor, Instructions args) {
        ResolvedJavaMethod method = descriptor.findMethod(masm.getProviders().getMetaAccess());
        return masm.getCall(method, true, new Call(masm.idFactory.forMethod(method), args));
    }

    protected abstract Instruction lowerWasmImportForeignCall(WasmImportForeignCallDescriptor descriptor, Instructions args);

    protected Instruction lowerWasmForeignCall(WasmForeignCallDescriptor descriptor, @SuppressWarnings("unused") Instructions args) {
        throw GraalError.unimplemented("Unsupported WasmForeignCallDescriptor: " + descriptor);
    }

    protected Instruction lowerForeignCall(ForeignCall n) {
        Instructions args = new Instructions();
        n.getArguments().forEach(arg -> args.add(lowerExpression(arg)));

        ForeignCallDescriptor descriptor = n.getDescriptor();
        return switch (descriptor) {
            case SnippetRuntime.SubstrateForeignCallDescriptor substrateDescriptor -> lowerSubstrateForeignCall(substrateDescriptor, args);
            case WasmImportForeignCallDescriptor wasmImportForeignCallDescriptor -> lowerWasmImportForeignCall(wasmImportForeignCallDescriptor, args);
            case WasmForeignCallDescriptor wasmForeignCallDescriptor -> lowerWasmForeignCall(wasmForeignCallDescriptor, args);
            default -> throw VMError.shouldNotReachHereUnexpectedInput(descriptor);
        };
    }

    protected Instruction lowerDivRem(IntegerDivRemNode n) {
        return getDivRemOp(n).create(lowerExpression(n.getX()), lowerExpression(n.getY()));
    }

    private static Binary.Op getDivRemOp(IntegerDivRemNode n) {
        JavaKind kind = n.getStackKind();
        assert n.getX().getStackKind() == n.getY().getStackKind() : n.getX().getStackKind() + " != " + n.getY().getStackKind();
        assert kind == n.getX().getStackKind() : kind + " != " + n.getX().getStackKind();

        WasmPrimitiveType type = WasmUtil.mapPrimitiveType(kind);
        assert type.isInt() : type;

        boolean isI32 = type == i32;

        IntegerDivRemNode.Op op = n.getOp();
        IntegerDivRemNode.Type signed = n.getType();

        if (op == IntegerDivRemNode.Op.DIV && signed == IntegerDivRemNode.Type.SIGNED) {
            return isI32 ? Binary.Op.I32DivS : Binary.Op.I64DivS;
        } else if (op == IntegerDivRemNode.Op.DIV && signed == IntegerDivRemNode.Type.UNSIGNED) {
            return isI32 ? Binary.Op.I32DivU : Binary.Op.I64DivU;
        } else if (op == IntegerDivRemNode.Op.REM && signed == IntegerDivRemNode.Type.SIGNED) {
            return isI32 ? Binary.Op.I32RemS : Binary.Op.I64RemS;
        } else if (op == IntegerDivRemNode.Op.REM && signed == IntegerDivRemNode.Type.UNSIGNED) {
            return isI32 ? Binary.Op.I32RemU : Binary.Op.I64RemU;
        }

        throw GraalError.unimplemented("IntegerDivRem, op: " + op + ", type: " + type); // ExcludeFromJacocoGeneratedReport
    }

    protected Instruction lowerLogicNode(LogicNode n, Requirements reqs) {
        if (n instanceof IntegerTestNode integerTest) {
            return lowerIntegerTest(integerTest);
        } else if (n instanceof CompareNode compare) {
            return lowerCompareNode(compare);
        } else if (n instanceof IsNullNode isNull) {
            return lowerIsNull(isNull);
        } else if (n instanceof WasmIsNonZeroNode isNonZero) {
            return lowerIsNonZero(isNonZero, reqs);
        } else {
            throw GraalError.unimplemented(n.toString()); // ExcludeFromJacocoGeneratedReport
        }
    }

    protected abstract Instruction lowerIsNull(IsNullNode n);

    private Instruction lowerIsNonZero(WasmIsNonZeroNode n, Requirements reqs) {
        ValueNode value = n.getValue();
        assert util.mapType(value.getStackKind()) == WasmPrimitiveType.i32 : value.getStackKind();
        if (reqs.hasStrictLogic()) {
            return Binary.Op.I32Ne.create(lowerExpression(value), Const.forInt(0));
        } else {
            return lowerExpression(value, reqs);
        }
    }

    private Instruction lowerIntegerTest(IntegerTestNode n) {
        JavaKind kind = n.getX().getStackKind();
        assert kind == n.getY().getStackKind() : kind + " != " + n.getY().getStackKind();
        assert kind.isNumericInteger() : kind;
        Unary.Op eqzOp = kind == JavaKind.Int ? Unary.Op.I32Eqz : Unary.Op.I64Eqz;
        Binary.Op andOp = kind == JavaKind.Int ? Binary.Op.I32And : Binary.Op.I64And;

        return eqzOp.create(andOp.create(lowerExpression(n.getX()), lowerExpression(n.getY())));
    }

    protected Instruction lowerUnary(UnaryNode node) {
        assert node.getStackKind().isPrimitive() && node.getValue().getStackKind().isPrimitive() : "This method can only deal with primitive UnaryNodes";
        WasmPrimitiveType type = util.mapType(node.getStackKind()).asPrimitive();
        WasmPrimitiveType fromType = util.mapType(node.getValue().getStackKind()).asPrimitive();

        // Special cases for nodes that don't have a corresponding unary operation in WASM.
        if (node instanceof UnaryMathIntrinsicNode unaryMathIntrinsic) {
            return lowerUnaryMathIntrinsic(unaryMathIntrinsic);
        } else if (node instanceof NotNode) {
            assert type.isInt() : type;

            Binary.Op xorOp;
            Instruction mask;
            switch (type) {
                case i32 -> {
                    xorOp = Binary.Op.I32Xor;
                    mask = Const.forInt(-1);
                }
                case i64 -> {
                    xorOp = Binary.Op.I64Xor;
                    mask = Const.forLong(-1);
                }
                default -> throw GraalError.shouldNotReachHereUnexpectedValue(type);
            }

            // ~x is encoded as (x ^ -1)
            return xorOp.create(lowerExpression(node.getValue()), mask);
        } else if (node instanceof NegateNode && type.isInt()) {
            Binary.Op subOp;
            Instruction zero;
            switch (type) {
                case i32 -> {
                    subOp = Binary.Op.I32Sub;
                    zero = Const.forInt(0);
                }
                case i64 -> {
                    subOp = Binary.Op.I64Sub;
                    zero = Const.forLong(0);
                }
                default -> throw GraalError.shouldNotReachHereUnexpectedValue(type);
            }

            // -x is encoded as (0 - x)
            return subOp.create(zero, lowerExpression(node.getValue()));
        } else if (node instanceof NarrowNode narrow) {
            assert type.isInt() : type;
            int inputBits = narrow.getInputBits();
            int resultBits = narrow.getResultBits();

            Instruction input = lowerExpression(node.getValue());

            if (inputBits > 32) {
                assert resultBits <= 32 : resultBits;
                /*
                 * First convert the i64 to i32 and then potentially AND with a mask.
                 */
                Instruction wrap = Unary.Op.I32Wrap64.create(input);

                if (resultBits == 32) {
                    return wrap;
                } else {
                    return Binary.Op.I32And.create(wrap, Const.forInt((int) CodeUtil.mask(resultBits)));
                }
            } else {
                assert resultBits < 32 : resultBits;
                /*
                 * For narrowing within a 32-bit int, only masking is needed as they are all stored
                 * as i32.
                 */
                return Binary.Op.I32And.create(input, Const.forInt((int) CodeUtil.mask(resultBits)));
            }
        } else if (node instanceof SignExtendNode signExtend) {
            assert type.isInt() : type;
            int inputBits = signExtend.getInputBits();
            int resultBits = signExtend.getResultBits();

            Instruction input = lowerExpression(node.getValue());

            /*
             * WASM provides extensions to sign extend from 8, 16, and 32 bits. For 1 bit sign
             * extension a special case is needed. A 1-bit input can only have two values, selecting
             * the right zero-extended value based on that value is the best we can do.
             */
            if (inputBits == 1) {
                if (type == i32) {
                    return new Select(Const.forInt(-1), Const.forInt(0), input, i32);
                } else {
                    return new Select(Const.forLong(-1), Const.forLong(0), input, i64);
                }
            }

            /*
             * Holds the input value sign extended to 32-bits
             */
            Instruction i32Input = input;

            assert inputBits <= 32 : inputBits;
            if (inputBits < 32) {
                i32Input = switch (inputBits) {
                    case 8 -> Unary.Op.I32Extend8.create(input);
                    case 16 -> Unary.Op.I32Extend16.create(input);
                    default -> throw GraalError.unimplemented("Sign extend from " + inputBits + "bit to " + resultBits + "bit"); // ExcludeFromJacocoGeneratedReport
                };
            }

            if (resultBits > 32) {
                assert resultBits == 64 : resultBits;
                /*
                 * To go from i32 to i64, we can directly use the built-in sign-extension
                 * instruction.
                 */
                return Unary.Op.I64ExtendI32S.create(i32Input);
            } else {
                return i32Input;
            }
        } else if (node instanceof CountLeadingZerosNode && fromType == i64) {
            /*
             * The i64.clz instruction returns 64-bit, but the node has a 32-bit stamp. The result
             * is guaranteed to fit.
             */
            return Unary.Op.I32Wrap64.create(Unary.Op.I64Clz.create(lowerExpression(node.getValue())));
        } else if (node instanceof CountTrailingZerosNode && fromType == i64) {
            /*
             * The i64.ctz instruction returns 64-bit, but the node has a 32-bit stamp. The result
             * is guaranteed to fit.
             */
            return Unary.Op.I32Wrap64.create(Unary.Op.I64Ctz.create(lowerExpression(node.getValue())));
        }

        Unary.Op op = getUnaryOp(node);
        if (op == Unary.Op.Nop) {
            return lowerExpression(node.getValue());
        } else {
            return op.create(lowerExpression(node.getValue()));
        }
    }

    private static Unary.Op getUnaryOp(UnaryNode node) {
        WasmPrimitiveType type = WasmUtil.mapPrimitiveType(node.getStackKind());
        WasmPrimitiveType fromType = WasmUtil.mapPrimitiveType(node.getValue().getStackKind());
        if (node instanceof NegateNode) {
            assert type.isFloat() : type;
            return type == f32 ? Unary.Op.F32Neg : Unary.Op.F64Neg;
        } else if (node instanceof AbsNode) {
            assert type.isFloat() : type;
            return type == f32 ? Unary.Op.F32Abs : Unary.Op.F64Abs;
        } else if (node instanceof SqrtNode) {
            assert type.isFloat() : type;
            return type == f32 ? Unary.Op.F32Sqrt : Unary.Op.F64Sqrt;
        } else if (node instanceof ZeroExtendNode zeroExtend) {
            assert type.isInt() : type;
            boolean outIsI32 = type == i32;
            int inputBits = zeroExtend.getInputBits();
            int resultBits = zeroExtend.getResultBits();

            if (outIsI32) {
                assert inputBits < 32 : inputBits;
                assert resultBits <= 32 : resultBits;
                /*
                 * Zero extension from < 32-bit integer to a <= 32-bit integer requires no //
                 * operation since both are i32 in WASM.
                 */
                return Unary.Op.Nop;
            } else {
                /*
                 * Otherwise, the input is already represented as a zero-extended 32-bit int and
                 * only has to be extended to 64 bits.
                 */
                assert inputBits <= 32 : inputBits;
                assert resultBits == 64 : resultBits;
                return Unary.Op.I64ExtendI32U;
            }
        } else if (node instanceof RoundNode round) {
            assert type.isFloat() : type;
            boolean isF32 = type == f32;
            ArithmeticLIRGeneratorTool.RoundingMode mode = round.mode();

            return switch (mode) {
                case NEAREST -> isF32 ? Unary.Op.F32Nearest : Unary.Op.F64Nearest;
                case DOWN -> isF32 ? Unary.Op.F32Floor : Unary.Op.F64Floor;
                case UP -> isF32 ? Unary.Op.F32Ceil : Unary.Op.F64Ceil;
                case TRUNCATE -> isF32 ? Unary.Op.F32Trunc : Unary.Op.F64Trunc;
            };
        } else if (node instanceof WasmPopcntNode) {
            assert type.isInt() : type;
            return type == i32 ? Unary.Op.I32Popcnt : Unary.Op.I64Popcnt;
        } else if (node instanceof FloatConvertNode floatConvert) {
            JavaKind inputKind = floatConvert.getValue().getStackKind();

            return switch (floatConvert.getFloatConvert()) {
                case F2I -> {
                    assert inputKind == JavaKind.Float : inputKind;
                    assert type == i32 : type;
                    yield Unary.Op.I32TruncSatF32S;
                }
                case D2I -> {
                    assert inputKind == JavaKind.Double : inputKind;
                    assert type == i32 : type;
                    yield Unary.Op.I32TruncSatF64S;
                }
                case F2L -> {
                    assert inputKind == JavaKind.Float : inputKind;
                    assert type == i64 : type;
                    yield Unary.Op.I64TruncSatF32S;
                }
                case D2L -> {
                    assert inputKind == JavaKind.Double : inputKind;
                    assert type == i64 : type;
                    yield Unary.Op.I64TruncSatF64S;
                }
                case I2F -> {
                    assert inputKind == JavaKind.Int : inputKind;
                    assert type == f32 : type;
                    yield Unary.Op.F32ConvertI32S;
                }
                case L2F -> {
                    assert inputKind == JavaKind.Long : inputKind;
                    assert type == f32 : type;
                    yield Unary.Op.F32ConvertI64S;
                }
                case D2F -> {
                    assert inputKind == JavaKind.Double : inputKind;
                    assert type == f32 : type;
                    yield Unary.Op.F32Demote64;
                }
                case I2D -> {
                    assert inputKind == JavaKind.Int : inputKind;
                    assert type == f64 : type;
                    yield Unary.Op.F64ConvertI32S;
                }
                case L2D -> {
                    assert inputKind == JavaKind.Long : inputKind;
                    assert type == f64 : type;
                    yield Unary.Op.F64ConvertI64S;
                }
                case F2D -> {
                    assert inputKind == JavaKind.Float : inputKind;
                    assert type == f64 : type;
                    yield Unary.Op.F64Promote32;
                }
                default -> throw GraalError.unimplemented("FloatConvertNode, op: " + floatConvert.getFloatConvert()); // ExcludeFromJacocoGeneratedReport
            };
        } else if (node instanceof ReinterpretNode) {
            assert node.getStackKind().getBitCount() == node.getValue().getStackKind().getBitCount() : node.getStackKind().getBitCount() + " != " + node.getValue().getStackKind().getBitCount();

            if (fromType == f32 && type == i32) {
                return Unary.Op.I32ReinterpretF32;
            } else if (fromType == f64 && type == i64) {
                return Unary.Op.I64ReinterpretF64;
            } else if (fromType == i32 && type == f32) {
                return Unary.Op.F32ReinterpretI32;
            } else if (fromType == i64 && type == f64) {
                return Unary.Op.F64ReinterpretI64;
            } else {
                throw GraalError.unimplemented("ReinterpretNode from " + fromType + " to " + type); // ExcludeFromJacocoGeneratedReport
            }
        } else if (node instanceof CountLeadingZerosNode) {
            // 64-bit inputs are a special case
            assert type == i32 : type;
            assert fromType == i32 : fromType;
            return Unary.Op.I32Clz;
        } else if (node instanceof CountTrailingZerosNode) {
            // 64-bit inputs are a special case
            assert type == i32 : type;
            assert fromType == i32 : fromType;
            return Unary.Op.I32Ctz;
        }
        throw GraalError.unimplemented("Unary node: " + node); // ExcludeFromJacocoGeneratedReport
    }

    private Instruction lowerUnaryMathIntrinsic(UnaryMathIntrinsicNode node) {
        assert node.getStackKind() == JavaKind.Double : node.getStackKind();

        ImportDescriptor.Function imported = switch (node.getOperation()) {
            case LOG -> WasmImports.F64Log;
            case LOG10 -> WasmImports.F64Log10;
            case SIN -> WasmImports.F64Sin;
            case SINH -> WasmImports.F64Sinh;
            case COS -> WasmImports.F64Cos;
            case TAN -> WasmImports.F64Tan;
            case TANH -> WasmImports.F64Tanh;
            case EXP -> WasmImports.F64Exp;
            case CBRT -> WasmImports.F64Cbrt;
        };

        return new Call(masm.idFactory.forFunctionImport(imported), lowerExpression(node.getValue()));
    }

    protected Instruction lowerConditional(ConditionalNode n) {
        ValueNode trueValue = n.trueValue();
        ValueNode falseValue = n.falseValue();
        assert trueValue.getStackKind() == falseValue.getStackKind() : n;
        assert n.getStackKind() == trueValue.getStackKind() : n;

        if (n.getStackKind() == JavaKind.Int) {
            /*
             * If the conditional node is of the form Conditional(condition, 1, 0), we can simply
             * lower it as its condition since LogicNodes are guaranteed to be either 1 or 0.
             */
            PrimitiveConstant trueConstant = (PrimitiveConstant) trueValue.stamp(NodeView.DEFAULT).asConstant();
            PrimitiveConstant falseConstant = (PrimitiveConstant) falseValue.stamp(NodeView.DEFAULT).asConstant();

            if (trueConstant != null && falseConstant != null && trueConstant.asInt() == 1 && falseConstant.asInt() == 0) {
                return lowerExpression(n.condition());
            }
        }

        return new Select(lowerExpression(trueValue), lowerExpression(falseValue), lowerExpression(n.condition(), Requirements.defaults().setStrictLogic(false)),
                        util.mapType(n.getStackKind()));
    }

    protected Instruction lowerBinary(BinaryNode node) {
        // Special case. The floating point remainder operation doesn't exist in WASM.
        if (node instanceof RemNode) {
            WasmPrimitiveType type = WasmUtil.mapPrimitiveType(node.getStackKind());
            assert type.isFloat() : type;
            ImportDescriptor.Function func = type == f32 ? WasmImports.F32Rem : WasmImports.F64Rem;
            return new Call(masm.idFactory.forFunctionImport(func), lowerExpression(node.getX()), lowerExpression(node.getY()));
        } else if (node instanceof BinaryMathIntrinsicNode binaryMathIntrinsic) {
            return lowerBinaryMathIntrinsic(binaryMathIntrinsic);
        }

        Binary.Op op = getBinaryOp(node);
        assert op != null;
        Instruction opY = lowerExpression(node.getY());

        if (node instanceof ShiftNode && node.getStackKind() == JavaKind.Long) {
            /*
             * The shift amount for shift nodes is always i32 in the Graal IR, but WASM requires it
             * to match the first operand. We first have to extend the shift amount to 64 bits.
             */
            if (opY instanceof Const constant) {
                // Do the extension directly here
                Literal literal = constant.literal;
                assert literal.type == i32 : literal;
                opY = Const.forLong(literal.getI32());
            } else {
                opY = Unary.Op.I64ExtendI32S.create(opY);
            }
        }

        return op.create(lowerExpression(node.getX()), opY);
    }

    private static Binary.Op getBinaryOp(BinaryNode node) {
        WasmPrimitiveType type = WasmUtil.mapPrimitiveType(node.getStackKind());

        if (node instanceof AndNode) {
            assert type.isInt() : type;
            return type == i32 ? Binary.Op.I32And : Binary.Op.I64And;
        } else if (node instanceof OrNode) {
            assert type.isInt() : type;
            return type == i32 ? Binary.Op.I32Or : Binary.Op.I64Or;
        } else if (node instanceof XorNode) {
            assert type.isInt() : type;
            return type == i32 ? Binary.Op.I32Xor : Binary.Op.I64Xor;
        } else if (node instanceof AddNode) {
            return switch (type) {
                case i32 -> Binary.Op.I32Add;
                case i64 -> Binary.Op.I64Add;
                case f32 -> Binary.Op.F32Add;
                case f64 -> Binary.Op.F64Add;
            };
        } else if (node instanceof SubNode) {
            return switch (type) {
                case i32 -> Binary.Op.I32Sub;
                case i64 -> Binary.Op.I64Sub;
                case f32 -> Binary.Op.F32Sub;
                case f64 -> Binary.Op.F64Sub;
            };
        } else if (node instanceof MulNode) {
            return switch (type) {
                case i32 -> Binary.Op.I32Mul;
                case i64 -> Binary.Op.I64Mul;
                case f32 -> Binary.Op.F32Mul;
                case f64 -> Binary.Op.F64Mul;
            };
        } else if (node instanceof FloatDivNode) {
            assert type.isFloat() : type;
            return type == f32 ? Binary.Op.F32Div : Binary.Op.F64Div;
        } else if (node instanceof ShiftNode) {
            assert type.isInt() : type;
            boolean isInt32 = type == i32;

            if (node instanceof LeftShiftNode) {
                return isInt32 ? Binary.Op.I32Shl : Binary.Op.I64Shl;
            } else if (node instanceof RightShiftNode) {
                return isInt32 ? Binary.Op.I32ShrS : Binary.Op.I64ShrS;
            } else if (node instanceof UnsignedRightShiftNode) {
                return isInt32 ? Binary.Op.I32ShrU : Binary.Op.I64ShrU;
            }
        } else if (node instanceof CopySignNode) {
            assert type.isFloat() : type;
            return type == f32 ? Binary.Op.F32CopySign : Binary.Op.F64CopySign;
        } else if (node instanceof FloatingIntegerDivRemNode) {
            assert type.isInt() : type;
            boolean isI32 = type == i32;

            if (node instanceof SignedFloatingIntegerDivNode) {
                return isI32 ? Binary.Op.I32DivS : Binary.Op.I64DivS;
            } else if (node instanceof SignedFloatingIntegerRemNode) {
                return isI32 ? Binary.Op.I32RemS : Binary.Op.I64RemS;
            }
        } else if (node instanceof MinMaxNode<?>) {
            assert type.isFloat() : type;
            boolean isF32 = type == f32;

            if (node instanceof MaxNode) {
                return isF32 ? Binary.Op.F32Max : Binary.Op.F64Max;
            } else if (node instanceof MinNode) {
                return isF32 ? Binary.Op.F32Min : Binary.Op.F64Min;
            }
        }

        throw GraalError.unimplemented(node.toString(Verbosity.All)); // ExcludeFromJacocoGeneratedReport
    }

    private Instruction lowerBinaryMathIntrinsic(BinaryMathIntrinsicNode node) {
        assert node.getStackKind() == JavaKind.Double : node.getStackKind();

        ImportDescriptor.Function imported = switch (node.getOperation()) {
            case POW -> WasmImports.F64Pow;
        };

        return new Call(masm.idFactory.forFunctionImport(imported), lowerExpression(node.getX()), lowerExpression(node.getY()));
    }

    protected Instruction lowerCompareNode(CompareNode compare) {
        ValueNode left = compare.getX();
        JavaKind leftKind = left.getStackKind();
        ValueNode right = compare.getY();
        JavaKind rightKind = right.getStackKind();

        assert leftKind == rightKind : leftKind + " != " + rightKind;
        assert !compare.unorderedIsTrue() : "Any comparison with unorderedIsTrue should be have been replaced in UnorderedIsTruePhase";

        WasmValType type = util.mapType(leftKind.getStackKind());

        // Special case to lower `x == 0` to inn.Eqz
        if (type.isInt() && compare.isIdentityComparison()) {
            ValueNode nonZeroValue = null;
            if (left.isDefaultConstant()) {
                nonZeroValue = right;
            } else if (right.isDefaultConstant()) {
                nonZeroValue = left;
            }

            if (nonZeroValue != null) {
                return (type == i32 ? Unary.Op.I32Eqz : Unary.Op.I64Eqz).create(lowerExpression(nonZeroValue));
            }
        }

        Binary.Op op = switch (compare.condition()) {
            case EQ -> {
                if (type.isPrimitive()) {
                    yield switch (type.asPrimitive()) {
                        case i32 -> Binary.Op.I32Eq;
                        case i64 -> Binary.Op.I64Eq;
                        case f32 -> Binary.Op.F32Eq;
                        case f64 -> Binary.Op.F64Eq;
                    };
                } else if (type.isRef()) {
                    yield Binary.Op.RefEq;
                } else {
                    throw GraalError.shouldNotReachHereUnexpectedValue(type);
                }
            }
            case LT -> switch (type.asPrimitive()) {
                case i32 -> Binary.Op.I32LtS;
                case i64 -> Binary.Op.I64LtS;
                case f32 -> Binary.Op.F32Lt;
                case f64 -> Binary.Op.F64Lt;
            };
            case BT -> {
                assert type.isInt() : type;
                yield type == i32 ? Binary.Op.I32LtU : Binary.Op.I64LtU;
            }
        };

        return op.create(lowerExpression(left), lowerExpression(right));
    }

    protected Instruction lowerReadException(@SuppressWarnings("unused") ReadExceptionObjectNode n) {
        return exceptionObjectVariable.getter();
    }

    protected Instruction lowerWordCast(WordCastNode n) {
        return lowerWordCast(n, n.getInput());
    }

    protected Instruction lowerFloatingWordCast(FloatingWordCastNode n) {
        return lowerWordCast(n, n.getInput());
    }

    protected Instruction lowerWordCast(ValueNode castNode, ValueNode input) {
        Instruction value = lowerExpression(input);

        int inputBits = util.typeForNode(input).asPrimitive().getBitCount();
        int outputBits = util.typeForNode(castNode).asPrimitive().getBitCount();

        /*
         * TODO GR-42105 word types are 64-bit while objects are 32-bits. Add 32-bit architecture,
         * then we can probably save both the wrap and extend operations.
         */
        if (inputBits == outputBits) {
            return value;
        } else if (inputBits == 32 && outputBits == 64) {
            return Unary.Op.I64ExtendI32U.create(value);
        } else if (inputBits == 64 && outputBits == 32) {
            return Unary.Op.I32Wrap64.create(value);
        } else {
            throw GraalError.unimplemented(castNode + ", inputBits=" + inputBits + ", outputBits=" + outputBits); // ExcludeFromJacocoGeneratedReport
        }
    }

    // region Unsupported operations
    private static void genUnreachable(Object comment) {
        GraalError.shouldNotReachHere(String.valueOf(comment));
    }

    @Override
    protected void lowerVarDeclPrefix(ResolvedVar resolvedVar) {
        genUnreachable(resolvedVar);
    }

    @Override
    protected void lower(ResolvedVar resolvedVar) {
        genUnreachable(resolvedVar);
    }

    @Override
    protected void lower(BlackholeNode node) {
        genUnreachable(node);
    }

    @Override
    protected void lower(ReachabilityFenceNode node) {
        genUnreachable(node);
    }

    @Override
    protected void lower(UnsafeMemoryLoadNode node) {
        genUnreachable(node);
    }

    @Override
    protected void lower(UnsafeMemoryStoreNode node) {
        genUnreachable(node);
    }

    @Override
    protected void lower(BytecodeExceptionNode node) {
        genUnreachable(node);
    }

    @Override
    protected void lower(RoundNode node) {
        genUnreachable(node);
    }

    @Override
    protected void lower(SqrtNode node) {
        genUnreachable(node);
    }

    @Override
    protected void lower(AbsNode node) {
        genUnreachable(node);
    }

    @Override
    protected void lower(UnaryMathIntrinsicNode node) {
        genUnreachable(node);
    }

    @Override
    protected void lower(BinaryMathIntrinsicNode node) {
        genUnreachable(node);
    }

    @Override
    protected void lower(GetClassNode node) {
        genUnreachable(node);
    }

    @Override
    protected void lower(BinaryArithmeticNode<?> node) {
        genUnreachable(node);
    }

    @Override
    protected void lower(IntegerDivRemNode node) {
        genUnreachable(node);
    }

    @Override
    protected void lower(ForeignCall node) {
        genUnreachable(node);
    }

    @Override
    protected void lower(ObjectIsArrayNode node) {
        genUnreachable(node);
    }

    @Override
    protected void lower(DynamicNewArrayNode node) {
        genUnreachable(node);
    }

    @Override
    protected void lower(AtomicReadAndWriteNode node) {
        genUnreachable(node);
    }

    @Override
    protected void lower(AtomicReadAndAddNode node) {
        genUnreachable(node);
    }

    @Override
    protected void lower(AbstractUnsafeCompareAndSwapNode node) {
        genUnreachable(node);
    }

    @Override
    protected void lower(RawStoreNode node) {
        genUnreachable(node);
    }

    @Override
    protected void lower(RawLoadNode node) {
        genUnreachable(node);
    }

    @Override
    protected void lower(ReadNode node) {
        genUnreachable(node);
    }

    @Override
    protected void lower(JavaWriteNode node) {
        genUnreachable(node);
    }

    @Override
    protected void lower(JavaReadNode node) {
        genUnreachable(node);
    }

    @Override
    protected void lower(StateSplitProxyNode node) {
        genUnreachable(node);
    }

    @Override
    protected void lower(EndNode node) {
        genUnreachable(node);
    }

    @Override
    protected void lower(LoadArrayComponentHubNode node) {
        genUnreachable(node);
    }

    @Override
    protected void lower(LoadHubNode node) {
        genUnreachable(node);
    }

    @Override
    protected void lower(ObjectClone node) {
        genUnreachable(node);
    }

    @Override
    protected void lower(NewMultiArrayNode node) {
        genUnreachable(node);
    }

    @Override
    protected void lower(ArrayEqualsNode node) {
        genUnreachable(node);
    }

    @Override
    protected void lower(ArrayFillNode node) {
        genUnreachable(node);
    }

    @Override
    protected void lower(InstanceOfDynamicNode node) {
        genUnreachable(node);
    }

    @Override
    protected void lower(InstanceOfNode node) {
        genUnreachable(node);
    }

    @Override
    protected void lower(WordCastNode node) {
        genUnreachable(node);
    }

    @Override
    protected void lower(ShortCircuitOrNode node) {
        genUnreachable(node);
    }

    @Override
    protected void lower(StoreFieldNode node) {
        genUnreachable(node);
    }

    @Override
    protected void lower(ReinterpretNode node) {
        genUnreachable(node);
    }

    @Override
    protected void lower(UnboxNode node) {
        genUnreachable(node);
    }

    @Override
    protected void lower(SignumNode node) {
        genUnreachable(node);
    }

    @Override
    protected void lower(LoadFieldNode node) {
        genUnreachable(node);
    }

    @Override
    protected void lower(NewInstanceNode node) {
        genUnreachable(node);
    }

    @Override
    protected void lower(DynamicNewInstanceNode node) {
        genUnreachable(node);
    }

    @Override
    protected void lower(NotNode node) {
        genUnreachable(node);
    }

    @Override
    protected void lower(LogicNegationNode node) {
        genUnreachable(node);
    }

    @Override
    protected void lower(NarrowNode node) {
        genUnreachable(node);
    }

    @Override
    protected void lower(ZeroExtendNode node) {
        genUnreachable(node);
    }

    @Override
    protected void lower(SignExtendNode node) {
        genUnreachable(node);
    }

    @Override
    protected void lower(ShiftNode<?> node) {
        genUnreachable(node);
    }

    @Override
    protected void lower(DeadEndNode node) {
        genUnreachable(node);
    }

    @Override
    protected void lower(CompareNode node) {
        genUnreachable(node);
    }

    @Override
    protected void lower(ObjectEqualsNode node) {
        lower((CompareNode) node);
    }

    @Override
    protected void lower(ReturnNode node) {
        genUnreachable(node);
    }

    @Override
    protected void lower(IntegerTestNode node) {
        genUnreachable(node);
    }

    @Override
    protected void lower(BasicArrayCopyNode node) {
        genUnreachable(node);
    }

    @Override
    protected void lower(ArrayLengthNode node) {
        genUnreachable(node);
    }

    @Override
    protected void lower(FloatConvertNode node) {
        genUnreachable(node);
    }

    @Override
    protected void lower(BoxNode node) {
        genUnreachable(node);
    }

    @Override
    protected void lower(ConditionalNode node) {
        genUnreachable(node);
    }

    @Override
    protected void lower(ParameterNode node) {
        genUnreachable(node);
    }

    @Override
    protected void lower(StoreIndexedNode node) {
        genUnreachable(node);
    }

    @Override
    protected void lower(NewArrayNode node) {
        genUnreachable(node);
    }

    @Override
    protected void lower(LoadIndexedNode node) {
        genUnreachable(node);
    }

    @Override
    protected void lower(ConstantNode node) {
        genUnreachable(node);
    }

    @Override
    protected void lower(ExceptionObjectNode node) {
        genUnreachable(node);
    }

    @Override
    protected void lower(IsNullNode node) {
        genUnreachable(node);
    }

    @Override
    protected void lower(Invoke node) {
        genUnreachable(node);
    }

    @Override
    protected void lower(UnwindNode node) {
        genUnreachable(node);
    }

    @Override
    protected void lower(NegateNode node) {
        genUnreachable(node);
    }

    @Override
    protected void lower(ClassIsAssignableFromNode node) {
        genUnreachable(node);
    }
    // endregion
}
