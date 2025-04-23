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
package com.oracle.svm.core.option;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import com.oracle.svm.core.SubstrateOptions;

import jdk.graal.compiler.api.replacements.Fold;
import jdk.graal.compiler.options.OptionDescriptors;
import jdk.graal.compiler.options.OptionsContainer;

public enum GCOptionValue {
    SERIAL("serial"),
    EPSILON("epsilon"),
    G1("G1");

    private static Set<String> supportedValues = null;

    private final String optionValue;

    GCOptionValue(String optionValue) {
        this.optionValue = optionValue;
    }

    public String getValue() {
        return optionValue;
    }

    @Fold
    public static synchronized Set<String> possibleValues() {
        if (supportedValues == null) {
            Set<String> values = new HashSet<>();
            Iterable<OptionDescriptors> optionDescriptors = OptionsContainer.getDiscoverableOptions(GCOptionValue.class.getClassLoader());
            SubstrateOptionsParser.collectOptions(optionDescriptors, optionDescriptor -> {
                for (APIOption annotation : OptionUtils.getAnnotationsByType(optionDescriptor, APIOption.class)) {
                    if (annotation.group().equals(SubstrateOptions.GCGroup.class)) {
                        values.add(annotation.name()[0]);
                    }
                }
            });
            supportedValues = Collections.unmodifiableSet(values);
        }
        return supportedValues;
    }

}
