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
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleLanguage.ContextReference;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.llvm.runtime.LLVMAddress;
import com.oracle.truffle.llvm.runtime.LLVMBoxedPrimitive;
import com.oracle.truffle.llvm.runtime.LLVMContext;
import com.oracle.truffle.llvm.runtime.LLVMFunctionDescriptor;
import com.oracle.truffle.llvm.runtime.LLVMIVarBit;
import com.oracle.truffle.llvm.runtime.LLVMLanguage;
import com.oracle.truffle.llvm.runtime.LLVMSharedGlobalVariable;
import com.oracle.truffle.llvm.runtime.LLVMTruffleObject;
import com.oracle.truffle.llvm.runtime.LLVMVirtualAllocationAddress;
import com.oracle.truffle.llvm.runtime.LLVMVirtualAllocationAddress.LLVMVirtualAllocationAddressTruffleObject;
import com.oracle.truffle.llvm.runtime.global.LLVMGlobal;
import com.oracle.truffle.llvm.runtime.interop.access.LLVMInteropType;
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
public abstract class LLVMDataEscapeNode extends Node {

    @Child private LLVMGlobal.IsObjectStore isObjectStoreNode;

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
    protected TruffleObject escapingAddress(LLVMAddress escapingValue, LLVMInteropType.Structured type) {
        return LLVMTruffleObject.createPointer(escapingValue.getVal()).export(type);
    }

    @Specialization
    protected TruffleObject escapingFunction(LLVMFunctionDescriptor escapingValue, @SuppressWarnings("unused") LLVMInteropType.Structured type) {
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

    protected static boolean isForeign(LLVMTruffleObject pointer) {
        return pointer.getOffset() == 0 && pointer.getObject() instanceof LLVMTypedForeignObject;
    }

    @Specialization(guards = "isForeign(address)")
    TruffleObject escapingForeign(LLVMTruffleObject address, @SuppressWarnings("unused") LLVMInteropType.Structured type) {
        LLVMTypedForeignObject typedForeign = (LLVMTypedForeignObject) address.getObject();
        return typedForeign.getForeign();
    }

    @Specialization(guards = {"!isForeign(address)", "type != null"})
    TruffleObject escapingPointerOverrideType(LLVMTruffleObject address, LLVMInteropType.Structured type) {
        return address.export(type);
    }

    @Specialization(guards = {"!isForeign(address)", "type == null"})
    TruffleObject escapingPointer(LLVMTruffleObject address, @SuppressWarnings("unused") LLVMInteropType.Structured type) {
        return address;
    }

    @Specialization
    protected LLVMVirtualAllocationAddressTruffleObject escapingJavaByteArray(LLVMVirtualAllocationAddress address, @SuppressWarnings("unused") LLVMInteropType.Structured type) {
        return new LLVMVirtualAllocationAddressTruffleObject(address.copy());
    }

    @Specialization(guards = "!isObjectStore(contextRef, escapingValue)")
    protected Object escapingGlobal(LLVMGlobal escapingValue, @SuppressWarnings("unused") LLVMInteropType.Structured type,
                    @SuppressWarnings("unused") @Cached("getContextRef()") ContextReference<LLVMContext> contextRef) {
        return new LLVMSharedGlobalVariable(escapingValue);
    }

    @Specialization(guards = "isObjectStore(contextRef, escapingValue)")
    protected Object escapingGlobalObjectStore(LLVMGlobal escapingValue, @SuppressWarnings("unused") LLVMInteropType.Structured type,
                    @Cached("getContextRef()") ContextReference<LLVMContext> contextRef,
                    @Cached("create()") LLVMDataEscapeNode recursive,
                    @Cached("create()") LLVMGlobal.GetGlobalValueNode getGlobalValueNode) {
        return recursive.executeWithTarget(getGlobalValueNode.execute(contextRef.get(), escapingValue));
    }

    protected static ContextReference<LLVMContext> getContextRef() {
        return LLVMLanguage.getLLVMContextReference();
    }

    protected boolean isObjectStore(ContextReference<LLVMContext> contextRef, LLVMGlobal global) {
        if (isObjectStoreNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            isObjectStoreNode = insert(LLVMGlobal.IsObjectStore.create());
        }
        return isObjectStoreNode.execute(contextRef.get(), global);
    }

    @Specialization(guards = "escapingValue == null")
    protected LLVMTruffleObject escapingNull(@SuppressWarnings("unused") Object escapingValue, @SuppressWarnings("unused") LLVMInteropType.Structured type) {
        return LLVMTruffleObject.createNullPointer();
    }

    @TruffleBoundary
    public static Object slowConvert(Object value) {
        if (value instanceof LLVMBoxedPrimitive) {
            return ((LLVMBoxedPrimitive) value).getValue();
        } else if (value instanceof LLVMAddress) {
            return LLVMTruffleObject.createPointer(((LLVMAddress) value).getVal());
        } else if (value instanceof LLVMTruffleObject) {
            LLVMTruffleObject object = (LLVMTruffleObject) value;
            if (isForeign(object)) {
                LLVMTypedForeignObject typedForeign = (LLVMTypedForeignObject) object.getObject();
                return typedForeign.getForeign();
            } else {
                return object;
            }
        } else if (value instanceof LLVMVirtualAllocationAddress) {
            return new LLVMVirtualAllocationAddressTruffleObject(((LLVMVirtualAllocationAddress) value).copy());
        } else if (value instanceof LLVMGlobal) {
            LLVMContext ctx = getContextRef().get();
            LLVMGlobal global = (LLVMGlobal) value;
            Object globalValue = ctx.getGlobalFrame().getValue(global.getSlot());
            if (LLVMGlobal.isObjectStore(global.getType(), globalValue)) {
                return globalValue;
            }
            return new LLVMSharedGlobalVariable((LLVMGlobal) value);
        } else if (value == null) {
            return LLVMTruffleObject.createNullPointer();
        } else {
            return value;
        }
    }
}
