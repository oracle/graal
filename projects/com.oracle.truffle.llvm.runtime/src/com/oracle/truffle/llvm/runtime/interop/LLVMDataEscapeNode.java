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
package com.oracle.truffle.llvm.runtime.interop;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.llvm.runtime.LLVMBoxedPrimitive;
import com.oracle.truffle.llvm.runtime.LLVMIVarBit;
import com.oracle.truffle.llvm.runtime.LLVMVirtualAllocationAddress;
import com.oracle.truffle.llvm.runtime.LLVMVirtualAllocationAddress.LLVMVirtualAllocationAddressTruffleObject;
import com.oracle.truffle.llvm.runtime.interop.access.LLVMInteropType;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMNode;
import com.oracle.truffle.llvm.runtime.pointer.LLVMManagedPointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMNativePointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMPointer;
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
 */
public abstract class LLVMDataEscapeNode extends LLVMNode {

    public static LLVMDataEscapeNode create() {
        return LLVMDataEscapeNodeGen.create();
    }

    public final Object executeWithTarget(Object escapingValue) {
        return executeWithType(escapingValue, null);
    }

    public abstract Object executeWithType(Object escapingValue, LLVMInteropType.Structured type);

    @Specialization
    protected boolean escapingPrimitive(boolean escapingValue, @SuppressWarnings("unused") LLVMInteropType.Structured type) {
        return escapingValue;
    }

    @Specialization
    protected byte escapingPrimitive(byte escapingValue, @SuppressWarnings("unused") LLVMInteropType.Structured type) {
        return escapingValue;
    }

    @Specialization
    protected short escapingPrimitive(short escapingValue, @SuppressWarnings("unused") LLVMInteropType.Structured type) {
        return escapingValue;
    }

    @Specialization
    protected char escapingPrimitive(char escapingValue, @SuppressWarnings("unused") LLVMInteropType.Structured type) {
        return escapingValue;
    }

    @Specialization
    protected int escapingPrimitive(int escapingValue, @SuppressWarnings("unused") LLVMInteropType.Structured type) {
        return escapingValue;
    }

    @Specialization
    protected long escapingPrimitive(long escapingValue, @SuppressWarnings("unused") LLVMInteropType.Structured type) {
        return escapingValue;
    }

    @Specialization
    protected float escapingPrimitive(float escapingValue, @SuppressWarnings("unused") LLVMInteropType.Structured type) {
        return escapingValue;
    }

    @Specialization
    protected double escapingPrimitive(double escapingValue, @SuppressWarnings("unused") LLVMInteropType.Structured type) {
        return escapingValue;
    }

    @Specialization
    protected String escapingString(String escapingValue, @SuppressWarnings("unused") LLVMInteropType.Structured type) {
        return escapingValue;
    }

    @Specialization
    protected Object escapingBoxed(LLVMBoxedPrimitive escapingValue, @SuppressWarnings("unused") LLVMInteropType.Structured type) {
        return escapingValue.getValue();
    }

    @Specialization
    protected TruffleObject escapingType(LLVMInteropType escapingValue, @SuppressWarnings("unused") LLVMInteropType.Structured type) {
        return escapingValue;
    }

    @Specialization
    @SuppressWarnings("unused")
    protected TruffleObject escapingVector(LLVMI8Vector vector, LLVMInteropType.Structured type) {
        CompilerDirectives.transferToInterpreter();
        throw new IllegalStateException("Exporting Vectors is not yet supported!");
    }

    @Specialization
    @SuppressWarnings("unused")
    protected TruffleObject escapingVector(LLVMI64Vector vecto, LLVMInteropType.Structured typer) {
        CompilerDirectives.transferToInterpreter();
        throw new IllegalStateException("Exporting Vectors is not yet supported!");
    }

    @Specialization
    @SuppressWarnings("unused")
    protected TruffleObject escapingVector(LLVMI32Vector vecto, LLVMInteropType.Structured typer) {
        CompilerDirectives.transferToInterpreter();
        throw new IllegalStateException("Exporting Vectors is not yet supported!");
    }

    @Specialization
    @SuppressWarnings("unused")
    protected TruffleObject escapingVector(LLVMI1Vector vector, LLVMInteropType.Structured type) {
        CompilerDirectives.transferToInterpreter();
        throw new IllegalStateException("Exporting Vectors is not yet supported!");
    }

    @Specialization
    @SuppressWarnings("unused")
    protected TruffleObject escapingVector(LLVMI16Vector vecto, LLVMInteropType.Structured typer) {
        CompilerDirectives.transferToInterpreter();
        throw new IllegalStateException("Exporting Vectors is not yet supported!");
    }

    @Specialization
    @SuppressWarnings("unused")
    protected TruffleObject escapingVector(LLVMFloatVector vecto, LLVMInteropType.Structured typer) {
        CompilerDirectives.transferToInterpreter();
        throw new IllegalStateException("Exporting Vectors is not yet supported!");
    }

    @Specialization
    @SuppressWarnings("unused")
    protected TruffleObject escapingVector(LLVMDoubleVector vecto, LLVMInteropType.Structured typer) {
        CompilerDirectives.transferToInterpreter();
        throw new IllegalStateException("Exporting Vectors is not yet supported!");
    }

    @Specialization
    @SuppressWarnings("unused")
    protected TruffleObject escapingVarbit(LLVMIVarBit vecto, LLVMInteropType.Structured typer) {
        CompilerDirectives.transferToInterpreter();
        throw new IllegalStateException("Exporting VarBit is not yet supported!");
    }

    protected static boolean isForeign(LLVMPointer pointer) {
        if (LLVMManagedPointer.isInstance(pointer)) {
            LLVMManagedPointer managed = LLVMManagedPointer.cast(pointer);
            return managed.getOffset() == 0 && managed.getObject() instanceof LLVMTypedForeignObject;
        } else {
            return false;
        }
    }

    @Specialization(guards = "isForeign(address)")
    TruffleObject escapingForeign(LLVMManagedPointer address, @SuppressWarnings("unused") LLVMInteropType.Structured type) {
        LLVMTypedForeignObject typedForeign = (LLVMTypedForeignObject) address.getObject();
        return typedForeign.getForeign();
    }

    @Specialization(guards = {"!isForeign(address)", "type != null"})
    TruffleObject escapingPointerOverrideType(LLVMPointer address, LLVMInteropType.Structured type) {
        return address.export(type);
    }

    @Specialization(guards = {"!isForeign(address)", "type == null"})
    TruffleObject escapingPointer(LLVMPointer address, @SuppressWarnings("unused") LLVMInteropType.Structured type) {
        return address;
    }

    @Specialization
    protected LLVMVirtualAllocationAddressTruffleObject escapingJavaByteArray(LLVMVirtualAllocationAddress address, @SuppressWarnings("unused") LLVMInteropType.Structured type) {
        return new LLVMVirtualAllocationAddressTruffleObject(address.copy());
    }

    @Specialization(guards = "escapingValue == null")
    protected LLVMNativePointer escapingNull(@SuppressWarnings("unused") Object escapingValue, @SuppressWarnings("unused") LLVMInteropType.Structured type) {
        return LLVMNativePointer.createNull();
    }
}
