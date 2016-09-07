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
package uk.ac.man.cs.llvm.ir.model;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import uk.ac.man.cs.llvm.ir.FunctionGenerator;
import uk.ac.man.cs.llvm.ir.ModuleGenerator;

import uk.ac.man.cs.llvm.ir.model.constants.BigIntegerConstant;
import uk.ac.man.cs.llvm.ir.model.constants.BinaryOperationConstant;
import uk.ac.man.cs.llvm.ir.model.constants.BlockAddressConstant;
import uk.ac.man.cs.llvm.ir.model.constants.CastConstant;
import uk.ac.man.cs.llvm.ir.model.constants.CompareConstant;
import uk.ac.man.cs.llvm.ir.model.constants.Constant;
import uk.ac.man.cs.llvm.ir.model.constants.FloatingPointConstant;
import uk.ac.man.cs.llvm.ir.model.constants.GetElementPointerConstant;
import uk.ac.man.cs.llvm.ir.model.constants.InlineAsmConstant;
import uk.ac.man.cs.llvm.ir.model.constants.IntegerConstant;
import uk.ac.man.cs.llvm.ir.model.constants.NullConstant;
import uk.ac.man.cs.llvm.ir.model.constants.StringConstant;
import uk.ac.man.cs.llvm.ir.model.constants.UndefinedConstant;
import uk.ac.man.cs.llvm.ir.types.FloatingPointType;
import uk.ac.man.cs.llvm.ir.types.FunctionType;
import uk.ac.man.cs.llvm.ir.types.IntegerType;
import uk.ac.man.cs.llvm.ir.types.Type;

public final class ModelModule implements ModuleGenerator {

    private final List<Type> types = new ArrayList<>();

    private final List<GlobalValueSymbol> variables = new ArrayList<>();

    private final List<FunctionDeclaration> declares = new ArrayList<>();

    private final List<FunctionDefinition> defines = new ArrayList<>();

    private final Symbols symbols = new Symbols();

    private int currentFunction = -1;

    public ModelModule() {
    }

    public void accept(ModelVisitor visitor) {
        for (Type type : types) {
            visitor.visit(type);
        }
        for (GlobalValueSymbol variable : variables) {
            variable.accept(visitor);
        }
        for (FunctionDefinition define : defines) {
            visitor.visit(define);
        }
        for (FunctionDeclaration declare : declares) {
            visitor.visit(declare);
        }
    }

    @Override
    public void createAlias(Type type, int aliasedValue) {
        GlobalAlias alias = new GlobalAlias(type, aliasedValue);

        symbols.addSymbol(alias);
        variables.add(alias);
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
    public void createFloatingPoint(Type type, long value) {
        symbols.addSymbol(new FloatingPointConstant((FloatingPointType) type, value));
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
            FunctionDefinition method = new FunctionDefinition(type);
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
    public void createVariable(Type type, boolean isConstant, int initialiser, int align) {
        GlobalValueSymbol variable;
        if (isConstant) {
            variable = new GlobalConstant(type, initialiser, align);
        } else {
            variable = new GlobalVariable(type, initialiser, align);
        }
        symbols.addSymbol(variable);
        variables.add(variable);
    }

    @Override
    public void exitModule() {
        for (GlobalValueSymbol variable : variables) {
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
}
