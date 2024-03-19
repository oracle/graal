/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.hotspot.test;

import static jdk.graal.compiler.debug.MemUseTrackerKey.getCurrentThreadAllocatedBytes;
import static sun.misc.Unsafe.ARRAY_BYTE_BASE_OFFSET;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.graalvm.collections.UnmodifiableMapCursor;

import com.oracle.truffle.runtime.hotspot.libgraal.LibGraal;
import com.oracle.truffle.runtime.hotspot.libgraal.LibGraalIsolate;
import com.oracle.truffle.runtime.hotspot.libgraal.LibGraalScope;

import jdk.graal.compiler.api.test.ModuleSupport;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.debug.TTY;
import jdk.graal.compiler.hotspot.CompilationTask;
import jdk.graal.compiler.hotspot.HotSpotGraalCompiler;
import jdk.graal.compiler.hotspot.HotSpotGraalRuntimeProvider;
import jdk.graal.compiler.hotspot.ProfileReplaySupport;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import jdk.graal.compiler.options.OptionKey;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.serviceprovider.GraalServices;
import jdk.graal.compiler.serviceprovider.GraalUnsafeAccess;
import jdk.graal.compiler.util.OptionsEncoder;
import jdk.vm.ci.hotspot.HotSpotCompilationRequest;
import jdk.vm.ci.hotspot.HotSpotCompilationRequestResult;
import jdk.vm.ci.hotspot.HotSpotInstalledCode;
import jdk.vm.ci.hotspot.HotSpotJVMCIRuntime;
import jdk.vm.ci.hotspot.HotSpotResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.runtime.JVMCICompiler;
import sun.misc.Unsafe;

/**
 * Encapsulates functionality to compile a batch of methods for stand-alone compile test programs
 * such as {@link CompileTheWorld}, using libgraal if available, and optionally using multiple
 * compilation threads.
 * <p>
 * This class does not handle gathering of methods to be compiled, which are instead provided as an
 * argument to {@link #compileAll}.
 */
public class LibGraalCompilationDriver {

    static {
        // To be able to use Unsafe
        ModuleSupport.exportAndOpenAllPackagesToUnnamed("jdk.graal.compiler");
    }

    private final HotSpotJVMCIRuntime jvmciRuntime;

    private final HotSpotGraalCompiler compiler;

    /**
     * Per-isolate values used to control if metrics should be printed and reset as part of the next
     * compilation in the isolate.
     */
    private final Map<Long, AtomicBoolean> printMetrics = new HashMap<>();

    /**
     * If true, will invalidate all generated code after compilation to prevent filling up the code
     * cache.
     */
    private final boolean invalidateInstalledCode;

    /**
     * @see GraphBuilderConfiguration#eagerResolving()
     */
    private final boolean eagerResolving;

    /**
     * If true, will not exit with an error in case of errors during compilation.
     */
    private final boolean ignoreFailures;

    /**
     * Whether to use multiple threads for compilation.
     */
    private final boolean multiThreaded;

    /**
     * Number of threads to use for multithreaded compilation. If 0, the value of
     * {@code Runtime.getRuntime().availableProcessors()} is used instead.
     */
    private final int numThreads;

    /**
     * Interval between compilation progress reports, in seconds.
     */
    private final int statsInterval;

    /**
     * @param invalidateInstalledCode If true, will invalidate all generated code after compilation
     *            to prevent filling up the code in the cache.
     * @param eagerResolving If true, all types will be eagerly resolved during compilation.
     * @param multiThreaded Whether to use multiple threads for compilation.
     * @param threadCount Number of threads to use for multithreaded compilation. If 0, the value of
     *            {@code Runtime.getRuntime().availableProcessors()} is used instead.
     * @param statsInterval Interval between compilation progress reports, in seconds.
     */
    public LibGraalCompilationDriver(HotSpotJVMCIRuntime jvmciRuntime, HotSpotGraalCompiler compiler,
                    boolean invalidateInstalledCode, boolean eagerResolving, boolean ignoreFailures,
                    boolean multiThreaded, int threadCount, int statsInterval) {
        this.jvmciRuntime = jvmciRuntime;
        this.compiler = compiler;
        this.invalidateInstalledCode = invalidateInstalledCode;
        this.eagerResolving = eagerResolving;
        this.ignoreFailures = ignoreFailures;
        this.multiThreaded = multiThreaded;
        this.numThreads = threadCount;
        this.statsInterval = statsInterval;
    }

