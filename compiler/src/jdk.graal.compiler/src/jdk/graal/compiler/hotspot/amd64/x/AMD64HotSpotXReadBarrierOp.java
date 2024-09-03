/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.hotspot.amd64.x;

import static jdk.vm.ci.code.ValueUtil.asRegister;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.AMD64RMOp.MOV;

import jdk.graal.compiler.asm.amd64.AMD64BaseAssembler;
import jdk.graal.compiler.asm.amd64.AMD64MacroAssembler;
import jdk.graal.compiler.core.common.spi.ForeignCallLinkage;
import jdk.graal.compiler.hotspot.GraalHotSpotVMConfig;
import jdk.graal.compiler.lir.LIRFrameState;
import jdk.graal.compiler.lir.LIRInstructionClass;
import jdk.graal.compiler.lir.Variable;
import jdk.graal.compiler.lir.amd64.AMD64AddressValue;
import jdk.graal.compiler.lir.asm.CompilationResultBuilder;

import jdk.vm.ci.code.Register;

public class AMD64HotSpotXReadBarrierOp extends AMD64HotSpotXBarrieredOp {
    public static final LIRInstructionClass<AMD64HotSpotXReadBarrierOp> TYPE = LIRInstructionClass.create(AMD64HotSpotXReadBarrierOp.class);

    @State protected LIRFrameState state;

    public AMD64HotSpotXReadBarrierOp(Variable result, AMD64AddressValue loadAddress, LIRFrameState state, GraalHotSpotVMConfig config, ForeignCallLinkage callTarget) {
        super(TYPE, result, loadAddress, config, callTarget);
        this.state = state;
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
        if (state != null) {
            crb.recordImplicitException(masm.position(), state);
        }
        final Register resultReg = asRegister(result);
        MOV.emit(masm, AMD64BaseAssembler.OperandSize.QWORD, resultReg, loadAddress.toAddress());
        emitBarrier(crb, masm);
    }
}
