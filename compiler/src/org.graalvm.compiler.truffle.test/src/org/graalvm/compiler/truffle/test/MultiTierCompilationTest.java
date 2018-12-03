/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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

import static org.graalvm.compiler.truffle.runtime.SharedTruffleRuntimeOptions.TruffleBackgroundCompilation;
import static org.graalvm.compiler.truffle.runtime.SharedTruffleRuntimeOptions.TruffleCompilationThreshold;
import static org.graalvm.compiler.truffle.runtime.SharedTruffleRuntimeOptions.TruffleMultiTier;
import static org.graalvm.compiler.truffle.runtime.SharedTruffleRuntimeOptions.TruffleSplitting;
import static org.graalvm.compiler.truffle.runtime.TruffleRuntimeOptions.overrideOptions;

import org.graalvm.compiler.truffle.runtime.GraalCompilerDirectives;
import org.graalvm.compiler.truffle.runtime.OptimizedCallTarget;
import org.graalvm.compiler.truffle.runtime.PolyglotCompilerOptions;
import org.graalvm.compiler.truffle.runtime.SharedTruffleRuntimeOptions;
import org.graalvm.compiler.truffle.runtime.TruffleRuntimeOptions;
import org.graalvm.compiler.truffle.runtime.TruffleRuntimeOptions.TruffleRuntimeOptionsOverrideScope;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.RootNode;

public class MultiTierCompilationTest extends PartialEvaluationTest {

    private static TruffleRuntimeOptionsOverrideScope immediateCompilationScope;

    @BeforeClass
    public static void setup() {
        immediateCompilationScope = TruffleRuntimeOptions.overrideOptions(SharedTruffleRuntimeOptions.TruffleCompileImmediately, false);
    }

    @AfterClass
    public static void tearDown() {
        immediateCompilationScope.close();
    }

    public static class MultiTierCalleeNode extends RootNode {
        protected MultiTierCalleeNode() {
            super(null);
        }

        @Override
        public Object execute(VirtualFrame frame) {
            if (CompilerDirectives.inInterpreter()) {
                return "callee:interpreter";
            }
            boundary();
            if (GraalCompilerDirectives.inFirstTier()) {
                return "callee:first-tier";
            }
            if (CompilerDirectives.inCompilationRoot()) {
                return "callee:compiled";
            }
            return "callee:inlined";
        }
    }

    private static class MultiTierRootNode extends RootNode {
        @Child private DirectCallNode callNode;

        MultiTierRootNode(CallTarget target) {
            super(null);
            this.callNode = Truffle.getRuntime().createDirectCallNode(target);
        }

        @Override
        public Object execute(VirtualFrame frame) {
            if (CompilerDirectives.inInterpreter()) {
                return "root:interpreter";
            }
            boundary();
            return callNode.call(frame.getArguments());
        }
    }

    @CompilerDirectives.TruffleBoundary
    private static void boundary() {
    }

    @SuppressWarnings("try")
    @Test
    public void testCompilationTiers() {
        Assume.assumeTrue(TruffleRuntimeOptions.getValue(SharedTruffleRuntimeOptions.TruffleCompileImmediately));
        try (TruffleRuntimeOptionsOverrideScope scope = overrideOptions(
                        TruffleBackgroundCompilation, false,
                        TruffleMultiTier, true,
                        TruffleSplitting, false)) {
            final int firstTierCompilationThreshold = PolyglotCompilerOptions.FirstTierCompilationThreshold.getDefaultValue();
            final int compilationThreshold = TruffleRuntimeOptions.getValue(TruffleCompilationThreshold);
            OptimizedCallTarget calleeTarget = (OptimizedCallTarget) Truffle.getRuntime().createCallTarget(new MultiTierCalleeNode());
            OptimizedCallTarget multiTierTarget = (OptimizedCallTarget) Truffle.getRuntime().createCallTarget(new MultiTierRootNode(calleeTarget));
            for (int i = 0; i < firstTierCompilationThreshold; i++) {
                multiTierTarget.call();
            }
            Assert.assertEquals("callee:interpreter", multiTierTarget.call());
            for (int i = 0; i < compilationThreshold; i++) {
                multiTierTarget.call();
            }
            Assert.assertEquals("callee:inlined", multiTierTarget.call());
        }
    }
}
