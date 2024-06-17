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

import static jdk.vm.ci.amd64.AMD64Kind.QWORD;
import static jdk.vm.ci.code.ValueUtil.asRegister;

import jdk.graal.compiler.asm.amd64.AMD64MacroAssembler;
import jdk.graal.compiler.core.common.spi.ForeignCallLinkage;
import jdk.graal.compiler.hotspot.GraalHotSpotVMConfig;
import jdk.graal.compiler.lir.LIRInstructionClass;
import jdk.graal.compiler.lir.Variable;
import jdk.graal.compiler.lir.amd64.AMD64AddressValue;
import jdk.graal.compiler.lir.amd64.AMD64Move;
import jdk.graal.compiler.lir.asm.CompilationResultBuilder;

import jdk.vm.ci.meta.AllocatableValue;

/**
 * Code generation for atomic read and write with read barrier.
 */
public class AMD64HotSpotXAtomicReadAndWriteOp extends AMD64HotSpotXBarrieredOp {
    public static final LIRInstructionClass<AMD64HotSpotXAtomicReadAndWriteOp> TYPE = LIRInstructionClass.create(AMD64HotSpotXAtomicReadAndWriteOp.class);

    @Use protected AllocatableValue newValue;

    public AMD64HotSpotXAtomicReadAndWriteOp(Variable result, AMD64AddressValue loadAddress, AllocatableValue newValue, GraalHotSpotVMConfig config, ForeignCallLinkage callTarget) {
        super(TYPE, result, loadAddress, config, callTarget);
        this.newValue = newValue;
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
        AMD64Move.move(QWORD, crb, masm, result, newValue);
        masm.xchgq(asRegister(result), loadAddress.toAddress());
        emitBarrier(crb, masm);
    }
}
