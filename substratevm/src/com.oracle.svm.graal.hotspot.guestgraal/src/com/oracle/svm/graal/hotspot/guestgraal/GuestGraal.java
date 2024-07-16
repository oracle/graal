/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.graal.hotspot.guestgraal;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.invoke.MethodHandle;
import java.lang.management.ManagementFactory;
import java.util.Arrays;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

import jdk.graal.compiler.debug.GraalError;
import org.graalvm.jniutils.JNI;
import org.graalvm.jniutils.JNI.JByteArray;
import org.graalvm.jniutils.JNI.JClass;
import org.graalvm.jniutils.JNI.JNIEnv;
import org.graalvm.jniutils.JNI.JObject;
import org.graalvm.jniutils.JNI.JObjectArray;
import org.graalvm.jniutils.JNI.JString;
import org.graalvm.jniutils.JNIMethodScope;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Isolate;
import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.nativeimage.c.function.CEntryPoint.Builtin;
import org.graalvm.nativeimage.c.function.CEntryPoint.IsolateContext;
import org.graalvm.nativeimage.c.type.CTypeConversion;
import org.graalvm.nativeimage.impl.IsolateSupport;
import org.graalvm.word.PointerBase;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.heap.Heap;
import com.sun.management.ThreadMXBean;

import jdk.internal.misc.Unsafe;

/**
 * Encapsulates {@link CEntryPoint} implementations as well as method handles for invoking guest
 * Graal and JVMCI functionality via {@link MethodHandle}s. The method handles are only invoked in
 * static methods which allows Native Image to fold them to direct calls to the method handle
 * targets.
 */
final class GuestGraal {

    private final MethodHandle getJNIEnv;
    private final MethodHandle getSavedProperty;
    private final MethodHandle ttyPrintf;
    private final MethodHandle compileMethod;

    /**
     * Returns the {@link GuestGraal} instance registered in the {@link ImageSingletons}.
     */
    private static GuestGraal singleton() {
        return ImageSingletons.lookup(GuestGraal.class);
    }

    GuestGraal(Map<String, MethodHandle> handles) {
        this.getJNIEnv = handles.get("getJNIEnv");
        this.getSavedProperty = handles.get("getSavedProperty");
        this.ttyPrintf = handles.get("ttyPrintf");
        this.compileMethod = handles.get("compileMethod");
    }

    /**
     * Calls {@code jdk.graal.compiler.hotspot.guestgraal.RunTime#getJNIEnv()}.
     */
    static JNI.JNIEnv getJNIEnv() {
        try {
            long raw = (long) singleton().getJNIEnv.invoke();
            return WordFactory.unsigned(raw);
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Throwable e) {
            throw new GraalError(e);
        }
    }

    /**
     * Calls {@code jdk.graal.compiler.serviceprovider.GraalServices#getSavedProperty(String)}.
     */
    static String getSavedProperty(String name) {
        try {
            return (String) singleton().getSavedProperty.invoke(name);
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Throwable e) {
            throw new GraalError(e);
        }
    }

