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
package com.oracle.max.asm.target.amd64;

import com.oracle.max.asm.*;
import com.sun.cri.ci.*;
import com.sun.cri.ri.*;

/**
 * This class implements commonly used X86 code patterns.
 */
public class AMD64MacroAssembler extends AMD64Assembler {

    public AMD64MacroAssembler(CiTarget target, RiRegisterConfig registerConfig) {
        super(target, registerConfig);
    }

    public void pushptr(CiAddress src) {
        pushq(src);
    }

    public void popptr(CiAddress src) {
        popq(src);
    }

    public void xorptr(CiRegister dst, CiRegister src) {
        xorq(dst, src);
    }

    public void xorptr(CiRegister dst, CiAddress src) {
        xorq(dst, src);
    }

    // 64 bit versions


    public void decrementq(CiRegister reg, int value) {
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
        if (value == 1 && AsmOptions.UseIncDec) {
            decq(reg);
        } else {
            subq(reg, value);
        }
    }

    public void incrementq(CiRegister reg, int value) {
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
        if (value == 1 && AsmOptions.UseIncDec) {
            incq(reg);
        } else {
            addq(reg, value);
        }
    }

    // These are mostly for initializing null
    public void movptr(CiAddress dst, int src) {
        movslq(dst, src);
    }

    public final void cmp32(CiRegister src1, int imm) {
        cmpl(src1, imm);
    }

    public final void cmp32(CiRegister src1, CiAddress src2) {
        cmpl(src1, src2);
    }

    public void cmpsd2int(CiRegister opr1, CiRegister opr2, CiRegister dst, boolean unorderedIsLess) {
        assert opr1.isFpu() && opr2.isFpu();
        ucomisd(opr1, opr2);

        Label l = new Label();
        if (unorderedIsLess) {
            movl(dst, -1);
            jcc(AMD64Assembler.ConditionFlag.parity, l);
            jcc(AMD64Assembler.ConditionFlag.below, l);
            movl(dst, 0);
            jcc(AMD64Assembler.ConditionFlag.equal, l);
            incrementl(dst, 1);
        } else { // unordered is greater
            movl(dst, 1);
            jcc(AMD64Assembler.ConditionFlag.parity, l);
            jcc(AMD64Assembler.ConditionFlag.above, l);
            movl(dst, 0);
            jcc(AMD64Assembler.ConditionFlag.equal, l);
            decrementl(dst, 1);
        }
        bind(l);
    }

    public void cmpss2int(CiRegister opr1, CiRegister opr2, CiRegister dst, boolean unorderedIsLess) {
        assert opr1.isFpu();
        assert opr2.isFpu();
        ucomiss(opr1, opr2);

        Label l = new Label();
        if (unorderedIsLess) {
            movl(dst, -1);
            jcc(AMD64Assembler.ConditionFlag.parity, l);
            jcc(AMD64Assembler.ConditionFlag.below, l);
            movl(dst, 0);
            jcc(AMD64Assembler.ConditionFlag.equal, l);
            incrementl(dst, 1);
        } else { // unordered is greater
            movl(dst, 1);
            jcc(AMD64Assembler.ConditionFlag.parity, l);
            jcc(AMD64Assembler.ConditionFlag.above, l);
            movl(dst, 0);
            jcc(AMD64Assembler.ConditionFlag.equal, l);
            decrementl(dst, 1);
        }
        bind(l);
    }

    public void cmpptr(CiRegister src1, CiRegister src2) {
        cmpq(src1, src2);
    }

    public void cmpptr(CiRegister src1, CiAddress src2) {
        cmpq(src1, src2);
    }

    public void cmpptr(CiRegister src1, int src2) {
        cmpq(src1, src2);
    }

    public void cmpptr(CiAddress src1, int src2) {
        cmpq(src1, src2);
    }

    public void decrementl(CiRegister reg, int value) {
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
        if (value == 1 && AsmOptions.UseIncDec) {
            decl(reg);
        } else {
            subl(reg, value);
        }
    }

    public void decrementl(CiAddress dst, int value) {
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
        if (value == 1 && AsmOptions.UseIncDec) {
            decl(dst);
        } else {
            subl(dst, value);
        }
    }

    public void incrementl(CiRegister reg, int value) {
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
        if (value == 1 && AsmOptions.UseIncDec) {
            incl(reg);
        } else {
            addl(reg, value);
        }
    }

    public void incrementl(CiAddress dst, int value) {
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
        if (value == 1 && AsmOptions.UseIncDec) {
            incl(dst);
        } else {
            addl(dst, value);
        }
    }

