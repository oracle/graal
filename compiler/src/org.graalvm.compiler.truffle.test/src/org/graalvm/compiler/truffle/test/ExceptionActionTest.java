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

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import java.util.logging.FileHandler;
import java.util.logging.SimpleFormatter;
import java.util.regex.Pattern;

import org.graalvm.compiler.test.SubprocessUtil;
import org.graalvm.compiler.truffle.common.TruffleCompilerRuntime;
import org.graalvm.compiler.truffle.runtime.OptimizedCallTarget;
import org.graalvm.compiler.truffle.test.nodes.AbstractTestNode;
import org.graalvm.compiler.truffle.test.nodes.RootTestNode;
import org.graalvm.polyglot.Context;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.OptimizationFailedException;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.RootNode;

public class ExceptionActionTest extends TestWithPolyglotOptions {

    private static final String LOG_FILE_PROPERTY = ExceptionActionTest.class.getSimpleName() + ".LogFile";
    private static final String[] DEFAULT_OPTIONS = {
                    "engine.CompileImmediately", "true",
                    "engine.BackgroundCompilation", "false",
    };

    static Object nonConstant;

    @BeforeClass
    public static void setUp() {
        // All ExceptionActionTest's tests are executed in the spawned subprocess. The
        // PermanentBailoutNode is used only by the code running in the subprocess. Needless
        // PermanentBailoutNode initialization in the parent process will cause ExceptionActionTest
        // failure when running with the engine.ExceptionAction=Throw.
        if (SubprocessTestUtils.isSubprocess()) {
            createPermanentBailoutNode().getCallTarget().call();
        }
    }

    @Test
    public void testDefault() throws Exception {
        BiConsumer<String, String> verifier = (log, output) -> {
            Assert.assertFalse(formatMessage("Unexpected bailout.", log, output), hasBailout(log));
            Assert.assertFalse(formatMessage("Unexpected exit.", log, output), hasExit(log));
            Assert.assertFalse(formatMessage("Unexpected OptimizationFailedException.", log, output), hasOptFailedException(log));
        };
        executeInSubProcess(verifier);
    }

    @Test
    public void testPermanentBailoutSilent() throws Exception {
        BiConsumer<String, String> verifier = (log, output) -> {
            Assert.assertFalse(formatMessage("Unexpected bailout.", log, output), hasBailout(log));
            Assert.assertFalse(formatMessage("Unexpected exit.", log, output), hasExit(log));
            Assert.assertFalse(formatMessage("Unexpected OptimizationFailedException.", log, output), hasOptFailedException(log));
        };
        executeInSubProcess(verifier,
                        "engine.CompilationFailureAction", "Silent");
    }

    @Test
    public void testPermanentBailoutPrint() throws Exception {
        BiConsumer<String, String> verifier = (log, output) -> {
            Assert.assertTrue(formatMessage("Expected bailout.", log, output), hasBailout(log));
            Assert.assertFalse(formatMessage("Unexpected exit.", log, output), hasExit(log));
            Assert.assertFalse(formatMessage("Unexpected OptimizationFailedException.", log, output), hasOptFailedException(log));
        };
        executeInSubProcess(verifier,
                        "engine.CompilationExceptionsArePrinted", "false",
                        "engine.CompilationFailureAction", "Print");
    }

    @Test
    public void testPermanentBailoutExceptionsArePrinted() throws Exception {
        BiConsumer<String, String> verifier = (log, output) -> {
            Assert.assertTrue(formatMessage("Expected bailout.", log, output), hasBailout(log));
            Assert.assertFalse(formatMessage("Unexpected exit.", log, output), hasExit(log));
            Assert.assertFalse(formatMessage("Unexpected OptimizationFailedException.", log, output), hasOptFailedException(log));
        };
        executeInSubProcess(verifier,
                        "engine.CompilationExceptionsArePrinted", "true",
                        "engine.CompilationFailureAction", "Silent");
    }

