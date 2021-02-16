/*
 * Copyright (c) 2015, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.util;

import org.graalvm.word.Pointer;
import org.graalvm.word.PointerBase;
import org.graalvm.word.UnsignedWord;

import com.oracle.svm.core.annotate.Uninterruptible;

/**
 * Utility methods on Pointers.
 */
public final class PointerUtils {

    private PointerUtils() {
        // This is a class of static methods, so no need for any instances.
    }

    /**
     * Round a Pointer down to the nearest smaller multiple.
     *
     * @param that The Pointer to be rounded up.
     * @param multiple The multiple to which that Pointer should be decreased.
     * @return That Pointer, but rounded down.
     */
    @Uninterruptible(reason = "Used in uninterruptible code.", mayBeInlined = true)
    public static Pointer roundDown(PointerBase that, UnsignedWord multiple) {
        return (Pointer) UnsignedUtils.roundDown((UnsignedWord) that, multiple);
    }

    /**
     * Round a Pointer up to the nearest larger multiple.
     *
     * @param that The Pointer to be rounded up.
     * @param multiple The multiple to which that Pointer should be increased.
     * @return That Pointer, but rounded up.
     */
    @Uninterruptible(reason = "Used in uninterruptible code.", mayBeInlined = true)
    public static Pointer roundUp(PointerBase that, UnsignedWord multiple) {
        return (Pointer) UnsignedUtils.roundUp((UnsignedWord) that, multiple);
    }

    /**
     * Check that a Pointer is an even multiple.
     *
     * @param that The Pointer to be verified as a multiple.
     * @param multiple The multiple against which the Pointer should be verified.
     * @return true if that Pointer is a multiple, false otherwise.
     */
    @Uninterruptible(reason = "Used in uninterruptible code.", mayBeInlined = true)
    public static boolean isAMultiple(PointerBase that, UnsignedWord multiple) {
        return UnsignedUtils.isAMultiple((UnsignedWord) that, multiple);
    }

    /**
     * Return the distance between two Pointers.
     *
     * @param pointer1 A first Pointer.
     * @param pointer2 A second Pointer.
     * @return The distance in bytes between the two Pointers.
     */
    public static UnsignedWord absoluteDifference(PointerBase pointer1, PointerBase pointer2) {
        Pointer p1 = (Pointer) pointer1;
        Pointer p2 = (Pointer) pointer2;
        final UnsignedWord result;
        if (p1.aboveOrEqual(p2)) {
            result = p1.subtract(p2);
        } else {
            result = p2.subtract(p1);
        }
        return result;
    }

    /**
     * The minimum of two Pointers.
     *
     * @param x A Pointer.
     * @param y Another Pointer.
     * @return The whichever Pointer is smaller.
     */
    public static <T extends PointerBase> T min(T x, T y) {
        return (((Pointer) x).belowOrEqual((Pointer) y)) ? x : y;
    }

    /**
     * The maximum of two Pointers.
     *
     * @param x A Pointer.
     * @param y Another Pointer.
     * @return The whichever Pointer is larger.
     */
    public static <T extends PointerBase> T max(T x, T y) {
        return (((Pointer) x).aboveOrEqual((Pointer) y)) ? x : y;
    }
}
