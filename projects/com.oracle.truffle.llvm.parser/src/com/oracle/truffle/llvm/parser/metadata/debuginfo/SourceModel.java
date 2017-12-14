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
package com.oracle.truffle.llvm.parser.metadata.debuginfo;

import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.llvm.parser.metadata.DwarfOpcode;
import com.oracle.truffle.llvm.parser.metadata.MDBaseNode;
import com.oracle.truffle.llvm.parser.metadata.MDCompileUnit;
import com.oracle.truffle.llvm.parser.metadata.MDExpression;
import com.oracle.truffle.llvm.parser.metadata.MDGlobalVariable;
import com.oracle.truffle.llvm.parser.metadata.MDGlobalVariableExpression;
import com.oracle.truffle.llvm.parser.metadata.MDKind;
import com.oracle.truffle.llvm.parser.metadata.MDLocalVariable;
import com.oracle.truffle.llvm.parser.metadata.MDNamedNode;
import com.oracle.truffle.llvm.parser.metadata.MDNode;
import com.oracle.truffle.llvm.parser.metadata.MDVoidNode;
import com.oracle.truffle.llvm.parser.metadata.MetadataSymbol;
import com.oracle.truffle.llvm.parser.metadata.MetadataValueList;
import com.oracle.truffle.llvm.parser.metadata.MDLocation;
import com.oracle.truffle.llvm.parser.metadata.MetadataAttachmentHolder;
import com.oracle.truffle.llvm.parser.metadata.MetadataVisitor;
import com.oracle.truffle.llvm.parser.model.ModelModule;
import com.oracle.truffle.llvm.parser.model.blocks.InstructionBlock;
import com.oracle.truffle.llvm.parser.model.functions.FunctionDeclaration;
import com.oracle.truffle.llvm.parser.model.functions.FunctionDefinition;
import com.oracle.truffle.llvm.parser.model.functions.FunctionParameter;
import com.oracle.truffle.llvm.parser.model.symbols.constants.NullConstant;
import com.oracle.truffle.llvm.parser.model.symbols.constants.integer.BigIntegerConstant;
import com.oracle.truffle.llvm.parser.model.symbols.globals.GlobalAlias;
import com.oracle.truffle.llvm.parser.model.symbols.globals.GlobalConstant;
import com.oracle.truffle.llvm.parser.model.symbols.globals.GlobalValueSymbol;
import com.oracle.truffle.llvm.parser.model.symbols.globals.GlobalVariable;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.DbgDeclareInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.DbgValueInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.Instruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.ValueInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.VoidCallInstruction;
import com.oracle.truffle.llvm.parser.model.visitors.FunctionVisitor;
import com.oracle.truffle.llvm.parser.model.visitors.InstructionVisitorAdapter;
import com.oracle.truffle.llvm.parser.model.visitors.ModelVisitor;
import com.oracle.truffle.llvm.parser.nodes.LLVMSymbolReadResolver;
import com.oracle.truffle.llvm.runtime.debug.LLVMSourceStaticMemberType;
import com.oracle.truffle.llvm.runtime.debug.LLVMSourceSymbol;
import com.oracle.truffle.llvm.runtime.debug.LLVMSourceType;
import com.oracle.truffle.llvm.runtime.debug.scope.LLVMSourceLocation;
import com.oracle.truffle.llvm.runtime.types.MetaType;
import com.oracle.truffle.llvm.parser.model.SymbolImpl;
import com.oracle.truffle.llvm.runtime.types.VariableBitWidthType;

