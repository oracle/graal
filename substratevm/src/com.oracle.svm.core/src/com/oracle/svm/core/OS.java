/*
 * Copyright (c) 2016, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core;

/**
 * Enumerated type for operating systems.
 */
public enum OS {

    DARWIN("Darwin", false),
    LINUX("Linux", true),
    SOLARIS("Solaris", true),
    WINDOWS("Windows", false);

    /**
     * The identifier of this OS as part of a class name.
     */
    public final String className;
    public final boolean hasProcFS;

    OS(String className, boolean hasProcFS) {
        this.className = className;
        this.hasProcFS = hasProcFS;
    }

    /**
     * Returns a string that can be used in a package name.
     */
    public String asPackageName() {
        return name().toLowerCase();
    }

    public boolean isCurrent() {
        return getCurrent() == this;
    }

    private static OS findCurrent() {
        final String name = System.getProperty("os.name");
        if (name.equals("Linux")) {
            return LINUX;
        }
        if (name.equals("SunOS")) {
            return SOLARIS;
        }
        if (name.equals("Mac OS X") || name.equals("Darwin")) {
            return DARWIN;
        }
        if (name.contains("Windows")) {
            return WINDOWS;
        }
        throw new IllegalArgumentException("unknown OS: " + name);
    }

    private static final OS current = findCurrent();

    public static OS getCurrent() {
        return current;
    }
}
