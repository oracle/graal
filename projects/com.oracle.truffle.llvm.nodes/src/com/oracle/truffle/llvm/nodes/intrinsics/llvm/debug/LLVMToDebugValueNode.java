/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates.
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

import com.oracle.truffle.api.TruffleLanguage.ContextReference;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.llvm.runtime.LLVMBoxedPrimitive;
import com.oracle.truffle.llvm.runtime.LLVMContext;
import com.oracle.truffle.llvm.runtime.LLVMFunctionDescriptor;
import com.oracle.truffle.llvm.runtime.LLVMIVarBit;
import com.oracle.truffle.llvm.runtime.LLVMSharedGlobalVariable;
import com.oracle.truffle.llvm.runtime.debug.LLVMDebugTypeConstants;
import com.oracle.truffle.llvm.runtime.debug.LLVMDebugValue;
import com.oracle.truffle.llvm.runtime.floating.LLVM80BitFloat;
import com.oracle.truffle.llvm.runtime.global.LLVMGlobal;
import com.oracle.truffle.llvm.runtime.memory.LLVMMemory;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMNode;
import com.oracle.truffle.llvm.runtime.pointer.LLVMManagedPointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMNativePointer;
import com.oracle.truffle.llvm.runtime.vector.LLVMPointerVector;
import com.oracle.truffle.llvm.runtime.vector.LLVMDoubleVector;
import com.oracle.truffle.llvm.runtime.vector.LLVMFloatVector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI16Vector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI1Vector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI32Vector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI64Vector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI8Vector;

public abstract class LLVMToDebugValueNode extends LLVMNode implements LLVMDebugValue.Builder {

    protected final ContextReference<LLVMContext> contextRef;

    protected LLVMToDebugValueNode(ContextReference<LLVMContext> contextRef) {
        this.contextRef = contextRef;
    }

    public abstract LLVMDebugValue executeWithTarget(Object target);

    @Override
    public LLVMDebugValue build(Object irValue) {
        return executeWithTarget(irValue);
    }

    @Specialization
    protected LLVMDebugValue fromBoolean(boolean value) {
        return new LLVMConstantValueProvider.Integer(LLVMDebugTypeConstants.BOOLEAN_SIZE, value ? 1L : 0L);
    }

    @Specialization
    protected LLVMDebugValue fromByte(byte value) {
        return new LLVMConstantValueProvider.Integer(Byte.SIZE, value);
    }

    @Specialization
    protected LLVMDebugValue fromShort(short value) {
        return new LLVMConstantValueProvider.Integer(Short.SIZE, value);
    }

    @Specialization
    protected LLVMDebugValue fromInt(int value) {
        return new LLVMConstantValueProvider.Integer(Integer.SIZE, value);
    }

    @Specialization
    protected LLVMDebugValue fromLong(long value) {
        return new LLVMConstantValueProvider.Integer(Long.SIZE, value);
    }

    @Specialization
    protected LLVMDebugValue fromIVarBit(LLVMIVarBit value) {
        return new LLVMConstantValueProvider.IVarBit(value);
    }

    @Specialization
    protected LLVMDebugValue fromBoxedPrimitive(LLVMBoxedPrimitive value) {
        return executeWithTarget(value.getValue());
    }

    @Specialization
    protected LLVMDebugValue fromAddress(LLVMNativePointer value,
                    @Cached("getLLVMMemory()") LLVMMemory memory) {
        return new LLVMConstantValueProvider.Pointer(memory, value);
    }

    @Specialization
    protected LLVMDebugValue fromFunctionHandle(LLVMFunctionDescriptor value) {
        return new LLVMConstantValueProvider.Function(value);
    }

    @Specialization
    protected LLVMDebugValue fromFloat(float value) {
        return new LLVMConstantValueProvider.Float(value);
    }

    @Specialization
    protected LLVMDebugValue fromDouble(double value) {
        return new LLVMConstantValueProvider.Double(value);
    }

