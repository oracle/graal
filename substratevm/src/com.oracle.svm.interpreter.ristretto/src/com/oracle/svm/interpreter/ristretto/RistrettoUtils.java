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
import java.lang.invoke.CallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MutableCallSite;
import java.lang.invoke.VolatileCallSite;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;

import org.graalvm.collections.EconomicMap;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.c.function.CFunctionPointer;
import org.graalvm.nativeimage.impl.RuntimeReflectionSupport;
import org.graalvm.word.impl.Word;

import com.oracle.graal.pointsto.BigBang;
import com.oracle.svm.core.code.CodeInfoEncoder;
import com.oracle.svm.core.deopt.RuntimeAssumptionSupport;
import com.oracle.svm.core.deopt.RuntimeAssumptionSupport.DependentAttachment;
import com.oracle.svm.core.deopt.SubstrateSpeculationLog;
import com.oracle.svm.core.graal.code.SubstrateCompilationIdentifier;
import com.oracle.svm.core.graal.code.SubstrateCompilationResult;
import com.oracle.svm.core.graal.meta.RuntimeConfiguration;
import com.oracle.svm.core.graal.meta.SubstrateReplacements;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.hub.DynamicHubUtils;
import com.oracle.svm.core.methodhandles.RuntimeCallSiteTargetSupport;
import com.oracle.svm.graal.RuntimeCompilationSupport;
import com.oracle.svm.graal.SubstrateGraalUtils;
import com.oracle.svm.graal.meta.RuntimeCodeInstaller;
import com.oracle.svm.graal.meta.SubstrateField;
import com.oracle.svm.graal.meta.SubstrateInstalledCodeImpl;
import com.oracle.svm.graal.meta.SubstrateMethod;
import com.oracle.svm.graal.meta.SubstrateType;
import com.oracle.svm.guest.staging.log.Log;
import com.oracle.svm.guest.staging.option.RuntimeOptionParser;
import com.oracle.svm.guest.staging.option.RuntimeOptionValues;
import com.oracle.svm.hosted.image.PreserveOptionsSupport;
import com.oracle.svm.interpreter.metadata.InterpreterResolvedJavaField;
import com.oracle.svm.interpreter.metadata.InterpreterResolvedJavaMethod;
import com.oracle.svm.interpreter.metadata.InterpreterResolvedJavaType;
import com.oracle.svm.interpreter.metadata.InterpreterResolvedObjectType;
import com.oracle.svm.interpreter.metadata.RuntimeLoadedClassHierarchy;
import com.oracle.svm.interpreter.ristretto.compile.RistrettoGraphBuilderPhase;
import com.oracle.svm.interpreter.ristretto.compile.RistrettoGraphBuilderPlugins;
import com.oracle.svm.interpreter.ristretto.compile.RistrettoInstalledCode;
import com.oracle.svm.interpreter.ristretto.compile.RistrettoMethodHandleWithExceptionPlugin;
import com.oracle.svm.interpreter.ristretto.compile.RistrettoNoDeoptPhase;
import com.oracle.svm.interpreter.ristretto.compile.RistrettoOnStackReplacementPhase;
import com.oracle.svm.interpreter.ristretto.compile.RistrettoSpeculationLog;
import com.oracle.svm.interpreter.ristretto.compile.RistrettoSpeculationLog.CompilationSpeculationLog;
import com.oracle.svm.interpreter.ristretto.meta.RistrettoField;
import com.oracle.svm.interpreter.ristretto.meta.RistrettoMetaAccess;
import com.oracle.svm.interpreter.ristretto.meta.RistrettoMethod;
import com.oracle.svm.interpreter.ristretto.meta.RistrettoMethodHandleAccessProvider;
import com.oracle.svm.interpreter.ristretto.meta.RistrettoReplacements;
import com.oracle.svm.interpreter.ristretto.meta.RistrettoStampProvider;
import com.oracle.svm.interpreter.ristretto.meta.RistrettoType;
import com.oracle.svm.interpreter.ristretto.profile.RistrettoCompilationManager;
import com.oracle.svm.interpreter.ristretto.verify.RistrettoGraphJVMCITypeVerifier;
import com.oracle.svm.jvmci.shared.meta.SharedType;
import com.oracle.svm.shared.Uninterruptible;
import com.oracle.svm.shared.option.CommonOptionParser;
import com.oracle.svm.shared.util.VMError;

