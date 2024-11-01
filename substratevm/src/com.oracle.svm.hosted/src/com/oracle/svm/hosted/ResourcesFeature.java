/*
 * Copyright (c) 2015, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted;

import static com.oracle.svm.core.jdk.Resources.RESOURCES_INTERNAL_PATH_SEPARATOR;
import static com.oracle.svm.core.jdk.Resources.createStorageKey;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.hosted.RuntimeResourceAccess;
import org.graalvm.nativeimage.impl.ConfigurationCondition;
import org.graalvm.nativeimage.impl.RuntimeResourceSupport;

import com.oracle.svm.core.BuildArtifacts;
import com.oracle.svm.core.ClassLoaderSupport;
import com.oracle.svm.core.ClassLoaderSupport.ConditionWithOrigin;
import com.oracle.svm.core.ClassLoaderSupport.ResourceCollector;
import com.oracle.svm.core.MissingRegistrationUtils;
import com.oracle.svm.core.ParsingReason;
import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.configure.ConfigurationConditionResolver;
import com.oracle.svm.core.configure.ConfigurationFile;
import com.oracle.svm.core.configure.ConfigurationFiles;
import com.oracle.svm.core.configure.ResourceConfigurationParser;
import com.oracle.svm.core.configure.ResourcesRegistry;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.jdk.Resources;
import com.oracle.svm.core.jdk.resources.NativeImageResourceFileAttributes;
import com.oracle.svm.core.jdk.resources.NativeImageResourceFileAttributesView;
import com.oracle.svm.core.jdk.resources.NativeImageResourceFileSystem;
import com.oracle.svm.core.jdk.resources.NativeImageResourceFileSystemProvider;
import com.oracle.svm.core.jdk.resources.CompressedGlobTrie.CompressedGlobTrie;
import com.oracle.svm.core.jdk.resources.CompressedGlobTrie.GlobTrieNode;
import com.oracle.svm.core.jdk.resources.CompressedGlobTrie.GlobUtils;
import com.oracle.svm.core.option.AccumulatingLocatableMultiOptionValue;
import com.oracle.svm.core.option.HostedOptionKey;
import com.oracle.svm.core.option.HostedOptionValues;
import com.oracle.svm.core.option.OptionMigrationMessage;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.classinitialization.ClassInitializationSupport;
import com.oracle.svm.hosted.config.ConfigurationParserUtils;
import com.oracle.svm.hosted.imagelayer.HostedImageLayerBuildingSupport;
import com.oracle.svm.hosted.jdk.localization.LocalizationFeature;
import com.oracle.svm.hosted.reflect.NativeImageConditionResolver;
import com.oracle.svm.hosted.snippets.SubstrateGraphBuilderPlugins;
import com.oracle.svm.hosted.util.ResourcesUtils;
import com.oracle.svm.util.LogUtils;
import com.oracle.svm.util.ModuleSupport;
import com.oracle.svm.util.ReflectionUtil;

import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugin;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugins;
import jdk.graal.compiler.options.Option;
import jdk.graal.compiler.options.OptionType;
import jdk.graal.compiler.phases.util.Providers;
import jdk.graal.compiler.util.json.JsonWriter;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * <p>
 * Resources are collected at build time in this feature and stored in a hash map in
 * {@link Resources} class.
 * </p>
 *
 * <p>
 * {@link NativeImageResourceFileSystemProvider } is a core class for building a custom file system
 * on top of resources in the native image.
 * </p>
 *
 * <p>
 * The {@link NativeImageResourceFileSystemProvider} provides most of the functionality of a
 * {@link FileSystem}. It is an in-memory file system that upon creation contains a copy of the
 * resources included in the native-image. Note that changes to files do not affect actual resources
 * returned by resource manipulation methods like `Class.getResource`. Upon being closed, all
 * changes are discarded.
 * </p>
 *
 * <p>
 * As with other file system providers, these methods provide a low-level interface and are not
 * meant for direct usage - see {@link java.nio.file.Files}
 * </p>
 *
 * @see NativeImageResourceFileSystem
 * @see NativeImageResourceFileAttributes
 * @see NativeImageResourceFileAttributesView
 */
