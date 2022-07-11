/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.core.common;

import org.graalvm.compiler.debug.GraalError;

import jdk.vm.ci.meta.JavaKind;

/**
 * A byte stride.
 */
public enum Stride {
    S1(1, 0),
    S2(2, 1),
    S4(4, 2),
    S8(8, 3);

    Stride(int value, int log2) {
        this.value = value;
        this.log2 = log2;
    }

    /**
     * The value (or multiplier) of this stride.
     */
    public final int value;

    /**
     * The {@linkplain #value value} of this stride log 2.
     */
    public final int log2;

    public int getBitCount() {
        return value << 3;
    }

    /**
     * Creates a {@link Stride} for the scaling factor in {@code scale}.
     */
    public static Stride fromInt(int scale) {
        switch (scale) {
            case 1:
                return S1;
            case 2:
                return S2;
            case 4:
                return S4;
            case 8:
                return S8;
            default:
                throw GraalError.shouldNotReachHere("Unsupported stride: " + scale);
        }
    }

    /**
     * Creates a {@link Stride} for the log2 scaling factor {@code shift}.
     */
    public static Stride fromLog2(int shift) {
        switch (shift) {
            case 0:
                return S1;
            case 1:
                return S2;
            case 2:
                return S4;
            case 3:
                return S8;
            default:
                throw GraalError.shouldNotReachHere("Unsupported stride: " + (1 << shift));
        }
    }

    public static Stride fromJavaKind(JavaKind kind) {
        return fromInt(kind.getByteCount());
    }

    public static Stride min(Stride a, Stride b) {
        return a.value < b.value ? a : b;
    }

    public static Stride max(Stride a, Stride b) {
        return a.value > b.value ? a : b;
    }
}
