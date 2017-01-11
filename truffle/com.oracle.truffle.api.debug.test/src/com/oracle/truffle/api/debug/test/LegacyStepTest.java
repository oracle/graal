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

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

import com.oracle.truffle.api.source.Source;

@SuppressWarnings("deprecation")
public class LegacyStepTest extends LegacyAbstractDebugTest {

    /*
     * TODO remove class with deprecated API.
     */

    @Test
    public void testBlock() throws Throwable {
        final Source block = LegacyTestSource.createBlock8("testBlock");
        expectExecutionEvent().resume();
        getEngine().eval(block);
        assertExecutedOK();
    }

    @Test
    public void testBlockStepIntoOver() throws Throwable {
        final Source block = LegacyTestSource.createBlock8("testBlockStepIntoOver");
        expectExecutionEvent().stepInto();
        expectSuspendedEvent().checkState(2, true, "STATEMENT").stepInto(1);
        expectSuspendedEvent().checkState(3, true, "STATEMENT").stepInto(2);
        expectSuspendedEvent().checkState(5, true, "STATEMENT").stepOver(1);
        expectSuspendedEvent().checkState(6, true, "STATEMENT").stepOver(3);
        expectSuspendedEvent().checkState(9, true, "STATEMENT").resume();
        getEngine().eval(block);
        assertExecutedOK();
    }

    @Test
    public void testBlockStepIntoBadArg() throws Throwable {
        final Source block = LegacyTestSource.createBlock8("testBlockStepIntoBadArg");
        expectExecutionEvent().stepInto();
        expectSuspendedEvent().checkState(2, true, "STATEMENT").stepInto(0);
        getEngine().eval(block);
        try {
            assertExecutedOK();
            fail("bad argument should cause exception");
        } catch (AssertionError e) {
            assertTrue(e.getCause() instanceof IllegalArgumentException);
        }
    }

    @Test
    public void testBlockStepOverBadArg() throws Throwable {
        final Source block = LegacyTestSource.createBlock8("testBlockStepOverBadArg");
        expectExecutionEvent().stepInto();
        expectSuspendedEvent().checkState(2, true, "STATEMENT").stepOver(0);
        getEngine().eval(block);
        try {
            assertExecutedOK();
        } catch (AssertionError e) {
            assertTrue(e.getCause() instanceof IllegalArgumentException);
        }
    }

    @Test
    public void testCallLoop() throws Throwable {
        final Source loop = LegacyTestSource.createCallLoop3("testCallLoop");
        expectExecutionEvent().resume();
        getEngine().eval(loop);
        assertExecutedOK();
    }

    @Test
    public void testCallLoopStepInto() throws Throwable {
        final Source loop = LegacyTestSource.createCallLoop3("testCallLoopStepInto");
        expectExecutionEvent().stepInto();                        // FIXME should stop at "CALL"?
        expectSuspendedEvent().checkState(4, true, "STATEMENT").stepInto(1);
        expectSuspendedEvent().checkState(4, true, "STATEMENT").stepInto(1);
        expectSuspendedEvent().checkState(4, true, "STATEMENT").stepInto(1);
        expectSuspendedEvent().checkState(6, false, "CALL(foo)").resume();
        getEngine().eval(loop);
        assertExecutedOK();
    }

    @Test
    public void testCallLoopStepOut() throws Throwable {
        final Source loop = LegacyTestSource.createCallLoop3("testCallLoopStepInto");
        expectExecutionEvent().stepInto();
        expectSuspendedEvent().checkState(4, true, "STATEMENT").stepOut();
        expectSuspendedEvent().checkState(6, false, "CALL(foo)").resume();
        getEngine().eval(loop);
        assertExecutedOK();
    }

}
