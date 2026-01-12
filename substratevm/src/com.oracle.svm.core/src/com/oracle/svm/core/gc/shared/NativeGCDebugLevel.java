/*
 * Copyright (c) 2019, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.gc.shared;

/** Defines which debug-levels are supported for GCs such as G1. */
public enum NativeGCDebugLevel {
    Product("", 0),
    FastDebug("-fastdebug", 1),
    Debug("-debug", 2);

    private final String libSuffix;
    private final int index;

    NativeGCDebugLevel(String libSuffix, int index) {
        this.libSuffix = libSuffix;
        this.index = index;
    }

    public String getLibSuffix() {
        return libSuffix;
    }

    public int getIndex() {
        return index;
    }

    public static NativeGCDebugLevel fromString(String value) {
        return switch (value) {
            case "product" -> Product;
            case "fastdebug" -> FastDebug;
            case "debug" -> Debug;
            default -> null;
        };
    }
}
