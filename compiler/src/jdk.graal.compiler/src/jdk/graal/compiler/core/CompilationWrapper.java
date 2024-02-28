/*
 * Copyright (c) 2017, 2022, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.core;

import static jdk.graal.compiler.core.CompilationWrapper.ExceptionAction.ExitVM;
import static jdk.graal.compiler.core.common.GraalOptions.TrackNodeSourcePosition;
import static jdk.graal.compiler.debug.DebugOptions.Count;
import static jdk.graal.compiler.debug.DebugOptions.Dump;
import static jdk.graal.compiler.debug.DebugOptions.DumpPath;
import static jdk.graal.compiler.debug.DebugOptions.MethodFilter;
import static jdk.graal.compiler.debug.DebugOptions.PrintBackendCFG;
import static jdk.graal.compiler.debug.DebugOptions.Time;
import static jdk.graal.compiler.debug.PathUtilities.getPath;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.Formatter;
import java.util.Map;

import jdk.graal.compiler.debug.DebugCloseable;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.debug.DebugOptions;
import jdk.graal.compiler.debug.DiagnosticsOutputDirectory;
import jdk.graal.compiler.debug.PathUtilities;
import jdk.graal.compiler.debug.TTY;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.serviceprovider.GlobalAtomicLong;
import jdk.vm.ci.code.BailoutException;

/**
 * Wrapper for a compilation that centralizes what action to take based on
 * {@link GraalCompilerOptions#CompilationBailoutAsFailure} and
 * {@link GraalCompilerOptions#CompilationFailureAction} when an uncaught exception occurs during
 * compilation.
 */
public abstract class CompilationWrapper<T> {

    /**
     * Actions to take upon an exception being raised during compilation performed via
     * {@link CompilationWrapper}. The actions are with respect to what the user sees on the
     * console. The compilation requester determines what ultimate action is taken in
     * {@link CompilationWrapper#handleException(Throwable)}.
     *
     * The actions are in ascending order of verbosity.
     */
    public enum ExceptionAction {
        /**
         * Print nothing to the console.
         */
        Silent,

        /**
         * Print a stack trace to the console.
         */
        Print,

        /**
         * An exception causes the compilation to be retried with extra diagnostics enabled.
         */
        Diagnose,

        /**
         * Same as {@link #Diagnose} except that the VM process is exited after retrying.
         */
        ExitVM;

        private static final ExceptionAction[] VALUES = values();

        /**
         * Gets the action that is one level less verbose than this action, bottoming out at the
         * least verbose action.
         */
        ExceptionAction quieter() {
            assert ExceptionAction.Silent.ordinal() == 0 : "Silent must be first";
            int index = Math.max(ordinal() - 1, 0);
            return VALUES[index];
        }
    }

    private final DiagnosticsOutputDirectory outputDirectory;

    private final Map<ExceptionAction, Integer> problemsHandledPerAction;

    /**
     * @param outputDirectory object used to access a directory for dumping if the compilation is
     *            re-executed
     * @param problemsHandledPerAction map used to count the number of compilation failures or
     *            bailouts handled by each action. This is provided by the caller as it is expected
     *            to be shared between instances of {@link CompilationWrapper}.
     */
    public CompilationWrapper(DiagnosticsOutputDirectory outputDirectory, Map<ExceptionAction, Integer> problemsHandledPerAction) {
        this.outputDirectory = outputDirectory;
        this.problemsHandledPerAction = problemsHandledPerAction;
    }

    /**
     * Handles an uncaught exception.
     *
     * @param t an exception thrown during {@link #run(DebugContext)}
     * @return a value representing the result of a failed compilation (may be {@code null})
     */
    protected abstract T handleException(Throwable t);

    /**
     * Gets the action to take based on the value of
     * {@link GraalCompilerOptions#CompilationBailoutAsFailure} and
     * {@link GraalCompilerOptions#CompilationFailureAction} in {@code options}.
     *
     * Subclasses can override this to choose a different action.
     *
     * @param cause the cause of the bailout or failure
     */
    protected ExceptionAction lookupAction(OptionValues options, Throwable cause) {
        if (isNonFailureBailout(options, cause)) {
            return ExceptionAction.Silent;
        }
        return GraalCompilerOptions.CompilationFailureAction.getValue(options);
    }

    private static boolean isNonFailureBailout(OptionValues options, Throwable cause) {
        return cause instanceof BailoutException && !GraalCompilerOptions.CompilationBailoutAsFailure.getValue(options);
    }

    /**
     * Perform the compilation wrapped by this object.
     *
     * @param debug the debug context to use for the compilation
     */
    protected abstract T performCompilation(DebugContext debug);

