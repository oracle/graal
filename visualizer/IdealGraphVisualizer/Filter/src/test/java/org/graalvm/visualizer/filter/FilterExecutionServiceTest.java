/*
 * Copyright (c) 2013, 2022, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package org.graalvm.visualizer.filter;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;

import org.graalvm.visualizer.graph.Diagram;
import org.junit.Test;

import jdk.graal.compiler.graphio.parsing.model.InputGraph;

/**
 * @author sdedic
 */
public class FilterExecutionServiceTest {
    List<FilterEvent> esEvents = new ArrayList<>();
    List<FilterEvent> eeEvents = new ArrayList<>();
    List<FilterEvent> fsEvents = new ArrayList<>();
    List<FilterEvent> feEvents = new ArrayList<>();

    class FL implements FilterListener {

        @Override
        public void filterStart(FilterEvent e) {
            fsEvents.add(e);
        }

        @Override
        public void filterEnd(FilterEvent e) {
            feEvents.add(e);
        }

        @Override
        public void executionStart(FilterEvent e) {
            esEvents.add(e);
        }

        @Override
        public void executionEnd(FilterEvent e) {
            eeEvents.add(e);
        }

    }

    /**
     * Checks that the filter is executed, gets environment.
     */
    @Test
    public void testBroadcastEvents() {
        FilterExecution.getExecutionService().addFilterListener(new FL());
        FilterChain ch = new FilterChain();
        InputGraph gr = InputGraph.createTestGraph("Ahoj");
        Diagram d = Diagram.createDiagram(gr, "testDiagram");

        ch.addFilter(new FilterExecutionTest.F() {
            @Override
            public void applyWith(FilterEnvironment env) {
            }

        });
        ch.apply(d);

        assertEquals(1, fsEvents.size());
        assertEquals(1, feEvents.size());
        assertEquals(1, esEvents.size());
        assertEquals(1, eeEvents.size());

        assertNotSame(feEvents.get(0), eeEvents.get(0));
    }

    /**
     * Checks that the filter is executed, gets environment.
     */
    @Test
    public void testBroadcastFailure() {
        FilterExecution.getExecutionService().addFilterListener(new FL());
        FilterChain ch = new FilterChain();
        InputGraph gr = InputGraph.createTestGraph("Ahoj");
        Diagram d = Diagram.createDiagram(gr, "testDiagram");

        ch.addFilter(new FilterExecutionTest.F() {
            @Override
            public void applyWith(FilterEnvironment env) {
                throw new RuntimeException();
            }

        });
        ch.apply(d);

        assertEquals(1, fsEvents.size());
        assertEquals(1, feEvents.size());
        assertEquals(1, esEvents.size());
        assertEquals(1, eeEvents.size());

        assertSame(feEvents.get(0), eeEvents.get(0));
    }

    /**
     * Checks that the filter is executed, gets environment.
     */
    @Test
    public void testFailureInMiddle() {
        FilterExecution.getExecutionService().addFilterListener(new FL());
        FilterChain ch = new FilterChain();
        InputGraph gr = InputGraph.createTestGraph("Ahoj");
        Diagram d = Diagram.createDiagram(gr, "testDiagram");

        ch.addFilter(new FilterExecutionTest.F() {
            @Override
            public void applyWith(FilterEnvironment env) {
            }

        });
        ch.addFilter(new FilterExecutionTest.F() {
            @Override
            public void applyWith(FilterEnvironment env) {
                throw new RuntimeException();
            }

        });
        ch.apply(d);

        assertEquals(2, fsEvents.size());
        assertEquals(2, feEvents.size());
        assertEquals(1, esEvents.size());
        assertEquals(1, eeEvents.size());

        assertSame(feEvents.get(1), eeEvents.get(0));
    }
}
