/*
 * Copyright (c) 2007, 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.max.asm.gen.risc.sparc;

import static com.sun.max.asm.gen.risc.sparc.SPARCFields.*;

import com.sun.max.asm.gen.risc.*;
import com.sun.max.asm.gen.risc.field.*;

/**
 */
class ConditionalMove extends SPARCInstructionDescriptionCreator {

    private void addIccOrFcc(String suffix, RiscField fmovccField, RiscField movccField, int typeBitContents, int condContents) {
        // A.33
        final Object[] fmovHead = {op(0x2), op3(0x35), bits_18_18(0), cond_17_14(condContents), fmovTypeBit(typeBitContents), fmovccField};
        setCurrentArchitectureManualSection("A.33");
        define("fmovs" + suffix, fmovHead, opfLow_10_5(0x1), sfrs2, sfrd);
        define("fmovd" + suffix, fmovHead, opfLow_10_5(0x2), dfrs2, dfrd);
        define("fmovq" + suffix, fmovHead, opfLow_10_5(0x3), qfrs2, qfrd);
        // A.35
        final Object[] movHead = {op(0x2), op3(0x2c), movTypeBit(typeBitContents), cond_17_14(condContents), movccField};
        setCurrentArchitectureManualSection("A.35");
        define("mov" + suffix, movHead, i(1), simm11, rd);
        define("mov" + suffix, movHead, i(0), res_10_5, rs2, rd);
    }

    private void addMovr(String suffix, int rcondContents) {
        // A.34
        final Object[] fmovrHead = {op(0x2), op3(0x35), bits_13_13(0), rcond_12_10(rcondContents)};
        setCurrentArchitectureManualSection("A.34");
        define("fmovrs" + suffix, fmovrHead, opfLow_9_5(0x5), rs1, sfrs2, sfrd);
        define("fmovrd" + suffix, fmovrHead, opfLow_9_5(0x6), rs1, dfrs2, dfrd);
        define("fmovrq" + suffix, fmovrHead, opfLow_9_5(0x7), rs1, qfrs2, qfrd);
        // A.36
        final Object[] movrHead = {op(0x2), op3(0x2f), rcond_12_10(rcondContents)};
        setCurrentArchitectureManualSection("A.36");
        define("movr" + suffix, movrHead, i(0), res_9_5, rs1, rs2, rd);
        // sparc asm is too lenient with simm10
        define("movr" + suffix, movrHead, i(1), rs1, simm10, rd);
    }

    private void addIcc(String suffix, int condContents) {
        addIccOrFcc(suffix, fmovicc, movicc, 1, condContents);
    }

    private void addFcc(String suffix, int condContents) {
        addIccOrFcc(suffix, fmovfcc, movfcc, 0, condContents);
    }

    private void create_A33_A35() {
        addIcc("a", 0x8);
        addIcc("n", 0x0);
        addIcc("ne", 0x9);
        addIcc("e", 0x1);
        addIcc("g", 0xa);
        addIcc("le", 0x2);
        addIcc("ge", 0xb);
        addIcc("l", 0x3);
        addIcc("gu", 0xc);
        addIcc("leu", 0x4);
        addIcc("cc", 0xd);
        addIcc("cs", 0x5);
        addIcc("pos", 0xe);
        addIcc("neg", 0x6);
        addIcc("vc", 0xf);
        addIcc("vs", 0x7);

        addFcc("a", 0x8);
        addFcc("n", 0x0);
        addFcc("u", 0x7);
        addFcc("g", 0x6);
        addFcc("ug", 0x5);
        addFcc("l", 0x4);
        addFcc("ul", 0x3);
        addFcc("lg", 0x2);
        addFcc("ne", 0x1);
        addFcc("e", 0x9);
        addFcc("ue", 0xa);
        addFcc("ge", 0xb);
        addFcc("uge", 0xc);
        addFcc("le", 0xd);
        addFcc("ule", 0xe);
        addFcc("o", 0xf);
    }

    private void create_A34_A36() {
        addMovr("e", 0x1);
        addMovr("lez", 0x2);
        addMovr("lz", 0x3);
        addMovr("ne", 0x5);
        addMovr("gz", 0x6);
        addMovr("gez", 0x7);
    }

    ConditionalMove(RiscTemplateCreator templateCreator) {
        super(templateCreator);

        create_A34_A36();
        create_A33_A35();
    }

}
