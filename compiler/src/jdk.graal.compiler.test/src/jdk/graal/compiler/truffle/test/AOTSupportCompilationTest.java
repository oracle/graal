/*
 * Copyright (c) 2020, 2022, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.truffle.test;

import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.java.MethodCallTargetNode;
import org.graalvm.polyglot.Context;
import org.junit.Test;
import org.openjdk.jmh.annotations.TearDown;

import com.oracle.truffle.api.dsl.test.AOTSupportTest;
import com.oracle.truffle.api.dsl.test.AOTSupportTest.AOTDynamicDispatch;
import com.oracle.truffle.api.dsl.test.AOTSupportTest.AOTDynamicDispatchTarget;
import com.oracle.truffle.api.dsl.test.AOTSupportTest.AOTInitializable;
import com.oracle.truffle.api.dsl.test.AOTSupportTest.BaseNode;
import com.oracle.truffle.api.dsl.test.AOTSupportTest.TestLanguage;
import com.oracle.truffle.api.dsl.test.AOTSupportTest.TestRootNode;
import com.oracle.truffle.api.dsl.test.AOTSupportTestFactory.AOTAutoLibraryNodeGen;
import com.oracle.truffle.api.dsl.test.AOTSupportTestFactory.AOTManualLibraryNodeGen;
import com.oracle.truffle.api.dsl.test.AOTSupportTestFactory.AOTManualLibrarySingleLimitNodeGen;
import com.oracle.truffle.api.dsl.test.AOTSupportTestFactory.TestNodeGen;
import com.oracle.truffle.api.test.polyglot.ProxyLanguage;
import com.oracle.truffle.runtime.OptimizedCallTarget;

public class AOTSupportCompilationTest extends PartialEvaluationTest {

    private Context context;

    public AOTSupportCompilationTest() {
        preventProfileCalls = true;
    }

    @Test
    public void testAOTCompilation() {
        assertValidCompilation(TestNodeGen.create(false), null);

        assertValidCompilation(AOTAutoLibraryNodeGen.create(), new AOTInitializable());
        assertValidCompilation(AOTManualLibraryNodeGen.create(), new AOTInitializable());
        assertValidCompilation(AOTManualLibrarySingleLimitNodeGen.create(), new AOTInitializable());

        assertValidCompilation(AOTAutoLibraryNodeGen.create(), new AOTDynamicDispatch(AOTDynamicDispatchTarget.class));
        assertValidCompilation(AOTManualLibraryNodeGen.create(), new AOTDynamicDispatch(AOTDynamicDispatchTarget.class));
        assertValidCompilation(AOTManualLibrarySingleLimitNodeGen.create(), new AOTDynamicDispatch(AOTDynamicDispatchTarget.class));
    }

    private void assertValidCompilation(BaseNode node, Object receiver) {
        TestRootNode root = setup(node, receiver);
        OptimizedCallTarget target = (OptimizedCallTarget) root.getCallTarget();
        target.prepareForAOT();

        context.enter();
        target.compile(true);
        assertTrue(target.isValidLastTier());
        target.call();
        context.leave();
        assertTrue(target.isValidLastTier());
    }

    @Test
    public void testGraph() {
        assertGraphNoCalls(TestNodeGen.create(false), null);

        assertGraphNoCalls(AOTAutoLibraryNodeGen.create(), new AOTInitializable());
        assertGraphNoCalls(AOTManualLibraryNodeGen.create(), new AOTInitializable());
        assertGraphNoCalls(AOTManualLibrarySingleLimitNodeGen.create(), new AOTInitializable());

        assertGraphNoCalls(AOTManualLibrarySingleLimitNodeGen.create(), new AOTDynamicDispatch(AOTDynamicDispatchTarget.class));
        assertGraphNoCalls(AOTAutoLibraryNodeGen.create(), new AOTDynamicDispatch(AOTDynamicDispatchTarget.class));
        assertGraphNoCalls(AOTManualLibraryNodeGen.create(), new AOTDynamicDispatch(AOTDynamicDispatchTarget.class));
    }

    private void assertGraphNoCalls(BaseNode node, Object receiver) throws AssertionError {
        TestRootNode root = setup(node, receiver);
        OptimizedCallTarget target = (OptimizedCallTarget) root.getCallTarget();
        context.enter();
        StructuredGraph graph = partialEval(target, new Object[0]);
        for (MethodCallTargetNode call : graph.getNodes(MethodCallTargetNode.TYPE)) {
            getDebugContext().forceDump(graph, "error");
            throw new AssertionError(call.toString());
        }
        context.leave();
    }

    private TestRootNode setup(BaseNode node, Object receiver) {
        if (context != null) {
            context.close();
            context = null;
        }
        context = Context.newBuilder().allowExperimentalOptions(true) //
                        .option("engine.CompileImmediately", "true") //
                        .option("engine.BackgroundCompilation", "false").build();
        context.initialize(AOTSupportTest.LANGUAGE_ID);
        context.initialize(ProxyLanguage.ID);
        context.enter();
        TestRootNode root = new TestRootNode(TestLanguage.getCurrentLanguage(), node, receiver);
        root.getCallTarget();
        context.leave();
        return root;
    }

    @TearDown
    public void tearDown() {
        context.close();
    }

}