    /**
     * Calls {@code jdk.graal.compiler.debug.TTY#printf(String, Object...)}.
     */
    static void ttyPrintf(String format, Object... args) {
        try {
            singleton().ttyPrintf.invoke(format, args);
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Throwable e) {
            throw new GraalError(e);
        }
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
     *            struct {
     *                int   length;
     *                byte  data[length]; // Bytes from a stack trace printed to a ByteArrayOutputStream.
     *            }
     *            </pre>
     *
     *            where {@code length} truncated to {@code stackTraceCapacity - 4} if necessary
     *
     * @param stackTraceCapacity the size of the stack trace buffer
     * @param timeAndMemBufferAddress 16-byte native buffer to store result of time and memory
     *            measurements of the compilation
     * @param profilePathBufferAddress native buffer containing a 0-terminated C string representing
     *            {@code Options#LoadProfiles} path.
     * @return a handle to a {@code InstalledCode} in HotSpot's heap or 0 if compilation failed
     */
    @SuppressWarnings({"unused", "try"})
    @CEntryPoint(name = "Java_jdk_graal_compiler_hotspot_test_LibGraalCompilationDriver_compileMethodInLibgraal", include = GuestGraalFeature.IsEnabled.class)
    private static long compileMethod(JNIEnv jniEnv,
                    PointerBase jclass,
                    @CEntryPoint.IsolateThreadContext long isolateThread,
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
            if (methodHandle == 0L) {
                return 0L;
            }
            String profileLoadPath;
            if (profilePathBufferAddress > 0) {
                profileLoadPath = CTypeConversion.toJavaString(WordFactory.pointer(profilePathBufferAddress));
            } else {
                profileLoadPath = null;
            }
            BiConsumer<Long, Long> timeAndMemConsumer;
            Supplier<Long> currentThreadAllocatedBytes;
            if (timeAndMemBufferAddress != 0) {
                timeAndMemConsumer = (timeSpent, bytesAllocated) -> {
                    Unsafe.getUnsafe().putLong(timeAndMemBufferAddress, bytesAllocated);
                    Unsafe.getUnsafe().putLong(timeAndMemBufferAddress + 8, timeSpent);
                };
                ThreadMXBean threadMXBean = (ThreadMXBean) ManagementFactory.getThreadMXBean();
                currentThreadAllocatedBytes = () -> threadMXBean.getCurrentThreadAllocatedBytes();
            } else {
                timeAndMemConsumer = null;
                currentThreadAllocatedBytes = null;
            }

            return (long) singleton().compileMethod.invoke(methodHandle, useProfilingInfo,
                            installAsDefault, printMetrics, eagerResolving,
                            optionsAddress, optionsSize, optionsHash,
                            profileLoadPath, timeAndMemConsumer, currentThreadAllocatedBytes);
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
            doReferenceHandling();
        }
    }

    /**
     * Since reference handling is synchronous in libgraal, explicitly perform it here and then run
     * any code which is expecting to process a reference queue to let it clean up.
     */
    static void doReferenceHandling() {
        Heap.getHeap().doReferenceHandling();
        synchronized (GuestGraalJVMCISubstitutions.Target_jdk_vm_ci_hotspot_Cleaner.class) {
            GuestGraalJVMCISubstitutions.Target_jdk_vm_ci_hotspot_Cleaner.clean();
        }
    }
}

final class GuestGraalLibGraalScope {

    @CEntryPoint(name = "Java_com_oracle_truffle_runtime_hotspot_libgraal_LibGraalScope_getIsolateThreadIn", builtin = Builtin.GET_CURRENT_THREAD)
    private static native IsolateThread getIsolateThreadIn(PointerBase env, PointerBase hsClazz, @IsolateContext Isolate isolate);

    @CEntryPoint(name = "Java_com_oracle_truffle_runtime_hotspot_libgraal_LibGraalScope_attachThreadTo", builtin = Builtin.ATTACH_THREAD)
    static native long attachThreadTo(PointerBase env, PointerBase hsClazz, @IsolateContext long isolate);

    @CEntryPoint(name = "Java_com_oracle_truffle_runtime_hotspot_libgraal_LibGraalScope_detachThreadFrom", builtin = Builtin.DETACH_THREAD)
    static native void detachThreadFrom(PointerBase env, PointerBase hsClazz, @CEntryPoint.IsolateThreadContext long isolateThread);

    @CEntryPoint(name = "Java_com_oracle_truffle_runtime_hotspot_libgraal_LibGraalScope_getIsolateId")
    @SuppressWarnings("unused")
    public static long getIsolateId(PointerBase env, PointerBase jclass, @CEntryPoint.IsolateThreadContext long isolateThreadId) {
        return ImageSingletons.lookup(IsolateSupport.class).getIsolateID();
    }
}

final class GuestGraalTruffleToLibGraalEntryPoints {
    @SuppressWarnings({"unused", "try"})
    @CEntryPoint(name = "Java_com_oracle_truffle_runtime_hotspot_libgraal_TruffleToLibGraalCalls_initializeIsolate")
    public static void initializeIsolate(JNIEnv env, JClass hsClazz, @CEntryPoint.IsolateThreadContext long isolateThreadId, JClass runtimeClass) {
    }

    @SuppressWarnings({"unused", "try"})
    @CEntryPoint(name = "Java_com_oracle_truffle_runtime_hotspot_libgraal_TruffleToLibGraalCalls_registerRuntime")
    public static boolean registerRuntime(JNIEnv env, JClass hsClazz, @CEntryPoint.IsolateThreadContext long isolateThreadId, JObject truffleRuntime) {
        return false;
    }

    @SuppressWarnings({"unused", "try"})
    @CEntryPoint(name = "Java_com_oracle_truffle_runtime_hotspot_libgraal_TruffleToLibGraalCalls_initializeRuntime")
    public static long initializeRuntime(JNIEnv env, JClass hsClazz, @CEntryPoint.IsolateThreadContext long isolateThreadId,
                    JObject truffleRuntime, JClass hsClassLoaderDelegate) {
        return 0L;
    }

    @SuppressWarnings({"unused", "try"})
    @CEntryPoint(name = "Java_com_oracle_truffle_runtime_hotspot_libgraal_TruffleToLibGraalCalls_newCompiler")
    public static long newCompiler(JNIEnv env, JClass hsClazz, @CEntryPoint.IsolateThreadContext long isolateThreadId, long truffleRuntimeHandle) {
        return 0;
    }

