/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.truffle.api.instrumentation.InstrumentationTestLanguage.FILENAME_EXTENSION;
import static com.oracle.truffle.api.instrumentation.InstrumentationTestLanguage.MIME_TYPE;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.oracle.truffle.api.source.Source;

public class StepDebugTest extends AbstractDebugTest {

    private static Source createStatements(String sourceName) {
        return Source.fromText("ROOT(\n" +
                        "  STATEMENT,\n" +
                        "  STATEMENT,\n" +
                        "  STATEMENT,\n" +
                        "  STATEMENT,\n" +
                        "  STATEMENT,\n" +
                        "  STATEMENT,\n" +
                        "  STATEMENT\n" +
                        ")\n",
                        sourceName + FILENAME_EXTENSION).withMimeType(MIME_TYPE);
    }

    @Test
    public void testNoDebug() throws Throwable {
        final Source statements = createStatements("testNoDebug");
        expectExecutionEvent().resume();
        engine.eval(statements);
        assertExecutedOK();
    }

    @Test
    public void testStepIntoOver() throws Throwable {
        final Source statements = createStatements("testStepIntoOver");
        expectExecutionEvent().stepInto();
        expectSuspendedEvent().checkState(2, true, "STATEMENT").stepInto(1);
        expectSuspendedEvent().checkState(3, true, "STATEMENT").stepInto(2);
        expectSuspendedEvent().checkState(5, true, "STATEMENT").stepOver(1);
        expectSuspendedEvent().checkState(6, true, "STATEMENT").stepOver(2);
        expectSuspendedEvent().checkState(8, true, "STATEMENT\n").resume(); // FIXME
        engine.eval(statements);
        assertExecutedOK();
    }

    @Test
    public void testStepIntoBadArg() throws Throwable {
        final Source statements = createStatements("testStepIntoBadArg");
        expectExecutionEvent().stepInto();
        expectSuspendedEvent().checkState(2, true, "STATEMENT").stepInto(0);
        engine.eval(statements);
        try {
            assertExecutedOK();
        } catch (AssertionError e) {
            assertTrue(e.getCause() instanceof IllegalArgumentException);
        }
    }

    @Test
    public void testStepOverBadArg() throws Throwable {
        final Source statements = createStatements("testStepOverBadArg");
        expectExecutionEvent().stepInto();
        expectSuspendedEvent().checkState(2, true, "STATEMENT").stepOver(0);
        engine.eval(statements);
        try {
            assertExecutedOK();
        } catch (AssertionError e) {
            assertTrue(e.getCause() instanceof IllegalArgumentException);
        }
    }

}
