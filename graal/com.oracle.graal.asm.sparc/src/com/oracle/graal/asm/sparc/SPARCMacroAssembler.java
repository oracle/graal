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

import com.oracle.graal.api.code.*;
import com.oracle.graal.sparc.*;

public class SPARCMacroAssembler extends SPARCAssembler {

    public SPARCMacroAssembler(TargetDescription target, RegisterConfig registerConfig) {
        super(target, registerConfig);
    }

    @SuppressWarnings("unused")
    public static class Cmp {

        public Cmp(SPARCAssembler masm, Register a, Register b) {
            new Subcc(masm, a, b, SPARC.r0);
        }

        public Cmp(SPARCAssembler masm, Register a, int simm13) {
            new Subcc(masm, a, simm13, SPARC.r0);
        }
    }

    @SuppressWarnings("unused")
    public static class Setuw {

        public Setuw(SPARCAssembler masm, int value, Register dst) {
            if (value >= 0 && ((value & 0x3FFF) == 0)) {
                new Sethi(masm, hi22(value), dst);
            } else if (-4095 <= value && value <= 4096) {
                // or g0, value, dst
                new Or(masm, SPARC.r0, value, dst);
            } else {
                new Sethi(masm, hi22(value), dst);
                new Or(masm, dst, lo10(value), dst);
            }
        }
    }

    @SuppressWarnings("unused")
    public static class Setx {

        public Setx(SPARCAssembler masm, long value, Register tmp, Register dst) {
            int hi = (int) (value >> 32);
            int lo = (int) (value & ~0);

            if (isSimm13(lo) && value == lo) {
                new Or(masm, SPARC.r0, lo, dst);
            } else if (hi == 0) {
                new Sethi(masm, lo, dst);   // hardware version zero-extends to upper 32
                if (lo10(lo) != 0) {
                    new Or(masm, dst, lo10(lo), dst);
                }
            } else if (hi == -1) {
                new Sethi(masm, ~lo, dst);  // hardware version zero-extends to upper 32
                new Xor(masm, dst, lo10(lo) ^ ~lo10(~0), dst);
            } else if (lo == 0) {
                if (isSimm13(hi)) {
                    new Or(masm, SPARC.r0, hi, dst);
                } else {
                    new Sethi(masm, hi, dst);   // hardware version zero-extends to upper 32
                    if (lo10(hi) != 0) {
                        new Or(masm, dst, lo10(hi), dst);
                    }
                }
                new Sllx(masm, dst, 32, dst);
            } else {
                new Sethi(masm, hi, tmp);
                new Sethi(masm, lo, dst); // macro assembler version sign-extends
                if (lo10(hi) != 0) {
                    new Or(masm, tmp, lo10(hi), tmp);
                }
                if (lo10(lo) != 0) {
                    new Or(masm, dst, lo10(lo), dst);
                }
                new Sllx(masm, tmp, 32, tmp);
                new Or(masm, dst, tmp, dst);
            }
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

}
