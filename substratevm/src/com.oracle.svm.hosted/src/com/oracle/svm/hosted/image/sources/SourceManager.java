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
import java.util.HashMap;

import org.graalvm.compiler.debug.DebugContext;

import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * A singleton class responsible for locating source files for classes included in a native image
 * and copying them into the local sources.
 */
public class SourceManager {

    SourceCache cache = new SourceCache();

    /**
     * Find and cache a source file for a given Java class and return a Path to the file relative to
     * the source.
     * 
     * @param resolvedType the Java type whose source file should be located and cached
     * @param clazz the Java class associated with the resolved type
     * @param debugContext context for logging details of any lookup failure
     * @return a path identifying the location of a successfully cached file for inclusion in the
     *         generated debug info or null if a source file cannot be found or cached.
     */
    public Path findAndCacheSource(ResolvedJavaType resolvedType, Class<?> clazz, DebugContext debugContext) {
        /* short circuit if we have already seen this type */
        Path path = verifiedPaths.get(resolvedType);
        if (path != null) {
            return (path != INVALID_PATH ? path : null);
        }

        String fileName = computeBaseName(resolvedType);
        /*
         * null for the name means this class will not have a source so we skip on that
         */
        if (fileName != null) {
            /*
             * we can only provide sources for known classes and interfaces
             */
            if (resolvedType.isInstanceClass() || resolvedType.isInterface()) {
                String packageName = computePackageName(resolvedType);
                path = locateSource(fileName, packageName, clazz);
                if (path == null) {
                    // as a last ditch effort derive path from the Java class name
                    if (debugContext.areScopesEnabled()) {
                        debugContext.log(DebugContext.INFO_LEVEL, "Failed to find source file for class %s\n", resolvedType.toJavaName());
                    }
                    if (packageName.length() > 0) {
                        path = Paths.get("", packageName.split("\\."));
                        path = path.resolve(fileName);
                    }
                }
            }
        }
        /* memoize the lookup */
        verifiedPaths.put(resolvedType, (path != null ? path : INVALID_PATH));

        return path;
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
     * A map from a Java type to an associated source paths which is known to have an up to date
     * entry in the relevant source file cache. This is used to memoize previous lookups.
     */
    private static HashMap<ResolvedJavaType, Path> verifiedPaths = new HashMap<>();

    /**
     * An invalid path used as a marker to track failed lookups so we don't waste time looking up
     * the source again. Note that all legitimate paths will end with a ".java" suffix.
     */
    private static final Path INVALID_PATH = Paths.get("invalid");

    private Path locateSource(String fileName, String packagename, Class<?> clazz) {
        Path prototypeName = computePrototypeName(fileName, packagename);
        if (prototypeName != null) {
            return cache.resolve(prototypeName, clazz);
        } else {
            return null;
        }
    }
}
