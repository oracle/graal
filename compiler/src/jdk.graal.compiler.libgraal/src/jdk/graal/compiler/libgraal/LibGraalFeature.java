/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.libgraal;

import java.io.IOException;
import java.io.InputStream;
import java.lang.module.ModuleDescriptor;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import jdk.graal.compiler.serviceprovider.GraalServices;
import org.graalvm.collections.EconomicMap;
import org.graalvm.jniutils.NativeBridgeSupport;
import org.graalvm.nativeimage.ImageInfo;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.hosted.FieldValueTransformer;
import org.graalvm.nativeimage.hosted.RuntimeClassInitialization;
import org.graalvm.nativeimage.hosted.RuntimeReflection;
import org.graalvm.nativeimage.libgraal.hosted.LibGraalLoader;

import jdk.graal.compiler.core.common.Fields;
import jdk.graal.compiler.core.common.LibGraalSupport.HostedOnly;
import jdk.graal.compiler.core.common.NativeImageSupport;
import jdk.graal.compiler.core.common.spi.ForeignCallSignature;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.graph.Edges;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.hotspot.CompilerConfig;
import jdk.graal.compiler.hotspot.EncodedSnippets;
import jdk.graal.compiler.hotspot.HotSpotForeignCallLinkage;
import jdk.graal.compiler.hotspot.HotSpotReplacementsImpl;
import jdk.graal.compiler.libgraal.truffle.LibGraalTruffleHostEnvironmentLookup;
import jdk.graal.compiler.options.OptionDescriptor;
import jdk.graal.compiler.options.OptionKey;
import jdk.graal.compiler.options.OptionsParser;
import jdk.graal.compiler.truffle.host.TruffleHostEnvironment;
import jdk.graal.compiler.util.CollectionsUtil;
import jdk.graal.compiler.util.EconomicHashMap;
import jdk.graal.compiler.util.ObjectCopier;
import jdk.internal.module.Modules;
import jdk.vm.ci.hotspot.HotSpotModifiers;

/**
 * This feature builds the libgraal shared library (e.g., libjvmcicompiler.so on linux).
 */
@Platforms(Platform.HOSTED_ONLY.class)
public final class LibGraalFeature implements Feature {

    /**
     * Looks up a class in the libgraal class loader.
     *
     * @throws Error if the lookup fails
     */
    public static Class<?> lookupClass(String className) {
        try {
            return Class.forName(className, false, LibGraalFeature.class.getClassLoader());
        } catch (ClassNotFoundException ex) {
            throw new GraalError(ex);
        }
    }

    /**
     * Looks up a field via reflection and makes it accessible for reading.
     *
     * @throws Error if the operation fails
     */
    public static Field lookupField(Class<?> declaringClass, String fieldName) {
        try {
            Field result = declaringClass.getDeclaredField(fieldName);
            Modules.addOpensToAllUnnamed(declaringClass.getModule(), declaringClass.getPackageName());
            result.setAccessible(true);
            return result;
        } catch (ReflectiveOperationException ex) {
            throw new GraalError(ex);
        }
    }

    public static final class IsEnabled implements BooleanSupplier {
        @Override
        public boolean getAsBoolean() {
            Class<LibGraalFeature> clazz = LibGraalFeature.class;
            return ImageSingletons.contains(clazz);
        }
    }

    /**
     * See javadoc for {@code jdk.graal.compiler.libgraal.loader.HostedLibGraalClassLoader}.
     */
    private static Path readLibgraalJavaHome(ClassLoader cl) {
        try (InputStream in = cl.getResourceAsStream("META-INF/libgraal.java.home")) {
            if (in == null) {
                throw new GraalError(cl.getClass().getName() + " does not support META-INF/libgraal.java.home protocol (see javadoc of HostedLibGraalClassLoader)");
            }
            return Path.of(new String(in.readAllBytes()));
        } catch (IOException e) {
            throw new GraalError(e);
        }
    }

    private final LibGraalLoader libgraalLoader = (LibGraalLoader) getClass().getClassLoader();
    private final Path libgraalJavaHome = readLibgraalJavaHome(getClass().getClassLoader());

