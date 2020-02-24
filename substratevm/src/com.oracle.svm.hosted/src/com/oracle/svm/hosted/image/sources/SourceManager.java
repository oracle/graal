/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2020, Red Hat Inc. All rights reserved.
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

import com.oracle.graal.pointsto.infrastructure.OriginalClassProvider;
import com.oracle.svm.util.ModuleSupport;
import jdk.vm.ci.meta.ResolvedJavaType;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;

/**
 * A singleton class responsible for locating source files
 * for classes included in a native image and copying them
 * into the local sources.
 */
public class SourceManager {
    /**
     * Find and cache a source file for a give Java class and
     * return a Path to the file relative to the source.
     * @param resolvedType the Java type whose source file
     * should  be located and cached
     * @return a path identifying the location of a successfully
     * cached file for inclusion in the generated debug info or
     * null if a source file cannot be found or cached.
     */
    public Path findAndCacheSource(ResolvedJavaType resolvedType) {
        Path path = null;
        String fileName = computeBaseName(resolvedType);
        /*
         * null for the name means this class
         * will not have a source so we skip on that
         */
        if (fileName != null) {
            /*
             * we can only provide sources
             * for known classes and interfaces
             */
            if (resolvedType.isInstanceClass() || resolvedType.isInterface()) {
                /*
                 * if we have an OriginalClassProvider we
                 * can use the underlying Java class
                 * to provide the details we need to locate
                 * a source
                 */
                if (resolvedType instanceof OriginalClassProvider) {
                    Class<?> javaClass = ((OriginalClassProvider) resolvedType).getJavaClass();
                    String packageName = computePackageName(javaClass);
                    SourceCacheType type = sourceCacheType(packageName, javaClass);
                    path = locateSource(fileName, packageName, type, javaClass);
                }
                /*
                 * if we could not locate a source via the cache
                 * then the fallback is to generate a path to the
                 * file based on the class name and let the
                 * user configure a path to the sources
                 */
                if (path == null) {
                    String name = resolvedType.toJavaName();
                    int idx = name.lastIndexOf('.');
                    if (idx >= 0 && idx < name.length() - 1) {
                        name = name.substring(0, idx);
                        path = Paths.get("", name.split("\\."));
                        path = path.resolve(fileName);
                    }
                }
            }
        }
        return path;
    }

    /**
     * Construct the base file name for a resolved
     * Java class excluding path elements using either
     * the source name embedded in the class file or
     * the class name itself.
     * @param resolvedType the resolved java type whose
     * source file name is required
     * @return the file name or null if it the class cannot
     * be associated with a source file
     */
    private String computeBaseName(ResolvedJavaType resolvedType) {
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
     * Construct the package name for a  Java class or
     * the empty String if it has no package.
     * @param javaClass the java class whose package
     * name is required
     * @return the package name or the empty String
     * if it has no package
     */
    private String computePackageName(Class<?> javaClass) {
        Package pkg = javaClass.getPackage();
        return (pkg == null ? "" : pkg.getName());
    }
    /**
     * Construct the prototype name for a Java source file
     * which can be used to resolve and cache an actual source
     * file.
     * @param fileName the base file name for the source file
     * @param packageName the name of the package for the associated Java class
     * @param type the type of cache in which to lookup or cache this class's source file
     * @param javaClass the java class whose prototype name is required
     * @return a protoype name for the source file
     */
    private Path computePrototypeName(String fileName, String packageName, SourceCacheType type, Class<?> javaClass) {
        String prefix = "";
        if (type == SourceCacheType.JDK) {
            /* JDK paths may require the module name as prefix */
            String moduleName = ModuleSupport.getModuleName(javaClass);
            if (moduleName != null) {
                prefix = moduleName;
            }
        }
        if (packageName.length() == 0) {
            return Paths.get("", fileName);
        } else {
            return Paths.get(prefix, packageName.split("\\.")).resolve(fileName);
        }
    }
    /**
     * A whitelist of packages prefixes used to
     * pre-filter JDK runtime class lookups.
     */
    public static final String[] JDK_SRC_PACKAGE_PREFIXES = {
            "java.",
            "jdk.",
            "javax.",
            "sun.",
            "com.sun.",
            "org.ietf.",
            "org.jcp.",
            "org.omg.",
            "org.w3c.",
            "org.xml",
    };
    /**
     * A whitelist of packages prefixes used to
     * pre-filter GraalVM class lookups.
     */
    public static final String[] GRAALVM_SRC_PACKAGE_PREFIXES = {
            "com.oracle.graal.",
            "com.oracle.objectfile.",
            "com.oracle.svm.",
            "com.oracle.truffle.",
            "org.graalvm.",
    };

    /**
     * A whitelist of packages prefixes used to
     * pre-filter app class lookups which
     * includes just the empty string because
     * any package will do.
     */
    private static final String[] APP_SRC_PACKAGE_PREFIXES = {
            "",
    };

    /**
     * Check a package name against a whitelist of acceptable packages.
     * @param packageName the package name of the class to be checked
     * @param whitelist a list of prefixes one of which may form
     * the initial prefix of the package name being checked
     * @return true if the package name matches an entry in the
     * whitelist otherwise false
     */
    private boolean whiteListPackage(String packageName, String[] whitelist) {
        for (String prefix : whitelist) {
            if (packageName.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Identify which type of source cache should be used
     * to locate a given class's source code.
     */
    private SourceCacheType sourceCacheType(String packageName, Class<?> javaClass) {
        if (whiteListPackage(packageName, JDK_SRC_PACKAGE_PREFIXES)) {
            return SourceCacheType.JDK;
        }
        if (whiteListPackage(packageName, GRAALVM_SRC_PACKAGE_PREFIXES)) {
            return SourceCacheType.GRAALVM;
        }
        return SourceCacheType.APPLICATION;
    }
    /**
     * A map from each of the top level root keys to a
     * cache that knows how to handle lookup and caching
     * of the associated type of source file.
     */
    private static HashMap<SourceCacheType, SourceCache> caches = new HashMap<>();

    /**
     * Retrieve the source cache used to locate and cache sources
     * of a given type as determined by the supplied key, creating
     * and initializing it if it does not already exist.
     * @param type an enum identifying the type of Java sources
     * cached by the returned cache.
     * @return the desired source cache.
     */
    private SourceCache getOrCreateCache(SourceCacheType type) {
        SourceCache sourceCache = caches.get(type);
        if (sourceCache == null) {
            sourceCache = SourceCache.createSourceCache(type);
            caches.put(type, sourceCache);
        }
        return sourceCache;
    }

    private Path locateSource(String fileName, String packagename, SourceCacheType type, Class<?> javaClass) {
        SourceCache cache = getOrCreateCache(type);
        Path prototypeName = computePrototypeName(fileName, packagename, type, javaClass);
        return cache.resolve(prototypeName);
    }
}

