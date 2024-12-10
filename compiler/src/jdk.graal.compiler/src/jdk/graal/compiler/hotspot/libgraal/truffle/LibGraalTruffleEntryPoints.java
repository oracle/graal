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
package jdk.graal.compiler.hotspot.libgraal.truffle;

import java.util.Objects;
import java.util.function.Supplier;

import org.graalvm.jniutils.HSObject;
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
import org.graalvm.nativeimage.ObjectHandles;
import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.nativeimage.c.function.CEntryPoint.IsolateThreadContext;
import org.graalvm.nativeimage.c.type.CLongPointer;
import org.graalvm.word.PointerBase;
import org.graalvm.word.WordFactory;

import com.oracle.truffle.compiler.TruffleCompilable;
import com.oracle.truffle.compiler.TruffleCompilerListener;
import com.oracle.truffle.compiler.TruffleCompilerOptionDescriptor;
import com.oracle.truffle.compiler.hotspot.libgraal.TruffleToLibGraal;
import com.oracle.truffle.compiler.hotspot.libgraal.TruffleToLibGraal.Id;

import jdk.graal.compiler.hotspot.CompilationContext;
import jdk.graal.compiler.hotspot.HotSpotGraalServices;
import jdk.graal.compiler.libgraal.LibGraalFeature;
import jdk.graal.compiler.libgraal.LibGraalJNIMethodScope;
import jdk.graal.compiler.libgraal.LibGraalUtil;
import jdk.graal.compiler.libgraal.NativeImageHostEntryPoints;
import jdk.graal.compiler.truffle.TruffleCompilerOptions;
import jdk.graal.compiler.truffle.hotspot.HotSpotTruffleCompilationSupport;
import jdk.graal.compiler.truffle.hotspot.HotSpotTruffleCompilerImpl;
import jdk.graal.nativeimage.LibGraalRuntime;
import jdk.vm.ci.hotspot.HotSpotJVMCIRuntime;
import jdk.vm.ci.hotspot.HotSpotVMConfigAccess;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Truffle specific {@link CEntryPoint} implementations.
 */
public class LibGraalTruffleEntryPoints {

    private static volatile int lastJavaPCOffset = -1;

    private static JNIMethodScope openScope(Enum<?> id, JNIEnv env) {
        Objects.requireNonNull(id, "Id must be non null.");
        String scopeName = LibGraalUtil.getUnqualifiedName(LibGraalTruffleEntryPoints.class) + "::" + id;
        int offset = lastJavaPCOffset;
        if (offset == -1) {
            HotSpotVMConfigAccess configAccess = new HotSpotVMConfigAccess(HotSpotJVMCIRuntime.runtime().getConfigStore());
            int anchor = configAccess.getFieldOffset("JavaThread::_anchor", Integer.class, "JavaFrameAnchor");
            int lastJavaPc = configAccess.getFieldOffset("JavaFrameAnchor::_last_Java_pc", Integer.class, "address");
            offset = anchor + lastJavaPc;
            lastJavaPCOffset = offset;
        }

        long currentJavaThread = HotSpotJVMCIRuntime.runtime().getCurrentJavaThread();
        CLongPointer currentThreadLastJavaPCOffset = (CLongPointer) WordFactory.unsigned(currentJavaThread).add(offset);
        PointerBase javaFrameAnchor = WordFactory.pointer(currentThreadLastJavaPCOffset.read());
        return LibGraalJNIMethodScope.open(scopeName, env, javaFrameAnchor.isNonNull());
    }

    @SuppressWarnings({"unused", "try"})
    @CEntryPoint(name = "Java_com_oracle_truffle_runtime_hotspot_libgraal_TruffleToLibGraalCalls_initializeIsolate", include = LibGraalFeature.IsEnabled.class)
    @TruffleToLibGraal(Id.InitializeIsolate)
    public static void initializeIsolate(JNIEnv env, JClass hsClazz, @IsolateThreadContext long isolateThreadId, JClass runtimeClass) {
        try (JNIMethodScope s = openScope(Id.InitializeIsolate, env)) {
            TruffleFromLibGraalStartPoints.initializeJNI(runtimeClass);
            TruffleLibGraalShutdownHook.registerShutdownHook();
        } catch (Throwable t) {
            JNIExceptionWrapper.throwInHotSpot(env, t);
        }
    }

