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

import java.util.EnumSet;

import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.runtime.OS;

public enum CapturableState {
    GET_LAST_ERROR(1 << 0, EnumSet.of(OS.Windows)),
    WSA_GET_LAST_ERROR(1 << 1, EnumSet.of(OS.Windows)),
    ERRNO(1 << 2, EnumSet.allOf(OS.class));
    // see jdk.internal.foreign.abi.CapturableState

    private final int mask;
    private final EnumSet<OS> supportedOS;

    CapturableState(int mask, EnumSet<OS> supportedOS) {
        this.mask = mask;
        this.supportedOS = supportedOS;
    }

    public boolean isSupported(OS os) {
        return supportedOS.contains(os);
    }

    public static EnumSet<CapturableState> fromMask(int m) {
        EnumSet<CapturableState> set = EnumSet.noneOf(CapturableState.class);
        int leftOver = 0;
        for (CapturableState state : CapturableState.values()) {
            if ((state.mask & m) != 0) {
                leftOver &= ~state.mask;
                set.add(state);
            }
        }
        if (leftOver != 0) {
            throw EspressoError.unimplemented("Unknown CapturableState mask: 0x%08x".formatted(leftOver));
        }
        return set;
    }

    public static int toMask(EnumSet<CapturableState> states) {
        int m = 0;
        for (CapturableState state : states) {
            m |= state.mask;
        }
        return m;
    }
}
