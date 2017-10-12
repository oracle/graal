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
import com.oracle.truffle.llvm.nodes.memory.LLVMObjectNativeFactoryFactory.CachedAsPointerNodeGen;
import com.oracle.truffle.llvm.nodes.memory.LLVMObjectNativeFactoryFactory.CachedIsPointerNodeGen;
import com.oracle.truffle.llvm.nodes.memory.LLVMObjectNativeFactoryFactory.CachedToNativeNodeGen;
import com.oracle.truffle.llvm.nodes.memory.LLVMObjectNativeFactoryFactory.DynamicObjectAsPointerNodeGen;
import com.oracle.truffle.llvm.nodes.memory.LLVMObjectNativeFactoryFactory.DynamicObjectIsPointerNodeGen;
import com.oracle.truffle.llvm.nodes.memory.LLVMObjectNativeFactoryFactory.DynamicObjectToNativeNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMObjectNative;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMObjectNative.LLVMObjectAsPointerNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMObjectNative.LLVMObjectIsPointerNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMObjectNative.LLVMObjectToNativeNode;

public abstract class LLVMObjectNativeFactory {

    public static LLVMObjectToNativeNode createToNative() {
        return CachedToNativeNodeGen.create();
    }

    public static LLVMObjectIsPointerNode createIsPointer() {
        return CachedIsPointerNodeGen.create();
    }

    public static LLVMObjectAsPointerNode createAsPointer() {
        return CachedAsPointerNodeGen.create();
    }

    abstract static class CachedToNativeNode extends LLVMObjectToNativeNode {

        static final int TYPE_LIMIT = 8;

        CachedToNativeNode() {
        }

        @Override
        public boolean isNative(Object obj) {
            return obj instanceof LLVMObjectNative || obj instanceof TruffleObject;
        }

        @Specialization(limit = "TYPE_LIMIT", guards = "impl.isNative(obj)")
        Object toNative(VirtualFrame frame, Object obj,
                        @Cached("createToNativeNode(obj)") LLVMObjectToNativeNode impl) {
            try {
                return impl.executeToNative(frame, obj);
            } catch (InteropException ex) {
                CompilerDirectives.transferToInterpreter();
                throw ex.raise();
            }
        }

        LLVMObjectToNativeNode createToNativeNode(Object obj) {
            if (obj instanceof LLVMObjectNative) {
                return ((LLVMObjectNative) obj).createToNativeNode();
            } else if (obj instanceof DynamicObject) {
                return DynamicObjectToNativeNodeGen.create();
            } else {
                return new FallbackToNativeNode(false);
            }
        }
    }

    abstract static class DynamicObjectToNativeNode extends LLVMObjectToNativeNode {

        static final int TYPE_LIMIT = 8;

        DynamicObjectToNativeNode() {
        }

        @Override
        public boolean isNative(Object obj) {
            return obj instanceof DynamicObject;
        }

        @Specialization(guards = "object.getShape() == cachedShape")
        Object doCachedShape(VirtualFrame frame, DynamicObject object,
                        @Cached("object.getShape()") @SuppressWarnings("unused") Shape cachedShape,
                        @Cached("createToNativeNode(cachedShape)") LLVMObjectToNativeNode impl) {
            return doToNative(frame, object, impl);
        }

        @Specialization(limit = "TYPE_LIMIT", replaces = "doCachedShape", guards = "impl.isNative(object)")
        Object doToNative(VirtualFrame frame, DynamicObject object,
                        @Cached("createToNativeNode(object.getShape())") LLVMObjectToNativeNode impl) {
            try {
                return impl.executeToNative(frame, object);
            } catch (InteropException ex) {
                CompilerDirectives.transferToInterpreter();
                throw ex.raise();
            }
        }

        LLVMObjectToNativeNode createToNativeNode(Shape shape) {
            ObjectType objectType = shape.getObjectType();
            if (objectType instanceof LLVMObjectNative) {
                return ((LLVMObjectNative) objectType).createToNativeNode();
            } else {
                return new FallbackToNativeNode(true);
            }
        }
    }

