/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.sandbox;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.management.ManagementFactory;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.math.BigInteger;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.logging.Level;

import org.graalvm.options.OptionCategory;
import org.graalvm.options.OptionKey;
import org.graalvm.options.OptionStability;
import org.graalvm.options.OptionType;
import org.graalvm.options.OptionValues;
import org.graalvm.polyglot.SandboxPolicy;

import com.oracle.truffle.api.Option;
import com.oracle.truffle.api.ThreadLocalAction;
import com.oracle.truffle.api.TruffleContext;
import com.oracle.truffle.api.TruffleOptions;
import com.sun.management.ThreadMXBean;

@Option.Group(SandboxInstrument.ID)
public final class SandboxContext {

    private static final String BOOLEAN_SYNTAX = "true|false";
    private static final String INT_SYNTAX = "[1, inf)";
    private static final String DOUBLE_SYNTAX = "[0.0, inf)";
    private static final String DURATION_SYNTAX = INT_SYNTAX + "ms|s|m|h|d";

    /**
     * Rounding for memory limit used for recommended sandbox options to make the printed options
     * stable.
     */
    private static final int MEMORY_LIMIT_ROUND_UP = 1 << 20; // 1MB
    /**
     * Rounding for CPU limit used for recommended sandbox options to make the printed options
     * stable.
     */
    private static final int CPU_LIMIT_ROUND_UP = 10;   // 10ms
    /**
     * Rounding for statements limit used for recommended sandbox options to make the printed
     * options stable.
     */
    private static final int STATEMENTS_LIMIT_ROUND_UP = 1_000;
    /**
     * Rounding for stack frames limit used for recommended sandbox options to make the printed
     * options stable.
     */
    private static final int STACK_FRAMES_LIMIT_ROUND_UP = 64;
    /**
     * Rounding for Truffle AST depth limit used for recommended sandbox options to make the printed
     * options stable.
     */
    private static final int AST_DEPTH_LIMIT_ROUND_UP = 64;
    /**
     * Rounding for output stream limit used for recommended sandbox options to make the printed
     * options stable.
     */
    private static final int OUTPUT_SIZE_LIMIT_ROUND_UP = 1 << 20; // 1MB

    @Option(category = OptionCategory.USER, stability = OptionStability.STABLE, help = "Specifies the maximum heap memory that can be retained by the application during its run. " +
                    "Includes only data retained by the guest application, runtime allocated data is not included. " +
                    "No limit is set by default and setting the related expert options has no effect. Example value: '100MB'.", usageSyntax = "[1, inf)B|KB|MB|GB", sandbox = SandboxPolicy.UNTRUSTED) //
    static final OptionKey<Long> MaxHeapMemory = new OptionKey<>(0L, createSizeInBytesType());

    @Option(category = OptionCategory.EXPERT, stability = OptionStability.STABLE, help = "Time interval to check allocated bytes for an execution context. " +
                    "Exceeding certain number of allocated bytes triggers computation of bytes retained in the heap by the context. Is set to '10ms' by default. Maximum interval is 1h.", //
                    usageSyntax = DURATION_SYNTAX, sandbox = SandboxPolicy.UNTRUSTED) //
    static final OptionKey<Duration> AllocatedBytesCheckInterval = new OptionKey<>(Duration.ofMillis(10), createDurationType());

    @Option(category = OptionCategory.INTERNAL, stability = OptionStability.EXPERIMENTAL, help = "Specifies whether checking of allocated bytes for an execution context is enabled. Is set to 'true' by default.", //
                    usageSyntax = BOOLEAN_SYNTAX, sandbox = SandboxPolicy.CONSTRAINED) //
    static final OptionKey<Boolean> AllocatedBytesCheckEnabled = new OptionKey<>(true);

    @Option(category = OptionCategory.EXPERT, stability = OptionStability.EXPERIMENTAL, help = "Specifies a factor of MaxHeapMemory the allocation of which triggers retained heap memory computation. " +
                    "When allocated bytes for an execution context reach the specified factor, computation of bytes retained in the heap by the context is initiated. Is set to '1.0' by default.", //
                    usageSyntax = DOUBLE_SYNTAX, sandbox = SandboxPolicy.UNTRUSTED) //
    static final OptionKey<Double> AllocatedBytesCheckFactor = new OptionKey<>(1.0d);

    @Option(category = OptionCategory.EXPERT, stability = OptionStability.STABLE, help = "Specifies the minimum time interval between two computations of retained bytes in the heap for a single execution context. " +
                    "Is set to '10ms' by default. Maximum value for the minimum interval is 1h.", usageSyntax = DURATION_SYNTAX, sandbox = SandboxPolicy.UNTRUSTED) //
    static final OptionKey<Duration> RetainedBytesCheckInterval = new OptionKey<>(Duration.ofMillis(10), createDurationType());

