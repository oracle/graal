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

import static com.oracle.svm.graal.hotspot.libgraal.HotSpotGraalOptionValuesUtil.compilerOptionDescriptors;
import static com.oracle.svm.graal.hotspot.libgraal.HotSpotGraalOptionValuesUtil.vmOptionDescriptors;
import static com.oracle.svm.graal.hotspot.libgraal.LibGraalEntryPoints.RuntimeStubInfo.Util.newCodeInfo;
import static com.oracle.svm.graal.hotspot.libgraal.LibGraalEntryPoints.RuntimeStubInfo.Util.newRuntimeStubInfo;
import static jdk.vm.ci.hotspot.HotSpotJVMCIRuntime.runtime;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;
import java.util.stream.Collectors;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.MapCursor;
import org.graalvm.jniutils.JNI;
import org.graalvm.jniutils.JNIExceptionWrapper;
import org.graalvm.jniutils.JNIMethodScope;
import org.graalvm.jniutils.JNIUtil;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.LogHandler;
import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.VMRuntime;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.hosted.RuntimeJNIAccess;
import org.graalvm.nativeimage.hosted.RuntimeReflection;
import org.graalvm.word.Pointer;
import org.graalvm.word.WordFactory;

import com.oracle.graal.pointsto.BigBang;
import com.oracle.graal.pointsto.ObjectScanner;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisUniverse;
import com.oracle.graal.pointsto.meta.InvokeInfo;
import com.oracle.graal.pointsto.meta.ObjectReachableCallback;
import com.oracle.svm.core.OS;
import com.oracle.svm.core.RuntimeAssertionsSupport;
import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.Delete;
import com.oracle.svm.core.annotate.InjectAccessors;
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
import com.oracle.svm.core.heap.UnknownObjectField;
import com.oracle.svm.core.log.FunctionPointerLogHandler;
import com.oracle.svm.core.option.RuntimeOptionKey;
import com.oracle.svm.core.option.RuntimeOptionValues;
import com.oracle.svm.core.option.XOptions;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.core.util.UserError.UserException;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.graal.hosted.GraalCompilerFeature;
import com.oracle.svm.graal.hotspot.libgraal.LibGraalEntryPoints.RuntimeStubInfo;
import com.oracle.svm.hosted.FeatureImpl;
import com.oracle.svm.hosted.FeatureImpl.DuringSetupAccessImpl;
import com.oracle.svm.hosted.ImageClassLoader;
import com.oracle.svm.hosted.jni.JNIFeature;
import com.oracle.svm.hosted.reflect.ReflectionFeature;
import com.oracle.svm.util.LogUtils;
import com.oracle.svm.util.ModuleSupport;
import com.oracle.svm.util.ReflectionUtil;

