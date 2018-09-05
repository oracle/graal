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
package com.oracle.truffle.llvm.runtime.nodes.factories;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.object.ObjectType;
import com.oracle.truffle.api.object.Shape;
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
import com.oracle.truffle.llvm.runtime.nodes.factories.LLVMObjectAccessFactoryFactory.DynamicObjectReadNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.factories.LLVMObjectAccessFactoryFactory.DynamicObjectWriteNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.factories.LLVMObjectAccessFactoryFactory.GetWriteIdentifierNodeGen;

public abstract class LLVMObjectAccessFactory {

    public static LLVMObjectReadNode createRead() {
        return CachedReadNodeGen.create();
    }

    public static LLVMObjectWriteNode createWrite() {
        return CachedWriteNodeGen.create();
    }

    abstract static class CachedReadNode extends LLVMObjectReadNode {

        static final int TYPE_LIMIT = 8;

        @Override
        public boolean canAccess(Object obj) {
            return obj instanceof LLVMObjectAccess || obj instanceof TruffleObject;
        }

        @Specialization(limit = "TYPE_LIMIT", guards = "impl.canAccess(obj)")
        protected Object doRead(Object obj, long offset, ForeignToLLVMType type,
                        @Cached("createReadNode(obj)") LLVMObjectReadNode impl) {
            try {
                return impl.executeRead(obj, offset, type);
            } catch (InteropException ex) {
                CompilerDirectives.transferToInterpreter();
                throw ex.raise();
            }
        }

        protected LLVMObjectReadNode createReadNode(Object obj) {
            if (obj instanceof LLVMObjectAccess) {
                return ((LLVMObjectAccess) obj).createReadNode();
            } else if (obj instanceof DynamicObject) {
                return DynamicObjectReadNodeGen.create();
            } else {
                return new FallbackReadNode(false);
            }
        }
    }

    abstract static class DynamicObjectReadNode extends LLVMObjectReadNode {

        static final int TYPE_LIMIT = 8;

        @Override
        public boolean canAccess(Object obj) {
            return obj instanceof DynamicObject;
        }

        @Specialization(guards = "object.getShape() == cachedShape")
        protected Object doCachedShape(DynamicObject object, long offset, ForeignToLLVMType type,
                        @Cached("object.getShape()") @SuppressWarnings("unused") Shape cachedShape,
                        @Cached("createReadNode(cachedShape)") LLVMObjectReadNode impl) {
            return doRead(object, offset, type, impl);
        }

        @Specialization(limit = "TYPE_LIMIT", replaces = "doCachedShape", guards = "impl.canAccess(object)")
        protected Object doRead(DynamicObject object, long offset, ForeignToLLVMType type,
                        @Cached("createReadNode(object.getShape())") LLVMObjectReadNode impl) {
            try {
                return impl.executeRead(object, offset, type);
            } catch (InteropException ex) {
                CompilerDirectives.transferToInterpreter();
                throw ex.raise();
            }
        }

        LLVMObjectReadNode createReadNode(Shape shape) {
            ObjectType objectType = shape.getObjectType();
            if (objectType instanceof LLVMObjectAccess) {
                return ((LLVMObjectAccess) objectType).createReadNode();
            } else {
                return new FallbackReadNode(true);
            }
        }
    }

    static class FallbackReadNode extends LLVMObjectReadNode {

        @Child private Node read = Message.READ.createNode();
        @Child private ToLLVM toLLVM = ToLLVMNodeGen.create();

        private final boolean acceptDynamicObject;

        FallbackReadNode(boolean acceptDynamicObject) {
            this.acceptDynamicObject = acceptDynamicObject;
        }

        @Override
        public boolean canAccess(Object obj) {
            if (acceptDynamicObject) {
                return !(((DynamicObject) obj).getShape().getObjectType() instanceof LLVMObjectAccess);
            } else {
                return obj instanceof TruffleObject && !(obj instanceof LLVMObjectAccess) && !(obj instanceof DynamicObject);
            }
        }

