/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.graalvm.locator;

import java.io.IOException;
import java.io.PrintStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

import org.graalvm.home.HomeFinder;

import com.oracle.truffle.api.TruffleOptions;
import com.oracle.truffle.api.impl.TruffleLocator;

public final class GraalVMLocator extends TruffleLocator
                implements Callable<ClassLoader> {

    private static final boolean LOCATOR_TRACE = Boolean.parseBoolean(System.getProperty("truffle.class.path.trace", "false"));

    private static URLClassLoader loader;

    public GraalVMLocator() {
    }

    private static void setGraalVMProperties(HomeFinder homeFinder) {
        Path homePath = homeFinder.getHomeFolder();
        if (homePath != null) {
            String home = homePath.toString();
            if (System.getProperty("org.graalvm.home") == null) {
                // automatically set org.graalvm.home
                System.setProperty("org.graalvm.home", home);
            }
        }
        String version = homeFinder.getVersion();
        System.setProperty("org.graalvm.version", version);
        for (Map.Entry<String, Path> languageHome : homeFinder.getLanguageHomes().entrySet()) {
            setLanguageHomeProperty(languageHome.getKey(), languageHome.getValue());
        }
        for (Map.Entry<String, Path> toolHome : homeFinder.getToolHomes().entrySet()) {
            setToolHomeProperty(toolHome.getKey(), toolHome.getValue());
        }
    }

    private static void setLanguageHomeProperty(String languageId, Path languageLocation) {
        if (Files.isDirectory(languageLocation)) {
            final String homeFolderKey = "org.graalvm.language." + languageId + ".home";
            if (System.getProperty(homeFolderKey) == null) {
                System.setProperty(homeFolderKey, languageLocation.toString());
            }
            final String legacyHomeFolderKey = languageId + ".home";
            if (System.getProperty(legacyHomeFolderKey) == null) {
                System.setProperty(legacyHomeFolderKey, languageLocation.toString());
            }
        }
    }

    private static void setToolHomeProperty(String toolId, Path toolLocation) {
        if (Files.isDirectory(toolLocation)) {
            final String homeFolderKey = "org.graalvm.tool." + toolId + ".home";
            if (System.getProperty(homeFolderKey) == null) {
                System.setProperty(homeFolderKey, toolLocation.toString());
            }
            final String legacyHomeFolderKey = toolId + ".home";
            if (System.getProperty(legacyHomeFolderKey) == null) {
                System.setProperty(legacyHomeFolderKey, toolLocation.toString());
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
            PrintStream out = System.out;
            out.println("Setting up Truffle GuestLanguageTools classpath:");
            for (URL url : classPath) {
                out.println(url);
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
                loader = new GuestLangToolsLoader(classPath.toArray(new URL[0]), JDKServices.getLocatorBaseClassLoader(GraalVMLocator.class));
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
