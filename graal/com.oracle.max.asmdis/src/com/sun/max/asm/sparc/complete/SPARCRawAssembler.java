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

package com.sun.max.asm.sparc.complete;

import com.sun.max.asm.sparc.*;

public abstract class SPARCRawAssembler extends AbstractSPARCAssembler {

// START GENERATED RAW ASSEMBLER METHODS
    /**
     * Pseudo-external assembler syntax: {@code casa  }<i>rs1</i>, <i>immAsi</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code casa          [%g0] 0x0,%g0, %g0}
     * <p>
     * Constraint: {@code 0 <= immAsi && immAsi <= 255}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.9"
     */
    // Template#: 1, Serial#: 1
    public void casa(final GPR rs1, final int immAsi, final GPR rs2, final GPR rd) {
        int instruction = 0xC1E00000;
        checkConstraint(0 <= immAsi && immAsi <= 255, "0 <= immAsi && immAsi <= 255");
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= ((immAsi & 0xff) << 5);
        instruction |= (rs2.value() & 0x1f);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code casa  }<i>rs1</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code casa          [%g0] %asi, %g0, %g0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.9"
     */
    // Template#: 2, Serial#: 2
    public void casa(final GPR rs1, final GPR rs2, final GPR rd) {
        int instruction = 0xC1E02000;
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (rs2.value() & 0x1f);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code casxa  }<i>rs1</i>, <i>immAsi</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code casxa         [%g0] 0x0,%g0, %g0}
     * <p>
     * Constraint: {@code 0 <= immAsi && immAsi <= 255}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.9"
     */
    // Template#: 3, Serial#: 3
    public void casxa(final GPR rs1, final int immAsi, final GPR rs2, final GPR rd) {
        int instruction = 0xC1F00000;
        checkConstraint(0 <= immAsi && immAsi <= 255, "0 <= immAsi && immAsi <= 255");
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= ((immAsi & 0xff) << 5);
        instruction |= (rs2.value() & 0x1f);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code casxa  }<i>rs1</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code casxa         [%g0] %asi, %g0, %g0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.9"
     */
    // Template#: 4, Serial#: 4
    public void casxa(final GPR rs1, final GPR rs2, final GPR rd) {
        int instruction = 0xC1F02000;
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (rs2.value() & 0x1f);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code flush  }<i>rs1</i>, <i>rs2</i>
     * Example disassembly syntax: {@code flush         %g0 + %g0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.20"
     */
    // Template#: 5, Serial#: 5
    public void flush(final GPR rs1, final GPR rs2) {
        int instruction = 0x81D80000;
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (rs2.value() & 0x1f);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code flush  }<i>rs1</i>, <i>simm13</i>
     * Example disassembly syntax: {@code flush         %g0 + -4096}
     * <p>
     * Constraint: {@code -4096 <= simm13 && simm13 <= 4095}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.20"
     */
    // Template#: 6, Serial#: 6
    public void flush(final GPR rs1, final int simm13) {
        int instruction = 0x81D82000;
        checkConstraint(-4096 <= simm13 && simm13 <= 4095, "-4096 <= simm13 && simm13 <= 4095");
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (simm13 & 0x1fff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code ld  }<i>rs1</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code ld            [%g0 + %g0], %f0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.25"
     */
    // Template#: 7, Serial#: 7
    public void ld(final GPR rs1, final GPR rs2, final SFPR rd) {
        int instruction = 0xC1000000;
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (rs2.value() & 0x1f);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code ld  }<i>rs1</i>, <i>simm13</i>, <i>rd</i>
     * Example disassembly syntax: {@code ld            [%g0 + -4096], %f0}
     * <p>
     * Constraint: {@code -4096 <= simm13 && simm13 <= 4095}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.25"
     */
    // Template#: 8, Serial#: 8
    public void ld(final GPR rs1, final int simm13, final SFPR rd) {
        int instruction = 0xC1002000;
        checkConstraint(-4096 <= simm13 && simm13 <= 4095, "-4096 <= simm13 && simm13 <= 4095");
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (simm13 & 0x1fff);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code ldd  }<i>rs1</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code ldd           [%g0 + %g0], %f0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.25"
     */
    // Template#: 9, Serial#: 9
    public void ldd(final GPR rs1, final GPR rs2, final DFPR rd) {
        int instruction = 0xC1180000;
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (rs2.value() & 0x1f);
        instruction |= (((rd.value() >>> 1) & 0xf) << 26) | (((rd.value() >>> 5) & 0x1) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code ldd  }<i>rs1</i>, <i>simm13</i>, <i>rd</i>
     * Example disassembly syntax: {@code ldd           [%g0 + -4096], %f0}
     * <p>
     * Constraint: {@code -4096 <= simm13 && simm13 <= 4095}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.25"
     */
    // Template#: 10, Serial#: 10
    public void ldd(final GPR rs1, final int simm13, final DFPR rd) {
        int instruction = 0xC1182000;
        checkConstraint(-4096 <= simm13 && simm13 <= 4095, "-4096 <= simm13 && simm13 <= 4095");
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (simm13 & 0x1fff);
        instruction |= (((rd.value() >>> 1) & 0xf) << 26) | (((rd.value() >>> 5) & 0x1) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code ldq  }<i>rs1</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code ldq           [%g0 + %g0], %f0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.25"
     */
    // Template#: 11, Serial#: 11
    public void ldq(final GPR rs1, final GPR rs2, final QFPR rd) {
        int instruction = 0xC1100000;
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (rs2.value() & 0x1f);
        instruction |= (((rd.value() >>> 2) & 0x7) << 27) | (((rd.value() >>> 5) & 0x1) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code ldq  }<i>rs1</i>, <i>simm13</i>, <i>rd</i>
     * Example disassembly syntax: {@code ldq           [%g0 + -4096], %f0}
     * <p>
     * Constraint: {@code -4096 <= simm13 && simm13 <= 4095}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.25"
     */
    // Template#: 12, Serial#: 12
    public void ldq(final GPR rs1, final int simm13, final QFPR rd) {
        int instruction = 0xC1102000;
        checkConstraint(-4096 <= simm13 && simm13 <= 4095, "-4096 <= simm13 && simm13 <= 4095");
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (simm13 & 0x1fff);
        instruction |= (((rd.value() >>> 2) & 0x7) << 27) | (((rd.value() >>> 5) & 0x1) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code ldx  }<i>rs1</i>, <i>rs2</i>
     * Example disassembly syntax: {@code ldx           [%g0 + %g0], %fsr}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.25"
     */
    // Template#: 13, Serial#: 13
    public void ldx_fsr(final GPR rs1, final GPR rs2) {
        int instruction = 0xC3080000;
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (rs2.value() & 0x1f);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code ldx  }<i>rs1</i>, <i>simm13</i>
     * Example disassembly syntax: {@code ldx           [%g0 + -4096], %fsr}
     * <p>
     * Constraint: {@code -4096 <= simm13 && simm13 <= 4095}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.25"
     */
    // Template#: 14, Serial#: 14
    public void ldx_fsr(final GPR rs1, final int simm13) {
        int instruction = 0xC3082000;
        checkConstraint(-4096 <= simm13 && simm13 <= 4095, "-4096 <= simm13 && simm13 <= 4095");
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (simm13 & 0x1fff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code ld  }<i>rs1</i>, <i>rs2</i>
     * Example disassembly syntax: {@code ld            [%g0 + %g0], %fsr}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.25"
     */
    // Template#: 15, Serial#: 15
    public void ld_fsr(final GPR rs1, final GPR rs2) {
        int instruction = 0xC1080000;
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (rs2.value() & 0x1f);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code ld  }<i>rs1</i>, <i>simm13</i>
     * Example disassembly syntax: {@code ld            [%g0 + -4096], %fsr}
     * <p>
     * Constraint: {@code -4096 <= simm13 && simm13 <= 4095}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.25"
     */
    // Template#: 16, Serial#: 16
    public void ld_fsr(final GPR rs1, final int simm13) {
        int instruction = 0xC1082000;
        checkConstraint(-4096 <= simm13 && simm13 <= 4095, "-4096 <= simm13 && simm13 <= 4095");
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (simm13 & 0x1fff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code swap  }<i>rs1</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code swap          [%g0 + %g0], %g0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.25"
     */
    // Template#: 17, Serial#: 17
    public void swap(final GPR rs1, final GPR rs2, final GPR rd) {
        int instruction = 0xC0780000;
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (rs2.value() & 0x1f);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code swap  }<i>rs1</i>, <i>simm13</i>, <i>rd</i>
     * Example disassembly syntax: {@code swap          [%g0 + -4096], %g0}
     * <p>
     * Constraint: {@code -4096 <= simm13 && simm13 <= 4095}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.25"
     */
    // Template#: 18, Serial#: 18
    public void swap(final GPR rs1, final int simm13, final GPR rd) {
        int instruction = 0xC0782000;
        checkConstraint(-4096 <= simm13 && simm13 <= 4095, "-4096 <= simm13 && simm13 <= 4095");
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (simm13 & 0x1fff);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code lda  }<i>rs1</i>, <i>rs2</i>, <i>immAsi</i>, <i>rd</i>
     * Example disassembly syntax: {@code lda           [%g0 + %g0] 0x0, %f0}
     * <p>
     * Constraint: {@code 0 <= immAsi && immAsi <= 255}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.26"
     */
    // Template#: 19, Serial#: 19
    public void lda(final GPR rs1, final GPR rs2, final int immAsi, final SFPR rd) {
        int instruction = 0xC1800000;
        checkConstraint(0 <= immAsi && immAsi <= 255, "0 <= immAsi && immAsi <= 255");
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (rs2.value() & 0x1f);
        instruction |= ((immAsi & 0xff) << 5);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code lda  }<i>rs1</i>, <i>simm13</i>, <i>rd</i>
     * Example disassembly syntax: {@code lda           [%g0 + -4096] %asi, %f0}
     * <p>
     * Constraint: {@code -4096 <= simm13 && simm13 <= 4095}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.26"
     */
    // Template#: 20, Serial#: 20
    public void lda(final GPR rs1, final int simm13, final SFPR rd) {
        int instruction = 0xC1802000;
        checkConstraint(-4096 <= simm13 && simm13 <= 4095, "-4096 <= simm13 && simm13 <= 4095");
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (simm13 & 0x1fff);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code ldda  }<i>rs1</i>, <i>rs2</i>, <i>immAsi</i>, <i>rd</i>
     * Example disassembly syntax: {@code ldda          [%g0 + %g0] 0x0, %f0}
     * <p>
     * Constraint: {@code 0 <= immAsi && immAsi <= 255}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.26"
     */
    // Template#: 21, Serial#: 21
    public void ldda(final GPR rs1, final GPR rs2, final int immAsi, final DFPR rd) {
        int instruction = 0xC1980000;
        checkConstraint(0 <= immAsi && immAsi <= 255, "0 <= immAsi && immAsi <= 255");
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (rs2.value() & 0x1f);
        instruction |= ((immAsi & 0xff) << 5);
        instruction |= (((rd.value() >>> 1) & 0xf) << 26) | (((rd.value() >>> 5) & 0x1) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code ldda  }<i>rs1</i>, <i>simm13</i>, <i>rd</i>
     * Example disassembly syntax: {@code ldda          [%g0 + -4096] %asi, %f0}
     * <p>
     * Constraint: {@code -4096 <= simm13 && simm13 <= 4095}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.26"
     */
    // Template#: 22, Serial#: 22
    public void ldda(final GPR rs1, final int simm13, final DFPR rd) {
        int instruction = 0xC1982000;
        checkConstraint(-4096 <= simm13 && simm13 <= 4095, "-4096 <= simm13 && simm13 <= 4095");
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (simm13 & 0x1fff);
        instruction |= (((rd.value() >>> 1) & 0xf) << 26) | (((rd.value() >>> 5) & 0x1) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code ldqa  }<i>rs1</i>, <i>rs2</i>, <i>immAsi</i>, <i>rd</i>
     * Example disassembly syntax: {@code ldqa          [%g0 + %g0] 0x0, %f0}
     * <p>
     * Constraint: {@code 0 <= immAsi && immAsi <= 255}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.26"
     */
    // Template#: 23, Serial#: 23
    public void ldqa(final GPR rs1, final GPR rs2, final int immAsi, final QFPR rd) {
        int instruction = 0xC1900000;
        checkConstraint(0 <= immAsi && immAsi <= 255, "0 <= immAsi && immAsi <= 255");
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (rs2.value() & 0x1f);
        instruction |= ((immAsi & 0xff) << 5);
        instruction |= (((rd.value() >>> 2) & 0x7) << 27) | (((rd.value() >>> 5) & 0x1) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code ldqa  }<i>rs1</i>, <i>simm13</i>, <i>rd</i>
     * Example disassembly syntax: {@code ldqa          [%g0 + -4096] %asi, %f0}
     * <p>
     * Constraint: {@code -4096 <= simm13 && simm13 <= 4095}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.26"
     */
    // Template#: 24, Serial#: 24
    public void ldqa(final GPR rs1, final int simm13, final QFPR rd) {
        int instruction = 0xC1902000;
        checkConstraint(-4096 <= simm13 && simm13 <= 4095, "-4096 <= simm13 && simm13 <= 4095");
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (simm13 & 0x1fff);
        instruction |= (((rd.value() >>> 2) & 0x7) << 27) | (((rd.value() >>> 5) & 0x1) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code ldsb  }<i>rs1</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code ldsb          [%g0 + %g0], %g0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.27"
     */
    // Template#: 25, Serial#: 25
    public void ldsb(final GPR rs1, final GPR rs2, final GPR rd) {
        int instruction = 0xC0480000;
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (rs2.value() & 0x1f);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code ldsb  }<i>rs1</i>, <i>simm13</i>, <i>rd</i>
     * Example disassembly syntax: {@code ldsb          [%g0 + -4096], %g0}
     * <p>
     * Constraint: {@code -4096 <= simm13 && simm13 <= 4095}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.27"
     */
    // Template#: 26, Serial#: 26
    public void ldsb(final GPR rs1, final int simm13, final GPR rd) {
        int instruction = 0xC0482000;
        checkConstraint(-4096 <= simm13 && simm13 <= 4095, "-4096 <= simm13 && simm13 <= 4095");
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (simm13 & 0x1fff);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code ldsh  }<i>rs1</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code ldsh          [%g0 + %g0], %g0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.27"
     */
    // Template#: 27, Serial#: 27
    public void ldsh(final GPR rs1, final GPR rs2, final GPR rd) {
        int instruction = 0xC0500000;
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (rs2.value() & 0x1f);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code ldsh  }<i>rs1</i>, <i>simm13</i>, <i>rd</i>
     * Example disassembly syntax: {@code ldsh          [%g0 + -4096], %g0}
     * <p>
     * Constraint: {@code -4096 <= simm13 && simm13 <= 4095}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.27"
     */
    // Template#: 28, Serial#: 28
    public void ldsh(final GPR rs1, final int simm13, final GPR rd) {
        int instruction = 0xC0502000;
        checkConstraint(-4096 <= simm13 && simm13 <= 4095, "-4096 <= simm13 && simm13 <= 4095");
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (simm13 & 0x1fff);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code ldsw  }<i>rs1</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code ldsw          [%g0 + %g0], %g0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.27"
     */
    // Template#: 29, Serial#: 29
    public void ldsw(final GPR rs1, final GPR rs2, final GPR rd) {
        int instruction = 0xC0400000;
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (rs2.value() & 0x1f);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code ldsw  }<i>rs1</i>, <i>simm13</i>, <i>rd</i>
     * Example disassembly syntax: {@code ldsw          [%g0 + -4096], %g0}
     * <p>
     * Constraint: {@code -4096 <= simm13 && simm13 <= 4095}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.27"
     */
    // Template#: 30, Serial#: 30
    public void ldsw(final GPR rs1, final int simm13, final GPR rd) {
        int instruction = 0xC0402000;
        checkConstraint(-4096 <= simm13 && simm13 <= 4095, "-4096 <= simm13 && simm13 <= 4095");
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (simm13 & 0x1fff);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code ldub  }<i>rs1</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code ldub          [%g0 + %g0], %g0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.27"
     */
    // Template#: 31, Serial#: 31
    public void ldub(final GPR rs1, final GPR rs2, final GPR rd) {
        int instruction = 0xC0080000;
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (rs2.value() & 0x1f);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code ldub  }<i>rs1</i>, <i>simm13</i>, <i>rd</i>
     * Example disassembly syntax: {@code ldub          [%g0 + -4096], %g0}
     * <p>
     * Constraint: {@code -4096 <= simm13 && simm13 <= 4095}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.27"
     */
    // Template#: 32, Serial#: 32
    public void ldub(final GPR rs1, final int simm13, final GPR rd) {
        int instruction = 0xC0082000;
        checkConstraint(-4096 <= simm13 && simm13 <= 4095, "-4096 <= simm13 && simm13 <= 4095");
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (simm13 & 0x1fff);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code lduh  }<i>rs1</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code lduh          [%g0 + %g0], %g0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.27"
     */
    // Template#: 33, Serial#: 33
    public void lduh(final GPR rs1, final GPR rs2, final GPR rd) {
        int instruction = 0xC0100000;
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (rs2.value() & 0x1f);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code lduh  }<i>rs1</i>, <i>simm13</i>, <i>rd</i>
     * Example disassembly syntax: {@code lduh          [%g0 + -4096], %g0}
     * <p>
     * Constraint: {@code -4096 <= simm13 && simm13 <= 4095}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.27"
     */
    // Template#: 34, Serial#: 34
    public void lduh(final GPR rs1, final int simm13, final GPR rd) {
        int instruction = 0xC0102000;
        checkConstraint(-4096 <= simm13 && simm13 <= 4095, "-4096 <= simm13 && simm13 <= 4095");
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (simm13 & 0x1fff);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code lduw  }<i>rs1</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code lduw          [%g0 + %g0], %g0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.27"
     */
    // Template#: 35, Serial#: 35
    public void lduw(final GPR rs1, final GPR rs2, final GPR rd) {
        int instruction = 0xC0000000;
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (rs2.value() & 0x1f);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code lduw  }<i>rs1</i>, <i>simm13</i>, <i>rd</i>
     * Example disassembly syntax: {@code lduw          [%g0 + -4096], %g0}
     * <p>
     * Constraint: {@code -4096 <= simm13 && simm13 <= 4095}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.27"
     */
    // Template#: 36, Serial#: 36
    public void lduw(final GPR rs1, final int simm13, final GPR rd) {
        int instruction = 0xC0002000;
        checkConstraint(-4096 <= simm13 && simm13 <= 4095, "-4096 <= simm13 && simm13 <= 4095");
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (simm13 & 0x1fff);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code ldx  }<i>rs1</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code ldx           [%g0 + %g0], %g0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.27"
     */
    // Template#: 37, Serial#: 37
    public void ldx(final GPR rs1, final GPR rs2, final GPR rd) {
        int instruction = 0xC0580000;
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (rs2.value() & 0x1f);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code ldx  }<i>rs1</i>, <i>simm13</i>, <i>rd</i>
     * Example disassembly syntax: {@code ldx           [%g0 + -4096], %g0}
     * <p>
     * Constraint: {@code -4096 <= simm13 && simm13 <= 4095}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.27"
     */
    // Template#: 38, Serial#: 38
    public void ldx(final GPR rs1, final int simm13, final GPR rd) {
        int instruction = 0xC0582000;
        checkConstraint(-4096 <= simm13 && simm13 <= 4095, "-4096 <= simm13 && simm13 <= 4095");
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (simm13 & 0x1fff);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code ldd  }<i>rs1</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code ldd           [%g0 + %g0], %g0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.27"
     */
    // Template#: 39, Serial#: 39
    public void ldd(final GPR rs1, final GPR rs2, final GPR.Even rd) {
        int instruction = 0xC0180000;
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (rs2.value() & 0x1f);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code ldd  }<i>rs1</i>, <i>simm13</i>, <i>rd</i>
     * Example disassembly syntax: {@code ldd           [%g0 + -4096], %g0}
     * <p>
     * Constraint: {@code -4096 <= simm13 && simm13 <= 4095}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.27"
     */
    // Template#: 40, Serial#: 40
    public void ldd(final GPR rs1, final int simm13, final GPR.Even rd) {
        int instruction = 0xC0182000;
        checkConstraint(-4096 <= simm13 && simm13 <= 4095, "-4096 <= simm13 && simm13 <= 4095");
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (simm13 & 0x1fff);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code ldsba  }<i>rs1</i>, <i>rs2</i>, <i>immAsi</i>, <i>rd</i>
     * Example disassembly syntax: {@code ldsba         [%g0 + %g0] 0x0, %g0}
     * <p>
     * Constraint: {@code 0 <= immAsi && immAsi <= 255}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.28"
     */
    // Template#: 41, Serial#: 41
    public void ldsba(final GPR rs1, final GPR rs2, final int immAsi, final GPR rd) {
        int instruction = 0xC0C80000;
        checkConstraint(0 <= immAsi && immAsi <= 255, "0 <= immAsi && immAsi <= 255");
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (rs2.value() & 0x1f);
        instruction |= ((immAsi & 0xff) << 5);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code ldsba  }<i>rs1</i>, <i>simm13</i>, <i>rd</i>
     * Example disassembly syntax: {@code ldsba         [%g0 + -4096] %asi, %g0}
     * <p>
     * Constraint: {@code -4096 <= simm13 && simm13 <= 4095}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.28"
     */
    // Template#: 42, Serial#: 42
    public void ldsba(final GPR rs1, final int simm13, final GPR rd) {
        int instruction = 0xC0C82000;
        checkConstraint(-4096 <= simm13 && simm13 <= 4095, "-4096 <= simm13 && simm13 <= 4095");
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (simm13 & 0x1fff);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code ldsha  }<i>rs1</i>, <i>rs2</i>, <i>immAsi</i>, <i>rd</i>
     * Example disassembly syntax: {@code ldsha         [%g0 + %g0] 0x0, %g0}
     * <p>
     * Constraint: {@code 0 <= immAsi && immAsi <= 255}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.28"
     */
    // Template#: 43, Serial#: 43
    public void ldsha(final GPR rs1, final GPR rs2, final int immAsi, final GPR rd) {
        int instruction = 0xC0D00000;
        checkConstraint(0 <= immAsi && immAsi <= 255, "0 <= immAsi && immAsi <= 255");
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (rs2.value() & 0x1f);
        instruction |= ((immAsi & 0xff) << 5);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code ldsha  }<i>rs1</i>, <i>simm13</i>, <i>rd</i>
     * Example disassembly syntax: {@code ldsha         [%g0 + -4096] %asi, %g0}
     * <p>
     * Constraint: {@code -4096 <= simm13 && simm13 <= 4095}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.28"
     */
    // Template#: 44, Serial#: 44
    public void ldsha(final GPR rs1, final int simm13, final GPR rd) {
        int instruction = 0xC0D02000;
        checkConstraint(-4096 <= simm13 && simm13 <= 4095, "-4096 <= simm13 && simm13 <= 4095");
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (simm13 & 0x1fff);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code ldswa  }<i>rs1</i>, <i>rs2</i>, <i>immAsi</i>, <i>rd</i>
     * Example disassembly syntax: {@code ldswa         [%g0 + %g0] 0x0, %g0}
     * <p>
     * Constraint: {@code 0 <= immAsi && immAsi <= 255}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.28"
     */
    // Template#: 45, Serial#: 45
    public void ldswa(final GPR rs1, final GPR rs2, final int immAsi, final GPR rd) {
        int instruction = 0xC0C00000;
        checkConstraint(0 <= immAsi && immAsi <= 255, "0 <= immAsi && immAsi <= 255");
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (rs2.value() & 0x1f);
        instruction |= ((immAsi & 0xff) << 5);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code ldswa  }<i>rs1</i>, <i>simm13</i>, <i>rd</i>
     * Example disassembly syntax: {@code ldswa         [%g0 + -4096] %asi, %g0}
     * <p>
     * Constraint: {@code -4096 <= simm13 && simm13 <= 4095}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.28"
     */
    // Template#: 46, Serial#: 46
    public void ldswa(final GPR rs1, final int simm13, final GPR rd) {
        int instruction = 0xC0C02000;
        checkConstraint(-4096 <= simm13 && simm13 <= 4095, "-4096 <= simm13 && simm13 <= 4095");
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (simm13 & 0x1fff);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code lduba  }<i>rs1</i>, <i>rs2</i>, <i>immAsi</i>, <i>rd</i>
     * Example disassembly syntax: {@code lduba         [%g0 + %g0] 0x0, %g0}
     * <p>
     * Constraint: {@code 0 <= immAsi && immAsi <= 255}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.28"
     */
    // Template#: 47, Serial#: 47
    public void lduba(final GPR rs1, final GPR rs2, final int immAsi, final GPR rd) {
        int instruction = 0xC0880000;
        checkConstraint(0 <= immAsi && immAsi <= 255, "0 <= immAsi && immAsi <= 255");
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (rs2.value() & 0x1f);
        instruction |= ((immAsi & 0xff) << 5);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code lduba  }<i>rs1</i>, <i>simm13</i>, <i>rd</i>
     * Example disassembly syntax: {@code lduba         [%g0 + -4096] %asi, %g0}
     * <p>
     * Constraint: {@code -4096 <= simm13 && simm13 <= 4095}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.28"
     */
    // Template#: 48, Serial#: 48
    public void lduba(final GPR rs1, final int simm13, final GPR rd) {
        int instruction = 0xC0882000;
        checkConstraint(-4096 <= simm13 && simm13 <= 4095, "-4096 <= simm13 && simm13 <= 4095");
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (simm13 & 0x1fff);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code lduha  }<i>rs1</i>, <i>rs2</i>, <i>immAsi</i>, <i>rd</i>
     * Example disassembly syntax: {@code lduha         [%g0 + %g0] 0x0, %g0}
     * <p>
     * Constraint: {@code 0 <= immAsi && immAsi <= 255}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.28"
     */
    // Template#: 49, Serial#: 49
    public void lduha(final GPR rs1, final GPR rs2, final int immAsi, final GPR rd) {
        int instruction = 0xC0900000;
        checkConstraint(0 <= immAsi && immAsi <= 255, "0 <= immAsi && immAsi <= 255");
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (rs2.value() & 0x1f);
        instruction |= ((immAsi & 0xff) << 5);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code lduha  }<i>rs1</i>, <i>simm13</i>, <i>rd</i>
     * Example disassembly syntax: {@code lduha         [%g0 + -4096] %asi, %g0}
     * <p>
     * Constraint: {@code -4096 <= simm13 && simm13 <= 4095}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.28"
     */
    // Template#: 50, Serial#: 50
    public void lduha(final GPR rs1, final int simm13, final GPR rd) {
        int instruction = 0xC0902000;
        checkConstraint(-4096 <= simm13 && simm13 <= 4095, "-4096 <= simm13 && simm13 <= 4095");
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (simm13 & 0x1fff);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code lduwa  }<i>rs1</i>, <i>rs2</i>, <i>immAsi</i>, <i>rd</i>
     * Example disassembly syntax: {@code lduwa         [%g0 + %g0] 0x0, %g0}
     * <p>
     * Constraint: {@code 0 <= immAsi && immAsi <= 255}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.28"
     */
    // Template#: 51, Serial#: 51
    public void lduwa(final GPR rs1, final GPR rs2, final int immAsi, final GPR rd) {
        int instruction = 0xC0800000;
        checkConstraint(0 <= immAsi && immAsi <= 255, "0 <= immAsi && immAsi <= 255");
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (rs2.value() & 0x1f);
        instruction |= ((immAsi & 0xff) << 5);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code lduwa  }<i>rs1</i>, <i>simm13</i>, <i>rd</i>
     * Example disassembly syntax: {@code lduwa         [%g0 + -4096] %asi, %g0}
     * <p>
     * Constraint: {@code -4096 <= simm13 && simm13 <= 4095}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.28"
     */
    // Template#: 52, Serial#: 52
    public void lduwa(final GPR rs1, final int simm13, final GPR rd) {
        int instruction = 0xC0802000;
        checkConstraint(-4096 <= simm13 && simm13 <= 4095, "-4096 <= simm13 && simm13 <= 4095");
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (simm13 & 0x1fff);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code ldxa  }<i>rs1</i>, <i>rs2</i>, <i>immAsi</i>, <i>rd</i>
     * Example disassembly syntax: {@code ldxa          [%g0 + %g0] 0x0, %g0}
     * <p>
     * Constraint: {@code 0 <= immAsi && immAsi <= 255}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.28"
     */
    // Template#: 53, Serial#: 53
    public void ldxa(final GPR rs1, final GPR rs2, final int immAsi, final GPR rd) {
        int instruction = 0xC0D80000;
        checkConstraint(0 <= immAsi && immAsi <= 255, "0 <= immAsi && immAsi <= 255");
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (rs2.value() & 0x1f);
        instruction |= ((immAsi & 0xff) << 5);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code ldxa  }<i>rs1</i>, <i>simm13</i>, <i>rd</i>
     * Example disassembly syntax: {@code ldxa          [%g0 + -4096] %asi, %g0}
     * <p>
     * Constraint: {@code -4096 <= simm13 && simm13 <= 4095}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.28"
     */
    // Template#: 54, Serial#: 54
    public void ldxa(final GPR rs1, final int simm13, final GPR rd) {
        int instruction = 0xC0D82000;
        checkConstraint(-4096 <= simm13 && simm13 <= 4095, "-4096 <= simm13 && simm13 <= 4095");
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (simm13 & 0x1fff);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code ldda  }<i>rs1</i>, <i>rs2</i>, <i>immAsi</i>, <i>rd</i>
     * Example disassembly syntax: {@code ldda          [%g0 + %g0] 0x0, %g0}
     * <p>
     * Constraint: {@code 0 <= immAsi && immAsi <= 255}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.28"
     */
    // Template#: 55, Serial#: 55
    public void ldda(final GPR rs1, final GPR rs2, final int immAsi, final GPR.Even rd) {
        int instruction = 0xC0980000;
        checkConstraint(0 <= immAsi && immAsi <= 255, "0 <= immAsi && immAsi <= 255");
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (rs2.value() & 0x1f);
        instruction |= ((immAsi & 0xff) << 5);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code ldda  }<i>rs1</i>, <i>simm13</i>, <i>rd</i>
     * Example disassembly syntax: {@code ldda          [%g0 + -4096] %asi, %g0}
     * <p>
     * Constraint: {@code -4096 <= simm13 && simm13 <= 4095}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.28"
     */
    // Template#: 56, Serial#: 56
    public void ldda(final GPR rs1, final int simm13, final GPR.Even rd) {
        int instruction = 0xC0982000;
        checkConstraint(-4096 <= simm13 && simm13 <= 4095, "-4096 <= simm13 && simm13 <= 4095");
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (simm13 & 0x1fff);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code ldstub  }<i>rs1</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code ldstub        [%g0 + %g0], %g0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.29"
     */
    // Template#: 57, Serial#: 57
    public void ldstub(final GPR rs1, final GPR rs2, final GPR rd) {
        int instruction = 0xC0680000;
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (rs2.value() & 0x1f);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code ldstub  }<i>rs1</i>, <i>simm13</i>, <i>rd</i>
     * Example disassembly syntax: {@code ldstub        [%g0 + -4096], %g0}
     * <p>
     * Constraint: {@code -4096 <= simm13 && simm13 <= 4095}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.29"
     */
    // Template#: 58, Serial#: 58
    public void ldstub(final GPR rs1, final int simm13, final GPR rd) {
        int instruction = 0xC0682000;
        checkConstraint(-4096 <= simm13 && simm13 <= 4095, "-4096 <= simm13 && simm13 <= 4095");
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (simm13 & 0x1fff);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code ldstuba  }<i>rs1</i>, <i>rs2</i>, <i>immAsi</i>, <i>rd</i>
     * Example disassembly syntax: {@code ldstuba       [%g0 + %g0] 0x0, %g0}
     * <p>
     * Constraint: {@code 0 <= immAsi && immAsi <= 255}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.30"
     */
    // Template#: 59, Serial#: 59
    public void ldstuba(final GPR rs1, final GPR rs2, final int immAsi, final GPR rd) {
        int instruction = 0xC0E80000;
        checkConstraint(0 <= immAsi && immAsi <= 255, "0 <= immAsi && immAsi <= 255");
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (rs2.value() & 0x1f);
        instruction |= ((immAsi & 0xff) << 5);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code ldstuba  }<i>rs1</i>, <i>simm13</i>, <i>rd</i>
     * Example disassembly syntax: {@code ldstuba       [%g0 + -4096] %asi, %g0}
     * <p>
     * Constraint: {@code -4096 <= simm13 && simm13 <= 4095}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.30"
     */
    // Template#: 60, Serial#: 60
    public void ldstuba(final GPR rs1, final int simm13, final GPR rd) {
        int instruction = 0xC0E82000;
        checkConstraint(-4096 <= simm13 && simm13 <= 4095, "-4096 <= simm13 && simm13 <= 4095");
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (simm13 & 0x1fff);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code prefetch  }<i>rs1</i>, <i>rs2</i>, <i>fcn</i>
     * Example disassembly syntax: {@code prefetch      [%g0 + %g0], 0x0}
     * <p>
     * Constraint: {@code 0 <= fcn && fcn <= 31}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.41"
     */
    // Template#: 61, Serial#: 61
    public void prefetch(final GPR rs1, final GPR rs2, final int fcn) {
        int instruction = 0xC1680000;
        checkConstraint(0 <= fcn && fcn <= 31, "0 <= fcn && fcn <= 31");
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (rs2.value() & 0x1f);
        instruction |= ((fcn & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code prefetch  }<i>rs1</i>, <i>simm13</i>, <i>fcn</i>
     * Example disassembly syntax: {@code prefetch      [%g0 + -4096], 0x0}
     * <p>
     * Constraint: {@code -4096 <= simm13 && simm13 <= 4095}<br />
     * Constraint: {@code 0 <= fcn && fcn <= 31}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.41"
     */
    // Template#: 62, Serial#: 62
    public void prefetch(final GPR rs1, final int simm13, final int fcn) {
        int instruction = 0xC1682000;
        checkConstraint(-4096 <= simm13 && simm13 <= 4095, "-4096 <= simm13 && simm13 <= 4095");
        checkConstraint(0 <= fcn && fcn <= 31, "0 <= fcn && fcn <= 31");
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (simm13 & 0x1fff);
        instruction |= ((fcn & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code prefetcha  }<i>rs1</i>, <i>rs2</i>, <i>immAsi</i>, <i>fcn</i>
     * Example disassembly syntax: {@code prefetcha     [%g0 + %g0] 0x0, 0x0}
     * <p>
     * Constraint: {@code 0 <= immAsi && immAsi <= 255}<br />
     * Constraint: {@code 0 <= fcn && fcn <= 31}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.41"
     */
    // Template#: 63, Serial#: 63
    public void prefetcha(final GPR rs1, final GPR rs2, final int immAsi, final int fcn) {
        int instruction = 0xC1E80000;
        checkConstraint(0 <= immAsi && immAsi <= 255, "0 <= immAsi && immAsi <= 255");
        checkConstraint(0 <= fcn && fcn <= 31, "0 <= fcn && fcn <= 31");
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (rs2.value() & 0x1f);
        instruction |= ((immAsi & 0xff) << 5);
        instruction |= ((fcn & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code prefetcha  }<i>rs1</i>, <i>simm13</i>, <i>fcn</i>
     * Example disassembly syntax: {@code prefetcha     [%g0 + -4096] %asi, 0x0}
     * <p>
     * Constraint: {@code -4096 <= simm13 && simm13 <= 4095}<br />
     * Constraint: {@code 0 <= fcn && fcn <= 31}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.41"
     */
    // Template#: 64, Serial#: 64
    public void prefetcha(final GPR rs1, final int simm13, final int fcn) {
        int instruction = 0xC1E82000;
        checkConstraint(-4096 <= simm13 && simm13 <= 4095, "-4096 <= simm13 && simm13 <= 4095");
        checkConstraint(0 <= fcn && fcn <= 31, "0 <= fcn && fcn <= 31");
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (simm13 & 0x1fff);
        instruction |= ((fcn & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code st  }<i>rd</i>, <i>rs1</i>, <i>rs2</i>
     * Example disassembly syntax: {@code st            %f0, [%g0 + %g0]}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.51"
     */
    // Template#: 65, Serial#: 65
    public void st(final SFPR rd, final GPR rs1, final GPR rs2) {
        int instruction = 0xC1200000;
        instruction |= ((rd.value() & 0x1f) << 25);
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (rs2.value() & 0x1f);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code st  }<i>rd</i>, <i>rs1</i>, <i>simm13</i>
     * Example disassembly syntax: {@code st            %f0, [%g0 + -4096]}
     * <p>
     * Constraint: {@code -4096 <= simm13 && simm13 <= 4095}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.51"
     */
    // Template#: 66, Serial#: 66
    public void st(final SFPR rd, final GPR rs1, final int simm13) {
        int instruction = 0xC1202000;
        checkConstraint(-4096 <= simm13 && simm13 <= 4095, "-4096 <= simm13 && simm13 <= 4095");
        instruction |= ((rd.value() & 0x1f) << 25);
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (simm13 & 0x1fff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code std  }<i>rd</i>, <i>rs1</i>, <i>rs2</i>
     * Example disassembly syntax: {@code std           %f0, [%g0 + %g0]}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.51"
     */
    // Template#: 67, Serial#: 67
    public void std(final DFPR rd, final GPR rs1, final GPR rs2) {
        int instruction = 0xC1380000;
        instruction |= (((rd.value() >>> 1) & 0xf) << 26) | (((rd.value() >>> 5) & 0x1) << 25);
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (rs2.value() & 0x1f);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code std  }<i>rd</i>, <i>rs1</i>, <i>simm13</i>
     * Example disassembly syntax: {@code std           %f0, [%g0 + -4096]}
     * <p>
     * Constraint: {@code -4096 <= simm13 && simm13 <= 4095}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.51"
     */
    // Template#: 68, Serial#: 68
    public void std(final DFPR rd, final GPR rs1, final int simm13) {
        int instruction = 0xC1382000;
        checkConstraint(-4096 <= simm13 && simm13 <= 4095, "-4096 <= simm13 && simm13 <= 4095");
        instruction |= (((rd.value() >>> 1) & 0xf) << 26) | (((rd.value() >>> 5) & 0x1) << 25);
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (simm13 & 0x1fff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code stq  }<i>rd</i>, <i>rs1</i>, <i>rs2</i>
     * Example disassembly syntax: {@code stq           %f0, [%g0 + %g0]}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.51"
     */
    // Template#: 69, Serial#: 69
    public void stq(final QFPR rd, final GPR rs1, final GPR rs2) {
        int instruction = 0xC1300000;
        instruction |= (((rd.value() >>> 2) & 0x7) << 27) | (((rd.value() >>> 5) & 0x1) << 25);
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (rs2.value() & 0x1f);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code stq  }<i>rd</i>, <i>rs1</i>, <i>simm13</i>
     * Example disassembly syntax: {@code stq           %f0, [%g0 + -4096]}
     * <p>
     * Constraint: {@code -4096 <= simm13 && simm13 <= 4095}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.51"
     */
    // Template#: 70, Serial#: 70
    public void stq(final QFPR rd, final GPR rs1, final int simm13) {
        int instruction = 0xC1302000;
        checkConstraint(-4096 <= simm13 && simm13 <= 4095, "-4096 <= simm13 && simm13 <= 4095");
        instruction |= (((rd.value() >>> 2) & 0x7) << 27) | (((rd.value() >>> 5) & 0x1) << 25);
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (simm13 & 0x1fff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code stx  }<i>rs1</i>, <i>rs2</i>
     * Example disassembly syntax: {@code stx            %fsr, [%g0 + %g0]}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.51"
     */
    // Template#: 71, Serial#: 71
    public void stx_fsr(final GPR rs1, final GPR rs2) {
        int instruction = 0xC3280000;
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (rs2.value() & 0x1f);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code stx  }<i>rs1</i>, <i>simm13</i>
     * Example disassembly syntax: {@code stx            %fsr, [%g0 + -4096]}
     * <p>
     * Constraint: {@code -4096 <= simm13 && simm13 <= 4095}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.51"
     */
    // Template#: 72, Serial#: 72
    public void stx_fsr(final GPR rs1, final int simm13) {
        int instruction = 0xC3282000;
        checkConstraint(-4096 <= simm13 && simm13 <= 4095, "-4096 <= simm13 && simm13 <= 4095");
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (simm13 & 0x1fff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code st  }<i>rs1</i>, <i>rs2</i>
     * Example disassembly syntax: {@code st             %fsr, [%g0 + %g0]}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.51"
     */
    // Template#: 73, Serial#: 73
    public void st_fsr(final GPR rs1, final GPR rs2) {
        int instruction = 0xC1280000;
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (rs2.value() & 0x1f);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code st  }<i>rs1</i>, <i>simm13</i>
     * Example disassembly syntax: {@code st             %fsr, [%g0 + -4096]}
     * <p>
     * Constraint: {@code -4096 <= simm13 && simm13 <= 4095}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.51"
     */
    // Template#: 74, Serial#: 74
    public void st_fsr(final GPR rs1, final int simm13) {
        int instruction = 0xC1282000;
        checkConstraint(-4096 <= simm13 && simm13 <= 4095, "-4096 <= simm13 && simm13 <= 4095");
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (simm13 & 0x1fff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code sta  }<i>rd</i>, <i>rs1</i>, <i>rs2</i>, <i>immAsi</i>
     * Example disassembly syntax: {@code sta           %f0, [%g0 + %g0]0x0}
     * <p>
     * Constraint: {@code 0 <= immAsi && immAsi <= 255}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.52"
     */
    // Template#: 75, Serial#: 75
    public void sta(final SFPR rd, final GPR rs1, final GPR rs2, final int immAsi) {
        int instruction = 0xC1A00000;
        checkConstraint(0 <= immAsi && immAsi <= 255, "0 <= immAsi && immAsi <= 255");
        instruction |= ((rd.value() & 0x1f) << 25);
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (rs2.value() & 0x1f);
        instruction |= ((immAsi & 0xff) << 5);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code sta  }<i>rd</i>, <i>rs1</i>, <i>simm13</i>
     * Example disassembly syntax: {@code sta           %f0, [%g0 + -4096] %asi}
     * <p>
     * Constraint: {@code -4096 <= simm13 && simm13 <= 4095}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.52"
     */
    // Template#: 76, Serial#: 76
    public void sta(final SFPR rd, final GPR rs1, final int simm13) {
        int instruction = 0xC1A02000;
        checkConstraint(-4096 <= simm13 && simm13 <= 4095, "-4096 <= simm13 && simm13 <= 4095");
        instruction |= ((rd.value() & 0x1f) << 25);
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (simm13 & 0x1fff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code stda  }<i>rd</i>, <i>rs1</i>, <i>rs2</i>, <i>immAsi</i>
     * Example disassembly syntax: {@code stda          %f0, [%g0 + %g0]0x0}
     * <p>
     * Constraint: {@code 0 <= immAsi && immAsi <= 255}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.52"
     */
    // Template#: 77, Serial#: 77
    public void stda(final DFPR rd, final GPR rs1, final GPR rs2, final int immAsi) {
        int instruction = 0xC1B80000;
        checkConstraint(0 <= immAsi && immAsi <= 255, "0 <= immAsi && immAsi <= 255");
        instruction |= (((rd.value() >>> 1) & 0xf) << 26) | (((rd.value() >>> 5) & 0x1) << 25);
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (rs2.value() & 0x1f);
        instruction |= ((immAsi & 0xff) << 5);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code stda  }<i>rd</i>, <i>rs1</i>, <i>simm13</i>
     * Example disassembly syntax: {@code stda          %f0, [%g0 + -4096] %asi}
     * <p>
     * Constraint: {@code -4096 <= simm13 && simm13 <= 4095}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.52"
     */
    // Template#: 78, Serial#: 78
    public void stda(final DFPR rd, final GPR rs1, final int simm13) {
        int instruction = 0xC1B82000;
        checkConstraint(-4096 <= simm13 && simm13 <= 4095, "-4096 <= simm13 && simm13 <= 4095");
        instruction |= (((rd.value() >>> 1) & 0xf) << 26) | (((rd.value() >>> 5) & 0x1) << 25);
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (simm13 & 0x1fff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code stqa  }<i>rd</i>, <i>rs1</i>, <i>rs2</i>, <i>immAsi</i>
     * Example disassembly syntax: {@code stqa          %f0, [%g0 + %g0]0x0}
     * <p>
     * Constraint: {@code 0 <= immAsi && immAsi <= 255}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.52"
     */
    // Template#: 79, Serial#: 79
    public void stqa(final QFPR rd, final GPR rs1, final GPR rs2, final int immAsi) {
        int instruction = 0xC1B00000;
        checkConstraint(0 <= immAsi && immAsi <= 255, "0 <= immAsi && immAsi <= 255");
        instruction |= (((rd.value() >>> 2) & 0x7) << 27) | (((rd.value() >>> 5) & 0x1) << 25);
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (rs2.value() & 0x1f);
        instruction |= ((immAsi & 0xff) << 5);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code stqa  }<i>rd</i>, <i>rs1</i>, <i>simm13</i>
     * Example disassembly syntax: {@code stqa          %f0, [%g0 + -4096] %asi}
     * <p>
     * Constraint: {@code -4096 <= simm13 && simm13 <= 4095}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.52"
     */
    // Template#: 80, Serial#: 80
    public void stqa(final QFPR rd, final GPR rs1, final int simm13) {
        int instruction = 0xC1B02000;
        checkConstraint(-4096 <= simm13 && simm13 <= 4095, "-4096 <= simm13 && simm13 <= 4095");
        instruction |= (((rd.value() >>> 2) & 0x7) << 27) | (((rd.value() >>> 5) & 0x1) << 25);
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (simm13 & 0x1fff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code stb  }<i>rd</i>, <i>rs1</i>, <i>rs2</i>
     * Example disassembly syntax: {@code stb           %g0, [%g0 + %g0]}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.53"
     */
    // Template#: 81, Serial#: 81
    public void stb(final GPR rd, final GPR rs1, final GPR rs2) {
        int instruction = 0xC0280000;
        instruction |= ((rd.value() & 0x1f) << 25);
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (rs2.value() & 0x1f);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code stb  }<i>rd</i>, <i>rs1</i>, <i>simm13</i>
     * Example disassembly syntax: {@code stb           %g0, [%g0 + -4096]}
     * <p>
     * Constraint: {@code -4096 <= simm13 && simm13 <= 4095}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.53"
     */
    // Template#: 82, Serial#: 82
    public void stb(final GPR rd, final GPR rs1, final int simm13) {
        int instruction = 0xC0282000;
        checkConstraint(-4096 <= simm13 && simm13 <= 4095, "-4096 <= simm13 && simm13 <= 4095");
        instruction |= ((rd.value() & 0x1f) << 25);
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (simm13 & 0x1fff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code sth  }<i>rd</i>, <i>rs1</i>, <i>rs2</i>
     * Example disassembly syntax: {@code sth           %g0, [%g0 + %g0]}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.53"
     */
    // Template#: 83, Serial#: 83
    public void sth(final GPR rd, final GPR rs1, final GPR rs2) {
        int instruction = 0xC0300000;
        instruction |= ((rd.value() & 0x1f) << 25);
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (rs2.value() & 0x1f);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code sth  }<i>rd</i>, <i>rs1</i>, <i>simm13</i>
     * Example disassembly syntax: {@code sth           %g0, [%g0 + -4096]}
     * <p>
     * Constraint: {@code -4096 <= simm13 && simm13 <= 4095}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.53"
     */
    // Template#: 84, Serial#: 84
    public void sth(final GPR rd, final GPR rs1, final int simm13) {
        int instruction = 0xC0302000;
        checkConstraint(-4096 <= simm13 && simm13 <= 4095, "-4096 <= simm13 && simm13 <= 4095");
        instruction |= ((rd.value() & 0x1f) << 25);
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (simm13 & 0x1fff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code stw  }<i>rd</i>, <i>rs1</i>, <i>rs2</i>
     * Example disassembly syntax: {@code stw           %g0, [%g0 + %g0]}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.53"
     */
    // Template#: 85, Serial#: 85
    public void stw(final GPR rd, final GPR rs1, final GPR rs2) {
        int instruction = 0xC0200000;
        instruction |= ((rd.value() & 0x1f) << 25);
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (rs2.value() & 0x1f);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code stw  }<i>rd</i>, <i>rs1</i>, <i>simm13</i>
     * Example disassembly syntax: {@code stw           %g0, [%g0 + -4096]}
     * <p>
     * Constraint: {@code -4096 <= simm13 && simm13 <= 4095}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.53"
     */
    // Template#: 86, Serial#: 86
    public void stw(final GPR rd, final GPR rs1, final int simm13) {
        int instruction = 0xC0202000;
        checkConstraint(-4096 <= simm13 && simm13 <= 4095, "-4096 <= simm13 && simm13 <= 4095");
        instruction |= ((rd.value() & 0x1f) << 25);
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (simm13 & 0x1fff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code stx  }<i>rd</i>, <i>rs1</i>, <i>rs2</i>
     * Example disassembly syntax: {@code stx           %g0, [%g0 + %g0]}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.53"
     */
    // Template#: 87, Serial#: 87
    public void stx(final GPR rd, final GPR rs1, final GPR rs2) {
        int instruction = 0xC0700000;
        instruction |= ((rd.value() & 0x1f) << 25);
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (rs2.value() & 0x1f);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code stx  }<i>rd</i>, <i>rs1</i>, <i>simm13</i>
     * Example disassembly syntax: {@code stx           %g0, [%g0 + -4096]}
     * <p>
     * Constraint: {@code -4096 <= simm13 && simm13 <= 4095}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.53"
     */
    // Template#: 88, Serial#: 88
    public void stx(final GPR rd, final GPR rs1, final int simm13) {
        int instruction = 0xC0702000;
        checkConstraint(-4096 <= simm13 && simm13 <= 4095, "-4096 <= simm13 && simm13 <= 4095");
        instruction |= ((rd.value() & 0x1f) << 25);
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (simm13 & 0x1fff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code std  }<i>rd</i>, <i>rs1</i>, <i>rs2</i>
     * Example disassembly syntax: {@code std           %g0, [%g0 + %g0]}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.53"
     */
    // Template#: 89, Serial#: 89
    public void std(final GPR.Even rd, final GPR rs1, final GPR rs2) {
        int instruction = 0xC0380000;
        instruction |= ((rd.value() & 0x1f) << 25);
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (rs2.value() & 0x1f);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code std  }<i>rd</i>, <i>rs1</i>, <i>simm13</i>
     * Example disassembly syntax: {@code std           %g0, [%g0 + -4096]}
     * <p>
     * Constraint: {@code -4096 <= simm13 && simm13 <= 4095}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.53"
     */
    // Template#: 90, Serial#: 90
    public void std(final GPR.Even rd, final GPR rs1, final int simm13) {
        int instruction = 0xC0382000;
        checkConstraint(-4096 <= simm13 && simm13 <= 4095, "-4096 <= simm13 && simm13 <= 4095");
        instruction |= ((rd.value() & 0x1f) << 25);
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (simm13 & 0x1fff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code stba  }<i>rd</i>, <i>rs1</i>, <i>rs2</i>, <i>immAsi</i>
     * Example disassembly syntax: {@code stba          %g0, [%g0 + %g0]0x0}
     * <p>
     * Constraint: {@code 0 <= immAsi && immAsi <= 255}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.54"
     */
    // Template#: 91, Serial#: 91
    public void stba(final GPR rd, final GPR rs1, final GPR rs2, final int immAsi) {
        int instruction = 0xC0A80000;
        checkConstraint(0 <= immAsi && immAsi <= 255, "0 <= immAsi && immAsi <= 255");
        instruction |= ((rd.value() & 0x1f) << 25);
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (rs2.value() & 0x1f);
        instruction |= ((immAsi & 0xff) << 5);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code stba  }<i>rd</i>, <i>rs1</i>, <i>simm13</i>
     * Example disassembly syntax: {@code stba          %g0, [%g0 + -4096] %asi}
     * <p>
     * Constraint: {@code -4096 <= simm13 && simm13 <= 4095}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.54"
     */
    // Template#: 92, Serial#: 92
    public void stba(final GPR rd, final GPR rs1, final int simm13) {
        int instruction = 0xC0A82000;
        checkConstraint(-4096 <= simm13 && simm13 <= 4095, "-4096 <= simm13 && simm13 <= 4095");
        instruction |= ((rd.value() & 0x1f) << 25);
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (simm13 & 0x1fff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code stha  }<i>rd</i>, <i>rs1</i>, <i>rs2</i>, <i>immAsi</i>
     * Example disassembly syntax: {@code stha          %g0, [%g0 + %g0]0x0}
     * <p>
     * Constraint: {@code 0 <= immAsi && immAsi <= 255}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.54"
     */
    // Template#: 93, Serial#: 93
    public void stha(final GPR rd, final GPR rs1, final GPR rs2, final int immAsi) {
        int instruction = 0xC0B00000;
        checkConstraint(0 <= immAsi && immAsi <= 255, "0 <= immAsi && immAsi <= 255");
        instruction |= ((rd.value() & 0x1f) << 25);
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (rs2.value() & 0x1f);
        instruction |= ((immAsi & 0xff) << 5);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code stha  }<i>rd</i>, <i>rs1</i>, <i>simm13</i>
     * Example disassembly syntax: {@code stha          %g0, [%g0 + -4096] %asi}
     * <p>
     * Constraint: {@code -4096 <= simm13 && simm13 <= 4095}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.54"
     */
    // Template#: 94, Serial#: 94
    public void stha(final GPR rd, final GPR rs1, final int simm13) {
        int instruction = 0xC0B02000;
        checkConstraint(-4096 <= simm13 && simm13 <= 4095, "-4096 <= simm13 && simm13 <= 4095");
        instruction |= ((rd.value() & 0x1f) << 25);
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (simm13 & 0x1fff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code stwa  }<i>rd</i>, <i>rs1</i>, <i>rs2</i>, <i>immAsi</i>
     * Example disassembly syntax: {@code stwa          %g0, [%g0 + %g0]0x0}
     * <p>
     * Constraint: {@code 0 <= immAsi && immAsi <= 255}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.54"
     */
    // Template#: 95, Serial#: 95
    public void stwa(final GPR rd, final GPR rs1, final GPR rs2, final int immAsi) {
        int instruction = 0xC0A00000;
        checkConstraint(0 <= immAsi && immAsi <= 255, "0 <= immAsi && immAsi <= 255");
        instruction |= ((rd.value() & 0x1f) << 25);
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (rs2.value() & 0x1f);
        instruction |= ((immAsi & 0xff) << 5);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code stwa  }<i>rd</i>, <i>rs1</i>, <i>simm13</i>
     * Example disassembly syntax: {@code stwa          %g0, [%g0 + -4096] %asi}
     * <p>
     * Constraint: {@code -4096 <= simm13 && simm13 <= 4095}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.54"
     */
    // Template#: 96, Serial#: 96
    public void stwa(final GPR rd, final GPR rs1, final int simm13) {
        int instruction = 0xC0A02000;
        checkConstraint(-4096 <= simm13 && simm13 <= 4095, "-4096 <= simm13 && simm13 <= 4095");
        instruction |= ((rd.value() & 0x1f) << 25);
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (simm13 & 0x1fff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code stxa  }<i>rd</i>, <i>rs1</i>, <i>rs2</i>, <i>immAsi</i>
     * Example disassembly syntax: {@code stxa          %g0, [%g0 + %g0]0x0}
     * <p>
     * Constraint: {@code 0 <= immAsi && immAsi <= 255}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.54"
     */
    // Template#: 97, Serial#: 97
    public void stxa(final GPR rd, final GPR rs1, final GPR rs2, final int immAsi) {
        int instruction = 0xC0F00000;
        checkConstraint(0 <= immAsi && immAsi <= 255, "0 <= immAsi && immAsi <= 255");
        instruction |= ((rd.value() & 0x1f) << 25);
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (rs2.value() & 0x1f);
        instruction |= ((immAsi & 0xff) << 5);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code stxa  }<i>rd</i>, <i>rs1</i>, <i>simm13</i>
     * Example disassembly syntax: {@code stxa          %g0, [%g0 + -4096] %asi}
     * <p>
     * Constraint: {@code -4096 <= simm13 && simm13 <= 4095}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.54"
     */
    // Template#: 98, Serial#: 98
    public void stxa(final GPR rd, final GPR rs1, final int simm13) {
        int instruction = 0xC0F02000;
        checkConstraint(-4096 <= simm13 && simm13 <= 4095, "-4096 <= simm13 && simm13 <= 4095");
        instruction |= ((rd.value() & 0x1f) << 25);
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (simm13 & 0x1fff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code stda  }<i>rd</i>, <i>rs1</i>, <i>rs2</i>, <i>immAsi</i>
     * Example disassembly syntax: {@code stda          %g0, [%g0 + %g0]0x0}
     * <p>
     * Constraint: {@code 0 <= immAsi && immAsi <= 255}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.54"
     */
    // Template#: 99, Serial#: 99
    public void stda(final GPR.Even rd, final GPR rs1, final GPR rs2, final int immAsi) {
        int instruction = 0xC0B80000;
        checkConstraint(0 <= immAsi && immAsi <= 255, "0 <= immAsi && immAsi <= 255");
        instruction |= ((rd.value() & 0x1f) << 25);
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (rs2.value() & 0x1f);
        instruction |= ((immAsi & 0xff) << 5);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code stda  }<i>rd</i>, <i>rs1</i>, <i>simm13</i>
     * Example disassembly syntax: {@code stda          %g0, [%g0 + -4096] %asi}
     * <p>
     * Constraint: {@code -4096 <= simm13 && simm13 <= 4095}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.54"
     */
    // Template#: 100, Serial#: 100
    public void stda(final GPR.Even rd, final GPR rs1, final int simm13) {
        int instruction = 0xC0B82000;
        checkConstraint(-4096 <= simm13 && simm13 <= 4095, "-4096 <= simm13 && simm13 <= 4095");
        instruction |= ((rd.value() & 0x1f) << 25);
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (simm13 & 0x1fff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code membar  }<i>membarMask</i>
     * Example disassembly syntax: {@code membar        0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.32"
     */
    // Template#: 101, Serial#: 101
    public void membar(final MembarOperand membarMask) {
        int instruction = 0x8143E000;
        instruction |= (membarMask.value() & 0x7f);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code stbar  }
     * Example disassembly syntax: {@code stbar         }
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.51"
     */
    // Template#: 102, Serial#: 102
    public void stbar() {
        int instruction = 0x8143C000;
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code add  }<i>rs1</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code add           %g0, %g0, %g0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.2"
     */
    // Template#: 103, Serial#: 103
    public void add(final GPR rs1, final GPR rs2, final GPR rd) {
        int instruction = 0x80000000;
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (rs2.value() & 0x1f);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code add  }<i>rs1</i>, <i>simm13</i>, <i>rd</i>
     * Example disassembly syntax: {@code add           %g0, -4096, %g0}
     * <p>
     * Constraint: {@code -4096 <= simm13 && simm13 <= 4095}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.2"
     */
    // Template#: 104, Serial#: 104
    public void add(final GPR rs1, final int simm13, final GPR rd) {
        int instruction = 0x80002000;
        checkConstraint(-4096 <= simm13 && simm13 <= 4095, "-4096 <= simm13 && simm13 <= 4095");
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (simm13 & 0x1fff);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code addc  }<i>rs1</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code addc          %g0, %g0, %g0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.2"
     */
    // Template#: 105, Serial#: 105
    public void addc(final GPR rs1, final GPR rs2, final GPR rd) {
        int instruction = 0x80400000;
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (rs2.value() & 0x1f);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code addc  }<i>rs1</i>, <i>simm13</i>, <i>rd</i>
     * Example disassembly syntax: {@code addc          %g0, -4096, %g0}
     * <p>
     * Constraint: {@code -4096 <= simm13 && simm13 <= 4095}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.2"
     */
    // Template#: 106, Serial#: 106
    public void addc(final GPR rs1, final int simm13, final GPR rd) {
        int instruction = 0x80402000;
        checkConstraint(-4096 <= simm13 && simm13 <= 4095, "-4096 <= simm13 && simm13 <= 4095");
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (simm13 & 0x1fff);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code addcc  }<i>rs1</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code addcc         %g0, %g0, %g0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.2"
     */
    // Template#: 107, Serial#: 107
    public void addcc(final GPR rs1, final GPR rs2, final GPR rd) {
        int instruction = 0x80800000;
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (rs2.value() & 0x1f);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code addcc  }<i>rs1</i>, <i>simm13</i>, <i>rd</i>
     * Example disassembly syntax: {@code addcc         %g0, -4096, %g0}
     * <p>
     * Constraint: {@code -4096 <= simm13 && simm13 <= 4095}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.2"
     */
    // Template#: 108, Serial#: 108
    public void addcc(final GPR rs1, final int simm13, final GPR rd) {
        int instruction = 0x80802000;
        checkConstraint(-4096 <= simm13 && simm13 <= 4095, "-4096 <= simm13 && simm13 <= 4095");
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (simm13 & 0x1fff);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code addccc  }<i>rs1</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code addccc        %g0, %g0, %g0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.2"
     */
    // Template#: 109, Serial#: 109
    public void addccc(final GPR rs1, final GPR rs2, final GPR rd) {
        int instruction = 0x80C00000;
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (rs2.value() & 0x1f);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code addccc  }<i>rs1</i>, <i>simm13</i>, <i>rd</i>
     * Example disassembly syntax: {@code addccc        %g0, -4096, %g0}
     * <p>
     * Constraint: {@code -4096 <= simm13 && simm13 <= 4095}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.2"
     */
    // Template#: 110, Serial#: 110
    public void addccc(final GPR rs1, final int simm13, final GPR rd) {
        int instruction = 0x80C02000;
        checkConstraint(-4096 <= simm13 && simm13 <= 4095, "-4096 <= simm13 && simm13 <= 4095");
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (simm13 & 0x1fff);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code udiv  }<i>rs1</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code udiv          %g0, %g0, %g0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.10"
     */
    // Template#: 111, Serial#: 111
    public void udiv(final GPR rs1, final GPR rs2, final GPR rd) {
        int instruction = 0x80700000;
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (rs2.value() & 0x1f);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code udiv  }<i>rs1</i>, <i>simm13</i>, <i>rd</i>
     * Example disassembly syntax: {@code udiv          %g0, -4096, %g0}
     * <p>
     * Constraint: {@code -4096 <= simm13 && simm13 <= 4095}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.10"
     */
    // Template#: 112, Serial#: 112
    public void udiv(final GPR rs1, final int simm13, final GPR rd) {
        int instruction = 0x80702000;
        checkConstraint(-4096 <= simm13 && simm13 <= 4095, "-4096 <= simm13 && simm13 <= 4095");
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (simm13 & 0x1fff);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code sdiv  }<i>rs1</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code sdiv          %g0, %g0, %g0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.10"
     */
    // Template#: 113, Serial#: 113
    public void sdiv(final GPR rs1, final GPR rs2, final GPR rd) {
        int instruction = 0x80780000;
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (rs2.value() & 0x1f);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code sdiv  }<i>rs1</i>, <i>simm13</i>, <i>rd</i>
     * Example disassembly syntax: {@code sdiv          %g0, -4096, %g0}
     * <p>
     * Constraint: {@code -4096 <= simm13 && simm13 <= 4095}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.10"
     */
    // Template#: 114, Serial#: 114
    public void sdiv(final GPR rs1, final int simm13, final GPR rd) {
        int instruction = 0x80782000;
        checkConstraint(-4096 <= simm13 && simm13 <= 4095, "-4096 <= simm13 && simm13 <= 4095");
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (simm13 & 0x1fff);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code udivcc  }<i>rs1</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code udivcc        %g0, %g0, %g0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.10"
     */
    // Template#: 115, Serial#: 115
    public void udivcc(final GPR rs1, final GPR rs2, final GPR rd) {
        int instruction = 0x80F00000;
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (rs2.value() & 0x1f);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code udivcc  }<i>rs1</i>, <i>simm13</i>, <i>rd</i>
     * Example disassembly syntax: {@code udivcc        %g0, -4096, %g0}
     * <p>
     * Constraint: {@code -4096 <= simm13 && simm13 <= 4095}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.10"
     */
    // Template#: 116, Serial#: 116
    public void udivcc(final GPR rs1, final int simm13, final GPR rd) {
        int instruction = 0x80F02000;
        checkConstraint(-4096 <= simm13 && simm13 <= 4095, "-4096 <= simm13 && simm13 <= 4095");
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (simm13 & 0x1fff);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code sdivcc  }<i>rs1</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code sdivcc        %g0, %g0, %g0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.10"
     */
    // Template#: 117, Serial#: 117
    public void sdivcc(final GPR rs1, final GPR rs2, final GPR rd) {
        int instruction = 0x80F80000;
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (rs2.value() & 0x1f);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code sdivcc  }<i>rs1</i>, <i>simm13</i>, <i>rd</i>
     * Example disassembly syntax: {@code sdivcc        %g0, -4096, %g0}
     * <p>
     * Constraint: {@code -4096 <= simm13 && simm13 <= 4095}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.10"
     */
    // Template#: 118, Serial#: 118
    public void sdivcc(final GPR rs1, final int simm13, final GPR rd) {
        int instruction = 0x80F82000;
        checkConstraint(-4096 <= simm13 && simm13 <= 4095, "-4096 <= simm13 && simm13 <= 4095");
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (simm13 & 0x1fff);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code and  }<i>rs1</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code and           %g0, %g0, %g0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.31"
     */
    // Template#: 119, Serial#: 119
    public void and(final GPR rs1, final GPR rs2, final GPR rd) {
        int instruction = 0x80080000;
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (rs2.value() & 0x1f);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code and  }<i>rs1</i>, <i>simm13</i>, <i>rd</i>
     * Example disassembly syntax: {@code and           %g0, -4096, %g0}
     * <p>
     * Constraint: {@code -4096 <= simm13 && simm13 <= 4095}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.31"
     */
    // Template#: 120, Serial#: 120
    public void and(final GPR rs1, final int simm13, final GPR rd) {
        int instruction = 0x80082000;
        checkConstraint(-4096 <= simm13 && simm13 <= 4095, "-4096 <= simm13 && simm13 <= 4095");
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (simm13 & 0x1fff);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code andcc  }<i>rs1</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code andcc         %g0, %g0, %g0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.31"
     */
    // Template#: 121, Serial#: 121
    public void andcc(final GPR rs1, final GPR rs2, final GPR rd) {
        int instruction = 0x80880000;
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (rs2.value() & 0x1f);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code andcc  }<i>rs1</i>, <i>simm13</i>, <i>rd</i>
     * Example disassembly syntax: {@code andcc         %g0, -4096, %g0}
     * <p>
     * Constraint: {@code -4096 <= simm13 && simm13 <= 4095}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.31"
     */
    // Template#: 122, Serial#: 122
    public void andcc(final GPR rs1, final int simm13, final GPR rd) {
        int instruction = 0x80882000;
        checkConstraint(-4096 <= simm13 && simm13 <= 4095, "-4096 <= simm13 && simm13 <= 4095");
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (simm13 & 0x1fff);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code andn  }<i>rs1</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code andn          %g0, %g0, %g0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.31"
     */
    // Template#: 123, Serial#: 123
    public void andn(final GPR rs1, final GPR rs2, final GPR rd) {
        int instruction = 0x80280000;
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (rs2.value() & 0x1f);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code andn  }<i>rs1</i>, <i>simm13</i>, <i>rd</i>
     * Example disassembly syntax: {@code andn          %g0, -4096, %g0}
     * <p>
     * Constraint: {@code -4096 <= simm13 && simm13 <= 4095}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.31"
     */
    // Template#: 124, Serial#: 124
    public void andn(final GPR rs1, final int simm13, final GPR rd) {
        int instruction = 0x80282000;
        checkConstraint(-4096 <= simm13 && simm13 <= 4095, "-4096 <= simm13 && simm13 <= 4095");
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (simm13 & 0x1fff);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code andncc  }<i>rs1</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code andncc        %g0, %g0, %g0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.31"
     */
    // Template#: 125, Serial#: 125
    public void andncc(final GPR rs1, final GPR rs2, final GPR rd) {
        int instruction = 0x80A80000;
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (rs2.value() & 0x1f);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code andncc  }<i>rs1</i>, <i>simm13</i>, <i>rd</i>
     * Example disassembly syntax: {@code andncc        %g0, -4096, %g0}
     * <p>
     * Constraint: {@code -4096 <= simm13 && simm13 <= 4095}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.31"
     */
    // Template#: 126, Serial#: 126
    public void andncc(final GPR rs1, final int simm13, final GPR rd) {
        int instruction = 0x80A82000;
        checkConstraint(-4096 <= simm13 && simm13 <= 4095, "-4096 <= simm13 && simm13 <= 4095");
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (simm13 & 0x1fff);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code or  }<i>rs1</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code or            %g0, %g0, %g0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.31"
     */
    // Template#: 127, Serial#: 127
    public void or(final GPR rs1, final GPR rs2, final GPR rd) {
        int instruction = 0x80100000;
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (rs2.value() & 0x1f);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code or  }<i>rs1</i>, <i>simm13</i>, <i>rd</i>
     * Example disassembly syntax: {@code or            %g0, -4096, %g0}
     * <p>
     * Constraint: {@code -4096 <= simm13 && simm13 <= 4095}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.31"
     */
    // Template#: 128, Serial#: 128
    public void or(final GPR rs1, final int simm13, final GPR rd) {
        int instruction = 0x80102000;
        checkConstraint(-4096 <= simm13 && simm13 <= 4095, "-4096 <= simm13 && simm13 <= 4095");
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (simm13 & 0x1fff);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code orcc  }<i>rs1</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code orcc          %g0, %g0, %g0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.31"
     */
    // Template#: 129, Serial#: 129
    public void orcc(final GPR rs1, final GPR rs2, final GPR rd) {
        int instruction = 0x80900000;
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (rs2.value() & 0x1f);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code orcc  }<i>rs1</i>, <i>simm13</i>, <i>rd</i>
     * Example disassembly syntax: {@code orcc          %g0, -4096, %g0}
     * <p>
     * Constraint: {@code -4096 <= simm13 && simm13 <= 4095}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.31"
     */
    // Template#: 130, Serial#: 130
    public void orcc(final GPR rs1, final int simm13, final GPR rd) {
        int instruction = 0x80902000;
        checkConstraint(-4096 <= simm13 && simm13 <= 4095, "-4096 <= simm13 && simm13 <= 4095");
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (simm13 & 0x1fff);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code orn  }<i>rs1</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code orn           %g0, %g0, %g0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.31"
     */
    // Template#: 131, Serial#: 131
    public void orn(final GPR rs1, final GPR rs2, final GPR rd) {
        int instruction = 0x80300000;
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (rs2.value() & 0x1f);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code orn  }<i>rs1</i>, <i>simm13</i>, <i>rd</i>
     * Example disassembly syntax: {@code orn           %g0, -4096, %g0}
     * <p>
     * Constraint: {@code -4096 <= simm13 && simm13 <= 4095}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.31"
     */
    // Template#: 132, Serial#: 132
    public void orn(final GPR rs1, final int simm13, final GPR rd) {
        int instruction = 0x80302000;
        checkConstraint(-4096 <= simm13 && simm13 <= 4095, "-4096 <= simm13 && simm13 <= 4095");
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (simm13 & 0x1fff);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code orncc  }<i>rs1</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code orncc         %g0, %g0, %g0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.31"
     */
    // Template#: 133, Serial#: 133
    public void orncc(final GPR rs1, final GPR rs2, final GPR rd) {
        int instruction = 0x80B00000;
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (rs2.value() & 0x1f);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code orncc  }<i>rs1</i>, <i>simm13</i>, <i>rd</i>
     * Example disassembly syntax: {@code orncc         %g0, -4096, %g0}
     * <p>
     * Constraint: {@code -4096 <= simm13 && simm13 <= 4095}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.31"
     */
    // Template#: 134, Serial#: 134
    public void orncc(final GPR rs1, final int simm13, final GPR rd) {
        int instruction = 0x80B02000;
        checkConstraint(-4096 <= simm13 && simm13 <= 4095, "-4096 <= simm13 && simm13 <= 4095");
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (simm13 & 0x1fff);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code xor  }<i>rs1</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code xor           %g0, %g0, %g0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.31"
     */
    // Template#: 135, Serial#: 135
    public void xor(final GPR rs1, final GPR rs2, final GPR rd) {
        int instruction = 0x80180000;
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (rs2.value() & 0x1f);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code xor  }<i>rs1</i>, <i>simm13</i>, <i>rd</i>
     * Example disassembly syntax: {@code xor           %g0, -4096, %g0}
     * <p>
     * Constraint: {@code -4096 <= simm13 && simm13 <= 4095}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.31"
     */
    // Template#: 136, Serial#: 136
    public void xor(final GPR rs1, final int simm13, final GPR rd) {
        int instruction = 0x80182000;
        checkConstraint(-4096 <= simm13 && simm13 <= 4095, "-4096 <= simm13 && simm13 <= 4095");
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (simm13 & 0x1fff);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code xorcc  }<i>rs1</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code xorcc         %g0, %g0, %g0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.31"
     */
    // Template#: 137, Serial#: 137
    public void xorcc(final GPR rs1, final GPR rs2, final GPR rd) {
        int instruction = 0x80980000;
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (rs2.value() & 0x1f);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code xorcc  }<i>rs1</i>, <i>simm13</i>, <i>rd</i>
     * Example disassembly syntax: {@code xorcc         %g0, -4096, %g0}
     * <p>
     * Constraint: {@code -4096 <= simm13 && simm13 <= 4095}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.31"
     */
    // Template#: 138, Serial#: 138
    public void xorcc(final GPR rs1, final int simm13, final GPR rd) {
        int instruction = 0x80982000;
        checkConstraint(-4096 <= simm13 && simm13 <= 4095, "-4096 <= simm13 && simm13 <= 4095");
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (simm13 & 0x1fff);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code xnor  }<i>rs1</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code xnor          %g0, %g0, %g0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.31"
     */
    // Template#: 139, Serial#: 139
    public void xnor(final GPR rs1, final GPR rs2, final GPR rd) {
        int instruction = 0x80380000;
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (rs2.value() & 0x1f);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code xnor  }<i>rs1</i>, <i>simm13</i>, <i>rd</i>
     * Example disassembly syntax: {@code xnor          %g0, -4096, %g0}
     * <p>
     * Constraint: {@code -4096 <= simm13 && simm13 <= 4095}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.31"
     */
    // Template#: 140, Serial#: 140
    public void xnor(final GPR rs1, final int simm13, final GPR rd) {
        int instruction = 0x80382000;
        checkConstraint(-4096 <= simm13 && simm13 <= 4095, "-4096 <= simm13 && simm13 <= 4095");
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (simm13 & 0x1fff);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code xnorcc  }<i>rs1</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code xnorcc        %g0, %g0, %g0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.31"
     */
    // Template#: 141, Serial#: 141
    public void xnorcc(final GPR rs1, final GPR rs2, final GPR rd) {
        int instruction = 0x80B80000;
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (rs2.value() & 0x1f);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code xnorcc  }<i>rs1</i>, <i>simm13</i>, <i>rd</i>
     * Example disassembly syntax: {@code xnorcc        %g0, -4096, %g0}
     * <p>
     * Constraint: {@code -4096 <= simm13 && simm13 <= 4095}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.31"
     */
    // Template#: 142, Serial#: 142
    public void xnorcc(final GPR rs1, final int simm13, final GPR rd) {
        int instruction = 0x80B82000;
        checkConstraint(-4096 <= simm13 && simm13 <= 4095, "-4096 <= simm13 && simm13 <= 4095");
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (simm13 & 0x1fff);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code mulx  }<i>rs1</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code mulx          %g0, %g0, %g0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.36"
     */
    // Template#: 143, Serial#: 143
    public void mulx(final GPR rs1, final GPR rs2, final GPR rd) {
        int instruction = 0x80480000;
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (rs2.value() & 0x1f);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code mulx  }<i>rs1</i>, <i>simm13</i>, <i>rd</i>
     * Example disassembly syntax: {@code mulx          %g0, -4096, %g0}
     * <p>
     * Constraint: {@code -4096 <= simm13 && simm13 <= 4095}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.36"
     */
    // Template#: 144, Serial#: 144
    public void mulx(final GPR rs1, final int simm13, final GPR rd) {
        int instruction = 0x80482000;
        checkConstraint(-4096 <= simm13 && simm13 <= 4095, "-4096 <= simm13 && simm13 <= 4095");
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (simm13 & 0x1fff);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code sdivx  }<i>rs1</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code sdivx         %g0, %g0, %g0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.36"
     */
    // Template#: 145, Serial#: 145
    public void sdivx(final GPR rs1, final GPR rs2, final GPR rd) {
        int instruction = 0x81680000;
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (rs2.value() & 0x1f);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code sdivx  }<i>rs1</i>, <i>simm13</i>, <i>rd</i>
     * Example disassembly syntax: {@code sdivx         %g0, -4096, %g0}
     * <p>
     * Constraint: {@code -4096 <= simm13 && simm13 <= 4095}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.36"
     */
    // Template#: 146, Serial#: 146
    public void sdivx(final GPR rs1, final int simm13, final GPR rd) {
        int instruction = 0x81682000;
        checkConstraint(-4096 <= simm13 && simm13 <= 4095, "-4096 <= simm13 && simm13 <= 4095");
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (simm13 & 0x1fff);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code udivx  }<i>rs1</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code udivx         %g0, %g0, %g0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.36"
     */
    // Template#: 147, Serial#: 147
    public void udivx(final GPR rs1, final GPR rs2, final GPR rd) {
        int instruction = 0x80680000;
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (rs2.value() & 0x1f);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code udivx  }<i>rs1</i>, <i>simm13</i>, <i>rd</i>
     * Example disassembly syntax: {@code udivx         %g0, -4096, %g0}
     * <p>
     * Constraint: {@code -4096 <= simm13 && simm13 <= 4095}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.36"
     */
    // Template#: 148, Serial#: 148
    public void udivx(final GPR rs1, final int simm13, final GPR rd) {
        int instruction = 0x80682000;
        checkConstraint(-4096 <= simm13 && simm13 <= 4095, "-4096 <= simm13 && simm13 <= 4095");
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (simm13 & 0x1fff);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code umul  }<i>rs1</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code umul          %g0, %g0, %g0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.38"
     */
    // Template#: 149, Serial#: 149
    public void umul(final GPR rs1, final GPR rs2, final GPR rd) {
        int instruction = 0x80500000;
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (rs2.value() & 0x1f);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code umul  }<i>rs1</i>, <i>simm13</i>, <i>rd</i>
     * Example disassembly syntax: {@code umul          %g0, -4096, %g0}
     * <p>
     * Constraint: {@code -4096 <= simm13 && simm13 <= 4095}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.38"
     */
    // Template#: 150, Serial#: 150
    public void umul(final GPR rs1, final int simm13, final GPR rd) {
        int instruction = 0x80502000;
        checkConstraint(-4096 <= simm13 && simm13 <= 4095, "-4096 <= simm13 && simm13 <= 4095");
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (simm13 & 0x1fff);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code smul  }<i>rs1</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code smul          %g0, %g0, %g0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.38"
     */
    // Template#: 151, Serial#: 151
    public void smul(final GPR rs1, final GPR rs2, final GPR rd) {
        int instruction = 0x80580000;
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (rs2.value() & 0x1f);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code smul  }<i>rs1</i>, <i>simm13</i>, <i>rd</i>
     * Example disassembly syntax: {@code smul          %g0, -4096, %g0}
     * <p>
     * Constraint: {@code -4096 <= simm13 && simm13 <= 4095}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.38"
     */
    // Template#: 152, Serial#: 152
    public void smul(final GPR rs1, final int simm13, final GPR rd) {
        int instruction = 0x80582000;
        checkConstraint(-4096 <= simm13 && simm13 <= 4095, "-4096 <= simm13 && simm13 <= 4095");
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (simm13 & 0x1fff);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code umulcc  }<i>rs1</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code umulcc        %g0, %g0, %g0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.38"
     */
    // Template#: 153, Serial#: 153
    public void umulcc(final GPR rs1, final GPR rs2, final GPR rd) {
        int instruction = 0x80D00000;
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (rs2.value() & 0x1f);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code umulcc  }<i>rs1</i>, <i>simm13</i>, <i>rd</i>
     * Example disassembly syntax: {@code umulcc        %g0, -4096, %g0}
     * <p>
     * Constraint: {@code -4096 <= simm13 && simm13 <= 4095}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.38"
     */
    // Template#: 154, Serial#: 154
    public void umulcc(final GPR rs1, final int simm13, final GPR rd) {
        int instruction = 0x80D02000;
        checkConstraint(-4096 <= simm13 && simm13 <= 4095, "-4096 <= simm13 && simm13 <= 4095");
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (simm13 & 0x1fff);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code smulcc  }<i>rs1</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code smulcc        %g0, %g0, %g0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.38"
     */
    // Template#: 155, Serial#: 155
    public void smulcc(final GPR rs1, final GPR rs2, final GPR rd) {
        int instruction = 0x80D80000;
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (rs2.value() & 0x1f);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code smulcc  }<i>rs1</i>, <i>simm13</i>, <i>rd</i>
     * Example disassembly syntax: {@code smulcc        %g0, -4096, %g0}
     * <p>
     * Constraint: {@code -4096 <= simm13 && simm13 <= 4095}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.38"
     */
    // Template#: 156, Serial#: 156
    public void smulcc(final GPR rs1, final int simm13, final GPR rd) {
        int instruction = 0x80D82000;
        checkConstraint(-4096 <= simm13 && simm13 <= 4095, "-4096 <= simm13 && simm13 <= 4095");
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (simm13 & 0x1fff);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code mulscc  }<i>rs1</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code mulscc        %g0, %g0, %g0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.39"
     */
    // Template#: 157, Serial#: 157
    public void mulscc(final GPR rs1, final GPR rs2, final GPR rd) {
        int instruction = 0x81200000;
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (rs2.value() & 0x1f);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code mulscc  }<i>rs1</i>, <i>simm13</i>, <i>rd</i>
     * Example disassembly syntax: {@code mulscc        %g0, -4096, %g0}
     * <p>
     * Constraint: {@code -4096 <= simm13 && simm13 <= 4095}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.39"
     */
    // Template#: 158, Serial#: 158
    public void mulscc(final GPR rs1, final int simm13, final GPR rd) {
        int instruction = 0x81202000;
        checkConstraint(-4096 <= simm13 && simm13 <= 4095, "-4096 <= simm13 && simm13 <= 4095");
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (simm13 & 0x1fff);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code popc  }<i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code popc          %g0, %g0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.41"
     */
    // Template#: 159, Serial#: 159
    public void popc(final GPR rs2, final GPR rd) {
        int instruction = 0x81700000;
        instruction |= (rs2.value() & 0x1f);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code popc  }<i>simm13</i>, <i>rd</i>
     * Example disassembly syntax: {@code popc          -4096, %g0}
     * <p>
     * Constraint: {@code -4096 <= simm13 && simm13 <= 4095}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.41"
     */
    // Template#: 160, Serial#: 160
    public void popc(final int simm13, final GPR rd) {
        int instruction = 0x81702000;
        checkConstraint(-4096 <= simm13 && simm13 <= 4095, "-4096 <= simm13 && simm13 <= 4095");
        instruction |= (simm13 & 0x1fff);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code sethi  }<i>imm22</i>, <i>rd</i>
     * Example disassembly syntax: {@code sethi         0x0, %g0}
     * <p>
     * Constraint: {@code -2097152 <= imm22 && imm22 <= 4194303}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.48"
     */
    // Template#: 161, Serial#: 161
    public void sethi(final int imm22, final GPR rd) {
        int instruction = 0x01000000;
        checkConstraint(-2097152 <= imm22 && imm22 <= 4194303, "-2097152 <= imm22 && imm22 <= 4194303");
        instruction |= (imm22 & 0x3fffff);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code sll  }<i>rs1</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code sll           %g0, %g0, %g0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.49"
     */
    // Template#: 162, Serial#: 162
    public void sll(final GPR rs1, final GPR rs2, final GPR rd) {
        int instruction = 0x81280000;
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (rs2.value() & 0x1f);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code sllx  }<i>rs1</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code sllx          %g0, %g0, %g0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.49"
     */
    // Template#: 163, Serial#: 163
    public void sllx(final GPR rs1, final GPR rs2, final GPR rd) {
        int instruction = 0x81281000;
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (rs2.value() & 0x1f);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code sll  }<i>rs1</i>, <i>shcnt32</i>, <i>rd</i>
     * Example disassembly syntax: {@code sll           %g0, 0x0, %g0}
     * <p>
     * Constraint: {@code 0 <= shcnt32 && shcnt32 <= 31}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.49"
     */
    // Template#: 164, Serial#: 164
    public void sll(final GPR rs1, final int shcnt32, final GPR rd) {
        int instruction = 0x81282000;
        checkConstraint(0 <= shcnt32 && shcnt32 <= 31, "0 <= shcnt32 && shcnt32 <= 31");
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (shcnt32 & 0x1f);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code sllx  }<i>rs1</i>, <i>shcnt64</i>, <i>rd</i>
     * Example disassembly syntax: {@code sllx          %g0, 0x0, %g0}
     * <p>
     * Constraint: {@code 0 <= shcnt64 && shcnt64 <= 63}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.49"
     */
    // Template#: 165, Serial#: 165
    public void sllx(final GPR rs1, final int shcnt64, final GPR rd) {
        int instruction = 0x81283000;
        checkConstraint(0 <= shcnt64 && shcnt64 <= 63, "0 <= shcnt64 && shcnt64 <= 63");
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (shcnt64 & 0x3f);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code srl  }<i>rs1</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code srl           %g0, %g0, %g0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.49"
     */
    // Template#: 166, Serial#: 166
    public void srl(final GPR rs1, final GPR rs2, final GPR rd) {
        int instruction = 0x81300000;
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (rs2.value() & 0x1f);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code srlx  }<i>rs1</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code srlx          %g0, %g0, %g0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.49"
     */
    // Template#: 167, Serial#: 167
    public void srlx(final GPR rs1, final GPR rs2, final GPR rd) {
        int instruction = 0x81301000;
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (rs2.value() & 0x1f);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code srl  }<i>rs1</i>, <i>shcnt32</i>, <i>rd</i>
     * Example disassembly syntax: {@code srl           %g0, 0x0, %g0}
     * <p>
     * Constraint: {@code 0 <= shcnt32 && shcnt32 <= 31}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.49"
     */
    // Template#: 168, Serial#: 168
    public void srl(final GPR rs1, final int shcnt32, final GPR rd) {
        int instruction = 0x81302000;
        checkConstraint(0 <= shcnt32 && shcnt32 <= 31, "0 <= shcnt32 && shcnt32 <= 31");
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (shcnt32 & 0x1f);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code srlx  }<i>rs1</i>, <i>shcnt64</i>, <i>rd</i>
     * Example disassembly syntax: {@code srlx          %g0, 0x0, %g0}
     * <p>
     * Constraint: {@code 0 <= shcnt64 && shcnt64 <= 63}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.49"
     */
    // Template#: 169, Serial#: 169
    public void srlx(final GPR rs1, final int shcnt64, final GPR rd) {
        int instruction = 0x81303000;
        checkConstraint(0 <= shcnt64 && shcnt64 <= 63, "0 <= shcnt64 && shcnt64 <= 63");
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (shcnt64 & 0x3f);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code sra  }<i>rs1</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code sra           %g0, %g0, %g0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.49"
     */
    // Template#: 170, Serial#: 170
    public void sra(final GPR rs1, final GPR rs2, final GPR rd) {
        int instruction = 0x81380000;
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (rs2.value() & 0x1f);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code srax  }<i>rs1</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code srax          %g0, %g0, %g0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.49"
     */
    // Template#: 171, Serial#: 171
    public void srax(final GPR rs1, final GPR rs2, final GPR rd) {
        int instruction = 0x81381000;
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (rs2.value() & 0x1f);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code sra  }<i>rs1</i>, <i>shcnt32</i>, <i>rd</i>
     * Example disassembly syntax: {@code sra           %g0, 0x0, %g0}
     * <p>
     * Constraint: {@code 0 <= shcnt32 && shcnt32 <= 31}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.49"
     */
    // Template#: 172, Serial#: 172
    public void sra(final GPR rs1, final int shcnt32, final GPR rd) {
        int instruction = 0x81382000;
        checkConstraint(0 <= shcnt32 && shcnt32 <= 31, "0 <= shcnt32 && shcnt32 <= 31");
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (shcnt32 & 0x1f);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code srax  }<i>rs1</i>, <i>shcnt64</i>, <i>rd</i>
     * Example disassembly syntax: {@code srax          %g0, 0x0, %g0}
     * <p>
     * Constraint: {@code 0 <= shcnt64 && shcnt64 <= 63}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.49"
     */
    // Template#: 173, Serial#: 173
    public void srax(final GPR rs1, final int shcnt64, final GPR rd) {
        int instruction = 0x81383000;
        checkConstraint(0 <= shcnt64 && shcnt64 <= 63, "0 <= shcnt64 && shcnt64 <= 63");
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (shcnt64 & 0x3f);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code sub  }<i>rs1</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code sub           %g0, %g0, %g0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.56"
     */
    // Template#: 174, Serial#: 174
    public void sub(final GPR rs1, final GPR rs2, final GPR rd) {
        int instruction = 0x80200000;
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (rs2.value() & 0x1f);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code sub  }<i>rs1</i>, <i>simm13</i>, <i>rd</i>
     * Example disassembly syntax: {@code sub           %g0, -4096, %g0}
     * <p>
     * Constraint: {@code -4096 <= simm13 && simm13 <= 4095}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.56"
     */
    // Template#: 175, Serial#: 175
    public void sub(final GPR rs1, final int simm13, final GPR rd) {
        int instruction = 0x80202000;
        checkConstraint(-4096 <= simm13 && simm13 <= 4095, "-4096 <= simm13 && simm13 <= 4095");
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (simm13 & 0x1fff);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code subcc  }<i>rs1</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code subcc         %g0, %g0, %g0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.56"
     */
    // Template#: 176, Serial#: 176
    public void subcc(final GPR rs1, final GPR rs2, final GPR rd) {
        int instruction = 0x80A00000;
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (rs2.value() & 0x1f);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code subcc  }<i>rs1</i>, <i>simm13</i>, <i>rd</i>
     * Example disassembly syntax: {@code subcc         %g0, -4096, %g0}
     * <p>
     * Constraint: {@code -4096 <= simm13 && simm13 <= 4095}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.56"
     */
    // Template#: 177, Serial#: 177
    public void subcc(final GPR rs1, final int simm13, final GPR rd) {
        int instruction = 0x80A02000;
        checkConstraint(-4096 <= simm13 && simm13 <= 4095, "-4096 <= simm13 && simm13 <= 4095");
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (simm13 & 0x1fff);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code subc  }<i>rs1</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code subc          %g0, %g0, %g0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.56"
     */
    // Template#: 178, Serial#: 178
    public void subc(final GPR rs1, final GPR rs2, final GPR rd) {
        int instruction = 0x80600000;
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (rs2.value() & 0x1f);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code subc  }<i>rs1</i>, <i>simm13</i>, <i>rd</i>
     * Example disassembly syntax: {@code subc          %g0, -4096, %g0}
     * <p>
     * Constraint: {@code -4096 <= simm13 && simm13 <= 4095}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.56"
     */
    // Template#: 179, Serial#: 179
    public void subc(final GPR rs1, final int simm13, final GPR rd) {
        int instruction = 0x80602000;
        checkConstraint(-4096 <= simm13 && simm13 <= 4095, "-4096 <= simm13 && simm13 <= 4095");
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (simm13 & 0x1fff);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code subccc  }<i>rs1</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code subccc        %g0, %g0, %g0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.56"
     */
    // Template#: 180, Serial#: 180
    public void subccc(final GPR rs1, final GPR rs2, final GPR rd) {
        int instruction = 0x80E00000;
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (rs2.value() & 0x1f);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code subccc  }<i>rs1</i>, <i>simm13</i>, <i>rd</i>
     * Example disassembly syntax: {@code subccc        %g0, -4096, %g0}
     * <p>
     * Constraint: {@code -4096 <= simm13 && simm13 <= 4095}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.56"
     */
    // Template#: 181, Serial#: 181
    public void subccc(final GPR rs1, final int simm13, final GPR rd) {
        int instruction = 0x80E02000;
        checkConstraint(-4096 <= simm13 && simm13 <= 4095, "-4096 <= simm13 && simm13 <= 4095");
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (simm13 & 0x1fff);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code taddcc  }<i>rs1</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code taddcc        %g0, %g0, %g0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.59"
     */
    // Template#: 182, Serial#: 182
    public void taddcc(final GPR rs1, final GPR rs2, final GPR rd) {
        int instruction = 0x81000000;
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (rs2.value() & 0x1f);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code taddcc  }<i>rs1</i>, <i>simm13</i>, <i>rd</i>
     * Example disassembly syntax: {@code taddcc        %g0, -4096, %g0}
     * <p>
     * Constraint: {@code -4096 <= simm13 && simm13 <= 4095}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.59"
     */
    // Template#: 183, Serial#: 183
    public void taddcc(final GPR rs1, final int simm13, final GPR rd) {
        int instruction = 0x81002000;
        checkConstraint(-4096 <= simm13 && simm13 <= 4095, "-4096 <= simm13 && simm13 <= 4095");
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (simm13 & 0x1fff);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code taddcctv  }<i>rs1</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code taddcctv      %g0, %g0, %g0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.59"
     */
    // Template#: 184, Serial#: 184
    public void taddcctv(final GPR rs1, final GPR rs2, final GPR rd) {
        int instruction = 0x81100000;
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (rs2.value() & 0x1f);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code taddcctv  }<i>rs1</i>, <i>simm13</i>, <i>rd</i>
     * Example disassembly syntax: {@code taddcctv      %g0, -4096, %g0}
     * <p>
     * Constraint: {@code -4096 <= simm13 && simm13 <= 4095}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.59"
     */
    // Template#: 185, Serial#: 185
    public void taddcctv(final GPR rs1, final int simm13, final GPR rd) {
        int instruction = 0x81102000;
        checkConstraint(-4096 <= simm13 && simm13 <= 4095, "-4096 <= simm13 && simm13 <= 4095");
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (simm13 & 0x1fff);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code tsubcc  }<i>rs1</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code tsubcc        %g0, %g0, %g0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.60"
     */
    // Template#: 186, Serial#: 186
    public void tsubcc(final GPR rs1, final GPR rs2, final GPR rd) {
        int instruction = 0x81080000;
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (rs2.value() & 0x1f);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code tsubcc  }<i>rs1</i>, <i>simm13</i>, <i>rd</i>
     * Example disassembly syntax: {@code tsubcc        %g0, -4096, %g0}
     * <p>
     * Constraint: {@code -4096 <= simm13 && simm13 <= 4095}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.60"
     */
    // Template#: 187, Serial#: 187
    public void tsubcc(final GPR rs1, final int simm13, final GPR rd) {
        int instruction = 0x81082000;
        checkConstraint(-4096 <= simm13 && simm13 <= 4095, "-4096 <= simm13 && simm13 <= 4095");
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (simm13 & 0x1fff);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code tsubcctv  }<i>rs1</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code tsubcctv      %g0, %g0, %g0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.60"
     */
    // Template#: 188, Serial#: 188
    public void tsubcctv(final GPR rs1, final GPR rs2, final GPR rd) {
        int instruction = 0x81180000;
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (rs2.value() & 0x1f);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code tsubcctv  }<i>rs1</i>, <i>simm13</i>, <i>rd</i>
     * Example disassembly syntax: {@code tsubcctv      %g0, -4096, %g0}
     * <p>
     * Constraint: {@code -4096 <= simm13 && simm13 <= 4095}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.60"
     */
    // Template#: 189, Serial#: 189
    public void tsubcctv(final GPR rs1, final int simm13, final GPR rd) {
        int instruction = 0x81182000;
        checkConstraint(-4096 <= simm13 && simm13 <= 4095, "-4096 <= simm13 && simm13 <= 4095");
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (simm13 & 0x1fff);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code brz{,a}{,pn|,pt}  }<i>rs1</i>, <i>label</i>
     * Example disassembly syntax: {@code brz,pn        %g0, L1: -131072}
     * <p>
     * Constraint: {@code (-131072 <= label && label <= 131068) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.3"
     */
    // Template#: 190, Serial#: 190
    public void brz(final AnnulBit a, final BranchPredictionBit p, final GPR rs1, final int label) {
        int instruction = 0x02C00000;
        checkConstraint((-131072 <= label && label <= 131068) && ((label % 4) == 0), "(-131072 <= label && label <= 131068) && ((label % 4) == 0)");
        instruction |= ((a.value() & 0x1) << 29);
        instruction |= ((p.value() & 0x1) << 19);
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= ((label >> 2) & 0x3fff) | ((((label >> 2) >> 14) & 0x3) << 20);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code brlez{,a}{,pn|,pt}  }<i>rs1</i>, <i>label</i>
     * Example disassembly syntax: {@code brlez,pn      %g0, L1: -131072}
     * <p>
     * Constraint: {@code (-131072 <= label && label <= 131068) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.3"
     */
    // Template#: 191, Serial#: 191
    public void brlez(final AnnulBit a, final BranchPredictionBit p, final GPR rs1, final int label) {
        int instruction = 0x04C00000;
        checkConstraint((-131072 <= label && label <= 131068) && ((label % 4) == 0), "(-131072 <= label && label <= 131068) && ((label % 4) == 0)");
        instruction |= ((a.value() & 0x1) << 29);
        instruction |= ((p.value() & 0x1) << 19);
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= ((label >> 2) & 0x3fff) | ((((label >> 2) >> 14) & 0x3) << 20);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code brlz{,a}{,pn|,pt}  }<i>rs1</i>, <i>label</i>
     * Example disassembly syntax: {@code brlz,pn       %g0, L1: -131072}
     * <p>
     * Constraint: {@code (-131072 <= label && label <= 131068) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.3"
     */
    // Template#: 192, Serial#: 192
    public void brlz(final AnnulBit a, final BranchPredictionBit p, final GPR rs1, final int label) {
        int instruction = 0x06C00000;
        checkConstraint((-131072 <= label && label <= 131068) && ((label % 4) == 0), "(-131072 <= label && label <= 131068) && ((label % 4) == 0)");
        instruction |= ((a.value() & 0x1) << 29);
        instruction |= ((p.value() & 0x1) << 19);
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= ((label >> 2) & 0x3fff) | ((((label >> 2) >> 14) & 0x3) << 20);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code brnz{,a}{,pn|,pt}  }<i>rs1</i>, <i>label</i>
     * Example disassembly syntax: {@code brnz,pn       %g0, L1: -131072}
     * <p>
     * Constraint: {@code (-131072 <= label && label <= 131068) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.3"
     */
    // Template#: 193, Serial#: 193
    public void brnz(final AnnulBit a, final BranchPredictionBit p, final GPR rs1, final int label) {
        int instruction = 0x0AC00000;
        checkConstraint((-131072 <= label && label <= 131068) && ((label % 4) == 0), "(-131072 <= label && label <= 131068) && ((label % 4) == 0)");
        instruction |= ((a.value() & 0x1) << 29);
        instruction |= ((p.value() & 0x1) << 19);
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= ((label >> 2) & 0x3fff) | ((((label >> 2) >> 14) & 0x3) << 20);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code brgz{,a}{,pn|,pt}  }<i>rs1</i>, <i>label</i>
     * Example disassembly syntax: {@code brgz,pn       %g0, L1: -131072}
     * <p>
     * Constraint: {@code (-131072 <= label && label <= 131068) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.3"
     */
    // Template#: 194, Serial#: 194
    public void brgz(final AnnulBit a, final BranchPredictionBit p, final GPR rs1, final int label) {
        int instruction = 0x0CC00000;
        checkConstraint((-131072 <= label && label <= 131068) && ((label % 4) == 0), "(-131072 <= label && label <= 131068) && ((label % 4) == 0)");
        instruction |= ((a.value() & 0x1) << 29);
        instruction |= ((p.value() & 0x1) << 19);
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= ((label >> 2) & 0x3fff) | ((((label >> 2) >> 14) & 0x3) << 20);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code brgez{,a}{,pn|,pt}  }<i>rs1</i>, <i>label</i>
     * Example disassembly syntax: {@code brgez,pn      %g0, L1: -131072}
     * <p>
     * Constraint: {@code (-131072 <= label && label <= 131068) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.3"
     */
    // Template#: 195, Serial#: 195
    public void brgez(final AnnulBit a, final BranchPredictionBit p, final GPR rs1, final int label) {
        int instruction = 0x0EC00000;
        checkConstraint((-131072 <= label && label <= 131068) && ((label % 4) == 0), "(-131072 <= label && label <= 131068) && ((label % 4) == 0)");
        instruction |= ((a.value() & 0x1) << 29);
        instruction |= ((p.value() & 0x1) << 19);
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= ((label >> 2) & 0x3fff) | ((((label >> 2) >> 14) & 0x3) << 20);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code brz  }<i>rs1</i>, <i>label</i>
     * Example disassembly syntax: {@code brz           %g0, L1: -131072}
     * <p>
     * Constraint: {@code (-131072 <= label && label <= 131068) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.3"
     */
    // Template#: 196, Serial#: 196
    public void brz(final GPR rs1, final int label) {
        int instruction = 0x02C80000;
        checkConstraint((-131072 <= label && label <= 131068) && ((label % 4) == 0), "(-131072 <= label && label <= 131068) && ((label % 4) == 0)");
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= ((label >> 2) & 0x3fff) | ((((label >> 2) >> 14) & 0x3) << 20);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code brlez  }<i>rs1</i>, <i>label</i>
     * Example disassembly syntax: {@code brlez         %g0, L1: -131072}
     * <p>
     * Constraint: {@code (-131072 <= label && label <= 131068) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.3"
     */
    // Template#: 197, Serial#: 197
    public void brlez(final GPR rs1, final int label) {
        int instruction = 0x04C80000;
        checkConstraint((-131072 <= label && label <= 131068) && ((label % 4) == 0), "(-131072 <= label && label <= 131068) && ((label % 4) == 0)");
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= ((label >> 2) & 0x3fff) | ((((label >> 2) >> 14) & 0x3) << 20);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code brlz  }<i>rs1</i>, <i>label</i>
     * Example disassembly syntax: {@code brlz          %g0, L1: -131072}
     * <p>
     * Constraint: {@code (-131072 <= label && label <= 131068) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.3"
     */
    // Template#: 198, Serial#: 198
    public void brlz(final GPR rs1, final int label) {
        int instruction = 0x06C80000;
        checkConstraint((-131072 <= label && label <= 131068) && ((label % 4) == 0), "(-131072 <= label && label <= 131068) && ((label % 4) == 0)");
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= ((label >> 2) & 0x3fff) | ((((label >> 2) >> 14) & 0x3) << 20);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code brnz  }<i>rs1</i>, <i>label</i>
     * Example disassembly syntax: {@code brnz          %g0, L1: -131072}
     * <p>
     * Constraint: {@code (-131072 <= label && label <= 131068) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.3"
     */
    // Template#: 199, Serial#: 199
    public void brnz(final GPR rs1, final int label) {
        int instruction = 0x0AC80000;
        checkConstraint((-131072 <= label && label <= 131068) && ((label % 4) == 0), "(-131072 <= label && label <= 131068) && ((label % 4) == 0)");
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= ((label >> 2) & 0x3fff) | ((((label >> 2) >> 14) & 0x3) << 20);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code brgz  }<i>rs1</i>, <i>label</i>
     * Example disassembly syntax: {@code brgz          %g0, L1: -131072}
     * <p>
     * Constraint: {@code (-131072 <= label && label <= 131068) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.3"
     */
    // Template#: 200, Serial#: 200
    public void brgz(final GPR rs1, final int label) {
        int instruction = 0x0CC80000;
        checkConstraint((-131072 <= label && label <= 131068) && ((label % 4) == 0), "(-131072 <= label && label <= 131068) && ((label % 4) == 0)");
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= ((label >> 2) & 0x3fff) | ((((label >> 2) >> 14) & 0x3) << 20);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code brgez  }<i>rs1</i>, <i>label</i>
     * Example disassembly syntax: {@code brgez         %g0, L1: -131072}
     * <p>
     * Constraint: {@code (-131072 <= label && label <= 131068) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.3"
     */
    // Template#: 201, Serial#: 201
    public void brgez(final GPR rs1, final int label) {
        int instruction = 0x0EC80000;
        checkConstraint((-131072 <= label && label <= 131068) && ((label % 4) == 0), "(-131072 <= label && label <= 131068) && ((label % 4) == 0)");
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= ((label >> 2) & 0x3fff) | ((((label >> 2) >> 14) & 0x3) << 20);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code br[z|lez|lz|nz|gz|gez]{,a}{,pn|,pt}  }<i>rs1</i>, <i>label</i>
     * Example disassembly syntax: {@code brz,pn        %g0, L1: -131072}
     * <p>
     * Constraint: {@code (-131072 <= label && label <= 131068) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.3"
     */
    // Template#: 202, Serial#: 202
    public void br(final BPr cond, final AnnulBit a, final BranchPredictionBit p, final GPR rs1, final int label) {
        int instruction = 0x00C00000;
        checkConstraint((-131072 <= label && label <= 131068) && ((label % 4) == 0), "(-131072 <= label && label <= 131068) && ((label % 4) == 0)");
        instruction |= ((cond.value() & 0x7) << 25);
        instruction |= ((a.value() & 0x1) << 29);
        instruction |= ((p.value() & 0x1) << 19);
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= ((label >> 2) & 0x3fff) | ((((label >> 2) >> 14) & 0x3) << 20);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fba{,a}  }<i>label</i>
     * Example disassembly syntax: {@code fba           L1: -8388608}
     * <p>
     * Constraint: {@code (-8388608 <= label && label <= 8388604) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.4"
     */
    // Template#: 203, Serial#: 203
    public void fba(final AnnulBit a, final int label) {
        int instruction = 0x11800000;
        checkConstraint((-8388608 <= label && label <= 8388604) && ((label % 4) == 0), "(-8388608 <= label && label <= 8388604) && ((label % 4) == 0)");
        instruction |= ((a.value() & 0x1) << 29);
        instruction |= ((label >> 2) & 0x3fffff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fbn{,a}  }<i>label</i>
     * Example disassembly syntax: {@code fbn           L1: -8388608}
     * <p>
     * Constraint: {@code (-8388608 <= label && label <= 8388604) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.4"
     */
    // Template#: 204, Serial#: 204
    public void fbn(final AnnulBit a, final int label) {
        int instruction = 0x01800000;
        checkConstraint((-8388608 <= label && label <= 8388604) && ((label % 4) == 0), "(-8388608 <= label && label <= 8388604) && ((label % 4) == 0)");
        instruction |= ((a.value() & 0x1) << 29);
        instruction |= ((label >> 2) & 0x3fffff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fbu{,a}  }<i>label</i>
     * Example disassembly syntax: {@code fbu           L1: -8388608}
     * <p>
     * Constraint: {@code (-8388608 <= label && label <= 8388604) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.4"
     */
    // Template#: 205, Serial#: 205
    public void fbu(final AnnulBit a, final int label) {
        int instruction = 0x0F800000;
        checkConstraint((-8388608 <= label && label <= 8388604) && ((label % 4) == 0), "(-8388608 <= label && label <= 8388604) && ((label % 4) == 0)");
        instruction |= ((a.value() & 0x1) << 29);
        instruction |= ((label >> 2) & 0x3fffff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fbg{,a}  }<i>label</i>
     * Example disassembly syntax: {@code fbg           L1: -8388608}
     * <p>
     * Constraint: {@code (-8388608 <= label && label <= 8388604) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.4"
     */
    // Template#: 206, Serial#: 206
    public void fbg(final AnnulBit a, final int label) {
        int instruction = 0x0D800000;
        checkConstraint((-8388608 <= label && label <= 8388604) && ((label % 4) == 0), "(-8388608 <= label && label <= 8388604) && ((label % 4) == 0)");
        instruction |= ((a.value() & 0x1) << 29);
        instruction |= ((label >> 2) & 0x3fffff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fbug{,a}  }<i>label</i>
     * Example disassembly syntax: {@code fbug          L1: -8388608}
     * <p>
     * Constraint: {@code (-8388608 <= label && label <= 8388604) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.4"
     */
    // Template#: 207, Serial#: 207
    public void fbug(final AnnulBit a, final int label) {
        int instruction = 0x0B800000;
        checkConstraint((-8388608 <= label && label <= 8388604) && ((label % 4) == 0), "(-8388608 <= label && label <= 8388604) && ((label % 4) == 0)");
        instruction |= ((a.value() & 0x1) << 29);
        instruction |= ((label >> 2) & 0x3fffff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fbl{,a}  }<i>label</i>
     * Example disassembly syntax: {@code fbl           L1: -8388608}
     * <p>
     * Constraint: {@code (-8388608 <= label && label <= 8388604) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.4"
     */
    // Template#: 208, Serial#: 208
    public void fbl(final AnnulBit a, final int label) {
        int instruction = 0x09800000;
        checkConstraint((-8388608 <= label && label <= 8388604) && ((label % 4) == 0), "(-8388608 <= label && label <= 8388604) && ((label % 4) == 0)");
        instruction |= ((a.value() & 0x1) << 29);
        instruction |= ((label >> 2) & 0x3fffff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fbul{,a}  }<i>label</i>
     * Example disassembly syntax: {@code fbul          L1: -8388608}
     * <p>
     * Constraint: {@code (-8388608 <= label && label <= 8388604) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.4"
     */
    // Template#: 209, Serial#: 209
    public void fbul(final AnnulBit a, final int label) {
        int instruction = 0x07800000;
        checkConstraint((-8388608 <= label && label <= 8388604) && ((label % 4) == 0), "(-8388608 <= label && label <= 8388604) && ((label % 4) == 0)");
        instruction |= ((a.value() & 0x1) << 29);
        instruction |= ((label >> 2) & 0x3fffff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fblg{,a}  }<i>label</i>
     * Example disassembly syntax: {@code fblg          L1: -8388608}
     * <p>
     * Constraint: {@code (-8388608 <= label && label <= 8388604) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.4"
     */
    // Template#: 210, Serial#: 210
    public void fblg(final AnnulBit a, final int label) {
        int instruction = 0x05800000;
        checkConstraint((-8388608 <= label && label <= 8388604) && ((label % 4) == 0), "(-8388608 <= label && label <= 8388604) && ((label % 4) == 0)");
        instruction |= ((a.value() & 0x1) << 29);
        instruction |= ((label >> 2) & 0x3fffff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fbne{,a}  }<i>label</i>
     * Example disassembly syntax: {@code fbne          L1: -8388608}
     * <p>
     * Constraint: {@code (-8388608 <= label && label <= 8388604) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.4"
     */
    // Template#: 211, Serial#: 211
    public void fbne(final AnnulBit a, final int label) {
        int instruction = 0x03800000;
        checkConstraint((-8388608 <= label && label <= 8388604) && ((label % 4) == 0), "(-8388608 <= label && label <= 8388604) && ((label % 4) == 0)");
        instruction |= ((a.value() & 0x1) << 29);
        instruction |= ((label >> 2) & 0x3fffff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fbe{,a}  }<i>label</i>
     * Example disassembly syntax: {@code fbe           L1: -8388608}
     * <p>
     * Constraint: {@code (-8388608 <= label && label <= 8388604) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.4"
     */
    // Template#: 212, Serial#: 212
    public void fbe(final AnnulBit a, final int label) {
        int instruction = 0x13800000;
        checkConstraint((-8388608 <= label && label <= 8388604) && ((label % 4) == 0), "(-8388608 <= label && label <= 8388604) && ((label % 4) == 0)");
        instruction |= ((a.value() & 0x1) << 29);
        instruction |= ((label >> 2) & 0x3fffff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fbue{,a}  }<i>label</i>
     * Example disassembly syntax: {@code fbue          L1: -8388608}
     * <p>
     * Constraint: {@code (-8388608 <= label && label <= 8388604) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.4"
     */
    // Template#: 213, Serial#: 213
    public void fbue(final AnnulBit a, final int label) {
        int instruction = 0x15800000;
        checkConstraint((-8388608 <= label && label <= 8388604) && ((label % 4) == 0), "(-8388608 <= label && label <= 8388604) && ((label % 4) == 0)");
        instruction |= ((a.value() & 0x1) << 29);
        instruction |= ((label >> 2) & 0x3fffff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fbge{,a}  }<i>label</i>
     * Example disassembly syntax: {@code fbge          L1: -8388608}
     * <p>
     * Constraint: {@code (-8388608 <= label && label <= 8388604) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.4"
     */
    // Template#: 214, Serial#: 214
    public void fbge(final AnnulBit a, final int label) {
        int instruction = 0x17800000;
        checkConstraint((-8388608 <= label && label <= 8388604) && ((label % 4) == 0), "(-8388608 <= label && label <= 8388604) && ((label % 4) == 0)");
        instruction |= ((a.value() & 0x1) << 29);
        instruction |= ((label >> 2) & 0x3fffff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fbuge{,a}  }<i>label</i>
     * Example disassembly syntax: {@code fbuge         L1: -8388608}
     * <p>
     * Constraint: {@code (-8388608 <= label && label <= 8388604) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.4"
     */
    // Template#: 215, Serial#: 215
    public void fbuge(final AnnulBit a, final int label) {
        int instruction = 0x19800000;
        checkConstraint((-8388608 <= label && label <= 8388604) && ((label % 4) == 0), "(-8388608 <= label && label <= 8388604) && ((label % 4) == 0)");
        instruction |= ((a.value() & 0x1) << 29);
        instruction |= ((label >> 2) & 0x3fffff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fble{,a}  }<i>label</i>
     * Example disassembly syntax: {@code fble          L1: -8388608}
     * <p>
     * Constraint: {@code (-8388608 <= label && label <= 8388604) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.4"
     */
    // Template#: 216, Serial#: 216
    public void fble(final AnnulBit a, final int label) {
        int instruction = 0x1B800000;
        checkConstraint((-8388608 <= label && label <= 8388604) && ((label % 4) == 0), "(-8388608 <= label && label <= 8388604) && ((label % 4) == 0)");
        instruction |= ((a.value() & 0x1) << 29);
        instruction |= ((label >> 2) & 0x3fffff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fbule{,a}  }<i>label</i>
     * Example disassembly syntax: {@code fbule         L1: -8388608}
     * <p>
     * Constraint: {@code (-8388608 <= label && label <= 8388604) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.4"
     */
    // Template#: 217, Serial#: 217
    public void fbule(final AnnulBit a, final int label) {
        int instruction = 0x1D800000;
        checkConstraint((-8388608 <= label && label <= 8388604) && ((label % 4) == 0), "(-8388608 <= label && label <= 8388604) && ((label % 4) == 0)");
        instruction |= ((a.value() & 0x1) << 29);
        instruction |= ((label >> 2) & 0x3fffff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fbo{,a}  }<i>label</i>
     * Example disassembly syntax: {@code fbo           L1: -8388608}
     * <p>
     * Constraint: {@code (-8388608 <= label && label <= 8388604) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.4"
     */
    // Template#: 218, Serial#: 218
    public void fbo(final AnnulBit a, final int label) {
        int instruction = 0x1F800000;
        checkConstraint((-8388608 <= label && label <= 8388604) && ((label % 4) == 0), "(-8388608 <= label && label <= 8388604) && ((label % 4) == 0)");
        instruction |= ((a.value() & 0x1) << 29);
        instruction |= ((label >> 2) & 0x3fffff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fba  }<i>label</i>
     * Example disassembly syntax: {@code fba           L1: -8388608}
     * <p>
     * Constraint: {@code (-8388608 <= label && label <= 8388604) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.4"
     */
    // Template#: 219, Serial#: 219
    public void fba(final int label) {
        int instruction = 0x11800000;
        checkConstraint((-8388608 <= label && label <= 8388604) && ((label % 4) == 0), "(-8388608 <= label && label <= 8388604) && ((label % 4) == 0)");
        instruction |= ((label >> 2) & 0x3fffff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fbn  }<i>label</i>
     * Example disassembly syntax: {@code fbn           L1: -8388608}
     * <p>
     * Constraint: {@code (-8388608 <= label && label <= 8388604) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.4"
     */
    // Template#: 220, Serial#: 220
    public void fbn(final int label) {
        int instruction = 0x01800000;
        checkConstraint((-8388608 <= label && label <= 8388604) && ((label % 4) == 0), "(-8388608 <= label && label <= 8388604) && ((label % 4) == 0)");
        instruction |= ((label >> 2) & 0x3fffff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fbu  }<i>label</i>
     * Example disassembly syntax: {@code fbu           L1: -8388608}
     * <p>
     * Constraint: {@code (-8388608 <= label && label <= 8388604) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.4"
     */
    // Template#: 221, Serial#: 221
    public void fbu(final int label) {
        int instruction = 0x0F800000;
        checkConstraint((-8388608 <= label && label <= 8388604) && ((label % 4) == 0), "(-8388608 <= label && label <= 8388604) && ((label % 4) == 0)");
        instruction |= ((label >> 2) & 0x3fffff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fbg  }<i>label</i>
     * Example disassembly syntax: {@code fbg           L1: -8388608}
     * <p>
     * Constraint: {@code (-8388608 <= label && label <= 8388604) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.4"
     */
    // Template#: 222, Serial#: 222
    public void fbg(final int label) {
        int instruction = 0x0D800000;
        checkConstraint((-8388608 <= label && label <= 8388604) && ((label % 4) == 0), "(-8388608 <= label && label <= 8388604) && ((label % 4) == 0)");
        instruction |= ((label >> 2) & 0x3fffff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fbug  }<i>label</i>
     * Example disassembly syntax: {@code fbug          L1: -8388608}
     * <p>
     * Constraint: {@code (-8388608 <= label && label <= 8388604) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.4"
     */
    // Template#: 223, Serial#: 223
    public void fbug(final int label) {
        int instruction = 0x0B800000;
        checkConstraint((-8388608 <= label && label <= 8388604) && ((label % 4) == 0), "(-8388608 <= label && label <= 8388604) && ((label % 4) == 0)");
        instruction |= ((label >> 2) & 0x3fffff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fbl  }<i>label</i>
     * Example disassembly syntax: {@code fbl           L1: -8388608}
     * <p>
     * Constraint: {@code (-8388608 <= label && label <= 8388604) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.4"
     */
    // Template#: 224, Serial#: 224
    public void fbl(final int label) {
        int instruction = 0x09800000;
        checkConstraint((-8388608 <= label && label <= 8388604) && ((label % 4) == 0), "(-8388608 <= label && label <= 8388604) && ((label % 4) == 0)");
        instruction |= ((label >> 2) & 0x3fffff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fbul  }<i>label</i>
     * Example disassembly syntax: {@code fbul          L1: -8388608}
     * <p>
     * Constraint: {@code (-8388608 <= label && label <= 8388604) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.4"
     */
    // Template#: 225, Serial#: 225
    public void fbul(final int label) {
        int instruction = 0x07800000;
        checkConstraint((-8388608 <= label && label <= 8388604) && ((label % 4) == 0), "(-8388608 <= label && label <= 8388604) && ((label % 4) == 0)");
        instruction |= ((label >> 2) & 0x3fffff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fblg  }<i>label</i>
     * Example disassembly syntax: {@code fblg          L1: -8388608}
     * <p>
     * Constraint: {@code (-8388608 <= label && label <= 8388604) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.4"
     */
    // Template#: 226, Serial#: 226
    public void fblg(final int label) {
        int instruction = 0x05800000;
        checkConstraint((-8388608 <= label && label <= 8388604) && ((label % 4) == 0), "(-8388608 <= label && label <= 8388604) && ((label % 4) == 0)");
        instruction |= ((label >> 2) & 0x3fffff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fbne  }<i>label</i>
     * Example disassembly syntax: {@code fbne          L1: -8388608}
     * <p>
     * Constraint: {@code (-8388608 <= label && label <= 8388604) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.4"
     */
    // Template#: 227, Serial#: 227
    public void fbne(final int label) {
        int instruction = 0x03800000;
        checkConstraint((-8388608 <= label && label <= 8388604) && ((label % 4) == 0), "(-8388608 <= label && label <= 8388604) && ((label % 4) == 0)");
        instruction |= ((label >> 2) & 0x3fffff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fbe  }<i>label</i>
     * Example disassembly syntax: {@code fbe           L1: -8388608}
     * <p>
     * Constraint: {@code (-8388608 <= label && label <= 8388604) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.4"
     */
    // Template#: 228, Serial#: 228
    public void fbe(final int label) {
        int instruction = 0x13800000;
        checkConstraint((-8388608 <= label && label <= 8388604) && ((label % 4) == 0), "(-8388608 <= label && label <= 8388604) && ((label % 4) == 0)");
        instruction |= ((label >> 2) & 0x3fffff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fbue  }<i>label</i>
     * Example disassembly syntax: {@code fbue          L1: -8388608}
     * <p>
     * Constraint: {@code (-8388608 <= label && label <= 8388604) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.4"
     */
    // Template#: 229, Serial#: 229
    public void fbue(final int label) {
        int instruction = 0x15800000;
        checkConstraint((-8388608 <= label && label <= 8388604) && ((label % 4) == 0), "(-8388608 <= label && label <= 8388604) && ((label % 4) == 0)");
        instruction |= ((label >> 2) & 0x3fffff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fbge  }<i>label</i>
     * Example disassembly syntax: {@code fbge          L1: -8388608}
     * <p>
     * Constraint: {@code (-8388608 <= label && label <= 8388604) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.4"
     */
    // Template#: 230, Serial#: 230
    public void fbge(final int label) {
        int instruction = 0x17800000;
        checkConstraint((-8388608 <= label && label <= 8388604) && ((label % 4) == 0), "(-8388608 <= label && label <= 8388604) && ((label % 4) == 0)");
        instruction |= ((label >> 2) & 0x3fffff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fbuge  }<i>label</i>
     * Example disassembly syntax: {@code fbuge         L1: -8388608}
     * <p>
     * Constraint: {@code (-8388608 <= label && label <= 8388604) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.4"
     */
    // Template#: 231, Serial#: 231
    public void fbuge(final int label) {
        int instruction = 0x19800000;
        checkConstraint((-8388608 <= label && label <= 8388604) && ((label % 4) == 0), "(-8388608 <= label && label <= 8388604) && ((label % 4) == 0)");
        instruction |= ((label >> 2) & 0x3fffff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fble  }<i>label</i>
     * Example disassembly syntax: {@code fble          L1: -8388608}
     * <p>
     * Constraint: {@code (-8388608 <= label && label <= 8388604) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.4"
     */
    // Template#: 232, Serial#: 232
    public void fble(final int label) {
        int instruction = 0x1B800000;
        checkConstraint((-8388608 <= label && label <= 8388604) && ((label % 4) == 0), "(-8388608 <= label && label <= 8388604) && ((label % 4) == 0)");
        instruction |= ((label >> 2) & 0x3fffff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fbule  }<i>label</i>
     * Example disassembly syntax: {@code fbule         L1: -8388608}
     * <p>
     * Constraint: {@code (-8388608 <= label && label <= 8388604) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.4"
     */
    // Template#: 233, Serial#: 233
    public void fbule(final int label) {
        int instruction = 0x1D800000;
        checkConstraint((-8388608 <= label && label <= 8388604) && ((label % 4) == 0), "(-8388608 <= label && label <= 8388604) && ((label % 4) == 0)");
        instruction |= ((label >> 2) & 0x3fffff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fbo  }<i>label</i>
     * Example disassembly syntax: {@code fbo           L1: -8388608}
     * <p>
     * Constraint: {@code (-8388608 <= label && label <= 8388604) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.4"
     */
    // Template#: 234, Serial#: 234
    public void fbo(final int label) {
        int instruction = 0x1F800000;
        checkConstraint((-8388608 <= label && label <= 8388604) && ((label % 4) == 0), "(-8388608 <= label && label <= 8388604) && ((label % 4) == 0)");
        instruction |= ((label >> 2) & 0x3fffff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fb[a|n|u|g|ug|l|ul|lg|ne|e|ue|ge|uge|le|ule|o]{,a}  }<i>label</i>
     * Example disassembly syntax: {@code fba           L1: -8388608}
     * <p>
     * Constraint: {@code (-8388608 <= label && label <= 8388604) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.4"
     */
    // Template#: 235, Serial#: 235
    public void fb(final FBfcc cond, final AnnulBit a, final int label) {
        int instruction = 0x01800000;
        checkConstraint((-8388608 <= label && label <= 8388604) && ((label % 4) == 0), "(-8388608 <= label && label <= 8388604) && ((label % 4) == 0)");
        instruction |= ((cond.value() & 0xf) << 25);
        instruction |= ((a.value() & 0x1) << 29);
        instruction |= ((label >> 2) & 0x3fffff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fba{,a}{,pn|,pt}  }<i>n</i>, <i>label</i>
     * Example disassembly syntax: {@code fba,pn        %fcc0, L1: -1048576}
     * <p>
     * Constraint: {@code (-1048576 <= label && label <= 1048572) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.5"
     */
    // Template#: 236, Serial#: 236
    public void fba(final AnnulBit a, final BranchPredictionBit p, final FCCOperand n, final int label) {
        int instruction = 0x11400000;
        checkConstraint((-1048576 <= label && label <= 1048572) && ((label % 4) == 0), "(-1048576 <= label && label <= 1048572) && ((label % 4) == 0)");
        instruction |= ((a.value() & 0x1) << 29);
        instruction |= ((p.value() & 0x1) << 19);
        instruction |= ((n.value() & 0x3) << 20);
        instruction |= ((label >> 2) & 0x7ffff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fbn{,a}{,pn|,pt}  }<i>n</i>, <i>label</i>
     * Example disassembly syntax: {@code fbn,pn        %fcc0, L1: -1048576}
     * <p>
     * Constraint: {@code (-1048576 <= label && label <= 1048572) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.5"
     */
    // Template#: 237, Serial#: 237
    public void fbn(final AnnulBit a, final BranchPredictionBit p, final FCCOperand n, final int label) {
        int instruction = 0x01400000;
        checkConstraint((-1048576 <= label && label <= 1048572) && ((label % 4) == 0), "(-1048576 <= label && label <= 1048572) && ((label % 4) == 0)");
        instruction |= ((a.value() & 0x1) << 29);
        instruction |= ((p.value() & 0x1) << 19);
        instruction |= ((n.value() & 0x3) << 20);
        instruction |= ((label >> 2) & 0x7ffff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fbu{,a}{,pn|,pt}  }<i>n</i>, <i>label</i>
     * Example disassembly syntax: {@code fbu,pn        %fcc0, L1: -1048576}
     * <p>
     * Constraint: {@code (-1048576 <= label && label <= 1048572) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.5"
     */
    // Template#: 238, Serial#: 238
    public void fbu(final AnnulBit a, final BranchPredictionBit p, final FCCOperand n, final int label) {
        int instruction = 0x0F400000;
        checkConstraint((-1048576 <= label && label <= 1048572) && ((label % 4) == 0), "(-1048576 <= label && label <= 1048572) && ((label % 4) == 0)");
        instruction |= ((a.value() & 0x1) << 29);
        instruction |= ((p.value() & 0x1) << 19);
        instruction |= ((n.value() & 0x3) << 20);
        instruction |= ((label >> 2) & 0x7ffff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fbg{,a}{,pn|,pt}  }<i>n</i>, <i>label</i>
     * Example disassembly syntax: {@code fbg,pn        %fcc0, L1: -1048576}
     * <p>
     * Constraint: {@code (-1048576 <= label && label <= 1048572) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.5"
     */
    // Template#: 239, Serial#: 239
    public void fbg(final AnnulBit a, final BranchPredictionBit p, final FCCOperand n, final int label) {
        int instruction = 0x0D400000;
        checkConstraint((-1048576 <= label && label <= 1048572) && ((label % 4) == 0), "(-1048576 <= label && label <= 1048572) && ((label % 4) == 0)");
        instruction |= ((a.value() & 0x1) << 29);
        instruction |= ((p.value() & 0x1) << 19);
        instruction |= ((n.value() & 0x3) << 20);
        instruction |= ((label >> 2) & 0x7ffff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fbug{,a}{,pn|,pt}  }<i>n</i>, <i>label</i>
     * Example disassembly syntax: {@code fbug,pn       %fcc0, L1: -1048576}
     * <p>
     * Constraint: {@code (-1048576 <= label && label <= 1048572) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.5"
     */
    // Template#: 240, Serial#: 240
    public void fbug(final AnnulBit a, final BranchPredictionBit p, final FCCOperand n, final int label) {
        int instruction = 0x0B400000;
        checkConstraint((-1048576 <= label && label <= 1048572) && ((label % 4) == 0), "(-1048576 <= label && label <= 1048572) && ((label % 4) == 0)");
        instruction |= ((a.value() & 0x1) << 29);
        instruction |= ((p.value() & 0x1) << 19);
        instruction |= ((n.value() & 0x3) << 20);
        instruction |= ((label >> 2) & 0x7ffff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fbl{,a}{,pn|,pt}  }<i>n</i>, <i>label</i>
     * Example disassembly syntax: {@code fbl,pn        %fcc0, L1: -1048576}
     * <p>
     * Constraint: {@code (-1048576 <= label && label <= 1048572) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.5"
     */
    // Template#: 241, Serial#: 241
    public void fbl(final AnnulBit a, final BranchPredictionBit p, final FCCOperand n, final int label) {
        int instruction = 0x09400000;
        checkConstraint((-1048576 <= label && label <= 1048572) && ((label % 4) == 0), "(-1048576 <= label && label <= 1048572) && ((label % 4) == 0)");
        instruction |= ((a.value() & 0x1) << 29);
        instruction |= ((p.value() & 0x1) << 19);
        instruction |= ((n.value() & 0x3) << 20);
        instruction |= ((label >> 2) & 0x7ffff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fbul{,a}{,pn|,pt}  }<i>n</i>, <i>label</i>
     * Example disassembly syntax: {@code fbul,pn       %fcc0, L1: -1048576}
     * <p>
     * Constraint: {@code (-1048576 <= label && label <= 1048572) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.5"
     */
    // Template#: 242, Serial#: 242
    public void fbul(final AnnulBit a, final BranchPredictionBit p, final FCCOperand n, final int label) {
        int instruction = 0x07400000;
        checkConstraint((-1048576 <= label && label <= 1048572) && ((label % 4) == 0), "(-1048576 <= label && label <= 1048572) && ((label % 4) == 0)");
        instruction |= ((a.value() & 0x1) << 29);
        instruction |= ((p.value() & 0x1) << 19);
        instruction |= ((n.value() & 0x3) << 20);
        instruction |= ((label >> 2) & 0x7ffff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fblg{,a}{,pn|,pt}  }<i>n</i>, <i>label</i>
     * Example disassembly syntax: {@code fblg,pn       %fcc0, L1: -1048576}
     * <p>
     * Constraint: {@code (-1048576 <= label && label <= 1048572) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.5"
     */
    // Template#: 243, Serial#: 243
    public void fblg(final AnnulBit a, final BranchPredictionBit p, final FCCOperand n, final int label) {
        int instruction = 0x05400000;
        checkConstraint((-1048576 <= label && label <= 1048572) && ((label % 4) == 0), "(-1048576 <= label && label <= 1048572) && ((label % 4) == 0)");
        instruction |= ((a.value() & 0x1) << 29);
        instruction |= ((p.value() & 0x1) << 19);
        instruction |= ((n.value() & 0x3) << 20);
        instruction |= ((label >> 2) & 0x7ffff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fbne{,a}{,pn|,pt}  }<i>n</i>, <i>label</i>
     * Example disassembly syntax: {@code fbne,pn       %fcc0, L1: -1048576}
     * <p>
     * Constraint: {@code (-1048576 <= label && label <= 1048572) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.5"
     */
    // Template#: 244, Serial#: 244
    public void fbne(final AnnulBit a, final BranchPredictionBit p, final FCCOperand n, final int label) {
        int instruction = 0x03400000;
        checkConstraint((-1048576 <= label && label <= 1048572) && ((label % 4) == 0), "(-1048576 <= label && label <= 1048572) && ((label % 4) == 0)");
        instruction |= ((a.value() & 0x1) << 29);
        instruction |= ((p.value() & 0x1) << 19);
        instruction |= ((n.value() & 0x3) << 20);
        instruction |= ((label >> 2) & 0x7ffff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fbe{,a}{,pn|,pt}  }<i>n</i>, <i>label</i>
     * Example disassembly syntax: {@code fbe,pn        %fcc0, L1: -1048576}
     * <p>
     * Constraint: {@code (-1048576 <= label && label <= 1048572) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.5"
     */
    // Template#: 245, Serial#: 245
    public void fbe(final AnnulBit a, final BranchPredictionBit p, final FCCOperand n, final int label) {
        int instruction = 0x13400000;
        checkConstraint((-1048576 <= label && label <= 1048572) && ((label % 4) == 0), "(-1048576 <= label && label <= 1048572) && ((label % 4) == 0)");
        instruction |= ((a.value() & 0x1) << 29);
        instruction |= ((p.value() & 0x1) << 19);
        instruction |= ((n.value() & 0x3) << 20);
        instruction |= ((label >> 2) & 0x7ffff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fbue{,a}{,pn|,pt}  }<i>n</i>, <i>label</i>
     * Example disassembly syntax: {@code fbue,pn       %fcc0, L1: -1048576}
     * <p>
     * Constraint: {@code (-1048576 <= label && label <= 1048572) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.5"
     */
    // Template#: 246, Serial#: 246
    public void fbue(final AnnulBit a, final BranchPredictionBit p, final FCCOperand n, final int label) {
        int instruction = 0x15400000;
        checkConstraint((-1048576 <= label && label <= 1048572) && ((label % 4) == 0), "(-1048576 <= label && label <= 1048572) && ((label % 4) == 0)");
        instruction |= ((a.value() & 0x1) << 29);
        instruction |= ((p.value() & 0x1) << 19);
        instruction |= ((n.value() & 0x3) << 20);
        instruction |= ((label >> 2) & 0x7ffff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fbge{,a}{,pn|,pt}  }<i>n</i>, <i>label</i>
     * Example disassembly syntax: {@code fbge,pn       %fcc0, L1: -1048576}
     * <p>
     * Constraint: {@code (-1048576 <= label && label <= 1048572) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.5"
     */
    // Template#: 247, Serial#: 247
    public void fbge(final AnnulBit a, final BranchPredictionBit p, final FCCOperand n, final int label) {
        int instruction = 0x17400000;
        checkConstraint((-1048576 <= label && label <= 1048572) && ((label % 4) == 0), "(-1048576 <= label && label <= 1048572) && ((label % 4) == 0)");
        instruction |= ((a.value() & 0x1) << 29);
        instruction |= ((p.value() & 0x1) << 19);
        instruction |= ((n.value() & 0x3) << 20);
        instruction |= ((label >> 2) & 0x7ffff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fbuge{,a}{,pn|,pt}  }<i>n</i>, <i>label</i>
     * Example disassembly syntax: {@code fbuge,pn      %fcc0, L1: -1048576}
     * <p>
     * Constraint: {@code (-1048576 <= label && label <= 1048572) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.5"
     */
    // Template#: 248, Serial#: 248
    public void fbuge(final AnnulBit a, final BranchPredictionBit p, final FCCOperand n, final int label) {
        int instruction = 0x19400000;
        checkConstraint((-1048576 <= label && label <= 1048572) && ((label % 4) == 0), "(-1048576 <= label && label <= 1048572) && ((label % 4) == 0)");
        instruction |= ((a.value() & 0x1) << 29);
        instruction |= ((p.value() & 0x1) << 19);
        instruction |= ((n.value() & 0x3) << 20);
        instruction |= ((label >> 2) & 0x7ffff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fble{,a}{,pn|,pt}  }<i>n</i>, <i>label</i>
     * Example disassembly syntax: {@code fble,pn       %fcc0, L1: -1048576}
     * <p>
     * Constraint: {@code (-1048576 <= label && label <= 1048572) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.5"
     */
    // Template#: 249, Serial#: 249
    public void fble(final AnnulBit a, final BranchPredictionBit p, final FCCOperand n, final int label) {
        int instruction = 0x1B400000;
        checkConstraint((-1048576 <= label && label <= 1048572) && ((label % 4) == 0), "(-1048576 <= label && label <= 1048572) && ((label % 4) == 0)");
        instruction |= ((a.value() & 0x1) << 29);
        instruction |= ((p.value() & 0x1) << 19);
        instruction |= ((n.value() & 0x3) << 20);
        instruction |= ((label >> 2) & 0x7ffff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fbule{,a}{,pn|,pt}  }<i>n</i>, <i>label</i>
     * Example disassembly syntax: {@code fbule,pn      %fcc0, L1: -1048576}
     * <p>
     * Constraint: {@code (-1048576 <= label && label <= 1048572) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.5"
     */
    // Template#: 250, Serial#: 250
    public void fbule(final AnnulBit a, final BranchPredictionBit p, final FCCOperand n, final int label) {
        int instruction = 0x1D400000;
        checkConstraint((-1048576 <= label && label <= 1048572) && ((label % 4) == 0), "(-1048576 <= label && label <= 1048572) && ((label % 4) == 0)");
        instruction |= ((a.value() & 0x1) << 29);
        instruction |= ((p.value() & 0x1) << 19);
        instruction |= ((n.value() & 0x3) << 20);
        instruction |= ((label >> 2) & 0x7ffff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fbo{,a}{,pn|,pt}  }<i>n</i>, <i>label</i>
     * Example disassembly syntax: {@code fbo,pn        %fcc0, L1: -1048576}
     * <p>
     * Constraint: {@code (-1048576 <= label && label <= 1048572) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.5"
     */
    // Template#: 251, Serial#: 251
    public void fbo(final AnnulBit a, final BranchPredictionBit p, final FCCOperand n, final int label) {
        int instruction = 0x1F400000;
        checkConstraint((-1048576 <= label && label <= 1048572) && ((label % 4) == 0), "(-1048576 <= label && label <= 1048572) && ((label % 4) == 0)");
        instruction |= ((a.value() & 0x1) << 29);
        instruction |= ((p.value() & 0x1) << 19);
        instruction |= ((n.value() & 0x3) << 20);
        instruction |= ((label >> 2) & 0x7ffff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fba  }<i>n</i>, <i>label</i>
     * Example disassembly syntax: {@code fba           %fcc0, L1: -1048576}
     * <p>
     * Constraint: {@code (-1048576 <= label && label <= 1048572) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.5"
     */
    // Template#: 252, Serial#: 252
    public void fba(final FCCOperand n, final int label) {
        int instruction = 0x11480000;
        checkConstraint((-1048576 <= label && label <= 1048572) && ((label % 4) == 0), "(-1048576 <= label && label <= 1048572) && ((label % 4) == 0)");
        instruction |= ((n.value() & 0x3) << 20);
        instruction |= ((label >> 2) & 0x7ffff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fbn  }<i>n</i>, <i>label</i>
     * Example disassembly syntax: {@code fbn           %fcc0, L1: -1048576}
     * <p>
     * Constraint: {@code (-1048576 <= label && label <= 1048572) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.5"
     */
    // Template#: 253, Serial#: 253
    public void fbn(final FCCOperand n, final int label) {
        int instruction = 0x01480000;
        checkConstraint((-1048576 <= label && label <= 1048572) && ((label % 4) == 0), "(-1048576 <= label && label <= 1048572) && ((label % 4) == 0)");
        instruction |= ((n.value() & 0x3) << 20);
        instruction |= ((label >> 2) & 0x7ffff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fbu  }<i>n</i>, <i>label</i>
     * Example disassembly syntax: {@code fbu           %fcc0, L1: -1048576}
     * <p>
     * Constraint: {@code (-1048576 <= label && label <= 1048572) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.5"
     */
    // Template#: 254, Serial#: 254
    public void fbu(final FCCOperand n, final int label) {
        int instruction = 0x0F480000;
        checkConstraint((-1048576 <= label && label <= 1048572) && ((label % 4) == 0), "(-1048576 <= label && label <= 1048572) && ((label % 4) == 0)");
        instruction |= ((n.value() & 0x3) << 20);
        instruction |= ((label >> 2) & 0x7ffff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fbg  }<i>n</i>, <i>label</i>
     * Example disassembly syntax: {@code fbg           %fcc0, L1: -1048576}
     * <p>
     * Constraint: {@code (-1048576 <= label && label <= 1048572) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.5"
     */
    // Template#: 255, Serial#: 255
    public void fbg(final FCCOperand n, final int label) {
        int instruction = 0x0D480000;
        checkConstraint((-1048576 <= label && label <= 1048572) && ((label % 4) == 0), "(-1048576 <= label && label <= 1048572) && ((label % 4) == 0)");
        instruction |= ((n.value() & 0x3) << 20);
        instruction |= ((label >> 2) & 0x7ffff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fbug  }<i>n</i>, <i>label</i>
     * Example disassembly syntax: {@code fbug          %fcc0, L1: -1048576}
     * <p>
     * Constraint: {@code (-1048576 <= label && label <= 1048572) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.5"
     */
    // Template#: 256, Serial#: 256
    public void fbug(final FCCOperand n, final int label) {
        int instruction = 0x0B480000;
        checkConstraint((-1048576 <= label && label <= 1048572) && ((label % 4) == 0), "(-1048576 <= label && label <= 1048572) && ((label % 4) == 0)");
        instruction |= ((n.value() & 0x3) << 20);
        instruction |= ((label >> 2) & 0x7ffff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fbl  }<i>n</i>, <i>label</i>
     * Example disassembly syntax: {@code fbl           %fcc0, L1: -1048576}
     * <p>
     * Constraint: {@code (-1048576 <= label && label <= 1048572) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.5"
     */
    // Template#: 257, Serial#: 257
    public void fbl(final FCCOperand n, final int label) {
        int instruction = 0x09480000;
        checkConstraint((-1048576 <= label && label <= 1048572) && ((label % 4) == 0), "(-1048576 <= label && label <= 1048572) && ((label % 4) == 0)");
        instruction |= ((n.value() & 0x3) << 20);
        instruction |= ((label >> 2) & 0x7ffff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fbul  }<i>n</i>, <i>label</i>
     * Example disassembly syntax: {@code fbul          %fcc0, L1: -1048576}
     * <p>
     * Constraint: {@code (-1048576 <= label && label <= 1048572) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.5"
     */
    // Template#: 258, Serial#: 258
    public void fbul(final FCCOperand n, final int label) {
        int instruction = 0x07480000;
        checkConstraint((-1048576 <= label && label <= 1048572) && ((label % 4) == 0), "(-1048576 <= label && label <= 1048572) && ((label % 4) == 0)");
        instruction |= ((n.value() & 0x3) << 20);
        instruction |= ((label >> 2) & 0x7ffff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fblg  }<i>n</i>, <i>label</i>
     * Example disassembly syntax: {@code fblg          %fcc0, L1: -1048576}
     * <p>
     * Constraint: {@code (-1048576 <= label && label <= 1048572) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.5"
     */
    // Template#: 259, Serial#: 259
    public void fblg(final FCCOperand n, final int label) {
        int instruction = 0x05480000;
        checkConstraint((-1048576 <= label && label <= 1048572) && ((label % 4) == 0), "(-1048576 <= label && label <= 1048572) && ((label % 4) == 0)");
        instruction |= ((n.value() & 0x3) << 20);
        instruction |= ((label >> 2) & 0x7ffff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fbne  }<i>n</i>, <i>label</i>
     * Example disassembly syntax: {@code fbne          %fcc0, L1: -1048576}
     * <p>
     * Constraint: {@code (-1048576 <= label && label <= 1048572) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.5"
     */
    // Template#: 260, Serial#: 260
    public void fbne(final FCCOperand n, final int label) {
        int instruction = 0x03480000;
        checkConstraint((-1048576 <= label && label <= 1048572) && ((label % 4) == 0), "(-1048576 <= label && label <= 1048572) && ((label % 4) == 0)");
        instruction |= ((n.value() & 0x3) << 20);
        instruction |= ((label >> 2) & 0x7ffff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fbe  }<i>n</i>, <i>label</i>
     * Example disassembly syntax: {@code fbe           %fcc0, L1: -1048576}
     * <p>
     * Constraint: {@code (-1048576 <= label && label <= 1048572) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.5"
     */
    // Template#: 261, Serial#: 261
    public void fbe(final FCCOperand n, final int label) {
        int instruction = 0x13480000;
        checkConstraint((-1048576 <= label && label <= 1048572) && ((label % 4) == 0), "(-1048576 <= label && label <= 1048572) && ((label % 4) == 0)");
        instruction |= ((n.value() & 0x3) << 20);
        instruction |= ((label >> 2) & 0x7ffff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fbue  }<i>n</i>, <i>label</i>
     * Example disassembly syntax: {@code fbue          %fcc0, L1: -1048576}
     * <p>
     * Constraint: {@code (-1048576 <= label && label <= 1048572) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.5"
     */
    // Template#: 262, Serial#: 262
    public void fbue(final FCCOperand n, final int label) {
        int instruction = 0x15480000;
        checkConstraint((-1048576 <= label && label <= 1048572) && ((label % 4) == 0), "(-1048576 <= label && label <= 1048572) && ((label % 4) == 0)");
        instruction |= ((n.value() & 0x3) << 20);
        instruction |= ((label >> 2) & 0x7ffff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fbge  }<i>n</i>, <i>label</i>
     * Example disassembly syntax: {@code fbge          %fcc0, L1: -1048576}
     * <p>
     * Constraint: {@code (-1048576 <= label && label <= 1048572) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.5"
     */
    // Template#: 263, Serial#: 263
    public void fbge(final FCCOperand n, final int label) {
        int instruction = 0x17480000;
        checkConstraint((-1048576 <= label && label <= 1048572) && ((label % 4) == 0), "(-1048576 <= label && label <= 1048572) && ((label % 4) == 0)");
        instruction |= ((n.value() & 0x3) << 20);
        instruction |= ((label >> 2) & 0x7ffff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fbuge  }<i>n</i>, <i>label</i>
     * Example disassembly syntax: {@code fbuge         %fcc0, L1: -1048576}
     * <p>
     * Constraint: {@code (-1048576 <= label && label <= 1048572) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.5"
     */
    // Template#: 264, Serial#: 264
    public void fbuge(final FCCOperand n, final int label) {
        int instruction = 0x19480000;
        checkConstraint((-1048576 <= label && label <= 1048572) && ((label % 4) == 0), "(-1048576 <= label && label <= 1048572) && ((label % 4) == 0)");
        instruction |= ((n.value() & 0x3) << 20);
        instruction |= ((label >> 2) & 0x7ffff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fble  }<i>n</i>, <i>label</i>
     * Example disassembly syntax: {@code fble          %fcc0, L1: -1048576}
     * <p>
     * Constraint: {@code (-1048576 <= label && label <= 1048572) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.5"
     */
    // Template#: 265, Serial#: 265
    public void fble(final FCCOperand n, final int label) {
        int instruction = 0x1B480000;
        checkConstraint((-1048576 <= label && label <= 1048572) && ((label % 4) == 0), "(-1048576 <= label && label <= 1048572) && ((label % 4) == 0)");
        instruction |= ((n.value() & 0x3) << 20);
        instruction |= ((label >> 2) & 0x7ffff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fbule  }<i>n</i>, <i>label</i>
     * Example disassembly syntax: {@code fbule         %fcc0, L1: -1048576}
     * <p>
     * Constraint: {@code (-1048576 <= label && label <= 1048572) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.5"
     */
    // Template#: 266, Serial#: 266
    public void fbule(final FCCOperand n, final int label) {
        int instruction = 0x1D480000;
        checkConstraint((-1048576 <= label && label <= 1048572) && ((label % 4) == 0), "(-1048576 <= label && label <= 1048572) && ((label % 4) == 0)");
        instruction |= ((n.value() & 0x3) << 20);
        instruction |= ((label >> 2) & 0x7ffff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fbo  }<i>n</i>, <i>label</i>
     * Example disassembly syntax: {@code fbo           %fcc0, L1: -1048576}
     * <p>
     * Constraint: {@code (-1048576 <= label && label <= 1048572) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.5"
     */
    // Template#: 267, Serial#: 267
    public void fbo(final FCCOperand n, final int label) {
        int instruction = 0x1F480000;
        checkConstraint((-1048576 <= label && label <= 1048572) && ((label % 4) == 0), "(-1048576 <= label && label <= 1048572) && ((label % 4) == 0)");
        instruction |= ((n.value() & 0x3) << 20);
        instruction |= ((label >> 2) & 0x7ffff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fb[a|n|u|g|ug|l|ul|lg|ne|e|ue|ge|uge|le|ule|o]{,a}{,pn|,pt}  }<i>n</i>, <i>label</i>
     * Example disassembly syntax: {@code fba,pn        %fcc0, L1: -1048576}
     * <p>
     * Constraint: {@code (-1048576 <= label && label <= 1048572) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.5"
     */
    // Template#: 268, Serial#: 268
    public void fb(final FBfcc cond, final AnnulBit a, final BranchPredictionBit p, final FCCOperand n, final int label) {
        int instruction = 0x01400000;
        checkConstraint((-1048576 <= label && label <= 1048572) && ((label % 4) == 0), "(-1048576 <= label && label <= 1048572) && ((label % 4) == 0)");
        instruction |= ((cond.value() & 0xf) << 25);
        instruction |= ((a.value() & 0x1) << 29);
        instruction |= ((p.value() & 0x1) << 19);
        instruction |= ((n.value() & 0x3) << 20);
        instruction |= ((label >> 2) & 0x7ffff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code ba{,a}  }<i>label</i>
     * Example disassembly syntax: {@code ba            L1: -8388608}
     * <p>
     * Constraint: {@code (-8388608 <= label && label <= 8388604) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.6"
     */
    // Template#: 269, Serial#: 269
    public void ba(final AnnulBit a, final int label) {
        int instruction = 0x10800000;
        checkConstraint((-8388608 <= label && label <= 8388604) && ((label % 4) == 0), "(-8388608 <= label && label <= 8388604) && ((label % 4) == 0)");
        instruction |= ((a.value() & 0x1) << 29);
        instruction |= ((label >> 2) & 0x3fffff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code bn{,a}  }<i>label</i>
     * Example disassembly syntax: {@code bn            L1: -8388608}
     * <p>
     * Constraint: {@code (-8388608 <= label && label <= 8388604) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.6"
     */
    // Template#: 270, Serial#: 270
    public void bn(final AnnulBit a, final int label) {
        int instruction = 0x00800000;
        checkConstraint((-8388608 <= label && label <= 8388604) && ((label % 4) == 0), "(-8388608 <= label && label <= 8388604) && ((label % 4) == 0)");
        instruction |= ((a.value() & 0x1) << 29);
        instruction |= ((label >> 2) & 0x3fffff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code bne{,a}  }<i>label</i>
     * Example disassembly syntax: {@code bne           L1: -8388608}
     * <p>
     * Constraint: {@code (-8388608 <= label && label <= 8388604) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.6"
     */
    // Template#: 271, Serial#: 271
    public void bne(final AnnulBit a, final int label) {
        int instruction = 0x12800000;
        checkConstraint((-8388608 <= label && label <= 8388604) && ((label % 4) == 0), "(-8388608 <= label && label <= 8388604) && ((label % 4) == 0)");
        instruction |= ((a.value() & 0x1) << 29);
        instruction |= ((label >> 2) & 0x3fffff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code be{,a}  }<i>label</i>
     * Example disassembly syntax: {@code be            L1: -8388608}
     * <p>
     * Constraint: {@code (-8388608 <= label && label <= 8388604) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.6"
     */
    // Template#: 272, Serial#: 272
    public void be(final AnnulBit a, final int label) {
        int instruction = 0x02800000;
        checkConstraint((-8388608 <= label && label <= 8388604) && ((label % 4) == 0), "(-8388608 <= label && label <= 8388604) && ((label % 4) == 0)");
        instruction |= ((a.value() & 0x1) << 29);
        instruction |= ((label >> 2) & 0x3fffff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code bg{,a}  }<i>label</i>
     * Example disassembly syntax: {@code bg            L1: -8388608}
     * <p>
     * Constraint: {@code (-8388608 <= label && label <= 8388604) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.6"
     */
    // Template#: 273, Serial#: 273
    public void bg(final AnnulBit a, final int label) {
        int instruction = 0x14800000;
        checkConstraint((-8388608 <= label && label <= 8388604) && ((label % 4) == 0), "(-8388608 <= label && label <= 8388604) && ((label % 4) == 0)");
        instruction |= ((a.value() & 0x1) << 29);
        instruction |= ((label >> 2) & 0x3fffff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code ble{,a}  }<i>label</i>
     * Example disassembly syntax: {@code ble           L1: -8388608}
     * <p>
     * Constraint: {@code (-8388608 <= label && label <= 8388604) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.6"
     */
    // Template#: 274, Serial#: 274
    public void ble(final AnnulBit a, final int label) {
        int instruction = 0x04800000;
        checkConstraint((-8388608 <= label && label <= 8388604) && ((label % 4) == 0), "(-8388608 <= label && label <= 8388604) && ((label % 4) == 0)");
        instruction |= ((a.value() & 0x1) << 29);
        instruction |= ((label >> 2) & 0x3fffff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code bge{,a}  }<i>label</i>
     * Example disassembly syntax: {@code bge           L1: -8388608}
     * <p>
     * Constraint: {@code (-8388608 <= label && label <= 8388604) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.6"
     */
    // Template#: 275, Serial#: 275
    public void bge(final AnnulBit a, final int label) {
        int instruction = 0x16800000;
        checkConstraint((-8388608 <= label && label <= 8388604) && ((label % 4) == 0), "(-8388608 <= label && label <= 8388604) && ((label % 4) == 0)");
        instruction |= ((a.value() & 0x1) << 29);
        instruction |= ((label >> 2) & 0x3fffff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code bl{,a}  }<i>label</i>
     * Example disassembly syntax: {@code bl            L1: -8388608}
     * <p>
     * Constraint: {@code (-8388608 <= label && label <= 8388604) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.6"
     */
    // Template#: 276, Serial#: 276
    public void bl(final AnnulBit a, final int label) {
        int instruction = 0x06800000;
        checkConstraint((-8388608 <= label && label <= 8388604) && ((label % 4) == 0), "(-8388608 <= label && label <= 8388604) && ((label % 4) == 0)");
        instruction |= ((a.value() & 0x1) << 29);
        instruction |= ((label >> 2) & 0x3fffff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code bgu{,a}  }<i>label</i>
     * Example disassembly syntax: {@code bgu           L1: -8388608}
     * <p>
     * Constraint: {@code (-8388608 <= label && label <= 8388604) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.6"
     */
    // Template#: 277, Serial#: 277
    public void bgu(final AnnulBit a, final int label) {
        int instruction = 0x18800000;
        checkConstraint((-8388608 <= label && label <= 8388604) && ((label % 4) == 0), "(-8388608 <= label && label <= 8388604) && ((label % 4) == 0)");
        instruction |= ((a.value() & 0x1) << 29);
        instruction |= ((label >> 2) & 0x3fffff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code bleu{,a}  }<i>label</i>
     * Example disassembly syntax: {@code bleu          L1: -8388608}
     * <p>
     * Constraint: {@code (-8388608 <= label && label <= 8388604) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.6"
     */
    // Template#: 278, Serial#: 278
    public void bleu(final AnnulBit a, final int label) {
        int instruction = 0x08800000;
        checkConstraint((-8388608 <= label && label <= 8388604) && ((label % 4) == 0), "(-8388608 <= label && label <= 8388604) && ((label % 4) == 0)");
        instruction |= ((a.value() & 0x1) << 29);
        instruction |= ((label >> 2) & 0x3fffff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code bcc{,a}  }<i>label</i>
     * Example disassembly syntax: {@code bcc           L1: -8388608}
     * <p>
     * Constraint: {@code (-8388608 <= label && label <= 8388604) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.6"
     */
    // Template#: 279, Serial#: 279
    public void bcc(final AnnulBit a, final int label) {
        int instruction = 0x1A800000;
        checkConstraint((-8388608 <= label && label <= 8388604) && ((label % 4) == 0), "(-8388608 <= label && label <= 8388604) && ((label % 4) == 0)");
        instruction |= ((a.value() & 0x1) << 29);
        instruction |= ((label >> 2) & 0x3fffff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code bcs{,a}  }<i>label</i>
     * Example disassembly syntax: {@code bcs           L1: -8388608}
     * <p>
     * Constraint: {@code (-8388608 <= label && label <= 8388604) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.6"
     */
    // Template#: 280, Serial#: 280
    public void bcs(final AnnulBit a, final int label) {
        int instruction = 0x0A800000;
        checkConstraint((-8388608 <= label && label <= 8388604) && ((label % 4) == 0), "(-8388608 <= label && label <= 8388604) && ((label % 4) == 0)");
        instruction |= ((a.value() & 0x1) << 29);
        instruction |= ((label >> 2) & 0x3fffff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code bpos{,a}  }<i>label</i>
     * Example disassembly syntax: {@code bpos          L1: -8388608}
     * <p>
     * Constraint: {@code (-8388608 <= label && label <= 8388604) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.6"
     */
    // Template#: 281, Serial#: 281
    public void bpos(final AnnulBit a, final int label) {
        int instruction = 0x1C800000;
        checkConstraint((-8388608 <= label && label <= 8388604) && ((label % 4) == 0), "(-8388608 <= label && label <= 8388604) && ((label % 4) == 0)");
        instruction |= ((a.value() & 0x1) << 29);
        instruction |= ((label >> 2) & 0x3fffff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code bneg{,a}  }<i>label</i>
     * Example disassembly syntax: {@code bneg          L1: -8388608}
     * <p>
     * Constraint: {@code (-8388608 <= label && label <= 8388604) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.6"
     */
    // Template#: 282, Serial#: 282
    public void bneg(final AnnulBit a, final int label) {
        int instruction = 0x0C800000;
        checkConstraint((-8388608 <= label && label <= 8388604) && ((label % 4) == 0), "(-8388608 <= label && label <= 8388604) && ((label % 4) == 0)");
        instruction |= ((a.value() & 0x1) << 29);
        instruction |= ((label >> 2) & 0x3fffff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code bvc{,a}  }<i>label</i>
     * Example disassembly syntax: {@code bvc           L1: -8388608}
     * <p>
     * Constraint: {@code (-8388608 <= label && label <= 8388604) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.6"
     */
    // Template#: 283, Serial#: 283
    public void bvc(final AnnulBit a, final int label) {
        int instruction = 0x1E800000;
        checkConstraint((-8388608 <= label && label <= 8388604) && ((label % 4) == 0), "(-8388608 <= label && label <= 8388604) && ((label % 4) == 0)");
        instruction |= ((a.value() & 0x1) << 29);
        instruction |= ((label >> 2) & 0x3fffff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code bvs{,a}  }<i>label</i>
     * Example disassembly syntax: {@code bvs           L1: -8388608}
     * <p>
     * Constraint: {@code (-8388608 <= label && label <= 8388604) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.6"
     */
    // Template#: 284, Serial#: 284
    public void bvs(final AnnulBit a, final int label) {
        int instruction = 0x0E800000;
        checkConstraint((-8388608 <= label && label <= 8388604) && ((label % 4) == 0), "(-8388608 <= label && label <= 8388604) && ((label % 4) == 0)");
        instruction |= ((a.value() & 0x1) << 29);
        instruction |= ((label >> 2) & 0x3fffff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code ba  }<i>label</i>
     * Example disassembly syntax: {@code ba            L1: -8388608}
     * <p>
     * Constraint: {@code (-8388608 <= label && label <= 8388604) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.6"
     */
    // Template#: 285, Serial#: 285
    public void ba(final int label) {
        int instruction = 0x10800000;
        checkConstraint((-8388608 <= label && label <= 8388604) && ((label % 4) == 0), "(-8388608 <= label && label <= 8388604) && ((label % 4) == 0)");
        instruction |= ((label >> 2) & 0x3fffff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code bn  }<i>label</i>
     * Example disassembly syntax: {@code bn            L1: -8388608}
     * <p>
     * Constraint: {@code (-8388608 <= label && label <= 8388604) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.6"
     */
    // Template#: 286, Serial#: 286
    public void bn(final int label) {
        int instruction = 0x00800000;
        checkConstraint((-8388608 <= label && label <= 8388604) && ((label % 4) == 0), "(-8388608 <= label && label <= 8388604) && ((label % 4) == 0)");
        instruction |= ((label >> 2) & 0x3fffff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code bne  }<i>label</i>
     * Example disassembly syntax: {@code bne           L1: -8388608}
     * <p>
     * Constraint: {@code (-8388608 <= label && label <= 8388604) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.6"
     */
    // Template#: 287, Serial#: 287
    public void bne(final int label) {
        int instruction = 0x12800000;
        checkConstraint((-8388608 <= label && label <= 8388604) && ((label % 4) == 0), "(-8388608 <= label && label <= 8388604) && ((label % 4) == 0)");
        instruction |= ((label >> 2) & 0x3fffff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code be  }<i>label</i>
     * Example disassembly syntax: {@code be            L1: -8388608}
     * <p>
     * Constraint: {@code (-8388608 <= label && label <= 8388604) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.6"
     */
    // Template#: 288, Serial#: 288
    public void be(final int label) {
        int instruction = 0x02800000;
        checkConstraint((-8388608 <= label && label <= 8388604) && ((label % 4) == 0), "(-8388608 <= label && label <= 8388604) && ((label % 4) == 0)");
        instruction |= ((label >> 2) & 0x3fffff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code bg  }<i>label</i>
     * Example disassembly syntax: {@code bg            L1: -8388608}
     * <p>
     * Constraint: {@code (-8388608 <= label && label <= 8388604) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.6"
     */
    // Template#: 289, Serial#: 289
    public void bg(final int label) {
        int instruction = 0x14800000;
        checkConstraint((-8388608 <= label && label <= 8388604) && ((label % 4) == 0), "(-8388608 <= label && label <= 8388604) && ((label % 4) == 0)");
        instruction |= ((label >> 2) & 0x3fffff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code ble  }<i>label</i>
     * Example disassembly syntax: {@code ble           L1: -8388608}
     * <p>
     * Constraint: {@code (-8388608 <= label && label <= 8388604) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.6"
     */
    // Template#: 290, Serial#: 290
    public void ble(final int label) {
        int instruction = 0x04800000;
        checkConstraint((-8388608 <= label && label <= 8388604) && ((label % 4) == 0), "(-8388608 <= label && label <= 8388604) && ((label % 4) == 0)");
        instruction |= ((label >> 2) & 0x3fffff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code bge  }<i>label</i>
     * Example disassembly syntax: {@code bge           L1: -8388608}
     * <p>
     * Constraint: {@code (-8388608 <= label && label <= 8388604) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.6"
     */
    // Template#: 291, Serial#: 291
    public void bge(final int label) {
        int instruction = 0x16800000;
        checkConstraint((-8388608 <= label && label <= 8388604) && ((label % 4) == 0), "(-8388608 <= label && label <= 8388604) && ((label % 4) == 0)");
        instruction |= ((label >> 2) & 0x3fffff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code bl  }<i>label</i>
     * Example disassembly syntax: {@code bl            L1: -8388608}
     * <p>
     * Constraint: {@code (-8388608 <= label && label <= 8388604) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.6"
     */
    // Template#: 292, Serial#: 292
    public void bl(final int label) {
        int instruction = 0x06800000;
        checkConstraint((-8388608 <= label && label <= 8388604) && ((label % 4) == 0), "(-8388608 <= label && label <= 8388604) && ((label % 4) == 0)");
        instruction |= ((label >> 2) & 0x3fffff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code bgu  }<i>label</i>
     * Example disassembly syntax: {@code bgu           L1: -8388608}
     * <p>
     * Constraint: {@code (-8388608 <= label && label <= 8388604) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.6"
     */
    // Template#: 293, Serial#: 293
    public void bgu(final int label) {
        int instruction = 0x18800000;
        checkConstraint((-8388608 <= label && label <= 8388604) && ((label % 4) == 0), "(-8388608 <= label && label <= 8388604) && ((label % 4) == 0)");
        instruction |= ((label >> 2) & 0x3fffff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code bleu  }<i>label</i>
     * Example disassembly syntax: {@code bleu          L1: -8388608}
     * <p>
     * Constraint: {@code (-8388608 <= label && label <= 8388604) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.6"
     */
    // Template#: 294, Serial#: 294
    public void bleu(final int label) {
        int instruction = 0x08800000;
        checkConstraint((-8388608 <= label && label <= 8388604) && ((label % 4) == 0), "(-8388608 <= label && label <= 8388604) && ((label % 4) == 0)");
        instruction |= ((label >> 2) & 0x3fffff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code bcc  }<i>label</i>
     * Example disassembly syntax: {@code bcc           L1: -8388608}
     * <p>
     * Constraint: {@code (-8388608 <= label && label <= 8388604) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.6"
     */
    // Template#: 295, Serial#: 295
    public void bcc(final int label) {
        int instruction = 0x1A800000;
        checkConstraint((-8388608 <= label && label <= 8388604) && ((label % 4) == 0), "(-8388608 <= label && label <= 8388604) && ((label % 4) == 0)");
        instruction |= ((label >> 2) & 0x3fffff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code bcs  }<i>label</i>
     * Example disassembly syntax: {@code bcs           L1: -8388608}
     * <p>
     * Constraint: {@code (-8388608 <= label && label <= 8388604) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.6"
     */
    // Template#: 296, Serial#: 296
    public void bcs(final int label) {
        int instruction = 0x0A800000;
        checkConstraint((-8388608 <= label && label <= 8388604) && ((label % 4) == 0), "(-8388608 <= label && label <= 8388604) && ((label % 4) == 0)");
        instruction |= ((label >> 2) & 0x3fffff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code bpos  }<i>label</i>
     * Example disassembly syntax: {@code bpos          L1: -8388608}
     * <p>
     * Constraint: {@code (-8388608 <= label && label <= 8388604) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.6"
     */
    // Template#: 297, Serial#: 297
    public void bpos(final int label) {
        int instruction = 0x1C800000;
        checkConstraint((-8388608 <= label && label <= 8388604) && ((label % 4) == 0), "(-8388608 <= label && label <= 8388604) && ((label % 4) == 0)");
        instruction |= ((label >> 2) & 0x3fffff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code bneg  }<i>label</i>
     * Example disassembly syntax: {@code bneg          L1: -8388608}
     * <p>
     * Constraint: {@code (-8388608 <= label && label <= 8388604) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.6"
     */
    // Template#: 298, Serial#: 298
    public void bneg(final int label) {
        int instruction = 0x0C800000;
        checkConstraint((-8388608 <= label && label <= 8388604) && ((label % 4) == 0), "(-8388608 <= label && label <= 8388604) && ((label % 4) == 0)");
        instruction |= ((label >> 2) & 0x3fffff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code bvc  }<i>label</i>
     * Example disassembly syntax: {@code bvc           L1: -8388608}
     * <p>
     * Constraint: {@code (-8388608 <= label && label <= 8388604) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.6"
     */
    // Template#: 299, Serial#: 299
    public void bvc(final int label) {
        int instruction = 0x1E800000;
        checkConstraint((-8388608 <= label && label <= 8388604) && ((label % 4) == 0), "(-8388608 <= label && label <= 8388604) && ((label % 4) == 0)");
        instruction |= ((label >> 2) & 0x3fffff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code bvs  }<i>label</i>
     * Example disassembly syntax: {@code bvs           L1: -8388608}
     * <p>
     * Constraint: {@code (-8388608 <= label && label <= 8388604) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.6"
     */
    // Template#: 300, Serial#: 300
    public void bvs(final int label) {
        int instruction = 0x0E800000;
        checkConstraint((-8388608 <= label && label <= 8388604) && ((label % 4) == 0), "(-8388608 <= label && label <= 8388604) && ((label % 4) == 0)");
        instruction |= ((label >> 2) & 0x3fffff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code b[a|n|ne|e|g|le|ge|l|gu|leu|cc|cs|pos|neg|vc|vs]{,a}  }<i>label</i>
     * Example disassembly syntax: {@code ba            L1: -8388608}
     * <p>
     * Constraint: {@code (-8388608 <= label && label <= 8388604) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.6"
     */
    // Template#: 301, Serial#: 301
    public void b(final Bicc cond, final AnnulBit a, final int label) {
        int instruction = 0x00800000;
        checkConstraint((-8388608 <= label && label <= 8388604) && ((label % 4) == 0), "(-8388608 <= label && label <= 8388604) && ((label % 4) == 0)");
        instruction |= ((cond.value() & 0xf) << 25);
        instruction |= ((a.value() & 0x1) << 29);
        instruction |= ((label >> 2) & 0x3fffff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code ba{,a}{,pn|,pt}  }<i>i_or_x_cc</i>, <i>label</i>
     * Example disassembly syntax: {@code ba,pn         %icc, L1: -1048576}
     * <p>
     * Constraint: {@code (-1048576 <= label && label <= 1048572) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.7"
     */
    // Template#: 302, Serial#: 302
    public void ba(final AnnulBit a, final BranchPredictionBit p, final ICCOperand i_or_x_cc, final int label) {
        int instruction = 0x10400000;
        checkConstraint((-1048576 <= label && label <= 1048572) && ((label % 4) == 0), "(-1048576 <= label && label <= 1048572) && ((label % 4) == 0)");
        instruction |= ((a.value() & 0x1) << 29);
        instruction |= ((p.value() & 0x1) << 19);
        instruction |= ((i_or_x_cc.value() & 0x3) << 20);
        instruction |= ((label >> 2) & 0x7ffff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code bn{,a}{,pn|,pt}  }<i>i_or_x_cc</i>, <i>label</i>
     * Example disassembly syntax: {@code bn,pn         %icc, L1: -1048576}
     * <p>
     * Constraint: {@code (-1048576 <= label && label <= 1048572) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.7"
     */
    // Template#: 303, Serial#: 303
    public void bn(final AnnulBit a, final BranchPredictionBit p, final ICCOperand i_or_x_cc, final int label) {
        int instruction = 0x00400000;
        checkConstraint((-1048576 <= label && label <= 1048572) && ((label % 4) == 0), "(-1048576 <= label && label <= 1048572) && ((label % 4) == 0)");
        instruction |= ((a.value() & 0x1) << 29);
        instruction |= ((p.value() & 0x1) << 19);
        instruction |= ((i_or_x_cc.value() & 0x3) << 20);
        instruction |= ((label >> 2) & 0x7ffff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code bne{,a}{,pn|,pt}  }<i>i_or_x_cc</i>, <i>label</i>
     * Example disassembly syntax: {@code bne,pn        %icc, L1: -1048576}
     * <p>
     * Constraint: {@code (-1048576 <= label && label <= 1048572) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.7"
     */
    // Template#: 304, Serial#: 304
    public void bne(final AnnulBit a, final BranchPredictionBit p, final ICCOperand i_or_x_cc, final int label) {
        int instruction = 0x12400000;
        checkConstraint((-1048576 <= label && label <= 1048572) && ((label % 4) == 0), "(-1048576 <= label && label <= 1048572) && ((label % 4) == 0)");
        instruction |= ((a.value() & 0x1) << 29);
        instruction |= ((p.value() & 0x1) << 19);
        instruction |= ((i_or_x_cc.value() & 0x3) << 20);
        instruction |= ((label >> 2) & 0x7ffff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code be{,a}{,pn|,pt}  }<i>i_or_x_cc</i>, <i>label</i>
     * Example disassembly syntax: {@code be,pn         %icc, L1: -1048576}
     * <p>
     * Constraint: {@code (-1048576 <= label && label <= 1048572) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.7"
     */
    // Template#: 305, Serial#: 305
    public void be(final AnnulBit a, final BranchPredictionBit p, final ICCOperand i_or_x_cc, final int label) {
        int instruction = 0x02400000;
        checkConstraint((-1048576 <= label && label <= 1048572) && ((label % 4) == 0), "(-1048576 <= label && label <= 1048572) && ((label % 4) == 0)");
        instruction |= ((a.value() & 0x1) << 29);
        instruction |= ((p.value() & 0x1) << 19);
        instruction |= ((i_or_x_cc.value() & 0x3) << 20);
        instruction |= ((label >> 2) & 0x7ffff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code bg{,a}{,pn|,pt}  }<i>i_or_x_cc</i>, <i>label</i>
     * Example disassembly syntax: {@code bg,pn         %icc, L1: -1048576}
     * <p>
     * Constraint: {@code (-1048576 <= label && label <= 1048572) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.7"
     */
    // Template#: 306, Serial#: 306
    public void bg(final AnnulBit a, final BranchPredictionBit p, final ICCOperand i_or_x_cc, final int label) {
        int instruction = 0x14400000;
        checkConstraint((-1048576 <= label && label <= 1048572) && ((label % 4) == 0), "(-1048576 <= label && label <= 1048572) && ((label % 4) == 0)");
        instruction |= ((a.value() & 0x1) << 29);
        instruction |= ((p.value() & 0x1) << 19);
        instruction |= ((i_or_x_cc.value() & 0x3) << 20);
        instruction |= ((label >> 2) & 0x7ffff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code ble{,a}{,pn|,pt}  }<i>i_or_x_cc</i>, <i>label</i>
     * Example disassembly syntax: {@code ble,pn        %icc, L1: -1048576}
     * <p>
     * Constraint: {@code (-1048576 <= label && label <= 1048572) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.7"
     */
    // Template#: 307, Serial#: 307
    public void ble(final AnnulBit a, final BranchPredictionBit p, final ICCOperand i_or_x_cc, final int label) {
        int instruction = 0x04400000;
        checkConstraint((-1048576 <= label && label <= 1048572) && ((label % 4) == 0), "(-1048576 <= label && label <= 1048572) && ((label % 4) == 0)");
        instruction |= ((a.value() & 0x1) << 29);
        instruction |= ((p.value() & 0x1) << 19);
        instruction |= ((i_or_x_cc.value() & 0x3) << 20);
        instruction |= ((label >> 2) & 0x7ffff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code bge{,a}{,pn|,pt}  }<i>i_or_x_cc</i>, <i>label</i>
     * Example disassembly syntax: {@code bge,pn        %icc, L1: -1048576}
     * <p>
     * Constraint: {@code (-1048576 <= label && label <= 1048572) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.7"
     */
    // Template#: 308, Serial#: 308
    public void bge(final AnnulBit a, final BranchPredictionBit p, final ICCOperand i_or_x_cc, final int label) {
        int instruction = 0x16400000;
        checkConstraint((-1048576 <= label && label <= 1048572) && ((label % 4) == 0), "(-1048576 <= label && label <= 1048572) && ((label % 4) == 0)");
        instruction |= ((a.value() & 0x1) << 29);
        instruction |= ((p.value() & 0x1) << 19);
        instruction |= ((i_or_x_cc.value() & 0x3) << 20);
        instruction |= ((label >> 2) & 0x7ffff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code bl{,a}{,pn|,pt}  }<i>i_or_x_cc</i>, <i>label</i>
     * Example disassembly syntax: {@code bl,pn         %icc, L1: -1048576}
     * <p>
     * Constraint: {@code (-1048576 <= label && label <= 1048572) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.7"
     */
    // Template#: 309, Serial#: 309
    public void bl(final AnnulBit a, final BranchPredictionBit p, final ICCOperand i_or_x_cc, final int label) {
        int instruction = 0x06400000;
        checkConstraint((-1048576 <= label && label <= 1048572) && ((label % 4) == 0), "(-1048576 <= label && label <= 1048572) && ((label % 4) == 0)");
        instruction |= ((a.value() & 0x1) << 29);
        instruction |= ((p.value() & 0x1) << 19);
        instruction |= ((i_or_x_cc.value() & 0x3) << 20);
        instruction |= ((label >> 2) & 0x7ffff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code bgu{,a}{,pn|,pt}  }<i>i_or_x_cc</i>, <i>label</i>
     * Example disassembly syntax: {@code bgu,pn        %icc, L1: -1048576}
     * <p>
     * Constraint: {@code (-1048576 <= label && label <= 1048572) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.7"
     */
    // Template#: 310, Serial#: 310
    public void bgu(final AnnulBit a, final BranchPredictionBit p, final ICCOperand i_or_x_cc, final int label) {
        int instruction = 0x18400000;
        checkConstraint((-1048576 <= label && label <= 1048572) && ((label % 4) == 0), "(-1048576 <= label && label <= 1048572) && ((label % 4) == 0)");
        instruction |= ((a.value() & 0x1) << 29);
        instruction |= ((p.value() & 0x1) << 19);
        instruction |= ((i_or_x_cc.value() & 0x3) << 20);
        instruction |= ((label >> 2) & 0x7ffff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code bleu{,a}{,pn|,pt}  }<i>i_or_x_cc</i>, <i>label</i>
     * Example disassembly syntax: {@code bleu,pn       %icc, L1: -1048576}
     * <p>
     * Constraint: {@code (-1048576 <= label && label <= 1048572) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.7"
     */
    // Template#: 311, Serial#: 311
    public void bleu(final AnnulBit a, final BranchPredictionBit p, final ICCOperand i_or_x_cc, final int label) {
        int instruction = 0x08400000;
        checkConstraint((-1048576 <= label && label <= 1048572) && ((label % 4) == 0), "(-1048576 <= label && label <= 1048572) && ((label % 4) == 0)");
        instruction |= ((a.value() & 0x1) << 29);
        instruction |= ((p.value() & 0x1) << 19);
        instruction |= ((i_or_x_cc.value() & 0x3) << 20);
        instruction |= ((label >> 2) & 0x7ffff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code bcc{,a}{,pn|,pt}  }<i>i_or_x_cc</i>, <i>label</i>
     * Example disassembly syntax: {@code bcc,pn        %icc, L1: -1048576}
     * <p>
     * Constraint: {@code (-1048576 <= label && label <= 1048572) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.7"
     */
    // Template#: 312, Serial#: 312
    public void bcc(final AnnulBit a, final BranchPredictionBit p, final ICCOperand i_or_x_cc, final int label) {
        int instruction = 0x1A400000;
        checkConstraint((-1048576 <= label && label <= 1048572) && ((label % 4) == 0), "(-1048576 <= label && label <= 1048572) && ((label % 4) == 0)");
        instruction |= ((a.value() & 0x1) << 29);
        instruction |= ((p.value() & 0x1) << 19);
        instruction |= ((i_or_x_cc.value() & 0x3) << 20);
        instruction |= ((label >> 2) & 0x7ffff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code bcs{,a}{,pn|,pt}  }<i>i_or_x_cc</i>, <i>label</i>
     * Example disassembly syntax: {@code bcs,pn        %icc, L1: -1048576}
     * <p>
     * Constraint: {@code (-1048576 <= label && label <= 1048572) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.7"
     */
    // Template#: 313, Serial#: 313
    public void bcs(final AnnulBit a, final BranchPredictionBit p, final ICCOperand i_or_x_cc, final int label) {
        int instruction = 0x0A400000;
        checkConstraint((-1048576 <= label && label <= 1048572) && ((label % 4) == 0), "(-1048576 <= label && label <= 1048572) && ((label % 4) == 0)");
        instruction |= ((a.value() & 0x1) << 29);
        instruction |= ((p.value() & 0x1) << 19);
        instruction |= ((i_or_x_cc.value() & 0x3) << 20);
        instruction |= ((label >> 2) & 0x7ffff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code bpos{,a}{,pn|,pt}  }<i>i_or_x_cc</i>, <i>label</i>
     * Example disassembly syntax: {@code bpos,pn       %icc, L1: -1048576}
     * <p>
     * Constraint: {@code (-1048576 <= label && label <= 1048572) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.7"
     */
    // Template#: 314, Serial#: 314
    public void bpos(final AnnulBit a, final BranchPredictionBit p, final ICCOperand i_or_x_cc, final int label) {
        int instruction = 0x1C400000;
        checkConstraint((-1048576 <= label && label <= 1048572) && ((label % 4) == 0), "(-1048576 <= label && label <= 1048572) && ((label % 4) == 0)");
        instruction |= ((a.value() & 0x1) << 29);
        instruction |= ((p.value() & 0x1) << 19);
        instruction |= ((i_or_x_cc.value() & 0x3) << 20);
        instruction |= ((label >> 2) & 0x7ffff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code bneg{,a}{,pn|,pt}  }<i>i_or_x_cc</i>, <i>label</i>
     * Example disassembly syntax: {@code bneg,pn       %icc, L1: -1048576}
     * <p>
     * Constraint: {@code (-1048576 <= label && label <= 1048572) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.7"
     */
    // Template#: 315, Serial#: 315
    public void bneg(final AnnulBit a, final BranchPredictionBit p, final ICCOperand i_or_x_cc, final int label) {
        int instruction = 0x0C400000;
        checkConstraint((-1048576 <= label && label <= 1048572) && ((label % 4) == 0), "(-1048576 <= label && label <= 1048572) && ((label % 4) == 0)");
        instruction |= ((a.value() & 0x1) << 29);
        instruction |= ((p.value() & 0x1) << 19);
        instruction |= ((i_or_x_cc.value() & 0x3) << 20);
        instruction |= ((label >> 2) & 0x7ffff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code bvc{,a}{,pn|,pt}  }<i>i_or_x_cc</i>, <i>label</i>
     * Example disassembly syntax: {@code bvc,pn        %icc, L1: -1048576}
     * <p>
     * Constraint: {@code (-1048576 <= label && label <= 1048572) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.7"
     */
    // Template#: 316, Serial#: 316
    public void bvc(final AnnulBit a, final BranchPredictionBit p, final ICCOperand i_or_x_cc, final int label) {
        int instruction = 0x1E400000;
        checkConstraint((-1048576 <= label && label <= 1048572) && ((label % 4) == 0), "(-1048576 <= label && label <= 1048572) && ((label % 4) == 0)");
        instruction |= ((a.value() & 0x1) << 29);
        instruction |= ((p.value() & 0x1) << 19);
        instruction |= ((i_or_x_cc.value() & 0x3) << 20);
        instruction |= ((label >> 2) & 0x7ffff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code bvs{,a}{,pn|,pt}  }<i>i_or_x_cc</i>, <i>label</i>
     * Example disassembly syntax: {@code bvs,pn        %icc, L1: -1048576}
     * <p>
     * Constraint: {@code (-1048576 <= label && label <= 1048572) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.7"
     */
    // Template#: 317, Serial#: 317
    public void bvs(final AnnulBit a, final BranchPredictionBit p, final ICCOperand i_or_x_cc, final int label) {
        int instruction = 0x0E400000;
        checkConstraint((-1048576 <= label && label <= 1048572) && ((label % 4) == 0), "(-1048576 <= label && label <= 1048572) && ((label % 4) == 0)");
        instruction |= ((a.value() & 0x1) << 29);
        instruction |= ((p.value() & 0x1) << 19);
        instruction |= ((i_or_x_cc.value() & 0x3) << 20);
        instruction |= ((label >> 2) & 0x7ffff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code ba  }<i>i_or_x_cc</i>, <i>label</i>
     * Example disassembly syntax: {@code ba            %icc, L1: -1048576}
     * <p>
     * Constraint: {@code (-1048576 <= label && label <= 1048572) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.7"
     */
    // Template#: 318, Serial#: 318
    public void ba(final ICCOperand i_or_x_cc, final int label) {
        int instruction = 0x10480000;
        checkConstraint((-1048576 <= label && label <= 1048572) && ((label % 4) == 0), "(-1048576 <= label && label <= 1048572) && ((label % 4) == 0)");
        instruction |= ((i_or_x_cc.value() & 0x3) << 20);
        instruction |= ((label >> 2) & 0x7ffff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code bn  }<i>i_or_x_cc</i>, <i>label</i>
     * Example disassembly syntax: {@code bn            %icc, L1: -1048576}
     * <p>
     * Constraint: {@code (-1048576 <= label && label <= 1048572) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.7"
     */
    // Template#: 319, Serial#: 319
    public void bn(final ICCOperand i_or_x_cc, final int label) {
        int instruction = 0x00480000;
        checkConstraint((-1048576 <= label && label <= 1048572) && ((label % 4) == 0), "(-1048576 <= label && label <= 1048572) && ((label % 4) == 0)");
        instruction |= ((i_or_x_cc.value() & 0x3) << 20);
        instruction |= ((label >> 2) & 0x7ffff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code bne  }<i>i_or_x_cc</i>, <i>label</i>
     * Example disassembly syntax: {@code bne           %icc, L1: -1048576}
     * <p>
     * Constraint: {@code (-1048576 <= label && label <= 1048572) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.7"
     */
    // Template#: 320, Serial#: 320
    public void bne(final ICCOperand i_or_x_cc, final int label) {
        int instruction = 0x12480000;
        checkConstraint((-1048576 <= label && label <= 1048572) && ((label % 4) == 0), "(-1048576 <= label && label <= 1048572) && ((label % 4) == 0)");
        instruction |= ((i_or_x_cc.value() & 0x3) << 20);
        instruction |= ((label >> 2) & 0x7ffff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code be  }<i>i_or_x_cc</i>, <i>label</i>
     * Example disassembly syntax: {@code be            %icc, L1: -1048576}
     * <p>
     * Constraint: {@code (-1048576 <= label && label <= 1048572) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.7"
     */
    // Template#: 321, Serial#: 321
    public void be(final ICCOperand i_or_x_cc, final int label) {
        int instruction = 0x02480000;
        checkConstraint((-1048576 <= label && label <= 1048572) && ((label % 4) == 0), "(-1048576 <= label && label <= 1048572) && ((label % 4) == 0)");
        instruction |= ((i_or_x_cc.value() & 0x3) << 20);
        instruction |= ((label >> 2) & 0x7ffff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code bg  }<i>i_or_x_cc</i>, <i>label</i>
     * Example disassembly syntax: {@code bg            %icc, L1: -1048576}
     * <p>
     * Constraint: {@code (-1048576 <= label && label <= 1048572) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.7"
     */
    // Template#: 322, Serial#: 322
    public void bg(final ICCOperand i_or_x_cc, final int label) {
        int instruction = 0x14480000;
        checkConstraint((-1048576 <= label && label <= 1048572) && ((label % 4) == 0), "(-1048576 <= label && label <= 1048572) && ((label % 4) == 0)");
        instruction |= ((i_or_x_cc.value() & 0x3) << 20);
        instruction |= ((label >> 2) & 0x7ffff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code ble  }<i>i_or_x_cc</i>, <i>label</i>
     * Example disassembly syntax: {@code ble           %icc, L1: -1048576}
     * <p>
     * Constraint: {@code (-1048576 <= label && label <= 1048572) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.7"
     */
    // Template#: 323, Serial#: 323
    public void ble(final ICCOperand i_or_x_cc, final int label) {
        int instruction = 0x04480000;
        checkConstraint((-1048576 <= label && label <= 1048572) && ((label % 4) == 0), "(-1048576 <= label && label <= 1048572) && ((label % 4) == 0)");
        instruction |= ((i_or_x_cc.value() & 0x3) << 20);
        instruction |= ((label >> 2) & 0x7ffff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code bge  }<i>i_or_x_cc</i>, <i>label</i>
     * Example disassembly syntax: {@code bge           %icc, L1: -1048576}
     * <p>
     * Constraint: {@code (-1048576 <= label && label <= 1048572) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.7"
     */
    // Template#: 324, Serial#: 324
    public void bge(final ICCOperand i_or_x_cc, final int label) {
        int instruction = 0x16480000;
        checkConstraint((-1048576 <= label && label <= 1048572) && ((label % 4) == 0), "(-1048576 <= label && label <= 1048572) && ((label % 4) == 0)");
        instruction |= ((i_or_x_cc.value() & 0x3) << 20);
        instruction |= ((label >> 2) & 0x7ffff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code bl  }<i>i_or_x_cc</i>, <i>label</i>
     * Example disassembly syntax: {@code bl            %icc, L1: -1048576}
     * <p>
     * Constraint: {@code (-1048576 <= label && label <= 1048572) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.7"
     */
    // Template#: 325, Serial#: 325
    public void bl(final ICCOperand i_or_x_cc, final int label) {
        int instruction = 0x06480000;
        checkConstraint((-1048576 <= label && label <= 1048572) && ((label % 4) == 0), "(-1048576 <= label && label <= 1048572) && ((label % 4) == 0)");
        instruction |= ((i_or_x_cc.value() & 0x3) << 20);
        instruction |= ((label >> 2) & 0x7ffff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code bgu  }<i>i_or_x_cc</i>, <i>label</i>
     * Example disassembly syntax: {@code bgu           %icc, L1: -1048576}
     * <p>
     * Constraint: {@code (-1048576 <= label && label <= 1048572) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.7"
     */
    // Template#: 326, Serial#: 326
    public void bgu(final ICCOperand i_or_x_cc, final int label) {
        int instruction = 0x18480000;
        checkConstraint((-1048576 <= label && label <= 1048572) && ((label % 4) == 0), "(-1048576 <= label && label <= 1048572) && ((label % 4) == 0)");
        instruction |= ((i_or_x_cc.value() & 0x3) << 20);
        instruction |= ((label >> 2) & 0x7ffff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code bleu  }<i>i_or_x_cc</i>, <i>label</i>
     * Example disassembly syntax: {@code bleu          %icc, L1: -1048576}
     * <p>
     * Constraint: {@code (-1048576 <= label && label <= 1048572) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.7"
     */
    // Template#: 327, Serial#: 327
    public void bleu(final ICCOperand i_or_x_cc, final int label) {
        int instruction = 0x08480000;
        checkConstraint((-1048576 <= label && label <= 1048572) && ((label % 4) == 0), "(-1048576 <= label && label <= 1048572) && ((label % 4) == 0)");
        instruction |= ((i_or_x_cc.value() & 0x3) << 20);
        instruction |= ((label >> 2) & 0x7ffff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code bcc  }<i>i_or_x_cc</i>, <i>label</i>
     * Example disassembly syntax: {@code bcc           %icc, L1: -1048576}
     * <p>
     * Constraint: {@code (-1048576 <= label && label <= 1048572) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.7"
     */
    // Template#: 328, Serial#: 328
    public void bcc(final ICCOperand i_or_x_cc, final int label) {
        int instruction = 0x1A480000;
        checkConstraint((-1048576 <= label && label <= 1048572) && ((label % 4) == 0), "(-1048576 <= label && label <= 1048572) && ((label % 4) == 0)");
        instruction |= ((i_or_x_cc.value() & 0x3) << 20);
        instruction |= ((label >> 2) & 0x7ffff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code bcs  }<i>i_or_x_cc</i>, <i>label</i>
     * Example disassembly syntax: {@code bcs           %icc, L1: -1048576}
     * <p>
     * Constraint: {@code (-1048576 <= label && label <= 1048572) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.7"
     */
    // Template#: 329, Serial#: 329
    public void bcs(final ICCOperand i_or_x_cc, final int label) {
        int instruction = 0x0A480000;
        checkConstraint((-1048576 <= label && label <= 1048572) && ((label % 4) == 0), "(-1048576 <= label && label <= 1048572) && ((label % 4) == 0)");
        instruction |= ((i_or_x_cc.value() & 0x3) << 20);
        instruction |= ((label >> 2) & 0x7ffff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code bpos  }<i>i_or_x_cc</i>, <i>label</i>
     * Example disassembly syntax: {@code bpos          %icc, L1: -1048576}
     * <p>
     * Constraint: {@code (-1048576 <= label && label <= 1048572) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.7"
     */
    // Template#: 330, Serial#: 330
    public void bpos(final ICCOperand i_or_x_cc, final int label) {
        int instruction = 0x1C480000;
        checkConstraint((-1048576 <= label && label <= 1048572) && ((label % 4) == 0), "(-1048576 <= label && label <= 1048572) && ((label % 4) == 0)");
        instruction |= ((i_or_x_cc.value() & 0x3) << 20);
        instruction |= ((label >> 2) & 0x7ffff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code bneg  }<i>i_or_x_cc</i>, <i>label</i>
     * Example disassembly syntax: {@code bneg          %icc, L1: -1048576}
     * <p>
     * Constraint: {@code (-1048576 <= label && label <= 1048572) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.7"
     */
    // Template#: 331, Serial#: 331
    public void bneg(final ICCOperand i_or_x_cc, final int label) {
        int instruction = 0x0C480000;
        checkConstraint((-1048576 <= label && label <= 1048572) && ((label % 4) == 0), "(-1048576 <= label && label <= 1048572) && ((label % 4) == 0)");
        instruction |= ((i_or_x_cc.value() & 0x3) << 20);
        instruction |= ((label >> 2) & 0x7ffff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code bvc  }<i>i_or_x_cc</i>, <i>label</i>
     * Example disassembly syntax: {@code bvc           %icc, L1: -1048576}
     * <p>
     * Constraint: {@code (-1048576 <= label && label <= 1048572) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.7"
     */
    // Template#: 332, Serial#: 332
    public void bvc(final ICCOperand i_or_x_cc, final int label) {
        int instruction = 0x1E480000;
        checkConstraint((-1048576 <= label && label <= 1048572) && ((label % 4) == 0), "(-1048576 <= label && label <= 1048572) && ((label % 4) == 0)");
        instruction |= ((i_or_x_cc.value() & 0x3) << 20);
        instruction |= ((label >> 2) & 0x7ffff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code bvs  }<i>i_or_x_cc</i>, <i>label</i>
     * Example disassembly syntax: {@code bvs           %icc, L1: -1048576}
     * <p>
     * Constraint: {@code (-1048576 <= label && label <= 1048572) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.7"
     */
    // Template#: 333, Serial#: 333
    public void bvs(final ICCOperand i_or_x_cc, final int label) {
        int instruction = 0x0E480000;
        checkConstraint((-1048576 <= label && label <= 1048572) && ((label % 4) == 0), "(-1048576 <= label && label <= 1048572) && ((label % 4) == 0)");
        instruction |= ((i_or_x_cc.value() & 0x3) << 20);
        instruction |= ((label >> 2) & 0x7ffff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code b[a|n|ne|e|g|le|ge|l|gu|leu|cc|cs|pos|neg|vc|vs]{,a}{,pn|,pt}  }<i>i_or_x_cc</i>, <i>label</i>
     * Example disassembly syntax: {@code ba,pn         %icc, L1: -1048576}
     * <p>
     * Constraint: {@code (-1048576 <= label && label <= 1048572) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.7"
     */
    // Template#: 334, Serial#: 334
    public void b(final Bicc cond, final AnnulBit a, final BranchPredictionBit p, final ICCOperand i_or_x_cc, final int label) {
        int instruction = 0x00400000;
        checkConstraint((-1048576 <= label && label <= 1048572) && ((label % 4) == 0), "(-1048576 <= label && label <= 1048572) && ((label % 4) == 0)");
        instruction |= ((cond.value() & 0xf) << 25);
        instruction |= ((a.value() & 0x1) << 29);
        instruction |= ((p.value() & 0x1) << 19);
        instruction |= ((i_or_x_cc.value() & 0x3) << 20);
        instruction |= ((label >> 2) & 0x7ffff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code call  }<i>label</i>
     * Example disassembly syntax: {@code call          L1: -2147483648}
     * <p>
     * Constraint: {@code (-2147483648 <= label && label <= 2147483644) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.8"
     */
    // Template#: 335, Serial#: 335
    public void call(final int label) {
        int instruction = 0x40000000;
        checkConstraint((-2147483648 <= label && label <= 2147483644) && ((label % 4) == 0), "(-2147483648 <= label && label <= 2147483644) && ((label % 4) == 0)");
        instruction |= ((label >> 2) & 0x3fffffff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code done  }
     * Example disassembly syntax: {@code done          }
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.11"
     */
    // Template#: 336, Serial#: 336
    public void done() {
        int instruction = 0x81F00000;
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code retry  }
     * Example disassembly syntax: {@code retry         }
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.11"
     */
    // Template#: 337, Serial#: 337
    public void retry() {
        int instruction = 0x83F00000;
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code illtrap  }<i>const22</i>
     * Example disassembly syntax: {@code illtrap       0x0}
     * <p>
     * Constraint: {@code 0 <= const22 && const22 <= 4194303}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.22"
     */
    // Template#: 338, Serial#: 338
    public void illtrap(final int const22) {
        int instruction = 0x00000000;
        checkConstraint(0 <= const22 && const22 <= 4194303, "0 <= const22 && const22 <= 4194303");
        instruction |= (const22 & 0x3fffff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code jmpl  }<i>rs1</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code jmpl          %g0 + %g0, %g0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.24"
     */
    // Template#: 339, Serial#: 339
    public void jmpl(final GPR rs1, final GPR rs2, final GPR rd) {
        int instruction = 0x81C00000;
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (rs2.value() & 0x1f);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code jmpl  }<i>rs1</i>, <i>simm13</i>, <i>rd</i>
     * Example disassembly syntax: {@code jmpl          %g0 + -4096, %g0}
     * <p>
     * Constraint: {@code -4096 <= simm13 && simm13 <= 4095}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.24"
     */
    // Template#: 340, Serial#: 340
    public void jmpl(final GPR rs1, final int simm13, final GPR rd) {
        int instruction = 0x81C02000;
        checkConstraint(-4096 <= simm13 && simm13 <= 4095, "-4096 <= simm13 && simm13 <= 4095");
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (simm13 & 0x1fff);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code return  }<i>rs1</i>, <i>rs2</i>
     * Example disassembly syntax: {@code return        %g0 + %g0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.45"
     */
    // Template#: 341, Serial#: 341
    public void return_(final GPR rs1, final GPR rs2) {
        int instruction = 0x81C80000;
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (rs2.value() & 0x1f);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code return  }<i>rs1</i>, <i>simm13</i>
     * Example disassembly syntax: {@code return        %g0 + -4096}
     * <p>
     * Constraint: {@code -4096 <= simm13 && simm13 <= 4095}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.45"
     */
    // Template#: 342, Serial#: 342
    public void return_(final GPR rs1, final int simm13) {
        int instruction = 0x81C82000;
        checkConstraint(-4096 <= simm13 && simm13 <= 4095, "-4096 <= simm13 && simm13 <= 4095");
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (simm13 & 0x1fff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code sir  }<i>simm13</i>
     * Example disassembly syntax: {@code sir           -4096}
     * <p>
     * Constraint: {@code -4096 <= simm13 && simm13 <= 4095}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.50"
     */
    // Template#: 343, Serial#: 343
    public void sir(final int simm13) {
        int instruction = 0x9F802000;
        checkConstraint(-4096 <= simm13 && simm13 <= 4095, "-4096 <= simm13 && simm13 <= 4095");
        instruction |= (simm13 & 0x1fff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code ta  }<i>i_or_x_cc</i>, <i>rs1</i>, <i>rs2</i>
     * Example disassembly syntax: {@code ta            %icc, %g0 + %g0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.61"
     */
    // Template#: 344, Serial#: 344
    public void ta(final ICCOperand i_or_x_cc, final GPR rs1, final GPR rs2) {
        int instruction = 0x91D00000;
        instruction |= ((i_or_x_cc.value() & 0x3) << 11);
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (rs2.value() & 0x1f);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code tn  }<i>i_or_x_cc</i>, <i>rs1</i>, <i>rs2</i>
     * Example disassembly syntax: {@code tn            %icc, %g0 + %g0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.61"
     */
    // Template#: 345, Serial#: 345
    public void tn(final ICCOperand i_or_x_cc, final GPR rs1, final GPR rs2) {
        int instruction = 0x81D00000;
        instruction |= ((i_or_x_cc.value() & 0x3) << 11);
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (rs2.value() & 0x1f);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code tne  }<i>i_or_x_cc</i>, <i>rs1</i>, <i>rs2</i>
     * Example disassembly syntax: {@code tne           %icc, %g0 + %g0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.61"
     */
    // Template#: 346, Serial#: 346
    public void tne(final ICCOperand i_or_x_cc, final GPR rs1, final GPR rs2) {
        int instruction = 0x93D00000;
        instruction |= ((i_or_x_cc.value() & 0x3) << 11);
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (rs2.value() & 0x1f);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code te  }<i>i_or_x_cc</i>, <i>rs1</i>, <i>rs2</i>
     * Example disassembly syntax: {@code te            %icc, %g0 + %g0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.61"
     */
    // Template#: 347, Serial#: 347
    public void te(final ICCOperand i_or_x_cc, final GPR rs1, final GPR rs2) {
        int instruction = 0x83D00000;
        instruction |= ((i_or_x_cc.value() & 0x3) << 11);
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (rs2.value() & 0x1f);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code tg  }<i>i_or_x_cc</i>, <i>rs1</i>, <i>rs2</i>
     * Example disassembly syntax: {@code tg            %icc, %g0 + %g0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.61"
     */
    // Template#: 348, Serial#: 348
    public void tg(final ICCOperand i_or_x_cc, final GPR rs1, final GPR rs2) {
        int instruction = 0x95D00000;
        instruction |= ((i_or_x_cc.value() & 0x3) << 11);
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (rs2.value() & 0x1f);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code tle  }<i>i_or_x_cc</i>, <i>rs1</i>, <i>rs2</i>
     * Example disassembly syntax: {@code tle           %icc, %g0 + %g0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.61"
     */
    // Template#: 349, Serial#: 349
    public void tle(final ICCOperand i_or_x_cc, final GPR rs1, final GPR rs2) {
        int instruction = 0x85D00000;
        instruction |= ((i_or_x_cc.value() & 0x3) << 11);
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (rs2.value() & 0x1f);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code tge  }<i>i_or_x_cc</i>, <i>rs1</i>, <i>rs2</i>
     * Example disassembly syntax: {@code tge           %icc, %g0 + %g0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.61"
     */
    // Template#: 350, Serial#: 350
    public void tge(final ICCOperand i_or_x_cc, final GPR rs1, final GPR rs2) {
        int instruction = 0x97D00000;
        instruction |= ((i_or_x_cc.value() & 0x3) << 11);
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (rs2.value() & 0x1f);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code tl  }<i>i_or_x_cc</i>, <i>rs1</i>, <i>rs2</i>
     * Example disassembly syntax: {@code tl            %icc, %g0 + %g0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.61"
     */
    // Template#: 351, Serial#: 351
    public void tl(final ICCOperand i_or_x_cc, final GPR rs1, final GPR rs2) {
        int instruction = 0x87D00000;
        instruction |= ((i_or_x_cc.value() & 0x3) << 11);
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (rs2.value() & 0x1f);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code tgu  }<i>i_or_x_cc</i>, <i>rs1</i>, <i>rs2</i>
     * Example disassembly syntax: {@code tgu           %icc, %g0 + %g0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.61"
     */
    // Template#: 352, Serial#: 352
    public void tgu(final ICCOperand i_or_x_cc, final GPR rs1, final GPR rs2) {
        int instruction = 0x99D00000;
        instruction |= ((i_or_x_cc.value() & 0x3) << 11);
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (rs2.value() & 0x1f);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code tleu  }<i>i_or_x_cc</i>, <i>rs1</i>, <i>rs2</i>
     * Example disassembly syntax: {@code tleu          %icc, %g0 + %g0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.61"
     */
    // Template#: 353, Serial#: 353
    public void tleu(final ICCOperand i_or_x_cc, final GPR rs1, final GPR rs2) {
        int instruction = 0x89D00000;
        instruction |= ((i_or_x_cc.value() & 0x3) << 11);
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (rs2.value() & 0x1f);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code tcc  }<i>i_or_x_cc</i>, <i>rs1</i>, <i>rs2</i>
     * Example disassembly syntax: {@code tcc           %icc, %g0 + %g0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.61"
     */
    // Template#: 354, Serial#: 354
    public void tcc(final ICCOperand i_or_x_cc, final GPR rs1, final GPR rs2) {
        int instruction = 0x9BD00000;
        instruction |= ((i_or_x_cc.value() & 0x3) << 11);
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (rs2.value() & 0x1f);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code tcs  }<i>i_or_x_cc</i>, <i>rs1</i>, <i>rs2</i>
     * Example disassembly syntax: {@code tcs           %icc, %g0 + %g0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.61"
     */
    // Template#: 355, Serial#: 355
    public void tcs(final ICCOperand i_or_x_cc, final GPR rs1, final GPR rs2) {
        int instruction = 0x8BD00000;
        instruction |= ((i_or_x_cc.value() & 0x3) << 11);
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (rs2.value() & 0x1f);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code tpos  }<i>i_or_x_cc</i>, <i>rs1</i>, <i>rs2</i>
     * Example disassembly syntax: {@code tpos          %icc, %g0 + %g0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.61"
     */
    // Template#: 356, Serial#: 356
    public void tpos(final ICCOperand i_or_x_cc, final GPR rs1, final GPR rs2) {
        int instruction = 0x9DD00000;
        instruction |= ((i_or_x_cc.value() & 0x3) << 11);
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (rs2.value() & 0x1f);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code tneg  }<i>i_or_x_cc</i>, <i>rs1</i>, <i>rs2</i>
     * Example disassembly syntax: {@code tneg          %icc, %g0 + %g0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.61"
     */
    // Template#: 357, Serial#: 357
    public void tneg(final ICCOperand i_or_x_cc, final GPR rs1, final GPR rs2) {
        int instruction = 0x8DD00000;
        instruction |= ((i_or_x_cc.value() & 0x3) << 11);
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (rs2.value() & 0x1f);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code tvc  }<i>i_or_x_cc</i>, <i>rs1</i>, <i>rs2</i>
     * Example disassembly syntax: {@code tvc           %icc, %g0 + %g0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.61"
     */
    // Template#: 358, Serial#: 358
    public void tvc(final ICCOperand i_or_x_cc, final GPR rs1, final GPR rs2) {
        int instruction = 0x9FD00000;
        instruction |= ((i_or_x_cc.value() & 0x3) << 11);
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (rs2.value() & 0x1f);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code tvs  }<i>i_or_x_cc</i>, <i>rs1</i>, <i>rs2</i>
     * Example disassembly syntax: {@code tvs           %icc, %g0 + %g0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.61"
     */
    // Template#: 359, Serial#: 359
    public void tvs(final ICCOperand i_or_x_cc, final GPR rs1, final GPR rs2) {
        int instruction = 0x8FD00000;
        instruction |= ((i_or_x_cc.value() & 0x3) << 11);
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (rs2.value() & 0x1f);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code ta  }<i>i_or_x_cc</i>, <i>rs1</i>, <i>software_trap_number</i>
     * Example disassembly syntax: {@code ta            %icc, %g0 + 0x0}
     * <p>
     * Constraint: {@code 0 <= software_trap_number && software_trap_number <= 127}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.61"
     */
    // Template#: 360, Serial#: 360
    public void ta(final ICCOperand i_or_x_cc, final GPR rs1, final int software_trap_number) {
        int instruction = 0x91D02000;
        checkConstraint(0 <= software_trap_number && software_trap_number <= 127, "0 <= software_trap_number && software_trap_number <= 127");
        instruction |= ((i_or_x_cc.value() & 0x3) << 11);
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (software_trap_number & 0x7f);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code tn  }<i>i_or_x_cc</i>, <i>rs1</i>, <i>software_trap_number</i>
     * Example disassembly syntax: {@code tn            %icc, %g0 + 0x0}
     * <p>
     * Constraint: {@code 0 <= software_trap_number && software_trap_number <= 127}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.61"
     */
    // Template#: 361, Serial#: 361
    public void tn(final ICCOperand i_or_x_cc, final GPR rs1, final int software_trap_number) {
        int instruction = 0x81D02000;
        checkConstraint(0 <= software_trap_number && software_trap_number <= 127, "0 <= software_trap_number && software_trap_number <= 127");
        instruction |= ((i_or_x_cc.value() & 0x3) << 11);
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (software_trap_number & 0x7f);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code tne  }<i>i_or_x_cc</i>, <i>rs1</i>, <i>software_trap_number</i>
     * Example disassembly syntax: {@code tne           %icc, %g0 + 0x0}
     * <p>
     * Constraint: {@code 0 <= software_trap_number && software_trap_number <= 127}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.61"
     */
    // Template#: 362, Serial#: 362
    public void tne(final ICCOperand i_or_x_cc, final GPR rs1, final int software_trap_number) {
        int instruction = 0x93D02000;
        checkConstraint(0 <= software_trap_number && software_trap_number <= 127, "0 <= software_trap_number && software_trap_number <= 127");
        instruction |= ((i_or_x_cc.value() & 0x3) << 11);
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (software_trap_number & 0x7f);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code te  }<i>i_or_x_cc</i>, <i>rs1</i>, <i>software_trap_number</i>
     * Example disassembly syntax: {@code te            %icc, %g0 + 0x0}
     * <p>
     * Constraint: {@code 0 <= software_trap_number && software_trap_number <= 127}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.61"
     */
    // Template#: 363, Serial#: 363
    public void te(final ICCOperand i_or_x_cc, final GPR rs1, final int software_trap_number) {
        int instruction = 0x83D02000;
        checkConstraint(0 <= software_trap_number && software_trap_number <= 127, "0 <= software_trap_number && software_trap_number <= 127");
        instruction |= ((i_or_x_cc.value() & 0x3) << 11);
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (software_trap_number & 0x7f);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code tg  }<i>i_or_x_cc</i>, <i>rs1</i>, <i>software_trap_number</i>
     * Example disassembly syntax: {@code tg            %icc, %g0 + 0x0}
     * <p>
     * Constraint: {@code 0 <= software_trap_number && software_trap_number <= 127}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.61"
     */
    // Template#: 364, Serial#: 364
    public void tg(final ICCOperand i_or_x_cc, final GPR rs1, final int software_trap_number) {
        int instruction = 0x95D02000;
        checkConstraint(0 <= software_trap_number && software_trap_number <= 127, "0 <= software_trap_number && software_trap_number <= 127");
        instruction |= ((i_or_x_cc.value() & 0x3) << 11);
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (software_trap_number & 0x7f);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code tle  }<i>i_or_x_cc</i>, <i>rs1</i>, <i>software_trap_number</i>
     * Example disassembly syntax: {@code tle           %icc, %g0 + 0x0}
     * <p>
     * Constraint: {@code 0 <= software_trap_number && software_trap_number <= 127}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.61"
     */
    // Template#: 365, Serial#: 365
    public void tle(final ICCOperand i_or_x_cc, final GPR rs1, final int software_trap_number) {
        int instruction = 0x85D02000;
        checkConstraint(0 <= software_trap_number && software_trap_number <= 127, "0 <= software_trap_number && software_trap_number <= 127");
        instruction |= ((i_or_x_cc.value() & 0x3) << 11);
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (software_trap_number & 0x7f);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code tge  }<i>i_or_x_cc</i>, <i>rs1</i>, <i>software_trap_number</i>
     * Example disassembly syntax: {@code tge           %icc, %g0 + 0x0}
     * <p>
     * Constraint: {@code 0 <= software_trap_number && software_trap_number <= 127}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.61"
     */
    // Template#: 366, Serial#: 366
    public void tge(final ICCOperand i_or_x_cc, final GPR rs1, final int software_trap_number) {
        int instruction = 0x97D02000;
        checkConstraint(0 <= software_trap_number && software_trap_number <= 127, "0 <= software_trap_number && software_trap_number <= 127");
        instruction |= ((i_or_x_cc.value() & 0x3) << 11);
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (software_trap_number & 0x7f);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code tl  }<i>i_or_x_cc</i>, <i>rs1</i>, <i>software_trap_number</i>
     * Example disassembly syntax: {@code tl            %icc, %g0 + 0x0}
     * <p>
     * Constraint: {@code 0 <= software_trap_number && software_trap_number <= 127}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.61"
     */
    // Template#: 367, Serial#: 367
    public void tl(final ICCOperand i_or_x_cc, final GPR rs1, final int software_trap_number) {
        int instruction = 0x87D02000;
        checkConstraint(0 <= software_trap_number && software_trap_number <= 127, "0 <= software_trap_number && software_trap_number <= 127");
        instruction |= ((i_or_x_cc.value() & 0x3) << 11);
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (software_trap_number & 0x7f);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code tgu  }<i>i_or_x_cc</i>, <i>rs1</i>, <i>software_trap_number</i>
     * Example disassembly syntax: {@code tgu           %icc, %g0 + 0x0}
     * <p>
     * Constraint: {@code 0 <= software_trap_number && software_trap_number <= 127}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.61"
     */
    // Template#: 368, Serial#: 368
    public void tgu(final ICCOperand i_or_x_cc, final GPR rs1, final int software_trap_number) {
        int instruction = 0x99D02000;
        checkConstraint(0 <= software_trap_number && software_trap_number <= 127, "0 <= software_trap_number && software_trap_number <= 127");
        instruction |= ((i_or_x_cc.value() & 0x3) << 11);
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (software_trap_number & 0x7f);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code tleu  }<i>i_or_x_cc</i>, <i>rs1</i>, <i>software_trap_number</i>
     * Example disassembly syntax: {@code tleu          %icc, %g0 + 0x0}
     * <p>
     * Constraint: {@code 0 <= software_trap_number && software_trap_number <= 127}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.61"
     */
    // Template#: 369, Serial#: 369
    public void tleu(final ICCOperand i_or_x_cc, final GPR rs1, final int software_trap_number) {
        int instruction = 0x89D02000;
        checkConstraint(0 <= software_trap_number && software_trap_number <= 127, "0 <= software_trap_number && software_trap_number <= 127");
        instruction |= ((i_or_x_cc.value() & 0x3) << 11);
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (software_trap_number & 0x7f);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code tcc  }<i>i_or_x_cc</i>, <i>rs1</i>, <i>software_trap_number</i>
     * Example disassembly syntax: {@code tcc           %icc, %g0 + 0x0}
     * <p>
     * Constraint: {@code 0 <= software_trap_number && software_trap_number <= 127}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.61"
     */
    // Template#: 370, Serial#: 370
    public void tcc(final ICCOperand i_or_x_cc, final GPR rs1, final int software_trap_number) {
        int instruction = 0x9BD02000;
        checkConstraint(0 <= software_trap_number && software_trap_number <= 127, "0 <= software_trap_number && software_trap_number <= 127");
        instruction |= ((i_or_x_cc.value() & 0x3) << 11);
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (software_trap_number & 0x7f);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code tcs  }<i>i_or_x_cc</i>, <i>rs1</i>, <i>software_trap_number</i>
     * Example disassembly syntax: {@code tcs           %icc, %g0 + 0x0}
     * <p>
     * Constraint: {@code 0 <= software_trap_number && software_trap_number <= 127}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.61"
     */
    // Template#: 371, Serial#: 371
    public void tcs(final ICCOperand i_or_x_cc, final GPR rs1, final int software_trap_number) {
        int instruction = 0x8BD02000;
        checkConstraint(0 <= software_trap_number && software_trap_number <= 127, "0 <= software_trap_number && software_trap_number <= 127");
        instruction |= ((i_or_x_cc.value() & 0x3) << 11);
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (software_trap_number & 0x7f);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code tpos  }<i>i_or_x_cc</i>, <i>rs1</i>, <i>software_trap_number</i>
     * Example disassembly syntax: {@code tpos          %icc, %g0 + 0x0}
     * <p>
     * Constraint: {@code 0 <= software_trap_number && software_trap_number <= 127}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.61"
     */
    // Template#: 372, Serial#: 372
    public void tpos(final ICCOperand i_or_x_cc, final GPR rs1, final int software_trap_number) {
        int instruction = 0x9DD02000;
        checkConstraint(0 <= software_trap_number && software_trap_number <= 127, "0 <= software_trap_number && software_trap_number <= 127");
        instruction |= ((i_or_x_cc.value() & 0x3) << 11);
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (software_trap_number & 0x7f);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code tneg  }<i>i_or_x_cc</i>, <i>rs1</i>, <i>software_trap_number</i>
     * Example disassembly syntax: {@code tneg          %icc, %g0 + 0x0}
     * <p>
     * Constraint: {@code 0 <= software_trap_number && software_trap_number <= 127}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.61"
     */
    // Template#: 373, Serial#: 373
    public void tneg(final ICCOperand i_or_x_cc, final GPR rs1, final int software_trap_number) {
        int instruction = 0x8DD02000;
        checkConstraint(0 <= software_trap_number && software_trap_number <= 127, "0 <= software_trap_number && software_trap_number <= 127");
        instruction |= ((i_or_x_cc.value() & 0x3) << 11);
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (software_trap_number & 0x7f);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code tvc  }<i>i_or_x_cc</i>, <i>rs1</i>, <i>software_trap_number</i>
     * Example disassembly syntax: {@code tvc           %icc, %g0 + 0x0}
     * <p>
     * Constraint: {@code 0 <= software_trap_number && software_trap_number <= 127}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.61"
     */
    // Template#: 374, Serial#: 374
    public void tvc(final ICCOperand i_or_x_cc, final GPR rs1, final int software_trap_number) {
        int instruction = 0x9FD02000;
        checkConstraint(0 <= software_trap_number && software_trap_number <= 127, "0 <= software_trap_number && software_trap_number <= 127");
        instruction |= ((i_or_x_cc.value() & 0x3) << 11);
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (software_trap_number & 0x7f);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code tvs  }<i>i_or_x_cc</i>, <i>rs1</i>, <i>software_trap_number</i>
     * Example disassembly syntax: {@code tvs           %icc, %g0 + 0x0}
     * <p>
     * Constraint: {@code 0 <= software_trap_number && software_trap_number <= 127}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.61"
     */
    // Template#: 375, Serial#: 375
    public void tvs(final ICCOperand i_or_x_cc, final GPR rs1, final int software_trap_number) {
        int instruction = 0x8FD02000;
        checkConstraint(0 <= software_trap_number && software_trap_number <= 127, "0 <= software_trap_number && software_trap_number <= 127");
        instruction |= ((i_or_x_cc.value() & 0x3) << 11);
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (software_trap_number & 0x7f);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fmovrse  }<i>rs1</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code fmovrse       %g0, %f0, %f0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.34"
     */
    // Template#: 376, Serial#: 376
    public void fmovrse(final GPR rs1, final SFPR rs2, final SFPR rd) {
        int instruction = 0x81A804A0;
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (rs2.value() & 0x1f);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fmovrde  }<i>rs1</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code fmovrde       %g0, %f0, %f0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.34"
     */
    // Template#: 377, Serial#: 377
    public void fmovrde(final GPR rs1, final DFPR rs2, final DFPR rd) {
        int instruction = 0x81A804C0;
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (((rs2.value() >>> 1) & 0xf) << 1) | ((rs2.value() >>> 5) & 0x1);
        instruction |= (((rd.value() >>> 1) & 0xf) << 26) | (((rd.value() >>> 5) & 0x1) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fmovrqe  }<i>rs1</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code fmovrqe       %g0, %f0, %f0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.34"
     */
    // Template#: 378, Serial#: 378
    public void fmovrqe(final GPR rs1, final QFPR rs2, final QFPR rd) {
        int instruction = 0x81A804E0;
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (((rs2.value() >>> 2) & 0x7) << 2) | ((rs2.value() >>> 5) & 0x1);
        instruction |= (((rd.value() >>> 2) & 0x7) << 27) | (((rd.value() >>> 5) & 0x1) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code movre  }<i>rs1</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code movre         %g0, %g0, %g0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.36"
     */
    // Template#: 379, Serial#: 379
    public void movre(final GPR rs1, final GPR rs2, final GPR rd) {
        int instruction = 0x81780400;
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (rs2.value() & 0x1f);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code movre  }<i>rs1</i>, <i>simm10</i>, <i>rd</i>
     * Example disassembly syntax: {@code movre         %g0, -512, %g0}
     * <p>
     * Constraint: {@code -512 <= simm10 && simm10 <= 511}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.36"
     */
    // Template#: 380, Serial#: 380
    public void movre(final GPR rs1, final int simm10, final GPR rd) {
        int instruction = 0x81782400;
        checkConstraint(-512 <= simm10 && simm10 <= 511, "-512 <= simm10 && simm10 <= 511");
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (simm10 & 0x3ff);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fmovrslez  }<i>rs1</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code fmovrslez     %g0, %f0, %f0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.34"
     */
    // Template#: 381, Serial#: 381
    public void fmovrslez(final GPR rs1, final SFPR rs2, final SFPR rd) {
        int instruction = 0x81A808A0;
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (rs2.value() & 0x1f);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fmovrdlez  }<i>rs1</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code fmovrdlez     %g0, %f0, %f0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.34"
     */
    // Template#: 382, Serial#: 382
    public void fmovrdlez(final GPR rs1, final DFPR rs2, final DFPR rd) {
        int instruction = 0x81A808C0;
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (((rs2.value() >>> 1) & 0xf) << 1) | ((rs2.value() >>> 5) & 0x1);
        instruction |= (((rd.value() >>> 1) & 0xf) << 26) | (((rd.value() >>> 5) & 0x1) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fmovrqlez  }<i>rs1</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code fmovrqlez     %g0, %f0, %f0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.34"
     */
    // Template#: 383, Serial#: 383
    public void fmovrqlez(final GPR rs1, final QFPR rs2, final QFPR rd) {
        int instruction = 0x81A808E0;
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (((rs2.value() >>> 2) & 0x7) << 2) | ((rs2.value() >>> 5) & 0x1);
        instruction |= (((rd.value() >>> 2) & 0x7) << 27) | (((rd.value() >>> 5) & 0x1) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code movrlez  }<i>rs1</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code movrlez       %g0, %g0, %g0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.36"
     */
    // Template#: 384, Serial#: 384
    public void movrlez(final GPR rs1, final GPR rs2, final GPR rd) {
        int instruction = 0x81780800;
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (rs2.value() & 0x1f);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code movrlez  }<i>rs1</i>, <i>simm10</i>, <i>rd</i>
     * Example disassembly syntax: {@code movrlez       %g0, -512, %g0}
     * <p>
     * Constraint: {@code -512 <= simm10 && simm10 <= 511}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.36"
     */
    // Template#: 385, Serial#: 385
    public void movrlez(final GPR rs1, final int simm10, final GPR rd) {
        int instruction = 0x81782800;
        checkConstraint(-512 <= simm10 && simm10 <= 511, "-512 <= simm10 && simm10 <= 511");
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (simm10 & 0x3ff);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fmovrslz  }<i>rs1</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code fmovrslz      %g0, %f0, %f0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.34"
     */
    // Template#: 386, Serial#: 386
    public void fmovrslz(final GPR rs1, final SFPR rs2, final SFPR rd) {
        int instruction = 0x81A80CA0;
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (rs2.value() & 0x1f);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fmovrdlz  }<i>rs1</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code fmovrdlz      %g0, %f0, %f0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.34"
     */
    // Template#: 387, Serial#: 387
    public void fmovrdlz(final GPR rs1, final DFPR rs2, final DFPR rd) {
        int instruction = 0x81A80CC0;
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (((rs2.value() >>> 1) & 0xf) << 1) | ((rs2.value() >>> 5) & 0x1);
        instruction |= (((rd.value() >>> 1) & 0xf) << 26) | (((rd.value() >>> 5) & 0x1) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fmovrqlz  }<i>rs1</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code fmovrqlz      %g0, %f0, %f0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.34"
     */
    // Template#: 388, Serial#: 388
    public void fmovrqlz(final GPR rs1, final QFPR rs2, final QFPR rd) {
        int instruction = 0x81A80CE0;
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (((rs2.value() >>> 2) & 0x7) << 2) | ((rs2.value() >>> 5) & 0x1);
        instruction |= (((rd.value() >>> 2) & 0x7) << 27) | (((rd.value() >>> 5) & 0x1) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code movrlz  }<i>rs1</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code movrlz        %g0, %g0, %g0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.36"
     */
    // Template#: 389, Serial#: 389
    public void movrlz(final GPR rs1, final GPR rs2, final GPR rd) {
        int instruction = 0x81780C00;
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (rs2.value() & 0x1f);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code movrlz  }<i>rs1</i>, <i>simm10</i>, <i>rd</i>
     * Example disassembly syntax: {@code movrlz        %g0, -512, %g0}
     * <p>
     * Constraint: {@code -512 <= simm10 && simm10 <= 511}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.36"
     */
    // Template#: 390, Serial#: 390
    public void movrlz(final GPR rs1, final int simm10, final GPR rd) {
        int instruction = 0x81782C00;
        checkConstraint(-512 <= simm10 && simm10 <= 511, "-512 <= simm10 && simm10 <= 511");
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (simm10 & 0x3ff);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fmovrsne  }<i>rs1</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code fmovrsne      %g0, %f0, %f0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.34"
     */
    // Template#: 391, Serial#: 391
    public void fmovrsne(final GPR rs1, final SFPR rs2, final SFPR rd) {
        int instruction = 0x81A814A0;
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (rs2.value() & 0x1f);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fmovrdne  }<i>rs1</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code fmovrdne      %g0, %f0, %f0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.34"
     */
    // Template#: 392, Serial#: 392
    public void fmovrdne(final GPR rs1, final DFPR rs2, final DFPR rd) {
        int instruction = 0x81A814C0;
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (((rs2.value() >>> 1) & 0xf) << 1) | ((rs2.value() >>> 5) & 0x1);
        instruction |= (((rd.value() >>> 1) & 0xf) << 26) | (((rd.value() >>> 5) & 0x1) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fmovrqne  }<i>rs1</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code fmovrqne      %g0, %f0, %f0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.34"
     */
    // Template#: 393, Serial#: 393
    public void fmovrqne(final GPR rs1, final QFPR rs2, final QFPR rd) {
        int instruction = 0x81A814E0;
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (((rs2.value() >>> 2) & 0x7) << 2) | ((rs2.value() >>> 5) & 0x1);
        instruction |= (((rd.value() >>> 2) & 0x7) << 27) | (((rd.value() >>> 5) & 0x1) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code movrne  }<i>rs1</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code movrne        %g0, %g0, %g0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.36"
     */
    // Template#: 394, Serial#: 394
    public void movrne(final GPR rs1, final GPR rs2, final GPR rd) {
        int instruction = 0x81781400;
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (rs2.value() & 0x1f);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code movrne  }<i>rs1</i>, <i>simm10</i>, <i>rd</i>
     * Example disassembly syntax: {@code movrne        %g0, -512, %g0}
     * <p>
     * Constraint: {@code -512 <= simm10 && simm10 <= 511}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.36"
     */
    // Template#: 395, Serial#: 395
    public void movrne(final GPR rs1, final int simm10, final GPR rd) {
        int instruction = 0x81783400;
        checkConstraint(-512 <= simm10 && simm10 <= 511, "-512 <= simm10 && simm10 <= 511");
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (simm10 & 0x3ff);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fmovrsgz  }<i>rs1</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code fmovrsgz      %g0, %f0, %f0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.34"
     */
    // Template#: 396, Serial#: 396
    public void fmovrsgz(final GPR rs1, final SFPR rs2, final SFPR rd) {
        int instruction = 0x81A818A0;
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (rs2.value() & 0x1f);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fmovrdgz  }<i>rs1</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code fmovrdgz      %g0, %f0, %f0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.34"
     */
    // Template#: 397, Serial#: 397
    public void fmovrdgz(final GPR rs1, final DFPR rs2, final DFPR rd) {
        int instruction = 0x81A818C0;
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (((rs2.value() >>> 1) & 0xf) << 1) | ((rs2.value() >>> 5) & 0x1);
        instruction |= (((rd.value() >>> 1) & 0xf) << 26) | (((rd.value() >>> 5) & 0x1) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fmovrqgz  }<i>rs1</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code fmovrqgz      %g0, %f0, %f0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.34"
     */
    // Template#: 398, Serial#: 398
    public void fmovrqgz(final GPR rs1, final QFPR rs2, final QFPR rd) {
        int instruction = 0x81A818E0;
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (((rs2.value() >>> 2) & 0x7) << 2) | ((rs2.value() >>> 5) & 0x1);
        instruction |= (((rd.value() >>> 2) & 0x7) << 27) | (((rd.value() >>> 5) & 0x1) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code movrgz  }<i>rs1</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code movrgz        %g0, %g0, %g0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.36"
     */
    // Template#: 399, Serial#: 399
    public void movrgz(final GPR rs1, final GPR rs2, final GPR rd) {
        int instruction = 0x81781800;
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (rs2.value() & 0x1f);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code movrgz  }<i>rs1</i>, <i>simm10</i>, <i>rd</i>
     * Example disassembly syntax: {@code movrgz        %g0, -512, %g0}
     * <p>
     * Constraint: {@code -512 <= simm10 && simm10 <= 511}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.36"
     */
    // Template#: 400, Serial#: 400
    public void movrgz(final GPR rs1, final int simm10, final GPR rd) {
        int instruction = 0x81783800;
        checkConstraint(-512 <= simm10 && simm10 <= 511, "-512 <= simm10 && simm10 <= 511");
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (simm10 & 0x3ff);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fmovrsgez  }<i>rs1</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code fmovrsgez     %g0, %f0, %f0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.34"
     */
    // Template#: 401, Serial#: 401
    public void fmovrsgez(final GPR rs1, final SFPR rs2, final SFPR rd) {
        int instruction = 0x81A81CA0;
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (rs2.value() & 0x1f);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fmovrdgez  }<i>rs1</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code fmovrdgez     %g0, %f0, %f0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.34"
     */
    // Template#: 402, Serial#: 402
    public void fmovrdgez(final GPR rs1, final DFPR rs2, final DFPR rd) {
        int instruction = 0x81A81CC0;
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (((rs2.value() >>> 1) & 0xf) << 1) | ((rs2.value() >>> 5) & 0x1);
        instruction |= (((rd.value() >>> 1) & 0xf) << 26) | (((rd.value() >>> 5) & 0x1) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fmovrqgez  }<i>rs1</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code fmovrqgez     %g0, %f0, %f0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.34"
     */
    // Template#: 403, Serial#: 403
    public void fmovrqgez(final GPR rs1, final QFPR rs2, final QFPR rd) {
        int instruction = 0x81A81CE0;
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (((rs2.value() >>> 2) & 0x7) << 2) | ((rs2.value() >>> 5) & 0x1);
        instruction |= (((rd.value() >>> 2) & 0x7) << 27) | (((rd.value() >>> 5) & 0x1) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code movrgez  }<i>rs1</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code movrgez       %g0, %g0, %g0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.36"
     */
    // Template#: 404, Serial#: 404
    public void movrgez(final GPR rs1, final GPR rs2, final GPR rd) {
        int instruction = 0x81781C00;
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (rs2.value() & 0x1f);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code movrgez  }<i>rs1</i>, <i>simm10</i>, <i>rd</i>
     * Example disassembly syntax: {@code movrgez       %g0, -512, %g0}
     * <p>
     * Constraint: {@code -512 <= simm10 && simm10 <= 511}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.36"
     */
    // Template#: 405, Serial#: 405
    public void movrgez(final GPR rs1, final int simm10, final GPR rd) {
        int instruction = 0x81783C00;
        checkConstraint(-512 <= simm10 && simm10 <= 511, "-512 <= simm10 && simm10 <= 511");
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (simm10 & 0x3ff);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fmovsa  }<i>i_or_x_cc</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code fmovsa        %icc, %f0, %f0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.33"
     */
    // Template#: 406, Serial#: 406
    public void fmovsa(final ICCOperand i_or_x_cc, final SFPR rs2, final SFPR rd) {
        int instruction = 0x81AA2020;
        instruction |= ((i_or_x_cc.value() & 0x3) << 11);
        instruction |= (rs2.value() & 0x1f);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fmovda  }<i>i_or_x_cc</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code fmovda        %icc, %f0, %f0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.33"
     */
    // Template#: 407, Serial#: 407
    public void fmovda(final ICCOperand i_or_x_cc, final DFPR rs2, final DFPR rd) {
        int instruction = 0x81AA2040;
        instruction |= ((i_or_x_cc.value() & 0x3) << 11);
        instruction |= (((rs2.value() >>> 1) & 0xf) << 1) | ((rs2.value() >>> 5) & 0x1);
        instruction |= (((rd.value() >>> 1) & 0xf) << 26) | (((rd.value() >>> 5) & 0x1) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fmovqa  }<i>i_or_x_cc</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code fmovqa        %icc, %f0, %f0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.33"
     */
    // Template#: 408, Serial#: 408
    public void fmovqa(final ICCOperand i_or_x_cc, final QFPR rs2, final QFPR rd) {
        int instruction = 0x81AA2060;
        instruction |= ((i_or_x_cc.value() & 0x3) << 11);
        instruction |= (((rs2.value() >>> 2) & 0x7) << 2) | ((rs2.value() >>> 5) & 0x1);
        instruction |= (((rd.value() >>> 2) & 0x7) << 27) | (((rd.value() >>> 5) & 0x1) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code mova  }<i>i_or_x_cc</i>, <i>simm11</i>, <i>rd</i>
     * Example disassembly syntax: {@code mova          %icc, -1024, %g0}
     * <p>
     * Constraint: {@code -1024 <= simm11 && simm11 <= 1023}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.35"
     */
    // Template#: 409, Serial#: 409
    public void mova(final ICCOperand i_or_x_cc, final int simm11, final GPR rd) {
        int instruction = 0x81662000;
        checkConstraint(-1024 <= simm11 && simm11 <= 1023, "-1024 <= simm11 && simm11 <= 1023");
        instruction |= ((i_or_x_cc.value() & 0x3) << 11);
        instruction |= (simm11 & 0x7ff);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code mova  }<i>i_or_x_cc</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code mova          %icc, %g0, %g0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.35"
     */
    // Template#: 410, Serial#: 410
    public void mova(final ICCOperand i_or_x_cc, final GPR rs2, final GPR rd) {
        int instruction = 0x81660000;
        instruction |= ((i_or_x_cc.value() & 0x3) << 11);
        instruction |= (rs2.value() & 0x1f);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fmovsn  }<i>i_or_x_cc</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code fmovsn        %icc, %f0, %f0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.33"
     */
    // Template#: 411, Serial#: 411
    public void fmovsn(final ICCOperand i_or_x_cc, final SFPR rs2, final SFPR rd) {
        int instruction = 0x81A82020;
        instruction |= ((i_or_x_cc.value() & 0x3) << 11);
        instruction |= (rs2.value() & 0x1f);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fmovdn  }<i>i_or_x_cc</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code fmovdn        %icc, %f0, %f0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.33"
     */
    // Template#: 412, Serial#: 412
    public void fmovdn(final ICCOperand i_or_x_cc, final DFPR rs2, final DFPR rd) {
        int instruction = 0x81A82040;
        instruction |= ((i_or_x_cc.value() & 0x3) << 11);
        instruction |= (((rs2.value() >>> 1) & 0xf) << 1) | ((rs2.value() >>> 5) & 0x1);
        instruction |= (((rd.value() >>> 1) & 0xf) << 26) | (((rd.value() >>> 5) & 0x1) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fmovqn  }<i>i_or_x_cc</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code fmovqn        %icc, %f0, %f0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.33"
     */
    // Template#: 413, Serial#: 413
    public void fmovqn(final ICCOperand i_or_x_cc, final QFPR rs2, final QFPR rd) {
        int instruction = 0x81A82060;
        instruction |= ((i_or_x_cc.value() & 0x3) << 11);
        instruction |= (((rs2.value() >>> 2) & 0x7) << 2) | ((rs2.value() >>> 5) & 0x1);
        instruction |= (((rd.value() >>> 2) & 0x7) << 27) | (((rd.value() >>> 5) & 0x1) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code movn  }<i>i_or_x_cc</i>, <i>simm11</i>, <i>rd</i>
     * Example disassembly syntax: {@code movn          %icc, -1024, %g0}
     * <p>
     * Constraint: {@code -1024 <= simm11 && simm11 <= 1023}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.35"
     */
    // Template#: 414, Serial#: 414
    public void movn(final ICCOperand i_or_x_cc, final int simm11, final GPR rd) {
        int instruction = 0x81642000;
        checkConstraint(-1024 <= simm11 && simm11 <= 1023, "-1024 <= simm11 && simm11 <= 1023");
        instruction |= ((i_or_x_cc.value() & 0x3) << 11);
        instruction |= (simm11 & 0x7ff);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code movn  }<i>i_or_x_cc</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code movn          %icc, %g0, %g0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.35"
     */
    // Template#: 415, Serial#: 415
    public void movn(final ICCOperand i_or_x_cc, final GPR rs2, final GPR rd) {
        int instruction = 0x81640000;
        instruction |= ((i_or_x_cc.value() & 0x3) << 11);
        instruction |= (rs2.value() & 0x1f);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fmovsne  }<i>i_or_x_cc</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code fmovsne       %icc, %f0, %f0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.33"
     */
    // Template#: 416, Serial#: 416
    public void fmovsne(final ICCOperand i_or_x_cc, final SFPR rs2, final SFPR rd) {
        int instruction = 0x81AA6020;
        instruction |= ((i_or_x_cc.value() & 0x3) << 11);
        instruction |= (rs2.value() & 0x1f);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fmovdne  }<i>i_or_x_cc</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code fmovdne       %icc, %f0, %f0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.33"
     */
    // Template#: 417, Serial#: 417
    public void fmovdne(final ICCOperand i_or_x_cc, final DFPR rs2, final DFPR rd) {
        int instruction = 0x81AA6040;
        instruction |= ((i_or_x_cc.value() & 0x3) << 11);
        instruction |= (((rs2.value() >>> 1) & 0xf) << 1) | ((rs2.value() >>> 5) & 0x1);
        instruction |= (((rd.value() >>> 1) & 0xf) << 26) | (((rd.value() >>> 5) & 0x1) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fmovqne  }<i>i_or_x_cc</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code fmovqne       %icc, %f0, %f0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.33"
     */
    // Template#: 418, Serial#: 418
    public void fmovqne(final ICCOperand i_or_x_cc, final QFPR rs2, final QFPR rd) {
        int instruction = 0x81AA6060;
        instruction |= ((i_or_x_cc.value() & 0x3) << 11);
        instruction |= (((rs2.value() >>> 2) & 0x7) << 2) | ((rs2.value() >>> 5) & 0x1);
        instruction |= (((rd.value() >>> 2) & 0x7) << 27) | (((rd.value() >>> 5) & 0x1) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code movne  }<i>i_or_x_cc</i>, <i>simm11</i>, <i>rd</i>
     * Example disassembly syntax: {@code movne         %icc, -1024, %g0}
     * <p>
     * Constraint: {@code -1024 <= simm11 && simm11 <= 1023}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.35"
     */
    // Template#: 419, Serial#: 419
    public void movne(final ICCOperand i_or_x_cc, final int simm11, final GPR rd) {
        int instruction = 0x81666000;
        checkConstraint(-1024 <= simm11 && simm11 <= 1023, "-1024 <= simm11 && simm11 <= 1023");
        instruction |= ((i_or_x_cc.value() & 0x3) << 11);
        instruction |= (simm11 & 0x7ff);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code movne  }<i>i_or_x_cc</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code movne         %icc, %g0, %g0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.35"
     */
    // Template#: 420, Serial#: 420
    public void movne(final ICCOperand i_or_x_cc, final GPR rs2, final GPR rd) {
        int instruction = 0x81664000;
        instruction |= ((i_or_x_cc.value() & 0x3) << 11);
        instruction |= (rs2.value() & 0x1f);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fmovse  }<i>i_or_x_cc</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code fmovse        %icc, %f0, %f0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.33"
     */
    // Template#: 421, Serial#: 421
    public void fmovse(final ICCOperand i_or_x_cc, final SFPR rs2, final SFPR rd) {
        int instruction = 0x81A86020;
        instruction |= ((i_or_x_cc.value() & 0x3) << 11);
        instruction |= (rs2.value() & 0x1f);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fmovde  }<i>i_or_x_cc</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code fmovde        %icc, %f0, %f0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.33"
     */
    // Template#: 422, Serial#: 422
    public void fmovde(final ICCOperand i_or_x_cc, final DFPR rs2, final DFPR rd) {
        int instruction = 0x81A86040;
        instruction |= ((i_or_x_cc.value() & 0x3) << 11);
        instruction |= (((rs2.value() >>> 1) & 0xf) << 1) | ((rs2.value() >>> 5) & 0x1);
        instruction |= (((rd.value() >>> 1) & 0xf) << 26) | (((rd.value() >>> 5) & 0x1) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fmovqe  }<i>i_or_x_cc</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code fmovqe        %icc, %f0, %f0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.33"
     */
    // Template#: 423, Serial#: 423
    public void fmovqe(final ICCOperand i_or_x_cc, final QFPR rs2, final QFPR rd) {
        int instruction = 0x81A86060;
        instruction |= ((i_or_x_cc.value() & 0x3) << 11);
        instruction |= (((rs2.value() >>> 2) & 0x7) << 2) | ((rs2.value() >>> 5) & 0x1);
        instruction |= (((rd.value() >>> 2) & 0x7) << 27) | (((rd.value() >>> 5) & 0x1) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code move  }<i>i_or_x_cc</i>, <i>simm11</i>, <i>rd</i>
     * Example disassembly syntax: {@code move          %icc, -1024, %g0}
     * <p>
     * Constraint: {@code -1024 <= simm11 && simm11 <= 1023}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.35"
     */
    // Template#: 424, Serial#: 424
    public void move(final ICCOperand i_or_x_cc, final int simm11, final GPR rd) {
        int instruction = 0x81646000;
        checkConstraint(-1024 <= simm11 && simm11 <= 1023, "-1024 <= simm11 && simm11 <= 1023");
        instruction |= ((i_or_x_cc.value() & 0x3) << 11);
        instruction |= (simm11 & 0x7ff);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code move  }<i>i_or_x_cc</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code move          %icc, %g0, %g0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.35"
     */
    // Template#: 425, Serial#: 425
    public void move(final ICCOperand i_or_x_cc, final GPR rs2, final GPR rd) {
        int instruction = 0x81644000;
        instruction |= ((i_or_x_cc.value() & 0x3) << 11);
        instruction |= (rs2.value() & 0x1f);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fmovsg  }<i>i_or_x_cc</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code fmovsg        %icc, %f0, %f0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.33"
     */
    // Template#: 426, Serial#: 426
    public void fmovsg(final ICCOperand i_or_x_cc, final SFPR rs2, final SFPR rd) {
        int instruction = 0x81AAA020;
        instruction |= ((i_or_x_cc.value() & 0x3) << 11);
        instruction |= (rs2.value() & 0x1f);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fmovdg  }<i>i_or_x_cc</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code fmovdg        %icc, %f0, %f0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.33"
     */
    // Template#: 427, Serial#: 427
    public void fmovdg(final ICCOperand i_or_x_cc, final DFPR rs2, final DFPR rd) {
        int instruction = 0x81AAA040;
        instruction |= ((i_or_x_cc.value() & 0x3) << 11);
        instruction |= (((rs2.value() >>> 1) & 0xf) << 1) | ((rs2.value() >>> 5) & 0x1);
        instruction |= (((rd.value() >>> 1) & 0xf) << 26) | (((rd.value() >>> 5) & 0x1) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fmovqg  }<i>i_or_x_cc</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code fmovqg        %icc, %f0, %f0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.33"
     */
    // Template#: 428, Serial#: 428
    public void fmovqg(final ICCOperand i_or_x_cc, final QFPR rs2, final QFPR rd) {
        int instruction = 0x81AAA060;
        instruction |= ((i_or_x_cc.value() & 0x3) << 11);
        instruction |= (((rs2.value() >>> 2) & 0x7) << 2) | ((rs2.value() >>> 5) & 0x1);
        instruction |= (((rd.value() >>> 2) & 0x7) << 27) | (((rd.value() >>> 5) & 0x1) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code movg  }<i>i_or_x_cc</i>, <i>simm11</i>, <i>rd</i>
     * Example disassembly syntax: {@code movg          %icc, -1024, %g0}
     * <p>
     * Constraint: {@code -1024 <= simm11 && simm11 <= 1023}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.35"
     */
    // Template#: 429, Serial#: 429
    public void movg(final ICCOperand i_or_x_cc, final int simm11, final GPR rd) {
        int instruction = 0x8166A000;
        checkConstraint(-1024 <= simm11 && simm11 <= 1023, "-1024 <= simm11 && simm11 <= 1023");
        instruction |= ((i_or_x_cc.value() & 0x3) << 11);
        instruction |= (simm11 & 0x7ff);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code movg  }<i>i_or_x_cc</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code movg          %icc, %g0, %g0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.35"
     */
    // Template#: 430, Serial#: 430
    public void movg(final ICCOperand i_or_x_cc, final GPR rs2, final GPR rd) {
        int instruction = 0x81668000;
        instruction |= ((i_or_x_cc.value() & 0x3) << 11);
        instruction |= (rs2.value() & 0x1f);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fmovsle  }<i>i_or_x_cc</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code fmovsle       %icc, %f0, %f0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.33"
     */
    // Template#: 431, Serial#: 431
    public void fmovsle(final ICCOperand i_or_x_cc, final SFPR rs2, final SFPR rd) {
        int instruction = 0x81A8A020;
        instruction |= ((i_or_x_cc.value() & 0x3) << 11);
        instruction |= (rs2.value() & 0x1f);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fmovdle  }<i>i_or_x_cc</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code fmovdle       %icc, %f0, %f0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.33"
     */
    // Template#: 432, Serial#: 432
    public void fmovdle(final ICCOperand i_or_x_cc, final DFPR rs2, final DFPR rd) {
        int instruction = 0x81A8A040;
        instruction |= ((i_or_x_cc.value() & 0x3) << 11);
        instruction |= (((rs2.value() >>> 1) & 0xf) << 1) | ((rs2.value() >>> 5) & 0x1);
        instruction |= (((rd.value() >>> 1) & 0xf) << 26) | (((rd.value() >>> 5) & 0x1) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fmovqle  }<i>i_or_x_cc</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code fmovqle       %icc, %f0, %f0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.33"
     */
    // Template#: 433, Serial#: 433
    public void fmovqle(final ICCOperand i_or_x_cc, final QFPR rs2, final QFPR rd) {
        int instruction = 0x81A8A060;
        instruction |= ((i_or_x_cc.value() & 0x3) << 11);
        instruction |= (((rs2.value() >>> 2) & 0x7) << 2) | ((rs2.value() >>> 5) & 0x1);
        instruction |= (((rd.value() >>> 2) & 0x7) << 27) | (((rd.value() >>> 5) & 0x1) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code movle  }<i>i_or_x_cc</i>, <i>simm11</i>, <i>rd</i>
     * Example disassembly syntax: {@code movle         %icc, -1024, %g0}
     * <p>
     * Constraint: {@code -1024 <= simm11 && simm11 <= 1023}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.35"
     */
    // Template#: 434, Serial#: 434
    public void movle(final ICCOperand i_or_x_cc, final int simm11, final GPR rd) {
        int instruction = 0x8164A000;
        checkConstraint(-1024 <= simm11 && simm11 <= 1023, "-1024 <= simm11 && simm11 <= 1023");
        instruction |= ((i_or_x_cc.value() & 0x3) << 11);
        instruction |= (simm11 & 0x7ff);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code movle  }<i>i_or_x_cc</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code movle         %icc, %g0, %g0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.35"
     */
    // Template#: 435, Serial#: 435
    public void movle(final ICCOperand i_or_x_cc, final GPR rs2, final GPR rd) {
        int instruction = 0x81648000;
        instruction |= ((i_or_x_cc.value() & 0x3) << 11);
        instruction |= (rs2.value() & 0x1f);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fmovsge  }<i>i_or_x_cc</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code fmovsge       %icc, %f0, %f0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.33"
     */
    // Template#: 436, Serial#: 436
    public void fmovsge(final ICCOperand i_or_x_cc, final SFPR rs2, final SFPR rd) {
        int instruction = 0x81AAE020;
        instruction |= ((i_or_x_cc.value() & 0x3) << 11);
        instruction |= (rs2.value() & 0x1f);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fmovdge  }<i>i_or_x_cc</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code fmovdge       %icc, %f0, %f0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.33"
     */
    // Template#: 437, Serial#: 437
    public void fmovdge(final ICCOperand i_or_x_cc, final DFPR rs2, final DFPR rd) {
        int instruction = 0x81AAE040;
        instruction |= ((i_or_x_cc.value() & 0x3) << 11);
        instruction |= (((rs2.value() >>> 1) & 0xf) << 1) | ((rs2.value() >>> 5) & 0x1);
        instruction |= (((rd.value() >>> 1) & 0xf) << 26) | (((rd.value() >>> 5) & 0x1) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fmovqge  }<i>i_or_x_cc</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code fmovqge       %icc, %f0, %f0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.33"
     */
    // Template#: 438, Serial#: 438
    public void fmovqge(final ICCOperand i_or_x_cc, final QFPR rs2, final QFPR rd) {
        int instruction = 0x81AAE060;
        instruction |= ((i_or_x_cc.value() & 0x3) << 11);
        instruction |= (((rs2.value() >>> 2) & 0x7) << 2) | ((rs2.value() >>> 5) & 0x1);
        instruction |= (((rd.value() >>> 2) & 0x7) << 27) | (((rd.value() >>> 5) & 0x1) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code movge  }<i>i_or_x_cc</i>, <i>simm11</i>, <i>rd</i>
     * Example disassembly syntax: {@code movge         %icc, -1024, %g0}
     * <p>
     * Constraint: {@code -1024 <= simm11 && simm11 <= 1023}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.35"
     */
    // Template#: 439, Serial#: 439
    public void movge(final ICCOperand i_or_x_cc, final int simm11, final GPR rd) {
        int instruction = 0x8166E000;
        checkConstraint(-1024 <= simm11 && simm11 <= 1023, "-1024 <= simm11 && simm11 <= 1023");
        instruction |= ((i_or_x_cc.value() & 0x3) << 11);
        instruction |= (simm11 & 0x7ff);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code movge  }<i>i_or_x_cc</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code movge         %icc, %g0, %g0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.35"
     */
    // Template#: 440, Serial#: 440
    public void movge(final ICCOperand i_or_x_cc, final GPR rs2, final GPR rd) {
        int instruction = 0x8166C000;
        instruction |= ((i_or_x_cc.value() & 0x3) << 11);
        instruction |= (rs2.value() & 0x1f);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fmovsl  }<i>i_or_x_cc</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code fmovsl        %icc, %f0, %f0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.33"
     */
    // Template#: 441, Serial#: 441
    public void fmovsl(final ICCOperand i_or_x_cc, final SFPR rs2, final SFPR rd) {
        int instruction = 0x81A8E020;
        instruction |= ((i_or_x_cc.value() & 0x3) << 11);
        instruction |= (rs2.value() & 0x1f);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fmovdl  }<i>i_or_x_cc</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code fmovdl        %icc, %f0, %f0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.33"
     */
    // Template#: 442, Serial#: 442
    public void fmovdl(final ICCOperand i_or_x_cc, final DFPR rs2, final DFPR rd) {
        int instruction = 0x81A8E040;
        instruction |= ((i_or_x_cc.value() & 0x3) << 11);
        instruction |= (((rs2.value() >>> 1) & 0xf) << 1) | ((rs2.value() >>> 5) & 0x1);
        instruction |= (((rd.value() >>> 1) & 0xf) << 26) | (((rd.value() >>> 5) & 0x1) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fmovql  }<i>i_or_x_cc</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code fmovql        %icc, %f0, %f0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.33"
     */
    // Template#: 443, Serial#: 443
    public void fmovql(final ICCOperand i_or_x_cc, final QFPR rs2, final QFPR rd) {
        int instruction = 0x81A8E060;
        instruction |= ((i_or_x_cc.value() & 0x3) << 11);
        instruction |= (((rs2.value() >>> 2) & 0x7) << 2) | ((rs2.value() >>> 5) & 0x1);
        instruction |= (((rd.value() >>> 2) & 0x7) << 27) | (((rd.value() >>> 5) & 0x1) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code movl  }<i>i_or_x_cc</i>, <i>simm11</i>, <i>rd</i>
     * Example disassembly syntax: {@code movl          %icc, -1024, %g0}
     * <p>
     * Constraint: {@code -1024 <= simm11 && simm11 <= 1023}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.35"
     */
    // Template#: 444, Serial#: 444
    public void movl(final ICCOperand i_or_x_cc, final int simm11, final GPR rd) {
        int instruction = 0x8164E000;
        checkConstraint(-1024 <= simm11 && simm11 <= 1023, "-1024 <= simm11 && simm11 <= 1023");
        instruction |= ((i_or_x_cc.value() & 0x3) << 11);
        instruction |= (simm11 & 0x7ff);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code movl  }<i>i_or_x_cc</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code movl          %icc, %g0, %g0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.35"
     */
    // Template#: 445, Serial#: 445
    public void movl(final ICCOperand i_or_x_cc, final GPR rs2, final GPR rd) {
        int instruction = 0x8164C000;
        instruction |= ((i_or_x_cc.value() & 0x3) << 11);
        instruction |= (rs2.value() & 0x1f);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fmovsgu  }<i>i_or_x_cc</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code fmovsgu       %icc, %f0, %f0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.33"
     */
    // Template#: 446, Serial#: 446
    public void fmovsgu(final ICCOperand i_or_x_cc, final SFPR rs2, final SFPR rd) {
        int instruction = 0x81AB2020;
        instruction |= ((i_or_x_cc.value() & 0x3) << 11);
        instruction |= (rs2.value() & 0x1f);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fmovdgu  }<i>i_or_x_cc</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code fmovdgu       %icc, %f0, %f0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.33"
     */
    // Template#: 447, Serial#: 447
    public void fmovdgu(final ICCOperand i_or_x_cc, final DFPR rs2, final DFPR rd) {
        int instruction = 0x81AB2040;
        instruction |= ((i_or_x_cc.value() & 0x3) << 11);
        instruction |= (((rs2.value() >>> 1) & 0xf) << 1) | ((rs2.value() >>> 5) & 0x1);
        instruction |= (((rd.value() >>> 1) & 0xf) << 26) | (((rd.value() >>> 5) & 0x1) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fmovqgu  }<i>i_or_x_cc</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code fmovqgu       %icc, %f0, %f0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.33"
     */
    // Template#: 448, Serial#: 448
    public void fmovqgu(final ICCOperand i_or_x_cc, final QFPR rs2, final QFPR rd) {
        int instruction = 0x81AB2060;
        instruction |= ((i_or_x_cc.value() & 0x3) << 11);
        instruction |= (((rs2.value() >>> 2) & 0x7) << 2) | ((rs2.value() >>> 5) & 0x1);
        instruction |= (((rd.value() >>> 2) & 0x7) << 27) | (((rd.value() >>> 5) & 0x1) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code movgu  }<i>i_or_x_cc</i>, <i>simm11</i>, <i>rd</i>
     * Example disassembly syntax: {@code movgu         %icc, -1024, %g0}
     * <p>
     * Constraint: {@code -1024 <= simm11 && simm11 <= 1023}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.35"
     */
    // Template#: 449, Serial#: 449
    public void movgu(final ICCOperand i_or_x_cc, final int simm11, final GPR rd) {
        int instruction = 0x81672000;
        checkConstraint(-1024 <= simm11 && simm11 <= 1023, "-1024 <= simm11 && simm11 <= 1023");
        instruction |= ((i_or_x_cc.value() & 0x3) << 11);
        instruction |= (simm11 & 0x7ff);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code movgu  }<i>i_or_x_cc</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code movgu         %icc, %g0, %g0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.35"
     */
    // Template#: 450, Serial#: 450
    public void movgu(final ICCOperand i_or_x_cc, final GPR rs2, final GPR rd) {
        int instruction = 0x81670000;
        instruction |= ((i_or_x_cc.value() & 0x3) << 11);
        instruction |= (rs2.value() & 0x1f);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fmovsleu  }<i>i_or_x_cc</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code fmovsleu      %icc, %f0, %f0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.33"
     */
    // Template#: 451, Serial#: 451
    public void fmovsleu(final ICCOperand i_or_x_cc, final SFPR rs2, final SFPR rd) {
        int instruction = 0x81A92020;
        instruction |= ((i_or_x_cc.value() & 0x3) << 11);
        instruction |= (rs2.value() & 0x1f);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fmovdleu  }<i>i_or_x_cc</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code fmovdleu      %icc, %f0, %f0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.33"
     */
    // Template#: 452, Serial#: 452
    public void fmovdleu(final ICCOperand i_or_x_cc, final DFPR rs2, final DFPR rd) {
        int instruction = 0x81A92040;
        instruction |= ((i_or_x_cc.value() & 0x3) << 11);
        instruction |= (((rs2.value() >>> 1) & 0xf) << 1) | ((rs2.value() >>> 5) & 0x1);
        instruction |= (((rd.value() >>> 1) & 0xf) << 26) | (((rd.value() >>> 5) & 0x1) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fmovqleu  }<i>i_or_x_cc</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code fmovqleu      %icc, %f0, %f0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.33"
     */
    // Template#: 453, Serial#: 453
    public void fmovqleu(final ICCOperand i_or_x_cc, final QFPR rs2, final QFPR rd) {
        int instruction = 0x81A92060;
        instruction |= ((i_or_x_cc.value() & 0x3) << 11);
        instruction |= (((rs2.value() >>> 2) & 0x7) << 2) | ((rs2.value() >>> 5) & 0x1);
        instruction |= (((rd.value() >>> 2) & 0x7) << 27) | (((rd.value() >>> 5) & 0x1) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code movleu  }<i>i_or_x_cc</i>, <i>simm11</i>, <i>rd</i>
     * Example disassembly syntax: {@code movleu        %icc, -1024, %g0}
     * <p>
     * Constraint: {@code -1024 <= simm11 && simm11 <= 1023}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.35"
     */
    // Template#: 454, Serial#: 454
    public void movleu(final ICCOperand i_or_x_cc, final int simm11, final GPR rd) {
        int instruction = 0x81652000;
        checkConstraint(-1024 <= simm11 && simm11 <= 1023, "-1024 <= simm11 && simm11 <= 1023");
        instruction |= ((i_or_x_cc.value() & 0x3) << 11);
        instruction |= (simm11 & 0x7ff);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code movleu  }<i>i_or_x_cc</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code movleu        %icc, %g0, %g0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.35"
     */
    // Template#: 455, Serial#: 455
    public void movleu(final ICCOperand i_or_x_cc, final GPR rs2, final GPR rd) {
        int instruction = 0x81650000;
        instruction |= ((i_or_x_cc.value() & 0x3) << 11);
        instruction |= (rs2.value() & 0x1f);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fmovscc  }<i>i_or_x_cc</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code fmovscc       %icc, %f0, %f0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.33"
     */
    // Template#: 456, Serial#: 456
    public void fmovscc(final ICCOperand i_or_x_cc, final SFPR rs2, final SFPR rd) {
        int instruction = 0x81AB6020;
        instruction |= ((i_or_x_cc.value() & 0x3) << 11);
        instruction |= (rs2.value() & 0x1f);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fmovdcc  }<i>i_or_x_cc</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code fmovdcc       %icc, %f0, %f0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.33"
     */
    // Template#: 457, Serial#: 457
    public void fmovdcc(final ICCOperand i_or_x_cc, final DFPR rs2, final DFPR rd) {
        int instruction = 0x81AB6040;
        instruction |= ((i_or_x_cc.value() & 0x3) << 11);
        instruction |= (((rs2.value() >>> 1) & 0xf) << 1) | ((rs2.value() >>> 5) & 0x1);
        instruction |= (((rd.value() >>> 1) & 0xf) << 26) | (((rd.value() >>> 5) & 0x1) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fmovqcc  }<i>i_or_x_cc</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code fmovqcc       %icc, %f0, %f0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.33"
     */
    // Template#: 458, Serial#: 458
    public void fmovqcc(final ICCOperand i_or_x_cc, final QFPR rs2, final QFPR rd) {
        int instruction = 0x81AB6060;
        instruction |= ((i_or_x_cc.value() & 0x3) << 11);
        instruction |= (((rs2.value() >>> 2) & 0x7) << 2) | ((rs2.value() >>> 5) & 0x1);
        instruction |= (((rd.value() >>> 2) & 0x7) << 27) | (((rd.value() >>> 5) & 0x1) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code movcc  }<i>i_or_x_cc</i>, <i>simm11</i>, <i>rd</i>
     * Example disassembly syntax: {@code movcc         %icc, -1024, %g0}
     * <p>
     * Constraint: {@code -1024 <= simm11 && simm11 <= 1023}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.35"
     */
    // Template#: 459, Serial#: 459
    public void movcc(final ICCOperand i_or_x_cc, final int simm11, final GPR rd) {
        int instruction = 0x81676000;
        checkConstraint(-1024 <= simm11 && simm11 <= 1023, "-1024 <= simm11 && simm11 <= 1023");
        instruction |= ((i_or_x_cc.value() & 0x3) << 11);
        instruction |= (simm11 & 0x7ff);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code movcc  }<i>i_or_x_cc</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code movcc         %icc, %g0, %g0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.35"
     */
    // Template#: 460, Serial#: 460
    public void movcc(final ICCOperand i_or_x_cc, final GPR rs2, final GPR rd) {
        int instruction = 0x81674000;
        instruction |= ((i_or_x_cc.value() & 0x3) << 11);
        instruction |= (rs2.value() & 0x1f);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fmovscs  }<i>i_or_x_cc</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code fmovscs       %icc, %f0, %f0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.33"
     */
    // Template#: 461, Serial#: 461
    public void fmovscs(final ICCOperand i_or_x_cc, final SFPR rs2, final SFPR rd) {
        int instruction = 0x81A96020;
        instruction |= ((i_or_x_cc.value() & 0x3) << 11);
        instruction |= (rs2.value() & 0x1f);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fmovdcs  }<i>i_or_x_cc</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code fmovdcs       %icc, %f0, %f0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.33"
     */
    // Template#: 462, Serial#: 462
    public void fmovdcs(final ICCOperand i_or_x_cc, final DFPR rs2, final DFPR rd) {
        int instruction = 0x81A96040;
        instruction |= ((i_or_x_cc.value() & 0x3) << 11);
        instruction |= (((rs2.value() >>> 1) & 0xf) << 1) | ((rs2.value() >>> 5) & 0x1);
        instruction |= (((rd.value() >>> 1) & 0xf) << 26) | (((rd.value() >>> 5) & 0x1) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fmovqcs  }<i>i_or_x_cc</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code fmovqcs       %icc, %f0, %f0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.33"
     */
    // Template#: 463, Serial#: 463
    public void fmovqcs(final ICCOperand i_or_x_cc, final QFPR rs2, final QFPR rd) {
        int instruction = 0x81A96060;
        instruction |= ((i_or_x_cc.value() & 0x3) << 11);
        instruction |= (((rs2.value() >>> 2) & 0x7) << 2) | ((rs2.value() >>> 5) & 0x1);
        instruction |= (((rd.value() >>> 2) & 0x7) << 27) | (((rd.value() >>> 5) & 0x1) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code movcs  }<i>i_or_x_cc</i>, <i>simm11</i>, <i>rd</i>
     * Example disassembly syntax: {@code movcs         %icc, -1024, %g0}
     * <p>
     * Constraint: {@code -1024 <= simm11 && simm11 <= 1023}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.35"
     */
    // Template#: 464, Serial#: 464
    public void movcs(final ICCOperand i_or_x_cc, final int simm11, final GPR rd) {
        int instruction = 0x81656000;
        checkConstraint(-1024 <= simm11 && simm11 <= 1023, "-1024 <= simm11 && simm11 <= 1023");
        instruction |= ((i_or_x_cc.value() & 0x3) << 11);
        instruction |= (simm11 & 0x7ff);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code movcs  }<i>i_or_x_cc</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code movcs         %icc, %g0, %g0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.35"
     */
    // Template#: 465, Serial#: 465
    public void movcs(final ICCOperand i_or_x_cc, final GPR rs2, final GPR rd) {
        int instruction = 0x81654000;
        instruction |= ((i_or_x_cc.value() & 0x3) << 11);
        instruction |= (rs2.value() & 0x1f);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fmovspos  }<i>i_or_x_cc</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code fmovspos      %icc, %f0, %f0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.33"
     */
    // Template#: 466, Serial#: 466
    public void fmovspos(final ICCOperand i_or_x_cc, final SFPR rs2, final SFPR rd) {
        int instruction = 0x81ABA020;
        instruction |= ((i_or_x_cc.value() & 0x3) << 11);
        instruction |= (rs2.value() & 0x1f);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fmovdpos  }<i>i_or_x_cc</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code fmovdpos      %icc, %f0, %f0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.33"
     */
    // Template#: 467, Serial#: 467
    public void fmovdpos(final ICCOperand i_or_x_cc, final DFPR rs2, final DFPR rd) {
        int instruction = 0x81ABA040;
        instruction |= ((i_or_x_cc.value() & 0x3) << 11);
        instruction |= (((rs2.value() >>> 1) & 0xf) << 1) | ((rs2.value() >>> 5) & 0x1);
        instruction |= (((rd.value() >>> 1) & 0xf) << 26) | (((rd.value() >>> 5) & 0x1) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fmovqpos  }<i>i_or_x_cc</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code fmovqpos      %icc, %f0, %f0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.33"
     */
    // Template#: 468, Serial#: 468
    public void fmovqpos(final ICCOperand i_or_x_cc, final QFPR rs2, final QFPR rd) {
        int instruction = 0x81ABA060;
        instruction |= ((i_or_x_cc.value() & 0x3) << 11);
        instruction |= (((rs2.value() >>> 2) & 0x7) << 2) | ((rs2.value() >>> 5) & 0x1);
        instruction |= (((rd.value() >>> 2) & 0x7) << 27) | (((rd.value() >>> 5) & 0x1) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code movpos  }<i>i_or_x_cc</i>, <i>simm11</i>, <i>rd</i>
     * Example disassembly syntax: {@code movpos        %icc, -1024, %g0}
     * <p>
     * Constraint: {@code -1024 <= simm11 && simm11 <= 1023}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.35"
     */
    // Template#: 469, Serial#: 469
    public void movpos(final ICCOperand i_or_x_cc, final int simm11, final GPR rd) {
        int instruction = 0x8167A000;
        checkConstraint(-1024 <= simm11 && simm11 <= 1023, "-1024 <= simm11 && simm11 <= 1023");
        instruction |= ((i_or_x_cc.value() & 0x3) << 11);
        instruction |= (simm11 & 0x7ff);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code movpos  }<i>i_or_x_cc</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code movpos        %icc, %g0, %g0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.35"
     */
    // Template#: 470, Serial#: 470
    public void movpos(final ICCOperand i_or_x_cc, final GPR rs2, final GPR rd) {
        int instruction = 0x81678000;
        instruction |= ((i_or_x_cc.value() & 0x3) << 11);
        instruction |= (rs2.value() & 0x1f);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fmovsneg  }<i>i_or_x_cc</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code fmovsneg      %icc, %f0, %f0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.33"
     */
    // Template#: 471, Serial#: 471
    public void fmovsneg(final ICCOperand i_or_x_cc, final SFPR rs2, final SFPR rd) {
        int instruction = 0x81A9A020;
        instruction |= ((i_or_x_cc.value() & 0x3) << 11);
        instruction |= (rs2.value() & 0x1f);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fmovdneg  }<i>i_or_x_cc</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code fmovdneg      %icc, %f0, %f0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.33"
     */
    // Template#: 472, Serial#: 472
    public void fmovdneg(final ICCOperand i_or_x_cc, final DFPR rs2, final DFPR rd) {
        int instruction = 0x81A9A040;
        instruction |= ((i_or_x_cc.value() & 0x3) << 11);
        instruction |= (((rs2.value() >>> 1) & 0xf) << 1) | ((rs2.value() >>> 5) & 0x1);
        instruction |= (((rd.value() >>> 1) & 0xf) << 26) | (((rd.value() >>> 5) & 0x1) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fmovqneg  }<i>i_or_x_cc</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code fmovqneg      %icc, %f0, %f0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.33"
     */
    // Template#: 473, Serial#: 473
    public void fmovqneg(final ICCOperand i_or_x_cc, final QFPR rs2, final QFPR rd) {
        int instruction = 0x81A9A060;
        instruction |= ((i_or_x_cc.value() & 0x3) << 11);
        instruction |= (((rs2.value() >>> 2) & 0x7) << 2) | ((rs2.value() >>> 5) & 0x1);
        instruction |= (((rd.value() >>> 2) & 0x7) << 27) | (((rd.value() >>> 5) & 0x1) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code movneg  }<i>i_or_x_cc</i>, <i>simm11</i>, <i>rd</i>
     * Example disassembly syntax: {@code movneg        %icc, -1024, %g0}
     * <p>
     * Constraint: {@code -1024 <= simm11 && simm11 <= 1023}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.35"
     */
    // Template#: 474, Serial#: 474
    public void movneg(final ICCOperand i_or_x_cc, final int simm11, final GPR rd) {
        int instruction = 0x8165A000;
        checkConstraint(-1024 <= simm11 && simm11 <= 1023, "-1024 <= simm11 && simm11 <= 1023");
        instruction |= ((i_or_x_cc.value() & 0x3) << 11);
        instruction |= (simm11 & 0x7ff);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code movneg  }<i>i_or_x_cc</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code movneg        %icc, %g0, %g0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.35"
     */
    // Template#: 475, Serial#: 475
    public void movneg(final ICCOperand i_or_x_cc, final GPR rs2, final GPR rd) {
        int instruction = 0x81658000;
        instruction |= ((i_or_x_cc.value() & 0x3) << 11);
        instruction |= (rs2.value() & 0x1f);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fmovsvc  }<i>i_or_x_cc</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code fmovsvc       %icc, %f0, %f0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.33"
     */
    // Template#: 476, Serial#: 476
    public void fmovsvc(final ICCOperand i_or_x_cc, final SFPR rs2, final SFPR rd) {
        int instruction = 0x81ABE020;
        instruction |= ((i_or_x_cc.value() & 0x3) << 11);
        instruction |= (rs2.value() & 0x1f);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fmovdvc  }<i>i_or_x_cc</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code fmovdvc       %icc, %f0, %f0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.33"
     */
    // Template#: 477, Serial#: 477
    public void fmovdvc(final ICCOperand i_or_x_cc, final DFPR rs2, final DFPR rd) {
        int instruction = 0x81ABE040;
        instruction |= ((i_or_x_cc.value() & 0x3) << 11);
        instruction |= (((rs2.value() >>> 1) & 0xf) << 1) | ((rs2.value() >>> 5) & 0x1);
        instruction |= (((rd.value() >>> 1) & 0xf) << 26) | (((rd.value() >>> 5) & 0x1) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fmovqvc  }<i>i_or_x_cc</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code fmovqvc       %icc, %f0, %f0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.33"
     */
    // Template#: 478, Serial#: 478
    public void fmovqvc(final ICCOperand i_or_x_cc, final QFPR rs2, final QFPR rd) {
        int instruction = 0x81ABE060;
        instruction |= ((i_or_x_cc.value() & 0x3) << 11);
        instruction |= (((rs2.value() >>> 2) & 0x7) << 2) | ((rs2.value() >>> 5) & 0x1);
        instruction |= (((rd.value() >>> 2) & 0x7) << 27) | (((rd.value() >>> 5) & 0x1) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code movvc  }<i>i_or_x_cc</i>, <i>simm11</i>, <i>rd</i>
     * Example disassembly syntax: {@code movvc         %icc, -1024, %g0}
     * <p>
     * Constraint: {@code -1024 <= simm11 && simm11 <= 1023}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.35"
     */
    // Template#: 479, Serial#: 479
    public void movvc(final ICCOperand i_or_x_cc, final int simm11, final GPR rd) {
        int instruction = 0x8167E000;
        checkConstraint(-1024 <= simm11 && simm11 <= 1023, "-1024 <= simm11 && simm11 <= 1023");
        instruction |= ((i_or_x_cc.value() & 0x3) << 11);
        instruction |= (simm11 & 0x7ff);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code movvc  }<i>i_or_x_cc</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code movvc         %icc, %g0, %g0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.35"
     */
    // Template#: 480, Serial#: 480
    public void movvc(final ICCOperand i_or_x_cc, final GPR rs2, final GPR rd) {
        int instruction = 0x8167C000;
        instruction |= ((i_or_x_cc.value() & 0x3) << 11);
        instruction |= (rs2.value() & 0x1f);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fmovsvs  }<i>i_or_x_cc</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code fmovsvs       %icc, %f0, %f0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.33"
     */
    // Template#: 481, Serial#: 481
    public void fmovsvs(final ICCOperand i_or_x_cc, final SFPR rs2, final SFPR rd) {
        int instruction = 0x81A9E020;
        instruction |= ((i_or_x_cc.value() & 0x3) << 11);
        instruction |= (rs2.value() & 0x1f);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fmovdvs  }<i>i_or_x_cc</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code fmovdvs       %icc, %f0, %f0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.33"
     */
    // Template#: 482, Serial#: 482
    public void fmovdvs(final ICCOperand i_or_x_cc, final DFPR rs2, final DFPR rd) {
        int instruction = 0x81A9E040;
        instruction |= ((i_or_x_cc.value() & 0x3) << 11);
        instruction |= (((rs2.value() >>> 1) & 0xf) << 1) | ((rs2.value() >>> 5) & 0x1);
        instruction |= (((rd.value() >>> 1) & 0xf) << 26) | (((rd.value() >>> 5) & 0x1) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fmovqvs  }<i>i_or_x_cc</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code fmovqvs       %icc, %f0, %f0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.33"
     */
    // Template#: 483, Serial#: 483
    public void fmovqvs(final ICCOperand i_or_x_cc, final QFPR rs2, final QFPR rd) {
        int instruction = 0x81A9E060;
        instruction |= ((i_or_x_cc.value() & 0x3) << 11);
        instruction |= (((rs2.value() >>> 2) & 0x7) << 2) | ((rs2.value() >>> 5) & 0x1);
        instruction |= (((rd.value() >>> 2) & 0x7) << 27) | (((rd.value() >>> 5) & 0x1) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code movvs  }<i>i_or_x_cc</i>, <i>simm11</i>, <i>rd</i>
     * Example disassembly syntax: {@code movvs         %icc, -1024, %g0}
     * <p>
     * Constraint: {@code -1024 <= simm11 && simm11 <= 1023}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.35"
     */
    // Template#: 484, Serial#: 484
    public void movvs(final ICCOperand i_or_x_cc, final int simm11, final GPR rd) {
        int instruction = 0x8165E000;
        checkConstraint(-1024 <= simm11 && simm11 <= 1023, "-1024 <= simm11 && simm11 <= 1023");
        instruction |= ((i_or_x_cc.value() & 0x3) << 11);
        instruction |= (simm11 & 0x7ff);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code movvs  }<i>i_or_x_cc</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code movvs         %icc, %g0, %g0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.35"
     */
    // Template#: 485, Serial#: 485
    public void movvs(final ICCOperand i_or_x_cc, final GPR rs2, final GPR rd) {
        int instruction = 0x8165C000;
        instruction |= ((i_or_x_cc.value() & 0x3) << 11);
        instruction |= (rs2.value() & 0x1f);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fmovsa  }<i>n</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code fmovsa        %fcc0, %f0, %f0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.33"
     */
    // Template#: 486, Serial#: 486
    public void fmovsa(final FCCOperand n, final SFPR rs2, final SFPR rd) {
        int instruction = 0x81AA0020;
        instruction |= ((n.value() & 0x3) << 11);
        instruction |= (rs2.value() & 0x1f);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fmovda  }<i>n</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code fmovda        %fcc0, %f0, %f0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.33"
     */
    // Template#: 487, Serial#: 487
    public void fmovda(final FCCOperand n, final DFPR rs2, final DFPR rd) {
        int instruction = 0x81AA0040;
        instruction |= ((n.value() & 0x3) << 11);
        instruction |= (((rs2.value() >>> 1) & 0xf) << 1) | ((rs2.value() >>> 5) & 0x1);
        instruction |= (((rd.value() >>> 1) & 0xf) << 26) | (((rd.value() >>> 5) & 0x1) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fmovqa  }<i>n</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code fmovqa        %fcc0, %f0, %f0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.33"
     */
    // Template#: 488, Serial#: 488
    public void fmovqa(final FCCOperand n, final QFPR rs2, final QFPR rd) {
        int instruction = 0x81AA0060;
        instruction |= ((n.value() & 0x3) << 11);
        instruction |= (((rs2.value() >>> 2) & 0x7) << 2) | ((rs2.value() >>> 5) & 0x1);
        instruction |= (((rd.value() >>> 2) & 0x7) << 27) | (((rd.value() >>> 5) & 0x1) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code mova  }<i>n</i>, <i>simm11</i>, <i>rd</i>
     * Example disassembly syntax: {@code mova          %fcc0, -1024, %g0}
     * <p>
     * Constraint: {@code -1024 <= simm11 && simm11 <= 1023}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.35"
     */
    // Template#: 489, Serial#: 489
    public void mova(final FCCOperand n, final int simm11, final GPR rd) {
        int instruction = 0x81622000;
        checkConstraint(-1024 <= simm11 && simm11 <= 1023, "-1024 <= simm11 && simm11 <= 1023");
        instruction |= ((n.value() & 0x3) << 11);
        instruction |= (simm11 & 0x7ff);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code mova  }<i>n</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code mova          %fcc0, %g0, %g0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.35"
     */
    // Template#: 490, Serial#: 490
    public void mova(final FCCOperand n, final GPR rs2, final GPR rd) {
        int instruction = 0x81620000;
        instruction |= ((n.value() & 0x3) << 11);
        instruction |= (rs2.value() & 0x1f);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fmovsn  }<i>n</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code fmovsn        %fcc0, %f0, %f0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.33"
     */
    // Template#: 491, Serial#: 491
    public void fmovsn(final FCCOperand n, final SFPR rs2, final SFPR rd) {
        int instruction = 0x81A80020;
        instruction |= ((n.value() & 0x3) << 11);
        instruction |= (rs2.value() & 0x1f);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fmovdn  }<i>n</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code fmovdn        %fcc0, %f0, %f0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.33"
     */
    // Template#: 492, Serial#: 492
    public void fmovdn(final FCCOperand n, final DFPR rs2, final DFPR rd) {
        int instruction = 0x81A80040;
        instruction |= ((n.value() & 0x3) << 11);
        instruction |= (((rs2.value() >>> 1) & 0xf) << 1) | ((rs2.value() >>> 5) & 0x1);
        instruction |= (((rd.value() >>> 1) & 0xf) << 26) | (((rd.value() >>> 5) & 0x1) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fmovqn  }<i>n</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code fmovqn        %fcc0, %f0, %f0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.33"
     */
    // Template#: 493, Serial#: 493
    public void fmovqn(final FCCOperand n, final QFPR rs2, final QFPR rd) {
        int instruction = 0x81A80060;
        instruction |= ((n.value() & 0x3) << 11);
        instruction |= (((rs2.value() >>> 2) & 0x7) << 2) | ((rs2.value() >>> 5) & 0x1);
        instruction |= (((rd.value() >>> 2) & 0x7) << 27) | (((rd.value() >>> 5) & 0x1) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code movn  }<i>n</i>, <i>simm11</i>, <i>rd</i>
     * Example disassembly syntax: {@code movn          %fcc0, -1024, %g0}
     * <p>
     * Constraint: {@code -1024 <= simm11 && simm11 <= 1023}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.35"
     */
    // Template#: 494, Serial#: 494
    public void movn(final FCCOperand n, final int simm11, final GPR rd) {
        int instruction = 0x81602000;
        checkConstraint(-1024 <= simm11 && simm11 <= 1023, "-1024 <= simm11 && simm11 <= 1023");
        instruction |= ((n.value() & 0x3) << 11);
        instruction |= (simm11 & 0x7ff);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code movn  }<i>n</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code movn          %fcc0, %g0, %g0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.35"
     */
    // Template#: 495, Serial#: 495
    public void movn(final FCCOperand n, final GPR rs2, final GPR rd) {
        int instruction = 0x81600000;
        instruction |= ((n.value() & 0x3) << 11);
        instruction |= (rs2.value() & 0x1f);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fmovsu  }<i>n</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code fmovsu        %fcc0, %f0, %f0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.33"
     */
    // Template#: 496, Serial#: 496
    public void fmovsu(final FCCOperand n, final SFPR rs2, final SFPR rd) {
        int instruction = 0x81A9C020;
        instruction |= ((n.value() & 0x3) << 11);
        instruction |= (rs2.value() & 0x1f);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fmovdu  }<i>n</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code fmovdu        %fcc0, %f0, %f0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.33"
     */
    // Template#: 497, Serial#: 497
    public void fmovdu(final FCCOperand n, final DFPR rs2, final DFPR rd) {
        int instruction = 0x81A9C040;
        instruction |= ((n.value() & 0x3) << 11);
        instruction |= (((rs2.value() >>> 1) & 0xf) << 1) | ((rs2.value() >>> 5) & 0x1);
        instruction |= (((rd.value() >>> 1) & 0xf) << 26) | (((rd.value() >>> 5) & 0x1) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fmovqu  }<i>n</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code fmovqu        %fcc0, %f0, %f0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.33"
     */
    // Template#: 498, Serial#: 498
    public void fmovqu(final FCCOperand n, final QFPR rs2, final QFPR rd) {
        int instruction = 0x81A9C060;
        instruction |= ((n.value() & 0x3) << 11);
        instruction |= (((rs2.value() >>> 2) & 0x7) << 2) | ((rs2.value() >>> 5) & 0x1);
        instruction |= (((rd.value() >>> 2) & 0x7) << 27) | (((rd.value() >>> 5) & 0x1) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code movu  }<i>n</i>, <i>simm11</i>, <i>rd</i>
     * Example disassembly syntax: {@code movu          %fcc0, -1024, %g0}
     * <p>
     * Constraint: {@code -1024 <= simm11 && simm11 <= 1023}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.35"
     */
    // Template#: 499, Serial#: 499
    public void movu(final FCCOperand n, final int simm11, final GPR rd) {
        int instruction = 0x8161E000;
        checkConstraint(-1024 <= simm11 && simm11 <= 1023, "-1024 <= simm11 && simm11 <= 1023");
        instruction |= ((n.value() & 0x3) << 11);
        instruction |= (simm11 & 0x7ff);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code movu  }<i>n</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code movu          %fcc0, %g0, %g0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.35"
     */
    // Template#: 500, Serial#: 500
    public void movu(final FCCOperand n, final GPR rs2, final GPR rd) {
        int instruction = 0x8161C000;
        instruction |= ((n.value() & 0x3) << 11);
        instruction |= (rs2.value() & 0x1f);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fmovsg  }<i>n</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code fmovsg        %fcc0, %f0, %f0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.33"
     */
    // Template#: 501, Serial#: 501
    public void fmovsg(final FCCOperand n, final SFPR rs2, final SFPR rd) {
        int instruction = 0x81A98020;
        instruction |= ((n.value() & 0x3) << 11);
        instruction |= (rs2.value() & 0x1f);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fmovdg  }<i>n</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code fmovdg        %fcc0, %f0, %f0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.33"
     */
    // Template#: 502, Serial#: 502
    public void fmovdg(final FCCOperand n, final DFPR rs2, final DFPR rd) {
        int instruction = 0x81A98040;
        instruction |= ((n.value() & 0x3) << 11);
        instruction |= (((rs2.value() >>> 1) & 0xf) << 1) | ((rs2.value() >>> 5) & 0x1);
        instruction |= (((rd.value() >>> 1) & 0xf) << 26) | (((rd.value() >>> 5) & 0x1) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fmovqg  }<i>n</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code fmovqg        %fcc0, %f0, %f0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.33"
     */
    // Template#: 503, Serial#: 503
    public void fmovqg(final FCCOperand n, final QFPR rs2, final QFPR rd) {
        int instruction = 0x81A98060;
        instruction |= ((n.value() & 0x3) << 11);
        instruction |= (((rs2.value() >>> 2) & 0x7) << 2) | ((rs2.value() >>> 5) & 0x1);
        instruction |= (((rd.value() >>> 2) & 0x7) << 27) | (((rd.value() >>> 5) & 0x1) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code movg  }<i>n</i>, <i>simm11</i>, <i>rd</i>
     * Example disassembly syntax: {@code movg          %fcc0, -1024, %g0}
     * <p>
     * Constraint: {@code -1024 <= simm11 && simm11 <= 1023}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.35"
     */
    // Template#: 504, Serial#: 504
    public void movg(final FCCOperand n, final int simm11, final GPR rd) {
        int instruction = 0x8161A000;
        checkConstraint(-1024 <= simm11 && simm11 <= 1023, "-1024 <= simm11 && simm11 <= 1023");
        instruction |= ((n.value() & 0x3) << 11);
        instruction |= (simm11 & 0x7ff);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code movg  }<i>n</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code movg          %fcc0, %g0, %g0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.35"
     */
    // Template#: 505, Serial#: 505
    public void movg(final FCCOperand n, final GPR rs2, final GPR rd) {
        int instruction = 0x81618000;
        instruction |= ((n.value() & 0x3) << 11);
        instruction |= (rs2.value() & 0x1f);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fmovsug  }<i>n</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code fmovsug       %fcc0, %f0, %f0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.33"
     */
    // Template#: 506, Serial#: 506
    public void fmovsug(final FCCOperand n, final SFPR rs2, final SFPR rd) {
        int instruction = 0x81A94020;
        instruction |= ((n.value() & 0x3) << 11);
        instruction |= (rs2.value() & 0x1f);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fmovdug  }<i>n</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code fmovdug       %fcc0, %f0, %f0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.33"
     */
    // Template#: 507, Serial#: 507
    public void fmovdug(final FCCOperand n, final DFPR rs2, final DFPR rd) {
        int instruction = 0x81A94040;
        instruction |= ((n.value() & 0x3) << 11);
        instruction |= (((rs2.value() >>> 1) & 0xf) << 1) | ((rs2.value() >>> 5) & 0x1);
        instruction |= (((rd.value() >>> 1) & 0xf) << 26) | (((rd.value() >>> 5) & 0x1) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fmovqug  }<i>n</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code fmovqug       %fcc0, %f0, %f0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.33"
     */
    // Template#: 508, Serial#: 508
    public void fmovqug(final FCCOperand n, final QFPR rs2, final QFPR rd) {
        int instruction = 0x81A94060;
        instruction |= ((n.value() & 0x3) << 11);
        instruction |= (((rs2.value() >>> 2) & 0x7) << 2) | ((rs2.value() >>> 5) & 0x1);
        instruction |= (((rd.value() >>> 2) & 0x7) << 27) | (((rd.value() >>> 5) & 0x1) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code movug  }<i>n</i>, <i>simm11</i>, <i>rd</i>
     * Example disassembly syntax: {@code movug         %fcc0, -1024, %g0}
     * <p>
     * Constraint: {@code -1024 <= simm11 && simm11 <= 1023}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.35"
     */
    // Template#: 509, Serial#: 509
    public void movug(final FCCOperand n, final int simm11, final GPR rd) {
        int instruction = 0x81616000;
        checkConstraint(-1024 <= simm11 && simm11 <= 1023, "-1024 <= simm11 && simm11 <= 1023");
        instruction |= ((n.value() & 0x3) << 11);
        instruction |= (simm11 & 0x7ff);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code movug  }<i>n</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code movug         %fcc0, %g0, %g0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.35"
     */
    // Template#: 510, Serial#: 510
    public void movug(final FCCOperand n, final GPR rs2, final GPR rd) {
        int instruction = 0x81614000;
        instruction |= ((n.value() & 0x3) << 11);
        instruction |= (rs2.value() & 0x1f);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fmovsl  }<i>n</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code fmovsl        %fcc0, %f0, %f0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.33"
     */
    // Template#: 511, Serial#: 511
    public void fmovsl(final FCCOperand n, final SFPR rs2, final SFPR rd) {
        int instruction = 0x81A90020;
        instruction |= ((n.value() & 0x3) << 11);
        instruction |= (rs2.value() & 0x1f);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fmovdl  }<i>n</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code fmovdl        %fcc0, %f0, %f0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.33"
     */
    // Template#: 512, Serial#: 512
    public void fmovdl(final FCCOperand n, final DFPR rs2, final DFPR rd) {
        int instruction = 0x81A90040;
        instruction |= ((n.value() & 0x3) << 11);
        instruction |= (((rs2.value() >>> 1) & 0xf) << 1) | ((rs2.value() >>> 5) & 0x1);
        instruction |= (((rd.value() >>> 1) & 0xf) << 26) | (((rd.value() >>> 5) & 0x1) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fmovql  }<i>n</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code fmovql        %fcc0, %f0, %f0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.33"
     */
    // Template#: 513, Serial#: 513
    public void fmovql(final FCCOperand n, final QFPR rs2, final QFPR rd) {
        int instruction = 0x81A90060;
        instruction |= ((n.value() & 0x3) << 11);
        instruction |= (((rs2.value() >>> 2) & 0x7) << 2) | ((rs2.value() >>> 5) & 0x1);
        instruction |= (((rd.value() >>> 2) & 0x7) << 27) | (((rd.value() >>> 5) & 0x1) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code movl  }<i>n</i>, <i>simm11</i>, <i>rd</i>
     * Example disassembly syntax: {@code movl          %fcc0, -1024, %g0}
     * <p>
     * Constraint: {@code -1024 <= simm11 && simm11 <= 1023}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.35"
     */
    // Template#: 514, Serial#: 514
    public void movl(final FCCOperand n, final int simm11, final GPR rd) {
        int instruction = 0x81612000;
        checkConstraint(-1024 <= simm11 && simm11 <= 1023, "-1024 <= simm11 && simm11 <= 1023");
        instruction |= ((n.value() & 0x3) << 11);
        instruction |= (simm11 & 0x7ff);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code movl  }<i>n</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code movl          %fcc0, %g0, %g0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.35"
     */
    // Template#: 515, Serial#: 515
    public void movl(final FCCOperand n, final GPR rs2, final GPR rd) {
        int instruction = 0x81610000;
        instruction |= ((n.value() & 0x3) << 11);
        instruction |= (rs2.value() & 0x1f);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fmovsul  }<i>n</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code fmovsul       %fcc0, %f0, %f0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.33"
     */
    // Template#: 516, Serial#: 516
    public void fmovsul(final FCCOperand n, final SFPR rs2, final SFPR rd) {
        int instruction = 0x81A8C020;
        instruction |= ((n.value() & 0x3) << 11);
        instruction |= (rs2.value() & 0x1f);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fmovdul  }<i>n</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code fmovdul       %fcc0, %f0, %f0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.33"
     */
    // Template#: 517, Serial#: 517
    public void fmovdul(final FCCOperand n, final DFPR rs2, final DFPR rd) {
        int instruction = 0x81A8C040;
        instruction |= ((n.value() & 0x3) << 11);
        instruction |= (((rs2.value() >>> 1) & 0xf) << 1) | ((rs2.value() >>> 5) & 0x1);
        instruction |= (((rd.value() >>> 1) & 0xf) << 26) | (((rd.value() >>> 5) & 0x1) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fmovqul  }<i>n</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code fmovqul       %fcc0, %f0, %f0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.33"
     */
    // Template#: 518, Serial#: 518
    public void fmovqul(final FCCOperand n, final QFPR rs2, final QFPR rd) {
        int instruction = 0x81A8C060;
        instruction |= ((n.value() & 0x3) << 11);
        instruction |= (((rs2.value() >>> 2) & 0x7) << 2) | ((rs2.value() >>> 5) & 0x1);
        instruction |= (((rd.value() >>> 2) & 0x7) << 27) | (((rd.value() >>> 5) & 0x1) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code movul  }<i>n</i>, <i>simm11</i>, <i>rd</i>
     * Example disassembly syntax: {@code movul         %fcc0, -1024, %g0}
     * <p>
     * Constraint: {@code -1024 <= simm11 && simm11 <= 1023}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.35"
     */
    // Template#: 519, Serial#: 519
    public void movul(final FCCOperand n, final int simm11, final GPR rd) {
        int instruction = 0x8160E000;
        checkConstraint(-1024 <= simm11 && simm11 <= 1023, "-1024 <= simm11 && simm11 <= 1023");
        instruction |= ((n.value() & 0x3) << 11);
        instruction |= (simm11 & 0x7ff);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code movul  }<i>n</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code movul         %fcc0, %g0, %g0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.35"
     */
    // Template#: 520, Serial#: 520
    public void movul(final FCCOperand n, final GPR rs2, final GPR rd) {
        int instruction = 0x8160C000;
        instruction |= ((n.value() & 0x3) << 11);
        instruction |= (rs2.value() & 0x1f);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fmovslg  }<i>n</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code fmovslg       %fcc0, %f0, %f0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.33"
     */
    // Template#: 521, Serial#: 521
    public void fmovslg(final FCCOperand n, final SFPR rs2, final SFPR rd) {
        int instruction = 0x81A88020;
        instruction |= ((n.value() & 0x3) << 11);
        instruction |= (rs2.value() & 0x1f);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fmovdlg  }<i>n</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code fmovdlg       %fcc0, %f0, %f0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.33"
     */
    // Template#: 522, Serial#: 522
    public void fmovdlg(final FCCOperand n, final DFPR rs2, final DFPR rd) {
        int instruction = 0x81A88040;
        instruction |= ((n.value() & 0x3) << 11);
        instruction |= (((rs2.value() >>> 1) & 0xf) << 1) | ((rs2.value() >>> 5) & 0x1);
        instruction |= (((rd.value() >>> 1) & 0xf) << 26) | (((rd.value() >>> 5) & 0x1) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fmovqlg  }<i>n</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code fmovqlg       %fcc0, %f0, %f0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.33"
     */
    // Template#: 523, Serial#: 523
    public void fmovqlg(final FCCOperand n, final QFPR rs2, final QFPR rd) {
        int instruction = 0x81A88060;
        instruction |= ((n.value() & 0x3) << 11);
        instruction |= (((rs2.value() >>> 2) & 0x7) << 2) | ((rs2.value() >>> 5) & 0x1);
        instruction |= (((rd.value() >>> 2) & 0x7) << 27) | (((rd.value() >>> 5) & 0x1) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code movlg  }<i>n</i>, <i>simm11</i>, <i>rd</i>
     * Example disassembly syntax: {@code movlg         %fcc0, -1024, %g0}
     * <p>
     * Constraint: {@code -1024 <= simm11 && simm11 <= 1023}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.35"
     */
    // Template#: 524, Serial#: 524
    public void movlg(final FCCOperand n, final int simm11, final GPR rd) {
        int instruction = 0x8160A000;
        checkConstraint(-1024 <= simm11 && simm11 <= 1023, "-1024 <= simm11 && simm11 <= 1023");
        instruction |= ((n.value() & 0x3) << 11);
        instruction |= (simm11 & 0x7ff);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code movlg  }<i>n</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code movlg         %fcc0, %g0, %g0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.35"
     */
    // Template#: 525, Serial#: 525
    public void movlg(final FCCOperand n, final GPR rs2, final GPR rd) {
        int instruction = 0x81608000;
        instruction |= ((n.value() & 0x3) << 11);
        instruction |= (rs2.value() & 0x1f);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fmovsne  }<i>n</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code fmovsne       %fcc0, %f0, %f0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.33"
     */
    // Template#: 526, Serial#: 526
    public void fmovsne(final FCCOperand n, final SFPR rs2, final SFPR rd) {
        int instruction = 0x81A84020;
        instruction |= ((n.value() & 0x3) << 11);
        instruction |= (rs2.value() & 0x1f);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fmovdne  }<i>n</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code fmovdne       %fcc0, %f0, %f0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.33"
     */
    // Template#: 527, Serial#: 527
    public void fmovdne(final FCCOperand n, final DFPR rs2, final DFPR rd) {
        int instruction = 0x81A84040;
        instruction |= ((n.value() & 0x3) << 11);
        instruction |= (((rs2.value() >>> 1) & 0xf) << 1) | ((rs2.value() >>> 5) & 0x1);
        instruction |= (((rd.value() >>> 1) & 0xf) << 26) | (((rd.value() >>> 5) & 0x1) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fmovqne  }<i>n</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code fmovqne       %fcc0, %f0, %f0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.33"
     */
    // Template#: 528, Serial#: 528
    public void fmovqne(final FCCOperand n, final QFPR rs2, final QFPR rd) {
        int instruction = 0x81A84060;
        instruction |= ((n.value() & 0x3) << 11);
        instruction |= (((rs2.value() >>> 2) & 0x7) << 2) | ((rs2.value() >>> 5) & 0x1);
        instruction |= (((rd.value() >>> 2) & 0x7) << 27) | (((rd.value() >>> 5) & 0x1) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code movne  }<i>n</i>, <i>simm11</i>, <i>rd</i>
     * Example disassembly syntax: {@code movne         %fcc0, -1024, %g0}
     * <p>
     * Constraint: {@code -1024 <= simm11 && simm11 <= 1023}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.35"
     */
    // Template#: 529, Serial#: 529
    public void movne(final FCCOperand n, final int simm11, final GPR rd) {
        int instruction = 0x81606000;
        checkConstraint(-1024 <= simm11 && simm11 <= 1023, "-1024 <= simm11 && simm11 <= 1023");
        instruction |= ((n.value() & 0x3) << 11);
        instruction |= (simm11 & 0x7ff);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code movne  }<i>n</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code movne         %fcc0, %g0, %g0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.35"
     */
    // Template#: 530, Serial#: 530
    public void movne(final FCCOperand n, final GPR rs2, final GPR rd) {
        int instruction = 0x81604000;
        instruction |= ((n.value() & 0x3) << 11);
        instruction |= (rs2.value() & 0x1f);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fmovse  }<i>n</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code fmovse        %fcc0, %f0, %f0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.33"
     */
    // Template#: 531, Serial#: 531
    public void fmovse(final FCCOperand n, final SFPR rs2, final SFPR rd) {
        int instruction = 0x81AA4020;
        instruction |= ((n.value() & 0x3) << 11);
        instruction |= (rs2.value() & 0x1f);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fmovde  }<i>n</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code fmovde        %fcc0, %f0, %f0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.33"
     */
    // Template#: 532, Serial#: 532
    public void fmovde(final FCCOperand n, final DFPR rs2, final DFPR rd) {
        int instruction = 0x81AA4040;
        instruction |= ((n.value() & 0x3) << 11);
        instruction |= (((rs2.value() >>> 1) & 0xf) << 1) | ((rs2.value() >>> 5) & 0x1);
        instruction |= (((rd.value() >>> 1) & 0xf) << 26) | (((rd.value() >>> 5) & 0x1) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fmovqe  }<i>n</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code fmovqe        %fcc0, %f0, %f0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.33"
     */
    // Template#: 533, Serial#: 533
    public void fmovqe(final FCCOperand n, final QFPR rs2, final QFPR rd) {
        int instruction = 0x81AA4060;
        instruction |= ((n.value() & 0x3) << 11);
        instruction |= (((rs2.value() >>> 2) & 0x7) << 2) | ((rs2.value() >>> 5) & 0x1);
        instruction |= (((rd.value() >>> 2) & 0x7) << 27) | (((rd.value() >>> 5) & 0x1) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code move  }<i>n</i>, <i>simm11</i>, <i>rd</i>
     * Example disassembly syntax: {@code move          %fcc0, -1024, %g0}
     * <p>
     * Constraint: {@code -1024 <= simm11 && simm11 <= 1023}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.35"
     */
    // Template#: 534, Serial#: 534
    public void move(final FCCOperand n, final int simm11, final GPR rd) {
        int instruction = 0x81626000;
        checkConstraint(-1024 <= simm11 && simm11 <= 1023, "-1024 <= simm11 && simm11 <= 1023");
        instruction |= ((n.value() & 0x3) << 11);
        instruction |= (simm11 & 0x7ff);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code move  }<i>n</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code move          %fcc0, %g0, %g0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.35"
     */
    // Template#: 535, Serial#: 535
    public void move(final FCCOperand n, final GPR rs2, final GPR rd) {
        int instruction = 0x81624000;
        instruction |= ((n.value() & 0x3) << 11);
        instruction |= (rs2.value() & 0x1f);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fmovsue  }<i>n</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code fmovsue       %fcc0, %f0, %f0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.33"
     */
    // Template#: 536, Serial#: 536
    public void fmovsue(final FCCOperand n, final SFPR rs2, final SFPR rd) {
        int instruction = 0x81AA8020;
        instruction |= ((n.value() & 0x3) << 11);
        instruction |= (rs2.value() & 0x1f);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fmovdue  }<i>n</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code fmovdue       %fcc0, %f0, %f0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.33"
     */
    // Template#: 537, Serial#: 537
    public void fmovdue(final FCCOperand n, final DFPR rs2, final DFPR rd) {
        int instruction = 0x81AA8040;
        instruction |= ((n.value() & 0x3) << 11);
        instruction |= (((rs2.value() >>> 1) & 0xf) << 1) | ((rs2.value() >>> 5) & 0x1);
        instruction |= (((rd.value() >>> 1) & 0xf) << 26) | (((rd.value() >>> 5) & 0x1) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fmovque  }<i>n</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code fmovque       %fcc0, %f0, %f0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.33"
     */
    // Template#: 538, Serial#: 538
    public void fmovque(final FCCOperand n, final QFPR rs2, final QFPR rd) {
        int instruction = 0x81AA8060;
        instruction |= ((n.value() & 0x3) << 11);
        instruction |= (((rs2.value() >>> 2) & 0x7) << 2) | ((rs2.value() >>> 5) & 0x1);
        instruction |= (((rd.value() >>> 2) & 0x7) << 27) | (((rd.value() >>> 5) & 0x1) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code movue  }<i>n</i>, <i>simm11</i>, <i>rd</i>
     * Example disassembly syntax: {@code movue         %fcc0, -1024, %g0}
     * <p>
     * Constraint: {@code -1024 <= simm11 && simm11 <= 1023}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.35"
     */
    // Template#: 539, Serial#: 539
    public void movue(final FCCOperand n, final int simm11, final GPR rd) {
        int instruction = 0x8162A000;
        checkConstraint(-1024 <= simm11 && simm11 <= 1023, "-1024 <= simm11 && simm11 <= 1023");
        instruction |= ((n.value() & 0x3) << 11);
        instruction |= (simm11 & 0x7ff);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code movue  }<i>n</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code movue         %fcc0, %g0, %g0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.35"
     */
    // Template#: 540, Serial#: 540
    public void movue(final FCCOperand n, final GPR rs2, final GPR rd) {
        int instruction = 0x81628000;
        instruction |= ((n.value() & 0x3) << 11);
        instruction |= (rs2.value() & 0x1f);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fmovsge  }<i>n</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code fmovsge       %fcc0, %f0, %f0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.33"
     */
    // Template#: 541, Serial#: 541
    public void fmovsge(final FCCOperand n, final SFPR rs2, final SFPR rd) {
        int instruction = 0x81AAC020;
        instruction |= ((n.value() & 0x3) << 11);
        instruction |= (rs2.value() & 0x1f);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fmovdge  }<i>n</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code fmovdge       %fcc0, %f0, %f0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.33"
     */
    // Template#: 542, Serial#: 542
    public void fmovdge(final FCCOperand n, final DFPR rs2, final DFPR rd) {
        int instruction = 0x81AAC040;
        instruction |= ((n.value() & 0x3) << 11);
        instruction |= (((rs2.value() >>> 1) & 0xf) << 1) | ((rs2.value() >>> 5) & 0x1);
        instruction |= (((rd.value() >>> 1) & 0xf) << 26) | (((rd.value() >>> 5) & 0x1) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fmovqge  }<i>n</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code fmovqge       %fcc0, %f0, %f0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.33"
     */
    // Template#: 543, Serial#: 543
    public void fmovqge(final FCCOperand n, final QFPR rs2, final QFPR rd) {
        int instruction = 0x81AAC060;
        instruction |= ((n.value() & 0x3) << 11);
        instruction |= (((rs2.value() >>> 2) & 0x7) << 2) | ((rs2.value() >>> 5) & 0x1);
        instruction |= (((rd.value() >>> 2) & 0x7) << 27) | (((rd.value() >>> 5) & 0x1) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code movge  }<i>n</i>, <i>simm11</i>, <i>rd</i>
     * Example disassembly syntax: {@code movge         %fcc0, -1024, %g0}
     * <p>
     * Constraint: {@code -1024 <= simm11 && simm11 <= 1023}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.35"
     */
    // Template#: 544, Serial#: 544
    public void movge(final FCCOperand n, final int simm11, final GPR rd) {
        int instruction = 0x8162E000;
        checkConstraint(-1024 <= simm11 && simm11 <= 1023, "-1024 <= simm11 && simm11 <= 1023");
        instruction |= ((n.value() & 0x3) << 11);
        instruction |= (simm11 & 0x7ff);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code movge  }<i>n</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code movge         %fcc0, %g0, %g0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.35"
     */
    // Template#: 545, Serial#: 545
    public void movge(final FCCOperand n, final GPR rs2, final GPR rd) {
        int instruction = 0x8162C000;
        instruction |= ((n.value() & 0x3) << 11);
        instruction |= (rs2.value() & 0x1f);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fmovsuge  }<i>n</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code fmovsuge      %fcc0, %f0, %f0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.33"
     */
    // Template#: 546, Serial#: 546
    public void fmovsuge(final FCCOperand n, final SFPR rs2, final SFPR rd) {
        int instruction = 0x81AB0020;
        instruction |= ((n.value() & 0x3) << 11);
        instruction |= (rs2.value() & 0x1f);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fmovduge  }<i>n</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code fmovduge      %fcc0, %f0, %f0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.33"
     */
    // Template#: 547, Serial#: 547
    public void fmovduge(final FCCOperand n, final DFPR rs2, final DFPR rd) {
        int instruction = 0x81AB0040;
        instruction |= ((n.value() & 0x3) << 11);
        instruction |= (((rs2.value() >>> 1) & 0xf) << 1) | ((rs2.value() >>> 5) & 0x1);
        instruction |= (((rd.value() >>> 1) & 0xf) << 26) | (((rd.value() >>> 5) & 0x1) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fmovquge  }<i>n</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code fmovquge      %fcc0, %f0, %f0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.33"
     */
    // Template#: 548, Serial#: 548
    public void fmovquge(final FCCOperand n, final QFPR rs2, final QFPR rd) {
        int instruction = 0x81AB0060;
        instruction |= ((n.value() & 0x3) << 11);
        instruction |= (((rs2.value() >>> 2) & 0x7) << 2) | ((rs2.value() >>> 5) & 0x1);
        instruction |= (((rd.value() >>> 2) & 0x7) << 27) | (((rd.value() >>> 5) & 0x1) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code movuge  }<i>n</i>, <i>simm11</i>, <i>rd</i>
     * Example disassembly syntax: {@code movuge        %fcc0, -1024, %g0}
     * <p>
     * Constraint: {@code -1024 <= simm11 && simm11 <= 1023}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.35"
     */
    // Template#: 549, Serial#: 549
    public void movuge(final FCCOperand n, final int simm11, final GPR rd) {
        int instruction = 0x81632000;
        checkConstraint(-1024 <= simm11 && simm11 <= 1023, "-1024 <= simm11 && simm11 <= 1023");
        instruction |= ((n.value() & 0x3) << 11);
        instruction |= (simm11 & 0x7ff);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code movuge  }<i>n</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code movuge        %fcc0, %g0, %g0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.35"
     */
    // Template#: 550, Serial#: 550
    public void movuge(final FCCOperand n, final GPR rs2, final GPR rd) {
        int instruction = 0x81630000;
        instruction |= ((n.value() & 0x3) << 11);
        instruction |= (rs2.value() & 0x1f);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fmovsle  }<i>n</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code fmovsle       %fcc0, %f0, %f0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.33"
     */
    // Template#: 551, Serial#: 551
    public void fmovsle(final FCCOperand n, final SFPR rs2, final SFPR rd) {
        int instruction = 0x81AB4020;
        instruction |= ((n.value() & 0x3) << 11);
        instruction |= (rs2.value() & 0x1f);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fmovdle  }<i>n</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code fmovdle       %fcc0, %f0, %f0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.33"
     */
    // Template#: 552, Serial#: 552
    public void fmovdle(final FCCOperand n, final DFPR rs2, final DFPR rd) {
        int instruction = 0x81AB4040;
        instruction |= ((n.value() & 0x3) << 11);
        instruction |= (((rs2.value() >>> 1) & 0xf) << 1) | ((rs2.value() >>> 5) & 0x1);
        instruction |= (((rd.value() >>> 1) & 0xf) << 26) | (((rd.value() >>> 5) & 0x1) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fmovqle  }<i>n</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code fmovqle       %fcc0, %f0, %f0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.33"
     */
    // Template#: 553, Serial#: 553
    public void fmovqle(final FCCOperand n, final QFPR rs2, final QFPR rd) {
        int instruction = 0x81AB4060;
        instruction |= ((n.value() & 0x3) << 11);
        instruction |= (((rs2.value() >>> 2) & 0x7) << 2) | ((rs2.value() >>> 5) & 0x1);
        instruction |= (((rd.value() >>> 2) & 0x7) << 27) | (((rd.value() >>> 5) & 0x1) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code movle  }<i>n</i>, <i>simm11</i>, <i>rd</i>
     * Example disassembly syntax: {@code movle         %fcc0, -1024, %g0}
     * <p>
     * Constraint: {@code -1024 <= simm11 && simm11 <= 1023}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.35"
     */
    // Template#: 554, Serial#: 554
    public void movle(final FCCOperand n, final int simm11, final GPR rd) {
        int instruction = 0x81636000;
        checkConstraint(-1024 <= simm11 && simm11 <= 1023, "-1024 <= simm11 && simm11 <= 1023");
        instruction |= ((n.value() & 0x3) << 11);
        instruction |= (simm11 & 0x7ff);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code movle  }<i>n</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code movle         %fcc0, %g0, %g0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.35"
     */
    // Template#: 555, Serial#: 555
    public void movle(final FCCOperand n, final GPR rs2, final GPR rd) {
        int instruction = 0x81634000;
        instruction |= ((n.value() & 0x3) << 11);
        instruction |= (rs2.value() & 0x1f);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fmovsule  }<i>n</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code fmovsule      %fcc0, %f0, %f0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.33"
     */
    // Template#: 556, Serial#: 556
    public void fmovsule(final FCCOperand n, final SFPR rs2, final SFPR rd) {
        int instruction = 0x81AB8020;
        instruction |= ((n.value() & 0x3) << 11);
        instruction |= (rs2.value() & 0x1f);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fmovdule  }<i>n</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code fmovdule      %fcc0, %f0, %f0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.33"
     */
    // Template#: 557, Serial#: 557
    public void fmovdule(final FCCOperand n, final DFPR rs2, final DFPR rd) {
        int instruction = 0x81AB8040;
        instruction |= ((n.value() & 0x3) << 11);
        instruction |= (((rs2.value() >>> 1) & 0xf) << 1) | ((rs2.value() >>> 5) & 0x1);
        instruction |= (((rd.value() >>> 1) & 0xf) << 26) | (((rd.value() >>> 5) & 0x1) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fmovqule  }<i>n</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code fmovqule      %fcc0, %f0, %f0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.33"
     */
    // Template#: 558, Serial#: 558
    public void fmovqule(final FCCOperand n, final QFPR rs2, final QFPR rd) {
        int instruction = 0x81AB8060;
        instruction |= ((n.value() & 0x3) << 11);
        instruction |= (((rs2.value() >>> 2) & 0x7) << 2) | ((rs2.value() >>> 5) & 0x1);
        instruction |= (((rd.value() >>> 2) & 0x7) << 27) | (((rd.value() >>> 5) & 0x1) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code movule  }<i>n</i>, <i>simm11</i>, <i>rd</i>
     * Example disassembly syntax: {@code movule        %fcc0, -1024, %g0}
     * <p>
     * Constraint: {@code -1024 <= simm11 && simm11 <= 1023}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.35"
     */
    // Template#: 559, Serial#: 559
    public void movule(final FCCOperand n, final int simm11, final GPR rd) {
        int instruction = 0x8163A000;
        checkConstraint(-1024 <= simm11 && simm11 <= 1023, "-1024 <= simm11 && simm11 <= 1023");
        instruction |= ((n.value() & 0x3) << 11);
        instruction |= (simm11 & 0x7ff);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code movule  }<i>n</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code movule        %fcc0, %g0, %g0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.35"
     */
    // Template#: 560, Serial#: 560
    public void movule(final FCCOperand n, final GPR rs2, final GPR rd) {
        int instruction = 0x81638000;
        instruction |= ((n.value() & 0x3) << 11);
        instruction |= (rs2.value() & 0x1f);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fmovso  }<i>n</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code fmovso        %fcc0, %f0, %f0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.33"
     */
    // Template#: 561, Serial#: 561
    public void fmovso(final FCCOperand n, final SFPR rs2, final SFPR rd) {
        int instruction = 0x81ABC020;
        instruction |= ((n.value() & 0x3) << 11);
        instruction |= (rs2.value() & 0x1f);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fmovdo  }<i>n</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code fmovdo        %fcc0, %f0, %f0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.33"
     */
    // Template#: 562, Serial#: 562
    public void fmovdo(final FCCOperand n, final DFPR rs2, final DFPR rd) {
        int instruction = 0x81ABC040;
        instruction |= ((n.value() & 0x3) << 11);
        instruction |= (((rs2.value() >>> 1) & 0xf) << 1) | ((rs2.value() >>> 5) & 0x1);
        instruction |= (((rd.value() >>> 1) & 0xf) << 26) | (((rd.value() >>> 5) & 0x1) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fmovqo  }<i>n</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code fmovqo        %fcc0, %f0, %f0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.33"
     */
    // Template#: 563, Serial#: 563
    public void fmovqo(final FCCOperand n, final QFPR rs2, final QFPR rd) {
        int instruction = 0x81ABC060;
        instruction |= ((n.value() & 0x3) << 11);
        instruction |= (((rs2.value() >>> 2) & 0x7) << 2) | ((rs2.value() >>> 5) & 0x1);
        instruction |= (((rd.value() >>> 2) & 0x7) << 27) | (((rd.value() >>> 5) & 0x1) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code movo  }<i>n</i>, <i>simm11</i>, <i>rd</i>
     * Example disassembly syntax: {@code movo          %fcc0, -1024, %g0}
     * <p>
     * Constraint: {@code -1024 <= simm11 && simm11 <= 1023}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.35"
     */
    // Template#: 564, Serial#: 564
    public void movo(final FCCOperand n, final int simm11, final GPR rd) {
        int instruction = 0x8163E000;
        checkConstraint(-1024 <= simm11 && simm11 <= 1023, "-1024 <= simm11 && simm11 <= 1023");
        instruction |= ((n.value() & 0x3) << 11);
        instruction |= (simm11 & 0x7ff);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code movo  }<i>n</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code movo          %fcc0, %g0, %g0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.35"
     */
    // Template#: 565, Serial#: 565
    public void movo(final FCCOperand n, final GPR rs2, final GPR rd) {
        int instruction = 0x8163C000;
        instruction |= ((n.value() & 0x3) << 11);
        instruction |= (rs2.value() & 0x1f);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code flushw  }
     * Example disassembly syntax: {@code flushw        }
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.21"
     */
    // Template#: 566, Serial#: 566
    public void flushw() {
        int instruction = 0x81580000;
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code save  }<i>rs1</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code save          %g0, %g0, %g0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.45"
     */
    // Template#: 567, Serial#: 567
    public void save(final GPR rs1, final GPR rs2, final GPR rd) {
        int instruction = 0x81E00000;
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (rs2.value() & 0x1f);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code save  }<i>rs1</i>, <i>simm13</i>, <i>rd</i>
     * Example disassembly syntax: {@code save          %g0, -4096, %g0}
     * <p>
     * Constraint: {@code -4096 <= simm13 && simm13 <= 4095}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.45"
     */
    // Template#: 568, Serial#: 568
    public void save(final GPR rs1, final int simm13, final GPR rd) {
        int instruction = 0x81E02000;
        checkConstraint(-4096 <= simm13 && simm13 <= 4095, "-4096 <= simm13 && simm13 <= 4095");
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (simm13 & 0x1fff);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code restore  }<i>rs1</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code restore       %g0, %g0, %g0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.45"
     */
    // Template#: 569, Serial#: 569
    public void restore(final GPR rs1, final GPR rs2, final GPR rd) {
        int instruction = 0x81E80000;
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (rs2.value() & 0x1f);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code restore  }<i>rs1</i>, <i>simm13</i>, <i>rd</i>
     * Example disassembly syntax: {@code restore       %g0, -4096, %g0}
     * <p>
     * Constraint: {@code -4096 <= simm13 && simm13 <= 4095}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.45"
     */
    // Template#: 570, Serial#: 570
    public void restore(final GPR rs1, final int simm13, final GPR rd) {
        int instruction = 0x81E82000;
        checkConstraint(-4096 <= simm13 && simm13 <= 4095, "-4096 <= simm13 && simm13 <= 4095");
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (simm13 & 0x1fff);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code saved  }
     * Example disassembly syntax: {@code saved         }
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.46"
     */
    // Template#: 571, Serial#: 571
    public void saved() {
        int instruction = 0x81880000;
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code restored  }
     * Example disassembly syntax: {@code restored      }
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.46"
     */
    // Template#: 572, Serial#: 572
    public void restored() {
        int instruction = 0x83880000;
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code rd  }<i>rs1</i>, <i>rd</i>
     * Example disassembly syntax: {@code rd            %y, %g0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.43"
     */
    // Template#: 573, Serial#: 573
    public void rd(final StateRegister rs1, final GPR rd) {
        int instruction = 0x81400000;
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code wr  }<i>rs1</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code wr            %g0, %g0, %y}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.62"
     */
    // Template#: 574, Serial#: 574
    public void wr(final GPR rs1, final GPR rs2, final StateRegister.Writable rd) {
        int instruction = 0x81800000;
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (rs2.value() & 0x1f);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code wr  }<i>rs1</i>, <i>simm13</i>, <i>rd</i>
     * Example disassembly syntax: {@code wr            %g0, -4096, %y}
     * <p>
     * Constraint: {@code -4096 <= simm13 && simm13 <= 4095}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.62"
     */
    // Template#: 575, Serial#: 575
    public void wr(final GPR rs1, final int simm13, final StateRegister.Writable rd) {
        int instruction = 0x81802000;
        checkConstraint(-4096 <= simm13 && simm13 <= 4095, "-4096 <= simm13 && simm13 <= 4095");
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (simm13 & 0x1fff);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code rdpr  }<i>rs1</i>, <i>rd</i>
     * Example disassembly syntax: {@code rdpr          %tpc, %g0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.42"
     */
    // Template#: 576, Serial#: 576
    public void rdpr(final PrivilegedRegister rs1, final GPR rd) {
        int instruction = 0x81500000;
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code wrpr  }<i>rs1</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code wrpr          %g0, %g0, %tpc}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.61"
     */
    // Template#: 577, Serial#: 577
    public void wrpr(final GPR rs1, final GPR rs2, final PrivilegedRegister.Writable rd) {
        int instruction = 0x81900000;
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (rs2.value() & 0x1f);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code wrpr  }<i>rs1</i>, <i>simm13</i>, <i>rd</i>
     * Example disassembly syntax: {@code wrpr          %g0, -4096, %tpc}
     * <p>
     * Constraint: {@code -4096 <= simm13 && simm13 <= 4095}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.61"
     */
    // Template#: 578, Serial#: 578
    public void wrpr(final GPR rs1, final int simm13, final PrivilegedRegister.Writable rd) {
        int instruction = 0x81902000;
        checkConstraint(-4096 <= simm13 && simm13 <= 4095, "-4096 <= simm13 && simm13 <= 4095");
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (simm13 & 0x1fff);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fadds  }<i>rs1</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code fadds         %f0, %f0, %f0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.12"
     */
    // Template#: 579, Serial#: 579
    public void fadds(final SFPR rs1, final SFPR rs2, final SFPR rd) {
        int instruction = 0x81A00820;
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (rs2.value() & 0x1f);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code faddd  }<i>rs1</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code faddd         %f0, %f0, %f0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.12"
     */
    // Template#: 580, Serial#: 580
    public void faddd(final DFPR rs1, final DFPR rs2, final DFPR rd) {
        int instruction = 0x81A00840;
        instruction |= (((rs1.value() >>> 1) & 0xf) << 15) | (((rs1.value() >>> 5) & 0x1) << 14);
        instruction |= (((rs2.value() >>> 1) & 0xf) << 1) | ((rs2.value() >>> 5) & 0x1);
        instruction |= (((rd.value() >>> 1) & 0xf) << 26) | (((rd.value() >>> 5) & 0x1) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code faddq  }<i>rs1</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code faddq         %f0, %f0, %f0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.12"
     */
    // Template#: 581, Serial#: 581
    public void faddq(final QFPR rs1, final QFPR rs2, final QFPR rd) {
        int instruction = 0x81A00860;
        instruction |= (((rs1.value() >>> 2) & 0x7) << 16) | (((rs1.value() >>> 5) & 0x1) << 14);
        instruction |= (((rs2.value() >>> 2) & 0x7) << 2) | ((rs2.value() >>> 5) & 0x1);
        instruction |= (((rd.value() >>> 2) & 0x7) << 27) | (((rd.value() >>> 5) & 0x1) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fsubs  }<i>rs1</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code fsubs         %f0, %f0, %f0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.12"
     */
    // Template#: 582, Serial#: 582
    public void fsubs(final SFPR rs1, final SFPR rs2, final SFPR rd) {
        int instruction = 0x81A008A0;
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (rs2.value() & 0x1f);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fsubd  }<i>rs1</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code fsubd         %f0, %f0, %f0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.12"
     */
    // Template#: 583, Serial#: 583
    public void fsubd(final DFPR rs1, final DFPR rs2, final DFPR rd) {
        int instruction = 0x81A008C0;
        instruction |= (((rs1.value() >>> 1) & 0xf) << 15) | (((rs1.value() >>> 5) & 0x1) << 14);
        instruction |= (((rs2.value() >>> 1) & 0xf) << 1) | ((rs2.value() >>> 5) & 0x1);
        instruction |= (((rd.value() >>> 1) & 0xf) << 26) | (((rd.value() >>> 5) & 0x1) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fsubq  }<i>rs1</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code fsubq         %f0, %f0, %f0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.12"
     */
    // Template#: 584, Serial#: 584
    public void fsubq(final QFPR rs1, final QFPR rs2, final QFPR rd) {
        int instruction = 0x81A008E0;
        instruction |= (((rs1.value() >>> 2) & 0x7) << 16) | (((rs1.value() >>> 5) & 0x1) << 14);
        instruction |= (((rs2.value() >>> 2) & 0x7) << 2) | ((rs2.value() >>> 5) & 0x1);
        instruction |= (((rd.value() >>> 2) & 0x7) << 27) | (((rd.value() >>> 5) & 0x1) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fcmps  }<i>n</i>, <i>rs1</i>, <i>rs2</i>
     * Example disassembly syntax: {@code fcmps         %fcc0, %f0, %f0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.13"
     */
    // Template#: 585, Serial#: 585
    public void fcmps(final FCCOperand n, final SFPR rs1, final SFPR rs2) {
        int instruction = 0x81A80A20;
        instruction |= ((n.value() & 0x3) << 25);
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (rs2.value() & 0x1f);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fcmpd  }<i>n</i>, <i>rs1</i>, <i>rs2</i>
     * Example disassembly syntax: {@code fcmpd         %fcc0, %f0, %f0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.13"
     */
    // Template#: 586, Serial#: 586
    public void fcmpd(final FCCOperand n, final DFPR rs1, final DFPR rs2) {
        int instruction = 0x81A80A40;
        instruction |= ((n.value() & 0x3) << 25);
        instruction |= (((rs1.value() >>> 1) & 0xf) << 15) | (((rs1.value() >>> 5) & 0x1) << 14);
        instruction |= (((rs2.value() >>> 1) & 0xf) << 1) | ((rs2.value() >>> 5) & 0x1);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fcmpq  }<i>n</i>, <i>rs1</i>, <i>rs2</i>
     * Example disassembly syntax: {@code fcmpq         %fcc0, %f0, %f0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.13"
     */
    // Template#: 587, Serial#: 587
    public void fcmpq(final FCCOperand n, final QFPR rs1, final QFPR rs2) {
        int instruction = 0x81A80A60;
        instruction |= ((n.value() & 0x3) << 25);
        instruction |= (((rs1.value() >>> 2) & 0x7) << 16) | (((rs1.value() >>> 5) & 0x1) << 14);
        instruction |= (((rs2.value() >>> 2) & 0x7) << 2) | ((rs2.value() >>> 5) & 0x1);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fcmpes  }<i>n</i>, <i>rs1</i>, <i>rs2</i>
     * Example disassembly syntax: {@code fcmpes        %fcc0, %f0, %f0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.13"
     */
    // Template#: 588, Serial#: 588
    public void fcmpes(final FCCOperand n, final SFPR rs1, final SFPR rs2) {
        int instruction = 0x81A80AA0;
        instruction |= ((n.value() & 0x3) << 25);
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (rs2.value() & 0x1f);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fcmped  }<i>n</i>, <i>rs1</i>, <i>rs2</i>
     * Example disassembly syntax: {@code fcmped        %fcc0, %f0, %f0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.13"
     */
    // Template#: 589, Serial#: 589
    public void fcmped(final FCCOperand n, final DFPR rs1, final DFPR rs2) {
        int instruction = 0x81A80AC0;
        instruction |= ((n.value() & 0x3) << 25);
        instruction |= (((rs1.value() >>> 1) & 0xf) << 15) | (((rs1.value() >>> 5) & 0x1) << 14);
        instruction |= (((rs2.value() >>> 1) & 0xf) << 1) | ((rs2.value() >>> 5) & 0x1);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fcmpeq  }<i>n</i>, <i>rs1</i>, <i>rs2</i>
     * Example disassembly syntax: {@code fcmpeq        %fcc0, %f0, %f0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.13"
     */
    // Template#: 590, Serial#: 590
    public void fcmpeq(final FCCOperand n, final QFPR rs1, final QFPR rs2) {
        int instruction = 0x81A80AE0;
        instruction |= ((n.value() & 0x3) << 25);
        instruction |= (((rs1.value() >>> 2) & 0x7) << 16) | (((rs1.value() >>> 5) & 0x1) << 14);
        instruction |= (((rs2.value() >>> 2) & 0x7) << 2) | ((rs2.value() >>> 5) & 0x1);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fstox  }<i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code fstox         %f0, %f0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.14"
     */
    // Template#: 591, Serial#: 591
    public void fstox(final SFPR rs2, final DFPR rd) {
        int instruction = 0x81A01020;
        instruction |= (rs2.value() & 0x1f);
        instruction |= (((rd.value() >>> 1) & 0xf) << 26) | (((rd.value() >>> 5) & 0x1) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fdtox  }<i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code fdtox         %f0, %f0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.14"
     */
    // Template#: 592, Serial#: 592
    public void fdtox(final DFPR rs2, final DFPR rd) {
        int instruction = 0x81A01040;
        instruction |= (((rs2.value() >>> 1) & 0xf) << 1) | ((rs2.value() >>> 5) & 0x1);
        instruction |= (((rd.value() >>> 1) & 0xf) << 26) | (((rd.value() >>> 5) & 0x1) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fqtox  }<i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code fqtox         %f0, %f0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.14"
     */
    // Template#: 593, Serial#: 593
    public void fqtox(final QFPR rs2, final DFPR rd) {
        int instruction = 0x81A01060;
        instruction |= (((rs2.value() >>> 2) & 0x7) << 2) | ((rs2.value() >>> 5) & 0x1);
        instruction |= (((rd.value() >>> 1) & 0xf) << 26) | (((rd.value() >>> 5) & 0x1) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fstoi  }<i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code fstoi         %f0, %f0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.14"
     */
    // Template#: 594, Serial#: 594
    public void fstoi(final SFPR rs2, final SFPR rd) {
        int instruction = 0x81A01A20;
        instruction |= (rs2.value() & 0x1f);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fdtoi  }<i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code fdtoi         %f0, %f0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.14"
     */
    // Template#: 595, Serial#: 595
    public void fdtoi(final DFPR rs2, final SFPR rd) {
        int instruction = 0x81A01A40;
        instruction |= (((rs2.value() >>> 1) & 0xf) << 1) | ((rs2.value() >>> 5) & 0x1);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fqtoi  }<i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code fqtoi         %f0, %f0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.14"
     */
    // Template#: 596, Serial#: 596
    public void fqtoi(final QFPR rs2, final SFPR rd) {
        int instruction = 0x81A01A60;
        instruction |= (((rs2.value() >>> 2) & 0x7) << 2) | ((rs2.value() >>> 5) & 0x1);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fstod  }<i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code fstod         %f0, %f0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.15"
     */
    // Template#: 597, Serial#: 597
    public void fstod(final SFPR rs2, final DFPR rd) {
        int instruction = 0x81A01920;
        instruction |= (rs2.value() & 0x1f);
        instruction |= (((rd.value() >>> 1) & 0xf) << 26) | (((rd.value() >>> 5) & 0x1) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fstoq  }<i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code fstoq         %f0, %f0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.15"
     */
    // Template#: 598, Serial#: 598
    public void fstoq(final SFPR rs2, final QFPR rd) {
        int instruction = 0x81A019A0;
        instruction |= (rs2.value() & 0x1f);
        instruction |= (((rd.value() >>> 2) & 0x7) << 27) | (((rd.value() >>> 5) & 0x1) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fdtos  }<i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code fdtos         %f0, %f0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.15"
     */
    // Template#: 599, Serial#: 599
    public void fdtos(final DFPR rs2, final SFPR rd) {
        int instruction = 0x81A018C0;
        instruction |= (((rs2.value() >>> 1) & 0xf) << 1) | ((rs2.value() >>> 5) & 0x1);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fdtoq  }<i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code fdtoq         %f0, %f0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.15"
     */
    // Template#: 600, Serial#: 600
    public void fdtoq(final DFPR rs2, final QFPR rd) {
        int instruction = 0x81A019C0;
        instruction |= (((rs2.value() >>> 1) & 0xf) << 1) | ((rs2.value() >>> 5) & 0x1);
        instruction |= (((rd.value() >>> 2) & 0x7) << 27) | (((rd.value() >>> 5) & 0x1) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fqtos  }<i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code fqtos         %f0, %f0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.15"
     */
    // Template#: 601, Serial#: 601
    public void fqtos(final QFPR rs2, final SFPR rd) {
        int instruction = 0x81A018E0;
        instruction |= (((rs2.value() >>> 2) & 0x7) << 2) | ((rs2.value() >>> 5) & 0x1);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fqtod  }<i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code fqtod         %f0, %f0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.15"
     */
    // Template#: 602, Serial#: 602
    public void fqtod(final QFPR rs2, final DFPR rd) {
        int instruction = 0x81A01960;
        instruction |= (((rs2.value() >>> 2) & 0x7) << 2) | ((rs2.value() >>> 5) & 0x1);
        instruction |= (((rd.value() >>> 1) & 0xf) << 26) | (((rd.value() >>> 5) & 0x1) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fxtos  }<i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code fxtos         %f0, %f0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.16"
     */
    // Template#: 603, Serial#: 603
    public void fxtos(final DFPR rs2, final SFPR rd) {
        int instruction = 0x81A01080;
        instruction |= (((rs2.value() >>> 1) & 0xf) << 1) | ((rs2.value() >>> 5) & 0x1);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fitos  }<i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code fitos         %f0, %f0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.16"
     */
    // Template#: 604, Serial#: 604
    public void fitos(final SFPR rs2, final SFPR rd) {
        int instruction = 0x81A01880;
        instruction |= (rs2.value() & 0x1f);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fxtod  }<i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code fxtod         %f0, %f0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.16"
     */
    // Template#: 605, Serial#: 605
    public void fxtod(final DFPR rs2, final DFPR rd) {
        int instruction = 0x81A01100;
        instruction |= (((rs2.value() >>> 1) & 0xf) << 1) | ((rs2.value() >>> 5) & 0x1);
        instruction |= (((rd.value() >>> 1) & 0xf) << 26) | (((rd.value() >>> 5) & 0x1) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fitod  }<i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code fitod         %f0, %f0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.16"
     */
    // Template#: 606, Serial#: 606
    public void fitod(final SFPR rs2, final DFPR rd) {
        int instruction = 0x81A01900;
        instruction |= (rs2.value() & 0x1f);
        instruction |= (((rd.value() >>> 1) & 0xf) << 26) | (((rd.value() >>> 5) & 0x1) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fxtoq  }<i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code fxtoq         %f0, %f0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.16"
     */
    // Template#: 607, Serial#: 607
    public void fxtoq(final DFPR rs2, final QFPR rd) {
        int instruction = 0x81A01180;
        instruction |= (((rs2.value() >>> 1) & 0xf) << 1) | ((rs2.value() >>> 5) & 0x1);
        instruction |= (((rd.value() >>> 2) & 0x7) << 27) | (((rd.value() >>> 5) & 0x1) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fitoq  }<i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code fitoq         %f0, %f0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.16"
     */
    // Template#: 608, Serial#: 608
    public void fitoq(final SFPR rs2, final QFPR rd) {
        int instruction = 0x81A01980;
        instruction |= (rs2.value() & 0x1f);
        instruction |= (((rd.value() >>> 2) & 0x7) << 27) | (((rd.value() >>> 5) & 0x1) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fmovs  }<i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code fmovs         %f0, %f0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.17"
     */
    // Template#: 609, Serial#: 609
    public void fmovs(final SFPR rs2, final SFPR rd) {
        int instruction = 0x81A00020;
        instruction |= (rs2.value() & 0x1f);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fmovd  }<i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code fmovd         %f0, %f0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.17"
     */
    // Template#: 610, Serial#: 610
    public void fmovd(final DFPR rs2, final DFPR rd) {
        int instruction = 0x81A00040;
        instruction |= (((rs2.value() >>> 1) & 0xf) << 1) | ((rs2.value() >>> 5) & 0x1);
        instruction |= (((rd.value() >>> 1) & 0xf) << 26) | (((rd.value() >>> 5) & 0x1) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fmovq  }<i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code fmovq         %f0, %f0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.17"
     */
    // Template#: 611, Serial#: 611
    public void fmovq(final QFPR rs2, final QFPR rd) {
        int instruction = 0x81A00060;
        instruction |= (((rs2.value() >>> 2) & 0x7) << 2) | ((rs2.value() >>> 5) & 0x1);
        instruction |= (((rd.value() >>> 2) & 0x7) << 27) | (((rd.value() >>> 5) & 0x1) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fnegs  }<i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code fnegs         %f0, %f0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.17"
     */
    // Template#: 612, Serial#: 612
    public void fnegs(final SFPR rs2, final SFPR rd) {
        int instruction = 0x81A000A0;
        instruction |= (rs2.value() & 0x1f);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fnegd  }<i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code fnegd         %f0, %f0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.17"
     */
    // Template#: 613, Serial#: 613
    public void fnegd(final DFPR rs2, final DFPR rd) {
        int instruction = 0x81A000C0;
        instruction |= (((rs2.value() >>> 1) & 0xf) << 1) | ((rs2.value() >>> 5) & 0x1);
        instruction |= (((rd.value() >>> 1) & 0xf) << 26) | (((rd.value() >>> 5) & 0x1) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fnegq  }<i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code fnegq         %f0, %f0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.17"
     */
    // Template#: 614, Serial#: 614
    public void fnegq(final QFPR rs2, final QFPR rd) {
        int instruction = 0x81A000E0;
        instruction |= (((rs2.value() >>> 2) & 0x7) << 2) | ((rs2.value() >>> 5) & 0x1);
        instruction |= (((rd.value() >>> 2) & 0x7) << 27) | (((rd.value() >>> 5) & 0x1) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fabss  }<i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code fabss         %f0, %f0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.17"
     */
    // Template#: 615, Serial#: 615
    public void fabss(final SFPR rs2, final SFPR rd) {
        int instruction = 0x81A00120;
        instruction |= (rs2.value() & 0x1f);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fabsd  }<i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code fabsd         %f0, %f0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.17"
     */
    // Template#: 616, Serial#: 616
    public void fabsd(final DFPR rs2, final DFPR rd) {
        int instruction = 0x81A00140;
        instruction |= (((rs2.value() >>> 1) & 0xf) << 1) | ((rs2.value() >>> 5) & 0x1);
        instruction |= (((rd.value() >>> 1) & 0xf) << 26) | (((rd.value() >>> 5) & 0x1) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fabsq  }<i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code fabsq         %f0, %f0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.17"
     */
    // Template#: 617, Serial#: 617
    public void fabsq(final QFPR rs2, final QFPR rd) {
        int instruction = 0x81A00160;
        instruction |= (((rs2.value() >>> 2) & 0x7) << 2) | ((rs2.value() >>> 5) & 0x1);
        instruction |= (((rd.value() >>> 2) & 0x7) << 27) | (((rd.value() >>> 5) & 0x1) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fmuls  }<i>rs1</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code fmuls         %f0, %f0, %f0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.18"
     */
    // Template#: 618, Serial#: 618
    public void fmuls(final SFPR rs1, final SFPR rs2, final SFPR rd) {
        int instruction = 0x81A00920;
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (rs2.value() & 0x1f);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fmuld  }<i>rs1</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code fmuld         %f0, %f0, %f0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.18"
     */
    // Template#: 619, Serial#: 619
    public void fmuld(final DFPR rs1, final DFPR rs2, final DFPR rd) {
        int instruction = 0x81A00940;
        instruction |= (((rs1.value() >>> 1) & 0xf) << 15) | (((rs1.value() >>> 5) & 0x1) << 14);
        instruction |= (((rs2.value() >>> 1) & 0xf) << 1) | ((rs2.value() >>> 5) & 0x1);
        instruction |= (((rd.value() >>> 1) & 0xf) << 26) | (((rd.value() >>> 5) & 0x1) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fmulq  }<i>rs1</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code fmulq         %f0, %f0, %f0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.18"
     */
    // Template#: 620, Serial#: 620
    public void fmulq(final QFPR rs1, final QFPR rs2, final QFPR rd) {
        int instruction = 0x81A00960;
        instruction |= (((rs1.value() >>> 2) & 0x7) << 16) | (((rs1.value() >>> 5) & 0x1) << 14);
        instruction |= (((rs2.value() >>> 2) & 0x7) << 2) | ((rs2.value() >>> 5) & 0x1);
        instruction |= (((rd.value() >>> 2) & 0x7) << 27) | (((rd.value() >>> 5) & 0x1) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fdivs  }<i>rs1</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code fdivs         %f0, %f0, %f0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.18"
     */
    // Template#: 621, Serial#: 621
    public void fdivs(final SFPR rs1, final SFPR rs2, final SFPR rd) {
        int instruction = 0x81A009A0;
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (rs2.value() & 0x1f);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fdivd  }<i>rs1</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code fdivd         %f0, %f0, %f0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.18"
     */
    // Template#: 622, Serial#: 622
    public void fdivd(final DFPR rs1, final DFPR rs2, final DFPR rd) {
        int instruction = 0x81A009C0;
        instruction |= (((rs1.value() >>> 1) & 0xf) << 15) | (((rs1.value() >>> 5) & 0x1) << 14);
        instruction |= (((rs2.value() >>> 1) & 0xf) << 1) | ((rs2.value() >>> 5) & 0x1);
        instruction |= (((rd.value() >>> 1) & 0xf) << 26) | (((rd.value() >>> 5) & 0x1) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fdivq  }<i>rs1</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code fdivq         %f0, %f0, %f0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.18"
     */
    // Template#: 623, Serial#: 623
    public void fdivq(final QFPR rs1, final QFPR rs2, final QFPR rd) {
        int instruction = 0x81A009E0;
        instruction |= (((rs1.value() >>> 2) & 0x7) << 16) | (((rs1.value() >>> 5) & 0x1) << 14);
        instruction |= (((rs2.value() >>> 2) & 0x7) << 2) | ((rs2.value() >>> 5) & 0x1);
        instruction |= (((rd.value() >>> 2) & 0x7) << 27) | (((rd.value() >>> 5) & 0x1) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fsmuld  }<i>rs1</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code fsmuld        %f0, %f0, %f0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.18"
     */
    // Template#: 624, Serial#: 624
    public void fsmuld(final SFPR rs1, final SFPR rs2, final DFPR rd) {
        int instruction = 0x81A00D20;
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (rs2.value() & 0x1f);
        instruction |= (((rd.value() >>> 1) & 0xf) << 26) | (((rd.value() >>> 5) & 0x1) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fdmulq  }<i>rs1</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code fdmulq        %f0, %f0, %f0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.18"
     */
    // Template#: 625, Serial#: 625
    public void fdmulq(final DFPR rs1, final DFPR rs2, final QFPR rd) {
        int instruction = 0x81A00DC0;
        instruction |= (((rs1.value() >>> 1) & 0xf) << 15) | (((rs1.value() >>> 5) & 0x1) << 14);
        instruction |= (((rs2.value() >>> 1) & 0xf) << 1) | ((rs2.value() >>> 5) & 0x1);
        instruction |= (((rd.value() >>> 2) & 0x7) << 27) | (((rd.value() >>> 5) & 0x1) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fsqrts  }<i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code fsqrts        %f0, %f0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.19"
     */
    // Template#: 626, Serial#: 626
    public void fsqrts(final SFPR rs2, final SFPR rd) {
        int instruction = 0x81A00520;
        instruction |= (rs2.value() & 0x1f);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fsqrtd  }<i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code fsqrtd        %f0, %f0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.19"
     */
    // Template#: 627, Serial#: 627
    public void fsqrtd(final DFPR rs2, final DFPR rd) {
        int instruction = 0x81A00540;
        instruction |= (((rs2.value() >>> 1) & 0xf) << 1) | ((rs2.value() >>> 5) & 0x1);
        instruction |= (((rd.value() >>> 1) & 0xf) << 26) | (((rd.value() >>> 5) & 0x1) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fsqrtq  }<i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code fsqrtq        %f0, %f0}
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.19"
     */
    // Template#: 628, Serial#: 628
    public void fsqrtq(final QFPR rs2, final QFPR rd) {
        int instruction = 0x81A00560;
        instruction |= (((rs2.value() >>> 2) & 0x7) << 2) | ((rs2.value() >>> 5) & 0x1);
        instruction |= (((rd.value() >>> 2) & 0x7) << 27) | (((rd.value() >>> 5) & 0x1) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code impdep1  }
     * Example disassembly syntax: {@code impdep1       }
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.23"
     */
    // Template#: 629, Serial#: 629
    public void impdep1() {
        int instruction = 0x81B00000;
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code impdep2  }
     * Example disassembly syntax: {@code impdep2       }
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.23"
     */
    // Template#: 630, Serial#: 630
    public void impdep2() {
        int instruction = 0x81B80000;
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code nop  }
     * Example disassembly syntax: {@code nop           }
     * <p>
     * This is a synthetic instruction equivalent to: {@code sethi(0, G0)}
     *
     * @see #sethi(int, GPR)
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.39"
     */
    // Template#: 631, Serial#: 631
    public void nop() {
        int instruction = 0x01000000;
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code cmp  }<i>rs1</i>, <i>rs2</i>
     * Example disassembly syntax: {@code cmp           %g0, %g0}
     * <p>
     * This is a synthetic instruction equivalent to: {@code subcc(rs1, rs2, G0)}
     *
     * @see #subcc(GPR, GPR, GPR)
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section G.3"
     */
    // Template#: 632, Serial#: 632
    public void cmp(final GPR rs1, final GPR rs2) {
        int instruction = 0x80A00000;
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (rs2.value() & 0x1f);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code cmp  }<i>rs1</i>, <i>simm13</i>
     * Example disassembly syntax: {@code cmp           %g0, -4096}
     * <p>
     * This is a synthetic instruction equivalent to: {@code subcc(rs1, simm13, G0)}
     * <p>
     * Constraint: {@code -4096 <= simm13 && simm13 <= 4095}<br />
     *
     * @see #subcc(GPR, int, GPR)
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section G.3"
     */
    // Template#: 633, Serial#: 633
    public void cmp(final GPR rs1, final int simm13) {
        int instruction = 0x80A02000;
        checkConstraint(-4096 <= simm13 && simm13 <= 4095, "-4096 <= simm13 && simm13 <= 4095");
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (simm13 & 0x1fff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code jmp  }<i>rs1</i>, <i>rs2</i>
     * Example disassembly syntax: {@code jmp           %g0 + %g0}
     * <p>
     * This is a synthetic instruction equivalent to: {@code jmpl(rs1, rs2, G0)}
     *
     * @see #jmpl(GPR, GPR, GPR)
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section G.3"
     */
    // Template#: 634, Serial#: 634
    public void jmp(final GPR rs1, final GPR rs2) {
        int instruction = 0x81C00000;
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (rs2.value() & 0x1f);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code jmp  }<i>rs1</i>, <i>simm13</i>
     * Example disassembly syntax: {@code jmp           %g0 + -4096}
     * <p>
     * This is a synthetic instruction equivalent to: {@code jmpl(rs1, simm13, G0)}
     * <p>
     * Constraint: {@code -4096 <= simm13 && simm13 <= 4095}<br />
     *
     * @see #jmpl(GPR, int, GPR)
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section G.3"
     */
    // Template#: 635, Serial#: 635
    public void jmp(final GPR rs1, final int simm13) {
        int instruction = 0x81C02000;
        checkConstraint(-4096 <= simm13 && simm13 <= 4095, "-4096 <= simm13 && simm13 <= 4095");
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (simm13 & 0x1fff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code call  }<i>rs1</i>, <i>rs2</i>
     * Example disassembly syntax: {@code call          %g0 + %g0}
     * <p>
     * This is a synthetic instruction equivalent to: {@code jmpl(rs1, rs2, O7)}
     *
     * @see #jmpl(GPR, GPR, GPR)
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section G.3"
     */
    // Template#: 636, Serial#: 636
    public void call(final GPR rs1, final GPR rs2) {
        int instruction = 0x9FC00000;
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (rs2.value() & 0x1f);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code call  }<i>rs1</i>, <i>simm13</i>
     * Example disassembly syntax: {@code call          %g0 + -4096}
     * <p>
     * This is a synthetic instruction equivalent to: {@code jmpl(rs1, simm13, O7)}
     * <p>
     * Constraint: {@code -4096 <= simm13 && simm13 <= 4095}<br />
     *
     * @see #jmpl(GPR, int, GPR)
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section G.3"
     */
    // Template#: 637, Serial#: 637
    public void call(final GPR rs1, final int simm13) {
        int instruction = 0x9FC02000;
        checkConstraint(-4096 <= simm13 && simm13 <= 4095, "-4096 <= simm13 && simm13 <= 4095");
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (simm13 & 0x1fff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code iprefetch  }<i>label</i>
     * Example disassembly syntax: {@code iprefetch     L1: -1048576}
     * <p>
     * This is a synthetic instruction equivalent to: {@code b(N, A, PT, XCC, label)}
     * <p>
     * Constraint: {@code (-1048576 <= label && label <= 1048572) && ((label % 4) == 0)}<br />
     *
     * @see #b(Bicc, AnnulBit, BranchPredictionBit, ICCOperand, int)
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section G.3"
     */
    // Template#: 638, Serial#: 638
    public void iprefetch(final int label) {
        int instruction = 0x20680000;
        checkConstraint((-1048576 <= label && label <= 1048572) && ((label % 4) == 0), "(-1048576 <= label && label <= 1048572) && ((label % 4) == 0)");
        instruction |= ((label >> 2) & 0x7ffff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code tst  }<i>rs2</i>
     * Example disassembly syntax: {@code tst           %g0}
     * <p>
     * This is a synthetic instruction equivalent to: {@code orcc(G0, rs2, G0)}
     *
     * @see #orcc(GPR, GPR, GPR)
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section G.3"
     */
    // Template#: 639, Serial#: 639
    public void tst(final GPR rs2) {
        int instruction = 0x80900000;
        instruction |= (rs2.value() & 0x1f);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code ret  }
     * Example disassembly syntax: {@code ret           }
     * <p>
     * This is a synthetic instruction equivalent to: {@code jmpl(I7, 8, G0)}
     *
     * @see #jmpl(GPR, int, GPR)
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section G.3"
     */
    // Template#: 640, Serial#: 640
    public void ret() {
        int instruction = 0x81C7E008;
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code retl  }
     * Example disassembly syntax: {@code retl          }
     * <p>
     * This is a synthetic instruction equivalent to: {@code jmpl(O7, 8, G0)}
     *
     * @see #jmpl(GPR, int, GPR)
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section G.3"
     */
    // Template#: 641, Serial#: 641
    public void retl() {
        int instruction = 0x81C3E008;
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code restore  }
     * Example disassembly syntax: {@code restore       }
     * <p>
     * This is a synthetic instruction equivalent to: {@code restore(G0, G0, G0)}
     *
     * @see #restore(GPR, GPR, GPR)
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section G.3"
     */
    // Template#: 642, Serial#: 642
    public void restore() {
        int instruction = 0x81E80000;
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code save  }
     * Example disassembly syntax: {@code save          }
     * <p>
     * This is a synthetic instruction equivalent to: {@code save(G0, G0, G0)}
     *
     * @see #save(GPR, GPR, GPR)
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section G.3"
     */
    // Template#: 643, Serial#: 643
    public void save() {
        int instruction = 0x81E00000;
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code signx  }<i>rs1</i>, <i>rd</i>
     * Example disassembly syntax: {@code signx         %g0, %g0}
     * <p>
     * This is a synthetic instruction equivalent to: {@code sra(rs1, G0, rd)}
     *
     * @see #sra(GPR, GPR, GPR)
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section G.3"
     */
    // Template#: 644, Serial#: 644
    public void signx(final GPR rs1, final GPR rd) {
        int instruction = 0x81380000;
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code signx  }<i>rd</i>
     * Example disassembly syntax: {@code signx         %g0}
     * <p>
     * This is a synthetic instruction equivalent to: {@code sra(rd.value(), G0, rd)}
     *
     * @see #sra(GPR, GPR, GPR)
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section G.3"
     */
    // Template#: 645, Serial#: 645
    public void signx(final GPR rd) {
        int instruction = 0x81380000;
        instruction |= ((rd.value() & 0x1f) << 14);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code not  }<i>rs1</i>, <i>rd</i>
     * Example disassembly syntax: {@code not           %g0, %g0}
     * <p>
     * This is a synthetic instruction equivalent to: {@code xnor(rs1, G0, rd)}
     *
     * @see #xnor(GPR, GPR, GPR)
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section G.3"
     */
    // Template#: 646, Serial#: 646
    public void not(final GPR rs1, final GPR rd) {
        int instruction = 0x80380000;
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code not  }<i>rd</i>
     * Example disassembly syntax: {@code not           %g0}
     * <p>
     * This is a synthetic instruction equivalent to: {@code xnor(rd.value(), G0, rd)}
     *
     * @see #xnor(GPR, GPR, GPR)
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section G.3"
     */
    // Template#: 647, Serial#: 647
    public void not(final GPR rd) {
        int instruction = 0x80380000;
        instruction |= ((rd.value() & 0x1f) << 14);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code neg  }<i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code neg           %g0, %g0}
     * <p>
     * This is a synthetic instruction equivalent to: {@code sub(G0, rs2, rd)}
     *
     * @see #sub(GPR, GPR, GPR)
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section G.3"
     */
    // Template#: 648, Serial#: 648
    public void neg(final GPR rs2, final GPR rd) {
        int instruction = 0x80200000;
        instruction |= (rs2.value() & 0x1f);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code neg  }<i>rd</i>
     * Example disassembly syntax: {@code neg           %g0}
     * <p>
     * This is a synthetic instruction equivalent to: {@code sub(G0, rd.value(), rd)}
     *
     * @see #sub(GPR, GPR, GPR)
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section G.3"
     */
    // Template#: 649, Serial#: 649
    public void neg(final GPR rd) {
        int instruction = 0x80200000;
        instruction |= (rd.value() & 0x1f);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code cas  }<i>rs1</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code cas           [%g0] ,%g0, %g0}
     * <p>
     * This is a synthetic instruction equivalent to: {@code casa(rs1, 128, rs2, rd)}
     *
     * @see #casa(GPR, int, GPR, GPR)
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section G.3"
     */
    // Template#: 650, Serial#: 650
    public void cas(final GPR rs1, final GPR rs2, final GPR rd) {
        int instruction = 0xC1E01000;
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (rs2.value() & 0x1f);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code casl  }<i>rs1</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code casl          [%g0] ,%g0, %g0}
     * <p>
     * This is a synthetic instruction equivalent to: {@code casa(rs1, 136, rs2, rd)}
     *
     * @see #casa(GPR, int, GPR, GPR)
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section G.3"
     */
    // Template#: 651, Serial#: 651
    public void casl(final GPR rs1, final GPR rs2, final GPR rd) {
        int instruction = 0xC1E01100;
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (rs2.value() & 0x1f);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code casx  }<i>rs1</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code casx          [%g0] ,%g0, %g0}
     * <p>
     * This is a synthetic instruction equivalent to: {@code casxa(rs1, 128, rs2, rd)}
     *
     * @see #casxa(GPR, int, GPR, GPR)
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section G.3"
     */
    // Template#: 652, Serial#: 652
    public void casx(final GPR rs1, final GPR rs2, final GPR rd) {
        int instruction = 0xC1F01000;
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (rs2.value() & 0x1f);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code casxl  }<i>rs1</i>, <i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code casxl         [%g0] ,%g0, %g0}
     * <p>
     * This is a synthetic instruction equivalent to: {@code casxa(rs1, 136, rs2, rd)}
     *
     * @see #casxa(GPR, int, GPR, GPR)
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section G.3"
     */
    // Template#: 653, Serial#: 653
    public void casxl(final GPR rs1, final GPR rs2, final GPR rd) {
        int instruction = 0xC1F01100;
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (rs2.value() & 0x1f);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code inc  }<i>rd</i>
     * Example disassembly syntax: {@code inc           %g0}
     * <p>
     * This is a synthetic instruction equivalent to: {@code add(rd.value(), 1, rd)}
     *
     * @see #add(GPR, int, GPR)
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section G.3"
     */
    // Template#: 654, Serial#: 654
    public void inc(final GPR rd) {
        int instruction = 0x80002001;
        instruction |= ((rd.value() & 0x1f) << 14);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code inc  }<i>simm13</i>, <i>rd</i>
     * Example disassembly syntax: {@code inc           -4096, %g0}
     * <p>
     * This is a synthetic instruction equivalent to: {@code add(rd.value(), simm13, rd)}
     * <p>
     * Constraint: {@code -4096 <= simm13 && simm13 <= 4095}<br />
     *
     * @see #add(GPR, int, GPR)
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section G.3"
     */
    // Template#: 655, Serial#: 655
    public void inc(final int simm13, final GPR rd) {
        int instruction = 0x80002000;
        checkConstraint(-4096 <= simm13 && simm13 <= 4095, "-4096 <= simm13 && simm13 <= 4095");
        instruction |= ((rd.value() & 0x1f) << 14);
        instruction |= (simm13 & 0x1fff);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code inccc  }<i>rd</i>
     * Example disassembly syntax: {@code inccc         %g0}
     * <p>
     * This is a synthetic instruction equivalent to: {@code addcc(rd.value(), 1, rd)}
     *
     * @see #addcc(GPR, int, GPR)
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section G.3"
     */
    // Template#: 656, Serial#: 656
    public void inccc(final GPR rd) {
        int instruction = 0x80802001;
        instruction |= ((rd.value() & 0x1f) << 14);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code inccc  }<i>simm13</i>, <i>rd</i>
     * Example disassembly syntax: {@code inccc         -4096, %g0}
     * <p>
     * This is a synthetic instruction equivalent to: {@code addcc(rd.value(), simm13, rd)}
     * <p>
     * Constraint: {@code -4096 <= simm13 && simm13 <= 4095}<br />
     *
     * @see #addcc(GPR, int, GPR)
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section G.3"
     */
    // Template#: 657, Serial#: 657
    public void inccc(final int simm13, final GPR rd) {
        int instruction = 0x80802000;
        checkConstraint(-4096 <= simm13 && simm13 <= 4095, "-4096 <= simm13 && simm13 <= 4095");
        instruction |= ((rd.value() & 0x1f) << 14);
        instruction |= (simm13 & 0x1fff);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code dec  }<i>rd</i>
     * Example disassembly syntax: {@code dec           %g0}
     * <p>
     * This is a synthetic instruction equivalent to: {@code sub(rd.value(), 1, rd)}
     *
     * @see #sub(GPR, int, GPR)
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section G.3"
     */
    // Template#: 658, Serial#: 658
    public void dec(final GPR rd) {
        int instruction = 0x80202001;
        instruction |= ((rd.value() & 0x1f) << 14);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code dec  }<i>simm13</i>, <i>rd</i>
     * Example disassembly syntax: {@code dec           -4096, %g0}
     * <p>
     * This is a synthetic instruction equivalent to: {@code sub(rd.value(), simm13, rd)}
     * <p>
     * Constraint: {@code -4096 <= simm13 && simm13 <= 4095}<br />
     *
     * @see #sub(GPR, int, GPR)
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section G.3"
     */
    // Template#: 659, Serial#: 659
    public void dec(final int simm13, final GPR rd) {
        int instruction = 0x80202000;
        checkConstraint(-4096 <= simm13 && simm13 <= 4095, "-4096 <= simm13 && simm13 <= 4095");
        instruction |= ((rd.value() & 0x1f) << 14);
        instruction |= (simm13 & 0x1fff);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code deccc  }<i>rd</i>
     * Example disassembly syntax: {@code deccc         %g0}
     * <p>
     * This is a synthetic instruction equivalent to: {@code subcc(rd.value(), 1, rd)}
     *
     * @see #subcc(GPR, int, GPR)
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section G.3"
     */
    // Template#: 660, Serial#: 660
    public void deccc(final GPR rd) {
        int instruction = 0x80A02001;
        instruction |= ((rd.value() & 0x1f) << 14);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code deccc  }<i>simm13</i>, <i>rd</i>
     * Example disassembly syntax: {@code deccc         -4096, %g0}
     * <p>
     * This is a synthetic instruction equivalent to: {@code subcc(rd.value(), simm13, rd)}
     * <p>
     * Constraint: {@code -4096 <= simm13 && simm13 <= 4095}<br />
     *
     * @see #subcc(GPR, int, GPR)
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section G.3"
     */
    // Template#: 661, Serial#: 661
    public void deccc(final int simm13, final GPR rd) {
        int instruction = 0x80A02000;
        checkConstraint(-4096 <= simm13 && simm13 <= 4095, "-4096 <= simm13 && simm13 <= 4095");
        instruction |= ((rd.value() & 0x1f) << 14);
        instruction |= (simm13 & 0x1fff);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code btst  }<i>rs2</i>, <i>rs1</i>
     * Example disassembly syntax: {@code btst          %g0, %g0}
     * <p>
     * This is a synthetic instruction equivalent to: {@code andcc(rs1, rs2, G0)}
     *
     * @see #andcc(GPR, GPR, GPR)
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section G.3"
     */
    // Template#: 662, Serial#: 662
    public void btst(final GPR rs2, final GPR rs1) {
        int instruction = 0x80880000;
        instruction |= (rs2.value() & 0x1f);
        instruction |= ((rs1.value() & 0x1f) << 14);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code btst  }<i>simm13</i>, <i>rs1</i>
     * Example disassembly syntax: {@code btst          -4096, %g0}
     * <p>
     * This is a synthetic instruction equivalent to: {@code andcc(rs1, simm13, G0)}
     * <p>
     * Constraint: {@code -4096 <= simm13 && simm13 <= 4095}<br />
     *
     * @see #andcc(GPR, int, GPR)
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section G.3"
     */
    // Template#: 663, Serial#: 663
    public void btst(final int simm13, final GPR rs1) {
        int instruction = 0x80882000;
        checkConstraint(-4096 <= simm13 && simm13 <= 4095, "-4096 <= simm13 && simm13 <= 4095");
        instruction |= (simm13 & 0x1fff);
        instruction |= ((rs1.value() & 0x1f) << 14);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code bset  }<i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code bset          %g0, %g0}
     * <p>
     * This is a synthetic instruction equivalent to: {@code or(rd.value(), rs2, rd)}
     *
     * @see #or(GPR, GPR, GPR)
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section G.3"
     */
    // Template#: 664, Serial#: 664
    public void bset(final GPR rs2, final GPR rd) {
        int instruction = 0x80100000;
        instruction |= ((rd.value() & 0x1f) << 14);
        instruction |= (rs2.value() & 0x1f);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code bset  }<i>simm13</i>, <i>rd</i>
     * Example disassembly syntax: {@code bset          -4096, %g0}
     * <p>
     * This is a synthetic instruction equivalent to: {@code or(rd.value(), simm13, rd)}
     * <p>
     * Constraint: {@code -4096 <= simm13 && simm13 <= 4095}<br />
     *
     * @see #or(GPR, int, GPR)
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section G.3"
     */
    // Template#: 665, Serial#: 665
    public void bset(final int simm13, final GPR rd) {
        int instruction = 0x80102000;
        checkConstraint(-4096 <= simm13 && simm13 <= 4095, "-4096 <= simm13 && simm13 <= 4095");
        instruction |= ((rd.value() & 0x1f) << 14);
        instruction |= (simm13 & 0x1fff);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code bclr  }<i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code bclr          %g0, %g0}
     * <p>
     * This is a synthetic instruction equivalent to: {@code andn(rd.value(), rs2, rd)}
     *
     * @see #andn(GPR, GPR, GPR)
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section G.3"
     */
    // Template#: 666, Serial#: 666
    public void bclr(final GPR rs2, final GPR rd) {
        int instruction = 0x80280000;
        instruction |= ((rd.value() & 0x1f) << 14);
        instruction |= (rs2.value() & 0x1f);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code bclr  }<i>simm13</i>, <i>rd</i>
     * Example disassembly syntax: {@code bclr          -4096, %g0}
     * <p>
     * This is a synthetic instruction equivalent to: {@code andn(rd.value(), simm13, rd)}
     * <p>
     * Constraint: {@code -4096 <= simm13 && simm13 <= 4095}<br />
     *
     * @see #andn(GPR, int, GPR)
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section G.3"
     */
    // Template#: 667, Serial#: 667
    public void bclr(final int simm13, final GPR rd) {
        int instruction = 0x80282000;
        checkConstraint(-4096 <= simm13 && simm13 <= 4095, "-4096 <= simm13 && simm13 <= 4095");
        instruction |= ((rd.value() & 0x1f) << 14);
        instruction |= (simm13 & 0x1fff);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code btog  }<i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code btog          %g0, %g0}
     * <p>
     * This is a synthetic instruction equivalent to: {@code xor(rd.value(), rs2, rd)}
     *
     * @see #xor(GPR, GPR, GPR)
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section G.3"
     */
    // Template#: 668, Serial#: 668
    public void btog(final GPR rs2, final GPR rd) {
        int instruction = 0x80180000;
        instruction |= ((rd.value() & 0x1f) << 14);
        instruction |= (rs2.value() & 0x1f);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code btog  }<i>simm13</i>, <i>rd</i>
     * Example disassembly syntax: {@code btog          -4096, %g0}
     * <p>
     * This is a synthetic instruction equivalent to: {@code xor(rd.value(), simm13, rd)}
     * <p>
     * Constraint: {@code -4096 <= simm13 && simm13 <= 4095}<br />
     *
     * @see #xor(GPR, int, GPR)
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section G.3"
     */
    // Template#: 669, Serial#: 669
    public void btog(final int simm13, final GPR rd) {
        int instruction = 0x80182000;
        checkConstraint(-4096 <= simm13 && simm13 <= 4095, "-4096 <= simm13 && simm13 <= 4095");
        instruction |= ((rd.value() & 0x1f) << 14);
        instruction |= (simm13 & 0x1fff);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code clr  }<i>rd</i>
     * Example disassembly syntax: {@code clr           %g0}
     * <p>
     * This is a synthetic instruction equivalent to: {@code or(G0, G0, rd)}
     *
     * @see #or(GPR, GPR, GPR)
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section G.3"
     */
    // Template#: 670, Serial#: 670
    public void clr(final GPR rd) {
        int instruction = 0x80100000;
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code clrb  }<i>rs1</i>, <i>rs2</i>
     * Example disassembly syntax: {@code clrb          [%g0 + %g0]}
     * <p>
     * This is a synthetic instruction equivalent to: {@code stb(G0, rs1, rs2)}
     *
     * @see #stb(GPR, GPR, GPR)
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section G.3"
     */
    // Template#: 671, Serial#: 671
    public void clrb(final GPR rs1, final GPR rs2) {
        int instruction = 0xC0280000;
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (rs2.value() & 0x1f);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code clrb  }<i>rs1</i>, <i>simm13</i>
     * Example disassembly syntax: {@code clrb          [%g0 + -4096]}
     * <p>
     * This is a synthetic instruction equivalent to: {@code stb(G0, rs1, simm13)}
     * <p>
     * Constraint: {@code -4096 <= simm13 && simm13 <= 4095}<br />
     *
     * @see #stb(GPR, GPR, int)
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section G.3"
     */
    // Template#: 672, Serial#: 672
    public void clrb(final GPR rs1, final int simm13) {
        int instruction = 0xC0282000;
        checkConstraint(-4096 <= simm13 && simm13 <= 4095, "-4096 <= simm13 && simm13 <= 4095");
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (simm13 & 0x1fff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code clrh  }<i>rs1</i>, <i>rs2</i>
     * Example disassembly syntax: {@code clrh          [%g0 + %g0]}
     * <p>
     * This is a synthetic instruction equivalent to: {@code sth(G0, rs1, rs2)}
     *
     * @see #sth(GPR, GPR, GPR)
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section G.3"
     */
    // Template#: 673, Serial#: 673
    public void clrh(final GPR rs1, final GPR rs2) {
        int instruction = 0xC0300000;
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (rs2.value() & 0x1f);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code clrh  }<i>rs1</i>, <i>simm13</i>
     * Example disassembly syntax: {@code clrh          [%g0 + -4096]}
     * <p>
     * This is a synthetic instruction equivalent to: {@code sth(G0, rs1, simm13)}
     * <p>
     * Constraint: {@code -4096 <= simm13 && simm13 <= 4095}<br />
     *
     * @see #sth(GPR, GPR, int)
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section G.3"
     */
    // Template#: 674, Serial#: 674
    public void clrh(final GPR rs1, final int simm13) {
        int instruction = 0xC0302000;
        checkConstraint(-4096 <= simm13 && simm13 <= 4095, "-4096 <= simm13 && simm13 <= 4095");
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (simm13 & 0x1fff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code clr  }<i>rs1</i>, <i>rs2</i>
     * Example disassembly syntax: {@code clr           [%g0 + %g0]}
     * <p>
     * This is a synthetic instruction equivalent to: {@code stw(G0, rs1, rs2)}
     *
     * @see #stw(GPR, GPR, GPR)
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section G.3"
     */
    // Template#: 675, Serial#: 675
    public void clr(final GPR rs1, final GPR rs2) {
        int instruction = 0xC0200000;
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (rs2.value() & 0x1f);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code clr  }<i>rs1</i>, <i>simm13</i>
     * Example disassembly syntax: {@code clr           [%g0 + -4096]}
     * <p>
     * This is a synthetic instruction equivalent to: {@code stw(G0, rs1, simm13)}
     * <p>
     * Constraint: {@code -4096 <= simm13 && simm13 <= 4095}<br />
     *
     * @see #stw(GPR, GPR, int)
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section G.3"
     */
    // Template#: 676, Serial#: 676
    public void clr(final GPR rs1, final int simm13) {
        int instruction = 0xC0202000;
        checkConstraint(-4096 <= simm13 && simm13 <= 4095, "-4096 <= simm13 && simm13 <= 4095");
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (simm13 & 0x1fff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code clrx  }<i>rs1</i>, <i>rs2</i>
     * Example disassembly syntax: {@code clrx          [%g0 + %g0]}
     * <p>
     * This is a synthetic instruction equivalent to: {@code stx(G0, rs1, rs2)}
     *
     * @see #stx(GPR, GPR, GPR)
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section G.3"
     */
    // Template#: 677, Serial#: 677
    public void clrx(final GPR rs1, final GPR rs2) {
        int instruction = 0xC0700000;
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (rs2.value() & 0x1f);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code clrx  }<i>rs1</i>, <i>simm13</i>
     * Example disassembly syntax: {@code clrx          [%g0 + -4096]}
     * <p>
     * This is a synthetic instruction equivalent to: {@code stx(G0, rs1, simm13)}
     * <p>
     * Constraint: {@code -4096 <= simm13 && simm13 <= 4095}<br />
     *
     * @see #stx(GPR, GPR, int)
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section G.3"
     */
    // Template#: 678, Serial#: 678
    public void clrx(final GPR rs1, final int simm13) {
        int instruction = 0xC0702000;
        checkConstraint(-4096 <= simm13 && simm13 <= 4095, "-4096 <= simm13 && simm13 <= 4095");
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= (simm13 & 0x1fff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code clruw  }<i>rs1</i>, <i>rd</i>
     * Example disassembly syntax: {@code clruw         %g0, %g0}
     * <p>
     * This is a synthetic instruction equivalent to: {@code srl(rs1, G0, rd)}
     *
     * @see #srl(GPR, GPR, GPR)
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section G.3"
     */
    // Template#: 679, Serial#: 679
    public void clruw(final GPR rs1, final GPR rd) {
        int instruction = 0x81300000;
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code clruw  }<i>rd</i>
     * Example disassembly syntax: {@code clruw         %g0}
     * <p>
     * This is a synthetic instruction equivalent to: {@code srl(rd.value(), G0, rd)}
     *
     * @see #srl(GPR, GPR, GPR)
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section G.3"
     */
    // Template#: 680, Serial#: 680
    public void clruw(final GPR rd) {
        int instruction = 0x81300000;
        instruction |= ((rd.value() & 0x1f) << 14);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code mov  }<i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code mov           %g0, %g0}
     * <p>
     * This is a synthetic instruction equivalent to: {@code or(G0, rs2, rd)}
     *
     * @see #or(GPR, GPR, GPR)
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section G.3"
     */
    // Template#: 681, Serial#: 681
    public void mov(final GPR rs2, final GPR rd) {
        int instruction = 0x80100000;
        instruction |= (rs2.value() & 0x1f);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code mov  }<i>simm13</i>, <i>rd</i>
     * Example disassembly syntax: {@code mov           -4096, %g0}
     * <p>
     * This is a synthetic instruction equivalent to: {@code or(G0, simm13, rd)}
     * <p>
     * Constraint: {@code -4096 <= simm13 && simm13 <= 4095}<br />
     *
     * @see #or(GPR, int, GPR)
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section G.3"
     */
    // Template#: 682, Serial#: 682
    public void mov(final int simm13, final GPR rd) {
        int instruction = 0x80102000;
        checkConstraint(-4096 <= simm13 && simm13 <= 4095, "-4096 <= simm13 && simm13 <= 4095");
        instruction |= (simm13 & 0x1fff);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code mov  }<i>rs1</i>, <i>rd</i>
     * Example disassembly syntax: {@code mov           %y, %g0}
     * <p>
     * This is a synthetic instruction equivalent to: {@code rd(rs1, rd)}
     * <p>
     * Constraint: {@code rs1.isYorASR()}<br />
     *
     * @see #rd(StateRegister, GPR)
     * @see com.sun.max.asm.sparc.StateRegister#isYorASR
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section G.3"
     */
    // Template#: 683, Serial#: 683
    public void mov(final StateRegister rs1, final GPR rd) {
        int instruction = 0x81400000;
        checkConstraint(rs1.isYorASR(), "rs1.isYorASR()");
        instruction |= ((rs1.value() & 0x1f) << 14);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code mov  }<i>rs2</i>, <i>rd</i>
     * Example disassembly syntax: {@code mov           %g0, %y}
     * <p>
     * This is a synthetic instruction equivalent to: {@code wr(G0, rs2, rd)}
     * <p>
     * Constraint: {@code rd.isYorASR()}<br />
     *
     * @see #wr(GPR, GPR, StateRegister.Writable)
     * @see com.sun.max.asm.sparc.StateRegister#isYorASR
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section G.3"
     */
    // Template#: 684, Serial#: 684
    public void mov(final GPR rs2, final StateRegister.Writable rd) {
        int instruction = 0x81800000;
        checkConstraint(rd.isYorASR(), "rd.isYorASR()");
        instruction |= (rs2.value() & 0x1f);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code mov  }<i>simm13</i>, <i>rd</i>
     * Example disassembly syntax: {@code mov           -4096, %y}
     * <p>
     * This is a synthetic instruction equivalent to: {@code wr(G0, simm13, rd)}
     * <p>
     * Constraint: {@code -4096 <= simm13 && simm13 <= 4095}<br />
     * Constraint: {@code rd.isYorASR()}<br />
     *
     * @see #wr(GPR, int, StateRegister.Writable)
     * @see com.sun.max.asm.sparc.StateRegister#isYorASR
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section G.3"
     */
    // Template#: 685, Serial#: 685
    public void mov(final int simm13, final StateRegister.Writable rd) {
        int instruction = 0x81802000;
        checkConstraint(-4096 <= simm13 && simm13 <= 4095, "-4096 <= simm13 && simm13 <= 4095");
        checkConstraint(rd.isYorASR(), "rd.isYorASR()");
        instruction |= (simm13 & 0x1fff);
        instruction |= ((rd.value() & 0x1f) << 25);
        emitInt(instruction);
    }

// END GENERATED RAW ASSEMBLER METHODS

}
