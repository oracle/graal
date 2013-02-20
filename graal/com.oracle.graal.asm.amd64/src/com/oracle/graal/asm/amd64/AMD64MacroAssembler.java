/*
 * Copyright (c) 2009, 2011, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.oracle.graal.asm.amd64;

import static com.oracle.graal.asm.amd64.AMD64AsmOptions.*;

import com.oracle.graal.amd64.*;
import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;

/**
 * This class implements commonly used X86 code patterns.
 */
public class AMD64MacroAssembler extends AMD64Assembler {

    public AMD64MacroAssembler(TargetDescription target, RegisterConfig registerConfig) {
        super(target, registerConfig);
    }

    public final void xorptr(Register dst, Register src) {
        xorq(dst, src);
    }

    public final void decrementq(Register reg, int value) {
        if (value == Integer.MIN_VALUE) {
            subq(reg, value);
            return;
        }
        if (value < 0) {
            incrementq(reg, -value);
            return;
        }
        if (value == 0) {
            return;
        }
        if (value == 1 && UseIncDec) {
            decq(reg);
        } else {
            subq(reg, value);
        }
    }

    public void incrementq(Register reg, int value) {
        if (value == Integer.MIN_VALUE) {
            addq(reg, value);
            return;
        }
        if (value < 0) {
            decrementq(reg, -value);
            return;
        }
        if (value == 0) {
            return;
        }
        if (value == 1 && UseIncDec) {
            incq(reg);
        } else {
            addq(reg, value);
        }
    }

    public final void movptr(Address dst, int src) {
        movslq(dst, src);
    }

    public final void cmpptr(Register src1, Register src2) {
        cmpq(src1, src2);
    }

    public final void cmpptr(Register src1, Address src2) {
        cmpq(src1, src2);
    }

    public final void decrementl(Register reg, int value) {
        if (value == Integer.MIN_VALUE) {
            subl(reg, value);
            return;
        }
        if (value < 0) {
            incrementl(reg, -value);
            return;
        }
        if (value == 0) {
            return;
        }
        if (value == 1 && UseIncDec) {
            decl(reg);
        } else {
            subl(reg, value);
        }
    }

    public final void decrementl(Address dst, int value) {
        if (value == Integer.MIN_VALUE) {
            subl(dst, value);
            return;
        }
        if (value < 0) {
            incrementl(dst, -value);
            return;
        }
        if (value == 0) {
            return;
        }
        if (value == 1 && UseIncDec) {
            decl(dst);
        } else {
            subl(dst, value);
        }
    }

    public final void incrementl(Register reg, int value) {
        if (value == Integer.MIN_VALUE) {
            addl(reg, value);
            return;
        }
        if (value < 0) {
            decrementl(reg, -value);
            return;
        }
        if (value == 0) {
            return;
        }
        if (value == 1 && UseIncDec) {
            incl(reg);
        } else {
            addl(reg, value);
        }
    }

    public final void incrementl(Address dst, int value) {
        if (value == Integer.MIN_VALUE) {
            addl(dst, value);
            return;
        }
        if (value < 0) {
            decrementl(dst, -value);
            return;
        }
        if (value == 0) {
            return;
        }
        if (value == 1 && UseIncDec) {
            incl(dst);
        } else {
            addl(dst, value);
        }
    }

    public final void signExtendByte(Register reg) {
        if (reg.isByte()) {
            movsxb(reg, reg);
        } else {
            shll(reg, 24);
            sarl(reg, 24);
        }
    }

    public final void signExtendShort(Register reg) {
        movsxw(reg, reg);
    }

    public final void movflt(Register dst, Register src) {
        assert dst.isFpu() && src.isFpu();
        if (UseXmmRegToRegMoveAll) {
            movaps(dst, src);
        } else {
            movss(dst, src);
        }
    }

    public final void movflt(Register dst, Address src) {
        assert dst.isFpu();
        movss(dst, src);
    }

    public final void movflt(Address dst, Register src) {
        assert src.isFpu();
        movss(dst, src);
    }

    public final void movdbl(Register dst, Register src) {
        assert dst.isFpu() && src.isFpu();
        if (UseXmmRegToRegMoveAll) {
            movapd(dst, src);
        } else {
            movsd(dst, src);
        }
    }

    public final void movdbl(Register dst, Address src) {
        assert dst.isFpu();
        if (UseXmmLoadAndClearUpper) {
            movsd(dst, src);
        } else {
            movlpd(dst, src);
        }
    }

    /**
     * Non-atomic write of a 64-bit constant to memory. Do not use if the address might be a
     * volatile field!
     */
    public final void movlong(Address dst, long src) {
        Address high = new Address(dst.getKind(), dst.getBase(), dst.getIndex(), dst.getScale(), dst.getDisplacement() + 4);
        movl(dst, (int) (src & 0xFFFFFFFF));
        movl(high, (int) (src >> 32));
    }

    public final void flog(Register dest, Register value, boolean base10) {
        assert dest.isFpu() && value.isFpu();

        Address tmp = new Address(Kind.Double, AMD64.RSP);
        if (base10) {
            fldlg2();
        } else {
            fldln2();
        }
        subq(AMD64.rsp, 8);
        movsd(tmp, value);
        fld(tmp);
        fyl2x();
        trigEpilogue(dest, tmp);
    }

    public final void fsin(Register dest, Register value) {
        Address tmp = trigPrologue(value);
        fsin();
        trigEpilogue(dest, tmp);
    }

    public final void fcos(Register dest, Register value) {
        Address tmp = trigPrologue(value);
        fcos();
        trigEpilogue(dest, tmp);
    }

    public final void ftan(Register dest, Register value) {
        Address tmp = trigPrologue(value);
        fptan();
        fstp(0); // ftan pushes 1.0 in addition to the actual result, pop
        trigEpilogue(dest, tmp);
    }

    private Address trigPrologue(Register value) {
        assert value.isFpu();
        Address tmp = new Address(Kind.Double, AMD64.RSP);
        subq(AMD64.rsp, 8);
        movsd(tmp, value);
        fld(tmp);
        return tmp;
    }

    private void trigEpilogue(Register dest, Address tmp) {
        assert dest.isFpu();
        fstp(tmp);
        movsd(dest, tmp);
        addq(AMD64.rsp, 8);
    }

    /**
     * Emit code to save a given set of callee save registers in the {@linkplain CalleeSaveLayout
     * CSA} within the frame.
     * 
     * @param csl the description of the CSA
     * @param frameToCSA offset from the frame pointer to the CSA
     */
    public final void save(CalleeSaveLayout csl, int frameToCSA) {
        RegisterValue frame = frameRegister.asValue();
        for (Register r : csl.registers) {
            int offset = csl.offsetOf(r);
            movq(new Address(target.wordKind, frame, frameToCSA + offset), r);
        }
    }

    public final void restore(CalleeSaveLayout csl, int frameToCSA) {
        RegisterValue frame = frameRegister.asValue();
        for (Register r : csl.registers) {
            int offset = csl.offsetOf(r);
            movq(r, new Address(target.wordKind, frame, frameToCSA + offset));
        }
    }
}
