/*
 * Copyright (c) 2018, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.graal.hotspot.libgraal.truffle;

import static com.oracle.truffle.compiler.hotspot.libgraal.TruffleToLibGraal.Id.DoCompile;
import static com.oracle.truffle.compiler.hotspot.libgraal.TruffleToLibGraal.Id.GetCompilerConfigurationFactoryName;
import static com.oracle.truffle.compiler.hotspot.libgraal.TruffleToLibGraal.Id.GetCompilerVersion;
import static com.oracle.truffle.compiler.hotspot.libgraal.TruffleToLibGraal.Id.GetDataPatchesCount;
import static com.oracle.truffle.compiler.hotspot.libgraal.TruffleToLibGraal.Id.GetExceptionHandlersCount;
import static com.oracle.truffle.compiler.hotspot.libgraal.TruffleToLibGraal.Id.GetInfopoints;
import static com.oracle.truffle.compiler.hotspot.libgraal.TruffleToLibGraal.Id.GetInfopointsCount;
import static com.oracle.truffle.compiler.hotspot.libgraal.TruffleToLibGraal.Id.GetMarksCount;
import static com.oracle.truffle.compiler.hotspot.libgraal.TruffleToLibGraal.Id.GetNodeCount;
import static com.oracle.truffle.compiler.hotspot.libgraal.TruffleToLibGraal.Id.GetNodeTypes;
import static com.oracle.truffle.compiler.hotspot.libgraal.TruffleToLibGraal.Id.GetSuppliedString;
import static com.oracle.truffle.compiler.hotspot.libgraal.TruffleToLibGraal.Id.GetTargetCodeSize;
import static com.oracle.truffle.compiler.hotspot.libgraal.TruffleToLibGraal.Id.GetTotalFrameSize;
import static com.oracle.truffle.compiler.hotspot.libgraal.TruffleToLibGraal.Id.InitializeCompiler;
import static com.oracle.truffle.compiler.hotspot.libgraal.TruffleToLibGraal.Id.InitializeRuntime;
import static com.oracle.truffle.compiler.hotspot.libgraal.TruffleToLibGraal.Id.InstallTruffleCallBoundaryMethod;
import static com.oracle.truffle.compiler.hotspot.libgraal.TruffleToLibGraal.Id.NewCompiler;
import static com.oracle.truffle.compiler.hotspot.libgraal.TruffleToLibGraal.Id.PendingTransferToInterpreterOffset;
import static com.oracle.truffle.compiler.hotspot.libgraal.TruffleToLibGraal.Id.PurgePartialEvaluationCaches;
import static com.oracle.truffle.compiler.hotspot.libgraal.TruffleToLibGraal.Id.RegisterRuntime;
import static com.oracle.truffle.compiler.hotspot.libgraal.TruffleToLibGraal.Id.Shutdown;
import static org.graalvm.jniutils.JNIUtil.NewObjectArray;
import static org.graalvm.jniutils.JNIUtil.SetObjectArrayElement;
import static org.graalvm.jniutils.JNIUtil.createHSString;

import java.util.function.Supplier;

import jdk.graal.compiler.hotspot.CompilationContext;
import jdk.graal.compiler.hotspot.HotSpotGraalServices;
import jdk.graal.compiler.serviceprovider.IsolateUtil;
import jdk.graal.compiler.truffle.TruffleCompilerOptions;
import jdk.graal.compiler.truffle.hotspot.HotSpotTruffleCompilationSupport;
import jdk.graal.compiler.truffle.hotspot.HotSpotTruffleCompilerImpl;
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
import org.graalvm.nativebridge.BinaryOutput.ByteArrayBinaryOutput;
import org.graalvm.nativeimage.Isolate;
import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.ObjectHandles;
import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.nativeimage.c.function.CEntryPoint.Builtin;
import org.graalvm.nativeimage.c.function.CEntryPoint.IsolateContext;
import org.graalvm.word.PointerBase;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.heap.Heap;
import com.oracle.svm.graal.hotspot.libgraal.LibGraal;
import com.oracle.svm.graal.hotspot.libgraal.LibGraalUtil;
import com.oracle.truffle.compiler.TruffleCompilable;
import com.oracle.truffle.compiler.TruffleCompilationTask;
import com.oracle.truffle.compiler.TruffleCompilerListener;
import com.oracle.truffle.compiler.TruffleCompilerListener.CompilationResultInfo;
import com.oracle.truffle.compiler.TruffleCompilerListener.GraphInfo;
import com.oracle.truffle.compiler.TruffleCompilerOptionDescriptor;
import com.oracle.truffle.compiler.hotspot.libgraal.TruffleToLibGraal;
import com.oracle.truffle.compiler.hotspot.libgraal.TruffleToLibGraal.Id;

import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Entry points in libgraal for {@link TruffleToLibGraal calls} from HotSpot.
 *
 * To trace Truffle calls between HotSpot and libgraal, set the {@code JNI_LIBGRAAL_TRACE_LEVEL}
 * system property to {@code 1}. For detailed tracing set the {@code JNI_LIBGRAAL_TRACE_LEVEL}
 * system property to {@code 3}.
 */