    @SuppressWarnings({"unused", "try"})
    @CEntryPoint(name = "Java_com_oracle_truffle_runtime_hotspot_libgraal_TruffleToLibGraalCalls_registerRuntime", include = LibGraalFeature.IsEnabled.class)
    @TruffleToLibGraal(Id.RegisterRuntime)
    public static boolean registerRuntime(JNIEnv env, JClass hsClazz, @IsolateThreadContext long isolateThreadId, JObject truffleRuntime) {
        try (JNIMethodScope s = openScope(Id.RegisterRuntime, env)) {
            long truffleRuntimeWeakRef = JNIUtil.NewWeakGlobalRef(env, truffleRuntime, "TruffleCompilerRuntime").rawValue();
            return LibGraalTruffleHostEnvironmentLookup.registerRuntime(truffleRuntimeWeakRef);
        } catch (Throwable t) {
            JNIExceptionWrapper.throwInHotSpot(env, t);
            return false;
        }
    }

    @SuppressWarnings({"unused", "try"})
    @CEntryPoint(name = "Java_com_oracle_truffle_runtime_hotspot_libgraal_TruffleToLibGraalCalls_initializeRuntime", include = LibGraalFeature.IsEnabled.class)
    @TruffleToLibGraal(Id.InitializeRuntime)
    public static long initializeRuntime(JNIEnv env, JClass hsClazz, @IsolateThreadContext long isolateThreadId,
                    JObject truffleRuntime, JClass hsClassLoaderDelegate) {
        try (JNIMethodScope s = openScope(Id.InitializeRuntime, env)) {
            HSObject hsHandle = new HSObject(env, truffleRuntime, true, false);
            HSTruffleCompilerRuntime hsTruffleRuntime = new HSTruffleCompilerRuntime(hsHandle, hsClassLoaderDelegate.rawValue());
            return LibGraalObjectHandles.create(hsTruffleRuntime);
        } catch (Throwable t) {
            JNIExceptionWrapper.throwInHotSpot(env, t);
            return 0L;
        }
    }

    @SuppressWarnings({"unused", "try"})
    @CEntryPoint(name = "Java_com_oracle_truffle_runtime_hotspot_libgraal_TruffleToLibGraalCalls_newCompiler", include = LibGraalFeature.IsEnabled.class)
    @TruffleToLibGraal(Id.NewCompiler)
    public static long newCompiler(JNIEnv env, JClass hsClazz, @IsolateThreadContext long isolateThreadId, long truffleRuntimeHandle) {
        try (JNIMethodScope s = openScope(Id.NewCompiler, env)) {
            Object truffleRuntime = LibGraalObjectHandles.resolve(truffleRuntimeHandle, Object.class);
            /*
             * Unlike `LibGraalTruffleHostEnvironment`, Truffle libgraal entry points use the global
             * compilation context by default, so we don't need to call
             * `HotSpotGraalServices.enterGlobalCompilationContext()` before creating
             * `TruffleCompilerImpl`. The `doCompile` method enters a local compilation context
             * through its own call to `HotSpotGraalServices.openLocalCompilationContext`.
             */
            HotSpotTruffleCompilerImpl compiler = HotSpotTruffleCompilerImpl.create((HSTruffleCompilerRuntime) truffleRuntime, null);
            return LibGraalObjectHandles.create(compiler);
        } catch (Throwable t) {
            JNIExceptionWrapper.throwInHotSpot(env, t);
            return 0;
        }
    }

