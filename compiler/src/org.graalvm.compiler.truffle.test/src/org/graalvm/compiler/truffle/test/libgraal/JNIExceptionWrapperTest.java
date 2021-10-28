/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
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
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.graalvm.compiler.core.CompilationWrapper;
import org.graalvm.compiler.core.GraalCompilerOptions;
import org.graalvm.compiler.test.SubprocessUtil;
import org.graalvm.compiler.truffle.common.CompilableTruffleAST;
import org.graalvm.compiler.truffle.common.TruffleCompilation;
import org.graalvm.compiler.truffle.common.TruffleCompilationTask;
import org.graalvm.compiler.truffle.common.TruffleCompiler;
import org.graalvm.compiler.truffle.common.TruffleCompilerListener;
import org.graalvm.compiler.truffle.common.TruffleDebugContext;
import org.graalvm.compiler.truffle.common.TruffleInliningData;
import org.graalvm.compiler.truffle.compiler.TruffleCompilerImpl;
import org.graalvm.compiler.truffle.runtime.GraalTruffleRuntime;
import org.graalvm.compiler.truffle.runtime.OptimizedCallTarget;
import org.graalvm.compiler.truffle.runtime.TruffleInlining;
import org.graalvm.compiler.truffle.test.TestWithPolyglotOptions;
import org.junit.Test;

import com.oracle.truffle.api.nodes.RootNode;

public class JNIExceptionWrapperTest extends TestWithPolyglotOptions {

    @Test
    public void testMergedStackTrace() throws Exception {
        if (isSilent()) {
            testMergedStackTraceImpl();
        } else {
            SubprocessUtil.Subprocess proc = SubprocessUtil.java(makeSilent(getVmArgs()), "com.oracle.mxtool.junit.MxJUnitWrapper", getClass().getName());
            int exitCode = proc.exitCode;
            if (exitCode != 0) {
                fail(String.format("non-zero exit code %d for command:%n%s", exitCode, proc));
            }
        }
    }

    private static boolean isSilent() {
        Object value = System.getProperty(String.format("graal.%s", GraalCompilerOptions.CompilationFailureAction.getName()));
        return CompilationWrapper.ExceptionAction.Silent.toString().equals(value);
    }

    private static List<String> getVmArgs() {
        // Filter out the LogFile option to prevent overriding of the unit tests log file by a
        // sub-process.
        List<String> vmArgs = SubprocessUtil.getVMCommandLine().stream().filter((vmArg) -> !vmArg.contains("LogFile")).collect(Collectors.toList());
        vmArgs.add(SubprocessUtil.PACKAGE_OPENING_OPTIONS);
        return vmArgs;
    }

    private static List<String> makeSilent(List<? extends String> vmArgs) {
        List<String> newVmArgs = new ArrayList<>();
        newVmArgs.addAll(vmArgs.stream().filter((vmArg) -> !vmArg.contains(GraalCompilerOptions.CompilationFailureAction.getName())).collect(Collectors.toList()));
        newVmArgs.add(1, String.format("-Dgraal.%s=%s", GraalCompilerOptions.CompilationFailureAction.getName(), CompilationWrapper.ExceptionAction.Silent.toString()));
        return newVmArgs;
    }

    private void testMergedStackTraceImpl() throws Exception {
        setupContext("engine.CompilationExceptionsAreThrown", Boolean.TRUE.toString(), "engine.CompilationExceptionsAreFatal", Boolean.FALSE.toString());
        GraalTruffleRuntime runtime = GraalTruffleRuntime.getRuntime();
        OptimizedCallTarget compilable = (OptimizedCallTarget) RootNode.createConstantNode(42).getCallTarget();
        TruffleCompiler compiler = runtime.getTruffleCompiler(compilable);
        try (TruffleCompilation compilation = compiler.openCompilation(compilable)) {
            try (TruffleDebugContext debug = compiler.openDebugContext(GraalTruffleRuntime.getOptionsForCompiler(compilable), compilation)) {
                TestListener listener = new TestListener();
                compiler.doCompile(debug, compilation, GraalTruffleRuntime.getOptionsForCompiler(compilable), new TestTruffleCompilationTask(), listener);
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
        public void onTruffleTierFinished(CompilableTruffleAST compilable, TruffleInliningData inliningPlan, GraphInfo graph) {
            throw new RuntimeException("Expected exception");
        }

        @Override
        public void onGraalTierFinished(CompilableTruffleAST compilable, GraphInfo graph) {
        }

        @Override
        public void onSuccess(CompilableTruffleAST compilable, TruffleInliningData inliningPlan, GraphInfo graphInfo, CompilationResultInfo compilationResultInfo, int tier) {
        }

        @Override
        public void onFailure(CompilableTruffleAST compilable, String reason, boolean bailout, boolean permanentBailout, int tier) {
        }
    }

    private static class TestTruffleCompilationTask implements TruffleCompilationTask {
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
    }
}