    @Test
    public void testPermanentBailoutExitVM() throws Exception {
        BiConsumer<String, String> verifier = (log, output) -> {
            Assert.assertTrue(formatMessage("Expected bailout.", log, output), hasBailout(log));
            Assert.assertTrue(formatMessage("Expected exit.", log, output), hasExit(log));
            Assert.assertFalse(formatMessage("Unexpected OptimizationFailedException.", log, output), hasOptFailedException(log));
        };
        executeInSubProcess(verifier, "engine.CompilationFailureAction", "ExitVM");
    }

    @Test
    public void testPermanentBailoutExceptionsAreFatal() throws Exception {
        BiConsumer<String, String> verifier = (log, output) -> {
            Assert.assertTrue(formatMessage("Expected bailout.", log, output), hasBailout(log));
            Assert.assertTrue(formatMessage("Expected exit.", log, output), hasExit(log));
            Assert.assertFalse(formatMessage("Unexpected OptimizationFailedException.", log, output), hasOptFailedException(log));
        };
        executeInSubProcess(verifier, "engine.CompilationExceptionsAreFatal", "true");
    }

    @Test
    public void testPermanentBailoutThrow() throws Exception {
        BiConsumer<String, String> verifier = (log, output) -> {
            Assert.assertFalse(formatMessage("Unexpected bailout.", log, output), hasBailout(log));
            Assert.assertFalse(formatMessage("Unexpected exit.", log, output), hasExit(log));
            Assert.assertTrue(formatMessage("Expected OptimizationFailedException.", log, output), hasOptFailedException(log));
        };
        executeInSubProcess(verifier, "engine.CompilationFailureAction", "Throw");
    }

    @Test
    public void testPermanentBailoutCompilationExceptionsAreThrown() throws Exception {
        BiConsumer<String, String> verifier = (log, output) -> {
            Assert.assertFalse(formatMessage("Unexpected bailout.", log, output), hasBailout(log));
            Assert.assertFalse(formatMessage("Unexpected exit.", log, output), hasExit(log));
            Assert.assertTrue(formatMessage("Expected OptimizationFailedException.", log, output), hasOptFailedException(log));
        };
        executeInSubProcess(verifier, "engine.CompilationExceptionsAreThrown", "true");
    }

    @Test
    public void testNonPermanentBailout() throws Exception {
        BiConsumer<String, String> verifier = (log, output) -> {
            Assert.assertFalse(formatMessage("Unexpected bailout.", log, output), hasBailout(log));
            Assert.assertFalse(formatMessage("Unexpected exit.", log, output), hasExit(log));
            Assert.assertFalse(formatMessage("Unexpected OptimizationFailedException.", log, output), hasOptFailedException(log));
        };
        executeInSubProcess(verifier, ExceptionActionTest::createConstantNode,
                        new String[]{"-Dgraal.CrashAt=org.graalvm.compiler.truffle.runtime.OptimizedCallTarget.profiledPERoot:Bailout"},
                        "engine.PerformanceWarningsAreFatal", "all");
    }

    @Test
    public void testNonPermanentBailoutTraceCompilationDetails() throws Exception {
        BiConsumer<String, String> verifier = (log, output) -> {
            Assert.assertTrue(formatMessage("Expected bailout.", log, output), hasBailout(log));
            Assert.assertFalse(formatMessage("Unexpected exit.", log, output), hasExit(log));
            Assert.assertFalse(formatMessage("Unexpected OptimizationFailedException.", log, output), hasOptFailedException(log));
        };
        executeInSubProcess(verifier, ExceptionActionTest::createConstantNode,
                        new String[]{"-Dgraal.CrashAt=org.graalvm.compiler.truffle.runtime.OptimizedCallTarget.profiledPERoot:Bailout"},
                        "engine.TraceCompilationDetails", "true");
    }

    private void executeInSubProcess(BiConsumer<String, String> verifier, String... contextOptions) throws IOException, InterruptedException {
        executeInSubProcess(verifier, ExceptionActionTest::createPermanentBailoutNode, new String[0], contextOptions);
    }

