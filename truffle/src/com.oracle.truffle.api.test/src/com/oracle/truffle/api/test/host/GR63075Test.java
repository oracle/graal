/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.test.host;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.HostAccess.Export;
import org.graalvm.polyglot.Value;
import org.junit.Test;

public class GR63075Test {

    static class BaseClass {

        @Export
        public String publicAbstractMethod() {
            return "publicAbstractMethod";
        }

    }

    public static class SubClass extends BaseClass {

        @Export
        public String publicMethod() {
            return "publicMethod";
        }

    }

    static class NonPublicSubClass extends BaseClass {

        public String publicMethod() {
            return "publicMethod";
        }

    }

    @Test
    public void testPublicAccess() {
        try (Context c = Context.newBuilder().allowHostAccess(HostAccess.ALL).build()) {
            SubClass b = new SubClass();
            Value v = c.asValue(b);

            assertEquals("publicMethod", v.invokeMember("publicMethod").asString());
            assertEquals("publicAbstractMethod", v.invokeMember("publicAbstractMethod").asString());
        }
    }

    @Test
    public void testNonPublicAccess() {
        try (Context c = Context.newBuilder().allowHostAccess(HostAccess.ALL).build()) {
            NonPublicSubClass b = new NonPublicSubClass();
            Value v = c.asValue(b);

            assertNull(v.getMember("publicMethod"));
            assertNull(v.getMember("publicAbstractMethod"));
        }
    }

    @Test
    public void testExplicitAccess() {
        try (Context c = Context.newBuilder().allowHostAccess(HostAccess.EXPLICIT).build()) {
            SubClass b = new SubClass();
            Value v = c.asValue(b);

            assertEquals("publicMethod", v.invokeMember("publicMethod").asString());
            assertNull(v.getMember("publicAbstractMethod"));
        }
    }

}
