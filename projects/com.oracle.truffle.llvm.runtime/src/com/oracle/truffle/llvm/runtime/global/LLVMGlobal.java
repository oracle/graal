/*
 * Copyright (c) 2016, 2017, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.runtime.global;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleLanguage.ContextReference;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.FrameSlotTypeException;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.llvm.runtime.LLVMAddress;
import com.oracle.truffle.llvm.runtime.LLVMContext;
import com.oracle.truffle.llvm.runtime.LLVMFunctionDescriptor;
import com.oracle.truffle.llvm.runtime.LLVMLanguage;
import com.oracle.truffle.llvm.runtime.LLVMTruffleObject;
import com.oracle.truffle.llvm.runtime.LLVMVirtualAllocationAddress;
import com.oracle.truffle.llvm.runtime.memory.LLVMMemory;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMObjectNativeLibrary;
import com.oracle.truffle.llvm.runtime.types.PrimitiveType;
import com.oracle.truffle.llvm.runtime.types.Type;

public final class LLVMGlobal implements LLVMObjectNativeLibrary.Provider {

    private final String name;
    private final FrameSlot slot;
    private final Type globalType;

    public static LLVMGlobal external(LLVMContext context, Object symbol, String name, Type type, LLVMAddress pointer) {
        LLVMGlobal global = new LLVMGlobal(name, context.getGlobalFrameSlot(symbol, type), type);
        global.setFrame(context, new Native(pointer.getVal()));
        return global;
    }

    public static LLVMGlobal internal(LLVMContext context, Object symbol, String name, Type type) {
        return new LLVMGlobal(name, context.getGlobalFrameSlot(symbol, type), type);
    }

    private LLVMGlobal(String name, FrameSlot slot, Type globalType) {
        this.name = name;
        this.slot = slot;
        this.globalType = globalType;
    }

    public String getName() {
        return name;
    }

    boolean isNative(LLVMContext context) {
        Object value = context.getGlobalFrame().getValue(slot);
        return value instanceof Native;
    }

    Object getFrame(LLVMContext context) {
        Object value = context.getGlobalFrame().getValue(slot);
        if (value == null) {
            return LLVMAddress.nullPointer();
        } else {
            return value;
        }
    }

    boolean getFrameI1(LLVMMemory memory, LLVMContext context) {
        try {
            return context.getGlobalFrame().getBoolean(slot);
        } catch (FrameSlotTypeException e) {
            CompilerDirectives.transferToInterpreter();
            getAsNative(memory, context);
            return getNativeI1(memory, context);
        }
    }

    byte getFrameI8(LLVMMemory memory, LLVMContext context) {
        try {
            return context.getGlobalFrame().getByte(slot);
        } catch (FrameSlotTypeException e) {
            CompilerDirectives.transferToInterpreter();
            getAsNative(memory, context);
            return getNativeI8(memory, context);
        }
    }

    short getFrameI16(LLVMMemory memory, LLVMContext context) {
        try {
            return (short) context.getGlobalFrame().getInt(slot);
        } catch (FrameSlotTypeException e) {
            CompilerDirectives.transferToInterpreter();
            getAsNative(memory, context);
            return getNativeI16(memory, context);
        }
    }

    int getFrameI32(LLVMMemory memory, LLVMContext context) {
        try {
            return context.getGlobalFrame().getInt(slot);
        } catch (FrameSlotTypeException e) {
            CompilerDirectives.transferToInterpreter();
            getAsNative(memory, context);
            return getNativeI32(memory, context);
        }
    }

    long getFrameI64(LLVMMemory memory, LLVMContext context) {
        try {
            return context.getGlobalFrame().getLong(slot);
        } catch (FrameSlotTypeException e) {
            CompilerDirectives.transferToInterpreter();
            getAsNative(memory, context);
            return getNativeI64(memory, context);
        }
    }

    float getFrameFloat(LLVMMemory memory, LLVMContext context) {
        try {
            return context.getGlobalFrame().getFloat(slot);
        } catch (FrameSlotTypeException e) {
            CompilerDirectives.transferToInterpreter();
            getAsNative(memory, context);
            return getNativeFloat(memory, context);
        }
    }

    double getFrameDouble(LLVMMemory memory, LLVMContext context) {
        try {
            return context.getGlobalFrame().getDouble(slot);
        } catch (FrameSlotTypeException e) {
            CompilerDirectives.transferToInterpreter();
            getAsNative(memory, context);
            return getNativeDouble(memory, context);
        }
    }

    LLVMAddress getNative(LLVMMemory memory, LLVMContext context) {
        Native value = (Native) context.getGlobalFrame().getValue(slot);
        return memory.getAddress(value.getPointer());
    }

    boolean getNativeI1(LLVMMemory memory, LLVMContext context) {
        Native value = (Native) context.getGlobalFrame().getValue(slot);
        return memory.getI1(value.getPointer());
    }

    byte getNativeI8(LLVMMemory memory, LLVMContext context) {
        Native value = (Native) context.getGlobalFrame().getValue(slot);
        return memory.getI8(value.getPointer());
    }

    short getNativeI16(LLVMMemory memory, LLVMContext context) {
        Native value = (Native) context.getGlobalFrame().getValue(slot);
        return memory.getI16(value.getPointer());
    }

    int getNativeI32(LLVMMemory memory, LLVMContext context) {
        Native value = (Native) context.getGlobalFrame().getValue(slot);
        return memory.getI32(value.getPointer());
    }

    long getNativeI64(LLVMMemory memory, LLVMContext context) {
        Native value = (Native) context.getGlobalFrame().getValue(slot);
        return memory.getI64(value.getPointer());
    }

    float getNativeFloat(LLVMMemory memory, LLVMContext context) {
        Native value = (Native) context.getGlobalFrame().getValue(slot);
        return memory.getFloat(value.getPointer());
    }

    double getNativeDouble(LLVMMemory memory, LLVMContext context) {
        Native value = (Native) context.getGlobalFrame().getValue(slot);
        return memory.getDouble(value.getPointer());
    }

    void setFrame(LLVMContext context, Object object) {
        context.getGlobalFrame().setObject(slot, object);
    }

    void setNative(LLVMMemory memory, LLVMContext context, LLVMAddress address) {
        Native value = (Native) context.getGlobalFrame().getValue(slot);
        memory.putAddress(value.getPointer(), address);
    }

    void setFrameI1(LLVMContext context, boolean value) {
        context.getGlobalFrame().setBoolean(slot, value);
    }

    void setNativeI1(LLVMMemory memory, LLVMContext context, boolean value) {
        Native n = (Native) context.getGlobalFrame().getValue(slot);
        memory.putI1(n.getPointer(), value);
    }

    void setNativeDouble(LLVMMemory memory, LLVMContext context, double value) {
        Native n = (Native) context.getGlobalFrame().getValue(slot);
        memory.putDouble(n.getPointer(), value);
    }

    void setFrameDouble(LLVMContext context, double value) {
        context.getGlobalFrame().setDouble(slot, value);
    }

    void setNativeFloat(LLVMMemory memory, LLVMContext context, float value) {
        Native n = (Native) context.getGlobalFrame().getValue(slot);
        memory.putFloat(n.getPointer(), value);
    }

    void setFrameFloat(LLVMContext context, float value) {
        context.getGlobalFrame().setFloat(slot, value);
    }

    void setNativeI64(LLVMMemory memory, LLVMContext context, long value) {
        Native n = (Native) context.getGlobalFrame().getValue(slot);
        memory.putI64(n.getPointer(), value);
    }

    void setFrameI64(LLVMContext context, long value) {
        context.getGlobalFrame().setLong(slot, value);
    }

    void setNativeI32(LLVMMemory memory, LLVMContext context, int value) {
        Native n = (Native) context.getGlobalFrame().getValue(slot);
        memory.putI32(n.getPointer(), value);
    }

    void setFrameI32(LLVMContext context, int value) {
        context.getGlobalFrame().setInt(slot, value);
    }

    void setNativeI16(LLVMMemory memory, LLVMContext context, short value) {
        Native n = (Native) context.getGlobalFrame().getValue(slot);
        memory.putI16(n.getPointer(), value);
    }

    void setFrameI16(LLVMContext context, short value) {
        context.getGlobalFrame().setInt(slot, value);
    }

    void setNativeI8(LLVMMemory memory, LLVMContext context, byte value) {
        Native n = (Native) context.getGlobalFrame().getValue(slot);
        memory.putI8(n.getPointer(), value);
    }

    void setFrameI8(LLVMContext context, byte value) {
        context.getGlobalFrame().setByte(slot, value);
    }

    private static final class Native {
        private final long pointer;

        private Native(long pointer) {
            this.pointer = pointer;
        }

        long getPointer() {
            return pointer;
        }
    }

    public static LLVMAddress toNative(LLVMContext context, LLVMMemory memory, LLVMGlobal global) {
        return LLVMAddress.fromLong(global.getAsNative(memory, context).getPointer());
    }

    public static void free(LLVMContext context, LLVMGlobal global) {
        Object content = global.getFrame(context);
        if (content instanceof Native) {
            global.setFrame(context, null);
        }
    }

    @Override
    public LLVMObjectNativeLibrary createLLVMObjectNativeLibrary() {
        return new LLVMGlobalNativeLibrary();
    }

    private static final class LLVMGlobalNativeLibrary extends LLVMObjectNativeLibrary {

        @CompilationFinal private ContextReference<LLVMContext> contextRef;

        @CompilationFinal private LLVMMemory memory;

        private LLVMMemory getMemory() {
            if (memory == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                memory = LLVMLanguage.getLanguage().getCapability(LLVMMemory.class);
            }
            return memory;
        }

        private LLVMContext getContext() {
            if (contextRef == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                contextRef = LLVMLanguage.getLLVMContextReference();
            }
            return contextRef.get();
        }

        @Override
        public Object toNative(VirtualFrame frame, Object obj) throws InteropException {
            LLVMGlobal global = (LLVMGlobal) obj;
            global.getAsNative(getMemory(), getContext());
            return global;
        }

        @Override
        public boolean isPointer(VirtualFrame frame, Object obj) {
            LLVMGlobal global = (LLVMGlobal) obj;
            Object value = getContext().getGlobalFrame().getValue(global.slot);
            return value instanceof Native;
        }

        @Override
        public boolean guard(Object obj) {
            return obj instanceof LLVMGlobal;
        }

        @Override
        public long asPointer(VirtualFrame frame, Object obj) throws InteropException {
            LLVMGlobal global = (LLVMGlobal) obj;
            Native value = (Native) getContext().getGlobalFrame().getValue(global.slot);
            return value.getPointer();
        }
    }

    private Native getAsNative(LLVMMemory memory, LLVMContext context) {
        Object value = context.getGlobalFrame().getValue(slot);
        if (value instanceof Native) {
            return (Native) value;
        }

        return transformToNative(memory, context, value);
    }

    @TruffleBoundary
    private Native transformToNative(LLVMMemory memory, LLVMContext context, Object value) {
        int byteSize = context.getByteSize(globalType);
        long a = context.getGlobalsStack().allocateStackMemory(byteSize);
        Native n = new Native(a);
        context.getGlobalFrame().setObject(slot, n);

        if (value == null) {
            return n;
        }

        if (globalType instanceof PrimitiveType) {
            switch (((PrimitiveType) globalType).getPrimitiveKind()) {
                case DOUBLE:
                    memory.putDouble(a, (double) value);
                    break;
                case FLOAT:
                    memory.putFloat(a, (float) value);
                    break;
                case I1:
                    memory.putI1(a, (boolean) value);
                    break;
                case I16:
                    memory.putI16(a, (short) (int) value);
                    break;
                case I32:
                    memory.putI32(a, (int) value);
                    break;
                case I64:
                    memory.putI64(a, (long) value);
                    break;
                case I8:
                    memory.putI8(a, (byte) value);
                    break;
                default:
                    putOther(memory, context, a, value);
                    break;
            }
        } else {
            putOther(memory, context, a, value);
        }

        return n;
    }

    private static void putOther(LLVMMemory memory, LLVMContext context, long address, Object managedValue) {
        if (managedValue instanceof LLVMFunctionDescriptor) {
            long pointer = ((LLVMFunctionDescriptor) managedValue).toNative().asPointer();
            memory.putAddress(address, pointer);
        } else if (managedValue instanceof LLVMAddress) {
            memory.putAddress(address, (LLVMAddress) managedValue);
        } else if (managedValue instanceof LLVMGlobal) {
            memory.putAddress(address, ((LLVMGlobal) managedValue).getAsNative(memory, context).getPointer());
        } else if (managedValue instanceof TruffleObject || managedValue instanceof LLVMTruffleObject) {
            throw new IllegalStateException("Cannot resolve address of a foreign TruffleObject: " + managedValue);
        } else if (managedValue instanceof LLVMVirtualAllocationAddress) {
            throw new IllegalStateException("Cannot resolve address of a managed allocation.");
        } else {
            throw new AssertionError("Unknown type: " + managedValue.getClass());
        }
    }

    public Type getType() {
        return globalType;
    }

}