        @Override
        public Object executeRead(Object obj, long offset, ForeignToLLVMType type) throws InteropException {
            Object foreign = ForeignAccess.sendRead(read, (TruffleObject) obj, offset / type.getSizeInBytes());
            return toLLVM.executeWithType(foreign, null, type);
        }
    }

    abstract static class CachedWriteNode extends LLVMObjectWriteNode {

        static final int TYPE_LIMIT = 8;

        @Override
        public boolean canAccess(Object obj) {
            return obj instanceof LLVMObjectAccess || obj instanceof TruffleObject;
        }

        @Specialization(limit = "TYPE_LIMIT", guards = "impl.canAccess(obj)")
        protected void doWrite(Object obj, long offset, Object value, ForeignToLLVMType type,
                        @Cached("createWriteNode(obj)") LLVMObjectWriteNode impl) {
            try {
                impl.executeWrite(obj, offset, value, type);
            } catch (InteropException ex) {
                CompilerDirectives.transferToInterpreter();
                throw ex.raise();
            }
        }

        LLVMObjectWriteNode createWriteNode(Object obj) {
            if (obj instanceof LLVMObjectAccess) {
                return ((LLVMObjectAccess) obj).createWriteNode();
            } else if (obj instanceof DynamicObject) {
                return DynamicObjectWriteNodeGen.create();
            } else {
                return new FallbackWriteNode(false);
            }
        }
    }

    abstract static class DynamicObjectWriteNode extends LLVMObjectWriteNode {

        static final int TYPE_LIMIT = 8;

        @Override
        public boolean canAccess(Object obj) {
            return obj instanceof DynamicObject;
        }

        @Specialization(guards = "object.getShape() == cachedShape")
        protected void doCachedShape(DynamicObject object, long offset, Object value, ForeignToLLVMType type,
                        @Cached("object.getShape()") @SuppressWarnings("unused") Shape cachedShape,
                        @Cached("createWriteNode(cachedShape)") LLVMObjectWriteNode impl) {
            doWrite(object, offset, value, type, impl);
        }

        @Specialization(limit = "TYPE_LIMIT", replaces = "doCachedShape", guards = "impl.canAccess(object)")
        protected void doWrite(DynamicObject object, long offset, Object value, ForeignToLLVMType type,
                        @Cached("createWriteNode(object.getShape())") LLVMObjectWriteNode impl) {
            try {
                impl.executeWrite(object, offset, value, type);
            } catch (InteropException ex) {
                CompilerDirectives.transferToInterpreter();
                throw ex.raise();
            }
        }

        LLVMObjectWriteNode createWriteNode(Shape shape) {
            ObjectType objectType = shape.getObjectType();
            if (objectType instanceof LLVMObjectAccess) {
                return ((LLVMObjectAccess) objectType).createWriteNode();
            } else {
                return new FallbackWriteNode(true);
            }
        }
    }

    static class FallbackWriteNode extends LLVMObjectWriteNode {

        @Child private Node write = Message.WRITE.createNode();
        @Child private GetWriteIdentifierNode getWriteIdentifier = GetWriteIdentifierNodeGen.create();
        @Child private LLVMDataEscapeNode dataEscape = LLVMDataEscapeNode.create();

        private final boolean acceptDynamicObject;

        FallbackWriteNode(boolean acceptDynamicObject) {
            this.acceptDynamicObject = acceptDynamicObject;
        }

        @Override
        public boolean canAccess(Object obj) {
            if (acceptDynamicObject) {
                return !(((DynamicObject) obj).getShape().getObjectType() instanceof LLVMObjectAccess);
            } else {
                return obj instanceof TruffleObject && !(obj instanceof LLVMObjectAccess) && !(obj instanceof DynamicObject);
            }
        }

        @Override
        public void executeWrite(Object obj, long offset, Object value, ForeignToLLVMType type) throws InteropException {
            long identifier = getWriteIdentifier.execute(offset, value);
            Object escaped = dataEscape.executeWithTarget(value);
            ForeignAccess.sendWrite(write, (TruffleObject) obj, identifier, escaped);
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
