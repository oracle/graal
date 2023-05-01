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
    private static final double BYTES_TO_KiB = 1024d;
    private static final double BYTES_TO_MiB = 1024d * 1024d;
    private static final double BYTES_TO_GiB = 1024d * 1024d * 1024d;

    public static String bytesToHuman(long bytes) {
        return bytesToHuman("%4.2f", bytes);
    }

    public static String bytesToHuman(String format, long bytes) {
        if (bytes < BYTES_TO_KiB) {
            return String.format(format, (double) bytes) + "B";
        } else if (bytes < BYTES_TO_MiB) {
            return String.format(format, bytesToKiB(bytes)) + "kB";
        } else if (bytes < BYTES_TO_GiB) {
            return String.format(format, bytesToMiB(bytes)) + "MB";
        } else {
            return String.format(format, bytesToGiB(bytes)) + "GB";
        }
    }

    static double bytesToKiB(long bytes) {
        return bytes / BYTES_TO_KiB;
    }

    static double bytesToGiB(long bytes) {
        return bytes / BYTES_TO_GiB;
    }

    static double bytesToMiB(long bytes) {
        return bytes / BYTES_TO_MiB;
    }
}
