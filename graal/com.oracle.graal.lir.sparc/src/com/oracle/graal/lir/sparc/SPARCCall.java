/*
 * Copyright (c) 2013, 2015, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.jvmci.code.ForeignCallLinkage;
import com.oracle.jvmci.code.Register;
import com.oracle.jvmci.meta.ResolvedJavaMethod;
import com.oracle.jvmci.meta.InvokeTarget;
import com.oracle.jvmci.meta.Value;

import static com.oracle.jvmci.code.ValueUtil.*;
import static com.oracle.jvmci.sparc.SPARC.*;
import static com.oracle.graal.lir.LIRInstruction.OperandFlag.*;

import com.oracle.graal.asm.sparc.*;
import com.oracle.graal.asm.sparc.SPARCMacroAssembler.*;
import com.oracle.graal.lir.*;
import com.oracle.graal.lir.asm.*;
import com.oracle.jvmci.common.*;

public class SPARCCall {

    public abstract static class CallOp extends SPARCLIRInstruction {
        public static final LIRInstructionClass<CallOp> TYPE = LIRInstructionClass.create(CallOp.class);

        @Def({REG, ILLEGAL}) protected Value result;
        @Use({REG, STACK}) protected Value[] parameters;
        @Temp protected Value[] temps;
        @State protected LIRFrameState state;

        protected CallOp(LIRInstructionClass<? extends CallOp> c, Value result, Value[] parameters, Value[] temps, LIRFrameState state) {
            super(c);
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
        public static final LIRInstructionClass<MethodCallOp> TYPE = LIRInstructionClass.create(MethodCallOp.class);

        protected final ResolvedJavaMethod callTarget;

        protected MethodCallOp(LIRInstructionClass<? extends MethodCallOp> c, ResolvedJavaMethod callTarget, Value result, Value[] parameters, Value[] temps, LIRFrameState state) {
            super(c, result, parameters, temps, state);
            this.callTarget = callTarget;
        }

    }

    @Opcode("CALL_DIRECT")
    public abstract static class DirectCallOp extends MethodCallOp /*
                                                                    * implements
                                                                    * SPARCDelayedControlTransfer
                                                                    */{
        public static final LIRInstructionClass<DirectCallOp> TYPE = LIRInstructionClass.create(DirectCallOp.class);
        private boolean emitted = false;
        private int before = -1;

        public DirectCallOp(LIRInstructionClass<? extends DirectCallOp> c, ResolvedJavaMethod callTarget, Value result, Value[] parameters, Value[] temps, LIRFrameState state) {
            super(c, callTarget, result, parameters, temps, state);
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, SPARCMacroAssembler masm) {
            if (!emitted) {
                emitCallPrefixCode(crb, masm);
                directCall(crb, masm, callTarget, null, true, state);
            } else {
                int after = masm.position();
                if (after - before == 4) {
                    masm.nop();
                } else if (after - before == 8) {
                    // everything is fine;
                } else {
                    JVMCIError.shouldNotReachHere("" + (after - before));
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
            masm.call(0);
            emitted = true;
        }

        public void resetState() {
            emitted = false;
            before = -1;
        }
    }

    @Opcode("CALL_INDIRECT")
    public abstract static class IndirectCallOp extends MethodCallOp {
        public static final LIRInstructionClass<IndirectCallOp> TYPE = LIRInstructionClass.create(IndirectCallOp.class);

        @Use({REG}) protected Value targetAddress;

        protected IndirectCallOp(LIRInstructionClass<? extends IndirectCallOp> c, ResolvedJavaMethod callTarget, Value result, Value[] parameters, Value[] temps, Value targetAddress,
                        LIRFrameState state) {
            super(c, callTarget, result, parameters, temps, state);
            this.targetAddress = targetAddress;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, SPARCMacroAssembler masm) {
            indirectCall(crb, masm, asRegister(targetAddress), callTarget, state);
        }

        @Override
        public void verify() {
            super.verify();
            assert isRegister(targetAddress) : "The current register allocator cannot handle variables to be used at call sites, it must be in a fixed register for now";
        }
    }

    public abstract static class ForeignCallOp extends CallOp {
        public static final LIRInstructionClass<ForeignCallOp> TYPE = LIRInstructionClass.create(ForeignCallOp.class);

        protected final ForeignCallLinkage callTarget;

        public ForeignCallOp(LIRInstructionClass<? extends ForeignCallOp> c, ForeignCallLinkage callTarget, Value result, Value[] parameters, Value[] temps, LIRFrameState state) {
            super(c, result, parameters, temps, state);
            this.callTarget = callTarget;
        }

        @Override
        public boolean destroysCallerSavedRegisters() {
            return callTarget.destroysRegisters();
        }
    }

    @Opcode("NEAR_FOREIGN_CALL")
    public static final class DirectNearForeignCallOp extends ForeignCallOp {
        public static final LIRInstructionClass<DirectNearForeignCallOp> TYPE = LIRInstructionClass.create(DirectNearForeignCallOp.class);

        public DirectNearForeignCallOp(ForeignCallLinkage linkage, Value result, Value[] parameters, Value[] temps, LIRFrameState state) {
            super(TYPE, linkage, result, parameters, temps, state);
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, SPARCMacroAssembler masm) {
            directCall(crb, masm, callTarget, null, false, state);
        }
    }

    @Opcode("FAR_FOREIGN_CALL")
    public static final class DirectFarForeignCallOp extends ForeignCallOp {
        public static final LIRInstructionClass<DirectFarForeignCallOp> TYPE = LIRInstructionClass.create(DirectFarForeignCallOp.class);

        public DirectFarForeignCallOp(ForeignCallLinkage callTarget, Value result, Value[] parameters, Value[] temps, LIRFrameState state) {
            super(TYPE, callTarget, result, parameters, temps, state);
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
            masm.jmpl(scratch, 0, o7);
        } else {
            masm.call(0);
        }
        masm.nop();  // delay slot
        int after = masm.position();
        crb.recordDirectCall(before, after, callTarget, info);
        crb.recordExceptionHandlers(after, info);
        masm.ensureUniquePC();
    }

    public static void indirectJmp(CompilationResultBuilder crb, SPARCMacroAssembler masm, Register dst, InvokeTarget target) {
        int before = masm.position();
        new Sethix(0L, dst, true).emit(masm);
        masm.jmp(new SPARCAddress(dst, 0));
        masm.nop();  // delay slot
        int after = masm.position();
        crb.recordIndirectCall(before, after, target, null);
        masm.ensureUniquePC();
    }

    public static void indirectCall(CompilationResultBuilder crb, SPARCMacroAssembler masm, Register dst, InvokeTarget callTarget, LIRFrameState info) {
        int before = masm.position();
        masm.jmpl(dst, 0, o7);
        masm.nop();  // delay slot
        int after = masm.position();
        crb.recordIndirectCall(before, after, callTarget, info);
        crb.recordExceptionHandlers(after, info);
        masm.ensureUniquePC();
    }
}