import jdk.graal.compiler.code.DisassemblerProvider;
import jdk.graal.compiler.core.GraalServiceThread;
import jdk.graal.compiler.core.common.spi.ForeignCallSignature;
import jdk.graal.compiler.core.target.Backend;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.hotspot.EncodedSnippets;
import jdk.graal.compiler.hotspot.HotSpotBackend;
import jdk.graal.compiler.hotspot.HotSpotCodeCacheListener;
import jdk.graal.compiler.hotspot.HotSpotForeignCallLinkageImpl;
import jdk.graal.compiler.hotspot.HotSpotForeignCallLinkageImpl.CodeInfo;
import jdk.graal.compiler.hotspot.HotSpotGraalCompiler;
import jdk.graal.compiler.hotspot.HotSpotGraalRuntime;
import jdk.graal.compiler.hotspot.HotSpotReplacementsImpl;
import jdk.graal.compiler.hotspot.SnippetObjectConstant;
import jdk.graal.compiler.hotspot.meta.HotSpotHostForeignCallsProvider;
import jdk.graal.compiler.hotspot.meta.HotSpotInvocationPluginProvider;
import jdk.graal.compiler.hotspot.meta.HotSpotProviders;
import jdk.graal.compiler.hotspot.stubs.Stub;
import jdk.graal.compiler.nodes.graphbuilderconf.GeneratedPluginFactory;
import jdk.graal.compiler.nodes.spi.SnippetParameterInfo;
import jdk.graal.compiler.options.Option;
import jdk.graal.compiler.options.OptionDescriptor;
import jdk.graal.compiler.options.OptionDescriptors;
import jdk.graal.compiler.options.OptionDescriptorsMap;
import jdk.graal.compiler.options.OptionGroup;
import jdk.graal.compiler.options.OptionKey;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.options.OptionsParser;
import jdk.graal.compiler.phases.util.Providers;
import jdk.graal.compiler.serviceprovider.GlobalAtomicLong;
import jdk.graal.compiler.serviceprovider.GraalServices;
import jdk.graal.compiler.serviceprovider.SpeculationReasonGroup;
import jdk.graal.compiler.truffle.PartialEvaluatorConfiguration;
import jdk.graal.compiler.truffle.host.TruffleHostEnvironment;
import jdk.graal.compiler.truffle.hotspot.HotSpotTruffleCompilerImpl;
import jdk.graal.compiler.truffle.hotspot.TruffleCallBoundaryInstrumentationFactory;
import jdk.graal.compiler.truffle.substitutions.GraphBuilderInvocationPluginProvider;
import jdk.graal.compiler.truffle.substitutions.GraphDecoderInvocationPluginProvider;
import jdk.internal.misc.Unsafe;
import jdk.vm.ci.code.CompilationRequest;
import jdk.vm.ci.code.CompilationRequestResult;
import jdk.vm.ci.hotspot.HotSpotConstantReflectionProvider;
import jdk.vm.ci.hotspot.HotSpotJVMCIBackendFactory;
import jdk.vm.ci.hotspot.HotSpotJVMCIRuntime;
import jdk.vm.ci.hotspot.HotSpotSignature;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.services.Services;

class LibGraalOptions {
    @Option(help = "If non-zero, converts an exception triggered by the CrashAt option into a fatal error " +
                    "if a non-null pointer was passed in the _fatal option to JNI_CreateJavaVM. " +
                    "The value of this option is the number of milliseconds to sleep before calling _fatal. " +
                    "This option exists for the purpose of testing fatal error handling in libgraal.") //
    static final RuntimeOptionKey<Integer> CrashAtIsFatal = new LibGraalRuntimeOptionKey<>(0);
    @Option(help = "The fully qualified name of a no-arg, void, static method to be invoked " +
                    "in HotSpot from libgraal when the libgraal isolate is being shutdown." +
                    "This option exists for the purpose of testing callbacks in this context.") //
    static final RuntimeOptionKey<String> OnShutdownCallback = new LibGraalRuntimeOptionKey<>(null);
    @Option(help = "Replaces first exception thrown by the CrashAt option with an OutOfMemoryError. " +
                    "Subsequently CrashAt exceptions are suppressed. " +
                    "This option exists to test HeapDumpOnOutOfMemoryError. " +
                    "See the MethodFilter option for the pattern syntax.") //
    static final RuntimeOptionKey<Boolean> CrashAtThrowsOOME = new LibGraalRuntimeOptionKey<>(false);
}

public class LibGraalFeature implements InternalFeature {

    private final OptionCollector optionCollector = new OptionCollector();
    private HotSpotReplacementsImpl hotSpotSubstrateReplacements;

    public LibGraalFeature() {
        /* Open up all modules needed to build LibGraal image */
        ModuleSupport.accessPackagesToClass(ModuleSupport.Access.EXPORT, LibGraalFeature.class, false, "java.base", "jdk.internal.misc");
        ModuleSupport.accessPackagesToClass(ModuleSupport.Access.EXPORT, LibGraalFeature.class, false, "jdk.internal.vm.ci");
        ModuleSupport.accessPackagesToClass(ModuleSupport.Access.EXPORT, LibGraalFeature.class, false, "jdk.graal.compiler");
        ModuleSupport.accessPackagesToClass(ModuleSupport.Access.EXPORT, LibGraalFeature.class, false, "jdk.graal.compiler.management");
        ModuleSupport.accessPackagesToClass(ModuleSupport.Access.EXPORT, LibGraalFeature.class, false, "org.graalvm.collections");
        ModuleSupport.accessPackagesToClass(ModuleSupport.Access.EXPORT, LibGraalFeature.class, false, "org.graalvm.word");
        ModuleSupport.accessPackagesToClass(ModuleSupport.Access.EXPORT, LibGraalFeature.class, false, "org.graalvm.nativeimage", "org.graalvm.nativeimage.impl");
        ModuleSupport.accessPackagesToClass(ModuleSupport.Access.EXPORT, LibGraalFeature.class, false, "org.graalvm.nativeimage.base");
        ModuleSupport.accessPackagesToClass(ModuleSupport.Access.EXPORT, LibGraalFeature.class, false, "org.graalvm.nativeimage.builder");
        ModuleSupport.accessPackagesToClass(ModuleSupport.Access.EXPORT, LibGraalFeature.class, true, "org.graalvm.nativeimage.llvm");
    }

