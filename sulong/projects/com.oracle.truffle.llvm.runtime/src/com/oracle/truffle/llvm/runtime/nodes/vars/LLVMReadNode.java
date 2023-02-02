/*
 * Copyright (c) 2016, 2021, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.runtime.nodes.vars;

import java.util.Arrays;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateAOT;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.FrameSlotTypeException;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.NodeCost;
import com.oracle.truffle.llvm.runtime.interop.LLVMTypedForeignObject;
import com.oracle.truffle.llvm.runtime.interop.access.LLVMInteropType;
import com.oracle.truffle.llvm.runtime.library.internal.LLVMAsForeignLibrary;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMNode;
import com.oracle.truffle.llvm.runtime.nodes.vars.LLVMReadNodeFactory.ForeignAttachInteropTypeNodeGen;
import com.oracle.truffle.llvm.runtime.pointer.LLVMManagedPointer;
import com.oracle.truffle.llvm.spi.NativeTypeLibrary;

public abstract class LLVMReadNode extends LLVMExpressionNode {

    @FunctionalInterface
    public interface IndexedFunction<T> {
        T get(int i);
    }

    public static final class IndexedSlotCache<T extends LLVMNode> {
        private static final int MIN_CACHE_SIZE = 32;
        private static final int MAX_CACHE_SIZE = 512;

        private Object[] cache = new Object[MIN_CACHE_SIZE];
        private final IndexedFunction<T> factory;

        protected IndexedSlotCache(IndexedFunction<T> factory) {
            this.factory = factory;
        }

        @SuppressWarnings("unchecked")
        public T get(int slot) {
            /*
             * This can remain completely unsynchronized, since unlikely update races can only ever
             * lead to a bit of memory duplication, which is acceptable.
             */
            Object[] c = cache;
            if (slot >= c.length) {
                if (slot >= MAX_CACHE_SIZE) {
                    return factory.get(slot);
                }
                int newLength = Math.min(MAX_CACHE_SIZE, Math.max(slot + 1, c.length * 2));
                cache = c = Arrays.copyOf(c, newLength);
            }
            T result = (T) c[slot];
            if (result == null) {
                c[slot] = result = factory.get(slot);
                assert result != null;
            }
            return result;
        }
    }

    private abstract static class LLVMReadCachableNode extends LLVMReadNode {

        protected LLVMReadCachableNode(int slot) {
            super(slot);
        }

        @Override
        public final boolean isAdoptable() {
            return false;
        }

        @Override
        public NodeCost getCost() {
            return NodeCost.MONOMORPHIC;
        }
    }

    protected final int slot;

    public LLVMReadNode(int slot) {
        assert slot >= 0;
        this.slot = slot;
    }

    @Override
    public String toString() {
        return getShortString("slot");
    }

    public static final class LLVMI1ReadNode extends LLVMReadCachableNode {
        protected LLVMI1ReadNode(int slot) {
            super(slot);
        }

        @Override
        public boolean executeI1(VirtualFrame frame) {
            return frame.getBoolean(slot);
        }

        @Override
        public Object executeGeneric(VirtualFrame frame) {
            return executeI1(frame);
        }

        private static final IndexedSlotCache<LLVMI1ReadNode> CACHE = new IndexedSlotCache<>(LLVMI1ReadNode::new);

        public static LLVMI1ReadNode create(int slot) {
            return CACHE.get(slot);
        }
    }

    public static final class LLVMI8ReadNode extends LLVMReadCachableNode {
        protected LLVMI8ReadNode(int slot) {
            super(slot);
        }

        @Override
        public byte executeI8(VirtualFrame frame) {
            return frame.getByte(slot);
        }

        @Override
        public Object executeGeneric(VirtualFrame frame) {
            return executeI8(frame);
        }

        private static final IndexedSlotCache<LLVMI8ReadNode> CACHE = new IndexedSlotCache<>(LLVMI8ReadNode::new);

        public static LLVMI8ReadNode create(int slot) {
            return CACHE.get(slot);
        }
    }

    public static final class LLVMI16ReadNode extends LLVMReadCachableNode {
        protected LLVMI16ReadNode(int slot) {
            super(slot);
        }

        @Override
        public short executeI16(VirtualFrame frame) {
            return (short) frame.getInt(slot);
        }

        @Override
        public Object executeGeneric(VirtualFrame frame) {
            return executeI16(frame);
        }

        private static final IndexedSlotCache<LLVMI16ReadNode> CACHE = new IndexedSlotCache<>(LLVMI16ReadNode::new);

        public static LLVMI16ReadNode create(int slot) {
            return CACHE.get(slot);
        }
    }

    public static final class LLVMI32ReadNode extends LLVMReadCachableNode {
        protected LLVMI32ReadNode(int slot) {
            super(slot);
        }

        @Override
        public int executeI32(VirtualFrame frame) {
            return frame.getInt(slot);
        }

        @Override
        public Object executeGeneric(VirtualFrame frame) {
            return executeI32(frame);
        }

        private static final IndexedSlotCache<LLVMI32ReadNode> CACHE = new IndexedSlotCache<>(LLVMI32ReadNode::new);

        public static LLVMI32ReadNode create(int slot) {
            return CACHE.get(slot);
        }
    }

    public abstract static class LLVMI64ReadNode extends LLVMReadNode {
        protected LLVMI64ReadNode(int slot) {
            super(slot);
        }

        @Specialization(rewriteOn = FrameSlotTypeException.class)
        protected long readI64(VirtualFrame frame) throws FrameSlotTypeException {
            return frame.getLong(slot);
        }

        @Specialization(rewriteOn = FrameSlotTypeException.class)
        protected Object readObject(VirtualFrame frame) throws FrameSlotTypeException {
            return frame.getObject(slot);
        }

        @Specialization
        protected Object readGeneric(VirtualFrame frame) {
            if (frame.isLong(slot)) {
                return frame.getLong(slot);
            } else {
                return frame.getObject(slot);
            }
        }
    }

    public static final class LLVMIReadVarBitNode extends LLVMReadCachableNode {
        protected LLVMIReadVarBitNode(int slot) {
            super(slot);
        }

        @Override
        public Object executeGeneric(VirtualFrame frame) {
            return frame.getObject(slot);
        }

        private static final IndexedSlotCache<LLVMIReadVarBitNode> CACHE = new IndexedSlotCache<>(LLVMIReadVarBitNode::new);

        public static LLVMIReadVarBitNode create(int slot) {
            return CACHE.get(slot);
        }
    }

    public static final class LLVMFloatReadNode extends LLVMReadCachableNode {
        protected LLVMFloatReadNode(int slot) {
            super(slot);
        }

        @Override
        public float executeFloat(VirtualFrame frame) {
            return frame.getFloat(slot);
        }

        @Override
        public Object executeGeneric(VirtualFrame frame) {
            return executeFloat(frame);
        }

        private static final IndexedSlotCache<LLVMFloatReadNode> CACHE = new IndexedSlotCache<>(LLVMFloatReadNode::new);

        public static LLVMFloatReadNode create(int slot) {
            return CACHE.get(slot);
        }
    }

    public static final class LLVMDoubleReadNode extends LLVMReadCachableNode {
        protected LLVMDoubleReadNode(int slot) {
            super(slot);
        }

        @Override
        public double executeDouble(VirtualFrame frame) {
            return frame.getDouble(slot);
        }

        @Override
        public Object executeGeneric(VirtualFrame frame) {
            return executeDouble(frame);
        }

        private static final IndexedSlotCache<LLVMDoubleReadNode> CACHE = new IndexedSlotCache<>(LLVMDoubleReadNode::new);

        public static LLVMDoubleReadNode create(int slot) {
            return CACHE.get(slot);
        }
    }

    public static final class LLVM80BitFloatReadNode extends LLVMReadCachableNode {
        protected LLVM80BitFloatReadNode(int slot) {
            super(slot);
        }

        @Override
        public Object executeGeneric(VirtualFrame frame) {
            return frame.getObject(slot);
        }

        private static final IndexedSlotCache<LLVM80BitFloatReadNode> CACHE = new IndexedSlotCache<>(LLVM80BitFloatReadNode::new);

        public static LLVM80BitFloatReadNode create(int slot) {
            return CACHE.get(slot);
        }
    }

    public static final class LLVMObjectReadNode extends LLVMReadCachableNode {
        protected LLVMObjectReadNode(int slot) {
            super(slot);
        }

        @Override
        public Object executeGeneric(VirtualFrame frame) {
            return frame.getObject(slot);
        }

        private static final IndexedSlotCache<LLVMObjectReadNode> CACHE = new IndexedSlotCache<>(LLVMObjectReadNode::new);

        public static LLVMObjectReadNode create(int slot) {
            return CACHE.get(slot);
        }
    }

    public abstract static class AttachInteropTypeNode extends LLVMNode {

        public abstract Object execute(Object object, LLVMInteropType type);

        @Specialization(guards = {"type != null", "pointer.getOffset() == 0"})
        protected Object doForeign(LLVMManagedPointer pointer, LLVMInteropType.Structured type,
                        @Cached("create()") ForeignAttachInteropTypeNode attach) {
            return LLVMManagedPointer.create(attach.execute(pointer.getObject(), type));
        }

        @Fallback
        protected Object doOther(Object object, @SuppressWarnings("unused") LLVMInteropType type) {
            return object;
        }
    }

    public abstract static class ForeignAttachInteropTypeNode extends LLVMNode {

        public abstract Object execute(Object object, LLVMInteropType.Structured type);

        public static ForeignAttachInteropTypeNode create() {
            return ForeignAttachInteropTypeNodeGen.create();
        }

        @Specialization(guards = {"foreigns.isForeign(object)", "!nativeTypes.hasNativeType(object)"})
        @GenerateAOT.Exclude
        protected Object doForeignNoNativeType(Object object, LLVMInteropType.Structured type,
                        @CachedLibrary(limit = "3") LLVMAsForeignLibrary foreigns,
                        @SuppressWarnings("unused") @CachedLibrary(limit = "3") NativeTypeLibrary nativeTypes) {
            return LLVMTypedForeignObject.create(foreigns.asForeign(object), type);
        }

        @Fallback
        protected Object doOther(Object object, @SuppressWarnings("unused") LLVMInteropType.Structured type) {
            return object;
        }
    }

    public static final class LLVMDebugReadNode extends LLVMReadCachableNode {
        protected LLVMDebugReadNode(int slot) {
            super(slot);
        }

        @Override
        public Object executeGeneric(VirtualFrame frame) {
            return frame.getValue(slot);
        }

        private static final IndexedSlotCache<LLVMDebugReadNode> CACHE = new IndexedSlotCache<>(LLVMDebugReadNode::new);

        public static LLVMDebugReadNode create(int slot) {
            return CACHE.get(slot);
        }
    }
}