    @SuppressWarnings("unused")
    @CEntryPoint(name = "Java_com_oracle_truffle_runtime_hotspot_libgraal_TruffleToLibGraalCalls_initializeCompiler", include = LibGraalFeature.IsEnabled.class)
    @TruffleToLibGraal(Id.InitializeRuntime)
    public static void initializeCompiler(JNIEnv env, JClass hsClazz, @IsolateThreadContext long isolateThreadId, long compilerHandle, JObject hsCompilable,
                    boolean firstInitialization) {
        try (JNIMethodScope scope = openScope(Id.InitializeCompiler, env)) {
            HotSpotTruffleCompilerImpl compiler = LibGraalObjectHandles.resolve(compilerHandle, HotSpotTruffleCompilerImpl.class);
            Object compilableHsHandle = new HSObject(scope, hsCompilable);
            TruffleCompilable compilable = new HSTruffleCompilable(compilableHsHandle);
            compiler.initialize(compilable, firstInitialization);
        } catch (Throwable t) {
            JNIExceptionWrapper.throwInHotSpot(env, t);
        }
    }

    @SuppressWarnings({"unused", "try"})
    @CEntryPoint(name = "Java_com_oracle_truffle_runtime_hotspot_libgraal_TruffleToLibGraalCalls_getCompilerConfigurationFactoryName", include = LibGraalFeature.IsEnabled.class)
    @TruffleToLibGraal(Id.GetCompilerConfigurationFactoryName)
    public static JString getCompilerConfigurationFactoryName(JNIEnv env, JClass hsClazz, @IsolateThreadContext long isolateThreadId, long truffleRuntimeHandle) {
        try {
            JNIMethodScope scope = openScope(Id.GetCompilerConfigurationFactoryName, env);
            try (JNIMethodScope s = scope) {
                String name = HotSpotTruffleCompilationSupport.getLazyCompilerConfigurationName();
                scope.setObjectResult(JNIUtil.createHSString(env, name));
            }
            return scope.getObjectResult();
        } catch (Throwable t) {
            JNIExceptionWrapper.throwInHotSpot(env, t);
            return WordFactory.nullPointer();
        }
    }

    @SuppressWarnings({"unused", "try"})
    @CEntryPoint(name = "Java_com_oracle_truffle_runtime_hotspot_libgraal_TruffleToLibGraalCalls_doCompile", include = LibGraalFeature.IsEnabled.class)
    @TruffleToLibGraal(Id.DoCompile)
    public static void doCompile(JNIEnv env,
                    JClass hsClazz,
                    @IsolateThreadContext long isolateThreadId,
                    long compilerHandle,
                    JObject hsTask,
                    JObject hsCompilable,
                    JObject hsListener) {
        try (JNIMethodScope scope = openScope(Id.DoCompile, env)) {
            HotSpotTruffleCompilerImpl compiler = LibGraalObjectHandles.resolve(compilerHandle, HotSpotTruffleCompilerImpl.class);
            Object taskHsHandle = hsTask.isNull() ? null : new HSObject(scope, hsTask);
            Object compilableHsHandle = new HSObject(scope, hsCompilable);
            Object listenerHsHandle = hsListener.isNull() ? null : new HSObject(scope, hsListener);
            try {
                HSTruffleCompilationTask task = taskHsHandle == null ? null : new HSTruffleCompilationTask(taskHsHandle);
                HSTruffleCompilerListener listener = listenerHsHandle == null ? null : new HSTruffleCompilerListener(listenerHsHandle);
                HSTruffleCompilable compilable = new HSTruffleCompilable(compilableHsHandle);
                try (CompilationContext hotSpotObjectConstantScope = HotSpotGraalServices.openLocalCompilationContext(compilable)) {
                    compiler.doCompile(task, compilable, listener);
                }
            } finally {
                LibGraalRuntime.processReferences();
                LibGraalRuntime.notifyLowMemoryPoint(true);
            }
        } catch (Throwable t) {
            JNIExceptionWrapper.throwInHotSpot(env, t);
        }
    }

