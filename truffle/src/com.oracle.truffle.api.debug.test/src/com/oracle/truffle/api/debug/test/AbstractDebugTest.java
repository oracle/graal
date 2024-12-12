/*
 * Copyright (c) 2016, 2022, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.truffle.api.instrumentation.test.InstrumentationTestLanguage.FILENAME_EXTENSION;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotAccess;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.io.IOAccess;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;

import com.oracle.truffle.api.debug.DebugStackFrame;
import com.oracle.truffle.api.debug.DebugValue;
import com.oracle.truffle.api.debug.Debugger;
import com.oracle.truffle.api.debug.DebuggerSession;
import com.oracle.truffle.api.debug.SourceElement;
import com.oracle.truffle.api.debug.SuspendAnchor;
import com.oracle.truffle.api.debug.SuspendedCallback;
import com.oracle.truffle.api.debug.SuspendedEvent;
import com.oracle.truffle.api.instrumentation.test.InstrumentationTestLanguage;
import com.oracle.truffle.tck.DebuggerTester;
import com.oracle.truffle.tck.tests.TruffleTestAssumptions;

/**
 * Framework for testing the Truffle {@linkplain Debugger Debugging API}.
 */
public abstract class AbstractDebugTest {

    protected DebuggerTester tester;
    private final ArrayDeque<DebuggerTester> sessionStack = new ArrayDeque<>();
    private final AtomicReference<Value> functionWithArgument = new AtomicReference<>();

    AbstractDebugTest() {
    }

    @Before
    public void before() {
        pushContext();
    }

    @After
    public void dispose() {
        popContext();
        functionWithArgument.set(null);
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

    protected final DebuggerSession startSession(SourceElement... sourceElements) {
        return tester.startSession(sourceElements);
    }

    protected final String getOutput() {
        return tester.getOut();
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

    protected final void startExecute(Function<Context, Value> script) {
        tester.startExecute(script);
    }

    protected final void pushContext() {
        if (tester != null) {
            sessionStack.push(tester);
        }
        Context.Builder builder = Context.newBuilder().allowCreateThread(true).allowPolyglotAccess(PolyglotAccess.ALL).allowIO(IOAccess.ALL);
        tester = new DebuggerTester(TruffleTestAssumptions.isOptimizingRuntime() ? builder.option("engine.MaximumCompilations", "-1") : builder);
    }

    protected final void popContext() {
        tester.close();
        if (!sessionStack.isEmpty()) {
            tester = sessionStack.pop();
        }
    }

    protected final Value getFunctionValue(Source source, String functionName) {
        AtomicReference<Value> functionValue = new AtomicReference<>();
        tester.startExecute((Context c) -> {
            Value v = c.eval(source);
            functionValue.set(c.getBindings(InstrumentationTestLanguage.ID).getMember(functionName));
            return v;
        });
        expectDone();
        Value v = functionValue.get();
        assertNotNull(v);
        return v;
    }

    private Value getFunctionWithArgument() {
        return functionWithArgument.updateAndGet(value -> {
            if (value == null) {
                Source source = testSource("DEFINE(function, ROOT(\n" +
                                "  ARGUMENT(a), \n" +
                                "  STATEMENT()\n" +
                                "))\n");
                return getFunctionValue(source, "function");
            } else {
                return value;
            }
        });
    }

    protected final void checkDebugValueOf(Object object, Consumer<DebugValue> checker) {
        checkDebugValueOf(object, (event, value) -> checker.accept(value));
    }

    protected final void checkDebugValueOf(Object object, BiConsumer<SuspendedEvent, DebugValue> checker) {
        Value functionValue = getFunctionWithArgument();

        AtomicBoolean suspended = new AtomicBoolean(false);
        try (DebuggerSession session = startSession()) {
            session.suspendNextExecution();
            startExecute(c -> functionValue.execute(object));
            expectSuspended((SuspendedEvent event) -> {
                assertFalse(suspended.get());
                DebugValue a = event.getTopStackFrame().getScope().getDeclaredValue("a");
                assertNotNull(a);
                checker.accept(event, a);
                event.prepareContinue();
                suspended.set(true);
            });
        }
        expectDone();
        assertTrue(suspended.get());
    }

    @SuppressWarnings("static-method")
    protected final com.oracle.truffle.api.source.Source getSourceImpl(Source source) {
        return DebuggerTester.getSourceImpl(source);
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
        return Source.create(InstrumentationTestLanguage.ID, code);
    }

    protected SuspendedEvent checkState(SuspendedEvent suspendedEvent, final int expectedLineNumber, final boolean expectedIsBefore, final String expectedCode, final String... expectedFrame) {
        final int actualLineNumber;
        if (expectedIsBefore) {
            actualLineNumber = suspendedEvent.getSourceSection().getStartLine();
        } else {
            actualLineNumber = suspendedEvent.getSourceSection().getEndLine();
        }
        Assert.assertEquals(expectedLineNumber, actualLineNumber);
        final String actualCode = suspendedEvent.getSourceSection().getCharacters().toString();
        Assert.assertEquals(expectedCode, actualCode);
        final boolean actualIsBefore = (suspendedEvent.getSuspendAnchor() == SuspendAnchor.BEFORE);
        Assert.assertEquals(expectedIsBefore, actualIsBefore);

        checkStack(suspendedEvent.getTopStackFrame(), expectedFrame);
        return suspendedEvent;
    }

    protected SuspendedEvent checkReturn(SuspendedEvent suspendedEvent, final String expectedReturnValue) {
        DebugValue returnValue = suspendedEvent.getReturnValue();
        if (expectedReturnValue == null) {
            Assert.assertNull(returnValue);
        } else {
            Assert.assertEquals(expectedReturnValue, returnValue.toDisplayString());
        }
        return suspendedEvent;
    }

    protected void checkStack(DebugStackFrame frame, String... expectedFrame) {
        Map<String, DebugValue> values = new HashMap<>();
        for (DebugValue value : frame.getScope().getDeclaredValues()) {
            values.put(value.getName(), value);
        }
        Assert.assertEquals(expectedFrame.length / 2, values.size());
        for (int i = 0; i < expectedFrame.length; i = i + 2) {
            String expectedIdentifier = expectedFrame[i];
            String expectedValue = expectedFrame[i + 1];
            DebugValue value = values.get(expectedIdentifier);
            Assert.assertNotNull("Identifier " + expectedIdentifier + " not found.", value);
            Assert.assertEquals(expectedValue, value.toDisplayString());
        }
    }

    protected final String expectDone() {
        return tester.expectDone();
    }

    protected final Throwable expectThrowable() {
        return tester.expectThrowable();
    }

    protected final void expectSuspended(SuspendedCallback handler) {
        tester.expectSuspended(handler);
    }

    protected final void expectKilled() {
        tester.expectKilled();
    }

    protected final void closeEngine() {
        tester.closeEngine();
    }
}
