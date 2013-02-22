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
package com.oracle.graal.ptx;

import static com.oracle.graal.api.code.ValueUtil.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;

/**
 * Represents an address in target machine memory, specified via some combination of a base register
 * and a displacement.
 */
public final class PTXAddress extends Address {

    private static final long serialVersionUID = 8343625682010474837L;

    private final Value[] base;
    private final long displacement;

    /**
     * Creates an {@link PTXAddress} with given base register and no displacement.
     * 
     * @param kind the kind of the value being addressed
     * @param base the base register
     */
    public PTXAddress(Kind kind, Value base) {
        this(kind, base, 0);
    }

    /**
     * Creates an {@link PTXAddress} with given base register and a displacement. This is the most
     * general constructor.
     * 
     * @param kind the kind of the value being addressed
     * @param base the base register
     * @param displacement the displacement
     */
    public PTXAddress(Kind kind, Value base, long displacement) {
        super(kind);
        this.base = new Value[1];
        this.setBase(base);
        this.displacement = displacement;

        assert !isConstant(base) && !isStackSlot(base);
    }

    @Override
    public Value[] components() {
        return base;
    }

    @Override
    public String toString() {
        StringBuilder s = new StringBuilder();
        s.append(getKind().getJavaName()).append("[");
        String sep = "";
        if (isLegal(getBase())) {
            s.append(getBase());
            sep = " + ";
        }
        if (getDisplacement() < 0) {
            s.append(" - ").append(-getDisplacement());
        } else if (getDisplacement() > 0) {
            s.append(sep).append(getDisplacement());
        }
        s.append("]");
        return s.toString();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof PTXAddress) {
            PTXAddress addr = (PTXAddress) obj;
            return getKind() == addr.getKind() && getDisplacement() == addr.getDisplacement() && getBase().equals(addr.getBase());
        }
        return false;
    }

    @Override
    public int hashCode() {
        return getBase().hashCode() ^ ((int) getDisplacement() << 4) ^ (getKind().ordinal() << 12);
    }

    /**
     * @return Base register that defines the start of the address computation. If not present, is
     *         denoted by {@link Value#ILLEGAL}.
     */
    public Value getBase() {
        return base[0];
    }

    public void setBase(Value base) {
        this.base[0] = base;
    }

    /**
     * @return Optional additive displacement.
     */
    public long getDisplacement() {
        return displacement;
    }
}
