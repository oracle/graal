/*
 * Copyright (c) 2015, 2015, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.graal.lir.alloc.trace;

import static com.oracle.graal.lir.LIRInstruction.OperandFlag.REG;
import static com.oracle.graal.lir.LIRInstruction.OperandFlag.STACK;

import java.util.EnumSet;

import jdk.vm.ci.code.RegisterValue;
import jdk.vm.ci.meta.AllocatableValue;

import com.oracle.graal.lir.CompositeValue;
import com.oracle.graal.lir.InstructionValueConsumer;
import com.oracle.graal.lir.InstructionValueProcedure;
import com.oracle.graal.lir.LIRInstruction;
import com.oracle.graal.lir.LIRInstruction.OperandFlag;
import com.oracle.graal.lir.LIRInstruction.OperandMode;

/**
 * Represents a {@link #register} which has a shadow copy on the {@link #stackslot stack}.
 */
final class ShadowedRegisterValue extends CompositeValue {
    private static final EnumSet<OperandFlag> registerFlags = EnumSet.of(REG);
    private static final EnumSet<OperandFlag> stackslotFlags = EnumSet.of(STACK);

    @Component({REG}) protected RegisterValue register;
    @Component({STACK}) protected AllocatableValue stackslot;

    public ShadowedRegisterValue(RegisterValue register, AllocatableValue stackslot) {
        super(register.getLIRKind());
        assert (register.getLIRKind().equals(stackslot.getLIRKind()));
        this.register = register;
        this.stackslot = stackslot;
    }

    public RegisterValue getRegister() {
        return register;
    }

    public AllocatableValue getStackSlot() {
        return stackslot;
    }

    @Override
    public CompositeValue forEachComponent(LIRInstruction inst, OperandMode mode, InstructionValueProcedure proc) {
        RegisterValue newRegister = (RegisterValue) proc.doValue(inst, register, mode, registerFlags);
        AllocatableValue newStackSlot = (AllocatableValue) proc.doValue(inst, stackslot, mode, stackslotFlags);
        if (register.equals(newRegister) || stackslot.equals(newStackSlot)) {
            return this;
        }
        return new ShadowedRegisterValue(newRegister, newStackSlot);
    }

    @Override
    protected void visitEachComponent(LIRInstruction inst, OperandMode mode, InstructionValueConsumer proc) {
        proc.visitValue(inst, register, mode, registerFlags);
        proc.visitValue(inst, stackslot, mode, stackslotFlags);
    }

}