@AutomaticallyRegisteredFeature
public class ResourcesFeature implements InternalFeature {

    static final String MODULE_NAME_ALL_UNNAMED = "ALL-UNNAMED";

    public static class Options {
        @OptionMigrationMessage("Use a resource-config.json in your META-INF/native-image/<groupID>/<artifactID> directory instead.")//
        @Option(help = "Regexp to match names of resources to be included in the image.", type = OptionType.User)//
        public static final HostedOptionKey<AccumulatingLocatableMultiOptionValue.Strings> IncludeResources = new HostedOptionKey<>(AccumulatingLocatableMultiOptionValue.Strings.build());

        @Option(help = "Regexp to match names of resources to be excluded from the image.", type = OptionType.User)//
        public static final HostedOptionKey<AccumulatingLocatableMultiOptionValue.Strings> ExcludeResources = new HostedOptionKey<>(AccumulatingLocatableMultiOptionValue.Strings.build());

        private static final String EMBEDDED_RESOURCES_FILE_NAME = "embedded-resources.json";
        @Option(help = "Create a " + EMBEDDED_RESOURCES_FILE_NAME + " file in the build directory. The output conforms to the JSON schema located at: " +
                        "docs/reference-manual/native-image/assets/embedded-resources-schema-v1.1.0.json", type = OptionType.User)//
        public static final HostedOptionKey<Boolean> GenerateEmbeddedResourcesFile = new HostedOptionKey<>(false);
    }

    private boolean sealed = false;

    private record ConditionalPattern(ConfigurationCondition condition, String pattern, Object origin) {
    }

    private record CompiledConditionalPattern(ConfigurationCondition condition, ResourcePattern compiledPattern, Object origin) {
    }

    private Set<ConditionalPattern> resourcePatternWorkSet = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private Set<ConditionalPattern> globWorkSet = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Set<String> excludedResourcePatterns = Collections.newSetFromMap(new ConcurrentHashMap<>());

    private int loadedConfigurations;
    private ImageClassLoader imageClassLoader;

    private class ResourcesRegistryImpl extends ConditionalConfigurationRegistry implements ResourcesRegistry<ConfigurationCondition> {
        private final ClassInitializationSupport classInitializationSupport = ClassInitializationSupport.singleton();

        private final Set<String> alreadyAddedResources = new HashSet<>();

        ResourcesRegistryImpl() {
        }

        @Override
        public void addResources(ConfigurationCondition condition, String pattern, Object origin) {
            try {
                resourcePatternWorkSet.add(new ConditionalPattern(condition, pattern, origin));
            } catch (UnsupportedOperationException e) {
                throw UserError.abort("Resource registration should be performed before beforeAnalysis phase.");
            }
        }

        @Override
        public void addGlob(ConfigurationCondition condition, String module, String glob, Object origin) {
            String canonicalGlob = Resources.toCanonicalForm(glob);
            String resolvedGlob = GlobUtils.transformToTriePath(canonicalGlob, module);
            globWorkSet.add(new ConditionalPattern(condition, resolvedGlob, origin));
        }

        @Override
        public void addCondition(ConfigurationCondition condition, Module module, String resourcePath) {
            var conditionalResource = Resources.singleton().getResourceStorage().get(createStorageKey(module, resourcePath));
            if (conditionalResource != null) {
                classInitializationSupport.addForTypeReachedTracking(condition.getType());
                conditionalResource.getConditions().addCondition(condition);
            }
        }

        /* Adds single resource defined with its module and name */
        @Override
        public void addResourceEntry(Module module, String resourcePath, Object origin) {
            if (!shouldRegisterResource(module, resourcePath)) {
                return;
            }

            if (module != null && module.isNamed()) {
                processResourceFromModule(module, resourcePath, origin);
            } else {
                processResourceFromClasspath(resourcePath, origin);
            }
        }

