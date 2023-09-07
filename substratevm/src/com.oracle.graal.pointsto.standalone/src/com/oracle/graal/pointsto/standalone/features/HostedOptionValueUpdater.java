/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2022, 2022, Alibaba Group Holding Limited. All rights reserved.
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

package com.oracle.graal.pointsto.standalone.features;

import com.oracle.graal.pointsto.util.AnalysisError;
import com.oracle.svm.util.ReflectionUtil;
import org.graalvm.compiler.options.OptionKey;
import org.graalvm.collections.EconomicMap;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

public class HostedOptionValueUpdater {
    public static final String HOSTED_OPTION_VALUES_CLASS = "com.oracle.svm.core.option.HostedOptionValues";

    private static List<HostedOptionValue> hostedOptionUpdates = new ArrayList<>();

    public static List<HostedOptionValue> getHostedOptionUpdates() {
        return hostedOptionUpdates;
    }

    public static class HostedOptionValue {
        Object newValue;
        Class<?> optionClass;
        String optionName;

        public HostedOptionValue(Object newValue, Class<?> optionClass, String optionName) {
            this.newValue = newValue;
            this.optionClass = optionClass;
            this.optionName = optionName;
        }

        public void update(EconomicMap<OptionKey<?>, Object> values) {
            try {
                Field optionField = ReflectionUtil.lookupField(optionClass, optionName);
                OptionKey<?> optionKey = (OptionKey<?>) optionField.get(null);
                optionKey.update(values, newValue);
            } catch (ReflectiveOperationException e) {
                AnalysisError.dependencyNotExist("option " + optionName, "svm.jar", e);
            }
        }
    }

    /**
     * Register one or more HostedOptions that are necessary to run the original SVM feature.
     *
     * @param updaters
     */
    public static void registerHostedOptionUpdate(HostedOptionValue... updaters) {
        for (HostedOptionValue hostedOptionValueUpdater : updaters) {
            hostedOptionUpdates.add(hostedOptionValueUpdater);
        }
    }
}
