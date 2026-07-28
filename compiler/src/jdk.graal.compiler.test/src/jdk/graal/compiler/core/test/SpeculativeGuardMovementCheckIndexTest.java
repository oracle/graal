/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.core.test;

import java.util.Objects;

import org.junit.Test;

import jdk.graal.compiler.api.directives.GraalDirectives;
import jdk.vm.ci.code.InstalledCode;
import jdk.vm.ci.code.InvalidInstalledCodeException;

/** Regression test for unsigned counted loop trip counts used by speculative guard movement. */
public class SpeculativeGuardMovementCheckIndexTest extends GraalCompilerTest {

    /** Exercises a counted loop whose unsigned trip count exceeds the signed 32 bit range. */
    public static int checkIndexWithLargeUnsignedTripCount(int stop, int index) {
        int sum = 0;
        for (int i = 100; GraalDirectives.injectIterationCount(100, i > Integer.MIN_VALUE); i--) {
            sum += Objects.checkIndex(index, i);
            if (i == stop) {
                return sum;
            }
        }
        return sum;
    }

    /** Verifies that the original check is not replaced by an unsound hoisted check. */
    @Test
    public void testCheckIndexWithLargeUnsignedTripCount() throws InvalidInstalledCodeException {
        for (int i = 0; i < 10_000; i++) {
            checkIndexWithLargeUnsignedTripCount(1, 0);
        }
        InstalledCode code = getCode(getResolvedJavaMethod("checkIndexWithLargeUnsignedTripCount"));
        try {
            code.executeVarargs(-2, 0);
            throw new AssertionError("Expected IndexOutOfBoundsException");
        } catch (IndexOutOfBoundsException expected) {
            /* Expected. */
        }
    }

    /** Exercises a loop with an unsigned trip count of 2^31 + 1. */
    public static int checkIndexWithMaxValueStart(int stop, int index) {
        int sum = 0;
        for (int i = Integer.MAX_VALUE; GraalDirectives.injectIterationCount(100, i >= -1); i--) {
            sum += Objects.checkIndex(index, i);
            if (i == stop) {
                return sum;
            }
        }
        return sum;
    }

    /** Verifies the guard for a trip count whose signed representation is Integer.MIN_VALUE. */
    @Test
    public void testCheckIndexWithMaxValueStart() throws InvalidInstalledCodeException {
        for (int i = 0; i < 10_000; i++) {
            checkIndexWithMaxValueStart(Integer.MAX_VALUE - 100, 0);
        }
        InstalledCode code = getCode(getResolvedJavaMethod("checkIndexWithMaxValueStart"));
        try {
            code.executeVarargs(-2, 0);
            throw new AssertionError("Expected IndexOutOfBoundsException");
        } catch (IndexOutOfBoundsException expected) {
            /* Expected. */
        }
    }
}
