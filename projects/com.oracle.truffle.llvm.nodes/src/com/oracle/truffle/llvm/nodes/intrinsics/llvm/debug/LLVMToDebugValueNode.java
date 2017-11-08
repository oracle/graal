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
package com.oracle.truffle.llvm.nodes.intrinsics.llvm.debug;

import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.llvm.runtime.LLVMAddress;
import com.oracle.truffle.llvm.runtime.LLVMBoxedPrimitive;
import com.oracle.truffle.llvm.runtime.LLVMFunctionHandle;
import com.oracle.truffle.llvm.runtime.LLVMIVarBit;
import com.oracle.truffle.llvm.runtime.LLVMSharedGlobalVariable;
import com.oracle.truffle.llvm.runtime.LLVMTruffleObject;
import com.oracle.truffle.llvm.runtime.debug.LLVMDebugTypeConstants;
import com.oracle.truffle.llvm.runtime.debug.LLVMDebugValueProvider;
import com.oracle.truffle.llvm.runtime.floating.LLVM80BitFloat;
import com.oracle.truffle.llvm.runtime.global.LLVMGlobalVariable;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMNode;
import com.oracle.truffle.llvm.runtime.vector.LLVMAddressVector;
import com.oracle.truffle.llvm.runtime.vector.LLVMDoubleVector;
import com.oracle.truffle.llvm.runtime.vector.LLVMFloatVector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI16Vector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI1Vector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI32Vector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI64Vector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI8Vector;

public abstract class LLVMToDebugValueNode extends LLVMNode implements LLVMDebugValueProvider.Builder {

    public abstract LLVMDebugValueProvider executeWithTarget(Object target);

    @Override
    public LLVMDebugValueProvider build(Object irValue) {
        return executeWithTarget(irValue);
    }

    @Specialization
    public LLVMDebugValueProvider fromBoolean(boolean value) {
        return new LLVMConstantValueProvider.Integer(LLVMDebugTypeConstants.BOOLEAN_SIZE, value ? 1L : 0L);
    }

    @Specialization
    public LLVMDebugValueProvider fromByte(byte value) {
        return new LLVMConstantValueProvider.Integer(Byte.SIZE, value);
    }

    @Specialization
    public LLVMDebugValueProvider fromShort(short value) {
        return new LLVMConstantValueProvider.Integer(Short.SIZE, value);
    }

    @Specialization
    public LLVMDebugValueProvider fromInt(int value) {
        return new LLVMConstantValueProvider.Integer(Integer.SIZE, value);
    }

    @Specialization
    public LLVMDebugValueProvider fromLong(long value) {
        return new LLVMConstantValueProvider.Integer(Long.SIZE, value);
    }

    @Specialization
    public LLVMDebugValueProvider fromIVarBit(LLVMIVarBit value) {
        return new LLVMConstantValueProvider.IVarBit(value);
    }

    @Specialization
    public LLVMDebugValueProvider fromBoxedPrimitive(LLVMBoxedPrimitive value) {
        return executeWithTarget(value.getValue());
    }

    @Specialization
    public LLVMDebugValueProvider fromAddress(LLVMAddress value) {
        return new LLVMConstantValueProvider.Address(value);
    }

    @Specialization
    public LLVMDebugValueProvider fromFunctionHandle(LLVMFunctionHandle value) {
        return new LLVMConstantValueProvider.Function(value, getContextReference().get());
    }

    @Specialization
    public LLVMDebugValueProvider fromFloat(float value) {
        return new LLVMConstantValueProvider.Float(value);
    }

    @Specialization
    public LLVMDebugValueProvider fromDouble(double value) {
        return new LLVMConstantValueProvider.Double(value);
    }

    @Specialization
    public LLVMDebugValueProvider from80BitFloat(LLVM80BitFloat value) {
        return new LLVMConstantValueProvider.BigFloat(value);
    }

    @Specialization
    public LLVMDebugValueProvider fromGlobal(LLVMGlobalVariable value) {
        return new LLVMConstantGlobalValueProvider(value, LLVMToDebugValueNodeGen.create());
    }

    @Specialization
    public LLVMDebugValueProvider fromSharedGlobal(LLVMSharedGlobalVariable value) {
        return fromGlobal(value.getDescriptor());
    }

    @Specialization
    public LLVMDebugValueProvider fromI1Vector(LLVMI1Vector value) {
        return new LLVMConstantVectorValueProvider.I1(value);
    }

    @Specialization
    public LLVMDebugValueProvider fromI8Vector(LLVMI8Vector value) {
        return new LLVMConstantVectorValueProvider.I8(value);
    }

    @Specialization
    public LLVMDebugValueProvider fromI16Vector(LLVMI16Vector value) {
        return new LLVMConstantVectorValueProvider.I16(value);
    }

    @Specialization
    public LLVMDebugValueProvider fromI32Vector(LLVMI32Vector value) {
        return new LLVMConstantVectorValueProvider.I32(value);
    }

    @Specialization
    public LLVMDebugValueProvider fromI64Vector(LLVMI64Vector value) {
        return new LLVMConstantVectorValueProvider.I64(value);
    }

    @Specialization
    public LLVMDebugValueProvider fromFloatVector(LLVMFloatVector value) {
        return new LLVMConstantVectorValueProvider.Float(value);
    }

    @Specialization
    public LLVMDebugValueProvider fromDoubleVector(LLVMDoubleVector value) {
        return new LLVMConstantVectorValueProvider.Double(value);
    }

    @Specialization
    public LLVMDebugValueProvider fromAddressVector(LLVMAddressVector value) {
        return new LLVMConstantVectorValueProvider.Address(value);
    }

    @Specialization
    public LLVMDebugValueProvider fromLLVMTruffleObject(LLVMTruffleObject value) {
        return new LLVMConstantValueProvider.InteropValue(value.getObject(), value.getOffset());
    }

    @Specialization
    public LLVMDebugValueProvider fromTruffleObject(TruffleObject value) {
        return new LLVMConstantValueProvider.InteropValue(value);
    }

    @Specialization
    public LLVMDebugValueProvider fromGenericObject(@SuppressWarnings("unused") Object value) {
        return LLVMUnavailableDebugValueProvider.INSTANCE;
    }
}
