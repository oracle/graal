/*
 * Copyright (c) 2013, 2021, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.lir.amd64;

import static jdk.vm.ci.code.ValueUtil.isLegal;
import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.REG;

import java.util.EnumSet;

import org.graalvm.compiler.asm.amd64.AMD64Address;
import org.graalvm.compiler.core.common.Stride;
import org.graalvm.compiler.lir.CompositeValue;
import org.graalvm.compiler.lir.InstructionValueConsumer;
import org.graalvm.compiler.lir.InstructionValueProcedure;
import org.graalvm.compiler.lir.LIRInstruction;
import org.graalvm.compiler.lir.LIRInstruction.OperandFlag;
import org.graalvm.compiler.lir.LIRInstruction.OperandMode;

import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.RegisterValue;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.Value;
import jdk.vm.ci.meta.ValueKind;

public final class AMD64AddressValue extends CompositeValue {

    @Component({REG, OperandFlag.ILLEGAL}) protected AllocatableValue base;
    @Component({REG, OperandFlag.ILLEGAL}) protected AllocatableValue index;
    protected final Stride stride;
    protected final int displacement;
    private final Object displacementAnnotation;

    private static final EnumSet<OperandFlag> flags = EnumSet.of(OperandFlag.REG, OperandFlag.ILLEGAL);

    public AMD64AddressValue(ValueKind<?> kind, AllocatableValue base, int displacement) {
        this(kind, base, Value.ILLEGAL, Stride.S1, displacement);
    }

    public AMD64AddressValue(ValueKind<?> kind, AllocatableValue base, AllocatableValue index, Stride stride, int displacement) {
        this(kind, base, index, stride, displacement, null);
    }

    public AMD64AddressValue(ValueKind<?> kind, AllocatableValue base, AllocatableValue index, Stride stride, int displacement, Object displacementAnnotation) {
        super(kind);
        this.base = base;
        this.index = index;
        this.stride = stride;
        this.displacement = displacement;
        this.displacementAnnotation = displacementAnnotation;

        assert stride != null;
    }

    public AllocatableValue getBase() {
        return base;
    }

    public AllocatableValue getIndex() {
        return index;
    }

    @Override
    public CompositeValue forEachComponent(LIRInstruction inst, OperandMode mode, InstructionValueProcedure proc) {
        AllocatableValue newBase = (AllocatableValue) proc.doValue(inst, base, mode, flags);
        AllocatableValue newIndex = (AllocatableValue) proc.doValue(inst, index, mode, flags);
        if (!base.identityEquals(newBase) || !index.identityEquals(newIndex)) {
            return new AMD64AddressValue(getValueKind(), newBase, newIndex, stride, displacement, displacementAnnotation);
        }
        return this;
    }

    @Override
    protected void visitEachComponent(LIRInstruction inst, OperandMode mode, InstructionValueConsumer proc) {
        proc.visitValue(inst, base, mode, flags);
        proc.visitValue(inst, index, mode, flags);
    }

    public AMD64AddressValue withKind(ValueKind<?> newKind) {
        return new AMD64AddressValue(newKind, base, index, stride, displacement, displacementAnnotation);
    }

    private static Register toRegister(AllocatableValue value) {
        if (value.equals(Value.ILLEGAL)) {
            return Register.None;
        } else {
            RegisterValue reg = (RegisterValue) value;
            return reg.getRegister();
        }
    }

    public AMD64Address toAddress() {
        return new AMD64Address(toRegister(base), toRegister(index), stride, displacement, displacementAnnotation);
    }

    @Override
    public String toString() {
        StringBuilder s = new StringBuilder("[");
        String sep = "";
        if (isLegal(base)) {
            s.append(base);
            sep = " + ";
        }
        if (isLegal(index)) {
            s.append(sep).append(index).append(" * ").append(stride.value);
            sep = " + ";
        }
        if (displacement < 0) {
            s.append(" - ").append(-displacement);
        } else if (displacement > 0) {
            s.append(sep).append(displacement);
        }
        if (displacementAnnotation != null) {
            s.append(" + ").append(displacementAnnotation);
        }
        s.append("]");
        return s.toString();
    }

    public boolean isValidImplicitNullCheckFor(Value value, int implicitNullCheckLimit) {
        return value.equals(base) && index.equals(Value.ILLEGAL) && displacement >= 0 && displacement < implicitNullCheckLimit;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof AMD64AddressValue) {
            AMD64AddressValue addr = (AMD64AddressValue) obj;
            return getValueKind().equals(addr.getValueKind()) && displacement == addr.displacement && base.equals(addr.base) && stride == addr.stride && index.equals(addr.index);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return base.hashCode() ^ index.hashCode() ^ (displacement << 4) ^ (stride.value << 8) ^ getValueKind().hashCode();
    }
}
