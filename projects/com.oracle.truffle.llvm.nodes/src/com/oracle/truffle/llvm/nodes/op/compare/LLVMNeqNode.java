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
package com.oracle.truffle.llvm.nodes.op.compare;

import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.NodeChildren;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.llvm.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.LLVMAddress;
import com.oracle.truffle.llvm.runtime.LLVMFunctionDescriptor;
import com.oracle.truffle.llvm.runtime.LLVMIVarBit;

@NodeChildren({@NodeChild(type = LLVMExpressionNode.class), @NodeChild(type = LLVMExpressionNode.class)})
public abstract class LLVMNeqNode extends LLVMExpressionNode {

    @Specialization
    public boolean executeI1(boolean val1, boolean val2) {
        return val1 != val2;
    }

    @Specialization
    public boolean executeI1(byte val1, byte val2) {
        return val1 != val2;
    }

    @Specialization
    public boolean executeI1(short val1, short val2) {
        return val1 != val2;
    }

    @Specialization
    public boolean executeI1(int val1, int val2) {
        return val1 != val2;
    }

    @Specialization
    public boolean executeI1(long val1, long val2) {
        return val1 != val2;
    }

    @Specialization
    public boolean executeI1(boolean val1, LLVMAddress val2) {
        return (val1 ? 1 : 0) != val2.getVal();
    }

    @Specialization
    public boolean executeI1(long val1, LLVMAddress val2) {
        return val1 != val2.getVal();
    }

    @Specialization
    public boolean executeI1(LLVMIVarBit val1, LLVMIVarBit val2) {
        return val1.compare(val2) != 0;
    }

    @Specialization
    public boolean executeI1(LLVMAddress val1, LLVMAddress val2) {
        return !val1.equals(val2);
    }

    @Specialization(guards = {"!isLLVMFunctionDescriptor(val1)", "!isLLVMFunctionDescriptor(val2)"})
    public boolean executeI1(TruffleObject val1, TruffleObject val2) {
        return val1 != val2;
    }

    @SuppressWarnings("unused")
    @Specialization
    public boolean executeI1(TruffleObject val1, boolean val2) {
        return true;
    }

    @SuppressWarnings("unused")
    @Specialization
    public boolean executeI1(LLVMAddress val1, TruffleObject val2) {
        return true;
    }

    @SuppressWarnings("unused")
    @Specialization
    public boolean executeI1(TruffleObject val1, LLVMAddress val2) {
        return true;
    }

    @Specialization
    public boolean executeI1(LLVMFunctionDescriptor val1, LLVMFunctionDescriptor val2) {
        return val1.getFunctionIndex() != val2.getFunctionIndex();
    }

    protected static boolean isLLVMFunctionDescriptor(TruffleObject object) {
        return object instanceof LLVMFunctionDescriptor;
    }

}