import java.math.BigInteger;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public final class SourceModel {

    public static final SourceFunction DEFAULT_FUNCTION = new SourceFunction(LLVMSourceLocation.createUnavailable(LLVMSourceLocation.Kind.FUNCTION, "<unavailable>", "<unavailable>", 0, 0));

    private static final int LLVM_DBG_INTRINSICS_VALUE_ARGINDEX = 0;

    private static final String LLVM_DBG_DECLARE_NAME = "@llvm.dbg.declare";
    private static final int LLVM_DBG_DECLARE_LOCALREF_ARGINDEX = 1;
    private static final int LLVM_DBG_DECLARE_EXPR_ARGINDEX = 2;

    private static final String LLVM_DBG_VALUE_NAME = "@llvm.dbg.value";
    private static final int LLVM_DBG_VALUE_INDEX_ARGINDEX = 1;
    private static final int LLVM_DBG_VALUE_LOCALREF_ARGINDEX = 2;
    private static final int LLVM_DBG_VALUE_EXPR_ARGINDEX = 3;

    private final Map<LLVMSourceSymbol, SymbolImpl> globals;
    private final Map<LLVMSourceStaticMemberType, SymbolImpl> staticMembers;

    public SourceModel() {
        globals = new HashMap<>();
        staticMembers = new HashMap<>();
    }

    public void process(ModelModule irModel, Source bitcodeSource, MetadataValueList metadata) {
        final Cache cache = new Cache(this, metadata);
        MDUpgrade.perform(metadata);

        final SymbolParser symbolParser = new SymbolParser(cache, bitcodeSource);
        irModel.accept(symbolParser);

        final MDBaseNode cuNode = metadata.getNamedNode(MDNamedNode.COMPILEUNIT_NAME);
        final MetadataParser mdParser = new MetadataParser(cache);
        if (cuNode != null) {
            cuNode.accept(mdParser);
        }
        metadata.localsAccept(mdParser);
    }

    public Map<LLVMSourceSymbol, SymbolImpl> getGlobals() {
        return Collections.unmodifiableMap(globals);
    }

    public Map<LLVMSourceStaticMemberType, SymbolImpl> getStaticMembers() {
        return Collections.unmodifiableMap(staticMembers);
    }

    private static final class Cache {

        final Map<MDBaseNode, LLVMSourceSymbol> parsedVariables;
        final DIScopeBuilder scopeBuilder;
        final DITypeExtractor typeExtractor;
        final SourceModel sourceModel;

        private Cache(SourceModel sourceModel, MetadataValueList metadata) {
            this.sourceModel = sourceModel;
            this.parsedVariables = new HashMap<>();
            this.scopeBuilder = new DIScopeBuilder(metadata);
            this.typeExtractor = new DITypeExtractor(scopeBuilder, metadata, sourceModel.staticMembers);
        }

        LLVMSourceSymbol getSourceSymbol(MDBaseNode mdVariable, boolean isGlobal) {
            if (parsedVariables.containsKey(mdVariable)) {
                return parsedVariables.get(mdVariable);
            }

            LLVMSourceLocation location = scopeBuilder.buildLocation(mdVariable);
            final LLVMSourceType type = typeExtractor.parseType(mdVariable);
            final String varName = MDNameExtractor.getName(mdVariable);

            final LLVMSourceSymbol symbol = new LLVMSourceSymbol(varName, location, type, isGlobal);
            parsedVariables.put(mdVariable, symbol);

            if (location != null) {
                // this is currently the line/column where the symbol was declared, we want the
                // scope
                location = location.getParent();
            }

            if (location != null) {
                location.addSymbol(symbol);
            }

            return symbol;
        }

        LLVMSourceLocation buildLocation(MDBaseNode node) {
            return scopeBuilder.buildLocation(node);
        }

        void endLocalScope() {
            scopeBuilder.clearLocalScopes();
        }
    }

    private static final class SymbolParser implements FunctionVisitor, InstructionVisitorAdapter, ModelVisitor {

        private static MDBaseNode getDebugInfo(MetadataAttachmentHolder holder) {
            if (holder.hasAttachedMetadata()) {
                return holder.getMetadataAttachment(MDKind.DBG_NAME);

            } else {
                return null;
            }
        }

        private final Cache cache;
        private final Source bitcodeSource;

        private SourceFunction currentFunction = null;
        private InstructionBlock currentBlock = null;

        private SymbolParser(Cache cache, Source bitcodeSource) {
            this.cache = cache;
            this.bitcodeSource = bitcodeSource;
        }

        @Override
        public void visit(FunctionDeclaration function) {
        }

        @Override
        public void visit(FunctionDefinition function) {
            final MDBaseNode debugInfo = getDebugInfo(function);
            LLVMSourceLocation scope = debugInfo != null ? cache.buildLocation(debugInfo) : null;

            if (scope == null) {
                final String sourceText = String.format("%s:%s", bitcodeSource.getName(), function.getName());
                final Source irSource = Source.newBuilder(sourceText).mimeType(DIScopeBuilder.getMimeType(null)).name(sourceText).build();
                final SourceSection simpleSection = irSource.createSection(1);
                scope = LLVMSourceLocation.createBitcodeFunction(function.getName(), simpleSection);
            }

            currentFunction = new SourceFunction(scope);

            function.accept((FunctionVisitor) this);
            function.setSourceFunction(currentFunction);

            for (SourceVariable local : currentFunction.getVariables()) {
                local.processFragments();
            }

            cache.endLocalScope();
            currentFunction = null;
        }

        private void visitGlobal(GlobalValueSymbol global) {
            MDBaseNode mdGlobal = getDebugInfo(global);
            if (mdGlobal != null) {
                final boolean isGlobal = !(mdGlobal instanceof MDLocalVariable);
                final LLVMSourceSymbol symbol = cache.getSourceSymbol(mdGlobal, isGlobal);
                if (symbol != null) {
                    cache.sourceModel.globals.put(symbol, global);
                }

                if (mdGlobal instanceof MDGlobalVariableExpression) {
                    mdGlobal = ((MDGlobalVariableExpression) mdGlobal).getGlobalVariable();
                }

                if (mdGlobal instanceof MDGlobalVariable) {
                    final MDBaseNode declaration = ((MDGlobalVariable) mdGlobal).getStaticMemberDeclaration();
                    if (declaration != MDVoidNode.INSTANCE) {
                        final LLVMSourceType sourceType = cache.typeExtractor.parseType(declaration);
                        if (sourceType instanceof LLVMSourceStaticMemberType) {
                            cache.sourceModel.staticMembers.put((LLVMSourceStaticMemberType) sourceType, global);
                        }
                    }
                }
            }
        }

        @Override
        public void visit(GlobalAlias alias) {
            visitGlobal(alias);
        }

        @Override
        public void visit(GlobalConstant constant) {
            visitGlobal(constant);
        }

        @Override
        public void visit(GlobalVariable variable) {
            visitGlobal(variable);
        }

        @Override
        public void visit(InstructionBlock block) {
            currentBlock = block;
            block.accept(this);
            currentBlock = null;
        }

        @Override
        public void visitInstruction(Instruction instruction) {
            final MDLocation loc = instruction.getDebugLocation();
            if (loc != null) {
                final LLVMSourceLocation scope = cache.buildLocation(loc);
                if (scope != null) {
                    currentFunction.addInstruction(instruction, scope);
                }
            }
        }

        @Override
        public void visit(VoidCallInstruction call) {
            final SymbolImpl callTarget = call.getCallTarget();
            if (callTarget instanceof FunctionDeclaration) {

                int mdlocalArgIndex = -1;
                int mdExprArgIndex = -1;
                boolean isDeclaration = false;
                switch (((FunctionDeclaration) callTarget).getName()) {
                    case LLVM_DBG_DECLARE_NAME:
                        mdlocalArgIndex = LLVM_DBG_DECLARE_LOCALREF_ARGINDEX;
                        mdExprArgIndex = LLVM_DBG_DECLARE_EXPR_ARGINDEX;
                        isDeclaration = true;
                        break;

                    case LLVM_DBG_VALUE_NAME:
                        mdlocalArgIndex = LLVM_DBG_VALUE_LOCALREF_ARGINDEX;
                        mdExprArgIndex = LLVM_DBG_VALUE_EXPR_ARGINDEX;
                        break;
                }

                if (mdlocalArgIndex >= 0) {
                    handleDebugIntrinsic(call, mdlocalArgIndex, mdExprArgIndex, isDeclaration);
                }
            }

            visitInstruction(call);
        }

        private static SymbolImpl getArg(VoidCallInstruction call, int index) {
            return index < call.getArgumentCount() ? call.getArgument(index) : null;
        }

        private SourceVariable getVariable(VoidCallInstruction call, int index) {
            final SymbolImpl varSymbol = getArg(call, index);
            if (varSymbol instanceof MetadataSymbol) {
                final MDBaseNode mdLocal = ((MetadataSymbol) varSymbol).getNode();

                final LLVMSourceSymbol symbol = cache.getSourceSymbol(mdLocal, false);
                return currentFunction.getLocal(symbol);
            }

            return null;
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

        private void handleDebugIntrinsic(VoidCallInstruction call, int mdlocalArgIndex, int mdExprArgIndex, boolean isDeclaration) {
            SymbolImpl value = getArg(call, LLVM_DBG_INTRINSICS_VALUE_ARGINDEX);
            if (value instanceof MetadataSymbol) {
                value = MDSymbolExtractor.getSymbol(((MetadataSymbol) value).getNode());
            }

            if (value == null) {
                // this may happen if llvm optimizations removed a variable
                value = new NullConstant(MetaType.DEBUG);

            } else if (value instanceof ValueInstruction) {
                ((ValueInstruction) value).setSourceVariable(true);

            } else if (value instanceof FunctionParameter) {
                ((FunctionParameter) value).setSourceVariable(true);
            }

            final SourceVariable variable = getVariable(call, mdlocalArgIndex);
            if (variable == null) {
                // invalid or unsupported debug information
                currentBlock.remove(call);
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
                currentBlock.replace(call, dbgDeclare);

            } else {
                long index = 0;
                final SymbolImpl indexSymbol = call.getArgument(LLVM_DBG_VALUE_INDEX_ARGINDEX);
                final Long l = LLVMSymbolReadResolver.evaluateLongIntegerConstant(indexSymbol);
                if (l != null) {
                    index = l;
                }
                final DbgValueInstruction dbgValue = new DbgValueInstruction(value, variable, index, expression);
                variable.addValue(dbgValue);
                currentBlock.replace(call, dbgValue);
            }
        }
    }

    private static final class MetadataParser implements MetadataVisitor {

        private final Cache cache;

        private MetadataParser(Cache cache) {
            this.cache = cache;
        }

        @Override
        public void visit(MDNamedNode md) {
            for (MDBaseNode node : md) {
                node.accept(this);
            }
        }

        @Override
        public void visit(MDNode md) {
            for (MDBaseNode node : md) {
                node.accept(this);
            }
        }

        @Override
        public void visit(MDCompileUnit md) {
            md.getGlobalVariables().accept(this);
        }

        @Override
        public void visit(MDGlobalVariableExpression md) {
            final LLVMSourceSymbol symbol = cache.getSourceSymbol(md, true);
            final MDBaseNode var = md.getExpression();
            if (var instanceof MDExpression && !MDExpression.EMPTY.equals(var)) {
                SymbolImpl value = getSymbol((MDExpression) var, (int) symbol.getType().getSize());
                if (value != null) {
                    cache.sourceModel.globals.put(symbol, value);
                }
            }
        }

        @Override
        public void visit(MDGlobalVariable md) {
            final LLVMSourceSymbol symbol = cache.getSourceSymbol(md, true);
            if (md.getVariable() == MDVoidNode.INSTANCE || cache.sourceModel.globals.containsKey(symbol)) {
                return;
            }

            final SymbolImpl value = MDSymbolExtractor.getSymbol(md.getVariable());
            if (value != null) {
                cache.sourceModel.globals.put(symbol, value);
            }
        }

        @Override
        public void visit(MDLocalVariable md) {
            // make sure the symbol is registered even if no value is available
            cache.getSourceSymbol(md, false);
        }

        private static SymbolImpl getSymbol(MDExpression expr, int typeSize) {
            final BigInteger val = DwarfOpcode.toIntegerSymbol(expr);
            if (val != null) {
                return new BigIntegerConstant(new VariableBitWidthType(typeSize), val);
            }
            return null;
        }
    }
}
