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
import uk.ac.man.cs.llvm.ir.model.constants.InlineAsmConstant;
import uk.ac.man.cs.llvm.ir.model.constants.IntegerConstant;
import uk.ac.man.cs.llvm.ir.model.constants.NullConstant;
import uk.ac.man.cs.llvm.ir.model.constants.StringConstant;
import uk.ac.man.cs.llvm.ir.model.constants.UndefinedConstant;
import uk.ac.man.cs.llvm.ir.model.elements.Instruction;
import uk.ac.man.cs.llvm.ir.model.elements.ValueInstruction;
import uk.ac.man.cs.llvm.ir.types.FloatingPointType;
import uk.ac.man.cs.llvm.ir.types.FunctionType;
import uk.ac.man.cs.llvm.ir.types.IntegerType;
import uk.ac.man.cs.llvm.ir.types.PointerType;
import uk.ac.man.cs.llvm.ir.types.Type;

public final class FunctionDefinition extends FunctionType implements Constant, FunctionGenerator, ValueSymbol {

    private final Symbols symbols = new Symbols();

    private final List<FunctionParameter> parameters = new ArrayList<>();

    private InstructionBlock[] blocks = new InstructionBlock[0];

    private int currentBlock = 0;

    private String name = ValueSymbol.UNKNOWN;

    private MetadataBlock metadata;

    public FunctionDefinition(FunctionType type, MetadataBlock metadata) {
        super(type.getReturnType(), type.getArgumentTypes(), type.isVarArg());
        this.metadata = metadata;
    }

    public void accept(FunctionVisitor visitor) {
        for (InstructionBlock block : blocks) {
            visitor.visit(block);
        }
    }

    @Override
    public void allocateBlocks(int count) {
        // we don't want do add function specific metadata to the global scope
        metadata = new MetadataBlock(metadata);

        blocks = new InstructionBlock[count];
        for (int i = 0; i < count; i++) {
            blocks[i] = new InstructionBlock(this, i);
        }
        blocks[0].setName("0");
    }

    @Override
    public void createParameter(Type type) {
        FunctionParameter parameter = new FunctionParameter(type, parameters.size());
        symbols.addSymbol(parameter);
        parameters.add(parameter);
    }

    @Override
    public void exitFunction() {
        int valueSymbolIdentifier = 0;
        int blockIdentifier = 1; // Zero clashes with entry block in sulong

        // in K&R style function declarations the parameters are not assigned names
        for (final FunctionParameter parameter : parameters) {
            if (ValueSymbol.UNKNOWN.equals(parameter.getName())) {
                parameter.setName(String.valueOf(valueSymbolIdentifier++));
            }
        }

        for (final InstructionBlock block : blocks) {
            if (block.getName().equals(ValueSymbol.UNKNOWN)) {
                // compilers like to assign numbers as blocknames, we name unnamed blocks this way
                // to prevent name clashes
                block.setName(String.format("%s\"%d\"", ValueSymbol.UNKNOWN, blockIdentifier++));
            }
            for (int i = 0; i < block.getInstructionCount(); i++) {
                final Instruction instruction = block.getInstruction(i);
                if (instruction instanceof ValueInstruction) {
                    final ValueInstruction value = (ValueInstruction) instruction;
                    if (value.getName().equals(ValueSymbol.UNKNOWN)) {
                        value.setName(String.valueOf(valueSymbolIdentifier++));
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
    public void createFloatingPoint(Type type, long[] bits) {
        symbols.addSymbol(FloatingPointConstant.create((FloatingPointType) type, bits));
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
    public void createNull(Type type) {
        symbols.addSymbol(new NullConstant(type));
    }

    @Override
    public void createUndefined(Type type) {
        symbols.addSymbol(new UndefinedConstant(type));
    }

    @Override
    public MetadataBlock getMetadata() {
        return metadata;
    }

    @Override
    public String toString() {
        return "FunctionDefinition [symbolCount=" + symbols.getSize() + ", parameters=" + parameters + ", blocks=" + blocks.length + ", currentBlock=" + currentBlock + ", name=" + name + "]";
    }
}
