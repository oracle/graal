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
package com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.x86_win;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
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
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.llvm.runtime.datalayout.DataLayout;
import com.oracle.truffle.llvm.runtime.interop.LLVMDataEscapeNode.LLVMPointerDataEscapeNode;
import com.oracle.truffle.llvm.runtime.interop.access.LLVMInteropType;
import com.oracle.truffle.llvm.runtime.library.internal.LLVMManagedReadLibrary;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMHasDatalayoutNode;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.va.LLVMVaListLibrary;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.va.LLVMVaListStorage.ArgsArea;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.va.LLVMVaListStorage.StackAllocationNode;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.va.LLVMVaListStorage.VAListPointerWrapperFactory;
import com.oracle.truffle.llvm.runtime.nodes.memory.load.LLVMI16LoadNode.LLVMI16OffsetLoadNode;
import com.oracle.truffle.llvm.runtime.nodes.memory.load.LLVMI32LoadNode.LLVMI32OffsetLoadNode;
import com.oracle.truffle.llvm.runtime.nodes.memory.load.LLVMI64LoadNode.LLVMI64OffsetLoadNode;
import com.oracle.truffle.llvm.runtime.nodes.memory.load.LLVMI8LoadNode.LLVMI8OffsetLoadNode;
import com.oracle.truffle.llvm.runtime.nodes.memory.load.LLVMPointerLoadNode.LLVMPointerOffsetLoadNode;
import com.oracle.truffle.llvm.runtime.nodes.memory.store.LLVMI64StoreNode.LLVMI64OffsetStoreNode;
import com.oracle.truffle.llvm.runtime.nodes.memory.store.LLVMPointerStoreNode.LLVMPointerOffsetStoreNode;
import com.oracle.truffle.llvm.runtime.pointer.LLVMManagedPointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMMaybeVaPointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMNativePointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMPointer;
import com.oracle.truffle.llvm.runtime.types.PointerType;
import com.oracle.truffle.llvm.runtime.types.PrimitiveType;
import com.oracle.truffle.llvm.runtime.types.Type;

@ExportLibrary(value = LLVMVaListLibrary.class, useForAOT = true, useForAOTPriority = 2)
@ExportLibrary(value = LLVMManagedReadLibrary.class, useForAOT = true, useForAOTPriority = 1)
@ExportLibrary(InteropLibrary.class)
public final class LLVMX86_64_WinVaListStorage implements TruffleObject {

    protected LLVMPointer nativeAreaPointer;
    protected WinArgsArea argsArea;
    protected Object[] realArguments;
    protected int numberOfExplicitArguments;
    protected boolean nativized;
    private int consumedBytes;

    public LLVMX86_64_WinVaListStorage() {
    }

    public boolean isNativized() {
        return nativized;
    }

    @ExportMessage(name = "isReadable")
    // @ExportMessage(name = "isWritable") TODO: even though questionable, writing should probably
    // be supported
    @SuppressWarnings("static-method")
    boolean isAccessible() {
        return true;
    }

    public static final class WinArgsArea extends ArgsArea {
        private final int numOfExpArgs;

