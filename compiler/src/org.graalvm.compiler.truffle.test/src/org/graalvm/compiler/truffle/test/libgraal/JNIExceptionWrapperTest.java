/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.test.libgraal;

import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import com.oracle.truffle.api.nodes.RootNode;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;
import org.graalvm.compiler.core.CompilationWrapper;
import org.graalvm.compiler.truffle.common.CompilableTruffleAST;
import org.graalvm.compiler.truffle.common.TruffleCompilation;
import org.graalvm.compiler.truffle.common.TruffleCompiler;
import org.graalvm.compiler.truffle.common.TruffleCompilerListener;
import org.graalvm.compiler.truffle.common.TruffleDebugContext;
import org.graalvm.compiler.truffle.common.TruffleInliningPlan;
import org.graalvm.compiler.truffle.compiler.TruffleCompilerImpl;
import org.graalvm.compiler.truffle.runtime.GraalTruffleRuntime;
import org.graalvm.compiler.truffle.runtime.OptimizedCallTarget;
import org.graalvm.compiler.truffle.runtime.SharedTruffleRuntimeOptions;
import org.graalvm.compiler.truffle.runtime.TruffleRuntimeOptions;
import org.junit.Test;

public class JNIExceptionWrapperTest {

    @Test
    @SuppressWarnings({"unused", "try"})
    public void testMergedStackTrace() throws Exception {
        try (TruffleRuntimeOptions.TruffleRuntimeOptionsOverrideScope runtimeScope = TruffleRuntimeOptions.overrideOptions(
                        SharedTruffleRuntimeOptions.TruffleCompilationExceptionsAreThrown, true)) {
            GraalTruffleRuntime runtime = GraalTruffleRuntime.getRuntime();
            OptimizedCallTarget compilable = (OptimizedCallTarget) runtime.createCallTarget(RootNode.createConstantNode(42));
            TruffleCompiler compiler = runtime.getTruffleCompiler();
            try (TruffleCompilation compilation = compiler.openCompilation(compilable)) {
                Map<String, Object> options = new HashMap<>();
                options.put("CompilationFailureAction", CompilationWrapper.ExceptionAction.Silent);
                options.put("TruffleCompilationExceptionsAreFatal", false);
                try (TruffleDebugContext debug = compiler.openDebugContext(options, compilation)) {
                    compilable.getCompilationProfile();
                    TruffleInliningPlan inliningPlan = runtime.createInliningPlan(compilable, null);
                    TestListener listener = new TestListener();
                    compiler.doCompile(debug, compilation, options, inliningPlan, null, listener);
                }
            } catch (Throwable t) {
                String message = t.getMessage();
                int runtimeIndex = findFrame(message, JNIExceptionWrapperTest.class, "testMergedStackTrace");
                assertNotEquals(message, -1, runtimeIndex);
                int listenerIndex = findFrame(message, TestListener.class, "onTruffleTierFinished");
                assertNotEquals(message, -1, listenerIndex);
                int compilerIndex = findFrame(message, TruffleCompilerImpl.class, "compileAST");
                assertNotEquals(message, -1, compilerIndex);
                assertTrue(listenerIndex < compilerIndex);
                assertTrue(compilerIndex < runtimeIndex);
            }
        }
    }

    private static int findFrame(String message, Class<?> clazz, String methodName) {
        String[] lines = message.split("\n");
        Pattern pattern = Pattern.compile("at\\s+(.*/)?" + Pattern.quote(clazz.getName() + '.' + methodName));
        for (int i = 0; i < lines.length; i++) {
            if (pattern.matcher(lines[i].trim()).find()) {
                return i;
            }
        }
        return -1;
    }

    private static final class TestListener implements TruffleCompilerListener {

        @Override
        public void onTruffleTierFinished(CompilableTruffleAST compilable, TruffleInliningPlan inliningPlan, GraphInfo graph) {
            throw new RuntimeException("Expected exception");
        }

        @Override
        public void onGraalTierFinished(CompilableTruffleAST compilable, GraphInfo graph) {
        }

        @Override
        public void onSuccess(CompilableTruffleAST compilable, TruffleInliningPlan inliningPlan, GraphInfo graphInfo, CompilationResultInfo compilationResultInfo) {
        }

        @Override
        public void onFailure(CompilableTruffleAST compilable, String reason, boolean bailout, boolean permanentBailout) {
        }
    }
}
