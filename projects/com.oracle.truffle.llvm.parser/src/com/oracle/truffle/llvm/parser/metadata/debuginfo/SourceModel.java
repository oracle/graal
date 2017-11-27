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
import com.oracle.truffle.llvm.parser.metadata.MDExpression;
import com.oracle.truffle.llvm.parser.metadata.MDGlobalVariable;
import com.oracle.truffle.llvm.parser.metadata.MDGlobalVariableExpression;
import com.oracle.truffle.llvm.parser.metadata.MDKind;
import com.oracle.truffle.llvm.parser.metadata.MDVoidNode;
import com.oracle.truffle.llvm.parser.metadata.MetadataSymbol;
import com.oracle.truffle.llvm.parser.metadata.MetadataValueList;
import com.oracle.truffle.llvm.parser.metadata.MDLocation;
import com.oracle.truffle.llvm.parser.metadata.MetadataAttachmentHolder;
import com.oracle.truffle.llvm.parser.model.ModelModule;
import com.oracle.truffle.llvm.parser.model.blocks.InstructionBlock;
import com.oracle.truffle.llvm.parser.model.functions.FunctionDeclaration;
import com.oracle.truffle.llvm.parser.model.functions.FunctionDefinition;
import com.oracle.truffle.llvm.parser.model.functions.FunctionParameter;
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
import com.oracle.truffle.llvm.parser.model.visitors.SymbolVisitor;
import com.oracle.truffle.llvm.runtime.debug.LLVMSourceStaticMemberType;
import com.oracle.truffle.llvm.runtime.debug.LLVMSourceSymbol;
import com.oracle.truffle.llvm.runtime.debug.LLVMSourceType;
import com.oracle.truffle.llvm.runtime.debug.scope.LLVMSourceFile;
import com.oracle.truffle.llvm.runtime.debug.scope.LLVMSourceLocation;
import com.oracle.truffle.llvm.runtime.types.MetaType;
import com.oracle.truffle.llvm.runtime.types.Type;
import com.oracle.truffle.llvm.parser.model.SymbolImpl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public final class SourceModel {

    public static final int LLVM_DBG_INTRINSICS_VALUE_ARGINDEX = 0;

    public static final String LLVM_DBG_DECLARE_NAME = "@llvm.dbg.declare";
    public static final int LLVM_DBG_DECLARE_ARGSIZE = 3;
    public static final int LLVM_DBG_DECLARE_LOCALREF_ARGINDEX = 1;
    public static final int LLVM_DBG_DECLARE_EXPR_ARGINDEX = 2;

    public static final String LLVM_DBG_VALUE_NAME = "@llvm.dbg.value";
    public static final int LLVM_DBG_VALUE_ARGSIZE = 4;
    public static final int LLVM_DBG_VALUE_LOCALREF_ARGINDEX = 2;
    public static final int LLVM_DBG_VALUE_EXPR_ARGINDEX = 3;

    public static final class Function {

        private final Source bitcodeSource;

        private final FunctionDefinition definition;

        private final Map<Instruction, LLVMSourceLocation> instructions = new HashMap<>();

        private final Map<LLVMSourceSymbol, Variable> locals = new HashMap<>();

        private LLVMSourceLocation lexicalScope;

        private Function(Source bitcodeSource, FunctionDefinition definition) {
            this.bitcodeSource = bitcodeSource;
            this.definition = definition;
            this.lexicalScope = null;
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

        Variable getLocal(LLVMSourceSymbol symbol) {
            if (locals.containsKey(symbol)) {
                return locals.get(symbol);
            }

            final Variable variable = new Variable(symbol);
            locals.put(symbol, variable);
            return variable;
        }

        public Set<Variable> getPartialValues() {
            return locals.values().stream().filter(Variable::hasFragments).collect(Collectors.toSet());
        }
    }

    public static final class Variable implements SymbolImpl {

        private final LLVMSourceSymbol variable;

        private List<ValueFragment> fragments;
        private boolean hasFullDefinition;

        private Variable(LLVMSourceSymbol variable) {
            this.variable = variable;
            this.fragments = null;
            this.hasFullDefinition = false;
        }

        public LLVMSourceSymbol getSymbol() {
            return variable;
        }

        public String getName() {
            return variable.getName();
        }

        public LLVMSourceType getSourceType() {
            return variable.getType();
        }

        public boolean hasFragments() {
            return fragments != null;
        }

        public int getFragmentIndex(int offset, int length) {
            if (fragments != null) {
                for (int i = 0; i < fragments.size(); i++) {
                    final ValueFragment fragment = fragments.get(i);
                    if (fragment.getOffset() == offset && fragment.getLength() == length) {
                        return i;
                    }
                }
            }
            return -1;
        }

        public List<ValueFragment> getFragments() {
            return fragments != null ? fragments : Collections.emptyList();
        }

        private void addFragment(ValueFragment fragment) {
            if (fragments == null) {
                fragments = new ArrayList<>();
            }

            if (!fragments.contains(fragment)) {
                fragments.add(fragment);
            }
        }

        private void processFragments() {
            if (fragments != null) {
                if (hasFullDefinition) {
                    addFragment(ValueFragment.create(0, (int) variable.getType().getSize()));
                }
                Collections.sort(fragments);
            }
        }

        @Override
        public Type getType() {
            return MetaType.DEBUG;
        }

        @Override
        public String toString() {
            return variable.getName();
        }

        private void addFullDefinition() {
            hasFullDefinition = true;
        }

        @Override
        public void replace(SymbolImpl oldValue, SymbolImpl newValue) {
        }

        @Override
        public void accept(SymbolVisitor visitor) {
            visitor.visit(this);
        }
    }

    public SourceModel() {
        globals = new HashMap<>();
        staticMembers = new HashMap<>();
    }

    public void process(ModelModule irModel, Source bitcodeSource, MetadataValueList metadata) {
        final Parser parser = new Parser(this, metadata, bitcodeSource);
        MDSymbolLinkUpgrade.perform(metadata);
        irModel.accept(parser);
    }

    private final Map<LLVMSourceSymbol, GlobalValueSymbol> globals;

    private final Map<LLVMSourceStaticMemberType, SymbolImpl> staticMembers;

    public Map<LLVMSourceSymbol, GlobalValueSymbol> getGlobals() {
        return Collections.unmodifiableMap(globals);
    }

    public Map<LLVMSourceStaticMemberType, SymbolImpl> getStaticMembers() {
        return Collections.unmodifiableMap(staticMembers);
    }

    private static final class Parser implements FunctionVisitor, InstructionVisitorAdapter, ModelVisitor {

        private static MDBaseNode getDebugInfo(MetadataAttachmentHolder holder) {
            if (holder.hasAttachedMetadata()) {
                return holder.getMetadataAttachment(MDKind.DBG_NAME);

            } else {
                return null;
            }
        }

        private final Map<MDBaseNode, LLVMSourceSymbol> parsedVariables;
        private final DIScopeExtractor scopeExtractor;
        private final DITypeExtractor typeExtractor;
        private final SourceModel sourceModel;
        private final Source bitcodeSource;

        private Function currentFunction = null;

        private Parser(SourceModel sourceModel, MetadataValueList metadata, Source bitcodeSource) {
            this.bitcodeSource = bitcodeSource;
            this.sourceModel = sourceModel;
            this.parsedVariables = new HashMap<>();
            this.scopeExtractor = new DIScopeExtractor(metadata);
            this.typeExtractor = new DITypeExtractor(scopeExtractor, metadata, sourceModel.staticMembers);
        }

        private LLVMSourceSymbol getSourceSymbol(MDBaseNode mdVariable, boolean isGlobal) {
            if (parsedVariables.containsKey(mdVariable)) {
                return parsedVariables.get(mdVariable);
            }

            LLVMSourceLocation location = scopeExtractor.resolve(mdVariable);
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

        @Override
        public void visit(FunctionDeclaration function) {
        }

        @Override
        public void visit(FunctionDefinition function) {
            currentFunction = new Function(bitcodeSource, function);

            final MDBaseNode debugInfo = getDebugInfo(function);
            if (debugInfo != null) {
                final LLVMSourceLocation scope = scopeExtractor.resolve(debugInfo);
                currentFunction.setLexicalScope(scope);
            }

            function.accept((FunctionVisitor) this);
            function.setSourceFunction(currentFunction);

            for (Variable local : currentFunction.locals.values()) {
                local.processFragments();
            }

            scopeExtractor.clearLineScopes();
            currentFunction = null;
        }

        private void visitGlobal(GlobalValueSymbol global) {
            MDBaseNode mdGlobal = getDebugInfo(global);
            if (mdGlobal != null) {
                final LLVMSourceSymbol symbol = getSourceSymbol(mdGlobal, true);
                if (symbol != null) {
                    sourceModel.globals.put(symbol, global);
                }

                if (mdGlobal instanceof MDGlobalVariableExpression) {
                    mdGlobal = ((MDGlobalVariableExpression) mdGlobal).getGlobalVariable();
                }

                if (mdGlobal instanceof MDGlobalVariable) {
                    final MDBaseNode declaration = ((MDGlobalVariable) mdGlobal).getStaticMemberDeclaration();
                    if (declaration != MDVoidNode.INSTANCE) {
                        final LLVMSourceType sourceType = typeExtractor.parseType(declaration);
                        if (sourceType instanceof LLVMSourceStaticMemberType) {
                            sourceModel.staticMembers.put((LLVMSourceStaticMemberType) sourceType, global);
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
            block.accept(this);
        }

        @Override
        public void visitInstruction(Instruction instruction) {
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
            final SymbolImpl callTarget = call.getCallTarget();
            if (callTarget instanceof FunctionDeclaration) {
                int mdlocalArgIndex = -1;
                int mdExprArgIndex = -1;
                switch (((FunctionDeclaration) callTarget).getName()) {
                    case LLVM_DBG_DECLARE_NAME:
                        if (call.getArgumentCount() >= LLVM_DBG_DECLARE_ARGSIZE) {
                            mdlocalArgIndex = LLVM_DBG_DECLARE_LOCALREF_ARGINDEX;
                            mdExprArgIndex = LLVM_DBG_DECLARE_EXPR_ARGINDEX;
                        }
                        break;

                    case LLVM_DBG_VALUE_NAME:
                        if (call.getArgumentCount() >= LLVM_DBG_VALUE_ARGSIZE) {
                            mdlocalArgIndex = LLVM_DBG_VALUE_LOCALREF_ARGINDEX;
                            mdExprArgIndex = LLVM_DBG_VALUE_EXPR_ARGINDEX;
                        }
                        break;
                }

                if (mdlocalArgIndex >= 0) {
                    handleDebugIntrinsic(call, mdlocalArgIndex, mdExprArgIndex);
                }
            }

            visitInstruction(call);
        }

        private void handleDebugIntrinsic(VoidCallInstruction call, int mdlocalArgIndex, int mdExprArgIndex) {
            SymbolImpl value = call.getArgument(LLVM_DBG_INTRINSICS_VALUE_ARGINDEX);
            if (value instanceof MetadataSymbol) {
                // the first argument should reference the allocation site of the variable
                value = MDSymbolExtractor.getSymbol(((MetadataSymbol) value).getNode());

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

            final SymbolImpl mdLocalMDRef = call.getArgument(mdlocalArgIndex);
            final Variable variable;
            if (mdLocalMDRef instanceof MetadataSymbol) {
                final MDBaseNode mdLocal = ((MetadataSymbol) mdLocalMDRef).getNode();

                final LLVMSourceSymbol symbol = getSourceSymbol(mdLocal, false);
                variable = currentFunction.getLocal(symbol);

                // ensure that lifetime analysis does not kill the variable before it is used in
                // the call
                call.replace(call.getArgument(LLVM_DBG_INTRINSICS_VALUE_ARGINDEX), value);
                call.replace(mdLocalMDRef, variable);

            } else {
                variable = null;
            }

            final SymbolImpl expr = call.getArgument(mdExprArgIndex);
            if (expr instanceof MetadataSymbol) {
                final MDBaseNode exprNode = ((MetadataSymbol) expr).getNode();
                if (exprNode instanceof MDExpression) {
                    final MDExpression expression = (MDExpression) exprNode;
                    if (variable != null) {
                        if (ValueFragment.describesFragment(expression)) {
                            variable.addFragment(ValueFragment.parse(expression));
                        } else {
                            variable.addFullDefinition();
                        }
                    }
                }
            }
        }
    }
}
