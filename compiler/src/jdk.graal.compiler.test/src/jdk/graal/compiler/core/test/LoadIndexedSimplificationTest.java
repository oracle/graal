/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

import org.junit.Test;

import jdk.graal.compiler.api.directives.GraalDirectives;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.PhiNode;
import jdk.graal.compiler.nodes.ReturnNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.java.LoadIndexedNode;

public class LoadIndexedSimplificationTest extends GraalCompilerTest {

    static final int[] ARRAY = new int[]{1, 2, 3, 4};

    public static int testConditionalIndexWithConstantTrueValueSnippet(int x) {
        int index = x < 1 ? 3 : x;
        var array = GraalDirectives.assumeStableDimension(ARRAY, 1);
        return array[index];
    }

    public static int testConditionalIndexWithConstantFalseValueSnippet(int x) {
        int index = x < 1 ? x : 3;
        var array = GraalDirectives.assumeStableDimension(ARRAY, 1);
        return array[index];
    }

    public static int testConditionalIndexWithAllConstantsSnippet(int x) {
        int index = x < 1 ? 3 : 1;
        var array = GraalDirectives.assumeStableDimension(ARRAY, 1);
        return array[index];
    }

    @Test
    public void testConditionalIndexWithOneConstant() {
        // true value is constant
        var graph = canonicalizedGraph("testConditionalIndexWithConstantTrueValueSnippet");
        assert checkResultBranchFolded(0, graph);
        // false value is constant
        graph = canonicalizedGraph("testConditionalIndexWithConstantFalseValueSnippet");
        assert checkResultBranchFolded(1, graph);
    }

    private boolean checkResultBranchFolded(int idx, StructuredGraph graph) {
        /*
         * Check that the graph's return value is a phi with the value at idx is constant folded and
         * the other value being a LoadIndexedNode
         */
        if (graph.getNodes().filter(ReturnNode.class).count() != 1) {
            return false;
        }
        ReturnNode ret = graph.getNodes().filter(ReturnNode.class).first();
        if (ret.result() instanceof PhiNode phi && phi.valueCount() == 2) {
            int nonConstantBranch = idx == 0 ? 1 : 0;
            return phi.valueAt(idx) instanceof ConstantNode && phi.valueAt(nonConstantBranch) instanceof LoadIndexedNode;
        }
        return false;
    }

    @Test
    public void testConditionalIndexWithAllConstants() {
        var graph = canonicalizedGraph("testConditionalIndexWithAllConstantsSnippet");
        assert graph.getNodes().filter(LoadIndexedNode.class).count() == 0 : "LoadIndexed should be folded away.";
    }

    private StructuredGraph canonicalizedGraph(String snippet) {
        var graph = parseEager(getResolvedJavaMethod(snippet), StructuredGraph.AllowAssumptions.NO);
        createCanonicalizerPhase().apply(graph, getDefaultHighTierContext());
        return graph;
    }
}
