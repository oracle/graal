/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
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

import static org.junit.Assume.assumeFalse;

import java.util.concurrent.Semaphore;
import java.util.function.Predicate;

import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.nodes.InvokeWithExceptionNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.calc.AddNode;
import org.graalvm.compiler.nodes.java.AtomicReadAndAddNode;
import org.graalvm.compiler.nodes.java.MethodCallTargetNode;
import org.graalvm.compiler.nodes.memory.ReadNode;
import org.graalvm.compiler.nodes.memory.WriteNode;
import org.graalvm.compiler.truffle.runtime.OptimizedCallTarget;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.ResourceLimits;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.oracle.truffle.api.instrumentation.test.InstrumentationTestLanguage;
import com.oracle.truffle.api.test.polyglot.EngineAPITest;

public class ResourceLimitsCompilationTest extends PartialEvaluationTest {

    @Before
    public void setup() {
        EngineAPITest.resetSingleContextState(true);
    }

    @Test
    @SuppressWarnings("try")
    public void testStatementLimitSingleContext() {
        ResourceLimits limits = ResourceLimits.newBuilder().//
                        statementLimit(5000, null).//
                        build();

        try (Context context = Context.newBuilder().resourceLimits(limits).build();) {
            OptimizedCallTarget target = evalTestScript(context);
            StructuredGraph graph = partialEval(target, new Object[0]);
            Assert.assertEquals(0, countNodes(graph, MethodCallTargetNode.TYPE));
            Assert.assertEquals(6, countNodes(graph, AddNode.TYPE));
            compile(target, graph);
            /*
             * Verify that the statements fold to a single read/write.
             */
            Assert.assertEquals(1, countNodes(graph, ReadNode.TYPE, (n) -> n.getLocationIdentity().toString().equals("PolyglotContextImpl.statementCounter")));
            Assert.assertEquals(1, countNodes(graph, WriteNode.TYPE, (n) -> n.getLocationIdentity().toString().equals("PolyglotContextImpl.statementCounter")));
        }
    }

    @Test
    @SuppressWarnings("try")
    public void testStatementLimitMultiContext() {
        ResourceLimits limits = ResourceLimits.newBuilder().//
                        statementLimit(5000, null).//
                        build();
        try (Engine engine = Engine.create();) {
            try (Context context = Context.newBuilder().engine(engine).resourceLimits(limits).build()) {
                assertLimitCheckFastPath(context);
            }
        }
    }

    @Test
    @SuppressWarnings("try")
    public void testStatementLimitMultiContextTwoEqualConfigs() {
        ResourceLimits limits0 = ResourceLimits.newBuilder().//
                        statementLimit(5000, null).//
                        build();
        ResourceLimits limits1 = ResourceLimits.newBuilder().//
                        statementLimit(5000, null).//
                        build();
        try (Engine engine = Engine.create();) {
            try (Context context = Context.newBuilder().engine(engine).resourceLimits(limits0).build()) {
                assertLimitCheckFastPath(context);
            }
            try (Context context = Context.newBuilder().engine(engine).resourceLimits(limits1).build()) {
                assertLimitCheckFastPath(context);
            }
        }
    }

    @Test
    @SuppressWarnings("try")
    public void testStatementLimitMultiContextTwoDifferentConfigs() {
        ResourceLimits limits0 = ResourceLimits.newBuilder().//
                        statementLimit(5000, null).//
                        build();
        ResourceLimits limits1 = ResourceLimits.newBuilder().//
                        statementLimit(5000 - 1, null).//
                        build();
        try (Engine engine = Engine.create();) {
            try (Context context = Context.newBuilder().engine(engine).resourceLimits(limits0).build()) {
                assertLimitCheckFastPath(context);
            }
            // the code deoptimizes and also reads the limit
            try (Context context = Context.newBuilder().engine(engine).resourceLimits(limits1).build()) {
                OptimizedCallTarget target = evalTestScript(context);
                StructuredGraph graph = partialEval(target, new Object[0]);
                Assert.assertEquals(0, countNodes(graph, MethodCallTargetNode.TYPE));
                Assert.assertEquals(6, countNodes(graph, AddNode.TYPE));
                compile(target, graph);
                /*
                 * Verify that the statements fold to a single read for the context and a single
                 * read/write for the statement counts.
                 */
                Assert.assertEquals(1, countNodes(graph, ReadNode.TYPE, (n) -> n.getLocationIdentity().toString().equals("ContextThreadLocal.activeSingleContextNonVolatile")));
                Assert.assertEquals(1, countNodes(graph, ReadNode.TYPE, (n) -> n.getLocationIdentity().toString().equals("PolyglotContextImpl.statementCounter")));
                Assert.assertEquals(0, countNodes(graph, ReadNode.TYPE, (n) -> n.getLocationIdentity().toString().equals("PolyglotContextImpl.statementLimit")));
                Assert.assertEquals(1, countNodes(graph, WriteNode.TYPE, (n) -> n.getLocationIdentity().toString().equals("PolyglotContextImpl.statementCounter")));
            }
        }
    }

