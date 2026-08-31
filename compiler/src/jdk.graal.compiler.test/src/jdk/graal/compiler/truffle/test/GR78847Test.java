/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.List;

import org.graalvm.polyglot.Context;
import org.graalvm.nativeimage.ImageInfo;
import org.junit.Assume;
import org.junit.Test;

import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.test.GCUtils;
import com.oracle.truffle.api.test.SubprocessTestUtils;
import com.oracle.truffle.runtime.OptimizedCallTarget;
import com.oracle.truffle.runtime.OptimizedTruffleRuntime;

import jdk.graal.compiler.truffle.test.GR78847TestFactory.MultiWeakCacheNodeGen;
import jdk.graal.compiler.truffle.test.GR78847TestFactory.WeakCacheNodeGen;

/**
 * Regression test for unbounded deoptimizations caused by weak cached entries.
 */
@SuppressWarnings("truffle-inlining")
public class GR78847Test {

    @Test
    public void testSingleInstanceWeakCacheStabilizes() throws Exception {
        runInSubprocess(() -> {
            Assume.assumeTrue(Truffle.getRuntime() instanceof OptimizedTruffleRuntime);

            Context context = Context.newBuilder() //
                            .option("engine.BackgroundCompilation", "false") //
                            .option("compiler.DeoptCycleDetectionThreshold", "-1") //
                            .build();
            try (context) {
                context.enter();
                OptimizedCallTarget target = (OptimizedCallTarget) new WeakCacheRootNode().getCallTarget();

                for (int i = 0; i < 20; i++) {
                    // 1. Call the node, potentially triggering re-specialization.
                    CachedValue value = new CachedValue();
                    WeakReference<CachedValue> cache = new WeakReference<>(value);
                    assertEquals(42, target.call(value));

                    if (target.isValid()) {
                        // After some number of iterations the compiled code should stabilize.
                        return;
                    }

                    // 2. GC the cached value.
                    value = null;
                    GCUtils.assertGc("The weak cache value was not collected", cache);
                    assertNull(cache.get());

                    // 3. Compile with the referent cleared.
                    target.compile(true);
                    assertTrue("Compilation did not produce valid code", target.isValid());
                }
                throw new AssertionError("The single-instance weak cache did not reach its replacement specialization.");
            }
        });
    }

    @Test
    public void testMultiInstanceWeakCacheStabilizes() throws Exception {
        runInSubprocess(() -> {
            Assume.assumeTrue(Truffle.getRuntime() instanceof OptimizedTruffleRuntime);

            Context context = Context.newBuilder() //
                            .option("engine.BackgroundCompilation", "false") //
                            .option("compiler.DeoptCycleDetectionThreshold", "-1") //
                            .build();
            try (context) {
                context.enter();
                OptimizedCallTarget target = (OptimizedCallTarget) new MultiWeakCacheRootNode().getCallTarget();

                // 1. Activate all instances of the cache.
                CachedValue value0 = new CachedValue();
                CachedValue value1 = new CachedValue();
                CachedValue value2 = new CachedValue();
                WeakReference<CachedValue> ref0 = new WeakReference<>(value0);
                WeakReference<CachedValue> ref1 = new WeakReference<>(value1);
                WeakReference<CachedValue> ref2 = new WeakReference<>(value2);
                target.call(value0);
                target.call(value1);
                target.call(value2);

                // 2. GC the cached values.
                List<WeakReference<CachedValue>> caches = List.of(ref0, ref1, ref2);
                value0 = null;
                value1 = null;
                value2 = null;
                GCUtils.assertGc("The multi-instance weak cache values were not collected", caches);

                for (int i = 0; i < 20; i++) {
                    // 3. Compile with the referents cleared.
                    target.compile(true);
                    assertTrue("Compilation did not produce valid code", target.isValid());

                    // 4. Call with another cached value. The code should eventually stabilize.
                    target.call(new CachedValue());
                    if (target.isValid()) {
                        return;
                    }
                }
                throw new AssertionError("The multi-instance weak cache did not reach its replacement specialization");
            } finally {
                context.close();
            }
        });
    }

    private static void runInSubprocess(Runnable runnable) throws IOException, InterruptedException {
        if (ImageInfo.inImageCode()) {
            runnable.run();
        } else {
            SubprocessTestUtils.newBuilder(GR78847Test.class, runnable).run();
        }
    }

    private static final class WeakCacheRootNode extends RootNode {
        @Child private WeakCacheNode node = WeakCacheNodeGen.create();

        WeakCacheRootNode() {
            super(null);
        }

        @Override
        public Object execute(VirtualFrame frame) {
            return node.execute((CachedValue) frame.getArguments()[0]);
        }
    }

    abstract static class WeakCacheNode extends Node {
        abstract int execute(CachedValue value);

        @Specialization
        static int doCached(CachedValue value, @Cached(value = "value", weak = true) CachedValue cached) {
            return cached.value;
        }

        @Specialization(replaces = "doCached")
        static int doGeneric(CachedValue value) {
            return value.value;
        }
    }

    private static final class MultiWeakCacheRootNode extends RootNode {
        @Child private MultiWeakCacheNode node = MultiWeakCacheNodeGen.create();

        MultiWeakCacheRootNode() {
            super(null);
        }

        @Override
        public Object execute(VirtualFrame frame) {
            return node.execute((CachedValue) frame.getArguments()[0]);
        }
    }

    abstract static class MultiWeakCacheNode extends Node {
        abstract int execute(CachedValue value);

        @Specialization(guards = "cached == value", limit = "3")
        static int doCached(CachedValue value,
                        @Cached(value = "value", weak = true) CachedValue cached) {
            return cached.value;
        }

        @Specialization(replaces = "doCached")
        static int doGeneric(CachedValue value) {
            return value.value;
        }

    }

    static final class CachedValue {
        final int value = 42;
    }

}
