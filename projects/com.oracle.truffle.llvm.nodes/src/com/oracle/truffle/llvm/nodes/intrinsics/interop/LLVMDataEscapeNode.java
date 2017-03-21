/*
 * Copyright (c) 2017, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.nodes.intrinsics.interop;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.llvm.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.ForeignBoxedPrimitive;
import com.oracle.truffle.llvm.runtime.LLVMAddress;
import com.oracle.truffle.llvm.runtime.LLVMFunction;
import com.oracle.truffle.llvm.runtime.LLVMGlobalVariableDescriptor;
import com.oracle.truffle.llvm.runtime.LLVMIVarBit;
import com.oracle.truffle.llvm.runtime.LLVMSharedGlobalVariableDescriptor;
import com.oracle.truffle.llvm.runtime.LLVMTruffleAddress;
import com.oracle.truffle.llvm.runtime.LLVMTruffleNull;
import com.oracle.truffle.llvm.runtime.LLVMTruffleObject;
import com.oracle.truffle.llvm.runtime.vector.LLVMDoubleVector;
import com.oracle.truffle.llvm.runtime.vector.LLVMFloatVector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI16Vector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI1Vector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI32Vector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI64Vector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI8Vector;

/**
 * Values that escape Sulong and flow to other languages must be primitive or TruffleObject. This
 * node ensures that.
 *
 */
@SuppressWarnings("unused")
public abstract class LLVMDataEscapeNode extends Node {

    public abstract Object executeWithTarget(Object escapingValue);

    @Specialization
    public Object escapingPrimitive(boolean escapingValue) {
        return escapingValue;
    }

    @Specialization
    public Object escapingPrimitive(byte escapingValue) {
        return escapingValue;
    }

    @Specialization
    public Object escapingPrimitive(short escapingValue) {
        return escapingValue;
    }

    @Specialization
    public Object escapingPrimitive(char escapingValue) {
        return escapingValue;
    }

    @Specialization
    public Object escapingPrimitive(int escapingValue) {
        return escapingValue;
    }

    @Specialization
    public Object escapingPrimitive(long escapingValue) {
        return escapingValue;
    }

    @Specialization
    public Object escapingPrimitive(float escapingValue) {
        return escapingValue;
    }

    @Specialization
    public Object escapingPrimitive(double escapingValue) {
        return escapingValue;
    }

    @Specialization
    public Object escapingString(String escapingValue) {
        return escapingValue;
    }

    @Specialization
    public Object escapingString(ForeignBoxedPrimitive escapingValue) {
        return escapingValue.getValue();
    }

    @Specialization
    public TruffleObject escapingAddress(LLVMAddress escapingValue) {
        if (LLVMAddress.NULL_POINTER.equals(escapingValue)) {
            return new LLVMTruffleNull();
        }
        return new LLVMTruffleAddress(escapingValue);
    }

    @Specialization
    public TruffleObject escapingFunction(LLVMFunction escapingValue) {
        return escapingValue;
    }

    @Specialization
    public TruffleObject escapingVector(LLVMI8Vector vector) {
        CompilerDirectives.transferToInterpreter();
        throw new IllegalStateException("Not yet implemented");
    }

    @Specialization
    public TruffleObject escapingVector(LLVMI64Vector vector) {
        CompilerDirectives.transferToInterpreter();
        throw new IllegalStateException("Not yet implemented");
    }

    @Specialization
    public TruffleObject escapingVector(LLVMI32Vector vector) {
        CompilerDirectives.transferToInterpreter();
        throw new IllegalStateException("Not yet implemented");
    }

    @Specialization
    public TruffleObject escapingVector(LLVMI1Vector vector) {
        CompilerDirectives.transferToInterpreter();
        throw new IllegalStateException("Not yet implemented");
    }

    @Specialization
    public TruffleObject escapingVector(LLVMI16Vector vector) {
        CompilerDirectives.transferToInterpreter();
        throw new IllegalStateException("Not yet implemented");
    }

    @Specialization
    public TruffleObject escapingVector(LLVMFloatVector vector) {
        CompilerDirectives.transferToInterpreter();
        throw new IllegalStateException("Not yet implemented");
    }

    @Specialization
    public TruffleObject escapingVector(LLVMDoubleVector vector) {
        CompilerDirectives.transferToInterpreter();
        throw new IllegalStateException("Not yet implemented");
    }

    @Specialization
    public TruffleObject escapingVarbit(LLVMIVarBit vector) {
        CompilerDirectives.transferToInterpreter();
        throw new IllegalStateException("Not yet implemented");
    }

    @Specialization
    public TruffleObject escapingTruffleObject(LLVMTruffleAddress address) {
        return address;
    }

    @Specialization
    public TruffleObject escapingTruffleObject(LLVMTruffleObject address) {
        if (address.getOffset() == 0) {
            return address.getObject();
        } else {
            CompilerDirectives.transferToInterpreter();
            throw new IllegalStateException("TruffleObject after pointer arithmetic must not leave Sulong.");
        }
    }

    @Specialization
    public Object escapingTruffleObject(LLVMGlobalVariableDescriptor escapingValue) {
        return new LLVMSharedGlobalVariableDescriptor(escapingValue);
    }

    public boolean notLLVM(TruffleObject v) {
        return LLVMExpressionNode.notLLVM(v);
    }

    public boolean notBoxedPrimitive(TruffleObject v) {
        return !(v instanceof ForeignBoxedPrimitive);
    }

    @Specialization(guards = {"notLLVM(escapingValue)", "notBoxedPrimitive(escapingValue)"})
    public Object escapingTruffleObject(TruffleObject escapingValue) {
        return escapingValue;
    }

    @Specialization(guards = "escapingValue == null")
    public Object escapingNull(Object escapingValue) {
        return new LLVMTruffleNull();
    }
}
