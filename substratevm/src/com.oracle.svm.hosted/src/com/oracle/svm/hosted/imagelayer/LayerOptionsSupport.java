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
package com.oracle.svm.hosted.imagelayer;

import java.nio.file.Path;
import java.util.List;

import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.util.VMError;

public class LayerOptionsSupport {

    public record LayerOption(Path fileName, ExtendedOption[] extendedOptions) {
        /** Split a layer option into its components. */
        public static LayerOption parse(String layerOptionValue) {
            VMError.guarantee(!layerOptionValue.isEmpty());
            // Given an argument of form layer-file.nil,module=m1,package=p1
            // First get the list: [layer-file.nil, module=m1, package=p1]
            return parse(List.of(SubstrateUtil.split(layerOptionValue, ",")));
        }

        public static LayerOption parse(List<String> options) {
            VMError.guarantee(!options.isEmpty());
            // Check for the optional file name
            Path fileName = null;
            int skip = 0;
            if (options.getFirst().endsWith(LayerArchiveSupport.LAYER_FILE_EXTENSION)) {
                fileName = Path.of(options.getFirst());
                skip = 1;
            }
            ExtendedOption[] extendedOptions = options.stream().skip(skip).map(ExtendedOption::parse).toArray(ExtendedOption[]::new);
            return new LayerOption(fileName, extendedOptions);
        }
    }

    public record ExtendedOption(String key, String value) {

        static ExtendedOption parse(String option) {
            String[] optionParts = SubstrateUtil.split(option, "=", 2);
            if (optionParts.length == 2) {
                return new ExtendedOption(optionParts[0], optionParts[1]);
            } else {
                return new ExtendedOption(option, null);
            }
        }
    }

    public record PackageOptionValue(String name, boolean isWildcard) {

        static final String PACKAGE_WILDCARD_SUFFIX = ".*";

        public static PackageOptionValue from(ExtendedOption extendedOption) {
            if (!extendedOption.key().equals(LayerArchiveSupport.PACKAGE_OPTION)) {
                return null;
            }
            String extendedOptionValue = extendedOption.value();
            if (extendedOptionValue.endsWith(PACKAGE_WILDCARD_SUFFIX)) {
                return new PackageOptionValue(extendedOptionValue.substring(0, extendedOptionValue.length() - PACKAGE_WILDCARD_SUFFIX.length()), true);
            }
            return new PackageOptionValue(extendedOptionValue, false);
        }

        @Override
        public String toString() {
            return name + (isWildcard ? PACKAGE_WILDCARD_SUFFIX : "");
        }
    }
}
