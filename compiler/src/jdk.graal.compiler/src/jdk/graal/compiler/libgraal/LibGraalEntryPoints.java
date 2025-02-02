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
package com.oracle.svm.graal.hotspot.libgraal;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.graalvm.collections.EconomicMap;
import org.graalvm.jniutils.HSObject;
import org.graalvm.jniutils.JNI;
import org.graalvm.jniutils.JNI.JByteArray;
import org.graalvm.jniutils.JNI.JClass;
import org.graalvm.jniutils.JNI.JNIEnv;
import org.graalvm.jniutils.JNI.JObject;
import org.graalvm.jniutils.JNI.JObjectArray;
import org.graalvm.jniutils.JNI.JString;
import org.graalvm.jniutils.JNIExceptionWrapper;
import org.graalvm.jniutils.JNIMethodScope;
import org.graalvm.jniutils.JNIUtil;
import org.graalvm.nativebridge.BinaryOutput;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Isolate;
import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.ObjectHandles;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.nativeimage.c.function.CEntryPoint.Builtin;
import org.graalvm.nativeimage.c.function.CEntryPoint.IsolateContext;
import org.graalvm.nativeimage.c.function.CEntryPoint.IsolateThreadContext;
import org.graalvm.nativeimage.c.type.CLongPointer;
import org.graalvm.nativeimage.c.type.CTypeConversion;
import org.graalvm.nativeimage.impl.IsolateSupport;
import org.graalvm.word.PointerBase;

import com.oracle.svm.core.heap.Heap;
import com.oracle.svm.core.heap.UnknownObjectField;
import com.oracle.svm.core.option.RuntimeOptionValues;
import com.oracle.svm.core.option.XOptions;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.graal.hotspot.LibGraalJNIMethodScope;
import com.oracle.svm.util.ClassUtil;
import com.oracle.truffle.compiler.TruffleCompilerOptionDescriptor;
import com.oracle.truffle.compiler.hotspot.libgraal.TruffleToLibGraal;
import com.oracle.truffle.compiler.hotspot.libgraal.TruffleToLibGraal.Id;
import com.sun.management.ThreadMXBean;

import jdk.graal.compiler.hotspot.libgraal.BuildTime;
import jdk.graal.compiler.hotspot.libgraal.RunTime;
import jdk.graal.compiler.options.OptionDescriptor;
import jdk.graal.compiler.options.OptionDescriptors;
import jdk.graal.compiler.options.OptionDescriptorsMap;
import jdk.graal.compiler.options.OptionKey;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.options.OptionsParser;
import jdk.graal.compiler.word.Word;
import jdk.internal.misc.Unsafe;

/**
 * Encapsulates {@link CEntryPoint} implementations as well as method handles for invoking LibGraal
 * and JVMCI functionality via {@link MethodHandle}s. The method handles (initialized by
 * {@link BuildTime#getRuntimeHandles()}) are only invoked in static methods which allows Native
 * Image to fold them to direct calls to the method handle targets.
 */
final class LibGraalEntryPoints {

    private final MethodHandle getJNIEnv;
    private final MethodHandle getSavedProperty;
    private final MethodHandle ttyPrintf;
    private final MethodHandle compileMethod;
    private final MethodHandle hashConstantOopFields;
    private final MethodHandle attachCurrentThread;
    private final MethodHandle detachCurrentThread;

