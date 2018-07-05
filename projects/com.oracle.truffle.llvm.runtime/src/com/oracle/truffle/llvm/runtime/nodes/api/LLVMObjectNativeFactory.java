/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.runtime.nodes.api;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.object.ObjectType;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMObjectNativeFactoryFactory.CachedAsPointerNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMObjectNativeFactoryFactory.CachedIsPointerNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMObjectNativeFactoryFactory.CachedToNativeNodeGen;

abstract class LLVMObjectNativeFactory {

    private static final LongLibrary LONG_LIBRARY = new LongLibrary();

    @TruffleBoundary
    public static LLVMObjectNativeLibrary createCached(Object obj) {
        if (obj instanceof Long) {
            return LONG_LIBRARY;
        } else if (obj instanceof LLVMObjectNativeLibrary.Provider) {
            return ((LLVMObjectNativeLibrary.Provider) obj).createLLVMObjectNativeLibrary();
        } else if (obj instanceof DynamicObject) {
            ObjectType objectType = ((DynamicObject) obj).getShape().getObjectType();
            if (objectType instanceof LLVMObjectNativeLibrary.Provider) {
                return ((LLVMObjectNativeLibrary.Provider) objectType).createLLVMObjectNativeLibrary();
            }
        }

        if (obj instanceof TruffleObject) {
            return new FallbackLibrary();
        } else {
            return new UnsupportedLibrary();
        }
    }

    public static LLVMObjectNativeLibrary createGeneric() {
        return new CachingLibrary();
    }

    private static class LongLibrary extends LLVMObjectNativeLibrary {
        @Override
        public boolean guard(Object obj) {
            return obj instanceof Long;
        }

        @Override
        public boolean isPointer(Object obj) {
            return true;
        }

        @Override
        public long asPointer(Object obj) throws InteropException {
            return (long) obj;
        }

        @Override
        public Object toNative(Object obj) throws InteropException {
            throw new IllegalStateException("Should never be invoked as isPointer is always true.");
        }
    }

    private static class FallbackLibrary extends LLVMObjectNativeLibrary {

        @Child private Node isPointer;
        @Child private Node asPointer;
        @Child private Node toNative;

        @Override
        public boolean guard(Object obj) {
            if (!(obj instanceof TruffleObject)) {
                return false;
            }
            if (obj instanceof DynamicObject) {
                return !(((DynamicObject) obj).getShape().getObjectType() instanceof LLVMObjectNativeLibrary.Provider);
            } else {
                return !(obj instanceof LLVMObjectNativeLibrary);
            }
        }

        @Override
        public boolean isPointer(Object obj) {
            if (isPointer == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                isPointer = insert(Message.IS_POINTER.createNode());
            }
            return ForeignAccess.sendIsPointer(isPointer, (TruffleObject) obj);
        }

        @Override
        public long asPointer(Object obj) throws InteropException {
            if (asPointer == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                asPointer = insert(Message.AS_POINTER.createNode());
            }
            return ForeignAccess.sendAsPointer(asPointer, (TruffleObject) obj);
        }

        @Override
        public Object toNative(Object obj) throws InteropException {
            if (toNative == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                toNative = insert(Message.TO_NATIVE.createNode());
            }
            return ForeignAccess.sendToNative(toNative, (TruffleObject) obj);
        }
    }

    private static class UnsupportedLibrary extends LLVMObjectNativeLibrary {

        @Override
        public boolean guard(Object obj) {
            return !(obj instanceof LLVMObjectNativeLibrary) && !(obj instanceof TruffleObject);
        }

        @Override
        public boolean isPointer(Object obj) {
            return false;
        }

        @Override
        public long asPointer(Object obj) throws InteropException {
            throw UnsupportedMessageException.raise(Message.AS_POINTER);
        }

        @Override
        public Object toNative(Object obj) throws InteropException {
            throw UnsupportedMessageException.raise(Message.TO_NATIVE);
        }
    }

    private static class CachingLibrary extends LLVMObjectNativeLibrary {

        @Child private CachedIsPointerNode isPointer;
        @Child private CachedAsPointerNode asPointer;
        @Child private CachedToNativeNode toNative;

        @Override
        public boolean guard(Object obj) {
            return true;
        }

        @Override
        public boolean isPointer(Object obj) {
            if (isPointer == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                isPointer = insert(CachedIsPointerNodeGen.create());
            }
            return isPointer.execute(obj);
        }

        @Override
        public long asPointer(Object obj) throws InteropException {
            if (asPointer == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                asPointer = insert(CachedAsPointerNodeGen.create());
            }
            return asPointer.execute(obj);
        }

        @Override
        public Object toNative(Object obj) throws InteropException {
            if (toNative == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                toNative = insert(CachedToNativeNodeGen.create());
            }
            return toNative.execute(obj);
        }
    }

    abstract static class CachedIsPointerNode extends LLVMNode {

        static final int TYPE_LIMIT = 8;

        abstract boolean execute(Object obj);

        @Specialization(limit = "TYPE_LIMIT", guards = "lib.guard(obj)")
        protected boolean isPointer(Object obj,
                        @Cached("createCached(obj)") LLVMObjectNativeLibrary lib) {
            return lib.isPointer(obj);
        }

        @Specialization(replaces = "isPointer")
        protected boolean slowpath(Object obj) {
            LLVMObjectNativeLibrary lib = createCached(obj);
            return isPointer(obj, lib);
        }
    }

    abstract static class CachedAsPointerNode extends LLVMNode {

        static final int TYPE_LIMIT = 8;

        abstract long execute(Object obj) throws InteropException;

        @Specialization(limit = "TYPE_LIMIT", guards = "lib.guard(obj)")
        protected long asPointer(Object obj,
                        @Cached("createCached(obj)") LLVMObjectNativeLibrary lib) {
            try {
                return lib.asPointer(obj);
            } catch (InteropException ex) {
                CompilerDirectives.transferToInterpreter();
                throw ex.raise();
            }
        }

        @Specialization(replaces = "asPointer")
        protected long slowpath(Object obj) {
            LLVMObjectNativeLibrary lib = createCached(obj);
            return asPointer(obj, lib);
        }
    }

    abstract static class CachedToNativeNode extends LLVMNode {

        static final int TYPE_LIMIT = 8;

        abstract Object execute(Object obj) throws InteropException;

        @Specialization(limit = "TYPE_LIMIT", guards = "lib.guard(obj)")
        protected Object toNative(Object obj,
                        @Cached("createCached(obj)") LLVMObjectNativeLibrary lib) {
            try {
                return lib.toNative(obj);
            } catch (InteropException ex) {
                CompilerDirectives.transferToInterpreter();
                throw ex.raise();
            }
        }

        @Specialization(replaces = "toNative")
        protected Object slowpath(Object obj) {
            LLVMObjectNativeLibrary lib = createCached(obj);
            return toNative(obj, lib);
        }
    }
}
