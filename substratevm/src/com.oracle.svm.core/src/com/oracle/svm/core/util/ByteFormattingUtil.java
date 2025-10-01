/*
 * Copyright (c) 2022, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.util;

public class ByteFormattingUtil {
    // "123.12KiB".length() = 9, holds as long as it's not >= 1000GiB
    private static final int MAX_WIDTH = 9;
    public static final String RIGHT_ALIGNED_FORMAT = "%" + MAX_WIDTH + "s";

    private enum Unit {
        KiB(1024L),
        MiB(1024L * 1024L),
        GiB(1024L * 1024L * 1024L);

        private final long value;

        Unit(long value) {
            this.value = value;
        }
    }

    // We want to respect MAX_WIDTH and keep it concise,
    // so we prefer to show 0.99MiB than 1010.00KiB (length 10).
    public static String bytesToHuman(long bytes) {
        assert bytes >= 0;
        if (bytes < 1_000) {
            return bytes + "B";
        } else if (bytes < 1_000 * Unit.KiB.value) {
            return toHuman(bytes, Unit.KiB);
        } else if (bytes < 1_000 * Unit.MiB.value) {
            return toHuman(bytes, Unit.MiB);
        } else {
            return bytesToHumanGB(bytes);
        }
    }

    public static String bytesToHumanGB(long bytes) {
        return toHuman(bytes, Unit.GiB);
    }

    private static String toHuman(long value, Unit unit) {
        String string = "%.2f%s".formatted((double) value / unit.value, unit);
        assert string.length() <= MAX_WIDTH || value >= 1000L * Unit.GiB.value;
        return string;
    }
}
