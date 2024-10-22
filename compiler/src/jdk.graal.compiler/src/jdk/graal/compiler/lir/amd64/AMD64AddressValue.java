/*
 * Copyright (c) 2013, 2024, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.lir.amd64;

import static jdk.vm.ci.amd64.AMD64.r12;
import static jdk.vm.ci.code.ValueUtil.isLegal;

import java.util.EnumSet;

import jdk.graal.compiler.asm.amd64.AMD64Address;
import jdk.graal.compiler.asm.amd64.AMD64MacroAssembler;
import jdk.graal.compiler.core.common.NumUtil;
import jdk.graal.compiler.core.common.Stride;
import jdk.graal.compiler.lir.CompositeValue;
import jdk.graal.compiler.lir.InstructionValueConsumer;
import jdk.graal.compiler.lir.InstructionValueProcedure;
import jdk.graal.compiler.lir.LIRInstruction;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.RegisterValue;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.Value;
import jdk.vm.ci.meta.ValueKind;

public final class AMD64AddressValue extends CompositeValue {

    @Component({LIRInstruction.OperandFlag.REG, LIRInstruction.OperandFlag.ILLEGAL}) protected AllocatableValue base;
    @Component({LIRInstruction.OperandFlag.REG, LIRInstruction.OperandFlag.ILLEGAL}) protected AllocatableValue index;
    protected final Stride stride;
    protected final int displacement;
    private final Object displacementAnnotation;

    private static final EnumSet<LIRInstruction.OperandFlag> flags = EnumSet.of(LIRInstruction.OperandFlag.REG, LIRInstruction.OperandFlag.ILLEGAL);

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
    public CompositeValue forEachComponent(LIRInstruction inst, LIRInstruction.OperandMode mode, InstructionValueProcedure proc) {
        AllocatableValue newBase = (AllocatableValue) proc.doValue(inst, base, mode, flags);
        AllocatableValue newIndex = (AllocatableValue) proc.doValue(inst, index, mode, flags);
        if (!base.identityEquals(newBase) || !index.identityEquals(newIndex)) {
            return new AMD64AddressValue(getValueKind(), newBase, newIndex, stride, displacement, displacementAnnotation);
        }
        return this;
    }

    @Override
    protected void visitEachComponent(LIRInstruction inst, LIRInstruction.OperandMode mode, InstructionValueConsumer proc) {
        proc.visitValue(inst, base, mode, flags);
        proc.visitValue(inst, index, mode, flags);
    }

    public AMD64AddressValue withKind(ValueKind<?> newKind) {
        return new AMD64AddressValue(newKind, base, index, stride, displacement, displacementAnnotation);
    }

    /**
     * Baseless address encoding forces 4-byte displacement. E.g.,
     *
     * <pre>
     * mov rax, QWORD PTR [rax*8+0x10]       48 8b 04 c5 10 00 00 00
     * mov rax, QWORD PTR [r12+rax*8+0x10]   49 8b 44 c4 10
     * </pre>
     *
     * We use r12 as the base register for addresses without base, if the displacement is within
     * range of a byte and the value in r12 is constantly 0. The latter scenario may happen in
     * HotSpot with compressed oops where r12 is served as the heap base register, and when the
     * offset for heap base is 0.
     *
     * For displacement outside the range of a byte, we keep the base register {@link Register#None}
     * to avoid potential additional REX prefix for the extended register (r8-r15). E.g.,
     *
     * <pre>
     * mov eax, DWORD PTR [rax*8+0x100]      8b 04 c5 00 01 00 00
     * mov eax, DWORD PTR [r12+rax*8+0x100]  41 8b 84 c4 00 01 00 00
     * </pre>
     */
    private Register getBaseRegisterForBaselessAddress(AMD64MacroAssembler masm) {
        if (NumUtil.isByte(displacement)) {
            Register reg = masm.getZeroValueRegister();
            if (r12.equals(reg)) {
                return r12;
            }
        }
        return Register.None;
    }

    public AMD64Address toAddress(AMD64MacroAssembler masm) {
        Register baseReg = Value.ILLEGAL.equals(base) ? getBaseRegisterForBaselessAddress(masm) : ((RegisterValue) base).getRegister();
        Register indexReg = Value.ILLEGAL.equals(index) ? Register.None : ((RegisterValue) index).getRegister();
        return new AMD64Address(baseReg, indexReg, stride, displacement, displacementAnnotation);
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
