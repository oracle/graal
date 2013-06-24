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
package com.oracle.graal.lir.sparc;

import static com.oracle.graal.lir.LIRInstruction.OperandFlag.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.asm.sparc.*;
import com.oracle.graal.lir.*;
import com.oracle.graal.lir.LIRInstruction.*;

public class SPARCAddressValue extends CompositeValue {

    private static final long serialVersionUID = -3583286416638228207L;

    @Component({REG, OperandFlag.ILLEGAL}) protected AllocatableValue base;
    protected final int displacement;

    public SPARCAddressValue(PlatformKind kind, AllocatableValue baseRegister, int finalDisp) {
        super(kind);
        this.base = baseRegister;
        this.displacement = finalDisp;
    }

    private static Register toRegister(AllocatableValue value) {
        if (value.equals(Value.ILLEGAL)) {
            return Register.None;
        } else {
            RegisterValue reg = (RegisterValue) value;
            return reg.getRegister();
        }
    }

    public SPARCAddress toAddress() {
        return new SPARCAddress(toRegister(base), displacement);
    }

}
