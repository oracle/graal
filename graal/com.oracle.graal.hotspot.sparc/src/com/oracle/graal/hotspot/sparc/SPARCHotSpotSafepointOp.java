/*
 * Copyright (c) 2011, 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.hotspot.sparc;

import static jdk.internal.jvmci.code.ValueUtil.asRegister;
import static jdk.internal.jvmci.sparc.SPARC.g0;
import jdk.internal.jvmci.code.InfopointReason;
import jdk.internal.jvmci.code.Register;
import jdk.internal.jvmci.code.ValueUtil;
import jdk.internal.jvmci.hotspot.HotSpotVMConfig;
import jdk.internal.jvmci.meta.AllocatableValue;

import com.oracle.graal.asm.sparc.SPARCAddress;
import com.oracle.graal.asm.sparc.SPARCMacroAssembler;
import com.oracle.graal.asm.sparc.SPARCMacroAssembler.Setx;
import com.oracle.graal.lir.LIRFrameState;
import com.oracle.graal.lir.LIRInstructionClass;
import com.oracle.graal.lir.Opcode;
import com.oracle.graal.lir.asm.CompilationResultBuilder;
import com.oracle.graal.lir.gen.LIRGeneratorTool;
import com.oracle.graal.lir.sparc.SPARCLIRInstruction;

/**
 * Emits a safepoint poll.
 */
@Opcode("SAFEPOINT")
public class SPARCHotSpotSafepointOp extends SPARCLIRInstruction {
    public static final LIRInstructionClass<SPARCHotSpotSafepointOp> TYPE = LIRInstructionClass.create(SPARCHotSpotSafepointOp.class);
    public static final SizeEstimate SIZE = SizeEstimate.create(9);

    @State protected LIRFrameState state;
    @Use({OperandFlag.REG}) AllocatableValue safepointPollAddress;
    private final HotSpotVMConfig config;

    public SPARCHotSpotSafepointOp(LIRFrameState state, HotSpotVMConfig config, LIRGeneratorTool tool) {
        super(TYPE, SIZE);
        this.state = state;
        this.config = config;
        SPARCHotSpotLIRGenerator lirGen = (SPARCHotSpotLIRGenerator) tool;
        safepointPollAddress = lirGen.getSafepointAddressValue();
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, SPARCMacroAssembler masm) {
        emitCode(crb, masm, config, false, state, asRegister(safepointPollAddress));
    }

    public static void emitCode(CompilationResultBuilder crb, SPARCMacroAssembler masm, HotSpotVMConfig config, boolean atReturn, LIRFrameState state, Register safepointPollAddress) {
        crb.recordMark(atReturn ? config.MARKID_POLL_RETURN_FAR : config.MARKID_POLL_FAR);
        if (state != null) {
            final int pos = masm.position();
            crb.recordInfopoint(pos, state, InfopointReason.SAFEPOINT);
        }
        masm.ldx(new SPARCAddress(safepointPollAddress, 0), g0);
    }

    public static class SPARCLoadSafepointPollAddress extends SPARCLIRInstruction {
        public static final LIRInstructionClass<SPARCLoadSafepointPollAddress> TYPE = LIRInstructionClass.create(SPARCLoadSafepointPollAddress.class);
        public static final SizeEstimate SIZE = SizeEstimate.create(2);

        @Def({OperandFlag.REG}) protected AllocatableValue result;
        private final HotSpotVMConfig config;

        public SPARCLoadSafepointPollAddress(AllocatableValue result, HotSpotVMConfig config) {
            super(TYPE, SIZE);
            this.result = result;
            this.config = config;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, SPARCMacroAssembler masm) {
            new Setx(config.safepointPollingAddress, ValueUtil.asRegister(result)).emit(masm);
        }
    }
}