    @SuppressWarnings({"unused", "try"})
    @CEntryPoint(name = "Java_com_oracle_truffle_runtime_hotspot_libgraal_TruffleToLibGraalCalls_shutdown", include = LibGraalFeature.IsEnabled.class)
    @TruffleToLibGraal(Id.Shutdown)
    public static void shutdown(JNIEnv env, JClass hsClazz, @IsolateThreadContext long isolateThreadId, long handle) {
        try (JNIMethodScope s = openScope(Id.Shutdown, env)) {
            HotSpotTruffleCompilerImpl compiler = LibGraalObjectHandles.resolve(handle, HotSpotTruffleCompilerImpl.class);
            compiler.shutdown();
        } catch (Throwable t) {
            JNIExceptionWrapper.throwInHotSpot(env, t);
        }
    }

    @SuppressWarnings({"unused", "try"})
    @CEntryPoint(name = "Java_com_oracle_truffle_runtime_hotspot_libgraal_TruffleToLibGraalCalls_installTruffleCallBoundaryMethod", include = LibGraalFeature.IsEnabled.class)
    @TruffleToLibGraal(Id.InstallTruffleCallBoundaryMethod)
    public static void installTruffleCallBoundaryMethod(JNIEnv env, JClass hsClazz, @IsolateThreadContext long isolateThreadId, long handle, long methodHandle) {
        try (JNIMethodScope s = openScope(Id.InstallTruffleCallBoundaryMethod, env)) {
            HotSpotTruffleCompilerImpl compiler = LibGraalObjectHandles.resolve(handle, HotSpotTruffleCompilerImpl.class);
            compiler.installTruffleCallBoundaryMethod(HotSpotJVMCIRuntime.runtime().unhand(ResolvedJavaMethod.class, methodHandle), null);
        } catch (Throwable t) {
            JNIExceptionWrapper.throwInHotSpot(env, t);
        }
    }

    @SuppressWarnings({"unused", "try"})
    @CEntryPoint(name = "Java_com_oracle_truffle_runtime_hotspot_libgraal_TruffleToLibGraalCalls_installTruffleReservedOopMethod", include = LibGraalFeature.IsEnabled.class)
    @TruffleToLibGraal(Id.InstallTruffleReservedOopMethod)
    public static void installTruffleReservedOopMethod(JNIEnv env, JClass hsClazz, @IsolateThreadContext long isolateThreadId, long handle, long methodHandle) {
        try (JNIMethodScope s = openScope(Id.InstallTruffleReservedOopMethod, env)) {
            HotSpotTruffleCompilerImpl compiler = LibGraalObjectHandles.resolve(handle, HotSpotTruffleCompilerImpl.class);
            compiler.installTruffleReservedOopMethod(HotSpotJVMCIRuntime.runtime().unhand(ResolvedJavaMethod.class, methodHandle), null);
        } catch (Throwable t) {
            JNIExceptionWrapper.throwInHotSpot(env, t);
        }
    }

    @SuppressWarnings("unused")
    @CEntryPoint(name = "Java_com_oracle_truffle_runtime_hotspot_libgraal_TruffleToLibGraalCalls_pendingTransferToInterpreterOffset", include = LibGraalFeature.IsEnabled.class)
    @TruffleToLibGraal(Id.PendingTransferToInterpreterOffset)
    public static int pendingTransferToInterpreterOffset(JNIEnv env, JClass hsClazz, @IsolateThreadContext long isolateThreadId, long handle, JObject hsCompilable) {
        try (JNIMethodScope scope = openScope(Id.PendingTransferToInterpreterOffset, env)) {
            HotSpotTruffleCompilerImpl compiler = LibGraalObjectHandles.resolve(handle, HotSpotTruffleCompilerImpl.class);
            Object compilableHsHandle = new HSObject(scope, hsCompilable);
            TruffleCompilable compilable = new HSTruffleCompilable(compilableHsHandle);
            return compiler.pendingTransferToInterpreterOffset(compilable);
        } catch (Throwable t) {
            JNIExceptionWrapper.throwInHotSpot(env, t);
            return 0;
        }
    }

