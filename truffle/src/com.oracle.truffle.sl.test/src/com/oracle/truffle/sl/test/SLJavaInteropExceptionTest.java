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
package com.oracle.truffle.sl.test;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Arrays;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.function.Supplier;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.junit.Assert;
import org.junit.Test;

import com.oracle.truffle.sl.SLLanguage;
import org.graalvm.polyglot.HostAccess;

public class SLJavaInteropExceptionTest {
    public static class Validator {
        @HostAccess.Export
        public int validateException() {
            throw new NoSuchElementException();
        }

        @HostAccess.Export
        public void validateNested() throws Exception {
            String sourceText = "function test(validator) {\n" +
                            "  return validator.validateException();\n" +
                            "}";
            try (Context context = Context.newBuilder(SLLanguage.ID).build()) {
                context.eval(Source.newBuilder(SLLanguage.ID, sourceText, "Test").build());
                Value test = context.getBindings(SLLanguage.ID).getMember("test");
                test.execute(Validator.this);
            }
        }

        @HostAccess.Export
        public long validateFunction(Supplier<Long> function) {
            return function.get();
        }

        @HostAccess.Export
        public void validateMap(Map<String, Object> map) {
            Assert.assertNull(map.get(null));
        }
    }

    @Test
    public void testGR7284() throws Exception {
        String sourceText = "function test(validator) {\n" +
                        "  return validator.validateException();\n" +
                        "}";
        try (Context context = Context.newBuilder(SLLanguage.ID).build()) {
            context.eval(Source.newBuilder(SLLanguage.ID, sourceText, "Test").build());
            Value test = context.getBindings(SLLanguage.ID).getMember("test");
            try {
                test.execute(new Validator());
                fail("expected a PolyglotException but did not throw");
            } catch (PolyglotException ex) {
                assertTrue("expected HostException", ex.isHostException());
                assertThat(ex.asHostException(), instanceOf(NoSuchElementException.class));
                assertNoJavaInteropStackFrames(ex);
            }
        }
    }

    @Test
    public void testGR7284GuestHostGuestHost() throws Exception {
        String sourceText = "function test(validator) {\n" +
                        "  return validator.validateNested();\n" +
                        "}";
        try (Context context = Context.newBuilder(SLLanguage.ID).build()) {
            context.eval(Source.newBuilder(SLLanguage.ID, sourceText, "Test").build());
            Value test = context.getBindings(SLLanguage.ID).getMember("test");
            try {
                test.execute(new Validator());
                fail("expected a PolyglotException but did not throw");
            } catch (PolyglotException ex) {
                assertTrue("expected HostException", ex.isHostException());
                assertThat(ex.asHostException(), instanceOf(NoSuchElementException.class));
                assertNoJavaInteropStackFrames(ex);
            }
        }
    }

    private static void assertNoJavaInteropStackFrames(PolyglotException ex) {
        String javaInteropPackageName = "com.oracle.truffle.api.interop.java";
        assertFalse("expected no java interop stack trace elements", Arrays.stream(ex.getStackTrace()).anyMatch(ste -> ste.getClassName().startsWith(javaInteropPackageName)));
    }

    @Test
    public void testFunctionProxy() throws Exception {
        String javaMethod = "validateFunction";
        String sourceText = "" +
                        "function supplier() {\n" +
                        "  return error();\n" +
                        "}\n" +
                        "function test(validator) {\n" +
                        "  return validator." + javaMethod + "(supplier);\n" +
                        "}";
        try (Context context = Context.newBuilder(SLLanguage.ID).build()) {
            context.eval(Source.newBuilder(SLLanguage.ID, sourceText, "Test").build());
            Value test = context.getBindings(SLLanguage.ID).getMember("test");
            try {
                test.execute(new Validator());
                fail("expected a PolyglotException but did not throw");
            } catch (PolyglotException ex) {
                StackTraceElement last = null;
                boolean found = false;
                for (StackTraceElement curr : ex.getStackTrace()) {
                    if (curr.getMethodName().contains(javaMethod)) {
                        assertNotNull(last);
                        assertThat("expected Proxy stack frame", last.getClassName(), containsString("Proxy"));
                        found = true;
                        break;
                    }
                    last = curr;
                }
                assertTrue(javaMethod + " not found in stack trace", found);
            }
        }
    }

    @Test
    public void testTruffleMap() throws Exception {
        String javaMethod = "validateMap";
        String sourceText = "" +
                        "function test(validator) {\n" +
                        "  return validator." + javaMethod + "(new());\n" +
                        "}";
        try (Context context = Context.newBuilder(SLLanguage.ID).build()) {
            context.eval(Source.newBuilder(SLLanguage.ID, sourceText, "Test").build());
            Value test = context.getBindings(SLLanguage.ID).getMember("test");
            test.execute(new Validator());
        }
    }
}
