/*
 * Copyright (c) 2018, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.runtime.interop.export;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleLanguage.ContextReference;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.CanResolve;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.KeyInfo;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.MessageResolution;
import com.oracle.truffle.api.interop.Resolve;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.llvm.runtime.LLVMContext;
import com.oracle.truffle.llvm.runtime.LLVMFunctionDescriptor;
import com.oracle.truffle.llvm.runtime.LLVMLanguage;
import com.oracle.truffle.llvm.runtime.interop.LLVMForeignCallNode;
import com.oracle.truffle.llvm.runtime.interop.access.LLVMInteropType;
import com.oracle.truffle.llvm.runtime.interop.export.LLVMPointerMessageResolutionFactory.AsPointerCachedNodeGen;
import com.oracle.truffle.llvm.runtime.interop.export.LLVMPointerMessageResolutionFactory.CanExecuteHelperNodeGen;
import com.oracle.truffle.llvm.runtime.interop.export.LLVMPointerMessageResolutionFactory.ExecuteCachedNodeGen;
import com.oracle.truffle.llvm.runtime.interop.export.LLVMPointerMessageResolutionFactory.IsPointerCachedNodeGen;
import com.oracle.truffle.llvm.runtime.interop.export.LLVMPointerMessageResolutionFactory.ToNativeCachedNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMObjectNativeLibrary;
import com.oracle.truffle.llvm.runtime.pointer.LLVMManagedPointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMNativePointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMPointer;

@MessageResolution(receiverType = LLVMPointer.class)
public class LLVMPointerMessageResolution {

    @CanResolve
    public abstract static class IsInstance extends Node {

        protected boolean test(TruffleObject receiver) {
            return LLVMPointer.isInstance(receiver);
        }
    }

    @Resolve(message = "IS_NULL")
    public abstract static class IsNull extends Node {

        protected boolean access(LLVMPointer receiver) {
            return receiver.isNull();
        }
    }

    @Resolve(message = "IS_POINTER")
    public abstract static class IsPointer extends Node {

        @Child IsPointerCached isPointer = IsPointerCachedNodeGen.create();

        protected boolean access(LLVMPointer receiver) {
            return isPointer.execute(receiver);
        }
    }

    abstract static class IsPointerCached extends Node {

        protected abstract boolean execute(Object receiver);

        @Specialization(guards = "lib.guard(receiver)")
        boolean doCached(Object receiver,
                        @Cached("createCached(receiver)") LLVMObjectNativeLibrary lib) {
            return lib.isPointer(receiver);
        }

        @Specialization(replaces = "doCached")
        boolean doGeneric(Object receiver,
                        @Cached("createGeneric()") LLVMObjectNativeLibrary lib) {
            return lib.isPointer(receiver);
        }
    }

    @Resolve(message = "AS_POINTER")
    public abstract static class AsPointer extends Node {

        @Child AsPointerCached asPointer = AsPointerCachedNodeGen.create();

        protected long access(LLVMPointer receiver) {
            return asPointer.execute(receiver);
        }
    }

    abstract static class AsPointerCached extends Node {

        protected abstract long execute(Object receiver);

        @Specialization(guards = "lib.guard(receiver)")
        long doCached(Object receiver,
                        @Cached("createCached(receiver)") LLVMObjectNativeLibrary lib) {
            try {
                return lib.asPointer(receiver);
            } catch (InteropException ex) {
                throw ex.raise();
            }
        }

        @Specialization(replaces = "doCached")
        long doGeneric(Object receiver,
                        @Cached("createGeneric()") LLVMObjectNativeLibrary lib) {
            try {
                return lib.asPointer(receiver);
            } catch (InteropException ex) {
                throw ex.raise();
            }
        }
    }

    @Resolve(message = "TO_NATIVE")
    public abstract static class ToNative extends Node {

        @Child ToNativeCached toNative = ToNativeCachedNodeGen.create();

        protected Object access(LLVMPointer receiver) {
            return toNative.execute(receiver);
        }
    }

    abstract static class ToNativeCached extends Node {

        protected abstract Object execute(Object receiver);

        @Specialization(guards = "lib.guard(receiver)")
        Object doCached(Object receiver,
                        @Cached("createCached(receiver)") LLVMObjectNativeLibrary lib) {
            try {
                return lib.toNative(receiver);
            } catch (InteropException ex) {
                throw ex.raise();
            }
        }

        @Specialization(replaces = "doCached")
        Object doGeneric(Object receiver,
                        @Cached("createGeneric()") LLVMObjectNativeLibrary lib) {
            try {
                return lib.toNative(receiver);
            } catch (InteropException ex) {
                throw ex.raise();
            }
        }
    }

    @Resolve(message = "HAS_SIZE")
    public abstract static class HasSize extends Node {

        protected boolean access(LLVMPointer receiver) {
            return receiver.getExportType() instanceof LLVMInteropType.Array;
        }
    }

    @Resolve(message = "GET_SIZE")
    public abstract static class GetSize extends Node {

        protected long access(LLVMPointer receiver) {
            if (!(receiver.getExportType() instanceof LLVMInteropType.Array)) {
                CompilerDirectives.transferToInterpreter();
                throw UnsupportedMessageException.raise(Message.GET_SIZE);
            }

            LLVMInteropType.Array array = (LLVMInteropType.Array) receiver.getExportType();
            return array.getLength();
        }
    }

    @Resolve(message = "HAS_KEYS")
    public abstract static class HasKeys extends Node {

        protected boolean access(LLVMPointer receiver) {
            return receiver.getExportType() instanceof LLVMInteropType.Struct;
        }
    }

    @Resolve(message = "KEYS")
    public abstract static class GetKeys extends Node {

        protected TruffleObject access(LLVMPointer receiver) {
            if (!(receiver.getExportType() instanceof LLVMInteropType.Struct)) {
                CompilerDirectives.transferToInterpreter();
                throw UnsupportedMessageException.raise(Message.KEYS);
            }

            LLVMInteropType.Struct struct = (LLVMInteropType.Struct) receiver.getExportType();
            return new Keys(struct);
        }
    }

    @Resolve(message = "KEY_INFO")
    public abstract static class GetKeyInfo extends Node {

        protected int access(LLVMPointer receiver, String ident) {
            if (!(receiver.getExportType() instanceof LLVMInteropType.Struct)) {
                return KeyInfo.NONE;
            }

            LLVMInteropType.Struct struct = (LLVMInteropType.Struct) receiver.getExportType();
            LLVMInteropType.StructMember member = struct.findMember(ident);
            if (member == null) {
                // does not exist
                return KeyInfo.NONE;
            } else if (member.getType() instanceof LLVMInteropType.Value) {
                // primitive or pointer, can be read or written
                return KeyInfo.READABLE | KeyInfo.MODIFIABLE;
            } else {
                assert member.getType() instanceof LLVMInteropType.Structured;
                // inline struct or array, can be read but not overwritten
                return KeyInfo.READABLE;
            }
        }

        protected int access(LLVMPointer receiver, Number key) {
            if (!(receiver.getExportType() instanceof LLVMInteropType.Array)) {
                return KeyInfo.NONE;
            }

            LLVMInteropType.Array array = (LLVMInteropType.Array) receiver.getExportType();
            long idx = key.longValue();
            if (Long.compareUnsigned(idx, array.getLength()) >= 0) {
                // out of bounds
                return KeyInfo.NONE;
            } else if (array.getElementType() instanceof LLVMInteropType.Value) {
                // primitive or pointer, can be read or written
                return KeyInfo.READABLE | KeyInfo.MODIFIABLE;
            } else {
                assert array.getElementType() instanceof LLVMInteropType.Structured;
                // array of structs or multi-dimensional array, can be read but not overwritten
                return KeyInfo.READABLE;
            }
        }
    }

    @Resolve(message = "READ")
    public abstract static class Read extends Node {

        @Child LLVMForeignGetElementPointerNode getElementPointer = LLVMForeignGetElementPointerNodeGen.create();
        @Child LLVMForeignAccessNode.Read read = LLVMForeignAccessNode.createRead();

        protected Object access(LLVMPointer receiver, String ident) {
            LLVMPointer ptr = getElementPointer.execute(receiver.getExportType(), receiver, ident);
            return read.execute(ptr, ptr.getExportType());
        }

        protected Object access(LLVMPointer receiver, Number idx) {
            LLVMPointer ptr = getElementPointer.execute(receiver.getExportType(), receiver, idx.longValue());
            return read.execute(ptr, ptr.getExportType());
        }
    }

    @Resolve(message = "WRITE")
    public abstract static class Write extends Node {

        @Child LLVMForeignGetElementPointerNode getElementPointer = LLVMForeignGetElementPointerNodeGen.create();
        @Child LLVMForeignAccessNode.Write write = LLVMForeignAccessNode.createWrite();

        protected Object access(LLVMPointer receiver, String ident, Object value) {
            LLVMPointer ptr = getElementPointer.execute(receiver.getExportType(), receiver, ident);
            doWrite(ptr, value);
            return value;
        }

        protected Object access(LLVMPointer receiver, Number idx, Object value) {
            LLVMPointer ptr = getElementPointer.execute(receiver.getExportType(), receiver, idx.longValue());
            doWrite(ptr, value);
            return value;
        }

        private void doWrite(LLVMPointer ptr, Object value) {
            LLVMInteropType type = ptr.getExportType();
            if (!(type instanceof LLVMInteropType.Value)) {
                // embedded structured type, write not possible
                CompilerDirectives.transferToInterpreter();
                throw UnsupportedMessageException.raise(Message.WRITE);
            }

            write.execute(ptr, (LLVMInteropType.Value) type, value);
        }
    }

    @Resolve(message = "IS_EXECUTABLE")
    public abstract static class CanExecute extends Node {

        @Child CanExecuteHelper canExecute = CanExecuteHelperNodeGen.create();

        protected boolean access(LLVMPointer receiver) {
            return canExecute.execute(receiver);
        }
    }

    abstract static class CanExecuteHelper extends LLVMNode {

        private final ContextReference<LLVMContext> ctxRef = LLVMLanguage.getLLVMContextReference();

        protected abstract boolean execute(LLVMPointer receiver);

        @Specialization
        boolean doNative(LLVMNativePointer receiver) {
            return ctxRef.get().getFunctionDescriptor(receiver) != null;
        }

        @Specialization
        boolean doDirect(LLVMManagedPointer receiver) {
            return receiver.getObject() instanceof LLVMFunctionDescriptor && receiver.getOffset() == 0;
        }
    }

    @Resolve(message = "EXECUTE")
    public abstract static class Execute extends Node {

        @Child ExecuteCached executeCached = ExecuteCachedNodeGen.create();

        protected Object access(LLVMPointer receiver, Object[] args) {
            return executeCached.execute(receiver, args);
        }
    }

    abstract static class ExecuteCached extends LLVMNode {

        private final ContextReference<LLVMContext> ctxRef = LLVMLanguage.getLLVMContextReference();

        protected abstract Object execute(LLVMPointer receiver, Object[] args);

        @Specialization(guards = {"value.asNative() == cachedAddress", "cachedDescriptor != null"})
        Object doNativeCached(@SuppressWarnings("unused") LLVMNativePointer value, Object[] args,
                        @Cached("value.asNative()") @SuppressWarnings("unused") long cachedAddress,
                        @Cached("getDescriptor(value)") LLVMFunctionDescriptor cachedDescriptor,
                        @Cached("create()") LLVMForeignCallNode foreignCall) {
            return foreignCall.executeCall(cachedDescriptor, args);
        }

        @Specialization(replaces = "doNativeCached")
        Object doNative(LLVMNativePointer value, Object[] args,
                        @Cached("create()") LLVMForeignCallNode foreignCall) {
            LLVMFunctionDescriptor descriptor = getDescriptor(value);
            if (descriptor != null) {
                return foreignCall.executeCall(descriptor, args);
            } else {
                CompilerDirectives.transferToInterpreter();
                return doFallback(value, args);
            }
        }

        @Specialization(guards = {"isSameObject(value.getObject(), cachedDescriptor)", "cachedDescriptor != null", "value.getOffset() == 0"})
        Object doManagedCached(@SuppressWarnings("unused") LLVMManagedPointer value, Object[] args,
                        @Cached("asFunctionDescriptor(value.getObject())") LLVMFunctionDescriptor cachedDescriptor,
                        @Cached("create()") LLVMForeignCallNode foreignCall) {
            return foreignCall.executeCall(cachedDescriptor, args);
        }

        @Specialization(guards = {"isFunctionDescriptor(value.getObject())", "value.getOffset() == 0"}, replaces = "doManagedCached")
        Object doManaged(LLVMManagedPointer value, Object[] args,
                        @Cached("create()") LLVMForeignCallNode foreignCall) {
            return foreignCall.executeCall((LLVMFunctionDescriptor) value.getObject(), args);
        }

        @Fallback
        @TruffleBoundary
        Object doFallback(@SuppressWarnings("unused") LLVMPointer value, @SuppressWarnings("unused") Object[] args) {
            throw UnsupportedMessageException.raise(Message.EXECUTE);
        }

        protected LLVMFunctionDescriptor getDescriptor(LLVMNativePointer value) {
            return ctxRef.get().getFunctionDescriptor(value);
        }
    }

    @MessageResolution(receiverType = Keys.class)
    public static final class Keys implements TruffleObject {

        private final LLVMInteropType.Struct type;

        private Keys(LLVMInteropType.Struct type) {
            this.type = type;
        }

        @Override
        public ForeignAccess getForeignAccess() {
            return KeysForeign.ACCESS;
        }

        static boolean isInstance(TruffleObject object) {
            return object instanceof Keys;
        }

        @Resolve(message = "GET_SIZE")
        abstract static class GetSize extends Node {

            int access(Keys receiver) {
                return receiver.type.getMemberCount();
            }
        }

        @Resolve(message = "READ")
        abstract static class Read extends Node {

            Object access(Keys receiver, int index) {
                try {
                    return receiver.type.getMember(index).getName();
                } catch (IndexOutOfBoundsException ex) {
                    CompilerDirectives.transferToInterpreter();
                    throw UnknownIdentifierException.raise(Integer.toString(index));
                }
            }
        }
    }
}
