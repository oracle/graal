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

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.Callable;

import com.oracle.truffle.api.impl.TruffleLocator;
import java.util.Collection;

public final class GraalVMLocator extends TruffleLocator
                implements Callable<ClassLoader> {

    private static final boolean LOCATOR_TRACE = Boolean.valueOf(System.getProperty("truffle.class.path.trace", "false"));

    private static URLClassLoader loader;

    public GraalVMLocator() {
    }

    public static ClassLoader getLanguagesLoader() {
        if (loader == null) {
            File home = new File(System.getProperty("java.home"));
            if (!home.exists()) {
                throw new AssertionError("Java home is not reachable.");
            }

            if (System.getProperty("graalvm.home") == null) {
                // automatically set graalvm.home
                System.setProperty("graalvm.home", home.getParentFile().getAbsolutePath());
            }
            if (System.getProperty("org.graalvm.home") == null) {
                // automatically set graalvm.home
                System.setProperty("org.graalvm.home", home.getParentFile().getAbsolutePath());
            }

            File releaseFile = new File(home, "release");
            File jre = new File(home, "jre");
            if (jre.exists()) {
                home = jre;
            } else {
                releaseFile = new File(new File(home, ".."), "release");
            }

            String version = "snapshot";
            if (releaseFile.exists()) {
                Properties properties = new Properties();
                try {
                    properties.load(new FileInputStream(releaseFile));
                    Object loadedVersion = properties.get("GRAALVM_VERSION");
                    if (loadedVersion != null) {
                        version = loadedVersion.toString();
                        if (version.startsWith("\"")) {
                            version = version.substring(1, version.length());
                        }
                        if (version.endsWith("\"")) {
                            version = version.substring(0, version.length() - 1);
                        }
                    }
                } catch (IOException e) {
                }
            }
            System.setProperty("graalvm.version", version);
            System.setProperty("org.graalvm.version", version);

            List<URL> classPath = new ArrayList<>();
            Collection<File> homeFolders = new ArrayList<>();
            collectLanguageJars(new File(home, "languages"), classPath, homeFolders);
            collectLanguageJars(new File(home, "tools"), classPath, homeFolders);

            for (File homeFolder : homeFolders) {
                final String homeFolderKey = homeFolder.getName() + ".home";
                if (System.getProperty(homeFolderKey) == null) {
                    System.setProperty(homeFolderKey, homeFolder.getAbsolutePath());
                }
            }

            String append = System.getProperty("truffle.class.path.append");
            if (append != null) {
                String[] files = append.split(System.getProperty("path.separator"));
                for (String file : files) {
                    addJarOrDir(classPath, new File(file));
                }
            }
            if (LOCATOR_TRACE) {
                System.out.println("Setting up Truffle GuestLanguageTools classpath:");
                for (URL url : classPath) {
                    System.out.println(url);
                }
            }
            loader = new GuestLangToolsLoader(classPath.toArray(new URL[0]), GraalVMLocator.class.getClassLoader());
        }
        return loader;
    }

    private static class GuestLangToolsLoader extends URLClassLoader {

        GuestLangToolsLoader(URL[] urls, ClassLoader parent) {
            super(urls, parent);
        }

    }

    private static void collectLanguageJars(File languages, List<URL> classPath, Collection<? super File> homeFolders) {
        if (languages.exists()) {
            for (File language : languages.listFiles()) {
                if (language.getName().startsWith(".")) {
                    continue;
                }
                if (language.isDirectory()) {
                    homeFolders.add(language);
                    for (File jar : language.listFiles()) {
                        addJar(classPath, jar);
                    }
                } else {
                    addJar(classPath, language);
                }
            }
        }
    }

    private static void addJarOrDir(List<URL> classPath, File file) {
        if (file.isDirectory()) {
            try {
                classPath.add(file.toURI().toURL());
            } catch (MalformedURLException ex) {
                throw new IllegalStateException(ex);
            }
        } else {
            addJar(classPath, file);
        }
    }

    private static void addJar(List<URL> classPath, File jar) {
        if (jar.exists() && jar.getName().endsWith(".jar")) {
            try {
                classPath.add(jar.toURI().toURL());
            } catch (MalformedURLException ex) {
                throw new IllegalStateException(ex);
            }
        }
    }

    @Override
    public void locate(Response response) {
        if (!"true".equals(System.getProperty("graalvm.locatorDisabled"))) {
            response.registerClassLoader(getLanguagesLoader());
        }
    }

    @Override
    public ClassLoader call() throws Exception {
        return getLanguagesLoader();
    }
}
