/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.replacements.test;

import org.junit.Test;

import com.oracle.graal.api.directives.GraalDirectives;
import com.oracle.graal.api.replacements.ClassSubstitution;
import com.oracle.graal.api.replacements.Fold;
import com.oracle.graal.api.replacements.MethodSubstitution;
import com.oracle.graal.compiler.test.GraalCompilerTest;
import com.oracle.graal.nodes.ReturnNode;
import com.oracle.graal.nodes.StartNode;
import com.oracle.graal.nodes.StructuredGraph;
import com.oracle.graal.nodes.graphbuilderconf.GraphBuilderConfiguration.Plugins;
import com.oracle.graal.nodes.graphbuilderconf.NodeIntrinsicPluginFactory.InjectionProvider;
import com.oracle.graal.replacements.NodeIntrinsificationProvider;

public class FoldTest extends GraalCompilerTest {

    private static class TestMethod {

        public static int test() {
            return 42;
        }
    }

    static class FoldUtils {

        private int number;

        FoldUtils(int number) {
            this.number = number;
        }

        @Fold
        static int multiply(int a, int b) {
            // we want to test whether @Fold works, so prevent automatic constant folding
            return a * GraalDirectives.opaque(b);
        }

        @Fold
        int getNumber() {
            // we want to test whether @Fold works, so prevent automatic constant folding
            return GraalDirectives.opaque(number);
        }
    }

    @ClassSubstitution(TestMethod.class)
    private static class TestMethodSubstitution {

        private static final FoldUtils utils = new FoldUtils(21);

        @MethodSubstitution
        public static int test() {
            return FoldUtils.multiply(utils.getNumber(), 2);
        }
    }

    private static boolean substitutionsInstalled;

    public FoldTest() {
        if (!substitutionsInstalled) {
            getProviders().getReplacements().registerSubstitutions(TestMethod.class, TestMethodSubstitution.class);
            substitutionsInstalled = true;
        }
    }

    public static int callTest() {
        return TestMethod.test();
    }

    @Override
    protected Plugins getDefaultGraphBuilderPlugins() {
        Plugins ret = super.getDefaultGraphBuilderPlugins();
        // manually register generated factories, jvmci service providers don't work from unit tests
        InjectionProvider injection = new NodeIntrinsificationProvider(getMetaAccess(), getSnippetReflection(), getProviders().getForeignCalls(), null);
        new FoldFactory_FoldTest_FoldUtils_getNumber().registerPlugin(ret.getInvocationPlugins(), injection);
        new FoldFactory_FoldTest_FoldUtils_multiply_255f288().registerPlugin(ret.getInvocationPlugins(), injection);
        return ret;
    }

    @Override
    protected boolean checkHighTierGraph(StructuredGraph graph) {
        // check that folding happened correctly
        StartNode start = graph.start();
        assert start.next() instanceof ReturnNode : "expected ReturnNode, got " + start.next();

        ReturnNode ret = (ReturnNode) start.next();
        assert ret.result().isConstant() : "expected ConstantNode, got " + ret.result();
        return true;
    }

    @Test
    public void snippetTest() {
        test("callTest");
    }
}