        @Override
        public void injectResource(Module module, String resourcePath, byte[] resourceContent, Object origin) {
            EmbeddedResourcesInfo.singleton().declareResourceAsRegistered(module, resourcePath, "INJECTED", origin);
            Resources.singleton().registerResource(module, resourcePath, resourceContent);
        }

        @Override
        public void ignoreResources(ConfigurationCondition condition, String pattern) {
            registerConditionalConfiguration(condition, (cnd) -> {
                UserError.guarantee(!sealed, "Resources ignored too late: %s", pattern);

                excludedResourcePatterns.add(pattern);
            });
        }

        @Override
        public void addResourceBundles(ConfigurationCondition condition, String name) {
            registerConditionalConfiguration(condition, (cnd) -> ImageSingletons.lookup(LocalizationFeature.class).prepareBundle(cnd, name));
        }

        @Override
        public void addClassBasedResourceBundle(ConfigurationCondition condition, String basename, String className) {
            registerConditionalConfiguration(condition, (cnd) -> ImageSingletons.lookup(LocalizationFeature.class).prepareClassResourceBundle(basename, className));
        }

        @Override
        public void addResourceBundles(ConfigurationCondition condition, String basename, Collection<Locale> locales) {
            registerConditionalConfiguration(condition, (cnd) -> ImageSingletons.lookup(LocalizationFeature.class).prepareBundle(cnd, basename, locales));
        }

        /*
         * It is possible that one resource can be registered under different conditions
         * (typeReachable). In some cases, few conditions will be satisfied, and we will try to
         * register same resource for each satisfied condition. This function will check if the
         * resource is already registered and prevent multiple registrations of same resource under
         * different conditions
         */
        public boolean shouldRegisterResource(Module module, String resourceName) {
            /* we only do this if we are on the classPath */
            if ((module == null || !module.isNamed())) {
                /*
                 * This check is not thread safe! If the resource is not added yet, maybe some other
                 * thread is attempting to do it, so we have to perform same check again in
                 * synchronized block (in that case). Anyway this check will cut the case when we
                 * are sure that resource is added (so we don't need to enter synchronized block)
                 */
                if (alreadyAddedResources.contains(resourceName)) {
                    return false;
                }

                /* addResources can be called from multiple threads */
                synchronized (alreadyAddedResources) {
                    if (!alreadyAddedResources.contains(resourceName)) {
                        alreadyAddedResources.add(resourceName);
                        return true;
                    } else {
                        return false;
                    }
                }
            } else {
                /* always try to register module entries (we will check duplicates in addEntries) */
                return true;
            }
        }

        private void processResourceFromModule(Module module, String resourcePath, Object origin) {
            try {
                String resourcePackage = jdk.internal.module.Resources.toPackageName(resourcePath);
                if (!resourcePackage.isEmpty()) {
                    /* if processing resource package, make sure that module exports that package */
                    if (module.getPackages().contains(resourcePackage)) {
                        /* Use Access.OPEN to find ALL resources within resource package */
                        ModuleSupport.accessModuleByClass(ModuleSupport.Access.OPEN, ResourcesFeature.class, module, resourcePackage);
                    }
                }

                boolean isDirectory = Files.isDirectory(Path.of(resourcePath));
                if (isDirectory) {
                    String content = ResourcesUtils.getDirectoryContent(resourcePath, false);
                    Resources.singleton().registerDirectoryResource(module, resourcePath, content, false);
                } else {
                    InputStream is = module.getResourceAsStream(resourcePath);
                    registerResource(module, resourcePath, false, is);
                }

                var resolvedModule = module.getLayer().configuration().findModule(module.getName());
                if (resolvedModule.isPresent()) {
                    Optional<URI> location = resolvedModule.get().reference().location();
                    location.ifPresent(uri -> EmbeddedResourcesInfo.singleton().declareResourceAsRegistered(module, resourcePath, uri.toString(), origin));
                }
            } catch (IOException e) {
                Resources.singleton().registerIOException(module, resourcePath, e, LinkAtBuildTimeSupport.singleton().packageOrClassAtBuildTime(resourcePath));
            }
        }

