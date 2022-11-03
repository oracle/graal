/*
 * Copyright (c) 2020, 2022, Oracle and/or its affiliates.
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
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateAOT;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.llvm.runtime.LLVMLanguage;
import com.oracle.truffle.llvm.runtime.LLVMVarArgCompoundValue;
import com.oracle.truffle.llvm.runtime.NodeFactory;
import com.oracle.truffle.llvm.runtime.PlatformCapability;
import com.oracle.truffle.llvm.runtime.debug.value.LLVMSourceTypeFactory;
import com.oracle.truffle.llvm.runtime.except.LLVMMemoryException;
import com.oracle.truffle.llvm.runtime.floating.LLVM80BitFloat;
import com.oracle.truffle.llvm.runtime.library.internal.LLVMManagedReadLibrary;
import com.oracle.truffle.llvm.runtime.library.internal.LLVMManagedWriteLibrary;
import com.oracle.truffle.llvm.runtime.memory.LLVMMemMoveNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMTypes;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.va.LLVMVAEnd;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.va.LLVMVAListNode;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.va.LLVMVAStart;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.va.LLVMVaListLibrary;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.va.LLVMVaListStorage;
import com.oracle.truffle.llvm.runtime.nodes.memory.NativeProfiledMemMove;
import com.oracle.truffle.llvm.runtime.nodes.memory.load.LLVMDoubleLoadNode.LLVMDoubleOffsetLoadNode;
import com.oracle.truffle.llvm.runtime.nodes.memory.load.LLVMFloatLoadNode.LLVMFloatOffsetLoadNode;
import com.oracle.truffle.llvm.runtime.nodes.memory.load.LLVMI16LoadNode.LLVMI16OffsetLoadNode;
import com.oracle.truffle.llvm.runtime.nodes.memory.load.LLVMI1LoadNode.LLVMI1OffsetLoadNode;
import com.oracle.truffle.llvm.runtime.nodes.memory.load.LLVMI32LoadNode;
import com.oracle.truffle.llvm.runtime.nodes.memory.load.LLVMI32LoadNode.LLVMI32OffsetLoadNode;
import com.oracle.truffle.llvm.runtime.nodes.memory.load.LLVMI64LoadNode.LLVMI64OffsetLoadNode;
import com.oracle.truffle.llvm.runtime.nodes.memory.load.LLVMI8LoadNode.LLVMI8OffsetLoadNode;
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
 * <p>
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
 * <code>va_list</code> type (obtained via {@link PlatformCapability#isManagedVAListType(Type)}).
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
 */
@ExportLibrary(value = LLVMManagedReadLibrary.class, useForAOT = true, useForAOTPriority = 3)
@ExportLibrary(value = LLVMManagedWriteLibrary.class, useForAOT = true, useForAOTPriority = 2)
@ExportLibrary(value = LLVMVaListLibrary.class, useForAOT = true, useForAOTPriority = 1)
@ExportLibrary(value = NativeTypeLibrary.class, useForAOT = true, useForAOTPriority = 0)
@ExportLibrary(InteropLibrary.class)
public final class LLVMX86_64VaListStorage extends LLVMVaListStorage {

    public static final ArrayType VA_LIST_TYPE = new ArrayType(StructureType.createNamedFromList("struct.__va_list_tag", false,
                    new ArrayList<>(Arrays.asList(PrimitiveType.I32, PrimitiveType.I32, PointerType.I8, PointerType.I8))), 1);

    private final Type vaListType;

    private int initGPOffset;
    private int gpOffset;
    private int initFPOffset;
    private int fpOffset;

    private RegSaveArea regSaveArea;
    private LLVMPointer regSaveAreaPtr;
    private OverflowArgArea overflowArgArea;

    private LLVMPointer overflowArgAreaBaseNativePtr;
    private LLVMPointer regSaveAreaNativePtr;

    public LLVMX86_64VaListStorage(LLVMPointer vaListStackPtr, Type vaListType) {
        super(vaListStackPtr);
        this.vaListType = vaListType;
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
        return LLVMLanguage.get(null).getInteropType(LLVMSourceTypeFactory.resolveType(vaListType, findDataLayoutFromCurrentFrame()));
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
        static int readManagedI32(LLVMX86_64VaListStorage vaList, long offset) {
            switch ((int) offset) {
                case X86_64BitVarArgs.GP_OFFSET:
                    return vaList.gpOffset;
                case X86_64BitVarArgs.FP_OFFSET:
                    return vaList.fpOffset;
                default:
                    throw CompilerDirectives.shouldNotReachHere();
            }
        }

        @Specialization(guards = "vaList.isNativized()")
        @GenerateAOT.Exclude // recursion cut
        static int readNativeI32(LLVMX86_64VaListStorage vaList, long offset,
                        @Cached LLVMI32LoadNode.LLVMI32OffsetLoadNode offsetLoad) {
            return offsetLoad.executeWithTarget(vaList.vaListStackPtr, offset);
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
                    throw CompilerDirectives.shouldNotReachHere();
            }
        }

