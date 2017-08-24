/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.truffle.api.instrumentation.test.InstrumentationTestLanguage.FILENAME_EXTENSION;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;

import com.oracle.truffle.api.debug.DebugStackFrame;
import com.oracle.truffle.api.debug.DebugValue;
import com.oracle.truffle.api.debug.Debugger;
import com.oracle.truffle.api.debug.DebuggerSession;
import com.oracle.truffle.api.debug.SuspendedCallback;
import com.oracle.truffle.api.debug.SuspendedEvent;
import com.oracle.truffle.api.instrumentation.test.InstrumentationTestLanguage;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.tck.DebuggerTester;

/**
 * Framework for testing the Truffle {@linkplain Debugger Debugging API}.
 */
public abstract class AbstractDebugTest {

    private DebuggerTester tester;
    private ArrayDeque<DebuggerTester> sessionStack = new ArrayDeque<>();

    AbstractDebugTest() {
    }

    @Before
    public void before() {
        pushContext();
    }

    @After
    public void dispose() {
        popContext();
    }

    protected final void resetContext(DebuggerTester newTester) {
        this.tester = newTester;
    }

    protected final Debugger getDebugger() {
        return tester.getDebugger();
    }

    protected final DebuggerSession startSession() {
        return tester.startSession();
    }

    protected final Thread getEvalThread() {
        return tester.getEvalThread();
    }

    protected final void startEval(String source) {
        startEval(testSource(source));
    }

    protected final void startEval(Source source) {
        tester.startEval(source);
    }

    protected final void pushContext() {
        if (tester != null) {
            sessionStack.push(tester);
        }
        tester = new DebuggerTester();
    }

    protected final void popContext() {
        tester.close();
        if (!sessionStack.isEmpty()) {
            tester = sessionStack.pop();
        }
    }

    protected File testFile(String code) throws IOException {
        File file = File.createTempFile("TestFile", FILENAME_EXTENSION).getCanonicalFile();
        try (Writer w = new FileWriter(file)) {
            w.write(code);
        }
        file.deleteOnExit();
        return file;
    }

    protected Source testSource(String code) {
        return Source.newBuilder(code).mimeType(InstrumentationTestLanguage.MIME_TYPE).name("test code").build();
    }

    protected SuspendedEvent checkState(SuspendedEvent suspendedEvent, final int expectedLineNumber, final boolean expectedIsBefore, final String expectedCode, final String... expectedFrame) {
        final int actualLineNumber = suspendedEvent.getSourceSection().getStartLine();
        Assert.assertEquals(expectedLineNumber, actualLineNumber);
        final String actualCode = suspendedEvent.getSourceSection().getCodeSequence().toString();
        Assert.assertEquals(expectedCode, actualCode);
        final boolean actualIsBefore = suspendedEvent.isHaltedBefore();
        Assert.assertEquals(expectedIsBefore, actualIsBefore);

        checkStack(suspendedEvent.getTopStackFrame(), expectedFrame);
        return suspendedEvent;
    }

    protected void checkStack(DebugStackFrame frame, String... expectedFrame) {
        Map<String, DebugValue> values = new HashMap<>();
        for (DebugValue value : frame) {
            values.put(value.getName(), value);
        }
        Assert.assertEquals(expectedFrame.length / 2, values.size());
        for (int i = 0; i < expectedFrame.length; i = i + 2) {
            String expectedIdentifier = expectedFrame[i];
            String expectedValue = expectedFrame[i + 1];
            DebugValue value = values.get(expectedIdentifier);
            Assert.assertNotNull(value);
            Assert.assertEquals(expectedValue, value.as(String.class));
        }
    }

    protected final String expectDone() {
        return tester.expectDone();
    }

    protected final void expectSuspended(SuspendedCallback handler) {
        tester.expectSuspended(handler);
    }

    protected final void expectKilled() {
        tester.expectKilled();
    }

}
