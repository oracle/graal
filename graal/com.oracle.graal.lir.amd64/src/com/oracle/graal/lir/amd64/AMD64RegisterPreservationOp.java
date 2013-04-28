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
package com.oracle.graal.lir.amd64;

import java.util.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.asm.amd64.*;
import com.oracle.graal.lir.asm.*;

/**
 * Base class for the operations that save or restore registers around another operation that may
 * potentially destroy any register (e.g., a call).
 */
public abstract class AMD64RegisterPreservationOp extends AMD64LIRInstruction {

    protected static void emitCode(TargetMethodAssembler tasm, AMD64MacroAssembler masm, Value[] dst, Value[] src) {
        for (int i = 0; i < dst.length; i++) {
            if (dst[i] != null) {
                AMD64Move.move(tasm, masm, dst[i], src[i]);
            } else {
                assert src[i] == null;
            }
        }
    }

    protected static void doNotPreserve(Set<Register> registers, RegisterValue[] registerValues, StackSlot[] slots) {
        for (int i = 0; i < slots.length; i++) {
            if (registerValues[i] != null) {
                if (registers.contains(registerValues[i].getRegister())) {
                    registerValues[i] = null;
                    slots[i] = null;
                }
            }
        }
    }

    /**
     * Records that no registers in {@code registers} need to be preserved.
     */
    public abstract void doNotPreserve(Set<Register> registers);
}
