/*
 * Copyright (c) 2016, 2018, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.nodes.vars;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.NodeField;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.FrameSlotKind;
import com.oracle.truffle.api.frame.FrameSlotTypeException;
import com.oracle.truffle.api.frame.FrameUtil;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.llvm.nodes.vars.LLVMReadNodeFactory.AttachInteropTypeNodeGen;
import com.oracle.truffle.llvm.nodes.vars.LLVMReadNodeFactory.ForeignAttachInteropTypeNodeGen;
import com.oracle.truffle.llvm.runtime.interop.LLVMTypedForeignObject;
import com.oracle.truffle.llvm.runtime.interop.access.LLVMInteropType;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMNode;
import com.oracle.truffle.llvm.runtime.pointer.LLVMManagedPointer;
import com.oracle.truffle.llvm.runtime.types.Type;

@NodeField(name = "slot", type = FrameSlot.class)
public abstract class LLVMReadNode extends LLVMExpressionNode {

    protected abstract FrameSlot getSlot();

    public abstract static class LLVMI1ReadNode extends LLVMReadNode {
        @Specialization
        protected boolean readI1(VirtualFrame frame) {
            return FrameUtil.getBooleanSafe(frame, getSlot());
        }
    }

    public abstract static class LLVMI8ReadNode extends LLVMReadNode {
        @Specialization
        protected byte readI8(VirtualFrame frame) {
            return FrameUtil.getByteSafe(frame, getSlot());
        }
    }

    public abstract static class LLVMI16ReadNode extends LLVMReadNode {
        @Specialization
        protected short readI16(VirtualFrame frame) {
            return (short) FrameUtil.getIntSafe(frame, getSlot());
        }
    }

    public abstract static class LLVMI32ReadNode extends LLVMReadNode {
        @Specialization
        protected int readI32(VirtualFrame frame) {
            return FrameUtil.getIntSafe(frame, getSlot());
        }
    }

    public abstract static class LLVMI64ReadNode extends LLVMReadNode {
        @Specialization(rewriteOn = FrameSlotTypeException.class)
        protected long readI64(VirtualFrame frame) throws FrameSlotTypeException {
            return frame.getLong(getSlot());
        }

        @Specialization(rewriteOn = FrameSlotTypeException.class)
        protected Object readObject(VirtualFrame frame) throws FrameSlotTypeException {
            return frame.getObject(getSlot());
        }

        @Specialization
        protected Object readGeneric(VirtualFrame frame) {
            if (frame.getFrameDescriptor().getFrameSlotKind(getSlot()) == FrameSlotKind.Long) {
                return FrameUtil.getLongSafe(frame, getSlot());
            } else {
                return FrameUtil.getObjectSafe(frame, getSlot());
            }
        }
    }

    public abstract static class LLVMIReadVarBitNode extends LLVMReadNode {
        @Specialization
        protected Object readVarBit(VirtualFrame frame) {
            return FrameUtil.getObjectSafe(frame, getSlot());
        }
    }

    public abstract static class LLVMFloatReadNode extends LLVMReadNode {
        @Specialization
        protected float readFloat(VirtualFrame frame) {
            return FrameUtil.getFloatSafe(frame, getSlot());
        }
    }

    public abstract static class LLVMDoubleReadNode extends LLVMReadNode {
        @Specialization
        protected double readDouble(VirtualFrame frame) {
            return FrameUtil.getDoubleSafe(frame, getSlot());
        }
    }

    public abstract static class LLVM80BitFloatReadNode extends LLVMReadNode {
        @Specialization
        protected Object read80BitFloat(VirtualFrame frame) {
            return FrameUtil.getObjectSafe(frame, getSlot());
        }
    }

    public abstract static class LLVMAddressReadNode extends LLVMReadNode {

        @Child private AttachInteropTypeNode attach = AttachInteropTypeNodeGen.create();

        @Specialization
        protected Object readObject(VirtualFrame frame) {
            return attachType(FrameUtil.getObjectSafe(frame, getSlot()));
        }

        private Object attachType(Object obj) {
            Type type = (Type) getSlot().getInfo();
            return attach.execute(obj, type != null ? type.getInteropType() : null);
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

        public abstract TruffleObject execute(TruffleObject object, LLVMInteropType.Structured type);

        public static ForeignAttachInteropTypeNode create() {
            return ForeignAttachInteropTypeNodeGen.create();
        }

        @Specialization(guards = "object.getType() == null")
        protected TruffleObject doForeign(LLVMTypedForeignObject object, LLVMInteropType.Structured type) {
            return LLVMTypedForeignObject.create(object.getForeign(), type);
        }

        @Fallback
        protected TruffleObject doOther(TruffleObject object, @SuppressWarnings("unused") LLVMInteropType.Structured type) {
            return object;
        }
    }

    public abstract static class LLVMDebugReadNode extends LLVMReadNode {
        @Specialization
        protected Object readObject(VirtualFrame frame) {
            return frame.getValue(getSlot());
        }
    }
}
