/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.runtime.panama;

import com.oracle.truffle.espresso.impl.Klass;

public class WindowsArgumentsCalculator extends AbstractArgumentsCalculator {
    private int globalIndex;
    private boolean inVarArg;

    public WindowsArgumentsCalculator(Platform platform, VMStorage[] callIntRegs, VMStorage[] callFloatRegs, VMStorage intReturn, VMStorage floatReturn) {
        super(platform, callIntRegs, callFloatRegs, intReturn, floatReturn);
    }

    @Override
    public int getNextInputIndex(VMStorage reg, Klass type, VMStorage nextReg, Klass nextType) {
        // TODO this currently depends on order but doesn't actually need to
        assert isInt(type) || isFloat(type) : platform.toString(reg) + ": " + type;
        if (inVarArg) {
            if (skipExtraVarArgMove(reg, type)) {
                return SKIP;
            }
            inVarArg = false;
        } else if (isVarArg(reg, type, nextReg, nextType)) {
            // start of 2 registers used for the same argument
            inVarArg = true;
            if (skipExtraVarArgMove(reg, type)) {
                return SKIP;
            }
        }
        if (globalIndex < callIntRegs.length && callIntRegs[globalIndex].equals(reg)) {
            assert isInt(type) : platform.toString(reg) + ": " + type;
            return globalIndex++;
        }
        if (globalIndex < callFloatRegs.length && callFloatRegs[globalIndex].equals(reg)) {
            assert isFloat(type) : platform.toString(reg) + ": " + type;
            return globalIndex++;
        }
        if (reg.type(platform).isStack()) {
            // TODO validate offset
            assert !isInt(type) || globalIndex >= callIntRegs.length : platform.toString(reg) + ": " + type;
            assert !isFloat(type) || globalIndex >= callFloatRegs.length : platform.toString(reg) + ": " + type;
            return globalIndex++;
        }
        return -1;
    }

    @Override
    public boolean checkReturn(VMStorage reg, Klass type) {
        if (intReturn.equals(reg)) {
            assert isInt(type) : platform.toString(reg) + ": " + type;
            return true;
        }
        if (floatReturn.equals(reg)) {
            assert isFloat(type) : platform.toString(reg) + ": " + type;
            return true;
        }
        return false;
    }

    private boolean skipExtraVarArgMove(VMStorage reg, Klass type) {
        return isFloat(type) && reg.type(platform).isInteger();
    }

    @Override
    public boolean isVarArg(VMStorage reg, Klass type, VMStorage nextReg, Klass nextType) {
        if (nextReg == null) {
            return false;
        }
        if (globalIndex >= callIntRegs.length || globalIndex >= callFloatRegs.length) {
            return false;
        }
        if (isFloat(type) && isFloat(nextType)) {
            // https://learn.microsoft.com/en-us/cpp/build/x64-calling-convention?view=msvc-170
            // for varargs floating point values get passed in both the usual floating-point
            // register and integer register
            if ((callIntRegs[globalIndex].equals(reg) && callFloatRegs[globalIndex].equals(nextReg)) ||
                            (callIntRegs[globalIndex].equals(nextReg) && callFloatRegs[globalIndex].equals(reg))) {
                return true;
            }
        }
        return false;
    }

    @Override
    public String toString() {
        return "DefaultArgumentsCalculator{" +
                        "platform=" + platform +
                        ", globalIndex=" + globalIndex +
                        '}';
    }
}
