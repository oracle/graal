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

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.OptimizationFailedException;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.RootNode;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.logging.FileHandler;
import java.util.logging.SimpleFormatter;
import java.util.stream.Collectors;
import org.graalvm.compiler.core.GraalCompilerOptions;
import org.graalvm.compiler.test.SubprocessUtil;
import org.graalvm.compiler.truffle.common.TruffleCompilerRuntime;
import org.graalvm.compiler.truffle.runtime.OptimizedCallTarget;
import org.graalvm.compiler.truffle.test.nodes.AbstractTestNode;
import org.graalvm.compiler.truffle.test.nodes.RootTestNode;
import org.graalvm.polyglot.Context;
import org.junit.Test;
import org.junit.Assert;
import org.junit.BeforeClass;

public class ExceptionActionTest extends TestWithPolyglotOptions {

    private static final String LOG_FILE_PROPERTY = ExceptionActionTest.class.getSimpleName() + ".LogFile";
    private static final String[] DEFAULT_OPTIONS = {
                    "engine.CompileImmediately", "true",
                    "engine.BackgroundCompilation", "false",
    };

    static Object nonConstant;

    @BeforeClass
    public static void setUp() {
        Truffle.getRuntime().createCallTarget(createPermanentBailoutNode()).call();
    }

    @Test
    public void testDefault() throws Exception {
        Consumer<Path> verifier = (log) -> {
            Assert.assertFalse(hasBailout(log));
            Assert.assertFalse(hasExit(log));
            Assert.assertFalse(hasOptFailedException(log));
        };
        executeForked(verifier);
    }

    @Test
    public void testPermanentBailoutSilent() throws Exception {
        Consumer<Path> verifier = (log) -> {
            Assert.assertFalse(hasBailout(log));
            Assert.assertFalse(hasExit(log));
            Assert.assertFalse(hasOptFailedException(log));
        };
        executeForked(verifier,
                        "engine.CompilationFailureAction", "Silent");
    }

    @Test
    public void testPermanentBailoutPrint() throws Exception {
        Consumer<Path> verifier = (log) -> {
            Assert.assertTrue(hasBailout(log));
            Assert.assertFalse(hasExit(log));
            Assert.assertFalse(hasOptFailedException(log));
        };
        executeForked(verifier,
                        "engine.CompilationExceptionsArePrinted", "false",
                        "engine.CompilationFailureAction", "Print");
    }

    @Test
    public void testPermanentBailoutExceptionsArePrinted() throws Exception {
        Consumer<Path> verifier = (log) -> {
            Assert.assertTrue(hasBailout(log));
            Assert.assertFalse(hasExit(log));
            Assert.assertFalse(hasOptFailedException(log));
        };
        executeForked(verifier,
                        "engine.CompilationExceptionsArePrinted", "true",
                        "engine.CompilationFailureAction", "Silent");
    }

    @Test
    public void testPermanentBailoutExitVM() throws Exception {
        Consumer<Path> verifier = (log) -> {
            Assert.assertTrue(hasBailout(log));
            Assert.assertTrue(hasExit(log));
            Assert.assertFalse(hasOptFailedException(log));
        };
        executeForked(verifier, "engine.CompilationFailureAction", "ExitVM");
    }

    @Test
    public void testPermanentBailoutExceptionsAreFatal() throws Exception {
        Consumer<Path> verifier = (log) -> {
            Assert.assertTrue(hasBailout(log));
            Assert.assertTrue(hasExit(log));
            Assert.assertFalse(hasOptFailedException(log));
        };
        executeForked(verifier, "engine.CompilationExceptionsAreFatal", "true");
    }

    @Test
    public void testPermanentBailoutThrow() throws Exception {
        Consumer<Path> verifier = (log) -> {
            Assert.assertFalse(hasBailout(log));
            Assert.assertFalse(hasExit(log));
            Assert.assertTrue(hasOptFailedException(log));
        };
        executeForked(verifier, "engine.CompilationFailureAction", "Throw");
    }

    @Test
    public void testPermanentBailoutCompilationExceptionsAreThrown() throws Exception {
        Consumer<Path> verifier = (log) -> {
            Assert.assertFalse(hasBailout(log));
            Assert.assertFalse(hasExit(log));
            Assert.assertTrue(hasOptFailedException(log));
        };
        executeForked(verifier, "engine.CompilationExceptionsAreThrown", "true");
    }

    @Test
    public void testNonPermanentBailout() throws Exception {
        Consumer<Path> verifier = (log) -> {
            Assert.assertFalse(hasBailout(log));
            Assert.assertFalse(hasExit(log));
            Assert.assertFalse(hasOptFailedException(log));
        };
        executeForked(verifier, ExceptionActionTest::createConstantNode,
                        new String[]{"-Dgraal.CrashAt=org.graalvm.compiler.truffle.runtime.OptimizedCallTarget.profiledPERoot:Bailout"},
                        "engine.PerformanceWarningsAreFatal", "all");
    }

    @Test
    public void testNonPermanentBailoutTraceCompilationDetails() throws Exception {
        Consumer<Path> verifier = (log) -> {
            Assert.assertTrue(hasBailout(log));
            Assert.assertFalse(hasExit(log));
            Assert.assertFalse(hasOptFailedException(log));
        };
        executeForked(verifier, ExceptionActionTest::createConstantNode,
                        new String[]{"-Dgraal.CrashAt=org.graalvm.compiler.truffle.runtime.OptimizedCallTarget.profiledPERoot:Bailout"},
                        "engine.TraceCompilationDetails", "true");
    }