    /**
     * Returns the {@link LibGraalEntryPoints} instance registered in the {@link ImageSingletons}.
     */
    private static LibGraalEntryPoints singleton() {
        return ImageSingletons.lookup(LibGraalEntryPoints.class);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    LibGraalEntryPoints(Map<String, MethodHandle> handles) {
        this.getJNIEnv = handles.get("getJNIEnv");
        this.getSavedProperty = handles.get("getSavedProperty");
        this.ttyPrintf = handles.get("ttyPrintf");
        this.compileMethod = handles.get("compileMethod");
        this.hashConstantOopFields = handles.get("hashConstantOopFields");
        this.attachCurrentThread = handles.get("attachCurrentThread");
        this.detachCurrentThread = handles.get("detachCurrentThread");
    }

    /**
     * Calls {@code jdk.graal.compiler.hotspot.libgraal.RunTime#getJNIEnv()}.
     */
    static JNI.JNIEnv getJNIEnv() {
        try {
            long raw = (long) singleton().getJNIEnv.invoke();
            return Word.unsigned(raw);
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Throwable e) {
            throw VMError.shouldNotReachHere(e);
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
            throw VMError.shouldNotReachHere(e);
        }
    }

    /**
     * Calls {@link RunTime#attachCurrentThread}.
     */
    static boolean attachCurrentThread(boolean daemon, long[] isolate) {
        try {
            return (boolean) singleton().attachCurrentThread.invoke(daemon, isolate);
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Throwable e) {
            throw VMError.shouldNotReachHere(e);
        }
    }

    /**
     * Calls {@link RunTime#detachCurrentThread}.
     */
    static boolean detachCurrentThread(boolean release) {
        try {
            return (boolean) singleton().detachCurrentThread.invoke(release);
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Throwable e) {
            throw VMError.shouldNotReachHere(e);
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
            throw VMError.shouldNotReachHere(e);
        }
    }

    /**
     * The implementation of
     * {@code jdk.graal.compiler.hotspot.test.LibGraalCompilationDriver#compileMethodInLibgraal}.
     * Calls {@link RunTime#compileMethod}.
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
            String profileLoadPath;
            if (profilePathBufferAddress > 0) {
                profileLoadPath = CTypeConversion.toJavaString(Word.pointer(profilePathBufferAddress));
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
            Runnable doReferenceHandling = LibGraalEntryPoints::doReferenceHandling;
            return (long) singleton().hashConstantOopFields.invoke(typeHandle, useScope, iterations, oopsPerIteration, verbose, doReferenceHandling);
        } catch (Throwable t) {
            JNIExceptionWrapper.throwInHotSpot(jniEnv, t);
            return 0;
        }
    }

    /**
     * Since reference handling is synchronous in libgraal, explicitly perform it here and then run
     * any code which is expecting to process a reference queue to let it clean up.
     */
    static void doReferenceHandling() {
        Heap.getHeap().doReferenceHandling();
        synchronized (LibGraalJVMCISubstitutions.Target_jdk_vm_ci_hotspot_Cleaner.class) {
            LibGraalJVMCISubstitutions.Target_jdk_vm_ci_hotspot_Cleaner.clean();
        }
    }

    /**
     * Options configuring the VM in which libgraal is running.
     */
    @UnknownObjectField(fullyQualifiedTypes = "org.graalvm.collections.EconomicMapImpl") //
    static EconomicMap<String, OptionDescriptor> vmOptionDescriptors = EconomicMap.create();

    static void initializeOptions(Map<String, String> settings) {
        EconomicMap<String, String> nonXSettings = processXOptions(settings);
        EconomicMap<OptionKey<?>, Object> vmOptionValues = OptionValues.newOptionMap();
        Iterable<OptionDescriptors> vmOptionLoader = List.of(new OptionDescriptorsMap(vmOptionDescriptors));
        OptionsParser.parseOptions(nonXSettings, vmOptionValues, vmOptionLoader);
        RuntimeOptionValues.singleton().update(vmOptionValues);
    }

    /**
     * Extracts and processes the {@link XOptions} in {@code settings}.
     *
     * @return the entries in {@code settings} that do not correspond to {@link XOptions}
     */
    private static EconomicMap<String, String> processXOptions(Map<String, String> settings) {
        EconomicMap<String, String> nonXSettings = EconomicMap.create(settings.size());
        for (var e : settings.entrySet()) {
            String key = e.getKey();
            String value = e.getValue();
            if (key.startsWith("X") && value.isEmpty()) {
                String xarg = key.substring(1);
                if (XOptions.setOption(xarg)) {
                    continue;
                }
            }
            nonXSettings.put(key, value);
        }
        return nonXSettings;
    }

    static void printOptions(PrintStream out, String prefix) {
        RuntimeOptionValues vmOptions = RuntimeOptionValues.singleton();
        Iterable<OptionDescriptors> vmOptionLoader = Collections.singletonList(new OptionDescriptorsMap(vmOptionDescriptors));
        vmOptions.printHelp(vmOptionLoader, out, prefix, true);
    }
}

final class LibGraalScopeEntryPoints {

    @CEntryPoint(name = "Java_com_oracle_truffle_runtime_hotspot_libgraal_LibGraalScope_getIsolateThreadIn", builtin = Builtin.GET_CURRENT_THREAD)
    private static native IsolateThread getIsolateThreadIn(PointerBase env, PointerBase hsClazz, @IsolateContext Isolate isolate);

    @CEntryPoint(name = "Java_com_oracle_truffle_runtime_hotspot_libgraal_LibGraalScope_attachThreadTo", builtin = Builtin.ATTACH_THREAD)
    static native long attachThreadTo(PointerBase env, PointerBase hsClazz, @IsolateContext long isolate);

    @CEntryPoint(name = "Java_com_oracle_truffle_runtime_hotspot_libgraal_LibGraalScope_detachThreadFrom", builtin = Builtin.DETACH_THREAD)
    static native void detachThreadFrom(PointerBase env, PointerBase hsClazz, @IsolateThreadContext long isolateThreadAddress);

    @CEntryPoint(name = "Java_com_oracle_truffle_runtime_hotspot_libgraal_LibGraalScope_getIsolateId")
    @SuppressWarnings("unused")
    public static long getIsolateId(PointerBase env, PointerBase jclass, @IsolateThreadContext long isolateThreadAddress) {
        return ImageSingletons.lookup(IsolateSupport.class).getIsolateID();
    }
}

final class LibGraalTruffleToLibGraalEntryPoints {

    private static volatile int lastJavaPCOffset = -1;

