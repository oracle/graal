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

import com.oracle.svm.core.util.UserError;

import jdk.graal.compiler.options.OptionKey;

/**
 * Value validator for '--enable-sbom' that aborts execution if the SBOM feature is activated.
 */
final class UnsupportedSBOMValueValidator extends SBOMValueValidator {
    @Override
    public void validateSBOMValues(OptionKey<?> optionKey) {
        List<String> values = SBOMFeature.Options.IncludeSBOM.getValue().values();
        if (values.isEmpty()) {
            return;
        }

        SBOMValueValidator.abortIfSBOMDisabledFromOtherThanCommandLine();
        abortIfLastOriginDoesNotDisableSBOM();
    }

    private static void abortIfLastOriginDoesNotDisableSBOM() {
        if (isLastValueNotDisable()) {
            String message = String.format("""
                            The SBOM feature is only available in Oracle GraalVM. \
                            Upgrade to Oracle GraalVM or disable the SBOM feature by omitting '%s' or \
                            by making sure '%s=%s' is last on the command line.
                            """, SBOMFeature.Options.name, SBOMFeature.Options.name, SBOMValues.disableSBOM);
            UserError.abort(message);
        }
    }
}
