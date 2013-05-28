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
package com.oracle.graal.asm.sparc;

import com.oracle.graal.api.code.AbstractAddress;
import com.oracle.graal.api.code.Register;

public class SPARCAddress extends AbstractAddress {

    private final Register base;
    private final int displacement; // need Register offset / displacement CompositeValue?

    /**
     * Creates an {@link SPARCAddress} with given base register, no scaling and a given
     * displacement.
     * 
     * @param base the base register
     * @param displacement the displacement
     */
    public SPARCAddress(Register base, int displacement) {
        this.base = base;
        this.displacement = displacement;
    }

    @Override
    public String toString() {
        StringBuilder s = new StringBuilder();
        s.append("[");
        if (!getBase().equals(Register.None)) {
            s.append(getBase());
        }
        // later: displacement CompositeValue?
        s.append("]");
        return s.toString();
    }

    /**
     * @return Base register that defines the start of the address computation. If not present, is
     *         denoted by {@link Register#None}.
     */
    public Register getBase() {
        return base;
    }

    /**
     * @return Optional additive displacement.
     */
    public int getDisplacement() {
        return displacement;
    }
}
