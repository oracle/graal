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
package com.oracle.graal.replacements.test;

import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.ResolvedJavaMethod;

import org.junit.Assert;
import org.junit.Test;

import com.oracle.graal.compiler.common.GraalOptions;
import com.oracle.graal.compiler.phases.HighTier;
import com.oracle.graal.compiler.test.GraalCompilerTest;
import com.oracle.graal.graph.iterators.NodePredicates;
import com.oracle.graal.nodes.StructuredGraph;
import com.oracle.graal.nodes.StructuredGraph.AllowAssumptions;
import com.oracle.graal.nodes.ValueNode;
import com.oracle.graal.nodes.extended.BytecodeExceptionNode;
import com.oracle.graal.nodes.graphbuilderconf.GraphBuilderConfiguration;
import com.oracle.graal.nodes.graphbuilderconf.GraphBuilderContext;
import com.oracle.graal.nodes.graphbuilderconf.InlineInvokePlugin;
import com.oracle.graal.nodes.java.ExceptionObjectNode;
import com.oracle.graal.options.OptionValue;
import com.oracle.graal.options.OptionValue.OverrideScope;
import com.oracle.graal.phases.tiers.Suites;

/**
 * Tests compilation of a hot exception handler.
 */
public class CompiledExceptionHandlerTest extends GraalCompilerTest {

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

            public InlineInfo shouldInlineInvoke(GraphBuilderContext b, ResolvedJavaMethod method, ValueNode[] args, JavaType returnType) {
                if (method.getName().startsWith("raiseException")) {
                    /*
                     * Make sure the raiseException* method invokes are not inlined and compiled
                     * with explicit exception handler.
                     */
                    return InlineInfo.DO_NOT_INLINE_WITH_EXCEPTION;
                } else {
                    /*
                     * We don't care whether other invokes are inlined or not, but we definitely
                     * don't want another explicit exception handler in the graph.
                     */
                    return InlineInfo.DO_NOT_INLINE_NO_EXCEPTION;
                }
            }
        });
        return ret;
    }

    @Override
    protected StructuredGraph parseEager(ResolvedJavaMethod m, AllowAssumptions allowAssumptions) {
        StructuredGraph graph = super.parseEager(m, allowAssumptions);
        int handlers = graph.getNodes().filter(NodePredicates.isA(ExceptionObjectNode.class).or(BytecodeExceptionNode.class)).count();
        Assert.assertEquals(1, handlers);
        return graph;
    }

    private static void raiseExceptionSimple(String s) {
        throw new RuntimeException("Raising exception with message \"" + s + "\"");
    }

    @Test
    public void test1() {
        test("test1Snippet", "a string");
        test("test1Snippet", (String) null);
    }

    public static String test1Snippet(String message) {
        if (message != null) {
            try {
                raiseExceptionSimple(message);
            } catch (Exception e) {
                return message + e.getMessage();
            }
        }
        return null;
    }

    private static void raiseException(String m1, String m2, String m3, String m4, String m5) {
        throw new RuntimeException(m1 + m2 + m3 + m4 + m5);
    }

    @Test
    public void test2() {
        test("test2Snippet", "m1", "m2", "m3", "m4", "m5");
        test("test2Snippet", null, "m2", "m3", "m4", "m5");
    }

    public static String test2Snippet(String m1, String m2, String m3, String m4, String m5) {
        if (m1 != null) {
            try {
                raiseException(m1, m2, m3, m4, m5);
            } catch (Exception e) {
                return m5 + m4 + m3 + m2 + m1;
            }
        }
        return m4 + m3;
    }

    @Test
    @SuppressWarnings("try")
    public void test3() {
        try (OverrideScope s = OptionValue.override(GraalOptions.StressExplicitExceptionCode, true)) {
            test("test3Snippet", (Object) null, "object2");
            test("test3Snippet", "object1", "object2");
        }
    }

    public static String test3Snippet(Object o, Object o2) {
        try {
            return o.toString();
        } catch (NullPointerException e) {
            return String.valueOf(o2);
        }
    }
}
