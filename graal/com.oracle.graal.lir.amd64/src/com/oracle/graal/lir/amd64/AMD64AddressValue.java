/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.lir.amd64;

import static com.oracle.graal.api.code.ValueUtil.*;
import static com.oracle.graal.lir.LIRInstruction.OperandFlag.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.asm.amd64.*;
import com.oracle.graal.asm.amd64.AMD64Address.Scale;
import com.oracle.graal.lir.*;
import com.oracle.graal.lir.LIRInstruction.OperandFlag;

public final class AMD64AddressValue extends CompositeValue {

    private static final long serialVersionUID = -4444600052487578694L;

    @Component({REG, OperandFlag.ILLEGAL}) protected AllocatableValue base;
    @Component({REG, OperandFlag.ILLEGAL}) protected AllocatableValue index;
    protected final Scale scale;
    protected final int displacement;

    public AMD64AddressValue(PlatformKind kind, AllocatableValue base, int displacement) {
        this(kind, base, Value.ILLEGAL, Scale.Times1, displacement);
    }

    public AMD64AddressValue(PlatformKind kind, AllocatableValue base, AllocatableValue index, Scale scale, int displacement) {
        super(kind);
        this.base = base;
        this.index = index;
        this.scale = scale;
        this.displacement = displacement;

        assert scale != null;
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
        return new AMD64Address(toRegister(base), toRegister(index), scale, displacement);
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
            s.append(sep).append(index).append(" * ").append(scale.value);
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
        if (obj instanceof AMD64AddressValue) {
            AMD64AddressValue addr = (AMD64AddressValue) obj;
            return getPlatformKind() == addr.getPlatformKind() && displacement == addr.displacement && base.equals(addr.base) && scale == addr.scale && index.equals(addr.index);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return base.hashCode() ^ index.hashCode() ^ (displacement << 4) ^ (scale.value << 8) ^ getPlatformKind().hashCode();
    }
}
