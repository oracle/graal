/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.tools.insight.test;

import com.oracle.truffle.api.exception.AbstractTruffleException;
import com.oracle.truffle.api.instrumentation.test.InstrumentationTestLanguage;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.test.polyglot.ProxyLanguage;
import java.io.ByteArrayOutputStream;
import static org.graalvm.tools.insight.test.InsightObjectFactory.createConfig;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Predicate;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Instrument;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.graalvm.tools.insight.Insight;
import org.junit.Assert;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import org.junit.Before;
import org.junit.Test;

public class InsightObjectTest {
    @Before
    public void cleanAgentObject() {
        InsightObjectFactory.cleanAgentObject();
    }

    @Test
    public void versionOfTheAgent() throws Exception {
        try (Context c = InsightObjectFactory.newContext()) {
            Value agent = InsightObjectFactory.readInsight(c, null);
            InsightAPI agentAPI = agent.as(InsightAPI.class);
            Assert.assertNotNull("Agent API obtained", agentAPI);
            assertEquals(Insight.VERSION, agentAPI.version());
        }
    }

    @Test
    public void versionOfTheAgentDirect() throws Exception {
        try (Context c = InsightObjectFactory.newContext()) {
            Object[] insightObject = {null};
            Value agent = InsightObjectFactory.readInsight(c, insightObject);
            assertNotNull("agent created", agent);
            assertNotNull("we have agent's truffle object", insightObject[0]);

            InteropLibrary iop = InteropLibrary.getFactory().getUncached();

            assertTrue("Yes, it has members", iop.hasMembers(insightObject[0]));

            Object members = iop.getMembers(insightObject[0]);
            long membersCount = iop.getArraySize(members);
            assertEquals(2, membersCount);

            assertEquals("id", iop.readArrayElement(members, 0));
            assertEquals("version", iop.readArrayElement(members, 1));
        }
    }

    @Test
    public void onErrorneousCallbackRegistration() throws Exception {
        try (Context c = InsightObjectFactory.newContext()) {
            Value agent = InsightObjectFactory.readInsight(c, null);
            InsightAPI agentAPI = agent.as(InsightAPI.class);
            Assert.assertNotNull("Agent API obtained", agentAPI);

            final InsightAPI.OnSourceLoadedHandler listener = (ev) -> {
            };
            agentAPI.on("enterOrLeave", listener);
            fail("Should have failed with PolyglotException");
        } catch (PolyglotException t) {
            assertTrue(t.getMessage(), t.getMessage().startsWith("insight: Unknown event type"));
        }
    }

    @Test
    public void onSourceCallback() throws Exception {
        try (Context c = InsightObjectFactory.newContext()) {
            Value agent = InsightObjectFactory.readInsight(c, null);
            InsightAPI agentAPI = agent.as(InsightAPI.class);
            Assert.assertNotNull("Agent API obtained", agentAPI);

            String[] loadedScript = new String[5];
            final InsightAPI.OnSourceLoadedHandler listener = (ev) -> {
                loadedScript[0] = ev.name();
                loadedScript[1] = ev.characters();
                loadedScript[2] = ev.language();
                loadedScript[3] = ev.mimeType();
                loadedScript[4] = ev.uri();
            };
            agentAPI.on("source", listener);

            Source sampleScript = Source.newBuilder(ProxyLanguage.ID, "sample, code", "sample.px").mimeType("application/x-proxy-language").uri(new URI("http://app.test/data")).build();
            c.eval(sampleScript);

            assertEquals(sampleScript.getName(), loadedScript[0]);
            assertEquals("sample, code", loadedScript[1]);
            assertEquals("language check", ProxyLanguage.ID, loadedScript[2]);
            assertEquals("mime type check", "application/x-proxy-language", loadedScript[3]);
            assertEquals("URI", "http://app.test/data", loadedScript[4]);

            loadedScript[0] = null;

            agentAPI.off("source", listener);

            Source sampleScript2 = Source.newBuilder(ProxyLanguage.ID, "sample, code", "sample.px").mimeType("application/x-proxy-language").uri(new URI("http://app.test/data2")).build();
            c.eval(sampleScript2);

            assertNull("No new script load notified", loadedScript[0]);
        }
    }

