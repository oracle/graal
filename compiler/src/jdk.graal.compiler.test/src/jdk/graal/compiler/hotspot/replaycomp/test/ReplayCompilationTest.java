/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.hotspot.replaycomp.test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.EconomicSet;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import com.oracle.truffle.api.Truffle;

import jdk.graal.compiler.api.test.Graal;
import jdk.graal.compiler.core.CompilationWrapper;
import jdk.graal.compiler.core.GraalCompilerOptions;
import jdk.graal.compiler.core.test.GraalCompilerTest;
import jdk.graal.compiler.debug.DebugCloseable;
import jdk.graal.compiler.debug.DebugOptions;
import jdk.graal.compiler.debug.GlobalMetrics;
import jdk.graal.compiler.debug.LogStream;
import jdk.graal.compiler.debug.TTY;
import jdk.graal.compiler.hotspot.CompilationTask;
import jdk.graal.compiler.hotspot.CompilerConfigurationFactory;
import jdk.graal.compiler.hotspot.HotSpotGraalCompiler;
import jdk.graal.compiler.hotspot.HotSpotGraalCompilerFactory;
import jdk.graal.compiler.hotspot.HotSpotReplacementsImpl;
import jdk.graal.compiler.hotspot.replaycomp.CompilerInterfaceDeclarations;
import jdk.graal.compiler.hotspot.replaycomp.ReplayCompilationRunner;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.util.Providers;
import jdk.graal.compiler.runtime.RuntimeProvider;
import jdk.vm.ci.hotspot.HotSpotCompilationRequest;
import jdk.vm.ci.hotspot.HotSpotCompilationRequestResult;
import jdk.vm.ci.hotspot.HotSpotJVMCIRuntime;
import jdk.vm.ci.hotspot.HotSpotResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.runtime.JVMCICompiler;

/**
 * Tests for compilation recording and replay.
 */
public class ReplayCompilationTest extends GraalCompilerTest {
    /**
     * A separate encoded snippet scope is necessary in case the global encoded snippets have cache
     * replacements.
     */
    private static DebugCloseable snippetScope;

    @BeforeClass
    public static void setup() {
        Providers providers = Graal.getRequiredCapability(RuntimeProvider.class).getHostBackend().getProviders();
        HotSpotReplacementsImpl replacements = (HotSpotReplacementsImpl) providers.getReplacements();
        snippetScope = replacements.suppressEncodedSnippets();
        replacements.encode(getInitialOptions());
    }

    @AfterClass
    public static void teardown() {
        snippetScope.close();
    }

    private static int[] lengthsSquared(List<String> strings) {
        return strings.stream().mapToInt(String::length).map(i -> i * i).toArray();
    }

    private static Map<String, Long> wordCount(List<String> sentences) {
        return sentences.stream().flatMap(sentence -> Arrays.stream(sentence.split("\\s+"))).collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
    }

    @Test
    public void recordsOnRetryAndReplays() throws Throwable {
        lengthsSquared(List.of("foo", "bar", "baz"));
        runTest((temp) -> {
            String methodName = "lengthsSquared";
            ResolvedJavaMethod method = getResolvedJavaMethod(methodName);
            OptionValues initialOptions = getInitialOptions();
            String diagnoseOptionValue = DebugOptions.RecordForReplay.getName() + "=" + methodName;
            OptionValues crashAndDiagnoseOptions = new OptionValues(initialOptions, DebugOptions.DumpPath, temp.toString(),
                            GraalCompilerOptions.CompilationFailureAction, CompilationWrapper.ExceptionAction.Diagnose,
                            DebugOptions.DiagnoseOptions, diagnoseOptionValue, GraalCompilerOptions.CrashAt, methodName);
            /*
             * Run a regular compilation with a forced crash, then retry and record the compilation.
             * We need to run in a new compiler instance to override the dump path for diagnostics,
             * where the recorded compilation unit is saved.
             */
            HotSpotCompilationRequestResult regularResult = runRegularCompilation(method, crashAndDiagnoseOptions);
            assertTrue(regularResult.getFailure() != null);

            // Replay the compilation without forcing a crash and enable diagnostic options.
            EconomicSet<DebugOptions.OptimizationLogTarget> logTargets = EconomicSet.create();
            logTargets.add(DebugOptions.OptimizationLogTarget.Stdout);
            OptionValues replayOptions = new OptionValues(initialOptions, DebugOptions.DumpPath, temp.toString(),
                            DebugOptions.PrintGraph, DebugOptions.PrintGraphTarget.File, DebugOptions.Dump, ":1",
                            DebugOptions.OptimizationLog, logTargets, DebugOptions.Log, "", DebugOptions.PrintBackendCFG, true);
            replayCompilation(findReplayCompFile(temp.path), replayOptions);
        });
    }

