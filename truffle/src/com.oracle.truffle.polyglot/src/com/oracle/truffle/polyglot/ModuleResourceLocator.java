/*
 * Copyright (c) 2016, 2018, Oracle and/or its affiliates. All rights reserved.
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
