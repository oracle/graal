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
package com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.x86;

import java.util.ArrayList;
import java.util.Arrays;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.CachedLanguage;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.FrameInstance.FrameAccess;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.llvm.runtime.LLVMContext;
import com.oracle.truffle.llvm.runtime.LLVMLanguage;
import com.oracle.truffle.llvm.runtime.LLVMVarArgCompoundValue;
import com.oracle.truffle.llvm.runtime.NodeFactory;
import com.oracle.truffle.llvm.runtime.PlatformCapability;
import com.oracle.truffle.llvm.runtime.datalayout.DataLayout;
import com.oracle.truffle.llvm.runtime.debug.value.LLVMSourceTypeFactory;
import com.oracle.truffle.llvm.runtime.except.LLVMMemoryException;
import com.oracle.truffle.llvm.runtime.floating.LLVM80BitFloat;
import com.oracle.truffle.llvm.runtime.library.internal.LLVMManagedReadLibrary;
import com.oracle.truffle.llvm.runtime.library.internal.LLVMManagedWriteLibrary;
import com.oracle.truffle.llvm.runtime.memory.LLVMMemMoveNode;
import com.oracle.truffle.llvm.runtime.memory.LLVMStack.LLVMGetStackSpaceInstruction;
import com.oracle.truffle.llvm.runtime.memory.VarargsAreaStackAllocationNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMTypes;
import com.oracle.truffle.llvm.runtime.nodes.func.LLVMRootNode;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.va.LLVMVAEnd;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.va.LLVMVAListNode;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.va.LLVMVAStart;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.va.LLVMVaListLibrary;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.va.LLVMVaListStorage;
import com.oracle.truffle.llvm.runtime.nodes.memory.LLVMNativeVarargsAreaStackAllocationNode;
import com.oracle.truffle.llvm.runtime.nodes.memory.LLVMNativeVarargsAreaStackAllocationNodeGen;
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
import com.oracle.truffle.llvm.runtime.types.ArrayType;
import com.oracle.truffle.llvm.runtime.types.PointerType;
import com.oracle.truffle.llvm.runtime.types.PrimitiveType;
import com.oracle.truffle.llvm.runtime.types.StructureType;
import com.oracle.truffle.llvm.runtime.types.Type;
import com.oracle.truffle.llvm.spi.NativeTypeLibrary;