    @Option(category = OptionCategory.EXPERT, stability = OptionStability.EXPERIMENTAL, help = "Specifies a factor of total heap memory of the host VM the exceeding of which stops the world. " +
                    "When the total number of bytes allocated in the heap for the whole host VM exceeds the factor, the following process is initiated. Execution for all memory-limited execution contexts " +
                    "(ones with `sandbox.MaxHeapMemory` set) is paused. Retained bytes in the heap for each memory-limited context are computed. Contexts exceeding their limits are cancelled. The execution is resumed. " +
                    "Is set to '0.7' by default. Has no effect if 'UseLowMemoryTrigger' is 'false'.", usageSyntax = DOUBLE_SYNTAX, sandbox = SandboxPolicy.CONSTRAINED) //
    static final OptionKey<Double> RetainedBytesCheckFactor = new OptionKey<>(0.7d);

    @Option(category = OptionCategory.EXPERT, stability = OptionStability.STABLE, help = "Specifies whether stopping the world is enabled. " +
                    "When enabled, memory-limited execution contexts are paused when the total number of bytes allocated in the heap " +
                    "for the whole host VM exceeds the specified factor of total heap memory of the host VM. Is set to 'true' by default.", usageSyntax = "true|false", sandbox = SandboxPolicy.CONSTRAINED) //
    static final OptionKey<Boolean> UseLowMemoryTrigger = new OptionKey<>(!TruffleOptions.AOT);

    @Option(category = OptionCategory.EXPERT, stability = OptionStability.EXPERIMENTAL, help = "Specifies whether an already set heap memory notification limit can be reused for the low memory trigger. When reusing is allowed and " +
                    "the usage threshold or the collection usage threshold of a heap memory pool has already been set, then the value of 'RetainedBytesCheckFactor' is ignored for that memory pool and threshold type and " +
                    "whatever threshold value has already been set is used. Is set to 'false' by default.", usageSyntax = "true|false") //
    static final OptionKey<Boolean> ReuseLowMemoryTriggerThreshold = new OptionKey<>(false);

    @Option(category = OptionCategory.USER, stability = OptionStability.STABLE, help = "Limits the total maximum CPU time that was spent running the application. No limit is set by default. " +
                    "Example value: '100ms'.", usageSyntax = DURATION_SYNTAX, sandbox = SandboxPolicy.UNTRUSTED) //
    static final OptionKey<Duration> MaxCPUTime = new OptionKey<>(null, createDurationType());

    @Option(category = OptionCategory.EXPERT, stability = OptionStability.STABLE, help = "Time interval to check the active CPU time for an execution context. Is set to '10ms' by default. " +
                    "Maximum interval is 1h.", usageSyntax = DURATION_SYNTAX, sandbox = SandboxPolicy.UNTRUSTED) //
    static final OptionKey<Duration> MaxCPUTimeCheckInterval = new OptionKey<>(Duration.ofMillis(10), createDurationType());

    @Option(category = OptionCategory.USER, stability = OptionStability.STABLE, help = "Limits the maximum number of guest language statements executed. " +
                    "The execution is cancelled with an resource exhausted error when it is exceeded.", usageSyntax = INT_SYNTAX, sandbox = SandboxPolicy.UNTRUSTED) //
    static final OptionKey<Long> MaxStatements = new OptionKey<>(-1L);

    @Option(category = OptionCategory.EXPERT, stability = OptionStability.STABLE, help = "Configures whether to include internal sources in the max statements computation.", sandbox = SandboxPolicy.UNTRUSTED) //
    static final OptionKey<Boolean> MaxStatementsIncludeInternal = new OptionKey<>(Boolean.FALSE);

    @Option(category = OptionCategory.USER, stability = OptionStability.STABLE, help = "Limits the maximum number of guest stack frames (default: no limit).", usageSyntax = INT_SYNTAX, sandbox = SandboxPolicy.UNTRUSTED) //
    static final OptionKey<Integer> MaxStackFrames = new OptionKey<>(-1);

    @Option(category = OptionCategory.USER, stability = OptionStability.STABLE, help = "Limits the number of threads that can be entered by a context at the same point in time (default: no limit).", //
                    usageSyntax = INT_SYNTAX, sandbox = SandboxPolicy.UNTRUSTED) //
    static final OptionKey<Integer> MaxThreads = new OptionKey<>(-1);

    @Option(category = OptionCategory.USER, stability = OptionStability.STABLE, help = "Maximum AST depth of a function (default: no limit).", usageSyntax = INT_SYNTAX, sandbox = SandboxPolicy.UNTRUSTED) //
    public static final OptionKey<Integer> MaxASTDepth = new OptionKey<>(-1);

    @Option(category = OptionCategory.USER, stability = OptionStability.STABLE, help = "Records the maximum amount of resources used during execution, and reports a summary of resource limits to the log file upon application exit. " +
                    "Users may also provide limits to enforce while tracing. " +
                    "This flag can be used to estimate an application's optimal sandbox parameters, either by tracing the limits of a stress test or peak usage.", usageSyntax = BOOLEAN_SYNTAX, sandbox = SandboxPolicy.ISOLATED) //
    static final OptionKey<Boolean> TraceLimits = new OptionKey<>(Boolean.FALSE);