    public void signExtendByte(CiRegister reg) {
        if (reg.isByte()) {
            movsxb(reg, reg); // movsxb
        } else {
            shll(reg, 24);
            sarl(reg, 24);
        }
    }

    public void signExtendShort(CiRegister reg) {
        movsxw(reg, reg); // movsxw
    }

    // Support optimal SSE move instructions.
    public void movflt(CiRegister dst, CiRegister src) {
        assert dst.isFpu() && src.isFpu();
        if (AsmOptions.UseXmmRegToRegMoveAll) {
            movaps(dst, src);
        } else {
            movss(dst, src);
        }
    }

    public void movflt(CiRegister dst, CiAddress src) {
        assert dst.isFpu();
        movss(dst, src);
    }

    public void movflt(CiAddress dst, CiRegister src) {
        assert src.isFpu();
        movss(dst, src);
    }

    public void movdbl(CiRegister dst, CiRegister src) {
        assert dst.isFpu() && src.isFpu();
        if (AsmOptions.UseXmmRegToRegMoveAll) {
            movapd(dst, src);
        } else {
            movsd(dst, src);
        }
    }

    public void movdbl(CiRegister dst, CiAddress src) {
        assert dst.isFpu();
        if (AsmOptions.UseXmmLoadAndClearUpper) {
            movsd(dst, src);
        } else {
            movlpd(dst, src);
        }
    }

    public void movdbl(CiAddress dst, CiRegister src) {
        assert src.isFpu();
        movsd(dst, src);
    }

    /**
     * Non-atomic write of a 64-bit constant to memory. Do not use
     * if the address might be a volatile field!
     */
    public void movlong(CiAddress dst, long src) {
        CiAddress high = new CiAddress(dst.kind, dst.base, dst.index, dst.scale, dst.displacement + 4);
        movl(dst, (int) (src & 0xFFFFFFFF));
        movl(high, (int) (src >> 32));
    }

    public void xchgptr(CiRegister src1, CiRegister src2) {
        xchgq(src1, src2);
    }

    public void flog(CiRegister dest, CiRegister value, boolean base10) {
        assert value.spillSlotSize == dest.spillSlotSize;

        CiAddress tmp = new CiAddress(CiKind.Double, AMD64.RSP);
        if (base10) {
            fldlg2();
        } else {
            fldln2();
        }
        subq(AMD64.rsp, value.spillSlotSize);
        movsd(tmp, value);
        fld(tmp);
        fyl2x();
        fstp(tmp);
        movsd(dest, tmp);
        addq(AMD64.rsp, dest.spillSlotSize);
    }

    public void fsin(CiRegister dest, CiRegister value) {
        ftrig(dest, value, 's');
    }

    public void fcos(CiRegister dest, CiRegister value) {
        ftrig(dest, value, 'c');
    }

    public void ftan(CiRegister dest, CiRegister value) {
        ftrig(dest, value, 't');
    }

    private void ftrig(CiRegister dest, CiRegister value, char op) {
        assert value.spillSlotSize == dest.spillSlotSize;

        CiAddress tmp = new CiAddress(CiKind.Double, AMD64.RSP);
        subq(AMD64.rsp, value.spillSlotSize);
        movsd(tmp, value);
        fld(tmp);
        if (op == 's') {
            fsin();
        } else if (op == 'c') {
            fcos();
        } else if (op == 't') {
            fptan();
            fstp(0); // ftan pushes 1.0 in addition to the actual result, pop
        } else {
            throw new InternalError("should not reach here");
        }
        fstp(tmp);
        movsd(dest, tmp);
        addq(AMD64.rsp, dest.spillSlotSize);
    }

    /**
     * Emit code to save a given set of callee save registers in the
     * {@linkplain CiCalleeSaveLayout CSA} within the frame.
     * @param csl the description of the CSA
     * @param frameToCSA offset from the frame pointer to the CSA
     */
    public void save(CiCalleeSaveLayout csl, int frameToCSA) {
        CiRegisterValue frame = frameRegister.asValue();
        for (CiRegister r : csl.registers) {
            int offset = csl.offsetOf(r);
            movq(new CiAddress(target.wordKind, frame, frameToCSA + offset), r);
        }
    }

    public void restore(CiCalleeSaveLayout csl, int frameToCSA) {
        CiRegisterValue frame = frameRegister.asValue();
        for (CiRegister r : csl.registers) {
            int offset = csl.offsetOf(r);
            movq(r, new CiAddress(target.wordKind, frame, frameToCSA + offset));
        }
    }
}