import jdk.graal.compiler.api.replacements.SnippetReflectionProvider;
import jdk.graal.compiler.code.CompilationResult;
import jdk.graal.compiler.core.CompilationWatchDog;
import jdk.graal.compiler.core.CompilationWrapper;
import jdk.graal.compiler.core.GraalCompiler;
import jdk.graal.compiler.core.ProfileReplaySupport;
import jdk.graal.compiler.core.common.PermanentBailoutException;
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
import jdk.graal.compiler.replacements.StandardGraphBuilderPlugins;
import jdk.graal.compiler.replacements.TargetGraphBuilderPlugins;
import jdk.graal.compiler.vector.replacements.VectorIntrinsics;
import jdk.vm.ci.code.InstalledCode;
import jdk.vm.ci.code.site.Infopoint;
import jdk.vm.ci.meta.Assumptions.Assumption;
import jdk.vm.ci.meta.Assumptions.ConcreteMethod;
import jdk.vm.ci.meta.Assumptions.ConcreteSubtype;
import jdk.vm.ci.meta.Assumptions.CallSiteTargetValue;
import jdk.vm.ci.meta.Assumptions.LeafType;
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
    public static void forcePreserveType(BigBang bb, Class<?> c) {
        final RuntimeReflectionSupport reflection = ImageSingletons.lookup(RuntimeReflectionSupport.class);
        PreserveOptionsSupport.registerType(bb, reflection, c);
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
            return DynamicHub.toClass(DynamicHubUtils.getHub(sharedType));
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
        InterpreterResolvedObjectType osrSupportType = (InterpreterResolvedObjectType) DynamicHubUtils.getInterpreterType(DynamicHub.fromClass(RistrettoOSRSupport.class));
        if (osrSupportType == null) {
            throw VMError.shouldNotReachHere("Ristretto OSR support type is not preserved for runtime compilation.");
        }
        for (InterpreterResolvedJavaMethod method : osrSupportType.getAllDeclaredMethods()) {
            if (method.getName().equals(methodName) && method.getSignature().toMethodDescriptor().equals(descriptor)) {
                return RistrettoMethod.getOrCreate(method);
            }
        }
        throw VMError.shouldNotReachHere("Could not find Ristretto OSR local getter " + methodName + descriptor + ".");
    }

    private record OSRGetters(ResolvedJavaMethod intLocal, ResolvedJavaMethod floatLocal, ResolvedJavaMethod longLocal,
                    ResolvedJavaMethod doubleLocal, ResolvedJavaMethod objectLocal, ResolvedJavaMethod lockObject) {
    }

    /** Profile capture may resolve class names and requires a thread that permits class loading. */
    public static CompilationResult compile(DebugContext debug, final SubstrateMethod method) {
        return doCompile(debug, RuntimeCompilationSupport.getRuntimeConfig(), RuntimeCompilationSupport.getLIRSuites(), method);
    }

    /** Compiles with profiles prepared before entering a compiler thread that cannot load classes. */
    public static CompilationResult compile(DebugContext debug, RistrettoMethod method, RistrettoProfileReplay profiles) {
        GraalError.guarantee(profiles != null, "Prepared Ristretto profiles must not be null");
        return doCompile(debug, RuntimeCompilationSupport.getRuntimeConfig(), RuntimeCompilationSupport.getLIRSuites(), method, INVOCATION_ENTRY_BCI,
                        method.getSubstrateSpeculationLog(), StructuredGraph.AllowAssumptions.YES, profiles);
    }

    /**
     * DEBUG ONLY facility to compile the given method and install the resulting code. Normally done
     * over {@link com.oracle.svm.interpreter.ristretto.profile.RistrettoCompilationManager}. Final
     * hierarchy validation and the installed-code assignment are one transaction so test code does
     * not expose hierarchy-dependent code after the validation lock is released. Use only for
     * testing.
     */
    public static SubstrateInstalledCodeImpl compileAndInstallInCrema(RistrettoMethod method) {
        SubstrateInstalledCodeImpl code = compileAndInstall(method, INVOCATION_ENTRY_BCI, installedCode -> {
            method.installedCode = installedCode;
            return true;
        });
        if (code == null) {
            method.installedCode = null;
        }
        return code;
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
            PhaseSuite<HighTierContext> ristrettoGraphBuilderSuite = ristrettoGraphBuilderSuite(INVOCATION_ENTRY_BCI, false);
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
        return compileAndInstall((RistrettoMethod) method, entryBCI, SubstrateInstalledCodeImpl::isValid);
    }

    /**
     * Compiles and installs code, then validates and attaches its runtime assumptions atomically
     * with {@code publish}. Installation remains outside the runtime-assumption monitor; the
     * callback is the final invocation- or OSR-state publication step.
     */
    public static SubstrateInstalledCodeImpl compileAndInstall(RistrettoMethod rMethod, int entryBCI, Predicate<SubstrateInstalledCodeImpl> publish) {
        try (DebugContext debug = RuntimeCompilationSupport.get().openDebugContext(RuntimeOptionValues.singleton().get(), getDescription(rMethod),
                        DebugContext.getDefaultLogStream())) {
            return compileAndInstall(rMethod, entryBCI, debug, publish);
        }
    }

    /** Uses the caller's debug context so it remains open through publication. */
    public static SubstrateInstalledCodeImpl compileAndInstall(RistrettoMethod rMethod, int entryBCI, DebugContext debug, Predicate<SubstrateInstalledCodeImpl> publish) {
        return compileAndInstall(rMethod, entryBCI, debug, null, publish);
    }

    /** Compiles with prepared profiles and publishes under the runtime-assumption lock. */
    public static SubstrateInstalledCodeImpl compileAndInstallWithProfiles(RistrettoMethod rMethod, int entryBCI, DebugContext debug, RistrettoProfileReplay profiles,
                    Predicate<SubstrateInstalledCodeImpl> publish) {
        GraalError.guarantee(profiles != null, "Prepared Ristretto profiles must not be null");
        return compileAndInstall(rMethod, entryBCI, debug, profiles, publish);
    }

    private static SubstrateInstalledCodeImpl compileAndInstall(RistrettoMethod rMethod, int entryBCI, DebugContext debug, RistrettoProfileReplay profiles,
                    Predicate<SubstrateInstalledCodeImpl> publish) {
        Objects.requireNonNull(publish, "publish");
        if (RistrettoOptions.JITTraceCompilation.getValue()) {
            Log.log().string("[Ristretto Compiler] Starting compilation of ").string(rMethod.format("%H.%n(%p)")).newline();
        }
        return compileAndInstallIfSpeculationsStillValid(rMethod, RuntimeCompilationSupport.getRuntimeConfig(), debug, entryBCI, profiles, publish);
    }

    /**
     * Compiles {@code rMethod} and installs the resulting code only if every speculation consumed by
     * that compilation is still valid around installation. Deoptimization can publish a failed
     * speculation after graph construction has already used the corresponding assumption. The
     * pre-install check avoids installing code with already-failed speculations, and the post-install
     * check invalidates code if a matching failure appeared during the install VM operation but before
     * the caller publishes the code through {@link RistrettoMethod#onCompilationSuccess}.
     */
    private static SubstrateInstalledCodeImpl compileAndInstallIfSpeculationsStillValid(RistrettoMethod rMethod, RuntimeConfiguration runtimeConfiguration, DebugContext debug, int entryBCI,
                    RistrettoProfileReplay profiles, Predicate<SubstrateInstalledCodeImpl> publish) {
        RistrettoSpeculationLog methodSpeculationLog = rMethod.getSubstrateSpeculationLog();
        CompilationSpeculationLog compilationSpeculationLog = methodSpeculationLog.createCompilationLog();
        boolean useDeoptimization = RistrettoOptions.useDeoptimization();
        CompilationResult compilationResult = doCompile(debug, runtimeConfiguration, RuntimeCompilationSupport.getLIRSuites(), rMethod, entryBCI, compilationSpeculationLog,
                        useDeoptimization ? StructuredGraph.AllowAssumptions.YES : StructuredGraph.AllowAssumptions.NO, profiles);
        if (useDeoptimization && hasUnsupportedRuntimeAssumption(compilationResult)) {
            compilationSpeculationLog = methodSpeculationLog.createCompilationLog();
            compilationResult = doCompile(debug, runtimeConfiguration, RuntimeCompilationSupport.getLIRSuites(), rMethod, entryBCI, compilationSpeculationLog,
                            StructuredGraph.AllowAssumptions.NO, profiles);
        }
        SnippetReflectionProvider snippetReflection = runtimeConfiguration.getProviders().getSnippetReflection();
        Assumption[] runtimeAssumptions = useDeoptimization ? copyRuntimeAssumptions(compilationResult, snippetReflection) : new Assumption[0];
        EconomicMap<Integer, Infopoint> relativeIpToInfopoint = collectInfopointsForDeopt(rMethod, compilationResult);
        RistrettoInstalledCode installedCode = new RistrettoInstalledCode(rMethod, relativeIpToInfopoint, methodSpeculationLog);
        if (compilationSpeculationLog.hasFailedSpeculation()) {
            return logDiscardedCompiledCode(rMethod, "because a speculation failed before installation");
        }
        List<DependentAttachment> attachments = runtimeAssumptions.length == 0 ? List.of() : new ArrayList<>(runtimeAssumptions.length);
        boolean completed = false;
        RuntimeCodeInstaller.install(rMethod, compilationResult, installedCode);
        try {
            /*
             * Installing code can cross a VM operation boundary, so a deoptimization may publish a failed
             * speculation after the pre-install check but before this request publishes the code.
             */
            if (compilationSpeculationLog.hasFailedSpeculation()) {
                return logDiscardedCompiledCode(rMethod, "because a speculation failed during installation");
            }
            PublicationResult result = validateAttachAndPublish(runtimeAssumptions, installedCode, snippetReflection, publish, attachments);
            if (result != PublicationResult.PUBLISHED) {
                return logDiscardedCompiledCode(rMethod, result.discardReason);
            }
            if (RistrettoOptions.JITTraceCodeInstallations.getValue()) {
                long start = installedCode.getAddress();
                int size = compilationResult.getTargetCodeSize();
                Log.log().string("[Ristretto CodeMap] method=").string(rMethod.format("%H.%n(%p)"))
                                .string(" entryBCI=").signed(entryBCI)
                                .string(" start=").hex(start)
                                .string(" end=").hex(start + size)
                                .string(" entry=").hex(installedCode.getEntryPoint())
                                .string(" size=").signed(size)
                                .newline();
            }
            if (RistrettoOptions.JITTraceCompilation.getValue()) {
                Log.log().string("[Ristretto Compiler] Finished compilation, code for ").string(rMethod.format("%H.%n(%p)")).string(": ").signed(compilationResult.getTargetCodeSize()).string(" bytes")
                                .newline();
            }
            completed = true;
            return installedCode;
        } finally {
            if (!completed) {
                /* A callback may have made the code enterable before rejecting it or throwing. */
                installedCode.invalidate();
                if (!attachments.isEmpty()) {
                    RuntimeAssumptionSupport.withAssumptionLock(() -> {
                        for (int i = attachments.size() - 1; i >= 0; i--) {
                            attachments.get(i).detach();
                        }
                    });
                }
            }
        }
    }

    /**
     * Returns whether the compilation used an assumption for which Ristretto has no runtime
     * invalidation mechanism. Such a result is discarded and compiled again without assumptions.
     */
    private static boolean hasUnsupportedRuntimeAssumption(CompilationResult compilationResult) {
        Assumption[] assumptions = compilationResult.getAssumptions();
        if (assumptions == null) {
            return false;
        }
        for (Assumption assumption : assumptions) {
            if (!(assumption instanceof CallSiteTargetValue || assumption instanceof LeafType || assumption instanceof ConcreteSubtype || assumption instanceof ConcreteMethod)) {
                return true;
            }
        }
        return false;
    }

    /** Copies one compilation result into an owned array of ordinary JVMCI assumptions. */
    private static Assumption[] copyRuntimeAssumptions(CompilationResult compilationResult, SnippetReflectionProvider snippetReflection) {
        Assumption[] assumptions = compilationResult.getAssumptions();
        if (assumptions == null || assumptions.length == 0) {
            return new Assumption[0];
        }
        for (Assumption assumption : assumptions) {
            guaranteeSupportedRuntimeAssumption(assumption, snippetReflection);
        }
        return assumptions.clone();
    }

    /** Verifies the closed set of assumptions emitted by the Ristretto compiler. */
    private static void guaranteeSupportedRuntimeAssumption(Assumption assumption, SnippetReflectionProvider snippetReflection) {
        if (assumption instanceof CallSiteTargetValue callSiteTargetValue) {
            GraalError.guarantee(RuntimeCallSiteTargetSupport.isTargetAssumptionTrackingEnabled(),
                            "Ristretto emitted a CallSite target assumption while target tracking is disabled: %s", assumption);
            CallSite site = requireCallSite(callSiteTargetValue, snippetReflection);
            GraalError.guarantee(site instanceof MutableCallSite || site instanceof VolatileCallSite,
                            "Ristretto emitted an unsupported CallSite target assumption: %s", assumption);
            target(callSiteTargetValue, snippetReflection);
        } else if (assumption instanceof LeafType leafType) {
            requireInterpreterObjectType(leafType.context, assumption);
        } else if (assumption instanceof ConcreteSubtype concreteSubtype) {
            requireInterpreterObjectType(concreteSubtype.context, assumption);
            requireInterpreterObjectType(concreteSubtype.subtype, assumption);
        } else if (assumption instanceof ConcreteMethod concreteMethod) {
            requireInterpreterMethod(concreteMethod.method, assumption);
            requireInterpreterObjectType(concreteMethod.context, assumption);
            requireInterpreterMethod(concreteMethod.impl, assumption);
        } else {
            throw GraalError.shouldNotReachHere("Unsupported Ristretto assumption: " + assumption);
        }
    }

    /**
     * Performs final validation, exact attachment, and actual code publication in one transaction.
     * Compilation and installation happen before this point, so a target or hierarchy update may
     * have invalidated an assumption recorded while the graph was built. Holding the shared monitor
     * across the final recheck, dependency attachment, and {@code publish} callback prevents either
     * assumption family from changing between validation and making the code enterable. The caller
     * owns the attachment handles: after rejection or an exception, it must invalidate code outside
     * the monitor before detaching them. This keeps published code protected during invalidation,
     * including when invalidation itself fails.
     */
    private static PublicationResult validateAttachAndPublish(Assumption[] assumptions, RistrettoInstalledCode installedCode,
                    SnippetReflectionProvider snippetReflection, Predicate<SubstrateInstalledCodeImpl> publish, List<DependentAttachment> attachments) {
        if (assumptions.length == 0) {
            if (!installedCode.isValid()) {
                return PublicationResult.INVALID_INSTALLED_CODE;
            }
            return publish.test(installedCode) ? PublicationResult.PUBLISHED : PublicationResult.PUBLICATION_REJECTED;
        }
        return RuntimeAssumptionSupport.withAssumptionLock(() -> {
            if (!installedCode.isValid()) {
                return PublicationResult.INVALID_INSTALLED_CODE;
            }
            if (!allRuntimeAssumptionsValidLocked(assumptions, snippetReflection)) {
                return PublicationResult.INVALID_ASSUMPTION;
            }
            RuntimeCallSiteTargetSupport.Registration callSiteRegistration = null;
            for (Assumption assumption : assumptions) {
                if (assumption instanceof CallSiteTargetValue callSiteTargetValue) {
                    if (callSiteRegistration == null) {
                        callSiteRegistration = RuntimeCallSiteTargetSupport.createRegistration(installedCode);
                    }
                    DependentAttachment attachment = RuntimeCallSiteTargetSupport.attachDependentLocked(
                                    requireCallSite(callSiteTargetValue, snippetReflection), callSiteRegistration);
                    if (attachment != null) {
                        attachments.add(attachment);
                    }
                } else {
                    attachments.add(RuntimeLoadedClassHierarchy.attachDependentLocked(assumption, installedCode));
                }
            }
            return publish.test(installedCode) ? PublicationResult.PUBLISHED : PublicationResult.PUBLICATION_REJECTED;
        });
    }

    /** Distinguishes rejected publication from invalid code or runtime assumptions. */
    private enum PublicationResult {
        PUBLISHED(null),
        INVALID_INSTALLED_CODE("because the installed code became invalid before final publication"),
        INVALID_ASSUMPTION("because a runtime assumption failed during final publication"),
        PUBLICATION_REJECTED("because the publication callback rejected the installed code");

        private final String discardReason;

        PublicationResult(String discardReason) {
            this.discardReason = discardReason;
        }
    }

    /**
     * Rechecks every runtime assumption against the state protected by the shared assumption
     * monitor immediately before its dependencies are attached.
     */
    private static boolean allRuntimeAssumptionsValidLocked(Assumption[] assumptions, SnippetReflectionProvider snippetReflection) {
        assert RuntimeAssumptionSupport.isAssumptionLockHeldByCurrentThread();
        for (Assumption assumption : assumptions) {
            boolean valid;
            if (assumption instanceof CallSiteTargetValue callSiteTargetValue) {
                valid = RuntimeCallSiteTargetSupport.isTargetValidLocked(requireCallSite(callSiteTargetValue, snippetReflection), target(callSiteTargetValue, snippetReflection));
            } else {
                valid = RuntimeLoadedClassHierarchy.isAssumptionValidLocked(assumption);
            }
            if (!valid) {
                return false;
            }
        }
        return true;
    }

    /** Returns the non-null runtime CallSite encoded by {@code assumption}. */
    private static CallSite requireCallSite(CallSiteTargetValue assumption, SnippetReflectionProvider snippetReflection) {
        CallSite callSite = snippetReflection.asObject(CallSite.class, assumption.callSite);
        GraalError.guarantee(callSite != null, "Ristretto CallSite target assumption has a non-object CallSite constant: %s", assumption);
        return callSite;
    }

    private static MethodHandle target(CallSiteTargetValue assumption, SnippetReflectionProvider snippetReflection) {
        MethodHandle target = snippetReflection.asObject(MethodHandle.class, assumption.methodHandle);
        GraalError.guarantee(target != null, "Ristretto CallSite target assumption has a non-object target constant: %s", assumption);
        return target;
    }

    /** Returns the canonical interpreter object type stored in a hierarchy assumption. */
    private static InterpreterResolvedObjectType requireInterpreterObjectType(ResolvedJavaType type, Assumption assumption) {
        GraalError.guarantee(type instanceof InterpreterResolvedObjectType,
                        "Ristretto hierarchy assumption has non-object interpreter metadata: %s in %s", type, assumption);
        return (InterpreterResolvedObjectType) type;
    }

    /** Returns the interpreter method represented by a supported assumption payload. */
    private static InterpreterResolvedJavaMethod requireInterpreterMethod(ResolvedJavaMethod method, Assumption assumption) {
        if (method instanceof InterpreterResolvedJavaMethod iMethod) {
            return iMethod;
        }
        throw GraalError.shouldNotReachHere("Ristretto hierarchy assumption has non-interpreter method metadata: " + method + " in " + assumption);
    }

    private static SubstrateInstalledCodeImpl logDiscardedCompiledCode(RistrettoMethod rMethod, String reason) {
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
        return doCompile(initialDebug, runtimeConfig, lirSuites, method, entryBCI, speculationLog, StructuredGraph.AllowAssumptions.YES);
    }

    private static CompilationResult doCompile(DebugContext initialDebug, RuntimeConfiguration runtimeConfig, LIRSuites lirSuites, SubstrateMethod method, int entryBCI,
                    SpeculationLog speculationLog, StructuredGraph.AllowAssumptions allowAssumptions) {
        return doCompile(initialDebug, runtimeConfig, lirSuites, method, entryBCI, speculationLog, allowAssumptions, null);
    }

    private static CompilationResult doCompile(DebugContext initialDebug, RuntimeConfiguration runtimeConfig, LIRSuites lirSuites, SubstrateMethod method, int entryBCI,
                    SpeculationLog speculationLog, StructuredGraph.AllowAssumptions allowAssumptions, RistrettoProfileReplay profiles) {
        GraalError.guarantee(method instanceof RistrettoMethod, "Ristretto runtime compilation requires a Ristretto method: %s", method);
        RistrettoMethod ristrettoMethod = (RistrettoMethod) method;
        SubstrateGraalUtils.updateGraalArchitectureWithHostCPUFeatures(runtimeConfig.lookupBackend(method));

        String methodString = method.format("%H.%n(%p)");
        SubstrateCompilationIdentifier compilationId = new SubstrateCompilationIdentifier(method);

        CompilationResult completedCompilation = new CompilationWrapper<CompilationResult>(RuntimeCompilationSupport.get().getDebugOutputDirectory(),
                        SubstrateGraalUtils.COMPILATION_PROBLEMS_PER_ACTION) {
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
                    RuntimeOptionParser.singleton().parseOptionAtRuntime(option, "", booleanFormat, values, false);
                }
            }

            @Override
            protected CompilationResult performCompilation(DebugContext debug) {
                try (CompilationWatchDog _ = watchCompilation(compilationId, debug.getOptions())) {
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
                    final StableProfileProvider profileProvider = RistrettoProfileReplaySupport.createProfileProvider(options, ristrettoMethod, entryBCI, profiles);
                    ProfileReplaySupport profileReplay = RistrettoProfileReplaySupport.startCapture(debug, entryBCI, ristrettoMethod, profileProvider);
                    graph = new StructuredGraph.Builder(options, debug, allowAssumptions).method(method).speculationLog(speculationLog)
                                    .profileProvider(profileProvider).compilationId(compilationId).entryBCI(entryBCI).build();
                    if (!RistrettoOptions.useDeoptimization()) {
                        graph.getGraphState().configureExplicitExceptionsNoDeopt();
                    }
                    assert graph != null;
                    PhaseSuite<HighTierContext> ristrettoGraphBuilderSuite = ristrettoGraphBuilderSuite(entryBCI, profiles != null);
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

                    providers = RistrettoProfileReplaySupport.configureProviders(providers, profiles != null);

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
                    if (profileReplay != null && !hasUnsupportedRuntimeAssumption(result)) {
                        profileReplay.profileReplayEpilogue(debug, result, graph, profileProvider, compilationId, entryBCI, method);
                    }
                    return result;
                }
            }

            @Override
            public String toString() {
                return methodString;
            }

            @SuppressWarnings("hiding")
            @Override
            protected DebugContext createRetryDebugContext(DebugContext initialDebug, OptionValues options, PrintStream logStream) {
                return RuntimeCompilationSupport.get().openDebugContext(options, getDescription(method), logStream);
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
        GraalError.guarantee(allowAssumptions == StructuredGraph.AllowAssumptions.YES || completedCompilation.getAssumptions() == null || completedCompilation.getAssumptions().length == 0,
                        "Compilation with assumptions disabled produced assumptions: %s", method);
        return completedCompilation;
    }

    private static CompilationWatchDog watchCompilation(SubstrateCompilationIdentifier compilationId, OptionValues options) {
        int ristrettoDelaySeconds = RistrettoOptions.JITCompilationWatchdogTimeoutSeconds.getValue();
        if (ristrettoDelaySeconds == 0) {
            return CompilationWatchDog.watch(compilationId, options, false, SubstrateGraalUtils.COMPILATION_WATCH_DOG_EVENT_HANDLER, null);
        }
        OptionValues ristrettoWatchDogOptions = new OptionValues(options, CompilationWatchDog.Options.CompilationWatchDogStartDelay, ristrettoDelaySeconds,
                        CompilationWatchDog.Options.CompilationWatchDogVMExitDelay, 0);
        return CompilationWatchDog.watch(compilationId, ristrettoWatchDogOptions, false, RistrettoCompilationManager.getCompilationWatchDogEventHandler(), null);
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

    private static PhaseSuite<HighTierContext> ristrettoGraphBuilderSuite(int entryBCI, boolean replay) {
        PhaseSuite<HighTierContext> suite = new PhaseSuite<>();
        GraphBuilderConfiguration configuration = createRistrettoGraphBuilderConfiguration();
        RistrettoProfileReplaySupport.configureGraphBuilder(configuration, replay);
        suite.appendPhase(createRistrettoGraphBuilder(configuration));
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
        runtimeParseGraphBuilderPlugins.appendNodePlugin(new RistrettoMethodHandleWithExceptionPlugin(new RistrettoMethodHandleAccessProvider(srp), true));
        RistrettoGraphBuilderPlugins.setRuntimeGraphBuilderPlugins(runtimeParseGraphBuilderPlugins);
        StandardGraphBuilderPlugins.registerInvocationPlugins(srp, runtimeParseGraphBuilderPlugins.getInvocationPlugins(), true, true, false, false);
        VectorIntrinsics.registerPlugins(runtimeParseGraphBuilderPlugins.getInvocationPlugins(), RuntimeOptionValues.singleton().get(), !RistrettoOptions.useDeoptimization());
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
        /** A test-only assumption inserted into the first assumptions-enabled compilation. */
        public static final ThreadLocal<Assumption> ForcedUnsupportedAssumption = new ThreadLocal<>();
        /** Counts compilation attempts observed while graph preservation is enabled. */
        public static final ThreadLocal<Integer> CompilationAttemptCount = ThreadLocal.withInitial(() -> 0);
        /** Counts actual injections, excluding the assumptions-disabled retry. */
        public static final ThreadLocal<Integer> ForcedAssumptionInjectionCount = ThreadLocal.withInitial(() -> 0);

        public static boolean shouldRememberGraph() {
            String prop = System.getProperty("com.oracle.svm.interpreter.ristretto.RistrettoUtils.PreserveLastCompiledGraph", "false");
            return Boolean.parseBoolean(prop);
        }

        static void installLastGraphThieves(Suites suites, StructuredGraph rootGraph) {
            CompilationAttemptCount.set(CompilationAttemptCount.get() + 1);
            TestingBackdoor.LastCompiledGraph.set(rootGraph);
            suites.getHighTier().prependPhase(new Phase() {
                @Override
                public Optional<NotApplicable> notApplicableTo(GraphState graphState) {
                    return ALWAYS_APPLICABLE;
                }

                @Override
                protected void run(StructuredGraph graph) {
                    Assumption forced = ForcedUnsupportedAssumption.get();
                    if (forced != null && graph.getAssumptions() != null) {
                        graph.getAssumptions().record(forced);
                        ForcedAssumptionInjectionCount.set(ForcedAssumptionInjectionCount.get() + 1);
                    }
                }
            });
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
        return rFields.toArray(new RistrettoField[0]);
    }

    public static RistrettoMethod toRMethodOrNull(SubstrateMethod substrateMethod) {
        InterpreterResolvedObjectType iType = (InterpreterResolvedObjectType) DynamicHubUtils.getInterpreterType(substrateMethod.getDeclaringClass().getHub());
        for (var iMeth : iType.getAllDeclaredMethods()) {
            if (iMeth.getName().equals(substrateMethod.getName()) && iMeth.getSignature().toMethodDescriptor().equals(substrateMethod.getSignature().toMethodDescriptor())) {
                RistrettoMethod rMethod = RistrettoMethod.getOrCreate(iMeth);
                rMethod.setOriginalRuntimeMethod(substrateMethod);
                return rMethod;
            }
        }
        return null;
    }

    public static RistrettoField toRFieldOrNull(SubstrateField substrateField) {
        InterpreterResolvedJavaType iType = (InterpreterResolvedJavaType) DynamicHubUtils.getInterpreterType(substrateField.getDeclaringClass().getHub());
        if (substrateField.isStatic()) {
            for (var iField : iType.getStaticFields()) {
                if (iField.getName().equals(substrateField.getName())) {
                    return RistrettoField.getOrCreate((InterpreterResolvedJavaField) iField, substrateField);
                }
            }
        } else {
            for (var iField : iType.getInstanceFields(false)) {
                if (iField.getName().equals(substrateField.getName())) {
                    return RistrettoField.getOrCreate((InterpreterResolvedJavaField) iField, substrateField);
                }
            }
        }
        return null;
    }

    public static RistrettoType toRType(SubstrateType substrateType) {
        return RistrettoType.getOrCreate((InterpreterResolvedJavaType) DynamicHubUtils.getInterpreterType(substrateType.getHub()));
    }
}
