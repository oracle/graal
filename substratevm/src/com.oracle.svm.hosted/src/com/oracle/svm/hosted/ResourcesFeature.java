/*
 * Copyright (c) 2015, 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.JarURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
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
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.hosted.RuntimeResourceAccess;
import org.graalvm.nativeimage.impl.ConfigurationCondition;
import org.graalvm.nativeimage.impl.RuntimeResourceSupport;

import com.oracle.svm.core.ClassLoaderSupport;
import com.oracle.svm.core.ClassLoaderSupport.ResourceCollector;
import com.oracle.svm.core.MissingRegistrationUtils;
import com.oracle.svm.core.ParsingReason;
import com.oracle.svm.core.SubstrateUtil;
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
import com.oracle.svm.core.option.HostedOptionKey;
import com.oracle.svm.core.option.LocatableMultiOptionValue;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.config.ConfigurationParserUtils;
import com.oracle.svm.hosted.jdk.localization.LocalizationFeature;
import com.oracle.svm.util.LogUtils;
import com.oracle.svm.util.ModuleSupport;
import com.oracle.svm.util.ReflectionUtil;

import jdk.graal.compiler.api.replacements.SnippetReflectionProvider;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugin;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugins;
import jdk.graal.compiler.options.Option;
import jdk.graal.compiler.options.OptionType;
import jdk.graal.compiler.phases.util.Providers;
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
public final class ResourcesFeature implements InternalFeature {

    static final String MODULE_NAME_ALL_UNNAMED = "ALL-UNNAMED";

    public static class Options {
        @Option(help = {"Regexp to match names of resources to be included in the image.",
                        "Use a resource-config.json in your META-INF/native-image/<groupID>/<artifactID> directory instead."}, type = OptionType.User)//
        public static final HostedOptionKey<LocatableMultiOptionValue.Strings> IncludeResources = new HostedOptionKey<>(LocatableMultiOptionValue.Strings.build());

        @Option(help = "Regexp to match names of resources to be excluded from the image.", type = OptionType.User)//
        public static final HostedOptionKey<LocatableMultiOptionValue.Strings> ExcludeResources = new HostedOptionKey<>(LocatableMultiOptionValue.Strings.build());
    }

    private boolean sealed = false;

    private record ConditionalPattern(ConfigurationCondition condition, String pattern) {
    }

    private record CompiledConditionalPattern(ConfigurationCondition condition, ResourcePattern compiledPattern) {
    }

    private Set<ConditionalPattern> resourcePatternWorkSet = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Set<String> excludedResourcePatterns = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private int loadedConfigurations;
    private ImageClassLoader imageClassLoader;

    private class ResourcesRegistryImpl extends ConditionalConfigurationRegistry implements ResourcesRegistry {
        private final ConfigurationTypeResolver configurationTypeResolver;
        private final Set<String> alreadyAddedResources = new HashSet<>();

        ResourcesRegistryImpl(ConfigurationTypeResolver configurationTypeResolver) {
            this.configurationTypeResolver = configurationTypeResolver;
        }

        @Override
        public void addResources(ConfigurationCondition condition, String pattern) {
            if (configurationTypeResolver.resolveConditionType(condition.getTypeName()) == null) {
                return;
            }

            try {
                resourcePatternWorkSet.add(new ConditionalPattern(condition, pattern));
            } catch (UnsupportedOperationException e) {
                throw UserError.abort("Resource registration should be performed before beforeAnalysis phase.");
            }
        }

        /* Adds single resource defined with its module and name */
        @Override
        public void addResource(Module module, String resourcePath) {
            if (!shouldRegisterResource(module, resourcePath)) {
                return;
            }

            if (module != null && module.isNamed()) {
                processResourceFromModule(module, resourcePath);
            } else {
                processResourceFromClasspath(resourcePath);
            }
        }

        @Override
        public void injectResource(Module module, String resourcePath, byte[] resourceContent) {
            Resources.singleton().registerResource(module, resourcePath, resourceContent);
        }

        @Override
        public void ignoreResources(ConfigurationCondition condition, String pattern) {
            if (configurationTypeResolver.resolveConditionType(condition.getTypeName()) == null) {
                return;
            }
            registerConditionalConfiguration(condition, () -> {
                UserError.guarantee(!sealed, "Resources ignored too late: %s", pattern);

                excludedResourcePatterns.add(pattern);
            });
        }

        @Override
        public void addResourceBundles(ConfigurationCondition condition, String name) {
            if (configurationTypeResolver.resolveConditionType(condition.getTypeName()) == null) {
                return;
            }
            registerConditionalConfiguration(condition, () -> ImageSingletons.lookup(LocalizationFeature.class).prepareBundle(name));
        }

        @Override
        public void addClassBasedResourceBundle(ConfigurationCondition condition, String basename, String className) {
            if (configurationTypeResolver.resolveConditionType(condition.getTypeName()) == null) {
                return;
            }
            registerConditionalConfiguration(condition, () -> ImageSingletons.lookup(LocalizationFeature.class).prepareClassResourceBundle(basename, className));
        }

        @Override
        public void addResourceBundles(ConfigurationCondition condition, String basename, Collection<Locale> locales) {
            if (configurationTypeResolver.resolveConditionType(condition.getTypeName()) == null) {
                return;
            }
            registerConditionalConfiguration(condition, () -> ImageSingletons.lookup(LocalizationFeature.class).prepareBundle(basename, locales));
        }

        /*
         * It is possible that one resource can be registered under different conditions
         * (typeReachable). In some cases, few conditions will be satisfied, and we will try to
         * register same resource for each satisfied condition. This function will check if the
         * resource is already registered and prevent multiple registrations of same resource under
         * different conditions
         */
        public boolean shouldRegisterResource(Module module, String resourceName) {
            // we only do this if we are on the classPath
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

                // addResources can be called from multiple threads
                synchronized (alreadyAddedResources) {
                    if (!alreadyAddedResources.contains(resourceName)) {
                        alreadyAddedResources.add(resourceName);
                        return true;
                    } else {
                        return false;
                    }
                }
            } else {
                // always try to register module entries (we will check duplicates in addEntries)
                return true;
            }
        }

        private void processResourceFromModule(Module module, String resourcePath) {
            try {
                String resourcePackage = jdk.internal.module.Resources.toPackageName(resourcePath);
                if (!resourcePackage.isEmpty()) {
                    // if processing resource package, make sure that module exports that package
                    if (module.getPackages().contains(resourcePackage)) {
                        // Use Access.OPEN to find ALL resources within resource package
                        ModuleSupport.accessModuleByClass(ModuleSupport.Access.OPEN, ResourcesFeature.class, module, resourcePackage);
                    }
                }

                boolean isDirectory = Files.isDirectory(Path.of(resourcePath));
                if (isDirectory) {
                    String content = getDirectoryContent(resourcePath, false);
                    Resources.singleton().registerDirectoryResource(module, resourcePath, content, false);
                } else {
                    InputStream is = module.getResourceAsStream(resourcePath);
                    registerResource(module, resourcePath, false, is);
                }
            } catch (IOException e) {
                Resources.singleton().registerIOException(module, resourcePath, e, LinkAtBuildTimeSupport.singleton().packageOrClassAtBuildTime(resourcePath));
            }
        }

        private void processResourceFromClasspath(String resourcePath) {
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

            // getResources could return same entry that was found by different(parent) classLoaders
            Set<String> alreadyProcessedResources = new HashSet<>();
            while (urls.hasMoreElements()) {
                URL url = urls.nextElement();
                if (alreadyProcessedResources.contains(url.toString())) {
                    continue;
                }

                alreadyProcessedResources.add(url.toString());
                try {
                    boolean fromJar = url.getProtocol().equalsIgnoreCase("jar");
                    boolean isDirectory = resourceIsDirectory(url, fromJar, resourcePath);
                    if (isDirectory) {
                        String content = getDirectoryContent(fromJar ? url.toString() : Paths.get(url.toURI()).toString(), fromJar);
                        Resources.singleton().registerDirectoryResource(null, resourcePath, content, fromJar);
                    } else {
                        InputStream is = url.openStream();
                        registerResource(null, resourcePath, fromJar, is);
                    }
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
                return;
            }

            Resources.singleton().registerResource(module, resourcePath, is, fromJar);

            try {
                is.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        /* Util functions for resource attributes calculations */
        private String urlToJarPath(URL url) {
            try {
                return ((JarURLConnection) url.openConnection()).getJarFileURL().toURI().getPath();
            } catch (IOException | URISyntaxException e) {
                throw new RuntimeException(e);
            }
        }

        private boolean resourceIsDirectory(URL url, boolean fromJar, String resourcePath) throws IOException, URISyntaxException {
            if (fromJar) {
                try (JarFile jf = new JarFile(urlToJarPath(url))) {
                    return jf.getEntry(resourcePath).isDirectory();
                }
            } else {
                return Files.isDirectory(Path.of(url.toURI()));
            }
        }

        private String getDirectoryContent(String path, boolean fromJar) throws IOException {
            Set<String> content = new TreeSet<>();
            if (fromJar) {
                try (JarFile jf = new JarFile(urlToJarPath(URI.create(path).toURL()))) {
                    String pathSeparator = FileSystems.getDefault().getSeparator();
                    String directoryPath = path.split("!")[1];

                    // we are removing leading slash because jar entry names don't start with slash
                    if (directoryPath.startsWith(pathSeparator)) {
                        directoryPath = directoryPath.substring(1);
                    }

                    Enumeration<JarEntry> entries = jf.entries();
                    while (entries.hasMoreElements()) {
                        String entry = entries.nextElement().getName();
                        if (entry.startsWith(directoryPath)) {
                            String contentEntry = entry.substring(directoryPath.length());

                            // remove the leading slash
                            if (contentEntry.startsWith(pathSeparator)) {
                                contentEntry = contentEntry.substring(1);
                            }

                            // prevent adding empty strings as a content
                            if (!contentEntry.isEmpty()) {
                                // get top level content only
                                int firstSlash = contentEntry.indexOf(pathSeparator);
                                if (firstSlash != -1) {
                                    content.add(contentEntry.substring(0, firstSlash));
                                } else {
                                    content.add(contentEntry);
                                }
                            }
                        }
                    }

                }
            } else {
                try (Stream<Path> contentStream = Files.list(Path.of(path))) {
                    content = new TreeSet<>(contentStream
                                    .map(Path::getFileName)
                                    .map(Path::toString)
                                    .toList());
                }
            }

            return String.join(System.lineSeparator(), content);
        }
    }

    @Override
    public void afterRegistration(AfterRegistrationAccess a) {
        FeatureImpl.AfterRegistrationAccessImpl access = (FeatureImpl.AfterRegistrationAccessImpl) a;
        imageClassLoader = access.getImageClassLoader();
        ResourcesRegistryImpl resourcesRegistry = new ResourcesRegistryImpl(new ConfigurationTypeResolver("resource configuration", imageClassLoader));
        ImageSingletons.add(ResourcesRegistry.class, resourcesRegistry);
        ImageSingletons.add(RuntimeResourceSupport.class, resourcesRegistry);
    }

    private static ResourcesRegistryImpl resourceRegistryImpl() {
        return (ResourcesRegistryImpl) ImageSingletons.lookup(ResourcesRegistry.class);
    }

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        ResourceConfigurationParser parser = new ResourceConfigurationParser(ImageSingletons.lookup(ResourcesRegistry.class), ConfigurationFiles.Options.StrictConfiguration.getValue());
        loadedConfigurations = ConfigurationParserUtils.parseAndRegisterConfigurations(parser, imageClassLoader, "resource",
                        ConfigurationFiles.Options.ResourceConfigurationFiles, ConfigurationFiles.Options.ResourceConfigurationResources,
                        ConfigurationFile.RESOURCES.getFileName());
        resourcePatternWorkSet.addAll(Options.IncludeResources.getValue()
                        .values()
                        .stream()
                        .map(e -> new ConditionalPattern(ConfigurationCondition.alwaysTrue(), e))
                        .toList());
        excludedResourcePatterns.addAll(Options.ExcludeResources.getValue().values());

        if (!resourcePatternWorkSet.isEmpty()) {
            FeatureImpl.BeforeAnalysisAccessImpl beforeAnalysisAccess = (FeatureImpl.BeforeAnalysisAccessImpl) access;
            Set<CompiledConditionalPattern> includePatterns = resourcePatternWorkSet
                            .stream()
                            .map(e -> new CompiledConditionalPattern(e.condition(), makeResourcePattern(e.pattern())))
                            .collect(Collectors.toSet());
            if (MissingRegistrationUtils.throwMissingRegistrationErrors()) {
                includePatterns.stream()
                                .map(pattern -> pattern.compiledPattern)
                                .forEach(resourcePattern -> {
                                    Resources.singleton().registerIncludePattern(resourcePattern.moduleName, resourcePattern.pattern.pattern());
                                });
            }
            ResourcePattern[] excludePatterns = compilePatterns(excludedResourcePatterns);
            ResourceCollectorImpl collector = new ResourceCollectorImpl(includePatterns, excludePatterns, beforeAnalysisAccess);
            try {
                collector.prepareProgressReporter();
                ImageSingletons.lookup(ClassLoaderSupport.class).collectResources(collector);
            } finally {
                collector.shutDownProgressReporter();
            }

            // We set resourcePatternWorkSet to empty unmodifiable set, so we can be sure that it
            // won't be populated in some later phase
            resourcePatternWorkSet = Set.of();
        }

        resourceRegistryImpl().flushConditionalConfiguration(access);
    }

    private static final class ResourceCollectorImpl implements ResourceCollector {
        private final Set<CompiledConditionalPattern> includePatterns;
        private final ResourcePattern[] excludePatterns;
        private static final int WATCHDOG_RESET_AFTER_EVERY_N_RESOURCES = 1000;
        private static final int WATCHDOG_INITIAL_WARNING_AFTER_N_SECONDS = 60;
        private static final int WATCHDOG_WARNING_AFTER_EVERY_N_SECONDS = 20;
        private final FeatureImpl.BeforeAnalysisAccessImpl access;
        private final LongAdder reachedResourceEntries;
        private boolean initialReport;
        private volatile String currentlyProcessedEntry;
        ScheduledExecutorService scheduledExecutor;

        private ResourceCollectorImpl(Set<CompiledConditionalPattern> includePatterns, ResourcePattern[] excludePatterns, FeatureImpl.BeforeAnalysisAccessImpl access) {
            this.includePatterns = includePatterns;
            this.excludePatterns = excludePatterns;

            this.access = access;
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
        public List<ConfigurationCondition> isIncluded(Module module, String resourceName, URI resource) {
            this.currentlyProcessedEntry = resource.getScheme().equals("jrt") ? (resource + "/" + resourceName) : resource.toString();

            this.reachedResourceEntries.increment();
            if (this.reachedResourceEntries.longValue() % WATCHDOG_RESET_AFTER_EVERY_N_RESOURCES == 0) {
                DeadlockWatchdog.singleton().recordActivity();
            }

            String relativePathWithTrailingSlash = resourceName + RESOURCES_INTERNAL_PATH_SEPARATOR;
            String moduleName = module == null ? null : module.getName();

            for (ResourcePattern rp : excludePatterns) {
                if (!rp.moduleNameMatches(moduleName)) {
                    continue;
                }
                if (rp.pattern.matcher(resourceName).matches() || rp.pattern.matcher(relativePathWithTrailingSlash).matches()) {
                    return List.of(); // nothing should match excluded resource
                }
            }

            // Possibly we can have multiple conditions for one resource
            List<ConfigurationCondition> conditions = new ArrayList<>();
            for (CompiledConditionalPattern rp : includePatterns) {
                if (!rp.compiledPattern().moduleNameMatches(moduleName)) {
                    continue;
                }
                if (rp.compiledPattern().pattern.matcher(resourceName).matches() || rp.compiledPattern().pattern.matcher(relativePathWithTrailingSlash).matches()) {
                    conditions.add(rp.condition());
                }
            }

            return conditions;
        }

        @Override
        public void addResource(Module module, String resourceName) {
            ImageSingletons.lookup(RuntimeResourceSupport.class).addResource(module, resourceName);
        }

        @Override
        public void addResourceConditionally(Module module, String resourceName, ConfigurationCondition condition) {
            access.registerReachabilityHandler(e -> addResource(module, resourceName), access.findClassByName(condition.getTypeName()));
        }

        @Override
        public void registerIOException(Module module, String resourceName, IOException e, boolean linkAtBuildTime) {
            Resources.singleton().registerIOException(module, resourceName, e, linkAtBuildTime);
        }

        @Override
        public void registerNegativeQuery(Module module, String resourceName) {
            Resources.singleton().registerNegativeQuery(module, resourceName);
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
    public void registerInvocationPlugins(Providers providers, SnippetReflectionProvider snippetReflection, GraphBuilderConfiguration.Plugins plugins, ParsingReason reason) {
        if (!reason.duringAnalysis() || reason == ParsingReason.JITCompilation) {
            return;
        }

        Method[] resourceMethods = {
                        ReflectionUtil.lookupMethod(Class.class, "getResource", String.class),
                        ReflectionUtil.lookupMethod(Class.class, "getResourceAsStream", String.class)
        };
        Method resolveResourceName = ReflectionUtil.lookupMethod(Class.class, "resolveName", String.class);

        for (Method method : resourceMethods) {
            registerResourceRegistrationPlugin(plugins.getInvocationPlugins(), method, snippetReflection, resolveResourceName, reason);
        }
    }

    private void registerResourceRegistrationPlugin(InvocationPlugins plugins, Method method, SnippetReflectionProvider snippetReflectionProvider, Method resolveResourceName, ParsingReason reason) {
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
                try {
                    if (!sealed && receiver.isConstant() && arg.isJavaConstant() && !arg.isNullConstant()) {
                        Class<?> clazz = snippetReflectionProvider.asObject(Class.class, receiver.get().asJavaConstant());
                        String resource = snippetReflectionProvider.asObject(String.class, arg.asJavaConstant());
                        String resourceName = (String) resolveResourceName.invoke(clazz, resource);
                        b.add(ReachabilityRegistrationNode.create(() -> RuntimeResourceAccess.addResource(clazz.getModule(), resourceName), reason));
                        return true;
                    }
                } catch (IllegalAccessException | InvocationTargetException e) {
                    throw VMError.shouldNotReachHere(e);
                }
                return false;
            }
        });
    }
}
