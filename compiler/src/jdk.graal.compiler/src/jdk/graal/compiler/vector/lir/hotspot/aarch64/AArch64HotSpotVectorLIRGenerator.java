/*
 * Copyright (c) 2021, 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.vector.lir.hotspot.aarch64;

import jdk.graal.compiler.core.aarch64.AArch64ArithmeticLIRGenerator;
import jdk.graal.compiler.core.common.LIRKind;
import jdk.graal.compiler.core.common.calc.Condition;
import jdk.graal.compiler.core.common.spi.LIRKindTool;
import jdk.graal.compiler.hotspot.GraalHotSpotVMConfig;
import jdk.graal.compiler.hotspot.aarch64.AArch64HotSpotLIRGenerator;
import jdk.graal.compiler.hotspot.meta.HotSpotProviders;
import jdk.graal.compiler.lir.Variable;
import jdk.graal.compiler.lir.VirtualStackSlot;
import jdk.graal.compiler.lir.gen.LIRGenerationResult;
import jdk.graal.compiler.lir.gen.MoveFactory;
import jdk.graal.compiler.vector.lir.aarch64.AArch64VectorArithmeticLIRGenerator;
import jdk.graal.compiler.vector.nodes.simd.SimdConstant;
import jdk.vm.ci.aarch64.AArch64Kind;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.PlatformKind;
import jdk.vm.ci.meta.Value;

public class AArch64HotSpotVectorLIRGenerator extends AArch64HotSpotLIRGenerator {
    public AArch64HotSpotVectorLIRGenerator(LIRKindTool lirKindTool, AArch64ArithmeticLIRGenerator arithmeticLIRGen, MoveFactory moveFactory, HotSpotProviders providers, GraalHotSpotVMConfig config,
                    LIRGenerationResult lirGenRes) {
        super(lirKindTool, arithmeticLIRGen, getBarrierSet(config, providers), moveFactory, providers, config, lirGenRes);
    }

    @Override
    protected VirtualStackSlot allocateSaveRegisterLocation(Register register) {
        PlatformKind kind = target().arch.getLargestStorableKind(register.getRegisterCategory());
        return getResult().getFrameMapBuilder().allocateSpillSlot(LIRKind.value(kind));
    }

    @Override
    public Variable emitIntegerTestMove(Value left, Value right, Value trueValue, Value falseValue) {
        AArch64VectorArithmeticLIRGenerator vectorGen = (AArch64VectorArithmeticLIRGenerator) arithmeticLIRGen;
        Variable vectorResult = vectorGen.emitVectorIntegerTestMove(left, right, trueValue, falseValue);
        if (vectorResult != null) {
            return vectorResult;
        }
        return super.emitIntegerTestMove(left, right, trueValue, falseValue);
    }

    @Override
    public Variable emitConditionalMove(PlatformKind cmpKind, Value left, Value right, Condition cond, boolean unorderedIsTrue, Value trueValue, Value falseValue) {
        AArch64VectorArithmeticLIRGenerator vectorGen = (AArch64VectorArithmeticLIRGenerator) arithmeticLIRGen;
        Variable vectorResult = vectorGen.emitVectorConditionalMove(cmpKind, left, right, cond, unorderedIsTrue, trueValue, falseValue);
        if (vectorResult != null) {
            return vectorResult;
        }
        return super.emitConditionalMove(cmpKind, left, right, cond, unorderedIsTrue, trueValue, falseValue);
    }

    @Override
    public Value emitConstant(LIRKind kind, Constant constant) {
        int length = kind.getPlatformKind().getVectorLength();
        if (length == 1) {
            return super.emitConstant(kind, constant);
        } else if (constant instanceof SimdConstant simd) {
            /*
             * JVMCI doesn't have a 16-bit vector kind. Two-byte constants are assigned a 32-bit
             * kind instead.
             */
            boolean specialCaseTwoBytes = kind.getPlatformKind().equals(AArch64Kind.V32_BYTE) && simd.getSerializedSize() == 2;
            assert specialCaseTwoBytes || simd.getVectorLength() == length : constant + " " + length;
            return super.emitConstant(kind, simd);
        } else {
            return super.emitConstant(kind, SimdConstant.broadcast(constant, length));
        }
    }

    @Override
    public Variable emitReverseBytes(Value input) {
        AArch64VectorArithmeticLIRGenerator vectorGen = (AArch64VectorArithmeticLIRGenerator) arithmeticLIRGen;
        Variable vectorResult = vectorGen.emitVectorByteSwap(input);
        if (vectorResult != null) {
            return vectorResult;
        }
        return super.emitReverseBytes(input);
    }
}
