/*
 * Copyright (c) 2012, 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.jtt.jdk;

import org.junit.*;


public class LongBits {
    @SuppressWarnings("unused")
    private static long init = Long.reverseBytes(42);
    private long original = 0x0102030405060708L;
    private long reversed = 0x0807060504030201L;
    private long v = 0b1000L;
    private long zero = 0L;

    public long test(long o) {
        return Long.reverseBytes(o);
    }

    public int test2(long o) {
        return Long.numberOfLeadingZeros(o);
    }

    public int test3(long o) {
        return Long.numberOfTrailingZeros(o);
    }

    @Test
    public void run0() {
        Assert.assertEquals(reversed, test(original));
    }

    @Test
    public void run1() {
        Assert.assertEquals(3, test3(v));
    }

    @Test
    public void run2() {
        Assert.assertEquals(60, test2(v));
    }

    @Test
    public void run3() {
        Assert.assertEquals(64, test3(zero));
    }

    @Test
    public void run4() {
        Assert.assertEquals(64, test2(zero));
    }

    @Test
    public void run5() {
        Assert.assertEquals(reversed, test(0x0102030405060708L));
    }

    @Test
    public void run6() {
        Assert.assertEquals(3, test3(0b1000L));
    }

    @Test
    public void run7() {
        Assert.assertEquals(60, test2(0b1000L));
    }

    @Test
    public void run8() {
        Assert.assertEquals(64, test3(0L));
    }

    @Test
    public void run9() {
        Assert.assertEquals(64, test2(0L));
    }
}