    /**
     * Dump any objects for the original failure.
     *
     * @param errorContext the context for dump
     * @param cause the exception that caused the failure
     */
    protected void dumpOnError(DebugContext errorContext, Throwable cause) {
    }

    /**
     * Gets a value that represents the input to the compilation.
     */
    @Override
    public abstract String toString();

    /**
     * Creates the {@link DebugContext} to use when retrying a compilation.
     *
     * @param initialDebug the debug context used in the failing compilation
     * @param options the options for configuring the debug context
     * @param logStream the log stream to use in the debug context
     */
    protected abstract DebugContext createRetryDebugContext(DebugContext initialDebug, OptionValues options, PrintStream logStream);

    /**
     * Entry point for handling a compilation failure.
     *
     * A subclass can use this to implement control over logging/dumping. This is important for
     * example when an embedder wants to prevent flooding logging mechanisms in the embedding
     * environment.
     *
     * @return the value returned by {@code failure.handle()}
     */
    protected T onCompilationFailure(Failure failure) {
        return failure.handle(false);
    }

    /**
     * Call back for {@linkplain #handle(boolean) handling} a compilation failure.
     */
    public final class Failure {
        /**
         * The cause of the failure.
         */
        public final Throwable cause;

        private final DebugContext debug;

        Failure(Throwable cause, DebugContext debug) {
            this.cause = cause;
            this.debug = debug;
        }

        /**
         * Handles the compilation failure.
         *
         * @param silent suppresses all logging and dumping iff {@code true}
         * @return a value representing the result of the failed compilation (may be {@code null})
         */
        public T handle(boolean silent) {
            if (silent) {
                return handleException(cause);
            }
            return handleFailure(debug, cause);
        }
    }

    @SuppressWarnings("try")
    public final T run(DebugContext initialDebug) {
        try {
            totalCompilations.incrementAndGet();
            return performCompilation(initialDebug);
        } catch (Throwable cause) {
            return onCompilationFailure(new Failure(cause, initialDebug));
        }
    }

    private static void printCompilationFailureActionAlternatives(PrintStream ps, ExceptionAction... alternatives) {
        if (alternatives.length > 0) {
            ps.printf("If in an environment where setting system properties is possible, the following%n");
            ps.printf("properties are available to change compilation failure reporting:%n");
            for (ExceptionAction action : alternatives) {
                String option = GraalCompilerOptions.CompilationFailureAction.getName();
                if (action == ExceptionAction.Silent) {
                    ps.printf("- To disable compilation failure notifications, set %s to %s (e.g., -Djdk.graal.%s=%s).%n",
                                    option, action,
                                    option, action);
                } else if (action == ExceptionAction.Print) {
                    ps.printf("- To print a message for a compilation failure without retrying the compilation, " +
                                    "set %s to %s (e.g., -Djdk.graal.%s=%s).%n",
                                    option, action,
                                    option, action);
                } else if (action == ExceptionAction.Diagnose) {
                    ps.printf("- To capture more information for diagnosing or reporting a compilation failure, " +
                                    "set %s to %s or %s (e.g., -Djdk.graal.%s=%s).%n",
                                    option, action,
                                    ExceptionAction.ExitVM,
                                    option, action);
                }
            }
        }
    }

