/*
 * Copyright (c) 2021, 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.core.test;

import java.io.IOException;
import java.util.Arrays;

import org.graalvm.collections.EconomicMap;
import jdk.graal.compiler.core.common.memory.BarrierType;
import jdk.graal.compiler.core.common.memory.MemoryOrderMode;
import jdk.graal.compiler.debug.DebugOptions;
import jdk.graal.compiler.debug.DebugOptions.PrintGraphTarget;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodeinfo.NodeCycles;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodeinfo.NodeSize;
import jdk.graal.compiler.debug.TTY;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.memory.WriteNode;
import jdk.graal.compiler.nodes.memory.address.AddressNode;
import jdk.graal.compiler.options.OptionKey;
import jdk.graal.compiler.options.OptionValues;
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

    @Test
    public void testDump() throws IOException {
        try (TTY.Filter _ = new TTY.Filter(); TemporaryDirectory temp = new TemporaryDirectory("GraphDumpingTest")) {
            compileWithDumping("snippet01", temp);
        }
    }

    @Test
    public void testInvalidNodeProperties() throws IOException {
        try (TTY.Filter _ = new TTY.Filter(); TemporaryDirectory temp = new TemporaryDirectory("GraphDumpingTest")) {
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
