package com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.aarch64;

import java.util.ArrayList;
import java.util.Arrays;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.CachedLanguage;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.FrameInstance.FrameAccess;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.llvm.runtime.LLVMLanguage;
import com.oracle.truffle.llvm.runtime.LLVMVarArgCompoundValue;
import com.oracle.truffle.llvm.runtime.datalayout.DataLayout;
import com.oracle.truffle.llvm.runtime.debug.value.LLVMSourceTypeFactory;
import com.oracle.truffle.llvm.runtime.except.LLVMMemoryException;
import com.oracle.truffle.llvm.runtime.floating.LLVM80BitFloat;
import com.oracle.truffle.llvm.runtime.library.internal.LLVMManagedReadLibrary;
import com.oracle.truffle.llvm.runtime.library.internal.LLVMManagedWriteLibrary;
import com.oracle.truffle.llvm.runtime.memory.LLVMMemMoveNode;
import com.oracle.truffle.llvm.runtime.memory.LLVMStack.LLVMGetStackSpaceInstruction;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.nodes.func.LLVMRootNode;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.va.LLVMVaListLibrary;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.x86.LLVMX86_64VaListStorage;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.x86.LLVMX86_64VaListStorage.AbstractOverflowArgArea;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.x86.LLVMX86_64VaListStorage.NativeAllocaInstruction;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.x86.LLVMX86_64VaListStorage.VarArgArea;
import com.oracle.truffle.llvm.runtime.nodes.memory.NativeProfiledMemMove;
import com.oracle.truffle.llvm.runtime.nodes.memory.store.LLVM80BitFloatStoreNode.LLVM80BitFloatOffsetStoreNode;
import com.oracle.truffle.llvm.runtime.nodes.memory.store.LLVMI32StoreNode.LLVMI32OffsetStoreNode;
import com.oracle.truffle.llvm.runtime.nodes.memory.store.LLVMI64StoreNode.LLVMI64OffsetStoreNode;
import com.oracle.truffle.llvm.runtime.nodes.memory.store.LLVMPointerStoreNode.LLVMPointerOffsetStoreNode;
import com.oracle.truffle.llvm.runtime.pointer.LLVMManagedPointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMNativePointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMPointer;
import com.oracle.truffle.llvm.runtime.types.PointerType;
import com.oracle.truffle.llvm.runtime.types.PrimitiveType;
import com.oracle.truffle.llvm.runtime.types.StructureType;
import com.oracle.truffle.llvm.runtime.types.Type;
import com.oracle.truffle.llvm.spi.NativeTypeLibrary;

@ExportLibrary(LLVMManagedReadLibrary.class)
@ExportLibrary(LLVMManagedWriteLibrary.class)
@ExportLibrary(LLVMVaListLibrary.class)
@ExportLibrary(InteropLibrary.class)
@ExportLibrary(NativeTypeLibrary.class)
public class LLVMAarch64VaListStorage implements TruffleObject {

    // %struct.__va_list = type { i8*, i8*, i8*, i32, i32 }

    public static final StructureType VA_LIST_TYPE = StructureType.createNamedFromList("struct.__va_list", false,
                    new ArrayList<>(Arrays.asList(PointerType.I8, PointerType.I8, PointerType.I8, PrimitiveType.I32, PrimitiveType.I32)));

    private final LLVMRootNode rootNode;

    private Object[] realArguments;
    private int numberOfExplicitArguments;

    private int gpOffset;
    private int fpOffset;

    private LLVMNativePointer nativized;
    private LLVMPointer overflowArgAreaBaseNativePtr;

    private RegSaveArea gpSaveArea;
    private LLVMPointer gpSaveAreaPtr;

    private RegSaveArea fpSaveArea;
    private LLVMPointer fpSaveAreaPtr;

    private OverflowArgArea overflowArgArea;

    public LLVMAarch64VaListStorage(RootNode rootNode) {
        assert rootNode instanceof LLVMRootNode;
        this.rootNode = (LLVMRootNode) rootNode;
    }

    public boolean isNativized() {
        return nativized != null;
    }

    private static int calculateUsedFpArea(Object[] realArguments, int numberOfExplicitArguments) {
        assert numberOfExplicitArguments <= realArguments.length;

        int usedFpArea = 0;
        for (int i = 0; i < numberOfExplicitArguments && usedFpArea < Aarch64BitVarArgs.FP_LIMIT; i++) {
            if (LLVMX86_64VaListStorage.getVarArgArea(realArguments[i]) == VarArgArea.FP_AREA) {
                usedFpArea += Aarch64BitVarArgs.FP_STEP;
            }
        }
        return usedFpArea;
    }

