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
package com.oracle.svm.hosted.driver;

import static com.oracle.svm.hosted.driver.IncludeOptionsSupport.ExtendedOption.MODULE_OPTION;
import static com.oracle.svm.hosted.driver.IncludeOptionsSupport.ExtendedOption.PACKAGE_OPTION;
import static com.oracle.svm.hosted.driver.IncludeOptionsSupport.ExtendedOption.PATH_OPTION;

import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.option.LocatableMultiOptionValue;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.hosted.NativeImageClassLoaderSupport;

public class IncludeOptionsSupport {
    public record ExtendedOptionWithOrigin(ExtendedOption option, LocatableMultiOptionValue.ValueWithOrigin<?> valueWithOrigin) {
    }

    public record ExtendedOption(String key, String value) {

        public static final String PACKAGE_OPTION = "package";
        public static final String MODULE_OPTION = "module";
        public static final String PATH_OPTION = "path";

        public static ExtendedOption parse(String option) {
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
            if (!extendedOption.key().equals(PACKAGE_OPTION)) {
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

    public static String possibleExtendedOptions() {
        return Stream.of(IncludeOptionsSupport.ExtendedOption.MODULE_OPTION, IncludeOptionsSupport.ExtendedOption.PACKAGE_OPTION, IncludeOptionsSupport.ExtendedOption.PATH_OPTION)
                        .map(option -> option + "=" + "<" + option + ">")
                        .collect(Collectors.joining(", "));
    }

    public static void parseIncludeSelector(String optionArg, LocatableMultiOptionValue.ValueWithOrigin<String> valueWithOrigin, NativeImageClassLoaderSupport.IncludeSelectors includeSelectors,
                    ExtendedOption option, String possibleOptions) {
        boolean validOption = option.value() != null && !option.value().isEmpty();
        switch (option.key()) {
            case MODULE_OPTION -> {
                UserError.guarantee(validOption, "Option %s specified with '%s' from %s requires a module name argument, e.g., %s=module-name.",
                                option.key(), optionArg, valueWithOrigin.origin(), option.key());
                includeSelectors.addModule(option.value, new ExtendedOptionWithOrigin(option, valueWithOrigin));

            }
            case PACKAGE_OPTION -> {
                UserError.guarantee(validOption, "Option %s specified with '%s' from %s requires a package name argument, e.g., %s=package-name.",
                                option.key(), optionArg, valueWithOrigin.origin(), option.key());
                includeSelectors.addPackage(Objects.requireNonNull(PackageOptionValue.from(option)));
            }
            case PATH_OPTION -> {
                UserError.guarantee(validOption, "Option %s specified with '%s' from %s requires a class-path entry, e.g., %s=path/to/cp-entry.",
                                option.key(), optionArg, valueWithOrigin.origin(), option.key());
                includeSelectors.addClassPathEntry(option.value(), new ExtendedOptionWithOrigin(option, valueWithOrigin));
            }
            default ->
                throw UserError.abort("Unknown option '%s' specified with %s from %s. The possible options are: " + possibleOptions,
                                option.key(), optionArg, valueWithOrigin.origin());
        }
    }
}
