/*
 * Copyright (c) 2019, 2022, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.truffle.test.libgraal;

import static com.oracle.truffle.api.test.SubprocessTestUtils.markForRemoval;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import java.util.regex.Pattern;

import com.oracle.truffle.api.test.SubprocessTestUtils;
import jdk.graal.compiler.truffle.TruffleCompilerImpl;
import jdk.graal.compiler.truffle.test.TestWithPolyglotOptions;
import org.junit.Test;

import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.compiler.TruffleCompilable;
import com.oracle.truffle.compiler.TruffleCompilationTask;
import com.oracle.truffle.compiler.TruffleCompiler;
import com.oracle.truffle.compiler.TruffleCompilerListener;
import com.oracle.truffle.runtime.OptimizedTruffleRuntime;
import com.oracle.truffle.runtime.OptimizedCallTarget;

public class JNIExceptionWrapperTest extends TestWithPolyglotOptions {

    @Test
    public void testMergedStackTrace() throws Exception {
        SubprocessTestUtils.newBuilder(JNIExceptionWrapperTest.class, this::testMergedStackTraceImpl).//
        /*
         * The test verifies correctness of a compilation exception stack trace. It uses
         * `CompilationFailureAction=Throw`, but we do not want to retain the unnecessary dump file.
         */
                        prefixVmOption(markForRemoval("-Djdk.graal.Dump"), "-Djdk.graal.Dump=~").//
                        run();
    }

    private void testMergedStackTraceImpl() {
        setupContext("engine.CompilationFailureAction", "Throw", "engine.BackgroundCompilation", Boolean.FALSE.toString());
        OptimizedTruffleRuntime runtime = OptimizedTruffleRuntime.getRuntime();
        OptimizedCallTarget compilable = (OptimizedCallTarget) RootNode.createConstantNode(42).getCallTarget();
        TruffleCompiler compiler = runtime.getTruffleCompiler(compilable);
        TestTruffleCompilationTask task = new TestTruffleCompilationTask();
        try {
            TestListener listener = new TestListener();
            compiler.doCompile(task, compilable, listener);
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
        public void onTruffleTierFinished(TruffleCompilable compilable, TruffleCompilationTask task, GraphInfo graph) {
            throw new RuntimeException("Expected exception");
        }

        @Override
        public void onGraalTierFinished(TruffleCompilable compilable, GraphInfo graph) {
        }
    }

    private static class TestTruffleCompilationTask implements TruffleCompilationTask {

        @Override
        public boolean isCancelled() {
            return false;
        }

        @Override
        public boolean isLastTier() {
            return true;
        }

        @Override
        public boolean hasNextTier() {
            return false;
        }
    }
}
