/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.test;

import static org.junit.Assert.assertEquals;

import org.graalvm.nativeimage.ImageInfo;
import org.junit.Assume;
import org.junit.Test;

import com.oracle.svm.guest.staging.core.jdk.UninterruptibleUtils;
import com.oracle.svm.shared.NeverInline;

/**
 * Tests the native compiler markers in {@link UninterruptibleUtils} end to end.
 *
 * The marker methods intentionally have neither a Java implementation nor a native library
 * implementation. During Native Image graph parsing, required hosted invocation plugins must
 * replace their calls with the corresponding Graal compiler nodes. Consequently, successfully
 * building and running these tests proves that no native call survives compilation. Comparing the
 * results with the JDK operations additionally verifies that each marker is replaced with the
 * correct operation.
 *
 * The {@link NeverInline} wrappers keep each marker in an independently compiled method whose
 * operand is a parameter. This prevents constant folding at the test call sites from bypassing the
 * plugin and its lowering.
 *
 * The test image opens the internal guest-staging package only to its unnamed application module;
 * the production module remains encapsulated.
 */
@NativeImageBuildArgs("--add-exports=org.graalvm.nativeimage.guest.staging/com.oracle.svm.guest.staging.core.jdk=ALL-UNNAMED")
public class UninterruptibleUtilsIntrinsicTest {

    private static final int[] INT_VALUES = {
                    Integer.MIN_VALUE, -1, 0, 1, 0x0001_0000, Integer.MAX_VALUE
    };

    private static final long[] LONG_VALUES = {
                    Long.MIN_VALUE, -1L, 0L, 1L, 0x0001_0000_0000L, Long.MAX_VALUE
    };

    /**
     * Keeps the leading-zero marker call in a separately compiled method with a nonconstant
     * operand.
     */
    @NeverInline("Keep the compiler-marker argument nonconstant during graph parsing.")
    private static int guestNumberOfLeadingZeros(int value) {
        return UninterruptibleUtils.Integer.numberOfLeadingZeros(value);
    }

    /**
     * Keeps the trailing-zero marker call in a separately compiled method with a nonconstant
     * operand.
     */
    @NeverInline("Keep the compiler-marker argument nonconstant during graph parsing.")
    private static int guestCountTrailingZeros(long value) {
        return UninterruptibleUtils.Long.countTrailingZeros(value);
    }

    /** Verifies the leading-zero marker replacement and its result semantics. */
    @Test
    public void testNumberOfLeadingZeros() {
        Assume.assumeTrue("Native Image only", ImageInfo.inImageRuntimeCode());

        for (int value : INT_VALUES) {
            assertEquals(java.lang.Integer.numberOfLeadingZeros(value), guestNumberOfLeadingZeros(value));
        }
    }

    /** Verifies the trailing-zero marker replacement and its result semantics. */
    @Test
    public void testCountTrailingZeros() {
        Assume.assumeTrue("Native Image only", ImageInfo.inImageRuntimeCode());

        for (long value : LONG_VALUES) {
            assertEquals(java.lang.Long.numberOfTrailingZeros(value), guestCountTrailingZeros(value));
        }
    }
}
