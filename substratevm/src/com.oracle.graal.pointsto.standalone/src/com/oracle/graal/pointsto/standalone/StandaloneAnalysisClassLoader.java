/*
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2022, 2023, Alibaba Group Holding Limited. All rights reserved.
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

package com.oracle.graal.pointsto.standalone;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;

import com.oracle.graal.pointsto.util.AnalysisError;
import com.oracle.svm.common.type.TypeResult;
import com.oracle.svm.common.util.ClassUtils;

public final class StandaloneAnalysisClassLoader extends URLClassLoader {
    private List<String> analysisClassPath;
    private List<String> analysisModulePath;
    private static TemporaryAnalysisDirectoryProviderImpl temporaryAnalysisDirectoryProvider;

    private StandaloneAnalysisClassLoader(List<String> classPath, List<String> modulePath, ClassLoader parent) {
        super(pathToUrl(classPath, modulePath), parent);
        analysisClassPath = classPath;
        analysisModulePath = modulePath;
    }

    public static StandaloneAnalysisClassLoader createNew(String classPath, String modulePath, ClassLoader parent, TemporaryAnalysisDirectoryProviderImpl tempDirProvider) {
        temporaryAnalysisDirectoryProvider = tempDirProvider;
        return new StandaloneAnalysisClassLoader(extractClassPath(classPath), extractClassPath(modulePath), parent);
    }

    public StandaloneAnalysisClassLoader(URL[] urls, ClassLoader parent) {
        super(urls, parent);
    }

    private static List<String> extractClassPath(String paths) {
        return paths == null ? Collections.emptyList()
                        : Arrays.stream(paths.split(File.pathSeparator))
                                        .collect(Collectors.toList());
    }

    public List<String> getClassPath() {
        return analysisClassPath;
    }

    public List<String> getModulePath() {
        return analysisModulePath;
    }

    public Class<?> defineClassFromOtherClassLoader(Class<?> clazz) {
        String classFile = "/" + clazz.getName().replace('.', '/') + ".class";
        InputStream clazzStream = clazz.getResourceAsStream(classFile);
        Class<?> newlyDefinedClass = null;
        if (clazzStream != null) {
            try {
                byte[] classBytes = clazzStream.readAllBytes();
                newlyDefinedClass = defineClass(clazz.getName(), classBytes);
            } catch (IOException e) {
                AnalysisError.shouldNotReachHere(e);
            }
        }
        return newlyDefinedClass;
    }

    public TypeResult<Class<?>> findClass(String name, boolean allowPrimitives) {
        try {
            if (allowPrimitives && name.indexOf('.') == -1) {
                TypeResult<Class<?>> primitiveType = ClassUtils.getPrimitiveTypeByName(name);
                if (primitiveType != null) {
                    return primitiveType;
                }
            }
            return TypeResult.forClass(Class.forName(name, false, this));
        } catch (ClassNotFoundException | LinkageError ex) {
            return TypeResult.forException(name, ex);
        }
    }

    public static URL[] pathToUrl(List<String> paths) {
        return pathToUrl(paths, new ArrayList<>());
    }

    private static URL[] pathToUrl(List<String> classPath, List<String> modulePath) {
        List<URL> urls = new ArrayList<>();
        Map<String, List<String>> fatJarMap = new HashMap<>();
        Stream.concat(classPath.stream(), modulePath.stream())
                        .forEach(cp -> {
                            if (isInFatJar(cp)) {
                                String[] fatJarPaths = cp.split("!");
                                if (fatJarPaths.length != 2) {
                                    AnalysisError.shouldNotReachHere("Fat jar dependency format is not supported.");
                                }
                                String entryName = fatJarPaths[1];
                                if (entryName.startsWith(File.separator)) {
                                    entryName = entryName.substring(1);
                                }
                                fatJarMap.computeIfAbsent(fatJarPaths[0], k -> new ArrayList<>()).add(entryName);
                            } else {
                                File f = new File(cp);
                                if (f.isFile() && cp.endsWith(".jar")) {
                                    try {
                                        JarFile jarFile = new JarFile(f);
                                        Iterator<JarEntry> iterator = jarFile.entries().asIterator();
                                        while (iterator.hasNext()) {
                                            JarEntry entry = iterator.next();
                                            if (!entry.isDirectory() && entry.getName().endsWith(".jar")) {
                                                fatJarMap.computeIfAbsent(cp, k -> new ArrayList<>()).add(entry.getName());
                                            }
                                        }
                                    } catch (IOException e) {
                                        e.printStackTrace();
                                    }
                                }
                                try {
                                    urls.add(f.toURI().toURL());
                                } catch (MalformedURLException e) {
                                    e.printStackTrace();
                                }
                            }
                        });
        Path directory = temporaryAnalysisDirectoryProvider.getTemporaryBuildDirectory();
        // Save the jar files in the fatjar to a temporary directory and get its url
        fatJarMap.forEach((k, v) -> {
            try {
                JarFile jarFile = new JarFile(k);
                File f = new File(k);
                for (String entryName : v) {
                    Path outputClassPath = directory.resolve(f.getName()).resolve(entryName).normalize();
                    try {
                        Path parentDirPath = outputClassPath.getParent();
                        if (parentDirPath == null) {
                            continue;
                        } else {
                            if (!Files.exists(parentDirPath)) {
                                Files.createDirectories(parentDirPath);
                            }
                        }
                    } catch (IOException e) {
                        AnalysisError.shouldNotReachHere(e);
                    }
                    ZipEntry entry = jarFile.getEntry(entryName);
                    byte[] data = jarFile.getInputStream(entry).readAllBytes();
                    try (FileOutputStream outputStream = new FileOutputStream(outputClassPath.toFile())) {
                        outputStream.write(data);
                    } catch (IOException e) {
                        AnalysisError.shouldNotReachHere(e);
                    }
                    urls.add(outputClassPath.toUri().toURL());
                }
            } catch (IOException e) {
                AnalysisError.shouldNotReachHere(e);
            }
        });
        return urls.toArray(new URL[0]);
    }

    public Class<?> defineClass(String name, byte[] data) {
        return defineClass(name, data, 0, data.length);
    }

    private static boolean isInFatJar(String cp) {
        return cp.contains("!") && cp.endsWith(".jar");
    }
}
