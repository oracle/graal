/*
 * Copyright (c) 2019, 2023, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.util.test;

import static jdk.graal.compiler.util.OptionsEncoder.decode;
import static jdk.graal.compiler.util.OptionsEncoder.encode;
import static org.junit.Assert.assertEquals;

import java.lang.annotation.ElementType;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;

import jdk.graal.compiler.util.CollectionsUtil;
import jdk.graal.compiler.util.TypedDataOutputStream;

public class OptionsEncoderTest {

    @Test
    public void testSmallString() {
        Map<String, Object> options = CollectionsUtil.mapOf("key", "smallString");
        assertEquals(options, decode(encode(options)));
    }

    @Test
    public void testLargeString() {
        StringBuilder fillBuilder = new StringBuilder();
        for (int i = 0; i < 1 << 8; i++) {
            fillBuilder.append(' ');
        }
        String fill = fillBuilder.toString();
        StringBuilder largeString = new StringBuilder();
        for (int i = 0; i <= Character.MAX_VALUE >>> 8; i++) {
            largeString.append(fill);
        }
        Map<String, Object> options = CollectionsUtil.mapOf("key", largeString.toString());
        assertEquals(options, decode(encode(options)));
    }

    @Test
    public void testEnum() {
        Map<String, Object> options = CollectionsUtil.mapOf("key", ElementType.TYPE);
        assertEquals(CollectionsUtil.mapOf("key", ElementType.TYPE.name()), decode(encode(options)));
    }

    @Test
    public void testNumbers() {
        testValueIntl("char", (char) 1);
        testValueIntl("byte", (byte) 1);
        testValueIntl("short", (short) 1);
        testValueIntl("integer", 1);
        testValueIntl("long", (long) 1);
        testValueIntl("float", (float) 1.5);
        testValueIntl("double", 1.5);
    }

    @Test
    public void testBoolean() {
        testValueIntl("boolTrue", true);
        testValueIntl("boolFalse", false);
    }

    private static void testValueIntl(String name, Object value) {
        Map<String, Object> options = CollectionsUtil.mapOf(name, value);
        assertEquals(options, decode(encode(options)));
    }

    @Test
    public void testFailure() {
        Map<String, Object> options = CollectionsUtil.mapOf("key", new Object());
        try {
            encode(options);
            Assert.fail("Expected an exception");
        } catch (IllegalArgumentException ex) {
            Assert.assertTrue(ex.getMessage().contains("Value type: class java.lang.Object"));
        }
    }

    @Test
    public void testIsValueSupported() {
        Assert.assertFalse(TypedDataOutputStream.isValueSupported(new Object()));
        Assert.assertFalse(TypedDataOutputStream.isValueSupported(null));

        Assert.assertTrue(TypedDataOutputStream.isValueSupported(1));
        Assert.assertTrue(TypedDataOutputStream.isValueSupported(true));
        Assert.assertTrue(TypedDataOutputStream.isValueSupported("test"));
    }
}
