/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.debug;

import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.utilities.AssumedValue;
import com.oracle.truffle.llvm.runtime.LLVMContext;
import com.oracle.truffle.llvm.runtime.LLVMFunctionDescriptor;
import com.oracle.truffle.llvm.runtime.LLVMIVarBit;
import com.oracle.truffle.llvm.runtime.debug.LLDBSupport;
import com.oracle.truffle.llvm.runtime.debug.scope.LLVMDebugGlobalVariable;
import com.oracle.truffle.llvm.runtime.debug.value.LLVMDebugTypeConstants;
import com.oracle.truffle.llvm.runtime.debug.value.LLVMDebugValue;
import com.oracle.truffle.llvm.runtime.floating.LLVM80BitFloat;
import com.oracle.truffle.llvm.runtime.global.LLVMGlobal;
import com.oracle.truffle.llvm.runtime.global.LLVMGlobalContainer;
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
import com.oracle.truffle.llvm.runtime.vector.LLVMPointerVector;

@GenerateUncached
public abstract class LLVMToDebugValueNode extends LLVMNode implements LLVMDebugValue.Builder {

    protected abstract LLVMDebugValue executeWithTarget(Object target);

    @Override
    public LLVMDebugValue build(Object irValue) {
        return executeWithTarget(irValue);
    }

    @Specialization
    protected LLVMDebugValue fromBoolean(boolean value) {
        return new LLDBConstant.Integer(LLVMDebugTypeConstants.BOOLEAN_SIZE, value ? 1L : 0L);
    }

    @Specialization
    protected LLVMDebugValue fromByte(byte value) {
        return new LLDBConstant.Integer(Byte.SIZE, value);
    }

    @Specialization
    protected LLVMDebugValue fromShort(short value) {
        return new LLDBConstant.Integer(Short.SIZE, value);
    }

    @Specialization
    protected LLVMDebugValue fromInt(int value) {
        return new LLDBConstant.Integer(Integer.SIZE, value);
    }

    @Specialization
    protected LLVMDebugValue fromLong(long value) {
        return new LLDBConstant.Integer(Long.SIZE, value);
    }

    @Specialization
    protected LLVMDebugValue fromIVarBit(LLVMIVarBit value) {
        return new LLDBConstant.IVarBit(value);
    }

    @Specialization
    protected LLVMDebugValue fromNativePointer(LLVMNativePointer value) {
        return new LLDBConstant.Pointer(value);
    }

    @Specialization
    protected LLVMDebugValue fromManagedPointer(LLVMManagedPointer value) {
        final Object target = value.getObject();

        if (target instanceof LLVMGlobalContainer) {
            return fromGlobalContainer((LLVMGlobalContainer) target);
        }

        try {
            /*
             * We're using the uncached library here because this node is only used from the slow
             * path.
             */
            InteropLibrary interop = InteropLibrary.getFactory().getUncached();
            if (interop.isBoolean(target)) {
                return new LLDBBoxedPrimitive(interop.asBoolean(target));
            } else if (interop.isNumber(target)) {
                Object unboxedValue;
                if (interop.fitsInByte(target)) {
                    unboxedValue = interop.asByte(target);
                } else if (interop.fitsInShort(target)) {
                    unboxedValue = interop.asShort(target);
                } else if (interop.fitsInInt(target)) {
                    unboxedValue = interop.asInt(target);
                } else if (interop.fitsInLong(target)) {
                    unboxedValue = interop.asLong(target);
                } else if (interop.fitsInFloat(target)) {
                    unboxedValue = interop.asFloat(target);
                } else if (interop.fitsInDouble(target)) {
                    unboxedValue = interop.asDouble(target);
                } else {
                    return LLVMDebugValue.UNAVAILABLE;
                }
                return new LLDBBoxedPrimitive(unboxedValue);
            }
        } catch (UnsupportedMessageException ignored) {
            // the default case is a sensible fallback for this
        }

        return new LLDBConstant.Pointer(value);
    }

    @Specialization
    protected LLVMDebugValue fromFunctionHandle(LLVMFunctionDescriptor value) {
        return new LLDBConstant.Function(value);
    }

    @Specialization
    protected LLVMDebugValue fromFloat(float value) {
        return new LLDBConstant.Float(value);
    }

    @Specialization
    protected LLVMDebugValue fromDouble(double value) {
        return new LLDBConstant.Double(value);
    }

    @Specialization
    protected LLVMDebugValue from80BitFloat(LLVM80BitFloat value) {
        return new LLDBConstant.BigFloat(value);
    }

    @Specialization
    protected LLVMDebugValue fromI1Vector(LLVMI1Vector value) {
        return new LLDBVector.I1(value);
    }

    @Specialization
    protected LLVMDebugValue fromI8Vector(LLVMI8Vector value) {
        return new LLDBVector.I8(value);
    }

    @Specialization
    protected LLVMDebugValue fromI16Vector(LLVMI16Vector value) {
        return new LLDBVector.I16(value);
    }

    @Specialization
    protected LLVMDebugValue fromI32Vector(LLVMI32Vector value) {
        return new LLDBVector.I32(value);
    }

    @Specialization
    protected LLVMDebugValue fromI64Vector(LLVMI64Vector value) {
        return new LLDBVector.I64(value);
    }

    @Specialization
    protected LLVMDebugValue fromFloatVector(LLVMFloatVector value) {
        return new LLDBVector.Float(value);
    }

    @Specialization
    protected LLVMDebugValue fromDoubleVector(LLVMDoubleVector value) {
        return new LLDBVector.Double(value);
    }

    @Specialization
    protected LLVMDebugValue fromAddressVector(LLVMPointerVector value) {
        return new LLDBVector.Address(value);
    }

    @Specialization
    protected LLVMDebugValue fromGlobalContainer(LLVMGlobalContainer value) {
        if (value.isPointer()) {
            return executeWithTarget(LLVMNativePointer.create(value.getAddress()));
        }

        final Object target = value.get();
        if (LLVMPointer.isInstance(target)) {
            return executeWithTarget(target);
        }

        return executeWithTarget(LLVMManagedPointer.create(target));
    }

    @Specialization
    protected LLVMDebugValue fromGlobal(LLVMDebugGlobalVariable value) {
        LLVMGlobal global = value.getDescriptor();
        LLVMContext context = value.getContext();
        AssumedValue<LLVMPointer>[] globals = context.findSymbolTable(global.getBitcodeID(false));
        int index = global.getSymbolIndex(false);
        Object target = globals[index].get();

        if (LLVMManagedPointer.isInstance(target)) {
            final LLVMManagedPointer managedPointer = LLVMManagedPointer.cast(target);
            if (LLDBSupport.pointsToObjectAccess(LLVMManagedPointer.cast(target))) {
                return new LLDBMemoryValue(managedPointer);
            }
        } else if (!LLVMPointer.isInstance(target)) {
            // a non-pointer was stored as a pointer in this global -- PLi: I'm not sure if this is
            // possible anymore
            // since we have removed AssumedValue<Object> being stored in a global
            return executeWithTarget(target);
        }
        return new LLDBGlobalConstant(global, context);
    }

    @Fallback
    protected LLVMDebugValue fromGenericObject(@SuppressWarnings("unused") Object value) {
        return LLVMDebugValue.UNAVAILABLE;
    }
}