    @Option(category = OptionCategory.USER, stability = OptionStability.STABLE, help = "Specifies the maximum size that the guest application can write to stdout. " +
                    "No limit is set by default. Example value: '10MB'.", usageSyntax = "[0, inf)B|KB|MB|GB", sandbox = SandboxPolicy.UNTRUSTED) //
    static final OptionKey<Long> MaxOutputStreamSize = new OptionKey<>(-1L, createSizeInBytesType());

    @Option(category = OptionCategory.USER, stability = OptionStability.STABLE, help = "Specifies the maximum size that the guest application can write to stderr. " +
                    "No limit is set by default. Example value: '10MB'.", usageSyntax = "[0, inf)B|KB|MB|GB", sandbox = SandboxPolicy.UNTRUSTED) //
    static final OptionKey<Long> MaxErrorStreamSize = new OptionKey<>(-1L, createSizeInBytesType());
    private static final AtomicLong idGenerator = new AtomicLong();

    final long id = idGenerator.incrementAndGet();

    private final SandboxInstrument instrument;
    private final TruffleContext truffleContext;

    final long heapMemoryLimit;
    final Duration allocatedBytesCheckInterval;
    final double allocatedBytesCheckFactor;
    final Duration retainedBytesCheckInterval;
    final double retainedBytesCheckFactor;
    final boolean reuseLowMemoryTriggerThreshold;
    final boolean allocatedBytesCheckEnabled;
    final boolean lowMemoryTriggerEnabled;

    final Duration cpuTimeLimit;
    final Duration cpuTimeLimitAccuracy;
    final long maxStatements;
    final boolean maxStatementsIncludeInternal;
    final int stackFrameLimit;
    final int activeThreadsLimit;
    final int astDepthLimit;
    final long outputStreamLimit;
    final long errorStreamLimit;

    final AtomicLong volatileOutputSizeCounter = new AtomicLong();
    final AtomicLong volatileErrorSizeCounter = new AtomicLong();

    final boolean tracingEnabled;
    final AtomicBoolean tracedLimitsPrinted;

    final AtomicLong volatileStatementCounter = new AtomicLong();
    long statementCounter;
    final AtomicInteger threadCounter = new AtomicInteger();
    /*
     * Counts the number of times this context changed its active status, either from active (at
     * least one entered thread) to inactive (no entered thread) or from inactive to active.
     */
    volatile long changedActiveStatusCount;

    final ReferenceQueue<Thread> collectedThreads = new ReferenceQueue<>();
    private final Set<SandboxThreadContext> threads = new LinkedHashSet<>();

    private int threadCount;
    volatile boolean initialized;

    /* The following are maximum and minimum values observed during tracing. */
    long maxHeapMemoryTraced;
    long maxCpuTimeTraced;
    int minStackFramesTraced;
    int maxActiveThreadsTraced;
    int maxAstDepthTraced;

    final WeakReference<SandboxContext> contextWeakReference;

    volatile SandboxTimeLimitChecker timeLimitChecker;
    volatile SandboxMemoryLimitChecker memoryLimitChecker;
    final AtomicBoolean retainedSizeComputationCancelled = new AtomicBoolean(false);
    volatile long lastRetainedBytes;

    /**
     * We need to remember the accumulated CPU time/thread allocated bytes of disposed threads in
     * order to correctly check the appropriate limit. CPU time and thread allocated bytes for an
     * active thread are stored in the corresponding {@link SandboxThreadContext thread context},
     * but when the thread is disposed, we no longer keep a reference to the thread context, so we
     * need to store those values somewhere else.
     */
    private Duration timeExecutedOfRemovedThreads = Duration.ZERO;
    private long allocatedBytesOfRemovedThreads = 0;

