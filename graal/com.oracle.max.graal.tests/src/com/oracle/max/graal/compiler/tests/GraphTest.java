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
package com.oracle.max.graal.compiler.tests;

import java.lang.reflect.*;

import junit.framework.Assert;

import com.oracle.max.cri.ri.*;
import com.oracle.max.graal.compiler.phases.*;
import com.oracle.max.graal.compiler.phases.PhasePlan.*;
import com.oracle.max.graal.cri.*;
import com.oracle.max.graal.debug.*;
import com.oracle.max.graal.java.*;
import com.oracle.max.graal.nodes.*;

/**
 * Base class for Graal compiler unit tests. These are white box tests
 * for Graal compiler transformations. The general pattern for a test is:
 * <ol>
 * <li>Create a graph by {@linkplain #parse(String) parsing} a method.</li>
 * <li>Manually modify the graph (e.g. replace a paramter node with a constant).</li>
 * <li>Apply a transformation to the graph.</li>
 * <li>Assert that the transformed graph is equal to an expected graph.</li>
 * </ol>
 * <p>
 * See {@link InvokeTest} as an example.
 * <p>
 * The tests can be run in Eclipse with the "Compiler Unit Test" Eclipse
 * launch configuration found in the top level of this project or by
 * running {@code mx gcut} on the command line.
 */
public abstract class GraphTest {

    protected final GraalRuntime runtime;

    public GraphTest() {
        this.runtime = GraalRuntimeAccess.getGraalRuntime();
    }

    protected void assertEquals(StructuredGraph expected, StructuredGraph graph) {
        if (expected.getNodeCount() != graph.getNodeCount()) {
            Debug.dump(expected, "Node count not matching - expected");
            Debug.dump(graph, "Node count not matching - actual");
            Assert.fail("Graphs do not have the same number of nodes");
        }
    }

    protected GraalRuntime runtime() {
        return runtime;
    }

    /**
     * Parses a Java method to produce a graph.
     *
     * @param methodName the name of the method in {@code this.getClass()} to be parsed
     */
    protected StructuredGraph parse(String methodName) {
        Method found = null;
        for (Method m : this.getClass().getMethods()) {
            if (m.getName().equals(methodName)) {
                Assert.assertNull(found);
                found = m;
            }
        }
        return parse(found);
    }

    /**
     * Parses a Java method to produce a graph.
     */
    protected StructuredGraph parse(Method m) {
        RiResolvedMethod riMethod = runtime.getRiMethod(m);
        StructuredGraph graph = new StructuredGraph(riMethod);
        new GraphBuilderPhase(runtime, GraphBuilderConfiguration.getDeoptFreeDefault()).apply(graph);
        return graph;
    }

    protected PhasePlan getDefaultPhasePlan() {
        PhasePlan plan = new PhasePlan();
        plan.addPhase(PhasePosition.AFTER_PARSING, new GraphBuilderPhase(runtime, GraphBuilderConfiguration.getDeoptFreeDefault()));
        return plan;
    }
}
