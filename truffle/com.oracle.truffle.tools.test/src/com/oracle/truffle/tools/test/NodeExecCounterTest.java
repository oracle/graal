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

import static com.oracle.truffle.tools.test.TestNodes.createExpr13TestCallTarget;
import static com.oracle.truffle.tools.test.TestNodes.createExpr13TestRootNode;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import org.junit.Test;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.instrument.Instrumenter;
import com.oracle.truffle.api.instrument.Probe;
import com.oracle.truffle.api.instrument.StandardSyntaxTag;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.tools.NodeExecCounter;
import com.oracle.truffle.tools.NodeExecCounter.NodeExecutionCount;
import com.oracle.truffle.tools.test.TestNodes.TestAddNode;
import com.oracle.truffle.tools.test.TestNodes.TestValueNode;

public class NodeExecCounterTest {

    @Test
    public void testNoExecution() throws NoSuchFieldException, SecurityException, IllegalArgumentException, IllegalAccessException {
        final Instrumenter instrumenter = TestNodes.createInstrumenter();
        final NodeExecCounter tool = new NodeExecCounter();
        assertEquals(tool.getCounts().length, 0);
        tool.install(instrumenter);
        assertEquals(tool.getCounts().length, 0);
        tool.setEnabled(false);
        assertEquals(tool.getCounts().length, 0);
        tool.setEnabled(true);
        assertEquals(tool.getCounts().length, 0);
        tool.reset();
        assertEquals(tool.getCounts().length, 0);
        tool.dispose();
        assertEquals(tool.getCounts().length, 0);
    }

    @Test
    public void testToolCreatedTooLate() throws NoSuchFieldException, SecurityException, IllegalArgumentException, IllegalAccessException {
        final Instrumenter instrumenter = TestNodes.createInstrumenter();
        final CallTarget expr13callTarget = createExpr13TestCallTarget(instrumenter);
        final NodeExecCounter tool = new NodeExecCounter();
        tool.install(instrumenter);
        assertEquals(13, expr13callTarget.call());
        assertEquals(tool.getCounts().length, 0);
        tool.dispose();
    }

    @Test
    public void testToolInstalledcTooLate() throws NoSuchFieldException, SecurityException, IllegalArgumentException, IllegalAccessException {
        final Instrumenter instrumenter = TestNodes.createInstrumenter();
        final NodeExecCounter tool = new NodeExecCounter();
        final CallTarget expr13callTarget = createExpr13TestCallTarget(instrumenter);
        tool.install(instrumenter);
        assertEquals(13, expr13callTarget.call());
        assertEquals(tool.getCounts().length, 0);
        tool.dispose();
    }

    @Test
    public void testCountingAll() throws NoSuchFieldException, SecurityException, IllegalArgumentException, IllegalAccessException {
        final Instrumenter instrumenter = TestNodes.createInstrumenter();
        final NodeExecCounter tool = new NodeExecCounter();
        tool.install(instrumenter);
        final CallTarget expr13callTarget = createExpr13TestCallTarget(instrumenter);

        // execute once
        assertEquals(13, expr13callTarget.call());
        final NodeExecutionCount[] count1 = tool.getCounts();
        assertNotNull(count1);
        assertEquals(count1.length, 2);
        for (NodeExecutionCount count : count1) {
            final Class<?> class1 = count.nodeClass();
            final long executionCount = count.executionCount();
            if (class1 == TestAddNode.class) {
                assertEquals(executionCount, 1);
            } else if (class1 == TestValueNode.class) {
                assertEquals(executionCount, 2);
            } else {
                fail();
            }
        }

        // Execute 99 more times
        for (int i = 0; i < 99; i++) {
            assertEquals(13, expr13callTarget.call());
        }
        final NodeExecutionCount[] counts100 = tool.getCounts();
        assertNotNull(counts100);
        assertEquals(counts100.length, 2);
        for (NodeExecutionCount count : counts100) {
            final Class<?> class1 = count.nodeClass();
            final long executionCount = count.executionCount();
            if (class1 == TestAddNode.class) {
                assertEquals(executionCount, 100);
            } else if (class1 == TestValueNode.class) {
                assertEquals(executionCount, 200);
            } else {
                fail();
            }
        }

        tool.dispose();
    }

    @Test
    public void testCountingTagged() throws NoSuchFieldException, SecurityException, IllegalArgumentException, IllegalAccessException {
        final Instrumenter instrumenter = TestNodes.createInstrumenter();
        final NodeExecCounter tool = new NodeExecCounter(StandardSyntaxTag.STATEMENT);
        tool.install(instrumenter);
        final RootNode expr13rootNode = createExpr13TestRootNode(instrumenter);

        // Not probed yet.
        assertEquals(13, expr13rootNode.execute(null));
        assertEquals(tool.getCounts().length, 0);

        final Node addNode = expr13rootNode.getChildren().iterator().next();
        final Probe probe = instrumenter.probe(addNode);

        // Probed but not tagged yet.
        assertEquals(13, expr13rootNode.execute(null));
        assertEquals(tool.getCounts().length, 0);

        probe.tagAs(StandardSyntaxTag.STATEMENT, "fake statement for testing");

        // Counting now; execute once
        assertEquals(13, expr13rootNode.execute(null));
        final NodeExecutionCount[] counts1 = tool.getCounts();
        assertNotNull(counts1);
        assertEquals(counts1.length, 1);
        final NodeExecutionCount count1 = counts1[0];
        assertNotNull(count1);
        assertEquals(count1.nodeClass(), addNode.getClass());
        assertEquals(count1.executionCount(), 1);

        // Execute 99 more times
        for (int i = 0; i < 99; i++) {
            assertEquals(13, expr13rootNode.execute(null));
        }

        final NodeExecutionCount[] counts100 = tool.getCounts();
        assertNotNull(counts100);
        assertEquals(counts100.length, 1);
        final NodeExecutionCount count100 = counts100[0];
        assertNotNull(count100);
        assertEquals(count100.nodeClass(), addNode.getClass());
        assertEquals(count100.executionCount(), 100);

        tool.dispose();
    }
}
