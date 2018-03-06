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
import com.oracle.truffle.llvm.parser.metadata.MetadataAttachmentHolder;
import com.oracle.truffle.llvm.parser.metadata.MetadataValueList;
import com.oracle.truffle.llvm.parser.metadata.MetadataVisitor;
import com.oracle.truffle.llvm.parser.model.ModelModule;
import com.oracle.truffle.llvm.parser.model.SymbolImpl;
import com.oracle.truffle.llvm.parser.model.functions.FunctionDeclaration;
import com.oracle.truffle.llvm.parser.model.functions.FunctionDefinition;
import com.oracle.truffle.llvm.parser.model.symbols.constants.integer.BigIntegerConstant;
import com.oracle.truffle.llvm.parser.model.symbols.globals.GlobalAlias;
import com.oracle.truffle.llvm.parser.model.symbols.globals.GlobalConstant;
import com.oracle.truffle.llvm.parser.model.symbols.globals.GlobalValueSymbol;
import com.oracle.truffle.llvm.parser.model.symbols.globals.GlobalVariable;
import com.oracle.truffle.llvm.parser.model.visitors.ModelVisitor;
import com.oracle.truffle.llvm.runtime.debug.LLVMSourceStaticMemberType;
import com.oracle.truffle.llvm.runtime.debug.LLVMSourceSymbol;
import com.oracle.truffle.llvm.runtime.debug.LLVMSourceType;
import com.oracle.truffle.llvm.runtime.debug.scope.LLVMSourceLocation;
import com.oracle.truffle.llvm.runtime.types.VariableBitWidthType;

import java.math.BigInteger;
import java.util.Map;

public final class DebugInfoModuleProcessor {

    public static final SourceFunction DEFAULT_FUNCTION = new SourceFunction(LLVMSourceLocation.createUnavailable(LLVMSourceLocation.Kind.FUNCTION, "<unavailable>", "<unavailable>", 0, 0));

    private DebugInfoModuleProcessor() {
    }

    public static DebugInfoFunctionProcessor processModule(ModelModule irModel, Source bitcodeSource, MetadataValueList metadata) {
        MDUpgrade.perform(metadata);

        final DebugInfoCache cache = new DebugInfoCache(metadata, irModel.getSourceStaticMembers());

        final Map<LLVMSourceSymbol, SymbolImpl> globals = irModel.getSourceGlobals();
        final Map<LLVMSourceStaticMemberType, SymbolImpl> staticMembers = irModel.getSourceStaticMembers();

        irModel.accept(new SymbolProcessor(cache, bitcodeSource, globals, staticMembers));

        final MDBaseNode cuNode = metadata.getNamedNode(MDNamedNode.COMPILEUNIT_NAME);
        final MetadataProcessor mdParser = new MetadataProcessor(cache, globals, staticMembers);
        if (cuNode != null) {
            cuNode.accept(mdParser);
        }

        return new DebugInfoFunctionProcessor(cache);
    }

    private static final class SymbolProcessor implements ModelVisitor {

        private static MDBaseNode getDebugInfo(MetadataAttachmentHolder holder) {
            if (holder.hasAttachedMetadata()) {
                return holder.getMetadataAttachment(MDKind.DBG_NAME);

            } else {
                return null;
            }
        }

        private final DebugInfoCache cache;
        private final Source bitcodeSource;
        private final Map<LLVMSourceSymbol, SymbolImpl> sourceGlobals;
        private final Map<LLVMSourceStaticMemberType, SymbolImpl> sourceStaticMembers;

        SymbolProcessor(DebugInfoCache cache, Source bitcodeSource, Map<LLVMSourceSymbol, SymbolImpl> sourceGlobals, Map<LLVMSourceStaticMemberType, SymbolImpl> sourceStaticMembers) {
            this.cache = cache;
            this.bitcodeSource = bitcodeSource;
            this.sourceGlobals = sourceGlobals;
            this.sourceStaticMembers = sourceStaticMembers;
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

            final SourceFunction sourceFunction = new SourceFunction(scope);
            function.setSourceFunction(sourceFunction);
            for (SourceVariable local : sourceFunction.getVariables()) {
                local.processFragments();
            }
        }

        private void visitGlobal(GlobalValueSymbol global) {
            MDBaseNode mdGlobal = getDebugInfo(global);
            if (mdGlobal != null) {
                final boolean isGlobal = !(mdGlobal instanceof MDLocalVariable);
                final LLVMSourceSymbol symbol = cache.getSourceSymbol(mdGlobal, isGlobal);
                if (symbol != null) {
                    sourceGlobals.put(symbol, global);
                }

                if (mdGlobal instanceof MDGlobalVariableExpression) {
                    mdGlobal = ((MDGlobalVariableExpression) mdGlobal).getGlobalVariable();
                }

                if (mdGlobal instanceof MDGlobalVariable) {
                    final MDBaseNode declaration = ((MDGlobalVariable) mdGlobal).getStaticMemberDeclaration();
                    if (declaration != MDVoidNode.INSTANCE) {
                        final LLVMSourceType sourceType = cache.parseType(declaration);
                        if (sourceType instanceof LLVMSourceStaticMemberType) {
                            sourceStaticMembers.put((LLVMSourceStaticMemberType) sourceType, global);
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
    }

    private static final class MetadataProcessor implements MetadataVisitor {

        private final DebugInfoCache cache;
        private final Map<LLVMSourceSymbol, SymbolImpl> sourceGlobals;
        private final Map<LLVMSourceStaticMemberType, SymbolImpl> sourceStaticMembers;

        MetadataProcessor(DebugInfoCache cache, Map<LLVMSourceSymbol, SymbolImpl> sourceGlobals, Map<LLVMSourceStaticMemberType, SymbolImpl> sourceStaticMembers) {
            this.cache = cache;
            this.sourceGlobals = sourceGlobals;
            this.sourceStaticMembers = sourceStaticMembers;
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
                    sourceGlobals.put(symbol, value);
                }
            }
        }

        @Override
        public void visit(MDGlobalVariable md) {
            final LLVMSourceSymbol symbol = cache.getSourceSymbol(md, true);
            if (md.getVariable() == MDVoidNode.INSTANCE || sourceGlobals.containsKey(symbol)) {
                return;
            }

            final SymbolImpl value = MDSymbolExtractor.getSymbol(md.getVariable());
            if (value != null || !sourceGlobals.containsKey(symbol)) {
                sourceGlobals.put(symbol, value);
            }

            final MDBaseNode declaration = md.getStaticMemberDeclaration();
            if (declaration != MDVoidNode.INSTANCE) {
                final LLVMSourceType sourceType = cache.parseType(declaration);
                if (sourceType instanceof LLVMSourceStaticMemberType) {
                    final LLVMSourceStaticMemberType memberType = (LLVMSourceStaticMemberType) sourceType;
                    if (!sourceStaticMembers.containsKey(memberType)) {
                        sourceStaticMembers.put((LLVMSourceStaticMemberType) sourceType, value);
                    }
                }
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
