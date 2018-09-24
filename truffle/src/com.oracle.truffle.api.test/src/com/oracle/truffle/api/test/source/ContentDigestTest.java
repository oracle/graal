/*
 * Copyright (c) 2015, 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.test.source;

import java.lang.reflect.Method;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.util.Random;

import org.junit.Assert;
import org.junit.Test;

import com.oracle.truffle.api.test.ReflectionUtils;

public class ContentDigestTest {
    @Test
    public void emptyMD2() throws Exception {
        assertDigest(new byte[0], "Empty MD2 digest");
    }

    @Test
    public void hiMD2() throws Exception {
        assertDigest("Hi".getBytes("UTF-8"), "Empty MD2 digest");
    }

    @Test
    public void helloWorldMD2() throws Exception {
        assertDigest("Hello World!".getBytes("UTF-8"), "Empty MD2 digest");
    }

    @Test
    public void minusMD2() throws Exception {
        assertDigest(new byte[]{-75, 119}, "MD2 digest for negative byte");
    }

    @Test
    public void computeMD2s() throws Exception {
        for (int i = 0; i < 100; i++) {
            long seed = System.currentTimeMillis();
            final String msg = "Digest for seed " + seed + " is the same";

            Random random = new Random(seed);

            int len = random.nextInt(2048);
            byte[] arr = new byte[len];
            random.nextBytes(arr);

            assertDigest(arr, msg);
        }
    }

    private static void assertDigest(byte[] arr, final String msg) throws Exception {
        byte[] result = MessageDigest.getInstance("MD2").digest(arr);
        String expecting = new BigInteger(1, result).toString(16);

        Method m = Class.forName("com.oracle.truffle.api.source.Source").getDeclaredMethod("digest", byte[].class, int.class, int.class);
        ReflectionUtils.setAccessible(m, true);
        String own = (String) m.invoke(null, arr, 0, arr.length);

        Assert.assertEquals(msg, expecting, own);
    }

}
