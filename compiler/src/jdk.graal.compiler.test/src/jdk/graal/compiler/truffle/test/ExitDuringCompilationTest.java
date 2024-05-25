/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.test.SubprocessTestUtils;
import com.oracle.truffle.compiler.TruffleCompilerListener;
import com.oracle.truffle.runtime.AbstractCompilationTask;
import com.oracle.truffle.runtime.OptimizedTruffleRuntime;
import com.oracle.truffle.runtime.OptimizedTruffleRuntimeListener;
import com.oracle.truffle.runtime.OptimizedCallTarget;

import org.junit.Test;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.regex.Pattern;

import static org.junit.Assert.assertFalse;

public class ExitDuringCompilationTest extends TestWithPolyglotOptions {

    @Test
    public void testExit() throws IOException, InterruptedException {
        SubprocessTestUtils.newBuilder(ExitDuringCompilationTest.class, () -> {
            try {
                var cond = new NotifyCompilation();
                OptimizedTruffleRuntime.getRuntime().addListener(cond);
                setupContext("engine.CompileImmediately", "true", "engine.CompilationFailureAction", "ExitVM");
                OptimizedCallTarget callTarget = (OptimizedCallTarget) RootNode.createConstantNode(42).getCallTarget();
                callTarget.call();
                cond.await();
                // Compiler thread is active
                System.exit(0);
            } catch (InterruptedException ie) {
                throw new AssertionError("Interrupted", ie);
            }
        }).prefixVmOption("-Djdk.graal.MethodFilter=RootNode.Constant", "-Djdk.graal.InjectedCompilationDelay=100").//
                        onExit((p) -> {
                            String output = String.join("\n", p.output);
                            assertFalse(output, Pattern.compile("(Exception|Error)").matcher(output).find());
                        }).//
                        run();
    }

    private static final class NotifyCompilation implements OptimizedTruffleRuntimeListener {
        private final CountDownLatch signal = new CountDownLatch(1);

        @Override
        public void onCompilationTruffleTierFinished(OptimizedCallTarget target, AbstractCompilationTask task, TruffleCompilerListener.GraphInfo graph) {
            signal.countDown();
        }

        void await() throws InterruptedException {
            signal.await();
        }
    }
}