/**
 * This class implements the AMD64 (X86_64) version of the va_list managed object and reflects the
 * ABI specification of variable arguments list for AMD64 architecture. See chapter 3.5.7 of <a
 * href=https://refspecs.linuxbase.org/elf/x86_64-abi-0.99.pdf>x86_64-abi</a>.
 * <p>
 * The structure of this class corresponds to the <code>va_list</code> structure defined on the
 * AMD64 platform as:
 *
 * <pre>
 * typedef struct {
 *    unsigned int gp_offset;
 *    unsigned int fp_offset;
 *    void *overflow_arg_area;
 *    void *reg_save_area;
 * } va_list[1];
 * </pre>
 *
 * The logic of this class implements the response of a <code>va_list</code> instance during the
 * interaction with the bitcode generated by Clang when expanding the built-in macro
 * <a href="https://en.cppreference.com/w/cpp/utility/variadic/va_arg">va_arg</a>.
 * <p>
 * <h3><code>va_list</code> overview</h3> The <code>va_list</code> is an array containing a single
 * element of one structure containing the necessary information to implement the
 * <code>va_arg</code> macro. It is actually a storage for arguments passed on the stack or via
 * registers. The AMD64 argument passing scheme first tries to use registers to pass the arguments
 * before using the stack. It is the <code>reg_save_area</code> field in <code>va_list</code> that
 * contains register arguments, while the <code>overflow_arg_area</code> contains the stack
 * arguments.
 * <p>
 * The <code>va_list</code> structure works as a kind of iterator, whose state indicates the current
 * variadic argument. The <code>gp_offset</code> and <code>fp_offset</code> points to
 * <code>reg_save_area</code> to determine the current variadic argument. The
 * <code>reg_save_area</code> is split to two sections: the 6x8-byte one (GP) followed by 16x16-byte
 * one (FP). (N.B. The type argument of <code>va_arg</code> determines which offset is used to
 * calculate the position of the next argument). When no register argument is available any more,
 * the <code>overflow_arg_area</code> is used. In contrast to <code>reg_save_area</code>, which is
 * final, the <code>overflow_arg_area</code> pointer is advanced (updated) after each argument
 * retrieval.
 *
 * <h3>Class structure</h3> The {@link LLVMX86_64VaListStorage} class corresponds roughly to the
 * <code>va_list[0]</code> structure. So it contains the offsets and two references to the
 * {@link RegSaveArea register save area} and {@link OverflowArgArea overflow area}. In addition,
 * the class stores a copy of the real arguments and the number of explicit arguments. These values
 * are passed upon the initialization.
 *
 * <h3>Instantiation and Initialization</h3> This class is instantiated by {@link LLVMVAListNode},
 * which is appended to the AST when {@link NodeFactory#createAlloca(Type, int)} is called. That
 * method is the place where the request to allocate a <code>va_list</code> variable on the stack is
 * intercepted by comparing the type argument with the predefined platform specific
 * <code>va_list</code> type (obtained via {@link PlatformCapability#getVAListType()}).
 * <p>
 * The initialization is deferred until the moment when the <code>va_start</code> macro is invoked,
 * which corresponds to the invocation of the node {@link LLVMVAStart}. That node is platform
 * agnostic and uses {@link LLVMVaListLibrary} implemented by this class to initialize the
 * <code>va_list</code> instance by passing the actual arguments and the number of explicit
 * arguments. The initialization code follows the AMD64 specification when populating the argument
 * areas and initializing the offsets.
 *
 * <h3>LLVMVaListLibrary</h3> {@link LLVMVaListLibrary} is a sort of SPI that allows the vararg
 * infrastructure to be split into platform independent and platform specific code. The platform
 * independent code (residing in
 * <code>com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.va</code>) uses
 * {@link LLVMVaListLibrary} to manipulate platform specific <code>va_list</code> managed objects.
 *
 * <h3>Interaction</h3> This class implements {@link LLVMManagedWriteLibrary} and
 * {@link LLVMManagedReadLibrary} Truffle libraries to interact with the <code>va_arg</code>
 * bitcode. As only certain interaction patterns are expected, some methods are implemented
 * incompletely or are not implemented at all.
 *
 * <h3>Interop</h3> Additionally, this class implements a simple interop to allow other languages to
 * retrieve the variadic arguments. For this purpose, the va_list behaves like an array (i.e. it
 * supports <code>hasArrayElements</code>, <code>getArraySize</code> and
 * <code>readArrayElement</code> messages). Moreover, there is defined an invokable member
 * <code>get</code> that can be used to retrieve a typed variadic argument. The <code>get</code>
 * member accepts two arguments: the index of the variadic argument and its type. The type argument
 * is usually obtained via {@link NativeTypeLibrary}.
 *
 * <h3>ToNative Notes</h3> Before a managed va_list object escapes Sulong to native code, e.g. when
 * it is passed to a native function as an argument, it must be converted into its native form using
 * the <code>toNative</code> interop message. The <code>toNative</code> implementation in this class
 * allocates memory on the native stack and copies the state of the managed va_list to its native
 * va_list counterpart. As both va_list instances may be used interchangeably throughout both native
 * and interpreted code, their state must be in sync. To achieve this, all interaction between
 * bitcode and the managed va_list via {@link LLVMManagedWriteLibrary} and
 * {@link LLVMManagedReadLibrary} is delegated to the native va_list counterpart from the moment of
 * creating the native counterpart and the state of the managed va_list is no longer updated.
 *
 */
@ExportLibrary(LLVMManagedReadLibrary.class)
@ExportLibrary(LLVMManagedWriteLibrary.class)
@ExportLibrary(LLVMVaListLibrary.class)
@ExportLibrary(NativeTypeLibrary.class)
@ExportLibrary(InteropLibrary.class)
public final class LLVMX86_64VaListStorage extends LLVMVaListStorage {

    public static final ArrayType VA_LIST_TYPE = new ArrayType(StructureType.createNamedFromList("struct.__va_list_tag", false,
                    new ArrayList<>(Arrays.asList(PrimitiveType.I32, PrimitiveType.I32, PointerType.I8, PointerType.I8))), 1);

    private int initGPOffset;
    private int gpOffset;
    private int initFPOffset;
    private int fpOffset;

    private RegSaveArea regSaveArea;
    private LLVMPointer regSaveAreaPtr;
    private OverflowArgArea overflowArgArea;

    private LLVMPointer overflowArgAreaBaseNativePtr;

    private final LLVMRootNode rootNode;

    public LLVMX86_64VaListStorage(RootNode rootNode) {
        assert rootNode instanceof LLVMRootNode;
        this.rootNode = (LLVMRootNode) rootNode;
    }