    /*
     * Each of the following MethodHandle fields corresponds to a TruffleToLibGraal.Id value. The
     * naming convention requires that each field name match the method name returned by
     * TruffleToLibGraal.Id.getMethodName(). Additionally, the GraalEntryPoints method that the
     * MethodHandle references must also follow this naming convention. The null MethodHandle values
     * are overwritten reflectively in the constructor
     */
    private final MethodHandle initializeIsolate = null;
    private final MethodHandle registerRuntime = null;
    private final MethodHandle initializeRuntime = null;
    private final MethodHandle newCompiler = null;
    private final MethodHandle initializeCompiler = null;
    private final MethodHandle getCompilerConfigurationFactoryName = null;
    private final MethodHandle doCompile = null;
    private final MethodHandle shutdown = null;
    private final MethodHandle installTruffleCallBoundaryMethod = null;
    private final MethodHandle installTruffleReservedOopMethod = null;
    private final MethodHandle pendingTransferToInterpreterOffset = null;
    private final MethodHandle getSuppliedString = null;
    private final MethodHandle getNodeCount = null;
    private final MethodHandle getNodeTypes = null;
    private final MethodHandle getCompilationId = null;
    private final MethodHandle getTargetCodeSize = null;
    private final MethodHandle getTotalFrameSize = null;
    private final MethodHandle getExceptionHandlersCount = null;
    private final MethodHandle getInfopointsCount = null;
    private final MethodHandle getInfopoints = null;
    private final MethodHandle listCompilerOptions = null;
    private final MethodHandle compilerOptionExists = null;
    private final MethodHandle validateCompilerOption = null;
    private final MethodHandle getMarksCount = null;
    private final MethodHandle getDataPatchesCount = null;
    private final MethodHandle purgePartialEvaluationCaches = null;
    private final MethodHandle getCompilerVersion = null;

    private final MethodHandle getCurrentJavaThread;
    private final MethodHandle getLastJavaPCOffset;

    @Platforms(Platform.HOSTED_ONLY.class)
    LibGraalTruffleToLibGraalEntryPoints(Lookup libgraalLookup, Class<?> graalEntryPoints) {
        try {
            Map<String, Method> graalMethodByName = Arrays.stream(graalEntryPoints.getDeclaredMethods()).//
                            filter((m) -> Modifier.isStatic(m.getModifiers()) && Modifier.isPublic(m.getModifiers())).//
                            collect(Collectors.toMap(Method::getName, (m) -> m));
            /*
             * hostMethodByName is used solely to ensure that all delegation methods have been
             * implemented.
             */
            Set<String> hostMethodByName = Arrays.stream(getClass().getDeclaredMethods()).//
                            filter((m) -> Modifier.isStatic(m.getModifiers()) && m.getAnnotation(TruffleToLibGraal.class) != null).//
                            map(Method::getName).//
                            collect(Collectors.toSet());
            for (Id id : Id.values()) {
                String methodName = id.getMethodName();
                Method method = graalMethodByName.get(methodName);
                if (method == null) {
                    throw VMError.shouldNotReachHere("Missing libgraal entry method %s.%s corresponding to TruffleToLibGraal.Id.%s. " +
                                    "To resolve this, add `public static <return_type> %s(<arguments>)` in %s, where the <return_type> and <arguments> correspond to TruffleToLibGraalCalls.%s.",
                                    ClassUtil.getUnqualifiedName(graalEntryPoints), methodName, id, methodName, graalEntryPoints.getName(), methodName);
                }
                if (!hostMethodByName.contains(methodName)) {
                    throw VMError.shouldNotReachHere("Missing C entry point method %s.%s corresponding to TruffleToLibGraal.Id.%s. " +
                                    "To resolve this, add `@CEntryPoint(\"Java_com_oracle_truffle_runtime_hotspot_libgraal_TruffleToLibGraalCalls_%s\") " +
                                    "@TruffleToLibGraal(Id.%s) static <return_type> %s(JNIEnv env, JClass jclass, @IsolateThreadContext long isolateThreadAddress, " +
                                    "<arguments>)` in %s, where the <return_type> and <arguments> correspond to TruffleToLibGraalCalls.%s. " +
                                    "Use a MethodHandle to delegate to %s.%s.",
                                    ClassUtil.getUnqualifiedName(getClass()), methodName, id, methodName, id, methodName, getClass().getName(), methodName,
                                    graalEntryPoints.getName(), methodName);
                }
                Field methodHandleField;
                try {
                    methodHandleField = getClass().getDeclaredField(methodName);
                } catch (NoSuchFieldException nsf) {
                    throw VMError.shouldNotReachHere("Missing field %s.%s corresponding to TruffleToLibGraal.Id.%s. " +
                                    "To resolve this, add `private final MethodHandle %s = null;` to %s.",
                                    ClassUtil.getUnqualifiedName(getClass()), methodName, id, methodName, getClass().getName());
                }
                methodHandleField.setAccessible(true);
                methodHandleField.set(this, libgraalLookup.unreflect(method));
            }
            Method m = graalMethodByName.get("getCurrentJavaThread");
            if (m == null) {
                throw VMError.shouldNotReachHere("Missing libgraal entry method %s.getCurrentJavaThread.", ClassUtil.getUnqualifiedName(graalEntryPoints));
            }
            getCurrentJavaThread = libgraalLookup.unreflect(m);
            m = graalMethodByName.get("getLastJavaPCOffset");
            if (m == null) {
                throw VMError.shouldNotReachHere("Missing libgraal entry method %s.getLastJavaPCOffset.", ClassUtil.getUnqualifiedName(graalEntryPoints));
            }
            getLastJavaPCOffset = libgraalLookup.unreflect(m);
        } catch (ReflectiveOperationException e) {
            throw VMError.shouldNotReachHere(e);
        }
    }

