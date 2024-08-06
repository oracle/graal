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
package com.oracle.svm.driver;

import static com.oracle.svm.driver.BundleSupport.BUNDLE_OPTION;

import java.util.Arrays;

import com.oracle.svm.core.SubstrateUtil;

public class BundleOptions {

    /** Split a bundle argument into its components. */
    public static BundleOption parseBundleOption(String cmdLineArg) {
        // Given an argument of form --bundle-create=bundle.nib,dry-run
        // First get the list: [bundle-create=bundle.nib, dry-run]
        String[] options = SubstrateUtil.split(cmdLineArg.substring(BUNDLE_OPTION.length() + 1), ",");
        // Then extract the variant components: [create=bundle.nib, dry-run]
        String[] variantAndFileName = SubstrateUtil.split(options[0], "=", 2);
        // First part is the option variant
        String variant = variantAndFileName[0];
        // Second part is the optional file name
        String fileName = null;
        if (variantAndFileName.length == 2) {
            fileName = variantAndFileName[1];
        }
        // The rest are optional extended options
        ExtendedOption[] extendedOptions = Arrays.stream(options).skip(1).map(BundleOptions::parseExtendedOption).toArray(ExtendedOption[]::new);
        return new BundleOption(variant, fileName, extendedOptions);
    }

    public record BundleOption(String variant, String fileName, ExtendedOption[] extendedOptions) {
    }

    private static ExtendedOption parseExtendedOption(String option) {
        String[] optionParts = SubstrateUtil.split(option, "=", 2);
        if (optionParts.length == 2) {
            return new ExtendedOption(optionParts[0], optionParts[1]);
        } else {
            return new ExtendedOption(option, null);
        }
    }

    public record ExtendedOption(String key, String value) {
    }

}
