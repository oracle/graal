/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates.
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

import java.util.ArrayList;
import java.util.List;

import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.FrameSlotKind;
import com.oracle.truffle.llvm.parser.metadata.DwarfOpcode;
import com.oracle.truffle.llvm.parser.metadata.MDExpression;
import com.oracle.truffle.llvm.parser.metadata.debuginfo.SourceVariable;
import com.oracle.truffle.llvm.parser.metadata.debuginfo.ValueFragment;
import com.oracle.truffle.llvm.parser.model.SymbolImpl;
import com.oracle.truffle.llvm.parser.model.functions.FunctionDefinition;
import com.oracle.truffle.llvm.parser.model.functions.FunctionParameter;
import com.oracle.truffle.llvm.parser.model.symbols.constants.NullConstant;
import com.oracle.truffle.llvm.parser.model.symbols.constants.UndefinedConstant;
import com.oracle.truffle.llvm.parser.model.symbols.constants.floatingpoint.DoubleConstant;
import com.oracle.truffle.llvm.parser.model.symbols.constants.floatingpoint.FloatConstant;
import com.oracle.truffle.llvm.parser.model.symbols.constants.floatingpoint.X86FP80Constant;
import com.oracle.truffle.llvm.parser.model.symbols.constants.integer.BigIntegerConstant;
import com.oracle.truffle.llvm.parser.model.symbols.constants.integer.IntegerConstant;
import com.oracle.truffle.llvm.parser.model.symbols.globals.GlobalValueSymbol;
import com.oracle.truffle.llvm.parser.model.symbols.globals.GlobalVariable;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.AllocateInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.DbgDeclareInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.DbgValueInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.ValueInstruction;
import com.oracle.truffle.llvm.parser.model.visitors.ValueInstructionVisitor;
import com.oracle.truffle.llvm.parser.nodes.LLVMSymbolReadResolver;
import com.oracle.truffle.llvm.runtime.LLVMContext;
import com.oracle.truffle.llvm.runtime.debug.scope.LLVMSourceSymbol;
import com.oracle.truffle.llvm.runtime.debug.type.LLVMSourceType;
import com.oracle.truffle.llvm.runtime.debug.value.LLVMDebugObjectBuilder;
import com.oracle.truffle.llvm.runtime.debug.value.LLVMFrameValueAccess;
import com.oracle.truffle.llvm.runtime.except.LLVMParserException;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMStatementNode;
import com.oracle.truffle.llvm.runtime.options.SulongEngineOption;
import com.oracle.truffle.llvm.runtime.types.FunctionType;
import com.oracle.truffle.llvm.runtime.types.MetaType;
import com.oracle.truffle.llvm.runtime.types.PointerType;
import com.oracle.truffle.llvm.runtime.types.PrimitiveType;

final class LLVMRuntimeDebugInformation {

    private final FrameDescriptor frame;
    private final LLVMContext context;
    private final List<FrameSlot> notNullableSlots;
    private final LLVMSymbolReadResolver symbols;
    private final boolean isEnabled;
    private final StaticValueAccessVisitor staticValueAccessVisitor;

    LLVMRuntimeDebugInformation(FrameDescriptor frame, LLVMContext context, List<FrameSlot> notNullableSlots, LLVMSymbolReadResolver symbols) {
        this.frame = frame;
        this.context = context;
        this.notNullableSlots = notNullableSlots;
        this.symbols = symbols;
        this.isEnabled = context.getEnv().getOptions().get(SulongEngineOption.ENABLE_LVI);
        this.staticValueAccessVisitor = new StaticValueAccessVisitor();
    }

    private final class StaticValueAccessVisitor extends ValueInstructionVisitor {

        private SourceVariable variable = null;
        private LLVMSourceSymbol symbol = null;
        private boolean isDeclaration = false;

        void registerStaticAccess(SourceVariable descriptor, SymbolImpl value, boolean mustDereference) {
            this.variable = descriptor;
            this.symbol = variable.getSymbol();
            this.isDeclaration = mustDereference;

            if (value != null) {
                value.accept(this);
            }

            this.variable = null;
            this.symbol = null;
            this.isDeclaration = false;
        }

        private void visitFrameValue(String name) {
            final FrameSlot slot = frame.findFrameSlot(name);
            assert slot != null;
            final LLVMFrameValueAccess valueAccess = context.getLanguage().getNodeFactory().createDebugFrameValue(slot, isDeclaration);
            context.getSourceContext().registerFrameValue(symbol, valueAccess);
            notNullableSlots.add(slot);
            variable.addStaticValue();
        }

