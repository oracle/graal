/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.asm.sparc;

import static com.oracle.graal.sparc.SPARC.*;
import static com.oracle.graal.asm.sparc.SPARCAssembler.CC.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.asm.*;

public class SPARCMacroAssembler extends SPARCAssembler {

    public SPARCMacroAssembler(TargetDescription target, RegisterConfig registerConfig) {
        super(target, registerConfig);
    }

    @Override
    public void align(int modulus) {
        if (codeBuffer.position() % modulus != 0) {
            final int count = modulus - (codeBuffer.position() % modulus);
            for (int i = 0; i < count; i++) {
                new Nop().emit(this);
            }
        }
    }

    @Override
    public void jmp(Label l) {
        new Bpa(l).emit(this);
        new Nop().emit(this);
    }

    @Override
    protected final void patchJumpTarget(int branch, int branchTarget) {
        final int disp = branchTarget - branch;
        Fmt00c fmt = Fmt00c.read(this, branch);
        fmt.setDisp19(disp);
        fmt.write(this, branch);
    }

    @Override
    public AbstractAddress makeAddress(Register base, int displacement) {
        throw new InternalError("NYI");
    }

    @Override
    public AbstractAddress getPlaceholder() {
        throw new InternalError("NYI");
    }

    public static class Bclr extends Andn {

        public Bclr(Register src, Register dst) {
            super(dst, src, dst);
        }

        public Bclr(int simm13, Register dst) {
            super(dst, simm13, dst);
        }
    }

    public static class Bset extends Or {

        public Bset(Register src, Register dst) {
            super(dst, src, dst);
        }

        public Bset(int simm13, Register dst) {
            super(dst, simm13, dst);
        }
    }

    public static class Btst extends Andcc {

        public Btst(Register src1, Register src2) {
            super(src1, src2, g0);
        }

        public Btst(Register src1, int simm13) {
            super(src1, simm13, g0);
        }
    }

    public static class Clr {

        public Clr(SPARCAssembler asm, Register dst) {
            new Or(g0, g0, dst).emit(asm);
        }

        public Clr(SPARCAssembler asm, SPARCAddress addr) {
            new Stw(g0, addr).emit(asm);
        }
    }

    public static class Clrb extends Stb {

        public Clrb(SPARCAddress addr) {
            super(g0, addr);
        }
    }

    public static class Clrh extends Sth {

        public Clrh(SPARCAddress addr) {
            super(g0, addr);
        }
    }

    public static class Clrx extends Stx {

        public Clrx(SPARCAddress addr) {
            super(g0, addr);
        }
    }

    public static class Clruw extends Srl {

        public Clruw(Register src1, Register dst) {
            super(src1, g0, dst);
            assert src1.encoding() != dst.encoding();
        }

        public Clruw(Register dst) {
            super(dst, g0, dst);
        }
    }

    public static class Cmp extends Subcc {

        public Cmp(Register a, Register b) {
            super(a, b, g0);
        }

        public Cmp(Register a, int simm13) {
            super(a, simm13, g0);
        }
    }

    public static class Dec extends Sub {

        public Dec(Register dst) {
            super(dst, 1, dst);
        }

        public Dec(int simm13, Register dst) {
            super(dst, simm13, dst);
        }
    }

    public static class Deccc extends Subcc {

        public Deccc(Register dst) {
            super(dst, 1, dst);
        }

        public Deccc(int simm13, Register dst) {
            super(dst, simm13, dst);
        }
    }

    @SuppressWarnings("unused")
    public static class Inc {

        public Inc(Register dst) {
            new Add(dst, 1, dst);
        }

        public Inc(int simm13, Register dst) {
            new Add(dst, simm13, dst);
        }
    }

    @SuppressWarnings("unused")
    public static class Inccc {

        public Inccc(Register dst) {
            new Addcc(dst, 1, dst);
        }

        public Inccc(int simm13, Register dst) {
            new Addcc(dst, simm13, dst);
        }
    }

    @SuppressWarnings("unused")
    public static class Jmp extends Jmpl {

