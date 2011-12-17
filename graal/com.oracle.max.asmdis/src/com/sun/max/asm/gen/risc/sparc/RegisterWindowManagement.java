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
public class RegisterWindowManagement extends SPARCInstructionDescriptionCreator {

    private void createSaveOrRestore(String name, int op3Contents) {
        define(name, op(0x2), rs1, op3(op3Contents), i(0), res_12_5, rs2, rd);
        define(name, op(0x2), rs1, op3(op3Contents), i(1), simm13, rd);
    }

    private void create_A21() {
        define("flushw", op(0x2), res_29_25, op3(0x2b), res_18_14, i(0), res_12_0);
    }

    private void create_A45() {
        createSaveOrRestore("save", 0x3c);
        createSaveOrRestore("restore", 0x3d);
    }

    private void create_A46() {
        define("saved", op(0x2), fcnc(0), op3(0x31), res_18_0);
        define("restored", op(0x2), fcnc(1), op3(0x31), res_18_0);
    }

    RegisterWindowManagement(RiscTemplateCreator templateCreator) {
        super(templateCreator);

        setCurrentArchitectureManualSection("A.21");
        create_A21();

        setCurrentArchitectureManualSection("A.45");
        create_A45();

        setCurrentArchitectureManualSection("A.46");
        create_A46();
    }
}
