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
package com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.aarch64;

import java.util.ArrayList;
import java.util.Arrays;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.CachedLanguage;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.FrameInstance.FrameAccess;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
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
import com.oracle.truffle.llvm.runtime.library.internal.LLVMCopyTargetLibrary;
import com.oracle.truffle.llvm.runtime.library.internal.LLVMManagedReadLibrary;
import com.oracle.truffle.llvm.runtime.library.internal.LLVMManagedWriteLibrary;
import com.oracle.truffle.llvm.runtime.memory.LLVMMemMoveNode;
import com.oracle.truffle.llvm.runtime.memory.LLVMStack.LLVMGetStackSpaceInstruction;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMTypes;
import com.oracle.truffle.llvm.runtime.nodes.func.LLVMRootNode;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.aarch64.LLVMAarch64VaListStorageFactory.ArgumentListExpanderFactory.ArgumentExpanderNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.va.LLVMVAEnd;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.va.LLVMVAStart;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.va.LLVMVaListLibrary;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.va.LLVMVaListStorage;
import com.oracle.truffle.llvm.runtime.nodes.memory.NativeProfiledMemMove;
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
import com.oracle.truffle.llvm.runtime.types.Type.TypeOverflowException;
import com.oracle.truffle.llvm.spi.NativeTypeLibrary;

@ExportLibrary(LLVMManagedReadLibrary.class)
@ExportLibrary(LLVMManagedWriteLibrary.class)
@ExportLibrary(LLVMVaListLibrary.class)
@ExportLibrary(NativeTypeLibrary.class)
@ExportLibrary(InteropLibrary.class)
@ExportLibrary(LLVMCopyTargetLibrary.class)
public class LLVMAarch64VaListStorage extends LLVMVaListStorage {

    // %struct.__va_list = type { i8*, i8*, i8*, i32, i32 }

    public static final StructureType VA_LIST_TYPE = StructureType.createNamedFromList("struct.__va_list", false,
                    new ArrayList<>(Arrays.asList(PointerType.I8, PointerType.I8, PointerType.I8, PrimitiveType.I32, PrimitiveType.I32)));

    private final LLVMRootNode rootNode;

    private Object[] originalArgs;
    private Object[][] expansions;

    private int gpOffset;
    private int fpOffset;

    private LLVMPointer overflowArgAreaBaseNativePtr;

    private RegSaveArea gpSaveArea;
    private LLVMPointer gpSaveAreaPtr;

    private RegSaveArea fpSaveArea;
    private LLVMPointer fpSaveAreaPtr;

    protected OverflowArgArea overflowArgArea;

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

    // LLVMCopyTargetLibrary

    @ExportMessage
    public boolean canCopyFrom(Object source, @SuppressWarnings("unused") long length) {
        /*
         * I do not test if the length is the size of va_list as I need that the execution proceed
         * to copyFrom. I think that an exception should not be thrown here in this idempotent
         * method, but in the actual attempt to make a copy.
         */
        return source instanceof LLVMAarch64VaListStorage || LLVMNativePointer.isInstance(source);
    }

    @ExportMessage
    public void copyFrom(Object source, @SuppressWarnings("unused") long length,
                    @Cached VAListPointerWrapperFactoryDelegate wrapperFactory,
                    @CachedLibrary(limit = "1") LLVMVaListLibrary vaListLibrary,
                    @Cached BranchProfile invalidLengthProfile) {
        if (length != Aarch64BitVarArgs.SIZE_OF_VALIST) {
            invalidLengthProfile.enter();
            CompilerDirectives.transferToInterpreter();
            throw new UnsupportedOperationException("Invalid length " + length + ". Expected length " + Aarch64BitVarArgs.SIZE_OF_VALIST);
        }
        vaListLibrary.copy(wrapperFactory.execute(source), this);
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
    Object getNativeType(@CachedLanguage LLVMLanguage language) {
        // This method should never be invoked
        return language.getInteropType(LLVMSourceTypeFactory.resolveType(VA_LIST_TYPE, getDataLayout()));
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
        static int readNativeI32(LLVMAarch64VaListStorage vaList, long offset,
                        @Cached LLVMI32LoadNode.LLVMI32OffsetLoadNode offsetLoad) {
            return offsetLoad.executeWithTarget(vaList.nativized, offset);
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
                        @Cached LLVMPointerLoadNode.LLVMPointerOffsetLoadNode offsetLoad) {
            return offsetLoad.executeWithTarget(vaList.nativized, offset);
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
                    break;
                case Aarch64BitVarArgs.FP_OFFSET:
                    vaList.fpOffset = value;
                    break;
                default:
                    CompilerDirectives.transferToInterpreter();
                    throw new UnsupportedOperationException("Should not get here");
            }
        }

        @Specialization(guards = "vaList.isNativized()")
        static void writeNative(LLVMAarch64VaListStorage vaList, long offset, int value,
                        @Cached LLVMI32OffsetStoreNode offsetStore) {
            offsetStore.executeWithTarget(vaList.nativized, offset, value);
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
                    /*
                     * Assume that updating the overflowArea pointer means shifting the current
                     * argument, according to abi
                     */
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
                        @Cached LLVMPointerStoreNode.LLVMPointerOffsetStoreNode offsetStore) {
            offsetStore.executeWithTarget(vaList.nativized, offset, value);
        }
    }

