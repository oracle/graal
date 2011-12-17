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
public class PrivilegedRegisterAccess extends SPARCInstructionDescriptionCreator {

    private void create_A42() {
        define("rdpr", op(0x2), op3(0x2a), res_13_0, rs1PrivReg, rd);
    }

    private void create_A61() {
        final Object[] head = {op(0x2), op3(0x32), rs1};
        define("wrpr", head, i(0), res_12_5, rs2, rdPrivReg);
        define("wrpr", head, i(1), simm13, rdPrivReg);
    }

    PrivilegedRegisterAccess(RiscTemplateCreator templateCreator) {
        super(templateCreator);

        setCurrentArchitectureManualSection("A.42");
        create_A42();

        setCurrentArchitectureManualSection("A.61");
        create_A61();
    }
}
