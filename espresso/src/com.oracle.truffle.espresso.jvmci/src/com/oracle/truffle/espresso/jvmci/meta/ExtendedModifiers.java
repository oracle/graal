/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.jvmci.meta;

public final class ExtendedModifiers {
    public static final int SYNTHETIC = 0x00001000;
    public static final int ENUM = 0x00004000;
    public static final int BRIDGE = 0x00000040;
    public static final int VARARGS = 0x00000080;
    public static final int ANNOTATION = 0x00002000;
    public static final int HIDDEN = 0x00100000;
    public static final int FINALIZER = 0x00010000;
    static final int STABLE_FIELD = 0x00010000;
    static final int SCOPED_METHOD = 0x00200000;

    private ExtendedModifiers() {
    }
}
