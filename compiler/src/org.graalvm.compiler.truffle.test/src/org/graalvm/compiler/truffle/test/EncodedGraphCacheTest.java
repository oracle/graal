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
package org.graalvm.compiler.truffle.test;

import java.lang.reflect.Method;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.graalvm.collections.EconomicMap;
import org.graalvm.compiler.core.common.CancellationBailoutException;
import org.graalvm.compiler.core.common.CompilationIdentifier;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.nodes.EncodedGraph;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.truffle.common.TruffleCompilationTask;
import org.graalvm.compiler.truffle.common.TruffleInliningData;
import org.graalvm.compiler.truffle.compiler.PartialEvaluator;
import org.graalvm.compiler.truffle.compiler.TruffleCompilerImpl;
import org.graalvm.compiler.truffle.options.PolyglotCompilerOptions;
import org.graalvm.compiler.truffle.runtime.GraalTruffleRuntime;
import org.graalvm.compiler.truffle.runtime.OptimizedCallTarget;
import org.graalvm.compiler.truffle.runtime.TruffleInlining;
import org.graalvm.compiler.truffle.test.nodes.AbstractTestNode;
import org.graalvm.compiler.truffle.test.nodes.RootTestNode;
import org.graalvm.polyglot.Context;
import org.junit.Assert;
import org.junit.Test;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.RootNode;

import jdk.vm.ci.code.BailoutException;
import jdk.vm.ci.meta.ResolvedJavaMethod;

public final class EncodedGraphCacheTest extends PartialEvaluationTest {

