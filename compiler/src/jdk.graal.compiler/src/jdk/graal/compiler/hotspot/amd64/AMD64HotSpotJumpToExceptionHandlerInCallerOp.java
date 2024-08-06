/*
 * Copyright (c) 2012, 2019, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.hotspot.amd64;

import static jdk.vm.ci.amd64.AMD64.rsp;
import static jdk.vm.ci.code.ValueUtil.asRegister;
import static jdk.graal.compiler.lir.LIRInstruction.OperandFlag.REG;

import jdk.graal.compiler.asm.amd64.AMD64MacroAssembler;
import jdk.graal.compiler.lir.LIRInstructionClass;
import jdk.graal.compiler.lir.Opcode;
import jdk.graal.compiler.lir.asm.CompilationResultBuilder;

import jdk.vm.ci.meta.AllocatableValue;

/**
 * Sets up the arguments for an exception handler in the callers frame, removes the current frame
 * and jumps to the handler.
 */
@Opcode("JUMP_TO_EXCEPTION_HANDLER_IN_CALLER")
final class AMD64HotSpotJumpToExceptionHandlerInCallerOp extends AMD64HotSpotEpilogueBlockEndOp {

    public static final LIRInstructionClass<AMD64HotSpotJumpToExceptionHandlerInCallerOp> TYPE = LIRInstructionClass.create(AMD64HotSpotJumpToExceptionHandlerInCallerOp.class);

    @Use(REG) AllocatableValue handlerInCallerPc;
    @Use(REG) AllocatableValue exception;
    @Use(REG) AllocatableValue exceptionPc;

    AMD64HotSpotJumpToExceptionHandlerInCallerOp(AllocatableValue handlerInCallerPc, AllocatableValue exception, AllocatableValue exceptionPc) {
        super(TYPE);
        this.handlerInCallerPc = handlerInCallerPc;
        this.exception = exception;
        this.exceptionPc = exceptionPc;
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
        leaveFrameAndRestoreRbp(crb, masm);

        // Discard the return address, thus completing restoration of caller frame
        masm.incrementq(rsp, 8);
        masm.jmp(asRegister(handlerInCallerPc));
    }
}
