/*
 * Copyright (c) 2011, 2021, Oracle and/or its affiliates. All rights reserved.
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

import org.junit.Assert;
import org.junit.Test;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.runtime.OptimizedTruffleRuntime;
import com.oracle.truffle.runtime.OptimizedCallTarget;
import com.oracle.truffle.runtime.OptimizedRuntimeOptions;

public class EngineModeTest extends TestWithSynchronousCompiling {

    public static final String ROOT = "root";
    public static final String LATENCY = "latency";
    public static final String MODE = "engine.Mode";

    private static void compileAndAssertLatency(OptimizedCallTarget target) {
        for (int i = 0; i < target.getOptionValue(OptimizedRuntimeOptions.FirstTierCompilationThreshold); i++) {
            target.call();
        }
        assertCompiled(target);
        Assert.assertFalse(target.isValidLastTier());
        for (int i = 0; i < target.getOptionValue(OptimizedRuntimeOptions.LastTierCompilationThreshold); i++) {
            target.call();
        }
        assertCompiled(target);
        Assert.assertFalse(target.isValidLastTier());
        // Run a few more times to exercise compiled behaviour
        for (int i = 0; i < 5; i++) {
            target.call();
        }
    }

    @Test
    public void testLatencyFirstTierOnly() {
        setupContext(MODE, LATENCY);
        OptimizedCallTarget target = (OptimizedCallTarget) new RootNode(null) {
            @Override
            public Object execute(VirtualFrame frame) {
                if (CompilerDirectives.hasNextTier()) {
                    CompilerAsserts.neverPartOfCompilation("First tier guarded code should not be evaluated in latency mode");
                }
                return null;
            }
        }.getCallTarget();
        compileAndAssertLatency(target);
    }

    @Test
    public void testLatencyNoSplitting() {
        setupContext(MODE, LATENCY);
        OptimizedTruffleRuntime runtime = OptimizedTruffleRuntime.getRuntime();
        AbstractSplittingStrategyTest.SplitCountingListener listener = new AbstractSplittingStrategyTest.SplitCountingListener();
        try {
            runtime.addListener(listener);
            OptimizedCallTarget inner = (OptimizedCallTarget) new RootNode(null) {
                @Override
                public Object execute(VirtualFrame frame) {
                    if (CompilerDirectives.inInterpreter()) {
                        reportPolymorphicSpecialize();
                    }
                    return null;
                }

                @Override
                public boolean isCloningAllowed() {
                    return true;
                }
            }.getCallTarget();
            DirectCallNode directCallNode = runtime.createDirectCallNode(inner);
            DirectCallNode directCallNode2 = runtime.createDirectCallNode(inner);

            OptimizedCallTarget target = (OptimizedCallTarget) new RootNode(null) {
                @Override
                public Object execute(VirtualFrame frame) {
                    directCallNode.call();
                    directCallNode2.call();
                    return null;
                }

                @Override
                public boolean isCloningAllowed() {
                    return true;
                }
            }.getCallTarget();
            compileAndAssertLatency(target);
            Assert.assertEquals("Should not be splitting in latency mode", 0, listener.splitCount);
        } finally {
            runtime.removeListener(listener);
        }
    }

    @Test
    public void testLatencyNoInlining() {
        setupContext(MODE, LATENCY, "engine.CompileOnly", ROOT);
        OptimizedTruffleRuntime runtime = OptimizedTruffleRuntime.getRuntime();
        OptimizedCallTarget inner = (OptimizedCallTarget) new RootNode(null) {
            @Override
            public Object execute(VirtualFrame frame) {
                if (CompilerDirectives.inCompiledCode()) {
                    fail();
                }
                return null;
            }

            @CompilerDirectives.TruffleBoundary
            private void fail() {
                Assert.fail("Should not inline in latency mode");
            }

            @Override
            public boolean isCloningAllowed() {
                return true;
            }
        }.getCallTarget();
        DirectCallNode directCallNode = runtime.createDirectCallNode(inner);
        DirectCallNode directCallNode2 = runtime.createDirectCallNode(inner);

        OptimizedCallTarget target = (OptimizedCallTarget) new RootNode(null) {
            @Override
            public Object execute(VirtualFrame frame) {
                directCallNode.call();
                directCallNode2.call();
                return null;
            }

            @Override
            public String getName() {
                return ROOT;
            }

            @Override
            public String toString() {
                return getName();
            }
        }.getCallTarget();
        compileAndAssertLatency(target);
        Assert.assertNull(directCallNode.getClonedCallTarget());
        Assert.assertNull(directCallNode2.getClonedCallTarget());
    }
}
