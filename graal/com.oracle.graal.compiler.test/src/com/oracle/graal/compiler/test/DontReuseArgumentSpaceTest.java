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
/*
 */
package com.oracle.graal.compiler.test;

import org.junit.Test;

import com.oracle.graal.code.CompilationResult;
import com.oracle.graal.compiler.phases.HighTier;
import com.oracle.graal.nodes.StructuredGraph;
import com.oracle.graal.nodes.StructuredGraph.AllowAssumptions;
import com.oracle.graal.nodes.ValueNode;
import com.oracle.graal.nodes.graphbuilderconf.GraphBuilderConfiguration;
import com.oracle.graal.nodes.graphbuilderconf.GraphBuilderContext;
import com.oracle.graal.nodes.graphbuilderconf.InlineInvokePlugin;
import com.oracle.graal.options.OptionValue;
import com.oracle.graal.options.OptionValue.OverrideScope;
import com.oracle.graal.phases.tiers.Suites;

import jdk.vm.ci.meta.ResolvedJavaMethod;

public final class DontReuseArgumentSpaceTest extends GraalCompilerTest {

    @Override
    @SuppressWarnings("try")
    protected Suites createSuites() {
        try (OverrideScope scope = OptionValue.override(HighTier.Options.Inline, false)) {
            return super.createSuites();
        }
    }

    @Override
    protected GraphBuilderConfiguration editGraphBuilderConfiguration(GraphBuilderConfiguration conf) {
        // Disable all inlining to make our life easier.
        GraphBuilderConfiguration ret = super.editGraphBuilderConfiguration(conf);
        ret.getPlugins().prependInlineInvokePlugin(new InlineInvokePlugin() {
            @Override
            public InlineInfo shouldInlineInvoke(GraphBuilderContext b, ResolvedJavaMethod method, ValueNode[] args) {
                return InlineInfo.DO_NOT_INLINE_NO_EXCEPTION;
            }
        });
        return ret;
    }

    public static int killArguments(int a, int b, int c, int d, int e, int f, int g, int h, int i, int j) {
        return a + b + c + d + e + f + g + h + i + j;
    }

    public static int callTwice(int a, int b, int c, int d, int e, int f, int g, int h, int i, int j) {
        /*
         * Call the same method twice so the arguments are in the same place each time and might
         * appear to be redundant moves.
         */
        killArguments(a, b, c, d, e, f, g, h, i, j);
        return killArguments(a, b, c, d, e, f, g, h, i, j);
    }

    @Test
    public void run0() throws Throwable {
        /*
         * Exercise the methods once so everything is resolved
         */
        callTwice(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);

        /*
         * Create a standalone compile of killArguments. This test assumes that zapping of argument
         * space is being performed by the backend.
         */
        ResolvedJavaMethod javaMethod = getResolvedJavaMethod("killArguments");
        StructuredGraph graph = parseEager(javaMethod, AllowAssumptions.YES);
        CompilationResult compilationResult = compile(javaMethod, graph);
        getBackend().createDefaultInstalledCode(javaMethod, compilationResult);

        test("callTwice", 1, 2, 3, 4, 5, 6, 7, 8, 9, 10);
    }
}
