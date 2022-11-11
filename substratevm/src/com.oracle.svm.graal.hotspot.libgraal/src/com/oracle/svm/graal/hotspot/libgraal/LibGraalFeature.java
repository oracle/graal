/*
 * Copyright (c) 2017, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.graal.hotspot.libgraal;

import static com.oracle.svm.graal.hotspot.libgraal.LibGraalEntryPoints.RuntimeStubInfo.Util.newCodeInfo;
import static com.oracle.svm.graal.hotspot.libgraal.LibGraalEntryPoints.RuntimeStubInfo.Util.newRuntimeStubInfo;
import static jdk.vm.ci.hotspot.HotSpotJVMCIRuntime.runtime;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.annotation.Annotation;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.function.BooleanSupplier;
import java.util.stream.Collectors;

import org.graalvm.collections.EconomicMap;
import org.graalvm.compiler.code.DisassemblerProvider;
import org.graalvm.compiler.core.GraalServiceThread;
import org.graalvm.compiler.core.common.spi.ForeignCallSignature;
import org.graalvm.compiler.core.target.Backend;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.debug.TTY;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.hotspot.EncodedSnippets;
import org.graalvm.compiler.hotspot.HotSpotCodeCacheListener;
import org.graalvm.compiler.hotspot.HotSpotForeignCallLinkageImpl;
import org.graalvm.compiler.hotspot.HotSpotForeignCallLinkageImpl.CodeInfo;
import org.graalvm.compiler.hotspot.HotSpotGraalCompiler;
import org.graalvm.compiler.hotspot.HotSpotGraalOptionValues;
import org.graalvm.compiler.hotspot.HotSpotGraalRuntime;
import org.graalvm.compiler.hotspot.HotSpotReplacementsImpl;
import org.graalvm.compiler.hotspot.SnippetObjectConstant;
import org.graalvm.compiler.hotspot.meta.HotSpotHostForeignCallsProvider;
import org.graalvm.compiler.hotspot.meta.HotSpotInvocationPluginProvider;
import org.graalvm.compiler.hotspot.meta.HotSpotProviders;
import org.graalvm.compiler.hotspot.stubs.Stub;
import org.graalvm.compiler.nodes.graphbuilderconf.GeneratedPluginFactory;
import org.graalvm.compiler.nodes.spi.SnippetParameterInfo;
import org.graalvm.compiler.options.Option;
import org.graalvm.compiler.options.OptionDescriptor;
import org.graalvm.compiler.options.OptionDescriptors;
import org.graalvm.compiler.options.OptionDescriptorsMap;
import org.graalvm.compiler.options.OptionKey;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.options.OptionsParser;
import org.graalvm.compiler.phases.util.Providers;
import org.graalvm.compiler.serviceprovider.GraalServices;
import org.graalvm.compiler.serviceprovider.IsolateUtil;
import org.graalvm.compiler.serviceprovider.SpeculationReasonGroup;
import org.graalvm.compiler.truffle.common.TruffleCompilerRuntime;
import org.graalvm.compiler.truffle.compiler.PartialEvaluatorConfiguration;
import org.graalvm.compiler.truffle.compiler.hotspot.TruffleCallBoundaryInstrumentationFactory;
import org.graalvm.compiler.truffle.compiler.substitutions.GraphBuilderInvocationPluginProvider;
import org.graalvm.compiler.truffle.compiler.substitutions.GraphDecoderInvocationPluginProvider;
import org.graalvm.compiler.truffle.runtime.GraalTruffleRuntime;
import org.graalvm.jniutils.JNI;
import org.graalvm.jniutils.JNIExceptionWrapper;
import org.graalvm.jniutils.JNIMethodScope;
import org.graalvm.jniutils.JNIUtil;
import org.graalvm.jniutils.NativeBridgeSupport;
import org.graalvm.libgraal.LibGraal;
import org.graalvm.libgraal.jni.LibGraalNativeBridgeSupport;
import org.graalvm.libgraal.jni.LibGraalUtil;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.LogHandler;
import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.VMRuntime;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.hosted.RuntimeJNIAccess;
import org.graalvm.nativeimage.hosted.RuntimeReflection;
import org.graalvm.word.LocationIdentity;
import org.graalvm.word.Pointer;
import org.graalvm.word.WordFactory;

import com.oracle.graal.pointsto.BigBang;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisUniverse;
import com.oracle.graal.pointsto.meta.InvokeInfo;
import com.oracle.svm.core.OS;
import com.oracle.svm.core.RuntimeAssertionsSupport;
import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.Delete;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.RecomputeFieldValue.Kind;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.annotate.TargetElement;
import com.oracle.svm.core.c.CGlobalData;
import com.oracle.svm.core.c.CGlobalDataFactory;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.graal.meta.RuntimeConfiguration;
import com.oracle.svm.core.graal.snippets.NodeLoweringProvider;
import com.oracle.svm.core.heap.GCCause;
import com.oracle.svm.core.heap.Heap;
import com.oracle.svm.core.log.FunctionPointerLogHandler;
import com.oracle.svm.core.option.HostedOptionKey;
import com.oracle.svm.core.option.RuntimeOptionKey;
import com.oracle.svm.core.option.RuntimeOptionValues;
import com.oracle.svm.core.option.XOptions;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.core.util.UserError.UserException;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.graal.hosted.RuntimeCompilationFeature;
import com.oracle.svm.graal.hotspot.libgraal.LibGraalEntryPoints.RuntimeStubInfo;
import com.oracle.svm.hosted.FeatureImpl;
import com.oracle.svm.hosted.FeatureImpl.DuringSetupAccessImpl;
import com.oracle.svm.hosted.ImageClassLoader;
import com.oracle.svm.hosted.jni.JNIFeature;
import com.oracle.svm.hosted.reflect.ReflectionFeature;
import com.oracle.svm.util.ModuleSupport;
import com.oracle.svm.util.ReflectionUtil;

import jdk.vm.ci.code.CompilationRequest;
import jdk.vm.ci.code.CompilationRequestResult;
import jdk.vm.ci.common.NativeImageReinitialize;
import jdk.vm.ci.hotspot.HotSpotConstantReflectionProvider;
import jdk.vm.ci.hotspot.HotSpotJVMCIBackendFactory;
import jdk.vm.ci.hotspot.HotSpotJVMCIRuntime;
import jdk.vm.ci.hotspot.HotSpotSignature;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.services.Services;

class LibGraalOptions {
    @Option(help = "Converts an exception triggered by the CrashAt option into a fatal error " +
                    "if a non-null pointer was passed in the _fatal option to JNI_CreateJavaVM. " +
                    "This option exists for the purpose of testing fatal error handling in libgraal.") //
    static final RuntimeOptionKey<Boolean> CrashAtIsFatal = new RuntimeOptionKey<>(false);
    @Option(help = "The fully qualified name of a no-arg, void, static method to be invoked " +
                    "in HotSpot from libgraal when the libgraal isolate is being shutdown." +
                    "This option exists for the purpose of testing callbacks in this context.") //
    static final RuntimeOptionKey<String> OnShutdownCallback = new RuntimeOptionKey<>(null);
}

public class LibGraalFeature implements InternalFeature {

    private HotSpotReplacementsImpl hotSpotSubstrateReplacements;

    public LibGraalFeature() {
        /* Open up all modules needed to build LibGraal image */
        ModuleSupport.accessPackagesToClass(ModuleSupport.Access.EXPORT, LibGraalFeature.class, false, "java.base", "jdk.internal.misc");
        ModuleSupport.accessPackagesToClass(ModuleSupport.Access.EXPORT, LibGraalFeature.class, false, "jdk.internal.vm.ci");
        ModuleSupport.accessPackagesToClass(ModuleSupport.Access.EXPORT, LibGraalFeature.class, false, "jdk.internal.vm.compiler");
        ModuleSupport.accessPackagesToClass(ModuleSupport.Access.EXPORT, LibGraalFeature.class, false, "jdk.internal.vm.compiler.management");
        ModuleSupport.accessPackagesToClass(ModuleSupport.Access.EXPORT, LibGraalFeature.class, false, "org.graalvm.sdk", "org.graalvm.nativeimage.impl");
        ModuleSupport.accessPackagesToClass(ModuleSupport.Access.EXPORT, LibGraalFeature.class, false, "org.graalvm.nativeimage.base");
        ModuleSupport.accessPackagesToClass(ModuleSupport.Access.EXPORT, LibGraalFeature.class, false, "org.graalvm.nativeimage.builder");
        ModuleSupport.accessPackagesToClass(ModuleSupport.Access.EXPORT, LibGraalFeature.class, true, "org.graalvm.nativeimage.llvm");
    }

    @Override
    public void afterImageWrite(AfterImageWriteAccess access) {
    }

    @Override
    public boolean isInConfiguration(IsInConfigurationAccess access) {
        if (!LibGraal.isSupported()) {
            throw new InternalError("LibGraalFeature is not supported by the current JDK");
        }
        return true;
    }

    @Override
    public List<Class<? extends Feature>> getRequiredFeatures() {
        return List.of(JNIFeature.class, RuntimeCompilationFeature.getRuntimeCompilationFeature(), ReflectionFeature.class);
    }

    public static final class IsEnabled implements BooleanSupplier {
        @Override
        public boolean getAsBoolean() {
            return ImageSingletons.contains(LibGraalFeature.class);
        }
    }

    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        ImageSingletons.add(NativeBridgeSupport.class, new LibGraalNativeBridgeSupport());
    }

    @Override
    public void duringSetup(DuringSetupAccess access) {
        ImageClassLoader imageClassLoader = ((DuringSetupAccessImpl) access).getImageClassLoader();

        registerJNIConfiguration(imageClassLoader);
        EconomicMap<String, OptionDescriptor> descriptors = EconomicMap.create();
        for (Class<? extends OptionDescriptors> optionsClass : imageClassLoader.findSubclasses(OptionDescriptors.class, false)) {
            if (!Modifier.isAbstract(optionsClass.getModifiers()) && !OptionDescriptorsMap.class.isAssignableFrom(optionsClass)) {
                try {
                    ModuleSupport.accessModuleByClass(ModuleSupport.Access.EXPORT, LibGraalFeature.class, optionsClass);
                    for (OptionDescriptor d : optionsClass.getDeclaredConstructor().newInstance()) {
                        if (!(d.getOptionKey() instanceof HostedOptionKey)) {
                            descriptors.put(d.getName(), d);
                        }
                    }
                } catch (ReflectiveOperationException ex) {
                    throw VMError.shouldNotReachHere(ex);
                }
            }
        }
        OptionsParser.setCachedOptionDescriptors(Collections.singletonList(new OptionDescriptorsMap(descriptors)));
    }

    /**
     * Helper for registering the JNI configuration for libgraal by parsing the output of the
     * {@code -XX:JVMCILibDumpJNIConfig} VM option.
     */
    static class JNIConfigSource implements AutoCloseable {
        /**
         * VM command executed to read the JNI config.
         */
        private final String quotedCommand;

        /**
         * JNI config lines.
         */
        private final List<String> lines;

        /**
         * Loader used to resolve type names in the config.
         */
        private final ImageClassLoader loader;

        /**
         * Path to intermediate file containing the config. This is deleted unless there is an
         * {@link #error(String, Object...)} parsing the config to make diagnosing the error easier.
         */
        private Path configFilePath;

        int lineNo;

        JNIConfigSource(ImageClassLoader loader) {
            this.loader = loader;
            Path javaHomePath = Paths.get(System.getProperty("java.home"));
            Path binJava = Paths.get("bin", OS.getCurrent() == OS.WINDOWS ? "java.exe" : "java");
            Path javaExe = javaHomePath.resolve(binJava);
            if (!Files.isExecutable(javaExe)) {
                throw UserError.abort("Java launcher %s does not exist or is not executable", javaExe);
            }
            configFilePath = Paths.get("libgraal_jniconfig.txt");

            String[] command = {javaExe.toFile().getAbsolutePath(), "-XX:+UnlockExperimentalVMOptions", "-XX:+EnableJVMCI", "-XX:JVMCILibDumpJNIConfig=" + configFilePath};
            quotedCommand = Arrays.asList(command).stream().map(e -> e.indexOf(' ') == -1 ? e : '\'' + e + '\'').collect(Collectors.joining(" "));
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            Process p;
            try {
                p = pb.start();
            } catch (IOException e) {
                throw UserError.abort("Could not run command: %s%n%s", quotedCommand, e);
            }

            String nl = System.getProperty("line.separator");
            String out = new BufferedReader(new InputStreamReader(p.getInputStream()))
                            .lines().collect(Collectors.joining(nl));

            int exitValue;
            try {
                exitValue = p.waitFor();
            } catch (InterruptedException e) {
                throw UserError.abort("Interrupted waiting for command: %s%n%s", quotedCommand, out);
            }
            if (exitValue != 0) {
                throw UserError.abort("Command finished with exit value %d: %s%n%s", exitValue, quotedCommand, out);
            }
            try {
                lines = Files.readAllLines(configFilePath);
            } catch (IOException e) {
                configFilePath = null;
                throw UserError.abort("Reading JNI config in %s dumped by command: %s%n%s", configFilePath, quotedCommand, out);
            }
        }

        @Override
        public void close() {
            if (configFilePath != null && Files.exists(configFilePath)) {
                try {
                    Files.delete(configFilePath);
                    configFilePath = null;
                } catch (IOException e) {
                    System.out.printf("Warning: Cound not delete %s: %s%n", configFilePath, e);
                }
            }
        }

        Class<?> findClass(String name) {
            String internalName = name;
            if (name.startsWith("L") && name.endsWith(";")) {
                internalName = name.substring(1, name.length() - 1);
            }
            Class<?> c = loader.findClass(internalName).get();
            if (c == null) {
                throw error("Class " + internalName + " not found");
            }
            return c;
        }

        void check(boolean condition, String format, Object... args) {
            if (!condition) {
                error(format, args);
            }
        }

        UserException error(String format, Object... args) {
            Path path = configFilePath;
            configFilePath = null; // prevent deletion
            String errorMessage = String.format(format, args);
            String errorLine = lines.get(lineNo - 1);
            throw UserError.abort("Line %d of %s: %s%n%s%n%s generated by command: %s",
                            lineNo, path.toAbsolutePath(), errorMessage, errorLine, path, quotedCommand);

        }
    }

    private static void registerJNIConfiguration(ImageClassLoader loader) {
        try (JNIConfigSource source = new JNIConfigSource(loader)) {
            Map<String, Class<?>> classes = new HashMap<>();
            for (String line : source.lines) {
                source.lineNo++;
                String[] tokens = line.split(" ");
                source.check(tokens.length >= 2, "Expected at least 2 tokens");
                String className = tokens[1].replace('/', '.');
                Class<?> clazz = classes.get(className);
                if (clazz == null) {
                    clazz = source.findClass(className);
                    RuntimeJNIAccess.register(clazz);
                    RuntimeJNIAccess.register(Array.newInstance(clazz, 0).getClass());
                    classes.put(className, clazz);
                }

                switch (tokens[0]) {
                    case "field": {
                        source.check(tokens.length == 4, "Expected 4 tokens for a field");
                        String fieldName = tokens[2];
                        try {
                            RuntimeJNIAccess.register(clazz.getDeclaredField(fieldName));
                        } catch (NoSuchFieldException e) {
                            throw source.error("Field %s.%s not found", clazz.getTypeName(), fieldName);
                        } catch (NoClassDefFoundError e) {
                            throw source.error("Could not register field %s.%s: %s", clazz.getTypeName(), fieldName, e);
                        }
                        break;
                    }
                    case "method": {
                        source.check(tokens.length == 4, "Expected 4 tokens for a method");
                        String methodName = tokens[2];
                        HotSpotSignature descriptor = new HotSpotSignature(runtime(), tokens[3]);
                        Class<?>[] parameters = Arrays.asList(descriptor.toParameterTypes(null))//
                                        .stream().map(JavaType::toClassName).map(source::findClass)//
                                        .collect(Collectors.toList())//
                                        .toArray(new Class<?>[descriptor.getParameterCount(false)]);
                        try {
                            if ("<init>".equals(methodName)) {
                                Constructor<?> cons = clazz.getDeclaredConstructor(parameters);
                                RuntimeJNIAccess.register(cons);
                                if (Throwable.class.isAssignableFrom(clazz) && !Modifier.isAbstract(clazz.getModifiers())) {
                                    if (usedInTranslatedException(parameters)) {
                                        RuntimeReflection.register(clazz);
                                        RuntimeReflection.register(cons);
                                    }
                                }
                            } else {
                                RuntimeJNIAccess.register(clazz.getDeclaredMethod(methodName, parameters));
                            }
                        } catch (NoSuchMethodException e) {
                            throw source.error("Method %s.%s%s not found: %s", clazz.getTypeName(), methodName, descriptor, e);
                        } catch (NoClassDefFoundError e) {
                            throw source.error("Could not register method %s.%s%s: %s", clazz.getTypeName(), methodName, descriptor, e);
                        }
                        break;
                    }
                    case "class": {
                        source.check(tokens.length == 2, "Expected 2 tokens for a class");
                        break;
                    }
                    default: {
                        throw source.error("Unexpected token: " + tokens[0]);
                    }
                }
            }
        }
    }

    /**
     * Determines if a throwable constructor with the signature specified by {@code parameters} is
     * potentially called via reflection in {@code jdk.vm.ci.hotspot.TranslatedException}.
     */
    private static boolean usedInTranslatedException(Class<?>[] parameters) {
        return parameters.length == 0 || (parameters.length == 1 && parameters[0] == String.class);
    }

    @Override
    public void registerLowerings(RuntimeConfiguration runtimeConfig, OptionValues options, Providers substrateProviders,
                    Map<Class<? extends Node>, NodeLoweringProvider<?>> lowerings, boolean hosted) {
        hotSpotSubstrateReplacements = getReplacements();
    }

    @SuppressWarnings({"try", "unchecked"})
    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        FeatureImpl.BeforeAnalysisAccessImpl impl = (FeatureImpl.BeforeAnalysisAccessImpl) access;
        BigBang bb = impl.getBigBang();
        DebugContext debug = bb.getDebug();

        // Services that will not be loaded if native-image is run
        // with -XX:-UseJVMCICompiler.
        GraalServices.load(TruffleCallBoundaryInstrumentationFactory.class);
        GraalServices.load(GraphBuilderInvocationPluginProvider.class);
        GraalServices.load(GraphDecoderInvocationPluginProvider.class);
        GraalServices.load(PartialEvaluatorConfiguration.class);
        GraalServices.load(HotSpotCodeCacheListener.class);
        GraalServices.load(DisassemblerProvider.class);
        GraalServices.load(HotSpotInvocationPluginProvider.class);

        try (DebugContext.Scope scope = debug.scope("SnippetSupportEncode")) {
            // Instantiate the truffle compiler to ensure the backends it uses are initialized.
            GraalTruffleRuntime.getRuntime().newTruffleCompiler();
        } catch (Throwable t) {
            throw debug.handle(t);
        }

        // Filter out any cached services which are for a different architecture
        try {
            HotSpotGraalCompiler compiler = (HotSpotGraalCompiler) HotSpotJVMCIRuntime.runtime().getCompiler();
            String osArch = compiler.getGraalRuntime().getVMConfig().osArch;
            String archPackage = "." + osArch + ".";

            final Field servicesCacheField = ReflectionUtil.lookupField(Services.class, "servicesCache");
            Map<Class<?>, List<?>> servicesCache = (Map<Class<?>, List<?>>) servicesCacheField.get(null);
            filterArchitectureServices(archPackage, servicesCache);
            servicesCache.remove(GeneratedPluginFactory.class);

            final Field graalServicesCacheField = ReflectionUtil.lookupField(GraalServices.class, "servicesCache");
            Map<Class<?>, List<?>> graalServicesCache = (Map<Class<?>, List<?>>) graalServicesCacheField.get(null);
            filterArchitectureServices(archPackage, graalServicesCache);
            graalServicesCache.remove(GeneratedPluginFactory.class);

            Field cachedHotSpotJVMCIBackendFactoriesField = ReflectionUtil.lookupField(HotSpotJVMCIRuntime.class, "cachedHotSpotJVMCIBackendFactories");
            List<HotSpotJVMCIBackendFactory> cachedHotSpotJVMCIBackendFactories = (List<HotSpotJVMCIBackendFactory>) cachedHotSpotJVMCIBackendFactoriesField.get(null);
            cachedHotSpotJVMCIBackendFactories.removeIf(factory -> !factory.getArchitecture().equalsIgnoreCase(osArch));
        } catch (ReflectiveOperationException ex) {
            throw VMError.shouldNotReachHere(ex);
        }

        // Force construction of all stubs so their types are known.
        HotSpotProviders providers = getReplacements().getProviders();
        HotSpotHostForeignCallsProvider foreignCalls = providers.getForeignCalls();
        foreignCalls.forEachForeignCall((sig, linkage) -> {
            if (linkage == null || linkage.isCompiledStub()) {
                boolean nonConstant = true;
                String symbol = null;
                CGlobalData<Pointer> data = CGlobalDataFactory.createWord((Pointer) WordFactory.zero(), symbol, nonConstant);
                LibGraalEntryPoints.STUBS.put(sig, data);
                if (linkage != null) {
                    // Force stub construction
                    foreignCalls.lookupForeignCall(sig);
                }
            }
        });

        hotSpotSubstrateReplacements.encode(bb.getOptions());
        if (!RuntimeAssertionsSupport.singleton().desiredAssertionStatus(SnippetParameterInfo.class)) {
            // Clear the saved names if assertions aren't enabled
            hotSpotSubstrateReplacements.clearSnippetParameterNames();
        }
        // Mark all the Node classes as allocated so they are available during graph decoding.
        EncodedSnippets encodedSnippets = HotSpotReplacementsImpl.getEncodedSnippets();
        for (NodeClass<?> nodeClass : encodedSnippets.getSnippetNodeClasses()) {
            bb.markTypeInHeap(impl.getMetaAccess().lookupJavaType(nodeClass.getClazz()));
        }
    }

    private static void filterArchitectureServices(String archPackage, Map<Class<?>, List<?>> services) {
        for (List<?> list : services.values()) {
            list.removeIf(o -> {
                String name = o.getClass().getName();
                if (name.contains(".aarch64.") || name.contains(".amd64.")) {
                    return !name.contains(archPackage);
                }
                return false;
            });
        }
    }

    @Override
    public void afterAnalysis(AfterAnalysisAccess access) {
        verifyReachableTruffleClasses(access);
    }

    @Override
    public void afterCompilation(AfterCompilationAccess access) {
        EncodedSnippets encodedSnippets = HotSpotReplacementsImpl.getEncodedSnippets();
        encodedSnippets.visitImmutable(access::registerAsImmutable);
    }

    /**
     * Verifies that the Truffle compiler does not bring Truffle API types into an image. The
     * Truffle compiler depends on {@code org.graalvm.compiler.truffle.options} which depends on the
     * Truffle APIs to be able to use the {@code @com.oracle.truffle.api.Option} annotation. We need
     * to use the points to analysis to verify that the Truffle API types are not reachable.
     */
    private static void verifyReachableTruffleClasses(AfterAnalysisAccess access) {
        AnalysisUniverse universe = ((FeatureImpl.AfterAnalysisAccessImpl) access).getUniverse();
        Map<AnalysisMethod, Object> seen = new LinkedHashMap<>();
        for (AnalysisMethod analysisMethod : universe.getMethods()) {
            if (analysisMethod.isDirectRootMethod() && analysisMethod.isImplementationInvoked()) {
                seen.put(analysisMethod, "direct root");
            }
            if (analysisMethod.isVirtualRootMethod()) {
                for (AnalysisMethod impl : analysisMethod.getImplementations()) {
                    VMError.guarantee(impl.isImplementationInvoked());
                    seen.put(impl, "virtual root");
                }
            }
        }
        Deque<AnalysisMethod> todo = new ArrayDeque<>(seen.keySet());
        SortedSet<String> disallowedTypes = new TreeSet<>();
        while (!todo.isEmpty()) {
            AnalysisMethod m = todo.removeFirst();
            String className = m.getDeclaringClass().toClassName();
            if (!isAllowedType(className)) {
                StringBuilder msg = new StringBuilder(className);
                Object reason = m;
                while (true) {
                    msg.append("<-");
                    if (reason instanceof ResolvedJavaMethod) {
                        msg.append(((ResolvedJavaMethod) reason).format("%H.%n(%p)"));
                        reason = seen.get(reason);
                    } else {
                        msg.append(reason);
                        break;
                    }
                }
                disallowedTypes.add(msg.toString());
            }
            for (InvokeInfo invoke : m.getInvokes()) {
                for (AnalysisMethod callee : invoke.getCallees()) {
                    if (seen.putIfAbsent(callee, m) == null) {
                        todo.add(callee);
                    }
                }
            }
        }
        if (!disallowedTypes.isEmpty()) {
            throw UserError.abort("Following non allowed Truffle types are reachable on heap: %s", String.join(", ", disallowedTypes));
        }
    }

    private static boolean isAllowedType(String className) {
        if (className.startsWith("com.oracle.truffle.")) {
            return className.startsWith("com.oracle.truffle.api.nodes.") || className.startsWith("com.oracle.truffle.compiler.enterprise.");
        }
        return true;
    }

    static HotSpotReplacementsImpl getReplacements() {
        HotSpotGraalCompiler compiler = (HotSpotGraalCompiler) HotSpotJVMCIRuntime.runtime().getCompiler();
        HotSpotProviders originalProvider = compiler.getGraalRuntime().getHostProviders();
        return (HotSpotReplacementsImpl) originalProvider.getReplacements();
    }
}

