/*
 * Copyright (c) 2021, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.runtime.interop.access;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateAOT;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidBufferOffsetException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.llvm.runtime.except.LLVMPolyglotException;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMNode;
import com.oracle.truffle.llvm.runtime.nodes.memory.LLVMNativePointerSupport;
import com.oracle.truffle.llvm.runtime.nodes.memory.load.LLVMDerefHandleGetReceiverNode;
import com.oracle.truffle.llvm.runtime.pointer.LLVMManagedPointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMNativePointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMPointer;
import com.oracle.truffle.llvm.spi.NativeTypeLibrary;

//@formatter:off
/**
 * Access a foreign object.
 *
 * This will try to use the following strategies, in order:
 *
 * <li> If the object {@link InteropLibrary#isPointer}:
 * <ul>
 *      <li>If the pointer value is a deref handle, it is resolved. Then the following strategies
 *          are applied in order.
 *      <li>Otherwise, the value is interpreted as pointer to the native heap, and the access is
 *          performed from there.
 * </ul>
 * <li> If the object {@link NativeTypeLibrary#hasNativeType}, the read will be done using member
 *      access or array element access (see {@link LLVMInteropReadNode} or
 *      {@link LLVMInteropWriteNode}).
 * <li> If the object {@link InteropLibrary#hasBufferElements}, a buffer access will be used.
 */
public abstract class LLVMAccessForeignObjectNode extends LLVMNode {
//@formatter:on

    protected LLVMPolyglotException oob(BranchProfile oobProfile, InvalidBufferOffsetException ex) {
        oobProfile.enter();
        throw new LLVMPolyglotException(this, "Out-of-bounds buffer access (offset %d, length %d)", ex.getByteOffset(), ex.getLength());
    }

    @GenerateUncached
    abstract static class ResolveNativePointerNode extends LLVMNode {

        /**
         * Helper for guards to check whether {@code obj} is an auto-deref handle (e.g. a wrapped
         * pointer). This helper assumes that an isPointer call returns true for {@code obj}.
         */
        final boolean isWrappedAutoDerefHandle(LLVMNativePointerSupport.IsPointerNode isPointerNode,
                        LLVMNativePointerSupport.AsPointerNode asPointerNode, Object obj) {
            try {
                assert isPointerNode.execute(obj);
                return isAutoDerefHandle(asPointerNode.execute(obj));
            } catch (UnsupportedMessageException ex) {
                throw CompilerDirectives.shouldNotReachHere(ex);
            }
        }

        public abstract LLVMPointer execute(Object foreign, long offset);

        @Specialization(limit = "3", guards = {"isPointerNode.execute(receiver)", "!isWrappedAutoDerefHandle(isPointerNode, asPointerNode, receiver)"})
        LLVMNativePointer doPointer(Object receiver, long offset,
                        @SuppressWarnings("unused") @Cached LLVMNativePointerSupport.IsPointerNode isPointerNode,
                        @Cached LLVMNativePointerSupport.AsPointerNode asPointerNode) {
            try {
                long addr = asPointerNode.execute(receiver) + offset;
                return LLVMNativePointer.create(addr);
            } catch (UnsupportedMessageException ex) {
                throw CompilerDirectives.shouldNotReachHere(ex);
            }
        }

        @Specialization(limit = "3", guards = {"isPointerNode.execute(receiver)", "isWrappedAutoDerefHandle(isPointerNode, asPointerNode, receiver)"})
        LLVMManagedPointer doDerefHandle(Object receiver, long offset,
                        @Cached LLVMDerefHandleGetReceiverNode receiverNode,
                        @SuppressWarnings("unused") @Cached LLVMNativePointerSupport.IsPointerNode isPointerNode,
                        @Cached LLVMNativePointerSupport.AsPointerNode asPointerNode) {
            try {
                long addr = asPointerNode.execute(receiver) + offset;
                return receiverNode.execute(addr);
            } catch (UnsupportedMessageException ex) {
                throw CompilerDirectives.shouldNotReachHere(ex);
            }
        }

        @Specialization(limit = "3", guards = "!isPointerNode.execute(receiver)")
        LLVMManagedPointer doManaged(Object receiver, long offset,
                        @SuppressWarnings("unused") @Cached LLVMNativePointerSupport.IsPointerNode isPointerNode) {
            return LLVMManagedPointer.create(receiver).increment(offset);
        }
    }

    @GenerateUncached
    abstract static class GetForeignTypeNode extends LLVMNode {

        public abstract Object execute(LLVMPointer pointer);

        @Specialization
        Object doNative(@SuppressWarnings("unused") LLVMNativePointer pointer) {
            // native pointers don't need an interop type, access is direct anyway
            return null;
        }

        @Specialization(guards = "types.hasNativeType(pointer.getObject())")
        Object doManagedWithType(LLVMManagedPointer pointer,
                        @CachedLibrary(limit = "3") NativeTypeLibrary types) {
            // known type, either from the object or user-specified
            return types.getNativeType(pointer.getObject());
        }

        @Specialization(limit = "3", guards = {"!types.hasNativeType(pointer.getObject())", "interop.hasBufferElements(pointer.getObject())"})
        @GenerateAOT.Exclude
        LLVMInteropType.Buffer doManagedBuffer(@SuppressWarnings("unused") LLVMManagedPointer pointer,
                        @SuppressWarnings("unused") @CachedLibrary("pointer.getObject()") NativeTypeLibrary types,
                        @SuppressWarnings("unused") @CachedLibrary("pointer.getObject()") InteropLibrary interop) {
            // no user-specified type, but implements the interop buffer API
            return LLVMInteropType.BUFFER;
        }

        @Specialization(limit = "3", guards = {"!types.hasNativeType(pointer.getObject())", "!interop.hasBufferElements(pointer.getObject())"})
        @GenerateAOT.Exclude
        Object doManagedUnknown(@SuppressWarnings("unused") LLVMManagedPointer pointer,
                        @SuppressWarnings("unused") @CachedLibrary("pointer.getObject()") NativeTypeLibrary types,
                        @SuppressWarnings("unused") @CachedLibrary("pointer.getObject()") InteropLibrary interop) {
            // unknown type, and not a buffer
            return null;
        }
    }

    abstract static class ForeignDummy extends LLVMNode {

        static ForeignDummy create() {
            return null;
        }

        abstract Object execute();
    }

    abstract static class OffsetDummy extends LLVMNode {

        static OffsetDummy create() {
            return null;
        }

        abstract long execute();
    }
}
