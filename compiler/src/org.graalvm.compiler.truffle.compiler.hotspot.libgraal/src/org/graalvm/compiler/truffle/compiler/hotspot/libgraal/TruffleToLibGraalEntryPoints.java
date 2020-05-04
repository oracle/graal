/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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


import static jdk.vm.ci.hotspot.HotSpotJVMCIRuntime.runtime;
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
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleToLibGraal.Id.GetGraphDumpDirectory;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleToLibGraal.Id.GetInfopoints;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleToLibGraal.Id.GetInfopointsCount;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleToLibGraal.Id.GetInitialOptions;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleToLibGraal.Id.GetMarksCount;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleToLibGraal.Id.GetNodeCount;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleToLibGraal.Id.GetNodeTypes;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleToLibGraal.Id.GetSuppliedString;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleToLibGraal.Id.GetTargetCodeSize;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleToLibGraal.Id.GetTotalFrameSize;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleToLibGraal.Id.GetTruffleCompilationId;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleToLibGraal.Id.GetTruffleCompilationTruffleAST;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleToLibGraal.Id.InitializeCompiler;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleToLibGraal.Id.InitializeRuntime;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleToLibGraal.Id.InstallTruffleCallBoundaryMethods;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleToLibGraal.Id.IsBasicDumpEnabled;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleToLibGraal.Id.IsDumpChannelOpen;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleToLibGraal.Id.NewCompiler;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleToLibGraal.Id.OpenCompilation;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleToLibGraal.Id.OpenDebugContext;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleToLibGraal.Id.OpenDebugContextScope;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleToLibGraal.Id.PendingTransferToInterpreterOffset;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleToLibGraal.Id.Shutdown;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleToLibGraal.Id.TtyWriteByte;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleToLibGraal.Id.TtyWriteBytes;
import static org.graalvm.libgraal.jni.JNIUtil.GetArrayLength;
import static org.graalvm.libgraal.jni.JNIUtil.GetByteArrayElements;
import static org.graalvm.libgraal.jni.JNIUtil.NewByteArray;
import static org.graalvm.libgraal.jni.JNIUtil.NewObjectArray;
import static org.graalvm.libgraal.jni.JNIUtil.ReleaseByteArrayElements;
import static org.graalvm.libgraal.jni.JNIUtil.SetObjectArrayElement;
import static org.graalvm.libgraal.jni.JNIUtil.createHSString;
import static org.graalvm.libgraal.jni.JNIUtil.createString;
import static org.graalvm.compiler.truffle.compiler.hotspot.libgraal.TruffleFromLibGraalUtil.getJNIClass;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import org.graalvm.compiler.core.common.CompilationIdentifier;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.debug.DebugOptions;
import org.graalvm.compiler.debug.TTY;
import org.graalvm.compiler.hotspot.CompilationContext;
import org.graalvm.compiler.hotspot.CompilerConfigurationFactory;
import org.graalvm.compiler.hotspot.HotSpotGraalOptionValues;
import org.graalvm.compiler.hotspot.HotSpotGraalServices;
import org.graalvm.compiler.options.OptionValues;
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
import org.graalvm.compiler.truffle.common.TruffleInliningPlan;
import org.graalvm.compiler.truffle.common.VoidGraphStructure;
import org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleToLibGraal;
import org.graalvm.compiler.truffle.compiler.TruffleCompilationIdentifier;
import org.graalvm.compiler.truffle.compiler.TruffleCompilerOptions;
import org.graalvm.compiler.truffle.compiler.TruffleDebugContextImpl;
import org.graalvm.compiler.truffle.compiler.hotspot.HotSpotTruffleCompilerImpl;
import org.graalvm.compiler.truffle.compiler.hotspot.HotSpotTruffleCompilerImpl.Options;
import org.graalvm.graphio.GraphOutput;
import org.graalvm.libgraal.LibGraal;
import org.graalvm.libgraal.jni.HSObject;
import org.graalvm.libgraal.jni.ToLibGraalScope;
import org.graalvm.libgraal.jni.JNI.JArray;
import org.graalvm.libgraal.jni.JNI.JByteArray;
import org.graalvm.libgraal.jni.JNI.JClass;
import org.graalvm.libgraal.jni.JNI.JNIEnv;
import org.graalvm.libgraal.jni.JNI.JObject;
import org.graalvm.libgraal.jni.JNI.JObjectArray;
import org.graalvm.libgraal.jni.JNI.JString;
import org.graalvm.libgraal.jni.JNIUtil;
import org.graalvm.nativeimage.Isolate;
import org.graalvm.nativeimage.Platform.HOSTED_ONLY;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CTypeConversion;
import org.graalvm.nativeimage.c.type.VoidPointer;
import org.graalvm.util.OptionsEncoder;
import org.graalvm.word.WordFactory;

