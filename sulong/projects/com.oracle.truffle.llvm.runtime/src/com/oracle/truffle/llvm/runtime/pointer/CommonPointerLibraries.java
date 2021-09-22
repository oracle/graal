/*
 * Copyright (c) 2018, 2021, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.runtime.pointer;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.Specialization;
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
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.api.utilities.TriState;
import com.oracle.truffle.llvm.runtime.LLVMLanguage;
import com.oracle.truffle.llvm.runtime.interop.access.LLVMInteropInvokeNode;
import com.oracle.truffle.llvm.runtime.interop.access.LLVMResolveForeignClassChainNode;
import com.oracle.truffle.llvm.runtime.interop.access.LLVMInteropType;
import com.oracle.truffle.llvm.runtime.interop.access.LLVMInteropType.Clazz;
import com.oracle.truffle.llvm.runtime.interop.export.LLVMForeignGetIndexPointerNode;
import com.oracle.truffle.llvm.runtime.interop.export.LLVMForeignGetMemberPointerNode;
import com.oracle.truffle.llvm.runtime.interop.export.LLVMForeignReadNode;
import com.oracle.truffle.llvm.runtime.interop.export.LLVMForeignWriteNode;
import com.oracle.truffle.llvm.runtime.library.internal.LLVMManagedReadLibrary;
import com.oracle.truffle.llvm.runtime.library.internal.LLVMManagedWriteLibrary;
import com.oracle.truffle.llvm.runtime.nodes.op.LLVMAddressEqualsNode;

@ExportLibrary(value = InteropLibrary.class, receiverType = LLVMPointerImpl.class)
@ExportLibrary(value = LLVMManagedWriteLibrary.class, receiverType = LLVMPointerImpl.class, useForAOT = true, useForAOTPriority = 1)
@ExportLibrary(value = LLVMManagedReadLibrary.class, receiverType = LLVMPointerImpl.class, useForAOT = true, useForAOTPriority = 2)
@SuppressWarnings({"static-method"})
abstract class CommonPointerLibraries {
    @ExportMessage
    static boolean isReadable(@SuppressWarnings("unused") LLVMPointerImpl receiver) {
        return false;
    }

    @ExportMessage
    static byte readI8(@SuppressWarnings("unused") LLVMPointerImpl receiver, @SuppressWarnings("unused") long offset) {
        throw CompilerDirectives.shouldNotReachHere("Cannot read a value of type I8 directly from a pointer. Perhaps a getObject() call is missing.");
    }

    @ExportMessage
    static short readI16(@SuppressWarnings("unused") LLVMPointerImpl receiver, @SuppressWarnings("unused") long offset) {
        throw CompilerDirectives.shouldNotReachHere("Cannot read a value of type I16 directly from a pointer. Perhaps a getObject() call is missing.");
    }

    @ExportMessage
    static int readI32(@SuppressWarnings("unused") LLVMPointerImpl receiver, @SuppressWarnings("unused") long offset) {
        throw CompilerDirectives.shouldNotReachHere("Cannot read a value of type I32 directly from a pointer. Perhaps a getObject() call is missing.");
    }

    @ExportMessage
    static float readFloat(@SuppressWarnings("unused") LLVMPointerImpl receiver, @SuppressWarnings("unused") long offset) {
        throw CompilerDirectives.shouldNotReachHere("Cannot read a value of type Float directly from a pointer. Perhaps a getObject() call is missing.");
    }

    @ExportMessage
    static long readI64(@SuppressWarnings("unused") LLVMPointerImpl receiver, @SuppressWarnings("unused") long offset) {
        throw CompilerDirectives.shouldNotReachHere("Cannot read a value of type I64 directly from a pointer. Perhaps a getObject() call is missing.");
    }

    @ExportMessage
    static double readDouble(@SuppressWarnings("unused") LLVMPointerImpl receiver, @SuppressWarnings("unused") long offset) {
        throw CompilerDirectives.shouldNotReachHere("Cannot read a value of type Double directly from a pointer. Perhaps a getObject() call is missing.");
    }

    @ExportMessage
    static LLVMPointer readPointer(@SuppressWarnings("unused") LLVMPointerImpl receiver, @SuppressWarnings("unused") long offset) {
        throw CompilerDirectives.shouldNotReachHere("Cannot read a value of type Pointer directly from a pointer. Perhaps a getObject() call is missing.");
    }

    @ExportMessage
    static Object readGenericI64(@SuppressWarnings("unused") LLVMPointerImpl receiver, @SuppressWarnings("unused") long offset) {
        throw CompilerDirectives.shouldNotReachHere("Cannot read a value of type Object directly from a pointer. Perhaps a getObject() call is missing.");
    }

    @ExportMessage
    static boolean isWritable(@SuppressWarnings("unused") LLVMPointerImpl receiver) {
        return false;
    }

    @ExportMessage
    static void writeI8(@SuppressWarnings("unused") LLVMPointerImpl receiver, @SuppressWarnings("unused") long offset, @SuppressWarnings("unused") byte value) {
        throw CompilerDirectives.shouldNotReachHere("Cannot write a value of type I8 directly to a pointer. Perhaps a getObject() call is missing.");
    }

    @ExportMessage
    static void writeI16(@SuppressWarnings("unused") LLVMPointerImpl receiver, @SuppressWarnings("unused") long offset, @SuppressWarnings("unused") short value) {
        throw CompilerDirectives.shouldNotReachHere("Cannot write a value of type I16 directly to a pointer. Perhaps a getObject() call is missing.");
    }

    @ExportMessage
    static void writeI32(@SuppressWarnings("unused") LLVMPointerImpl receiver, @SuppressWarnings("unused") long offset, @SuppressWarnings("unused") int value) {
        throw CompilerDirectives.shouldNotReachHere("Cannot write a value of type I32 directly to a pointer. Perhaps a getObject() call is missing.");
    }

    @ExportMessage
    static void writeFloat(@SuppressWarnings("unused") LLVMPointerImpl receiver, @SuppressWarnings("unused") long offset, @SuppressWarnings("unused") float value) {
        throw CompilerDirectives.shouldNotReachHere("Cannot write a value of type Float directly to a pointer. Perhaps a getObject() call is missing.");
    }

    @ExportMessage
    static void writeI64(@SuppressWarnings("unused") LLVMPointerImpl receiver, @SuppressWarnings("unused") long offset, @SuppressWarnings("unused") long value) {
        throw CompilerDirectives.shouldNotReachHere("Cannot write a value of type I64 directly to a pointer. Perhaps a getObject() call is missing.");
    }

    @ExportMessage
    static void writeGenericI64(@SuppressWarnings("unused") LLVMPointerImpl receiver, @SuppressWarnings("unused") long offset, @SuppressWarnings("unused") Object value) {
        throw CompilerDirectives.shouldNotReachHere("Cannot write a value of type Object directly to a pointer. Perhaps a getObject() call is missing.");
    }

    @ExportMessage
    static void writeDouble(@SuppressWarnings("unused") LLVMPointerImpl receiver, @SuppressWarnings("unused") long offset, @SuppressWarnings("unused") double value) {
        throw CompilerDirectives.shouldNotReachHere("Cannot write a value of type Double directly to a pointer. Perhaps a getObject() call is missing.");
    }

    @ExportMessage
    static void writePointer(@SuppressWarnings("unused") LLVMPointerImpl receiver, @SuppressWarnings("unused") long offset, @SuppressWarnings("unused") LLVMPointer value) {
        throw CompilerDirectives.shouldNotReachHere("Cannot write a value of type Pointer directly to a pointer. Perhaps a getObject() call is missing.");
    }

    @ExportMessage
    static boolean hasMembers(LLVMPointerImpl receiver) {
        // check for Clazz is not needed, since Clazz inherits from Struct
        return receiver.getExportType() instanceof LLVMInteropType.Struct;
    }

    /**
     * @param receiver
     * @param includeInternal
     * @see InteropLibrary#getMembers(Object, boolean)
     */
    @ExportMessage
    static Object getMembers(LLVMPointerImpl receiver, boolean includeInternal,
                    @Shared("isObject") @Cached ConditionProfile isObject) throws UnsupportedMessageException {
        if (isObject.profile(receiver.getExportType() instanceof LLVMInteropType.Clazz)) {
            LLVMInteropType.Clazz clazz = (LLVMInteropType.Clazz) receiver.getExportType();
            return new ClassKeys(clazz);
        } else if (isObject.profile(receiver.getExportType() instanceof LLVMInteropType.Struct)) {
            LLVMInteropType.Struct struct = (LLVMInteropType.Struct) receiver.getExportType();
            return new Keys(struct);
        } else {
            throw UnsupportedMessageException.create();
        }
    }

    @ExportMessage
    static boolean isMemberReadable(LLVMPointerImpl receiver, String ident,
                    @Shared("isObject") @Cached ConditionProfile isObject) {
        if (isObject.profile(receiver.getExportType() instanceof LLVMInteropType.Clazz)) {
            LLVMInteropType.Clazz clazz = (LLVMInteropType.Clazz) receiver.getExportType();
            LLVMInteropType.StructMember member = clazz.findMember(ident);
            if (member == null) {
                LLVMInteropType.Method method = clazz.findMethod(ident);
                return method != null;
            }
            return member != null;
        } else if (isObject.profile(receiver.getExportType() instanceof LLVMInteropType.Struct)) {
            LLVMInteropType.Struct struct = (LLVMInteropType.Struct) receiver.getExportType();
            LLVMInteropType.StructMember member = struct.findMember(ident);
            return member != null;
        } else {
            return false;
        }
    }

    @ExportMessage
    static Object readMember(LLVMPointerImpl receiver, String ident,
                    @Shared("getDirectClass") @Cached LLVMResolveForeignClassChainNode resolveClassChain,
                    @Shared("getMember") @Cached LLVMForeignGetMemberPointerNode getElementPointer,
                    @Exclusive @Cached LLVMForeignReadNode read) throws UnsupportedMessageException, UnknownIdentifierException {
        LLVMPointer correctClassPtr = resolveClassChain.execute(receiver, ident, receiver.getExportType());
        LLVMPointer ptr = getElementPointer.execute(correctClassPtr.getExportType(), correctClassPtr, ident);
        return read.execute(ptr, ptr.getExportType());
    }

    @ExportMessage
    static boolean isMemberModifiable(LLVMPointerImpl receiver, String ident,
                    @Shared("isObject") @Cached ConditionProfile isObject) {
        if (isObject.profile(receiver.getExportType() instanceof LLVMInteropType.Struct)) {
            /*
             * check for Clazz is not needed, since Clazz inherits from Struct, and methods
             * (=currently the only difference between Clazz and Struct) are not modifiable
             */
            LLVMInteropType.Struct struct = (LLVMInteropType.Struct) receiver.getExportType();
            LLVMInteropType.StructMember member = struct.findMember(ident);
            if (member == null) {
                // not found
                return false;
            } else {
                return member.type instanceof LLVMInteropType.Value;
            }
        } else {
            return false;
        }
    }

    /**
     * @param receiver
     * @param ident
     * @see InteropLibrary#isMemberInsertable(Object, String)
     */
    @ExportMessage
    static boolean isMemberInvocable(LLVMPointerImpl receiver, String ident, @CachedLibrary(limit = "5") InteropLibrary interop) {
        LLVMInteropType type = receiver.getExportType();
        if (type instanceof LLVMInteropType.Clazz &&
                        ((LLVMInteropType.Clazz) type).findMethod(ident) != null) {
            return true;
        }

        try {
            if (interop.isMemberReadable(receiver, ident)) {
                Object member = interop.readMember(receiver, ident);
                return interop.isExecutable(member);
            }
        } catch (UnsupportedMessageException | UnknownIdentifierException e) {
        }
        return false;
    }

    static LLVMInteropType.Clazz asClazz(LLVMPointerImpl receiver) throws UnsupportedMessageException {
        LLVMInteropType type = receiver.getExportType();
        if (!(type instanceof LLVMInteropType.Clazz)) {
            throw UnsupportedMessageException.create();
        }
        return (Clazz) type;
    }

    @ExportMessage
    static Object invokeMember(LLVMPointerImpl receiver, String member, Object[] arguments,
                    @Cached LLVMInteropInvokeNode invoke)
                    throws UnsupportedMessageException, ArityException, UnknownIdentifierException,
                    UnsupportedTypeException {
        return invoke.execute(receiver, receiver.getExportType(), member, arguments);
    }

    /**
     * @param receiver
     * @param ident
     */
    @ExportMessage
    static boolean isMemberInsertable(LLVMPointerImpl receiver, String ident) {
        return false;
    }

    @ExportMessage
    static void writeMember(LLVMPointerImpl receiver, String ident, Object value,
                    @Shared("getDirectClass") @Cached LLVMResolveForeignClassChainNode resolveClassChain,
                    @Shared("getMember") @Cached LLVMForeignGetMemberPointerNode getElementPointer,
                    @Exclusive @Cached LLVMForeignWriteNode write)
                    throws UnsupportedMessageException, UnknownIdentifierException {
        LLVMPointer correctClassPtr = resolveClassChain.execute(receiver, ident, receiver.getExportType());
        LLVMPointer ptr = getElementPointer.execute(correctClassPtr.getExportType(), correctClassPtr, ident);
        write.execute(ptr, ptr.getExportType(), value);
    }

    @ExportMessage
    static boolean hasArrayElements(LLVMPointerImpl receiver) {
        return receiver.getExportType() instanceof LLVMInteropType.Array;
    }

    @ExportMessage
    static long getArraySize(LLVMPointerImpl receiver,
                    @Shared("isArray") @Cached ConditionProfile isArray) throws UnsupportedMessageException {
        if (isArray.profile(receiver.getExportType() instanceof LLVMInteropType.Array)) {
            return ((LLVMInteropType.Array) receiver.getExportType()).length;
        } else {
            throw UnsupportedMessageException.create();
        }
    }

    @ExportMessage
    static boolean isArrayElementReadable(LLVMPointerImpl receiver, long idx,
                    @Shared("isArray") @Cached ConditionProfile isArray) {
        if (isArray.profile(receiver.getExportType() instanceof LLVMInteropType.Array)) {
            long length = ((LLVMInteropType.Array) receiver.getExportType()).length;
            return Long.compareUnsigned(idx, length) < 0;
        } else {
            return false;
        }
    }

    @ExportMessage
    static Object readArrayElement(LLVMPointerImpl receiver, long idx,
                    @Shared("getIndex") @Cached LLVMForeignGetIndexPointerNode getElementPointer,
                    @Exclusive @Cached LLVMForeignReadNode read) throws UnsupportedMessageException, InvalidArrayIndexException {
        LLVMPointer ptr = getElementPointer.execute(receiver.getExportType(), receiver, idx);
        return read.execute(ptr, ptr.getExportType());
    }

    @ExportMessage
    static boolean isArrayElementModifiable(LLVMPointerImpl receiver, long idx,
                    @Shared("isArray") @Cached ConditionProfile isArray) {
        if (isArray.profile(receiver.getExportType() instanceof LLVMInteropType.Array)) {
            LLVMInteropType.Array arrayType = (LLVMInteropType.Array) receiver.getExportType();
            if (arrayType.elementType instanceof LLVMInteropType.Value) {
                long length = arrayType.length;
                return Long.compareUnsigned(idx, length) < 0;
            } else {
                // embedded structured type, write not possible
                return false;
            }
        } else {
            return false;
        }
    }

    /**
     * @param receiver
     * @param idx
     * @see InteropLibrary#isArrayElementInsertable(Object, long)
     */
    @ExportMessage
    static boolean isArrayElementInsertable(LLVMPointerImpl receiver, long idx) {
        // native arrays have fixed size, new elements can't be inserted
        return false;
    }

    @ExportMessage
    static void writeArrayElement(LLVMPointerImpl receiver, long idx, Object value,
                    @Shared("getIndex") @Cached LLVMForeignGetIndexPointerNode getElementPointer,
                    @Exclusive @Cached LLVMForeignWriteNode write) throws UnsupportedMessageException, InvalidArrayIndexException {
        LLVMPointer ptr = getElementPointer.execute(receiver.getExportType(), receiver, idx);
        write.execute(ptr, ptr.getExportType(), value);
    }

    @ExportLibrary(InteropLibrary.class)
    public static final class Keys implements TruffleObject {

        private final LLVMInteropType.Struct type;

        private Keys(LLVMInteropType.Struct type) {
            this.type = type;
        }

        @ExportMessage
        boolean hasArrayElements() {
            return true;
        }

        @ExportMessage
        long getArraySize() {
            return type.getMemberCount();
        }

        @ExportMessage
        boolean isArrayElementReadable(long idx) {
            return Long.compareUnsigned(idx, getArraySize()) < 0;
        }

        @ExportMessage
        Object readArrayElement(long idx,
                        @Cached BranchProfile exception) throws InvalidArrayIndexException {
            try {
                return type.getMember((int) idx).name;
            } catch (IndexOutOfBoundsException ex) {
                exception.enter();
                throw InvalidArrayIndexException.create(idx);
            }
        }
    }

    @ExportLibrary(InteropLibrary.class)
    public static final class ClassKeys implements TruffleObject {
        private final LLVMInteropType.Clazz type;

        private ClassKeys(LLVMInteropType.Clazz type) {
            this.type = type;
        }

        @ExportMessage
        boolean hasArrayElements() {
            return true;
        }

        @ExportMessage
        long getArraySize() {
            return type.getMemberCount() + type.getMethodCount();
        }

        @ExportMessage
        boolean isArrayElementReadable(long idx) {
            return Long.compareUnsigned(idx, getArraySize()) < 0;
        }

        @ExportMessage
        Object readArrayElement(long idx,
                        @Cached BranchProfile exception) throws InvalidArrayIndexException {
            try {
                int index = (int) idx;
                if (index < type.getMemberCount()) {
                    return type.getMember(index).name;
                } else {
                    return type.getMethod(index - type.getMemberCount()).getName();
                }
            } catch (IndexOutOfBoundsException ex) {
                exception.enter();
                throw InvalidArrayIndexException.create(idx);
            }
        }
    }

    /**
     * @param receiver
     * @see InteropLibrary#hasLanguage(Object)
     */
    @ExportMessage
    static boolean hasLanguage(LLVMPointerImpl receiver) {
        return true;
    }

    @ExportMessage
    static Class<? extends TruffleLanguage<?>> getLanguage(@SuppressWarnings("unused") LLVMPointerImpl receiver) {
        return LLVMLanguage.class;
    }

    @ExportMessage
    @TruffleBoundary
    static String toDisplayString(LLVMPointerImpl receiver, @SuppressWarnings("unused") boolean allowSideEffects) {
        return receiver.toString();
    }

    @ExportMessage
    static Object getMetaObject(LLVMPointerImpl receiver) throws UnsupportedMessageException {
        Object type = receiver.getExportType();
        if (type != null) {
            return type;
        }
        throw UnsupportedMessageException.create();
    }

    @ExportMessage
    static boolean hasMetaObject(LLVMPointerImpl receiver) {
        return receiver.getExportType() != null;
    }

    @ExportMessage
    static class IsIdenticalOrUndefined {

        @Specialization
        static TriState doPointer(LLVMPointerImpl receiver, LLVMPointerImpl other,
                        @Cached LLVMAddressEqualsNode.Operation equals) {
            return TriState.valueOf(equals.executeWithTarget(receiver, other));
        }

        @Fallback
        static TriState doOther(@SuppressWarnings("unused") LLVMPointerImpl receiver, @SuppressWarnings("unused") Object other) {
            return TriState.UNDEFINED;
        }
    }

    @ExportMessage
    static int identityHashCode(@SuppressWarnings("unused") LLVMPointerImpl receiver) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        throw new AbstractMethodError(); // overridden in {Native,Managed}PointerLibraries
    }
}
