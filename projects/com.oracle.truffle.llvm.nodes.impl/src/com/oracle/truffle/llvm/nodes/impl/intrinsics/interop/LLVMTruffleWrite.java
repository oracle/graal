/*
 * Copyright (c) 2016, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.nodes.impl.intrinsics.interop;

import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.NodeChildren;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.llvm.nodes.impl.base.LLVMAddressNode;
import com.oracle.truffle.llvm.nodes.impl.base.floating.LLVMDoubleNode;
import com.oracle.truffle.llvm.nodes.impl.base.floating.LLVMFloatNode;
import com.oracle.truffle.llvm.nodes.impl.base.integers.LLVMI1Node;
import com.oracle.truffle.llvm.nodes.impl.base.integers.LLVMI32Node;
import com.oracle.truffle.llvm.nodes.impl.base.integers.LLVMI64Node;
import com.oracle.truffle.llvm.nodes.impl.base.integers.LLVMI8Node;
import com.oracle.truffle.llvm.nodes.impl.intrinsics.llvm.LLVMIntrinsic.LLVMVoidIntrinsic;
import com.oracle.truffle.llvm.types.LLVMAddress;
import com.oracle.truffle.llvm.types.LLVMTruffleObject;

public final class LLVMTruffleWrite {

    private static void doWrite(VirtualFrame frame, Node foreignWrite, LLVMTruffleObject value, LLVMAddress id, Object v) {
        String name = LLVMTruffleIntrinsicUtil.readString(id);
        try {
            if (value.getIndex() != 0 || value.getName() != null) {
                throw new IllegalAccessError("Pointee must be unmodified");
            }
            ForeignAccess.sendWrite(foreignWrite, frame, value.getObject(), name, v);
        } catch (UnknownIdentifierException | UnsupportedMessageException | UnsupportedTypeException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void doWriteIdx(VirtualFrame frame, Node foreignWrite, LLVMTruffleObject value, int id, Object v) {
        try {
            if (value.getIndex() != 0 || value.getName() != null) {
                throw new IllegalAccessError("Pointee must be unmodified");
            }
            ForeignAccess.sendWrite(foreignWrite, frame, value.getObject(), id, v);
        } catch (UnknownIdentifierException | UnsupportedMessageException | UnsupportedTypeException e) {
            throw new IllegalStateException(e);
        }
    }

    @NodeChildren({@NodeChild(type = LLVMAddressNode.class), @NodeChild(type = LLVMAddressNode.class), @NodeChild(type = LLVMAddressNode.class)})
    public abstract static class LLVMTruffleWriteP extends LLVMVoidIntrinsic {

        @Child private Node foreignWrite = Message.WRITE.createNode();

        @Specialization
        public void executeIntrinsic(VirtualFrame frame, LLVMTruffleObject value, LLVMAddress id, LLVMAddress v) {
            doWrite(frame, foreignWrite, value, id, v);
        }
    }

    @NodeChildren({@NodeChild(type = LLVMAddressNode.class), @NodeChild(type = LLVMAddressNode.class), @NodeChild(type = LLVMI32Node.class)})
    public abstract static class LLVMTruffleWriteI extends LLVMVoidIntrinsic {

        @Child private Node foreignWrite = Message.WRITE.createNode();

        @Specialization
        public void executeIntrinsic(VirtualFrame frame, LLVMTruffleObject value, LLVMAddress id, int v) {
            doWrite(frame, foreignWrite, value, id, v);
        }
    }

    @NodeChildren({@NodeChild(type = LLVMAddressNode.class), @NodeChild(type = LLVMAddressNode.class), @NodeChild(type = LLVMI64Node.class)})
    public abstract static class LLVMTruffleWriteL extends LLVMVoidIntrinsic {

        @Child private Node foreignWrite = Message.WRITE.createNode();

        @Specialization
        public void executeIntrinsic(VirtualFrame frame, LLVMTruffleObject value, LLVMAddress id, long v) {
            doWrite(frame, foreignWrite, value, id, v);
        }
    }

    @NodeChildren({@NodeChild(type = LLVMAddressNode.class), @NodeChild(type = LLVMAddressNode.class), @NodeChild(type = LLVMI8Node.class)})
    public abstract static class LLVMTruffleWriteC extends LLVMVoidIntrinsic {

        @Child private Node foreignWrite = Message.WRITE.createNode();

        @Specialization
        public void executeIntrinsic(VirtualFrame frame, LLVMTruffleObject value, LLVMAddress id, byte v) {
            doWrite(frame, foreignWrite, value, id, v);
        }
    }

    @NodeChildren({@NodeChild(type = LLVMAddressNode.class), @NodeChild(type = LLVMAddressNode.class), @NodeChild(type = LLVMFloatNode.class)})
    public abstract static class LLVMTruffleWriteF extends LLVMVoidIntrinsic {

        @Child private Node foreignWrite = Message.WRITE.createNode();

        @Specialization
        public void executeIntrinsic(VirtualFrame frame, LLVMTruffleObject value, LLVMAddress id, float v) {
            doWrite(frame, foreignWrite, value, id, v);
        }
    }

    @NodeChildren({@NodeChild(type = LLVMAddressNode.class), @NodeChild(type = LLVMAddressNode.class), @NodeChild(type = LLVMDoubleNode.class)})
    public abstract static class LLVMTruffleWriteD extends LLVMVoidIntrinsic {

        @Child private Node foreignWrite = Message.WRITE.createNode();

        @Specialization
        public void executeIntrinsic(VirtualFrame frame, LLVMTruffleObject value, LLVMAddress id, double v) {
            doWrite(frame, foreignWrite, value, id, v);
        }
    }

    @NodeChildren({@NodeChild(type = LLVMAddressNode.class), @NodeChild(type = LLVMAddressNode.class), @NodeChild(type = LLVMI1Node.class)})
    public abstract static class LLVMTruffleWriteB extends LLVMVoidIntrinsic {

        @Child private Node foreignWrite = Message.WRITE.createNode();

        @Specialization
        public void executeIntrinsic(VirtualFrame frame, LLVMTruffleObject value, LLVMAddress id, boolean v) {
            doWrite(frame, foreignWrite, value, id, v);
        }
    }

    // INDEXED:
    @NodeChildren({@NodeChild(type = LLVMAddressNode.class), @NodeChild(type = LLVMI32Node.class), @NodeChild(type = LLVMAddressNode.class)})
    public abstract static class LLVMTruffleWriteIdxP extends LLVMVoidIntrinsic {

        @Child private Node foreignWrite = Message.WRITE.createNode();

        @Specialization
        public void executeIntrinsic(VirtualFrame frame, LLVMTruffleObject value, int id, LLVMAddress v) {
            doWriteIdx(frame, foreignWrite, value, id, v);
        }
    }

    @NodeChildren({@NodeChild(type = LLVMAddressNode.class), @NodeChild(type = LLVMI32Node.class), @NodeChild(type = LLVMI32Node.class)})
    public abstract static class LLVMTruffleWriteIdxI extends LLVMVoidIntrinsic {

        @Child private Node foreignWrite = Message.WRITE.createNode();

        @Specialization
        public void executeIntrinsic(VirtualFrame frame, LLVMTruffleObject value, int id, int v) {
            doWriteIdx(frame, foreignWrite, value, id, v);
        }
    }

    @NodeChildren({@NodeChild(type = LLVMAddressNode.class), @NodeChild(type = LLVMI32Node.class), @NodeChild(type = LLVMI64Node.class)})
    public abstract static class LLVMTruffleWriteIdxL extends LLVMVoidIntrinsic {

        @Child private Node foreignWrite = Message.WRITE.createNode();

        @Specialization
        public void executeIntrinsic(VirtualFrame frame, LLVMTruffleObject value, int id, long v) {
            doWriteIdx(frame, foreignWrite, value, id, v);
        }
    }

    @NodeChildren({@NodeChild(type = LLVMAddressNode.class), @NodeChild(type = LLVMI32Node.class), @NodeChild(type = LLVMI8Node.class)})
    public abstract static class LLVMTruffleWriteIdxC extends LLVMVoidIntrinsic {

        @Child private Node foreignWrite = Message.WRITE.createNode();

        @Specialization
        public void executeIntrinsic(VirtualFrame frame, LLVMTruffleObject value, int id, byte v) {
            doWriteIdx(frame, foreignWrite, value, id, v);
        }
    }

    @NodeChildren({@NodeChild(type = LLVMAddressNode.class), @NodeChild(type = LLVMI32Node.class), @NodeChild(type = LLVMFloatNode.class)})
    public abstract static class LLVMTruffleWriteIdxF extends LLVMVoidIntrinsic {

        @Child private Node foreignWrite = Message.WRITE.createNode();

        @Specialization
        public void executeIntrinsic(VirtualFrame frame, LLVMTruffleObject value, int id, float v) {
            doWriteIdx(frame, foreignWrite, value, id, v);
        }
    }

    @NodeChildren({@NodeChild(type = LLVMAddressNode.class), @NodeChild(type = LLVMI32Node.class), @NodeChild(type = LLVMDoubleNode.class)})
    public abstract static class LLVMTruffleWriteIdxD extends LLVMVoidIntrinsic {

        @Child private Node foreignWrite = Message.WRITE.createNode();

        @Specialization
        public void executeIntrinsic(VirtualFrame frame, LLVMTruffleObject value, int id, double v) {
            doWriteIdx(frame, foreignWrite, value, id, v);
        }
    }

    @NodeChildren({@NodeChild(type = LLVMAddressNode.class), @NodeChild(type = LLVMI32Node.class), @NodeChild(type = LLVMI1Node.class)})
    public abstract static class LLVMTruffleWriteIdxB extends LLVMVoidIntrinsic {

        @Child private Node foreignWrite = Message.WRITE.createNode();

        @Specialization
        public void executeIntrinsic(VirtualFrame frame, LLVMTruffleObject value, int id, boolean v) {
            doWriteIdx(frame, foreignWrite, value, id, v);
        }
    }
}
