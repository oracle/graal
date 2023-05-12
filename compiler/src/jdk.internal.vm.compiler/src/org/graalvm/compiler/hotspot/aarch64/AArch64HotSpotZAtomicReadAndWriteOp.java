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
package org.graalvm.compiler.hotspot.aarch64;

import static jdk.vm.ci.code.ValueUtil.asRegister;

import org.graalvm.compiler.asm.aarch64.AArch64Address;
import org.graalvm.compiler.asm.aarch64.AArch64MacroAssembler;
import org.graalvm.compiler.core.common.spi.ForeignCallLinkage;
import org.graalvm.compiler.hotspot.GraalHotSpotVMConfig;
import org.graalvm.compiler.lir.LIRInstructionClass;
import org.graalvm.compiler.lir.Variable;
import org.graalvm.compiler.lir.aarch64.AArch64AtomicMove;
import org.graalvm.compiler.lir.asm.CompilationResultBuilder;

import jdk.vm.ci.aarch64.AArch64Kind;
import jdk.vm.ci.meta.AllocatableValue;

/**
 * Code generation for atomic read and write with read barrier.
 */
public class AArch64HotSpotZAtomicReadAndWriteOp extends AArch64AtomicMove.AtomicReadAndWriteOp {
    public static final LIRInstructionClass<AArch64HotSpotZAtomicReadAndWriteOp> TYPE = LIRInstructionClass.create(AArch64HotSpotZAtomicReadAndWriteOp.class);
    private final GraalHotSpotVMConfig config;
    private final ForeignCallLinkage callTarget;

    public AArch64HotSpotZAtomicReadAndWriteOp(AArch64Kind platformKind, Variable result, AllocatableValue newValue, AllocatableValue asAllocatable, GraalHotSpotVMConfig config,
                    ForeignCallLinkage callTarget) {
        super(TYPE, platformKind, result, newValue, asAllocatable);
        this.config = config;
        this.callTarget = callTarget;
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
        super.emitCode(crb, masm);
        AArch64Address address = AArch64Address.createBaseRegisterOnlyAddress(64, asRegister(addressValue));
        AArch64HotSpotZBarrierSetLIRGenerator.emitBarrier(crb, masm, null, asRegister(resultValue), config, callTarget, address, this, null);
    }
}