    @Test
    public void recordAndExecuteReplayRunner() throws Throwable {
        wordCount(List.of("first test sentence", "second test sentence"));
        runTest((temp) -> {
            String methodName = "wordCount";
            ResolvedJavaMethod method = getResolvedJavaMethod(methodName);
            OptionValues initialOptions = getInitialOptions();
            OptionValues recordOptions = new OptionValues(initialOptions, DebugOptions.RecordForReplay, "*",
                            DebugOptions.DumpPath, temp.toString());
            HotSpotCompilationRequestResult regularResult = runRegularCompilation(method, recordOptions);
            assertTrue(regularResult.getFailure() == null);
            Path replayFile = findReplayCompFile(temp.path);
            String[][] argumentLists = new String[][]{
                            new String[]{"--compare-graphs=true", replayFile.toString()},
                            new String[]{"--compare-graphs=false", "--benchmark=true", "--iterations=1", temp.path.toString()}
            };
            for (String[] arguments : argumentLists) {
                ReplayCompilationRunner.ExitStatus status = ReplayCompilationRunner.run(arguments, TTY.out().out());
                assertTrue(status == ReplayCompilationRunner.ExitStatus.Success);
            }
        });
    }

    @Test
    public void unparsableReplayFileSucceeds() throws Throwable {
        runTest((temp) -> {
            /*
             * A replay file may not be parsable when the compiler thread exits during writing -
             * this is not an error.
             */
            assertTrue(Path.of(temp.path.toString(), "empty.json").toFile().createNewFile());
            ReplayCompilationRunner.ExitStatus status = ReplayCompilationRunner.run(new String[]{temp.path.toString()}, TTY.out().out());
            assertTrue(status == ReplayCompilationRunner.ExitStatus.Success);
        });
    }

    @Test
    public void emptyLauncherInputFails() throws Throwable {
        runTest((temp) -> {
            ReplayCompilationRunner.ExitStatus status = ReplayCompilationRunner.run(new String[]{temp.path.toString()}, TTY.out().out());
            assertTrue(status == ReplayCompilationRunner.ExitStatus.Failure);
        });
    }

    @FunctionalInterface
    interface TestRunner {
        void run(TemporaryDirectory temp) throws Throwable;
    }

    @SuppressWarnings("try")
    private static void runTest(TestRunner test) throws Throwable {
        Truffle.getRuntime(); // Initialize the Truffle runtime and enable the HostInliningPhase.
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream(); TTY.Filter filter = new TTY.Filter(new LogStream(outputStream))) {
            try (TemporaryDirectory temp = new TemporaryDirectory("ReplayCompilationTest")) {
                test.run(temp);
            } catch (Throwable throwable) {
                System.err.println(outputStream.toString(Charset.defaultCharset()));
                throw throwable;
            }
        }
    }

    private static HotSpotCompilationRequestResult runRegularCompilation(ResolvedJavaMethod method, OptionValues options) {
        HotSpotJVMCIRuntime jvmciRuntime = HotSpotJVMCIRuntime.runtime();
        RuntimeProvider runtimeProvider = Graal.getRequiredCapability(RuntimeProvider.class);
        CompilerConfigurationFactory configFactory = CompilerConfigurationFactory.selectFactory(runtimeProvider.getCompilerConfigurationName(), options, jvmciRuntime);
        HotSpotGraalCompiler compiler = HotSpotGraalCompilerFactory.createCompiler("VM-test", jvmciRuntime, options, configFactory, null);
        HotSpotCompilationRequest request = new HotSpotCompilationRequest((HotSpotResolvedJavaMethod) method, JVMCICompiler.INVOCATION_ENTRY_BCI, 0L);
        CompilationTask task = new CompilationTask(jvmciRuntime, compiler, request, true, false, false, false);
        return task.runCompilation(options);
    }

    private static void replayCompilation(Path replayCompFile, OptionValues options) throws ReplayCompilationRunner.ReplayLauncherFailure {
        CompilerInterfaceDeclarations declarations = CompilerInterfaceDeclarations.build();
        HotSpotJVMCIRuntime jvmciRuntime = HotSpotJVMCIRuntime.runtime();
        RuntimeProvider runtimeProvider = Graal.getRequiredCapability(RuntimeProvider.class);
        CompilerConfigurationFactory configFactory = CompilerConfigurationFactory.selectFactory(runtimeProvider.getCompilerConfigurationName(), options, jvmciRuntime);
        try (ReplayCompilationRunner.Reproducer reproducer = ReplayCompilationRunner.Reproducer.initializeFromFile(replayCompFile.toString(),
                        declarations, jvmciRuntime, options, configFactory, new GlobalMetrics(), TTY.out().out(), EconomicMap.create())) {
            reproducer.compile().verify(false);
        }
    }

    private static Path findReplayCompFile(Path dumpPath) throws IOException {
        Path replayCompDirectory;
        try (Stream<Path> replayCompDirectoryStream = Files.find(dumpPath, 8,
                        (path, attributes) -> attributes.isDirectory() && path.toFile().getName().equals("replaycomp"))) {
            replayCompDirectory = replayCompDirectoryStream.findAny().orElseThrow();
        }
        try (Stream<Path> replayCompFileStream = Files.list(replayCompDirectory)) {
            return replayCompFileStream.findAny().orElseThrow();
        }
    }
}
