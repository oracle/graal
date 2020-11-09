/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

package com.oracle.truffle.espresso.runtime;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.graalvm.options.OptionDescriptor;
import org.graalvm.options.OptionDescriptors;
import org.graalvm.options.OptionKey;
import org.graalvm.options.OptionValues;

import com.oracle.truffle.espresso.EspressoOptions;
import com.oracle.truffle.espresso.meta.EspressoError;

/**
 * Utility class to provide version checking predicates for clarity in the code base. Avoids
 * cluttering {@link EspressoContext}.
 * 
 * Makes it harder to access the raw int version: please add new predicates instead.
 */
public final class JavaVersion {
    public static final int LATEST = 11;

    private final int version;

    JavaVersion(int version) {
        this.version = version;
    }

    public boolean java8OrEarlier() {
        return version <= 8;
    }

    public boolean java9OrLater() {
        return version >= 9;
    }

    public boolean java11OrLater() {
        return version >= 11;
    }

    public boolean modulesEnabled() {
        return java9OrLater();
    }

    public boolean varHandlesEnabled() {
        return java9OrLater();
    }

    public boolean compactStringsEnabled() {
        return java9OrLater();
    }

    public int classFileVersion() {
        return version + 44;
    }

    // region version checks

    private static final List<String> emptyExcluded = Collections.emptyList();
    private static final List<String> excludedOptions8 = Arrays.asList(
                    "java.Module",
                    "java.ModulePath",
                    "java.AddExports",
                    "java.AddModules",
                    "java.AddOpens",
                    "java.AddReads");

    void checkVersion(EspressoOptions.VersionType versionType) {
        boolean b = false;
        // @formatter:off
        switch (versionType) {
            case J8:     b = version == 8;      break;
            case J11:    b = version == 11;     break;
            case J17:    b = version == 17;     break;
            case LATEST: b = version == LATEST; break;
            case LAX:    b = true;              break;
        }
        //@formatter:on
        EspressoError.guarantee(b,
                        "Inconsistent versions. Requested version: %s, discovered java home version: %s", versionType.asString(), toString());
    }

    void checkOptions(OptionValues options) {
        switch (version) {
            case 8:
                optionsCheck(excludedOptions8, options);
                break;
            default:
                optionsCheck(emptyExcluded, options);
                break;
        }
    }

    private static void optionsCheck(List<String> list, OptionValues options) {
        OptionDescriptors descriptors = options.getDescriptors();
        for (String str : list) {
            OptionDescriptor descriptor = descriptors.get(str);
            if (descriptor != null) {
                OptionKey<?> key = descriptor.getKey();
                EspressoError.guarantee(!options.hasBeenSet(key), "unrecognized option: %s", descriptor.getName());
            }
        }
    }

    // endregion version checks

    @Override
    public String toString() {
        return Integer.toString(version);
    }
}
