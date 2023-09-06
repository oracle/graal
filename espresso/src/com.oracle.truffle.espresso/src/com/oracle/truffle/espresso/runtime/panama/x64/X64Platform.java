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
package com.oracle.truffle.espresso.runtime.panama.x64;

import com.oracle.truffle.espresso.runtime.panama.Platform;
import com.oracle.truffle.espresso.runtime.panama.StorageType;

public abstract class X64Platform extends Platform {
    @Override
    public StorageType getStorageType(byte id) {
        return X64StorageType.get(id);
    }

    @Override
    protected String getIntegerRegisterName(int idx, int maskOrSize) {
        if (maskOrSize == X64Regs.REG64_MASK) {
            return X64Regs.getIntegerRegisterName(idx);
        } else {
            return "?INT_REG?[" + idx + ", " + maskOrSize + "]";
        }
    }

    @Override
    protected String getVectorRegisterName(int idx, int maskOrSize) {
        if (maskOrSize == X64Regs.XMM_MASK) {
            return X64Regs.getVectorRegisterName(idx);
        } else {
            return "?VEC_REG?[" + idx + ", " + maskOrSize + "]";
        }
    }
}
