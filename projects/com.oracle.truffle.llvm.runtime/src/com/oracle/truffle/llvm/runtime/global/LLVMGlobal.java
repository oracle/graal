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
import com.oracle.truffle.llvm.runtime.debug.LLVMSourceSymbol;
import com.oracle.truffle.llvm.runtime.debug.LLVMSourceType;
import com.oracle.truffle.llvm.runtime.global.LLVMGlobalFactory.GetFrameNodeGen;
import com.oracle.truffle.llvm.runtime.global.LLVMGlobalFactory.GetGlobalValueNodeGen;
import com.oracle.truffle.llvm.runtime.global.LLVMGlobalFactory.GetNativePointerNodeGen;
import com.oracle.truffle.llvm.runtime.global.LLVMGlobalFactory.GetSlotNodeGen;
import com.oracle.truffle.llvm.runtime.global.LLVMGlobalFactory.IsNativeNodeGen;
import com.oracle.truffle.llvm.runtime.global.LLVMGlobalFactory.IsObjectStoreNodeGen;
import com.oracle.truffle.llvm.runtime.interop.access.LLVMInteropType;
import com.oracle.truffle.llvm.runtime.memory.LLVMMemory;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMObjectNativeLibrary;
import com.oracle.truffle.llvm.runtime.types.PrimitiveType;
import com.oracle.truffle.llvm.runtime.types.Type;

public final class LLVMGlobal implements LLVMObjectNativeLibrary.Provider {

    private final String name;
    private final FrameSlot slot;
    private final Type globalType;
    private final LLVMSourceSymbol sourceSymbol;

    @CompilationFinal private boolean interopTypeCached = false;
    @CompilationFinal private LLVMInteropType interopType;

    public static LLVMGlobal external(LLVMContext context, Object symbol, String name, Type type, LLVMAddress pointer, LLVMSourceSymbol sourceSymbol) {
        LLVMGlobal global = new LLVMGlobal(name, context.getGlobalFrameSlot(symbol, type), type, sourceSymbol);
        global.setFrame(context, pointer);
        return global;
    }

    public static LLVMGlobal internal(LLVMContext context, Object symbol, String name, Type type, LLVMSourceSymbol sourceSymbol) {
        return new LLVMGlobal(name, context.getGlobalFrameSlot(symbol, type), type, sourceSymbol);
    }

    public static Object toManagedStore(Object object) {
        return new Managed(object);
    }

    public static Object fromManagedStore(Object store) {
        if (store instanceof Managed) {
            return ((Managed) store).wrapped;
        }
        return store;
    }

    private LLVMGlobal(String name, FrameSlot slot, Type globalType, LLVMSourceSymbol sourceSymbol) {
        this.name = name;
        this.slot = slot;
        this.globalType = globalType;
        this.sourceSymbol = sourceSymbol;
    }

    public String getName() {
        return name;
    }

    public FrameSlot getSlot() {
        return slot;
    }

    public LLVMSourceSymbol getSourceSymbol() {
        return sourceSymbol;
    }

    public LLVMSourceType getSourceType() {
        return sourceSymbol != null ? sourceSymbol.getType() : null;
    }