    @SuppressWarnings("SynchronizationOnLocalVariableOrMethodParameter")
    SandboxContext(SandboxInstrument instrument, TruffleContext context) {
        this.instrument = instrument;
        this.truffleContext = context;

        try {
            instrument.environment.submitThreadLocal(context, new Thread[0], new ThreadLocalAction(false, false) {
                @Override
                protected void perform(ThreadLocalAction.Access access) {
                }
            });
        } catch (UnsupportedOperationException e) {
            throw new SandboxException("Sandbox features are not supported on the current Truffle runtime.", e);
        }

        OptionValues values = instrument.environment.getOptions(context);

        boolean tracingLimitsEnabled = values.get(TraceLimits);
        this.tracingEnabled = tracingLimitsEnabled;
        if (tracingLimitsEnabled) {
            this.tracedLimitsPrinted = new AtomicBoolean();
            instrument.noTracingNeeded.invalidate();
        } else {
            this.tracedLimitsPrinted = null;
        }

        Duration timeLimit = values.get(MaxCPUTime);
        if (Duration.ZERO.equals(timeLimit)) {
            timeLimit = null;
        }
        if (timeLimit != null) {
            long time = -1;
            RuntimeException cause = null;
            try {
                ManagementFactory.getThreadMXBean().setThreadCpuTimeEnabled(true);
                time = ManagementFactory.getThreadMXBean().getThreadCpuTime(SandboxInstrument.getThreadId(Thread.currentThread()));
            } catch (UnsupportedOperationException e) {
                // fallthrough not supported
                cause = e;
            }
            if (time == -1) {
                throw new SandboxException("ThreadMXBean.getThreadCpuTime() is not supported or enabled by the host VM but required for CPU time limit.", cause);
            }
        } else if (tracingLimitsEnabled) {
            timeLimit = Duration.ofMillis(Long.MAX_VALUE);
        }
        long memoryLimit = values.get(MaxHeapMemory);
        boolean totalHeapCheckingEnabled = values.get(UseLowMemoryTrigger);
        if (memoryLimit > 0L) {
            if (totalHeapCheckingEnabled && TruffleOptions.AOT) {
                throw new SandboxException("Use of the low memory trigger is not supported in the ahead-of-time compilation mode. " +
                                "To resolve this set 'sandbox.UseLowMemoryTrigger' to false.", null);
            }
            if (!SandboxAccessor.RUNTIME.supportsHeapMemoryLimits()) {
                throw new SandboxException("Heap memory limit is not supported on the current Truffle runtime.", null);
            }
            long allocatedBytes = -1;
            RuntimeException cause = null;
            try {
                java.lang.management.ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
                if (threadMXBean instanceof ThreadMXBean) {
                    allocatedBytes = ((ThreadMXBean) threadMXBean).getThreadAllocatedBytes(SandboxInstrument.getCurrentThreadId());
                }
            } catch (UnsupportedOperationException e) {
                cause = e;
            }
            if (allocatedBytes == -1) {
                throw new SandboxException("ThreadMXBean.getThreadAllocatedBytes() is not supported or enabled by the host VM but required for heap memory limit.", cause);
            }
        }
        long maxStatementsValue = values.get(MaxStatements);
        if (maxStatementsValue == -1 && tracingLimitsEnabled) {
            maxStatementsValue = Long.MAX_VALUE;
        }
        this.maxStatements = maxStatementsValue;
        this.maxStatementsIncludeInternal = values.get(MaxStatementsIncludeInternal);
        if (hasStatementLimit()) {
            synchronized (instrument) {
                if (instrument.maxStatementsIncludeInternal != null &&
                                !Objects.equals(instrument.maxStatementsIncludeInternal, this.maxStatementsIncludeInternal)) {
                    throw new SandboxException(
                                    "Invalid max statements filter configuration detected. " +
                                                    "All contexts of the same engine need to use the same option value for 'sandbox.MaxStatementsIncludeInternal'. " +
                                                    "To resolve this use the same option value for 'sandbox.MaxStatementsIncludeInternal'.",
                                    null);
                } else if (instrument.maxStatementsIncludeInternal == null) {
                    instrument.maxStatementsIncludeInternal = this.maxStatementsIncludeInternal;
                }
            }
        }

        Duration timeLimitAccuracy = values.get(MaxCPUTimeCheckInterval);
        if (timeLimitAccuracy.isZero()) {
            timeLimitAccuracy = Duration.ofMillis(1);
        }
        if (timeLimitAccuracy.compareTo(Duration.ofHours(1)) > 0) {
            throw new SandboxException("Invalid value of the option 'sandbox.MaxCPUTimeCheckInterval'. The value must be in the range [1ms, 1h].", null);
        }
        this.cpuTimeLimitAccuracy = timeLimitAccuracy;
        this.cpuTimeLimit = timeLimit;

        int maxStackFramesValue = values.get(MaxStackFrames);
        if (maxStackFramesValue == -1 && tracingLimitsEnabled) {
            maxStackFramesValue = Integer.MAX_VALUE - 1;
        }
        this.stackFrameLimit = maxStackFramesValue;

        int maxThreadsValue = values.get(MaxThreads);
        if (maxThreadsValue == -1 && tracingLimitsEnabled) {
            maxThreadsValue = Integer.MAX_VALUE - 1;
        }
        this.activeThreadsLimit = maxThreadsValue;

        int astDepthLimitValue = values.get(MaxASTDepth);
        if (astDepthLimitValue == -1 && tracingLimitsEnabled) {
            astDepthLimitValue = Integer.MAX_VALUE - 1;
        }
        this.astDepthLimit = astDepthLimitValue;
        long outputStreamLimitValue = values.get(MaxOutputStreamSize);
        if (outputStreamLimitValue == -1 && tracingLimitsEnabled) {
            outputStreamLimitValue = Long.MAX_VALUE - 1;
        }
        this.outputStreamLimit = outputStreamLimitValue;
        long errorStreamLimitValue = values.get(MaxErrorStreamSize);
        if (errorStreamLimitValue == -1 && tracingLimitsEnabled) {
            errorStreamLimitValue = Long.MAX_VALUE - 1;
        }
        this.errorStreamLimit = errorStreamLimitValue;
        synchronized (instrument) {
            if (instrument.astDepthLimit != null && !Objects.equals(instrument.astDepthLimit, this.astDepthLimit)) {
                throw new SandboxException(
                                "Invalid AST depth limit configuration detected. " +
                                                "All contexts of the same engine need to use the same option value for 'sandbox.MaxASTDepth'. " +
                                                "To resolve this use the same option value for 'sandbox.MaxASTDepth'." +
                                                "Even combining context with and without AST depth limit is not allowed.",
                                null);
            } else if (instrument.astDepthLimit == null) {
                instrument.astDepthLimit = this.astDepthLimit;
            }
        }

        this.heapMemoryLimit = memoryLimit;
        Duration allocationCheckInterval = values.get(AllocatedBytesCheckInterval);
        if (allocationCheckInterval.isZero()) {
            allocationCheckInterval = Duration.ofMillis(1);
        }
        if (allocationCheckInterval.compareTo(Duration.ofHours(1)) > 0) {
            throw new SandboxException("Invalid value of the option 'sandbox.AllocatedBytesCheckInterval'. The value must be in the range [1ms, 1h].", null);
        }
        this.allocatedBytesCheckInterval = allocationCheckInterval;
        double allocationCheckFactor = values.get(AllocatedBytesCheckFactor);
        if (allocationCheckFactor < 0.0d) {
            throw new SandboxException("Invalid allocated bytes check factor '" + formatMemorySizeFactor(allocationCheckFactor) + "'. Value greater or equal to 0.0 is expected.", null);
        }
        this.allocatedBytesCheckFactor = allocationCheckFactor;
        Duration retainedSizeCheckInterval = values.get(RetainedBytesCheckInterval);
        if (retainedSizeCheckInterval.compareTo(Duration.ofHours(1)) > 0) {
            throw new SandboxException("Invalid value of the option 'sandbox.RetainedBytesCheckInterval'. The value must be in the range [1ms, 1h].", null);
        }
        this.retainedBytesCheckInterval = retainedSizeCheckInterval;
        double heapCheckFactor = values.get(RetainedBytesCheckFactor);
        if (heapCheckFactor < 0.0d || heapCheckFactor > 1.0d) {
            throw new SandboxException("Invalid retained bytes check factor '" + formatMemorySizeFactor(heapCheckFactor) + "'. Value between 0.0 and 1.0 is expected.", null);
        }
        this.retainedBytesCheckFactor = heapCheckFactor;
        boolean allocationCheckingEnabled = values.get(AllocatedBytesCheckEnabled);
        if (!allocationCheckingEnabled && !totalHeapCheckingEnabled) {
            throw new SandboxException("AllocatedBytesCheckEnabled and UseLowMemoryTrigger cannot both be false.", null);
        }
        boolean totalHeapCheckingReusesSetThreshold = values.get(ReuseLowMemoryTriggerThreshold);
        if (hasMemoryLimit()) {
            /*
             * The low memory trigger has nothing to do with tracing, so if memory limit is not
             * enabled, but tracing is, then any value of the UseLowMemoryTrigger option is fine.
             */
            synchronized (SandboxLowMemoryListener.class) {
                if (SandboxLowMemoryListener.lowMemoryTriggerEnabled != null) {
                    if (!SandboxLowMemoryListener.lowMemoryTriggerEnabled.equals(totalHeapCheckingEnabled)) {
                        throw new SandboxException(
                                        "Invalid 'sandbox.UseLowMemoryTrigger' option value '" + totalHeapCheckingEnabled + "' detected. " +
                                                        "All memory-limited contexts of all engines on the host VM need to use the same option value for 'sandbox.UseLowMemoryTrigger'. " +
                                                        "To resolve this use the same option value for 'sandbox.UseLowMemoryTrigger' for all contexts.",
                                        null);
                    }
                    if (totalHeapCheckingEnabled && SandboxLowMemoryListener.lowMemoryTriggerReusesSetThreshold != null &&
                                    !SandboxLowMemoryListener.lowMemoryTriggerReusesSetThreshold.equals(totalHeapCheckingReusesSetThreshold)) {
                        throw new SandboxException(
                                        "Invalid 'sandbox.ReuseLowMemoryTriggerThreshold' option value '" + totalHeapCheckingReusesSetThreshold + "' detected. " +
                                                        "All memory-limited contexts of all engines on the host VM need to use the same option value for 'sandbox.ReuseLowMemoryTriggerThreshold'. " +
                                                        "To resolve this use the same option value for 'sandbox.ReuseLowMemoryTriggerThreshold' for all contexts.",
                                        null);
                    }
                } else {
                    SandboxLowMemoryListener.lowMemoryTriggerEnabled = totalHeapCheckingEnabled;
                    SandboxLowMemoryListener.lowMemoryTriggerReusesSetThreshold = totalHeapCheckingReusesSetThreshold;
                }
            }
        }
        this.allocatedBytesCheckEnabled = allocationCheckingEnabled;
        this.lowMemoryTriggerEnabled = totalHeapCheckingEnabled;
        this.reuseLowMemoryTriggerThreshold = totalHeapCheckingReusesSetThreshold;

        contextWeakReference = new WeakReference<>(this, instrument.sandboxContextReferenceQueue);
    }

