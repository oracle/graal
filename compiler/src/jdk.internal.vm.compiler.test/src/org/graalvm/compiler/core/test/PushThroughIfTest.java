/*
 * Copyright (c) 2011, 2019, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.nodes.FrameState;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.StructuredGraph.AllowAssumptions;
import org.graalvm.compiler.nodes.util.GraphUtil;
import org.junit.Test;

public class PushThroughIfTest extends GraalCompilerTest {

    public int field1;
    public int field2;

    public int testSnippet(boolean b) {
        int i;
        if (b) {
            i = field1;
        } else {
            i = field1;
        }
        return i + field2;
    }

    @SuppressWarnings("unused")
    public int referenceSnippet(boolean b) {
        return field1 + field2;
    }

    @Test
    public void test1() {
        test("testSnippet", "referenceSnippet");
    }

    private void test(String snippet, String reference) {
        StructuredGraph graph = parseEager(snippet, AllowAssumptions.YES);
        DebugContext debug = graph.getDebug();
        debug.dump(DebugContext.BASIC_LEVEL, graph, "Graph");
        for (FrameState fs : graph.getNodes(FrameState.TYPE).snapshot()) {
            fs.replaceAtUsages(null);
            GraphUtil.killWithUnusedFloatingInputs(fs);
        }
        createCanonicalizerPhase().apply(graph, getProviders());
        createCanonicalizerPhase().apply(graph, getProviders());

        StructuredGraph referenceGraph = parseEager(reference, AllowAssumptions.YES);
        for (FrameState fs : referenceGraph.getNodes(FrameState.TYPE).snapshot()) {
            fs.replaceAtUsages(null);
            GraphUtil.killWithUnusedFloatingInputs(fs);
        }
        createCanonicalizerPhase().apply(referenceGraph, getProviders());
        assertEquals(referenceGraph, graph);
    }
}
