/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.test.examples;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Value;
import org.junit.Test;

import com.oracle.truffle.api.test.polyglot.HostAccessTest.MyEquals;

public class HostAccessExamplesTest {

    @Test
    public void banAccessToReflection() throws Exception {
        // @formatter:off
        HostAccess config = HostAccess.newBuilder().
            allowPublicAccess(true).
            denyAccess(Class.class).
            denyAccess(Method.class).
            denyAccess(Field.class).
            denyAccess(Proxy.class).
            denyAccess(Object.class, false).
            build();
        // @formatter:on

        try (Context context = Context.newBuilder().allowHostAccess(config).build()) {
            Value readValue = context.eval("sl", "" +
                            "function readValue(x, y) {\n" +
                            "  return x.equals(y);\n" +
                            "}\n" +
                            "function main() {\n" +
                            "  return readValue;\n" +
                            "}\n");

            MyEquals myEquals = new MyEquals();
            assertTrue("MyEquals.equals method is accessible", readValue.execute(myEquals, myEquals).asBoolean());

            Value res;
            try {
                res = readValue.execute(new Object());
            } catch (PolyglotException ex) {
                return;
            }
            fail("expecting no result: " + res);
        }
    }

    @Test
    public void testSampleMappings() {
        HostAccess.Builder builder = HostAccess.newBuilder();
        // character coercion
        TargetMappings.enableStringCoercions(builder);

        HostAccess config = builder.build();

        try (Context context = Context.newBuilder().allowHostAccess(config).build()) {
            assertEquals((byte) 42, (byte) context.asValue("42").as(byte.class));
            assertEquals(Byte.valueOf((byte) 42), context.asValue("42").as(Byte.class));
            assertEquals((short) 42, (short) context.asValue("42").as(short.class));
            assertEquals(Short.valueOf((short) 42), context.asValue("42").as(Short.class));
            assertEquals(42, (int) context.asValue("42").as(int.class));
            assertEquals(Integer.valueOf(42), context.asValue("42").as(Integer.class));
            assertEquals(42L, (long) context.asValue("42").as(long.class));
            assertEquals(Long.valueOf(42L), context.asValue("42").as(Long.class));
            assertEquals(42f, context.asValue("42").as(float.class), 0.001f);
            assertEquals(Float.valueOf(42f), context.asValue("42").as(Float.class));
            assertEquals(42d, context.asValue("42").as(double.class), 0.001d);
            assertEquals(Double.valueOf(42d), context.asValue("42").as(Double.class));
            assertEquals(true, context.asValue("true").as(boolean.class));
            assertEquals(false, context.asValue("false").as(boolean.class));
            assertEquals(true, context.asValue("true").as(Boolean.class));

            assertEquals("42", context.asValue((byte) 42).as(String.class));
            assertEquals("42", context.asValue((short) 42).as(String.class));
            assertEquals("42", context.asValue(42).as(String.class));
            assertEquals("42", context.asValue(42L).as(String.class));
            assertEquals("42.0", context.asValue((float) 42).as(String.class));
            assertEquals("42.0", context.asValue((double) 42).as(String.class));
            assertEquals("true", context.asValue(true).as(String.class));

            assertEquals((Character) (char) 42, context.asValue(42).as(char.class));
            assertEquals((Character) (char) 42, context.asValue(42).as(Character.class));
        }
    }

}