    static String formatMemorySizeFactor(double factor) {
        return String.format(Locale.US, "%.6f", factor);
    }

    SandboxInstrument getInstrument() {
        return instrument;
    }

    TruffleContext getTruffleContext() {
        return truffleContext;
    }

    boolean isClosed() {
        return truffleContext.isClosed();
    }

    synchronized SandboxThreadContext createThreadContext(Thread t) {
        SandboxThreadContext threadContext = new SandboxThreadContext(this, t);
        threads.add(threadContext);
        removeCollectedThreads();
        threadCount++;
        if (threadCount > 1) {
            instrument.singleThreadPerContext.invalidate();
        }
        return threadContext;
    }

    void removeCollectedThreads() {
        assert Thread.holdsLock(this);
        SandboxThreadContext.ThreadReference threadRef;
        while ((threadRef = (SandboxThreadContext.ThreadReference) collectedThreads.poll()) != null) {
            removeThreadContext(threadRef.threadContext);
        }
    }

    synchronized Duration getTimeActive() {
        Duration timeExecuted = timeExecutedOfRemovedThreads;
        for (SandboxThreadContext threadInfo : threads) {
            timeExecuted = timeExecuted.plus(threadInfo.getTimeExecuted());
        }
        return timeExecuted;
    }

    synchronized long getAllocatedBytes() {
        long allocatedBytes = allocatedBytesOfRemovedThreads;
        for (SandboxThreadContext threadInfo : threads) {
            allocatedBytes += threadInfo.getAllocatedBytes();
        }
        return allocatedBytes;
    }

