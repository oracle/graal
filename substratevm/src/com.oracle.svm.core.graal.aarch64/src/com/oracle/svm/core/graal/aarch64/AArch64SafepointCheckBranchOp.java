/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package com.oracle.svm.core.graal.aarch64;

import java.util.function.Consumer;

import com.oracle.svm.core.nodes.SafepointCheckNode;

import jdk.graal.compiler.asm.Label;
import jdk.graal.compiler.asm.aarch64.AArch64MacroAssembler;
import jdk.graal.compiler.asm.aarch64.AArch64MacroAssembler.ScratchRegister;
import jdk.graal.compiler.lir.LIRInstructionClass;
import jdk.graal.compiler.lir.LabelRef;
import jdk.graal.compiler.lir.StandardOp;
import jdk.graal.compiler.lir.aarch64.AArch64BlockEndOp;
import jdk.graal.compiler.lir.aarch64.AArch64ControlFlow;
import jdk.graal.compiler.lir.asm.CompilationResultBuilder;
import jdk.vm.ci.code.Register;

/**
 * Compact branch for {@link SafepointCheckNode} when safepoint checks only distinguish the normal
 * {@code MAX_VALUE} state from a requested-safepoint {@code 0} state.
 */
public final class AArch64SafepointCheckBranchOp extends AArch64BlockEndOp implements StandardOp.BranchOp {
    public static final LIRInstructionClass<AArch64SafepointCheckBranchOp> TYPE = LIRInstructionClass.create(AArch64SafepointCheckBranchOp.class);

    private static final int CBZ_IMMEDIATE_BITS = 21;

    private final LabelRef trueDestination;
    private final LabelRef falseDestination;
    private final double trueDestinationProbability;

    public AArch64SafepointCheckBranchOp(LabelRef trueDestination, LabelRef falseDestination, double trueDestinationProbability) {
        super(TYPE);
        this.trueDestination = trueDestination;
        this.falseDestination = falseDestination;
        this.trueDestinationProbability = trueDestinationProbability;
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
        try (ScratchRegister scratchRegister = masm.getScratchRegister()) {
            Register counter = scratchRegister.getRegister();
            masm.ldr(AArch64SafepointCheckOp.COUNTER_SIZE, counter, AArch64SafepointCheckOp.counterAddress());
            if (crb.isSuccessorEdge(trueDestination)) {
                emitBranch(crb, masm, counter, falseDestination, true);
            } else if (crb.isSuccessorEdge(falseDestination)) {
                emitBranch(crb, masm, counter, trueDestination, false);
            } else if (trueDestinationProbability < 0.5) {
                emitBranch(crb, masm, counter, falseDestination, true);
                masm.jmp(trueDestination.label());
            } else {
                emitBranch(crb, masm, counter, trueDestination, false);
                masm.jmp(falseDestination.label());
            }
        }
    }

    private void emitBranch(CompilationResultBuilder crb, AArch64MacroAssembler masm, Register counter, LabelRef target, boolean negate) {
        Consumer<Label> cbzBranch = l -> masm.cbz(AArch64SafepointCheckOp.COUNTER_SIZE, counter, l);
        Consumer<Label> cbnzBranch = l -> masm.cbnz(AArch64SafepointCheckOp.COUNTER_SIZE, counter, l);
        AArch64ControlFlow.emitBranchOrFarBranch(crb, masm, this, CBZ_IMMEDIATE_BITS, target.label(), negate ? cbnzBranch : cbzBranch, negate ? cbzBranch : cbnzBranch);
    }
}
