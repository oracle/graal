/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.truffle.espresso.runtime;

/**
 * Utility class to provide version checking predicates for clarity in the code base. Avoids
 * cluttering {@link EspressoContext}.
 * 
 * Makes it harder to access the raw int version: please add new predicates instead.
 */
public final class JavaVersion {
    private static final String HOST_VERSION = System.getProperty("java.version");
    public static final boolean HOST_COMPACT_STRINGS = !HOST_VERSION.startsWith("1.");

    public static final int LATEST_SUPPORTED = 11;

    private final int version;

    JavaVersion(int version) {
        this.version = version;
    }

    public boolean java8OrEarlier() {
        return version <= 8;
    }

    public boolean java9OrLater() {
        return version >= 9;
    }

    public boolean java11OrLater() {
        return version >= 11;
    }

    public boolean modulesEnabled() {
        return java9OrLater();
    }

    public boolean varHandlesEnabled() {
        return java9OrLater();
    }

    public boolean compactStringsEnabled() {
        return java9OrLater();
    }

    public int classFileVersion() {
        return version + 44;
    }

    @Override
    public String toString() {
        return Integer.toString(version);
    }

    public boolean matchesVersion(int version) {
        return this.version == version;
    }
}
