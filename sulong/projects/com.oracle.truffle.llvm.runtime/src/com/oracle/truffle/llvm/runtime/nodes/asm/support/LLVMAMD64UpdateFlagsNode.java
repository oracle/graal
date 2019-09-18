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
package com.oracle.truffle.llvm.runtime.nodes.asm.support;

import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMNode;

public class LLVMAMD64UpdateFlagsNode extends LLVMNode {
    public static class LLVMAMD64UpdatePZSFlagsNode extends LLVMAMD64UpdateFlagsNode {
        private final FrameSlot pf;
        private final FrameSlot zf;
        private final FrameSlot sf;

        public LLVMAMD64UpdatePZSFlagsNode(FrameSlot pf, FrameSlot zf, FrameSlot sf) {
            this.pf = pf;
            this.zf = zf;
            this.sf = sf;
        }

        public void execute(VirtualFrame frame, byte value) {
            frame.setBoolean(sf, value < 0);
            frame.setBoolean(zf, value == 0);
            frame.setBoolean(pf, getParity(value));
        }

        public void execute(VirtualFrame frame, short value) {
            frame.setBoolean(sf, value < 0);
            frame.setBoolean(zf, value == 0);
            frame.setBoolean(pf, getParity(value));
        }

        public void execute(VirtualFrame frame, int value) {
            frame.setBoolean(sf, value < 0);
            frame.setBoolean(zf, value == 0);
            frame.setBoolean(pf, getParity(value));
        }

        public void execute(VirtualFrame frame, long value) {
            frame.setBoolean(sf, value < 0);
            frame.setBoolean(zf, value == 0);
            frame.setBoolean(pf, getParity(value));
        }
    }

    public static class LLVMAMD64UpdatePZSOFlagsNode extends LLVMAMD64UpdateFlagsNode {
        private final FrameSlot pf;
        private final FrameSlot zf;
        private final FrameSlot sf;
        private final FrameSlot of;

        public LLVMAMD64UpdatePZSOFlagsNode(FrameSlot pf, FrameSlot zf, FrameSlot sf, FrameSlot of) {
            this.pf = pf;
            this.zf = zf;
            this.sf = sf;
            this.of = of;
        }

        public void execute(VirtualFrame frame, boolean overflow, byte value) {
            frame.setBoolean(of, overflow);
            frame.setBoolean(sf, value < 0);
            frame.setBoolean(zf, value == 0);
            frame.setBoolean(pf, getParity(value));
        }

        public void execute(VirtualFrame frame, boolean overflow, short value) {
            frame.setBoolean(of, overflow);
            frame.setBoolean(sf, value < 0);
            frame.setBoolean(zf, value == 0);
            frame.setBoolean(pf, getParity(value));
        }

        public void execute(VirtualFrame frame, boolean overflow, int value) {
            frame.setBoolean(of, overflow);
            frame.setBoolean(sf, value < 0);
            frame.setBoolean(zf, value == 0);
            frame.setBoolean(pf, getParity(value));
        }

        public void execute(VirtualFrame frame, boolean overflow, long value) {
            frame.setBoolean(of, overflow);
            frame.setBoolean(sf, value < 0);
            frame.setBoolean(zf, value == 0);
            frame.setBoolean(pf, getParity(value));
        }
    }

    public static class LLVMAMD64UpdateCPZSOFlagsNode extends LLVMAMD64UpdateFlagsNode {
        private final FrameSlot cf;
        private final FrameSlot pf;
        private final FrameSlot zf;
        private final FrameSlot sf;
        private final FrameSlot of;

        public LLVMAMD64UpdateCPZSOFlagsNode(FrameSlot cf, FrameSlot pf, FrameSlot zf, FrameSlot sf, FrameSlot of) {
            this.cf = cf;
            this.pf = pf;
            this.zf = zf;
            this.sf = sf;
            this.of = of;
        }