@TargetClass(className = "jdk.vm.ci.hotspot.SharedLibraryJVMCIReflection", onlyWith = LibGraalFeature.IsEnabled.class)
final class Target_jdk_vm_ci_hotspot_SharedLibraryJVMCIReflection {

    @Substitute
    static Object convertUnknownValue(Object object) {
        return object;
    }

    // Annotations are currently unsupported in libgraal. These substitutions will turn their use
    // into a image time build error.
    @Delete
    static native Annotation[] getClassAnnotations(String className);

    @Delete
    static native Annotation[][] getParameterAnnotations(String className, String methodName);

    @Delete
    static native Annotation[] getMethodAnnotationsInternal(ResolvedJavaMethod javaMethod);
}

@TargetClass(value = SpeculationReasonGroup.class, onlyWith = LibGraalFeature.IsEnabled.class)
final class Target_org_graalvm_compiler_serviceprovider_SpeculationReasonGroup {

    /**
     * Delete this constructor to ensure {@link SpeculationReasonGroup} ids are in the libgraal
     * image and thus global across all libgraal isolates.
     */
    @Delete
    @TargetElement(name = TargetElement.CONSTRUCTOR_NAME)
    native void constructor(String name, Class<?>... signature);
}

/**
 * {@link HotSpotConstantReflectionProvider#forObject} can only be used to wrap compiler objects so
 * interpose to return a {@link SnippetObjectConstant}.
 */
