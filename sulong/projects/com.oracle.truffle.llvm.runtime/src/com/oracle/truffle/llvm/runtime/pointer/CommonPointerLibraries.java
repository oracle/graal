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
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.CachedContext;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.ImportStatic;
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
import com.oracle.truffle.llvm.runtime.LLVMContext;
import com.oracle.truffle.llvm.runtime.LLVMFunction;
import com.oracle.truffle.llvm.runtime.LLVMLanguage;
import com.oracle.truffle.llvm.runtime.except.LLVMLinkerException;
import com.oracle.truffle.llvm.runtime.interop.access.LLVMInteropType;
import com.oracle.truffle.llvm.runtime.interop.access.LLVMInteropType.Clazz;
import com.oracle.truffle.llvm.runtime.interop.access.LLVMInteropType.Method;
import com.oracle.truffle.llvm.runtime.interop.export.LLVMForeignGetIndexPointerNode;
import com.oracle.truffle.llvm.runtime.interop.export.LLVMForeignGetMemberPointerNode;
import com.oracle.truffle.llvm.runtime.interop.export.LLVMForeignReadNode;
import com.oracle.truffle.llvm.runtime.interop.export.LLVMForeignWriteNode;
import com.oracle.truffle.llvm.runtime.nodes.op.LLVMAddressEqualsNode;
import com.oracle.truffle.llvm.runtime.nodes.others.LLVMDynAccessSymbolNode;

@ExportLibrary(value = InteropLibrary.class, receiverType = LLVMPointerImpl.class)
@ExportLibrary(value = com.oracle.truffle.llvm.spi.ReferenceLibrary.class, receiverType = LLVMPointerImpl.class)
@SuppressWarnings({"static-method", "deprecation"})
// implements deprecated ReferenceLibrary for backwards compatibility
abstract class CommonPointerLibraries {

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
                    @Shared("getMember") @Cached LLVMForeignGetMemberPointerNode getElementPointer,
                    @Exclusive @Cached LLVMForeignReadNode read) throws UnsupportedMessageException, UnknownIdentifierException {
        LLVMPointer ptr = getElementPointer.execute(receiver.getExportType(), receiver, ident);
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
    static boolean isMemberInvocable(LLVMPointerImpl receiver, String ident) {
        LLVMInteropType type = receiver.getExportType();
        if (type instanceof LLVMInteropType.Clazz) {
            LLVMInteropType.Clazz clazz = (LLVMInteropType.Clazz) type;
            return clazz.findMethod(ident) != null;
        }
        return false;
    }

    @ExportMessage
    @ImportStatic(LLVMLanguage.class)
    static class InvokeMember {
        /**
         * @param member
         * @param context
         * @param clazz
         * @param method
         * @param argCount
         * @param methodName
         * @param llvmFunction
         * @see InteropLibrary#invokeMember(Object, String, Object[])
         */
        @Specialization(guards = {"asClazz(receiver)==clazz", "member.equals(methodName)", "argCount==arguments.length"}, assumptions = "getLanguage().singleContextAssumption")
        static Object doCached(LLVMPointerImpl receiver, String member, Object[] arguments,
                        @CachedContext(LLVMLanguage.class) LLVMContext context,
                        @CachedLibrary(limit = "5") InteropLibrary interop,
                        @Cached(value = "asClazz(receiver)") LLVMInteropType.Clazz clazz,
                        @Cached(value = "clazz.findMethodByArguments(receiver, member, arguments)") Method method,
                        @Cached(value = "arguments.length") int argCount,
                        @Cached(value = "method.getName()") String methodName,
                        @Cached(value = "getLLVMFunction(context, method, clazz, member)") LLVMFunction llvmFunction)
                        throws UnsupportedMessageException, ArityException, UnsupportedTypeException {
            Object[] newArguments = addSelfObject(receiver, arguments);
            return interop.execute(context.getSymbol(llvmFunction), newArguments);
        }

        @Specialization(replaces = "doCached")
        static Object doResolve(LLVMPointerImpl receiver, String member, Object[] arguments,
                        @CachedContext(LLVMLanguage.class) LLVMContext context,
                        @CachedLibrary(limit = "5") InteropLibrary interop,
                        @Cached LLVMDynAccessSymbolNode dynAccessSymbolNode)
                        throws UnsupportedMessageException, ArityException, UnsupportedTypeException, UnknownIdentifierException {
            Object[] newArguments = addSelfObject(receiver, arguments);
            LLVMInteropType.Clazz newClazz = asClazz(receiver);
            Method newMethod = newClazz.findMethodByArguments(receiver, member, arguments);
            LLVMFunction newLLVMFunction = getLLVMFunction(context, newMethod, newClazz, member);
            Object newReceiver = dynAccessSymbolNode.execute(newLLVMFunction);
            return interop.execute(newReceiver, newArguments);
        }
    }

    static LLVMInteropType.Clazz asClazz(LLVMPointerImpl receiver) throws UnsupportedMessageException {
        LLVMInteropType type = receiver.getExportType();
        if (!(type instanceof LLVMInteropType.Clazz)) {
            throw UnsupportedMessageException.create();
        }
        return (Clazz) type;
    }

    static Object[] addSelfObject(Object receiver, Object[] rawArgs) {
        Object[] newArguments = new Object[rawArgs.length + 1];
        newArguments[0] = receiver;
        for (int i = 0; i < rawArgs.length; i++) {
            newArguments[i + 1] = rawArgs[i];
        }
        return newArguments;
    }

    static LLVMFunction getLLVMFunction(LLVMContext context, Method method, LLVMInteropType.Clazz clazz, String member) throws UnknownIdentifierException {
        if (method == null) {
            throw UnknownIdentifierException.create(member);
        }
        LLVMFunction llvmFunction = context.getGlobalScope().getFunction(method.getLinkageName());
        if (llvmFunction == null) {
            CompilerDirectives.transferToInterpreter();
            final String clazzName = clazz.toString().startsWith("class ") ? clazz.toString().substring(6) : clazz.toString();
            final String msg = String.format("No implementation of declared method %s::%s (%s) found", clazzName, method.getName(), method.getLinkageName());
            throw new LLVMLinkerException(msg);
        }
        return llvmFunction;
    }

    /**
     * @param receiver
     * @param ident
     * @see InteropLibrary#isMemberInsertable(Object, String)
     */
    @ExportMessage
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

    @ExportMessage
    static class IsSame {

        @Specialization
        static boolean doNative(LLVMPointerImpl receiver, LLVMPointerImpl other,
                        @Cached LLVMAddressEqualsNode.Operation equals) {
            return equals.executeWithTarget(receiver, other);
        }

        /**
         * @param receiver
         * @param other
         */
        @Fallback
        static boolean doOther(LLVMPointerImpl receiver, Object other) {
            return false;
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
