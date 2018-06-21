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
import com.oracle.truffle.llvm.runtime.LLVMContext;
import com.oracle.truffle.llvm.runtime.LLVMContext.ExternalLibrary;
import com.oracle.truffle.llvm.runtime.LLVMFunctionDescriptor;
import com.oracle.truffle.llvm.runtime.LLVMLanguage;
import com.oracle.truffle.llvm.runtime.LLVMSymbol;
import com.oracle.truffle.llvm.runtime.LLVMVirtualAllocationAddress;
import com.oracle.truffle.llvm.runtime.debug.scope.LLVMSourceSymbol;
import com.oracle.truffle.llvm.runtime.debug.type.LLVMSourceType;
import com.oracle.truffle.llvm.runtime.global.LLVMGlobalFactory.GetFrameNodeGen;
import com.oracle.truffle.llvm.runtime.global.LLVMGlobalFactory.GetGlobalValueNodeGen;
import com.oracle.truffle.llvm.runtime.global.LLVMGlobalFactory.GetNativePointerNodeGen;
import com.oracle.truffle.llvm.runtime.global.LLVMGlobalFactory.GetSlotNodeGen;
import com.oracle.truffle.llvm.runtime.global.LLVMGlobalFactory.IsNativeNodeGen;
import com.oracle.truffle.llvm.runtime.global.LLVMGlobalFactory.IsObjectStoreNodeGen;
import com.oracle.truffle.llvm.runtime.interop.access.LLVMInteropType;
import com.oracle.truffle.llvm.runtime.memory.LLVMMemory;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMObjectNativeLibrary;
import com.oracle.truffle.llvm.runtime.pointer.LLVMManagedPointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMNativePointer;
import com.oracle.truffle.llvm.runtime.types.PointerType;
import com.oracle.truffle.llvm.runtime.types.PrimitiveType;
import com.oracle.truffle.llvm.runtime.types.Type;

public final class LLVMGlobal implements LLVMSymbol, LLVMObjectNativeLibrary.Provider {

    private final LLVMSourceSymbol sourceSymbol;
    private final FrameSlot slot;
    private final boolean readOnly;

    @CompilationFinal private String name;
    @CompilationFinal private PointerType type;
    @CompilationFinal private ExternalLibrary library;
    @CompilationFinal private boolean interopTypeCached;
    @CompilationFinal private LLVMInteropType interopType;

