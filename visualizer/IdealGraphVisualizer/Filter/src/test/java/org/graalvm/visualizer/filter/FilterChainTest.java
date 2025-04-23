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
import org.junit.Before;
import org.junit.Test;

import jdk.graal.compiler.graphio.parsing.model.ChangedListener;
import jdk.graal.compiler.graphio.parsing.model.InputGraph;

/**
 * @author sdedic
 */
public class FilterChainTest {
    Diagram testDiagram;
    InputGraph graph;
    FilterChain chain;

    @Before
    public void setup() {
        FilterChain ch = new FilterChain();
        InputGraph gr = InputGraph.createTestGraph("Ahoj");
        Diagram d = Diagram.createDiagram(gr, "testDiagram");

        this.graph = gr;
        this.testDiagram = d;
        this.chain = ch;
    }

    private int changeCount;

    /**
     * Checks that events are fired, and the filter is added
     * to the known filters
     */
    @Test
    public void testAddFilter() {
        Filter f = new ColorFilter("ahoj");
        chain.getChangedEvent().addListener(new ChangedListener<FilterChain>() {
            @Override
            public void changed(FilterChain source) {
                changeCount++;
            }
        });

        chain.addFilter(f);
        assertEquals(1, changeCount);
        assertTrue(chain.containsFilter(f));
    }

    /**
     * Checks that events are fired, and the filter is added
     * to the known filters
     */
    @Test
    public void testRemoveFilter() {
        Filter f = new ColorFilter("ahoj");
        chain.getChangedEvent().addListener(new ChangedListener<FilterChain>() {
            @Override
            public void changed(FilterChain source) {
                changeCount++;
            }
        });
        assertFalse(chain.containsFilter(f));

        chain.addFilter(f);
        assertTrue(chain.containsFilter(f));
        changeCount = 0;

        chain.removeFilter(f);
        assertEquals(1, changeCount);
        assertFalse(chain.containsFilter(f));
    }

    private FilterEvent startEvent;
    private FilterEvent stopEvent;

    /**
     * Checks that events are generated from filter processing
     */
    @Test
    public void testEventsFromFilterProcessing() {
        Filter f = new ColorFilter("ahoj");
        chain.addFilter(f);

        chain.addFilterListener(new FilterListener() {
            @Override
            public void filterStart(FilterEvent e) {
                assertNull(startEvent);
                startEvent = e;
            }

            @Override
            public void filterEnd(FilterEvent e) {
                assertNull(stopEvent);
                stopEvent = e;
            }
        });

        chain.apply(testDiagram);

        assertNotNull(startEvent);
        assertNotNull(stopEvent);
    }

    private final List<FilterEvent> startEvents = new ArrayList<>();
    private final List<FilterEvent> endEvents = new ArrayList<>();

    /**
     * Checks that events are generated from filter processing
     */
    @Test
    public void testErrorEventFromProcessing() {
        Filter f = new ColorFilter("ahoj") {
            @Override
            public void apply(Diagram diagram) {
                throw new NumberFormatException();
            }
        };

        chain.addFilter(f);
        chain.addFilter(new ColorFilter("bye"));

        chain.addFilterListener(new FilterListener() {
            @Override
            public void filterStart(FilterEvent e) {
                startEvents.add(e);
            }

            @Override
            public void filterEnd(FilterEvent e) {
                endEvents.add(e);
            }
        });

        chain.apply(testDiagram);

        assertEquals(2, startEvents.size());
        assertEquals(2, endEvents.size());

        FilterEvent e = endEvents.get(0);
        assertTrue(e.getExecutionError() instanceof NumberFormatException);
    }
}
