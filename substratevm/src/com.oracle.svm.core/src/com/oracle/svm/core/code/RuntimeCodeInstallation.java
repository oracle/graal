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
package com.oracle.svm.core.code;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.hosted.Feature;

import com.oracle.svm.shared.BuildPhaseProvider;
import com.oracle.svm.shared.util.VMError;

import jdk.graal.compiler.api.replacements.Fold;

public final class RuntimeCodeInstallation {
    /**
     * Returns whether this image can contain code installed into the runtime code cache after
     * startup. This is broader than runtime compilation: runtime compilation is one producer of
     * runtime-installed code, but features can also install prepared code without enabling the
     * runtime compiler. Use this predicate for code that must handle non-image {@link CodeInfo};
     * keep runtime-compilation checks for compiler and deoptimization semantics.
     * <p>
     * This method can be called as early as during {@link Feature#afterRegistration}.
     */
    @Fold
    public static boolean isEnabled() {
        VMError.guarantee(BuildPhaseProvider.isFeatureRegistrationFinished(), "RuntimeCodeInstallation.isEnabled() must not be called before the feature registration is finished.");
        return ImageSingletons.contains(RuntimeCodeInstallationCanaryFeature.class);
    }

    private RuntimeCodeInstallation() {
    }
}
