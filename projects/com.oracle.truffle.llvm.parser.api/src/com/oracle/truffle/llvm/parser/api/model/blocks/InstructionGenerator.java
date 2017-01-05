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
package com.oracle.truffle.llvm.parser.api.model.blocks;

import com.oracle.truffle.llvm.runtime.types.Type;

public interface InstructionGenerator {

    void createAllocation(Type type, int count, int align);

    void createAtomicLoad(Type type, int source, int align, boolean isVolatile, long atomicOrdering, long synchronizationScope);

    void createAtomicStore(int destination, int source, int align, boolean isVolatile, long atomicOrdering, long synchronizationScope);

    void createBinaryOperation(Type type, int opcode, int flags, int lhs, int rhs);

    void createBranch(int block);

    void createBranch(int condition, int blockTrue, int blockFalse);

    void createCall(Type type, int target, int[] arguments, long visibility, long linkage);

    void createCast(Type type, int opcode, int value);

    void createCompare(Type type, int opcode, int lhs, int rhs);

    void createExtractElement(Type type, int vector, int index);

    void createExtractValue(Type type, int aggregate, int index);

    void createGetElementPointer(Type type, int pointer, int[] indices, boolean isInbounds);

    void createIndirectBranch(int address, int[] successors);

    void createInsertElement(Type type, int vector, int index, int value);

    void createInsertValue(Type type, int aggregate, int index, int value);

    void createLoad(Type type, int source, int align, boolean isVolatile);

    void createPhi(Type type, int[] values, int[] blocks);

    void createReturn();

    void createReturn(int value);

    void createSelect(Type type, int condition, int trueValue, int falseValue);

    void createShuffleVector(Type type, int vector1, int vector2, int mask);

    void createStore(int destination, int source, int align, boolean isVolatile);

    void createSwitch(int condition, int defaultBlock, int[] caseValues, int[] caseBlocks);

    void createSwitchOld(int condition, int defaultBlock, long[] caseConstants, int[] caseBlocks);

    void createUnreachable();

    void enterBlock(long id);

    void exitBlock();
}