@TargetClass(className = "jdk.vm.ci.hotspot.HotSpotConstantReflectionProvider", onlyWith = LibGraalFeature.IsEnabled.class)
final class Target_jdk_vm_ci_hotspot_HotSpotConstantReflectionProvider {

    @Substitute
    public JavaConstant forString(String value) {
        return forObject(value);
    }

    @Substitute
    @SuppressWarnings({"static-method", "unused"})
    public JavaConstant forObject(Object value) {
        return new SnippetObjectConstant(value);
    }
}

@TargetClass(className = "jdk.vm.ci.hotspot.DirectHotSpotObjectConstantImpl", onlyWith = LibGraalFeature.IsEnabled.class)
final class Target_jdk_vm_ci_hotspot_DirectHotSpotObjectConstantImpl {

    @Substitute
    @SuppressWarnings({"static-method", "unused"})
    @TargetElement(name = TargetElement.CONSTRUCTOR_NAME)
    void constructor(Object object, boolean compressed) {
        throw new InternalError("DirectHotSpotObjectConstantImpl unsupported");
    }
}

@TargetClass(className = "org.graalvm.compiler.hotspot.HotSpotGraalCompiler", onlyWith = LibGraalFeature.IsEnabled.class)
final class Target_org_graalvm_compiler_hotspot_HotSpotGraalCompiler {

