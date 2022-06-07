/*
 * Copyright (c) 2012, 2024, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.truffle.tck.DebuggerTester.getSourceImpl;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Collection;
import java.util.Iterator;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.oracle.truffle.api.debug.Breakpoint;
import com.oracle.truffle.api.debug.DebugScope;
import com.oracle.truffle.api.debug.DebugStackFrame;
import com.oracle.truffle.api.debug.DebugValue;
import com.oracle.truffle.api.debug.DebuggerSession;
import com.oracle.truffle.api.debug.SourceElement;
import com.oracle.truffle.api.debug.SuspendedCallback;
import com.oracle.truffle.api.debug.SuspendedEvent;
import com.oracle.truffle.api.nodes.LanguageInfo;
import com.oracle.truffle.tck.DebuggerTester;

// Contains usages of deprecated debugger APIs, that was in tests before member interop was
// introduced.
@SuppressWarnings("deprecation")
public class LegacySLDebugTest {

    @BeforeClass
    public static void runWithWeakEncapsulationOnly() {
        TruffleTestAssumptions.assumeWeakEncapsulation();
    }

    private DebuggerTester tester;

    @Before
    public void before() {
        tester = new DebuggerTester(Context.newBuilder().allowAllAccess(true));
    }

    @After
    public void dispose() {
        tester.close();
    }

    private void startEval(Source code) {
        tester.startEval(code);
    }

    private static Source slCode(String code) {
        return Source.create("sl", code);
    }

    private DebuggerSession startSession() {
        return tester.startSession();
    }

    private DebuggerSession startSession(SourceElement... sourceElements) {
        return tester.startSession(sourceElements);
    }

    private String expectDone() {
        return tester.expectDone();
    }

    private void expectSuspended(SuspendedCallback callback) {
        tester.expectSuspended(callback);
    }

    @Test
    public void testHostValueMetadata() {
        Source source = slCode("function main(){\n" +
                        "  symbol = java(\"java.lang.StringBuilder\");\n" +
                        "  instance = new(symbol);\n" +
                        "  instance.reverse();\n" +
                        "}\n");
        try (DebuggerSession session = startSession()) {
            startEval(source);
            session.suspendNextExecution();

            expectSuspended(event -> event.prepareStepOver(1));

            expectSuspended(event -> {
                DebugValue symbolValue = event.getTopStackFrame().getScope().getDeclaredValue("symbol");
                assertTrue(symbolValue.isReadable());
                assertFalse(symbolValue.isInternal());
                assertFalse(symbolValue.hasReadSideEffects());
                LanguageInfo hostLang = symbolValue.getOriginalLanguage();
                assertNotNull(hostLang);
                assertEquals("host", hostLang.getId());
                DebugValue symbolValCasted = symbolValue.asInLanguage(hostLang);

                assertEquals(symbolValCasted.getOriginalLanguage(), symbolValue.getOriginalLanguage());
                DebugValue symbolMeta = symbolValCasted.getMetaObject();
                assertNotNull(symbolMeta);
                assertEquals(Class.class.getSimpleName(), symbolMeta.getMetaSimpleName());
                assertEquals(Class.class.getName(), symbolMeta.getMetaQualifiedName());
                event.prepareStepOver(1);
            });

            expectSuspended(event -> {
                DebugValue instanceValue = event.getTopStackFrame().getScope().getDeclaredValue("instance");
                LanguageInfo hostLang = instanceValue.getOriginalLanguage();
                assertEquals("host", hostLang.getId());
                instanceValue = instanceValue.asInLanguage(hostLang);
                DebugValue instanceMeta = instanceValue.getMetaObject();
                assertEquals(StringBuilder.class.getSimpleName(), instanceMeta.getMetaSimpleName());
                assertEquals(StringBuilder.class.getName(), instanceMeta.getMetaQualifiedName());
                event.prepareContinue();
            });

            expectDone();
        }
    }

    @Test
    public void testDebugValue() throws Throwable {
        final Source varsSource = slCode("function main() {\n" +
                        "  a = doNull();\n" +
                        "  b = 10 == 10;\n" +
                        "  c = 10;\n" +
                        "  d = \"str\";\n" +
                        "  e = new();\n" +
                        "  e.p1 = 1;\n" +
                        "  e.p2 = new();\n" +
                        "  e.p2.p21 = 21;\n" +
                        "  return;\n" +
                        "}\n" +
                        "function doNull() {}\n");

        try (DebuggerSession session = startSession()) {
            session.install(Breakpoint.newBuilder(getSourceImpl(varsSource)).lineIs(10).build());
            startEval(varsSource);

            expectSuspended((SuspendedEvent event) -> {
                DebugStackFrame frame = event.getTopStackFrame();

                DebugScope scope = frame.getScope();
                DebugValue a = scope.getDeclaredValue("a");
                assertFalse(a.isArray());
                assertNull(a.getArray());
                assertNull(a.getProperties());

                DebugValue b = scope.getDeclaredValue("b");
                assertFalse(b.isArray());
                assertNull(b.getArray());
                assertNull(b.getProperties());

                DebugValue c = scope.getDeclaredValue("c");
                assertFalse(c.isArray());
                assertEquals("10", c.toDisplayString());
                assertNull(c.getArray());
                assertNull(c.getProperties());

                DebugValue d = scope.getDeclaredValue("d");
                assertFalse(d.isArray());
                assertEquals("str", d.toDisplayString());
                assertNull(d.getArray());
                assertNull(d.getProperties());

                DebugValue e = scope.getDeclaredValue("e");
                assertFalse(e.isArray());
                assertNull(e.getArray());
                assertEquals(scope, e.getScope());
                Collection<DebugValue> propertyValues = e.getProperties();
                assertEquals(2, propertyValues.size());
                Iterator<DebugValue> propertiesIt = propertyValues.iterator();
                assertTrue(propertiesIt.hasNext());
                DebugValue p1 = propertiesIt.next();
                assertEquals("p1", p1.getName());
                assertEquals("1", p1.toDisplayString());
                assertNull(p1.getScope());
                assertTrue(propertiesIt.hasNext());
                DebugValue p2 = propertiesIt.next();
                assertEquals("p2", p2.getName());
                assertNull(p2.getScope());
                assertFalse(propertiesIt.hasNext());

                propertyValues = p2.getProperties();
                assertEquals(1, propertyValues.size());
                propertiesIt = propertyValues.iterator();
                assertTrue(propertiesIt.hasNext());
                DebugValue p21 = propertiesIt.next();
                assertEquals("p21", p21.getName());
                assertEquals("21", p21.toDisplayString());
                assertNull(p21.getScope());
                assertFalse(propertiesIt.hasNext());

                DebugValue ep1 = e.getProperty("p1");
                assertEquals("1", ep1.toDisplayString());
                ep1.set(p21);
                assertEquals("21", ep1.toDisplayString());
                assertNull(e.getProperty("NonExisting"));
            });

            expectDone();
        }
    }
}
