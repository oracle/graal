/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

import org.graalvm.collections.EconomicSet;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.function.CFunctionPointer;
import org.graalvm.nativeimage.c.type.CIntPointer;
import org.graalvm.nativeimage.hosted.RuntimeClassInitialization;
import org.graalvm.nativeimage.hosted.RuntimeReflection;
import org.graalvm.nativeimage.impl.ConfigurationCondition;
import org.graalvm.nativeimage.impl.RuntimeForeignAccessSupport;

import com.oracle.graal.pointsto.meta.AnalysisMetaAccess;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisUniverse;
import com.oracle.svm.configure.ConfigurationFile;
import com.oracle.svm.configure.ConfigurationParser;
import com.oracle.svm.core.ForeignSupport;
import com.oracle.svm.core.OS;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.code.FactoryMethodHolder;
import com.oracle.svm.core.configure.ConfigurationFiles;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.foreign.AbiUtils;
import com.oracle.svm.core.foreign.ForeignFunctionsRuntime;
import com.oracle.svm.core.foreign.JavaEntryPointInfo;
import com.oracle.svm.core.foreign.NativeEntryPointInfo;
import com.oracle.svm.core.foreign.RuntimeSystemLookup;
import com.oracle.svm.core.foreign.SubstrateMappedMemoryUtils;
import com.oracle.svm.core.foreign.Target_java_nio_MappedMemoryUtils;
import com.oracle.svm.core.graal.meta.SubstrateForeignCallsProvider;
import com.oracle.svm.core.meta.MethodPointer;
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
import com.oracle.svm.hosted.foreign.phases.SubstrateOptimizeSharedArenaAccessPhase;
import com.oracle.svm.util.LogUtils;
import com.oracle.svm.util.ModuleSupport;
import com.oracle.svm.util.ReflectionUtil;

