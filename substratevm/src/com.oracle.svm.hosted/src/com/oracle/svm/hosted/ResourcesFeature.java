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

import java.io.InputStream;
import java.nio.file.FileSystem;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.options.Option;
import org.graalvm.compiler.options.OptionType;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.impl.ConfigurationCondition;

import com.oracle.svm.core.ClassLoaderSupport;
import com.oracle.svm.core.ClassLoaderSupport.ResourceCollector;
import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.configure.ConfigurationFile;
import com.oracle.svm.core.configure.ConfigurationFiles;
import com.oracle.svm.core.configure.ResourceConfigurationParser;
import com.oracle.svm.core.configure.ResourcesRegistry;
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
@AutomaticFeature
public final class ResourcesFeature implements Feature {

    public static class Options {
        @Option(help = "Regexp to match names of resources to be included in the image.", type = OptionType.User)//
        public static final HostedOptionKey<LocatableMultiOptionValue.Strings> IncludeResources = new HostedOptionKey<>(new LocatableMultiOptionValue.Strings());

        @Option(help = "Regexp to match names of resources to be excluded from the image.", type = OptionType.User)//
        public static final HostedOptionKey<LocatableMultiOptionValue.Strings> ExcludeResources = new HostedOptionKey<>(new LocatableMultiOptionValue.Strings());
    }

    private boolean sealed = false;
    private final Set<String> resourcePatternWorkSet = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Set<String> excludedResourcePatterns = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private int loadedConfigurations;
    private ImageClassLoader imageClassLoader;

    public final Set<String> includedResourcesModules = new HashSet<>();

    private class ResourcesRegistryImpl extends ConditionalConfigurationRegistry implements ResourcesRegistry {
        private ConfigurationTypeResolver configurationTypeResolver;

        ResourcesRegistryImpl(ConfigurationTypeResolver configurationTypeResolver) {
            this.configurationTypeResolver = configurationTypeResolver;
        }

        @Override
        public void addResources(ConfigurationCondition condition, String pattern) {
            if (configurationTypeResolver.resolveType(condition.getTypeName()) == null) {
                return;
            }
            registerConditionalConfiguration(condition, () -> {
                UserError.guarantee(!sealed, "Resources added too late: %s", pattern);
                resourcePatternWorkSet.add(pattern);
            });
        }

        @Override
        public void ignoreResources(ConfigurationCondition condition, String pattern) {
            if (configurationTypeResolver.resolveType(condition.getTypeName()) == null) {
                return;
            }
            registerConditionalConfiguration(condition, () -> {
                UserError.guarantee(!sealed, "Resources ignored too late: %s", pattern);

                excludedResourcePatterns.add(pattern);
            });
        }

        @Override
        public void addResourceBundles(ConfigurationCondition condition, String name) {
            if (configurationTypeResolver.resolveType(condition.getTypeName()) == null) {
                return;
            }
            registerConditionalConfiguration(condition, () -> ImageSingletons.lookup(LocalizationFeature.class).prepareBundle(name));
        }

        @Override
        public void addClassBasedResourceBundle(ConfigurationCondition condition, String basename, String className) {
            if (configurationTypeResolver.resolveType(condition.getTypeName()) == null) {
                return;
            }
            registerConditionalConfiguration(condition, () -> ImageSingletons.lookup(LocalizationFeature.class).prepareClassResourceBundle(basename, className));
        }

        @Override
        public void addResourceBundles(ConfigurationCondition condition, String basename, Collection<Locale> locales) {
            if (configurationTypeResolver.resolveType(condition.getTypeName()) == null) {
                return;
            }
            registerConditionalConfiguration(condition, () -> ImageSingletons.lookup(LocalizationFeature.class).prepareBundle(basename, locales));
        }
    }

    @Override
    public void afterRegistration(AfterRegistrationAccess a) {
        FeatureImpl.AfterRegistrationAccessImpl access = (FeatureImpl.AfterRegistrationAccessImpl) a;
        imageClassLoader = access.getImageClassLoader();
        ImageSingletons.add(ResourcesRegistry.class,
                        new ResourcesRegistryImpl(new ConfigurationTypeResolver("resource configuration", imageClassLoader, NativeImageOptions.AllowIncompleteClasspath.getValue())));
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

        resourcePatternWorkSet.addAll(Options.IncludeResources.getValue().values());
        excludedResourcePatterns.addAll(Options.ExcludeResources.getValue().values());
        resourceRegistryImpl().flushConditionalConfiguration(access);
    }

    private static class ResourceCollectorImpl implements ResourceCollector {
        private final DebugContext debugContext;
        private final ResourcePattern[] includePatterns;
        private final ResourcePattern[] excludePatterns;
        private final Set<String> includedResourcesModules;

