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
package com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.va;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateAOT;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.llvm.runtime.LLVMLanguage;
import com.oracle.truffle.llvm.runtime.LLVMVarArgCompoundValue;
import com.oracle.truffle.llvm.runtime.PlatformCapability;
import com.oracle.truffle.llvm.runtime.datalayout.DataLayout;
import com.oracle.truffle.llvm.runtime.floating.LLVM80BitFloat;
import com.oracle.truffle.llvm.runtime.interop.LLVMDataEscapeNode.LLVMPointerDataEscapeNode;
import com.oracle.truffle.llvm.runtime.interop.access.LLVMInteropType;
import com.oracle.truffle.llvm.runtime.interop.access.LLVMInteropType.Array;
import com.oracle.truffle.llvm.runtime.library.internal.LLVMManagedReadLibrary;
import com.oracle.truffle.llvm.runtime.memory.LLVMMemMoveNode;
import com.oracle.truffle.llvm.runtime.memory.VarargsAreaStackAllocationNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMHasDatalayoutNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMNode;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.va.LLVMVaListStorageFactory.ArgumentListExpanderFactory.ArgumentExpanderNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.va.LLVMVaListStorageFactory.ArgumentListExpanderFactory.Unpack32ArgumentExpanderNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.va.LLVMVaListStorageFactory.ByteConversionHelperNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.va.LLVMVaListStorageFactory.IntegerConversionHelperNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.va.LLVMVaListStorageFactory.LongConversionHelperNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.va.LLVMVaListStorageFactory.PointerConversionHelperNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.va.LLVMVaListStorageFactory.ShortConversionHelperNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.LLVMNativePointerSupport.ToNativePointerNode;
import com.oracle.truffle.llvm.runtime.nodes.memory.load.LLVMI32LoadNode;
import com.oracle.truffle.llvm.runtime.nodes.memory.load.LLVMI64LoadNode;
import com.oracle.truffle.llvm.runtime.nodes.memory.load.LLVMI8LoadNode;
import com.oracle.truffle.llvm.runtime.nodes.memory.load.LLVMPointerLoadNode;
import com.oracle.truffle.llvm.runtime.nodes.memory.store.LLVM80BitFloatStoreNode.LLVM80BitFloatOffsetStoreNode;
import com.oracle.truffle.llvm.runtime.nodes.memory.store.LLVMI32StoreNode.LLVMI32OffsetStoreNode;
import com.oracle.truffle.llvm.runtime.nodes.memory.store.LLVMI64StoreNode.LLVMI64OffsetStoreNode;
import com.oracle.truffle.llvm.runtime.nodes.memory.store.LLVMPointerStoreNode.LLVMPointerOffsetStoreNode;
import com.oracle.truffle.llvm.runtime.pointer.LLVMManagedPointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMNativePointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMPointer;
import com.oracle.truffle.llvm.runtime.types.ArrayType;
import com.oracle.truffle.llvm.runtime.types.PointerType;
import com.oracle.truffle.llvm.runtime.types.PrimitiveType;
import com.oracle.truffle.llvm.runtime.types.Type;
import com.oracle.truffle.llvm.runtime.types.VectorType;
import com.oracle.truffle.llvm.runtime.vector.LLVMDoubleVector;
import com.oracle.truffle.llvm.runtime.vector.LLVMFloatVector;

@ExportLibrary(InteropLibrary.class)
public class LLVMVaListStorage implements TruffleObject {

    public enum VarArgArea {
        GP_AREA,
        FP_AREA,
        OVERFLOW_AREA
    }

