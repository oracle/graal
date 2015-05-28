/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.graal.hotspot.loader;

import java.io.*;
import java.net.*;
import java.util.*;

/**
 * Utility to create and register a separate class loader for loading Graal classes (i.e., those in
 * found in jars in lib/graal).
 */
public class Factory {

    /**
     * Copy of the {@code UseGraalClassLoader} VM option. Set by the VM before the static
     * initializer is called.
     */
    private static boolean useGraalClassLoader;

    /**
     * Registers the Graal class loader in the VM.
     */
    private static native void init(ClassLoader loader);

    static {
        init(useGraalClassLoader ? newClassLoader() : null);
    }

    /**
     * Creates a new class loader for loading graal classes.
     */
    private static ClassLoader newClassLoader() {
        URL[] urls = getGraalJarsUrls();
        ClassLoader parent = null;
        return URLClassLoader.newInstance(urls, parent);
    }

    /**
     * Gets the URLs for lib/graal/graal*.jar.
     */
    private static URL[] getGraalJarsUrls() {
        File javaHome = new File(System.getProperty("java.home"));
        File lib = new File(javaHome, "lib");
        File graal = new File(lib, "graal");
        if (!graal.exists()) {
            throw new InternalError(graal + " does not exist");
        }

        List<URL> urls = new ArrayList<>();
        for (String fileName : graal.list()) {
            if (fileName.endsWith(".jar")) {
                File file = new File(graal, fileName);
                if (file.isDirectory()) {
                    continue;
                }
                try {
                    urls.add(file.toURI().toURL());
                } catch (MalformedURLException e) {
                    throw new InternalError(e);
                }
            }
        }

        return urls.toArray(new URL[urls.size()]);
    }
}
