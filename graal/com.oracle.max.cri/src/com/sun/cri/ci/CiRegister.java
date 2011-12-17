/*
 * Copyright (c) 2009, 2011, Oracle and/or its affiliates. All rights reserved.
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

import java.io.*;
import java.util.*;

/**
 * Represents a target machine register.
 */
public final class CiRegister implements Comparable<CiRegister>, Serializable {

    /**
     * Invalid register.
     */
    public static final CiRegister None = new CiRegister(-1, -1, 0, "noreg");

    /**
     * Frame pointer of the current method. All spill slots and outgoing stack-based arguments
     * are addressed relative to this register.
     */
    public static final CiRegister Frame = new CiRegister(-2, -2, 0, "framereg", RegisterFlag.CPU);

    public static final CiRegister CallerFrame = new CiRegister(-3, -3, 0, "callerframereg", RegisterFlag.CPU);

    /**
     * The identifier for this register that is unique across all the registers in a {@link CiArchitecture}.
     * A valid register has {@code number > 0}.
     */
    public final int number;

    /**
     * The mnemonic of this register.
     */
    public final String name;

    /**
     * The actual encoding in a target machine instruction for this register, which may or
     * may not be the same as {@link #number}.
     */
    public final int encoding;

    /**
     * The size of the stack slot used to spill the value of this register.
     */
    public final int spillSlotSize;

    /**
     * The set of {@link RegisterFlag} values associated with this register.
     */
    private final int flags;

    /**
     * An array of {@link CiRegisterValue} objects, for this register, with one entry
     * per {@link CiKind}, indexed by {@link CiKind#ordinal}.
     */
    private final CiRegisterValue[] values;

    /**
     * Attributes that characterize a register in a useful way.
     *
     */
    public enum RegisterFlag {
        /**
         * Denotes an integral (i.e. non floating point) register.
         */
        CPU,

        /**
         * Denotes a register whose lowest order byte can be addressed separately.
         */
        Byte,

        /**
         * Denotes a floating point register.
         */
        FPU;

        public final int mask = 1 << (ordinal() + 1);
    }

    /**
     * Creates a {@code CiRegister} instance.
     *
     * @param number unique identifier for the register
     * @param encoding the target machine encoding for the register
     * @param spillSlotSize the size of the stack slot used to spill the value of the register
     * @param name the mnemonic name for the register
     * @param flags the set of {@link RegisterFlag} values for the register
     */
    public CiRegister(int number, int encoding, int spillSlotSize, String name, RegisterFlag... flags) {
        this.number = number;
        this.name = name;
        this.spillSlotSize = spillSlotSize;
        this.flags = createMask(flags);
        this.encoding = encoding;

        values = new CiRegisterValue[CiKind.VALUES.length];
        for (CiKind kind : CiKind.VALUES) {
            values[kind.ordinal()] = new CiRegisterValue(kind, this);
        }
    }

    private int createMask(RegisterFlag... flags) {
        int result = 0;
        for (RegisterFlag f : flags) {
            result |= f.mask;
        }
        return result;
    }

    public boolean isSet(RegisterFlag f) {
        return (flags & f.mask) != 0;
    }

    /**
     * Gets this register as a {@linkplain CiRegisterValue value} with a specified kind.
     * @param kind the specified kind
     * @return the {@link CiRegisterValue}
     */
    public CiRegisterValue asValue(CiKind kind) {
        return values[kind.ordinal()];
    }

    /**
     * Gets this register as a {@linkplain CiRegisterValue value} with no particular kind.
     * @return a {@link CiRegisterValue} with {@link CiKind#Illegal} kind.
     */
    public CiRegisterValue asValue() {
        return asValue(CiKind.Illegal);
    }

    /**
     * Determines if this is a valid register.
     * @return {@code true} iff this register is valid
     */
    public boolean isValid() {
        return number >= 0;
    }

    /**
     * Determines if this a floating point register.
     */
    public boolean isFpu() {
        return isSet(RegisterFlag.FPU);
    }

    /**
     * Determines if this a general purpose register.
     */
    public boolean isCpu() {
        return isSet(RegisterFlag.CPU);
    }

    /**
     * Determines if this register has the {@link RegisterFlag#Byte} attribute set.
     * @return {@code true} iff this register has the {@link RegisterFlag#Byte} attribute set.
     */
    public boolean isByte() {
        return isSet(RegisterFlag.Byte);
    }

    /**
     * Categorizes a set of registers by {@link RegisterFlag}.
     *
     * @param registers a list of registers to be categorized
     * @return a map from each {@link RegisterFlag} constant to the list of registers for which the flag is
     *         {@linkplain #isSet(RegisterFlag) set}
     */
    public static EnumMap<RegisterFlag, CiRegister[]> categorize(CiRegister[] registers) {
        EnumMap<RegisterFlag, CiRegister[]> result = new EnumMap<RegisterFlag, CiRegister[]>(RegisterFlag.class);
        for (RegisterFlag flag : RegisterFlag.values()) {
            ArrayList<CiRegister> list = new ArrayList<CiRegister>();
            for (CiRegister r : registers) {
                if (r.isSet(flag)) {
                    list.add(r);
                }
            }
            result.put(flag, list.toArray(new CiRegister[list.size()]));
        }
        return result;
    }

    /**
     * Gets the maximum register {@linkplain #number number} in a given set of registers.
     *
     * @param registers the set of registers to process
     * @return the maximum register number for any register in {@code registers}
     */
    public static int maxRegisterNumber(CiRegister[] registers) {
        int max = Integer.MIN_VALUE;
        for (CiRegister r : registers) {
            if (r.number > max) {
                max = r.number;
            }
        }
        return max;
    }

    /**
     * Gets the maximum register {@linkplain #encoding encoding} in a given set of registers.
     *
     * @param registers the set of registers to process
     * @return the maximum register encoding for any register in {@code registers}
     */
    public static int maxRegisterEncoding(CiRegister[] registers) {
        int max = Integer.MIN_VALUE;
        for (CiRegister r : registers) {
            if (r.encoding > max) {
                max = r.encoding;
            }
        }
        return max;
    }

    @Override
    public String toString() {
        return name;
    }

    @Override
    public int compareTo(CiRegister o) {
        if (number < o.number) {
            return -1;
        }
        if (number > o.number) {
            return 1;
        }
        return 0;
    }

}
