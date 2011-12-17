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
package com.sun.max.asm.arm.example;

import com.sun.max.asm.arm.*;

/**
 */
public interface ExampleARMAssemblerSpecification {

    public static class Generator {
        public static void main(String[] args) throws Exception {
            final String[] programArguments = {
                "-i=" + ExampleARMAssemblerSpecification.class.getName(),
                "-r=" + ExampleARMAssembler.class.getName(),
                "-l=" + ExampleARMAssembler.class.getName()
            };
            // Using reflection prevents a literal reference to a class in the assembler generator framework.
            Class.forName("com.sun.max.asm.gen.risc.arm.ARMAssemblerGenerator").
                getMethod("main", String[].class).invoke(null, (Object) programArguments);
        }
    }

    // Checkstyle: stop

    void adc(final ConditionCode cond, final SBit s, final GPR Rd, final GPR Rn, final int immed_8, final int rotate_amount);
    void adclsl(final ConditionCode cond, final SBit s, final GPR Rd, final GPR Rn, final GPR Rm, final int shift_imm);
    void add(final ConditionCode cond, final SBit s, final GPR Rd, final GPR Rn, final int immed_8, final int rotate_amount);
    void addror(final ConditionCode cond, final SBit s, final GPR Rd, final GPR Rn, final GPR Rm, final int shift_imm);
    void biclsr(final ConditionCode cond, final SBit s, final GPR Rd, final GPR Rn, final GPR Rm, final GPR Rs);
    void cmnasr(final ConditionCode cond, final GPR Rn, final GPR Rm, final GPR Rs);
    void cmnror(final ConditionCode cond, final GPR Rn, final GPR Rm, final GPR Rs);
    void cmpasr(final ConditionCode cond, final GPR Rn, final GPR Rm, final int shift_imm);
    void eorlsr(final ConditionCode cond, final SBit s, final GPR Rd, final GPR Rn, final GPR Rm, final GPR Rs);
    void movror(final ConditionCode cond, final SBit s, final GPR Rd, final GPR Rm, final int shift_imm);
    void mvnror(final ConditionCode cond, final SBit s, final GPR Rd, final GPR Rm, final GPR Rs);
    void orrlsl(final ConditionCode cond, final SBit s, final GPR Rd, final GPR Rn, final GPR Rm, final int shift_imm);
    void rsb(final ConditionCode cond, final SBit s, final GPR Rd, final GPR Rn, final int immed_8, final int rotate_amount);
    void rsblsl(final ConditionCode cond, final SBit s, final GPR Rd, final GPR Rn, final GPR Rm, final int shift_imm);
    void rsc(final ConditionCode cond, final SBit s, final GPR Rd, final GPR Rn, final GPR Rm);
    void rsclsr(final ConditionCode cond, final SBit s, final GPR Rd, final GPR Rn, final GPR Rm, final GPR Rs);
    void sbcror(final ConditionCode cond, final SBit s, final GPR Rd, final GPR Rn, final GPR Rm, final GPR Rs);
    void sub(final ConditionCode cond, final SBit s, final GPR Rd, final GPR Rn, final int immed_8, final int rotate_amount);
    void tst(final ConditionCode cond, final GPR Rn, final int immediate);
    void tstlsr(final ConditionCode cond, final GPR Rn, final GPR Rm, final int shift_imm);
    void smlal(final ConditionCode cond, final SBit s, final GPR RdLo, final GPR RdHi, final GPR Rm, final GPR Rs);
    void umull(final ConditionCode cond, final SBit s, final GPR RdLo, final GPR RdHi, final GPR Rm, final GPR Rs);
    void ldradd(final ConditionCode cond, final GPR Rd, final GPR Rn, final GPR Rm);
    void ldrsubw(final ConditionCode cond, final GPR Rd, final GPR Rn, final int offset_12);
    void ldraddrorpost(final ConditionCode cond, final GPR Rd, final GPR Rn, final GPR Rm, final int shift_imm);
    void strsubasr(final ConditionCode cond, final GPR Rd, final GPR Rn, final GPR Rm, final int shift_imm);
    void strsubrorw(final ConditionCode cond, final GPR Rd, final GPR Rn, final GPR Rm, final int shift_imm);
    void straddpost(final ConditionCode cond, final GPR Rd, final GPR Rn, final int offset_12);
    void swi(final ConditionCode cond, final int immed_24);

    // Checkstyle: resume

}
