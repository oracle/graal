/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.test.instrument;

import static org.junit.Assert.*;

import org.junit.*;

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.instrument.*;
import com.oracle.truffle.api.nodes.*;
import com.oracle.truffle.api.test.instrument.InstrumentationTestNodes.TestAdditionNode;
import com.oracle.truffle.api.test.instrument.InstrumentationTestNodes.TestRootNode;
import com.oracle.truffle.api.test.instrument.InstrumentationTestNodes.TestValueNode;
import com.oracle.truffle.api.test.instrument.InstrumentationTestNodes.TestToolEvalCounterNode;

/**
 * Tests the kind of instrumentation where a client can provide an AST fragment to be
 * <em>spliced</em> directly into the AST.
 */
public class ToolEvalInstrumentTest {

    @Test
    public void testSpliceInstrumentListener() {
        // Create a simple addition AST
        final TruffleRuntime runtime = Truffle.getRuntime();
        final TestValueNode leftValueNode = new TestValueNode(6);
        final TestValueNode rightValueNode = new TestValueNode(7);
        final TestAdditionNode addNode = new TestAdditionNode(leftValueNode, rightValueNode);
        final TestRootNode rootNode = new TestRootNode(addNode);
        final CallTarget callTarget1 = runtime.createCallTarget(rootNode);

        // Ensure it executes correctly
        assertEquals(13, callTarget1.call());

        // Probe the addition node
        final Probe probe = addNode.probe();

        assertEquals(13, callTarget1.call());

        // Attach a null listener; it never actually attaches a node.
        final Instrument instrument = Instrument.create(new ToolEvalNodeFactory() {

            public ToolEvalNode createToolEvalNode(Probe p, Node n) {
                return null;
            }
        }, null);
        probe.attach(instrument);

        assertEquals(13, callTarget1.call());

        final TestToolEvalCounterNode counter = new TestToolEvalCounterNode();

        // Attach a listener that splices an execution counter into the AST.
        probe.attach(Instrument.create(new ToolEvalNodeFactory() {

            public ToolEvalNode createToolEvalNode(Probe p, Node n) {
                return counter;
            }
        }, null));
        assertEquals(0, counter.getCount());

        assertEquals(13, callTarget1.call());

        assertEquals(1, counter.getCount());

    }

}