    private static JNIMethodScope openScope(Enum<?> id, JNIEnv env) throws Throwable {
        Objects.requireNonNull(id, "Id must be non null.");
        String scopeName = ClassUtil.getUnqualifiedName(LibGraalTruffleToLibGraalEntryPoints.class) + "::" + id;
        int offset = lastJavaPCOffset;
        if (offset == -1) {
            offset = (int) singleton().getLastJavaPCOffset.invoke();
            lastJavaPCOffset = offset;
        }
        CLongPointer currentThreadLastJavaPCOffset = (CLongPointer) Word.unsigned((long) singleton().getCurrentJavaThread.invoke()).add(offset);
        PointerBase javaFrameAnchor = Word.pointer(currentThreadLastJavaPCOffset.read());
        return LibGraalJNIMethodScope.open(scopeName, env, javaFrameAnchor.isNonNull());
    }

    private static LibGraalTruffleToLibGraalEntryPoints singleton() {
        return ImageSingletons.lookup(LibGraalTruffleToLibGraalEntryPoints.class);
    }

    @SuppressWarnings({"unused", "try"})
    @CEntryPoint(name = "Java_com_oracle_truffle_runtime_hotspot_libgraal_TruffleToLibGraalCalls_initializeIsolate")
    @TruffleToLibGraal(Id.InitializeIsolate)
    public static void initializeIsolate(JNIEnv env, JClass hsClazz, @IsolateThreadContext long isolateThreadAddress, JClass runtimeClass) {
        try (JNIMethodScope s = openScope(Id.InitializeIsolate, env)) {
            TruffleFromLibGraalStartPoints.initializeJNI(runtimeClass);
            singleton().initializeIsolate.invoke();
        } catch (Throwable t) {
            JNIExceptionWrapper.throwInHotSpot(env, t);
        }
    }

    @SuppressWarnings({"unused", "try"})
    @CEntryPoint(name = "Java_com_oracle_truffle_runtime_hotspot_libgraal_TruffleToLibGraalCalls_registerRuntime")
    @TruffleToLibGraal(Id.RegisterRuntime)
    public static boolean registerRuntime(JNIEnv env, JClass hsClazz, @IsolateThreadContext long isolateThreadAddress, JObject truffleRuntime) {
        try (JNIMethodScope s = openScope(Id.RegisterRuntime, env)) {
            return (boolean) singleton().registerRuntime.invoke(JNIUtil.NewWeakGlobalRef(env, truffleRuntime, "TruffleCompilerRuntime").rawValue());
        } catch (Throwable t) {
            JNIExceptionWrapper.throwInHotSpot(env, t);
            return false;
        }
    }

    @SuppressWarnings({"unused", "try"})
    @CEntryPoint(name = "Java_com_oracle_truffle_runtime_hotspot_libgraal_TruffleToLibGraalCalls_initializeRuntime")
    @TruffleToLibGraal(Id.InitializeRuntime)
    public static long initializeRuntime(JNIEnv env, JClass hsClazz, @IsolateThreadContext long isolateThreadAddress,
                    JObject truffleRuntime, JClass hsClassLoaderDelegate) {
        try (JNIMethodScope s = openScope(Id.InitializeRuntime, env)) {
            HSObject hsHandle = new HSObject(env, truffleRuntime, true, false);
            Object hsTruffleRuntime = singleton().initializeRuntime.invoke(hsHandle, hsClassLoaderDelegate.rawValue());
            return LibGraalObjectHandles.create(hsTruffleRuntime);
        } catch (Throwable t) {
            JNIExceptionWrapper.throwInHotSpot(env, t);
            return 0L;
        }
    }

    @SuppressWarnings({"unused", "try"})
    @CEntryPoint(name = "Java_com_oracle_truffle_runtime_hotspot_libgraal_TruffleToLibGraalCalls_newCompiler")
    @TruffleToLibGraal(Id.NewCompiler)
    public static long newCompiler(JNIEnv env, JClass hsClazz, @IsolateThreadContext long isolateThreadAddress, long truffleRuntimeHandle) {
        try (JNIMethodScope s = openScope(Id.NewCompiler, env)) {
            Object truffleRuntime = LibGraalObjectHandles.resolve(truffleRuntimeHandle, Object.class);
            Object compiler = singleton().newCompiler.invoke(truffleRuntime);
            return LibGraalObjectHandles.create(compiler);
        } catch (Throwable t) {
            JNIExceptionWrapper.throwInHotSpot(env, t);
            return 0;
        }
    }

    @SuppressWarnings("unused")
    @CEntryPoint(name = "Java_com_oracle_truffle_runtime_hotspot_libgraal_TruffleToLibGraalCalls_initializeCompiler")
    @TruffleToLibGraal(Id.InitializeRuntime)
    public static void initializeCompiler(JNIEnv env, JClass hsClazz, @IsolateThreadContext long isolateThreadAddress, long compilerHandle, JObject hsCompilable,
                    boolean firstInitialization) {
        try (JNIMethodScope scope = openScope(Id.InitializeCompiler, env)) {
            Object compiler = LibGraalObjectHandles.resolve(compilerHandle, Object.class);
            singleton().initializeCompiler.invoke(compiler, new HSObject(scope, hsCompilable), firstInitialization);
        } catch (Throwable t) {
            JNIExceptionWrapper.throwInHotSpot(env, t);
        }
    }

