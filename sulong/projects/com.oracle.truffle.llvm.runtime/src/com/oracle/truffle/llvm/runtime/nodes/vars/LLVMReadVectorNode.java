/*
 * Copyright (c) 2016, 2019, Oracle and/or its affiliates.
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

import com.oracle.truffle.api.dsl.NodeField;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.FrameUtil;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.vector.LLVMDoubleVector;
import com.oracle.truffle.llvm.runtime.vector.LLVMFloatVector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI16Vector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI1Vector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI32Vector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI64Vector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI8Vector;
import com.oracle.truffle.llvm.runtime.vector.LLVMPointerVector;

public class LLVMReadVectorNode {

    @NodeField(name = "slot", type = FrameSlot.class)
    public abstract static class LLVMI1VectorReadNode extends LLVMExpressionNode {

        protected abstract FrameSlot getSlot();

        @Specialization
        protected LLVMI1Vector readI1Vector(VirtualFrame frame) {
            return (LLVMI1Vector) FrameUtil.getObjectSafe(frame, getSlot());
        }
    }

    @NodeField(name = "slot", type = FrameSlot.class)
    public abstract static class LLVMI8VectorReadNode extends LLVMExpressionNode {

        protected abstract FrameSlot getSlot();

        @Specialization
        protected LLVMI8Vector readI8Vector(VirtualFrame frame) {
            return (LLVMI8Vector) FrameUtil.getObjectSafe(frame, getSlot());
        }
    }

    @NodeField(name = "slot", type = FrameSlot.class)
    public abstract static class LLVMI16VectorReadNode extends LLVMExpressionNode {

        protected abstract FrameSlot getSlot();

        @Specialization
        protected LLVMI16Vector readI16Vector(VirtualFrame frame) {
            return (LLVMI16Vector) FrameUtil.getObjectSafe(frame, getSlot());
        }
    }

    @NodeField(name = "slot", type = FrameSlot.class)
    public abstract static class LLVMI32VectorReadNode extends LLVMExpressionNode {

        protected abstract FrameSlot getSlot();

        @Specialization
        protected LLVMI32Vector readI32Vector(VirtualFrame frame) {
            return (LLVMI32Vector) FrameUtil.getObjectSafe(frame, getSlot());
        }
    }

    @NodeField(name = "slot", type = FrameSlot.class)
    public abstract static class LLVMI64VectorReadNode extends LLVMExpressionNode {

        protected abstract FrameSlot getSlot();

        @Specialization
        protected Object readVector(VirtualFrame frame) {
            Object result = FrameUtil.getObjectSafe(frame, getSlot());
            assert result instanceof LLVMI64Vector || result instanceof LLVMPointerVector;
            return result;
        }
    }

    @NodeField(name = "slot", type = FrameSlot.class)
    public abstract static class LLVMFloatVectorReadNode extends LLVMExpressionNode {

        protected abstract FrameSlot getSlot();

        @Specialization
        protected LLVMFloatVector readFloatVector(VirtualFrame frame) {
            return (LLVMFloatVector) FrameUtil.getObjectSafe(frame, getSlot());
        }
    }

    @NodeField(name = "slot", type = FrameSlot.class)
    public abstract static class LLVMDoubleVectorReadNode extends LLVMExpressionNode {

        protected abstract FrameSlot getSlot();

        @Specialization
        protected LLVMDoubleVector readDoubleVector(VirtualFrame frame) {
            return (LLVMDoubleVector) FrameUtil.getObjectSafe(frame, getSlot());
        }
    }
}