    @SuppressWarnings("try")
    protected T handleFailure(DebugContext initialDebug, Throwable cause) {
        OptionValues initialOptions = initialDebug.getOptions();

        synchronized (GraalCompilerOptions.CompilationFailureAction) {
            // Serialize all compilation failure handling.
            // This prevents retry compilation storms and interleaving
            // of compilation exception messages.
            // It also allows for reliable testing of CompilationWrapper
            // by avoiding a race whereby retry compilation output from a
            // forced crash (i.e., use of GraalCompilerOptions.CrashAt)
            // is truncated.

            ExceptionAction action = lookupAction(initialOptions, cause);

            action = adjustAction(initialOptions, action, cause);

            if (action == ExceptionAction.Silent) {
                return handleException(cause);
            }

            if (action == ExceptionAction.Print) {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                try (PrintStream ps = new PrintStream(baos)) {
                    ps.printf("%s: Compilation of %s failed: ", Thread.currentThread(), this);
                    cause.printStackTrace(ps);
                    printCompilationFailureActionAlternatives(ps, ExceptionAction.Silent, ExceptionAction.Diagnose);
                }
                TTY.print(baos.toString());
                return handleException(cause);
            }

            // action is Diagnose or ExitVM

            if (Dump.hasBeenSet(initialOptions)) {
                // If dumping is explicitly enabled, Graal is being debugged
                // so don't interfere with what the user is expecting to see.
                return handleException(cause);
            }

            String dumpPath = null;
            try {
                String dir = this.outputDirectory.getPath();
                if (dir != null) {
                    String dumpName = PathUtilities.sanitizeFileName(toString());
                    dumpPath = PathUtilities.createDirectories(getPath(dir, dumpName));
                }
            } catch (Throwable t) {
                TTY.println("Warning: could not create Graal diagnostics directory");
                t.printStackTrace(TTY.out);
            }

            String message;
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (PrintStream ps = new PrintStream(baos)) {
                // This output is used by external tools to detect compilation failures.
                ps.println("[[[Graal compilation failure]]]");

                ps.printf("%s: Compilation of %s failed:%n", Thread.currentThread(), this);
                cause.printStackTrace(ps);
                printCompilationFailureActionAlternatives(ps, ExceptionAction.Silent, ExceptionAction.Print);
                if (dumpPath != null) {
                    ps.println("Retrying compilation of " + this);
                } else {
                    ps.println("Not retrying compilation of " + this + " as the dump path could not be created.");
                }
                message = baos.toString();
            }

            TTY.print(message);
            if (dumpPath == null) {
                return handleException(cause);
            }

            String retryLogFile = getPath(dumpPath, "retry.log");
            try (PrintStream ps = new PrintStream(PathUtilities.openOutputStream(retryLogFile))) {
                ps.print(message);
            } catch (IOException ioe) {
                TTY.printf("Error writing to %s: %s%n", retryLogFile, ioe);
            }

            String diagnoseLevel = DebugOptions.DiagnoseDumpLevel.getValue(initialOptions);

            // pre GR-51012 this was just a number - we still want to support the old numeric values
            boolean isOldLevel = diagnoseLevel.matches("-?\\d+");
            if (isOldLevel) {
                diagnoseLevel = ":" + diagnoseLevel;
            }

            OptionValues retryOptions = new OptionValues(initialOptions,
                            Dump, diagnoseLevel,
                            MethodFilter, null,
                            Count, "",
                            Time, "",
                            DumpPath, dumpPath,
                            PrintBackendCFG, true,
                            TrackNodeSourcePosition, true);

            ByteArrayOutputStream logBaos = new ByteArrayOutputStream();
            PrintStream ps = new PrintStream(logBaos);
            try (DebugContext retryDebug = createRetryDebugContext(initialDebug, retryOptions, ps);
                            DebugCloseable retryScope = retryDebug.openRetryCompilation()) {
                dumpOnError(retryDebug, cause);

                T res;
                try {
                    res = performCompilation(retryDebug);
                } finally {
                    ps.println("<Metrics>");
                    retryDebug.printMetrics(initialDebug.getDescription(), ps, true);
                    ps.println("</Metrics>");
                }
                ps.println("There was no exception during retry.");
                finalizeRetryLog(retryLogFile, logBaos, ps);
                return postRetry(action, res);
            } catch (Throwable e) {
                ps.println("Exception during retry:");
                e.printStackTrace(ps);
                finalizeRetryLog(retryLogFile, logBaos, ps);
                return postRetry(action, handleException(cause));
            }
        }
    }

    private T postRetry(ExceptionAction action, T res) {
        maybeExitVM(action);
        return res;
    }

    private static void finalizeRetryLog(String retryLogFile, ByteArrayOutputStream logBaos, PrintStream ps) {
        ps.close();
        try (OutputStream fos = PathUtilities.openOutputStream(retryLogFile, true)) {
            fos.write(logBaos.toByteArray());
        } catch (Throwable e) {
            TTY.printf("Error writing to %s: %s%n", retryLogFile, e);
        }
    }

    /**
     * Calls {@link System#exit(int)} in the runtime embedding the Graal compiler. This will be a
     * different runtime than Graal's runtime in the case of libgraal.
     */
    protected abstract void exitHostVM(int status);

    private void maybeExitVM(ExceptionAction action) {
        if (action == ExitVM) {
            TTY.println("Exiting VM after retry compilation of " + this);
            exitHostVM(-1);
        }
    }

    // Global counters used to measure compilation failure rate over a
    // period of COMPILATION_FAILURE_DETECTION_PERIOD_MS
    private static final GlobalAtomicLong totalCompilations = new GlobalAtomicLong(0L);
    private static final GlobalAtomicLong failedCompilations = new GlobalAtomicLong(0L);
    private static final GlobalAtomicLong compilationPeriodStart = new GlobalAtomicLong(0L);
    private static final int COMPILATION_FAILURE_DETECTION_PERIOD_MS = 2000;
    private static final int MIN_COMPILATIONS_FOR_FAILURE_DETECTION = 25;

