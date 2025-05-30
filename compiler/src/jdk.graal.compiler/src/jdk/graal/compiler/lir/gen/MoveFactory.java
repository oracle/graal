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
package jdk.graal.compiler.lir.gen;

import java.util.List;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.Equivalence;

import jdk.graal.compiler.core.common.LIRKind;
import jdk.graal.compiler.debug.Assertions;
import jdk.graal.compiler.lir.LIRInstruction;
import jdk.graal.compiler.lir.VirtualStackSlot;
import jdk.graal.compiler.lir.framemap.FrameMapBuilder;
import jdk.vm.ci.code.Architecture;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.RegisterConfig;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.PlatformKind;
import jdk.vm.ci.meta.Value;

/**
 * Factory for creating moves.
 */
public abstract class MoveFactory {

    /**
     * Checks whether the loading of the supplied constant can be deferred until usage.
     */
    @SuppressWarnings("unused")
    public boolean mayEmbedConstantLoad(Constant constant) {
        return false;
    }

    /**
     * Checks whether the supplied constant can be used without loading it into a register for most
     * operations, i.e., for commonly used arithmetic, logical, and comparison operations.
     *
     * @param constant The constant to check.
     * @return True if the constant can be used directly, false if the constant needs to be in a
     *         register.
     */
    public abstract boolean canInlineConstant(Constant constant);

    /**
     * @param constant The constant that might be moved to a stack slot.
     * @return {@code true} if constant to stack moves are supported for this constant.
     */
    public abstract boolean allowConstantToStackMove(Constant constant);

    public abstract LIRInstruction createMove(AllocatableValue result, Value input);

    public abstract LIRInstruction createStackMove(AllocatableValue result, AllocatableValue input);

    public abstract LIRInstruction createLoad(AllocatableValue result, Constant input);

    public abstract LIRInstruction createStackLoad(AllocatableValue result, Constant input);

    public static class RegisterBackupPair {
        public final Register register;
        public final VirtualStackSlot backupSlot;

        RegisterBackupPair(Register register, VirtualStackSlot backupSlot) {
            this.register = register;
            this.backupSlot = backupSlot;
        }
    }

    public static final class BackupSlotProvider {

        private final FrameMapBuilder frameMapBuilder;
        private EconomicMap<PlatformKind.Key, RegisterBackupPair> categorized;

        public BackupSlotProvider(FrameMapBuilder frameMapBuilder) {
            this.frameMapBuilder = frameMapBuilder;
        }

        public RegisterBackupPair getScratchRegister(PlatformKind kind) {
            PlatformKind.Key key = kind.getKey();
            if (categorized == null) {
                categorized = EconomicMap.create(Equivalence.DEFAULT);
            } else if (categorized.containsKey(key)) {
                return categorized.get(key);
            }

            RegisterConfig registerConfig = frameMapBuilder.getRegisterConfig();

            List<Register> availableRegister = registerConfig.filterAllocatableRegisters(kind, registerConfig.getAllocatableRegisters());
            assert availableRegister != null;
            assert availableRegister.size() > 1 : Assertions.errorMessageContext("availableReg", availableRegister);
            Register scratchRegister = availableRegister.get(0);

            Architecture arch = frameMapBuilder.getCodeCache().getTarget().arch;
            LIRKind largestKind = LIRKind.value(arch.getLargestStorableKind(scratchRegister.getRegisterCategory()));
            VirtualStackSlot backupSlot = frameMapBuilder.allocateSpillSlot(largestKind);

            RegisterBackupPair value = new RegisterBackupPair(scratchRegister, backupSlot);
            categorized.put(key, value);

            return value;
        }
    }
}