    @CEntryPoint(name = "Java_com_oracle_truffle_runtime_hotspot_libgraal_TruffleToLibGraalCalls_getSuppliedString", include = LibGraalFeature.IsEnabled.class)
    @SuppressWarnings({"unused", "try", "unchecked"})
    @TruffleToLibGraal(Id.GetSuppliedString)
    public static JString getSuppliedString(JNIEnv env, JClass hsClazz, @IsolateThreadContext long isolateThreadId, long handle) {
        try {
            JNIMethodScope scope = openScope(Id.GetSuppliedString, env);
            try (JNIMethodScope s = scope) {
                Supplier<String> stringSupplier = LibGraalObjectHandles.resolve(handle, Supplier.class);
                if (stringSupplier != null) {
                    String stackTrace = stringSupplier.get();
                    scope.setObjectResult(JNIUtil.createHSString(env, stackTrace));
                } else {
                    scope.setObjectResult(WordFactory.nullPointer());
                }
            }
            return scope.getObjectResult();
        } catch (Throwable t) {
            JNIExceptionWrapper.throwInHotSpot(env, t);
            return WordFactory.nullPointer();
        }
    }

    @CEntryPoint(name = "Java_com_oracle_truffle_runtime_hotspot_libgraal_TruffleToLibGraalCalls_getNodeCount", include = LibGraalFeature.IsEnabled.class)
    @SuppressWarnings({"unused", "try"})
    @TruffleToLibGraal(Id.GetNodeCount)
    public static int getNodeCount(JNIEnv env, JClass hsClazz, @IsolateThreadContext long isolateThreadId, long handle) {
        try (JNIMethodScope s = openScope(Id.GetNodeCount, env)) {
            Object graphInfo = LibGraalObjectHandles.resolve(handle, Object.class);
            return ((TruffleCompilerListener.GraphInfo) graphInfo).getNodeCount();
        } catch (Throwable t) {
            JNIExceptionWrapper.throwInHotSpot(env, t);
            return 0;
        }
    }

    @CEntryPoint(name = "Java_com_oracle_truffle_runtime_hotspot_libgraal_TruffleToLibGraalCalls_getNodeTypes", include = LibGraalFeature.IsEnabled.class)
    @SuppressWarnings({"unused", "try"})
    @TruffleToLibGraal(Id.GetNodeTypes)
    public static JObjectArray getNodeTypes(JNIEnv env, JClass hsClazz, @IsolateThreadContext long isolateThreadId, long handle, boolean simpleNames) {
        try {
            JNIMethodScope scope = openScope(Id.GetNodeTypes, env);
            try (JNIMethodScope s = scope) {
                Object graphInfo = LibGraalObjectHandles.resolve(handle, Object.class);
                String[] nodeTypes = ((TruffleCompilerListener.GraphInfo) graphInfo).getNodeTypes(simpleNames);
                JClass componentType = getStringClass(env);
                JObjectArray res = JNIUtil.NewObjectArray(env, nodeTypes.length, componentType, WordFactory.nullPointer());
                for (int i = 0; i < nodeTypes.length; i++) {
                    JNIUtil.SetObjectArrayElement(env, res, i, JNIUtil.createHSString(env, nodeTypes[i]));
                }
                scope.setObjectResult(res);
            }
            return scope.getObjectResult();
        } catch (Throwable t) {
            JNIExceptionWrapper.throwInHotSpot(env, t);
            return WordFactory.nullPointer();
        }
    }

    private static JClass getStringClass(JNIEnv env) {
        return JNIUtil.NewGlobalRef(env, JNIUtil.findClass(env, "java/lang/String"), "Class<java.lang.String>");
    }

