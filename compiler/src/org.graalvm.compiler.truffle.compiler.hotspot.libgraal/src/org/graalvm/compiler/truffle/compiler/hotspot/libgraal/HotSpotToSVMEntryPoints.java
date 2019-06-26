/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.HotSpotToSVM.Id.CloseCompilation;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.HotSpotToSVM.Id.CloseDebugContext;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.HotSpotToSVM.Id.CloseDebugContextScope;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.HotSpotToSVM.Id.DoCompile;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.HotSpotToSVM.Id.DumpChannelClose;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.HotSpotToSVM.Id.DumpChannelWrite;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.HotSpotToSVM.Id.GetCompilerConfigurationFactoryName;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.HotSpotToSVM.Id.GetCompilerConfigurationName;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.HotSpotToSVM.Id.GetDataPatchesCount;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.HotSpotToSVM.Id.GetDumpChannel;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.HotSpotToSVM.Id.GetExceptionHandlersCount;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.HotSpotToSVM.Id.GetGraphDumpDirectory;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.HotSpotToSVM.Id.GetInfopoints;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.HotSpotToSVM.Id.GetInfopointsCount;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.HotSpotToSVM.Id.GetInitialOptions;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.HotSpotToSVM.Id.GetMarksCount;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.HotSpotToSVM.Id.GetNodeCount;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.HotSpotToSVM.Id.GetNodeTypes;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.HotSpotToSVM.Id.GetSuppliedString;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.HotSpotToSVM.Id.GetTargetCodeSize;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.HotSpotToSVM.Id.GetTotalFrameSize;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.HotSpotToSVM.Id.GetTruffleCompilationId;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.HotSpotToSVM.Id.GetTruffleCompilationTruffleAST;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.HotSpotToSVM.Id.InitializeCompiler;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.HotSpotToSVM.Id.InitializeRuntime;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.HotSpotToSVM.Id.InstallTruffleCallBoundaryMethods;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.HotSpotToSVM.Id.IsBasicDumpEnabled;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.HotSpotToSVM.Id.IsDumpChannelOpen;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.HotSpotToSVM.Id.Log;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.HotSpotToSVM.Id.OpenCompilation;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.HotSpotToSVM.Id.OpenDebugContext;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.HotSpotToSVM.Id.OpenDebugContextScope;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.HotSpotToSVM.Id.PendingTransferToInterpreterOffset;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.HotSpotToSVM.Id.ReleaseHandle;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.HotSpotToSVM.Id.Shutdown;
import static org.graalvm.compiler.truffle.compiler.hotspot.libgraal.JNIUtil.GetArrayLength;
import static org.graalvm.compiler.truffle.compiler.hotspot.libgraal.JNIUtil.GetByteArrayElements;
import static org.graalvm.compiler.truffle.compiler.hotspot.libgraal.JNIUtil.NewByteArray;
import static org.graalvm.compiler.truffle.compiler.hotspot.libgraal.JNIUtil.NewObjectArray;
import static org.graalvm.compiler.truffle.compiler.hotspot.libgraal.JNIUtil.ReleaseByteArrayElements;
import static org.graalvm.compiler.truffle.compiler.hotspot.libgraal.JNIUtil.SetObjectArrayElement;
import static org.graalvm.compiler.truffle.compiler.hotspot.libgraal.JNIUtil.createHSString;
import static org.graalvm.compiler.truffle.compiler.hotspot.libgraal.JNIUtil.createString;
import static org.graalvm.compiler.truffle.compiler.hotspot.libgraal.SVMToHotSpotUtil.getJNIClass;

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
import org.graalvm.compiler.truffle.common.TruffleCompilerRuntimeInstance;
import org.graalvm.compiler.truffle.common.TruffleDebugContext;
import org.graalvm.compiler.truffle.common.TruffleDebugJavaMethod;
import org.graalvm.compiler.truffle.common.TruffleInliningPlan;
import org.graalvm.compiler.truffle.common.VoidGraphStructure;
import org.graalvm.compiler.truffle.common.hotspot.libgraal.HotSpotToSVM;
import org.graalvm.compiler.truffle.compiler.TruffleCompilationIdentifier;
import org.graalvm.compiler.truffle.compiler.TruffleCompilerOptions;
import org.graalvm.compiler.truffle.compiler.TruffleDebugContextImpl;
import org.graalvm.compiler.truffle.compiler.hotspot.HotSpotTruffleCompilerImpl;
import org.graalvm.compiler.truffle.compiler.hotspot.HotSpotTruffleCompilerImpl.Options;
import org.graalvm.compiler.truffle.compiler.hotspot.libgraal.JNI.JArray;
import org.graalvm.compiler.truffle.compiler.hotspot.libgraal.JNI.JByteArray;
import org.graalvm.compiler.truffle.compiler.hotspot.libgraal.JNI.JClass;
import org.graalvm.compiler.truffle.compiler.hotspot.libgraal.JNI.JNIEnv;
import org.graalvm.compiler.truffle.compiler.hotspot.libgraal.JNI.JObject;
import org.graalvm.compiler.truffle.compiler.hotspot.libgraal.JNI.JObjectArray;
import org.graalvm.compiler.truffle.compiler.hotspot.libgraal.JNI.JString;
import org.graalvm.graphio.GraphOutput;
import org.graalvm.libgraal.LibGraal;
import org.graalvm.libgraal.OptionsEncoder;
import org.graalvm.nativeimage.Isolate;
import org.graalvm.nativeimage.Platform.HOSTED_ONLY;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CTypeConversion;
import org.graalvm.nativeimage.c.type.VoidPointer;
import org.graalvm.word.WordFactory;

