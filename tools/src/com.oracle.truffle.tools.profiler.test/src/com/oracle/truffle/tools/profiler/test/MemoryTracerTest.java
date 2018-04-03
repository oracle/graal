/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.tools.profiler.test;

import java.util.Collection;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.oracle.truffle.tools.profiler.MemoryTracer;
import com.oracle.truffle.tools.profiler.ProfilerNode;

public class MemoryTracerTest extends AbstractProfilerTest {

    private MemoryTracer tracer;

    @Before
    public void setupTracer() {
        tracer = MemoryTracer.find(context.getEngine());
        Assert.assertNotNull(tracer);
    }

    @Test
    public void testNoObjectAllocations() {
        Assert.assertFalse(tracer.isCollecting());

        tracer.setCollecting(true);

        Assert.assertTrue(tracer.isCollecting());
        Assert.assertFalse(tracer.hasData());

        execute(defaultSource);

        Assert.assertTrue(tracer.isCollecting());
        Assert.assertFalse(tracer.hasData());

        Collection<ProfilerNode<MemoryTracer.Payload>> rootNodes = tracer.getRootNodes();
        Assert.assertEquals("More allocations found", 0, rootNodes.size());
    }

    @Test
    public void testNoObjectAllocationsRecursive() {
        Assert.assertFalse(tracer.isCollecting());

        tracer.setCollecting(true);

        Assert.assertTrue(tracer.isCollecting());
        Assert.assertFalse(tracer.hasData());

        execute(defaultRecursiveSource);

        Assert.assertTrue(tracer.isCollecting());
        Assert.assertFalse(tracer.hasData());

        Collection<ProfilerNode<MemoryTracer.Payload>> rootNodes = tracer.getRootNodes();
        Assert.assertEquals("More allocations found", 0, rootNodes.size());
    }

    @Test
    public void testOneAllocationInRoot() {
        final String oneAllocationSource = "ROOT(" + "DEFINE(foo,ROOT(STATEMENT))," + "DEFINE(bar,ROOT(BLOCK(STATEMENT,LOOP(10, CALL(foo)))))," +
                        "DEFINE(baz,ROOT(BLOCK(STATEMENT,LOOP(10, CALL(bar)))))," + "ALLOCATION,CALL(baz),CALL(bar)" + ")";

        Assert.assertFalse(tracer.isCollecting());

        tracer.setCollecting(true);

        Assert.assertTrue(tracer.isCollecting());
        Assert.assertFalse(tracer.hasData());

        execute(makeSource(oneAllocationSource));

        Assert.assertTrue(tracer.isCollecting());
        Assert.assertTrue(tracer.hasData());

        Collection<ProfilerNode<MemoryTracer.Payload>> rootNodes = tracer.getRootNodes();
        Assert.assertEquals("More allocations found", 1, rootNodes.size());
        ProfilerNode<MemoryTracer.Payload> node = rootNodes.iterator().next();
        if (node.getChildren() != null) {
            Assert.assertEquals("Nested allocations found!", 0, node.getChildren().size());
        }

        Assert.assertEquals("Incorect number of events!", 1, node.getPayload().getEvents().size());

        tracer.clearData();

        Assert.assertFalse(tracer.hasData());
    }

    @Test
    public void testOneAllocationInRootRecursive() {
        final String oneAllocationSource = "ROOT(" + "DEFINE(foo,ROOT(BLOCK(STATEMENT,RECURSIVE_CALL(foo, 10))))," + "DEFINE(bar,ROOT(BLOCK(STATEMENT,LOOP(10, CALL(foo)))))," +
                        "ALLOCATION,CALL(bar)" +
                        ")";

        Assert.assertFalse(tracer.isCollecting());

        tracer.setCollecting(true);

        Assert.assertTrue(tracer.isCollecting());
        Assert.assertFalse(tracer.hasData());

        execute(makeSource(oneAllocationSource));

        Assert.assertTrue(tracer.isCollecting());
        Assert.assertTrue(tracer.hasData());

        Collection<ProfilerNode<MemoryTracer.Payload>> rootNodes = tracer.getRootNodes();
        Assert.assertEquals("More allocations found", 1, rootNodes.size());
        ProfilerNode<MemoryTracer.Payload> node = rootNodes.iterator().next();
        if (node.getChildren() != null) {
            Assert.assertEquals("Nested allocations found!", 0, node.getChildren().size());
        }

        Assert.assertEquals("Incorect number of events!", 1, node.getPayload().getEvents().size());

        tracer.clearData();

        Assert.assertFalse(tracer.hasData());
    }