        WinArgsArea(Object[] args, int numOfExpArgs) {
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
    static class ReadI8 {
        @Specialization(guards = "va.isNativized()")
        static byte readI8Native(LLVMX86_64_WinVaListStorage va, long offset, @Cached LLVMI8OffsetLoadNode loadNode) {
            return loadNode.executeWithTarget(va.nativeAreaPointer, offset);
        }

        @Specialization(guards = "!va.isNativized()", limit = "1")
        static byte readI8Managed(LLVMX86_64_WinVaListStorage va, long offset, @CachedLibrary("va.argsArea") LLVMManagedReadLibrary readLibrary) {
            return readLibrary.readI8(va.argsArea, offset);
        }
    }

    @ExportMessage
    static class ReadI16 {
        @Specialization(guards = "va.isNativized()")
        static short readI16Native(LLVMX86_64_WinVaListStorage va, long offset, @Cached LLVMI16OffsetLoadNode loadNode) {
            return loadNode.executeWithTarget(va.nativeAreaPointer, offset);
        }

        @Specialization(guards = "!va.isNativized()", limit = "1")
        static short readI16Managed(LLVMX86_64_WinVaListStorage va, long offset, @CachedLibrary("va.argsArea") LLVMManagedReadLibrary readLibrary) {
            return readLibrary.readI16(va.argsArea, offset);
        }
    }

    @ExportMessage
    static class ReadI32 {
        @Specialization(guards = "va.isNativized()")
        static int readI32Native(LLVMX86_64_WinVaListStorage va, long offset, @Cached LLVMI32OffsetLoadNode loadNode) {
            return loadNode.executeWithTarget(va.nativeAreaPointer, offset);
        }

        @Specialization(guards = "!va.isNativized()", limit = "1")
        static int readI32Managed(LLVMX86_64_WinVaListStorage va, long offset, @CachedLibrary("va.argsArea") LLVMManagedReadLibrary readLibrary) {
            return readLibrary.readI32(va.argsArea, offset);
        }
    }

    @ExportMessage
    static class ReadPointer {
        @Specialization(guards = "va.isNativized()")
        static LLVMPointer readPointerNative(LLVMX86_64_WinVaListStorage va, long offset, @Cached LLVMPointerOffsetLoadNode loadNode) {
            return loadNode.executeWithTarget(va.nativeAreaPointer, offset);
        }

        @Specialization(guards = "!va.isNativized()", limit = "1")
        static LLVMPointer readPointerManaged(LLVMX86_64_WinVaListStorage va, long offset, @CachedLibrary("va.argsArea") LLVMManagedReadLibrary readLibrary) {
            return readLibrary.readPointer(va.argsArea, offset);
        }
    }

    @ExportMessage
    static class ReadGenericI64 {
        @Specialization(guards = "va.isNativized()")
        static Object readI64Native(LLVMX86_64_WinVaListStorage va, long offset, @Cached LLVMI64OffsetLoadNode loadNode) {
            return loadNode.executeWithTargetGeneric(va.nativeAreaPointer, offset);
        }

        @Specialization(guards = "!va.isNativized()", limit = "1")
        static Object readI64Managed(LLVMX86_64_WinVaListStorage va, long offset, @CachedLibrary("va.argsArea") LLVMManagedReadLibrary readLibrary) {
            return readLibrary.readGenericI64(va.argsArea, offset);
        }
    }

    @ExportMessage
    public boolean isPointer() {
        return isNativized();
    }

    protected static DataLayout findDataLayoutFromCurrentFrame() {
        RootCallTarget callTarget = (RootCallTarget) Truffle.getRuntime().iterateFrames((f) -> f.getCallTarget());
        return (((LLVMHasDatalayoutNode) callTarget.getRootNode())).getDatalayout();
    }

    @ExportMessage
    public long asPointer() throws UnsupportedMessageException {
        if (isPointer()) {
            return LLVMNativePointer.cast(nativeAreaPointer).asNative();
        }
        throw UnsupportedMessageException.create();
    }

    private static void doPrimitiveWrite(LLVMPointer ptr, long offset, LLVMI64OffsetStoreNode storeNode, Object arg) throws AssertionError {
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
    }

    private static void storeArgument(LLVMPointer ptr, long offset, Object object, LLVMI64OffsetStoreNode storeI64Node,
                    LLVMPointerOffsetStoreNode storePointerNode) {
        if (object instanceof Number) {
            doPrimitiveWrite(ptr, offset, storeI64Node, object);
        } else if (LLVMPointer.isInstance(object)) {
            storePointerNode.executeWithTarget(ptr, offset, object);
        } else {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw CompilerDirectives.shouldNotReachHere(String.valueOf(object));
        }
    }

    private static void initNative(Object[] realArgs, int numOfExpArgs, LLVMPointer pointer,
                    LLVMI64OffsetStoreNode storeI64Node,
                    @Exclusive @Cached LLVMPointerOffsetStoreNode storePointerNode) {
        int offset = 0;
        for (int i = numOfExpArgs; i < realArgs.length; i++) {
            storeArgument(pointer, offset, realArgs[i], storeI64Node, storePointerNode);
            offset += Long.BYTES;
        }
    }

    @SuppressWarnings("static-method")
    @ExportMessage
    @TruffleBoundary
    void toNative(
                    @Exclusive @Cached LLVMI64OffsetStoreNode storeI64Node,
                    @Exclusive @Cached LLVMPointerOffsetStoreNode storePointerNode,
                    @Cached BranchProfile nativizedProfile) {
        if (isNativized()) {
            nativizedProfile.enter();
        }

        this.nativized = true;
        initNative(realArguments, numberOfExplicitArguments, nativeAreaPointer, storeI64Node, storePointerNode);
    }

    @ExportMessage
    public void initialize(Object[] realArgs, int numOfExpArgs, Frame frame,
                    @Cached StackAllocationNode stackAllocationNode) {
        realArguments = realArgs;
        numberOfExplicitArguments = numOfExpArgs;
        argsArea = new WinArgsArea(realArgs, numOfExpArgs);
        assert numOfExpArgs <= realArgs.length;

        nativeAreaPointer = stackAllocationNode.executeWithTarget((realArgs.length - numOfExpArgs) * Long.BYTES, frame);
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

    @ExportLibrary(value = LLVMVaListLibrary.class, useForAOT = true, useForAOTPriority = 0)
    @ImportStatic(LLVMX86_64_WinVaListStorage.class)
    public static final class NativeVAListWrapper {
        final LLVMPointer nativeVAListPtr;

        public NativeVAListWrapper(LLVMPointer nativeVAListPtr) {
            this.nativeVAListPtr = nativeVAListPtr;
        }

        @ExportMessage
        public void initialize(Object[] arguments, int numberOfExplicitArguments, @SuppressWarnings("unused") Frame frame,
                        @Exclusive @Cached LLVMI64OffsetStoreNode storeI64Node,
                        @Exclusive @Cached LLVMPointerOffsetStoreNode storePointerNode) {
            initNative(arguments, numberOfExplicitArguments, nativeVAListPtr, storeI64Node, storePointerNode);
        }

        @ExportMessage
        static void copy(@SuppressWarnings("unused") NativeVAListWrapper self, @SuppressWarnings("unused") Object destVaList, @SuppressWarnings("unused") Frame frame) {
            throw CompilerDirectives.shouldNotReachHere("Should not copy VA directly.");
        }

        @ExportMessage
        public void cleanup(@SuppressWarnings("unused") Frame frame) {
            // nop
        }

        @SuppressWarnings("static-method")
        @ExportMessage
        public Object shift(@SuppressWarnings("unused") Type type, @SuppressWarnings("unused") Frame frame) {
            throw CompilerDirectives.shouldNotReachHere("Should not shift VA directly.");
        }

    }

    @GenerateUncached
    public abstract static class PointerWrapperFactory extends VAListPointerWrapperFactory {

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
            return o instanceof LLVMMaybeVaPointer;
        }
    }

    public static final String GET_MEMBER = "get";
    public static final String NEXT_MEMBER = "next";

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
        public static boolean get(@SuppressWarnings("unused") LLVMX86_64_WinVaListStorage receiver, @SuppressWarnings("unused") String member) {
            return true;
        }

        @Specialization(guards = "NEXT_MEMBER.equals(member)")
        public static boolean next(@SuppressWarnings("unused") LLVMX86_64_WinVaListStorage receiver, @SuppressWarnings("unused") String member) {
            return true;
        }

        @Fallback
        public static boolean other(@SuppressWarnings("unused") LLVMX86_64_WinVaListStorage receiver, @SuppressWarnings("unused") String member) {
            return false;
        }
    }