    @Test
    public void nullMimeType() throws Exception {
        try (Context c = InsightObjectFactory.newContext()) {
            Value agent = InsightObjectFactory.readInsight(c, null);
            InsightAPI agentAPI = agent.as(InsightAPI.class);
            Assert.assertNotNull("Agent API obtained", agentAPI);

            String[] loadedScript = new String[5];
            agentAPI.on("source", (ev) -> {
                loadedScript[0] = ev.name();
                loadedScript[1] = ev.characters();
                loadedScript[2] = ev.language();
                loadedScript[3] = ev.mimeType();
                loadedScript[4] = ev.uri();
            });

            Source sampleScript = Source.newBuilder(ProxyLanguage.ID, "another code", "sample.px").build();
            c.eval(sampleScript);

            assertEquals(sampleScript.getName(), loadedScript[0]);
            assertEquals("another code", loadedScript[1]);
            assertEquals("language check", ProxyLanguage.ID, loadedScript[2]);
            assertNull("null mime type", loadedScript[3]);
            assertNotNull("Some uri generated", loadedScript[4]);
            assertTrue("Uses truffle prefix: " + loadedScript[4], loadedScript[4].startsWith("truffle:"));
        }
    }

    @Test
    public void onEnterCallback() throws Exception {
        try (Context c = InsightObjectFactory.newContext()) {
            Value agent = InsightObjectFactory.readInsight(c, null);
            InsightAPI agentAPI = agent.as(InsightAPI.class);
            Assert.assertNotNull("Agent API obtained", agentAPI);

            boolean[] program = {false};
            String[] functionName = {null};
            final InsightAPI.OnEventHandler listener = (ctx, frame) -> {
                if (ctx.name().length() == 0) {
                    assertFalse("Program root is entered just once", program[0]);
                    program[0] = true;
                    return;
                }
                assertNull("No function entered yet", functionName[0]);
                functionName[0] = ctx.name();
            };
            agentAPI.on("enter", listener, InsightObjectFactory.createConfig(false, false, true, null, null));

            // @formatter:off
            Source sampleScript = Source.newBuilder(InstrumentationTestLanguage.ID,
                "ROOT(\n" +
                "  DEFINE(foo,\n" +
                "    LOOP(10, STATEMENT(EXPRESSION,EXPRESSION))\n" +
                "  ),\n" +
                "  CALL(foo)\n" +
                ")",
                "sample.px"
            ).build();
            // @formatter:on
            c.eval(sampleScript);

            assertTrue("Program started", program[0]);
            assertEquals("Function foo has been called", "foo", functionName[0]);

            agentAPI.off("enter", listener);
            program[0] = false;
            functionName[0] = null;

            // @formatter:off
            Source sampleScript2 = Source.newBuilder(InstrumentationTestLanguage.ID,
                    "ROOT(\n"
                    + "  DEFINE(foo,\n"
                    + "    LOOP(10, STATEMENT(EXPRESSION,EXPRESSION))\n"
                    + "  ),\n"
                    + "  CALL(foo)\n"
                    + ")",
                    "sample.px"
            ).build();
            // @formatter:on
            c.eval(sampleScript2);

            assertFalse("No listener notified", program[0]);
            assertNull("No function entered", functionName[0]);
        }
    }

    @Test
    public void evalFirstAndThenOnEnterCallback() throws Throwable {
        Executor direct = (c) -> c.run();
        evalFirstAndThenOnEnterCallbackImpl(direct);
    }

    @Test
    public void evalFirstAndThenOnEnterCallbackInBackground() throws Throwable {
        Executor background = Executors.newSingleThreadExecutor();
        evalFirstAndThenOnEnterCallbackImpl(background);
    }

