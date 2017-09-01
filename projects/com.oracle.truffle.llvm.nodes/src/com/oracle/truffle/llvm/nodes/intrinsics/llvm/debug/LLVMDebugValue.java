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

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.NodeChildren;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.llvm.runtime.LLVMAddress;
import com.oracle.truffle.llvm.runtime.LLVMBoxedPrimitive;
import com.oracle.truffle.llvm.runtime.LLVMFunctionHandle;
import com.oracle.truffle.llvm.runtime.LLVMIVarBit;
import com.oracle.truffle.llvm.runtime.LLVMTruffleObject;
import com.oracle.truffle.llvm.runtime.debug.LLVMDebugObject;
import com.oracle.truffle.llvm.runtime.debug.LLVMDebugValueContainer;
import com.oracle.truffle.llvm.runtime.debug.LLVMDebugValueProvider;
import com.oracle.truffle.llvm.runtime.debug.LLVMSourceType;
import com.oracle.truffle.llvm.runtime.floating.LLVM80BitFloat;
import com.oracle.truffle.llvm.runtime.global.LLVMGlobalVariable;
import com.oracle.truffle.llvm.runtime.global.LLVMGlobalVariableAccess;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.vector.LLVMAddressVector;
import com.oracle.truffle.llvm.runtime.vector.LLVMDoubleVector;
import com.oracle.truffle.llvm.runtime.vector.LLVMFloatVector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI16Vector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI1Vector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI32Vector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI64Vector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI8Vector;

@NodeChildren({@NodeChild(value = "container", type = LLVMExpressionNode.class), @NodeChild(value = "value", type = LLVMExpressionNode.class)})
public abstract class LLVMDebugValue extends LLVMExpressionNode {

    private static final int BOOLEAN_SIZE = 1;

    private final String varName;
    private final LLVMSourceType varType;

    private final FrameSlot containerSlot;

    public LLVMDebugValue(String varName, LLVMSourceType varType, FrameSlot containerSlot) {
        this.varName = varName;
        this.varType = varType;
        this.containerSlot = containerSlot;
    }

    @Specialization
    public Object readBoolean(LLVMDebugValueContainer container, boolean value) {
        instantiate(container, new LLVMConstantValueProvider.Integer(BOOLEAN_SIZE, value ? 1 : 0));
        return null;
    }

    @Specialization
    public Object readByte(LLVMDebugValueContainer container, byte value) {
        instantiate(container, new LLVMConstantValueProvider.Integer(Byte.SIZE, value));
        return null;
    }

    @Specialization
    public Object readShort(LLVMDebugValueContainer container, short value) {
        instantiate(container, new LLVMConstantValueProvider.Integer(Short.SIZE, value));
        return null;
    }

    @Specialization
    public Object readInt(LLVMDebugValueContainer container, int value) {
        instantiate(container, new LLVMConstantValueProvider.Integer(Integer.SIZE, value));
        return null;
    }

    @Specialization
    public Object readLong(LLVMDebugValueContainer container, long value) {
        instantiate(container, new LLVMConstantValueProvider.Integer(Long.SIZE, value));
        return null;
    }

    @Specialization
    public Object readIVarBit(LLVMDebugValueContainer container, LLVMIVarBit value) {
        instantiate(container, new LLVMConstantValueProvider.IVarBit(value));
        return null;
    }

    @Specialization
    public Object readBoxedPrimitive(LLVMDebugValueContainer container, LLVMBoxedPrimitive boxedValue) {
        final Object value = boxedValue.getValue();
        if (value instanceof Boolean) {
            return readBoolean(container, (boolean) value);
        } else if (value instanceof Byte) {
            return readByte(container, (byte) value);
        } else if (value instanceof Short) {
            return readShort(container, (short) value);
        } else if (value instanceof Integer) {
            return readInt(container, (int) value);
        } else if (value instanceof Long) {
            return readLong(container, (long) value);
        } else if (value instanceof Float) {
            return readFloat(container, (float) value);
        } else if (value instanceof Double) {
            return readDouble(container, (double) value);
        } else if (value instanceof Character) {
            return readShort(container, (short) ((char) value));
        } else {
            CompilerDirectives.transferToInterpreter();
            throw new UnsupportedOperationException("Cannot handle unboxed value: " + value);
        }
    }

    @Specialization
    public Object readPointer(LLVMDebugValueContainer container, LLVMAddress address) {
        instantiate(container, new LLVMConstantValueProvider.Address(address));
        return null;
    }

