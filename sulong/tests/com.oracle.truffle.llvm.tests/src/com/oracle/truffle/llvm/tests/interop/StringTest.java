/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to
 * endorse or promote products derived from this software without specific prior written
 * permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.oracle.truffle.llvm.tests.interop;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.llvm.tests.interop.values.BoxedStringValue;
import com.oracle.truffle.tck.TruffleRunner;
import com.oracle.truffle.tck.TruffleRunner.Inject;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

@RunWith(TruffleRunner.class)
public class StringTest extends InteropTestBase {

    private static final String ASCII_STRING = "Hello, World!";
    private static final int ASCII_LENGTH = ASCII_STRING.length();

    private static final String UNICODE_STRING = "test unicode \u00e4\u00e1\u00e7\u20ac";
    private static final int UNICODE_LENGTH_UTF8 = StandardCharsets.UTF_8.encode(UNICODE_STRING).limit();
    private static final int UNICODE_LENGTH_UTF32 = Charset.forName("utf-32").encode(UNICODE_STRING).limit();

    private static TruffleObject testLibrary;

    @BeforeClass
    public static void loadTestBitcode() {
        testLibrary = loadTestBitcodeInternal("stringTest.c");
    }

    public class TestStringSizeNode extends SulongTestNode {

        public TestStringSizeNode() {
            super(testLibrary, "test_get_string_size");
        }
    }

    private static void testStringSize(CallTarget getSize, String str) {
        Object ret = getSize.call(str);
        Assert.assertEquals((long) str.length(), ret);
    }

    @Test
    public void testStringSizeAscii(@Inject(TestStringSizeNode.class) CallTarget getSize) {
        testStringSize(getSize, ASCII_STRING);
    }

    @Test
    public void testStringSizeUnicode(@Inject(TestStringSizeNode.class) CallTarget getSize) {
        testStringSize(getSize, UNICODE_STRING);
    }

    @Test
    public void testStringSizeBoxed(@Inject(TestStringSizeNode.class) CallTarget getSize) {
        Object ret = getSize.call(new BoxedStringValue(ASCII_STRING));
        Assert.assertEquals((long) ASCII_STRING.length(), ret);
    }

    public class TestAsStringAsciiNode extends SulongTestNode {

        public TestAsStringAsciiNode() {
            super(testLibrary, "test_as_string_ascii");
        }
    }

    @Test
    public void testAsStringAscii(@Inject(TestAsStringAsciiNode.class) CallTarget asString) {
        Object ret = asString.call(ASCII_STRING);
        Assert.assertEquals(ASCII_LENGTH, ret);
    }

    @Test
    public void testAsStringBoxed(@Inject(TestAsStringAsciiNode.class) CallTarget asString) {
        Object ret = asString.call(new BoxedStringValue(ASCII_STRING));
        Assert.assertEquals(ASCII_LENGTH, ret);
    }

    public class TestAsStringUTF8Node extends SulongTestNode {

        public TestAsStringUTF8Node() {
            super(testLibrary, "test_as_string_utf8");
        }
    }

    @Test
    public void testAsStringUTF8(@Inject(TestAsStringUTF8Node.class) CallTarget asString) {
        Object ret = asString.call(UNICODE_STRING);
        Assert.assertEquals(UNICODE_LENGTH_UTF8, ret);
    }

    public class TestAsStringUTF32Node extends SulongTestNode {

        public TestAsStringUTF32Node() {
            super(testLibrary, "test_as_string_utf32");
        }
    }

    @Test
    public void testAsStringUTF32(@Inject(TestAsStringUTF32Node.class) CallTarget asString) {
        Object ret = asString.call(UNICODE_STRING);
        Assert.assertEquals(UNICODE_LENGTH_UTF32, ret);
    }

    public class TestAsStringOverflowNode extends SulongTestNode {

        public TestAsStringOverflowNode() {
            super(testLibrary, "test_as_string_overflow");
        }
    }

    @Test
    public void testAsStringOverflow(@Inject(TestAsStringOverflowNode.class) CallTarget asString) {
        Object ret = asString.call(ASCII_STRING);
        Assert.assertEquals(5, ret);
    }

    public class TestFromStringNode extends SulongTestNode {

        public TestFromStringNode() {
            super(testLibrary, "test_from_string");
        }
    }

    @Test
    public void testFromStringAscii(@Inject(TestFromStringNode.class) CallTarget fromString) {
        Object ret = fromString.call(1);
        Assert.assertEquals("Hello, from Native!", ret);
    }

    @Test
    public void testFromStringNAscii(@Inject(TestFromStringNode.class) CallTarget fromString) {
        Object ret = fromString.call(2);
        Assert.assertEquals("Hello, from Native!\0There is more!\0", ret);
    }

    @Test
    public void testFromStringUTF8(@Inject(TestFromStringNode.class) CallTarget fromString) {
        Object ret = fromString.call(3);
        Assert.assertEquals("unicode from native \u263a", ret);
    }

    @Test
    public void testFromStringNUTF8(@Inject(TestFromStringNode.class) CallTarget fromString) {
        Object ret = fromString.call(4);
        Assert.assertEquals("unicode from native \u263a\0stuff after zero \u2639\0", ret);
    }

    @Test
    public void testFromStringUTF32(@Inject(TestFromStringNode.class) CallTarget fromString) {
        Object ret = fromString.call(5);
        Assert.assertEquals("utf-32 works too \u263a", ret);
    }

    @Test
    public void testFromStringNUTF32(@Inject(TestFromStringNode.class) CallTarget fromString) {
        Object ret = fromString.call(6);
        Assert.assertEquals("utf-32 works too \u263a\0also with zero \u2639\0", ret);
    }
}
