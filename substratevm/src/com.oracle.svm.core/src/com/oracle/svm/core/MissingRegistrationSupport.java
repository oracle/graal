/*
 * Copyright (c) 2023, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.shared.option.OptionClassFilter;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.AllAccess;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.NoLayeredCallbacks;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.PartiallyLayerAware;
import com.oracle.svm.shared.singletons.traits.SingletonLayeredInstallationKind.Duplicable;
import com.oracle.svm.shared.singletons.traits.SingletonTraits;

import jdk.graal.compiler.api.replacements.Fold;

/**
 * Exact reachability metadata is selected at build time by {@code --future-defaults=exact-reflection}
 * or the deprecated build-time aliases. Only such builds encode the exact-mode metadata and
 * reporting paths; the runtime options merely refine the scope within them.
 */
@SingletonTraits(access = AllAccess.class, layeredCallbacks = NoLayeredCallbacks.class, layeredInstallationKind = Duplicable.class, other = PartiallyLayerAware.class)
public class MissingRegistrationSupport {
    private final OptionClassFilter legacyExactMetadataFilter;
    private final boolean legacyExactMetadata;
    /** Set iff the build prepares exact metadata (FS-003-reflection.10). */
    private final boolean exactMetadataSupported;

    @Platforms(Platform.HOSTED_ONLY.class)
    public MissingRegistrationSupport(OptionClassFilter legacyExactMetadataFilter, boolean legacyExactMetadata) {
        this.legacyExactMetadataFilter = legacyExactMetadataFilter;
        this.legacyExactMetadata = legacyExactMetadata;
        this.exactMetadataSupported = FutureDefaultsOptions.exactReflection() || legacyExactMetadata;
    }

    @Fold
    public static MissingRegistrationSupport singleton() {
        return ImageSingletons.lookup(MissingRegistrationSupport.class);
    }

    /**
     * Whether this image was built with exact reachability metadata, i.e., whether the exact-mode
     * metadata and reporting paths exist. Constant-folded through {@link #singleton()}.
     */
    public boolean exactMetadataSupported() {
        return exactMetadataSupported;
    }

    /**
     * Whether the build must prepare exact-mode metadata for a call made from the given class. The
     * runtime options can only narrow the scope, so the future default covers every caller and the
     * deprecated aliases cover their configured scope.
     */
    @Platforms(Platform.HOSTED_ONLY.class)
    public boolean prepareExactMetadata(String moduleName, String packageName, String className) {
        return FutureDefaultsOptions.exactReflection() || legacyExactMetadataFilter.isIncluded(moduleName, packageName, className) != null;
    }

    public boolean reportMissingRegistrationErrors(StackTraceElement responsibleClass) {
        return reportMissingRegistrationErrors(responsibleClass.getModuleName(), getPackageName(responsibleClass.getClassName()), responsibleClass.getClassName());
    }

    public boolean reportMissingRegistrationErrorsWithoutResponsibleClass() {
        return FutureDefaultsOptions.exactReflection() || MissingRegistrationUtils.globalExactReachabilityMetadata() || legacyExactMetadata;
    }

    /**
     * {@code -XX:+ExactReachabilityMetadata} covers all callers, {@code -XX:ExactReachabilityMetadataPackages}
     * limits the future default to the listed packages and the deprecated aliases report for their
     * configured scope.
     */
    public boolean reportMissingRegistrationErrors(String moduleName, String packageName, String className) {
        /* See FS-003-reflection.10: compatibility options can refine reporting scope. */
        String packages = MissingRegistrationUtils.exactReachabilityMetadataPackages();
        return MissingRegistrationUtils.globalExactReachabilityMetadata() || exactMetadataForPackage(packages, packageName) ||
                        legacyExactMetadataFilter.isIncluded(moduleName, packageName, className) != null ||
                        (FutureDefaultsOptions.exactReflection() && packages.isEmpty());
    }

    public boolean legacyExactMetadata() {
        return legacyExactMetadata;
    }

    private static boolean exactMetadataForPackage(String packages, String packageName) {
        int start = 0;
        while (start < packages.length()) {
            int end = packages.indexOf(',', start);
            if (end == -1) {
                end = packages.length();
            }
            if (packageName.length() == end - start && packages.regionMatches(start, packageName, 0, packageName.length())) {
                return true;
            }
            start = end + 1;
        }
        return false;
    }

    private static String getPackageName(String className) {
        int lastDot = className.lastIndexOf('.');
        return lastDot == -1 ? "" : className.substring(0, lastDot);
    }
}