import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Entry points in SVM for {@linkplain HotSpotToSVM calls} from HotSpot.
 *
 * To trace Truffle calls between HotSpot and SVM, set the environment variable
 * {@value #TRUFFLE_LIBGRAAL_TRACE_LEVEL_ENV_VAR_NAME} to {@code true}.
 */
final class HotSpotToSVMEntryPoints {

    @HotSpotToSVM(InitializeRuntime)
    @SuppressWarnings({"unused", "try"})
    @CEntryPoint(name = "Java_org_graalvm_compiler_truffle_runtime_hotspot_libgraal_HotSpotToSVMCalls_initializeRuntime")
    public static long initializeRuntime(JNIEnv env, JClass hsClazz, @CEntryPoint.IsolateThreadContext long isolateThreadId,
                    JObject truffleRuntime, long classLoaderDelegateId) {
        try (HotSpotToSVMScope s = new HotSpotToSVMScope(InitializeRuntime, env)) {
            ResolvedJavaType classLoaderDelegate = LibGraal.unhand(runtime(), ResolvedJavaType.class, classLoaderDelegateId);
            HSTruffleCompilerRuntime hsTruffleRuntime = new HSTruffleCompilerRuntime(env, truffleRuntime, classLoaderDelegate, HotSpotGraalOptionValues.defaultOptions());
            TruffleCompilerRuntimeInstance.initialize(hsTruffleRuntime);
            long truffleRuntimeHandle = SVMObjectHandles.create(hsTruffleRuntime);
            return truffleRuntimeHandle;
        } catch (Throwable t) {
            JNIExceptionWrapper.throwInHotSpot(env, t);
            return 0L;
        }
    }

    @HotSpotToSVM(InitializeCompiler)
    @SuppressWarnings({"unused", "try"})
    @CEntryPoint(name = "Java_org_graalvm_compiler_truffle_runtime_hotspot_libgraal_HotSpotToSVMCalls_initializeCompiler")
    public static long initializeCompiler(JNIEnv env, JClass hsClazz, @CEntryPoint.IsolateThreadContext long isolateThreadId, long truffleRuntimeHandle) {
        try (HotSpotToSVMScope s = new HotSpotToSVMScope(InitializeCompiler, env)) {
            HSTruffleCompilerRuntime hsTruffleRuntime = SVMObjectHandles.resolve(truffleRuntimeHandle, HSTruffleCompilerRuntime.class);
            return SVMObjectHandles.create(HotSpotTruffleCompilerImpl.create(hsTruffleRuntime));
        } catch (Throwable t) {
            JNIExceptionWrapper.throwInHotSpot(env, t);
            return 0;
        }
    }

    @HotSpotToSVM(ReleaseHandle)
    @SuppressWarnings({"unused", "try"})
    @CEntryPoint(name = "Java_org_graalvm_compiler_truffle_runtime_hotspot_libgraal_HotSpotToSVMCalls_releaseHandle")
    public static void releaseHandle(JNIEnv env, JClass hsClazz, @CEntryPoint.IsolateThreadContext long isolateThreadId, long handle) {
        try (HotSpotToSVMScope s = new HotSpotToSVMScope(ReleaseHandle, env)) {
            SVMObjectHandles.remove(handle);
        } catch (Throwable t) {
            JNIExceptionWrapper.throwInHotSpot(env, t);
        }
    }

    @HotSpotToSVM(GetCompilerConfigurationFactoryName)
    @SuppressWarnings({"unused", "try"})
    @CEntryPoint(name = "Java_org_graalvm_compiler_truffle_runtime_hotspot_libgraal_HotSpotToSVMCalls_getCompilerConfigurationFactoryName")
    public static JString getCompilerConfigurationFactoryName(JNIEnv env, JClass hsClazz, @CEntryPoint.IsolateThreadContext long isolateThreadId) {
        HotSpotToSVMScope scope = new HotSpotToSVMScope(GetCompilerConfigurationFactoryName, env);
        try (HotSpotToSVMScope s = scope) {
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

    @HotSpotToSVM(OpenCompilation)
    @SuppressWarnings({"unused", "try"})
    @CEntryPoint(name = "Java_org_graalvm_compiler_truffle_runtime_hotspot_libgraal_HotSpotToSVMCalls_openCompilation")
    public static long openCompilation(JNIEnv env, JClass hsClazz, @CEntryPoint.IsolateThreadContext long isolateThreadId, long handle, JObject hsCompilable) {
        HotSpotToSVMScope scope = new HotSpotToSVMScope(OpenCompilation, env);
        try (HotSpotToSVMScope s = scope) {
            HotSpotTruffleCompilerImpl compiler = SVMObjectHandles.resolve(handle, HotSpotTruffleCompilerImpl.class);
            CompilableTruffleAST compilable = new HSCompilableTruffleAST(env, hsCompilable);
            TruffleCompilation compilation = compiler.openCompilation(compilable);
            assert compilation instanceof TruffleCompilationIdentifier;
            return SVMObjectHandles.create(compilation);
        } catch (Throwable t) {
            JNIExceptionWrapper.throwInHotSpot(env, t);
            return 0;
        }
    }

    @HotSpotToSVM(GetCompilerConfigurationName)
    @SuppressWarnings({"unused", "try"})
    @CEntryPoint(name = "Java_org_graalvm_compiler_truffle_runtime_hotspot_libgraal_HotSpotToSVMCalls_getCompilerConfigurationName")
    public static JString getCompilerConfigurationName(JNIEnv env, JClass hsClazz, @CEntryPoint.IsolateThreadContext long isolateId, long handle) {
        HotSpotToSVMScope scope = new HotSpotToSVMScope(GetCompilerConfigurationName, env);
        try (HotSpotToSVMScope s = scope) {
            HotSpotTruffleCompilerImpl compiler = SVMObjectHandles.resolve(handle, HotSpotTruffleCompilerImpl.class);
            String name = compiler.getCompilerConfigurationName();
            scope.setObjectResult(createHSString(env, name));
        } catch (Throwable t) {
            JNIExceptionWrapper.throwInHotSpot(env, t);
            return WordFactory.nullPointer();
        }
        return scope.getObjectResult();
    }

    @HotSpotToSVM(DoCompile)
    @SuppressWarnings({"unused", "try"})
    @CEntryPoint(name = "Java_org_graalvm_compiler_truffle_runtime_hotspot_libgraal_HotSpotToSVMCalls_doCompile")
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
        try (HotSpotToSVMScope scope = new HotSpotToSVMScope(DoCompile, env)) {
            TruffleCompilationIdentifier compilation = SVMObjectHandles.resolve(compilationHandle, TruffleCompilationIdentifier.class);
            try (CompilationContext hotSpotObjectConstantScope = HotSpotGraalServices.openLocalCompilationContext(compilation)) {
                HotSpotTruffleCompilerImpl compiler = SVMObjectHandles.resolve(compilerHandle, HotSpotTruffleCompilerImpl.class);
                TruffleDebugContext debugContext = SVMObjectHandles.resolve(debugContextHandle, TruffleDebugContext.class);
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

    @HotSpotToSVM(CloseCompilation)
    @SuppressWarnings({"unused", "try"})
    @CEntryPoint(name = "Java_org_graalvm_compiler_truffle_runtime_hotspot_libgraal_HotSpotToSVMCalls_closeCompilation")
    public static void closeCompilation(JNIEnv env, JClass hsClazz, @CEntryPoint.IsolateThreadContext long isolateThreadId, long compilationHandle) {
        try (HotSpotToSVMScope scope = new HotSpotToSVMScope(CloseCompilation, env)) {
            TruffleCompilation compilation = SVMObjectHandles.resolve(compilationHandle, TruffleCompilation.class);
            HSCompilableTruffleAST compilable = (HSCompilableTruffleAST) compilation.getCompilable();
            compilable.release(env);
            HSObject.cleanHandles(env);
        } catch (Throwable t) {
            JNIExceptionWrapper.throwInHotSpot(env, t);
        }
    }

    @HotSpotToSVM(Shutdown)
    @SuppressWarnings({"unused", "try"})
    @CEntryPoint(name = "Java_org_graalvm_compiler_truffle_runtime_hotspot_libgraal_HotSpotToSVMCalls_shutdown")
    public static void shutdown(JNIEnv env, JClass hsClazz, @CEntryPoint.IsolateThreadContext long isolateThreadId, long handle) {
        try (HotSpotToSVMScope s = new HotSpotToSVMScope(Shutdown, env)) {
            HotSpotTruffleCompilerImpl compiler = SVMObjectHandles.resolve(handle, HotSpotTruffleCompilerImpl.class);
            compiler.shutdown();
        } catch (Throwable t) {
            JNIExceptionWrapper.throwInHotSpot(env, t);
        }
    }

    @HotSpotToSVM(InstallTruffleCallBoundaryMethods)
    @SuppressWarnings({"unused", "try"})
    @CEntryPoint(name = "Java_org_graalvm_compiler_truffle_runtime_hotspot_libgraal_HotSpotToSVMCalls_installTruffleCallBoundaryMethods")
    public static void installTruffleCallBoundaryMethods(JNIEnv env, JClass hsClazz, @CEntryPoint.IsolateThreadContext long isolateThreadId, long handle) {
        try (HotSpotToSVMScope s = new HotSpotToSVMScope(InstallTruffleCallBoundaryMethods, env)) {
            HotSpotTruffleCompilerImpl compiler = SVMObjectHandles.resolve(handle, HotSpotTruffleCompilerImpl.class);
            compiler.installTruffleCallBoundaryMethods();
        } catch (Throwable t) {
            JNIExceptionWrapper.throwInHotSpot(env, t);
        }
    }

    @HotSpotToSVM(PendingTransferToInterpreterOffset)
    @SuppressWarnings({"unused", "try"})
    @CEntryPoint(name = "Java_org_graalvm_compiler_truffle_runtime_hotspot_libgraal_HotSpotToSVMCalls_pendingTransferToInterpreterOffset")
    public static int pendingTransferToInterpreterOffset(JNIEnv env, JClass hsClazz, @CEntryPoint.IsolateThreadContext long isolateThreadId, long handle) {
        try (HotSpotToSVMScope scope = new HotSpotToSVMScope(PendingTransferToInterpreterOffset, env)) {
            HotSpotTruffleCompilerImpl compiler = SVMObjectHandles.resolve(handle, HotSpotTruffleCompilerImpl.class);
            return compiler.pendingTransferToInterpreterOffset();
        } catch (Throwable t) {
            JNIExceptionWrapper.throwInHotSpot(env, t);
            return 0;
        }
    }

    @HotSpotToSVM(GetGraphDumpDirectory)
    @SuppressWarnings({"unused", "try"})
    @CEntryPoint(name = "Java_org_graalvm_compiler_truffle_runtime_hotspot_libgraal_HotSpotToSVMCalls_getGraphDumpDirectory")
    public static JString getGraphDumpDirectory(JNIEnv env, JClass hsClazz, @CEntryPoint.IsolateThreadContext long isolateThreadId) {
        HotSpotToSVMScope scope = new HotSpotToSVMScope(GetGraphDumpDirectory, env);
        try (HotSpotToSVMScope s = scope) {
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

    @HotSpotToSVM(Log)
    @SuppressWarnings({"unused", "try"})
    @CEntryPoint(name = "Java_org_graalvm_compiler_truffle_runtime_hotspot_libgraal_HotSpotToSVMCalls_log")
    public static void log(JNIEnv env, JClass hsClazz, @CEntryPoint.IsolateThreadContext long isolateThreadId, JString hsMessage) {
        try (HotSpotToSVMScope s = new HotSpotToSVMScope(Log, env)) {
            String message = createString(env, hsMessage);
            TTY.println(message);
        } catch (Throwable t) {
            JNIExceptionWrapper.throwInHotSpot(env, t);
        }
    }

    @HotSpotToSVM(GetInitialOptions)
    @SuppressWarnings({"unused", "try"})
    @CEntryPoint(name = "Java_org_graalvm_compiler_truffle_runtime_hotspot_libgraal_HotSpotToSVMCalls_getInitialOptions")
    public static JByteArray getInitialOptions(JNIEnv env, JClass hsClazz, @CEntryPoint.IsolateThreadContext long isolateThreadId, long truffleRuntimeHandle) {
        HotSpotToSVMScope scope = new HotSpotToSVMScope(GetInitialOptions, env);
        try (HotSpotToSVMScope s = scope) {
            HSTruffleCompilerRuntime hsTruffleRuntime = SVMObjectHandles.resolve(truffleRuntimeHandle, HSTruffleCompilerRuntime.class);
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

    @HotSpotToSVM(GetSuppliedString)
    @CEntryPoint(name = "Java_org_graalvm_compiler_truffle_runtime_hotspot_libgraal_HotSpotToSVMCalls_getSuppliedString")
    @SuppressWarnings({"unused", "unchecked", "try"})
    public static JString getString(JNIEnv env, JClass hsClazz, @CEntryPoint.IsolateThreadContext long isolateThreadId, long handle) {
        HotSpotToSVMScope scope = new HotSpotToSVMScope(GetSuppliedString, env);
        try (HotSpotToSVMScope s = scope) {
            Supplier<String> orig = SVMObjectHandles.resolve(handle, Supplier.class);
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

    @HotSpotToSVM(GetNodeCount)
    @CEntryPoint(name = "Java_org_graalvm_compiler_truffle_runtime_hotspot_libgraal_HotSpotToSVMCalls_getNodeCount")
    @SuppressWarnings({"unused", "try"})
    public static int getNodeCount(JNIEnv env, JClass hsClazz, @CEntryPoint.IsolateThreadContext long isolateThreadId, long handle) {
        try (HotSpotToSVMScope s = new HotSpotToSVMScope(GetNodeCount, env)) {
            GraphInfo orig = SVMObjectHandles.resolve(handle, GraphInfo.class);
            return orig.getNodeCount();
        } catch (Throwable t) {
            JNIExceptionWrapper.throwInHotSpot(env, t);
            return 0;
        }
    }

    @HotSpotToSVM(GetNodeTypes)
    @CEntryPoint(name = "Java_org_graalvm_compiler_truffle_runtime_hotspot_libgraal_HotSpotToSVMCalls_getNodeTypes")
    @SuppressWarnings({"unused", "try"})
    public static JObjectArray getNodeTypes(JNIEnv env, JClass hsClazz, @CEntryPoint.IsolateThreadContext long isolateThreadId, long handle, boolean simpleNames) {
        HotSpotToSVMScope scope = new HotSpotToSVMScope(GetNodeTypes, env);
        try (HotSpotToSVMScope s = scope) {
            GraphInfo orig = SVMObjectHandles.resolve(handle, GraphInfo.class);
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

    @HotSpotToSVM(GetTargetCodeSize)
    @CEntryPoint(name = "Java_org_graalvm_compiler_truffle_runtime_hotspot_libgraal_HotSpotToSVMCalls_getTargetCodeSize")
    @SuppressWarnings({"unused", "try"})
    public static int getTargetCodeSize(JNIEnv env, JClass hsClazz, @CEntryPoint.IsolateThreadContext long isolateThreadId, long handle) {
        try (HotSpotToSVMScope s = new HotSpotToSVMScope(GetTargetCodeSize, env)) {
            return SVMObjectHandles.resolve(handle, CompilationResultInfo.class).getTargetCodeSize();
        } catch (Throwable t) {
            JNIExceptionWrapper.throwInHotSpot(env, t);
            return 0;
        }
    }

    @HotSpotToSVM(GetTotalFrameSize)
    @CEntryPoint(name = "Java_org_graalvm_compiler_truffle_runtime_hotspot_libgraal_HotSpotToSVMCalls_getTotalFrameSize")
    @SuppressWarnings({"unused", "try"})
    public static int getTotalFrameSize(JNIEnv env, JClass hsClazz, @CEntryPoint.IsolateThreadContext long isolateThreadId, long handle) {
        try (HotSpotToSVMScope s = new HotSpotToSVMScope(GetTotalFrameSize, env)) {
            return SVMObjectHandles.resolve(handle, CompilationResultInfo.class).getTotalFrameSize();
        } catch (Throwable t) {
            JNIExceptionWrapper.throwInHotSpot(env, t);
            return 0;
        }
    }

    @HotSpotToSVM(GetExceptionHandlersCount)
    @CEntryPoint(name = "Java_org_graalvm_compiler_truffle_runtime_hotspot_libgraal_HotSpotToSVMCalls_getExceptionHandlersCount")
    @SuppressWarnings({"unused", "try"})
    public static int getExceptionHandlersCount(JNIEnv env, JClass hsClazz, @CEntryPoint.IsolateThreadContext long isolateThreadId, long handle) {
        try (HotSpotToSVMScope s = new HotSpotToSVMScope(GetExceptionHandlersCount, env)) {
            return SVMObjectHandles.resolve(handle, CompilationResultInfo.class).getExceptionHandlersCount();
        } catch (Throwable t) {
            JNIExceptionWrapper.throwInHotSpot(env, t);
            return 0;
        }
    }

    @HotSpotToSVM(GetInfopointsCount)
    @CEntryPoint(name = "Java_org_graalvm_compiler_truffle_runtime_hotspot_libgraal_HotSpotToSVMCalls_getInfopointsCount")
    @SuppressWarnings({"unused", "try"})
    public static int getInfopointsCount(JNIEnv env, JClass hsClazz, @CEntryPoint.IsolateThreadContext long isolateThreadId, long handle) {
        try (HotSpotToSVMScope s = new HotSpotToSVMScope(GetInfopointsCount, env)) {
            return SVMObjectHandles.resolve(handle, CompilationResultInfo.class).getInfopointsCount();
        } catch (Throwable t) {
            JNIExceptionWrapper.throwInHotSpot(env, t);
            return 0;
        }
    }

    @HotSpotToSVM(GetInfopoints)
    @CEntryPoint(name = "Java_org_graalvm_compiler_truffle_runtime_hotspot_libgraal_HotSpotToSVMCalls_getInfopoints")
    @SuppressWarnings({"unused", "try"})
    public static JObjectArray getInfopoints(JNIEnv env, JClass hsClazz, @CEntryPoint.IsolateThreadContext long isolateThreadId, long handle) {
        HotSpotToSVMScope scope = new HotSpotToSVMScope(GetInfopoints, env);
        try (HotSpotToSVMScope s = scope) {
            String[] infoPoints = SVMObjectHandles.resolve(handle, CompilationResultInfo.class).getInfopoints();
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

    @HotSpotToSVM(GetMarksCount)
    @CEntryPoint(name = "Java_org_graalvm_compiler_truffle_runtime_hotspot_libgraal_HotSpotToSVMCalls_getMarksCount")
    @SuppressWarnings({"unused", "try"})
    public static int getMarksCount(JNIEnv env, JClass hsClazz, @CEntryPoint.IsolateThreadContext long isolateThreadId, long handle) {
        try (HotSpotToSVMScope s = new HotSpotToSVMScope(GetMarksCount, env)) {
            return SVMObjectHandles.resolve(handle, CompilationResultInfo.class).getMarksCount();
        } catch (Throwable t) {
            JNIExceptionWrapper.throwInHotSpot(env, t);
            return 0;
        }
    }

    @HotSpotToSVM(GetDataPatchesCount)
    @CEntryPoint(name = "Java_org_graalvm_compiler_truffle_runtime_hotspot_libgraal_HotSpotToSVMCalls_getDataPatchesCount")
    @SuppressWarnings({"unused", "try"})
    public static int getDataPatchesCount(JNIEnv env, JClass hsClazz, @CEntryPoint.IsolateThreadContext long isolateThreadId, long handle) {
        try (HotSpotToSVMScope s = new HotSpotToSVMScope(GetDataPatchesCount, env)) {
            return SVMObjectHandles.resolve(handle, CompilationResultInfo.class).getDataPatchesCount();
        } catch (Throwable t) {
            JNIExceptionWrapper.throwInHotSpot(env, t);
            return 0;
        }
    }

    @HotSpotToSVM(OpenDebugContext)
    @CEntryPoint(name = "Java_org_graalvm_compiler_truffle_runtime_hotspot_libgraal_HotSpotToSVMCalls_openDebugContext")
    @SuppressWarnings({"unused", "try"})
    public static long openDebugContext(JNIEnv env,
                    JClass hsClazz,
                    @CEntryPoint.IsolateThreadContext long isolateThreadId,
                    long compilerHandle,
                    long compilationHandle,
                    JByteArray hsOptions) {
        try (HotSpotToSVMScope s = new HotSpotToSVMScope(OpenDebugContext, env)) {
            HotSpotTruffleCompilerImpl compiler = SVMObjectHandles.resolve(compilerHandle, HotSpotTruffleCompilerImpl.class);
            TruffleCompilation compilation = SVMObjectHandles.resolve(compilationHandle, TruffleCompilation.class);
            Map<String, Object> options = decodeOptions(env, hsOptions);
            TruffleDebugContext debugContext = compiler.openDebugContext(options, compilation);
            long handle = SVMObjectHandles.create(debugContext);
            return handle;
        } catch (Throwable t) {
            JNIExceptionWrapper.throwInHotSpot(env, t);
            return 0;
        }
    }

    @HotSpotToSVM(OpenDebugContextScope)
    @CEntryPoint(name = "Java_org_graalvm_compiler_truffle_runtime_hotspot_libgraal_HotSpotToSVMCalls_openDebugContextScope")
    @SuppressWarnings({"unused", "try"})
    public static long openDebugContextScope(JNIEnv env, JClass hsClazz, @CEntryPoint.IsolateThreadContext long isolateThreadId, long handle, JString hsName, long compilationHandle) {
        try (HotSpotToSVMScope s = new HotSpotToSVMScope(OpenDebugContextScope, env)) {
            TruffleDebugContext debugContext = SVMObjectHandles.resolve(handle, TruffleDebugContext.class);
            String name = createString(env, hsName);
            AutoCloseable scope;
            if (compilationHandle == 0) {
                scope = debugContext.scope(name);
            } else {
                TruffleCompilationIdentifier compilation = SVMObjectHandles.resolve(compilationHandle, TruffleCompilationIdentifier.class);
                scope = debugContext.scope(name, new TruffleDebugJavaMethod(compilation.getCompilable()));
            }
            if (scope == null) {
                return 0;
            }
            long scopeHandle = SVMObjectHandles.create(scope);
            return scopeHandle;
        } catch (Throwable t) {
            JNIExceptionWrapper.throwInHotSpot(env, t);
            return 0;
        }
    }

    @HotSpotToSVM(CloseDebugContext)
    @CEntryPoint(name = "Java_org_graalvm_compiler_truffle_runtime_hotspot_libgraal_HotSpotToSVMCalls_closeDebugContext")
    @SuppressWarnings({"unused", "try"})
    public static void closeDebugContext(JNIEnv env, JClass hsClazz, @CEntryPoint.IsolateThreadContext long isolateThreadId, long handle) {
        try (HotSpotToSVMScope s = new HotSpotToSVMScope(CloseDebugContext, env)) {
            TruffleDebugContext debugContext = SVMObjectHandles.resolve(handle, TruffleDebugContext.class);
            debugContext.close();
        } catch (Throwable t) {
            JNIExceptionWrapper.throwInHotSpot(env, t);
        }
    }

    @HotSpotToSVM(CloseDebugContextScope)
    @CEntryPoint(name = "Java_org_graalvm_compiler_truffle_runtime_hotspot_libgraal_HotSpotToSVMCalls_closeDebugContextScope")
    @SuppressWarnings({"unused", "try"})
    public static void closeDebugContextScope(JNIEnv env, JClass hsClazz, @CEntryPoint.IsolateThreadContext long isolateThreadId, long handle) {
        try (HotSpotToSVMScope s = new HotSpotToSVMScope(CloseDebugContextScope, env)) {
            AutoCloseable scope = SVMObjectHandles.resolve(handle, DebugContext.Scope.class);
            scope.close();
        } catch (Throwable t) {
            JNIExceptionWrapper.throwInHotSpot(env, t);
        }
    }

    @HotSpotToSVM(IsBasicDumpEnabled)
    @CEntryPoint(name = "Java_org_graalvm_compiler_truffle_runtime_hotspot_libgraal_HotSpotToSVMCalls_isBasicDumpEnabled")
    @SuppressWarnings({"unused", "try"})
    public static boolean isBasicDumpEnabled(JNIEnv env, JClass hsClazz, @CEntryPoint.IsolateThreadContext long isolateThreadId, long handle) {
        try (HotSpotToSVMScope s = new HotSpotToSVMScope(IsBasicDumpEnabled, env)) {
            TruffleDebugContext debugContext = SVMObjectHandles.resolve(handle, TruffleDebugContext.class);
            return debugContext.isDumpEnabled();
        } catch (Throwable t) {
            JNIExceptionWrapper.throwInHotSpot(env, t);
            return false;
        }
    }

    @HotSpotToSVM(GetTruffleCompilationTruffleAST)
    @CEntryPoint(name = "Java_org_graalvm_compiler_truffle_runtime_hotspot_libgraal_HotSpotToSVMCalls_getTruffleCompilationTruffleAST")
    @SuppressWarnings({"unused", "try"})
    public static JObject getTruffleCompilationTruffleAST(JNIEnv env, JClass hsClazz, @CEntryPoint.IsolateThreadContext long isolateThreadId, long compilationHandle) {
        HotSpotToSVMScope scope = new HotSpotToSVMScope(GetTruffleCompilationTruffleAST, env);
        try (HotSpotToSVMScope s = scope) {
            HSCompilableTruffleAST compilable = (HSCompilableTruffleAST) SVMObjectHandles.resolve(compilationHandle, TruffleCompilation.class).getCompilable();
            scope.setObjectResult(compilable.getHandle());
        } catch (Throwable t) {
            JNIExceptionWrapper.throwInHotSpot(env, t);
            return WordFactory.nullPointer();
        }
        return scope.getObjectResult();
    }

    @HotSpotToSVM(GetTruffleCompilationId)
    @CEntryPoint(name = "Java_org_graalvm_compiler_truffle_runtime_hotspot_libgraal_HotSpotToSVMCalls_getTruffleCompilationId")
    @SuppressWarnings({"unused", "try"})
    public static JString getTruffleCompilationId(JNIEnv env, JClass hsClazz, @CEntryPoint.IsolateThreadContext long isolateThreadId, long compilationHandle) {
        HotSpotToSVMScope scope = new HotSpotToSVMScope(GetTruffleCompilationId, env);
        try (HotSpotToSVMScope s = scope) {
            String compilationId = SVMObjectHandles.resolve(compilationHandle, TruffleCompilationIdentifier.class).toString(CompilationIdentifier.Verbosity.ID);
            scope.setObjectResult(createHSString(env, compilationId));
        } catch (Throwable t) {
            JNIExceptionWrapper.throwInHotSpot(env, t);
            return WordFactory.nullPointer();
        }
        return scope.getObjectResult();
    }

    @HotSpotToSVM(GetDumpChannel)
    @CEntryPoint(name = "Java_org_graalvm_compiler_truffle_runtime_hotspot_libgraal_HotSpotToSVMCalls_getDumpChannel")
    @SuppressWarnings({"unused", "try"})
    public static long getDumpChannel(JNIEnv env, JClass hsClazz, @CEntryPoint.IsolateThreadContext long isolateThreadId, long debugContextHandle) {
        try (HotSpotToSVMScope s = new HotSpotToSVMScope(GetDumpChannel, env)) {
            TruffleDebugContextImpl debugContext = SVMObjectHandles.resolve(debugContextHandle, TruffleDebugContextImpl.class);
            GraphOutput<Void, ?> graphOutput = debugContext.buildOutput(GraphOutput.newBuilder(VoidGraphStructure.INSTANCE).protocolVersion(6, 1));
            return SVMObjectHandles.create(graphOutput);
        } catch (Throwable t) {
            JNIExceptionWrapper.throwInHotSpot(env, t);
            return 0;
        }
    }

    @HotSpotToSVM(IsDumpChannelOpen)
    @CEntryPoint(name = "Java_org_graalvm_compiler_truffle_runtime_hotspot_libgraal_HotSpotToSVMCalls_isDumpChannelOpen")
    @SuppressWarnings({"unused", "try"})
    public static boolean isDumpChannelOpen(JNIEnv env, JClass hsClazz, @CEntryPoint.IsolateThreadContext long isolateThreadId, long channelHandle) {
        try (HotSpotToSVMScope s = new HotSpotToSVMScope(IsDumpChannelOpen, env)) {
            return SVMObjectHandles.resolve(channelHandle, WritableByteChannel.class).isOpen();
        } catch (Throwable t) {
            JNIExceptionWrapper.throwInHotSpot(env, t);
            return false;
        }
    }

    @HotSpotToSVM(DumpChannelWrite)
    @CEntryPoint(name = "Java_org_graalvm_compiler_truffle_runtime_hotspot_libgraal_HotSpotToSVMCalls_dumpChannelWrite")
    @SuppressWarnings({"unused", "try"})
    public static int dumpChannelWrite(JNIEnv env, JClass hsClass, @CEntryPoint.IsolateThreadContext long isolateThreadId, long channelHandle, JObject hsSource, int capacity, int position,
                    int limit) {
        try (HotSpotToSVMScope s = new HotSpotToSVMScope(DumpChannelWrite, env)) {
            WritableByteChannel channel = SVMObjectHandles.resolve(channelHandle, WritableByteChannel.class);
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

    @HotSpotToSVM(DumpChannelClose)
    @CEntryPoint(name = "Java_org_graalvm_compiler_truffle_runtime_hotspot_libgraal_HotSpotToSVMCalls_dumpChannelClose")
    @SuppressWarnings({"unused", "try"})
    public static void dumpChannelClose(JNIEnv env, JClass hsClass, @CEntryPoint.IsolateThreadContext long isolateThreadId, long channelHandle) {
        try (HotSpotToSVMScope s = new HotSpotToSVMScope(IsDumpChannelOpen, env)) {
            SVMObjectHandles.resolve(channelHandle, WritableByteChannel.class).close();
        } catch (Throwable t) {
            JNIExceptionWrapper.throwInHotSpot(env, t);
        }
    }

    /*----------------- TRACING ------------------*/

    private static Integer traceLevel;

    private static final String TRUFFLE_LIBGRAAL_TRACE_LEVEL_ENV_VAR_NAME = "TRUFFLE_LIBGRAAL_TRACE_LEVEL";

    /**
     * Checks if {@link SVMToHotSpotUtil} and {@link HotSpotToSVMEntryPoints} are verbose.
     */
    private static int traceLevel() {
        if (traceLevel == null) {
            String var = System.getenv(TRUFFLE_LIBGRAAL_TRACE_LEVEL_ENV_VAR_NAME);
            if (var != null) {
                try {
                    traceLevel = Integer.parseInt(var);
                } catch (NumberFormatException e) {
                    TTY.printf("Invalid value for %s: %s%n", TRUFFLE_LIBGRAAL_TRACE_LEVEL_ENV_VAR_NAME, e);
                    traceLevel = 0;
                }
            } else {
                traceLevel = 0;
            }
        }
        return traceLevel;
    }

    static boolean tracingAt(int level) {
        return traceLevel() >= level;
    }

    /**
     * Emits a trace line composed of {@code format} and {@code args} if the tracing level equal to
     * or greater than {@code level}.
     */
    static void trace(int level, String format, Object... args) {
        if (traceLevel() >= level) {
            HotSpotToSVMScope scope = HotSpotToSVMScope.scopeOrNull();
            String indent = scope == null ? "" : new String(new char[2 + (scope.depth() * 2)]).replace('\0', ' ');
            TTY.printf(indent + format + "%n", args);
        }
    }

    /*----------------- CHECKING ------------------*/

    /**
     * Checks that all {@link HotSpotToSVM}s are implemented and their HotSpot/SVM ends points
     * match.
     */
    @Platforms(HOSTED_ONLY.class)
    private static void checkHotSpotToSVMCalls() throws InternalError {
        try {
            Class<?> hsClass = Class.forName("org.graalvm.compiler.truffle.runtime.hotspot.libgraal.HotSpotToSVMCalls");
            Set<HotSpotToSVM.Id> unimplemented = EnumSet.allOf(HotSpotToSVM.Id.class);
            for (Method svmMethod : HotSpotToSVMEntryPoints.class.getDeclaredMethods()) {
                HotSpotToSVM call = svmMethod.getAnnotation(HotSpotToSVM.class);
                if (call != null) {
                    check(Modifier.isStatic(svmMethod.getModifiers()), "Method annotated by %s must be static: %s", HotSpotToSVM.class, svmMethod);
                    CEntryPoint ep = svmMethod.getAnnotation(CEntryPoint.class);
                    check(ep != null, "Method annotated by %s must also be annotated by %s: %s", HotSpotToSVM.class, CEntryPoint.class, svmMethod);
                    String name = ep.name();
                    String prefix = "Java_" + hsClass.getName().replace('.', '_') + '_';
                    check(name.startsWith(prefix), "Method must be a JNI entry point for a method in %s: %s", hsClass, svmMethod);
                    name = name.substring(prefix.length());
                    Method hsMethod = findHSMethod(hsClass, name);
                    Class<?>[] svmParameters = svmMethod.getParameterTypes();
                    Class<?>[] hsParameters = hsMethod.getParameterTypes();
                    check(hsParameters.length + 2 == svmParameters.length, "%s should have 2 more parameters than %s", svmMethod, hsMethod);
                    check(svmParameters.length >= 3, "Expect at least 3 parameters: %s", svmMethod);
                    check(svmParameters[0] == JNIEnv.class, "Parameter 0 must be of type %s: %s", JNIEnv.class, svmMethod);
                    check(svmParameters[1] == JClass.class, "Parameter 1 must be of type %s: %s", JClass.class, svmMethod);
                    if (ep.builtin() == CEntryPoint.Builtin.ATTACH_THREAD) {
                        check(svmParameters[2] == Isolate.class, "Parameter 2 must be of type %s: %s", Isolate.class, svmMethod);
                    } else {
                        check(svmParameters[2] == long.class, "Parameter 2 must be of type long: %s", svmMethod);
                    }

                    check(hsParameters[0] == long.class, "Parameter 0 must be of type long: %s", hsMethod);

                    for (int i = 3, j = 1; i < svmParameters.length; i++, j++) {
                        Class<?> svm = svmParameters[i];
                        Class<?> hs = hsParameters[j];
                        Class<?> hsExpect;
                        if (hs.isPrimitive()) {
                            hsExpect = svm;
                        } else {
                            if (svm == JString.class) {
                                hsExpect = String.class;
                            } else if (svm == JByteArray.class) {
                                hsExpect = byte[].class;
                            } else {
                                check(svm == JObject.class, "must be");
                                hsExpect = Object.class;
                            }
                        }
                        check(hsExpect.isAssignableFrom(hs), "HotSpot parameter %d (%s) incompatible with SVM parameter %d (%s): %s", j, hs.getName(), i, svm.getName(), hsMethod);
                    }
                    unimplemented.remove(call.value());
                }
            }
            check(unimplemented.isEmpty(), "Unimplemented SVM calls: %s", unimplemented);
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
                HotSpotToSVM call = m.getAnnotation(HotSpotToSVM.class);
                check(call != null, "Method must be annotated by %s: %s", HotSpotToSVM.class, m);
                check(Modifier.isStatic(m.getModifiers()) && Modifier.isNative(m.getModifiers()), "Method must be static and native: %s", m);
                res = m;
            }
        }
        check(res != null, "Could not find method named \"%s\" in %s", name, hsClass);
        return res;
    }

    static {
        checkHotSpotToSVMCalls();
    }
}
