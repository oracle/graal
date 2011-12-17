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
package com.sun.cri.ri;

import java.util.*;

import com.sun.cri.ci.*;

/**
 * A collection of register attributes. The specific attribute values for a register may be
 * local to a compilation context. For example, a {@link RiRegisterConfig} in use during
 * a compilation will determine which registers are callee saved.
 */
public class RiRegisterAttributes {

    /**
     * Denotes a register whose value preservation (if required) across a call is the responsibility of the caller.
     */
    public final boolean isCallerSave;

    /**
     * Denotes a register whose value preservation (if required) across a call is the responsibility of the callee.
     */
    public final boolean isCalleeSave;

    /**
     * Denotes a register that is available for use by a register allocator.
     */
    public final boolean isAllocatable;

    /**
     * Denotes a register guaranteed to be non-zero if read in compiled Java code.
     * For example, a register dedicated to holding the current thread.
     */
    public boolean isNonZero;

    public RiRegisterAttributes(boolean isCallerSave, boolean isCalleeSave, boolean isAllocatable) {
        this.isCallerSave = isCallerSave;
        this.isCalleeSave = isCalleeSave;
        this.isAllocatable = isAllocatable;
    }

    public static final RiRegisterAttributes NONE = new RiRegisterAttributes(false, false, false);

    /**
     * Creates a map from register {@linkplain CiRegister#number numbers} to register
     * {@linkplain RiRegisterAttributes attributes} for a given register configuration and set of
     * registers.
     *
     * @param registerConfig a register configuration
     * @param registers a set of registers
     * @return an array whose length is the max register number in {@code registers} plus 1. An element at index i holds
     *         the attributes of the register whose number is i.
     */
    public static RiRegisterAttributes[] createMap(RiRegisterConfig registerConfig, CiRegister[] registers) {
        RiRegisterAttributes[] map = new RiRegisterAttributes[registers.length];
        for (CiRegister reg : registers) {
            if (reg != null) {
                CiCalleeSaveLayout csl = registerConfig.getCalleeSaveLayout();
                RiRegisterAttributes attr = new RiRegisterAttributes(
                                Arrays.asList(registerConfig.getCallerSaveRegisters()).contains(reg),
                                csl == null ? false : Arrays.asList(csl.registers).contains(reg),
                                Arrays.asList(registerConfig.getAllocatableRegisters()).contains(reg));
                if (map.length <= reg.number) {
                    map = Arrays.copyOf(map, reg.number + 1);
                }
                map[reg.number] = attr;
            }
        }
        for (int i = 0; i < map.length; i++) {
            if (map[i] == null) {
                map[i] = NONE;
            }
        }
        return map;
    }
}