        public void execute(VirtualFrame frame, boolean overflow, boolean carry, byte value) {
            frame.setBoolean(of, overflow);
            frame.setBoolean(cf, carry);
            frame.setBoolean(sf, value < 0);
            frame.setBoolean(zf, value == 0);
            frame.setBoolean(pf, getParity(value));
        }

        public void execute(VirtualFrame frame, boolean overflow, boolean carry, short value) {
            frame.setBoolean(of, overflow);
            frame.setBoolean(cf, carry);
            frame.setBoolean(sf, value < 0);
            frame.setBoolean(zf, value == 0);
            frame.setBoolean(pf, getParity(value));
        }

        public void execute(VirtualFrame frame, boolean overflow, boolean carry, int value) {
            frame.setBoolean(of, overflow);
            frame.setBoolean(cf, carry);
            frame.setBoolean(sf, value < 0);
            frame.setBoolean(zf, value == 0);
            frame.setBoolean(pf, getParity(value));
        }

        public void execute(VirtualFrame frame, boolean overflow, boolean carry, long value) {
            frame.setBoolean(of, overflow);
            frame.setBoolean(cf, carry);
            frame.setBoolean(sf, value < 0);
            frame.setBoolean(zf, value == 0);
            frame.setBoolean(pf, getParity(value));
        }
    }

    public static class LLVMAMD64UpdateCPAZSOFlagsNode extends LLVMAMD64UpdateFlagsNode {
        private final FrameSlot cf;
        private final FrameSlot pf;
        private final FrameSlot af;
        private final FrameSlot zf;
        private final FrameSlot sf;
        private final FrameSlot of;

        public LLVMAMD64UpdateCPAZSOFlagsNode(FrameSlot cf, FrameSlot pf, FrameSlot af, FrameSlot zf, FrameSlot sf, FrameSlot of) {
            this.cf = cf;
            this.pf = pf;
            this.af = af;
            this.zf = zf;
            this.sf = sf;
            this.of = of;
        }

        public void execute(VirtualFrame frame, boolean overflow, boolean carry, boolean adjust, byte value) {
            frame.setBoolean(of, overflow);
            frame.setBoolean(cf, carry);
            frame.setBoolean(af, adjust);
            frame.setBoolean(sf, value < 0);
            frame.setBoolean(zf, value == 0);
            frame.setBoolean(pf, getParity(value));
        }

        public void execute(VirtualFrame frame, boolean overflow, boolean carry, boolean adjust, short value) {
            frame.setBoolean(of, overflow);
            frame.setBoolean(cf, carry);
            frame.setBoolean(af, adjust);
            frame.setBoolean(sf, value < 0);
            frame.setBoolean(zf, value == 0);
            frame.setBoolean(pf, getParity(value));
        }

        public void execute(VirtualFrame frame, boolean overflow, boolean carry, boolean adjust, int value) {
            frame.setBoolean(of, overflow);
            frame.setBoolean(cf, carry);
            frame.setBoolean(af, adjust);
            frame.setBoolean(sf, value < 0);
            frame.setBoolean(zf, value == 0);
            frame.setBoolean(pf, getParity(value));
        }

        public void execute(VirtualFrame frame, boolean overflow, boolean carry, boolean adjust, long value) {
            frame.setBoolean(of, overflow);
            frame.setBoolean(cf, carry);
            frame.setBoolean(af, adjust);
            frame.setBoolean(sf, value < 0);
            frame.setBoolean(zf, value == 0);
            frame.setBoolean(pf, getParity(value));
        }
    }

    public static boolean getParity(byte value) {
        return getParity((int) value);
    }

    public static boolean getParity(short value) {
        return getParity((int) value);
    }

    public static boolean getParity(int value) {
        return (Integer.bitCount(value & 0xFF) & 1) == 0;
    }

    public static boolean getParity(long value) {
        return (Long.bitCount(value & 0xFFL) & 1) == 0;
    }
}
