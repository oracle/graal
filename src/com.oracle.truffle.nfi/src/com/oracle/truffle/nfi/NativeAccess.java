/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.nfi;

class NativeAccess {

    static {
        String nfiLib = System.getProperty("truffle.nfi.library");
        if (nfiLib == null) {
            System.loadLibrary("trufflenfi");
            nfiLib = System.mapLibraryName("trufflenfi");
        } else {
            System.load(nfiLib);
        }
        initialize(nfiLib, LibFFIType.simpleTypeMap);
    }

    static void ensureInitialized() {
    }

    // initialized by native code
    // Checkstyle: stop field name check
    static int RTLD_GLOBAL;
    static int RTLD_LOCAL;
    static int RTLD_LAZY;
    static int RTLD_NOW;
    // Checkstyle: resume field name check

    private static native void initialize(String libName, LibFFIType[] simpleTypeMap);

    static native long loadLibrary(String name, int flags);

    static native void freeLibrary(long library);

    static native long lookup(long library, String name);
}
