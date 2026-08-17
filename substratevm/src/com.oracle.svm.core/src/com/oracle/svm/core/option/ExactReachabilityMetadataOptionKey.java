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
package com.oracle.svm.core.option;

import java.util.Objects;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.FutureDefaultsOptions;
import com.oracle.svm.core.MissingRegistrationSupport;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.guest.staging.option.RuntimeOptionKey;
import com.oracle.svm.shared.util.SubstrateUtil;

/**
 * An immutable runtime option that refines exact reachability metadata.
 *
 * The option only has an effect in an image that was built with exact reachability metadata, so
 * setting it to a non-default value is rejected both as a {@code -R:} build-time default and at run
 * time when the image was built without {@code --future-defaults=exact-reflection} (or a deprecated
 * alias).
 */
public final class ExactReachabilityMetadataOptionKey<T> extends RuntimeOptionKey<T> {

    public ExactReachabilityMetadataOptionKey(T defaultValue) {
        super(defaultValue, RuntimeOptionKeyFlag.Immutable);
    }

    @Override
    @Platforms(Platform.HOSTED_ONLY.class)
    public void validate() {
        super.validate();
        if (selectsExactMetadataWithoutSupport()) {
            throw UserError.abort("The option '%s' can only be set for an image built with '%s'.", getName(), FutureDefaultsOptions.EXACT_REFLECTION_ARGUMENT);
        }
    }

    @Override
    protected void afterValueUpdate() {
        super.afterValueUpdate();
        if (!SubstrateUtil.HOSTED && selectsExactMetadataWithoutSupport()) {
            throw new IllegalArgumentException("The option '" + getName() + "' requires an executable built with '" + FutureDefaultsOptions.EXACT_REFLECTION_ARGUMENT + "'.");
        }
    }

    /** Non-default values need a build with exact metadata (FS-001-native-image-semantics.3.4). */
    private boolean selectsExactMetadataWithoutSupport() {
        return !MissingRegistrationSupport.singleton().exactMetadataSupported() && hasBeenSet() && !Objects.equals(getValue(), getDefaultValue());
    }
}
