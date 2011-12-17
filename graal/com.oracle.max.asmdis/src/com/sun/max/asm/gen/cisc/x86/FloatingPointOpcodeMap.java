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
package com.sun.max.asm.gen.cisc.x86;

import static com.sun.max.asm.gen.cisc.x86.FloatingPointOperandCode.*;
import static com.sun.max.asm.gen.cisc.x86.HexByte.*;
import static com.sun.max.asm.ia32.IA32GeneralRegister16.*;
import static com.sun.max.asm.x86.FPStackRegister.*;

import com.sun.max.asm.gen.*;

/**
 */
public class FloatingPointOpcodeMap extends X86InstructionDescriptionCreator {

    private void create_D8() {
        define(_D8, _C0, "FADD", ST, ST_i);
        define(_D8, _C8, "FMUL", ST, ST_i);
        define(_D8, _D0, "FCOM", ST_i);
        define(_D8, _D8, "FCOMP", ST_i);
        define(_D8, _E0, "FSUB", ST, ST_i);
        define(_D8, _E8, "FSUBR", ST, ST_i);
        define(_D8, _F0, "FDIV", ST, ST_i);
        define(_D8, _F8, "FDIVR", ST, ST_i);
    }

    private void create_D9() {
        define(_D9, _C0, "FLD", ST_i);
        define(_D9, _C8, "FXCH", ST_i);

        define(_D9, _D0, "FNOP");
        define(_D9, _D8, "FSTP1", ST_i).beNotExternallyTestable(); // not implemented by gas, since this is a redundant instruction

        define(_D9, _E0, "FCHS");
        define(_D9, _E1, "FABS");
        define(_D9, _E4, "FTST");
        define(_D9, _E5, "FXAM");
        define(_D9, _E8, "FLD1");
        define(_D9, _E9, "FLDL2T");
        define(_D9, _EA, "FLDL2E");
        define(_D9, _EB, "FLDPI");
        define(_D9, _EC, "FLDLG2");
        define(_D9, _ED, "FLDLN2");
        define(_D9, _EE, "FLDZ");

        define(_D9, _F0, "F2XM1");
        define(_D9, _F1, "FYL2X");
        define(_D9, _F2, "FPTAN");
        define(_D9, _F3, "FPATAN");
        define(_D9, _F4, "FXTRACT");
        define(_D9, _F5, "FPREM1");
        define(_D9, _F6, "FDECSTP");
        define(_D9, _F7, "FINCSTP");
        define(_D9, _F8, "FPREM");
        define(_D9, _F9, "FYL2XP1");
        define(_D9, _FA, "FSQRT");
        define(_D9, _FB, "FSINCOS");
        define(_D9, _FC, "FRNDINT");
        define(_D9, _FD, "FSCALE");
        define(_D9, _FE, "FSIN");
        define(_D9, _FF, "FCOS");
    }

    private void create_DA() {
        define(_DA, _C0, "FCMOVB", ST, ST_i);
        define(_DA, _C8, "FCMOVE", ST, ST_i);
        define(_DA, _D0, "FCMOVBE", ST, ST_i);
        define(_DA, _D8, "FCMOVU", ST, ST_i);
        define(_DA, _E9, "FUCOMPP");
    }

    private void create_DB() {
        define(_DB, _C0, "FCMOVNB", ST, ST_i);
        define(_DB, _C8, "FCMOVNE", ST, ST_i);
        define(_DB, _D0, "FCMOVNBE", ST, ST_i);
        define(_DB, _D8, "FCMOVNU", ST, ST_i);
        define(_DB, _E2, "FCLEX");
        define(_DB, _E3, "FINIT");
        define(_DB, _E8, "FUCOMI", ST, ST_i);
        define(_DB, _F0, "FCOMI", ST, ST_i);
    }

    private void create_DC() {
        define(_DC, _C0, "FADD", ST_i.excludeExternalTestArguments(ST_0, ST), ST); //gas uses D8 if both operands are ST(0)
        define(_DC, _C8, "FMUL", ST_i.excludeExternalTestArguments(ST_0, ST), ST); //gas uses D8 if both operands are ST(0)
        define(_DC, _D0, "FCOM2", ST_i).beNotExternallyTestable(); // not implemented by gas, since this is a redundant instruction
        define(_DC, _D8, "FCOMP3", ST_i).beNotExternallyTestable(); // not implemented by gas, since this is a redundant instruction
        define(_DC, _E0, "FSUB", ST_i.excludeExternalTestArguments(ST_0, ST), ST); //gas uses D8 if both operands are ST(0)
        define(_DC, _E8, "FSUBR", ST_i.excludeExternalTestArguments(ST_0, ST), ST); //gas uses D8 if both operands are ST(0)
        define(_DC, _F0, "FDIV", ST_i.excludeExternalTestArguments(ST_0, ST), ST); //gas uses D8 if both operands are ST(0)
        define(_DC, _F8, "FDIVR", ST_i.excludeExternalTestArguments(ST_0, ST), ST); //gas uses D8 if both operands are ST(0)
    }

    private void create_DD() {
        define(_DD, _C0, "FFREE", ST_i);
        define(_DD, _C8, "FXCH4", ST_i).beNotExternallyTestable(); // not implemented by gas, since this is a redundant instruction
        define(_DD, _D0, "FST", ST_i);
        define(_DD, _D8, "FSTP", ST_i);
        define(_DD, _E0, "FUCOM", ST_i);
        define(_DD, _E8, "FUCOMP", ST_i);
    }

    private void create_DE() {
        define(_DE, _C0, "FADDP", ST_i, ST);
        define(_DE, _C8, "FMULP", ST_i, ST);
        define(_DE, _D0, "FCOMP5", ST_i, ST).beNotExternallyTestable(); // not implemented by gas, since this is a redundant instruction
        define(_DE, _D9, "FCOMPP");
        define(_DE, _E0, "FSUBRP", ST_i, ST).setExternalName("fsubp"); // gas bug: confounding FSUBRP and FSUBP
        define(_DE, _E8, "FSUBP", ST_i, ST).setExternalName("fsubrp"); // gas bug: confounding FSUBRP and FSUBP
        define(_DE, _F0, "FDIVRP", ST_i, ST).setExternalName("fdivp"); // gas bug: confounding fdivrp and fdivp
        define(_DE, _F8, "FDIVP", ST_i, ST).setExternalName("fdivrp"); // gas bug: confounding fdivrp and fdivp
    }

    private void create_DF() {
        define(_DF, _C0, "FFREEP", ST_i);
        define(_DF, _C8, "FXCH7", ST_i).beNotExternallyTestable(); // not implemented by gas, since this is a redundant instruction
        define(_DF, _D0, "FSTP8", ST_i).beNotExternallyTestable(); // not implemented by gas, since this is a redundant instruction
        define(_DF, _D8, "FSTP9", ST_i).beNotExternallyTestable(); // not implemented by gas, since this is a redundant instruction
        define(_DF, _E0, "FSTSW", AX);
        define(_DF, _E8, "FUCOMIP", ST, ST_i);
        define(_DF, _F0, "FCOMIP", ST, ST_i);
    }

    public FloatingPointOpcodeMap(Assembly<? extends X86Template> assembly) {
        super(assembly);
        create_D8();
        create_D9();
        create_DA();
        create_DB();
        create_DC();
        create_DD();
        create_DE();
        create_DF();
    }
}
