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
import static jdk.graal.compiler.serviceprovider.GraalServices.getCurrentThreadAllocatedBytes;
import static jdk.graal.compiler.serviceprovider.GraalServices.getCurrentThreadCpuTime;

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

import jdk.graal.compiler.code.CompilationResult;
import jdk.graal.compiler.core.GraalCompilerOptions;
import jdk.graal.compiler.core.common.LibGraalSupport;
import jdk.graal.compiler.debug.GlobalMetrics;
import jdk.graal.compiler.debug.PathUtilities;
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
import jdk.graal.compiler.util.args.Command;
import jdk.graal.compiler.util.args.CommandGroup;
import jdk.graal.compiler.util.args.IntegerValue;
import jdk.graal.compiler.util.args.OptionValue;
import jdk.graal.compiler.util.args.Program;
import jdk.graal.compiler.util.args.StringValue;
import jdk.graal.compiler.util.json.JsonParserException;
import jdk.vm.ci.code.CompilationRequest;
import jdk.vm.ci.code.CompilationRequestResult;
import jdk.vm.ci.hotspot.HotSpotCompilationRequest;
import jdk.vm.ci.hotspot.HotSpotJVMCIRuntime;

/**
 * The entry point for running replay compilations in jargraal and libgraal.
 *
 * @see ReplayCompilationSupport
 * @see CompilerInterfaceDeclarations
 */
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
        OptionValue<Boolean> compareGraphsArg = program.addNamed("--compare-graphs", new BooleanValue("true|false", false, "Verify that the replayed graph equals the recorded one."));
        OptionValue<String> inputPathArg = program.addPositional(new StringValue("TARGET", "Path to a directory with replay compilation files (or path to a single file)."));
        CommandGroup<LauncherCommand> commandGroup = new CommandGroup<>("COMMAND", new ReplayCommand(), "The mode to replay in.");
        commandGroup.addCommand(new BenchmarkCommand());
        program.addCommandGroup(commandGroup);
        program.parseAndValidate(args, true);
        List<Path> inputFiles = getInputFiles(out, inputPathArg);
        if (inputFiles == null) {
            return ExitStatus.Failure;
        }
        return commandGroup.getSelectedCommand().run(out, inputFiles, compareGraphsArg.getValue());
    }

    /**
     * A command implementing one use case of the replay compilation launcher.
     */
    private abstract static class LauncherCommand extends Command {
        /**
         * Constructs a launcher command.
         *
         * @param name the name of the command
         * @param description the description of the command
         */
        private LauncherCommand(String name, String description) {
            super(name, description);
        }

        /**
         * Runs the command.
         *
         * @param out the stream for output
         * @param inputFiles the files that should be replayed
         * @param compareGraphs whether the replayed graph should be compared with the recorded one
         * @return the exit status of the launcher
         */
        public abstract ExitStatus run(PrintStream out, List<Path> inputFiles, boolean compareGraphs);
    }

    /**
     * A command that replays a set of files (e.g., for debugging purposes).
     */
    private static final class ReplayCommand extends LauncherCommand {
        private ReplayCommand() {
            super("--replay", "Replay compilations.");
        }

        @SuppressWarnings("try")
        @Override
        public ExitStatus run(PrintStream out, List<Path> inputFiles, boolean compareGraphs) {
            OptionValues systemOptions = new OptionValues(HotSpotGraalOptionValues.parseOptions());
            OptionValues options = new OptionValues(systemOptions, GraalCompilerOptions.SystemicCompilationFailureRate, 0);
            CompilerInterfaceDeclarations declarations = CompilerInterfaceDeclarations.build();
            HotSpotJVMCIRuntime runtime = HotSpotJVMCIRuntime.runtime();
            CompilerConfigurationFactory factory = CompilerConfigurationFactory.selectFactory(null, options, runtime);
            LibGraalSupport libgraal = LibGraalSupport.INSTANCE;
            GlobalMetrics globalMetrics = new GlobalMetrics();
            ReplayTaskStatistics statistics = new ReplayTaskStatistics();
            for (Path file : inputFiles) {
                ReplayCompilationTask task = statistics.startTask(file.toString());
                try (AutoCloseable ignored = libgraal != null ? libgraal.openCompilationRequestScope() : null;
                                Reproducer reproducer = Reproducer.initializeFromFile(file.toString(), declarations, runtime,
                                                options, factory, globalMetrics, out, EconomicMap.create())) {
                    reproducer.compile().verify(compareGraphs);
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
            globalMetrics.print(options);
            return ExitStatus.Success;
        }
    }

    private static final double ONE_MILLION = 1_000_000d;

    /**
     * A command that runs replay compilation as a benchmark.
     */
    private static final class BenchmarkCommand extends LauncherCommand {
        private final OptionValue<Integer> iterationsArg;

        private final OptionValue<String> resultsFileArg;

        private BenchmarkCommand() {
            super("--benchmark", "Replay compilations as a benchmark.");
            iterationsArg = addNamed("--iterations", new IntegerValue("N", 10, "The number of benchmark iterations."));
            resultsFileArg = addNamed("--results-file", new StringValue("RESULTS_FILE", null, "Write benchmark metrics to the file in CSV format."));
        }

        @SuppressWarnings("try")
        @Override
        public ExitStatus run(PrintStream out, List<Path> inputFiles, boolean compareGraphs) {
            OptionValues options = new OptionValues(HotSpotGraalOptionValues.parseOptions());
            CompilerInterfaceDeclarations declarations = CompilerInterfaceDeclarations.build();
            HotSpotJVMCIRuntime runtime = HotSpotJVMCIRuntime.runtime();
            CompilerConfigurationFactory factory = CompilerConfigurationFactory.selectFactory(null, options, runtime);
            LibGraalSupport libgraal = LibGraalSupport.INSTANCE;
            GlobalMetrics globalMetrics = new GlobalMetrics();
            List<Reproducer> reproducers = new ArrayList<>();
            EconomicMap<Object, Object> internPool = EconomicMap.create();
            for (Path file : inputFiles) {
                try (AutoCloseable ignored = libgraal != null ? libgraal.openCompilationRequestScope() : null) {
                    reproducers.add(Reproducer.initializeFromFile(file.toString(), declarations, runtime, options,
                                    factory, globalMetrics, out, internPool));
                } catch (ReplayParserFailure failure) {
                    out.printf("Preparation failed for %s: %s%n", file, failure.getMessage());
                    return ExitStatus.Failure;
                } catch (Exception e) {
                    out.printf("Preparation failed for %s, which may be caused by breaking JVMCI or replay changes. The causing exception is:%n", file);
                    e.printStackTrace(out);
                    return ExitStatus.Failure;
                }
            }
            internPool.clear();
            if (reproducers.isEmpty()) {
                out.println("There are no compilations to replay");
                return ExitStatus.Failure;
            }
            try (PrintStream outStat = (resultsFileArg.isSet()) ? new PrintStream(PathUtilities.openOutputStream(resultsFileArg.getValue())) : null) {
                for (int i = 0; i < iterationsArg.getValue(); i++) {
                    performCollection(out);
                    BenchmarkIterationMetrics metrics = new BenchmarkIterationMetrics(i);
                    metrics.beginIteration(out, outStat);
                    for (Reproducer reproducer : reproducers) {
                        try (AutoCloseable ignored = libgraal != null ? libgraal.openCompilationRequestScope() : null) {
                            ReplayResult replayResult = reproducer.compile();
                            replayResult.verify(compareGraphs);
                            metrics.addVerifiedResult(replayResult);
                        } catch (Exception e) {
                            out.println("Replay failed: " + e);
                            e.printStackTrace(out);
                            return ExitStatus.Failure;
                        }
                    }
                    metrics.endIteration(out, outStat);
                }
            } catch (IOException e) {
                out.println("Failed to write benchmark statistics to " + resultsFileArg.getValue());
                return ExitStatus.Failure;
            }
            for (Reproducer reproducer : reproducers) {
                reproducer.close();
            }
            globalMetrics.print(options);
            return ExitStatus.Success;
        }

        private static void performCollection(PrintStream out) {
            Runtime javaRuntime = Runtime.getRuntime();
            double memBefore = (javaRuntime.totalMemory() - javaRuntime.freeMemory()) / ONE_MILLION;
            long gcBeforeTimestamp = System.nanoTime();
            System.gc();
            long gcAfterTimestamp = System.nanoTime();
            double memAfter = (javaRuntime.totalMemory() - javaRuntime.freeMemory()) / ONE_MILLION;
            double gcMillis = (gcAfterTimestamp - gcBeforeTimestamp) / ONE_MILLION;
            out.printf("GC before operation: completed in %.3f ms, heap usage %.3f MB -> %.3f MB.%n", gcMillis, memBefore, memAfter);
        }
    }

    private static List<Path> getInputFiles(PrintStream out, OptionValue<String> inputPathArg) {
        Path inputPath = Path.of(inputPathArg.getValue());
        List<Path> inputFiles;
        try {
            inputFiles = findJsonFiles(inputPath);
        } catch (IOException e) {
            out.println(e.getMessage());
            return null;
        }
        if (inputFiles.isEmpty()) {
            out.println("No replay files found in " + inputPath);
            return null;
        }
        return inputFiles;
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
    private static class ReplayTaskStatistics {
        private final List<ReplayCompilationTask> tasks;

        ReplayTaskStatistics() {
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

    /**
     * Collects and prints compilation metrics for one iteration of a replay benchmark.
     */
    private static final class BenchmarkIterationMetrics {
        private final int iteration;

        private long beginWallTime;

        private long beginThreadTime;

        private long beginMemory;

        private int compiledBytecodes;

        private int targetCodeSize;

        private int targetCodeHash;

        private BenchmarkIterationMetrics(int iteration) {
            this.iteration = iteration;
        }

        public void beginIteration(PrintStream out, PrintStream outStat) {
            if (iteration == 0 && outStat != null) {
                outStat.println("iteration,wall_time_ns,thread_time_ns,allocated_memory,compiled_bytecodes,target_code_size,target_code_hash");
            }
            out.printf("====== replaycomp iteration %d started ======%n", iteration);
            beginMemory = getCurrentThreadAllocatedBytes();
            beginWallTime = System.nanoTime();
            beginThreadTime = getCurrentThreadCpuTime();
        }

        public void addVerifiedResult(ReplayResult replayResult) {
            CompilationResult result = replayResult.replayedArtifacts().result();
            compiledBytecodes += result.getBytecodeSize();
            targetCodeSize += result.getTargetCodeSize();
            targetCodeHash = targetCodeHash * 31 + Arrays.hashCode(result.getTargetCode());
        }

        public void endIteration(PrintStream out, PrintStream outStat) {
            long endThreadTime = getCurrentThreadCpuTime();
            long endWallTime = System.nanoTime();
            long endMemory = getCurrentThreadAllocatedBytes();
            long wallTimeNanos = endWallTime - beginWallTime;
            long threadTimeNanos = endThreadTime - beginThreadTime;
            long allocatedMemory = endMemory - beginMemory;
            if (outStat != null) {
                outStat.printf("%d,%d,%d,%d,%d,%d,%08x%n", iteration, wallTimeNanos, threadTimeNanos, allocatedMemory, compiledBytecodes, targetCodeSize, targetCodeHash);
            }
            out.printf("         Thread time: %12.3f ms%n", threadTimeNanos / ONE_MILLION);
            out.printf("    Allocated memory: %12.3f MB%n", allocatedMemory / ONE_MILLION);
            out.printf("  Compiled bytecodes: %12d B%n", compiledBytecodes);
            out.printf("    Target code size: %12d B%n", targetCodeSize);
            out.printf("    Target code hash:     %08x%n", targetCodeHash);
            out.printf("====== replaycomp iteration %d completed (%.3f ms) ======%n", iteration, wallTimeNanos / ONE_MILLION);
        }
    }
}
