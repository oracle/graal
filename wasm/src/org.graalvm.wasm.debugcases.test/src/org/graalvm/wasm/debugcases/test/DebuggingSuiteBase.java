/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.wasm.debugcases.test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.io.ByteSequence;
import org.graalvm.wasm.WasmContext;
import org.graalvm.wasm.WasmFunctionInstance;
import org.graalvm.wasm.WasmInstance;
import org.graalvm.wasm.WasmLanguage;
import org.graalvm.wasm.utils.WasmResource;
import org.junit.Assert;

import com.oracle.truffle.api.debug.DebugScope;
import com.oracle.truffle.api.debug.DebugStackFrame;
import com.oracle.truffle.api.debug.DebugValue;
import com.oracle.truffle.api.debug.Debugger;
import com.oracle.truffle.api.debug.DebuggerSession;
import com.oracle.truffle.api.debug.SuspendedCallback;

public abstract class DebuggingSuiteBase {
    private static final String BASE_DIR = "wasm";

    private static final ByteArrayOutputStream TEST_OUT = new ByteArrayOutputStream();

    protected abstract String resourceDir();

    private Source getSource(String path, String testCaseName) throws IOException {
        final String resourcePath;
        if (path == null) {
            resourcePath = String.format("/%s/%s/%s", BASE_DIR, resourceDir(), testCaseName);
        } else {
            resourcePath = String.format("/%s/%s/%s/%s", BASE_DIR, resourceDir(), path, testCaseName);
        }
        byte[] source = (byte[]) WasmResource.getResourceAsTest(resourcePath, true);
        if (source == null) {
            return null;
        }
        return Source.newBuilder(WasmLanguage.ID, ByteSequence.create(source), testCaseName).build();
    }

    private void runDebugTest(String path, String testCaseName, SuspendedCallback callbackListener, Runnable exitListener) throws IOException {
        final Context.Builder contextBuilder = Context.newBuilder(WasmLanguage.ID);
        contextBuilder.option("wasm.Builtins", "env:emscripten,wasi_snapshot_preview1");
        if (path == null) {
            contextBuilder.option("wasm.DebugCompDirectory", String.format("/%s/%s/", BASE_DIR, resourceDir()));
        } else {
            contextBuilder.option("wasm.DebugCompDirectory", String.format("/%s/%s/%s/", BASE_DIR, resourceDir(), path));
        }
        final Context context = contextBuilder.out(TEST_OUT).build();
        final Debugger debugger = Debugger.find(context.getEngine());
        final Source source = getSource(path, testCaseName);
        context.eval(source);
        context.enter();
        final WasmContext wasmContext = WasmContext.get(null);
        Value entryPoint = null;
        // find possible entry point
        for (final WasmInstance instance : wasmContext.moduleInstances().values()) {
            final WasmFunctionInstance function = instance.inferEntryPoint();
            if (function != null) {
                entryPoint = Value.asValue(function);
                break;
            }
        }
        if (entryPoint == null) {
            Assert.fail("No entry point exported");
        }
        DebuggerSession session = debugger.startSession(callbackListener);
        session.suspendNextExecution();
        try {
            entryPoint.execute();
        } catch (PolyglotException e) {
            if (e.isExit() && e.getExitStatus() == 0) {
                exitListener.run();
            } else {
                throw e;
            }
        } finally {
            session.close();
            context.close(true);
        }
        exitListener.run();
    }

    /**
     * Runs the given test case.
     *
     * @param path The path to the file
     * @param testCaseName The name of the test file without the file extension
     * @param inspector The actions that should be performed in the debugging session
     * @throws IOException If the file does not exist
     * @throws InterruptedException If the debugging session is interrupted
     */
    protected void runTest(String path, String testCaseName, DebugInspector inspector) throws IOException, InterruptedException {
        runDebugTest(path, testCaseName, event -> {
            final int line = event.getSourceSection().getStartLine();
            final DebugStackFrame topOfStack = event.getTopStackFrame();
            BiConsumer<DebugScope, Iterable<DebugStackFrame>> action = inspector.next(line);
            while (action != null) {
                action.accept(topOfStack.getScope(), event.getStackFrames());
                action = inspector.next(line);
            }
            event.prepareStepInto(1);
        }, () -> Assert.assertTrue("Not all actions have been executed", inspector.isEmpty()));
    }

    protected void runTest(String testCaseName, DebugInspector inspector) throws IOException, InterruptedException {
        runTest(null, testCaseName, inspector);
    }

    /**
     * Creates a new inspector for a test debug session.
     */
    protected static DebugInspector createInspector() {
        return new DebugInspector();
    }

    /**
     * Adds an enter function action to the inspector.
     *
     * @param inspector The inspector
     * @param line The line at which the check should be performed
     * @param name The name of the function that is entered
     */
    protected static void enterFunction(DebugInspector inspector, int line, String name) {
        inspector.enterMethod(line, name);
    }

    /**
     * Adds the function name to the internal stack frame without performing a check.
     *
     * @param inspector The inspector
     * @param name The name of the function that should be added
     */
    protected static void enterFunctionUnchecked(DebugInspector inspector, String name) {
        inspector.enterMethodUnchecked(name);
    }

    /**
     * Removes the topmost entry from the internal stack frame.
     *
     * @param inspector The inspector
     */
    protected static void exitFunction(DebugInspector inspector) {
        inspector.exitMethod();
    }

    /**
     * Adds an action that allows to perform checks on local variables.
     *
     * @param inspector The inspector
     * @param line The line at which the check should be performed
     * @param checkFunction The function that should be executed for checking the locals
     */
    protected static void checkLocals(DebugInspector inspector, int line, Consumer<DebugValue> checkFunction) {
        inspector.checkLocals(line, checkFunction);
    }

    /**
     * Adds an action that allows to perform checks on global variables.
     *
     * @param inspector The inspector
     * @param line The line at which the check should be performed
     * @param checkFunction The function that should be executed for checking the globals
     */
    protected static void checkGlobals(DebugInspector inspector, int line, Consumer<DebugValue> checkFunction) {
        inspector.checkGlobals(line, checkFunction);
    }

    /**
     * Adds an action that move to the given line without performing any checks.
     *
     * @param inspector The inspector
     * @param line The target line
     */
    protected static void moveTo(DebugInspector inspector, int line) {
        inspector.checkLocals(line, locals -> {
        });
    }
}
