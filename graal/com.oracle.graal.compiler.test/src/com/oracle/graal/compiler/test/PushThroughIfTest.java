/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.compiler.test;

import jdk.internal.jvmci.debug.*;

import org.junit.*;

import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.StructuredGraph.AllowAssumptions;
import com.oracle.graal.nodes.util.*;
import com.oracle.graal.phases.common.*;
import com.oracle.graal.phases.tiers.*;

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
        Debug.dump(graph, "Graph");
        for (FrameState fs : graph.getNodes(FrameState.TYPE).snapshot()) {
            fs.replaceAtUsages(null);
            GraphUtil.killWithUnusedFloatingInputs(fs);
        }
        new CanonicalizerPhase().apply(graph, new PhaseContext(getProviders()));
        new CanonicalizerPhase().apply(graph, new PhaseContext(getProviders()));

        StructuredGraph referenceGraph = parseEager(reference, AllowAssumptions.YES);
        for (FrameState fs : referenceGraph.getNodes(FrameState.TYPE).snapshot()) {
            fs.replaceAtUsages(null);
            GraphUtil.killWithUnusedFloatingInputs(fs);
        }
        new CanonicalizerPhase().apply(referenceGraph, new PhaseContext(getProviders()));
        assertEquals(referenceGraph, graph);
    }
}
