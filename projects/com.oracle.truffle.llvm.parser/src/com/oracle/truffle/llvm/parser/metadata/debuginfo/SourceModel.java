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

import com.oracle.truffle.llvm.parser.metadata.MDBaseNode;
import com.oracle.truffle.llvm.parser.metadata.MDGlobalVariable;
import com.oracle.truffle.llvm.parser.metadata.MetadataVisitor;
import com.oracle.truffle.llvm.parser.model.ModelModule;
import com.oracle.truffle.llvm.parser.model.blocks.InstructionBlock;
import com.oracle.truffle.llvm.parser.model.functions.FunctionDeclaration;
import com.oracle.truffle.llvm.parser.model.functions.FunctionDefinition;
import com.oracle.truffle.llvm.parser.model.symbols.constants.MetadataConstant;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.ValueInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.VoidCallInstruction;
import com.oracle.truffle.llvm.parser.model.visitors.FunctionVisitor;
import com.oracle.truffle.llvm.parser.model.visitors.InstructionVisitorAdapter;
import com.oracle.truffle.llvm.parser.model.visitors.ModelVisitor;
import com.oracle.truffle.llvm.runtime.debug.LLVMDebugType;
import com.oracle.truffle.llvm.runtime.types.symbols.Symbol;

import java.util.HashMap;
import java.util.Map;

public final class SourceModel {

    public static SourceModel generate(ModelModule irModel) {
        final Parser parser = new Parser();
        irModel.getMetadata().accept(parser.mdGlobalVisitor);
        irModel.accept(parser);
        return parser.sourceModel;
    }

    public static final class Function {

        private final FunctionDefinition definition;

        private final Map<Symbol, Variable> locals = new HashMap<>();

        private Function(FunctionDefinition definition) {
            this.definition = definition;
        }

        public FunctionDefinition getDefinition() {
            return definition;
        }

        public Variable findVariable(Symbol symbol) {
            return locals.get(symbol);
        }

        public String getFrameSlotName(ValueInstruction symbol) {
            if (locals.containsKey(symbol)) {
                return locals.get(symbol).getName();
            } else {
                return symbol.getName();
            }
        }
    }

    public static final class Variable {

        public static final String INVALID_NAME = MDNameExtractor.DEFAULT_STRING;

        private final String name;

        private final Symbol symbol;

        private final LLVMDebugType type;

        private Variable(String name, Symbol symbol, LLVMDebugType type) {
            this.name = name;
            this.symbol = symbol;
            this.type = type;
        }

        public String getName() {
            return name;
        }

        public Symbol getSymbol() {
            return symbol;
        }

        public LLVMDebugType getType() {
            return type;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    private final Map<FunctionDefinition, Function> functions = new HashMap<>();

    private final Map<Symbol, Variable> globals = new HashMap<>();

    private SourceModel() {
    }

    public Function getFunction(FunctionDefinition functionDefinition) {
        if (functions.containsKey(functionDefinition)) {
            return functions.get(functionDefinition);

        } else {
            Function newFunction = new Function(functionDefinition);
            functions.put(functionDefinition, newFunction);
            return newFunction;
        }
    }

    public Map<Symbol, Variable> getGlobals() {
        return globals;
    }

    private static final class Parser implements ModelVisitor, MetadataVisitor, FunctionVisitor, InstructionVisitorAdapter {

        private final SourceModel sourceModel;

        private Function currentFunction = null;

        private final MDTypeExtractor typeExtractor = new MDTypeExtractor();

        private Parser() {
            sourceModel = new SourceModel();
        }

        @Override
        public void visit(FunctionDefinition function) {
            currentFunction = new Function(function);
            sourceModel.functions.put(function, currentFunction);
            function.accept(this);
            currentFunction = null;
        }

        @Override
        public void visit(InstructionBlock block) {
            block.accept(this);
        }

        @Override
        public void visit(VoidCallInstruction call) {
            if (!(call.getCallTarget() instanceof FunctionDeclaration && "@llvm.dbg.declare".equals(((FunctionDeclaration) call.getCallTarget()).getName()) && call.getArgumentCount() >= 2)) {
                return;
            }

            Symbol alloca = call.getArgument(0);
            if (alloca instanceof MetadataConstant) {
                // the first argument should reference the allocation site of the variable
                final long mdIndex = ((MetadataConstant) alloca).getValue();
                alloca = MDSymbolExtractor.getSymbol(currentFunction.definition.getMetadata().getMDRef(mdIndex));
            }

            if (alloca instanceof ValueInstruction) {
                Symbol mdLocalMDRef = call.getArgument(1);
                if (mdLocalMDRef instanceof MetadataConstant) {
                    final long mdIndex = ((MetadataConstant) mdLocalMDRef).getValue();
                    final MDBaseNode mdLocal = currentFunction.definition.getMetadata().getMDRef(mdIndex);
                    LLVMDebugType type = typeExtractor.parseType(mdLocal);
                    String varName = MDNameExtractor.getName(mdLocal);
                    Variable var = new Variable(varName, alloca, type);
                    ((ValueInstruction) alloca).setSourceVariable(var);
                    currentFunction.locals.put(alloca, var);
                }
            }
        }

        private final MDFollowRefVisitor mdGlobalVisitor = new MDFollowRefVisitor() {

            @Override
            public void visit(MDGlobalVariable mdGlobal) {
                String name = MDNameExtractor.getName(mdGlobal.getName());
                Symbol symbol = MDSymbolExtractor.getSymbol(mdGlobal.getVariable());
                LLVMDebugType type = typeExtractor.parseType(mdGlobal.getType());
                Variable globalVar = new Variable(name, symbol, type);
                sourceModel.globals.put(symbol, globalVar);
            }
        };
    }
}
