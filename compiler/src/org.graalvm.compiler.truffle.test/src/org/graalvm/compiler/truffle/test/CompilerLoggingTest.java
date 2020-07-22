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

import com.oracle.truffle.api.nodes.RootNode;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Map;
import org.graalvm.compiler.debug.TTY;
import org.graalvm.compiler.truffle.common.CompilableTruffleAST;
import org.graalvm.compiler.truffle.common.TruffleCompilation;
import org.graalvm.compiler.truffle.common.TruffleCompiler;
import org.graalvm.compiler.truffle.common.TruffleCompilerListener;
import org.graalvm.compiler.truffle.common.TruffleDebugContext;
import org.graalvm.compiler.truffle.common.TruffleInliningPlan;
import org.graalvm.compiler.truffle.runtime.GraalTruffleRuntime;
import org.graalvm.compiler.truffle.runtime.OptimizedCallTarget;
import org.graalvm.compiler.truffle.runtime.TruffleRuntimeOptions;
import org.graalvm.polyglot.Context;
import org.junit.Test;

public class CompilerLoggingTest extends TruffleCompilerImplTest {

    private static final String FORMAT_FAILURE = "Failed to compile %s due to %s";
    private static final String FORMAT_SUCCESS = "Compiled %s";

    @Test
    public void testLogging() throws IOException {
        try (ByteArrayOutputStream logOut = new ByteArrayOutputStream()) {
            Context.Builder builder = Context.newBuilder().logHandler(logOut);
            setupContext(builder);
            GraalTruffleRuntime runtime = GraalTruffleRuntime.getRuntime();
            OptimizedCallTarget compilable = (OptimizedCallTarget) runtime.createCallTarget(RootNode.createConstantNode(true));
            TruffleCompiler truffleCompiler = getTruffleCompiler(compilable);
            try (TruffleCompilation compilation = truffleCompiler.openCompilation(compilable)) {
                Map<String, Object> options = TruffleRuntimeOptions.getOptionsForCompiler(compilable);
                try (TruffleDebugContext debug = truffleCompiler.openDebugContext(options, compilation)) {
                    TruffleInliningPlan inliningPlan = runtime.createInliningPlan(compilable, null);
                    TruffleCompilerListener testListener = new TestListener();
                    truffleCompiler.doCompile(debug, compilation, options, inliningPlan, null, testListener);
                }
            }
            String logContent = new String(logOut.toByteArray());
            String expected = String.format(FORMAT_SUCCESS, compilable.getName());
            assertTrue("Expected " + expected + " in " + logContent, logContent.contains(expected));
        }
    }

    private static final class TestListener implements TruffleCompilerListener {

        @Override
        public void onGraalTierFinished(CompilableTruffleAST compilable, GraphInfo graph) {
        }

        @Override
        public void onTruffleTierFinished(CompilableTruffleAST compilable, TruffleInliningPlan inliningPlan, GraphInfo graph) {
        }

        @Override
        public void onSuccess(CompilableTruffleAST compilable, TruffleInliningPlan inliningPlan, GraphInfo graph, CompilationResultInfo compilationResultInfo) {
            TTY.printf(FORMAT_SUCCESS, compilable.getName());
            TTY.flush();
        }

        @Override
        public void onFailure(CompilableTruffleAST compilable, String reason, boolean bailout, boolean permanentBailout) {
            TTY.printf(FORMAT_FAILURE, compilable.getName(), reason);
            TTY.flush();
        }
    }
}
