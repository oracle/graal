/*
 * Copyright (c) 2016, 2018, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.nodes.RootNode;
import org.graalvm.compiler.truffle.common.TruffleCompilerListener;
import org.graalvm.compiler.truffle.compiler.SharedTruffleCompilerOptions;
import org.graalvm.compiler.truffle.compiler.TruffleCompilerOptions;
import org.graalvm.compiler.truffle.runtime.GraalTruffleRuntime;
import org.graalvm.compiler.truffle.runtime.GraalTruffleRuntimeListener;
import org.graalvm.compiler.truffle.runtime.OptimizedCallTarget;
import org.graalvm.compiler.truffle.runtime.SharedTruffleRuntimeOptions;
import org.graalvm.compiler.truffle.runtime.TruffleInlining;
import org.graalvm.compiler.truffle.runtime.TruffleRuntimeOptions;
import org.junit.Assert;
import org.junit.Test;

public class OverrideOptionsTest extends TruffleCompilerImplTest {

    @Test
    @SuppressWarnings("try")
    public void testOverrideOptions() {
        try (TruffleRuntimeOptions.TruffleRuntimeOptionsOverrideScope scope = TruffleRuntimeOptions.overrideOptions(
                        SharedTruffleRuntimeOptions.TruffleBackgroundCompilation, false,
                        SharedTruffleRuntimeOptions.TruffleCompileImmediately, true,
                        SharedTruffleRuntimeOptions.TruffleInliningMaxCallerSize, 42)) {
            Assert.assertEquals((Integer) 42, TruffleRuntimeOptions.getValue(SharedTruffleRuntimeOptions.TruffleInliningMaxCallerSize));
            GraalTruffleRuntime runtime = GraalTruffleRuntime.getRuntime();
            GraalTruffleRuntimeListener listener = new GraalTruffleRuntimeListener() {
                @Override
                public void onCompilationTruffleTierFinished(OptimizedCallTarget target, TruffleInlining inliningDecision, TruffleCompilerListener.GraphInfo graph) {
                    Assert.assertEquals((Integer) 42, TruffleCompilerOptions.getValue(SharedTruffleCompilerOptions.TruffleInliningMaxCallerSize));
                }
            };
            runtime.addListener(listener);
            try {
                CallTarget callTarget = Truffle.getRuntime().createCallTarget(RootNode.createConstantNode(42));
                callTarget.call();
            } finally {
                runtime.removeListener(listener);
            }
        }
    }
}
