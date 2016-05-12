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
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.llvm.nodes.impl.base.LLVMAddressNode;
import com.oracle.truffle.llvm.nodes.impl.base.integers.LLVMI32Node;
import com.oracle.truffle.llvm.nodes.impl.intrinsics.llvm.LLVMIntrinsic.LLVMAddressIntrinsic;
import com.oracle.truffle.llvm.nodes.impl.intrinsics.llvm.LLVMIntrinsic.LLVMBooleanIntrinsic;
import com.oracle.truffle.llvm.nodes.impl.intrinsics.llvm.LLVMIntrinsic.LLVMDoubleIntrinsic;
import com.oracle.truffle.llvm.nodes.impl.intrinsics.llvm.LLVMIntrinsic.LLVMFloatIntrinsic;
import com.oracle.truffle.llvm.nodes.impl.intrinsics.llvm.LLVMIntrinsic.LLVMI32Intrinsic;
import com.oracle.truffle.llvm.nodes.impl.intrinsics.llvm.LLVMIntrinsic.LLVMI64Intrinsic;
import com.oracle.truffle.llvm.nodes.impl.intrinsics.llvm.LLVMIntrinsic.LLVMI8Intrinsic;
import com.oracle.truffle.llvm.types.LLVMAddress;
import com.oracle.truffle.llvm.types.LLVMTruffleObject;

public final class LLVMTruffleRead {

