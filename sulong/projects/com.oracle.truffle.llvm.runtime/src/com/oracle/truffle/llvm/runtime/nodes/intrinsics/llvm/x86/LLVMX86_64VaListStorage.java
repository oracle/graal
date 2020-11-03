/*
 * Copyright (c) 2020, Oracle and/or its affiliates.
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

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.CachedLanguage;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.FrameInstance.FrameAccess;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
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
import com.oracle.truffle.llvm.runtime.interop.LLVMDataEscapeNode.LLVMPointerDataEscapeNode;
import com.oracle.truffle.llvm.runtime.interop.access.LLVMInteropType;
import com.oracle.truffle.llvm.runtime.interop.access.LLVMInteropType.Array;
import com.oracle.truffle.llvm.runtime.library.internal.LLVMManagedReadLibrary;
import com.oracle.truffle.llvm.runtime.library.internal.LLVMManagedWriteLibrary;
import com.oracle.truffle.llvm.runtime.library.internal.LLVMNativeLibrary;
import com.oracle.truffle.llvm.runtime.memory.LLVMMemMoveNode;
import com.oracle.truffle.llvm.runtime.memory.VarargsAreaStackAllocationNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMHasDatalayoutNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMStoreNode;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.va.LLVMVAEnd;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.va.LLVMVAListNode;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.va.LLVMVAStart;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.va.LLVMVaListLibrary;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.x86.LLVMX86_64VaListStorageFactory.ByteConversionHelperNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.x86.LLVMX86_64VaListStorageFactory.IntegerConversionHelperNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.x86.LLVMX86_64VaListStorageFactory.LongConversionHelperNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.x86.LLVMX86_64VaListStorageFactory.PointerConversionHelperNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.x86.LLVMX86_64VaListStorageFactory.ShortConversionHelperNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.LLVMNativeVarargsAreaStackAllocationNode;
import com.oracle.truffle.llvm.runtime.nodes.memory.NativeProfiledMemMoveNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.store.LLVM80BitFloatStoreNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.store.LLVMI32StoreNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.store.LLVMI64StoreNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.store.LLVMPointerStoreNodeGen;
import com.oracle.truffle.llvm.runtime.pointer.LLVMManagedPointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMNativePointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMPointer;
import com.oracle.truffle.llvm.runtime.types.ArrayType;
import com.oracle.truffle.llvm.runtime.types.PointerType;
import com.oracle.truffle.llvm.runtime.types.PrimitiveType;
import com.oracle.truffle.llvm.runtime.types.StructureType;
import com.oracle.truffle.llvm.runtime.types.Type;
import com.oracle.truffle.llvm.runtime.types.VectorType;
import com.oracle.truffle.llvm.runtime.vector.LLVMDoubleVector;
import com.oracle.truffle.llvm.runtime.vector.LLVMFloatVector;
import com.oracle.truffle.llvm.spi.NativeTypeLibrary;

import java.util.ArrayList;
import java.util.Arrays;

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
@ExportLibrary(InteropLibrary.class)
@ExportLibrary(NativeTypeLibrary.class)
public final class LLVMX86_64VaListStorage implements TruffleObject {

    public static final ArrayType VA_LIST_TYPE = new ArrayType(StructureType.createNamedFromList("struct.__va_list_tag", false,
                    new ArrayList<>(Arrays.asList(PrimitiveType.I32, PrimitiveType.I32, PointerType.I8, PointerType.I8))), 1);

    private static final String GET_MEMBER = "get";

    private Object[] realArguments;
    private int numberOfExplicitArguments;

    private int initGPOffset;
    private int gpOffset;
    private int initFPOffset;
    private int fpOffset;

    private RegSaveArea regSaveArea;
    private LLVMPointer regSaveAreaPtr;
    private OverflowArgArea overflowArgArea;

    private LLVMPointer nativized;
    private LLVMPointer overflowArgAreaBaseNativePtr;

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

    // InteropLibrary implementation

    /*
     * The managed va_list can be accessed as an array, where the array elements correspond to the
     * varargs, i.e. the explicit arguments are excluded.
     *
     * Further, the managed va_list exposes one invokable member 'get(index, type)'. The index
     * argument identifies the argument in the va_list, while the type specifies the required type
     * of the returned argument. In the case of a pointer argument, the pointer is just exported
     * with the given type. For other argument types the appropriate conversion should be done
     * (TODO).
     */

    @SuppressWarnings("static-method")
    @ExportMessage
    public boolean hasMembers() {
        return true;
    }

    @ExportLibrary(InteropLibrary.class)
    static final class VAListMembers implements TruffleObject {

        @ExportMessage
        @SuppressWarnings("static-method")
        boolean hasArrayElements() {
            return true;
        }

        @ExportMessage
        @SuppressWarnings("static-method")
        long getArraySize() {
            return 1;
        }

        @ExportMessage
        @SuppressWarnings("static-method")
        boolean isArrayElementReadable(long index) {
            return index == 0;
        }

        @ExportMessage
        @SuppressWarnings("static-method")
        Object readArrayElement(long index) throws InvalidArrayIndexException {
            if (index == 0) {
                return "get";
            } else {
                throw InvalidArrayIndexException.create(index);
            }
        }
    }

    @SuppressWarnings("static-method")
    @ExportMessage
    public Object getMembers(@SuppressWarnings("unused") boolean includeInternal) {
        return new VAListMembers();
    }

    @SuppressWarnings("static-method")
    @ExportMessage
    public boolean isMemberInvocable(String member) {
        return GET_MEMBER.equals(member);
    }

    @ExportMessage
    public Object invokeMember(String member, Object[] arguments,
                    @Cached LLVMPointerDataEscapeNode pointerEscapeNode) throws ArityException, UnknownIdentifierException, UnsupportedTypeException {
        if (GET_MEMBER.equals(member)) {
            if (arguments.length == 2) {
                if (!(arguments[0] instanceof Integer)) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    throw UnsupportedTypeException.create(new Object[]{arguments[0]}, "Index argument must be an integer");
                }
                int i = (Integer) arguments[0];
                if (i >= realArguments.length - numberOfExplicitArguments) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    throw new ArrayIndexOutOfBoundsException(i);
                }

                if (!(arguments[1] instanceof LLVMInteropType.Structured)) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    throw UnsupportedTypeException.create(new Object[]{arguments[1]}, "Type argument must be an instance of LLVMInteropType.Structured");
                }
                LLVMInteropType.Structured type = (LLVMInteropType.Structured) arguments[1];

                Object arg = realArguments[numberOfExplicitArguments + i];
                if (!LLVMPointer.isInstance(arg)) {
                    // TODO: Do some conversion if the type in the 2nd argument does not match the
                    // arg's types
                    return arg;
                }
                LLVMPointer ptrArg = LLVMPointer.cast(arg);

                return pointerEscapeNode.executeWithType(ptrArg, type);

            } else {
                throw ArityException.create(2, arguments.length);
            }
        }
        throw UnknownIdentifierException.create(member);
    }

    @SuppressWarnings("static-method")
    @ExportMessage
    public boolean hasArrayElements() {
        return true;
    }

    @ExportMessage
    public long getArraySize() {
        return realArguments.length - numberOfExplicitArguments;
    }

    @ExportMessage
    public boolean isArrayElementReadable(long index) {
        return index < realArguments.length - numberOfExplicitArguments;
    }

    @ExportMessage
    public Object readArrayElement(long index) {
        return realArguments[(int) index + numberOfExplicitArguments];
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
        static int readNativeI32(LLVMX86_64VaListStorage vaList, long offset, @CachedLibrary(limit = "1") LLVMManagedReadLibrary nativeReadLibrary) {
            return nativeReadLibrary.readI32(vaList.nativized, offset);
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
        static LLVMPointer readNativePointer(LLVMX86_64VaListStorage vaList, long offset, @CachedLibrary(limit = "1") LLVMManagedReadLibrary nativeReadLibrary) {
            return nativeReadLibrary.readPointer(vaList.nativized, offset);
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
        static void writeNative(LLVMX86_64VaListStorage vaList, long offset, int value, @CachedLibrary(limit = "1") LLVMManagedWriteLibrary nativeWriteLibrary) {
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
        static void writeNative(LLVMX86_64VaListStorage vaList, long offset, LLVMPointer value, @CachedLibrary(limit = "1") LLVMManagedWriteLibrary nativeWriteLibrary) {
            nativeWriteLibrary.writePointer(vaList.nativized, offset, value);
        }
    }

    private enum VarArgArea {
        GP_AREA,
        FP_AREA,
        OVERFLOW_AREA;
    }

    private static VarArgArea getVarArgArea(Object arg) {
        if (arg instanceof Boolean) {
            return VarArgArea.GP_AREA;
        } else if (arg instanceof Byte) {
            return VarArgArea.GP_AREA;
        } else if (arg instanceof Short) {
            return VarArgArea.GP_AREA;
        } else if (arg instanceof Integer) {
            return VarArgArea.GP_AREA;
        } else if (arg instanceof Long) {
            return VarArgArea.GP_AREA;
        } else if (arg instanceof Float) {
            return VarArgArea.FP_AREA;
        } else if (arg instanceof Double) {
            return VarArgArea.FP_AREA;
        } else if (arg instanceof LLVMVarArgCompoundValue) {
            return VarArgArea.OVERFLOW_AREA;
        } else if (LLVMPointer.isInstance(arg)) {
            return VarArgArea.GP_AREA;
        } else if (arg instanceof LLVM80BitFloat) {
            return VarArgArea.OVERFLOW_AREA;
        } else if (arg instanceof LLVMFloatVector && ((LLVMFloatVector) arg).getLength() <= 2) {
            return VarArgArea.FP_AREA;
        } else {
            CompilerDirectives.transferToInterpreter();
            throw new AssertionError(arg);
        }
    }

    private static VarArgArea getVarArgArea(Type type) {
        if (type == PrimitiveType.I1) {
            return VarArgArea.GP_AREA;
        } else if (type == PrimitiveType.I8) {
            return VarArgArea.GP_AREA;
        } else if (type == PrimitiveType.I16) {
            return VarArgArea.GP_AREA;
        } else if (type == PrimitiveType.I32) {
            return VarArgArea.GP_AREA;
        } else if (type == PrimitiveType.I64) {
            return VarArgArea.GP_AREA;
        } else if (type == PrimitiveType.FLOAT) {
            return VarArgArea.FP_AREA;
        } else if (type == PrimitiveType.DOUBLE) {
            return VarArgArea.FP_AREA;
        } else if (type == PrimitiveType.X86_FP80) {
            return VarArgArea.OVERFLOW_AREA;
        } else if (isFloatVectorWithMaxTwoElems(type)) {
            return VarArgArea.FP_AREA;
        } else if (type instanceof PointerType) {
            return VarArgArea.GP_AREA;
        } else {
            return VarArgArea.OVERFLOW_AREA;
        }
    }

    @TruffleBoundary
    private static boolean isFloatVectorWithMaxTwoElems(Type type) {
        return type instanceof VectorType && getElementType(type) == PrimitiveType.FLOAT && ((VectorType) type).getNumberOfElements() <= 2;
    }

    @TruffleBoundary
    private static Type getElementType(Type type) {
        return ((VectorType) type).getElementType();
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

    @ExplodeLoop
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
                        @Cached(value = "create()", uncached = "create()") LLVMNativeVarargsAreaStackAllocationNode stackAllocationNode,
                        @Cached(value = "createI64StoreNode()", uncached = "createI64StoreNode()") LLVMStoreNode i64RegSaveAreaStore,
                        @Cached(value = "createI32StoreNode()", uncached = "createI32StoreNode()") LLVMStoreNode i32RegSaveAreaStore,
                        @Cached(value = "create80BitFloatStoreNode()", uncached = "create80BitFloatStoreNode()") LLVMStoreNode fp80bitRegSaveAreaStore,
                        @Cached(value = "createPointerStoreNode()", uncached = "createPointerStoreNode()") LLVMStoreNode pointerRegSaveAreaStore,
                        @Cached(value = "createI64StoreNode()", uncached = "createI64StoreNode()") LLVMStoreNode i64OverflowArgAreaStore,
                        @Cached(value = "createI32StoreNode()", uncached = "createI32StoreNode()") LLVMStoreNode i32OverflowArgAreaStore,
                        @Cached(value = "create80BitFloatStoreNode()", uncached = "create80BitFloatStoreNode()") LLVMStoreNode fp80bitOverflowArgAreaStore,
                        @Cached(value = "createPointerStoreNode()", uncached = "createPointerStoreNode()") LLVMStoreNode pointerOverflowArgAreaStore,
                        @Cached(value = "createI32StoreNode()", uncached = "createI32StoreNode()") LLVMStoreNode gpOffsetStore,
                        @Cached(value = "createI32StoreNode()", uncached = "createI32StoreNode()") LLVMStoreNode fpOffsetStore,
                        @Cached(value = "createPointerStoreNode()", uncached = "createPointerStoreNode()") LLVMStoreNode overflowArgAreaStore,
                        @Cached(value = "createPointerStoreNode()", uncached = "createPointerStoreNode()") LLVMStoreNode regSaveAreaStore,
                        @Cached(value = "createMemMoveNode()", uncached = "createMemMoveNode()") LLVMMemMoveNode memMove) {
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
        static void copyManaged(LLVMX86_64VaListStorage source, LLVMX86_64VaListStorage dest, @SuppressWarnings("unused") int numberOfExplicitArguments) {
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
        static void copyNative(LLVMX86_64VaListStorage source, LLVMX86_64VaListStorage dest, int numberOfExplicitArguments, @CachedLibrary("source") LLVMManagedReadLibrary srcReadLib) {

            // The destination va_list will be in the managed state, even if the source has been
            // nativized. We need to read some state from the native memory, though.

            copyManaged(source, dest, numberOfExplicitArguments);

            dest.fpOffset = srcReadLib.readI32(source, X86_64BitVarArgs.FP_OFFSET);
            dest.gpOffset = srcReadLib.readI32(source, X86_64BitVarArgs.GP_OFFSET);
            dest.overflowArgArea.setOffset(getArgPtrFromNativePtr(source, srcReadLib));
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

    @SuppressWarnings("static-method")
    VarargsAreaStackAllocationNode createVarargsAreaStackAllocationNode(LLVMContext llvmCtx) {
        DataLayout dataLayout = getDataLayout();
        return llvmCtx.getLanguage().getActiveConfiguration().createNodeFactory(llvmCtx.getLanguage(), dataLayout).createVarargsAreaStackAllocation();
    }

    private static DataLayout getDataLayout() {
        RootCallTarget rootCallTarget = (RootCallTarget) Truffle.getRuntime().getCurrentFrame().getCallTarget();
        DataLayout dataLayout = (((LLVMHasDatalayoutNode) rootCallTarget.getRootNode())).getDatalayout();
        return dataLayout;
    }

    public static LLVMStoreNode createI64StoreNode() {
        return LLVMI64StoreNodeGen.create(null, null);
    }

    public static LLVMStoreNode createI32StoreNode() {
        return LLVMI32StoreNodeGen.create(null, null);
    }

    public static LLVMStoreNode create80BitFloatStoreNode() {
        return LLVM80BitFloatStoreNodeGen.create(null, null);
    }

    public static LLVMStoreNode createPointerStoreNode() {
        return LLVMPointerStoreNodeGen.create(null, null);
    }

    public static LLVMMemMoveNode createMemMoveNode() {
        return NativeProfiledMemMoveNodeGen.create();
    }

    @SuppressWarnings("static-method")
    @ExportMessage
    @TruffleBoundary
    void toNative(@SuppressWarnings("unused") @CachedLanguage() LLVMLanguage language,
                    @Cached(value = "this.createAllocaNode(language)", uncached = "this.createAllocaNode(language)") LLVMExpressionNode allocaNode,
                    @Cached(value = "create()", uncached = "create()") LLVMNativeVarargsAreaStackAllocationNode stackAllocationNode,
                    @Cached(value = "createI64StoreNode()", uncached = "createI64StoreNode()") LLVMStoreNode i64RegSaveAreaStore,
                    @Cached(value = "createI32StoreNode()", uncached = "createI32StoreNode()") LLVMStoreNode i32RegSaveAreaStore,
                    @Cached(value = "create80BitFloatStoreNode()", uncached = "create80BitFloatStoreNode()") LLVMStoreNode fp80bitRegSaveAreaStore,
                    @Cached(value = "createPointerStoreNode()", uncached = "createPointerStoreNode()") LLVMStoreNode pointerRegSaveAreaStore,
                    @Cached(value = "createI64StoreNode()", uncached = "createI64StoreNode()") LLVMStoreNode i64OverflowArgAreaStore,
                    @Cached(value = "createI32StoreNode()", uncached = "createI32StoreNode()") LLVMStoreNode i32OverflowArgAreaStore,
                    @Cached(value = "create80BitFloatStoreNode()", uncached = "create80BitFloatStoreNode()") LLVMStoreNode fp80bitOverflowArgAreaStore,
                    @Cached(value = "createPointerStoreNode()", uncached = "createPointerStoreNode()") LLVMStoreNode pointerOverflowArgAreaStore,
                    @Cached(value = "createI32StoreNode()", uncached = "createI32StoreNode()") LLVMStoreNode gpOffsetStore,
                    @Cached(value = "createI32StoreNode()", uncached = "createI32StoreNode()") LLVMStoreNode fpOffsetStore,
                    @Cached(value = "createPointerStoreNode()", uncached = "createPointerStoreNode()") LLVMStoreNode overflowArgAreaStore,
                    @Cached(value = "createPointerStoreNode()", uncached = "createPointerStoreNode()") LLVMStoreNode regSaveAreaStore,
                    @Cached(value = "createMemMoveNode()", uncached = "createMemMoveNode()") LLVMMemMoveNode memMove,
                    @Cached BranchProfile nativizedProfile) {

        if (nativized != null) {
            nativizedProfile.enter();
            return;
        }

        // N.B. Using FrameAccess.READ_WRITE may lead to throwing NPE, nevertheless using the safe
        // FrameAccess.READ_ONLY is not sufficient as some nodes below need to write to the frame.
        // Therefore toNative is put behind the Truffle boundary and FrameAccess.MATERIALIZE is
        // used as a workaround.
        VirtualFrame frame = (VirtualFrame) Truffle.getRuntime().getCurrentFrame().getFrame(FrameAccess.MATERIALIZE);
        nativized = LLVMPointer.cast(allocaNode.executeGeneric(frame));

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

    private LLVMPointer allocateNativeAreas(VarargsAreaStackAllocationNode stackAllocationNode, LLVMStoreNode gpOffsetStore, LLVMStoreNode fpOffsetStore, LLVMStoreNode overflowArgAreaStore,
                    LLVMStoreNode regSaveAreaStore, VirtualFrame frame) {
        LLVMPointer regSaveAreaNativePtr = stackAllocationNode.executeWithTarget(frame, X86_64BitVarArgs.FP_LIMIT);
        this.overflowArgAreaBaseNativePtr = stackAllocationNode.executeWithTarget(frame, overflowArgArea.overflowAreaSize);

        Object p = nativized.increment(X86_64BitVarArgs.GP_OFFSET);
        gpOffsetStore.executeWithTarget(p, gpOffset);

        p = nativized.increment(X86_64BitVarArgs.FP_OFFSET);
        fpOffsetStore.executeWithTarget(p, fpOffset);

        p = nativized.increment(X86_64BitVarArgs.OVERFLOW_ARG_AREA);
        overflowArgAreaStore.executeWithTarget(p, overflowArgAreaBaseNativePtr.increment(overflowArgArea.getOffset()));

        p = nativized.increment(X86_64BitVarArgs.REG_SAVE_AREA);
        regSaveAreaStore.executeWithTarget(p, regSaveAreaNativePtr);
        return regSaveAreaNativePtr;
    }

    /**
     * Reconstruct native register_save_area and overflow_arg_area according to AMD64 ABI.
     */
    private static void initNativeAreas(Object[] realArguments, int numberOfExplicitArguments, int initGPOffset, int initFPOffset, LLVMPointer regSaveAreaNativePtr,
                    LLVMPointer overflowArgAreaBaseNativePtr,
                    LLVMStoreNode i64RegSaveAreaStore,
                    LLVMStoreNode i32RegSaveAreaStore,
                    LLVMStoreNode fp80bitRegSaveAreaStore,
                    LLVMStoreNode pointerRegSaveAreaStore,
                    LLVMStoreNode i64OverflowArgAreaStore,
                    LLVMStoreNode i32OverflowArgAreaStore,
                    LLVMStoreNode fp80bitOverflowArgAreaStore,
                    LLVMStoreNode pointerOverflowArgAreaStore,
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
                                    fp80bitRegSaveAreaStore, pointerRegSaveAreaStore, object);
                    gp += X86_64BitVarArgs.GP_STEP;
                } else if (area == VarArgArea.FP_AREA && fp < X86_64BitVarArgs.FP_LIMIT) {
                    storeArgument(regSaveAreaNativePtr, fp, memMove, i64RegSaveAreaStore, i32RegSaveAreaStore,
                                    fp80bitRegSaveAreaStore, pointerRegSaveAreaStore, object);
                    fp += X86_64BitVarArgs.FP_STEP;
                } else {
                    overflowOffset += storeArgument(overflowArgAreaBaseNativePtr, overflowOffset, memMove,
                                    i64OverflowArgAreaStore, i32OverflowArgAreaStore,
                                    fp80bitOverflowArgAreaStore, pointerOverflowArgAreaStore, object);
                }
            }
        }
    }

    private static long storeArgument(LLVMPointer ptr, long offset, LLVMMemMoveNode memmove, LLVMStoreNode storeI64Node,
                    LLVMStoreNode storeI32Node, LLVMStoreNode storeFP80Node, LLVMStoreNode storePointerNode, Object object) {
        if (object instanceof Number) {
            return doPrimitiveWrite(ptr, offset, storeI64Node, object);
        } else if (object instanceof LLVMVarArgCompoundValue) {
            LLVMVarArgCompoundValue obj = (LLVMVarArgCompoundValue) object;
            Object currentPtr = ptr.increment(offset);
            memmove.executeWithTarget(currentPtr, obj.getAddr(), obj.getSize());
            return obj.getSize();
        } else if (LLVMPointer.isInstance(object)) {
            Object currentPtr = ptr.increment(offset);
            storePointerNode.executeWithTarget(currentPtr, object);
            return X86_64BitVarArgs.STACK_STEP;
        } else if (object instanceof LLVM80BitFloat) {
            Object currentPtr = ptr.increment(offset);
            storeFP80Node.executeWithTarget(currentPtr, object);
            return 16;
        } else if (object instanceof LLVMFloatVector) {
            final LLVMFloatVector floatVec = (LLVMFloatVector) object;
            for (int i = 0; i < floatVec.getLength(); i++) {
                Object currentPtr = ptr.increment(offset + i * Float.BYTES);
                storeI32Node.executeWithTarget(currentPtr, Float.floatToIntBits(floatVec.getValue(i)));
            }
            return floatVec.getLength() * Float.BYTES;
        } else {
            CompilerDirectives.transferToInterpreter();
            throw new AssertionError(object);
        }
    }

    private static int doPrimitiveWrite(LLVMPointer ptr, long offset, LLVMStoreNode storeNode, Object arg) throws AssertionError {
        Object currentPtr = ptr.increment(offset);
        long value;
        if (arg instanceof Boolean) {
            value = ((boolean) arg) ? 1L : 0L;
        } else if (arg instanceof Byte) {
            value = Integer.toUnsignedLong((byte) arg);
        } else if (arg instanceof Short) {
            value = Integer.toUnsignedLong((short) arg);
        } else if (arg instanceof Integer) {
            value = Integer.toUnsignedLong((int) arg);
        } else if (arg instanceof Long) {
            value = (long) arg;
        } else if (arg instanceof Float) {
            value = Integer.toUnsignedLong(Float.floatToIntBits((float) arg));
        } else if (arg instanceof Double) {
            value = Double.doubleToRawLongBits((double) arg);
        } else {
            CompilerDirectives.transferToInterpreter();
            throw new AssertionError(arg);
        }
        storeNode.executeWithTarget(currentPtr, value);
        return X86_64BitVarArgs.STACK_STEP;
    }

    @ExportMessage
    boolean isPointer() {
        return nativized != null && LLVMNativePointer.isInstance(nativized);
    }

    @ExportMessage
    long asPointer() {
        return nativized == null ? 0L : LLVMNativePointer.cast(nativized).asNative();
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

        public NativeVAListWrapper(LLVMNativePointer nativeVAListPtr) {
            this.nativeVAListPtr = nativeVAListPtr;
        }

        @ExportMessage
        public void initialize(Object[] arguments, int numberOfExplicitArguments,
                        @Cached(value = "create()", uncached = "create()") LLVMNativeVarargsAreaStackAllocationNode stackAllocationNode,
                        @Cached(value = "createI32StoreNode()", uncached = "createI32StoreNode()") LLVMStoreNode gpOffsetStore,
                        @Cached(value = "createI32StoreNode()", uncached = "createI32StoreNode()") LLVMStoreNode fpOffsetStore,
                        @Cached(value = "createI64StoreNode()", uncached = "createI64StoreNode()") LLVMStoreNode i64RegSaveAreaStore,
                        @Cached(value = "createI32StoreNode()", uncached = "createI32StoreNode()") LLVMStoreNode i32RegSaveAreaStore,
                        @Cached(value = "create80BitFloatStoreNode()", uncached = "create80BitFloatStoreNode()") LLVMStoreNode fp80bitRegSaveAreaStore,
                        @Cached(value = "createPointerStoreNode()", uncached = "createPointerStoreNode()") LLVMStoreNode pointerRegSaveAreaStore,
                        @Cached(value = "createI64StoreNode()", uncached = "createI64StoreNode()") LLVMStoreNode i64OverflowArgAreaStore,
                        @Cached(value = "createI32StoreNode()", uncached = "createI32StoreNode()") LLVMStoreNode i32OverflowArgAreaStore,
                        @Cached(value = "create80BitFloatStoreNode()", uncached = "create80BitFloatStoreNode()") LLVMStoreNode fp80bitOverflowArgAreaStore,
                        @Cached(value = "createPointerStoreNode()", uncached = "createPointerStoreNode()") LLVMStoreNode pointerOverflowArgAreaStore,
                        @Cached(value = "createPointerStoreNode()", uncached = "createPointerStoreNode()") LLVMStoreNode overflowArgAreaStore,
                        @Cached(value = "createPointerStoreNode()", uncached = "createPointerStoreNode()") LLVMStoreNode regSaveAreaStore,
                        @Cached(value = "createMemMoveNode()", uncached = "createMemMoveNode()") LLVMMemMoveNode memMove) {

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

            LLVMPointer regSaveAreaNativePtr = stackAllocationNode.executeWithTarget(frame,
                            X86_64BitVarArgs.FP_LIMIT);
            LLVMPointer overflowArgAreaBaseNativePtr = LLVMNativePointer.cast(stackAllocationNode.executeWithTarget(frame, overflowArea));

            Object p = nativeVAListPtr.increment(X86_64BitVarArgs.GP_OFFSET);
            gpOffsetStore.executeWithTarget(p, initGPOffset);

            p = nativeVAListPtr.increment(X86_64BitVarArgs.FP_OFFSET);
            fpOffsetStore.executeWithTarget(p, initFPOffset);

            p = nativeVAListPtr.increment(X86_64BitVarArgs.OVERFLOW_ARG_AREA);
            overflowArgAreaStore.executeWithTarget(p, overflowArgAreaBaseNativePtr);

            p = nativeVAListPtr.increment(X86_64BitVarArgs.REG_SAVE_AREA);
            regSaveAreaStore.executeWithTarget(p, regSaveAreaNativePtr);

            initNativeAreas(arguments, numberOfExplicitArguments, initGPOffset, initFPOffset, regSaveAreaNativePtr, overflowArgAreaBaseNativePtr, i64RegSaveAreaStore, i32RegSaveAreaStore,
                            fp80bitRegSaveAreaStore, pointerRegSaveAreaStore, i64OverflowArgAreaStore, i32OverflowArgAreaStore, fp80bitOverflowArgAreaStore, pointerOverflowArgAreaStore, memMove);
        }

        @ExportMessage
        public void cleanup() {
            // nop
        }

        @SuppressWarnings("static-method")
        @ExportMessage
        public void copy(@SuppressWarnings("unused") Object destVaList, @SuppressWarnings("unused") int numberOfExplicitArguments) {
            CompilerDirectives.transferToInterpreter();
            throw new UnsupportedOperationException("TODO");
        }

        @SuppressWarnings("static-method")
        @ExportMessage
        public Object shift(@SuppressWarnings("unused") Type type) {
            CompilerDirectives.transferToInterpreter();
            throw new UnsupportedOperationException("TODO");
        }
    }

    /**
     * The abstraction for the two special areas in the va_list structure. Both, the reg_save_area
     * and overflow_area descendants have an array of arguments that is indirectly addressable by
     * offset. The conversion between the offset and the array index is calculated by the abstract
     * <code>offsetToIndex</code> method, which is called from the common implementation of
     * {@link LLVMManagedReadLibrary}.
     */
    @ExportLibrary(LLVMManagedReadLibrary.class)
    public abstract static class ArgsArea implements TruffleObject {
        final Object[] args;

        ArgsArea(Object[] args) {
            this.args = args;
        }

        /**
         * @param offset the offset of the argument. The offset may exceed the offset of the
         *            corresponding argument. When it happens, the remainder offset is encoded in
         *            the higher 4 bytes of the returned value.
         * @return -1 if there is no argument for the given offset or the argument index encoded in
         *         the lower 4 bytes and the remainder offset encoded in the higher 4 bytes.
         */
        protected abstract long offsetToIndex(long offset);

        @ExportMessage
        @SuppressWarnings("static-method")
        boolean isReadable() {
            return true;
        }

        @ExportMessage
        byte readI8(long offset, @Cached ByteConversionHelperNode convNode) {
            long n = offsetToIndex(offset);
            int i = (int) ((n << 32) >> 32);
            int j = (int) (n >> 32);
            if (i < 0) {
                return 0;
            } else {
                return (Byte) convNode.execute(args[i], j);
            }
        }

        @ExportMessage
        short readI16(long offset, @Cached ShortConversionHelperNode convNode) {
            long n = offsetToIndex(offset);
            int i = (int) ((n << 32) >> 32);
            int j = (int) (n >> 32);
            if (i < 0) {
                return 0;
            } else {
                return (Short) convNode.execute(args[i], j);
            }
        }

        @ExportMessage
        int readI32(long offset, @Cached IntegerConversionHelperNode convNode) {
            long n = offsetToIndex(offset);
            int i = (int) ((n << 32) >> 32);
            int j = (int) (n >> 32);
            if (i < 0) {
                return 0;
            } else {
                return (Integer) convNode.execute(args[i], j);
            }
        }

        @ExportMessage
        LLVMPointer readPointer(long offset, @Cached PointerConversionHelperNode convNode) {
            long n = offsetToIndex(offset);
            int i = (int) ((n << 32) >> 32);
            int j = (int) (n >> 32);
            return i < 0 ? LLVMNativePointer.createNull() : LLVMPointer.cast(convNode.execute(args[i], j));
        }

        @ExportMessage
        Object readGenericI64(long offset, @Cached LongConversionHelperNode convNode) {
            long n = offsetToIndex(offset);
            int i = (int) ((n << 32) >> 32);
            int j = (int) (n >> 32);
            return i < 0 ? Double.doubleToLongBits(0d) : convNode.execute(args[i], j);
        }

        private static final Array I8_ARG_TYPE = LLVMInteropType.ValueKind.I8.type.toArray(8);
        private static final Array I16_ARG_TYPE = LLVMInteropType.ValueKind.I16.type.toArray(4);
        private static final Array I32_ARG_TYPE = LLVMInteropType.ValueKind.I32.type.toArray(2);
        private static final Array I64_ARG_TYPE = LLVMInteropType.ValueKind.I64.type.toArray(1);
        private static final Array F80_ARG_TYPE = LLVMInteropType.ValueKind.I64.type.toArray(2);

        protected static LLVMInteropType getVarArgType(Object arg) {
            if (arg == null) {
                return LLVMInteropType.ValueKind.I8.type.toArray(0);
            } else if (arg instanceof Boolean) {
                return I8_ARG_TYPE;
            } else if (arg instanceof Byte) {
                return I8_ARG_TYPE;
            } else if (arg instanceof Short) {
                return I16_ARG_TYPE;
            } else if (arg instanceof Integer) {
                return I32_ARG_TYPE;
            } else if (arg instanceof Long) {
                return I64_ARG_TYPE;
            } else if (arg instanceof Float) {
                return I32_ARG_TYPE;
            } else if (arg instanceof Double) {
                return I64_ARG_TYPE;
            } else if (LLVMPointer.isInstance(arg)) {
                return I64_ARG_TYPE;
            } else if (arg instanceof LLVMFloatVector && ((LLVMFloatVector) arg).getLength() <= 2) {
                return I32_ARG_TYPE;
            } else if (arg instanceof LLVMVarArgCompoundValue) {
                LLVMVarArgCompoundValue compVal = (LLVMVarArgCompoundValue) arg;
                return LLVMInteropType.ValueKind.I64.type.toArray(compVal.getSize() / 8);
            } else if (arg instanceof LLVM80BitFloat) {
                return F80_ARG_TYPE;
            } else {
                CompilerDirectives.transferToInterpreter();
                throw new AssertionError(arg);
            }
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
    @ExportLibrary(LLVMManagedReadLibrary.class)
    @ExportLibrary(NativeTypeLibrary.class)
    public static final class RegSaveArea extends ArgsArea {

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

    /**
     * The class of the managed <code>overflow_area</code> in <code>va_list</code>. In contrast to
     * the <code>RegSaveArea</code>, the arguments array contains only the arguments that are to be
     * stored in the <code>overflow_area</code>. The <code>offsets</code> array serves to map
     * offsets to the indices of that array.
     */
    @ExportLibrary(LLVMManagedReadLibrary.class)
    @ExportLibrary(NativeTypeLibrary.class)
    public static final class OverflowArgArea extends ArgsArea implements Cloneable {
        private final long[] offsets;
        final int overflowAreaSize;

        private long currentOffset;

        OverflowArgArea(Object[] args, long[] offsets, int overflowAreaSize) {
            super(args);
            this.overflowAreaSize = overflowAreaSize;
            this.offsets = offsets;
            this.currentOffset = offsets[0];
        }

        @Override
        protected long offsetToIndex(long offset) {
            if (offset < 0) {
                return -1;
            }

            for (int i = 0; i < offsets.length; i++) {
                // The offsets array has the same length as the real arguments, however, it is
                // rarely fully filled. The unused elements are initialized to -1.
                if (offsets[i] < 0) {
                    // The input offset points beyond the last element of the offsets array
                    if (offset < overflowAreaSize) {
                        // and it is still within the boundary of the overflow area.
                        long j = offset - offsets[i - 1];
                        return (i - 1) + (j << 32);
                    } else {
                        // and it is outside the overflow area boundary. We return -1 as an
                        // indication of that fact.
                        return -1;
                    }
                }
                if (offset == offsets[i]) {
                    // The input offset aligns with the i-th calculated offset, so just return the
                    // index.
                    return i;
                }
                if (offset < offsets[i]) {
                    assert i > 0;
                    long j = offset - offsets[i - 1];
                    return (i - 1) + (j << 32);
                }
            }
            int i = offsets.length - 1;
            long j = offset - offsets[i];
            return i + (j << 32);
        }

        void shift(int steps) {
            long n = offsetToIndex(currentOffset);
            int i = (int) ((n << 32) >> 32);
            currentOffset = offsets[i + steps];
        }

        void setOffset(long newOffset) {
            currentOffset = newOffset;
        }

        Object getCurrentArg() {
            long n = offsetToIndex(currentOffset);
            int i = (int) ((n << 32) >> 32);
            return i < 0 ? null : args[i];
        }

        int getCurrentArgIndex() {
            long n = offsetToIndex(currentOffset);
            int i = (int) ((n << 32) >> 32);
            return i;
        }

        LLVMManagedPointer getCurrentArgPtr() {
            return LLVMManagedPointer.create(this, currentOffset);
        }

        long getOffset() {
            return currentOffset;
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

    abstract static class NumberConversionHelperNode extends LLVMNode {

        abstract Object execute(Object x, int offset);

        static boolean isNativePointer(Object x) {
            return LLVMNativePointer.isInstance(x);
        }

        static boolean isManagedPointer(Object x) {
            return LLVMManagedPointer.isInstance(x);
        }

    }

    @GenerateUncached
    abstract static class ByteConversionHelperNode extends NumberConversionHelperNode {

        @Specialization
        byte byteConversion(Byte x, @SuppressWarnings("unused") int offset) {
            assert offset == 0;
            return x;
        }

        @Specialization
        byte shortConversion(Short x, int offset, @Cached("createBinaryProfile()") ConditionProfile conditionProfile) {
            if (conditionProfile.profile(offset == 0)) {
                return x.byteValue();
            } else {
                assert offset == 1 : "Illegal short offset " + offset;
                return (byte) (x >> 8);
            }
        }

        @Specialization
        byte intConversion(Integer x, int offset,
                        @Cached("createBinaryProfile()") ConditionProfile conditionProfile1,
                        @Cached("createBinaryProfile()") ConditionProfile conditionProfile2) {
            if (conditionProfile1.profile(offset < 2)) {
                return shortConversion(x.shortValue(), offset, conditionProfile2);
            } else {
                return shortConversion((short) (x >> 16), offset % 2, conditionProfile2);
            }
        }

        @Specialization
        byte longConversion(Long x, int offset,
                        @Cached("createBinaryProfile()") ConditionProfile conditionProfile1,
                        @Cached("createBinaryProfile()") ConditionProfile conditionProfile2,
                        @Cached("createBinaryProfile()") ConditionProfile conditionProfile3) {
            if (conditionProfile1.profile(offset < 4)) {
                return intConversion(x.intValue(), offset, conditionProfile2, conditionProfile3);
            } else {
                return intConversion((int) (x >> 32), offset % 4, conditionProfile2, conditionProfile3);
            }
        }

        @Specialization
        byte doubleConversion(Double x, int offset,
                        @Cached("createBinaryProfile()") ConditionProfile conditionProfile1,
                        @Cached("createBinaryProfile()") ConditionProfile conditionProfile2,
                        @Cached("createBinaryProfile()") ConditionProfile conditionProfile3) {
            return longConversion(Double.doubleToLongBits(x), offset, conditionProfile1, conditionProfile2, conditionProfile3);
        }

        @Specialization
        byte float80Conversion(LLVM80BitFloat x, int offset) {
            assert offset < 10;
            return x.getBytes()[offset];
        }

        @Specialization
        byte floatVectorConversion(LLVMFloatVector x, int offset,
                        @Cached("createBinaryProfile()") ConditionProfile conditionProfile1,
                        @Cached("createBinaryProfile()") ConditionProfile conditionProfile2) {
            int index = offset / 4;
            assert index < x.getLength();
            int fi = Float.floatToIntBits((Float) x.getElement(index));
            return intConversion(fi, offset, conditionProfile1, conditionProfile2);
        }

        @Specialization(guards = "isNativePointer(x.getAddr())")
        byte compoundObjectConversionNative(LLVMVarArgCompoundValue x, int offset, @CachedLibrary(limit = "1") LLVMManagedReadLibrary compObjReadLib) {
            return compObjReadLib.readI8(x.getAddr(), offset);
        }

        @Specialization(guards = "!isNativePointer(x.getAddr())")
        byte compoundObjectConversionManaged(LLVMVarArgCompoundValue x, int offset, @CachedLibrary(limit = "1") LLVMManagedReadLibrary compObjReadLib) {
            LLVMManagedPointer ptr = LLVMManagedPointer.cast(x.getAddr());
            return compObjReadLib.readI8(ptr.getObject(), offset);
        }

        @Specialization(guards = "isNativePointer(x)")
        byte nativePointerObjectConversion(LLVMNativePointer x, int offset,
                        @Cached("createBinaryProfile()") ConditionProfile conditionProfile1,
                        @Cached("createBinaryProfile()") ConditionProfile conditionProfile2,
                        @Cached("createBinaryProfile()") ConditionProfile conditionProfile3) {
            return longConversion(x.asNative(), offset, conditionProfile1, conditionProfile2, conditionProfile3);
        }

        @Specialization(guards = "!isNativePointer(x)")
        byte managedPointerObjectConversion(LLVMManagedPointer x, int offset,
                        @CachedLibrary(limit = "1") LLVMNativeLibrary nativeLib,
                        @Cached("createBinaryProfile()") ConditionProfile conditionProfile1,
                        @Cached("createBinaryProfile()") ConditionProfile conditionProfile2,
                        @Cached("createBinaryProfile()") ConditionProfile conditionProfile3) {
            LLVMNativePointer nativePointer = nativeLib.toNativePointer(x.getObject());
            return nativePointerObjectConversion(nativePointer, offset, conditionProfile1, conditionProfile2, conditionProfile3);
        }

        static ByteConversionHelperNode create() {
            return ByteConversionHelperNodeGen.create();
        }

    }

    @GenerateUncached
    abstract static class ShortConversionHelperNode extends NumberConversionHelperNode {

        @Specialization
        short byteConversion(Byte x, @SuppressWarnings("unused") int offset) {
            assert offset == 0;
            return x.shortValue();
        }

        @Specialization
        short shortConversion(Short x, @SuppressWarnings("unused") int offset) {
            assert offset == 0;
            return x;
        }

        @Specialization
        short intConversion(Integer x, int offset, @Cached("createBinaryProfile()") ConditionProfile conditionProfile) {
            if (conditionProfile.profile(offset == 0)) {
                return x.shortValue();
            } else {
                assert offset == 2 : "Illegal integer offset " + offset;
                return (short) (x >> 16);
            }
        }

        @Specialization
        short longConversion(Long x, int offset,
                        @Cached("createBinaryProfile()") ConditionProfile conditionProfile1,
                        @Cached("createBinaryProfile()") ConditionProfile conditionProfile2) {

            if (conditionProfile1.profile(offset < 4)) {
                return intConversion(x.intValue(), offset, conditionProfile2);
            } else {
                return intConversion((int) (x >> 32), offset % 4, conditionProfile2);
            }
        }

        @Specialization
        short doubleConversion(Double x, int offset,
                        @Cached("createBinaryProfile()") ConditionProfile conditionProfile1,
                        @Cached("createBinaryProfile()") ConditionProfile conditionProfile2) {
            return longConversion(Double.doubleToLongBits(x), offset, conditionProfile1, conditionProfile2);
        }

        @Specialization
        short floatVectorConversion(LLVMFloatVector x, int offset,
                        @Cached("createBinaryProfile()") ConditionProfile conditionProfile) {
            int index = offset / 4;
            assert index < x.getLength();
            int fi = Float.floatToIntBits((Float) x.getElement(index));
            return intConversion(fi, offset, conditionProfile);
        }

        @Specialization(guards = "isNativePointer(x.getAddr())")
        short compoundObjectConversion(LLVMVarArgCompoundValue x, int offset, @CachedLibrary(limit = "1") LLVMManagedReadLibrary compObjReadLib) {
            return compObjReadLib.readI16(x.getAddr(), offset);
        }

        @Specialization(guards = "!isNativePointer(x.getAddr())")
        short compoundObjectConversionManaged(LLVMVarArgCompoundValue x, int offset, @CachedLibrary(limit = "1") LLVMManagedReadLibrary compObjReadLib) {
            LLVMManagedPointer ptr = LLVMManagedPointer.cast(x.getAddr());
            return compObjReadLib.readI16(ptr.getObject(), offset);
        }

        @Specialization(guards = "isNativePointer(x)")
        short nativePointerObjectConversion(LLVMNativePointer x, int offset,
                        @Cached("createBinaryProfile()") ConditionProfile conditionProfile1,
                        @Cached("createBinaryProfile()") ConditionProfile conditionProfile2) {
            return longConversion(x.asNative(), offset, conditionProfile1, conditionProfile2);
        }

        @Specialization(guards = "!isNativePointer(x)")
        short managedPointerObjectConversion(LLVMManagedPointer x, int offset,
                        @CachedLibrary(limit = "1") LLVMNativeLibrary nativeLib,
                        @Cached("createBinaryProfile()") ConditionProfile conditionProfile1,
                        @Cached("createBinaryProfile()") ConditionProfile conditionProfile2) {
            LLVMNativePointer nativePointer = nativeLib.toNativePointer(x.getObject());
            return nativePointerObjectConversion(nativePointer, offset, conditionProfile1, conditionProfile2);
        }

        static ShortConversionHelperNode create() {
            return ShortConversionHelperNodeGen.create();
        }
    }

    @GenerateUncached
    abstract static class IntegerConversionHelperNode extends NumberConversionHelperNode {

        @Specialization
        int byteConversion(Byte x, @SuppressWarnings("unused") int offset) {
            assert offset == 0;
            return x.intValue();
        }

        @Specialization
        int shortConversion(Short x, @SuppressWarnings("unused") int offset) {
            assert offset == 0;
            return x.intValue();
        }

        @Specialization
        int intConversion(Integer x, @SuppressWarnings("unused") int offset) {
            assert offset == 0;
            return x;
        }

        @Specialization
        int longConversion(Long x, int offset, @Cached("createBinaryProfile()") ConditionProfile conditionProfile) {
            if (conditionProfile.profile(offset == 0)) {
                return x.intValue();
            } else {
                assert offset == 4 : "Illegal long offset " + offset;
                return (int) (x >> 32);
            }
        }

        @Specialization
        int doubleConversion(Double x, int offset,
                        @Cached("createBinaryProfile()") ConditionProfile conditionProfile) {
            return longConversion(Double.doubleToLongBits(x), offset, conditionProfile);
        }

        @Specialization
        int floatVectorConversion(LLVMFloatVector x, int offset) {
            int index = offset / 4;
            assert index < x.getLength();
            return Float.floatToIntBits((Float) x.getElement(index));
        }

        @Specialization(guards = "isNativePointer(x.getAddr())")
        int compoundObjectConversion(LLVMVarArgCompoundValue x, int offset, @CachedLibrary(limit = "1") LLVMManagedReadLibrary compObjReadLib) {
            return compObjReadLib.readI32(x.getAddr(), offset);
        }

        @Specialization(guards = "!isNativePointer(x.getAddr())")
        int compoundObjectConversionManaged(LLVMVarArgCompoundValue x, int offset, @CachedLibrary(limit = "1") LLVMManagedReadLibrary compObjReadLib) {
            LLVMManagedPointer ptr = LLVMManagedPointer.cast(x.getAddr());
            return compObjReadLib.readI32(ptr.getObject(), offset);
        }

        @Specialization(guards = "isNativePointer(x)")
        int nativePointerObjectConversion(LLVMNativePointer x, int offset,
                        @Cached("createBinaryProfile()") ConditionProfile conditionProfile) {
            return longConversion(LLVMNativePointer.cast(x).asNative(), offset, conditionProfile);
        }

        @Specialization(guards = "!isNativePointer(x)")
        int managedPointerObjectConversion(LLVMManagedPointer x, int offset,
                        @CachedLibrary(limit = "1") LLVMNativeLibrary nativeLib,
                        @Cached("createBinaryProfile()") ConditionProfile conditionProfile) {
            LLVMNativePointer nativePointer = nativeLib.toNativePointer(x.getObject());
            return nativePointerObjectConversion(nativePointer, offset, conditionProfile);
        }

        static IntegerConversionHelperNode create() {
            return IntegerConversionHelperNodeGen.create();
        }
    }

    @GenerateUncached
    abstract static class LongConversionHelperNode extends NumberConversionHelperNode {

        @Specialization
        long byteConversion(Byte x, @SuppressWarnings("unused") int offset) {
            assert offset == 0;
            return x.longValue();
        }

        @Specialization
        long shortConversion(Short x, @SuppressWarnings("unused") int offset) {
            assert offset == 0;
            return x.longValue();
        }

        @Specialization
        long intConversion(Integer x, @SuppressWarnings("unused") int offset) {
            assert offset == 0;
            return x.longValue();
        }

        @Specialization
        long longConversion(Long x, int offset) {
            assert offset == 0;
            return x;
        }

        @Specialization
        long doubleConversion(Double x, int offset) {
            assert offset == 0;
            return Double.doubleToLongBits(x);
        }

        @Specialization
        long floatVectorConversion(LLVMFloatVector x, int offset) {
            int index = offset / 4;
            assert index + 1 < x.getLength();
            long low = Float.floatToIntBits((Float) x.getElement(index));
            long high = Float.floatToIntBits((Float) x.getElement(index + 1));
            return low + (high << 32);
        }

        @Specialization
        long doubleVectorConversion(LLVMDoubleVector x, int offset) {
            int index = offset / 8;
            assert index < x.getLength();
            return Double.doubleToLongBits((Double) x.getElement(index));
        }

        @Specialization(guards = "isNativePointer(x.getAddr())")
        long compoundObjectConversion(LLVMVarArgCompoundValue x, int offset, @CachedLibrary(limit = "1") LLVMManagedReadLibrary compObjReadLib) {
            try {
                return compObjReadLib.readI64(x.getAddr(), offset);
            } catch (UnexpectedResultException e) {
                CompilerDirectives.transferToInterpreter();
                throw new UnsupportedOperationException("Should not get here");
            }
        }

        @Specialization(guards = "!isNativePointer(x.getAddr())")
        Object compoundObjectConversionManaged(LLVMVarArgCompoundValue x, int offset, @CachedLibrary(limit = "1") LLVMManagedReadLibrary compObjReadLib) {
            LLVMManagedPointer ptr = LLVMManagedPointer.cast(x.getAddr());
            return compObjReadLib.readGenericI64(ptr.getObject(), offset);
        }

        @Specialization(guards = "isNativePointer(x)")
        long nativePointerConversion(LLVMPointer x, int offset) {
            assert offset == 0;
            return LLVMNativePointer.cast(x).asNative();
        }

        @Specialization(guards = "isManagedPointer(x)")
        Object managedPointerConversion(LLVMPointer x, int offset) {
            assert offset == 0;
            return x;
        }

        static LongConversionHelperNode create() {
            return LongConversionHelperNodeGen.create();
        }
    }

    @GenerateUncached
    abstract static class PointerConversionHelperNode extends NumberConversionHelperNode {

        @Specialization
        LLVMPointer pointerConversion(LLVMPointer p, @SuppressWarnings("unused") int offset) {
            assert offset == 0;
            return p;
        }

        @Specialization
        LLVMPointer longObjectConversion(long x, int offset) {
            assert offset == 0;
            return LLVMNativePointer.create(x);
        }

        @Specialization
        LLVMPointer intObjectConversion(int x, int offset) {
            assert offset == 0;
            return LLVMNativePointer.create(x);
        }

        @Specialization(guards = "isNativePointer(x.getAddr())")
        LLVMPointer compoundObjectConversion(LLVMVarArgCompoundValue x, int offset, @CachedLibrary(limit = "1") LLVMManagedReadLibrary compObjReadLib) {
            return compObjReadLib.readPointer(x.getAddr(), offset);
        }

        @Specialization(guards = "!isNativePointer(x.getAddr())")
        LLVMPointer compoundObjectConversionManaged(LLVMVarArgCompoundValue x, int offset, @CachedLibrary(limit = "1") LLVMManagedReadLibrary compObjReadLib) {
            LLVMManagedPointer ptr = LLVMManagedPointer.cast(x.getAddr());
            return compObjReadLib.readPointer(ptr.getObject(), offset);
        }

        @Fallback
        LLVMPointer fallackConversion(@SuppressWarnings("unused") Object x, @SuppressWarnings("unused") int offset) {
            return LLVMNativePointer.createNull();
        }

        static PointerConversionHelperNode create() {
            return PointerConversionHelperNodeGen.create();
        }
    }
}
