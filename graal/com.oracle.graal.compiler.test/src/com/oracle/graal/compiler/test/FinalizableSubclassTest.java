/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.reflect.*;

import org.junit.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.code.Assumptions.Assumption;
import com.oracle.graal.api.code.Assumptions.NoFinalizableSubclass;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.java.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.java.*;
import com.oracle.graal.phases.*;
import com.oracle.graal.phases.common.*;

public class FinalizableSubclassTest extends GraalCompilerTest {

    public static class NoFinalizerEver {
    }

    public static class NoFinalizerYet {
    }

    public static class WithFinalizer extends NoFinalizerYet {

        @Override
        protected void finalize() throws Throwable {
            super.finalize();
        }
    }

    private StructuredGraph parseAndProcess(Class cl, Assumptions assumptions) {
        Constructor<?>[] constructors = cl.getConstructors();
        Assert.assertTrue(constructors.length == 1);
        final ResolvedJavaMethod javaMethod = runtime.lookupJavaConstructor(constructors[0]);
        StructuredGraph graph = new StructuredGraph(javaMethod);

        GraphBuilderConfiguration conf = GraphBuilderConfiguration.getSnippetDefault();
        new GraphBuilderPhase(runtime, conf, OptimisticOptimizations.ALL).apply(graph);
        new InliningPhase(runtime(), null, replacements, assumptions, null, getDefaultPhasePlan(), OptimisticOptimizations.ALL).apply(graph);
        new CanonicalizerPhase.Instance(runtime(), assumptions).apply(graph);
        return graph;
    }

    private void checkForRegisterFinalizeNode(Class cl, boolean shouldContainFinalizer, boolean optimistic) {
        Assumptions assumptions = new Assumptions(optimistic);
        StructuredGraph graph = parseAndProcess(cl, assumptions);
        Assert.assertTrue(graph.getNodes().filter(RegisterFinalizerNode.class).count() == (shouldContainFinalizer ? 1 : 0));
        int noFinalizerAssumption = 0;
        for (Assumption a : assumptions) {
            if (a instanceof NoFinalizableSubclass) {
                noFinalizerAssumption++;
            }
        }
        Assert.assertTrue(noFinalizerAssumption == (shouldContainFinalizer ? 0 : 1));
    }

    @Test
    public void test1() {
        checkForRegisterFinalizeNode(NoFinalizerEver.class, true, false);
        checkForRegisterFinalizeNode(NoFinalizerEver.class, false, true);

        // fails if WithFinalizer is already loaded (e.g. on a second execution of this test)
        checkForRegisterFinalizeNode(NoFinalizerYet.class, false, true);

        checkForRegisterFinalizeNode(WithFinalizer.class, true, true);
        checkForRegisterFinalizeNode(NoFinalizerYet.class, true, true);
    }
}