    @Test
    public void testMultipleAllocation() {
        final String oneAllocationSource = "ROOT(" + "DEFINE(foo,ROOT(ALLOCATION,STATEMENT))," + "DEFINE(bar,ROOT(BLOCK(ALLOCATION,STATEMENT,LOOP(10, CALL(foo)))))," +
                        "DEFINE(baz,ROOT(BLOCK(ALLOCATION,STATEMENT,LOOP(10, CALL(bar)))))," + "ALLOCATION,CALL(baz),CALL(bar)" + ")";

        Assert.assertFalse(tracer.isCollecting());

        tracer.setCollecting(true);

        Assert.assertTrue(tracer.isCollecting());
        Assert.assertFalse(tracer.hasData());

        execute(makeSource(oneAllocationSource));

        Assert.assertTrue(tracer.isCollecting());
        Assert.assertTrue(tracer.hasData());

        // ROOT
        Collection<ProfilerNode<MemoryTracer.Payload>> rootNodes = tracer.getRootNodes();
        ProfilerNode<MemoryTracer.Payload> node = rootNodes.iterator().next();
        Assert.assertEquals("Incorrect number of allocations found", 123, node.getPayload().getTotalAllocations());
        Assert.assertEquals("Incorrect number of events found", 1, node.getPayload().getEvents().size());

        // BAZ
        node = node.getChildren().iterator().next();
        if (!node.getRootName().equals("baz")) {
            node = node.getChildren().iterator().next();
        }
        Assert.assertEquals("Incorrect number of allocations found", 111, node.getPayload().getTotalAllocations());
        Assert.assertEquals("Incorrect number of events found", 1, node.getPayload().getEvents().size());

        // BAR (from BAZ, not the ROOT)
        node = node.getChildren().iterator().next();
        Assert.assertEquals("Incorrect number of allocations found", 110, node.getPayload().getTotalAllocations());
        Assert.assertEquals("Incorrect number of events found", 10, node.getPayload().getEvents().size());

        // BAR (from BAZ, not the ROOT)
        node = node.getChildren().iterator().next();
        Assert.assertEquals("Incorrect number of allocations found", 100, node.getPayload().getTotalAllocations());
        Assert.assertEquals("Incorrect number of events found", 100, node.getPayload().getEvents().size());

        Assert.assertTrue("Children too deep found!",
                        node.getChildren() == null || node.getChildren().isEmpty());

    }

    @Test
    public void testMultipleAllocationRecursive() {
        final String oneAllocationSource = "ROOT(" + "DEFINE(foo,ROOT(BLOCK(ALLOCATION,STATEMENT,RECURSIVE_CALL(foo, 10))))," + "DEFINE(bar,ROOT(BLOCK(ALLOCATION,STATEMENT,LOOP(10, CALL(foo)))))," +
                        "ALLOCATION,CALL(bar)" + ")";

        Assert.assertFalse(tracer.isCollecting());

        tracer.setCollecting(true);

        Assert.assertTrue(tracer.isCollecting());
        Assert.assertFalse(tracer.hasData());

        execute(makeSource(oneAllocationSource));

        Assert.assertTrue(tracer.isCollecting());
        Assert.assertTrue(tracer.hasData());

        final int totalAllocationsExpected = 112;

        // ROOT
        Collection<ProfilerNode<MemoryTracer.Payload>> rootNodes = tracer.getRootNodes();
        ProfilerNode<MemoryTracer.Payload> node = rootNodes.iterator().next();
        Assert.assertEquals("Incorrect number of allocations found",
                        totalAllocationsExpected, node.getPayload().getTotalAllocations());
        Assert.assertEquals("Incorrect number of events found", 1, node.getPayload().getEvents().size());

        // BAR
        node = node.getChildren().iterator().next();
        Assert.assertEquals("Incorrect number of allocations found",
                        totalAllocationsExpected - 1, node.getPayload().getTotalAllocations());
        Assert.assertEquals("Incorrect number of events found", 1, node.getPayload().getEvents().size());

        // FOO - 11 times because RECURSIVE_CALL goes to recursion depth of 10
        for (int i = 0; i < 11; i++) {
            node = node.getChildren().iterator().next();
            Assert.assertEquals("Incorrect number of allocations found",
                            totalAllocationsExpected - 2 - (10 * i), node.getPayload().getTotalAllocations());
            Assert.assertEquals("Incorrect number of events found", 10, node.getPayload().getEvents().size());
        }
        Assert.assertTrue("Children too deep found!",
                        node.getChildren() == null || node.getChildren().isEmpty());
    }
}
