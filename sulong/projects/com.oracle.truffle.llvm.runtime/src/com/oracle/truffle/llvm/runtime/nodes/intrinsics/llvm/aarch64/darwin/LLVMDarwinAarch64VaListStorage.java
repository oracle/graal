/*
 * Copyright (c) 2022, 2024, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.aarch64.darwin;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateAOT;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.llvm.runtime.LLVMLanguage;
import com.oracle.truffle.llvm.runtime.LLVMVarArgCompoundValue;
import com.oracle.truffle.llvm.runtime.debug.value.LLVMSourceTypeFactory;
import com.oracle.truffle.llvm.runtime.global.LLVMGlobalContainer;
import com.oracle.truffle.llvm.runtime.library.internal.LLVMManagedReadLibrary;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.va.LLVMVaListLibrary;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.va.LLVMVaListStorage;
import com.oracle.truffle.llvm.runtime.nodes.memory.NativeProfiledMemMoveToNative;
import com.oracle.truffle.llvm.runtime.nodes.memory.load.LLVMDoubleLoadNode.LLVMDoubleOffsetLoadNode;
import com.oracle.truffle.llvm.runtime.nodes.memory.load.LLVMI16LoadNode.LLVMI16OffsetLoadNode;
import com.oracle.truffle.llvm.runtime.nodes.memory.load.LLVMI32LoadNode.LLVMI32OffsetLoadNode;
import com.oracle.truffle.llvm.runtime.nodes.memory.load.LLVMI64LoadNode.LLVMI64OffsetLoadNode;
import com.oracle.truffle.llvm.runtime.nodes.memory.load.LLVMI8LoadNode.LLVMI8OffsetLoadNode;
import com.oracle.truffle.llvm.runtime.nodes.memory.load.LLVMPointerLoadNode.LLVMPointerOffsetLoadNode;
import com.oracle.truffle.llvm.runtime.nodes.memory.store.LLVM80BitFloatStoreNode.LLVM80BitFloatOffsetStoreNode;
import com.oracle.truffle.llvm.runtime.nodes.memory.store.LLVMI32StoreNode.LLVMI32OffsetStoreNode;
import com.oracle.truffle.llvm.runtime.nodes.memory.store.LLVMI64StoreNode.LLVMI64OffsetStoreNode;
import com.oracle.truffle.llvm.runtime.nodes.memory.store.LLVMPointerStoreNode.LLVMPointerOffsetStoreNode;
import com.oracle.truffle.llvm.runtime.pointer.LLVMManagedPointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMMaybeVaPointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMNativePointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMPointer;
import com.oracle.truffle.llvm.runtime.types.PointerType;
import com.oracle.truffle.llvm.runtime.types.PrimitiveType;
import com.oracle.truffle.llvm.runtime.types.Type;
import com.oracle.truffle.llvm.spi.NativeTypeLibrary;

@ExportLibrary(value = LLVMManagedReadLibrary.class, useForAOT = true, useForAOTPriority = 3)
@ExportLibrary(value = LLVMVaListLibrary.class, useForAOT = true, useForAOTPriority = 2)
@ExportLibrary(value = NativeTypeLibrary.class, useForAOT = true, useForAOTPriority = 1)
@ExportLibrary(InteropLibrary.class)
public final class LLVMDarwinAarch64VaListStorage extends LLVMVaListStorage {
    // va_list is an alias for char*
    public static final PointerType VA_LIST_TYPE = PointerType.I8;

    DarwinAArch64ArgsArea argsArea;
    private int consumedBytes;

    public LLVMDarwinAarch64VaListStorage() {
        super(null);
    }

    static final class DarwinAArch64ArgsArea extends ArgsArea {
        private final int numOfExpArgs;

        DarwinAArch64ArgsArea(Object[] args, int numOfExpArgs) {
            super(args);
            this.numOfExpArgs = numOfExpArgs;
        }

        @Override
        protected long offsetToIndex(long offset) {
            if (offset < 0) {
                return -1;
            }

            long argIndex = offset / Long.BYTES + numOfExpArgs;
            long argOffset = offset % Long.BYTES;
            return argIndex + (argOffset << Integer.SIZE);
        }
    }

    @ExportMessage
    static void initialize(LLVMDarwinAarch64VaListStorage self, Object[] realArguments, int numberOfExplicitArguments, Frame frame,
                    @Cached.Exclusive @Cached(parameters = "KEEP_32BIT_PRIMITIVES_IN_STRUCTS") ArgumentListExpander argsExpander,
                    @Cached.Exclusive @Cached StackAllocationNode stackAllocationNode) {
        Object[][][] expansionsOutArg = new Object[1][][];
        self.realArguments = argsExpander.expand(realArguments, expansionsOutArg);
        self.numberOfExplicitArguments = numberOfExplicitArguments;
        self.argsArea = new DarwinAArch64ArgsArea(self.realArguments, numberOfExplicitArguments);

        long stackSize = 0;
        for (int i = numberOfExplicitArguments; i < self.realArguments.length; i++) {
            Object o = self.realArguments[i];
            if (o instanceof LLVMVarArgCompoundValue) {
                stackSize += alignUp(((LLVMVarArgCompoundValue) o).getSize());
            } else {
                stackSize += Long.BYTES;
            }
        }
        self.vaListStackPtr = stackAllocationNode.executeWithTarget(stackSize, frame);
        self.nativized = false;
    }

    // TODO: is there a helper for this?
    private static long alignUp(long address) {
        long mask = (8 - 1); // 64bit
        return ((address + mask) & ~mask);
    }

    // NativeTypeLibrary library
    @SuppressWarnings("static-method")
    @ExportMessage
    boolean hasNativeType() {
        return true;
    }

    @SuppressWarnings("static-method")
    @ExportMessage
    @TruffleBoundary
    Object getNativeType() {
        return LLVMLanguage.get(null).getInteropType(LLVMSourceTypeFactory.resolveType(VA_LIST_TYPE, findDataLayoutFromCurrentFrame()));
    }

    // LLVMManagedReadLibrary implementation
    @SuppressWarnings("static-method")
    @ExportMessage
    boolean isReadable() {
        return true;
    }

    @ExportMessage
    static class ReadI8 {
        @Specialization(guards = "vaList.isNativized()")
        @GenerateAOT.Exclude // recursion cut
        static byte readNativeI8(LLVMDarwinAarch64VaListStorage vaList, long offset,
                        @Cached LLVMI8OffsetLoadNode offsetLoadNode) {
            return offsetLoadNode.executeWithTarget(vaList.vaListStackPtr, offset);
        }

        @Specialization(guards = "!vaList.isNativized()", limit = "1")
        static byte readI8Managed(LLVMDarwinAarch64VaListStorage vaList, long offset,
                        @CachedLibrary("vaList.argsArea") LLVMManagedReadLibrary readLibrary) {
            return readLibrary.readI8(vaList.argsArea, offset);
        }
    }

    @ExportMessage
    static class ReadI16 {
        @Specialization(guards = "vaList.isNativized()")
        @GenerateAOT.Exclude // recursion cut
        static short readNativeI16(LLVMDarwinAarch64VaListStorage vaList, long offset,
                        @Cached LLVMI16OffsetLoadNode offsetLoadNode) {
            return offsetLoadNode.executeWithTarget(vaList.vaListStackPtr, offset);
        }

        @Specialization(guards = "!vaList.isNativized()", limit = "1")
        static short readI16Managed(LLVMDarwinAarch64VaListStorage vaList, long offset,
                        @CachedLibrary("vaList.argsArea") LLVMManagedReadLibrary readLibrary) {
            return readLibrary.readI16(vaList.argsArea, offset);
        }
    }

    @ExportMessage
    static class ReadI32 {
        @Specialization(guards = "vaList.isNativized()")
        @GenerateAOT.Exclude // recursion cut
        static int readNativeI32(LLVMDarwinAarch64VaListStorage vaList, long offset,
                        @Cached LLVMI32OffsetLoadNode offsetLoad) {
            return offsetLoad.executeWithTarget(vaList.vaListStackPtr, offset);
        }

        @Specialization(guards = "!vaList.isNativized()", limit = "1")
        static int readI32Managed(LLVMDarwinAarch64VaListStorage vaList, long offset,
                        @CachedLibrary("vaList.argsArea") LLVMManagedReadLibrary readLibrary) {
            return readLibrary.readI32(vaList.argsArea, offset);
        }
    }

    @ExportMessage
    static class ReadPointer {
        @Specialization(guards = "vaList.isNativized()")
        @GenerateAOT.Exclude // recursion cut
        static LLVMPointer readNativePointer(LLVMDarwinAarch64VaListStorage vaList, long offset,
                        @Cached LLVMPointerOffsetLoadNode offsetLoad) {
            return offsetLoad.executeWithTarget(vaList.vaListStackPtr, offset);
        }

        @Specialization(guards = "!vaList.isNativized()", limit = "1")
        static LLVMPointer readPointerManaged(LLVMDarwinAarch64VaListStorage vaList, long offset,
                        @CachedLibrary("vaList.argsArea") LLVMManagedReadLibrary readLibrary) {
            return readLibrary.readPointer(vaList.argsArea, offset);
        }
    }

    @ExportMessage
    static class ReadDouble {
        @Specialization(guards = "vaList.isNativized()")
        @GenerateAOT.Exclude // recursion cut
        static double readNativeDouble(LLVMDarwinAarch64VaListStorage vaList, long offset,
                        @Cached LLVMDoubleOffsetLoadNode doubleOffsetLoadNode) {
            return doubleOffsetLoadNode.executeWithTarget(vaList.vaListStackPtr, offset);
        }

        @Specialization(guards = "!vaList.isNativized()", limit = "1")
        static double readDoubleManaged(LLVMDarwinAarch64VaListStorage vaList, long offset,
                        @CachedLibrary("vaList.argsArea") LLVMManagedReadLibrary readLibrary) {
            return readLibrary.readDouble(vaList.argsArea, offset);
        }
    }

    @ExportMessage
    static class ReadGenericI64 {
        @Specialization(guards = "vaList.isNativized()")
        @GenerateAOT.Exclude // recursion cut
        static Object readNativeGenericI64(LLVMDarwinAarch64VaListStorage vaList, long offset,
                        @Cached LLVMI64OffsetLoadNode llvmi64OffsetLoadNode) {
            return llvmi64OffsetLoadNode.executeWithTargetGeneric(vaList.vaListStackPtr, offset);
        }

        @Specialization(guards = "!vaList.isNativized()", limit = "1")
        static Object readI64Managed(LLVMDarwinAarch64VaListStorage vaList, long offset,
                        @CachedLibrary("vaList.argsArea") LLVMManagedReadLibrary readLibrary) {
            return readLibrary.readGenericI64(vaList.argsArea, offset);
        }
    }

    @SuppressWarnings("static-method")
    @ExportMessage
    @TruffleBoundary
    void toNative(@Cached LLVMI64OffsetStoreNode i64RegSaveAreaStore,
                    @Cached LLVMI32OffsetStoreNode i32RegSaveAreaStore,
                    @Cached LLVM80BitFloatOffsetStoreNode fp80bitRegSaveAreaStore,
                    @Cached LLVMPointerOffsetStoreNode pointerRegSaveAreaStore,
                    @Cached NativeProfiledMemMoveToNative memMove,
                    @Cached BranchProfile nativizedProfile) {

        if (isNativized()) {
            nativizedProfile.enter();
            return;
        }

        if (!LLVMNativePointer.isInstance(vaListStackPtr)) {
            /*
             * We don't have native memory pre-reserved on the stack. Maybe we have no access to
             * native memory? In this case, the toNative transition fails.
             */
            return;
        }
        LLVMNativePointer nativeStackPtr = LLVMNativePointer.cast(vaListStackPtr);

        nativized = true;

        /* Reconstruct the native memory according to darwin-aarch64 ABI. */
        final int vaLength = realArguments.length - numberOfExplicitArguments;
        assert vaLength >= 0 : vaLength;

        long offset = 0;
        for (int i = numberOfExplicitArguments; i < realArguments.length; i++) {
            final Object object = realArguments[i];

            long size = storeArgument(nativeStackPtr, offset, memMove, i64RegSaveAreaStore, i32RegSaveAreaStore, fp80bitRegSaveAreaStore, pointerRegSaveAreaStore, object, Integer.BYTES);
            assert size <= Long.BYTES;
            offset += Long.BYTES;
        }
    }

    @SuppressWarnings("static-method")
    @ExportMessage
    void cleanup(@SuppressWarnings("unused") Frame frame) {
        throw CompilerDirectives.shouldNotReachHere("should only be called on LLVMMaybeVaPointer");
    }

    @SuppressWarnings("static-method")
    @ExportMessage
    void copy(@SuppressWarnings("unused") Object dest, @SuppressWarnings("unused") Frame frame) {
        throw CompilerDirectives.shouldNotReachHere("should never be called directly");
    }

    /**
     * This is the implementation of the {@code va_arg} instruction.
     */
    @SuppressWarnings("static-method")
    @ExportMessage
    Object shift(Type type, @SuppressWarnings("unused") Frame frame,
                    @CachedLibrary(limit = "1") LLVMManagedReadLibrary readLib) {

        try {
            if (type instanceof PrimitiveType) {
                switch (((PrimitiveType) type).getPrimitiveKind()) {
                    case DOUBLE:
                        return readLib.readDouble(this, consumedBytes);
                    case FLOAT:
                        return readLib.readFloat(this, consumedBytes);
                    case I1:
                        return readLib.readI8(this, consumedBytes) != 0;
                    case I16:
                        return readLib.readI16(this, consumedBytes);
                    case I32:
                        return readLib.readI32(this, consumedBytes);
                    case I64:
                        return readLib.readGenericI64(this, consumedBytes);
                    case I8:
                        return readLib.readI8(this, consumedBytes);
                    default:
                        throw CompilerDirectives.shouldNotReachHere("not implemented");
                }
            } else if (type instanceof PointerType) {
                return readLib.readPointer(this, consumedBytes);
            } else {
                throw CompilerDirectives.shouldNotReachHere("not implemented");
            }
        } finally {
            consumedBytes += Long.BYTES;
        }
    }

    @GenerateUncached
    public abstract static class Aarch64VAListPointerWrapperFactory extends VAListPointerWrapperFactory {

        public abstract Object execute(Object pointer);

        @Specialization(guards = "!isManagedPointer(p)")
        Object createNativeWrapper(LLVMPointer p) {
            return LLVMMaybeVaPointer.createWithHeap(p);
        }

        @Specialization(limit = "3", guards = {"isManagedPointer(p)", "isGlobal(p)"})
        @GenerateAOT.Exclude
        Object extractFromGlobal(LLVMManagedPointer p,
                        @Bind("getGlobal(p)") Object global,
                        @CachedLibrary("global") LLVMManagedReadLibrary readLibrary) {
            /* probe content of global storage */
            Object ret = readLibrary.readGenericI64(global, 0);
            if (ret instanceof LLVMDarwinAarch64VaListStorage) {
                return LLVMMaybeVaPointer.createWithStorage(p, ret);
            } else if (ret instanceof LLVMMaybeVaPointer) {
                return ret;
            }

            assert LLVMManagedPointer.isInstance(p);
            /* no VAList in it, use LLVMMaybeVaPointer as a wrapper */
            return LLVMMaybeVaPointer.createWithHeap(p);
        }

        @Specialization(guards = {"isManagedPointer(p)", "!isGlobal(p)", "isLLVMMaybeVaPointer(p)"})
        Object extractFromManaged(LLVMManagedPointer p) {
            return p.getObject();
        }

        @Specialization(guards = {"isManagedPointer(p)", "!isGlobal(p)", "!isLLVMMaybeVaPointer(p)"})
        Object createNativeWrapperForeign(LLVMManagedPointer p) {
            return LLVMMaybeVaPointer.createWithHeap(p);
        }

        static boolean isManagedPointer(Object o) {
            return LLVMManagedPointer.isInstance(o);
        }

        static boolean isGlobal(Object o) {
            return LLVMManagedPointer.cast(o).getObject() instanceof LLVMGlobalContainer;
        }

        static boolean isLLVMMaybeVaPointer(Object o) {
            return LLVMManagedPointer.cast(o).getObject() instanceof LLVMMaybeVaPointer;
        }

        static Object getGlobal(Object o) {
            return LLVMManagedPointer.cast(o).getObject();
        }
    }
}
