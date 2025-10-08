/*
 * Copyright (c) 2015, 2022, Oracle and/or its affiliates. All rights reserved.
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

package jdk.graal.compiler.core.amd64;

import jdk.graal.compiler.lir.VirtualStackSlot;
import jdk.graal.compiler.lir.amd64.AMD64LIRInstruction;
import jdk.graal.compiler.lir.gen.MoveFactory;
import jdk.vm.ci.amd64.AMD64Kind;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.AllocatableValue;

public abstract class AMD64MoveFactoryBase extends MoveFactory {

    private final BackupSlotProvider backupSlotProvider;

    public AMD64MoveFactoryBase(BackupSlotProvider backupSlotProvider) {
        this.backupSlotProvider = backupSlotProvider;
    }

    @Override
    public final AMD64LIRInstruction createStackMove(AllocatableValue result, AllocatableValue input) {
        AMD64Kind backupKind = (AMD64Kind) input.getPlatformKind();
        if (backupKind.isXMM() && backupKind.getSizeInBytes() <= Long.BYTES) {
            // backup using a gp register
            backupKind = AMD64Kind.QWORD;
        }

        RegisterBackupPair backup = backupSlotProvider.getScratchRegister(backupKind, backupKind.isInteger() ? getPreferredGeneralPurposeScratchRegister() : null);
        Register scratchRegister = backup.register;
        VirtualStackSlot backupSlot = backup.backupSlot;
        return createStackMove(result, input, scratchRegister, backupSlot);
    }

    public abstract AMD64LIRInstruction createStackMove(AllocatableValue result, AllocatableValue input, Register scratchRegister, AllocatableValue backupSlot);

    /**
     * @return a concrete register as the preferred scratch register for integer AMD64Kind; or null
     *         if there is no preference
     */
    public Register getPreferredGeneralPurposeScratchRegister() {
        return null;
    }
}
