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
import com.oracle.truffle.llvm.parser.metadata.MDBaseNode;
import com.oracle.truffle.llvm.parser.metadata.MDKind;
import com.oracle.truffle.llvm.parser.metadata.MDLocation;
import com.oracle.truffle.llvm.parser.metadata.MetadataAttachmentHolder;
import com.oracle.truffle.llvm.parser.metadata.MetadataList;
import com.oracle.truffle.llvm.parser.model.ModelModule;
import com.oracle.truffle.llvm.parser.model.blocks.InstructionBlock;
import com.oracle.truffle.llvm.parser.model.functions.FunctionDeclaration;
import com.oracle.truffle.llvm.parser.model.functions.FunctionDefinition;
import com.oracle.truffle.llvm.parser.model.functions.FunctionParameter;
import com.oracle.truffle.llvm.parser.model.symbols.constants.MetadataConstant;
import com.oracle.truffle.llvm.parser.model.symbols.constants.NullConstant;
import com.oracle.truffle.llvm.parser.model.symbols.globals.GlobalAlias;
import com.oracle.truffle.llvm.parser.model.symbols.globals.GlobalConstant;
import com.oracle.truffle.llvm.parser.model.symbols.globals.GlobalValueSymbol;
import com.oracle.truffle.llvm.parser.model.symbols.globals.GlobalVariable;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.Instruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.ValueInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.VoidCallInstruction;
import com.oracle.truffle.llvm.parser.model.visitors.FunctionVisitor;
import com.oracle.truffle.llvm.parser.model.visitors.InstructionVisitorAdapter;
import com.oracle.truffle.llvm.parser.model.visitors.ModelVisitor;
import com.oracle.truffle.llvm.runtime.debug.LLVMSourceSymbol;
import com.oracle.truffle.llvm.runtime.debug.LLVMSourceType;
import com.oracle.truffle.llvm.runtime.debug.scope.LLVMSourceFile;
import com.oracle.truffle.llvm.runtime.debug.scope.LLVMSourceLocation;
import com.oracle.truffle.llvm.runtime.types.MetaType;
import com.oracle.truffle.llvm.runtime.types.Type;
import com.oracle.truffle.llvm.runtime.types.symbols.Symbol;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public final class SourceModel {

    public static final int LLVM_DBG_INTRINSICS_VALUE_ARGINDEX = 0;

    public static final String LLVM_DBG_DECLARE_NAME = "@llvm.dbg.declare";
    public static final int LLVM_DBG_DECLARE_LOCALREF_ARGINDEX = 1;

    public static final String LLVM_DBG_VALUE_NAME = "@llvm.dbg.value";
    public static final int LLVM_DBG_VALUE_LOCALREF_ARGINDEX = 2;

    public static SourceModel generate(ModelModule irModel, Source bitcodeSource) {
        final MetadataList moduleMetadata = irModel.getMetadata();
        final Parser parser = new Parser(moduleMetadata, bitcodeSource);
        MDSymbolLinkUpgrade.perform(moduleMetadata);
        irModel.accept(parser);
        return parser.sourceModel;
    }

    public static final class Function {

        private final Source bitcodeSource;

        private final FunctionDefinition definition;

        private final Map<Instruction, LLVMSourceLocation> instructions = new HashMap<>();

        private LLVMSourceLocation lexicalScope;

        private Function(Source bitcodeSource, FunctionDefinition definition) {
            this.bitcodeSource = bitcodeSource;
            this.definition = definition;
        }

        public SourceSection getSourceSection() {
            SourceSection section = null;
            if (lexicalScope != null) {
                section = lexicalScope.getSourceSection(true);
            }

            if (section == null) {
                final String sourceText = String.format("%s:%s", bitcodeSource.getName(), definition.getName());
                final Source irSource = Source.newBuilder(sourceText).mimeType(LLVMSourceFile.getMimeType(null)).name(sourceText).build();
                section = irSource.createSection(1);
            }

            return section;
        }

        private void setLexicalScope(LLVMSourceLocation lexicalScope) {
            this.lexicalScope = lexicalScope;
        }

        public SourceSection getSourceSection(Instruction instruction) {
            final LLVMSourceLocation scope = instructions.get(instruction);
            return scope != null ? scope.getSourceSection() : null;
        }

        public LLVMSourceLocation getLexicalScope() {
            return lexicalScope;
        }
    }

    public static final class Variable implements Symbol {

        private final Symbol symbol;

        private final LLVMSourceSymbol variable;

        private Variable(Symbol symbol, LLVMSourceSymbol variable) {
            this.symbol = symbol;
            this.variable = variable;
        }

        public LLVMSourceSymbol getVariable() {
            return variable;
        }

        public String getName() {
            return variable.getName();
        }

        public Symbol getSymbol() {
            return symbol;
        }

        public LLVMSourceType getSourceType() {
            return variable.getType();
        }

        @Override
        public Type getType() {
            return MetaType.DEBUG;
        }

        @Override
        public String toString() {
            return variable.getName();
        }
    }

    private SourceModel() {
        globals = new HashMap<>();
    }

    private final Map<LLVMSourceSymbol, GlobalValueSymbol> globals;

    public Map<LLVMSourceSymbol, GlobalValueSymbol> getGlobals() {
        return Collections.unmodifiableMap(globals);
    }

    private static final class Parser implements ModelVisitor, FunctionVisitor, InstructionVisitorAdapter {

        private static MDBaseNode getDebugInfo(MetadataAttachmentHolder holder) {
            if (holder.hasAttachedMetadata()) {
                return holder.getMetadataAttachment(MDKind.DBG_NAME);

            } else {
                return null;
            }
        }

        private final Map<MDBaseNode, LLVMSourceSymbol> parsedVariables;

        private final DITypeIdentifier typeIdentifier;
        private final DIScopeExtractor scopeExtractor;
        private final DITypeExtractor typeExtractor;
        private final SourceModel sourceModel;

        private final MetadataList moduleMetadata;
        private final Source bitcodeSource;

        private Function currentFunction = null;

        private Parser(MetadataList moduleMetadata, Source bitcodeSource) {
            this.moduleMetadata = moduleMetadata;
            this.bitcodeSource = bitcodeSource;
            this.parsedVariables = new HashMap<>();
            typeIdentifier = new DITypeIdentifier();
            typeIdentifier.setMetadata(moduleMetadata);
            scopeExtractor = new DIScopeExtractor(typeIdentifier);
            typeExtractor = new DITypeExtractor(scopeExtractor, typeIdentifier);
            sourceModel = new SourceModel();
        }

        private LLVMSourceSymbol getSourceVariable(MDBaseNode mdVariable, boolean isGlobal) {
            if (parsedVariables.containsKey(mdVariable)) {
                return parsedVariables.get(mdVariable);
            }

            LLVMSourceLocation location = scopeExtractor.resolve(mdVariable);
            final LLVMSourceType type = typeExtractor.parseType(mdVariable);
            final String varName = MDNameExtractor.getName(mdVariable);

            final LLVMSourceSymbol variable = new LLVMSourceSymbol(varName, location, type, isGlobal);
            parsedVariables.put(mdVariable, variable);

            if (location != null) {
                // this is currently the line/column where the variable was declared, we want the
                // scope
                location = location.getParent();
            }

            if (location != null) {
                location.addSymbol(variable);
            }

            return variable;
        }

        @Override
        public void visit(FunctionDefinition function) {
            currentFunction = new Function(bitcodeSource, function);
            typeIdentifier.setMetadata(function.getMetadata());

            final MDBaseNode debugInfo = getDebugInfo(function);
            if (debugInfo != null) {
                final LLVMSourceLocation scope = scopeExtractor.resolve(debugInfo);
                currentFunction.setLexicalScope(scope);
            }

            function.accept(this);
            function.setSourceFunction(currentFunction);

            typeIdentifier.setMetadata(moduleMetadata);
            currentFunction = null;
        }

        private void visitGlobal(GlobalValueSymbol global) {
            final MDBaseNode mdGlobal = getDebugInfo(global);
            if (mdGlobal != null) {
                final LLVMSourceSymbol symbol = getSourceVariable(mdGlobal, true);
                if (symbol != null) {
                    sourceModel.globals.put(symbol, global);
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
            block.accept(this);
        }

        @Override
        public void defaultAction(Instruction instruction) {
            final MDLocation loc = instruction.getDebugLocation();
            if (loc != null) {
                final LLVMSourceLocation scope = scopeExtractor.resolve(loc);
                if (scope != null) {
                    currentFunction.instructions.put(instruction, scope);
                }
            }
        }

        @Override
        public void visit(VoidCallInstruction call) {
            final Symbol callTarget = call.getCallTarget();
            if (callTarget instanceof FunctionDeclaration) {
                int mdlocalArgumentIndex = -1;
                switch (((FunctionDeclaration) callTarget).getName()) {
                    case LLVM_DBG_DECLARE_NAME:
                        if (call.getArgumentCount() >= LLVM_DBG_DECLARE_LOCALREF_ARGINDEX) {
                            mdlocalArgumentIndex = LLVM_DBG_DECLARE_LOCALREF_ARGINDEX;
                        }
                        break;

                    case LLVM_DBG_VALUE_NAME:
                        if (call.getArgumentCount() >= LLVM_DBG_VALUE_LOCALREF_ARGINDEX) {
                            mdlocalArgumentIndex = LLVM_DBG_VALUE_LOCALREF_ARGINDEX;
                        }
                        break;
                }

                if (mdlocalArgumentIndex >= 0) {
                    handleDebugIntrinsic(call, mdlocalArgumentIndex);
                }
            }

            defaultAction(call);
        }

        private void handleDebugIntrinsic(VoidCallInstruction call, int mdlocalArgumentIndex) {
            Symbol value = call.getArgument(LLVM_DBG_INTRINSICS_VALUE_ARGINDEX);
            if (value instanceof MetadataConstant) {
                // the first argument should reference the allocation site of the variable
                final long mdIndex = ((MetadataConstant) value).getValue();
                value = MDSymbolExtractor.getSymbol(currentFunction.definition.getMetadata().getMDRef(mdIndex));

            } else {
                return;
            }

            if (value == null) {
                // this may happen if llvm optimizations removed a variable
                value = new NullConstant(MetaType.DEBUG);

            } else if (value instanceof ValueInstruction) {
                ((ValueInstruction) value).setSourceVariable(true);

            } else if (value instanceof FunctionParameter) {
                ((FunctionParameter) value).setSourceVariable(true);
            }

            final Symbol mdLocalMDRef = call.getArgument(mdlocalArgumentIndex);
            if (mdLocalMDRef instanceof MetadataConstant) {
                final long mdIndex = ((MetadataConstant) mdLocalMDRef).getValue();
                final MDBaseNode mdLocal = currentFunction.definition.getMetadata().getMDRef(mdIndex);

                final LLVMSourceSymbol variable = getSourceVariable(mdLocal, false);
                final Variable var = new Variable(value, variable);

                // ensure that lifetime analysis does not kill the variable before it is used in
                // the call
                call.replace(call.getArgument(LLVM_DBG_INTRINSICS_VALUE_ARGINDEX), value);
                call.replace(mdLocalMDRef, var);
            }
        }
    }
}
