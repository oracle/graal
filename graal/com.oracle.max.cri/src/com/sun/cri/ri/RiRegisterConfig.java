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
package com.sun.cri.ri;

import java.util.*;

import com.sun.cri.ci.*;
import com.sun.cri.ci.CiCallingConvention.Type;
import com.sun.cri.ci.CiRegister.RegisterFlag;

/**
 * A register configuration binds roles and {@linkplain RiRegisterAttributes attributes}
 * to physical registers.
 */
public interface RiRegisterConfig {

    /**
     * Gets the register to be used for returning a value of a given kind.
     */
    CiRegister getReturnRegister(CiKind kind);

    /**
     * Gets the register to which {@link CiRegister#Frame} and {@link CiRegister#CallerFrame} are bound.
     */
    CiRegister getFrameRegister();

    CiRegister getScratchRegister();

    /**
     * Gets the calling convention describing how arguments are passed.
     *
     * @param type the type of calling convention being requested
     * @param parameters the types of the arguments of the call
     * @param target the target platform
     * @param stackOnly ignore registers
     */
    CiCallingConvention getCallingConvention(Type type, CiKind[] parameters, CiTarget target, boolean stackOnly);

    /**
     * Gets the ordered set of registers that are can be used to pass parameters
     * according to a given calling convention.
     *
     * @param type the type of calling convention
     * @param flag specifies whether registers for {@linkplain RegisterFlag#CPU integral} or
     *             {@linkplain} RegisterFlag#FPU floating point} parameters are being requested
     * @return the ordered set of registers that may be used to pass parameters in a call conforming to {@code type}
     */
    CiRegister[] getCallingConventionRegisters(Type type, RegisterFlag flag);

    /**
     * Gets the set of registers that can be used by the register allocator.
     */
    CiRegister[] getAllocatableRegisters();

    /**
     * Gets the set of registers that can be used by the register allocator,
     * {@linkplain CiRegister#categorize(CiRegister[]) categorized} by register {@linkplain RegisterFlag flags}.
     *
     * @return a map from each {@link RegisterFlag} constant to the list of {@linkplain #getAllocatableRegisters()
     *         allocatable} registers for which the flag is {@linkplain #isSet(RegisterFlag) set}
     *
     */
    EnumMap<RegisterFlag, CiRegister[]> getCategorizedAllocatableRegisters();

    /**
     * Gets the registers whose values must be preserved by a method across any call it makes.
     */
    CiRegister[] getCallerSaveRegisters();

    /**
     * Gets the layout of the callee save area of this register configuration.
     *
     * @return {@code null} if there is no callee save area
     */
    CiCalleeSaveLayout getCalleeSaveLayout();

    /**
     * Gets a map from register {@linkplain CiRegister#number numbers} to register
     * {@linkplain RiRegisterAttributes attributes} for this register configuration.
     *
     * @return an array where an element at index i holds the attributes of the register whose number is i
     * @see CiRegister#categorize(CiRegister[])
     */
    RiRegisterAttributes[] getAttributesMap();

    /**
     * Gets the register corresponding to a runtime-defined role.
     *
     * @param id the identifier of a runtime-defined register role
     * @return the register playing the role specified by {@code id}
     */
    CiRegister getRegisterForRole(int id);
}
