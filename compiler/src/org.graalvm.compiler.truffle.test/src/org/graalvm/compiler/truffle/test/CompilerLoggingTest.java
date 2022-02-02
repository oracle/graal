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

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import org.graalvm.compiler.debug.TTY;
import org.graalvm.compiler.truffle.common.TruffleCompilerListener;
import org.graalvm.compiler.truffle.runtime.GraalTruffleRuntime;
import org.graalvm.compiler.truffle.runtime.GraalTruffleRuntimeListener;
import org.graalvm.compiler.truffle.runtime.OptimizedCallTarget;
import org.graalvm.compiler.truffle.runtime.TruffleInlining;
import org.graalvm.polyglot.Context;
import org.junit.Assert;
import org.junit.Test;

import com.oracle.truffle.api.nodes.RootNode;

public class CompilerLoggingTest extends TruffleCompilerImplTest {

    private static final String FORMAT_FAILURE = "Failed to compile %s due to %s";
    private static final String FORMAT_SUCCESS = "Compiled %s";
    private static final String MESSAGE_TO_STREAM = "begin";
    private static final String MESSAGE_TO_TTY = "end";

    @Test
    public void testLogging() throws IOException {
        try (ByteArrayOutputStream logOut = new ByteArrayOutputStream()) {
            setupContext(Context.newBuilder().logHandler(logOut).option("engine.CompileImmediately", "true").option("engine.MultiTier", "false").option("engine.BackgroundCompilation", "false"));
            GraalTruffleRuntime runtime = GraalTruffleRuntime.getRuntime();
            TestListener listener = new TestListener();
            try {
                runtime.addListener(listener);
                OptimizedCallTarget compilable = (OptimizedCallTarget) RootNode.createConstantNode(true).getCallTarget();
                compilable.call();
                String logContent = new String(logOut.toByteArray());
                String expected = String.format(FORMAT_SUCCESS + "%s%s%n", compilable.getName(), MESSAGE_TO_STREAM, MESSAGE_TO_TTY);
                Assert.assertEquals("Expected " + expected + " in " + logContent, expected, logContent);
            } finally {
                runtime.removeListener(listener);
            }
        }
    }

    private static final class TestListener implements GraalTruffleRuntimeListener {

        @Override
        public void onCompilationSuccess(OptimizedCallTarget target, TruffleInlining inliningDecision, TruffleCompilerListener.GraphInfo graph,
                        TruffleCompilerListener.CompilationResultInfo result, int tier) {
            TTY.printf(FORMAT_SUCCESS, target.getName());
            printCommon();
        }

        @Override
        public void onCompilationFailed(OptimizedCallTarget target, String reason, boolean bailout, boolean permanentBailout, int tier) {
            TTY.printf(FORMAT_FAILURE, target.getName(), reason);
            printCommon();
        }

        private static void printCommon() {
            TTY.out().out().print(MESSAGE_TO_STREAM);
            TTY.println(MESSAGE_TO_TTY);
        }
    }
}