    @SuppressWarnings("unused")
    @CEntryPoint(name = "Java_com_oracle_truffle_runtime_hotspot_libgraal_TruffleToLibGraalCalls_initializeCompiler")
    public static void initializeCompiler(JNIEnv env, JClass hsClazz, @CEntryPoint.IsolateThreadContext long isolateThreadId, long compilerHandle, JObject hsCompilable,
                    boolean firstInitialization) {
    }

    @SuppressWarnings({"unused", "try"})
    @CEntryPoint(name = "Java_com_oracle_truffle_runtime_hotspot_libgraal_TruffleToLibGraalCalls_getCompilerConfigurationFactoryName")
    public static JString getCompilerConfigurationFactoryName(JNIEnv env, JClass hsClazz, @CEntryPoint.IsolateThreadContext long isolateThreadId, long truffleRuntimeHandle) {
        return WordFactory.nullPointer();
    }

    @SuppressWarnings({"unused", "try"})
    @CEntryPoint(name = "Java_com_oracle_truffle_runtime_hotspot_libgraal_TruffleToLibGraalCalls_doCompile")
    public static void doCompile(JNIEnv env,
                    JClass hsClazz,
                    @CEntryPoint.IsolateThreadContext long isolateThreadId,
                    long compilerHandle,
                    JObject hsTask,
                    JObject hsCompilable,
                    JObject hsListener) {
    }

    @SuppressWarnings({"unused", "try"})
    @CEntryPoint(name = "Java_com_oracle_truffle_runtime_hotspot_libgraal_TruffleToLibGraalCalls_shutdown")
    public static void shutdown(JNIEnv env, JClass hsClazz, @CEntryPoint.IsolateThreadContext long isolateThreadId, long handle) {
    }

    @SuppressWarnings({"unused", "try"})
    @CEntryPoint(name = "Java_com_oracle_truffle_runtime_hotspot_libgraal_TruffleToLibGraalCalls_installTruffleCallBoundaryMethod")
    public static void installTruffleCallBoundaryMethod(JNIEnv env, JClass hsClazz, @CEntryPoint.IsolateThreadContext long isolateThreadId, long handle, long methodHandle) {
    }

    @SuppressWarnings({"unused", "try"})
    @CEntryPoint(name = "Java_com_oracle_truffle_runtime_hotspot_libgraal_TruffleToLibGraalCalls_installTruffleReservedOopMethod")
    public static void installTruffleReservedOopMethod(JNIEnv env, JClass hsClazz, @CEntryPoint.IsolateThreadContext long isolateThreadId, long handle, long methodHandle) {
    }

    @SuppressWarnings("unused")
    @CEntryPoint(name = "Java_com_oracle_truffle_runtime_hotspot_libgraal_TruffleToLibGraalCalls_pendingTransferToInterpreterOffset")
    public static int pendingTransferToInterpreterOffset(JNIEnv env, JClass hsClazz, @CEntryPoint.IsolateThreadContext long isolateThreadId, long handle, JObject hsCompilable) {
        return 0;
    }

    @CEntryPoint(name = "Java_com_oracle_truffle_runtime_hotspot_libgraal_TruffleToLibGraalCalls_getSuppliedString")
    @SuppressWarnings({"unused", "unchecked", "try"})
    public static JString getString(JNIEnv env, JClass hsClazz, @CEntryPoint.IsolateThreadContext long isolateThreadId, long handle) {
        return WordFactory.nullPointer();
    }

    @CEntryPoint(name = "Java_com_oracle_truffle_runtime_hotspot_libgraal_TruffleToLibGraalCalls_getNodeCount")
    @SuppressWarnings({"unused", "try"})
    public static int getNodeCount(JNIEnv env, JClass hsClazz, @CEntryPoint.IsolateThreadContext long isolateThreadId, long handle) {
        return 0;
    }

    @CEntryPoint(name = "Java_com_oracle_truffle_runtime_hotspot_libgraal_TruffleToLibGraalCalls_getNodeTypes")
    @SuppressWarnings({"unused", "try"})
    public static JObjectArray getNodeTypes(JNIEnv env, JClass hsClazz, @CEntryPoint.IsolateThreadContext long isolateThreadId, long handle, boolean simpleNames) {
        return WordFactory.nullPointer();
    }

    @CEntryPoint(name = "Java_com_oracle_truffle_runtime_hotspot_libgraal_TruffleToLibGraalCalls_getTargetCodeSize")
    @SuppressWarnings({"unused", "try"})
    public static int getTargetCodeSize(JNIEnv env, JClass hsClazz, @CEntryPoint.IsolateThreadContext long isolateThreadId, long handle) {
        return 0;
    }

