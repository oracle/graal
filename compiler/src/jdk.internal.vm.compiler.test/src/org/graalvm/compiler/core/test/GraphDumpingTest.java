/*
 * Copyright (c) 2021, 2023, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.core.test;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.Arrays;

import org.graalvm.collections.EconomicMap;
import org.graalvm.compiler.core.common.memory.BarrierType;
import org.graalvm.compiler.core.common.memory.MemoryOrderMode;
import org.graalvm.compiler.debug.DebugOptions;
import org.graalvm.compiler.debug.DebugOptions.PrintGraphTarget;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.nodeinfo.NodeCycles;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodeinfo.NodeSize;
import org.graalvm.compiler.debug.TTY;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.memory.WriteNode;
import org.graalvm.compiler.nodes.memory.address.AddressNode;
import org.graalvm.compiler.options.OptionKey;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.word.LocationIdentity;
import org.junit.Test;

import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Tests related to graph dumping.
 */
public class GraphDumpingTest extends GraalCompilerTest {

    public static Object snippet01(Object[] array) {
        return Arrays.toString(array);
    }

    public static int sideEffect = 42;

    public void snippet02(int i) {
        sideEffect = i;
    }

    @SuppressWarnings("try")
    @Test
    public void testDump() throws IOException {
        try (TTY.Filter suppressTTY = new TTY.Filter(); TemporaryDirectory temp = new TemporaryDirectory(Paths.get("."), "GraphDumpingTest")) {
            compileWithDumping("snippet01", temp);
        }
    }

    @SuppressWarnings("try")
    @Test
    public void testInvalidNodeProperties() throws IOException {
        try (TTY.Filter suppressTTY = new TTY.Filter(); TemporaryDirectory temp = new TemporaryDirectory(Paths.get("."), "GraphDumpingTest")) {
            StructuredGraph graph = compileWithDumping("snippet02", temp);

            // introduce an invalid node with broken properties
            WriteNode write = graph.getNodes(WriteNode.TYPE).first();
            BrokenWriteNode brokenWrite = new BrokenWriteNode(write.getAddress(), write.getLocationIdentity(), write.value(), write.getBarrierType(), write.getMemoryOrder());
            graph.add(brokenWrite);
            write.replaceAndDelete(brokenWrite);

            graph.getDebug().forceDump(graph, "Dump with broken write.");
        }
    }

    @NodeInfo(nameTemplate = "brokenWrite")
    public class BrokenWriteNode extends WriteNode {
        public static final NodeClass<BrokenWriteNode> TYPE = NodeClass.create(BrokenWriteNode.class);

        public BrokenWriteNode(AddressNode address, LocationIdentity location, ValueNode value, BarrierType barrierType, MemoryOrderMode memoryOrder) {
            super(TYPE, address, location, location, value, barrierType, memoryOrder);
        }

        @Override
        public LocationIdentity getKilledLocationIdentity() {
            throw new UnsupportedOperationException("This operation throws an exception on purpose.");
        }

        @Override
        public NodeCycles estimatedNodeCycles() {
            throw new UnsupportedOperationException("This operation throws an exception on purpose.");
        }

        @Override
        protected NodeSize dynamicNodeSizeEstimate() {
            throw new UnsupportedOperationException("This operation throws an exception on purpose.");
        }

        @Override
        public LocationIdentity getLocationIdentity() {
            throw new UnsupportedOperationException("This operation throws an exception on purpose.");
        }
    }

    private StructuredGraph compileWithDumping(String methodName, TemporaryDirectory temp) {
        EconomicMap<OptionKey<?>, Object> overrides = OptionValues.newOptionMap();
        overrides.put(DebugOptions.DumpPath, temp.toString());
        overrides.put(DebugOptions.Dump, ":5");
        overrides.put(DebugOptions.PrintGraph, PrintGraphTarget.File);
        overrides.put(DebugOptions.MethodFilter, null);

        ResolvedJavaMethod method = getResolvedJavaMethod(methodName);
        StructuredGraph graph = parseForCompile(method, new OptionValues(getInitialOptions(), overrides));
        getCode(method, graph);

        return graph;
    }
}
