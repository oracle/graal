/*
 * Copyright (c) 2020, 2021, Oracle and/or its affiliates.
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
import com.oracle.truffle.api.dsl.*;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.llvm.runtime.LLVMLanguage;
import com.oracle.truffle.llvm.runtime.LLVMVarArgCompoundValue;
import com.oracle.truffle.llvm.runtime.debug.value.LLVMSourceTypeFactory;
import com.oracle.truffle.llvm.runtime.global.LLVMGlobalContainer;
import com.oracle.truffle.llvm.runtime.library.internal.LLVMCopyTargetLibrary;
import com.oracle.truffle.llvm.runtime.library.internal.LLVMManagedReadLibrary;
import com.oracle.truffle.llvm.runtime.library.internal.LLVMManagedWriteLibrary;
import com.oracle.truffle.llvm.runtime.memory.LLVMMemMoveNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMTypes;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.aarch64.Aarch64BitVarArgs;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.aarch64.darwin.LLVMDarwinAarch64VaListStorageFactory.ArgumentListExpanderFactory.ArgumentExpanderNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.va.LLVMVAEnd;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.va.LLVMVAStart;
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
import com.oracle.truffle.llvm.runtime.pointer.LLVMNativePointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMPointer;
import com.oracle.truffle.llvm.runtime.types.PointerType;
import com.oracle.truffle.llvm.runtime.types.PrimitiveType;
import com.oracle.truffle.llvm.runtime.types.StructureType;
import com.oracle.truffle.llvm.runtime.types.Type;
import com.oracle.truffle.llvm.spi.NativeTypeLibrary;

@ExportLibrary(value = LLVMManagedReadLibrary.class, useForAOT = true, useForAOTPriority = 5)
@ExportLibrary(value = LLVMManagedWriteLibrary.class, useForAOT = true, useForAOTPriority = 4)
@ExportLibrary(value = LLVMVaListLibrary.class, useForAOT = true, useForAOTPriority = 3)
@ExportLibrary(value = NativeTypeLibrary.class, useForAOT = true, useForAOTPriority = 2)
@ExportLibrary(value = LLVMCopyTargetLibrary.class, useForAOT = true, useForAOTPriority = 1)
@ExportLibrary(InteropLibrary.class)
public final class LLVMDarwinAarch64VaListStorage extends LLVMVaListStorage {

    // va_list is an alias for char*
    public static final PointerType VA_LIST_TYPE = PointerType.I8;

    protected Object[] realArguments;
    protected int numberOfExplicitArguments;

    private Object[] originalArgs;
    private Object[][] expansions;

    private int managedOffset = 0;
    private long nativeSeekOffset = 0;
    private long nativeOffset = 0;

    public LLVMDarwinAarch64VaListStorage() {
        super(null);
    }

    public LLVMDarwinAarch64VaListStorage(LLVMPointer vaList) {
        super(vaList);
    }

    // LLVMCopyTargetLibrary

    LLVMPointer effectiveVaListPtr() {
        return vaListStackPtr.increment(nativeOffset);
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    public boolean canCopyFrom(Object source, @SuppressWarnings("unused") long length) {
        /*
         * I do not test if the length is the size of va_list as I need that the execution proceed
         * to copyFrom. I think that an exception should not be thrown here in this idempotent
         * method, but in the actual attempt to make a copy.
         */
        return source instanceof LLVMDarwinAarch64VaListStorage || LLVMNativePointer.isInstance(source);
    }

    @ExportMessage
    public void copyFrom(Object source, @SuppressWarnings("unused") long length,
                    @Cached VAListPointerWrapperFactoryDelegate wrapperFactory,
                    @CachedLibrary(limit = "1") LLVMVaListLibrary vaListLibrary,
                    @Cached BranchProfile invalidLengthProfile) {
        if (length != Aarch64BitVarArgs.SIZE_OF_VALIST) {
            invalidLengthProfile.enter();
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw CompilerDirectives.shouldNotReachHere("Invalid length " + length + ". Expected length " + Aarch64BitVarArgs.SIZE_OF_VALIST);
        }

        vaListLibrary.copyWithoutFrame(wrapperFactory.execute(source), this);
    }

    @ExportMessage
    public void initialize(Object[] realArgs, int numOfExpArgs, Frame frame,
                     @Shared("stackAllocationNode") @Cached StackAllocationNode stackAllocationNode) {
        realArguments = realArgs;
        numberOfExplicitArguments = numOfExpArgs;

        long stackSize = 0;
        for (int i = numOfExpArgs; i < realArgs.length; i++) {
            Object o = realArgs[i];
            if (o instanceof LLVMVarArgCompoundValue) {
                stackSize += alignup(((LLVMVarArgCompoundValue) o).getSize());
            } else {
                // TODO: true for everything? What about Float Vector? LLVM80BitFloat?
                stackSize += 8;
            }
        }
        vaListStackPtr = stackAllocationNode.executeWithTarget(stackSize * Long.BYTES, frame);

        assert numOfExpArgs <= realArguments.length;
    }

    // TODO: move it to the super class
    static final class ArgumentListExpander extends LLVMNode {
        private final BranchProfile expansionBranchProfile;
        private final ConditionProfile noExpansionProfile;
        private @Child ArgumentExpander expander;

        private static final ArgumentListExpander uncached = new ArgumentListExpander(false);

        private ArgumentListExpander(boolean cached) {
            expansionBranchProfile = cached ? BranchProfile.create() : BranchProfile.getUncached();
            noExpansionProfile = cached ? ConditionProfile.createBinaryProfile() : ConditionProfile.getUncached();
            expander = cached ? ArgumentExpanderNodeGen.create() : ArgumentExpanderNodeGen.getUncached();
        }

        public static ArgumentListExpander create() {
            return new ArgumentListExpander(true);
        }

        public static ArgumentListExpander getUncached() {
            return uncached;
        }

        Object[] expand(Object[] args, Object[][][] expansionsOutArg) {
            Object[][] expansions = null;
            int extraSize = 0;
            for (int i = 0; i < args.length; i++) {
                Object[] expansion = expander.execute(args[i]);
                if (expansion != null) {
                    expansionBranchProfile.enter();
                    if (expansions == null) {
                        expansions = new Object[args.length][];
                    }
                    expansions[i] = expansion;
                    extraSize += expansion.length - 1;
                }
            }
            expansionsOutArg[0] = expansions;
            return noExpansionProfile.profile(expansions == null) ? args : expandArgs(args, expansions, extraSize);
        }

        static Object[] expandArgs(Object[] args, Object[][] expansions, int extraSize) {
            Object[] result = new Object[args.length + extraSize];
            int j = 0;
            for (int i = 0; i < args.length; i++) {
                if (expansions[i] == null) {
                    result[j] = args[i];
                    j++;
                } else {
                    for (int k = 0; k < expansions[i].length; k++) {
                        result[j] = expansions[i][k];
                        j++;
                    }
                }
            }
            return result;
        }

        @ImportStatic(LLVMVaListStorage.class)
        @GenerateUncached
        abstract static class ArgumentExpander extends LLVMNode {

            public abstract Object[] execute(Object arg);

            @Specialization(guards = "isFloatArrayWithMaxTwoElems(arg.getType()) || isFloatVectorWithMaxTwoElems(arg.getType())")
            protected Object[] expandFloatArrayOrVectorCompoundArg(LLVMVarArgCompoundValue arg, @Cached IntegerConversionHelperNode convNode) {
                return new Object[]{Float.intBitsToFloat(convNode.executeInteger(arg, 0)), Float.intBitsToFloat(convNode.executeInteger(arg, 4))};
            }

            @Specialization(guards = "isDoubleArrayWithMaxTwoElems(arg.getType()) || isDoubleVectorWithMaxTwoElems(arg.getType())")
            protected Object[] expandDoubleArrayOrVectorCompoundArg(LLVMVarArgCompoundValue arg, @Cached LongConversionHelperNode convNode) {
                return new Object[]{Double.longBitsToDouble(convNode.executeLong(arg, 0)), Double.longBitsToDouble(convNode.executeLong(arg, 8))};
            }

            @Specialization(guards = "isI32ArrayWithMaxTwoElems(arg.getType()) || isI32VectorWithMaxTwoElems(arg.getType())")
            protected Object[] expandI32ArrayOrVectorCompoundArg(LLVMVarArgCompoundValue arg, @Cached IntegerConversionHelperNode convNode) {
                return new Object[]{convNode.executeInteger(arg, 0), convNode.executeInteger(arg, 4)};
            }

            @Specialization(guards = "isI64ArrayWithMaxTwoElems(arg.getType()) || isI64VectorWithMaxTwoElems(arg.getType())")
            protected Object[] expandI64ArrayOrVectorCompoundArg(LLVMVarArgCompoundValue arg, @Cached LongConversionHelperNode convNode) {
                return new Object[]{convNode.executeLong(arg, 0), convNode.executeLong(arg, 8)};
            }

            @Fallback
            protected Object[] noExpansion(@SuppressWarnings("unused") Object arg) {
                return null;
            }
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

        @Specialization(guards = "!vaList.isNativized()", limit = "1")
        static byte readManagedI8(LLVMDarwinAarch64VaListStorage vaList, long offset,
                                  @CachedLibrary("vaList.vaListStackPtr") LLVMManagedReadLibrary readLibrary) {
            CompilerDirectives.shouldNotReachHere("ohhh");
            return readLibrary.readI8(vaList.effectiveVaListPtr(), offset);
        }

        @Specialization(guards = "vaList.isNativized()")
        @GenerateAOT.Exclude // recursion cut
        static byte readNativeI8(LLVMDarwinAarch64VaListStorage vaList, long offset,
                                 @Cached LLVMI8OffsetLoadNode offsetLoadNode) {
            return offsetLoadNode.executeWithTarget(vaList.effectiveVaListPtr(), offset);
        }
    }

    @SuppressWarnings("static-method")
    @ExportMessage
    short readI16(@SuppressWarnings("unused") long offset) {
        throw CompilerDirectives.shouldNotReachHere();
    }

    @ExportMessage
    static class ReadI32 {

        @Specialization(guards = "!vaList.isNativized()", limit = "1")
        static int readManagedI32(LLVMDarwinAarch64VaListStorage vaList, long offset,
                                  @CachedLibrary("vaList.vaListStackPtr") LLVMManagedReadLibrary readLibrary) {
            CompilerDirectives.shouldNotReachHere("ohhh");
            return readLibrary.readI32(vaList.effectiveVaListPtr(), offset);
        }

        @Specialization(guards = "vaList.isNativized()")
        @GenerateAOT.Exclude // recursion cut
        static int readNativeI32(LLVMDarwinAarch64VaListStorage vaList, long offset,
                                 @Cached LLVMI32OffsetLoadNode offsetLoad) {
            return offsetLoad.executeWithTarget(vaList.effectiveVaListPtr(), offset);
        }
    }

    @ExportMessage
    static class ReadPointer {

        @Specialization(guards = "!vaList.isNativized()")
        static LLVMPointer readManagedPointer(LLVMDarwinAarch64VaListStorage vaList, long offset) {
            CompilerDirectives.shouldNotReachHere();
            return null;
        }

        @Specialization(guards = "vaList.isNativized()")
        @GenerateAOT.Exclude // recursion cut
        static LLVMPointer readNativePointer(LLVMDarwinAarch64VaListStorage vaList, long offset,
                                             @Cached LLVMPointerOffsetLoadNode offsetLoad) {
            return offsetLoad.executeWithTarget(vaList.effectiveVaListPtr(), offset);
        }
    }

    @ExportMessage
    static class ReadDouble{

        @Specialization(guards = "!vaList.isNativized()")
        static double readManagedDouble(LLVMDarwinAarch64VaListStorage vaList, long offset) {
            throw CompilerDirectives.shouldNotReachHere();
        }

        @Specialization(guards = "vaList.isNativized()")
        @GenerateAOT.Exclude // recursion cut
        static double readNativeDouble(LLVMDarwinAarch64VaListStorage vaList, long offset,
                                           @Cached LLVMDoubleOffsetLoadNode doubleOffsetLoadNode) {
            return doubleOffsetLoadNode.executeWithTarget(vaList.effectiveVaListPtr(), offset);
        }
    }

    @ExportMessage
    static class ReadGenericI64{

        @Specialization(guards = "!vaList.isNativized()")
        static Object readManagedGenericI64(LLVMDarwinAarch64VaListStorage vaList, long offset) {
            CompilerDirectives.shouldNotReachHere();
            return null;
        }

        @Specialization(guards = "vaList.isNativized()")
        @GenerateAOT.Exclude // recursion cut
        static Object readNativeGenericI64(LLVMDarwinAarch64VaListStorage vaList, long offset,
                                             @Cached LLVMI64OffsetLoadNode llvmi64OffsetLoadNode) {
            return llvmi64OffsetLoadNode.executeWithTargetGeneric(vaList.effectiveVaListPtr(), offset);
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
    static class WriteI32 {

        @Specialization(guards = "!vaList.isNativized()")
        static void writeManaged(LLVMDarwinAarch64VaListStorage vaList, long offset, int value) {
            CompilerDirectives.shouldNotReachHere();
        }

        @Specialization(guards = "vaList.isNativized()")
        @GenerateAOT.Exclude // recursion cut
        static void writeNative(LLVMDarwinAarch64VaListStorage vaList, long offset, int value,
                                @Cached LLVMI32OffsetStoreNode offsetStore) {
            offsetStore.executeWithTarget(vaList.effectiveVaListPtr(), offset, value);
        }
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    void writeGenericI64(@SuppressWarnings("unused") long offset, @SuppressWarnings("unused") Object value) {
        throw CompilerDirectives.shouldNotReachHere();
    }

    @ExportMessage
    static class WritePointer {

        @Specialization(guards = "!vaList.isNativized()")
        static void writeManaged(LLVMDarwinAarch64VaListStorage vaList, long offset, @SuppressWarnings("unused") LLVMPointer value,
                                 @Cached BranchProfile exception,
                                 @CachedLibrary("vaList") LLVMManagedWriteLibrary self) {
            CompilerDirectives.shouldNotReachHere();
        }

        @Specialization(guards = "vaList.isNativized()")
        @GenerateAOT.Exclude // recursion cut
        static void writeNative(LLVMDarwinAarch64VaListStorage vaList, long offset, LLVMPointer value,
                                @Cached LLVMPointerOffsetStoreNode offsetStore) {
            offsetStore.executeWithTarget(vaList.effectiveVaListPtr(), offset, value);
        }
    }

    @SuppressWarnings("static-method")
    @ExportMessage
    @TruffleBoundary
    void toNative(@Cached LLVMI64OffsetStoreNode i64RegSaveAreaStore,
                    @Cached LLVMI32OffsetStoreNode i32RegSaveAreaStore,
                    @Cached LLVM80BitFloatOffsetStoreNode fp80bitRegSaveAreaStore,
                    @Cached LLVMPointerOffsetStoreNode pointerRegSaveAreaStore,
                    @Cached LLVMI64OffsetStoreNode i64OverflowArgAreaStore,
                    @Cached LLVMI32OffsetStoreNode i32OverflowArgAreaStore,
                    @Cached LLVM80BitFloatOffsetStoreNode fp80bitOverflowArgAreaStore,
                    @Cached LLVMPointerOffsetStoreNode pointerOverflowArgAreaStore,
                    @Cached NativeProfiledMemMove memMove,
                    @Cached BranchProfile nativizedProfile) {

        if (isNativized()) {
            nativizedProfile.enter();
            return;
        }

        this.nativized = true;

        initNativeAreas(this.realArguments, this.originalArgs, this.expansions, this.numberOfExplicitArguments, effectiveVaListPtr(),
                        i64RegSaveAreaStore, i32RegSaveAreaStore, fp80bitRegSaveAreaStore, pointerRegSaveAreaStore, i64OverflowArgAreaStore, i32OverflowArgAreaStore,
                        fp80bitOverflowArgAreaStore, pointerOverflowArgAreaStore, memMove);
    }

    /**
     * Reconstruct the native areas according to Aarch64 ABI.
     */
    private static void initNativeAreas(Object[] realArguments, Object[] originalArguments, Object[][] expansions, int numberOfExplicitArguments,
                    LLVMPointer nativeStackPointer,
                    LLVMI64OffsetStoreNode i64RegSaveAreaStore,
                    LLVMI32OffsetStoreNode i32RegSaveAreaStore,
                    LLVM80BitFloatOffsetStoreNode fp80bitRegSaveAreaStore,
                    LLVMPointerOffsetStoreNode pointerRegSaveAreaStore,
                    LLVMI64OffsetStoreNode i64OverflowArgAreaStore,
                    LLVMI32OffsetStoreNode i32OverflowArgAreaStore,
                    LLVM80BitFloatOffsetStoreNode fp80bitOverflowArgAreaStore,
                    LLVMPointerOffsetStoreNode pointerOverflowArgAreaStore,
                    LLVMMemMoveNode memMove) {
        final int vaLength = realArguments.length - numberOfExplicitArguments;
        if (vaLength <= 0) {
            return;
        }

        long offset = 0;
        for (int i = numberOfExplicitArguments; i < realArguments.length; i++) {
            final Object object = realArguments[i];
            // TODO: next_offset = align_up(sizeof(object), 8);

            long size = storeArgument(nativeStackPointer, offset, memMove, i64RegSaveAreaStore, i32RegSaveAreaStore, fp80bitRegSaveAreaStore, pointerRegSaveAreaStore, object, Aarch64BitVarArgs.STACK_STEP);
            offset += alignup(size);
        }
    }
    private static long alignup(long address) {
        long mask = (8 - 1);  // 64bit
        return ((address + mask) & ~mask);
    }

    @ExportMessage
    public void cleanup(@SuppressWarnings("unused") Frame frame) {
        managedOffset = 0;
        nativeOffset = 0;
    }

    @ExportMessage
    static class Copy {

        @Specialization(guards = {"!source.isNativized()"})
        static void copyManaged(LLVMDarwinAarch64VaListStorage source, LLVMDarwinAarch64VaListStorage dest, Frame frame,
                                @Shared("stackAllocationNode") @Cached StackAllocationNode stackAllocationNode) {
            dest.realArguments = source.realArguments;
            dest.originalArgs = source.originalArgs;
            dest.expansions = source.expansions;
            dest.numberOfExplicitArguments = source.numberOfExplicitArguments;
            dest.vaListStackPtr = source.vaListStackPtr;
        }

        @Specialization(guards = {"source.isNativized()"})
        @GenerateAOT.Exclude // recursion cut
        static void copyNative(LLVMDarwinAarch64VaListStorage source, LLVMDarwinAarch64VaListStorage dest, Frame frame,
                               @Cached VAListPointerWrapperFactoryDelegate wrapperFactory,
                               @CachedLibrary(limit = "1") LLVMVaListLibrary vaListLibrary) {
            // The source valist is just a holder of the native counterpart and thus the destination
            // will not be set up as a managed va_list as it would be too complicated to restore the
            // managed state from the native one.
            vaListLibrary.copy(wrapperFactory.execute(source.vaListStackPtr), dest, frame);
        }

        @Specialization(limit = "1")
        @GenerateAOT.Exclude // TODO: Needed?
        static void copyManagedToMaybeVaPointer(LLVMDarwinAarch64VaListStorage source, LLVMMaybeVaPointer dest, Frame frame,
                               @Shared("stackAllocationNode") @Cached StackAllocationNode stackAllocationNode,
                               @CachedLibrary("dest") LLVMManagedWriteLibrary writeLibrary) {
            LLVMDarwinAarch64VaListStorage vaListDest = new LLVMDarwinAarch64VaListStorage();
            copyManaged(source, vaListDest, frame, stackAllocationNode);
            writeLibrary.writePointer(dest, 0, LLVMManagedPointer.create(vaListDest));
        }

        @Specialization
        @GenerateAOT.Exclude // recursion cut
        static void copyManagedToNative(LLVMDarwinAarch64VaListStorage source, NativeVAListWrapper dest, Frame frame,
                                        @Cached.Exclusive @Cached StackAllocationNode stackAllocationNode,
                                        @Cached.Exclusive @Cached LLVMI64OffsetStoreNode i64RegSaveAreaStore,
                                        @Cached.Exclusive @Cached LLVMI32OffsetStoreNode i32RegSaveAreaStore,
                                        @Cached.Exclusive @Cached LLVM80BitFloatOffsetStoreNode fp80bitRegSaveAreaStore,
                                        @Cached.Exclusive @Cached LLVMPointerOffsetStoreNode pointerRegSaveAreaStore,
                                        @Cached.Exclusive @Cached LLVMI64OffsetStoreNode i64OverflowArgAreaStore,
                                        @Cached.Exclusive @Cached LLVMI32OffsetStoreNode i32OverflowArgAreaStore,
                                        @Cached.Exclusive @Cached LLVM80BitFloatOffsetStoreNode fp80bitOverflowArgAreaStore,
                                        @Cached.Exclusive @Cached LLVMPointerOffsetStoreNode pointerOverflowArgAreaStore,
                                        @Cached.Exclusive @Cached LLVMPointerOffsetStoreNode pointerOffsetStoreNode,
                                        @Cached.Exclusive @Cached BranchProfile nativizedProfile,
                                        @Cached NativeProfiledMemMove memMove,
                                        @CachedLibrary(limit = "1") LLVMVaListLibrary vaListLibrary) {
            if (!source.isNativized()) {
                source.toNative(i64RegSaveAreaStore, i32RegSaveAreaStore, fp80bitRegSaveAreaStore, pointerRegSaveAreaStore, i64OverflowArgAreaStore, i32OverflowArgAreaStore, fp80bitOverflowArgAreaStore,
                        pointerOverflowArgAreaStore, memMove, nativizedProfile);

            }
            pointerOffsetStoreNode.executeWithTarget(dest.nativeVAListPtr, 0, LLVMManagedPointer.create(source));
            boolean hmm = false;

        }

        /*
        @Specialization(limit = "1")
        @GenerateAOT.Exclude
        static void copyManagedToNative(LLVMDarwinAarch64VaListStorage source, LLVMMaybeVaPointer dest, Frame frame,
                                        @CachedLibrary LLVMVaListLibrary vaListLibrary,
                                        @CachedLibrary("dest") LLVMManagedWriteLibrary writeLibrary) {
            LLVMDarwinAarch64VaListStorage dummyClone = new LLVMDarwinAarch64VaListStorage();
            dummyClone.nativized = true;
            vaListLibrary.initialize(dummyClone, source.realArguments, source.numberOfExplicitArguments, frame);
            writeLibrary.writePointer(dest, 0, LLVMManagedPointer.create(dummyClone));
        }
         */
    }

    @ExportMessage
    void seek(long seekOffset) {
        if (!isNativized()) {
            CompilerDirectives.shouldNotReachHere("can only be done for nativized lists");
        }
        nativeSeekOffset = seekOffset;
    }

    /**
     * This is the implementation of the {@code va_arg} instruction.
     */
    @SuppressWarnings("static-method")
    @ExportMessage
    Object shift(Type type, @SuppressWarnings("unused") Frame frame,
                 // @CachedLibrary InteropLibrary interop,
                 @Cached LLVMDoubleOffsetLoadNode loadDoubleNode,
                 @Cached LLVMI32OffsetLoadNode loadi32Node,
                 @Cached LLVMI64OffsetLoadNode loadi64Node,
                 @Cached LLVMPointerOffsetLoadNode loadPointerNode) {
        // TODO: specialize regarding nativized
        Object ret = null;
        if (!isNativized()) {
            if (realArguments == null) {
                CompilerDirectives.shouldNotReachHere("should not be nativized");
            }
            // TODO: compute proper increment
            ret = realArguments[numberOfExplicitArguments + managedOffset++];
            // TODO: do all the conversions.
            if (ret instanceof Integer) {
                Integer i = (Integer) ret;
                if (PrimitiveType.I32.equals(type)) {
                    return i;
                } else if (PrimitiveType.DOUBLE.equals(type)) {
                    return Double.longBitsToDouble(Integer.toUnsignedLong(i));
                } else {
                    CompilerDirectives.shouldNotReachHere("Conv not supported: from Integer to " + type);
                }
            } else if (ret instanceof Double) {
                Double d = (Double) ret;
                if (PrimitiveType.DOUBLE.equals(type)) {
                    return d;
                } else if (PrimitiveType.I32.equals(type)) {
                    return (int) Double.doubleToLongBits(d);
                } else {
                    CompilerDirectives.shouldNotReachHere("Conv not supported: from Double to " + type);
                }
            } else if (ret instanceof LLVMPointer) {
                LLVMPointer p = (LLVMPointer) ret;
                if (type instanceof PointerType) {
                    return p;
                } else {
                    CompilerDirectives.shouldNotReachHere("Conv not supported: from LLVMPointer to " + type);
                }
            } else if (ret instanceof LLVMVarArgCompoundValue) {
                CompilerDirectives.shouldNotReachHere("should not reach? aren't they decomposed by LLVM?");
                LLVMVarArgCompoundValue struct = (LLVMVarArgCompoundValue) ret;
                if (type instanceof PointerType && ((PointerType) type).getPointeeType() instanceof StructureType) {
                    // TODO: better shape check?
                    return struct;
                } else {
                    CompilerDirectives.shouldNotReachHere("Conv not supported: from LLVMVarArgCompoundValue to " + type);
                }
        }
            CompilerDirectives.shouldNotReachHere("unsupported type: " + ret.getClass());
        }

        if (!isNativized()) {
            CompilerDirectives.shouldNotReachHere("should be nativized");
        }
        if (PrimitiveType.DOUBLE.equals(type)) {
            ret = loadDoubleNode.executeWithTarget(effectiveVaListPtr(), nativeSeekOffset);
        } else if (type.equals(PrimitiveType.I32)) {
            ret = loadi32Node.executeWithTarget(effectiveVaListPtr(), nativeSeekOffset);
        } else if (type.equals(PrimitiveType.I64)) {
            try {
                ret = loadi64Node.executeWithTarget(effectiveVaListPtr(), nativeSeekOffset);
            } catch (UnexpectedResultException e) {
                e.printStackTrace();
            }
        } else if (type instanceof PointerType) {
            ret = loadPointerNode.executeWithTarget(effectiveVaListPtr(), nativeSeekOffset);
        } else {
            CompilerDirectives.shouldNotReachHere("Implement type: " + type);
        }
        // We'll never see a struct type here? Because va_arg are decomposed.
        nativeOffset += 8;

        return ret;
    }

    @GenerateUncached
    public abstract static class Aarch64VAListPointerWrapperFactory extends VAListPointerWrapperFactory {

        public abstract Object execute(Object pointer);

        @Specialization(guards = "isGlobalVAList(p.getObject())")
        protected Object createGlobalVAList(LLVMManagedPointer p) {
            LLVMManagedPointer fallback = (LLVMManagedPointer) ((LLVMGlobalContainer) p.getObject()).getFallback();
            return fallback.getObject();
        }

        @Specialization
        protected Object createNativeWrapper(LLVMNativePointer p,
                                             @Cached LLVMPointerOffsetLoadNode pointerOffsetLoadNode) {
            // LLVMPointer value = pointerOffsetLoadNode.executeWithTarget(p, 0);
            // return new NativeVAListWrapper(value);
            return new NativeVAListWrapper(p);
        }

        @Specialization(guards = "!isManagedVAList(p.getObject())")
        protected Object createManagedWrapper(LLVMManagedPointer p) {
                return new NativeVAListWrapper(p);
        }

        @Specialization(guards = "isManagedVAList(p.getObject())")
        protected Object createManagedVAListWrapper(LLVMManagedPointer p) {
            return p.getObject();
        }

        static boolean isManagedVAList(Object o) {
            return o instanceof LLVMMaybeVaPointer;
        }

        static boolean isGlobalVAList(Object o) {
            // TODO: should be done with ReadLib?
            if (o instanceof LLVMGlobalContainer) {
                LLVMGlobalContainer globalContainer = (LLVMGlobalContainer) o;
                if (globalContainer.getFallback() instanceof LLVMManagedPointer) {
                    return ((LLVMManagedPointer) globalContainer.getFallback()).getObject() instanceof LLVMDarwinAarch64VaListStorage;
                }
            }
            return false;
        }
    }

    /**
     * A helper implementation of {@link LLVMVaListLibrary} for native <code>va_list</code>
     * instances. It allows for {@link LLVMVAStart} and others to treat native LLVM pointers to
     * <code>va_list</code> just as the managed <code>va_list</code> objects and thus to remain
     * platform independent.
     *
     * @see LLVMVAStart
     * @see LLVMVAEnd
     */
    @ExportLibrary(value = LLVMVaListLibrary.class, useForAOT = true, useForAOTPriority = 0)
    @ImportStatic(LLVMVaListStorage.class)
    public static final class NativeVAListWrapper {

        final LLVMPointer nativeVAListPtr;
        private int offset;

        public NativeVAListWrapper(LLVMPointer nativeVAListPtr) {
            this.nativeVAListPtr = nativeVAListPtr;
        }
        @ExportMessage
        static class Initialize {

            static boolean isManagedPointer(NativeVAListWrapper nativeVAListWrapper) {
                return nativeVAListWrapper.nativeVAListPtr instanceof LLVMManagedPointer;
            }

            // @Specialization(guards = "isManagedPointer(self)")
            @Specialization
            @GenerateAOT.Exclude // TODO: Needed?
            static void initializeManaged(NativeVAListWrapper self, Object[] originalArgs,
                                   int numberOfExplicitArguments, Frame frame,
                                   // @CachedLibrary("((LLVMManagedPointer) self.nativeVAListPtr).getObject()") LLVMManagedWriteLibrary writeLibrary,
                                   @Cached.Exclusive @Cached StackAllocationNode stackAllocationNode,
                                   @Cached.Exclusive @Cached LLVMI64OffsetStoreNode i64RegSaveAreaStore,
                                   @Cached.Exclusive @Cached LLVMI32OffsetStoreNode i32RegSaveAreaStore,
                                   @Cached.Exclusive @Cached LLVM80BitFloatOffsetStoreNode fp80bitRegSaveAreaStore,
                                   @Cached.Exclusive @Cached LLVMPointerOffsetStoreNode pointerRegSaveAreaStore,
                                   @Cached.Exclusive @Cached LLVMI64OffsetStoreNode i64OverflowArgAreaStore,
                                   @Cached.Exclusive @Cached LLVMI32OffsetStoreNode i32OverflowArgAreaStore,
                                   @Cached.Exclusive @Cached LLVM80BitFloatOffsetStoreNode fp80bitOverflowArgAreaStore,
                                   @Cached.Exclusive @Cached LLVMPointerOffsetStoreNode pointerOverflowArgAreaStore,
                                   @Cached.Exclusive @Cached BranchProfile nativizedProfile,
                                   @Cached NativeProfiledMemMove memMove,
                                   @Cached ArgumentListExpander argsExpander) {

                Object[][][] expansionsOutArg = new Object[1][][];

                // TODO: expansion needed?
                Object[] realArguments = argsExpander.expand(originalArgs, expansionsOutArg);
                Object[][] expansions = expansionsOutArg[0];

                LLVMDarwinAarch64VaListStorage vaListStorage = new LLVMDarwinAarch64VaListStorage();
                vaListStorage.initialize(realArguments, numberOfExplicitArguments, frame, stackAllocationNode);
                // writeLibrary.writeGenericI64(self.nativeVAListPtr, 0, vaListStorage);
                pointerRegSaveAreaStore.executeWithTarget(self.nativeVAListPtr, 0, LLVMManagedPointer.create(vaListStorage));

                // TODO: execute above already does toNative when storing to memory?
                vaListStorage.toNative(i64RegSaveAreaStore, i32RegSaveAreaStore, fp80bitRegSaveAreaStore, pointerRegSaveAreaStore, i64OverflowArgAreaStore, i32OverflowArgAreaStore, fp80bitOverflowArgAreaStore,
                        pointerOverflowArgAreaStore, memMove, nativizedProfile);
            }

        }

        @ExportMessage
        public void cleanup(@SuppressWarnings("unused") Frame frame) {
            // nop
        }

        @ExportMessage
        @ImportStatic({LLVMTypes.class, LLVMDarwinAarch64VaListStorage.class})
        static class Copy {

            @Specialization
            @GenerateAOT.Exclude // recursion cut
            static void copyToManagedObject(NativeVAListWrapper source, LLVMDarwinAarch64VaListStorage dest, Frame frame,
                                            @CachedLibrary("source") LLVMVaListLibrary vaListLibrary) {
                vaListLibrary.copy(source, new NativeVAListWrapper(dest.vaListStackPtr), frame);
                dest.nativized = true;
            }

            @Specialization(limit = "1")
            @GenerateAOT.Exclude // TODO: Needed?
            static void copyToMaybeVAList(NativeVAListWrapper source, LLVMMaybeVaPointer dest, Frame frame,
                        @Shared("pointerOffsetLoadNode") @Cached LLVMPointerOffsetLoadNode pointerOffsetLoadNode,
                        @Shared("stackAllocationNode") @Cached StackAllocationNode stackAllocationNode,
                        @CachedLibrary("dest") LLVMManagedWriteLibrary writeLibrary) {
                LLVMManagedPointer vaListSourcePtr = (LLVMManagedPointer) pointerOffsetLoadNode.executeWithTarget(source.nativeVAListPtr, 0);
                LLVMDarwinAarch64VaListStorage vaListSource = (LLVMDarwinAarch64VaListStorage) vaListSourcePtr.getObject();

                LLVMDarwinAarch64VaListStorage vaListDest = new LLVMDarwinAarch64VaListStorage();
                LLVMDarwinAarch64VaListStorage.Copy.copyManaged(vaListSource, vaListDest, frame, stackAllocationNode);
                writeLibrary.writePointer(dest, 0, LLVMManagedPointer.create(vaListDest));
            }

            @Specialization
            static void copyToNative(NativeVAListWrapper source, NativeVAListWrapper dest, @SuppressWarnings("unused") Frame frame,
                        @Cached.Exclusive @Cached LLVMPointerOffsetStoreNode pointerOffsetStoreNode) {
                // TODO: is that enough?
                pointerOffsetStoreNode.executeWithTarget(dest.nativeVAListPtr, 0, source.nativeVAListPtr);
            }
        }

        @SuppressWarnings("static-method")
        @ExportMessage
        public Object shift(Type type, Frame frame,
                            @Cached LLVMDoubleOffsetLoadNode loadDoubleNode,
                            @Cached LLVMI64OffsetLoadNode loadi64Node,
                            @Cached LLVMI32OffsetLoadNode loadi32Node) {
            Object ret = null;
            if (PrimitiveType.DOUBLE.equals(type)) {
                ret = loadDoubleNode.executeWithTarget(nativeVAListPtr, 8 * offset);
            } else if (type.equals(PrimitiveType.I64)) {
                try {
                    ret = loadi64Node.executeWithTarget(nativeVAListPtr, 8 * offset);
                } catch (UnexpectedResultException e) {
                    e.printStackTrace();
                    System.exit(2);
                }
            } else if (type.equals(PrimitiveType.I32)) {
                ret = loadi32Node.executeWithTarget(nativeVAListPtr, 8 * offset);
            } else {
                CompilerDirectives.shouldNotReachHere("Implement type: " + type);
            }
            offset += 1;
            return ret;
        }
    }
}
