/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.libjavavm;

public enum OS {
    Darwin,
    Linux,
    Solaris,
    Windows;

    private static final OS current = findCurrent();

    private static OS findCurrent() {
        final String name = System.getProperty("os.name");
        if (name.equals("Linux")) {
            return OS.Linux;
        }
        if (name.equals("SunOS")) {
            return OS.Solaris;
        }
        if (name.equals("Mac OS X") || name.equals("Darwin")) {
            return OS.Darwin;
        }
        if (name.startsWith("Windows")) {
            return OS.Windows;
        }
        throw new RuntimeException("shouldNotReachHere: unknown OS: " + name);
    }

    public static OS getCurrent() {
        return current;
    }

    public static boolean isWindows() {
        return getCurrent() == OS.Windows;
    }

    public static boolean isUnix() {
        return getCurrent() != OS.Windows;
    }
}
