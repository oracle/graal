/*
 * Copyright (c) 2023, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.foreign;

import static java.lang.invoke.MethodHandles.exactInvoker;
import static java.lang.invoke.MethodHandles.insertArguments;

import java.io.FileDescriptor;
import java.lang.constant.DirectMethodHandleDesc;
import java.lang.constant.DirectMethodHandleDesc.Kind;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemoryLayout;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

import com.oracle.svm.core.thread.JavaThreads;
import org.graalvm.collections.EconomicSet;
import org.graalvm.nativeimage.AnnotationAccess;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.function.CFunctionPointer;
import org.graalvm.nativeimage.c.type.CIntPointer;
import org.graalvm.nativeimage.hosted.RuntimeClassInitialization;
import org.graalvm.nativeimage.hosted.RuntimeReflection;
import org.graalvm.nativeimage.impl.ConfigurationCondition;
import org.graalvm.nativeimage.impl.RuntimeForeignAccessSupport;
import org.graalvm.nativeimage.impl.RuntimeReflectionSupport;
import org.graalvm.word.Pointer;
import org.graalvm.word.UnsignedWord;

import com.oracle.graal.pointsto.meta.AnalysisMetaAccess;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisUniverse;
import com.oracle.svm.common.meta.MultiMethod;
import com.oracle.svm.configure.ConfigurationParser;
import com.oracle.svm.core.ForeignSupport;
import com.oracle.svm.core.JavaMemoryUtil;
import com.oracle.svm.core.OS;
import com.oracle.svm.core.ParsingReason;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.UnmanagedMemoryUtil;
import com.oracle.svm.core.code.FactoryMethodHolder;
import com.oracle.svm.core.code.FactoryThrowMethodHolder;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.foreign.AbiUtils;
import com.oracle.svm.core.foreign.ForeignFunctionsRuntime;
import com.oracle.svm.core.foreign.JavaEntryPointInfo;
import com.oracle.svm.core.foreign.NativeEntryPointInfo;
import com.oracle.svm.core.foreign.RuntimeSystemLookup;
import com.oracle.svm.core.foreign.SubstrateForeignUtil;
import com.oracle.svm.core.foreign.SubstrateMappedMemoryUtils;
import com.oracle.svm.core.foreign.Target_java_nio_MappedMemoryUtils;
import com.oracle.svm.core.foreign.phases.SubstrateOptimizeSharedArenaAccessPhase;
import com.oracle.svm.core.graal.RuntimeCompilation;
import com.oracle.svm.core.graal.code.SubstrateBackend;
import com.oracle.svm.core.graal.code.SubstrateBackendWithAssembler;
import com.oracle.svm.core.graal.meta.SubstrateForeignCallsProvider;
import com.oracle.svm.core.jdk.VectorAPIEnabled;
import com.oracle.svm.core.meta.MethodPointer;
import com.oracle.svm.core.nodes.SubstrateMethodCallTargetNode;
import com.oracle.svm.core.util.BasedOnJDKFile;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.ConditionalConfigurationRegistry;
import com.oracle.svm.hosted.FeatureImpl;
import com.oracle.svm.hosted.FeatureImpl.BeforeAnalysisAccessImpl;
import com.oracle.svm.hosted.ImageClassLoader;
import com.oracle.svm.hosted.ProgressReporter;
import com.oracle.svm.hosted.SharedArenaSupport;
import com.oracle.svm.hosted.code.CEntryPointData;
import com.oracle.svm.hosted.config.ConfigurationParserUtils;
import com.oracle.svm.hosted.meta.HostedMethod;
import com.oracle.svm.hosted.meta.HostedType;
import com.oracle.svm.util.LogUtils;
import com.oracle.svm.util.ModuleSupport;
import com.oracle.svm.util.ReflectionUtil;

import jdk.graal.compiler.api.replacements.Fold;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.nodes.CallTargetNode.InvokeKind;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration.Plugins;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import jdk.graal.compiler.nodes.graphbuilderconf.NodePlugin;
import jdk.graal.compiler.nodes.java.MethodCallTargetNode;
import jdk.graal.compiler.phases.BasePhase;
import jdk.graal.compiler.phases.PhaseSuite;
import jdk.graal.compiler.phases.common.CanonicalizerPhase;
import jdk.graal.compiler.phases.common.IterativeConditionalEliminationPhase;
import jdk.graal.compiler.phases.tiers.MidTierContext;
import jdk.graal.compiler.phases.util.Providers;
import jdk.internal.foreign.AbstractMemorySegmentImpl;
import jdk.internal.foreign.MemorySessionImpl;
import jdk.internal.foreign.abi.AbstractLinker;
import jdk.internal.foreign.abi.LinkerOptions;
import jdk.internal.foreign.abi.SharedUtils;
import jdk.internal.misc.ScopedMemoryAccess.ScopedAccessError;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

@AutomaticallyRegisteredFeature
@Platforms(Platform.HOSTED_ONLY.class)
public class ForeignFunctionsFeature implements InternalFeature {

    private static final Map<String, String[]> REQUIRES_CONCEALED = Map.of(
                    "jdk.internal.vm.ci", new String[]{"jdk.vm.ci.code", "jdk.vm.ci.meta", "jdk.vm.ci.amd64", "jdk.vm.ci.aarch64"},
                    "java.base", new String[]{
                                    "jdk.internal.access.foreign",
                                    "jdk.internal.misc",
                                    "jdk.internal.util",
                                    "jdk.internal.foreign",
                                    "jdk.internal.foreign.abi",
                                    "jdk.internal.foreign.abi.aarch64",
                                    "jdk.internal.foreign.abi.aarch64.macos",
                                    "jdk.internal.foreign.abi.aarch64.linux",
                                    "jdk.internal.foreign.abi.x64",
                                    "jdk.internal.foreign.abi.x64.sysv",
                                    "jdk.internal.foreign.abi.x64.windows",
                                    "jdk.internal.foreign.layout"});

    /** Indicates if the registration of stubs is no longer allowed. */
    private RuntimeForeignAccessSupportImpl accessSupport;

    /** Indicates if at least one stub was registered. */
    private boolean stubsRegistered;

    private final EconomicSet<ResolvedJavaType> neverAccessesSharedArena = EconomicSet.create();

    private final EconomicSet<ResolvedJavaMethod> neverAccessesSharedArenaMethods = EconomicSet.create();

    private AbiUtils abiUtils;
    private ForeignFunctionsRuntime foreignFunctionsRuntime;

    @Fold
    public static ForeignFunctionsFeature singleton() {
        return ImageSingletons.lookup(ForeignFunctionsFeature.class);
    }

    /**
     * Descriptor that represents both, up- and downcalls.
     */
    private record SharedDesc(FunctionDescriptor fd, LinkerOptions options) {
    }

    private record DirectUpcallDesc(MethodHandle mh, DirectMethodHandleDesc mhDesc, FunctionDescriptor fd, LinkerOptions options) {

        public SharedDesc toSharedDesc() {
            return new SharedDesc(fd, options);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o || !(o instanceof DirectUpcallDesc that)) {
                return this == o;
            }
            return Objects.equals(mhDesc, that.mhDesc) && Objects.equals(fd, that.fd) && Objects.equals(options, that.options);
        }

        @Override
        public int hashCode() {
            return Objects.hash(mhDesc, fd, options);
        }
    }

    private final class RuntimeForeignAccessSupportImpl extends ConditionalConfigurationRegistry implements StronglyTypedRuntimeForeignAccessSupport {

        private final Lookup implLookup = ReflectionUtil.readStaticField(MethodHandles.Lookup.class, "IMPL_LOOKUP");

        private AnalysisMetaAccess analysisMetaAccess;

        RuntimeForeignAccessSupportImpl() {
        }

        void duringSetup(AnalysisMetaAccess metaAccess, AnalysisUniverse analysisUniverse) {
            this.analysisMetaAccess = metaAccess;
            setUniverse(analysisUniverse);
        }

        @Override
        public void registerForDowncall(ConfigurationCondition condition, FunctionDescriptor desc, Linker.Option... options) {
            abortIfSealed();
            try {
                LinkerOptions linkerOptions = LinkerOptions.forDowncall(desc, options);
                SharedDesc sharedDesc = new SharedDesc(desc, linkerOptions);
                runConditionalTask(condition, _ -> createStub(DowncallStubFactory.INSTANCE, sharedDesc));
            } catch (IllegalArgumentException e) {
                throw UserError.abort(e, "Could not register downcall");
            }
        }

        @Override
        public void registerForUpcall(ConfigurationCondition condition, FunctionDescriptor desc, Linker.Option... options) {
            abortIfSealed();
            try {
                LinkerOptions linkerOptions = LinkerOptions.forUpcall(desc, options);
                SharedDesc sharedDesc = new SharedDesc(desc, linkerOptions);
                runConditionalTask(condition, _ -> createStub(UpcallStubFactory.INSTANCE, sharedDesc));
            } catch (IllegalArgumentException e) {
                throw UserError.abort(e, "Could not register upcall");
            }
        }

        @Override
        public void registerForDirectUpcall(ConfigurationCondition condition, MethodHandle target, FunctionDescriptor desc, Linker.Option... options) {
            abortIfSealed();
            DirectMethodHandleDesc directMethodHandleDesc = target.describeConstable()
                            .filter(x -> x instanceof DirectMethodHandleDesc dmh && dmh.kind() == Kind.STATIC)
                            .map(x -> ((DirectMethodHandleDesc) x))
                            .orElseThrow(() -> new IllegalArgumentException("Target must be a direct method handle to a static method"));
            /*
             * The call 'implLookup.revealDirect' can only succeed if the method handle is
             * crackable. The call is expected to succeed because we already call
             * 'describeConstable' before and this also requires the method handle to be crackable.
             * Further, since we use Lookup.IMPL_LOOKUP, we also do not expect any access violation
             * exceptions.
             */
            Executable method = implLookup.revealDirect(Objects.requireNonNull(target)).reflectAs(Executable.class, implLookup);
            try {
                LinkerOptions linkerOptions = LinkerOptions.forUpcall(desc, options);
                DirectUpcallDesc directUpcallDesc = new DirectUpcallDesc(target, directMethodHandleDesc, desc, linkerOptions);
                runConditionalTask(condition, _ -> {
                    ImageSingletons.lookup(RuntimeReflectionSupport.class).register(ConfigurationCondition.alwaysTrue(), false, method);
                    createStub(UpcallStubFactory.INSTANCE, directUpcallDesc.toSharedDesc());
                    createStub(DirectUpcallStubFactory.INSTANCE, directUpcallDesc);
                });
            } catch (IllegalArgumentException e) {
                throw UserError.abort(e, "Could not register direct upcall");
            }
        }

        /**
         * Generic routine for creating a single stub. This method must be thread-safe because it is
         * called during analysis.
         *
         * @param <S> The descriptor type which instances uniquely identify the stubs at run time
         *            (e.g. {@link NativeEntryPointInfo}).
         * @param <T> The stub descriptor type (e.g. {@link SharedDesc}).
         * @param <U> The stub type (e.g. {@link DowncallStub}).
         */
        private <S, T, U extends ResolvedJavaMethod> void createStub(StubFactory<S, T, U> factory, T descriptor) {

            /*
             * If foreign function calls are generally not supported on this platform, we just
             * remember (for reporting) that there was an attempt to create a stub.
             */
            if (!ForeignFunctionsRuntime.areFunctionCallsSupported()) {
                stubsRegistered = true;
                return;
            }

            S key = factory.createKey(abiUtils, descriptor);

            /*
             * Early test if there is already a stub for 'key'. We do this just to save some
             * unnecessary work. However, since an equivalent stub may be created concurrently,
             * there is no guarantee that this condition holds until the end of this method
             * execution.
             */
            if (factory.stubExists(foreignFunctionsRuntime, key)) {
                return;
            }

            U stub = factory.generateStub(analysisMetaAccess.getWrapped(), universe, key);
            AnalysisMethod analysisStub = universe.lookup(stub);

            /*
             * If 'factory.registerStub' returns 'true', then the stub created in this method
             * execution was actually "consumed" and we need to register it as root method as well.
             * If the return value is 'false', the stub was not consumed since a concurrent method
             * execution created an equal stub and this execution lost the race.
             */
            if (factory.registerStub(foreignFunctionsRuntime, key, new MethodPointer(analysisStub))) {
                universe.getBigbang().addRootMethod(analysisStub, false, "Foreign stub, registered in " + ForeignFunctionsFeature.class);
                if (factory.registerAsEntryPoint()) {
                    analysisStub.registerAsNativeEntryPoint(CEntryPointData.createCustomUnpublished());
                }
            }
        }
    }

    private final class SharedArenaSupportImpl implements SharedArenaSupport {

        @Override
        public BasePhase<MidTierContext> createOptimizeSharedArenaAccessPhase(boolean hosted) {
            VMError.guarantee(SubstrateOptions.isSharedArenaSupportEnabled(), "Support for shared arenas must be enabled");
            VMError.guarantee(!VectorAPIEnabled.getValue(), "Shared arenas cannot be used together with Vector API support (GR-65162)");

            PhaseSuite<MidTierContext> sharedArenaPhases = new PhaseSuite<>();
            if (hosted) {
                sharedArenaPhases.appendPhase(new SubstrateOptimizeSharedArenaAccessPhase(CanonicalizerPhase.create(), method -> {
                    if (getNeverAccessesSharedArena().contains(((HostedType) method.getDeclaringClass()).getWrapped())) {
                        return true;
                    }
                    return getNeverAccessesSharedArenaMethods().contains(((HostedMethod) method).getWrapped());
                }));
            } else {
                sharedArenaPhases.appendPhase(new SubstrateOptimizeSharedArenaAccessPhase(CanonicalizerPhase.create(), foreignFunctionsRuntime));
            }
            /*
             * After we injected all necessary scope wide session checks we need to cleanup any new,
             * potentially repetitive, control flow logic.
             */
            sharedArenaPhases.appendPhase(new IterativeConditionalEliminationPhase(CanonicalizerPhase.create(), false));
            sharedArenaPhases.appendPhase(CanonicalizerPhase.create());
            return sharedArenaPhases;
        }

        @Override
        public void registerSafeArenaAccessorClass(AnalysisMetaAccess metaAccess, Class<?> klass) {
            assert SubstrateOptions.isSharedArenaSupportEnabled();
            ForeignFunctionsFeature.this.registerSafeArenaAccessorClass(metaAccess, klass);
        }

        @Override
        public void registerSafeArenaAccessorsForRuntimeCompilation(Function<ResolvedJavaMethod, ResolvedJavaMethod> objectReplacer, Function<ResolvedJavaType, ResolvedJavaType> createType) {
            for (var method : getNeverAccessesSharedArenaMethods()) {
                foreignFunctionsRuntime.registerSafeArenaAccessorMethod(objectReplacer.apply(method));
            }
            for (var type : getNeverAccessesSharedArena()) {
                foreignFunctionsRuntime.registerSafeArenaAccessorClass(createType.apply(type));
            }
        }
    }

    protected ForeignFunctionsFeature() {
        /*
         * We intentionally add these exports in the constructor to avoid access errors from plugins
         * when the feature is disabled in the config.
         */
        for (var modulePackages : REQUIRES_CONCEALED.entrySet()) {
            ModuleSupport.accessPackagesToClass(ModuleSupport.Access.EXPORT, ForeignFunctionsFeature.class, false, modulePackages.getKey(), modulePackages.getValue());
        }
    }

    @Override
    public boolean isInConfiguration(IsInConfigurationAccess access) {
        if (!SubstrateOptions.isForeignAPIEnabled()) {
            return false;
        }
        UserError.guarantee(!SubstrateOptions.useLLVMBackend(), "Support for the Foreign Function and Memory API is not available with the LLVM backend.");
        return true;
    }

    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        abiUtils = AbiUtils.create();
        foreignFunctionsRuntime = new ForeignFunctionsRuntime(abiUtils);
        accessSupport = new RuntimeForeignAccessSupportImpl();
        ImageSingletons.add(RuntimeForeignAccessSupport.class, accessSupport);
        ImageSingletons.add(AbiUtils.class, abiUtils);
        ImageSingletons.add(ForeignSupport.class, foreignFunctionsRuntime);
        ImageSingletons.add(ForeignFunctionsRuntime.class, foreignFunctionsRuntime);
    }

    @Override
    public void duringSetup(DuringSetupAccess a) {
        var access = (FeatureImpl.DuringSetupAccessImpl) a;
        accessSupport.duringSetup(access.getMetaAccess(), access.getUniverse());
        if (SubstrateOptions.isSharedArenaSupportEnabled()) {
            ImageSingletons.add(SharedArenaSupport.class, new SharedArenaSupportImpl());
        }

        ImageClassLoader imageClassLoader = access.getImageClassLoader();
        ConfigurationParserUtils.parseAndRegisterConfigurationsFromCombinedFile(getConfigurationParser(imageClassLoader), imageClassLoader, "panama foreign");
    }

    @Override
    public void beforeCompilation(BeforeCompilationAccess access) {
        FeatureImpl.BeforeCompilationAccessImpl a = (FeatureImpl.BeforeCompilationAccessImpl) access;
        SubstrateBackend b = a.getRuntimeConfiguration().getBackendForNormalMethod();
        if (b instanceof SubstrateBackendWithAssembler<?> bAsm) {
            foreignFunctionsRuntime.generateTrampolineTemplate(bAsm);
        } else {
            throw VMError.shouldNotReachHere("Support for the Foreign Function and Memory API needs a backend with an assembler, it is not available with backend %s", b.getClass());
        }
    }

    private ConfigurationParser getConfigurationParser(ImageClassLoader imageClassLoader) {
        /*
         * If foreign function calls are not supported on this platform, we still want to parse the
         * configuration files such that their syntax is validated. In this case,
         * 'AbiUtils.singleton()' would return the 'Unsupported' ABI and calling method
         * 'canonicalLayouts' would cause an exception. However, since the layouts won't be
         * consumed, it doesn't matter much which ones we use and so we just use the hosted ones.
         */
        Map<String, MemoryLayout> canonicalLayouts = ForeignFunctionsRuntime.areFunctionCallsSupported() ? AbiUtils.singleton().canonicalLayouts() : Linker.nativeLinker().canonicalLayouts();
        return new ForeignFunctionsConfigurationParser(imageClassLoader, accessSupport, canonicalLayouts);
    }

    private interface StubFactory<S, T, U extends ResolvedJavaMethod> {
        S createKey(AbiUtils abiUtils, T registeredDescriptor);

        U generateStub(MetaAccessProvider metaAccessProvider, AnalysisUniverse universe, S stubDescriptor);

        boolean registerStub(ForeignFunctionsRuntime runtime, S stubDescriptor, CFunctionPointer stubPointer);

        boolean stubExists(ForeignFunctionsRuntime runtime, S key);

        boolean registerAsEntryPoint();
    }

    private record DowncallStubFactory() implements StubFactory<NativeEntryPointInfo, SharedDesc, DowncallStub> {
        private static final DowncallStubFactory INSTANCE = new DowncallStubFactory();

        @Override
        public NativeEntryPointInfo createKey(AbiUtils abiUtils, SharedDesc registeredDescriptor) {
            return abiUtils.makeNativeEntrypoint(registeredDescriptor.fd, registeredDescriptor.options);
        }

        @Override
        public DowncallStub generateStub(MetaAccessProvider metaAccessProvider, AnalysisUniverse universe, NativeEntryPointInfo stubDescriptor) {
            return new DowncallStub(stubDescriptor, metaAccessProvider);
        }

        @Override
        public boolean registerStub(ForeignFunctionsRuntime runtime, NativeEntryPointInfo stubDescriptor, CFunctionPointer stubPointer) {
            return runtime.addDowncallStubPointer(stubDescriptor, stubPointer);
        }

        @Override
        public boolean stubExists(ForeignFunctionsRuntime runtime, NativeEntryPointInfo key) {
            return runtime.downcallStubExists(key);
        }

        @Override
        public boolean registerAsEntryPoint() {
            return false;
        }
    }

    private record DirectUpcall(DirectMethodHandleDesc targetDesc, MethodHandle bindings, JavaEntryPointInfo jep) {
        @Override
        public boolean equals(Object o) {
            if (this == o || !(o instanceof DirectUpcall)) {
                return this == o;
            }
            return Objects.equals(targetDesc, ((DirectUpcall) o).targetDesc);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(targetDesc);
        }
    }

    private record UpcallStubFactory() implements StubFactory<JavaEntryPointInfo, SharedDesc, UpcallStub> {
        private static final UpcallStubFactory INSTANCE = new UpcallStubFactory();

        @Override
        public JavaEntryPointInfo createKey(AbiUtils abiUtils, SharedDesc registeredDescriptor) {
            return abiUtils.makeJavaEntryPoint(registeredDescriptor.fd, registeredDescriptor.options);
        }

        @Override
        public UpcallStub generateStub(MetaAccessProvider metaAccessProvider, AnalysisUniverse universe, JavaEntryPointInfo stubDescriptor) {
            return LowLevelUpcallStub.make(stubDescriptor, universe, metaAccessProvider);
        }

        @Override
        public boolean registerStub(ForeignFunctionsRuntime runtime, JavaEntryPointInfo stubDescriptor, CFunctionPointer stubPointer) {
            return runtime.addUpcallStubPointer(stubDescriptor, stubPointer);
        }

        @Override
        public boolean stubExists(ForeignFunctionsRuntime runtime, JavaEntryPointInfo key) {
            return runtime.upcallStubExists(key);
        }

        @Override
        public boolean registerAsEntryPoint() {
            return true;
        }
    }

    /**
     * The DirectMethodHandle provided by the "user" is not directly called but will be wrapped into
     * other method handles that do appropriate argument and result value conversions.
     *
     * However, there is no clean way to get access to the MethodHandle that is actually executed by
     * the upcall stub. This class extracts the 'UpcallStubFactory' and then re-creates an equal
     * method handle. This one is then passed to the DirectUpcallStub and is there subject for
     * intrinsification.
     */
    private static final class DirectUpcallStubFactory implements StubFactory<DirectUpcall, DirectUpcallDesc, UpcallStub> {
        private static final DirectUpcallStubFactory INSTANCE = new DirectUpcallStubFactory();
        private static final String COULD_NOT_EXTRACT_METHOD_HANDLE_FOR_UPCALL = "Could not extract method handle for upcall.";

        private final Method arrangeUpcallMethod;
        private final Method adaptUpcallForIMRMethod;

        DirectUpcallStubFactory() {
            this.arrangeUpcallMethod = ReflectionUtil.lookupMethod(LINKER.getClass(), "arrangeUpcall", MethodType.class, FunctionDescriptor.class, LinkerOptions.class);
            this.adaptUpcallForIMRMethod = ReflectionUtil.lookupMethod(SharedUtils.class, "adaptUpcallForIMR", MethodHandle.class, boolean.class);
        }

        @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-24+25/src/java.base/share/classes/jdk/internal/foreign/abi/AbstractLinker.java#L117-L135")
        @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-25+13/src/java.base/share/classes/jdk/internal/foreign/abi/SharedUtils.java#L191-L210")
        @Override
        public DirectUpcall createKey(AbiUtils abiUtils, DirectUpcallDesc desc) {
            MethodHandle target = desc.mh();

            /*
             * For each unique link request, 'AbstractLinker.upcallStub' calls
             * 'AbstractLinker.arrangeUpcall' which produces an upcall stub factory. This factory is
             * a closure created in 'UpcallLinker.makeFactory' and it closes over the
             * 'doBindingsMaker' (stored in a private field). This is a unary operator that is
             * applied to the user-provided method handle. We then re-create a method handle that is
             * equal to the one created in 'UpcallLinker.makeFactory'. This MH is then invoked from
             * the specialized upcall stub.
             *
             * Additionally, if the return type requires an in-memory return (e.g. in case of a
             * struct that doesn't fit into registers) the upcall stub factory is decorated (in
             * 'SharedUtils.arrangeUpcallHelper') with another factory that preprocesses the
             * user-provided method handle.
             */
            boolean inMemoryReturn = abiUtils.isInMemoryReturn(desc.fd().returnLayout());
            if (inMemoryReturn) {
                target = ReflectionUtil.invokeMethod(adaptUpcallForIMRMethod, null, target, abiUtils.dropReturn());
            }
            AbstractLinker.UpcallStubFactory upcallStubFactory = ReflectionUtil.invokeMethod(arrangeUpcallMethod, LINKER, desc.fd().toMethodType(), desc.fd(), desc.options());
            UnaryOperator<MethodHandle> doBindingsMaker = lookupAndReadUnaryOperatorField(upcallStubFactory, inMemoryReturn);
            MethodHandle doBindings = doBindingsMaker.apply(target);
            doBindings = insertArguments(exactInvoker(doBindings.type()), 0, doBindings);

            JavaEntryPointInfo jepi = abiUtils.makeJavaEntryPoint(desc.fd(), desc.options());
            return new DirectUpcall(desc.mhDesc(), doBindings, jepi);
        }

        @Override
        public UpcallStub generateStub(MetaAccessProvider metaAccessProvider, AnalysisUniverse universe, DirectUpcall directUpcall) {
            return LowLevelUpcallStub.makeDirect(directUpcall.bindings(), directUpcall.jep(), universe, metaAccessProvider);
        }

        @Override
        public boolean registerStub(ForeignFunctionsRuntime runtime, DirectUpcall stubDescriptor, CFunctionPointer stubPointer) {
            return runtime.addDirectUpcallStubPointer(stubDescriptor.targetDesc(), stubDescriptor.jep(), stubPointer);
        }

        @Override
        public boolean stubExists(ForeignFunctionsRuntime runtime, DirectUpcall key) {
            return runtime.directUpcallStubExists(key.targetDesc(), key.jep());
        }

        @Override
        public boolean registerAsEntryPoint() {
            return true;
        }

        /**
         * Looks up a field of type {@link UnaryOperator}, reads its value and returns it. There
         * must be exactly one such field that is readable. Otherwise, an Error is thrown.
         */
        @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-25+13/src/java.base/share/classes/jdk/internal/foreign/abi/UpcallLinker.java#L62-L110")
        @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-25+13/src/java.base/share/classes/jdk/internal/foreign/abi/SharedUtils.java#L201-L207")
        private static UnaryOperator<MethodHandle> lookupAndReadUnaryOperatorField(AbstractLinker.UpcallStubFactory outerFactory, boolean inMemoryReturn) {
            AbstractLinker.UpcallStubFactory upcallStubFactory = outerFactory;

            /*
             * The upcall stub factory created in 'UpcallLinker.makeFactory' may be decorated in
             * 'SharedUtils.arrangeUpcallHelper' if an in-memory return is necessary. We need to
             * extract the original factory first.
             */
            if (inMemoryReturn) {
                Class<? extends AbstractLinker.UpcallStubFactory> outerFactoryClass = outerFactory.getClass();
                Field upcallStubFactoryField = findSingleFieldOfType(AbstractLinker.UpcallStubFactory.class, outerFactoryClass.getDeclaredFields());
                upcallStubFactory = ReflectionUtil.readField(outerFactoryClass, upcallStubFactoryField.getName(), outerFactory);
            }

            Class<? extends AbstractLinker.UpcallStubFactory> upcallStubFactoryClass = upcallStubFactory.getClass();
            Field unaryOperatorField = findSingleFieldOfType(UnaryOperator.class, upcallStubFactoryClass.getDeclaredFields());
            UnaryOperator<MethodHandle> value = ReflectionUtil.readField(upcallStubFactoryClass, unaryOperatorField.getName(), upcallStubFactory);
            if (value == null) {
                throw VMError.shouldNotReachHere(COULD_NOT_EXTRACT_METHOD_HANDLE_FOR_UPCALL);
            }

            return value;
        }

        private static Field findSingleFieldOfType(Class<?> expectedFieldType, Field[] declaredFields) {
            Field candidate = null;
            for (Field field : declaredFields) {
                if (expectedFieldType.isAssignableFrom(field.getType())) {
                    if (candidate != null) {
                        // found a second field of type 'expectedFieldType' -> fail
                        throw VMError.shouldNotReachHere(COULD_NOT_EXTRACT_METHOD_HANDLE_FOR_UPCALL);
                    }
                    candidate = field;
                }
            }
            if (candidate == null) {
                // did not find any field of type 'expectedFieldType' -> fail
                throw VMError.shouldNotReachHere(COULD_NOT_EXTRACT_METHOD_HANDLE_FOR_UPCALL);
            }
            return candidate;
        }
    }

    private static final Linker LINKER = Linker.nativeLinker();

    private static final String JLI_PACKAGE = "java.lang.invoke";

    /**
     * List of (generated) classes that provide accessor methods for memory segments. Those methods
     * are referenced with {@code java.lang.invoke.SegmentVarHandle}. Unfortunately, the classes
     * containing the methods are not subclasses of {@link java.lang.invoke.VarHandle} and so the
     * automatic registration for reflective access (see
     * {@link com.oracle.svm.hosted.methodhandles.MethodHandleFeature#beforeAnalysis}) does not
     * trigger.
     */
    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-25+13/src/java.base/share/classes/java/lang/invoke/VarHandles.java#L313-L344") //
    private static final List<String> VAR_HANDLE_SEGMENT_ACCESSORS = List.of(
                    "VarHandleSegmentAsBooleans",
                    "VarHandleSegmentAsBytes",
                    "VarHandleSegmentAsShorts",
                    "VarHandleSegmentAsChars",
                    "VarHandleSegmentAsInts",
                    "VarHandleSegmentAsLongs",
                    "VarHandleSegmentAsFloats",
                    "VarHandleSegmentAsDoubles");

    private static void registerVarHandleMethodsForReflection(@SuppressWarnings("unused") FeatureAccess access, Class<?> subtype) {
        assert JLI_PACKAGE.equals(subtype.getPackage().getName());
        RuntimeReflection.register(subtype.getDeclaredMethods());
    }

    private static String platform() {
        return (OS.getCurrent().className + "-" + SubstrateUtil.getArchitectureName()).toLowerCase(Locale.ROOT);
    }

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess a) {
        var access = (FeatureImpl.BeforeAnalysisAccessImpl) a;

        AbiUtils.singleton().checkLibrarySupport();

        accessSupport.setAnalysisAccess(a);

        for (String simpleName : VAR_HANDLE_SEGMENT_ACCESSORS) {
            Class<?> varHandleSegmentAsXClass = ReflectionUtil.lookupClass(JLI_PACKAGE + '.' + simpleName);
            access.registerSubtypeReachabilityHandler(ForeignFunctionsFeature::registerVarHandleMethodsForReflection, varHandleSegmentAsXClass);
        }

        /*
         * Specializing an adapter would define a new class at runtime, which is not allowed in
         * SubstrateVM
         */
        access.registerFieldValueTransformer(
                        ReflectionUtil.lookupField(
                                        ReflectionUtil.lookupClass(false, "jdk.internal.foreign.abi.DowncallLinker"),
                                        "USE_SPEC"),
                        (_, _) -> false);

        access.registerFieldValueTransformer(
                        ReflectionUtil.lookupField(
                                        ReflectionUtil.lookupClass(false, "jdk.internal.foreign.abi.UpcallLinker"),
                                        "USE_SPEC"),
                        (_, _) -> false);

        RuntimeClassInitialization.initializeAtRunTime(RuntimeSystemLookup.class);

        access.registerAsRoot(ReflectionUtil.lookupMethod(ForeignFunctionsRuntime.class, "captureCallState", int.class, CIntPointer.class), false,
                        "Runtime support, registered in " + ForeignFunctionsFeature.class);

        /*
         * Even if there is no instance of MemorySessionImpl, we will kill the field location of
         * 'MemorySessionImpl.state' which may trigger registration of the declaring type after the
         * analysis universe was sealed. So, we eagerly register the field as accessed.
         */
        access.registerAsRead(ReflectionUtil.lookupField(MemorySessionImpl.class, "state"), "field location is killed after safepoint");
        try {
            initSafeArenaAccessors(access);
        } catch (Throwable t) {
            throw GraalError.shouldNotReachHere(t);
        }
    }

    @Override
    public void afterAnalysis(AfterAnalysisAccess access) {
        accessSupport.sealed();
        if (!ForeignFunctionsRuntime.areFunctionCallsSupported() && stubsRegistered) {
            assert getCreatedDowncallStubsCount() == 0;
            assert getCreatedUpcallStubsCount() == 0;
            assert getCreatedDirectUpcallStubsCount() == 0;
            LogUtils.warning("Registered down- and upcall stubs will be ignored because calling foreign functions is currently not supported on platform: %s", platform());
        }
        ProgressReporter.singleton().setForeignFunctionsInfo(getCreatedDowncallStubsCount(), getCreatedUpcallStubsCount());
    }

    /**
     * Remember a set of known methods that frequently appear in scoped memory access methods as
     * callees. Not all of those callees have to be inlined because some of them are SVM specific
     * and are known to never access a (potentially already closed) memory session. Thus, such
     * callees can be excluded during verification.
     */
    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-25+14/src/java.base/share/classes/java/nio/MappedMemoryUtils.java")
    protected void initSafeArenaAccessors(BeforeAnalysisAccessImpl access) throws NoSuchMethodException {
        MetaAccessProvider metaAccess = access.getMetaAccess();

        registerSafeArenaAccessorClass(metaAccess, FactoryMethodHolder.class);
        registerSafeArenaAccessorClass(metaAccess, FactoryThrowMethodHolder.class);
        registerSafeArenaAccessorClass(metaAccess, LogUtils.class);

        /*
         * Some methods that are normally part of the exception handler code for the calls to
         * checkValidStateRaw
         */
        registerSafeArenaAccessorMethod(metaAccess, Supplier.class.getMethod("get"));
        registerSafeArenaAccessorMethod(metaAccess, ScopedAccessError.class.getMethod("newRuntimeException"));
        registerSafeArenaAccessorMethod(metaAccess, Throwable.class.getMethod("getMessage"));
        registerSafeArenaAccessorMethod(metaAccess, ReflectionUtil.lookupMethod(Throwable.class, "fillInStackTrace", int.class));
        registerSafeArenaAccessorClass(metaAccess, VMError.class);

        /*
         * Our uninterruptible implementations of Unsafe.setMemory0, Unsafe.copyMemory0, and
         * Unsafe.copySwapMemory0 are also safe to be called.
         */
        registerSafeArenaAccessorMethod(metaAccess,
                        ReflectionUtil.lookupMethod(JavaMemoryUtil.class, "copyOnHeap", Object.class, UnsignedWord.class, Object.class, UnsignedWord.class, UnsignedWord.class));
        registerSafeArenaAccessorMethod(metaAccess, ReflectionUtil.lookupMethod(JavaMemoryUtil.class, "fill", Pointer.class, UnsignedWord.class, byte.class));
        registerSafeArenaAccessorMethod(metaAccess, ReflectionUtil.lookupMethod(JavaMemoryUtil.class, "fillOnHeap", Object.class, long.class, long.class, byte.class));
        registerSafeArenaAccessorMethod(metaAccess, ReflectionUtil.lookupMethod(JavaMemoryUtil.class, "copySwapOnHeap", Object.class, long.class, Object.class, long.class, long.class, long.class));
        registerSafeArenaAccessorMethod(metaAccess, ReflectionUtil.lookupMethod(JavaMemoryUtil.class, "copySwap", Pointer.class, Pointer.class, UnsignedWord.class, UnsignedWord.class));
        registerSafeArenaAccessorMethod(metaAccess, ReflectionUtil.lookupMethod(UnmanagedMemoryUtil.class, "copy", Pointer.class, Pointer.class, UnsignedWord.class));

        /*
         * Calls to the following methods may remain in the @Scoped-annotated methods because they
         * don't actually access the native memory in a way that it could lead to a crash. They do
         * syscalls which can handle unmapped memory gracefully. However, any changes in class
         * 'MappedMemoryUtils' must be carefully considered!
         */
        Class<?> mappedMemoryUtils = Target_java_nio_MappedMemoryUtils.class;
        registerSafeArenaAccessorMethod(metaAccess, ReflectionUtil.lookupMethod(mappedMemoryUtils, "force", FileDescriptor.class, long.class, boolean.class, long.class, long.class));
        registerSafeArenaAccessorMethod(metaAccess, ReflectionUtil.lookupMethod(mappedMemoryUtils, "isLoaded", long.class, boolean.class, long.class));
        registerSafeArenaAccessorMethod(metaAccess, ReflectionUtil.lookupMethod(mappedMemoryUtils, "unload", long.class, boolean.class, long.class));
        registerSafeArenaAccessorMethod(metaAccess, ReflectionUtil.lookupMethod(SubstrateMappedMemoryUtils.class, "load", long.class, boolean.class, long.class));
        registerSafeArenaAccessorMethod(metaAccess, Thread.class.getMethod("currentThread"));
        registerSafeArenaAccessorMethod(metaAccess, JavaThreads.class.getMethod("getCurrentThreadOrNull"));

        /*
         * The actual method checking a valid session state (if not inlined) is also safe as this
         * one would yield the error.
         */
        registerSafeArenaAccessorMethod(metaAccess, ReflectionUtil.lookupMethod(MemorySessionImpl.class, "checkValidStateRaw"));

        /*
         * In case of open type world, methods 'ScopedMemoryAccess.(load|store)*MemorySegment*' do
         * virtual calls to 'AbstractMemorySegmentImpl.unsafeGet(Base|Offset)'. Those cannot be
         * inlined (since virtual) but we know that those methods do not access native memory.
         */
        registerSafeArenaAccessorMethod(metaAccess, ReflectionUtil.lookupMethod(AbstractMemorySegmentImpl.class, "unsafeGetBase"));
        registerSafeArenaAccessorMethod(metaAccess, ReflectionUtil.lookupMethod(AbstractMemorySegmentImpl.class, "unsafeGetOffset"));
    }

    protected void registerSafeArenaAccessorClass(MetaAccessProvider metaAccess, Class<?> klass) {
        neverAccessesSharedArena.add(metaAccess.lookupJavaType(klass));
    }

    protected void registerSafeArenaAccessorMethod(MetaAccessProvider metaAccess, Executable method) {
        neverAccessesSharedArenaMethods.add(metaAccess.lookupJavaMethod(method));
    }

    public EconomicSet<ResolvedJavaType> getNeverAccessesSharedArena() {
        return neverAccessesSharedArena;
    }

    public EconomicSet<ResolvedJavaMethod> getNeverAccessesSharedArenaMethods() {
        return neverAccessesSharedArenaMethods;
    }

    @Override
    public void registerForeignCalls(SubstrateForeignCallsProvider foreignCalls) {
        foreignCalls.register(ForeignFunctionsRuntime.CAPTURE_CALL_STATE);
    }

    @Override
    public void registerGraphBuilderPlugins(Providers providers, Plugins plugins, ParsingReason reason) {
        /*
         * If support for shared arenas is enabled, register a graph builder plugin that replaces
         * invocations '((MemorySessionImpl)session).checkValidStateRaw' with
         * 'SubstrateForeignUtil.checkValidStateRawInRuntimeCompiledCode(session)'
         * in @Scoped-annotated methods that are built for runtime compilation (GR-66841). We use a
         * graph builder plugin such that the invocation can already be replaced during bytecode
         * parsing where the call is still virtual (and thus, an invocation plugin won't trigger).
         */
        if (!SubstrateOptions.isSharedArenaSupportEnabled() || !RuntimeCompilation.isEnabled()) {
            return;
        }

        ResolvedJavaMethod checkValidState = providers.getMetaAccess().lookupJavaMethod(ReflectionUtil.lookupMethod(MemorySessionImpl.class, "checkValidStateRaw"));
        ResolvedJavaMethod checkValidStateRawInRuntimeCompiledCode = providers.getMetaAccess().lookupJavaMethod(
                        ReflectionUtil.lookupMethod(SubstrateForeignUtil.class, "checkValidStateRawInRuntimeCompiledCode", MemorySessionImpl.class));
        plugins.appendNodePlugin(new NodePlugin() {
            @Override
            public boolean handleInvoke(GraphBuilderContext b, ResolvedJavaMethod method, ValueNode[] args) {
                if (!checkValidState.equals(method)) {
                    return false;
                }
                if (MultiMethod.isOriginalMethod(b.getMethod())) { // not for hosted compilation
                    return false;
                }
                if (!AnnotationAccess.isAnnotationPresent(b.getMethod(), SharedArenaSupport.SCOPED_ANNOTATION)) {
                    return false;
                }
                MethodCallTargetNode mt = b.add(new SubstrateMethodCallTargetNode(InvokeKind.Static, checkValidStateRawInRuntimeCompiledCode, args, b.getInvokeReturnStamp(b.getAssumptions())));
                b.handleReplacedInvoke(mt, b.getInvokeReturnType().getJavaKind());
                return true;
            }
        });
    }

    /* Testing and reporting interface */

    public int getCreatedDowncallStubsCount() {
        assert accessSupport.isSealed();
        return foreignFunctionsRuntime.getDowncallStubsCount();
    }

    public int getCreatedUpcallStubsCount() {
        assert accessSupport.isSealed();
        return foreignFunctionsRuntime.getUpcallStubsCount();
    }

    public int getCreatedDirectUpcallStubsCount() {
        assert accessSupport.isSealed();
        return foreignFunctionsRuntime.getDirectUpcallStubsCount();
    }
}
