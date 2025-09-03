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

package com.oracle.svm.webimage.substitute;

import org.graalvm.webimage.api.JS;

import com.oracle.svm.webimage.annotation.JSRawCall;

import jdk.internal.misc.Unsafe;

public class WebImageUtil {

    @JSRawCall
    @JS("return Math.random();")
    public static native double random();

    /**
     * The purpose of this method and {@link #unsafeCopyMemory} is to serve as a replacement for
     * {@link com.oracle.svm.core.JavaMemoryUtil} which we cannot use because it does pointer
     * arithmetic.
     */
    public static void unsafeSetMemory(Object destBase, long destOffset, long bytes, byte bvalue) {
        if (destBase == null) {
            for (long i = 0; i < bytes; i++) {
                Unsafe.getUnsafe().putByte(destOffset + i, bvalue);
            }
        } else {
            assert destBase.getClass().isArray() : destBase.getClass();
            for (long i = 0; i < bytes; i++) {
                Unsafe.getUnsafe().putByte(destBase, destOffset + i, bvalue);
            }
        }
    }

    public static void unsafeCopyMemory(Object srcBase, long srcOffset, Object destBase, long destOffset, long bytes) {
        for (long i = 0; i < bytes; i++) {
            byte b;
            if (srcBase == null) {
                b = Unsafe.getUnsafe().getByte(srcOffset + i);
            } else {
                b = Unsafe.getUnsafe().getByte(srcBase, srcOffset + i);
            }
            if (destBase == null) {
                Unsafe.getUnsafe().putByte(destOffset + i, b);
            } else {
                Unsafe.getUnsafe().putByte(destBase, destOffset + i, b);
            }
        }
    }
}
