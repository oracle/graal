/*
 * Copyright (c) 2011, 2021, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.core.test.deopt;

import org.graalvm.compiler.api.directives.GraalDirectives;
import org.graalvm.compiler.core.test.GraalCompilerTest;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.StructuredGraph.AllowAssumptions;
import org.graalvm.compiler.phases.common.DeadCodeEliminationPhase;
import org.junit.Assert;
import org.junit.Test;

import jdk.vm.ci.code.InstalledCode;
import jdk.vm.ci.code.InvalidInstalledCodeException;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;

public class CompiledMethodTest extends GraalCompilerTest {

    public static Object testMethod(Object arg1, Object arg2, Object arg3) {
        String res = arg1 + " " + arg2 + " " + arg3;
        return GraalDirectives.inCompiledCode() ? res : "interpreter";
    }

    Object f1;

    public Object testMethodVirtual(Object arg1, Object arg2, Object arg3) {
        return f1 + " " + arg1 + " " + arg2 + " " + arg3;
    }

    /**
     * Usages of the constant {@code " "} are replaced with the constant {@code "-"} and it is
     * verified that executing the compiled code produces a result that the preserves the node
     * replacement unless deoptimization occurs (e.g., due to -Xcomp causing profiles to be
     * missing).
     */
    @Test
    public void test1() {
        final ResolvedJavaMethod javaMethod = getResolvedJavaMethod("testMethod");
        final StructuredGraph graph = parseEager(javaMethod, AllowAssumptions.NO);
        createCanonicalizerPhase().apply(graph, getProviders());
        new DeadCodeEliminationPhase().apply(graph);

        for (ConstantNode node : ConstantNode.getConstantNodes(graph)) {
            if (node.getStackKind() == JavaKind.Object && " ".equals(getSnippetReflection().asObject(String.class, node.asJavaConstant()))) {
                node.replace(graph, ConstantNode.forConstant(getSnippetReflection().forObject("-"), getMetaAccess(), graph));
            }
        }

        InstalledCode compiledMethod = getCode(javaMethod, graph, true);
        try {
            Object result = compiledMethod.executeVarargs("1", "2", "3");
            String expected = "1-2-3";
            checkResult(result, expected);
        } catch (InvalidInstalledCodeException t) {
            Assert.fail("method invalidated");
        }
    }

    private static void checkResult(Object result, String expected) {
        if (!expected.equals(result)) {
            // Deoptimization probably occurred
            Assert.assertEquals("interpreter", result);
        }
    }

    @Test
    public void test3() {
        final ResolvedJavaMethod javaMethod = getResolvedJavaMethod("testMethod");
        InstalledCode compiledMethod = getCode(javaMethod);
        try {
            Object result = compiledMethod.executeVarargs("1", "2", "3");
            checkResult(result, "1 2 3");
        } catch (InvalidInstalledCodeException t) {
            Assert.fail("method invalidated");
        }
    }

    @Test
    public void test4() {
        final ResolvedJavaMethod javaMethod = getResolvedJavaMethod("testMethodVirtual");
        InstalledCode compiledMethod = getCode(javaMethod);
        try {
            f1 = "0";
            Object result = compiledMethod.executeVarargs(this, "1", "2", "3");
            checkResult(result, "0 1 2 3");
        } catch (InvalidInstalledCodeException t) {
            Assert.fail("method invalidated");
        }
    }
}
