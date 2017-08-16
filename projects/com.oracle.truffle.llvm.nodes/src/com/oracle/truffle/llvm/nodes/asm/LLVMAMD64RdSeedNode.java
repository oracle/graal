/*
 * Copyright (c) 2017, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.nodes.asm;

import java.security.SecureRandom;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.llvm.nodes.asm.support.LLVMAMD64WriteBooleanNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;

public abstract class LLVMAMD64RdSeedNode extends LLVMExpressionNode {
    private final SecureRandom rng = new SecureRandom();

    @Child protected LLVMAMD64WriteBooleanNode cf;

    // TODO: OF, SF, ZF, AF, PF = 0

    @TruffleBoundary
    protected short getSeedI16() {
        byte[] seed = rng.generateSeed(2);
        return (short) (Byte.toUnsignedInt(seed[0]) << 8 | Byte.toUnsignedInt(seed[1]));
    }

    @TruffleBoundary
    protected int getSeedI32() {
        byte[] seed = rng.generateSeed(4);
        return Byte.toUnsignedInt(seed[0]) << 24 | Byte.toUnsignedInt(seed[1]) << 16 | Byte.toUnsignedInt(seed[2]) << 8 | Byte.toUnsignedInt(seed[3]);
    }

    @TruffleBoundary
    protected long getSeedI64() {
        byte[] seed = rng.generateSeed(8);
        return Byte.toUnsignedInt(seed[0]) << 56 | Byte.toUnsignedInt(seed[1]) << 48 | Byte.toUnsignedInt(seed[2]) << 40 | Byte.toUnsignedInt(seed[3]) << 32 | Byte.toUnsignedInt(seed[4]) << 24 |
                        Byte.toUnsignedInt(seed[5]) << 16 | Byte.toUnsignedInt(seed[6]) << 8 | Byte.toUnsignedInt(seed[7]);
    }

    public LLVMAMD64RdSeedNode(LLVMAMD64WriteBooleanNode cf) {
        this.cf = cf;
    }

    public abstract static class LLVMAMD64RdSeedwNode extends LLVMAMD64RdSeedNode {
        public LLVMAMD64RdSeedwNode(LLVMAMD64WriteBooleanNode cf) {
            super(cf);
        }

        @Override
        @Specialization
        public short executeI16(VirtualFrame frame) {
            cf.execute(frame, true);
            return getSeedI16();
        }
    }

    public abstract static class LLVMAMD64RdSeedlNode extends LLVMAMD64RdSeedNode {
        public LLVMAMD64RdSeedlNode(LLVMAMD64WriteBooleanNode cf) {
            super(cf);
        }

        @Override
        @Specialization
        public int executeI32(VirtualFrame frame) {
            cf.execute(frame, true);
            return getSeedI32();
        }
    }

    public abstract static class LLVMAMD64RdSeedqNode extends LLVMAMD64RdSeedNode {
        public LLVMAMD64RdSeedqNode(LLVMAMD64WriteBooleanNode cf) {
            super(cf);
        }

        @Override
        @Specialization
        public long executeI64(VirtualFrame frame) {
            cf.execute(frame, true);
            return getSeedI64();
        }
    }
}
