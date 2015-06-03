/*
 * Copyright (c) 2014, 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.hotspot.amd64;

import com.oracle.jvmci.asm.amd64.*;
import com.oracle.jvmci.code.ValueUtil;
import com.oracle.jvmci.meta.JavaConstant;
import com.oracle.jvmci.meta.AllocatableValue;
import com.oracle.jvmci.meta.Kind;
import com.oracle.graal.hotspot.*;
import com.oracle.graal.lir.*;
import com.oracle.graal.lir.amd64.*;
import com.oracle.graal.lir.asm.*;
import com.oracle.jvmci.hotspot.*;

public final class AMD64HotSpotCardTableShiftOp extends AMD64LIRInstruction {
    public static final LIRInstructionClass<AMD64HotSpotCardTableShiftOp> TYPE = LIRInstructionClass.create(AMD64HotSpotCardTableShiftOp.class);

    @Def({OperandFlag.REG, OperandFlag.ILLEGAL}) private AllocatableValue result;

    private final HotSpotVMConfig config;

    public AMD64HotSpotCardTableShiftOp(AllocatableValue result, HotSpotVMConfig config) {
        super(TYPE);
        this.result = result;
        this.config = config;
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler asm) {
        Kind hostWordKind = HotSpotGraalRuntime.getHostWordKind();
        int alignment = Kind.Int.getBitCount() / Byte.SIZE;
        JavaConstant shift = JavaConstant.forIntegerKind(hostWordKind, 0);
        // recordDataReferenceInCode forces the mov to be rip-relative
        asm.movq(ValueUtil.asRegister(result), (AMD64Address) crb.recordDataReferenceInCode(shift, alignment));
        crb.recordMark(config.MARKID_CARD_TABLE_SHIFT);
    }
}
