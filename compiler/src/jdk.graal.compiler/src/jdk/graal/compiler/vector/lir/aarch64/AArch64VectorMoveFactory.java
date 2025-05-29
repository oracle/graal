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
package jdk.graal.compiler.vector.lir.aarch64;

import jdk.graal.compiler.core.aarch64.AArch64MoveFactory;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.lir.LIRInstruction;
import jdk.graal.compiler.lir.VirtualStackSlot;
import jdk.graal.compiler.lir.aarch64.AArch64LIRInstruction;
import jdk.graal.compiler.vector.nodes.simd.SimdConstant;
import jdk.vm.ci.aarch64.AArch64Kind;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.Value;

public class AArch64VectorMoveFactory extends AArch64MoveFactory {
    private final AArch64MoveFactory baseMoveFactory;
    private final BackupSlotProvider backupSlotProvider;

    public AArch64VectorMoveFactory(AArch64MoveFactory baseMoveFactory, BackupSlotProvider backupSlotProvider) {
        this.baseMoveFactory = baseMoveFactory;
        this.backupSlotProvider = backupSlotProvider;
    }

    @Override
    public boolean canInlineConstant(Constant constant) {
        if (constant instanceof SimdConstant) {
            /* Only comparisons against 0 can be inlined into operations. */
            return false;
        }
        return baseMoveFactory.canInlineConstant(constant);
    }

    @Override
    public boolean allowConstantToStackMove(Constant constant) {
        if (constant instanceof SimdConstant) {
            return false;
        }
        return baseMoveFactory.allowConstantToStackMove(constant);
    }

    @Override
    public LIRInstruction createMove(AllocatableValue result, Value input) {
        return baseMoveFactory.createMove(result, input);
    }

    @Override
    public LIRInstruction createStackMove(AllocatableValue result, AllocatableValue input) {
        /*
         * AArch64Move's stack2stack uses a gp register. Therefore, we have to reserve a floating
         * point scratch register when move is larger than 64 bits.
         */
        assert result.getPlatformKind().getSizeInBytes() <= input.getPlatformKind().getSizeInBytes() : "cannot move " + input + " into a larger Value " + result;
        AArch64Kind moveKind = (AArch64Kind) result.getPlatformKind();
        if (moveKind.getSizeInBytes() * Byte.SIZE > Long.SIZE) {
            RegisterBackupPair backup = backupSlotProvider.getScratchRegister(moveKind);
            Register scratchRegister = backup.register;
            VirtualStackSlot backupSlot = backup.backupSlot;
            return new AArch64ASIMDMove.StackMoveOp(result, input, scratchRegister, backupSlot);
        } else {
            return baseMoveFactory.createStackMove(result, input);
        }
    }

    /**
     * Wrapper to insert {@link AArch64ASIMDMove.LoadInlineConstant} for {@link SimdConstant}
     * values.
     */
    private static AArch64LIRInstruction createLoad(AllocatableValue result, Constant constant, boolean stack) {
        if (constant instanceof SimdConstant) {
            if (stack) {
                throw GraalError.shouldNotReachHere("Unexpected simd constant to stack move: " + constant); // ExcludeFromJacocoGeneratedReport
            }
            SimdConstant simd = (SimdConstant) constant;
            return new AArch64ASIMDMove.LoadInlineConstant(result, simd);
        }
        return null;
    }

    @Override
    public AArch64LIRInstruction createLoad(AllocatableValue result, Constant input) {
        AArch64LIRInstruction load = createLoad(result, input, false);
        if (load != null) {
            return load;
        }
        return baseMoveFactory.createLoad(result, input);
    }

    @Override
    public LIRInstruction createStackLoad(AllocatableValue result, Constant input) {
        LIRInstruction load = createLoad(result, input, true);
        if (load != null) {
            return load;
        }
        return baseMoveFactory.createStackLoad(result, input);
    }
}