    private static int calculateUsedGpArea(Object[] realArguments, int numberOfExplicitArguments) {
        assert numberOfExplicitArguments <= realArguments.length;

        int usedGpArea = 0;
        for (int i = 0; i < numberOfExplicitArguments && usedGpArea < Aarch64BitVarArgs.GP_LIMIT; i++) {
            if (LLVMX86_64VaListStorage.getVarArgArea(realArguments[i]) == VarArgArea.GP_AREA) {
                usedGpArea += Aarch64BitVarArgs.GP_STEP;
            }
        }

        return usedGpArea;
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
    Object getNativeType(@CachedLanguage LLVMLanguage language) {
        // This method should never be invoked
        return language.getInteropType(LLVMSourceTypeFactory.resolveType(VA_LIST_TYPE, LLVMX86_64VaListStorage.getDataLayout()));
    }

    // LLVMManagedReadLibrary implementation

    @ExportMessage(name = "isReadable")
    @ExportMessage(name = "isWritable")
    @SuppressWarnings("static-method")
    boolean isAccessible() {
        return true;
    }

    @SuppressWarnings("static-method")
    @ExportMessage
    byte readI8(@SuppressWarnings("unused") long offset) {
        CompilerDirectives.transferToInterpreter();
        throw new UnsupportedOperationException("Should not get here");
    }

    @SuppressWarnings("static-method")
    @ExportMessage
    short readI16(@SuppressWarnings("unused") long offset) {
        CompilerDirectives.transferToInterpreter();
        throw new UnsupportedOperationException("Should not get here");
    }

    @ExportMessage
    static class ReadI32 {

        @Specialization(guards = "!vaList.isNativized()")
        static int readManagedI32(LLVMAarch64VaListStorage vaList, long offset) {
            switch ((int) offset) {
                case Aarch64BitVarArgs.GP_OFFSET:
                    return vaList.gpOffset;
                case Aarch64BitVarArgs.FP_OFFSET:
                    return vaList.fpOffset;
                default:
                    CompilerDirectives.transferToInterpreter();
                    throw new UnsupportedOperationException("Invalid offset " + offset);
            }
        }

        @Specialization(guards = "vaList.isNativized()")
        static int readNativeI32(LLVMAarch64VaListStorage vaList, long offset, @CachedLibrary(limit = "1") LLVMManagedReadLibrary nativeReadLibrary) {
            return nativeReadLibrary.readI32(vaList.nativized, offset);
        }
    }

    @ExportMessage
    static class ReadPointer {

        @Specialization(guards = "!vaList.isNativized()")
        static LLVMPointer readManagedPointer(LLVMAarch64VaListStorage vaList, long offset) {
            switch ((int) offset) {
                case Aarch64BitVarArgs.OVERFLOW_ARG_AREA:
                    return vaList.overflowArgArea.getCurrentArgPtr();
                case Aarch64BitVarArgs.GP_SAVE_AREA:
                    return vaList.gpSaveAreaPtr;
                case Aarch64BitVarArgs.FP_SAVE_AREA:
                    return vaList.fpSaveAreaPtr;
                default:
                    CompilerDirectives.transferToInterpreter();
                    throw new UnsupportedOperationException("Invalid offset " + offset);
            }
        }

        @Specialization(guards = "vaList.isNativized()")
        static LLVMPointer readNativePointer(LLVMAarch64VaListStorage vaList, long offset,
                        @CachedLibrary(limit = "1") LLVMManagedReadLibrary nativeReadLibrary) {
            return nativeReadLibrary.readPointer(vaList.nativized, offset);
        }
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    Object readGenericI64(long offset) {
        switch ((int) offset) {
            case Aarch64BitVarArgs.OVERFLOW_ARG_AREA:
                return overflowArgArea.getCurrentArgPtr();
            default:
                CompilerDirectives.transferToInterpreter();
                throw new UnsupportedOperationException("Should not get here");
        }
    }

    // LLVMManagedWriteLibrary implementation

    @ExportMessage
    @SuppressWarnings("static-method")
    void writeI8(@SuppressWarnings("unused") long offset, @SuppressWarnings("unused") byte value) {
        CompilerDirectives.transferToInterpreter();
        throw new UnsupportedOperationException("Should not get here");
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    void writeI16(@SuppressWarnings("unused") long offset, @SuppressWarnings("unused") short value) {
        CompilerDirectives.transferToInterpreter();
        throw new UnsupportedOperationException("Should not get here");
    }

    @ExportMessage
    static class WriteI32 {

        @Specialization(guards = "!vaList.isNativized()")
        static void writeManaged(LLVMAarch64VaListStorage vaList, long offset, int value) {
            switch ((int) offset) {
                case Aarch64BitVarArgs.GP_OFFSET:
                    vaList.gpOffset = value;
                    vaList.gpSaveArea.shift();
                    break;
                case Aarch64BitVarArgs.FP_OFFSET:
                    vaList.fpOffset = value;
                    vaList.fpSaveArea.shift();
                    break;
                default:
                    CompilerDirectives.transferToInterpreter();
                    throw new UnsupportedOperationException("Should not get here");
            }
        }

        @Specialization(guards = "vaList.isNativized()")
        static void writeNative(LLVMAarch64VaListStorage vaList, long offset, int value,
                        @CachedLibrary(limit = "1") LLVMManagedWriteLibrary nativeWriteLibrary) {
            nativeWriteLibrary.writeI32(vaList.nativized, offset, value);
        }
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    void writeGenericI64(@SuppressWarnings("unused") long offset, @SuppressWarnings("unused") Object value) {
        CompilerDirectives.transferToInterpreter();
        throw new UnsupportedOperationException("Should not get here");
    }

    @ExportMessage
    static class WritePointer {

        @Specialization(guards = "!vaList.isNativized()")
        static void writeManaged(LLVMAarch64VaListStorage vaList, long offset, @SuppressWarnings("unused") LLVMPointer value) {
            switch ((int) offset) {
                case Aarch64BitVarArgs.OVERFLOW_ARG_AREA:
                    // Assume that updating the overflowArea pointer means shifting the current
                    // argument, according to abi
                    if (!LLVMManagedPointer.isInstance(value) || LLVMManagedPointer.cast(value).getObject() != vaList.overflowArgArea) {
                        CompilerDirectives.transferToInterpreter();
                        throw new LLVMMemoryException(null, "updates to VA_LIST overflowArea pointer can only shift the current argument");
                    }
                    vaList.overflowArgArea.setOffset(LLVMManagedPointer.cast(value).getOffset());
                    break;
                default:
                    CompilerDirectives.transferToInterpreter();
                    throw new UnsupportedOperationException("Should not get here");
            }
        }

        @Specialization(guards = "vaList.isNativized()")
        static void writeNative(LLVMAarch64VaListStorage vaList, long offset, LLVMPointer value,
                        @CachedLibrary(limit = "1") LLVMManagedWriteLibrary nativeWriteLibrary) {
            nativeWriteLibrary.writePointer(vaList.nativized, offset, value);
        }
    }

    // LLVMVaListLibrary implementation

    @ExportMessage
    static class Initialize {

        @Specialization(guards = {"!vaList.isNativized()"})
        static void initializeManaged(LLVMAarch64VaListStorage vaList, Object[] realArgs, int numOfExpArgs) {
            vaList.realArguments = realArgs;
            vaList.numberOfExplicitArguments = numOfExpArgs;
            assert numOfExpArgs <= realArgs.length;

            int namedGPUsage = calculateUsedGpArea(realArgs, numOfExpArgs);
            vaList.gpOffset = namedGPUsage - Aarch64BitVarArgs.GP_LIMIT;
            int namedFPUsage = calculateUsedFpArea(realArgs, numOfExpArgs);
            vaList.fpOffset = namedFPUsage - Aarch64BitVarArgs.FP_LIMIT;

            int[] gpIdx = new int[realArgs.length];
            Arrays.fill(gpIdx, -1);
            int[] fpIdx = new int[realArgs.length];
            Arrays.fill(fpIdx, -1);

            int gp = vaList.gpOffset;
            int fp = vaList.fpOffset;

            Object[] overflowArgs = new Object[realArgs.length];
            long[] overflowAreaArgOffsets = new long[realArgs.length];
            Arrays.fill(overflowAreaArgOffsets, -1);

            int oi = 0;
            int overflowArea = 0;

            for (int i = numOfExpArgs; i < realArgs.length; i++) {
                final Object arg = realArgs[i];

                final LLVMX86_64VaListStorage.VarArgArea area = LLVMX86_64VaListStorage.getVarArgArea(arg);
                if (area == VarArgArea.GP_AREA && gp < 0) {
                    gpIdx[(Aarch64BitVarArgs.GP_LIMIT + gp) / Aarch64BitVarArgs.GP_STEP] = i;
                    gp += Aarch64BitVarArgs.GP_STEP;
                } else if (area == VarArgArea.FP_AREA && fp < 0) {
                    fpIdx[(Aarch64BitVarArgs.FP_LIMIT + fp) / Aarch64BitVarArgs.FP_STEP] = i;
                    fp += Aarch64BitVarArgs.FP_STEP;
                } else if (area != VarArgArea.OVERFLOW_AREA) {
                    overflowAreaArgOffsets[oi] = overflowArea;
                    overflowArea += Aarch64BitVarArgs.STACK_STEP;
                    overflowArgs[oi++] = arg;
                } else if (arg instanceof LLVM80BitFloat) {
                    overflowAreaArgOffsets[oi] = overflowArea;
                    overflowArea += 16;
                    overflowArgs[oi++] = arg;
                } else if (arg instanceof LLVMVarArgCompoundValue) {
                    LLVMVarArgCompoundValue obj = (LLVMVarArgCompoundValue) arg;
                    if (gp + obj.getSize() <= 0 && obj.getSize() <= 2 * Aarch64BitVarArgs.GP_STEP) {
                        assert obj.getSize() % Aarch64BitVarArgs.GP_STEP == 0;

                        gpIdx[(Aarch64BitVarArgs.GP_LIMIT + gp) / Aarch64BitVarArgs.GP_STEP] = i;
                        if (obj.getSize() == 2 * Aarch64BitVarArgs.GP_STEP) {
                            gpIdx[1 + (Aarch64BitVarArgs.GP_LIMIT + gp) / Aarch64BitVarArgs.GP_STEP] = i;
                        }

                        gp += obj.getSize();
                    } else {
                        gp = 0; // Terminate the GP save area
                        overflowAreaArgOffsets[oi] = overflowArea;
                        overflowArea += obj.getSize();
                        overflowArgs[oi++] = arg;
                    }
                } else {
                    CompilerDirectives.transferToInterpreter();
                    throw new AssertionError(arg);
                }
            }

            // Clear the gpOffset/fpOffset in the case the FP/GP save area is empty
            if (gp == vaList.gpOffset) {
                vaList.gpOffset = 0;
            }
            if (fp == vaList.fpOffset) {
                vaList.fpOffset = 0;
            }

            vaList.gpSaveArea = new RegSaveArea(realArgs, gpIdx, numOfExpArgs, Aarch64BitVarArgs.GP_STEP, Aarch64BitVarArgs.GP_LIMIT);
            vaList.gpSaveAreaPtr = LLVMManagedPointer.create(vaList.gpSaveArea);
            vaList.fpSaveArea = new RegSaveArea(realArgs, fpIdx, numOfExpArgs, Aarch64BitVarArgs.FP_STEP, Aarch64BitVarArgs.FP_LIMIT);
            vaList.fpSaveAreaPtr = LLVMManagedPointer.create(vaList.fpSaveArea);
            vaList.overflowArgArea = new OverflowArgArea(overflowArgs, overflowAreaArgOffsets, overflowArea, oi);
        }

        @Specialization(guards = {"vaList.isNativized()"})
        static void initializeNativized(LLVMAarch64VaListStorage vaList, Object[] realArgs, int numOfExpArgs,
                        @Cached NativeAllocaInstruction stackAllocationNode,
                        @Cached LLVMI64OffsetStoreNode i64RegSaveAreaStore,
                        @Cached LLVMI32OffsetStoreNode i32RegSaveAreaStore,
                        @Cached LLVM80BitFloatOffsetStoreNode fp80bitRegSaveAreaStore,
                        @Cached LLVMPointerOffsetStoreNode pointerRegSaveAreaStore,
                        @Cached LLVMI64OffsetStoreNode i64OverflowArgAreaStore,
                        @Cached LLVMI32OffsetStoreNode i32OverflowArgAreaStore,
                        @Cached LLVM80BitFloatOffsetStoreNode fp80bitOverflowArgAreaStore,
                        @Cached LLVMPointerOffsetStoreNode pointerOverflowArgAreaStore,
                        @Cached LLVMI32OffsetStoreNode gpOffsetStore,
                        @Cached LLVMI32OffsetStoreNode fpOffsetStore,
                        @Cached LLVMPointerOffsetStoreNode overflowArgAreaStore,
                        @Cached LLVMPointerOffsetStoreNode gpSaveAreaStore,
                        @Cached LLVMPointerOffsetStoreNode fpSaveAreaStore,
                        @Cached NativeProfiledMemMove memMove) {
            initializeManaged(vaList, realArgs, numOfExpArgs);

            VirtualFrame frame = (VirtualFrame) Truffle.getRuntime().getCurrentFrame().getFrame(FrameAccess.READ_WRITE);
            LLVMPointer[] regSaveAreaNativePtrs = vaList.allocateNativeAreas(stackAllocationNode, gpOffsetStore, fpOffsetStore, overflowArgAreaStore, gpSaveAreaStore, fpSaveAreaStore, frame);

            initNativeAreas(vaList.realArguments, vaList.numberOfExplicitArguments, vaList.gpOffset, vaList.fpOffset, regSaveAreaNativePtrs[0], regSaveAreaNativePtrs[1],
                            vaList.overflowArgAreaBaseNativePtr,
                            i64RegSaveAreaStore,
                            i32RegSaveAreaStore, fp80bitRegSaveAreaStore, pointerRegSaveAreaStore, i64OverflowArgAreaStore, i32OverflowArgAreaStore, fp80bitOverflowArgAreaStore,
                            pointerOverflowArgAreaStore, memMove);
        }

    }

    @SuppressWarnings("static-method")
    LLVMExpressionNode createAllocaNode(LLVMLanguage language) {
        DataLayout dataLayout = LLVMX86_64VaListStorage.getDataLayout();
        return language.getActiveConfiguration().createNodeFactory(language, dataLayout).createAlloca(VA_LIST_TYPE, 16);
    }

    LLVMExpressionNode createAllocaNodeUncached(LLVMLanguage language) {
        DataLayout dataLayout = LLVMX86_64VaListStorage.getDataLayout();
        LLVMExpressionNode alloca = language.getActiveConfiguration().createNodeFactory(language, dataLayout).createAlloca(VA_LIST_TYPE, 16);
        if (alloca instanceof LLVMGetStackSpaceInstruction) {
            ((LLVMGetStackSpaceInstruction) alloca).setStackAccess(rootNode.getStackAccess());
        }
        return alloca;
    }

    @SuppressWarnings("static-method")
    @ExportMessage
    @TruffleBoundary
    void toNative(@SuppressWarnings("unused") @CachedLanguage() LLVMLanguage language,
                    @Cached(value = "this.createAllocaNode(language)", uncached = "this.createAllocaNodeUncached(language)") LLVMExpressionNode allocaNode,
                    @Cached NativeAllocaInstruction stackAllocationNode,
                    @Cached LLVMI64OffsetStoreNode i64RegSaveAreaStore,
                    @Cached LLVMI32OffsetStoreNode i32RegSaveAreaStore,
                    @Cached LLVM80BitFloatOffsetStoreNode fp80bitRegSaveAreaStore,
                    @Cached LLVMPointerOffsetStoreNode pointerRegSaveAreaStore,
                    @Cached LLVMI64OffsetStoreNode i64OverflowArgAreaStore,
                    @Cached LLVMI32OffsetStoreNode i32OverflowArgAreaStore,
                    @Cached LLVM80BitFloatOffsetStoreNode fp80bitOverflowArgAreaStore,
                    @Cached LLVMPointerOffsetStoreNode pointerOverflowArgAreaStore,
                    @Cached LLVMI32OffsetStoreNode gpOffsetStore,
                    @Cached LLVMI32OffsetStoreNode fpOffsetStore,
                    @Cached LLVMPointerOffsetStoreNode overflowArgAreaStore,
                    @Cached LLVMPointerOffsetStoreNode gpSaveAreaStore,
                    @Cached LLVMPointerOffsetStoreNode fpSaveAreaStore,
                    @Cached NativeProfiledMemMove memMove,
                    @Cached BranchProfile nativizedProfile) {

        if (nativized != null) {
            nativizedProfile.enter();
            return;
        }

        // N.B. Using FrameAccess.READ_WRITE may lead to throwing NPE, nevertheless using the
        // safe
        // FrameAccess.READ_ONLY is not sufficient as some nodes below need to write to the
        // frame.
        // Therefore toNative is put behind the Truffle boundary and FrameAccess.MATERIALIZE is
        // used as a workaround.
        VirtualFrame frame = (VirtualFrame) Truffle.getRuntime().getCurrentFrame().getFrame(FrameAccess.MATERIALIZE);
        nativized = LLVMNativePointer.cast(allocaNode.executeGeneric(frame));

        if (overflowArgArea == null) {
            // toNative is called before the va_list is initialized by va_start. It happens in
            // situations like this:
            //
            // va_list va;
            // va_list *pva = &va;
            //
            // In this case we just allocate the va_list on the native stack and defer its
            // initialization until va_start is called.
            return;
        }

        LLVMPointer[] regSaveAreaNativePtrs = allocateNativeAreas(stackAllocationNode, gpOffsetStore, fpOffsetStore, overflowArgAreaStore, gpSaveAreaStore, fpSaveAreaStore, frame);

        initNativeAreas(this.realArguments, this.numberOfExplicitArguments, this.gpOffset, this.fpOffset, regSaveAreaNativePtrs[0], regSaveAreaNativePtrs[1], this.overflowArgAreaBaseNativePtr,
                        i64RegSaveAreaStore,
                        i32RegSaveAreaStore, fp80bitRegSaveAreaStore, pointerRegSaveAreaStore, i64OverflowArgAreaStore, i32OverflowArgAreaStore, fp80bitOverflowArgAreaStore,
                        pointerOverflowArgAreaStore, memMove);
    }

    private LLVMPointer[] allocateNativeAreas(NativeAllocaInstruction stackAllocationNode, LLVMI32OffsetStoreNode gpOffsetStore, LLVMI32OffsetStoreNode fpOffsetStore,
                    LLVMPointerOffsetStoreNode overflowArgAreaStore, LLVMPointerOffsetStoreNode gpSaveAreaStore, LLVMPointerOffsetStoreNode fpSaveAreaStore, VirtualFrame frame) {
        this.overflowArgAreaBaseNativePtr = stackAllocationNode.executeWithTarget(frame, overflowArgArea.overflowAreaSize, rootNode.getStackAccess());
        LLVMPointer gpSaveAreaNativePtr = stackAllocationNode.executeWithTarget(frame, Aarch64BitVarArgs.GP_LIMIT, rootNode.getStackAccess());
        gpSaveAreaNativePtr = gpSaveAreaNativePtr.increment(Aarch64BitVarArgs.GP_LIMIT);
        LLVMPointer fpSaveAreaNativePtr = stackAllocationNode.executeWithTarget(frame, Aarch64BitVarArgs.FP_LIMIT, rootNode.getStackAccess());
        fpSaveAreaNativePtr = fpSaveAreaNativePtr.increment(Aarch64BitVarArgs.FP_LIMIT);

        gpOffsetStore.executeWithTarget(nativized, Aarch64BitVarArgs.GP_OFFSET, gpOffset);

        fpOffsetStore.executeWithTarget(nativized, Aarch64BitVarArgs.FP_OFFSET, fpOffset);

        overflowArgAreaStore.executeWithTarget(nativized, Aarch64BitVarArgs.OVERFLOW_ARG_AREA, overflowArgAreaBaseNativePtr.increment(overflowArgArea.getOffset()));
        gpSaveAreaStore.executeWithTarget(nativized, Aarch64BitVarArgs.GP_SAVE_AREA, gpSaveAreaNativePtr);
        fpSaveAreaStore.executeWithTarget(nativized, Aarch64BitVarArgs.FP_SAVE_AREA, fpSaveAreaNativePtr);

        return new LLVMPointer[]{gpSaveAreaNativePtr, fpSaveAreaNativePtr};
    }

    /**
     * Reconstruct the native areas according to Aarch64 ABI.
     */
    private static void initNativeAreas(Object[] realArguments, int numberOfExplicitArguments, int initGPOffset, int initFPOffset,
                    LLVMPointer gpSaveAreaNativePtr,
                    LLVMPointer fpSaveAreaNativePtr,
                    LLVMPointer overflowArgAreaBaseNativePtr,
                    LLVMI64OffsetStoreNode i64RegSaveAreaStore,
                    LLVMI32OffsetStoreNode i32RegSaveAreaStore,
                    LLVM80BitFloatOffsetStoreNode fp80bitRegSaveAreaStore,
                    LLVMPointerOffsetStoreNode pointerRegSaveAreaStore,
                    LLVMI64OffsetStoreNode i64OverflowArgAreaStore,
                    LLVMI32OffsetStoreNode i32OverflowArgAreaStore,
                    LLVM80BitFloatOffsetStoreNode fp80bitOverflowArgAreaStore,
                    LLVMPointerOffsetStoreNode pointerOverflowArgAreaStore,
                    LLVMMemMoveNode memMove) {
        int gp = initGPOffset;
        int fp = initFPOffset;

        final int vaLength = realArguments.length - numberOfExplicitArguments;
        if (vaLength > 0) {
            int overflowOffset = 0;

            // TODO (chaeubl): this generates pretty bad machine code as we don't know anything
            // about the arguments
            for (int i = 0; i < vaLength; i++) {
                final Object object = realArguments[numberOfExplicitArguments + i];
                final VarArgArea area = LLVMX86_64VaListStorage.getVarArgArea(object);

                if (area == VarArgArea.GP_AREA && gp < Aarch64BitVarArgs.GP_LIMIT) {
                    LLVMX86_64VaListStorage.storeArgument(gpSaveAreaNativePtr, gp, memMove, i64RegSaveAreaStore, i32RegSaveAreaStore,
                                    fp80bitRegSaveAreaStore, pointerRegSaveAreaStore, object);
                    gp += Aarch64BitVarArgs.GP_STEP;
                } else if (area == VarArgArea.FP_AREA && fp < Aarch64BitVarArgs.FP_LIMIT) {
                    LLVMX86_64VaListStorage.storeArgument(fpSaveAreaNativePtr, fp, memMove, i64RegSaveAreaStore, i32RegSaveAreaStore,
                                    fp80bitRegSaveAreaStore, pointerRegSaveAreaStore, object);
                    fp += Aarch64BitVarArgs.FP_STEP;
                } else if ((object instanceof LLVMVarArgCompoundValue) && (gp + ((LLVMVarArgCompoundValue) object).getSize()) <= 0 &&
                                ((LLVMVarArgCompoundValue) object).getSize() <= 2 * Aarch64BitVarArgs.GP_STEP) {
                    long sz = ((LLVMVarArgCompoundValue) object).getSize();
                    assert sz % Aarch64BitVarArgs.GP_STEP == 0;
                    LLVMX86_64VaListStorage.storeArgument(gpSaveAreaNativePtr, gp, memMove, i64RegSaveAreaStore, i32RegSaveAreaStore,
                                    fp80bitRegSaveAreaStore, pointerRegSaveAreaStore, object);
                    gp += sz;
                } else {
                    gp = 0; // Terminate the GP save area
                    overflowOffset += LLVMX86_64VaListStorage.storeArgument(overflowArgAreaBaseNativePtr, overflowOffset, memMove,
                                    i64OverflowArgAreaStore, i32OverflowArgAreaStore,
                                    fp80bitOverflowArgAreaStore, pointerOverflowArgAreaStore, object);
                }
            }
        }
    }

    @ExportMessage
    boolean isPointer() {
        return nativized != null && LLVMNativePointer.isInstance(nativized);
    }

    @ExportMessage
    long asPointer() {
        return nativized == null ? 0L : LLVMNativePointer.cast(nativized).asNative();
    }

    @ExportMessage
    public void cleanup() {

    }

    @ExportMessage
    static class Copy {

        @Specialization(guards = {"!source.isNativized()"})
        static void copyManaged(LLVMAarch64VaListStorage source, LLVMAarch64VaListStorage dest, @SuppressWarnings("unused") int numberOfExplicitArguments) {
            dest.realArguments = source.realArguments;
            dest.numberOfExplicitArguments = source.numberOfExplicitArguments;
            dest.fpOffset = source.fpOffset;
            dest.gpOffset = source.gpOffset;
            dest.gpSaveArea = source.gpSaveArea;
            dest.gpSaveAreaPtr = source.gpSaveAreaPtr;
            dest.fpSaveArea = source.fpSaveArea;
            dest.fpSaveAreaPtr = source.fpSaveAreaPtr;
            dest.overflowArgArea = source.overflowArgArea.clone();
            dest.nativized = null;
            dest.overflowArgAreaBaseNativePtr = null;
        }

        @Specialization(guards = {"source.isNativized()"})
        static void copyNative(LLVMAarch64VaListStorage source, LLVMAarch64VaListStorage dest, int numberOfExplicitArguments, @CachedLibrary("source") LLVMManagedReadLibrary srcReadLib) {

            // The destination va_list will be in the managed state, even if the source has been
            // nativized. We need to read some state from the native memory, though.

            copyManaged(source, dest, numberOfExplicitArguments);

            dest.fpOffset = srcReadLib.readI32(source, Aarch64BitVarArgs.FP_OFFSET);
            dest.gpOffset = srcReadLib.readI32(source, Aarch64BitVarArgs.GP_OFFSET);
            dest.overflowArgArea.setOffset(getArgPtrFromNativePtr(source, srcReadLib));
        }
    }

    /**
     * Calculate the number of argument shifts in the overflow area.
     *
     * @param srcVaList
     * @param readLib
     */
    private static long getArgPtrFromNativePtr(LLVMAarch64VaListStorage srcVaList, LLVMManagedReadLibrary readLib) {
        long curAddr;
        long baseAddr;
        LLVMPointer overflowAreaPtr = readLib.readPointer(srcVaList, Aarch64BitVarArgs.OVERFLOW_ARG_AREA);
        if (LLVMNativePointer.isInstance(overflowAreaPtr)) {
            curAddr = LLVMNativePointer.cast(overflowAreaPtr).asNative();
            baseAddr = LLVMNativePointer.cast(srcVaList.overflowArgAreaBaseNativePtr).asNative();
        } else {
            curAddr = LLVMManagedPointer.cast(overflowAreaPtr).getOffset();
            baseAddr = LLVMManagedPointer.cast(srcVaList.overflowArgAreaBaseNativePtr).getOffset();
        }
        return curAddr - baseAddr;
    }

    /**
     * This is the implementation of the {@code va_arg} instruction.
     */
    @SuppressWarnings("static-method")
    @ExportMessage
    Object shift(Type type,
                    @CachedLibrary("this") LLVMManagedReadLibrary readLib,
                    @CachedLibrary("this") LLVMManagedWriteLibrary writeLib,
                    @Cached BranchProfile regAreaProfile,
                    @Cached("createBinaryProfile()") ConditionProfile isNativizedProfile) {
        int regSaveOffs = 0;
        int regSaveStep = 0;
        boolean lookIntoRegSaveArea = true;

        VarArgArea varArgArea = LLVMX86_64VaListStorage.getVarArgArea(type);
        RegSaveArea regSaveArea = null;
        switch (varArgArea) {
            case GP_AREA:
                regSaveOffs = Aarch64BitVarArgs.GP_OFFSET;
                regSaveStep = Aarch64BitVarArgs.GP_STEP;
                regSaveArea = gpSaveArea;
                break;

            case FP_AREA:
                regSaveOffs = Aarch64BitVarArgs.FP_OFFSET;
                regSaveStep = Aarch64BitVarArgs.FP_STEP;
                regSaveArea = fpSaveArea;
                break;

            case OVERFLOW_AREA:
                lookIntoRegSaveArea = false;
                break;
        }

        if (lookIntoRegSaveArea) {
            regAreaProfile.enter();

            int offs = readLib.readI32(this, regSaveOffs);
            if (offs < 0) {
                // The va shift logic for GP/FP regsave areas is done by updating the gp/fp offset
                // field in va_list
                writeLib.writeI32(this, regSaveOffs, offs + regSaveStep);
                long n = regSaveArea.offsetToIndex(offs);
                assert regSaveArea != null;
                if (n >= 0) {
                    int i = (int) ((n << 32) >> 32);
                    return regSaveArea.args[i];
                }
            }
        }

        // overflow area
        if (isNativizedProfile.profile(isNativized())) {
            // Synchronize the managed current argument pointer from the native overflow area
            this.overflowArgArea.setOffset(getArgPtrFromNativePtr(this, readLib));
            Object currentArg = this.overflowArgArea.getCurrentArg();
            // Shift the managed current argument pointer
            this.overflowArgArea.shift(1);
            // Update the new native current argument pointer from the managed one
            long shiftOffs = this.overflowArgArea.getOffset();
            LLVMPointer shiftedOverflowAreaPtr = overflowArgAreaBaseNativePtr.increment(shiftOffs);
            writeLib.writePointer(this, Aarch64BitVarArgs.OVERFLOW_ARG_AREA, shiftedOverflowAreaPtr);

            return currentArg;
        } else {
            Object currentArg = this.overflowArgArea.getCurrentArg();
            this.overflowArgArea.shift(1);
            return currentArg;
        }
    }

    @ExportLibrary(NativeTypeLibrary.class)
    public static final class RegSaveArea extends LLVMX86_64VaListStorage.ArgsArea {

        private final int[] idx;
        private final int numOfExpArgs;
        private final int step;
        private final int limit;

        private int curArg;

        RegSaveArea(Object[] args, int[] idx, int numOfExpArgs, int step, int limit) {
            super(args);
            this.idx = idx;
            this.numOfExpArgs = numOfExpArgs;
            this.step = step;
            this.limit = limit;
        }

        @Override
        public long offsetToIndex(long offset) {
            if (offset >= 0) {
                return -1;
            }

            int s = step;
            long i = (limit + offset) / s;
            if (i > 0 && idx[(int) i] == idx[(int) i - 1]) {
                // the i-th and (i-1)-the indices refer to the same argument, which means that the
                // i-th one corresponds to the high 8 bytes of a compound argument. The remainder j
                // must therefore be calculated using modulo 16, not 8.
                s = 16;
            }

            long j = (limit + offset) % s;
            return i >= idx.length ? -1 : idx[(int) i] + (j << 32);
        }

        void shift() {
            this.curArg++;
        }

        // NativeTypeLibrary

        @SuppressWarnings("static-method")
        @ExportMessage
        public boolean hasNativeType() {
            return true;
        }

        @SuppressWarnings("static-method")
        @ExportMessage
        public Object getNativeType() {
            if (curArg < numOfExpArgs) {
                curArg = numOfExpArgs;
            }
            Object arg = curArg < args.length ? args[curArg] : null;
            return getVarArgType(arg);
        }

    }

    @ExportLibrary(NativeTypeLibrary.class)
    public static final class OverflowArgArea extends AbstractOverflowArgArea {

        final int argsCnt;

        OverflowArgArea(Object[] args, long[] offsets, int overflowAreaSize, int argsCnt) {
            super(args, offsets, overflowAreaSize);
            this.argsCnt = argsCnt;
        }

        @Override
        public OverflowArgArea clone() {
            OverflowArgArea cloned = new OverflowArgArea(args, offsets, overflowAreaSize, argsCnt);
            cloned.currentOffset = currentOffset;
            return cloned;
        }

        // NativeTypeLibrary

        @SuppressWarnings("static-method")
        @ExportMessage
        public boolean hasNativeType() {
            return true;
        }

        @ExportMessage
        public Object getNativeType() {
            // In contrast to to the X86 version, the native type is requested after the pointer to
            // this area was shifted. Therefore we have to take the previous argument.

            int i = getCurrentArgIndex();
            assert i != 0;
            if (i < 0) {
                return getVarArgType(args[argsCnt - 1]);
            } else {
                return getVarArgType(args[i - 1]);
            }
        }

    }

}
