/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2020, 2020, Alibaba Group Holding Limited. All rights reserved.
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

import java.util.EnumSet;
import java.util.List;

import org.graalvm.collections.EconomicMap;
import org.graalvm.nativeimage.impl.RuntimeSerializationSupport;

import com.oracle.svm.configure.config.conditional.ConfigurationConditionResolver;

public abstract class SerializationConfigurationParser<C> extends ConditionalConfigurationParser {

    public static final String CUSTOM_TARGET_CONSTRUCTOR_CLASS_KEY = "customTargetConstructorClass";

    protected final ConfigurationConditionResolver<C> conditionResolver;
    protected final RuntimeSerializationSupport<C> serializationSupport;

    public static <C> SerializationConfigurationParser<C> create(boolean combinedFileSchema, ConfigurationConditionResolver<C> conditionResolver, RuntimeSerializationSupport<C> serializationSupport,
                    EnumSet<ConfigurationParserOption> parserOptions) {
        if (combinedFileSchema) {
            return new SerializationMetadataParser<>(conditionResolver, serializationSupport, parserOptions);
        } else {
            return new LegacySerializationConfigurationParser<>(conditionResolver, serializationSupport, parserOptions);
        }
    }

    public SerializationConfigurationParser(ConfigurationConditionResolver<C> conditionResolver, RuntimeSerializationSupport<C> serializationSupport,
                    EnumSet<ConfigurationParserOption> parserOptions) {
        super(parserOptions);
        this.serializationSupport = serializationSupport;
        this.conditionResolver = conditionResolver;
    }

    protected void parseSerializationTypes(List<Object> listOfSerializationTypes, boolean lambdaCapturingTypes) {
        for (Object serializationType : listOfSerializationTypes) {
            parseSerializationDescriptorObject(asMap(serializationType, "Third-level of document must be serialization descriptor objects"), lambdaCapturingTypes);
        }
    }

    protected abstract void parseSerializationDescriptorObject(EconomicMap<String, Object> data, boolean lambdaCapturingType);

    protected void registerType(ConfigurationTypeDescriptor targetSerializationClass, C condition) {
        switch (targetSerializationClass.getDescriptorType()) {
            case NAMED -> serializationSupport.register(condition, ((NamedConfigurationTypeDescriptor) targetSerializationClass).name());
            case PROXY -> serializationSupport.registerProxyClass(condition, ((ProxyConfigurationTypeDescriptor) targetSerializationClass).interfaceNames());
            case LAMBDA -> serializationSupport.registerLambdaCapturingClass(condition,
                            ((NamedConfigurationTypeDescriptor) ((LambdaConfigurationTypeDescriptor) targetSerializationClass).declaringClass()).name());
        }
    }
}
