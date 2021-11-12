/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;

import org.graalvm.compiler.serviceprovider.JavaVersionUtil;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.hosted.Feature;

import com.oracle.svm.core.ClassLoaderSupport;
import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.util.ClasspathUtils;
import com.oracle.svm.core.util.UserError;

public class ClassLoaderSupportImpl extends ClassLoaderSupport {

    private final AbstractNativeImageClassLoaderSupport classLoaderSupport;
    private final ClassLoader imageClassLoader;

    protected ClassLoaderSupportImpl(AbstractNativeImageClassLoaderSupport classLoaderSupport) {
        this.classLoaderSupport = classLoaderSupport;
        this.imageClassLoader = classLoaderSupport.getClassLoader();
    }

    @Override
    protected boolean isNativeImageClassLoaderImpl(ClassLoader loader) {
        return loader == imageClassLoader || loader instanceof NativeImageSystemClassLoader;
    }

    @Override
    public void collectResources(ResourceCollector resourceCollector) {
        classLoaderSupport.classpath().stream().forEach(classpathFile -> {
            try {
                if (Files.isDirectory(classpathFile)) {
                    scanDirectory(classpathFile, resourceCollector);
                } else if (ClasspathUtils.isJar(classpathFile)) {
                    scanJar(classpathFile, resourceCollector);
                }
            } catch (IOException ex) {
                throw UserError.abort("Unable to handle classpath element '%s'. Make sure that all classpath entries are either directories or valid jar files.", classpathFile);
            }
        });
    }

    private static void scanDirectory(Path root, ResourceCollector collector) throws IOException {
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
                if (collector.isIncluded(null, relativeFilePath)) {
                    matchedDirectoryResources.put(relativeFilePath, new ArrayList<>());
                }
                try (Stream<Path> files = Files.list(entry)) {
                    files.forEach(queue::push);
                }
            } else {
                if (collector.isIncluded(null, relativeFilePath)) {
                    try (InputStream is = Files.newInputStream(entry)) {
                        collector.addResource(null, relativeFilePath, is);
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
            collector.addDirectoryResource(null, dir, String.join(System.lineSeparator(), content));
        });
    }

    private static void scanJar(Path jarPath, ResourceCollector collector) throws IOException {
        try (JarFile jf = new JarFile(jarPath.toFile())) {
            Enumeration<JarEntry> entries = jf.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (entry.isDirectory()) {
                    String dirName = entry.getName().substring(0, entry.getName().length() - 1);
                    if (collector.isIncluded(null, dirName)) {
                        // Register the directory with empty content to preserve Java behavior
                        collector.addDirectoryResource(null, dirName, "");
                    }
                } else {
                    if (collector.isIncluded(null, entry.getName())) {
                        try (InputStream is = jf.getInputStream(entry)) {
                            collector.addResource(null, entry.getName(), is);
                        }
                    }
                }
            }
        }
    }

    @Override
    public List<ResourceBundle> getResourceBundle(String bundleName, Locale locale) {
        return Collections.singletonList(ResourceBundle.getBundle(bundleName, locale, imageClassLoader));
    }
}

@AutomaticFeature
class ClassLoaderSupportFeature implements Feature {
    @Override
    public boolean isInConfiguration(IsInConfigurationAccess access) {
        return JavaVersionUtil.JAVA_SPEC == 8;
    }

    @Override
    public void afterRegistration(AfterRegistrationAccess a) {
        FeatureImpl.AfterRegistrationAccessImpl access = (FeatureImpl.AfterRegistrationAccessImpl) a;
        ImageSingletons.add(ClassLoaderSupport.class, new ClassLoaderSupportImpl(access.getImageClassLoader().classLoaderSupport));
    }
}