    @ExportMessage
    public static class InvokeMember {

        @Specialization(guards = "GET_MEMBER.equals(member)")
        public static Object get(LLVMX86_64_WinVaListStorage receiver, @SuppressWarnings("unused") String member, Object[] arguments,
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
        public static Object next(LLVMX86_64_WinVaListStorage receiver, @SuppressWarnings("unused") String member, Object[] arguments,
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
        public static Object other(@SuppressWarnings("unused") LLVMX86_64_WinVaListStorage receiver, String member, @SuppressWarnings("unused") Object[] arguments) throws UnknownIdentifierException {
            throw UnknownIdentifierException.create(member);
        }
    }

    @ExportMessage
    public long getArraySize() {
        return argsArea.args.length - argsArea.numOfExpArgs;
    }

    @SuppressWarnings("static-method")
    @ExportMessage
    public boolean hasArrayElements() {
        return true;
    }

    @ExportMessage
    public boolean isArrayElementReadable(long index) {
        return index < getArraySize();
    }

    @ExportMessage
    Object readArrayElement(long index, @Cached.Shared("escapeNode") @Cached LLVMPointerDataEscapeNode pointerEscapeNode) {
        Object arg = argsArea.args[(int) (index + argsArea.numOfExpArgs)];
        return pointerEscapeNode.executeWithTarget(arg);
    }
}
