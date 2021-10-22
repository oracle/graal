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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.options.Option;
import org.graalvm.compiler.options.OptionType;
import org.graalvm.compiler.serviceprovider.JavaVersionUtil;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.impl.ConfigurationCondition;

import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.configure.ConfigurationFile;
import com.oracle.svm.core.configure.ConfigurationFiles;
import com.oracle.svm.core.configure.ResourceConfigurationParser;
import com.oracle.svm.core.configure.ResourcesRegistry;
import com.oracle.svm.core.jdk.Resources;
import com.oracle.svm.hosted.jdk.localization.LocalizationFeature;
import com.oracle.svm.core.jdk.resources.NativeImageResourceFileAttributes;
import com.oracle.svm.core.jdk.resources.NativeImageResourceFileAttributesView;
import com.oracle.svm.core.jdk.resources.NativeImageResourceFileSystem;
import com.oracle.svm.core.jdk.resources.NativeImageResourceFileSystemProvider;
import com.oracle.svm.core.option.HostedOptionKey;
import com.oracle.svm.core.option.LocatableMultiOptionValue;
import com.oracle.svm.core.util.ClasspathUtils;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.FeatureImpl.BeforeAnalysisAccessImpl;
import com.oracle.svm.hosted.FeatureImpl.DuringAnalysisAccessImpl;
import com.oracle.svm.hosted.config.ConfigurationParserUtils;
import com.oracle.svm.util.ModuleSupport;

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
        ImageClassLoader imageClassLoader = access.getImageClassLoader();
        ImageSingletons.add(ResourcesRegistry.class,
                        new ResourcesRegistryImpl(new ConfigurationTypeResolver("resource configuration", imageClassLoader, NativeImageOptions.AllowIncompleteClasspath.getValue())));
    }

    private static ResourcesRegistryImpl resourceRegistryImpl() {
        return (ResourcesRegistryImpl) ImageSingletons.lookup(ResourcesRegistry.class);
    }

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        ImageClassLoader imageClassLoader = ((BeforeAnalysisAccessImpl) access).getImageClassLoader();
        ResourceConfigurationParser parser = new ResourceConfigurationParser(ImageSingletons.lookup(ResourcesRegistry.class), ConfigurationFiles.Options.StrictConfiguration.getValue());
        loadedConfigurations = ConfigurationParserUtils.parseAndRegisterConfigurations(parser, imageClassLoader, "resource",
                        ConfigurationFiles.Options.ResourceConfigurationFiles, ConfigurationFiles.Options.ResourceConfigurationResources,
                        ConfigurationFile.RESOURCES.getFileName());

        resourcePatternWorkSet.addAll(Options.IncludeResources.getValue().values());
        excludedResourcePatterns.addAll(Options.ExcludeResources.getValue().values());
        resourceRegistryImpl().flushConditionalConfiguration(access);
    }

    @Override
    public void duringAnalysis(DuringAnalysisAccess access) {
        resourceRegistryImpl().flushConditionalConfiguration(access);
        if (resourcePatternWorkSet.isEmpty()) {
            return;
        }

        access.requireAnalysisIteration();
        DuringAnalysisAccessImpl accessImpl = (DuringAnalysisAccessImpl) access;
        DebugContext debugContext = accessImpl.getDebugContext();
        final Pattern[] includePatterns = compilePatterns(resourcePatternWorkSet);
        final Pattern[] excludePatterns = compilePatterns(excludedResourcePatterns);

        if (JavaVersionUtil.JAVA_SPEC > 8) {
            try {
                ModuleSupport.findResourcesInModules(name -> matches(includePatterns, excludePatterns, name),
                                (resName, content) -> registerResource(debugContext, resName, content));
            } catch (IOException ex) {
                throw UserError.abort(ex, "Can not read resources from modules. This is possible due to incorrect module path or missing module visibility directives");
            }
        }

        /*
         * Since IncludeResources takes regular expressions it's safer to disallow passing
         * more than one regex with a single IncludeResources option. Note that it's still
         * possible pass multiple IncludeResources regular expressions by passing each as
         * its own IncludeResources option. E.g.
         * @formatter:off
         * -H:IncludeResources=nobel/prizes.json -H:IncludeResources=fields/prizes.json
         * @formatter:on
         */

        ImageClassLoader loader = accessImpl.imageClassLoader;
        Stream.concat(loader.modulepath().stream(), loader.classpath().stream()).distinct().forEach(classpathFile -> {
            try {
                if (Files.isDirectory(classpathFile)) {
                    scanDirectory(debugContext, classpathFile, includePatterns, excludePatterns);
                } else if (ClasspathUtils.isJar(classpathFile)) {
                    scanJar(debugContext, classpathFile, includePatterns, excludePatterns);
                }
            } catch (IOException ex) {
                throw UserError.abort("Unable to handle classpath element '%s'. Make sure that all classpath entries are either directories or valid jar files.", classpathFile);
            }
        });

        resourcePatternWorkSet.clear();
    }

    private static Pattern[] compilePatterns(Set<String> patterns) {
        return patterns.stream()
                        .filter(s -> s.length() > 0)
                        .map(Pattern::compile)
                        .collect(Collectors.toList())
                        .toArray(new Pattern[]{});
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

    private static void scanDirectory(DebugContext debugContext, Path root, Pattern[] includePatterns, Pattern[] excludePatterns) throws IOException {
        Map<String, List<String>> matchedDirectoryResources = new HashMap<>();
        Set<String> allEntries = new HashSet<>();

        ArrayDeque<Path> queue = new ArrayDeque<>();
        queue.push(root);
        while (!queue.isEmpty()) {
            Path entry = queue.pop();

            /* Resources always use / as the separator, as do our resource inclusion patterns */
            String relativeFilePath;
            if (entry != root) {
                relativeFilePath = root.relativize(entry).toString().replace(File.separatorChar, RESOURCES_INTERNAL_PATH_SEPARATOR);
                allEntries.add(relativeFilePath);
            } else {
                relativeFilePath = "";
            }

            if (Files.isDirectory(entry)) {
                if (matches(includePatterns, excludePatterns, relativeFilePath)) {
                    matchedDirectoryResources.put(relativeFilePath, new ArrayList<>());
                }
                try (Stream<Path> files = Files.list(entry)) {
                    files.forEach(queue::push);
                }
            } else {
                if (matches(includePatterns, excludePatterns, relativeFilePath)) {
                    try (InputStream is = Files.newInputStream(entry)) {
                        registerResource(debugContext, relativeFilePath, is);
                    }
                }
            }
        }

        for (String entry : allEntries) {
            int last = entry.lastIndexOf(RESOURCES_INTERNAL_PATH_SEPARATOR);
            String key = last == -1 ? "" : entry.substring(0, last);
            List<String> dirContent = matchedDirectoryResources.get(key);
            if (dirContent != null && !dirContent.contains(entry)) {
                dirContent.add(entry.substring(last + 1));
            }
        }

        matchedDirectoryResources.forEach((dir, content) -> {
            content.sort(Comparator.naturalOrder());
            registerDirectoryResource(debugContext, dir, String.join(System.lineSeparator(), content));
        });
    }

    private static void scanJar(DebugContext debugContext, Path jarPath, Pattern[] includePatterns, Pattern[] excludePatterns) throws IOException {
        try (JarFile jf = new JarFile(jarPath.toFile())) {
            Enumeration<JarEntry> entries = jf.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (entry.isDirectory()) {
                    String dirName = entry.getName().substring(0, entry.getName().length() - 1);
                    if (matches(includePatterns, excludePatterns, dirName)) {
                        // Register the directory with empty content to preserve Java behavior
                        registerDirectoryResource(debugContext, dirName, "");
                    }
                } else {
                    if (matches(includePatterns, excludePatterns, entry.getName())) {
                        try (InputStream is = jf.getInputStream(entry)) {
                            registerResource(debugContext, entry.getName(), is);
                        }
                    }
                }
            }
        }
    }

    private static boolean matches(Pattern[] includePatterns, Pattern[] excludePatterns, String relativePath) {
        VMError.guarantee(!relativePath.contains("\\"), "Resource path contains backslash!");
        String relativePathWithTrailingSlash = relativePath + RESOURCES_INTERNAL_PATH_SEPARATOR;
        for (Pattern p : excludePatterns) {
            if (p.matcher(relativePath).matches() || p.matcher(relativePathWithTrailingSlash).matches()) {
                return false;
            }
        }

        for (Pattern p : includePatterns) {
            if (p.matcher(relativePath).matches() || p.matcher(relativePathWithTrailingSlash).matches()) {
                return true;
            }
        }

        return false;
    }

    @SuppressWarnings("try")
    private static void registerResource(DebugContext debugContext, String resourceName, InputStream resourceStream) {
        try (DebugContext.Scope s = debugContext.scope("registerResource")) {
            debugContext.log(DebugContext.VERBOSE_LEVEL, "ResourcesFeature: registerResource: " + resourceName);
            Resources.registerResource(resourceName, resourceStream);
        }
    }

    @SuppressWarnings("try")
    private static void registerDirectoryResource(DebugContext debugContext, String dir, String content) {
        try (DebugContext.Scope s = debugContext.scope("registerResource")) {
            debugContext.log(DebugContext.VERBOSE_LEVEL, "ResourcesFeature: registerResource: " + dir);
            Resources.registerDirectoryResource(dir, content);
        }
    }
}
