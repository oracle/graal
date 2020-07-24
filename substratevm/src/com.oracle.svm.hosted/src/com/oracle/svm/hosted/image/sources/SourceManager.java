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

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.util.ModuleSupport;
import jdk.vm.ci.meta.ResolvedJavaType;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.serviceprovider.JavaVersionUtil;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.stream.Stream;

/**
 * A singleton class responsible for locating source files for classes included in a native image
 * and copying them into the local sources.
 */
public class SourceManager {
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
                SourceCacheType sourceCacheType = sourceCacheType(packageName);
                path = locateSource(fileName, packageName, sourceCacheType, clazz);
                if (path == null) {
                    // as a last ditch effort derive path from the Java class name
                    if (debugContext.areScopesEnabled()) {
                        debugContext.log(DebugContext.INFO_LEVEL, "Failed to find source file for class %s\n", resolvedType.toJavaName());
                    }
                    if (packageName.length() > 0) {
                        path = Paths.get("", packageName.split("\\."));
                        path = path.resolve(fileName);
                    }
                } else {
                    verifiedCachePaths.put(resolvedType, SubstrateOptions.getDebugInfoSourceCacheRoot().resolve(sourceCacheType.getSubdir()));
                }
            }
        }
        /* memoize the lookup */
        verifiedPaths.put(resolvedType, (path != null ? path : INVALID_PATH));

        return path;
    }

    /**
     * Get the cache Path of the source file for a given Java class.
     *
     * @param resolvedType the Java type whose source file should be located and cached
     * @return a path identifying the cache location for a successfully cached file for inclusion in
     *         the generated debug info or {@code null} if a source file cannot be found or cached.
     */
    public Path getCachePathForSource(ResolvedJavaType resolvedType) {
        return verifiedCachePaths.get(resolvedType);
    }

    /**
     * Construct the base file name for a resolved Java class excluding path elements using either
     * the source name embedded in the class file or the class name itself.
     * 
     * @param resolvedType the resolved java type whose source file name is required
     * @return the file name or null if it the class cannot be associated with a source file
     */
    private static String computeBaseName(ResolvedJavaType resolvedType) {
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
     * @param sourceCacheType the sourceCacheType of cache in which to lookup or cache this class's
     *            source file
     * @param clazz the class associated with the sourceCacheType used to identify the module prefix
     *            for JDK classes
     * @return a protoype name for the source file
     */
    private static Path computePrototypeName(String fileName, String packageName, SourceCacheType sourceCacheType, Class<?> clazz) {
        String prefix = "";
        if (sourceCacheType == SourceCacheType.JDK && clazz != null) {
            /* JDK11+ paths will require the module name as prefix */
            String moduleName = ModuleSupport.getModuleName(clazz);
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
     * An accept list of packages prefixes used to pre-filter JDK8 runtime class lookups.
     */
    public static final String[] JDK8_SRC_PACKAGE_PREFIXES = {
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
     * A variant accept list of packages prefixes used to pre-filter JDK11+ runtime class lookups.
     */
    public static final String[] JDK_SRC_PACKAGE_PREFIXES = Stream.concat(Stream.of(JDK8_SRC_PACKAGE_PREFIXES),
                    Stream.of("org.graalvm.compiler"))
                    .toArray(String[]::new);

    /**
     * An accept list of packages prefixes used to pre-filter GraalVM class lookups.
     */
    public static final String[] GRAALVM_SRC_PACKAGE_PREFIXES = {
                    "com.oracle.graal.",
                    "com.oracle.objectfile.",
                    "com.oracle.svm.",
                    "com.oracle.truffle.",
                    "org.graalvm.",
    };

    /**
     * Check a package name against an accept list of acceptable packages.
     * 
     * @param packageName the package name of the class to be checked
     * @param acceptList a list of prefixes one of which may form the initial prefix of the package
     *            name being checked
     * @return true if the package name matches an entry in the acceptlist otherwise false
     */
    private static boolean acceptList(String packageName, String[] acceptList) {
        for (String prefix : acceptList) {
            if (packageName.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Identify which type of source cache should be used to locate a given class's source code as
     * determined by it's package name.
     * 
     * @param packageName the package name of the class.
     * @return the corresponding source cache type
     */
    private static SourceCacheType sourceCacheType(String packageName) {
        if (acceptList(packageName, (JavaVersionUtil.JAVA_SPEC >= 11 ? JDK_SRC_PACKAGE_PREFIXES : JDK8_SRC_PACKAGE_PREFIXES))) {
            return SourceCacheType.JDK;
        }
        if (acceptList(packageName, GRAALVM_SRC_PACKAGE_PREFIXES)) {
            return SourceCacheType.GRAALVM;
        }
        return SourceCacheType.APPLICATION;
    }

    /**
     * A map from each of the top level root keys to a cache that knows how to handle lookup and
     * caching of the associated type of source file.
     */
    private static HashMap<SourceCacheType, SourceCache> caches = new HashMap<>();

    /**
     * A map from a Java type to an associated source paths which is known to have an up to date
     * entry in the relevant source file cache. This is used to memoize previous lookups.
     */
    private static HashMap<ResolvedJavaType, Path> verifiedPaths = new HashMap<>();

    /**
     * A map from a Java type to an associated source file cache path.
     */
    private static HashMap<ResolvedJavaType, Path> verifiedCachePaths = new HashMap<>();

    /**
     * An invalid path used as a marker to track failed lookups so we don't waste time looking up
     * the source again. Note that all legitimate paths will end with a ".java" suffix.
     */
    private static final Path INVALID_PATH = Paths.get("invalid");

    /**
     * Retrieve the source cache used to locate and cache sources of a given type as determined by
     * the supplied key, creating and initializing it if it does not already exist.
     * 
     * @param type an enum identifying the type of Java sources cached by the returned cache.
     * @return the desired source cache.
     */
    private static SourceCache getOrCreateCache(SourceCacheType type) {
        SourceCache sourceCache = caches.get(type);
        if (sourceCache == null) {
            sourceCache = SourceCache.createSourceCache(type);
            caches.put(type, sourceCache);
        }
        return sourceCache;
    }

    private static Path locateSource(String fileName, String packagename, SourceCacheType cacheType, Class<?> clazz) {
        SourceCache cache = getOrCreateCache(cacheType);
        Path prototypeName = computePrototypeName(fileName, packagename, cacheType, clazz);
        if (prototypeName != null) {
            return cache.resolve(prototypeName);
        } else {
            return null;
        }
    }
}
