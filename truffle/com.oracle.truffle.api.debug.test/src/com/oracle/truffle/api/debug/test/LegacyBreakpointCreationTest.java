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
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.oracle.truffle.api.debug.Breakpoint;
import com.oracle.truffle.api.debug.Debugger;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.vm.PolyglotEngine;

/**
 * Creating and modifying breakpoints; no actual executions.
 */
@SuppressWarnings("deprecation")
public final class LegacyBreakpointCreationTest {

    /*
     * TODO remove class with deprecated API.
     */
    private Debugger db;
    protected PolyglotEngine engine;
    protected final ByteArrayOutputStream out = new ByteArrayOutputStream();
    protected final ByteArrayOutputStream err = new ByteArrayOutputStream();

    final Source testSource = Source.newBuilder("line 1\n" +
                    "line 2\n" +
                    "line 3\n").name("testSource").mimeType("content/unknown").build();

    @Before
    public void before() {
        engine = PolyglotEngine.newBuilder().setOut(out).setErr(err).build();
        db = Debugger.find(engine);
    }

    @After
    public void dispose() {
        if (engine != null) {
            engine.dispose();
        }
    }

    @Test
    public void breakpointBasics() throws IOException {

        assertEquals("no breakpoints at start", 0, db.getBreakpoints().size());

        // Create breakpoint at line 1
        final com.oracle.truffle.api.source.LineLocation line1 = testSource.createLineLocation(1);
        final Breakpoint b1 = db.setLineBreakpoint(0, line1, false);
        assertEquals("one breakpoint created so far", 1, db.getBreakpoints().size());
        assertSame("same as one created", b1, db.getBreakpoints().iterator().next());

        // Check initial state of breakpoint at line 1
        assertFalse("not one-shot", b1.isOneShot());
        assertEquals("ignorecount 0", 0, b1.getIgnoreCount());
        assertEquals("no hits yet", 0, b1.getHitCount());
        assertNull("no condition set", b1.getCondition());
        assertEquals("initial state", Breakpoint.State.ENABLED_UNRESOLVED, b1.getState());

        // Make some state changes
        final int newIgnoreCount = 9;
        b1.setIgnoreCount(newIgnoreCount);
        assertEquals("new ignore count is 9", newIgnoreCount, b1.getIgnoreCount());

        assertTrue("originally enabled", b1.isEnabled());
        b1.setEnabled(false);
        assertFalse("now disabled", b1.isEnabled());

        final String condExpr = "a + b";
        b1.setCondition(condExpr);
        assertEquals("same code", condExpr, b1.getCondition());

        // Create breakpoint at line 2
        final com.oracle.truffle.api.source.LineLocation line2 = testSource.createLineLocation(2);
        final Breakpoint b2 = db.setLineBreakpoint(0, line2, false);
        assertEquals("two breakpoint created so far", 2, db.getBreakpoints().size());

        // Dispose breakpoint at line 1
        b1.dispose();
        assertEquals("breakpoint is disposed", Breakpoint.State.DISPOSED, b1.getState());
        assertEquals("one breakpoint left", 1, db.getBreakpoints().size());
        assertSame("second breakpoint remains", b2, db.getBreakpoints().iterator().next());

        // Dispose breakpoint at line 2
        b2.dispose();
        assertEquals("no breakpoints left", db.getBreakpoints().size(), 0);

        // Create a new breakpoint at line 1
        db.setLineBreakpoint(0, line1, false);
        assertEquals("one breakpoint exists", 1, db.getBreakpoints().size());
        assertNotSame(b1, db.getBreakpoints().iterator().next());
    }

    @Test(expected = IOException.class)
    public void duplicateLocation() throws IOException {
        final com.oracle.truffle.api.source.LineLocation line1 = testSource.createLineLocation(1);
        db.setLineBreakpoint(0, line1, false);
        db.setLineBreakpoint(0, line1, false);
    }
}
