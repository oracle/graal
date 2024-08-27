/*
 * Copyright (c) 2015, 2021, Oracle and/or its affiliates. All rights reserved.
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
import java.net.URI;
import java.nio.file.FileSystem;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;
import java.util.regex.Pattern;

import org.graalvm.compiler.api.replacements.SnippetReflectionProvider;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugin;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins;
import org.graalvm.compiler.options.Option;
import org.graalvm.compiler.options.OptionType;
import org.graalvm.compiler.phases.util.Providers;
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
import com.oracle.svm.hosted.FeatureImpl.DuringAnalysisAccessImpl;
import com.oracle.svm.hosted.config.ConfigurationParserUtils;
import com.oracle.svm.hosted.jdk.localization.LocalizationFeature;
import com.oracle.svm.util.LogUtils;
import com.oracle.svm.util.ReflectionUtil;

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
        @Option(help = "Regexp to match names of resources to be included in the image.", type = OptionType.User)//
        public static final HostedOptionKey<LocatableMultiOptionValue.Strings> IncludeResources = new HostedOptionKey<>(LocatableMultiOptionValue.Strings.build());

        @Option(help = "Regexp to match names of resources to be excluded from the image.", type = OptionType.User)//
        public static final HostedOptionKey<LocatableMultiOptionValue.Strings> ExcludeResources = new HostedOptionKey<>(LocatableMultiOptionValue.Strings.build());
    }

    private boolean sealed = false;
    private final Set<String> resourcePatternWorkSet = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Set<String> excludedResourcePatterns = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private int loadedConfigurations;
    private ImageClassLoader imageClassLoader;

    private class ResourcesRegistryImpl extends ConditionalConfigurationRegistry implements ResourcesRegistry {
        private final ConfigurationTypeResolver configurationTypeResolver;

        ResourcesRegistryImpl(ConfigurationTypeResolver configurationTypeResolver) {
            this.configurationTypeResolver = configurationTypeResolver;
        }

        @Override
        public void addResources(ConfigurationCondition condition, String pattern) {
            if (configurationTypeResolver.resolveConditionType(condition.getTypeName()) == null) {
                return;
            }
            registerConditionalConfiguration(condition, () -> {
                UserError.guarantee(!sealed, "Resources added too late: %s", pattern);
                resourcePatternWorkSet.add(pattern);
            });
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
        ResourceConfigurationParser parser = ResourceConfigurationParser.create(true, ResourcesRegistry.singleton(),
                        ConfigurationFiles.Options.StrictConfiguration.getValue());
        loadedConfigurations = ConfigurationParserUtils.parseAndRegisterConfigurationsFromCombinedFile(parser, imageClassLoader, "resource");

        ResourceConfigurationParser legacyParser = ResourceConfigurationParser.create(false, ResourcesRegistry.singleton(),
                        ConfigurationFiles.Options.StrictConfiguration.getValue());
        loadedConfigurations += ConfigurationParserUtils.parseAndRegisterConfigurations(legacyParser, imageClassLoader, "resource", ConfigurationFiles.Options.ResourceConfigurationFiles,
                        ConfigurationFiles.Options.ResourceConfigurationResources, ConfigurationFile.RESOURCES.getFileName());

        resourcePatternWorkSet.addAll(Options.IncludeResources.getValue().values());
        excludedResourcePatterns.addAll(Options.ExcludeResources.getValue().values());
        resourceRegistryImpl().flushConditionalConfiguration(access);
    }

    private static final class ResourceCollectorImpl implements ResourceCollector {
        private final DebugContext debugContext;
        private final ResourcePattern[] includePatterns;
        private final ResourcePattern[] excludePatterns;

        private static final int WATCHDOG_RESET_AFTER_EVERY_N_RESOURCES = 1000;
        private static final int WATCHDOG_INITIAL_WARNING_AFTER_N_SECONDS = 60;
        private static final int WATCHDOG_WARNING_AFTER_EVERY_N_SECONDS = 20;
        private final Runnable heartbeatCallback;
        private final LongAdder reachedResourceEntries;
        private boolean initialReport;
        private volatile String currentlyProcessedEntry;
        ScheduledExecutorService scheduledExecutor;

        private ResourceCollectorImpl(DebugContext debugContext, ResourcePattern[] includePatterns, ResourcePattern[] excludePatterns, Runnable heartbeatCallback) {
            this.debugContext = debugContext;
            this.includePatterns = includePatterns;
            this.excludePatterns = excludePatterns;

            this.heartbeatCallback = heartbeatCallback;
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
        public boolean isIncluded(Module module, String resourceName, URI resource) {
            this.currentlyProcessedEntry = resource.getScheme().equals("jrt") ? (resource + "/" + resourceName) : resource.toString();

            this.reachedResourceEntries.increment();
            if (this.reachedResourceEntries.longValue() % WATCHDOG_RESET_AFTER_EVERY_N_RESOURCES == 0) {
                this.heartbeatCallback.run();
            }

            String relativePathWithTrailingSlash = resourceName + RESOURCES_INTERNAL_PATH_SEPARATOR;
            String moduleName = module == null ? null : module.getName();

            for (ResourcePattern rp : excludePatterns) {
                if (!rp.moduleNameMatches(moduleName)) {
                    continue;
                }
                if (rp.pattern.matcher(resourceName).matches() || rp.pattern.matcher(relativePathWithTrailingSlash).matches()) {
                    return false;
                }
            }

            for (ResourcePattern rp : includePatterns) {
                if (!rp.moduleNameMatches(moduleName)) {
                    continue;
                }
                if (rp.pattern.matcher(resourceName).matches() || rp.pattern.matcher(relativePathWithTrailingSlash).matches()) {
                    return true;
                }
            }

            return false;
        }

        @Override
        public void addResource(Module module, String resourceName, InputStream resourceStream, boolean fromJar) {
            registerResource(debugContext, module, resourceName, resourceStream, fromJar);
        }

        @Override
        public void addDirectoryResource(Module module, String dir, String content, boolean fromJar) {
            registerDirectoryResource(debugContext, module, dir, content, fromJar);
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

    @Override
    public void duringAnalysis(DuringAnalysisAccess access) {
        resourceRegistryImpl().flushConditionalConfiguration(access);
        if (resourcePatternWorkSet.isEmpty()) {
            return;
        }

        access.requireAnalysisIteration();

        DuringAnalysisAccessImpl duringAnalysisAccess = ((DuringAnalysisAccessImpl) access);
        ResourcePattern[] includePatterns = compilePatterns(resourcePatternWorkSet);
        if (MissingRegistrationUtils.throwMissingRegistrationErrors()) {
            for (ResourcePattern resourcePattern : includePatterns) {
                Resources.singleton().registerIncludePattern(resourcePattern.moduleName, resourcePattern.pattern.pattern());
            }
        }
        ResourcePattern[] excludePatterns = compilePatterns(excludedResourcePatterns);
        DebugContext debugContext = duringAnalysisAccess.getDebugContext();
        ResourceCollectorImpl collector = new ResourceCollectorImpl(debugContext, includePatterns, excludePatterns, duringAnalysisAccess.bb.getHeartbeatCallback());
        try {
            collector.prepareProgressReporter();
            ImageSingletons.lookup(ClassLoaderSupport.class).collectResources(collector);
        } finally {
            collector.shutDownProgressReporter();
        }
        resourcePatternWorkSet.clear();
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

    private static final class ResourcePattern {
        final String moduleName;
        final Pattern pattern;

        private ResourcePattern(String moduleName, Pattern pattern) {
            this.moduleName = moduleName;
            this.pattern = pattern;
        }

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

    @SuppressWarnings("try")
    private static void registerResource(DebugContext debugContext, Module module, String resourceName, InputStream resourceStream, boolean fromJar) {
        try (DebugContext.Scope s = debugContext.scope("registerResource")) {
            String moduleNamePrefix = module == null ? "" : module.getName() + ":";
            debugContext.log(DebugContext.VERBOSE_LEVEL, "ResourcesFeature: registerResource: %s%s", moduleNamePrefix, resourceName);
            Resources.singleton().registerResource(module, resourceName, resourceStream, fromJar);
        }
    }

    @SuppressWarnings("try")
    private static void registerDirectoryResource(DebugContext debugContext, Module module, String dir, String content, boolean fromJar) {
        try (DebugContext.Scope s = debugContext.scope("registerResource")) {
            String moduleNamePrefix = module == null ? "" : module.getName() + ":";
            debugContext.log(DebugContext.VERBOSE_LEVEL, "ResourcesFeature: registerResource: %s%s", moduleNamePrefix, dir);
            Resources.singleton().registerDirectoryResource(module, dir, content, fromJar);
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
