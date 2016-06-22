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

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.NodeChildren;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.llvm.nodes.base.LLVMExpressionNode;
import com.oracle.truffle.llvm.nodes.impl.func.LLVMCallNode;
import com.oracle.truffle.llvm.nodes.impl.intrinsics.llvm.LLVMIntrinsic.LLVMAddressIntrinsic;
import com.oracle.truffle.llvm.nodes.impl.intrinsics.llvm.LLVMIntrinsic.LLVMBooleanIntrinsic;
import com.oracle.truffle.llvm.nodes.impl.intrinsics.llvm.LLVMIntrinsic.LLVMDoubleIntrinsic;
import com.oracle.truffle.llvm.nodes.impl.intrinsics.llvm.LLVMIntrinsic.LLVMFloatIntrinsic;
import com.oracle.truffle.llvm.nodes.impl.intrinsics.llvm.LLVMIntrinsic.LLVMI32Intrinsic;
import com.oracle.truffle.llvm.nodes.impl.intrinsics.llvm.LLVMIntrinsic.LLVMI64Intrinsic;
import com.oracle.truffle.llvm.nodes.impl.intrinsics.llvm.LLVMIntrinsic.LLVMI8Intrinsic;
import com.oracle.truffle.llvm.types.LLVMTruffleObject;

public final class LLVMTruffleExecute {

    private static final int NAMED_ARGS = 1;

