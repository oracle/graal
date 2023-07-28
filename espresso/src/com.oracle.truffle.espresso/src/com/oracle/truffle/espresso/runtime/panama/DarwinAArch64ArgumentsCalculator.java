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

public class DarwinAArch64ArgumentsCalculator extends DefaultArgumentsCalculator {
    // see https://developer.apple.com/documentation/xcode/writing-arm64-code-for-apple-platforms
    private boolean isVarArgs;
    private int varArgsStackOffset;

    public DarwinAArch64ArgumentsCalculator(Platform platform, VMStorage[] callIntRegs, VMStorage[] callFloatRegs, VMStorage intReturn, VMStorage floatReturn) {
        super(platform, callIntRegs, callFloatRegs, intReturn, floatReturn);
    }

    @Override
    public boolean isVarArgsStart(VMStorage reg, Klass type) {
        if (reg.type(platform).isStack()) {
            if (isInt(type)) {
                return intIndex < callIntRegs.length;
            }
            if (isFloat(type)) {
                return floatIndex < callFloatRegs.length;
            }
        }
        return super.isVarArgsStart(reg, type);
    }

    @Override
    public int getNextInputIndex(VMStorage reg, Klass type) {
        if (!isVarArgs) {
            isVarArgs = isVarArgsStart(reg, type);
        }
        if (isVarArgs) {
            assert reg.type(platform).isStack();
            assert varArgsStackOffset == reg.indexOrOffset();
            varArgsStackOffset += 8;
            return globalIndex++;
        }
        return super.getNextInputIndex(reg, type);
    }
}
