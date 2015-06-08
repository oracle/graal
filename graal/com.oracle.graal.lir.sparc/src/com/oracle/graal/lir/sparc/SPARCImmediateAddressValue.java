/*
 * Copyright (c) 2013, 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.lir.sparc;

import static com.oracle.graal.lir.LIRInstruction.OperandFlag.*;
import static com.oracle.jvmci.code.ValueUtil.*;

import java.util.*;

import com.oracle.graal.asm.sparc.*;
import com.oracle.graal.lir.*;
import com.oracle.graal.lir.LIRInstruction.OperandFlag;
import com.oracle.graal.lir.LIRInstruction.OperandMode;
import com.oracle.jvmci.meta.*;

public final class SPARCImmediateAddressValue extends SPARCAddressValue {

    @Component({REG}) protected AllocatableValue base;
    protected final int displacement;

    private static final EnumSet<OperandFlag> flags = EnumSet.of(OperandFlag.REG);

    public SPARCImmediateAddressValue(LIRKind kind, AllocatableValue base, int displacement) {
        super(kind);
        assert SPARCAssembler.isSimm13(displacement);
        this.base = base;
        this.displacement = displacement;
    }

    @Override
    public CompositeValue forEachComponent(LIRInstruction inst, OperandMode mode, InstructionValueProcedure proc) {
        AllocatableValue newBase = (AllocatableValue) proc.doValue(inst, base, mode, flags);
        if (!base.identityEquals(newBase)) {
            return new SPARCImmediateAddressValue(getLIRKind(), newBase, displacement);
        }
        return this;
    }

    @Override
    protected void forEachComponent(LIRInstruction inst, OperandMode mode, InstructionValueConsumer proc) {
        proc.visitValue(inst, base, mode, flags);
    }

    @Override
    public SPARCAddress toAddress() {
        return new SPARCAddress(asRegister(base), displacement);
    }

    @Override
    public boolean isValidImplicitNullCheckFor(Value value, int implicitNullCheckLimit) {
        return value.equals(base) && displacement >= 0 && displacement < implicitNullCheckLimit;
    }

    @Override
    public String toString() {
        StringBuilder s = new StringBuilder("[");
        String sep = "";
        if (isLegal(base)) {
            s.append(base);
            sep = " + ";
        }
        if (displacement < 0) {
            s.append(" - ").append(-displacement);
        } else if (displacement > 0) {
            s.append(sep).append(displacement);
        }
        s.append("]");
        return s.toString();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof SPARCImmediateAddressValue) {
            SPARCImmediateAddressValue addr = (SPARCImmediateAddressValue) obj;
            return getLIRKind().equals(addr.getLIRKind()) && displacement == addr.displacement && base.equals(addr.base);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return base.hashCode() ^ (displacement << 4) ^ getLIRKind().hashCode();
    }
}
