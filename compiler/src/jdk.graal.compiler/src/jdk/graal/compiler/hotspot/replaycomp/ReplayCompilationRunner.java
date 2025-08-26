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
package jdk.graal.compiler.hotspot.replaycomp;

import static jdk.graal.compiler.core.common.NativeImageSupport.inRuntimeCode;

import java.io.Closeable;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintStream;
import java.io.Serial;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import org.graalvm.collections.EconomicMap;

import jdk.graal.compiler.core.GraalCompilerOptions;
import jdk.graal.compiler.core.common.LibGraalSupport;
import jdk.graal.compiler.debug.GlobalMetrics;
import jdk.graal.compiler.hotspot.CompilerConfigurationFactory;
import jdk.graal.compiler.hotspot.HotSpotGraalCompiler;
import jdk.graal.compiler.hotspot.HotSpotGraalCompilerFactory;
import jdk.graal.compiler.hotspot.HotSpotGraalOptionValues;
import jdk.graal.compiler.hotspot.HotSpotGraalRuntime;
import jdk.graal.compiler.hotspot.HotSpotGraalRuntimeProvider;
import jdk.graal.compiler.hotspot.HotSpotReplacementsImpl;
import jdk.graal.compiler.hotspot.Platform;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.util.args.BooleanValue;
import jdk.graal.compiler.util.args.IntegerValue;
import jdk.graal.compiler.util.args.OptionValue;
import jdk.graal.compiler.util.args.Program;
import jdk.graal.compiler.util.args.StringValue;
import jdk.graal.compiler.util.json.JsonParserException;
import jdk.vm.ci.code.CompilationRequest;
import jdk.vm.ci.code.CompilationRequestResult;
import jdk.vm.ci.hotspot.HotSpotCompilationRequest;
import jdk.vm.ci.hotspot.HotSpotJVMCIRuntime;

//JaCoCo Exclude

/**
 * The entry point for running replay compilations in jargraal and libgraal.
 *
 * @see ReplayCompilationSupport
 * @see CompilerInterfaceDeclarations
 */
@LibGraalSupport.HostedOnly(unlessTrue = ReplayCompilationSupport.ENABLE_REPLAY_LAUNCHER_PROP)
public class ReplayCompilationRunner {
    /**
     * The exit status of a replay compilation run.
     */
    public enum ExitStatus {
        /**
         * Successful execution.
         */
        Success(0),

        /**
         * Failed execution.
         */
        Failure(1);

        private final int status;

        ExitStatus(int status) {
            this.status = status;
        }

        /**
         * Exits the virtual machine with the corresponding status code.
         */
        public void exitVM() {
            System.exit(status);
        }

        /**
         * Returns the exit status code.
         */
        public int getStatus() {
            return status;
        }
    }