    private static void evalFirstAndThenOnEnterCallbackImpl(Executor registerIn) throws Throwable {
        try (Context c = InsightObjectFactory.newContext()) {

            // @formatter:off
            Source sampleScript = Source.newBuilder(InstrumentationTestLanguage.ID,
                "ROOT(\n" +
                "  DEFINE(foo,\n" +
                "    LOOP(10, STATEMENT(EXPRESSION,EXPRESSION))\n" +
                "  ),\n" +
                "  CALL(foo)\n" +
                ")",
                "sample.px"
            ).build();
            // @formatter:on
            c.eval(sampleScript);

            Value agent = InsightObjectFactory.readInsight(c, null);
            InsightAPI agentAPI = agent.as(InsightAPI.class);
            Assert.assertNotNull("Agent API obtained", agentAPI);

            String[] functionName = {null};
            final InsightAPI.OnEventHandler listener = (ctx, frame) -> {
                if (ctx.name().length() == 0) {
                    return;
                }
                assertNull("No function entered yet", functionName[0]);
                functionName[0] = ctx.name();
                assertNotEquals("Subset of the source found", -1, ctx.characters().indexOf("LOOP(10, STATEMENT(EXPRESSION,EXPRESSION))"));

                assertEquals(14, ctx.column());
                assertEquals(14, ctx.startColumn());
                assertEquals(2, ctx.endColumn());

                assertEquals(2, ctx.line());
                assertEquals(2, ctx.startLine());
                assertEquals(4, ctx.endLine());

                String fullText = ctx.source().characters();
                final int charIndex = ctx.charIndex();
                final int charEndIndex = ctx.charEndIndex();
                final int charLength = ctx.charLength();

                final int begOffset = indexOfLine(fullText, 2) + 13;
                final int endOffset = indexOfLine(fullText, 4) + 2;
                assertEquals(begOffset, charIndex);
                assertEquals(endOffset, charEndIndex);
                assertEquals(endOffset - begOffset, charLength);
            };

            CountDownLatch await = new CountDownLatch(1);
            Throwable[] err = {null};
            registerIn.execute(() -> {
                try {
                    agentAPI.on("enter", listener, InsightObjectFactory.createConfig(false, false, true, null, null));
                } catch (Throwable t) {
                    err[0] = t;
                } finally {
                    await.countDown();
                }
            });
            await.await(10, TimeUnit.SECONDS);
            if (err[0] != null) {
                throw err[0];
            }

            // @formatter:off
            Source runScript = Source.newBuilder(InstrumentationTestLanguage.ID,
                    "ROOT(\n"
                    + "  CALL(foo)\n"
                    + ")",
                    "run.px"
            ).build();
            // @formatter:on
            c.eval(runScript);

            assertEquals("Function foo has been called", "foo", functionName[0]);
        }
    }

