/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.runtime.nodes.asm;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.llvm.runtime.nodes.asm.support.LLVMAMD64UpdateFlagsNode.LLVMAMD64UpdateCPAZSOFlagsNode;
import com.oracle.truffle.llvm.runtime.nodes.asm.support.LLVMAMD64WriteValueNode;
import com.oracle.truffle.llvm.runtime.nodes.op.ToComparableValue;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMStatementNode;
import com.oracle.truffle.llvm.runtime.nodes.op.ToComparableValueNodeGen;
import com.oracle.truffle.llvm.runtime.pointer.LLVMManagedPointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMNativePointer;

@NodeChild(value = "a", type = LLVMExpressionNode.class)
@NodeChild(value = "src", type = LLVMExpressionNode.class)
@NodeChild(value = "dst", type = LLVMExpressionNode.class)
public abstract class LLVMAMD64CmpXchgNode extends LLVMStatementNode {
    @Child protected LLVMAMD64UpdateCPAZSOFlagsNode flags;

    @Child protected LLVMAMD64WriteValueNode out1;
    @Child protected LLVMAMD64WriteValueNode out2;

    protected final ConditionProfile profile;

    private LLVMAMD64CmpXchgNode(LLVMAMD64UpdateCPAZSOFlagsNode flags, LLVMAMD64WriteValueNode out1, LLVMAMD64WriteValueNode out2) {
        this.flags = flags;
        this.out1 = out1;
        this.out2 = out2;
        profile = ConditionProfile.createCountingProfile();
    }

    public abstract static class LLVMAMD64CmpXchgbNode extends LLVMAMD64CmpXchgNode {
        public LLVMAMD64CmpXchgbNode(LLVMAMD64UpdateCPAZSOFlagsNode flags, LLVMAMD64WriteValueNode dst1, LLVMAMD64WriteValueNode dst2) {
            super(flags, dst1, dst2);
        }

        @Specialization
        protected void doOp(VirtualFrame frame, byte a, byte src, byte dst) {
            int result = a - dst;
            boolean carry = Byte.toUnsignedInt(a) < Byte.toUnsignedInt(dst);
            boolean adjust = (((a ^ dst) ^ result) & 0x10) != 0;
            flags.execute(frame, false, carry, adjust, result);
            if (profile.profile(a == dst)) {
                out1.execute(frame, src);
            } else {
                out2.execute(frame, dst);
            }
        }
    }

    public abstract static class LLVMAMD64CmpXchgwNode extends LLVMAMD64CmpXchgNode {
        public LLVMAMD64CmpXchgwNode(LLVMAMD64UpdateCPAZSOFlagsNode flags, LLVMAMD64WriteValueNode dst1, LLVMAMD64WriteValueNode dst2) {
            super(flags, dst1, dst2);
        }

        @Specialization
        protected void doOp(VirtualFrame frame, short a, short src, short dst) {
            int result = a - dst;
            boolean carry = Short.toUnsignedInt(a) < Short.toUnsignedInt(dst);
            boolean adjust = (((a ^ dst) ^ result) & 0x10) != 0;
            flags.execute(frame, false, carry, adjust, result);
            if (profile.profile(a == dst)) {
                out1.execute(frame, src);
            } else {
                out2.execute(frame, dst);
            }
        }
    }

    public abstract static class LLVMAMD64CmpXchglNode extends LLVMAMD64CmpXchgNode {
        public LLVMAMD64CmpXchglNode(LLVMAMD64UpdateCPAZSOFlagsNode flags, LLVMAMD64WriteValueNode dst1, LLVMAMD64WriteValueNode dst2) {
            super(flags, dst1, dst2);
        }

        @Specialization
        protected void doOp(VirtualFrame frame, int a, int src, int dst) {
            int result = a - dst;
            boolean carry = Integer.compareUnsigned(a, dst) < 0;
            boolean adjust = (((a ^ dst) ^ result) & 0x10) != 0;
            flags.execute(frame, false, carry, adjust, result);
            if (profile.profile(a == dst)) {
                out1.execute(frame, src);
            } else {
                out2.execute(frame, dst);
            }
        }
    }

    public abstract static class LLVMAMD64CmpXchgqNode extends LLVMAMD64CmpXchgNode {
        public LLVMAMD64CmpXchgqNode(LLVMAMD64UpdateCPAZSOFlagsNode flags, LLVMAMD64WriteValueNode dst1, LLVMAMD64WriteValueNode dst2) {
            super(flags, dst1, dst2);
        }

        @Specialization
        protected void doOp(VirtualFrame frame, long a, long src, long dst) {
            long result = a - dst;
            boolean carry = Long.compareUnsigned(a, dst) < 0;
            boolean adjust = (((a ^ dst) ^ result) & 0x10) != 0;
            flags.execute(frame, false, carry, adjust, result);
            if (profile.profile(a == dst)) {
                out1.execute(frame, src);
            } else {
                out2.execute(frame, dst);
            }
        }

        @Specialization
        protected void doOp(VirtualFrame frame, LLVMNativePointer a, LLVMNativePointer src, LLVMNativePointer dst) {
            long result = a.asNative() - dst.asNative();
            boolean carry = Long.compareUnsigned(a.asNative(), dst.asNative()) < 0;
            boolean adjust = (((a.asNative() ^ dst.asNative()) ^ result) & 0x10) != 0;
            flags.execute(frame, false, carry, adjust, result);
            if (profile.profile(a.isSame(dst))) {
                out1.execute(frame, src);
            } else {
                out2.execute(frame, dst);
            }
        }

        @Specialization
        protected void doOp(VirtualFrame frame, LLVMManagedPointer pointerA, LLVMNativePointer pointerSrc, LLVMManagedPointer pointerDst,
                        @Cached("createToComparable()") ToComparableValue toComparableA,
                        @Cached("createToComparable()") ToComparableValue toComparableB) {
            long a = toComparableA.executeWithTarget(pointerA);
            long dst = toComparableB.executeWithTarget(pointerDst);

            long result = a - dst;
            boolean carry = Long.compareUnsigned(a, dst) < 0;
            boolean adjust = (((a ^ dst) ^ result) & 0x10) != 0;
            flags.execute(frame, false, carry, adjust, result);
            if (profile.profile(pointerA.isSame(pointerDst))) {
                out1.execute(frame, pointerSrc);
            } else {
                out2.execute(frame, pointerDst);
            }
        }

        @TruffleBoundary
        protected static ToComparableValue createToComparable() {
            return ToComparableValueNodeGen.create();
        }
    }
}
