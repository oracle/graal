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

import org.junit.*;

import com.oracle.max.cri.ci.*;
import com.oracle.max.cri.ri.*;
import com.oracle.max.cri.ri.RiCompiledMethod.MethodInvalidatedException;
import com.oracle.max.graal.compiler.phases.*;
import com.oracle.max.graal.graph.*;
import com.oracle.max.graal.nodes.*;

/**
 * In the following tests, the usages of local variable "a" are replaced with the integer constant 0. Then
 * canonicalization is applied and it is verified that the resulting graph is equal to the graph of the method that just
 * has a "return 1" statement in it.
 */
public class CompiledMethodTest extends GraphTest {

    public static Object testMethod(Object arg1, Object arg2, Object arg3) {
        return arg1 + " " + arg2 + " " + arg3;
    }

    @Test
    public void test1() {
        Method method = getMethod("testMethod");
        final StructuredGraph graph = parse(method);
        new CanonicalizerPhase(null, runtime(), null).apply(graph);
        new DeadCodeEliminationPhase().apply(graph);

        for (Node node : graph.getNodes()) {
            if (node instanceof ConstantNode) {
                ConstantNode constant = (ConstantNode) node;
                if (constant.kind() == CiKind.Object && " ".equals(constant.value.asObject())) {
                    graph.replaceFloating(constant, ConstantNode.forObject("-", runtime, graph));
                }
            }
        }

        final RiResolvedMethod riMethod = runtime.getRiMethod(method);
        CiTargetMethod targetMethod = runtime.compile(riMethod, graph);
        RiCompiledMethod compiledMethod = runtime.addMethod(riMethod, targetMethod);
        try {
            Object result = compiledMethod.execute("1", "2", "3");
            Assert.assertEquals("1-2-3", result);
        } catch (MethodInvalidatedException t) {
            Assert.fail("method invalidated");
        }

    }
}
