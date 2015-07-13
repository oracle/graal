/*
 * Copyright (c) 2009, 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.lir.sparc;

import static com.oracle.graal.lir.LIRInstruction.OperandFlag.*;
import static jdk.internal.jvmci.code.ValueUtil.*;
import jdk.internal.jvmci.code.*;
import jdk.internal.jvmci.meta.*;

import com.oracle.graal.asm.sparc.*;
import com.oracle.graal.lir.*;
import com.oracle.graal.lir.StandardOp.NoOp;
import com.oracle.graal.lir.asm.*;

/**
 * Loads the constant section base into a register.
 *
 * <p>
 * Layout:
 *
 * <pre>
 * +----constant section----+--pad--+---code section--+
 * |<-------------------_------------------->|
 *                      ^- Constant section base pointer
 * </pre>
 *
 * The constant section base pointer is placed as such that the lowest offset -4096 points to the
 * start of the constant section.
 * <p>
 * If the constant section grows beyond 8k size, the immediate addressing cannot be used anymore; in
 * this case absolute addressing (without using the base pointer is used). See also:
 * CodeInstaller::pd_patch_DataSectionReference
 *
 * @see SPARCMove#loadFromConstantTable(CompilationResultBuilder, SPARCMacroAssembler, Kind,
 *      Register, Register, SPARCDelayedControlTransfer, Runnable)
 */
public class SPARCLoadConstantTableBaseOp extends SPARCLIRInstruction {
    public static final LIRInstructionClass<SPARCLoadConstantTableBaseOp> TYPE = LIRInstructionClass.create(SPARCLoadConstantTableBaseOp.class);
    public static final SizeEstimate SIZE = SizeEstimate.create(9);

    private final NoOp placeHolder;
    @Def({REG}) private AllocatableValue base;

    public SPARCLoadConstantTableBaseOp(Variable base, NoOp placeHolder) {
        super(TYPE, SIZE);
        this.base = base;
        this.placeHolder = placeHolder;
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, SPARCMacroAssembler masm) {
        Register baseRegister = asRegister(base);
        int beforePosition = masm.position();
        masm.rdpc(baseRegister);
        // Must match with CodeInstaller::pd_patch_DataSectionReference
        masm.add(baseRegister, (int) SPARCAssembler.minSimm(13), baseRegister);
        masm.sub(baseRegister, beforePosition, baseRegister);
    }

    public AllocatableValue getResult() {
        return base;
    }

    public void setAlive(LIR lir, boolean alive) {
        if (alive) {
            placeHolder.replace(lir, this);
        } else {
            placeHolder.remove(lir);
        }
    }
}
