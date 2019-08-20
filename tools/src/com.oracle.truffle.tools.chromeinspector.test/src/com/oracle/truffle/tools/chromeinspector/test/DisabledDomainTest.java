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
package com.oracle.truffle.tools.chromeinspector.test;

import static org.junit.Assert.assertEquals;
import org.junit.Test;

/**
 * Test that no methods are called on disabled domains.
 */
public class DisabledDomainTest {

    // @formatter:off   The default formatting makes unnecessarily big indents and illogical line breaks
    // CheckStyle: stop line length check
    @Test
    public void testDisabledDomainsCalls() throws Exception {
        InspectorTester tester = InspectorTester.start(true, false, false);
        String runtimeMessage = "\"method\":\"Runtime.setCustomObjectFormatterEnabled\",\"params\":{\"enabled\":\"true\"}}";
        tester.sendMessage("{\"id\":1," + runtimeMessage);
        assertEquals("{\"id\":1,\"error\":{\"code\":-32601,\"message\":\"Domain Runtime is disabled.\"}}", tester.getMessages(true).trim());
        tester.sendMessage("{\"id\":2,\"method\":\"Runtime.enable\"}");
        assertEquals("{\"result\":{},\"id\":2}", tester.getMessages(true).trim());
        tester.sendMessage("{\"id\":3," + runtimeMessage);
        assertEquals("{\"result\":{},\"id\":3}", tester.getMessages(true).trim());
        tester.sendMessage("{\"id\":4,\"method\":\"Runtime.disable\"}");
        assertEquals("{\"result\":{},\"id\":4}", tester.getMessages(true).trim());
        tester.sendMessage("{\"id\":5," + runtimeMessage);
        assertEquals("{\"id\":5,\"error\":{\"code\":-32601,\"message\":\"Domain Runtime is disabled.\"}}", tester.getMessages(true).trim());

        String debuggerMessage = "\"method\":\"Debugger.setBreakpointByUrl\",\"params\":{\"url\":\"TestURL\",\"lineNumber\":1}}";
        tester.sendMessage("{\"id\":10," + debuggerMessage);
        assertEquals("{\"id\":10,\"error\":{\"code\":-32601,\"message\":\"Domain Debugger is disabled.\"}}", tester.getMessages(true).trim());
        tester.sendMessage("{\"id\":11,\"method\":\"Debugger.enable\"}");
        assertEquals("{\"result\":{},\"id\":11}", tester.getMessages(true).trim());
        tester.sendMessage("{\"id\":12," + debuggerMessage);
        assertEquals("{\"result\":{\"breakpointId\":\"1\",\"locations\":[]},\"id\":12}", tester.getMessages(true).trim());
        tester.sendMessage("{\"id\":13,\"method\":\"Debugger.disable\"}");
        assertEquals("{\"result\":{},\"id\":13}", tester.getMessages(true).trim());
        tester.sendMessage("{\"id\":14," + debuggerMessage);
        assertEquals("{\"id\":14,\"error\":{\"code\":-32601,\"message\":\"Domain Debugger is disabled.\"}}", tester.getMessages(true).trim());

        String profilerMessage = "\"method\":\"Profiler.setSamplingInterval\",\"params\":{\"interval\":10}}";
        tester.sendMessage("{\"id\":20," + profilerMessage);
        assertEquals("{\"id\":20,\"error\":{\"code\":-32601,\"message\":\"Domain Profiler is disabled.\"}}", tester.getMessages(true).trim());
        tester.sendMessage("{\"id\":21,\"method\":\"Profiler.enable\"}");
        assertEquals("{\"result\":{},\"id\":21}", tester.getMessages(true).trim());
        tester.sendMessage("{\"id\":22," + profilerMessage);
        assertEquals("{\"result\":{},\"id\":22}", tester.getMessages(true).trim());
        tester.sendMessage("{\"id\":23,\"method\":\"Profiler.disable\"}");
        assertEquals("{\"result\":{},\"id\":23}", tester.getMessages(true).trim());
        tester.sendMessage("{\"id\":24," + profilerMessage);
        assertEquals("{\"id\":24,\"error\":{\"code\":-32601,\"message\":\"Domain Profiler is disabled.\"}}", tester.getMessages(true).trim());
        tester.finish();
    }
    // @formatter:on
    // CheckStyle: resume line length check
}
