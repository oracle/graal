/*
 * Copyright (c) 2022, Oracle and/or its affiliates.
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
import com.oracle.truffle.llvm.runtime.library.internal.LLVMManagedWriteLibrary;
import com.oracle.truffle.llvm.runtime.memory.LLVMMemMoveNode;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.va.LLVMVaListLibrary;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.va.LLVMVaListStorage;
import com.oracle.truffle.llvm.runtime.nodes.memory.NativeProfiledMemMove;
import com.oracle.truffle.llvm.runtime.nodes.memory.load.LLVMDoubleLoadNode.LLVMDoubleOffsetLoadNode;
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
import com.oracle.truffle.llvm.runtime.pointer.LLVMPointer;
import com.oracle.truffle.llvm.runtime.types.PointerType;
import com.oracle.truffle.llvm.runtime.types.Type;
import com.oracle.truffle.llvm.spi.NativeTypeLibrary;

@ExportLibrary(value = LLVMManagedReadLibrary.class, useForAOT = true, useForAOTPriority = 5)
@ExportLibrary(value = LLVMManagedWriteLibrary.class, useForAOT = true, useForAOTPriority = 4)
@ExportLibrary(value = LLVMVaListLibrary.class, useForAOT = true, useForAOTPriority = 3)
@ExportLibrary(value = NativeTypeLibrary.class, useForAOT = true, useForAOTPriority = 2)
@ExportLibrary(InteropLibrary.class)
public final class LLVMDarwinAarch64VaListStorage extends LLVMVaListStorage {

    // va_list is an alias for char*
    public static final PointerType VA_LIST_TYPE = PointerType.I8;

    protected DarwinAArch64ArgsArea argsArea;

    public LLVMDarwinAarch64VaListStorage() {
        super(null);
    }

    public static final class DarwinAArch64ArgsArea extends ArgsArea {
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
    public static class Initialize {
        public static boolean isManagedPointer(LLVMDarwinAarch64VaListStorage vaList) {
            return LLVMManagedPointer.isInstance(vaList.vaListStackPtr);
        }

        public static Object getManagedStorage(LLVMDarwinAarch64VaListStorage vaList) {
            if (!isManagedPointer(vaList)) {
                // TODO: why is that not caught by the specialization guard?
                return null;
            }
            return ((LLVMManagedPointer) vaList.vaListStackPtr).getObject();
        }

        @Specialization(guards = "!isManagedPointer(self)")
        public static void initializeManaged(LLVMDarwinAarch64VaListStorage self, Object[] realArguments, int numberOfExplicitArguments, Frame frame,
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
                    stackSize += 8;
                }
            }
            self.vaListStackPtr = stackAllocationNode.executeWithTarget(stackSize * Long.BYTES, frame);
            self.nativized = false;
        }

        // TODO: is there a helper for this?
        private static long alignUp(long address) {
            long mask = (8 - 1); // 64bit
            return ((address + mask) & ~mask);
        }

        @Specialization(limit = "3", guards = "isManagedPointer(self)")
        @GenerateAOT.Exclude
        static void initializeGlobal(LLVMDarwinAarch64VaListStorage self, Object[] realArguments, int numberOfExplicitArguments, Frame frame,
                        @Bind("getManagedStorage(self)") Object managedStorage,
                        @CachedLibrary("managedStorage") LLVMManagedWriteLibrary writeLibrary,
                        @Cached.Exclusive @Cached(parameters = "KEEP_32BIT_PRIMITIVES_IN_STRUCTS") ArgumentListExpander argsExpander,
                        @Cached.Exclusive @Cached StackAllocationNode stackAllocationNode) {
            // hack for global var storage
            initializeManaged(self, realArguments, numberOfExplicitArguments, frame, argsExpander, stackAllocationNode);
            writeLibrary.writeGenericI64(managedStorage, 0, self);
        }
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
        // This method should never be invoked
        return LLVMLanguage.get(null).getInteropType(LLVMSourceTypeFactory.resolveType(VA_LIST_TYPE, findDataLayoutFromCurrentFrame()));
    }

    // LLVMManagedReadLibrary implementation

    @ExportMessage(name = "isReadable")
    @ExportMessage(name = "isWritable")
    @SuppressWarnings("static-method")
    boolean isAccessible() {
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
        static byte readI8Managed(LLVMDarwinAarch64VaListStorage vaList, long offset, @CachedLibrary("vaList.argsArea") LLVMManagedReadLibrary readLibrary) {
            return readLibrary.readI8(vaList.argsArea, offset);
        }
    }

    @SuppressWarnings("static-method")
    @ExportMessage
    short readI16(@SuppressWarnings("unused") long offset) {
        throw CompilerDirectives.shouldNotReachHere();
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
        static int readI32Managed(LLVMDarwinAarch64VaListStorage vaList, long offset, @CachedLibrary("vaList.argsArea") LLVMManagedReadLibrary readLibrary) {
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
        static LLVMPointer readPointerManaged(LLVMDarwinAarch64VaListStorage vaList, long offset, @CachedLibrary("vaList.argsArea") LLVMManagedReadLibrary readLibrary) {
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
        static Object readI64Managed(LLVMDarwinAarch64VaListStorage vaList, long offset, @CachedLibrary("vaList.argsArea") LLVMManagedReadLibrary readLibrary) {
            return readLibrary.readGenericI64(vaList.argsArea, offset);
        }
    }

    // LLVMManagedWriteLibrary implementation
    @ExportMessage
    @SuppressWarnings("static-method")
    void writeI8(@SuppressWarnings("unused") long offset, @SuppressWarnings("unused") byte value) {
        throw CompilerDirectives.shouldNotReachHere();
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    void writeI16(@SuppressWarnings("unused") long offset, @SuppressWarnings("unused") short value) {
        throw CompilerDirectives.shouldNotReachHere();
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    void writeI32(@SuppressWarnings("unused") long offset, @SuppressWarnings("unused") int value) {
        throw CompilerDirectives.shouldNotReachHere();
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    void writeGenericI64(@SuppressWarnings("unused") long offset, @SuppressWarnings("unused") Object value) {
        throw CompilerDirectives.shouldNotReachHere();
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    void writePointer(@SuppressWarnings("unused") long offset, @SuppressWarnings("unused") LLVMPointer value) {
        throw CompilerDirectives.shouldNotReachHere();
    }

    @SuppressWarnings("static-method")
    @ExportMessage
    @TruffleBoundary
    void toNative(@Cached LLVMI64OffsetStoreNode i64RegSaveAreaStore,
                    @Cached LLVMI32OffsetStoreNode i32RegSaveAreaStore,
                    @Cached LLVM80BitFloatOffsetStoreNode fp80bitRegSaveAreaStore,
                    @Cached LLVMPointerOffsetStoreNode pointerRegSaveAreaStore,
                    @Cached NativeProfiledMemMove memMove,
                    @Cached BranchProfile nativizedProfile) {

        if (isNativized()) {
            nativizedProfile.enter();
            return;
        }

        nativized = true;

        initNativeAreas(realArguments, numberOfExplicitArguments, vaListStackPtr, i64RegSaveAreaStore,
                        i32RegSaveAreaStore, fp80bitRegSaveAreaStore, pointerRegSaveAreaStore, memMove);
    }

    /**
     * Reconstruct the native areas according to Aarch64 ABI.
     */
    private static void initNativeAreas(Object[] realArguments, int numberOfExplicitArguments,
                    LLVMPointer nativeStackPointer,
                    LLVMI64OffsetStoreNode i64RegSaveAreaStore,
                    LLVMI32OffsetStoreNode i32RegSaveAreaStore,
                    LLVM80BitFloatOffsetStoreNode fp80bitRegSaveAreaStore,
                    LLVMPointerOffsetStoreNode pointerRegSaveAreaStore,
                    LLVMMemMoveNode memMove) {
        final int vaLength = realArguments.length - numberOfExplicitArguments;
        assert vaLength > 0;

        long offset = 0;
        for (int i = numberOfExplicitArguments; i < realArguments.length; i++) {
            final Object object = realArguments[i];

            long size = storeArgument(nativeStackPointer, offset, memMove, i64RegSaveAreaStore, i32RegSaveAreaStore, fp80bitRegSaveAreaStore, pointerRegSaveAreaStore, object, Integer.BYTES);
            assert size <= Long.BYTES;
            offset += Long.BYTES;
        }
    }

    @ExportMessage
    public void cleanup(@SuppressWarnings("unused") Frame frame) {
        // nop
    }

    @ExportMessage
    static class Copy {
        @Specialization(guards = {"!source.isNativized()", "dest.realArguments == null"})
        static void copyToManagedFromNative(LLVMDarwinAarch64VaListStorage source, LLVMDarwinAarch64VaListStorage dest, @SuppressWarnings("unused") Frame frame,
                        @Cached LLVMPointerOffsetStoreNode pointerOffsetStoreNode) {
            /* @formatter:off
             *
             * Write back stack pointer into global/native memory. Example
             * > va_list src;
             * > va_start(src, count);
             * > va_list *dest = malloc(sizeof(va_list));
             * > va_copy(*dest, src);
             *
             * The stack pointer representing the va_list should be stored into `dest` in this case.
             *
             * @formatter:on
             */
            // `dest` here is just a dummy object wrapping the native pointer.
            pointerOffsetStoreNode.executeWithTarget(dest.vaListStackPtr, 0, source);
        }

        @Specialization
        static void copyManaged(LLVMDarwinAarch64VaListStorage source, LLVMDarwinAarch64VaListStorage dest, @SuppressWarnings("unused") Frame frame) {
            dest.realArguments = source.realArguments;
            dest.numberOfExplicitArguments = source.numberOfExplicitArguments;
            dest.vaListStackPtr = source.vaListStackPtr;
            dest.argsArea = source.argsArea;
            dest.nativized = source.nativized;
        }

        @Specialization(limit = "1")
        @GenerateAOT.Exclude // Truffle DSL bug?
        static void copyManagedToMaybeVaPointer(LLVMDarwinAarch64VaListStorage source, LLVMMaybeVaPointer dest, @SuppressWarnings("unused") Frame frame,
                        @CachedLibrary("dest") LLVMManagedWriteLibrary writeLibrary) {
            writeLibrary.writeGenericI64(dest, 0, source);
        }
    }

    @SuppressWarnings("static-method")
    @ExportMessage
    Object shift(@SuppressWarnings("unused") Type type, @SuppressWarnings("unused") Frame frame) {
        throw CompilerDirectives.shouldNotReachHere("should never be called directly");
    }

    @GenerateUncached
    public abstract static class Aarch64VAListPointerWrapperFactory extends VAListPointerWrapperFactory {

        public abstract Object execute(Object pointer);

        protected static LLVMDarwinAarch64VaListStorage createWrapper(LLVMPointer p) {
            LLVMDarwinAarch64VaListStorage storage = new LLVMDarwinAarch64VaListStorage();
            storage.nativized = true;
            storage.vaListStackPtr = p;
            return storage;
        }

        @Specialization(guards = "!isManagedPointer(p)")
        protected Object createNativeWrapper(LLVMPointer p) {
            return createWrapper(p);
        }

        @Specialization(limit = "3", guards = {"isManagedPointer(p)", "isGlobal(p)"})
        @GenerateAOT.Exclude
        protected Object extractFromGlobal(LLVMManagedPointer p,
                        @Bind("getGlobal(p)") Object global,
                        @CachedLibrary("global") LLVMManagedReadLibrary readLibrary) {
            Object ret = readLibrary.readGenericI64(global, 0);
            if (ret instanceof LLVMDarwinAarch64VaListStorage) {
                return ret;
            }
            return createWrapper(p);
        }

        @Specialization(guards = "isManagedPointer(p)")
        protected Object extractFromManaged(LLVMManagedPointer p) {
            Object ret = p.getObject();
            if (ret instanceof LLVMMaybeVaPointer) {
                ((LLVMMaybeVaPointer) ret).wasVAListPointer = true;
            } else {
                CompilerDirectives.shouldNotReachHere("unhandled case: " + ret);
            }
            return ret;
        }

        static boolean isManagedPointer(Object o) {
            return LLVMManagedPointer.isInstance(o);
        }

        static boolean isGlobal(Object o) {
            return ((LLVMManagedPointer) o).getObject() instanceof LLVMGlobalContainer;
        }

        static Object getGlobal(Object o) {
            return ((LLVMManagedPointer) o).getObject();
        }
    }
}
