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

import static com.oracle.graal.asm.sparc.SPARCAssembler.CC.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.sparc.*;

public class SPARCMacroAssembler extends SPARCAssembler {

    public SPARCMacroAssembler(TargetDescription target, RegisterConfig registerConfig) {
        super(target, registerConfig);
    }

    @SuppressWarnings("unused")
    public static class Bclr {

        public Bclr(SPARCAssembler asm, Register src, Register dst) {
            new Andn(asm, dst, src, dst);
        }

        public Bclr(SPARCAssembler asm, int simm13, Register dst) {
            new Andn(asm, dst, simm13, dst);
        }
    }

    @SuppressWarnings("unused")
    public static class Bset {

        public Bset(SPARCAssembler asm, Register src, Register dst) {
            new Or(asm, dst, src, dst);
        }

        public Bset(SPARCAssembler asm, int simm13, Register dst) {
            new Or(asm, dst, simm13, dst);
        }
    }

    @SuppressWarnings("unused")
    public static class Btog {

        public Btog(SPARCAssembler asm, Register src, Register dst) {
            new Xor(asm, dst, src, dst);
        }

        public Btog(SPARCAssembler asm, int simm13, Register dst) {
            new Xor(asm, dst, simm13, dst);
        }
    }

    @SuppressWarnings("unused")
    public static class Btst {

        public Btst(SPARCAssembler asm, Register src1, Register src2) {
            new Andcc(asm, src1, src2, SPARC.g0);
        }

        public Btst(SPARCAssembler asm, Register src1, int simm13) {
            new Andcc(asm, src1, simm13, SPARC.g0);
        }
    }

    @SuppressWarnings("unused")
    public static class Clr {

        public Clr(SPARCAssembler asm, Register dst) {
            new Or(asm, SPARC.g0, SPARC.g0, dst);
        }

        public Clr(SPARCAssembler asm, SPARCAddress addr) {
            new Stw(asm, SPARC.g0, addr);
        }
    }

    @SuppressWarnings("unused")
    public static class Clrb {

        public Clrb(SPARCAssembler asm, SPARCAddress addr) {
            new Stb(asm, SPARC.g0, addr);
        }
    }

    @SuppressWarnings("unused")
    public static class Clrh {

        public Clrh(SPARCAssembler asm, SPARCAddress addr) {
            new Sth(asm, SPARC.g0, addr);
        }
    }

    @SuppressWarnings("unused")
    public static class Clrx {

        public Clrx(SPARCAssembler asm, SPARCAddress addr) {
            new Stx(asm, SPARC.g0, addr);
        }
    }

    @SuppressWarnings("unused")
    public static class Clruw {

        public Clruw(SPARCAssembler asm, Register src1, Register dst) {
            assert src1.encoding() != dst.encoding();
            new Srl(asm, src1, SPARC.g0, dst);
        }

        public Clruw(SPARCAssembler asm, Register dst) {
            new Srl(asm, dst, SPARC.g0, dst);
        }
    }

    @SuppressWarnings("unused")
    public static class Cmp {

        public Cmp(SPARCAssembler asm, Register a, Register b) {
            new Subcc(asm, a, b, SPARC.g0);
        }

        public Cmp(SPARCAssembler asm, Register a, int simm13) {
            new Subcc(asm, a, simm13, SPARC.g0);
        }
    }

    @SuppressWarnings("unused")
    public static class Dec {

        public Dec(SPARCAssembler asm, Register dst) {
            new Sub(asm, dst, 1, dst);
        }

        public Dec(SPARCAssembler asm, int simm13, Register dst) {
            new Sub(asm, dst, simm13, dst);
        }
    }

    @SuppressWarnings("unused")
    public static class Deccc {

        public Deccc(SPARCAssembler asm, Register dst) {
            new Subcc(asm, dst, 1, dst);
        }

        public Deccc(SPARCAssembler asm, int simm13, Register dst) {
            new Subcc(asm, dst, simm13, dst);
        }
    }

    @SuppressWarnings("unused")
    public static class Inc {

        public Inc(SPARCAssembler asm, Register dst) {
            new Add(asm, dst, 1, dst);
        }

        public Inc(SPARCAssembler asm, int simm13, Register dst) {
            new Add(asm, dst, simm13, dst);
        }
    }

    @SuppressWarnings("unused")
    public static class Inccc {

        public Inccc(SPARCAssembler asm, Register dst) {
            new Addcc(asm, dst, 1, dst);
        }

