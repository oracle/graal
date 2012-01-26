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
package com.oracle.max.graal.compiler.target.amd64;

import static com.oracle.max.cri.ci.CiValueUtil.*;

import java.util.*;

import com.oracle.max.asm.target.amd64.*;
import com.oracle.max.cri.ci.*;
import com.oracle.max.cri.ci.CiTargetMethod.Mark;
import com.oracle.max.cri.xir.CiXirAssembler.XirMark;
import com.oracle.max.graal.compiler.*;
import com.oracle.max.graal.compiler.asm.*;
import com.oracle.max.graal.compiler.lir.*;
import com.oracle.max.graal.compiler.util.*;

public class AMD64Call {

    public static class DirectCallOp extends AMD64LIRInstruction implements StandardOp.CallOp {
        private final Object targetMethod;
        private final Map<XirMark, Mark> marks;

        public DirectCallOp(Object targetMethod, CiValue result, CiValue[] parameters, LIRDebugInfo info, Map<XirMark, Mark> marks) {
            super("CALL_DIRECT", new CiValue[] {result}, info, parameters, LIRInstruction.NO_OPERANDS, LIRInstruction.NO_OPERANDS);
            this.targetMethod = targetMethod;
            this.marks = marks;
        }

        @Override
        public void emitCode(TargetMethodAssembler tasm, AMD64MacroAssembler masm) {
            callAlignment(tasm, masm);
            if (marks != null) {
                marks.put(XirMark.CALLSITE, tasm.recordMark(null, new Mark[0]));
            }
            directCall(tasm, masm, targetMethod, info);
        }

        @Override
        protected EnumSet<OperandFlag> flagsFor(OperandMode mode, int index) {
            if (mode == OperandMode.Input) {
                return EnumSet.of(OperandFlag.Register, OperandFlag.Stack);
            } else if (mode == OperandMode.Output) {
                return EnumSet.of(OperandFlag.Register, OperandFlag.Illegal);
            }
            throw Util.shouldNotReachHere();
        }
    }

    public static class IndirectCallOp extends AMD64LIRInstruction implements StandardOp.CallOp {
        private final Object targetMethod;
        private final Map<XirMark, Mark> marks;

        private static CiValue[] concat(CiValue[] parameters, CiValue targetAddress) {
            CiValue[] result = Arrays.copyOf(parameters, parameters.length + 1);
            result[result.length - 1] = targetAddress;
            return result;
        }

        public IndirectCallOp(Object targetMethod, CiValue result, CiValue[] parameters, CiValue targetAddress, LIRDebugInfo info, Map<XirMark, Mark> marks) {
            super("CALL_INDIRECT", new CiValue[] {result}, info, concat(parameters, targetAddress), LIRInstruction.NO_OPERANDS, LIRInstruction.NO_OPERANDS);
            this.targetMethod = targetMethod;
            this.marks = marks;
        }

        private CiValue targetAddress() {
            return input(inputs.length - 1);
        }

        @Override
        public void emitCode(TargetMethodAssembler tasm, AMD64MacroAssembler masm) {
            callAlignment(tasm, masm);
            if (marks != null) {
                marks.put(XirMark.CALLSITE, tasm.recordMark(null, new Mark[0]));
            }
            indirectCall(tasm, masm, asRegister(targetAddress()), targetMethod, info);
        }

        @Override
        protected EnumSet<OperandFlag> flagsFor(OperandMode mode, int index) {
            if (mode == OperandMode.Input) {
                return EnumSet.of(OperandFlag.Register, OperandFlag.Stack);
            } else if (mode == OperandMode.Output) {
                return EnumSet.of(OperandFlag.Register, OperandFlag.Illegal);
            }
            throw Util.shouldNotReachHere();
        }
    }


    public static void callAlignment(TargetMethodAssembler tasm, AMD64MacroAssembler masm) {
        if (GraalOptions.AlignCallsForPatching) {
            // make sure that the displacement word of the call ends up word aligned
            int offset = masm.codeBuffer.position();
            offset += tasm.target.arch.machineCodeCallDisplacementOffset;
            while (offset++ % tasm.target.wordSize != 0) {
                masm.nop();
            }
        }
    }

    public static void directCall(TargetMethodAssembler tasm, AMD64MacroAssembler masm, Object target, LIRDebugInfo info) {
        int before = masm.codeBuffer.position();
        if (target instanceof CiRuntimeCall) {
            long maxOffset = tasm.runtime.getMaxCallTargetOffset((CiRuntimeCall) target);
            if (maxOffset != (int) maxOffset) {
                // offset might not fit a 32-bit immediate, generate an
                // indirect call with a 64-bit immediate
                CiRegister scratch = tasm.frameMap.registerConfig.getScratchRegister();
                // TODO(cwi): we want to get rid of a generally reserved scratch register.
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

    public static void indirectCall(TargetMethodAssembler tasm, AMD64MacroAssembler masm, CiRegister dst, Object target, LIRDebugInfo info) {
        int before = masm.codeBuffer.position();
        masm.call(dst);
        int after = masm.codeBuffer.position();
        tasm.recordIndirectCall(before, after, tasm.runtime.asCallTarget(target), info);
        tasm.recordExceptionHandlers(after, info);
        masm.ensureUniquePC();
    }

    public static void shouldNotReachHere(TargetMethodAssembler tasm, AMD64MacroAssembler masm) {
        if (GraalOptions.GenAssertionCode) {
            directCall(tasm, masm, CiRuntimeCall.Debug, null);
            masm.hlt();
        }
    }
}