    @Specialization
    protected LLVMDebugValue from80BitFloat(LLVM80BitFloat value) {
        return new LLVMConstantValueProvider.BigFloat(value);
    }

    protected LLVMDebugValue createFromGlobal(@SuppressWarnings("unused") LLVMGlobal value, @SuppressWarnings("unused") LLVMMemory memory) {
        return LLVMDebugValue.UNAVAILABLE;
    }

    @Specialization
    protected LLVMDebugValue fromGlobal(LLVMGlobal value,
                    @Cached("getLLVMMemory()") LLVMMemory memory) {
        return createFromGlobal(value, memory);
    }

    @Specialization
    protected LLVMDebugValue fromSharedGlobal(LLVMSharedGlobalVariable value,
                    @Cached("getLLVMMemory()") LLVMMemory memory) {
        return fromGlobal(value.getDescriptor(), memory);
    }

    @Specialization
    protected LLVMDebugValue fromI1Vector(LLVMI1Vector value) {
        return new LLVMConstantVectorValueProvider.I1(value);
    }

    @Specialization
    protected LLVMDebugValue fromI8Vector(LLVMI8Vector value) {
        return new LLVMConstantVectorValueProvider.I8(value);
    }

    @Specialization
    protected LLVMDebugValue fromI16Vector(LLVMI16Vector value) {
        return new LLVMConstantVectorValueProvider.I16(value);
    }

    @Specialization
    protected LLVMDebugValue fromI32Vector(LLVMI32Vector value) {
        return new LLVMConstantVectorValueProvider.I32(value);
    }

    @Specialization
    protected LLVMDebugValue fromI64Vector(LLVMI64Vector value) {
        return new LLVMConstantVectorValueProvider.I64(value);
    }

    @Specialization
    protected LLVMDebugValue fromFloatVector(LLVMFloatVector value) {
        return new LLVMConstantVectorValueProvider.Float(value);
    }

    @Specialization
    protected LLVMDebugValue fromDoubleVector(LLVMDoubleVector value) {
        return new LLVMConstantVectorValueProvider.Double(value);
    }

    @Specialization
    protected LLVMDebugValue fromAddressVector(LLVMPointerVector value) {
        return new LLVMConstantVectorValueProvider.Address(value);
    }

    @Specialization
    protected LLVMDebugValue fromManagedPointer(LLVMManagedPointer value) {
        return new LLVMConstantValueProvider.InteropValue(value.getObject(), value.getOffset());
    }

    @Specialization
    protected LLVMDebugValue fromGenericObject(@SuppressWarnings("unused") Object value) {
        return LLVMDebugValue.UNAVAILABLE;
    }

    public abstract static class LLVMToStaticDebugValueNode extends LLVMToDebugValueNode {

        protected LLVMToStaticDebugValueNode(ContextReference<LLVMContext> contextRef) {
            super(contextRef);
        }

        @Override
        protected LLVMDebugValue createFromGlobal(LLVMGlobal value, LLVMMemory memory) {
            // global as value container, all referenced globals should instead be treated as
            // pointers
            return new LLVMConstantGlobalValueProvider(memory, value, contextRef.get(), LLVMToDebugValueNodeGen.LLVMToDynamicDebugValueNodeGen.create(contextRef));
        }
    }

    public abstract static class LLVMToDynamicDebugValueNode extends LLVMToDebugValueNode {

        protected LLVMToDynamicDebugValueNode(ContextReference<LLVMContext> contextRef) {
            super(contextRef);
        }

        @Override
        protected LLVMDebugValue createFromGlobal(LLVMGlobal value, LLVMMemory memory) {
            // global as pointer value
            return new LLVMConstantGlobalPointerProvider(memory, value, contextRef.get(), LLVMToDebugValueNodeGen.LLVMToDynamicDebugValueNodeGen.create(contextRef));
        }
    }
}
