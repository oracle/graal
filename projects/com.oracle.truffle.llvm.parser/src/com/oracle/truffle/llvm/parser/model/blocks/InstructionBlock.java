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
package com.oracle.truffle.llvm.parser.model.blocks;

import java.util.ArrayList;
import java.util.List;

import com.oracle.truffle.llvm.parser.model.functions.FunctionDefinition;
import com.oracle.truffle.llvm.parser.model.symbols.Symbols;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.AllocateInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.BinaryOperationInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.BranchInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.CallInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.CastInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.CompareInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.ConditionalBranchInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.ExtractElementInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.ExtractValueInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.GetElementPointerInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.IndirectBranchInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.InsertElementInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.InsertValueInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.Instruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.LoadInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.PhiInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.ReturnInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.SelectInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.ShuffleVectorInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.StoreInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.SwitchInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.SwitchOldInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.UnreachableInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.ValueInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.VoidCallInstruction;
import com.oracle.truffle.llvm.parser.model.visitors.InstructionVisitor;
import com.oracle.truffle.llvm.runtime.types.Type;
import com.oracle.truffle.llvm.runtime.types.VoidType;
import com.oracle.truffle.llvm.runtime.types.symbols.LLVMIdentifier;
import com.oracle.truffle.llvm.runtime.types.symbols.ValueSymbol;

public final class InstructionBlock implements ValueSymbol {

    private final FunctionDefinition function;

    private final int blockIndex;

    private final List<Instruction> instructions = new ArrayList<>();

    private String name = LLVMIdentifier.UNKNOWN;

    public InstructionBlock(FunctionDefinition function, int index) {
        this.function = function;
        this.blockIndex = index;
    }

    public void accept(InstructionVisitor visitor) {
        for (Instruction instruction : instructions) {
            instruction.accept(visitor);
        }
    }

    private void addInstruction(Instruction element) {
        if (element instanceof ValueInstruction) {
            function.getSymbols().addSymbol(element);
        }
        instructions.add(element);
    }

    public void createAllocation(Type type, int count, int align) {
        addInstruction(AllocateInstruction.fromSymbols(function.getSymbols(), type, count, align));
    }

    public void createAtomicLoad(Type type, int source, int align, boolean isVolatile, long atomicOrdering, long synchronizationScope) {
        addInstruction(LoadInstruction.fromSymbols(function.getSymbols(), type, source, align, isVolatile, atomicOrdering, synchronizationScope));
    }

    public void createAtomicStore(int destination, int source, int align, boolean isVolatile, long atomicOrdering, long synchronizationScope) {
        addInstruction(StoreInstruction.fromSymbols(function.getSymbols(), destination, source, align, isVolatile, atomicOrdering, synchronizationScope));
    }

    public void createBinaryOperation(Type type, int opcode, int flags, int lhs, int rhs) {
        addInstruction(BinaryOperationInstruction.fromSymbols(function.getSymbols(), type, opcode, flags, lhs, rhs));
    }

    public void createBranch(int block) {
        addInstruction(BranchInstruction.fromTarget(function.getBlock(block)));
    }

    public void createBranch(int condition, int blockTrue, int blockFalse) {
        addInstruction(ConditionalBranchInstruction.fromSymbols(function.getSymbols(), condition, function.getBlock(blockTrue), function.getBlock(blockFalse)));
    }

    public void createCall(Type type, int target, int[] arguments, long visibility, long linkage) {
        if (type == VoidType.INSTANCE) {
            addInstruction(VoidCallInstruction.fromSymbols(function.getSymbols(), target, arguments, visibility, linkage));
        } else {
            addInstruction(CallInstruction.fromSymbols(function.getSymbols(), type, target, arguments, visibility, linkage));
        }
    }

    public void createCast(Type type, int opcode, int value) {
        addInstruction(CastInstruction.fromSymbols(function.getSymbols(), type, opcode, value));
    }

    public void createCompare(Type type, int opcode, int lhs, int rhs) {
        addInstruction(CompareInstruction.fromSymbols(function.getSymbols(), type, opcode, lhs, rhs));
    }

    public void createExtractElement(Type type, int vector, int index) {
        addInstruction(ExtractElementInstruction.fromSymbols(function.getSymbols(), type, vector, index));
    }

    public void createExtractValue(Type type, int aggregate, int index) {
        addInstruction(ExtractValueInstruction.fromSymbols(function.getSymbols(), type, aggregate, index));
    }

    public void createGetElementPointer(Type type, int pointer, int[] indices, boolean isInbounds) {
        addInstruction(GetElementPointerInstruction.fromSymbols(function.getSymbols(), type, pointer, indices, isInbounds));
    }

    public void createIndirectBranch(int address, int[] successors) {
        addInstruction(IndirectBranchInstruction.generate(function, address, successors));
    }

    public void createInsertElement(Type type, int vector, int index, int value) {
        addInstruction(InsertElementInstruction.fromSymbols(function.getSymbols(), type, vector, index, value));
    }

    public void createInsertValue(Type type, int aggregate, int index, int value) {
        addInstruction(InsertValueInstruction.fromSymbols(function.getSymbols(), type, aggregate, index, value));
    }

    public void createLoad(Type type, int source, int align, boolean isVolatile) {
        addInstruction(LoadInstruction.fromSymbols(function.getSymbols(), type, source, align, isVolatile));
    }

    public void createPhi(Type type, int[] values, int[] blocks) {
        addInstruction(PhiInstruction.generate(function, type, values, blocks));
    }

    public void createReturn() {
        addInstruction(ReturnInstruction.generate());
    }

    public void createReturn(int value) {
        addInstruction(ReturnInstruction.generate(function.getSymbols(), value));
    }

    public void createSelect(Type type, int condition, int trueValue, int falseValue) {
        addInstruction(SelectInstruction.fromSymbols(function.getSymbols(), type, condition, trueValue, falseValue));
    }

    public void createShuffleVector(Type type, int vector1, int vector2, int mask) {
        addInstruction(ShuffleVectorInstruction.fromSymbols(function.getSymbols(), type, vector1, vector2, mask));
    }

    public void createStore(int destination, int source, int align, boolean isVolatile) {
        addInstruction(StoreInstruction.fromSymbols(function.getSymbols(), destination, source, align, isVolatile));
    }

    public void createSwitch(int condition, int defaultBlock, int[] caseValues, int[] caseBlocks) {
        addInstruction(SwitchInstruction.generate(function, condition, defaultBlock, caseValues, caseBlocks));
    }

    public void createSwitchOld(int condition, int defaultBlock, long[] caseConstants, int[] caseBlocks) {
        addInstruction(SwitchOldInstruction.generate(function, condition, defaultBlock, caseConstants, caseBlocks));
    }

    public void createUnreachable() {
        addInstruction(UnreachableInstruction.generate());
    }

    public int getBlockIndex() {
        return blockIndex;
    }

    @Override
    public String getName() {
        return name;
    }

    public Instruction getInstruction(int index) {
        return instructions.get(index);
    }

    public Symbols getFunctionSymbols() {
        return function.getSymbols();
    }

    public int getInstructionCount() {
        return instructions.size();
    }

    @Override
    public Type getType() {
        return VoidType.INSTANCE;
    }

    @Override
    public void setName(String name) {
        this.name = LLVMIdentifier.toBlockName(name);
    }

    public void setImplicitName(int label) {
        this.name = LLVMIdentifier.toImplicitBlockName(label);
    }

    @Override
    public String toString() {
        return "InstructionBlock [function=" + function.getName() + ", blockIndex=" + blockIndex + ", instructionCount=" + instructions.size() + ", name=" + name + "]";
    }
}
