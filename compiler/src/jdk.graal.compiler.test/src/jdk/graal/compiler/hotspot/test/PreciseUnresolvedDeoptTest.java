/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

package jdk.graal.compiler.hotspot.test;

import org.junit.Assert;
import org.junit.Test;

import jdk.graal.compiler.api.directives.GraalDirectives;
import jdk.graal.compiler.core.test.GraalCompilerTest;
import jdk.graal.compiler.nodes.DeoptimizeNode;
import jdk.graal.compiler.nodes.IfNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.extended.StateSplitProxyNode;

public class PreciseUnresolvedDeoptTest extends GraalCompilerTest {

    public static boolean doNotConvertToGuardSnippet(boolean condition) {
        if (GraalDirectives.injectBranchProbability(0.5, condition)) {
            GraalDirectives.preciseDeoptimize();
        }
        return condition;
    }

    @Override
    protected void checkHighTierGraph(StructuredGraph graph) {
        super.checkHighTierGraph(graph);
        /*
         * Ensure that the deopt was not converted to a guard, and the side effect is still there.
         */
        Assert.assertEquals("if nodes", 1, graph.getNodes(IfNode.TYPE).count());
        Assert.assertEquals("state split proxy nodes", 1, graph.getNodes().filter(StateSplitProxyNode.class).count());
        Assert.assertEquals("deopt nodes", 1, graph.getNodes(DeoptimizeNode.TYPE).count());
    }

    @Test
    public void doNotConvertToGuard() {
        test("doNotConvertToGuardSnippet", true);
    }
}
