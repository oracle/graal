/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted;

public class ByteFormattingUtil {
    private static final double BYTES_TO_KB = 1000d;
    private static final double BYTES_TO_MB = 1000d * 1000d;
    private static final double BYTES_TO_GB = 1000d * 1000d * 1000d;

    public static String bytesToHuman(long bytes) {
        assert bytes >= 0;
        if (bytes < BYTES_TO_KB) {
            return plainBytes(bytes, "B");
        } else if (bytes < BYTES_TO_MB) {
            return toHuman(bytes / BYTES_TO_KB, "kB");
        } else if (bytes < BYTES_TO_GB) {
            return toHuman(bytes / BYTES_TO_MB, "MB");
        } else {
            return bytesToHumanGB(bytes);
        }
    }

    public static String bytesToHumanGB(long bytes) {
        return toHuman(bytes / BYTES_TO_GB, "GB");
    }

    private static String toHuman(double value, String unit) {
        return "%.2f%s".formatted(value, unit);
    }

    private static String plainBytes(long value, String unit) {
        assert 0 <= value && value < BYTES_TO_KB;
        return "%d%s".formatted(value, unit);
    }
}
