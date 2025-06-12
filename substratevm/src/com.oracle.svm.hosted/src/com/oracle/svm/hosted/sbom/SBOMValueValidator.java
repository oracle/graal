/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.sbom;

import java.util.List;
import java.util.stream.Stream;

import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.svm.core.option.LocatableMultiOptionValue;
import com.oracle.svm.core.util.UserError;

import jdk.graal.compiler.options.OptionKey;

/**
 * Validates the values passed to '--enable-sbom'.
 */
public abstract class SBOMValueValidator {
    public abstract void validateSBOMValues(OptionKey<?> optionKey);

    static SBOMValueValidator getInstance() {
        return ImageSingletons.lookup(SBOMValueValidator.class);
    }

    /**
     * Aborts execution if the SBOM feature is deactivated from non-command-line sources like
     * 'native-image.properties'. Native Image only supports subtractive option usage from the CLI.
     */
    protected static void abortIfSBOMDisabledFromOtherThanCommandLine() {
        var optionalFalseOrigin = getNonCommandLikeOriginStream()
                        .filter(v -> v.value().equals(SBOMValues.disableSBOM))
                        .findFirst();
        if (optionalFalseOrigin.isPresent()) {
            List<String> nonCommandLineValues = getNonCommandLikeOriginStream()
                            .map(LocatableMultiOptionValue.ValueWithOrigin::value)
                            .toList();
            String message = String.format("Value '%s' for option '%s' can only be used on the command line with 'native-image'. Found non-command-line option '%s=%s' from %s.",
                            SBOMValues.disableSBOM, SBOMFeature.Options.name, SBOMFeature.Options.name, String.join(",", nonCommandLineValues), optionalFalseOrigin.get().origin());
            throw UserError.abort(message);
        }
    }

    private static Stream<LocatableMultiOptionValue.ValueWithOrigin<String>> getNonCommandLikeOriginStream() {
        return SBOMFeature.Options.IncludeSBOM.getValue().getValuesWithOrigins()
                        .filter(v -> !v.origin().commandLineLike());
    }

    protected static boolean isLastValueNotDisable() {
        var lastOrigin = SBOMFeature.Options.IncludeSBOM.getValue().lastValueWithOrigin();
        return lastOrigin.map(v -> !v.value().equals(SBOMValues.disableSBOM))
                        .orElse(true);
    }
}