final class TruffleToLibGraalEntryPoints {

    private static final String COMPILER_VERSION = HotSpotTruffleCompilationSupport.readCompilerVersion();

    @CEntryPoint(builtin = Builtin.GET_CURRENT_THREAD, name = "Java_com_oracle_truffle_runtime_hotspot_libgraal_LibGraalScope_getIsolateThreadIn")
    private static native IsolateThread getIsolateThreadIn(PointerBase env, PointerBase hsClazz, @IsolateContext Isolate isolate);

    @CEntryPoint(name = "Java_com_oracle_truffle_runtime_hotspot_libgraal_LibGraalScope_attachThreadTo", builtin = CEntryPoint.Builtin.ATTACH_THREAD)
    static native long attachThreadTo(PointerBase env, PointerBase hsClazz, @CEntryPoint.IsolateContext long isolate);

    @CEntryPoint(name = "Java_com_oracle_truffle_runtime_hotspot_libgraal_LibGraalScope_detachThreadFrom", builtin = CEntryPoint.Builtin.DETACH_THREAD)
    static native void detachThreadFrom(PointerBase env, PointerBase hsClazz, @CEntryPoint.IsolateThreadContext long isolateThread);

    @SuppressWarnings({"unused"})
    @CEntryPoint(name = "Java_com_oracle_truffle_runtime_hotspot_libgraal_LibGraalObject_releaseHandle")
    public static boolean releaseHandle(PointerBase jniEnv,
                    PointerBase jclass,
                    @CEntryPoint.IsolateThreadContext long isolateThreadId,
                    long handle) {
        try {
            ObjectHandles.getGlobal().destroy(WordFactory.pointer(handle));
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    @SuppressWarnings({"unused"})
    @CEntryPoint(name = "Java_com_oracle_truffle_runtime_hotspot_libgraal_LibGraalScope_getIsolateId")
    public static long getIsolateId(PointerBase jniEnv,
                    PointerBase jclass,
                    @CEntryPoint.IsolateThreadContext long isolateThreadId) {
        try {
            return IsolateUtil.getIsolateID();
        } catch (Throwable t) {
            return 0L;
        }
    }

    @TruffleToLibGraal(Id.InitializeIsolate)
    @SuppressWarnings({"unused", "try"})
    @CEntryPoint(name = "Java_com_oracle_truffle_runtime_hotspot_libgraal_TruffleToLibGraalCalls_initializeIsolate")
    public static void initializeIsolate(JNIEnv env, JClass hsClazz, @CEntryPoint.IsolateThreadContext long isolateThreadId, JClass runtimeClass) {
        try (JNIMethodScope s = LibGraalUtil.openScope(TruffleToLibGraalEntryPoints.class, Id.InitializeIsolate, env)) {
            TruffleLibGraalShutdownHook.registerShutdownHook(env, runtimeClass);
        } catch (Throwable t) {
            JNIExceptionWrapper.throwInHotSpot(env, t);
        }
    }

    @TruffleToLibGraal(RegisterRuntime)
    @SuppressWarnings({"unused", "try"})
    @CEntryPoint(name = "Java_com_oracle_truffle_runtime_hotspot_libgraal_TruffleToLibGraalCalls_registerRuntime")
    public static boolean registerRuntime(JNIEnv env, JClass hsClazz, @CEntryPoint.IsolateThreadContext long isolateThreadId, JObject truffleRuntime) {
        try (JNIMethodScope s = LibGraalUtil.openScope(TruffleToLibGraalEntryPoints.class, RegisterRuntime, env)) {
            return LibGraalTruffleHostEnvironmentLookup.registerRuntime(env, truffleRuntime);
        } catch (Throwable t) {
            JNIExceptionWrapper.throwInHotSpot(env, t);
            return false;
        }
    }

    @TruffleToLibGraal(InitializeRuntime)
    @SuppressWarnings({"unused", "try"})
    @CEntryPoint(name = "Java_com_oracle_truffle_runtime_hotspot_libgraal_TruffleToLibGraalCalls_initializeRuntime")
    public static long initializeRuntime(JNIEnv env, JClass hsClazz, @CEntryPoint.IsolateThreadContext long isolateThreadId,
                    JObject truffleRuntime, JClass hsClassLoaderDelegate) {
        try (JNIMethodScope s = LibGraalUtil.openScope(TruffleToLibGraalEntryPoints.class, InitializeRuntime, env)) {
            ResolvedJavaType classLoaderDelegate = LibGraal.asResolvedJavaType(hsClassLoaderDelegate);
            HSTruffleCompilerRuntime hsTruffleRuntime = new HSTruffleCompilerRuntime(env, truffleRuntime, classLoaderDelegate, hsClassLoaderDelegate);
            long truffleRuntimeHandle = LibGraalObjectHandles.create(hsTruffleRuntime);
            return truffleRuntimeHandle;
        } catch (Throwable t) {
            JNIExceptionWrapper.throwInHotSpot(env, t);
            return 0L;
        }
    }

    @TruffleToLibGraal(NewCompiler)
    @SuppressWarnings({"unused", "try"})
    @CEntryPoint(name = "Java_com_oracle_truffle_runtime_hotspot_libgraal_TruffleToLibGraalCalls_newCompiler")
    public static long newCompiler(JNIEnv env, JClass hsClazz, @CEntryPoint.IsolateThreadContext long isolateThreadId, long truffleRuntimeHandle) {
        try (JNIMethodScope s = LibGraalUtil.openScope(TruffleToLibGraalEntryPoints.class, NewCompiler, env)) {
            HSTruffleCompilerRuntime hsTruffleRuntime = LibGraalObjectHandles.resolve(truffleRuntimeHandle, HSTruffleCompilerRuntime.class);
            HotSpotTruffleCompilerImpl compiler = HotSpotTruffleCompilerImpl.create(hsTruffleRuntime);
            return LibGraalObjectHandles.create(compiler);
        } catch (Throwable t) {
            JNIExceptionWrapper.throwInHotSpot(env, t);
            return 0;
        }
    }

    private static JClass getStringClass(JNIEnv env) {
        return JNIUtil.NewGlobalRef(env, JNIUtil.findClass(env, "java/lang/String"), "Class<java.lang.String>");
    }

    @TruffleToLibGraal(InitializeCompiler)
    @SuppressWarnings("unused")
    @CEntryPoint(name = "Java_com_oracle_truffle_runtime_hotspot_libgraal_TruffleToLibGraalCalls_initializeCompiler")
    public static void initializeCompiler(JNIEnv env, JClass hsClazz, @CEntryPoint.IsolateThreadContext long isolateThreadId, long compilerHandle, JObject hsCompilable,
                    boolean firstInitialization) {
        try (JNIMethodScope s = LibGraalUtil.openScope(TruffleToLibGraalEntryPoints.class, InitializeCompiler, env)) {
            HotSpotTruffleCompilerImpl compiler = LibGraalObjectHandles.resolve(compilerHandle, HotSpotTruffleCompilerImpl.class);
            HSTruffleCompilerRuntime runtime = (HSTruffleCompilerRuntime) compiler.getConfig().runtime();
            TruffleCompilable compilable = new HSTruffleCompilable(s, hsCompilable, runtime);
            compiler.initialize(compilable, firstInitialization);
        } catch (Throwable t) {
            JNIExceptionWrapper.throwInHotSpot(env, t);
        }
    }

    @TruffleToLibGraal(GetCompilerConfigurationFactoryName)
    @SuppressWarnings({"unused", "try"})
    @CEntryPoint(name = "Java_com_oracle_truffle_runtime_hotspot_libgraal_TruffleToLibGraalCalls_getCompilerConfigurationFactoryName")
    public static JString getCompilerConfigurationFactoryName(JNIEnv env, JClass hsClazz, @CEntryPoint.IsolateThreadContext long isolateThreadId, long truffleRuntimeHandle) {
        JNIMethodScope scope = LibGraalUtil.openScope(TruffleToLibGraalEntryPoints.class, GetCompilerConfigurationFactoryName, env);
        try (JNIMethodScope s = scope) {
            String name = HotSpotTruffleCompilationSupport.getLazyCompilerConfigurationName();
            scope.setObjectResult(createHSString(env, name));
        } catch (Throwable t) {
            JNIExceptionWrapper.throwInHotSpot(env, t);
            scope.setObjectResult(WordFactory.nullPointer());
        }
        return scope.getObjectResult();
    }

    @TruffleToLibGraal(DoCompile)
    @SuppressWarnings({"unused", "try"})
    @CEntryPoint(name = "Java_com_oracle_truffle_runtime_hotspot_libgraal_TruffleToLibGraalCalls_doCompile")
    public static void doCompile(JNIEnv env,
                    JClass hsClazz,
                    @CEntryPoint.IsolateThreadContext long isolateThreadId,
                    long compilerHandle,
                    JObject hsTask,
                    JObject hsCompilable,
                    JObject hsListener) {
        try (JNIMethodScope scope = LibGraalUtil.openScope(TruffleToLibGraalEntryPoints.class, DoCompile, env)) {
            HotSpotTruffleCompilerImpl compiler = LibGraalObjectHandles.resolve(compilerHandle, HotSpotTruffleCompilerImpl.class);
            HSTruffleCompilerRuntime runtime = (HSTruffleCompilerRuntime) compiler.getConfig().runtime();
            HSTruffleCompilable compilable = new HSTruffleCompilable(scope, hsCompilable, runtime);
            TruffleCompilationTask task = hsTask.isNull() ? null : new HSTruffleCompilationTask(scope, hsTask, runtime);
            try (CompilationContext hotSpotObjectConstantScope = HotSpotGraalServices.openLocalCompilationContext(compilable)) {
                TruffleCompilerListener listener = hsListener.isNull() ? null : new HSTruffleCompilerListener(scope, hsListener, runtime);
                compiler.doCompile(task, compilable, listener);
            } finally {
                Heap.getHeap().doReferenceHandling();
                Heap.getHeap().getGC().collectionHint(true);
            }
        } catch (Throwable t) {
            JNIExceptionWrapper.throwInHotSpot(env, t);
        }
    }

    @TruffleToLibGraal(Shutdown)
    @SuppressWarnings({"unused", "try"})
    @CEntryPoint(name = "Java_com_oracle_truffle_runtime_hotspot_libgraal_TruffleToLibGraalCalls_shutdown")
    public static void shutdown(JNIEnv env, JClass hsClazz, @CEntryPoint.IsolateThreadContext long isolateThreadId, long handle) {
        try (JNIMethodScope s = LibGraalUtil.openScope(TruffleToLibGraalEntryPoints.class, Shutdown, env)) {
            HotSpotTruffleCompilerImpl compiler = LibGraalObjectHandles.resolve(handle, HotSpotTruffleCompilerImpl.class);
            compiler.shutdown();
        } catch (Throwable t) {
            JNIExceptionWrapper.throwInHotSpot(env, t);
        }
    }

    @TruffleToLibGraal(InstallTruffleCallBoundaryMethod)
    @SuppressWarnings({"unused", "try"})
    @CEntryPoint(name = "Java_com_oracle_truffle_runtime_hotspot_libgraal_TruffleToLibGraalCalls_installTruffleCallBoundaryMethod")
    public static void installTruffleCallBoundaryMethod(JNIEnv env, JClass hsClazz, @CEntryPoint.IsolateThreadContext long isolateThreadId, long handle, long methodHandle) {
        try (JNIMethodScope s = LibGraalUtil.openScope(TruffleToLibGraalEntryPoints.class, InstallTruffleCallBoundaryMethod, env)) {
            HotSpotTruffleCompilerImpl compiler = LibGraalObjectHandles.resolve(handle, HotSpotTruffleCompilerImpl.class);
            compiler.installTruffleCallBoundaryMethod(LibGraal.unhand(ResolvedJavaMethod.class, methodHandle), null);
        } catch (Throwable t) {
            JNIExceptionWrapper.throwInHotSpot(env, t);
        }
    }

    @TruffleToLibGraal(Id.InstallTruffleReservedOopMethod)
    @SuppressWarnings({"unused", "try"})
    @CEntryPoint(name = "Java_com_oracle_truffle_runtime_hotspot_libgraal_TruffleToLibGraalCalls_installTruffleReservedOopMethod")
    public static void installTruffleReservedOopMethod(JNIEnv env, JClass hsClazz, @CEntryPoint.IsolateThreadContext long isolateThreadId, long handle, long methodHandle) {
        try (JNIMethodScope s = LibGraalUtil.openScope(TruffleToLibGraalEntryPoints.class, Id.InstallTruffleReservedOopMethod, env)) {
            HotSpotTruffleCompilerImpl compiler = LibGraalObjectHandles.resolve(handle, HotSpotTruffleCompilerImpl.class);
            compiler.installTruffleReservedOopMethod(LibGraal.unhand(ResolvedJavaMethod.class, methodHandle), null);
        } catch (Throwable t) {
            JNIExceptionWrapper.throwInHotSpot(env, t);
        }
    }

    @TruffleToLibGraal(PendingTransferToInterpreterOffset)
    @SuppressWarnings("unused")
    @CEntryPoint(name = "Java_com_oracle_truffle_runtime_hotspot_libgraal_TruffleToLibGraalCalls_pendingTransferToInterpreterOffset")
    public static int pendingTransferToInterpreterOffset(JNIEnv env, JClass hsClazz, @CEntryPoint.IsolateThreadContext long isolateThreadId, long handle, JObject hsCompilable) {
        try (JNIMethodScope scope = LibGraalUtil.openScope(TruffleToLibGraalEntryPoints.class, PendingTransferToInterpreterOffset, env)) {
            HotSpotTruffleCompilerImpl compiler = LibGraalObjectHandles.resolve(handle, HotSpotTruffleCompilerImpl.class);
            HSTruffleCompilerRuntime runtime = (HSTruffleCompilerRuntime) compiler.getConfig().runtime();
            TruffleCompilable compilable = new HSTruffleCompilable(scope, hsCompilable, runtime);
            return compiler.pendingTransferToInterpreterOffset(compilable);
        } catch (Throwable t) {
            JNIExceptionWrapper.throwInHotSpot(env, t);
            return 0;
        }
    }

    @TruffleToLibGraal(GetSuppliedString)
    @CEntryPoint(name = "Java_com_oracle_truffle_runtime_hotspot_libgraal_TruffleToLibGraalCalls_getSuppliedString")
    @SuppressWarnings({"unused", "unchecked", "try"})
    public static JString getString(JNIEnv env, JClass hsClazz, @CEntryPoint.IsolateThreadContext long isolateThreadId, long handle) {
        JNIMethodScope scope = LibGraalUtil.openScope(TruffleToLibGraalEntryPoints.class, GetSuppliedString, env);
        try (JNIMethodScope s = scope) {
            Supplier<String> orig = LibGraalObjectHandles.resolve(handle, Supplier.class);
            if (orig != null) {
                String stackTrace = orig.get();
                scope.setObjectResult(JNIUtil.createHSString(env, stackTrace));
            } else {
                scope.setObjectResult(WordFactory.nullPointer());
            }
        } catch (Throwable t) {
            JNIExceptionWrapper.throwInHotSpot(env, t);
            scope.setObjectResult(WordFactory.nullPointer());
        }
        return scope.getObjectResult();
    }

    @TruffleToLibGraal(GetNodeCount)
    @CEntryPoint(name = "Java_com_oracle_truffle_runtime_hotspot_libgraal_TruffleToLibGraalCalls_getNodeCount")
    @SuppressWarnings({"unused", "try"})
    public static int getNodeCount(JNIEnv env, JClass hsClazz, @CEntryPoint.IsolateThreadContext long isolateThreadId, long handle) {
        try (JNIMethodScope s = LibGraalUtil.openScope(TruffleToLibGraalEntryPoints.class, GetNodeCount, env)) {
            GraphInfo orig = LibGraalObjectHandles.resolve(handle, GraphInfo.class);
            return orig.getNodeCount();
        } catch (Throwable t) {
            JNIExceptionWrapper.throwInHotSpot(env, t);
            return 0;
        }
    }

    @TruffleToLibGraal(GetNodeTypes)
    @CEntryPoint(name = "Java_com_oracle_truffle_runtime_hotspot_libgraal_TruffleToLibGraalCalls_getNodeTypes")
    @SuppressWarnings({"unused", "try"})
    public static JObjectArray getNodeTypes(JNIEnv env, JClass hsClazz, @CEntryPoint.IsolateThreadContext long isolateThreadId, long handle, boolean simpleNames) {
        JNIMethodScope scope = LibGraalUtil.openScope(TruffleToLibGraalEntryPoints.class, GetNodeTypes, env);
        try (JNIMethodScope s = scope) {
            GraphInfo orig = LibGraalObjectHandles.resolve(handle, GraphInfo.class);
            String[] nodeTypes = orig.getNodeTypes(simpleNames);
            JClass componentType = getStringClass(env);
            JObjectArray res = NewObjectArray(env, nodeTypes.length, componentType, WordFactory.nullPointer());
            for (int i = 0; i < nodeTypes.length; i++) {
                SetObjectArrayElement(env, res, i, JNIUtil.createHSString(env, nodeTypes[i]));
            }
            scope.setObjectResult(res);
        } catch (Throwable t) {
            JNIExceptionWrapper.throwInHotSpot(env, t);
            scope.setObjectResult(WordFactory.nullPointer());
        }
        return scope.getObjectResult();
    }

    @TruffleToLibGraal(GetTargetCodeSize)
    @CEntryPoint(name = "Java_com_oracle_truffle_runtime_hotspot_libgraal_TruffleToLibGraalCalls_getTargetCodeSize")
    @SuppressWarnings({"unused", "try"})
    public static int getTargetCodeSize(JNIEnv env, JClass hsClazz, @CEntryPoint.IsolateThreadContext long isolateThreadId, long handle) {
        try (JNIMethodScope s = LibGraalUtil.openScope(TruffleToLibGraalEntryPoints.class, GetTargetCodeSize, env)) {
            return LibGraalObjectHandles.resolve(handle, CompilationResultInfo.class).getTargetCodeSize();
        } catch (Throwable t) {
            JNIExceptionWrapper.throwInHotSpot(env, t);
            return 0;
        }
    }

    @TruffleToLibGraal(GetTotalFrameSize)
    @CEntryPoint(name = "Java_com_oracle_truffle_runtime_hotspot_libgraal_TruffleToLibGraalCalls_getTotalFrameSize")
    @SuppressWarnings({"unused", "try"})
    public static int getTotalFrameSize(JNIEnv env, JClass hsClazz, @CEntryPoint.IsolateThreadContext long isolateThreadId, long handle) {
        try (JNIMethodScope s = LibGraalUtil.openScope(TruffleToLibGraalEntryPoints.class, GetTotalFrameSize, env)) {
            return LibGraalObjectHandles.resolve(handle, CompilationResultInfo.class).getTotalFrameSize();
        } catch (Throwable t) {
            JNIExceptionWrapper.throwInHotSpot(env, t);
            return 0;
        }
    }

    @TruffleToLibGraal(GetExceptionHandlersCount)
    @CEntryPoint(name = "Java_com_oracle_truffle_runtime_hotspot_libgraal_TruffleToLibGraalCalls_getExceptionHandlersCount")
    @SuppressWarnings({"unused", "try"})
    public static int getExceptionHandlersCount(JNIEnv env, JClass hsClazz, @CEntryPoint.IsolateThreadContext long isolateThreadId, long handle) {
        try (JNIMethodScope s = LibGraalUtil.openScope(TruffleToLibGraalEntryPoints.class, GetExceptionHandlersCount, env)) {
            return LibGraalObjectHandles.resolve(handle, CompilationResultInfo.class).getExceptionHandlersCount();
        } catch (Throwable t) {
            JNIExceptionWrapper.throwInHotSpot(env, t);
            return 0;
        }
    }

    @TruffleToLibGraal(GetInfopointsCount)
    @CEntryPoint(name = "Java_com_oracle_truffle_runtime_hotspot_libgraal_TruffleToLibGraalCalls_getInfopointsCount")
    @SuppressWarnings({"unused", "try"})
    public static int getInfopointsCount(JNIEnv env, JClass hsClazz, @CEntryPoint.IsolateThreadContext long isolateThreadId, long handle) {
        try (JNIMethodScope s = LibGraalUtil.openScope(TruffleToLibGraalEntryPoints.class, GetInfopointsCount, env)) {
            return LibGraalObjectHandles.resolve(handle, CompilationResultInfo.class).getInfopointsCount();
        } catch (Throwable t) {
            JNIExceptionWrapper.throwInHotSpot(env, t);
            return 0;
        }
    }

    @TruffleToLibGraal(GetInfopoints)
    @CEntryPoint(name = "Java_com_oracle_truffle_runtime_hotspot_libgraal_TruffleToLibGraalCalls_getInfopoints")
    @SuppressWarnings({"unused", "try"})
    public static JObjectArray getInfopoints(JNIEnv env, JClass hsClazz, @CEntryPoint.IsolateThreadContext long isolateThreadId, long handle) {
        JNIMethodScope scope = LibGraalUtil.openScope(TruffleToLibGraalEntryPoints.class, GetInfopoints, env);
        try (JNIMethodScope s = scope) {
            String[] infoPoints = LibGraalObjectHandles.resolve(handle, CompilationResultInfo.class).getInfopoints();
            JClass componentType = getStringClass(env);
            JObjectArray res = NewObjectArray(env, infoPoints.length, componentType, WordFactory.nullPointer());
            for (int i = 0; i < infoPoints.length; i++) {
                SetObjectArrayElement(env, res, i, createHSString(env, infoPoints[i]));
            }
            scope.setObjectResult(res);
        } catch (Throwable t) {
            JNIExceptionWrapper.throwInHotSpot(env, t);
            scope.setObjectResult(WordFactory.nullPointer());
        }
        return scope.getObjectResult();
    }

    @TruffleToLibGraal(Id.ListCompilerOptions)
    @CEntryPoint(name = "Java_com_oracle_truffle_runtime_hotspot_libgraal_TruffleToLibGraalCalls_listCompilerOptions")
    @SuppressWarnings({"unused", "try"})
    public static JByteArray listCompilerOptions(JNIEnv env, JClass hsClazz, @CEntryPoint.IsolateThreadContext long isolateThreadId) {
        JNIMethodScope scope = LibGraalUtil.openScope(TruffleToLibGraalEntryPoints.class, Id.ListCompilerOptions, env);
        try (JNIMethodScope s = scope) {
            TruffleCompilerOptionDescriptor[] options = TruffleCompilerOptions.listOptions();
            ByteArrayBinaryOutput out = BinaryOutput.create();

            out.writeInt(options.length);
            for (int i = 0; i < options.length; i++) {
                TruffleCompilerOptionDescriptor descriptor = options[i];
                out.writeUTF(descriptor.name());
                out.writeInt(descriptor.type().ordinal());
                out.writeBoolean(descriptor.deprecated());
                out.writeUTF(descriptor.help());
                out.writeUTF(descriptor.deprecationMessage());
            }

            JByteArray res = JNIUtil.createHSArray(env, out.getArray());
            scope.setObjectResult(res);

        } catch (Throwable t) {
            JNIExceptionWrapper.throwInHotSpot(env, t);
            scope.setObjectResult(WordFactory.nullPointer());
        }
        return scope.getObjectResult();
    }

    @TruffleToLibGraal(Id.CompilerOptionExists)
    @CEntryPoint(name = "Java_com_oracle_truffle_runtime_hotspot_libgraal_TruffleToLibGraalCalls_compilerOptionExists")
    @SuppressWarnings({"unused", "try"})
    public static boolean existsCompilerOption(JNIEnv env, JClass hsClazz, @CEntryPoint.IsolateThreadContext long isolateThreadId, JString optionName) {
        JNIMethodScope scope = LibGraalUtil.openScope(TruffleToLibGraalEntryPoints.class, Id.CompilerOptionExists, env);
        try (JNIMethodScope s = scope) {
            return TruffleCompilerOptions.optionExists(JNIUtil.createString(env, optionName));
        } catch (Throwable t) {
            JNIExceptionWrapper.throwInHotSpot(env, t);
            return false;
        }
    }

    @TruffleToLibGraal(Id.ValidateCompilerOption)
    @CEntryPoint(name = "Java_com_oracle_truffle_runtime_hotspot_libgraal_TruffleToLibGraalCalls_validateCompilerOption")
    @SuppressWarnings({"unused", "try"})
    public static JString validateCompilerOption(JNIEnv env, JClass hsClazz, @CEntryPoint.IsolateThreadContext long isolateThreadId, JString optionName, JString optionValue) {
        JNIMethodScope scope = LibGraalUtil.openScope(TruffleToLibGraalEntryPoints.class, Id.ValidateCompilerOption, env);
        try (JNIMethodScope s = scope) {
            scope.setObjectResult(JNIUtil.createHSString(env, TruffleCompilerOptions.validateOption(JNIUtil.createString(env, optionName), JNIUtil.createString(env, optionValue))));
        } catch (Throwable t) {
            JNIExceptionWrapper.throwInHotSpot(env, t);
            scope.setObjectResult(WordFactory.nullPointer());
        }
        return scope.getObjectResult();
    }

    @TruffleToLibGraal(GetMarksCount)
    @CEntryPoint(name = "Java_com_oracle_truffle_runtime_hotspot_libgraal_TruffleToLibGraalCalls_getMarksCount")
    @SuppressWarnings({"unused", "try"})
    public static int getMarksCount(JNIEnv env, JClass hsClazz, @CEntryPoint.IsolateThreadContext long isolateThreadId, long handle) {
        try (JNIMethodScope s = LibGraalUtil.openScope(TruffleToLibGraalEntryPoints.class, GetMarksCount, env)) {
            return LibGraalObjectHandles.resolve(handle, CompilationResultInfo.class).getMarksCount();
        } catch (Throwable t) {
            JNIExceptionWrapper.throwInHotSpot(env, t);
            return 0;
        }
    }

    @TruffleToLibGraal(GetDataPatchesCount)
    @CEntryPoint(name = "Java_com_oracle_truffle_runtime_hotspot_libgraal_TruffleToLibGraalCalls_getDataPatchesCount")
    @SuppressWarnings({"unused", "try"})
    public static int getDataPatchesCount(JNIEnv env, JClass hsClazz, @CEntryPoint.IsolateThreadContext long isolateThreadId, long handle) {
        try (JNIMethodScope s = LibGraalUtil.openScope(TruffleToLibGraalEntryPoints.class, GetDataPatchesCount, env)) {
            return LibGraalObjectHandles.resolve(handle, CompilationResultInfo.class).getDataPatchesCount();
        } catch (Throwable t) {
            JNIExceptionWrapper.throwInHotSpot(env, t);
            return 0;
        }
    }

    @TruffleToLibGraal(PurgePartialEvaluationCaches)
    @CEntryPoint(name = "Java_com_oracle_truffle_runtime_hotspot_libgraal_TruffleToLibGraalCalls_purgePartialEvaluationCaches")
    @SuppressWarnings({"unused", "try"})
    public static void purgePartialEvaluationCaches(JNIEnv env, JClass hsClass, @CEntryPoint.IsolateThreadContext long isolateThreadId, long compilerHandle) {
        try (JNIMethodScope s = LibGraalUtil.openScope(TruffleToLibGraalEntryPoints.class, PurgePartialEvaluationCaches, env)) {
            HotSpotTruffleCompilerImpl compiler = LibGraalObjectHandles.resolve(compilerHandle, HotSpotTruffleCompilerImpl.class);
            if (compiler != null) {
                compiler.purgePartialEvaluationCaches();
            }
        } catch (Throwable t) {
            JNIExceptionWrapper.throwInHotSpot(env, t);
        }
    }

    @TruffleToLibGraal(GetCompilerVersion)
    @CEntryPoint(name = "Java_com_oracle_truffle_runtime_hotspot_libgraal_TruffleToLibGraalCalls_getCompilerVersion")
    @SuppressWarnings({"unused", "try"})
    public static JString getCompilerVersion(JNIEnv env, JClass hsClass, @CEntryPoint.IsolateThreadContext long isolateThreadId) {
        JNIMethodScope scope = LibGraalUtil.openScope(TruffleToLibGraalEntryPoints.class, GetCompilerVersion, env);
        try (JNIMethodScope s = scope) {
            scope.setObjectResult(createHSString(env, COMPILER_VERSION));
        } catch (Throwable t) {
            JNIExceptionWrapper.throwInHotSpot(env, t);
            scope.setObjectResult(WordFactory.nullPointer());
        }
        return scope.getObjectResult();
    }

    static {
        Class<?> callsClass;
        try {
            callsClass = Class.forName("com.oracle.truffle.runtime.hotspot.libgraal.TruffleToLibGraalCalls");
            LibGraalChecker.checkToLibGraalCalls(TruffleToLibGraalEntryPoints.class, callsClass, TruffleToLibGraal.class);
        } catch (ClassNotFoundException e) {
            /*
             * Truffle might not be on the class path if libgraal is built. If it is, we should
             * validate the usage.
             */
        }
    }

}