    public LLVMInteropType getInteropType() {
        if (!interopTypeCached) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            LLVMSourceType sourceType = getSourceType();
            if (sourceType == null) {
                interopType = LLVMInteropType.UNKNOWN;
            } else {
                interopType = LLVMInteropType.fromSourceType(sourceType);
            }
            interopTypeCached = true;
        }
        return interopType;
    }

    public String getSourceName() {
        return sourceSymbol != null ? sourceSymbol.getName() : name;
    }

    private void setFrame(LLVMContext context, Object object) {
        context.getGlobalFrame().setObject(slot, object);
    }

    /**
     * Used as a wrapper if the global variable's value is directly stored in the frame as a managed
     * object. This is also necessary to disambiguate between a pointer to the native store and a
     * pointer value.
     */
    private static final class Managed {
        final Object wrapped;

        protected Managed(Object wrapped) {
            this.wrapped = wrapped;
        }
    }

    abstract static class TestGlobalStateNode extends Node {
        public abstract boolean execute(LLVMContext context, LLVMGlobal global);

        @SuppressWarnings("unused")
        boolean doCheck(LLVMGlobal global, Object value) {
            throw new AssertionError("should not reach here");
        }

        MaterializedFrame getFrame(LLVMContext context) {
            return context.getGlobalFrame();
        }

        Assumption getSingleContextAssumption() {
            return LLVMLanguage.SINGLE_CONTEXT_ASSUMPTION;
        }

        @Specialization(assumptions = "getSingleContextAssumption()", guards = {"global == cachedGlobal"})
        boolean doCachedSingleThread(@SuppressWarnings("unused") LLVMContext context, @SuppressWarnings("unused") LLVMGlobal global,
                        @Cached("global") LLVMGlobal cachedGlobal,
                        @Cached("getFrame(context)") MaterializedFrame frame) {
            return doCheck(cachedGlobal, frame.getValue(cachedGlobal.slot));
        }

        @Specialization(assumptions = "getSingleContextAssumption()", replaces = "doCachedSingleThread")
        boolean doSingleThread(@SuppressWarnings("unused") LLVMContext context, LLVMGlobal global,
                        @Cached("getFrame(context)") MaterializedFrame frame) {
            return doCheck(global, frame.getValue(global.slot));
        }

        @Specialization(replaces = {"doCachedSingleThread", "doSingleThread"})
        boolean generic(LLVMContext context, LLVMGlobal global) {
            return doCheck(global, getFrame(context).getValue(global.slot));
        }

    }

    public abstract static class IsNative extends TestGlobalStateNode {
        @Override
        boolean doCheck(LLVMGlobal global, Object value) {
            return value instanceof LLVMAddress;
        }

        public static IsNative create() {
            return IsNativeNodeGen.create();
        }
    }

    public abstract static class IsObjectStore extends TestGlobalStateNode {
        @Override
        boolean doCheck(LLVMGlobal global, Object value) {
            return LLVMGlobal.isObjectStore(global.globalType, value);
        }

        public static IsObjectStore create() {
            return IsObjectStoreNodeGen.create();
        }
    }

    @SuppressWarnings("unused")
    abstract static class GetNativePointer extends Node {
        abstract long execute(LLVMContext context, LLVMGlobal global);

        public static GetNativePointer create() {
            return GetNativePointerNodeGen.create();
        }

        long getValue(LLVMContext context, LLVMGlobal global) {
            return ((LLVMAddress) context.getGlobalFrame().getValue(global.slot)).getVal();
        }

        MaterializedFrame getFrame(LLVMContext context) {
            return context.getGlobalFrame();
        }

        Assumption getSingleContextAsssumption() {
            return LLVMLanguage.SINGLE_CONTEXT_ASSUMPTION;
        }

        @Specialization(assumptions = "getSingleContextAsssumption()", guards = {"global == cachedGlobal"})
        long doCachedSingleThread(LLVMContext context, LLVMGlobal global,
                        @Cached("global") LLVMGlobal cachedGlobal,
                        @Cached("getValue(context, global)") long nativeValue) {
            return nativeValue;
        }

        @Specialization(assumptions = "getSingleContextAsssumption()", replaces = "doCachedSingleThread")
        long doSingleThread(LLVMContext context, LLVMGlobal global, @Cached("getFrame(context)") MaterializedFrame frame) {
            return ((LLVMAddress) frame.getValue(global.slot)).getVal();
        }

        @Specialization(replaces = {"doCachedSingleThread", "doSingleThread"})
        long generic(LLVMContext context, LLVMGlobal global) {
            return ((LLVMAddress) getFrame(context).getValue(global.slot)).getVal();
        }
    }

    @SuppressWarnings("unused")
    public abstract static class GetGlobalValueNode extends Node {
        public abstract Object execute(LLVMContext context, LLVMGlobal global);

        public static GetGlobalValueNode create() {
            return GetGlobalValueNodeGen.create();
        }

        MaterializedFrame getFrame(LLVMContext context) {
            return context.getGlobalFrame();
        }

        Assumption getSingleContextAsssumption() {
            return LLVMLanguage.SINGLE_CONTEXT_ASSUMPTION;
        }

        @Specialization(assumptions = "getSingleContextAsssumption()")
        Object doSingleThread(LLVMContext context, LLVMGlobal global,
                        @Cached("getFrame(context)") MaterializedFrame frame) {
            return frame.getValue(global.slot);
        }

        @Specialization(replaces = "doSingleThread")
        Object generic(LLVMContext context, LLVMGlobal global) {
            return getFrame(context).getValue(global.slot);
        }
    }

    @SuppressWarnings("unused")
    public abstract static class GetFrame extends Node {
        public abstract MaterializedFrame execute(LLVMContext context);

        public static GetFrame create() {
            return GetFrameNodeGen.create();
        }

        MaterializedFrame getFrame(LLVMContext context) {
            return context.getGlobalFrame();
        }

        Assumption getSingleContextAssumption() {
            return LLVMLanguage.SINGLE_CONTEXT_ASSUMPTION;
        }

        @Specialization(assumptions = "getSingleContextAssumption()")
        MaterializedFrame doSingleThread(LLVMContext context,
                        @Cached("getFrame(context)") MaterializedFrame frame) {
            return frame;
        }

        @Specialization(replaces = {"doSingleThread"})
        MaterializedFrame generic(LLVMContext context) {
            return getFrame(context);
        }
    }

    @SuppressWarnings("unused")
    public abstract static class GetSlot extends Node {
        public abstract FrameSlot execute(LLVMGlobal global);

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
        return global.getAsNative(memory, context);
    }

    @Override
    public LLVMObjectNativeLibrary createLLVMObjectNativeLibrary() {
        return new LLVMGlobalNativeLibrary();
    }

    private static final class LLVMGlobalNativeLibrary extends LLVMObjectNativeLibrary {

        @CompilationFinal private ContextReference<LLVMContext> contextRef;
        @CompilationFinal private LLVMMemory memory;
        @Child private LLVMObjectNativeLibrary recursiveLib;

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

        private LLVMObjectNativeLibrary getNativeLibrary() {
            if (recursiveLib == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                recursiveLib = insert(LLVMObjectNativeLibrary.createGeneric());
            }
            return recursiveLib;
        }

        @Override
        public Object toNative(Object obj) throws InteropException {
            LLVMGlobal global = (LLVMGlobal) obj;
            global.getAsNative(getMemory(), getContext());
            return global;
        }

        @Override
        public boolean isPointer(Object obj) {
            LLVMGlobal global = (LLVMGlobal) obj;
            Object value = getContext().getGlobalFrame().getValue(global.slot);
            return getNativeLibrary().isPointer(value);
        }

        @Override
        public boolean guard(Object obj) {
            return obj instanceof LLVMGlobal;
        }

        @Override
        public long asPointer(Object obj) throws InteropException {
            LLVMGlobal global = (LLVMGlobal) obj;
            Object value = getContext().getGlobalFrame().getValue(global.slot);
            return getNativeLibrary().asPointer(value);
        }
    }

    LLVMAddress getAsNative(LLVMMemory memory, LLVMContext context) {
        Object value = context.getGlobalFrame().getValue(slot);
        if (value instanceof LLVMAddress) {
            return (LLVMAddress) value;
        } else if (value instanceof Managed) {
            return transformToNative(memory, context, ((Managed) value).wrapped);
        } else if (value instanceof TruffleObject) {
            return transformToNative(context, (TruffleObject) value);
        } else if (value == null || globalType instanceof PrimitiveType) {
            return transformToNative(memory, context, value);
        }

        CompilerDirectives.transferToInterpreter();
        throw new IllegalStateException("unknown state of global variable");
    }

    @TruffleBoundary
    private LLVMAddress transformToNative(LLVMContext context, TruffleObject value) {
        try {
            Object nativized = ForeignAccess.sendToNative(Message.TO_NATIVE.createNode(), value);
            if (value != nativized) {
                context.getGlobalFrame().setObject(slot, nativized);
            }
            long toAddr = ForeignAccess.sendAsPointer(Message.AS_POINTER.createNode(), (TruffleObject) nativized);
            return LLVMAddress.fromLong(toAddr);
        } catch (UnsupportedMessageException e) {
            throw new IllegalStateException("Cannot resolve address of a foreign TruffleObject: " + value);
        }
    }

    @TruffleBoundary
    private LLVMAddress transformToNative(LLVMMemory memory, LLVMContext context, Object value) {
        int byteSize = context.getByteSize(globalType);
        long a = context.getGlobalsStack().allocateStackMemory(byteSize);
        LLVMAddress n = LLVMAddress.fromLong(a);
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
            memory.putAddress(address, ((LLVMGlobal) managedValue).getAsNative(memory, context).getVal());
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

    public static boolean isObjectStore(Type globalType, Object value) {
        return !(globalType instanceof PrimitiveType || value instanceof LLVMAddress || value instanceof Managed);
    }
}