    @SuppressWarnings({"unused", "try"})
    @Substitute
    private static CompilationRequestResult compileMethod(HotSpotGraalCompiler compiler, CompilationRequest request) {
        long offset = compiler.getGraalRuntime().getVMConfig().jniEnvironmentOffset;
        long javaThreadAddr = HotSpotJVMCIRuntime.runtime().getCurrentJavaThread();
        JNI.JNIEnv env = (JNI.JNIEnv) WordFactory.unsigned(javaThreadAddr).add(WordFactory.unsigned(offset));
        // This scope is required to allow Graal compilations of host methods to call methods
        // on the TruffleCompilerRuntime. This is, for example, required to find out about
        // Truffle-specific method annotations.
        try {
            try (JNIMethodScope scope = LibGraalUtil.openScope("<called from VM>", env)) {
                return compiler.compileMethod(request, true, compiler.getGraalRuntime().getOptions());
            }
        } finally {
            /*
             * libgraal doesn't use a dedicated reference handler thread, so we trigger the
             * reference handling manually when a compilation finishes.
             */
            Heap.getHeap().doReferenceHandling();
        }
    }
}

@TargetClass(className = "org.graalvm.compiler.hotspot.HotSpotGraalRuntime", onlyWith = LibGraalFeature.IsEnabled.class)
final class Target_org_graalvm_compiler_hotspot_HotSpotGraalRuntime {