    public HotSpotGraalRuntimeProvider getGraalRuntime() {
        return compiler.getGraalRuntime();
    }

    private static final Unsafe UNSAFE = GraalUnsafeAccess.getUnsafe();

    /**
     * Implemented by
     * {@code com.oracle.svm.graal.hotspot.libgraal.LibGraalEntryPoints.compileMethod}.
     */
    public static native long compileMethodInLibgraal(long isolateThread,
                    long methodHandle,
                    boolean useProfilingInfo,
                    boolean installAsDefault,
                    boolean printMetrics,
                    boolean eagerResolving,
                    long optionsAddress,
                    int optionsSize,
                    int optionsHash,
                    long encodedThrowableBufferAddress,
                    int encodedThrowableBufferSize,
                    long timeAndMemBufferAddress,
                    long profileLoadAddress);

    /**
     * Manages native memory buffers for passing arguments into libgraal and receiving return
     * values. The native memory buffers are freed when this object is {@linkplain #close() closed}.
     */
    public static class LibGraalParams implements AutoCloseable {

        static {
            LibGraal.registerNativeMethods(LibGraalCompilationDriver.class);
        }

        /**
         * A buffer of native memory that will be handed to libgraal to send or receive data such as
         * options or stack traces. Will allocate and initialize memory upon the first call to
         * {@link #getAddress()}, and release it upon calling {@link #free()} or when
         * {@linkplain #close() closed}.
         */
        public abstract static class NativeBuffer implements AutoCloseable {
            private Long address = null;

            /**
             * @return the length of the buffer, in bytes.
             */
            public abstract int length();

            /**
             * Called upon allocation of the buffer, should copy any relevant data to the allocated
             * buffer.
             *
             * @param addr the address of the newly-allocated native buffer.
             */
            public void initialize(long addr) {
            }

            /**
             * @return The address of the native buffer. The buffer is allocated, initialized and
             *         cached the first time this function is called.
             */
            public long getAddress() {
                if (address == null) {
                    address = UNSAFE.allocateMemory(length());
                    initialize(address);
                }
                return address;
            }

            /**
             * Deallocates the native buffer. Subsequent calls to {@link #getAddress()} will
             * allocate a new buffer.
             */
            public void free() {
                if (address != null) {
                    UNSAFE.freeMemory(address);
                    address = null;
                }
            }

            @Override
            public void close() {
                free();
            }
        }

        /**
         * Native memory containing {@linkplain OptionsEncoder encoded} {@link OptionValues}.
         */
        public static class OptionsBuffer extends NativeBuffer {
            final byte[] encoded;
            final int hash;

            OptionsBuffer(OptionValues options) {
                Map<String, Object> map = new HashMap<>();
                UnmodifiableMapCursor<OptionKey<?>, Object> cursor = options.getMap().getEntries();
                while (cursor.advance()) {
                    final OptionKey<?> key = cursor.getKey();
                    Object value = cursor.getValue();
                    map.put(key.getName(), value);
                }
                encoded = OptionsEncoder.encode(map);
                hash = Arrays.hashCode(encoded);
            }

            @Override
            public int length() {
                return encoded.length;
            }

            @Override
            public void initialize(long address) {
                UNSAFE.copyMemory(encoded, ARRAY_BYTE_BASE_OFFSET, null, address, encoded.length);
            }

            public int getHash() {
                return hash;
            }
        }

        /**
         * Manages native memory for receiving a {@linkplain Throwable#printStackTrace() stack
         * trace} from libgraal serialized via {@link ByteArrayOutputStream} to a byte array.
         */
        public static class StackTraceBuffer extends NativeBuffer {
            final int capacity;

            StackTraceBuffer(int capacity) {
                this.capacity = capacity;
            }

            @Override
            public int length() {
                return capacity;
            }

            public String readToString() {
                long address = getAddress();
                int size = UNSAFE.getInt(address);
                byte[] data = new byte[size];
                UNSAFE.copyMemory(null, address + Integer.BYTES, data, ARRAY_BYTE_BASE_OFFSET, size);
                return new String(data).trim();
            }
        }

