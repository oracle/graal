/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.parser.metadata.debuginfo;

import static com.oracle.truffle.llvm.parser.metadata.debuginfo.DebugInfoCache.getDebugInfo;

import java.util.ArrayDeque;

import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.llvm.parser.metadata.MDBaseNode;
import com.oracle.truffle.llvm.parser.metadata.MDExpression;
import com.oracle.truffle.llvm.parser.metadata.MDLocalVariable;
import com.oracle.truffle.llvm.parser.metadata.MDLocation;
import com.oracle.truffle.llvm.parser.metadata.MetadataSymbol;
import com.oracle.truffle.llvm.parser.metadata.MetadataVisitor;
import com.oracle.truffle.llvm.parser.model.IRScope;
import com.oracle.truffle.llvm.parser.model.SymbolImpl;
import com.oracle.truffle.llvm.parser.model.blocks.InstructionBlock;
import com.oracle.truffle.llvm.parser.model.functions.FunctionDeclaration;
import com.oracle.truffle.llvm.parser.model.functions.FunctionDefinition;
import com.oracle.truffle.llvm.parser.model.symbols.constants.NullConstant;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.DbgDeclareInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.DbgValueInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.DebugTrapInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.Instruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.VoidCallInstruction;
import com.oracle.truffle.llvm.parser.model.visitors.FunctionVisitor;
import com.oracle.truffle.llvm.parser.model.visitors.InstructionVisitorAdapter;
import com.oracle.truffle.llvm.parser.nodes.LLVMSymbolReadResolver;
import com.oracle.truffle.llvm.runtime.LLVMContext;
import com.oracle.truffle.llvm.runtime.debug.scope.LLVMSourceLocation;
import com.oracle.truffle.llvm.runtime.debug.scope.LLVMSourceSymbol;
import com.oracle.truffle.llvm.runtime.debug.type.LLVMSourceFunctionType;
import com.oracle.truffle.llvm.runtime.debug.type.LLVMSourceType;
import com.oracle.truffle.llvm.runtime.types.MetaType;

public final class DebugInfoFunctionProcessor {

    private static final int LLVM_DBG_INTRINSICS_VALUE_ARGINDEX = 0;

    private static final String LLVM_DBG_DECLARE_NAME = "llvm.dbg.declare";
    private static final int LLVM_DBG_DECLARE_LOCALREF_ARGINDEX = 1;
    private static final int LLVM_DBG_DECLARE_EXPR_ARGINDEX = 2;

    private static final String LLVM_DBG_ADDR_NAME = "llvm.dbg.addr";

    private static final String LLVM_DBG_VALUE_NAME = "llvm.dbg.value";

    private static final int LLVM_DBG_VALUE_INDEX_ARGINDEX_OLD = 1;
    private static final int LLVM_DBG_VALUE_LOCALREF_ARGINDEX_OLD = 2;
    private static final int LLVM_DBG_VALUE_EXPR_ARGINDEX_OLD = 3;
    private static final int LLVM_DBG_VALUE_LOCALREF_ARGSIZE_OLD = 4;

    private static final int LLVM_DBG_VALUE_LOCALREF_ARGINDEX_NEW = 1;
    private static final int LLVM_DBG_VALUE_EXPR_ARGINDEX_NEW = 2;
    private static final int LLVM_DBG_VALUE_LOCALREF_ARGSIZE_NEW = 3;

    private static final String LLVM_DEBUGTRAP_NAME = "llvm.debugtrap";

    private final DebugInfoCache cache;

    DebugInfoFunctionProcessor(DebugInfoCache cache) {
        this.cache = cache;
    }

    public void process(FunctionDefinition function, IRScope scope, Source bitcodeSource, LLVMContext context) {
        ImportsProcessor.process(scope.getMetadata(), context, cache);
        initSourceFunction(function, bitcodeSource);
        function.accept((FunctionVisitor) new SymbolProcessor(function.getSourceFunction()));
        scope.getMetadata().consumeLocals(new MetadataProcessor());
        for (SourceVariable local : function.getSourceFunction().getVariables()) {
            local.processFragments();
        }
        cache.endLocalScope();
    }

