/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.polyglot;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;

/**
 * Utility for creating a {@link ClassLoader} to access resources in JDK9.
 *
 * The {@code java.lang.reflect.Module} class introduced in JDK9 provides <a href=
 * "http://download.java.net/java/jdk9/docs/api/java/lang/reflect/Module.html#getResourceAsStream-java.lang.String-">
 * getResourceAsStream</a> for accessing resources in a module. This means at most one resource for
 * a given name can be retrieved which is problematic when multiple such resources are available on
 * paths patched (via the {@code -Xpatch} VM option) into the module. Since Truffle relies upon
 * {@link ClassLoader#getResources(String)} to access all resources of a given name, this utility
 * creates a {@link URLClassLoader} to access all resources on the standard class path as well as on
 * the module path including patched paths.
 *
 * Note that since Jigsaw is still under development with respect accessing resources in modules,
 * this mechanism in this utility is subject to change.
 *
 * @see "https://bugs.openjdk.java.net/browse/JDK-8149784"
 * @see "http://openjdk.java.net/projects/jigsaw/spec/issues/#ResourceEncapsulation"
 */
final class ModuleResourceLocator {

    /**
     * Creates a class loader than can access all resources in all modules on the module path.
     */
    static ClassLoader createLoader() {
        List<URL> urls = new ArrayList<>();
        for (String name : new String[]{"java.class.path", "jdk.module.path"}) {
            String value = System.getProperty(name);
            if (value != null) {
                addURLsFromPath(urls, value);
            }
        }
        for (int i = 0; addPatchPaths(urls, i); i++) {
        }
        return new URLClassLoader(urls.toArray(new URL[urls.size()]));
    }

    /**
     * Adds URLs to {@code urls} for each entry in {@code paths}.
     *
     * @param paths a class path like string containing path separated by {@link File#pathSeparator}
     */
    private static void addURLsFromPath(List<URL> urls, String paths) {
        for (String path : paths.split(File.pathSeparator)) {
            try {
                urls.add(new File(path).toURI().toURL());
            } catch (MalformedURLException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Adds URLs to {@code urls} for the patch path specified in the system property named
     * {@code "jdk.launcher.patch." + i}.
     *
     * @return whether the system property named {@code "jdk.launcher.patch." + i} was defined
     */
    private static boolean addPatchPaths(List<URL> urls, int i) {
        String patchPropertyName = "jdk.launcher.patch." + i;
        String patchProperty = System.getProperty(patchPropertyName);
        if (patchProperty != null) {
            int eq = patchProperty.indexOf('=');
            addURLsFromPath(urls, patchProperty.substring(eq + 1));
            return true;
        }
        return false;
    }
}