    public boolean isNativized() {
        return nativized != null;
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

    /*
     * The algorithm specified in https://refspecs.linuxbase.org/elf/x86_64-abi-0.99.pdf allows us
     * to implement both LLVMManagedReadLibrary and LLVMManagedWriteLibrary quite sparsely, as only
     * certain types and offsets are used.
     */

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
        static int readManagedI32(LLVMX86_64VaListStorage vaList, long offset) {
            switch ((int) offset) {
                case X86_64BitVarArgs.GP_OFFSET:
                    return vaList.gpOffset;
                case X86_64BitVarArgs.FP_OFFSET:
                    return vaList.fpOffset;
                default:
                    CompilerDirectives.transferToInterpreter();
                    throw new UnsupportedOperationException("Should not get here");
            }
        }

        @Specialization(guards = "vaList.isNativized()")
        static int readNativeI32(LLVMX86_64VaListStorage vaList, long offset,
                        @Cached LLVMI32LoadNode.LLVMI32OffsetLoadNode offsetLoad) {
            return offsetLoad.executeWithTarget(vaList.nativized, offset);
        }
    }

    @ExportMessage
    static class ReadPointer {

        @Specialization(guards = "!vaList.isNativized()")
        static LLVMPointer readManagedPointer(LLVMX86_64VaListStorage vaList, long offset) {
            switch ((int) offset) {
                case X86_64BitVarArgs.OVERFLOW_ARG_AREA:
                    return vaList.overflowArgArea.getCurrentArgPtr();
                case X86_64BitVarArgs.REG_SAVE_AREA:
                    return vaList.regSaveAreaPtr;
                default:
                    CompilerDirectives.transferToInterpreter();
                    throw new UnsupportedOperationException("Should not get here");
            }
        }