    private BeforeAnalysisAccess beforeAnalysisAccess;
    private BeforeCompilationAccess beforeCompilationAccess;
    private OptionCollector optionCollector;

    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        // Check that NativeImageSupport.inBuildtimeCode() and ImageInfo.inImageBuildtimeCode()
        // agree on the system property key and value they rely on.
        GraalError.guarantee(ImageInfo.PROPERTY_IMAGE_CODE_KEY.equals("org.graalvm.nativeimage.imagecode") &&
                        ImageInfo.PROPERTY_IMAGE_CODE_VALUE_BUILDTIME.equals("buildtime"),
                        "%s is out of sync with %s", NativeImageSupport.class, ImageInfo.class);

        ImageSingletons.add(NativeBridgeSupport.class, new LibGraalNativeBridgeSupport());

        // All qualified exports to libgraal modules need to be further exported to
        // ALL-UNNAMED so that access is also possible when the libgraal classes
        // are loaded via the libgraal loader into unnamed modules.
        Set<String> libgraalModules = CollectionsUtil.setCopyOf(libgraalLoader.getClassModuleMap().values());
        for (Module module : ModuleLayer.boot().modules()) {
            Set<ModuleDescriptor.Exports> exports = module.getDescriptor().exports();
            for (ModuleDescriptor.Exports e : exports) {
                if (e.targets().stream().anyMatch(libgraalModules::contains)) {
                    Modules.addExportsToAllUnnamed(module, e.source());
                }
            }
        }
    }

    @Override
    public void duringSetup(DuringSetupAccess access) {
        optionCollector = new OptionCollector();

        /*
         * Replace HostedLibGraalClassLoader with a basic class loader at runtime that cuts out the
         * parent class loader (i.e., NativeImageClassLoader) and is guaranteed not to make any
         * additional types/methods/fields accidentally reachable.
         */
        String loaderName = ((ClassLoader) libgraalLoader).getName();
        LibGraalClassLoader runtimeLibgraalLoader = new LibGraalClassLoader(loaderName);
        access.registerObjectReplacer(obj -> obj == libgraalLoader ? runtimeLibgraalLoader : obj);

        // Register reachability handler used to initialize OptionsParser.libgraalOptions.
        access.registerObjectReachabilityHandler(optionCollector::accept, OptionKey.class);

        // Register reachability handler that marks the fields that are accessed via unsafe.
        access.registerObjectReachabilityHandler(fields -> {
            for (int i = 0; i < fields.getOffsets().length; i++) {
                Field field = fields.getField(i);
                beforeAnalysisAccess.registerAsUnsafeAccessed(field);
            }
        }, Fields.class);

        // Register reachability handler that marks NodeClass subclasses as unsafe allocated
        // (see jdk.graal.compiler.graph.NodeClass.allocateInstance).
        access.registerObjectReachabilityHandler(nodeClass -> {
            Class<?> clazz = nodeClass.getClazz();
            if (!nodeClass.isAbstract()) {
                /* Support for NodeClass.allocateInstance. */
                beforeAnalysisAccess.registerAsUnsafeAllocated(clazz);
            }
        }, NodeClass.class);

        // Register the fields/methods/classes accessed via JNI.
        GetJNIConfig.register((ClassLoader) libgraalLoader, libgraalJavaHome);
    }

    /**
     * Collects all instances of the LibGraalLoader loaded {@link OptionKey} class reached by the
     * static analysis and uses them to initialize {@link OptionsParser#libgraalOptions}.
     */
    private final class OptionCollector implements Consumer<OptionKey<?>> {
        private final Set<OptionKey<?>> options = Collections.newSetFromMap(new ConcurrentHashMap<>());

        private boolean sealed;

        @Override
        public void accept(OptionKey<?> option) {
            if (sealed) {
                GraalError.guarantee(options.contains(option), "All options must have been discovered during static analysis: %s", option);
            } else {
                options.add(option);
            }
        }

        void afterAnalysis(AfterAnalysisAccess access) {
            sealed = true;
            Map<String, String> modules = libgraalLoader.getClassModuleMap();
            for (OptionKey<?> option : options) {
                OptionDescriptor descriptor = option.getDescriptor();
                if (descriptor != null && descriptor.getContainer().optionsAreDiscoverable()) {
                    GraalError.guarantee(access.isReachable(option.getClass()), "%s", option.getClass());
                    GraalError.guarantee(access.isReachable(descriptor.getClass()), "%s", descriptor.getClass());

                    String name = option.getName();
                    OptionDescriptor conflict = OptionsParser.libgraalOptions.descriptors().put(name, descriptor);
                    if (conflict != null) {
                        throw new GraalError("Duplicate option names for %s.% and %s.%s",
                                        descriptor.getDeclaringClass().getName(),
                                        descriptor.getFieldName(),
                                        conflict.getDeclaringClass().getName(),
                                        conflict.getFieldName());
                    }

                    String module = modules.get(descriptor.getDeclaringClass().getName());
                    if (module.contains("enterprise")) {
                        OptionsParser.libgraalOptions.enterpriseOptions().add(name);
                    }
                }
            }
            OptionDescriptor.sealHelpStrings();
        }
    }

    /**
     * Transformer for {@code Fields.offsets} and {@code Edges.iterationMask} which need to be
     * recomputed to use SVM field offsets instead of HotSpot field offsets.
     */
    class FieldOffsetsTransformer implements FieldValueTransformer {
        /**
         * Map from {@link Fields} objects to a (newOffsets, newIterationMask) tuple represented as
         * a {@link java.util.Map.Entry} value.
         */
        private final Map<Object, Map.Entry<long[], Long>> replacements = EconomicHashMap.newIdentityMap();

        final Field fieldsOffsetsField;
        final Field edgesIterationMaskField;

        FieldOffsetsTransformer() {
            fieldsOffsetsField = lookupField(Fields.class, "offsets");
            edgesIterationMaskField = lookupField(Edges.class, "iterationMask");
        }

        void register(BeforeAnalysisAccess access) {
            access.registerFieldValueTransformer(fieldsOffsetsField, this);
            access.registerFieldValueTransformer(edgesIterationMaskField, this);
        }

        @Override
        public boolean isAvailable() {
            return beforeCompilationAccess != null;
        }

        @Override
        public Object transform(Object receiver, Object originalValue) {
            Map.Entry<long[], Long> repl = getReplacement(receiver);
            if (originalValue instanceof long[]) {
                return repl.getKey();
            }
            return repl.getValue();
        }

        private Map.Entry<long[], Long> getReplacement(Object receiver) {
            synchronized (replacements) {
                return replacements.computeIfAbsent(receiver, this::computeReplacement);
            }
        }

        private Map.Entry<long[], Long> computeReplacement(Object receiver) {
            Fields fields = (Fields) receiver;
            return fields.recomputeOffsetsAndIterationMask(beforeCompilationAccess::objectFieldOffset);
        }
    }

    /**
     * List of reached elements annotated by {@link HostedOnly}.
     */
    private final List<Object> reachedHostedOnlyElements = new ArrayList<>();

    record HostedOnlyElementCallback(Object element, List<Object> reached) implements Consumer<DuringAnalysisAccess> {
        @Override
        public void accept(DuringAnalysisAccess duringAnalysisAccess) {
            reached.add(element);
        }
    }

    private void registerHostedOnlyElements(BeforeAnalysisAccess access, AnnotatedElement... elements) {
        for (AnnotatedElement element : elements) {
            HostedOnly annotation = element.getAnnotation(HostedOnly.class);
            if (annotation == null) {
                continue;
            }
            if (annotation.unlessTrue().isEmpty() || !Boolean.parseBoolean(GraalServices.getSavedProperty(annotation.unlessTrue()))) {
                access.registerReachabilityHandler(new HostedOnlyElementCallback(element, reachedHostedOnlyElements), element);
            }
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        beforeAnalysisAccess = access;
        new FieldOffsetsTransformer().register(access);

        for (String className : libgraalLoader.getClassModuleMap().keySet()) {
            try {
                Class<?> c = Class.forName(className, false, (ClassLoader) libgraalLoader);
                registerHostedOnlyElements(access, c);
                registerHostedOnlyElements(access, c.getDeclaredMethods());
                registerHostedOnlyElements(access, c.getDeclaredFields());
                registerHostedOnlyElements(access, c.getDeclaredConstructors());
            } catch (ClassNotFoundException e) {
                throw new GraalError(e);
            }
        }

        /* Contains static fields that depend on HotSpotJVMCIRuntime */
        RuntimeClassInitialization.initializeAtRunTime(HotSpotModifiers.class);
        RuntimeClassInitialization.initializeAtRunTime(lookupClass("jdk.vm.ci.hotspot.HotSpotCompiledCodeStream"));
        RuntimeClassInitialization.initializeAtRunTime(lookupClass("jdk.vm.ci.hotspot.HotSpotCompiledCodeStream$Tag"));

        /* Needed for runtime calls to BoxingSnippets.Templates.getCacheClass(JavaKind) */
        RuntimeReflection.registerAllDeclaredClasses(Character.class);
        RuntimeReflection.register(lookupField(lookupClass("java.lang.Character$CharacterCache"), "cache"));
        RuntimeReflection.registerAllDeclaredClasses(Byte.class);
        RuntimeReflection.register(lookupField(lookupClass("java.lang.Byte$ByteCache"), "cache"));
        RuntimeReflection.registerAllDeclaredClasses(Short.class);
        RuntimeReflection.register(lookupField(lookupClass("java.lang.Short$ShortCache"), "cache"));
        RuntimeReflection.registerAllDeclaredClasses(Integer.class);
        RuntimeReflection.register(lookupField(lookupClass("java.lang.Integer$IntegerCache"), "cache"));
        RuntimeReflection.registerAllDeclaredClasses(Long.class);
        RuntimeReflection.register(lookupField(lookupClass("java.lang.Long$LongCache"), "cache"));

        GetCompilerConfig.Result configResult = GetCompilerConfig.from(libgraalJavaHome);
        for (var e : configResult.opens().entrySet()) {
            Module module = ModuleLayer.boot().findModule(e.getKey()).orElseThrow();
            for (String source : e.getValue()) {
                Modules.addOpensToAllUnnamed(module, source);
            }
        }

        EconomicMap<String, Object> libgraalObjects = (EconomicMap<String, Object>) ObjectCopier.decode(configResult.encodedConfig(), (ClassLoader) libgraalLoader);
        EncodedSnippets encodedSnippets = (EncodedSnippets) libgraalObjects.get("encodedSnippets");
        checkNodeClasses(encodedSnippets, (String) libgraalObjects.get("snippetNodeClasses"));

        // Mark all non-abstract Node classes as allocated so they
        // are available during graph decoding.
        for (NodeClass<?> nodeClass : encodedSnippets.getSnippetNodeClasses()) {
            if (!nodeClass.isAbstract()) {
                access.registerAsInHeap(nodeClass.getClazz());
            }
        }
        HotSpotReplacementsImpl.setEncodedSnippets(encodedSnippets);

        List<ForeignCallSignature> foreignCallSignatures = (List<ForeignCallSignature>) libgraalObjects.get("foreignCallSignatures");
        HotSpotForeignCallLinkage.Stubs.initStubs(foreignCallSignatures);

        TruffleHostEnvironment.overrideLookup(new LibGraalTruffleHostEnvironmentLookup());
    }

    private static void checkNodeClasses(EncodedSnippets encodedSnippets, String actual) {
        String expect = CompilerConfig.snippetNodeClassesToJSON(encodedSnippets);
        GraalError.guarantee(actual.equals(expect), "%n%s%n !=%n%s", actual, expect);
    }

    @Override
    public void afterAnalysis(AfterAnalysisAccess access) {
        optionCollector.afterAnalysis(access);

        if (!reachedHostedOnlyElements.isEmpty()) {
            Map<String, Set<String>> suggestions = new TreeMap<>();
            for (var e : reachedHostedOnlyElements) {
                String name;
                String value;
                switch (e) {
                    case Method m -> {
                        name = "Method";
                        value = m.getDeclaringClass().getName() + "." + m.getName();
                    }
                    case Constructor<?> c -> {
                        name = "Method";
                        Class<?> dc = c.getDeclaringClass();
                        value = dc.getName() + "." + dc.getSimpleName();
                    }
                    case Field f -> {
                        name = "Field";
                        value = f.getDeclaringClass().getName() + "." + f.getName();
                    }
                    case Class<?> c -> {
                        name = "Type";
                        value = c.getName();
                    }
                    case null, default -> throw new GraalError("Unexpected element: ", e);
                }
                suggestions.computeIfAbsent(name, k -> new TreeSet<>()).add(value);
            }
            var sep = System.lineSeparator() + "  ";
            var s = suggestions.entrySet().stream().map(e -> e.getValue().stream().map(v -> "-H:AbortOn" + e.getKey() + "Reachable" + "=" + v).collect(Collectors.joining(sep))).collect(
                            Collectors.joining(sep));
            String anno = HostedOnly.class.getSimpleName();
            throw new IllegalArgumentException("@" + anno + " annotated elements reached. Add following Native Image flags to see why they are reachable:" + sep + s);
        }
    }

    @Override
    public void beforeCompilation(BeforeCompilationAccess access) {
        beforeCompilationAccess = access;
    }
}
