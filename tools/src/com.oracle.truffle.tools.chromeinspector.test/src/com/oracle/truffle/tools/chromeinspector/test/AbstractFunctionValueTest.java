/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.tools.chromeinspector.test;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.junit.After;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Before;

import com.oracle.truffle.api.instrumentation.test.InstrumentationTestLanguage;

/**
 * An abstract inspector test, that operates on a passed TruffleObject.
 */
public abstract class AbstractFunctionValueTest {

    private static final String CODE = "DEFINE(function, ROOT(\n" +
                    "  ARGUMENT(a), \n" +
                    "  STATEMENT()\n" +
                    "))\n";
    private static final String FILE_NAME = "MapTest.itl";

    protected InspectorTester tester;

    @Before
    public void setUp() throws Exception {
        tester = InspectorTester.start(false);
    }

    // @formatter:off   The default formatting makes unnecessarily big indents and illogical line breaks
    // CheckStyle: stop line length check

    protected final Future<?> runWith(Object truffleObject) throws Exception {
        Source source = Source.newBuilder(InstrumentationTestLanguage.ID, CODE, FILE_NAME).build();
        String testURI = InspectorTester.getStringURI(source.getURI());
        tester.sendMessage("{\"id\":1,\"method\":\"Runtime.enable\"}");
        assertEquals("{\"result\":{},\"id\":1}", tester.getMessages(true).trim());
        tester.sendMessage("{\"id\":2,\"method\":\"Debugger.enable\"}");
        tester.receiveMessages(
                        "{\"result\":{\"debuggerId\":\"UniqueDebuggerId.", "},\"id\":2}\n");
        tester.sendMessage("{\"id\":3,\"method\":\"Debugger.setBreakpointByUrl\",\"params\":{\"lineNumber\":3,\"url\":\"" + FILE_NAME + "\",\"columnNumber\":0,\"condition\":\"\"}}");
        assertTrue(tester.compareReceivedMessages(
                        "{\"result\":{\"breakpointId\":\"1\",\"locations\":[]},\"id\":3}\n"));
        tester.sendMessage("{\"id\":4,\"method\":\"Runtime.runIfWaitingForDebugger\"}");
        assertTrue(tester.compareReceivedMessages(
                        "{\"result\":{},\"id\":4}\n" +
                        "{\"method\":\"Runtime.executionContextCreated\",\"params\":{\"context\":{\"origin\":\"\",\"name\":\"test\",\"id\":1}}}\n"));
        Thread thread = new Thread(() -> {
            Future<Value> eval = tester.eval(source);
            try {
                eval.get().getContext().getBindings(InstrumentationTestLanguage.ID).getMember("function").execute(truffleObject);
            } catch (ExecutionException | InterruptedException ex) {
                ex.printStackTrace();
            }
        });
        thread.start();
        long id = tester.getContextId();

        assertTrue(tester.compareReceivedMessages(
                        "{\"method\":\"Debugger.scriptParsed\",\"params\":{\"endLine\":3,\"scriptId\":\"0\",\"endColumn\":2,\"startColumn\":0,\"startLine\":0,\"length\":" + CODE.length() + ",\"executionContextId\":" + id + ",\"url\":\"" + testURI + "\",\"hash\":\"f123c4e9e1aaf660fb0f0a21f903c238ffffffff\"}}\n" +
                        "{\"method\":\"Debugger.breakpointResolved\",\"params\":{\"breakpointId\":\"1\",\"location\":{\"scriptId\":\"0\",\"columnNumber\":2,\"lineNumber\":2}}}\n"));
        // Stops on the STATEMENT:
        assertTrue(tester.compareReceivedMessages(
                        "{\"method\":\"Debugger.paused\",\"params\":{\"reason\":\"other\",\"hitBreakpoints\":[\"1\"]," +
                                "\"callFrames\":[{\"callFrameId\":\"0\",\"functionName\":\"function\"," +
                                                 "\"scopeChain\":[{\"name\":\"function\",\"type\":\"local\",\"object\":{\"description\":\"function\",\"type\":\"object\",\"objectId\":\"1\"}}," +
                                                                 "{\"name\":\"global\",\"type\":\"global\",\"object\":{\"description\":\"global\",\"type\":\"object\",\"objectId\":\"2\"}}]," +
                                                 "\"this\":null," +
                                                 "\"functionLocation\":{\"scriptId\":\"0\",\"columnNumber\":16,\"lineNumber\":0}," +
                                                 "\"location\":{\"scriptId\":\"0\",\"columnNumber\":2,\"lineNumber\":2}," +
                                                 "\"url\":\"" + testURI + "\"}]}}\n"));
        return new Future<Void>() {
            @Override
            public boolean cancel(boolean mayInterruptIfRunning) {
                throw new UnsupportedOperationException();
            }

            @Override
            public boolean isCancelled() {
                return false;
            }

            @Override
            public boolean isDone() {
                return !thread.isAlive();
            }

            @Override
            public Void get() throws InterruptedException, ExecutionException {
                thread.join();
                return null;
            }

            @Override
            public Void get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
                thread.join(unit.toMillis(timeout));
                return null;
            }
        };
    }

    // @formatter:on
    // CheckStyle: resume line length check

    @After
    public void tearDown() {
        tester = null;
    }

}
