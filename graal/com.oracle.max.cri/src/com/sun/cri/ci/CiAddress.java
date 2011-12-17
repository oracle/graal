/*
 * Copyright (c) 2010, 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.cri.ci;

/**
 * Represents an address in target machine memory, specified via some combination of a base register, an index register,
 * a displacement and a scale. Note that the base and index registers may be {@link CiVariable variable}, that is as yet
 * unassigned to target machine registers.
 */
public final class CiAddress extends CiValue {

    /**
     * A sentinel value used as a place holder in an instruction stream for an address that will be patched.
     */
    public static final CiAddress Placeholder = new CiAddress(CiKind.Illegal, CiRegister.None.asValue());

    /**
     * Base register that defines the start of the address computation; always present.
     */
    public final CiValue base;
    /**
     * Optional index register, the value of which (possibly scaled by {@link #scale}) is added to {@link #base}.
     * If not present, is denoted by {@link CiValue#IllegalValue}.
     */
    public final CiValue index;
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
    public CiAddress(CiKind kind, CiValue base) {
        this(kind, base, IllegalValue, Scale.Times1, 0);
    }

    /**
     * Creates a {@code CiAddress} with given base register, no scaling and a given displacement.
     * @param kind the kind of the value being addressed
     * @param base the base register
     * @param displacement the displacement
     */
    public CiAddress(CiKind kind, CiValue base, int displacement) {
        this(kind, base, IllegalValue, Scale.Times1, displacement);
    }

    /**
     * Creates a {@code CiAddress} with given base and offset registers, no scaling and no displacement.
     * @param kind the kind of the value being addressed
     * @param base the base register
     * @param offset the offset register
     */
    public CiAddress(CiKind kind, CiValue base, CiValue offset) {
        this(kind, base, offset, Scale.Times1, 0);
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
    public CiAddress(CiKind kind, CiValue base, CiValue index, Scale scale, int displacement) {
        super(kind);

        if (index.isConstant()) {
            long longIndex = ((CiConstant) index).asLong();
            long longDisp = displacement + longIndex * scale.value;
            if ((int) longIndex != longIndex || (int) longDisp != longDisp) {
                throw new Error("integer overflow when computing constant displacement");
            }
            displacement = (int) longDisp;
            index = IllegalValue;
            scale = Scale.Times1;
        }
        assert base.isIllegal() || base.isVariableOrRegister();
        assert index.isIllegal() || index.isVariableOrRegister();

        this.base = base;
        this.index = index;
        this.scale = scale;
        this.displacement = displacement;
    }

    /**
     * A scaling factor used in complex addressing modes such as those supported by x86 platforms.
     */
    public enum Scale {
        Times1(1, 0),
        Times2(2, 1),
        Times4(4, 2),
        Times8(8, 3);

        Scale(int value, int log2) {
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
            // Checkstyle: stop
            switch (scale) {
                case 1: return Times1;
                case 2: return Times2;
                case 4: return Times4;
                case 8: return Times8;
                default: throw new IllegalArgumentException(String.valueOf(scale));
            }
            // Checkstyle: resume
        }

        public static Scale fromShift(int shift) {
            return fromInt(1 << shift);
        }
    }

    /**
     * If the base register is a {@link CiRegisterValue} returns the associated {@link CiRegister}
     * otherwise raises an exception..
     * @return the base {@link CiRegister}
     * @exception Error  if {@code base} is not a {@link CiRegisterValue}
     */
    public CiRegister base() {
        return base.asRegister();
    }

    /**
     * If the index register is a {@link CiRegisterValue} returns the associated {@link CiRegister}
     * otherwise raises an exception..
     * @return the base {@link CiRegister}
     * @exception Error  if {@code index} is not a {@link CiRegisterValue}
     */
    public CiRegister index() {
        return index.asRegister();
    }

    /**
     * Encodes the possible addressing modes as a simple value.
     */
    public enum Format {
        BASE,
        BASE_DISP,
        BASE_INDEX,
        BASE_INDEX_DISP,
        PLACEHOLDER;
    }

    /**
     * Returns the {@link Format encoded addressing mode} that this {@code CiAddress} represents.
     * @return the encoded addressing mode
     */
    public Format format() {
        if (this == Placeholder) {
            return Format.PLACEHOLDER;
        }
        assert base.isLegal();
        if (index.isLegal()) {
            if (displacement != 0) {
                return Format.BASE_INDEX_DISP;
            } else {
                return Format.BASE_INDEX;
            }
        } else {
            if (displacement != 0) {
                return Format.BASE_DISP;
            } else {
                return Format.BASE;
            }
        }
    }

    private static String s(CiValue location) {
        if (location.isRegister()) {
            return location.asRegister().name;
        }
        assert location.isVariable();
        return "v" + ((CiVariable) location).index;
    }

    private static String signed(int i) {
        if (i >= 0) {
            return "+" + i;
        }
        return String.valueOf(i);
    }

    @Override
    public String name() {
        // Checkstyle: stop
        switch (format()) {
            case BASE            : return "[" + s(base) + "]";
            case BASE_DISP       : return "[" + s(base) + signed(displacement) + "]";
            case BASE_INDEX      : return "[" + s(base) + "+" + s(index) + "]";
            case BASE_INDEX_DISP : return "[" + s(base) + "+(" + s(index) + "*" + scale.value + ")" + signed(displacement) + "]";
            case PLACEHOLDER     : return "[<placeholder>]";
            default              : throw new IllegalArgumentException("unknown format: " + format());
        }
        // Checkstyle: resume
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
    public boolean equalsIgnoringKind(CiValue o) {
        if (o instanceof CiAddress) {
            CiAddress addr = (CiAddress) o;
            return displacement == addr.displacement && base.equalsIgnoringKind(addr.base) && scale == addr.scale && index.equalsIgnoringKind(addr.index);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return (base.hashCode() << 4) | kind.ordinal();
    }
}
