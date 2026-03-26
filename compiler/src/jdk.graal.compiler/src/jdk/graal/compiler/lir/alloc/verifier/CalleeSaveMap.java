/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package jdk.graal.compiler.lir.alloc.verifier;

import java.util.List;
import java.util.Map;

import jdk.graal.compiler.util.EconomicHashMap;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.RegisterConfig;
import jdk.vm.ci.code.RegisterValue;

/**
 * Make sure callee-saved register values are retrieved at exit block.
 *
 * <p>
 * Virtual moves assigning to same registers need to also be handled, because both values could be
 * retrieved.
 * </p>
 */
public class CalleeSaveMap {
    protected RegisterConfig registerConfig;
    protected List<Register> calleeSaveRegisters;

    protected Map<RARegister, RAValue> virtualValues;

    public CalleeSaveMap(RegisterConfig registerConfig) {
        this.registerConfig = registerConfig;
        calleeSaveRegisters = registerConfig.getCalleeSaveRegisters();
        virtualValues = new EconomicHashMap<>();
    }

    public class CalleeSavedRegister extends RARegister {
        protected CalleeSavedRegister(RegisterValue registerValue) {
            super(registerValue);
        }

        @Override
        public String toString() {
            return "CalleeSaved " + super.toString();
        }
    }

    /**
     * Create a callee saved register, this is signalling that this value needs to be retrieved on
     * exit point, not just same register value.
     *
     * @param registerValue Callee saved register
     * @return Instance of callee saved register
     */
    public CalleeSavedRegister createCalleeSavedRegister(RegisterValue registerValue) {
        var calleeSavedRegister = new CalleeSavedRegister(registerValue);
        virtualValues.put(calleeSavedRegister, calleeSavedRegister);
        return calleeSavedRegister;
    }

    /**
     * Add a variable from virtual move that is assigned to a callee saved register and can also be
     * retrieved on exit.
     *
     * @param register Callee saved register
     * @param value New callee saved value
     */
    public void addValue(RARegister register, RAValue value) {
        if (!isCalleeSaveRegister(register)) {
            return;
        }

        if (virtualValues.get(register) instanceof CalleeSavedRegister) {
            virtualValues.put(register, value);
        }
    }

    /**
     * Is callee saved value?
     *
     * @param register Callee saved register
     * @return Get callee saved value for this register
     */
    public RAValue getCalleeSavedValue(RARegister register) {
        return virtualValues.get(register);
    }

    /**
     * Is register callee-saved?
     *
     * @param register Register
     * @return true, if register is callee saved
     */
    public boolean isCalleeSaveRegister(RARegister register) {
        if (calleeSaveRegisters == null) {
            return false;
        }

        return calleeSaveRegisters.contains(register.getRegister());
    }

    /**
     * Get list of callee saved registers.
     *
     * @return callee saved registers
     */
    public List<Register> getCalleeSaveRegisters() {
        return calleeSaveRegisters;
    }
}
