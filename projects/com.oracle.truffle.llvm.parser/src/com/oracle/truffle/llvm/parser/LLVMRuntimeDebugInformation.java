/*
 * Copyright (c) 2017, Oracle and/or its affiliates.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to
 * endorse or promote products derived from this software without specific prior written
 * permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.oracle.truffle.llvm.parser;

import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.FrameSlotKind;
import com.oracle.truffle.llvm.parser.metadata.DwarfOpcode;
import com.oracle.truffle.llvm.parser.metadata.MDExpression;
import com.oracle.truffle.llvm.parser.metadata.MetadataSymbol;
import com.oracle.truffle.llvm.parser.metadata.debuginfo.SourceModel;
import com.oracle.truffle.llvm.parser.metadata.debuginfo.SourceModel.Variable;
import com.oracle.truffle.llvm.parser.metadata.debuginfo.ValueFragment;
import com.oracle.truffle.llvm.parser.model.SymbolImpl;
import com.oracle.truffle.llvm.parser.model.blocks.InstructionBlock;
import com.oracle.truffle.llvm.parser.model.functions.FunctionDeclaration;
import com.oracle.truffle.llvm.parser.model.functions.FunctionDefinition;
import com.oracle.truffle.llvm.parser.model.functions.FunctionParameter;
import com.oracle.truffle.llvm.parser.model.symbols.constants.NullConstant;
import com.oracle.truffle.llvm.parser.model.symbols.constants.UndefinedConstant;
import com.oracle.truffle.llvm.parser.model.symbols.constants.floatingpoint.DoubleConstant;
import com.oracle.truffle.llvm.parser.model.symbols.constants.floatingpoint.FloatConstant;
import com.oracle.truffle.llvm.parser.model.symbols.constants.floatingpoint.X86FP80Constant;
import com.oracle.truffle.llvm.parser.model.symbols.constants.integer.BigIntegerConstant;
import com.oracle.truffle.llvm.parser.model.symbols.constants.integer.IntegerConstant;
import com.oracle.truffle.llvm.parser.model.symbols.globals.GlobalConstant;
import com.oracle.truffle.llvm.parser.model.symbols.globals.GlobalValueSymbol;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.AllocateInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.Call;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.ValueInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.VoidCallInstruction;
import com.oracle.truffle.llvm.parser.model.visitors.FunctionVisitor;
import com.oracle.truffle.llvm.parser.model.visitors.InstructionVisitorAdapter;
import com.oracle.truffle.llvm.parser.model.visitors.ValueInstructionVisitor;
import com.oracle.truffle.llvm.parser.nodes.LLVMSymbolReadResolver;
import com.oracle.truffle.llvm.runtime.LLVMContext;
import com.oracle.truffle.llvm.runtime.debug.LLVMDebugValue;
import com.oracle.truffle.llvm.runtime.debug.LLVMSourceSymbol;
import com.oracle.truffle.llvm.runtime.debug.scope.LLVMFrameValueAccess;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.options.SulongEngineOption;
import com.oracle.truffle.llvm.runtime.types.FunctionType;
import com.oracle.truffle.llvm.runtime.types.MetaType;
import com.oracle.truffle.llvm.runtime.types.PointerType;
import com.oracle.truffle.llvm.runtime.types.PrimitiveType;

import java.util.ArrayList;
import java.util.List;

final class LLVMRuntimeDebugInformation {

    private final FrameDescriptor frame;
    private final NodeFactory factory;
    private final LLVMContext context;
    private final List<FrameSlot> notNullableSlots;
    private final LLVMSymbolReadResolver symbols;
    private final LLVMParserRuntime runtime;
    private final boolean isEnabled;
    private final StaticValueAccessVisitor staticValueAccessVisitor;

    LLVMRuntimeDebugInformation(FrameDescriptor frame, NodeFactory factory, LLVMContext context, List<FrameSlot> notNullableSlots, LLVMSymbolReadResolver symbols, LLVMParserRuntime runtime) {
        this.frame = frame;
        this.factory = factory;
        this.context = context;
        this.notNullableSlots = notNullableSlots;
        this.symbols = symbols;
        this.runtime = runtime;
        this.isEnabled = context.getEnv().getOptions().get(SulongEngineOption.ENABLE_LVI);
        this.staticValueAccessVisitor = new StaticValueAccessVisitor();
    }

    private final class StaticValueAccessVisitor extends ValueInstructionVisitor {

        private Variable variable = null;
        private LLVMSourceSymbol symbol = null;
        private boolean isDeclaration = false;

        void registerStaticAccess(Variable descriptor, SymbolImpl value, boolean mustDereference) {
            this.variable = descriptor;
            this.symbol = variable.getSymbol();
            this.isDeclaration = mustDereference;

            value.accept(this);

            this.variable = null;
            this.symbol = null;
            this.isDeclaration = false;
        }

        private void visitFrameValue(String name) {
            final FrameSlot slot = frame.findFrameSlot(name);
            assert slot != null;
            final LLVMFrameValueAccess valueAccess = factory.createDebugFrameValue(slot, isDeclaration);
            context.getSourceContext().registerFrameValue(symbol, valueAccess);
            notNullableSlots.add(slot);
            variable.addStaticValue();
        }

        private void visitSimpleConstant(SymbolImpl constant) {
            final LLVMExpressionNode node = symbols.resolve(constant);
            assert node != null;
            final LLVMDebugValue value = factory.createDebugStaticValue(node);
            context.getSourceContext().registerStatic(symbol, value);
            variable.addStaticValue();
        }

        @Override
        public void visitValueInstruction(ValueInstruction inst) {
            visitFrameValue(inst.getName());
        }

        @Override
        public void visit(FunctionParameter param) {
            visitFrameValue(param.getName());
        }

        @Override
        public void visit(IntegerConstant constant) {
            visitSimpleConstant(constant);
        }

        @Override
        public void visit(BigIntegerConstant constant) {
            visitSimpleConstant(constant);
        }

        @Override
        public void visit(DoubleConstant constant) {
            visitSimpleConstant(constant);
        }

        @Override
        public void visit(FloatConstant constant) {
            visitSimpleConstant(constant);
        }

        @Override
        public void visit(X86FP80Constant constant) {
            visitSimpleConstant(constant);
        }

        @Override
        public void visit(GlobalConstant constant) {
            visitSimpleConstant(constant);
        }

        @Override
        public void visit(NullConstant constant) {
            if (constant.getType() instanceof PrimitiveType || constant.getType() instanceof PointerType || constant.getType() instanceof FunctionType) {
                visitSimpleConstant(constant);
            }
        }
    }

    public boolean isEnabled() {
        return isEnabled;
    }

    private final class NotNullableVisitor implements InstructionVisitorAdapter, FunctionVisitor {

        @Override
        public void visit(InstructionBlock block) {
            block.accept(this);
        }

        @Override
        public void visit(VoidCallInstruction call) {
            final FunctionDeclaration decl;
            if (call.getCallTarget() instanceof FunctionDeclaration) {
                decl = (FunctionDeclaration) call.getCallTarget();
            } else {
                return;
            }

            if (SourceModel.LLVM_DBG_DECLARE_NAME.equals(decl.getName()) && call.getArgumentCount() >= SourceModel.LLVM_DBG_DECLARE_ARGSIZE) {

                FrameSlot frameSlot = null;
                final SymbolImpl value = call.getArgument(SourceModel.LLVM_DBG_INTRINSICS_VALUE_ARGINDEX);
                if (value instanceof AllocateInstruction) {
                    frameSlot = frame.findFrameSlot(((AllocateInstruction) value).getName());
                }

                if (frameSlot == null) {
                    return;
                }

                final SymbolImpl varDefSymbol = call.getArgument(SourceModel.LLVM_DBG_DECLARE_LOCALREF_ARGINDEX);
                if (varDefSymbol instanceof Variable && ((Variable) varDefSymbol).isSingleDeclaration()) {
                    final LLVMSourceSymbol symbol = ((Variable) varDefSymbol).getSymbol();
                    final LLVMFrameValueAccess alloc = factory.createDebugFrameValue(frameSlot, true);
                    notNullableSlots.add(frameSlot);
                    context.getSourceContext().registerFrameValue(symbol, alloc);
                    ((Variable) varDefSymbol).addStaticValue();
                }

            } else if (SourceModel.LLVM_DBG_VALUE_NAME.equals(decl.getName()) && call.getArgumentCount() >= SourceModel.LLVM_DBG_VALUE_ARGSIZE) {

                final SymbolImpl varDefSymbol = call.getArgument(SourceModel.LLVM_DBG_VALUE_LOCALREF_ARGINDEX);
                if (varDefSymbol instanceof Variable && ((Variable) varDefSymbol).isSingleValue()) {

                    boolean mustDereference = false;

                    final SymbolImpl exprSymbol = call.getArgument(SourceModel.LLVM_DBG_VALUE_EXPR_ARGINDEX);
                    if (exprSymbol instanceof MetadataSymbol && ((MetadataSymbol) exprSymbol).getNode() instanceof MDExpression) {
                        final MDExpression expression = (MDExpression) ((MetadataSymbol) exprSymbol).getNode();
                        if (DwarfOpcode.isDeref(expression)) {
                            mustDereference = true;

                        } else if (expression.getElementCount() != 0) {
                            return;
                        }
                    }

                    final SymbolImpl value = call.getArgument(SourceModel.LLVM_DBG_INTRINSICS_VALUE_ARGINDEX);
                    staticValueAccessVisitor.registerStaticAccess(((Variable) varDefSymbol), value, mustDereference);
                }
            }
        }
    }

    void registerStaticDebugSymbols(FunctionDefinition fn) {
        if (isEnabled) {
            fn.accept((FunctionVisitor) new NotNullableVisitor());
        }
    }

    LLVMExpressionNode createInitializer(Variable variable) {
        if (!isEnabled) {
            return null;
        }

        final FrameSlot targetSlot = frame.findOrAddFrameSlot(variable.getSymbol(), MetaType.DEBUG, FrameSlotKind.Object);

        int[] offsets = null;
        int[] lengths = null;

        if (variable.hasFragments()) {
            final List<ValueFragment> fragments = variable.getFragments();
            offsets = new int[fragments.size()];
            lengths = new int[fragments.size()];

            for (int i = 0; i < fragments.size(); i++) {
                final ValueFragment fragment = fragments.get(i);
                offsets[i] = fragment.getOffset();
                lengths[i] = fragment.getLength();
            }
        }

        return factory.createDebugInit(targetSlot, offsets, lengths);
    }

    private static final int LLVM_DBG_VALUE_OFFSET_INDEX = 1;
    private static final int[] CLEAR_NONE = new int[0];

    LLVMExpressionNode handleDebugIntrinsic(SymbolImpl valueSymbol, boolean isDeclaration, Call call, Variable var) {
        if (!isEnabled || var.hasStaticAllocation()) {
            return null;
        }

        LLVMExpressionNode valueRead = null;
        int exprIndex;
        if (isDeclaration) {
            if (valueSymbol instanceof UndefinedConstant) {
                if (var.hasValue()) {
                    // this declaration only tells us that the variable is not in memory, we already
                    // know this from the presence of the value
                    return null;
                }
                valueRead = symbols.resolve(new NullConstant(MetaType.DEBUG));

            } else if (valueSymbol instanceof NullConstant) {
                valueRead = symbols.resolve(new NullConstant(MetaType.DEBUG));

            } else if (valueSymbol instanceof GlobalValueSymbol || valueSymbol.getType() instanceof PointerType) {
                valueRead = symbols.resolve(valueSymbol);
            }

            exprIndex = SourceModel.LLVM_DBG_DECLARE_EXPR_ARGINDEX;

        } else {
            final SymbolImpl valueOffsetSymbol = call.getArgument(LLVM_DBG_VALUE_OFFSET_INDEX);
            final Integer valueOffset = LLVMSymbolReadResolver.evaluateIntegerConstant(valueOffsetSymbol);
            valueRead = symbols.resolve(valueSymbol);
            exprIndex = SourceModel.LLVM_DBG_VALUE_EXPR_ARGINDEX;

            if (valueOffset != null && valueOffset != 0) {
                // this is unsupported, it doesn't appear in LLVM 3.8+
                return null;
            }
        }

        if (valueRead == null) {
            return null;
        }

        boolean mustDereference = isDeclaration;
        int partIndex = -1;
        int[] clearParts = null;

        final SymbolImpl exprSymbol = call.getArgument(exprIndex);
        if (exprSymbol instanceof MetadataSymbol && ((MetadataSymbol) exprSymbol).getNode() instanceof MDExpression) {
            final MDExpression expression = (MDExpression) ((MetadataSymbol) exprSymbol).getNode();
            if (ValueFragment.describesFragment(expression)) {
                final ValueFragment fragment = ValueFragment.parse(expression);
                final List<ValueFragment> siblings = var.getFragments();
                final List<Integer> clearSiblings = new ArrayList<>(siblings.size());
                partIndex = ValueFragment.getPartIndex(fragment, siblings, clearSiblings);
                if (clearSiblings.isEmpty()) {
                    // this will be the case most of the time
                    clearParts = CLEAR_NONE;
                } else {
                    clearParts = clearSiblings.stream().mapToInt(Integer::intValue).toArray();
                }
            }
            if (DwarfOpcode.isDeref(expression)) {
                mustDereference = true;
            }
        }

        if (partIndex < 0 && var.hasFragments()) {
            partIndex = var.getFragmentIndex(0, (int) var.getSymbol().getType().getSize());
            if (partIndex < 0) {
                throw new IllegalStateException("Cannot find index of value fragment!");
            }

            clearParts = new int[var.getFragments().size() - 1];
            for (int i = 0; i < partIndex; i++) {
                clearParts[i] = i;
            }
            for (int i = partIndex; i < clearParts.length; i++) {
                clearParts[i] = i + 1;
            }
        }

        final FrameSlot targetSlot = frame.findOrAddFrameSlot(var.getSymbol(), MetaType.DEBUG, FrameSlotKind.Object);
        final LLVMExpressionNode containerRead = factory.createFrameRead(runtime, MetaType.DEBUG, targetSlot);
        return factory.createDebugWrite(mustDereference, valueRead, targetSlot, containerRead, partIndex, clearParts);
    }

}
