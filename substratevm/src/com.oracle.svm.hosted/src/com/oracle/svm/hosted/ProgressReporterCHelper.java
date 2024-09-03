/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
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

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import com.oracle.svm.core.util.VMError;

public final class ProgressReporterCHelper {
    private static final int DEFAULT_CHARACTERS_PER_LINE = 80;
    static final int MAX_CHARACTERS_PER_LINE = 120;

    static {
        loadCHelperLibrary();
    }

    private static void loadCHelperLibrary() {
        Path javaHome = Paths.get(System.getProperty("java.home"));
        String libName = System.mapLibraryName("reporterchelper");
        Path libRSSHelperPath = javaHome.resolve(Paths.get("lib", "svm", "builder", "lib", libName));
        if (Files.exists(libRSSHelperPath)) {
            System.load(libRSSHelperPath.toString());
        } else {
            throw VMError.shouldNotReachHere("Helper library for ProgressReporterCHelper not available");
        }
    }

    private ProgressReporterCHelper() {
    }

    public static int getTerminalWindowColumnsClamped() {
        return Math.min(Math.max(DEFAULT_CHARACTERS_PER_LINE, ProgressReporterCHelper.getTerminalWindowColumns()), MAX_CHARACTERS_PER_LINE);
    }

    /**
     * @return get terminal window columns or {@link #DEFAULT_CHARACTERS_PER_LINE} if unavailable.
     */
    public static int getTerminalWindowColumns() {
        try {
            return getTerminalWindowColumns0();
        } catch (UnsatisfiedLinkError e) {
            throw VMError.shouldNotReachHere("ProgressReporterCHelper.getTerminalWindowColumns0 native method not available");
        }
    }

    /**
     * @return peak RSS (in bytes) or -1 if unavailable.
     */
    public static long getPeakRSS() {
        try {
            return getPeakRSS0();
        } catch (UnsatisfiedLinkError e) {
            throw VMError.shouldNotReachHere("ProgressReporterCHelper.getPeakRSS0 native method not available");
        }
    }

    private static native long getPeakRSS0();

    private static native int getTerminalWindowColumns0();

}
