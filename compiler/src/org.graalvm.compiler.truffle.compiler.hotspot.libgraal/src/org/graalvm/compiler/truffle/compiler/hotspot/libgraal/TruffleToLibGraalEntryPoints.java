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
package org.graalvm.compiler.truffle.compiler.hotspot.libgraal;

import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleToLibGraal.Id.CloseCompilation;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleToLibGraal.Id.CloseDebugContext;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleToLibGraal.Id.CloseDebugContextScope;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleToLibGraal.Id.DoCompile;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleToLibGraal.Id.DumpChannelClose;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleToLibGraal.Id.DumpChannelWrite;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleToLibGraal.Id.GetCompilerConfigurationFactoryName;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleToLibGraal.Id.GetCompilerConfigurationName;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleToLibGraal.Id.GetDataPatchesCount;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleToLibGraal.Id.GetDumpChannel;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleToLibGraal.Id.GetExceptionHandlersCount;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleToLibGraal.Id.GetExecutionID;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleToLibGraal.Id.GetGraphDumpDirectory;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleToLibGraal.Id.GetInfopoints;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleToLibGraal.Id.GetInfopointsCount;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleToLibGraal.Id.GetMarksCount;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleToLibGraal.Id.GetNodeCount;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleToLibGraal.Id.GetNodeTypes;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleToLibGraal.Id.GetSuppliedString;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleToLibGraal.Id.GetTargetCodeSize;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleToLibGraal.Id.GetTotalFrameSize;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleToLibGraal.Id.GetTruffleCompilationId;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleToLibGraal.Id.GetTruffleCompilationTruffleAST;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleToLibGraal.Id.GetVersionProperties;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleToLibGraal.Id.InitializeCompiler;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleToLibGraal.Id.InitializeRuntime;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleToLibGraal.Id.InstallTruffleCallBoundaryMethod;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleToLibGraal.Id.IsBasicDumpEnabled;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleToLibGraal.Id.IsDumpChannelOpen;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleToLibGraal.Id.IsPrintGraphEnabled;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleToLibGraal.Id.NewCompiler;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleToLibGraal.Id.OpenCompilation;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleToLibGraal.Id.OpenDebugContext;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleToLibGraal.Id.OpenDebugContextScope;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleToLibGraal.Id.PendingTransferToInterpreterOffset;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleToLibGraal.Id.PurgePartialEvaluationCaches;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleToLibGraal.Id.Shutdown;
import static org.graalvm.jniutils.JNIUtil.GetArrayLength;
import static org.graalvm.jniutils.JNIUtil.GetByteArrayElements;
import static org.graalvm.jniutils.JNIUtil.NewByteArray;
import static org.graalvm.jniutils.JNIUtil.NewObjectArray;
import static org.graalvm.jniutils.JNIUtil.ReleaseByteArrayElements;
import static org.graalvm.jniutils.JNIUtil.SetObjectArrayElement;
import static org.graalvm.jniutils.JNIUtil.createHSString;
import static org.graalvm.jniutils.JNIUtil.createString;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

import org.graalvm.compiler.core.common.CompilationIdentifier;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.debug.DebugOptions;
import org.graalvm.compiler.hotspot.CompilationContext;
import org.graalvm.compiler.hotspot.CompilerConfigurationFactory;
import org.graalvm.compiler.hotspot.HotSpotGraalOptionValues;
import org.graalvm.compiler.hotspot.HotSpotGraalServices;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.serviceprovider.GraalServices;
import org.graalvm.compiler.truffle.common.CompilableTruffleAST;
import org.graalvm.compiler.truffle.common.TruffleCompilation;
import org.graalvm.compiler.truffle.common.TruffleCompilationTask;
import org.graalvm.compiler.truffle.common.TruffleCompilerListener;
import org.graalvm.compiler.truffle.common.TruffleCompilerListener.CompilationResultInfo;
import org.graalvm.compiler.truffle.common.TruffleCompilerListener.GraphInfo;
import org.graalvm.compiler.truffle.common.TruffleCompilerRuntime;
import org.graalvm.compiler.truffle.common.TruffleCompilerRuntimeInstance;
import org.graalvm.compiler.truffle.common.TruffleDebugContext;
import org.graalvm.compiler.truffle.common.TruffleDebugJavaMethod;
import org.graalvm.compiler.truffle.common.VoidGraphStructure;
import org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleToLibGraal;
import org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleToLibGraal.Id;
import org.graalvm.compiler.truffle.compiler.TruffleCompilationIdentifier;
import org.graalvm.compiler.truffle.compiler.TruffleDebugContextImpl;
import org.graalvm.compiler.truffle.compiler.hotspot.HotSpotTruffleCompilerImpl;
import org.graalvm.compiler.truffle.compiler.hotspot.HotSpotTruffleCompilerImpl.Options;
import org.graalvm.graphio.GraphOutput;
import org.graalvm.jniutils.HSObject;
import org.graalvm.jniutils.JNI.JArray;
import org.graalvm.jniutils.JNI.JByteArray;
import org.graalvm.jniutils.JNI.JClass;
import org.graalvm.jniutils.JNI.JNIEnv;
import org.graalvm.jniutils.JNI.JObject;
import org.graalvm.jniutils.JNI.JObjectArray;
import org.graalvm.jniutils.JNI.JString;
import org.graalvm.jniutils.JNIExceptionWrapper;
import org.graalvm.jniutils.JNIMethodScope;
import org.graalvm.jniutils.JNIUtil;
import org.graalvm.libgraal.LibGraal;
import org.graalvm.libgraal.jni.FromLibGraalCalls;
import org.graalvm.libgraal.jni.LibGraalUtil;
import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CTypeConversion;
import org.graalvm.nativeimage.c.type.VoidPointer;
import org.graalvm.util.OptionsEncoder;
import org.graalvm.word.WordFactory;

