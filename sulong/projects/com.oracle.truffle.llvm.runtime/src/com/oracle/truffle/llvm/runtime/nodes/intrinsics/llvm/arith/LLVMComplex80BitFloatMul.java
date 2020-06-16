/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.arith;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import com.oracle.truffle.llvm.runtime.floating.LLVM80BitFloat;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.nodes.memory.store.LLVM80BitFloatStoreNode;
import com.oracle.truffle.llvm.runtime.nodes.memory.store.LLVM80BitFloatStoreNodeGen;
import com.oracle.truffle.llvm.runtime.pointer.LLVMPointer;
import com.oracle.truffle.llvm.runtime.types.PrimitiveType;
import com.oracle.truffle.llvm.runtime.types.Type.TypeOverflowException;

public abstract class LLVMComplex80BitFloatMul extends LLVMExpressionNode {

    @Child private LLVMExpressionNode aNode;
    @Child private LLVMExpressionNode bNode;
    @Child private LLVMExpressionNode cNode;
    @Child private LLVMExpressionNode dNode;
    @Child private LLVMExpressionNode alloc;

    @Child private LLVM80BitFloatStoreNode store;

    public LLVMComplex80BitFloatMul(LLVMExpressionNode alloc, LLVMExpressionNode a, LLVMExpressionNode b, LLVMExpressionNode c, LLVMExpressionNode d) {
        this.alloc = alloc;
        this.aNode = a;
        this.bNode = b;
        this.cNode = c;
        this.dNode = d;

        this.store = LLVM80BitFloatStoreNodeGen.create(null, null);
    }

    int getSizeInBytes() {
        try {
            long value = getDataLayout().getSize(PrimitiveType.X86_FP80);
            assert (int) value == value : "Size of X86_F80 does not fit into an int?";
            return (int) value;
        } catch (TypeOverflowException e) {
            // should not reach here
            throw new AssertionError(e);
        }
    }

    @Specialization
    public Object doMul(VirtualFrame frame,
                    @Cached("getSizeInBytes()") int sizeInBytes) {
        try {
            LLVM80BitFloat longDoubleA = (LLVM80BitFloat) aNode.executeGeneric(frame);
            LLVM80BitFloat longDoubleB = (LLVM80BitFloat) bNode.executeGeneric(frame);
            LLVM80BitFloat longDoubleC = (LLVM80BitFloat) cNode.executeGeneric(frame);
            LLVM80BitFloat longDoubleD = (LLVM80BitFloat) dNode.executeGeneric(frame);

            double a = longDoubleA.getDoubleValue();
            double b = longDoubleB.getDoubleValue();
            double c = longDoubleC.getDoubleValue();
            double d = longDoubleD.getDoubleValue();

            double ac = a * c;
            double bd = b * d;
            double ad = a * d;
            double bc = b * c;
            double zReal = ac - bd;
            double zImag = ad + bc;

            LLVMPointer allocatedMemory = alloc.executeLLVMPointer(frame);
            store.executeWithTarget(allocatedMemory, LLVM80BitFloat.fromDouble(zReal));
            store.executeWithTarget(allocatedMemory.increment(sizeInBytes), LLVM80BitFloat.fromDouble(zImag));

            return allocatedMemory;
        } catch (UnexpectedResultException | ClassCastException e) {
            CompilerDirectives.transferToInterpreter();
            throw new IllegalStateException(e);
        }
    }
}
