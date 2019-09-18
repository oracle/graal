/*
 * Copyright (c) 2014, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.graal.amd64;

import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.ILLEGAL;
import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.REG;

import org.graalvm.compiler.asm.Label;
import org.graalvm.compiler.asm.amd64.AMD64Address;
import org.graalvm.compiler.asm.amd64.AMD64Assembler.ConditionFlag;
import org.graalvm.compiler.asm.amd64.AMD64MacroAssembler;
import org.graalvm.compiler.lir.LIRInstructionClass;
import org.graalvm.compiler.lir.Opcode;
import org.graalvm.compiler.lir.amd64.AMD64BlockEndOp;
import org.graalvm.compiler.lir.asm.CompilationResultBuilder;

import com.oracle.svm.core.FrameAccess;
import com.oracle.svm.core.SubstrateOptions;

import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.code.ValueUtil;
import jdk.vm.ci.meta.AllocatableValue;

@Opcode("FAR_RETURN")
public final class AMD64FarReturnOp extends AMD64BlockEndOp {
    public static final LIRInstructionClass<AMD64FarReturnOp> TYPE = LIRInstructionClass.create(AMD64FarReturnOp.class);

    @Use({REG, ILLEGAL}) AllocatableValue result;
    @Use(REG) AllocatableValue sp;
    @Use(REG) AllocatableValue ip;

    public AMD64FarReturnOp(AllocatableValue result, AllocatableValue sp, AllocatableValue ip) {
        super(TYPE);
        this.result = result;
        this.sp = sp;
        this.ip = ip;
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
        if (SubstrateOptions.PreserveFramePointer.getValue()) {
            /*
             * We need to properly restore RBP to the value that matches the frame of the new stack
             * pointer. Two options: 1) When RSP is not changing, we are jumping within the same
             * frame - no adjustment of RBP necessary. 2) We jump to a frame earlier in the stack -
             * the corresponding RBP value was spilled to the stack by the callee.
             */
            Label done = new Label();
            masm.cmpq(AMD64.rsp, ValueUtil.asRegister(sp));
            masm.jcc(ConditionFlag.Equal, done);
            /*
             * The callee pushes two word-sized values: first the return address, then the saved
             * RBP. The stack grows downwards, so the offset is negative relative to the new stack
             * pointer.
             */
            masm.movq(AMD64.rbp, new AMD64Address(ValueUtil.asRegister(sp), -(FrameAccess.returnAddressSize() + FrameAccess.singleton().savedBasePointerSize())));
            masm.bind(done);
        }

        masm.movq(AMD64.rsp, ValueUtil.asRegister(sp));
        masm.jmp(ValueUtil.asRegister(ip));
    }
}
