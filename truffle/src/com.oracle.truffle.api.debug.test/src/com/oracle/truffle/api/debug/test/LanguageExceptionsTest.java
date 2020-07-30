/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.debug.test;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Collections;
import java.util.Objects;

import org.junit.Assert;
import org.junit.Test;

import com.oracle.truffle.api.Scope;
import com.oracle.truffle.api.debug.DebugException;
import com.oracle.truffle.api.debug.DebugValue;
import com.oracle.truffle.api.debug.DebuggerSession;
import com.oracle.truffle.api.debug.SuspendedCallback;
import com.oracle.truffle.api.debug.SuspendedEvent;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.api.test.polyglot.ProxyLanguage;

import org.graalvm.polyglot.Source;

/**
 * Test that all language Throwables are converted to DebugException.
 */
public class LanguageExceptionsTest extends AbstractDebugTest {

    @Test
    public void testBuggyToString() {
        testBuggyLanguageCalls(new TestDebugBuggyLanguage() {
            @Override
            protected String toString(ProxyLanguage.LanguageContext c, Object value) {
                throwBug(value);
                return Objects.toString(value);
            }
        }, (SuspendedEvent event) -> {
            DebugValue value = event.getTopStackFrame().getScope().getDeclaredValue("a");
            value.toDisplayString();
        });
    }

    @Test
    public void testBuggyFindMetaObject() {
        testBuggyLanguageCalls(new TestDebugBuggyLanguage() {
            @Override
            protected Object findMetaObject(ProxyLanguage.LanguageContext context, Object value) {
                throwBug(value);
                return value.getClass().getName();
            }
        }, (SuspendedEvent event) -> {
            DebugValue value = event.getTopStackFrame().getScope().getDeclaredValue("a");
            value.getMetaObject();
        });
    }

    @Test
    public void testBuggySourceLocation() {
        testBuggyLanguageCalls(new TestDebugBuggyLanguage() {
            @Override
            protected SourceSection findSourceLocation(ProxyLanguage.LanguageContext context, Object value) {
                throwBug(value);
                return null;
            }
        }, (SuspendedEvent event) -> {
            DebugValue value = event.getTopStackFrame().getScope().getDeclaredValue("a");
            value.getSourceLocation();
        });
    }

    @Test
    public void testBuggyScope() {
        testBuggyLanguageCalls(new TestDebugBuggyLanguage() {
            @Override
            protected Iterable<Scope> findLocalScopes(ProxyLanguage.LanguageContext context, Node node, Frame frame) {
                String text = node.getSourceSection().getCharacters().toString();
                throwBug(Integer.parseInt(text));
                return Collections.emptyList();
            }
        }, (SuspendedEvent event) -> {
            event.getTopStackFrame().getScope();
        });
    }

    @Test
    public void testBuggyKeys() {
        testBuggyLanguageCalls(new TestDebugBuggyLanguage(),
                        (SuspendedEvent event) -> {
                            DebugValue value = event.getTopStackFrame().getScope().getDeclaredValue("o");
                            value.getProperties();
                        }, "KEYS");
    }

    @Test
    public void testBuggyKeyInfo() {
        testBuggyLanguageCalls(new TestDebugBuggyLanguage(),
                        (SuspendedEvent event) -> {
                            DebugValue value = event.getTopStackFrame().getScope().getDeclaredValue("o");
                            value.getProperty("A");
                        }, "KEY_INFO");
    }

    @Test
    public void testBuggyRead() {
        testBuggyLanguageCalls(new TestDebugBuggyLanguage(),
                        (SuspendedEvent event) -> {
                            DebugValue value = event.getTopStackFrame().getScope().getDeclaredValue("o");
                            value.getProperty("A").toDisplayString();
                        }, "READ");
    }

    @Test
    public void testBuggyWrite() {
        testBuggyLanguageCalls(new TestDebugBuggyLanguage(),
                        (SuspendedEvent event) -> {
                            DebugValue value = event.getTopStackFrame().getScope().getDeclaredValue("o");
                            value.getProperty("A").set(10);
                        }, "WRITE");
    }

    @Test
    public void testBuggyFrame() {
        testBuggyLanguageCalls(new TestDebugBuggyLanguage(),
                        (SuspendedEvent event) -> {
                            event.getTopStackFrame().getName();
                        }, "ROOT");
    }

    @Test
    public void testBuggyFrame2() {
        testBuggyLanguageCalls(new TestDebugBuggyLanguage(),
                        (SuspendedEvent event) -> {
                            event.getStackFrames().iterator().next().getName();
                        }, "ROOT");
    }

    @Test
    public void testBuggyExecute() {
        testBuggyLanguageCalls(new TestDebugBuggyLanguage(),
                        (SuspendedEvent event) -> {
                            DebugValue value = event.getTopStackFrame().getScope().getDeclaredValue("o");
                            value.canExecute();
                        }, "CAN_EXECUTE");
    }

    private void testBuggyLanguageCalls(ProxyLanguage language, SuspendedCallback callback) {
        testBuggyLanguageCalls(language, callback, "");
    }

    private void testBuggyLanguageCalls(ProxyLanguage language, SuspendedCallback callback, String prefix) {
        ProxyLanguage.setDelegate(language);
        DebuggerSession session = tester.startSession();
        session.suspendNextExecution();
        Source source = Source.create(ProxyLanguage.ID, prefix + "1");
        tester.startEval(source);
        tester.expectSuspended((SuspendedEvent event) -> {
            try {
                callback.onSuspend(event);
                Assert.fail("No DebugException is thrown!");
            } catch (DebugException dex) {
                Assert.assertEquals("1", dex.getLocalizedMessage());
                verifyExStack(dex, IllegalStateException.class.getName());
            } catch (Throwable t) {
                Assert.fail(t.getLocalizedMessage());
            }
        });
        tester.expectDone();
        source = Source.create(ProxyLanguage.ID, prefix + "2");
        session.suspendNextExecution();
        tester.startEval(source);
        tester.expectSuspended((SuspendedEvent event) -> {
            try {
                callback.onSuspend(event);
                Assert.fail("No DebugException is thrown!");
            } catch (DebugException dex) {
                Assert.assertEquals("A TruffleException", dex.getLocalizedMessage());
                Assert.assertNull(Objects.toString(dex.getCause()), dex.getCause());
            } catch (Throwable t) {
                Assert.fail(t.getLocalizedMessage());
            }
        });
        tester.expectDone();
        source = Source.create(ProxyLanguage.ID, prefix + "3");
        session.suspendNextExecution();
        tester.startEval(source);
        tester.expectSuspended((SuspendedEvent event) -> {
            try {
                callback.onSuspend(event);
                Assert.fail("No DebugException is thrown!");
            } catch (DebugException dex) {
                Assert.assertEquals("3", dex.getLocalizedMessage());
                verifyExStack(dex, AssertionError.class.getName());
            } catch (Throwable t) {
                Assert.fail(t.getLocalizedMessage());
            }
        });
        tester.expectDone();
    }

    private static void verifyExStack(DebugException dex, String causeName) {
        StringWriter w = new StringWriter();
        dex.printStackTrace(new PrintWriter(w));
        String trace = w.toString();
        Assert.assertTrue(trace, trace.startsWith(DebugException.class.getName()));
        Assert.assertTrue(trace, trace.indexOf("Caused by: " + causeName) > 0);
        Assert.assertTrue(trace, trace.indexOf(TestDebugBuggyLanguage.class.getName() + ".throwBug") > 0);
    }

}