    synchronized void resetLimits() {
        if (hasCPULimit()) {
            timeExecutedOfRemovedThreads = Duration.ZERO;
            for (SandboxThreadContext threadInfo : threads) {
                threadInfo.resetTiming();
            }
        }
        if (hasStatementLimit()) {
            statementCounter = maxStatements;
            volatileStatementCounter.set(maxStatements);
        } else {
            statementCounter = Long.MAX_VALUE - 1;
            volatileStatementCounter.set(Long.MAX_VALUE - 1);
        }
        if (hasOutputStreamLimit()) {
            volatileOutputSizeCounter.set(outputStreamLimit);
        } else {
            volatileOutputSizeCounter.set(Long.MAX_VALUE - 1);
        }
        if (hasErrorStreamLimit()) {
            volatileErrorSizeCounter.set(errorStreamLimit);
        } else {
            volatileErrorSizeCounter.set(Long.MAX_VALUE - 1);
        }
        if (hasStackFrameLimit()) {
            minStackFramesTraced = stackFrameLimit;
        } else {
            minStackFramesTraced = Integer.MAX_VALUE - 1;
        }
        maxActiveThreadsTraced = 0;
    }

    void removeThreadContext(SandboxThreadContext threadContext) {
        assert Thread.holdsLock(this);
        threads.remove(threadContext);
        if (hasCPULimit()) {
            timeExecutedOfRemovedThreads = timeExecutedOfRemovedThreads.plus(threadContext.getTimeExecuted());
        }
        if (hasMemoryLimit() || isTracingEnabled()) {
            allocatedBytesOfRemovedThreads += threadContext.getAllocatedBytes();
        }
    }

    boolean hasStatementLimit() {
        return maxStatements >= 0;
    }

    boolean hasCPULimit() {
        return cpuTimeLimit != null;
    }

    boolean hasStackFrameLimit() {
        return stackFrameLimit >= 0;
    }

    boolean hasActiveThreadsLimit() {
        return activeThreadsLimit >= 0;
    }

    boolean hasASTDepthLimit() {
        return astDepthLimit >= 0;
    }

    boolean hasMemoryLimit() {
        return heapMemoryLimit != 0L;
    }

    boolean hasErrorStreamLimit() {
        return errorStreamLimit >= 0;
    }

    boolean hasOutputStreamLimit() {
        return outputStreamLimit >= 0;
    }

    long computeStatementsLimit(long limit) {
        return maxStatements - limit;
    }

    int computeStackFramesLimit(int limit) {
        return stackFrameLimit - limit;
    }

    boolean isTracingEnabled() {
        return tracingEnabled;
    }

    record TracedLimits(long memory, long cpu, long statements, int stackFrames, int threads, long astDepth, long stdout, long stderr) {

        void print(PrintWriter printWriter, String format) {
            printWriter.printf(format, renderMemoryLimit(memory()), renderCPULimit(cpu()), statements(), stackFrames(),
                            threads(), astDepth(), renderMemoryLimit(stdout()), renderMemoryLimit(stderr()));
        }

        TracedLimits round() {
            return new TracedLimits(round(memory(), MEMORY_LIMIT_ROUND_UP), round(cpu(), CPU_LIMIT_ROUND_UP),
                            round(statements(), STATEMENTS_LIMIT_ROUND_UP), (int) round(stackFrames(), STACK_FRAMES_LIMIT_ROUND_UP),
                            threads(), round(astDepth(), AST_DEPTH_LIMIT_ROUND_UP), round(stdout(), OUTPUT_SIZE_LIMIT_ROUND_UP),
                            round(stderr(), OUTPUT_SIZE_LIMIT_ROUND_UP));
        }