    public static void purgeEncodedGraphCache(TruffleCompilerImpl truffleCompiler) {
        try {
            PartialEvaluator pe = truffleCompiler.getPartialEvaluator();
            Method m = pe.getClass().getMethod("purgeEncodedGraphCache");
            m.invoke(pe);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    public static boolean disableEncodedGraphCachePurges(TruffleCompilerImpl truffleCompiler, boolean value) {
        try {
            PartialEvaluator pe = truffleCompiler.getPartialEvaluator();
            Method m = pe.getClass().getMethod("disableEncodedGraphCachePurges", boolean.class);
            return (boolean) m.invoke(pe, value);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
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

    private static boolean encodedGraphCacheContains(TruffleCompilerImpl compiler, ResolvedJavaMethod method, boolean encodedGraphCache) {
        EconomicMap<ResolvedJavaMethod, EncodedGraph> cache = compiler.getPartialEvaluator().getOrCreateEncodedGraphCache(encodedGraphCache);
        return cache.containsKey(method);
    }

    @SuppressWarnings("try")
    private static OptimizedCallTarget compileAST(RootNode rootNode) {
        OptimizedCallTarget target = (OptimizedCallTarget) rootNode.getCallTarget();
        DebugContext debug = new DebugContext.Builder(GraalTruffleRuntime.getRuntime().getGraalOptions(OptionValues.class)).build();
        try (DebugContext.Scope s = debug.scope("EncodedGraphCacheTest")) {
            CompilationIdentifier compilationId = getTruffleCompilerFromRuntime(target).createCompilationIdentifier(target);
            getTruffleCompilerFromRuntime(target).compileAST(target.getOptionValues(), debug, target, compilationId,
                            new TruffleCompilerImpl.CancellableTruffleCompilationTask(new TruffleCompilationTask() {
                                private TruffleInliningData inlining = new TruffleInlining();

                                @Override
                                public boolean isCancelled() {
                                    return false;
                                }

                                @Override
                                public boolean isLastTier() {
                                    return true;
                                }

                                @Override
                                public TruffleInliningData inliningData() {
                                    return inlining;
                                }

                                @Override
                                public boolean hasNextTier() {
                                    return false;
                                }
                            }), null);
            assertTrue(target.isValid());
            return target;
        }
    }

    private void testHelper(boolean enableEncodedGraphCache, int purgeDelay, Consumer<TruffleCompilerImpl> verification) {
        setupContext(Context.newBuilder() //
                        .allowExperimentalOptions(true) //
                        .option("engine.EncodedGraphCache", String.valueOf(enableEncodedGraphCache)) //
                        .option("engine.EncodedGraphCachePurgeDelay", String.valueOf(purgeDelay)) //
                        .option("engine.CompilerIdleDelay", "0") //
                        .option("engine.BackgroundCompilation", "false") //
                        .option("engine.CompileImmediately", "true"));
        OptimizedCallTarget callTarget = compileAST(rootTestNode());
        TruffleCompilerImpl truffleCompiler = getTruffleCompilerFromRuntime(callTarget);
        verification.accept(truffleCompiler);

        // Purge cache after checks.
        boolean previousValue = disableEncodedGraphCachePurges(truffleCompiler, false);
        purgeEncodedGraphCache(truffleCompiler);
        disableEncodedGraphCachePurges(truffleCompiler, previousValue);
    }

    private static boolean checkEventuallyTrue(int stepMillis, int maxWaitMillis, Supplier<Boolean> condition) {
        for (int totalWait = 0; totalWait <= maxWaitMillis; totalWait += stepMillis) {
            if (condition.get()) {
                return true;
            }
            try {
                Thread.sleep(stepMillis);
            } catch (InterruptedException e) {
                throw new AssertionError(e);
            }
        }
        return false;
    }

    @Test
    public void testCacheInvalidation() {
        boolean encodedGraphCache = true;
        setupContext(Context.newBuilder() //
                        .allowExperimentalOptions(true) //
                        .option("engine.EncodedGraphCache", String.valueOf(encodedGraphCache)) //
                        .option("engine.EncodedGraphCachePurgeDelay", "10000" /* 10s */) //
                        .option("engine.CompilerIdleDelay", "0"));

        RootTestNode rootTestNode = rootTestNode();
        OptimizedCallTarget callTarget = null;
        TruffleCompilerImpl truffleCompiler = null;
        boolean graphWasCached = false;
        for (int attempts = 0; attempts < 10 && !graphWasCached; attempts++) {
            callTarget = compileAST(rootTestNode);
            truffleCompiler = getTruffleCompilerFromRuntime(callTarget);
            // Disable graph cache purges to reduce transient failures with many compiler threads.
            disableEncodedGraphCachePurges(truffleCompiler, true);
            graphWasCached = encodedGraphCacheContains(truffleCompiler, testMethod, encodedGraphCache);
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
                        encodedGraphCacheContains(truffleCompiler, testMethod, encodedGraphCache));

        // Retry again, the encoded graph is re-parsed without the (invalidated) assumption.
        callTarget = compileAST(rootTestNode);
        // Cache purges are disabled.
        graphWasCached = encodedGraphCacheContains(truffleCompiler, testMethod, encodedGraphCache);

        Assert.assertTrue("Re-parsed graph was cached", graphWasCached);
        Assert.assertEquals(42, (int) callTarget.call());
    }

    @Test
    public void testCacheIsDisabled() {
        boolean encodedGraphCache = false;
        setupContext(Context.newBuilder() //
                        .allowExperimentalOptions(true) //
                        .option("engine.EncodedGraphCache", String.valueOf(encodedGraphCache)));

        OptimizedCallTarget callTarget = compileAST(rootTestNode());
        TruffleCompilerImpl truffleCompiler = getTruffleCompilerFromRuntime(callTarget);

        // Reset state.
        disableEncodedGraphCachePurges(truffleCompiler, false);
        purgeEncodedGraphCache(truffleCompiler);
        disableEncodedGraphCachePurges(truffleCompiler, true);

        callTarget = compileAST(rootTestNode());
        truffleCompiler = getTruffleCompilerFromRuntime(callTarget);

        assertFalse("InvalidationTestNode.execute is not cached",
                        encodedGraphCacheContains(truffleCompiler, testMethod, true));
    }

    @Test
    public void testCacheIsEnabled() {
        boolean[] cacheContainsTargetGraph = {false};
        for (int attempts = 0; attempts < 10 && !cacheContainsTargetGraph[0]; attempts++) {
            boolean encodedGraphCache = true;
            testHelper(encodedGraphCache, 100_000, compiler -> {
                disableEncodedGraphCachePurges(compiler, true);
                cacheContainsTargetGraph[0] = encodedGraphCacheContains(compiler, testMethod, encodedGraphCache);
            });
        }
        Assert.assertTrue("InvalidationTestNode.execute is cached", cacheContainsTargetGraph[0]);
    }

    @Test
    public void testUnboundedCacheCapacity() {
        boolean[] nonEmptyGraphCache = {false};
        for (int attempts = 0; attempts < 10 && !nonEmptyGraphCache[0]; attempts++) {
            boolean encodedGraphCache = true;
            testHelper(encodedGraphCache, 100_000, compiler -> {
                disableEncodedGraphCachePurges(compiler, true);
                EconomicMap<?, ?> cache = compiler.getPartialEvaluator().getOrCreateEncodedGraphCache(encodedGraphCache);
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

        boolean[] cacheWasPurged = {false};
        for (int attempts = 0; attempts < 10 && !cacheWasPurged[0]; attempts++) {
            boolean encodedGraphCache = true;
            testHelper(encodedGraphCache, purgeDelay, compiler -> {
                // Ensure that the graph cache is empty.
                boolean previousValue = disableEncodedGraphCachePurges(compiler, false);
                purgeEncodedGraphCache(compiler);
                disableEncodedGraphCachePurges(compiler, previousValue);
                assertFalse(encodedGraphCacheContains(compiler, testMethod, encodedGraphCache));

                // Submit a regular compilation, so a compiler thread will then timeout and trigger
                // a cache purge e.g. do not compile in the main thread with compileAST.
                OptimizedCallTarget callTarget = (OptimizedCallTarget) rootTestNode().getCallTarget();
                callTarget.compile(true);

                boolean graphWasCached = encodedGraphCacheContains(compiler, testMethod, encodedGraphCache);
                /*
                 * The cache can be purged at any time, there's no guarantee that the graph will be
                 * cached at this point. If the graph is not cached we just retry compilation.
                 */
                if (graphWasCached) {
                    cacheWasPurged[0] = checkEventuallyTrue(
                                    100,
                                    purgeDelay + 5000,
                                    () -> !encodedGraphCacheContains(compiler, testMethod, encodedGraphCache));
                }
            });
        }

        assertTrue("Encoded graph cache was dropped after delay elapsed", cacheWasPurged[0]);
    }
}
