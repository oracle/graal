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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;

import org.junit.Test;

import com.oracle.truffle.api.debug.Breakpoint;
import com.oracle.truffle.api.debug.Debugger;
import com.oracle.truffle.api.source.Source;
import java.io.File;

@SuppressWarnings("deprecation")
public class LegacyBreakpointTest extends LegacyAbstractDebugTest {

    /*
     * TODO remove class with deprecated API.
     */

    @Test
    public void testBreak() throws Throwable {
        testBreak(false);
        testBreak(true);
    }

    private void testBreak(boolean onUri) throws Throwable {
        final Source block = LegacyTestSource.createBlock12("testBreak" + (onUri ? "Uri" : ""));
        final Breakpoint[] breakpoints = new Breakpoint[block.getLineCount() + 1];
        final Debugger debugger = getDebugger();
        if (onUri) {
            breakpoints[4] = debugger.setLineBreakpoint(0, block.getURI(), 4, false);
        } else {
            breakpoints[4] = debugger.setLineBreakpoint(0, block.createLineLocation(4), false);
        }
        expectExecutionEvent().resume();
        expectSuspendedEvent().checkState(4, true, "STATEMENT").resume();
        getEngine().eval(block);
        assertExecutedOK();
        assertTrue(breakpoints[4].isEnabled());
        assertEquals(breakpoints[4].getHitCount(), 1);
        breakpoints[4].dispose();
    }

    @Test
    public void testBreak2() throws Throwable {
        final Source test2 = LegacyTestSource.createCall("testBreak");
        final Breakpoint[] breakpoints = new Breakpoint[test2.getLineCount() + 1];
        final Debugger debugger = getDebugger();
        expectExecutionEvent().stepInto();
        expectSuspendedEvent().checkState(5, true, "STATEMENT").run(new Runnable() {

            public void run() {
                try {
                    breakpoints[3] = debugger.setLineBreakpoint(0, test2.createLineLocation(3), false);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }).resume();
        expectSuspendedEvent().checkState(3, true, "STATEMENT").resume();
        getEngine().eval(test2);
        assertExecutedOK();
    }

    @Test
    public void testBreakOneShot() throws Throwable {
        testBreakOneShot(false);
        testBreakOneShot(true);
    }

    public void testBreakOneShot(final boolean onUri) throws Throwable {
        final Source block = LegacyTestSource.createBlock12("testBreakOneShot" + (onUri ? "Uri" : ""));
        final Breakpoint[] breakpoints = new Breakpoint[block.getLineCount() + 1];
        final Debugger debugger = getDebugger();
        if (onUri) {
            breakpoints[4] = debugger.setLineBreakpoint(0, block.getURI(), 4, true);
        } else {
            breakpoints[4] = debugger.setLineBreakpoint(0, block.createLineLocation(4), true);
        }
        expectExecutionEvent().resume();
        expectSuspendedEvent().checkState(4, true, "STATEMENT").run(new Runnable() {
            public void run() {
                try {
                    if (onUri) {
                        breakpoints[6] = debugger.setLineBreakpoint(0, block.getURI(), 6, false);
                    } else {
                        breakpoints[6] = debugger.setLineBreakpoint(0, block.createLineLocation(6), false);
                    }
                } catch (IOException e) {
                    fail("breakpoint");
                }
            }
        }).resume();
        expectSuspendedEvent().checkState(6, true, "STATEMENT").resume();
        getEngine().eval(block);
        assertExecutedOK();
        assertFalse(breakpoints[4].isEnabled());
        assertEquals(breakpoints[4].getHitCount(), 1);
        assertTrue(breakpoints[6].isEnabled());
        assertEquals(breakpoints[6].getHitCount(), 1);
        breakpoints[4].dispose();
        breakpoints[6].dispose();
    }

    @Test
    public void testBreakDisableDispose() throws Throwable {
        final Source block = LegacyTestSource.createBlock12("testBreakDisableDispose");
        final Breakpoint[] breakpoints = new Breakpoint[block.getLineCount() + 1];
        final Debugger debugger = getDebugger();
        breakpoints[4] = debugger.setLineBreakpoint(0, block.createLineLocation(4), false);
        breakpoints[6] = debugger.setLineBreakpoint(0, block.createLineLocation(6), false);
        breakpoints[6].dispose();
        breakpoints[8] = debugger.setLineBreakpoint(0, block.createLineLocation(8), false);
        breakpoints[8].setEnabled(false);
        breakpoints[10] = debugger.setLineBreakpoint(0, block.createLineLocation(10), false);
        breakpoints[10].setEnabled(false);
        breakpoints[10].setEnabled(true);
        expectExecutionEvent().stepInto();
        expectSuspendedEvent().checkState(2, true, "STATEMENT").resume();
        expectSuspendedEvent().checkState(4, true, "STATEMENT").resume();
        expectSuspendedEvent().checkState(10, true, "STATEMENT").resume();
        getEngine().eval(block);
        assertExecutedOK();
        assertTrue(breakpoints[4].isEnabled());
        assertEquals(breakpoints[4].getHitCount(), 1);
        assertFalse(breakpoints[6].isEnabled());
        assertEquals(breakpoints[6].getHitCount(), 0);
        assertFalse(breakpoints[8].isEnabled());
        assertEquals(breakpoints[8].getHitCount(), 0);
    }

    @Test
    public void testURIBreak() throws Throwable {
        File loopFile = LegacyTestSource.createCallLoop3File();
        final Debugger debugger = getDebugger();
        Breakpoint breakpoint = debugger.setLineBreakpoint(0, loopFile.toURI(), 4, false);
        expectExecutionEvent().resume();
        for (int i = 0; i < 3; i++) {
            expectSuspendedEvent().checkState(4, true, "STATEMENT").resume();
        }
        getEngine().eval(Source.newBuilder(new File(loopFile.getAbsolutePath())).build());
        assertExecutedOK();
        assertTrue(breakpoint.isEnabled());
        assertEquals(breakpoint.getHitCount(), 3);
        breakpoint.dispose();
    }
}
