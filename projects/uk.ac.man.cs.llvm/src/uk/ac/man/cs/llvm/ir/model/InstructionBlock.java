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

import java.util.ArrayList;
import java.util.List;

import uk.ac.man.cs.llvm.ir.InstructionGenerator;
import uk.ac.man.cs.llvm.ir.model.elements.AllocateInstruction;
import uk.ac.man.cs.llvm.ir.model.elements.BinaryOperationInstruction;
import uk.ac.man.cs.llvm.ir.model.elements.BranchInstruction;
import uk.ac.man.cs.llvm.ir.model.elements.CallInstruction;
import uk.ac.man.cs.llvm.ir.model.elements.CastInstruction;
import uk.ac.man.cs.llvm.ir.model.elements.CompareInstruction;
import uk.ac.man.cs.llvm.ir.model.elements.ConditionalBranchInstruction;
import uk.ac.man.cs.llvm.ir.model.elements.ExtractElementInstruction;
import uk.ac.man.cs.llvm.ir.model.elements.ExtractValueInstruction;
import uk.ac.man.cs.llvm.ir.model.elements.GetElementPointerInstruction;
import uk.ac.man.cs.llvm.ir.model.elements.IndirectBranchInstruction;
import uk.ac.man.cs.llvm.ir.model.elements.InsertElementInstruction;
import uk.ac.man.cs.llvm.ir.model.elements.InsertValueInstruction;
import uk.ac.man.cs.llvm.ir.model.elements.Instruction;
import uk.ac.man.cs.llvm.ir.model.elements.LoadInstruction;
import uk.ac.man.cs.llvm.ir.model.elements.PhiInstruction;
import uk.ac.man.cs.llvm.ir.model.elements.ReturnInstruction;
import uk.ac.man.cs.llvm.ir.model.elements.SelectInstruction;
import uk.ac.man.cs.llvm.ir.model.elements.ShuffleVectorInstruction;
import uk.ac.man.cs.llvm.ir.model.elements.StoreInstruction;
import uk.ac.man.cs.llvm.ir.model.elements.SwitchInstruction;
import uk.ac.man.cs.llvm.ir.model.elements.SwitchOldInstruction;
import uk.ac.man.cs.llvm.ir.model.elements.UnreachableInstruction;
import uk.ac.man.cs.llvm.ir.model.elements.ValueInstruction;
import uk.ac.man.cs.llvm.ir.model.elements.VoidCallInstruction;
import uk.ac.man.cs.llvm.ir.types.MetaType;
import uk.ac.man.cs.llvm.ir.types.Type;

public final class InstructionBlock implements InstructionGenerator, ValueSymbol {

    private final FunctionDefinition function;

    private final int blockIndex;

    private final List<Instruction> instructions = new ArrayList<>();

    private String name = ValueSymbol.UNKNOWN;

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

    @Override
    public void createAllocation(Type type, int count, int align) {
        addInstruction(AllocateInstruction.fromSymbols(function.getSymbols(), type, count, align));
    }

    @Override
    public void createAtomicLoad(Type type, int source, int align, boolean isVolatile, long atomicOrdering, long synchronizationScope) {
        addInstruction(LoadInstruction.fromSymbols(function.getSymbols(), type, source, align, isVolatile, atomicOrdering, synchronizationScope));
    }

    @Override
    public void createAtomicStore(int destination, int source, int align, boolean isVolatile, long atomicOrdering, long synchronizationScope) {
        addInstruction(StoreInstruction.fromSymbols(function.getSymbols(), destination, source, align, isVolatile, atomicOrdering, synchronizationScope));
    }

    @Override
    public void createBinaryOperation(Type type, int opcode, int flags, int lhs, int rhs) {
        addInstruction(BinaryOperationInstruction.fromSymbols(function.getSymbols(), type, opcode, flags, lhs, rhs));
    }

    @Override
    public void createBranch(int block) {
        addInstruction(BranchInstruction.fromTarget(function.getBlock(block)));
    }

    @Override
    public void createBranch(int condition, int blockTrue, int blockFalse) {
        addInstruction(ConditionalBranchInstruction.fromSymbols(function.getSymbols(), condition, function.getBlock(blockTrue), function.getBlock(blockFalse)));
    }

