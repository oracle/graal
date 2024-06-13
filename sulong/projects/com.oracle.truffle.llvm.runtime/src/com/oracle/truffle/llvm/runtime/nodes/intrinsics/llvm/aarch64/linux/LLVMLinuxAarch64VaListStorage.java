/*
 * Copyright (c) 2020, 2024, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.aarch64.linux;

import java.util.ArrayList;
import java.util.Arrays;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateAOT;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.llvm.runtime.LLVMLanguage;
import com.oracle.truffle.llvm.runtime.LLVMVarArgCompoundValue;
import com.oracle.truffle.llvm.runtime.debug.value.LLVMSourceTypeFactory;
import com.oracle.truffle.llvm.runtime.except.LLVMMemoryException;
import com.oracle.truffle.llvm.runtime.floating.LLVM80BitFloat;
import com.oracle.truffle.llvm.runtime.library.internal.LLVMManagedReadLibrary;
import com.oracle.truffle.llvm.runtime.library.internal.LLVMManagedWriteLibrary;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMTypes;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.va.LLVMVAEnd;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.va.LLVMVAStart;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.va.LLVMVaListLibrary;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.va.LLVMVaListStorage;
import com.oracle.truffle.llvm.runtime.nodes.memory.NativeProfiledMemMoveToNative;
import com.oracle.truffle.llvm.runtime.nodes.memory.load.LLVMI32LoadNode;
import com.oracle.truffle.llvm.runtime.nodes.memory.load.LLVMI32LoadNode.LLVMI32OffsetLoadNode;
import com.oracle.truffle.llvm.runtime.nodes.memory.load.LLVMPointerLoadNode;
import com.oracle.truffle.llvm.runtime.nodes.memory.load.LLVMPointerLoadNode.LLVMPointerOffsetLoadNode;
import com.oracle.truffle.llvm.runtime.nodes.memory.store.LLVM80BitFloatStoreNode.LLVM80BitFloatOffsetStoreNode;
import com.oracle.truffle.llvm.runtime.nodes.memory.store.LLVMI32StoreNode.LLVMI32OffsetStoreNode;
import com.oracle.truffle.llvm.runtime.nodes.memory.store.LLVMI64StoreNode.LLVMI64OffsetStoreNode;
import com.oracle.truffle.llvm.runtime.nodes.memory.store.LLVMPointerStoreNode;
import com.oracle.truffle.llvm.runtime.nodes.memory.store.LLVMPointerStoreNode.LLVMPointerOffsetStoreNode;
import com.oracle.truffle.llvm.runtime.pointer.LLVMManagedPointer;
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
@ExportLibrary(InteropLibrary.class)
public final class LLVMLinuxAarch64VaListStorage extends LLVMVaListStorage {

    // LLVM12: %struct.__va_list = type { i8*, i8*, i8*, i32, i32 }
    // LLVM14: %struct.std::__va_list = type { i8*, i8*, i8*, i32, i32 }

    // TODO: the va_list type name may change from version to version, so it may break the backward
    // compatibility.
    // We should use the type defined in a given BC module instead of this global one.
    public static final StructureType VA_LIST_TYPE_12 = StructureType.createNamedFromList("struct.__va_list", false,
                    new ArrayList<>(Arrays.asList(PointerType.I8, PointerType.I8, PointerType.I8, PrimitiveType.I32, PrimitiveType.I32)));
    public static final StructureType VA_LIST_TYPE_14 = StructureType.createNamedFromList("struct.std::__va_list", false,
                    new ArrayList<>(Arrays.asList(PointerType.I8, PointerType.I8, PointerType.I8, PrimitiveType.I32, PrimitiveType.I32)));

    private final Type vaListType;

    private Object[] originalArgs;
    private Object[][] expansions;

    private int gpOffset;
    private int fpOffset;

    private LLVMPointer overflowArgAreaBaseNativePtr;
    private LLVMPointer gpSaveAreaNativePtr;
    private LLVMPointer fpSaveAreaNativePtr;

    private RegSaveArea gpSaveArea;
    private LLVMPointer gpSaveAreaPtr;

    private RegSaveArea fpSaveArea;
    private LLVMPointer fpSaveAreaPtr;

    protected OverflowArgArea overflowArgArea;

    public LLVMLinuxAarch64VaListStorage(LLVMPointer vaListStackPtr, Type vaListType) {
        super(vaListStackPtr);
        this.vaListType = vaListType;
    }

    private static int calculateUsedFpArea(Object[] realArguments, int numberOfExplicitArguments) {
        assert numberOfExplicitArguments <= realArguments.length;

        int usedFpArea = 0;
        for (int i = 0; i < numberOfExplicitArguments && usedFpArea < Aarch64BitVarArgs.FP_LIMIT; i++) {
            if (getVarArgArea(realArguments[i]) == VarArgArea.FP_AREA) {
                usedFpArea += Aarch64BitVarArgs.FP_STEP;
            }
        }
        return usedFpArea;
    }

    private static int calculateUsedGpArea(Object[] realArguments, int numberOfExplicitArguments) {
        assert numberOfExplicitArguments <= realArguments.length;

        int usedGpArea = 0;
        for (int i = 0; i < numberOfExplicitArguments && usedGpArea < Aarch64BitVarArgs.GP_LIMIT; i++) {
            if (getVarArgArea(realArguments[i]) == VarArgArea.GP_AREA) {
                usedGpArea += Aarch64BitVarArgs.GP_STEP;
            }
        }

        return usedGpArea;
    }

    /**
     * Sanity check for special cased memmove between va_list objects.
     */
    public static void checkMemmoveLength(LLVMPointer destPtr, long length) {
        assert LLVMManagedPointer.cast(destPtr).getObject() instanceof LLVMLinuxAarch64VaListStorage;
        if (length != Aarch64BitVarArgs.SIZE_OF_VALIST) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw CompilerDirectives.shouldNotReachHere("Invalid length " + length + ". Expected length " + Aarch64BitVarArgs.SIZE_OF_VALIST);
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
        // This message is needed by ManagedMemMoveHelperNode.
        return LLVMLanguage.get(null).getInteropType(LLVMSourceTypeFactory.resolveType(vaListType, findDataLayoutFromCurrentFrame()));
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
        throw CompilerDirectives.shouldNotReachHere();
    }

    @SuppressWarnings("static-method")
    @ExportMessage
    short readI16(@SuppressWarnings("unused") long offset) {
        throw CompilerDirectives.shouldNotReachHere();
    }

    @ExportMessage
    static class ReadI32 {

        @Specialization(guards = "!vaList.isNativized()")
        static int readManagedI32(LLVMLinuxAarch64VaListStorage vaList, long offset) {
            switch ((int) offset) {
                case Aarch64BitVarArgs.GP_OFFSET:
                    return vaList.gpOffset;
                case Aarch64BitVarArgs.FP_OFFSET:
                    return vaList.fpOffset;
                default:
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    throw CompilerDirectives.shouldNotReachHere("Invalid offset " + offset);
            }
        }

        @Specialization(guards = "vaList.isNativized()")
        @GenerateAOT.Exclude // recursion cut
        static int readNativeI32(LLVMLinuxAarch64VaListStorage vaList, long offset,
                        @Cached LLVMI32LoadNode.LLVMI32OffsetLoadNode offsetLoad) {
            return offsetLoad.executeWithTarget(vaList.vaListStackPtr, offset);
        }
    }

    @ExportMessage
    static class ReadPointer {

        @Specialization(guards = "!vaList.isNativized()")
        static LLVMPointer readManagedPointer(LLVMLinuxAarch64VaListStorage vaList, long offset) {
            switch ((int) offset) {
                case Aarch64BitVarArgs.OVERFLOW_ARG_AREA:
                    return vaList.overflowArgArea.getCurrentArgPtr();
                case Aarch64BitVarArgs.GP_SAVE_AREA:
                    return vaList.gpSaveAreaPtr;
                case Aarch64BitVarArgs.FP_SAVE_AREA:
                    return vaList.fpSaveAreaPtr;
                default:
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    throw CompilerDirectives.shouldNotReachHere("Invalid offset " + offset);
            }
        }

        @Specialization(guards = "vaList.isNativized()")
        @GenerateAOT.Exclude // recursion cut
        static LLVMPointer readNativePointer(LLVMLinuxAarch64VaListStorage vaList, long offset,
                        @Cached LLVMPointerLoadNode.LLVMPointerOffsetLoadNode offsetLoad) {
            return offsetLoad.executeWithTarget(vaList.vaListStackPtr, offset);
        }
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    Object readGenericI64(long offset) {
        switch ((int) offset) {
            case Aarch64BitVarArgs.OVERFLOW_ARG_AREA:
                return overflowArgArea.getCurrentArgPtr();
            default:
                throw CompilerDirectives.shouldNotReachHere();
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
        static void writeManaged(LLVMLinuxAarch64VaListStorage vaList, long offset, int value) {
            switch ((int) offset) {
                case Aarch64BitVarArgs.GP_OFFSET:
                    vaList.gpOffset = value;
                    break;
                case Aarch64BitVarArgs.FP_OFFSET:
                    vaList.fpOffset = value;
                    break;
                default:
                    throw CompilerDirectives.shouldNotReachHere();
            }
        }

        @Specialization(guards = "vaList.isNativized()")
        @GenerateAOT.Exclude // recursion cut
        static void writeNative(LLVMLinuxAarch64VaListStorage vaList, long offset, int value,
                        @Cached LLVMI32OffsetStoreNode offsetStore) {
            offsetStore.executeWithTarget(vaList.vaListStackPtr, offset, value);
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
        static void writeManaged(LLVMLinuxAarch64VaListStorage vaList, long offset, @SuppressWarnings("unused") LLVMPointer value,
                        @Cached BranchProfile exception,
                        @CachedLibrary("vaList") LLVMManagedWriteLibrary self) {
            switch ((int) offset) {
                case Aarch64BitVarArgs.OVERFLOW_ARG_AREA:
                    /*
                     * Assume that updating the overflowArea pointer means shifting the current
                     * argument, according to abi
                     */
                    if (!LLVMManagedPointer.isInstance(value) || LLVMManagedPointer.cast(value).getObject() != vaList.overflowArgArea) {
                        exception.enter();
                        throw new LLVMMemoryException(self, "updates to VA_LIST overflowArea pointer can only shift the current argument");
                    }
                    vaList.overflowArgArea.setOffset(LLVMManagedPointer.cast(value).getOffset());
                    break;
                default:
                    throw CompilerDirectives.shouldNotReachHere();
            }
        }

        @Specialization(guards = "vaList.isNativized()")
        @GenerateAOT.Exclude // recursion cut
        static void writeNative(LLVMLinuxAarch64VaListStorage vaList, long offset, LLVMPointer value,
                        @Cached LLVMPointerStoreNode.LLVMPointerOffsetStoreNode offsetStore) {
            offsetStore.executeWithTarget(vaList.vaListStackPtr, offset, value);
        }
    }

    // LLVMVaListLibrary implementation

    @ExportMessage
    static class Initialize {

        @Specialization(guards = {"!vaList.isNativized()"})
        static void initializeManaged(LLVMLinuxAarch64VaListStorage vaList, Object[] args, int numOfExpArgs, Frame frame,
                        @Cached.Exclusive @Cached(parameters = "UNPACK_32BIT_PRIMITIVES_IN_STRUCTS") ArgumentListExpander argsExpander,
                        @Shared("stackAllocationNode") @Cached StackAllocationNode stackAllocationNode) {
            vaList.originalArgs = args;

            Object[][][] expansionsOutArg = new Object[1][][];

            vaList.realArguments = argsExpander.expand(args, expansionsOutArg);
            vaList.expansions = expansionsOutArg[0];

            vaList.numberOfExplicitArguments = numOfExpArgs;
            assert numOfExpArgs <= vaList.realArguments.length;

            int gpUsage = calculateUsedGpArea(vaList.realArguments, numOfExpArgs);
            vaList.gpOffset = gpUsage - Aarch64BitVarArgs.GP_LIMIT;
            int fpUsage = calculateUsedFpArea(vaList.realArguments, numOfExpArgs);
            vaList.fpOffset = fpUsage - Aarch64BitVarArgs.FP_LIMIT;

            int[] gpIdx = new int[vaList.realArguments.length];
            Arrays.fill(gpIdx, -1);
            int[] fpIdx = new int[vaList.realArguments.length];
            Arrays.fill(fpIdx, -1);

            int gp = vaList.gpOffset;
            int fp = vaList.fpOffset;

            Object[] overflowArgs = new Object[vaList.realArguments.length];
            long[] overflowAreaArgOffsets = new long[vaList.realArguments.length];
            Arrays.fill(overflowAreaArgOffsets, -1);

            int ei = -1;
            int expansionStart = 0;
            int expansionLength = 0;
            int remainingExpLength = 0;
            int oi = 0;
            long overflowArea = 0;

            for (int i = 0; i < vaList.realArguments.length; i++) {
                final Object arg = vaList.realArguments[i];

                if (i >= expansionStart + expansionLength) {
                    ei++;
                    expansionStart += expansionLength;
                    expansionLength = vaList.expansions == null || vaList.expansions[ei] == null ? 1 : vaList.expansions[ei].length;
                    remainingExpLength = expansionLength;
                } else {
                    remainingExpLength--;
                }

                if (i < numOfExpArgs) {
                    continue;
                }

                final VarArgArea area = getVarArgArea(arg);
                if (area == VarArgArea.GP_AREA) {
                    if (gp + remainingExpLength * Aarch64BitVarArgs.GP_STEP <= 0) {
                        gpIdx[(Aarch64BitVarArgs.GP_LIMIT + gp) / Aarch64BitVarArgs.GP_STEP] = i;
                        gp += Aarch64BitVarArgs.GP_STEP;
                    } else {
                        if (remainingExpLength == expansionLength) {
                            // update the overflow area at the expansion start only
                            gp = 0; // Terminate the GP save area
                            overflowAreaArgOffsets[oi] = overflowArea;
                            overflowArgs[oi++] = args[ei]; // add the unexpanded arg

                            if (args[ei] instanceof LLVMVarArgCompoundValue) {
                                overflowArea += ((LLVMVarArgCompoundValue) args[ei]).getSize();
                            } else {
                                overflowArea += expansionLength * Aarch64BitVarArgs.STACK_STEP;
                            }
                        }
                    }
                } else if (area == VarArgArea.FP_AREA) {
                    if (fp + remainingExpLength * Aarch64BitVarArgs.FP_STEP <= 0) {
                        fpIdx[(Aarch64BitVarArgs.FP_LIMIT + fp) / Aarch64BitVarArgs.FP_STEP] = i;
                        fp += Aarch64BitVarArgs.FP_STEP;
                    } else {
                        if (remainingExpLength == expansionLength) {
                            // update the overflow area at the expansion start only
                            fp = 0; // Terminate the FP save area
                            overflowAreaArgOffsets[oi] = overflowArea;
                            overflowArgs[oi++] = args[ei]; // add the unexpanded arg

                            if (args[ei] instanceof LLVMVarArgCompoundValue) {
                                overflowArea += ((LLVMVarArgCompoundValue) args[ei]).getSize();
                            } else {
                                overflowArea += expansionLength * Aarch64BitVarArgs.STACK_STEP;
                            }
                        }
                    }
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
                    overflowAreaArgOffsets[oi] = overflowArea;
                    overflowArea += obj.getSize();
                    overflowArgs[oi++] = arg;
                } else {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    throw CompilerDirectives.shouldNotReachHere(String.valueOf(arg));
                }
            }

            // Clear the gpOffset/fpOffset in the case the FP/GP save area is empty
            if (gp == vaList.gpOffset) {
                vaList.gpOffset = 0;
            }
            if (fp == vaList.fpOffset) {
                vaList.fpOffset = 0;
            }

            vaList.gpSaveArea = new RegSaveArea(vaList.realArguments, gpIdx, Aarch64BitVarArgs.GP_STEP, Aarch64BitVarArgs.GP_LIMIT);
            vaList.gpSaveAreaPtr = LLVMManagedPointer.create(vaList.gpSaveArea);
            vaList.fpSaveArea = new RegSaveArea(vaList.realArguments, fpIdx, Aarch64BitVarArgs.FP_STEP, Aarch64BitVarArgs.FP_LIMIT);
            vaList.fpSaveAreaPtr = LLVMManagedPointer.create(vaList.fpSaveArea);
            vaList.overflowArgArea = new OverflowArgArea(overflowArgs, overflowAreaArgOffsets, overflowArea, oi);
            vaList.allocateNativeAreas(stackAllocationNode, frame);
        }

        @Specialization(guards = {"vaList.isNativized()"})
        static void initializeNativized(LLVMLinuxAarch64VaListStorage vaList, Object[] realArgs, int numOfExpArgs, Frame frame,
                        @Shared("stackAllocationNode") @Cached StackAllocationNode stackAllocationNode,
                        @Cached.Exclusive @Cached LLVMI64OffsetStoreNode i64RegSaveAreaStore,
                        @Cached.Exclusive @Cached LLVMI32OffsetStoreNode i32RegSaveAreaStore,
                        @Cached.Exclusive @Cached LLVM80BitFloatOffsetStoreNode fp80bitRegSaveAreaStore,
                        @Cached.Exclusive @Cached LLVMPointerOffsetStoreNode pointerRegSaveAreaStore,
                        @Cached.Exclusive @Cached LLVMI64OffsetStoreNode i64OverflowArgAreaStore,
                        @Cached.Exclusive @Cached LLVMI32OffsetStoreNode i32OverflowArgAreaStore,
                        @Cached.Exclusive @Cached LLVM80BitFloatOffsetStoreNode fp80bitOverflowArgAreaStore,
                        @Cached.Exclusive @Cached LLVMPointerOffsetStoreNode pointerOverflowArgAreaStore,
                        @Cached LLVMI32OffsetStoreNode gpOffsetStore,
                        @Cached LLVMI32OffsetStoreNode fpOffsetStore,
                        @Cached LLVMPointerOffsetStoreNode overflowArgAreaStore,
                        @Cached LLVMPointerOffsetStoreNode gpSaveAreaStore,
                        @Cached LLVMPointerOffsetStoreNode fpSaveAreaStore,
                        @Cached NativeProfiledMemMoveToNative memMove,
                        @Cached.Exclusive @Cached(parameters = "UNPACK_32BIT_PRIMITIVES_IN_STRUCTS") ArgumentListExpander argsExpander) {

            initializeManaged(vaList, realArgs, numOfExpArgs, frame, argsExpander, stackAllocationNode);

            if (!LLVMNativePointer.isInstance(vaList.gpSaveAreaNativePtr) || !LLVMNativePointer.isInstance(vaList.fpSaveAreaNativePtr) ||
                            !LLVMNativePointer.isInstance(vaList.overflowArgAreaBaseNativePtr)) {
                /*
                 * We don't have native memory pre-reserved on the stack. Maybe we have no access to
                 * native memory? In this case, the toNative transition fails.
                 */
                vaList.nativized = false;
                return;
            }

            initNativeVAList(gpOffsetStore, fpOffsetStore, overflowArgAreaStore, gpSaveAreaStore, fpSaveAreaStore, vaList.vaListStackPtr, vaList.gpOffset, vaList.fpOffset,
                            vaList.overflowArgAreaBaseNativePtr.increment(vaList.overflowArgArea.getOffset()), vaList.gpSaveAreaNativePtr, vaList.fpSaveAreaNativePtr);
            initNativeAreas(vaList.realArguments, vaList.originalArgs, vaList.expansions, vaList.numberOfExplicitArguments, vaList.gpOffset, vaList.fpOffset,
                            LLVMNativePointer.cast(vaList.gpSaveAreaNativePtr), LLVMNativePointer.cast(vaList.fpSaveAreaNativePtr), LLVMNativePointer.cast(vaList.overflowArgAreaBaseNativePtr),
                            i64RegSaveAreaStore, i32RegSaveAreaStore, fp80bitRegSaveAreaStore, pointerRegSaveAreaStore, i64OverflowArgAreaStore, i32OverflowArgAreaStore, fp80bitOverflowArgAreaStore,
                            pointerOverflowArgAreaStore, memMove);
            vaList.nativized = true;
        }

    }

    @SuppressWarnings("static-method")
    @ExportMessage
    @TruffleBoundary
    void toNative(@Cached LLVMI32OffsetStoreNode gpOffsetStore,
                    @Cached LLVMI32OffsetStoreNode fpOffsetStore,
                    @Cached LLVMPointerOffsetStoreNode overflowArgAreaStore,
                    @Cached LLVMPointerOffsetStoreNode gpSaveAreaStore,
                    @Cached LLVMPointerOffsetStoreNode fpSaveAreaStore,
                    @Cached LLVMI64OffsetStoreNode i64RegSaveAreaStore,
                    @Cached LLVMI32OffsetStoreNode i32RegSaveAreaStore,
                    @Cached LLVM80BitFloatOffsetStoreNode fp80bitRegSaveAreaStore,
                    @Cached LLVMPointerOffsetStoreNode pointerRegSaveAreaStore,
                    @Cached LLVMI64OffsetStoreNode i64OverflowArgAreaStore,
                    @Cached LLVMI32OffsetStoreNode i32OverflowArgAreaStore,
                    @Cached LLVM80BitFloatOffsetStoreNode fp80bitOverflowArgAreaStore,
                    @Cached LLVMPointerOffsetStoreNode pointerOverflowArgAreaStore,
                    @Cached NativeProfiledMemMoveToNative memMove,
                    @Cached BranchProfile nativizedProfile) {

        if (isNativized()) {
            nativizedProfile.enter();
            return;
        }

        this.nativized = true;

        if (overflowArgArea == null) {
            // toNative is called before the va_list is initialized by va_start. It happens in
            // situations like this:
            //
            // va_list va;
            // va_list *pva = &va;
            //
            // In this case we defer the va_list initialization until va_start is called.
            return;
        }

        if (!LLVMNativePointer.isInstance(gpSaveAreaNativePtr) || !LLVMNativePointer.isInstance(fpSaveAreaNativePtr) || !LLVMNativePointer.isInstance(overflowArgAreaBaseNativePtr)) {
            /*
             * We don't have native memory pre-reserved on the stack. Maybe we have no access to
             * native memory? In this case, the toNative transition fails.
             */
            this.nativized = false;
            return;
        }

        initNativeVAList(gpOffsetStore, fpOffsetStore, overflowArgAreaStore, gpSaveAreaStore, fpSaveAreaStore, vaListStackPtr, gpOffset, fpOffset,
                        overflowArgAreaBaseNativePtr.increment(overflowArgArea.getOffset()), gpSaveAreaNativePtr, fpSaveAreaNativePtr);

        initNativeAreas(this.realArguments, this.originalArgs, this.expansions, this.numberOfExplicitArguments, this.gpOffset, this.fpOffset, LLVMNativePointer.cast(gpSaveAreaNativePtr),
                        LLVMNativePointer.cast(fpSaveAreaNativePtr), LLVMNativePointer.cast(overflowArgAreaBaseNativePtr), i64RegSaveAreaStore, i32RegSaveAreaStore, fp80bitRegSaveAreaStore,
                        pointerRegSaveAreaStore, i64OverflowArgAreaStore, i32OverflowArgAreaStore, fp80bitOverflowArgAreaStore, pointerOverflowArgAreaStore, memMove);
    }

    private void allocateNativeAreas(StackAllocationNode stackAllocationNode, Frame frame) {
        this.overflowArgAreaBaseNativePtr = overflowArgArea == null ? null : stackAllocationNode.executeWithTarget(overflowArgArea.overflowAreaSize, frame);
        this.gpSaveAreaNativePtr = stackAllocationNode.executeWithTarget(Aarch64BitVarArgs.GP_LIMIT, frame);
        gpSaveAreaNativePtr = gpSaveAreaNativePtr.increment(Aarch64BitVarArgs.GP_LIMIT);
        this.fpSaveAreaNativePtr = stackAllocationNode.executeWithTarget(Aarch64BitVarArgs.FP_LIMIT, frame);
        fpSaveAreaNativePtr = fpSaveAreaNativePtr.increment(Aarch64BitVarArgs.FP_LIMIT);
    }

    private static void initNativeVAList(LLVMI32OffsetStoreNode gpOffsetStore, LLVMI32OffsetStoreNode fpOffsetStore, LLVMPointerOffsetStoreNode overflowArgAreaStore,
                    LLVMPointerOffsetStoreNode gpSaveAreaStore, LLVMPointerOffsetStoreNode fpSaveAreaStore, LLVMPointer vaListStackPtr, int gpOffset, int fpOffset,
                    LLVMPointer overflowArgAreaNativePtr, LLVMPointer gpSaveAreaNativePtr, LLVMPointer fpSaveAreaNativePtr) {
        gpOffsetStore.executeWithTarget(vaListStackPtr, Aarch64BitVarArgs.GP_OFFSET, gpOffset);
        fpOffsetStore.executeWithTarget(vaListStackPtr, Aarch64BitVarArgs.FP_OFFSET, fpOffset);

        overflowArgAreaStore.executeWithTarget(vaListStackPtr, Aarch64BitVarArgs.OVERFLOW_ARG_AREA, overflowArgAreaNativePtr);
        gpSaveAreaStore.executeWithTarget(vaListStackPtr, Aarch64BitVarArgs.GP_SAVE_AREA, gpSaveAreaNativePtr);
        fpSaveAreaStore.executeWithTarget(vaListStackPtr, Aarch64BitVarArgs.FP_SAVE_AREA, fpSaveAreaNativePtr);
    }

    /**
     * Reconstruct the native areas according to Aarch64 ABI.
     */
    private static void initNativeAreas(Object[] realArguments, Object[] originalArguments, Object[][] expansions, int numberOfExplicitArguments, int initGPOffset, int initFPOffset,
                    LLVMNativePointer gpSaveAreaNativePtr, LLVMNativePointer fpSaveAreaNativePtr, LLVMNativePointer overflowArgAreaBaseNativePtr,
                    LLVMI64OffsetStoreNode i64RegSaveAreaStore,
                    LLVMI32OffsetStoreNode i32RegSaveAreaStore,
                    LLVM80BitFloatOffsetStoreNode fp80bitRegSaveAreaStore,
                    LLVMPointerOffsetStoreNode pointerRegSaveAreaStore,
                    LLVMI64OffsetStoreNode i64OverflowArgAreaStore,
                    LLVMI32OffsetStoreNode i32OverflowArgAreaStore,
                    LLVM80BitFloatOffsetStoreNode fp80bitOverflowArgAreaStore,
                    LLVMPointerOffsetStoreNode pointerOverflowArgAreaStore,
                    NativeProfiledMemMoveToNative memMove) {
        int gp = initGPOffset;
        int fp = initFPOffset;

        final int vaLength = realArguments.length - numberOfExplicitArguments;
        if (vaLength > 0) {
            long overflowOffset = 0;

            int ei = -1;
            int expansionStart = 0;
            int expansionLength = 0;
            int remainingExpLength = 0;

            // TODO (chaeubl): this generates pretty bad machine code as we don't know anything
            // about the arguments
            for (int i = 0; i < realArguments.length; i++) {
                final Object object = realArguments[i];

                if (i >= expansionStart + expansionLength) {
                    ei++;
                    expansionStart += expansionLength;
                    expansionLength = expansions == null || expansions[ei] == null ? 1 : expansions[ei].length;
                    remainingExpLength = expansionLength;
                } else {
                    remainingExpLength--;
                }

                if (i < numberOfExplicitArguments) {
                    continue;
                }

                final VarArgArea area = getVarArgArea(object);

                if (area == VarArgArea.GP_AREA) {
                    if (gp + remainingExpLength * Aarch64BitVarArgs.GP_STEP <= 0) {
                        storeArgument(gpSaveAreaNativePtr, gp, memMove, i64RegSaveAreaStore, i32RegSaveAreaStore, fp80bitRegSaveAreaStore, pointerRegSaveAreaStore, object,
                                        Aarch64BitVarArgs.STACK_STEP);
                        gp += Aarch64BitVarArgs.GP_STEP;
                    } else {
                        if (remainingExpLength == expansionLength) {
                            // update the overflow area at the expansion start only
                            gp = 0;
                            storeArgument(overflowArgAreaBaseNativePtr, overflowOffset, memMove,
                                            i64OverflowArgAreaStore, i32OverflowArgAreaStore,
                                            fp80bitOverflowArgAreaStore, pointerOverflowArgAreaStore, originalArguments[ei], Aarch64BitVarArgs.STACK_STEP);

                            if (originalArguments[ei] instanceof LLVMVarArgCompoundValue) {
                                overflowOffset += ((LLVMVarArgCompoundValue) originalArguments[ei]).getSize();
                            } else {
                                overflowOffset += expansionLength * Aarch64BitVarArgs.STACK_STEP;
                            }
                        }
                    }
                } else if (area == VarArgArea.FP_AREA) {
                    if (fp + remainingExpLength * Aarch64BitVarArgs.FP_STEP <= 0) {
                        storeArgument(fpSaveAreaNativePtr, fp, memMove, i64RegSaveAreaStore, i32RegSaveAreaStore, fp80bitRegSaveAreaStore, pointerRegSaveAreaStore, object,
                                        Aarch64BitVarArgs.STACK_STEP);
                        fp += Aarch64BitVarArgs.FP_STEP;
                    } else {
                        if (remainingExpLength == expansionLength) {
                            // update the overflow area at the expansion start only
                            fp = 0;
                            storeArgument(overflowArgAreaBaseNativePtr, overflowOffset, memMove,
                                            i64OverflowArgAreaStore, i32OverflowArgAreaStore,
                                            fp80bitOverflowArgAreaStore, pointerOverflowArgAreaStore, originalArguments[ei], Aarch64BitVarArgs.STACK_STEP);

                            if (originalArguments[ei] instanceof LLVMVarArgCompoundValue) {
                                overflowOffset += ((LLVMVarArgCompoundValue) originalArguments[ei]).getSize();
                            } else {
                                overflowOffset += expansionLength * Aarch64BitVarArgs.STACK_STEP;
                            }
                        }
                    }
                } else if (object instanceof LLVMVarArgCompoundValue) {
                    overflowOffset += storeArgument(overflowArgAreaBaseNativePtr, overflowOffset, memMove,
                                    i64OverflowArgAreaStore, i32OverflowArgAreaStore,
                                    fp80bitOverflowArgAreaStore, pointerOverflowArgAreaStore, object, Aarch64BitVarArgs.STACK_STEP);
                }
            }
        }
    }

    @ExportMessage
    public void cleanup(@SuppressWarnings("unused") Frame frame) {

    }

    @ExportMessage
    static class Copy {

        @Specialization(guards = {"!source.isNativized()"})
        static void copyManaged(LLVMLinuxAarch64VaListStorage source, LLVMLinuxAarch64VaListStorage dest, Frame frame,
                        @Shared("stackAllocationNode") @Cached StackAllocationNode stackAllocationNode) {
            dest.realArguments = source.realArguments;
            dest.originalArgs = source.originalArgs;
            dest.expansions = source.expansions;
            dest.numberOfExplicitArguments = source.numberOfExplicitArguments;
            dest.fpOffset = source.fpOffset;
            dest.gpOffset = source.gpOffset;
            dest.gpSaveArea = source.gpSaveArea;
            dest.gpSaveAreaPtr = source.gpSaveAreaPtr;
            dest.fpSaveArea = source.fpSaveArea;
            dest.fpSaveAreaPtr = source.fpSaveAreaPtr;
            dest.overflowArgArea = source.overflowArgArea == null ? null : source.overflowArgArea.clone();

            dest.allocateNativeAreas(stackAllocationNode, frame);
        }

        @Specialization(guards = {"source.isNativized()", "source.overflowArgArea != null"})
        static void copyNativeToManaged(LLVMLinuxAarch64VaListStorage source, LLVMLinuxAarch64VaListStorage dest, Frame frame,
                        @CachedLibrary(limit = "1") LLVMManagedReadLibrary srcReadLib,
                        @CachedLibrary(limit = "1") LLVMManagedWriteLibrary writeLib,
                        @Shared("stackAllocationNode") @Cached StackAllocationNode stackAllocationNode) {
            // The destination va_list will be in the managed state, even if the source has been
            // nativized. We need to read some state from the native memory, though.

            copyManaged(source, dest, frame, stackAllocationNode);

            dest.fpOffset = srcReadLib.readI32(source, Aarch64BitVarArgs.FP_OFFSET);
            dest.gpOffset = srcReadLib.readI32(source, Aarch64BitVarArgs.GP_OFFSET);

            LLVMPointer overflowArgArea = srcReadLib.readPointer(source, Aarch64BitVarArgs.OVERFLOW_ARG_AREA);
            LLVMPointer regFpSaveArea = srcReadLib.readPointer(source, Aarch64BitVarArgs.FP_SAVE_AREA);
            LLVMPointer regGpSaveArea = srcReadLib.readPointer(source, Aarch64BitVarArgs.GP_SAVE_AREA);
            dest.nativized = true; // otherwise the following writes will fail:
            writeLib.writeI32(dest, Aarch64BitVarArgs.FP_OFFSET, dest.fpOffset);
            writeLib.writeI32(dest, Aarch64BitVarArgs.GP_OFFSET, dest.gpOffset);
            writeLib.writePointer(dest, Aarch64BitVarArgs.OVERFLOW_ARG_AREA, overflowArgArea);
            writeLib.writePointer(dest, Aarch64BitVarArgs.FP_SAVE_AREA, regFpSaveArea);
            writeLib.writePointer(dest, Aarch64BitVarArgs.GP_SAVE_AREA, regGpSaveArea);
            if (dest.overflowArgArea != null) {
                dest.overflowArgArea.setOffset(getArgPtrFromNativePtr(source, srcReadLib));
            }
        }

        @Specialization(guards = {"source.isNativized()", "source.overflowArgArea == null"})
        @GenerateAOT.Exclude // recursion cut
        static void copyNative(LLVMLinuxAarch64VaListStorage source, LLVMLinuxAarch64VaListStorage dest, Frame frame,
                        @Cached VAListPointerWrapperFactoryDelegate wrapperFactory,
                        @CachedLibrary(limit = "2") LLVMVaListLibrary vaListLibrary) {
            // The source valist is just a holder of the native counterpart and thus the destination
            // will not be set up as a managed va_list as it would be too complicated to restore the
            // managed state from the native one.
            vaListLibrary.copy(wrapperFactory.execute(source.vaListStackPtr), dest, frame);
        }

        @Specialization
        @GenerateAOT.Exclude // recursion cut
        static void copyManagedToNative(LLVMLinuxAarch64VaListStorage source, NativeVAListWrapper dest, Frame frame,
                        @CachedLibrary(limit = "2") LLVMVaListLibrary vaListLibrary) {
            LLVMLinuxAarch64VaListStorage dummyClone = new LLVMLinuxAarch64VaListStorage(dest.nativeVAListPtr, source.vaListType);
            dummyClone.nativized = true;
            vaListLibrary.initialize(dummyClone, source.realArguments, source.numberOfExplicitArguments, frame);
        }
    }

    /**
     * Calculate the number of argument shifts in the overflow area.
     *
     * @param srcVaList
     * @param readLib
     */
    private static long getArgPtrFromNativePtr(LLVMLinuxAarch64VaListStorage srcVaList, LLVMManagedReadLibrary readLib) {
        long curAddr;
        long baseAddr;
        LLVMPointer overflowAreaPtr = readLib.readPointer(srcVaList, Aarch64BitVarArgs.OVERFLOW_ARG_AREA);
        if (LLVMNativePointer.isInstance(overflowAreaPtr)) {
            curAddr = LLVMNativePointer.cast(overflowAreaPtr).asNative();
        } else {
            curAddr = LLVMManagedPointer.cast(overflowAreaPtr).getOffset();
        }
        if (LLVMNativePointer.isInstance(srcVaList.overflowArgAreaBaseNativePtr)) {
            baseAddr = LLVMNativePointer.cast(srcVaList.overflowArgAreaBaseNativePtr).asNative();
        } else {
            baseAddr = LLVMManagedPointer.cast(srcVaList.overflowArgAreaBaseNativePtr).getOffset();
        }
        return curAddr - baseAddr;
    }

    /**
     * This is the implementation of the {@code va_arg} instruction.
     */
    @SuppressWarnings("static-method")
    @ExportMessage
    Object shift(Type type, @SuppressWarnings("unused") Frame frame,
                    @CachedLibrary(limit = "1") LLVMManagedReadLibrary readLib,
                    @CachedLibrary(limit = "1") LLVMManagedWriteLibrary writeLib,
                    @Cached BranchProfile regAreaProfile,
                    @Cached LoadFromAreaNode loadFromArea,
                    @Cached ConditionProfile isNativizedProfile) {
        int regSaveAreaOffs = 0;
        int regSaveOffs = 0;
        int regSaveStep = 0;
        boolean lookIntoRegSaveArea = true;

        VarArgArea varArgArea = getVarArgArea(type);
        RegSaveArea regSaveArea = null;
        switch (varArgArea) {
            case GP_AREA:
                regSaveAreaOffs = Aarch64BitVarArgs.GP_SAVE_AREA;
                regSaveOffs = Aarch64BitVarArgs.GP_OFFSET;
                regSaveStep = Aarch64BitVarArgs.GP_STEP;
                regSaveArea = gpSaveArea;
                break;

            case FP_AREA:
                regSaveAreaOffs = Aarch64BitVarArgs.FP_SAVE_AREA;
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
                if (regSaveArea != null) {
                    long n = regSaveArea.offsetToIndex(offs);
                    if (n >= 0) {
                        int i = (int) ((n << 32) >> 32);
                        return regSaveArea.args[i];
                    }
                } else {
                    return loadFromArea.execute(this.vaListStackPtr, regSaveAreaOffs, offs, 0, type);
                }
            }
        }

        // overflow area
        if (isNativizedProfile.profile(isNativized())) {
            assert regSaveStep == 8;
            return loadFromArea.execute(vaListStackPtr, Aarch64BitVarArgs.OVERFLOW_ARG_AREA, 0, 8, type);
        } else {
            Object currentArg = this.overflowArgArea.getCurrentArg();
            this.overflowArgArea.shift(1);
            return currentArg;
        }
    }

    @GenerateUncached
    public abstract static class Aarch64VAListPointerWrapperFactory extends VAListPointerWrapperFactory {

        public abstract Object execute(Object pointer);

        @Specialization
        protected Object createNativeWrapper(LLVMNativePointer p) {
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
            return o instanceof LLVMLinuxAarch64VaListStorage;
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

        public NativeVAListWrapper(LLVMPointer nativeVAListPtr) {
            this.nativeVAListPtr = nativeVAListPtr;
        }

        @ExportMessage
        public void initialize(Object[] originalArgs, int numberOfExplicitArguments, Frame frame,
                        @Cached.Exclusive @Cached StackAllocationNode stackAllocationNode,
                        @Shared("gpOffsetStore") @Cached LLVMI32OffsetStoreNode gpOffsetStore,
                        @Shared("fpOffsetStore") @Cached LLVMI32OffsetStoreNode fpOffsetStore,
                        @Cached.Exclusive @Cached LLVMI64OffsetStoreNode i64RegSaveAreaStore,
                        @Cached.Exclusive @Cached LLVMI32OffsetStoreNode i32RegSaveAreaStore,
                        @Cached.Exclusive @Cached LLVM80BitFloatOffsetStoreNode fp80bitRegSaveAreaStore,
                        @Cached.Exclusive @Cached LLVMPointerOffsetStoreNode pointerRegSaveAreaStore,
                        @Cached.Exclusive @Cached LLVMI64OffsetStoreNode i64OverflowArgAreaStore,
                        @Cached.Exclusive @Cached LLVMI32OffsetStoreNode i32OverflowArgAreaStore,
                        @Cached.Exclusive @Cached LLVM80BitFloatOffsetStoreNode fp80bitOverflowArgAreaStore,
                        @Cached.Exclusive @Cached LLVMPointerOffsetStoreNode pointerOverflowArgAreaStore,
                        @Shared("overflowAreaStore") @Cached LLVMPointerOffsetStoreNode overflowArgAreaStore,
                        @Shared("gpSaveAreaStore") @Cached LLVMPointerOffsetStoreNode gpSaveAreaStore,
                        @Shared("fpSaveAreaStore") @Cached LLVMPointerOffsetStoreNode fpSaveAreaStore,
                        @Cached NativeProfiledMemMoveToNative memMove,
                        @Cached(parameters = "UNPACK_32BIT_PRIMITIVES_IN_STRUCTS") ArgumentListExpander argsExpander) {

            Object[][][] expansionsOutArg = new Object[1][][];

            Object[] realArguments = argsExpander.expand(originalArgs, expansionsOutArg);
            Object[][] expansions = expansionsOutArg[0];

            int gpNamedUsage = calculateUsedGpArea(realArguments, numberOfExplicitArguments);
            int initGPOffset = gpNamedUsage - Aarch64BitVarArgs.GP_LIMIT;
            int gp = initGPOffset;
            int fpNamedUsage = calculateUsedFpArea(realArguments, numberOfExplicitArguments);
            int initFPOffset = fpNamedUsage - Aarch64BitVarArgs.FP_LIMIT;
            int fp = initFPOffset;

            int ei = -1;
            int expansionStart = 0;
            int expansionLength = 0;
            int remainingExpLength = 0;

            long overflowArea = 0;
            for (int i = 0; i < realArguments.length; i++) {
                final Object arg = realArguments[i];

                if (i >= expansionStart + expansionLength) {
                    ei++;
                    expansionStart += expansionLength;
                    expansionLength = expansions == null || expansions[ei] == null ? 1 : expansions[ei].length;
                    remainingExpLength = expansionLength;
                } else {
                    remainingExpLength--;
                }

                if (i < numberOfExplicitArguments) {
                    continue;
                }

                final VarArgArea area = getVarArgArea(arg);

                if (area == VarArgArea.GP_AREA) {
                    if (gp + remainingExpLength * Aarch64BitVarArgs.GP_STEP <= 0) {
                        gp += Aarch64BitVarArgs.GP_STEP;
                    } else {
                        gp = 0; // Terminate the GP save area
                        if (remainingExpLength == expansionLength) {
                            if (originalArgs[ei] instanceof LLVMVarArgCompoundValue) {
                                overflowArea += ((LLVMVarArgCompoundValue) originalArgs[ei]).getSize();
                            } else {
                                overflowArea += expansionLength * Aarch64BitVarArgs.STACK_STEP;
                            }
                        }
                    }
                } else if (area == VarArgArea.FP_AREA) {
                    if (fp + remainingExpLength * Aarch64BitVarArgs.FP_STEP <= 0) {
                        fp += Aarch64BitVarArgs.FP_STEP;
                    } else {
                        fp = 0; // Terminate the FP save area
                        if (remainingExpLength == expansionLength) {
                            if (originalArgs[ei] instanceof LLVMVarArgCompoundValue) {
                                overflowArea += ((LLVMVarArgCompoundValue) originalArgs[ei]).getSize();
                            } else {
                                overflowArea += expansionLength * Aarch64BitVarArgs.STACK_STEP;
                            }
                        }
                    }
                } else if (arg instanceof LLVM80BitFloat) {
                    overflowArea += 16;
                } else if (arg instanceof LLVMVarArgCompoundValue) {
                    LLVMVarArgCompoundValue obj = (LLVMVarArgCompoundValue) arg;
                    overflowArea += obj.getSize();
                } else {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    throw CompilerDirectives.shouldNotReachHere(String.valueOf(arg));
                }
            }

            LLVMNativePointer gpSaveAreaNativePtr = LLVMNativePointer.cast(stackAllocationNode.executeWithTarget(Aarch64BitVarArgs.GP_LIMIT, frame));
            // GP area pointer points to the top
            gpSaveAreaNativePtr = gpSaveAreaNativePtr.increment(Aarch64BitVarArgs.GP_LIMIT);

            LLVMNativePointer fpSaveAreaNativePtr = LLVMNativePointer.cast(stackAllocationNode.executeWithTarget(Aarch64BitVarArgs.FP_LIMIT, frame));
            // FP area pointer points to the top
            fpSaveAreaNativePtr = fpSaveAreaNativePtr.increment(Aarch64BitVarArgs.FP_LIMIT);

            LLVMNativePointer overflowArgAreaBaseNativePtr = LLVMNativePointer.cast(stackAllocationNode.executeWithTarget(overflowArea, frame));

            initNativeVAList(gpOffsetStore, fpOffsetStore, overflowArgAreaStore, gpSaveAreaStore, fpSaveAreaStore, nativeVAListPtr, initGPOffset, initFPOffset, overflowArgAreaBaseNativePtr,
                            gpSaveAreaNativePtr, fpSaveAreaNativePtr);
            initNativeAreas(realArguments, originalArgs, expansions, numberOfExplicitArguments, initGPOffset, initFPOffset, gpSaveAreaNativePtr, fpSaveAreaNativePtr, overflowArgAreaBaseNativePtr,
                            i64RegSaveAreaStore, i32RegSaveAreaStore, fp80bitRegSaveAreaStore, pointerRegSaveAreaStore, i64OverflowArgAreaStore, i32OverflowArgAreaStore, fp80bitOverflowArgAreaStore,
                            pointerOverflowArgAreaStore, memMove);
        }

        @ExportMessage
        public void cleanup(@SuppressWarnings("unused") Frame frame) {
            // nop
        }

        @ExportMessage
        @ImportStatic({LLVMTypes.class, LLVMLinuxAarch64VaListStorage.class})
        static class Copy {

            @Specialization
            @GenerateAOT.Exclude // recursion cut
            static void copyToManagedObject(NativeVAListWrapper source, LLVMLinuxAarch64VaListStorage dest, Frame frame,
                            @CachedLibrary("source") LLVMVaListLibrary vaListLibrary) {
                vaListLibrary.copy(source, new NativeVAListWrapper(dest.vaListStackPtr), frame);
                dest.nativized = true;
            }

            @Specialization
            static void copyToNative(NativeVAListWrapper source, NativeVAListWrapper dest, @SuppressWarnings("unused") Frame frame,
                            @Shared("gpOffsetStore") @Cached LLVMI32OffsetStoreNode gpOffsetStore,
                            @Shared("fpOffsetStore") @Cached LLVMI32OffsetStoreNode fpOffsetStore,
                            @Shared("gpSaveAreaStore") @Cached LLVMPointerOffsetStoreNode gpSaveAreaStore,
                            @Shared("fpSaveAreaStore") @Cached LLVMPointerOffsetStoreNode fpSaveAreaStore,
                            @Shared("overflowAreaStore") @Cached LLVMPointerOffsetStoreNode overflowAreaStore,
                            @Cached.Exclusive @Cached LLVMI32OffsetLoadNode gpOffsetLoad,
                            @Cached.Exclusive @Cached LLVMI32OffsetLoadNode fpOffsetLoad,
                            @Cached.Exclusive @Cached LLVMPointerOffsetLoadNode gpSaveAreaLoad,
                            @Cached.Exclusive @Cached LLVMPointerOffsetLoadNode fpSaveAreaLoad,
                            @Cached.Exclusive @Cached LLVMPointerOffsetLoadNode overflowAreaLoad) {

                // read fields from the source native va_list
                int gp = gpOffsetLoad.executeWithTarget(source.nativeVAListPtr, Aarch64BitVarArgs.GP_OFFSET);
                int fp = fpOffsetLoad.executeWithTarget(source.nativeVAListPtr, Aarch64BitVarArgs.FP_OFFSET);
                LLVMPointer gpSaveAreaPtr = gpSaveAreaLoad.executeWithTarget(source.nativeVAListPtr, Aarch64BitVarArgs.GP_SAVE_AREA);
                LLVMPointer fpSaveAreaPtr = fpSaveAreaLoad.executeWithTarget(source.nativeVAListPtr, Aarch64BitVarArgs.FP_SAVE_AREA);
                LLVMPointer overflowSaveAreaPtr = overflowAreaLoad.executeWithTarget(source.nativeVAListPtr, Aarch64BitVarArgs.OVERFLOW_ARG_AREA);

                // write fields to the destination native va_list
                initNativeVAList(gpOffsetStore, fpOffsetStore, gpSaveAreaStore, fpSaveAreaStore, overflowAreaStore, dest.nativeVAListPtr, gp, fp, overflowSaveAreaPtr, gpSaveAreaPtr, fpSaveAreaPtr);
            }
        }

        @SuppressWarnings("static-method")
        @ExportMessage
        public Object shift(@SuppressWarnings("unused") Type type, @SuppressWarnings("unused") Frame frame) {
            throw CompilerDirectives.shouldNotReachHere("TODO");
        }
    }

    public static final class RegSaveArea extends ArgsArea {

        private final int[] idx;
        private final int step;
        private final int limit;

        RegSaveArea(Object[] args, int[] idx, int step, int limit) {
            super(args);
            this.idx = idx;
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

    }

    @ExportLibrary(InteropLibrary.class)
    public static final class OverflowArgArea extends AbstractOverflowArgArea {

        final int argsCnt;

        OverflowArgArea(Object[] args, long[] offsets, long overflowAreaSize, int argsCnt) {
            super(args, offsets, overflowAreaSize);
            this.argsCnt = argsCnt;
        }

        @Override
        public OverflowArgArea clone() {
            OverflowArgArea cloned = new OverflowArgArea(args, offsets, overflowAreaSize, argsCnt);
            cloned.currentOffset = currentOffset;
            cloned.previousOffset = previousOffset;
            return cloned;
        }

        // InteropLibrary

        @ExportMessage
        public boolean isPointer() {
            Object prevArg = getPreviousArg();
            if (prevArg instanceof LLVMVarArgCompoundValue) {
                return LLVMNativePointer.isInstance(((LLVMVarArgCompoundValue) prevArg).getAddr());
            }
            return false;
        }

        @ExportMessage
        public long asPointer() throws UnsupportedMessageException {
            Object prevArg = getPreviousArg();
            assert prevArg != null && previousOffset >= 0;

            if (prevArg instanceof LLVMVarArgCompoundValue) {
                return LLVMNativePointer.cast(((LLVMVarArgCompoundValue) prevArg).getAddr()).increment(-previousOffset).asNative();
            }
            throw UnsupportedMessageException.create();
        }

    }

}
