/*
 * Copyright (c) 2025, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.runtime.nodes.memory;

import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.nodes.memory.load.LLVMDoubleLoadNode.LLVMDoubleOffsetLoadNode;
import com.oracle.truffle.llvm.runtime.nodes.memory.load.LLVMFloatLoadNode.LLVMFloatOffsetLoadNode;
import com.oracle.truffle.llvm.runtime.nodes.memory.load.LLVMI16LoadNode.LLVMI16OffsetLoadNode;
import com.oracle.truffle.llvm.runtime.nodes.memory.load.LLVMI32LoadNode.LLVMI32OffsetLoadNode;
import com.oracle.truffle.llvm.runtime.nodes.memory.load.LLVMI64LoadNode.LLVMI64OffsetLoadNode;
import com.oracle.truffle.llvm.runtime.nodes.memory.load.LLVMI8LoadNode.LLVMI8OffsetLoadNode;
import com.oracle.truffle.llvm.runtime.pointer.LLVMManagedPointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMNativePointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMPointer;

@NodeChild(value = "base", type = LLVMExpressionNode.class)
@NodeChild(value = "offset", type = LLVMExpressionNode.class)
public abstract class LLVMWithElementPtrLoadNode extends LLVMExpressionNode {

    final long typeWidth;

    protected LLVMWithElementPtrLoadNode(long typeWidth) {
        this.typeWidth = typeWidth;
    }

    public final long getTypeWidth() {
        return typeWidth;
    }

    @ImportStatic(LLVMWithElementPtrStoreNode.class)
    public abstract static class LLVMWithElementPtrLoadI8Node extends LLVMWithElementPtrLoadNode {
        @Child private LLVMI8OffsetLoadNode load = LLVMI8OffsetLoadNode.create();

        protected LLVMWithElementPtrLoadI8Node(long typeWidth) {
            super(typeWidth);
        }

        @Specialization(guards = "isNegated(addr.getObject(), element.getObject())")
        protected byte doPointerDiff(LLVMManagedPointer addr, LLVMManagedPointer element) {
            return load.executeWithTarget(LLVMNativePointer.create(addr.getOffset() + element.getOffset()), 0);
        }

        @Specialization(guards = "isNegated(element.getObject(), addr.getObject())")
        protected byte doPointerDiffRev(LLVMManagedPointer addr, LLVMManagedPointer element) {
            return load.executeWithTarget(LLVMNativePointer.create(element.getOffset() + addr.getOffset()), 0);
        }

        @Specialization
        protected byte doInt(LLVMPointer addr, int element) {
            return load.executeWithTarget(addr, typeWidth * element);
        }

        @Specialization
        protected byte doLong(LLVMPointer addr, long element) {
            return load.executeWithTarget(addr, typeWidth * element);
        }

        @Specialization
        protected byte doNativePointer(LLVMPointer addr, LLVMNativePointer element) {
            return load.executeWithTarget(addr, typeWidth * element.asNative());
        }
    }

    @ImportStatic(LLVMWithElementPtrStoreNode.class)
    public abstract static class LLVMWithElementPtrLoadI16Node extends LLVMWithElementPtrLoadNode {
        @Child private LLVMI16OffsetLoadNode load = LLVMI16OffsetLoadNode.create();

        protected LLVMWithElementPtrLoadI16Node(long typeWidth) {
            super(typeWidth);
        }

        @Specialization(guards = "isNegated(addr.getObject(), element.getObject())")
        protected short doPointerDiff(LLVMManagedPointer addr, LLVMManagedPointer element) {
            return load.executeWithTarget(LLVMNativePointer.create(addr.getOffset() + element.getOffset()), 0);
        }

        @Specialization(guards = "isNegated(element.getObject(), addr.getObject())")
        protected short doPointerDiffRev(LLVMManagedPointer addr, LLVMManagedPointer element) {
            return load.executeWithTarget(LLVMNativePointer.create(element.getOffset() + addr.getOffset()), 0);
        }

        @Specialization
        protected short doInt(LLVMPointer addr, int element) {
            return load.executeWithTarget(addr, typeWidth * element);
        }

        @Specialization
        protected short doLong(LLVMPointer addr, long element) {
            return load.executeWithTarget(addr, typeWidth * element);
        }

        @Specialization
        protected short doNativePointer(LLVMPointer addr, LLVMNativePointer element) {
            return load.executeWithTarget(addr, typeWidth * element.asNative());
        }
    }

    @ImportStatic(LLVMWithElementPtrStoreNode.class)
    public abstract static class LLVMWithElementPtrLoadI32Node extends LLVMWithElementPtrLoadNode {
        @Child private LLVMI32OffsetLoadNode load = LLVMI32OffsetLoadNode.create();

        protected LLVMWithElementPtrLoadI32Node(long typeWidth) {
            super(typeWidth);
        }

        @Specialization(guards = "isNegated(addr.getObject(), element.getObject())")
        protected int doPointerDiff(LLVMManagedPointer addr, LLVMManagedPointer element) {
            return load.executeWithTarget(LLVMNativePointer.create(addr.getOffset() + element.getOffset()), 0);
        }

        @Specialization(guards = "isNegated(element.getObject(), addr.getObject())")
        protected int doPointerDiffRev(LLVMManagedPointer addr, LLVMManagedPointer element) {
            return load.executeWithTarget(LLVMNativePointer.create(element.getOffset() + addr.getOffset()), 0);
        }

        @Specialization
        protected int doInt(LLVMPointer addr, int element) {
            return load.executeWithTarget(addr, typeWidth * element);
        }

        @Specialization
        protected int doLong(LLVMPointer addr, long element) {
            return load.executeWithTarget(addr, typeWidth * element);
        }

        @Specialization
        protected int doNativePointer(LLVMPointer addr, LLVMNativePointer element) {
            return load.executeWithTarget(addr, typeWidth * element.asNative());
        }
    }

    @ImportStatic(LLVMWithElementPtrStoreNode.class)
    public abstract static class LLVMWithElementPtrLoadI64Node extends LLVMWithElementPtrLoadNode {
        @Child private LLVMI64OffsetLoadNode load = LLVMI64OffsetLoadNode.create();

        protected LLVMWithElementPtrLoadI64Node(long typeWidth) {
            super(typeWidth);
        }

        @Specialization(guards = "isNegated(addr.getObject(), element.getObject())", rewriteOn = UnexpectedResultException.class)
        protected long doPointerDiff(LLVMManagedPointer addr, LLVMManagedPointer element) throws UnexpectedResultException {
            return load.executeWithTarget(LLVMNativePointer.create(addr.getOffset() + element.getOffset()), 0);
        }

        @Specialization(guards = "isNegated(element.getObject(), addr.getObject())", rewriteOn = UnexpectedResultException.class)
        protected long doPointerDiffRev(LLVMManagedPointer addr, LLVMManagedPointer element) throws UnexpectedResultException {
            return load.executeWithTarget(LLVMNativePointer.create(element.getOffset() + addr.getOffset()), 0);
        }

        @Specialization(rewriteOn = UnexpectedResultException.class)
        protected long doInt(LLVMPointer addr, int element) throws UnexpectedResultException {
            return load.executeWithTarget(addr, typeWidth * element);
        }

        @Specialization(rewriteOn = UnexpectedResultException.class)
        protected long doLong(LLVMPointer addr, long element) throws UnexpectedResultException {
            return load.executeWithTarget(addr, typeWidth * element);
        }

        @Specialization(rewriteOn = UnexpectedResultException.class)
        protected long doNativePointer(LLVMPointer addr, LLVMNativePointer element) throws UnexpectedResultException {
            return load.executeWithTarget(addr, typeWidth * element.asNative());
        }

        @Specialization(guards = "isNegated(addr.getObject(), element.getObject())", replaces = "doPointerDiff")
        protected Object doPointerDiffGeneric(LLVMManagedPointer addr, LLVMManagedPointer element) {
            return load.executeWithTargetGeneric(LLVMNativePointer.create(addr.getOffset() + element.getOffset()), 0);
        }

        @Specialization(guards = "isNegated(element.getObject(), addr.getObject())", replaces = "doPointerDiffRev")
        protected Object doPointerDiffRevGeneric(LLVMManagedPointer addr, LLVMManagedPointer element) {
            return load.executeWithTargetGeneric(LLVMNativePointer.create(element.getOffset() + addr.getOffset()), 0);
        }

        @Specialization(replaces = "doInt")
        protected Object doIntGeneric(LLVMPointer addr, int element) {
            return load.executeWithTargetGeneric(addr, typeWidth * element);
        }

        @Specialization(replaces = "doLong")
        protected Object doLongGeneric(LLVMPointer addr, long element) {
            return load.executeWithTargetGeneric(addr, typeWidth * element);
        }

        @Specialization(replaces = "doNativePointer")
        protected Object doNativePointerGeneric(LLVMPointer addr, LLVMNativePointer element) {
            return load.executeWithTargetGeneric(addr, typeWidth * element.asNative());
        }
    }

    @ImportStatic(LLVMWithElementPtrStoreNode.class)
    public abstract static class LLVMWithElementPtrLoadDoubleNode extends LLVMWithElementPtrLoadNode {
        @Child private LLVMDoubleOffsetLoadNode load = LLVMDoubleOffsetLoadNode.create();

        protected LLVMWithElementPtrLoadDoubleNode(long typeWidth) {
            super(typeWidth);
        }

        @Specialization(guards = "isNegated(addr.getObject(), element.getObject())")
        protected double doPointerDiff(LLVMManagedPointer addr, LLVMManagedPointer element) {
            return load.executeWithTarget(LLVMNativePointer.create(addr.getOffset() + element.getOffset()), 0);
        }

        @Specialization(guards = "isNegated(element.getObject(), addr.getObject())")
        protected double doPointerDiffRev(LLVMManagedPointer addr, LLVMManagedPointer element) {
            return load.executeWithTarget(LLVMNativePointer.create(element.getOffset() + addr.getOffset()), 0);
        }

        @Specialization
        protected double doInt(LLVMPointer addr, int element) {
            return load.executeWithTarget(addr, typeWidth * element);
        }

        @Specialization
        protected double doLong(LLVMPointer addr, long element) {
            return load.executeWithTarget(addr, typeWidth * element);
        }

        @Specialization
        protected double doNativePointer(LLVMPointer addr, LLVMNativePointer element) {
            return load.executeWithTarget(addr, typeWidth * element.asNative());
        }
    }

    @ImportStatic(LLVMWithElementPtrStoreNode.class)
    public abstract static class LLVMWithElementPtrLoadFloatNode extends LLVMWithElementPtrLoadNode {
        @Child private LLVMFloatOffsetLoadNode load = LLVMFloatOffsetLoadNode.create();

        protected LLVMWithElementPtrLoadFloatNode(long typeWidth) {
            super(typeWidth);
        }

        @Specialization(guards = "isNegated(addr.getObject(), element.getObject())")
        protected float doPointerDiff(LLVMManagedPointer addr, LLVMManagedPointer element) {
            return load.executeWithTarget(LLVMNativePointer.create(addr.getOffset() + element.getOffset()), 0);
        }

        @Specialization(guards = "isNegated(element.getObject(), addr.getObject())")
        protected float doPointerDiffRev(LLVMManagedPointer addr, LLVMManagedPointer element) {
            return load.executeWithTarget(LLVMNativePointer.create(element.getOffset() + addr.getOffset()), 0);
        }

        @Specialization
        protected float doInt(LLVMPointer addr, int element) {
            return load.executeWithTarget(addr, typeWidth * element);
        }

        @Specialization
        protected float doLong(LLVMPointer addr, long element) {
            return load.executeWithTarget(addr, typeWidth * element);
        }

        @Specialization
        protected float doNativePointer(LLVMPointer addr, LLVMNativePointer element) {
            return load.executeWithTarget(addr, typeWidth * element.asNative());
        }
    }
}
