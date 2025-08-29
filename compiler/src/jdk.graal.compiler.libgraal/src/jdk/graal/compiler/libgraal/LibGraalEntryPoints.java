/*
 * Copyright (c) 2019, 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.libgraal;

import static jdk.graal.compiler.serviceprovider.GraalServices.getCurrentThreadAllocatedBytes;
import static jdk.graal.compiler.serviceprovider.GraalServices.isThreadAllocatedMemorySupported;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.Map;
import java.util.function.BooleanSupplier;

import org.graalvm.collections.EconomicMap;
import org.graalvm.jniutils.JNI.JNIEnv;
import org.graalvm.jniutils.JNIExceptionWrapper;
import org.graalvm.jniutils.JNIMethodScope;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.nativeimage.c.function.CEntryPoint.IsolateThreadContext;
import org.graalvm.nativeimage.c.type.CTypeConversion;
import org.graalvm.word.PointerBase;

import jdk.graal.compiler.debug.GlobalMetrics;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.debug.TTY;
import jdk.graal.compiler.hotspot.CompilationContext;
import jdk.graal.compiler.hotspot.CompilationTask;
import jdk.graal.compiler.hotspot.HotSpotGraalCompiler;
import jdk.graal.compiler.hotspot.HotSpotGraalRuntime;
import jdk.graal.compiler.hotspot.HotSpotGraalServices;
import jdk.graal.compiler.hotspot.ProfileReplaySupport;
import jdk.graal.compiler.hotspot.replaycomp.ReplayCompilationRunner;
import jdk.graal.compiler.hotspot.replaycomp.ReplayCompilationSupport;
import jdk.graal.compiler.options.OptionDescriptors;
import jdk.graal.compiler.options.OptionKey;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.options.OptionsParser;
import jdk.graal.compiler.util.OptionsEncoder;
import jdk.graal.compiler.word.Word;
import jdk.internal.misc.Unsafe;
import jdk.vm.ci.hotspot.HotSpotCompilationRequest;
import jdk.vm.ci.hotspot.HotSpotCompilationRequestResult;
import jdk.vm.ci.hotspot.HotSpotInstalledCode;
import jdk.vm.ci.hotspot.HotSpotJVMCIRuntime;
import jdk.vm.ci.hotspot.HotSpotResolvedJavaMethod;
import jdk.vm.ci.hotspot.HotSpotResolvedJavaType;
import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.runtime.JVMCIBackend;
import jdk.vm.ci.runtime.JVMCICompiler;

/**
 * Encapsulates {@link CEntryPoint} implementations.
 */
final class LibGraalEntryPoints {

