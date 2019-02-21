/*
 * Copyright (c) 2013, 2016, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graalvm.locator;

import com.oracle.truffle.api.TruffleOptions;
import com.oracle.truffle.api.impl.HomeFinder;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

import com.oracle.truffle.api.impl.TruffleLocator;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

public final class GraalVMLocator extends TruffleLocator
                implements Callable<ClassLoader> {

    private static final boolean LOCATOR_TRACE = Boolean.valueOf(System.getProperty("truffle.class.path.trace", "false"));

    private static URLClassLoader loader;

    public GraalVMLocator() {
    }

    private static void setGraalVMProperties(HomeFinder homeFinder) {
        Path homePath = homeFinder.getHomeFolder();
        if (homePath != null) {
            String home = homePath.toString();
            if (System.getProperty("graalvm.home") == null) {
                // automatically set graalvm.home
                System.setProperty("graalvm.home", home);
            }
            if (System.getProperty("org.graalvm.home") == null) {
                // automatically set graalvm.home
                System.setProperty("org.graalvm.home", home);
            }
        }
        String version = homeFinder.getVersion();
        System.setProperty("graalvm.version", version);
        System.setProperty("org.graalvm.version", version);
        for (Map.Entry<String, Path> languageHome : homeFinder.getLanguageHomes().entrySet()) {
            setLanguageHomeProperty(languageHome.getKey(), languageHome.getValue());
        }
        for (Map.Entry<String, Path> toolHome : homeFinder.getToolHomes().entrySet()) {
            setLanguageHomeProperty(toolHome.getKey(), toolHome.getValue());
        }
    }

    private static void setLanguageHomeProperty(String languageId, Path languageLocation) {
        if (Files.isDirectory(languageLocation)) {
            final String homeFolderKey = languageId + ".home";
            if (System.getProperty(homeFolderKey) == null) {
                System.setProperty(homeFolderKey, languageLocation.toString());
            }
        }
    }

    private static List<URL> collectClassPath(HomeFinder homeFinder) {
        List<URL> classPath = new ArrayList<>();
        collectLanguageJars(homeFinder.getLanguageHomes(), classPath);
        collectLanguageJars(homeFinder.getToolHomes(), classPath);

        String append = System.getProperty("truffle.class.path.append");
        if (append != null) {
            String[] files = append.split(System.getProperty("path.separator"));
            for (String file : files) {
                addJarOrDir(classPath, Paths.get(file));
            }
        }
        if (LOCATOR_TRACE) {
            System.out.println("Setting up Truffle GuestLanguageTools classpath:");
            for (URL url : classPath) {
                System.out.println(url);
            }
        }
        return classPath;
    }

    public static ClassLoader getLanguagesLoader() {
        if (loader == null) {
            HomeFinder homeFinder = HomeFinder.getInstance();
            if (homeFinder == null) {
                throw new IllegalStateException("No HomeFinder instance.");
            }
            setGraalVMProperties(homeFinder);
            if (!TruffleOptions.AOT) {
                final List<URL> classPath = collectClassPath(homeFinder);
                loader = new GuestLangToolsLoader(classPath.toArray(new URL[0]), GraalVMLocator.class.getClassLoader());
            }
        }
        return loader;
    }

    private static class GuestLangToolsLoader extends URLClassLoader {

        GuestLangToolsLoader(URL[] urls, ClassLoader parent) {
            super(urls, parent);
        }

    }

    private static void collectLanguageJars(Map<String, Path> homes, List<URL> classPath) {
        for (Map.Entry<String, Path> languageHome : homes.entrySet()) {
            final Path languageLocation = languageHome.getValue();
            if (Files.isDirectory(languageLocation)) {
                try (DirectoryStream<Path> dirStream = Files.newDirectoryStream(languageLocation)) {
                    for (Path file : dirStream) {
                        addJar(classPath, file);
                    }
                } catch (IOException ioe) {
                    throw new RuntimeException(ioe);
                }
            } else {
                addJar(classPath, languageLocation);
            }
        }
    }

    private static void addJarOrDir(List<URL> classPath, Path file) {
        if (Files.isDirectory(file)) {
            try {
                classPath.add(file.toUri().toURL());
            } catch (MalformedURLException ex) {
                throw new IllegalStateException(ex);
            }
        } else {
            addJar(classPath, file);
        }
    }

    private static void addJar(List<URL> classPath, Path jar) {
        Path filename = jar.getFileName();
        if (filename != null && filename.toString().endsWith(".jar") && Files.exists(jar)) {
            try {
                classPath.add(jar.toUri().toURL());
            } catch (MalformedURLException ex) {
                throw new IllegalStateException(ex);
            }
        }
    }

    @Override
    public void locate(Response response) {
        if (!"true".equals(System.getProperty("graalvm.locatorDisabled"))) {
            final ClassLoader cl = getLanguagesLoader();
            if (cl != null) {
                response.registerClassLoader(cl);
            }
        }
    }

    @Override
    public ClassLoader call() throws Exception {
        return getLanguagesLoader();
    }
}
