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
import java.util.regex.Pattern;
import org.graalvm.compiler.truffle.common.TruffleCompilerListener;
import org.graalvm.compiler.truffle.compiler.TruffleCompilerImpl;
import org.graalvm.compiler.truffle.runtime.GraalTruffleRuntime;
import org.graalvm.compiler.truffle.runtime.GraalTruffleRuntimeListener;
import org.graalvm.compiler.truffle.runtime.OptimizedCallTarget;
import org.graalvm.compiler.truffle.runtime.SharedTruffleRuntimeOptions;
import org.graalvm.compiler.truffle.runtime.TruffleInlining;
import org.graalvm.compiler.truffle.runtime.TruffleRuntimeOptions;
import org.junit.Test;

public class JNIExceptionWrapperTest {

    @Test
    @SuppressWarnings({"unused", "try"})
    public void testMergedStackTrace() throws Exception {
        try (TruffleRuntimeOptions.TruffleRuntimeOptionsOverrideScope runtimeScope = TruffleRuntimeOptions.overrideOptions(
                        SharedTruffleRuntimeOptions.TruffleBackgroundCompilation, false,
                        SharedTruffleRuntimeOptions.TruffleCompileImmediately, true,
                        SharedTruffleRuntimeOptions.TruffleCompilationExceptionsAreThrown, true)) {
            GraalTruffleRuntime runtime = GraalTruffleRuntime.getRuntime();
            OptimizedCallTarget compilable = (OptimizedCallTarget) runtime.createCallTarget(RootNode.createConstantNode(42));
            TestListener listener = new TestListener();
            runtime.addListener(listener);
            try {
                compilable.compile(false);
            } catch (Throwable t) {
                String message = t.getMessage();
                int runtimeIndex = findFrame(message, GraalTruffleRuntime.class.getName(), "doCompile");
                assertNotEquals(-1, runtimeIndex);
                int listenerIndex = findFrame(message, TestListener.class.getName(), "onCompilationTruffleTierFinished");
                assertNotEquals(-1, listenerIndex);
                int compilerIndex = findFrame(message, TruffleCompilerImpl.class.getName(), "compileAST");
                assertNotEquals(-1, compilerIndex);
                assertTrue(listenerIndex < compilerIndex);
                assertTrue(compilerIndex < runtimeIndex);
            } finally {
                runtime.removeListener(listener);
            }
        }
    }

    private static int findFrame(String message, String className, String methodName) {
        String[] lines = message.split("\n");
        Pattern pattern = Pattern.compile("\\s*at\\s+" + Pattern.quote(className + '.' + methodName), 0);
        for (int i = 0; i < lines.length; i++) {
            if (pattern.matcher(lines[i]).find()) {
                return i;
            }
        }
        return -1;
    }

    private static final class TestListener implements GraalTruffleRuntimeListener {

        @Override
        public void onCompilationTruffleTierFinished(OptimizedCallTarget target, TruffleInlining inliningDecision, TruffleCompilerListener.GraphInfo graph) {
            throw new RuntimeException("Expected exception");
        }
    }
}
