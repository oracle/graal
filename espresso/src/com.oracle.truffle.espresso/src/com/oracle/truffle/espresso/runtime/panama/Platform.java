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

import java.util.Locale;

import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.runtime.OS;
import com.oracle.truffle.espresso.runtime.panama.aarch64.AAPCS64;
import com.oracle.truffle.espresso.runtime.panama.aarch64.DarwinAAPCS64;
import com.oracle.truffle.espresso.runtime.panama.x64.SysVx64;
import com.oracle.truffle.espresso.runtime.panama.x64.WindowsX64;

public abstract class Platform {
    public static Platform getHostPlatform() {
        String arch = System.getProperty("os.arch").toLowerCase(Locale.ROOT);
        return switch (arch) {
            case "x86_64", "amd64" -> switch (OS.getCurrent()) {
                case Linux, Darwin -> SysVx64.INSTANCE;
                case Windows -> WindowsX64.INSTANCE;
                default -> throw EspressoError.unimplemented(OS.getCurrent() + "-x86_64");
            };
            case "aarch64", "arm64" -> switch (OS.getCurrent()) {
                case Linux -> AAPCS64.INSTANCE;
                case Darwin -> DarwinAAPCS64.INSTANCE;
                default -> throw EspressoError.unimplemented(OS.getCurrent() + "-aarch64");
            };
            default -> throw EspressoError.unimplemented(arch);
        };
    }

    public abstract StorageType getStorageType(byte id);

    public abstract boolean ignoreDownCallArgument(VMStorage reg);

    public abstract ArgumentsCalculator getArgumentsCalculator();

    @Override
    public String toString() {
        return getClass().getSimpleName();
    }

    public String toString(VMStorage reg) {
        StorageType type = reg.type(this);
        if (type.isPlaceholder()) {
            return reg.getStubLocation(this).toString();
        } else if (type.isStack()) {
            return "stack[" + reg.indexOrOffset() + ";" + reg.segmentMaskOrSize() + "]";
        } else if (type.isInteger()) {
            return getIntegerRegisterName(reg.indexOrOffset(), reg.segmentMaskOrSize());
        } else if (type.isVector()) {
            return getVectorRegisterName(reg.indexOrOffset(), reg.segmentMaskOrSize());
        } else {
            return "??[" + type + ", " + reg.indexOrOffset() + ", " + reg.segmentMaskOrSize() + "]";
        }
    }

    protected abstract String getIntegerRegisterName(int idx, int maskOrSize);

    protected abstract String getVectorRegisterName(int idx, int maskOrSize);
}
