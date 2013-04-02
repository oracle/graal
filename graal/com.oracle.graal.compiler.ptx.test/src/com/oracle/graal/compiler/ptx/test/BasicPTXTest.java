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
package com.oracle.graal.compiler.ptx.test;

import java.lang.reflect.*;

import org.junit.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.api.runtime.*;
import com.oracle.graal.compiler.*;
import com.oracle.graal.compiler.ptx.*;
import com.oracle.graal.compiler.test.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.java.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.type.*;
import com.oracle.graal.phases.*;
import com.oracle.graal.phases.PhasePlan.PhasePosition;
import com.oracle.graal.ptx.*;

/**
 * Test class for small Java methods compiled to PTX kernels.
 */
public class BasicPTXTest extends GraalCompilerTest {

    @Test
    public void testAdd() {
        test("testAddSnippet");
    }

    public static int testAddSnippet(int a) {
        return a + 1;
    }

    @Test
    public void testArray() {
        test("testArraySnippet");
    }

    public static int testArraySnippet(int[] array) {
        return array[0];
    }

    private CompilationResult test(String snippet) {
        StructuredGraph graph = parse(snippet);
        Debug.dump(graph, "Graph");
        TargetDescription target = new TargetDescription(new PTX(), true, 1, 0, true);
        PTXBackend ptxBackend = new PTXBackend(Graal.getRequiredCapability(CodeCacheProvider.class), target);
        PhasePlan phasePlan = new PhasePlan();
        GraphBuilderPhase graphBuilderPhase = new GraphBuilderPhase(runtime, GraphBuilderConfiguration.getDefault(), OptimisticOptimizations.NONE);
        phasePlan.addPhase(PhasePosition.AFTER_PARSING, graphBuilderPhase);
        phasePlan.addPhase(PhasePosition.AFTER_PARSING, new PTXPhase());
        new PTXPhase().apply(graph);
        CompilationResult result = GraalCompiler.compileMethod(runtime, ptxBackend, target, graph.method(), graph, null, phasePlan, OptimisticOptimizations.NONE, new SpeculationLog());
        return result;
    }

    private static class PTXPhase extends Phase {

        @Override
        protected void run(StructuredGraph graph) {
            for (LocalNode local : graph.getNodes(LocalNode.class)) {
                if (local.kind() == Kind.Object) {
                    local.setStamp(StampFactory.declaredNonNull(local.objectStamp().type()));
                }
            }
        }

    }

    public static void main(String[] args) {
        BasicPTXTest basicPTXTest = new BasicPTXTest();
        Method[] methods = BasicPTXTest.class.getMethods();
        for (Method m : methods) {
            if (m.getAnnotation(Test.class) != null) {
                String name = m.getName() + "Snippet";
                System.out.println(name + ": \n" + new String(basicPTXTest.test(name).getTargetCode()));
            }
        }
    }
}
