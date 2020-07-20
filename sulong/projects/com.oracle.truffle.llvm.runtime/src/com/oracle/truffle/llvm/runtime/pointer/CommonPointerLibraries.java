/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates.
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
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.api.utilities.TriState;
import com.oracle.truffle.llvm.runtime.LLVMLanguage;
import com.oracle.truffle.llvm.runtime.interop.access.LLVMInteropType;
import com.oracle.truffle.llvm.runtime.interop.export.LLVMForeignGetIndexPointerNode;
import com.oracle.truffle.llvm.runtime.interop.export.LLVMForeignGetMemberPointerNode;
import com.oracle.truffle.llvm.runtime.interop.export.LLVMForeignReadNode;
import com.oracle.truffle.llvm.runtime.interop.export.LLVMForeignWriteNode;
import com.oracle.truffle.llvm.runtime.nodes.op.LLVMAddressEqualsNode;
import com.oracle.truffle.llvm.spi.ReferenceLibrary;

@ExportLibrary(value = InteropLibrary.class, receiverType = LLVMPointerImpl.class)
@ExportLibrary(value = ReferenceLibrary.class, receiverType = LLVMPointerImpl.class)
@SuppressWarnings({"static-method", "deprecation"})
// implements deprecated ReferenceLibrary for backwards compatibility
abstract class CommonPointerLibraries {

    @ExportMessage
    static boolean hasMembers(LLVMPointerImpl receiver) {
        return receiver.getExportType() instanceof LLVMInteropType.Struct;
    }

    @ExportMessage
    @SuppressWarnings("unused")
    static Object getMembers(LLVMPointerImpl receiver, boolean includeInternal,
                    @Shared("isObject") @Cached ConditionProfile isObject) throws UnsupportedMessageException {
        if (isObject.profile(receiver.getExportType() instanceof LLVMInteropType.Struct)) {
            LLVMInteropType.Struct struct = (LLVMInteropType.Struct) receiver.getExportType();
            return new Keys(struct);
        } else {
            throw UnsupportedMessageException.create();
        }
    }

    @ExportMessage
    static boolean isMemberReadable(LLVMPointerImpl receiver, String ident,
                    @Shared("isObject") @Cached ConditionProfile isObject) {
        if (isObject.profile(receiver.getExportType() instanceof LLVMInteropType.Struct)) {
            LLVMInteropType.Struct struct = (LLVMInteropType.Struct) receiver.getExportType();
            LLVMInteropType.StructMember member = struct.findMember(ident);
            return member != null;
        } else {
            return false;
        }
    }

    @ExportMessage
    static Object readMember(LLVMPointerImpl receiver, String ident,
                    @Shared("getMember") @Cached LLVMForeignGetMemberPointerNode getElementPointer,
                    @Exclusive @Cached LLVMForeignReadNode read) throws UnsupportedMessageException, UnknownIdentifierException {
        LLVMPointer ptr = getElementPointer.execute(receiver.getExportType(), receiver, ident);
        return read.execute(ptr, ptr.getExportType());
    }

    @ExportMessage
    static boolean isMemberModifiable(LLVMPointerImpl receiver, String ident,
                    @Shared("isObject") @Cached ConditionProfile isObject) {
        if (isObject.profile(receiver.getExportType() instanceof LLVMInteropType.Struct)) {
            LLVMInteropType.Struct struct = (LLVMInteropType.Struct) receiver.getExportType();
            LLVMInteropType.StructMember member = struct.findMember(ident);
            if (member == null) {
                // not found
                return false;
            } else {
                return member.getType() instanceof LLVMInteropType.Value;
            }
        } else {
            return false;
        }
    }

    @ExportMessage
    @SuppressWarnings("unused")
    static boolean isMemberInsertable(LLVMPointerImpl receiver, String ident) {
        return false;
    }

    @ExportMessage
    static void writeMember(LLVMPointerImpl receiver, String ident, Object value,
                    @Shared("getMember") @Cached LLVMForeignGetMemberPointerNode getElementPointer,
                    @Exclusive @Cached LLVMForeignWriteNode write) throws UnsupportedMessageException, UnknownIdentifierException {
        LLVMPointer ptr = getElementPointer.execute(receiver.getExportType(), receiver, ident);
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
            return ((LLVMInteropType.Array) receiver.getExportType()).getLength();
        } else {
            throw UnsupportedMessageException.create();
        }
    }

    @ExportMessage
    static boolean isArrayElementReadable(LLVMPointerImpl receiver, long idx,
                    @Shared("isArray") @Cached ConditionProfile isArray) {
        if (isArray.profile(receiver.getExportType() instanceof LLVMInteropType.Array)) {
            long length = ((LLVMInteropType.Array) receiver.getExportType()).getLength();
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
            if (arrayType.getElementType() instanceof LLVMInteropType.Value) {
                long length = arrayType.getLength();
                return Long.compareUnsigned(idx, length) < 0;
            } else {
                // embedded structured type, write not possible
                return false;
            }
        } else {
            return false;
        }
    }

    @ExportMessage
    @SuppressWarnings("unused")
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
                return type.getMember((int) idx).getName();
            } catch (IndexOutOfBoundsException ex) {
                exception.enter();
                throw InvalidArrayIndexException.create(idx);
            }
        }
    }

    @ExportMessage
    static class IsSame {

        @Specialization
        static boolean doNative(LLVMPointerImpl receiver, LLVMPointerImpl other,
                        @Cached LLVMAddressEqualsNode equals) {
            return equals.executeWithTarget(receiver, other);
        }

        @Fallback
        @SuppressWarnings("unused")
        static boolean doOther(LLVMPointerImpl receiver, Object other) {
            return false;
        }
    }

    @ExportMessage
    @SuppressWarnings("unused")
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
                        @Cached LLVMAddressEqualsNode equals) {
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
