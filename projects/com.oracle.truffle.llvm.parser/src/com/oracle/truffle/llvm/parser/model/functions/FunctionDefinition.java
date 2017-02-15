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
package com.oracle.truffle.llvm.parser.model.functions;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.llvm.parser.model.blocks.InstructionBlock;
import com.oracle.truffle.llvm.parser.model.generators.FunctionGenerator;
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
import com.oracle.truffle.llvm.parser.model.symbols.instructions.Instruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.ValueInstruction;
import com.oracle.truffle.llvm.parser.model.visitors.FunctionVisitor;
import com.oracle.truffle.llvm.runtime.types.FloatingPointType;
import com.oracle.truffle.llvm.runtime.types.FunctionType;
import com.oracle.truffle.llvm.runtime.types.IntegerType;
import com.oracle.truffle.llvm.runtime.types.Type;
import com.oracle.truffle.llvm.runtime.types.metadata.MetadataBlock;
import com.oracle.truffle.llvm.runtime.types.symbols.ValueSymbol;

public final class FunctionDefinition extends FunctionType implements Constant, FunctionGenerator {

    private final Symbols symbols = new Symbols();

    private final List<FunctionParameter> parameters = new ArrayList<>();

    private InstructionBlock[] blocks = new InstructionBlock[0];

    private int currentBlock = 0;

    private MetadataBlock metadata;

    private final Map<String, Type> namesToTypes;

    public FunctionDefinition(FunctionType type, MetadataBlock metadata) {
        super(type.getReturnType(), type.getArgumentTypes(), type.isVarArg());
        this.metadata = metadata;
        namesToTypes = new HashMap<>();
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
    }

    @Override
    public void createParameter(Type type) {
        FunctionParameter parameter = new FunctionParameter(type, parameters.size());
        symbols.addSymbol(parameter);
        parameters.add(parameter);
    }

    @Override
    public void exitFunction() {
        int symbolIndex = 0;

        // in K&R style function declarations the parameters are not assigned names
        for (final FunctionParameter parameter : parameters) {
            if (ValueSymbol.UNKNOWN.equals(parameter.getName())) {
                parameter.setName(String.valueOf(symbolIndex++));
            }
            namesToTypes.put(parameter.getName(), parameter.getType());
        }

        final Set<String> explicitBlockNames = Arrays.stream(blocks).map(InstructionBlock::getName).filter(blockName -> !ValueSymbol.UNKNOWN.equals(blockName)).collect(Collectors.toSet());
        for (final InstructionBlock block : blocks) {
            if (block.getName().equals(ValueSymbol.UNKNOWN)) {
                do {
                    block.setName(String.valueOf(symbolIndex++));
                    // avoid name clashes
                } while (explicitBlockNames.contains(block.getName()));
            }
            for (int i = 0; i < block.getInstructionCount(); i++) {
                final Instruction instruction = block.getInstruction(i);
                if (instruction instanceof ValueInstruction) {
                    final ValueInstruction value = (ValueInstruction) instruction;
                    if (value.getName().equals(ValueSymbol.UNKNOWN)) {
                        value.setName(String.valueOf(symbolIndex++));
                    }
                    namesToTypes.put(value.getName(), value.getType());
                }
            }
        }
    }

    @Override
    public InstructionBlock generateBlock() {
        return blocks[currentBlock++];
    }

    public Type getType(String instructionName) {
        CompilerAsserts.neverPartOfCompilation();
        return namesToTypes.get(instructionName);
    }

    public InstructionBlock getBlock(long idx) {
        CompilerAsserts.neverPartOfCompilation();
        return blocks[(int) idx];
    }

    public int getBlockCount() {
        CompilerAsserts.neverPartOfCompilation();
        return blocks.length;
    }

    public List<InstructionBlock> getBlocks() {
        CompilerAsserts.neverPartOfCompilation();
        return Arrays.asList(blocks);
    }

    public List<FunctionParameter> getParameters() {
        CompilerAsserts.neverPartOfCompilation();
        return parameters;
    }

    public Symbols getSymbols() {
        CompilerAsserts.neverPartOfCompilation();
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
    public void createInlineASM(Type type, long[] asm) {
        symbols.addSymbol(InlineAsmConstant.generate(type, asm));
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
        CompilerAsserts.neverPartOfCompilation();
        return metadata;
    }

    @Override
    public int hashCode() {
        CompilerAsserts.neverPartOfCompilation();
        int hash = super.hashCode();
        hash = 43 * hash + ((parameters == null) ? 0 : parameters.hashCode());
        hash = 43 * hash + ((symbols == null) ? 0 : symbols.hashCode());
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        CompilerAsserts.neverPartOfCompilation();
        if (obj instanceof FunctionDefinition) {
            FunctionDefinition other = (FunctionDefinition) obj;
            return super.equals(other) && Objects.equals(parameters, other.parameters) && Objects.equals(symbols, other.symbols) && Arrays.equals(blocks, other.blocks) &&
                            currentBlock == other.currentBlock;
        }
        return false;
    }

    @Override
    public String toString() {
        CompilerAsserts.neverPartOfCompilation();
        return "FunctionDefinition [symbolCount=" + symbols.getSize() + ", parameters=" + parameters + ", blocks=" + blocks.length + ", currentBlock=" + currentBlock + ", name=" + getName() + "]";
    }
}
