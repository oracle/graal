/*
 * Copyright (c) 2015, 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.api.directives.test;

import org.junit.*;

import com.oracle.graal.api.directives.*;
import com.oracle.graal.compiler.test.*;
import com.oracle.graal.graph.iterators.*;
import com.oracle.graal.nodes.*;

public class ProbabilityDirectiveTest extends GraalCompilerTest {

    public static int branchProbabilitySnippet(int arg) {
        if (GraalDirectives.injectBranchProbability(0.125, arg > 0)) {
            GraalDirectives.controlFlowAnchor(); // prevent removal of the if
            return 1;
        } else {
            GraalDirectives.controlFlowAnchor(); // prevent removal of the if
            return 2;
        }
    }

    @Test
    public void testBranchProbability() {
        test("branchProbabilitySnippet", 5);
    }

    @Override
    protected boolean checkLowTierGraph(StructuredGraph graph) {
        NodeIterable<IfNode> ifNodes = graph.getNodes(IfNode.TYPE);
        Assert.assertEquals("IfNode count", 1, ifNodes.count());

        IfNode ifNode = ifNodes.first();
        AbstractBeginNode trueSuccessor = ifNode.trueSuccessor();
        Assert.assertEquals("branch probability of " + ifNode, 0.125, ifNode.probability(trueSuccessor), 0);

        return true;
    }
}
