/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Map;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

import com.oracle.truffle.api.test.ReflectionUtils;
import com.oracle.truffle.tools.chromeinspector.RemoteObjectsHandler;
import org.graalvm.polyglot.Source;

/**
 * Test of memory leaks during debugging.
 */
public class TestMemoryLeaks {

    private static final String[] REMOTE_OBJECTS_MAP_NAMES = {"remotesByIDs", "remotesByValue", "customPreviewBodies", "customPreviewConfigs"};

    @Test
    public void testRemoteObjectsLeak() throws Exception {
        InspectorTester tester = InspectorTester.start(false);
        RemoteObjectsHandler remoteObjectsHandler = tester.getInspectorContext().getRemoteObjectsHandler();
        assertEmptyRemoteObjectsMaps(remoteObjectsHandler);

        Source source = Source.newBuilder("sl", "function main() {\n" +
                        "  func1();\n" +
                        "  func2(new());\n" +
                        "  func3();\n" +
                        "}\n" +
                        "function func1() {\n" +
                        "  a = 0;\n" +
                        "  b = new();\n" +
                        "  debugger;\n" +
                        "}\n" +
                        "function func2(arg) {\n" +
                        "  c = new();\n" +
                        "  d = 3;\n" +
                        "  debugger;\n" +
                        "}\n" +
                        "function func3() {\n" +
                        "  debugger;\n" +
                        "}\n", "code").build();
        String slTestURI = InspectorTester.getStringURI(source.getURI());
        tester.sendMessage("{\"id\":1,\"method\":\"Runtime.enable\"}");
        tester.sendMessage("{\"id\":2,\"method\":\"Debugger.enable\"}");
        tester.sendMessage("{\"id\":3,\"method\":\"Runtime.runIfWaitingForDebugger\"}");
        assertTrue(tester.compareReceivedMessages("" +
                        "{\"result\":{},\"id\":1}\n" +
                        "{\"result\":{},\"id\":2}\n" +
                        "{\"result\":{},\"id\":3}\n" +
                        "{\"method\":\"Runtime.executionContextCreated\",\"params\":{\"context\":{\"origin\":\"\",\"name\":\"test\",\"id\":1}}}\n"));
        tester.eval(source);

        // Suspend in func1:
        tester.receiveMessages(
                        "{\"method\":\"Debugger.scriptParsed\"",
                        "{\"method\":\"Debugger.paused\"",
                        "\"url\":\"" + slTestURI + "\"}]}}\n");
        tester.sendMessage("{\"id\":5,\"method\":\"Runtime.getProperties\",\"params\":{\"objectId\":\"1\"}}");
        tester.receiveMessages(
                        "{\"result\":",
                        "\"name\":\"a\"",
                        "\"name\":\"b\"",
                        "\"id\":5}\n");
        assertRemoteObjectsMapsSize(remoteObjectsHandler, 5); // global, func1, func2, func3, b

        tester.sendMessage("{\"id\":10,\"method\":\"Debugger.resume\"}");
        assertTrue(tester.compareReceivedMessages("" +
                        "{\"result\":{},\"id\":10}\n" +
                        "{\"method\":\"Debugger.resumed\"}\n"));

        // Suspend in func2:
        tester.receiveMessages(
                        "{\"method\":\"Debugger.paused\"",
                        "\"url\":\"" + slTestURI + "\"}]}}\n");
        tester.sendMessage("{\"id\":15,\"method\":\"Runtime.getProperties\",\"params\":{\"objectId\":\"8\"}}");
        tester.receiveMessages(
                        "{\"result\":",
                        "\"name\":\"arg\"",
                        "\"name\":\"c\"",
                        "\"name\":\"d\"",
                        "\"id\":15}\n");
        assertRemoteObjectsMapsSize(remoteObjectsHandler, 6); // global, func1, func2, func3, arg, c

        tester.sendMessage("{\"id\":20,\"method\":\"Debugger.resume\"}");
        assertTrue(tester.compareReceivedMessages("" +
                        "{\"result\":{},\"id\":20}\n" +
                        "{\"method\":\"Debugger.resumed\"}\n"));

        // Suspend in func3:
        tester.receiveMessages(
                        "{\"method\":\"Debugger.paused\"",
                        "\"url\":\"" + slTestURI + "\"}]}}\n");
        tester.sendMessage("{\"id\":25,\"method\":\"Runtime.getProperties\",\"params\":{\"objectId\":\"16\"}}");
        assertTrue(tester.compareReceivedMessages(
                        "{\"result\":{\"result\":[],\"internalProperties\":[]},\"id\":25}\n"));
        assertRemoteObjectsMapsSize(remoteObjectsHandler, 4); // global, func1, func2, func3

        // Finish
        tester.sendMessage("{\"id\":30,\"method\":\"Debugger.resume\"}");
        assertTrue(tester.compareReceivedMessages("" +
                        "{\"result\":{},\"id\":30}\n" +
                        "{\"method\":\"Debugger.resumed\"}\n"));
        tester.finishNoGC();
        assertEmptyRemoteObjectsMaps(remoteObjectsHandler);
        remoteObjectsHandler = null;
        tester.finish();
    }

    private static void assertEmptyRemoteObjectsMaps(RemoteObjectsHandler remoteObjectsHandler) {
        for (String mapName : REMOTE_OBJECTS_MAP_NAMES) {
            Map<?, ?> map = (Map<?, ?>) ReflectionUtils.getField(remoteObjectsHandler, mapName);
            assertEquals("Map " + mapName, 0, map.size());
        }
    }

    private static void assertRemoteObjectsMapsSize(RemoteObjectsHandler remoteObjectsHandler, int size) {
        for (int i = 0; i < 1; i++) {
            String mapName = REMOTE_OBJECTS_MAP_NAMES[i];
            Map<?, ?> map = (Map<?, ?>) ReflectionUtils.getField(remoteObjectsHandler, mapName);
            assertEquals("Map " + mapName, size, map.size());
        }
    }
}