    // LLVMVaListLibrary implementation

    @ExportMessage
    static class Initialize {

        @Specialization(guards = {"!vaList.isNativized()"})
        static void initializeManaged(LLVMAarch64VaListStorage vaList, Object[] args, int numOfExpArgs,
                        @Shared("expander") @Cached ArgumentListExpander argsExpander) {
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
            int overflowArea = 0;

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

            vaList.gpSaveArea = new RegSaveArea(vaList.realArguments, gpIdx, Aarch64BitVarArgs.GP_STEP, Aarch64BitVarArgs.GP_LIMIT);
            vaList.gpSaveAreaPtr = LLVMManagedPointer.create(vaList.gpSaveArea);
            vaList.fpSaveArea = new RegSaveArea(vaList.realArguments, fpIdx, Aarch64BitVarArgs.FP_STEP, Aarch64BitVarArgs.FP_LIMIT);
            vaList.fpSaveAreaPtr = LLVMManagedPointer.create(vaList.fpSaveArea);
            vaList.overflowArgArea = new OverflowArgArea(overflowArgs, overflowAreaArgOffsets, overflowArea, oi);
        }

        @Specialization(guards = {"vaList.isNativized()"})
        static void initializeNativized(LLVMAarch64VaListStorage vaList, Object[] realArgs, int numOfExpArgs,
                        @Cached StackAllocationNode stackAllocationNode,
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
                        @Shared("expander") @Cached ArgumentListExpander argsExpander) {
            initializeManaged(vaList, realArgs, numOfExpArgs, argsExpander);

            LLVMPointer[] regSaveAreaNativePtrs = vaList.allocateNativeAreas(stackAllocationNode, gpOffsetStore, fpOffsetStore, overflowArgAreaStore, gpSaveAreaStore, fpSaveAreaStore);

            initNativeAreas(vaList.realArguments, vaList.originalArgs, vaList.expansions, vaList.numberOfExplicitArguments, vaList.gpOffset, vaList.fpOffset, regSaveAreaNativePtrs[0],
                            regSaveAreaNativePtrs[1], vaList.overflowArgAreaBaseNativePtr, i64RegSaveAreaStore, i32RegSaveAreaStore, fp80bitRegSaveAreaStore, pointerRegSaveAreaStore,
                            i64OverflowArgAreaStore, i32OverflowArgAreaStore, fp80bitOverflowArgAreaStore, pointerOverflowArgAreaStore, memMove);
        }

    }

    static long getVAListTypeSize() {
        try {
            return VA_LIST_TYPE.getSize(getDataLayout());
        } catch (TypeOverflowException e) {
            CompilerDirectives.transferToInterpreter();
            throw new UnsupportedOperationException("Should not get here");
        }
    }

    @SuppressWarnings("static-method")
    LLVMExpressionNode createAllocaNode(LLVMLanguage language) {
        DataLayout dataLayout = getDataLayout();
        return language.getActiveConfiguration().createNodeFactory(language, dataLayout).createAlloca(VA_LIST_TYPE, 16);
    }

    LLVMExpressionNode createAllocaNodeUncached(LLVMLanguage language) {
        DataLayout dataLayout = getDataLayout();
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
                    @Cached StackAllocationNode stackAllocationNode,
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

        LLVMPointer[] regSaveAreaNativePtrs = allocateNativeAreas(stackAllocationNode, gpOffsetStore, fpOffsetStore, overflowArgAreaStore, gpSaveAreaStore, fpSaveAreaStore);

        initNativeAreas(this.realArguments, this.originalArgs, this.expansions, this.numberOfExplicitArguments, this.gpOffset, this.fpOffset, regSaveAreaNativePtrs[0], regSaveAreaNativePtrs[1],
                        this.overflowArgAreaBaseNativePtr, i64RegSaveAreaStore, i32RegSaveAreaStore, fp80bitRegSaveAreaStore, pointerRegSaveAreaStore, i64OverflowArgAreaStore, i32OverflowArgAreaStore,
                        fp80bitOverflowArgAreaStore, pointerOverflowArgAreaStore, memMove);
    }