    @CEntryPoint(name = "Java_com_oracle_truffle_runtime_hotspot_libgraal_TruffleToLibGraalCalls_getTargetCodeSize", include = LibGraalFeature.IsEnabled.class)
    @SuppressWarnings({"unused", "try"})
    @TruffleToLibGraal(Id.GetTargetCodeSize)
    public static int getTargetCodeSize(JNIEnv env, JClass hsClazz, @IsolateThreadContext long isolateThreadId, long handle) {
        try (JNIMethodScope s = openScope(Id.GetTargetCodeSize, env)) {
            Object compilationResultInfo = LibGraalObjectHandles.resolve(handle, Object.class);
            return ((TruffleCompilerListener.CompilationResultInfo) compilationResultInfo).getTargetCodeSize();
        } catch (Throwable t) {
            JNIExceptionWrapper.throwInHotSpot(env, t);
            return 0;
        }
    }

    @CEntryPoint(name = "Java_com_oracle_truffle_runtime_hotspot_libgraal_TruffleToLibGraalCalls_getTotalFrameSize", include = LibGraalFeature.IsEnabled.class)
    @SuppressWarnings({"unused", "try"})
    @TruffleToLibGraal(Id.GetTotalFrameSize)
    public static int getTotalFrameSize(JNIEnv env, JClass hsClazz, @IsolateThreadContext long isolateThreadId, long handle) {
        try (JNIMethodScope s = openScope(Id.GetTotalFrameSize, env)) {
            Object compilationResultInfo = LibGraalObjectHandles.resolve(handle, Object.class);
            return ((TruffleCompilerListener.CompilationResultInfo) compilationResultInfo).getTotalFrameSize();
        } catch (Throwable t) {
            JNIExceptionWrapper.throwInHotSpot(env, t);
            return 0;
        }
    }

    @CEntryPoint(name = "Java_com_oracle_truffle_runtime_hotspot_libgraal_TruffleToLibGraalCalls_getExceptionHandlersCount", include = LibGraalFeature.IsEnabled.class)
    @SuppressWarnings({"unused", "try"})
    @TruffleToLibGraal(Id.GetExceptionHandlersCount)
    public static int getExceptionHandlersCount(JNIEnv env, JClass hsClazz, @IsolateThreadContext long isolateThreadId, long handle) {
        try (JNIMethodScope s = openScope(Id.GetExceptionHandlersCount, env)) {
            Object compilationResultInfo = LibGraalObjectHandles.resolve(handle, Object.class);
            return ((TruffleCompilerListener.CompilationResultInfo) compilationResultInfo).getExceptionHandlersCount();
        } catch (Throwable t) {
            JNIExceptionWrapper.throwInHotSpot(env, t);
            return 0;
        }
    }

    @CEntryPoint(name = "Java_com_oracle_truffle_runtime_hotspot_libgraal_TruffleToLibGraalCalls_getInfopointsCount", include = LibGraalFeature.IsEnabled.class)
    @SuppressWarnings({"unused", "try"})
    @TruffleToLibGraal(Id.GetInfopointsCount)
    public static int getInfopointsCount(JNIEnv env, JClass hsClazz, @IsolateThreadContext long isolateThreadId, long handle) {
        try (JNIMethodScope s = openScope(Id.GetInfopointsCount, env)) {
            Object compilationResultInfo = LibGraalObjectHandles.resolve(handle, Object.class);
            return ((TruffleCompilerListener.CompilationResultInfo) compilationResultInfo).getInfopointsCount();
        } catch (Throwable t) {
            JNIExceptionWrapper.throwInHotSpot(env, t);
            return 0;
        }
    }

    @CEntryPoint(name = "Java_com_oracle_truffle_runtime_hotspot_libgraal_TruffleToLibGraalCalls_getInfopoints", include = LibGraalFeature.IsEnabled.class)
    @SuppressWarnings({"unused", "try"})
    @TruffleToLibGraal(Id.GetInfopoints)
    public static JObjectArray getInfopoints(JNIEnv env, JClass hsClazz, @IsolateThreadContext long isolateThreadId, long handle) {
        try {
            JNIMethodScope scope = openScope(Id.GetInfopoints, env);
            try (JNIMethodScope s = scope) {
                Object compilationResultInfo = LibGraalObjectHandles.resolve(handle, Object.class);
                String[] infoPoints = ((TruffleCompilerListener.CompilationResultInfo) compilationResultInfo).getInfopoints();
                JClass componentType = getStringClass(env);
                JObjectArray res = JNIUtil.NewObjectArray(env, infoPoints.length, componentType, WordFactory.nullPointer());
                for (int i = 0; i < infoPoints.length; i++) {
                    JNIUtil.SetObjectArrayElement(env, res, i, JNIUtil.createHSString(env, infoPoints[i]));
                }
                scope.setObjectResult(res);
            }
            return scope.getObjectResult();
        } catch (Throwable t) {
            JNIExceptionWrapper.throwInHotSpot(env, t);
            return WordFactory.nullPointer();
        }
    }