import jdk.graal.compiler.api.replacements.Fold;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.phases.BasePhase;
import jdk.graal.compiler.phases.PhaseSuite;
import jdk.graal.compiler.phases.common.CanonicalizerPhase;
import jdk.graal.compiler.phases.common.IterativeConditionalEliminationPhase;
import jdk.graal.compiler.phases.tiers.MidTierContext;
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

    private boolean sealed = false;
    private final RuntimeForeignAccessSupportImpl accessSupport = new RuntimeForeignAccessSupportImpl();

    private final Set<SharedDesc> registeredDowncalls = ConcurrentHashMap.newKeySet();
    private int downcallCount = -1;

    private final Set<SharedDesc> registeredUpcalls = ConcurrentHashMap.newKeySet();
    private int upcallCount = -1;

    private final Set<DirectUpcallDesc> registeredDirectUpcalls = ConcurrentHashMap.newKeySet();
    private int directUpcallCount = -1;

    private final EconomicSet<ResolvedJavaType> neverAccessesSharedArena = EconomicSet.create();

    private final EconomicSet<ResolvedJavaMethod> neverAccessesSharedArenaMethods = EconomicSet.create();

    @Fold
    public static ForeignFunctionsFeature singleton() {
        return ImageSingletons.lookup(ForeignFunctionsFeature.class);
    }

    private void checkNotSealed() {
        UserError.guarantee(!sealed, "Registration of foreign functions was closed.");
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

        @Override
        public void registerForDowncall(ConfigurationCondition condition, FunctionDescriptor desc, Linker.Option... options) {
            checkNotSealed();
            try {
                LinkerOptions linkerOptions = LinkerOptions.forDowncall(desc, options);
                registerConditionalConfiguration(condition, (cnd) -> registeredDowncalls.add(new SharedDesc(desc, linkerOptions)));
            } catch (IllegalArgumentException e) {
                throw UserError.abort(e, "Could not register downcall");
            }
        }

        @Override
        public void registerForUpcall(ConfigurationCondition condition, FunctionDescriptor desc, Linker.Option... options) {
            checkNotSealed();
            try {
                LinkerOptions linkerOptions = LinkerOptions.forUpcall(desc, options);
                registerConditionalConfiguration(condition, (ignored) -> registeredUpcalls.add(new SharedDesc(desc, linkerOptions)));
            } catch (IllegalArgumentException e) {
                throw UserError.abort(e, "Could not register upcall");
            }
        }

        @Override
        public void registerForDirectUpcall(ConfigurationCondition condition, MethodHandle target, FunctionDescriptor desc, Linker.Option... options) {
            checkNotSealed();
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
                registerConditionalConfiguration(condition, (ignored) -> {
                    RuntimeReflection.register(method);
                    registeredDirectUpcalls.add(new DirectUpcallDesc(target, directMethodHandleDesc, desc, linkerOptions));
                });
            } catch (IllegalArgumentException e) {
                throw UserError.abort(e, "Could not register direct upcall");
            }
        }
    }

    private final class SharedArenaSupportImpl implements SharedArenaSupport {

        @Override
        public BasePhase<MidTierContext> createOptimizeSharedArenaAccessPhase() {
            PhaseSuite<MidTierContext> sharedArenaPhases = new PhaseSuite<>();
            sharedArenaPhases.appendPhase(new SubstrateOptimizeSharedArenaAccessPhase(CanonicalizerPhase.create()));
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
            ForeignFunctionsFeature.this.registerSafeArenaAccessorClass(metaAccess, klass);
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
        if (!SubstrateOptions.ForeignAPISupport.getValue()) {
            return false;
        }
        UserError.guarantee(!SubstrateOptions.useLLVMBackend(), "Support for the Foreign Function and Memory API is not available with the LLVM backend.");
        return true;
    }

    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        AbiUtils abiUtils = AbiUtils.create();
        ForeignFunctionsRuntime foreignFunctionsRuntime = new ForeignFunctionsRuntime(abiUtils);

        ImageSingletons.add(AbiUtils.class, abiUtils);
        ImageSingletons.add(ForeignSupport.class, foreignFunctionsRuntime);
        ImageSingletons.add(ForeignFunctionsRuntime.class, foreignFunctionsRuntime);
    }

    @Override
    public void duringSetup(DuringSetupAccess a) {
        var access = (FeatureImpl.DuringSetupAccessImpl) a;
        ImageSingletons.add(RuntimeForeignAccessSupport.class, accessSupport);
        ImageSingletons.add(SharedArenaSupport.class, new SharedArenaSupportImpl());

        ImageClassLoader imageClassLoader = access.getImageClassLoader();
        ConfigurationParserUtils.parseAndRegisterConfigurations(getConfigurationParser(imageClassLoader), imageClassLoader, "panama foreign",
                        ConfigurationFiles.Options.ForeignConfigurationFiles, ConfigurationFiles.Options.ForeignResources, ConfigurationFile.FOREIGN.getFileName());
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
        S createKey(T registeredDescriptor);

        U generateStub(S stubDescriptor);

        void registerStub(S stubDescriptor, CFunctionPointer stubPointer);
    }

    private record DowncallStubFactory(MetaAccessProvider metaAccessProvider) implements StubFactory<NativeEntryPointInfo, SharedDesc, DowncallStub> {

        @Override
        public NativeEntryPointInfo createKey(SharedDesc registeredDescriptor) {
            return AbiUtils.singleton().makeNativeEntrypoint(registeredDescriptor.fd, registeredDescriptor.options);
        }

        @Override
        public DowncallStub generateStub(NativeEntryPointInfo stubDescriptor) {
            return new DowncallStub(stubDescriptor, metaAccessProvider);
        }

        @Override
        public void registerStub(NativeEntryPointInfo stubDescriptor, CFunctionPointer stubPointer) {
            ForeignFunctionsRuntime.singleton().addDowncallStubPointer(stubDescriptor, stubPointer);
        }
    }

    private void createDowncallStubs(FeatureImpl.BeforeAnalysisAccessImpl access) {
        this.downcallCount = createStubs(
                        registeredDowncalls,
                        access,
                        false,
                        new DowncallStubFactory(access.getMetaAccess().getWrapped())).size();
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

    private record UpcallStubFactory(AnalysisUniverse universe, MetaAccessProvider metaAccessProvider) implements StubFactory<JavaEntryPointInfo, SharedDesc, UpcallStub> {

        @Override
        public JavaEntryPointInfo createKey(SharedDesc registeredDescriptor) {
            return AbiUtils.singleton().makeJavaEntryPoint(registeredDescriptor.fd, registeredDescriptor.options);
        }

        @Override
        public UpcallStub generateStub(JavaEntryPointInfo stubDescriptor) {
            return LowLevelUpcallStub.make(stubDescriptor, universe, metaAccessProvider);
        }

        @Override
        public void registerStub(JavaEntryPointInfo stubDescriptor, CFunctionPointer stubPointer) {
            ForeignFunctionsRuntime.singleton().addUpcallStubPointer(stubDescriptor, stubPointer);
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
        private static final String COULD_NOT_EXTRACT_METHOD_HANDLE_FOR_UPCALL = "Could not extract method handle for upcall.";

        private final AnalysisUniverse universe;
        private final MetaAccessProvider metaAccessProvider;
        private final Method arrangeUpcallMethod;
        private final Method adaptUpcallForIMRMethod;
        private final Set<SharedDesc> registeredUpcalls;

        DirectUpcallStubFactory(AnalysisUniverse universe, MetaAccessProvider metaAccessProvider, Set<SharedDesc> registeredUpcalls) {
            this.universe = universe;
            this.metaAccessProvider = metaAccessProvider;
            this.registeredUpcalls = registeredUpcalls;
            this.arrangeUpcallMethod = ReflectionUtil.lookupMethod(LINKER.getClass(), "arrangeUpcall", MethodType.class, FunctionDescriptor.class, LinkerOptions.class);
            this.adaptUpcallForIMRMethod = ReflectionUtil.lookupMethod(SharedUtils.class, "adaptUpcallForIMR", MethodHandle.class, boolean.class);
        }

        @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-24+25/src/java.base/share/classes/jdk/internal/foreign/abi/AbstractLinker.java#L117-L135")
        @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-25+13/src/java.base/share/classes/jdk/internal/foreign/abi/SharedUtils.java#L191-L210")
        @Override
        public DirectUpcall createKey(DirectUpcallDesc desc) {
            AbiUtils abiUtils = AbiUtils.singleton();
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
            registeredUpcalls.add(desc.toSharedDesc());
            return new DirectUpcall(desc.mhDesc(), doBindings, jepi);
        }

        @Override
        public UpcallStub generateStub(DirectUpcall directUpcall) {
            return LowLevelUpcallStub.makeDirect(directUpcall.bindings(), directUpcall.jep(), universe, metaAccessProvider);
        }

        @Override
        public void registerStub(DirectUpcall stubDescriptor, CFunctionPointer stubPointer) {
            ForeignFunctionsRuntime.singleton().addDirectUpcallStubPointer(stubDescriptor.targetDesc(), stubDescriptor.jep(), stubPointer);
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

    private void createUpcallStubs(FeatureImpl.BeforeAnalysisAccessImpl access) {
        Map<DirectUpcall, UpcallStub> directUpcallStubs = createStubs(registeredDirectUpcalls, access, true,
                        new DirectUpcallStubFactory(access.getUniverse(), access.getMetaAccess().getWrapped(), registeredUpcalls));
        this.directUpcallCount = directUpcallStubs.size();
        registeredDirectUpcalls.clear();

        Map<JavaEntryPointInfo, UpcallStub> upcallStubs = createStubs(registeredUpcalls, access, true,
                        new UpcallStubFactory(access.getUniverse(), access.getMetaAccess().getWrapped()));
        this.upcallCount = upcallStubs.size();
        registeredUpcalls.clear();
    }

    private static final Linker LINKER = Linker.nativeLinker();

    private static <S, T, U extends ResolvedJavaMethod> Map<S, U> createStubs(
                    Iterable<T> sources,
                    FeatureImpl.BeforeAnalysisAccessImpl access,
                    boolean registerAsEntryPoints,
                    StubFactory<S, T, U> factory) {

        Map<S, U> created = new HashMap<>();

        for (T source : sources) {
            S key = factory.createKey(source);

            if (!created.containsKey(key)) {
                U stub = factory.generateStub(key);
                AnalysisMethod analysisStub = access.getUniverse().lookup(stub);
                access.getBigBang().addRootMethod(analysisStub, false, "Foreign stub, registered in " + ForeignFunctionsFeature.class);
                if (registerAsEntryPoints) {
                    analysisStub.registerAsNativeEntryPoint(CEntryPointData.createCustomUnpublished());
                }
                created.put(key, stub);
                factory.registerStub(key, new MethodPointer(analysisStub));
            }
        }
        return created;
    }

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

    private static void registerVarHandleMethodsForReflection(FeatureAccess access, Class<?> subtype) {
        assert JLI_PACKAGE.equals(subtype.getPackage().getName());
        RuntimeReflection.register(subtype.getDeclaredMethods());
    }

    private static String platform() {
        return (OS.getCurrent().className + "-" + SubstrateUtil.getArchitectureName()).toLowerCase(Locale.ROOT);
    }

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess a) {
        var access = (FeatureImpl.BeforeAnalysisAccessImpl) a;
        sealed = true;

        AbiUtils.singleton().checkLibrarySupport();

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

        if (ForeignFunctionsRuntime.areFunctionCallsSupported()) {
            createDowncallStubs(access);
            createUpcallStubs(access);
        } else {
            if (!registeredDowncalls.isEmpty() || !registeredUpcalls.isEmpty() || !registeredDirectUpcalls.isEmpty()) {
                registeredDowncalls.clear();
                registeredUpcalls.clear();
                registeredDirectUpcalls.clear();

                LogUtils.warning("Registered down- and upcall stubs will be ignored because calling foreign functions is currently not supported on platform: %s", platform());
            }
            downcallCount = 0;
            upcallCount = 0;
            directUpcallCount = 0;
        }
        ProgressReporter.singleton().setForeignFunctionsInfo(getCreatedDowncallStubsCount(), getCreatedUpcallStubsCount());

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
        registerSafeArenaAccessorClass(metaAccess, LogUtils.class);

        /*
         * Some methods that are normally part of the exception handler code for the calls to
         * checkValidStateRaw
         */
        registerSafeArenaAccessorMethod(metaAccess, Supplier.class.getMethod("get"));
        registerSafeArenaAccessorMethod(metaAccess, VMError.class.getMethod("shouldNotReachHereSubstitution"));
        registerSafeArenaAccessorMethod(metaAccess, ScopedAccessError.class.getMethod("newRuntimeException"));
        registerSafeArenaAccessorMethod(metaAccess, Throwable.class.getMethod("getMessage"));
        registerSafeArenaAccessorMethod(metaAccess, ReflectionUtil.lookupMethod(Throwable.class, "fillInStackTrace", int.class));

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

    /* Testing interface */

    public int getCreatedDowncallStubsCount() {
        assert sealed;
        assert downcallCount >= 0;
        return downcallCount;
    }

    public int getCreatedUpcallStubsCount() {
        assert sealed;
        assert upcallCount >= 0;
        return upcallCount;
    }

    public int getCreatedDirectUpcallStubsCount() {
        assert sealed;
        assert directUpcallCount >= 0;
        return directUpcallCount;
    }
}
