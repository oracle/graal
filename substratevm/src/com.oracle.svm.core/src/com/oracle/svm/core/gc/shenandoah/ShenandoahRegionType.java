/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.gc.shenandoah;

import com.oracle.svm.core.util.DuplicatedInNativeCode;

/**
 * There are two types of image heap regions, which differ in the kind of references that are
 * allowed for the contained objects:
 *
 * <ul>
 * <li>Closed image heap regions may only contain references to other image heap regions. The GC
 * does not visit any objects in such regions. Note that parts of the closed image heap are
 * read-only.</li>
 * <li>Open image heap regions allow references to any other region in the image or collected Java
 * heap. The GC visits open image heap regions and adjusts pointers in these regions. However, it
 * doesn't move or collect any objects that are in open image heap regions.</li>
 * </ul>
 */
@DuplicatedInNativeCode
public enum ShenandoahRegionType {
    ClosedImageHeap(Flags.ClosedImageHeapBit),
    ClosedImageHeapStartsHumongous(Flags.ClosedImageHeapBit | Flags.StartsHumongousBit),
    ClosedImageHeapContinuesHumongous(Flags.ClosedImageHeapBit | Flags.ContinuesHumongousBit),

    OpenImageHeap(Flags.OpenImageHeapBit),
    OpenImageHeapStartsHumongous(Flags.OpenImageHeapBit | Flags.StartsHumongousBit),
    OpenImageHeapContinuesHumongous(Flags.OpenImageHeapBit | Flags.ContinuesHumongousBit);

    private final byte tag;

    ShenandoahRegionType(int tag) {
        assert tag >= 0 && tag < Byte.MAX_VALUE : tag;
        this.tag = (byte) tag;
    }

    public byte getTag() {
        return tag;
    }

    public boolean isHumongous() {
        return (tag & Flags.HumongousBits) != 0;
    }

    public static boolean isHumongous(byte tag) {
        return (tag & Flags.HumongousBits) != 0;
    }

    public static boolean isContinuesHumongous(byte tag) {
        return (tag & Flags.ContinuesHumongousBit) != 0;
    }

    public boolean isClosedImageHeap() {
        return (tag & Flags.ClosedImageHeapBit) != 0;
    }

    public static String toString(int tag) {
        if (tag == OpenImageHeap.tag) {
            return "OI";
        } else if (tag == ClosedImageHeap.tag) {
            return "CI";
        } else if (tag == OpenImageHeapStartsHumongous.tag) {
            return "OIHS";
        } else if (tag == OpenImageHeapContinuesHumongous.tag) {
            return "OIHC";
        } else if (tag == ClosedImageHeapStartsHumongous.tag) {
            return "CIHS";
        } else if (tag == ClosedImageHeapContinuesHumongous.tag) {
            return "CIHC";
        } else {
            return "?";
        }
    }

    // Constants that need to match the C++ side
    private static final class Flags {
        private static final byte StartsHumongousBit = 0b0001;
        private static final byte ContinuesHumongousBit = 0b0010;
        private static final byte HumongousBits = StartsHumongousBit | ContinuesHumongousBit;

        private static final byte ClosedImageHeapBit = 0b0100;
        private static final byte OpenImageHeapBit = 0b1000;
    }
}