    @Specialization
    public Object readFunctionHandle(LLVMDebugValueContainer container, LLVMFunctionHandle value) {
        instantiate(container, new LLVMConstantValueProvider.Function(value, getContext()));
        return null;
    }

    @Specialization
    public Object readFloat(LLVMDebugValueContainer container, float value) {
        instantiate(container, new LLVMConstantValueProvider.Float(value));
        return null;
    }

    @Specialization
    public Object readDouble(LLVMDebugValueContainer container, double value) {
        instantiate(container, new LLVMConstantValueProvider.Double(value));
        return null;
    }

    @Specialization
    public Object read80BitFloat(LLVMDebugValueContainer container, LLVM80BitFloat value) {
        instantiate(container, new LLVMConstantValueProvider.LLVM80BitFloat(value));
        return null;
    }

    @Specialization
    public Object readInterop(LLVMDebugValueContainer container, TruffleObject value) {
        instantiate(container, new LLVMConstantValueProvider.InteropValue(value));
        return null;
    }

    @Specialization
    public Object readLLVMInterop(LLVMDebugValueContainer container, LLVMTruffleObject value) {
        instantiate(container, new LLVMConstantValueProvider.InteropValue(value.getObject(), value.getOffset()));
        return null;
    }

    @Specialization
    public Object readGlobal(LLVMDebugValueContainer container, LLVMGlobalVariable value, @Cached("createGlobalAccess()") LLVMGlobalVariableAccess globalAccess) {
        instantiate(container, new LLVMGlobalVariableValueProvider(varName, value, globalAccess));
        return null;
    }

    @Specialization
    public Object readI1Vector(LLVMDebugValueContainer container, LLVMI1Vector value) {
        instantiate(container, new LLVMConstantVectorValueProvider.I1(value));
        return null;
    }

    @Specialization
    public Object readI8Vector(LLVMDebugValueContainer container, LLVMI8Vector value) {
        instantiate(container, new LLVMConstantVectorValueProvider.I8(value));
        return null;
    }

    @Specialization
    public Object readI16Vector(LLVMDebugValueContainer container, LLVMI16Vector value) {
        instantiate(container, new LLVMConstantVectorValueProvider.I16(value));
        return null;
    }

    @Specialization
    public Object readI32Vector(LLVMDebugValueContainer container, LLVMI32Vector value) {
        instantiate(container, new LLVMConstantVectorValueProvider.I32(value));
        return null;
    }

    @Specialization
    public Object readI64Vector(LLVMDebugValueContainer container, LLVMI64Vector value) {
        instantiate(container, new LLVMConstantVectorValueProvider.I64(value));
        return null;
    }

    @Specialization
    public Object readFloatVector(LLVMDebugValueContainer container, LLVMFloatVector value) {
        instantiate(container, new LLVMConstantVectorValueProvider.Float(value));
        return null;
    }

    @Specialization
    public Object readDoubleVector(LLVMDebugValueContainer container, LLVMDoubleVector value) {
        instantiate(container, new LLVMConstantVectorValueProvider.Double(value));
        return null;
    }

    @Specialization
    public Object readAddressVector(LLVMDebugValueContainer container, LLVMAddressVector value) {
        instantiate(container, new LLVMConstantVectorValueProvider.Address(value));
        return null;
    }

    @Specialization
    public Object initializeOnBoolean(VirtualFrame frame, @SuppressWarnings("unused") Object defaultValue, boolean value) {
        return readBoolean(setupContainer(frame), value);
    }

    @Specialization
    public Object initializeOnByte(VirtualFrame frame, @SuppressWarnings("unused") Object defaultValue, byte value) {
        return readByte(setupContainer(frame), value);
    }

    @Specialization
    public Object initializeOnShort(VirtualFrame frame, @SuppressWarnings("unused") Object defaultValue, short value) {
        return readShort(setupContainer(frame), value);
    }

    @Specialization
    public Object initializeOnInt(VirtualFrame frame, @SuppressWarnings("unused") Object defaultValue, int value) {
        return readInt(setupContainer(frame), value);
    }

    @Specialization
    public Object initializeOnLong(VirtualFrame frame, @SuppressWarnings("unused") Object defaultValue, long value) {
        return readLong(setupContainer(frame), value);
    }

    @Specialization
    public Object initializeOnIVarBit(VirtualFrame frame, @SuppressWarnings("unused") Object defaultValue, LLVMIVarBit value) {
        return readIVarBit(setupContainer(frame), value);
    }