import jdk.vm.ci.meta.ResolvedJavaType;



/**
 * Entry points in libgraal for {@link TruffleToLibGraal calls} from HotSpot.
 *
 * To trace Truffle calls between HotSpot and libgraal, set the environment variable
 * {@code JNI_LIBGRAAL_TRACE_LEVEL} to {@code true}.
 */
final class TruffleToLibGraalEntryPoints {

    @TruffleToLibGraal(InitializeRuntime)
    @SuppressWarnings({"unused", "try"})
    @CEntryPoint(name = "Java_org_graalvm_compiler_truffle_runtime_hotspot_libgraal_TruffleToLibGraalCalls_initializeRuntime")
    public static long initializeRuntime(JNIEnv env, JClass hsClazz, @CEntryPoint.IsolateThreadContext long isolateThreadId,
                    JObject truffleRuntime, long classLoaderDelegateId) {
        try (ToLibGraalScope<TruffleToLibGraal.Id> s = new ToLibGraalScope<>(InitializeRuntime, env)) {
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
        try (ToLibGraalScope<TruffleToLibGraal.Id> s = new ToLibGraalScope<>(NewCompiler, env)) {
            HSTruffleCompilerRuntime hsTruffleRuntime = LibGraalObjectHandles.resolve(truffleRuntimeHandle, HSTruffleCompilerRuntime.class);
            return LibGraalObjectHandles.create(HotSpotTruffleCompilerImpl.create(hsTruffleRuntime));
        } catch (Throwable t) {
            JNIExceptionWrapper.throwInHotSpot(env, t);
            return 0;
        }
    }

    @TruffleToLibGraal(InitializeCompiler)
    @SuppressWarnings({"unused", "try"})
    @CEntryPoint(name = "Java_org_graalvm_compiler_truffle_runtime_hotspot_libgraal_TruffleToLibGraalCalls_initializeCompiler")
    public static void initializeCompiler(JNIEnv env, JClass hsClazz, @CEntryPoint.IsolateThreadContext long isolateThreadId, long compilerHandle, JByteArray hsOptions) {
        try (ToLibGraalScope<TruffleToLibGraal.Id> s = new ToLibGraalScope<>(InitializeCompiler, env)) {
            HotSpotTruffleCompilerImpl compiler = LibGraalObjectHandles.resolve(compilerHandle, HotSpotTruffleCompilerImpl.class);
            Map<String, Object> options = decodeOptions(env, hsOptions);
            compiler.initialize(options);
        } catch (Throwable t) {
            JNIExceptionWrapper.throwInHotSpot(env, t);
        }
    }

    @TruffleToLibGraal(GetCompilerConfigurationFactoryName)
    @SuppressWarnings({"unused", "try"})
    @CEntryPoint(name = "Java_org_graalvm_compiler_truffle_runtime_hotspot_libgraal_TruffleToLibGraalCalls_getCompilerConfigurationFactoryName")
    public static JString getCompilerConfigurationFactoryName(JNIEnv env, JClass hsClazz, @CEntryPoint.IsolateThreadContext long isolateThreadId, long truffleRuntimeHandle) {
        ToLibGraalScope<TruffleToLibGraal.Id> scope = new ToLibGraalScope<>(GetCompilerConfigurationFactoryName, env);
        try (ToLibGraalScope<TruffleToLibGraal.Id> s = scope) {
            HSTruffleCompilerRuntime hsTruffleRuntime = LibGraalObjectHandles.resolve(truffleRuntimeHandle, HSTruffleCompilerRuntime.class);
            assert TruffleCompilerRuntime.getRuntime() == hsTruffleRuntime;
            OptionValues options = TruffleCompilerOptions.getOptions();
            CompilerConfigurationFactory compilerConfigurationFactory = CompilerConfigurationFactory.selectFactory(Options.TruffleCompilerConfiguration.getValue(options), options);
            String name = compilerConfigurationFactory.getName();
            scope.setObjectResult(createHSString(env, name));
        } catch (Throwable t) {
            JNIExceptionWrapper.throwInHotSpot(env, t);
            return WordFactory.nullPointer();
        }
        return scope.getObjectResult();
    }

    @TruffleToLibGraal(OpenCompilation)
    @SuppressWarnings({"unused", "try"})
    @CEntryPoint(name = "Java_org_graalvm_compiler_truffle_runtime_hotspot_libgraal_TruffleToLibGraalCalls_openCompilation")
    public static long openCompilation(JNIEnv env, JClass hsClazz, @CEntryPoint.IsolateThreadContext long isolateThreadId, long handle, JObject hsCompilable) {
        ToLibGraalScope<TruffleToLibGraal.Id> scope = new ToLibGraalScope<>(OpenCompilation, env);
        try (ToLibGraalScope<TruffleToLibGraal.Id> s = scope) {
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
        ToLibGraalScope<TruffleToLibGraal.Id> scope = new ToLibGraalScope<>(GetCompilerConfigurationName, env);
        try (ToLibGraalScope<TruffleToLibGraal.Id> s = scope) {
            HotSpotTruffleCompilerImpl compiler = LibGraalObjectHandles.resolve(handle, HotSpotTruffleCompilerImpl.class);
            String name = compiler.getCompilerConfigurationName();
            scope.setObjectResult(createHSString(env, name));
        } catch (Throwable t) {
            JNIExceptionWrapper.throwInHotSpot(env, t);
            return WordFactory.nullPointer();
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
                    JObject hsInlining,
                    JObject hsTask,
                    JObject hsListener) {
        try (ToLibGraalScope<TruffleToLibGraal.Id> scope = new ToLibGraalScope<>(DoCompile, env)) {
            TruffleCompilationIdentifier compilation = LibGraalObjectHandles.resolve(compilationHandle, TruffleCompilationIdentifier.class);
            try (CompilationContext hotSpotObjectConstantScope = HotSpotGraalServices.openLocalCompilationContext(compilation)) {
                HotSpotTruffleCompilerImpl compiler = LibGraalObjectHandles.resolve(compilerHandle, HotSpotTruffleCompilerImpl.class);
                TruffleDebugContext debugContext = LibGraalObjectHandles.resolve(debugContextHandle, TruffleDebugContext.class);
                Map<String, Object> options = decodeOptions(env, hsOptions);
                TruffleInliningPlan inlining = new HSTruffleInliningPlan(scope, hsInlining);
                TruffleCompilationTask task = hsTask.isNull() ? null : new HSTruffleCompilationTask(scope, hsTask);
                TruffleCompilerListener listener = hsListener.isNull() ? null : new HSTruffleCompilerListener(scope, hsListener);
                compiler.doCompile(debugContext, compilation, options, inlining, task, listener);
            }
        } catch (Throwable t) {
            JNIExceptionWrapper.throwInHotSpot(env, t);
        }
    }

    @TruffleToLibGraal(CloseCompilation)
    @SuppressWarnings({"unused", "try"})
    @CEntryPoint(name = "Java_org_graalvm_compiler_truffle_runtime_hotspot_libgraal_TruffleToLibGraalCalls_closeCompilation")
    public static void closeCompilation(JNIEnv env, JClass hsClazz, @CEntryPoint.IsolateThreadContext long isolateThreadId, long compilationHandle) {
        try (ToLibGraalScope<TruffleToLibGraal.Id> scope = new ToLibGraalScope<>(CloseCompilation, env)) {
            TruffleCompilation compilation = LibGraalObjectHandles.resolve(compilationHandle, TruffleCompilation.class);
            HSCompilableTruffleAST compilable = (HSCompilableTruffleAST) compilation.getCompilable();
            compilable.release(env);
            HSObject.cleanHandles(env);
        } catch (Throwable t) {
            JNIExceptionWrapper.throwInHotSpot(env, t);
        }
    }

    @TruffleToLibGraal(Shutdown)
    @SuppressWarnings({"unused", "try"})
    @CEntryPoint(name = "Java_org_graalvm_compiler_truffle_runtime_hotspot_libgraal_TruffleToLibGraalCalls_shutdown")
    public static void shutdown(JNIEnv env, JClass hsClazz, @CEntryPoint.IsolateThreadContext long isolateThreadId, long handle) {
        try (ToLibGraalScope<TruffleToLibGraal.Id> s = new ToLibGraalScope<>(Shutdown, env)) {
            HotSpotTruffleCompilerImpl compiler = LibGraalObjectHandles.resolve(handle, HotSpotTruffleCompilerImpl.class);
            compiler.shutdown();
        } catch (Throwable t) {
            JNIExceptionWrapper.throwInHotSpot(env, t);
        }
    }

    @TruffleToLibGraal(InstallTruffleCallBoundaryMethods)
    @SuppressWarnings({"unused", "try"})
    @CEntryPoint(name = "Java_org_graalvm_compiler_truffle_runtime_hotspot_libgraal_TruffleToLibGraalCalls_installTruffleCallBoundaryMethods")
    public static void installTruffleCallBoundaryMethods(JNIEnv env, JClass hsClazz, @CEntryPoint.IsolateThreadContext long isolateThreadId, long handle) {
        try (ToLibGraalScope<TruffleToLibGraal.Id> s = new ToLibGraalScope<>(InstallTruffleCallBoundaryMethods, env)) {
            HotSpotTruffleCompilerImpl compiler = LibGraalObjectHandles.resolve(handle, HotSpotTruffleCompilerImpl.class);
            compiler.installTruffleCallBoundaryMethods();
        } catch (Throwable t) {
            JNIExceptionWrapper.throwInHotSpot(env, t);
        }
    }

    @TruffleToLibGraal(PendingTransferToInterpreterOffset)
    @SuppressWarnings({"unused", "try"})
    @CEntryPoint(name = "Java_org_graalvm_compiler_truffle_runtime_hotspot_libgraal_TruffleToLibGraalCalls_pendingTransferToInterpreterOffset")
    public static int pendingTransferToInterpreterOffset(JNIEnv env, JClass hsClazz, @CEntryPoint.IsolateThreadContext long isolateThreadId, long handle) {
        try (ToLibGraalScope<TruffleToLibGraal.Id> scope = new ToLibGraalScope<>(PendingTransferToInterpreterOffset, env)) {
            HotSpotTruffleCompilerImpl compiler = LibGraalObjectHandles.resolve(handle, HotSpotTruffleCompilerImpl.class);
            return compiler.pendingTransferToInterpreterOffset();
        } catch (Throwable t) {
            JNIExceptionWrapper.throwInHotSpot(env, t);
            return 0;
        }
    }

    @TruffleToLibGraal(GetGraphDumpDirectory)
    @SuppressWarnings({"unused", "try"})
    @CEntryPoint(name = "Java_org_graalvm_compiler_truffle_runtime_hotspot_libgraal_TruffleToLibGraalCalls_getGraphDumpDirectory")
    public static JString getGraphDumpDirectory(JNIEnv env, JClass hsClazz, @CEntryPoint.IsolateThreadContext long isolateThreadId) {
        ToLibGraalScope<TruffleToLibGraal.Id> scope = new ToLibGraalScope<>(GetGraphDumpDirectory, env);
        try (ToLibGraalScope<TruffleToLibGraal.Id> s = scope) {
            Path path = DebugOptions.getDumpDirectory(HotSpotGraalOptionValues.defaultOptions());
            scope.setObjectResult(createHSString(env, path.toString()));
        } catch (IOException ioe) {
            return WordFactory.nullPointer();
        } catch (Throwable t) {
            JNIExceptionWrapper.throwInHotSpot(env, t);
            return WordFactory.nullPointer();
        }
        return scope.getObjectResult();
    }

    @TruffleToLibGraal(GetInitialOptions)
    @SuppressWarnings({"unused", "try"})
    @CEntryPoint(name = "Java_org_graalvm_compiler_truffle_runtime_hotspot_libgraal_TruffleToLibGraalCalls_getInitialOptions")
    public static JByteArray getInitialOptions(JNIEnv env, JClass hsClazz, @CEntryPoint.IsolateThreadContext long isolateThreadId, long truffleRuntimeHandle) {
        ToLibGraalScope<TruffleToLibGraal.Id> scope = new ToLibGraalScope<>(GetInitialOptions, env);
        try (ToLibGraalScope<TruffleToLibGraal.Id> s = scope) {
            HSTruffleCompilerRuntime hsTruffleRuntime = LibGraalObjectHandles.resolve(truffleRuntimeHandle, HSTruffleCompilerRuntime.class);
            Map<String, Object> allOptions = hsTruffleRuntime.getOptions();
            Map<String, Object> options = new HashMap<>();
            for (Map.Entry<String, Object> option : allOptions.entrySet()) {
                String key = option.getKey();
                Object value = option.getValue();
                if (OptionsEncoder.isValueSupported(value)) {
                    options.put(key, value);
                }
            }
            byte[] serializedOptions = OptionsEncoder.encode(options);
            JByteArray hsSerializedOptions = NewByteArray(env, serializedOptions.length);
            CCharPointer cdata = GetByteArrayElements(env, hsSerializedOptions, WordFactory.nullPointer());
            for (int i = 0; i < serializedOptions.length; i++) {
                cdata.write(i, serializedOptions[i]);
            }
            ReleaseByteArrayElements(env, hsSerializedOptions, cdata, JArray.MODE_WRITE_RELEASE);
            scope.setObjectResult(hsSerializedOptions);
        } catch (Throwable t) {
            JNIExceptionWrapper.throwInHotSpot(env, t);
            return WordFactory.nullPointer();
        }
        return scope.getObjectResult();
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
        ToLibGraalScope<TruffleToLibGraal.Id> scope = new ToLibGraalScope<>(GetSuppliedString, env);
        try (ToLibGraalScope<TruffleToLibGraal.Id> s = scope) {
            Supplier<String> orig = LibGraalObjectHandles.resolve(handle, Supplier.class);
            if (orig != null) {
                String stackTrace = orig.get();
                scope.setObjectResult(JNIUtil.createHSString(env, stackTrace));
            } else {
                return WordFactory.nullPointer();
            }
        } catch (Throwable t) {
            JNIExceptionWrapper.throwInHotSpot(env, t);
            return WordFactory.nullPointer();
        }
        return scope.getObjectResult();
    }

    @TruffleToLibGraal(GetNodeCount)
    @CEntryPoint(name = "Java_org_graalvm_compiler_truffle_runtime_hotspot_libgraal_TruffleToLibGraalCalls_getNodeCount")
    @SuppressWarnings({"unused", "try"})
    public static int getNodeCount(JNIEnv env, JClass hsClazz, @CEntryPoint.IsolateThreadContext long isolateThreadId, long handle) {
        try (ToLibGraalScope<TruffleToLibGraal.Id> s = new ToLibGraalScope<>(GetNodeCount, env)) {
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
        ToLibGraalScope<TruffleToLibGraal.Id> scope = new ToLibGraalScope<>(GetNodeTypes, env);
        try (ToLibGraalScope<TruffleToLibGraal.Id> s = scope) {
            GraphInfo orig = LibGraalObjectHandles.resolve(handle, GraphInfo.class);
            String[] nodeTypes = orig.getNodeTypes(simpleNames);
            JClass componentType = getJNIClass(env, String.class).jclass;
            JObjectArray res = NewObjectArray(env, nodeTypes.length, componentType, WordFactory.nullPointer());
            for (int i = 0; i < nodeTypes.length; i++) {
                SetObjectArrayElement(env, res, i, JNIUtil.createHSString(env, nodeTypes[i]));
            }
            scope.setObjectResult(res);
        } catch (Throwable t) {
            JNIExceptionWrapper.throwInHotSpot(env, t);
            return WordFactory.nullPointer();
        }
        return scope.getObjectResult();
    }

    @TruffleToLibGraal(GetTargetCodeSize)
    @CEntryPoint(name = "Java_org_graalvm_compiler_truffle_runtime_hotspot_libgraal_TruffleToLibGraalCalls_getTargetCodeSize")
    @SuppressWarnings({"unused", "try"})
    public static int getTargetCodeSize(JNIEnv env, JClass hsClazz, @CEntryPoint.IsolateThreadContext long isolateThreadId, long handle) {
        try (ToLibGraalScope<TruffleToLibGraal.Id> s = new ToLibGraalScope<>(GetTargetCodeSize, env)) {
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
        try (ToLibGraalScope<TruffleToLibGraal.Id> s = new ToLibGraalScope<>(GetTotalFrameSize, env)) {
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
        try (ToLibGraalScope<TruffleToLibGraal.Id> s = new ToLibGraalScope<>(GetExceptionHandlersCount, env)) {
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
        try (ToLibGraalScope<TruffleToLibGraal.Id> s = new ToLibGraalScope<>(GetInfopointsCount, env)) {
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
        ToLibGraalScope<TruffleToLibGraal.Id> scope = new ToLibGraalScope<>(GetInfopoints, env);
        try (ToLibGraalScope<TruffleToLibGraal.Id> s = scope) {
            String[] infoPoints = LibGraalObjectHandles.resolve(handle, CompilationResultInfo.class).getInfopoints();
            JClass componentType = getJNIClass(env, String.class).jclass;
            JObjectArray res = NewObjectArray(env, infoPoints.length, componentType, WordFactory.nullPointer());
            for (int i = 0; i < infoPoints.length; i++) {
                SetObjectArrayElement(env, res, i, createHSString(env, infoPoints[i]));
            }
            scope.setObjectResult(res);
        } catch (Throwable t) {
            JNIExceptionWrapper.throwInHotSpot(env, t);
            return WordFactory.nullPointer();
        }
        return scope.getObjectResult();
    }

    @TruffleToLibGraal(GetMarksCount)
    @CEntryPoint(name = "Java_org_graalvm_compiler_truffle_runtime_hotspot_libgraal_TruffleToLibGraalCalls_getMarksCount")
    @SuppressWarnings({"unused", "try"})
    public static int getMarksCount(JNIEnv env, JClass hsClazz, @CEntryPoint.IsolateThreadContext long isolateThreadId, long handle) {
        try (ToLibGraalScope<TruffleToLibGraal.Id> s = new ToLibGraalScope<>(GetMarksCount, env)) {
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
        try (ToLibGraalScope<TruffleToLibGraal.Id> s = new ToLibGraalScope<>(GetDataPatchesCount, env)) {
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
        try (ToLibGraalScope<TruffleToLibGraal.Id> s = new ToLibGraalScope<>(OpenDebugContext, env)) {
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
        try (ToLibGraalScope<TruffleToLibGraal.Id> s = new ToLibGraalScope<>(OpenDebugContextScope, env)) {
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
        try (ToLibGraalScope<TruffleToLibGraal.Id> s = new ToLibGraalScope<>(CloseDebugContext, env)) {
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
        try (ToLibGraalScope<TruffleToLibGraal.Id> s = new ToLibGraalScope<>(CloseDebugContextScope, env)) {
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
        try (ToLibGraalScope<TruffleToLibGraal.Id> s = new ToLibGraalScope<>(IsBasicDumpEnabled, env)) {
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
        ToLibGraalScope<TruffleToLibGraal.Id> scope = new ToLibGraalScope<>(GetTruffleCompilationTruffleAST, env);
        try (ToLibGraalScope<TruffleToLibGraal.Id> s = scope) {
            HSCompilableTruffleAST compilable = (HSCompilableTruffleAST) LibGraalObjectHandles.resolve(compilationHandle, TruffleCompilation.class).getCompilable();
            scope.setObjectResult(compilable.getHandle());
        } catch (Throwable t) {
            JNIExceptionWrapper.throwInHotSpot(env, t);
            return WordFactory.nullPointer();
        }
        return scope.getObjectResult();
    }

    @TruffleToLibGraal(GetTruffleCompilationId)
    @CEntryPoint(name = "Java_org_graalvm_compiler_truffle_runtime_hotspot_libgraal_TruffleToLibGraalCalls_getTruffleCompilationId")
    @SuppressWarnings({"unused", "try"})
    public static JString getTruffleCompilationId(JNIEnv env, JClass hsClazz, @CEntryPoint.IsolateThreadContext long isolateThreadId, long compilationHandle) {
        ToLibGraalScope<TruffleToLibGraal.Id> scope = new ToLibGraalScope<>(GetTruffleCompilationId, env);
        try (ToLibGraalScope<TruffleToLibGraal.Id> s = scope) {
            String compilationId = LibGraalObjectHandles.resolve(compilationHandle, TruffleCompilationIdentifier.class).toString(CompilationIdentifier.Verbosity.ID);
            scope.setObjectResult(createHSString(env, compilationId));
        } catch (Throwable t) {
            JNIExceptionWrapper.throwInHotSpot(env, t);
            return WordFactory.nullPointer();
        }
        return scope.getObjectResult();
    }

    @TruffleToLibGraal(GetDumpChannel)
    @CEntryPoint(name = "Java_org_graalvm_compiler_truffle_runtime_hotspot_libgraal_TruffleToLibGraalCalls_getDumpChannel")
    @SuppressWarnings({"unused", "try"})
    public static long getDumpChannel(JNIEnv env, JClass hsClazz, @CEntryPoint.IsolateThreadContext long isolateThreadId, long debugContextHandle) {
        try (ToLibGraalScope<TruffleToLibGraal.Id> s = new ToLibGraalScope<>(GetDumpChannel, env)) {
            TruffleDebugContextImpl debugContext = LibGraalObjectHandles.resolve(debugContextHandle, TruffleDebugContextImpl.class);
            GraphOutput<Void, ?> graphOutput = debugContext.buildOutput(GraphOutput.newBuilder(VoidGraphStructure.INSTANCE).protocolVersion(6, 1));
            return LibGraalObjectHandles.create(graphOutput);
        } catch (Throwable t) {
            JNIExceptionWrapper.throwInHotSpot(env, t);
            return 0;
        }
    }

    @TruffleToLibGraal(IsDumpChannelOpen)
    @CEntryPoint(name = "Java_org_graalvm_compiler_truffle_runtime_hotspot_libgraal_TruffleToLibGraalCalls_isDumpChannelOpen")
    @SuppressWarnings({"unused", "try"})
    public static boolean isDumpChannelOpen(JNIEnv env, JClass hsClazz, @CEntryPoint.IsolateThreadContext long isolateThreadId, long channelHandle) {
        try (ToLibGraalScope<TruffleToLibGraal.Id> s = new ToLibGraalScope<>(IsDumpChannelOpen, env)) {
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
        try (ToLibGraalScope<TruffleToLibGraal.Id> s = new ToLibGraalScope<>(DumpChannelWrite, env)) {
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
        try (ToLibGraalScope<TruffleToLibGraal.Id> s = new ToLibGraalScope<>(IsDumpChannelOpen, env)) {
            LibGraalObjectHandles.resolve(channelHandle, WritableByteChannel.class).close();
        } catch (Throwable t) {
            JNIExceptionWrapper.throwInHotSpot(env, t);
        }
    }

    @TruffleToLibGraal(TtyWriteByte)
    @CEntryPoint(name = "Java_org_graalvm_compiler_truffle_runtime_hotspot_libgraal_TruffleToLibGraalCalls_ttyWriteByte")
    @SuppressWarnings({"unused", "try"})
    public static void ttyWriteByte(JNIEnv env, JClass hsClass, @CEntryPoint.IsolateThreadContext long isolateThreadId, int b) {
        try (ToLibGraalScope<TruffleToLibGraal.Id> s = new ToLibGraalScope<>(TtyWriteByte, env)) {
            TTY.out.write(b);
        } catch (Throwable t) {
            JNIExceptionWrapper.throwInHotSpot(env, t);
        }
    }

    @TruffleToLibGraal(TtyWriteBytes)
    @CEntryPoint(name = "Java_org_graalvm_compiler_truffle_runtime_hotspot_libgraal_TruffleToLibGraalCalls_ttyWriteBytes")
    @SuppressWarnings({"unused", "try"})
    public static void ttyWriteBytes(JNIEnv env, JClass hsClass, @CEntryPoint.IsolateThreadContext long isolateThreadId, JByteArray array, int off, int len) {
        try (ToLibGraalScope<TruffleToLibGraal.Id> s = new ToLibGraalScope<>(TtyWriteBytes, env)) {
            int arrayLen = GetArrayLength(env, array);
            CCharPointer arrayPointer = GetByteArrayElements(env, array, WordFactory.nullPointer());
            try {
                byte[] buf = new byte[arrayLen];
                CTypeConversion.asByteBuffer(arrayPointer, arrayLen).get(buf);
                TTY.out.write(buf, off, len);
            } finally {
                ReleaseByteArrayElements(env, array, arrayPointer, JArray.MODE_RELEASE);
            }
        } catch (Throwable t) {
            JNIExceptionWrapper.throwInHotSpot(env, t);
        }
    }

    /*----------------- CHECKING ------------------*/

    /**
     * Checks that all {@link TruffleToLibGraal}s are implemented and their HotSpot/libgraal ends
     * points match.
     */
    @Platforms(HOSTED_ONLY.class)
    private static void checkTruffleToLibGraalCalls() throws InternalError {
        try {
            Class<?> hsClass = Class.forName("org.graalvm.compiler.truffle.runtime.hotspot.libgraal.TruffleToLibGraalCalls");
            Set<TruffleToLibGraal.Id> unimplemented = EnumSet.allOf(TruffleToLibGraal.Id.class);
            for (Method libGraalMethod : TruffleToLibGraalEntryPoints.class.getDeclaredMethods()) {
                TruffleToLibGraal call = libGraalMethod.getAnnotation(TruffleToLibGraal.class);
                if (call != null) {
                    check(Modifier.isStatic(libGraalMethod.getModifiers()), "Method annotated by %s must be static: %s", TruffleToLibGraal.class, libGraalMethod);
                    CEntryPoint ep = libGraalMethod.getAnnotation(CEntryPoint.class);
                    check(ep != null, "Method annotated by %s must also be annotated by %s: %s", TruffleToLibGraal.class, CEntryPoint.class, libGraalMethod);
                    String name = ep.name();
                    String prefix = "Java_" + hsClass.getName().replace('.', '_') + '_';
                    check(name.startsWith(prefix), "Method must be a JNI entry point for a method in %s: %s", hsClass, libGraalMethod);
                    name = name.substring(prefix.length());
                    Method hsMethod = findHSMethod(hsClass, name);
                    Class<?>[] libGraalParameters = libGraalMethod.getParameterTypes();
                    Class<?>[] hsParameters = hsMethod.getParameterTypes();
                    check(hsParameters.length + 2 == libGraalParameters.length, "%s should have 2 more parameters than %s", libGraalMethod, hsMethod);
                    check(libGraalParameters.length >= 3, "Expect at least 3 parameters: %s", libGraalMethod);
                    check(libGraalParameters[0] == JNIEnv.class, "Parameter 0 must be of type %s: %s", JNIEnv.class, libGraalMethod);
                    check(libGraalParameters[1] == JClass.class, "Parameter 1 must be of type %s: %s", JClass.class, libGraalMethod);
                    if (ep.builtin() == CEntryPoint.Builtin.ATTACH_THREAD) {
                        check(libGraalParameters[2] == Isolate.class, "Parameter 2 must be of type %s: %s", Isolate.class, libGraalMethod);
                    } else {
                        check(libGraalParameters[2] == long.class, "Parameter 2 must be of type long: %s", libGraalMethod);
                    }

                    check(hsParameters[0] == long.class, "Parameter 0 must be of type long: %s", hsMethod);

                    for (int i = 3, j = 1; i < libGraalParameters.length; i++, j++) {
                        Class<?> libgraal = libGraalParameters[i];
                        Class<?> hs = hsParameters[j];
                        Class<?> hsExpect;
                        if (hs.isPrimitive()) {
                            hsExpect = libgraal;
                        } else {
                            if (libgraal == JString.class) {
                                hsExpect = String.class;
                            } else if (libgraal == JByteArray.class) {
                                hsExpect = byte[].class;
                            } else {
                                check(libgraal == JObject.class, "must be");
                                hsExpect = Object.class;
                            }
                        }
                        check(hsExpect.isAssignableFrom(hs), "HotSpot parameter %d (%s) incompatible with libgraal parameter %d (%s): %s", j, hs.getName(), i, libgraal.getName(), hsMethod);
                    }
                    unimplemented.remove(call.value());
                }
            }
            check(unimplemented.isEmpty(), "Unimplemented libgraal calls: %s", unimplemented);
        } catch (ClassNotFoundException e) {
            throw new InternalError(e);
        }
    }

    @Platforms(HOSTED_ONLY.class)
    private static void check(boolean condition, String format, Object... args) {
        if (!condition) {
            throw new InternalError(String.format(format, args));
        }
    }

    @Platforms(HOSTED_ONLY.class)
    private static Method findHSMethod(Class<?> hsClass, String name) {
        Method res = null;
        for (Method m : hsClass.getDeclaredMethods()) {
            if (m.getName().equals(name)) {
                check(res == null, "More than one method named \"%s\" in %s", name, hsClass);
                TruffleToLibGraal call = m.getAnnotation(TruffleToLibGraal.class);
                check(call != null, "Method must be annotated by %s: %s", TruffleToLibGraal.class, m);
                check(Modifier.isStatic(m.getModifiers()) && Modifier.isNative(m.getModifiers()), "Method must be static and native: %s", m);
                res = m;
            }
        }
        check(res != null, "Could not find method named \"%s\" in %s", name, hsClass);
        return res;
    }

    static {
        checkTruffleToLibGraalCalls();
    }
}
