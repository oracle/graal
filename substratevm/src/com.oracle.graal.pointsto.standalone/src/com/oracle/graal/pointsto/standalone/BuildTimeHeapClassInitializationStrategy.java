/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.graal.pointsto.standalone;

import java.util.LinkedHashSet;
import java.util.Set;

import com.oracle.graal.pointsto.meta.AnalysisType;

import jdk.graal.compiler.options.OptionValues;

/**
 * Standalone build-time heap mode initializes reachable classes eagerly, except for guest classes
 * that are known to fail when initialized in the current standalone guest configuration.
 */
public final class BuildTimeHeapClassInitializationStrategy implements StandaloneClassInitializationStrategy {
    /*
     * GR-75091: build-time initialization of Continuation can re-enter Espresso initialization
     * through locks that require TruffleSafepoint state on the worker thread. Standalone does not
     * have a dedicated VMAccess.attachThread-style setup for those workers yet.
     */
    private static final Set<String> DENIED_BUILD_TIME_INITIALIZATION_TYPES = Set.of(
                    "jdk.internal.vm.Continuation");
    /*
     * GR-75203: build-time initialization of the AWT/Swing stack can currently reach Truffle NFI
     * setup from the standalone guest embedding and crash in libtrufflenfi. Keep the entire UI
     * stack runtime initialized until that embedding bug is fixed.
     */
    private static final Set<String> DENIED_BUILD_TIME_INITIALIZATION_PACKAGES = Set.of(
                    "java.awt.",
                    "javax.swing.",
                    "sun.awt.",
                    "sun.lwawt.",
                    "sun.swing.",
                    "com.sun.java.swing.");
    private final Set<String> deniedBuildTimeInitializationTypes;
    private final Set<String> deniedBuildTimeInitializationPackages;

    public BuildTimeHeapClassInitializationStrategy(OptionValues options) {
        deniedBuildTimeInitializationTypes = collectDeniedTypes(options);
        deniedBuildTimeInitializationPackages = collectDeniedPackages(options);
    }

    @Override
    public boolean shouldInitializeAtBuildTime(AnalysisType type) {
        String javaName = type.toJavaName(true);
        return !deniedBuildTimeInitializationTypes.contains(javaName) &&
                        deniedBuildTimeInitializationPackages.stream().noneMatch(javaName::startsWith);
    }

    private static Set<String> collectDeniedTypes(OptionValues options) {
        LinkedHashSet<String> deniedTypes = new LinkedHashSet<>(DENIED_BUILD_TIME_INITIALIZATION_TYPES);
        deniedTypes.addAll(StandaloneOptions.StandaloneExtraRuntimeInitializedClasses.getValue(options).values());
        return Set.copyOf(deniedTypes);
    }

    private static Set<String> collectDeniedPackages(OptionValues options) {
        LinkedHashSet<String> deniedPackages = new LinkedHashSet<>(DENIED_BUILD_TIME_INITIALIZATION_PACKAGES);
        for (String packagePrefix : StandaloneOptions.StandaloneExtraRuntimeInitializedPackages.getValue(options).values()) {
            deniedPackages.add(normalizePackagePrefix(packagePrefix));
        }
        return Set.copyOf(deniedPackages);
    }

    private static String normalizePackagePrefix(String packagePrefix) {
        return packagePrefix.endsWith(".") ? packagePrefix : packagePrefix + ".";
    }
}