        /**
         * Memory and time buffer used to avoid measuring libgraal call overhead when performing
         * compilations with libgraal.
         */
        public static class MemoryAndTimeMeasurementsBuffer extends NativeBuffer {
            @Override
            public int length() {
                // one long for memory, one for time.
                return Long.BYTES * 2;
            }

            /**
             * @return number of bytes allocated as output by libgraal, in bytes.
             */
            public long readBytesAllocated() {
                return UNSAFE.getLong(getAddress());
            }

            static final long NANOS_IN_MILLI = 1_000_000;

            /**
             * @return compilation time as output by libgraal, in nanoseconds.
             */
            public long readTimeElapsed() {
                // Libgraal uses milliseconds, convert to nano for consistency.
                return UNSAFE.getLong(getAddress() + Long.BYTES) * NANOS_IN_MILLI;
            }
        }

        /**
         * Buffer to serialize profile paths over the libgraal boundary.
         */
        public static class ProfilePathBuffer extends NativeBuffer {
            private final byte[] profilesPathBytes;

            ProfilePathBuffer(String profilesPath) {
                /* Append the terminating 0. */
                byte[] stringBytes = profilesPath.getBytes();
                this.profilesPathBytes = Arrays.copyOf(stringBytes, stringBytes.length + 1);
            }

            @Override
            public int length() {
                return profilesPathBytes.length;
            }

            @Override
            public void initialize(long address) {
                UNSAFE.copyMemory(profilesPathBytes, ARRAY_BYTE_BASE_OFFSET, null, address, profilesPathBytes.length);
            }
        }

        private final OptionValues inputOptions;
        private final List<OptionsBuffer> optionsBuffers = new ArrayList<>();

        /**
         * Gets the isolate-specific buffer used to pass options to an isolate.
         */
        public OptionsBuffer getOptions() {
            return LibGraalScope.current().getIsolate().getSingleton(OptionsBuffer.class, () -> {
                OptionsBuffer optionsBuffer = new OptionsBuffer(inputOptions);
                synchronized (optionsBuffers) {
                    optionsBuffers.add(optionsBuffer);
                }
                return optionsBuffer;
            });

        }

        private final List<StackTraceBuffer> stackTraceBuffers = new ArrayList<>();

        /**
         * Gets a stack trace buffer for the current thread.
         */
        public StackTraceBuffer getStackTraceBuffer() {
            return stackTraceBuffer.get();
        }

        private final ThreadLocal<StackTraceBuffer> stackTraceBuffer = ThreadLocal.withInitial(() -> {
            StackTraceBuffer buffer = new StackTraceBuffer(10_000);
            synchronized (stackTraceBuffers) {
                stackTraceBuffers.add(buffer);
            }
            return buffer;
        });

        private final List<MemoryAndTimeMeasurementsBuffer> memTimeBuffers = new ArrayList<>();

        /**
         * Gets a stack trace buffer for the current thread.
         */
        public MemoryAndTimeMeasurementsBuffer getMemoryAndTimeBuffer() {
            return memTimeBuffer.get();
        }

        private final ThreadLocal<MemoryAndTimeMeasurementsBuffer> memTimeBuffer = ThreadLocal.withInitial(() -> {
            MemoryAndTimeMeasurementsBuffer buffer = new MemoryAndTimeMeasurementsBuffer();
            synchronized (memTimeBuffers) {
                memTimeBuffers.add(buffer);
            }
            return buffer;
        });

        /**
         * @param inputOptions options that will be passed to libgraal via {@link OptionsBuffer}.
         */
        public LibGraalParams(OptionValues inputOptions) {
            this.inputOptions = inputOptions;
        }

        @Override
        public void close() {
            synchronized (optionsBuffers) {
                for (OptionsBuffer options : optionsBuffers) {
                    options.free();
                }
                optionsBuffers.clear();
            }
            synchronized (stackTraceBuffers) {
                for (StackTraceBuffer buffer : stackTraceBuffers) {
                    buffer.free();
                }
                stackTraceBuffers.clear();
            }
            synchronized (memTimeBuffers) {
                for (MemoryAndTimeMeasurementsBuffer buffer : memTimeBuffers) {
                    buffer.free();
                }
                memTimeBuffers.clear();
            }
        }
    }

    /**
     * One unit of compilation: a single {@link ResolvedJavaMethod}. Can be subclassed to provide
     * additional information, such as its index in the compilation worklist, or additional files
     * relevant to the compilation.
     */
    public static class Compilation {
        /**
         * Method to be compiled.
         */
        private final HotSpotResolvedJavaMethod method;