    @SuppressWarnings("unused")
    @Substitute
    private static void startupLibGraal(HotSpotGraalRuntime runtime) {
        VMRuntime.initialize();
    }

    @Substitute
    private static void shutdownLibGraal(HotSpotGraalRuntime runtime) {
        try {
            // Unregister this isolate if it was created as a peer
            if (LibGraalEntryPoints.hasLibGraalIsolatePeer()) {
                long offset = runtime.getVMConfig().jniEnvironmentOffset;
                long javaThreadAddr = HotSpotJVMCIRuntime.runtime().getCurrentJavaThread();
                JNI.JNIEnv env = (JNI.JNIEnv) WordFactory.unsigned(javaThreadAddr).add(WordFactory.unsigned(offset));
                JNI.JClass libGraalIsolateClass = JNIUtil.findClass(env, JNIUtil.getJVMCIClassLoader(env),
                                JNIUtil.getBinaryName("org.graalvm.libgraal.LibGraalIsolate"), true);
                JNI.JMethodID unregisterMethod = JNIUtil.findMethod(env, libGraalIsolateClass, true, "unregister", "(J)V");
                JNI.JValue args = StackValue.get(JNI.JValue.class);
                args.setLong(IsolateUtil.getIsolateID());
                env.getFunctions().getCallStaticVoidMethodA().call(env, libGraalIsolateClass, unregisterMethod, args);
                JNIExceptionWrapper.wrapAndThrowPendingJNIException(env);

                String callback = LibGraalOptions.OnShutdownCallback.getValue();
                if (callback != null) {
                    int lastDot = callback.lastIndexOf('.');
                    if (lastDot < 1 || lastDot == callback.length() - 1) {
                        throw new IllegalArgumentException(LibGraalOptions.OnShutdownCallback.getName() + " value does not have <classname>.<method name> format: " + callback);
                    }
                    String cbClassName = callback.substring(0, lastDot);
                    String cbMethodName = callback.substring(lastDot + 1);
                    JNI.JClass cbClass = JNIUtil.findClass(env, JNIUtil.getSystemClassLoader(env),
                                    JNIUtil.getBinaryName(cbClassName), true);
                    JNI.JMethodID cbMethod = JNIUtil.findMethod(env, cbClass, true, cbMethodName, "()V");
                    env.getFunctions().getCallStaticVoidMethodA().call(env, cbClass, cbMethod, StackValue.get(0));
                    JNIExceptionWrapper.wrapAndThrowPendingJNIException(env);
                }
            }
        } catch (Throwable t) {
            t.printStackTrace(TTY.out);
        } finally {
            VMRuntime.shutdown();
        }
    }
}