    @Override
    public void afterImageWrite(AfterImageWriteAccess access) {
    }

    @Override
    public boolean isInConfiguration(IsInConfigurationAccess access) {
        return true;
    }

    @Override
    public List<Class<? extends Feature>> getRequiredFeatures() {
        return List.of(JNIFeature.class, GraalCompilerFeature.class, ReflectionFeature.class);
    }

    public static final class IsEnabled implements BooleanSupplier {
        @Override
        public boolean getAsBoolean() {
            return ImageSingletons.contains(LibGraalFeature.class);
        }
    }

    @Override
    public void duringSetup(DuringSetupAccess a) {
        DuringSetupAccessImpl access = (DuringSetupAccessImpl) a;
        access.registerObjectReachableCallback(OptionKey.class, optionCollector::doCallback);

        ImageClassLoader imageClassLoader = access.getImageClassLoader();
        registerJNIConfiguration(imageClassLoader);
    }

    /** Collects all {@link OptionKey}s that are reachable at run time. */
    private static class OptionCollector implements ObjectReachableCallback<OptionKey<?>> {
        final ConcurrentHashMap<OptionKey<?>, OptionKey<?>> options = new ConcurrentHashMap<>();
        private boolean sealed;

        @Override
        public void doCallback(DuringAnalysisAccess access, OptionKey<?> option, ObjectScanner.ScanReason reason) {
            if (sealed) {
                GraalError.guarantee(options.contains(option), "All options must have been discovered during static analysis");
            } else {
                options.put(option, option);
            }
        }

        public void setSealed() {
            sealed = true;
        }
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
                    LogUtils.warning("Could not delete %s: %s", configFilePath, e);
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
        GraalServices.load(TruffleHostEnvironment.Lookup.class);

        List<HotSpotBackend> truffleBackends;
        try (DebugContext.Scope scope = debug.scope("SnippetSupportEncode")) {
            // Instantiate the truffle compiler to ensure the backends it uses are initialized.
            truffleBackends = HotSpotTruffleCompilerImpl.ensureBackendsInitialized(RuntimeOptionValues.singleton());
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
        registerForeignCalls(providers);

        for (Backend backend : truffleBackends) {
            registerForeignCalls((HotSpotProviders) backend.getProviders());
        }

        hotSpotSubstrateReplacements.encode(bb.getOptions());
        if (!RuntimeAssertionsSupport.singleton().desiredAssertionStatus(SnippetParameterInfo.class)) {
            // Clear the saved names if assertions aren't enabled
            hotSpotSubstrateReplacements.clearSnippetParameterNames();
        }
        // Mark all the Node classes as allocated so they are available during graph decoding.
        EncodedSnippets encodedSnippets = HotSpotReplacementsImpl.getEncodedSnippets();
        for (NodeClass<?> nodeClass : encodedSnippets.getSnippetNodeClasses()) {
            impl.getMetaAccess().lookupJavaType(nodeClass.getClazz()).registerAsInstantiated("All " + NodeClass.class.getName() + " classes are marked as instantiated eagerly.");
        }
    }