        private void processResourceFromClasspath(String resourcePath, Object origin) {
            Enumeration<URL> urls;
            try {
                /*
                 * There is an edge case where same resource name can be present in multiple jars
                 * (different resources), so we are collecting all resources with given name in all
                 * jars on classpath
                 */
                urls = imageClassLoader.getClassLoader().getResources(resourcePath);
            } catch (IOException e) {
                throw VMError.shouldNotReachHere("getResources for resourcePath " + resourcePath + " failed", e);
            }

            /*
             * getResources could return same entry that was found by different(parent) classLoaders
             */
            Set<String> alreadyProcessedResources = new HashSet<>();
            while (urls.hasMoreElements()) {
                URL url = urls.nextElement();
                if (alreadyProcessedResources.contains(url.toString())) {
                    continue;
                }

                alreadyProcessedResources.add(url.toString());
                try {
                    boolean fromJar = url.getProtocol().equalsIgnoreCase("jar");
                    boolean isDirectory = ResourcesUtils.resourceIsDirectory(url, fromJar);
                    if (isDirectory) {
                        String content = ResourcesUtils.getDirectoryContent(fromJar ? url.toString() : Paths.get(url.toURI()).toString(), fromJar);
                        Resources.singleton().registerDirectoryResource(null, resourcePath, content, fromJar);
                    } else {
                        InputStream is = url.openStream();
                        registerResource(null, resourcePath, fromJar, is);
                    }

                    String source = ResourcesUtils.getResourceSource(url, resourcePath, fromJar);
                    EmbeddedResourcesInfo.singleton().declareResourceAsRegistered(null, resourcePath, source, origin);
                } catch (IOException e) {
                    Resources.singleton().registerIOException(null, resourcePath, e, LinkAtBuildTimeSupport.singleton().packageOrClassAtBuildTime(resourcePath));
                    return;
                } catch (URISyntaxException e) {
                    throw VMError.shouldNotReachHere("resourceIsDirectory for resourcePath " + resourcePath + " failed", e);
                }
            }
        }