    private static Object doExecute(VirtualFrame frame, Node foreignExecute, LLVMTruffleObject value, ToLLVMNode toLLVM, Class<?> expectedType) {
        int argsLength = getFunctionArgumentLength(frame);
        Object[] args = new Object[argsLength];
        for (int i = LLVMCallNode.ARG_START_INDEX + NAMED_ARGS, j = 0; i < frame.getArguments().length; i++, j++) {
            args[j] = frame.getArguments()[i];
        }
        try {
            if (value.getOffset() != 0 || value.getName() != null) {
                throw new IllegalAccessError("Pointee must be unmodified");
            }
            Object rawValue = ForeignAccess.sendExecute(foreignExecute, frame, value.getObject(), args);
            return toLLVM.convert(frame, rawValue, expectedType);
        } catch (UnsupportedMessageException | UnsupportedTypeException | ArityException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Object doExecute(VirtualFrame frame, Node foreignExecute, TruffleObject value, ToLLVMNode toLLVM, Class<?> expectedType) {
        int argsLength = getFunctionArgumentLength(frame);
        Object[] args = new Object[argsLength];
        for (int i = LLVMCallNode.ARG_START_INDEX + NAMED_ARGS, j = 0; i < frame.getArguments().length; i++, j++) {
            args[j] = frame.getArguments()[i];
        }
        try {
            Object rawValue = ForeignAccess.sendExecute(foreignExecute, frame, value, args);
            return toLLVM.convert(frame, rawValue, expectedType);
        } catch (UnsupportedMessageException | UnsupportedTypeException | ArityException e) {
            throw new IllegalStateException(e);
        }
    }

    private static int getFunctionArgumentLength(VirtualFrame frame) {
        return frame.getArguments().length - LLVMCallNode.ARG_START_INDEX - NAMED_ARGS;
    }

    @NodeChildren({@NodeChild(type = LLVMExpressionNode.class)})
    public abstract static class LLVMTruffleExecuteP extends LLVMAddressIntrinsic {

        @Child private Node foreignExecute;
        @Child private ToLLVMNode toLLVM = new ToLLVMNode();

        private static final Class<?> expectedType = TruffleObject.class;

        @Specialization
        public Object executeIntrinsic(VirtualFrame frame, LLVMTruffleObject value) {
            if (foreignExecute == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                foreignExecute = insert(Message.createExecute(getFunctionArgumentLength(frame)).createNode());
            }
            return doExecute(frame, foreignExecute, value, toLLVM, expectedType);
        }

        @Specialization
        public Object executeIntrinsic(VirtualFrame frame, TruffleObject value) {
            if (foreignExecute == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                foreignExecute = insert(Message.createExecute(getFunctionArgumentLength(frame)).createNode());
            }
            return doExecute(frame, foreignExecute, value, toLLVM, expectedType);
        }
    }

    @NodeChildren({@NodeChild(type = LLVMExpressionNode.class)})
    public abstract static class LLVMTruffleExecuteI extends LLVMI32Intrinsic {

        @Child private Node foreignExecute;
        @Child private ToLLVMNode toLLVM = new ToLLVMNode();

        private static final Class<?> expectedType = int.class;

        @Specialization
        public int executeIntrinsic(VirtualFrame frame, LLVMTruffleObject value) {
            if (foreignExecute == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                foreignExecute = insert(Message.createExecute(getFunctionArgumentLength(frame)).createNode());
            }
            return (int) doExecute(frame, foreignExecute, value, toLLVM, expectedType);
        }

        @Specialization
        public int executeIntrinsic(VirtualFrame frame, TruffleObject value) {
            if (foreignExecute == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                foreignExecute = insert(Message.createExecute(getFunctionArgumentLength(frame)).createNode());
            }
            return (int) doExecute(frame, foreignExecute, value, toLLVM, expectedType);
        }
    }

    @NodeChildren({@NodeChild(type = LLVMExpressionNode.class)})
    public abstract static class LLVMTruffleExecuteL extends LLVMI64Intrinsic {

        @Child private Node foreignExecute;
        @Child private ToLLVMNode toLLVM = new ToLLVMNode();

        private static final Class<?> expectedType = long.class;

        @Specialization
        public long executeIntrinsic(VirtualFrame frame, LLVMTruffleObject value) {
            if (foreignExecute == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                foreignExecute = insert(Message.createExecute(getFunctionArgumentLength(frame)).createNode());
            }
            return (long) doExecute(frame, foreignExecute, value, toLLVM, expectedType);
        }

        @Specialization
        public long executeIntrinsic(VirtualFrame frame, TruffleObject value) {
            if (foreignExecute == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                foreignExecute = insert(Message.createExecute(getFunctionArgumentLength(frame)).createNode());
            }
            return (long) doExecute(frame, foreignExecute, value, toLLVM, expectedType);
        }
    }

    @NodeChildren({@NodeChild(type = LLVMExpressionNode.class)})
    public abstract static class LLVMTruffleExecuteC extends LLVMI8Intrinsic {

        @Child private Node foreignExecute;
        @Child private ToLLVMNode toLLVM = new ToLLVMNode();

        private static final Class<?> expectedType = byte.class;

        @Specialization
        public byte executeIntrinsic(VirtualFrame frame, LLVMTruffleObject value) {
            if (foreignExecute == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                foreignExecute = insert(Message.createExecute(getFunctionArgumentLength(frame)).createNode());
            }
            return (byte) doExecute(frame, foreignExecute, value, toLLVM, expectedType);
        }

        @Specialization
        public byte executeIntrinsic(VirtualFrame frame, TruffleObject value) {
            if (foreignExecute == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                foreignExecute = insert(Message.createExecute(getFunctionArgumentLength(frame)).createNode());
            }
            return (byte) doExecute(frame, foreignExecute, value, toLLVM, expectedType);
        }
    }

    @NodeChildren({@NodeChild(type = LLVMExpressionNode.class)})
    public abstract static class LLVMTruffleExecuteF extends LLVMFloatIntrinsic {

        @Child private Node foreignExecute;
        @Child private ToLLVMNode toLLVM = new ToLLVMNode();

        private static final Class<?> expectedType = float.class;

        @Specialization
        public float executeIntrinsic(VirtualFrame frame, LLVMTruffleObject value) {
            if (foreignExecute == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                foreignExecute = insert(Message.createExecute(getFunctionArgumentLength(frame)).createNode());
            }
            return (float) doExecute(frame, foreignExecute, value, toLLVM, expectedType);
        }

        @Specialization
        public float executeIntrinsic(VirtualFrame frame, TruffleObject value) {
            if (foreignExecute == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                foreignExecute = insert(Message.createExecute(getFunctionArgumentLength(frame)).createNode());
            }
            return (float) doExecute(frame, foreignExecute, value, toLLVM, expectedType);
        }
    }

    @NodeChildren({@NodeChild(type = LLVMExpressionNode.class)})
    public abstract static class LLVMTruffleExecuteD extends LLVMDoubleIntrinsic {

        @Child private Node foreignExecute;
        @Child private ToLLVMNode toLLVM = new ToLLVMNode();

        private static final Class<?> expectedType = double.class;

        @Specialization
        public double executeIntrinsic(VirtualFrame frame, LLVMTruffleObject value) {
            if (foreignExecute == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                foreignExecute = insert(Message.createExecute(getFunctionArgumentLength(frame)).createNode());
            }
            return (double) doExecute(frame, foreignExecute, value, toLLVM, expectedType);
        }

        @Specialization
        public double executeIntrinsic(VirtualFrame frame, TruffleObject value) {
            if (foreignExecute == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                foreignExecute = insert(Message.createExecute(getFunctionArgumentLength(frame)).createNode());
            }
            return (double) doExecute(frame, foreignExecute, value, toLLVM, expectedType);
        }
    }

    @NodeChildren({@NodeChild(type = LLVMExpressionNode.class)})
    public abstract static class LLVMTruffleExecuteB extends LLVMBooleanIntrinsic {

        @Child private Node foreignExecute;
        @Child private ToLLVMNode toLLVM = new ToLLVMNode();

        private static final Class<?> expectedType = boolean.class;

        @Specialization
        public boolean executeIntrinsic(VirtualFrame frame, LLVMTruffleObject value) {
            if (foreignExecute == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                foreignExecute = insert(Message.createExecute(getFunctionArgumentLength(frame)).createNode());
            }
            return (boolean) doExecute(frame, foreignExecute, value, toLLVM, expectedType);
        }

        @Specialization
        public boolean executeIntrinsic(VirtualFrame frame, TruffleObject value) {
            if (foreignExecute == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                foreignExecute = insert(Message.createExecute(getFunctionArgumentLength(frame)).createNode());
            }
            return (boolean) doExecute(frame, foreignExecute, value, toLLVM, expectedType);
        }
    }
}