@TargetClass(className = "org.graalvm.compiler.hotspot.HotSpotTTYStreamProvider", onlyWith = LibGraalFeature.IsEnabled.class)
final class Target_org_graalvm_compiler_hotspot_HotSpotTTYStreamProvider {

    @Substitute
    private static Pointer getBarrierPointer() {
        return LibGraalEntryPoints.LOG_FILE_BARRIER.get();
    }
}

@TargetClass(className = "org.graalvm.compiler.serviceprovider.GraalServices", onlyWith = LibGraalFeature.IsEnabled.class)
final class Target_org_graalvm_compiler_serviceprovider_GraalServices {

    @Substitute
    public static long getGlobalTimeStamp() {
        Pointer timestamp = LibGraalEntryPoints.GLOBAL_TIMESTAMP.get();
        if (timestamp.readLong(0) == 0) {
            timestamp.compareAndSwapLong(0, 0, System.currentTimeMillis(), LocationIdentity.ANY_LOCATION);
        }
        return timestamp.readLong(0);
    }

    @Substitute
    private static void notifyLowMemoryPoint(boolean fullGC) {
        Heap.getHeap().getGC().maybeCauseUserRequestedCollection(GCCause.HintedGC, fullGC);
    }
}

@TargetClass(className = "org.graalvm.compiler.hotspot.HotSpotGraalOptionValues", onlyWith = LibGraalFeature.IsEnabled.class)
final class Target_org_graalvm_compiler_hotspot_HotSpotGraalOptionValues {
    @Substitute
    private static OptionValues initializeOptions() {
        return HotSpotGraalOptionValuesUtil.initializeOptions();
    }
}

