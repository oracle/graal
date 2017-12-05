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
package com.oracle.truffle.llvm.nodes.memory;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.NodeChildren;
import com.oracle.truffle.api.dsl.NodeField;
import com.oracle.truffle.api.dsl.NodeFields;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.llvm.nodes.memory.LLVMAddressGetElementPtrNodeGen.LLVMIncrementPointerNodeGen;
import com.oracle.truffle.llvm.runtime.LLVMAddress;
import com.oracle.truffle.llvm.runtime.LLVMBoxedPrimitive;
import com.oracle.truffle.llvm.runtime.LLVMTruffleObject;
import com.oracle.truffle.llvm.runtime.LLVMVirtualAllocationAddress;
import com.oracle.truffle.llvm.runtime.global.LLVMGlobal;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMToNativeNode;
import com.oracle.truffle.llvm.runtime.types.PointerType;
import com.oracle.truffle.llvm.runtime.types.Type;

@NodeChildren({@NodeChild(type = LLVMExpressionNode.class), @NodeChild(type = LLVMExpressionNode.class)})
@NodeFields({@NodeField(type = int.class, name = "typeWidth"), @NodeField(type = Type.class, name = "targetType")})
public abstract class LLVMAddressGetElementPtrNode extends LLVMExpressionNode {

    public abstract int getTypeWidth();

    public abstract Type getTargetType();

    protected LLVMIncrementPointerNode getIncrementPointerNode() {
        return LLVMIncrementPointerNodeGen.create();
    }

    @Specialization
    protected Object intIncrement(VirtualFrame frame, Object addr, int val,
                    @Cached("getIncrementPointerNode()") LLVMIncrementPointerNode incrementNode) {
        int incr = getTypeWidth() * val;
        return incrementNode.executeWithTarget(frame, addr, incr, getTargetType());
    }

    @Specialization
    protected Object longIncrement(VirtualFrame frame, Object addr, long val,
                    @Cached("getIncrementPointerNode()") LLVMIncrementPointerNode incrementNode) {
        long incr = getTypeWidth() * val;
        return incrementNode.executeWithTarget(frame, addr, incr, getTargetType());
    }

    public abstract static class LLVMIncrementPointerNode extends LLVMNode {

        public abstract Object executeWithTarget(VirtualFrame frame, Object addr, Object val, Type targetType);

        @Specialization
        protected LLVMAddress doPointee(LLVMAddress addr, int incr, @SuppressWarnings("unused") Type targetType) {
            return addr.increment(incr);
        }

        @Specialization
        protected LLVMVirtualAllocationAddress doTruffleObject(LLVMVirtualAllocationAddress addr, int incr, @SuppressWarnings("unused") Type targetType) {
            return addr.increment(incr);
        }

        @Specialization
        protected LLVMVirtualAllocationAddress doTruffleObject(LLVMVirtualAllocationAddress addr, long incr, @SuppressWarnings("unused") Type targetType) {
            return addr.increment(incr);
        }

        @Specialization
        protected LLVMAddress executePointee(VirtualFrame frame, LLVMGlobal addr, int incr, @SuppressWarnings("unused") Type targetType,
                        @Cached("createToNativeWithTarget()") LLVMToNativeNode globalAccess) {
            return globalAccess.executeWithTarget(frame, addr).increment(incr);
        }

        @Specialization
        protected LLVMTruffleObject doTruffleObject(LLVMTruffleObject addr, int incr, Type targetType) {
            return addr.increment(incr, new PointerType(targetType));
        }

        @Specialization
        protected LLVMAddress doLLVMBoxedPrimitive(LLVMBoxedPrimitive addr, int incr, @SuppressWarnings("unused") Type targetType) {
            if (addr.getValue() instanceof Long) {
                return LLVMAddress.fromLong((long) addr.getValue() + incr);
            } else {
                CompilerDirectives.transferToInterpreter();
                throw new IllegalAccessError("Cannot do pointer arithmetic with address: " + addr.getValue());
            }
        }

        @Specialization
        protected LLVMAddress doPointee(LLVMAddress addr, long incr, @SuppressWarnings("unused") Type targetType) {
            return addr.increment(incr);
        }

        @Specialization
        protected LLVMTruffleObject doTruffleObject(LLVMTruffleObject addr, long incr, Type targetType) {
            return addr.increment(incr, new PointerType(targetType));
        }

        @Specialization
        protected LLVMAddress doLLVMBoxedPrimitive(LLVMBoxedPrimitive addr, long incr, @SuppressWarnings("unused") Type targetType) {
            if (addr.getValue() instanceof Long) {
                return LLVMAddress.fromLong((long) addr.getValue() + incr);
            } else {
                CompilerDirectives.transferToInterpreter();
                throw new IllegalAccessError("Cannot do pointer arithmetic with address: " + addr.getValue());
            }
        }

        @Specialization
        protected LLVMAddress executePointee(VirtualFrame frame, LLVMGlobal addr, long incr, @SuppressWarnings("unused") Type targetType,
                        @Cached("createToNativeWithTarget()") LLVMToNativeNode globalAccess) {
            return globalAccess.executeWithTarget(frame, addr).increment(incr);
        }
    }
}
