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

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.function.Consumer;

import com.oracle.truffle.api.test.SubprocessTestUtils;
import org.junit.Assert;
import org.junit.Test;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.HostCompilerDirectives.BytecodeInterpreterSwitch;
import com.oracle.truffle.api.Truffle;

/**
 * Test to ensure that host inlining is enabled only if a Truffle runtime is enabled.
 */
public class TruffleHostInliningTest {

    @Test
    public void testWithRuntime() throws IOException, InterruptedException {
        runHostCompilationTest(() -> {
            Truffle.getRuntime();
            TruffleHostInliningTest.testBytecodeInterpreter();
        }, (log) -> {
            /*
             * With an initialized host inlining should trigger and therefore the log output should
             * contain output.
             */
            Assert.assertTrue(log, log.contains("CUTOFF   " + TruffleHostInliningTest.class.getName() + ".boundary()"));
        });
    }

    @Test
    public void testWithoutRuntime() throws IOException, InterruptedException {
        runHostCompilationTest(() -> {
            TruffleHostInliningTest.testBytecodeInterpreter();
        }, (log) -> {
            /*
             * Without a runtime host inlining should not trigger and therefore the log output
             * should be empty.
             */
            Assert.assertTrue(log, log.isEmpty());
        });

    }

    private void runHostCompilationTest(Runnable inProcess, Consumer<String> log) throws IOException, InterruptedException {
        if (SubprocessTestUtils.isSubprocess()) {
            inProcess.run();
        } else {
            File logFile = File.createTempFile(getClass().getSimpleName(), "test");
            SubprocessTestUtils.newBuilder(getClass(), inProcess).failOnNonZeroExit(true).//
                            prefixVmOption("-Djdk.graal.Log=HostInliningPhase,~CanonicalizerPhase,~InlineGraph",
                                            "-Djdk.graal.MethodFilter=" + TruffleHostInliningTest.class.getSimpleName() + ".*",
                                            "-Djdk.graal.CompilationFailureAction=Print",
                                            "-Djdk.graal.LogFile=" + logFile.getAbsolutePath(),
                                            String.format("-XX:CompileCommand=compileonly,%s::*", TruffleHostInliningTest.class.getName()),
                                            "-Xbatch").// force synchronous compilation
                            postfixVmOption("-XX:+UseJVMCICompiler").// force Graal host compilation
                            onExit((process) -> {
                                try {
                                    log.accept((Files.readString(logFile.toPath())));
                                } catch (IOException e) {
                                    throw new AssertionError(e);
                                }
                                logFile.delete();
                            }).run();
        }
    }

    public static void testBytecodeInterpreter() {
        byte[] bc = new byte[42];
        for (int i = 0; i < bc.length; i++) {
            bc[i] = (byte) (i % 7);
        }

        // we explicitly do not initialize the Truffle runtime here.
        for (int i = 0; i < 10000000; i++) {
            execute(bc);
        }
    }

    @BytecodeInterpreterSwitch
    public static void execute(byte[] ops) {
        int bci = 0;
        while (bci < ops.length) {
            switch (ops[bci++]) {
                case 0:
                    trivial();
                    break;
                case 1:
                    trivial();
                    break;
                case 2:
                    trivial();
                    break;
                case 3:
                    trivial();
                    break;
                case 4:
                    boundary();
                    // first level of recursion is inlined
                    break;
                case 5:
                    boundary();
                    // can be inlined is still monomorphic (with profile)
                    break;
                case 6:
                    trivial();
                    break;
                case 7:
                    trivial();
                    break;
            }
        }
    }

    private static void trivial() {

    }

    @TruffleBoundary
    private static void boundary() {

    }

}
