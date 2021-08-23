/*
 * Copyright (c) 2015, 2020, Oracle and/or its affiliates. All rights reserved.
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

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.FileSystem;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.oracle.svm.core.util.ClasspathUtils;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.options.Option;
import org.graalvm.compiler.options.OptionType;
import org.graalvm.compiler.serviceprovider.JavaVersionUtil;
import org.graalvm.home.HomeFinder;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.hosted.Feature;

import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.configure.ConfigurationFile;
import com.oracle.svm.core.configure.ConfigurationFiles;
import com.oracle.svm.core.configure.ResourceConfigurationParser;
import com.oracle.svm.core.configure.ResourcesRegistry;
import com.oracle.svm.core.jdk.Resources;
import com.oracle.svm.core.jdk.localization.LocalizationFeature;
import com.oracle.svm.core.jdk.resources.NativeImageResourceFileAttributes;
import com.oracle.svm.core.jdk.resources.NativeImageResourceFileAttributesView;
import com.oracle.svm.core.jdk.resources.NativeImageResourceFileSystem;
import com.oracle.svm.core.jdk.resources.NativeImageResourceFileSystemProvider;
import com.oracle.svm.core.option.HostedOptionKey;
import com.oracle.svm.core.option.LocatableMultiOptionValue;
import com.oracle.svm.core.util.UserError;
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

    private class ResourcesRegistryImpl implements ResourcesRegistry {
        @Override
        public void addResources(String pattern) {
            UserError.guarantee(!sealed, "Resources added too late: %s", pattern);
            resourcePatternWorkSet.add(pattern);
        }

        @Override
        public void ignoreResources(String pattern) {
            UserError.guarantee(!sealed, "Resources ignored too late: %s", pattern);
            excludedResourcePatterns.add(pattern);
        }

        @Override
        public void addResourceBundles(String name) {
            ImageSingletons.lookup(LocalizationFeature.class).prepareBundle(name);
        }
    }

    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        ImageSingletons.add(ResourcesRegistry.class, new ResourcesRegistryImpl());
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
    }

    @Override
    public void duringAnalysis(DuringAnalysisAccess access) {
        if (resourcePatternWorkSet.isEmpty()) {
            return;
        }

        access.requireAnalysisIteration();
        DebugContext debugContext = ((DuringAnalysisAccessImpl) access).getDebugContext();
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

        final ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
        final LinkedHashSet<File> userClasspathFiles = new LinkedHashSet<>();
        final LinkedHashSet<File> supportLibraries = new LinkedHashSet<>();
        String homeFolder = HomeFinder.getInstance().getHomeFolder().toString();
        if (contextClassLoader instanceof URLClassLoader) {
            for (URL url : ((URLClassLoader) contextClassLoader).getURLs()) {
                try {
                    final File file = new File(url.toURI());
                    // Make sure the user resources are the first to be registered.
                    if (file.getAbsolutePath().startsWith(homeFolder)) {
                        supportLibraries.add(file);
                    } else {
                        userClasspathFiles.add(file);
                    }
                } catch (URISyntaxException | IllegalArgumentException e) {
                    throw UserError.abort("Unable to handle image classpath element '%s'. Make sure that all image classpath entries are either directories or valid jar files.", url.toExternalForm());
                }
            }
        }

        userClasspathFiles.addAll(supportLibraries);
        for (File classpathFile : userClasspathFiles) {
            try {
                if (classpathFile.isDirectory()) {
                    scanDirectory(debugContext, classpathFile, includePatterns, excludePatterns);
                } else if (ClasspathUtils.isJar(classpathFile.toPath())) {
                    scanJar(debugContext, classpathFile, includePatterns, excludePatterns);
                }
            } catch (IOException ex) {
                throw UserError.abort("Unable to handle classpath element '%s'. Make sure that all classpath entries are either directories or valid jar files.", classpathFile);
            }
        }

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

    private static void scanDirectory(DebugContext debugContext, File root, Pattern[] includePatterns, Pattern[] excludePatterns) throws IOException {
        Map<String, List<String>> matchedDirectoryResources = new HashMap<>();
        Set<String> allEntries = new HashSet<>();
        ArrayList<File> queue = new ArrayList<>();

        queue.add(root);
        while (!queue.isEmpty()) {
            File file = queue.remove(0);
            String relativeFilePath = "";
            if (file != root) {
                relativeFilePath = file.getAbsolutePath().substring(root.getAbsolutePath().length() + 1);
                /*
                 * Java resources always use / as the path separator, as do our resource inclusion
                 * patterns.
                 */
                relativeFilePath = relativeFilePath.replace(File.separatorChar, '/');
            }
            if (file.isDirectory()) {
                if (!relativeFilePath.isEmpty()) {
                    allEntries.add(relativeFilePath);
                }
                if (matches(includePatterns, excludePatterns, relativeFilePath)) {
                    matchedDirectoryResources.put(relativeFilePath, new ArrayList<>());
                }
                File[] files = file.listFiles();
                if (files == null) {
                    throw UserError.abort("Cannot scan directory %s", file);
                }
                queue.addAll(Arrays.asList(files));
            } else {
                allEntries.add(relativeFilePath);
                if (matches(includePatterns, excludePatterns, relativeFilePath)) {
                    try (InputStream is = new FileInputStream(file)) {
                        registerResource(debugContext, relativeFilePath, is);
                    }
                }
            }
        }

        for (String entry : allEntries) {
            int last = entry.lastIndexOf('/');
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

    private static void scanJar(DebugContext debugContext, File root, Pattern[] includePatterns, Pattern[] excludePatterns) throws IOException {
        JarFile jf = new JarFile(root);
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

    private static boolean matches(Pattern[] includePatterns, Pattern[] excludePatterns, String relativePath) {
        for (Pattern p : excludePatterns) {
            if (p.matcher(relativePath).matches()) {
                return false;
            }
        }

        for (Pattern p : includePatterns) {
            if (p.matcher(relativePath).matches()) {
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