    @CEntryPoint(name = "Java_com_oracle_truffle_runtime_hotspot_libgraal_TruffleToLibGraalCalls_getMarksCount", include = LibGraalFeature.IsEnabled.class)
    @SuppressWarnings({"unused", "try"})
    @TruffleToLibGraal(Id.GetMarksCount)
    public static int getMarksCount(JNIEnv env, JClass hsClazz, @IsolateThreadContext long isolateThreadId, long handle) {
        try (JNIMethodScope s = openScope(Id.GetMarksCount, env)) {
            Object compilationResultInfo = LibGraalObjectHandles.resolve(handle, Object.class);
            return ((TruffleCompilerListener.CompilationResultInfo) compilationResultInfo).getMarksCount();
        } catch (Throwable t) {
            JNIExceptionWrapper.throwInHotSpot(env, t);
            return 0;
        }
    }

    @CEntryPoint(name = "Java_com_oracle_truffle_runtime_hotspot_libgraal_TruffleToLibGraalCalls_getDataPatchesCount", include = LibGraalFeature.IsEnabled.class)
    @SuppressWarnings({"unused", "try"})
    @TruffleToLibGraal(Id.GetDataPatchesCount)
    public static int getDataPatchesCount(JNIEnv env, JClass hsClazz, @IsolateThreadContext long isolateThreadId, long handle) {
        try (JNIMethodScope s = openScope(Id.GetDataPatchesCount, env)) {
            Object compilationResultInfo = LibGraalObjectHandles.resolve(handle, Object.class);
            return ((TruffleCompilerListener.CompilationResultInfo) compilationResultInfo).getDataPatchesCount();
        } catch (Throwable t) {
            JNIExceptionWrapper.throwInHotSpot(env, t);
            return 0;
        }
    }

