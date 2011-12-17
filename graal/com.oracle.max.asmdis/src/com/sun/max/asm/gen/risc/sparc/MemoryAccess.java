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

/**
 */
class MemoryAccess extends SPARCInstructionDescriptionCreator {

    private void create_A9() {
        define("casa",  op(0x3), op3(0x3c), "[", rs1, "] ", i(0), immAsi, ",",    rs2, rd);
        define("casa",  op(0x3), op3(0x3c), "[", rs1, "] %asi, ", i(1), res_12_5, rs2, rd);
        define("casxa", op(0x3), op3(0x3e), "[", rs1, "] ", i(0), immAsi, ",",    rs2, rd);
        define("casxa", op(0x3), op3(0x3e), "[", rs1, "] %asi, ", i(1), res_12_5, rs2, rd);
    }

    private void create_A20() {
        final Object[] head = {op(0x2), res_29_25, op3(0x3b), rs1, " + "};

        define("flush", head, i(0), res_12_5, rs2);
        define("flush", head, i(1), simm13);
    }

    private void createLoad(String name, Object resultRegister, int op3Contents) {
        String internalName = name;
        String resultPrefix = "";
        if (op3Contents == 0x21) {
            internalName += "_fsr";
            resultPrefix = "%fsr";
        }
        final Object[] head = {op(0x3), "[", rs1, " + ", op3(op3Contents)};
        define(internalName, head, i(0), res_12_5,  rs2, "], ", resultPrefix, resultRegister).setExternalName(name);
        define(internalName, head, i(1), simm13, "], ", resultPrefix, resultRegister).setExternalName(name);
    }

    private void create_A25() {
        createLoad("ld", sfrd, 0x20);
        createLoad("ldd", dfrd, 0x23);
        createLoad("ldq", qfrd, 0x22);
        createLoad("ldx", rd(1), 0x21); // ldxfsr
        if (assembly().generatingDeprecatedInstructions()) {
            createLoad("ld", rd(0), 0x21); // ldfsr
            createLoad("swap", rd, 0xf);
        }
    }

    private void createLoadA(String name, Object resultRegister, int op3Contents) {
        final Object[] head = {op(0x3), "[", rs1, " + ", op3(op3Contents)};
        define(name, head, i(0), rs2, "] ", immAsi, resultRegister);
        define(name, head, i(1), simm13, "] %asi, ", resultRegister);
    }

    private void create_A26() {
        createLoadA("lda", sfrd, 0x30);
        createLoadA("ldda", dfrd, 0x33);
        createLoadA("ldqa", qfrd, 0x32);
    }

    private void create_A27() {
        createLoad("ldsb", rd, 0x9);
        createLoad("ldsh", rd, 0xa);
        createLoad("ldsw", rd, 0x8);
        createLoad("ldub", rd, 0x1);
        createLoad("lduh", rd, 0x2);
        createLoad("lduw", rd, 0x0);
        createLoad("ldx", rd, 0xb);
        if (assembly().generatingDeprecatedInstructions()) {
            createLoad("ldd", rd_even, 0x3);
        }
    }

    private void create_A28() {
        createLoadA("ldsba", rd, 0x19);
        createLoadA("ldsha", rd, 0x1a);
        createLoadA("ldswa", rd, 0x18);
        createLoadA("lduba", rd, 0x11);
        createLoadA("lduha", rd, 0x12);
        createLoadA("lduwa", rd, 0x10);
        createLoadA("ldxa", rd, 0x1b);

        if (assembly().generatingDeprecatedInstructions()) {
            createLoadA("ldda", rd_even, 0x13);
        }
    }

    private void create_A29() {
        createLoad("ldstub", rd, 0xd);
    }

    private void create_A30() {
        createLoadA("ldstuba", rd, 0x1d);
    }

    private void create_A41() {
        define("prefetch", op(0x3), op3(0x2d), "[", rs1, " + ", i(0), res_12_5, rs2, "], ", fcn);
        define("prefetch", op(0x3), op3(0x2d), "[", rs1, " + ", i(1), simm13, "], ", fcn);
        define("prefetcha", op(0x3), op3(0x3d), "[", rs1, " + ", i(0), rs2, "] ", immAsi, fcn);
        define("prefetcha", op(0x3), op3(0x3d), "[", rs1, " + ", i(1), simm13, "] %asi, ", fcn);
    }

    private void createStore(String name, Object fromRegister, int op3Contents) {
        String internalName = name;
        Object openBracket = new Object[]{", [", };
        if (op3Contents == 0x25) {
            internalName += "_fsr";
            openBracket = " %fsr, [";
        }
        final Object[] head = {op(0x3), op3(op3Contents), fromRegister, openBracket, rs1, " + "};
        define(internalName, head, i(0), res_12_5, rs2, "]").setExternalName(name);
        define(internalName, head, i(1), simm13, "]").setExternalName(name);
    }

    private void create_A51() {
        createStore("st", sfrd, 0x24);
        createStore("std", dfrd, 0x27);
        createStore("stq", qfrd, 0x26);
        createStore("stx", rd(1), 0x25);
        if (assembly().generatingDeprecatedInstructions()) {
            createStore("st", rd(0), 0x25);
        }
    }

    private void createStoreA(String name, Object fromRegister, int op3Contents) {
        final Object[] head = {op(0x3), op3(op3Contents), fromRegister, ", [", rs1, " + "};
        define(name, head, i(0), rs2, "]", immAsi);
        define(name, head, i(1), simm13, "] %asi");
    }

    private void create_A52() {
        createStoreA("sta", sfrd, 0x34);
        createStoreA("stda", dfrd, 0x37);
        createStoreA("stqa", qfrd, 0x36);
    }

    private void create_A53() {
        createStore("stb", rd, 0x5);
        createStore("sth", rd, 0x6);
        createStore("stw", rd, 0x4);
        createStore("stx", rd, 0xe);

        if (assembly().generatingDeprecatedInstructions()) {
            createStore("std", rd_even, 0x7);
        }
    }

    private void create_A54() {
        createStoreA("stba", rd, 0x15);
        createStoreA("stha", rd, 0x16);
        createStoreA("stwa", rd, 0x14);
        createStoreA("stxa", rd, 0x1e);

        if (assembly().generatingDeprecatedInstructions()) {
            createStoreA("stda", rd_even, 0x17);
        }
    }

    MemoryAccess(RiscTemplateCreator templateCreator) {
        super(templateCreator);

        setCurrentArchitectureManualSection("A.9");
        create_A9();

        setCurrentArchitectureManualSection("A.20");
        create_A20();

        setCurrentArchitectureManualSection("A.25");
        create_A25();

        setCurrentArchitectureManualSection("A.26");
        create_A26();

        setCurrentArchitectureManualSection("A.27");
        create_A27();

        setCurrentArchitectureManualSection("A.28");
        create_A28();

        setCurrentArchitectureManualSection("A.29");
        create_A29();

        setCurrentArchitectureManualSection("A.30");
        create_A30();

        setCurrentArchitectureManualSection("A.41");
        create_A41();

        setCurrentArchitectureManualSection("A.51");
        create_A51();

        setCurrentArchitectureManualSection("A.52");
        create_A52();

        setCurrentArchitectureManualSection("A.53");
        create_A53();

        setCurrentArchitectureManualSection("A.54");
        create_A54();
    }

}
