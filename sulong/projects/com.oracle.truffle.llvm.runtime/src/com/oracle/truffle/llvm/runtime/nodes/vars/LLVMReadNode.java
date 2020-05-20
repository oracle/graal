/*
 * Copyright (c) 2016, 2020, Oracle and/or its affiliates.
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

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.FrameSlotTypeException;
import com.oracle.truffle.api.frame.FrameUtil;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.llvm.runtime.interop.LLVMTypedForeignObject;
import com.oracle.truffle.llvm.runtime.interop.access.LLVMInteropType;
import com.oracle.truffle.llvm.runtime.library.internal.LLVMAsForeignLibrary;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMNode;
import com.oracle.truffle.llvm.runtime.nodes.vars.LLVMReadNodeFactory.ForeignAttachInteropTypeNodeGen;
import com.oracle.truffle.llvm.runtime.pointer.LLVMManagedPointer;
import com.oracle.truffle.llvm.spi.NativeTypeLibrary;

public abstract class LLVMReadNode extends LLVMExpressionNode {

    protected final FrameSlot slot;

    public LLVMReadNode(FrameSlot slot) {
        assert slot != null;
        this.slot = slot;
    }

    @Override
    public String toString() {
        return getShortString("slot");
    }

    public abstract static class LLVMI1ReadNode extends LLVMReadNode {
        protected LLVMI1ReadNode(FrameSlot slot) {
            super(slot);
        }

        @Specialization
        protected boolean readI1(VirtualFrame frame) {
            return FrameUtil.getBooleanSafe(frame, slot);
        }
    }

    public abstract static class LLVMI8ReadNode extends LLVMReadNode {
        protected LLVMI8ReadNode(FrameSlot slot) {
            super(slot);
        }

        @Specialization
        protected byte readI8(VirtualFrame frame) {
            return FrameUtil.getByteSafe(frame, slot);
        }
    }

    public abstract static class LLVMI16ReadNode extends LLVMReadNode {
        protected LLVMI16ReadNode(FrameSlot slot) {
            super(slot);
        }

        @Specialization
        protected short readI16(VirtualFrame frame) {
            return (short) FrameUtil.getIntSafe(frame, slot);
        }
    }

    public abstract static class LLVMI32ReadNode extends LLVMReadNode {
        protected LLVMI32ReadNode(FrameSlot slot) {
            super(slot);
        }

        @Specialization
        protected int readI32(VirtualFrame frame) {
            return FrameUtil.getIntSafe(frame, slot);
        }
    }

    public abstract static class LLVMI64ReadNode extends LLVMReadNode {
        protected LLVMI64ReadNode(FrameSlot slot) {
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
                return FrameUtil.getLongSafe(frame, slot);
            } else {
                return FrameUtil.getObjectSafe(frame, slot);
            }
        }
    }

    public abstract static class LLVMIReadVarBitNode extends LLVMReadNode {
        protected LLVMIReadVarBitNode(FrameSlot slot) {
            super(slot);
        }

        @Specialization
        protected Object readVarBit(VirtualFrame frame) {
            return FrameUtil.getObjectSafe(frame, slot);
        }
    }

    public abstract static class LLVMFloatReadNode extends LLVMReadNode {
        protected LLVMFloatReadNode(FrameSlot slot) {
            super(slot);
        }

        @Specialization
        protected float readFloat(VirtualFrame frame) {
            return FrameUtil.getFloatSafe(frame, slot);
        }
    }

    public abstract static class LLVMDoubleReadNode extends LLVMReadNode {
        protected LLVMDoubleReadNode(FrameSlot slot) {
            super(slot);
        }

        @Specialization
        protected double readDouble(VirtualFrame frame) {
            return FrameUtil.getDoubleSafe(frame, slot);
        }
    }

    public abstract static class LLVM80BitFloatReadNode extends LLVMReadNode {
        protected LLVM80BitFloatReadNode(FrameSlot slot) {
            super(slot);
        }

        @Specialization
        protected Object read80BitFloat(VirtualFrame frame) {
            return FrameUtil.getObjectSafe(frame, slot);
        }
    }

    public abstract static class LLVMAddressReadNode extends LLVMReadNode {
        protected LLVMAddressReadNode(FrameSlot slot) {
            super(slot);
        }

        @Specialization
        protected Object readObject(VirtualFrame frame) {
            return FrameUtil.getObjectSafe(frame, slot);
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

    public abstract static class LLVMDebugReadNode extends LLVMReadNode {
        protected LLVMDebugReadNode(FrameSlot slot) {
            super(slot);
        }

        @Specialization
        protected Object readObject(VirtualFrame frame) {
            return frame.getValue(slot);
        }
    }
}
