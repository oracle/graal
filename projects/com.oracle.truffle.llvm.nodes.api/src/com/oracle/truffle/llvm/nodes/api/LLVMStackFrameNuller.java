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
package com.oracle.truffle.llvm.nodes.api;

import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.llvm.runtime.LLVMAddress;

public abstract class LLVMStackFrameNuller extends LLVMNode {

    private final FrameSlot frameSlot;

    public LLVMStackFrameNuller(FrameSlot slot) {
        this.frameSlot = slot;
    }

    public void nullifySlot(VirtualFrame frame) {
        nullify(frame, frameSlot);
    }

    public abstract void nullify(VirtualFrame frame, FrameSlot slot);

    public static final class LLVMBooleanNuller extends LLVMStackFrameNuller {

        public LLVMBooleanNuller(FrameSlot slot) {
            super(slot);
        }

        @Override
        public void nullify(VirtualFrame frame, FrameSlot slot) {
            frame.setBoolean(slot, false);
        }

    }

    public static final class LLVMByteNuller extends LLVMStackFrameNuller {

        public LLVMByteNuller(FrameSlot slot) {
            super(slot);
        }

        @Override
        public void nullify(VirtualFrame frame, FrameSlot slot) {
            frame.setByte(slot, (byte) 0);
        }

    }

    public static final class LLVMIntNuller extends LLVMStackFrameNuller {

        public LLVMIntNuller(FrameSlot slot) {
            super(slot);
        }

        @Override
        public void nullify(VirtualFrame frame, FrameSlot slot) {
            frame.setInt(slot, 0);
        }

    }

    public static final class LLVMLongNuller extends LLVMStackFrameNuller {

        public LLVMLongNuller(FrameSlot slot) {
            super(slot);
        }

        @Override
        public void nullify(VirtualFrame frame, FrameSlot slot) {
            frame.setLong(slot, 0);
        }

    }

    public static final class LLVMFloatNuller extends LLVMStackFrameNuller {

        public LLVMFloatNuller(FrameSlot slot) {
            super(slot);
        }

        @Override
        public void nullify(VirtualFrame frame, FrameSlot slot) {
            frame.setFloat(slot, 0);
        }

    }

    public static final class LLVMDoubleNuller extends LLVMStackFrameNuller {

        public LLVMDoubleNuller(FrameSlot slot) {
            super(slot);
        }

        @Override
        public void nullify(VirtualFrame frame, FrameSlot slot) {
            frame.setDouble(slot, 0);
        }

    }

    public static final class LLVMAddressNuller extends LLVMStackFrameNuller {

        public LLVMAddressNuller(FrameSlot slot) {
            super(slot);
        }

        @Override
        public void nullify(VirtualFrame frame, FrameSlot slot) {
            frame.setObject(slot, LLVMAddress.fromLong(0));
        }

    }

}
