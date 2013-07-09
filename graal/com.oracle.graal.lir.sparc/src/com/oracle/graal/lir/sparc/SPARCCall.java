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
package com.oracle.graal.lir.sparc;

import static com.oracle.graal.api.code.ValueUtil.*;
import static com.oracle.graal.lir.LIRInstruction.OperandFlag.*;
import static com.oracle.graal.asm.sparc.SPARCMacroAssembler.*;
import static com.oracle.graal.sparc.SPARC.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.asm.sparc.*;
import com.oracle.graal.lir.*;
import com.oracle.graal.lir.asm.*;
import com.oracle.graal.nodes.spi.*;

public class SPARCCall {

    public abstract static class CallOp extends SPARCLIRInstruction {

        @Def({REG, ILLEGAL}) protected Value result;
        @Use({REG, STACK}) protected Value[] parameters;
        @Temp protected Value[] temps;
        @State protected LIRFrameState state;

        public CallOp(Value result, Value[] parameters, Value[] temps, LIRFrameState state) {
            this.result = result;
            this.parameters = parameters;
            this.state = state;
            this.temps = temps;
            assert temps != null;
        }

        @Override
        public boolean destroysCallerSavedRegisters() {
            return true;
        }
    }

    public abstract static class MethodCallOp extends CallOp {

        protected final ResolvedJavaMethod callTarget;

        public MethodCallOp(ResolvedJavaMethod callTarget, Value result, Value[] parameters, Value[] temps, LIRFrameState state) {
            super(result, parameters, temps, state);
            this.callTarget = callTarget;
        }

    }

    @Opcode("CALL_DIRECT")
    public static class DirectCallOp extends MethodCallOp {

        public DirectCallOp(ResolvedJavaMethod callTarget, Value result, Value[] parameters, Value[] temps, LIRFrameState state) {
            super(callTarget, result, parameters, temps, state);
        }

        @Override
        public void emitCode(TargetMethodAssembler tasm, SPARCMacroAssembler masm) {
            directCall(tasm, masm, callTarget, null, true, state);
        }
    }

    @Opcode("CALL_INDIRECT")
    public static class IndirectCallOp extends MethodCallOp {

        @Use({REG}) protected Value targetAddress;

        public IndirectCallOp(ResolvedJavaMethod callTarget, Value result, Value[] parameters, Value[] temps, Value targetAddress, LIRFrameState state) {
            super(callTarget, result, parameters, temps, state);
            this.targetAddress = targetAddress;
        }

        @Override
        public void emitCode(TargetMethodAssembler tasm, SPARCMacroAssembler masm) {
            indirectCall(tasm, masm, asRegister(targetAddress), callTarget, state);
        }

        @Override
        protected void verify() {
            super.verify();
            assert isRegister(targetAddress) : "The current register allocator cannot handle variables to be used at call sites, it must be in a fixed register for now";
        }
    }

    public abstract static class ForeignCallOp extends CallOp {

        protected final ForeignCallLinkage callTarget;

        public ForeignCallOp(ForeignCallLinkage callTarget, Value result, Value[] parameters, Value[] temps, LIRFrameState state) {
            super(result, parameters, temps, state);
            this.callTarget = callTarget;
        }

        @Override
        public boolean destroysCallerSavedRegisters() {
            return callTarget.destroysRegisters();
        }
    }

    @Opcode("NEAR_FOREIGN_CALL")
    public static class DirectNearForeignCallOp extends ForeignCallOp {

        public DirectNearForeignCallOp(ForeignCallLinkage linkage, Value result, Value[] parameters, Value[] temps, LIRFrameState state) {
            super(linkage, result, parameters, temps, state);
        }

        @Override
        public void emitCode(TargetMethodAssembler tasm, SPARCMacroAssembler masm) {
            directCall(tasm, masm, callTarget, null, false, state);
        }
    }

    @Opcode("FAR_FOREIGN_CALL")
    public static class DirectFarForeignCallOp extends ForeignCallOp {

        @Temp({REG}) protected AllocatableValue callTemp;

        public DirectFarForeignCallOp(LIRGeneratorTool gen, ForeignCallLinkage callTarget, Value result, Value[] parameters, Value[] temps, LIRFrameState state) {
            super(callTarget, result, parameters, temps, state);
            callTemp = gen.newVariable(Kind.Long);
        }

        @Override
        public void emitCode(TargetMethodAssembler tasm, SPARCMacroAssembler masm) {
            directCall(tasm, masm, callTarget, ((RegisterValue) callTemp).getRegister(), false, state);
        }
    }

    public static void directCall(TargetMethodAssembler tasm, SPARCMacroAssembler masm, InvokeTarget callTarget, Register scratch, boolean align, LIRFrameState info) {
        if (align) {
            // We don't need alignment on SPARC.
        }
        int before = masm.codeBuffer.position();
        if (scratch != null) {
// // offset might not fit a 32-bit immediate, generate an
// // indirect call with a 64-bit immediate
// masm.movq(scratch, 0L);
// masm.call(scratch);
// } else {
// masm.call();
        }
        new Call(0).emit(masm);
        int after = masm.codeBuffer.position();
        tasm.recordDirectCall(before, after, callTarget, info);
        tasm.recordExceptionHandlers(after, info);
// masm.ensureUniquePC();
        new Nop().emit(masm);  // delay slot
    }

    public static void directJmp(TargetMethodAssembler tasm, SPARCMacroAssembler masm, InvokeTarget target) {
        int before = masm.codeBuffer.position();
// masm.jmp(0, true);
        int after = masm.codeBuffer.position();
        tasm.recordDirectCall(before, after, target, null);
// masm.ensureUniquePC();
        new Nop().emit(masm);  // delay slot
        throw new InternalError("NYI");
    }

    public static void indirectCall(TargetMethodAssembler tasm, SPARCMacroAssembler masm, Register dst, InvokeTarget callTarget, LIRFrameState info) {
        int before = masm.codeBuffer.position();
        new Jmpl(dst, 0, r15).emit(masm);
        int after = masm.codeBuffer.position();
        tasm.recordIndirectCall(before, after, callTarget, info);
        tasm.recordExceptionHandlers(after, info);
// masm.ensureUniquePC();
        new Nop().emit(masm);  // delay slot
    }
}
