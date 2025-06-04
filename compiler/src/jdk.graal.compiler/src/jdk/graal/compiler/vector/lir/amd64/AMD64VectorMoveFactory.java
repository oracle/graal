/*
 * Copyright (c) 2015, 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.vector.lir.amd64;

import static jdk.graal.compiler.lir.LIRValueUtil.asConstant;
import static jdk.graal.compiler.lir.LIRValueUtil.isConstantValue;
import static jdk.graal.compiler.lir.LIRValueUtil.isStackSlotValue;
import static jdk.vm.ci.code.ValueUtil.isRegister;

import jdk.graal.compiler.asm.amd64.AMD64Assembler.AMD64SIMDInstructionEncoding;
import jdk.graal.compiler.core.amd64.AMD64MoveFactoryBase;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.lir.LIRInstruction;
import jdk.graal.compiler.lir.LIRValueUtil;
import jdk.graal.compiler.lir.amd64.AMD64LIRInstruction;
import jdk.graal.compiler.lir.amd64.vector.AMD64VectorMove;
import jdk.graal.compiler.vector.nodes.simd.SimdConstant;
import jdk.vm.ci.amd64.AMD64Kind;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.RegisterValue;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.Value;

public class AMD64VectorMoveFactory extends AMD64MoveFactoryBase {

    private final AMD64MoveFactoryBase baseMoveFactory;

    private final AMD64SIMDInstructionEncoding encoding;

    public AMD64VectorMoveFactory(AMD64MoveFactoryBase baseMoveFactory, BackupSlotProvider backupSlotProvider, AMD64SIMDInstructionEncoding encoding) {
        super(backupSlotProvider);
        this.baseMoveFactory = baseMoveFactory;
        this.encoding = encoding;
    }

    @Override
    public boolean mayEmbedConstantLoad(Constant constant) {
        if (constant instanceof SimdConstant) {
            // Embedded as memory load
            return true;
        }
        return baseMoveFactory.mayEmbedConstantLoad(constant);
    }

    @Override
    public boolean canInlineConstant(Constant c) {
        if (c instanceof SimdConstant) {
            return false;
        } else {
            return baseMoveFactory.canInlineConstant(c);
        }
    }

    @Override
    public boolean allowConstantToStackMove(Constant constant) {
        if (constant instanceof SimdConstant) {
            return false;
        } else {
            return baseMoveFactory.allowConstantToStackMove(constant);
        }
    }

    @Override
    public LIRInstruction createMove(AllocatableValue dst, Value src) {
        if (isConstantValue(src)) {
            return createLoad(dst, asConstant(src));
        } else if (((AMD64Kind) src.getPlatformKind()).isXMM()) {
            if (isRegister(src) || isStackSlotValue(dst)) {
                return new AMD64VectorMove.MoveFromRegOp(dst, (AllocatableValue) src, encoding);
            } else {
                return new AMD64VectorMove.MoveToRegOp(dst, (AllocatableValue) src, encoding);
            }
        } else {
            return baseMoveFactory.createMove(dst, src);
        }
    }

    @Override
    public AMD64LIRInstruction createStackMove(AllocatableValue dst, AllocatableValue src, Register scratchRegister, AllocatableValue backupSlot) {
        assert dst.getPlatformKind().getSizeInBytes() <= src.getPlatformKind().getSizeInBytes() : "cannot move " + src + " into a larger Value " + dst;
        if (((AMD64Kind) src.getPlatformKind()).isXMM()) {
            return new AMD64VectorMove.StackMoveOp(dst, src, scratchRegister, backupSlot, encoding);
        } else {
            return baseMoveFactory.createStackMove(dst, src, scratchRegister, backupSlot);
        }
    }

    private LIRInstruction createLoad(AllocatableValue result, Constant constant, boolean stack) {
        if (constant instanceof SimdConstant) {
            if (stack) {
                throw GraalError.shouldNotReachHere("unexpected simd constant to stack move: " + constant); // ExcludeFromJacocoGeneratedReport
            }

            SimdConstant simd = (SimdConstant) constant;
            AMD64Kind kind = (AMD64Kind) result.getPlatformKind();

            /*
             * JVMCI doesn't have a 16-bit vector kind. Two-byte constants are assigned a 32-bit
             * kind instead. If we construct the constant value in a register (for all zeros or all
             * ones), this doesn't matter. If we emit a load from the constant area, we make sure to
             * pad two-byte constants to four bytes and load them with a four-byte load.
             */
            boolean specialCaseTwoBytes = kind.equals(AMD64Kind.V32_BYTE) && simd.getSerializedSize() == 2;
            GraalError.guarantee(specialCaseTwoBytes || kind.getVectorLength() <= simd.getVectorLength(), "vector length mismatch: %s vs. %s", kind, simd);

            if (simd.isDefaultForKind()) {
                return new AVXClearVectorConstant(result, simd, encoding);
            } else if (SimdConstant.isAllOnes(simd)) {
                return new AVXAllOnesOp(result, simd);
            } else {
                if (specialCaseTwoBytes) {
                    simd = padTwoByteConstant(simd);
                }
                return new AVXLoadConstantVectorOp(result, simd, kind.getScalar(), encoding);
            }
        } else if (constant instanceof JavaConstant) {
            JavaConstant jc = (JavaConstant) constant;
            if (jc.getJavaKind().isNumericFloat()) {
                if (!stack && SimdConstant.isAllOnes(jc) && (LIRValueUtil.isVariable(result) || result instanceof RegisterValue)) {
                    return new AVXAllOnesOp(result, jc);
                } else {
                    return new AMD64VectorMove.MoveFromConstOp(result, jc, encoding);
                }
            }
        }

        return null;
    }

    /**
     * Pad the given constant, which must have a size of two bytes, to four bytes by appending
     * bytes. The values of the padding bytes are irrelevant, as they should never be used.
     */
    private static SimdConstant padTwoByteConstant(SimdConstant simd) {
        GraalError.guarantee(simd.getVectorLength() == 2 && simd.getValue(0) instanceof JavaConstant javaConstant && javaConstant.getJavaKind() == JavaKind.Byte, "expected exactly two bytes: %s",
                        simd);
        return new SimdConstant(new Constant[]{simd.getValue(0), simd.getValue(1), JavaConstant.forByte((byte) 0xc0), JavaConstant.forByte((byte) 0xfe)});
    }

    @Override
    public LIRInstruction createLoad(AllocatableValue result, Constant input) {
        LIRInstruction load = createLoad(result, input, false);
        if (load != null) {
            return load;
        } else {
            return baseMoveFactory.createLoad(result, input);
        }
    }

    @Override
    public LIRInstruction createStackLoad(AllocatableValue result, Constant input) {
        LIRInstruction load = createLoad(result, input, true);
        if (load != null) {
            return load;
        } else {
            return baseMoveFactory.createStackLoad(result, input);
        }
    }
}