    static class FallbackToNativeNode extends LLVMObjectToNativeNode {

        @Child Node toNative = Message.TO_NATIVE.createNode();

        private final boolean acceptDynamicObject;

        FallbackToNativeNode(boolean acceptDynamicObject) {
            this.acceptDynamicObject = acceptDynamicObject;
        }

        @Override
        public boolean isNative(Object obj) {
            if (acceptDynamicObject) {
                return !(((DynamicObject) obj).getShape().getObjectType() instanceof LLVMObjectNative);
            } else {
                return obj instanceof TruffleObject && !(obj instanceof LLVMObjectNative) && !(obj instanceof DynamicObject);
            }
        }

        @Override
        public Object executeToNative(VirtualFrame frame, Object obj) throws InteropException {
            return ForeignAccess.sendToNative(toNative, (TruffleObject) obj);
        }
    }

    // isPointer

    abstract static class CachedIsPointerNode extends LLVMObjectIsPointerNode {

        static final int TYPE_LIMIT = 8;

        CachedIsPointerNode() {
        }

        @Override
        public boolean isNative(Object obj) {
            return obj instanceof LLVMObjectNative || obj instanceof TruffleObject;
        }

        @Specialization(limit = "TYPE_LIMIT", guards = "impl.isNative(obj)")
        boolean toNative(VirtualFrame frame, Object obj,
                        @Cached("createIsPointerNode(obj)") LLVMObjectIsPointerNode impl) {
            return impl.executeIsPointer(frame, obj);
        }

        LLVMObjectIsPointerNode createIsPointerNode(Object obj) {
            if (obj instanceof LLVMObjectNative) {
                return ((LLVMObjectNative) obj).createIsPointerNode();
            } else if (obj instanceof DynamicObject) {
                return DynamicObjectIsPointerNodeGen.create();
            } else {
                return new FallbackIsPointerNode(false);
            }
        }
    }

    abstract static class DynamicObjectIsPointerNode extends LLVMObjectIsPointerNode {

        static final int TYPE_LIMIT = 8;

        DynamicObjectIsPointerNode() {
        }

        @Override
        public boolean isNative(Object obj) {
            return obj instanceof DynamicObject;
        }

        @Specialization(guards = "object.getShape() == cachedShape")
        boolean doCachedShape(VirtualFrame frame, DynamicObject object,
                        @Cached("object.getShape()") @SuppressWarnings("unused") Shape cachedShape,
                        @Cached("createIsPointerNode(cachedShape)") LLVMObjectIsPointerNode impl) {
            return doIsPointer(frame, object, impl);
        }

        @Specialization(limit = "TYPE_LIMIT", replaces = "doCachedShape", guards = "impl.isNative(object)")
        boolean doIsPointer(VirtualFrame frame, DynamicObject object,
                        @Cached("createIsPointerNode(object.getShape())") LLVMObjectIsPointerNode impl) {
            return impl.executeIsPointer(frame, object);
        }

        LLVMObjectIsPointerNode createIsPointerNode(Shape shape) {
            ObjectType objectType = shape.getObjectType();
            if (objectType instanceof LLVMObjectNative) {
                return ((LLVMObjectNative) objectType).createIsPointerNode();
            } else {
                return new FallbackIsPointerNode(true);
            }
        }
    }

    static class FallbackIsPointerNode extends LLVMObjectIsPointerNode {

        @Child Node isPoitner = Message.IS_POINTER.createNode();

        private final boolean acceptDynamicObject;

        FallbackIsPointerNode(boolean acceptDynamicObject) {
            this.acceptDynamicObject = acceptDynamicObject;
        }

