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
import uk.ac.man.cs.llvm.ir.InstructionGenerator;
import uk.ac.man.cs.llvm.ir.model.constants.BigIntegerConstant;
import uk.ac.man.cs.llvm.ir.model.constants.BinaryOperationConstant;
import uk.ac.man.cs.llvm.ir.model.constants.BlockAddressConstant;
import uk.ac.man.cs.llvm.ir.model.constants.CastConstant;
import uk.ac.man.cs.llvm.ir.model.constants.CompareConstant;
import uk.ac.man.cs.llvm.ir.model.constants.Constant;
import uk.ac.man.cs.llvm.ir.model.constants.FloatingPointConstant;
import uk.ac.man.cs.llvm.ir.model.constants.GetElementPointerConstant;
import uk.ac.man.cs.llvm.ir.model.constants.IntegerConstant;
import uk.ac.man.cs.llvm.ir.model.constants.NullConstant;
import uk.ac.man.cs.llvm.ir.model.constants.StringConstant;
import uk.ac.man.cs.llvm.ir.model.constants.UndefinedConstant;
import uk.ac.man.cs.llvm.ir.model.elements.Instruction;
import uk.ac.man.cs.llvm.ir.model.elements.ValueInstruction;
import uk.ac.man.cs.llvm.ir.model.enums.BinaryOperator;
import uk.ac.man.cs.llvm.ir.model.enums.CastOperator;
import uk.ac.man.cs.llvm.ir.model.enums.CompareOperator;
import uk.ac.man.cs.llvm.ir.types.FloatingPointType;
import uk.ac.man.cs.llvm.ir.types.FunctionType;
import uk.ac.man.cs.llvm.ir.types.IntegerType;
import uk.ac.man.cs.llvm.ir.types.PointerType;
import uk.ac.man.cs.llvm.ir.types.Type;
import uk.ac.man.cs.llvm.ir.types.VectorType;

public final class FunctionDefinition extends FunctionType implements Constant, FunctionGenerator, ValueSymbol {

    private final Symbols symbols = new Symbols();

    private final List<FunctionParameter> parameters = new ArrayList<>();

    private InstructionBlock[] blocks = new InstructionBlock[0];

    private int currentBlock = 0;

    private String name = ValueSymbol.UNKNOWN;

    public FunctionDefinition(FunctionType type) {
        super(type.getReturnType(), type.getArgumentTypes(), type.isVarArg());
    }

    public void accept(FunctionVisitor visitor) {
        for (InstructionBlock block : blocks) {
            visitor.visit(block);
        }
    }

    @Override
    public void allocateBlocks(int count) {
        blocks = new InstructionBlock[count];
        for (int i = 0; i < count; i++) {
            blocks[i] = new InstructionBlock(this, i);
        }
        blocks[0].setName("");
    }

    @Override
    public void createParameter(Type type) {
        FunctionParameter parameter = new FunctionParameter(type, parameters.size());
        symbols.addSymbol(parameter);
        parameters.add(parameter);
    }

    @Override
    public void exitFunction() {
        int identifier = 1; // Zero clashes with entry block in sulong
        for (InstructionBlock block : blocks) {
            if (block.getName().equals(ValueSymbol.UNKNOWN)) {
                block.setName(String.valueOf(identifier++));
            }
            for (int i = 0; i < block.getInstructionCount(); i++) {
                Instruction instruction = block.getInstruction(i);
                if (instruction instanceof ValueInstruction) {
                    ValueInstruction value = (ValueInstruction) instruction;
                    if (value.getName().equals(ValueSymbol.UNKNOWN)) {
                        value.setName(String.valueOf(identifier++));
                    }
                }
            }
        }
    }

    @Override
    public InstructionGenerator generateBlock() {
        return blocks[currentBlock++];
    }

    public InstructionBlock getBlock(long idx) {
        return blocks[(int) idx];
    }

    public int getBlockCount() {
        return blocks.length;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public Type getType() {
        return new PointerType(super.getType());
    }

    public List<FunctionParameter> getParameters() {
        return parameters;
    }

    public Symbols getSymbols() {
        return symbols;
    }

    @Override
    public void nameBlock(int index, String argName) {
        blocks[index].setName(argName);
    }

    @Override
    public void nameEntry(int index, String argName) {
        symbols.setSymbolName(index, argName);
    }

    @Override
    public void nameFunction(int index, int offset, String argName) {
        symbols.setSymbolName(index, argName);
    }

    @Override
    public void setName(String name) {
        this.name = "@" + name;
    }

    @Override
    public void createBinaryOperationExpression(Type type, int opcode, int lhs, int rhs) {
        boolean isFloatingPoint = type instanceof FloatingPointType || (type instanceof VectorType && ((VectorType) type).getElementType() instanceof FloatingPointType);

        symbols.addSymbol(new BinaryOperationConstant(
                        type,
                        BinaryOperator.decode(opcode, isFloatingPoint),
                        symbols.getSymbol(lhs),
                        symbols.getSymbol(rhs)));
    }

    @Override
    public void createBlockAddress(Type type, int method, int block) {
        symbols.addSymbol(new BlockAddressConstant(
                        type,
                        symbols.getSymbol(method),
                        getBlock(block)));
    }

    @Override
    public void createCastExpression(Type type, int opcode, int value) {
        CastConstant cast = new CastConstant(type, CastOperator.decode(opcode));

        cast.setValue(symbols.getSymbol(value, cast));

        symbols.addSymbol(cast);
    }

    @Override
    public void createCompareExpression(Type type, int opcode, int lhs, int rhs) {
        CompareConstant compare = new CompareConstant(type, CompareOperator.decode(opcode));

        compare.setLHS(symbols.getSymbol(lhs, compare));
        compare.setRHS(symbols.getSymbol(rhs, compare));

        symbols.addSymbol(compare);
    }

    @Override
    public void createFloatingPoint(Type type, long bits) {
        symbols.addSymbol(new FloatingPointConstant((FloatingPointType) type, bits));
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
        symbols.addSymbol(Constant.createFromValues(type, symbols.getConstants(values)));
    }

    @Override
    public void createGetElementPointerExpression(Type type, int pointer, int[] indices, boolean isInbounds) {
        GetElementPointerConstant gep = new GetElementPointerConstant(type, isInbounds);

        gep.setBasePointer(symbols.getSymbol(pointer, gep));
        for (int index : indices) {
            gep.addIndex(symbols.getSymbol(index, gep));
        }

        symbols.addSymbol(gep);
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
    public void createNull(Type type) {
        symbols.addSymbol(new NullConstant(type));
    }

    @Override
    public void createUndefined(Type type) {
        symbols.addSymbol(new UndefinedConstant(type));
    }
}