    private static void registerForeignCalls(HotSpotProviders providers) {
        HotSpotHostForeignCallsProvider foreignCalls = providers.getForeignCalls();
        foreignCalls.forEachForeignCall((sig, linkage) -> {
            if (linkage == null || linkage.isCompiledStub()) {
                boolean nonConstant = true;
                String symbol = null;

                /*
                 * We process all foreign calls of all backends including Truffle backends. Some
                 * stubs may be encountered multiple times with multiple backends. It is enough to
                 * do this once per stub signature.
                 */
                if (!LibGraalEntryPoints.STUBS.containsKey(sig)) {
                    CGlobalData<Pointer> data = CGlobalDataFactory.createWord((Pointer) WordFactory.zero(), symbol, nonConstant);
                    LibGraalEntryPoints.STUBS.put(sig, data);
                    if (linkage != null) {
                        // Force stub construction
                        foreignCalls.lookupForeignCall(sig);
                    }
                }
            }
        });
    }

    private static void filterArchitectureServices(String archPackage, Map<Class<?>, List<?>> services) {
        for (List<?> list : services.values()) {
            list.removeIf(o -> {
                String name = o.getClass().getName();
                if (name.contains(".aarch64.") || name.contains(".amd64.") || name.contains(".riscv64.")) {
                    return !name.contains(archPackage);
                }
                return false;
            });
        }
    }

    @Override
    public void afterAnalysis(AfterAnalysisAccess access) {
        optionCollector.setSealed();

        verifyReachableTruffleClasses(access);
        registerReachableOptions(access);
    }

    private void registerReachableOptions(AfterAnalysisAccess access) {
        compilerOptionDescriptors = EconomicMap.create();
        vmOptionDescriptors = EconomicMap.create();
        for (OptionKey<?> option : optionCollector.options.keySet()) {
            VMError.guarantee(access.isReachable(option.getClass()));
            OptionDescriptor descriptor = option.getDescriptor();
            OptionGroup group = descriptor.getDeclaringClass().getAnnotation(OptionGroup.class);
            if (group != null && !group.registerAsService()) {
                // Ignore options (such as TruffleCompilerOptions) that should not
                // be service loaded.
                continue;
            }
            if (!isNativeImageOption(option)) {
                compilerOptionDescriptors.put(option.getName(), option.getDescriptor());
            } else {
                vmOptionDescriptors.put(option.getName(), option.getDescriptor());
            }
        }

    }