    private LLVMPointer[] allocateNativeAreas(StackAllocationNode stackAllocationNode, LLVMI32OffsetStoreNode gpOffsetStore, LLVMI32OffsetStoreNode fpOffsetStore,
                    LLVMPointerOffsetStoreNode overflowArgAreaStore, LLVMPointerOffsetStoreNode gpSaveAreaStore, LLVMPointerOffsetStoreNode fpSaveAreaStore) {
        this.overflowArgAreaBaseNativePtr = stackAllocationNode.executeWithTarget(overflowArgArea.overflowAreaSize);
        LLVMPointer gpSaveAreaNativePtr = stackAllocationNode.executeWithTarget(Aarch64BitVarArgs.GP_LIMIT);
        gpSaveAreaNativePtr = gpSaveAreaNativePtr.increment(Aarch64BitVarArgs.GP_LIMIT);
        LLVMPointer fpSaveAreaNativePtr = stackAllocationNode.executeWithTarget(Aarch64BitVarArgs.FP_LIMIT);
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
    private static void initNativeAreas(Object[] realArguments, Object[] originalArguments, Object[][] expansions, int numberOfExplicitArguments, int initGPOffset, int initFPOffset,
                    LLVMPointer gpSaveAreaNativePtr, LLVMPointer fpSaveAreaNativePtr, LLVMPointer overflowArgAreaBaseNativePtr,
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
    public void cleanup() {

    }

    @ExportMessage
    static class Copy {

        @Specialization(guards = {"!source.isNativized()"})
        static void copyManaged(LLVMAarch64VaListStorage source, LLVMAarch64VaListStorage dest) {
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
            dest.overflowArgArea = source.overflowArgArea.clone();
            dest.nativized = null;
            dest.overflowArgAreaBaseNativePtr = null;
        }

        @Specialization(guards = {"source.isNativized()", "source.overflowArgArea != null"})
        static void copyNativeToManaged(LLVMAarch64VaListStorage source, LLVMAarch64VaListStorage dest,
                        @CachedLibrary("source") LLVMManagedReadLibrary srcReadLib) {
            // The destination va_list will be in the managed state, even if the source has been
            // nativized. We need to read some state from the native memory, though.

            copyManaged(source, dest);

            dest.fpOffset = srcReadLib.readI32(source, Aarch64BitVarArgs.FP_OFFSET);
            dest.gpOffset = srcReadLib.readI32(source, Aarch64BitVarArgs.GP_OFFSET);
            dest.overflowArgArea.setOffset(getArgPtrFromNativePtr(source, srcReadLib));
        }

        @Specialization(guards = {"source.isNativized()", "source.overflowArgArea == null"})
        static void copyNative(LLVMAarch64VaListStorage source, LLVMAarch64VaListStorage dest,
                        @Cached VAListPointerWrapperFactoryDelegate wrapperFactory,
                        @CachedLibrary(limit = "1") LLVMVaListLibrary vaListLibrary) {
            // The source valist is just a holder of the native counterpart and thus the destination
            // will not be set up as a managed va_list as it would be too complicated to restore the
            // managed state from the native one.
            vaListLibrary.copy(wrapperFactory.execute(source.nativized), dest);
        }

        @Specialization
        static void copyManagedToNative(LLVMAarch64VaListStorage source, NativeVAListWrapper dest, @CachedLibrary(limit = "1") LLVMVaListLibrary vaListLibrary) {
            LLVMAarch64VaListStorage dummyClone = new LLVMAarch64VaListStorage(source.rootNode);
            dummyClone.nativized = dest.nativeVAListPtr;
            vaListLibrary.initialize(dummyClone, source.realArguments, source.numberOfExplicitArguments);
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
    Object shift(Type type,
                    @CachedLibrary("this") LLVMManagedReadLibrary readLib,
                    @CachedLibrary("this") LLVMManagedWriteLibrary writeLib,
                    @Cached BranchProfile regAreaProfile,
                    @Cached("createBinaryProfile()") ConditionProfile isNativizedProfile) {
        int regSaveOffs = 0;
        int regSaveStep = 0;
        boolean lookIntoRegSaveArea = true;

        VarArgArea varArgArea = getVarArgArea(type);
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
                assert regSaveArea != null;
                long n = regSaveArea.offsetToIndex(offs);
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
            return o instanceof LLVMAarch64VaListStorage;
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
    @ExportLibrary(LLVMVaListLibrary.class)
    @ImportStatic(LLVMVaListStorage.class)
    public static final class NativeVAListWrapper {

        final LLVMPointer nativeVAListPtr;

        public NativeVAListWrapper(LLVMPointer nativeVAListPtr) {
            this.nativeVAListPtr = nativeVAListPtr;
        }

        @ExportMessage
        public void initialize(Object[] originalArgs, int numberOfExplicitArguments,
                        @Shared("allocaNode") @Cached StackAllocationNode stackAllocationNode,
                        @Shared("gpOffsetStore") @Cached LLVMI32OffsetStoreNode gpOffsetStore,
                        @Shared("fpOffsetStore") @Cached LLVMI32OffsetStoreNode fpOffsetStore,
                        @Exclusive @Cached LLVMI64OffsetStoreNode i64RegSaveAreaStore,
                        @Exclusive @Cached LLVMI32OffsetStoreNode i32RegSaveAreaStore,
                        @Exclusive @Cached LLVM80BitFloatOffsetStoreNode fp80bitRegSaveAreaStore,
                        @Exclusive @Cached LLVMPointerOffsetStoreNode pointerRegSaveAreaStore,
                        @Exclusive @Cached LLVMI64OffsetStoreNode i64OverflowArgAreaStore,
                        @Exclusive @Cached LLVMI32OffsetStoreNode i32OverflowArgAreaStore,
                        @Exclusive @Cached LLVM80BitFloatOffsetStoreNode fp80bitOverflowArgAreaStore,
                        @Exclusive @Cached LLVMPointerOffsetStoreNode pointerOverflowArgAreaStore,
                        @Shared("overflowAreaStore") @Cached LLVMPointerOffsetStoreNode overflowArgAreaStore,
                        @Shared("gpSaveAreaStore") @Cached LLVMPointerOffsetStoreNode gpSaveAreaStore,
                        @Shared("fpSaveAreaStore") @Cached LLVMPointerOffsetStoreNode fpSaveAreaStore,
                        @Cached NativeProfiledMemMove memMove,
                        @Cached ArgumentListExpander argsExpander) {

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

            int overflowArea = 0;
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
                    CompilerDirectives.transferToInterpreter();
                    throw new AssertionError(arg);
                }
            }

            LLVMPointer gpSaveAreaNativePtr = stackAllocationNode.executeWithTarget(Aarch64BitVarArgs.GP_LIMIT);
            // GP area pointer points to the top
            gpSaveAreaNativePtr = gpSaveAreaNativePtr.increment(Aarch64BitVarArgs.GP_LIMIT);

            LLVMPointer fpSaveAreaNativePtr = stackAllocationNode.executeWithTarget(Aarch64BitVarArgs.FP_LIMIT);
            // FP area pointer points to the top
            fpSaveAreaNativePtr = fpSaveAreaNativePtr.increment(Aarch64BitVarArgs.FP_LIMIT);

            LLVMPointer overflowArgAreaBaseNativePtr = stackAllocationNode.executeWithTarget(overflowArea);

            gpOffsetStore.executeWithTarget(nativeVAListPtr, Aarch64BitVarArgs.GP_OFFSET, initGPOffset);
            fpOffsetStore.executeWithTarget(nativeVAListPtr, Aarch64BitVarArgs.FP_OFFSET, initFPOffset);
            overflowArgAreaStore.executeWithTarget(nativeVAListPtr, Aarch64BitVarArgs.OVERFLOW_ARG_AREA, overflowArgAreaBaseNativePtr);
            gpSaveAreaStore.executeWithTarget(nativeVAListPtr, Aarch64BitVarArgs.GP_SAVE_AREA, gpSaveAreaNativePtr);
            fpSaveAreaStore.executeWithTarget(nativeVAListPtr, Aarch64BitVarArgs.FP_SAVE_AREA, fpSaveAreaNativePtr);

            initNativeAreas(realArguments, originalArgs, expansions, numberOfExplicitArguments, initGPOffset, initFPOffset, gpSaveAreaNativePtr, fpSaveAreaNativePtr, overflowArgAreaBaseNativePtr,
                            i64RegSaveAreaStore, i32RegSaveAreaStore, fp80bitRegSaveAreaStore, pointerRegSaveAreaStore, i64OverflowArgAreaStore, i32OverflowArgAreaStore, fp80bitOverflowArgAreaStore,
                            pointerOverflowArgAreaStore, memMove);
        }

        @ExportMessage
        public void cleanup() {
            // nop
        }

        @ExportMessage
        @ImportStatic({LLVMTypes.class, LLVMAarch64VaListStorage.class})
        static class Copy {

            @Specialization
            @TruffleBoundary
            static void copyToManagedObject(NativeVAListWrapper source, LLVMAarch64VaListStorage dest,
                            @Shared("allocaNode") @Cached StackAllocationNode allocaNode,
                            @CachedLibrary("source") LLVMVaListLibrary vaListLibrary,
                            @Cached(value = "getVAListTypeSize()", allowUncached = true) long vaListTypeSize) {
                LLVMPointer nativeDestPtr = allocaNode.executeWithTarget(vaListTypeSize);
                dest.nativized = nativeDestPtr;

                vaListLibrary.copy(source, new NativeVAListWrapper(nativeDestPtr));
            }

            @Specialization
            static void copyToNative(NativeVAListWrapper source, NativeVAListWrapper dest,
                            @Shared("gpOffsetStore") @Cached LLVMI32OffsetStoreNode gpOffsetStore,
                            @Shared("fpOffsetStore") @Cached LLVMI32OffsetStoreNode fpOffsetStore,
                            @Shared("gpSaveAreaStore") @Cached LLVMPointerOffsetStoreNode gpSaveAreaStore,
                            @Shared("fpSaveAreaStore") @Cached LLVMPointerOffsetStoreNode fpSaveAreaStore,
                            @Shared("overflowAreaStore") @Cached LLVMPointerOffsetStoreNode overflowAreaStore,
                            @Cached LLVMI32OffsetLoadNode gpOffsetLoad,
                            @Cached LLVMI32OffsetLoadNode fpOffsetLoad,
                            @Cached LLVMPointerOffsetLoadNode gpSaveAreaLoad,
                            @Cached LLVMPointerOffsetLoadNode fpSaveAreaLoad,
                            @Cached LLVMPointerOffsetLoadNode overflowAreaLoad) {

                // read fields from the source native va_list
                int gp = gpOffsetLoad.executeWithTarget(source.nativeVAListPtr, Aarch64BitVarArgs.GP_OFFSET);
                int fp = fpOffsetLoad.executeWithTarget(source.nativeVAListPtr, Aarch64BitVarArgs.FP_OFFSET);
                LLVMPointer gpSaveAreaPtr = gpSaveAreaLoad.executeWithTarget(source.nativeVAListPtr, Aarch64BitVarArgs.GP_SAVE_AREA);
                LLVMPointer fpSaveAreaPtr = fpSaveAreaLoad.executeWithTarget(source.nativeVAListPtr, Aarch64BitVarArgs.FP_SAVE_AREA);
                LLVMPointer overflowSaveAreaPtr = overflowAreaLoad.executeWithTarget(source.nativeVAListPtr, Aarch64BitVarArgs.OVERFLOW_ARG_AREA);

                // write fields to the destination native va_list
                gpOffsetStore.executeWithTarget(dest.nativeVAListPtr, Aarch64BitVarArgs.GP_OFFSET, gp);
                fpOffsetStore.executeWithTarget(dest.nativeVAListPtr, Aarch64BitVarArgs.FP_OFFSET, fp);
                gpSaveAreaStore.executeWithTarget(dest.nativeVAListPtr, Aarch64BitVarArgs.GP_SAVE_AREA, gpSaveAreaPtr);
                fpSaveAreaStore.executeWithTarget(dest.nativeVAListPtr, Aarch64BitVarArgs.FP_SAVE_AREA, fpSaveAreaPtr);
                overflowAreaStore.executeWithTarget(dest.nativeVAListPtr, Aarch64BitVarArgs.OVERFLOW_ARG_AREA, overflowSaveAreaPtr);
            }
        }

        @SuppressWarnings("static-method")
        @ExportMessage
        public Object shift(@SuppressWarnings("unused") Type type) {
            CompilerDirectives.transferToInterpreter();
            throw new UnsupportedOperationException("TODO");
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

        OverflowArgArea(Object[] args, long[] offsets, int overflowAreaSize, int argsCnt) {
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