        @Specialization(guards = "vaList.isNativized()")
        static LLVMPointer readNativePointer(LLVMX86_64VaListStorage vaList, long offset,
                        @Cached LLVMPointerLoadNode.LLVMPointerOffsetLoadNode offsetLoad) {
            return offsetLoad.executeWithTarget(vaList.nativized, offset);
        }
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    Object readGenericI64(long offset) {
        switch ((int) offset) {
            case X86_64BitVarArgs.OVERFLOW_ARG_AREA:
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
        static void writeManaged(LLVMX86_64VaListStorage vaList, long offset, int value) {
            switch ((int) offset) {
                case X86_64BitVarArgs.GP_OFFSET:
                    vaList.gpOffset = value;
                    vaList.regSaveArea.shift();
                    break;
                case X86_64BitVarArgs.FP_OFFSET:
                    vaList.fpOffset = value;
                    vaList.regSaveArea.shift();
                    break;
                default:
                    CompilerDirectives.transferToInterpreter();
                    throw new UnsupportedOperationException("Should not get here");
            }
        }

        @Specialization(guards = "vaList.isNativized()")
        static void writeNative(LLVMX86_64VaListStorage vaList, long offset, int value,
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
        static void writeManaged(LLVMX86_64VaListStorage vaList, long offset, @SuppressWarnings("unused") LLVMPointer value) {
            switch ((int) offset) {
                case X86_64BitVarArgs.OVERFLOW_ARG_AREA:
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
        static void writeNative(LLVMX86_64VaListStorage vaList, long offset, LLVMPointer value,
                        @Cached LLVMPointerStoreNode.LLVMPointerOffsetStoreNode offsetStore) {
            offsetStore.executeWithTarget(vaList.nativized, offset, value);
        }
    }

    private static int calculateUsedFpArea(Object[] realArguments, int numberOfExplicitArguments) {
        assert numberOfExplicitArguments <= realArguments.length;

        int usedFpArea = 0;
        final int fpAreaLimit = X86_64BitVarArgs.FP_LIMIT - X86_64BitVarArgs.GP_LIMIT;
        for (int i = 0; i < numberOfExplicitArguments && usedFpArea < fpAreaLimit; i++) {
            if (getVarArgArea(realArguments[i]) == VarArgArea.FP_AREA) {
                usedFpArea += X86_64BitVarArgs.FP_STEP;
            }
        }
        return usedFpArea;
    }

    private static int calculateUsedGpArea(Object[] realArguments, int numberOfExplicitArguments) {
        assert numberOfExplicitArguments <= realArguments.length;

        int usedGpArea = 0;
        for (int i = 0; i < numberOfExplicitArguments && usedGpArea < X86_64BitVarArgs.GP_LIMIT; i++) {
            if (getVarArgArea(realArguments[i]) == VarArgArea.GP_AREA) {
                usedGpArea += X86_64BitVarArgs.GP_STEP;
            }
        }

        return usedGpArea;
    }

    @ExportMessage
    static class Initialize {

        @Specialization(guards = {"!vaList.isNativized()"})
        static void initializeManaged(LLVMX86_64VaListStorage vaList, Object[] realArgs, int numOfExpArgs) {
            vaList.realArguments = realArgs;
            vaList.numberOfExplicitArguments = numOfExpArgs;
            assert numOfExpArgs <= realArgs.length;

            int gp = calculateUsedGpArea(realArgs, numOfExpArgs);
            vaList.gpOffset = vaList.initGPOffset = gp;
            int fp = X86_64BitVarArgs.GP_LIMIT + calculateUsedFpArea(realArgs, numOfExpArgs);
            vaList.fpOffset = vaList.initFPOffset = fp;

            int[] gpIdx = new int[realArgs.length];
            Arrays.fill(gpIdx, -1);
            int[] fpIdx = new int[realArgs.length];
            Arrays.fill(fpIdx, -1);

            Object[] overflowArgs = new Object[realArgs.length];
            long[] overflowAreaArgOffsets = new long[realArgs.length];
            Arrays.fill(overflowAreaArgOffsets, -1);

            int oi = 0;
            int overflowArea = 0;
            for (int i = numOfExpArgs; i < realArgs.length; i++) {
                final Object arg = realArgs[i];
                final VarArgArea area = getVarArgArea(arg);
                if (area == VarArgArea.GP_AREA && gp < X86_64BitVarArgs.GP_LIMIT) {
                    gpIdx[gp / X86_64BitVarArgs.GP_STEP] = i;
                    gp += X86_64BitVarArgs.GP_STEP;
                } else if (area == VarArgArea.FP_AREA && fp < X86_64BitVarArgs.FP_LIMIT) {
                    fpIdx[(fp - X86_64BitVarArgs.GP_LIMIT) / X86_64BitVarArgs.FP_STEP] = i;
                    fp += X86_64BitVarArgs.FP_STEP;
                } else if (area != VarArgArea.OVERFLOW_AREA) {
                    overflowAreaArgOffsets[oi] = overflowArea;
                    overflowArea += X86_64BitVarArgs.STACK_STEP;
                    overflowArgs[oi++] = arg;
                } else if (arg instanceof LLVM80BitFloat) {
                    overflowAreaArgOffsets[oi] = overflowArea;
                    overflowArea += 16;
                    overflowArgs[oi++] = arg;
                } else if (arg instanceof LLVMVarArgCompoundValue) {
                    overflowAreaArgOffsets[oi] = overflowArea;
                    LLVMVarArgCompoundValue obj = (LLVMVarArgCompoundValue) arg;
                    overflowArea += obj.getSize();
                    overflowArgs[oi++] = arg;
                } else {
                    CompilerDirectives.transferToInterpreter();
                    throw new AssertionError(arg);
                }
            }

            vaList.regSaveArea = new RegSaveArea(realArgs, gpIdx, fpIdx, numOfExpArgs);
            vaList.regSaveAreaPtr = LLVMManagedPointer.create(vaList.regSaveArea);
            vaList.overflowArgArea = new OverflowArgArea(overflowArgs, overflowAreaArgOffsets, overflowArea);
        }

        @Specialization(guards = {"vaList.isNativized()"})
        static void initializeNativized(LLVMX86_64VaListStorage vaList, Object[] realArgs, int numOfExpArgs,
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
                        @Cached LLVMPointerOffsetStoreNode regSaveAreaStore,
                        @Cached NativeProfiledMemMove memMove) {
            initializeManaged(vaList, realArgs, numOfExpArgs);

            VirtualFrame frame = (VirtualFrame) Truffle.getRuntime().getCurrentFrame().getFrame(FrameAccess.READ_WRITE);
            LLVMPointer regSaveAreaNativePtr = vaList.allocateNativeAreas(stackAllocationNode, gpOffsetStore, fpOffsetStore, overflowArgAreaStore, regSaveAreaStore, frame);

            initNativeAreas(vaList.realArguments, vaList.numberOfExplicitArguments, vaList.initGPOffset, vaList.initFPOffset, regSaveAreaNativePtr, vaList.overflowArgAreaBaseNativePtr,
                            i64RegSaveAreaStore, i32RegSaveAreaStore, fp80bitRegSaveAreaStore, pointerRegSaveAreaStore, i64OverflowArgAreaStore, i32OverflowArgAreaStore, fp80bitOverflowArgAreaStore,
                            pointerOverflowArgAreaStore, memMove);
        }

    }

    @ExportMessage
    void cleanup() {
        // nop
    }

    @ExportMessage
    static class Copy {

        @Specialization(guards = {"!source.isNativized()"})
        static void copyManaged(LLVMX86_64VaListStorage source, LLVMX86_64VaListStorage dest) {
            dest.realArguments = source.realArguments;
            dest.numberOfExplicitArguments = source.numberOfExplicitArguments;
            dest.initFPOffset = source.initFPOffset;
            dest.initGPOffset = source.initGPOffset;
            dest.fpOffset = source.fpOffset;
            dest.gpOffset = source.gpOffset;
            dest.regSaveArea = source.regSaveArea;
            dest.regSaveAreaPtr = source.regSaveAreaPtr;
            dest.overflowArgArea = source.overflowArgArea.clone();
            dest.nativized = null;
            dest.overflowArgAreaBaseNativePtr = null;

        }

        @Specialization(guards = {"source.isNativized()"})
        static void copyManagedNativized(LLVMX86_64VaListStorage source, LLVMX86_64VaListStorage dest, @CachedLibrary("source") LLVMManagedReadLibrary srcReadLib) {

            // The destination va_list will be in the managed state, even if the source has been
            // nativized. We need to read some state from the native memory, though.

            copyManaged(source, dest);

            dest.fpOffset = srcReadLib.readI32(source, X86_64BitVarArgs.FP_OFFSET);
            dest.gpOffset = srcReadLib.readI32(source, X86_64BitVarArgs.GP_OFFSET);
            dest.overflowArgArea.setOffset(getArgPtrFromNativePtr(source, srcReadLib));
        }

        @Specialization
        static void copyManagedToNative(LLVMX86_64VaListStorage source, LLVMNativePointer dest, @CachedLibrary(limit = "1") LLVMVaListLibrary vaListLibrary) {
            LLVMX86_64VaListStorage dummyClone = new LLVMX86_64VaListStorage(source.rootNode);
            dummyClone.nativized = dest;
            vaListLibrary.initialize(dummyClone, source.realArguments, source.numberOfExplicitArguments);
        }
    }

    /**
     * Calculate the number of argument shifts in the overflow area.
     *
     * @param srcVaList
     * @param readLib
     */
    private static long getArgPtrFromNativePtr(LLVMX86_64VaListStorage srcVaList, LLVMManagedReadLibrary readLib) {
        long curAddr;
        long baseAddr;
        LLVMPointer overflowAreaPtr = readLib.readPointer(srcVaList, X86_64BitVarArgs.OVERFLOW_ARG_AREA);
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
        int regSaveLimit = 0;
        boolean lookIntoRegSaveArea = true;

        VarArgArea varArgArea = getVarArgArea(type);
        switch (varArgArea) {
            case GP_AREA:
                regSaveOffs = X86_64BitVarArgs.GP_OFFSET;
                regSaveStep = X86_64BitVarArgs.GP_STEP;
                regSaveLimit = X86_64BitVarArgs.GP_LIMIT;
                break;

            case FP_AREA:
                regSaveOffs = X86_64BitVarArgs.FP_OFFSET;
                regSaveStep = X86_64BitVarArgs.FP_STEP;
                regSaveLimit = X86_64BitVarArgs.FP_LIMIT;
                break;

            case OVERFLOW_AREA:
                lookIntoRegSaveArea = false;
                break;
        }

        if (lookIntoRegSaveArea) {
            regAreaProfile.enter();

            int offs = readLib.readI32(this, regSaveOffs);
            if (offs < regSaveLimit) {
                writeLib.writeI32(this, regSaveOffs, offs + regSaveStep);
                long n = this.regSaveArea.offsetToIndex(offs);
                int i = (int) ((n << 32) >> 32);
                return this.regSaveArea.args[i];
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
            writeLib.writePointer(this, X86_64BitVarArgs.OVERFLOW_ARG_AREA, shiftedOverflowAreaPtr);

            return currentArg;
        } else {
            Object currentArg = this.overflowArgArea.getCurrentArg();
            this.overflowArgArea.shift(1);
            return currentArg;
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
    VarargsAreaStackAllocationNode createVarargsAreaStackAllocationNode(LLVMContext llvmCtx) {
        DataLayout dataLayout = getDataLayout();
        return llvmCtx.getLanguage().getActiveConfiguration().createNodeFactory(llvmCtx.getLanguage(), dataLayout).createVarargsAreaStackAllocation();
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
                    @Cached LLVMPointerOffsetStoreNode regSaveAreaStore,
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

        LLVMPointer regSaveAreaNativePtr = allocateNativeAreas(stackAllocationNode, gpOffsetStore, fpOffsetStore, overflowArgAreaStore, regSaveAreaStore, frame);

        initNativeAreas(this.realArguments, this.numberOfExplicitArguments, this.initGPOffset, this.initFPOffset, regSaveAreaNativePtr, this.overflowArgAreaBaseNativePtr, i64RegSaveAreaStore,
                        i32RegSaveAreaStore, fp80bitRegSaveAreaStore, pointerRegSaveAreaStore, i64OverflowArgAreaStore, i32OverflowArgAreaStore, fp80bitOverflowArgAreaStore,
                        pointerOverflowArgAreaStore, memMove);
    }

    private LLVMPointer allocateNativeAreas(NativeAllocaInstruction stackAllocationNode, LLVMI32OffsetStoreNode gpOffsetStore, LLVMI32OffsetStoreNode fpOffsetStore,
                    LLVMPointerOffsetStoreNode overflowArgAreaStore,
                    LLVMPointerOffsetStoreNode regSaveAreaStore, VirtualFrame frame) {
        LLVMPointer regSaveAreaNativePtr = stackAllocationNode.executeWithTarget(frame, X86_64BitVarArgs.FP_LIMIT, rootNode.getStackAccess());
        this.overflowArgAreaBaseNativePtr = stackAllocationNode.executeWithTarget(frame, overflowArgArea.overflowAreaSize, rootNode.getStackAccess());

        gpOffsetStore.executeWithTarget(nativized, X86_64BitVarArgs.GP_OFFSET, gpOffset);
        fpOffsetStore.executeWithTarget(nativized, X86_64BitVarArgs.FP_OFFSET, fpOffset);
        overflowArgAreaStore.executeWithTarget(nativized, X86_64BitVarArgs.OVERFLOW_ARG_AREA, overflowArgAreaBaseNativePtr.increment(overflowArgArea.getOffset()));
        regSaveAreaStore.executeWithTarget(nativized, X86_64BitVarArgs.REG_SAVE_AREA, regSaveAreaNativePtr);
        return regSaveAreaNativePtr;
    }

    /**
     * Reconstruct native register_save_area and overflow_arg_area according to AMD64 ABI.
     */
    private static void initNativeAreas(Object[] realArguments, int numberOfExplicitArguments, int initGPOffset, int initFPOffset, LLVMPointer regSaveAreaNativePtr,
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
                final VarArgArea area = getVarArgArea(object);

                if (area == VarArgArea.GP_AREA && gp < X86_64BitVarArgs.GP_LIMIT) {
                    storeArgument(regSaveAreaNativePtr, gp, memMove, i64RegSaveAreaStore, i32RegSaveAreaStore,
                                    fp80bitRegSaveAreaStore, pointerRegSaveAreaStore, object, X86_64BitVarArgs.STACK_STEP);
                    gp += X86_64BitVarArgs.GP_STEP;
                } else if (area == VarArgArea.FP_AREA && fp < X86_64BitVarArgs.FP_LIMIT) {
                    storeArgument(regSaveAreaNativePtr, fp, memMove, i64RegSaveAreaStore, i32RegSaveAreaStore,
                                    fp80bitRegSaveAreaStore, pointerRegSaveAreaStore, object, X86_64BitVarArgs.STACK_STEP);
                    fp += X86_64BitVarArgs.FP_STEP;
                } else {
                    overflowOffset += storeArgument(overflowArgAreaBaseNativePtr, overflowOffset, memMove,
                                    i64OverflowArgAreaStore, i32OverflowArgAreaStore,
                                    fp80bitOverflowArgAreaStore, pointerOverflowArgAreaStore, object, X86_64BitVarArgs.STACK_STEP);
                }
            }
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
    @ImportStatic(LLVMX86_64VaListStorage.class)
    public static final class NativeVAListWrapper {

        final LLVMNativePointer nativeVAListPtr;
        private final LLVMRootNode rootNode;

        public NativeVAListWrapper(LLVMNativePointer nativeVAListPtr, RootNode rootNode) {
            this.nativeVAListPtr = nativeVAListPtr;
            assert rootNode instanceof LLVMRootNode;
            this.rootNode = (LLVMRootNode) rootNode;
        }

        @SuppressWarnings("static-method")
        LLVMNativeVarargsAreaStackAllocationNode createLLVMNativeVarargsAreaStackAllocationNode() {
            return LLVMNativeVarargsAreaStackAllocationNodeGen.create();
        }

        @SuppressWarnings("static-method")
        LLVMNativeVarargsAreaStackAllocationNode createLLVMNativeVarargsAreaStackAllocationNodeUncached() {
            LLVMNativeVarargsAreaStackAllocationNode node = LLVMNativeVarargsAreaStackAllocationNodeGen.create();
            node.setStackAccess(rootNode.getStackAccess());
            return node;
        }

        @ExportMessage
        public void initialize(Object[] arguments, int numberOfExplicitArguments,
                        @Cached(value = "this.createLLVMNativeVarargsAreaStackAllocationNode()", uncached = "this.createLLVMNativeVarargsAreaStackAllocationNodeUncached()") LLVMNativeVarargsAreaStackAllocationNode stackAllocationNode,
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
                        @Shared("regSaveAreaStore") @Cached LLVMPointerOffsetStoreNode regSaveAreaStore,
                        @Cached NativeProfiledMemMove memMove) {
            VirtualFrame frame = (VirtualFrame) Truffle.getRuntime().getCurrentFrame().getFrame(FrameAccess.READ_WRITE);

            int gp = calculateUsedGpArea(arguments, numberOfExplicitArguments);
            int initGPOffset = gp;
            int fp = X86_64BitVarArgs.GP_LIMIT + calculateUsedFpArea(arguments, numberOfExplicitArguments);
            int initFPOffset = fp;

            int overflowArea = 0;
            for (int i = numberOfExplicitArguments; i < arguments.length; i++) {
                final Object arg = arguments[i];
                final VarArgArea area = getVarArgArea(arg);
                if (area == VarArgArea.GP_AREA && gp < X86_64BitVarArgs.GP_LIMIT) {
                    gp += X86_64BitVarArgs.GP_STEP;
                } else if (area == VarArgArea.FP_AREA && fp < X86_64BitVarArgs.FP_LIMIT) {
                    fp += X86_64BitVarArgs.FP_STEP;
                } else if (area != VarArgArea.OVERFLOW_AREA) {
                    overflowArea += X86_64BitVarArgs.STACK_STEP;
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

            LLVMPointer regSaveAreaNativePtr = stackAllocationNode.executeWithTarget(frame, X86_64BitVarArgs.FP_LIMIT);
            LLVMPointer overflowArgAreaBaseNativePtr = LLVMNativePointer.cast(stackAllocationNode.executeWithTarget(frame, overflowArea));

            gpOffsetStore.executeWithTarget(nativeVAListPtr, X86_64BitVarArgs.GP_OFFSET, initGPOffset);
            fpOffsetStore.executeWithTarget(nativeVAListPtr, X86_64BitVarArgs.FP_OFFSET, initFPOffset);
            overflowArgAreaStore.executeWithTarget(nativeVAListPtr, X86_64BitVarArgs.OVERFLOW_ARG_AREA, overflowArgAreaBaseNativePtr);
            regSaveAreaStore.executeWithTarget(nativeVAListPtr, X86_64BitVarArgs.REG_SAVE_AREA, regSaveAreaNativePtr);

            initNativeAreas(arguments, numberOfExplicitArguments, initGPOffset, initFPOffset, regSaveAreaNativePtr, overflowArgAreaBaseNativePtr, i64RegSaveAreaStore, i32RegSaveAreaStore,
                            fp80bitRegSaveAreaStore, pointerRegSaveAreaStore, i64OverflowArgAreaStore, i32OverflowArgAreaStore, fp80bitOverflowArgAreaStore, pointerOverflowArgAreaStore, memMove);
        }

        @ExportMessage
        public void cleanup() {
            // nop
        }

        @ExportMessage
        @ImportStatic(LLVMTypes.class)
        static class Copy {

            static LLVMExpressionNode createAllocaNode(LLVMLanguage language) {
                DataLayout dataLayout = getDataLayout();
                return language.getActiveConfiguration().createNodeFactory(language, dataLayout).createAlloca(VA_LIST_TYPE, 16);
            }

            @Specialization(guards = "isManagedPointer(dest)")
            @TruffleBoundary
            static void copyToManaged(NativeVAListWrapper source, LLVMManagedPointer dest,
                            @SuppressWarnings("unused") @CachedLanguage() LLVMLanguage language,
                            @Cached(value = "createAllocaNode(language)", allowUncached = true) LLVMExpressionNode allocaNode,
                            @CachedLibrary("source") LLVMVaListLibrary vaListLibrary) {
                assert dest.getObject() instanceof LLVMX86_64VaListStorage;

                VirtualFrame frame = (VirtualFrame) Truffle.getRuntime().getCurrentFrame().getFrame(FrameAccess.MATERIALIZE);
                LLVMNativePointer nativeDestPtr = LLVMNativePointer.cast(allocaNode.executeGeneric(frame));
                ((LLVMX86_64VaListStorage) dest.getObject()).nativized = nativeDestPtr;

                vaListLibrary.copy(source, nativeDestPtr);
            }

            @Specialization(guards = "isNativePointer(dest)")
            static void copyToNative(NativeVAListWrapper source, LLVMNativePointer dest,
                            @Shared("gpOffsetStore") @Cached LLVMI32OffsetStoreNode gpOffsetStore,
                            @Shared("fpOffsetStore") @Cached LLVMI32OffsetStoreNode fpOffsetStore,
                            @Shared("regSaveAreaStore") @Cached LLVMPointerOffsetStoreNode regSaveAreaStore,
                            @Shared("overflowAreaStore") @Cached LLVMPointerOffsetStoreNode overflowAreaStore,
                            @Cached LLVMI32OffsetLoadNode gpOffsetLoad,
                            @Cached LLVMI32OffsetLoadNode fpOffsetLoad,
                            @Cached LLVMPointerOffsetLoadNode regSaveAreaLoad,
                            @Cached LLVMPointerOffsetLoadNode overflowAreaLoad) {

                // read fields from the source native va_list
                int gp = gpOffsetLoad.executeWithTarget(source.nativeVAListPtr, X86_64BitVarArgs.GP_OFFSET);
                int fp = fpOffsetLoad.executeWithTarget(source.nativeVAListPtr, X86_64BitVarArgs.FP_OFFSET);
                LLVMPointer regSaveAreaPtr = regSaveAreaLoad.executeWithTarget(source.nativeVAListPtr, X86_64BitVarArgs.REG_SAVE_AREA);
                LLVMPointer overflowSaveAreaPtr = overflowAreaLoad.executeWithTarget(source.nativeVAListPtr, X86_64BitVarArgs.OVERFLOW_ARG_AREA);

                // read fields to the destination native va_list
                gpOffsetStore.executeWithTarget(dest, X86_64BitVarArgs.GP_OFFSET, gp);
                fpOffsetStore.executeWithTarget(dest, X86_64BitVarArgs.FP_OFFSET, fp);
                regSaveAreaStore.executeWithTarget(dest, X86_64BitVarArgs.REG_SAVE_AREA, regSaveAreaPtr);
                overflowAreaStore.executeWithTarget(dest, X86_64BitVarArgs.OVERFLOW_ARG_AREA, overflowSaveAreaPtr);
            }
        }

        @SuppressWarnings("static-method")
        @ExportMessage
        public Object shift(@SuppressWarnings("unused") Type type) {
            CompilerDirectives.transferToInterpreter();
            throw new UnsupportedOperationException("TODO");
        }
    }

    /**
     * The class of the managed <code>reg_save_area</code> of <code>va_list</code>. The array of
     * arguments is the array of real arguments. The input offset argument to
     * <code>offsetToIndex</code> originates in <code>gp_offset</code> or <code>fp_offset</code>
     * fields in <code>va_list</code> and points to the <code>reg_save_area</code>. The
     * <code>offsetToIndex</code> method calculates the corresponding <code>reg_save_area</code>
     * index for the input offset and uses the <code>gpIdx</code> and <code>fpIdx</code> arrays to
     * translate <code>reg_save_area</code> index to the real arguments index.
     */
    @ExportLibrary(NativeTypeLibrary.class)
    public static final class RegSaveArea extends ArgsArea {

        // TODO: consider removing NativeTypeLibrary

        private final int[] gpIdx;
        private final int[] fpIdx;
        private final int numOfExpArgs;

        private int curArg;

        RegSaveArea(Object[] args, int[] gpIdx, int[] fpIdx, int numOfExpArgs) {
            super(args);
            this.gpIdx = gpIdx;
            this.fpIdx = fpIdx;
            this.numOfExpArgs = numOfExpArgs;
        }

        @Override
        protected long offsetToIndex(long offset) {
            if (offset < 0) {
                return -1;
            }

            if (offset < X86_64BitVarArgs.GP_LIMIT) {
                long i = offset / X86_64BitVarArgs.GP_STEP;
                long j = offset % X86_64BitVarArgs.GP_STEP;
                return i >= gpIdx.length ? -1 : gpIdx[(int) i] + (j << 32);
            } else {
                assert offset < X86_64BitVarArgs.FP_LIMIT;
                long i = (offset - X86_64BitVarArgs.GP_LIMIT) / (X86_64BitVarArgs.FP_STEP);
                long j = (offset - X86_64BitVarArgs.GP_LIMIT) % (X86_64BitVarArgs.FP_STEP);
                return i >= fpIdx.length ? -1 : fpIdx[(int) i] + (j << 32);
            }
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

        // TODO: consider removing NativeTypeLibrary

        OverflowArgArea(Object[] args, long[] offsets, int overflowAreaSize) {
            super(args, offsets, overflowAreaSize);
        }

        @Override
        public OverflowArgArea clone() {
            OverflowArgArea cloned = new OverflowArgArea(args, offsets, overflowAreaSize);
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
            return getVarArgType(getCurrentArg());
        }

    }

}
