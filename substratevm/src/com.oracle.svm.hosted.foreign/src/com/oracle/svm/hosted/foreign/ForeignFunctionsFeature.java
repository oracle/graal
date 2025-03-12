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

import java.lang.constant.DirectMethodHandleDesc;
import java.lang.constant.DirectMethodHandleDesc.Kind;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.UnaryOperator;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.function.CFunctionPointer;
import org.graalvm.nativeimage.c.type.CIntPointer;
import org.graalvm.nativeimage.hosted.RuntimeClassInitialization;
import org.graalvm.nativeimage.hosted.RuntimeReflection;
import org.graalvm.nativeimage.impl.ConfigurationCondition;
import org.graalvm.nativeimage.impl.RuntimeForeignAccessSupport;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisUniverse;
import com.oracle.svm.core.LinkToNativeSupport;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.configure.ConfigurationFile;
import com.oracle.svm.core.configure.ConfigurationFiles;
import com.oracle.svm.core.configure.ConfigurationParser;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.foreign.AbiUtils;
import com.oracle.svm.core.foreign.ForeignFunctionsRuntime;
import com.oracle.svm.core.foreign.JavaEntryPointInfo;
import com.oracle.svm.core.foreign.LinkToNativeSupportImpl;
import com.oracle.svm.core.foreign.NativeEntryPointInfo;
import com.oracle.svm.core.foreign.RuntimeSystemLookup;
import com.oracle.svm.core.graal.meta.SubstrateForeignCallsProvider;
import com.oracle.svm.core.meta.MethodPointer;
import com.oracle.svm.core.util.BasedOnJDKFile;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.ConditionalConfigurationRegistry;
import com.oracle.svm.hosted.FeatureImpl;
import com.oracle.svm.hosted.ProgressReporter;
import com.oracle.svm.hosted.code.CEntryPointData;
import com.oracle.svm.hosted.config.ConfigurationParserUtils;
import com.oracle.svm.util.ModuleSupport;
import com.oracle.svm.util.ReflectionUtil;

import jdk.graal.compiler.api.replacements.Fold;
import jdk.graal.compiler.serviceprovider.JavaVersionUtil;
import jdk.internal.foreign.abi.AbstractLinker;
import jdk.internal.foreign.abi.LinkerOptions;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;

@AutomaticallyRegisteredFeature
@Platforms(Platform.HOSTED_ONLY.class)
public class ForeignFunctionsFeature implements InternalFeature {
    private static final Map<String, String[]> REQUIRES_CONCEALED = Map.of(
                    "jdk.internal.vm.ci", new String[]{"jdk.vm.ci.code", "jdk.vm.ci.meta", "jdk.vm.ci.amd64"},
                    "java.base", new String[]{
                                    "jdk.internal.foreign",
                                    "jdk.internal.foreign.abi",
                                    "jdk.internal.foreign.abi.x64",
                                    "jdk.internal.foreign.abi.x64.sysv",
                                    "jdk.internal.foreign.abi.x64.windows"});

    private boolean sealed = false;
    private final RuntimeForeignAccessSupportImpl accessSupport = new RuntimeForeignAccessSupportImpl();

    private final Set<SharedDesc> registeredDowncalls = ConcurrentHashMap.newKeySet();
    private int downcallCount = -1;

    private final Set<SharedDesc> registeredUpcalls = ConcurrentHashMap.newKeySet();
    private int upcallCount = -1;

    private final Set<DirectUpcallDesc> registeredDirectUpcalls = ConcurrentHashMap.newKeySet();
    private int directUpcallCount = -1;

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
    private record SharedDesc(FunctionDescriptor fd, Linker.Option[] options) {
        @Override
        public boolean equals(Object o) {
            if (this == o || !(o instanceof SharedDesc that)) {
                return this == o;
            }
            return Objects.equals(fd, that.fd) && Arrays.equals(options, that.options);
        }

        @Override
        public int hashCode() {
            return Objects.hash(fd, Arrays.hashCode(options));
        }
    }

