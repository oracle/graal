/*
 * Copyright (c) 2014, 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.tools.test;

import static com.oracle.truffle.tools.test.TestNodes.*;
import static org.junit.Assert.*;

import org.junit.*;

import com.oracle.truffle.api.instrument.*;
import com.oracle.truffle.api.nodes.*;
import com.oracle.truffle.tools.*;

public class CoverageTrackerTest {

    @Test
    public void testNoExecution() {
        final CoverageTracker tool = new CoverageTracker();
        assertEquals(tool.getCounts().entrySet().size(), 0);
        tool.install();
        assertEquals(tool.getCounts().entrySet().size(), 0);
        tool.setEnabled(false);
        assertEquals(tool.getCounts().entrySet().size(), 0);
        tool.setEnabled(true);
        assertEquals(tool.getCounts().entrySet().size(), 0);
        tool.reset();
        assertEquals(tool.getCounts().entrySet().size(), 0);
        tool.dispose();
        assertEquals(tool.getCounts().entrySet().size(), 0);
    }

    @Test
    public void testToolCreatedTooLate() {
        final RootNode expr13rootNode = createExpr13TestRootNode();
        final CoverageTracker tool = new CoverageTracker();
        tool.install();
        assertEquals(13, expr13rootNode.execute(null));
        assertTrue(tool.getCounts().isEmpty());
        tool.dispose();
    }

    @Test
    public void testToolInstalledcTooLate() {
        final CoverageTracker tool = new CoverageTracker();
        final RootNode expr13rootNode = createExpr13TestRootNode();
        tool.install();
        assertEquals(13, expr13rootNode.execute(null));
        assertTrue(tool.getCounts().isEmpty());
        tool.dispose();
    }

    @Test
    public void testCountingCoverage() {
        final CoverageTracker tool = new CoverageTracker();
        tool.install();
        final RootNode expr13rootNode = createExpr13TestRootNode();

        // Not probed yet.
        assertEquals(13, expr13rootNode.execute(null));
        assertTrue(tool.getCounts().isEmpty());

        final Node addNode = expr13rootNode.getChildren().iterator().next();
        final Probe probe = addNode.probe();

        // Probed but not tagged yet.
        assertEquals(13, expr13rootNode.execute(null));
        assertTrue(tool.getCounts().isEmpty());

        probe.tagAs(StandardSyntaxTag.STATEMENT, "fake statement for testing");

        // Counting now; execute once
        assertEquals(13, expr13rootNode.execute(null));

        final Long[] longs1 = tool.getCounts().get(addNode.getSourceSection().getSource());
        assertNotNull(longs1);
        assertEquals(longs1.length, 2);
        assertNull(longs1[0]);  // Line 1 is empty (text lines are 1-based)
        assertEquals(1L, longs1[1].longValue());  // Expression is on line 2

        // Execute 99 more times
        for (int i = 0; i < 99; i++) {
            assertEquals(13, expr13rootNode.execute(null));
        }

        final Long[] longs100 = tool.getCounts().get(addNode.getSourceSection().getSource());
        assertNotNull(longs100);
        assertEquals(longs100.length, 2);
        assertNull(longs100[0]);
        assertEquals(100L, longs100[1].longValue());

        tool.dispose();
    }

}
