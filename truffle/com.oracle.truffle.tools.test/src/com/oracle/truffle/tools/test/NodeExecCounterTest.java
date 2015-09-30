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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.lang.reflect.Field;

import org.junit.Test;

import com.oracle.truffle.api.instrument.Instrumenter;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.vm.PolyglotEngine;
import com.oracle.truffle.tools.NodeExecCounter;
import com.oracle.truffle.tools.NodeExecCounter.NodeExecutionCount;

public class NodeExecCounterTest {

    @Test
    public void testNoExecution() throws NoSuchFieldException, SecurityException, IllegalArgumentException, IllegalAccessException {
        final PolyglotEngine vm = PolyglotEngine.buildNew().build();
        final Field field = PolyglotEngine.class.getDeclaredField("instrumenter");
        field.setAccessible(true);
        final Instrumenter instrumenter = (Instrumenter) field.get(vm);
        final NodeExecCounter tool = new NodeExecCounter();
        assertEquals(tool.getCounts().length, 0);
        instrumenter.install(tool);
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

    void checkCounts(NodeExecCounter execTool, int addCount, int valueCount) {
        NodeExecutionCount[] counts = execTool.getCounts();
        assertEquals(counts.length, 2);
        for (NodeExecutionCount counter : counts) {
            if (counter.nodeClass() == ToolTestUtil.TestAdditionNode.class) {
                assertEquals(counter.executionCount(), addCount);
            } else if (counter.nodeClass() == ToolTestUtil.TestValueNode.class) {
                assertEquals(counter.executionCount(), valueCount);
            } else {
                fail("correct classes counted");
            }
        }
    }

    @Test
    public void testCounting() throws NoSuchFieldException, SecurityException, IllegalArgumentException, IllegalAccessException, IOException {
        final PolyglotEngine vm = PolyglotEngine.buildNew().build();
        final Field field = PolyglotEngine.class.getDeclaredField("instrumenter");
        field.setAccessible(true);
        final Instrumenter instrumenter = (Instrumenter) field.get(vm);
        final Source source = ToolTestUtil.createTestSource("testCounting");
        final NodeExecCounter execTool = new NodeExecCounter();
        instrumenter.install(execTool);

        assertEquals(execTool.getCounts().length, 0);

        assertEquals(vm.eval(source).get(), 13);

        checkCounts(execTool, 1, 2);

        for (int i = 0; i < 99; i++) {
            assertEquals(vm.eval(source).get(), 13);
        }
        checkCounts(execTool, 100, 200);

        execTool.setEnabled(false);
        assertEquals(vm.eval(source).get(), 13);
        checkCounts(execTool, 100, 200);

        execTool.dispose();
    }
}