    private void assertLimitCheckFastPath(Context context) {
        OptimizedCallTarget target = evalTestScript(context);
        StructuredGraph graph = partialEval(target, new Object[0]);
        Assert.assertEquals(0, countNodes(graph, MethodCallTargetNode.TYPE));
        Assert.assertEquals(6, countNodes(graph, AddNode.TYPE));
        compile(target, graph);
        /*
         * Verify that the statements fold to a single read for the context and a single read/write
         * for the statement counts.
         */
        Assert.assertEquals(1, countNodes(graph, ReadNode.TYPE, (n) -> n.getLocationIdentity().toString().equals("ContextThreadLocal.activeSingleContextNonVolatile")));
        Assert.assertEquals(1, countNodes(graph, ReadNode.TYPE, (n) -> n.getLocationIdentity().toString().equals("PolyglotContextImpl.statementCounter")));
        Assert.assertEquals(1, countNodes(graph, WriteNode.TYPE, (n) -> n.getLocationIdentity().toString().equals("PolyglotContextImpl.statementCounter")));
        Assert.assertEquals(0, countNodes(graph, ReadNode.TYPE, (n) -> n.getLocationIdentity().toString().equals("PolyglotContextImpl.statementLimit")));
    }

    @Test
    @SuppressWarnings("try")
    public void testStatementLimitEngineMultiThread() throws InterruptedException {
        ResourceLimits limits = ResourceLimits.newBuilder().//
                        statementLimit(5000, null).//
                        build();
        try (Engine engine = Engine.create();) {

            try (Context context0 = Context.newBuilder().engine(engine).resourceLimits(limits).build();
                            Context context1 = Context.newBuilder().engine(engine).resourceLimits(limits).build()) {
                forceEngineMultiThreading(context0, context1);

                OptimizedCallTarget target = evalTestScript(context0);
                StructuredGraph graph = partialEval(target, new Object[0]);
                Assert.assertEquals(1, countNodes(graph, MethodCallTargetNode.TYPE));
                Assert.assertEquals(6, countNodes(graph, AddNode.TYPE));
                compile(target, graph);
                /*
                 * Verify that the statements fold to a single read for the context and a single
                 * read/write for the statement counts.
                 */
                Assert.assertEquals(1, countNodes(graph, ReadNode.TYPE, (n) -> n.getLocationIdentity().toString().equals("PolyglotContextImpl.statementCounter")));
                Assert.assertEquals(1, countNodes(graph, WriteNode.TYPE, (n) -> n.getLocationIdentity().toString().equals("PolyglotContextImpl.statementCounter")));
                Assert.assertEquals(1, countNodes(graph, InvokeWithExceptionNode.TYPE));
            }
        }
    }

    @Test
    @SuppressWarnings("try")
    public void testStatementLimitContextMultiThread() throws InterruptedException {
        assumeFalse("skipping SPARC unsupported test", isSPARC(getBackend().getTarget().arch));

        ResourceLimits limits = ResourceLimits.newBuilder().//
                        statementLimit(5000, null).//
                        build();

        try (Engine engine = Engine.create();) {
            try (Context context = Context.newBuilder().engine(engine).resourceLimits(limits).build()) {
                forceContextMultiThreading(context);

                OptimizedCallTarget target = evalTestScript(context);
                StructuredGraph graph = partialEval(target, new Object[0]);
                Assert.assertEquals(1, countNodes(graph, MethodCallTargetNode.TYPE));
                Assert.assertEquals(6, countNodes(graph, AtomicReadAndAddNode.TYPE));
                compile(target, graph);
                Assert.assertEquals(6, countNodes(graph, AtomicReadAndAddNode.TYPE));
                Assert.assertEquals(1, countNodes(graph, InvokeWithExceptionNode.TYPE));
            }
        }
    }

    /**
     * Make sure using two contexts of the same engine that the engine is accessed from two threads
     * at the same time. The context does not need to support multi-threading for this.
     */
    private static void forceEngineMultiThreading(Context context0, Context context1) throws InterruptedException {
        Assert.assertSame(context0.getEngine(), context1.getEngine());
        Semaphore entered = new Semaphore(0);
        Semaphore leaving = new Semaphore(0);
        Thread t = new Thread(() -> {
            context1.enter();
            entered.release();
            leaving.acquireUninterruptibly();
            context1.leave();
        });
        t.start();
        entered.acquire();
        context0.enter();
        leaving.release();
        context0.leave();
        t.join();
    }

    private static void forceContextMultiThreading(Context context) throws InterruptedException {
        context.enter();
        Thread t = new Thread(() -> {
            context.enter();
            context.leave();
        });
        t.start();
        t.join();
        context.leave();
    }

    private static OptimizedCallTarget evalTestScript(Context context) {
        context.eval(InstrumentationTestLanguage.ID, "ROOT(STATEMENT, STATEMENT, STATEMENT, STATEMENT, STATEMENT, STATEMENT)");
        context.enter();
        return (OptimizedCallTarget) InstrumentationTestLanguage.getLastParsedCalltarget();
    }

    private static <T> int countNodes(StructuredGraph graph, NodeClass<T> nodeClass) {
        return countNodes(graph, nodeClass, (n) -> true);
    }

    @SuppressWarnings("unchecked")
    private static <T> int countNodes(StructuredGraph graph, NodeClass<T> nodeClass, Predicate<T> filter) {
        int count = 0;
        for (Node node : graph.getNodes()) {
            if (nodeClass.getClazz().isInstance(node) && filter.test((T) node)) {
                count++;
            }
        }
        return count;
    }

}
