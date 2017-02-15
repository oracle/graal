/*
 * Copyright (c) 2016, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.parser.model;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import com.oracle.truffle.llvm.parser.model.functions.FunctionDeclaration;
import com.oracle.truffle.llvm.parser.model.functions.FunctionDefinition;
import com.oracle.truffle.llvm.parser.model.generators.FunctionGenerator;
import com.oracle.truffle.llvm.parser.model.generators.ModuleGenerator;
import com.oracle.truffle.llvm.parser.model.globals.GlobalAlias;
import com.oracle.truffle.llvm.parser.model.globals.GlobalConstant;
import com.oracle.truffle.llvm.parser.model.globals.GlobalValueSymbol;
import com.oracle.truffle.llvm.parser.model.globals.GlobalVariable;
import com.oracle.truffle.llvm.parser.model.symbols.Symbols;
import com.oracle.truffle.llvm.parser.model.symbols.constants.BinaryOperationConstant;
import com.oracle.truffle.llvm.parser.model.symbols.constants.BlockAddressConstant;
import com.oracle.truffle.llvm.parser.model.symbols.constants.CastConstant;
import com.oracle.truffle.llvm.parser.model.symbols.constants.CompareConstant;
import com.oracle.truffle.llvm.parser.model.symbols.constants.Constant;
import com.oracle.truffle.llvm.parser.model.symbols.constants.GetElementPointerConstant;
import com.oracle.truffle.llvm.parser.model.symbols.constants.InlineAsmConstant;
import com.oracle.truffle.llvm.parser.model.symbols.constants.NullConstant;
import com.oracle.truffle.llvm.parser.model.symbols.constants.StringConstant;
import com.oracle.truffle.llvm.parser.model.symbols.constants.UndefinedConstant;
import com.oracle.truffle.llvm.parser.model.symbols.constants.floatingpoint.FloatingPointConstant;
import com.oracle.truffle.llvm.parser.model.symbols.constants.integer.BigIntegerConstant;
import com.oracle.truffle.llvm.parser.model.symbols.constants.integer.IntegerConstant;
import com.oracle.truffle.llvm.parser.model.target.TargetDataLayout;
import com.oracle.truffle.llvm.parser.model.visitors.ModelVisitor;
import com.oracle.truffle.llvm.runtime.types.FloatingPointType;
import com.oracle.truffle.llvm.runtime.types.FunctionType;
import com.oracle.truffle.llvm.runtime.types.IntegerType;
import com.oracle.truffle.llvm.runtime.types.Type;
import com.oracle.truffle.llvm.runtime.types.metadata.MetadataBlock;
import com.oracle.truffle.llvm.runtime.types.symbols.Symbol;

public final class ModelModule implements ModuleGenerator {

    private final List<Type> types = new ArrayList<>();

    private final List<GlobalValueSymbol> globals = new ArrayList<>();

    private final List<FunctionDeclaration> declares = new ArrayList<>();

    private final List<FunctionDefinition> defines = new ArrayList<>();

    private final Symbols symbols = new Symbols();

    private final MetadataBlock metadata = new MetadataBlock();

    private int currentFunction = -1;

    private TargetDataLayout targetDataLayout = null;

    @Override
    public void createTargetDataLayout(TargetDataLayout layout) {
        targetDataLayout = layout;
    }

    public TargetDataLayout getTargetDataLayout() {
        return targetDataLayout;
    }

    public ModelModule() {
    }

    public void accept(ModelVisitor visitor) {
        if (targetDataLayout != null) {
            visitor.visit(targetDataLayout);
        }
        types.forEach(visitor::visit);
        for (GlobalValueSymbol variable : globals) {
            variable.accept(visitor);
        }
        defines.forEach(visitor::visit);
        declares.forEach(visitor::visit);
    }

    @Override
    public void createAlias(Type type, int aliasedValue, long linkage) {
        GlobalAlias alias = new GlobalAlias(type, aliasedValue, linkage);

        symbols.addSymbol(alias);
        globals.add(alias);
    }

    @Override
    public void createBinaryOperationExpression(Type type, int opcode, int lhs, int rhs) {
        symbols.addSymbol(BinaryOperationConstant.fromSymbols(symbols, type, opcode, lhs, rhs));
    }

    @Override
    public void createBlockAddress(Type type, int function, int block) {
        symbols.addSymbol(BlockAddressConstant.fromSymbols(symbols, type, function, block));
    }

    @Override
    public void createCastExpression(Type type, int opcode, int value) {
        symbols.addSymbol(CastConstant.fromSymbols(symbols, type, opcode, value));
    }

    @Override
    public void createCompareExpression(Type type, int opcode, int lhs, int rhs) {
        symbols.addSymbol(CompareConstant.fromSymbols(symbols, type, opcode, lhs, rhs));
    }

    @Override
    public void createFloatingPoint(Type type, long[] value) {
        symbols.addSymbol(FloatingPointConstant.create((FloatingPointType) type, value));
    }

    @Override
    public void createFromData(Type type, long[] data) {
        symbols.addSymbol(Constant.createFromData(type, data));
    }

    @Override
    public void creatFromString(Type type, String string, boolean isCString) {
        symbols.addSymbol(new StringConstant(type, string, isCString));
    }

    @Override
    public void createFromValues(Type type, int[] values) {
        symbols.addSymbol(Constant.createFromValues(type, symbols, values));
    }

    @Override
    public void createGetElementPointerExpression(Type type, int pointer, int[] indices, boolean isInbounds) {
        symbols.addSymbol(GetElementPointerConstant.fromSymbols(symbols, type, pointer, indices, isInbounds));
    }

    @Override
    public void createInlineASM(Type type, long[] args) {
        symbols.addSymbol(InlineAsmConstant.generate(type, args));
    }

    @Override
    public void createInteger(Type type, long value) {
        symbols.addSymbol(new IntegerConstant((IntegerType) type, value));
    }

    @Override
    public void createInteger(Type type, BigInteger value) {
        symbols.addSymbol(new BigIntegerConstant((IntegerType) type, value));
    }

    @Override
    public void createFunction(FunctionType type, boolean isPrototype) {
        if (isPrototype) {
            FunctionDeclaration function = new FunctionDeclaration(type);
            symbols.addSymbol(function);
            declares.add(function);
        } else {
            FunctionDefinition method = new FunctionDefinition(type, metadata);
            symbols.addSymbol(method);
            defines.add(method);
        }
    }

    @Override
    public void createNull(Type type) {
        symbols.addSymbol(new NullConstant(type));
    }

    @Override
    public void createType(Type type) {
        types.add(type);
    }

    @Override
    public void createUndefined(Type type) {
        symbols.addSymbol(new UndefinedConstant(type));
    }

    @Override
    public void createGlobal(Type type, boolean isConstant, int initialiser, int align, long linkage) {
        final GlobalValueSymbol global;
        if (isConstant) {
            global = GlobalConstant.create(type, initialiser, align, linkage);
        } else {
            global = GlobalVariable.create(type, initialiser, align, linkage);
        }
        symbols.addSymbol(global);
        globals.add(global);
    }

    @Override
    public void exitModule() {
        for (GlobalValueSymbol variable : globals) {
            variable.initialise(symbols);
        }
    }

    @Override
    public FunctionGenerator generateFunction() {
        while (++currentFunction < symbols.getSize()) {
            Symbol symbol = symbols.getSymbol(currentFunction);
            if (symbol instanceof FunctionDefinition) {
                FunctionDefinition function = (FunctionDefinition) symbol;
                function.getSymbols().addSymbols(symbols);
                return function;
            }
        }
        throw new RuntimeException("Trying to generate undefined function");
    }

    @Override
    public MetadataBlock getMetadata() {
        return metadata;
    }

    @Override
    public void nameBlock(int index, String name) {
    }

    @Override
    public void nameEntry(int index, String name) {
        symbols.setSymbolName(index, name);
    }

    @Override
    public void nameFunction(int index, int offset, String name) {
        symbols.setSymbolName(index, name);
    }

    @Override
    public String toString() {
        return "ModelModule [types=" + types + ", globals=" + globals + ", declares=" + declares + ", defines=" + defines + ", symbols=" + symbols + ", currentFunction=" + currentFunction + "]";
    }
}