    @SuppressWarnings({"unused", "try"})
    @CEntryPoint(name = "Java_com_oracle_truffle_runtime_hotspot_libgraal_TruffleToLibGraalCalls_getCompilerConfigurationFactoryName")
    @TruffleToLibGraal(Id.GetCompilerConfigurationFactoryName)
    public static JString getCompilerConfigurationFactoryName(JNIEnv env, JClass hsClazz, @IsolateThreadContext long isolateThreadAddress, long truffleRuntimeHandle) {
        try {
            JNIMethodScope scope = openScope(Id.GetCompilerConfigurationFactoryName, env);
            try (JNIMethodScope s = scope) {
                String name = (String) singleton().getCompilerConfigurationFactoryName.invoke();
                scope.setObjectResult(JNIUtil.createHSString(env, name));
            }
            return scope.getObjectResult();
        } catch (Throwable t) {
            JNIExceptionWrapper.throwInHotSpot(env, t);
            return Word.nullPointer();
        }
    }

    @SuppressWarnings({"unused", "try"})
    @CEntryPoint(name = "Java_com_oracle_truffle_runtime_hotspot_libgraal_TruffleToLibGraalCalls_doCompile")
    @TruffleToLibGraal(Id.DoCompile)
    public static void doCompile(JNIEnv env,
                    JClass hsClazz,
                    @IsolateThreadContext long isolateThreadAddress,
                    long compilerHandle,
                    JObject hsTask,
                    JObject hsCompilable,
                    JObject hsListener) {
        try (JNIMethodScope scope = openScope(Id.DoCompile, env)) {
            Object compiler = LibGraalObjectHandles.resolve(compilerHandle, Object.class);
            Object taskHsHandle = hsTask.isNull() ? null : new HSObject(scope, hsTask);
            Object compilableHsHandle = new HSObject(scope, hsCompilable);
            Object listenerHsHandle = hsListener.isNull() ? null : new HSObject(scope, hsListener);
            try {
                singleton().doCompile.invoke(compiler, taskHsHandle, compilableHsHandle, listenerHsHandle);
            } finally {
                Heap.getHeap().doReferenceHandling();
                Heap.getHeap().getGC().collectionHint(true);
            }
        } catch (Throwable t) {
            JNIExceptionWrapper.throwInHotSpot(env, t);
        }
    }

    @SuppressWarnings({"unused", "try"})
    @CEntryPoint(name = "Java_com_oracle_truffle_runtime_hotspot_libgraal_TruffleToLibGraalCalls_shutdown")
    @TruffleToLibGraal(Id.Shutdown)
    public static void shutdown(JNIEnv env, JClass hsClazz, @IsolateThreadContext long isolateThreadAddress, long handle) {
        try (JNIMethodScope s = openScope(Id.Shutdown, env)) {
            Object compiler = LibGraalObjectHandles.resolve(handle, Object.class);
            singleton().shutdown.invoke(compiler);
        } catch (Throwable t) {
            JNIExceptionWrapper.throwInHotSpot(env, t);
        }
    }

    @SuppressWarnings({"unused", "try"})
    @CEntryPoint(name = "Java_com_oracle_truffle_runtime_hotspot_libgraal_TruffleToLibGraalCalls_installTruffleCallBoundaryMethod")
    @TruffleToLibGraal(Id.InstallTruffleCallBoundaryMethod)
    public static void installTruffleCallBoundaryMethod(JNIEnv env, JClass hsClazz, @IsolateThreadContext long isolateThreadAddress, long handle, long methodHandle) {
        try (JNIMethodScope s = openScope(Id.InstallTruffleCallBoundaryMethod, env)) {
            Object compiler = LibGraalObjectHandles.resolve(handle, Object.class);
            singleton().installTruffleCallBoundaryMethod.invoke(compiler, methodHandle);
        } catch (Throwable t) {
            JNIExceptionWrapper.throwInHotSpot(env, t);
        }
    }

    @SuppressWarnings({"unused", "try"})
    @CEntryPoint(name = "Java_com_oracle_truffle_runtime_hotspot_libgraal_TruffleToLibGraalCalls_installTruffleReservedOopMethod")
    @TruffleToLibGraal(Id.InstallTruffleReservedOopMethod)
    public static void installTruffleReservedOopMethod(JNIEnv env, JClass hsClazz, @IsolateThreadContext long isolateThreadAddress, long handle, long methodHandle) {
        try (JNIMethodScope s = openScope(Id.InstallTruffleReservedOopMethod, env)) {
            Object compiler = LibGraalObjectHandles.resolve(handle, Object.class);
            singleton().installTruffleReservedOopMethod.invoke(compiler, methodHandle);
        } catch (Throwable t) {
            JNIExceptionWrapper.throwInHotSpot(env, t);
        }
    }

    @SuppressWarnings("unused")
    @CEntryPoint(name = "Java_com_oracle_truffle_runtime_hotspot_libgraal_TruffleToLibGraalCalls_pendingTransferToInterpreterOffset")
    @TruffleToLibGraal(Id.PendingTransferToInterpreterOffset)
    public static int pendingTransferToInterpreterOffset(JNIEnv env, JClass hsClazz, @IsolateThreadContext long isolateThreadAddress, long handle, JObject hsCompilable) {
        try (JNIMethodScope scope = openScope(Id.PendingTransferToInterpreterOffset, env)) {
            Object compiler = LibGraalObjectHandles.resolve(handle, Object.class);
            return (int) singleton().pendingTransferToInterpreterOffset.invoke(compiler, new HSObject(scope, hsCompilable));
        } catch (Throwable t) {
            JNIExceptionWrapper.throwInHotSpot(env, t);
            return 0;
        }
    }