        @Specialization(guards = "vaList.isNativized()")
        @GenerateAOT.Exclude // recursion cut
        static LLVMPointer readNativePointer(LLVMX86_64VaListStorage vaList, long offset,
                        @Cached LLVMPointerLoadNode.LLVMPointerOffsetLoadNode offsetLoad) {
            return offsetLoad.executeWithTarget(vaList.vaListStackPtr, offset);
        }
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    Object readGenericI64(long offset) {
        switch ((int) offset) {
            case X86_64BitVarArgs.OVERFLOW_ARG_AREA:
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
                    throw CompilerDirectives.shouldNotReachHere();
            }
        }

        @Specialization(guards = "vaList.isNativized()")
        @GenerateAOT.Exclude // recursion cut
        static void writeNative(LLVMX86_64VaListStorage vaList, long offset, int value,
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
        static void writeManaged(LLVMX86_64VaListStorage vaList, long offset, @SuppressWarnings("unused") LLVMPointer value,
                        @Cached BranchProfile exception,
                        @CachedLibrary("vaList") LLVMManagedWriteLibrary self) {
            switch ((int) offset) {
                case X86_64BitVarArgs.OVERFLOW_ARG_AREA:
                    // Assume that updating the overflowArea pointer means shifting the current
                    // argument, according to abi
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
        static void writeNative(LLVMX86_64VaListStorage vaList, long offset, LLVMPointer value,
                        @Cached LLVMPointerStoreNode.LLVMPointerOffsetStoreNode offsetStore) {
            offsetStore.executeWithTarget(vaList.vaListStackPtr, offset, value);
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
        static void initializeManaged(LLVMX86_64VaListStorage vaList, Object[] realArgs, int numOfExpArgs, Frame frame,
                        @Shared("stackAllocationNode") @Cached StackAllocationNode stackAllocationNode) {
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
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    throw CompilerDirectives.shouldNotReachHere(String.valueOf(arg));
                }
            }

            vaList.regSaveArea = new RegSaveArea(realArgs, gpIdx, fpIdx, numOfExpArgs);
            vaList.regSaveAreaPtr = LLVMManagedPointer.create(vaList.regSaveArea);
            vaList.overflowArgArea = new OverflowArgArea(overflowArgs, overflowAreaArgOffsets, overflowArea);
            vaList.allocateNativeAreas(stackAllocationNode, frame);
        }

        @Specialization(guards = {"vaList.isNativized()"})
        static void initializeNativized(LLVMX86_64VaListStorage vaList, Object[] realArgs, int numOfExpArgs, Frame frame,
                        @Exclusive @Cached LLVMI64OffsetStoreNode i64RegSaveAreaStore,
                        @Exclusive @Cached LLVMI32OffsetStoreNode i32RegSaveAreaStore,
                        @Exclusive @Cached LLVM80BitFloatOffsetStoreNode fp80bitRegSaveAreaStore,
                        @Exclusive @Cached LLVMPointerOffsetStoreNode pointerRegSaveAreaStore,
                        @Exclusive @Cached LLVMI64OffsetStoreNode i64OverflowArgAreaStore,
                        @Exclusive @Cached LLVMI32OffsetStoreNode i32OverflowArgAreaStore,
                        @Exclusive @Cached LLVM80BitFloatOffsetStoreNode fp80bitOverflowArgAreaStore,
                        @Exclusive @Cached LLVMPointerOffsetStoreNode pointerOverflowArgAreaStore,
                        @Cached LLVMI32OffsetStoreNode gpOffsetStore,
                        @Cached LLVMI32OffsetStoreNode fpOffsetStore,
                        @Cached LLVMPointerOffsetStoreNode overflowArgAreaStore,
                        @Exclusive @Cached LLVMPointerOffsetStoreNode regSaveAreaStore,
                        @Cached NativeProfiledMemMove memMove,
                        @Shared("stackAllocationNode") @Cached StackAllocationNode stackAllocationNode) {
            initializeManaged(vaList, realArgs, numOfExpArgs, frame, stackAllocationNode);
            initNativeVAList(gpOffsetStore, fpOffsetStore, overflowArgAreaStore, regSaveAreaStore, vaList.vaListStackPtr, vaList.gpOffset, vaList.fpOffset,
                            vaList.overflowArgAreaBaseNativePtr.increment(vaList.overflowArgArea.getOffset()), vaList.regSaveAreaNativePtr);
            initNativeAreas(vaList.realArguments, vaList.numberOfExplicitArguments, vaList.initGPOffset, vaList.initFPOffset, vaList.regSaveAreaNativePtr, vaList.overflowArgAreaBaseNativePtr,
                            i64RegSaveAreaStore, i32RegSaveAreaStore, fp80bitRegSaveAreaStore, pointerRegSaveAreaStore, i64OverflowArgAreaStore, i32OverflowArgAreaStore, fp80bitOverflowArgAreaStore,
                            pointerOverflowArgAreaStore, memMove);
            vaList.nativized = true;
        }

    }

    @ExportMessage
    void cleanup(@SuppressWarnings("unused") Frame frame) {
        // nop
    }

    @ExportMessage
    static class Copy {

        @Specialization(guards = {"!source.isNativized()"})
        static void copyManaged(LLVMX86_64VaListStorage source, LLVMX86_64VaListStorage dest, Frame frame,
                        @Shared("stackAllocationNode") @Cached StackAllocationNode stackAllocationNode) {
            dest.realArguments = source.realArguments;
            dest.numberOfExplicitArguments = source.numberOfExplicitArguments;
            dest.initFPOffset = source.initFPOffset;
            dest.initGPOffset = source.initGPOffset;
            dest.fpOffset = source.fpOffset;
            dest.gpOffset = source.gpOffset;
            dest.regSaveArea = source.regSaveArea;
            dest.regSaveAreaPtr = source.regSaveAreaPtr;
            dest.overflowArgArea = source.overflowArgArea.clone();

            dest.allocateNativeAreas(stackAllocationNode, frame);
        }

        @Specialization(guards = {"source.isNativized()"})
        static void copyManagedNativized(LLVMX86_64VaListStorage source, LLVMX86_64VaListStorage dest, Frame frame,
                        @CachedLibrary(limit = "1") LLVMManagedReadLibrary srcReadLib,
                        @Shared("stackAllocationNode") @Cached StackAllocationNode stackAllocationNode) {

            // The destination va_list will be in the managed state, even if the source has been
            // nativized. We need to read some state from the native memory, though.

            copyManaged(source, dest, frame, stackAllocationNode);

            dest.fpOffset = srcReadLib.readI32(source, X86_64BitVarArgs.FP_OFFSET);
            dest.gpOffset = srcReadLib.readI32(source, X86_64BitVarArgs.GP_OFFSET);
            dest.overflowArgArea.setOffset(getArgPtrFromNativePtr(source, srcReadLib));
        }

        @Specialization
        @GenerateAOT.Exclude // recursion cut
        static void copyManagedToNative(LLVMX86_64VaListStorage source, NativeVAListWrapper dest, Frame frame,
                        @CachedLibrary(limit = "1") LLVMVaListLibrary vaListLibrary) {
            LLVMX86_64VaListStorage dummyClone = new LLVMX86_64VaListStorage(dest.nativeVAListPtr, source.vaListType);
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
    private static long getArgPtrFromNativePtr(LLVMX86_64VaListStorage srcVaList, LLVMManagedReadLibrary readLib) {
        long curAddr;
        long baseAddr;
        LLVMPointer overflowAreaPtr = readLib.readPointer(srcVaList, X86_64BitVarArgs.OVERFLOW_ARG_AREA);
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
                    @Cached LLVMI1OffsetLoadNode loadI1,
                    @Cached LLVMI8OffsetLoadNode loadI8,
                    @Cached LLVMI16OffsetLoadNode loadI16,
                    @Cached LLVMI32OffsetLoadNode loadI32,
                    @Cached LLVMI64OffsetLoadNode loadI64,
                    @Cached LLVMPointerOffsetLoadNode loadPointer,
                    @Cached LLVMFloatOffsetLoadNode loadFloat,
                    @Cached LLVMDoubleOffsetLoadNode loadDouble,
                    @Cached LLVMPointerOffsetLoadNode load1,
                    @Exclusive @Cached LLVMPointerOffsetStoreNode store1,
                    @Cached LLVMPointerOffsetLoadNode regSaveAreaLoad,
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
                if (regSaveArea != null) {
                    long n = this.regSaveArea.offsetToIndex(offs);
                    int i = (int) ((n << 32) >> 32);
                    return this.regSaveArea.args[i];
                } else {
                    LLVMPointer areaPtr = regSaveAreaLoad.executeWithTarget(this.vaListStackPtr, X86_64BitVarArgs.REG_SAVE_AREA);
                    if (type instanceof PrimitiveType) {
                        switch (((PrimitiveType) type).getPrimitiveKind()) {
                            case DOUBLE:
                                return loadDouble.executeWithTargetGeneric(areaPtr, offs);
                            case FLOAT:
                                return loadFloat.executeWithTargetGeneric(areaPtr, offs);
                            case I1:
                                return loadI1.executeWithTargetGeneric(areaPtr, offs);
                            case I16:
                                return loadI16.executeWithTargetGeneric(areaPtr, offs);
                            case I32:
                                return loadI32.executeWithTargetGeneric(areaPtr, offs);
                            case I64:
                                return loadI64.executeWithTargetGeneric(areaPtr, offs);
                            case I8:
                                return loadI8.executeWithTargetGeneric(areaPtr, offs);
                            default:
                                throw CompilerDirectives.shouldNotReachHere("not implemented");
                        }
                    } else if (type instanceof PointerType) {
                        return loadPointer.executeWithTargetGeneric(areaPtr, offs);
                    } else {
                        throw CompilerDirectives.shouldNotReachHere("not implemented");
                    }
                }
            }
        }

        // overflow area
        if (isNativizedProfile.profile(isNativized())) {
            // Synchronize the managed current argument pointer from the native overflow area
            LLVMPointer areaPtr = load1.executeWithTarget(vaListStackPtr, X86_64BitVarArgs.OVERFLOW_ARG_AREA);
            store1.executeWithTarget(vaListStackPtr, X86_64BitVarArgs.OVERFLOW_ARG_AREA, areaPtr.increment(8));
            int offs = 0;
            if (type instanceof PrimitiveType) {
                switch (((PrimitiveType) type).getPrimitiveKind()) {
                    case DOUBLE:
                        return loadDouble.executeWithTargetGeneric(areaPtr, offs);
                    case FLOAT:
                        return loadFloat.executeWithTargetGeneric(areaPtr, offs);
                    case I1:
                        return loadI1.executeWithTargetGeneric(areaPtr, offs);
                    case I16:
                        return loadI16.executeWithTargetGeneric(areaPtr, offs);
                    case I32:
                        return loadI32.executeWithTargetGeneric(areaPtr, offs);
                    case I64:
                        return loadI64.executeWithTargetGeneric(areaPtr, offs);
                    case I8:
                        return loadI8.executeWithTargetGeneric(areaPtr, offs);
                    default:
                        throw CompilerDirectives.shouldNotReachHere("not implemented");
                }
            } else if (type instanceof PointerType) {
                return loadPointer.executeWithTargetGeneric(areaPtr, offs);
            } else {
                throw CompilerDirectives.shouldNotReachHere("not implemented");
            }
        } else {
            Object currentArg = this.overflowArgArea.getCurrentArg();
            this.overflowArgArea.shift(1);
            return currentArg;
        }
    }

    @SuppressWarnings("static-method")
    @ExportMessage
    @TruffleBoundary
    void toNative(@Cached LLVMI32OffsetStoreNode gpOffsetStore,
                    @Cached LLVMI32OffsetStoreNode fpOffsetStore,
                    @Cached LLVMPointerOffsetStoreNode overflowArgAreaStore,
                    @Cached LLVMPointerOffsetStoreNode regSaveAreaStore,
                    @Cached LLVMI64OffsetStoreNode i64RegSaveAreaStore,
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

        initNativeVAList(gpOffsetStore, fpOffsetStore, overflowArgAreaStore, regSaveAreaStore, vaListStackPtr, gpOffset, fpOffset, overflowArgAreaBaseNativePtr.increment(overflowArgArea.getOffset()),
                        regSaveAreaNativePtr);

        initNativeAreas(this.realArguments, this.numberOfExplicitArguments, this.initGPOffset, this.initFPOffset, regSaveAreaNativePtr, this.overflowArgAreaBaseNativePtr, i64RegSaveAreaStore,
                        i32RegSaveAreaStore, fp80bitRegSaveAreaStore, pointerRegSaveAreaStore, i64OverflowArgAreaStore, i32OverflowArgAreaStore, fp80bitOverflowArgAreaStore,
                        pointerOverflowArgAreaStore, memMove);
    }

    private void allocateNativeAreas(StackAllocationNode stackAllocationNode, Frame frame) {
        regSaveAreaNativePtr = stackAllocationNode.executeWithTarget(X86_64BitVarArgs.FP_LIMIT, frame);
        this.overflowArgAreaBaseNativePtr = stackAllocationNode.executeWithTarget(overflowArgArea.overflowAreaSize, frame);
    }

    private static void initNativeVAList(LLVMI32OffsetStoreNode gpOffsetStore, LLVMI32OffsetStoreNode fpOffsetStore, LLVMPointerOffsetStoreNode overflowArgAreaStore,
                    LLVMPointerOffsetStoreNode regSaveAreaStore, LLVMPointer vaListStackPtr, int gpOffset, int fpOffset, LLVMPointer overflowArgAreaNativePtr, LLVMPointer regSaveAreaNativePtr) {
        gpOffsetStore.executeWithTarget(vaListStackPtr, X86_64BitVarArgs.GP_OFFSET, gpOffset);
        fpOffsetStore.executeWithTarget(vaListStackPtr, X86_64BitVarArgs.FP_OFFSET, fpOffset);
        overflowArgAreaStore.executeWithTarget(vaListStackPtr, X86_64BitVarArgs.OVERFLOW_ARG_AREA, overflowArgAreaNativePtr);
        regSaveAreaStore.executeWithTarget(vaListStackPtr, X86_64BitVarArgs.REG_SAVE_AREA, regSaveAreaNativePtr);
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

    @GenerateUncached
    public abstract static class X86_64VAListPointerWrapperFactory extends VAListPointerWrapperFactory {

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
            return o instanceof LLVMX86_64VaListStorage;
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
    @ImportStatic(LLVMX86_64VaListStorage.class)
    public static final class NativeVAListWrapper {

        final LLVMPointer nativeVAListPtr;

        public NativeVAListWrapper(LLVMPointer nativeVAListPtr) {
            this.nativeVAListPtr = nativeVAListPtr;
        }

        @ExportMessage
        public void initialize(Object[] arguments, int numberOfExplicitArguments, Frame frame,
                        @Exclusive @Cached StackAllocationNode stackAllocationNode,
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
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    throw CompilerDirectives.shouldNotReachHere(String.valueOf(arg));
                }
            }

            LLVMPointer regSaveAreaNativePtr = stackAllocationNode.executeWithTarget(X86_64BitVarArgs.FP_LIMIT, frame);
            LLVMPointer overflowArgAreaBaseNativePtr = stackAllocationNode.executeWithTarget(overflowArea, frame);

            initNativeVAList(gpOffsetStore, fpOffsetStore, overflowArgAreaStore, regSaveAreaStore, nativeVAListPtr, initGPOffset, initFPOffset, overflowArgAreaBaseNativePtr, regSaveAreaNativePtr);
            initNativeAreas(arguments, numberOfExplicitArguments, initGPOffset, initFPOffset, regSaveAreaNativePtr, overflowArgAreaBaseNativePtr, i64RegSaveAreaStore, i32RegSaveAreaStore,
                            fp80bitRegSaveAreaStore, pointerRegSaveAreaStore, i64OverflowArgAreaStore, i32OverflowArgAreaStore, fp80bitOverflowArgAreaStore, pointerOverflowArgAreaStore, memMove);
        }

        @ExportMessage
        public void cleanup(@SuppressWarnings("unused") Frame frame) {
            // nop
        }

        @ExportMessage
        @ImportStatic({LLVMTypes.class, LLVMX86_64VaListStorage.class})
        static class Copy {

            @Specialization
            @GenerateAOT.Exclude // recursion cut
            static void copyToManaged(NativeVAListWrapper source, LLVMX86_64VaListStorage dest, Frame frame,
                            @CachedLibrary(limit = "1") LLVMVaListLibrary vaListLibrary) {
                vaListLibrary.copy(source, new NativeVAListWrapper(dest.vaListStackPtr), frame);
                dest.nativized = true;
            }

            @Specialization
            static void copyToNative(NativeVAListWrapper source, NativeVAListWrapper dest, @SuppressWarnings("unused") Frame frame,
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
                initNativeVAList(gpOffsetStore, fpOffsetStore, regSaveAreaStore, overflowAreaStore, dest.nativeVAListPtr, gp, fp, overflowSaveAreaPtr, regSaveAreaPtr);
            }
        }

        @SuppressWarnings("static-method")
        @ExportMessage
        public Object shift(@SuppressWarnings("unused") Type type, @SuppressWarnings("unused") Frame frame) {
            throw CompilerDirectives.shouldNotReachHere("TODO");
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
    @ExportLibrary(value = NativeTypeLibrary.class, useForAOT = true, useForAOTPriority = 1)
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

    @ExportLibrary(value = NativeTypeLibrary.class, useForAOT = true, useForAOTPriority = 1)
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
