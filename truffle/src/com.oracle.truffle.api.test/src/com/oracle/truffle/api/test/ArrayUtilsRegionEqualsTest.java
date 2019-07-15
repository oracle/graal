/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.api.test;

import static com.oracle.truffle.api.test.ArrayUtilsTest.toByteArray;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import com.oracle.truffle.api.ArrayUtils;

@RunWith(Parameterized.class)
public class ArrayUtilsRegionEqualsTest {

    @Parameters(name = "{index}: fromIndex1 {1} fromIndex2 {3} length {4} expected {6}")
    public static Iterable<Object[]> data() {
        return ArrayUtilsRegionEqualsWithMaskTest.data(false);
    }

    private final String a1;
    private final int fromIndex1;
    private final String a2;
    private final int fromIndex2;
    private final int length;
    private final boolean expected;

    public ArrayUtilsRegionEqualsTest(String a1, int fromIndex1, String a2, int fromIndex2, int length, @SuppressWarnings("unused") int length2, boolean expected) {
        this.a1 = a1;
        this.fromIndex1 = fromIndex1;
        this.a2 = a2;
        this.fromIndex2 = fromIndex2;
        this.length = length;
        this.expected = expected;
    }

    @Test
    public void test() {
        Assert.assertEquals(expected, ArrayUtils.regionEquals(toByteArray(a1), fromIndex1, toByteArray(a2), fromIndex2, length));
        Assert.assertEquals(expected, ArrayUtils.regionEquals(a1.toCharArray(), fromIndex1, a2.toCharArray(), fromIndex2, length));
        Assert.assertEquals(expected, ArrayUtils.regionEquals(a1, fromIndex1, a2, fromIndex2, length));
    }
}