final class HotSpotGraalOptionValuesUtil {
    private static final String LIBGRAAL_PREFIX = "libgraal.";
    private static final String LIBGRAAL_XOPTION_PREFIX = "libgraal.X";

    static OptionValues initializeOptions() {
        // Parse "graal." options.
        RuntimeOptionValues options = RuntimeOptionValues.singleton();
        options.update(HotSpotGraalOptionValues.parseOptions());

        // Parse "libgraal." options. This include the XOptions as well
        // as normal Graal options that are specified with the "libgraal."
        // prefix so as to be parsed only in libgraal and not by JavaGraal.
        // A motivating use case for this is CompileTheWorld + libgraal
        // where one may want to see GC stats with the VerboseGC option.
        // Since CompileTheWorld also initializes JavaGraal, specifying this
        // option with -Dgraal.VerboseGC would cause the VM to exit with an
        // unknown option error. Specifying it as -Dlibgraal.VerboseGC=true
        // avoids the error and provides the desired behavior.
        Map<String, String> savedProps = jdk.vm.ci.services.Services.getSavedProperties();
        EconomicMap<String, String> optionSettings = EconomicMap.create();
        for (Map.Entry<String, String> e : savedProps.entrySet()) {
            String name = e.getKey();
            if (name.startsWith(LIBGRAAL_PREFIX)) {
                if (name.startsWith(LIBGRAAL_XOPTION_PREFIX)) {
                    String xarg = removePrefix(name, LIBGRAAL_XOPTION_PREFIX) + e.getValue();
                    if (XOptions.setOption(xarg)) {
                        continue;
                    }
                }

                String value = e.getValue();
                optionSettings.put(removePrefix(name, LIBGRAAL_PREFIX), value);
            }
        }
        if (!optionSettings.isEmpty()) {
            EconomicMap<OptionKey<?>, Object> values = OptionValues.newOptionMap();
            Iterable<OptionDescriptors> loader = OptionsParser.getOptionsLoader();
            OptionsParser.parseOptions(optionSettings, values, loader);
            options.update(values);
        }
        return options;
    }