    @Platforms(Platform.HOSTED_ONLY.class)
    private LibGraalEntryPoints() {
    }

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
     * The implementation of
     * {@code jdk.graal.compiler.hotspot.test.LibGraalCompilationDriver#compileMethodInLibgraal}.
     *
     * @param methodHandle the method to be compiled. This is a handle to a
     *            {@code HotSpotResolvedJavaMethod} in HotSpot's heap. A value of 0L can be passed
     *            to use this method for the side effect of initializing a
     *            {@code HotSpotGraalCompiler} instance without doing any compilation.
     * @param useProfilingInfo specifies if profiling info should be used during the compilation
     * @param installAsDefault specifies if the compiled code should be installed for the
     *            {@code Method*} associated with {@code methodHandle}
     * @param printMetrics specifies if global metrics should be printed and reset
     * @param optionsAddress native byte buffer storing a serialized {@code OptionValues} object
     * @param optionsSize the number of bytes in the buffer
     * @param optionsHash hash code of bytes in the buffer (computed with
     *            {@link Arrays#hashCode(byte[])})
     * @param stackTraceAddress a native buffer in which a serialized stack trace can be returned.
     *            The caller will only read from this buffer if this method returns 0. A returned
     *            serialized stack trace is returned in this buffer with the following format:
     *
     *            <pre>
     *               struct {
     *                   int   length;
     *                   byte  data[length]; // Bytes from a stack trace printed to a ByteArrayOutputStream.
     *               }
     *            </pre>
     *
     *            where {@code length} is truncated to {@code stackTraceCapacity - 4} if necessary
     *
     * @param stackTraceCapacity the size of the stack trace buffer
     * @param timeAndMemBufferAddress 16-byte native buffer to store result of time and memory
     *            measurements of the compilation
     * @param profilePathBufferAddress native buffer containing a 0-terminated C string representing
     *            {@code Options#LoadProfiles} path.
     * @return a handle to a {@code InstalledCode} in HotSpot's heap or 0 if compilation failed
     */
    @SuppressWarnings({"unused", "try"})
    @CEntryPoint(name = "Java_jdk_graal_compiler_hotspot_test_LibGraalCompilationDriver_compileMethodInLibgraal", include = LibGraalFeature.IsEnabled.class)
    private static long compileMethod(JNIEnv jniEnv,
                    PointerBase jclass,
                    @IsolateThreadContext long isolateThreadAddress,
                    long methodHandle,
                    boolean useProfilingInfo,
                    boolean installAsDefault,
                    boolean printMetrics,
                    boolean eagerResolving,
                    long optionsAddress,
                    int optionsSize,
                    int optionsHash,
                    long stackTraceAddress,
                    int stackTraceCapacity,
                    long timeAndMemBufferAddress,
                    long profilePathBufferAddress) {
        try (JNIMethodScope jniScope = new JNIMethodScope("compileMethod", jniEnv)) {
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
                if (timeAndMemBufferAddress != 0) {
                    allocatedBytesBefore = isThreadAllocatedMemorySupported() ? getCurrentThreadAllocatedBytes() : -1;
                    timeBefore = System.nanoTime();
                }
                OptionValues options = decodeOptions(optionsAddress, optionsSize, optionsHash);
                if (profilePathBufferAddress > 0) {
                    String profileLoadPath = CTypeConversion.toJavaString(Word.pointer(profilePathBufferAddress));
                    options = new OptionValues(options, ProfileReplaySupport.Options.LoadProfiles, profileLoadPath);
                }
                HotSpotCompilationRequestResult compilationRequestResult = task.runCompilation(options);
                if (compilationRequestResult.getFailure() != null) {
                    throw new GraalError(compilationRequestResult.getFailureMessage());
                }
                if (timeAndMemBufferAddress != 0) {
                    long allocatedBytesAfter = allocatedBytesBefore == -1 ? -1 : getCurrentThreadAllocatedBytes();
                    long bytesAllocated = allocatedBytesAfter - allocatedBytesBefore;
                    long timeAfter = System.nanoTime();
                    long timeSpent = timeAfter - timeBefore;
                    Unsafe.getUnsafe().putLong(timeAndMemBufferAddress, bytesAllocated);
                    Unsafe.getUnsafe().putLong(timeAndMemBufferAddress + 8, timeSpent);
                }
                HotSpotInstalledCode installedCode = task.getInstalledCode();
                if (printMetrics) {
                    GlobalMetrics metricValues = ((HotSpotGraalRuntime) compiler.getGraalRuntime()).getMetricValues();
                    metricValues.print(options);
                    metricValues.clear();
                }
                return runtime.translate(installedCode);
            }
        } catch (Throwable t) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            t.printStackTrace(new PrintStream(baos));
            byte[] stackTrace = baos.toByteArray();
            int length = Math.min(stackTraceCapacity - Integer.BYTES, stackTrace.length);
            Unsafe.getUnsafe().putInt(stackTraceAddress, length);
            Unsafe.getUnsafe().copyMemory(stackTrace, Unsafe.ARRAY_BYTE_BASE_OFFSET, null, stackTraceAddress + Integer.BYTES, length);
            return 0L;
        } finally {
            /*
             * libgraal doesn't use a dedicated reference handler thread, so we trigger the
             * reference handling manually when a compilation finishes.
             */
            LibGraalSupportImpl.doReferenceHandling();
        }
    }

    @CEntryPoint(name = "Java_jdk_graal_compiler_hotspot_test_LibGraalCompilerTest_hashConstantOopFields", include = LibGraalFeature.IsEnabled.class)
    @SuppressWarnings({"unused", "try"})
    private static long hashConstantOopFields(JNIEnv jniEnv,
                    PointerBase jclass,
                    @IsolateThreadContext long isolateThreadAddress,
                    long typeHandle,
                    boolean useScope,
                    int iterations,
                    int oopsPerIteration,
                    boolean verbose) {
        try (JNIMethodScope scope = new JNIMethodScope("hashConstantOopFields", jniEnv)) {
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
                try (CompilationContext scope1 = useScope ? HotSpotGraalServices.openLocalCompilationContext(scopeDescription) : null) {
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
                    LibGraalSupportImpl.doReferenceHandling();
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
        } catch (Throwable t) {
            JNIExceptionWrapper.throwInHotSpot(jniEnv, t);
            return 0;
        }
    }

    /**
     * Runs the replay compilation launcher in libgraal with the provided command-line arguments.
     *
     * @param argBuffer a native buffer containing a zero-terminated UTF-8 string of
     *            {@code '\n'}-separated arguments for the replay compilation launcher
     * @return the exit status of the replay compilation launcher
     */
    @SuppressWarnings({"unused", "try"})
    @CEntryPoint(name = "Java_jdk_graal_compiler_hotspot_replaycomp_test_ReplayCompilationLauncher_runInLibgraal", include = LibGraalReplayLauncherEnabled.class)
    private static int replayCompilation(JNIEnv jniEnv,
                    PointerBase jclass,
                    @IsolateThreadContext long isolateThread,
                    long argBuffer) {
        try (JNIMethodScope scope = new JNIMethodScope("replayCompilation", jniEnv)) {
            String argString = CTypeConversion.utf8ToJavaString(Word.pointer(argBuffer));
            String[] args;
            if (argString.isEmpty()) {
                args = new String[0];
            } else {
                args = argString.split("\n");
            }
            return ReplayCompilationRunner.run(args, TTY.out().out()).getStatus();
        } catch (Throwable t) {
            JNIExceptionWrapper.throwInHotSpot(jniEnv, t);
            return ReplayCompilationRunner.ExitStatus.Failure.getStatus();
        } finally {
            LibGraalSupportImpl.doReferenceHandling();
        }
    }

    /**
     * Controls whether the replay launcher entry point should be included in libgraal.
     */
    private static final class LibGraalReplayLauncherEnabled implements BooleanSupplier {
        @Override
        public boolean getAsBoolean() {
            return new LibGraalFeature.IsEnabled().getAsBoolean() && ReplayCompilationSupport.ENABLE_REPLAY_LAUNCHER;
        }
    }
}
