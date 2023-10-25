/*
 * Copyright (c) 2016, 2023, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.hotspot.aarch64;

import static jdk.vm.ci.code.ValueUtil.asRegister;

import java.util.function.Function;

import jdk.graal.compiler.asm.aarch64.AArch64Assembler;
import jdk.graal.compiler.asm.aarch64.AArch64MacroAssembler;
import jdk.graal.compiler.core.common.calc.Condition;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.lir.LIRInstructionClass;
import jdk.graal.compiler.lir.LabelRef;
import jdk.graal.compiler.lir.SwitchStrategy;
import jdk.graal.compiler.lir.aarch64.AArch64ControlFlow;
import jdk.graal.compiler.lir.asm.CompilationResultBuilder;

import jdk.vm.ci.code.Register;
import jdk.vm.ci.hotspot.HotSpotMetaspaceConstant;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.Constant;

final class AArch64HotSpotStrategySwitchOp extends AArch64ControlFlow.StrategySwitchOp {
    public static final LIRInstructionClass<AArch64HotSpotStrategySwitchOp> TYPE = LIRInstructionClass.create(AArch64HotSpotStrategySwitchOp.class);

    AArch64HotSpotStrategySwitchOp(SwitchStrategy strategy, LabelRef[] keyTargets, LabelRef defaultTarget, AllocatableValue key, Function<Condition, AArch64Assembler.ConditionFlag> converter) {
        super(TYPE, strategy, keyTargets, defaultTarget, key, converter);
    }

    @Override
    public void emitCode(final CompilationResultBuilder crb, final AArch64MacroAssembler masm) {
        strategy.run(new HotSpotSwitchClosure(asRegister(key), crb, masm));
    }

    public class HotSpotSwitchClosure extends SwitchClosure {

        protected HotSpotSwitchClosure(Register keyRegister, CompilationResultBuilder crb, AArch64MacroAssembler masm) {
            super(keyRegister, crb, masm);
        }

        @Override
        protected void emitComparison(Constant c) {
            if (c instanceof HotSpotMetaspaceConstant) {
                HotSpotMetaspaceConstant meta = (HotSpotMetaspaceConstant) c;
                crb.recordInlineDataInCode(meta);
                if (meta.isCompressed()) {
                    throw GraalError.unimplemented("compressed metaspace constant"); // ExcludeFromJacocoGeneratedReport
                } else {
                    try (AArch64MacroAssembler.ScratchRegister scratchRegister = masm.getScratchRegister()) {
                        Register scratch = scratchRegister.getRegister();
                        masm.movNativeAddress(scratch, 0x0000_DEAD_DEAD_DEADL);
                        masm.cmp(64, keyRegister, scratch);
                    }
                }
            } else {
                super.emitComparison(c);
            }
        }
    }
}
