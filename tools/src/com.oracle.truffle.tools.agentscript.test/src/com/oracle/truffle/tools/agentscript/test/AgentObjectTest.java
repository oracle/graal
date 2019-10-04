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
package com.oracle.truffle.tools.agentscript.test;

import com.oracle.truffle.api.instrumentation.test.InstrumentationTestLanguage;
import com.oracle.truffle.api.test.polyglot.ProxyLanguage;
import java.util.Map;
import java.util.function.Predicate;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.junit.Assert;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class AgentObjectTest {
    public interface API {
        public interface Context {
            String name();
        }

        @FunctionalInterface
        public interface OnEventHandler {
            void eventHasJustHappened(Context ctx);
        }

        @FunctionalInterface
        public interface OnEventWithFrameHandler {
            void eventHasJustHappened(Context ctx, Map<String, Object> frame);
        }

        void on(String event, OnEventHandler handler);

        void on(String event, OnEventWithFrameHandler handler);

        void on(String event, OnEventHandler handler, OnConfig config);

        void on(String event, OnEventWithFrameHandler handler, OnConfig config);

        final class OnConfig {
            public final boolean expressions;
            public final boolean statements;
            public final boolean roots;
            public final Predicate<String> rootNameFilter;

            OnConfig(boolean expressions, boolean statements, boolean roots) {
                this(expressions, statements, roots, null);
            }

            OnConfig(boolean expressions, boolean statements, boolean roots, Predicate<String> rootNameFilter) {
                this.expressions = expressions;
                this.statements = statements;
                this.roots = roots;
                this.rootNameFilter = rootNameFilter;
            }
        }
    }

    @Test
    public void onSourceCallback() throws Exception {
        try (Context c = Context.newBuilder().allowHostAccess(HostAccess.ALL).build()) {
            Value agent = AgentObjectFactory.createAgentObject(c);
            API agentAPI = agent.as(API.class);
            Assert.assertNotNull("Agent API obtained", agentAPI);

            String[] loadedScript = {null};
            agentAPI.on("source", (ev) -> {
                loadedScript[0] = ev.name();
            });

            Source sampleScript = Source.newBuilder(ProxyLanguage.ID, "sample, code", "sample.px").build();
            c.eval(sampleScript);

            assertEquals(sampleScript.getName(), loadedScript[0]);
        }
    }

    @Test
    public void onEnterCallback() throws Exception {
        try (Context c = Context.newBuilder().allowHostAccess(HostAccess.ALL).build()) {
            Value agent = AgentObjectFactory.createAgentObject(c);
            API agentAPI = agent.as(API.class);
            Assert.assertNotNull("Agent API obtained", agentAPI);

            boolean[] program = {false};
            String[] functionName = {null};
            agentAPI.on("enter", (ctx, frame) -> {
                if (ctx.name().length() == 0) {
                    assertFalse("Program root is entered just once", program[0]);
                    program[0] = true;
                    return;
                }
                assertNull("No function entered yet", functionName[0]);
                functionName[0] = ctx.name();
            }, new API.OnConfig(false, false, true));

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
        }
    }

    @Test
    public void onEnterCallbackWithFilterOnRootName() throws Exception {
        try (Context c = Context.newBuilder().allowHostAccess(HostAccess.ALL).build()) {
            Value agent = AgentObjectFactory.createAgentObject(c);
            API agentAPI = agent.as(API.class);
            Assert.assertNotNull("Agent API obtained", agentAPI);

            String[] functionName = {null};
            agentAPI.on("enter", (ctx, frame) -> {
                assertNull("No function entered yet", functionName[0]);
                functionName[0] = ctx.name();
            }, new API.OnConfig(false, false, true, (name) -> "foo".equals(name)));

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
        }
    }

    @Test
    public void onStatementCallback() throws Exception {
        try (Context c = Context.newBuilder().allowHostAccess(HostAccess.ALL).build()) {
            Value agent = AgentObjectFactory.createAgentObject(c);
            API agentAPI = agent.as(API.class);
            Assert.assertNotNull("Agent API obtained", agentAPI);

            int[] loopIndexSum = {0};
            agentAPI.on("enter", (ctx, frame) -> {
                Object index = frame.get("loopIndex0");
                assertTrue("Number as expected: " + index, index instanceof Number);
                loopIndexSum[0] += ((Number) index).intValue();
            }, new API.OnConfig(false, true, false));

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
        try (Context c = Context.newBuilder().allowHostAccess(HostAccess.ALL).build()) {
            Value agent = AgentObjectFactory.createAgentObject(c);
            API agentAPI = agent.as(API.class);
            Assert.assertNotNull("Agent API obtained", agentAPI);

            int[] expressionCounter = {0};
            agentAPI.on("enter", (ev, frame) -> {
                expressionCounter[0]++;
            }, new API.OnConfig(true, false, false));

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
        }
    }
}