    /**
     * Gets the start of the current compilation period, initializing it to {@code initialValue} if
     * this is the first period.
     */
    private static long getCompilationPeriodStart(long initialValue) {
        long start = compilationPeriodStart.get();
        if (start == 0) {
            // Lazy initialization of compilationPeriodStart
            if (compilationPeriodStart.compareAndSet(start, initialValue)) {
                start = initialValue;
            } else {
                start = compilationPeriodStart.get();
            }
        }
        return start;
    }

    /**
     * Issue a warning if the current compilation failure rate exceed the limit specified by
     * {@link GraalCompilerOptions#SystemicCompilationFailureRate}.
     *
     * @return whether the VM should exit
     */
    private static boolean detectCompilationFailureRateTooHigh(OptionValues options, Throwable cause) {
        if (isNonFailureBailout(options, cause)) {
            return false;
        }

        long failed = failedCompilations.incrementAndGet();
        long total = totalCompilations.get();
        if (total == 0) {
            return false;
        }

        int rate = (int) (failed * 100 / total);
        int maxRateValue = GraalCompilerOptions.SystemicCompilationFailureRate.getValue(options);
        if (maxRateValue == 0) {
            // Systemic compilation failure detection is disabled.
            return false;
        }

        int maxRate = Math.min(100, Math.abs(maxRateValue));
        long now = System.currentTimeMillis();
        long start = getCompilationPeriodStart(now);

        long period = now - start;
        boolean periodExpired = period > COMPILATION_FAILURE_DETECTION_PERIOD_MS;

        // Wait for period to expire or some minimum amount of compilations
        // before detecting systemic failure.
        if (rate > maxRate && (periodExpired || total > MIN_COMPILATIONS_FOR_FAILURE_DETECTION)) {
            Formatter msg = new Formatter();
            String option = GraalCompilerOptions.SystemicCompilationFailureRate.getName();
            msg.format("Warning: Systemic Graal compilation failure detected: %d of %d (%d%%) of compilations failed during last %d ms [max rate set by %s is %d%%]. ",
                            failed, total, rate, period, option, maxRateValue);
            msg.format("To mitigate systemic compilation failure detection, set %s to a higher value. ", option);
            msg.format("To disable systemic compilation failure detection, set %s to 0. ", option);
            msg.format("To get more information on compilation failures, set %s to Print or Diagnose. ", GraalCompilerOptions.CompilationFailureAction.getName());
            TTY.println(msg.toString());
            if (maxRateValue < 0) {
                // A negative value means the VM should be exited
                return true;
            }
            periodExpired = true;
        }

        if (periodExpired) {

            if (compilationPeriodStart.compareAndSet(start, now)) {
                // Reset compilation counters for new period
                failedCompilations.set(0);
                totalCompilations.set(0);
            }
        }

        return false;
    }

    /**
     * Adjusts {@code initialAction} if necessary based on
     * {@link GraalCompilerOptions#SystemicCompilationFailureRate} and
     * {@link GraalCompilerOptions#MaxCompilationProblemsPerAction}.
     */
    private ExceptionAction adjustAction(OptionValues initialOptions, ExceptionAction initialAction, Throwable cause) {
        ExceptionAction action = initialAction;
        int maxProblems = GraalCompilerOptions.MaxCompilationProblemsPerAction.getValue(initialOptions);
        if (action != ExceptionAction.ExitVM) {
            if (detectCompilationFailureRateTooHigh(initialOptions, cause)) {
                return ExceptionAction.ExitVM;
            }
            synchronized (problemsHandledPerAction) {
                while (action != ExceptionAction.Silent) {
                    int problems = problemsHandledPerAction.getOrDefault(action, 0);
                    if (problems >= maxProblems) {
                        if (problems == maxProblems) {
                            TTY.printf("Warning: adjusting %s from %s to %s after %s (%d) failed compilations%n", GraalCompilerOptions.CompilationFailureAction, action, action.quieter(),
                                            GraalCompilerOptions.MaxCompilationProblemsPerAction, maxProblems);
                            // Ensure that the message above is only printed once
                            problemsHandledPerAction.put(action, problems + 1);
                        }
                        action = action.quieter();
                    } else {
                        break;
                    }
                }
                problemsHandledPerAction.put(action, problemsHandledPerAction.getOrDefault(action, 0) + 1);
            }
        }
        return action;
    }
}