    /**
     * Runs the replay compilation launcher based on the provided command-line arguments.
     *
     * @param args command-line arguments
     * @param out output stream for printing messages
     * @return the exit status of the launcher
     */
    @SuppressWarnings("try")
    public static ExitStatus run(String[] args, PrintStream out) {
        Program program = new Program("mx replaycomp", "Replay compilations from files.");
        OptionValue<Boolean> benchmarkArg = program.addNamed("--benchmark", new BooleanValue("true|false", false, "Replay compilations as a benchmark."));
        OptionValue<Integer> iterationsArg = program.addNamed("--iterations", new IntegerValue("n", 10, "The number of benchmark iterations."));
        OptionValue<Boolean> compareGraphsArg = program.addNamed("--compare-graphs", new BooleanValue("true|false", false, "Verify that the replayed graph equals the recorded one."));
        OptionValue<String> inputPathArg = program.addPositional(new StringValue("TARGET", "Path to a directory with replay compilation files (or path to a single file)."));
        program.parseAndValidate(args, true);
        Path inputPath = Path.of(inputPathArg.getValue());
        List<Path> inputFiles;
        try {
            inputFiles = findJsonFiles(inputPath);
        } catch (IOException e) {
            out.println(e.getMessage());
            return ExitStatus.Failure;
        }
        if (inputFiles.isEmpty()) {
            out.println("No replay files found in " + inputPath);
            return ExitStatus.Failure;
        }

        OptionValues systemOptions = new OptionValues(HotSpotGraalOptionValues.parseOptions());
        OptionValues options = new OptionValues(systemOptions, GraalCompilerOptions.SystemicCompilationFailureRate, 0);
        CompilerInterfaceDeclarations declarations = CompilerInterfaceDeclarations.build();
        HotSpotJVMCIRuntime runtime = HotSpotJVMCIRuntime.runtime();
        CompilerConfigurationFactory factory = CompilerConfigurationFactory.selectFactory(null, options, runtime);

        LibGraalSupport libgraal = LibGraalSupport.INSTANCE;
        GlobalMetrics globalMetrics = new GlobalMetrics();
        if (benchmarkArg.getValue()) {
            List<Reproducer> reproducers = new ArrayList<>();
            EconomicMap<Object, Object> internPool = EconomicMap.create();
            for (Path file : inputFiles) {
                try (AutoCloseable ignored = libgraal != null ? libgraal.openCompilationRequestScope() : null) {
                    try {
                        reproducers.add(Reproducer.initializeFromFile(file.toString(), declarations, runtime, options,
                                        factory, globalMetrics, out, internPool));
                    } catch (Exception e) {
                        out.println("Preparation failed for " + file + ": " + e);
                    }
                } catch (Exception e) {
                    return ExitStatus.Failure;
                }
            }
            internPool.clear();
            Runtime javaRuntime = Runtime.getRuntime();
            for (int i = 0; i < iterationsArg.getValue(); i++) {
                out.printf("====== replaycomp iteration %d started ======%n", i);
                double memBefore = (javaRuntime.totalMemory() - javaRuntime.freeMemory()) / 1_000_000d;
                long before = System.nanoTime();
                System.gc();
                long afterGC = System.nanoTime();
                double memAfter = (javaRuntime.totalMemory() - javaRuntime.freeMemory()) / 1_000_000d;
                double gcMillis = (afterGC - before) / 1_000_000d;
                out.printf("GC before operation: completed in %.3f ms, heap usage %.3f MB -> %.3f MB.%n", gcMillis, memBefore, memAfter);
                int codeHash = 0;
                for (Reproducer reproducer : reproducers) {
                    try (AutoCloseable ignored = libgraal != null ? libgraal.openCompilationRequestScope() : null) {
                        ReplayResult replayResult = reproducer.compile();
                        replayResult.verify(compareGraphsArg.getValue());
                        codeHash = codeHash * 31 + Arrays.hashCode(replayResult.replayedArtifacts().result().getTargetCode());
                    } catch (Exception e) {
                        out.println("Replay failed: " + e);
                        e.printStackTrace(out);
                        return ExitStatus.Failure;
                    }
                }
                out.printf("Compiled code hash: %d%n", codeHash);
                long after = System.nanoTime();
                double iterMillis = (after - before) / 1_000_000d;
                out.printf("====== replaycomp iteration %d completed (%.3f ms) ======%n", i, iterMillis);
            }
            for (Reproducer reproducer : reproducers) {
                reproducer.close();
            }
        } else {
            ReplayCompilationStatistics statistics = new ReplayCompilationStatistics();
            for (Path file : inputFiles) {
                ReplayCompilationTask task = statistics.startTask(file.toString());
                try (AutoCloseable ignored = libgraal != null ? libgraal.openCompilationRequestScope() : null;
                                Reproducer reproducer = Reproducer.initializeFromFile(file.toString(), declarations, runtime,
                                                options, factory, globalMetrics, out, EconomicMap.create())) {
                    reproducer.compile().verify(compareGraphsArg.getValue());
                    out.println("Successfully replayed " + reproducer.request);
                } catch (ReplayParserFailure failure) {
                    out.println("Replay failed: " + failure.getMessage());
                    task.setFailureReason(failure.getMessage());
                } catch (Exception e) {
                    out.println("Replay failed: " + e);
                    e.printStackTrace(out);
                    return ExitStatus.Failure;
                }
            }
            out.println();
            statistics.printStatistics(out);
        }
        globalMetrics.print(options);
        return ExitStatus.Success;
    }