    /**
     * Verifies that the Truffle compiler does not bring Truffle API types into an image. We need to
     * use the points to analysis to verify that the Truffle API types are not reachable.
     */
    private static void verifyReachableTruffleClasses(AfterAnalysisAccess access) {
        AnalysisUniverse universe = ((FeatureImpl.AfterAnalysisAccessImpl) access).getUniverse();
        Map<AnalysisMethod, Object> seen = new LinkedHashMap<>();
        for (AnalysisMethod analysisMethod : universe.getMethods()) {
            if (analysisMethod.isDirectRootMethod() && analysisMethod.isSimplyImplementationInvoked()) {
                seen.put(analysisMethod, "direct root");
            }
            if (analysisMethod.isVirtualRootMethod()) {
                for (AnalysisMethod impl : analysisMethod.collectMethodImplementations(false)) {
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
                for (AnalysisMethod callee : invoke.getOriginalCallees()) {
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
            return className.startsWith("com.oracle.truffle.compiler.");
        }
        return true;
    }

    static HotSpotReplacementsImpl getReplacements() {
        HotSpotGraalCompiler compiler = (HotSpotGraalCompiler) HotSpotJVMCIRuntime.runtime().getCompiler();
        HotSpotProviders originalProvider = compiler.getGraalRuntime().getHostProviders();
        return (HotSpotReplacementsImpl) originalProvider.getReplacements();
    }

    private static boolean isNativeImageOption(OptionKey<?> key) {
        return key instanceof RuntimeOptionKey && !(key instanceof LibGraalRuntimeOptionKey);
    }
}

@TargetClass(className = "jdk.graal.compiler.options.OptionsParser", onlyWith = LibGraalFeature.IsEnabled.class)
final class Target_jdk_graal_compiler_options_OptionsParser {
    @Alias @InjectAccessors(OptionsParserAccessors.class) //
    private static volatile List<OptionDescriptors> cachedOptionDescriptors;
}

class OptionsParserAccessors {
    private static List<OptionDescriptors> cachedOptions;

    @SuppressWarnings("unused")
    static List<OptionDescriptors> getCachedOptionDescriptors() {
        List<OptionDescriptors> result = cachedOptions;
        if (result == null) {
            result = initialize();
        }
        return result;
    }

    @SuppressWarnings("unused")
    static void setCachedOptionDescriptors(List<OptionDescriptors> value) {
        throw VMError.shouldNotReachHereAtRuntime();
    }

    private static synchronized List<OptionDescriptors> initialize() {
        List<OptionDescriptors> result = cachedOptions;
        if (result == null) {
            result = Collections.singletonList(new OptionDescriptorsMap(compilerOptionDescriptors));

            /* Ensure that all stores are visible before we publish the data. */
            Unsafe.getUnsafe().storeFence();
            cachedOptions = result;
        }
        return result;
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
final class Target_jdk_graal_compiler_serviceprovider_SpeculationReasonGroup {

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

@TargetClass(className = "jdk.graal.compiler.hotspot.HotSpotGraalCompiler", onlyWith = LibGraalFeature.IsEnabled.class)
final class Target_jdk_graal_compiler_hotspot_HotSpotGraalCompiler {

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
            LibGraalEntryPoints.doReferenceHandling();
        }
    }
}

@TargetClass(className = "jdk.graal.compiler.hotspot.HotSpotGraalRuntime", onlyWith = LibGraalFeature.IsEnabled.class)
final class Target_jdk_graal_compiler_hotspot_HotSpotGraalRuntime {

    @SuppressWarnings("unused")
    @Substitute
    private static void startupLibGraal(HotSpotGraalRuntime runtime) {
        VMRuntime.initialize();
    }

    @SuppressWarnings("unused")
    @Substitute
    private static void shutdownLibGraal(HotSpotGraalRuntime runtime) {
        try {
            String callback = LibGraalOptions.OnShutdownCallback.getValue();
            if (callback != null) {
                long offset = runtime.getVMConfig().jniEnvironmentOffset;
                long javaThreadAddr = HotSpotJVMCIRuntime.runtime().getCurrentJavaThread();
                JNI.JNIEnv env = (JNI.JNIEnv) WordFactory.unsigned(javaThreadAddr).add(WordFactory.unsigned(offset));
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
        } finally {
            VMRuntime.shutdown();
        }
    }
}

@TargetClass(className = "jdk.graal.compiler.serviceprovider.GraalServices", onlyWith = LibGraalFeature.IsEnabled.class)
final class Target_jdk_graal_compiler_serviceprovider_GraalServices {

    @Substitute
    private static void notifyLowMemoryPoint(boolean hintFullGC, boolean forceFullGC) {
        if (forceFullGC) {
            Heap.getHeap().getGC().collectCompletely(GCCause.JavaLangSystemGC);
        } else {
            Heap.getHeap().getGC().collectionHint(hintFullGC);
        }
        LibGraalEntryPoints.doReferenceHandling();
    }
}

@TargetClass(className = "jdk.graal.compiler.hotspot.HotSpotGraalOptionValues", onlyWith = LibGraalFeature.IsEnabled.class)
final class Target_jdk_graal_compiler_hotspot_HotSpotGraalOptionValues {
    @Substitute
    private static void notifyLibgraalOptions(EconomicMap<OptionKey<?>, Object> compilerOptionValues, EconomicMap<String, String> vmOptionSettings) {
        HotSpotGraalOptionValuesUtil.initializeOptions(compilerOptionValues, vmOptionSettings);
    }

    @Substitute
    private static void printLibgraalProperties(PrintStream out, String prefix) {
        RuntimeOptionValues vmOptions = RuntimeOptionValues.singleton();
        Iterable<OptionDescriptors> vmOptionLoader = Collections.singletonList(new OptionDescriptorsMap(vmOptionDescriptors));
        vmOptions.printHelp(vmOptionLoader, out, prefix, true);
    }
}

final class HotSpotGraalOptionValuesUtil {
    // Support for CrashAtThrowsOOME
    static final GlobalAtomicLong OOME_CRASH_DONE = new GlobalAtomicLong(0);

    /**
     * Options configuring the Graal compiler.
     */
    @UnknownObjectField(fullyQualifiedTypes = "org.graalvm.collections.EconomicMapImpl") //
    static EconomicMap<String, OptionDescriptor> compilerOptionDescriptors;

    /**
     * Options configuring the VM in which libgraal is running.
     */
    @UnknownObjectField(fullyQualifiedTypes = "org.graalvm.collections.EconomicMapImpl") //
    static EconomicMap<String, OptionDescriptor> vmOptionDescriptors;

    static void initializeOptions(EconomicMap<OptionKey<?>, Object> compilerOptionValues, EconomicMap<String, String> vmOptionSettings) {

        RuntimeOptionValues vmOptions = RuntimeOptionValues.singleton();
        vmOptions.update(compilerOptionValues);

        if (LibGraalOptions.CrashAtThrowsOOME.getValue() && LibGraalOptions.CrashAtIsFatal.getValue() != 0) {
            throw new IllegalArgumentException("CrashAtThrowsOOME and CrashAtIsFatal cannot both be enabled");
        }

        if (!vmOptionSettings.isEmpty()) {
            MapCursor<String, String> entries = vmOptionSettings.getEntries();
            while (entries.advance()) {
                String key = entries.getKey();
                String value = entries.getValue();
                if (key.startsWith("X") && value.isEmpty()) {
                    String xarg = key.substring(1);
                    if (XOptions.setOption(xarg)) {
                        entries.remove();
                    }
                }
            }
            EconomicMap<OptionKey<?>, Object> vmOptionValues = OptionValues.newOptionMap();
            Iterable<OptionDescriptors> vmOptionLoader = Collections.singletonList(new OptionDescriptorsMap(vmOptionDescriptors));
            OptionsParser.parseOptions(vmOptionSettings, vmOptionValues, vmOptionLoader);
            vmOptions.update(vmOptionValues);
        }
    }
}

@TargetClass(className = "jdk.graal.compiler.core.GraalServiceThread", onlyWith = LibGraalFeature.IsEnabled.class)
final class Target_jdk_graal_compiler_core_GraalServiceThread {
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

@TargetClass(className = "jdk.graal.compiler.core.GraalCompiler", onlyWith = LibGraalFeature.IsEnabled.class)
final class Target_jdk_graal_compiler_core_GraalCompiler {
    @SuppressWarnings("unused")
    @Substitute()
    private static boolean notifyCrash(String crashMessage) {
        if (LibGraalOptions.CrashAtThrowsOOME.getValue()) {
            if (HotSpotGraalOptionValuesUtil.OOME_CRASH_DONE.compareAndSet(0L, 1L)) {
                // The -Djdk.graal.internal.Xmx option should also be employed to make
                // this allocation fail quicky
                String largeString = Arrays.toString(new int[Integer.MAX_VALUE - 1]);
                throw new InternalError("Failed to trigger OOME: largeString.length=" + largeString.length());
            } else {
                // Remaining compilations should proceed so that test finishes quickly.
                return false;
            }
        } else if (LibGraalOptions.CrashAtIsFatal.getValue() != 0) {
            LogHandler handler = ImageSingletons.lookup(LogHandler.class);
            if (handler instanceof FunctionPointerLogHandler) {
                try {
                    Thread.sleep(LibGraalOptions.CrashAtIsFatal.getValue());
                } catch (InterruptedException e) {
                    // ignore
                }
                VMError.shouldNotReachHere(crashMessage);
            }
            // If changing this message, update the test for it in mx_vm_gate.py
            System.out.println("CrashAtIsFatal: no fatalError function pointer installed");
        }
        return true;
    }
}

@TargetClass(className = "jdk.graal.compiler.hotspot.SymbolicSnippetEncoder", onlyWith = LibGraalFeature.IsEnabled.class)
@Delete("shouldn't appear in libgraal")
final class Target_jdk_graal_compiler_hotspot_SymbolicSnippetEncoder {
}

@TargetClass(value = HotSpotForeignCallLinkageImpl.class, onlyWith = LibGraalFeature.IsEnabled.class)
final class Target_jdk_graal_compiler_hotspot_HotSpotForeignCallLinkageImpl {
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