    private static String removePrefix(String value, String prefix) {
        assert value.startsWith(prefix);
        return value.substring(prefix.length());
    }
}

/**
 * This field resetting must be done via substitutions instead of {@link NativeImageReinitialize} as
 * the fields must only be reset in a libgraal image.
 */
@TargetClass(className = "org.graalvm.compiler.truffle.common.TruffleCompilerRuntimeInstance", onlyWith = LibGraalFeature.IsEnabled.class)
final class Target_org_graalvm_compiler_truffle_common_TruffleCompilerRuntimeInstance {
    // Checkstyle: stop
    @Alias @RecomputeFieldValue(kind = Kind.Reset, isFinal = true) static Object TRUFFLE_RUNTIME;
    // Checkstyle: resume
    @Alias @RecomputeFieldValue(kind = Kind.Reset) static TruffleCompilerRuntime truffleCompilerRuntime;
}

@TargetClass(className = "org.graalvm.compiler.core.GraalServiceThread", onlyWith = LibGraalFeature.IsEnabled.class)
final class Target_org_graalvm_compiler_core_GraalServiceThread {
    @Substitute()
    void beforeRun() {
        GraalServiceThread thread = SubstrateUtil.cast(this, GraalServiceThread.class);
        if (!LibGraal.attachCurrentThread(thread.isDaemon(), null)) {
            throw new InternalError("Couldn't attach to HotSpot runtime");
        }
    }

    @Substitute
    @SuppressWarnings("static-method")
    void afterRun() {
        LibGraal.detachCurrentThread(false);
    }
}

@TargetClass(className = "org.graalvm.compiler.core.GraalCompiler", onlyWith = LibGraalFeature.IsEnabled.class)
final class Target_org_graalvm_compiler_core_GraalCompiler {
    @SuppressWarnings("unused")
    @Substitute()
    private static void notifyCrash(String crashMessage) {
        if (LibGraalOptions.CrashAtIsFatal.getValue()) {
            LogHandler handler = ImageSingletons.lookup(LogHandler.class);
            if (handler instanceof FunctionPointerLogHandler) {
                VMError.shouldNotReachHere(crashMessage);
            }
            // If changing this message, update the test for it in mx_vm_gate.py
            System.out.println("CrashAtIsFatal: no fatalError function pointer installed");
        }
    }
}

@TargetClass(className = "org.graalvm.compiler.hotspot.SymbolicSnippetEncoder", onlyWith = LibGraalFeature.IsEnabled.class)
@Delete("shouldn't appear in libgraal")
final class Target_org_graalvm_compiler_hotspot_SymbolicSnippetEncoder {
}

@TargetClass(className = "org.graalvm.compiler.truffle.compiler.hotspot.libgraal.TruffleToLibGraalEntryPoints", onlyWith = LibGraalFeature.IsEnabled.class)
final class Target_org_graalvm_compiler_truffle_compiler_hotspot_libgraal_TruffleToLibGraalEntryPoints {
    @SuppressWarnings("unused")
    @Substitute
    private static void doReferenceHandling() {
        Heap.getHeap().doReferenceHandling();
    }
}

@TargetClass(value = HotSpotForeignCallLinkageImpl.class, onlyWith = LibGraalFeature.IsEnabled.class)
final class Target_org_graalvm_compiler_hotspot_HotSpotForeignCallLinkageImpl {
    /**
     * Gets the code info for a runtime stub, consulting and updating
     * {@link LibGraalEntryPoints#STUBS} in the process to share runtime stub code info between
     * libgraal isolates.
     */
    @SuppressWarnings("unused")
    @Substitute
    private static CodeInfo getCodeInfo(Stub stub, Backend backend) {
        ForeignCallSignature sig = stub.getLinkage().getDescriptor().getSignature();
        CGlobalData<Pointer> data = LibGraalEntryPoints.STUBS.get(sig);
        GraalError.guarantee(data != null, "missing global data for %s", sig);
        Pointer rsiPointer = data.get();
        RuntimeStubInfo rsi = rsiPointer.readWord(0);
        if (rsi.isNull()) {
            rsi = newRuntimeStubInfo(stub, backend);
            rsiPointer.writeWord(0, rsi);
        }
        return newCodeInfo(rsi, backend);
    }
}
