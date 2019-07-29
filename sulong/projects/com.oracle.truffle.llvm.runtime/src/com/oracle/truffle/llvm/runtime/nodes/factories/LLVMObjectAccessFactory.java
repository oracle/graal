/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.runtime.nodes.factories;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.object.ObjectType;
import com.oracle.truffle.llvm.runtime.except.LLVMPolyglotException;
import com.oracle.truffle.llvm.runtime.interop.LLVMDataEscapeNode;
import com.oracle.truffle.llvm.runtime.interop.convert.ForeignToLLVM.ForeignToLLVMType;
import com.oracle.truffle.llvm.runtime.interop.convert.ToLLVM;
import com.oracle.truffle.llvm.runtime.interop.convert.ToLLVMNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMObjectAccess;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMObjectAccess.LLVMObjectReadNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMObjectAccess.LLVMObjectWriteNode;
import com.oracle.truffle.llvm.runtime.nodes.factories.LLVMObjectAccessFactoryFactory.CachedReadNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.factories.LLVMObjectAccessFactoryFactory.CachedWriteNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.factories.LLVMObjectAccessFactoryFactory.FallbackWriteNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.factories.LLVMObjectAccessFactoryFactory.GetWriteIdentifierNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.factories.LLVMObjectAccessFactoryFactory.UseLLVMObjectAccessNodeGen;

public abstract class LLVMObjectAccessFactory {

    public static LLVMObjectReadNode createRead() {
        return CachedReadNodeGen.create();
    }

    public static LLVMObjectWriteNode createWrite() {
        return CachedWriteNodeGen.create();
    }

    abstract static class CachedReadNode extends LLVMNode implements LLVMObjectReadNode {

        static final int TYPE_LIMIT = 8;

        @Override
        public boolean canAccess(Object obj) {
            return obj instanceof LLVMObjectAccess || obj instanceof TruffleObject;
        }

        @Specialization(limit = "TYPE_LIMIT", guards = "impl.canAccess(obj)")
        protected Object doRead(Object obj, long offset, ForeignToLLVMType type,
                        @Cached("createReadNode(obj)") LLVMObjectReadNode impl) {
            return impl.executeRead(obj, offset, type);
        }

        protected LLVMObjectReadNode createReadNode(Object obj) {
            if (obj instanceof LLVMObjectAccess) {
                return ((LLVMObjectAccess) obj).createReadNode();
            } else if (obj instanceof DynamicObject) {
                DynamicObject dynamicObject = (DynamicObject) obj;
                ObjectType objectType = dynamicObject.getShape().getObjectType();
                if (objectType instanceof LLVMObjectAccess) {
                    return ((LLVMObjectAccess) objectType).createReadNode();
                }
            }

            return new FallbackReadNode();
        }
    }

    static class FallbackReadNode extends LLVMNode implements LLVMObjectReadNode {

        @Child private InteropLibrary interop = InteropLibrary.getFactory().createDispatched(5);
        @Child private ToLLVM toLLVM = ToLLVMNodeGen.create();
        @Child private UseLLVMObjectAccessNode useLLVMObjectAccess = UseLLVMObjectAccessNodeGen.create();

        @Override
        public boolean canAccess(Object obj) {
            return !useLLVMObjectAccess.executeWithTarget(obj) && interop.accepts(obj);
        }

        @Override
        public Object executeRead(Object obj, long offset, ForeignToLLVMType type) {
            try {
                Object foreign = interop.readArrayElement(obj, offset / type.getSizeInBytes());
                return toLLVM.executeWithType(foreign, null, type);
            } catch (InteropException e) {
                CompilerDirectives.transferToInterpreter();
                throw new LLVMPolyglotException(this, "Error reading from foreign array.");
            }
        }
    }

    abstract static class UseLLVMObjectAccessNode extends LLVMNode {
        public abstract boolean executeWithTarget(Object obj);

        @Specialization(guards = {"obj.getClass() == cachedClass", "!isDynamicObject(cachedClass)"})
        protected boolean doNonDynamicObjectCached(@SuppressWarnings("unused") Object obj,
                        @Cached("obj.getClass()") Class<?> cachedClass) {
            return LLVMObjectAccess.class.isAssignableFrom(cachedClass);
        }