    @CEntryPoint(name = "Java_com_oracle_truffle_runtime_hotspot_libgraal_TruffleToLibGraalCalls_listCompilerOptions", include = LibGraalFeature.IsEnabled.class)
    @SuppressWarnings({"unused", "try"})
    @TruffleToLibGraal(Id.ListCompilerOptions)
    public static JByteArray listCompilerOptions(JNIEnv env, JClass hsClazz, @IsolateThreadContext long isolateThreadId) {
        try {
            JNIMethodScope scope = openScope(Id.ListCompilerOptions, env);
            try (JNIMethodScope s = scope) {
                TruffleCompilerOptionDescriptor[] options1 = TruffleCompilerOptions.listOptions();
                Object[] result = new Object[options1.length];
                for (int i1 = 0; i1 < options1.length; i1++) {
                    TruffleCompilerOptionDescriptor option = options1[i1];
                    result[i1] = NativeImageHostEntryPoints.createTruffleCompilerOptionDescriptor(option.name(), option.type().ordinal(), option.deprecated(), option.help(),
                                    option.deprecationMessage());
                }
                Object[] options = result;
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
            return WordFactory.nullPointer();
        }
    }

    @CEntryPoint(name = "Java_com_oracle_truffle_runtime_hotspot_libgraal_TruffleToLibGraalCalls_compilerOptionExists", include = LibGraalFeature.IsEnabled.class)
    @SuppressWarnings({"unused", "try"})
    @TruffleToLibGraal(Id.CompilerOptionExists)
    public static boolean compilerOptionExists(JNIEnv env, JClass hsClazz, @IsolateThreadContext long isolateThreadId, JString optionName) {
        try (JNIMethodScope scope = openScope(Id.CompilerOptionExists, env)) {
            String optionName1 = JNIUtil.createString(env, optionName);
            return TruffleCompilerOptions.optionExists(optionName1);
        } catch (Throwable t) {
            JNIExceptionWrapper.throwInHotSpot(env, t);
            return false;
        }
    }

    private static String validateCompilerOption(String optionName, String optionValue) {
        return TruffleCompilerOptions.validateOption(optionName, optionValue);
    }

    @CEntryPoint(name = "Java_com_oracle_truffle_runtime_hotspot_libgraal_TruffleToLibGraalCalls_validateCompilerOption", include = LibGraalFeature.IsEnabled.class)
    @SuppressWarnings({"unused", "try"})
    @TruffleToLibGraal(Id.ValidateCompilerOption)
    public static JString validateCompilerOption(JNIEnv env, JClass hsClazz, @IsolateThreadContext long isolateThreadId, JString optionName, JString optionValue) {
        try {
            JNIMethodScope scope = openScope(Id.ValidateCompilerOption, env);
            try (JNIMethodScope s = scope) {
                String result = validateCompilerOption(JNIUtil.createString(env, optionName), JNIUtil.createString(env, optionValue));
                scope.setObjectResult(JNIUtil.createHSString(env, result));
            }
            return scope.getObjectResult();
        } catch (Throwable t) {
            JNIExceptionWrapper.throwInHotSpot(env, t);
            return WordFactory.nullPointer();
        }
    }

    @CEntryPoint(name = "Java_com_oracle_truffle_runtime_hotspot_libgraal_TruffleToLibGraalCalls_purgePartialEvaluationCaches", include = LibGraalFeature.IsEnabled.class)
    @SuppressWarnings({"unused", "try"})
    @TruffleToLibGraal(Id.PurgePartialEvaluationCaches)
    public static void purgePartialEvaluationCaches(JNIEnv env, JClass hsClass, @IsolateThreadContext long isolateThreadId, long compilerHandle) {
        try (JNIMethodScope s = openScope(Id.PurgePartialEvaluationCaches, env)) {
            HotSpotTruffleCompilerImpl compiler = LibGraalObjectHandles.resolve(compilerHandle, HotSpotTruffleCompilerImpl.class);
            if (compiler != null) {
                compiler.purgePartialEvaluationCaches();
            }
        } catch (Throwable t) {
            JNIExceptionWrapper.throwInHotSpot(env, t);
        }
    }

    @CEntryPoint(name = "Java_com_oracle_truffle_runtime_hotspot_libgraal_TruffleToLibGraalCalls_getCompilerVersion", include = LibGraalFeature.IsEnabled.class)
    @SuppressWarnings({"unused", "try"})
    @TruffleToLibGraal(Id.GetCompilerVersion)
    public static JString getCompilerVersion(JNIEnv env, JClass hsClass, @IsolateThreadContext long isolateThreadId) {
        try {
            JNIMethodScope scope = openScope(Id.GetCompilerVersion, env);
            try (JNIMethodScope s = scope) {
                String version = HSTruffleCompilerRuntime.COMPILER_VERSION;
                scope.setObjectResult(JNIUtil.createHSString(env, version));
            }
            return scope.getObjectResult();
        } catch (Throwable t) {
            JNIExceptionWrapper.throwInHotSpot(env, t);
            return WordFactory.nullPointer();
        }
    }

    @CEntryPoint(name = "Java_com_oracle_truffle_runtime_hotspot_libgraal_LibGraalObject_releaseHandle", include = LibGraalFeature.IsEnabled.class)
    @SuppressWarnings("unused")
    public static boolean releaseHandle(JNIEnv jniEnv, JClass jclass, @IsolateThreadContext long isolateThreadId, long handle) {
        try {
            ObjectHandles.getGlobal().destroy(WordFactory.pointer(handle));
            return true;
        } catch (Throwable t) {
            return false;
        }
    }
}
