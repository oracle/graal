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

package com.sun.max.asm.amd64.complete;

import com.sun.max.asm.*;
import com.sun.max.asm.amd64.*;
import com.sun.max.asm.x86.*;

public class AMD64LabelAssembler extends AMD64RawAssembler {

    public AMD64LabelAssembler(long startAddress) {
        super(startAddress);
    }

    public AMD64LabelAssembler() {
    }

// START GENERATED LABEL ASSEMBLER METHODS
    /**
     * Pseudo-external assembler syntax: {@code adc  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code adc       ax, [L1: +305419896]}
     */
    // Template#: 1, Serial#: 217
    public void rip_adc(final AMD64GeneralRegister16 destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_adc(destination, placeHolder);
        new rip_adc_217(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code adc  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code adc       eax, [L1: +305419896]}
     */
    // Template#: 2, Serial#: 199
    public void rip_adc(final AMD64GeneralRegister32 destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_adc(destination, placeHolder);
        new rip_adc_199(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code adc  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code adc       rax, [L1: +305419896]}
     */
    // Template#: 3, Serial#: 208
    public void rip_adc(final AMD64GeneralRegister64 destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_adc(destination, placeHolder);
        new rip_adc_208(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code adc  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code adc       al, [L1: +305419896]}
     */
    // Template#: 4, Serial#: 172
    public void rip_adc(final AMD64GeneralRegister8 destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_adc(destination, placeHolder);
        new rip_adc_172(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code adcb  }<i>label</i>, <i>imm8</i>
     * Example disassembly syntax: {@code adcb      [L1: +305419896], 0x12}
     */
    // Template#: 5, Serial#: 508
    public void rip_adcb(final Label label, final byte imm8) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_adcb(placeHolder, imm8);
        new rip_adcb_508(startPosition, currentPosition() - startPosition, imm8, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code adcl  }<i>label</i>, <i>imm8</i>
     * Example disassembly syntax: {@code adcl      [L1: +305419896], 0x12}
     */
    // Template#: 6, Serial#: 940
    public void rip_adcl(final Label label, final byte imm8) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_adcl(placeHolder, imm8);
        new rip_adcl_940(startPosition, currentPosition() - startPosition, imm8, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code adcq  }<i>label</i>, <i>imm8</i>
     * Example disassembly syntax: {@code adcq      [L1: +305419896], 0x12}
     */
    // Template#: 7, Serial#: 1012
    public void rip_adcq(final Label label, final byte imm8) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_adcq(placeHolder, imm8);
        new rip_adcq_1012(startPosition, currentPosition() - startPosition, imm8, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code adcw  }<i>label</i>, <i>imm8</i>
     * Example disassembly syntax: {@code adcw      [L1: +305419896], 0x12}
     */
    // Template#: 8, Serial#: 1084
    public void rip_adcw(final Label label, final byte imm8) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_adcw(placeHolder, imm8);
        new rip_adcw_1084(startPosition, currentPosition() - startPosition, imm8, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code adc  }<i>label</i>, <i>source</i>
     * Example disassembly syntax: {@code adc       [L1: +305419896], ax}
     */
    // Template#: 9, Serial#: 163
    public void rip_adc(final Label label, final AMD64GeneralRegister16 source) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_adc(placeHolder, source);
        new rip_adc_163(startPosition, currentPosition() - startPosition, source, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code adc  }<i>label</i>, <i>source</i>
     * Example disassembly syntax: {@code adc       [L1: +305419896], eax}
     */
    // Template#: 10, Serial#: 145
    public void rip_adc(final Label label, final AMD64GeneralRegister32 source) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_adc(placeHolder, source);
        new rip_adc_145(startPosition, currentPosition() - startPosition, source, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code adc  }<i>label</i>, <i>source</i>
     * Example disassembly syntax: {@code adc       [L1: +305419896], rax}
     */
    // Template#: 11, Serial#: 154
    public void rip_adc(final Label label, final AMD64GeneralRegister64 source) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_adc(placeHolder, source);
        new rip_adc_154(startPosition, currentPosition() - startPosition, source, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code adc  }<i>label</i>, <i>source</i>
     * Example disassembly syntax: {@code adc       [L1: +305419896], al}
     */
    // Template#: 12, Serial#: 118
    public void rip_adc(final Label label, final AMD64GeneralRegister8 source) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_adc(placeHolder, source);
        new rip_adc_118(startPosition, currentPosition() - startPosition, source, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code adcl  }<i>label</i>, <i>imm32</i>
     * Example disassembly syntax: {@code adcl      [L1: +305419896], 0x12345678}
     */
    // Template#: 13, Serial#: 724
    public void rip_adcl(final Label label, final int imm32) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_adcl(placeHolder, imm32);
        new rip_adcl_724(startPosition, currentPosition() - startPosition, imm32, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code adcq  }<i>label</i>, <i>imm32</i>
     * Example disassembly syntax: {@code adcq      [L1: +305419896], 0x12345678}
     */
    // Template#: 14, Serial#: 796
    public void rip_adcq(final Label label, final int imm32) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_adcq(placeHolder, imm32);
        new rip_adcq_796(startPosition, currentPosition() - startPosition, imm32, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code adcw  }<i>label</i>, <i>imm16</i>
     * Example disassembly syntax: {@code adcw      [L1: +305419896], 0x1234}
     */
    // Template#: 15, Serial#: 868
    public void rip_adcw(final Label label, final short imm16) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_adcw(placeHolder, imm16);
        new rip_adcw_868(startPosition, currentPosition() - startPosition, imm16, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code add  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code add       ax, [L1: +305419896]}
     */
    // Template#: 16, Serial#: 103
    public void rip_add(final AMD64GeneralRegister16 destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_add(destination, placeHolder);
        new rip_add_103(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code add  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code add       eax, [L1: +305419896]}
     */
    // Template#: 17, Serial#: 85
    public void rip_add(final AMD64GeneralRegister32 destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_add(destination, placeHolder);
        new rip_add_85(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code add  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code add       rax, [L1: +305419896]}
     */
    // Template#: 18, Serial#: 94
    public void rip_add(final AMD64GeneralRegister64 destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_add(destination, placeHolder);
        new rip_add_94(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code add  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code add       al, [L1: +305419896]}
     */
    // Template#: 19, Serial#: 58
    public void rip_add(final AMD64GeneralRegister8 destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_add(destination, placeHolder);
        new rip_add_58(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code addb  }<i>label</i>, <i>imm8</i>
     * Example disassembly syntax: {@code addb      [L1: +305419896], 0x12}
     */
    // Template#: 20, Serial#: 500
    public void rip_addb(final Label label, final byte imm8) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_addb(placeHolder, imm8);
        new rip_addb_500(startPosition, currentPosition() - startPosition, imm8, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code addl  }<i>label</i>, <i>imm8</i>
     * Example disassembly syntax: {@code addl      [L1: +305419896], 0x12}
     */
    // Template#: 21, Serial#: 932
    public void rip_addl(final Label label, final byte imm8) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_addl(placeHolder, imm8);
        new rip_addl_932(startPosition, currentPosition() - startPosition, imm8, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code addq  }<i>label</i>, <i>imm8</i>
     * Example disassembly syntax: {@code addq      [L1: +305419896], 0x12}
     */
    // Template#: 22, Serial#: 1004
    public void rip_addq(final Label label, final byte imm8) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_addq(placeHolder, imm8);
        new rip_addq_1004(startPosition, currentPosition() - startPosition, imm8, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code addw  }<i>label</i>, <i>imm8</i>
     * Example disassembly syntax: {@code addw      [L1: +305419896], 0x12}
     */
    // Template#: 23, Serial#: 1076
    public void rip_addw(final Label label, final byte imm8) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_addw(placeHolder, imm8);
        new rip_addw_1076(startPosition, currentPosition() - startPosition, imm8, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code add  }<i>label</i>, <i>source</i>
     * Example disassembly syntax: {@code add       [L1: +305419896], ax}
     */
    // Template#: 24, Serial#: 49
    public void rip_add(final Label label, final AMD64GeneralRegister16 source) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_add(placeHolder, source);
        new rip_add_49(startPosition, currentPosition() - startPosition, source, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code add  }<i>label</i>, <i>source</i>
     * Example disassembly syntax: {@code add       [L1: +305419896], eax}
     */
    // Template#: 25, Serial#: 31
    public void rip_add(final Label label, final AMD64GeneralRegister32 source) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_add(placeHolder, source);
        new rip_add_31(startPosition, currentPosition() - startPosition, source, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code add  }<i>label</i>, <i>source</i>
     * Example disassembly syntax: {@code add       [L1: +305419896], rax}
     */
    // Template#: 26, Serial#: 40
    public void rip_add(final Label label, final AMD64GeneralRegister64 source) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_add(placeHolder, source);
        new rip_add_40(startPosition, currentPosition() - startPosition, source, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code add  }<i>label</i>, <i>source</i>
     * Example disassembly syntax: {@code add       [L1: +305419896], al}
     */
    // Template#: 27, Serial#: 4
    public void rip_add(final Label label, final AMD64GeneralRegister8 source) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_add(placeHolder, source);
        new rip_add_4(startPosition, currentPosition() - startPosition, source, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code addl  }<i>label</i>, <i>imm32</i>
     * Example disassembly syntax: {@code addl      [L1: +305419896], 0x12345678}
     */
    // Template#: 28, Serial#: 716
    public void rip_addl(final Label label, final int imm32) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_addl(placeHolder, imm32);
        new rip_addl_716(startPosition, currentPosition() - startPosition, imm32, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code addq  }<i>label</i>, <i>imm32</i>
     * Example disassembly syntax: {@code addq      [L1: +305419896], 0x12345678}
     */
    // Template#: 29, Serial#: 788
    public void rip_addq(final Label label, final int imm32) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_addq(placeHolder, imm32);
        new rip_addq_788(startPosition, currentPosition() - startPosition, imm32, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code addw  }<i>label</i>, <i>imm16</i>
     * Example disassembly syntax: {@code addw      [L1: +305419896], 0x1234}
     */
    // Template#: 30, Serial#: 860
    public void rip_addw(final Label label, final short imm16) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_addw(placeHolder, imm16);
        new rip_addw_860(startPosition, currentPosition() - startPosition, imm16, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code addpd  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code addpd     xmm0, [L1: +305419896]}
     */
    // Template#: 31, Serial#: 10107
    public void rip_addpd(final AMD64XMMRegister destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_addpd(destination, placeHolder);
        new rip_addpd_10107(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code addps  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code addps     xmm0, [L1: +305419896]}
     */
    // Template#: 32, Serial#: 9963
    public void rip_addps(final AMD64XMMRegister destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_addps(destination, placeHolder);
        new rip_addps_9963(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code addsd  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code addsd     xmm0, [L1: +305419896]}
     */
    // Template#: 33, Serial#: 10251
    public void rip_addsd(final AMD64XMMRegister destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_addsd(destination, placeHolder);
        new rip_addsd_10251(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code addss  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code addss     xmm0, [L1: +305419896]}
     */
    // Template#: 34, Serial#: 10377
    public void rip_addss(final AMD64XMMRegister destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_addss(destination, placeHolder);
        new rip_addss_10377(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code addsubpd  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code addsubpd  xmm0, [L1: +305419896]}
     */
    // Template#: 35, Serial#: 8313
    public void rip_addsubpd(final AMD64XMMRegister destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_addsubpd(destination, placeHolder);
        new rip_addsubpd_8313(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code and  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code and       ax, [L1: +305419896]}
     */
    // Template#: 36, Serial#: 331
    public void rip_and(final AMD64GeneralRegister16 destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_and(destination, placeHolder);
        new rip_and_331(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code and  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code and       eax, [L1: +305419896]}
     */
    // Template#: 37, Serial#: 313
    public void rip_and(final AMD64GeneralRegister32 destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_and(destination, placeHolder);
        new rip_and_313(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code and  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code and       rax, [L1: +305419896]}
     */
    // Template#: 38, Serial#: 322
    public void rip_and(final AMD64GeneralRegister64 destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_and(destination, placeHolder);
        new rip_and_322(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code and  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code and       al, [L1: +305419896]}
     */
    // Template#: 39, Serial#: 286
    public void rip_and(final AMD64GeneralRegister8 destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_and(destination, placeHolder);
        new rip_and_286(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code andb  }<i>label</i>, <i>imm8</i>
     * Example disassembly syntax: {@code andb      [L1: +305419896], 0x12}
     */
    // Template#: 40, Serial#: 516
    public void rip_andb(final Label label, final byte imm8) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_andb(placeHolder, imm8);
        new rip_andb_516(startPosition, currentPosition() - startPosition, imm8, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code andl  }<i>label</i>, <i>imm8</i>
     * Example disassembly syntax: {@code andl      [L1: +305419896], 0x12}
     */
    // Template#: 41, Serial#: 948
    public void rip_andl(final Label label, final byte imm8) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_andl(placeHolder, imm8);
        new rip_andl_948(startPosition, currentPosition() - startPosition, imm8, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code andq  }<i>label</i>, <i>imm8</i>
     * Example disassembly syntax: {@code andq      [L1: +305419896], 0x12}
     */
    // Template#: 42, Serial#: 1020
    public void rip_andq(final Label label, final byte imm8) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_andq(placeHolder, imm8);
        new rip_andq_1020(startPosition, currentPosition() - startPosition, imm8, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code andw  }<i>label</i>, <i>imm8</i>
     * Example disassembly syntax: {@code andw      [L1: +305419896], 0x12}
     */
    // Template#: 43, Serial#: 1092
    public void rip_andw(final Label label, final byte imm8) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_andw(placeHolder, imm8);
        new rip_andw_1092(startPosition, currentPosition() - startPosition, imm8, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code and  }<i>label</i>, <i>source</i>
     * Example disassembly syntax: {@code and       [L1: +305419896], ax}
     */
    // Template#: 44, Serial#: 277
    public void rip_and(final Label label, final AMD64GeneralRegister16 source) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_and(placeHolder, source);
        new rip_and_277(startPosition, currentPosition() - startPosition, source, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code and  }<i>label</i>, <i>source</i>
     * Example disassembly syntax: {@code and       [L1: +305419896], eax}
     */
    // Template#: 45, Serial#: 259
    public void rip_and(final Label label, final AMD64GeneralRegister32 source) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_and(placeHolder, source);
        new rip_and_259(startPosition, currentPosition() - startPosition, source, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code and  }<i>label</i>, <i>source</i>
     * Example disassembly syntax: {@code and       [L1: +305419896], rax}
     */
    // Template#: 46, Serial#: 268
    public void rip_and(final Label label, final AMD64GeneralRegister64 source) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_and(placeHolder, source);
        new rip_and_268(startPosition, currentPosition() - startPosition, source, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code and  }<i>label</i>, <i>source</i>
     * Example disassembly syntax: {@code and       [L1: +305419896], al}
     */
    // Template#: 47, Serial#: 232
    public void rip_and(final Label label, final AMD64GeneralRegister8 source) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_and(placeHolder, source);
        new rip_and_232(startPosition, currentPosition() - startPosition, source, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code andl  }<i>label</i>, <i>imm32</i>
     * Example disassembly syntax: {@code andl      [L1: +305419896], 0x12345678}
     */
    // Template#: 48, Serial#: 732
    public void rip_andl(final Label label, final int imm32) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_andl(placeHolder, imm32);
        new rip_andl_732(startPosition, currentPosition() - startPosition, imm32, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code andq  }<i>label</i>, <i>imm32</i>
     * Example disassembly syntax: {@code andq      [L1: +305419896], 0x12345678}
     */
    // Template#: 49, Serial#: 804
    public void rip_andq(final Label label, final int imm32) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_andq(placeHolder, imm32);
        new rip_andq_804(startPosition, currentPosition() - startPosition, imm32, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code andw  }<i>label</i>, <i>imm16</i>
     * Example disassembly syntax: {@code andw      [L1: +305419896], 0x1234}
     */
    // Template#: 50, Serial#: 876
    public void rip_andw(final Label label, final short imm16) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_andw(placeHolder, imm16);
        new rip_andw_876(startPosition, currentPosition() - startPosition, imm16, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code andnpd  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code andnpd    xmm0, [L1: +305419896]}
     */
    // Template#: 51, Serial#: 6752
    public void rip_andnpd(final AMD64XMMRegister destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_andnpd(destination, placeHolder);
        new rip_andnpd_6752(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code andnps  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code andnps    xmm0, [L1: +305419896]}
     */
    // Template#: 52, Serial#: 6659
    public void rip_andnps(final AMD64XMMRegister destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_andnps(destination, placeHolder);
        new rip_andnps_6659(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code andpd  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code andpd     xmm0, [L1: +305419896]}
     */
    // Template#: 53, Serial#: 6734
    public void rip_andpd(final AMD64XMMRegister destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_andpd(destination, placeHolder);
        new rip_andpd_6734(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code andps  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code andps     xmm0, [L1: +305419896]}
     */
    // Template#: 54, Serial#: 6641
    public void rip_andps(final AMD64XMMRegister destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_andps(destination, placeHolder);
        new rip_andps_6641(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code bsf  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code bsf       ax, [L1: +305419896]}
     */
    // Template#: 55, Serial#: 11646
    public void rip_bsf(final AMD64GeneralRegister16 destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_bsf(destination, placeHolder);
        new rip_bsf_11646(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code bsf  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code bsf       eax, [L1: +305419896]}
     */
    // Template#: 56, Serial#: 11628
    public void rip_bsf(final AMD64GeneralRegister32 destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_bsf(destination, placeHolder);
        new rip_bsf_11628(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code bsf  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code bsf       rax, [L1: +305419896]}
     */
    // Template#: 57, Serial#: 11637
    public void rip_bsf(final AMD64GeneralRegister64 destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_bsf(destination, placeHolder);
        new rip_bsf_11637(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code bsr  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code bsr       ax, [L1: +305419896]}
     */
    // Template#: 58, Serial#: 11673
    public void rip_bsr(final AMD64GeneralRegister16 destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_bsr(destination, placeHolder);
        new rip_bsr_11673(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code bsr  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code bsr       eax, [L1: +305419896]}
     */
    // Template#: 59, Serial#: 11655
    public void rip_bsr(final AMD64GeneralRegister32 destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_bsr(destination, placeHolder);
        new rip_bsr_11655(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code bsr  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code bsr       rax, [L1: +305419896]}
     */
    // Template#: 60, Serial#: 11664
    public void rip_bsr(final AMD64GeneralRegister64 destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_bsr(destination, placeHolder);
        new rip_bsr_11664(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code bt  }<i>label</i>, <i>imm8</i>
     * Example disassembly syntax: {@code bt        [L1: +305419896], 0x12}
     */
    // Template#: 61, Serial#: 11493
    public void rip_bt(final Label label, final byte imm8) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_bt(placeHolder, imm8);
        new rip_bt_11493(startPosition, currentPosition() - startPosition, imm8, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code bt  }<i>label</i>, <i>source</i>
     * Example disassembly syntax: {@code bt        [L1: +305419896], ax}
     */
    // Template#: 62, Serial#: 7760
    public void rip_bt(final Label label, final AMD64GeneralRegister16 source) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_bt(placeHolder, source);
        new rip_bt_7760(startPosition, currentPosition() - startPosition, source, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code bt  }<i>label</i>, <i>source</i>
     * Example disassembly syntax: {@code bt        [L1: +305419896], eax}
     */
    // Template#: 63, Serial#: 7742
    public void rip_bt(final Label label, final AMD64GeneralRegister32 source) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_bt(placeHolder, source);
        new rip_bt_7742(startPosition, currentPosition() - startPosition, source, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code bt  }<i>label</i>, <i>source</i>
     * Example disassembly syntax: {@code bt        [L1: +305419896], rax}
     */
    // Template#: 64, Serial#: 7751
    public void rip_bt(final Label label, final AMD64GeneralRegister64 source) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_bt(placeHolder, source);
        new rip_bt_7751(startPosition, currentPosition() - startPosition, source, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code btc  }<i>label</i>, <i>imm8</i>
     * Example disassembly syntax: {@code btc       [L1: +305419896], 0x12}
     */
    // Template#: 65, Serial#: 11505
    public void rip_btc(final Label label, final byte imm8) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_btc(placeHolder, imm8);
        new rip_btc_11505(startPosition, currentPosition() - startPosition, imm8, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code btc  }<i>label</i>, <i>source</i>
     * Example disassembly syntax: {@code btc       [L1: +305419896], ax}
     */
    // Template#: 66, Serial#: 11619
    public void rip_btc(final Label label, final AMD64GeneralRegister16 source) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_btc(placeHolder, source);
        new rip_btc_11619(startPosition, currentPosition() - startPosition, source, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code btc  }<i>label</i>, <i>source</i>
     * Example disassembly syntax: {@code btc       [L1: +305419896], eax}
     */
    // Template#: 67, Serial#: 11601
    public void rip_btc(final Label label, final AMD64GeneralRegister32 source) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_btc(placeHolder, source);
        new rip_btc_11601(startPosition, currentPosition() - startPosition, source, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code btc  }<i>label</i>, <i>source</i>
     * Example disassembly syntax: {@code btc       [L1: +305419896], rax}
     */
    // Template#: 68, Serial#: 11610
    public void rip_btc(final Label label, final AMD64GeneralRegister64 source) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_btc(placeHolder, source);
        new rip_btc_11610(startPosition, currentPosition() - startPosition, source, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code btr  }<i>label</i>, <i>imm8</i>
     * Example disassembly syntax: {@code btr       [L1: +305419896], 0x12}
     */
    // Template#: 69, Serial#: 11501
    public void rip_btr(final Label label, final byte imm8) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_btr(placeHolder, imm8);
        new rip_btr_11501(startPosition, currentPosition() - startPosition, imm8, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code btr  }<i>label</i>, <i>source</i>
     * Example disassembly syntax: {@code btr       [L1: +305419896], ax}
     */
    // Template#: 70, Serial#: 7895
    public void rip_btr(final Label label, final AMD64GeneralRegister16 source) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_btr(placeHolder, source);
        new rip_btr_7895(startPosition, currentPosition() - startPosition, source, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code btr  }<i>label</i>, <i>source</i>
     * Example disassembly syntax: {@code btr       [L1: +305419896], eax}
     */
    // Template#: 71, Serial#: 7877
    public void rip_btr(final Label label, final AMD64GeneralRegister32 source) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_btr(placeHolder, source);
        new rip_btr_7877(startPosition, currentPosition() - startPosition, source, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code btr  }<i>label</i>, <i>source</i>
     * Example disassembly syntax: {@code btr       [L1: +305419896], rax}
     */
    // Template#: 72, Serial#: 7886
    public void rip_btr(final Label label, final AMD64GeneralRegister64 source) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_btr(placeHolder, source);
        new rip_btr_7886(startPosition, currentPosition() - startPosition, source, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code bts  }<i>label</i>, <i>imm8</i>
     * Example disassembly syntax: {@code bts       [L1: +305419896], 0x12}
     */
    // Template#: 73, Serial#: 11497
    public void rip_bts(final Label label, final byte imm8) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_bts(placeHolder, imm8);
        new rip_bts_11497(startPosition, currentPosition() - startPosition, imm8, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code bts  }<i>label</i>, <i>source</i>
     * Example disassembly syntax: {@code bts       [L1: +305419896], ax}
     */
    // Template#: 74, Serial#: 11274
    public void rip_bts(final Label label, final AMD64GeneralRegister16 source) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_bts(placeHolder, source);
        new rip_bts_11274(startPosition, currentPosition() - startPosition, source, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code bts  }<i>label</i>, <i>source</i>
     * Example disassembly syntax: {@code bts       [L1: +305419896], eax}
     */
    // Template#: 75, Serial#: 11256
    public void rip_bts(final Label label, final AMD64GeneralRegister32 source) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_bts(placeHolder, source);
        new rip_bts_11256(startPosition, currentPosition() - startPosition, source, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code bts  }<i>label</i>, <i>source</i>
     * Example disassembly syntax: {@code bts       [L1: +305419896], rax}
     */
    // Template#: 76, Serial#: 11265
    public void rip_bts(final Label label, final AMD64GeneralRegister64 source) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_bts(placeHolder, source);
        new rip_bts_11265(startPosition, currentPosition() - startPosition, source, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code call  }<i>label</i>
     * Example disassembly syntax: {@code call      L1: +305419896}
     */
    // Template#: 77, Serial#: 5288
    public void call(final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        call(placeHolder);
        new call_5288(startPosition, currentPosition() - startPosition, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code call  }<i>label</i>
     * Example disassembly syntax: {@code call      [L1: +305419896]}
     */
    // Template#: 78, Serial#: 5432
    public void rip_call(final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_call(placeHolder);
        new rip_call_5432(startPosition, currentPosition() - startPosition, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code clflush  }<i>label</i>
     * Example disassembly syntax: {@code clflush   [L1: +305419896]}
     */
    // Template#: 79, Serial#: 11353
    public void rip_clflush(final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_clflush(placeHolder);
        new rip_clflush_11353(startPosition, currentPosition() - startPosition, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code cmova  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code cmova     ax, [L1: +305419896]}
     */
    // Template#: 80, Serial#: 6575
    public void rip_cmova(final AMD64GeneralRegister16 destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_cmova(destination, placeHolder);
        new rip_cmova_6575(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code cmova  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code cmova     eax, [L1: +305419896]}
     */
    // Template#: 81, Serial#: 6557
    public void rip_cmova(final AMD64GeneralRegister32 destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_cmova(destination, placeHolder);
        new rip_cmova_6557(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code cmova  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code cmova     rax, [L1: +305419896]}
     */
    // Template#: 82, Serial#: 6566
    public void rip_cmova(final AMD64GeneralRegister64 destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_cmova(destination, placeHolder);
        new rip_cmova_6566(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code cmovae  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code cmovae    ax, [L1: +305419896]}
     */
    // Template#: 83, Serial#: 6467
    public void rip_cmovae(final AMD64GeneralRegister16 destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_cmovae(destination, placeHolder);
        new rip_cmovae_6467(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code cmovae  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code cmovae    eax, [L1: +305419896]}
     */
    // Template#: 84, Serial#: 6449
    public void rip_cmovae(final AMD64GeneralRegister32 destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_cmovae(destination, placeHolder);
        new rip_cmovae_6449(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code cmovae  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code cmovae    rax, [L1: +305419896]}
     */
    // Template#: 85, Serial#: 6458
    public void rip_cmovae(final AMD64GeneralRegister64 destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_cmovae(destination, placeHolder);
        new rip_cmovae_6458(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code cmovb  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code cmovb     ax, [L1: +305419896]}
     */
    // Template#: 86, Serial#: 6440
    public void rip_cmovb(final AMD64GeneralRegister16 destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_cmovb(destination, placeHolder);
        new rip_cmovb_6440(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code cmovb  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code cmovb     eax, [L1: +305419896]}
     */
    // Template#: 87, Serial#: 6422
    public void rip_cmovb(final AMD64GeneralRegister32 destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_cmovb(destination, placeHolder);
        new rip_cmovb_6422(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code cmovb  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code cmovb     rax, [L1: +305419896]}
     */
    // Template#: 88, Serial#: 6431
    public void rip_cmovb(final AMD64GeneralRegister64 destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_cmovb(destination, placeHolder);
        new rip_cmovb_6431(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code cmovbe  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code cmovbe    ax, [L1: +305419896]}
     */
    // Template#: 89, Serial#: 6548
    public void rip_cmovbe(final AMD64GeneralRegister16 destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_cmovbe(destination, placeHolder);
        new rip_cmovbe_6548(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code cmovbe  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code cmovbe    eax, [L1: +305419896]}
     */
    // Template#: 90, Serial#: 6530
    public void rip_cmovbe(final AMD64GeneralRegister32 destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_cmovbe(destination, placeHolder);
        new rip_cmovbe_6530(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code cmovbe  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code cmovbe    rax, [L1: +305419896]}
     */
    // Template#: 91, Serial#: 6539
    public void rip_cmovbe(final AMD64GeneralRegister64 destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_cmovbe(destination, placeHolder);
        new rip_cmovbe_6539(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code cmove  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code cmove     ax, [L1: +305419896]}
     */
    // Template#: 92, Serial#: 6494
    public void rip_cmove(final AMD64GeneralRegister16 destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_cmove(destination, placeHolder);
        new rip_cmove_6494(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code cmove  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code cmove     eax, [L1: +305419896]}
     */
    // Template#: 93, Serial#: 6476
    public void rip_cmove(final AMD64GeneralRegister32 destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_cmove(destination, placeHolder);
        new rip_cmove_6476(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code cmove  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code cmove     rax, [L1: +305419896]}
     */
    // Template#: 94, Serial#: 6485
    public void rip_cmove(final AMD64GeneralRegister64 destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_cmove(destination, placeHolder);
        new rip_cmove_6485(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code cmovg  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code cmovg     ax, [L1: +305419896]}
     */
    // Template#: 95, Serial#: 9954
    public void rip_cmovg(final AMD64GeneralRegister16 destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_cmovg(destination, placeHolder);
        new rip_cmovg_9954(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code cmovg  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code cmovg     eax, [L1: +305419896]}
     */
    // Template#: 96, Serial#: 9936
    public void rip_cmovg(final AMD64GeneralRegister32 destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_cmovg(destination, placeHolder);
        new rip_cmovg_9936(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code cmovg  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code cmovg     rax, [L1: +305419896]}
     */
    // Template#: 97, Serial#: 9945
    public void rip_cmovg(final AMD64GeneralRegister64 destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_cmovg(destination, placeHolder);
        new rip_cmovg_9945(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code cmovge  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code cmovge    ax, [L1: +305419896]}
     */
    // Template#: 98, Serial#: 9900
    public void rip_cmovge(final AMD64GeneralRegister16 destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_cmovge(destination, placeHolder);
        new rip_cmovge_9900(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code cmovge  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code cmovge    eax, [L1: +305419896]}
     */
    // Template#: 99, Serial#: 9882
    public void rip_cmovge(final AMD64GeneralRegister32 destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_cmovge(destination, placeHolder);
        new rip_cmovge_9882(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code cmovge  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code cmovge    rax, [L1: +305419896]}
     */
    // Template#: 100, Serial#: 9891
    public void rip_cmovge(final AMD64GeneralRegister64 destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_cmovge(destination, placeHolder);
        new rip_cmovge_9891(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code cmovl  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code cmovl     ax, [L1: +305419896]}
     */
    // Template#: 101, Serial#: 9873
    public void rip_cmovl(final AMD64GeneralRegister16 destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_cmovl(destination, placeHolder);
        new rip_cmovl_9873(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code cmovl  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code cmovl     eax, [L1: +305419896]}
     */
    // Template#: 102, Serial#: 9855
    public void rip_cmovl(final AMD64GeneralRegister32 destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_cmovl(destination, placeHolder);
        new rip_cmovl_9855(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code cmovl  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code cmovl     rax, [L1: +305419896]}
     */
    // Template#: 103, Serial#: 9864
    public void rip_cmovl(final AMD64GeneralRegister64 destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_cmovl(destination, placeHolder);
        new rip_cmovl_9864(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code cmovle  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code cmovle    ax, [L1: +305419896]}
     */
    // Template#: 104, Serial#: 9927
    public void rip_cmovle(final AMD64GeneralRegister16 destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_cmovle(destination, placeHolder);
        new rip_cmovle_9927(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code cmovle  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code cmovle    eax, [L1: +305419896]}
     */
    // Template#: 105, Serial#: 9909
    public void rip_cmovle(final AMD64GeneralRegister32 destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_cmovle(destination, placeHolder);
        new rip_cmovle_9909(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code cmovle  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code cmovle    rax, [L1: +305419896]}
     */
    // Template#: 106, Serial#: 9918
    public void rip_cmovle(final AMD64GeneralRegister64 destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_cmovle(destination, placeHolder);
        new rip_cmovle_9918(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code cmovne  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code cmovne    ax, [L1: +305419896]}
     */
    // Template#: 107, Serial#: 6521
    public void rip_cmovne(final AMD64GeneralRegister16 destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_cmovne(destination, placeHolder);
        new rip_cmovne_6521(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code cmovne  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code cmovne    eax, [L1: +305419896]}
     */
    // Template#: 108, Serial#: 6503
    public void rip_cmovne(final AMD64GeneralRegister32 destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_cmovne(destination, placeHolder);
        new rip_cmovne_6503(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code cmovne  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code cmovne    rax, [L1: +305419896]}
     */
    // Template#: 109, Serial#: 6512
    public void rip_cmovne(final AMD64GeneralRegister64 destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_cmovne(destination, placeHolder);
        new rip_cmovne_6512(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code cmovno  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code cmovno    ax, [L1: +305419896]}
     */
    // Template#: 110, Serial#: 6413
    public void rip_cmovno(final AMD64GeneralRegister16 destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_cmovno(destination, placeHolder);
        new rip_cmovno_6413(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code cmovno  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code cmovno    eax, [L1: +305419896]}
     */
    // Template#: 111, Serial#: 6395
    public void rip_cmovno(final AMD64GeneralRegister32 destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_cmovno(destination, placeHolder);
        new rip_cmovno_6395(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code cmovno  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code cmovno    rax, [L1: +305419896]}
     */
    // Template#: 112, Serial#: 6404
    public void rip_cmovno(final AMD64GeneralRegister64 destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_cmovno(destination, placeHolder);
        new rip_cmovno_6404(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code cmovnp  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code cmovnp    ax, [L1: +305419896]}
     */
    // Template#: 113, Serial#: 9846
    public void rip_cmovnp(final AMD64GeneralRegister16 destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_cmovnp(destination, placeHolder);
        new rip_cmovnp_9846(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code cmovnp  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code cmovnp    eax, [L1: +305419896]}
     */
    // Template#: 114, Serial#: 9828
    public void rip_cmovnp(final AMD64GeneralRegister32 destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_cmovnp(destination, placeHolder);
        new rip_cmovnp_9828(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code cmovnp  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code cmovnp    rax, [L1: +305419896]}
     */
    // Template#: 115, Serial#: 9837
    public void rip_cmovnp(final AMD64GeneralRegister64 destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_cmovnp(destination, placeHolder);
        new rip_cmovnp_9837(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code cmovns  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code cmovns    ax, [L1: +305419896]}
     */
    // Template#: 116, Serial#: 9792
    public void rip_cmovns(final AMD64GeneralRegister16 destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_cmovns(destination, placeHolder);
        new rip_cmovns_9792(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code cmovns  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code cmovns    eax, [L1: +305419896]}
     */
    // Template#: 117, Serial#: 9774
    public void rip_cmovns(final AMD64GeneralRegister32 destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_cmovns(destination, placeHolder);
        new rip_cmovns_9774(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code cmovns  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code cmovns    rax, [L1: +305419896]}
     */
    // Template#: 118, Serial#: 9783
    public void rip_cmovns(final AMD64GeneralRegister64 destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_cmovns(destination, placeHolder);
        new rip_cmovns_9783(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code cmovo  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code cmovo     ax, [L1: +305419896]}
     */
    // Template#: 119, Serial#: 6386
    public void rip_cmovo(final AMD64GeneralRegister16 destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_cmovo(destination, placeHolder);
        new rip_cmovo_6386(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code cmovo  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code cmovo     eax, [L1: +305419896]}
     */
    // Template#: 120, Serial#: 6368
    public void rip_cmovo(final AMD64GeneralRegister32 destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_cmovo(destination, placeHolder);
        new rip_cmovo_6368(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code cmovo  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code cmovo     rax, [L1: +305419896]}
     */
    // Template#: 121, Serial#: 6377
    public void rip_cmovo(final AMD64GeneralRegister64 destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_cmovo(destination, placeHolder);
        new rip_cmovo_6377(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code cmovp  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code cmovp     ax, [L1: +305419896]}
     */
    // Template#: 122, Serial#: 9819
    public void rip_cmovp(final AMD64GeneralRegister16 destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_cmovp(destination, placeHolder);
        new rip_cmovp_9819(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code cmovp  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code cmovp     eax, [L1: +305419896]}
     */
    // Template#: 123, Serial#: 9801
    public void rip_cmovp(final AMD64GeneralRegister32 destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_cmovp(destination, placeHolder);
        new rip_cmovp_9801(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code cmovp  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code cmovp     rax, [L1: +305419896]}
     */
    // Template#: 124, Serial#: 9810
    public void rip_cmovp(final AMD64GeneralRegister64 destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_cmovp(destination, placeHolder);
        new rip_cmovp_9810(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code cmovs  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code cmovs     ax, [L1: +305419896]}
     */
    // Template#: 125, Serial#: 9765
    public void rip_cmovs(final AMD64GeneralRegister16 destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_cmovs(destination, placeHolder);
        new rip_cmovs_9765(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code cmovs  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code cmovs     eax, [L1: +305419896]}
     */
    // Template#: 126, Serial#: 9747
    public void rip_cmovs(final AMD64GeneralRegister32 destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_cmovs(destination, placeHolder);
        new rip_cmovs_9747(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code cmovs  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code cmovs     rax, [L1: +305419896]}
     */
    // Template#: 127, Serial#: 9756
    public void rip_cmovs(final AMD64GeneralRegister64 destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_cmovs(destination, placeHolder);
        new rip_cmovs_9756(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code cmp  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code cmp       ax, [L1: +305419896]}
     */
    // Template#: 128, Serial#: 3563
    public void rip_cmp(final AMD64GeneralRegister16 destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_cmp(destination, placeHolder);
        new rip_cmp_3563(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code cmp  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code cmp       eax, [L1: +305419896]}
     */
    // Template#: 129, Serial#: 3545
    public void rip_cmp(final AMD64GeneralRegister32 destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_cmp(destination, placeHolder);
        new rip_cmp_3545(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code cmp  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code cmp       rax, [L1: +305419896]}
     */
    // Template#: 130, Serial#: 3554
    public void rip_cmp(final AMD64GeneralRegister64 destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_cmp(destination, placeHolder);
        new rip_cmp_3554(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code cmp  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code cmp       al, [L1: +305419896]}
     */
    // Template#: 131, Serial#: 3518
    public void rip_cmp(final AMD64GeneralRegister8 destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_cmp(destination, placeHolder);
        new rip_cmp_3518(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code cmpb  }<i>label</i>, <i>imm8</i>
     * Example disassembly syntax: {@code cmpb      [L1: +305419896], 0x12}
     */
    // Template#: 132, Serial#: 528
    public void rip_cmpb(final Label label, final byte imm8) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_cmpb(placeHolder, imm8);
        new rip_cmpb_528(startPosition, currentPosition() - startPosition, imm8, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code cmpl  }<i>label</i>, <i>imm8</i>
     * Example disassembly syntax: {@code cmpl      [L1: +305419896], 0x12}
     */
    // Template#: 133, Serial#: 960
    public void rip_cmpl(final Label label, final byte imm8) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_cmpl(placeHolder, imm8);
        new rip_cmpl_960(startPosition, currentPosition() - startPosition, imm8, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code cmpq  }<i>label</i>, <i>imm8</i>
     * Example disassembly syntax: {@code cmpq      [L1: +305419896], 0x12}
     */
    // Template#: 134, Serial#: 1032
    public void rip_cmpq(final Label label, final byte imm8) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_cmpq(placeHolder, imm8);
        new rip_cmpq_1032(startPosition, currentPosition() - startPosition, imm8, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code cmpw  }<i>label</i>, <i>imm8</i>
     * Example disassembly syntax: {@code cmpw      [L1: +305419896], 0x12}
     */
    // Template#: 135, Serial#: 1104
    public void rip_cmpw(final Label label, final byte imm8) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_cmpw(placeHolder, imm8);
        new rip_cmpw_1104(startPosition, currentPosition() - startPosition, imm8, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code cmp  }<i>label</i>, <i>source</i>
     * Example disassembly syntax: {@code cmp       [L1: +305419896], ax}
     */
    // Template#: 136, Serial#: 3509
    public void rip_cmp(final Label label, final AMD64GeneralRegister16 source) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_cmp(placeHolder, source);
        new rip_cmp_3509(startPosition, currentPosition() - startPosition, source, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code cmp  }<i>label</i>, <i>source</i>
     * Example disassembly syntax: {@code cmp       [L1: +305419896], eax}
     */
    // Template#: 137, Serial#: 3491
    public void rip_cmp(final Label label, final AMD64GeneralRegister32 source) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_cmp(placeHolder, source);
        new rip_cmp_3491(startPosition, currentPosition() - startPosition, source, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code cmp  }<i>label</i>, <i>source</i>
     * Example disassembly syntax: {@code cmp       [L1: +305419896], rax}
     */
    // Template#: 138, Serial#: 3500
    public void rip_cmp(final Label label, final AMD64GeneralRegister64 source) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_cmp(placeHolder, source);
        new rip_cmp_3500(startPosition, currentPosition() - startPosition, source, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code cmp  }<i>label</i>, <i>source</i>
     * Example disassembly syntax: {@code cmp       [L1: +305419896], al}
     */
    // Template#: 139, Serial#: 3464
    public void rip_cmp(final Label label, final AMD64GeneralRegister8 source) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_cmp(placeHolder, source);
        new rip_cmp_3464(startPosition, currentPosition() - startPosition, source, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code cmpl  }<i>label</i>, <i>imm32</i>
     * Example disassembly syntax: {@code cmpl      [L1: +305419896], 0x12345678}
     */
    // Template#: 140, Serial#: 744
    public void rip_cmpl(final Label label, final int imm32) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_cmpl(placeHolder, imm32);
        new rip_cmpl_744(startPosition, currentPosition() - startPosition, imm32, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code cmpq  }<i>label</i>, <i>imm32</i>
     * Example disassembly syntax: {@code cmpq      [L1: +305419896], 0x12345678}
     */
    // Template#: 141, Serial#: 816
    public void rip_cmpq(final Label label, final int imm32) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_cmpq(placeHolder, imm32);
        new rip_cmpq_816(startPosition, currentPosition() - startPosition, imm32, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code cmpw  }<i>label</i>, <i>imm16</i>
     * Example disassembly syntax: {@code cmpw      [L1: +305419896], 0x1234}
     */
    // Template#: 142, Serial#: 888
    public void rip_cmpw(final Label label, final short imm16) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_cmpw(placeHolder, imm16);
        new rip_cmpw_888(startPosition, currentPosition() - startPosition, imm16, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code cmppd  }<i>destination</i>, <i>label</i>, <i>amd64xmmcomparison</i>
     * Example disassembly syntax: {@code cmppd     xmm0, [L1: +305419896], less_than_or_equal}
     */
    // Template#: 143, Serial#: 8091
    public void rip_cmppd(final AMD64XMMRegister destination, final Label label, final AMD64XMMComparison amd64xmmcomparison) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_cmppd(destination, placeHolder, amd64xmmcomparison);
        new rip_cmppd_8091(startPosition, currentPosition() - startPosition, destination, amd64xmmcomparison, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code cmpps  }<i>destination</i>, <i>label</i>, <i>amd64xmmcomparison</i>
     * Example disassembly syntax: {@code cmpps     xmm0, [L1: +305419896], less_than_or_equal}
     */
    // Template#: 144, Serial#: 8003
    public void rip_cmpps(final AMD64XMMRegister destination, final Label label, final AMD64XMMComparison amd64xmmcomparison) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_cmpps(destination, placeHolder, amd64xmmcomparison);
        new rip_cmpps_8003(startPosition, currentPosition() - startPosition, destination, amd64xmmcomparison, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code cmpsd  }<i>destination</i>, <i>label</i>, <i>amd64xmmcomparison</i>
     * Example disassembly syntax: {@code cmpsd     xmm0, [L1: +305419896], less_than_or_equal}
     */
    // Template#: 145, Serial#: 8139
    public void rip_cmpsd(final AMD64XMMRegister destination, final Label label, final AMD64XMMComparison amd64xmmcomparison) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_cmpsd(destination, placeHolder, amd64xmmcomparison);
        new rip_cmpsd_8139(startPosition, currentPosition() - startPosition, destination, amd64xmmcomparison, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code cmpss  }<i>destination</i>, <i>label</i>, <i>amd64xmmcomparison</i>
     * Example disassembly syntax: {@code cmpss     xmm0, [L1: +305419896], less_than_or_equal}
     */
    // Template#: 146, Serial#: 8157
    public void rip_cmpss(final AMD64XMMRegister destination, final Label label, final AMD64XMMComparison amd64xmmcomparison) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_cmpss(destination, placeHolder, amd64xmmcomparison);
        new rip_cmpss_8157(startPosition, currentPosition() - startPosition, destination, amd64xmmcomparison, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code cmpxchg  }<i>label</i>, <i>source</i>
     * Example disassembly syntax: {@code cmpxchg   [L1: +305419896], ax}
     */
    // Template#: 147, Serial#: 7868
    public void rip_cmpxchg(final Label label, final AMD64GeneralRegister16 source) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_cmpxchg(placeHolder, source);
        new rip_cmpxchg_7868(startPosition, currentPosition() - startPosition, source, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code cmpxchg  }<i>label</i>, <i>source</i>
     * Example disassembly syntax: {@code cmpxchg   [L1: +305419896], eax}
     */
    // Template#: 148, Serial#: 7850
    public void rip_cmpxchg(final Label label, final AMD64GeneralRegister32 source) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_cmpxchg(placeHolder, source);
        new rip_cmpxchg_7850(startPosition, currentPosition() - startPosition, source, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code cmpxchg  }<i>label</i>, <i>source</i>
     * Example disassembly syntax: {@code cmpxchg   [L1: +305419896], rax}
     */
    // Template#: 149, Serial#: 7859
    public void rip_cmpxchg(final Label label, final AMD64GeneralRegister64 source) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_cmpxchg(placeHolder, source);
        new rip_cmpxchg_7859(startPosition, currentPosition() - startPosition, source, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code cmpxchg  }<i>label</i>, <i>source</i>
     * Example disassembly syntax: {@code cmpxchg   [L1: +305419896], al}
     */
    // Template#: 150, Serial#: 7823
    public void rip_cmpxchg(final Label label, final AMD64GeneralRegister8 source) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_cmpxchg(placeHolder, source);
        new rip_cmpxchg_7823(startPosition, currentPosition() - startPosition, source, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code cmpxchg16b  }<i>label</i>
     * Example disassembly syntax: {@code cmpxchg16b  [L1: +305419896]}
     */
    // Template#: 151, Serial#: 8067
    public void rip_cmpxchg16b(final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_cmpxchg16b(placeHolder);
        new rip_cmpxchg16b_8067(startPosition, currentPosition() - startPosition, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code comisd  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code comisd    xmm0, [L1: +305419896]}
     */
    // Template#: 152, Serial#: 9621
    public void rip_comisd(final AMD64XMMRegister destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_comisd(destination, placeHolder);
        new rip_comisd_9621(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code comiss  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code comiss    xmm0, [L1: +305419896]}
     */
    // Template#: 153, Serial#: 9462
    public void rip_comiss(final AMD64XMMRegister destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_comiss(destination, placeHolder);
        new rip_comiss_9462(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code cvtdq2pd  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code cvtdq2pd  xmm0, [L1: +305419896]}
     */
    // Template#: 154, Serial#: 8802
    public void rip_cvtdq2pd(final AMD64XMMRegister destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_cvtdq2pd(destination, placeHolder);
        new rip_cvtdq2pd_8802(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code cvtdq2ps  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code cvtdq2ps  xmm0, [L1: +305419896]}
     */
    // Template#: 155, Serial#: 10017
    public void rip_cvtdq2ps(final AMD64XMMRegister destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_cvtdq2ps(destination, placeHolder);
        new rip_cvtdq2ps_10017(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code cvtpd2dq  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code cvtpd2dq  xmm0, [L1: +305419896]}
     */
    // Template#: 156, Serial#: 8784
    public void rip_cvtpd2dq(final AMD64XMMRegister destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_cvtpd2dq(destination, placeHolder);
        new rip_cvtpd2dq_8784(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code cvtpd2pi  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code cvtpd2pi  mm0, [L1: +305419896]}
     */
    // Template#: 157, Serial#: 9585
    public void rip_cvtpd2pi(final MMXRegister destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_cvtpd2pi(destination, placeHolder);
        new rip_cvtpd2pi_9585(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code cvtpd2ps  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code cvtpd2ps  xmm0, [L1: +305419896]}
     */
    // Template#: 158, Serial#: 10143
    public void rip_cvtpd2ps(final AMD64XMMRegister destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_cvtpd2ps(destination, placeHolder);
        new rip_cvtpd2ps_10143(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code cvtpi2pd  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code cvtpi2pd  xmm0, [L1: +305419896]}
     */
    // Template#: 159, Serial#: 9516
    public void rip_cvtpi2pd(final AMD64XMMRegister destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_cvtpi2pd(destination, placeHolder);
        new rip_cvtpi2pd_9516(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code cvtpi2ps  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code cvtpi2ps  xmm0, [L1: +305419896]}
     */
    // Template#: 160, Serial#: 9357
    public void rip_cvtpi2ps(final AMD64XMMRegister destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_cvtpi2ps(destination, placeHolder);
        new rip_cvtpi2ps_9357(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code cvtps2dq  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code cvtps2dq  xmm0, [L1: +305419896]}
     */
    // Template#: 161, Serial#: 10161
    public void rip_cvtps2dq(final AMD64XMMRegister destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_cvtps2dq(destination, placeHolder);
        new rip_cvtps2dq_10161(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code cvtps2pd  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code cvtps2pd  xmm0, [L1: +305419896]}
     */
    // Template#: 162, Serial#: 9999
    public void rip_cvtps2pd(final AMD64XMMRegister destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_cvtps2pd(destination, placeHolder);
        new rip_cvtps2pd_9999(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code cvtps2pi  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code cvtps2pi  mm0, [L1: +305419896]}
     */
    // Template#: 163, Serial#: 9426
    public void rip_cvtps2pi(final MMXRegister destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_cvtps2pi(destination, placeHolder);
        new rip_cvtps2pi_9426(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code cvtsd2si  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code cvtsd2si  eax, [L1: +305419896]}
     */
    // Template#: 164, Serial#: 9675
    public void rip_cvtsd2si(final AMD64GeneralRegister32 destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_cvtsd2si(destination, placeHolder);
        new rip_cvtsd2si_9675(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code cvtsd2si  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code cvtsd2si  rax, [L1: +305419896]}
     */
    // Template#: 165, Serial#: 9684
    public void rip_cvtsd2si(final AMD64GeneralRegister64 destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_cvtsd2si(destination, placeHolder);
        new rip_cvtsd2si_9684(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code cvtsd2ss  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code cvtsd2ss  xmm0, [L1: +305419896]}
     */
    // Template#: 166, Serial#: 10287
    public void rip_cvtsd2ss(final AMD64XMMRegister destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_cvtsd2ss(destination, placeHolder);
        new rip_cvtsd2ss_10287(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code cvtsi2sdl  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code cvtsi2sdl  xmm0, [L1: +305419896]}
     */
    // Template#: 167, Serial#: 9639
    public void rip_cvtsi2sdl(final AMD64XMMRegister destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_cvtsi2sdl(destination, placeHolder);
        new rip_cvtsi2sdl_9639(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code cvtsi2sdq  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code cvtsi2sdq  xmm0, [L1: +305419896]}
     */
    // Template#: 168, Serial#: 9648
    public void rip_cvtsi2sdq(final AMD64XMMRegister destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_cvtsi2sdq(destination, placeHolder);
        new rip_cvtsi2sdq_9648(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code cvtsi2ssl  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code cvtsi2ssl  xmm0, [L1: +305419896]}
     */
    // Template#: 169, Serial#: 9693
    public void rip_cvtsi2ssl(final AMD64XMMRegister destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_cvtsi2ssl(destination, placeHolder);
        new rip_cvtsi2ssl_9693(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code cvtsi2ssq  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code cvtsi2ssq  xmm0, [L1: +305419896]}
     */
    // Template#: 170, Serial#: 9702
    public void rip_cvtsi2ssq(final AMD64XMMRegister destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_cvtsi2ssq(destination, placeHolder);
        new rip_cvtsi2ssq_9702(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code cvtss2sd  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code cvtss2sd  xmm0, [L1: +305419896]}
     */
    // Template#: 171, Serial#: 10413
    public void rip_cvtss2sd(final AMD64XMMRegister destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_cvtss2sd(destination, placeHolder);
        new rip_cvtss2sd_10413(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code cvtss2si  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code cvtss2si  eax, [L1: +305419896]}
     */
    // Template#: 172, Serial#: 9729
    public void rip_cvtss2si(final AMD64GeneralRegister32 destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_cvtss2si(destination, placeHolder);
        new rip_cvtss2si_9729(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code cvtss2si  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code cvtss2si  rax, [L1: +305419896]}
     */
    // Template#: 173, Serial#: 9738
    public void rip_cvtss2si(final AMD64GeneralRegister64 destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_cvtss2si(destination, placeHolder);
        new rip_cvtss2si_9738(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code cvttpd2dq  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code cvttpd2dq  xmm0, [L1: +305419896]}
     */
    // Template#: 174, Serial#: 8742
    public void rip_cvttpd2dq(final AMD64XMMRegister destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_cvttpd2dq(destination, placeHolder);
        new rip_cvttpd2dq_8742(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code cvttpd2pi  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code cvttpd2pi  mm0, [L1: +305419896]}
     */
    // Template#: 175, Serial#: 9567
    public void rip_cvttpd2pi(final MMXRegister destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_cvttpd2pi(destination, placeHolder);
        new rip_cvttpd2pi_9567(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code cvttps2dq  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code cvttps2dq  xmm0, [L1: +305419896]}
     */
    // Template#: 176, Serial#: 10431
    public void rip_cvttps2dq(final AMD64XMMRegister destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_cvttps2dq(destination, placeHolder);
        new rip_cvttps2dq_10431(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code cvttps2pi  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code cvttps2pi  mm0, [L1: +305419896]}
     */
    // Template#: 177, Serial#: 9408
    public void rip_cvttps2pi(final MMXRegister destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_cvttps2pi(destination, placeHolder);
        new rip_cvttps2pi_9408(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code cvttsd2si  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code cvttsd2si  eax, [L1: +305419896]}
     */
    // Template#: 178, Serial#: 9657
    public void rip_cvttsd2si(final AMD64GeneralRegister32 destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_cvttsd2si(destination, placeHolder);
        new rip_cvttsd2si_9657(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code cvttsd2si  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code cvttsd2si  rax, [L1: +305419896]}
     */
    // Template#: 179, Serial#: 9666
    public void rip_cvttsd2si(final AMD64GeneralRegister64 destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_cvttsd2si(destination, placeHolder);
        new rip_cvttsd2si_9666(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code cvttss2si  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code cvttss2si  eax, [L1: +305419896]}
     */
    // Template#: 180, Serial#: 9711
    public void rip_cvttss2si(final AMD64GeneralRegister32 destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_cvttss2si(destination, placeHolder);
        new rip_cvttss2si_9711(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code cvttss2si  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code cvttss2si  rax, [L1: +305419896]}
     */
    // Template#: 181, Serial#: 9720
    public void rip_cvttss2si(final AMD64GeneralRegister64 destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_cvttss2si(destination, placeHolder);
        new rip_cvttss2si_9720(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code decb  }<i>label</i>
     * Example disassembly syntax: {@code decb      [L1: +305419896]}
     */
    // Template#: 182, Serial#: 5328
    public void rip_decb(final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_decb(placeHolder);
        new rip_decb_5328(startPosition, currentPosition() - startPosition, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code decl  }<i>label</i>
     * Example disassembly syntax: {@code decl      [L1: +305419896]}
     */
    // Template#: 183, Serial#: 5382
    public void rip_decl(final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_decl(placeHolder);
        new rip_decl_5382(startPosition, currentPosition() - startPosition, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code decq  }<i>label</i>
     * Example disassembly syntax: {@code decq      [L1: +305419896]}
     */
    // Template#: 184, Serial#: 5400
    public void rip_decq(final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_decq(placeHolder);
        new rip_decq_5400(startPosition, currentPosition() - startPosition, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code decw  }<i>label</i>
     * Example disassembly syntax: {@code decw      [L1: +305419896]}
     */
    // Template#: 185, Serial#: 5418
    public void rip_decw(final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_decw(placeHolder);
        new rip_decw_5418(startPosition, currentPosition() - startPosition, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code divb  }<i>label</i>
     * Example disassembly syntax: {@code divb      [L1: +305419896], al}
     */
    // Template#: 186, Serial#: 2708
    public void rip_divb___AL(final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_divb___AL(placeHolder);
        new rip_divb___AL_2708(startPosition, currentPosition() - startPosition, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code divl  }<i>label</i>
     * Example disassembly syntax: {@code divl      [L1: +305419896]}
     */
    // Template#: 187, Serial#: 2924
    public void rip_divl(final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_divl(placeHolder);
        new rip_divl_2924(startPosition, currentPosition() - startPosition, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code divq  }<i>label</i>
     * Example disassembly syntax: {@code divq      [L1: +305419896]}
     */
    // Template#: 188, Serial#: 2996
    public void rip_divq(final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_divq(placeHolder);
        new rip_divq_2996(startPosition, currentPosition() - startPosition, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code divw  }<i>label</i>
     * Example disassembly syntax: {@code divw      [L1: +305419896]}
     */
    // Template#: 189, Serial#: 3068
    public void rip_divw(final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_divw(placeHolder);
        new rip_divw_3068(startPosition, currentPosition() - startPosition, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code divpd  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code divpd     xmm0, [L1: +305419896]}
     */
    // Template#: 190, Serial#: 10215
    public void rip_divpd(final AMD64XMMRegister destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_divpd(destination, placeHolder);
        new rip_divpd_10215(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code divps  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code divps     xmm0, [L1: +305419896]}
     */
    // Template#: 191, Serial#: 10071
    public void rip_divps(final AMD64XMMRegister destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_divps(destination, placeHolder);
        new rip_divps_10071(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code divsd  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code divsd     xmm0, [L1: +305419896]}
     */
    // Template#: 192, Serial#: 10341
    public void rip_divsd(final AMD64XMMRegister destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_divsd(destination, placeHolder);
        new rip_divsd_10341(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code divss  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code divss     xmm0, [L1: +305419896]}
     */
    // Template#: 193, Serial#: 10485
    public void rip_divss(final AMD64XMMRegister destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_divss(destination, placeHolder);
        new rip_divss_10485(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code fadds  }<i>label</i>
     * Example disassembly syntax: {@code fadds     [L1: +305419896]}
     */
    // Template#: 194, Serial#: 3923
    public void rip_fadds(final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_fadds(placeHolder);
        new rip_fadds_3923(startPosition, currentPosition() - startPosition, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code faddl  }<i>label</i>
     * Example disassembly syntax: {@code faddl     [L1: +305419896]}
     */
    // Template#: 195, Serial#: 4595
    public void rip_faddl(final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_faddl(placeHolder);
        new rip_faddl_4595(startPosition, currentPosition() - startPosition, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code fbld  }<i>label</i>
     * Example disassembly syntax: {@code fbld      [L1: +305419896]}
     */
    // Template#: 196, Serial#: 5135
    public void rip_fbld(final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_fbld(placeHolder);
        new rip_fbld_5135(startPosition, currentPosition() - startPosition, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code fbstp  }<i>label</i>
     * Example disassembly syntax: {@code fbstp     [L1: +305419896]}
     */
    // Template#: 197, Serial#: 5143
    public void rip_fbstp(final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_fbstp(placeHolder);
        new rip_fbstp_5143(startPosition, currentPosition() - startPosition, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code fcoms  }<i>label</i>
     * Example disassembly syntax: {@code fcoms     [L1: +305419896]}
     */
    // Template#: 198, Serial#: 3931
    public void rip_fcoms(final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_fcoms(placeHolder);
        new rip_fcoms_3931(startPosition, currentPosition() - startPosition, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code fcoml  }<i>label</i>
     * Example disassembly syntax: {@code fcoml     [L1: +305419896]}
     */
    // Template#: 199, Serial#: 4603
    public void rip_fcoml(final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_fcoml(placeHolder);
        new rip_fcoml_4603(startPosition, currentPosition() - startPosition, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code fcomps  }<i>label</i>
     * Example disassembly syntax: {@code fcomps    [L1: +305419896]}
     */
    // Template#: 200, Serial#: 3935
    public void rip_fcomps(final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_fcomps(placeHolder);
        new rip_fcomps_3935(startPosition, currentPosition() - startPosition, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code fcompl  }<i>label</i>
     * Example disassembly syntax: {@code fcompl    [L1: +305419896]}
     */
    // Template#: 201, Serial#: 4607
    public void rip_fcompl(final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_fcompl(placeHolder);
        new rip_fcompl_4607(startPosition, currentPosition() - startPosition, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code fdivs  }<i>label</i>
     * Example disassembly syntax: {@code fdivs     [L1: +305419896]}
     */
    // Template#: 202, Serial#: 3947
    public void rip_fdivs(final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_fdivs(placeHolder);
        new rip_fdivs_3947(startPosition, currentPosition() - startPosition, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code fdivl  }<i>label</i>
     * Example disassembly syntax: {@code fdivl     [L1: +305419896]}
     */
    // Template#: 203, Serial#: 4619
    public void rip_fdivl(final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_fdivl(placeHolder);
        new rip_fdivl_4619(startPosition, currentPosition() - startPosition, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code fdivrs  }<i>label</i>
     * Example disassembly syntax: {@code fdivrs    [L1: +305419896]}
     */
    // Template#: 204, Serial#: 3951
    public void rip_fdivrs(final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_fdivrs(placeHolder);
        new rip_fdivrs_3951(startPosition, currentPosition() - startPosition, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code fdivrl  }<i>label</i>
     * Example disassembly syntax: {@code fdivrl    [L1: +305419896]}
     */
    // Template#: 205, Serial#: 4623
    public void rip_fdivrl(final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_fdivrl(placeHolder);
        new rip_fdivrl_4623(startPosition, currentPosition() - startPosition, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code fiaddl  }<i>label</i>
     * Example disassembly syntax: {@code fiaddl    [L1: +305419896]}
     */
    // Template#: 206, Serial#: 4283
    public void rip_fiaddl(final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_fiaddl(placeHolder);
        new rip_fiaddl_4283(startPosition, currentPosition() - startPosition, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code fiadds  }<i>label</i>
     * Example disassembly syntax: {@code fiadds    [L1: +305419896]}
     */
    // Template#: 207, Serial#: 4931
    public void rip_fiadds(final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_fiadds(placeHolder);
        new rip_fiadds_4931(startPosition, currentPosition() - startPosition, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code ficoml  }<i>label</i>
     * Example disassembly syntax: {@code ficoml    [L1: +305419896]}
     */
    // Template#: 208, Serial#: 4291
    public void rip_ficoml(final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_ficoml(placeHolder);
        new rip_ficoml_4291(startPosition, currentPosition() - startPosition, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code ficoms  }<i>label</i>
     * Example disassembly syntax: {@code ficoms    [L1: +305419896]}
     */
    // Template#: 209, Serial#: 4939
    public void rip_ficoms(final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_ficoms(placeHolder);
        new rip_ficoms_4939(startPosition, currentPosition() - startPosition, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code ficompl  }<i>label</i>
     * Example disassembly syntax: {@code ficompl   [L1: +305419896]}
     */
    // Template#: 210, Serial#: 4295
    public void rip_ficompl(final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_ficompl(placeHolder);
        new rip_ficompl_4295(startPosition, currentPosition() - startPosition, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code ficomps  }<i>label</i>
     * Example disassembly syntax: {@code ficomps   [L1: +305419896]}
     */
    // Template#: 211, Serial#: 4943
    public void rip_ficomps(final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_ficomps(placeHolder);
        new rip_ficomps_4943(startPosition, currentPosition() - startPosition, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code fidivl  }<i>label</i>
     * Example disassembly syntax: {@code fidivl    [L1: +305419896]}
     */
    // Template#: 212, Serial#: 4307
    public void rip_fidivl(final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_fidivl(placeHolder);
        new rip_fidivl_4307(startPosition, currentPosition() - startPosition, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code fidivs  }<i>label</i>
     * Example disassembly syntax: {@code fidivs    [L1: +305419896]}
     */
    // Template#: 213, Serial#: 4955
    public void rip_fidivs(final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_fidivs(placeHolder);
        new rip_fidivs_4955(startPosition, currentPosition() - startPosition, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code fidivrl  }<i>label</i>
     * Example disassembly syntax: {@code fidivrl   [L1: +305419896]}
     */
    // Template#: 214, Serial#: 4311
    public void rip_fidivrl(final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_fidivrl(placeHolder);
        new rip_fidivrl_4311(startPosition, currentPosition() - startPosition, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code fidivrs  }<i>label</i>
     * Example disassembly syntax: {@code fidivrs   [L1: +305419896]}
     */
    // Template#: 215, Serial#: 4959
    public void rip_fidivrs(final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_fidivrs(placeHolder);
        new rip_fidivrs_4959(startPosition, currentPosition() - startPosition, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code fildl  }<i>label</i>
     * Example disassembly syntax: {@code fildl     [L1: +305419896]}
     */
    // Template#: 216, Serial#: 4475
    public void rip_fildl(final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_fildl(placeHolder);
        new rip_fildl_4475(startPosition, currentPosition() - startPosition, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code filds  }<i>label</i>
     * Example disassembly syntax: {@code filds     [L1: +305419896]}
     */
    // Template#: 217, Serial#: 5123
    public void rip_filds(final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_filds(placeHolder);
        new rip_filds_5123(startPosition, currentPosition() - startPosition, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code fildq  }<i>label</i>
     * Example disassembly syntax: {@code fildq     [L1: +305419896]}
     */
    // Template#: 218, Serial#: 5139
    public void rip_fildq(final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_fildq(placeHolder);
        new rip_fildq_5139(startPosition, currentPosition() - startPosition, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code fimull  }<i>label</i>
     * Example disassembly syntax: {@code fimull    [L1: +305419896]}
     */
    // Template#: 219, Serial#: 4287
    public void rip_fimull(final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_fimull(placeHolder);
        new rip_fimull_4287(startPosition, currentPosition() - startPosition, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code fimuls  }<i>label</i>
     * Example disassembly syntax: {@code fimuls    [L1: +305419896]}
     */
    // Template#: 220, Serial#: 4935
    public void rip_fimuls(final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_fimuls(placeHolder);
        new rip_fimuls_4935(startPosition, currentPosition() - startPosition, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code fistl  }<i>label</i>
     * Example disassembly syntax: {@code fistl     [L1: +305419896]}
     */
    // Template#: 221, Serial#: 4479
    public void rip_fistl(final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_fistl(placeHolder);
        new rip_fistl_4479(startPosition, currentPosition() - startPosition, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code fists  }<i>label</i>
     * Example disassembly syntax: {@code fists     [L1: +305419896]}
     */
    // Template#: 222, Serial#: 5127
    public void rip_fists(final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_fists(placeHolder);
        new rip_fists_5127(startPosition, currentPosition() - startPosition, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code fistpl  }<i>label</i>
     * Example disassembly syntax: {@code fistpl    [L1: +305419896]}
     */
    // Template#: 223, Serial#: 4483
    public void rip_fistpl(final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_fistpl(placeHolder);
        new rip_fistpl_4483(startPosition, currentPosition() - startPosition, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code fistps  }<i>label</i>
     * Example disassembly syntax: {@code fistps    [L1: +305419896]}
     */
    // Template#: 224, Serial#: 5131
    public void rip_fistps(final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_fistps(placeHolder);
        new rip_fistps_5131(startPosition, currentPosition() - startPosition, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code fistpq  }<i>label</i>
     * Example disassembly syntax: {@code fistpq    [L1: +305419896]}
     */
    // Template#: 225, Serial#: 5147
    public void rip_fistpq(final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_fistpq(placeHolder);
        new rip_fistpq_5147(startPosition, currentPosition() - startPosition, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code fisubl  }<i>label</i>
     * Example disassembly syntax: {@code fisubl    [L1: +305419896]}
     */
    // Template#: 226, Serial#: 4299
    public void rip_fisubl(final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_fisubl(placeHolder);
        new rip_fisubl_4299(startPosition, currentPosition() - startPosition, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code fisubs  }<i>label</i>
     * Example disassembly syntax: {@code fisubs    [L1: +305419896]}
     */
    // Template#: 227, Serial#: 4947
    public void rip_fisubs(final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_fisubs(placeHolder);
        new rip_fisubs_4947(startPosition, currentPosition() - startPosition, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code fisubrl  }<i>label</i>
     * Example disassembly syntax: {@code fisubrl   [L1: +305419896]}
     */
    // Template#: 228, Serial#: 4303
    public void rip_fisubrl(final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_fisubrl(placeHolder);
        new rip_fisubrl_4303(startPosition, currentPosition() - startPosition, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code fisubrs  }<i>label</i>
     * Example disassembly syntax: {@code fisubrs   [L1: +305419896]}
     */
    // Template#: 229, Serial#: 4951
    public void rip_fisubrs(final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_fisubrs(placeHolder);
        new rip_fisubrs_4951(startPosition, currentPosition() - startPosition, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code flds  }<i>label</i>
     * Example disassembly syntax: {@code flds      [L1: +305419896]}
     */
    // Template#: 230, Serial#: 4115
    public void rip_flds(final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_flds(placeHolder);
        new rip_flds_4115(startPosition, currentPosition() - startPosition, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code fldt  }<i>label</i>
     * Example disassembly syntax: {@code fldt      [L1: +305419896]}
     */
    // Template#: 231, Serial#: 4487
    public void rip_fldt(final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_fldt(placeHolder);
        new rip_fldt_4487(startPosition, currentPosition() - startPosition, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code fldl  }<i>label</i>
     * Example disassembly syntax: {@code fldl      [L1: +305419896]}
     */
    // Template#: 232, Serial#: 4787
    public void rip_fldl(final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_fldl(placeHolder);
        new rip_fldl_4787(startPosition, currentPosition() - startPosition, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code fldcw  }<i>label</i>
     * Example disassembly syntax: {@code fldcw     [L1: +305419896]}
     */
    // Template#: 233, Serial#: 4131
    public void rip_fldcw(final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_fldcw(placeHolder);
        new rip_fldcw_4131(startPosition, currentPosition() - startPosition, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code fldenv  }<i>label</i>
     * Example disassembly syntax: {@code fldenv    [L1: +305419896]}
     */
    // Template#: 234, Serial#: 4127
    public void rip_fldenv(final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_fldenv(placeHolder);
        new rip_fldenv_4127(startPosition, currentPosition() - startPosition, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code fmuls  }<i>label</i>
     * Example disassembly syntax: {@code fmuls     [L1: +305419896]}
     */
    // Template#: 235, Serial#: 3927
    public void rip_fmuls(final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_fmuls(placeHolder);
        new rip_fmuls_3927(startPosition, currentPosition() - startPosition, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code fmull  }<i>label</i>
     * Example disassembly syntax: {@code fmull     [L1: +305419896]}
     */
    // Template#: 236, Serial#: 4599
    public void rip_fmull(final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_fmull(placeHolder);
        new rip_fmull_4599(startPosition, currentPosition() - startPosition, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code frstor  }<i>label</i>
     * Example disassembly syntax: {@code frstor    [L1: +305419896]}
     */
    // Template#: 237, Serial#: 4799
    public void rip_frstor(final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_frstor(placeHolder);
        new rip_frstor_4799(startPosition, currentPosition() - startPosition, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code fsave  }<i>label</i>
     * Example disassembly syntax: {@code fsave     [L1: +305419896]}
     */
    // Template#: 238, Serial#: 4803
    public void rip_fsave(final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_fsave(placeHolder);
        new rip_fsave_4803(startPosition, currentPosition() - startPosition, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code fsts  }<i>label</i>
     * Example disassembly syntax: {@code fsts      [L1: +305419896]}
     */
    // Template#: 239, Serial#: 4119
    public void rip_fsts(final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_fsts(placeHolder);
        new rip_fsts_4119(startPosition, currentPosition() - startPosition, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code fstl  }<i>label</i>
     * Example disassembly syntax: {@code fstl      [L1: +305419896]}
     */
    // Template#: 240, Serial#: 4791
    public void rip_fstl(final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_fstl(placeHolder);
        new rip_fstl_4791(startPosition, currentPosition() - startPosition, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code fstcw  }<i>label</i>
     * Example disassembly syntax: {@code fstcw     [L1: +305419896]}
     */
    // Template#: 241, Serial#: 4139
    public void rip_fstcw(final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_fstcw(placeHolder);
        new rip_fstcw_4139(startPosition, currentPosition() - startPosition, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code fstenv  }<i>label</i>
     * Example disassembly syntax: {@code fstenv    [L1: +305419896]}
     */
    // Template#: 242, Serial#: 4135
    public void rip_fstenv(final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_fstenv(placeHolder);
        new rip_fstenv_4135(startPosition, currentPosition() - startPosition, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code fstps  }<i>label</i>
     * Example disassembly syntax: {@code fstps     [L1: +305419896]}
     */
    // Template#: 243, Serial#: 4123
    public void rip_fstps(final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_fstps(placeHolder);
        new rip_fstps_4123(startPosition, currentPosition() - startPosition, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code fstpt  }<i>label</i>
     * Example disassembly syntax: {@code fstpt     [L1: +305419896]}
     */
    // Template#: 244, Serial#: 4491
    public void rip_fstpt(final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_fstpt(placeHolder);
        new rip_fstpt_4491(startPosition, currentPosition() - startPosition, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code fstpl  }<i>label</i>
     * Example disassembly syntax: {@code fstpl     [L1: +305419896]}
     */
    // Template#: 245, Serial#: 4795
    public void rip_fstpl(final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_fstpl(placeHolder);
        new rip_fstpl_4795(startPosition, currentPosition() - startPosition, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code fstsw  }<i>label</i>
     * Example disassembly syntax: {@code fstsw     [L1: +305419896]}
     */
    // Template#: 246, Serial#: 4807
    public void rip_fstsw(final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_fstsw(placeHolder);
        new rip_fstsw_4807(startPosition, currentPosition() - startPosition, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code fsubs  }<i>label</i>
     * Example disassembly syntax: {@code fsubs     [L1: +305419896]}
     */
    // Template#: 247, Serial#: 3939
    public void rip_fsubs(final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_fsubs(placeHolder);
        new rip_fsubs_3939(startPosition, currentPosition() - startPosition, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code fsubl  }<i>label</i>
     * Example disassembly syntax: {@code fsubl     [L1: +305419896]}
     */
    // Template#: 248, Serial#: 4611
    public void rip_fsubl(final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_fsubl(placeHolder);
        new rip_fsubl_4611(startPosition, currentPosition() - startPosition, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code fsubrs  }<i>label</i>
     * Example disassembly syntax: {@code fsubrs    [L1: +305419896]}
     */
    // Template#: 249, Serial#: 3943
    public void rip_fsubrs(final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_fsubrs(placeHolder);
        new rip_fsubrs_3943(startPosition, currentPosition() - startPosition, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code fsubrl  }<i>label</i>
     * Example disassembly syntax: {@code fsubrl    [L1: +305419896]}
     */
    // Template#: 250, Serial#: 4615
    public void rip_fsubrl(final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_fsubrl(placeHolder);
        new rip_fsubrl_4615(startPosition, currentPosition() - startPosition, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code fxrstor  }<i>label</i>
     * Example disassembly syntax: {@code fxrstor   [L1: +305419896]}
     */
    // Template#: 251, Serial#: 11341
    public void rip_fxrstor(final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_fxrstor(placeHolder);
        new rip_fxrstor_11341(startPosition, currentPosition() - startPosition, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code fxsave  }<i>label</i>
     * Example disassembly syntax: {@code fxsave    [L1: +305419896]}
     */
    // Template#: 252, Serial#: 11337
    public void rip_fxsave(final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_fxsave(placeHolder);
        new rip_fxsave_11337(startPosition, currentPosition() - startPosition, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code haddpd  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code haddpd    xmm0, [L1: +305419896]}
     */
    // Template#: 253, Serial#: 10881
    public void rip_haddpd(final AMD64XMMRegister destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_haddpd(destination, placeHolder);
        new rip_haddpd_10881(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code haddps  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code haddps    xmm0, [L1: +305419896]}
     */
    // Template#: 254, Serial#: 10953
    public void rip_haddps(final AMD64XMMRegister destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_haddps(destination, placeHolder);
        new rip_haddps_10953(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code hsubpd  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code hsubpd    xmm0, [L1: +305419896]}
     */
    // Template#: 255, Serial#: 10899
    public void rip_hsubpd(final AMD64XMMRegister destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_hsubpd(destination, placeHolder);
        new rip_hsubpd_10899(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code hsubps  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code hsubps    xmm0, [L1: +305419896]}
     */
    // Template#: 256, Serial#: 10971
    public void rip_hsubps(final AMD64XMMRegister destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_hsubps(destination, placeHolder);
        new rip_hsubps_10971(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code idivb  }<i>label</i>
     * Example disassembly syntax: {@code idivb     [L1: +305419896], al}
     */
    // Template#: 257, Serial#: 2712
    public void rip_idivb___AL(final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_idivb___AL(placeHolder);
        new rip_idivb___AL_2712(startPosition, currentPosition() - startPosition, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code idivl  }<i>label</i>
     * Example disassembly syntax: {@code idivl     [L1: +305419896]}
     */
    // Template#: 258, Serial#: 2928
    public void rip_idivl(final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_idivl(placeHolder);
        new rip_idivl_2928(startPosition, currentPosition() - startPosition, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code idivq  }<i>label</i>
     * Example disassembly syntax: {@code idivq     [L1: +305419896]}
     */
    // Template#: 259, Serial#: 3000
    public void rip_idivq(final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_idivq(placeHolder);
        new rip_idivq_3000(startPosition, currentPosition() - startPosition, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code idivw  }<i>label</i>
     * Example disassembly syntax: {@code idivw     [L1: +305419896]}
     */
    // Template#: 260, Serial#: 3072
    public void rip_idivw(final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_idivw(placeHolder);
        new rip_idivw_3072(startPosition, currentPosition() - startPosition, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code imul  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code imul      ax, [L1: +305419896]}
     */
    // Template#: 261, Serial#: 11484
    public void rip_imul(final AMD64GeneralRegister16 destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_imul(destination, placeHolder);
        new rip_imul_11484(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code imul  }<i>destination</i>, <i>label</i>, <i>imm8</i>
     * Example disassembly syntax: {@code imul      ax, [L1: +305419896], 0x12}
     */
    // Template#: 262, Serial#: 3629
    public void rip_imul(final AMD64GeneralRegister16 destination, final Label label, final byte imm8) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_imul(destination, placeHolder, imm8);
        new rip_imul_3629(startPosition, currentPosition() - startPosition, destination, imm8, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code imul  }<i>destination</i>, <i>label</i>, <i>imm16</i>
     * Example disassembly syntax: {@code imul      ax, [L1: +305419896], 0x1234}
     */
    // Template#: 263, Serial#: 3600
    public void rip_imul(final AMD64GeneralRegister16 destination, final Label label, final short imm16) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_imul(destination, placeHolder, imm16);
        new rip_imul_3600(startPosition, currentPosition() - startPosition, destination, imm16, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code imul  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code imul      eax, [L1: +305419896]}
     */
    // Template#: 264, Serial#: 11466
    public void rip_imul(final AMD64GeneralRegister32 destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_imul(destination, placeHolder);
        new rip_imul_11466(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code imul  }<i>destination</i>, <i>label</i>, <i>imm8</i>
     * Example disassembly syntax: {@code imul      eax, [L1: +305419896], 0x12}
     */
    // Template#: 265, Serial#: 3611
    public void rip_imul(final AMD64GeneralRegister32 destination, final Label label, final byte imm8) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_imul(destination, placeHolder, imm8);
        new rip_imul_3611(startPosition, currentPosition() - startPosition, destination, imm8, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code imul  }<i>destination</i>, <i>label</i>, <i>imm32</i>
     * Example disassembly syntax: {@code imul      eax, [L1: +305419896], 0x12345678}
     */
    // Template#: 266, Serial#: 3582
    public void rip_imul(final AMD64GeneralRegister32 destination, final Label label, final int imm32) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_imul(destination, placeHolder, imm32);
        new rip_imul_3582(startPosition, currentPosition() - startPosition, destination, imm32, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code imul  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code imul      rax, [L1: +305419896]}
     */
    // Template#: 267, Serial#: 11475
    public void rip_imul(final AMD64GeneralRegister64 destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_imul(destination, placeHolder);
        new rip_imul_11475(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code imul  }<i>destination</i>, <i>label</i>, <i>imm8</i>
     * Example disassembly syntax: {@code imul      rax, [L1: +305419896], 0x12}
     */
    // Template#: 268, Serial#: 3620
    public void rip_imul(final AMD64GeneralRegister64 destination, final Label label, final byte imm8) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_imul(destination, placeHolder, imm8);
        new rip_imul_3620(startPosition, currentPosition() - startPosition, destination, imm8, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code imul  }<i>destination</i>, <i>label</i>, <i>imm32</i>
     * Example disassembly syntax: {@code imul      rax, [L1: +305419896], 0x12345678}
     */
    // Template#: 269, Serial#: 3591
    public void rip_imul(final AMD64GeneralRegister64 destination, final Label label, final int imm32) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_imul(destination, placeHolder, imm32);
        new rip_imul_3591(startPosition, currentPosition() - startPosition, destination, imm32, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code imulb  }<i>label</i>
     * Example disassembly syntax: {@code imulb     [L1: +305419896], al}
     */
    // Template#: 270, Serial#: 2704
    public void rip_imulb___AL(final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_imulb___AL(placeHolder);
        new rip_imulb___AL_2704(startPosition, currentPosition() - startPosition, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code imull  }<i>label</i>
     * Example disassembly syntax: {@code imull     [L1: +305419896]}
     */
    // Template#: 271, Serial#: 2920
    public void rip_imull(final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_imull(placeHolder);
        new rip_imull_2920(startPosition, currentPosition() - startPosition, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code imulq  }<i>label</i>
     * Example disassembly syntax: {@code imulq     [L1: +305419896]}
     */
    // Template#: 272, Serial#: 2992
    public void rip_imulq(final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_imulq(placeHolder);
        new rip_imulq_2992(startPosition, currentPosition() - startPosition, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code imulw  }<i>label</i>
     * Example disassembly syntax: {@code imulw     [L1: +305419896]}
     */
    // Template#: 273, Serial#: 3064
    public void rip_imulw(final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_imulw(placeHolder);
        new rip_imulw_3064(startPosition, currentPosition() - startPosition, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code incb  }<i>label</i>
     * Example disassembly syntax: {@code incb      [L1: +305419896]}
     */
    // Template#: 274, Serial#: 5324
    public void rip_incb(final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_incb(placeHolder);
        new rip_incb_5324(startPosition, currentPosition() - startPosition, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code incl  }<i>label</i>
     * Example disassembly syntax: {@code incl      [L1: +305419896]}
     */
    // Template#: 275, Serial#: 5378
    public void rip_incl(final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_incl(placeHolder);
        new rip_incl_5378(startPosition, currentPosition() - startPosition, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code incq  }<i>label</i>
     * Example disassembly syntax: {@code incq      [L1: +305419896]}
     */
    // Template#: 276, Serial#: 5396
    public void rip_incq(final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_incq(placeHolder);
        new rip_incq_5396(startPosition, currentPosition() - startPosition, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code incw  }<i>label</i>
     * Example disassembly syntax: {@code incw      [L1: +305419896]}
     */
    // Template#: 277, Serial#: 5414
    public void rip_incw(final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_incw(placeHolder);
        new rip_incw_5414(startPosition, currentPosition() - startPosition, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code invlpg  }<i>label</i>
     * Example disassembly syntax: {@code invlpg    [L1: +305419896]}
     */
    // Template#: 278, Serial#: 5672
    public void rip_invlpg(final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_invlpg(placeHolder);
        new rip_invlpg_5672(startPosition, currentPosition() - startPosition, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code jb  }<i>label</i>
     * Example disassembly syntax: {@code jb        L1: +18}
     */
    // Template#: 279, Serial#: 491
    public void jb(final Label label) {
        final int startPosition = currentPosition();
        final byte placeHolder = 0;
        jb(placeHolder);
        new jb_491(startPosition, currentPosition() - startPosition, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code jbe  }<i>label</i>
     * Example disassembly syntax: {@code jbe       L1: +18}
     */
    // Template#: 280, Serial#: 495
    public void jbe(final Label label) {
        final int startPosition = currentPosition();
        final byte placeHolder = 0;
        jbe(placeHolder);
        new jbe_495(startPosition, currentPosition() - startPosition, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code jl  }<i>label</i>
     * Example disassembly syntax: {@code jl        L1: +18}
     */
    // Template#: 281, Serial#: 3649
    public void jl(final Label label) {
        final int startPosition = currentPosition();
        final byte placeHolder = 0;
        jl(placeHolder);
        new jl_3649(startPosition, currentPosition() - startPosition, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code jle  }<i>label</i>
     * Example disassembly syntax: {@code jle       L1: +18}
     */
    // Template#: 282, Serial#: 3651
    public void jle(final Label label) {
        final int startPosition = currentPosition();
        final byte placeHolder = 0;
        jle(placeHolder);
        new jle_3651(startPosition, currentPosition() - startPosition, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code jmp  }<i>label</i>
     * Example disassembly syntax: {@code jmp       L1: +18}
     */
    // Template#: 283, Serial#: 5290
    public void jmp(final Label label) {
        final int startPosition = currentPosition();
        final byte placeHolder = 0;
        jmp(placeHolder);
        new jmp_5290(startPosition, currentPosition() - startPosition, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code jmp  }<i>label</i>
     * Example disassembly syntax: {@code jmp       [L1: +305419896]}
     */
    // Template#: 284, Serial#: 5436
    public void rip_jmp(final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_jmp(placeHolder);
        new rip_jmp_5436(startPosition, currentPosition() - startPosition, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code jnb  }<i>label</i>
     * Example disassembly syntax: {@code jnb       L1: +18}
     */
    // Template#: 285, Serial#: 492
    public void jnb(final Label label) {
        final int startPosition = currentPosition();
        final byte placeHolder = 0;
        jnb(placeHolder);
        new jnb_492(startPosition, currentPosition() - startPosition, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code jnbe  }<i>label</i>
     * Example disassembly syntax: {@code jnbe      L1: +18}
     */
    // Template#: 286, Serial#: 496
    public void jnbe(final Label label) {
        final int startPosition = currentPosition();
        final byte placeHolder = 0;
        jnbe(placeHolder);
        new jnbe_496(startPosition, currentPosition() - startPosition, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code jnl  }<i>label</i>
     * Example disassembly syntax: {@code jnl       L1: +18}
     */
    // Template#: 287, Serial#: 3650
    public void jnl(final Label label) {
        final int startPosition = currentPosition();
        final byte placeHolder = 0;
        jnl(placeHolder);
        new jnl_3650(startPosition, currentPosition() - startPosition, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code jnle  }<i>label</i>
     * Example disassembly syntax: {@code jnle      L1: +18}
     */
    // Template#: 288, Serial#: 3652
    public void jnle(final Label label) {
        final int startPosition = currentPosition();
        final byte placeHolder = 0;
        jnle(placeHolder);
        new jnle_3652(startPosition, currentPosition() - startPosition, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code jno  }<i>label</i>
     * Example disassembly syntax: {@code jno       L1: +18}
     */
    // Template#: 289, Serial#: 490
    public void jno(final Label label) {
        final int startPosition = currentPosition();
        final byte placeHolder = 0;
        jno(placeHolder);
        new jno_490(startPosition, currentPosition() - startPosition, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code jnp  }<i>label</i>
     * Example disassembly syntax: {@code jnp       L1: +18}
     */
    // Template#: 290, Serial#: 3648
    public void jnp(final Label label) {
        final int startPosition = currentPosition();
        final byte placeHolder = 0;
        jnp(placeHolder);
        new jnp_3648(startPosition, currentPosition() - startPosition, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code jns  }<i>label</i>
     * Example disassembly syntax: {@code jns       L1: +18}
     */
    // Template#: 291, Serial#: 3646
    public void jns(final Label label) {
        final int startPosition = currentPosition();
        final byte placeHolder = 0;
        jns(placeHolder);
        new jns_3646(startPosition, currentPosition() - startPosition, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code jnz  }<i>label</i>
     * Example disassembly syntax: {@code jnz       L1: +18}
     */
    // Template#: 292, Serial#: 494
    public void jnz(final Label label) {
        final int startPosition = currentPosition();
        final byte placeHolder = 0;
        jnz(placeHolder);
        new jnz_494(startPosition, currentPosition() - startPosition, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code jo  }<i>label</i>
     * Example disassembly syntax: {@code jo        L1: +18}
     */
    // Template#: 293, Serial#: 489
    public void jo(final Label label) {
        final int startPosition = currentPosition();
        final byte placeHolder = 0;
        jo(placeHolder);
        new jo_489(startPosition, currentPosition() - startPosition, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code jp  }<i>label</i>
     * Example disassembly syntax: {@code jp        L1: +18}
     */
    // Template#: 294, Serial#: 3647
    public void jp(final Label label) {
        final int startPosition = currentPosition();
        final byte placeHolder = 0;
        jp(placeHolder);
        new jp_3647(startPosition, currentPosition() - startPosition, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code jrcxz  }<i>label</i>
     * Example disassembly syntax: {@code jrcxz     L1: +18}
     */
    // Template#: 295, Serial#: 2649
    public void jrcxz(final Label label) {
        final int startPosition = currentPosition();
        final byte placeHolder = 0;
        jrcxz(placeHolder);
        new jrcxz_2649(startPosition, currentPosition() - startPosition, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code js  }<i>label</i>
     * Example disassembly syntax: {@code js        L1: +18}
     */
    // Template#: 296, Serial#: 3645
    public void js(final Label label) {
        final int startPosition = currentPosition();
        final byte placeHolder = 0;
        js(placeHolder);
        new js_3645(startPosition, currentPosition() - startPosition, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code jz  }<i>label</i>
     * Example disassembly syntax: {@code jz        L1: +18}
     */
    // Template#: 297, Serial#: 493
    public void jz(final Label label) {
        final int startPosition = currentPosition();
        final byte placeHolder = 0;
        jz(placeHolder);
        new jz_493(startPosition, currentPosition() - startPosition, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code lar  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code lar       ax, [L1: +305419896]}
     */
    // Template#: 298, Serial#: 5843
    public void rip_lar(final AMD64GeneralRegister16 destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_lar(destination, placeHolder);
        new rip_lar_5843(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code lar  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code lar       eax, [L1: +305419896]}
     */
    // Template#: 299, Serial#: 5825
    public void rip_lar(final AMD64GeneralRegister32 destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_lar(destination, placeHolder);
        new rip_lar_5825(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code lar  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code lar       rax, [L1: +305419896]}
     */
    // Template#: 300, Serial#: 5834
    public void rip_lar(final AMD64GeneralRegister64 destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_lar(destination, placeHolder);
        new rip_lar_5834(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code lddqu  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code lddqu     xmm0, [L1: +305419896]}
     */
    // Template#: 301, Serial#: 9096
    public void rip_lddqu(final AMD64XMMRegister destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_lddqu(destination, placeHolder);
        new rip_lddqu_9096(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code ldmxcsr  }<i>label</i>
     * Example disassembly syntax: {@code ldmxcsr   [L1: +305419896]}
     */
    // Template#: 302, Serial#: 11345
    public void rip_ldmxcsr(final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_ldmxcsr(placeHolder);
        new rip_ldmxcsr_11345(startPosition, currentPosition() - startPosition, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code lea  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code lea       ax, [L1: +305419896]}
     */
    // Template#: 303, Serial#: 3807
    public void rip_lea(final AMD64GeneralRegister16 destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_lea(destination, placeHolder);
        new rip_lea_3807(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code lea  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code lea       eax, [L1: +305419896]}
     */
    // Template#: 304, Serial#: 3791
    public void rip_lea(final AMD64GeneralRegister32 destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_lea(destination, placeHolder);
        new rip_lea_3791(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code lea  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code lea       rax, [L1: +305419896]}
     */
    // Template#: 305, Serial#: 3799
    public void rip_lea(final AMD64GeneralRegister64 destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_lea(destination, placeHolder);
        new rip_lea_3799(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code lgdt  }<i>label</i>
     * Example disassembly syntax: {@code lgdt      [L1: +305419896]}
     */
    // Template#: 306, Serial#: 5656
    public void rip_lgdt(final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_lgdt(placeHolder);
        new rip_lgdt_5656(startPosition, currentPosition() - startPosition, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code lidt  }<i>label</i>
     * Example disassembly syntax: {@code lidt      [L1: +305419896]}
     */
    // Template#: 307, Serial#: 5660
    public void rip_lidt(final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_lidt(placeHolder);
        new rip_lidt_5660(startPosition, currentPosition() - startPosition, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code lldt  }<i>label</i>
     * Example disassembly syntax: {@code lldt      [L1: +305419896]}
     */
    // Template#: 308, Serial#: 5494
    public void rip_lldt(final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_lldt(placeHolder);
        new rip_lldt_5494(startPosition, currentPosition() - startPosition, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code lmsw  }<i>label</i>
     * Example disassembly syntax: {@code lmsw      [L1: +305419896]}
     */
    // Template#: 309, Serial#: 5668
    public void rip_lmsw(final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_lmsw(placeHolder);
        new rip_lmsw_5668(startPosition, currentPosition() - startPosition, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code loop  }<i>label</i>
     * Example disassembly syntax: {@code loop      L1: +18}
     */
    // Template#: 310, Serial#: 2647
    public void loop(final Label label) {
        final int startPosition = currentPosition();
        final byte placeHolder = 0;
        loop(placeHolder);
        new loop_2647(startPosition, currentPosition() - startPosition, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code loope  }<i>label</i>
     * Example disassembly syntax: {@code loope     L1: +18}
     */
    // Template#: 311, Serial#: 2645
    public void loope(final Label label) {
        final int startPosition = currentPosition();
        final byte placeHolder = 0;
        loope(placeHolder);
        new loope_2645(startPosition, currentPosition() - startPosition, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code loopne  }<i>label</i>
     * Example disassembly syntax: {@code loopne    L1: +18}
     */
    // Template#: 312, Serial#: 2643
    public void loopne(final Label label) {
        final int startPosition = currentPosition();
        final byte placeHolder = 0;
        loopne(placeHolder);
        new loopne_2643(startPosition, currentPosition() - startPosition, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code lsl  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code lsl       ax, [L1: +305419896]}
     */
    // Template#: 313, Serial#: 5870
    public void rip_lsl(final AMD64GeneralRegister16 destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_lsl(destination, placeHolder);
        new rip_lsl_5870(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code lsl  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code lsl       eax, [L1: +305419896]}
     */
    // Template#: 314, Serial#: 5852
    public void rip_lsl(final AMD64GeneralRegister32 destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_lsl(destination, placeHolder);
        new rip_lsl_5852(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code lsl  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code lsl       rax, [L1: +305419896]}
     */
    // Template#: 315, Serial#: 5861
    public void rip_lsl(final AMD64GeneralRegister64 destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_lsl(destination, placeHolder);
        new rip_lsl_5861(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code ltr  }<i>label</i>
     * Example disassembly syntax: {@code ltr       [L1: +305419896]}
     */
    // Template#: 316, Serial#: 5498
    public void rip_ltr(final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_ltr(placeHolder);
        new rip_ltr_5498(startPosition, currentPosition() - startPosition, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code maxpd  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code maxpd     xmm0, [L1: +305419896]}
     */
    // Template#: 317, Serial#: 10233
    public void rip_maxpd(final AMD64XMMRegister destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_maxpd(destination, placeHolder);
        new rip_maxpd_10233(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code maxps  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code maxps     xmm0, [L1: +305419896]}
     */
    // Template#: 318, Serial#: 10089
    public void rip_maxps(final AMD64XMMRegister destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_maxps(destination, placeHolder);
        new rip_maxps_10089(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code maxsd  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code maxsd     xmm0, [L1: +305419896]}
     */
    // Template#: 319, Serial#: 10359
    public void rip_maxsd(final AMD64XMMRegister destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_maxsd(destination, placeHolder);
        new rip_maxsd_10359(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code maxss  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code maxss     xmm0, [L1: +305419896]}
     */
    // Template#: 320, Serial#: 10503
    public void rip_maxss(final AMD64XMMRegister destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_maxss(destination, placeHolder);
        new rip_maxss_10503(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code minpd  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code minpd     xmm0, [L1: +305419896]}
     */
    // Template#: 321, Serial#: 10197
    public void rip_minpd(final AMD64XMMRegister destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_minpd(destination, placeHolder);
        new rip_minpd_10197(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code minps  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code minps     xmm0, [L1: +305419896]}
     */
    // Template#: 322, Serial#: 10053
    public void rip_minps(final AMD64XMMRegister destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_minps(destination, placeHolder);
        new rip_minps_10053(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code minsd  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code minsd     xmm0, [L1: +305419896]}
     */
    // Template#: 323, Serial#: 10323
    public void rip_minsd(final AMD64XMMRegister destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_minsd(destination, placeHolder);
        new rip_minsd_10323(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code minss  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code minss     xmm0, [L1: +305419896]}
     */
    // Template#: 324, Serial#: 10467
    public void rip_minss(final AMD64XMMRegister destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_minss(destination, placeHolder);
        new rip_minss_10467(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code mov  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code mov       ax, [L1: +305419896]}
     */
    // Template#: 325, Serial#: 3755
    public void rip_mov(final AMD64GeneralRegister16 destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_mov(destination, placeHolder);
        new rip_mov_3755(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code mov  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code mov       eax, [L1: +305419896]}
     */
    // Template#: 326, Serial#: 3737
    public void rip_mov(final AMD64GeneralRegister32 destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_mov(destination, placeHolder);
        new rip_mov_3737(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code mov  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code mov       rax, [L1: +305419896]}
     */
    // Template#: 327, Serial#: 3746
    public void rip_mov(final AMD64GeneralRegister64 destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_mov(destination, placeHolder);
        new rip_mov_3746(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code mov  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code mov       al, [L1: +305419896]}
     */
    // Template#: 328, Serial#: 3710
    public void rip_mov(final AMD64GeneralRegister8 destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_mov(destination, placeHolder);
        new rip_mov_3710(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code mov  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code mov       es, [L1: +305419896]}
     */
    // Template#: 329, Serial#: 3815
    public void rip_mov(final SegmentRegister destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_mov(destination, placeHolder);
        new rip_mov_3815(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code movb  }<i>label</i>, <i>imm8</i>
     * Example disassembly syntax: {@code movb      [L1: +305419896], 0x12}
     */
    // Template#: 330, Serial#: 1725
    public void rip_movb(final Label label, final byte imm8) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_movb(placeHolder, imm8);
        new rip_movb_1725(startPosition, currentPosition() - startPosition, imm8, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code mov  }<i>label</i>, <i>source</i>
     * Example disassembly syntax: {@code mov       [L1: +305419896], ax}
     */
    // Template#: 331, Serial#: 3701
    public void rip_mov(final Label label, final AMD64GeneralRegister16 source) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_mov(placeHolder, source);
        new rip_mov_3701(startPosition, currentPosition() - startPosition, source, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code mov  }<i>label</i>, <i>source</i>
     * Example disassembly syntax: {@code mov       [L1: +305419896], eax}
     */
    // Template#: 332, Serial#: 3683
    public void rip_mov(final Label label, final AMD64GeneralRegister32 source) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_mov(placeHolder, source);
        new rip_mov_3683(startPosition, currentPosition() - startPosition, source, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code mov  }<i>label</i>, <i>source</i>
     * Example disassembly syntax: {@code mov       [L1: +305419896], rax}
     */
    // Template#: 333, Serial#: 3692
    public void rip_mov(final Label label, final AMD64GeneralRegister64 source) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_mov(placeHolder, source);
        new rip_mov_3692(startPosition, currentPosition() - startPosition, source, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code mov  }<i>label</i>, <i>source</i>
     * Example disassembly syntax: {@code mov       [L1: +305419896], al}
     */
    // Template#: 334, Serial#: 3656
    public void rip_mov(final Label label, final AMD64GeneralRegister8 source) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_mov(placeHolder, source);
        new rip_mov_3656(startPosition, currentPosition() - startPosition, source, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code mov  }<i>label</i>, <i>source</i>
     * Example disassembly syntax: {@code mov       [L1: +305419896], es}
     */
    // Template#: 335, Serial#: 3764
    public void rip_mov(final Label label, final SegmentRegister source) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_mov(placeHolder, source);
        new rip_mov_3764(startPosition, currentPosition() - startPosition, source, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code movl  }<i>label</i>, <i>imm32</i>
     * Example disassembly syntax: {@code movl      [L1: +305419896], 0x12345678}
     */
    // Template#: 336, Serial#: 1752
    public void rip_movl(final Label label, final int imm32) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_movl(placeHolder, imm32);
        new rip_movl_1752(startPosition, currentPosition() - startPosition, imm32, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code movq  }<i>label</i>, <i>imm32</i>
     * Example disassembly syntax: {@code movq      [L1: +305419896], 0x12345678}
     */
    // Template#: 337, Serial#: 1761
    public void rip_movq(final Label label, final int imm32) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_movq(placeHolder, imm32);
        new rip_movq_1761(startPosition, currentPosition() - startPosition, imm32, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code movw  }<i>label</i>, <i>imm16</i>
     * Example disassembly syntax: {@code movw      [L1: +305419896], 0x1234}
     */
    // Template#: 338, Serial#: 1770
    public void rip_movw(final Label label, final short imm16) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_movw(placeHolder, imm16);
        new rip_movw_1770(startPosition, currentPosition() - startPosition, imm16, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code mov  }<i>label</i>
     * Example disassembly syntax: {@code mov       al, [L1: 0x123456789ABCDE]}
     */
    // Template#: 339, Serial#: 1259
    public void m_mov_AL(final Label label) {
        final int startPosition = currentPosition();
        final long placeHolder = 0;
        m_mov_AL(placeHolder);
        new m_mov_AL_1259(startPosition, currentPosition() - startPosition, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code mov  }<i>label</i>
     * Example disassembly syntax: {@code mov       eax, [L1: 0x123456789ABCDE]}
     */
    // Template#: 340, Serial#: 1262
    public void m_mov_EAX(final Label label) {
        final int startPosition = currentPosition();
        final long placeHolder = 0;
        m_mov_EAX(placeHolder);
        new m_mov_EAX_1262(startPosition, currentPosition() - startPosition, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code mov  }<i>label</i>
     * Example disassembly syntax: {@code mov       rax, [L1: 0x123456789ABCDE]}
     */
    // Template#: 341, Serial#: 1263
    public void m_mov_RAX(final Label label) {
        final int startPosition = currentPosition();
        final long placeHolder = 0;
        m_mov_RAX(placeHolder);
        new m_mov_RAX_1263(startPosition, currentPosition() - startPosition, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code mov  }<i>label</i>
     * Example disassembly syntax: {@code mov       ax, [L1: 0x123456789ABCDE]}
     */
    // Template#: 342, Serial#: 1264
    public void m_mov_AX(final Label label) {
        final int startPosition = currentPosition();
        final long placeHolder = 0;
        m_mov_AX(placeHolder);
        new m_mov_AX_1264(startPosition, currentPosition() - startPosition, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code mov  }<i>label</i>
     * Example disassembly syntax: {@code mov       [L1: 0x123456789ABCDE], al}
     */
    // Template#: 343, Serial#: 1265
    public void m_mov___AL(final Label label) {
        final int startPosition = currentPosition();
        final long placeHolder = 0;
        m_mov___AL(placeHolder);
        new m_mov___AL_1265(startPosition, currentPosition() - startPosition, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code mov  }<i>label</i>
     * Example disassembly syntax: {@code mov       [L1: 0x123456789ABCDE], eax}
     */
    // Template#: 344, Serial#: 1268
    public void m_mov___EAX(final Label label) {
        final int startPosition = currentPosition();
        final long placeHolder = 0;
        m_mov___EAX(placeHolder);
        new m_mov___EAX_1268(startPosition, currentPosition() - startPosition, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code mov  }<i>label</i>
     * Example disassembly syntax: {@code mov       [L1: 0x123456789ABCDE], rax}
     */
    // Template#: 345, Serial#: 1269
    public void m_mov___RAX(final Label label) {
        final int startPosition = currentPosition();
        final long placeHolder = 0;
        m_mov___RAX(placeHolder);
        new m_mov___RAX_1269(startPosition, currentPosition() - startPosition, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code mov  }<i>label</i>
     * Example disassembly syntax: {@code mov       [L1: 0x123456789ABCDE], ax}
     */
    // Template#: 346, Serial#: 1270
    public void m_mov___AX(final Label label) {
        final int startPosition = currentPosition();
        final long placeHolder = 0;
        m_mov___AX(placeHolder);
        new m_mov___AX_1270(startPosition, currentPosition() - startPosition, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code movapd  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code movapd    xmm0, [L1: +305419896]}
     */
    // Template#: 347, Serial#: 9480
    public void rip_movapd(final AMD64XMMRegister destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_movapd(destination, placeHolder);
        new rip_movapd_9480(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code movapd  }<i>label</i>, <i>source</i>
     * Example disassembly syntax: {@code movapd    [L1: +305419896], xmm0}
     */
    // Template#: 348, Serial#: 9498
    public void rip_movapd(final Label label, final AMD64XMMRegister source) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_movapd(placeHolder, source);
        new rip_movapd_9498(startPosition, currentPosition() - startPosition, source, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code movaps  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code movaps    xmm0, [L1: +305419896]}
     */
    // Template#: 349, Serial#: 9321
    public void rip_movaps(final AMD64XMMRegister destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_movaps(destination, placeHolder);
        new rip_movaps_9321(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code movaps  }<i>label</i>, <i>source</i>
     * Example disassembly syntax: {@code movaps    [L1: +305419896], xmm0}
     */
    // Template#: 350, Serial#: 9339
    public void rip_movaps(final Label label, final AMD64XMMRegister source) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_movaps(placeHolder, source);
        new rip_movaps_9339(startPosition, currentPosition() - startPosition, source, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code movdl  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code movdl     xmm0, [L1: +305419896]}
     */
    // Template#: 351, Serial#: 10782
    public void rip_movdl(final AMD64XMMRegister destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_movdl(destination, placeHolder);
        new rip_movdl_10782(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code movdq  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code movdq     xmm0, [L1: +305419896]}
     */
    // Template#: 352, Serial#: 10791
    public void rip_movdq(final AMD64XMMRegister destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_movdq(destination, placeHolder);
        new rip_movdq_10791(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code movdl  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code movdl     mm0, [L1: +305419896]}
     */
    // Template#: 353, Serial#: 10629
    public void rip_movdl(final MMXRegister destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_movdl(destination, placeHolder);
        new rip_movdl_10629(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code movdq  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code movdq     mm0, [L1: +305419896]}
     */
    // Template#: 354, Serial#: 10638
    public void rip_movdq(final MMXRegister destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_movdq(destination, placeHolder);
        new rip_movdq_10638(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code movdl  }<i>label</i>, <i>source</i>
     * Example disassembly syntax: {@code movdl     [L1: +305419896], xmm0}
     */
    // Template#: 355, Serial#: 10917
    public void rip_movdl(final Label label, final AMD64XMMRegister source) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_movdl(placeHolder, source);
        new rip_movdl_10917(startPosition, currentPosition() - startPosition, source, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code movdq  }<i>label</i>, <i>source</i>
     * Example disassembly syntax: {@code movdq     [L1: +305419896], xmm0}
     */
    // Template#: 356, Serial#: 10926
    public void rip_movdq(final Label label, final AMD64XMMRegister source) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_movdq(placeHolder, source);
        new rip_movdq_10926(startPosition, currentPosition() - startPosition, source, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code movdl  }<i>label</i>, <i>source</i>
     * Example disassembly syntax: {@code movdl     [L1: +305419896], mm0}
     */
    // Template#: 357, Serial#: 10836
    public void rip_movdl(final Label label, final MMXRegister source) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_movdl(placeHolder, source);
        new rip_movdl_10836(startPosition, currentPosition() - startPosition, source, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code movdq  }<i>label</i>, <i>source</i>
     * Example disassembly syntax: {@code movdq     [L1: +305419896], mm0}
     */
    // Template#: 358, Serial#: 10845
    public void rip_movdq(final Label label, final MMXRegister source) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_movdq(placeHolder, source);
        new rip_movdq_10845(startPosition, currentPosition() - startPosition, source, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code movddup  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code movddup   xmm0, [L1: +305419896]}
     */
    // Template#: 359, Serial#: 6236
    public void rip_movddup(final AMD64XMMRegister destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_movddup(destination, placeHolder);
        new rip_movddup_6236(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code movdqa  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code movdqa    xmm0, [L1: +305419896]}
     */
    // Template#: 360, Serial#: 10800
    public void rip_movdqa(final AMD64XMMRegister destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_movdqa(destination, placeHolder);
        new rip_movdqa_10800(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code movdqa  }<i>label</i>, <i>source</i>
     * Example disassembly syntax: {@code movdqa    [L1: +305419896], xmm0}
     */
    // Template#: 361, Serial#: 10935
    public void rip_movdqa(final Label label, final AMD64XMMRegister source) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_movdqa(placeHolder, source);
        new rip_movdqa_10935(startPosition, currentPosition() - startPosition, source, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code movdqu  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code movdqu    xmm0, [L1: +305419896]}
     */
    // Template#: 362, Serial#: 10818
    public void rip_movdqu(final AMD64XMMRegister destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_movdqu(destination, placeHolder);
        new rip_movdqu_10818(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code movdqu  }<i>label</i>, <i>source</i>
     * Example disassembly syntax: {@code movdqu    [L1: +305419896], xmm0}
     */
    // Template#: 363, Serial#: 11007
    public void rip_movdqu(final Label label, final AMD64XMMRegister source) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_movdqu(placeHolder, source);
        new rip_movdqu_11007(startPosition, currentPosition() - startPosition, source, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code movhpd  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code movhpd    xmm0, [L1: +305419896]}
     */
    // Template#: 364, Serial#: 6134
    public void rip_movhpd(final AMD64XMMRegister destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_movhpd(destination, placeHolder);
        new rip_movhpd_6134(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code movhpd  }<i>label</i>, <i>source</i>
     * Example disassembly syntax: {@code movhpd    [L1: +305419896], xmm0}
     */
    // Template#: 365, Serial#: 6158
    public void rip_movhpd(final Label label, final AMD64XMMRegister source) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_movhpd(placeHolder, source);
        new rip_movhpd_6158(startPosition, currentPosition() - startPosition, source, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code movhps  }<i>label</i>, <i>source</i>
     * Example disassembly syntax: {@code movhps    [L1: +305419896], xmm0}
     */
    // Template#: 366, Serial#: 5990
    public void rip_movhps(final Label label, final AMD64XMMRegister source) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_movhps(placeHolder, source);
        new rip_movhps_5990(startPosition, currentPosition() - startPosition, source, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code movlpd  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code movlpd    xmm0, [L1: +305419896]}
     */
    // Template#: 367, Serial#: 6050
    public void rip_movlpd(final AMD64XMMRegister destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_movlpd(destination, placeHolder);
        new rip_movlpd_6050(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code movlpd  }<i>label</i>, <i>source</i>
     * Example disassembly syntax: {@code movlpd    [L1: +305419896], xmm0}
     */
    // Template#: 368, Serial#: 6074
    public void rip_movlpd(final Label label, final AMD64XMMRegister source) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_movlpd(placeHolder, source);
        new rip_movlpd_6074(startPosition, currentPosition() - startPosition, source, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code movlps  }<i>label</i>, <i>source</i>
     * Example disassembly syntax: {@code movlps    [L1: +305419896], xmm0}
     */
    // Template#: 369, Serial#: 5927
    public void rip_movlps(final Label label, final AMD64XMMRegister source) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_movlps(placeHolder, source);
        new rip_movlps_5927(startPosition, currentPosition() - startPosition, source, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code movnti  }<i>label</i>, <i>source</i>
     * Example disassembly syntax: {@code movnti    [L1: +305419896], eax}
     */
    // Template#: 370, Serial#: 8021
    public void rip_movnti(final Label label, final AMD64GeneralRegister32 source) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_movnti(placeHolder, source);
        new rip_movnti_8021(startPosition, currentPosition() - startPosition, source, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code movnti  }<i>label</i>, <i>source</i>
     * Example disassembly syntax: {@code movnti    [L1: +305419896], rax}
     */
    // Template#: 371, Serial#: 8029
    public void rip_movnti(final Label label, final AMD64GeneralRegister64 source) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_movnti(placeHolder, source);
        new rip_movnti_8029(startPosition, currentPosition() - startPosition, source, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code movntpd  }<i>label</i>, <i>source</i>
     * Example disassembly syntax: {@code movntpd   [L1: +305419896], xmm0}
     */
    // Template#: 372, Serial#: 9543
    public void rip_movntpd(final Label label, final AMD64XMMRegister source) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_movntpd(placeHolder, source);
        new rip_movntpd_9543(startPosition, currentPosition() - startPosition, source, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code movntps  }<i>label</i>, <i>source</i>
     * Example disassembly syntax: {@code movntps   [L1: +305419896], xmm0}
     */
    // Template#: 373, Serial#: 9384
    public void rip_movntps(final Label label, final AMD64XMMRegister source) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_movntps(placeHolder, source);
        new rip_movntps_9384(startPosition, currentPosition() - startPosition, source, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code movntq  }<i>label</i>, <i>source</i>
     * Example disassembly syntax: {@code movntq    [L1: +305419896], mm0}
     */
    // Template#: 374, Serial#: 8610
    public void rip_movntq(final Label label, final MMXRegister source) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_movntq(placeHolder, source);
        new rip_movntq_8610(startPosition, currentPosition() - startPosition, source, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code movq  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code movq      xmm0, [L1: +305419896]}
     */
    // Template#: 375, Serial#: 10989
    public void rip_movq(final AMD64XMMRegister destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_movq(destination, placeHolder);
        new rip_movq_10989(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code movq  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code movq      mm0, [L1: +305419896]}
     */
    // Template#: 376, Serial#: 10647
    public void rip_movq(final MMXRegister destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_movq(destination, placeHolder);
        new rip_movq_10647(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code movq  }<i>label</i>, <i>source</i>
     * Example disassembly syntax: {@code movq      [L1: +305419896], xmm0}
     */
    // Template#: 377, Serial#: 8421
    public void rip_movq(final Label label, final AMD64XMMRegister source) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_movq(placeHolder, source);
        new rip_movq_8421(startPosition, currentPosition() - startPosition, source, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code movq  }<i>label</i>, <i>source</i>
     * Example disassembly syntax: {@code movq      [L1: +305419896], mm0}
     */
    // Template#: 378, Serial#: 10854
    public void rip_movq(final Label label, final MMXRegister source) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_movq(placeHolder, source);
        new rip_movq_10854(startPosition, currentPosition() - startPosition, source, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code movsd  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code movsd     xmm0, [L1: +305419896]}
     */
    // Template#: 379, Serial#: 6182
    public void rip_movsd(final AMD64XMMRegister destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_movsd(destination, placeHolder);
        new rip_movsd_6182(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code movsd  }<i>label</i>, <i>source</i>
     * Example disassembly syntax: {@code movsd     [L1: +305419896], xmm0}
     */
    // Template#: 380, Serial#: 6218
    public void rip_movsd(final Label label, final AMD64XMMRegister source) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_movsd(placeHolder, source);
        new rip_movsd_6218(startPosition, currentPosition() - startPosition, source, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code movshdup  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code movshdup  xmm0, [L1: +305419896]}
     */
    // Template#: 381, Serial#: 6326
    public void rip_movshdup(final AMD64XMMRegister destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_movshdup(destination, placeHolder);
        new rip_movshdup_6326(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code movsldup  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code movsldup  xmm0, [L1: +305419896]}
     */
    // Template#: 382, Serial#: 6308
    public void rip_movsldup(final AMD64XMMRegister destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_movsldup(destination, placeHolder);
        new rip_movsldup_6308(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code movss  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code movss     xmm0, [L1: +305419896]}
     */
    // Template#: 383, Serial#: 6254
    public void rip_movss(final AMD64XMMRegister destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_movss(destination, placeHolder);
        new rip_movss_6254(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code movss  }<i>label</i>, <i>source</i>
     * Example disassembly syntax: {@code movss     [L1: +305419896], xmm0}
     */
    // Template#: 384, Serial#: 6290
    public void rip_movss(final Label label, final AMD64XMMRegister source) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_movss(placeHolder, source);
        new rip_movss_6290(startPosition, currentPosition() - startPosition, source, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code movsx  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code movsx     ax, [L1: +305419896]}
     */
    // Template#: 385, Serial#: 11700
    public void rip_movsxb(final AMD64GeneralRegister16 destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_movsxb(destination, placeHolder);
        new rip_movsxb_11700(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code movsx  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code movsx     eax, [L1: +305419896]}
     */
    // Template#: 386, Serial#: 11682
    public void rip_movsxb(final AMD64GeneralRegister32 destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_movsxb(destination, placeHolder);
        new rip_movsxb_11682(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code movsx  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code movsx     rax, [L1: +305419896]}
     */
    // Template#: 387, Serial#: 11691
    public void rip_movsxb(final AMD64GeneralRegister64 destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_movsxb(destination, placeHolder);
        new rip_movsxb_11691(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code movsxd  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code movsxd    rax, [L1: +305419896]}
     */
    // Template#: 388, Serial#: 462
    public void rip_movsxd(final AMD64GeneralRegister64 destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_movsxd(destination, placeHolder);
        new rip_movsxd_462(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code movsxw  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code movsxw    eax, [L1: +305419896]}
     */
    // Template#: 389, Serial#: 11709
    public void rip_movsxw(final AMD64GeneralRegister32 destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_movsxw(destination, placeHolder);
        new rip_movsxw_11709(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code movsxw  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code movsxw    rax, [L1: +305419896]}
     */
    // Template#: 390, Serial#: 11718
    public void rip_movsxw(final AMD64GeneralRegister64 destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_movsxw(destination, placeHolder);
        new rip_movsxw_11718(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code movupd  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code movupd    xmm0, [L1: +305419896]}
     */
    // Template#: 391, Serial#: 6014
    public void rip_movupd(final AMD64XMMRegister destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_movupd(destination, placeHolder);
        new rip_movupd_6014(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code movupd  }<i>label</i>, <i>source</i>
     * Example disassembly syntax: {@code movupd    [L1: +305419896], xmm0}
     */
    // Template#: 392, Serial#: 6032
    public void rip_movupd(final Label label, final AMD64XMMRegister source) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_movupd(placeHolder, source);
        new rip_movupd_6032(startPosition, currentPosition() - startPosition, source, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code movups  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code movups    xmm0, [L1: +305419896]}
     */
    // Template#: 393, Serial#: 5888
    public void rip_movups(final AMD64XMMRegister destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_movups(destination, placeHolder);
        new rip_movups_5888(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code movups  }<i>label</i>, <i>source</i>
     * Example disassembly syntax: {@code movups    [L1: +305419896], xmm0}
     */
    // Template#: 394, Serial#: 5906
    public void rip_movups(final Label label, final AMD64XMMRegister source) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_movups(placeHolder, source);
        new rip_movups_5906(startPosition, currentPosition() - startPosition, source, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code movzx  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code movzx     ax, [L1: +305419896]}
     */
    // Template#: 395, Serial#: 7922
    public void rip_movzxb(final AMD64GeneralRegister16 destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_movzxb(destination, placeHolder);
        new rip_movzxb_7922(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code movzx  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code movzx     eax, [L1: +305419896]}
     */
    // Template#: 396, Serial#: 7904
    public void rip_movzxb(final AMD64GeneralRegister32 destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_movzxb(destination, placeHolder);
        new rip_movzxb_7904(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code movzx  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code movzx     rax, [L1: +305419896]}
     */
    // Template#: 397, Serial#: 7913
    public void rip_movzxb(final AMD64GeneralRegister64 destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_movzxb(destination, placeHolder);
        new rip_movzxb_7913(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code movzxd  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code movzxd    rax, [L1: +305419896]}
     */
    // Template#: 398, Serial#: 471
    public void rip_movzxd(final AMD64GeneralRegister64 destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_movzxd(destination, placeHolder);
        new rip_movzxd_471(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code movzxw  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code movzxw    eax, [L1: +305419896]}
     */
    // Template#: 399, Serial#: 7931
    public void rip_movzxw(final AMD64GeneralRegister32 destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_movzxw(destination, placeHolder);
        new rip_movzxw_7931(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code movzxw  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code movzxw    rax, [L1: +305419896]}
     */
    // Template#: 400, Serial#: 7940
    public void rip_movzxw(final AMD64GeneralRegister64 destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_movzxw(destination, placeHolder);
        new rip_movzxw_7940(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code mulb  }<i>label</i>
     * Example disassembly syntax: {@code mulb      [L1: +305419896], al}
     */
    // Template#: 401, Serial#: 2700
    public void rip_mulb___AL(final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_mulb___AL(placeHolder);
        new rip_mulb___AL_2700(startPosition, currentPosition() - startPosition, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code mull  }<i>label</i>
     * Example disassembly syntax: {@code mull      [L1: +305419896]}
     */
    // Template#: 402, Serial#: 2916
    public void rip_mull(final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_mull(placeHolder);
        new rip_mull_2916(startPosition, currentPosition() - startPosition, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code mulq  }<i>label</i>
     * Example disassembly syntax: {@code mulq      [L1: +305419896]}
     */
    // Template#: 403, Serial#: 2988
    public void rip_mulq(final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_mulq(placeHolder);
        new rip_mulq_2988(startPosition, currentPosition() - startPosition, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code mulw  }<i>label</i>
     * Example disassembly syntax: {@code mulw      [L1: +305419896]}
     */
    // Template#: 404, Serial#: 3060
    public void rip_mulw(final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_mulw(placeHolder);
        new rip_mulw_3060(startPosition, currentPosition() - startPosition, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code mulpd  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code mulpd     xmm0, [L1: +305419896]}
     */
    // Template#: 405, Serial#: 10125
    public void rip_mulpd(final AMD64XMMRegister destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_mulpd(destination, placeHolder);
        new rip_mulpd_10125(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code mulps  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code mulps     xmm0, [L1: +305419896]}
     */
    // Template#: 406, Serial#: 9981
    public void rip_mulps(final AMD64XMMRegister destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_mulps(destination, placeHolder);
        new rip_mulps_9981(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code mulsd  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code mulsd     xmm0, [L1: +305419896]}
     */
    // Template#: 407, Serial#: 10269
    public void rip_mulsd(final AMD64XMMRegister destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_mulsd(destination, placeHolder);
        new rip_mulsd_10269(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code mulss  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code mulss     xmm0, [L1: +305419896]}
     */
    // Template#: 408, Serial#: 10395
    public void rip_mulss(final AMD64XMMRegister destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_mulss(destination, placeHolder);
        new rip_mulss_10395(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code mvntdq  }<i>label</i>, <i>source</i>
     * Example disassembly syntax: {@code mvntdq    [L1: +305419896], xmm0}
     */
    // Template#: 409, Serial#: 8760
    public void rip_mvntdq(final Label label, final AMD64XMMRegister source) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_mvntdq(placeHolder, source);
        new rip_mvntdq_8760(startPosition, currentPosition() - startPosition, source, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code negb  }<i>label</i>
     * Example disassembly syntax: {@code negb      [L1: +305419896]}
     */
    // Template#: 410, Serial#: 2696
    public void rip_negb(final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_negb(placeHolder);
        new rip_negb_2696(startPosition, currentPosition() - startPosition, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code negl  }<i>label</i>
     * Example disassembly syntax: {@code negl      [L1: +305419896]}
     */
    // Template#: 411, Serial#: 2912
    public void rip_negl(final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_negl(placeHolder);
        new rip_negl_2912(startPosition, currentPosition() - startPosition, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code negq  }<i>label</i>
     * Example disassembly syntax: {@code negq      [L1: +305419896]}
     */
    // Template#: 412, Serial#: 2984
    public void rip_negq(final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_negq(placeHolder);
        new rip_negq_2984(startPosition, currentPosition() - startPosition, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code negw  }<i>label</i>
     * Example disassembly syntax: {@code negw      [L1: +305419896]}
     */
    // Template#: 413, Serial#: 3056
    public void rip_negw(final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_negw(placeHolder);
        new rip_negw_3056(startPosition, currentPosition() - startPosition, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code notb  }<i>label</i>
     * Example disassembly syntax: {@code notb      [L1: +305419896]}
     */
    // Template#: 414, Serial#: 2692
    public void rip_notb(final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_notb(placeHolder);
        new rip_notb_2692(startPosition, currentPosition() - startPosition, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code notl  }<i>label</i>
     * Example disassembly syntax: {@code notl      [L1: +305419896]}
     */
    // Template#: 415, Serial#: 2908
    public void rip_notl(final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_notl(placeHolder);
        new rip_notl_2908(startPosition, currentPosition() - startPosition, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code notq  }<i>label</i>
     * Example disassembly syntax: {@code notq      [L1: +305419896]}
     */
    // Template#: 416, Serial#: 2980
    public void rip_notq(final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_notq(placeHolder);
        new rip_notq_2980(startPosition, currentPosition() - startPosition, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code notw  }<i>label</i>
     * Example disassembly syntax: {@code notw      [L1: +305419896]}
     */
    // Template#: 417, Serial#: 3052
    public void rip_notw(final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_notw(placeHolder);
        new rip_notw_3052(startPosition, currentPosition() - startPosition, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code or  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code or        ax, [L1: +305419896]}
     */
    // Template#: 418, Serial#: 3215
    public void rip_or(final AMD64GeneralRegister16 destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_or(destination, placeHolder);
        new rip_or_3215(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code or  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code or        eax, [L1: +305419896]}
     */
    // Template#: 419, Serial#: 3197
    public void rip_or(final AMD64GeneralRegister32 destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_or(destination, placeHolder);
        new rip_or_3197(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code or  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code or        rax, [L1: +305419896]}
     */
    // Template#: 420, Serial#: 3206
    public void rip_or(final AMD64GeneralRegister64 destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_or(destination, placeHolder);
        new rip_or_3206(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code or  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code or        al, [L1: +305419896]}
     */
    // Template#: 421, Serial#: 3170
    public void rip_or(final AMD64GeneralRegister8 destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_or(destination, placeHolder);
        new rip_or_3170(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code orb  }<i>label</i>, <i>imm8</i>
     * Example disassembly syntax: {@code orb       [L1: +305419896], 0x12}
     */
    // Template#: 422, Serial#: 504
    public void rip_orb(final Label label, final byte imm8) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_orb(placeHolder, imm8);
        new rip_orb_504(startPosition, currentPosition() - startPosition, imm8, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code orl  }<i>label</i>, <i>imm8</i>
     * Example disassembly syntax: {@code orl       [L1: +305419896], 0x12}
     */
    // Template#: 423, Serial#: 936
    public void rip_orl(final Label label, final byte imm8) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_orl(placeHolder, imm8);
        new rip_orl_936(startPosition, currentPosition() - startPosition, imm8, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code orq  }<i>label</i>, <i>imm8</i>
     * Example disassembly syntax: {@code orq       [L1: +305419896], 0x12}
     */
    // Template#: 424, Serial#: 1008
    public void rip_orq(final Label label, final byte imm8) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_orq(placeHolder, imm8);
        new rip_orq_1008(startPosition, currentPosition() - startPosition, imm8, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code orw  }<i>label</i>, <i>imm8</i>
     * Example disassembly syntax: {@code orw       [L1: +305419896], 0x12}
     */
    // Template#: 425, Serial#: 1080
    public void rip_orw(final Label label, final byte imm8) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_orw(placeHolder, imm8);
        new rip_orw_1080(startPosition, currentPosition() - startPosition, imm8, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code or  }<i>label</i>, <i>source</i>
     * Example disassembly syntax: {@code or        [L1: +305419896], ax}
     */
    // Template#: 426, Serial#: 3161
    public void rip_or(final Label label, final AMD64GeneralRegister16 source) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_or(placeHolder, source);
        new rip_or_3161(startPosition, currentPosition() - startPosition, source, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code or  }<i>label</i>, <i>source</i>
     * Example disassembly syntax: {@code or        [L1: +305419896], eax}
     */
    // Template#: 427, Serial#: 3143
    public void rip_or(final Label label, final AMD64GeneralRegister32 source) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_or(placeHolder, source);
        new rip_or_3143(startPosition, currentPosition() - startPosition, source, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code or  }<i>label</i>, <i>source</i>
     * Example disassembly syntax: {@code or        [L1: +305419896], rax}
     */
    // Template#: 428, Serial#: 3152
    public void rip_or(final Label label, final AMD64GeneralRegister64 source) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_or(placeHolder, source);
        new rip_or_3152(startPosition, currentPosition() - startPosition, source, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code or  }<i>label</i>, <i>source</i>
     * Example disassembly syntax: {@code or        [L1: +305419896], al}
     */
    // Template#: 429, Serial#: 3116
    public void rip_or(final Label label, final AMD64GeneralRegister8 source) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_or(placeHolder, source);
        new rip_or_3116(startPosition, currentPosition() - startPosition, source, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code orl  }<i>label</i>, <i>imm32</i>
     * Example disassembly syntax: {@code orl       [L1: +305419896], 0x12345678}
     */
    // Template#: 430, Serial#: 720
    public void rip_orl(final Label label, final int imm32) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_orl(placeHolder, imm32);
        new rip_orl_720(startPosition, currentPosition() - startPosition, imm32, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code orq  }<i>label</i>, <i>imm32</i>
     * Example disassembly syntax: {@code orq       [L1: +305419896], 0x12345678}
     */
    // Template#: 431, Serial#: 792
    public void rip_orq(final Label label, final int imm32) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_orq(placeHolder, imm32);
        new rip_orq_792(startPosition, currentPosition() - startPosition, imm32, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code orw  }<i>label</i>, <i>imm16</i>
     * Example disassembly syntax: {@code orw       [L1: +305419896], 0x1234}
     */
    // Template#: 432, Serial#: 864
    public void rip_orw(final Label label, final short imm16) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_orw(placeHolder, imm16);
        new rip_orw_864(startPosition, currentPosition() - startPosition, imm16, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code orpd  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code orpd      xmm0, [L1: +305419896]}
     */
    // Template#: 433, Serial#: 6770
    public void rip_orpd(final AMD64XMMRegister destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_orpd(destination, placeHolder);
        new rip_orpd_6770(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code orps  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code orps      xmm0, [L1: +305419896]}
     */
    // Template#: 434, Serial#: 6677
    public void rip_orps(final AMD64XMMRegister destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_orps(destination, placeHolder);
        new rip_orps_6677(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code packssdw  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code packssdw  xmm0, [L1: +305419896]}
     */
    // Template#: 435, Serial#: 10728
    public void rip_packssdw(final AMD64XMMRegister destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_packssdw(destination, placeHolder);
        new rip_packssdw_10728(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code packssdw  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code packssdw  mm0, [L1: +305419896]}
     */
    // Template#: 436, Serial#: 10602
    public void rip_packssdw(final MMXRegister destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_packssdw(destination, placeHolder);
        new rip_packssdw_10602(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code packsswb  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code packsswb  xmm0, [L1: +305419896]}
     */
    // Template#: 437, Serial#: 7148
    public void rip_packsswb(final AMD64XMMRegister destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_packsswb(destination, placeHolder);
        new rip_packsswb_7148(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code packsswb  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code packsswb  mm0, [L1: +305419896]}
     */
    // Template#: 438, Serial#: 6959
    public void rip_packsswb(final MMXRegister destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_packsswb(destination, placeHolder);
        new rip_packsswb_6959(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code packuswb  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code packuswb  xmm0, [L1: +305419896]}
     */
    // Template#: 439, Serial#: 7220
    public void rip_packuswb(final AMD64XMMRegister destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_packuswb(destination, placeHolder);
        new rip_packuswb_7220(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code packuswb  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code packuswb  mm0, [L1: +305419896]}
     */
    // Template#: 440, Serial#: 7067
    public void rip_packuswb(final MMXRegister destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_packuswb(destination, placeHolder);
        new rip_packuswb_7067(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code paddb  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code paddb     xmm0, [L1: +305419896]}
     */
    // Template#: 441, Serial#: 12710
    public void rip_paddb(final AMD64XMMRegister destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_paddb(destination, placeHolder);
        new rip_paddb_12710(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code paddb  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code paddb     mm0, [L1: +305419896]}
     */
    // Template#: 442, Serial#: 12557
    public void rip_paddb(final MMXRegister destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_paddb(destination, placeHolder);
        new rip_paddb_12557(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code paddd  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code paddd     xmm0, [L1: +305419896]}
     */
    // Template#: 443, Serial#: 12746
    public void rip_paddd(final AMD64XMMRegister destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_paddd(destination, placeHolder);
        new rip_paddd_12746(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code paddd  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code paddd     mm0, [L1: +305419896]}
     */
    // Template#: 444, Serial#: 12611
    public void rip_paddd(final MMXRegister destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_paddd(destination, placeHolder);
        new rip_paddd_12611(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code paddq  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code paddq     xmm0, [L1: +305419896]}
     */
    // Template#: 445, Serial#: 8385
    public void rip_paddq(final AMD64XMMRegister destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_paddq(destination, placeHolder);
        new rip_paddq_8385(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code paddq  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code paddq     mm0, [L1: +305419896]}
     */
    // Template#: 446, Serial#: 8256
    public void rip_paddq(final MMXRegister destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_paddq(destination, placeHolder);
        new rip_paddq_8256(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code paddsb  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code paddsb    xmm0, [L1: +305419896]}
     */
    // Template#: 447, Serial#: 12377
    public void rip_paddsb(final AMD64XMMRegister destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_paddsb(destination, placeHolder);
        new rip_paddsb_12377(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code paddsb  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code paddsb    mm0, [L1: +305419896]}
     */
    // Template#: 448, Serial#: 12197
    public void rip_paddsb(final MMXRegister destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_paddsb(destination, placeHolder);
        new rip_paddsb_12197(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code paddsw  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code paddsw    xmm0, [L1: +305419896]}
     */
    // Template#: 449, Serial#: 12395
    public void rip_paddsw(final AMD64XMMRegister destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_paddsw(destination, placeHolder);
        new rip_paddsw_12395(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code paddsw  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code paddsw    mm0, [L1: +305419896]}
     */
    // Template#: 450, Serial#: 12224
    public void rip_paddsw(final MMXRegister destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_paddsw(destination, placeHolder);
        new rip_paddsw_12224(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code paddusb  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code paddusb   xmm0, [L1: +305419896]}
     */
    // Template#: 451, Serial#: 12017
    public void rip_paddusb(final AMD64XMMRegister destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_paddusb(destination, placeHolder);
        new rip_paddusb_12017(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code paddusb  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code paddusb   mm0, [L1: +305419896]}
     */
    // Template#: 452, Serial#: 11837
    public void rip_paddusb(final MMXRegister destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_paddusb(destination, placeHolder);
        new rip_paddusb_11837(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code paddusw  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code paddusw   xmm0, [L1: +305419896]}
     */
    // Template#: 453, Serial#: 12035
    public void rip_paddusw(final AMD64XMMRegister destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_paddusw(destination, placeHolder);
        new rip_paddusw_12035(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code paddusw  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code paddusw   mm0, [L1: +305419896]}
     */
    // Template#: 454, Serial#: 11864
    public void rip_paddusw(final MMXRegister destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_paddusw(destination, placeHolder);
        new rip_paddusw_11864(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code paddw  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code paddw     xmm0, [L1: +305419896]}
     */
    // Template#: 455, Serial#: 12728
    public void rip_paddw(final AMD64XMMRegister destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_paddw(destination, placeHolder);
        new rip_paddw_12728(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code paddw  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code paddw     mm0, [L1: +305419896]}
     */
    // Template#: 456, Serial#: 12584
    public void rip_paddw(final MMXRegister destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_paddw(destination, placeHolder);
        new rip_paddw_12584(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code pand  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code pand      xmm0, [L1: +305419896]}
     */
    // Template#: 457, Serial#: 11999
    public void rip_pand(final AMD64XMMRegister destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_pand(destination, placeHolder);
        new rip_pand_11999(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code pand  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code pand      mm0, [L1: +305419896]}
     */
    // Template#: 458, Serial#: 11810
    public void rip_pand(final MMXRegister destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_pand(destination, placeHolder);
        new rip_pand_11810(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code pandn  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code pandn     xmm0, [L1: +305419896]}
     */
    // Template#: 459, Serial#: 12071
    public void rip_pandn(final AMD64XMMRegister destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_pandn(destination, placeHolder);
        new rip_pandn_12071(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code pandn  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code pandn     mm0, [L1: +305419896]}
     */
    // Template#: 460, Serial#: 11918
    public void rip_pandn(final MMXRegister destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_pandn(destination, placeHolder);
        new rip_pandn_11918(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code pavgb  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code pavgb     xmm0, [L1: +305419896]}
     */
    // Template#: 461, Serial#: 8634
    public void rip_pavgb(final AMD64XMMRegister destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_pavgb(destination, placeHolder);
        new rip_pavgb_8634(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code pavgb  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code pavgb     mm0, [L1: +305419896]}
     */
    // Template#: 462, Serial#: 8448
    public void rip_pavgb(final MMXRegister destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_pavgb(destination, placeHolder);
        new rip_pavgb_8448(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code pavgw  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code pavgw     xmm0, [L1: +305419896]}
     */
    // Template#: 463, Serial#: 8688
    public void rip_pavgw(final AMD64XMMRegister destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_pavgw(destination, placeHolder);
        new rip_pavgw_8688(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code pavgw  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code pavgw     mm0, [L1: +305419896]}
     */
    // Template#: 464, Serial#: 8529
    public void rip_pavgw(final MMXRegister destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_pavgw(destination, placeHolder);
        new rip_pavgw_8529(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code pcmpeqb  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code pcmpeqb   xmm0, [L1: +305419896]}
     */
    // Template#: 465, Serial#: 7421
    public void rip_pcmpeqb(final AMD64XMMRegister destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_pcmpeqb(destination, placeHolder);
        new rip_pcmpeqb_7421(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code pcmpeqb  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code pcmpeqb   mm0, [L1: +305419896]}
     */
    // Template#: 466, Serial#: 7289
    public void rip_pcmpeqb(final MMXRegister destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_pcmpeqb(destination, placeHolder);
        new rip_pcmpeqb_7289(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code pcmpeqd  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code pcmpeqd   xmm0, [L1: +305419896]}
     */
    // Template#: 467, Serial#: 7457
    public void rip_pcmpeqd(final AMD64XMMRegister destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_pcmpeqd(destination, placeHolder);
        new rip_pcmpeqd_7457(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code pcmpeqd  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code pcmpeqd   mm0, [L1: +305419896]}
     */
    // Template#: 468, Serial#: 7343
    public void rip_pcmpeqd(final MMXRegister destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_pcmpeqd(destination, placeHolder);
        new rip_pcmpeqd_7343(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code pcmpeqw  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code pcmpeqw   xmm0, [L1: +305419896]}
     */
    // Template#: 469, Serial#: 7439
    public void rip_pcmpeqw(final AMD64XMMRegister destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_pcmpeqw(destination, placeHolder);
        new rip_pcmpeqw_7439(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code pcmpeqw  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code pcmpeqw   mm0, [L1: +305419896]}
     */
    // Template#: 470, Serial#: 7316
    public void rip_pcmpeqw(final MMXRegister destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_pcmpeqw(destination, placeHolder);
        new rip_pcmpeqw_7316(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code pcmpgtb  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code pcmpgtb   xmm0, [L1: +305419896]}
     */
    // Template#: 471, Serial#: 7166
    public void rip_pcmpgtb(final AMD64XMMRegister destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_pcmpgtb(destination, placeHolder);
        new rip_pcmpgtb_7166(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code pcmpgtb  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code pcmpgtb   mm0, [L1: +305419896]}
     */
    // Template#: 472, Serial#: 6986
    public void rip_pcmpgtb(final MMXRegister destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_pcmpgtb(destination, placeHolder);
        new rip_pcmpgtb_6986(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code pcmpgtd  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code pcmpgtd   xmm0, [L1: +305419896]}
     */
    // Template#: 473, Serial#: 7202
    public void rip_pcmpgtd(final AMD64XMMRegister destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_pcmpgtd(destination, placeHolder);
        new rip_pcmpgtd_7202(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code pcmpgtd  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code pcmpgtd   mm0, [L1: +305419896]}
     */
    // Template#: 474, Serial#: 7040
    public void rip_pcmpgtd(final MMXRegister destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_pcmpgtd(destination, placeHolder);
        new rip_pcmpgtd_7040(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code pcmpgtw  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code pcmpgtw   xmm0, [L1: +305419896]}
     */
    // Template#: 475, Serial#: 7184
    public void rip_pcmpgtw(final AMD64XMMRegister destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_pcmpgtw(destination, placeHolder);
        new rip_pcmpgtw_7184(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code pcmpgtw  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code pcmpgtw   mm0, [L1: +305419896]}
     */
    // Template#: 476, Serial#: 7013
    public void rip_pcmpgtw(final MMXRegister destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_pcmpgtw(destination, placeHolder);
        new rip_pcmpgtw_7013(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code pinsrw  }<i>destination</i>, <i>label</i>, <i>imm8</i>
     * Example disassembly syntax: {@code pinsrw    xmm0, [L1: +305419896], 0x12}
     */
    // Template#: 477, Serial#: 8109
    public void rip_pinsrw(final AMD64XMMRegister destination, final Label label, final byte imm8) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_pinsrw(destination, placeHolder, imm8);
        new rip_pinsrw_8109(startPosition, currentPosition() - startPosition, destination, imm8, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code pinsrw  }<i>destination</i>, <i>label</i>, <i>imm8</i>
     * Example disassembly syntax: {@code pinsrw    mm0, [L1: +305419896], 0x12}
     */
    // Template#: 478, Serial#: 8037
    public void rip_pinsrw(final MMXRegister destination, final Label label, final byte imm8) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_pinsrw(destination, placeHolder, imm8);
        new rip_pinsrw_8037(startPosition, currentPosition() - startPosition, destination, imm8, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code pmaddwd  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code pmaddwd   xmm0, [L1: +305419896]}
     */
    // Template#: 479, Serial#: 9057
    public void rip_pmaddwd(final AMD64XMMRegister destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_pmaddwd(destination, placeHolder);
        new rip_pmaddwd_9057(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code pmaddwd  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code pmaddwd   mm0, [L1: +305419896]}
     */
    // Template#: 480, Serial#: 8928
    public void rip_pmaddwd(final MMXRegister destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_pmaddwd(destination, placeHolder);
        new rip_pmaddwd_8928(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code pmaxsw  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code pmaxsw    xmm0, [L1: +305419896]}
     */
    // Template#: 481, Serial#: 12413
    public void rip_pmaxsw(final AMD64XMMRegister destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_pmaxsw(destination, placeHolder);
        new rip_pmaxsw_12413(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code pmaxsw  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code pmaxsw    mm0, [L1: +305419896]}
     */
    // Template#: 482, Serial#: 12251
    public void rip_pmaxsw(final MMXRegister destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_pmaxsw(destination, placeHolder);
        new rip_pmaxsw_12251(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code pmaxub  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code pmaxub    xmm0, [L1: +305419896]}
     */
    // Template#: 483, Serial#: 12053
    public void rip_pmaxub(final AMD64XMMRegister destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_pmaxub(destination, placeHolder);
        new rip_pmaxub_12053(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code pmaxub  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code pmaxub    mm0, [L1: +305419896]}
     */
    // Template#: 484, Serial#: 11891
    public void rip_pmaxub(final MMXRegister destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_pmaxub(destination, placeHolder);
        new rip_pmaxub_11891(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code pminsw  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code pminsw    xmm0, [L1: +305419896]}
     */
    // Template#: 485, Serial#: 12341
    public void rip_pminsw(final AMD64XMMRegister destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_pminsw(destination, placeHolder);
        new rip_pminsw_12341(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code pminsw  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code pminsw    mm0, [L1: +305419896]}
     */
    // Template#: 486, Serial#: 12143
    public void rip_pminsw(final MMXRegister destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_pminsw(destination, placeHolder);
        new rip_pminsw_12143(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code pminub  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code pminub    xmm0, [L1: +305419896]}
     */
    // Template#: 487, Serial#: 11981
    public void rip_pminub(final AMD64XMMRegister destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_pminub(destination, placeHolder);
        new rip_pminub_11981(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code pminub  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code pminub    mm0, [L1: +305419896]}
     */
    // Template#: 488, Serial#: 11783
    public void rip_pminub(final MMXRegister destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_pminub(destination, placeHolder);
        new rip_pminub_11783(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code pmulhuw  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code pmulhuw   xmm0, [L1: +305419896]}
     */
    // Template#: 489, Serial#: 8706
    public void rip_pmulhuw(final AMD64XMMRegister destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_pmulhuw(destination, placeHolder);
        new rip_pmulhuw_8706(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code pmulhuw  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code pmulhuw   mm0, [L1: +305419896]}
     */
    // Template#: 490, Serial#: 8556
    public void rip_pmulhuw(final MMXRegister destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_pmulhuw(destination, placeHolder);
        new rip_pmulhuw_8556(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code pmulhw  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code pmulhw    xmm0, [L1: +305419896]}
     */
    // Template#: 491, Serial#: 8724
    public void rip_pmulhw(final AMD64XMMRegister destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_pmulhw(destination, placeHolder);
        new rip_pmulhw_8724(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code pmulhw  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code pmulhw    mm0, [L1: +305419896]}
     */
    // Template#: 492, Serial#: 8583
    public void rip_pmulhw(final MMXRegister destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_pmulhw(destination, placeHolder);
        new rip_pmulhw_8583(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code pmullw  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code pmullw    xmm0, [L1: +305419896]}
     */
    // Template#: 493, Serial#: 8403
    public void rip_pmullw(final AMD64XMMRegister destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_pmullw(destination, placeHolder);
        new rip_pmullw_8403(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code pmullw  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code pmullw    mm0, [L1: +305419896]}
     */
    // Template#: 494, Serial#: 8283
    public void rip_pmullw(final MMXRegister destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_pmullw(destination, placeHolder);
        new rip_pmullw_8283(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code pmuludq  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code pmuludq   xmm0, [L1: +305419896]}
     */
    // Template#: 495, Serial#: 9039
    public void rip_pmuludq(final AMD64XMMRegister destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_pmuludq(destination, placeHolder);
        new rip_pmuludq_9039(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code pmuludq  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code pmuludq   mm0, [L1: +305419896]}
     */
    // Template#: 496, Serial#: 8901
    public void rip_pmuludq(final MMXRegister destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_pmuludq(destination, placeHolder);
        new rip_pmuludq_8901(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code pop  }<i>label</i>
     * Example disassembly syntax: {@code pop       [L1: +305419896]}
     */
    // Template#: 497, Serial#: 3842
    public void rip_pop(final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_pop(placeHolder);
        new rip_pop_3842(startPosition, currentPosition() - startPosition, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code por  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code por       xmm0, [L1: +305419896]}
     */
    // Template#: 498, Serial#: 12359
    public void rip_por(final AMD64XMMRegister destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_por(destination, placeHolder);
        new rip_por_12359(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code por  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code por       mm0, [L1: +305419896]}
     */
    // Template#: 499, Serial#: 12170
    public void rip_por(final MMXRegister destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_por(destination, placeHolder);
        new rip_por_12170(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code prefetch  }<i>label</i>
     * Example disassembly syntax: {@code prefetch  [L1: +305419896]}
     */
    // Template#: 500, Serial#: 9129
    public void rip_prefetch(final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_prefetch(placeHolder);
        new rip_prefetch_9129(startPosition, currentPosition() - startPosition, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code prefetchnta  }<i>label</i>
     * Example disassembly syntax: {@code prefetchnta  [L1: +305419896]}
     */
    // Template#: 501, Serial#: 9204
    public void rip_prefetchnta(final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_prefetchnta(placeHolder);
        new rip_prefetchnta_9204(startPosition, currentPosition() - startPosition, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code prefetcht0  }<i>label</i>
     * Example disassembly syntax: {@code prefetcht0  [L1: +305419896]}
     */
    // Template#: 502, Serial#: 9208
    public void rip_prefetcht0(final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_prefetcht0(placeHolder);
        new rip_prefetcht0_9208(startPosition, currentPosition() - startPosition, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code prefetcht1  }<i>label</i>
     * Example disassembly syntax: {@code prefetcht1  [L1: +305419896]}
     */
    // Template#: 503, Serial#: 9212
    public void rip_prefetcht1(final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_prefetcht1(placeHolder);
        new rip_prefetcht1_9212(startPosition, currentPosition() - startPosition, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code prefetcht2  }<i>label</i>
     * Example disassembly syntax: {@code prefetcht2  [L1: +305419896]}
     */
    // Template#: 504, Serial#: 9216
    public void rip_prefetcht2(final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_prefetcht2(placeHolder);
        new rip_prefetcht2_9216(startPosition, currentPosition() - startPosition, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code prefetchw  }<i>label</i>
     * Example disassembly syntax: {@code prefetchw  [L1: +305419896]}
     */
    // Template#: 505, Serial#: 9133
    public void rip_prefetchw(final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_prefetchw(placeHolder);
        new rip_prefetchw_9133(startPosition, currentPosition() - startPosition, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code psadbw  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code psadbw    xmm0, [L1: +305419896]}
     */
    // Template#: 506, Serial#: 9075
    public void rip_psadbw(final AMD64XMMRegister destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_psadbw(destination, placeHolder);
        new rip_psadbw_9075(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code psadbw  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code psadbw    mm0, [L1: +305419896]}
     */
    // Template#: 507, Serial#: 8955
    public void rip_psadbw(final MMXRegister destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_psadbw(destination, placeHolder);
        new rip_psadbw_8955(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code pshufd  }<i>destination</i>, <i>label</i>, <i>imm8</i>
     * Example disassembly syntax: {@code pshufd    xmm0, [L1: +305419896], 0x12}
     */
    // Template#: 508, Serial#: 7373
    public void rip_pshufd(final AMD64XMMRegister destination, final Label label, final byte imm8) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_pshufd(destination, placeHolder, imm8);
        new rip_pshufd_7373(startPosition, currentPosition() - startPosition, destination, imm8, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code pshufhw  }<i>destination</i>, <i>label</i>, <i>imm8</i>
     * Example disassembly syntax: {@code pshufhw   xmm0, [L1: +305419896], 0x12}
     */
    // Template#: 509, Serial#: 7493
    public void rip_pshufhw(final AMD64XMMRegister destination, final Label label, final byte imm8) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_pshufhw(destination, placeHolder, imm8);
        new rip_pshufhw_7493(startPosition, currentPosition() - startPosition, destination, imm8, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code pshuflw  }<i>destination</i>, <i>label</i>, <i>imm8</i>
     * Example disassembly syntax: {@code pshuflw   xmm0, [L1: +305419896], 0x12}
     */
    // Template#: 510, Serial#: 7475
    public void rip_pshuflw(final AMD64XMMRegister destination, final Label label, final byte imm8) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_pshuflw(destination, placeHolder, imm8);
        new rip_pshuflw_7475(startPosition, currentPosition() - startPosition, destination, imm8, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code pshufw  }<i>destination</i>, <i>label</i>, <i>imm8</i>
     * Example disassembly syntax: {@code pshufw    mm0, [L1: +305419896], 0x12}
     */
    // Template#: 511, Serial#: 7238
    public void rip_pshufw(final MMXRegister destination, final Label label, final byte imm8) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_pshufw(destination, placeHolder, imm8);
        new rip_pshufw_7238(startPosition, currentPosition() - startPosition, destination, imm8, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code pslld  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code pslld     xmm0, [L1: +305419896]}
     */
    // Template#: 512, Serial#: 9003
    public void rip_pslld(final AMD64XMMRegister destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_pslld(destination, placeHolder);
        new rip_pslld_9003(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code pslld  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code pslld     mm0, [L1: +305419896]}
     */
    // Template#: 513, Serial#: 8847
    public void rip_pslld(final MMXRegister destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_pslld(destination, placeHolder);
        new rip_pslld_8847(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code psllq  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code psllq     xmm0, [L1: +305419896]}
     */
    // Template#: 514, Serial#: 9021
    public void rip_psllq(final AMD64XMMRegister destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_psllq(destination, placeHolder);
        new rip_psllq_9021(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code psllq  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code psllq     mm0, [L1: +305419896]}
     */
    // Template#: 515, Serial#: 8874
    public void rip_psllq(final MMXRegister destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_psllq(destination, placeHolder);
        new rip_psllq_8874(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code psllw  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code psllw     xmm0, [L1: +305419896]}
     */
    // Template#: 516, Serial#: 8985
    public void rip_psllw(final AMD64XMMRegister destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_psllw(destination, placeHolder);
        new rip_psllw_8985(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code psllw  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code psllw     mm0, [L1: +305419896]}
     */
    // Template#: 517, Serial#: 8820
    public void rip_psllw(final MMXRegister destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_psllw(destination, placeHolder);
        new rip_psllw_8820(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code psrad  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code psrad     xmm0, [L1: +305419896]}
     */
    // Template#: 518, Serial#: 8670
    public void rip_psrad(final AMD64XMMRegister destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_psrad(destination, placeHolder);
        new rip_psrad_8670(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code psrad  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code psrad     mm0, [L1: +305419896]}
     */
    // Template#: 519, Serial#: 8502
    public void rip_psrad(final MMXRegister destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_psrad(destination, placeHolder);
        new rip_psrad_8502(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code psraw  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code psraw     xmm0, [L1: +305419896]}
     */
    // Template#: 520, Serial#: 8652
    public void rip_psraw(final AMD64XMMRegister destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_psraw(destination, placeHolder);
        new rip_psraw_8652(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code psraw  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code psraw     mm0, [L1: +305419896]}
     */
    // Template#: 521, Serial#: 8475
    public void rip_psraw(final MMXRegister destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_psraw(destination, placeHolder);
        new rip_psraw_8475(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code psrld  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code psrld     xmm0, [L1: +305419896]}
     */
    // Template#: 522, Serial#: 8349
    public void rip_psrld(final AMD64XMMRegister destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_psrld(destination, placeHolder);
        new rip_psrld_8349(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code psrld  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code psrld     mm0, [L1: +305419896]}
     */
    // Template#: 523, Serial#: 8202
    public void rip_psrld(final MMXRegister destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_psrld(destination, placeHolder);
        new rip_psrld_8202(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code psrlq  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code psrlq     xmm0, [L1: +305419896]}
     */
    // Template#: 524, Serial#: 8367
    public void rip_psrlq(final AMD64XMMRegister destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_psrlq(destination, placeHolder);
        new rip_psrlq_8367(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code psrlq  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code psrlq     mm0, [L1: +305419896]}
     */
    // Template#: 525, Serial#: 8229
    public void rip_psrlq(final MMXRegister destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_psrlq(destination, placeHolder);
        new rip_psrlq_8229(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code psrlw  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code psrlw     xmm0, [L1: +305419896]}
     */
    // Template#: 526, Serial#: 8331
    public void rip_psrlw(final AMD64XMMRegister destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_psrlw(destination, placeHolder);
        new rip_psrlw_8331(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code psrlw  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code psrlw     mm0, [L1: +305419896]}
     */
    // Template#: 527, Serial#: 8175
    public void rip_psrlw(final MMXRegister destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_psrlw(destination, placeHolder);
        new rip_psrlw_8175(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code psubb  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code psubb     xmm0, [L1: +305419896]}
     */
    // Template#: 528, Serial#: 12638
    public void rip_psubb(final AMD64XMMRegister destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_psubb(destination, placeHolder);
        new rip_psubb_12638(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code psubb  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code psubb     mm0, [L1: +305419896]}
     */
    // Template#: 529, Serial#: 12449
    public void rip_psubb(final MMXRegister destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_psubb(destination, placeHolder);
        new rip_psubb_12449(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code psubd  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code psubd     xmm0, [L1: +305419896]}
     */
    // Template#: 530, Serial#: 12674
    public void rip_psubd(final AMD64XMMRegister destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_psubd(destination, placeHolder);
        new rip_psubd_12674(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code psubd  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code psubd     mm0, [L1: +305419896]}
     */
    // Template#: 531, Serial#: 12503
    public void rip_psubd(final MMXRegister destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_psubd(destination, placeHolder);
        new rip_psubd_12503(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code psubq  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code psubq     xmm0, [L1: +305419896]}
     */
    // Template#: 532, Serial#: 12692
    public void rip_psubq(final AMD64XMMRegister destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_psubq(destination, placeHolder);
        new rip_psubq_12692(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code psubq  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code psubq     mm0, [L1: +305419896]}
     */
    // Template#: 533, Serial#: 12530
    public void rip_psubq(final MMXRegister destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_psubq(destination, placeHolder);
        new rip_psubq_12530(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code psubsb  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code psubsb    xmm0, [L1: +305419896]}
     */
    // Template#: 534, Serial#: 12305
    public void rip_psubsb(final AMD64XMMRegister destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_psubsb(destination, placeHolder);
        new rip_psubsb_12305(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code psubsb  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code psubsb    mm0, [L1: +305419896]}
     */
    // Template#: 535, Serial#: 12089
    public void rip_psubsb(final MMXRegister destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_psubsb(destination, placeHolder);
        new rip_psubsb_12089(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code psubsw  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code psubsw    xmm0, [L1: +305419896]}
     */
    // Template#: 536, Serial#: 12323
    public void rip_psubsw(final AMD64XMMRegister destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_psubsw(destination, placeHolder);
        new rip_psubsw_12323(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code psubsw  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code psubsw    mm0, [L1: +305419896]}
     */
    // Template#: 537, Serial#: 12116
    public void rip_psubsw(final MMXRegister destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_psubsw(destination, placeHolder);
        new rip_psubsw_12116(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code psubusb  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code psubusb   xmm0, [L1: +305419896]}
     */
    // Template#: 538, Serial#: 11945
    public void rip_psubusb(final AMD64XMMRegister destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_psubusb(destination, placeHolder);
        new rip_psubusb_11945(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code psubusb  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code psubusb   mm0, [L1: +305419896]}
     */
    // Template#: 539, Serial#: 11729
    public void rip_psubusb(final MMXRegister destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_psubusb(destination, placeHolder);
        new rip_psubusb_11729(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code psubusw  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code psubusw   xmm0, [L1: +305419896]}
     */
    // Template#: 540, Serial#: 11963
    public void rip_psubusw(final AMD64XMMRegister destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_psubusw(destination, placeHolder);
        new rip_psubusw_11963(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code psubusw  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code psubusw   mm0, [L1: +305419896]}
     */
    // Template#: 541, Serial#: 11756
    public void rip_psubusw(final MMXRegister destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_psubusw(destination, placeHolder);
        new rip_psubusw_11756(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code psubw  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code psubw     xmm0, [L1: +305419896]}
     */
    // Template#: 542, Serial#: 12656
    public void rip_psubw(final AMD64XMMRegister destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_psubw(destination, placeHolder);
        new rip_psubw_12656(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code psubw  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code psubw     mm0, [L1: +305419896]}
     */
    // Template#: 543, Serial#: 12476
    public void rip_psubw(final MMXRegister destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_psubw(destination, placeHolder);
        new rip_psubw_12476(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code punpckhbw  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code punpckhbw  xmm0, [L1: +305419896]}
     */
    // Template#: 544, Serial#: 10674
    public void rip_punpckhbw(final AMD64XMMRegister destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_punpckhbw(destination, placeHolder);
        new rip_punpckhbw_10674(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code punpckhbw  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code punpckhbw  mm0, [L1: +305419896]}
     */
    // Template#: 545, Serial#: 10521
    public void rip_punpckhbw(final MMXRegister destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_punpckhbw(destination, placeHolder);
        new rip_punpckhbw_10521(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code punpckhdq  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code punpckhdq  xmm0, [L1: +305419896]}
     */
    // Template#: 546, Serial#: 10710
    public void rip_punpckhdq(final AMD64XMMRegister destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_punpckhdq(destination, placeHolder);
        new rip_punpckhdq_10710(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code punpckhdq  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code punpckhdq  mm0, [L1: +305419896]}
     */
    // Template#: 547, Serial#: 10575
    public void rip_punpckhdq(final MMXRegister destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_punpckhdq(destination, placeHolder);
        new rip_punpckhdq_10575(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code punpckhqdq  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code punpckhqdq  xmm0, [L1: +305419896]}
     */
    // Template#: 548, Serial#: 10764
    public void rip_punpckhqdq(final AMD64XMMRegister destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_punpckhqdq(destination, placeHolder);
        new rip_punpckhqdq_10764(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code punpckhwd  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code punpckhwd  xmm0, [L1: +305419896]}
     */
    // Template#: 549, Serial#: 10692
    public void rip_punpckhwd(final AMD64XMMRegister destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_punpckhwd(destination, placeHolder);
        new rip_punpckhwd_10692(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code punpckhwd  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code punpckhwd  mm0, [L1: +305419896]}
     */
    // Template#: 550, Serial#: 10548
    public void rip_punpckhwd(final MMXRegister destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_punpckhwd(destination, placeHolder);
        new rip_punpckhwd_10548(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code punpcklbw  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code punpcklbw  xmm0, [L1: +305419896]}
     */
    // Template#: 551, Serial#: 7094
    public void rip_punpcklbw(final AMD64XMMRegister destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_punpcklbw(destination, placeHolder);
        new rip_punpcklbw_7094(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code punpcklbw  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code punpcklbw  mm0, [L1: +305419896]}
     */
    // Template#: 552, Serial#: 6878
    public void rip_punpcklbw(final MMXRegister destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_punpcklbw(destination, placeHolder);
        new rip_punpcklbw_6878(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code punpckldq  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code punpckldq  xmm0, [L1: +305419896]}
     */
    // Template#: 553, Serial#: 7130
    public void rip_punpckldq(final AMD64XMMRegister destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_punpckldq(destination, placeHolder);
        new rip_punpckldq_7130(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code punpckldq  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code punpckldq  mm0, [L1: +305419896]}
     */
    // Template#: 554, Serial#: 6932
    public void rip_punpckldq(final MMXRegister destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_punpckldq(destination, placeHolder);
        new rip_punpckldq_6932(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code punpcklqdq  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code punpcklqdq  xmm0, [L1: +305419896]}
     */
    // Template#: 555, Serial#: 10746
    public void rip_punpcklqdq(final AMD64XMMRegister destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_punpcklqdq(destination, placeHolder);
        new rip_punpcklqdq_10746(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code punpcklwd  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code punpcklwd  xmm0, [L1: +305419896]}
     */
    // Template#: 556, Serial#: 7112
    public void rip_punpcklwd(final AMD64XMMRegister destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_punpcklwd(destination, placeHolder);
        new rip_punpcklwd_7112(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code punpcklwd  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code punpcklwd  mm0, [L1: +305419896]}
     */
    // Template#: 557, Serial#: 6905
    public void rip_punpcklwd(final MMXRegister destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_punpcklwd(destination, placeHolder);
        new rip_punpcklwd_6905(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code push  }<i>label</i>
     * Example disassembly syntax: {@code push      [L1: +305419896]}
     */
    // Template#: 558, Serial#: 5440
    public void rip_push(final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_push(placeHolder);
        new rip_push_5440(startPosition, currentPosition() - startPosition, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code pxor  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code pxor      xmm0, [L1: +305419896]}
     */
    // Template#: 559, Serial#: 12431
    public void rip_pxor(final AMD64XMMRegister destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_pxor(destination, placeHolder);
        new rip_pxor_12431(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code pxor  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code pxor      mm0, [L1: +305419896]}
     */
    // Template#: 560, Serial#: 12278
    public void rip_pxor(final MMXRegister destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_pxor(destination, placeHolder);
        new rip_pxor_12278(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code rclb  }<i>label</i>
     * Example disassembly syntax: {@code rclb      [L1: +305419896], 0x1}
     */
    // Template#: 561, Serial#: 1787
    public void rip_rclb___1(final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_rclb___1(placeHolder);
        new rip_rclb___1_1787(startPosition, currentPosition() - startPosition, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code rcll  }<i>label</i>
     * Example disassembly syntax: {@code rcll      [L1: +305419896], 0x1}
     */
    // Template#: 562, Serial#: 2003
    public void rip_rcll___1(final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_rcll___1(placeHolder);
        new rip_rcll___1_2003(startPosition, currentPosition() - startPosition, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code rclq  }<i>label</i>
     * Example disassembly syntax: {@code rclq      [L1: +305419896], 0x1}
     */
    // Template#: 563, Serial#: 2075
    public void rip_rclq___1(final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_rclq___1(placeHolder);
        new rip_rclq___1_2075(startPosition, currentPosition() - startPosition, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code rclw  }<i>label</i>
     * Example disassembly syntax: {@code rclw      [L1: +305419896], 0x1}
     */
    // Template#: 564, Serial#: 2147
    public void rip_rclw___1(final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_rclw___1(placeHolder);
        new rip_rclw___1_2147(startPosition, currentPosition() - startPosition, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code rclb  }<i>label</i>
     * Example disassembly syntax: {@code rclb      [L1: +305419896], cl}
     */
    // Template#: 565, Serial#: 2219
    public void rip_rclb___CL(final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_rclb___CL(placeHolder);
        new rip_rclb___CL_2219(startPosition, currentPosition() - startPosition, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code rcll  }<i>label</i>
     * Example disassembly syntax: {@code rcll      [L1: +305419896], cl}
     */
    // Template#: 566, Serial#: 2435
    public void rip_rcll___CL(final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_rcll___CL(placeHolder);
        new rip_rcll___CL_2435(startPosition, currentPosition() - startPosition, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code rclq  }<i>label</i>
     * Example disassembly syntax: {@code rclq      [L1: +305419896], cl}
     */
    // Template#: 567, Serial#: 2507
    public void rip_rclq___CL(final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_rclq___CL(placeHolder);
        new rip_rclq___CL_2507(startPosition, currentPosition() - startPosition, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code rclw  }<i>label</i>
     * Example disassembly syntax: {@code rclw      [L1: +305419896], cl}
     */
    // Template#: 568, Serial#: 2579
    public void rip_rclw___CL(final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_rclw___CL(placeHolder);
        new rip_rclw___CL_2579(startPosition, currentPosition() - startPosition, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code rclb  }<i>label</i>, <i>imm8</i>
     * Example disassembly syntax: {@code rclb      [L1: +305419896], 0x12}
     */
    // Template#: 569, Serial#: 1297
    public void rip_rclb(final Label label, final byte imm8) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_rclb(placeHolder, imm8);
        new rip_rclb_1297(startPosition, currentPosition() - startPosition, imm8, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code rcll  }<i>label</i>, <i>imm8</i>
     * Example disassembly syntax: {@code rcll      [L1: +305419896], 0x12}
     */
    // Template#: 570, Serial#: 1513
    public void rip_rcll(final Label label, final byte imm8) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_rcll(placeHolder, imm8);
        new rip_rcll_1513(startPosition, currentPosition() - startPosition, imm8, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code rclq  }<i>label</i>, <i>imm8</i>
     * Example disassembly syntax: {@code rclq      [L1: +305419896], 0x12}
     */
    // Template#: 571, Serial#: 1585
    public void rip_rclq(final Label label, final byte imm8) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_rclq(placeHolder, imm8);
        new rip_rclq_1585(startPosition, currentPosition() - startPosition, imm8, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code rclw  }<i>label</i>, <i>imm8</i>
     * Example disassembly syntax: {@code rclw      [L1: +305419896], 0x12}
     */
    // Template#: 572, Serial#: 1657
    public void rip_rclw(final Label label, final byte imm8) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_rclw(placeHolder, imm8);
        new rip_rclw_1657(startPosition, currentPosition() - startPosition, imm8, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code rcpps  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code rcpps     xmm0, [L1: +305419896]}
     */
    // Template#: 573, Serial#: 6623
    public void rip_rcpps(final AMD64XMMRegister destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_rcpps(destination, placeHolder);
        new rip_rcpps_6623(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code rcpss  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code rcpss     xmm0, [L1: +305419896]}
     */
    // Template#: 574, Serial#: 6860
    public void rip_rcpss(final AMD64XMMRegister destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_rcpss(destination, placeHolder);
        new rip_rcpss_6860(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code rcrb  }<i>label</i>
     * Example disassembly syntax: {@code rcrb      [L1: +305419896], 0x1}
     */
    // Template#: 575, Serial#: 1791
    public void rip_rcrb___1(final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_rcrb___1(placeHolder);
        new rip_rcrb___1_1791(startPosition, currentPosition() - startPosition, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code rcrl  }<i>label</i>
     * Example disassembly syntax: {@code rcrl      [L1: +305419896], 0x1}
     */
    // Template#: 576, Serial#: 2007
    public void rip_rcrl___1(final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_rcrl___1(placeHolder);
        new rip_rcrl___1_2007(startPosition, currentPosition() - startPosition, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code rcrq  }<i>label</i>
     * Example disassembly syntax: {@code rcrq      [L1: +305419896], 0x1}
     */
    // Template#: 577, Serial#: 2079
    public void rip_rcrq___1(final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_rcrq___1(placeHolder);
        new rip_rcrq___1_2079(startPosition, currentPosition() - startPosition, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code rcrw  }<i>label</i>
     * Example disassembly syntax: {@code rcrw      [L1: +305419896], 0x1}
     */
    // Template#: 578, Serial#: 2151
    public void rip_rcrw___1(final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_rcrw___1(placeHolder);
        new rip_rcrw___1_2151(startPosition, currentPosition() - startPosition, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code rcrb  }<i>label</i>
     * Example disassembly syntax: {@code rcrb      [L1: +305419896], cl}
     */
    // Template#: 579, Serial#: 2223
    public void rip_rcrb___CL(final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_rcrb___CL(placeHolder);
        new rip_rcrb___CL_2223(startPosition, currentPosition() - startPosition, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code rcrl  }<i>label</i>
     * Example disassembly syntax: {@code rcrl      [L1: +305419896], cl}
     */
    // Template#: 580, Serial#: 2439
    public void rip_rcrl___CL(final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_rcrl___CL(placeHolder);
        new rip_rcrl___CL_2439(startPosition, currentPosition() - startPosition, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code rcrq  }<i>label</i>
     * Example disassembly syntax: {@code rcrq      [L1: +305419896], cl}
     */
    // Template#: 581, Serial#: 2511
    public void rip_rcrq___CL(final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_rcrq___CL(placeHolder);
        new rip_rcrq___CL_2511(startPosition, currentPosition() - startPosition, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code rcrw  }<i>label</i>
     * Example disassembly syntax: {@code rcrw      [L1: +305419896], cl}
     */
    // Template#: 582, Serial#: 2583
    public void rip_rcrw___CL(final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_rcrw___CL(placeHolder);
        new rip_rcrw___CL_2583(startPosition, currentPosition() - startPosition, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code rcrb  }<i>label</i>, <i>imm8</i>
     * Example disassembly syntax: {@code rcrb      [L1: +305419896], 0x12}
     */
    // Template#: 583, Serial#: 1301
    public void rip_rcrb(final Label label, final byte imm8) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_rcrb(placeHolder, imm8);
        new rip_rcrb_1301(startPosition, currentPosition() - startPosition, imm8, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code rcrl  }<i>label</i>, <i>imm8</i>
     * Example disassembly syntax: {@code rcrl      [L1: +305419896], 0x12}
     */
    // Template#: 584, Serial#: 1517
    public void rip_rcrl(final Label label, final byte imm8) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_rcrl(placeHolder, imm8);
        new rip_rcrl_1517(startPosition, currentPosition() - startPosition, imm8, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code rcrq  }<i>label</i>, <i>imm8</i>
     * Example disassembly syntax: {@code rcrq      [L1: +305419896], 0x12}
     */
    // Template#: 585, Serial#: 1589
    public void rip_rcrq(final Label label, final byte imm8) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_rcrq(placeHolder, imm8);
        new rip_rcrq_1589(startPosition, currentPosition() - startPosition, imm8, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code rcrw  }<i>label</i>, <i>imm8</i>
     * Example disassembly syntax: {@code rcrw      [L1: +305419896], 0x12}
     */
    // Template#: 586, Serial#: 1661
    public void rip_rcrw(final Label label, final byte imm8) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_rcrw(placeHolder, imm8);
        new rip_rcrw_1661(startPosition, currentPosition() - startPosition, imm8, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code rolb  }<i>label</i>
     * Example disassembly syntax: {@code rolb      [L1: +305419896], 0x1}
     */
    // Template#: 587, Serial#: 1779
    public void rip_rolb___1(final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_rolb___1(placeHolder);
        new rip_rolb___1_1779(startPosition, currentPosition() - startPosition, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code roll  }<i>label</i>
     * Example disassembly syntax: {@code roll      [L1: +305419896], 0x1}
     */
    // Template#: 588, Serial#: 1995
    public void rip_roll___1(final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_roll___1(placeHolder);
        new rip_roll___1_1995(startPosition, currentPosition() - startPosition, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code rolq  }<i>label</i>
     * Example disassembly syntax: {@code rolq      [L1: +305419896], 0x1}
     */
    // Template#: 589, Serial#: 2067
    public void rip_rolq___1(final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_rolq___1(placeHolder);
        new rip_rolq___1_2067(startPosition, currentPosition() - startPosition, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code rolw  }<i>label</i>
     * Example disassembly syntax: {@code rolw      [L1: +305419896], 0x1}
     */
    // Template#: 590, Serial#: 2139
    public void rip_rolw___1(final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_rolw___1(placeHolder);
        new rip_rolw___1_2139(startPosition, currentPosition() - startPosition, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code rolb  }<i>label</i>
     * Example disassembly syntax: {@code rolb      [L1: +305419896], cl}
     */
    // Template#: 591, Serial#: 2211
    public void rip_rolb___CL(final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_rolb___CL(placeHolder);
        new rip_rolb___CL_2211(startPosition, currentPosition() - startPosition, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code roll  }<i>label</i>
     * Example disassembly syntax: {@code roll      [L1: +305419896], cl}
     */
    // Template#: 592, Serial#: 2427
    public void rip_roll___CL(final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_roll___CL(placeHolder);
        new rip_roll___CL_2427(startPosition, currentPosition() - startPosition, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code rolq  }<i>label</i>
     * Example disassembly syntax: {@code rolq      [L1: +305419896], cl}
     */
    // Template#: 593, Serial#: 2499
    public void rip_rolq___CL(final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_rolq___CL(placeHolder);
        new rip_rolq___CL_2499(startPosition, currentPosition() - startPosition, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code rolw  }<i>label</i>
     * Example disassembly syntax: {@code rolw      [L1: +305419896], cl}
     */
    // Template#: 594, Serial#: 2571
    public void rip_rolw___CL(final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_rolw___CL(placeHolder);
        new rip_rolw___CL_2571(startPosition, currentPosition() - startPosition, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code rolb  }<i>label</i>, <i>imm8</i>
     * Example disassembly syntax: {@code rolb      [L1: +305419896], 0x12}
     */
    // Template#: 595, Serial#: 1289
    public void rip_rolb(final Label label, final byte imm8) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_rolb(placeHolder, imm8);
        new rip_rolb_1289(startPosition, currentPosition() - startPosition, imm8, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code roll  }<i>label</i>, <i>imm8</i>
     * Example disassembly syntax: {@code roll      [L1: +305419896], 0x12}
     */
    // Template#: 596, Serial#: 1505
    public void rip_roll(final Label label, final byte imm8) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_roll(placeHolder, imm8);
        new rip_roll_1505(startPosition, currentPosition() - startPosition, imm8, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code rolq  }<i>label</i>, <i>imm8</i>
     * Example disassembly syntax: {@code rolq      [L1: +305419896], 0x12}
     */
    // Template#: 597, Serial#: 1577
    public void rip_rolq(final Label label, final byte imm8) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_rolq(placeHolder, imm8);
        new rip_rolq_1577(startPosition, currentPosition() - startPosition, imm8, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code rolw  }<i>label</i>, <i>imm8</i>
     * Example disassembly syntax: {@code rolw      [L1: +305419896], 0x12}
     */
    // Template#: 598, Serial#: 1649
    public void rip_rolw(final Label label, final byte imm8) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_rolw(placeHolder, imm8);
        new rip_rolw_1649(startPosition, currentPosition() - startPosition, imm8, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code rorb  }<i>label</i>
     * Example disassembly syntax: {@code rorb      [L1: +305419896], 0x1}
     */
    // Template#: 599, Serial#: 1783
    public void rip_rorb___1(final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_rorb___1(placeHolder);
        new rip_rorb___1_1783(startPosition, currentPosition() - startPosition, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code rorl  }<i>label</i>
     * Example disassembly syntax: {@code rorl      [L1: +305419896], 0x1}
     */
    // Template#: 600, Serial#: 1999
    public void rip_rorl___1(final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_rorl___1(placeHolder);
        new rip_rorl___1_1999(startPosition, currentPosition() - startPosition, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code rorq  }<i>label</i>
     * Example disassembly syntax: {@code rorq      [L1: +305419896], 0x1}
     */
    // Template#: 601, Serial#: 2071
    public void rip_rorq___1(final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_rorq___1(placeHolder);
        new rip_rorq___1_2071(startPosition, currentPosition() - startPosition, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code rorw  }<i>label</i>
     * Example disassembly syntax: {@code rorw      [L1: +305419896], 0x1}
     */
    // Template#: 602, Serial#: 2143
    public void rip_rorw___1(final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_rorw___1(placeHolder);
        new rip_rorw___1_2143(startPosition, currentPosition() - startPosition, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code rorb  }<i>label</i>
     * Example disassembly syntax: {@code rorb      [L1: +305419896], cl}
     */
    // Template#: 603, Serial#: 2215
    public void rip_rorb___CL(final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_rorb___CL(placeHolder);
        new rip_rorb___CL_2215(startPosition, currentPosition() - startPosition, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code rorl  }<i>label</i>
     * Example disassembly syntax: {@code rorl      [L1: +305419896], cl}
     */
    // Template#: 604, Serial#: 2431
    public void rip_rorl___CL(final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_rorl___CL(placeHolder);
        new rip_rorl___CL_2431(startPosition, currentPosition() - startPosition, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code rorq  }<i>label</i>
     * Example disassembly syntax: {@code rorq      [L1: +305419896], cl}
     */
    // Template#: 605, Serial#: 2503
    public void rip_rorq___CL(final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_rorq___CL(placeHolder);
        new rip_rorq___CL_2503(startPosition, currentPosition() - startPosition, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code rorw  }<i>label</i>
     * Example disassembly syntax: {@code rorw      [L1: +305419896], cl}
     */
    // Template#: 606, Serial#: 2575
    public void rip_rorw___CL(final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_rorw___CL(placeHolder);
        new rip_rorw___CL_2575(startPosition, currentPosition() - startPosition, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code rorb  }<i>label</i>, <i>imm8</i>
     * Example disassembly syntax: {@code rorb      [L1: +305419896], 0x12}
     */
    // Template#: 607, Serial#: 1293
    public void rip_rorb(final Label label, final byte imm8) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_rorb(placeHolder, imm8);
        new rip_rorb_1293(startPosition, currentPosition() - startPosition, imm8, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code rorl  }<i>label</i>, <i>imm8</i>
     * Example disassembly syntax: {@code rorl      [L1: +305419896], 0x12}
     */
    // Template#: 608, Serial#: 1509
    public void rip_rorl(final Label label, final byte imm8) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_rorl(placeHolder, imm8);
        new rip_rorl_1509(startPosition, currentPosition() - startPosition, imm8, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code rorq  }<i>label</i>, <i>imm8</i>
     * Example disassembly syntax: {@code rorq      [L1: +305419896], 0x12}
     */
    // Template#: 609, Serial#: 1581
    public void rip_rorq(final Label label, final byte imm8) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_rorq(placeHolder, imm8);
        new rip_rorq_1581(startPosition, currentPosition() - startPosition, imm8, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code rorw  }<i>label</i>, <i>imm8</i>
     * Example disassembly syntax: {@code rorw      [L1: +305419896], 0x12}
     */
    // Template#: 610, Serial#: 1653
    public void rip_rorw(final Label label, final byte imm8) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_rorw(placeHolder, imm8);
        new rip_rorw_1653(startPosition, currentPosition() - startPosition, imm8, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code rsqrtps  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code rsqrtps   xmm0, [L1: +305419896]}
     */
    // Template#: 611, Serial#: 6605
    public void rip_rsqrtps(final AMD64XMMRegister destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_rsqrtps(destination, placeHolder);
        new rip_rsqrtps_6605(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code rsqrtss  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code rsqrtss   xmm0, [L1: +305419896]}
     */
    // Template#: 612, Serial#: 6842
    public void rip_rsqrtss(final AMD64XMMRegister destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_rsqrtss(destination, placeHolder);
        new rip_rsqrtss_6842(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code sarb  }<i>label</i>
     * Example disassembly syntax: {@code sarb      [L1: +305419896], 0x1}
     */
    // Template#: 613, Serial#: 1807
    public void rip_sarb___1(final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_sarb___1(placeHolder);
        new rip_sarb___1_1807(startPosition, currentPosition() - startPosition, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code sarl  }<i>label</i>
     * Example disassembly syntax: {@code sarl      [L1: +305419896], 0x1}
     */
    // Template#: 614, Serial#: 2023
    public void rip_sarl___1(final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_sarl___1(placeHolder);
        new rip_sarl___1_2023(startPosition, currentPosition() - startPosition, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code sarq  }<i>label</i>
     * Example disassembly syntax: {@code sarq      [L1: +305419896], 0x1}
     */
    // Template#: 615, Serial#: 2095
    public void rip_sarq___1(final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_sarq___1(placeHolder);
        new rip_sarq___1_2095(startPosition, currentPosition() - startPosition, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code sarw  }<i>label</i>
     * Example disassembly syntax: {@code sarw      [L1: +305419896], 0x1}
     */
    // Template#: 616, Serial#: 2167
    public void rip_sarw___1(final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_sarw___1(placeHolder);
        new rip_sarw___1_2167(startPosition, currentPosition() - startPosition, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code sarb  }<i>label</i>
     * Example disassembly syntax: {@code sarb      [L1: +305419896], cl}
     */
    // Template#: 617, Serial#: 2239
    public void rip_sarb___CL(final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_sarb___CL(placeHolder);
        new rip_sarb___CL_2239(startPosition, currentPosition() - startPosition, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code sarl  }<i>label</i>
     * Example disassembly syntax: {@code sarl      [L1: +305419896], cl}
     */
    // Template#: 618, Serial#: 2455
    public void rip_sarl___CL(final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_sarl___CL(placeHolder);
        new rip_sarl___CL_2455(startPosition, currentPosition() - startPosition, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code sarq  }<i>label</i>
     * Example disassembly syntax: {@code sarq      [L1: +305419896], cl}
     */
    // Template#: 619, Serial#: 2527
    public void rip_sarq___CL(final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_sarq___CL(placeHolder);
        new rip_sarq___CL_2527(startPosition, currentPosition() - startPosition, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code sarw  }<i>label</i>
     * Example disassembly syntax: {@code sarw      [L1: +305419896], cl}
     */
    // Template#: 620, Serial#: 2599
    public void rip_sarw___CL(final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_sarw___CL(placeHolder);
        new rip_sarw___CL_2599(startPosition, currentPosition() - startPosition, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code sarb  }<i>label</i>, <i>imm8</i>
     * Example disassembly syntax: {@code sarb      [L1: +305419896], 0x12}
     */
    // Template#: 621, Serial#: 1317
    public void rip_sarb(final Label label, final byte imm8) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_sarb(placeHolder, imm8);
        new rip_sarb_1317(startPosition, currentPosition() - startPosition, imm8, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code sarl  }<i>label</i>, <i>imm8</i>
     * Example disassembly syntax: {@code sarl      [L1: +305419896], 0x12}
     */
    // Template#: 622, Serial#: 1533
    public void rip_sarl(final Label label, final byte imm8) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_sarl(placeHolder, imm8);
        new rip_sarl_1533(startPosition, currentPosition() - startPosition, imm8, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code sarq  }<i>label</i>, <i>imm8</i>
     * Example disassembly syntax: {@code sarq      [L1: +305419896], 0x12}
     */
    // Template#: 623, Serial#: 1605
    public void rip_sarq(final Label label, final byte imm8) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_sarq(placeHolder, imm8);
        new rip_sarq_1605(startPosition, currentPosition() - startPosition, imm8, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code sarw  }<i>label</i>, <i>imm8</i>
     * Example disassembly syntax: {@code sarw      [L1: +305419896], 0x12}
     */
    // Template#: 624, Serial#: 1677
    public void rip_sarw(final Label label, final byte imm8) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_sarw(placeHolder, imm8);
        new rip_sarw_1677(startPosition, currentPosition() - startPosition, imm8, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code sbb  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code sbb       ax, [L1: +305419896]}
     */
    // Template#: 625, Serial#: 3329
    public void rip_sbb(final AMD64GeneralRegister16 destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_sbb(destination, placeHolder);
        new rip_sbb_3329(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code sbb  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code sbb       eax, [L1: +305419896]}
     */
    // Template#: 626, Serial#: 3311
    public void rip_sbb(final AMD64GeneralRegister32 destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_sbb(destination, placeHolder);
        new rip_sbb_3311(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code sbb  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code sbb       rax, [L1: +305419896]}
     */
    // Template#: 627, Serial#: 3320
    public void rip_sbb(final AMD64GeneralRegister64 destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_sbb(destination, placeHolder);
        new rip_sbb_3320(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code sbb  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code sbb       al, [L1: +305419896]}
     */
    // Template#: 628, Serial#: 3284
    public void rip_sbb(final AMD64GeneralRegister8 destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_sbb(destination, placeHolder);
        new rip_sbb_3284(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code sbbb  }<i>label</i>, <i>imm8</i>
     * Example disassembly syntax: {@code sbbb      [L1: +305419896], 0x12}
     */
    // Template#: 629, Serial#: 512
    public void rip_sbbb(final Label label, final byte imm8) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_sbbb(placeHolder, imm8);
        new rip_sbbb_512(startPosition, currentPosition() - startPosition, imm8, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code sbbl  }<i>label</i>, <i>imm8</i>
     * Example disassembly syntax: {@code sbbl      [L1: +305419896], 0x12}
     */
    // Template#: 630, Serial#: 944
    public void rip_sbbl(final Label label, final byte imm8) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_sbbl(placeHolder, imm8);
        new rip_sbbl_944(startPosition, currentPosition() - startPosition, imm8, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code sbbq  }<i>label</i>, <i>imm8</i>
     * Example disassembly syntax: {@code sbbq      [L1: +305419896], 0x12}
     */
    // Template#: 631, Serial#: 1016
    public void rip_sbbq(final Label label, final byte imm8) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_sbbq(placeHolder, imm8);
        new rip_sbbq_1016(startPosition, currentPosition() - startPosition, imm8, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code sbbw  }<i>label</i>, <i>imm8</i>
     * Example disassembly syntax: {@code sbbw      [L1: +305419896], 0x12}
     */
    // Template#: 632, Serial#: 1088
    public void rip_sbbw(final Label label, final byte imm8) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_sbbw(placeHolder, imm8);
        new rip_sbbw_1088(startPosition, currentPosition() - startPosition, imm8, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code sbb  }<i>label</i>, <i>source</i>
     * Example disassembly syntax: {@code sbb       [L1: +305419896], ax}
     */
    // Template#: 633, Serial#: 3275
    public void rip_sbb(final Label label, final AMD64GeneralRegister16 source) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_sbb(placeHolder, source);
        new rip_sbb_3275(startPosition, currentPosition() - startPosition, source, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code sbb  }<i>label</i>, <i>source</i>
     * Example disassembly syntax: {@code sbb       [L1: +305419896], eax}
     */
    // Template#: 634, Serial#: 3257
    public void rip_sbb(final Label label, final AMD64GeneralRegister32 source) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_sbb(placeHolder, source);
        new rip_sbb_3257(startPosition, currentPosition() - startPosition, source, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code sbb  }<i>label</i>, <i>source</i>
     * Example disassembly syntax: {@code sbb       [L1: +305419896], rax}
     */
    // Template#: 635, Serial#: 3266
    public void rip_sbb(final Label label, final AMD64GeneralRegister64 source) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_sbb(placeHolder, source);
        new rip_sbb_3266(startPosition, currentPosition() - startPosition, source, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code sbb  }<i>label</i>, <i>source</i>
     * Example disassembly syntax: {@code sbb       [L1: +305419896], al}
     */
    // Template#: 636, Serial#: 3230
    public void rip_sbb(final Label label, final AMD64GeneralRegister8 source) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_sbb(placeHolder, source);
        new rip_sbb_3230(startPosition, currentPosition() - startPosition, source, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code sbbl  }<i>label</i>, <i>imm32</i>
     * Example disassembly syntax: {@code sbbl      [L1: +305419896], 0x12345678}
     */
    // Template#: 637, Serial#: 728
    public void rip_sbbl(final Label label, final int imm32) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_sbbl(placeHolder, imm32);
        new rip_sbbl_728(startPosition, currentPosition() - startPosition, imm32, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code sbbq  }<i>label</i>, <i>imm32</i>
     * Example disassembly syntax: {@code sbbq      [L1: +305419896], 0x12345678}
     */
    // Template#: 638, Serial#: 800
    public void rip_sbbq(final Label label, final int imm32) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_sbbq(placeHolder, imm32);
        new rip_sbbq_800(startPosition, currentPosition() - startPosition, imm32, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code sbbw  }<i>label</i>, <i>imm16</i>
     * Example disassembly syntax: {@code sbbw      [L1: +305419896], 0x1234}
     */
    // Template#: 639, Serial#: 872
    public void rip_sbbw(final Label label, final short imm16) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_sbbw(placeHolder, imm16);
        new rip_sbbw_872(startPosition, currentPosition() - startPosition, imm16, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code setb  }<i>label</i>
     * Example disassembly syntax: {@code setb      [L1: +305419896]}
     */
    // Template#: 640, Serial#: 7573
    public void rip_setb(final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_setb(placeHolder);
        new rip_setb_7573(startPosition, currentPosition() - startPosition, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code setbe  }<i>label</i>
     * Example disassembly syntax: {@code setbe     [L1: +305419896]}
     */
    // Template#: 641, Serial#: 7681
    public void rip_setbe(final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_setbe(placeHolder);
        new rip_setbe_7681(startPosition, currentPosition() - startPosition, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code setl  }<i>label</i>
     * Example disassembly syntax: {@code setl      [L1: +305419896]}
     */
    // Template#: 642, Serial#: 11141
    public void rip_setl(final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_setl(placeHolder);
        new rip_setl_11141(startPosition, currentPosition() - startPosition, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code setle  }<i>label</i>
     * Example disassembly syntax: {@code setle     [L1: +305419896]}
     */
    // Template#: 643, Serial#: 11195
    public void rip_setle(final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_setle(placeHolder);
        new rip_setle_11195(startPosition, currentPosition() - startPosition, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code setnb  }<i>label</i>
     * Example disassembly syntax: {@code setnb     [L1: +305419896]}
     */
    // Template#: 644, Serial#: 7600
    public void rip_setnb(final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_setnb(placeHolder);
        new rip_setnb_7600(startPosition, currentPosition() - startPosition, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code setnbe  }<i>label</i>
     * Example disassembly syntax: {@code setnbe    [L1: +305419896]}
     */
    // Template#: 645, Serial#: 7708
    public void rip_setnbe(final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_setnbe(placeHolder);
        new rip_setnbe_7708(startPosition, currentPosition() - startPosition, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code setnl  }<i>label</i>
     * Example disassembly syntax: {@code setnl     [L1: +305419896]}
     */
    // Template#: 646, Serial#: 11168
    public void rip_setnl(final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_setnl(placeHolder);
        new rip_setnl_11168(startPosition, currentPosition() - startPosition, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code setnle  }<i>label</i>
     * Example disassembly syntax: {@code setnle    [L1: +305419896]}
     */
    // Template#: 647, Serial#: 11222
    public void rip_setnle(final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_setnle(placeHolder);
        new rip_setnle_11222(startPosition, currentPosition() - startPosition, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code setno  }<i>label</i>
     * Example disassembly syntax: {@code setno     [L1: +305419896]}
     */
    // Template#: 648, Serial#: 7546
    public void rip_setno(final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_setno(placeHolder);
        new rip_setno_7546(startPosition, currentPosition() - startPosition, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code setnp  }<i>label</i>
     * Example disassembly syntax: {@code setnp     [L1: +305419896]}
     */
    // Template#: 649, Serial#: 11114
    public void rip_setnp(final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_setnp(placeHolder);
        new rip_setnp_11114(startPosition, currentPosition() - startPosition, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code setns  }<i>label</i>
     * Example disassembly syntax: {@code setns     [L1: +305419896]}
     */
    // Template#: 650, Serial#: 11060
    public void rip_setns(final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_setns(placeHolder);
        new rip_setns_11060(startPosition, currentPosition() - startPosition, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code setnz  }<i>label</i>
     * Example disassembly syntax: {@code setnz     [L1: +305419896]}
     */
    // Template#: 651, Serial#: 7654
    public void rip_setnz(final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_setnz(placeHolder);
        new rip_setnz_7654(startPosition, currentPosition() - startPosition, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code seto  }<i>label</i>
     * Example disassembly syntax: {@code seto      [L1: +305419896]}
     */
    // Template#: 652, Serial#: 7519
    public void rip_seto(final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_seto(placeHolder);
        new rip_seto_7519(startPosition, currentPosition() - startPosition, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code setp  }<i>label</i>
     * Example disassembly syntax: {@code setp      [L1: +305419896]}
     */
    // Template#: 653, Serial#: 11087
    public void rip_setp(final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_setp(placeHolder);
        new rip_setp_11087(startPosition, currentPosition() - startPosition, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code sets  }<i>label</i>
     * Example disassembly syntax: {@code sets      [L1: +305419896]}
     */
    // Template#: 654, Serial#: 11033
    public void rip_sets(final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_sets(placeHolder);
        new rip_sets_11033(startPosition, currentPosition() - startPosition, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code setz  }<i>label</i>
     * Example disassembly syntax: {@code setz      [L1: +305419896]}
     */
    // Template#: 655, Serial#: 7627
    public void rip_setz(final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_setz(placeHolder);
        new rip_setz_7627(startPosition, currentPosition() - startPosition, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code sgdt  }<i>label</i>
     * Example disassembly syntax: {@code sgdt      [L1: +305419896]}
     */
    // Template#: 656, Serial#: 5648
    public void rip_sgdt(final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_sgdt(placeHolder);
        new rip_sgdt_5648(startPosition, currentPosition() - startPosition, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code shlb  }<i>label</i>
     * Example disassembly syntax: {@code shlb      [L1: +305419896], 0x1}
     */
    // Template#: 657, Serial#: 1795
    public void rip_shlb___1(final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_shlb___1(placeHolder);
        new rip_shlb___1_1795(startPosition, currentPosition() - startPosition, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code shll  }<i>label</i>
     * Example disassembly syntax: {@code shll      [L1: +305419896], 0x1}
     */
    // Template#: 658, Serial#: 2011
    public void rip_shll___1(final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_shll___1(placeHolder);
        new rip_shll___1_2011(startPosition, currentPosition() - startPosition, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code shlq  }<i>label</i>
     * Example disassembly syntax: {@code shlq      [L1: +305419896], 0x1}
     */
    // Template#: 659, Serial#: 2083
    public void rip_shlq___1(final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_shlq___1(placeHolder);
        new rip_shlq___1_2083(startPosition, currentPosition() - startPosition, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code shlw  }<i>label</i>
     * Example disassembly syntax: {@code shlw      [L1: +305419896], 0x1}
     */
    // Template#: 660, Serial#: 2155
    public void rip_shlw___1(final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_shlw___1(placeHolder);
        new rip_shlw___1_2155(startPosition, currentPosition() - startPosition, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code shlb  }<i>label</i>
     * Example disassembly syntax: {@code shlb      [L1: +305419896], cl}
     */
    // Template#: 661, Serial#: 2227
    public void rip_shlb___CL(final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_shlb___CL(placeHolder);
        new rip_shlb___CL_2227(startPosition, currentPosition() - startPosition, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code shll  }<i>label</i>
     * Example disassembly syntax: {@code shll      [L1: +305419896], cl}
     */
    // Template#: 662, Serial#: 2443
    public void rip_shll___CL(final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_shll___CL(placeHolder);
        new rip_shll___CL_2443(startPosition, currentPosition() - startPosition, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code shlq  }<i>label</i>
     * Example disassembly syntax: {@code shlq      [L1: +305419896], cl}
     */
    // Template#: 663, Serial#: 2515
    public void rip_shlq___CL(final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_shlq___CL(placeHolder);
        new rip_shlq___CL_2515(startPosition, currentPosition() - startPosition, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code shlw  }<i>label</i>
     * Example disassembly syntax: {@code shlw      [L1: +305419896], cl}
     */
    // Template#: 664, Serial#: 2587
    public void rip_shlw___CL(final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_shlw___CL(placeHolder);
        new rip_shlw___CL_2587(startPosition, currentPosition() - startPosition, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code shlb  }<i>label</i>, <i>imm8</i>
     * Example disassembly syntax: {@code shlb      [L1: +305419896], 0x12}
     */
    // Template#: 665, Serial#: 1305
    public void rip_shlb(final Label label, final byte imm8) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_shlb(placeHolder, imm8);
        new rip_shlb_1305(startPosition, currentPosition() - startPosition, imm8, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code shll  }<i>label</i>, <i>imm8</i>
     * Example disassembly syntax: {@code shll      [L1: +305419896], 0x12}
     */
    // Template#: 666, Serial#: 1521
    public void rip_shll(final Label label, final byte imm8) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_shll(placeHolder, imm8);
        new rip_shll_1521(startPosition, currentPosition() - startPosition, imm8, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code shlq  }<i>label</i>, <i>imm8</i>
     * Example disassembly syntax: {@code shlq      [L1: +305419896], 0x12}
     */
    // Template#: 667, Serial#: 1593
    public void rip_shlq(final Label label, final byte imm8) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_shlq(placeHolder, imm8);
        new rip_shlq_1593(startPosition, currentPosition() - startPosition, imm8, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code shlw  }<i>label</i>, <i>imm8</i>
     * Example disassembly syntax: {@code shlw      [L1: +305419896], 0x12}
     */
    // Template#: 668, Serial#: 1665
    public void rip_shlw(final Label label, final byte imm8) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_shlw(placeHolder, imm8);
        new rip_shlw_1665(startPosition, currentPosition() - startPosition, imm8, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code shld  }<i>label</i>, <i>source</i>
     * Example disassembly syntax: {@code shld      [L1: +305419896], ax, cl}
     */
    // Template#: 669, Serial#: 7814
    public void rip_shld_CL(final Label label, final AMD64GeneralRegister16 source) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_shld_CL(placeHolder, source);
        new rip_shld_CL_7814(startPosition, currentPosition() - startPosition, source, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code shld  }<i>label</i>, <i>source</i>, <i>imm8</i>
     * Example disassembly syntax: {@code shld      [L1: +305419896], ax, 0x12}
     */
    // Template#: 670, Serial#: 7787
    public void rip_shld(final Label label, final AMD64GeneralRegister16 source, final byte imm8) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_shld(placeHolder, source, imm8);
        new rip_shld_7787(startPosition, currentPosition() - startPosition, source, imm8, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code shld  }<i>label</i>, <i>source</i>
     * Example disassembly syntax: {@code shld      [L1: +305419896], eax, cl}
     */
    // Template#: 671, Serial#: 7796
    public void rip_shld_CL(final Label label, final AMD64GeneralRegister32 source) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_shld_CL(placeHolder, source);
        new rip_shld_CL_7796(startPosition, currentPosition() - startPosition, source, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code shld  }<i>label</i>, <i>source</i>, <i>imm8</i>
     * Example disassembly syntax: {@code shld      [L1: +305419896], eax, 0x12}
     */
    // Template#: 672, Serial#: 7769
    public void rip_shld(final Label label, final AMD64GeneralRegister32 source, final byte imm8) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_shld(placeHolder, source, imm8);
        new rip_shld_7769(startPosition, currentPosition() - startPosition, source, imm8, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code shld  }<i>label</i>, <i>source</i>
     * Example disassembly syntax: {@code shld      [L1: +305419896], rax, cl}
     */
    // Template#: 673, Serial#: 7805
    public void rip_shld_CL(final Label label, final AMD64GeneralRegister64 source) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_shld_CL(placeHolder, source);
        new rip_shld_CL_7805(startPosition, currentPosition() - startPosition, source, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code shld  }<i>label</i>, <i>source</i>, <i>imm8</i>
     * Example disassembly syntax: {@code shld      [L1: +305419896], rax, 0x12}
     */
    // Template#: 674, Serial#: 7778
    public void rip_shld(final Label label, final AMD64GeneralRegister64 source, final byte imm8) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_shld(placeHolder, source, imm8);
        new rip_shld_7778(startPosition, currentPosition() - startPosition, source, imm8, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code shrb  }<i>label</i>
     * Example disassembly syntax: {@code shrb      [L1: +305419896], 0x1}
     */
    // Template#: 675, Serial#: 1799
    public void rip_shrb___1(final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_shrb___1(placeHolder);
        new rip_shrb___1_1799(startPosition, currentPosition() - startPosition, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code shrl  }<i>label</i>
     * Example disassembly syntax: {@code shrl      [L1: +305419896], 0x1}
     */
    // Template#: 676, Serial#: 2015
    public void rip_shrl___1(final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_shrl___1(placeHolder);
        new rip_shrl___1_2015(startPosition, currentPosition() - startPosition, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code shrq  }<i>label</i>
     * Example disassembly syntax: {@code shrq      [L1: +305419896], 0x1}
     */
    // Template#: 677, Serial#: 2087
    public void rip_shrq___1(final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_shrq___1(placeHolder);
        new rip_shrq___1_2087(startPosition, currentPosition() - startPosition, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code shrw  }<i>label</i>
     * Example disassembly syntax: {@code shrw      [L1: +305419896], 0x1}
     */
    // Template#: 678, Serial#: 2159
    public void rip_shrw___1(final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_shrw___1(placeHolder);
        new rip_shrw___1_2159(startPosition, currentPosition() - startPosition, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code shrb  }<i>label</i>
     * Example disassembly syntax: {@code shrb      [L1: +305419896], cl}
     */
    // Template#: 679, Serial#: 2231
    public void rip_shrb___CL(final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_shrb___CL(placeHolder);
        new rip_shrb___CL_2231(startPosition, currentPosition() - startPosition, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code shrl  }<i>label</i>
     * Example disassembly syntax: {@code shrl      [L1: +305419896], cl}
     */
    // Template#: 680, Serial#: 2447
    public void rip_shrl___CL(final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_shrl___CL(placeHolder);
        new rip_shrl___CL_2447(startPosition, currentPosition() - startPosition, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code shrq  }<i>label</i>
     * Example disassembly syntax: {@code shrq      [L1: +305419896], cl}
     */
    // Template#: 681, Serial#: 2519
    public void rip_shrq___CL(final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_shrq___CL(placeHolder);
        new rip_shrq___CL_2519(startPosition, currentPosition() - startPosition, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code shrw  }<i>label</i>
     * Example disassembly syntax: {@code shrw      [L1: +305419896], cl}
     */
    // Template#: 682, Serial#: 2591
    public void rip_shrw___CL(final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_shrw___CL(placeHolder);
        new rip_shrw___CL_2591(startPosition, currentPosition() - startPosition, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code shrb  }<i>label</i>, <i>imm8</i>
     * Example disassembly syntax: {@code shrb      [L1: +305419896], 0x12}
     */
    // Template#: 683, Serial#: 1309
    public void rip_shrb(final Label label, final byte imm8) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_shrb(placeHolder, imm8);
        new rip_shrb_1309(startPosition, currentPosition() - startPosition, imm8, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code shrl  }<i>label</i>, <i>imm8</i>
     * Example disassembly syntax: {@code shrl      [L1: +305419896], 0x12}
     */
    // Template#: 684, Serial#: 1525
    public void rip_shrl(final Label label, final byte imm8) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_shrl(placeHolder, imm8);
        new rip_shrl_1525(startPosition, currentPosition() - startPosition, imm8, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code shrq  }<i>label</i>, <i>imm8</i>
     * Example disassembly syntax: {@code shrq      [L1: +305419896], 0x12}
     */
    // Template#: 685, Serial#: 1597
    public void rip_shrq(final Label label, final byte imm8) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_shrq(placeHolder, imm8);
        new rip_shrq_1597(startPosition, currentPosition() - startPosition, imm8, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code shrw  }<i>label</i>, <i>imm8</i>
     * Example disassembly syntax: {@code shrw      [L1: +305419896], 0x12}
     */
    // Template#: 686, Serial#: 1669
    public void rip_shrw(final Label label, final byte imm8) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_shrw(placeHolder, imm8);
        new rip_shrw_1669(startPosition, currentPosition() - startPosition, imm8, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code shrd  }<i>label</i>, <i>source</i>
     * Example disassembly syntax: {@code shrd      [L1: +305419896], ax, cl}
     */
    // Template#: 687, Serial#: 11328
    public void rip_shrd_CL(final Label label, final AMD64GeneralRegister16 source) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_shrd_CL(placeHolder, source);
        new rip_shrd_CL_11328(startPosition, currentPosition() - startPosition, source, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code shrd  }<i>label</i>, <i>source</i>, <i>imm8</i>
     * Example disassembly syntax: {@code shrd      [L1: +305419896], ax, 0x12}
     */
    // Template#: 688, Serial#: 11301
    public void rip_shrd(final Label label, final AMD64GeneralRegister16 source, final byte imm8) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_shrd(placeHolder, source, imm8);
        new rip_shrd_11301(startPosition, currentPosition() - startPosition, source, imm8, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code shrd  }<i>label</i>, <i>source</i>
     * Example disassembly syntax: {@code shrd      [L1: +305419896], eax, cl}
     */
    // Template#: 689, Serial#: 11310
    public void rip_shrd_CL(final Label label, final AMD64GeneralRegister32 source) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_shrd_CL(placeHolder, source);
        new rip_shrd_CL_11310(startPosition, currentPosition() - startPosition, source, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code shrd  }<i>label</i>, <i>source</i>, <i>imm8</i>
     * Example disassembly syntax: {@code shrd      [L1: +305419896], eax, 0x12}
     */
    // Template#: 690, Serial#: 11283
    public void rip_shrd(final Label label, final AMD64GeneralRegister32 source, final byte imm8) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_shrd(placeHolder, source, imm8);
        new rip_shrd_11283(startPosition, currentPosition() - startPosition, source, imm8, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code shrd  }<i>label</i>, <i>source</i>
     * Example disassembly syntax: {@code shrd      [L1: +305419896], rax, cl}
     */
    // Template#: 691, Serial#: 11319
    public void rip_shrd_CL(final Label label, final AMD64GeneralRegister64 source) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_shrd_CL(placeHolder, source);
        new rip_shrd_CL_11319(startPosition, currentPosition() - startPosition, source, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code shrd  }<i>label</i>, <i>source</i>, <i>imm8</i>
     * Example disassembly syntax: {@code shrd      [L1: +305419896], rax, 0x12}
     */
    // Template#: 692, Serial#: 11292
    public void rip_shrd(final Label label, final AMD64GeneralRegister64 source, final byte imm8) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_shrd(placeHolder, source, imm8);
        new rip_shrd_11292(startPosition, currentPosition() - startPosition, source, imm8, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code shufpd  }<i>destination</i>, <i>label</i>, <i>imm8</i>
     * Example disassembly syntax: {@code shufpd    xmm0, [L1: +305419896], 0x12}
     */
    // Template#: 693, Serial#: 8121
    public void rip_shufpd(final AMD64XMMRegister destination, final Label label, final byte imm8) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_shufpd(destination, placeHolder, imm8);
        new rip_shufpd_8121(startPosition, currentPosition() - startPosition, destination, imm8, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code shufps  }<i>destination</i>, <i>label</i>, <i>imm8</i>
     * Example disassembly syntax: {@code shufps    xmm0, [L1: +305419896], 0x12}
     */
    // Template#: 694, Serial#: 8049
    public void rip_shufps(final AMD64XMMRegister destination, final Label label, final byte imm8) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_shufps(destination, placeHolder, imm8);
        new rip_shufps_8049(startPosition, currentPosition() - startPosition, destination, imm8, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code sidt  }<i>label</i>
     * Example disassembly syntax: {@code sidt      [L1: +305419896]}
     */
    // Template#: 695, Serial#: 5652
    public void rip_sidt(final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_sidt(placeHolder);
        new rip_sidt_5652(startPosition, currentPosition() - startPosition, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code sldt  }<i>label</i>
     * Example disassembly syntax: {@code sldt      [L1: +305419896]}
     */
    // Template#: 696, Serial#: 5486
    public void rip_sldt(final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_sldt(placeHolder);
        new rip_sldt_5486(startPosition, currentPosition() - startPosition, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code smsw  }<i>label</i>
     * Example disassembly syntax: {@code smsw      [L1: +305419896]}
     */
    // Template#: 697, Serial#: 5664
    public void rip_smsw(final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_smsw(placeHolder);
        new rip_smsw_5664(startPosition, currentPosition() - startPosition, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code sqrtpd  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code sqrtpd    xmm0, [L1: +305419896]}
     */
    // Template#: 698, Serial#: 6716
    public void rip_sqrtpd(final AMD64XMMRegister destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_sqrtpd(destination, placeHolder);
        new rip_sqrtpd_6716(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code sqrtps  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code sqrtps    xmm0, [L1: +305419896]}
     */
    // Template#: 699, Serial#: 6587
    public void rip_sqrtps(final AMD64XMMRegister destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_sqrtps(destination, placeHolder);
        new rip_sqrtps_6587(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code sqrtsd  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code sqrtsd    xmm0, [L1: +305419896]}
     */
    // Template#: 700, Serial#: 6806
    public void rip_sqrtsd(final AMD64XMMRegister destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_sqrtsd(destination, placeHolder);
        new rip_sqrtsd_6806(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code sqrtss  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code sqrtss    xmm0, [L1: +305419896]}
     */
    // Template#: 701, Serial#: 6824
    public void rip_sqrtss(final AMD64XMMRegister destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_sqrtss(destination, placeHolder);
        new rip_sqrtss_6824(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code stmxcsr  }<i>label</i>
     * Example disassembly syntax: {@code stmxcsr   [L1: +305419896]}
     */
    // Template#: 702, Serial#: 11349
    public void rip_stmxcsr(final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_stmxcsr(placeHolder);
        new rip_stmxcsr_11349(startPosition, currentPosition() - startPosition, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code str  }<i>label</i>
     * Example disassembly syntax: {@code str       [L1: +305419896]}
     */
    // Template#: 703, Serial#: 5490
    public void rip_str(final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_str(placeHolder);
        new rip_str_5490(startPosition, currentPosition() - startPosition, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code sub  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code sub       ax, [L1: +305419896]}
     */
    // Template#: 704, Serial#: 3443
    public void rip_sub(final AMD64GeneralRegister16 destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_sub(destination, placeHolder);
        new rip_sub_3443(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code sub  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code sub       eax, [L1: +305419896]}
     */
    // Template#: 705, Serial#: 3425
    public void rip_sub(final AMD64GeneralRegister32 destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_sub(destination, placeHolder);
        new rip_sub_3425(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code sub  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code sub       rax, [L1: +305419896]}
     */
    // Template#: 706, Serial#: 3434
    public void rip_sub(final AMD64GeneralRegister64 destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_sub(destination, placeHolder);
        new rip_sub_3434(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code sub  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code sub       al, [L1: +305419896]}
     */
    // Template#: 707, Serial#: 3398
    public void rip_sub(final AMD64GeneralRegister8 destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_sub(destination, placeHolder);
        new rip_sub_3398(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code subb  }<i>label</i>, <i>imm8</i>
     * Example disassembly syntax: {@code subb      [L1: +305419896], 0x12}
     */
    // Template#: 708, Serial#: 520
    public void rip_subb(final Label label, final byte imm8) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_subb(placeHolder, imm8);
        new rip_subb_520(startPosition, currentPosition() - startPosition, imm8, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code subl  }<i>label</i>, <i>imm8</i>
     * Example disassembly syntax: {@code subl      [L1: +305419896], 0x12}
     */
    // Template#: 709, Serial#: 952
    public void rip_subl(final Label label, final byte imm8) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_subl(placeHolder, imm8);
        new rip_subl_952(startPosition, currentPosition() - startPosition, imm8, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code subq  }<i>label</i>, <i>imm8</i>
     * Example disassembly syntax: {@code subq      [L1: +305419896], 0x12}
     */
    // Template#: 710, Serial#: 1024
    public void rip_subq(final Label label, final byte imm8) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_subq(placeHolder, imm8);
        new rip_subq_1024(startPosition, currentPosition() - startPosition, imm8, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code subw  }<i>label</i>, <i>imm8</i>
     * Example disassembly syntax: {@code subw      [L1: +305419896], 0x12}
     */
    // Template#: 711, Serial#: 1096
    public void rip_subw(final Label label, final byte imm8) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_subw(placeHolder, imm8);
        new rip_subw_1096(startPosition, currentPosition() - startPosition, imm8, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code sub  }<i>label</i>, <i>source</i>
     * Example disassembly syntax: {@code sub       [L1: +305419896], ax}
     */
    // Template#: 712, Serial#: 3389
    public void rip_sub(final Label label, final AMD64GeneralRegister16 source) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_sub(placeHolder, source);
        new rip_sub_3389(startPosition, currentPosition() - startPosition, source, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code sub  }<i>label</i>, <i>source</i>
     * Example disassembly syntax: {@code sub       [L1: +305419896], eax}
     */
    // Template#: 713, Serial#: 3371
    public void rip_sub(final Label label, final AMD64GeneralRegister32 source) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_sub(placeHolder, source);
        new rip_sub_3371(startPosition, currentPosition() - startPosition, source, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code sub  }<i>label</i>, <i>source</i>
     * Example disassembly syntax: {@code sub       [L1: +305419896], rax}
     */
    // Template#: 714, Serial#: 3380
    public void rip_sub(final Label label, final AMD64GeneralRegister64 source) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_sub(placeHolder, source);
        new rip_sub_3380(startPosition, currentPosition() - startPosition, source, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code sub  }<i>label</i>, <i>source</i>
     * Example disassembly syntax: {@code sub       [L1: +305419896], al}
     */
    // Template#: 715, Serial#: 3344
    public void rip_sub(final Label label, final AMD64GeneralRegister8 source) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_sub(placeHolder, source);
        new rip_sub_3344(startPosition, currentPosition() - startPosition, source, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code subl  }<i>label</i>, <i>imm32</i>
     * Example disassembly syntax: {@code subl      [L1: +305419896], 0x12345678}
     */
    // Template#: 716, Serial#: 736
    public void rip_subl(final Label label, final int imm32) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_subl(placeHolder, imm32);
        new rip_subl_736(startPosition, currentPosition() - startPosition, imm32, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code subq  }<i>label</i>, <i>imm32</i>
     * Example disassembly syntax: {@code subq      [L1: +305419896], 0x12345678}
     */
    // Template#: 717, Serial#: 808
    public void rip_subq(final Label label, final int imm32) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_subq(placeHolder, imm32);
        new rip_subq_808(startPosition, currentPosition() - startPosition, imm32, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code subw  }<i>label</i>, <i>imm16</i>
     * Example disassembly syntax: {@code subw      [L1: +305419896], 0x1234}
     */
    // Template#: 718, Serial#: 880
    public void rip_subw(final Label label, final short imm16) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_subw(placeHolder, imm16);
        new rip_subw_880(startPosition, currentPosition() - startPosition, imm16, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code subpd  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code subpd     xmm0, [L1: +305419896]}
     */
    // Template#: 719, Serial#: 10179
    public void rip_subpd(final AMD64XMMRegister destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_subpd(destination, placeHolder);
        new rip_subpd_10179(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code subps  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code subps     xmm0, [L1: +305419896]}
     */
    // Template#: 720, Serial#: 10035
    public void rip_subps(final AMD64XMMRegister destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_subps(destination, placeHolder);
        new rip_subps_10035(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code subsd  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code subsd     xmm0, [L1: +305419896]}
     */
    // Template#: 721, Serial#: 10305
    public void rip_subsd(final AMD64XMMRegister destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_subsd(destination, placeHolder);
        new rip_subsd_10305(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code subss  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code subss     xmm0, [L1: +305419896]}
     */
    // Template#: 722, Serial#: 10449
    public void rip_subss(final AMD64XMMRegister destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_subss(destination, placeHolder);
        new rip_subss_10449(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code testb  }<i>label</i>, <i>imm8</i>
     * Example disassembly syntax: {@code testb     [L1: +305419896], 0x12}
     */
    // Template#: 723, Serial#: 2684
    public void rip_testb(final Label label, final byte imm8) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_testb(placeHolder, imm8);
        new rip_testb_2684(startPosition, currentPosition() - startPosition, imm8, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code test  }<i>label</i>, <i>source</i>
     * Example disassembly syntax: {@code test      [L1: +305419896], ax}
     */
    // Template#: 724, Serial#: 1193
    public void rip_test(final Label label, final AMD64GeneralRegister16 source) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_test(placeHolder, source);
        new rip_test_1193(startPosition, currentPosition() - startPosition, source, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code test  }<i>label</i>, <i>source</i>
     * Example disassembly syntax: {@code test      [L1: +305419896], eax}
     */
    // Template#: 725, Serial#: 1175
    public void rip_test(final Label label, final AMD64GeneralRegister32 source) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_test(placeHolder, source);
        new rip_test_1175(startPosition, currentPosition() - startPosition, source, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code test  }<i>label</i>, <i>source</i>
     * Example disassembly syntax: {@code test      [L1: +305419896], rax}
     */
    // Template#: 726, Serial#: 1184
    public void rip_test(final Label label, final AMD64GeneralRegister64 source) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_test(placeHolder, source);
        new rip_test_1184(startPosition, currentPosition() - startPosition, source, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code test  }<i>label</i>, <i>source</i>
     * Example disassembly syntax: {@code test      [L1: +305419896], al}
     */
    // Template#: 727, Serial#: 1148
    public void rip_test(final Label label, final AMD64GeneralRegister8 source) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_test(placeHolder, source);
        new rip_test_1148(startPosition, currentPosition() - startPosition, source, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code testl  }<i>label</i>, <i>imm32</i>
     * Example disassembly syntax: {@code testl     [L1: +305419896], 0x12345678}
     */
    // Template#: 728, Serial#: 2900
    public void rip_testl(final Label label, final int imm32) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_testl(placeHolder, imm32);
        new rip_testl_2900(startPosition, currentPosition() - startPosition, imm32, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code testq  }<i>label</i>, <i>imm32</i>
     * Example disassembly syntax: {@code testq     [L1: +305419896], 0x12345678}
     */
    // Template#: 729, Serial#: 2972
    public void rip_testq(final Label label, final int imm32) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_testq(placeHolder, imm32);
        new rip_testq_2972(startPosition, currentPosition() - startPosition, imm32, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code testw  }<i>label</i>, <i>imm16</i>
     * Example disassembly syntax: {@code testw     [L1: +305419896], 0x1234}
     */
    // Template#: 730, Serial#: 3044
    public void rip_testw(final Label label, final short imm16) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_testw(placeHolder, imm16);
        new rip_testw_3044(startPosition, currentPosition() - startPosition, imm16, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code ucomisd  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code ucomisd   xmm0, [L1: +305419896]}
     */
    // Template#: 731, Serial#: 9603
    public void rip_ucomisd(final AMD64XMMRegister destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_ucomisd(destination, placeHolder);
        new rip_ucomisd_9603(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code ucomiss  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code ucomiss   xmm0, [L1: +305419896]}
     */
    // Template#: 732, Serial#: 9444
    public void rip_ucomiss(final AMD64XMMRegister destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_ucomiss(destination, placeHolder);
        new rip_ucomiss_9444(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code unpckhpd  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code unpckhpd  xmm0, [L1: +305419896]}
     */
    // Template#: 733, Serial#: 6116
    public void rip_unpckhpd(final AMD64XMMRegister destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_unpckhpd(destination, placeHolder);
        new rip_unpckhpd_6116(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code unpckhps  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code unpckhps  xmm0, [L1: +305419896]}
     */
    // Template#: 734, Serial#: 5969
    public void rip_unpckhps(final AMD64XMMRegister destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_unpckhps(destination, placeHolder);
        new rip_unpckhps_5969(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code unpcklpd  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code unpcklpd  xmm0, [L1: +305419896]}
     */
    // Template#: 735, Serial#: 6098
    public void rip_unpcklpd(final AMD64XMMRegister destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_unpcklpd(destination, placeHolder);
        new rip_unpcklpd_6098(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code unpcklps  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code unpcklps  xmm0, [L1: +305419896]}
     */
    // Template#: 736, Serial#: 5951
    public void rip_unpcklps(final AMD64XMMRegister destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_unpcklps(destination, placeHolder);
        new rip_unpcklps_5951(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code verr  }<i>label</i>
     * Example disassembly syntax: {@code verr      [L1: +305419896]}
     */
    // Template#: 737, Serial#: 5502
    public void rip_verr(final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_verr(placeHolder);
        new rip_verr_5502(startPosition, currentPosition() - startPosition, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code verw  }<i>label</i>
     * Example disassembly syntax: {@code verw      [L1: +305419896]}
     */
    // Template#: 738, Serial#: 5506
    public void rip_verw(final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_verw(placeHolder);
        new rip_verw_5506(startPosition, currentPosition() - startPosition, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code xadd  }<i>label</i>, <i>source</i>
     * Example disassembly syntax: {@code xadd      [L1: +305419896], ax}
     */
    // Template#: 739, Serial#: 7994
    public void rip_xadd(final Label label, final AMD64GeneralRegister16 source) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_xadd(placeHolder, source);
        new rip_xadd_7994(startPosition, currentPosition() - startPosition, source, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code xadd  }<i>label</i>, <i>source</i>
     * Example disassembly syntax: {@code xadd      [L1: +305419896], eax}
     */
    // Template#: 740, Serial#: 7976
    public void rip_xadd(final Label label, final AMD64GeneralRegister32 source) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_xadd(placeHolder, source);
        new rip_xadd_7976(startPosition, currentPosition() - startPosition, source, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code xadd  }<i>label</i>, <i>source</i>
     * Example disassembly syntax: {@code xadd      [L1: +305419896], rax}
     */
    // Template#: 741, Serial#: 7985
    public void rip_xadd(final Label label, final AMD64GeneralRegister64 source) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_xadd(placeHolder, source);
        new rip_xadd_7985(startPosition, currentPosition() - startPosition, source, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code xadd  }<i>label</i>, <i>source</i>
     * Example disassembly syntax: {@code xadd      [L1: +305419896], al}
     */
    // Template#: 742, Serial#: 7949
    public void rip_xadd(final Label label, final AMD64GeneralRegister8 source) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_xadd(placeHolder, source);
        new rip_xadd_7949(startPosition, currentPosition() - startPosition, source, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code xchg  }<i>label</i>, <i>source</i>
     * Example disassembly syntax: {@code xchg      [L1: +305419896], ax}
     */
    // Template#: 743, Serial#: 1247
    public void rip_xchg(final Label label, final AMD64GeneralRegister16 source) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_xchg(placeHolder, source);
        new rip_xchg_1247(startPosition, currentPosition() - startPosition, source, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code xchg  }<i>label</i>, <i>source</i>
     * Example disassembly syntax: {@code xchg      [L1: +305419896], eax}
     */
    // Template#: 744, Serial#: 1229
    public void rip_xchg(final Label label, final AMD64GeneralRegister32 source) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_xchg(placeHolder, source);
        new rip_xchg_1229(startPosition, currentPosition() - startPosition, source, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code xchg  }<i>label</i>, <i>source</i>
     * Example disassembly syntax: {@code xchg      [L1: +305419896], rax}
     */
    // Template#: 745, Serial#: 1238
    public void rip_xchg(final Label label, final AMD64GeneralRegister64 source) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_xchg(placeHolder, source);
        new rip_xchg_1238(startPosition, currentPosition() - startPosition, source, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code xchg  }<i>label</i>, <i>source</i>
     * Example disassembly syntax: {@code xchg      [L1: +305419896], al}
     */
    // Template#: 746, Serial#: 1202
    public void rip_xchg(final Label label, final AMD64GeneralRegister8 source) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_xchg(placeHolder, source);
        new rip_xchg_1202(startPosition, currentPosition() - startPosition, source, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code xor  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code xor       ax, [L1: +305419896]}
     */
    // Template#: 747, Serial#: 445
    public void rip_xor(final AMD64GeneralRegister16 destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_xor(destination, placeHolder);
        new rip_xor_445(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code xor  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code xor       eax, [L1: +305419896]}
     */
    // Template#: 748, Serial#: 427
    public void rip_xor(final AMD64GeneralRegister32 destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_xor(destination, placeHolder);
        new rip_xor_427(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code xor  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code xor       rax, [L1: +305419896]}
     */
    // Template#: 749, Serial#: 436
    public void rip_xor(final AMD64GeneralRegister64 destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_xor(destination, placeHolder);
        new rip_xor_436(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code xor  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code xor       al, [L1: +305419896]}
     */
    // Template#: 750, Serial#: 400
    public void rip_xor(final AMD64GeneralRegister8 destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_xor(destination, placeHolder);
        new rip_xor_400(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code xorb  }<i>label</i>, <i>imm8</i>
     * Example disassembly syntax: {@code xorb      [L1: +305419896], 0x12}
     */
    // Template#: 751, Serial#: 524
    public void rip_xorb(final Label label, final byte imm8) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_xorb(placeHolder, imm8);
        new rip_xorb_524(startPosition, currentPosition() - startPosition, imm8, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code xorl  }<i>label</i>, <i>imm8</i>
     * Example disassembly syntax: {@code xorl      [L1: +305419896], 0x12}
     */
    // Template#: 752, Serial#: 956
    public void rip_xorl(final Label label, final byte imm8) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_xorl(placeHolder, imm8);
        new rip_xorl_956(startPosition, currentPosition() - startPosition, imm8, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code xorq  }<i>label</i>, <i>imm8</i>
     * Example disassembly syntax: {@code xorq      [L1: +305419896], 0x12}
     */
    // Template#: 753, Serial#: 1028
    public void rip_xorq(final Label label, final byte imm8) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_xorq(placeHolder, imm8);
        new rip_xorq_1028(startPosition, currentPosition() - startPosition, imm8, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code xorw  }<i>label</i>, <i>imm8</i>
     * Example disassembly syntax: {@code xorw      [L1: +305419896], 0x12}
     */
    // Template#: 754, Serial#: 1100
    public void rip_xorw(final Label label, final byte imm8) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_xorw(placeHolder, imm8);
        new rip_xorw_1100(startPosition, currentPosition() - startPosition, imm8, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code xor  }<i>label</i>, <i>source</i>
     * Example disassembly syntax: {@code xor       [L1: +305419896], ax}
     */
    // Template#: 755, Serial#: 391
    public void rip_xor(final Label label, final AMD64GeneralRegister16 source) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_xor(placeHolder, source);
        new rip_xor_391(startPosition, currentPosition() - startPosition, source, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code xor  }<i>label</i>, <i>source</i>
     * Example disassembly syntax: {@code xor       [L1: +305419896], eax}
     */
    // Template#: 756, Serial#: 373
    public void rip_xor(final Label label, final AMD64GeneralRegister32 source) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_xor(placeHolder, source);
        new rip_xor_373(startPosition, currentPosition() - startPosition, source, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code xor  }<i>label</i>, <i>source</i>
     * Example disassembly syntax: {@code xor       [L1: +305419896], rax}
     */
    // Template#: 757, Serial#: 382
    public void rip_xor(final Label label, final AMD64GeneralRegister64 source) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_xor(placeHolder, source);
        new rip_xor_382(startPosition, currentPosition() - startPosition, source, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code xor  }<i>label</i>, <i>source</i>
     * Example disassembly syntax: {@code xor       [L1: +305419896], al}
     */
    // Template#: 758, Serial#: 346
    public void rip_xor(final Label label, final AMD64GeneralRegister8 source) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_xor(placeHolder, source);
        new rip_xor_346(startPosition, currentPosition() - startPosition, source, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code xorl  }<i>label</i>, <i>imm32</i>
     * Example disassembly syntax: {@code xorl      [L1: +305419896], 0x12345678}
     */
    // Template#: 759, Serial#: 740
    public void rip_xorl(final Label label, final int imm32) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_xorl(placeHolder, imm32);
        new rip_xorl_740(startPosition, currentPosition() - startPosition, imm32, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code xorq  }<i>label</i>, <i>imm32</i>
     * Example disassembly syntax: {@code xorq      [L1: +305419896], 0x12345678}
     */
    // Template#: 760, Serial#: 812
    public void rip_xorq(final Label label, final int imm32) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_xorq(placeHolder, imm32);
        new rip_xorq_812(startPosition, currentPosition() - startPosition, imm32, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code xorw  }<i>label</i>, <i>imm16</i>
     * Example disassembly syntax: {@code xorw      [L1: +305419896], 0x1234}
     */
    // Template#: 761, Serial#: 884
    public void rip_xorw(final Label label, final short imm16) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_xorw(placeHolder, imm16);
        new rip_xorw_884(startPosition, currentPosition() - startPosition, imm16, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code xorpd  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code xorpd     xmm0, [L1: +305419896]}
     */
    // Template#: 762, Serial#: 6788
    public void rip_xorpd(final AMD64XMMRegister destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_xorpd(destination, placeHolder);
        new rip_xorpd_6788(startPosition, currentPosition() - startPosition, destination, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code xorps  }<i>destination</i>, <i>label</i>
     * Example disassembly syntax: {@code xorps     xmm0, [L1: +305419896]}
     */
    // Template#: 763, Serial#: 6695
    public void rip_xorps(final AMD64XMMRegister destination, final Label label) {
        final int startPosition = currentPosition();
        final int placeHolder = 0;
        rip_xorps(destination, placeHolder);
        new rip_xorps_6695(startPosition, currentPosition() - startPosition, destination, label);
    }

    class rip_adc_217 extends InstructionWithOffset {
        private final AMD64GeneralRegister16 destination;
        rip_adc_217(int startPosition, int endPosition, AMD64GeneralRegister16 destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_adc(destination, offsetAsInt());
        }
    }

    class rip_adc_199 extends InstructionWithOffset {
        private final AMD64GeneralRegister32 destination;
        rip_adc_199(int startPosition, int endPosition, AMD64GeneralRegister32 destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_adc(destination, offsetAsInt());
        }
    }

    class rip_adc_208 extends InstructionWithOffset {
        private final AMD64GeneralRegister64 destination;
        rip_adc_208(int startPosition, int endPosition, AMD64GeneralRegister64 destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_adc(destination, offsetAsInt());
        }
    }

    class rip_adc_172 extends InstructionWithOffset {
        private final AMD64GeneralRegister8 destination;
        rip_adc_172(int startPosition, int endPosition, AMD64GeneralRegister8 destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_adc(destination, offsetAsInt());
        }
    }

    class rip_adcb_508 extends InstructionWithOffset {
        private final byte imm8;
        rip_adcb_508(int startPosition, int endPosition, byte imm8, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.imm8 = imm8;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_adcb(offsetAsInt(), imm8);
        }
    }

    class rip_adcl_940 extends InstructionWithOffset {
        private final byte imm8;
        rip_adcl_940(int startPosition, int endPosition, byte imm8, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.imm8 = imm8;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_adcl(offsetAsInt(), imm8);
        }
    }

    class rip_adcq_1012 extends InstructionWithOffset {
        private final byte imm8;
        rip_adcq_1012(int startPosition, int endPosition, byte imm8, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.imm8 = imm8;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_adcq(offsetAsInt(), imm8);
        }
    }

    class rip_adcw_1084 extends InstructionWithOffset {
        private final byte imm8;
        rip_adcw_1084(int startPosition, int endPosition, byte imm8, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.imm8 = imm8;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_adcw(offsetAsInt(), imm8);
        }
    }

    class rip_adc_163 extends InstructionWithOffset {
        private final AMD64GeneralRegister16 source;
        rip_adc_163(int startPosition, int endPosition, AMD64GeneralRegister16 source, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.source = source;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_adc(offsetAsInt(), source);
        }
    }

    class rip_adc_145 extends InstructionWithOffset {
        private final AMD64GeneralRegister32 source;
        rip_adc_145(int startPosition, int endPosition, AMD64GeneralRegister32 source, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.source = source;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_adc(offsetAsInt(), source);
        }
    }

    class rip_adc_154 extends InstructionWithOffset {
        private final AMD64GeneralRegister64 source;
        rip_adc_154(int startPosition, int endPosition, AMD64GeneralRegister64 source, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.source = source;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_adc(offsetAsInt(), source);
        }
    }

    class rip_adc_118 extends InstructionWithOffset {
        private final AMD64GeneralRegister8 source;
        rip_adc_118(int startPosition, int endPosition, AMD64GeneralRegister8 source, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.source = source;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_adc(offsetAsInt(), source);
        }
    }

    class rip_adcl_724 extends InstructionWithOffset {
        private final int imm32;
        rip_adcl_724(int startPosition, int endPosition, int imm32, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.imm32 = imm32;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_adcl(offsetAsInt(), imm32);
        }
    }

    class rip_adcq_796 extends InstructionWithOffset {
        private final int imm32;
        rip_adcq_796(int startPosition, int endPosition, int imm32, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.imm32 = imm32;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_adcq(offsetAsInt(), imm32);
        }
    }

    class rip_adcw_868 extends InstructionWithOffset {
        private final short imm16;
        rip_adcw_868(int startPosition, int endPosition, short imm16, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.imm16 = imm16;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_adcw(offsetAsInt(), imm16);
        }
    }

    class rip_add_103 extends InstructionWithOffset {
        private final AMD64GeneralRegister16 destination;
        rip_add_103(int startPosition, int endPosition, AMD64GeneralRegister16 destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_add(destination, offsetAsInt());
        }
    }

    class rip_add_85 extends InstructionWithOffset {
        private final AMD64GeneralRegister32 destination;
        rip_add_85(int startPosition, int endPosition, AMD64GeneralRegister32 destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_add(destination, offsetAsInt());
        }
    }

    class rip_add_94 extends InstructionWithOffset {
        private final AMD64GeneralRegister64 destination;
        rip_add_94(int startPosition, int endPosition, AMD64GeneralRegister64 destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_add(destination, offsetAsInt());
        }
    }

    class rip_add_58 extends InstructionWithOffset {
        private final AMD64GeneralRegister8 destination;
        rip_add_58(int startPosition, int endPosition, AMD64GeneralRegister8 destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_add(destination, offsetAsInt());
        }
    }

    class rip_addb_500 extends InstructionWithOffset {
        private final byte imm8;
        rip_addb_500(int startPosition, int endPosition, byte imm8, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.imm8 = imm8;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_addb(offsetAsInt(), imm8);
        }
    }

    class rip_addl_932 extends InstructionWithOffset {
        private final byte imm8;
        rip_addl_932(int startPosition, int endPosition, byte imm8, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.imm8 = imm8;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_addl(offsetAsInt(), imm8);
        }
    }

    class rip_addq_1004 extends InstructionWithOffset {
        private final byte imm8;
        rip_addq_1004(int startPosition, int endPosition, byte imm8, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.imm8 = imm8;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_addq(offsetAsInt(), imm8);
        }
    }

    class rip_addw_1076 extends InstructionWithOffset {
        private final byte imm8;
        rip_addw_1076(int startPosition, int endPosition, byte imm8, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.imm8 = imm8;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_addw(offsetAsInt(), imm8);
        }
    }

    class rip_add_49 extends InstructionWithOffset {
        private final AMD64GeneralRegister16 source;
        rip_add_49(int startPosition, int endPosition, AMD64GeneralRegister16 source, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.source = source;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_add(offsetAsInt(), source);
        }
    }

    class rip_add_31 extends InstructionWithOffset {
        private final AMD64GeneralRegister32 source;
        rip_add_31(int startPosition, int endPosition, AMD64GeneralRegister32 source, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.source = source;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_add(offsetAsInt(), source);
        }
    }

    class rip_add_40 extends InstructionWithOffset {
        private final AMD64GeneralRegister64 source;
        rip_add_40(int startPosition, int endPosition, AMD64GeneralRegister64 source, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.source = source;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_add(offsetAsInt(), source);
        }
    }

    class rip_add_4 extends InstructionWithOffset {
        private final AMD64GeneralRegister8 source;
        rip_add_4(int startPosition, int endPosition, AMD64GeneralRegister8 source, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.source = source;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_add(offsetAsInt(), source);
        }
    }

    class rip_addl_716 extends InstructionWithOffset {
        private final int imm32;
        rip_addl_716(int startPosition, int endPosition, int imm32, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.imm32 = imm32;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_addl(offsetAsInt(), imm32);
        }
    }

    class rip_addq_788 extends InstructionWithOffset {
        private final int imm32;
        rip_addq_788(int startPosition, int endPosition, int imm32, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.imm32 = imm32;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_addq(offsetAsInt(), imm32);
        }
    }

    class rip_addw_860 extends InstructionWithOffset {
        private final short imm16;
        rip_addw_860(int startPosition, int endPosition, short imm16, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.imm16 = imm16;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_addw(offsetAsInt(), imm16);
        }
    }

    class rip_addpd_10107 extends InstructionWithOffset {
        private final AMD64XMMRegister destination;
        rip_addpd_10107(int startPosition, int endPosition, AMD64XMMRegister destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_addpd(destination, offsetAsInt());
        }
    }

    class rip_addps_9963 extends InstructionWithOffset {
        private final AMD64XMMRegister destination;
        rip_addps_9963(int startPosition, int endPosition, AMD64XMMRegister destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_addps(destination, offsetAsInt());
        }
    }

    class rip_addsd_10251 extends InstructionWithOffset {
        private final AMD64XMMRegister destination;
        rip_addsd_10251(int startPosition, int endPosition, AMD64XMMRegister destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_addsd(destination, offsetAsInt());
        }
    }

    class rip_addss_10377 extends InstructionWithOffset {
        private final AMD64XMMRegister destination;
        rip_addss_10377(int startPosition, int endPosition, AMD64XMMRegister destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_addss(destination, offsetAsInt());
        }
    }

    class rip_addsubpd_8313 extends InstructionWithOffset {
        private final AMD64XMMRegister destination;
        rip_addsubpd_8313(int startPosition, int endPosition, AMD64XMMRegister destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_addsubpd(destination, offsetAsInt());
        }
    }

    class rip_and_331 extends InstructionWithOffset {
        private final AMD64GeneralRegister16 destination;
        rip_and_331(int startPosition, int endPosition, AMD64GeneralRegister16 destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_and(destination, offsetAsInt());
        }
    }

    class rip_and_313 extends InstructionWithOffset {
        private final AMD64GeneralRegister32 destination;
        rip_and_313(int startPosition, int endPosition, AMD64GeneralRegister32 destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_and(destination, offsetAsInt());
        }
    }

    class rip_and_322 extends InstructionWithOffset {
        private final AMD64GeneralRegister64 destination;
        rip_and_322(int startPosition, int endPosition, AMD64GeneralRegister64 destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_and(destination, offsetAsInt());
        }
    }

    class rip_and_286 extends InstructionWithOffset {
        private final AMD64GeneralRegister8 destination;
        rip_and_286(int startPosition, int endPosition, AMD64GeneralRegister8 destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_and(destination, offsetAsInt());
        }
    }

    class rip_andb_516 extends InstructionWithOffset {
        private final byte imm8;
        rip_andb_516(int startPosition, int endPosition, byte imm8, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.imm8 = imm8;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_andb(offsetAsInt(), imm8);
        }
    }

    class rip_andl_948 extends InstructionWithOffset {
        private final byte imm8;
        rip_andl_948(int startPosition, int endPosition, byte imm8, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.imm8 = imm8;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_andl(offsetAsInt(), imm8);
        }
    }

    class rip_andq_1020 extends InstructionWithOffset {
        private final byte imm8;
        rip_andq_1020(int startPosition, int endPosition, byte imm8, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.imm8 = imm8;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_andq(offsetAsInt(), imm8);
        }
    }

    class rip_andw_1092 extends InstructionWithOffset {
        private final byte imm8;
        rip_andw_1092(int startPosition, int endPosition, byte imm8, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.imm8 = imm8;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_andw(offsetAsInt(), imm8);
        }
    }

    class rip_and_277 extends InstructionWithOffset {
        private final AMD64GeneralRegister16 source;
        rip_and_277(int startPosition, int endPosition, AMD64GeneralRegister16 source, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.source = source;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_and(offsetAsInt(), source);
        }
    }

    class rip_and_259 extends InstructionWithOffset {
        private final AMD64GeneralRegister32 source;
        rip_and_259(int startPosition, int endPosition, AMD64GeneralRegister32 source, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.source = source;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_and(offsetAsInt(), source);
        }
    }

    class rip_and_268 extends InstructionWithOffset {
        private final AMD64GeneralRegister64 source;
        rip_and_268(int startPosition, int endPosition, AMD64GeneralRegister64 source, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.source = source;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_and(offsetAsInt(), source);
        }
    }

    class rip_and_232 extends InstructionWithOffset {
        private final AMD64GeneralRegister8 source;
        rip_and_232(int startPosition, int endPosition, AMD64GeneralRegister8 source, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.source = source;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_and(offsetAsInt(), source);
        }
    }

    class rip_andl_732 extends InstructionWithOffset {
        private final int imm32;
        rip_andl_732(int startPosition, int endPosition, int imm32, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.imm32 = imm32;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_andl(offsetAsInt(), imm32);
        }
    }

    class rip_andq_804 extends InstructionWithOffset {
        private final int imm32;
        rip_andq_804(int startPosition, int endPosition, int imm32, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.imm32 = imm32;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_andq(offsetAsInt(), imm32);
        }
    }

    class rip_andw_876 extends InstructionWithOffset {
        private final short imm16;
        rip_andw_876(int startPosition, int endPosition, short imm16, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.imm16 = imm16;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_andw(offsetAsInt(), imm16);
        }
    }

    class rip_andnpd_6752 extends InstructionWithOffset {
        private final AMD64XMMRegister destination;
        rip_andnpd_6752(int startPosition, int endPosition, AMD64XMMRegister destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_andnpd(destination, offsetAsInt());
        }
    }

    class rip_andnps_6659 extends InstructionWithOffset {
        private final AMD64XMMRegister destination;
        rip_andnps_6659(int startPosition, int endPosition, AMD64XMMRegister destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_andnps(destination, offsetAsInt());
        }
    }

    class rip_andpd_6734 extends InstructionWithOffset {
        private final AMD64XMMRegister destination;
        rip_andpd_6734(int startPosition, int endPosition, AMD64XMMRegister destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_andpd(destination, offsetAsInt());
        }
    }

    class rip_andps_6641 extends InstructionWithOffset {
        private final AMD64XMMRegister destination;
        rip_andps_6641(int startPosition, int endPosition, AMD64XMMRegister destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_andps(destination, offsetAsInt());
        }
    }

    class rip_bsf_11646 extends InstructionWithOffset {
        private final AMD64GeneralRegister16 destination;
        rip_bsf_11646(int startPosition, int endPosition, AMD64GeneralRegister16 destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_bsf(destination, offsetAsInt());
        }
    }

    class rip_bsf_11628 extends InstructionWithOffset {
        private final AMD64GeneralRegister32 destination;
        rip_bsf_11628(int startPosition, int endPosition, AMD64GeneralRegister32 destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_bsf(destination, offsetAsInt());
        }
    }

    class rip_bsf_11637 extends InstructionWithOffset {
        private final AMD64GeneralRegister64 destination;
        rip_bsf_11637(int startPosition, int endPosition, AMD64GeneralRegister64 destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_bsf(destination, offsetAsInt());
        }
    }

    class rip_bsr_11673 extends InstructionWithOffset {
        private final AMD64GeneralRegister16 destination;
        rip_bsr_11673(int startPosition, int endPosition, AMD64GeneralRegister16 destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_bsr(destination, offsetAsInt());
        }
    }

    class rip_bsr_11655 extends InstructionWithOffset {
        private final AMD64GeneralRegister32 destination;
        rip_bsr_11655(int startPosition, int endPosition, AMD64GeneralRegister32 destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_bsr(destination, offsetAsInt());
        }
    }

    class rip_bsr_11664 extends InstructionWithOffset {
        private final AMD64GeneralRegister64 destination;
        rip_bsr_11664(int startPosition, int endPosition, AMD64GeneralRegister64 destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_bsr(destination, offsetAsInt());
        }
    }

    class rip_bt_11493 extends InstructionWithOffset {
        private final byte imm8;
        rip_bt_11493(int startPosition, int endPosition, byte imm8, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.imm8 = imm8;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_bt(offsetAsInt(), imm8);
        }
    }

    class rip_bt_7760 extends InstructionWithOffset {
        private final AMD64GeneralRegister16 source;
        rip_bt_7760(int startPosition, int endPosition, AMD64GeneralRegister16 source, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.source = source;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_bt(offsetAsInt(), source);
        }
    }

    class rip_bt_7742 extends InstructionWithOffset {
        private final AMD64GeneralRegister32 source;
        rip_bt_7742(int startPosition, int endPosition, AMD64GeneralRegister32 source, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.source = source;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_bt(offsetAsInt(), source);
        }
    }

    class rip_bt_7751 extends InstructionWithOffset {
        private final AMD64GeneralRegister64 source;
        rip_bt_7751(int startPosition, int endPosition, AMD64GeneralRegister64 source, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.source = source;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_bt(offsetAsInt(), source);
        }
    }

    class rip_btc_11505 extends InstructionWithOffset {
        private final byte imm8;
        rip_btc_11505(int startPosition, int endPosition, byte imm8, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.imm8 = imm8;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_btc(offsetAsInt(), imm8);
        }
    }

    class rip_btc_11619 extends InstructionWithOffset {
        private final AMD64GeneralRegister16 source;
        rip_btc_11619(int startPosition, int endPosition, AMD64GeneralRegister16 source, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.source = source;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_btc(offsetAsInt(), source);
        }
    }

    class rip_btc_11601 extends InstructionWithOffset {
        private final AMD64GeneralRegister32 source;
        rip_btc_11601(int startPosition, int endPosition, AMD64GeneralRegister32 source, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.source = source;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_btc(offsetAsInt(), source);
        }
    }

    class rip_btc_11610 extends InstructionWithOffset {
        private final AMD64GeneralRegister64 source;
        rip_btc_11610(int startPosition, int endPosition, AMD64GeneralRegister64 source, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.source = source;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_btc(offsetAsInt(), source);
        }
    }

    class rip_btr_11501 extends InstructionWithOffset {
        private final byte imm8;
        rip_btr_11501(int startPosition, int endPosition, byte imm8, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.imm8 = imm8;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_btr(offsetAsInt(), imm8);
        }
    }

    class rip_btr_7895 extends InstructionWithOffset {
        private final AMD64GeneralRegister16 source;
        rip_btr_7895(int startPosition, int endPosition, AMD64GeneralRegister16 source, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.source = source;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_btr(offsetAsInt(), source);
        }
    }

    class rip_btr_7877 extends InstructionWithOffset {
        private final AMD64GeneralRegister32 source;
        rip_btr_7877(int startPosition, int endPosition, AMD64GeneralRegister32 source, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.source = source;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_btr(offsetAsInt(), source);
        }
    }

    class rip_btr_7886 extends InstructionWithOffset {
        private final AMD64GeneralRegister64 source;
        rip_btr_7886(int startPosition, int endPosition, AMD64GeneralRegister64 source, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.source = source;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_btr(offsetAsInt(), source);
        }
    }

    class rip_bts_11497 extends InstructionWithOffset {
        private final byte imm8;
        rip_bts_11497(int startPosition, int endPosition, byte imm8, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.imm8 = imm8;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_bts(offsetAsInt(), imm8);
        }
    }

    class rip_bts_11274 extends InstructionWithOffset {
        private final AMD64GeneralRegister16 source;
        rip_bts_11274(int startPosition, int endPosition, AMD64GeneralRegister16 source, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.source = source;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_bts(offsetAsInt(), source);
        }
    }

    class rip_bts_11256 extends InstructionWithOffset {
        private final AMD64GeneralRegister32 source;
        rip_bts_11256(int startPosition, int endPosition, AMD64GeneralRegister32 source, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.source = source;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_bts(offsetAsInt(), source);
        }
    }

    class rip_bts_11265 extends InstructionWithOffset {
        private final AMD64GeneralRegister64 source;
        rip_bts_11265(int startPosition, int endPosition, AMD64GeneralRegister64 source, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.source = source;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_bts(offsetAsInt(), source);
        }
    }

    class call_5288 extends InstructionWithOffset {
        call_5288(int startPosition, int endPosition, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
        }
        @Override
        protected void assemble() throws AssemblyException {
            call(offsetAsInt());
        }
    }

    class rip_call_5432 extends InstructionWithOffset {
        rip_call_5432(int startPosition, int endPosition, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_call(offsetAsInt());
        }
    }

    class rip_clflush_11353 extends InstructionWithOffset {
        rip_clflush_11353(int startPosition, int endPosition, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_clflush(offsetAsInt());
        }
    }

    class rip_cmova_6575 extends InstructionWithOffset {
        private final AMD64GeneralRegister16 destination;
        rip_cmova_6575(int startPosition, int endPosition, AMD64GeneralRegister16 destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_cmova(destination, offsetAsInt());
        }
    }

    class rip_cmova_6557 extends InstructionWithOffset {
        private final AMD64GeneralRegister32 destination;
        rip_cmova_6557(int startPosition, int endPosition, AMD64GeneralRegister32 destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_cmova(destination, offsetAsInt());
        }
    }

    class rip_cmova_6566 extends InstructionWithOffset {
        private final AMD64GeneralRegister64 destination;
        rip_cmova_6566(int startPosition, int endPosition, AMD64GeneralRegister64 destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_cmova(destination, offsetAsInt());
        }
    }

    class rip_cmovae_6467 extends InstructionWithOffset {
        private final AMD64GeneralRegister16 destination;
        rip_cmovae_6467(int startPosition, int endPosition, AMD64GeneralRegister16 destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_cmovae(destination, offsetAsInt());
        }
    }

    class rip_cmovae_6449 extends InstructionWithOffset {
        private final AMD64GeneralRegister32 destination;
        rip_cmovae_6449(int startPosition, int endPosition, AMD64GeneralRegister32 destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_cmovae(destination, offsetAsInt());
        }
    }

    class rip_cmovae_6458 extends InstructionWithOffset {
        private final AMD64GeneralRegister64 destination;
        rip_cmovae_6458(int startPosition, int endPosition, AMD64GeneralRegister64 destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_cmovae(destination, offsetAsInt());
        }
    }

    class rip_cmovb_6440 extends InstructionWithOffset {
        private final AMD64GeneralRegister16 destination;
        rip_cmovb_6440(int startPosition, int endPosition, AMD64GeneralRegister16 destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_cmovb(destination, offsetAsInt());
        }
    }

    class rip_cmovb_6422 extends InstructionWithOffset {
        private final AMD64GeneralRegister32 destination;
        rip_cmovb_6422(int startPosition, int endPosition, AMD64GeneralRegister32 destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_cmovb(destination, offsetAsInt());
        }
    }

    class rip_cmovb_6431 extends InstructionWithOffset {
        private final AMD64GeneralRegister64 destination;
        rip_cmovb_6431(int startPosition, int endPosition, AMD64GeneralRegister64 destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_cmovb(destination, offsetAsInt());
        }
    }

    class rip_cmovbe_6548 extends InstructionWithOffset {
        private final AMD64GeneralRegister16 destination;
        rip_cmovbe_6548(int startPosition, int endPosition, AMD64GeneralRegister16 destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_cmovbe(destination, offsetAsInt());
        }
    }

    class rip_cmovbe_6530 extends InstructionWithOffset {
        private final AMD64GeneralRegister32 destination;
        rip_cmovbe_6530(int startPosition, int endPosition, AMD64GeneralRegister32 destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_cmovbe(destination, offsetAsInt());
        }
    }

    class rip_cmovbe_6539 extends InstructionWithOffset {
        private final AMD64GeneralRegister64 destination;
        rip_cmovbe_6539(int startPosition, int endPosition, AMD64GeneralRegister64 destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_cmovbe(destination, offsetAsInt());
        }
    }

    class rip_cmove_6494 extends InstructionWithOffset {
        private final AMD64GeneralRegister16 destination;
        rip_cmove_6494(int startPosition, int endPosition, AMD64GeneralRegister16 destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_cmove(destination, offsetAsInt());
        }
    }

    class rip_cmove_6476 extends InstructionWithOffset {
        private final AMD64GeneralRegister32 destination;
        rip_cmove_6476(int startPosition, int endPosition, AMD64GeneralRegister32 destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_cmove(destination, offsetAsInt());
        }
    }

    class rip_cmove_6485 extends InstructionWithOffset {
        private final AMD64GeneralRegister64 destination;
        rip_cmove_6485(int startPosition, int endPosition, AMD64GeneralRegister64 destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_cmove(destination, offsetAsInt());
        }
    }

    class rip_cmovg_9954 extends InstructionWithOffset {
        private final AMD64GeneralRegister16 destination;
        rip_cmovg_9954(int startPosition, int endPosition, AMD64GeneralRegister16 destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_cmovg(destination, offsetAsInt());
        }
    }

    class rip_cmovg_9936 extends InstructionWithOffset {
        private final AMD64GeneralRegister32 destination;
        rip_cmovg_9936(int startPosition, int endPosition, AMD64GeneralRegister32 destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_cmovg(destination, offsetAsInt());
        }
    }

    class rip_cmovg_9945 extends InstructionWithOffset {
        private final AMD64GeneralRegister64 destination;
        rip_cmovg_9945(int startPosition, int endPosition, AMD64GeneralRegister64 destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_cmovg(destination, offsetAsInt());
        }
    }

    class rip_cmovge_9900 extends InstructionWithOffset {
        private final AMD64GeneralRegister16 destination;
        rip_cmovge_9900(int startPosition, int endPosition, AMD64GeneralRegister16 destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_cmovge(destination, offsetAsInt());
        }
    }

    class rip_cmovge_9882 extends InstructionWithOffset {
        private final AMD64GeneralRegister32 destination;
        rip_cmovge_9882(int startPosition, int endPosition, AMD64GeneralRegister32 destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_cmovge(destination, offsetAsInt());
        }
    }

    class rip_cmovge_9891 extends InstructionWithOffset {
        private final AMD64GeneralRegister64 destination;
        rip_cmovge_9891(int startPosition, int endPosition, AMD64GeneralRegister64 destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_cmovge(destination, offsetAsInt());
        }
    }

    class rip_cmovl_9873 extends InstructionWithOffset {
        private final AMD64GeneralRegister16 destination;
        rip_cmovl_9873(int startPosition, int endPosition, AMD64GeneralRegister16 destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_cmovl(destination, offsetAsInt());
        }
    }

    class rip_cmovl_9855 extends InstructionWithOffset {
        private final AMD64GeneralRegister32 destination;
        rip_cmovl_9855(int startPosition, int endPosition, AMD64GeneralRegister32 destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_cmovl(destination, offsetAsInt());
        }
    }

    class rip_cmovl_9864 extends InstructionWithOffset {
        private final AMD64GeneralRegister64 destination;
        rip_cmovl_9864(int startPosition, int endPosition, AMD64GeneralRegister64 destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_cmovl(destination, offsetAsInt());
        }
    }

    class rip_cmovle_9927 extends InstructionWithOffset {
        private final AMD64GeneralRegister16 destination;
        rip_cmovle_9927(int startPosition, int endPosition, AMD64GeneralRegister16 destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_cmovle(destination, offsetAsInt());
        }
    }

    class rip_cmovle_9909 extends InstructionWithOffset {
        private final AMD64GeneralRegister32 destination;
        rip_cmovle_9909(int startPosition, int endPosition, AMD64GeneralRegister32 destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_cmovle(destination, offsetAsInt());
        }
    }

    class rip_cmovle_9918 extends InstructionWithOffset {
        private final AMD64GeneralRegister64 destination;
        rip_cmovle_9918(int startPosition, int endPosition, AMD64GeneralRegister64 destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_cmovle(destination, offsetAsInt());
        }
    }

    class rip_cmovne_6521 extends InstructionWithOffset {
        private final AMD64GeneralRegister16 destination;
        rip_cmovne_6521(int startPosition, int endPosition, AMD64GeneralRegister16 destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_cmovne(destination, offsetAsInt());
        }
    }

    class rip_cmovne_6503 extends InstructionWithOffset {
        private final AMD64GeneralRegister32 destination;
        rip_cmovne_6503(int startPosition, int endPosition, AMD64GeneralRegister32 destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_cmovne(destination, offsetAsInt());
        }
    }

    class rip_cmovne_6512 extends InstructionWithOffset {
        private final AMD64GeneralRegister64 destination;
        rip_cmovne_6512(int startPosition, int endPosition, AMD64GeneralRegister64 destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_cmovne(destination, offsetAsInt());
        }
    }

    class rip_cmovno_6413 extends InstructionWithOffset {
        private final AMD64GeneralRegister16 destination;
        rip_cmovno_6413(int startPosition, int endPosition, AMD64GeneralRegister16 destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_cmovno(destination, offsetAsInt());
        }
    }

    class rip_cmovno_6395 extends InstructionWithOffset {
        private final AMD64GeneralRegister32 destination;
        rip_cmovno_6395(int startPosition, int endPosition, AMD64GeneralRegister32 destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_cmovno(destination, offsetAsInt());
        }
    }

    class rip_cmovno_6404 extends InstructionWithOffset {
        private final AMD64GeneralRegister64 destination;
        rip_cmovno_6404(int startPosition, int endPosition, AMD64GeneralRegister64 destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_cmovno(destination, offsetAsInt());
        }
    }

    class rip_cmovnp_9846 extends InstructionWithOffset {
        private final AMD64GeneralRegister16 destination;
        rip_cmovnp_9846(int startPosition, int endPosition, AMD64GeneralRegister16 destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_cmovnp(destination, offsetAsInt());
        }
    }

    class rip_cmovnp_9828 extends InstructionWithOffset {
        private final AMD64GeneralRegister32 destination;
        rip_cmovnp_9828(int startPosition, int endPosition, AMD64GeneralRegister32 destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_cmovnp(destination, offsetAsInt());
        }
    }

    class rip_cmovnp_9837 extends InstructionWithOffset {
        private final AMD64GeneralRegister64 destination;
        rip_cmovnp_9837(int startPosition, int endPosition, AMD64GeneralRegister64 destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_cmovnp(destination, offsetAsInt());
        }
    }

    class rip_cmovns_9792 extends InstructionWithOffset {
        private final AMD64GeneralRegister16 destination;
        rip_cmovns_9792(int startPosition, int endPosition, AMD64GeneralRegister16 destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_cmovns(destination, offsetAsInt());
        }
    }

    class rip_cmovns_9774 extends InstructionWithOffset {
        private final AMD64GeneralRegister32 destination;
        rip_cmovns_9774(int startPosition, int endPosition, AMD64GeneralRegister32 destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_cmovns(destination, offsetAsInt());
        }
    }

    class rip_cmovns_9783 extends InstructionWithOffset {
        private final AMD64GeneralRegister64 destination;
        rip_cmovns_9783(int startPosition, int endPosition, AMD64GeneralRegister64 destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_cmovns(destination, offsetAsInt());
        }
    }

    class rip_cmovo_6386 extends InstructionWithOffset {
        private final AMD64GeneralRegister16 destination;
        rip_cmovo_6386(int startPosition, int endPosition, AMD64GeneralRegister16 destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_cmovo(destination, offsetAsInt());
        }
    }

    class rip_cmovo_6368 extends InstructionWithOffset {
        private final AMD64GeneralRegister32 destination;
        rip_cmovo_6368(int startPosition, int endPosition, AMD64GeneralRegister32 destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_cmovo(destination, offsetAsInt());
        }
    }

    class rip_cmovo_6377 extends InstructionWithOffset {
        private final AMD64GeneralRegister64 destination;
        rip_cmovo_6377(int startPosition, int endPosition, AMD64GeneralRegister64 destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_cmovo(destination, offsetAsInt());
        }
    }

    class rip_cmovp_9819 extends InstructionWithOffset {
        private final AMD64GeneralRegister16 destination;
        rip_cmovp_9819(int startPosition, int endPosition, AMD64GeneralRegister16 destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_cmovp(destination, offsetAsInt());
        }
    }

    class rip_cmovp_9801 extends InstructionWithOffset {
        private final AMD64GeneralRegister32 destination;
        rip_cmovp_9801(int startPosition, int endPosition, AMD64GeneralRegister32 destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_cmovp(destination, offsetAsInt());
        }
    }

    class rip_cmovp_9810 extends InstructionWithOffset {
        private final AMD64GeneralRegister64 destination;
        rip_cmovp_9810(int startPosition, int endPosition, AMD64GeneralRegister64 destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_cmovp(destination, offsetAsInt());
        }
    }

    class rip_cmovs_9765 extends InstructionWithOffset {
        private final AMD64GeneralRegister16 destination;
        rip_cmovs_9765(int startPosition, int endPosition, AMD64GeneralRegister16 destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_cmovs(destination, offsetAsInt());
        }
    }

    class rip_cmovs_9747 extends InstructionWithOffset {
        private final AMD64GeneralRegister32 destination;
        rip_cmovs_9747(int startPosition, int endPosition, AMD64GeneralRegister32 destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_cmovs(destination, offsetAsInt());
        }
    }

    class rip_cmovs_9756 extends InstructionWithOffset {
        private final AMD64GeneralRegister64 destination;
        rip_cmovs_9756(int startPosition, int endPosition, AMD64GeneralRegister64 destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_cmovs(destination, offsetAsInt());
        }
    }

    class rip_cmp_3563 extends InstructionWithOffset {
        private final AMD64GeneralRegister16 destination;
        rip_cmp_3563(int startPosition, int endPosition, AMD64GeneralRegister16 destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_cmp(destination, offsetAsInt());
        }
    }

    class rip_cmp_3545 extends InstructionWithOffset {
        private final AMD64GeneralRegister32 destination;
        rip_cmp_3545(int startPosition, int endPosition, AMD64GeneralRegister32 destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_cmp(destination, offsetAsInt());
        }
    }

    class rip_cmp_3554 extends InstructionWithOffset {
        private final AMD64GeneralRegister64 destination;
        rip_cmp_3554(int startPosition, int endPosition, AMD64GeneralRegister64 destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_cmp(destination, offsetAsInt());
        }
    }

    class rip_cmp_3518 extends InstructionWithOffset {
        private final AMD64GeneralRegister8 destination;
        rip_cmp_3518(int startPosition, int endPosition, AMD64GeneralRegister8 destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_cmp(destination, offsetAsInt());
        }
    }

    class rip_cmpb_528 extends InstructionWithOffset {
        private final byte imm8;
        rip_cmpb_528(int startPosition, int endPosition, byte imm8, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.imm8 = imm8;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_cmpb(offsetAsInt(), imm8);
        }
    }

    class rip_cmpl_960 extends InstructionWithOffset {
        private final byte imm8;
        rip_cmpl_960(int startPosition, int endPosition, byte imm8, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.imm8 = imm8;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_cmpl(offsetAsInt(), imm8);
        }
    }

    class rip_cmpq_1032 extends InstructionWithOffset {
        private final byte imm8;
        rip_cmpq_1032(int startPosition, int endPosition, byte imm8, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.imm8 = imm8;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_cmpq(offsetAsInt(), imm8);
        }
    }

    class rip_cmpw_1104 extends InstructionWithOffset {
        private final byte imm8;
        rip_cmpw_1104(int startPosition, int endPosition, byte imm8, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.imm8 = imm8;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_cmpw(offsetAsInt(), imm8);
        }
    }

    class rip_cmp_3509 extends InstructionWithOffset {
        private final AMD64GeneralRegister16 source;
        rip_cmp_3509(int startPosition, int endPosition, AMD64GeneralRegister16 source, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.source = source;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_cmp(offsetAsInt(), source);
        }
    }

    class rip_cmp_3491 extends InstructionWithOffset {
        private final AMD64GeneralRegister32 source;
        rip_cmp_3491(int startPosition, int endPosition, AMD64GeneralRegister32 source, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.source = source;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_cmp(offsetAsInt(), source);
        }
    }

    class rip_cmp_3500 extends InstructionWithOffset {
        private final AMD64GeneralRegister64 source;
        rip_cmp_3500(int startPosition, int endPosition, AMD64GeneralRegister64 source, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.source = source;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_cmp(offsetAsInt(), source);
        }
    }

    class rip_cmp_3464 extends InstructionWithOffset {
        private final AMD64GeneralRegister8 source;
        rip_cmp_3464(int startPosition, int endPosition, AMD64GeneralRegister8 source, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.source = source;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_cmp(offsetAsInt(), source);
        }
    }

    class rip_cmpl_744 extends InstructionWithOffset {
        private final int imm32;
        rip_cmpl_744(int startPosition, int endPosition, int imm32, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.imm32 = imm32;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_cmpl(offsetAsInt(), imm32);
        }
    }

    class rip_cmpq_816 extends InstructionWithOffset {
        private final int imm32;
        rip_cmpq_816(int startPosition, int endPosition, int imm32, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.imm32 = imm32;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_cmpq(offsetAsInt(), imm32);
        }
    }

    class rip_cmpw_888 extends InstructionWithOffset {
        private final short imm16;
        rip_cmpw_888(int startPosition, int endPosition, short imm16, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.imm16 = imm16;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_cmpw(offsetAsInt(), imm16);
        }
    }

    class rip_cmppd_8091 extends InstructionWithOffset {
        private final AMD64XMMRegister destination;
        private final AMD64XMMComparison amd64xmmcomparison;
        rip_cmppd_8091(int startPosition, int endPosition, AMD64XMMRegister destination, AMD64XMMComparison amd64xmmcomparison, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
            this.amd64xmmcomparison = amd64xmmcomparison;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_cmppd(destination, offsetAsInt(), amd64xmmcomparison);
        }
    }

    class rip_cmpps_8003 extends InstructionWithOffset {
        private final AMD64XMMRegister destination;
        private final AMD64XMMComparison amd64xmmcomparison;
        rip_cmpps_8003(int startPosition, int endPosition, AMD64XMMRegister destination, AMD64XMMComparison amd64xmmcomparison, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
            this.amd64xmmcomparison = amd64xmmcomparison;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_cmpps(destination, offsetAsInt(), amd64xmmcomparison);
        }
    }

    class rip_cmpsd_8139 extends InstructionWithOffset {
        private final AMD64XMMRegister destination;
        private final AMD64XMMComparison amd64xmmcomparison;
        rip_cmpsd_8139(int startPosition, int endPosition, AMD64XMMRegister destination, AMD64XMMComparison amd64xmmcomparison, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
            this.amd64xmmcomparison = amd64xmmcomparison;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_cmpsd(destination, offsetAsInt(), amd64xmmcomparison);
        }
    }

    class rip_cmpss_8157 extends InstructionWithOffset {
        private final AMD64XMMRegister destination;
        private final AMD64XMMComparison amd64xmmcomparison;
        rip_cmpss_8157(int startPosition, int endPosition, AMD64XMMRegister destination, AMD64XMMComparison amd64xmmcomparison, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
            this.amd64xmmcomparison = amd64xmmcomparison;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_cmpss(destination, offsetAsInt(), amd64xmmcomparison);
        }
    }

    class rip_cmpxchg_7868 extends InstructionWithOffset {
        private final AMD64GeneralRegister16 source;
        rip_cmpxchg_7868(int startPosition, int endPosition, AMD64GeneralRegister16 source, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.source = source;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_cmpxchg(offsetAsInt(), source);
        }
    }

    class rip_cmpxchg_7850 extends InstructionWithOffset {
        private final AMD64GeneralRegister32 source;
        rip_cmpxchg_7850(int startPosition, int endPosition, AMD64GeneralRegister32 source, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.source = source;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_cmpxchg(offsetAsInt(), source);
        }
    }

    class rip_cmpxchg_7859 extends InstructionWithOffset {
        private final AMD64GeneralRegister64 source;
        rip_cmpxchg_7859(int startPosition, int endPosition, AMD64GeneralRegister64 source, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.source = source;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_cmpxchg(offsetAsInt(), source);
        }
    }

    class rip_cmpxchg_7823 extends InstructionWithOffset {
        private final AMD64GeneralRegister8 source;
        rip_cmpxchg_7823(int startPosition, int endPosition, AMD64GeneralRegister8 source, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.source = source;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_cmpxchg(offsetAsInt(), source);
        }
    }

    class rip_cmpxchg16b_8067 extends InstructionWithOffset {
        rip_cmpxchg16b_8067(int startPosition, int endPosition, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_cmpxchg16b(offsetAsInt());
        }
    }

    class rip_comisd_9621 extends InstructionWithOffset {
        private final AMD64XMMRegister destination;
        rip_comisd_9621(int startPosition, int endPosition, AMD64XMMRegister destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_comisd(destination, offsetAsInt());
        }
    }

    class rip_comiss_9462 extends InstructionWithOffset {
        private final AMD64XMMRegister destination;
        rip_comiss_9462(int startPosition, int endPosition, AMD64XMMRegister destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_comiss(destination, offsetAsInt());
        }
    }

    class rip_cvtdq2pd_8802 extends InstructionWithOffset {
        private final AMD64XMMRegister destination;
        rip_cvtdq2pd_8802(int startPosition, int endPosition, AMD64XMMRegister destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_cvtdq2pd(destination, offsetAsInt());
        }
    }

    class rip_cvtdq2ps_10017 extends InstructionWithOffset {
        private final AMD64XMMRegister destination;
        rip_cvtdq2ps_10017(int startPosition, int endPosition, AMD64XMMRegister destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_cvtdq2ps(destination, offsetAsInt());
        }
    }

    class rip_cvtpd2dq_8784 extends InstructionWithOffset {
        private final AMD64XMMRegister destination;
        rip_cvtpd2dq_8784(int startPosition, int endPosition, AMD64XMMRegister destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_cvtpd2dq(destination, offsetAsInt());
        }
    }

    class rip_cvtpd2pi_9585 extends InstructionWithOffset {
        private final MMXRegister destination;
        rip_cvtpd2pi_9585(int startPosition, int endPosition, MMXRegister destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_cvtpd2pi(destination, offsetAsInt());
        }
    }

    class rip_cvtpd2ps_10143 extends InstructionWithOffset {
        private final AMD64XMMRegister destination;
        rip_cvtpd2ps_10143(int startPosition, int endPosition, AMD64XMMRegister destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_cvtpd2ps(destination, offsetAsInt());
        }
    }

    class rip_cvtpi2pd_9516 extends InstructionWithOffset {
        private final AMD64XMMRegister destination;
        rip_cvtpi2pd_9516(int startPosition, int endPosition, AMD64XMMRegister destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_cvtpi2pd(destination, offsetAsInt());
        }
    }

    class rip_cvtpi2ps_9357 extends InstructionWithOffset {
        private final AMD64XMMRegister destination;
        rip_cvtpi2ps_9357(int startPosition, int endPosition, AMD64XMMRegister destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_cvtpi2ps(destination, offsetAsInt());
        }
    }

    class rip_cvtps2dq_10161 extends InstructionWithOffset {
        private final AMD64XMMRegister destination;
        rip_cvtps2dq_10161(int startPosition, int endPosition, AMD64XMMRegister destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_cvtps2dq(destination, offsetAsInt());
        }
    }

    class rip_cvtps2pd_9999 extends InstructionWithOffset {
        private final AMD64XMMRegister destination;
        rip_cvtps2pd_9999(int startPosition, int endPosition, AMD64XMMRegister destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_cvtps2pd(destination, offsetAsInt());
        }
    }

    class rip_cvtps2pi_9426 extends InstructionWithOffset {
        private final MMXRegister destination;
        rip_cvtps2pi_9426(int startPosition, int endPosition, MMXRegister destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_cvtps2pi(destination, offsetAsInt());
        }
    }

    class rip_cvtsd2si_9675 extends InstructionWithOffset {
        private final AMD64GeneralRegister32 destination;
        rip_cvtsd2si_9675(int startPosition, int endPosition, AMD64GeneralRegister32 destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_cvtsd2si(destination, offsetAsInt());
        }
    }

    class rip_cvtsd2si_9684 extends InstructionWithOffset {
        private final AMD64GeneralRegister64 destination;
        rip_cvtsd2si_9684(int startPosition, int endPosition, AMD64GeneralRegister64 destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_cvtsd2si(destination, offsetAsInt());
        }
    }

    class rip_cvtsd2ss_10287 extends InstructionWithOffset {
        private final AMD64XMMRegister destination;
        rip_cvtsd2ss_10287(int startPosition, int endPosition, AMD64XMMRegister destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_cvtsd2ss(destination, offsetAsInt());
        }
    }

    class rip_cvtsi2sdl_9639 extends InstructionWithOffset {
        private final AMD64XMMRegister destination;
        rip_cvtsi2sdl_9639(int startPosition, int endPosition, AMD64XMMRegister destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_cvtsi2sdl(destination, offsetAsInt());
        }
    }

    class rip_cvtsi2sdq_9648 extends InstructionWithOffset {
        private final AMD64XMMRegister destination;
        rip_cvtsi2sdq_9648(int startPosition, int endPosition, AMD64XMMRegister destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_cvtsi2sdq(destination, offsetAsInt());
        }
    }

    class rip_cvtsi2ssl_9693 extends InstructionWithOffset {
        private final AMD64XMMRegister destination;
        rip_cvtsi2ssl_9693(int startPosition, int endPosition, AMD64XMMRegister destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_cvtsi2ssl(destination, offsetAsInt());
        }
    }

    class rip_cvtsi2ssq_9702 extends InstructionWithOffset {
        private final AMD64XMMRegister destination;
        rip_cvtsi2ssq_9702(int startPosition, int endPosition, AMD64XMMRegister destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_cvtsi2ssq(destination, offsetAsInt());
        }
    }

    class rip_cvtss2sd_10413 extends InstructionWithOffset {
        private final AMD64XMMRegister destination;
        rip_cvtss2sd_10413(int startPosition, int endPosition, AMD64XMMRegister destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_cvtss2sd(destination, offsetAsInt());
        }
    }

    class rip_cvtss2si_9729 extends InstructionWithOffset {
        private final AMD64GeneralRegister32 destination;
        rip_cvtss2si_9729(int startPosition, int endPosition, AMD64GeneralRegister32 destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_cvtss2si(destination, offsetAsInt());
        }
    }

    class rip_cvtss2si_9738 extends InstructionWithOffset {
        private final AMD64GeneralRegister64 destination;
        rip_cvtss2si_9738(int startPosition, int endPosition, AMD64GeneralRegister64 destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_cvtss2si(destination, offsetAsInt());
        }
    }

    class rip_cvttpd2dq_8742 extends InstructionWithOffset {
        private final AMD64XMMRegister destination;
        rip_cvttpd2dq_8742(int startPosition, int endPosition, AMD64XMMRegister destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_cvttpd2dq(destination, offsetAsInt());
        }
    }

    class rip_cvttpd2pi_9567 extends InstructionWithOffset {
        private final MMXRegister destination;
        rip_cvttpd2pi_9567(int startPosition, int endPosition, MMXRegister destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_cvttpd2pi(destination, offsetAsInt());
        }
    }

    class rip_cvttps2dq_10431 extends InstructionWithOffset {
        private final AMD64XMMRegister destination;
        rip_cvttps2dq_10431(int startPosition, int endPosition, AMD64XMMRegister destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_cvttps2dq(destination, offsetAsInt());
        }
    }

    class rip_cvttps2pi_9408 extends InstructionWithOffset {
        private final MMXRegister destination;
        rip_cvttps2pi_9408(int startPosition, int endPosition, MMXRegister destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_cvttps2pi(destination, offsetAsInt());
        }
    }

    class rip_cvttsd2si_9657 extends InstructionWithOffset {
        private final AMD64GeneralRegister32 destination;
        rip_cvttsd2si_9657(int startPosition, int endPosition, AMD64GeneralRegister32 destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_cvttsd2si(destination, offsetAsInt());
        }
    }

    class rip_cvttsd2si_9666 extends InstructionWithOffset {
        private final AMD64GeneralRegister64 destination;
        rip_cvttsd2si_9666(int startPosition, int endPosition, AMD64GeneralRegister64 destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_cvttsd2si(destination, offsetAsInt());
        }
    }

    class rip_cvttss2si_9711 extends InstructionWithOffset {
        private final AMD64GeneralRegister32 destination;
        rip_cvttss2si_9711(int startPosition, int endPosition, AMD64GeneralRegister32 destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_cvttss2si(destination, offsetAsInt());
        }
    }

    class rip_cvttss2si_9720 extends InstructionWithOffset {
        private final AMD64GeneralRegister64 destination;
        rip_cvttss2si_9720(int startPosition, int endPosition, AMD64GeneralRegister64 destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_cvttss2si(destination, offsetAsInt());
        }
    }

    class rip_decb_5328 extends InstructionWithOffset {
        rip_decb_5328(int startPosition, int endPosition, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_decb(offsetAsInt());
        }
    }

    class rip_decl_5382 extends InstructionWithOffset {
        rip_decl_5382(int startPosition, int endPosition, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_decl(offsetAsInt());
        }
    }

    class rip_decq_5400 extends InstructionWithOffset {
        rip_decq_5400(int startPosition, int endPosition, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_decq(offsetAsInt());
        }
    }

    class rip_decw_5418 extends InstructionWithOffset {
        rip_decw_5418(int startPosition, int endPosition, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_decw(offsetAsInt());
        }
    }

    class rip_divb___AL_2708 extends InstructionWithOffset {
        rip_divb___AL_2708(int startPosition, int endPosition, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_divb___AL(offsetAsInt());
        }
    }

    class rip_divl_2924 extends InstructionWithOffset {
        rip_divl_2924(int startPosition, int endPosition, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_divl(offsetAsInt());
        }
    }

    class rip_divq_2996 extends InstructionWithOffset {
        rip_divq_2996(int startPosition, int endPosition, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_divq(offsetAsInt());
        }
    }

    class rip_divw_3068 extends InstructionWithOffset {
        rip_divw_3068(int startPosition, int endPosition, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_divw(offsetAsInt());
        }
    }

    class rip_divpd_10215 extends InstructionWithOffset {
        private final AMD64XMMRegister destination;
        rip_divpd_10215(int startPosition, int endPosition, AMD64XMMRegister destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_divpd(destination, offsetAsInt());
        }
    }

    class rip_divps_10071 extends InstructionWithOffset {
        private final AMD64XMMRegister destination;
        rip_divps_10071(int startPosition, int endPosition, AMD64XMMRegister destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_divps(destination, offsetAsInt());
        }
    }

    class rip_divsd_10341 extends InstructionWithOffset {
        private final AMD64XMMRegister destination;
        rip_divsd_10341(int startPosition, int endPosition, AMD64XMMRegister destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_divsd(destination, offsetAsInt());
        }
    }

    class rip_divss_10485 extends InstructionWithOffset {
        private final AMD64XMMRegister destination;
        rip_divss_10485(int startPosition, int endPosition, AMD64XMMRegister destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_divss(destination, offsetAsInt());
        }
    }

    class rip_fadds_3923 extends InstructionWithOffset {
        rip_fadds_3923(int startPosition, int endPosition, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_fadds(offsetAsInt());
        }
    }

    class rip_faddl_4595 extends InstructionWithOffset {
        rip_faddl_4595(int startPosition, int endPosition, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_faddl(offsetAsInt());
        }
    }

    class rip_fbld_5135 extends InstructionWithOffset {
        rip_fbld_5135(int startPosition, int endPosition, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_fbld(offsetAsInt());
        }
    }

    class rip_fbstp_5143 extends InstructionWithOffset {
        rip_fbstp_5143(int startPosition, int endPosition, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_fbstp(offsetAsInt());
        }
    }

    class rip_fcoms_3931 extends InstructionWithOffset {
        rip_fcoms_3931(int startPosition, int endPosition, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_fcoms(offsetAsInt());
        }
    }

    class rip_fcoml_4603 extends InstructionWithOffset {
        rip_fcoml_4603(int startPosition, int endPosition, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_fcoml(offsetAsInt());
        }
    }

    class rip_fcomps_3935 extends InstructionWithOffset {
        rip_fcomps_3935(int startPosition, int endPosition, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_fcomps(offsetAsInt());
        }
    }

    class rip_fcompl_4607 extends InstructionWithOffset {
        rip_fcompl_4607(int startPosition, int endPosition, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_fcompl(offsetAsInt());
        }
    }

    class rip_fdivs_3947 extends InstructionWithOffset {
        rip_fdivs_3947(int startPosition, int endPosition, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_fdivs(offsetAsInt());
        }
    }

    class rip_fdivl_4619 extends InstructionWithOffset {
        rip_fdivl_4619(int startPosition, int endPosition, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_fdivl(offsetAsInt());
        }
    }

    class rip_fdivrs_3951 extends InstructionWithOffset {
        rip_fdivrs_3951(int startPosition, int endPosition, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_fdivrs(offsetAsInt());
        }
    }

    class rip_fdivrl_4623 extends InstructionWithOffset {
        rip_fdivrl_4623(int startPosition, int endPosition, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_fdivrl(offsetAsInt());
        }
    }

    class rip_fiaddl_4283 extends InstructionWithOffset {
        rip_fiaddl_4283(int startPosition, int endPosition, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_fiaddl(offsetAsInt());
        }
    }

    class rip_fiadds_4931 extends InstructionWithOffset {
        rip_fiadds_4931(int startPosition, int endPosition, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_fiadds(offsetAsInt());
        }
    }

    class rip_ficoml_4291 extends InstructionWithOffset {
        rip_ficoml_4291(int startPosition, int endPosition, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_ficoml(offsetAsInt());
        }
    }

    class rip_ficoms_4939 extends InstructionWithOffset {
        rip_ficoms_4939(int startPosition, int endPosition, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_ficoms(offsetAsInt());
        }
    }

    class rip_ficompl_4295 extends InstructionWithOffset {
        rip_ficompl_4295(int startPosition, int endPosition, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_ficompl(offsetAsInt());
        }
    }

    class rip_ficomps_4943 extends InstructionWithOffset {
        rip_ficomps_4943(int startPosition, int endPosition, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_ficomps(offsetAsInt());
        }
    }

    class rip_fidivl_4307 extends InstructionWithOffset {
        rip_fidivl_4307(int startPosition, int endPosition, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_fidivl(offsetAsInt());
        }
    }

    class rip_fidivs_4955 extends InstructionWithOffset {
        rip_fidivs_4955(int startPosition, int endPosition, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_fidivs(offsetAsInt());
        }
    }

    class rip_fidivrl_4311 extends InstructionWithOffset {
        rip_fidivrl_4311(int startPosition, int endPosition, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_fidivrl(offsetAsInt());
        }
    }

    class rip_fidivrs_4959 extends InstructionWithOffset {
        rip_fidivrs_4959(int startPosition, int endPosition, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_fidivrs(offsetAsInt());
        }
    }

    class rip_fildl_4475 extends InstructionWithOffset {
        rip_fildl_4475(int startPosition, int endPosition, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_fildl(offsetAsInt());
        }
    }

    class rip_filds_5123 extends InstructionWithOffset {
        rip_filds_5123(int startPosition, int endPosition, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_filds(offsetAsInt());
        }
    }

    class rip_fildq_5139 extends InstructionWithOffset {
        rip_fildq_5139(int startPosition, int endPosition, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_fildq(offsetAsInt());
        }
    }

    class rip_fimull_4287 extends InstructionWithOffset {
        rip_fimull_4287(int startPosition, int endPosition, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_fimull(offsetAsInt());
        }
    }

    class rip_fimuls_4935 extends InstructionWithOffset {
        rip_fimuls_4935(int startPosition, int endPosition, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_fimuls(offsetAsInt());
        }
    }

    class rip_fistl_4479 extends InstructionWithOffset {
        rip_fistl_4479(int startPosition, int endPosition, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_fistl(offsetAsInt());
        }
    }

    class rip_fists_5127 extends InstructionWithOffset {
        rip_fists_5127(int startPosition, int endPosition, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_fists(offsetAsInt());
        }
    }

    class rip_fistpl_4483 extends InstructionWithOffset {
        rip_fistpl_4483(int startPosition, int endPosition, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_fistpl(offsetAsInt());
        }
    }

    class rip_fistps_5131 extends InstructionWithOffset {
        rip_fistps_5131(int startPosition, int endPosition, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_fistps(offsetAsInt());
        }
    }

    class rip_fistpq_5147 extends InstructionWithOffset {
        rip_fistpq_5147(int startPosition, int endPosition, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_fistpq(offsetAsInt());
        }
    }

    class rip_fisubl_4299 extends InstructionWithOffset {
        rip_fisubl_4299(int startPosition, int endPosition, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_fisubl(offsetAsInt());
        }
    }

    class rip_fisubs_4947 extends InstructionWithOffset {
        rip_fisubs_4947(int startPosition, int endPosition, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_fisubs(offsetAsInt());
        }
    }

    class rip_fisubrl_4303 extends InstructionWithOffset {
        rip_fisubrl_4303(int startPosition, int endPosition, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_fisubrl(offsetAsInt());
        }
    }

    class rip_fisubrs_4951 extends InstructionWithOffset {
        rip_fisubrs_4951(int startPosition, int endPosition, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_fisubrs(offsetAsInt());
        }
    }

    class rip_flds_4115 extends InstructionWithOffset {
        rip_flds_4115(int startPosition, int endPosition, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_flds(offsetAsInt());
        }
    }

    class rip_fldt_4487 extends InstructionWithOffset {
        rip_fldt_4487(int startPosition, int endPosition, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_fldt(offsetAsInt());
        }
    }

    class rip_fldl_4787 extends InstructionWithOffset {
        rip_fldl_4787(int startPosition, int endPosition, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_fldl(offsetAsInt());
        }
    }

    class rip_fldcw_4131 extends InstructionWithOffset {
        rip_fldcw_4131(int startPosition, int endPosition, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_fldcw(offsetAsInt());
        }
    }

    class rip_fldenv_4127 extends InstructionWithOffset {
        rip_fldenv_4127(int startPosition, int endPosition, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_fldenv(offsetAsInt());
        }
    }

    class rip_fmuls_3927 extends InstructionWithOffset {
        rip_fmuls_3927(int startPosition, int endPosition, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_fmuls(offsetAsInt());
        }
    }

    class rip_fmull_4599 extends InstructionWithOffset {
        rip_fmull_4599(int startPosition, int endPosition, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_fmull(offsetAsInt());
        }
    }

    class rip_frstor_4799 extends InstructionWithOffset {
        rip_frstor_4799(int startPosition, int endPosition, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_frstor(offsetAsInt());
        }
    }

    class rip_fsave_4803 extends InstructionWithOffset {
        rip_fsave_4803(int startPosition, int endPosition, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_fsave(offsetAsInt());
        }
    }

    class rip_fsts_4119 extends InstructionWithOffset {
        rip_fsts_4119(int startPosition, int endPosition, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_fsts(offsetAsInt());
        }
    }

    class rip_fstl_4791 extends InstructionWithOffset {
        rip_fstl_4791(int startPosition, int endPosition, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_fstl(offsetAsInt());
        }
    }

    class rip_fstcw_4139 extends InstructionWithOffset {
        rip_fstcw_4139(int startPosition, int endPosition, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_fstcw(offsetAsInt());
        }
    }

    class rip_fstenv_4135 extends InstructionWithOffset {
        rip_fstenv_4135(int startPosition, int endPosition, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_fstenv(offsetAsInt());
        }
    }

    class rip_fstps_4123 extends InstructionWithOffset {
        rip_fstps_4123(int startPosition, int endPosition, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_fstps(offsetAsInt());
        }
    }

    class rip_fstpt_4491 extends InstructionWithOffset {
        rip_fstpt_4491(int startPosition, int endPosition, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_fstpt(offsetAsInt());
        }
    }

    class rip_fstpl_4795 extends InstructionWithOffset {
        rip_fstpl_4795(int startPosition, int endPosition, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_fstpl(offsetAsInt());
        }
    }

    class rip_fstsw_4807 extends InstructionWithOffset {
        rip_fstsw_4807(int startPosition, int endPosition, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_fstsw(offsetAsInt());
        }
    }

    class rip_fsubs_3939 extends InstructionWithOffset {
        rip_fsubs_3939(int startPosition, int endPosition, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_fsubs(offsetAsInt());
        }
    }

    class rip_fsubl_4611 extends InstructionWithOffset {
        rip_fsubl_4611(int startPosition, int endPosition, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_fsubl(offsetAsInt());
        }
    }

    class rip_fsubrs_3943 extends InstructionWithOffset {
        rip_fsubrs_3943(int startPosition, int endPosition, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_fsubrs(offsetAsInt());
        }
    }

    class rip_fsubrl_4615 extends InstructionWithOffset {
        rip_fsubrl_4615(int startPosition, int endPosition, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_fsubrl(offsetAsInt());
        }
    }

    class rip_fxrstor_11341 extends InstructionWithOffset {
        rip_fxrstor_11341(int startPosition, int endPosition, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_fxrstor(offsetAsInt());
        }
    }

    class rip_fxsave_11337 extends InstructionWithOffset {
        rip_fxsave_11337(int startPosition, int endPosition, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_fxsave(offsetAsInt());
        }
    }

    class rip_haddpd_10881 extends InstructionWithOffset {
        private final AMD64XMMRegister destination;
        rip_haddpd_10881(int startPosition, int endPosition, AMD64XMMRegister destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_haddpd(destination, offsetAsInt());
        }
    }

    class rip_haddps_10953 extends InstructionWithOffset {
        private final AMD64XMMRegister destination;
        rip_haddps_10953(int startPosition, int endPosition, AMD64XMMRegister destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_haddps(destination, offsetAsInt());
        }
    }

    class rip_hsubpd_10899 extends InstructionWithOffset {
        private final AMD64XMMRegister destination;
        rip_hsubpd_10899(int startPosition, int endPosition, AMD64XMMRegister destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_hsubpd(destination, offsetAsInt());
        }
    }

    class rip_hsubps_10971 extends InstructionWithOffset {
        private final AMD64XMMRegister destination;
        rip_hsubps_10971(int startPosition, int endPosition, AMD64XMMRegister destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_hsubps(destination, offsetAsInt());
        }
    }

    class rip_idivb___AL_2712 extends InstructionWithOffset {
        rip_idivb___AL_2712(int startPosition, int endPosition, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_idivb___AL(offsetAsInt());
        }
    }

    class rip_idivl_2928 extends InstructionWithOffset {
        rip_idivl_2928(int startPosition, int endPosition, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_idivl(offsetAsInt());
        }
    }

    class rip_idivq_3000 extends InstructionWithOffset {
        rip_idivq_3000(int startPosition, int endPosition, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_idivq(offsetAsInt());
        }
    }

    class rip_idivw_3072 extends InstructionWithOffset {
        rip_idivw_3072(int startPosition, int endPosition, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_idivw(offsetAsInt());
        }
    }

    class rip_imul_11484 extends InstructionWithOffset {
        private final AMD64GeneralRegister16 destination;
        rip_imul_11484(int startPosition, int endPosition, AMD64GeneralRegister16 destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_imul(destination, offsetAsInt());
        }
    }

    class rip_imul_3629 extends InstructionWithOffset {
        private final AMD64GeneralRegister16 destination;
        private final byte imm8;
        rip_imul_3629(int startPosition, int endPosition, AMD64GeneralRegister16 destination, byte imm8, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
            this.imm8 = imm8;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_imul(destination, offsetAsInt(), imm8);
        }
    }

    class rip_imul_3600 extends InstructionWithOffset {
        private final AMD64GeneralRegister16 destination;
        private final short imm16;
        rip_imul_3600(int startPosition, int endPosition, AMD64GeneralRegister16 destination, short imm16, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
            this.imm16 = imm16;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_imul(destination, offsetAsInt(), imm16);
        }
    }

    class rip_imul_11466 extends InstructionWithOffset {
        private final AMD64GeneralRegister32 destination;
        rip_imul_11466(int startPosition, int endPosition, AMD64GeneralRegister32 destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_imul(destination, offsetAsInt());
        }
    }

    class rip_imul_3611 extends InstructionWithOffset {
        private final AMD64GeneralRegister32 destination;
        private final byte imm8;
        rip_imul_3611(int startPosition, int endPosition, AMD64GeneralRegister32 destination, byte imm8, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
            this.imm8 = imm8;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_imul(destination, offsetAsInt(), imm8);
        }
    }

    class rip_imul_3582 extends InstructionWithOffset {
        private final AMD64GeneralRegister32 destination;
        private final int imm32;
        rip_imul_3582(int startPosition, int endPosition, AMD64GeneralRegister32 destination, int imm32, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
            this.imm32 = imm32;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_imul(destination, offsetAsInt(), imm32);
        }
    }

    class rip_imul_11475 extends InstructionWithOffset {
        private final AMD64GeneralRegister64 destination;
        rip_imul_11475(int startPosition, int endPosition, AMD64GeneralRegister64 destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_imul(destination, offsetAsInt());
        }
    }

    class rip_imul_3620 extends InstructionWithOffset {
        private final AMD64GeneralRegister64 destination;
        private final byte imm8;
        rip_imul_3620(int startPosition, int endPosition, AMD64GeneralRegister64 destination, byte imm8, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
            this.imm8 = imm8;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_imul(destination, offsetAsInt(), imm8);
        }
    }

    class rip_imul_3591 extends InstructionWithOffset {
        private final AMD64GeneralRegister64 destination;
        private final int imm32;
        rip_imul_3591(int startPosition, int endPosition, AMD64GeneralRegister64 destination, int imm32, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
            this.imm32 = imm32;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_imul(destination, offsetAsInt(), imm32);
        }
    }

    class rip_imulb___AL_2704 extends InstructionWithOffset {
        rip_imulb___AL_2704(int startPosition, int endPosition, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_imulb___AL(offsetAsInt());
        }
    }

    class rip_imull_2920 extends InstructionWithOffset {
        rip_imull_2920(int startPosition, int endPosition, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_imull(offsetAsInt());
        }
    }

    class rip_imulq_2992 extends InstructionWithOffset {
        rip_imulq_2992(int startPosition, int endPosition, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_imulq(offsetAsInt());
        }
    }

    class rip_imulw_3064 extends InstructionWithOffset {
        rip_imulw_3064(int startPosition, int endPosition, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_imulw(offsetAsInt());
        }
    }

    class rip_incb_5324 extends InstructionWithOffset {
        rip_incb_5324(int startPosition, int endPosition, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_incb(offsetAsInt());
        }
    }

    class rip_incl_5378 extends InstructionWithOffset {
        rip_incl_5378(int startPosition, int endPosition, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_incl(offsetAsInt());
        }
    }

    class rip_incq_5396 extends InstructionWithOffset {
        rip_incq_5396(int startPosition, int endPosition, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_incq(offsetAsInt());
        }
    }

    class rip_incw_5414 extends InstructionWithOffset {
        rip_incw_5414(int startPosition, int endPosition, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_incw(offsetAsInt());
        }
    }

    class rip_invlpg_5672 extends InstructionWithOffset {
        rip_invlpg_5672(int startPosition, int endPosition, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_invlpg(offsetAsInt());
        }
    }

    class jb_491 extends InstructionWithOffset {
        jb_491(int startPosition, int endPosition, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 1 | 4);
        }
        @Override
        protected void assemble() throws AssemblyException {
            final int labelSize = labelSize();
            if (labelSize == 1) {
                jb(offsetAsByte());
            } else if (labelSize == 4) {
                jb(offsetAsInt());
            } else {
                throw new AssemblyException("Unexpected label width: " + labelSize);
            }
        }
    }

    class jbe_495 extends InstructionWithOffset {
        jbe_495(int startPosition, int endPosition, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 1 | 4);
        }
        @Override
        protected void assemble() throws AssemblyException {
            final int labelSize = labelSize();
            if (labelSize == 1) {
                jbe(offsetAsByte());
            } else if (labelSize == 4) {
                jbe(offsetAsInt());
            } else {
                throw new AssemblyException("Unexpected label width: " + labelSize);
            }
        }
    }

    class jl_3649 extends InstructionWithOffset {
        jl_3649(int startPosition, int endPosition, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 1 | 4);
        }
        @Override
        protected void assemble() throws AssemblyException {
            final int labelSize = labelSize();
            if (labelSize == 1) {
                jl(offsetAsByte());
            } else if (labelSize == 4) {
                jl(offsetAsInt());
            } else {
                throw new AssemblyException("Unexpected label width: " + labelSize);
            }
        }
    }

    class jle_3651 extends InstructionWithOffset {
        jle_3651(int startPosition, int endPosition, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 1 | 4);
        }
        @Override
        protected void assemble() throws AssemblyException {
            final int labelSize = labelSize();
            if (labelSize == 1) {
                jle(offsetAsByte());
            } else if (labelSize == 4) {
                jle(offsetAsInt());
            } else {
                throw new AssemblyException("Unexpected label width: " + labelSize);
            }
        }
    }

    class jmp_5290 extends InstructionWithOffset {
        jmp_5290(int startPosition, int endPosition, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 1 | 4);
        }
        @Override
        protected void assemble() throws AssemblyException {
            final int labelSize = labelSize();
            if (labelSize == 1) {
                jmp(offsetAsByte());
            } else if (labelSize == 4) {
                jmp(offsetAsInt());
            } else {
                throw new AssemblyException("Unexpected label width: " + labelSize);
            }
        }
    }

    class rip_jmp_5436 extends InstructionWithOffset {
        rip_jmp_5436(int startPosition, int endPosition, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_jmp(offsetAsInt());
        }
    }

    class jnb_492 extends InstructionWithOffset {
        jnb_492(int startPosition, int endPosition, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 1 | 4);
        }
        @Override
        protected void assemble() throws AssemblyException {
            final int labelSize = labelSize();
            if (labelSize == 1) {
                jnb(offsetAsByte());
            } else if (labelSize == 4) {
                jnb(offsetAsInt());
            } else {
                throw new AssemblyException("Unexpected label width: " + labelSize);
            }
        }
    }

    class jnbe_496 extends InstructionWithOffset {
        jnbe_496(int startPosition, int endPosition, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 1 | 4);
        }
        @Override
        protected void assemble() throws AssemblyException {
            final int labelSize = labelSize();
            if (labelSize == 1) {
                jnbe(offsetAsByte());
            } else if (labelSize == 4) {
                jnbe(offsetAsInt());
            } else {
                throw new AssemblyException("Unexpected label width: " + labelSize);
            }
        }
    }

    class jnl_3650 extends InstructionWithOffset {
        jnl_3650(int startPosition, int endPosition, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 1 | 4);
        }
        @Override
        protected void assemble() throws AssemblyException {
            final int labelSize = labelSize();
            if (labelSize == 1) {
                jnl(offsetAsByte());
            } else if (labelSize == 4) {
                jnl(offsetAsInt());
            } else {
                throw new AssemblyException("Unexpected label width: " + labelSize);
            }
        }
    }

    class jnle_3652 extends InstructionWithOffset {
        jnle_3652(int startPosition, int endPosition, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 1 | 4);
        }
        @Override
        protected void assemble() throws AssemblyException {
            final int labelSize = labelSize();
            if (labelSize == 1) {
                jnle(offsetAsByte());
            } else if (labelSize == 4) {
                jnle(offsetAsInt());
            } else {
                throw new AssemblyException("Unexpected label width: " + labelSize);
            }
        }
    }

    class jno_490 extends InstructionWithOffset {
        jno_490(int startPosition, int endPosition, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 1 | 4);
        }
        @Override
        protected void assemble() throws AssemblyException {
            final int labelSize = labelSize();
            if (labelSize == 1) {
                jno(offsetAsByte());
            } else if (labelSize == 4) {
                jno(offsetAsInt());
            } else {
                throw new AssemblyException("Unexpected label width: " + labelSize);
            }
        }
    }

    class jnp_3648 extends InstructionWithOffset {
        jnp_3648(int startPosition, int endPosition, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 1 | 4);
        }
        @Override
        protected void assemble() throws AssemblyException {
            final int labelSize = labelSize();
            if (labelSize == 1) {
                jnp(offsetAsByte());
            } else if (labelSize == 4) {
                jnp(offsetAsInt());
            } else {
                throw new AssemblyException("Unexpected label width: " + labelSize);
            }
        }
    }

    class jns_3646 extends InstructionWithOffset {
        jns_3646(int startPosition, int endPosition, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 1 | 4);
        }
        @Override
        protected void assemble() throws AssemblyException {
            final int labelSize = labelSize();
            if (labelSize == 1) {
                jns(offsetAsByte());
            } else if (labelSize == 4) {
                jns(offsetAsInt());
            } else {
                throw new AssemblyException("Unexpected label width: " + labelSize);
            }
        }
    }

    class jnz_494 extends InstructionWithOffset {
        jnz_494(int startPosition, int endPosition, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 1 | 4);
        }
        @Override
        protected void assemble() throws AssemblyException {
            final int labelSize = labelSize();
            if (labelSize == 1) {
                jnz(offsetAsByte());
            } else if (labelSize == 4) {
                jnz(offsetAsInt());
            } else {
                throw new AssemblyException("Unexpected label width: " + labelSize);
            }
        }
    }

    class jo_489 extends InstructionWithOffset {
        jo_489(int startPosition, int endPosition, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 1 | 4);
        }
        @Override
        protected void assemble() throws AssemblyException {
            final int labelSize = labelSize();
            if (labelSize == 1) {
                jo(offsetAsByte());
            } else if (labelSize == 4) {
                jo(offsetAsInt());
            } else {
                throw new AssemblyException("Unexpected label width: " + labelSize);
            }
        }
    }

    class jp_3647 extends InstructionWithOffset {
        jp_3647(int startPosition, int endPosition, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 1 | 4);
        }
        @Override
        protected void assemble() throws AssemblyException {
            final int labelSize = labelSize();
            if (labelSize == 1) {
                jp(offsetAsByte());
            } else if (labelSize == 4) {
                jp(offsetAsInt());
            } else {
                throw new AssemblyException("Unexpected label width: " + labelSize);
            }
        }
    }

    class jrcxz_2649 extends InstructionWithOffset {
        jrcxz_2649(int startPosition, int endPosition, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 1);
        }
        @Override
        protected void assemble() throws AssemblyException {
            jrcxz(offsetAsByte());
        }
    }

    class js_3645 extends InstructionWithOffset {
        js_3645(int startPosition, int endPosition, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 1 | 4);
        }
        @Override
        protected void assemble() throws AssemblyException {
            final int labelSize = labelSize();
            if (labelSize == 1) {
                js(offsetAsByte());
            } else if (labelSize == 4) {
                js(offsetAsInt());
            } else {
                throw new AssemblyException("Unexpected label width: " + labelSize);
            }
        }
    }

    class jz_493 extends InstructionWithOffset {
        jz_493(int startPosition, int endPosition, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 1 | 4);
        }
        @Override
        protected void assemble() throws AssemblyException {
            final int labelSize = labelSize();
            if (labelSize == 1) {
                jz(offsetAsByte());
            } else if (labelSize == 4) {
                jz(offsetAsInt());
            } else {
                throw new AssemblyException("Unexpected label width: " + labelSize);
            }
        }
    }

    class rip_lar_5843 extends InstructionWithOffset {
        private final AMD64GeneralRegister16 destination;
        rip_lar_5843(int startPosition, int endPosition, AMD64GeneralRegister16 destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_lar(destination, offsetAsInt());
        }
    }

    class rip_lar_5825 extends InstructionWithOffset {
        private final AMD64GeneralRegister32 destination;
        rip_lar_5825(int startPosition, int endPosition, AMD64GeneralRegister32 destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_lar(destination, offsetAsInt());
        }
    }

    class rip_lar_5834 extends InstructionWithOffset {
        private final AMD64GeneralRegister64 destination;
        rip_lar_5834(int startPosition, int endPosition, AMD64GeneralRegister64 destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_lar(destination, offsetAsInt());
        }
    }

    class rip_lddqu_9096 extends InstructionWithOffset {
        private final AMD64XMMRegister destination;
        rip_lddqu_9096(int startPosition, int endPosition, AMD64XMMRegister destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_lddqu(destination, offsetAsInt());
        }
    }

    class rip_ldmxcsr_11345 extends InstructionWithOffset {
        rip_ldmxcsr_11345(int startPosition, int endPosition, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_ldmxcsr(offsetAsInt());
        }
    }

    class rip_lea_3807 extends InstructionWithOffset {
        private final AMD64GeneralRegister16 destination;
        rip_lea_3807(int startPosition, int endPosition, AMD64GeneralRegister16 destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_lea(destination, offsetAsInt());
        }
    }

    class rip_lea_3791 extends InstructionWithOffset {
        private final AMD64GeneralRegister32 destination;
        rip_lea_3791(int startPosition, int endPosition, AMD64GeneralRegister32 destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_lea(destination, offsetAsInt());
        }
    }

    class rip_lea_3799 extends InstructionWithOffset {
        private final AMD64GeneralRegister64 destination;
        rip_lea_3799(int startPosition, int endPosition, AMD64GeneralRegister64 destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_lea(destination, offsetAsInt());
        }
    }

    class rip_lgdt_5656 extends InstructionWithOffset {
        rip_lgdt_5656(int startPosition, int endPosition, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_lgdt(offsetAsInt());
        }
    }

    class rip_lidt_5660 extends InstructionWithOffset {
        rip_lidt_5660(int startPosition, int endPosition, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_lidt(offsetAsInt());
        }
    }

    class rip_lldt_5494 extends InstructionWithOffset {
        rip_lldt_5494(int startPosition, int endPosition, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_lldt(offsetAsInt());
        }
    }

    class rip_lmsw_5668 extends InstructionWithOffset {
        rip_lmsw_5668(int startPosition, int endPosition, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_lmsw(offsetAsInt());
        }
    }

    class loop_2647 extends InstructionWithOffset {
        loop_2647(int startPosition, int endPosition, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 1);
        }
        @Override
        protected void assemble() throws AssemblyException {
            loop(offsetAsByte());
        }
    }

    class loope_2645 extends InstructionWithOffset {
        loope_2645(int startPosition, int endPosition, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 1);
        }
        @Override
        protected void assemble() throws AssemblyException {
            loope(offsetAsByte());
        }
    }

    class loopne_2643 extends InstructionWithOffset {
        loopne_2643(int startPosition, int endPosition, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 1);
        }
        @Override
        protected void assemble() throws AssemblyException {
            loopne(offsetAsByte());
        }
    }

    class rip_lsl_5870 extends InstructionWithOffset {
        private final AMD64GeneralRegister16 destination;
        rip_lsl_5870(int startPosition, int endPosition, AMD64GeneralRegister16 destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_lsl(destination, offsetAsInt());
        }
    }

    class rip_lsl_5852 extends InstructionWithOffset {
        private final AMD64GeneralRegister32 destination;
        rip_lsl_5852(int startPosition, int endPosition, AMD64GeneralRegister32 destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_lsl(destination, offsetAsInt());
        }
    }

    class rip_lsl_5861 extends InstructionWithOffset {
        private final AMD64GeneralRegister64 destination;
        rip_lsl_5861(int startPosition, int endPosition, AMD64GeneralRegister64 destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_lsl(destination, offsetAsInt());
        }
    }

    class rip_ltr_5498 extends InstructionWithOffset {
        rip_ltr_5498(int startPosition, int endPosition, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_ltr(offsetAsInt());
        }
    }

    class rip_maxpd_10233 extends InstructionWithOffset {
        private final AMD64XMMRegister destination;
        rip_maxpd_10233(int startPosition, int endPosition, AMD64XMMRegister destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_maxpd(destination, offsetAsInt());
        }
    }

    class rip_maxps_10089 extends InstructionWithOffset {
        private final AMD64XMMRegister destination;
        rip_maxps_10089(int startPosition, int endPosition, AMD64XMMRegister destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_maxps(destination, offsetAsInt());
        }
    }

    class rip_maxsd_10359 extends InstructionWithOffset {
        private final AMD64XMMRegister destination;
        rip_maxsd_10359(int startPosition, int endPosition, AMD64XMMRegister destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_maxsd(destination, offsetAsInt());
        }
    }

    class rip_maxss_10503 extends InstructionWithOffset {
        private final AMD64XMMRegister destination;
        rip_maxss_10503(int startPosition, int endPosition, AMD64XMMRegister destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_maxss(destination, offsetAsInt());
        }
    }

    class rip_minpd_10197 extends InstructionWithOffset {
        private final AMD64XMMRegister destination;
        rip_minpd_10197(int startPosition, int endPosition, AMD64XMMRegister destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_minpd(destination, offsetAsInt());
        }
    }

    class rip_minps_10053 extends InstructionWithOffset {
        private final AMD64XMMRegister destination;
        rip_minps_10053(int startPosition, int endPosition, AMD64XMMRegister destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_minps(destination, offsetAsInt());
        }
    }

    class rip_minsd_10323 extends InstructionWithOffset {
        private final AMD64XMMRegister destination;
        rip_minsd_10323(int startPosition, int endPosition, AMD64XMMRegister destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_minsd(destination, offsetAsInt());
        }
    }

    class rip_minss_10467 extends InstructionWithOffset {
        private final AMD64XMMRegister destination;
        rip_minss_10467(int startPosition, int endPosition, AMD64XMMRegister destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_minss(destination, offsetAsInt());
        }
    }

    class rip_mov_3755 extends InstructionWithOffset {
        private final AMD64GeneralRegister16 destination;
        rip_mov_3755(int startPosition, int endPosition, AMD64GeneralRegister16 destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_mov(destination, offsetAsInt());
        }
    }

    class rip_mov_3737 extends InstructionWithOffset {
        private final AMD64GeneralRegister32 destination;
        rip_mov_3737(int startPosition, int endPosition, AMD64GeneralRegister32 destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_mov(destination, offsetAsInt());
        }
    }

    class rip_mov_3746 extends InstructionWithOffset {
        private final AMD64GeneralRegister64 destination;
        rip_mov_3746(int startPosition, int endPosition, AMD64GeneralRegister64 destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_mov(destination, offsetAsInt());
        }
    }

    class rip_mov_3710 extends InstructionWithOffset {
        private final AMD64GeneralRegister8 destination;
        rip_mov_3710(int startPosition, int endPosition, AMD64GeneralRegister8 destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_mov(destination, offsetAsInt());
        }
    }

    class rip_mov_3815 extends InstructionWithOffset {
        private final SegmentRegister destination;
        rip_mov_3815(int startPosition, int endPosition, SegmentRegister destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_mov(destination, offsetAsInt());
        }
    }

    class rip_movb_1725 extends InstructionWithOffset {
        private final byte imm8;
        rip_movb_1725(int startPosition, int endPosition, byte imm8, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.imm8 = imm8;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_movb(offsetAsInt(), imm8);
        }
    }

    class rip_mov_3701 extends InstructionWithOffset {
        private final AMD64GeneralRegister16 source;
        rip_mov_3701(int startPosition, int endPosition, AMD64GeneralRegister16 source, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.source = source;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_mov(offsetAsInt(), source);
        }
    }

    class rip_mov_3683 extends InstructionWithOffset {
        private final AMD64GeneralRegister32 source;
        rip_mov_3683(int startPosition, int endPosition, AMD64GeneralRegister32 source, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.source = source;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_mov(offsetAsInt(), source);
        }
    }

    class rip_mov_3692 extends InstructionWithOffset {
        private final AMD64GeneralRegister64 source;
        rip_mov_3692(int startPosition, int endPosition, AMD64GeneralRegister64 source, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.source = source;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_mov(offsetAsInt(), source);
        }
    }

    class rip_mov_3656 extends InstructionWithOffset {
        private final AMD64GeneralRegister8 source;
        rip_mov_3656(int startPosition, int endPosition, AMD64GeneralRegister8 source, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.source = source;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_mov(offsetAsInt(), source);
        }
    }

    class rip_mov_3764 extends InstructionWithOffset {
        private final SegmentRegister source;
        rip_mov_3764(int startPosition, int endPosition, SegmentRegister source, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.source = source;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_mov(offsetAsInt(), source);
        }
    }

    class rip_movl_1752 extends InstructionWithOffset {
        private final int imm32;
        rip_movl_1752(int startPosition, int endPosition, int imm32, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.imm32 = imm32;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_movl(offsetAsInt(), imm32);
        }
    }

    class rip_movq_1761 extends InstructionWithOffset {
        private final int imm32;
        rip_movq_1761(int startPosition, int endPosition, int imm32, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.imm32 = imm32;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_movq(offsetAsInt(), imm32);
        }
    }

    class rip_movw_1770 extends InstructionWithOffset {
        private final short imm16;
        rip_movw_1770(int startPosition, int endPosition, short imm16, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.imm16 = imm16;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_movw(offsetAsInt(), imm16);
        }
    }

    class m_mov_AL_1259 extends InstructionWithAddress {
        m_mov_AL_1259(int startPosition, int endPosition, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label);
        }
        @Override
        protected void assemble() throws AssemblyException {
            m_mov_AL(addressAsLong());
        }
    }

    class m_mov_EAX_1262 extends InstructionWithAddress {
        m_mov_EAX_1262(int startPosition, int endPosition, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label);
        }
        @Override
        protected void assemble() throws AssemblyException {
            m_mov_EAX(addressAsLong());
        }
    }

    class m_mov_RAX_1263 extends InstructionWithAddress {
        m_mov_RAX_1263(int startPosition, int endPosition, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label);
        }
        @Override
        protected void assemble() throws AssemblyException {
            m_mov_RAX(addressAsLong());
        }
    }

    class m_mov_AX_1264 extends InstructionWithAddress {
        m_mov_AX_1264(int startPosition, int endPosition, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label);
        }
        @Override
        protected void assemble() throws AssemblyException {
            m_mov_AX(addressAsLong());
        }
    }

    class m_mov___AL_1265 extends InstructionWithAddress {
        m_mov___AL_1265(int startPosition, int endPosition, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label);
        }
        @Override
        protected void assemble() throws AssemblyException {
            m_mov___AL(addressAsLong());
        }
    }

    class m_mov___EAX_1268 extends InstructionWithAddress {
        m_mov___EAX_1268(int startPosition, int endPosition, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label);
        }
        @Override
        protected void assemble() throws AssemblyException {
            m_mov___EAX(addressAsLong());
        }
    }

    class m_mov___RAX_1269 extends InstructionWithAddress {
        m_mov___RAX_1269(int startPosition, int endPosition, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label);
        }
        @Override
        protected void assemble() throws AssemblyException {
            m_mov___RAX(addressAsLong());
        }
    }

    class m_mov___AX_1270 extends InstructionWithAddress {
        m_mov___AX_1270(int startPosition, int endPosition, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label);
        }
        @Override
        protected void assemble() throws AssemblyException {
            m_mov___AX(addressAsLong());
        }
    }

    class rip_movapd_9480 extends InstructionWithOffset {
        private final AMD64XMMRegister destination;
        rip_movapd_9480(int startPosition, int endPosition, AMD64XMMRegister destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_movapd(destination, offsetAsInt());
        }
    }

    class rip_movapd_9498 extends InstructionWithOffset {
        private final AMD64XMMRegister source;
        rip_movapd_9498(int startPosition, int endPosition, AMD64XMMRegister source, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.source = source;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_movapd(offsetAsInt(), source);
        }
    }

    class rip_movaps_9321 extends InstructionWithOffset {
        private final AMD64XMMRegister destination;
        rip_movaps_9321(int startPosition, int endPosition, AMD64XMMRegister destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_movaps(destination, offsetAsInt());
        }
    }

    class rip_movaps_9339 extends InstructionWithOffset {
        private final AMD64XMMRegister source;
        rip_movaps_9339(int startPosition, int endPosition, AMD64XMMRegister source, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.source = source;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_movaps(offsetAsInt(), source);
        }
    }

    class rip_movdl_10782 extends InstructionWithOffset {
        private final AMD64XMMRegister destination;
        rip_movdl_10782(int startPosition, int endPosition, AMD64XMMRegister destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_movdl(destination, offsetAsInt());
        }
    }

    class rip_movdq_10791 extends InstructionWithOffset {
        private final AMD64XMMRegister destination;
        rip_movdq_10791(int startPosition, int endPosition, AMD64XMMRegister destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_movdq(destination, offsetAsInt());
        }
    }

    class rip_movdl_10629 extends InstructionWithOffset {
        private final MMXRegister destination;
        rip_movdl_10629(int startPosition, int endPosition, MMXRegister destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_movdl(destination, offsetAsInt());
        }
    }

    class rip_movdq_10638 extends InstructionWithOffset {
        private final MMXRegister destination;
        rip_movdq_10638(int startPosition, int endPosition, MMXRegister destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_movdq(destination, offsetAsInt());
        }
    }

    class rip_movdl_10917 extends InstructionWithOffset {
        private final AMD64XMMRegister source;
        rip_movdl_10917(int startPosition, int endPosition, AMD64XMMRegister source, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.source = source;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_movdl(offsetAsInt(), source);
        }
    }

    class rip_movdq_10926 extends InstructionWithOffset {
        private final AMD64XMMRegister source;
        rip_movdq_10926(int startPosition, int endPosition, AMD64XMMRegister source, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.source = source;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_movdq(offsetAsInt(), source);
        }
    }

    class rip_movdl_10836 extends InstructionWithOffset {
        private final MMXRegister source;
        rip_movdl_10836(int startPosition, int endPosition, MMXRegister source, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.source = source;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_movdl(offsetAsInt(), source);
        }
    }

    class rip_movdq_10845 extends InstructionWithOffset {
        private final MMXRegister source;
        rip_movdq_10845(int startPosition, int endPosition, MMXRegister source, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.source = source;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_movdq(offsetAsInt(), source);
        }
    }

    class rip_movddup_6236 extends InstructionWithOffset {
        private final AMD64XMMRegister destination;
        rip_movddup_6236(int startPosition, int endPosition, AMD64XMMRegister destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_movddup(destination, offsetAsInt());
        }
    }

    class rip_movdqa_10800 extends InstructionWithOffset {
        private final AMD64XMMRegister destination;
        rip_movdqa_10800(int startPosition, int endPosition, AMD64XMMRegister destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_movdqa(destination, offsetAsInt());
        }
    }

    class rip_movdqa_10935 extends InstructionWithOffset {
        private final AMD64XMMRegister source;
        rip_movdqa_10935(int startPosition, int endPosition, AMD64XMMRegister source, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.source = source;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_movdqa(offsetAsInt(), source);
        }
    }

    class rip_movdqu_10818 extends InstructionWithOffset {
        private final AMD64XMMRegister destination;
        rip_movdqu_10818(int startPosition, int endPosition, AMD64XMMRegister destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_movdqu(destination, offsetAsInt());
        }
    }

    class rip_movdqu_11007 extends InstructionWithOffset {
        private final AMD64XMMRegister source;
        rip_movdqu_11007(int startPosition, int endPosition, AMD64XMMRegister source, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.source = source;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_movdqu(offsetAsInt(), source);
        }
    }

    class rip_movhpd_6134 extends InstructionWithOffset {
        private final AMD64XMMRegister destination;
        rip_movhpd_6134(int startPosition, int endPosition, AMD64XMMRegister destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_movhpd(destination, offsetAsInt());
        }
    }

    class rip_movhpd_6158 extends InstructionWithOffset {
        private final AMD64XMMRegister source;
        rip_movhpd_6158(int startPosition, int endPosition, AMD64XMMRegister source, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.source = source;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_movhpd(offsetAsInt(), source);
        }
    }

    class rip_movhps_5990 extends InstructionWithOffset {
        private final AMD64XMMRegister source;
        rip_movhps_5990(int startPosition, int endPosition, AMD64XMMRegister source, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.source = source;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_movhps(offsetAsInt(), source);
        }
    }

    class rip_movlpd_6050 extends InstructionWithOffset {
        private final AMD64XMMRegister destination;
        rip_movlpd_6050(int startPosition, int endPosition, AMD64XMMRegister destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_movlpd(destination, offsetAsInt());
        }
    }

    class rip_movlpd_6074 extends InstructionWithOffset {
        private final AMD64XMMRegister source;
        rip_movlpd_6074(int startPosition, int endPosition, AMD64XMMRegister source, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.source = source;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_movlpd(offsetAsInt(), source);
        }
    }

    class rip_movlps_5927 extends InstructionWithOffset {
        private final AMD64XMMRegister source;
        rip_movlps_5927(int startPosition, int endPosition, AMD64XMMRegister source, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.source = source;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_movlps(offsetAsInt(), source);
        }
    }

    class rip_movnti_8021 extends InstructionWithOffset {
        private final AMD64GeneralRegister32 source;
        rip_movnti_8021(int startPosition, int endPosition, AMD64GeneralRegister32 source, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.source = source;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_movnti(offsetAsInt(), source);
        }
    }

    class rip_movnti_8029 extends InstructionWithOffset {
        private final AMD64GeneralRegister64 source;
        rip_movnti_8029(int startPosition, int endPosition, AMD64GeneralRegister64 source, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.source = source;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_movnti(offsetAsInt(), source);
        }
    }

    class rip_movntpd_9543 extends InstructionWithOffset {
        private final AMD64XMMRegister source;
        rip_movntpd_9543(int startPosition, int endPosition, AMD64XMMRegister source, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.source = source;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_movntpd(offsetAsInt(), source);
        }
    }

    class rip_movntps_9384 extends InstructionWithOffset {
        private final AMD64XMMRegister source;
        rip_movntps_9384(int startPosition, int endPosition, AMD64XMMRegister source, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.source = source;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_movntps(offsetAsInt(), source);
        }
    }

    class rip_movntq_8610 extends InstructionWithOffset {
        private final MMXRegister source;
        rip_movntq_8610(int startPosition, int endPosition, MMXRegister source, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.source = source;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_movntq(offsetAsInt(), source);
        }
    }

    class rip_movq_10989 extends InstructionWithOffset {
        private final AMD64XMMRegister destination;
        rip_movq_10989(int startPosition, int endPosition, AMD64XMMRegister destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_movq(destination, offsetAsInt());
        }
    }

    class rip_movq_10647 extends InstructionWithOffset {
        private final MMXRegister destination;
        rip_movq_10647(int startPosition, int endPosition, MMXRegister destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_movq(destination, offsetAsInt());
        }
    }

    class rip_movq_8421 extends InstructionWithOffset {
        private final AMD64XMMRegister source;
        rip_movq_8421(int startPosition, int endPosition, AMD64XMMRegister source, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.source = source;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_movq(offsetAsInt(), source);
        }
    }

    class rip_movq_10854 extends InstructionWithOffset {
        private final MMXRegister source;
        rip_movq_10854(int startPosition, int endPosition, MMXRegister source, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.source = source;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_movq(offsetAsInt(), source);
        }
    }

    class rip_movsd_6182 extends InstructionWithOffset {
        private final AMD64XMMRegister destination;
        rip_movsd_6182(int startPosition, int endPosition, AMD64XMMRegister destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_movsd(destination, offsetAsInt());
        }
    }

    class rip_movsd_6218 extends InstructionWithOffset {
        private final AMD64XMMRegister source;
        rip_movsd_6218(int startPosition, int endPosition, AMD64XMMRegister source, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.source = source;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_movsd(offsetAsInt(), source);
        }
    }

    class rip_movshdup_6326 extends InstructionWithOffset {
        private final AMD64XMMRegister destination;
        rip_movshdup_6326(int startPosition, int endPosition, AMD64XMMRegister destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_movshdup(destination, offsetAsInt());
        }
    }

    class rip_movsldup_6308 extends InstructionWithOffset {
        private final AMD64XMMRegister destination;
        rip_movsldup_6308(int startPosition, int endPosition, AMD64XMMRegister destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_movsldup(destination, offsetAsInt());
        }
    }

    class rip_movss_6254 extends InstructionWithOffset {
        private final AMD64XMMRegister destination;
        rip_movss_6254(int startPosition, int endPosition, AMD64XMMRegister destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_movss(destination, offsetAsInt());
        }
    }

    class rip_movss_6290 extends InstructionWithOffset {
        private final AMD64XMMRegister source;
        rip_movss_6290(int startPosition, int endPosition, AMD64XMMRegister source, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.source = source;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_movss(offsetAsInt(), source);
        }
    }

    class rip_movsxb_11700 extends InstructionWithOffset {
        private final AMD64GeneralRegister16 destination;
        rip_movsxb_11700(int startPosition, int endPosition, AMD64GeneralRegister16 destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_movsxb(destination, offsetAsInt());
        }
    }

    class rip_movsxb_11682 extends InstructionWithOffset {
        private final AMD64GeneralRegister32 destination;
        rip_movsxb_11682(int startPosition, int endPosition, AMD64GeneralRegister32 destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_movsxb(destination, offsetAsInt());
        }
    }

    class rip_movsxb_11691 extends InstructionWithOffset {
        private final AMD64GeneralRegister64 destination;
        rip_movsxb_11691(int startPosition, int endPosition, AMD64GeneralRegister64 destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_movsxb(destination, offsetAsInt());
        }
    }

    class rip_movsxd_462 extends InstructionWithOffset {
        private final AMD64GeneralRegister64 destination;
        rip_movsxd_462(int startPosition, int endPosition, AMD64GeneralRegister64 destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_movsxd(destination, offsetAsInt());
        }
    }

    class rip_movsxw_11709 extends InstructionWithOffset {
        private final AMD64GeneralRegister32 destination;
        rip_movsxw_11709(int startPosition, int endPosition, AMD64GeneralRegister32 destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_movsxw(destination, offsetAsInt());
        }
    }

    class rip_movsxw_11718 extends InstructionWithOffset {
        private final AMD64GeneralRegister64 destination;
        rip_movsxw_11718(int startPosition, int endPosition, AMD64GeneralRegister64 destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_movsxw(destination, offsetAsInt());
        }
    }

    class rip_movupd_6014 extends InstructionWithOffset {
        private final AMD64XMMRegister destination;
        rip_movupd_6014(int startPosition, int endPosition, AMD64XMMRegister destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_movupd(destination, offsetAsInt());
        }
    }

    class rip_movupd_6032 extends InstructionWithOffset {
        private final AMD64XMMRegister source;
        rip_movupd_6032(int startPosition, int endPosition, AMD64XMMRegister source, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.source = source;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_movupd(offsetAsInt(), source);
        }
    }

    class rip_movups_5888 extends InstructionWithOffset {
        private final AMD64XMMRegister destination;
        rip_movups_5888(int startPosition, int endPosition, AMD64XMMRegister destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_movups(destination, offsetAsInt());
        }
    }

    class rip_movups_5906 extends InstructionWithOffset {
        private final AMD64XMMRegister source;
        rip_movups_5906(int startPosition, int endPosition, AMD64XMMRegister source, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.source = source;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_movups(offsetAsInt(), source);
        }
    }

    class rip_movzxb_7922 extends InstructionWithOffset {
        private final AMD64GeneralRegister16 destination;
        rip_movzxb_7922(int startPosition, int endPosition, AMD64GeneralRegister16 destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_movzxb(destination, offsetAsInt());
        }
    }

    class rip_movzxb_7904 extends InstructionWithOffset {
        private final AMD64GeneralRegister32 destination;
        rip_movzxb_7904(int startPosition, int endPosition, AMD64GeneralRegister32 destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_movzxb(destination, offsetAsInt());
        }
    }

    class rip_movzxb_7913 extends InstructionWithOffset {
        private final AMD64GeneralRegister64 destination;
        rip_movzxb_7913(int startPosition, int endPosition, AMD64GeneralRegister64 destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_movzxb(destination, offsetAsInt());
        }
    }

    class rip_movzxd_471 extends InstructionWithOffset {
        private final AMD64GeneralRegister64 destination;
        rip_movzxd_471(int startPosition, int endPosition, AMD64GeneralRegister64 destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_movzxd(destination, offsetAsInt());
        }
    }

    class rip_movzxw_7931 extends InstructionWithOffset {
        private final AMD64GeneralRegister32 destination;
        rip_movzxw_7931(int startPosition, int endPosition, AMD64GeneralRegister32 destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_movzxw(destination, offsetAsInt());
        }
    }

    class rip_movzxw_7940 extends InstructionWithOffset {
        private final AMD64GeneralRegister64 destination;
        rip_movzxw_7940(int startPosition, int endPosition, AMD64GeneralRegister64 destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_movzxw(destination, offsetAsInt());
        }
    }

    class rip_mulb___AL_2700 extends InstructionWithOffset {
        rip_mulb___AL_2700(int startPosition, int endPosition, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_mulb___AL(offsetAsInt());
        }
    }

    class rip_mull_2916 extends InstructionWithOffset {
        rip_mull_2916(int startPosition, int endPosition, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_mull(offsetAsInt());
        }
    }

    class rip_mulq_2988 extends InstructionWithOffset {
        rip_mulq_2988(int startPosition, int endPosition, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_mulq(offsetAsInt());
        }
    }

    class rip_mulw_3060 extends InstructionWithOffset {
        rip_mulw_3060(int startPosition, int endPosition, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_mulw(offsetAsInt());
        }
    }

    class rip_mulpd_10125 extends InstructionWithOffset {
        private final AMD64XMMRegister destination;
        rip_mulpd_10125(int startPosition, int endPosition, AMD64XMMRegister destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_mulpd(destination, offsetAsInt());
        }
    }

    class rip_mulps_9981 extends InstructionWithOffset {
        private final AMD64XMMRegister destination;
        rip_mulps_9981(int startPosition, int endPosition, AMD64XMMRegister destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_mulps(destination, offsetAsInt());
        }
    }

    class rip_mulsd_10269 extends InstructionWithOffset {
        private final AMD64XMMRegister destination;
        rip_mulsd_10269(int startPosition, int endPosition, AMD64XMMRegister destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_mulsd(destination, offsetAsInt());
        }
    }

    class rip_mulss_10395 extends InstructionWithOffset {
        private final AMD64XMMRegister destination;
        rip_mulss_10395(int startPosition, int endPosition, AMD64XMMRegister destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_mulss(destination, offsetAsInt());
        }
    }

    class rip_mvntdq_8760 extends InstructionWithOffset {
        private final AMD64XMMRegister source;
        rip_mvntdq_8760(int startPosition, int endPosition, AMD64XMMRegister source, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.source = source;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_mvntdq(offsetAsInt(), source);
        }
    }

    class rip_negb_2696 extends InstructionWithOffset {
        rip_negb_2696(int startPosition, int endPosition, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_negb(offsetAsInt());
        }
    }

    class rip_negl_2912 extends InstructionWithOffset {
        rip_negl_2912(int startPosition, int endPosition, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_negl(offsetAsInt());
        }
    }

    class rip_negq_2984 extends InstructionWithOffset {
        rip_negq_2984(int startPosition, int endPosition, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_negq(offsetAsInt());
        }
    }

    class rip_negw_3056 extends InstructionWithOffset {
        rip_negw_3056(int startPosition, int endPosition, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_negw(offsetAsInt());
        }
    }

    class rip_notb_2692 extends InstructionWithOffset {
        rip_notb_2692(int startPosition, int endPosition, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_notb(offsetAsInt());
        }
    }

    class rip_notl_2908 extends InstructionWithOffset {
        rip_notl_2908(int startPosition, int endPosition, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_notl(offsetAsInt());
        }
    }

    class rip_notq_2980 extends InstructionWithOffset {
        rip_notq_2980(int startPosition, int endPosition, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_notq(offsetAsInt());
        }
    }

    class rip_notw_3052 extends InstructionWithOffset {
        rip_notw_3052(int startPosition, int endPosition, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_notw(offsetAsInt());
        }
    }

    class rip_or_3215 extends InstructionWithOffset {
        private final AMD64GeneralRegister16 destination;
        rip_or_3215(int startPosition, int endPosition, AMD64GeneralRegister16 destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_or(destination, offsetAsInt());
        }
    }

    class rip_or_3197 extends InstructionWithOffset {
        private final AMD64GeneralRegister32 destination;
        rip_or_3197(int startPosition, int endPosition, AMD64GeneralRegister32 destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_or(destination, offsetAsInt());
        }
    }

    class rip_or_3206 extends InstructionWithOffset {
        private final AMD64GeneralRegister64 destination;
        rip_or_3206(int startPosition, int endPosition, AMD64GeneralRegister64 destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_or(destination, offsetAsInt());
        }
    }

    class rip_or_3170 extends InstructionWithOffset {
        private final AMD64GeneralRegister8 destination;
        rip_or_3170(int startPosition, int endPosition, AMD64GeneralRegister8 destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_or(destination, offsetAsInt());
        }
    }

    class rip_orb_504 extends InstructionWithOffset {
        private final byte imm8;
        rip_orb_504(int startPosition, int endPosition, byte imm8, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.imm8 = imm8;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_orb(offsetAsInt(), imm8);
        }
    }

    class rip_orl_936 extends InstructionWithOffset {
        private final byte imm8;
        rip_orl_936(int startPosition, int endPosition, byte imm8, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.imm8 = imm8;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_orl(offsetAsInt(), imm8);
        }
    }

    class rip_orq_1008 extends InstructionWithOffset {
        private final byte imm8;
        rip_orq_1008(int startPosition, int endPosition, byte imm8, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.imm8 = imm8;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_orq(offsetAsInt(), imm8);
        }
    }

    class rip_orw_1080 extends InstructionWithOffset {
        private final byte imm8;
        rip_orw_1080(int startPosition, int endPosition, byte imm8, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.imm8 = imm8;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_orw(offsetAsInt(), imm8);
        }
    }

    class rip_or_3161 extends InstructionWithOffset {
        private final AMD64GeneralRegister16 source;
        rip_or_3161(int startPosition, int endPosition, AMD64GeneralRegister16 source, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.source = source;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_or(offsetAsInt(), source);
        }
    }

    class rip_or_3143 extends InstructionWithOffset {
        private final AMD64GeneralRegister32 source;
        rip_or_3143(int startPosition, int endPosition, AMD64GeneralRegister32 source, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.source = source;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_or(offsetAsInt(), source);
        }
    }

    class rip_or_3152 extends InstructionWithOffset {
        private final AMD64GeneralRegister64 source;
        rip_or_3152(int startPosition, int endPosition, AMD64GeneralRegister64 source, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.source = source;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_or(offsetAsInt(), source);
        }
    }

    class rip_or_3116 extends InstructionWithOffset {
        private final AMD64GeneralRegister8 source;
        rip_or_3116(int startPosition, int endPosition, AMD64GeneralRegister8 source, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.source = source;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_or(offsetAsInt(), source);
        }
    }

    class rip_orl_720 extends InstructionWithOffset {
        private final int imm32;
        rip_orl_720(int startPosition, int endPosition, int imm32, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.imm32 = imm32;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_orl(offsetAsInt(), imm32);
        }
    }

    class rip_orq_792 extends InstructionWithOffset {
        private final int imm32;
        rip_orq_792(int startPosition, int endPosition, int imm32, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.imm32 = imm32;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_orq(offsetAsInt(), imm32);
        }
    }

    class rip_orw_864 extends InstructionWithOffset {
        private final short imm16;
        rip_orw_864(int startPosition, int endPosition, short imm16, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.imm16 = imm16;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_orw(offsetAsInt(), imm16);
        }
    }

    class rip_orpd_6770 extends InstructionWithOffset {
        private final AMD64XMMRegister destination;
        rip_orpd_6770(int startPosition, int endPosition, AMD64XMMRegister destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_orpd(destination, offsetAsInt());
        }
    }

    class rip_orps_6677 extends InstructionWithOffset {
        private final AMD64XMMRegister destination;
        rip_orps_6677(int startPosition, int endPosition, AMD64XMMRegister destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_orps(destination, offsetAsInt());
        }
    }

    class rip_packssdw_10728 extends InstructionWithOffset {
        private final AMD64XMMRegister destination;
        rip_packssdw_10728(int startPosition, int endPosition, AMD64XMMRegister destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_packssdw(destination, offsetAsInt());
        }
    }

    class rip_packssdw_10602 extends InstructionWithOffset {
        private final MMXRegister destination;
        rip_packssdw_10602(int startPosition, int endPosition, MMXRegister destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_packssdw(destination, offsetAsInt());
        }
    }

    class rip_packsswb_7148 extends InstructionWithOffset {
        private final AMD64XMMRegister destination;
        rip_packsswb_7148(int startPosition, int endPosition, AMD64XMMRegister destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_packsswb(destination, offsetAsInt());
        }
    }

    class rip_packsswb_6959 extends InstructionWithOffset {
        private final MMXRegister destination;
        rip_packsswb_6959(int startPosition, int endPosition, MMXRegister destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_packsswb(destination, offsetAsInt());
        }
    }

    class rip_packuswb_7220 extends InstructionWithOffset {
        private final AMD64XMMRegister destination;
        rip_packuswb_7220(int startPosition, int endPosition, AMD64XMMRegister destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_packuswb(destination, offsetAsInt());
        }
    }

    class rip_packuswb_7067 extends InstructionWithOffset {
        private final MMXRegister destination;
        rip_packuswb_7067(int startPosition, int endPosition, MMXRegister destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_packuswb(destination, offsetAsInt());
        }
    }

    class rip_paddb_12710 extends InstructionWithOffset {
        private final AMD64XMMRegister destination;
        rip_paddb_12710(int startPosition, int endPosition, AMD64XMMRegister destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_paddb(destination, offsetAsInt());
        }
    }

    class rip_paddb_12557 extends InstructionWithOffset {
        private final MMXRegister destination;
        rip_paddb_12557(int startPosition, int endPosition, MMXRegister destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_paddb(destination, offsetAsInt());
        }
    }

    class rip_paddd_12746 extends InstructionWithOffset {
        private final AMD64XMMRegister destination;
        rip_paddd_12746(int startPosition, int endPosition, AMD64XMMRegister destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_paddd(destination, offsetAsInt());
        }
    }

    class rip_paddd_12611 extends InstructionWithOffset {
        private final MMXRegister destination;
        rip_paddd_12611(int startPosition, int endPosition, MMXRegister destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_paddd(destination, offsetAsInt());
        }
    }

    class rip_paddq_8385 extends InstructionWithOffset {
        private final AMD64XMMRegister destination;
        rip_paddq_8385(int startPosition, int endPosition, AMD64XMMRegister destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_paddq(destination, offsetAsInt());
        }
    }

    class rip_paddq_8256 extends InstructionWithOffset {
        private final MMXRegister destination;
        rip_paddq_8256(int startPosition, int endPosition, MMXRegister destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_paddq(destination, offsetAsInt());
        }
    }

    class rip_paddsb_12377 extends InstructionWithOffset {
        private final AMD64XMMRegister destination;
        rip_paddsb_12377(int startPosition, int endPosition, AMD64XMMRegister destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_paddsb(destination, offsetAsInt());
        }
    }

    class rip_paddsb_12197 extends InstructionWithOffset {
        private final MMXRegister destination;
        rip_paddsb_12197(int startPosition, int endPosition, MMXRegister destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_paddsb(destination, offsetAsInt());
        }
    }

    class rip_paddsw_12395 extends InstructionWithOffset {
        private final AMD64XMMRegister destination;
        rip_paddsw_12395(int startPosition, int endPosition, AMD64XMMRegister destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_paddsw(destination, offsetAsInt());
        }
    }

    class rip_paddsw_12224 extends InstructionWithOffset {
        private final MMXRegister destination;
        rip_paddsw_12224(int startPosition, int endPosition, MMXRegister destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_paddsw(destination, offsetAsInt());
        }
    }

    class rip_paddusb_12017 extends InstructionWithOffset {
        private final AMD64XMMRegister destination;
        rip_paddusb_12017(int startPosition, int endPosition, AMD64XMMRegister destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_paddusb(destination, offsetAsInt());
        }
    }

    class rip_paddusb_11837 extends InstructionWithOffset {
        private final MMXRegister destination;
        rip_paddusb_11837(int startPosition, int endPosition, MMXRegister destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_paddusb(destination, offsetAsInt());
        }
    }

    class rip_paddusw_12035 extends InstructionWithOffset {
        private final AMD64XMMRegister destination;
        rip_paddusw_12035(int startPosition, int endPosition, AMD64XMMRegister destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_paddusw(destination, offsetAsInt());
        }
    }

    class rip_paddusw_11864 extends InstructionWithOffset {
        private final MMXRegister destination;
        rip_paddusw_11864(int startPosition, int endPosition, MMXRegister destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_paddusw(destination, offsetAsInt());
        }
    }

    class rip_paddw_12728 extends InstructionWithOffset {
        private final AMD64XMMRegister destination;
        rip_paddw_12728(int startPosition, int endPosition, AMD64XMMRegister destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_paddw(destination, offsetAsInt());
        }
    }

    class rip_paddw_12584 extends InstructionWithOffset {
        private final MMXRegister destination;
        rip_paddw_12584(int startPosition, int endPosition, MMXRegister destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_paddw(destination, offsetAsInt());
        }
    }

    class rip_pand_11999 extends InstructionWithOffset {
        private final AMD64XMMRegister destination;
        rip_pand_11999(int startPosition, int endPosition, AMD64XMMRegister destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_pand(destination, offsetAsInt());
        }
    }

    class rip_pand_11810 extends InstructionWithOffset {
        private final MMXRegister destination;
        rip_pand_11810(int startPosition, int endPosition, MMXRegister destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_pand(destination, offsetAsInt());
        }
    }

    class rip_pandn_12071 extends InstructionWithOffset {
        private final AMD64XMMRegister destination;
        rip_pandn_12071(int startPosition, int endPosition, AMD64XMMRegister destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_pandn(destination, offsetAsInt());
        }
    }

    class rip_pandn_11918 extends InstructionWithOffset {
        private final MMXRegister destination;
        rip_pandn_11918(int startPosition, int endPosition, MMXRegister destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_pandn(destination, offsetAsInt());
        }
    }

    class rip_pavgb_8634 extends InstructionWithOffset {
        private final AMD64XMMRegister destination;
        rip_pavgb_8634(int startPosition, int endPosition, AMD64XMMRegister destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_pavgb(destination, offsetAsInt());
        }
    }

    class rip_pavgb_8448 extends InstructionWithOffset {
        private final MMXRegister destination;
        rip_pavgb_8448(int startPosition, int endPosition, MMXRegister destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_pavgb(destination, offsetAsInt());
        }
    }

    class rip_pavgw_8688 extends InstructionWithOffset {
        private final AMD64XMMRegister destination;
        rip_pavgw_8688(int startPosition, int endPosition, AMD64XMMRegister destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_pavgw(destination, offsetAsInt());
        }
    }

    class rip_pavgw_8529 extends InstructionWithOffset {
        private final MMXRegister destination;
        rip_pavgw_8529(int startPosition, int endPosition, MMXRegister destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_pavgw(destination, offsetAsInt());
        }
    }

    class rip_pcmpeqb_7421 extends InstructionWithOffset {
        private final AMD64XMMRegister destination;
        rip_pcmpeqb_7421(int startPosition, int endPosition, AMD64XMMRegister destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_pcmpeqb(destination, offsetAsInt());
        }
    }

    class rip_pcmpeqb_7289 extends InstructionWithOffset {
        private final MMXRegister destination;
        rip_pcmpeqb_7289(int startPosition, int endPosition, MMXRegister destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_pcmpeqb(destination, offsetAsInt());
        }
    }

    class rip_pcmpeqd_7457 extends InstructionWithOffset {
        private final AMD64XMMRegister destination;
        rip_pcmpeqd_7457(int startPosition, int endPosition, AMD64XMMRegister destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_pcmpeqd(destination, offsetAsInt());
        }
    }

    class rip_pcmpeqd_7343 extends InstructionWithOffset {
        private final MMXRegister destination;
        rip_pcmpeqd_7343(int startPosition, int endPosition, MMXRegister destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_pcmpeqd(destination, offsetAsInt());
        }
    }

    class rip_pcmpeqw_7439 extends InstructionWithOffset {
        private final AMD64XMMRegister destination;
        rip_pcmpeqw_7439(int startPosition, int endPosition, AMD64XMMRegister destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_pcmpeqw(destination, offsetAsInt());
        }
    }

    class rip_pcmpeqw_7316 extends InstructionWithOffset {
        private final MMXRegister destination;
        rip_pcmpeqw_7316(int startPosition, int endPosition, MMXRegister destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_pcmpeqw(destination, offsetAsInt());
        }
    }

    class rip_pcmpgtb_7166 extends InstructionWithOffset {
        private final AMD64XMMRegister destination;
        rip_pcmpgtb_7166(int startPosition, int endPosition, AMD64XMMRegister destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_pcmpgtb(destination, offsetAsInt());
        }
    }

    class rip_pcmpgtb_6986 extends InstructionWithOffset {
        private final MMXRegister destination;
        rip_pcmpgtb_6986(int startPosition, int endPosition, MMXRegister destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_pcmpgtb(destination, offsetAsInt());
        }
    }

    class rip_pcmpgtd_7202 extends InstructionWithOffset {
        private final AMD64XMMRegister destination;
        rip_pcmpgtd_7202(int startPosition, int endPosition, AMD64XMMRegister destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_pcmpgtd(destination, offsetAsInt());
        }
    }

    class rip_pcmpgtd_7040 extends InstructionWithOffset {
        private final MMXRegister destination;
        rip_pcmpgtd_7040(int startPosition, int endPosition, MMXRegister destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_pcmpgtd(destination, offsetAsInt());
        }
    }

    class rip_pcmpgtw_7184 extends InstructionWithOffset {
        private final AMD64XMMRegister destination;
        rip_pcmpgtw_7184(int startPosition, int endPosition, AMD64XMMRegister destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_pcmpgtw(destination, offsetAsInt());
        }
    }

    class rip_pcmpgtw_7013 extends InstructionWithOffset {
        private final MMXRegister destination;
        rip_pcmpgtw_7013(int startPosition, int endPosition, MMXRegister destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_pcmpgtw(destination, offsetAsInt());
        }
    }

    class rip_pinsrw_8109 extends InstructionWithOffset {
        private final AMD64XMMRegister destination;
        private final byte imm8;
        rip_pinsrw_8109(int startPosition, int endPosition, AMD64XMMRegister destination, byte imm8, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
            this.imm8 = imm8;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_pinsrw(destination, offsetAsInt(), imm8);
        }
    }

    class rip_pinsrw_8037 extends InstructionWithOffset {
        private final MMXRegister destination;
        private final byte imm8;
        rip_pinsrw_8037(int startPosition, int endPosition, MMXRegister destination, byte imm8, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
            this.imm8 = imm8;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_pinsrw(destination, offsetAsInt(), imm8);
        }
    }

    class rip_pmaddwd_9057 extends InstructionWithOffset {
        private final AMD64XMMRegister destination;
        rip_pmaddwd_9057(int startPosition, int endPosition, AMD64XMMRegister destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_pmaddwd(destination, offsetAsInt());
        }
    }

    class rip_pmaddwd_8928 extends InstructionWithOffset {
        private final MMXRegister destination;
        rip_pmaddwd_8928(int startPosition, int endPosition, MMXRegister destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_pmaddwd(destination, offsetAsInt());
        }
    }

    class rip_pmaxsw_12413 extends InstructionWithOffset {
        private final AMD64XMMRegister destination;
        rip_pmaxsw_12413(int startPosition, int endPosition, AMD64XMMRegister destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_pmaxsw(destination, offsetAsInt());
        }
    }

    class rip_pmaxsw_12251 extends InstructionWithOffset {
        private final MMXRegister destination;
        rip_pmaxsw_12251(int startPosition, int endPosition, MMXRegister destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_pmaxsw(destination, offsetAsInt());
        }
    }

    class rip_pmaxub_12053 extends InstructionWithOffset {
        private final AMD64XMMRegister destination;
        rip_pmaxub_12053(int startPosition, int endPosition, AMD64XMMRegister destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_pmaxub(destination, offsetAsInt());
        }
    }

    class rip_pmaxub_11891 extends InstructionWithOffset {
        private final MMXRegister destination;
        rip_pmaxub_11891(int startPosition, int endPosition, MMXRegister destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_pmaxub(destination, offsetAsInt());
        }
    }

    class rip_pminsw_12341 extends InstructionWithOffset {
        private final AMD64XMMRegister destination;
        rip_pminsw_12341(int startPosition, int endPosition, AMD64XMMRegister destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_pminsw(destination, offsetAsInt());
        }
    }

    class rip_pminsw_12143 extends InstructionWithOffset {
        private final MMXRegister destination;
        rip_pminsw_12143(int startPosition, int endPosition, MMXRegister destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_pminsw(destination, offsetAsInt());
        }
    }

    class rip_pminub_11981 extends InstructionWithOffset {
        private final AMD64XMMRegister destination;
        rip_pminub_11981(int startPosition, int endPosition, AMD64XMMRegister destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_pminub(destination, offsetAsInt());
        }
    }

    class rip_pminub_11783 extends InstructionWithOffset {
        private final MMXRegister destination;
        rip_pminub_11783(int startPosition, int endPosition, MMXRegister destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_pminub(destination, offsetAsInt());
        }
    }

    class rip_pmulhuw_8706 extends InstructionWithOffset {
        private final AMD64XMMRegister destination;
        rip_pmulhuw_8706(int startPosition, int endPosition, AMD64XMMRegister destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_pmulhuw(destination, offsetAsInt());
        }
    }

    class rip_pmulhuw_8556 extends InstructionWithOffset {
        private final MMXRegister destination;
        rip_pmulhuw_8556(int startPosition, int endPosition, MMXRegister destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_pmulhuw(destination, offsetAsInt());
        }
    }

    class rip_pmulhw_8724 extends InstructionWithOffset {
        private final AMD64XMMRegister destination;
        rip_pmulhw_8724(int startPosition, int endPosition, AMD64XMMRegister destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_pmulhw(destination, offsetAsInt());
        }
    }

    class rip_pmulhw_8583 extends InstructionWithOffset {
        private final MMXRegister destination;
        rip_pmulhw_8583(int startPosition, int endPosition, MMXRegister destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_pmulhw(destination, offsetAsInt());
        }
    }

    class rip_pmullw_8403 extends InstructionWithOffset {
        private final AMD64XMMRegister destination;
        rip_pmullw_8403(int startPosition, int endPosition, AMD64XMMRegister destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_pmullw(destination, offsetAsInt());
        }
    }

    class rip_pmullw_8283 extends InstructionWithOffset {
        private final MMXRegister destination;
        rip_pmullw_8283(int startPosition, int endPosition, MMXRegister destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_pmullw(destination, offsetAsInt());
        }
    }

    class rip_pmuludq_9039 extends InstructionWithOffset {
        private final AMD64XMMRegister destination;
        rip_pmuludq_9039(int startPosition, int endPosition, AMD64XMMRegister destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_pmuludq(destination, offsetAsInt());
        }
    }

    class rip_pmuludq_8901 extends InstructionWithOffset {
        private final MMXRegister destination;
        rip_pmuludq_8901(int startPosition, int endPosition, MMXRegister destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_pmuludq(destination, offsetAsInt());
        }
    }

    class rip_pop_3842 extends InstructionWithOffset {
        rip_pop_3842(int startPosition, int endPosition, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_pop(offsetAsInt());
        }
    }

    class rip_por_12359 extends InstructionWithOffset {
        private final AMD64XMMRegister destination;
        rip_por_12359(int startPosition, int endPosition, AMD64XMMRegister destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_por(destination, offsetAsInt());
        }
    }

    class rip_por_12170 extends InstructionWithOffset {
        private final MMXRegister destination;
        rip_por_12170(int startPosition, int endPosition, MMXRegister destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_por(destination, offsetAsInt());
        }
    }

    class rip_prefetch_9129 extends InstructionWithOffset {
        rip_prefetch_9129(int startPosition, int endPosition, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_prefetch(offsetAsInt());
        }
    }

    class rip_prefetchnta_9204 extends InstructionWithOffset {
        rip_prefetchnta_9204(int startPosition, int endPosition, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_prefetchnta(offsetAsInt());
        }
    }

    class rip_prefetcht0_9208 extends InstructionWithOffset {
        rip_prefetcht0_9208(int startPosition, int endPosition, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_prefetcht0(offsetAsInt());
        }
    }

    class rip_prefetcht1_9212 extends InstructionWithOffset {
        rip_prefetcht1_9212(int startPosition, int endPosition, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_prefetcht1(offsetAsInt());
        }
    }

    class rip_prefetcht2_9216 extends InstructionWithOffset {
        rip_prefetcht2_9216(int startPosition, int endPosition, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_prefetcht2(offsetAsInt());
        }
    }

    class rip_prefetchw_9133 extends InstructionWithOffset {
        rip_prefetchw_9133(int startPosition, int endPosition, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_prefetchw(offsetAsInt());
        }
    }

    class rip_psadbw_9075 extends InstructionWithOffset {
        private final AMD64XMMRegister destination;
        rip_psadbw_9075(int startPosition, int endPosition, AMD64XMMRegister destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_psadbw(destination, offsetAsInt());
        }
    }

    class rip_psadbw_8955 extends InstructionWithOffset {
        private final MMXRegister destination;
        rip_psadbw_8955(int startPosition, int endPosition, MMXRegister destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_psadbw(destination, offsetAsInt());
        }
    }

    class rip_pshufd_7373 extends InstructionWithOffset {
        private final AMD64XMMRegister destination;
        private final byte imm8;
        rip_pshufd_7373(int startPosition, int endPosition, AMD64XMMRegister destination, byte imm8, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
            this.imm8 = imm8;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_pshufd(destination, offsetAsInt(), imm8);
        }
    }

    class rip_pshufhw_7493 extends InstructionWithOffset {
        private final AMD64XMMRegister destination;
        private final byte imm8;
        rip_pshufhw_7493(int startPosition, int endPosition, AMD64XMMRegister destination, byte imm8, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
            this.imm8 = imm8;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_pshufhw(destination, offsetAsInt(), imm8);
        }
    }

    class rip_pshuflw_7475 extends InstructionWithOffset {
        private final AMD64XMMRegister destination;
        private final byte imm8;
        rip_pshuflw_7475(int startPosition, int endPosition, AMD64XMMRegister destination, byte imm8, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
            this.imm8 = imm8;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_pshuflw(destination, offsetAsInt(), imm8);
        }
    }

    class rip_pshufw_7238 extends InstructionWithOffset {
        private final MMXRegister destination;
        private final byte imm8;
        rip_pshufw_7238(int startPosition, int endPosition, MMXRegister destination, byte imm8, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
            this.imm8 = imm8;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_pshufw(destination, offsetAsInt(), imm8);
        }
    }

    class rip_pslld_9003 extends InstructionWithOffset {
        private final AMD64XMMRegister destination;
        rip_pslld_9003(int startPosition, int endPosition, AMD64XMMRegister destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_pslld(destination, offsetAsInt());
        }
    }

    class rip_pslld_8847 extends InstructionWithOffset {
        private final MMXRegister destination;
        rip_pslld_8847(int startPosition, int endPosition, MMXRegister destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_pslld(destination, offsetAsInt());
        }
    }

    class rip_psllq_9021 extends InstructionWithOffset {
        private final AMD64XMMRegister destination;
        rip_psllq_9021(int startPosition, int endPosition, AMD64XMMRegister destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_psllq(destination, offsetAsInt());
        }
    }

    class rip_psllq_8874 extends InstructionWithOffset {
        private final MMXRegister destination;
        rip_psllq_8874(int startPosition, int endPosition, MMXRegister destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_psllq(destination, offsetAsInt());
        }
    }

    class rip_psllw_8985 extends InstructionWithOffset {
        private final AMD64XMMRegister destination;
        rip_psllw_8985(int startPosition, int endPosition, AMD64XMMRegister destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_psllw(destination, offsetAsInt());
        }
    }

    class rip_psllw_8820 extends InstructionWithOffset {
        private final MMXRegister destination;
        rip_psllw_8820(int startPosition, int endPosition, MMXRegister destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_psllw(destination, offsetAsInt());
        }
    }

    class rip_psrad_8670 extends InstructionWithOffset {
        private final AMD64XMMRegister destination;
        rip_psrad_8670(int startPosition, int endPosition, AMD64XMMRegister destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_psrad(destination, offsetAsInt());
        }
    }

    class rip_psrad_8502 extends InstructionWithOffset {
        private final MMXRegister destination;
        rip_psrad_8502(int startPosition, int endPosition, MMXRegister destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_psrad(destination, offsetAsInt());
        }
    }

    class rip_psraw_8652 extends InstructionWithOffset {
        private final AMD64XMMRegister destination;
        rip_psraw_8652(int startPosition, int endPosition, AMD64XMMRegister destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_psraw(destination, offsetAsInt());
        }
    }

    class rip_psraw_8475 extends InstructionWithOffset {
        private final MMXRegister destination;
        rip_psraw_8475(int startPosition, int endPosition, MMXRegister destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_psraw(destination, offsetAsInt());
        }
    }

    class rip_psrld_8349 extends InstructionWithOffset {
        private final AMD64XMMRegister destination;
        rip_psrld_8349(int startPosition, int endPosition, AMD64XMMRegister destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_psrld(destination, offsetAsInt());
        }
    }

    class rip_psrld_8202 extends InstructionWithOffset {
        private final MMXRegister destination;
        rip_psrld_8202(int startPosition, int endPosition, MMXRegister destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_psrld(destination, offsetAsInt());
        }
    }

    class rip_psrlq_8367 extends InstructionWithOffset {
        private final AMD64XMMRegister destination;
        rip_psrlq_8367(int startPosition, int endPosition, AMD64XMMRegister destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_psrlq(destination, offsetAsInt());
        }
    }

    class rip_psrlq_8229 extends InstructionWithOffset {
        private final MMXRegister destination;
        rip_psrlq_8229(int startPosition, int endPosition, MMXRegister destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_psrlq(destination, offsetAsInt());
        }
    }

    class rip_psrlw_8331 extends InstructionWithOffset {
        private final AMD64XMMRegister destination;
        rip_psrlw_8331(int startPosition, int endPosition, AMD64XMMRegister destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_psrlw(destination, offsetAsInt());
        }
    }

    class rip_psrlw_8175 extends InstructionWithOffset {
        private final MMXRegister destination;
        rip_psrlw_8175(int startPosition, int endPosition, MMXRegister destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_psrlw(destination, offsetAsInt());
        }
    }

    class rip_psubb_12638 extends InstructionWithOffset {
        private final AMD64XMMRegister destination;
        rip_psubb_12638(int startPosition, int endPosition, AMD64XMMRegister destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_psubb(destination, offsetAsInt());
        }
    }

    class rip_psubb_12449 extends InstructionWithOffset {
        private final MMXRegister destination;
        rip_psubb_12449(int startPosition, int endPosition, MMXRegister destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_psubb(destination, offsetAsInt());
        }
    }

    class rip_psubd_12674 extends InstructionWithOffset {
        private final AMD64XMMRegister destination;
        rip_psubd_12674(int startPosition, int endPosition, AMD64XMMRegister destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_psubd(destination, offsetAsInt());
        }
    }

    class rip_psubd_12503 extends InstructionWithOffset {
        private final MMXRegister destination;
        rip_psubd_12503(int startPosition, int endPosition, MMXRegister destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_psubd(destination, offsetAsInt());
        }
    }

    class rip_psubq_12692 extends InstructionWithOffset {
        private final AMD64XMMRegister destination;
        rip_psubq_12692(int startPosition, int endPosition, AMD64XMMRegister destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_psubq(destination, offsetAsInt());
        }
    }

    class rip_psubq_12530 extends InstructionWithOffset {
        private final MMXRegister destination;
        rip_psubq_12530(int startPosition, int endPosition, MMXRegister destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_psubq(destination, offsetAsInt());
        }
    }

    class rip_psubsb_12305 extends InstructionWithOffset {
        private final AMD64XMMRegister destination;
        rip_psubsb_12305(int startPosition, int endPosition, AMD64XMMRegister destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_psubsb(destination, offsetAsInt());
        }
    }

    class rip_psubsb_12089 extends InstructionWithOffset {
        private final MMXRegister destination;
        rip_psubsb_12089(int startPosition, int endPosition, MMXRegister destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_psubsb(destination, offsetAsInt());
        }
    }

    class rip_psubsw_12323 extends InstructionWithOffset {
        private final AMD64XMMRegister destination;
        rip_psubsw_12323(int startPosition, int endPosition, AMD64XMMRegister destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_psubsw(destination, offsetAsInt());
        }
    }

    class rip_psubsw_12116 extends InstructionWithOffset {
        private final MMXRegister destination;
        rip_psubsw_12116(int startPosition, int endPosition, MMXRegister destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_psubsw(destination, offsetAsInt());
        }
    }

    class rip_psubusb_11945 extends InstructionWithOffset {
        private final AMD64XMMRegister destination;
        rip_psubusb_11945(int startPosition, int endPosition, AMD64XMMRegister destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_psubusb(destination, offsetAsInt());
        }
    }

    class rip_psubusb_11729 extends InstructionWithOffset {
        private final MMXRegister destination;
        rip_psubusb_11729(int startPosition, int endPosition, MMXRegister destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_psubusb(destination, offsetAsInt());
        }
    }

    class rip_psubusw_11963 extends InstructionWithOffset {
        private final AMD64XMMRegister destination;
        rip_psubusw_11963(int startPosition, int endPosition, AMD64XMMRegister destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_psubusw(destination, offsetAsInt());
        }
    }

    class rip_psubusw_11756 extends InstructionWithOffset {
        private final MMXRegister destination;
        rip_psubusw_11756(int startPosition, int endPosition, MMXRegister destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_psubusw(destination, offsetAsInt());
        }
    }

    class rip_psubw_12656 extends InstructionWithOffset {
        private final AMD64XMMRegister destination;
        rip_psubw_12656(int startPosition, int endPosition, AMD64XMMRegister destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_psubw(destination, offsetAsInt());
        }
    }

    class rip_psubw_12476 extends InstructionWithOffset {
        private final MMXRegister destination;
        rip_psubw_12476(int startPosition, int endPosition, MMXRegister destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_psubw(destination, offsetAsInt());
        }
    }

    class rip_punpckhbw_10674 extends InstructionWithOffset {
        private final AMD64XMMRegister destination;
        rip_punpckhbw_10674(int startPosition, int endPosition, AMD64XMMRegister destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_punpckhbw(destination, offsetAsInt());
        }
    }

    class rip_punpckhbw_10521 extends InstructionWithOffset {
        private final MMXRegister destination;
        rip_punpckhbw_10521(int startPosition, int endPosition, MMXRegister destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_punpckhbw(destination, offsetAsInt());
        }
    }

    class rip_punpckhdq_10710 extends InstructionWithOffset {
        private final AMD64XMMRegister destination;
        rip_punpckhdq_10710(int startPosition, int endPosition, AMD64XMMRegister destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_punpckhdq(destination, offsetAsInt());
        }
    }

    class rip_punpckhdq_10575 extends InstructionWithOffset {
        private final MMXRegister destination;
        rip_punpckhdq_10575(int startPosition, int endPosition, MMXRegister destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_punpckhdq(destination, offsetAsInt());
        }
    }

    class rip_punpckhqdq_10764 extends InstructionWithOffset {
        private final AMD64XMMRegister destination;
        rip_punpckhqdq_10764(int startPosition, int endPosition, AMD64XMMRegister destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_punpckhqdq(destination, offsetAsInt());
        }
    }

    class rip_punpckhwd_10692 extends InstructionWithOffset {
        private final AMD64XMMRegister destination;
        rip_punpckhwd_10692(int startPosition, int endPosition, AMD64XMMRegister destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_punpckhwd(destination, offsetAsInt());
        }
    }

    class rip_punpckhwd_10548 extends InstructionWithOffset {
        private final MMXRegister destination;
        rip_punpckhwd_10548(int startPosition, int endPosition, MMXRegister destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_punpckhwd(destination, offsetAsInt());
        }
    }

    class rip_punpcklbw_7094 extends InstructionWithOffset {
        private final AMD64XMMRegister destination;
        rip_punpcklbw_7094(int startPosition, int endPosition, AMD64XMMRegister destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_punpcklbw(destination, offsetAsInt());
        }
    }

    class rip_punpcklbw_6878 extends InstructionWithOffset {
        private final MMXRegister destination;
        rip_punpcklbw_6878(int startPosition, int endPosition, MMXRegister destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_punpcklbw(destination, offsetAsInt());
        }
    }

    class rip_punpckldq_7130 extends InstructionWithOffset {
        private final AMD64XMMRegister destination;
        rip_punpckldq_7130(int startPosition, int endPosition, AMD64XMMRegister destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_punpckldq(destination, offsetAsInt());
        }
    }

    class rip_punpckldq_6932 extends InstructionWithOffset {
        private final MMXRegister destination;
        rip_punpckldq_6932(int startPosition, int endPosition, MMXRegister destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_punpckldq(destination, offsetAsInt());
        }
    }

    class rip_punpcklqdq_10746 extends InstructionWithOffset {
        private final AMD64XMMRegister destination;
        rip_punpcklqdq_10746(int startPosition, int endPosition, AMD64XMMRegister destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_punpcklqdq(destination, offsetAsInt());
        }
    }

    class rip_punpcklwd_7112 extends InstructionWithOffset {
        private final AMD64XMMRegister destination;
        rip_punpcklwd_7112(int startPosition, int endPosition, AMD64XMMRegister destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_punpcklwd(destination, offsetAsInt());
        }
    }

    class rip_punpcklwd_6905 extends InstructionWithOffset {
        private final MMXRegister destination;
        rip_punpcklwd_6905(int startPosition, int endPosition, MMXRegister destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_punpcklwd(destination, offsetAsInt());
        }
    }

    class rip_push_5440 extends InstructionWithOffset {
        rip_push_5440(int startPosition, int endPosition, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_push(offsetAsInt());
        }
    }

    class rip_pxor_12431 extends InstructionWithOffset {
        private final AMD64XMMRegister destination;
        rip_pxor_12431(int startPosition, int endPosition, AMD64XMMRegister destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_pxor(destination, offsetAsInt());
        }
    }

    class rip_pxor_12278 extends InstructionWithOffset {
        private final MMXRegister destination;
        rip_pxor_12278(int startPosition, int endPosition, MMXRegister destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_pxor(destination, offsetAsInt());
        }
    }

    class rip_rclb___1_1787 extends InstructionWithOffset {
        rip_rclb___1_1787(int startPosition, int endPosition, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_rclb___1(offsetAsInt());
        }
    }

    class rip_rcll___1_2003 extends InstructionWithOffset {
        rip_rcll___1_2003(int startPosition, int endPosition, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_rcll___1(offsetAsInt());
        }
    }

    class rip_rclq___1_2075 extends InstructionWithOffset {
        rip_rclq___1_2075(int startPosition, int endPosition, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_rclq___1(offsetAsInt());
        }
    }

    class rip_rclw___1_2147 extends InstructionWithOffset {
        rip_rclw___1_2147(int startPosition, int endPosition, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_rclw___1(offsetAsInt());
        }
    }

    class rip_rclb___CL_2219 extends InstructionWithOffset {
        rip_rclb___CL_2219(int startPosition, int endPosition, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_rclb___CL(offsetAsInt());
        }
    }

    class rip_rcll___CL_2435 extends InstructionWithOffset {
        rip_rcll___CL_2435(int startPosition, int endPosition, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_rcll___CL(offsetAsInt());
        }
    }

    class rip_rclq___CL_2507 extends InstructionWithOffset {
        rip_rclq___CL_2507(int startPosition, int endPosition, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_rclq___CL(offsetAsInt());
        }
    }

    class rip_rclw___CL_2579 extends InstructionWithOffset {
        rip_rclw___CL_2579(int startPosition, int endPosition, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_rclw___CL(offsetAsInt());
        }
    }

    class rip_rclb_1297 extends InstructionWithOffset {
        private final byte imm8;
        rip_rclb_1297(int startPosition, int endPosition, byte imm8, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.imm8 = imm8;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_rclb(offsetAsInt(), imm8);
        }
    }

    class rip_rcll_1513 extends InstructionWithOffset {
        private final byte imm8;
        rip_rcll_1513(int startPosition, int endPosition, byte imm8, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.imm8 = imm8;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_rcll(offsetAsInt(), imm8);
        }
    }

    class rip_rclq_1585 extends InstructionWithOffset {
        private final byte imm8;
        rip_rclq_1585(int startPosition, int endPosition, byte imm8, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.imm8 = imm8;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_rclq(offsetAsInt(), imm8);
        }
    }

    class rip_rclw_1657 extends InstructionWithOffset {
        private final byte imm8;
        rip_rclw_1657(int startPosition, int endPosition, byte imm8, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.imm8 = imm8;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_rclw(offsetAsInt(), imm8);
        }
    }

    class rip_rcpps_6623 extends InstructionWithOffset {
        private final AMD64XMMRegister destination;
        rip_rcpps_6623(int startPosition, int endPosition, AMD64XMMRegister destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_rcpps(destination, offsetAsInt());
        }
    }

    class rip_rcpss_6860 extends InstructionWithOffset {
        private final AMD64XMMRegister destination;
        rip_rcpss_6860(int startPosition, int endPosition, AMD64XMMRegister destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_rcpss(destination, offsetAsInt());
        }
    }

    class rip_rcrb___1_1791 extends InstructionWithOffset {
        rip_rcrb___1_1791(int startPosition, int endPosition, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_rcrb___1(offsetAsInt());
        }
    }

    class rip_rcrl___1_2007 extends InstructionWithOffset {
        rip_rcrl___1_2007(int startPosition, int endPosition, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_rcrl___1(offsetAsInt());
        }
    }

    class rip_rcrq___1_2079 extends InstructionWithOffset {
        rip_rcrq___1_2079(int startPosition, int endPosition, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_rcrq___1(offsetAsInt());
        }
    }

    class rip_rcrw___1_2151 extends InstructionWithOffset {
        rip_rcrw___1_2151(int startPosition, int endPosition, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_rcrw___1(offsetAsInt());
        }
    }

    class rip_rcrb___CL_2223 extends InstructionWithOffset {
        rip_rcrb___CL_2223(int startPosition, int endPosition, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_rcrb___CL(offsetAsInt());
        }
    }

    class rip_rcrl___CL_2439 extends InstructionWithOffset {
        rip_rcrl___CL_2439(int startPosition, int endPosition, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_rcrl___CL(offsetAsInt());
        }
    }

    class rip_rcrq___CL_2511 extends InstructionWithOffset {
        rip_rcrq___CL_2511(int startPosition, int endPosition, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_rcrq___CL(offsetAsInt());
        }
    }

    class rip_rcrw___CL_2583 extends InstructionWithOffset {
        rip_rcrw___CL_2583(int startPosition, int endPosition, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_rcrw___CL(offsetAsInt());
        }
    }

    class rip_rcrb_1301 extends InstructionWithOffset {
        private final byte imm8;
        rip_rcrb_1301(int startPosition, int endPosition, byte imm8, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.imm8 = imm8;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_rcrb(offsetAsInt(), imm8);
        }
    }

    class rip_rcrl_1517 extends InstructionWithOffset {
        private final byte imm8;
        rip_rcrl_1517(int startPosition, int endPosition, byte imm8, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.imm8 = imm8;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_rcrl(offsetAsInt(), imm8);
        }
    }

    class rip_rcrq_1589 extends InstructionWithOffset {
        private final byte imm8;
        rip_rcrq_1589(int startPosition, int endPosition, byte imm8, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.imm8 = imm8;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_rcrq(offsetAsInt(), imm8);
        }
    }

    class rip_rcrw_1661 extends InstructionWithOffset {
        private final byte imm8;
        rip_rcrw_1661(int startPosition, int endPosition, byte imm8, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.imm8 = imm8;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_rcrw(offsetAsInt(), imm8);
        }
    }

    class rip_rolb___1_1779 extends InstructionWithOffset {
        rip_rolb___1_1779(int startPosition, int endPosition, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_rolb___1(offsetAsInt());
        }
    }

    class rip_roll___1_1995 extends InstructionWithOffset {
        rip_roll___1_1995(int startPosition, int endPosition, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_roll___1(offsetAsInt());
        }
    }

    class rip_rolq___1_2067 extends InstructionWithOffset {
        rip_rolq___1_2067(int startPosition, int endPosition, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_rolq___1(offsetAsInt());
        }
    }

    class rip_rolw___1_2139 extends InstructionWithOffset {
        rip_rolw___1_2139(int startPosition, int endPosition, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_rolw___1(offsetAsInt());
        }
    }

    class rip_rolb___CL_2211 extends InstructionWithOffset {
        rip_rolb___CL_2211(int startPosition, int endPosition, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_rolb___CL(offsetAsInt());
        }
    }

    class rip_roll___CL_2427 extends InstructionWithOffset {
        rip_roll___CL_2427(int startPosition, int endPosition, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_roll___CL(offsetAsInt());
        }
    }

    class rip_rolq___CL_2499 extends InstructionWithOffset {
        rip_rolq___CL_2499(int startPosition, int endPosition, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_rolq___CL(offsetAsInt());
        }
    }

    class rip_rolw___CL_2571 extends InstructionWithOffset {
        rip_rolw___CL_2571(int startPosition, int endPosition, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_rolw___CL(offsetAsInt());
        }
    }

    class rip_rolb_1289 extends InstructionWithOffset {
        private final byte imm8;
        rip_rolb_1289(int startPosition, int endPosition, byte imm8, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.imm8 = imm8;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_rolb(offsetAsInt(), imm8);
        }
    }

    class rip_roll_1505 extends InstructionWithOffset {
        private final byte imm8;
        rip_roll_1505(int startPosition, int endPosition, byte imm8, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.imm8 = imm8;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_roll(offsetAsInt(), imm8);
        }
    }

    class rip_rolq_1577 extends InstructionWithOffset {
        private final byte imm8;
        rip_rolq_1577(int startPosition, int endPosition, byte imm8, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.imm8 = imm8;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_rolq(offsetAsInt(), imm8);
        }
    }

    class rip_rolw_1649 extends InstructionWithOffset {
        private final byte imm8;
        rip_rolw_1649(int startPosition, int endPosition, byte imm8, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.imm8 = imm8;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_rolw(offsetAsInt(), imm8);
        }
    }

    class rip_rorb___1_1783 extends InstructionWithOffset {
        rip_rorb___1_1783(int startPosition, int endPosition, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_rorb___1(offsetAsInt());
        }
    }

    class rip_rorl___1_1999 extends InstructionWithOffset {
        rip_rorl___1_1999(int startPosition, int endPosition, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_rorl___1(offsetAsInt());
        }
    }

    class rip_rorq___1_2071 extends InstructionWithOffset {
        rip_rorq___1_2071(int startPosition, int endPosition, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_rorq___1(offsetAsInt());
        }
    }

    class rip_rorw___1_2143 extends InstructionWithOffset {
        rip_rorw___1_2143(int startPosition, int endPosition, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_rorw___1(offsetAsInt());
        }
    }

    class rip_rorb___CL_2215 extends InstructionWithOffset {
        rip_rorb___CL_2215(int startPosition, int endPosition, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_rorb___CL(offsetAsInt());
        }
    }

    class rip_rorl___CL_2431 extends InstructionWithOffset {
        rip_rorl___CL_2431(int startPosition, int endPosition, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_rorl___CL(offsetAsInt());
        }
    }

    class rip_rorq___CL_2503 extends InstructionWithOffset {
        rip_rorq___CL_2503(int startPosition, int endPosition, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_rorq___CL(offsetAsInt());
        }
    }

    class rip_rorw___CL_2575 extends InstructionWithOffset {
        rip_rorw___CL_2575(int startPosition, int endPosition, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_rorw___CL(offsetAsInt());
        }
    }

    class rip_rorb_1293 extends InstructionWithOffset {
        private final byte imm8;
        rip_rorb_1293(int startPosition, int endPosition, byte imm8, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.imm8 = imm8;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_rorb(offsetAsInt(), imm8);
        }
    }

    class rip_rorl_1509 extends InstructionWithOffset {
        private final byte imm8;
        rip_rorl_1509(int startPosition, int endPosition, byte imm8, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.imm8 = imm8;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_rorl(offsetAsInt(), imm8);
        }
    }

    class rip_rorq_1581 extends InstructionWithOffset {
        private final byte imm8;
        rip_rorq_1581(int startPosition, int endPosition, byte imm8, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.imm8 = imm8;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_rorq(offsetAsInt(), imm8);
        }
    }

    class rip_rorw_1653 extends InstructionWithOffset {
        private final byte imm8;
        rip_rorw_1653(int startPosition, int endPosition, byte imm8, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.imm8 = imm8;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_rorw(offsetAsInt(), imm8);
        }
    }

    class rip_rsqrtps_6605 extends InstructionWithOffset {
        private final AMD64XMMRegister destination;
        rip_rsqrtps_6605(int startPosition, int endPosition, AMD64XMMRegister destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_rsqrtps(destination, offsetAsInt());
        }
    }

    class rip_rsqrtss_6842 extends InstructionWithOffset {
        private final AMD64XMMRegister destination;
        rip_rsqrtss_6842(int startPosition, int endPosition, AMD64XMMRegister destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_rsqrtss(destination, offsetAsInt());
        }
    }

    class rip_sarb___1_1807 extends InstructionWithOffset {
        rip_sarb___1_1807(int startPosition, int endPosition, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_sarb___1(offsetAsInt());
        }
    }

    class rip_sarl___1_2023 extends InstructionWithOffset {
        rip_sarl___1_2023(int startPosition, int endPosition, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_sarl___1(offsetAsInt());
        }
    }

    class rip_sarq___1_2095 extends InstructionWithOffset {
        rip_sarq___1_2095(int startPosition, int endPosition, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_sarq___1(offsetAsInt());
        }
    }

    class rip_sarw___1_2167 extends InstructionWithOffset {
        rip_sarw___1_2167(int startPosition, int endPosition, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_sarw___1(offsetAsInt());
        }
    }

    class rip_sarb___CL_2239 extends InstructionWithOffset {
        rip_sarb___CL_2239(int startPosition, int endPosition, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_sarb___CL(offsetAsInt());
        }
    }

    class rip_sarl___CL_2455 extends InstructionWithOffset {
        rip_sarl___CL_2455(int startPosition, int endPosition, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_sarl___CL(offsetAsInt());
        }
    }

    class rip_sarq___CL_2527 extends InstructionWithOffset {
        rip_sarq___CL_2527(int startPosition, int endPosition, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_sarq___CL(offsetAsInt());
        }
    }

    class rip_sarw___CL_2599 extends InstructionWithOffset {
        rip_sarw___CL_2599(int startPosition, int endPosition, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_sarw___CL(offsetAsInt());
        }
    }

    class rip_sarb_1317 extends InstructionWithOffset {
        private final byte imm8;
        rip_sarb_1317(int startPosition, int endPosition, byte imm8, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.imm8 = imm8;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_sarb(offsetAsInt(), imm8);
        }
    }

    class rip_sarl_1533 extends InstructionWithOffset {
        private final byte imm8;
        rip_sarl_1533(int startPosition, int endPosition, byte imm8, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.imm8 = imm8;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_sarl(offsetAsInt(), imm8);
        }
    }

    class rip_sarq_1605 extends InstructionWithOffset {
        private final byte imm8;
        rip_sarq_1605(int startPosition, int endPosition, byte imm8, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.imm8 = imm8;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_sarq(offsetAsInt(), imm8);
        }
    }

    class rip_sarw_1677 extends InstructionWithOffset {
        private final byte imm8;
        rip_sarw_1677(int startPosition, int endPosition, byte imm8, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.imm8 = imm8;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_sarw(offsetAsInt(), imm8);
        }
    }

    class rip_sbb_3329 extends InstructionWithOffset {
        private final AMD64GeneralRegister16 destination;
        rip_sbb_3329(int startPosition, int endPosition, AMD64GeneralRegister16 destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_sbb(destination, offsetAsInt());
        }
    }

    class rip_sbb_3311 extends InstructionWithOffset {
        private final AMD64GeneralRegister32 destination;
        rip_sbb_3311(int startPosition, int endPosition, AMD64GeneralRegister32 destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_sbb(destination, offsetAsInt());
        }
    }

    class rip_sbb_3320 extends InstructionWithOffset {
        private final AMD64GeneralRegister64 destination;
        rip_sbb_3320(int startPosition, int endPosition, AMD64GeneralRegister64 destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_sbb(destination, offsetAsInt());
        }
    }

    class rip_sbb_3284 extends InstructionWithOffset {
        private final AMD64GeneralRegister8 destination;
        rip_sbb_3284(int startPosition, int endPosition, AMD64GeneralRegister8 destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_sbb(destination, offsetAsInt());
        }
    }

    class rip_sbbb_512 extends InstructionWithOffset {
        private final byte imm8;
        rip_sbbb_512(int startPosition, int endPosition, byte imm8, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.imm8 = imm8;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_sbbb(offsetAsInt(), imm8);
        }
    }

    class rip_sbbl_944 extends InstructionWithOffset {
        private final byte imm8;
        rip_sbbl_944(int startPosition, int endPosition, byte imm8, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.imm8 = imm8;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_sbbl(offsetAsInt(), imm8);
        }
    }

    class rip_sbbq_1016 extends InstructionWithOffset {
        private final byte imm8;
        rip_sbbq_1016(int startPosition, int endPosition, byte imm8, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.imm8 = imm8;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_sbbq(offsetAsInt(), imm8);
        }
    }

    class rip_sbbw_1088 extends InstructionWithOffset {
        private final byte imm8;
        rip_sbbw_1088(int startPosition, int endPosition, byte imm8, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.imm8 = imm8;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_sbbw(offsetAsInt(), imm8);
        }
    }

    class rip_sbb_3275 extends InstructionWithOffset {
        private final AMD64GeneralRegister16 source;
        rip_sbb_3275(int startPosition, int endPosition, AMD64GeneralRegister16 source, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.source = source;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_sbb(offsetAsInt(), source);
        }
    }

    class rip_sbb_3257 extends InstructionWithOffset {
        private final AMD64GeneralRegister32 source;
        rip_sbb_3257(int startPosition, int endPosition, AMD64GeneralRegister32 source, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.source = source;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_sbb(offsetAsInt(), source);
        }
    }

    class rip_sbb_3266 extends InstructionWithOffset {
        private final AMD64GeneralRegister64 source;
        rip_sbb_3266(int startPosition, int endPosition, AMD64GeneralRegister64 source, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.source = source;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_sbb(offsetAsInt(), source);
        }
    }

    class rip_sbb_3230 extends InstructionWithOffset {
        private final AMD64GeneralRegister8 source;
        rip_sbb_3230(int startPosition, int endPosition, AMD64GeneralRegister8 source, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.source = source;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_sbb(offsetAsInt(), source);
        }
    }

    class rip_sbbl_728 extends InstructionWithOffset {
        private final int imm32;
        rip_sbbl_728(int startPosition, int endPosition, int imm32, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.imm32 = imm32;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_sbbl(offsetAsInt(), imm32);
        }
    }

    class rip_sbbq_800 extends InstructionWithOffset {
        private final int imm32;
        rip_sbbq_800(int startPosition, int endPosition, int imm32, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.imm32 = imm32;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_sbbq(offsetAsInt(), imm32);
        }
    }

    class rip_sbbw_872 extends InstructionWithOffset {
        private final short imm16;
        rip_sbbw_872(int startPosition, int endPosition, short imm16, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.imm16 = imm16;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_sbbw(offsetAsInt(), imm16);
        }
    }

    class rip_setb_7573 extends InstructionWithOffset {
        rip_setb_7573(int startPosition, int endPosition, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_setb(offsetAsInt());
        }
    }

    class rip_setbe_7681 extends InstructionWithOffset {
        rip_setbe_7681(int startPosition, int endPosition, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_setbe(offsetAsInt());
        }
    }

    class rip_setl_11141 extends InstructionWithOffset {
        rip_setl_11141(int startPosition, int endPosition, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_setl(offsetAsInt());
        }
    }

    class rip_setle_11195 extends InstructionWithOffset {
        rip_setle_11195(int startPosition, int endPosition, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_setle(offsetAsInt());
        }
    }

    class rip_setnb_7600 extends InstructionWithOffset {
        rip_setnb_7600(int startPosition, int endPosition, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_setnb(offsetAsInt());
        }
    }

    class rip_setnbe_7708 extends InstructionWithOffset {
        rip_setnbe_7708(int startPosition, int endPosition, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_setnbe(offsetAsInt());
        }
    }

    class rip_setnl_11168 extends InstructionWithOffset {
        rip_setnl_11168(int startPosition, int endPosition, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_setnl(offsetAsInt());
        }
    }

    class rip_setnle_11222 extends InstructionWithOffset {
        rip_setnle_11222(int startPosition, int endPosition, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_setnle(offsetAsInt());
        }
    }

    class rip_setno_7546 extends InstructionWithOffset {
        rip_setno_7546(int startPosition, int endPosition, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_setno(offsetAsInt());
        }
    }

    class rip_setnp_11114 extends InstructionWithOffset {
        rip_setnp_11114(int startPosition, int endPosition, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_setnp(offsetAsInt());
        }
    }

    class rip_setns_11060 extends InstructionWithOffset {
        rip_setns_11060(int startPosition, int endPosition, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_setns(offsetAsInt());
        }
    }

    class rip_setnz_7654 extends InstructionWithOffset {
        rip_setnz_7654(int startPosition, int endPosition, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_setnz(offsetAsInt());
        }
    }

    class rip_seto_7519 extends InstructionWithOffset {
        rip_seto_7519(int startPosition, int endPosition, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_seto(offsetAsInt());
        }
    }

    class rip_setp_11087 extends InstructionWithOffset {
        rip_setp_11087(int startPosition, int endPosition, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_setp(offsetAsInt());
        }
    }

    class rip_sets_11033 extends InstructionWithOffset {
        rip_sets_11033(int startPosition, int endPosition, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_sets(offsetAsInt());
        }
    }

    class rip_setz_7627 extends InstructionWithOffset {
        rip_setz_7627(int startPosition, int endPosition, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_setz(offsetAsInt());
        }
    }

    class rip_sgdt_5648 extends InstructionWithOffset {
        rip_sgdt_5648(int startPosition, int endPosition, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_sgdt(offsetAsInt());
        }
    }

    class rip_shlb___1_1795 extends InstructionWithOffset {
        rip_shlb___1_1795(int startPosition, int endPosition, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_shlb___1(offsetAsInt());
        }
    }

    class rip_shll___1_2011 extends InstructionWithOffset {
        rip_shll___1_2011(int startPosition, int endPosition, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_shll___1(offsetAsInt());
        }
    }

    class rip_shlq___1_2083 extends InstructionWithOffset {
        rip_shlq___1_2083(int startPosition, int endPosition, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_shlq___1(offsetAsInt());
        }
    }

    class rip_shlw___1_2155 extends InstructionWithOffset {
        rip_shlw___1_2155(int startPosition, int endPosition, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_shlw___1(offsetAsInt());
        }
    }

    class rip_shlb___CL_2227 extends InstructionWithOffset {
        rip_shlb___CL_2227(int startPosition, int endPosition, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_shlb___CL(offsetAsInt());
        }
    }

    class rip_shll___CL_2443 extends InstructionWithOffset {
        rip_shll___CL_2443(int startPosition, int endPosition, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_shll___CL(offsetAsInt());
        }
    }

    class rip_shlq___CL_2515 extends InstructionWithOffset {
        rip_shlq___CL_2515(int startPosition, int endPosition, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_shlq___CL(offsetAsInt());
        }
    }

    class rip_shlw___CL_2587 extends InstructionWithOffset {
        rip_shlw___CL_2587(int startPosition, int endPosition, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_shlw___CL(offsetAsInt());
        }
    }

    class rip_shlb_1305 extends InstructionWithOffset {
        private final byte imm8;
        rip_shlb_1305(int startPosition, int endPosition, byte imm8, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.imm8 = imm8;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_shlb(offsetAsInt(), imm8);
        }
    }

    class rip_shll_1521 extends InstructionWithOffset {
        private final byte imm8;
        rip_shll_1521(int startPosition, int endPosition, byte imm8, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.imm8 = imm8;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_shll(offsetAsInt(), imm8);
        }
    }

    class rip_shlq_1593 extends InstructionWithOffset {
        private final byte imm8;
        rip_shlq_1593(int startPosition, int endPosition, byte imm8, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.imm8 = imm8;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_shlq(offsetAsInt(), imm8);
        }
    }

    class rip_shlw_1665 extends InstructionWithOffset {
        private final byte imm8;
        rip_shlw_1665(int startPosition, int endPosition, byte imm8, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.imm8 = imm8;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_shlw(offsetAsInt(), imm8);
        }
    }

    class rip_shld_CL_7814 extends InstructionWithOffset {
        private final AMD64GeneralRegister16 source;
        rip_shld_CL_7814(int startPosition, int endPosition, AMD64GeneralRegister16 source, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.source = source;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_shld_CL(offsetAsInt(), source);
        }
    }

    class rip_shld_7787 extends InstructionWithOffset {
        private final AMD64GeneralRegister16 source;
        private final byte imm8;
        rip_shld_7787(int startPosition, int endPosition, AMD64GeneralRegister16 source, byte imm8, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.source = source;
            this.imm8 = imm8;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_shld(offsetAsInt(), source, imm8);
        }
    }

    class rip_shld_CL_7796 extends InstructionWithOffset {
        private final AMD64GeneralRegister32 source;
        rip_shld_CL_7796(int startPosition, int endPosition, AMD64GeneralRegister32 source, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.source = source;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_shld_CL(offsetAsInt(), source);
        }
    }

    class rip_shld_7769 extends InstructionWithOffset {
        private final AMD64GeneralRegister32 source;
        private final byte imm8;
        rip_shld_7769(int startPosition, int endPosition, AMD64GeneralRegister32 source, byte imm8, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.source = source;
            this.imm8 = imm8;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_shld(offsetAsInt(), source, imm8);
        }
    }

    class rip_shld_CL_7805 extends InstructionWithOffset {
        private final AMD64GeneralRegister64 source;
        rip_shld_CL_7805(int startPosition, int endPosition, AMD64GeneralRegister64 source, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.source = source;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_shld_CL(offsetAsInt(), source);
        }
    }

    class rip_shld_7778 extends InstructionWithOffset {
        private final AMD64GeneralRegister64 source;
        private final byte imm8;
        rip_shld_7778(int startPosition, int endPosition, AMD64GeneralRegister64 source, byte imm8, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.source = source;
            this.imm8 = imm8;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_shld(offsetAsInt(), source, imm8);
        }
    }

    class rip_shrb___1_1799 extends InstructionWithOffset {
        rip_shrb___1_1799(int startPosition, int endPosition, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_shrb___1(offsetAsInt());
        }
    }

    class rip_shrl___1_2015 extends InstructionWithOffset {
        rip_shrl___1_2015(int startPosition, int endPosition, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_shrl___1(offsetAsInt());
        }
    }

    class rip_shrq___1_2087 extends InstructionWithOffset {
        rip_shrq___1_2087(int startPosition, int endPosition, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_shrq___1(offsetAsInt());
        }
    }

    class rip_shrw___1_2159 extends InstructionWithOffset {
        rip_shrw___1_2159(int startPosition, int endPosition, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_shrw___1(offsetAsInt());
        }
    }

    class rip_shrb___CL_2231 extends InstructionWithOffset {
        rip_shrb___CL_2231(int startPosition, int endPosition, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_shrb___CL(offsetAsInt());
        }
    }

    class rip_shrl___CL_2447 extends InstructionWithOffset {
        rip_shrl___CL_2447(int startPosition, int endPosition, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_shrl___CL(offsetAsInt());
        }
    }

    class rip_shrq___CL_2519 extends InstructionWithOffset {
        rip_shrq___CL_2519(int startPosition, int endPosition, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_shrq___CL(offsetAsInt());
        }
    }

    class rip_shrw___CL_2591 extends InstructionWithOffset {
        rip_shrw___CL_2591(int startPosition, int endPosition, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_shrw___CL(offsetAsInt());
        }
    }

    class rip_shrb_1309 extends InstructionWithOffset {
        private final byte imm8;
        rip_shrb_1309(int startPosition, int endPosition, byte imm8, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.imm8 = imm8;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_shrb(offsetAsInt(), imm8);
        }
    }

    class rip_shrl_1525 extends InstructionWithOffset {
        private final byte imm8;
        rip_shrl_1525(int startPosition, int endPosition, byte imm8, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.imm8 = imm8;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_shrl(offsetAsInt(), imm8);
        }
    }

    class rip_shrq_1597 extends InstructionWithOffset {
        private final byte imm8;
        rip_shrq_1597(int startPosition, int endPosition, byte imm8, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.imm8 = imm8;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_shrq(offsetAsInt(), imm8);
        }
    }

    class rip_shrw_1669 extends InstructionWithOffset {
        private final byte imm8;
        rip_shrw_1669(int startPosition, int endPosition, byte imm8, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.imm8 = imm8;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_shrw(offsetAsInt(), imm8);
        }
    }

    class rip_shrd_CL_11328 extends InstructionWithOffset {
        private final AMD64GeneralRegister16 source;
        rip_shrd_CL_11328(int startPosition, int endPosition, AMD64GeneralRegister16 source, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.source = source;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_shrd_CL(offsetAsInt(), source);
        }
    }

    class rip_shrd_11301 extends InstructionWithOffset {
        private final AMD64GeneralRegister16 source;
        private final byte imm8;
        rip_shrd_11301(int startPosition, int endPosition, AMD64GeneralRegister16 source, byte imm8, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.source = source;
            this.imm8 = imm8;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_shrd(offsetAsInt(), source, imm8);
        }
    }

    class rip_shrd_CL_11310 extends InstructionWithOffset {
        private final AMD64GeneralRegister32 source;
        rip_shrd_CL_11310(int startPosition, int endPosition, AMD64GeneralRegister32 source, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.source = source;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_shrd_CL(offsetAsInt(), source);
        }
    }

    class rip_shrd_11283 extends InstructionWithOffset {
        private final AMD64GeneralRegister32 source;
        private final byte imm8;
        rip_shrd_11283(int startPosition, int endPosition, AMD64GeneralRegister32 source, byte imm8, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.source = source;
            this.imm8 = imm8;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_shrd(offsetAsInt(), source, imm8);
        }
    }

    class rip_shrd_CL_11319 extends InstructionWithOffset {
        private final AMD64GeneralRegister64 source;
        rip_shrd_CL_11319(int startPosition, int endPosition, AMD64GeneralRegister64 source, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.source = source;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_shrd_CL(offsetAsInt(), source);
        }
    }

    class rip_shrd_11292 extends InstructionWithOffset {
        private final AMD64GeneralRegister64 source;
        private final byte imm8;
        rip_shrd_11292(int startPosition, int endPosition, AMD64GeneralRegister64 source, byte imm8, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.source = source;
            this.imm8 = imm8;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_shrd(offsetAsInt(), source, imm8);
        }
    }

    class rip_shufpd_8121 extends InstructionWithOffset {
        private final AMD64XMMRegister destination;
        private final byte imm8;
        rip_shufpd_8121(int startPosition, int endPosition, AMD64XMMRegister destination, byte imm8, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
            this.imm8 = imm8;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_shufpd(destination, offsetAsInt(), imm8);
        }
    }

    class rip_shufps_8049 extends InstructionWithOffset {
        private final AMD64XMMRegister destination;
        private final byte imm8;
        rip_shufps_8049(int startPosition, int endPosition, AMD64XMMRegister destination, byte imm8, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
            this.imm8 = imm8;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_shufps(destination, offsetAsInt(), imm8);
        }
    }

    class rip_sidt_5652 extends InstructionWithOffset {
        rip_sidt_5652(int startPosition, int endPosition, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_sidt(offsetAsInt());
        }
    }

    class rip_sldt_5486 extends InstructionWithOffset {
        rip_sldt_5486(int startPosition, int endPosition, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_sldt(offsetAsInt());
        }
    }

    class rip_smsw_5664 extends InstructionWithOffset {
        rip_smsw_5664(int startPosition, int endPosition, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_smsw(offsetAsInt());
        }
    }

    class rip_sqrtpd_6716 extends InstructionWithOffset {
        private final AMD64XMMRegister destination;
        rip_sqrtpd_6716(int startPosition, int endPosition, AMD64XMMRegister destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_sqrtpd(destination, offsetAsInt());
        }
    }

    class rip_sqrtps_6587 extends InstructionWithOffset {
        private final AMD64XMMRegister destination;
        rip_sqrtps_6587(int startPosition, int endPosition, AMD64XMMRegister destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_sqrtps(destination, offsetAsInt());
        }
    }

    class rip_sqrtsd_6806 extends InstructionWithOffset {
        private final AMD64XMMRegister destination;
        rip_sqrtsd_6806(int startPosition, int endPosition, AMD64XMMRegister destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_sqrtsd(destination, offsetAsInt());
        }
    }

    class rip_sqrtss_6824 extends InstructionWithOffset {
        private final AMD64XMMRegister destination;
        rip_sqrtss_6824(int startPosition, int endPosition, AMD64XMMRegister destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_sqrtss(destination, offsetAsInt());
        }
    }

    class rip_stmxcsr_11349 extends InstructionWithOffset {
        rip_stmxcsr_11349(int startPosition, int endPosition, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_stmxcsr(offsetAsInt());
        }
    }

    class rip_str_5490 extends InstructionWithOffset {
        rip_str_5490(int startPosition, int endPosition, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_str(offsetAsInt());
        }
    }

    class rip_sub_3443 extends InstructionWithOffset {
        private final AMD64GeneralRegister16 destination;
        rip_sub_3443(int startPosition, int endPosition, AMD64GeneralRegister16 destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_sub(destination, offsetAsInt());
        }
    }

    class rip_sub_3425 extends InstructionWithOffset {
        private final AMD64GeneralRegister32 destination;
        rip_sub_3425(int startPosition, int endPosition, AMD64GeneralRegister32 destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_sub(destination, offsetAsInt());
        }
    }

    class rip_sub_3434 extends InstructionWithOffset {
        private final AMD64GeneralRegister64 destination;
        rip_sub_3434(int startPosition, int endPosition, AMD64GeneralRegister64 destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_sub(destination, offsetAsInt());
        }
    }

    class rip_sub_3398 extends InstructionWithOffset {
        private final AMD64GeneralRegister8 destination;
        rip_sub_3398(int startPosition, int endPosition, AMD64GeneralRegister8 destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_sub(destination, offsetAsInt());
        }
    }

    class rip_subb_520 extends InstructionWithOffset {
        private final byte imm8;
        rip_subb_520(int startPosition, int endPosition, byte imm8, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.imm8 = imm8;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_subb(offsetAsInt(), imm8);
        }
    }

    class rip_subl_952 extends InstructionWithOffset {
        private final byte imm8;
        rip_subl_952(int startPosition, int endPosition, byte imm8, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.imm8 = imm8;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_subl(offsetAsInt(), imm8);
        }
    }

    class rip_subq_1024 extends InstructionWithOffset {
        private final byte imm8;
        rip_subq_1024(int startPosition, int endPosition, byte imm8, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.imm8 = imm8;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_subq(offsetAsInt(), imm8);
        }
    }

    class rip_subw_1096 extends InstructionWithOffset {
        private final byte imm8;
        rip_subw_1096(int startPosition, int endPosition, byte imm8, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.imm8 = imm8;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_subw(offsetAsInt(), imm8);
        }
    }

    class rip_sub_3389 extends InstructionWithOffset {
        private final AMD64GeneralRegister16 source;
        rip_sub_3389(int startPosition, int endPosition, AMD64GeneralRegister16 source, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.source = source;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_sub(offsetAsInt(), source);
        }
    }

    class rip_sub_3371 extends InstructionWithOffset {
        private final AMD64GeneralRegister32 source;
        rip_sub_3371(int startPosition, int endPosition, AMD64GeneralRegister32 source, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.source = source;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_sub(offsetAsInt(), source);
        }
    }

    class rip_sub_3380 extends InstructionWithOffset {
        private final AMD64GeneralRegister64 source;
        rip_sub_3380(int startPosition, int endPosition, AMD64GeneralRegister64 source, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.source = source;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_sub(offsetAsInt(), source);
        }
    }

    class rip_sub_3344 extends InstructionWithOffset {
        private final AMD64GeneralRegister8 source;
        rip_sub_3344(int startPosition, int endPosition, AMD64GeneralRegister8 source, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.source = source;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_sub(offsetAsInt(), source);
        }
    }

    class rip_subl_736 extends InstructionWithOffset {
        private final int imm32;
        rip_subl_736(int startPosition, int endPosition, int imm32, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.imm32 = imm32;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_subl(offsetAsInt(), imm32);
        }
    }

    class rip_subq_808 extends InstructionWithOffset {
        private final int imm32;
        rip_subq_808(int startPosition, int endPosition, int imm32, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.imm32 = imm32;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_subq(offsetAsInt(), imm32);
        }
    }

    class rip_subw_880 extends InstructionWithOffset {
        private final short imm16;
        rip_subw_880(int startPosition, int endPosition, short imm16, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.imm16 = imm16;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_subw(offsetAsInt(), imm16);
        }
    }

    class rip_subpd_10179 extends InstructionWithOffset {
        private final AMD64XMMRegister destination;
        rip_subpd_10179(int startPosition, int endPosition, AMD64XMMRegister destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_subpd(destination, offsetAsInt());
        }
    }

    class rip_subps_10035 extends InstructionWithOffset {
        private final AMD64XMMRegister destination;
        rip_subps_10035(int startPosition, int endPosition, AMD64XMMRegister destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_subps(destination, offsetAsInt());
        }
    }

    class rip_subsd_10305 extends InstructionWithOffset {
        private final AMD64XMMRegister destination;
        rip_subsd_10305(int startPosition, int endPosition, AMD64XMMRegister destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_subsd(destination, offsetAsInt());
        }
    }

    class rip_subss_10449 extends InstructionWithOffset {
        private final AMD64XMMRegister destination;
        rip_subss_10449(int startPosition, int endPosition, AMD64XMMRegister destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_subss(destination, offsetAsInt());
        }
    }

    class rip_testb_2684 extends InstructionWithOffset {
        private final byte imm8;
        rip_testb_2684(int startPosition, int endPosition, byte imm8, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.imm8 = imm8;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_testb(offsetAsInt(), imm8);
        }
    }

    class rip_test_1193 extends InstructionWithOffset {
        private final AMD64GeneralRegister16 source;
        rip_test_1193(int startPosition, int endPosition, AMD64GeneralRegister16 source, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.source = source;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_test(offsetAsInt(), source);
        }
    }

    class rip_test_1175 extends InstructionWithOffset {
        private final AMD64GeneralRegister32 source;
        rip_test_1175(int startPosition, int endPosition, AMD64GeneralRegister32 source, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.source = source;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_test(offsetAsInt(), source);
        }
    }

    class rip_test_1184 extends InstructionWithOffset {
        private final AMD64GeneralRegister64 source;
        rip_test_1184(int startPosition, int endPosition, AMD64GeneralRegister64 source, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.source = source;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_test(offsetAsInt(), source);
        }
    }

    class rip_test_1148 extends InstructionWithOffset {
        private final AMD64GeneralRegister8 source;
        rip_test_1148(int startPosition, int endPosition, AMD64GeneralRegister8 source, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.source = source;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_test(offsetAsInt(), source);
        }
    }

    class rip_testl_2900 extends InstructionWithOffset {
        private final int imm32;
        rip_testl_2900(int startPosition, int endPosition, int imm32, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.imm32 = imm32;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_testl(offsetAsInt(), imm32);
        }
    }

    class rip_testq_2972 extends InstructionWithOffset {
        private final int imm32;
        rip_testq_2972(int startPosition, int endPosition, int imm32, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.imm32 = imm32;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_testq(offsetAsInt(), imm32);
        }
    }

    class rip_testw_3044 extends InstructionWithOffset {
        private final short imm16;
        rip_testw_3044(int startPosition, int endPosition, short imm16, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.imm16 = imm16;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_testw(offsetAsInt(), imm16);
        }
    }

    class rip_ucomisd_9603 extends InstructionWithOffset {
        private final AMD64XMMRegister destination;
        rip_ucomisd_9603(int startPosition, int endPosition, AMD64XMMRegister destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_ucomisd(destination, offsetAsInt());
        }
    }

    class rip_ucomiss_9444 extends InstructionWithOffset {
        private final AMD64XMMRegister destination;
        rip_ucomiss_9444(int startPosition, int endPosition, AMD64XMMRegister destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_ucomiss(destination, offsetAsInt());
        }
    }

    class rip_unpckhpd_6116 extends InstructionWithOffset {
        private final AMD64XMMRegister destination;
        rip_unpckhpd_6116(int startPosition, int endPosition, AMD64XMMRegister destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_unpckhpd(destination, offsetAsInt());
        }
    }

    class rip_unpckhps_5969 extends InstructionWithOffset {
        private final AMD64XMMRegister destination;
        rip_unpckhps_5969(int startPosition, int endPosition, AMD64XMMRegister destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_unpckhps(destination, offsetAsInt());
        }
    }

    class rip_unpcklpd_6098 extends InstructionWithOffset {
        private final AMD64XMMRegister destination;
        rip_unpcklpd_6098(int startPosition, int endPosition, AMD64XMMRegister destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_unpcklpd(destination, offsetAsInt());
        }
    }

    class rip_unpcklps_5951 extends InstructionWithOffset {
        private final AMD64XMMRegister destination;
        rip_unpcklps_5951(int startPosition, int endPosition, AMD64XMMRegister destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_unpcklps(destination, offsetAsInt());
        }
    }

    class rip_verr_5502 extends InstructionWithOffset {
        rip_verr_5502(int startPosition, int endPosition, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_verr(offsetAsInt());
        }
    }

    class rip_verw_5506 extends InstructionWithOffset {
        rip_verw_5506(int startPosition, int endPosition, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_verw(offsetAsInt());
        }
    }

    class rip_xadd_7994 extends InstructionWithOffset {
        private final AMD64GeneralRegister16 source;
        rip_xadd_7994(int startPosition, int endPosition, AMD64GeneralRegister16 source, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.source = source;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_xadd(offsetAsInt(), source);
        }
    }

    class rip_xadd_7976 extends InstructionWithOffset {
        private final AMD64GeneralRegister32 source;
        rip_xadd_7976(int startPosition, int endPosition, AMD64GeneralRegister32 source, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.source = source;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_xadd(offsetAsInt(), source);
        }
    }

    class rip_xadd_7985 extends InstructionWithOffset {
        private final AMD64GeneralRegister64 source;
        rip_xadd_7985(int startPosition, int endPosition, AMD64GeneralRegister64 source, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.source = source;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_xadd(offsetAsInt(), source);
        }
    }

    class rip_xadd_7949 extends InstructionWithOffset {
        private final AMD64GeneralRegister8 source;
        rip_xadd_7949(int startPosition, int endPosition, AMD64GeneralRegister8 source, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.source = source;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_xadd(offsetAsInt(), source);
        }
    }

    class rip_xchg_1247 extends InstructionWithOffset {
        private final AMD64GeneralRegister16 source;
        rip_xchg_1247(int startPosition, int endPosition, AMD64GeneralRegister16 source, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.source = source;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_xchg(offsetAsInt(), source);
        }
    }

    class rip_xchg_1229 extends InstructionWithOffset {
        private final AMD64GeneralRegister32 source;
        rip_xchg_1229(int startPosition, int endPosition, AMD64GeneralRegister32 source, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.source = source;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_xchg(offsetAsInt(), source);
        }
    }

    class rip_xchg_1238 extends InstructionWithOffset {
        private final AMD64GeneralRegister64 source;
        rip_xchg_1238(int startPosition, int endPosition, AMD64GeneralRegister64 source, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.source = source;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_xchg(offsetAsInt(), source);
        }
    }

    class rip_xchg_1202 extends InstructionWithOffset {
        private final AMD64GeneralRegister8 source;
        rip_xchg_1202(int startPosition, int endPosition, AMD64GeneralRegister8 source, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.source = source;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_xchg(offsetAsInt(), source);
        }
    }

    class rip_xor_445 extends InstructionWithOffset {
        private final AMD64GeneralRegister16 destination;
        rip_xor_445(int startPosition, int endPosition, AMD64GeneralRegister16 destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_xor(destination, offsetAsInt());
        }
    }

    class rip_xor_427 extends InstructionWithOffset {
        private final AMD64GeneralRegister32 destination;
        rip_xor_427(int startPosition, int endPosition, AMD64GeneralRegister32 destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_xor(destination, offsetAsInt());
        }
    }

    class rip_xor_436 extends InstructionWithOffset {
        private final AMD64GeneralRegister64 destination;
        rip_xor_436(int startPosition, int endPosition, AMD64GeneralRegister64 destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_xor(destination, offsetAsInt());
        }
    }

    class rip_xor_400 extends InstructionWithOffset {
        private final AMD64GeneralRegister8 destination;
        rip_xor_400(int startPosition, int endPosition, AMD64GeneralRegister8 destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_xor(destination, offsetAsInt());
        }
    }

    class rip_xorb_524 extends InstructionWithOffset {
        private final byte imm8;
        rip_xorb_524(int startPosition, int endPosition, byte imm8, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.imm8 = imm8;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_xorb(offsetAsInt(), imm8);
        }
    }

    class rip_xorl_956 extends InstructionWithOffset {
        private final byte imm8;
        rip_xorl_956(int startPosition, int endPosition, byte imm8, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.imm8 = imm8;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_xorl(offsetAsInt(), imm8);
        }
    }

    class rip_xorq_1028 extends InstructionWithOffset {
        private final byte imm8;
        rip_xorq_1028(int startPosition, int endPosition, byte imm8, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.imm8 = imm8;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_xorq(offsetAsInt(), imm8);
        }
    }

    class rip_xorw_1100 extends InstructionWithOffset {
        private final byte imm8;
        rip_xorw_1100(int startPosition, int endPosition, byte imm8, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.imm8 = imm8;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_xorw(offsetAsInt(), imm8);
        }
    }

    class rip_xor_391 extends InstructionWithOffset {
        private final AMD64GeneralRegister16 source;
        rip_xor_391(int startPosition, int endPosition, AMD64GeneralRegister16 source, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.source = source;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_xor(offsetAsInt(), source);
        }
    }

    class rip_xor_373 extends InstructionWithOffset {
        private final AMD64GeneralRegister32 source;
        rip_xor_373(int startPosition, int endPosition, AMD64GeneralRegister32 source, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.source = source;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_xor(offsetAsInt(), source);
        }
    }

    class rip_xor_382 extends InstructionWithOffset {
        private final AMD64GeneralRegister64 source;
        rip_xor_382(int startPosition, int endPosition, AMD64GeneralRegister64 source, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.source = source;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_xor(offsetAsInt(), source);
        }
    }

    class rip_xor_346 extends InstructionWithOffset {
        private final AMD64GeneralRegister8 source;
        rip_xor_346(int startPosition, int endPosition, AMD64GeneralRegister8 source, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.source = source;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_xor(offsetAsInt(), source);
        }
    }

    class rip_xorl_740 extends InstructionWithOffset {
        private final int imm32;
        rip_xorl_740(int startPosition, int endPosition, int imm32, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.imm32 = imm32;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_xorl(offsetAsInt(), imm32);
        }
    }

    class rip_xorq_812 extends InstructionWithOffset {
        private final int imm32;
        rip_xorq_812(int startPosition, int endPosition, int imm32, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.imm32 = imm32;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_xorq(offsetAsInt(), imm32);
        }
    }

    class rip_xorw_884 extends InstructionWithOffset {
        private final short imm16;
        rip_xorw_884(int startPosition, int endPosition, short imm16, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.imm16 = imm16;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_xorw(offsetAsInt(), imm16);
        }
    }

    class rip_xorpd_6788 extends InstructionWithOffset {
        private final AMD64XMMRegister destination;
        rip_xorpd_6788(int startPosition, int endPosition, AMD64XMMRegister destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_xorpd(destination, offsetAsInt());
        }
    }

    class rip_xorps_6695 extends InstructionWithOffset {
        private final AMD64XMMRegister destination;
        rip_xorps_6695(int startPosition, int endPosition, AMD64XMMRegister destination, Label label) {
            super(AMD64LabelAssembler.this, startPosition, currentPosition(), label, 4);
            this.destination = destination;
        }
        @Override
        protected void assemble() throws AssemblyException {
            rip_xorps(destination, offsetAsInt());
        }
    }

// END GENERATED LABEL ASSEMBLER METHODS
}
