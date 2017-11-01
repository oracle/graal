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
package com.oracle.truffle.api.debug.test;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

import com.oracle.truffle.api.debug.DebugScope;
import com.oracle.truffle.api.debug.DebugValue;
import com.oracle.truffle.api.debug.DebuggerSession;
import com.oracle.truffle.api.debug.SuspendedEvent;
import org.graalvm.polyglot.Source;

public class DebugScopeTest extends AbstractDebugTest {

    // Local scopes are well tested by DebugStackFrameTest and others.
    @Test
    public void testTopScope() {
        final Source source = testSource("ROOT(DEFINE(function1,ROOT(\n" +
                        "  EXPRESSION()\n" +
                        "  )\n" +
                        "),\n" +
                        "DEFINE(g,ROOT(\n" +
                        "  EXPRESSION()\n" +
                        "  )\n" +
                        "),\n" +
                        "STATEMENT())\n");
        try (DebuggerSession session = startSession()) {
            session.suspendNextExecution();
            startEval(source);

            expectSuspended((SuspendedEvent event) -> {
                DebugScope topScope = session.getTopScope(event.getSourceSection().getSource().getLanguage());
                assertNotNull(topScope);
                DebugValue function1 = topScope.getDeclaredValue("function1");
                assertNotNull(function1);
                assertTrue(function1.as(String.class).contains("Function"));
                DebugValue g = topScope.getDeclaredValue("g");
                assertNotNull(g);
                assertTrue(g.as(String.class).contains("Function"));
            });

            expectDone();
        }
    }
}