    private void initSourceFunction(FunctionDefinition function, Source bitcodeSource) {
        final MDBaseNode debugInfo = getDebugInfo(function);
        LLVMSourceLocation scope = null;
        LLVMSourceFunctionType type = null;
        if (debugInfo != null) {
            scope = cache.buildLocation(debugInfo);
            LLVMSourceType actualType = cache.parseType(debugInfo);
            if (actualType instanceof LLVMSourceFunctionType) {
                type = (LLVMSourceFunctionType) actualType;
            }
        }

        if (scope == null) {
            final String sourceText = String.format("%s:%s", bitcodeSource.getName(), function.getName());
            final Source irSource = Source.newBuilder("llvm", sourceText, sourceText).mimeType(DIScopeBuilder.getMimeType(null)).build();
            final SourceSection simpleSection = irSource.createSection(1);
            scope = LLVMSourceLocation.createBitcodeFunction(function.getName(), simpleSection);
        }

        final SourceFunction sourceFunction = new SourceFunction(scope, type);
        function.setSourceFunction(sourceFunction);
    }

    private static SymbolImpl getArg(VoidCallInstruction call, int index) {
        return index < call.getArgumentCount() ? call.getArgument(index) : null;
    }

    private static MDExpression getExpression(VoidCallInstruction call, int index) {
        final SymbolImpl argSymbol = getArg(call, index);
        if (argSymbol instanceof MetadataSymbol) {
            final MDBaseNode mdNode = ((MetadataSymbol) argSymbol).getNode();
            if (mdNode instanceof MDExpression) {
                return (MDExpression) mdNode;
            }
        }
        return MDExpression.EMPTY;
    }

    private final class SymbolProcessor implements FunctionVisitor, InstructionVisitorAdapter {

        private final SourceFunction function;
        private final ArrayDeque<Integer> removeFromBlock = new ArrayDeque<>();

        private int blockInstIndex = 0;
        private DbgValueInstruction lastDbgValue = null;
        private InstructionBlock currentBlock = null;

        private SymbolProcessor(SourceFunction function) {
            this.function = function;
        }

        @Override
        public void visit(InstructionBlock block) {
            currentBlock = block;
            lastDbgValue = null;
            for (blockInstIndex = 0; blockInstIndex < block.getInstructionCount(); blockInstIndex++) {
                block.getInstruction(blockInstIndex).accept(this);
            }
            if (!removeFromBlock.isEmpty()) {
                for (int i : removeFromBlock) {
                    currentBlock.remove(i);
                }
                removeFromBlock.clear();
            }
        }

        @Override
        public void visitInstruction(Instruction instruction) {
            final MDLocation loc = instruction.getDebugLocation();
            if (loc != null) {
                final LLVMSourceLocation scope = cache.buildLocation(loc);
                if (scope != null) {
                    instruction.setSourceLocation(scope);
                }
            }
        }

        @Override
        public void visit(VoidCallInstruction call) {
            final SymbolImpl callTarget = call.getCallTarget();
            if (callTarget instanceof FunctionDeclaration) {
                switch (((FunctionDeclaration) callTarget).getName()) {
                    case LLVM_DBG_DECLARE_NAME:
                        handleDebugIntrinsic(call, true);
                        return;

                    case LLVM_DBG_ADDR_NAME:
                        // dbg.declare and dbg.addr have the same interface and, for our purposes,
                        // the same semantics
                        handleDebugIntrinsic(call, true);
                        return;

                    case LLVM_DBG_VALUE_NAME:
                        handleDebugIntrinsic(call, false);
                        return;

                    case LLVM_DEBUGTRAP_NAME:
                        visitDebugTrap(call);
                        return;
                }
            }

            visitInstruction(call);
        }

        private void visitDebugTrap(VoidCallInstruction call) {
            final DebugTrapInstruction trap = DebugTrapInstruction.create(call);
            currentBlock.set(blockInstIndex, trap);
            visitInstruction(trap);
        }