    @Test
    public void onEnterCallbackWithFilterOnRootName() throws Exception {
        boolean[] finished = {false};
        try (Context c = InsightObjectFactory.newContext()) {
            Value agent = InsightObjectFactory.readInsight(c, null);
            InsightAPI agentAPI = agent.as(InsightAPI.class);
            Assert.assertNotNull("Agent API obtained", agentAPI);

            String[] functionName = {null};
            agentAPI.on("enter", (ctx, frame) -> {
                assertNull("No function entered yet", functionName[0]);
                functionName[0] = ctx.name();
            }, InsightObjectFactory.createConfig(false, false, true, "foo", null));
            agentAPI.on("close", () -> {
                finished[0] = true;
            });

            // @formatter:off
            Source sampleScript = Source.newBuilder(InstrumentationTestLanguage.ID,
                "ROOT(\n" +
                "  DEFINE(foo,\n" +
                "    LOOP(10, STATEMENT(EXPRESSION,EXPRESSION))\n" +
                "  ),\n" +
                "  DEFINE(bar,\n" +
                "    CALL(foo)\n" +
                "  ),\n" +
                "  CALL(bar)\n" +
                ")",
                "sample.px"
            ).build();
            // @formatter:on
            c.eval(sampleScript);

            assertEquals("Function foo has been called", "foo", functionName[0]);

            assertFalse("Not closed yet", finished[0]);
        }
        assertTrue("Closed now", finished[0]);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void onEnterCallbackWithFilterOnSourceName() throws Exception {
        boolean[] finished = {false};
        try (Context c = InsightObjectFactory.newContext()) {
            Value agent = InsightObjectFactory.readInsight(c, null);
            InsightAPI agentAPI = agent.as(InsightAPI.class);
            Assert.assertNotNull("Agent API obtained", agentAPI);

            String[] functionName = {null};
            agentAPI.on("enter", (ctx, frame) -> {
                if (ctx.name().isEmpty()) {
                    return;
                }
                assertNull("No function entered yet", functionName[0]);
                functionName[0] = ctx.name();
            }, InsightObjectFactory.createConfig(false, false, true, null, new SourceNameCheck("foo.px")));
            agentAPI.on("close", () -> {
                finished[0] = true;
            });

            // @formatter:off
            Source foo = Source.newBuilder(InstrumentationTestLanguage.ID,
                "ROOT(\n" +
                "  DEFINE(foo,\n" +
                "    LOOP(10, STATEMENT(EXPRESSION,EXPRESSION))\n" +
                "  )\n" +
                ")",
                "foo.px"
            ).build();
            // @formatter:on
            c.eval(foo);
            // @formatter:off
            Source bar = Source.newBuilder(InstrumentationTestLanguage.ID,
                "ROOT(\n" +
                "  DEFINE(bar,\n" +
                "    CALL(foo)\n" +
                "  ),\n" +
                "  CALL(bar)\n" +
                ")",
                "bar.px"
            ).build();
            // @formatter:on
            c.eval(bar);

            assertEquals("Function foo has been called", "foo", functionName[0]);

            assertFalse("Not closed yet", finished[0]);
        }
        assertTrue("Closed now", finished[0]);
    }

    @Test
    public void onStatementCallback() throws Exception {
        try (Context c = InsightObjectFactory.newContext()) {
            Value agent = InsightObjectFactory.readInsight(c, null);
            InsightAPI agentAPI = agent.as(InsightAPI.class);
            Assert.assertNotNull("Agent API obtained", agentAPI);

            int[] loopIndexSum = {0};
            agentAPI.on("enter", (ctx, frame) -> {
                Object index = frame.get("loopIndex0");
                assertTrue("Number as expected: " + index, index instanceof Number);
                loopIndexSum[0] += ((Number) index).intValue();
            }, InsightObjectFactory.createConfig(false, true, false, null, null));

            // @formatter:off
            Source sampleScript = Source.newBuilder(InstrumentationTestLanguage.ID,
                "ROOT(\n" +
                "  DEFINE(foo,\n" +
                "    LOOP(10, STATEMENT(EXPRESSION,EXPRESSION))\n" +
                "  ),\n" +
                "  CALL(foo)\n" +
                ")",
                "sample.px"
            ).build();
            // @formatter:on
            c.eval(sampleScript);

            assertEquals("0,1,2,...9 indexes", 10 * 9 / 2, loopIndexSum[0]);
        }
    }

    @Test
    public void onExpressionCallback() throws Exception {
        try (Context c = InsightObjectFactory.newContext()) {
            Value agent = InsightObjectFactory.readInsight(c, null);
            InsightAPI agentAPI = agent.as(InsightAPI.class);
            Assert.assertNotNull("Agent API obtained", agentAPI);

            int[] expressionCounter = {0};
            int[] expressionReturnCounter = {0};
            agentAPI.on("enter", (ev, frame) -> {
                expressionCounter[0]++;
            }, InsightObjectFactory.createConfig(true, false, false, null, null));
            agentAPI.on("return", (ev, frame) -> {
                expressionReturnCounter[0]++;
            }, InsightObjectFactory.createConfig(true, false, false, null, null));

            // @formatter:off
            Source sampleScript = Source.newBuilder(InstrumentationTestLanguage.ID,
                "ROOT(\n" +
                "  DEFINE(foo,\n" +
                "    LOOP(10, STATEMENT(EXPRESSION,EXPRESSION))\n" +
                "  ),\n" +
                "  CALL(foo)\n" +
                ")",
                "sample.px"
            ).build();
            // @formatter:on
            c.eval(sampleScript);

            assertEquals("10x2 expressions", 20, expressionCounter[0]);
            assertEquals("Same amount of expressions", expressionCounter[0], expressionReturnCounter[0]);
        }
    }

    @Test
    public void internalScriptsAreIgnored() throws Exception {
        int[] closeCounter = {0};
        try (Context c = InsightObjectFactory.newContext()) {
            Value agent = InsightObjectFactory.readInsight(c, null);
            InsightAPI agentAPI = agent.as(InsightAPI.class);
            Assert.assertNotNull("Agent API obtained", agentAPI);

            // @formatter:off
            Source sampleScript = Source.newBuilder(InstrumentationTestLanguage.ID,
                "ROOT(\n" +
                "  DEFINE(foo,\n" +
                "    LOOP(10, STATEMENT(EXPRESSION,EXPRESSION))\n" +
                "  ),\n" +
                "  CALL(foo)\n" +
                ")",
                "sample.px"
            ).internal(true).build();
            // @formatter:on

            final InsightAPI.OnSourceLoadedHandler listener = (ev) -> {
                if (ev.name().equals(sampleScript.getName())) {
                    Assert.fail("Don't load internal scripts: " + ev.uri());
                }
            };
            agentAPI.on("source", listener);

            int[] expressionCounter = {0};
            agentAPI.on("enter", (ev, frame) -> {
                expressionCounter[0]++;
            }, InsightObjectFactory.createConfig(true, false, false, null, null));
            agentAPI.on("return", (ev, frame) -> {
                expressionCounter[0]++;
            }, InsightObjectFactory.createConfig(true, false, false, null, null));

            agentAPI.on("close", () -> {
                closeCounter[0]++;
            });

            c.eval(sampleScript);

            assertEquals("No expressions entered & exited", 0, expressionCounter[0]);
        }
        assertEquals("Close is reported", 1, closeCounter[0]);
    }

    @Test
    public void onEnterAndReturn() throws Exception {
        try (Context c = InsightObjectFactory.newContext()) {
            Value agent = InsightObjectFactory.readInsight(c, null);
            InsightAPI agentAPI = agent.as(InsightAPI.class);
            Assert.assertNotNull("Agent API obtained", agentAPI);

            String[][] max = {new String[0]};
            LinkedList<String> stack = new LinkedList<>();
            final InsightAPI.OnConfig allRoots = InsightObjectFactory.createConfig(false, false, true, null, null);
            agentAPI.on("enter", (ev, frame) -> {
                stack.push(ev.name());
                if (stack.size() > max[0].length) {
                    max[0] = stack.toArray(new String[0]);
                }
            }, allRoots);
            agentAPI.on("return", (ev, frame) -> {
                String prev = stack.pop();
                assertEquals("Exit from a topmost scope", prev, ev.name());

            }, allRoots);

            // @formatter:off
            Source sampleScript = Source.newBuilder(InstrumentationTestLanguage.ID,
                "ROOT(\n" +
                "  DEFINE(mar,\n" +
                "    LOOP(10, STATEMENT(EXPRESSION,EXPRESSION))\n" +
                "  ),\n" +
                "  DEFINE(bar,\n" +
                "    LOOP(10, CALL(mar))\n" +
                "  ),\n" +
                "  DEFINE(foo,\n" +
                "    LOOP(10, CALL(bar))\n" +
                "  ),\n" +
                "  CALL(foo)\n" +
                ")",
                "sample.px"
            ).build();
            // @formatter:on
            c.eval(sampleScript);

            List<String> maxStack = Arrays.asList(max[0]);
            assertEquals("Three functions & main program", Arrays.asList("mar", "bar", "foo", ""), maxStack);
        }
    }

    @Test
    public void accessFrameVariables() throws Exception {
        try (Context c = InsightObjectFactory.newContext()) {
            Value agent = InsightObjectFactory.readInsight(c, null);
            InsightAPI agentAPI = agent.as(InsightAPI.class);
            Assert.assertNotNull("Agent API obtained", agentAPI);

            // @formatter:off
            Source sampleScript = Source.newBuilder(InstrumentationTestLanguage.ID,
                "ROOT(\n" +
                "  DEFINE(do_mul,\n" +
                "    ARGUMENT(a),\n" +
                "    ARGUMENT(b),\n" +
                "    EXPRESSION\n" +
                "  ),\n" +
                "  DEFINE(mul,\n" +
                "    ARGUMENT(a),\n" +
                "    ARGUMENT(b),\n" +
                "    CALL_WITH(do_mul, 1, READ_VAR(a), READ_VAR(b))\n" +
                "  ),\n" +
                "  CALL(mul, CONSTANT(6), CONSTANT(7))\n" +
                ")",
                "sample.px"
            ).build();
            // @formatter:on

            Set<String> names = new TreeSet<>();
            final InsightAPI.OnEventHandler captureNames = (ctx, frame) -> {
                assertTrue(names.isEmpty());
                names.addAll(frame.keySet());
            };
            agentAPI.on("enter", captureNames, createConfig(true, false, false, "do_mul.*", null));
            c.eval(sampleScript);
            agentAPI.off("enter", captureNames);

            Assert.assertArrayEquals("THIS, a and b found", new Object[]{"THIS", "a", "b"}, names.toArray());

            Object[] values = {0, 0, 0};
            agentAPI.on("enter", (ctx, frame) -> {
                values[0] = frame.get("a");
                values[1] = frame.get("b");
                frame.put("a", 33);
                assertEquals(Integer.valueOf(33), frame.get("a"));
                frame.put("a", "ahoj");
                assertEquals("ahoj", frame.get("a"));
                try {
                    frame.put("c", 42);
                    fail("Expecting an exception when setting unknown variable c");
                } catch (IllegalArgumentException t) {
                    if (!t.getMessage().contains("identifier 'c'")) {
                        fail(t.getMessage());
                    }
                }
                values[2] = frame.get("THIS");
                try {
                    frame.put("THIS", 42);
                    fail("Expecting an exception when setting THIS");
                } catch (IllegalArgumentException t) {
                    if (!t.getMessage().contains("identifier 'THIS'")) {
                        fail(t.getMessage());
                    }
                }
            }, InsightObjectFactory.createConfig(true, false, false, "do_mul", null));

            Value mul = c.getBindings(InstrumentationTestLanguage.ID).getMember("mul");
            assertNotNull("mul function found", mul);
            assertTrue("mul function found", mul.canExecute());

            Random r = new Random();
            for (int i = 1; i <= 100000; i++) {
                int a = r.nextInt();
                int b = r.nextInt();

                mul.execute(a, b);

                assertEquals(i + "th: a has been read", a, values[0]);
                assertEquals(i + "th: b has been read", b, values[1]);
                assertEquals(i + "th: THIS has been read", 1, values[2]);
            }
        }
    }

    @Test
    public void changeReturnValue() throws Exception {
        try (Context c = InsightObjectFactory.newContext()) {
            Value agent = InsightObjectFactory.readInsight(c, null);
            InsightAPI agentAPI = agent.as(InsightAPI.class);
            Assert.assertNotNull("Agent API obtained", agentAPI);

            // @formatter:off
            Source sampleScript = Source.newBuilder(InstrumentationTestLanguage.ID,
                "ROOT(\n" +
                "  DEFINE(mul,\n" +
                "    ARGUMENT(a),\n" +
                "    ARGUMENT(b),\n" +
                "    EXPRESSION\n" +
                "  ),\n" +
                "  CALL(mul, CONSTANT(6), CONSTANT(7))\n" +
                ")",
                "sample.px"
            ).build();
            // @formatter:on

            final InsightAPI.OnEventHandler return42 = (ctx, frame) -> {
                ctx.returnNow(42);
            };
            agentAPI.on("enter", return42, createConfig(true, false, false, "mul.*", null));
            Value fourtyTwo = c.eval(sampleScript);
            agentAPI.off("enter", return42);

            assertEquals(42, fourtyTwo.asInt());
        }
    }

    @Test
    public void doubleReturnValue() throws Exception {
        try (Context c = InsightObjectFactory.newContext()) {
            Value agent = InsightObjectFactory.readInsight(c, null);
            InsightAPI agentAPI = agent.as(InsightAPI.class);
            Assert.assertNotNull("Agent API obtained", agentAPI);

            // @formatter:off
            Source sampleScript = Source.newBuilder(InstrumentationTestLanguage.ID,
                "ROOT(\n" +
                "  DEFINE(meaning,\n" +
                "    EXPRESSION(\n" +
                "      CONSTANT(6),\n" +
                "      CONSTANT(7)\n" +
                "    )\n" +
                "  ),\n" +
                "  CALL(meaning)\n" +
                ")",
                "sample.px"
            ).build();
            // @formatter:on

            final InsightAPI.OnEventHandler return42 = (ctx, frame) -> {
                Object obj = ctx.returnValue(frame);
                assertTrue("A string is returned: " + obj, obj instanceof String);
                String[] constants = obj.toString().replaceAll("[\\(\\)]", "").split("\\+");
                Assert.assertArrayEquals("6 and 7", new String[]{"6", "7"}, constants);
                int mul = Integer.parseInt(constants[0]) * Integer.parseInt(constants[1]);
                ctx.returnNow(mul);
            };
            agentAPI.on("return", return42, createConfig(true, false, false, "meaning.*", null));
            Value fourtyTwo = c.eval(sampleScript);
            agentAPI.off("return", return42);

            assertEquals(42, fourtyTwo.asInt());
        }
    }

    @Test
    public void wrongReturnValueCall() throws Exception {
        try (Context c = InsightObjectFactory.newContext()) {
            Value agent = InsightObjectFactory.readInsight(c, null);
            InsightAPI agentAPI = agent.as(InsightAPI.class);
            Assert.assertNotNull("Agent API obtained", agentAPI);

            // @formatter:off
            Source sampleScript = Source.newBuilder(InstrumentationTestLanguage.ID,
                "ROOT(\n" +
                "  DEFINE(meaning,\n" +
                "    EXPRESSION(\n" +
                "      CONSTANT(6),\n" +
                "      CONSTANT(7)\n" +
                "    )\n" +
                "  ),\n" +
                "  CALL(meaning)\n" +
                ")",
                "sample.px"
            ).build();
            // @formatter:on

            final InsightAPI.OnEventHandler return42 = (ctx, frame) -> {
                try {
                    ctx.returnValue(Collections.emptyMap());
                } catch (RuntimeException ex) {
                    assertTrue("Expecting TruffleException: " + ex, ex instanceof AbstractTruffleException);
                }
                ctx.returnNow(42);
            };
            agentAPI.on("return", return42, createConfig(true, false, false, "meaning.*", null));
            Value fourtyTwo = c.eval(sampleScript);
            agentAPI.off("return", return42);

            assertEquals(42, fourtyTwo.asInt());
        }
    }

    @Test
    public void closeOfInsightHandleIsChecked() throws Exception {
        try (Context c = InsightObjectFactory.newContext()) {
            AutoCloseable[] handle = {null};
            Value agent = InsightObjectFactory.readInsight(c, null, handle);
            InsightAPI agentAPI = agent.as(InsightAPI.class);
            Assert.assertNotNull("Agent API obtained", agentAPI);

            // @formatter:off
            Source sampleScript = Source.newBuilder(InstrumentationTestLanguage.ID,
                "ROOT(\n" +
                "  DEFINE(meaning,\n" +
                "    EXPRESSION(\n" +
                "      CONSTANT(6),\n" +
                "      CONSTANT(7)\n" +
                "    )\n" +
                "  ),\n" +
                "  CALL(meaning)\n" +
                ")",
                "sample.px"
            ).build();
            // @formatter:on

            final InsightAPI.OnEventHandler return42 = (ctx, frame) -> {
                try {
                    ctx.returnValue(Collections.emptyMap());
                } catch (RuntimeException ex) {
                    assertTrue("Expecting TruffleException: " + ex, ex instanceof AbstractTruffleException);
                }
                ctx.returnNow(42);
            };
            agentAPI.on("return", return42, createConfig(true, false, false, "meaning.*", null));
            Value fourtyTwo = c.eval(sampleScript);
            assertEquals(42, fourtyTwo.asInt());
            handle[0].close();
            Value sixSeven = c.eval(sampleScript);
            assertEquals("Hook is no longer active", "(6+7)", sixSeven.asString());

            try {
                agentAPI.on("enter", return42, createConfig(true, false, false, "meaning.*", null));
                fail("Expecting exception");
            } catch (PolyglotException ex) {
                assertEquals("insight: The script has already been closed", ex.getMessage());
            }
        }
    }

    @Test
    public void unknownLanguageCall() throws Exception {
        try (Context c = InsightObjectFactory.newContext()) {
            Instrument insight = c.getEngine().getInstruments().get(Insight.ID);
            assertNotNull("found", insight);

            // @formatter:off
            Source sampleScript = Source.newBuilder(InstrumentationTestLanguage.ID,
                "ROOT(\n" +
                "  DEFINE(meaning,\n" +
                "    EXPRESSION(\n" +
                "      CONSTANT(6),\n" +
                "      CONSTANT(7)\n" +
                "    )\n" +
                "  ),\n" +
                "  CALL(meaning)\n" +
                ")",
                "sample.px"
            ).build();

            Source insightScript = Source.newBuilder("unknownInsightLanguage",
                "anything",
                "insightScript.uil"
            ).build();
            // @formatter:on

            @SuppressWarnings("unchecked")
            Object script = insight.lookup(Function.class).apply(insightScript);
            assertNotNull("Handle returned", script);

            try {
                Value fourtyTwo = c.eval(sampleScript);
                assertEquals("Unlikely to happen, expect exception", 42, fourtyTwo.asInt());
            } catch (PolyglotException ex) {
                assertNotEquals("Missing language error reported", -1, ex.getMessage().indexOf("No language for id unknownInsightLanguage found"));
            }
        }
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void exportViaSymbolProvider() throws Exception {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        Context.Builder b = Context.newBuilder();
        b.option("count", "41");
        try (Context c = InsightObjectFactory.newContext(b, os, os)) {
            Instrument insight = c.getEngine().getInstruments().get(Insight.ID);
            assertNotNull("found", insight);

            Value count = InsightObjectFactory.readObject(c, "count");
            assertEquals(41, count.asInt());
        }
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void meaningViaSymbolProvider() throws Exception {
        try (Context c = InsightObjectFactory.newContext()) {
            Instrument insight = c.getEngine().getInstruments().get(Insight.ID);
            assertNotNull("found", insight);

            Value meaning = InsightObjectFactory.readObject(c, "meaning");
            assertEquals(42, meaning.asInt());
        }
    }

    @Test
    public void iterateFrames() throws Exception {
        try (Context c = InsightObjectFactory.newContext()) {
            Value insight = InsightObjectFactory.readInsight(c, null);
            InsightAPI insightAPI = insight.as(InsightAPI.class);
            Assert.assertNotNull("Agent API obtained", insightAPI);

            // @formatter:off
            Source s1 = Source.newBuilder(InstrumentationTestLanguage.ID,
                "ROOT(\n" +
                "  DEFINE(s1,\n" +
                "    EXPRESSION(\n" +
                "      CONSTANT(6),\n" +
                "      CONSTANT(7)\n" +
                "    )\n" +
                "  )\n" +
                ")",
                "s1.px"
            ).build();

            Source s2internal = Source.newBuilder(InstrumentationTestLanguage.ID,
                "ROOT(\n" +
                "  DEFINE(s2,\n" +
                "    CALL(s1)\n" +
                "  )\n" +
                ")",
                "s2.px"
            ).internal(true).build();

            Source s3 = Source.newBuilder(InstrumentationTestLanguage.ID,
                "ROOT(\n" +
                "  CALL(s2)\n" +
                ")",
                "s3.px"
            ).build();
            // @formatter:on

            String[] firstSourceName = {"ignore"};
            List<InsightAPI.SourceSectionInfo> stackTraces = new ArrayList<>();
            final InsightAPI.OnEventHandler threadDump = (ctx, frame) -> {
                InsightAPI.FramesIterator<String> it = (loc, vars) -> {
                    stackTraces.add(loc);
                    return firstSourceName[0] == null ? loc.source().name() : null;
                };
                String value = ctx.iterateFrames(it);
                if (firstSourceName[0] == null) {
                    firstSourceName[0] = value;
                }
            };
            insightAPI.on("return", threadDump, createConfig(true, false, false, "s1", null));

            c.eval(s1);
            c.eval(s2internal);
            c.eval(s3);

            assertEquals("Two frames", 2, stackTraces.size());
            assertEquals("s1.px", stackTraces.get(0).source().name());
            assertEquals("s3.px", stackTraces.get(1).source().name());

            firstSourceName[0] = null;
            c.eval(s3);
            assertEquals("Third added", 3, stackTraces.size());
            assertEquals("s1.px", stackTraces.get(2).source().name());
        }
    }

    @SuppressWarnings("rawtypes")
    public static class SourceNameCheck implements Predicate {
        private final String name;

        SourceNameCheck(String name) {
            this.name = name;
        }

        @Override
        public boolean test(Object obj) {
            Map src = (Map) obj;
            Object srcName = src.get("name");
            return name.equals(srcName);
        }
    }

    @SuppressWarnings("all")
    private static int indexOfLine(String text, int line) {
        int offset = 0;
        for (String l : text.split("\n")) {
            if (--line <= 0) {
                return offset;
            }
            offset += l.length();
            offset++;
        }
        throw new IllegalStateException();
    }
}
