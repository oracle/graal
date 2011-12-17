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

import java.util.*;

import com.oracle.max.cri.intrinsics.*;
import com.sun.cri.ci.CiRegister.RegisterFlag;


/**
 * Represents a CPU architecture, including information such as its endianness, CPU
 * registers, word width, etc.
 */
public abstract class CiArchitecture {

    /**
     * The endianness of the architecture.
     */
    public static enum ByteOrder {
        LittleEndian,
        BigEndian
    }

    /**
     * The number of bits required in a bit map covering all the registers that may store references.
     * The bit position of a register in the map is the register's {@linkplain CiRegister#number number}.
     */
    public final int registerReferenceMapBitCount;

    /**
     * Represents the natural size of words (typically registers and pointers) of this architecture, in bytes.
     */
    public final int wordSize;

    /**
     * The name of this architecture (e.g. "AMD64", "SPARCv9").
     */
    public final String name;

    /**
     * Array of all available registers on this architecture. The index of each register in this
     * array is equal to its {@linkplain CiRegister#number number}.
     */
    public final CiRegister[] registers;

    /**
     * Map of all registers keyed by their {@linkplain CiRegister#name names}.
     */
    public final HashMap<String, CiRegister> registersByName;

    /**
     * The byte ordering can be either little or big endian.
     */
    public final ByteOrder byteOrder;

    /**
     * Mask of the barrier constants defined in {@link MemoryBarriers} denoting the barriers that
     * are not required to be explicitly inserted under this architecture.
     */
    public final int implicitMemoryBarriers;

    /**
     * Determines the barriers in a given barrier mask that are explicitly required on this architecture.
     *
     * @param barriers a mask of the barrier constants defined in {@link MemoryBarriers}
     * @return the value of {@code barriers} minus the barriers unnecessary on this architecture
     */
    public final int requiredBarriers(int barriers) {
        return barriers & ~implicitMemoryBarriers;
    }

    /**
     * Offset in bytes from the beginning of a call instruction to the displacement.
     */
    public final int machineCodeCallDisplacementOffset;

    /**
     * The size of the return address pushed to the stack by a call instruction.
     * A value of 0 denotes that call linkage uses registers instead (e.g. SPARC).
     */
    public final int returnAddressSize;

    private final EnumMap<RegisterFlag, CiRegister[]> registersByTypeAndEncoding;

    /**
     * Gets the register for a given {@linkplain CiRegister#encoding encoding} and type.
     *
     * @param encoding a register value as used in a machine instruction
     * @param type the type of the register
     */
    public CiRegister registerFor(int encoding, RegisterFlag type) {
        CiRegister[] regs = registersByTypeAndEncoding.get(type);
        assert encoding >= 0 && encoding < regs.length;
        CiRegister reg = regs[encoding];
        assert reg != null;
        return reg;
    }

    protected CiArchitecture(String name,
                    int wordSize,
                    ByteOrder byteOrder,
                    CiRegister[] registers,
                    int implicitMemoryBarriers,
                    int nativeCallDisplacementOffset,
                    int registerReferenceMapBitCount,
                    int returnAddressSize) {
        this.name = name;
        this.registers = registers;
        this.wordSize = wordSize;
        this.byteOrder = byteOrder;
        this.implicitMemoryBarriers = implicitMemoryBarriers;
        this.machineCodeCallDisplacementOffset = nativeCallDisplacementOffset;
        this.registerReferenceMapBitCount = registerReferenceMapBitCount;
        this.returnAddressSize = returnAddressSize;

        registersByName = new HashMap<String, CiRegister>(registers.length);
        for (CiRegister register : registers) {
            registersByName.put(register.name, register);
            assert registers[register.number] == register;
        }

        registersByTypeAndEncoding = new EnumMap<CiRegister.RegisterFlag, CiRegister[]>(RegisterFlag.class);
        EnumMap<RegisterFlag, CiRegister[]> categorizedRegs = CiRegister.categorize(registers);
        for (RegisterFlag type : RegisterFlag.values()) {
            CiRegister[] regs = categorizedRegs.get(type);
            int max = CiRegister.maxRegisterEncoding(regs);
            CiRegister[] regsByEnc = new CiRegister[max + 1];
            for (CiRegister reg : regs) {
                regsByEnc[reg.encoding] = reg;
            }
            registersByTypeAndEncoding.put(type, regsByEnc);
        }
    }

    /**
     * Converts this architecture to a string.
     * @return the string representation of this architecture
     */
    @Override
    public final String toString() {
        return name.toLowerCase();
    }

    /**
     * Checks whether this is a 32-bit architecture.
     * @return {@code true} if this architecture is 32-bit
     */
    public final boolean is32bit() {
        return wordSize == 4;
    }

    /**
     * Checks whether this is a 64-bit architecture.
     * @return {@code true} if this architecture is 64-bit
     */
    public final boolean is64bit() {
        return wordSize == 8;
    }

    // The following methods are architecture specific and not dependent on state
    // stored in this class. They have convenient default implementations.

    /**
     * Checks whether this architecture's normal arithmetic instructions use a two-operand form
     * (e.g. x86 which overwrites one operand register with the result when adding).
     * @return {@code true} if this architecture uses two-operand mode
     */
    public boolean twoOperandMode() {
        return false;
    }

    // TODO: Why enumerate the concrete subclasses here rather
    // than use instanceof comparisons in code that cares?

    /**
     * Checks whether the architecture is x86.
     * @return {@code true} if the architecture is x86
     */
    public boolean isX86() {
        return false;
    }

    /**
     * Checks whether the architecture is SPARC.
     * @return {@code true} if the architecture is SPARC
     */
    public boolean isSPARC() {
        return false;
    }

}
