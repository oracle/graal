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

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import com.oracle.truffle.api.debug.Breakpoint;
import com.oracle.truffle.api.debug.Debugger;
import com.oracle.truffle.api.source.Source;

@SuppressWarnings("deprecation")
public class LegacyDoubleHaltTest extends LegacyAbstractDebugTest {

    /*
     * TODO remove class with deprecated API.
     */

    @Test
    public void testBlockStepInto() throws Throwable {
        final Debugger debugger = getDebugger();
        final Source block = LegacyTestSource.createBlock8("testBlockStepInto");
        final Breakpoint[] shouldBreak = new Breakpoint[block.getLineCount() + 1];
        shouldBreak[2] = debugger.setLineBreakpoint(0, block.createLineLocation(2), false);
        shouldBreak[3] = debugger.setLineBreakpoint(0, block.createLineLocation(3), false);
        shouldBreak[5] = debugger.setLineBreakpoint(0, block.createLineLocation(5), false);
        shouldBreak[6] = debugger.setLineBreakpoint(0, block.createLineLocation(6), false);
        expectExecutionEvent().stepInto();
        expectSuspendedEvent().checkState(2, true, "STATEMENT").stepInto(1);
        expectSuspendedEvent().checkState(3, true, "STATEMENT").stepInto(2);
        expectSuspendedEvent().checkState(5, true, "STATEMENT").stepInto(2);
        expectSuspendedEvent().checkState(6, true, "STATEMENT").resume();
        getEngine().eval(block);
        assertExecutedOK();
        for (Breakpoint breakpoint : shouldBreak) {
            if (breakpoint != null) {
                assertEquals(breakpoint.getHitCount(), 1);
                breakpoint.dispose();
            }
        }
    }

    @Test
    public void testBlockStepOver() throws Throwable {
        final Debugger debugger = getDebugger();
        final Source block = LegacyTestSource.createBlock8("testBlockStepOver");
        final Breakpoint[] shouldBreak = new Breakpoint[block.getLineCount() + 1];
        shouldBreak[2] = debugger.setLineBreakpoint(0, block.createLineLocation(2), false);
        shouldBreak[3] = debugger.setLineBreakpoint(0, block.createLineLocation(3), false);
        shouldBreak[5] = debugger.setLineBreakpoint(0, block.createLineLocation(5), false);
        shouldBreak[6] = debugger.setLineBreakpoint(0, block.createLineLocation(6), false);
        expectExecutionEvent().stepInto();
        expectSuspendedEvent().checkState(2, true, "STATEMENT").stepOver(1);
        expectSuspendedEvent().checkState(3, true, "STATEMENT").stepOver(2);
        expectSuspendedEvent().checkState(5, true, "STATEMENT").stepOver(2);
        expectSuspendedEvent().checkState(6, true, "STATEMENT").resume();
        getEngine().eval(block);
        assertExecutedOK();
        for (Breakpoint breakpoint : shouldBreak) {
            if (breakpoint != null) {
                assertEquals(breakpoint.getHitCount(), 1);
                breakpoint.dispose();
            }
        }
    }

    @Test
    public void testCallLoopStepInto() throws Throwable {
        final Debugger debugger = getDebugger();
        final Source loop = LegacyTestSource.createCallLoop3("testCallLoopStepInto");
        final Breakpoint[] shouldBreak = new Breakpoint[loop.getLineCount() + 1];
        shouldBreak[4] = debugger.setLineBreakpoint(0, loop.createLineLocation(4), false);
        expectExecutionEvent().stepInto();                        // FIXME should stop at "CALL"?
        expectSuspendedEvent().checkState(4, true, "STATEMENT").stepInto(1);
        expectSuspendedEvent().checkState(4, true, "STATEMENT").stepInto(1);
        expectSuspendedEvent().checkState(4, true, "STATEMENT").stepInto(1);
        expectSuspendedEvent().checkState(6, false, "CALL(foo)").resume();
        getEngine().eval(loop);
        assertExecutedOK();
        for (Breakpoint breakpoint : shouldBreak) {
            if (breakpoint != null) {
                assertEquals(breakpoint.getHitCount(), 3);
                breakpoint.dispose();
            }
        }
    }

    @Test
    public void testCallLoopStepOver() throws Throwable {
        final Debugger debugger = getDebugger();
        final Source loop = LegacyTestSource.createCallLoop3("testCallLoopStepOver");
        final Breakpoint[] shouldBreak = new Breakpoint[loop.getLineCount() + 1];
        shouldBreak[4] = debugger.setLineBreakpoint(0, loop.createLineLocation(4), false);
        expectExecutionEvent().stepInto();                        // FIXME should stop at "CALL"?
        expectSuspendedEvent().checkState(4, true, "STATEMENT").stepOver(1);
        expectSuspendedEvent().checkState(4, true, "STATEMENT").stepOver(1);
        expectSuspendedEvent().checkState(4, true, "STATEMENT").stepOver(1);
        expectSuspendedEvent().checkState(6, false, "CALL(foo)").resume();
        getEngine().eval(loop);
        assertExecutedOK();
        for (Breakpoint breakpoint : shouldBreak) {
            if (breakpoint != null) {
                assertEquals(breakpoint.getHitCount(), 3);
                breakpoint.dispose();
            }
        }
    }

    @Test
    public void testCallLoopStepOut() throws Throwable {
        final Debugger debugger = getDebugger();
        final Source loop = LegacyTestSource.createCallLoop3("testCallLoopStepOut");
        final Breakpoint[] shouldBreak = new Breakpoint[loop.getLineCount() + 1];
        shouldBreak[4] = debugger.setLineBreakpoint(0, loop.createLineLocation(4), false);
        expectExecutionEvent().stepInto();
        expectSuspendedEvent().checkState(4, true, "STATEMENT").stepOut();
        /*
         * Note Chumer: breakpoints should always hit independent if we are currently stepping out
         * or not. thats why we need to repeat the step out command three times here.
         */
        expectSuspendedEvent().checkState(4, true, "STATEMENT").stepOut();
        expectSuspendedEvent().checkState(4, true, "STATEMENT").stepOut();

        expectSuspendedEvent().checkState(6, false, "CALL(foo)").resume();
        getEngine().eval(loop);
        assertExecutedOK();
        for (Breakpoint breakpoint : shouldBreak) {
            if (breakpoint != null) {
                assertEquals(breakpoint.getHitCount(), 3);
                breakpoint.dispose();
            }
        }
    }

}
