/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import org.junit.After;
import org.junit.Test;

import com.oracle.truffle.tools.utils.json.JSONObject;

import org.graalvm.polyglot.Source;

public class SLInspectProfileTest {

    // @formatter:off   The default formatting makes unnecessarily big indents and illogical line breaks
    // CheckStyle: stop line length check
    private static final String CODE1 = "function main() {\n" +
            " a = 10;\n" +
            " b = factorial(a/2) / 60;\n" +
            " while (b > 0) {\n" +
            "  c = a + b;\n" +
            "  b = b - c/10;\n" +
            " }\n" +
            " return b;\n" +
            "}\n" +
            "function factorial(n) {\n" +
            " f = 1;\n" +
            " i = 2;\n" +
            " while (i <= n) {\n" +
            "  f2 = f * i;\n" +
            "  i = i + 1;\n" +
            "  f = f2;\n" +
            " }\n" +
            " return f;\n" +
            "}";

    private static final String CODE2 = "function add(x, y) {\n" +
            " return x + y;\n" +
            "}\n" +
            "function main() {\n" +
            " add(1, 2);\n" +
            " add(1, \"some string\");\n" +
            "}";

    private InspectorTester tester;

    @After
    public void tearDown() {
        tester = null;
    }

    @Test
    public void testCPUProfiler() throws Exception {
        tester = InspectorTester.start(false);
        Source source = Source.newBuilder("sl", CODE1, "SLTest.sl").build();
        tester.sendMessage("{\"id\":1,\"method\":\"Runtime.enable\"}");
        assertEquals("{\"result\":{},\"id\":1}", tester.getMessages(true).trim());
        tester.sendMessage("{\"id\":2,\"method\":\"Profiler.enable\"}");
        assertEquals("{\"result\":{},\"id\":2}", tester.getMessages(true).trim());
        tester.sendMessage("{\"id\":3,\"method\":\"Profiler.setSamplingInterval\",\"params\":{\"interval\":1000}}");
        assertEquals("{\"result\":{},\"id\":3}", tester.getMessages(true).trim());
        assertFalse(tester.shouldWaitForClose());
        tester.sendMessage("{\"id\":4,\"method\":\"Profiler.start\"}");
        assertEquals("{\"result\":{},\"id\":4}", tester.getMessages(true).trim());
        assertTrue(tester.shouldWaitForClose());
        tester.eval(source).get();
        tester.sendMessage("{\"id\":5,\"method\":\"Profiler.stop\"}");
        JSONObject json = new JSONObject(tester.getMessages(true).trim());
        assertNotNull(json);
        assertEquals(json.getInt("id"), 5);
        JSONObject jsonResult = json.getJSONObject("result");
        assertNotNull(jsonResult);
        JSONObject jsonProfile = jsonResult.getJSONObject("profile");
        assertNotNull(jsonProfile);
        tester.sendMessage("{\"id\":6,\"method\":\"Profiler.disable\"}");
        assertEquals("{\"result\":{},\"id\":6}", tester.getMessages(true).trim());
        assertTrue(tester.shouldWaitForClose());
        tester.finish();
    }

    @Test
    public void testCodeCoverage() throws Exception {
        tester = InspectorTester.start(false);
        Source source = Source.newBuilder("sl", CODE2, "SLTest.sl").build();
        String slTestURI = InspectorTester.getStringURI(source.getURI());
        tester.sendMessage("{\"id\":1,\"method\":\"Runtime.enable\"}");
        assertEquals("{\"result\":{},\"id\":1}", tester.getMessages(true).trim());
        tester.sendMessage("{\"id\":2,\"method\":\"Profiler.enable\"}");
        assertEquals("{\"result\":{},\"id\":2}", tester.getMessages(true).trim());
        assertFalse(tester.shouldWaitForClose());
        tester.sendMessage("{\"id\":3,\"method\":\"Profiler.startPreciseCoverage\"}");
        assertEquals("{\"result\":{},\"id\":3}", tester.getMessages(true).trim());
        assertTrue(tester.shouldWaitForClose());
        tester.sendMessage("{\"id\":4,\"method\":\"Profiler.takePreciseCoverage\"}");
        assertEquals("{\"result\":{\"result\":[]},\"id\":4}", tester.getMessages(true).trim());
        tester.eval(source).get();
        tester.sendMessage("{\"id\":5,\"method\":\"Profiler.takePreciseCoverage\"}");
        assertEquals("{\"result\":{\"result\":[{\"scriptId\":\"1\",\"functions\":["
                + "{\"ranges\":[{\"endOffset\":37,\"startOffset\":9,\"count\":2}],\"functionName\":\"add\",\"isBlockCoverage\":false},"
                + "{\"ranges\":[{\"endOffset\":93,\"startOffset\":47,\"count\":1}],\"functionName\":\"main\",\"isBlockCoverage\":false}],"
                + "\"url\":\"" + slTestURI + "\"}]},\"id\":5}", tester.getMessages(true).trim());
        tester.sendMessage("{\"id\":6,\"method\":\"Profiler.takePreciseCoverage\"}");
        assertEquals("{\"result\":{\"result\":[]},\"id\":6}", tester.getMessages(true).trim());
        tester.sendMessage("{\"id\":7,\"method\":\"Profiler.stopPreciseCoverage\"}");
        assertEquals("{\"result\":{},\"id\":7}", tester.getMessages(true).trim());
        tester.sendMessage("{\"id\":8,\"method\":\"Profiler.disable\"}");
        assertEquals("{\"result\":{},\"id\":8}", tester.getMessages(true).trim());
        assertTrue(tester.shouldWaitForClose());
        tester.finish();
    }

