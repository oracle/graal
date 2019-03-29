/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.nfi.test.parser;

import com.oracle.truffle.nfi.spi.types.NativeSignature;
import org.junit.Assert;
import org.junit.Test;

public class VarargsParseSignatureTest extends ParseSignatureTest {

    private static void testVarargs(String signatureString, int expectedArgCount, int expectedFixedArgCount) {
        NativeSignature signature = parseSignature(signatureString);
        Assert.assertEquals("argument count", expectedArgCount, signature.getArgTypes().size());
        Assert.assertEquals("fixed argument count", expectedFixedArgCount, signature.getFixedArgCount());
        if (expectedArgCount == expectedFixedArgCount) {
            Assert.assertFalse(signature.isVarargs());
        } else {
            Assert.assertTrue(signature.isVarargs());
        }
    }

    @Test
    public void testFixedArgs() {
        testVarargs("(float, double) : void", 2, 2);
    }

    @Test
    public void testNoFixedArgs() {
        testVarargs("(...float, double) : void", 2, 0);
    }

    @Test
    public void testTwoFixedArgs() {
        testVarargs("(object, pointer, ...float, double) : void", 4, 2);
    }

    @Test
    public void testOneVararg() {
        testVarargs("(string, ...sint32) : void", 2, 1);
    }

    @Test
    public void testTwoVarargs() {
        testVarargs("(string, ...object, uint32) : void", 3, 1);
    }
}