    /**
     * Recursively searches for JSON files within the given root directory (or file) and its
     * subdirectories. The returned list of paths is sorted lexicographically to ensure consistency
     * between runs.
     *
     * @param root the root directory/file to start the search from
     * @return a list of paths to JSON files found within the root directory/file and its
     *         subdirectories
     * @throws IOException if an I/O error occurs while traversing the file tree
     */
    private static List<Path> findJsonFiles(Path root) throws IOException {
        List<Path> paths = new ArrayList<>();
        Files.walkFileTree(root, new FileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (Files.isRegularFile(file) && file.toString().endsWith(".json")) {
                    paths.add(file);
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) {
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) {
                return FileVisitResult.CONTINUE;
            }
        });
        paths.sort(Path::compareTo);
        return paths;
    }

    public static sealed class ReplayLauncherFailure extends Exception {
        @Serial private static final long serialVersionUID = -1610853593485835928L;

        public ReplayLauncherFailure(String message) {
            super(message);
        }
    }

    private static final class ReplayParserFailure extends ReplayLauncherFailure {
        @Serial private static final long serialVersionUID = 6521004798119106469L;

        ReplayParserFailure(String message) {
            super(message);
        }
    }

    /**
     * The result of a single replay compilation.
     *
     * @param request the compilation request for the replay compilation
     * @param result the result of the replayed compilation
     * @param replayedArtifacts the artifacts produced by the replayed compilation
     * @param originalGraph the canonical graph string of the final original graph (if available)
     */
    public record ReplayResult(CompilationRequest request, CompilationRequestResult result, ReplayCompilationSupport.CompilationArtifacts replayedArtifacts, String originalGraph) {
        public void verify(boolean verifyGraphs) throws ReplayLauncherFailure {
            if (result.getFailure() == null) {
                if (!verifyGraphs) {
                    return;
                }
                if (originalGraph == null) {
                    throw new ReplayLauncherFailure("Cannot verify the replayed graph for " + request);
                } else if (!originalGraph.equals(replayedArtifacts.finalCanonicalGraph())) {
                    throw new ReplayLauncherFailure("Replay completed but final graphs differ");
                }
            } else {
                throw new ReplayLauncherFailure(Objects.toString(result.getFailure()));
            }
        }
    }

    /**
     * A recorded compilation that is ready to be compiled (potentially multiple times).
     */
    public static final class Reproducer implements Closeable {
        /**
         * The compiler instance used for replaying the compilation.
         */
        private final HotSpotGraalCompiler replayCompiler;

        /**
         * The compilation request for the replayed compilation.
         */
        private final HotSpotCompilationRequest request;

        /**
         * The final graph of the recorded compilation.
         */
        private final String finalGraph;

        /**
         * The compiler options for replay.
         */
        private final OptionValues options;

        private Reproducer(HotSpotGraalCompiler replayCompiler, HotSpotCompilationRequest request, String finalGraph, OptionValues options) {
            this.replayCompiler = replayCompiler;
            this.request = request;
            this.finalGraph = finalGraph;
            this.options = options;
        }

        /**
         * Creates a new reproducer instance from a JSON file.
         *
         * @param fileName the name of the JSON file containing the recorded compilation
         * @param declarations describes the compiler interface
         * @param runtime the JVMCI runtime
         * @param options the options for the replay compiler
         * @param factory the factory used to create the compiler configuration
         * @param globalMetrics the metrics object where metrics from the replayed compilations are
         *            accumulated
         * @param out stream for debug output
         * @param internPool the pool of interned objects
         * @return a new reproducer instance
         * @throws ReplayLauncherFailure if an error occurs during initialization
         */
        @SuppressWarnings("try")
        public static Reproducer initializeFromFile(String fileName, CompilerInterfaceDeclarations declarations, HotSpotJVMCIRuntime runtime,
                        OptionValues options, CompilerConfigurationFactory factory, GlobalMetrics globalMetrics, PrintStream out, EconomicMap<Object, Object> internPool) throws ReplayLauncherFailure {
            if (!inRuntimeCode() && !HotSpotReplacementsImpl.snippetsAreEncoded()) {
                out.println("Encode snippets");
                HotSpotJVMCIRuntime.runtime().getCompiler();
            }
            ReplayCompilationProxies proxies = new ReplayCompilationProxies(declarations, globalMetrics, options);
            out.println("Loading " + fileName);
            RecordedOperationPersistence.RecordedCompilationUnit compilationUnit;
            try (FileReader reader = new FileReader(fileName)) {
                RecordedOperationPersistence persistence = new RecordedOperationPersistence(declarations, Platform.ofCurrentHost(),
                                HotSpotJVMCIRuntime.runtime().getHostJVMCIBackend().getTarget());
                compilationUnit = persistence.load(reader, proxies::createProxy);
                proxies.setTargetPlatform(compilationUnit.platform());
                proxies.loadOperationResults(compilationUnit.operations(), internPool);
            } catch (Exception exception) {
                if (exception instanceof JsonParserException parserException && parserException.isAtEOF().isTrue()) {
                    throw new ReplayParserFailure("Failed to parse an incomplete JSON file (likely caused by VM shutdown during the recorded compilation).");
                }
                throw new ReplayParserFailure("Parsing failed due to " + exception.getMessage());
            }
            HotSpotCompilationRequest request = compilationUnit.request();
            if (LibGraalSupport.inLibGraalRuntime() && !compilationUnit.isLibgraal()) {
                throw new ReplayLauncherFailure("Cannot replay jargraal compilation " + request + " in libgraal");
            }
            out.println("Initializing the replay compiler for " + request);
            HotSpotGraalCompiler replayCompiler = HotSpotGraalCompilerFactory.createCompiler("VM-replay", runtime, options, factory, new ReplayCompilationSupport(proxies, factory.getName()));
            HotSpotGraalRuntimeProvider graalRuntime = replayCompiler.getGraalRuntime();
            if (!graalRuntime.getCompilerConfigurationName().equals(compilationUnit.compilerConfiguration())) {
                throw new ReplayLauncherFailure(("Compiler configuration mismatch: the task was compiled using " + compilationUnit.compilerConfiguration() +
                                " but the initialized compiler is " + graalRuntime.getCompilerConfigurationName()));
            }
            graalRuntime.getReplayCompilationSupport().setRecordedForeignCallLinkages(compilationUnit.linkages());
            return new Reproducer(replayCompiler, request, compilationUnit.finalGraph(), options);
        }

        /**
         * Compiles (or recompiles) the method using the recorded data.
         *
         * @return a replay result object containing the compilation result and the replayed graph
         */
        @SuppressWarnings("try")
        public ReplayResult compile() {
            ReplayCompilationSupport support = replayCompiler.getGraalRuntime().getReplayCompilationSupport();
            CompilationRequestResult result = replayCompiler.compileMethod(request, true, options);
            ReplayCompilationSupport.CompilationArtifacts replayedArtifacts = support.clearCompilationArtifacts();
            return new ReplayResult(request, result, replayedArtifacts, finalGraph);
        }

        /**
         * Shuts down the {@link HotSpotGraalRuntime} associated with this reproducer.
         */
        @Override
        public void close() {
            ((HotSpotGraalRuntime) replayCompiler.getGraalRuntime()).shutdown();
        }
    }

    /**
     * Tracks the outcome of a single replayed compilation.
     */
    private static class ReplayCompilationTask {
        private final String fileName;

        private boolean success;

        private String failureReason;

        ReplayCompilationTask(String fileName) {
            this.fileName = fileName;
            this.success = true;
        }

        public void setFailureReason(String newFailureReason) {
            failureReason = newFailureReason;
            success = false;
        }

        public boolean isSuccess() {
            return success;
        }

        public boolean isFailure() {
            return !success;
        }
    }

    /**
     * Tracks the outcomes of all replayed compilations, which is used to print summary statistics
     * at the end.
     */
    private static class ReplayCompilationStatistics {
        private final List<ReplayCompilationTask> tasks;

        ReplayCompilationStatistics() {
            this.tasks = new ArrayList<>();
        }

        public ReplayCompilationTask startTask(String fileName) {
            ReplayCompilationTask task = new ReplayCompilationTask(fileName);
            tasks.add(task);
            return task;
        }

        public void printStatistics(PrintStream out) {
            out.printf("Completed %d replay compilation task(s)%n", tasks.size());
            out.printf("%8d task(s) succeeded%n", successCount());
            out.printf("%8d task(s) failed%n", failureCount());
            for (ReplayCompilationTask task : failedTasks()) {
                out.printf("           %s %s%n", task.fileName, task.failureReason);
            }
        }

        private long successCount() {
            return tasks.stream().filter(ReplayCompilationTask::isSuccess).count();
        }

        private long failureCount() {
            return tasks.stream().filter(ReplayCompilationTask::isFailure).count();
        }

        private List<ReplayCompilationTask> failedTasks() {
            return tasks.stream().filter(ReplayCompilationTask::isFailure).toList();
        }
    }
}
