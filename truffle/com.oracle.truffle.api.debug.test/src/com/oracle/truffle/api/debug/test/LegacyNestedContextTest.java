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
import static org.junit.Assert.fail;

import org.junit.Test;

import com.oracle.truffle.api.debug.Breakpoint;
import com.oracle.truffle.api.debug.Debugger;
import com.oracle.truffle.api.source.Source;

@SuppressWarnings("deprecation")
public class LegacyNestedContextTest extends LegacyAbstractDebugTest {

    /*
     * TODO remove class with deprecated API.
     */

    @Test
    public void testNestedRun() throws Throwable {
        final Debugger debugger = getDebugger();

        final Source block8 = LegacyTestSource.createBlock8("testNestedRunBlock8");
        final Breakpoint block8line6 = debugger.setLineBreakpoint(0, block8.createLineLocation(6), false);
        expectExecutionEvent().resume();
        expectSuspendedEvent().checkState(6, true, "STATEMENT").run(new Runnable() {

            public void run() {
                try {
                    pushContext();
                    final Source block12 = LegacyTestSource.createBlock12("testNestedRunBlock12");
                    expectExecutionEvent().resume();
                    getEngine().eval(block12);
                    assertExecutedOK();
                    popContext();
                } catch (Throwable e) {
                    fail();
                }
            }
        }).checkState(6, true, "STATEMENT").stepInto(1);
        expectSuspendedEvent().checkState(7, true, "STATEMENT").resume();
        getEngine().eval(block8);
        assertExecutedOK();
        assertEquals(1, block8line6.getHitCount());
    }

    @Test
    public void testNestedContinue() throws Throwable {
        final Debugger debugger = getDebugger();

        final Source block8 = LegacyTestSource.createBlock8("testNestedContinueBlock8");
        final Breakpoint block8line6 = debugger.setLineBreakpoint(0, block8.createLineLocation(6), false);
        expectExecutionEvent().resume();
        expectSuspendedEvent().checkState(6, true, "STATEMENT").run(new Runnable() {

            public void run() {
                try {
                    pushContext();
                    final Source block12 = LegacyTestSource.createBlock12("testNestedContinueBlock12");
                    final Breakpoint block12line9 = debugger.setLineBreakpoint(0, block12.createLineLocation(9), false);
                    expectExecutionEvent().resume();
                    expectSuspendedEvent().checkState(9, true, "STATEMENT").resume();
                    getEngine().eval(block12);
                    assertExecutedOK();
                    assertEquals(1, block12line9.getHitCount());
                    popContext();
                } catch (Throwable e) {
                    throw new AssertionError(e);
                }
            }
        }).checkState(6, true, "STATEMENT").stepInto(1);
        expectSuspendedEvent().checkState(7, true, "STATEMENT").resume();
        getEngine().eval(block8);
        assertExecutedOK();
        assertEquals(1, block8line6.getHitCount());
    }

    @Test
    public void testNestedKill() throws Throwable {
        final Debugger debugger = getDebugger();

        final Source block8 = LegacyTestSource.createBlock8("testNestedKillBlock8");
        final Breakpoint block8line6 = debugger.setLineBreakpoint(0, block8.createLineLocation(6), false);
        expectExecutionEvent().resume();
        expectSuspendedEvent().checkState(6, true, "STATEMENT").run(new Runnable() {

            public void run() {
                try {
                    pushContext();
                    final Source block12 = LegacyTestSource.createBlock12("testNestedKillBlock12");
                    final Breakpoint block12line9 = debugger.setLineBreakpoint(0, block12.createLineLocation(9), false);
                    expectExecutionEvent().resume();
                    expectSuspendedEvent().checkState(9, true, "STATEMENT").run(new Runnable() {
                        public void run() {
                            assertEquals(1, block12line9.getHitCount());
                        }
                    }).kill();
                    getEngine().eval(block12);
                    fail();
                } catch (ThreadDeath ex) {
                    popContext();
                } catch (Throwable e) {
                    throw new AssertionError(e);
                }
            }
        }).checkState(6, true, "STATEMENT").stepInto(1);
        expectSuspendedEvent().checkState(7, true, "STATEMENT").resume();
        getEngine().eval(block8);
        assertExecutedOK();
        assertEquals(1, block8line6.getHitCount());
    }

}
