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

import static com.oracle.graal.lir.LIRInstruction.OperandFlag.*;

import java.util.*;

import jdk.internal.jvmci.code.*;
import jdk.internal.jvmci.meta.*;

import com.oracle.graal.lir.*;
import com.oracle.graal.lir.LIRInstruction.OperandFlag;
import com.oracle.graal.lir.LIRInstruction.OperandMode;

/**
 * Represents a {@link #register} which has a shadow copy on the {@link #stackslot stack}.
 * <p>
 * Note: {@link ShadowedRegisterValue} does not follow the contract of {@link CompositeValue}, for
 * instance the {@link #forEachComponent} does not visit {@link #register} or {@link #stackslot} but
 * the {@link CompositeValue} itself. Therefore it should be only used in the context of the
 * {@link TraceRegisterAllocationPhase}.
 */
final class ShadowedRegisterValue extends CompositeValue {
    private static final EnumSet<OperandFlag> flags = EnumSet.of(COMPOSITE);
    private static final EnumSet<OperandFlag> registerFlags = EnumSet.of(REG);
    private static final EnumSet<OperandFlag> stackslotFlags = EnumSet.of(STACK);

    @Component({REG}) protected RegisterValue register;
    @Component({STACK}) protected StackSlotValue stackslot;

    public ShadowedRegisterValue(RegisterValue register, StackSlotValue stackslot) {
        super(register.getLIRKind());
        assert (register.getLIRKind().equals(stackslot.getLIRKind()));
        this.register = register;
        this.stackslot = stackslot;
    }

    public RegisterValue getRegister() {
        return register;
    }

    public StackSlotValue getStackSlot() {
        return stackslot;
    }

    @Override
    public Value forEachComponent(LIRInstruction inst, OperandMode mode, InstructionValueProcedure proc) {
        /* TODO(jeisl) This is a hack to be able to replace the composite value with the register. */
        return proc.doValue(inst, this, mode, flags);
    }

    @Override
    protected void visitEachComponent(LIRInstruction inst, OperandMode mode, InstructionValueConsumer proc) {
        proc.visitValue(inst, register, mode, registerFlags);
        proc.visitValue(inst, stackslot, mode, stackslotFlags);
    }

}
