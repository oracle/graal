/*
 * Copyright (c) 2018 Oracle and/or its affiliates. All rights reserved.
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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Properties;
import java.util.Queue;
import java.util.Set;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class SLLoggerTest {

    private TestHandler testHandler;
    private Context ctx;
    private Level rootLoggerLevel;

    @Before
    public void setUp() {
        saveLoggersState();
        testHandler = new TestHandler();
        ctx = Context.newBuilder("sl").logHandler(testHandler).build();
    }

    @After
    public void tearDown() throws IOException {
        if (testHandler != null) {
            testHandler.close();
        }
        if (ctx != null) {
            ctx.close();
        }
        restoreLoggersState();
    }

    @Test
    public void testLoggerNoConfig() throws IOException {
        final ByteArrayInputStream loggerConfig = new ByteArrayInputStream(new byte[0]);
        LogManager.getLogManager().readConfiguration(loggerConfig);
        executeSlScript();
        Assert.assertTrue(functionNames(testHandler.getRecords()).isEmpty());
    }

    @Test
    public void testLoggerSlFunctionLevelFine() throws IOException {
        LogManager.getLogManager().readConfiguration(createLogConfigFile("com.oracle.truffle.sl.runtime.SLFunction.level", "FINE"));
        executeSlScript();
        Assert.assertFalse(functionNames(testHandler.getRecords()).isEmpty());
    }

    @Test
    public void testLoggerSlFunctionParentLevelFine() throws IOException {
        // LogManager.readConfiguration does not restore parent levels for existing loogers with non
        // existing parent - need to have existing parent
        final Logger dummy = Logger.getLogger("com.oracle.truffle.sl.runtime");
        LogManager.getLogManager().readConfiguration(createLogConfigFile("com.oracle.truffle.sl.runtime.level", "FINE"));
        executeSlScript();
        Assert.assertFalse(functionNames(testHandler.getRecords()).isEmpty());
    }

    @Test
    public void testLoggerSlFunctionSiblingLevelFine() throws IOException {
        LogManager.getLogManager().readConfiguration(createLogConfigFile("com.oracle.truffle.sl.runtime.SLContext.level", "FINE"));
        executeSlScript();
        Assert.assertTrue(functionNames(testHandler.getRecords()).isEmpty());
    }

    @Test
    public void testRootLoggerLevelFine() throws IOException {
        LogManager.getLogManager().getLogger("").setLevel(Level.FINE);
        executeSlScript();
        Assert.assertFalse(functionNames(testHandler.getRecords()).isEmpty());
    }

    private void executeSlScript() throws IOException {
        final Source src = Source.newBuilder("sl", "function add(a,b) {return a + b;} function main() {return add(1,1);}", "testLogger.sl").build();
        final Value res = ctx.eval(src);
        Assert.assertTrue(res.isNumber());
        Assert.assertEquals(2, res.asInt());
    }

    private void saveLoggersState() {
        rootLoggerLevel = LogManager.getLogManager().getLogger("").getLevel();
    }

    private void restoreLoggersState() throws IOException {
        LogManager.getLogManager().getLogger("").setLevel(rootLoggerLevel);
        LogManager.getLogManager().readConfiguration();
    }

    private static InputStream createLogConfigFile(String... kvs) throws IOException {
        if ((kvs.length & 1) == 1) {
            throw new IllegalArgumentException("Keys and values has to have even length.");
        }
        final Properties props = new Properties();
        for (int i = 0; i < kvs.length; i += 2) {
            props.setProperty(kvs[i], kvs[i + 1]);
        }
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        props.store(out, null);
        return new ByteArrayInputStream(out.toByteArray());
    }

    private static Set<String> functionNames(final List<? extends LogRecord> records) {
        return records.stream().filter((lr) -> "com.oracle.truffle.sl.runtime.SLFunction".equals(lr.getLoggerName())).map((lr) -> (String) lr.getParameters()[0]).collect(Collectors.toSet());
    }

    private static final class TestHandler extends Handler {
        private final Queue<LogRecord> records;

        TestHandler() {
            this.records = new ArrayDeque<>();
        }

        @Override
        public void publish(LogRecord record) {
            records.offer(record);
        }

        @Override
        public void flush() {
        }

        public List<? extends LogRecord> getRecords() {
            return new ArrayList<>(records);
        }

        @Override
        public void close() throws SecurityException {
            records.clear();
        }
    }
}