    @CEntryPoint(name = "Java_com_oracle_truffle_runtime_hotspot_libgraal_TruffleToLibGraalCalls_getSuppliedString")
    @SuppressWarnings({"unused", "try"})
    @TruffleToLibGraal(Id.GetSuppliedString)
    public static JString getSuppliedString(JNIEnv env, JClass hsClazz, @IsolateThreadContext long isolateThreadAddress, long handle) {
        try {
            JNIMethodScope scope = openScope(Id.GetSuppliedString, env);
            try (JNIMethodScope s = scope) {
                Object stringSupplier = LibGraalObjectHandles.resolve(handle, Object.class);
                if (stringSupplier != null) {
                    String stackTrace = (String) singleton().getSuppliedString.invoke(stringSupplier);
                    scope.setObjectResult(JNIUtil.createHSString(env, stackTrace));
                } else {
                    scope.setObjectResult(Word.nullPointer());
                }
            }
            return scope.getObjectResult();
        } catch (Throwable t) {
            JNIExceptionWrapper.throwInHotSpot(env, t);
            return Word.nullPointer();
        }
    }

    @CEntryPoint(name = "Java_com_oracle_truffle_runtime_hotspot_libgraal_TruffleToLibGraalCalls_getNodeCount")
    @SuppressWarnings({"unused", "try"})
    @TruffleToLibGraal(Id.GetNodeCount)
    public static int getNodeCount(JNIEnv env, JClass hsClazz, @IsolateThreadContext long isolateThreadAddress, long handle) {
        try (JNIMethodScope s = openScope(Id.GetNodeCount, env)) {
            Object graphInfo = LibGraalObjectHandles.resolve(handle, Object.class);
            return (int) singleton().getNodeCount.invoke(graphInfo);
        } catch (Throwable t) {
            JNIExceptionWrapper.throwInHotSpot(env, t);
            return 0;
        }
    }

    @CEntryPoint(name = "Java_com_oracle_truffle_runtime_hotspot_libgraal_TruffleToLibGraalCalls_getNodeTypes")
    @SuppressWarnings({"unused", "try"})
    @TruffleToLibGraal(Id.GetNodeTypes)
    public static JObjectArray getNodeTypes(JNIEnv env, JClass hsClazz, @IsolateThreadContext long isolateThreadAddress, long handle, boolean simpleNames) {
        try {
            JNIMethodScope scope = openScope(Id.GetNodeTypes, env);
            try (JNIMethodScope s = scope) {
                Object graphInfo = LibGraalObjectHandles.resolve(handle, Object.class);
                String[] nodeTypes = (String[]) singleton().getNodeTypes.invoke(graphInfo, simpleNames);
                JClass componentType = getStringClass(env);
                JObjectArray res = JNIUtil.NewObjectArray(env, nodeTypes.length, componentType, Word.nullPointer());
                for (int i = 0; i < nodeTypes.length; i++) {
                    JNIUtil.SetObjectArrayElement(env, res, i, JNIUtil.createHSString(env, nodeTypes[i]));
                }
                scope.setObjectResult(res);
            }
            return scope.getObjectResult();
        } catch (Throwable t) {
            JNIExceptionWrapper.throwInHotSpot(env, t);
            return Word.nullPointer();
        }
    }

    private static JClass getStringClass(JNIEnv env) {
        return JNIUtil.NewGlobalRef(env, JNIUtil.findClass(env, "java/lang/String"), "Class<java.lang.String>");
    }

    @CEntryPoint(name = "Java_com_oracle_truffle_runtime_hotspot_libgraal_TruffleToLibGraalCalls2_getCompilationId")
    @SuppressWarnings({"unused", "try"})
    @TruffleToLibGraal(Id.GetCompilationId)
    public static long getCompilationId(JNIEnv env, JClass hsClazz, @IsolateThreadContext long isolateThreadAddress, long handle) {
        try {
            JNIMethodScope scope = openScope(Id.GetCompilationId, env);
            try (JNIMethodScope s = scope) {
                Object compilationResultInfo = LibGraalObjectHandles.resolve(handle, Object.class);
                return (long) singleton().getCompilationId.invoke(compilationResultInfo);
            }
        } catch (Throwable t) {
            JNIExceptionWrapper.throwInHotSpot(env, t);
            return -1;
        }
    }

    @CEntryPoint(name = "Java_com_oracle_truffle_runtime_hotspot_libgraal_TruffleToLibGraalCalls_getTargetCodeSize")
    @SuppressWarnings({"unused", "try"})
    @TruffleToLibGraal(Id.GetTargetCodeSize)
    public static int getTargetCodeSize(JNIEnv env, JClass hsClazz, @IsolateThreadContext long isolateThreadAddress, long handle) {
        try (JNIMethodScope s = openScope(Id.GetTargetCodeSize, env)) {
            Object compilationResultInfo = LibGraalObjectHandles.resolve(handle, Object.class);
            return (int) singleton().getTargetCodeSize.invoke(compilationResultInfo);
        } catch (Throwable t) {
            JNIExceptionWrapper.throwInHotSpot(env, t);
            return 0;
        }
    }

