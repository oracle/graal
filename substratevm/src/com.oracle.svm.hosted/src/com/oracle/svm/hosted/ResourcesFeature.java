/*
 * Copyright (c) 2015, 2017, Oracle and/or its affiliates. All rights reserved.
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
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Pattern;

import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.options.Option;
import org.graalvm.compiler.options.OptionType;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.hosted.Feature;

import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.configure.ConfigurationFiles;
import com.oracle.svm.core.configure.ResourceConfigurationParser;
import com.oracle.svm.core.configure.ResourcesRegistry;
import com.oracle.svm.core.jdk.LocalizationSupport;
import com.oracle.svm.core.jdk.Resources;
import com.oracle.svm.core.option.HostedOptionKey;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.hosted.FeatureImpl.BeforeAnalysisAccessImpl;
import com.oracle.svm.hosted.FeatureImpl.DuringAnalysisAccessImpl;
import com.oracle.svm.hosted.config.ConfigurationParserUtils;

@AutomaticFeature
public final class ResourcesFeature implements Feature {

    public static class Options {
        @Option(help = "Regexp to match names of resources to be included in the image.", type = OptionType.User)//
        public static final HostedOptionKey<String[]> IncludeResources = new HostedOptionKey<>(new String[0]);
    }

    private boolean sealed = false;
    private Set<String> newResources = Collections.newSetFromMap(new ConcurrentHashMap<>());

    private class ResourcesRegistryImpl implements ResourcesRegistry {
        @Override
        public void addResources(String pattern) {
            UserError.guarantee(!sealed, "Resources added too late");
            newResources.add(pattern);
        }

        @Override
        public void addResourceBundles(String name) {
            ImageSingletons.lookup(LocalizationSupport.class).addBundleToCache(name);
        }
    }

    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        ImageSingletons.add(ResourcesRegistry.class, new ResourcesRegistryImpl());
    }

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        ImageClassLoader imageClassLoader = ((BeforeAnalysisAccessImpl) access).getImageClassLoader();
        ResourceConfigurationParser parser = new ResourceConfigurationParser(ImageSingletons.lookup(ResourcesRegistry.class));
        ConfigurationParserUtils.parseAndRegisterConfigurations(parser, imageClassLoader, "resource",
                        ConfigurationFiles.Options.ResourceConfigurationFiles, ConfigurationFiles.Options.ResourceConfigurationResources,
                        ConfigurationFiles.RESOURCES_NAME);

        newResources.addAll(Arrays.asList(Options.IncludeResources.getValue()));
    }

    @Override
    public void duringAnalysis(DuringAnalysisAccess access) {
        if (newResources.isEmpty()) {
            return;
        }
        access.requireAnalysisIteration();
        for (String regExp : newResources) {
            if (regExp.length() == 0) {
                continue;
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

            Pattern pattern = Pattern.compile(regExp);

            final Set<File> todo = new HashSet<>();
            // Checkstyle: stop
            final ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
            if (contextClassLoader instanceof URLClassLoader) {
                for (URL url : ((URLClassLoader) contextClassLoader).getURLs()) {
                    try {
                        final File file = new File(url.toURI());
                        todo.add(file);
                    } catch (URISyntaxException | IllegalArgumentException e) {
                        throw UserError.abort("Unable to handle imagecp element '" + url.toExternalForm() + "'. Make sure that all imagecp entries are either directories or valid jar files.");
                    }
                }
            }
            // Checkstyle: resume
            for (File element : todo) {
                try {
                    DebugContext debugContext = ((DuringAnalysisAccessImpl) access).getDebugContext();
                    if (element.isDirectory()) {
                        scanDirectory(debugContext, element, "", pattern);
                    } else {
                        scanJar(debugContext, element, pattern);
                    }
                } catch (IOException ex) {
                    throw UserError.abort("Unable to handle classpath element '" + element + "'. Make sure that all classpath entries are either directories or valid jar files.");
                }
            }
        }
        newResources.clear();
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
        if (resourceFallback != null && Options.IncludeResources.getValue().length == 0 &&
                        ConfigurationFiles.Options.ResourceConfigurationFiles.getValue().length == 0 &&
                        ConfigurationFiles.Options.ResourceConfigurationResources.getValue().length == 0) {
            throw resourceFallback;
        }
    }

    @SuppressWarnings("try")
    private void scanDirectory(DebugContext debugContext, File f, String relativePath, Pattern... patterns) throws IOException {
        if (f.isDirectory()) {
            File[] files = f.listFiles();
            if (files == null) {
                throw UserError.abort("Cannot scan directory " + f);
            } else {
                for (File ch : files) {
                    scanDirectory(debugContext, ch, relativePath.isEmpty() ? ch.getName() : relativePath + "/" + ch.getName(), patterns);
                }
            }
        } else {
            if (matches(patterns, relativePath)) {
                try (FileInputStream is = new FileInputStream(f)) {
                    try (DebugContext.Scope s = debugContext.scope("registerResource")) {
                        debugContext.log("ResourcesFeature: registerResource: " + relativePath);
                    }
                    Resources.registerResource(relativePath, is);
                }
            }
        }
    }

    @SuppressWarnings("try")
    private static void scanJar(DebugContext debugContext, File element, Pattern... patterns) throws IOException {
        JarFile jf = new JarFile(element);
        Enumeration<JarEntry> en = jf.entries();
        while (en.hasMoreElements()) {
            JarEntry e = en.nextElement();
            if (e.getName().endsWith("/")) {
                continue;
            }
            if (matches(patterns, e.getName())) {
                try (InputStream is = jf.getInputStream(e)) {
                    try (DebugContext.Scope s = debugContext.scope("registerResource")) {
                        debugContext.log("ResourcesFeature: registerResource: " + e.getName());
                    }
                    Resources.registerResource(e.getName(), is);
                }
            }
        }
    }

    private static boolean matches(Pattern[] patterns, String relativePath) {
        for (Pattern p : patterns) {
            if (p.matcher(relativePath).matches()) {
                return true;
            }
        }
        return false;
    }
}