    @CEntryPoint(name = "Java_com_oracle_truffle_runtime_hotspot_libgraal_TruffleToLibGraalCalls_getTotalFrameSize")
    @SuppressWarnings({"unused", "try"})
    public static int getTotalFrameSize(JNIEnv env, JClass hsClazz, @CEntryPoint.IsolateThreadContext long isolateThreadId, long handle) {
        return 0;
    }

    @CEntryPoint(name = "Java_com_oracle_truffle_runtime_hotspot_libgraal_TruffleToLibGraalCalls_getExceptionHandlersCount")
    @SuppressWarnings({"unused", "try"})
    public static int getExceptionHandlersCount(JNIEnv env, JClass hsClazz, @CEntryPoint.IsolateThreadContext long isolateThreadId, long handle) {
        return 0;
    }

    @CEntryPoint(name = "Java_com_oracle_truffle_runtime_hotspot_libgraal_TruffleToLibGraalCalls_getInfopointsCount")
    @SuppressWarnings({"unused", "try"})
    public static int getInfopointsCount(JNIEnv env, JClass hsClazz, @CEntryPoint.IsolateThreadContext long isolateThreadId, long handle) {
        return 0;
    }

    @CEntryPoint(name = "Java_com_oracle_truffle_runtime_hotspot_libgraal_TruffleToLibGraalCalls_getInfopoints")
    @SuppressWarnings({"unused", "try"})
    public static JObjectArray getInfopoints(JNIEnv env, JClass hsClazz, @CEntryPoint.IsolateThreadContext long isolateThreadId, long handle) {
        return WordFactory.nullPointer();
    }

    @CEntryPoint(name = "Java_com_oracle_truffle_runtime_hotspot_libgraal_TruffleToLibGraalCalls_listCompilerOptions")
    @SuppressWarnings({"unused", "try"})
    public static JByteArray listCompilerOptions(JNIEnv env, JClass hsClazz, @CEntryPoint.IsolateThreadContext long isolateThreadId) {
        return WordFactory.nullPointer();
    }

    @CEntryPoint(name = "Java_com_oracle_truffle_runtime_hotspot_libgraal_TruffleToLibGraalCalls_compilerOptionExists")
    @SuppressWarnings({"unused", "try"})
    public static boolean existsCompilerOption(JNIEnv env, JClass hsClazz, @CEntryPoint.IsolateThreadContext long isolateThreadId, JString optionName) {
        return false;
    }

    @CEntryPoint(name = "Java_com_oracle_truffle_runtime_hotspot_libgraal_TruffleToLibGraalCalls_validateCompilerOption")
    @SuppressWarnings({"unused", "try"})
    public static JString validateCompilerOption(JNIEnv env, JClass hsClazz, @CEntryPoint.IsolateThreadContext long isolateThreadId, JString optionName, JString optionValue) {
        return WordFactory.nullPointer();
    }

    @CEntryPoint(name = "Java_com_oracle_truffle_runtime_hotspot_libgraal_TruffleToLibGraalCalls_getMarksCount")
    @SuppressWarnings({"unused", "try"})
    public static int getMarksCount(JNIEnv env, JClass hsClazz, @CEntryPoint.IsolateThreadContext long isolateThreadId, long handle) {
        return 0;
    }

    @CEntryPoint(name = "Java_com_oracle_truffle_runtime_hotspot_libgraal_TruffleToLibGraalCalls_getDataPatchesCount")
    @SuppressWarnings({"unused", "try"})
    public static int getDataPatchesCount(JNIEnv env, JClass hsClazz, @CEntryPoint.IsolateThreadContext long isolateThreadId, long handle) {
        return 0;
    }

    @CEntryPoint(name = "Java_com_oracle_truffle_runtime_hotspot_libgraal_TruffleToLibGraalCalls_purgePartialEvaluationCaches")
    @SuppressWarnings({"unused", "try"})
    public static void purgePartialEvaluationCaches(JNIEnv env, JClass hsClass, @CEntryPoint.IsolateThreadContext long isolateThreadId, long compilerHandle) {
    }

    @CEntryPoint(name = "Java_com_oracle_truffle_runtime_hotspot_libgraal_TruffleToLibGraalCalls_getCompilerVersion")
    @SuppressWarnings({"unused", "try"})
    public static JString getCompilerVersion(JNIEnv env, JClass hsClass, @CEntryPoint.IsolateThreadContext long isolateThreadId) {
        return WordFactory.nullPointer();
    }
}