    @CEntryPoint(name = "Java_com_oracle_truffle_runtime_hotspot_libgraal_TruffleToLibGraalCalls_getTotalFrameSize")
    @SuppressWarnings({"unused", "try"})
    @TruffleToLibGraal(Id.GetTotalFrameSize)
    public static int getTotalFrameSize(JNIEnv env, JClass hsClazz, @IsolateThreadContext long isolateThreadAddress, long handle) {
        try (JNIMethodScope s = openScope(Id.GetTotalFrameSize, env)) {
            Object compilationResultInfo = LibGraalObjectHandles.resolve(handle, Object.class);
            return (int) singleton().getTotalFrameSize.invoke(compilationResultInfo);
        } catch (Throwable t) {
            JNIExceptionWrapper.throwInHotSpot(env, t);
            return 0;
        }
    }

    @CEntryPoint(name = "Java_com_oracle_truffle_runtime_hotspot_libgraal_TruffleToLibGraalCalls_getExceptionHandlersCount")
    @SuppressWarnings({"unused", "try"})
    @TruffleToLibGraal(Id.GetExceptionHandlersCount)
    public static int getExceptionHandlersCount(JNIEnv env, JClass hsClazz, @IsolateThreadContext long isolateThreadAddress, long handle) {
        try (JNIMethodScope s = openScope(Id.GetExceptionHandlersCount, env)) {
            Object compilationResultInfo = LibGraalObjectHandles.resolve(handle, Object.class);
            return (int) singleton().getExceptionHandlersCount.invoke(compilationResultInfo);
        } catch (Throwable t) {
            JNIExceptionWrapper.throwInHotSpot(env, t);
            return 0;
        }
    }

    @CEntryPoint(name = "Java_com_oracle_truffle_runtime_hotspot_libgraal_TruffleToLibGraalCalls_getInfopointsCount")
    @SuppressWarnings({"unused", "try"})
    @TruffleToLibGraal(Id.GetInfopointsCount)
    public static int getInfopointsCount(JNIEnv env, JClass hsClazz, @IsolateThreadContext long isolateThreadAddress, long handle) {
        try (JNIMethodScope s = openScope(Id.GetInfopointsCount, env)) {
            Object compilationResultInfo = LibGraalObjectHandles.resolve(handle, Object.class);
            return (int) singleton().getInfopointsCount.invoke(compilationResultInfo);
        } catch (Throwable t) {
            JNIExceptionWrapper.throwInHotSpot(env, t);
            return 0;
        }
    }

    @CEntryPoint(name = "Java_com_oracle_truffle_runtime_hotspot_libgraal_TruffleToLibGraalCalls_getInfopoints")
    @SuppressWarnings({"unused", "try"})
    @TruffleToLibGraal(Id.GetInfopoints)
    public static JObjectArray getInfopoints(JNIEnv env, JClass hsClazz, @IsolateThreadContext long isolateThreadAddress, long handle) {
        try {
            JNIMethodScope scope = openScope(Id.GetInfopoints, env);
            try (JNIMethodScope s = scope) {
                Object compilationResultInfo = LibGraalObjectHandles.resolve(handle, Object.class);
                String[] infoPoints = (String[]) singleton().getInfopoints.invoke(compilationResultInfo);
                JClass componentType = getStringClass(env);
                JObjectArray res = JNIUtil.NewObjectArray(env, infoPoints.length, componentType, Word.nullPointer());
                for (int i = 0; i < infoPoints.length; i++) {
                    JNIUtil.SetObjectArrayElement(env, res, i, JNIUtil.createHSString(env, infoPoints[i]));
                }
                scope.setObjectResult(res);
            }
            return scope.getObjectResult();
        } catch (Throwable t) {
            JNIExceptionWrapper.throwInHotSpot(env, t);
            return Word.nullPointer();
        }
    }

    @CEntryPoint(name = "Java_com_oracle_truffle_runtime_hotspot_libgraal_TruffleToLibGraalCalls_getMarksCount")
    @SuppressWarnings({"unused", "try"})
    @TruffleToLibGraal(Id.GetMarksCount)
    public static int getMarksCount(JNIEnv env, JClass hsClazz, @IsolateThreadContext long isolateThreadAddress, long handle) {
        try (JNIMethodScope s = openScope(Id.GetMarksCount, env)) {
            Object compilationResultInfo = LibGraalObjectHandles.resolve(handle, Object.class);
            return (int) singleton().getMarksCount.invoke(compilationResultInfo);
        } catch (Throwable t) {
            JNIExceptionWrapper.throwInHotSpot(env, t);
            return 0;
        }
    }

    @CEntryPoint(name = "Java_com_oracle_truffle_runtime_hotspot_libgraal_TruffleToLibGraalCalls_getDataPatchesCount")
    @SuppressWarnings({"unused", "try"})
    @TruffleToLibGraal(Id.GetDataPatchesCount)
    public static int getDataPatchesCount(JNIEnv env, JClass hsClazz, @IsolateThreadContext long isolateThreadAddress, long handle) {
        try (JNIMethodScope s = openScope(Id.GetDataPatchesCount, env)) {
            Object compilationResultInfo = LibGraalObjectHandles.resolve(handle, Object.class);
            return (int) singleton().getDataPatchesCount.invoke(compilationResultInfo);
        } catch (Throwable t) {
            JNIExceptionWrapper.throwInHotSpot(env, t);
            return 0;
        }
    }