        public Jmp(SPARCAssembler asm, SPARCAddress address) {
            super(address.getBase(), address.getDisplacement(), g0);
        }
    }

    public static class Neg extends Sub {

        public Neg(Register src2, Register dst) {
            super(g0, src2, dst);
            assert src2.encoding() != dst.encoding();
        }

        public Neg(Register dst) {
            super(g0, dst, dst);
        }
    }

    public static class Mov extends Or {

        public Mov(Register src1, Register dst) {
            super(g0, src1, dst);
            assert src1.encoding() != dst.encoding();
        }

        public Mov(int simm13, Register dst) {
            super(g0, simm13, dst);
        }
    }

    public static class Nop extends Sethi {

        public Nop() {
            super(0, r0);
        }
    }

    public static class Not extends Xnor {

        public Not(Register src1, Register dst) {
            super(src1, g0, dst);
            assert src1.encoding() != dst.encoding();
        }

        public Not(Register dst) {
            super(dst, g0, dst);
        }
    }

    public static class RestoreWindow extends Restore {

        public RestoreWindow() {
            super(g0, g0, g0);
        }
    }

    @SuppressWarnings("unused")
    public static class Ret extends Jmpl {

        public Ret() {
            super(i7, 8, g0);
        }
    }

    public static class SaveWindow extends Save {

        public SaveWindow() {
            super(g0, g0, g0);
        }
    }

    @SuppressWarnings("unused")
    public static class Setuw {

        public Setuw(SPARCAssembler masm, int value, Register dst) {
            if (value == 0) {
                new Clr(masm, dst);
            } else if (-4095 <= value && value <= 4096) {
                new Or(g0, value, dst).emit(masm);
            } else if (value >= 0 && ((value & 0x3FFF) == 0)) {
                new Sethi(hi22(value), dst).emit(masm);
            } else {
                new Sethi(hi22(value), dst).emit(masm);
                new Or(dst, lo10(value), dst).emit(masm);
            }
        }
    }

    public static class Setx {

        public Setx(SPARCAssembler asm, long value, Register tmp, Register dst) {
            int hi = (int) (value >> 32);
            int lo = (int) (value & ~0);

            if (isSimm13(lo) && value == lo) {
                new Or(g0, lo, dst).emit(asm);
            } else if (hi == 0) {
                new Sethi(lo, dst).emit(asm);   // hardware version zero-extends to upper 32
                if (lo10(lo) != 0) {
                    new Or(dst, lo10(lo), dst).emit(asm);
                }
            } else if (hi == -1) {
                new Sethi(~lo, dst).emit(asm);  // hardware version zero-extends to upper 32
                new Xor(dst, lo10(lo) ^ ~lo10(~0), dst).emit(asm);
            } else if (lo == 0) {
                if (isSimm13(hi)) {
                    new Or(g0, hi, dst).emit(asm);
                } else {
                    new Sethi(hi, dst).emit(asm);   // hardware version zero-extends to upper 32
                    if (lo10(hi) != 0) {
                        new Or(dst, lo10(hi), dst).emit(asm);
                    }
                }
                new Sllx(dst, 32, dst).emit(asm);
            } else {
                new Sethi(hi, tmp).emit(asm);
                new Sethi(lo, dst).emit(asm); // macro assembler version sign-extends
                if (lo10(hi) != 0) {
                    new Or(tmp, lo10(hi), tmp).emit(asm);
                }
                if (lo10(lo) != 0) {
                    new Or(dst, lo10(lo), dst).emit(asm);
                }
                new Sllx(tmp, 32, tmp).emit(asm);
                new Or(dst, tmp, dst).emit(asm);
            }
        }
    }

    public static class Signx extends Sra {

        public Signx(Register src1, Register dst) {
            super(src1, g0, dst);
            assert src1.encoding() != dst.encoding();
        }

        public Signx(Register dst) {
            super(dst, g0, dst);
        }
    }

    @SuppressWarnings("unused")
    public static class Trap {

        public Trap(SPARCAssembler asm, int trap) {
            assert trap >= 0 && trap <= 0x7f;
            new Ta(asm, Icc, g0, trap);
        }
    }
}
