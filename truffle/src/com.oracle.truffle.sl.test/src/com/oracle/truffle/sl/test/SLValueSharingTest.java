/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Value;
import org.junit.Assert;
import org.junit.Test;

public class SLValueSharingTest {

    public static class JavaObject {
        public Object sharedField;
    }

    /*
     * Tests that if a language tries to share a value through host interop it fails with an error
     * that shows the location in the guest code.
     */
    @Test
    public void testImplicitValueSharing() {
        JavaObject obj = new JavaObject();
        Context.Builder b = Context.newBuilder().allowAllAccess(true);
        try (Context c0 = b.build();
                        Context c1 = b.build()) {

            c0.eval("sl", "function test(obj) { obj.sharedField = new(); }");
            c1.eval("sl", "function test(obj) { return obj.sharedField; }");

            c0.getBindings("sl").getMember("test").execute(obj);
            Value test1 = c1.getBindings("sl").getMember("test");
            try {
                test1.execute(obj);
                Assert.fail();
            } catch (PolyglotException e) {
                Assert.assertEquals(28, e.getSourceLocation().getCharIndex());
                Assert.assertEquals(43, e.getSourceLocation().getCharEndIndex());
                Assert.assertTrue(e.getMessage(), e.getMessage().contains("cannot be passed from one context to another"));
            }
        }

    }
}