        @Specialization(guards = {"cachedObjectTypeClass == obj.getShape().getObjectType().getClass()"})
        protected boolean doDynamicObjectCached(@SuppressWarnings("unused") DynamicObject obj,
                        @Cached("obj.getShape().getObjectType().getClass()") Class<?> cachedObjectTypeClass) {
            return LLVMObjectAccess.class.isAssignableFrom(cachedObjectTypeClass);
        }

        @Specialization(replaces = {"doNonDynamicObjectCached", "doDynamicObjectCached"})
        protected boolean uncached(Object obj) {
            return obj instanceof LLVMObjectAccess || obj instanceof DynamicObject && ((DynamicObject) obj).getShape().getObjectType() instanceof LLVMObjectAccess;
        }

        protected boolean isDynamicObject(Class<?> clazz) {
            return DynamicObject.class.isAssignableFrom(clazz);
        }
    }

    abstract static class CachedWriteNode extends LLVMNode implements LLVMObjectWriteNode {

        static final int TYPE_LIMIT = 8;

        @Override
        public boolean canAccess(Object obj) {
            return obj instanceof TruffleObject;
        }

        @Specialization(limit = "TYPE_LIMIT", guards = "impl.canAccess(obj)")
        protected void doWrite(Object obj, long offset, Object value, ForeignToLLVMType type,
                        @Cached("createWriteNode(obj)") LLVMObjectWriteNode impl) {
            impl.executeWrite(obj, offset, value, type);
        }

        LLVMObjectWriteNode createWriteNode(Object obj) {
            if (obj instanceof LLVMObjectAccess) {
                return ((LLVMObjectAccess) obj).createWriteNode();
            } else if (obj instanceof DynamicObject) {
                DynamicObject dynamicObject = (DynamicObject) obj;
                ObjectType objectType = dynamicObject.getShape().getObjectType();
                if (objectType instanceof LLVMObjectAccess) {
                    return ((LLVMObjectAccess) objectType).createWriteNode();
                }
            }

            return FallbackWriteNodeGen.create();
        }
    }

    abstract static class FallbackWriteNode extends LLVMNode implements LLVMObjectWriteNode {

        @Child private InteropLibrary interop = InteropLibrary.getFactory().createDispatched(5);
        @Child private GetWriteIdentifierNode getWriteIdentifier = GetWriteIdentifierNodeGen.create();
        @Child private UseLLVMObjectAccessNode useLLVMObjectAccess = UseLLVMObjectAccessNodeGen.create();

        @Override
        public boolean canAccess(Object obj) {
            return !useLLVMObjectAccess.executeWithTarget(obj) && interop.accepts(obj);
        }

        @Specialization(limit = "3", guards = "type == cachedType")
        @SuppressWarnings("unused")
        void doCachedType(Object obj, long offset, Object value, ForeignToLLVMType type,
                        @Cached("type") ForeignToLLVMType cachedType,
                        @Cached(parameters = "cachedType") LLVMDataEscapeNode dataEscape) {
            doWrite(obj, offset, value, dataEscape);
        }

        @Specialization(replaces = "doCachedType")
        @TruffleBoundary
        void doUncached(Object obj, long offset, Object value, ForeignToLLVMType type) {
            doWrite(obj, offset, value, LLVMDataEscapeNode.getUncached(type));
        }

        private void doWrite(Object obj, long offset, Object value, LLVMDataEscapeNode dataEscape) {
            long identifier = getWriteIdentifier.execute(offset, value);
            Object escaped = dataEscape.executeWithTarget(value);
            try {
                interop.writeArrayElement(obj, identifier, escaped);
            } catch (InteropException e) {
                CompilerDirectives.transferToInterpreter();
                throw new LLVMPolyglotException(this, "Error writing to foreign array.");
            }
        }
    }

    abstract static class GetWriteIdentifierNode extends LLVMNode {

        abstract long execute(long offset, Object value);

        @Specialization
        long doByte(long offset, @SuppressWarnings("unused") byte value) {
            return offset;
        }

        @Specialization
        long doShort(long offset, @SuppressWarnings("unused") short value) {
            return offset / 2;
        }

        @Specialization
        long doChar(long offset, @SuppressWarnings("unused") char value) {
            return offset / 2;
        }

        @Specialization
        long doInt(long offset, @SuppressWarnings("unused") int value) {
            return offset / 4;
        }

        @Specialization
        long doFloat(long offset, @SuppressWarnings("unused") float value) {
            return offset / 4;
        }

        @Fallback // long, double or non-primitive
        long doDouble(long offset, @SuppressWarnings("unused") Object value) {
            return offset / 8;
        }
    }
}