        public Compilation(HotSpotResolvedJavaMethod method) {
            this.method = method;
        }

        public HotSpotResolvedJavaMethod getMethod() {
            return method;
        }

        @Override
        public String toString() {
            return method.format("%H.%n(%p):%r");
        }

        public String testName() {
            return "BulkCompile";
        }
    }

    /**
     * The result of compiling a single method.
     *
     * @param installedCode the compiled code. Will never be null if {@code this != null}.
     * @param compileTime amount of time taken to compile this method, in nanoseconds.
     * @param memoryUsed amount of memory that was allocated during compilation of this method, in
     *            bytes.
     */
    public record CompilationResult(HotSpotInstalledCode installedCode, long compileTime, long memoryUsed) {
        public long codeSize() {
            return installedCode.getCodeSize();
        }
    }

    /**
     * Returns the path to a .glog file containing stable profile data for the given compilation.
     * See {@link ProfileReplaySupport for more information.}
     *
     * @return {@code null} by default. This method be overridden by subclasses that use profile
     *         replay.
     */
    @SuppressWarnings("unused")
    protected String getProfilePath(Compilation compilation) {
        return null;
    }

    /**
     * Compiles a method.
     *
     * @param compilation the compilation to perform.
     * @param libgraal if non-null, compilation will be performed using libgraal. Otherwise, it will
     *            be performed in-process with a {@link HotSpotCompilationRequest}.
     * @param options option values to use during compilation. Will only be used with a jargraal
     *            compilation: libgraal compilations will instead use options encoded in
     *            {@code libgraal.getOptions()}.
     * @return the result of compilation, or {@code null} if compilation failed.
     */
    public CompilationResult compile(Compilation compilation, LibGraalParams libgraal, OptionValues options) {
        try {
            String profilePath = getProfilePath(compilation);
            boolean installAsDefault = false;
            CompilationResult result;
            if (libgraal != null) {
                result = compileWithLibGraal(compilation, libgraal, profilePath, installAsDefault);
            } else {
                result = compileWithJarGraal(compilation, options, profilePath, installAsDefault);
            }
            return result;
        } catch (Throwable t) {
            // Catch everything and print a message
            TTY.println("%s : Error compiling method: %s", compilation.testName(), compilation);
            t.printStackTrace(TTY.out);
            return null;
        }
    }

    /**
     * Compiles a method with libgraal.
     *
     * @param compilation compilation to perform.
     * @param libgraal native memory buffers to pass to libgraal. Must not be null.
     * @param profilePath path to a .glog file containing stable profiles for the given compilation.
     *            If null, profile replay will be disabled for this compilation.
     * @param installAsDefault whether to install the compiled code as default for the given method.
     */
    protected CompilationResult compileWithLibGraal(Compilation compilation, LibGraalParams libgraal, String profilePath, boolean installAsDefault) {
        HotSpotResolvedJavaMethod method = compilation.getMethod();
        long methodHandle = LibGraal.translate(method);
        long isolateThread = LibGraalScope.getIsolateThread();

        LibGraalParams.StackTraceBuffer stackTraceBuffer = libgraal.getStackTraceBuffer();
        LibGraalParams.OptionsBuffer options = libgraal.getOptions();
        LibGraalParams.MemoryAndTimeMeasurementsBuffer memTimeBuffer = libgraal.getMemoryAndTimeBuffer();

        boolean useProfilingInfo = profilePath != null;
        try (LibGraalParams.ProfilePathBuffer profileBuffer = useProfilingInfo ? new LibGraalParams.ProfilePathBuffer(profilePath) : null) {
            long installedCodeHandle = compileMethodInLibgraal(isolateThread,
                            methodHandle,
                            useProfilingInfo,
                            installAsDefault,
                            shouldPrintMetrics(LibGraalScope.current().getIsolate()),
                            eagerResolving,
                            options.getAddress(),
                            options.length(),
                            options.getHash(),
                            stackTraceBuffer.getAddress(),
                            stackTraceBuffer.length(),
                            memTimeBuffer.getAddress(),
                            profileBuffer == null ? 0 : profileBuffer.getAddress());

            HotSpotInstalledCode installedCode = LibGraal.unhand(HotSpotInstalledCode.class, installedCodeHandle);
            if (installedCode == null) {
                String stackTrace = stackTraceBuffer.readToString();
                TTY.println("%s : Error compiling method: %s", compilation.testName(), compilation);
                TTY.println(stackTrace);
                return null;
            }
            return new CompilationResult(installedCode, memTimeBuffer.readTimeElapsed(), memTimeBuffer.readBytesAllocated());
        }
    }