    @Specialization
    public Object initializeOnBoxedPrimitive(VirtualFrame frame, @SuppressWarnings("unused") Object defaultValue, LLVMBoxedPrimitive value) {
        return readBoxedPrimitive(setupContainer(frame), value);
    }

    @Specialization
    public Object initializeOnPointer(VirtualFrame frame, @SuppressWarnings("unused") Object defaultValue, LLVMAddress address) {
        return readPointer(setupContainer(frame), address);
    }

    @Specialization
    public Object initializeOnFunctionHandle(VirtualFrame frame, @SuppressWarnings("unused") Object defaultValue, LLVMFunctionHandle value) {
        return readFunctionHandle(setupContainer(frame), value);
    }

    @Specialization
    public Object initializeOnFloat(VirtualFrame frame, @SuppressWarnings("unused") Object defaultValue, float value) {
        return readFloat(setupContainer(frame), value);
    }

    @Specialization
    public Object initializeOnDouble(VirtualFrame frame, @SuppressWarnings("unused") Object defaultValue, double value) {
        return readDouble(setupContainer(frame), value);
    }

    @Specialization
    public Object initializeOn80BitFloat(VirtualFrame frame, @SuppressWarnings("unused") Object defaultValue, LLVM80BitFloat value) {
        return read80BitFloat(setupContainer(frame), value);
    }

    @Specialization
    public Object initializeOnInterop(VirtualFrame frame, @SuppressWarnings("unused") Object defaultValue, TruffleObject value) {
        return readInterop(setupContainer(frame), value);
    }

    @Specialization
    public Object initializeOnLLVMInterop(VirtualFrame frame, @SuppressWarnings("unused") Object defaultValue, LLVMTruffleObject value) {
        return readLLVMInterop(setupContainer(frame), value);
    }

    @Specialization
    public Object initializeOnI1Vector(VirtualFrame frame, @SuppressWarnings("unused") Object defaultValue, LLVMI1Vector value) {
        return readI1Vector(setupContainer(frame), value);
    }

    @Specialization
    public Object initializeOnI8Vector(VirtualFrame frame, @SuppressWarnings("unused") Object defaultValue, LLVMI8Vector value) {
        return readI8Vector(setupContainer(frame), value);
    }

    @Specialization
    public Object initializeOnI16Vector(VirtualFrame frame, @SuppressWarnings("unused") Object defaultValue, LLVMI16Vector value) {
        return readI16Vector(setupContainer(frame), value);
    }

    @Specialization
    public Object initializeOnI32Vector(VirtualFrame frame, @SuppressWarnings("unused") Object defaultValue, LLVMI32Vector value) {
        return readI32Vector(setupContainer(frame), value);
    }

    @Specialization
    public Object initializeOnI64Vector(VirtualFrame frame, @SuppressWarnings("unused") Object defaultValue, LLVMI64Vector value) {
        return readI64Vector(setupContainer(frame), value);
    }

    @Specialization
    public Object initializeOnFloatVector(VirtualFrame frame, @SuppressWarnings("unused") Object defaultValue, LLVMFloatVector value) {
        return readFloatVector(setupContainer(frame), value);
    }

    @Specialization
    public Object initializeOnDoubleVector(VirtualFrame frame, @SuppressWarnings("unused") Object defaultValue, LLVMDoubleVector value) {
        return readDoubleVector(setupContainer(frame), value);
    }

    @Specialization
    public Object initializeOnAddressVector(VirtualFrame frame, @SuppressWarnings("unused") Object defaultValue, LLVMAddressVector value) {
        return readAddressVector(setupContainer(frame), value);
    }

    @Specialization
    public Object initializeOnGlobal(VirtualFrame frame, @SuppressWarnings("unused") Object defaultValue, LLVMGlobalVariable global,
                    @Cached("createGlobalAccess()") LLVMGlobalVariableAccess globalAccess) {
        return readGlobal(setupContainer(frame), global, globalAccess);
    }

    private void instantiate(LLVMDebugValueContainer container, LLVMDebugValueProvider value) {
        final LLVMDebugObject object = LLVMDebugObject.instantiate(varType, 0L, value);
        container.addMember(varName, object);
    }

    private LLVMDebugValueContainer setupContainer(VirtualFrame frame) {
        final LLVMDebugValueContainer container = LLVMDebugValueContainer.createContainer();
        frame.setObject(containerSlot, container);
        return container;
    }
}
