/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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

import org.junit.Assert;
import org.junit.Test;

import com.oracle.graal.compiler.phases.HighTier;
import com.oracle.graal.compiler.test.GraalCompilerTest;
import com.oracle.graal.nodes.StructuredGraph;
import com.oracle.graal.nodes.StructuredGraph.AllowAssumptions;
import com.oracle.graal.nodes.ValueNode;
import com.oracle.graal.nodes.extended.BytecodeExceptionNode;
import com.oracle.graal.nodes.graphbuilderconf.GraphBuilderConfiguration;
import com.oracle.graal.nodes.graphbuilderconf.GraphBuilderConfiguration.BytecodeExceptionMode;
import com.oracle.graal.nodes.graphbuilderconf.GraphBuilderContext;
import com.oracle.graal.nodes.graphbuilderconf.InlineInvokePlugin;
import com.oracle.graal.options.OptionValue;
import com.oracle.graal.options.OptionValue.OverrideScope;
import com.oracle.graal.phases.tiers.Suites;

import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Tests compilation of a hot exception handler.
 */
public class CompiledNullPointerExceptionTest extends GraalCompilerTest {

    @Override
    @SuppressWarnings("try")
    protected Suites createSuites() {
        try (OverrideScope scope = OptionValue.override(HighTier.Options.Inline, false)) {
            return super.createSuites();
        }
    }

    @Override
    protected GraphBuilderConfiguration editGraphBuilderConfiguration(GraphBuilderConfiguration conf) {
        GraphBuilderConfiguration ret = super.editGraphBuilderConfiguration(conf);
        ret.getPlugins().prependInlineInvokePlugin(new InlineInvokePlugin() {
            @Override
            public InlineInfo shouldInlineInvoke(GraphBuilderContext b, ResolvedJavaMethod method, ValueNode[] args) {
                return InlineInfo.DO_NOT_INLINE_NO_EXCEPTION;
            }
        });
        return ret.withBytecodeExceptionMode(BytecodeExceptionMode.CheckAll);
    }

    @Override
    protected StructuredGraph parseEager(ResolvedJavaMethod m, AllowAssumptions allowAssumptions) {
        StructuredGraph graph = super.parseEager(m, allowAssumptions);
        int handlers = graph.getNodes().filter(BytecodeExceptionNode.class).count();
        Assert.assertEquals(1, handlers);
        return graph;
    }

    private class TestClass {

        @Override
        public String toString() {
            return "TestClass";
        }
    }

    @Test
    public void test() {
        test("testSnippet", (TestClass) null, "object2");
        test("testSnippet", new TestClass(), "object2");
    }

    public static String testSnippet(TestClass o, Object o2) {
        try {
            return o.toString();
        } catch (NullPointerException e) {
            return String.valueOf(o2);
        }
    }
}
