/*
 * Copyright (c) 2023, 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.nodes.test;

import jdk.graal.compiler.core.test.GraalCompilerTest;
import jdk.graal.compiler.core.common.GraalOptions;
import jdk.graal.compiler.debug.TTY;
import jdk.graal.compiler.nodes.InliningLog;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.loop.LoopPolicies;
import jdk.graal.compiler.options.OptionValues;
import org.junit.Assert;
import org.junit.Test;

import jdk.vm.ci.meta.ResolvedJavaMethod;

public class InliningLogTest extends GraalCompilerTest {

    private interface Foo {
        void bar();
    }

    public static void snippetA(Foo foo) {
        snippetB(foo);
    }

    private static void snippetB(Foo foo) {
        for (int i = 0; i < 2; i++) {
            foo.bar();
        }
    }

    /**
     * Verifies that the invokes created by peeling the loop in {@link #snippetB(Foo)} are siblings
     * of the original call-tree node.
     */
    @Test
    public void duplicatedInvokesAttachedCorrectly() {
        OptionValues optionValues = new OptionValues(getInitialOptions(), GraalOptions.TraceInlining, true, LoopPolicies.Options.PeelALot, true);
        ResolvedJavaMethod method = getResolvedJavaMethod("snippetA");
        StructuredGraph graph = parseEager(method, StructuredGraph.AllowAssumptions.YES, getCompilationId(method), optionValues);
        try (TTY.Filter _ = new TTY.Filter()) {
            compile(method, graph);
        }
        InliningLog inliningLog = graph.getInliningLog();
        InliningLog.Callsite root = inliningLog.getRootCallsite();
        Assert.assertEquals(1, root.getChildren().size());
        Assert.assertTrue(root.getChildren().get(0).getChildren().size() > 1);
    }
}
