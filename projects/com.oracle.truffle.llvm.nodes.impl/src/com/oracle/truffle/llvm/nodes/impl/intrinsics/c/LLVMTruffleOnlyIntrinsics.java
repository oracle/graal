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
package com.oracle.truffle.llvm.nodes.impl.intrinsics.c;

import com.oracle.nfi.api.NativeFunctionHandle;
import com.oracle.truffle.api.CompilerDirectives;
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
import com.oracle.truffle.llvm.nativeint.NativeLookup;
import com.oracle.truffle.llvm.nodes.base.LLVMExpressionNode;
import com.oracle.truffle.llvm.nodes.impl.intrinsics.interop.ToLLVMNode;
import com.oracle.truffle.llvm.nodes.impl.intrinsics.llvm.LLVMIntrinsic.LLVMI32Intrinsic;
import com.oracle.truffle.llvm.nodes.impl.intrinsics.llvm.LLVMIntrinsic.LLVMI64Intrinsic;
import com.oracle.truffle.llvm.types.LLVMAddress;
import com.oracle.truffle.llvm.types.LLVMFunction;

public final class LLVMTruffleOnlyIntrinsics {

    private LLVMTruffleOnlyIntrinsics() {
    }

    public abstract static class LLVMTruffleOnlyI64Intrinsic extends LLVMI64Intrinsic {

        protected final NativeFunctionHandle handle;

        public LLVMTruffleOnlyI64Intrinsic(LLVMFunction descriptor) {
            handle = NativeLookup.getNFI().getFunctionHandle(descriptor.getName().substring(1), getReturnValueClass(), getParameterClasses());
        }

        protected abstract Class<?>[] getParameterClasses();

        protected Class<?> getReturnValueClass() {
            return long.class;
        }
    }

    public abstract static class LLVMTruffleOnlyI32Intrinsic extends LLVMI32Intrinsic {

        protected final NativeFunctionHandle handle;

        public LLVMTruffleOnlyI32Intrinsic(LLVMFunction descriptor) {
            handle = NativeLookup.getNFI().getFunctionHandle(descriptor.getName().substring(1), getReturnValueClass(), getParameterClasses());
        }

        protected abstract Class<?>[] getParameterClasses();

        protected Class<?> getReturnValueClass() {
            return int.class;
        }
    }

    @NodeChild(type = LLVMExpressionNode.class)
    public abstract static class LLVMStrlen extends LLVMTruffleOnlyI64Intrinsic {

        public LLVMStrlen(LLVMFunction descriptor) {
            super(descriptor);
        }

        @Override
        protected Class<?>[] getParameterClasses() {
            return new Class<?>[]{long.class};
        }

        @Specialization
        public long executeIntrinsic(LLVMAddress string) {
            return (long) handle.call(string.getVal());
        }

        @Child private Node foreignHasSize = Message.HAS_SIZE.createNode();
        @Child private Node foreignGetSize = Message.GET_SIZE.createNode();
        @Child private ToLLVMNode toLLVM = new ToLLVMNode();

        @Specialization
        public long executeIntrinsic(VirtualFrame frame, TruffleObject object) {
            boolean hasSize = ForeignAccess.sendHasSize(foreignHasSize, frame, object);
            if (hasSize) {
                Object strlen;
                try {
                    strlen = ForeignAccess.sendGetSize(foreignGetSize, frame, object);
                    long size = toLLVM.convert(frame, strlen, long.class);
                    return size;
                } catch (UnsupportedMessageException e) {
                    throw new AssertionError(e);
                }
            } else {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw new AssertionError(object);
            }
        }

    }

    @NodeChildren({@NodeChild(type = LLVMExpressionNode.class), @NodeChild(type = LLVMExpressionNode.class)})
    public abstract static class LLVMStrCmp extends LLVMTruffleOnlyI32Intrinsic {

        public LLVMStrCmp(LLVMFunction descriptor) {
            super(descriptor);
        }

        @Override
        protected Class<?>[] getParameterClasses() {
            return new Class<?>[]{long.class, long.class};
        }

        @Specialization
        public int executeIntrinsic(LLVMAddress str1, LLVMAddress str2) {
            return (int) handle.call(str1.getVal(), str2.getVal());
        }

        @Child private Node readStr1 = Message.READ.createNode();
        @Child private Node readStr2 = Message.READ.createNode();
        @Child private Node getSize1 = Message.GET_SIZE.createNode();
        @Child private Node getSize2 = Message.GET_SIZE.createNode();
        @Child private ToLLVMNode toLLVMSize1 = new ToLLVMNode();
        @Child private ToLLVMNode toLLVMSize2 = new ToLLVMNode();
        @Child private ToLLVMNode toLLVM1 = new ToLLVMNode();
        @Child private ToLLVMNode toLLVM2 = new ToLLVMNode();

        @Specialization
        public int executeIntrinsic(VirtualFrame frame, TruffleObject str1, TruffleObject str2) {
            try {
                return execute(frame, str1, str2);
            } catch (Exception e) {
                throw new AssertionError(e);
            }
        }

        private int execute(VirtualFrame frame, TruffleObject str1, TruffleObject str2) throws UnsupportedMessageException, UnknownIdentifierException {
            long size1 = toLLVMSize1.convert(frame, ForeignAccess.sendGetSize(getSize1, frame, str1), long.class);
            long size2 = toLLVMSize2.convert(frame, ForeignAccess.sendGetSize(getSize2, frame, str2), long.class);
            int i;
            for (i = 0; i < size1; i++) {
                Object s1 = ForeignAccess.sendRead(readStr1, frame, str1, i);
                char c1 = toLLVM1.convert(frame, s1, char.class);
                if (i >= size2) {
                    return c1;
                }
                Object s2 = ForeignAccess.sendRead(readStr2, frame, str2, i);
                char c2 = toLLVM2.convert(frame, s2, char.class);
                if (c1 != c2) {
                    return c1 - c2;
                }
            }
            if (i < size2) {
                Object s2 = ForeignAccess.sendRead(readStr2, frame, str2, i);
                char c2 = toLLVM2.convert(frame, s2, char.class);
                return -c2;
            } else {
                return 0;
            }
        }

    }

}