    @CEntryPoint(name = "Java_com_oracle_truffle_runtime_hotspot_libgraal_TruffleToLibGraalCalls_listCompilerOptions")
    @SuppressWarnings({"unused", "try"})
    @TruffleToLibGraal(Id.ListCompilerOptions)
    public static JByteArray listCompilerOptions(JNIEnv env, JClass hsClazz, @IsolateThreadContext long isolateThreadAddress) {
        try {
            JNIMethodScope scope = openScope(Id.ListCompilerOptions, env);
            try (JNIMethodScope s = scope) {
                Object[] options = (Object[]) singleton().listCompilerOptions.invoke();
                BinaryOutput.ByteArrayBinaryOutput out = BinaryOutput.create();
                out.writeInt(options.length);
                for (int i = 0; i < options.length; i++) {
                    TruffleCompilerOptionDescriptor descriptor = (TruffleCompilerOptionDescriptor) options[i];
                    out.writeUTF(descriptor.name());
                    out.writeInt(descriptor.type().ordinal());
                    out.writeBoolean(descriptor.deprecated());
                    out.writeUTF(descriptor.help());
                    out.writeUTF(descriptor.deprecationMessage());
                }
                JByteArray res = JNIUtil.createHSArray(env, out.getArray());
                scope.setObjectResult(res);
            }
            return scope.getObjectResult();
        } catch (Throwable t) {
            JNIExceptionWrapper.throwInHotSpot(env, t);
            return Word.nullPointer();
        }
    }

    @CEntryPoint(name = "Java_com_oracle_truffle_runtime_hotspot_libgraal_TruffleToLibGraalCalls_compilerOptionExists")
    @SuppressWarnings({"unused", "try"})
    @TruffleToLibGraal(Id.CompilerOptionExists)
    public static boolean compilerOptionExists(JNIEnv env, JClass hsClazz, @IsolateThreadContext long isolateThreadAddress, JString optionName) {
        try (JNIMethodScope scope = openScope(Id.CompilerOptionExists, env)) {
            return (boolean) singleton().compilerOptionExists.invoke(JNIUtil.createString(env, optionName));
        } catch (Throwable t) {
            JNIExceptionWrapper.throwInHotSpot(env, t);
            return false;
        }
    }

    @CEntryPoint(name = "Java_com_oracle_truffle_runtime_hotspot_libgraal_TruffleToLibGraalCalls_validateCompilerOption")
    @SuppressWarnings({"unused", "try"})
    @TruffleToLibGraal(Id.ValidateCompilerOption)
    public static JString validateCompilerOption(JNIEnv env, JClass hsClazz, @IsolateThreadContext long isolateThreadAddress, JString optionName, JString optionValue) {
        try {
            JNIMethodScope scope = openScope(Id.ValidateCompilerOption, env);
            try (JNIMethodScope s = scope) {
                String result = (String) singleton().validateCompilerOption.invoke(JNIUtil.createString(env, optionName), JNIUtil.createString(env, optionValue));
                scope.setObjectResult(JNIUtil.createHSString(env, result));
            }
            return scope.getObjectResult();
        } catch (Throwable t) {
            JNIExceptionWrapper.throwInHotSpot(env, t);
            return Word.nullPointer();
        }
    }

    @CEntryPoint(name = "Java_com_oracle_truffle_runtime_hotspot_libgraal_TruffleToLibGraalCalls_purgePartialEvaluationCaches")
    @SuppressWarnings({"unused", "try"})
    @TruffleToLibGraal(Id.PurgePartialEvaluationCaches)
    public static void purgePartialEvaluationCaches(JNIEnv env, JClass hsClass, @IsolateThreadContext long isolateThreadAddress, long compilerHandle) {
        try (JNIMethodScope s = openScope(Id.PurgePartialEvaluationCaches, env)) {
            Object compiler = LibGraalObjectHandles.resolve(compilerHandle, Object.class);
            if (compiler != null) {
                singleton().purgePartialEvaluationCaches.invoke(compiler);
            }
        } catch (Throwable t) {
            JNIExceptionWrapper.throwInHotSpot(env, t);
        }
    }

    @CEntryPoint(name = "Java_com_oracle_truffle_runtime_hotspot_libgraal_TruffleToLibGraalCalls_getCompilerVersion")
    @SuppressWarnings({"unused", "try"})
    @TruffleToLibGraal(Id.GetCompilerVersion)
    public static JString getCompilerVersion(JNIEnv env, JClass hsClass, @IsolateThreadContext long isolateThreadAddress) {
        try {
            JNIMethodScope scope = openScope(Id.GetCompilerVersion, env);
            try (JNIMethodScope s = scope) {
                String version = (String) singleton().getCompilerVersion.invoke();
                scope.setObjectResult(JNIUtil.createHSString(env, version));
            }
            return scope.getObjectResult();
        } catch (Throwable t) {
            JNIExceptionWrapper.throwInHotSpot(env, t);
            return Word.nullPointer();
        }
    }

    @CEntryPoint(name = "Java_com_oracle_truffle_runtime_hotspot_libgraal_LibGraalObject_releaseHandle")
    @SuppressWarnings("unused")
    public static boolean releaseHandle(JNIEnv jniEnv, JClass jclass, @IsolateThreadContext long isolateThreadAddress, long handle) {
        try {
            ObjectHandles.getGlobal().destroy(Word.pointer(handle));
            return true;
        } catch (Throwable t) {
            return false;
        }
    }
}
