/*
 * Copyright (c) 2017, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.nodes.memory;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.TruffleLanguage.ContextReference;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.object.ObjectType;
import com.oracle.truffle.api.object.Shape;
import com.oracle.truffle.llvm.nodes.memory.LLVMObjectAccessFactoryFactory.CachedReadNodeGen;
import com.oracle.truffle.llvm.nodes.memory.LLVMObjectAccessFactoryFactory.CachedWriteNodeGen;
import com.oracle.truffle.llvm.nodes.memory.LLVMObjectAccessFactoryFactory.DynamicObjectReadNodeGen;
import com.oracle.truffle.llvm.nodes.memory.LLVMObjectAccessFactoryFactory.DynamicObjectWriteNodeGen;
import com.oracle.truffle.llvm.runtime.LLVMContext;
import com.oracle.truffle.llvm.runtime.LLVMLanguage;
import com.oracle.truffle.llvm.runtime.interop.LLVMDataEscapeNode;
import com.oracle.truffle.llvm.runtime.interop.LLVMDataEscapeNodeGen;
import com.oracle.truffle.llvm.runtime.interop.convert.ForeignToLLVM;
import com.oracle.truffle.llvm.runtime.interop.convert.ForeignToLLVM.ForeignToLLVMType;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMObjectAccess;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMObjectAccess.LLVMObjectReadNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMObjectAccess.LLVMObjectWriteNode;
import com.oracle.truffle.llvm.runtime.types.Type;

public abstract class LLVMObjectAccessFactory {

    public static LLVMObjectReadNode createRead(ForeignToLLVMType type) {
        return CachedReadNodeGen.create(type);
    }

    public static LLVMObjectWriteNode createWrite(Type type) {
        return CachedWriteNodeGen.create(type);
    }

    abstract static class CachedReadNode extends LLVMObjectReadNode {

        static final int TYPE_LIMIT = 8;

        private final ForeignToLLVMType type;

        CachedReadNode(ForeignToLLVMType type) {
            this.type = type;
        }

        @Override
        public boolean canAccess(Object obj) {
            return obj instanceof LLVMObjectAccess || obj instanceof TruffleObject;
        }

        @Specialization(limit = "TYPE_LIMIT", guards = "impl.canAccess(obj)")
        Object doRead(VirtualFrame frame, Object obj, Object identifier, long offset,
                        @Cached("createReadNode(obj)") LLVMObjectReadNode impl) {
            try {
                return impl.executeRead(frame, obj, identifier, offset);
            } catch (InteropException ex) {
                CompilerDirectives.transferToInterpreter();
                throw ex.raise();
            }
        }

        LLVMObjectReadNode createReadNode(Object obj) {
            if (obj instanceof LLVMObjectAccess) {
                return ((LLVMObjectAccess) obj).createReadNode(type);
            } else if (obj instanceof DynamicObject) {
                return DynamicObjectReadNodeGen.create(type);
            } else {
                return new FallbackReadNode(type, false);
            }
        }
    }

    abstract static class DynamicObjectReadNode extends LLVMObjectReadNode {

        static final int TYPE_LIMIT = 8;

        private final ForeignToLLVMType type;

        DynamicObjectReadNode(ForeignToLLVMType type) {
            this.type = type;
        }

        @Override
        public boolean canAccess(Object obj) {
            return obj instanceof DynamicObject;
        }

        @Specialization(guards = "object.getShape() == cachedShape")
        Object doCachedShape(VirtualFrame frame, DynamicObject object, Object identifier, long offset,
                        @Cached("object.getShape()") @SuppressWarnings("unused") Shape cachedShape,
                        @Cached("createReadNode(cachedShape)") LLVMObjectReadNode impl) {
            return doRead(frame, object, identifier, offset, impl);
        }

        @Specialization(limit = "TYPE_LIMIT", replaces = "doCachedShape", guards = "impl.canAccess(object)")
        Object doRead(VirtualFrame frame, DynamicObject object, Object identifier, long offset,
                        @Cached("createReadNode(object.getShape())") LLVMObjectReadNode impl) {
            try {
                return impl.executeRead(frame, object, identifier, offset);
            } catch (InteropException ex) {
                CompilerDirectives.transferToInterpreter();
                throw ex.raise();
            }
        }

        LLVMObjectReadNode createReadNode(Shape shape) {
            ObjectType objectType = shape.getObjectType();
            if (objectType instanceof LLVMObjectAccess) {
                return ((LLVMObjectAccess) objectType).createReadNode(type);
            } else {
                return new FallbackReadNode(type, true);
            }
        }
    }

    static class FallbackReadNode extends LLVMObjectReadNode {

        @Child Node read = Message.READ.createNode();
        @Child ForeignToLLVM toLLVM;

        private final boolean acceptDynamicObject;

        FallbackReadNode(ForeignToLLVMType type, boolean acceptDynamicObject) {
            this.toLLVM = ForeignToLLVM.create(type);
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
        public Object executeRead(VirtualFrame frame, Object obj, Object identifier, long offset) throws InteropException {
            Object foreign = ForeignAccess.sendRead(read, (TruffleObject) obj, identifier);
            return toLLVM.executeWithTarget(foreign);
        }
    }

    abstract static class CachedWriteNode extends LLVMObjectWriteNode {

        static final int TYPE_LIMIT = 8;

        private final Type type;

        CachedWriteNode(Type type) {
            this.type = type;
        }

        @Override
        public boolean canAccess(Object obj) {
            return obj instanceof LLVMObjectAccess || obj instanceof TruffleObject;
        }

        @Specialization(limit = "TYPE_LIMIT", guards = "impl.canAccess(obj)")
        void doWrite(VirtualFrame frame, Object obj, Object identifier, long offset, Object value,
                        @Cached("createWriteNode(obj)") LLVMObjectWriteNode impl) {
            try {
                impl.executeWrite(frame, obj, identifier, offset, value);
            } catch (InteropException ex) {
                CompilerDirectives.transferToInterpreter();
                throw ex.raise();
            }
        }

        LLVMObjectWriteNode createWriteNode(Object obj) {
            if (obj instanceof LLVMObjectAccess) {
                return ((LLVMObjectAccess) obj).createWriteNode();
            } else if (obj instanceof DynamicObject) {
                return DynamicObjectWriteNodeGen.create(type);
            } else {
                return new FallbackWriteNode(type, getRootNode().getLanguage(LLVMLanguage.class).getContextReference(), false);
            }
        }
    }

    abstract static class DynamicObjectWriteNode extends LLVMObjectWriteNode {

        static final int TYPE_LIMIT = 8;

        private final Type type;

        DynamicObjectWriteNode(Type type) {
            this.type = type;
        }

        @Override
        public boolean canAccess(Object obj) {
            return obj instanceof DynamicObject;
        }

        @Specialization(guards = "object.getShape() == cachedShape")
        void doCachedShape(VirtualFrame frame, DynamicObject object, Object identifier, long offset, Object value,
                        @Cached("object.getShape()") @SuppressWarnings("unused") Shape cachedShape,
                        @Cached("createWriteNode(cachedShape)") LLVMObjectWriteNode impl) {
            doWrite(frame, object, identifier, offset, value, impl);
        }

        @Specialization(limit = "TYPE_LIMIT", replaces = "doCachedShape", guards = "impl.canAccess(object)")
        void doWrite(VirtualFrame frame, DynamicObject object, Object identifier, long offset, Object value,
                        @Cached("createWriteNode(object.getShape())") LLVMObjectWriteNode impl) {
            try {
                impl.executeWrite(frame, object, identifier, offset, value);
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
                return new FallbackWriteNode(type, getRootNode().getLanguage(LLVMLanguage.class).getContextReference(), true);
            }
        }
    }

    static class FallbackWriteNode extends LLVMObjectWriteNode {

        @Child Node write = Message.WRITE.createNode();
        @Child LLVMDataEscapeNode dataEscape;

        private final ContextReference<LLVMContext> ctxRef;
        private final boolean acceptDynamicObject;

        FallbackWriteNode(Type type, ContextReference<LLVMContext> ctxRef, boolean acceptDynamicObject) {
            this.dataEscape = LLVMDataEscapeNodeGen.create(type);
            this.ctxRef = ctxRef;
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
        public void executeWrite(VirtualFrame frame, Object obj, Object identifier, long offset, Object value) throws InteropException {
            Object escaped = dataEscape.executeWithTarget(value, ctxRef.get());
            ForeignAccess.sendWrite(write, (TruffleObject) obj, identifier, escaped);
        }

    }

}
