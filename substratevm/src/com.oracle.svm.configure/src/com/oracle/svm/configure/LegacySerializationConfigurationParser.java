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
package com.oracle.svm.configure;

import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

import org.graalvm.collections.EconomicMap;
import org.graalvm.nativeimage.impl.RuntimeSerializationSupport;

import com.oracle.svm.configure.config.conditional.ConfigurationConditionResolver;
import com.oracle.svm.util.LogUtils;

import jdk.graal.compiler.util.json.JsonParserException;

final class LegacySerializationConfigurationParser<C> extends SerializationConfigurationParser<C> {

    private static final String SERIALIZATION_TYPES_KEY = "types";
    private static final String LAMBDA_CAPTURING_SERIALIZATION_TYPES_KEY = "lambdaCapturingTypes";
    private static final String PROXY_SERIALIZATION_TYPES_KEY = "proxies";

    private final ProxyConfigurationParser<C> proxyConfigurationParser;

    LegacySerializationConfigurationParser(ConfigurationConditionResolver<C> conditionResolver, RuntimeSerializationSupport<C> serializationSupport, EnumSet<ConfigurationParserOption> parserOptions) {
        super(conditionResolver, serializationSupport, parserOptions);
        this.proxyConfigurationParser = new ProxyConfigurationParser<>(conditionResolver, parserOptions, serializationSupport::registerProxyClass);
    }

    @Override
    public void parseAndRegister(Object json, URI origin) {
        if (json instanceof List) {
            parseOldConfiguration(asList(json, "First-level of document must be an array of serialization lists"));
        } else if (json instanceof EconomicMap) {
            parseNewConfiguration(asMap(json, "First-level of document must be a map of serialization types"));
        } else {
            throw new JsonParserException("First-level of document must either be an array of serialization lists or a map of serialization types");
        }
    }

    private void parseOldConfiguration(List<Object> listOfSerializationConfigurationObjects) {
        parseSerializationTypes(asList(listOfSerializationConfigurationObjects, "Second-level of document must be serialization descriptor objects"), false);
    }

    private void parseNewConfiguration(EconomicMap<String, Object> listOfSerializationConfigurationObjects) {
        if (!listOfSerializationConfigurationObjects.containsKey(SERIALIZATION_TYPES_KEY) || !listOfSerializationConfigurationObjects.containsKey(LAMBDA_CAPTURING_SERIALIZATION_TYPES_KEY)) {
            throw new JsonParserException("Second-level of document must be arrays of serialization descriptor objects");
        }

        parseSerializationTypes(
                        asList(listOfSerializationConfigurationObjects.get(SERIALIZATION_TYPES_KEY), "The types property must be an array of serialization descriptor objects"),
                        false);
        parseSerializationTypes(
                        asList(listOfSerializationConfigurationObjects.get(LAMBDA_CAPTURING_SERIALIZATION_TYPES_KEY),
                                        "The lambdaCapturingTypes property must be an array of serialization descriptor objects"),
                        true);

        if (listOfSerializationConfigurationObjects.containsKey(PROXY_SERIALIZATION_TYPES_KEY)) {
            proxyConfigurationParser.parseAndRegister(listOfSerializationConfigurationObjects.get(PROXY_SERIALIZATION_TYPES_KEY), null);
        }
    }

    private boolean customConstructorWarningTriggered = false;

    @Override
    protected void parseSerializationDescriptorObject(EconomicMap<String, Object> data, boolean lambdaCapturingType) {
        if (lambdaCapturingType) {
            checkAttributes(data, "serialization descriptor object", Collections.singleton(NAME_KEY), Collections.singleton(CONDITIONAL_KEY));
        } else {
            checkAttributes(data, "serialization descriptor object", Collections.singleton(NAME_KEY),
                            Arrays.asList(CUSTOM_TARGET_CONSTRUCTOR_CLASS_KEY, CONDITIONAL_KEY));
        }

        NamedConfigurationTypeDescriptor targetSerializationClass = NamedConfigurationTypeDescriptor.fromJSONName(asString(data.get(NAME_KEY)));
        UnresolvedConfigurationCondition unresolvedCondition = parseCondition(data, false);
        var condition = conditionResolver.resolveCondition(unresolvedCondition);
        if (!condition.isPresent()) {
            return;
        }

        if (lambdaCapturingType) {
            String className = targetSerializationClass.name();
            serializationSupport.registerLambdaCapturingClass(condition.get(), className);
        } else {
            if (!customConstructorWarningTriggered && data.containsKey(CUSTOM_TARGET_CONSTRUCTOR_CLASS_KEY)) {
                customConstructorWarningTriggered = true;
                LogUtils.warning("\"" + CUSTOM_TARGET_CONSTRUCTOR_CLASS_KEY +
                                "\" is deprecated in serialization-config.json. All serializable classes can be instantiated through any superclass constructor without the use of the flag.");
            }
            serializationSupport.register(condition.get(), targetSerializationClass.name());
        }
    }
}