        /**
         * Rounds up the limit value. Values with friction part less than half of {@code delta} are
         * rounded up to whole delta. Values with friction part larger or equal to half of
         * {@code delta} are rounded up by two delta.
         * <p>
         * Rounding examples for {@code delta 10}:
         *
         * <pre>
         * 4ms -> 10ms
         * 5ms -> 20ms
         * 9ms -> 20ms
         * 14ms -> 20ms
         * 15ms -> 30ms
         * </pre>
         * </p>
         *
         * @param value to be rounded
         * @param delta the unit to round to
         */
        private static long round(long value, int delta) {
            return ((value + (int) (1.5 * delta)) / delta) * delta;
        }
    }

    void printLimits() {
        assert tracingEnabled;
        if (tracedLimitsPrinted.compareAndSet(false, true)) {
            TracedLimits tracedLimits = new TracedLimits(maxHeapMemoryTraced, maxCpuTimeTraced, computeStatementsLimit(statementCounter),
                            computeStackFramesLimit(minStackFramesTraced), maxActiveThreadsTraced, maxAstDepthTraced,
                            outputStreamLimit - volatileOutputSizeCounter.get(), errorStreamLimit - volatileErrorSizeCounter.get());
            StringWriter logMessage = new StringWriter();
            try (PrintWriter out = new PrintWriter(logMessage)) {
                out.println();
                printTable(out, tracedLimits);
                TracedLimits roundedLimits = tracedLimits.round();
                out.println();
                printContextBuilderOptions(out, roundedLimits);
                out.println();
                printCommandLineOptions(out, roundedLimits);
            }
            SandboxInstrument.logBoundary(Level.INFO, getInstrument(), this, "[trace-limits] %s", null, logMessage.toString());
        }
    }

    static String renderMemoryLimit(long limit) {
        String suffix = "B";
        double fractionalLimit = limit;
        if (limit > SizeUnit.GIGABYTE.factor) {
            suffix = SizeUnit.GIGABYTE.symbol;
            fractionalLimit /= SizeUnit.GIGABYTE.factor;
        } else if (limit > SizeUnit.MEGABYTE.factor) {
            suffix = SizeUnit.MEGABYTE.symbol;
            fractionalLimit /= SizeUnit.MEGABYTE.factor;
        } else if (limit > SizeUnit.KILOBYTE.factor) {
            suffix = SizeUnit.KILOBYTE.symbol;
            fractionalLimit /= SizeUnit.KILOBYTE.factor;
        }
        long upperLimit = (long) Math.ceil(fractionalLimit);
        return Long.toString(upperLimit) + suffix;
    }

    static String renderCPULimit(long limit) {
        String suffix = "ms";
        double fractionalLimit = limit;
        if (limit > TimeUnit.DAY.factor) {
            suffix = TimeUnit.DAY.symbol;
            fractionalLimit /= TimeUnit.DAY.factor;
        } else if (limit > TimeUnit.HOUR.factor) {
            suffix = TimeUnit.HOUR.symbol;
            fractionalLimit /= TimeUnit.HOUR.factor;
        } else if (limit > TimeUnit.MINUTE.factor) {
            suffix = TimeUnit.MINUTE.symbol;
            fractionalLimit /= TimeUnit.MINUTE.factor;
        } else if (limit > TimeUnit.SECOND.factor) {
            suffix = TimeUnit.SECOND.symbol;
            fractionalLimit /= TimeUnit.SECOND.factor;
        }
        long upperLimit = (long) Math.ceil(fractionalLimit);
        return Long.toString(upperLimit) + suffix;
    }

    private static void printTable(PrintWriter printWriter, TracedLimits tracedLimits) {
        String format = """
                        Traced Limits:
                        Maximum Heap Memory:                   %25s
                        CPU Time:                              %25s
                        Number of statements executed:         %25d
                        Maximum active stack frames:           %25d
                        Maximum number of threads:             %25d
                        Maximum AST Depth:                     %25d
                        Size written to standard output:       %25s
                        Size written to standard error output: %25s
                        """;
        tracedLimits.print(printWriter, format);
    }

    private static void printCommandLineOptions(PrintWriter printWriter, TracedLimits tracedLimits) {
        String format = """
                        Recommended Command Line Limits:
                        --sandbox.MaxHeapMemory=%s --sandbox.MaxCPUTime=%s --sandbox.MaxStatements=%d --sandbox.MaxStackFrames=%d --sandbox.MaxThreads=%d --sandbox.MaxASTDepth=%d --sandbox.MaxOutputStreamSize=%s --sandbox.MaxErrorStreamSize=%s
                        """;
        tracedLimits.print(printWriter, format);
    }

