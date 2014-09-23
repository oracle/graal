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
import static com.oracle.graal.sparc.SPARC.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.asm.sparc.*;
import com.oracle.graal.asm.sparc.SPARCAssembler.Call;
import com.oracle.graal.asm.sparc.SPARCAssembler.Jmpl;
import com.oracle.graal.asm.sparc.SPARCMacroAssembler.Jmp;
import com.oracle.graal.asm.sparc.SPARCMacroAssembler.Nop;
import com.oracle.graal.asm.sparc.SPARCMacroAssembler.Sethix;
import com.oracle.graal.compiler.common.*;
import com.oracle.graal.lir.*;
import com.oracle.graal.lir.asm.*;

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
    public static class DirectCallOp extends MethodCallOp implements SPARCDelayedControlTransfer {
        private boolean emitted = false;
        private int before = -1;

        public DirectCallOp(ResolvedJavaMethod callTarget, Value result, Value[] parameters, Value[] temps, LIRFrameState state) {
            super(callTarget, result, parameters, temps, state);
        }

        @Override
        public final void emitCode(CompilationResultBuilder crb, SPARCMacroAssembler masm) {
            if (!emitted) {
                emitCallPrefixCode(crb, masm);
                directCall(crb, masm, callTarget, null, true, state);
            } else {
                int after = masm.position();
                if (after - before == 4) {
                    new Nop().emit(masm);
                } else if (after - before == 8) {
                    // everything is fine;
                } else {
                    GraalInternalError.shouldNotReachHere("" + (after - before));
                }
                after = masm.position();
                crb.recordDirectCall(before, after, callTarget, state);
                crb.recordExceptionHandlers(after, state);
                masm.ensureUniquePC();
            }
        }

        @SuppressWarnings("unused")
        public void emitCallPrefixCode(CompilationResultBuilder crb, SPARCMacroAssembler masm) {
            //
        }

        public void emitControlTransfer(CompilationResultBuilder crb, SPARCMacroAssembler masm) {
            assert !emitted;
            emitCallPrefixCode(crb, masm);
            before = masm.position();
            new Call(0).emit(masm);
            emitted = true;
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
        public void emitCode(CompilationResultBuilder crb, SPARCMacroAssembler masm) {
            indirectCall(crb, masm, asRegister(targetAddress), callTarget, state);
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
        public void emitCode(CompilationResultBuilder crb, SPARCMacroAssembler masm) {
            directCall(crb, masm, callTarget, null, false, state);
        }
    }

    @Opcode("FAR_FOREIGN_CALL")
    public static class DirectFarForeignCallOp extends ForeignCallOp {

        public DirectFarForeignCallOp(ForeignCallLinkage callTarget, Value result, Value[] parameters, Value[] temps, LIRFrameState state) {
            super(callTarget, result, parameters, temps, state);
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, SPARCMacroAssembler masm) {
            directCall(crb, masm, callTarget, o7, false, state);
        }
    }

    public static void directCall(CompilationResultBuilder crb, SPARCMacroAssembler masm, InvokeTarget callTarget, Register scratch, boolean align, LIRFrameState info) {
        if (align) {
            // We don't need alignment on SPARC.
        }
        int before = masm.position();
        if (scratch != null) {
            // offset might not fit a 30-bit displacement, generate an
            // indirect call with a 64-bit immediate
            new Sethix(0L, scratch, true).emit(masm);
            new Jmpl(scratch, 0, o7).emit(masm);
        } else {
            new Call(0).emit(masm);
        }
        new Nop().emit(masm);  // delay slot
        int after = masm.position();
        crb.recordDirectCall(before, after, callTarget, info);
        crb.recordExceptionHandlers(after, info);
        masm.ensureUniquePC();
    }

    public static void indirectJmp(CompilationResultBuilder crb, SPARCMacroAssembler masm, Register dst, InvokeTarget target) {
        int before = masm.position();
        new Sethix(0L, dst, true).emit(masm);
        new Jmp(new SPARCAddress(dst, 0)).emit(masm);
        new Nop().emit(masm);  // delay slot
        int after = masm.position();
        crb.recordIndirectCall(before, after, target, null);
        masm.ensureUniquePC();
    }

    public static void indirectCall(CompilationResultBuilder crb, SPARCMacroAssembler masm, Register dst, InvokeTarget callTarget, LIRFrameState info) {
        int before = masm.position();
        new Jmpl(dst, 0, o7).emit(masm);
        new Nop().emit(masm);  // delay slot
        int after = masm.position();
        crb.recordIndirectCall(before, after, callTarget, info);
        crb.recordExceptionHandlers(after, info);
        masm.ensureUniquePC();
    }
}
