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

import java.lang.reflect.Method;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.graalvm.collections.EconomicMap;
import org.graalvm.compiler.core.common.CancellationBailoutException;
import org.graalvm.compiler.core.common.CompilationIdentifier;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.nodes.EncodedGraph;
import org.graalvm.compiler.truffle.common.TruffleInliningPlan;
import org.graalvm.compiler.truffle.compiler.TruffleCompilerImpl;
import org.graalvm.compiler.truffle.options.PolyglotCompilerOptions;
import org.graalvm.compiler.truffle.runtime.DefaultInliningPolicy;
import org.graalvm.compiler.truffle.runtime.GraalTruffleRuntime;
import org.graalvm.compiler.truffle.runtime.OptimizedCallTarget;
import org.graalvm.compiler.truffle.runtime.TruffleInlining;
import org.graalvm.compiler.truffle.test.nodes.AbstractTestNode;
import org.graalvm.compiler.truffle.test.nodes.RootTestNode;
import org.graalvm.polyglot.Context;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleOptions;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.RootNode;

import jdk.vm.ci.code.BailoutException;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import org.graalvm.compiler.options.OptionValues;

@Ignore("GR-26854")
public final class EncodedGraphCacheTest extends PartialEvaluationTest {

    @AfterClass
    public static void resetCompiler() {
        Assume.assumeFalse("This test does not apply to SVM runtime where the compiler is initialized eagerly.", TruffleOptions.AOT);
        try {
            Method m = Truffle.getRuntime().getClass().getMethod("resetCompiler");
            m.invoke(Truffle.getRuntime());
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    @SuppressWarnings("static-method")
    @Before
    public void resetCompilerBefore() {
        resetCompiler();
    }

    private static TruffleCompilerImpl getTruffleCompilerFromRuntime(OptimizedCallTarget callTarget) {
        return (TruffleCompilerImpl) GraalTruffleRuntime.getRuntime().getTruffleCompiler(callTarget);
    }

    @SuppressWarnings("serial")
    static class DummyException extends RuntimeException {
    }

    @SuppressWarnings("serial")
    static class DummyChildException extends DummyException {
        public static void ensureInitialized() {
            // nop: Invalidates HotSpot's leaf class assumption for DummyException.
        }
    }

    static class InvalidationTestNode extends AbstractTestNode {
        @Override
        public int execute(VirtualFrame frame) {
            try {
                boundary();
            } catch (DummyException e) {
                // HotSpot adds a leaf class assumption for DummyException.
                return -1;
            }
            return 42;
        }

        @TruffleBoundary
        private void boundary() {
            // nop
        }
    }

    final ResolvedJavaMethod testMethod = getResolvedJavaMethod(InvalidationTestNode.class, "execute");

    private static RootTestNode rootTestNode() {
        return new RootTestNode(new FrameDescriptor(), "test", new InvalidationTestNode());
    }

    private static boolean encodedGraphCacheContains(TruffleCompilerImpl compiler, ResolvedJavaMethod method) {
        EconomicMap<ResolvedJavaMethod, EncodedGraph> cache = compiler.getPartialEvaluator().getOrCreateEncodedGraphCache();
        return cache.containsKey(method);
    }

    @SuppressWarnings("try")
    private static OptimizedCallTarget compileAST(RootNode rootNode) {
        GraalTruffleRuntime runtime = GraalTruffleRuntime.getRuntime();
        OptimizedCallTarget target = (OptimizedCallTarget) runtime.createCallTarget(rootNode);
        DebugContext debug = new DebugContext.Builder(runtime.getGraalOptions(OptionValues.class)).build();
        try (DebugContext.Scope s = debug.scope("EncodedGraphCacheTest")) {
            CompilationIdentifier compilationId = getTruffleCompilerFromRuntime(target).createCompilationIdentifier(target);
            TruffleInliningPlan inliningPlan = new TruffleInlining(target, new DefaultInliningPolicy());
            getTruffleCompilerFromRuntime(target).compileAST(target.getOptionValues(), debug, target, inliningPlan, compilationId, null, null);
            assertTrue(target.isValid());
            return target;
        }
    }

    private void testHelper(int graphCapacity, int purgeDelay, Consumer<TruffleCompilerImpl> verification) {
        setupContext(Context.newBuilder() //
                        .allowExperimentalOptions(true) //
                        .option("engine.EncodedGraphCacheCapacity", String.valueOf(graphCapacity)) //
                        .option("engine.EncodedGraphCachePurgeDelay", String.valueOf(purgeDelay)) //
                        .option("engine.CompilerIdleDelay", "0"));

        OptimizedCallTarget callTarget = compileAST(rootTestNode());
        TruffleCompilerImpl truffleCompiler = getTruffleCompilerFromRuntime(callTarget);
        verification.accept(truffleCompiler);
    }

    private static void assertEventuallyTrue(String message, int stepMillis, int maxWaitMillis, Supplier<Boolean> condition) {
        for (int totalWait = 0; totalWait <= maxWaitMillis; totalWait += stepMillis) {
            if (condition.get()) {
                return;
            }
            try {
                Thread.sleep(stepMillis);
            } catch (InterruptedException e) {
                throw new AssertionError(message, e);
            }
        }
        fail(message);
    }

    @Test
    public void testCacheInvalidation() {
        setupContext(Context.newBuilder() //
                        .allowExperimentalOptions(true) //
                        .option("engine.EncodedGraphCacheCapacity", "1024") //
                        .option("engine.EncodedGraphCachePurgeDelay", "10000" /* 10s */) //
                        .option("engine.CompilerIdleDelay", "0"));

        RootTestNode rootTestNode = rootTestNode();
        OptimizedCallTarget callTarget = null;
        TruffleCompilerImpl truffleCompiler = null;
        boolean graphWasCached = false;
        for (int attempts = 0; attempts < 10 && !graphWasCached; attempts++) {
            callTarget = compileAST(rootTestNode);
            truffleCompiler = getTruffleCompilerFromRuntime(callTarget);
            // Graph cache can be purged anytime.
            graphWasCached = encodedGraphCacheContains(truffleCompiler, testMethod);
        }
        assertTrue("InvalidationTestNode.execute is cached", graphWasCached);
        Assert.assertNotNull(truffleCompiler);

        // Invalidates HotSpot's leaf class assumption for DummyException.
        DummyChildException.ensureInitialized();

        // Re-compilation should fail on code installation and the cached graph must be
        // invalidated/evicted.
        try {
            compileAST(rootTestNode);
            fail("Should fail on code installation due to invalid dependencies");
        } catch (BailoutException expected) {
            assertFalse(expected.isPermanent());
            assertFalse(expected instanceof CancellationBailoutException);
        }

        assertFalse("InvalidationTestNode.execute was invalidated/evicted",
                        encodedGraphCacheContains(truffleCompiler, testMethod));

        // Retry again, the encoded graph is re-parsed without the (invalidated) assumption.
        graphWasCached = false;
        for (int attempts = 0; attempts < 10 && !graphWasCached; attempts++) {
            // Compilation succeeds.
            callTarget = compileAST(rootTestNode);
            // But the cache can be purged anytime, retry if the graph is not cached.
            graphWasCached = encodedGraphCacheContains(truffleCompiler, testMethod);
        }

        Assert.assertTrue("Re-parsed graph was cached", graphWasCached);
        Assert.assertEquals(42, (int) callTarget.call());
    }

    @Test
    public void testCacheIsDisabled() {
        setupContext(Context.newBuilder() //
                        .allowExperimentalOptions(true) //
                        .option("engine.EncodedGraphCacheCapacity", "0" /* disabled */));

        OptimizedCallTarget callTarget = compileAST(rootTestNode());
        TruffleCompilerImpl truffleCompiler = getTruffleCompilerFromRuntime(callTarget);
        assertFalse("InvalidationTestNode.execute is not cached",
                        encodedGraphCacheContains(truffleCompiler, testMethod));
    }

    @Test
    public void testCacheIsEnabled() {
        boolean[] cacheContainsTargetGraph = {false};
        for (int attempts = 0; attempts < 10 && !cacheContainsTargetGraph[0]; attempts++) {
            testHelper(100, 100_000, compiler -> {
                cacheContainsTargetGraph[0] = encodedGraphCacheContains(compiler, testMethod);
            });
        }
        Assert.assertTrue("InvalidationTestNode.execute is cached", cacheContainsTargetGraph[0]);
    }

    @Test
    public void testCacheCapacity() {
        boolean[] cacheHolds1Element = {false};
        for (int attempts = 0; attempts < 10 && !cacheHolds1Element[0]; attempts++) {
            testHelper(1, 100_000, compiler -> {
                EconomicMap<?, ?> cache = compiler.getPartialEvaluator().getOrCreateEncodedGraphCache();
                // The cache can have at most 1 element, but it can be purged anytime.
                Assert.assertTrue("Cache holds at most 1 element", cache.size() <= 1);
                cacheHolds1Element[0] = (cache.size() == 1); // can be empty
            });
        }
        Assert.assertTrue("Cache holds exactly 1 element", cacheHolds1Element[0]);
    }

    @Test
    public void testUnboundedCacheCapacity() {
        boolean[] nonEmptyGraphCache = {false};
        for (int attempts = 0; attempts < 10 && !nonEmptyGraphCache[0]; attempts++) {
            testHelper(-1, 100_000, compiler -> {
                EconomicMap<?, ?> cache = compiler.getPartialEvaluator().getOrCreateEncodedGraphCache();
                nonEmptyGraphCache[0] = !cache.isEmpty();
            });
        }
        Assert.assertTrue("Unbounded cache was populated", nonEmptyGraphCache[0]);
    }

    @Test
    public void testCacheIsReleased() {
        /*
         * SLOW TEST WARNING!
         *
         * The cache release mechanism is implemented on the compile queue, which at this point, is
         * already initialized and running with the options of the first engine it sees. We assume
         * the default value for EncodedGraphCachePurgeDelay is used, so this test will take at
         * least EncodedGraphCachePurgeDelay milliseconds to finish.
         */
        int purgeDelay = PolyglotCompilerOptions.EncodedGraphCachePurgeDelay.getDefaultValue();

        boolean[] graphWasCached = {false};
        for (int attempts = 0; attempts < 10 && !graphWasCached[0]; attempts++) {
            testHelper(1024, purgeDelay, compiler -> {
                graphWasCached[0] = encodedGraphCacheContains(compiler, testMethod);
                /*
                 * The compile queue can purge the encoded cache at any time, there's no guarantee
                 * that the graph will be cached at this point. If the graph is not cached we just
                 * retry compilation.
                 */
                if (graphWasCached[0]) {
                    assertEventuallyTrue("Encoded graph cache must be dropped after delay elapsed",
                                    1000,
                                    purgeDelay + 2000,
                                    () -> !encodedGraphCacheContains(compiler, testMethod));
                }
            });
        }

        assertTrue("Encoded graph was cached", graphWasCached[0]);
    }
}
