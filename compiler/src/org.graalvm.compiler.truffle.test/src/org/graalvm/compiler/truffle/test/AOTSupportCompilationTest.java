/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.test;

import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.java.MethodCallTargetNode;
import org.graalvm.compiler.truffle.runtime.GraalTruffleRuntime;
import org.graalvm.compiler.truffle.runtime.OptimizedCallTarget;
import org.graalvm.polyglot.Context;
import org.junit.Test;
import org.openjdk.jmh.annotations.TearDown;

import com.oracle.truffle.api.dsl.test.AOTSupportTest;
import com.oracle.truffle.api.dsl.test.AOTSupportTest.TestLanguage;
import com.oracle.truffle.api.dsl.test.AOTSupportTest.TestRootNode;

public class AOTSupportCompilationTest extends PartialEvaluationTest {

    private Context context;

    public AOTSupportCompilationTest() {
        preventProfileCalls = true;
    }

    @Test
    public void testAOTCompilation() {
        TestRootNode root = setup();
        OptimizedCallTarget target = (OptimizedCallTarget) root.getCallTarget();
        context.enter();
        target.compile(true);
        assertTrue(target.isValidLastTier());
        target.call();
        context.leave();
        assertTrue(target.isValidLastTier());
    }

    @Test
    public void testGraph() {
        TestRootNode root = setup();
        OptimizedCallTarget target = (OptimizedCallTarget) root.getCallTarget();
        context.enter();
        StructuredGraph graph = partialEval(target, new Object[0]);
        for (MethodCallTargetNode call : graph.getNodes(MethodCallTargetNode.TYPE)) {
            throw new AssertionError(call.toString());
        }
        context.leave();
    }

    private TestRootNode setup() {
        context = Context.newBuilder().allowExperimentalOptions(true) //
                        .option("engine.CompileImmediately", "true") //
                        .option("engine.BackgroundCompilation", "false").build();
        context.initialize(AOTSupportTest.LANGUAGE_ID);
        context.enter();
        TestRootNode root = new TestRootNode(TestLanguage.getCurrentLanguage());
        GraalTruffleRuntime.getRuntime().createCallTarget(root);
        context.leave();
        return root;
    }

    @TearDown
    public void tearDown() {
        context.close();
    }

}