    private void executeInSubProcess(BiConsumer<String, String> verifier, Supplier<RootNode> rootNodeFactory, String[] additionalVmOptions, String... contextOptions)
                    throws IOException, InterruptedException {
        Path log = SubprocessTestUtils.isSubprocess() ? null : File.createTempFile("compiler", ".log").toPath();
        SubprocessUtil.Subprocess subprocess = null;
        boolean success = false;
        try {
            String[] useVMOptions = Arrays.copyOf(additionalVmOptions, additionalVmOptions.length + 2);
            useVMOptions[useVMOptions.length - 2] = String.format("-D%s=%s", LOG_FILE_PROPERTY, log);
            // Prevent graal graph dumping for ExceptionAction#Diagnose
            useVMOptions[useVMOptions.length - 1] = "-Dgraal.Dump=Truffle:0";
            subprocess = SubprocessTestUtils.executeInSubprocess(ExceptionActionTest.class, () -> {
                setupContext(contextOptions);
                OptimizedCallTarget target = (OptimizedCallTarget) rootNodeFactory.get().getCallTarget();
                try {
                    target.call();
                } catch (RuntimeException e) {
                    OptimizationFailedException optFailedException = isOptimizationFailed(e);
                    if (optFailedException != null) {
                        TruffleCompilerRuntime.getRuntime().log(target, optFailedException.getClass().getName());
                    }
                }
            }, false, useVMOptions);
            success = true;
        } finally {
            if (log != null) {
                if (success) {
                    String logContent = String.join("\n", Files.readAllLines(log));
                    String output = String.join("\n", subprocess.output);
                    verifier.accept(logContent, output);
                }
                Files.deleteIfExists(log);
            }
        }
    }

    private static OptimizationFailedException isOptimizationFailed(Throwable t) {
        if (t == null) {
            return null;
        } else if (t instanceof OptimizationFailedException) {
            return (OptimizationFailedException) t;
        }
        return isOptimizationFailed(t.getCause());
    }

    @Override
    protected Context.Builder newContextBuilder() {
        try {
            Context.Builder builder = super.newContextBuilder();
            String logFile = System.getProperty(LOG_FILE_PROPERTY);
            FileHandler handler = new FileHandler(logFile);
            handler.setFormatter(new SimpleFormatter());
            builder.logHandler(handler);
            for (int i = 0; i < DEFAULT_OPTIONS.length; i += 2) {
                builder.option(DEFAULT_OPTIONS[i], DEFAULT_OPTIONS[i + 1]);
            }
            return builder;
        } catch (IOException ioe) {
            throw new AssertionError("Cannot write log file.", ioe);
        }
    }

    private static boolean hasExit(String content) {
        return contains(content, "^.*Exiting VM.*$");
    }

    private static boolean hasBailout(String content) {
        return contains(content, "^[\\w.]*BailoutException.*$") || contains(content, "^.*Non permanent bailout.*$");
    }

    private static boolean hasOptFailedException(String content) {
        return contains(content, "^.*OptimizationFailedException.*$");
    }

    private static boolean contains(String content, String pattern) {
        return Pattern.compile(pattern, Pattern.MULTILINE).matcher(content).find();
    }

    private static String formatMessage(String message, String log, String output) {
        return String.format("%s%nLog content:%n%s%nSubprocess output:%n%s", message, log, output);
    }

    private static RootNode createPermanentBailoutNode() {
        FrameDescriptor fd = new FrameDescriptor();
        return new RootTestNode(fd, "permanent-bailout-test-node", new AbstractTestNode() {
            @Override
            public int execute(VirtualFrame frame) {
                CompilerAsserts.partialEvaluationConstant(nonConstant);
                return 0;
            }
        });
    }

    private static RootNode createConstantNode() {
        FrameDescriptor fd = new FrameDescriptor();
        return new RootTestNode(fd, "nonpermanent-bailout-test-node", new AbstractTestNode() {
            @Override
            public int execute(VirtualFrame frame) {
                return 0;
            }
        });
    }
}
