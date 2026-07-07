/*
 * Copyright (c) 2025, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.interpreter.ristretto;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

import org.graalvm.collections.EconomicMap;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.c.function.CFunctionPointer;
import org.graalvm.nativeimage.impl.RuntimeReflectionSupport;
import org.graalvm.word.impl.Word;

import com.oracle.svm.core.code.CodeInfoEncoder;
import com.oracle.svm.core.deopt.SubstrateSpeculationLog;
import com.oracle.svm.core.graal.code.SubstrateCompilationIdentifier;
import com.oracle.svm.core.graal.code.SubstrateCompilationResult;
import com.oracle.svm.core.graal.meta.RuntimeConfiguration;
import com.oracle.svm.core.graal.meta.SubstrateReplacements;
import com.oracle.svm.core.meta.SharedType;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.option.RuntimeOptionParserPolicy;
import com.oracle.svm.guest.staging.log.Log;
import com.oracle.svm.guest.staging.option.RuntimeOptionValues;
import com.oracle.svm.graal.RuntimeCompilationSupport;
import com.oracle.svm.graal.SubstrateGraalUtils;
import com.oracle.svm.graal.meta.RuntimeCodeInstaller;
import com.oracle.svm.graal.meta.SubstrateField;
import com.oracle.svm.graal.meta.SubstrateInstalledCodeImpl;
import com.oracle.svm.graal.meta.SubstrateMetaAccess;
import com.oracle.svm.graal.meta.SubstrateMethod;
import com.oracle.svm.graal.meta.SubstrateType;
import com.oracle.svm.shared.Uninterruptible;
import com.oracle.svm.hosted.image.PreserveOptionsSupport;
import com.oracle.svm.interpreter.metadata.InterpreterResolvedJavaField;
import com.oracle.svm.interpreter.metadata.InterpreterResolvedJavaMethod;
import com.oracle.svm.interpreter.metadata.InterpreterResolvedJavaType;
import com.oracle.svm.interpreter.metadata.InterpreterResolvedObjectType;
import com.oracle.svm.interpreter.ristretto.compile.RistrettoGraphBuilderPhase;
import com.oracle.svm.interpreter.ristretto.compile.RistrettoGraphBuilderPlugins;
import com.oracle.svm.interpreter.ristretto.compile.RistrettoInstalledCode;
import com.oracle.svm.interpreter.ristretto.compile.RistrettoNoDeoptPhase;
import com.oracle.svm.interpreter.ristretto.compile.RistrettoOnStackReplacementPhase;
import com.oracle.svm.interpreter.ristretto.compile.RistrettoSpeculationLog;
import com.oracle.svm.interpreter.ristretto.compile.RistrettoSpeculationLog.CompilationSpeculationLog;
import com.oracle.svm.interpreter.ristretto.meta.RistrettoConstantReflectionProvider;
import com.oracle.svm.interpreter.ristretto.meta.RistrettoField;
import com.oracle.svm.interpreter.ristretto.meta.RistrettoMethodHandleAccessProvider;
import com.oracle.svm.interpreter.ristretto.meta.RistrettoMetaAccess;
import com.oracle.svm.interpreter.ristretto.meta.RistrettoMethod;
import com.oracle.svm.interpreter.ristretto.meta.RistrettoReplacements;
import com.oracle.svm.interpreter.ristretto.meta.RistrettoStampProvider;
import com.oracle.svm.interpreter.ristretto.meta.RistrettoType;
import com.oracle.svm.interpreter.ristretto.verify.RistrettoGraphJVMCITypeVerifier;
import com.oracle.svm.shared.util.VMError;
import com.oracle.svm.shared.option.CommonOptionParser;

import jdk.graal.compiler.core.common.PermanentBailoutException;
import jdk.graal.compiler.api.replacements.SnippetReflectionProvider;
import jdk.graal.compiler.code.CompilationResult;
import jdk.graal.compiler.core.CompilationWatchDog;
import jdk.graal.compiler.core.CompilationWrapper;
import jdk.graal.compiler.core.GraalCompiler;
import jdk.graal.compiler.core.target.Backend;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.java.GraphBuilderPhase;
import jdk.graal.compiler.lir.asm.CompilationResultBuilderFactory;
import jdk.graal.compiler.lir.phases.LIRSuites;
import jdk.graal.compiler.nodes.GraphState;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugins;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.nodes.spi.ProfileProvider;
import jdk.graal.compiler.nodes.spi.StableProfileProvider;
import jdk.graal.compiler.options.OptionKey;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.BasePhase;
import jdk.graal.compiler.phases.OptimisticOptimizations;
import jdk.graal.compiler.phases.Phase;
import jdk.graal.compiler.phases.PhaseSuite;
import jdk.graal.compiler.phases.common.HighTierLoweringPhase;
import jdk.graal.compiler.phases.common.LowTierLoweringPhase;
import jdk.graal.compiler.phases.common.LoweringPhase;
import jdk.graal.compiler.phases.common.MidTierLoweringPhase;
import jdk.graal.compiler.phases.tiers.HighTierContext;
import jdk.graal.compiler.phases.tiers.Suites;
import jdk.graal.compiler.phases.util.Providers;
import jdk.graal.compiler.printer.GraalDebugHandlersFactory;
import jdk.graal.compiler.replacements.MethodHandleWithExceptionPlugin;
import jdk.graal.compiler.replacements.StandardGraphBuilderPlugins;
import jdk.graal.compiler.replacements.TargetGraphBuilderPlugins;
import jdk.vm.ci.code.InstalledCode;
import jdk.vm.ci.code.site.Infopoint;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.SpeculationLog;

public class RistrettoUtils {

    /*
     * Entry BCI used for ordinary invocation compilations. This mirrors
     * jdk.vm.ci.runtime.JVMCICompiler.INVOCATION_ENTRY_BCI without importing the concealed
     * jdk.vm.ci.runtime package into the interpreter module.
     */
    public static final int INVOCATION_ENTRY_BCI = -1;

    private static OSRGetters osrGetters;

    /**
     * Forces the given type to be preserved in the native image and registered for runtime
     * reflection.
     * <p>
     * Registers the class with the native-image
     * {@link org.graalvm.nativeimage.impl.RuntimeReflectionSupport} so that its type metadata is
     * retained at runtime. This prevents the image builder from pruning the type and makes it
     * available to the Ristretto interpreter/JIT infrastructure at run time. This means classes
     * loaded at runtime can use the preserved types.
     *
     * @see org.graalvm.nativeimage.impl.RuntimeReflectionSupport
     * @see com.oracle.svm.hosted.image.PreserveOptionsSupport
     */
    public static void forcePreserveType(Class<?> c) {
        final RuntimeReflectionSupport reflection = ImageSingletons.lookup(RuntimeReflectionSupport.class);
        PreserveOptionsSupport.registerType(reflection, c);
    }

    /**
     * Determines if the given method has bytecode available to use at runtime. This means we have,
     * through whatever way (class files or serialized to runtime) bytecodes available to interpret
     * and use for bytecode-based JIT compilation.
     */
    public static boolean runtimeBytecodesAvailable(ResolvedJavaMethod method) {
        ResolvedJavaType type = method.getDeclaringClass();
        if (type.isPrimitive()) {
            return false; // Primitives are always hosted (no bytecode interpretation)
        }
        InterpreterResolvedJavaMethod iMethod = null;
        if (method instanceof RistrettoMethod rMethod) {
            iMethod = rMethod.getInterpreterMethod();
        } else if (method instanceof InterpreterResolvedJavaMethod interpreterResolvedJavaMethod) {
            iMethod = interpreterResolvedJavaMethod;
        } else {
            throw GraalError.shouldNotReachHere("Unknown interpreter method " + method);
        }
        return iMethod.getInterpretedCode() != null;
    }

    /**
     * Determines if the given method was compiled hosted already during image building.
     */
    public static boolean wasAOTCompiled(ResolvedJavaMethod method) {
        ResolvedJavaType type = method.getDeclaringClass();
        if (type.isPrimitive()) {
            return true;
        }
        InterpreterResolvedJavaMethod iMethod = null;
        if (method instanceof RistrettoMethod rMethod) {
            iMethod = rMethod.getInterpreterMethod();
        } else if (method instanceof InterpreterResolvedJavaMethod interpreterResolvedJavaMethod) {
            iMethod = interpreterResolvedJavaMethod;
        } else {
            throw GraalError.shouldNotReachHere("Unknown interpreter method " + method);
        }
        return iMethod.hasNativeEntryPoint();
    }

    public static boolean canInlineAOT(InterpreterResolvedJavaMethod method) {
        GraalError.guarantee(method != null, "method must not be null");
        if (!wasAOTCompiled(method)) {
            return false;
        }
        // java.lang.Object::<init> can be inlined also at ristretto runtime, its special in that we
        // do not have its bytecode but its always available at runtime and its empty
        return method.getDeclaringClass().isJavaLangObject() && method.isConstructor() && method.getSignature().getParameterCount(false) == 0;
    }

    /** Determines whether the given type has been loaded at runtime. */
    public static boolean isRuntimeLoaded(ResolvedJavaType type) {
        DynamicHub hub = null;
        if (type instanceof RistrettoType rType) {
            hub = rType.getHub();
        } else if (type instanceof InterpreterResolvedJavaType interpreterResolvedJavaType) {
            hub = DynamicHub.fromClass(interpreterResolvedJavaType.getJavaClass());
        } else if (type instanceof SubstrateType sType) {
            hub = sType.getHub();
        } else {
            throw GraalError.shouldNotReachHere("Unknown type " + type);
        }
        return hub.isRuntimeLoaded();
    }

    /**
     * Returns the runtime Java declaring class for the given method across the three method/type
     * representations Ristretto can see while parsing: runtime-loaded {@link RistrettoType}, direct
     * interpreter metadata, and shared AOT metadata.
     */
    public static Class<?> getDeclaringJavaClass(ResolvedJavaMethod method) {
        ResolvedJavaType declaringClass = method.getDeclaringClass();
        if (declaringClass instanceof RistrettoType ristrettoType) {
            return ristrettoType.getInterpreterType().getJavaClass();
        } else if (declaringClass instanceof InterpreterResolvedJavaType interpreterType) {
            return interpreterType.getJavaClass();
        } else if (declaringClass instanceof SharedType sharedType) {
            return DynamicHub.toClass(sharedType.getHub());
        }
        throw VMError.shouldNotReachHere("Unexpected declaring class for runtime Java declaring-class lookup: " + declaringClass);
    }

    /**
     * Returns the preserved Ristretto OSR helper method that loads one interpreter local or lock
     * object from the active OSR transfer state.
     *
     * The lookup is cached in {@link RistrettoUtils} instead of the OSR graph phase so the preserved
     * helper API has a single owner. The cache is initialized lazily: resolving the helpers touches
     * {@link DynamicHub#fromClass(Class)}, which must only happen when runtime compilation actually
     * needs OSR helper methods.
     */
    public static ResolvedJavaMethod lookupOSRGetter(JavaKind kind, boolean lock) {
        OSRGetters getters = osrGetters();
        if (lock) {
            return getters.lockObject();
        }
        switch (kind) {
            case Int:
                return getters.intLocal();
            case Float:
                return getters.floatLocal();
            case Long:
                return getters.longLocal();
            case Double:
                return getters.doubleLocal();
            case Object:
                return getters.objectLocal();
            default:
                throw new PermanentBailoutException("Unsupported Ristretto OSR local kind: %s", kind);
        }
    }

    private static synchronized OSRGetters osrGetters() {
        if (osrGetters == null) {
            osrGetters = new OSRGetters(resolveOSRGetter("getIntLocal", "(I)I"),
                            resolveOSRGetter("getFloatLocal", "(I)F"),
                            resolveOSRGetter("getLongLocal", "(I)J"),
                            resolveOSRGetter("getDoubleLocal", "(I)D"),
                            resolveOSRGetter("getObjectLocal", "(I)Ljava/lang/Object;"),
                            resolveOSRGetter("getLockObject", "(I)Ljava/lang/Object;"));
        }
        return osrGetters;
    }

    private static ResolvedJavaMethod resolveOSRGetter(String methodName, String descriptor) {
        InterpreterResolvedJavaType osrSupportType = (InterpreterResolvedJavaType) DynamicHub.fromClass(RistrettoOSRSupport.class).getInterpreterType();
        if (osrSupportType == null) {
            throw VMError.shouldNotReachHere("Ristretto OSR support type is not preserved for runtime compilation.");
        }
        for (InterpreterResolvedJavaMethod method : osrSupportType.getDeclaredMethods(true)) {
            if (method.getName().equals(methodName) && method.getSignature().toMethodDescriptor().equals(descriptor)) {
                return RistrettoMethod.getOrCreate(method);
            }
        }
        throw VMError.shouldNotReachHere("Could not find Ristretto OSR local getter " + methodName + descriptor + ".");
    }

    private record OSRGetters(ResolvedJavaMethod intLocal, ResolvedJavaMethod floatLocal, ResolvedJavaMethod longLocal,
                    ResolvedJavaMethod doubleLocal, ResolvedJavaMethod objectLocal, ResolvedJavaMethod lockObject) {
    }

    public static CompilationResult compile(DebugContext debug, final SubstrateMethod method) {
        return doCompile(debug, RuntimeCompilationSupport.getRuntimeConfig(), RuntimeCompilationSupport.getLIRSuites(), method);
    }

    /**
     * DEBUG ONLY facility to compile the given method and install the resulting code. Normally done
     * over {@link com.oracle.svm.interpreter.ristretto.profile.RistrettoCompilationManager}. Use
     * only for testing.
     */
    public static SubstrateInstalledCodeImpl compileAndInstallInCrema(RistrettoMethod method) {
        SubstrateInstalledCodeImpl ic = compileAndInstall(method);
        method.installedCode = ic;
        return ic;
    }

    public static StructuredGraph parseOnly(SubstrateMethod method) {
        if (method instanceof RistrettoMethod) {
            final RuntimeConfiguration runtimeConfig = RuntimeCompilationSupport.getRuntimeConfig();
            final DebugContext debug = new DebugContext.Builder(RuntimeOptionValues.singleton().get(), new GraalDebugHandlersFactory(runtimeConfig.getProviders().getSnippetReflection())).build();
            final OptionValues options = debug.getOptions();
            final SpeculationLog speculationLog = new SubstrateSpeculationLog();
            final ProfileProvider profileProvider = new StableProfileProvider();
            final StructuredGraph.AllowAssumptions allowAssumptions = StructuredGraph.AllowAssumptions.NO;
            SubstrateCompilationIdentifier compilationId = new SubstrateCompilationIdentifier(method);
            StructuredGraph graph = new StructuredGraph.Builder(options, debug, allowAssumptions).method(method).speculationLog(speculationLog)
                            .profileProvider(profileProvider).compilationId(compilationId).build();
            assert graph != null;
            PhaseSuite<HighTierContext> ristrettoGraphBuilderSuite = ristrettoGraphBuilderSuite(INVOCATION_ENTRY_BCI);
            HighTierContext hc = new HighTierContext(runtimeConfig.getProviders(), null, OptimisticOptimizations.ALL);
            parseFromBytecode(graph, ristrettoGraphBuilderSuite, hc);
            return graph;
        }
        return null;
    }

    public static SubstrateInstalledCodeImpl compileAndInstall(SubstrateMethod method) {
        return compileAndInstall(method, INVOCATION_ENTRY_BCI);
    }

    public static SubstrateInstalledCodeImpl compileAndInstall(SubstrateMethod method, int entryBCI) {
        if (!(method instanceof RistrettoMethod)) {
            throw GraalError.shouldNotReachHere("Invalid substrate method " + method);
        }
        if (RistrettoOptions.JITTraceCompilation.getValue()) {
            Log.log().string("[Ristretto Compiler] Starting compilation of ").string(method.format("%H.%n(%p)")).newline();
        }
        RistrettoMethod rMethod = (RistrettoMethod) method;
        RuntimeConfiguration runtimeConfiguration = RuntimeCompilationSupport.getRuntimeConfig();
        DebugContext debug = new DebugContext.Builder(RuntimeOptionValues.singleton().get(), new GraalDebugHandlersFactory(runtimeConfiguration.getProviders().getSnippetReflection())).build();
        return compileAndInstallIfSpeculationsStillValid(rMethod, runtimeConfiguration, debug, entryBCI);
    }

    /**
     * Compiles {@code rMethod} and installs the resulting code only if every speculation consumed by
     * that compilation is still valid around installation. Deoptimization can publish a failed
     * speculation after graph construction has already used the corresponding assumption. The
     * pre-install check avoids installing code with already-failed speculations, and the post-install
     * check invalidates code if a matching failure appeared during the install VM operation but before
     * the caller publishes the code through {@link RistrettoMethod#onCompilationSuccess}.
     */
    private static SubstrateInstalledCodeImpl compileAndInstallIfSpeculationsStillValid(RistrettoMethod rMethod, RuntimeConfiguration runtimeConfiguration, DebugContext debug, int entryBCI) {
        RistrettoSpeculationLog methodSpeculationLog = rMethod.getSubstrateSpeculationLog();
        CompilationSpeculationLog compilationSpeculationLog = methodSpeculationLog.createCompilationLog();
        CompilationResult compilationResult = doCompile(debug, runtimeConfiguration, RuntimeCompilationSupport.getLIRSuites(), rMethod, entryBCI, compilationSpeculationLog);
        EconomicMap<Integer, Infopoint> relativeIpToInfopoint = collectInfopointsForDeopt(rMethod, compilationResult);
        RistrettoInstalledCode installedCode = new RistrettoInstalledCode(rMethod, relativeIpToInfopoint, methodSpeculationLog);
        if (compilationSpeculationLog.hasFailedSpeculation()) {
            return discardCompiledCode(rMethod, "because a speculation failed before installation");
        }
        RuntimeCodeInstaller.install(rMethod, compilationResult, installedCode);
        /*
         * Installing code can cross a VM operation boundary, so a deoptimization may publish a failed
         * speculation after the pre-install check but before this request publishes the code.
         */
        if (compilationSpeculationLog.hasFailedSpeculation()) {
            installedCode.invalidate();
            return discardCompiledCode(rMethod, "because a speculation failed during installation");
        }
        if (RistrettoOptions.JITTraceCompilation.getValue()) {
            Log.log().string("[Ristretto Compiler] Finished compilation, code for ").string(rMethod.format("%H.%n(%p)")).string(": ").signed(compilationResult.getTargetCodeSize()).string(" bytes")
                            .newline();
        }
        return installedCode;
    }

    private static SubstrateInstalledCodeImpl discardCompiledCode(RistrettoMethod rMethod, String reason) {
        if (RistrettoOptions.JITTraceCompilation.getValue()) {
            Log.log().string("[Ristretto Compiler] Discarding compiled code for ").string(rMethod.format("%H.%n(%p)")).string(" ").string(reason).newline();
        }
        return null;
    }

    /**
     * Post-processes infopoints and records the mapping needed to decode deoptimization entries.
     *
     * Deoptimization to baseline-compiled AOT code uses a mapping that is generated at build-time.
     * This mapping associates each potential deoptimization entry point (i.e., BCI in the original
     * method) with an encoded BCI in the corresponding deoptimization target. At runtime, SVM
     * decodes the current code position to this pair and then reconstructs the frame chain.
     *
     * Ristretto mirrors this behavior by creating the same mapping from runtime compilation
     * infopoints.
     */
    private static EconomicMap<Integer, Infopoint> collectInfopointsForDeopt(RistrettoMethod method, CompilationResult compilationResult) {
        EconomicMap<Integer, Infopoint> relativeIpToInfopoint = EconomicMap.create();
        if (RistrettoOptions.JITTraceCompilation.getValue()) {
            Log.log().string("Recording infopoints for method ").string(method.format("%H.%n(%p)")).newline();
        }
        for (Infopoint infopoint : compilationResult.getInfopoints()) {
            if (infopoint.debugInfo == null) {
                continue;
            }
            int entryOffset = CodeInfoEncoder.getEntryOffset(infopoint);
            if (entryOffset < 0) {
                continue;
            }
            GraalError.guarantee(!relativeIpToInfopoint.containsKey(entryOffset), "Must not contain infopoint at entry offset %s", entryOffset);
            relativeIpToInfopoint.put(entryOffset, infopoint);
            if (RistrettoOptions.JITTraceCompilation.getValue()) {
                Log.log().string("Recording infopoint ").string(infopoint.toString()).string(" at entryOffset=").signed(entryOffset).string(" debugInfo=").string(infopoint.debugInfo.toString())
                                .newline();
            }
        }
        return relativeIpToInfopoint;
    }

    public static DebugContext.Description getDescription(SubstrateMethod method) {
        final String id = "RistrettoJIT:" + method.format("%H.%n(%p)");
        DebugContext.Description desc = new DebugContext.Description(method, id);
        return desc;
    }

    public static CompilationResult doCompile(DebugContext initialDebug, RuntimeConfiguration runtimeConfig, LIRSuites lirSuites, SubstrateMethod method) {
        return doCompile(initialDebug, runtimeConfig, lirSuites, method, INVOCATION_ENTRY_BCI);
    }

    public static CompilationResult doCompile(DebugContext initialDebug, RuntimeConfiguration runtimeConfig, LIRSuites lirSuites, SubstrateMethod method, int entryBCI) {
        GraalError.guarantee(method instanceof RistrettoMethod, "Ristretto runtime compilation requires a Ristretto method: %s", method);
        RistrettoMethod ristrettoMethod = (RistrettoMethod) method;
        return doCompile(initialDebug, runtimeConfig, lirSuites, method, entryBCI, ristrettoMethod.getSubstrateSpeculationLog());
    }

    private static CompilationResult doCompile(DebugContext initialDebug, RuntimeConfiguration runtimeConfig, LIRSuites lirSuites, SubstrateMethod method, int entryBCI,
                    SpeculationLog speculationLog) {
        GraalError.guarantee(method instanceof RistrettoMethod, "Ristretto runtime compilation requires a Ristretto method: %s", method);
        RistrettoMethod ristrettoMethod = (RistrettoMethod) method;
        SubstrateGraalUtils.updateGraalArchitectureWithHostCPUFeatures(runtimeConfig.lookupBackend(method));

        String methodString = method.format("%H.%n(%p)");
        SubstrateCompilationIdentifier compilationId = new SubstrateCompilationIdentifier(method);

        return new CompilationWrapper<CompilationResult>(RuntimeCompilationSupport.get().getDebugOutputDirectory(), SubstrateGraalUtils.COMPILATION_PROBLEMS_PER_ACTION) {
            @SuppressWarnings({"unchecked", "unused"})
            <E extends Throwable> RuntimeException silenceThrowable(Class<E> type, Throwable ex) throws E {
                throw (E) ex;
            }

            @Override
            protected CompilationResult handleException(Throwable t) {
                throw silenceThrowable(RuntimeException.class, t);
            }

            @Override
            protected void parseRetryOptions(String[] options, EconomicMap<OptionKey<?>, Object> values) {
                // Use name=value boolean format for compatibility with Graal options
                CommonOptionParser.BooleanOptionFormat booleanFormat = CommonOptionParser.BooleanOptionFormat.NAME_VALUE;
                for (String option : options) {
                    RuntimeOptionParserPolicy.parseOptionAtRuntime(option, "", booleanFormat, values, false);
                }
            }

            @Override
            protected CompilationResult performCompilation(DebugContext d) {
                try (DebugContext debug = new DebugContext.Builder(RuntimeOptionValues.singleton().get(), new GraalDebugHandlersFactory(runtimeConfig.getProviders().getSnippetReflection()))
                                .description(getDescription(method))
                                .build()) {
                    try (CompilationWatchDog _ = CompilationWatchDog.watch(compilationId, debug.getOptions(), false, SubstrateGraalUtils.COMPILATION_WATCH_DOG_EVENT_HANDLER, null)) {
                        StructuredGraph graph;
                        Suites suites;
                        PhaseSuite<HighTierContext> graphBuilderSuite;

                        final OptionValues options = debug.getOptions();
                        // final int entryBCI = 0;
                        /*
                         * SubstrateSpeculationLog collects under synchronization while
                         * deoptimization appends failures through an atomic list, so this is safe for
                         * concurrent deoptimization and compilation.
                         */
                        speculationLog.collectFailedSpeculations();
                        final ProfileProvider profileProvider = new StableProfileProvider();
                        final StructuredGraph.AllowAssumptions allowAssumptions = StructuredGraph.AllowAssumptions.NO;
                        graph = new StructuredGraph.Builder(options, debug, allowAssumptions).method(method).speculationLog(speculationLog)
                                        .profileProvider(profileProvider).compilationId(compilationId).entryBCI(entryBCI).build();
                        if (!RistrettoOptions.useDeoptimization()) {
                            graph.getGraphState().configureExplicitExceptionsNoDeopt();
                        }
                        assert graph != null;
                        PhaseSuite<HighTierContext> ristrettoGraphBuilderSuite = ristrettoGraphBuilderSuite(entryBCI);
                        suites = preparePrivateSuitesForRistretto(RuntimeCompilationSupport.getMatchingSuitesForGraph(graph), options);
                        if (TestingBackdoor.shouldRememberGraph()) {
                            TestingBackdoor.installLastGraphThieves(suites, graph);
                        }
                        graphBuilderSuite = ristrettoGraphBuilderSuite;
                        graph.getDebug().dump(DebugContext.VERY_DETAILED_LEVEL, graph, "After parsing ");
                        OptimisticOptimizations optimisticOpts = getOptimisticOptimizations(ristrettoMethod, graph.getProfileProvider(), debug.getOptions());
                        if (!RistrettoOptions.useDeoptimization()) {
                            optimisticOpts = OptimisticOptimizations.NONE;
                        }
                        final Backend backend = runtimeConfig.lookupBackend(method);
                        SubstrateCompilationResult result = new SubstrateCompilationResult(graph.compilationId(), method.format("%H.%n(%p)"));
                        result.setEntryBCI(entryBCI);
                        Providers providers = backend.getProviders();

                        // use our ristretto meta access
                        providers = providers.copyWith(new RistrettoMetaAccess(providers.getMetaAccess()));

                        // and the ristretto constant reflection
                        providers = providers.copyWith(new RistrettoConstantReflectionProvider((SubstrateMetaAccess) providers.getMetaAccess(), providers.getSnippetReflection()));

                        SubstrateReplacements substrateReplacements = (SubstrateReplacements) providers.getReplacements();

                        providers = providers.copyWith(new RistrettoReplacements(substrateReplacements));
                        providers = copyWithRistrettoStampProvider(providers);

                        substrateReplacements.setProviders(providers);

                        GraalCompiler.compile(new GraalCompiler.Request<>(graph,
                                        method,
                                        providers,
                                        backend,
                                        graphBuilderSuite,
                                        optimisticOpts,
                                        null,
                                        suites,
                                        lirSuites,
                                        result,
                                        CompilationResultBuilderFactory.Default,
                                        false));
                        return result;
                    }
                }
            }

            @Override
            public String toString() {
                return methodString;
            }

            @SuppressWarnings("hiding")
            @Override
            protected DebugContext createRetryDebugContext(DebugContext initialDebug, OptionValues options, PrintStream logStream) {
                return RuntimeCompilationSupport.get().openDebugContext(options, compilationId, method, logStream);
            }

            /**
             * Integrates Ristretto runtime compilation with {@link CompilationWrapper}'s
             * {@code CompilationFailureAction=ExitVM} handling.
             *
             * The wrapper calls this hook after it has diagnosed a fatal compilation failure but
             * before {@link CompilationWrapper#run(DebugContext)} has finished closing its debug
             * scopes and retry output streams. Calling {@link System#exit(int)} directly here would
             * stop the current thread immediately and can prevent that wrapper cleanup from
             * completing. Instead, start a non-daemon helper thread that requests process
             * termination while the compiling thread returns to the wrapper and lets it finish
             * unwinding normally.
             *
             * Returning {@code true} means the exit request was successfully handed to the helper
             * thread. Returning {@code false} lets the wrapper fall back to its normal exception
             * path if the helper cannot be started.
             */
            @Override
            protected boolean requestExitVMOnCompilationFailure() {
                CountDownLatch exitStarted = new CountDownLatch(1);
                AtomicBoolean exitRequested = new AtomicBoolean();
                Thread exitThread = new Thread(() -> {
                    exitRequested.set(true);
                    exitStarted.countDown();
                    System.exit(-1);
                }, "RistrettoExitVMOnCompilationFailure");
                exitThread.setDaemon(false);
                try {
                    exitThread.start();
                } catch (Throwable t) {
                    return false;
                }
                try {
                    exitStarted.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return exitRequested.get();
            }

            @Override
            protected void exitHostVM(int status) {
                System.exit(status);
            }
        }.run(initialDebug);
    }

    private static boolean assertVerifyRistrettoJVMCI(Suites suites) {
        insertVerifierBeforeLowering(suites.getHighTier(), HighTierLoweringPhase.class, true);
        suites.getHighTier().appendPhase(new RistrettoGraphJVMCITypeVerifier(true));
        insertVerifierBeforeLowering(suites.getMidTier(), MidTierLoweringPhase.class, false);
        suites.getMidTier().appendPhase(new RistrettoGraphJVMCITypeVerifier(true));
        insertVerifierBeforeLowering(suites.getLowTier(), LowTierLoweringPhase.class, false);
        suites.getLowTier().appendPhase(new RistrettoGraphJVMCITypeVerifier(true));
        return true;
    }

    private static Providers copyWithRistrettoStampProvider(Providers providers) {
        return new Providers(providers.getMetaAccess(), providers.getCodeCache(), providers.getConstantReflection(), providers.getConstantFieldProvider(), providers.getForeignCalls(),
                        providers.getLowerer(), providers.getReplacements(), new RistrettoStampProvider(providers.getStampProvider()), providers.getPlatformConfigurationProvider(),
                        providers.getMetaAccessExtensionProvider(), providers.getSnippetReflection(), providers.getWordTypes(), providers.getLoopsDataProvider());
    }

    private static <C extends CoreProviders> void insertVerifierBeforeLowering(PhaseSuite<C> suite, Class<? extends LoweringPhase> loweringPhase, boolean verifyStamps) {
        insertVerifierBeforePhase(suite, loweringPhase, verifyStamps);
    }

    private static <C extends CoreProviders> void insertVerifierBeforePhase(PhaseSuite<C> suite, Class<?> phaseClass, boolean verifyStamps) {
        List<? extends BasePhase<? super C>> phases = suite.getPhases();
        for (int i = 0; i < phases.size(); i++) {
            BasePhase<? super C> phase = phases.get(i);
            if (phaseClass.isInstance(phase)) {
                suite.insertAtIndex(i, new RistrettoGraphJVMCITypeVerifier(verifyStamps));
                return;
            }
        }
    }

    private static Suites preparePrivateSuitesForRistretto(Suites suites, OptionValues options) {
        /*
         * RuntimeCompilationSupport hands out shared suite instances. Every Ristretto-specific
         * adapter, graph thief, and verifier phase must be installed only on this private copy so
         * repeated Ristretto compilations cannot accumulate phases globally.
         */
        Suites effectiveSuites = suites.copy();
        if (ImageSingletons.contains(RistrettoSuitesAdapter.class)) {
            ImageSingletons.lookup(RistrettoSuitesAdapter.class).adaptSuites(effectiveSuites, options);
        }
        if (!RistrettoOptions.useDeoptimization()) {
            effectiveSuites.getLowTier().appendPhase(new RistrettoNoDeoptPhase());
        }
        assert assertVerifyRistrettoJVMCI(effectiveSuites);
        return effectiveSuites;
    }

    private static OptimisticOptimizations getOptimisticOptimizations(RistrettoMethod method, ProfileProvider profileProvider, OptionValues options) {
        ProfileProvider effectiveProfileProvider = profileProvider != null ? profileProvider : new StableProfileProvider();
        return new OptimisticOptimizations(effectiveProfileProvider.getProfilingInfo(null, method, true, false), options);
    }

    private static PhaseSuite<HighTierContext> ristrettoGraphBuilderSuite(int entryBCI) {
        PhaseSuite<HighTierContext> suite = new PhaseSuite<>();
        suite.appendPhase(createRistrettoGraphBuilder(createRistrettoGraphBuilderConfiguration()));
        if (entryBCI != INVOCATION_ENTRY_BCI) {
            suite.appendPhase(new RistrettoOnStackReplacementPhase());
        }
        return suite;
    }

    private static GraphBuilderPhase createRistrettoGraphBuilder(GraphBuilderConfiguration gpc) {
        return new RistrettoGraphBuilderPhase(gpc);
    }

    public static GraphBuilderConfiguration createRistrettoGraphBuilderConfiguration() {
        // init fresh graph builder plugins
        GraphBuilderConfiguration.Plugins runtimeParseGraphBuilderPlugins = new GraphBuilderConfiguration.Plugins(new InvocationPlugins());
        SnippetReflectionProvider srp = RuntimeCompilationSupport.getRuntimeConfig().getProviders().getSnippetReflection();
        /*
         * Ristretto does not provide method-handle-specific deoptimization entries, so folding
         * linkTo* calls must preserve enough state for normal deoptimization replay.
         */
        runtimeParseGraphBuilderPlugins.appendNodePlugin(new MethodHandleWithExceptionPlugin(new RistrettoMethodHandleAccessProvider(srp), true));
        RistrettoGraphBuilderPlugins.setRuntimeGraphBuilderPlugins(runtimeParseGraphBuilderPlugins);
        StandardGraphBuilderPlugins.registerInvocationPlugins(srp, runtimeParseGraphBuilderPlugins.getInvocationPlugins(), true, true, false, false);
        if (ImageSingletons.contains(TargetGraphBuilderPlugins.class)) {
            ImageSingletons.lookup(TargetGraphBuilderPlugins.class).registerPlugins(runtimeParseGraphBuilderPlugins, RuntimeOptionValues.singleton().get());
        }
        if (ImageSingletons.contains(RistrettoSuitesAdapter.class)) {
            ImageSingletons.lookup(RistrettoSuitesAdapter.class).adaptGraphBuilderPlugins(runtimeParseGraphBuilderPlugins.getInvocationPlugins());
        }
        runtimeParseGraphBuilderPlugins.getInvocationPlugins().closeRegistration();

        GraphBuilderConfiguration gpc = GraphBuilderConfiguration.getDefault(runtimeParseGraphBuilderPlugins);
        if (!RistrettoOptions.useDeoptimization()) {
            gpc = gpc.withBytecodeExceptionMode(GraphBuilderConfiguration.BytecodeExceptionMode.CheckAll);
        }
        return gpc;
    }

    private static void parseFromBytecode(StructuredGraph graph, PhaseSuite<HighTierContext> graphBuilderSuite, HighTierContext context) {
        graphBuilderSuite.apply(graph, context);
        assert graph.getNodeCount() > 1 : "Must have nodes after parsing";
    }

    /**
     * DEBUG ONLY facility to log the virtual method table (vtable) of an interpreter type. This
     * method is highly unsafe, does not check for concurrent updates to any data structures inside
     * {@code iType} and it is also not {@link Uninterruptible}.
     * <p>
     * Use with extreme caution and only from places where it is guaranteed that no concurrent
     * updates to the data inside {@code iType} happens.
     */
    public static void logVTable(InterpreterResolvedJavaType iType) {
        if (!(iType instanceof InterpreterResolvedObjectType t)) {
            return;
        }
        Log.log().string("Dumping vtable ").string(t.toClassName()).newline();
        InterpreterResolvedJavaMethod[] table = t.getVtable();
        if (table == null) {
            Log.log().string("No vtable found").newline();
            return;
        }
        for (int i = 0; i < table.length; i++) {
            InterpreterResolvedJavaMethod method = table[i];
            CFunctionPointer jitEntryPoint = Word.nullPointer();
            RistrettoMethod rMethod = (RistrettoMethod) method.getRistrettoMethod();
            if (rMethod == null) {
                continue;
            }
            InstalledCode ic = rMethod.installedCode;
            if (ic != null && ic.isValid()) {
                /*
                 * A JIT compiled version is available, execute this one instead. This could be more
                 * optimized, see GR-71160.
                 */
                jitEntryPoint = Word.pointer(ic.getEntryPoint());
            }
            Log.log().string("\tslot=").signed(method.getVTableIndex()).string(" -> ").string(method.getDeclaringClass().toClassName()).string("::").string(method.getName()).string(", AOT addr=")
                            .hex(method.getNativeEntryPoint()).string(", JIT addr=").hex(jitEntryPoint).newline();
        }
    }

    public static final class TestingBackdoor {
        private TestingBackdoor() {
            // this type should never be allocated
        }

        /**
         * Export access to the last compiled graph of a ristretto compile to be able to white box
         * test various properties.
         */
        public static final ThreadLocal<StructuredGraph> LastCompiledGraph = new ThreadLocal<>();
        public static final ThreadLocal<StructuredGraph> LastCompiledGraphBeforeHighTier = new ThreadLocal<>();
        public static final ThreadLocal<StructuredGraph> LastCompiledGraphAfterHighTierLowering = new ThreadLocal<>();
        public static final ThreadLocal<StructuredGraph> LastCompiledGraphAfterMidTierLowering = new ThreadLocal<>();
        public static final ThreadLocal<StructuredGraph> LastCompiledGraphAfterLowTierLowering = new ThreadLocal<>();

        public static boolean shouldRememberGraph() {
            String prop = System.getProperty("com.oracle.svm.interpreter.ristretto.RistrettoUtils.PreserveLastCompiledGraph", "false");
            return Boolean.parseBoolean(prop);
        }

        static void installLastGraphThieves(Suites suites, StructuredGraph rootGraph) {
            TestingBackdoor.LastCompiledGraph.set(rootGraph);
            suites.getHighTier().prependPhase(new Phase() {
                @Override
                public Optional<NotApplicable> notApplicableTo(GraphState graphState) {
                    return ALWAYS_APPLICABLE;
                }

                @Override
                protected void run(StructuredGraph graph) {
                    TestingBackdoor.LastCompiledGraphBeforeHighTier.set((StructuredGraph) graph.copy(graph.getDebug()));
                }
            });
            suites.getHighTier().insertAfterPhase(HighTierLoweringPhase.class, new Phase() {
                @Override
                public Optional<NotApplicable> notApplicableTo(GraphState graphState) {
                    return ALWAYS_APPLICABLE;
                }

                @Override
                protected void run(StructuredGraph graph) {
                    TestingBackdoor.LastCompiledGraphAfterHighTierLowering.set((StructuredGraph) graph.copy(graph.getDebug()));
                }
            });
            suites.getMidTier().insertAfterPhase(MidTierLoweringPhase.class, new Phase() {
                @Override
                public Optional<NotApplicable> notApplicableTo(GraphState graphState) {
                    return ALWAYS_APPLICABLE;
                }

                @Override
                protected void run(StructuredGraph graph) {
                    TestingBackdoor.LastCompiledGraphAfterMidTierLowering.set((StructuredGraph) graph.copy(graph.getDebug()));
                }
            });
            suites.getLowTier().insertAfterPhase(LowTierLoweringPhase.class, new Phase() {
                @Override
                public Optional<NotApplicable> notApplicableTo(GraphState graphState) {
                    return ALWAYS_APPLICABLE;
                }

                @Override
                protected void run(StructuredGraph graph) {
                    TestingBackdoor.LastCompiledGraphAfterLowTierLowering.set((StructuredGraph) graph.copy(graph.getDebug()));
                }
            });
        }
    }

    public static RistrettoField[] toRFields(ResolvedJavaField[] iFields) {
        ArrayList<RistrettoField> rFields = new ArrayList<>();
        for (int i = 0; i < iFields.length; i++) {
            RistrettoField rField = RistrettoField.getOrCreate((InterpreterResolvedJavaField) iFields[i]);
            if (rField.getOffset() < 0) {
                /*
                 * TODO GR-73029: Hosted fields that are not needed at runtime might still have
                 * interpreter fields associated with them.
                 */
                continue;
            }
            rFields.add(rField);
        }
        /*
         * TODO GR-73047: Sort the fields in all interpreter types already by offset, then no resort
         * is ever necessary.
         */
        rFields.sort((x, y) -> Integer.compare(x.getOffset(), y.getOffset()));
        return rFields.toArray(new RistrettoField[0]);
    }

    public static RistrettoMethod toRMethodOrNull(SubstrateMethod substrateMethod) {
        InterpreterResolvedJavaType iType = (InterpreterResolvedJavaType) substrateMethod.getDeclaringClass().getHub().getInterpreterType();
        for (var iMeth : iType.getDeclaredMethods()) {
            if (iMeth.getName().equals(substrateMethod.getName()) && iMeth.getSignature().toMethodDescriptor().equals(substrateMethod.getSignature().toMethodDescriptor())) {
                RistrettoMethod rMethod = RistrettoMethod.getOrCreate(iMeth);
                rMethod.setOriginalRuntimeMethod(substrateMethod);
                return rMethod;
            }
        }
        return null;
    }

    public static RistrettoField toRFieldOrNull(SubstrateField substrateField) {
        InterpreterResolvedJavaType iType = (InterpreterResolvedJavaType) substrateField.getDeclaringClass().getHub().getInterpreterType();
        if (substrateField.isStatic()) {
            for (var iField : iType.getStaticFields()) {
                if (iField.getName().equals(substrateField.getName())) {
                    return RistrettoField.getOrCreate((InterpreterResolvedJavaField) iField, substrateField);
                }
            }
        } else {
            for (var iField : iType.getInstanceFields(true)) {
                if (iField.getName().equals(substrateField.getName())) {
                    return RistrettoField.getOrCreate((InterpreterResolvedJavaField) iField, substrateField);
                }
            }
        }
        return null;
    }

    public static RistrettoType toRType(SubstrateType substrateType) {
        return RistrettoType.getOrCreate((InterpreterResolvedJavaType) substrateType.getHub().getInterpreterType());
    }
}
