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
package com.oracle.truffle.llvm.runtime.nodes.asm;

import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.llvm.runtime.nodes.asm.support.LLVMAMD64Flags;
import com.oracle.truffle.llvm.runtime.nodes.asm.support.LLVMAMD64WriteBooleanNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMStatementNode;

public abstract class LLVMAMD64StoreFlags extends LLVMStatementNode {
    @Child protected LLVMAMD64WriteBooleanNode cf;
    @Child protected LLVMAMD64WriteBooleanNode pf;
    @Child protected LLVMAMD64WriteBooleanNode af;
    @Child protected LLVMAMD64WriteBooleanNode zf;
    @Child protected LLVMAMD64WriteBooleanNode sf;

    public LLVMAMD64StoreFlags(LLVMAMD64WriteBooleanNode cf, LLVMAMD64WriteBooleanNode pf, LLVMAMD64WriteBooleanNode af, LLVMAMD64WriteBooleanNode zf, LLVMAMD64WriteBooleanNode sf) {
        this.cf = cf;
        this.pf = pf;
        this.af = af;
        this.zf = zf;
        this.sf = sf;
    }

    protected static boolean set(long value, long flag) {
        return (value & (1 << flag)) != 0;
    }

    @NodeChild(value = "flags", type = LLVMExpressionNode.class)
    public abstract static class LLVMAMD64SahfNode extends LLVMAMD64StoreFlags {
        public LLVMAMD64SahfNode(LLVMAMD64WriteBooleanNode cf, LLVMAMD64WriteBooleanNode pf, LLVMAMD64WriteBooleanNode af, LLVMAMD64WriteBooleanNode zf, LLVMAMD64WriteBooleanNode sf) {
            super(cf, pf, af, zf, sf);
        }

        @Specialization
        protected void doObject(VirtualFrame frame, byte flags) {
            cf.execute(frame, set(flags, LLVMAMD64Flags.CF));
            pf.execute(frame, set(flags, LLVMAMD64Flags.PF));
            af.execute(frame, set(flags, LLVMAMD64Flags.AF));
            zf.execute(frame, set(flags, LLVMAMD64Flags.ZF));
            sf.execute(frame, set(flags, LLVMAMD64Flags.SF));
        }
    }

    @NodeChild(value = "flags", type = LLVMExpressionNode.class)
    public abstract static class LLVMAMD64WriteFlagswNode extends LLVMAMD64StoreFlags {
        @Child protected LLVMAMD64WriteBooleanNode of;

        public LLVMAMD64WriteFlagswNode(LLVMAMD64WriteBooleanNode cf, LLVMAMD64WriteBooleanNode pf, LLVMAMD64WriteBooleanNode af, LLVMAMD64WriteBooleanNode zf, LLVMAMD64WriteBooleanNode sf,
                        LLVMAMD64WriteBooleanNode of) {
            super(cf, pf, af, zf, sf);
            this.of = of;
        }

        @Specialization
        protected void doObject(VirtualFrame frame, short flags) {
            cf.execute(frame, set(flags, LLVMAMD64Flags.CF));
            pf.execute(frame, set(flags, LLVMAMD64Flags.PF));
            af.execute(frame, set(flags, LLVMAMD64Flags.AF));
            zf.execute(frame, set(flags, LLVMAMD64Flags.ZF));
            sf.execute(frame, set(flags, LLVMAMD64Flags.SF));
            of.execute(frame, set(flags, LLVMAMD64Flags.OF));
        }
    }
}