    /**
     * Creates a new {@link CompilationTask} with the given parameters. Subclasses wishing to use
     * custom compilation tasks with different parameters should override this method instead of the
     * helper {@link #createCompilationTask(HotSpotResolvedJavaMethod, boolean, boolean)}.
     */
    protected CompilationTask createCompilationTask(HotSpotJVMCIRuntime runtime, HotSpotGraalCompiler graalCompiler,
                    HotSpotCompilationRequest request, boolean useProfilingInfo, boolean installAsDefault) {
        return new CompilationTask(runtime, graalCompiler, request, useProfilingInfo, false, false, eagerResolving, installAsDefault);
    }

    /**
     * Creates a new {@link CompilationTask} for the given method. Subclasses wishing to use custom
     * compilation tasks with different parameters should instead override
     * {@link #createCompilationTask(HotSpotJVMCIRuntime, HotSpotGraalCompiler, HotSpotCompilationRequest, boolean, boolean)}.
     */
    protected final CompilationTask createCompilationTask(HotSpotResolvedJavaMethod method, boolean useProfilingInfo, boolean installAsDefault) {
        int entryBCI = JVMCICompiler.INVOCATION_ENTRY_BCI;
        HotSpotCompilationRequest request = new HotSpotCompilationRequest(method, entryBCI, 0L);
        return createCompilationTask(jvmciRuntime, compiler, request, useProfilingInfo, installAsDefault);
    }

    /**
     * Compiles a method using {@link HotSpotCompilationRequest}.
     *
     * @param compilation compilation to perform.
     * @param options option values that will be used during compilation.
     * @param profilePath path to a .glog file containing stable profiles for the given compilation.
     *            If null, profile replay will be disabled for this compilation.
     * @param installAsDefault whether to install the compiled code as default for the given method.
     */
    protected CompilationResult compileWithJarGraal(Compilation compilation, OptionValues options, String profilePath, boolean installAsDefault) {
        HotSpotResolvedJavaMethod method = compilation.getMethod();
        OptionValues compileOptions = options;

        boolean useProfilingInfo = profilePath != null;
        if (useProfilingInfo) {
            compileOptions = new OptionValues(compileOptions, ProfileReplaySupport.Options.LoadProfiles, profilePath);
        }

        long start = System.nanoTime();
        long allocatedAtStart = getCurrentThreadAllocatedBytes();

        CompilationTask task = createCompilationTask(method, useProfilingInfo, installAsDefault);
        HotSpotCompilationRequestResult result = task.runCompilation(compileOptions);
        if (result.getFailure() != null) {
            throw new GraalError("Compilation request failed: %s", result.getFailureMessage());
        }
        HotSpotInstalledCode installedCode = task.getInstalledCode();
        assert installedCode != null : "installed code is null yet no failure detected";
        long duration = System.nanoTime() - start;
        long memoryUsed = getCurrentThreadAllocatedBytes() - allocatedAtStart;
        return new CompilationResult(installedCode, duration, memoryUsed);
    }

    private final AtomicInteger failedCompilations = new AtomicInteger(0);

    /**
     * Compiles all the given methods, using libgraal if available.
     */
    @SuppressWarnings("try")
    public void compileAll(List<? extends Compilation> compilations, OptionValues options) {
        try (LibGraalParams libgraal = LibGraal.isAvailable() ? new LibGraalParams(options) : null) {
            compileAll(libgraal, compilations, options);
        }
    }