        private void visitSimpleConstant(SymbolImpl constant) {
            final LLVMExpressionNode node = symbols.resolve(constant);
            assert node != null;
            final LLVMDebugObjectBuilder value = context.getLanguage().getNodeFactory().createDebugStaticValue(node, false);
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
        public void visit(GlobalVariable global) {
            if (global.isReadOnly()) {
                visitSimpleConstant(global);
            } else {
                super.visit(global);
            }
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

    private static boolean mustDereferenceValue(MDExpression expr, LLVMSourceType type, SymbolImpl value) {
        // sometimes at O1+ llvm drops a dbg.declare to a dbg.value without adding a Dwarf.DEREF to
        // it
        return DwarfOpcode.isDeref(expr) || (type != null && !type.isPointer() && value instanceof AllocateInstruction);
    }

    void registerStaticDebugSymbols(FunctionDefinition fn) {
        if (!isEnabled) {
            return;
        }

        for (SourceVariable local : fn.getSourceFunction().getVariables()) {
            if (local.isSingleDeclaration()) {
                final DbgDeclareInstruction dbg = local.getSingleDeclaration();
                FrameSlot frameSlot = null;
                if (dbg.getValue() instanceof AllocateInstruction) {
                    frameSlot = frame.findFrameSlot(((AllocateInstruction) dbg.getValue()).getName());
                }
                if (frameSlot == null) {
                    continue;
                }

                final LLVMSourceSymbol symbol = local.getSymbol();
                final LLVMFrameValueAccess alloc = context.getLanguage().getNodeFactory().createDebugFrameValue(frameSlot, true);
                notNullableSlots.add(frameSlot);
                context.getSourceContext().registerFrameValue(symbol, alloc);
                local.addStaticValue();

            } else if (local.isSingleValue()) {
                final DbgValueInstruction dbg = local.getSingleValue();
                final MDExpression expr = dbg.getExpression();
                final SymbolImpl value = dbg.getValue();
                if (expr.getElementCount() != 0) {
                    continue;
                }
                final boolean mustDereference = mustDereferenceValue(expr, local.getSourceType(), value);

                staticValueAccessVisitor.registerStaticAccess(local, value, mustDereference);
            }
        }
    }

    LLVMStatementNode createInitializer(SourceVariable variable) {
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

        return context.getLanguage().getNodeFactory().createDebugValueInit(targetSlot, offsets, lengths);
    }

    private static final int[] CLEAR_NONE = new int[0];

    LLVMStatementNode handleDebugIntrinsic(SymbolImpl value, SourceVariable variable, MDExpression expression, long index, boolean isDeclaration) {
        if (!isEnabled || variable.hasStaticAllocation()) {
            return null;
        }

        LLVMExpressionNode valueRead = null;
        if (isDeclaration) {
            if (value instanceof UndefinedConstant) {
                if (variable.hasValue()) {
                    // this declaration only tells us that the variable is not in memory, we already
                    // know this from the presence of the value
                    return null;
                }
                valueRead = symbols.resolve(new NullConstant(MetaType.DEBUG));

            } else if (value instanceof NullConstant) {
                valueRead = symbols.resolve(new NullConstant(MetaType.DEBUG));

            } else if (value instanceof GlobalValueSymbol || value.getType() instanceof PointerType) {
                valueRead = symbols.resolve(value);
            }

        } else {
            valueRead = symbols.resolve(value);

            if (index != 0) {
                // this is unsupported, it doesn't appear in LLVM 3.8+
                return null;
            }
        }

        if (valueRead == null) {
            return null;
        }

        int partIndex = -1;
        int[] clearParts = null;

        if (ValueFragment.describesFragment(expression)) {
            final ValueFragment fragment = ValueFragment.parse(expression);
            final List<ValueFragment> siblings = variable.getFragments();
            final List<Integer> clearSiblings = new ArrayList<>(siblings.size());
            partIndex = ValueFragment.getPartIndex(fragment, siblings, clearSiblings);
            if (clearSiblings.isEmpty()) {
                // this will be the case most of the time
                clearParts = CLEAR_NONE;
            } else {
                clearParts = clearSiblings.stream().mapToInt(Integer::intValue).toArray();
            }
        }

        if (partIndex < 0 && variable.hasFragments()) {
            partIndex = variable.getFragmentIndex(0, (int) variable.getSymbol().getType().getSize());
            if (partIndex < 0) {
                throw new LLVMParserException("Cannot find index of value fragment!");
            }

            clearParts = new int[variable.getFragments().size() - 1];
            for (int i = 0; i < partIndex; i++) {
                clearParts[i] = i;
            }
            for (int i = partIndex; i < clearParts.length; i++) {
                clearParts[i] = i + 1;
            }
        }

        final boolean mustDereference = isDeclaration || mustDereferenceValue(expression, variable.getSourceType(), value);

        final FrameSlot targetSlot = frame.findOrAddFrameSlot(variable.getSymbol(), MetaType.DEBUG, FrameSlotKind.Object);
        final LLVMExpressionNode containerRead = context.getLanguage().getNodeFactory().createFrameRead(MetaType.DEBUG, targetSlot);
        return context.getLanguage().getNodeFactory().createDebugValueUpdate(mustDereference, valueRead, targetSlot, containerRead, partIndex, clearParts);
    }

}
