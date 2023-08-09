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

    public WindowsArgumentsCalculator(Platform platform, VMStorage[] callIntRegs, VMStorage[] callFloatRegs, VMStorage intReturn, VMStorage floatReturn) {
        super(platform, callIntRegs, callFloatRegs, intReturn, floatReturn);
    }

    @Override
    public int getNextInputIndex(VMStorage reg, Klass type) {
        // TODO this currently depends on order but doesn't actually need to
        assert isInt(type) || isFloat(type);
        if (globalIndex < callIntRegs.length && callIntRegs[globalIndex].equals(reg)) {
            assert isInt(type);
            return globalIndex++;
        }
        if (globalIndex < callFloatRegs.length && callFloatRegs[globalIndex].equals(reg)) {
            assert isFloat(type);
            return globalIndex++;
        }
        if (reg.type(platform).isStack()) {
            // TODO validate offset
            assert !isInt(type) || globalIndex >= callIntRegs.length;
            assert !isFloat(type) || globalIndex >= callFloatRegs.length;
            return globalIndex++;
        }
        return -1;
    }

    @Override
    public boolean checkReturn(VMStorage reg, Klass type) {
        if (intReturn.equals(reg)) {
            assert isInt(type);
            return true;
        }
        if (floatReturn.equals(reg)) {
            assert isFloat(type);
            return true;
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