    private void compileAll(LibGraalParams libgraal, List<? extends Compilation> compilations, OptionValues options) {
        failedCompilations.set(0);
        int threadCount = getThreadCount();

        AtomicLong compileTime = new AtomicLong();
        AtomicLong memoryUsed = new AtomicLong();
        AtomicLong codeSize = new AtomicLong();

        TTY.println("%s : Starting compilations ...", testName());
        if (libgraal != null) {
            TTY.println("%s : Using libgraal for compilation.", testName());
        }

        long start = System.nanoTime();
        Map<ResolvedJavaMethod, CompilationResult> results;
        if (threadCount == 1) {
            results = compileAllSingleThreaded(libgraal, compilations, options, compileTime, memoryUsed, codeSize);
        } else {
            results = compileAllMultiThreaded(libgraal, compilations, options, threadCount, compileTime, memoryUsed, codeSize);
        }
        if (!ignoreFailures && failedCompilations.get() > 0) {
            throw new GraalError("%d failures occurred during compilation", failedCompilations.get());
        }

        long elapsedTime = System.nanoTime() - start;

        printResults(options, results, compileTime.get(), memoryUsed.get(), codeSize.get(), elapsedTime);
    }

    /**
     * @return the actual number of threads to use for compilation. If {@link #numThreads} is 0,
     *         will return {@code Runtime.getRuntime().availableProcessors()}.
     */
    private int getThreadCount() {
        int threadCount = 1;
        if (multiThreaded) {
            threadCount = numThreads;
            if (threadCount == 0) {
                threadCount = Runtime.getRuntime().availableProcessors();
            }
        }
        return threadCount;
    }

    /**
     * Compiles the given method using {@link #compile} and updates the given parameters with
     * information gathered during compilation.
     *
     * @param compileTime total amount of time spent in compilation, in nanoseconds.
     * @param memoryUsed total amount of memory allocated during compilation, in bytes.
     * @param codeSize total code size of all compiled methods, in bytes.
     * @param results records individual compilation metrics for each method.
     */
    private void compileAndRecord(Compilation task, LibGraalParams libgraal, OptionValues options, AtomicLong compileTime, AtomicLong memoryUsed, AtomicLong codeSize,
                    Map<ResolvedJavaMethod, CompilationResult> results) {
        CompilationResult result = compile(task, libgraal, options);
        if (result == null) {
            failedCompilations.getAndAdd(1);
            return;
        }
        compileTime.getAndAdd(result.compileTime());
        memoryUsed.getAndAdd(result.memoryUsed());
        codeSize.getAndAdd(result.codeSize());
        results.put(task.getMethod(), result);
        if (invalidateInstalledCode) {
            result.installedCode().invalidate();
        }
    }

    /**
     * Compiles all methods in {@code compilations} sequentially on one thread.
     */
    @SuppressWarnings("try")
    private Map<ResolvedJavaMethod, CompilationResult> compileAllSingleThreaded(
                    LibGraalParams libgraal, List<? extends Compilation> compilations, OptionValues options,
                    AtomicLong compileTime, AtomicLong memoryUsed, AtomicLong codeSize) {
        Map<ResolvedJavaMethod, CompilationResult> results = new HashMap<>();

        long intervalStart = System.currentTimeMillis();
        long lastCompletedTaskCount = 0;
        long completedTaskCount = 0;
        try (LibGraalScope scope = libgraal == null ? null : new LibGraalScope()) {
            for (Compilation task : compilations) {
                compileAndRecord(task, libgraal, options, compileTime, memoryUsed, codeSize, results);
                completedTaskCount++;

                long now = System.currentTimeMillis();
                if (now - intervalStart > statsInterval * 1000L) {
                    long completedSinceLastInterval = completedTaskCount - lastCompletedTaskCount;
                    printIntervalMessage(libgraal, compilations.size(), completedTaskCount, completedSinceLastInterval);
                    lastCompletedTaskCount = completedTaskCount;
                    intervalStart = now;
                }
            }
        }
        return results;
    }

    static final class GraalCompileThread extends Thread {
        private final LibGraalParams libgraal;

        GraalCompileThread(Runnable r, LibGraalParams libgraal) {
            super(r);
            this.libgraal = libgraal;
            setName("GraalCompileThread-" + GraalServices.getThreadId(this));
            setPriority(Thread.MAX_PRIORITY);
            setDaemon(true);
        }

        @SuppressWarnings("try")
        @Override
        public void run() {
            setContextClassLoader(getClass().getClassLoader());
            try (LibGraalScope scope = libgraal == null ? null : new LibGraalScope()) {
                super.run();
            }
        }
    }

    static final class GraalCompileThreadFactory implements ThreadFactory {
        private final LibGraalParams libgraal;

        GraalCompileThreadFactory(LibGraalParams libgraal) {
            this.libgraal = libgraal;
        }

