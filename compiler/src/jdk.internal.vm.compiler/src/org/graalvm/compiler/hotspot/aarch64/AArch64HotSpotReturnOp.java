/*
 * Copyright (c) 2013, 2021, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.hotspot.aarch64;

import static jdk.vm.ci.aarch64.AArch64.lr;
import static jdk.vm.ci.aarch64.AArch64.r0;
import static jdk.vm.ci.aarch64.AArch64.v0;
import static jdk.vm.ci.code.ValueUtil.asRegister;
import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.ILLEGAL;
import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.REG;

import org.graalvm.compiler.asm.aarch64.AArch64MacroAssembler;
import org.graalvm.compiler.hotspot.GraalHotSpotVMConfig;
import org.graalvm.compiler.lir.LIRInstructionClass;
import org.graalvm.compiler.lir.Opcode;
import org.graalvm.compiler.lir.asm.CompilationResultBuilder;
import org.graalvm.compiler.lir.gen.DiagnosticLIRGeneratorTool.ZapStackArgumentSpaceBeforeInstruction;

import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.Value;

/**
 * Returns from a function.
 */
@Opcode("RETURN")
public final class AArch64HotSpotReturnOp extends AArch64HotSpotEpilogueOp implements ZapStackArgumentSpaceBeforeInstruction {

    public static final LIRInstructionClass<AArch64HotSpotReturnOp> TYPE = LIRInstructionClass.create(AArch64HotSpotReturnOp.class);

    @Use({REG, ILLEGAL}) private Value result;
    private final boolean isStub;
    private final boolean requiresReservedStackAccessCheck;

    public AArch64HotSpotReturnOp(Value result, boolean isStub, GraalHotSpotVMConfig config, Register thread, boolean requiresReservedStackAccessCheck) {
        super(TYPE, config, thread);
        this.requiresReservedStackAccessCheck = requiresReservedStackAccessCheck;
        assert validReturnValue(result);
        this.result = result;
        this.isStub = isStub;
    }

    private static boolean validReturnValue(Value result) {
        if (result.equals(Value.ILLEGAL)) {
            return true;
        }
        // The result must be in either register r0 or v0.
        Register reg = asRegister(result);
        return reg.equals(r0) || reg.equals(v0);
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
        final boolean emitSafepoint = !isStub;
        leaveFrame(crb, masm, emitSafepoint, requiresReservedStackAccessCheck);
        masm.ret(lr);
        crb.frameContext.returned(crb);
    }
}