        public Inccc(SPARCAssembler asm, int simm13, Register dst) {
            new Addcc(asm, dst, simm13, dst);
        }
    }

    @SuppressWarnings("unused")
    public static class Jmp {

        public Jmp(SPARCAssembler asm, SPARCAddress address) {
            new Jmpl(asm, address, SPARC.g0);
        }
    }

    @SuppressWarnings("unused")
    public static class Neg {

        public Neg(SPARCAssembler asm, Register src2, Register dst) {
            assert src2.encoding() != dst.encoding();
            new Sub(asm, SPARC.g0, src2, dst);
        }

        public Neg(SPARCAssembler asm, Register dst) {
            new Sub(asm, SPARC.g0, dst, dst);
        }
    }

    @SuppressWarnings("unused")
    public static class Not {

        public Not(SPARCAssembler asm, Register src1, Register dst) {
            assert src1.encoding() != dst.encoding();
            new Xnor(asm, src1, SPARC.g0, dst);
        }

        public Not(SPARCAssembler asm, Register dst) {
            new Xnor(asm, dst, SPARC.g0, dst);
        }
    }

    @SuppressWarnings("unused")
    public static class RestoreWindow {

        public RestoreWindow(SPARCAssembler asm) {
            new Restore(asm, SPARC.g0, SPARC.g0, SPARC.g0);
        }
    }

    @SuppressWarnings("unused")
    public static class Ret {

        public Ret(SPARCAssembler asm) {
            new Jmpl(asm, new SPARCAddress(SPARC.i0, 8), SPARC.g0);

        }
    }

    @SuppressWarnings("unused")
    public static class SaveWindow {

        public SaveWindow(SPARCAssembler asm) {
            new Save(asm, SPARC.g0, SPARC.g0, SPARC.g0);
        }
    }

    @SuppressWarnings("unused")
    public static class Setuw {

        public Setuw(SPARCAssembler asm, int value, Register dst) {
            if (value >= 0 && ((value & 0x3FFF) == 0)) {
                new Sethi(asm, hi22(value), dst);
            } else if (-4095 <= value && value <= 4096) {
                new Or(asm, SPARC.g0, value, dst);
            } else {
                new Sethi(asm, hi22(value), dst);
                new Or(asm, dst, lo10(value), dst);
            }
        }
    }

    @SuppressWarnings("unused")
    public static class Setx {

        public Setx(SPARCAssembler asm, long value, Register tmp, Register dst) {
            int hi = (int) (value >> 32);
            int lo = (int) (value & ~0);

            if (isSimm13(lo) && value == lo) {
                new Or(asm, SPARC.g0, lo, dst);
            } else if (hi == 0) {
                new Sethi(asm, lo, dst);   // hardware version zero-extends to upper 32
                if (lo10(lo) != 0) {
                    new Or(asm, dst, lo10(lo), dst);
                }
            } else if (hi == -1) {
                new Sethi(asm, ~lo, dst);  // hardware version zero-extends to upper 32
                new Xor(asm, dst, lo10(lo) ^ ~lo10(~0), dst);
            } else if (lo == 0) {
                if (isSimm13(hi)) {
                    new Or(asm, SPARC.g0, hi, dst);
                } else {
                    new Sethi(asm, hi, dst);   // hardware version zero-extends to upper 32
                    if (lo10(hi) != 0) {
                        new Or(asm, dst, lo10(hi), dst);
                    }
                }
                new Sllx(asm, dst, 32, dst);
            } else {
                new Sethi(asm, hi, tmp);
                new Sethi(asm, lo, dst); // macro assembler version sign-extends
                if (lo10(hi) != 0) {
                    new Or(asm, tmp, lo10(hi), tmp);
                }
                if (lo10(lo) != 0) {
                    new Or(asm, dst, lo10(lo), dst);
                }
                new Sllx(asm, tmp, 32, tmp);
                new Or(asm, dst, tmp, dst);
            }
        }
    }

    @SuppressWarnings("unused")
    public static class Signx {

        public Signx(SPARCAssembler asm, Register src1, Register dst) {
            assert src1.encoding() != dst.encoding();
            new Sra(asm, src1, SPARC.g0, dst);
        }

        public Signx(SPARCAssembler asm, Register dst) {
            new Sra(asm, dst, SPARC.g0, dst);
        }
    }

    @SuppressWarnings("unused")
    public static class Trap {

        public Trap(SPARCAssembler asm, int trap) {
            assert trap >= 0 && trap <= 0x7f;
            new Ta(asm, Icc, SPARC.g0, trap);
        }
    }
}