        @Override
        public Thread newThread(Runnable r) {
            return new GraalCompileThread(r, libgraal);
        }
    }

    /**
     * Compiles all methods in {@code compilations} in parallel, using a {@link ThreadPoolExecutor}.
     */
    private Map<ResolvedJavaMethod, CompilationResult> compileAllMultiThreaded(
                    LibGraalParams libgraal, List<? extends Compilation> compilations, OptionValues options,
                    int threadCount, AtomicLong compileTime, AtomicLong memoryUsed, AtomicLong codeSize) {
        TTY.println("%s : Using %d threads", testName(), threadCount);

        Map<ResolvedJavaMethod, CompilationResult> results = new ConcurrentHashMap<>();
        try (ThreadPoolExecutor threadPool = new ThreadPoolExecutor(threadCount, threadCount, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>(),
                        new GraalCompileThreadFactory(libgraal))) {
            for (Compilation task : compilations) {
                threadPool.submit(() -> compileAndRecord(task, libgraal, options, compileTime, memoryUsed, codeSize, results));
            }

            int taskCount = compilations.size();
            int wakeups = 0;
            long lastCompletedTaskCount = 0;
            long completedTaskCount;
            do {
                completedTaskCount = threadPool.getCompletedTaskCount();
                if (completedTaskCount != 0 && (wakeups % statsInterval == 0 || completedTaskCount == taskCount)) {
                    long completedSinceLastInterval = completedTaskCount - lastCompletedTaskCount;
                    printIntervalMessage(libgraal, taskCount, completedTaskCount, completedSinceLastInterval);
                    lastCompletedTaskCount = completedTaskCount;
                }
                try {
                    threadPool.awaitTermination(1, TimeUnit.SECONDS);
                    wakeups++;
                } catch (InterruptedException e) {

                }
            } while (completedTaskCount != taskCount);
        }
        return results;
    }

    /**
     * Requests that the next compilation in each libgraal isolate prints and resets global metrics.
     */
    private void armPrintMetrics() {
        synchronized (printMetrics) {
            for (AtomicBoolean value : printMetrics.values()) {
                value.set(true);
            }
        }
    }

    /**
     * Determines if the next compilation in {@code isolate} should print and reset global metrics.
     */
    private boolean shouldPrintMetrics(LibGraalIsolate isolate) {
        synchronized (printMetrics) {
            return printMetrics.computeIfAbsent(isolate.getId(), id -> new AtomicBoolean()).getAndSet(false);
        }
    }

    /**
     * Prints a message showing the amount of compilations completed in the last
     * {@link #statsInterval} seconds.
     *
     * @param total total amount of methods to be compiled in this run.
     * @param completed current amount of methods compiled.
     * @param completedInInterval amount of methods that were compiled since the last time this
     *            message was printed.
     */
    private void printIntervalMessage(LibGraalParams libgraal, long total, long completed, long completedInInterval) {
        double rate = (double) completedInInterval / statsInterval;
        long percent = completed * 100 / total;
        TTY.println("%s : [%2d%%, %.1f compiles/s] %d of %d compilations completed, %d in last interval",
                        testName(), percent, rate,
                        completed, total,
                        completedInInterval);
        if (libgraal != null) {
            armPrintMetrics();
        }
    }

    /**
     * Called once all methods have been compiled, so that compilation statistics can be printed to
     * the console. Since each test will be interested in different subsets of compilation data,
     * this method makes no assumptions and prints nothing. All information printing is left to the
     * subclass.
     *
     * @param options option values used for compilation (that were passed to
     *            {@link #compileAll(List, OptionValues)}.)
     * @param compileTimes individual compilation statistics for each method.
     * @param compileTime total amount of time spent in compilation, in nanoseconds. This is summed
     *            across all threads.
     * @param memoryUsed total amount of memory allocated during compilation, in bytes.
     * @param codeSize total code size of all compiled methods, in bytes.
     * @param elapsedTime wall-time elapsed between the start and end of the compile test, in
     *            nanoseconds.
     */
    protected void printResults(OptionValues options, Map<ResolvedJavaMethod, CompilationResult> compileTimes, long compileTime, long memoryUsed, long codeSize, long elapsedTime) {
    }

    /**
     * Used as a label for console output during compilation (for example, in
     * {@link #printIntervalMessage}. Should be overridden by subclasses.
     */
    public String testName() {
        return "BulkCompile";
    }
}
