/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Iterator;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.PolyglotException.StackFrame;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyArray;
import org.graalvm.polyglot.proxy.ProxyExecutable;
import org.graalvm.polyglot.proxy.ProxyObject;
import org.junit.BeforeClass;
import org.junit.Test;

import com.oracle.truffle.sl.SLLanguage;

public class SLForeignExceptionStackTraceTest {

    @BeforeClass
    public static void runWithWeakEncapsulationOnly() {
        // Host exception identity and stack inspection require the same mode as SLExceptionTest.
        TruffleTestAssumptions.assumeWeakEncapsulation();
    }

    @Test
    public void testUncachedForeignExceptionStackTrace() {
        assertForeignExceptionStackTrace("UNCACHED");
    }

    @Test
    public void testCachedForeignExceptionStackTrace() {
        assertForeignExceptionStackTrace("CACHED");
    }

    @Test
    public void testUncachedForeignMemberReadExceptionStackTrace() {
        assertForeignMemberExceptionStackTrace("UNCACHED", "o.x");
    }

    @Test
    public void testCachedForeignMemberReadExceptionStackTrace() {
        assertForeignMemberExceptionStackTrace("CACHED", "o.x");
    }

    @Test
    public void testUncachedForeignMemberWriteExceptionStackTrace() {
        assertForeignMemberExceptionStackTrace("UNCACHED", "o.x = 7");
    }

    @Test
    public void testCachedForeignMemberWriteExceptionStackTrace() {
        assertForeignMemberExceptionStackTrace("CACHED", "o.x = 7");
    }

    @Test
    public void testUncachedForeignArrayReadExceptionStackTrace() {
        assertForeignArrayExceptionStackTrace("UNCACHED", "o[0]");
    }

    @Test
    public void testCachedForeignArrayReadExceptionStackTrace() {
        assertForeignArrayExceptionStackTrace("CACHED", "o[0]");
    }

    @Test
    public void testUncachedForeignArrayWriteExceptionStackTrace() {
        assertForeignArrayExceptionStackTrace("UNCACHED", "o[0] = 7");
    }

    @Test
    public void testCachedForeignArrayWriteExceptionStackTrace() {
        assertForeignArrayExceptionStackTrace("CACHED", "o[0] = 7");
    }

    private static void assertForeignExceptionStackTrace(String tier) {
        RuntimeException hostException = new RuntimeException("host exception");
        ProxyExecutable proxy = (arguments) -> {
            throw hostException;
        };
        assertForeignExceptionStackTrace(tier, "function foo(f) { f(); } function main() { return foo; }", proxy, hostException, 18, 21);
    }

    private static void assertForeignMemberExceptionStackTrace(String tier, String expression) {
        RuntimeException hostException = new RuntimeException("host exception");
        ProxyObject proxy = new ProxyObject() {
            @Override
            public Object getMember(String key) {
                throw hostException;
            }

            @Override
            public Object getMemberKeys() {
                return new String[]{"x"};
            }

            @Override
            public boolean hasMember(String key) {
                return key.equals("x");
            }

            @Override
            public void putMember(String key, Value value) {
                throw hostException;
            }
        };
        assertForeignExceptionStackTrace(tier, "function foo(o) { " + expression + "; } function main() { return foo; }", proxy, hostException, 18, 18 + expression.length());
    }

    private static void assertForeignArrayExceptionStackTrace(String tier, String expression) {
        RuntimeException hostException = new RuntimeException("host exception");
        ProxyArray proxy = new ProxyArray() {
            @Override
            public Object get(long index) {
                throw hostException;
            }

            @Override
            public void set(long index, Value value) {
                throw hostException;
            }

            @Override
            public long getSize() {
                return 1;
            }
        };
        assertForeignExceptionStackTrace(tier, "function foo(o) { " + expression + "; } function main() { return foo; }", proxy, hostException, 18, 18 + expression.length());
    }

    private static void assertForeignExceptionStackTrace(String tier, String code, Object proxy, RuntimeException hostException, int startIndex, int endIndex) {
        try (Context context = Context.newBuilder(SLLanguage.ID).allowExperimentalOptions(true).option("sl.UseBytecode", "true").option("sl.ForceBytecodeTier", tier).build()) {
            Value function = context.eval(Source.newBuilder(SLLanguage.ID, code, "foreign.sl").buildLiteral());
            try {
                function.execute(proxy);
                fail("Expected host exception");
            } catch (PolyglotException ex) {
                assertTrue(ex.isHostException());
                assertSame(hostException, ex.asHostException());

                Iterator<StackFrame> frames = ex.getPolyglotStackTrace().iterator();
                StackFrame frame = null;
                while (frames.hasNext()) {
                    StackFrame next = frames.next();
                    if (next.isGuestFrame()) {
                        frame = next;
                        break;
                    }
                    assertTrue(next.toString(), next.isHostFrame());
                }
                assertNotNull(frame);
                assertEquals(SLLanguage.ID, frame.getLanguage().getId());
                assertEquals("foo", frame.getRootName());
                assertNotNull(frame.getSourceLocation());
                assertEquals("foreign.sl", frame.getSourceLocation().getSource().getName());
                assertEquals(startIndex, frame.getSourceLocation().getCharIndex());
                assertEquals(endIndex, frame.getSourceLocation().getCharEndIndex());
            }
        }
    }
}
