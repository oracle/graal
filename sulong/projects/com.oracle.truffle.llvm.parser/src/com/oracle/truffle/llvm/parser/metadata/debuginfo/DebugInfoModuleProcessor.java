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
package com.oracle.truffle.llvm.parser.metadata.debuginfo;

import static com.oracle.truffle.llvm.parser.metadata.debuginfo.DebugInfoCache.getDebugInfo;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;

import com.oracle.truffle.llvm.parser.metadata.DwarfOpcode;
import com.oracle.truffle.llvm.parser.metadata.MDBaseNode;
import com.oracle.truffle.llvm.parser.metadata.MDCompileUnit;
import com.oracle.truffle.llvm.parser.metadata.MDExpression;
import com.oracle.truffle.llvm.parser.metadata.MDGlobalVariable;
import com.oracle.truffle.llvm.parser.metadata.MDGlobalVariableExpression;
import com.oracle.truffle.llvm.parser.metadata.MDLocalVariable;
import com.oracle.truffle.llvm.parser.metadata.MDNamedNode;
import com.oracle.truffle.llvm.parser.metadata.MDNode;
import com.oracle.truffle.llvm.parser.metadata.MDVoidNode;
import com.oracle.truffle.llvm.parser.metadata.MetadataValueList;
import com.oracle.truffle.llvm.parser.metadata.MetadataVisitor;
import com.oracle.truffle.llvm.parser.model.ModelModule;
import com.oracle.truffle.llvm.parser.model.SymbolImpl;
import com.oracle.truffle.llvm.parser.model.symbols.constants.integer.BigIntegerConstant;
import com.oracle.truffle.llvm.parser.model.symbols.globals.GlobalValueSymbol;
import com.oracle.truffle.llvm.runtime.LLVMContext;
import com.oracle.truffle.llvm.runtime.debug.scope.LLVMSourceSymbol;
import com.oracle.truffle.llvm.runtime.debug.type.LLVMSourceStaticMemberType;
import com.oracle.truffle.llvm.runtime.debug.type.LLVMSourceType;
import com.oracle.truffle.llvm.runtime.types.VariableBitWidthType;

public final class DebugInfoModuleProcessor {

    private DebugInfoModuleProcessor() {
    }

    public static void processModule(ModelModule irModel, MetadataValueList metadata, LLVMContext context) {
        MDUpgrade.perform(metadata);

        final DebugInfoCache cache = new DebugInfoCache(metadata, irModel.getSourceStaticMembers(), context);

        ImportsProcessor.process(metadata, context, cache);

        // in LLVM 3.9+ function debug information is available only in the corresponding
        // function block in the *.bc file, we process the function metadata only after it is
        // actually available
        processSymbols(irModel.getGlobalVariables(), cache, irModel);
        processSymbols(irModel.getAliases(), cache, irModel);

        final MetadataProcessor mdParser = new MetadataProcessor(cache, irModel.getSourceGlobals(), irModel.getSourceStaticMembers());
        final MDBaseNode cuNode = metadata.getNamedNode(MDNamedNode.COMPILEUNIT_NAME);
        if (cuNode != null) {
            cuNode.accept(mdParser);
        }

        irModel.setFunctionProcessor(new DebugInfoFunctionProcessor(cache));
    }

    private static void processSymbols(List<? extends GlobalValueSymbol> list, DebugInfoCache cache, ModelModule irModel) {
        for (GlobalValueSymbol global : list) {
            MDBaseNode mdGlobal = getDebugInfo(global);
            if (mdGlobal != null) {
                final boolean isGlobal = !(mdGlobal instanceof MDLocalVariable);
                final LLVMSourceSymbol symbol = cache.getSourceSymbol(mdGlobal, isGlobal);
                if (symbol != null) {
                    irModel.getSourceGlobals().put(symbol, global);
                    global.setSourceSymbol(symbol);
                }

                if (mdGlobal instanceof MDGlobalVariableExpression) {
                    mdGlobal = ((MDGlobalVariableExpression) mdGlobal).getGlobalVariable();
                }

                if (mdGlobal instanceof MDGlobalVariable) {
                    final MDBaseNode declaration = ((MDGlobalVariable) mdGlobal).getStaticMemberDeclaration();
                    if (declaration != MDVoidNode.INSTANCE) {
                        final LLVMSourceType sourceType = cache.parseType(declaration);
                        if (sourceType instanceof LLVMSourceStaticMemberType) {
                            irModel.getSourceStaticMembers().put((LLVMSourceStaticMemberType) sourceType, global);
                        }
                    }
                }
            }
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
