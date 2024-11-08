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
package jdk.graal.compiler.hotspot.libgraal;

import java.util.Arrays;
import java.util.Map;
import java.util.function.BiConsumer;

import org.graalvm.collections.EconomicMap;

import jdk.graal.compiler.debug.GlobalMetrics;
import jdk.graal.compiler.hotspot.CompilationContext;
import jdk.graal.compiler.hotspot.CompilationTask;
import jdk.graal.compiler.hotspot.HotSpotGraalCompiler;
import jdk.graal.compiler.hotspot.HotSpotGraalRuntime;
import jdk.graal.compiler.hotspot.HotSpotGraalServices;
import jdk.graal.compiler.hotspot.ProfileReplaySupport;
import jdk.graal.compiler.options.OptionDescriptors;
import jdk.graal.compiler.options.OptionKey;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.options.OptionsParser;
import jdk.graal.compiler.util.OptionsEncoder;
import jdk.internal.misc.Unsafe;
import jdk.vm.ci.hotspot.HotSpotCompilationRequest;
import jdk.vm.ci.hotspot.HotSpotInstalledCode;
import jdk.vm.ci.hotspot.HotSpotJVMCIRuntime;
import jdk.vm.ci.hotspot.HotSpotResolvedJavaMethod;
import jdk.vm.ci.hotspot.HotSpotResolvedJavaType;
import jdk.vm.ci.hotspot.HotSpotVMConfigAccess;
import jdk.vm.ci.hotspot.HotSpotVMConfigStore;
import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.runtime.JVMCIBackend;
import jdk.vm.ci.runtime.JVMCICompiler;

import static jdk.graal.compiler.serviceprovider.GraalServices.getCurrentThreadAllocatedBytes;
import static jdk.graal.compiler.serviceprovider.GraalServices.isThreadAllocatedMemorySupported;

/**
 * This class provides implementations for {@code @CEntryPoint}s that libgraal has to provide as a
 * JVM JIT compiler as well as handles (created by {@link BuildTime#getRuntimeHandles}) to other
 * utility methods needed by {@code LibGraalFeature}.
 */
public class RunTime {

    private static final Unsafe UNSAFE = Unsafe.getUnsafe();

    private record CachedOptions(OptionValues options, long hash) {
    }

    private static final ThreadLocal<CachedOptions> CACHED_OPTIONS_THREAD_LOCAL = new ThreadLocal<>();

    private static OptionValues decodeOptions(long address, int size, int hash) {
        CachedOptions options = CACHED_OPTIONS_THREAD_LOCAL.get();
        if (options == null || options.hash != hash) {
            byte[] buffer = new byte[size];
            UNSAFE.copyMemory(null, address, buffer, Unsafe.ARRAY_BYTE_BASE_OFFSET, size);
            int actualHash = Arrays.hashCode(buffer);
            if (actualHash != hash) {
                throw new IllegalArgumentException(actualHash + " != " + hash);
            }
            Map<String, Object> srcMap = OptionsEncoder.decode(buffer);
            final EconomicMap<OptionKey<?>, Object> dstMap = OptionValues.newOptionMap();
            final Iterable<OptionDescriptors> loader = OptionsParser.getOptionsLoader();
            for (Map.Entry<String, Object> e : srcMap.entrySet()) {
                final String optionName = e.getKey();
                final Object optionValue = e.getValue();
                OptionsParser.parseOption(optionName, optionValue, dstMap, loader);
            }

            options = new CachedOptions(new OptionValues(dstMap), hash);
            CACHED_OPTIONS_THREAD_LOCAL.set(options);
        }
        return options.options;
    }

    /**
     * This is the implementation that {@code @CEntryPoint}-method
     * {@code com.oracle.svm.graal.hotspot.libgraal.LibGraalEntryPoints#compileMethod} delegates to.
     * Most parameters are identical to the caller method parameters except for the following:
     *
     * @param profileLoadPath value of the {@code Options#LoadProfiles} option or null
     * @param timeAndMemConsumer allows caller to get info about compile time and memory consumption
     */
    @SuppressWarnings("try")
    public static long compileMethod(long methodHandle, boolean useProfilingInfo,
                    boolean installAsDefault, boolean printMetrics, boolean eagerResolving,
                    long optionsAddress, int optionsSize, int optionsHash,
                    String profileLoadPath, BiConsumer<Long, Long> timeAndMemConsumer) {

        HotSpotJVMCIRuntime runtime = HotSpotJVMCIRuntime.runtime();
        HotSpotGraalCompiler compiler = (HotSpotGraalCompiler) runtime.getCompiler();
        if (methodHandle == 0L) {
            return 0L;
        }

        int entryBCI = JVMCICompiler.INVOCATION_ENTRY_BCI;
        HotSpotResolvedJavaMethod method = runtime.unhand(HotSpotResolvedJavaMethod.class, methodHandle);
        HotSpotCompilationRequest request = new HotSpotCompilationRequest(method, entryBCI, 0L);
        try (CompilationContext ignored = HotSpotGraalServices.openLocalCompilationContext(request)) {
            CompilationTask task = new CompilationTask(runtime, compiler, request, useProfilingInfo, false, false, eagerResolving, installAsDefault);
            long allocatedBytesBefore = 0;
            long timeBefore = 0;
            if (timeAndMemConsumer != null) {
                allocatedBytesBefore = isThreadAllocatedMemorySupported() ? getCurrentThreadAllocatedBytes() : -1;
                timeBefore = System.nanoTime();
            }
            OptionValues options = decodeOptions(optionsAddress, optionsSize, optionsHash);
            if (profileLoadPath != null) {
                options = new OptionValues(options, ProfileReplaySupport.Options.LoadProfiles, profileLoadPath);
            }
            task.runCompilation(options);
            if (timeAndMemConsumer != null) {
                long allocatedBytesAfter = allocatedBytesBefore == -1 ? -1 : getCurrentThreadAllocatedBytes();
                long bytesAllocated = allocatedBytesAfter - allocatedBytesBefore;
                long timeAfter = System.nanoTime();
                long timeSpent = timeAfter - timeBefore;
                timeAndMemConsumer.accept(timeSpent, bytesAllocated);
            }
            HotSpotInstalledCode installedCode = task.getInstalledCode();
            if (printMetrics) {
                GlobalMetrics metricValues = ((HotSpotGraalRuntime) compiler.getGraalRuntime()).getMetricValues();
                metricValues.print(options);
                metricValues.clear();
            }
            return runtime.translate(installedCode);
        }
    }