    public static VarArgArea getVarArgArea(Object arg) {
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
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw CompilerDirectives.shouldNotReachHere(String.valueOf(arg));
        }
    }

    public static VarArgArea getVarArgArea(Type type) {
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
        } else if (isFloatArrayWithMaxTwoElems(type)) {
            return VarArgArea.FP_AREA;
        } else if (type instanceof PointerType) {
            return VarArgArea.GP_AREA;
        } else {
            return VarArgArea.OVERFLOW_AREA;
        }
    }

    @TruffleBoundary
    public static boolean isFloatVectorWithMaxTwoElems(Type type) {
        return type instanceof VectorType && getVectorElementType(type) == PrimitiveType.FLOAT && ((VectorType) type).getNumberOfElements() <= 2;
    }

    @TruffleBoundary
    public static boolean isFloatArrayWithMaxTwoElems(Type type) {
        return type instanceof ArrayType && getArrayElementType(type) == PrimitiveType.FLOAT && ((ArrayType) type).getNumberOfElements() <= 2;
    }

    @TruffleBoundary
    public static boolean isDoubleVectorWithMaxTwoElems(Type type) {
        return type instanceof VectorType && getVectorElementType(type) == PrimitiveType.DOUBLE && ((VectorType) type).getNumberOfElements() <= 2;
    }

    @TruffleBoundary
    public static boolean isDoubleArrayWithMaxTwoElems(Type type) {
        return type instanceof ArrayType && getArrayElementType(type) == PrimitiveType.DOUBLE && ((ArrayType) type).getNumberOfElements() <= 2;
    }

    @TruffleBoundary
    public static boolean isI64VectorWithMaxTwoElems(Type type) {
        return type instanceof VectorType && getVectorElementType(type) == PrimitiveType.I64 && ((VectorType) type).getNumberOfElements() <= 2;
    }

    @TruffleBoundary
    public static boolean isI64ArrayWithMaxTwoElems(Type type) {
        return type instanceof ArrayType && getArrayElementType(type) == PrimitiveType.I64 && ((ArrayType) type).getNumberOfElements() <= 2;
    }

    @TruffleBoundary
    public static boolean isI32VectorWithMaxTwoElems(Type type) {
        return type instanceof VectorType && getVectorElementType(type) == PrimitiveType.I32 && ((VectorType) type).getNumberOfElements() <= 2;
    }

    @TruffleBoundary
    public static boolean isI32ArrayWithMaxTwoElems(Type type) {
        return type instanceof ArrayType && getArrayElementType(type) == PrimitiveType.I32 && ((ArrayType) type).getNumberOfElements() <= 2;
    }

    @TruffleBoundary
    private static Type getVectorElementType(Type type) {
        return ((VectorType) type).getElementType();
    }

    @TruffleBoundary
    private static Type getArrayElementType(Type type) {
        return ((ArrayType) type).getElementType();
    }

    protected static DataLayout findDataLayoutFromCurrentFrame() {
        RootCallTarget callTarget = (RootCallTarget) Truffle.getRuntime().iterateFrames((f) -> f.getCallTarget());
        return (((LLVMHasDatalayoutNode) callTarget.getRootNode())).getDatalayout();
    }

    public static long storeArgument(LLVMPointer ptr, long offset, LLVMMemMoveNode memmove, LLVMI64OffsetStoreNode storeI64Node,
                    LLVMI32OffsetStoreNode storeI32Node, LLVM80BitFloatOffsetStoreNode storeFP80Node, LLVMPointerOffsetStoreNode storePointerNode, Object object, int stackStep) {
        if (object instanceof Number) {
            return doPrimitiveWrite(ptr, offset, storeI64Node, object, stackStep);
        } else if (object instanceof LLVMVarArgCompoundValue) {
            LLVMVarArgCompoundValue obj = (LLVMVarArgCompoundValue) object;
            Object currentPtr = ptr.increment(offset);
            memmove.executeWithTarget(currentPtr, obj.getAddr(), obj.getSize());
            return obj.getSize();
        } else if (LLVMPointer.isInstance(object)) {
            storePointerNode.executeWithTarget(ptr, offset, object);
            return stackStep;
        } else if (object instanceof LLVM80BitFloat) {
            storeFP80Node.executeWithTarget(ptr, offset, (LLVM80BitFloat) object);
            return 16;
        } else if (object instanceof LLVMFloatVector) {
            final LLVMFloatVector floatVec = (LLVMFloatVector) object;
            for (int i = 0; i < floatVec.getLength(); i++) {
                storeI32Node.executeWithTarget(ptr, offset + i * Float.BYTES, Float.floatToIntBits(floatVec.getValue(i)));
            }
            return floatVec.getLength() * Float.BYTES;
        } else {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw CompilerDirectives.shouldNotReachHere(String.valueOf(object));
        }
    }

    private static int doPrimitiveWrite(LLVMPointer ptr, long offset, LLVMI64OffsetStoreNode storeNode, Object arg, int stackStep) throws AssertionError {
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
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw CompilerDirectives.shouldNotReachHere(String.valueOf(arg));
        }
        storeNode.executeWithTarget(ptr, offset, value);
        return stackStep;
    }

    // Interop library implementation

    public static final String GET_MEMBER = "get";
    public static final String NEXT_MEMBER = "next";

    public Object[] realArguments;
    protected int numberOfExplicitArguments;

    protected LLVMPointer vaListStackPtr;
    protected boolean nativized;

    protected LLVMVaListStorage(LLVMPointer vaListStackPtr) {
        this.vaListStackPtr = vaListStackPtr;
    }

    public boolean isNativized() {
        return nativized;
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
    public static final class VAListMembers implements TruffleObject {

        @ExportMessage
        @SuppressWarnings("static-method")
        boolean hasArrayElements() {
            return true;
        }

        @ExportMessage
        @SuppressWarnings("static-method")
        long getArraySize() {
            return 2;
        }

        @ExportMessage
        @SuppressWarnings("static-method")
        boolean isArrayElementReadable(long index) {
            return index == 0 || index == 1;
        }

        @ExportMessage
        @SuppressWarnings("static-method")
        Object readArrayElement(long index) throws InvalidArrayIndexException {
            if (index == 0) {
                return GET_MEMBER;
            } else if (index == 1) {
                return NEXT_MEMBER;
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

    @ExportMessage
    public static class IsMemberInvocable {

        @Specialization(guards = "GET_MEMBER.equals(member)")
        public static boolean get(@SuppressWarnings("unused") LLVMVaListStorage receiver, @SuppressWarnings("unused") String member) {
            return true;
        }

        @Specialization(guards = "NEXT_MEMBER.equals(member)")
        public static boolean next(@SuppressWarnings("unused") LLVMVaListStorage receiver, @SuppressWarnings("unused") String member) {
            return true;
        }

        @Fallback
        public static boolean other(@SuppressWarnings("unused") LLVMVaListStorage receiver, @SuppressWarnings("unused") String member) {
            return false;
        }
    }

    @ExportMessage
    public static class InvokeMember {

        @Specialization(guards = "GET_MEMBER.equals(member)")
        public static Object get(LLVMVaListStorage receiver, @SuppressWarnings("unused") String member, Object[] arguments,
                        @Cached.Shared("escapeNode") @Cached LLVMPointerDataEscapeNode pointerEscapeNode) throws ArityException, UnsupportedTypeException {
            if (arguments.length != 2) {
                throw ArityException.create(2, 2, arguments.length);
            }
            if (!(arguments[0] instanceof Integer)) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw UnsupportedTypeException.create(new Object[]{arguments[0]}, "Index argument must be an integer");
            }
            int i = (Integer) arguments[0];
            if (i >= receiver.realArguments.length - receiver.numberOfExplicitArguments) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw new ArrayIndexOutOfBoundsException(i);
            }

            Object arg = receiver.realArguments[receiver.numberOfExplicitArguments + i];

            if (!(arguments[1] instanceof LLVMInteropType.Structured)) {
                return arg;
            }
            LLVMInteropType.Structured type = (LLVMInteropType.Structured) arguments[1];

            if (!LLVMPointer.isInstance(arg)) {
                // TODO: Do some conversion if the type in the 2nd argument does not match the
                // arg's types
                return arg;
            }
            LLVMPointer ptrArg = LLVMPointer.cast(arg);

            return pointerEscapeNode.executeWithType(ptrArg, type);
        }

        @Specialization(guards = "NEXT_MEMBER.equals(member)")
        public static Object next(LLVMVaListStorage receiver, @SuppressWarnings("unused") String member, Object[] arguments,
                        @CachedLibrary("receiver") LLVMVaListLibrary vaListLib,
                        @Cached.Shared("escapeNode") @Cached LLVMPointerDataEscapeNode pointerEscapeNode) throws ArityException, UnsupportedTypeException {
            if (arguments.length != 1) {
                throw ArityException.create(1, 1, arguments.length);
            }
            if (!(arguments[0] instanceof LLVMInteropType)) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw UnsupportedTypeException.create(arguments, "LLVMInteropType");
            }
            LLVMInteropType type = (LLVMInteropType) arguments[0];
            Type internalType;
            if (type instanceof LLVMInteropType.Value) {
                switch (((LLVMInteropType.Value) type).kind) {
                    case DOUBLE:
                        internalType = PrimitiveType.DOUBLE;
                        break;
                    case FLOAT:
                        internalType = PrimitiveType.FLOAT;
                        break;
                    case I1:
                        internalType = PrimitiveType.I1;
                        break;
                    case I16:
                        internalType = PrimitiveType.I16;
                        break;
                    case I32:
                        internalType = PrimitiveType.I32;
                        break;
                    case I64:
                        internalType = PrimitiveType.I64;
                        break;
                    case I8:
                        internalType = PrimitiveType.I8;
                        break;
                    case POINTER:
                        // don't care about the pointee type
                        internalType = new PointerType(PrimitiveType.I64);
                        break;
                    default:
                        throw CompilerDirectives.shouldNotReachHere("not implemented");
                }
            } else if (type instanceof LLVMInteropType.Structured) {
                // don't care about the pointee type
                internalType = new PointerType(PrimitiveType.I64);
            } else {
                throw CompilerDirectives.shouldNotReachHere("not implemented");
            }
            // only pointers?
            Object result = vaListLib.shift(receiver, internalType, null);

            if (!(type instanceof LLVMInteropType.Structured)) {
                return result;
            }
            if (!LLVMPointer.isInstance(result)) {
                // TODO: Do some conversion if the type in the 2nd argument does not match the
                // arg's types
                return result;
            }
            LLVMPointer ptrArg = LLVMPointer.cast(result);

            return pointerEscapeNode.executeWithType(ptrArg, (LLVMInteropType.Structured) type);
        }

        @Fallback
        public static Object other(@SuppressWarnings("unused") LLVMVaListStorage receiver, String member, @SuppressWarnings("unused") Object[] arguments) throws UnknownIdentifierException {
            throw UnknownIdentifierException.create(member);
        }
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
    public Object readArrayElement(long index, @Cached.Shared("escapeNode") @Cached LLVMPointerDataEscapeNode pointerEscapeNode) {
        Object arg = realArguments[(int) index + numberOfExplicitArguments];
        return pointerEscapeNode.executeWithTarget(arg);
    }

    @ExportMessage
    public boolean isPointer() {
        return isNativized() && LLVMNativePointer.isInstance(vaListStackPtr);
    }

    @ExportMessage
    public long asPointer() throws UnsupportedMessageException {
        if (isPointer()) {
            return LLVMNativePointer.cast(vaListStackPtr).asNative();
        }
        throw UnsupportedMessageException.create();
    }

    @GenerateUncached
    public abstract static class VAListPointerWrapperFactory extends LLVMNode {

        public abstract Object execute(LLVMPointer pointer);
    }

    @GenerateUncached
    public abstract static class VAListPointerWrapperFactoryDelegate extends LLVMNode {

        public abstract Object execute(Object pointer);

        @Specialization
        Object createWrapper(LLVMPointer pointer,
                        @Cached(value = "createVAListPointerWrapperFactory(true)", uncached = "createVAListPointerWrapperFactory(false)") VAListPointerWrapperFactory wrapperFactory) {
            return wrapperFactory.execute(pointer);
        }

        @Fallback
        Object createWrapper(Object o) {
            return o;
        }

        public static VAListPointerWrapperFactory createVAListPointerWrapperFactory(boolean cached) {
            return LLVMLanguage.get(null).getCapability(PlatformCapability.class).createNativeVAListWrapper(cached);
        }

    }

    public static final class StackAllocationNode extends LLVMNode implements GenerateAOT.Provider {

        private static final StackAllocationNode UNCACHED = new StackAllocationNode();

        @Child VarargsAreaStackAllocationNode allocNode;

        private StackAllocationNode() {
        }

        public static StackAllocationNode create() {
            return new StackAllocationNode();
        }

        public static StackAllocationNode getUncached() {
            return UNCACHED;
        }

        @Override
        public void prepareForAOT(TruffleLanguage<?> language, RootNode root) {
            allocNode = createVarargsAreaStackAllocationNode();
            insert((Node) allocNode);
        }

        public LLVMPointer executeWithTarget(long size, Frame frame) {
            if (allocNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                allocNode = createVarargsAreaStackAllocationNode();
                insert((Node) allocNode);
            }
            assert frame instanceof VirtualFrame;
            return allocNode.executeWithTarget((VirtualFrame) frame, size);
        }

        @SuppressWarnings("static-method")
        VarargsAreaStackAllocationNode createVarargsAreaStackAllocationNode() {
            DataLayout dataLayout = getDataLayout();
            LLVMLanguage lang = LLVMLanguage.get(null);
            return lang.getActiveConfiguration().createNodeFactory(lang, dataLayout).createVarargsAreaStackAllocation();
        }
    }

    /**
     * The abstraction for the two special areas in the va_list structure. Both, the reg_save_area
     * and overflow_area descendants have an array of arguments that is indirectly addressable by
     * offset. The conversion between the offset and the array index is calculated by the abstract
     * <code>offsetToIndex</code> method, which is called from the common implementation of
     * {@link LLVMManagedReadLibrary}.
     */
    @ExportLibrary(value = LLVMManagedReadLibrary.class, useForAOT = false)
    public abstract static class ArgsArea implements TruffleObject {
        public final Object[] args;

        protected ArgsArea(Object[] args) {
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
        public boolean isReadable() {
            return true;
        }

        @ExportMessage
        public byte readI8(long offset, @Cached ByteConversionHelperNode convNode) {
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
        public short readI16(long offset, @Cached ShortConversionHelperNode convNode) {
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
        public int readI32(long offset, @Cached IntegerConversionHelperNode convNode) {
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
        public LLVMPointer readPointer(long offset, @Cached PointerConversionHelperNode convNode) {
            long n = offsetToIndex(offset);
            int i = (int) ((n << 32) >> 32);
            int j = (int) (n >> 32);
            return i < 0 ? LLVMNativePointer.createNull() : LLVMPointer.cast(convNode.execute(args[i], j));
        }

        @ExportMessage
        public Object readGenericI64(long offset, @Cached LongConversionHelperNode convNode) {
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
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw CompilerDirectives.shouldNotReachHere(String.valueOf(arg));
            }
        }
    }

    /**
     * The class of the managed <code>overflow_area</code> in <code>va_list</code>. In contrast to
     * the <code>RegSaveArea</code>, the arguments array contains only the arguments that are to be
     * stored in the <code>overflow_area</code>. The <code>offsets</code> array serves to map
     * offsets to the indices of that array.
     */
    public abstract static class AbstractOverflowArgArea extends ArgsArea implements Cloneable {
        protected final long[] offsets;
        public final int overflowAreaSize;

        protected long previousOffset = -1;
        protected long currentOffset;

        protected AbstractOverflowArgArea(Object[] args, long[] offsets, int overflowAreaSize) {
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
                    // The input offset aligns with the i-th calculated offset, so just return
                    // the
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

        public void shift(int steps) {
            previousOffset = currentOffset;
            long n = offsetToIndex(currentOffset);
            int i = (int) ((n << 32) >> 32);
            currentOffset = offsets[i + steps];
        }

        public void setOffset(long newOffset) {
            previousOffset = currentOffset;
            currentOffset = newOffset;
        }

        public Object getCurrentArg() {
            int i = getCurrentArgIndex();
            return i < 0 ? null : args[i];
        }

        public Object getPreviousArg() {
            int i = getPreviousArgIndex();
            return i < 0 ? null : args[i];
        }

        public int getCurrentArgIndex() {
            long n = offsetToIndex(currentOffset);
            int i = (int) ((n << 32) >> 32);
            return i;
        }

        public int getPreviousArgIndex() {
            if (previousOffset < 0) {
                return -1;
            }
            long n = offsetToIndex(previousOffset);
            int i = (int) ((n << 32) >> 32);
            return i;
        }

        public LLVMManagedPointer getCurrentArgPtr() {
            return LLVMManagedPointer.create(this, currentOffset);
        }

        public long getOffset() {
            return currentOffset;
        }

    }

    public abstract static class NumberConversionHelperNode extends LLVMNode {

        public abstract Object execute(Object x, int offset);

        static boolean isNativePointer(Object x) {
            return LLVMNativePointer.isInstance(x);
        }

        static boolean isManagedPointer(Object x) {
            return LLVMManagedPointer.isInstance(x);
        }

    }

    @GenerateUncached
    public abstract static class ByteConversionHelperNode extends NumberConversionHelperNode {

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
        byte compoundObjectConversionNative(LLVMVarArgCompoundValue x, int offset,
                        @Cached LLVMI8LoadNode.LLVMI8OffsetLoadNode offsetLoad) {
            return offsetLoad.executeWithTarget(LLVMNativePointer.cast(x.getAddr()), offset);
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
                        @Cached ToNativePointerNode toNativePointer,
                        @Cached("createBinaryProfile()") ConditionProfile conditionProfile1,
                        @Cached("createBinaryProfile()") ConditionProfile conditionProfile2,
                        @Cached("createBinaryProfile()") ConditionProfile conditionProfile3) {
            LLVMNativePointer nativePointer = toNativePointer.execute(x.getObject());
            return nativePointerObjectConversion(nativePointer, offset, conditionProfile1, conditionProfile2, conditionProfile3);
        }

        public static ByteConversionHelperNode create() {
            return ByteConversionHelperNodeGen.create();
        }

    }

    @GenerateUncached
    public abstract static class ShortConversionHelperNode extends NumberConversionHelperNode {

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
                        @Cached ToNativePointerNode toNativePointer,
                        @Cached("createBinaryProfile()") ConditionProfile conditionProfile1,
                        @Cached("createBinaryProfile()") ConditionProfile conditionProfile2) {
            LLVMNativePointer nativePointer = toNativePointer.execute(x.getObject());
            return nativePointerObjectConversion(nativePointer, offset, conditionProfile1, conditionProfile2);
        }

        public static ShortConversionHelperNode create() {
            return ShortConversionHelperNodeGen.create();
        }
    }

    @GenerateUncached
    public abstract static class IntegerConversionHelperNode extends NumberConversionHelperNode {

        public int executeInteger(Object x, int offset) {
            return (int) execute(x, offset);
        }

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
        int floatConversion(Float x, @SuppressWarnings("unused") int offset) {
            return Float.floatToIntBits(x);
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
        int compoundObjectConversion(LLVMVarArgCompoundValue x, int offset,
                        @Cached LLVMI32LoadNode.LLVMI32OffsetLoadNode offsetLoad) {
            return offsetLoad.executeWithTarget(LLVMNativePointer.cast(x.getAddr()), offset);
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
                        @Cached ToNativePointerNode toNativePointer,
                        @Cached("createBinaryProfile()") ConditionProfile conditionProfile) {
            LLVMNativePointer nativePointer = toNativePointer.execute(x.getObject());
            return nativePointerObjectConversion(nativePointer, offset, conditionProfile);
        }

        public static IntegerConversionHelperNode create() {
            return IntegerConversionHelperNodeGen.create();
        }
    }

    @GenerateUncached
    public abstract static class LongConversionHelperNode extends NumberConversionHelperNode {

        public long executeLong(Object x, int offset) {
            return (long) execute(x, offset);
        }

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
        long compoundObjectConversion(LLVMVarArgCompoundValue x, int offset,
                        @Cached LLVMI64LoadNode.LLVMI64OffsetLoadNode offsetLoad) {
            try {
                return offsetLoad.executeWithTarget(LLVMNativePointer.cast(x.getAddr()), offset);
            } catch (UnexpectedResultException e) {
                throw CompilerDirectives.shouldNotReachHere();
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

        public static LongConversionHelperNode create() {
            return LongConversionHelperNodeGen.create();
        }
    }

    @GenerateUncached
    public abstract static class PointerConversionHelperNode extends NumberConversionHelperNode {

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
        LLVMPointer compoundObjectConversion(LLVMVarArgCompoundValue x, int offset,
                        @Cached LLVMPointerLoadNode.LLVMPointerOffsetLoadNode offsetLoad) {
            return offsetLoad.executeWithTarget(LLVMNativePointer.cast(x.getAddr()), offset);
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

        public static PointerConversionHelperNode create() {
            return PointerConversionHelperNodeGen.create();
        }
    }

    public static final boolean UNPACK_32BIT_PRIMITIVES_IN_STRUCTS = true;
    public static final boolean KEEP_32BIT_PRIMITIVES_IN_STRUCTS = false;

    public static final class ArgumentListExpander extends LLVMNode {
        private final BranchProfile expansionBranchProfile;
        private final ConditionProfile noExpansionProfile;
        private @Child ArgumentExpander expander;
        private final boolean cached;

        private static final ArgumentListExpander uncached_unpack32 = new ArgumentListExpander(false, UNPACK_32BIT_PRIMITIVES_IN_STRUCTS);
        private static final ArgumentListExpander uncached_no_unpack32 = new ArgumentListExpander(false, KEEP_32BIT_PRIMITIVES_IN_STRUCTS);

        private ArgumentListExpander(boolean cached, boolean unpack32) {
            expansionBranchProfile = cached ? BranchProfile.create() : BranchProfile.getUncached();
            noExpansionProfile = cached ? ConditionProfile.createBinaryProfile() : ConditionProfile.getUncached();
            if (cached) {
                expander = unpack32 ? Unpack32ArgumentExpanderNodeGen.create() : ArgumentExpanderNodeGen.create();
            } else {
                expander = unpack32 ? Unpack32ArgumentExpanderNodeGen.getUncached() : ArgumentExpanderNodeGen.getUncached();
            }
            this.cached = cached;
        }

        @Override
        public boolean isAdoptable() {
            return cached;
        }

        public static ArgumentListExpander create(boolean unpack32) {
            return new ArgumentListExpander(true, unpack32);
        }

        public static ArgumentListExpander getUncached(boolean unpack32) {
            return unpack32 ? uncached_unpack32 : uncached_no_unpack32;
        }

        public Object[] expand(Object[] args, Object[][][] expansionsOutArg) {
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
        public abstract static class ArgumentExpander extends LLVMNode {
            public abstract Object[] execute(Object arg);

            @Specialization(guards = "isDoubleArrayWithMaxTwoElems(arg.getType()) || isDoubleVectorWithMaxTwoElems(arg.getType())")
            protected Object[] expandDoubleArrayOrVectorCompoundArg(LLVMVarArgCompoundValue arg, @Cached LongConversionHelperNode convNode) {
                return new Object[]{Double.longBitsToDouble(convNode.executeLong(arg, 0)), Double.longBitsToDouble(convNode.executeLong(arg, 8))};
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

        @ImportStatic(LLVMVaListStorage.class)
        @GenerateUncached
        public abstract static class Unpack32ArgumentExpander extends ArgumentExpander {
            @Specialization(guards = {"isFloatArrayWithMaxTwoElems(arg.getType()) || isFloatVectorWithMaxTwoElems(arg.getType())"})
            protected Object[] expandFloatArrayOrVectorCompoundArg(LLVMVarArgCompoundValue arg, @Cached IntegerConversionHelperNode convNode) {
                return new Object[]{Float.intBitsToFloat(convNode.executeInteger(arg, 0)), Float.intBitsToFloat(convNode.executeInteger(arg, 4))};
            }

            @Specialization(guards = {"isI32ArrayWithMaxTwoElems(arg.getType()) || isI32VectorWithMaxTwoElems(arg.getType())"})
            protected Object[] expandI32ArrayOrVectorCompoundArg(LLVMVarArgCompoundValue arg, @Cached IntegerConversionHelperNode convNode) {
                return new Object[]{convNode.executeInteger(arg, 0), convNode.executeInteger(arg, 4)};
            }
        }
    }
}