    private static Object doRead(VirtualFrame frame, Node foreignRead, LLVMTruffleObject value, LLVMAddress id, ToLLVMNode toLLVM, Class<?> expectedType) {
        String name = LLVMTruffleIntrinsicUtil.readString(id);
        try {
            if (value.getOffset() != 0 || value.getName() != null) {
                throw new IllegalAccessError("Pointee must be unmodified");
            }
            Object rawValue = ForeignAccess.sendRead(foreignRead, frame, value.getObject(), name);
            return toLLVM.convert(frame, rawValue, expectedType);
        } catch (UnknownIdentifierException | UnsupportedMessageException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Object doReadIdx(VirtualFrame frame, Node foreignRead, LLVMTruffleObject value, int id, ToLLVMNode toLLVM, Class<?> expectedType) {
        try {
            if (value.getOffset() != 0 || value.getName() != null) {
                throw new IllegalAccessError("Pointee must be unmodified");
            }
            Object rawValue = ForeignAccess.sendRead(foreignRead, frame, value.getObject(), id);
            return toLLVM.convert(frame, rawValue, expectedType);
        } catch (UnknownIdentifierException | UnsupportedMessageException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Object doRead(VirtualFrame frame, Node foreignRead, TruffleObject value, LLVMAddress id, ToLLVMNode toLLVM, Class<?> expectedType) {
        String name = LLVMTruffleIntrinsicUtil.readString(id);
        try {
            Object rawValue = ForeignAccess.sendRead(foreignRead, frame, value, name);
            return toLLVM.convert(frame, rawValue, expectedType);
        } catch (UnknownIdentifierException | UnsupportedMessageException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Object doReadIdx(VirtualFrame frame, Node foreignRead, TruffleObject value, int id, ToLLVMNode toLLVM, Class<?> expectedType) {
        try {
            Object rawValue = ForeignAccess.sendRead(foreignRead, frame, value, id);
            return toLLVM.convert(frame, rawValue, expectedType);
        } catch (UnknownIdentifierException | UnsupportedMessageException e) {
            throw new IllegalStateException(e);
        }
    }

    @NodeChildren({@NodeChild(type = LLVMAddressNode.class), @NodeChild(type = LLVMAddressNode.class)})
    public abstract static class LLVMTruffleReadP extends LLVMAddressIntrinsic {

        @Child private Node foreignRead = Message.READ.createNode();
        @Child private ToLLVMNode toLLVM = new ToLLVMNode();

        private static final Class<?> expectedType = TruffleObject.class;

        @Specialization
        public Object executeIntrinsic(VirtualFrame frame, LLVMTruffleObject value, LLVMAddress id) {
            return doRead(frame, foreignRead, value, id, toLLVM, expectedType);
        }

        @Specialization
        public Object executeIntrinsic(VirtualFrame frame, TruffleObject value, LLVMAddress id) {
            return doRead(frame, foreignRead, value, id, toLLVM, expectedType);
        }
    }

    @NodeChildren({@NodeChild(type = LLVMAddressNode.class), @NodeChild(type = LLVMAddressNode.class)})
    public abstract static class LLVMTruffleReadI extends LLVMI32Intrinsic {

        @Child private Node foreignRead = Message.READ.createNode();
        @Child private ToLLVMNode toLLVM = new ToLLVMNode();

        private static final Class<?> expectedType = int.class;

        @Specialization
        public int executeIntrinsic(VirtualFrame frame, LLVMTruffleObject value, LLVMAddress id) {
            return (int) doRead(frame, foreignRead, value, id, toLLVM, expectedType);
        }

        @Specialization
        public int executeIntrinsic(VirtualFrame frame, TruffleObject value, LLVMAddress id) {
            return (int) doRead(frame, foreignRead, value, id, toLLVM, expectedType);
        }
    }

    @NodeChildren({@NodeChild(type = LLVMAddressNode.class), @NodeChild(type = LLVMAddressNode.class)})
    public abstract static class LLVMTruffleReadL extends LLVMI64Intrinsic {

        @Child private Node foreignRead = Message.READ.createNode();
        @Child private ToLLVMNode toLLVM = new ToLLVMNode();

        private static final Class<?> expectedType = long.class;

        @Specialization
        public long executeIntrinsic(VirtualFrame frame, LLVMTruffleObject value, LLVMAddress id) {
            return (long) doRead(frame, foreignRead, value, id, toLLVM, expectedType);
        }

        @Specialization
        public long executeIntrinsic(VirtualFrame frame, TruffleObject value, LLVMAddress id) {
            return (long) doRead(frame, foreignRead, value, id, toLLVM, expectedType);
        }
    }

    @NodeChildren({@NodeChild(type = LLVMAddressNode.class), @NodeChild(type = LLVMAddressNode.class)})
    public abstract static class LLVMTruffleReadC extends LLVMI8Intrinsic {

        @Child private Node foreignRead = Message.READ.createNode();
        @Child private ToLLVMNode toLLVM = new ToLLVMNode();

        private static final Class<?> expectedType = byte.class;

        @Specialization
        public byte executeIntrinsic(VirtualFrame frame, LLVMTruffleObject value, LLVMAddress id) {
            return (byte) doRead(frame, foreignRead, value, id, toLLVM, expectedType);
        }

        @Specialization
        public byte executeIntrinsic(VirtualFrame frame, TruffleObject value, LLVMAddress id) {
            return (byte) doRead(frame, foreignRead, value, id, toLLVM, expectedType);
        }
    }

    @NodeChildren({@NodeChild(type = LLVMAddressNode.class), @NodeChild(type = LLVMAddressNode.class)})
    public abstract static class LLVMTruffleReadF extends LLVMFloatIntrinsic {

        @Child private Node foreignRead = Message.READ.createNode();
        @Child private ToLLVMNode toLLVM = new ToLLVMNode();

        private static final Class<?> expectedType = float.class;

        @Specialization
        public float executeIntrinsic(VirtualFrame frame, LLVMTruffleObject value, LLVMAddress id) {
            return (float) doRead(frame, foreignRead, value, id, toLLVM, expectedType);
        }

        @Specialization
        public float executeIntrinsic(VirtualFrame frame, TruffleObject value, LLVMAddress id) {
            return (float) doRead(frame, foreignRead, value, id, toLLVM, expectedType);
        }
    }

    @NodeChildren({@NodeChild(type = LLVMAddressNode.class), @NodeChild(type = LLVMAddressNode.class)})
    public abstract static class LLVMTruffleReadD extends LLVMDoubleIntrinsic {

        @Child private Node foreignRead = Message.READ.createNode();
        @Child private ToLLVMNode toLLVM = new ToLLVMNode();

        private static final Class<?> expectedType = double.class;

        @Specialization
        public double executeIntrinsic(VirtualFrame frame, LLVMTruffleObject value, LLVMAddress id) {
            return (double) doRead(frame, foreignRead, value, id, toLLVM, expectedType);
        }

        @Specialization
        public double executeIntrinsic(VirtualFrame frame, TruffleObject value, LLVMAddress id) {
            return (double) doRead(frame, foreignRead, value, id, toLLVM, expectedType);
        }
    }

    @NodeChildren({@NodeChild(type = LLVMAddressNode.class), @NodeChild(type = LLVMAddressNode.class)})
    public abstract static class LLVMTruffleReadB extends LLVMBooleanIntrinsic {

        @Child private Node foreignRead = Message.READ.createNode();
        @Child private ToLLVMNode toLLVM = new ToLLVMNode();

        private static final Class<?> expectedType = boolean.class;

        @Specialization
        public boolean executeIntrinsic(VirtualFrame frame, LLVMTruffleObject value, LLVMAddress id) {
            return (boolean) doRead(frame, foreignRead, value, id, toLLVM, expectedType);
        }

        @Specialization
        public boolean executeIntrinsic(VirtualFrame frame, TruffleObject value, LLVMAddress id) {
            return (boolean) doRead(frame, foreignRead, value, id, toLLVM, expectedType);
        }
    }

    // INDEXED:

    @NodeChildren({@NodeChild(type = LLVMAddressNode.class), @NodeChild(type = LLVMI32Node.class)})
    public abstract static class LLVMTruffleReadIdxP extends LLVMAddressIntrinsic {

        @Child private Node foreignRead = Message.READ.createNode();
        @Child private ToLLVMNode toLLVM = new ToLLVMNode();

        private static final Class<?> expectedType = TruffleObject.class;

        @Specialization
        public Object executeIntrinsic(VirtualFrame frame, LLVMTruffleObject value, int id) {
            return doReadIdx(frame, foreignRead, value, id, toLLVM, expectedType);
        }

        @Specialization
        public Object executeIntrinsic(VirtualFrame frame, TruffleObject value, int id) {
            return doReadIdx(frame, foreignRead, value, id, toLLVM, expectedType);
        }
    }

    @NodeChildren({@NodeChild(type = LLVMAddressNode.class), @NodeChild(type = LLVMI32Node.class)})
    public abstract static class LLVMTruffleReadIdxI extends LLVMI32Intrinsic {

        @Child private Node foreignRead = Message.READ.createNode();
        @Child private ToLLVMNode toLLVM = new ToLLVMNode();

        private static final Class<?> expectedType = int.class;

        @Specialization
        public int executeIntrinsic(VirtualFrame frame, LLVMTruffleObject value, int id) {
            return (int) doReadIdx(frame, foreignRead, value, id, toLLVM, expectedType);
        }

        @Specialization
        public int executeIntrinsic(VirtualFrame frame, TruffleObject value, int id) {
            return (int) doReadIdx(frame, foreignRead, value, id, toLLVM, expectedType);
        }
    }

    @NodeChildren({@NodeChild(type = LLVMAddressNode.class), @NodeChild(type = LLVMI32Node.class)})
    public abstract static class LLVMTruffleReadIdxL extends LLVMI64Intrinsic {

        @Child private Node foreignRead = Message.READ.createNode();
        @Child private ToLLVMNode toLLVM = new ToLLVMNode();

        private static final Class<?> expectedType = long.class;

        @Specialization
        public long executeIntrinsic(VirtualFrame frame, LLVMTruffleObject value, int id) {
            return (long) doReadIdx(frame, foreignRead, value, id, toLLVM, expectedType);
        }

        @Specialization
        public long executeIntrinsic(VirtualFrame frame, TruffleObject value, int id) {
            return (long) doReadIdx(frame, foreignRead, value, id, toLLVM, expectedType);
        }
    }

    @NodeChildren({@NodeChild(type = LLVMAddressNode.class), @NodeChild(type = LLVMI32Node.class)})
    public abstract static class LLVMTruffleReadIdxC extends LLVMI8Intrinsic {

        @Child private Node foreignRead = Message.READ.createNode();
        @Child private ToLLVMNode toLLVM = new ToLLVMNode();

        private static final Class<?> expectedType = byte.class;

        @Specialization
        public byte executeIntrinsic(VirtualFrame frame, LLVMTruffleObject value, int id) {
            return (byte) doReadIdx(frame, foreignRead, value, id, toLLVM, expectedType);
        }

        @Specialization
        public byte executeIntrinsic(VirtualFrame frame, TruffleObject value, int id) {
            return (byte) doReadIdx(frame, foreignRead, value, id, toLLVM, expectedType);
        }
    }

    @NodeChildren({@NodeChild(type = LLVMAddressNode.class), @NodeChild(type = LLVMI32Node.class)})
    public abstract static class LLVMTruffleReadIdxF extends LLVMFloatIntrinsic {

        @Child private Node foreignRead = Message.READ.createNode();
        @Child private ToLLVMNode toLLVM = new ToLLVMNode();

        private static final Class<?> expectedType = float.class;

        @Specialization
        public float executeIntrinsic(VirtualFrame frame, LLVMTruffleObject value, int id) {
            return (float) doReadIdx(frame, foreignRead, value, id, toLLVM, expectedType);
        }

        @Specialization
        public float executeIntrinsic(VirtualFrame frame, TruffleObject value, int id) {
            return (float) doReadIdx(frame, foreignRead, value, id, toLLVM, expectedType);
        }
    }

    @NodeChildren({@NodeChild(type = LLVMAddressNode.class), @NodeChild(type = LLVMI32Node.class)})
    public abstract static class LLVMTruffleReadIdxD extends LLVMDoubleIntrinsic {

        @Child private Node foreignRead = Message.READ.createNode();
        @Child private ToLLVMNode toLLVM = new ToLLVMNode();

        private static final Class<?> expectedType = double.class;

        @Specialization
        public double executeIntrinsic(VirtualFrame frame, LLVMTruffleObject value, int id) {
            return (double) doReadIdx(frame, foreignRead, value, id, toLLVM, expectedType);
        }

        @Specialization
        public double executeIntrinsic(VirtualFrame frame, TruffleObject value, int id) {
            return (double) doReadIdx(frame, foreignRead, value, id, toLLVM, expectedType);
        }
    }

    @NodeChildren({@NodeChild(type = LLVMAddressNode.class), @NodeChild(type = LLVMI32Node.class)})
    public abstract static class LLVMTruffleReadIdxB extends LLVMBooleanIntrinsic {

        @Child private Node foreignRead = Message.READ.createNode();
        @Child private ToLLVMNode toLLVM = new ToLLVMNode();

        private static final Class<?> expectedType = boolean.class;

        @Specialization
        public boolean executeIntrinsic(VirtualFrame frame, LLVMTruffleObject value, int id) {
            return (boolean) doReadIdx(frame, foreignRead, value, id, toLLVM, expectedType);
        }

        @Specialization
        public boolean executeIntrinsic(VirtualFrame frame, TruffleObject value, int id) {
            return (boolean) doReadIdx(frame, foreignRead, value, id, toLLVM, expectedType);
        }
    }

}
