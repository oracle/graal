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

import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.llvm.runtime.LLVMAddress;
import com.oracle.truffle.llvm.runtime.LLVMBoxedPrimitive;
import com.oracle.truffle.llvm.runtime.LLVMFunctionHandle;
import com.oracle.truffle.llvm.runtime.LLVMIVarBit;
import com.oracle.truffle.llvm.runtime.LLVMTruffleObject;
import com.oracle.truffle.llvm.runtime.floating.LLVM80BitFloat;
import com.oracle.truffle.llvm.runtime.global.LLVMGlobalVariable;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.vector.LLVMAddressVector;
import com.oracle.truffle.llvm.runtime.vector.LLVMDoubleVector;
import com.oracle.truffle.llvm.runtime.vector.LLVMFloatVector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI16Vector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI1Vector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI32Vector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI64Vector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI8Vector;

@NodeChild(value = "valueSource", type = LLVMExpressionNode.class)
public abstract class LLVMToDebugValueNode extends LLVMExpressionNode {

    private static final int BOOLEAN_SIZE = 1;

    @Specialization
    public Object fromBoolean(boolean value) {
        return new LLVMConstantValueProvider.Integer(BOOLEAN_SIZE, value ? 1L : 0L);
    }

    @Specialization
    public Object fromByte(byte value) {
        return new LLVMConstantValueProvider.Integer(Byte.SIZE, value);
    }

    @Specialization
    public Object fromShort(short value) {
        return new LLVMConstantValueProvider.Integer(Short.SIZE, value);
    }

    @Specialization
    public Object fromInt(int value) {
        return new LLVMConstantValueProvider.Integer(Integer.SIZE, value);
    }

    @Specialization
    public Object fromLong(long value) {
        return new LLVMConstantValueProvider.Integer(Long.SIZE, value);
    }

    @Specialization
    public Object fromIVarBit(LLVMIVarBit value) {
        return new LLVMConstantValueProvider.IVarBit(value);
    }

    @Specialization
    public Object fromBoxedPrimitive(VirtualFrame frame, LLVMBoxedPrimitive value) {
        final LLVMToDebugValueNode unboxedToDebug = LLVMToDebugValueNodeGen.create(new LLVMUnboxPrimitiveNode(value));
        return unboxedToDebug.executeGeneric(frame);
    }

    @Specialization
    public Object fromAddress(LLVMAddress value) {
        return new LLVMConstantValueProvider.Address(value);
    }

    @Specialization
    public Object fromFunctionHandle(LLVMFunctionHandle value) {
        return new LLVMConstantValueProvider.Function(value, getContext());
    }

    @Specialization
    public Object fromFloat(float value) {
        return new LLVMConstantValueProvider.Float(value);
    }

    @Specialization
    public Object fromDouble(double value) {
        return new LLVMConstantValueProvider.Double(value);
    }

    @Specialization
    public Object from80BitFloat(LLVM80BitFloat value) {
        return new LLVMConstantValueProvider.LLVM80BitFloat(value);
    }

    @Specialization
    public Object fromTruffleObject(TruffleObject value) {
        return new LLVMConstantValueProvider.InteropValue(value);
    }

    @Specialization
    public Object fromLLVMTruffleObject(LLVMTruffleObject value) {
        return new LLVMConstantValueProvider.InteropValue(value.getObject(), value.getOffset());
    }

    @Specialization
    public Object fromGlobalVariable(LLVMGlobalVariable value) {
        return new LLVMConstantGlobalValueProvider(value);
    }

    @Specialization
    public Object fromI1Vector(LLVMI1Vector value) {
        return new LLVMConstantVectorValueProvider.I1(value);
    }

    @Specialization
    public Object fromI8Vector(LLVMI8Vector value) {
        return new LLVMConstantVectorValueProvider.I8(value);
    }

    @Specialization
    public Object fromI16Vector(LLVMI16Vector value) {
        return new LLVMConstantVectorValueProvider.I16(value);
    }

    @Specialization
    public Object fromI32Vector(LLVMI32Vector value) {
        return new LLVMConstantVectorValueProvider.I32(value);
    }

    @Specialization
    public Object fromI64Vector(LLVMI64Vector value) {
        return new LLVMConstantVectorValueProvider.I64(value);
    }

    @Specialization
    public Object fromFloatVector(LLVMFloatVector value) {
        return new LLVMConstantVectorValueProvider.Float(value);
    }

    @Specialization
    public Object fromDoubleVector(LLVMDoubleVector value) {
        return new LLVMConstantVectorValueProvider.Double(value);
    }

    @Specialization
    public Object fromAddressVector(LLVMAddressVector value) {
        return new LLVMConstantVectorValueProvider.Address(value);
    }
}
