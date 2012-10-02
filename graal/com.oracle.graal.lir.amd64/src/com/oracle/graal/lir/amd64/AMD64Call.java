/*
 * Copyright (c) 2011, 2012, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.api.code.ValueUtil.*;
import static com.oracle.graal.lir.LIRInstruction.OperandFlag.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.lir.*;
import com.oracle.graal.lir.LIRInstruction.*;
import com.oracle.graal.lir.asm.*;
import com.oracle.max.asm.amd64.*;

public class AMD64Call {

    @Opcode("CALL_DIRECT")
    public static class DirectCallOp extends AMD64LIRInstruction implements StandardOp.CallOp {
        @Def({REG, ILLEGAL}) protected Value result;
        @Use({REG, STACK}) protected Value[] parameters;
        @Temp protected Value[] temps;
        @State protected LIRFrameState state;

        protected final Object targetMethod;

        public DirectCallOp(Object targetMethod, Value result, Value[] parameters, Value[] temps, LIRFrameState state) {
            this.targetMethod = targetMethod;
            this.result = result;
            this.parameters = parameters;
            this.state = state;
            this.temps = temps;
            assert temps != null;
        }

        @Override
        public void emitCode(TargetMethodAssembler tasm, AMD64MacroAssembler masm) {
            emitAlignmentForDirectCall(tasm, masm);
            directCall(tasm, masm, targetMethod, state);
        }

        protected void emitAlignmentForDirectCall(TargetMethodAssembler tasm, AMD64MacroAssembler masm) {
            // make sure that the displacement word of the call ends up word aligned
            int offset = masm.codeBuffer.position();
            offset += tasm.target.arch.machineCodeCallDisplacementOffset;
            while (offset++ % tasm.target.wordSize != 0) {
                masm.nop();
            }
        }
    }

    @Opcode("CALL_INDIRECT")
    public static class IndirectCallOp extends AMD64LIRInstruction implements StandardOp.CallOp {
        @Def({REG, ILLEGAL}) protected Value result;
        @Use({REG, STACK}) protected Value[] parameters;
        @Use({REG}) protected Value targetAddress;
        @Temp protected Value[] temps;
        @State protected LIRFrameState state;

        protected final Object targetMethod;

        public IndirectCallOp(Object targetMethod, Value result, Value[] parameters, Value[] temps, Value targetAddress, LIRFrameState state) {
            this.targetMethod = targetMethod;
            this.result = result;
            this.parameters = parameters;
            this.targetAddress = targetAddress;
            this.state = state;
            this.temps = temps;
            assert temps != null;
        }

        @Override
        public void emitCode(TargetMethodAssembler tasm, AMD64MacroAssembler masm) {
            indirectCall(tasm, masm, asRegister(targetAddress), targetMethod, state);
        }

        @Override
        protected void verify() {
            super.verify();
            assert isRegister(targetAddress) : "The current register allocator cannot handle variables to be used at call sites, it must be in a fixed register for now";
        }
    }

    public static void directCall(TargetMethodAssembler tasm, AMD64MacroAssembler masm, Object target, LIRFrameState info) {
        int before = masm.codeBuffer.position();
        if (target instanceof RuntimeCall) {
            long maxOffset = tasm.runtime.getMaxCallTargetOffset((RuntimeCall) target);
            if (maxOffset != (int) maxOffset) {
                // offset might not fit a 32-bit immediate, generate an
                // indirect call with a 64-bit immediate
                Register scratch = tasm.frameMap.registerConfig.getScratchRegister();
                // TODO (cwimmer): we want to get rid of a generally reserved scratch register.
                masm.movq(scratch, 0L);
                masm.call(scratch);
            } else {
                masm.call();
            }
        } else {
            masm.call();
        }
        int after = masm.codeBuffer.position();
        tasm.recordDirectCall(before, after, tasm.runtime.asCallTarget(target), info);
        tasm.recordExceptionHandlers(after, info);
        masm.ensureUniquePC();
    }

    public static void directJmp(TargetMethodAssembler tasm, AMD64MacroAssembler masm, Object target) {
        int before = masm.codeBuffer.position();
        masm.jmp(0, true);
        int after = masm.codeBuffer.position();
        tasm.recordDirectCall(before, after, tasm.runtime.asCallTarget(target), null);
        masm.ensureUniquePC();
    }

    public static void indirectCall(TargetMethodAssembler tasm, AMD64MacroAssembler masm, Register dst, Object target, LIRFrameState info) {
        int before = masm.codeBuffer.position();
        masm.call(dst);
        int after = masm.codeBuffer.position();
        tasm.recordIndirectCall(before, after, tasm.runtime.asCallTarget(target), info);
        tasm.recordExceptionHandlers(after, info);
        masm.ensureUniquePC();
    }

    public static void shouldNotReachHere(TargetMethodAssembler tasm, AMD64MacroAssembler masm) {
        boolean assertions = false;
        assert (assertions = true) == true;

        if (assertions) {
            directCall(tasm, masm, RuntimeCall.Debug, null);
            masm.hlt();
        }
    }
}