    @SuppressWarnings({"unused", "try"})
    public static long hashConstantOopFields(long typeHandle, boolean useScope, int iterations,
                    int oopsPerIteration, boolean verbose, Runnable doReferenceHandling) {
        HotSpotJVMCIRuntime runtime = HotSpotJVMCIRuntime.runtime();
        JVMCIBackend backend = runtime.getHostJVMCIBackend();
        ConstantReflectionProvider constantReflection = backend.getConstantReflection();
        HotSpotResolvedJavaType type = runtime.unhand(HotSpotResolvedJavaType.class, typeHandle);
        ResolvedJavaField[] staticFields = type.getStaticFields();
        JavaConstant receiver = null;
        long hash = 13;

        Object scopeDescription = "TestingOopHandles";

        int remainingIterations = iterations;
        while (remainingIterations-- > 0) {
            ResolvedJavaField lastReadField = null;
            try (CompilationContext scope = useScope ? HotSpotGraalServices.openLocalCompilationContext(scopeDescription) : null) {
                if (verbose && useScope) {
                    System.out.println("Opened " + scopeDescription);
                }
                int remainingOops = oopsPerIteration;
                while (remainingOops-- > 0) {
                    for (ResolvedJavaField field : staticFields) {
                        if (field.getType().getJavaKind() == JavaKind.Object) {
                            JavaConstant value = constantReflection.readFieldValue(field, receiver);
                            if (value != null) {
                                lastReadField = field;
                                hash = hash ^ value.hashCode();
                            }
                        }
                    }
                }
            }
            if (!useScope) {
                System.gc();
                if (verbose) {
                    System.out.println("calling reference handling");
                }
                doReferenceHandling.run();
                if (verbose) {
                    System.out.println("called reference handling");
                }
                // Need one more remote oop creation to trigger releasing
                // of remote oops that were wrapped in weakly reachable
                // IndirectHotSpotObjectConstantImpl objects just collected.
                constantReflection.readFieldValue(lastReadField, receiver);
            } else if (verbose) {
                System.out.println(" Closed " + scopeDescription);
            }
        }
        return hash;
    }

    private static long jniEnvironmentOffset = Integer.MAX_VALUE;

    private static long getJniEnvironmentOffset() {
        if (jniEnvironmentOffset == Integer.MAX_VALUE) {
            HotSpotJVMCIRuntime jvmciRuntime = HotSpotJVMCIRuntime.runtime();
            HotSpotVMConfigStore store = jvmciRuntime.getConfigStore();
            HotSpotVMConfigAccess config = new HotSpotVMConfigAccess(store);
            jniEnvironmentOffset = config.getFieldOffset("JavaThread::_jni_environment", Integer.class, "JNIEnv");
        }
        return jniEnvironmentOffset;
    }

    /**
     * Gets the JNIEnv value for the current HotSpot thread.
     */
    static long getJNIEnv() {
        HotSpotJVMCIRuntime jvmciRuntime = HotSpotJVMCIRuntime.runtime();
        long offset = getJniEnvironmentOffset();
        long javaThreadAddr = jvmciRuntime.getCurrentJavaThread();
        return javaThreadAddr + offset;
    }

    /**
     * Ensures the current thread is attached to the peer runtime.
     *
     * @param isDaemon if the thread is not yet attached, should it be attached as a daemon
     * @param isolate if non-null, the isolate for the current thread is returned in element 0
     * @return {@code true} if this call attached the current thread, {@code false} if the current
     *         thread was already attached
     */
    public static boolean attachCurrentThread(boolean isDaemon, long[] isolate) {
        long[] javaVMInfo = isolate != null ? new long[4] : null;
        boolean res = HotSpotJVMCIRuntime.runtime().attachCurrentThread(isDaemon, javaVMInfo);
        if (isolate != null) {
            isolate[0] = javaVMInfo[1];
        }
        return res;
    }

    /**
     * Detaches the current thread from the peer runtime.
     *
     * @param release if {@code true} and the VM supports releasing the {@code JavaVM} associated
     *            with libgraal runtimes and this is the last thread attached to a libgraal runtime,
     *            then this call destroys the associated {@code JavaVM} instance, releasing its
     *            resources
     * @return {@code true} if the {@code JavaVM} associated with the libgraal runtime was destroyed
     *         as a result of this call
     */
    public static boolean detachCurrentThread(boolean release) {
        return HotSpotJVMCIRuntime.runtime().detachCurrentThread(release);
    }
}
