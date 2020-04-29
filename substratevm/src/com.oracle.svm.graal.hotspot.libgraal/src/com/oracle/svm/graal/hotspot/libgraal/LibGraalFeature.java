/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.vm.ci.hotspot.HotSpotJVMCIRuntime.runtime;
import static org.graalvm.compiler.serviceprovider.JavaVersionUtil.JAVA_SPEC;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.MapCursor;
import org.graalvm.compiler.api.replacements.SnippetReflectionProvider;
import org.graalvm.compiler.core.GraalServiceThread;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.debug.DebugHandlersFactory;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.hotspot.EncodedSnippets;
import org.graalvm.compiler.hotspot.HotSpotCodeCacheListener;
import org.graalvm.compiler.hotspot.HotSpotGraalCompiler;
import org.graalvm.compiler.hotspot.HotSpotGraalManagementRegistration;
import org.graalvm.compiler.hotspot.HotSpotGraalOptionValues;
import org.graalvm.compiler.hotspot.HotSpotReplacementsImpl;
import org.graalvm.compiler.hotspot.meta.HotSpotProviders;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins;
import org.graalvm.compiler.nodes.graphbuilderconf.MethodSubstitutionPlugin;
import org.graalvm.compiler.nodes.spi.SnippetParameterInfo;
import org.graalvm.compiler.options.OptionDescriptor;
import org.graalvm.compiler.options.OptionDescriptors;
import org.graalvm.compiler.options.OptionDescriptorsMap;
import org.graalvm.compiler.options.OptionKey;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.options.OptionsParser;
import org.graalvm.compiler.phases.common.jmx.HotSpotMBeanOperationProvider;
import org.graalvm.compiler.phases.util.Providers;
import org.graalvm.compiler.serviceprovider.GraalServices;
import org.graalvm.compiler.truffle.common.TruffleCompilerRuntime;
import org.graalvm.compiler.truffle.compiler.TruffleCompilerBase;
import org.graalvm.compiler.truffle.compiler.hotspot.TruffleCallBoundaryInstrumentationFactory;
import org.graalvm.compiler.truffle.compiler.substitutions.TruffleInvocationPluginProvider;
import org.graalvm.compiler.truffle.runtime.GraalTruffleRuntime;
import org.graalvm.libgraal.LibGraal;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.VMRuntime;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.hosted.RuntimeReflection;
import org.graalvm.word.Pointer;

import com.oracle.graal.pointsto.flow.InvokeTypeFlow;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisUniverse;
import com.oracle.svm.core.OS;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.Delete;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.RecomputeFieldValue.Kind;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.graal.meta.RuntimeConfiguration;
import com.oracle.svm.core.graal.snippets.NodeLoweringProvider;
import com.oracle.svm.core.jni.JNIRuntimeAccess;
import com.oracle.svm.core.option.HostedOptionKey;
import com.oracle.svm.core.option.RuntimeOptionValues;
import com.oracle.svm.core.option.XOptions;
import com.oracle.svm.core.snippets.KnownIntrinsics;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.core.util.UserError.UserException;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.graal.hosted.GraalFeature;
import com.oracle.svm.hosted.FeatureImpl;
import com.oracle.svm.hosted.FeatureImpl.DuringSetupAccessImpl;
import com.oracle.svm.hosted.ImageClassLoader;
import com.oracle.svm.jni.hosted.JNIFeature;
import com.oracle.svm.reflect.hosted.ReflectionFeature;
import com.oracle.svm.util.ReflectionUtil;

import jdk.vm.ci.common.NativeImageReinitialize;
import jdk.vm.ci.hotspot.HotSpotJVMCIBackendFactory;
import jdk.vm.ci.hotspot.HotSpotJVMCIRuntime;
import jdk.vm.ci.hotspot.HotSpotSignature;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.services.Services;

public final class LibGraalFeature implements com.oracle.svm.core.graal.GraalFeature {