import jdk.vm.ci.hotspot.HotSpotJVMCIRuntime;
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

    @TruffleToLibGraal(InitializeRuntime)
    @SuppressWarnings({"unused", "try"})
    @CEntryPoint(name = "Java_org_graalvm_compiler_truffle_runtime_hotspot_libgraal_TruffleToLibGraalCalls_initializeRuntime")
    public static long initializeRuntime(JNIEnv env, JClass hsClazz, @CEntryPoint.IsolateThreadContext long isolateThreadId,
                    JObject truffleRuntime, long classLoaderDelegateId) {
        try (JNIMethodScope s = LibGraalUtil.openScope(TruffleToLibGraalEntryPoints.class, InitializeRuntime, env)) {
            ResolvedJavaType classLoaderDelegate = LibGraal.unhand(ResolvedJavaType.class, classLoaderDelegateId);
            HSTruffleCompilerRuntime hsTruffleRuntime = new HSTruffleCompilerRuntime(env, truffleRuntime, classLoaderDelegate, HotSpotGraalOptionValues.defaultOptions());
            TruffleCompilerRuntimeInstance.initialize(hsTruffleRuntime);
            long truffleRuntimeHandle = LibGraalObjectHandles.create(hsTruffleRuntime);
            return truffleRuntimeHandle;
        } catch (Throwable t) {
            JNIExceptionWrapper.throwInHotSpot(env, t);
            return 0L;
        }
    }

    @TruffleToLibGraal(NewCompiler)
    @SuppressWarnings({"unused", "try"})
    @CEntryPoint(name = "Java_org_graalvm_compiler_truffle_runtime_hotspot_libgraal_TruffleToLibGraalCalls_newCompiler")
    public static long newCompiler(JNIEnv env, JClass hsClazz, @CEntryPoint.IsolateThreadContext long isolateThreadId, long truffleRuntimeHandle) {
        try (JNIMethodScope s = LibGraalUtil.openScope(TruffleToLibGraalEntryPoints.class, NewCompiler, env)) {
            HSTruffleCompilerRuntime hsTruffleRuntime = LibGraalObjectHandles.resolve(truffleRuntimeHandle, HSTruffleCompilerRuntime.class);
            return LibGraalObjectHandles.create(HotSpotTruffleCompilerImpl.create(hsTruffleRuntime));
        } catch (Throwable t) {
            JNIExceptionWrapper.throwInHotSpot(env, t);
            return 0;
        }
    }

    @TruffleToLibGraal(InitializeCompiler)
    @SuppressWarnings("unused")
    @CEntryPoint(name = "Java_org_graalvm_compiler_truffle_runtime_hotspot_libgraal_TruffleToLibGraalCalls_initializeCompiler")
    public static void initializeCompiler(JNIEnv env, JClass hsClazz, @CEntryPoint.IsolateThreadContext long isolateThreadId, long compilerHandle, JByteArray hsOptions, JObject hsCompilable,
                    boolean firstInitialization) {
        try (JNIMethodScope s = LibGraalUtil.openScope(TruffleToLibGraalEntryPoints.class, InitializeCompiler, env)) {
            HotSpotTruffleCompilerImpl compiler = LibGraalObjectHandles.resolve(compilerHandle, HotSpotTruffleCompilerImpl.class);
            Map<String, Object> options = decodeOptions(env, hsOptions);
            CompilableTruffleAST compilable = new HSCompilableTruffleAST(s, hsCompilable);
            compiler.initialize(options, compilable, firstInitialization);
        } catch (Throwable t) {
            JNIExceptionWrapper.throwInHotSpot(env, t);
        }
    }

    @TruffleToLibGraal(GetCompilerConfigurationFactoryName)
    @SuppressWarnings({"unused", "try"})
    @CEntryPoint(name = "Java_org_graalvm_compiler_truffle_runtime_hotspot_libgraal_TruffleToLibGraalCalls_getCompilerConfigurationFactoryName")
    public static JString getCompilerConfigurationFactoryName(JNIEnv env, JClass hsClazz, @CEntryPoint.IsolateThreadContext long isolateThreadId, long truffleRuntimeHandle) {
        JNIMethodScope scope = LibGraalUtil.openScope(TruffleToLibGraalEntryPoints.class, GetCompilerConfigurationFactoryName, env);
        try (JNIMethodScope s = scope) {
            HSTruffleCompilerRuntime hsTruffleRuntime = LibGraalObjectHandles.resolve(truffleRuntimeHandle, HSTruffleCompilerRuntime.class);
            assert TruffleCompilerRuntime.getRuntime() == hsTruffleRuntime;
            OptionValues graalOptions = hsTruffleRuntime.getGraalOptions(OptionValues.class);
            String compConfig = Options.TruffleCompilerConfiguration.getValue(graalOptions);
            CompilerConfigurationFactory compilerConfigurationFactory = CompilerConfigurationFactory.selectFactory(compConfig, graalOptions, HotSpotJVMCIRuntime.runtime());
            String name = compilerConfigurationFactory.getName();
            scope.setObjectResult(createHSString(env, name));
        } catch (Throwable t) {
            JNIExceptionWrapper.throwInHotSpot(env, t);
            scope.setObjectResult(WordFactory.nullPointer());
        }
        return scope.getObjectResult();
    }

    @TruffleToLibGraal(OpenCompilation)
    @SuppressWarnings({"unused", "try"})
    @CEntryPoint(name = "Java_org_graalvm_compiler_truffle_runtime_hotspot_libgraal_TruffleToLibGraalCalls_openCompilation")
    public static long openCompilation(JNIEnv env, JClass hsClazz, @CEntryPoint.IsolateThreadContext long isolateThreadId, long handle, JObject hsCompilable) {
        JNIMethodScope scope = LibGraalUtil.openScope(TruffleToLibGraalEntryPoints.class, OpenCompilation, env);
        try (JNIMethodScope s = scope) {
            HotSpotTruffleCompilerImpl compiler = LibGraalObjectHandles.resolve(handle, HotSpotTruffleCompilerImpl.class);
            CompilableTruffleAST compilable = new HSCompilableTruffleAST(env, hsCompilable);
            TruffleCompilation compilation = compiler.openCompilation(compilable);
            assert compilation instanceof TruffleCompilationIdentifier;
            return LibGraalObjectHandles.create(compilation);
        } catch (Throwable t) {
            JNIExceptionWrapper.throwInHotSpot(env, t);
            return 0;
        }
    }

    @TruffleToLibGraal(GetCompilerConfigurationName)
    @SuppressWarnings({"unused", "try"})
    @CEntryPoint(name = "Java_org_graalvm_compiler_truffle_runtime_hotspot_libgraal_TruffleToLibGraalCalls_getCompilerConfigurationName")
    public static JString getCompilerConfigurationName(JNIEnv env, JClass hsClazz, @CEntryPoint.IsolateThreadContext long isolateId, long handle) {
        JNIMethodScope scope = LibGraalUtil.openScope(TruffleToLibGraalEntryPoints.class, GetCompilerConfigurationName, env);
        try (JNIMethodScope s = scope) {
            HotSpotTruffleCompilerImpl compiler = LibGraalObjectHandles.resolve(handle, HotSpotTruffleCompilerImpl.class);
            String name = compiler.getCompilerConfigurationName();
            scope.setObjectResult(createHSString(env, name));
        } catch (Throwable t) {
            JNIExceptionWrapper.throwInHotSpot(env, t);
            scope.setObjectResult(WordFactory.nullPointer());
        }
        return scope.getObjectResult();
    }

    @TruffleToLibGraal(DoCompile)
    @SuppressWarnings({"unused", "try"})
    @CEntryPoint(name = "Java_org_graalvm_compiler_truffle_runtime_hotspot_libgraal_TruffleToLibGraalCalls_doCompile")
    public static void doCompile(JNIEnv env,
                    JClass hsClazz,
                    @CEntryPoint.IsolateThreadContext long isolateThreadId,
                    long compilerHandle,
                    long debugContextHandle,
                    long compilationHandle,
                    JByteArray hsOptions,
                    JObject hsTask,
                    JObject hsListener) {
        try (JNIMethodScope scope = LibGraalUtil.openScope(TruffleToLibGraalEntryPoints.class, DoCompile, env)) {
            TruffleCompilationIdentifier compilation = LibGraalObjectHandles.resolve(compilationHandle, TruffleCompilationIdentifier.class);
            try (CompilationContext hotSpotObjectConstantScope = HotSpotGraalServices.openLocalCompilationContext(compilation)) {
                HotSpotTruffleCompilerImpl compiler = LibGraalObjectHandles.resolve(compilerHandle, HotSpotTruffleCompilerImpl.class);
                TruffleDebugContext debugContext = LibGraalObjectHandles.resolve(debugContextHandle, TruffleDebugContext.class);
                Map<String, Object> options = decodeOptions(env, hsOptions);
                TruffleCompilationTask task = hsTask.isNull() ? null : new HSTruffleCompilationTask(hsTask);
                TruffleCompilerListener listener = hsListener.isNull() ? null : new HSTruffleCompilerListener(scope, hsListener);
                compiler.doCompile(debugContext, compilation, options, task, listener);
            }
        } catch (Throwable t) {
            JNIExceptionWrapper.throwInHotSpot(env, t);
        }
    }

    @TruffleToLibGraal(CloseCompilation)
    @SuppressWarnings({"unused", "try"})
    @CEntryPoint(name = "Java_org_graalvm_compiler_truffle_runtime_hotspot_libgraal_TruffleToLibGraalCalls_closeCompilation")
    public static void closeCompilation(JNIEnv env, JClass hsClazz, @CEntryPoint.IsolateThreadContext long isolateThreadId, long compilationHandle) {
        try (JNIMethodScope scope = LibGraalUtil.openScope(TruffleToLibGraalEntryPoints.class, CloseCompilation, env)) {
            TruffleCompilation compilation = LibGraalObjectHandles.resolve(compilationHandle, TruffleCompilation.class);
            HSCompilableTruffleAST compilable = (HSCompilableTruffleAST) compilation.getCompilable();
            try {
                compilation.close();
            } finally {
                compilable.release(env);
                HSObject.cleanHandles(env);
                doReferenceHandling();
            }
        } catch (Throwable t) {
            JNIExceptionWrapper.throwInHotSpot(env, t);
        }
    }

    private static void doReferenceHandling() {
        // Substituted in LibGraalFeature.
    }

    @TruffleToLibGraal(Shutdown)
    @SuppressWarnings({"unused", "try"})
    @CEntryPoint(name = "Java_org_graalvm_compiler_truffle_runtime_hotspot_libgraal_TruffleToLibGraalCalls_shutdown")
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
    @CEntryPoint(name = "Java_org_graalvm_compiler_truffle_runtime_hotspot_libgraal_TruffleToLibGraalCalls_installTruffleCallBoundaryMethod")
    public static void installTruffleCallBoundaryMethod(JNIEnv env, JClass hsClazz, @CEntryPoint.IsolateThreadContext long isolateThreadId, long handle, long methodHandle) {
        try (JNIMethodScope s = LibGraalUtil.openScope(TruffleToLibGraalEntryPoints.class, InstallTruffleCallBoundaryMethod, env)) {
            HotSpotTruffleCompilerImpl compiler = LibGraalObjectHandles.resolve(handle, HotSpotTruffleCompilerImpl.class);
            compiler.installTruffleCallBoundaryMethod(LibGraal.unhand(ResolvedJavaMethod.class, methodHandle));
        } catch (Throwable t) {
            JNIExceptionWrapper.throwInHotSpot(env, t);
        }
    }

    @TruffleToLibGraal(Id.InstallTruffleReservedOopMethod)
    @SuppressWarnings({"unused", "try"})
    @CEntryPoint(name = "Java_org_graalvm_compiler_truffle_runtime_hotspot_libgraal_TruffleToLibGraalCalls_installTruffleReservedOopMethod")
    public static void installTruffleReservedOopMethod(JNIEnv env, JClass hsClazz, @CEntryPoint.IsolateThreadContext long isolateThreadId, long handle, long methodHandle) {
        try (JNIMethodScope s = LibGraalUtil.openScope(TruffleToLibGraalEntryPoints.class, Id.InstallTruffleReservedOopMethod, env)) {
            HotSpotTruffleCompilerImpl compiler = LibGraalObjectHandles.resolve(handle, HotSpotTruffleCompilerImpl.class);
            compiler.installTruffleReservedOopMethod(LibGraal.unhand(ResolvedJavaMethod.class, methodHandle));
        } catch (Throwable t) {
            JNIExceptionWrapper.throwInHotSpot(env, t);
        }
    }

    @TruffleToLibGraal(PendingTransferToInterpreterOffset)
    @SuppressWarnings("unused")
    @CEntryPoint(name = "Java_org_graalvm_compiler_truffle_runtime_hotspot_libgraal_TruffleToLibGraalCalls_pendingTransferToInterpreterOffset")
    public static int pendingTransferToInterpreterOffset(JNIEnv env, JClass hsClazz, @CEntryPoint.IsolateThreadContext long isolateThreadId, long handle, JObject hsCompilable) {
        try (JNIMethodScope scope = LibGraalUtil.openScope(TruffleToLibGraalEntryPoints.class, PendingTransferToInterpreterOffset, env)) {
            HotSpotTruffleCompilerImpl compiler = LibGraalObjectHandles.resolve(handle, HotSpotTruffleCompilerImpl.class);
            CompilableTruffleAST compilable = new HSCompilableTruffleAST(scope, hsCompilable);
            return compiler.pendingTransferToInterpreterOffset(compilable);
        } catch (Throwable t) {
            JNIExceptionWrapper.throwInHotSpot(env, t);
            return 0;
        }
    }

    @TruffleToLibGraal(GetGraphDumpDirectory)
    @SuppressWarnings({"unused", "try"})
    @CEntryPoint(name = "Java_org_graalvm_compiler_truffle_runtime_hotspot_libgraal_TruffleToLibGraalCalls_getGraphDumpDirectory")
    public static JString getGraphDumpDirectory(JNIEnv env, JClass hsClazz, @CEntryPoint.IsolateThreadContext long isolateThreadId) {
        JNIMethodScope scope = LibGraalUtil.openScope(TruffleToLibGraalEntryPoints.class, GetGraphDumpDirectory, env);
        try (JNIMethodScope s = scope) {
            String path = DebugOptions.getDumpDirectory(HotSpotGraalOptionValues.defaultOptions());
            scope.setObjectResult(createHSString(env, path));
        } catch (IOException ioe) {
            scope.setObjectResult(WordFactory.nullPointer());
        } catch (Throwable t) {
            JNIExceptionWrapper.throwInHotSpot(env, t);
            scope.setObjectResult(WordFactory.nullPointer());
        }
        return scope.getObjectResult();
    }

    @TruffleToLibGraal(IsPrintGraphEnabled)
    @SuppressWarnings({"unused", "try"})
    @CEntryPoint(name = "Java_org_graalvm_compiler_truffle_runtime_hotspot_libgraal_TruffleToLibGraalCalls_isPrintGraphEnabled")
    public static boolean isPrintGraphEnabled(JNIEnv env, JClass hsClazz, @CEntryPoint.IsolateThreadContext long isolateThreadId, long truffleRuntimeHandle) {
        try (JNIMethodScope scope = LibGraalUtil.openScope(TruffleToLibGraalEntryPoints.class, IsPrintGraphEnabled, env)) {
            HSTruffleCompilerRuntime hsTruffleRuntime = LibGraalObjectHandles.resolve(truffleRuntimeHandle, HSTruffleCompilerRuntime.class);
            assert TruffleCompilerRuntime.getRuntime() == hsTruffleRuntime;
            OptionValues graalOptions = hsTruffleRuntime.getGraalOptions(OptionValues.class);
            return DebugOptions.PrintGraph.getValue(graalOptions) != DebugOptions.PrintGraphTarget.Disable;
        } catch (Throwable t) {
            JNIExceptionWrapper.throwInHotSpot(env, t);
            return false;
        }
    }

    private static Map<String, Object> decodeOptions(JNIEnv env, JByteArray hsOptions) {
        Map<String, Object> options;
        int len = GetArrayLength(env, hsOptions);
        CCharPointer optionsCPointer = GetByteArrayElements(env, hsOptions, WordFactory.nullPointer());
        try {
            byte[] optionsBuffer = new byte[len];
            CTypeConversion.asByteBuffer(optionsCPointer, len).get(optionsBuffer);
            options = OptionsEncoder.decode(optionsBuffer);
        } finally {
            ReleaseByteArrayElements(env, hsOptions, optionsCPointer, JArray.MODE_RELEASE);
        }
        return options;
    }

    @TruffleToLibGraal(GetSuppliedString)
    @CEntryPoint(name = "Java_org_graalvm_compiler_truffle_runtime_hotspot_libgraal_TruffleToLibGraalCalls_getSuppliedString")
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
    @CEntryPoint(name = "Java_org_graalvm_compiler_truffle_runtime_hotspot_libgraal_TruffleToLibGraalCalls_getNodeCount")
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
    @CEntryPoint(name = "Java_org_graalvm_compiler_truffle_runtime_hotspot_libgraal_TruffleToLibGraalCalls_getNodeTypes")
    @SuppressWarnings({"unused", "try"})
    public static JObjectArray getNodeTypes(JNIEnv env, JClass hsClazz, @CEntryPoint.IsolateThreadContext long isolateThreadId, long handle, boolean simpleNames) {
        JNIMethodScope scope = LibGraalUtil.openScope(TruffleToLibGraalEntryPoints.class, GetNodeTypes, env);
        try (JNIMethodScope s = scope) {
            GraphInfo orig = LibGraalObjectHandles.resolve(handle, GraphInfo.class);
            String[] nodeTypes = orig.getNodeTypes(simpleNames);
            JClass componentType = FromLibGraalCalls.getJNIClass(env, String.class);
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
    @CEntryPoint(name = "Java_org_graalvm_compiler_truffle_runtime_hotspot_libgraal_TruffleToLibGraalCalls_getTargetCodeSize")
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
    @CEntryPoint(name = "Java_org_graalvm_compiler_truffle_runtime_hotspot_libgraal_TruffleToLibGraalCalls_getTotalFrameSize")
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
    @CEntryPoint(name = "Java_org_graalvm_compiler_truffle_runtime_hotspot_libgraal_TruffleToLibGraalCalls_getExceptionHandlersCount")
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
    @CEntryPoint(name = "Java_org_graalvm_compiler_truffle_runtime_hotspot_libgraal_TruffleToLibGraalCalls_getInfopointsCount")
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
    @CEntryPoint(name = "Java_org_graalvm_compiler_truffle_runtime_hotspot_libgraal_TruffleToLibGraalCalls_getInfopoints")
    @SuppressWarnings({"unused", "try"})
    public static JObjectArray getInfopoints(JNIEnv env, JClass hsClazz, @CEntryPoint.IsolateThreadContext long isolateThreadId, long handle) {
        JNIMethodScope scope = LibGraalUtil.openScope(TruffleToLibGraalEntryPoints.class, GetInfopoints, env);
        try (JNIMethodScope s = scope) {
            String[] infoPoints = LibGraalObjectHandles.resolve(handle, CompilationResultInfo.class).getInfopoints();
            JClass componentType = FromLibGraalCalls.getJNIClass(env, String.class);
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

    @TruffleToLibGraal(GetMarksCount)
    @CEntryPoint(name = "Java_org_graalvm_compiler_truffle_runtime_hotspot_libgraal_TruffleToLibGraalCalls_getMarksCount")
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
    @CEntryPoint(name = "Java_org_graalvm_compiler_truffle_runtime_hotspot_libgraal_TruffleToLibGraalCalls_getDataPatchesCount")
    @SuppressWarnings({"unused", "try"})
    public static int getDataPatchesCount(JNIEnv env, JClass hsClazz, @CEntryPoint.IsolateThreadContext long isolateThreadId, long handle) {
        try (JNIMethodScope s = LibGraalUtil.openScope(TruffleToLibGraalEntryPoints.class, GetDataPatchesCount, env)) {
            return LibGraalObjectHandles.resolve(handle, CompilationResultInfo.class).getDataPatchesCount();
        } catch (Throwable t) {
            JNIExceptionWrapper.throwInHotSpot(env, t);
            return 0;
        }
    }

    @TruffleToLibGraal(OpenDebugContext)
    @CEntryPoint(name = "Java_org_graalvm_compiler_truffle_runtime_hotspot_libgraal_TruffleToLibGraalCalls_openDebugContext")
    @SuppressWarnings({"unused", "try"})
    public static long openDebugContext(JNIEnv env,
                    JClass hsClazz,
                    @CEntryPoint.IsolateThreadContext long isolateThreadId,
                    long compilerHandle,
                    long compilationHandle,
                    JByteArray hsOptions) {
        try (JNIMethodScope s = LibGraalUtil.openScope(TruffleToLibGraalEntryPoints.class, OpenDebugContext, env)) {
            HotSpotTruffleCompilerImpl compiler = LibGraalObjectHandles.resolve(compilerHandle, HotSpotTruffleCompilerImpl.class);
            TruffleCompilation compilation = LibGraalObjectHandles.resolve(compilationHandle, TruffleCompilation.class);
            Map<String, Object> options = decodeOptions(env, hsOptions);
            TruffleDebugContext debugContext = compiler.openDebugContext(options, compilation);
            long handle = LibGraalObjectHandles.create(debugContext);
            return handle;
        } catch (Throwable t) {
            JNIExceptionWrapper.throwInHotSpot(env, t);
            return 0;
        }
    }

    @TruffleToLibGraal(OpenDebugContextScope)
    @CEntryPoint(name = "Java_org_graalvm_compiler_truffle_runtime_hotspot_libgraal_TruffleToLibGraalCalls_openDebugContextScope")
    @SuppressWarnings({"unused", "try"})
    public static long openDebugContextScope(JNIEnv env, JClass hsClazz, @CEntryPoint.IsolateThreadContext long isolateThreadId, long handle, JString hsName, long compilationHandle) {
        try (JNIMethodScope s = LibGraalUtil.openScope(TruffleToLibGraalEntryPoints.class, OpenDebugContextScope, env)) {
            TruffleDebugContext debugContext = LibGraalObjectHandles.resolve(handle, TruffleDebugContext.class);
            String name = createString(env, hsName);
            AutoCloseable scope;
            if (compilationHandle == 0) {
                scope = debugContext.scope(name);
            } else {
                TruffleCompilationIdentifier compilation = LibGraalObjectHandles.resolve(compilationHandle, TruffleCompilationIdentifier.class);
                scope = debugContext.scope(name, new TruffleDebugJavaMethod(compilation.getCompilable()));
            }
            if (scope == null) {
                return 0;
            }
            long scopeHandle = LibGraalObjectHandles.create(scope);
            return scopeHandle;
        } catch (Throwable t) {
            JNIExceptionWrapper.throwInHotSpot(env, t);
            return 0;
        }
    }

    @TruffleToLibGraal(CloseDebugContext)
    @CEntryPoint(name = "Java_org_graalvm_compiler_truffle_runtime_hotspot_libgraal_TruffleToLibGraalCalls_closeDebugContext")
    @SuppressWarnings({"unused", "try"})
    public static void closeDebugContext(JNIEnv env, JClass hsClazz, @CEntryPoint.IsolateThreadContext long isolateThreadId, long handle) {
        try (JNIMethodScope s = LibGraalUtil.openScope(TruffleToLibGraalEntryPoints.class, CloseDebugContext, env)) {
            TruffleDebugContext debugContext = LibGraalObjectHandles.resolve(handle, TruffleDebugContext.class);
            debugContext.close();
        } catch (Throwable t) {
            JNIExceptionWrapper.throwInHotSpot(env, t);
        }
    }

    @TruffleToLibGraal(CloseDebugContextScope)
    @CEntryPoint(name = "Java_org_graalvm_compiler_truffle_runtime_hotspot_libgraal_TruffleToLibGraalCalls_closeDebugContextScope")
    @SuppressWarnings({"unused", "try"})
    public static void closeDebugContextScope(JNIEnv env, JClass hsClazz, @CEntryPoint.IsolateThreadContext long isolateThreadId, long handle) {
        try (JNIMethodScope s = LibGraalUtil.openScope(TruffleToLibGraalEntryPoints.class, CloseDebugContextScope, env)) {
            AutoCloseable scope = LibGraalObjectHandles.resolve(handle, DebugContext.Scope.class);
            scope.close();
        } catch (Throwable t) {
            JNIExceptionWrapper.throwInHotSpot(env, t);
        }
    }

    @TruffleToLibGraal(IsBasicDumpEnabled)
    @CEntryPoint(name = "Java_org_graalvm_compiler_truffle_runtime_hotspot_libgraal_TruffleToLibGraalCalls_isBasicDumpEnabled")
    @SuppressWarnings({"unused", "try"})
    public static boolean isBasicDumpEnabled(JNIEnv env, JClass hsClazz, @CEntryPoint.IsolateThreadContext long isolateThreadId, long handle) {
        try (JNIMethodScope s = LibGraalUtil.openScope(TruffleToLibGraalEntryPoints.class, IsBasicDumpEnabled, env)) {
            TruffleDebugContext debugContext = LibGraalObjectHandles.resolve(handle, TruffleDebugContext.class);
            return debugContext.isDumpEnabled();
        } catch (Throwable t) {
            JNIExceptionWrapper.throwInHotSpot(env, t);
            return false;
        }
    }

    @TruffleToLibGraal(GetTruffleCompilationTruffleAST)
    @CEntryPoint(name = "Java_org_graalvm_compiler_truffle_runtime_hotspot_libgraal_TruffleToLibGraalCalls_getTruffleCompilationTruffleAST")
    @SuppressWarnings({"unused", "try"})
    public static JObject getTruffleCompilationTruffleAST(JNIEnv env, JClass hsClazz, @CEntryPoint.IsolateThreadContext long isolateThreadId, long compilationHandle) {
        JNIMethodScope scope = LibGraalUtil.openScope(TruffleToLibGraalEntryPoints.class, GetTruffleCompilationTruffleAST, env);
        try (JNIMethodScope s = scope) {
            HSCompilableTruffleAST compilable = (HSCompilableTruffleAST) LibGraalObjectHandles.resolve(compilationHandle, TruffleCompilation.class).getCompilable();
            scope.setObjectResult(compilable.getHandle());
        } catch (Throwable t) {
            JNIExceptionWrapper.throwInHotSpot(env, t);
            scope.setObjectResult(WordFactory.nullPointer());
        }
        return scope.getObjectResult();
    }

    @TruffleToLibGraal(GetTruffleCompilationId)
    @CEntryPoint(name = "Java_org_graalvm_compiler_truffle_runtime_hotspot_libgraal_TruffleToLibGraalCalls_getTruffleCompilationId")
    @SuppressWarnings({"unused", "try"})
    public static JString getTruffleCompilationId(JNIEnv env, JClass hsClazz, @CEntryPoint.IsolateThreadContext long isolateThreadId, long compilationHandle) {
        JNIMethodScope scope = LibGraalUtil.openScope(TruffleToLibGraalEntryPoints.class, GetTruffleCompilationId, env);
        try (JNIMethodScope s = scope) {
            String compilationId = LibGraalObjectHandles.resolve(compilationHandle, TruffleCompilationIdentifier.class).toString(CompilationIdentifier.Verbosity.ID);
            scope.setObjectResult(createHSString(env, compilationId));
        } catch (Throwable t) {
            JNIExceptionWrapper.throwInHotSpot(env, t);
            scope.setObjectResult(WordFactory.nullPointer());
        }
        return scope.getObjectResult();
    }

    @TruffleToLibGraal(GetDumpChannel)
    @CEntryPoint(name = "Java_org_graalvm_compiler_truffle_runtime_hotspot_libgraal_TruffleToLibGraalCalls_getDumpChannel")
    @SuppressWarnings({"unused", "try"})
    public static long getDumpChannel(JNIEnv env, JClass hsClazz, @CEntryPoint.IsolateThreadContext long isolateThreadId, long debugContextHandle) {
        try (JNIMethodScope s = LibGraalUtil.openScope(TruffleToLibGraalEntryPoints.class, GetDumpChannel, env)) {
            TruffleDebugContextImpl debugContext = LibGraalObjectHandles.resolve(debugContextHandle, TruffleDebugContextImpl.class);
            GraphOutput<Void, ?> graphOutput = debugContext.buildOutput(GraphOutput.newBuilder(VoidGraphStructure.INSTANCE));
            return LibGraalObjectHandles.create(graphOutput);
        } catch (Throwable t) {
            JNIExceptionWrapper.throwInHotSpot(env, t);
            return 0;
        }
    }

    @TruffleToLibGraal(GetVersionProperties)
    @CEntryPoint(name = "Java_org_graalvm_compiler_truffle_runtime_hotspot_libgraal_TruffleToLibGraalCalls_getVersionProperties")
    @SuppressWarnings({"unused", "try"})
    public static JByteArray getVersionProperties(JNIEnv env, JClass hsClazz, @CEntryPoint.IsolateThreadContext long isolateThreadId) {
        JNIMethodScope scope = LibGraalUtil.openScope(TruffleToLibGraalEntryPoints.class, GetVersionProperties, env);
        try (JNIMethodScope s = scope) {
            Map<String, Object> versionProperties = new HashMap<>();
            for (Map.Entry<Object, Object> e : DebugContext.addVersionProperties(null).entrySet()) {
                Object key = e.getKey();
                Object value = e.getValue();
                assert key instanceof String;
                assert value instanceof String;
                versionProperties.put((String) key, value);
            }
            byte[] serializedProperties = OptionsEncoder.encode(versionProperties);
            JByteArray hsSerializedOptions = NewByteArray(env, serializedProperties.length);
            CCharPointer cdata = GetByteArrayElements(env, hsSerializedOptions, WordFactory.nullPointer());
            CTypeConversion.asByteBuffer(cdata, serializedProperties.length).put(serializedProperties);
            ReleaseByteArrayElements(env, hsSerializedOptions, cdata, JArray.MODE_WRITE_RELEASE);
            scope.setObjectResult(hsSerializedOptions);
        } catch (Throwable t) {
            JNIExceptionWrapper.throwInHotSpot(env, t);
            scope.setObjectResult(WordFactory.nullPointer());
        }
        return scope.getObjectResult();
    }

    @TruffleToLibGraal(IsDumpChannelOpen)
    @CEntryPoint(name = "Java_org_graalvm_compiler_truffle_runtime_hotspot_libgraal_TruffleToLibGraalCalls_isDumpChannelOpen")
    @SuppressWarnings({"unused", "try"})
    public static boolean isDumpChannelOpen(JNIEnv env, JClass hsClazz, @CEntryPoint.IsolateThreadContext long isolateThreadId, long channelHandle) {
        try (JNIMethodScope s = LibGraalUtil.openScope(TruffleToLibGraalEntryPoints.class, IsDumpChannelOpen, env)) {
            return LibGraalObjectHandles.resolve(channelHandle, WritableByteChannel.class).isOpen();
        } catch (Throwable t) {
            JNIExceptionWrapper.throwInHotSpot(env, t);
            return false;
        }
    }

    @TruffleToLibGraal(DumpChannelWrite)
    @CEntryPoint(name = "Java_org_graalvm_compiler_truffle_runtime_hotspot_libgraal_TruffleToLibGraalCalls_dumpChannelWrite")
    @SuppressWarnings({"unused", "try"})
    public static int dumpChannelWrite(JNIEnv env, JClass hsClass, @CEntryPoint.IsolateThreadContext long isolateThreadId, long channelHandle, JObject hsSource, int capacity, int position,
                    int limit) {
        try (JNIMethodScope s = LibGraalUtil.openScope(TruffleToLibGraalEntryPoints.class, DumpChannelWrite, env)) {
            WritableByteChannel channel = LibGraalObjectHandles.resolve(channelHandle, WritableByteChannel.class);
            VoidPointer baseAddr = JNIUtil.GetDirectBufferAddress(env, hsSource);
            ByteBuffer source = CTypeConversion.asByteBuffer(baseAddr, capacity);
            source.position(position);
            source.limit(limit);
            return channel.write(source);
        } catch (Throwable t) {
            JNIExceptionWrapper.throwInHotSpot(env, t);
            return -1;
        }
    }

    @TruffleToLibGraal(DumpChannelClose)
    @CEntryPoint(name = "Java_org_graalvm_compiler_truffle_runtime_hotspot_libgraal_TruffleToLibGraalCalls_dumpChannelClose")
    @SuppressWarnings({"unused", "try"})
    public static void dumpChannelClose(JNIEnv env, JClass hsClass, @CEntryPoint.IsolateThreadContext long isolateThreadId, long channelHandle) {
        try (JNIMethodScope s = LibGraalUtil.openScope(TruffleToLibGraalEntryPoints.class, IsDumpChannelOpen, env)) {
            LibGraalObjectHandles.resolve(channelHandle, WritableByteChannel.class).close();
        } catch (Throwable t) {
            JNIExceptionWrapper.throwInHotSpot(env, t);
        }
    }

    @TruffleToLibGraal(PurgePartialEvaluationCaches)
    @CEntryPoint(name = "Java_org_graalvm_compiler_truffle_runtime_hotspot_libgraal_TruffleToLibGraalCalls_purgePartialEvaluationCaches")
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

    @TruffleToLibGraal(GetExecutionID)
    @CEntryPoint(name = "Java_org_graalvm_compiler_truffle_runtime_hotspot_libgraal_TruffleToLibGraalCalls_getExecutionID")
    @SuppressWarnings({"unused", "try"})
    public static JString getExecutionID(JNIEnv env, JClass hsClass, @CEntryPoint.IsolateThreadContext long isolateThreadId) {
        JNIMethodScope scope = LibGraalUtil.openScope(TruffleToLibGraalEntryPoints.class, GetExecutionID, env);
        try (JNIMethodScope s = scope) {
            scope.setObjectResult(createHSString(env, GraalServices.getExecutionID()));
        } catch (Throwable t) {
            JNIExceptionWrapper.throwInHotSpot(env, t);
            scope.setObjectResult(WordFactory.nullPointer());
        }
        return scope.getObjectResult();
    }

    static {
        try {
            Class<?> callsClass = Class.forName("org.graalvm.compiler.truffle.runtime.hotspot.libgraal.TruffleToLibGraalCalls");
            LibGraalUtil.checkToLibGraalCalls(TruffleToLibGraalEntryPoints.class, callsClass, TruffleToLibGraal.class);
        } catch (ClassNotFoundException e) {
            throw new InternalError(e);
        }
    }
}
