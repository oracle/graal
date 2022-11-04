/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2020, 2020, Red Hat Inc. All rights reserved.
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

package com.oracle.svm.hosted.image.sources;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.ConcurrentHashMap;

import org.graalvm.compiler.debug.DebugContext;

import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * A singleton class responsible for locating source files for classes included in a native image
 * and copying them into the local sources cache directory.
 */
public class SourceManager {

    /**
     * Find and cache a source file for a given Java class and return a Path to the file relative to
     * the local sources cache directory.
     * 
     * @param resolvedType the Java type whose source file should be located and cached
     * @param clazz the Java class associated with the resolved type
     * @param debugContext context for logging details of any lookup failure
     * @return a path identifying the location of a successfully cached file for inclusion in the
     *         generated debug info or null if a source file cannot be found or cached.
     */
    public Path findAndCacheSource(ResolvedJavaType resolvedType, Class<?> clazz, DebugContext debugContext) {
        // only instance classes and interfaces have a source file
        if (!resolvedType.isInstanceClass() && !resolvedType.isInterface()) {
            return null;
        }
        // see if we can find a base file name for the class
        String fileName = computeBaseName(resolvedType);
        if (fileName == null) {
            return null;
        }
        String packageName = computePackageName(resolvedType);
        Path path = computePrototypeName(fileName, packageName);

        /* lookup the source and race with other threads to cache it */
        Source source = sourceIndex.computeIfAbsent(path, Source::new);
        if (source.shouldCache()) {
            // ask the source cache to locate the source file
            String moduleName = null;
            if (clazz != null) {
                /* Paths require the module name as prefix */
                moduleName = clazz.getModule().getName();
            }

            cache.resolve(source, moduleName);

            assert !source.isCaching();
        }

        /* cache attempt has been tried - return any associated path */
        return source.getPath();
    }

    /**
     * Construct the base file name for a resolved Java class excluding path elements using either
     * the source name embedded in the class file or the class name itself.
     * 
     * @param resolvedType the resolved java type whose source file name is required
     * @return the file name or null if it the class cannot be associated with a source file
     */
    private static String computeBaseName(ResolvedJavaType resolvedType) {
        if (resolvedType.isPrimitive()) {
            return null;
        }
        String fileName = resolvedType.getSourceFileName();
        if (fileName == null) {
            /* ok, try to construct it from the class name */
            fileName = resolvedType.toJavaName();
            int idx = fileName.lastIndexOf('.');
            if (idx > 0) {
                // strip off package prefix
                fileName = fileName.substring(idx + 1);
            }
            idx = fileName.indexOf('$');
            if (idx == 0) {
                // name is $XXX so cannot associate with a file
                //
                fileName = null;
            } else {
                if (idx > 0) {
                    // name is XXX$YYY so use outer class to derive file name
                    fileName = fileName.substring(0, idx);
                }
                fileName = fileName + ".java";
            }
        }
        return fileName;
    }

    /**
     * Construct the package name for a Java type or the empty String if it has no package.
     * 
     * @param javaType the Java type whose package name is required
     * @return the package name or the empty String if it has no package
     */
    private static String computePackageName(ResolvedJavaType javaType) {
        String name = javaType.toClassName();
        int idx = name.lastIndexOf('.');
        if (idx > 0) {
            return name.substring(0, idx);
        } else {
            return "";
        }
    }

    /**
     * Construct the prototype name for a Java source file which can be used to resolve and cache an
     * actual source file.
     * 
     * @param fileName the base file name for the source file
     * @param packageName the name of the package for the associated Java class
     * @return a protoype name for the source file
     */
    private static Path computePrototypeName(String fileName, String packageName) {
        if (packageName.length() == 0) {
            return Paths.get("", fileName);
        } else {
            return Paths.get("", packageName.split("\\.")).resolve(fileName);
        }
    }

    /**
     * A SourceCache that is responsible for locating and caching source files.
     */
    private SourceCache cache = new SourceCache();

    /**
     * An index recording the status of target files in the source file cache.
     */
    private ConcurrentHashMap<Path, Source> sourceIndex = new ConcurrentHashMap<>();
}