    public static LLVMGlobal create(LLVMContext context, String name, PointerType type, LLVMSourceSymbol sourceSymbol, boolean readOnly) {
        return new LLVMGlobal(context, name, type, sourceSymbol, readOnly);
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

    private LLVMGlobal(LLVMContext context, String name, PointerType type, LLVMSourceSymbol sourceSymbol, boolean readOnly) {
        this.name = name;
        this.type = type;
        this.sourceSymbol = sourceSymbol;
        this.slot = context.getGlobalFrameSlot(this, type);
        this.readOnly = readOnly;

        this.library = null;
        this.interopTypeCached = false;
        this.interopType = null;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public void setName(String name) {
        this.name = name;
    }

    public FrameSlot getSlot() {
        return slot;
    }

    @Override
    public ExternalLibrary getLibrary() {
        return library;
    }

    public LLVMNativePointer bindToNativeAddress(LLVMContext context, long nativeAddress) {
        LLVMNativePointer n = LLVMNativePointer.create(nativeAddress);
        context.getGlobalFrame().setObject(slot, n);
        return n;
    }

    public LLVMInteropType getInteropType() {
        if (!interopTypeCached) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            LLVMSourceType sourceType = sourceSymbol != null ? sourceSymbol.getType() : null;
            interopType = sourceType == null ? LLVMInteropType.UNKNOWN : LLVMInteropType.fromSourceType(sourceType);
            interopTypeCached = true;
        }
        return interopType;
    }

    public String getSourceName() {
        return sourceSymbol != null ? sourceSymbol.getName() : name;
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

    abstract static class TestGlobalStateNode extends LLVMNode {
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
            return doCheck(cachedGlobal, frame.getValue(cachedGlobal.getSlot()));
        }

        @Specialization(assumptions = "getSingleContextAssumption()", replaces = "doCachedSingleThread")
        boolean doSingleThread(@SuppressWarnings("unused") LLVMContext context, LLVMGlobal global,
                        @Cached("getFrame(context)") MaterializedFrame frame) {
            return doCheck(global, frame.getValue(global.getSlot()));
        }

        @Specialization(replaces = {"doCachedSingleThread", "doSingleThread"})
        boolean generic(LLVMContext context, LLVMGlobal global) {
            return doCheck(global, getFrame(context).getValue(global.getSlot()));
        }

    }

    public abstract static class IsNative extends TestGlobalStateNode {
        @Override
        boolean doCheck(LLVMGlobal global, Object value) {
            return LLVMNativePointer.isInstance(value);
        }

        public static IsNative create() {
            return IsNativeNodeGen.create();
        }
    }

    public abstract static class IsObjectStore extends TestGlobalStateNode {
        @Override
        boolean doCheck(LLVMGlobal global, Object value) {
            return LLVMGlobal.isObjectStore(global.getPointeeType(), value);
        }

        public static IsObjectStore create() {
            return IsObjectStoreNodeGen.create();
        }
    }

    @SuppressWarnings("unused")
    abstract static class GetNativePointer extends LLVMNode {
        abstract long execute(LLVMContext context, LLVMGlobal global);

        public static GetNativePointer create() {
            return GetNativePointerNodeGen.create();
        }

        long getValue(LLVMContext context, LLVMGlobal global) {
            return LLVMNativePointer.cast(context.getGlobalFrame().getValue(global.getSlot())).asNative();
        }

        MaterializedFrame getFrame(LLVMContext context) {
            return context.getGlobalFrame();
        }

        Assumption getSingleContextAssumption() {
            return LLVMLanguage.SINGLE_CONTEXT_ASSUMPTION;
        }

        @Specialization(assumptions = "getSingleContextAssumption()", guards = {"global == cachedGlobal"})
        long doCachedSingleThread(LLVMContext context, LLVMGlobal global,
                        @Cached("global") LLVMGlobal cachedGlobal,
                        @Cached("getValue(context, global)") long nativeValue) {
            return nativeValue;
        }

        @Specialization(assumptions = "getSingleContextAssumption()", replaces = "doCachedSingleThread")
        long doSingleThread(LLVMContext context, LLVMGlobal global, @Cached("getFrame(context)") MaterializedFrame frame) {
            return LLVMNativePointer.cast(frame.getValue(global.getSlot())).asNative();
        }

        @Specialization(replaces = {"doCachedSingleThread", "doSingleThread"})
        long generic(LLVMContext context, LLVMGlobal global) {
            return LLVMNativePointer.cast(getFrame(context).getValue(global.getSlot())).asNative();
        }
    }

    @SuppressWarnings("unused")
    public abstract static class GetGlobalValueNode extends LLVMNode {
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
    public abstract static class GetFrame extends LLVMNode {
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
    public abstract static class GetSlot extends LLVMNode {
        public abstract FrameSlot execute(LLVMGlobal global);

        public static GetSlot create() {
            return GetSlotNodeGen.create();
        }

        FrameSlot getSlot(LLVMGlobal global) {
            return global.getSlot();
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

    public static LLVMNativePointer toNative(LLVMContext context, LLVMMemory memory, LLVMGlobal global) {
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
            Object value = getContext().getGlobalFrame().getValue(global.getSlot());
            return getNativeLibrary().isPointer(value);
        }

        @Override
        public boolean guard(Object obj) {
            return obj instanceof LLVMGlobal;
        }

        @Override
        public long asPointer(Object obj) throws InteropException {
            LLVMGlobal global = (LLVMGlobal) obj;
            Object value = getContext().getGlobalFrame().getValue(global.getSlot());
            return getNativeLibrary().asPointer(value);
        }
    }

    LLVMNativePointer getAsNative(LLVMMemory memory, LLVMContext context) {
        Object value = context.getGlobalFrame().getValue(slot);
        if (LLVMNativePointer.isInstance(value)) {
            return LLVMNativePointer.cast(value);
        } else if (value instanceof Managed) {
            return transformToNative(memory, context, ((Managed) value).wrapped);
        } else if (value instanceof TruffleObject) {
            return transformToNative(context, (TruffleObject) value);
        } else if (value == null || getPointeeType() instanceof PrimitiveType) {
            return transformToNative(memory, context, value);
        }

        CompilerDirectives.transferToInterpreter();
        throw new IllegalStateException("unknown state of global variable");
    }

    @TruffleBoundary
    private LLVMNativePointer transformToNative(LLVMContext context, TruffleObject value) {
        try {
            Object nativized = ForeignAccess.sendToNative(Message.TO_NATIVE.createNode(), value);
            if (value != nativized) {
                context.getGlobalFrame().setObject(slot, nativized);
            }
            long toAddr = ForeignAccess.sendAsPointer(Message.AS_POINTER.createNode(), (TruffleObject) nativized);
            return LLVMNativePointer.create(toAddr);
        } catch (UnsupportedMessageException e) {
            throw new IllegalStateException("Cannot resolve address of a foreign TruffleObject: " + value);
        }
    }

    @TruffleBoundary
    private LLVMNativePointer transformToNative(LLVMMemory memory, LLVMContext context, Object value) {
        Type pointeeType = getPointeeType();
        int byteSize = context.getByteSize(pointeeType);
        long a = context.getGlobalsStack().allocateStackMemory(byteSize);
        LLVMNativePointer n = bindToNativeAddress(context, a);
        if (value == null) {
            return n;
        }

        if (pointeeType instanceof PrimitiveType) {
            switch (((PrimitiveType) pointeeType).getPrimitiveKind()) {
                case I1:
                    memory.putI1(a, (boolean) value);
                    break;
                case I8:
                    memory.putI8(a, (byte) value);
                    break;
                case I16:
                    memory.putI16(a, (short) (int) value);
                    break;
                case I32:
                case FLOAT:
                    if (value instanceof Float) {
                        memory.putFloat(a, (float) value);
                    } else if (value instanceof Integer) {
                        memory.putI32(a, (int) value);
                    } else {
                        putOther(memory, context, a, value);
                    }
                    break;
                case I64:
                case DOUBLE:
                    if (value instanceof Double) {
                        memory.putDouble(a, (double) value);
                    } else if (value instanceof Long) {
                        memory.putI64(a, (long) value);
                    } else {
                        putOther(memory, context, a, value);
                    }
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
            memory.putPointer(address, pointer);
        } else if (LLVMNativePointer.isInstance(managedValue)) {
            memory.putPointer(address, LLVMNativePointer.cast(managedValue));
        } else if (managedValue instanceof LLVMGlobal) {
            memory.putPointer(address, ((LLVMGlobal) managedValue).getAsNative(memory, context));
        } else if (LLVMManagedPointer.isInstance(managedValue)) {
            LLVMManagedPointer pointer = LLVMManagedPointer.cast(managedValue);
            try {
                Object nativized = ForeignAccess.sendToNative(Message.TO_NATIVE.createNode(), pointer.getObject());
                long toAddr = ForeignAccess.sendAsPointer(Message.AS_POINTER.createNode(), (TruffleObject) nativized);
                memory.putPointer(address, toAddr + pointer.getOffset());
            } catch (UnsupportedMessageException e) {
                throw new IllegalStateException("Cannot resolve address of a foreign TruffleObject: " + managedValue);
            }
        } else if (managedValue instanceof LLVMVirtualAllocationAddress) {
            throw new IllegalStateException("Cannot resolve address of a managed allocation.");
        } else {
            throw new AssertionError("Unknown type: " + managedValue.getClass());
        }
    }

    public Type getPointeeType() {
        return type.getPointeeType();
    }

    @Override
    public boolean isDefined() {
        return library != null;
    }

    public void define(ExternalLibrary newLibrary) {
        define(type, newLibrary);
    }

    // TODO (chaeubl): overwriting the type is a workaround to avoid type mismatches that occur for
    // C++ code
    public void define(PointerType newType, ExternalLibrary newLibrary) {
        assert newType != null && newLibrary != null;
        if (!isDefined()) {
            this.type = newType;
            this.library = newLibrary;
        } else {
            throw new AssertionError("Found multiple definitions of global " + getName() + ".");
        }
    }

    public boolean isReadOnly() {
        return readOnly;
    }

    public static boolean isObjectStore(Type globalType, Object value) {
        return !(globalType instanceof PrimitiveType || LLVMNativePointer.isInstance(value) || value instanceof Managed);
    }

    @Override
    public boolean isFunction() {
        return false;
    }

    @Override
    public boolean isGlobalVariable() {
        return true;
    }

    @Override
    public LLVMFunctionDescriptor asFunction() {
        throw new IllegalStateException("Global " + name + " is not a function.");
    }

    @Override
    public LLVMGlobal asGlobalVariable() {
        return this;
    }
}
