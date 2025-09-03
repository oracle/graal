/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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
/**
 * Converts the given floating point number to a 32-bit integer using Java's
 * narrowing semantics given in §5.1.3 of the Java SE 8 Language Specification
 */
function toInt(f) {
    if (isNaN(f)) {
        return 0;
    }

    // Integer.MAX_VALUE = 2^31 - 1
    if (f >= 0x7fffffff) {
        return 0x7fffffff;
    }

    /*
     * Integer.MIN_VALUE = -2^31
     * Needs to interpreted as a 32-bit two's complement number otherwise
     * JavaScript doesn't recognize the sign
     */
    if (f <= (0x80000000 | 0)) {
        return 0x80000000 | 0;
    }

    return f | 0;
}

/**
 * Converts the given floating point number to a 64-bit integer using Java's
 * narrowing semantics given in §5.1.3 of the Java SE 8 Language Specification
 */
function toLong(f) {
    return Long64.fromDouble(f);
}