    private static void printContextBuilderOptions(PrintWriter printWriter, TracedLimits tracedLimits) {
        String format = """
                        Recommended Programmatic Limits:
                        Context.newBuilder()
                                    .option("sandbox.MaxHeapMemory", "%s")
                                    .option("sandbox.MaxCPUTime","%s")
                                    .option("sandbox.MaxStatements","%d")
                                    .option("sandbox.MaxStackFrames","%d")
                                    .option("sandbox.MaxThreads","%d")
                                    .option("sandbox.MaxASTDepth","%d")
                                    .option("sandbox.MaxOutputStreamSize","%s")
                                    .option("sandbox.MaxErrorStreamSize","%s")
                                    .build();
                        """;
        tracedLimits.print(printWriter, format);
    }

    private static OptionType<Duration> createDurationType() {
        return new OptionType<>("time", new Function<String, Duration>() {

            @SuppressWarnings("ResultOfMethodCallIgnored")
            @Override
            public Duration apply(String t) {
                try {
                    ChronoUnit foundUnit = null;
                    String foundUnitName = null;
                    for (ChronoUnit unit : ChronoUnit.values()) {
                        String unitName = getUnitName(unit);
                        if (unitName == null) {
                            continue;
                        }
                        if (t.endsWith(unitName)) {
                            foundUnit = unit;
                            foundUnitName = unitName;
                            break;
                        }
                    }
                    if (foundUnit == null || foundUnitName == null) {
                        throw invalidValue(t);
                    }
                    String subString = t.substring(0, t.length() - foundUnitName.length());
                    BigInteger value = new BigInteger(subString);
                    if (value.signum() < 0) {
                        throw invalidValue(t);
                    }
                    BigInteger millis = value.multiply(BigInteger.valueOf(foundUnit.getDuration().toMillis()));
                    if (millis.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) > 0) {
                        throw valueTooHigh(t);
                    }
                    return Duration.of(value.longValue(), foundUnit);
                } catch (NumberFormatException | ArithmeticException e) {
                    throw invalidValue(t);
                }
            }

            private String getUnitName(ChronoUnit unit) {
                switch (unit) {
                    case MILLIS:
                        return "ms";
                    case SECONDS:
                        return "s";
                    case MINUTES:
                        return "m";
                    case HOURS:
                        return "h";
                    case DAYS:
                        return "d";
                }
                return null;
            }

            private IllegalArgumentException invalidValue(String value) {
                throw new IllegalArgumentException("Invalid duration '" + value + "' specified. " //
                                + "A valid duration consists of a positive integer value followed by a chronological time unit. " //
                                + "For example '15ms' or '6s'. Valid time units are " //
                                + "'ms' for milliseconds, " //
                                + "'s' for seconds, " //
                                + "'m' for minutes, " //
                                + "'h' for hours, and " //
                                + "'d' for days.");
            }

            private IllegalArgumentException valueTooHigh(String value) {
                throw new IllegalArgumentException("Invalid duration '" + value +
                                "' specified. The duration exceeds Long.MAX_VALUE milliseconds, which is already roughly 292 million years.");
            }
        });
    }

    private enum SizeUnit {
        GIGABYTE("GB", 1024 * 1024 * 1024),
        MEGABYTE("MB", 1024 * 1024),
        KILOBYTE("KB", 1024),
        BYTE("B", 1);

        private final String symbol;
        private final long factor;

        SizeUnit(String symbol, long factor) {
            this.symbol = symbol;
            this.factor = factor;
        }
    }

    private enum TimeUnit {
        DAY("d", 1000 * 60 * 60 * 24),
        HOUR("h", 1000 * 60 * 60),
        MINUTE("m", 1000 * 60),
        SECOND("s", 1000),
        MILLIS("ms", 1);

        private final String symbol;
        private final long factor;

        TimeUnit(String symbol, long factor) {
            this.symbol = symbol;
            this.factor = factor;
        }
    }

    private static OptionType<Long> createSizeInBytesType() {
        return new OptionType<>("sizeinbytes", new Function<String, Long>() {

            @Override
            public Long apply(String s) {
                try {
                    SizeUnit foundUnit = null;
                    for (SizeUnit unit : SizeUnit.values()) {
                        if (s.endsWith(unit.symbol)) {
                            foundUnit = unit;
                            break;
                        }
                    }
                    if (foundUnit == null) {
                        throw invalidValue(s);
                    }
                    String subString = s.substring(0, s.length() - foundUnit.symbol.length());
                    long value = Long.parseLong(subString);
                    if (value < 0) {
                        throw invalidValue(s);
                    }
                    return Math.multiplyExact(value, foundUnit.factor);
                } catch (NumberFormatException | ArithmeticException e) {
                    throw invalidValue(s);
                }
            }

            private IllegalArgumentException invalidValue(String value) {
                throw new IllegalArgumentException("Invalid size '" + value + "' specified. " //
                                + "A valid size consists of a positive integer value and a byte-based size unit. " //
                                + "For example '512KB' or '100MB'. Valid size units are " //
                                + "'B' for bytes, " //
                                + "'KB' for kilobytes, " //
                                + "'MB' for megabytes, and " //
                                + "'GB' for gigabytes ");
            }
        });
    }
}
