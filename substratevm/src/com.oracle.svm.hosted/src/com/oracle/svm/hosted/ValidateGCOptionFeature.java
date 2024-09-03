/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted;

import java.util.Set;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.impl.InternalPlatform;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.option.GCOptionValue;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.util.StringUtil;

/**
 * The normal option validation cannot be used for {@link SubstrateOptions#SupportedGCs} as that
 * would be executed too late.
 */
@AutomaticallyRegisteredFeature
public class ValidateGCOptionFeature implements InternalFeature {

    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        validateGCOption();
    }

    private static void validateGCOption() {
        Set<String> values = SubstrateOptions.SupportedGCs.getValue().valuesAsSet();

        if (!Platform.includedIn(InternalPlatform.NATIVE_ONLY.class) && values.isEmpty()) {
            // For non-native platforms, it is possible to have no GC.
            return;
        }

        Set<String> possibleValues = GCOptionValue.possibleValues();

        if (values.isEmpty()) {
            throw UserError.abort("Invalid option '--gc'. No GC specified. %s", getGCErrorReason(possibleValues));
        }

        // Check that all specified values are valid.
        for (String val : values) {
            if (!possibleValues.contains(val)) {
                throw UserError.abort("Invalid option '--gc'. '%s' is not an accepted value. %s", val, getGCErrorReason(possibleValues));
            }
        }

        // Check that the specified combination is valid.
        if (values.size() != 1) {
            throw UserError.abort("%s is an invalid combination of GCs for option '--gc'.", StringUtil.joinSingleQuoted(values));
        }
    }

    private static String getGCErrorReason(Set<String> values) {
        return "Accepted values are " + StringUtil.joinSingleQuoted(values) + ".";
    }

}
