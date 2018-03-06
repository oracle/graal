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

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleLanguage.ContextReference;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.llvm.runtime.LLVMAddress;
import com.oracle.truffle.llvm.runtime.LLVMContext;
import com.oracle.truffle.llvm.runtime.LLVMFunctionDescriptor;
import com.oracle.truffle.llvm.runtime.LLVMLanguage;
import com.oracle.truffle.llvm.runtime.LLVMTruffleObject;
import com.oracle.truffle.llvm.runtime.LLVMVirtualAllocationAddress;
import com.oracle.truffle.llvm.runtime.global.LLVMGlobalFactory.GetFrameNodeGen;
import com.oracle.truffle.llvm.runtime.global.LLVMGlobalFactory.GetNativePointerNodeGen;
import com.oracle.truffle.llvm.runtime.global.LLVMGlobalFactory.GetSlotNodeGen;
import com.oracle.truffle.llvm.runtime.global.LLVMGlobalFactory.IsNativeNodeGen;
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

    public FrameSlot getSlot() {
        return slot;
    }

    private void setFrame(LLVMContext context, Object object) {
        context.getGlobalFrame().setObject(slot, object);
    }

    static final class Native {
        private final long pointer;

        private Native(long pointer) {
            this.pointer = pointer;
        }

        long getPointer() {
            return pointer;
        }
    }

    @SuppressWarnings("unused")
    abstract static class IsNative extends Node {
        abstract boolean execute(LLVMContext context, LLVMGlobal global);

        static IsNative create() {
            return IsNativeNodeGen.create();
        }

        MaterializedFrame getFrame(LLVMContext context) {
            return context.getGlobalFrame();
        }

        Assumption getSingleContextAsssumption() {
            return LLVMLanguage.SINGLE_CONTEXT_ASSUMPTION;
        }

        @Specialization(assumptions = "getSingleContextAsssumption()", guards = {"global == cachedGlobal"})
        boolean doCachedSingleThread(LLVMContext context, LLVMGlobal global,
                        @Cached("global") LLVMGlobal cachedGlobal,
                        @Cached("getFrame(context)") MaterializedFrame frame) {
            return frame.getValue(cachedGlobal.slot) instanceof Native;
        }

        @Specialization(assumptions = "getSingleContextAsssumption()", replaces = "doCachedSingleThread")
        boolean doSingleThread(LLVMContext context, LLVMGlobal global,
                        @Cached("getFrame(context)") MaterializedFrame frame) {
            return frame.getValue(global.slot) instanceof Native;
        }

        @Specialization(replaces = {"doCachedSingleThread", "doSingleThread"})
        boolean generic(LLVMContext context, LLVMGlobal global) {
            return getFrame(context).getValue(global.slot) instanceof Native;
        }
    }

    @SuppressWarnings("unused")
    abstract static class GetNativePointer extends Node {
        abstract long execute(LLVMContext context, LLVMGlobal global);

        public static GetNativePointer create() {
            return GetNativePointerNodeGen.create();
        }

        long getValue(LLVMContext context, LLVMGlobal global) {
            return ((Native) context.getGlobalFrame().getValue(global.slot)).getPointer();
        }

        MaterializedFrame getFrame(LLVMContext context) {
            return context.getGlobalFrame();
        }

        Assumption getSingleContextAsssumption() {
            return LLVMLanguage.SINGLE_CONTEXT_ASSUMPTION;
        }

        @Specialization(assumptions = "getSingleContextAsssumption()", guards = {"global == cachedGlobal"})
        long doCachedSingleThread(LLVMContext context, LLVMGlobal global, @Cached("global") LLVMGlobal cachedGlobal, @Cached("getValue(context, global)") long nativeValue) {
            return nativeValue;
        }

        @Specialization(assumptions = "getSingleContextAsssumption()", replaces = "doCachedSingleThread")
        long doSingleThread(LLVMContext context, LLVMGlobal global, @Cached("getFrame(context)") MaterializedFrame frame) {
            return ((Native) frame.getValue(global.slot)).getPointer();
        }

        @Specialization(replaces = {"doCachedSingleThread", "doSingleThread"})
        long generic(LLVMContext context, LLVMGlobal global) {
            return ((Native) getFrame(context).getValue(global.slot)).getPointer();
        }
    }

    @SuppressWarnings("unused")
    abstract static class GetFrame extends Node {
        abstract MaterializedFrame execute(LLVMContext context);

        public static GetFrame create() {
            return GetFrameNodeGen.create();
        }

        MaterializedFrame getFrame(LLVMContext context) {
            return context.getGlobalFrame();
        }

        Assumption getSingleContextAsssumption() {
            return LLVMLanguage.SINGLE_CONTEXT_ASSUMPTION;
        }

        @Specialization(assumptions = "getSingleContextAsssumption()")
        MaterializedFrame doSingleThread(LLVMContext context, @Cached("getFrame(context)") MaterializedFrame frame) {
            return frame;
        }

        @Specialization(replaces = {"doSingleThread"})
        MaterializedFrame generic(LLVMContext context) {
            return getFrame(context);
        }
    }

    @SuppressWarnings("unused")
    abstract static class GetSlot extends Node {
        abstract FrameSlot execute(LLVMGlobal global);

        public static GetSlot create() {
            return GetSlotNodeGen.create();
        }

        FrameSlot getSlot(LLVMGlobal global) {
            return global.slot;
        }

        @Specialization(guards = {"global == cachedGlobal"})
        FrameSlot doCached(LLVMGlobal global, @Cached("global") LLVMGlobal cachedGlobal, @Cached("getSlot(global)") FrameSlot slot) {
            return slot;
        }

        @Specialization(replaces = {"doCached"})
        FrameSlot generic(LLVMGlobal global) {
            return getSlot(global);
        }
    }

    public static LLVMAddress toNative(LLVMContext context, LLVMMemory memory, LLVMGlobal global) {
        return LLVMAddress.fromLong(global.getAsNative(memory, context).getPointer());
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

    Native getAsNative(LLVMMemory memory, LLVMContext context) {
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

    @TruffleBoundary
    private static void putOther(LLVMMemory memory, LLVMContext context, long address, Object managedValue) {
        if (managedValue instanceof LLVMFunctionDescriptor) {
            long pointer = ((LLVMFunctionDescriptor) managedValue).toNative().asPointer();
            memory.putAddress(address, pointer);
        } else if (managedValue instanceof LLVMAddress) {
            memory.putAddress(address, (LLVMAddress) managedValue);
        } else if (managedValue instanceof LLVMGlobal) {
            memory.putAddress(address, ((LLVMGlobal) managedValue).getAsNative(memory, context).getPointer());
        } else if (managedValue instanceof LLVMTruffleObject) {
            try {
                Object nativized = ForeignAccess.sendToNative(Message.TO_NATIVE.createNode(), ((LLVMTruffleObject) managedValue).getObject());
                long toAddr = ForeignAccess.sendAsPointer(Message.AS_POINTER.createNode(), (TruffleObject) nativized);
                memory.putAddress(address, toAddr + ((LLVMTruffleObject) managedValue).getOffset());
            } catch (UnsupportedMessageException e) {
                throw new IllegalStateException("Cannot resolve address of a foreign TruffleObject: " + managedValue);
            }
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
