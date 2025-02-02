/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.nativeimage;

import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

/**
 * The class loader used to load the Graal and JVMCI classes compiled into libgraal implements this
 * interface to provide extra information about the libgraal classes.
 *
 * @since 25
 */
public interface LibGraalLoader {

    /**
     * Gets the {@code java.home} of the JDK whose runtime image contains the Graal and JVMCI
     * classes from which libgraal will be built.
     */
    Path getJavaHome();

    /**
     * Gets the ClassLoader that should be seen at image runtime if a class was loaded at image
     * build-time by this loader.
     */
    ClassLoader getRuntimeClassLoader();

    /**
     * Gets a map from the {@linkplain Class#forName(String) name} of a class to the name of its
     * enclosing module. There is one entry in the map for each class available for loading by this
     * loader.
     *
     * @return an unmodifiable map
     */
    Map<String, String> getModuleMap();

    /**
     * Gets the names of the modules containing classes that can be annotated by
     * {@code LibGraalService}.
     */
    Set<String> getServicesModules();
}