        @Override
        public boolean isNative(Object obj) {
            if (acceptDynamicObject) {
                return !(((DynamicObject) obj).getShape().getObjectType() instanceof LLVMObjectNative);
            } else {
                return obj instanceof TruffleObject && !(obj instanceof LLVMObjectNative) && !(obj instanceof DynamicObject);
            }
        }

        @Override
        public boolean executeIsPointer(VirtualFrame frame, Object obj) {
            return ForeignAccess.sendIsPointer(isPoitner, (TruffleObject) obj);
        }
    }

    // asPointer

    abstract static class CachedAsPointerNode extends LLVMObjectAsPointerNode {

        static final int TYPE_LIMIT = 8;

        CachedAsPointerNode() {
        }

        @Override
        public boolean isNative(Object obj) {
            return obj instanceof LLVMObjectNative || obj instanceof TruffleObject;
        }

        @Specialization(limit = "TYPE_LIMIT", guards = "impl.isNative(obj)")
        long toNative(VirtualFrame frame, Object obj,
                        @Cached("createAsPointerNode(obj)") LLVMObjectAsPointerNode impl) {
            try {
                return impl.executeAsPointer(frame, obj);
            } catch (InteropException ex) {
                CompilerDirectives.transferToInterpreter();
                throw ex.raise();
            }
        }

        LLVMObjectAsPointerNode createAsPointerNode(Object obj) {
            if (obj instanceof LLVMObjectNative) {
                return ((LLVMObjectNative) obj).createAsPointerNode();
            } else if (obj instanceof DynamicObject) {
                return DynamicObjectAsPointerNodeGen.create();
            } else {
                return new FallbackAsPointerNode(false);
            }
        }
    }

    abstract static class DynamicObjectAsPointerNode extends LLVMObjectAsPointerNode {

        static final int TYPE_LIMIT = 8;

        DynamicObjectAsPointerNode() {
        }

        @Override
        public boolean isNative(Object obj) {
            return obj instanceof DynamicObject;
        }

        @Specialization(guards = "object.getShape() == cachedShape")
        long doCachedShape(VirtualFrame frame, DynamicObject object,
                        @Cached("object.getShape()") @SuppressWarnings("unused") Shape cachedShape,
                        @Cached("createAsPointerNode(cachedShape)") LLVMObjectAsPointerNode impl) {
            return doAsPointer(frame, object, impl);
        }

        @Specialization(limit = "TYPE_LIMIT", replaces = "doCachedShape", guards = "impl.isNative(object)")
        long doAsPointer(VirtualFrame frame, DynamicObject object,
                        @Cached("createAsPointerNode(object.getShape())") LLVMObjectAsPointerNode impl) {
            try {
                return impl.executeAsPointer(frame, object);
            } catch (InteropException ex) {
                CompilerDirectives.transferToInterpreter();
                throw ex.raise();
            }
        }

        LLVMObjectAsPointerNode createAsPointerNode(Shape shape) {
            ObjectType objectType = shape.getObjectType();
            if (objectType instanceof LLVMObjectNative) {
                return ((LLVMObjectNative) objectType).createAsPointerNode();
            } else {
                return new FallbackAsPointerNode(true);
            }
        }
    }

    static class FallbackAsPointerNode extends LLVMObjectAsPointerNode {

        @Child Node asPointer = Message.AS_POINTER.createNode();

        private final boolean acceptDynamicObject;

        FallbackAsPointerNode(boolean acceptDynamicObject) {
            this.acceptDynamicObject = acceptDynamicObject;
        }

        @Override
        public boolean isNative(Object obj) {
            if (acceptDynamicObject) {
                return !(((DynamicObject) obj).getShape().getObjectType() instanceof LLVMObjectNative);
            } else {
                return obj instanceof TruffleObject && !(obj instanceof LLVMObjectNative) && !(obj instanceof DynamicObject);
            }
        }

        @Override
        public long executeAsPointer(VirtualFrame frame, Object obj) throws InteropException {
            return ForeignAccess.sendAsPointer(asPointer, (TruffleObject) obj);
        }
    }
}
