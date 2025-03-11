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
package com.oracle.svm.configure;

import static com.oracle.svm.configure.UnresolvedConfigurationCondition.TYPE_REACHABLE_KEY;
import static com.oracle.svm.configure.UnresolvedConfigurationCondition.TYPE_REACHED_KEY;

import java.util.EnumSet;

import org.graalvm.collections.EconomicMap;

public abstract class ConditionalConfigurationParser extends ConfigurationParser {
    public static final String CONDITIONAL_KEY = "condition";

    protected ConditionalConfigurationParser(EnumSet<ConfigurationParserOption> parserOptions) {
        super(parserOptions);
    }

    @Override
    protected EnumSet<ConfigurationParserOption> supportedOptions() {
        EnumSet<ConfigurationParserOption> base = super.supportedOptions();
        base.add(ConfigurationParserOption.TREAT_ALL_TYPE_REACHABLE_CONDITIONS_AS_TYPE_REACHED);
        return base;
    }

    protected UnresolvedConfigurationCondition parseCondition(EconomicMap<String, Object> data, boolean runtimeCondition) {
        Object conditionData = data.get(CONDITIONAL_KEY);
        if (conditionData != null) {
            EconomicMap<String, Object> conditionObject = asMap(conditionData, "Attribute '" + CONDITIONAL_KEY + "' must be an object");
            if (conditionObject.containsKey(TYPE_REACHABLE_KEY) && conditionObject.containsKey(TYPE_REACHED_KEY)) {
                failOnSchemaError("condition can not have both '" + TYPE_REACHED_KEY + "' and '" + TYPE_REACHABLE_KEY + "' set.");
            }

            if (conditionObject.containsKey(TYPE_REACHED_KEY)) {
                if (!runtimeCondition) {
                    failOnSchemaError("'" + TYPE_REACHED_KEY + "' condition cannot be used in older schemas. Please migrate the file to the latest schema.");
                }
                Object object = conditionObject.get(TYPE_REACHED_KEY);
                var condition = parseTypeContents(object);
                if (condition.isPresent()) {
                    NamedConfigurationTypeDescriptor namedDescriptor = checkConditionType(condition.get());
                    return UnresolvedConfigurationCondition.create(namedDescriptor);
                }
            } else if (conditionObject.containsKey(TYPE_REACHABLE_KEY)) {
                if (runtimeCondition && !checkOption(ConfigurationParserOption.TREAT_ALL_TYPE_REACHABLE_CONDITIONS_AS_TYPE_REACHED)) {
                    failOnSchemaError("'" + TYPE_REACHABLE_KEY + "' condition can not be used with the latest schema. Please use '" + TYPE_REACHED_KEY + "'.");
                }
                Object object = conditionObject.get(TYPE_REACHABLE_KEY);
                var condition = parseTypeContents(object);
                if (condition.isPresent()) {
                    NamedConfigurationTypeDescriptor namedDescriptor = checkConditionType(condition.get());
                    return UnresolvedConfigurationCondition.create(namedDescriptor, checkOption(ConfigurationParserOption.TREAT_ALL_TYPE_REACHABLE_CONDITIONS_AS_TYPE_REACHED));
                }
            }
        }
        return UnresolvedConfigurationCondition.alwaysTrue();
    }

    private static NamedConfigurationTypeDescriptor checkConditionType(ConfigurationTypeDescriptor type) {
        if (!(type instanceof NamedConfigurationTypeDescriptor)) {
            failOnSchemaError("condition should be a fully qualified class name.");
        }
        return (NamedConfigurationTypeDescriptor) type;
    }

}
