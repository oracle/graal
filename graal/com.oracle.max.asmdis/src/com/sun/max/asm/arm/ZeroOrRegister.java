/*
 * Copyright (c) 2007, 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.max.asm.arm;

import com.sun.max.asm.*;
import com.sun.max.util.*;

/**
 * The super type of all the {@link GPR General Purpose Registers} and the constant {@link Zero#ZERO}.
 */
public abstract class ZeroOrRegister extends AbstractSymbolicArgument {

    ZeroOrRegister(String name, int value) {
        super(name, value);
    }

    /**
     * Determines if this register specifier is outside the range of registers
     * {@code [target .. target+n]} where the range
     * wraps at 32.
     */
    public boolean isOutsideRegisterRange(GPR target, int n) {
        final int rt = target.value();
        final int ra = value();
        final int numRegs = (n + 3) / 4;
        final int lastReg = (rt + numRegs - 1) % 32;
        final boolean wrapsAround = lastReg < rt;
        if (wrapsAround) {
            return lastReg < ra && ra < rt;
        }
        return ra < rt || lastReg < ra;
    }

    public static Symbolizer<ZeroOrRegister> symbolizer() {
        if (symbolizer == null) {
            symbolizer = Symbolizer.Static.fromList(ZeroOrRegister.class, GPR.GPR_SYMBOLIZER, Zero.ZERO);
        }
        return symbolizer;
    }

    // This must be lazily constructed to avoid dependency on the GPR class initializer
    private static Symbolizer<ZeroOrRegister> symbolizer;

}
