/*
 * Copyright (c) 2014, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.option;

import org.graalvm.collections.EconomicMap;

import com.oracle.svm.common.option.MultiOptionValue;

import jdk.graal.compiler.options.OptionKey;
import jdk.graal.compiler.options.OptionValues;

public class HostedOptionCustomizer implements HostedOptionProvider {
    private final EconomicMap<OptionKey<?>, Object> hostedValues;
    private final EconomicMap<OptionKey<?>, Object> runtimeValues;

    public HostedOptionCustomizer(HostedOptionParser hostedOptionParser) {
        this.hostedValues = copyOptionValues(hostedOptionParser.getHostedValues());
        this.runtimeValues = copyOptionValues(hostedOptionParser.getRuntimeValues());
    }

    @Override
    public EconomicMap<OptionKey<?>, Object> getHostedValues() {
        return hostedValues;
    }

    @Override
    public EconomicMap<OptionKey<?>, Object> getRuntimeValues() {
        return runtimeValues;
    }

    private static EconomicMap<OptionKey<?>, Object> copyOptionValues(EconomicMap<OptionKey<?>, Object> original) {
        EconomicMap<OptionKey<?>, Object> result = OptionValues.newOptionMap();
        var cursor = original.getEntries();
        while (cursor.advance()) {
            OptionKey<?> key = cursor.getKey();
            Object value = cursor.getValue();
            if (value instanceof MultiOptionValue<?> v) {
                value = v.createCopy();
            }
            result.put(key, value);
        }
        return result;
    }
}
