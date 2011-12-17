/*
 * Copyright (c) 2011, 2011, Oracle and/or its affiliates. All rights reserved.
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

import java.util.*;

import com.oracle.max.asm.target.amd64.*;
import com.oracle.max.graal.compiler.*;
import com.oracle.max.graal.compiler.asm.*;
import com.oracle.max.graal.compiler.lir.*;
import com.oracle.max.graal.compiler.stub.*;
import com.oracle.max.graal.compiler.util.*;
import com.sun.cri.ci.*;
import com.sun.cri.ci.CiTargetMethod.Mark;
import com.sun.cri.xir.CiXirAssembler.XirMark;

public enum AMD64CallOpcode implements StandardOpcode.CallOpcode {
    DIRECT_CALL, INDIRECT_CALL, NATIVE_CALL;

    public LIRInstruction create(Object target, CiValue result, List<CiValue> arguments, CiValue targetAddress, LIRDebugInfo info, Map<XirMark, Mark> marks) {
        return new LIRCall(this, target, result, arguments, targetAddress, info, marks) {
            @Override
            public void emitCode(TargetMethodAssembler tasm) {
                emit(tasm, (AMD64MacroAssembler) tasm.asm, this);
            }

            @Override
            public boolean hasCall() {
                return true;
            }
        };
    }

    private void emit(TargetMethodAssembler tasm, AMD64MacroAssembler masm, LIRCall op) {
        switch (this) {
            case DIRECT_CALL: {
                callAlignment(tasm, masm);
                if (op.marks != null) {
                    op.marks.put(XirMark.CALLSITE, tasm.recordMark(null, new Mark[0]));
                }
                directCall(tasm, masm, op.target, op.info);
                break;
            }
            case INDIRECT_CALL: {
                callAlignment(tasm, masm);
                if (op.marks != null) {
                    op.marks.put(XirMark.CALLSITE, tasm.recordMark(null, new Mark[0]));
                }
                CiRegister reg = tasm.asRegister(op.targetAddress());
                indirectCall(tasm, masm, reg, op.target, op.info);
                break;
            }
            case NATIVE_CALL: {
                CiRegister reg = tasm.asRegister(op.targetAddress());
                indirectCall(tasm, masm, reg, op.target, op.info);
                break;
            }
            default:
                throw Util.shouldNotReachHere();
        }
    }


    public void callAlignment(TargetMethodAssembler tasm, AMD64MacroAssembler masm) {
        if (GraalOptions.AlignCallsForPatching) {
            // make sure that the displacement word of the call ends up word aligned
            int offset = masm.codeBuffer.position();
            offset += tasm.target.arch.machineCodeCallDisplacementOffset;
            while (offset++ % tasm.target.wordSize != 0) {
                masm.nop();
            }
        }
    }

    public static void callStub(TargetMethodAssembler tasm, AMD64MacroAssembler masm, CompilerStub stub, CiKind resultKind, LIRDebugInfo info, CiValue result, CiValue... args) {
        assert args.length == stub.inArgs.length;
        for (int i = 0; i < args.length; i++) {
            assert stub.inArgs[i].inCallerFrame();
            AMD64MoveOpcode.move(tasm, masm, stub.inArgs[i].asOutArg(), args[i]);
        }

        directCall(tasm, masm, stub.stubObject, info);

        if (result.isLegal()) {
            AMD64MoveOpcode.move(tasm, masm, result, stub.outResult.asOutArg());
        }

        // Clear out parameters
        if (GraalOptions.GenAssertionCode) {
            for (int i = 0; i < args.length; i++) {
                CiStackSlot inArg = stub.inArgs[i];
                CiStackSlot outArg = inArg.asOutArg();
                CiAddress dst = tasm.asAddress(outArg);
                masm.movptr(dst, 0);
            }
        }
    }

    public static void directCall(TargetMethodAssembler tasm, AMD64MacroAssembler masm, Object target, LIRDebugInfo info) {
        int before = masm.codeBuffer.position();
        if (target instanceof CiRuntimeCall) {
            long maxOffset = tasm.compilation.compiler.runtime.getMaxCallTargetOffset((CiRuntimeCall) target);
            if (maxOffset != (int) maxOffset) {
                // offset might not fit a 32-bit immediate, generate an
                // indirect call with a 64-bit immediate
                CiRegister scratch = tasm.compilation.registerConfig.getScratchRegister();
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
        tasm.recordDirectCall(before, after, asCallTarget(tasm, target), info);
        tasm.recordExceptionHandlers(after, info);
        masm.ensureUniquePC();
    }

    public static void directJmp(TargetMethodAssembler tasm, AMD64MacroAssembler masm, Object target) {
        int before = masm.codeBuffer.position();
        masm.jmp(0, true);
        int after = masm.codeBuffer.position();
        tasm.recordDirectCall(before, after, asCallTarget(tasm, target), null);
        masm.ensureUniquePC();
    }

    public static void indirectCall(TargetMethodAssembler tasm, AMD64MacroAssembler masm, CiRegister dst, Object target, LIRDebugInfo info) {
        int before = masm.codeBuffer.position();
        masm.call(dst);
        int after = masm.codeBuffer.position();
        tasm.recordIndirectCall(before, after, asCallTarget(tasm, target), info);
        tasm.recordExceptionHandlers(after, info);
        masm.ensureUniquePC();
    }

    private static Object asCallTarget(TargetMethodAssembler tasm, Object o) {
        return tasm.compilation.compiler.runtime.asCallTarget(o);
    }

    public static void shouldNotReachHere(TargetMethodAssembler tasm, AMD64MacroAssembler masm) {
        if (GraalOptions.GenAssertionCode) {
            directCall(tasm, masm, CiRuntimeCall.Debug, null);
            masm.hlt();
        }
    }
}
