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
class MemorySynchronization extends SPARCInstructionDescriptionCreator {

    private void create_A32() {
        define("membar", op(0x2), res_29_25, op3(0x28), bits_18_14(0xf), i(1), res_12_7, membarMask);
    }

    private void create_A51() {
        if (assembly().generatingDeprecatedInstructions()) {
            define("stbar", op(0x2), res_29_25, op3(0x28), bits_18_14(0xf), i(0), res_12_0);
        }
    }

    MemorySynchronization(RiscTemplateCreator templateCreator) {
        super(templateCreator);

        setCurrentArchitectureManualSection("A.32");
        create_A32();

        setCurrentArchitectureManualSection("A.51");
        create_A51();
    }
}