        private void registerResource(Module module, String resourcePath, boolean fromJar, InputStream is) {
            if (is == null) {
                Resources.singleton().registerNegativeQuery(module, resourcePath);
                return;
            }

            Resources.singleton().registerResource(module, resourcePath, is, fromJar);

            try {
                is.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Override
    public void afterRegistration(AfterRegistrationAccess a) {
        FeatureImpl.AfterRegistrationAccessImpl access = (FeatureImpl.AfterRegistrationAccessImpl) a;
        imageClassLoader = access.getImageClassLoader();
        ResourcesRegistryImpl resourcesRegistry = new ResourcesRegistryImpl();
        ImageSingletons.add(ResourcesRegistry.class, resourcesRegistry);
        ImageSingletons.add(RuntimeResourceSupport.class, resourcesRegistry);
        EmbeddedResourcesInfo embeddedResourcesInfo = new EmbeddedResourcesInfo();
        ImageSingletons.add(EmbeddedResourcesInfo.class, embeddedResourcesInfo);
    }

    private static ResourcesRegistryImpl resourceRegistryImpl() {
        return (ResourcesRegistryImpl) ImageSingletons.lookup(ResourcesRegistry.class);
    }

    protected boolean collectEmbeddedResourcesInfo() {
        return Options.GenerateEmbeddedResourcesFile.getValue();
    }

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess a) {
        FeatureImpl.BeforeAnalysisAccessImpl access = (FeatureImpl.BeforeAnalysisAccessImpl) a;
        /* load and parse resource configuration files */
        ConfigurationConditionResolver<ConfigurationCondition> conditionResolver = new NativeImageConditionResolver(access.getImageClassLoader(),
                        ClassInitializationSupport.singleton());

        ResourceConfigurationParser<ConfigurationCondition> parser = ResourceConfigurationParser.create(true, conditionResolver, ResourcesRegistry.singleton(),
                        ConfigurationFiles.Options.StrictConfiguration.getValue());
        loadedConfigurations = ConfigurationParserUtils.parseAndRegisterConfigurationsFromCombinedFile(parser, imageClassLoader, "resource");

        ResourceConfigurationParser<ConfigurationCondition> legacyParser = ResourceConfigurationParser.create(false, conditionResolver, ResourcesRegistry.singleton(),
                        ConfigurationFiles.Options.StrictConfiguration.getValue());
        loadedConfigurations += ConfigurationParserUtils.parseAndRegisterConfigurations(legacyParser, imageClassLoader, "resource",
                        ConfigurationFiles.Options.ResourceConfigurationFiles, ConfigurationFiles.Options.ResourceConfigurationResources,
                        ConfigurationFile.RESOURCES.getFileName());

        /* prepare globs for resource registration */
        List<CompressedGlobTrie.GlobWithInfo<ConditionWithOrigin>> patternsWithInfo = globWorkSet
                        .stream()
                        .map(entry -> new CompressedGlobTrie.GlobWithInfo<>(entry.pattern(), new ConditionWithOrigin(entry.condition(), entry.origin()))).toList();
        GlobTrieNode<ConditionWithOrigin> trie = CompressedGlobTrie.CompressedGlobTrieBuilder.build(patternsWithInfo);
        Resources.singleton().setResourcesTrieRoot(trie);

        /*
         * GR-58701: The SVM core is currently not included in the base layer of a Layered Image.
         * Those specific types can be reachable from Resources#resourcesTrieRoot, but they can be
         * missed by the analysis because the GlobTrieNode#children field is only available after
         * analysis and the only reference to those types is with ThrowMissingRegistrationErrors
         * enabled. Until a clear SVM core separation is created and included in the base layer,
         * those types should be manually registered as instantiated before the analysis.
         */
        if (HostedImageLayerBuildingSupport.buildingSharedLayer()) {
            String reason = "Included in the base image";
            access.getMetaAccess().lookupJavaType(ReflectionUtil.lookupClass(false, "com.oracle.svm.core.jdk.resources.CompressedGlobTrie.LiteralNode")).registerAsInstantiated(reason);
            access.getMetaAccess().lookupJavaType(ReflectionUtil.lookupClass(false, "com.oracle.svm.core.jdk.resources.CompressedGlobTrie.DoubleStarNode")).registerAsInstantiated(reason);
            access.getMetaAccess().lookupJavaType(ReflectionUtil.lookupClass(false, "com.oracle.svm.core.jdk.resources.CompressedGlobTrie.StarTrieNode")).registerAsInstantiated(reason);
        }

        /* prepare regex patterns for resource registration */
        resourcePatternWorkSet.addAll(Options.IncludeResources.getValue()
                        .getValuesWithOrigins()
                        .map(e -> new ConditionalPattern(ConfigurationCondition.alwaysTrue(), e.value(), e.origin()))
                        .toList());
        Set<CompiledConditionalPattern> includePatterns = resourcePatternWorkSet
                        .stream()
                        .map(e -> new CompiledConditionalPattern(e.condition(), makeResourcePattern(e.pattern()), e.origin()))
                        .collect(Collectors.toSet());

        excludedResourcePatterns.addAll(Options.ExcludeResources.getValue().values());
        ResourcePattern[] excludePatterns = compilePatterns(excludedResourcePatterns);

        ResourceCollectorImpl collector = new ResourceCollectorImpl(includePatterns, excludePatterns);
        /*
         * register all included patterns in Resources singleton (if we are throwing
         * MissingRegistrationErrors), so they can be queried at runtime to detect missing entries
         */
        if (MissingRegistrationUtils.throwMissingRegistrationErrors()) {
            includePatterns.forEach(resourcePattern -> collector.registerIncludePattern(resourcePattern.condition, resourcePattern.compiledPattern.moduleName(),
                            resourcePattern.compiledPattern.pattern.pattern()));
        }

        /* if we have any entry in resource config file we should collect resources */
        if (!resourcePatternWorkSet.isEmpty() || !globWorkSet.isEmpty()) {
            try {
                collector.prepareProgressReporter();
                ImageSingletons.lookup(ClassLoaderSupport.class).collectResources(collector);
                collector.setAnalysisAccess(access);
            } finally {
                collector.shutDownProgressReporter();
            }
        }

        /*
         * Since we finished resources registration, we are setting resourcePatternWorkSet and
         * globWorkSet to empty unmodifiable set, so we can be sure that they won't be populated in
         * some later phase
         */
        resourcePatternWorkSet = Set.of();
        globWorkSet = Set.of();

        resourceRegistryImpl().setAnalysisAccess(access);
    }

    private static final class ResourceCollectorImpl extends ConditionalConfigurationRegistry implements ResourceCollector {
        private final Set<CompiledConditionalPattern> includePatterns;
        private final ResourcePattern[] excludePatterns;
        private static final int WATCHDOG_RESET_AFTER_EVERY_N_RESOURCES = 1000;
        private static final int WATCHDOG_INITIAL_WARNING_AFTER_N_SECONDS = 60;
        private static final int WATCHDOG_WARNING_AFTER_EVERY_N_SECONDS = 20;
        private final LongAdder reachedResourceEntries;
        private boolean initialReport;
        private volatile String currentlyProcessedEntry;
        ScheduledExecutorService scheduledExecutor;

        private ResourceCollectorImpl(Set<CompiledConditionalPattern> includePatterns, ResourcePattern[] excludePatterns) {
            this.includePatterns = includePatterns;
            this.excludePatterns = excludePatterns;

            this.reachedResourceEntries = new LongAdder();
            this.initialReport = true;
            this.currentlyProcessedEntry = null;
        }

        private void prepareProgressReporter() {
            this.scheduledExecutor = Executors.newSingleThreadScheduledExecutor();
            scheduledExecutor.scheduleAtFixedRate(() -> {
                if (initialReport) {
                    initialReport = false;
                    LogUtils.warning("Resource scanning is taking a long time. " +
                                    "This can be caused by class-path or module-path entries that point to large directory structures. " +
                                    "Please make sure class-/module-path entries are easily accessible to native-image");
                }
                System.out.println("Total scanned entries: " + this.reachedResourceEntries + "," +
                                " current entry: " + (this.currentlyProcessedEntry != null ? this.currentlyProcessedEntry : "Unknown resource"));
            }, WATCHDOG_INITIAL_WARNING_AFTER_N_SECONDS, WATCHDOG_WARNING_AFTER_EVERY_N_SECONDS, TimeUnit.SECONDS);
        }

        private void shutDownProgressReporter() {
            if (!this.scheduledExecutor.isShutdown()) {
                this.scheduledExecutor.shutdown();
            }
        }

        @Override
        public List<ConditionWithOrigin> isIncluded(Module module, String resourceName, URI resource) {
            this.currentlyProcessedEntry = resource.getScheme().equals("jrt") ? (resource + "/" + resourceName) : resource.toString();

            this.reachedResourceEntries.increment();
            if (this.reachedResourceEntries.longValue() % WATCHDOG_RESET_AFTER_EVERY_N_RESOURCES == 0) {
                DeadlockWatchdog.singleton().recordActivity();
            }

            String relativePathWithTrailingSlash = resourceName + RESOURCES_INTERNAL_PATH_SEPARATOR;
            String moduleName = module == null ? null : module.getName();

            /*
             * Once migration to glob patterns is done, this code should be removed (include and
             * exclude patterns)
             */
            for (ResourcePattern rp : excludePatterns) {
                if (!rp.moduleNameMatches(moduleName)) {
                    continue;
                }
                if (rp.pattern.matcher(resourceName).matches() || rp.pattern.matcher(relativePathWithTrailingSlash).matches()) {
                    return List.of(); // nothing should match excluded resource
                }
            }

            /* Possibly we can have multiple conditions for one resource */
            List<ConditionWithOrigin> conditions = new ArrayList<>();
            for (CompiledConditionalPattern rp : includePatterns) {
                if (!rp.compiledPattern().moduleNameMatches(moduleName)) {
                    continue;
                }
                if (rp.compiledPattern().pattern.matcher(resourceName).matches() || rp.compiledPattern().pattern.matcher(relativePathWithTrailingSlash).matches()) {
                    conditions.add(new ConditionWithOrigin(rp.condition(), rp.origin()));
                }
            }

            /* check if resource can be matched in globTrie structure */
            conditions.addAll(getConditionsFromGlobTrie(module, resourceName));

            return conditions;
        }

        private static List<ConditionWithOrigin> getConditionsFromGlobTrie(Module module, String resourceName) {
            String pattern = GlobUtils.transformToTriePath(resourceName, module == null ? "" : module.getName());
            List<ConditionWithOrigin> types = CompressedGlobTrie.getHostedOnlyContentIfMatched(Resources.singleton().getResourcesTrieRoot(), pattern);
            if (types == null) {
                return Collections.emptyList();
            }
            return types;
        }

        @Override
        public void addResourceEntry(Module module, String resourceName, Object origin) {
            ImageSingletons.lookup(RuntimeResourceSupport.class).addResourceEntry(module, resourceName, origin);
        }

        @Override
        public void addResourceConditionally(Module module, String resourceName, ConfigurationCondition condition, Object origin) {
            registerConditionalConfiguration(condition, cnd -> {
                addResourceEntry(module, resourceName, origin);
                ImageSingletons.lookup(RuntimeResourceSupport.class).addCondition(cnd, module, resourceName);
            });
        }

        @Override
        public void registerIOException(Module module, String resourceName, IOException e, boolean linkAtBuildTime) {
            Resources.singleton().registerIOException(module, resourceName, e, linkAtBuildTime);
        }

        @Override
        public void registerNegativeQuery(Module module, String resourceName) {
            EmbeddedResourcesInfo.singleton().declareResourceAsRegistered(module, resourceName, "", "");
            Resources.singleton().registerNegativeQuery(module, resourceName);
        }

        public void registerIncludePattern(ConfigurationCondition condition, String module, String pattern) {
            registerConditionalConfiguration(condition, cnd -> Resources.singleton().registerIncludePattern(cnd, module, pattern));
        }
    }

    private ResourcePattern[] compilePatterns(Set<String> patterns) {
        return patterns.stream()
                        .filter(s -> s.length() > 0)
                        .map(this::makeResourcePattern)
                        .toList()
                        .toArray(new ResourcePattern[]{});
    }

    private ResourcePattern makeResourcePattern(String rawPattern) {
        String[] moduleNameWithPattern = SubstrateUtil.split(rawPattern, ":", 2);
        if (moduleNameWithPattern.length < 2) {
            return new ResourcePattern(null, Pattern.compile(moduleNameWithPattern[0]));
        } else {
            String moduleName = moduleNameWithPattern[0];
            return new ResourcePattern(moduleName, Pattern.compile(moduleNameWithPattern[1]));
        }
    }

    private record ResourcePattern(String moduleName, Pattern pattern) {

        boolean moduleNameMatches(String resourceContainerModuleName) {
            if (moduleName == null) {
                // Accept everything
                return true;
            }
            if (moduleName.equals(MODULE_NAME_ALL_UNNAMED)) {
                // Only accept if resource is from classpath
                return resourceContainerModuleName == null;
            }
            return moduleName.equals(resourceContainerModuleName);
        }
    }

    @Override
    public void afterAnalysis(AfterAnalysisAccess access) {
        sealed = true;
        if (Options.GenerateEmbeddedResourcesFile.getValue()) {
            Path reportLocation = NativeImageGenerator.generatedFiles(HostedOptionValues.singleton()).resolve(Options.EMBEDDED_RESOURCES_FILE_NAME);
            try (JsonWriter writer = new JsonWriter(reportLocation)) {
                EmbeddedResourceExporter.printReport(writer);
            } catch (IOException e) {
                throw VMError.shouldNotReachHere("Json writer cannot write to: " + reportLocation, e);
            }

            BuildArtifacts.singleton().add(BuildArtifacts.ArtifactType.BUILD_INFO, reportLocation);
        }

        /* prepare resources GlobTrie for runtime */
        GlobTrieNode<ConditionWithOrigin> root = Resources.singleton().getResourcesTrieRoot();
        CompressedGlobTrie.removeNodes(root, (conditionWithOrigin) -> !access.isReachable(conditionWithOrigin.condition().getType()));
        CompressedGlobTrie.finalize(root);
    }

    @Override
    public void beforeCompilation(BeforeCompilationAccess access) {
        if (!ImageSingletons.contains(FallbackFeature.class)) {
            return;
        }
        FallbackFeature.FallbackImageRequest resourceFallback = ImageSingletons.lookup(FallbackFeature.class).resourceFallback;
        if (resourceFallback != null && Options.IncludeResources.getValue().values().isEmpty() && loadedConfigurations == 0) {
            throw resourceFallback;
        }
    }

    @Override
    public void registerInvocationPlugins(Providers providers, GraphBuilderConfiguration.Plugins plugins, ParsingReason reason) {
        if (!reason.duringAnalysis() || reason == ParsingReason.JITCompilation) {
            return;
        }

        Method[] resourceMethods = {
                        ReflectionUtil.lookupMethod(Class.class, "getResource", String.class),
                        ReflectionUtil.lookupMethod(Class.class, "getResourceAsStream", String.class)
        };
        Method resolveResourceName = ReflectionUtil.lookupMethod(Class.class, "resolveName", String.class);

        for (Method method : resourceMethods) {
            registerResourceRegistrationPlugin(plugins.getInvocationPlugins(), method, resolveResourceName, reason);
        }
    }

    private void registerResourceRegistrationPlugin(InvocationPlugins plugins, Method method, Method resolveResourceName, ParsingReason reason) {
        List<Class<?>> parameterTypes = new ArrayList<>();
        assert !Modifier.isStatic(method.getModifiers());
        parameterTypes.add(InvocationPlugin.Receiver.class);
        parameterTypes.addAll(Arrays.asList(method.getParameterTypes()));

        plugins.register(method.getDeclaringClass(), new InvocationPlugin.RequiredInvocationPlugin(method.getName(), parameterTypes.toArray(new Class<?>[0])) {
            @Override
            public boolean isDecorator() {
                return true;
            }

            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode arg) {
                VMError.guarantee(!sealed, "All bytecode parsing happens before the analysis, i.e., before the registry is sealed");
                Class<?> clazz = SubstrateGraphBuilderPlugins.asConstantObject(b, Class.class, receiver.get(false));
                String resource = SubstrateGraphBuilderPlugins.asConstantObject(b, String.class, arg);
                if (clazz != null && resource != null) {
                    String resourceName;
                    try {
                        resourceName = (String) resolveResourceName.invoke(clazz, resource);
                    } catch (ReflectiveOperationException e) {
                        throw VMError.shouldNotReachHere(e);
                    }
                    b.add(ReachabilityRegistrationNode.create(() -> RuntimeResourceAccess.addResource(clazz.getModule(), resourceName), reason));
                    return true;
                }
                return false;
            }
        });
    }
}