    private void executeForked(Consumer<? super Path> verifier, String... contextOptions) throws IOException, InterruptedException {
        executeForked(verifier, ExceptionActionTest::createPermanentBailoutNode, new String[0], contextOptions);
    }

    private void executeForked(Consumer<? super Path> verifier, Supplier<RootNode> rootNodeFactory, String[] additionalVmOptions, String... contextOptions) throws IOException, InterruptedException {
        if (!isConfigured()) {
            Path log = File.createTempFile("compiler", ".log").toPath();
            String testName = getTestName();
            execute(testName, log, additionalVmOptions);
            verifier.accept(log);
        } else {
            setupContext(contextOptions);
            OptimizedCallTarget target = (OptimizedCallTarget) Truffle.getRuntime().createCallTarget(rootNodeFactory.get());
            try {
                target.call();
            } catch (RuntimeException e) {
                OptimizationFailedException optFailedException = isOptimizationFailed(e);
                if (optFailedException != null) {
                    TruffleCompilerRuntime.getRuntime().log(target, optFailedException.getClass().getName());
                }
            }
        }
    }

    private static String getTestName() {
        boolean inExecuteForked = false;
        StackTraceElement[] stack = Thread.currentThread().getStackTrace();
        if (stack != null) {
            for (StackTraceElement frame : stack) {
                if ("executeForked".equals(frame.getMethodName())) {
                    inExecuteForked = true;
                } else if (inExecuteForked) {
                    return frame.getMethodName();
                }
            }
        }
        throw new IllegalStateException("Failed to find test name");
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
    protected Context setupContext(String... keyValuePairs) {
        try {
            String logFile = System.getProperty(LOG_FILE_PROPERTY);
            FileHandler handler = new FileHandler(logFile);
            handler.setFormatter(new SimpleFormatter());
            Context.Builder builder = Context.newBuilder().allowAllAccess(true).allowExperimentalOptions(true).logHandler(handler);
            setOptions(builder, DEFAULT_OPTIONS);
            setOptions(builder, keyValuePairs);
            return super.setupContext(builder);
        } catch (IOException ioe) {
            throw new AssertionError("Cannot write log file.", ioe);
        }
    }

    private static void setOptions(Context.Builder builder, String... keyValuePairs) {
        if ((keyValuePairs.length & 1) == 1) {
            throw new IllegalArgumentException("KeyValuePairs must have even length.");
        }
        for (int i = 0; i < keyValuePairs.length; i += 2) {
            builder.option(keyValuePairs[i], keyValuePairs[i + 1]);
        }
    }

    private static boolean isConfigured() {
        return System.getProperty(LOG_FILE_PROPERTY) != null;
    }

    private static void execute(String testName, Path logFile, String... additionalVmOptions) throws IOException, InterruptedException {
        SubprocessUtil.java(
                        configure(getVmArgs(), logFile, additionalVmOptions),
                        "com.oracle.mxtool.junit.MxJUnitWrapper",
                        String.format("%s#%s", ExceptionActionTest.class.getName(), testName));
    }

    private static List<String> configure(List<String> vmArgs, Path logFile, String... additionalVmOptions) {
        List<String> newVmArgs = new ArrayList<>();
        newVmArgs.addAll(vmArgs.stream().filter(new Predicate<String>() {
            @Override
            public boolean test(String vmArg) {
                // Filter out the LogFile option to prevent overriding of the unit tests log file by
                // a sub-process.
                return !vmArg.contains(GraalCompilerOptions.CompilationFailureAction.getName()) &&
                                !vmArg.contains(GraalCompilerOptions.CompilationBailoutAsFailure.getName()) &&
                                !vmArg.contains(GraalCompilerOptions.CrashAt.getName()) &
                                                !vmArg.contains("LogFile");
            }
        }).collect(Collectors.toList()));
        for (String additionalVmOption : additionalVmOptions) {
            newVmArgs.add(1, additionalVmOption);
        }
        newVmArgs.add(1, String.format("-D%s=%s", LOG_FILE_PROPERTY, logFile.toAbsolutePath().toString()));
        return newVmArgs;
    }

    private static List<String> getVmArgs() {
        List<String> vmArgs = SubprocessUtil.getVMCommandLine();
        vmArgs.add(SubprocessUtil.PACKAGE_OPENING_OPTIONS);
        return vmArgs;
    }

    private static boolean hasExit(Path logFile) {
        return contains(logFile, Pattern.compile(".*Exiting VM.*"));
    }

    private static boolean hasBailout(Path logFile) {
        return contains(logFile, Pattern.compile("[\\w.]*BailoutException.*")) || contains(logFile, Pattern.compile(".*Non permanent bailout.*"));
    }

    private static boolean hasOptFailedException(Path logFile) {
        return contains(logFile, Pattern.compile(".*OptimizationFailedException.*"));
    }

    private static boolean contains(Path logFile, Pattern pattern) {
        try {
            for (String line : Files.readAllLines(logFile)) {
                if (pattern.matcher(line).matches()) {
                    return true;
                }
            }
            return false;
        } catch (IOException ioe) {
            throw sthrow(ioe, RuntimeException.class);
        }
    }

    @SuppressWarnings({"unchecked", "unused"})
    private static <T extends Throwable> T sthrow(Throwable t, Class<T> type) throws T {
        throw (T) t;
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