        private SourceVariable getVariable(VoidCallInstruction call, int index) {
            final SymbolImpl varSymbol = getArg(call, index);
            if (varSymbol instanceof MetadataSymbol) {
                final MDBaseNode mdLocal = ((MetadataSymbol) varSymbol).getNode();

                final LLVMSourceSymbol symbol = cache.getSourceSymbol(mdLocal, false);
                return function.getLocal(symbol);
            }

            return null;
        }

        private void handleDebugIntrinsic(VoidCallInstruction call, boolean isDeclaration) {
            SymbolImpl value = getArg(call, LLVM_DBG_INTRINSICS_VALUE_ARGINDEX);
            if (value instanceof MetadataSymbol) {
                value = MDSymbolExtractor.getSymbol(((MetadataSymbol) value).getNode());
            }

            if (value == null) {
                // this may happen if llvm optimizations removed a variable
                value = new NullConstant(MetaType.DEBUG);
            }

            int mdLocalArgIndex;
            int mdExprArgIndex;
            if (isDeclaration) {
                mdLocalArgIndex = LLVM_DBG_DECLARE_LOCALREF_ARGINDEX;
                mdExprArgIndex = LLVM_DBG_DECLARE_EXPR_ARGINDEX;

            } else if (call.getArgumentCount() == LLVM_DBG_VALUE_LOCALREF_ARGSIZE_NEW) {
                mdLocalArgIndex = LLVM_DBG_VALUE_LOCALREF_ARGINDEX_NEW;
                mdExprArgIndex = LLVM_DBG_VALUE_EXPR_ARGINDEX_NEW;

            } else if (call.getArgumentCount() == LLVM_DBG_VALUE_LOCALREF_ARGSIZE_OLD) {
                mdLocalArgIndex = LLVM_DBG_VALUE_LOCALREF_ARGINDEX_OLD;
                mdExprArgIndex = LLVM_DBG_VALUE_EXPR_ARGINDEX_OLD;

            } else {
                return;
            }

            final SourceVariable variable = getVariable(call, mdLocalArgIndex);
            if (variable == null) {
                // invalid or unsupported debug information
                // remove upper indices so we do not need to update the later ones
                removeFromBlock.addFirst(blockInstIndex);
                return;
            }

            final MDExpression expression = getExpression(call, mdExprArgIndex);
            if (ValueFragment.describesFragment(expression)) {
                variable.addFragment(ValueFragment.parse(expression));
            } else {
                variable.addFullDefinition();
            }

            if (isDeclaration) {
                final DbgDeclareInstruction dbgDeclare = new DbgDeclareInstruction(value, variable, expression);
                variable.addDeclaration(dbgDeclare);
                currentBlock.set(blockInstIndex, dbgDeclare);

            } else {
                long index = 0;
                if (call.getArgumentCount() == LLVM_DBG_VALUE_LOCALREF_ARGSIZE_OLD) {
                    final SymbolImpl indexSymbol = call.getArgument(LLVM_DBG_VALUE_INDEX_ARGINDEX_OLD);
                    final Long l = LLVMSymbolReadResolver.evaluateLongIntegerConstant(indexSymbol);
                    if (l != null) {
                        index = l;
                    }
                }
                final DbgValueInstruction dbgValue = new DbgValueInstruction(value, variable, index, expression);

                if (dbgValue.equals(lastDbgValue)) {
                    // at higher optimization levels llvm often duplicates the @llvm.dbg.value
                    // intrinsic call, we remove it again to avoid unnecessary runtime overhead
                    removeFromBlock.addFirst(blockInstIndex);

                } else {
                    variable.addValue(dbgValue);
                    currentBlock.set(blockInstIndex, dbgValue);
                    lastDbgValue = dbgValue;
                }
            }
        }
    }

    private final class MetadataProcessor implements MetadataVisitor {

        @Override
        public void visit(MDLocalVariable md) {
            // make sure the symbol is registered even if no value is available
            cache.getSourceSymbol(md, false);
        }

    }
}
