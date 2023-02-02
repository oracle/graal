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

import com.oracle.truffle.api.instrumentation.test.InstrumentationTestLanguage;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.junit.Assert;
import static org.junit.Assert.assertEquals;
import org.junit.Before;
import org.junit.Test;

public class DoubleEnterTest {
    @Before
    public void cleanAgentObject() {
        InsightObjectFactory.cleanAgentObject();
    }

    @Test
    public void onEnterFooBoo() throws Exception {
        try (Context c = InsightObjectFactory.newContext()) {
            Value agent = InsightObjectFactory.readInsight(c, null);
            InsightAPI agentAPI = agent.as(InsightAPI.class);
            Assert.assertNotNull("Agent API obtained", agentAPI);

            List<String> fnNames = new ArrayList<>();
            agentAPI.on("enter", (ctx, frame) -> {
                fnNames.add("1st@" + ctx.source().name() + ":" + ctx.name());
            }, new ConfigWithPredicate(
                            "boofoo.px", (n) -> n.startsWith("foo")));
            agentAPI.on("enter", (ctx, frame) -> {
                fnNames.add("2nd@" + ctx.source().name() + ":" + ctx.name());
            }, new ConfigWithPredicate(
                            "fooboo.px", (n) -> n.startsWith("boo")));

            // @formatter:off
            Source fooboo = Source.newBuilder(InstrumentationTestLanguage.ID,
                "ROOT(\n" +
                "  DEFINE(foo,\n" +
                "    LOOP(10, STATEMENT(EXPRESSION,EXPRESSION))\n" +
                "  ),\n" +
                "  DEFINE(boo,\n" +
                "    LOOP(10, STATEMENT(EXPRESSION,EXPRESSION))\n" +
                "  ),\n" +
                "  CALL(foo),\n" +
                "  CALL(boo)\n" +
                ")",
                "fooboo.px"
            ).build();
            Source boofoo = Source.newBuilder(InstrumentationTestLanguage.ID,
                "ROOT(\n" +
                "  DEFINE(foo2,\n" +
                "    LOOP(10, STATEMENT(EXPRESSION,EXPRESSION))\n" +
                "  ),\n" +
                "  DEFINE(boo2,\n" +
                "    LOOP(10, STATEMENT(EXPRESSION,EXPRESSION))\n" +
                "  ),\n" +
                "  CALL(boo2),\n" +
                "  CALL(foo2)\n" +
                ")",
                "boofoo.px"
            ).build();
            // @formatter:on
            c.eval(fooboo);
            c.eval(boofoo);

            assertEquals("Four elements " + fnNames, 2, fnNames.size());
            assertEquals("2nd@fooboo.px:boo", fnNames.get(0));
            assertEquals("1st@boofoo.px:foo2", fnNames.get(1));
        }
    }

    public final class ConfigWithPredicate extends InsightAPI.OnConfig {
        public Predicate<String> rootNameFilter;

        @SuppressWarnings("unchecked")
        public ConfigWithPredicate(String sourceName, Predicate<String> fn) {
            this.rootNameFilter = fn;
            this.expressions = false;
            this.roots = true;
            this.statements = false;
            this.sourceFilter = new InsightObjectTest.SourceNameCheck(sourceName);
        }
    }
}
