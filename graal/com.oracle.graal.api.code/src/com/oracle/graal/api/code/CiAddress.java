/*
 * Copyright (c) 2010, 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.api.code;

import static com.oracle.graal.api.code.CiValueUtil.*;

import com.oracle.graal.api.meta.*;

/**
 * Represents an address in target machine memory, specified via some combination of a base register, an index register,
 * a displacement and a scale. Note that the base and index registers may be a variable that will get a register assigned
 * later by the register allocator.
 */
public final class CiAddress extends RiValue {
    private static final long serialVersionUID = -1003772042519945089L;

    /**
     * A sentinel value used as a place holder in an instruction stream for an address that will be patched.
     */
    public static final CiAddress Placeholder = new CiAddress(RiKind.Illegal, RiValue.IllegalValue);

    /**
     * Base register that defines the start of the address computation.
     * If not present, is denoted by {@link RiValue#IllegalValue}.
     */
    public RiValue base;

    /**
     * Index register, the value of which (possibly scaled by {@link #scale}) is added to {@link #base}.
     * If not present, is denoted by {@link RiValue#IllegalValue}.
     */
    public RiValue index;

    /**
     * Scaling factor for indexing, dependent on target operand size.
     */
    public final Scale scale;

    /**
     * Optional additive displacement.
     */
    public final int displacement;

    /**
     * Creates a {@code CiAddress} with given base register, no scaling and no displacement.
     * @param kind the kind of the value being addressed
     * @param base the base register
     */
    public CiAddress(RiKind kind, RiValue base) {
        this(kind, base, IllegalValue, Scale.Times1, 0);
    }

    /**
     * Creates a {@code CiAddress} with given base register, no scaling and a given displacement.
     * @param kind the kind of the value being addressed
     * @param base the base register
     * @param displacement the displacement
     */
    public CiAddress(RiKind kind, RiValue base, int displacement) {
        this(kind, base, IllegalValue, Scale.Times1, displacement);
    }

    /**
     * Creates a {@code CiAddress} with given base and index registers, scaling and displacement.
     * This is the most general constructor..
     * @param kind the kind of the value being addressed
     * @param base the base register
     * @param index the index register
     * @param scale the scaling factor
     * @param displacement the displacement
     */
    public CiAddress(RiKind kind, RiValue base, RiValue index, Scale scale, int displacement) {
        super(kind);
        this.base = base;
        this.index = index;
        this.scale = scale;
        this.displacement = displacement;

        assert !isConstant(base) && !isStackSlot(base);
        assert !isConstant(index) && !isStackSlot(index);
    }

    /**
     * A scaling factor used in complex addressing modes such as those supported by x86 platforms.
     */
    public enum Scale {
        Times1(1, 0),
        Times2(2, 1),
        Times4(4, 2),
        Times8(8, 3);

        private Scale(int value, int log2) {
            this.value = value;
            this.log2 = log2;
        }

        /**
         * The value (or multiplier) of this scale.
         */
        public final int value;

        /**
         * The {@linkplain #value value} of this scale log 2.
         */
        public final int log2;

        public static Scale fromInt(int scale) {
            switch (scale) {
                case 1:  return Times1;
                case 2:  return Times2;
                case 4:  return Times4;
                case 8:  return Times8;
                default: throw new IllegalArgumentException(String.valueOf(scale));
            }
        }
    }

    @Override
    public String toString() {
        if (this == Placeholder) {
            return "[<placeholder>]";
        }

        StringBuilder s = new StringBuilder();
        s.append(kind.javaName).append("[");
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
        if (obj instanceof CiAddress) {
            CiAddress addr = (CiAddress) obj;
            return kind == addr.kind && displacement == addr.displacement && base.equals(addr.base) && scale == addr.scale && index.equals(addr.index);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return base.hashCode() ^ index.hashCode() ^ (displacement << 4) ^ (scale.value << 8) ^ (kind.ordinal() << 12);
    }
}