    @Override
    public void createCall(Type type, int target, int[] arguments, long visibility, long linkage) {
        if (type == MetaType.VOID) {
            addInstruction(VoidCallInstruction.fromSymbols(function.getSymbols(), target, arguments, visibility, linkage));
        } else {
            addInstruction(CallInstruction.fromSymbols(function.getSymbols(), type, target, arguments, visibility, linkage));
        }
    }

    @Override
    public void createCast(Type type, int opcode, int value) {
        addInstruction(CastInstruction.fromSymbols(function.getSymbols(), type, opcode, value));
    }

    @Override
    public void createCompare(Type type, int opcode, int lhs, int rhs) {
        addInstruction(CompareInstruction.fromSymbols(function.getSymbols(), type, opcode, lhs, rhs));
    }

    @Override
    public void createExtractElement(Type type, int vector, int index) {
        addInstruction(ExtractElementInstruction.fromSymbols(function.getSymbols(), type, vector, index));
    }

    @Override
    public void createExtractValue(Type type, int aggregate, int index) {
        addInstruction(ExtractValueInstruction.fromSymbols(function.getSymbols(), type, aggregate, index));
    }

    @Override
    public void createGetElementPointer(Type type, int pointer, int[] indices, boolean isInbounds) {
        addInstruction(GetElementPointerInstruction.fromSymbols(function.getSymbols(), type, pointer, indices, isInbounds));
    }

    @Override
    public void createIndirectBranch(int address, int[] successors) {
        addInstruction(IndirectBranchInstruction.generate(function, address, successors));
    }

    @Override
    public void createInsertElement(Type type, int vector, int index, int value) {
        addInstruction(InsertElementInstruction.fromSymbols(function.getSymbols(), type, vector, index, value));
    }

    @Override
    public void createInsertValue(Type type, int aggregate, int index, int value) {
        addInstruction(InsertValueInstruction.fromSymbols(function.getSymbols(), type, aggregate, index, value));
    }

    @Override
    public void createLoad(Type type, int source, int align, boolean isVolatile) {
        addInstruction(LoadInstruction.fromSymbols(function.getSymbols(), type, source, align, isVolatile));
    }

    @Override
    public void createPhi(Type type, int[] values, int[] blocks) {
        addInstruction(PhiInstruction.generate(function, type, values, blocks));
    }

    @Override
    public void createReturn() {
        addInstruction(ReturnInstruction.generate());
    }

    @Override
    public void createReturn(int value) {
        addInstruction(ReturnInstruction.generate(function.getSymbols(), value));
    }

    @Override
    public void createSelect(Type type, int condition, int trueValue, int falseValue) {
        addInstruction(SelectInstruction.fromSymbols(function.getSymbols(), type, condition, trueValue, falseValue));
    }

    @Override
    public void createShuffleVector(Type type, int vector1, int vector2, int mask) {
        addInstruction(ShuffleVectorInstruction.fromSymbols(function.getSymbols(), type, vector1, vector2, mask));
    }

    @Override
    public void createStore(int destination, int source, int align, boolean isVolatile) {
        addInstruction(StoreInstruction.fromSymbols(function.getSymbols(), destination, source, align, isVolatile));
    }

    @Override
    public void createSwitch(int condition, int defaultBlock, int[] caseValues, int[] caseBlocks) {
        addInstruction(SwitchInstruction.generate(function, condition, defaultBlock, caseValues, caseBlocks));
    }

    @Override
    public void createSwitchOld(int condition, int defaultBlock, long[] caseConstants, int[] caseBlocks) {
        addInstruction(SwitchOldInstruction.generate(function, condition, defaultBlock, caseConstants, caseBlocks));
    }

    @Override
    public void createUnreachable() {
        addInstruction(UnreachableInstruction.generate());
    }

    @Override
    public void enterBlock(long id) {
    }

    @Override
    public void exitBlock() {
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
        return MetaType.VOID;
    }

    @Override
    public void setName(String name) {
        this.name = "%" + name;
    }

    @Override
    public String toString() {
        return "InstructionBlock [function=" + function.getName() + ", blockIndex=" + blockIndex + ", instructionCount=" + instructions.size() + ", name=" + name + "]";
    }
}
