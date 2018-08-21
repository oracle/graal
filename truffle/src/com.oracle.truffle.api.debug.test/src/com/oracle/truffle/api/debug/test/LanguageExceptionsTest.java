/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
            value.as(String.class);
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
                            value.getProperty("A").as(String.class);
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
