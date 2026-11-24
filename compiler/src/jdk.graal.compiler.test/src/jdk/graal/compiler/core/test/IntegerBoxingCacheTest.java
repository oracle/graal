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

import java.io.IOException;

import org.junit.Test;

public class IntegerBoxingCacheTest extends SubprocessTest {

    /// Compares boxed constants within the required cache range.
    public static boolean cachedConstants() {
        return Integer.valueOf(-128) == Integer.valueOf(-128) &&
                        Integer.valueOf(0) == Integer.valueOf(0) &&
                        Integer.valueOf(127) == Integer.valueOf(127);
    }

    /// Compares the boxed constant from the regression reproducer.
    public static boolean same() {
        return Integer.valueOf(100) == Integer.valueOf(100);
    }

    /// Compares boxed constants below the cache range.
    public static boolean belowCache() {
        return Integer.valueOf(-129) == Integer.valueOf(-129);
    }

    /// Compares boxed constants at the upper bound of a cache extended through 128.
    public static boolean extendedCache() {
        return Integer.valueOf(128) == Integer.valueOf(128);
    }

    /// Compares boxed constants above every cache range configured by this test.
    public static boolean aboveCache() {
        return Integer.valueOf(129) == Integer.valueOf(129);
    }

    /// Checks compiled identity comparisons in a VM started with the requested cache limit.
    private void checkCache(int high) throws IOException, InterruptedException {
        // The property is saved during VM startup, so each limit needs a fresh VM.
        launchSubprocess(() -> {
            test("same");
            test("cachedConstants");
            test("belowCache");
            test("extendedCache");
            test("aboveCache");
        }, "-Djava.lang.Integer.IntegerCache.high=" + high);
    }

    @Test
    public void testZeroLimit() throws IOException, InterruptedException {
        checkCache(0);
    }

    @Test
    public void testNegativeLimit() throws IOException, InterruptedException {
        checkCache(-1);
    }

    @Test
    public void testBelowMinimumLimit() throws IOException, InterruptedException {
        checkCache(126);
    }

    @Test
    public void testMinimumLimit() throws IOException, InterruptedException {
        checkCache(127);
    }

    @Test
    public void testExtendedLimit() throws IOException, InterruptedException {
        checkCache(128);
    }
}