    @Test
    public void testDetailedCodeCoverage() throws Exception {
        tester = InspectorTester.start(false);
        Source source = Source.newBuilder("sl", CODE2, "SLTest.sl").build();
        String slTestURI = InspectorTester.getStringURI(source.getURI());
        tester.sendMessage("{\"id\":1,\"method\":\"Runtime.enable\"}");
        assertEquals("{\"result\":{},\"id\":1}", tester.getMessages(true).trim());
        tester.sendMessage("{\"id\":2,\"method\":\"Profiler.enable\"}");
        assertEquals("{\"result\":{},\"id\":2}", tester.getMessages(true).trim());
        assertFalse(tester.shouldWaitForClose());
        tester.sendMessage("{\"id\":3,\"method\":\"Profiler.startPreciseCoverage\",\"params\":{\"detailed\":true}}");
        assertEquals("{\"result\":{},\"id\":3}", tester.getMessages(true).trim());
        assertTrue(tester.shouldWaitForClose());
        tester.sendMessage("{\"id\":4,\"method\":\"Profiler.takePreciseCoverage\"}");
        assertEquals("{\"result\":{\"result\":[]},\"id\":4}", tester.getMessages(true).trim());
        tester.eval(source).get();
        tester.sendMessage("{\"id\":5,\"method\":\"Profiler.takePreciseCoverage\"}");
        assertEquals("{\"result\":{\"result\":[{\"scriptId\":\"1\",\"functions\":["
                + "{\"ranges\":[{\"endOffset\":34,\"startOffset\":22,\"count\":2}],\"functionName\":\"add\",\"isBlockCoverage\":true},"
                + "{\"ranges\":[{\"endOffset\":66,\"startOffset\":57,\"count\":1},{\"endOffset\":90,\"startOffset\":69,\"count\":1}],\"functionName\":\"main\",\"isBlockCoverage\":true}],"
                + "\"url\":\"" + slTestURI + "\"}]},\"id\":5}", tester.getMessages(true).trim());
        tester.sendMessage("{\"id\":6,\"method\":\"Profiler.takePreciseCoverage\"}");
        assertEquals("{\"result\":{\"result\":[]},\"id\":6}", tester.getMessages(true).trim());
        tester.sendMessage("{\"id\":7,\"method\":\"Profiler.stopPreciseCoverage\"}");
        assertEquals("{\"result\":{},\"id\":7}", tester.getMessages(true).trim());
        tester.sendMessage("{\"id\":8,\"method\":\"Profiler.disable\"}");
        assertEquals("{\"result\":{},\"id\":8}", tester.getMessages(true).trim());
        assertTrue(tester.shouldWaitForClose());
        tester.finish();
    }

    @Test
    public void testTypeProfile() throws Exception {
        tester = InspectorTester.start(false);
        Source source = Source.newBuilder("sl", CODE2, "SLTest.sl").build();
        String slTestURI = InspectorTester.getStringURI(source.getURI());
        tester.sendMessage("{\"id\":1,\"method\":\"Runtime.enable\"}");
        assertEquals("{\"result\":{},\"id\":1}", tester.getMessages(true).trim());
        tester.sendMessage("{\"id\":2,\"method\":\"Profiler.enable\"}");
        assertEquals("{\"result\":{},\"id\":2}", tester.getMessages(true).trim());
        assertFalse(tester.shouldWaitForClose());
        tester.sendMessage("{\"id\":3,\"method\":\"Profiler.startTypeProfile\"}");
        assertEquals("{\"result\":{},\"id\":3}", tester.getMessages(true).trim());
        assertTrue(tester.shouldWaitForClose());
        tester.sendMessage("{\"id\":4,\"method\":\"Profiler.takeTypeProfile\"}");
        assertEquals("{\"result\":{\"result\":[]},\"id\":4}", tester.getMessages(true).trim());
        tester.eval(source).get();
        tester.sendMessage("{\"id\":5,\"method\":\"Profiler.takeTypeProfile\"}");
        assertEquals("{\"result\":{\"result\":[{\"scriptId\":\"1\",\"entries\":["
                + "{\"types\":[{\"name\":\"Number\"}],\"offset\":14},"
                + "{\"types\":[{\"name\":\"Number\"},{\"name\":\"String\"}],\"offset\":17},"
                + "{\"types\":[{\"name\":\"Number\"},{\"name\":\"String\"}],\"offset\":37},"
                + "{\"types\":[{\"name\":\"NULL\"}],\"offset\":93}],"
                + "\"url\":\"" + slTestURI + "\"}]},\"id\":5}", tester.getMessages(true).trim());
        tester.sendMessage("{\"id\":6,\"method\":\"Profiler.takeTypeProfile\"}");
        assertEquals("{\"result\":{\"result\":[]},\"id\":6}", tester.getMessages(true).trim());
        tester.sendMessage("{\"id\":7,\"method\":\"Profiler.stopTypeProfile\"}");
        assertEquals("{\"result\":{},\"id\":7}", tester.getMessages(true).trim());
        tester.sendMessage("{\"id\":8,\"method\":\"Profiler.disable\"}");
        assertEquals("{\"result\":{},\"id\":8}", tester.getMessages(true).trim());
        assertTrue(tester.shouldWaitForClose());
        tester.finish();
    }
    // @formatter:on
    // CheckStyle: resume line length check
}