        private ResourceCollectorImpl(DebugContext debugContext, ResourcePattern[] includePatterns, ResourcePattern[] excludePatterns, Set<String> includedResourcesModules) {
            this.debugContext = debugContext;
            this.includePatterns = includePatterns;
            this.excludePatterns = excludePatterns;
            this.includedResourcesModules = includedResourcesModules;
        }

        @Override
        public boolean isIncluded(String moduleName, String resourceName) {
            VMError.guarantee(!resourceName.contains("\\"), "Resource path contains backslash!");
            String relativePathWithTrailingSlash = resourceName + RESOURCES_INTERNAL_PATH_SEPARATOR;

            for (ResourcePattern rp : excludePatterns) {
                if (rp.moduleName != null && !rp.moduleName.equals(moduleName)) {
                    continue;
                }
                if (rp.pattern.matcher(resourceName).matches() || rp.pattern.matcher(relativePathWithTrailingSlash).matches()) {
                    return false;
                }
            }

            for (ResourcePattern rp : includePatterns) {
                if (rp.moduleName != null && !rp.moduleName.equals(moduleName)) {
                    continue;
                }
                if (rp.pattern.matcher(resourceName).matches() || rp.pattern.matcher(relativePathWithTrailingSlash).matches()) {
                    return true;
                }
            }

            return false;
        }

        @Override
        public void addResource(String moduleName, String resourceName, InputStream resourceStream) {
            collectModuleName(moduleName);
            registerResource(debugContext, moduleName, resourceName, resourceStream);
        }

        @Override
        public void addDirectoryResource(String moduleName, String dir, String content) {
            collectModuleName(moduleName);
            registerDirectoryResource(debugContext, moduleName, dir, content);
        }

        private void collectModuleName(String moduleName) {
            if (moduleName != null) {
                includedResourcesModules.add(moduleName);
            }
        }
    }

    @Override
    public void duringAnalysis(DuringAnalysisAccess access) {
        resourceRegistryImpl().flushConditionalConfiguration(access);
        if (resourcePatternWorkSet.isEmpty()) {
            return;
        }

        access.requireAnalysisIteration();

        DebugContext debugContext = ((DuringAnalysisAccessImpl) access).getDebugContext();
        ResourcePattern[] includePatterns = compilePatterns(resourcePatternWorkSet);
        ResourcePattern[] excludePatterns = compilePatterns(excludedResourcePatterns);
        ResourceCollectorImpl collector = new ResourceCollectorImpl(debugContext, includePatterns, excludePatterns, includedResourcesModules);

        ImageSingletons.lookup(ClassLoaderSupport.class).collectResources(collector);

        resourcePatternWorkSet.clear();
    }

    private ResourcePattern[] compilePatterns(Set<String> patterns) {
        return patterns.stream()
                        .filter(s -> s.length() > 0)
                        .map(this::makeResourcePattern)
                        .collect(Collectors.toList())
                        .toArray(new ResourcePattern[]{});
    }

    private ResourcePattern makeResourcePattern(String rawPattern) {
        String[] moduleNameWithPattern = SubstrateUtil.split(rawPattern, ":", 2);
        if (moduleNameWithPattern.length < 2) {
            return new ResourcePattern(null, Pattern.compile(moduleNameWithPattern[0]));
        } else {
            Optional<? extends Object> optModule = imageClassLoader.findModule(moduleNameWithPattern[0]);
            if (optModule.isPresent()) {
                return new ResourcePattern(moduleNameWithPattern[0], Pattern.compile(moduleNameWithPattern[1]));
            } else {
                throw UserError.abort("Resource pattern \"" + rawPattern + "\"s specifies unknown module " + moduleNameWithPattern[0]);
            }
        }
    }

    private static class ResourcePattern {
        final String moduleName;
        final Pattern pattern;

        private ResourcePattern(String moduleName, Pattern pattern) {
            this.moduleName = moduleName;
            this.pattern = pattern;
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
    private static void registerResource(DebugContext debugContext, String moduleName, String resourceName, InputStream resourceStream) {
        try (DebugContext.Scope s = debugContext.scope("registerResource")) {
            debugContext.log(DebugContext.VERBOSE_LEVEL, "ResourcesFeature: registerResource: " + resourceName);
            Resources.registerResource(moduleName, resourceName, resourceStream);
        }
    }

    @SuppressWarnings("try")
    private static void registerDirectoryResource(DebugContext debugContext, String moduleName, String dir, String content) {
        try (DebugContext.Scope s = debugContext.scope("registerResource")) {
            debugContext.log(DebugContext.VERBOSE_LEVEL, "ResourcesFeature: registerResource: " + dir);
            Resources.registerDirectoryResource(moduleName, dir, content);
        }
    }
}
