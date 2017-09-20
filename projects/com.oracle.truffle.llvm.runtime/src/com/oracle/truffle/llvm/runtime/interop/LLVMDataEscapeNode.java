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
package com.oracle.truffle.llvm.runtime.interop;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.llvm.runtime.LLVMAddress;
import com.oracle.truffle.llvm.runtime.LLVMBoxedPrimitive;
import com.oracle.truffle.llvm.runtime.LLVMVirtualAllocationAddress;
import com.oracle.truffle.llvm.runtime.LLVMVirtualAllocationAddress.LLVMVirtualAllocationAddressTruffleObject;
import com.oracle.truffle.llvm.runtime.LLVMContext;
import com.oracle.truffle.llvm.runtime.LLVMFunctionDescriptor;
import com.oracle.truffle.llvm.runtime.LLVMFunctionHandle;
import com.oracle.truffle.llvm.runtime.LLVMIVarBit;
import com.oracle.truffle.llvm.runtime.LLVMSharedGlobalVariable;
import com.oracle.truffle.llvm.runtime.LLVMTruffleAddress;
import com.oracle.truffle.llvm.runtime.LLVMTruffleObject;
import com.oracle.truffle.llvm.runtime.global.LLVMGlobalVariable;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.types.PointerType;
import com.oracle.truffle.llvm.runtime.types.Type;
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

    private final Type typeForExport;

    public LLVMDataEscapeNode(Type typeForExport) {
        this.typeForExport = typeForExport;
    }

    public abstract Object executeWithTarget(Object escapingValue, LLVMContext context);

    @Specialization
    public Object escapingPrimitive(boolean escapingValue, LLVMContext context) {
        return escapingValue;
    }

    @Specialization
    public Object escapingPrimitive(byte escapingValue, LLVMContext context) {
        return escapingValue;
    }

    @Specialization
    public Object escapingPrimitive(short escapingValue, LLVMContext context) {
        return escapingValue;
    }

    @Specialization
    public Object escapingPrimitive(char escapingValue, LLVMContext context) {
        return escapingValue;
    }

    @Specialization
    public Object escapingPrimitive(int escapingValue, LLVMContext context) {
        return escapingValue;
    }

    @Specialization
    public Object escapingPrimitive(long escapingValue, LLVMContext context) {
        return escapingValue;
    }

    @Specialization
    public Object escapingPrimitive(float escapingValue, LLVMContext context) {
        return escapingValue;
    }

    @Specialization
    public Object escapingPrimitive(double escapingValue, LLVMContext context) {
        return escapingValue;
    }

    @Specialization
    public Object escapingString(String escapingValue, LLVMContext context) {
        return escapingValue;
    }

    @Specialization
    public Object escapingString(LLVMBoxedPrimitive escapingValue, LLVMContext context) {
        return escapingValue.getValue();
    }

    @Specialization
    public TruffleObject escapingAddress(LLVMAddress escapingValue, LLVMContext context) {
        if (LLVMAddress.nullPointer().equals(escapingValue)) {
            return new LLVMTruffleAddress(LLVMAddress.fromLong(0), new PointerType(null), context);
        }
        assert typeForExport != null;
        return new LLVMTruffleAddress(escapingValue, typeForExport, context);
    }

    @Specialization
    public TruffleObject escapingFunction(LLVMFunctionHandle escapingValue, LLVMContext context) {
        return context.getFunctionDescriptor(escapingValue);
    }

    @Specialization
    public TruffleObject escapingFunction(LLVMFunctionDescriptor escapingValue, LLVMContext context) {
        return escapingValue;
    }

    @Specialization
    public TruffleObject escapingVector(LLVMI8Vector vector, LLVMContext context) {
        CompilerDirectives.transferToInterpreter();
        throw new IllegalStateException("Exporting Vectors is not yet supported!");
    }

    @Specialization
    public TruffleObject escapingVector(LLVMI64Vector vector, LLVMContext context) {
        CompilerDirectives.transferToInterpreter();
        throw new IllegalStateException("Exporting Vectors is not yet supported!");
    }

    @Specialization
    public TruffleObject escapingVector(LLVMI32Vector vector, LLVMContext context) {
        CompilerDirectives.transferToInterpreter();
        throw new IllegalStateException("Exporting Vectors is not yet supported!");
    }

    @Specialization
    public TruffleObject escapingVector(LLVMI1Vector vector, LLVMContext context) {
        CompilerDirectives.transferToInterpreter();
        throw new IllegalStateException("Exporting Vectors is not yet supported!");
    }

    @Specialization
    public TruffleObject escapingVector(LLVMI16Vector vector, LLVMContext context) {
        CompilerDirectives.transferToInterpreter();
        throw new IllegalStateException("Exporting Vectors is not yet supported!");
    }

    @Specialization
    public TruffleObject escapingVector(LLVMFloatVector vector, LLVMContext context) {
        CompilerDirectives.transferToInterpreter();
        throw new IllegalStateException("Exporting Vectors is not yet supported!");
    }

    @Specialization
    public TruffleObject escapingVector(LLVMDoubleVector vector, LLVMContext context) {
        CompilerDirectives.transferToInterpreter();
        throw new IllegalStateException("Exporting Vectors is not yet supported!");
    }

    @Specialization
    public TruffleObject escapingVarbit(LLVMIVarBit vector, LLVMContext context) {
        CompilerDirectives.transferToInterpreter();
        throw new IllegalStateException("Exporting VarBit is not yet supported!");
    }

    @Specialization
    public TruffleObject escapingTruffleObject(LLVMTruffleAddress address, LLVMContext context) {
        return address;
    }

    @Specialization
    public TruffleObject escapingTruffleObject(LLVMTruffleObject address, LLVMContext context) {
        if (address.getOffset() == 0) {
            return address.getObject();
        } else {
            CompilerDirectives.transferToInterpreter();
            throw new IllegalStateException("TruffleObject after pointer arithmetic must not leave Sulong.");
        }
    }

    @Specialization
    public TruffleObject escapingJavaByteArray(LLVMVirtualAllocationAddress address, LLVMContext context) {
        return new LLVMVirtualAllocationAddressTruffleObject(address.copy());
    }

    @Specialization
    public Object escapingTruffleObject(LLVMGlobalVariable escapingValue, LLVMContext context) {
        return new LLVMSharedGlobalVariable(escapingValue, context);
    }

    public boolean notLLVM(TruffleObject v) {
        return LLVMExpressionNode.notLLVM(v);
    }

    @Specialization(guards = {"notLLVM(escapingValue)"})
    public Object escapingTruffleObject(TruffleObject escapingValue, LLVMContext context) {
        return escapingValue;
    }

    @Specialization(guards = "escapingValue == null")
    public Object escapingNull(Object escapingValue, LLVMContext context) {
        return new LLVMTruffleAddress(LLVMAddress.nullPointer(), new PointerType(null), context);
    }

    @TruffleBoundary
    public static Object slowConvert(Object value, Type type, LLVMContext context) {
        if (value instanceof LLVMBoxedPrimitive) {
            return ((LLVMBoxedPrimitive) value).getValue();
        } else if (value instanceof LLVMAddress && LLVMAddress.nullPointer().equals(value)) {
            return new LLVMTruffleAddress(LLVMAddress.nullPointer(), new PointerType(null), context);
        } else if (value instanceof LLVMAddress) {
            return new LLVMTruffleAddress((LLVMAddress) value, type, context);
        } else if (value instanceof LLVMFunctionHandle) {
            return context.getFunctionDescriptor((LLVMFunctionHandle) value);
        } else if (value instanceof LLVMTruffleObject && ((LLVMTruffleObject) value).getOffset() == 0) {
            return ((LLVMTruffleObject) value).getObject();
        } else if (value instanceof LLVMTruffleObject) {
            throw new IllegalStateException("TruffleObject after pointer arithmetic must not leave Sulong.");
        } else if (value instanceof LLVMVirtualAllocationAddress) {
            return new LLVMVirtualAllocationAddressTruffleObject(((LLVMVirtualAllocationAddress) value).copy());
        } else if (value instanceof LLVMGlobalVariable) {
            return new LLVMSharedGlobalVariable((LLVMGlobalVariable) value, context);
        } else if (value instanceof TruffleObject && LLVMExpressionNode.notLLVM((TruffleObject) value)) {
            return value;
        } else if (value == null) {
            return new LLVMTruffleAddress(LLVMAddress.nullPointer(), new PointerType(null), context);
        } else {
            return value;
        }
    }
}
