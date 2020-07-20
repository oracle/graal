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

import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.annotate.Uninterruptible;

/**
 * Utility methods on Unsigned values.
 */
public final class UnsignedUtils {

    /** The UnsignedWord of the greatest magnitude. */
    public static final UnsignedWord MAX_VALUE = WordFactory.unsigned(0xffffffffffffffffL);

    private UnsignedUtils() {
        // This is a class of static methods, so no need for any instances.
    }

    /**
     * Round an Unsigned down to the nearest smaller multiple.
     *
     * @param that The Unsigned to be rounded down.
     * @param multiple The multiple to which that Unsigned should be decreased.
     * @return That Unsigned, but rounded down.
     */
    @Uninterruptible(reason = "Used in uninterruptible code.", mayBeInlined = true)
    public static UnsignedWord roundDown(UnsignedWord that, UnsignedWord multiple) {
        return that.unsignedDivide(multiple).multiply(multiple);
    }

    /**
     * Round an Unsigned up to the nearest larger multiple.
     *
     * @param that The Unsigned to be rounded up.
     * @param multiple The multiple to which that Unsigned should be increased.
     * @return That Unsigned, but rounded up.
     */
    @Uninterruptible(reason = "Used in uninterruptible code.", mayBeInlined = true)
    public static UnsignedWord roundUp(UnsignedWord that, UnsignedWord multiple) {
        return UnsignedUtils.roundDown(that.add(multiple.subtract(1)), multiple);
    }

    /**
     * Check that an Unsigned is an even multiple.
     *
     * @param that The Unsigned to be verified as a multiple.
     * @param multiple The multiple against which the Unsigned should be verified.
     * @return true if that Unsigned is a multiple, false otherwise.
     */
    @Uninterruptible(reason = "Used in uninterruptible code.", mayBeInlined = true)
    public static boolean isAMultiple(UnsignedWord that, UnsignedWord multiple) {
        return that.unsignedRemainder(multiple).equal(0);
    }

    /**
     * The minimum of two Unsigneds.
     *
     * @param x An Unsigned.
     * @param y Another Unsigned.
     * @return The whichever Unsigned is smaller.
     */
    @Uninterruptible(reason = "Used in uninterruptible code.", mayBeInlined = true)
    public static UnsignedWord min(UnsignedWord x, UnsignedWord y) {
        return (x.belowOrEqual(y)) ? x : y;
    }

    /**
     * The maximum of two Unsigneds.
     *
     * @param x An Unsigned.
     * @param y Another Unsigned.
     * @return The whichever Unsigned is larger.
     */
    public static UnsignedWord max(UnsignedWord x, UnsignedWord y) {
        return (x.aboveOrEqual(y)) ? x : y;
    }

    /**
     * Converts an {@link UnsignedWord} to a positive signed {@code int}, asserting that it can be
     * correctly represented.
     */
    public static int safeToInt(UnsignedWord w) {
        long l = w.rawValue();
        assert l >= 0 && l == (int) l;
        return (int) l;
    }
}