    private HotSpotReplacementsImpl hotSpotSubstrateReplacements;

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
        return Arrays.asList(JNIFeature.class, GraalFeature.class, ReflectionFeature.class);
    }

    public static final class IsEnabled implements BooleanSupplier {
        @Override
        public boolean getAsBoolean() {
            return ImageSingletons.contains(LibGraalFeature.class);
        }
    }

    @Override
    public void duringSetup(DuringSetupAccess access) {
        JNIRuntimeAccess.JNIRuntimeAccessibilitySupport registry = ImageSingletons.lookup(JNIRuntimeAccess.JNIRuntimeAccessibilitySupport.class);
        ImageClassLoader imageClassLoader = ((DuringSetupAccessImpl) access).getImageClassLoader();
        registerJNIConfiguration(registry, imageClassLoader);

        EconomicMap<String, OptionDescriptor> descriptors = EconomicMap.create();
        for (Class<? extends OptionDescriptors> optionsClass : imageClassLoader.findSubclasses(OptionDescriptors.class, false)) {
            if (!Modifier.isAbstract(optionsClass.getModifiers()) && !OptionDescriptorsMap.class.isAssignableFrom(optionsClass)) {
                try {
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
                throw UserError.abort("Java launcher " + javaExe + " does not exist or is not executable");
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
                throw UserError.abort(String.format("Could not run command: %s%n%s", quotedCommand, e));
            }

            String nl = System.getProperty("line.separator");
            String out = new BufferedReader(new InputStreamReader(p.getInputStream()))
                            .lines().collect(Collectors.joining(nl));

            int exitValue;
            try {
                exitValue = p.waitFor();
            } catch (InterruptedException e) {
                throw UserError.abort(String.format("Interrupted waiting for command: %s%n%s", quotedCommand, out));
            }
            if (exitValue != 0) {
                throw UserError.abort(String.format("Command finished with exit value %d: %s%n%s", exitValue, quotedCommand, out));
            }
            try {
                lines = Files.readAllLines(configFilePath);
            } catch (IOException e) {
                configFilePath = null;
                throw UserError.abort(String.format("Reading JNI config in %s dumped by command: %s%n%s", configFilePath, quotedCommand, out));
            }
        }

        @Override
        public void close() {
            if (configFilePath != null && Files.exists(configFilePath)) {
                try {
                    Files.delete(configFilePath);
                    configFilePath = null;
                } catch (IOException e) {
                    System.out.printf("WARNING: Cound not delete %s: %s%n", configFilePath, e);
                }
            }
        }

        Class<?> findClass(String name) {
            Class<?> c = loader.findClassByName(name, false);
            if (c == null) {
                throw error("Class " + name + " not found");
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
            throw UserError.abort(String.format("Line %d of %s: %s%n%s%n%s generated by command: %s",
                            lineNo, path.toAbsolutePath(), errorMessage, errorLine, path, quotedCommand));

        }
    }

    private static void registerJNIConfiguration(JNIRuntimeAccess.JNIRuntimeAccessibilitySupport registry, ImageClassLoader loader) {
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
                    registry.register(clazz);
                    registry.register(Array.newInstance(clazz, 0).getClass());
                    classes.put(className, clazz);
                }

                switch (tokens[0]) {
                    case "field": {
                        source.check(tokens.length == 4, "Expected 4 tokens for a field");
                        String fieldName = tokens[2];
                        try {
                            registry.register(false, false, clazz.getDeclaredField(fieldName));
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
                                registry.register(cons);
                                if (Throwable.class.isAssignableFrom(clazz) && !Modifier.isAbstract(clazz.getModifiers())) {
                                    if (usedInTranslatedException(parameters)) {
                                        RuntimeReflection.register(clazz);
                                        RuntimeReflection.register(cons);
                                    }
                                }
                            } else {
                                registry.register(clazz.getDeclaredMethod(methodName, parameters));
                            }
                        } catch (NoSuchMethodException e) {
                            throw source.error("Method %s.%s%s not found: %e", clazz.getTypeName(), methodName, descriptor, e);
                        } catch (NoClassDefFoundError e) {
                            throw source.error("Could not register method %s.%s%s: %e", clazz.getTypeName(), methodName, descriptor, e);
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
    public void registerLowerings(RuntimeConfiguration runtimeConfig, OptionValues options, Iterable<DebugHandlersFactory> factories, Providers substrateProviders,
                    SnippetReflectionProvider substrateSnippetReflection,
                    Map<Class<? extends Node>, NodeLoweringProvider<?>> lowerings, boolean hosted) {
        hotSpotSubstrateReplacements = getReplacements();
    }

    private void registerMethodSubstitutions(DebugContext debug, InvocationPlugins invocationPlugins, MetaAccessProvider metaAccess) {
        MapCursor<String, List<InvocationPlugins.Binding>> cursor = invocationPlugins.getBindings(true).getEntries();
        while (cursor.advance()) {
            String className = cursor.getKey();
            for (InvocationPlugins.Binding binding : cursor.getValue()) {
                if (binding.plugin instanceof MethodSubstitutionPlugin) {
                    MethodSubstitutionPlugin plugin = (MethodSubstitutionPlugin) binding.plugin;

                    ResolvedJavaMethod original = plugin.getOriginalMethod(metaAccess);
                    if (original != null) {
                        ResolvedJavaMethod method = plugin.getSubstitute(metaAccess);
                        debug.log("Method substitution %s %s", method, original);

                        hotSpotSubstrateReplacements.checkRegistered(plugin);
                    } else {
                        throw new GraalError("Can't find original method for " + plugin + " with class " + className);
                    }
                }
            }
        }
    }

    @SuppressWarnings({"try", "unchecked"})
    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {

        // Services that will not be loaded if native-image is run
        // with -XX:-UseJVMCICompiler.
        GraalServices.load(TruffleCallBoundaryInstrumentationFactory.class);
        GraalServices.load(TruffleInvocationPluginProvider.class);
        GraalServices.load(HotSpotCodeCacheListener.class);
        GraalServices.load(HotSpotMBeanOperationProvider.class);

        FeatureImpl.BeforeAnalysisAccessImpl impl = (FeatureImpl.BeforeAnalysisAccessImpl) access;
        DebugContext debug = impl.getBigBang().getDebug();
        try (DebugContext.Scope scope = debug.scope("SnippetSupportEncode")) {
            InvocationPlugins compilerPlugins = hotSpotSubstrateReplacements.getGraphBuilderPlugins().getInvocationPlugins();
            MetaAccessProvider metaAccess = hotSpotSubstrateReplacements.getProviders().getMetaAccess();
            registerMethodSubstitutions(debug, compilerPlugins, metaAccess);

            // Also register Truffle plugins
            TruffleCompilerBase truffleCompiler = (TruffleCompilerBase) GraalTruffleRuntime.getRuntime().newTruffleCompiler();
            InvocationPlugins trufflePlugins = truffleCompiler.getPartialEvaluator().getConfigPrototype().getPlugins().getInvocationPlugins();
            registerMethodSubstitutions(debug, trufflePlugins, metaAccess);
        } catch (Throwable t) {
            throw debug.handle(t);
        }

        // Filter out any cached services which are for a different architecture
        try {
            HotSpotGraalCompiler compiler = (HotSpotGraalCompiler) HotSpotJVMCIRuntime.runtime().getCompiler();
            String osArch = compiler.getGraalRuntime().getVMConfig().osArch;
            String archPackage = "." + osArch + ".";

            final Field servicesCacheField = ReflectionUtil.lookupField(Services.class, "servicesCache");
            filterArchitectureServices(archPackage, (Map<Class<?>, List<?>>) servicesCacheField.get(null));

            if (JAVA_SPEC > 8) {
                final Field graalServicesCacheField = ReflectionUtil.lookupField(GraalServices.class, "servicesCache");
                filterArchitectureServices(archPackage, (Map<Class<?>, List<?>>) graalServicesCacheField.get(null));
            }

            Field cachedHotSpotJVMCIBackendFactoriesField = ReflectionUtil.lookupField(HotSpotJVMCIRuntime.class, "cachedHotSpotJVMCIBackendFactories");
            List<HotSpotJVMCIBackendFactory> cachedHotSpotJVMCIBackendFactories = (List<HotSpotJVMCIBackendFactory>) cachedHotSpotJVMCIBackendFactoriesField.get(null);
            cachedHotSpotJVMCIBackendFactories.removeIf(factory -> !factory.getArchitecture().equalsIgnoreCase(osArch));
        } catch (ReflectiveOperationException ex) {
            throw VMError.shouldNotReachHere(ex);
        }

        hotSpotSubstrateReplacements.encode(impl.getBigBang().getOptions());
        if (!SubstrateOptions.getRuntimeAssertionsForClass(SnippetParameterInfo.class.getName())) {
            // Clear that saved names if assertions aren't enabled
            hotSpotSubstrateReplacements.clearSnippetParameterNames();
        }
        // Mark all the Node classes as allocated so they are available during graph decoding.
        EncodedSnippets encodedSnippets = HotSpotReplacementsImpl.getEncodedSnippets(impl.getBigBang().getOptions());
        for (NodeClass<?> nodeClass : encodedSnippets.getSnippetNodeClasses()) {
            impl.getMetaAccess().lookupJavaType(nodeClass.getClazz()).registerAsAllocated(null);
        }
    }

    private static void filterArchitectureServices(String archPackage, Map<Class<?>, List<?>> services) {
        for (List<?> list : services.values()) {
            list.removeIf(o -> {
                String name = o.getClass().getName();
                if (name.contains(".aarch64.") || name.contains(".sparc.") || name.contains(".amd64.")) {
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
        FeatureImpl.AfterCompilationAccessImpl accessImpl = (FeatureImpl.AfterCompilationAccessImpl) access;
        EncodedSnippets encodedSnippets = HotSpotReplacementsImpl.getEncodedSnippets(accessImpl.getUniverse().getBigBang().getOptions());

        // These fields are all immutable but the snippet object table isn't since symbolic JVMCI
        // references are converted into real references during decoding.
        access.registerAsImmutable(encodedSnippets.getOriginalMethods());
        access.registerAsImmutable(encodedSnippets.getSnippetEncoding());
        access.registerAsImmutable(encodedSnippets.getSnippetNodeClasses());
        access.registerAsImmutable(encodedSnippets.getSnippetStartOffsets());
    }

    /**
     * Verifies that the Truffle compiler does not bring Truffle API types into an image. The
     * Truffle compiler depends on {@code org.graalvm.compiler.truffle.options} which depends on the
     * Truffle APIs to be able to use the {@code @com.oracle.truffle.api.Option} annotation. We need
     * to use the points to analysis to verify that the Truffle API types are not reachable.
     */
    private static void verifyReachableTruffleClasses(AfterAnalysisAccess access) {
        AnalysisUniverse universe = ((FeatureImpl.AfterAnalysisAccessImpl) access).getUniverse();
        Set<AnalysisMethod> seen = new HashSet<>();
        universe.getMethods().stream().filter(AnalysisMethod::isRootMethod).forEach(seen::add);
        Deque<AnalysisMethod> todo = new ArrayDeque<>(seen);
        SortedSet<String> disallowedTypes = new TreeSet<>();
        while (!todo.isEmpty()) {
            AnalysisMethod m = todo.removeFirst();
            String className = m.getDeclaringClass().toClassName();
            if (!isAllowedType(className)) {
                disallowedTypes.add(className);
            }
            for (InvokeTypeFlow invoke : m.getTypeFlow().getInvokes()) {
                for (AnalysisMethod callee : invoke.getCallees()) {
                    if (seen.add(callee)) {
                        todo.add(callee);
                    }
                }
            }
        }
        if (!disallowedTypes.isEmpty()) {
            throw UserError.abort("Following non allowed Truffle types are reachable on heap: " + String.join(", ", disallowedTypes));
        }
    }

    private static boolean isAllowedType(String className) {
        if (className.startsWith("com.oracle.truffle.")) {
            return className.startsWith("com.oracle.truffle.api.nodes.") || className.startsWith("com.oracle.truffle.compiler.enterprise.");
        }
        return true;
    }

    static final Annotation[][] NO_PARAMETER_ANNOTATIONS = new Annotation[0][];
    static final Annotation[] NO_ANNOTATIONS = new Annotation[0];

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
        return KnownIntrinsics.convertUnknownValue(object, Object.class);
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

@TargetClass(className = "org.graalvm.compiler.hotspot.HotSpotGraalRuntime", onlyWith = LibGraalFeature.IsEnabled.class)
final class Target_org_graalvm_compiler_hotspot_HotSpotGraalRuntime {

    // Checkstyle: stop
    @Alias @RecomputeFieldValue(kind = Kind.Custom, declClass = InjectedManagementComputer.class, isFinal = true) private static Supplier<HotSpotGraalManagementRegistration> AOT_INJECTED_MANAGEMENT;
    // Checkstyle: resume

    private static final class InjectedManagementComputer implements RecomputeFieldValue.CustomFieldValueComputer {
        @Override
        public Object compute(MetaAccessProvider metaAccess, ResolvedJavaField original, ResolvedJavaField annotated, Object receiver) {
            try {
                Class<?> clazz = Thread.currentThread().getContextClassLoader().loadClass("org.graalvm.compiler.hotspot.management.libgraal.HotSpotGraalManagement$Factory");
                Constructor<?> constructor = clazz.getDeclaredConstructor();
                constructor.setAccessible(true);
                return constructor.newInstance();
            } catch (ClassNotFoundException cnf) {
                return null;
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Substitute
    private static void shutdownLibGraal() {
        VMRuntime.shutdown();
    }
}

@TargetClass(className = "org.graalvm.compiler.hotspot.HotSpotTTYStreamProvider", onlyWith = LibGraalFeature.IsEnabled.class)
final class Target_org_graalvm_compiler_hotspot_HotSpotTTYStreamProvider {

    @Substitute
    private static Pointer getBarrierPointer() {
        return LibGraalEntryPoints.LOG_FILE_BARRIER.get();
    }
}

@TargetClass(className = "org.graalvm.compiler.hotspot.HotSpotGraalOptionValues", onlyWith = LibGraalFeature.IsEnabled.class)
final class Target_org_graalvm_compiler_hotspot_HotSpotGraalOptionValues {

    @Substitute
    private static OptionValues initializeOptions() {
        // Sanity check
        if (!XOptions.getXmn().getPrefix().equals("-X")) {
            throw new InternalError("Expected " + XOptions.getXmn().getPrefixAndName() + " to start with -X");
        }

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
            if (name.startsWith("libgraal.")) {
                if (name.startsWith("libgraal.X")) {
                    String[] xarg = {"-" + name.substring("libgraal.".length()) + e.getValue()};
                    String[] unknown = XOptions.singleton().parse(xarg, false);
                    if (unknown.length == 0) {
                        continue;
                    }
                } else {
                    String value = e.getValue();
                    optionSettings.put(name.substring("libgraal.".length()), value);
                }
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
        GraalServiceThread thread = KnownIntrinsics.convertUnknownValue(this, GraalServiceThread.class);
        if (!LibGraal.attachCurrentThread(HotSpotJVMCIRuntime.runtime(), thread.isDaemon(), null)) {
            throw new InternalError("Couldn't attach to HotSpot runtime");
        }
    }

    @Substitute
    @SuppressWarnings("static-method")
    void afterRun() {
        LibGraal.detachCurrentThread(HotSpotJVMCIRuntime.runtime());
    }
}

@TargetClass(className = "org.graalvm.compiler.hotspot.management.libgraal.MBeanProxy", onlyWith = {LibGraalFeature.IsEnabled.class,
                Target_org_graalvm_compiler_hotspot_management_libgraal_MBeanProxy.IsEnabled.class})
final class Target_org_graalvm_compiler_hotspot_management_libgraal_MBeanProxy {

    @Target(ElementType.FIELD)
    @Retention(RetentionPolicy.RUNTIME)
    @interface ClassData {
        String value();
    }

    // @formatter:off
    // Checkstyle: stop
    @Alias
    @ClassData("org.graalvm.compiler.hotspot.management.SVMMBean")
    @RecomputeFieldValue(kind = Kind.Custom, declClass = Target_org_graalvm_compiler_hotspot_management_libgraal_MBeanProxy.ClassNameComputer.class, isFinal = true)
    private static String HS_BEAN_CLASS_NAME;

    @Alias
    @ClassData("org.graalvm.compiler.hotspot.management.SVMMBean")
    @RecomputeFieldValue(kind = Kind.Custom, declClass = Target_org_graalvm_compiler_hotspot_management_libgraal_MBeanProxy.ClassDataComputer.class, isFinal = true)
    private static byte[] HS_BEAN_CLASS;

    @Alias
    @ClassData("org.graalvm.compiler.hotspot.management.SVMMBean$Factory")
    @RecomputeFieldValue(kind = Kind.Custom, declClass = Target_org_graalvm_compiler_hotspot_management_libgraal_MBeanProxy.ClassNameComputer.class, isFinal = true)
    private static String HS_BEAN_FACTORY_CLASS_NAME;

    @Alias
    @ClassData("org.graalvm.compiler.hotspot.management.SVMMBean$Factory")
    @RecomputeFieldValue(kind = Kind.Custom, declClass = Target_org_graalvm_compiler_hotspot_management_libgraal_MBeanProxy.ClassDataComputer.class, isFinal = true)
    private static byte[] HS_BEAN_FACTORY_CLASS;

    @Alias
    @ClassData("org.graalvm.compiler.hotspot.management.SVMMBean$IsolateThreadScope")
    @RecomputeFieldValue(kind = Kind.Custom, declClass = Target_org_graalvm_compiler_hotspot_management_libgraal_MBeanProxy.ClassNameComputer.class, isFinal = true)
    private static String HS_ISOLATE_THREAD_SCOPE_CLASS_NAME;

    @Alias
    @ClassData("org.graalvm.compiler.hotspot.management.SVMMBean$IsolateThreadScope")
    @RecomputeFieldValue(kind = Kind.Custom, declClass = Target_org_graalvm_compiler_hotspot_management_libgraal_MBeanProxy.ClassDataComputer.class, isFinal = true)
    private static byte[] HS_ISOLATE_THREAD_SCOPE_CLASS;

    @Alias
    @ClassData("org.graalvm.compiler.hotspot.management.SVMMBean$PushBackIterator")
    @RecomputeFieldValue(kind = Kind.Custom, declClass = Target_org_graalvm_compiler_hotspot_management_libgraal_MBeanProxy.ClassNameComputer.class, isFinal = true)
    private static String HS_PUSHBACK_ITER_CLASS_NAME;

    @Alias
    @ClassData("org.graalvm.compiler.hotspot.management.SVMMBean$PushBackIterator")
    @RecomputeFieldValue(kind = Kind.Custom, declClass = Target_org_graalvm_compiler_hotspot_management_libgraal_MBeanProxy.ClassDataComputer.class, isFinal = true)
    private static byte[] HS_PUSHBACK_ITER_CLASS;

    @Alias
    @ClassData("org.graalvm.compiler.hotspot.management.HotSpotToSVMCalls")
    @RecomputeFieldValue(kind = Kind.Custom, declClass = Target_org_graalvm_compiler_hotspot_management_libgraal_MBeanProxy.ClassNameComputer.class, isFinal = true)
    private static String HS_SVM_CALLS_CLASS_NAME;

    @Alias
    @ClassData("org.graalvm.compiler.hotspot.management.HotSpotToSVMCalls")
    @RecomputeFieldValue(kind = Kind.Custom, declClass = Target_org_graalvm_compiler_hotspot_management_libgraal_MBeanProxy.ClassDataComputer.class, isFinal = true)
    private static byte[] HS_SVM_CALLS_CLASS;

    @Alias
    @ClassData("org.graalvm.compiler.hotspot.management.SVMToHotSpotEntryPoints")
    @RecomputeFieldValue(kind = Kind.Custom, declClass = Target_org_graalvm_compiler_hotspot_management_libgraal_MBeanProxy.ClassNameComputer.class, isFinal = true)
    private static String SVM_HS_ENTRYPOINTS_CLASS_NAME;

    @Alias
    @ClassData("org.graalvm.compiler.hotspot.management.SVMToHotSpotEntryPoints")
    @RecomputeFieldValue(kind = Kind.Custom, declClass = Target_org_graalvm_compiler_hotspot_management_libgraal_MBeanProxy.ClassDataComputer.class, isFinal = true)
    private static byte[] SVM_HS_ENTRYPOINTS_CLASS;
    // Checkstyle: resume
    // @formatter:on

    static final class ClassDataComputer implements RecomputeFieldValue.CustomFieldValueComputer {
        @Override
        public Object compute(MetaAccessProvider metaAccess, ResolvedJavaField original, ResolvedJavaField annotated, Object receiver) {
            ClassData classData = annotated.getAnnotation(ClassData.class);
            if (classData == null || classData.value() == null) {
                throw UserError.abort("ClassData must be given");
            }
            URL url = Thread.currentThread().getContextClassLoader().getResource(classData.value().replace('.', '/') + ".class");
            if (url == null) {
                throw UserError.abort("Cannot find SVMMBean class");
            }
            try (InputStream in = url.openStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[4096];
                int len;
                while ((len = in.read(buffer)) > 0) {
                    out.write(buffer, 0, len);
                }
                return out.toByteArray();
            } catch (IOException ioe) {
                throw UserError.abort("Cannot load SVMMBean class due to: " + ioe.getMessage());
            }
        }
    }

    static final class ClassNameComputer implements RecomputeFieldValue.CustomFieldValueComputer {
        @Override
        public Object compute(MetaAccessProvider metaAccess, ResolvedJavaField original, ResolvedJavaField annotated, Object receiver) {
            ClassData classData = annotated.getAnnotation(ClassData.class);
            if (classData == null || classData.value() == null) {
                throw UserError.abort("ClassData must be given");
            }
            return classData.value().replace('.', '/');
        }
    }

    static final class IsEnabled implements BooleanSupplier {
        @Override
        public boolean getAsBoolean() {
            try {
                Class.forName("org.graalvm.compiler.hotspot.management.libgraal.MBeanProxy", false, Thread.currentThread().getContextClassLoader());
                return true;
            } catch (ReflectiveOperationException e) {
                return false;
            }
        }
    }
}

@TargetClass(className = "org.graalvm.compiler.hotspot.SymbolicSnippetEncoder", onlyWith = LibGraalFeature.IsEnabled.class)
@Delete("shouldn't appear in libgraal")
final class Target_org_graalvm_compiler_hotspot_SymbolicSnippetEncoder {
}