    private record DirectUpcallDesc(MethodHandle mh, DirectMethodHandleDesc mhDesc, FunctionDescriptor fd, Linker.Option[] options) {

        public SharedDesc toSharedDesc() {
            return new SharedDesc(fd, options);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o || !(o instanceof DirectUpcallDesc that)) {
                return this == o;
            }
            return Objects.equals(mhDesc, that.mhDesc) && Objects.equals(fd, that.fd) && Arrays.equals(options, that.options);
        }

        @Override
        public int hashCode() {
            return Objects.hash(mhDesc, fd, Arrays.hashCode(options));
        }
    }

    private final class RuntimeForeignAccessSupportImpl extends ConditionalConfigurationRegistry implements StronglyTypedRuntimeForeignAccessSupport {

        private final Lookup implLookup = ReflectionUtil.readStaticField(MethodHandles.Lookup.class, "IMPL_LOOKUP");

        @Override
        public void registerForDowncall(ConfigurationCondition condition, FunctionDescriptor desc, Linker.Option... options) {
            checkNotSealed();
            registerConditionalConfiguration(condition, (cnd) -> registeredDowncalls.add(new SharedDesc(desc, options)));
        }

        @Override
        public void registerForUpcall(ConfigurationCondition condition, FunctionDescriptor desc, Linker.Option... options) {
            checkNotSealed();
            registerConditionalConfiguration(condition, (ignored) -> registeredUpcalls.add(new SharedDesc(desc, options)));
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
            registerConditionalConfiguration(condition, (ignored) -> {
                RuntimeReflection.register(method);
                registeredDirectUpcalls.add(new DirectUpcallDesc(target, directMethodHandleDesc, desc, options));
            });
        }
    }

    ForeignFunctionsFeature() {
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
        UserError.guarantee(JavaVersionUtil.JAVA_SPEC >= 22, "Support for the Foreign Function and Memory API is available only with JDK 22 and later.");
        UserError.guarantee(SubstrateUtil.getArchitectureName().contains("amd64"), "Support for the Foreign Function and Memory API is currently available only on the AMD64 architecture.");
        UserError.guarantee(!SubstrateOptions.useLLVMBackend(), "Support for the Foreign Function and Memory API is not available with the LLVM backend.");
        return true;
    }

    @Override
    public void duringSetup(DuringSetupAccess a) {
        var access = (FeatureImpl.DuringSetupAccessImpl) a;
        ImageSingletons.add(AbiUtils.class, AbiUtils.create());
        ImageSingletons.add(ForeignFunctionsRuntime.class, new ForeignFunctionsRuntime());
        ImageSingletons.add(RuntimeForeignAccessSupport.class, accessSupport);
        ImageSingletons.add(LinkToNativeSupport.class, new LinkToNativeSupportImpl());

        ConfigurationParser parser = new ForeignFunctionsConfigurationParser(access.getImageClassLoader(), accessSupport);
        ConfigurationParserUtils.parseAndRegisterConfigurations(parser, access.getImageClassLoader(), "panama foreign",
                        ConfigurationFiles.Options.ForeignConfigurationFiles, ConfigurationFiles.Options.ForeignResources, ConfigurationFile.FOREIGN.getFileName());
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
        private final Set<SharedDesc> registeredUpcalls;

        DirectUpcallStubFactory(AnalysisUniverse universe, MetaAccessProvider metaAccessProvider, Set<SharedDesc> registeredUpcalls) {
            this.universe = universe;
            this.metaAccessProvider = metaAccessProvider;
            this.registeredUpcalls = registeredUpcalls;
            this.arrangeUpcallMethod = ReflectionUtil.lookupMethod(LINKER.getClass(), "arrangeUpcall", MethodType.class, FunctionDescriptor.class, LinkerOptions.class);
        }

        @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-24+25/src/java.base/share/classes/jdk/internal/foreign/abi/AbstractLinker.java#L117-L135")
        @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-24+25/src/java.base/share/classes/jdk/internal/foreign/abi/UpcallLinker.java#L64-L112")
        @Override
        public DirectUpcall createKey(DirectUpcallDesc desc) {
            LinkerOptions optionSet = LinkerOptions.forUpcall(desc.fd(), desc.options());
            /*
             * For each unique link request, 'AbstractLinker.upcallStub' calls
             * 'AbstractLinker.arrangeUpcall' which produces an upcall stub factory. This factory is
             * a closure created in 'UpcallLinker.makeFactory' and it closes over the
             * 'doBindingsMaker' (stored in a private field). This is a unary operator that is
             * applied to the user-provided method handle. We then re-create a method handle that is
             * equal to the one created in 'UpcallLinker.makeFactory'. This MH is then invoked from
             * the specialized upcall stub.
             */
            AbstractLinker.UpcallStubFactory upcallStubFactory = ReflectionUtil.invokeMethod(arrangeUpcallMethod, LINKER, desc.fd().toMethodType(), desc.fd(), optionSet);
            UnaryOperator<MethodHandle> doBindingsMaker = lookupAndReadUnaryOperatorField(upcallStubFactory);
            MethodHandle doBindings = doBindingsMaker.apply(desc.mh());
            doBindings = insertArguments(exactInvoker(doBindings.type()), 0, doBindings);
            JavaEntryPointInfo jepi = AbiUtils.singleton().makeJavaEntryPoint(desc.fd(), desc.options());
            registeredUpcalls.add(desc.toSharedDesc());
            return new DirectUpcall(desc.mhDesc(), doBindings, jepi);
        }

        @Override
        public UpcallStub generateStub(DirectUpcall directUpcall) {
            return LowLevelUpcallStub.makeDirect(directUpcall.bindings(), directUpcall.jep(), universe, metaAccessProvider);
        }

        @Override
        public void registerStub(DirectUpcall stubDescriptor, CFunctionPointer stubPointer) {
            ForeignFunctionsRuntime.singleton().addDirectUpcallStubPointer(stubDescriptor.targetDesc(), stubPointer);
        }

        /**
         * Looks up a field of type {@link UnaryOperator}, reads its value and returns it. There
         * must be exactly one such field that is readable. Otherwise, an Error is thrown.
         */
        private static UnaryOperator<MethodHandle> lookupAndReadUnaryOperatorField(AbstractLinker.UpcallStubFactory upcallStubFactory) {
            Class<? extends AbstractLinker.UpcallStubFactory> upcallStubFactoryClass = upcallStubFactory.getClass();
            List<Field> list = Arrays.stream(upcallStubFactoryClass.getDeclaredFields())
                            .filter(field -> UnaryOperator.class.isAssignableFrom(field.getType())).toList();
            if (list.size() != 1) {
                throw VMError.shouldNotReachHere(COULD_NOT_EXTRACT_METHOD_HANDLE_FOR_UPCALL);
            }
            Field candidate = list.getFirst();
            assert candidate != null;

            UnaryOperator<MethodHandle> value = ReflectionUtil.readField(upcallStubFactoryClass, candidate.getName(), upcallStubFactory);
            if (value == null) {
                throw VMError.shouldNotReachHere(COULD_NOT_EXTRACT_METHOD_HANDLE_FOR_UPCALL);
            }

            return value;
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

    private static final String JLI_PREFIX = "java.lang.invoke.";

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
        assert subtype.getPackage().getName().equals(JLI_PREFIX.substring(0, JLI_PREFIX.length() - 1));
        RuntimeReflection.register(subtype.getDeclaredMethods());
    }

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess a) {
        var access = (FeatureImpl.BeforeAnalysisAccessImpl) a;
        sealed = true;

        AbiUtils.singleton().checkLibrarySupport();

        for (String simpleName : VAR_HANDLE_SEGMENT_ACCESSORS) {
            Class<?> varHandleSegmentAsXClass = ReflectionUtil.lookupClass(JLI_PREFIX + simpleName);
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

        createDowncallStubs(access);
        createUpcallStubs(access);
        ProgressReporter.singleton().setForeignFunctionsInfo(getCreatedDowncallStubsCount(), getCreatedUpcallStubsCount());
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
